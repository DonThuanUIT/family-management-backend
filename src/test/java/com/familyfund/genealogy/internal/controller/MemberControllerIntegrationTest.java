package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.JwtTokenProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration + security tests for {@link MemberController#createMember} over the real stack:
 * the Spring Security filter chain (method security included), {@code TenantFilter}, Bean Validation,
 * {@code GlobalExceptionHandler} and the Flyway schema in PostgreSQL. Every test runs inside a
 * transaction that is rolled back, so no rows are left behind.
 *
 * <p>Contract under test: {@code POST /api/v1/branches/{branchId}/members} → {@code 201 Created},
 * {@code 400} on invalid input, {@code 403} for unauthorised roles/branch scope, {@code 404} for an
 * unknown branch, {@code 409} for a duplicate profile.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberControllerIntegrationTest {

    private static final String CLAN = "CLAN_CTRL_IT";
    private static final String OTHER_CLAN = "CLAN_CTRL_IT_OTHER";
    private static final String PATH = "/api/v1/branches/{branchId}/members";

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
    private JwtTokenProvider jwtTokenProvider;

    private FamilyBranch headedBranch;
    private FamilyBranch otherBranch;
    private String headMemberId;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN);
        clanRepository.save(Clan.builder().clanId(CLAN).clanName("CTRL IT Clan").build());
        clanRepository.save(Clan.builder().clanId(OTHER_CLAN).clanName("CTRL IT Other Clan").build());

        FamilyBranch root = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_CTRL_ROOT").clanId(CLAN).branchName("Root").generationLevel(1).build());
        headedBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_CTRL_HEAD").clanId(CLAN).branchName("Chi Mot").generationLevel(2).parentBranch(root).build());
        otherBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_CTRL_OTHER").clanId(CLAN).branchName("Chi Hai").generationLevel(2).parentBranch(root).build());
        // A branch that belongs to ANOTHER clan: never visible under the CLAN tenant.
        branchRepository.save(FamilyBranch.builder()
                .branchId("BR_CTRL_FOREIGN").clanId(OTHER_CLAN).branchName("Ngoai Toc").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId("MEMCTRLHEAD000001").fullName("Truong Chi Mot").gender("Male")
                .generation(2).branch(headedBranch).build());
        headMemberId = head.getMemberId();
        headedBranch.setRepresentative(head);
        branchRepository.saveAndFlush(headedBranch);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private Authentication auth(String memberId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(new Principal("ACC_CTRL_IT", memberId), "n/a", authorities);
    }

    // ---------------------------------------------------------------- 201 Created

    @Test
    @DisplayName("201: CLAN_HEAD creates a member and the response + DB row match the OpenAPI schema")
    void clanHead_Creates_Returns201() throws Exception {
        long before = memberRepository.count();

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Member created successfully"))
                .andExpect(jsonPath("$.data.memberId").isNotEmpty())
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Thi Ba"))
                .andExpect(jsonPath("$.data.gender").value("Female"))
                .andExpect(jsonPath("$.data.generation").value(3))
                .andExpect(jsonPath("$.data.status").value("Living"))
                .andExpect(jsonPath("$.data.branchId").value(otherBranch.getBranchId()))
                .andExpect(jsonPath("$.data.branchName").value("Chi Hai"))
                .andExpect(jsonPath("$.data.clanId").value(CLAN))
                .andExpect(jsonPath("$.data.isPlaceholder").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        assertEquals(before + 1, memberRepository.count());
        Member stored = memberRepository.findByBranch_ClanId(CLAN).stream()
                .filter(m -> "Nguyen Thi Ba".equals(m.getFullName()))
                .findFirst()
                .orElseThrow();
        assertEquals(otherBranch.getBranchId(), stored.getBranch().getBranchId());
        assertTrue(stored.getIsPlaceholder());
        assertEquals("Living", stored.getStatus());
        assertTrue(stored.getMemberId().length() <= 20);
    }

    @Test
    @DisplayName("201: ADMIN may create in any branch")
    void admin_Creates_Returns201() throws Exception {
        mockMvc.perform(post(PATH, headedBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.branchId").value(headedBranch.getBranchId()));
    }

    @Test
    @DisplayName("201: BRANCH_HEAD may create inside the branch they represent")
    void branchHead_OwnBranch_Returns201() throws Exception {
        mockMvc.perform(post(PATH, headedBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(headMemberId, SystemRoles.BRANCH_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.branchId").value(headedBranch.getBranchId()));
    }

    // ------------------------------------------- signed JWT through the real security filter chain

    @Test
    @DisplayName("201: BRANCH_HEAD authenticated by a signed JWT reaches their own branch scope")
    void branchHead_SignedJwt_OwnBranch_Returns201() throws Exception {
        mockMvc.perform(post(PATH, headedBranch.getBranchId())
                        .header("Authorization", "Bearer " + accessToken(headMemberId, SystemRoles.BRANCH_HEAD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.branchId").value(headedBranch.getBranchId()))
                .andExpect(jsonPath("$.data.clanId").value(CLAN));
    }

    @Test
    @DisplayName("403: BRANCH_HEAD authenticated by a signed JWT cannot create outside their branch")
    void branchHead_SignedJwt_ForeignBranch_Returns403() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header("Authorization", "Bearer " + accessToken(headMemberId, SystemRoles.BRANCH_HEAD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Signs a token the way {@code POST /auth/login} does, so the request travels through
     * {@link com.familyfund.core.security.JwtAuthenticationFilter}: the caller's member profile must come
     * from the {@code memberId} claim, not from the test-only {@link Principal} used above.
     */
    private String accessToken(String memberId, String role) {
        return jwtTokenProvider.generateAccessToken(
                "ACC_CTRL_IT", CLAN, memberId, "Truong Chi Mot", Set.of(role));
    }

    // ---------------------------------------------------------------- 403 Forbidden

    @Test
    @DisplayName("403: ROLE_MEMBER is blocked by @PreAuthorize and nothing is persisted")
    void memberRole_Returns403() throws Exception {
        long before = memberRepository.count();

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.MEMBER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(before, memberRepository.count());
    }

    @Test
    @DisplayName("403: BRANCH_HEAD creating outside their branch is denied and nothing is persisted")
    void branchHead_OtherBranch_Returns403() throws Exception {
        long before = memberRepository.count();

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(headMemberId, SystemRoles.BRANCH_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(before, memberRepository.count());
    }

    @Test
    @DisplayName("401: an anonymous caller cannot create a member")
    void anonymous_Returns401() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---------------------------------------------------------------- 400 Bad Request

    @Test
    @DisplayName("400: blank fullName is rejected by Bean Validation")
    void blankFullName_Returns400() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("Nguyen Thi Ba", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    @DisplayName("400: gender outside the CHECK list is rejected")
    void invalidGender_Returns400() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"Female\"", "\"female\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.gender").exists());
    }

    @Test
    @DisplayName("400: generation below 1 and a future birth date are rejected")
    void invalidGenerationAndFutureBirthDate_Returns400() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Nguyen Thi Ba",
                                    "dateOfBirth": "%s",
                                    "gender": "Female",
                                    "generation": 0
                                }
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.generation").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    @DisplayName("400: a branchId longer than VARCHAR(20) is rejected before any lookup")
    void tooLongBranchId_Returns400() throws Exception {
        mockMvc.perform(post(PATH, "B".repeat(21))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("400: generation lower than the branch generation level is rejected")
    void generationBelowBranchLevel_Returns400() throws Exception {
        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"generation\": 3", "\"generation\": 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---------------------------------------------------------------- 404 / 409

    @Test
    @DisplayName("404: unknown branch id")
    void unknownBranch_Returns404() throws Exception {
        mockMvc.perform(post(PATH, "BR_DOES_NOT_EXIST")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("404: a branch of another clan is invisible under the current tenant")
    void foreignClanBranch_Returns404() throws Exception {
        mockMvc.perform(post(PATH, "BR_CTRL_FOREIGN")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(null, SystemRoles.CLAN_HEAD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("409: the same profile twice in the clan is rejected and stored only once")
    void duplicateProfile_Returns409() throws Exception {
        Authentication clanHead = auth(null, SystemRoles.CLAN_HEAD);

        mockMvc.perform(post(PATH, otherBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(clanHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated());

        long after = memberRepository.count();

        // Same profile, different branch and different case: still the same person for this clan.
        String duplicate = VALID_PAYLOAD.replace("Nguyen Thi Ba", "NGUYEN THI BA")
                .replace("2000-01-01", "2000-01-01");
        mockMvc.perform(post(PATH, headedBranch.getBranchId())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(clanHead))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(after, memberRepository.count());
        assertEquals(1, memberRepository.findByBranch_ClanId(CLAN).stream()
                .filter(m -> "Nguyen Thi Ba".equalsIgnoreCase(m.getFullName()))
                .count());
    }
}
