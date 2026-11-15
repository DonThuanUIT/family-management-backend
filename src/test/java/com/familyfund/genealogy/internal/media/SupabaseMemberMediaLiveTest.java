package com.familyfund.genealogy.internal.media;

import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.storage.SupabaseStorageAdapter;
import com.familyfund.core.tenant.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Opt-in API -> policy -> PostgreSQL -> real Supabase test with disposable UUID-scoped fixtures. */
@EnabledIfEnvironmentVariable(named="SUPABASE_LIVE_TEST",matches="true")
@SpringBootTest
@AutoConfigureMockMvc
class SupabaseMemberMediaLiveTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.supabase-url",()->System.getenv("SUPABASE_URL"));
        registry.add("app.storage.supabase-service-key",()->System.getenv("SUPABASE_SERVICE_ROLE_KEY"));
        registry.add("app.storage.bucket",()->System.getenv().getOrDefault("APP_STORAGE_BUCKET","member-media"));
    }
    @Autowired MockMvc mvc;
    @Autowired SupabaseStorageAdapter storage;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    String clan,branch,member,account;
    UsernamePasswordAuthenticationToken auth;
    private record Principal(String getAccountId,String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach void seed() {
        String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,10);
        clan="CLIVE"+suffix;branch="BLIVE"+suffix;member="MLIVE"+suffix;account="ALIVE"+suffix;
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("INSERT INTO clans(clan_id,clan_name) VALUES (?,'US32 cloud smoke')",clan);
            jdbc.update("INSERT INTO family_branches(branch_id,clan_id,branch_name,generation_level) VALUES (?,?,'Smoke',1)",branch,clan);
            jdbc.update("INSERT INTO members(member_id,full_name,gender,generation,branch_id,is_placeholder) VALUES (?,'US32 cloud smoke','Other',1,?,false)",member,branch);
            jdbc.update("INSERT INTO user_accounts(account_id,member_id,phone_number,password_hash) VALUES (?,?,?,'unused-test-hash')",account,member,account);
            jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,'ROLE_MEMBER')",account);
        });
        auth=new UsernamePasswordAuthenticationToken(new Principal(account,member),null,List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        SecurityContextHolder.getContext().setAuthentication(auth);TenantContext.setClanId(clan);
    }
    @AfterEach void cleanup() {
        try {
            // Only delete objects registered to this test's random clan, never sweep the shared bucket.
            for(var file:jdbc.queryForList("SELECT bucket,object_key FROM stored_files WHERE clan_id=?",clan)) {
                String bucket=file.get("bucket").toString(),key=file.get("object_key").toString();
                storage.delete(bucket,key);assertFalse(storage.exists(bucket,key));
            }
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                jdbc.update("DELETE FROM audit_logs WHERE clan_id=?",clan);
                jdbc.update("UPDATE members SET avatar_file_id=NULL,avatar_url=NULL WHERE member_id=?",member);
                jdbc.update("DELETE FROM media_upload_requests WHERE member_id=?",member);
                jdbc.update("DELETE FROM member_attachments WHERE member_id=?",member);
                jdbc.update("DELETE FROM stored_files WHERE clan_id=?",clan);
                jdbc.update("DELETE FROM user_accounts WHERE account_id=?",account);
                jdbc.update("DELETE FROM members WHERE member_id=?",member);
                jdbc.update("DELETE FROM family_branches WHERE branch_id=?",branch);
                jdbc.update("DELETE FROM clans WHERE clan_id=?",clan);
            });
        } finally { SecurityContextHolder.clearContext();TenantContext.clear(); }
    }
    @Test void avatarAndPdfRoundTripThroughBackendOnRealPrivateStorage() throws Exception {
        mvc.perform(multipart("/api/v1/members/{memberId}/avatar",member).file(MediaFileValidatorTest.png(128,128))
                .param("expectedMediaVersion","0").header("X-Clan-Id",clan).with(authentication(auth))
                .with(request->{request.setMethod("PUT");return request;}))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mediaVersion").value(1));
        mvc.perform(get("/api/v1/members/{memberId}/avatar/content",member).header("X-Clan-Id",clan).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        var pdfBytes=new ByteArrayOutputStream();
        try(var pdf=new PDDocument()) { pdf.addPage(new PDPage());pdf.save(pdfBytes); }
        var result=mvc.perform(multipart("/api/v1/members/{memberId}/attachments",member)
                .file(new MockMultipartFile("file","smoke.pdf","application/pdf",pdfBytes.toByteArray()))
                .param("title","Disposable cloud smoke").param("visibility","RESTRICTED")
                .header("Idempotency-Key",UUID.randomUUID()).header("X-Clan-Id",clan).with(authentication(auth)))
                .andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(result.getResponse().getContentAsByteArray()).get("data").get("id").asString();
        mvc.perform(get("/api/v1/members/{memberId}/attachments/{id}/content",member,id).header("X-Clan-Id",clan).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(content().bytes(pdfBytes.toByteArray()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE clan_id=? AND provider='SUPABASE' AND state='READY'",Integer.class,clan));
        mvc.perform(delete("/api/v1/members/{memberId}/attachments/{id}",member,id).param("expectedVersion","0")
                .header("X-Clan-Id",clan).with(authentication(auth))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/members/{memberId}/attachments/{id}/content",member,id).header("X-Clan-Id",clan).with(authentication(auth)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/members/{memberId}/avatar",member).param("expectedMediaVersion","1")
                .header("X-Clan-Id",clan).with(authentication(auth))).andExpect(status().isOk());
    }
}
