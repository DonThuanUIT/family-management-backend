package com.familyfund.identity.internal.controller;

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
import com.familyfund.identity.internal.repository.AccountRoleRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for "enter the PositionLinkCode while registering and join the branch automatically" (US-19),
 * over the real stack: Spring Security filter chain ({@code permitAll} rules), {@code TenantFilter}, Bean Validation,
 * {@code GlobalExceptionHandler}, both modules' services and the Flyway schema in PostgreSQL.
 *
 * <p>The happy path really starts where the feature starts: a clan head creates the virtual position through the
 * US-18 endpoint, the recipient opens the shared code as an anonymous visitor, registers with it and is joined to
 * that branch with an {@code Active} account - no join request, no approval. Every test runs inside a transaction
 * that is rolled back, so no rows are left behind.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PositionLinkClaimIntegrationTest {

    private static final String CLAN = "CLAN_PL_CLAIM_IT";
    private static final String OTHER_CLAN = "CLAN_PLC_OTHER";

    private static final String CREATE_PATH = "/api/v1/branches/{branchId}/virtual-members";
    private static final String PREVIEW_PATH = "/api/v1/position-links/{inviteCode}";
    private static final String CLAIM_PATH = "/api/v1/position-links/{inviteCode}/claim";
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final String CLAN_ACCOUNT = "ACC_PL_CLAIM_HEAD";
    private static final String RAW_PASSWORD = "secret123";

    private static final String VIRTUAL_MEMBER_PAYLOAD = """
            {
                "fullName": "Nguyen Van Ut",
                "dateOfBirth": "2015-03-08",
                "gender": "Male",
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
    private AccountRoleRepository accountRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private FamilyBranch chiBranch;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN);
        clanRepository.save(Clan.builder().clanId(CLAN).clanName("PL Claim IT Clan").build());
        clanRepository.save(Clan.builder().clanId(OTHER_CLAN).clanName("PL Claim IT Other Clan").build());

        FamilyBranch root = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_PLC_ROOT").clanId(CLAN).branchName("Root").generationLevel(1).build());
        chiBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_PLC_CHI").clanId(CLAN).branchName("Chi Ut").generationLevel(2)
                .parentBranch(root).build());

        userAccountRepository.save(UserAccount.builder()
                .accountId(CLAN_ACCOUNT).phoneNumber("0917000001").passwordHash("{noop}test")
                .accountStatus(AccountStatus.Active).build());
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Authentication clanHead() {
        return new UsernamePasswordAuthenticationToken(
                new Principal(CLAN_ACCOUNT, null),
                "n/a",
                List.of(new SimpleGrantedAuthority(SystemRoles.CLAN_HEAD)));
    }

    private static String registration(String phoneNumber, String password, String email) {
        String emailJson = email == null ? "null" : "\"" + email + "\"";
        return """
                {
                    "phoneNumber": "%s",
                    "password": "%s",
                    "email": %s
                }
                """.formatted(phoneNumber, password, emailJson);
    }

    /** Creates a placeholder profile + code through the public US-18 endpoint and returns the code. */
    private String createPositionLinkThroughApi() throws Exception {
        String body = mockMvc.perform(post(CREATE_PATH, chiBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(clanHead()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VIRTUAL_MEMBER_PAYLOAD))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = Pattern.compile("\"inviteCode\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "Response has no inviteCode");
        return matcher.group(1);
    }

    private String placeholderMemberId(String inviteCode) {
        return invitationCodeRepository.findById(inviteCode).orElseThrow().getTargetMemberId();
    }

    // ------------------------------------------------------------- the feature end to end

    @Test
    @DisplayName("US-18 code + registration = joined branch, Active account, no approval step")
    void registerWithCode_JoinsBranchAutomatically() throws Exception {
        String inviteCode = createPositionLinkThroughApi();
        String memberId = placeholderMemberId(inviteCode);
        String phoneNumber = "0917000111";

        // 1. The recipient opens the shared link as an anonymous visitor and sees the position.
        mockMvc.perform(get(PREVIEW_PATH, inviteCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
                .andExpect(jsonPath("$.data.clanId").value(CLAN))
                .andExpect(jsonPath("$.data.clanName").value("PL Claim IT Clan"))
                .andExpect(jsonPath("$.data.branchId").value(chiBranch.getBranchId()))
                .andExpect(jsonPath("$.data.branchName").value("Chi Ut"))
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Ut"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());

        // 2. They register with the code - no token, no join request, no approval.
        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(phoneNumber, RAW_PASSWORD, "ut@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Liên kết tài khoản với vị trí phả hệ thành công"))
                .andExpect(jsonPath("$.data.accountStatus").value("Active"))
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Ut"))
                .andExpect(jsonPath("$.data.generation").value(3))
                .andExpect(jsonPath("$.data.branchId").value(chiBranch.getBranchId()))
                .andExpect(jsonPath("$.data.branchName").value("Chi Ut"))
                .andExpect(jsonPath("$.data.clanId").value(CLAN))
                .andExpect(jsonPath("$.data.clanName").value("PL Claim IT Clan"))
                .andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
                .andExpect(jsonPath("$.data.roles[0]").value(SystemRoles.MEMBER))
                .andExpect(jsonPath("$.data.phoneNumber").value(phoneNumber))
                .andExpect(jsonPath("$.data.email").value("ut@example.com"))
                .andExpect(jsonPath("$.data.linkedAt").isNotEmpty());

        // 3. The position is no longer a placeholder: it is a real profile of that branch.
        Member member = memberRepository.findById(memberId).orElseThrow();
        assertFalse(member.getIsPlaceholder());
        assertEquals("Nguyen Van Ut", member.getFullName());
        assertEquals(3, member.getGeneration());
        assertEquals(chiBranch.getBranchId(), member.getBranch().getBranchId());

        // 4. The account is Active, bound to the position and holds ROLE_MEMBER.
        UserAccount account = userAccountRepository.findByPhoneNumber(phoneNumber).orElseThrow();
        assertEquals(AccountStatus.Active, account.getAccountStatus());
        assertEquals(memberId, account.getMemberId());
        assertEquals("ut@example.com", account.getEmail());
        assertTrue(accountRoleRepository.existsByIdAccountIdAndIdRoleId(account.getAccountId(), SystemRoles.MEMBER));

        // 5. The one-time code is spent and can never be redeemed again.
        InvitationCode code = invitationCodeRepository.findById(inviteCode).orElseThrow();
        assertEquals(1, code.getUsedCount());
        assertFalse(code.getIsActive());

        // 6. "Active without verification" is proven by the login that now succeeds.
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginIdentifier": "%s", "password": "%s"}
                                """.formatted(phoneNumber, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accountId").value(account.getAccountId()))
                .andExpect(jsonPath("$.data.memberId").value(memberId))
                .andExpect(jsonPath("$.data.clanId").value(CLAN));
    }

    @Test
    @DisplayName("The code is the credential: joining works even when the request carries another X-Clan-Id")
    void claimIgnoresRequestTenant() throws Exception {
        String inviteCode = createPositionLinkThroughApi();

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .header(TenantFilter.HEADER_CLAN_ID, OTHER_CLAN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000112", RAW_PASSWORD, "ut2@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clanId").value(CLAN))
                .andExpect(jsonPath("$.data.branchId").value(chiBranch.getBranchId()));

        assertFalse(memberRepository.findById(placeholderMemberId(inviteCode)).orElseThrow().getIsPlaceholder());
    }

    @Test
    @DisplayName("An account created by the US-17 join flow (PendingApproval) is activated by the code")
    void pendingApprovalAccount_IsActivatedByTheCode() throws Exception {
        String inviteCode = createPositionLinkThroughApi();
        String memberId = placeholderMemberId(inviteCode);
        String phoneNumber = "0917000113";

        userAccountRepository.save(UserAccount.builder()
                .accountId("ACC_PL_CLAIM_PENDING")
                .phoneNumber(phoneNumber)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .accountStatus(AccountStatus.PendingApproval)
                .build());

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(phoneNumber, RAW_PASSWORD, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value("ACC_PL_CLAIM_PENDING"))
                .andExpect(jsonPath("$.data.accountStatus").value("Active"))
                .andExpect(jsonPath("$.data.memberId").value(memberId));

        UserAccount account = userAccountRepository.findById("ACC_PL_CLAIM_PENDING").orElseThrow();
        assertEquals(AccountStatus.Active, account.getAccountStatus());
        assertEquals(memberId, account.getMemberId());
    }

    // ------------------------------------------------------------- rejected claims

    @Test
    @DisplayName("400: the same code cannot be redeemed twice")
    void usedCode_CannotBeRedeemedTwice() throws Exception {
        String inviteCode = createPositionLinkThroughApi();

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000114", RAW_PASSWORD, "first@example.com")))
                .andExpect(status().isOk());

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000115", RAW_PASSWORD, "second@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // The second claimant was never created.
        assertTrue(userAccountRepository.findByPhoneNumber("0917000115").isEmpty());
    }

    @Test
    @DisplayName("404: unknown code, and a general clan invite is not a position link")
    void unknownOrForeignCodeType_Returns404() throws Exception {
        mockMvc.perform(post(CLAIM_PATH, "NOSUCHCD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000116", RAW_PASSWORD, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        invitationCodeRepository.save(InvitationCode.builder()
                .inviteCode("GENRL123").clanId(CLAN).inviteType(InviteType.GeneralClanInvite)
                .maxUses(100).usedCount(0).expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .isActive(true).createdByAccountId(CLAN_ACCOUNT).build());

        mockMvc.perform(get(PREVIEW_PATH, "GENRL123"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(CLAIM_PATH, "GENRL123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000117", RAW_PASSWORD, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("400: an expired position link cannot be redeemed")
    void expiredCode_Returns400() throws Exception {
        String memberId = "MEM_PLC_EXPIRED";
        memberRepository.save(Member.builder()
                .memberId(memberId).fullName("Nguyen Van Het Han").gender("Male").generation(3)
                .branch(chiBranch).isPlaceholder(true).build());
        invitationCodeRepository.save(InvitationCode.builder()
                .inviteCode("EXPIRD99").clanId(CLAN).inviteType(InviteType.PositionLinkCode)
                .targetBranchId(chiBranch.getBranchId()).targetMemberId(memberId)
                .maxUses(1).usedCount(0).expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .isActive(true).createdByAccountId(CLAN_ACCOUNT).build());

        mockMvc.perform(post(CLAIM_PATH, "EXPIRD99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000118", RAW_PASSWORD, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertTrue(memberRepository.findById(memberId).orElseThrow().getIsPlaceholder());
    }

    @Test
    @DisplayName("400: a wrong password cannot take over an existing account")
    void wrongPassword_Returns400() throws Exception {
        String inviteCode = createPositionLinkThroughApi();
        String phoneNumber = "0917000119";

        userAccountRepository.save(UserAccount.builder()
                .accountId("ACC_PLC_EXISTING").phoneNumber(phoneNumber)
                .passwordHash(passwordEncoder.encode("anotherPass")).accountStatus(AccountStatus.Active).build());

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(phoneNumber, RAW_PASSWORD, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertTrue(memberRepository.findById(placeholderMemberId(inviteCode)).orElseThrow().getIsPlaceholder());
        assertNull(userAccountRepository.findById("ACC_PLC_EXISTING").orElseThrow().getMemberId());
    }

    @Test
    @DisplayName("409: an account that already owns a profile cannot claim a second one")
    void accountAlreadyLinked_Returns409() throws Exception {
        String inviteCode = createPositionLinkThroughApi();
        String phoneNumber = "0917000120";

        Member owned = memberRepository.save(Member.builder()
                .memberId("MEM_PLC_OWNED").fullName("Nguyen Van So Huu").gender("Male").generation(2)
                .branch(chiBranch).isPlaceholder(false).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId("ACC_PL_CLAIM_LINKED").phoneNumber(phoneNumber).memberId(owned.getMemberId())
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD)).accountStatus(AccountStatus.Active).build());

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(phoneNumber, RAW_PASSWORD, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("400: an invalid registration payload is rejected field by field")
    void invalidRegistration_Returns400() throws Exception {
        String inviteCode = createPositionLinkThroughApi();

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber": "12345", "password": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        assertTrue(memberRepository.findById(placeholderMemberId(inviteCode)).orElseThrow().getIsPlaceholder());
    }

    @Test
    @DisplayName("The branch scope is respected: the claimed profile stays inside its own branch")
    void claimedProfileKeepsItsBranch() throws Exception {
        String inviteCode = createPositionLinkThroughApi();
        String memberId = placeholderMemberId(inviteCode);

        mockMvc.perform(post(CLAIM_PATH, inviteCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("0917000121", RAW_PASSWORD, null)))
                .andExpect(status().isOk());

        Member member = memberRepository.findById(memberId).orElseThrow();
        assertNotNull(member.getBranch());
        assertEquals("BR_PLC_CHI", member.getBranch().getBranchId());
        assertEquals(CLAN, member.getBranch().getClanId());
        assertTrue(memberRepository.findByBranch_ClanIdAndBranch_BranchId(CLAN, "BR_PLC_CHI").stream()
                .anyMatch(m -> m.getMemberId().equals(memberId) && Boolean.FALSE.equals(m.getIsPlaceholder())));
    }
}