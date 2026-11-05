package com.familyfund.identity.internal.controller;

import com.familyfund.core.security.JwtTokenProvider;
import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration + security tests for {@code POST /api/v1/branches/{branchId}/virtual-members} (US-18) over the real
 * stack: Spring Security filter chain and method security, {@code TenantFilter}, Bean Validation,
 * {@code GlobalExceptionHandler}, both modules' services and the Flyway schema in PostgreSQL.
 * Every test runs inside a transaction that is rolled back, so no rows are left behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PositionLinkControllerIntegrationTest {

    private static final String CLAN = "CLAN_PL_IT";
    private static final String OTHER_CLAN = "CLAN_PL_IT_OTHER";
    private static final String PATH = "/api/v1/branches/{branchId}/virtual-members";
    private static final String CLAN_ACCOUNT = "ACC_PL_IT_CLAN";
    private static final String HEAD_ACCOUNT = "ACC_PL_IT_BRANCH";

    private static final String VALID_PAYLOAD = """
            {
                "fullName": "  Nguyen Thi Ba  ",
                "dateOfBirth": "2000-01-01",
                "gender": "Female",
                "generation": 3
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClanRepository clanRepository;
    @Autowired
    private FamilyBranchRepository branchRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private InvitationCodeRepository invitationCodeRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private FamilyBranch headedBranch;
    private FamilyBranch otherBranch;
    private String headMemberId;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN);
        clanRepository.save(Clan.builder().clanId(CLAN).clanName("PL IT Clan").build());
        clanRepository.save(Clan.builder().clanId(OTHER_CLAN).clanName("PL IT Other Clan").build());

        FamilyBranch root = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_PL_ROOT").clanId(CLAN).branchName("Root").generationLevel(1).build());
        headedBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_PL_HEAD").clanId(CLAN).branchName("Chi Mot").generationLevel(2).parentBranch(root).build());
        otherBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_PL_OTHER").clanId(CLAN).branchName("Chi Hai").generationLevel(2).parentBranch(root).build());
        branchRepository.save(FamilyBranch.builder()
                .branchId("BR_PL_FOREIGN").clanId(OTHER_CLAN).branchName("Ngoai Toc").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId("MEM_PL_IT_HEAD").fullName("Truong Chi Mot").gender("Male")
                .generation(2).branch(headedBranch).build());
        headMemberId = head.getMemberId();
        headedBranch.setRepresentative(head);
        branchRepository.saveAndFlush(headedBranch);

        // invitation_codes.created_by_account_id is a real FK: the caller must be a persisted account.
        userAccountRepository.save(UserAccount.builder()
                .accountId(CLAN_ACCOUNT).phoneNumber("0911000001").passwordHash("{noop}test")
                .accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(HEAD_ACCOUNT).memberId(headMemberId).phoneNumber("0911000002").passwordHash("{noop}test")
                .accountStatus(AccountStatus.Active).build());
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Authentication auth(String accountId, String memberId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(new Principal(accountId, memberId), "n/a", authorities);
    }

    private String accessToken(String accountId, String memberId, String role) {
        return jwtTokenProvider.generateAccessToken(accountId, CLAN, memberId, "Truong Chi Mot", Set.of(role));
    }

    private int memberCount() {
        return memberRepository.findByBranch_ClanId(CLAN).size();
    }

    private int codeCount() {
        return invitationCodeRepository.findAllByClanIdAndCreatedByAccountIdOrderByCreatedAtDesc(CLAN, CLAN_ACCOUNT).size();
    }

    // ---------------------------------------------------------------- 201 Created

    @Test
    @DisplayName("201: CLAN_HEAD creates a placeholder leaf and a one-time position link code")
    void clanHead_Creates_Returns201() throws Exception {
        int membersBefore = memberCount();

        String body = mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/invitations/")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo hồ sơ ảo và mã liên kết vị trí thành công"))
                .andExpect(jsonPath("$.data.member.memberId").isNotEmpty())
                .andExpect(jsonPath("$.data.member.fullName").value("Nguyen Thi Ba"))
                .andExpect(jsonPath("$.data.member.gender").value("Female"))
                .andExpect(jsonPath("$.data.member.generation").value(3))
                .andExpect(jsonPath("$.data.member.status").value("Living"))
                .andExpect(jsonPath("$.data.member.branchId").value(otherBranch.getBranchId()))
                .andExpect(jsonPath("$.data.member.branchName").value("Chi Hai"))
                .andExpect(jsonPath("$.data.member.clanId").value(CLAN))
                .andExpect(jsonPath("$.data.member.isPlaceholder").value(true))
                .andExpect(jsonPath("$.data.positionLinkCode.inviteCode").isNotEmpty())
                .andExpect(jsonPath("$.data.positionLinkCode.inviteType").value("PositionLinkCode"))
                .andExpect(jsonPath("$.data.positionLinkCode.maxUses").value(1))
                .andExpect(jsonPath("$.data.positionLinkCode.usedCount").value(0))
                .andExpect(jsonPath("$.data.positionLinkCode.isActive").value(true))
                .andExpect(jsonPath("$.data.positionLinkCode.clanId").value(CLAN))
                .andExpect(jsonPath("$.data.positionLinkCode.clanName").value("PL IT Clan"))
                .andExpect(jsonPath("$.data.positionLinkCode.targetBranchId").value(otherBranch.getBranchId()))
                .andExpect(jsonPath("$.data.positionLinkCode.targetBranchName").value("Chi Hai"))
                .andExpect(jsonPath("$.data.positionLinkCode.createdByAccountId").value(CLAN_ACCOUNT))
                .andExpect(jsonPath("$.data.positionLinkCode.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertEquals(membersBefore + 1, memberCount());

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"inviteCode\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "Response has no inviteCode");
        String inviteCode = matcher.group(1);

        Member stored = memberRepository.findByBranch_ClanId(CLAN).stream()
                .filter(m -> "Nguyen Thi Ba".equals(m.getFullName()))
                .findFirst()
                .orElseThrow();
        assertTrue(stored.getIsPlaceholder());
        assertEquals("Living", stored.getStatus());
        assertTrue(stored.getMemberId().length() <= 20);
        assertEquals(otherBranch.getBranchId(), stored.getBranch().getBranchId());

        InvitationCode code = invitationCodeRepository.findById(inviteCode).orElseThrow();
        assertEquals(InviteType.PositionLinkCode, code.getInviteType());
        assertEquals(CLAN, code.getClanId());
        assertEquals(otherBranch.getBranchId(), code.getTargetBranchId());
        assertEquals(stored.getMemberId(), code.getTargetMemberId());
        assertEquals(1, code.getMaxUses());
        assertEquals(0, code.getUsedCount());
        assertTrue(code.getIsActive());
        assertEquals(CLAN_ACCOUNT, code.getCreatedByAccountId());
        assertTrue(code.getExpiresAt().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
        assertTrue(code.getShareableUrl().endsWith("?code=" + inviteCode));
        assertTrue(code.getShareableUrl().contains("position-link"));
    }

    @Test
    @DisplayName("201: BRANCH_HEAD authenticated by a signed JWT reaches their own branch scope")
    void branchHead_SignedJwt_OwnBranch_Returns201() throws Exception {
        mockMvc.perform(post(PATH, headedBranch.getBranchId())
                        .header("Authorization", "Bearer " + accessToken(HEAD_ACCOUNT, headMemberId, SystemRoles.BRANCH_HEAD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.member.branchId").value(headedBranch.getBranchId()))
                .andExpect(jsonPath("$.data.positionLinkCode.inviteType").value("PositionLinkCode"));

        assertEquals(1, invitationCodeRepository
                .findAllByClanIdAndCreatedByAccountIdOrderByCreatedAtDesc(CLAN, HEAD_ACCOUNT).size());
    }

    @Test
    @DisplayName("201: explicit expiry is honoured on the persisted code")
    void explicitExpiry_IsPersisted() throws Exception {
        Instant expiry = Instant.now().plus(2, ChronoUnit.DAYS);

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Nguyen Thi Ba",
                                    "gender": "Female",
                                    "generation": 3,
                                    "expiresAt": "%s"
                                }
                                """.formatted(expiry)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.positionLinkCode.expiresAt").isNotEmpty());

        InvitationCode code = invitationCodeRepository
                .findAllByClanIdAndCreatedByAccountIdOrderByCreatedAtDesc(CLAN, CLAN_ACCOUNT).get(0);
        assertEquals(expiry.getEpochSecond(), code.getExpiresAt().getEpochSecond());
    }

    // ---------------------------------------------------------------- 401 / 403

    @Test
    @DisplayName("401: an anonymous caller cannot create a virtual position")
    void anonymous_Returns401() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("403: ROLE_MEMBER is blocked by @PreAuthorize and nothing is persisted")
    void memberRole_Returns403() throws Exception {
        int membersBefore = memberCount();
        int codesBefore = codeCount();

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.MEMBER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(membersBefore, memberCount());
        assertEquals(codesBefore, codeCount());
    }

    @Test
    @DisplayName("403: BRANCH_HEAD authenticated by a signed JWT cannot create outside their branch")
    void branchHead_SignedJwt_ForeignBranch_Returns403() throws Exception {
        int membersBefore = memberCount();
        int codesBefore = invitationCodeRepository
                .findAllByClanIdAndCreatedByAccountIdOrderByCreatedAtDesc(CLAN, HEAD_ACCOUNT).size();

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header("Authorization", "Bearer " + accessToken(HEAD_ACCOUNT, headMemberId, SystemRoles.BRANCH_HEAD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(membersBefore, memberCount());
        assertEquals(codesBefore, invitationCodeRepository
                .findAllByClanIdAndCreatedByAccountIdOrderByCreatedAtDesc(CLAN, HEAD_ACCOUNT).size());
    }

    // ---------------------------------------------------------------- 400 / 404 / 409

    @Test
    @DisplayName("400: invalid payload (blank name, bad gender, generation < 1) is rejected")
    void invalidPayload_Returns400() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "   ",
                                    "gender": "female",
                                    "generation": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.gender").exists())
                .andExpect(jsonPath("$.errors.generation").exists());
    }

    @Test
    @DisplayName("400: generation lower than the branch generation level is rejected")
    void generationBelowBranchLevel_Returns400() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"generation\": 3", "\"generation\": 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("400: a branchId longer than VARCHAR(20) is rejected before any lookup")
    void tooLongBranchId_Returns400() throws Exception {
        mockMvc.perform(post(PATH, "B".repeat(21))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("404: unknown branch")
    void unknownBranch_Returns404() throws Exception {
        mockMvc.perform(post(PATH, "BR_PL_NOPE")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("404: a branch of another clan is invisible under the current tenant")
    void foreignClanBranch_Returns404() throws Exception {
        mockMvc.perform(post(PATH, "BR_PL_FOREIGN")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("409: the same virtual profile twice in the clan stores exactly one member and one code")
    void duplicateProfile_Returns409() throws Exception {
        Authentication clanHead = auth(CLAN_ACCOUNT, null, SystemRoles.CLAN_HEAD);

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(clanHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated());

        int membersAfter = memberCount();
        int codesAfter = codeCount();

        mockMvc.perform(post(PATH, headedBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(clanHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("Nguyen Thi Ba", "NGUYEN THI BA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(membersAfter, memberCount());
        assertEquals(codesAfter, codeCount());
    }
}
