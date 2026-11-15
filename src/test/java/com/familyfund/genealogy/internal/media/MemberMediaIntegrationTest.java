package com.familyfund.genealogy.internal.media;

import com.familyfund.core.security.*;
import com.familyfund.core.storage.*;
import com.familyfund.core.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Real committed transactions and isolated fixtures; no shared demo profile is modified. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberMediaIntegrationTest {
    @Autowired MemberMediaService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StorageRegistry registry;
    @Autowired MockMvc mvc;
    @Autowired StorageProperties properties;
    @MockitoBean SupabaseStorageAdapter storage;
    private final ConcurrentMap<String,byte[]> objects = new ConcurrentHashMap<>();
    String clan,otherClan,branch,child,sibling,owner,viewer,childViewer,manager,account,viewerAccount,childAccount,managerAccount;
    private record Principal(String getAccountId,String getMemberId) implements MemberLinkedPrincipal {}

    private void configureStorage() {
        when(storage.provider()).thenReturn("SUPABASE");
        doAnswer(call -> {
            objects.put(call.getArgument(0)+"/"+call.getArgument(1), ((byte[])call.getArgument(2)).clone());
            return null;
        }).when(storage).put(anyString(),anyString(),any(byte[].class),anyString());
        when(storage.open(anyString(),anyString())).thenAnswer(call -> {
            byte[] bytes=objects.get(call.getArgument(0)+"/"+call.getArgument(1));
            if(bytes==null) throw MediaException.missing();
            return new ByteArrayInputStream(bytes);
        });
        when(storage.exists(anyString(),anyString())).thenAnswer(call -> objects.containsKey(call.getArgument(0)+"/"+call.getArgument(1)));
        doAnswer(call -> { objects.remove(call.getArgument(0)+"/"+call.getArgument(1));return null; })
                .when(storage).delete(anyString(),anyString());
    }
    @BeforeEach void seed() {
        configureStorage();
        String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,9);
        clan="C32"+suffix;otherClan="O32"+suffix;branch="B32"+suffix;child="D32"+suffix;sibling="S32"+suffix;
        owner="M32"+suffix;viewer="V32"+suffix;childViewer="K32"+suffix;manager="H32"+suffix;
        account="A32"+suffix;viewerAccount="Q32"+suffix;childAccount="R32"+suffix;managerAccount="T32"+suffix;
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("INSERT INTO clans(clan_id,clan_name) VALUES (?,?),(?,?)",clan,"US32 fixture",otherClan,"US32 other fixture");
            jdbc.update("INSERT INTO family_branches(branch_id,clan_id,branch_name,generation_level) VALUES (?,?,?,1),(?,?,?,1)",branch,clan,"Root",sibling,clan,"Sibling");
            jdbc.update("INSERT INTO family_branches(branch_id,clan_id,branch_name,generation_level,parent_branch_id) VALUES (?,?,?,2,?)",child,clan,"Child",branch);
            member(owner,branch);member(viewer,sibling);member(childViewer,child);member(manager,branch);
            account(account,owner);account(viewerAccount,viewer);account(childAccount,childViewer);account(managerAccount,manager);
            jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,'ROLE_BRANCH_HEAD')",managerAccount);
            jdbc.update("UPDATE family_branches SET representative_id=? WHERE branch_id=?",manager,branch);
        });
        login(account,owner);
    }
    void member(String id,String branchId) { jdbc.update("INSERT INTO members(member_id,full_name,gender,generation,branch_id,is_placeholder) VALUES (?,?,'Male',2,?,false)",id,"US32 fixture",branchId); }
    void account(String id,String memberId) {
        jdbc.update("INSERT INTO user_accounts(account_id,member_id,phone_number,password_hash) VALUES (?,?,?,'unused-test-hash')",id,memberId,id);
        jdbc.update("INSERT INTO account_roles(account_id,role_id) VALUES (?,'ROLE_MEMBER')",id);
    }
    UsernamePasswordAuthenticationToken login(String accountId,String memberId) {
        var authentication=new UsernamePasswordAuthenticationToken(new Principal(accountId,memberId),null,List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);TenantContext.setClanId(clan);return authentication;
    }
    @AfterEach void cleanup() throws Exception {
        reset(storage);properties.setQuotaBytes(524288000);properties.setMaxAttachments(20);
        SecurityContextHolder.clearContext();TenantContext.clear();
        if(clan==null)return;
        // Only this test's disposable in-memory objects and UUID-scoped database fixtures.
        objects.clear();
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("DELETE FROM audit_logs WHERE clan_id=?",clan);
            jdbc.update("UPDATE members SET avatar_file_id=NULL,avatar_url=NULL WHERE branch_id IN (SELECT branch_id FROM family_branches WHERE clan_id=?)",clan);
            jdbc.update("DELETE FROM media_upload_requests WHERE member_id IN (SELECT member_id FROM members WHERE branch_id IN (SELECT branch_id FROM family_branches WHERE clan_id=?))",clan);
            jdbc.update("DELETE FROM member_attachments WHERE member_id IN (SELECT member_id FROM members WHERE branch_id IN (SELECT branch_id FROM family_branches WHERE clan_id=?))",clan);
            jdbc.update("DELETE FROM stored_files WHERE clan_id=?",clan);
            jdbc.update("DELETE FROM user_accounts WHERE member_id IN (SELECT member_id FROM members WHERE branch_id IN (SELECT branch_id FROM family_branches WHERE clan_id=?))",clan);
            jdbc.update("UPDATE family_branches SET representative_id=NULL WHERE clan_id=?",clan);
            jdbc.update("DELETE FROM member_privacy_settings WHERE member_id IN (SELECT member_id FROM members WHERE branch_id IN (SELECT branch_id FROM family_branches WHERE clan_id=?))",clan);
            jdbc.update("DELETE FROM members WHERE branch_id IN (SELECT branch_id FROM family_branches WHERE clan_id=?)",clan);
            jdbc.update("DELETE FROM family_branches WHERE branch_id=?",child);
            jdbc.update("DELETE FROM family_branches WHERE clan_id=?",clan);
            jdbc.update("DELETE FROM clans WHERE clan_id IN (?,?)",clan,otherClan);
        });
    }
    Map<String,Object> upload(String visibility,UUID key) throws Exception { return service.uploadAttachment(owner,MediaFileValidatorTest.png(128,128),"Gia sử",null,visibility,key); }
    UUID id(Map<String,Object> response) { return UUID.fromString(response.get("id").toString()); }
    @Test void avatarRoundTripVersionAndStorageFailurePreserveOldImage() throws Exception {
        var response=service.avatar(owner,0,MediaFileValidatorTest.png(128,128));assertEquals(1L,response.get("mediaVersion"));
        try(var content=service.avatarContent(owner).stream()) { assertTrue(content.readAllBytes().length>0); }
        assertNotNull(service.detail(owner).get("avatarUrl"));
        doThrow(MediaException.unavailable()).when(storage).put(anyString(),anyString(),any(byte[].class),anyString());
        assertEquals(503,assertThrows(MediaException.class,()->service.avatar(owner,1,MediaFileValidatorTest.png(128,128))).getStatus());
        assertEquals(1L,service.detail(owner).get("mediaVersion"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE member_id=? AND state='DELETE_PENDING'",Integer.class,owner));
        assertEquals(409,assertThrows(MediaException.class,()->service.deleteAvatar(owner,0)).getStatus());
        assertNull(service.deleteAvatar(owner,1).get("avatarUrl"));
    }
    @Test void biographyIsPrivateByDefaultAndRejectsStaleWrite() {
        var delta=mapper.readTree("{\"ops\":[{\"insert\":\"Kỷ niệm\\n\"}]}");
        service.biography(owner,0,delta,"RESTRICTED");
        login(viewerAccount,viewer);assertNull(service.detail(owner).get("biography"));
        assertEquals("RESTRICTED",service.detail(owner).get("biographyAccess"));
        login(account,owner);assertNotNull(service.detail(owner).get("biography"));
        assertEquals(409,assertThrows(MediaException.class,()->service.biography(owner,0,delta,"CLAN")).getStatus());
        service.biography(owner,1,delta,"CLAN");login(viewerAccount,viewer);
        assertNotNull(service.detail(owner).get("biography"));
    }
    @Test void listCountsOnlyVisibleFilesAndChecksDownload() throws Exception {
        UUID restricted=id(upload("RESTRICTED",UUID.randomUUID()));
        UUID shared=id(upload("CLAN",UUID.randomUUID()));
        login(viewerAccount,viewer);
        assertEquals(1L,service.attachments(owner,0,10).get("totalElements"));
        assertEquals(404,assertThrows(MediaException.class,()->service.attachmentContent(owner,restricted)).getStatus());
        try(var stream=service.attachmentContent(owner,shared).stream()) { assertTrue(stream.read()>0); }
        assertEquals(403,assertThrows(MediaException.class,()->service.deleteAttachment(owner,shared,0)).getStatus());
    }
    @Test void branchSharingAllowsDescendantsButNotSiblings() throws Exception {
        UUID attachment=id(upload("BRANCH",UUID.randomUUID()));
        login(childAccount,childViewer);assertNotNull(service.attachment(owner,attachment));
        login(viewerAccount,viewer);assertEquals(404,assertThrows(MediaException.class,()->service.attachment(owner,attachment)).getStatus());
    }
    @Test void branchManagerUsesLiveRoleAndScopeRatherThanJwt() throws Exception {
        login(managerAccount,manager);
        service.avatar(childViewer,0,MediaFileValidatorTest.png(128,128));
        assertEquals(403,assertThrows(MediaException.class,()->service.avatar(viewer,0,MediaFileValidatorTest.png(128,128))).getStatus());
        jdbc.update("DELETE FROM account_roles WHERE account_id=? AND role_id='ROLE_BRANCH_HEAD'",managerAccount);
        assertEquals(403,assertThrows(MediaException.class,()->service.avatar(childViewer,1,MediaFileValidatorTest.png(128,128))).getStatus());
    }
    @Test void idempotencyReplayDoesNotDuplicateAndConflictsOnDifferentPayload() throws Exception {
        UUID key=UUID.randomUUID();var first=upload("CLAN",key);var second=upload("CLAN",key);
        assertEquals(first.get("id"),second.get("id"));assertEquals(1L,service.attachments(owner,0,10).get("totalElements"));
        assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(MediaException.class,()->upload("BRANCH",key)).getCode());
        service.deleteAttachment(owner,id(first),0);
        assertEquals(404,assertThrows(MediaException.class,()->service.attachmentContent(owner,id(first))).getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE member_id=? AND state='DELETE_PENDING'",Integer.class,owner));
    }
    @Test void cleanupConfirmsObjectAbsenceAndReleasesQuota() throws Exception {
        var first=upload("CLAN",UUID.randomUUID());UUID id=id(first);service.deleteAttachment(owner,id,0);
        new MediaCleanupWorker(new MemberMediaRepository(jdbc),registry,transactions).run();
        assertEquals("DELETED",jdbc.queryForObject("SELECT state FROM stored_files WHERE member_id=?",String.class,owner));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE member_id=? AND state<>'DELETED'",Integer.class,owner));
    }
    @Test void quotaRejectsWithoutCreatingPendingObject() throws Exception {
        properties.setQuotaBytes(1);
        assertEquals("STORAGE_QUOTA_EXCEEDED",assertThrows(MediaException.class,()->upload("CLAN",UUID.randomUUID())).getCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE member_id=?",Integer.class,owner));
    }
    @Test void concurrentProfileEditAfterUploadSchedulesNewObjectForCleanup() throws Exception {
        service.avatar(owner,0,MediaFileValidatorTest.png(128,128));
        Object original=jdbc.queryForObject("SELECT avatar_file_id FROM members WHERE member_id=?",Object.class,owner);
        doAnswer(invocation->{
            service.biography(owner,1,mapper.readTree("{\"ops\":[{\"insert\":\"Updated\\n\"}]}"),"CLAN");
            objects.put(invocation.getArgument(0)+"/"+invocation.getArgument(1), ((byte[])invocation.getArgument(2)).clone());
            return null;
        }).when(storage).put(anyString(),anyString(),any(byte[].class),anyString());
        assertEquals(409,assertThrows(MediaException.class,()->service.avatar(owner,1,MediaFileValidatorTest.png(128,128))).getStatus());
        assertEquals(original,jdbc.queryForObject("SELECT avatar_file_id FROM members WHERE member_id=?",Object.class,owner));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE member_id=? AND state='DELETE_PENDING'",Integer.class,owner));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM stored_files WHERE member_id=? AND state='PENDING'",Integer.class,owner));
    }
    @Test void cleanupFailureKeepsQuotaAndRetriesWithoutExposingProviderError() throws Exception {
        UUID attachment=id(upload("RESTRICTED",UUID.randomUUID()));service.deleteAttachment(owner,attachment,0);
        doThrow(MediaException.unavailable()).when(storage).delete(anyString(),anyString());
        var worker=new MediaCleanupWorker(new MemberMediaRepository(jdbc),registry,transactions);worker.run();
        assertEquals("DELETE_PENDING",jdbc.queryForObject("SELECT state FROM stored_files WHERE member_id=?",String.class,owner));
        assertEquals(1,jdbc.queryForObject("SELECT delete_attempts FROM stored_files WHERE member_id=?",Integer.class,owner));
        reset(storage);configureStorage();jdbc.update("UPDATE stored_files SET cleanup_after=now()-interval '1 second' WHERE member_id=?",owner);worker.run();
        assertEquals("DELETED",jdbc.queryForObject("SELECT state FROM stored_files WHERE member_id=?",String.class,owner));
    }
    @Test void metadataUpdateChangesPermissionsAndUsesVersion() throws Exception {
        UUID attachment=id(upload("CLAN",UUID.randomUUID()));
        var updated=service.patchAttachment(owner,attachment,mapper.readTree("{\"expectedVersion\":0,\"title\":\"Updated\",\"visibility\":\"RESTRICTED\",\"description\":null}"));
        assertEquals(1L,updated.get("version"));assertEquals("Updated",updated.get("title"));
        assertEquals(409,assertThrows(MediaException.class,()->service.patchAttachment(owner,attachment,mapper.readTree("{\"expectedVersion\":0,\"title\":\"Lost edit\"}"))).getStatus());
        login(viewerAccount,viewer);assertEquals(0L,service.attachments(owner,0,10).get("totalElements"));
        assertEquals(404,assertThrows(MediaException.class,()->service.attachment(viewer,attachment)).getStatus());
    }
    @Test void inactiveAccountsAndForgedTenantCannotAccessMedia() throws Exception {
        TenantContext.setClanId(otherClan);
        assertEquals(404,assertThrows(MediaException.class,()->service.detail(owner)).getStatus());
        TenantContext.setClanId(clan);jdbc.update("UPDATE user_accounts SET account_status='Suspended' WHERE account_id=?",account);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.detail(owner));
    }
    @Test void concurrentUploadsCannotBothTakeLastSlot() throws Exception {
        properties.setMaxAttachments(1);
        var start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        Callable<String> attempt=()->{login(account,owner);try{start.await();upload("CLAN",UUID.randomUUID());return "OK";}
            catch(MediaException ex){return ex.getCode();}finally{SecurityContextHolder.clearContext();TenantContext.clear();}};
        try {
            var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();
            assertEquals(Set.of("OK","ATTACHMENT_LIMIT_REACHED"),Set.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)));
            assertEquals(1L,service.attachments(owner,0,10).get("totalElements"));
        }finally{pool.shutdownNow();}
    }
    @Test void httpContractAuthenticationHeadersAndPrivateContent() throws Exception {
        SecurityContextHolder.clearContext(); TenantContext.clear();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/members/{memberId}",owner).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
        var auth=login(account,owner);
        mvc.perform(multipart("/api/v1/members/{memberId}/avatar",owner).file(MediaFileValidatorTest.png(128,128)).param("expectedMediaVersion","0")
                .with(authentication(auth)).header("X-Clan-Id",clan).with(request->{request.setMethod("PUT");return request;}))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.mediaVersion").value(1));
        mvc.perform(get("/api/v1/members/{memberId}/avatar/content",owner).with(authentication(auth)).header("X-Clan-Id",clan))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/v1/members/{memberId}",owner).with(authentication(auth)).header("X-Clan-Id",otherClan)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/members/{memberId}/attachments/invalid-id",owner).with(authentication(auth)).header("X-Clan-Id",clan))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEDIA_REQUEST"));
    }
}
