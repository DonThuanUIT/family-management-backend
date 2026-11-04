package com.familyfund.genealogy.internal.service;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.controller.MemberController;
import com.familyfund.genealogy.internal.dto.request.MemberSearchCriteria;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MemberSearchIntegrationTest {

    private static final String CLAN_A = "CLAN_SEARCH_A";
    private static final String CLAN_B = "CLAN_SEARCH_B";

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

    private FamilyBranch branchA1;
    private FamilyBranch branchA2;
    private Member member1;
    private Member member2;
    private Member member3;

    @BeforeEach
    void setupData() {
        TenantContext.setClanId(CLAN_A);
        clanRepository.save(Clan.builder().clanId(CLAN_A).clanName("Nguyen Clan Search").build());
        clanRepository.save(Clan.builder().clanId(CLAN_B).clanName("Tran Clan Search").build());

        FamilyBranch rootA = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_SCH_ROOT").clanId(CLAN_A).branchName("Chi Dai Ton").generationLevel(1).build());

        branchA1 = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_SCH_01").clanId(CLAN_A).branchName("Chi Nhanh Dong").generationLevel(2).parentBranch(rootA).build());

        branchA2 = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_SCH_02").clanId(CLAN_A).branchName("Chi Nhanh Tay").generationLevel(3).parentBranch(branchA1).build());

        FamilyBranch branchB = branchRepository.save(FamilyBranch.builder()
                .branchId("BR_SCH_B").clanId(CLAN_B).branchName("Chi Tran").generationLevel(1).build());

        member1 = memberRepository.save(Member.builder()
                .memberId("MEMSCH0000000001")
                .fullName("Nguyễn Văn Bình")
                .gender("Male")
                .generation(2)
                .status("Living")
                .dateOfBirth(LocalDate.of(1985, 5, 20))
                .branch(branchA1)
                .build());

        member2 = memberRepository.save(Member.builder()
                .memberId("MEMSCH0000000002")
                .fullName("Nguyễn Thị Mai")
                .gender("Female")
                .generation(3)
                .status("Living")
                .dateOfBirth(LocalDate.of(1992, 11, 15))
                .branch(branchA2)
                .build());

        member3 = memberRepository.save(Member.builder()
                .memberId("MEMSCH0000000003")
                .fullName("Trần Đình Trọng")
                .gender("Male")
                .generation(2)
                .status("Deceased")
                .branch(branchA1)
                .build());

        // Member belonging to Clan B (Cross-tenant security check)
        memberRepository.save(Member.builder()
                .memberId("MEMSCH0000000009")
                .fullName("Nguyễn Văn Bình Ở Dòng Họ Khác")
                .gender("Male")
                .generation(2)
                .status("Living")
                .branch(branchB)
                .build());

        // Authenticate with ROLE_MEMBER
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user_search_test", "n/a", List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("US-30: Search unaccented - Vietnamese without accents matches accented names")
    void search_UnaccentedVietnameseName() {
        // Search "nguyen van binh" (no accents) -> should find "Nguyễn Văn Bình"
        MemberSearchCriteria criteria = MemberSearchCriteria.of("nguyen van binh", null, null, null, null, null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(1, results.size());
        assertEquals(member1.getMemberId(), results.get(0).memberId());
        assertEquals("Nguyễn Văn Bình", results.get(0).fullName());
    }

    @Test
    @DisplayName("US-30: Search unaccented - Partial word search works")
    void search_PartialWordSearch() {
        // Search "binh" -> matches "Nguyễn Văn Bình"
        MemberSearchCriteria criteria = MemberSearchCriteria.of("binh", null, null, null, null, null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(1, results.size());
        assertEquals(member1.getMemberId(), results.get(0).memberId());

        // Search "mai" -> matches "Nguyễn Thị Mai"
        criteria = MemberSearchCriteria.of("mai", null, null, null, null, null);
        results = memberService.searchMembers(criteria);

        assertEquals(1, results.size());
        assertEquals(member2.getMemberId(), results.get(0).memberId());
    }

    @Test
    @DisplayName("US-30: Search by member ID partial/exact match")
    void search_ByMemberId() {
        MemberSearchCriteria criteria = MemberSearchCriteria.of(null, "MEMSCH0000000002", null, null, null, null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(1, results.size());
        assertEquals("Nguyễn Thị Mai", results.get(0).fullName());
    }

    @Test
    @DisplayName("US-30: Search by generation filter")
    void search_ByGeneration() {
        // Generation 2 in Clan A: member1 and member3
        MemberSearchCriteria criteria = MemberSearchCriteria.of(null, null, null, 2, null, null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(m -> m.memberId().equals(member1.getMemberId())));
        assertTrue(results.stream().anyMatch(m -> m.memberId().equals(member3.getMemberId())));
    }

    @Test
    @DisplayName("US-30: Search by branch filter")
    void search_ByBranchId() {
        MemberSearchCriteria criteria = MemberSearchCriteria.of(null, null, null, null, branchA2.getBranchId(), null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(1, results.size());
        assertEquals(member2.getMemberId(), results.get(0).memberId());
    }

    @Test
    @DisplayName("US-30: Search by keyword matching branch name")
    void search_ByKeywordMatchingBranchName() {
        // Keyword "Nhanh Dong" should match member1 and member3 whose branch is "Chi Nhanh Dong"
        MemberSearchCriteria criteria = MemberSearchCriteria.of("Nhanh Dong", null, null, null, null, null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("US-30: Multi-tenancy - Cross-tenant records in Clan B must never be returned")
    void search_MultiTenantIsolation() {
        // Search "Bình" in Clan A -> MUST NOT return the member in Clan B!
        MemberSearchCriteria criteria = MemberSearchCriteria.of("Bình", null, null, null, null, null);
        List<MemberResponse> results = memberService.searchMembers(criteria);

        assertEquals(1, results.size());
        assertEquals(CLAN_A, results.get(0).clanId());
        assertNotEquals("MEMSCH0000000009", results.get(0).memberId());
    }

    @Test
    @DisplayName("US-30: Controller endpoint GET /api/v1/members/search returns ApiResponse")
    void controller_SearchEndpoint() {
        var response = memberController.searchMembers("binh", null, null, null, null, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("Nguyễn Văn Bình", response.getBody().getData().get(0).fullName());
    }
}
