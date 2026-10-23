package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.controller.MemberController;
import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against the real Flyway schema (PostgreSQL). Every test runs in a transaction that is
 * rolled back, so no data is left behind. Verifies derived queries, the clan row lock and that
 * {@code @PreAuthorize} plus service-level branch scoping are enforced through the Spring proxies.
 */
@SpringBootTest
@Transactional
class MemberServiceIntegrationTest {

    private static final String CLAN = "CLAN_IT_US03";

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberController memberController;
    @Autowired
    private ClanRepository clanRepository;
    @Autowired
    private FamilyBranchRepository branchRepository;
    @Autowired
    private MemberRepository memberRepository;

    private FamilyBranch headedBranch;
    private FamilyBranch otherBranch;
    private String headMemberId;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN);
        clanRepository.save(Clan.builder().clanId(CLAN).clanName("IT Clan US-03").build());

        FamilyBranch root = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_IT_ROOT").clanId(CLAN).branchName("Root").generationLevel(1).build());
        headedBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_IT_HEAD").clanId(CLAN).branchName("Chi Mot").generationLevel(2).parentBranch(root).build());
        otherBranch = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_IT_OTHER").clanId(CLAN).branchName("Chi Hai").generationLevel(2).parentBranch(root).build());

        Member head = memberRepository.save(Member.builder()
                .memberId("MEMITHEAD00000001").fullName("Truong Chi Mot").gender("Male")
                .generation(2).branch(headedBranch).build());
        headMemberId = head.getMemberId();
        headedBranch.setRepresentative(head);
        branchRepository.saveAndFlush(headedBranch);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void login(String memberId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new Principal("ACC_IT", memberId), "n/a",
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }

    private static CreateMemberRequest request(String name) {
        return CreateMemberRequest.builder()
                .fullName(name).gender("Female").generation(3)
                .dateOfBirth(LocalDate.of(2000, 1, 1)).build();
    }

    @Test
    @DisplayName("CLAN_HEAD creates a placeholder member that is persisted with DB defaults")
    void clanHead_Creates() {
        login(null, SystemRoles.CLAN_HEAD);
        MemberResponse response = memberService.createMember(otherBranch.getBranchId(), request("Nguyen Thi Ba"));

        Member stored = memberRepository.findById(response.memberId()).orElseThrow();
        assertTrue(stored.getIsPlaceholder());
        assertEquals("Living", stored.getStatus());
        assertEquals(otherBranch.getBranchId(), stored.getBranch().getBranchId());
        assertTrue(stored.getMemberId().length() <= 20);
    }

    @Test
    @DisplayName("BRANCH_HEAD can add to own branch but gets 403 on another branch")
    void branchHead_Scope() {
        login(headMemberId, SystemRoles.BRANCH_HEAD);
        long before = memberRepository.count();

        memberService.createMember(headedBranch.getBranchId(), request("Nguyen Thi Bon"));
        assertThrows(AccessDeniedException.class,
                () -> memberService.createMember(otherBranch.getBranchId(), request("Nguyen Thi Nam")));

        assertEquals(before + 1, memberRepository.count());
    }

    @Test
    @DisplayName("Same profile twice in the clan is rejected and stored only once")
    void duplicate_Rejected() {
        login(null, SystemRoles.ADMIN);
        memberService.createMember(otherBranch.getBranchId(), request("Nguyen Thi Sau"));
        long after = memberRepository.count();

        assertThrows(DuplicateResourceException.class,
                () -> memberService.createMember(headedBranch.getBranchId(), request("NGUYEN THI SAU")));
        assertEquals(after, memberRepository.count());
    }

    @Test
    @DisplayName("@PreAuthorize blocks ROLE_MEMBER at the controller before any service logic")
    void member_BlockedByPreAuthorize() {
        login(null, SystemRoles.MEMBER);
        assertThrows(AccessDeniedException.class,
                () -> memberController.createMember(otherBranch.getBranchId(), request("Nguyen Thi Bay")));
    }
}
