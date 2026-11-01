package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenealogyQueryServiceImplTest {

    private static final String CLAN_A = "CLAN_01";
    private static final String CLAN_B = "CLAN_02";

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private ClanRepository clanRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private GenealogyQueryServiceImpl genealogyQueryService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("existsBranch with blank or null branchId should return false immediately")
    void testExistsBranch_BlankOrNull_ReturnsFalse() {
        assertFalse(genealogyQueryService.existsBranch(null));
        assertFalse(genealogyQueryService.existsBranch("   "));
        verifyNoInteractions(branchRepository);
    }

    @Test
    @DisplayName("existsBranch using current tenant context should verify branch against current clan")
    void testExistsBranch_CurrentTenant_Success() {
        when(branchRepository.existsByClanIdAndBranchId(CLAN_A, "BR_001")).thenReturn(true);
        when(branchRepository.existsByClanIdAndBranchId(CLAN_A, "BR_CLAN_B_001")).thenReturn(false);

        assertTrue(genealogyQueryService.existsBranch("BR_001"));
        assertFalse(genealogyQueryService.existsBranch("BR_CLAN_B_001"));

        verify(branchRepository).existsByClanIdAndBranchId(CLAN_A, "BR_001");
        verify(branchRepository).existsByClanIdAndBranchId(CLAN_A, "BR_CLAN_B_001");
    }

    @Test
    @DisplayName("existsBranch with explicit clanId should verify branch against that specific clan")
    void testExistsBranch_ExplicitClanId_Success() {
        when(branchRepository.existsByClanIdAndBranchId(CLAN_B, "BR_002")).thenReturn(true);

        assertTrue(genealogyQueryService.existsBranch(CLAN_B, "BR_002"));
        verify(branchRepository).existsByClanIdAndBranchId(CLAN_B, "BR_002");
    }

    @Test
    @DisplayName("getClanName should return clan display name when clan exists")
    void testGetClanName_Success() {
        Clan clan = Clan.builder().clanId(CLAN_A).clanName("Dòng họ Nguyễn").build();
        when(clanRepository.findById(CLAN_A)).thenReturn(Optional.of(clan));

        Optional<String> name = genealogyQueryService.getClanName(CLAN_A);
        assertTrue(name.isPresent());
        assertEquals("Dòng họ Nguyễn", name.get());

        assertTrue(genealogyQueryService.getClanName(null).isEmpty());
        assertTrue(genealogyQueryService.getClanName("   ").isEmpty());
    }

    @Test
    @DisplayName("getBranchName should return branch display name when branch exists")
    void testGetBranchName_Success() {
        FamilyBranch branch = FamilyBranch.builder().branchId("BR_01").branchName("Chi Trưởng").build();
        when(branchRepository.findByClanIdAndBranchId(CLAN_A, "BR_01")).thenReturn(Optional.of(branch));

        Optional<String> name = genealogyQueryService.getBranchName(CLAN_A, "BR_01");
        assertTrue(name.isPresent());
        assertEquals("Chi Trưởng", name.get());

        assertTrue(genealogyQueryService.getBranchName(null, "BR_01").isEmpty());
        assertTrue(genealogyQueryService.getBranchName(CLAN_A, null).isEmpty());
    }

    @Test
    @DisplayName("getOnboardingBranchTree should build sanitized tree starting from clan root")
    void testGetOnboardingBranchTree_RootTree_Success() {
        Clan clan = Clan.builder().clanId(CLAN_A).clanName("Dòng họ Nguyễn").build();
        when(clanRepository.findById(CLAN_A)).thenReturn(Optional.of(clan));

        Member repRoot = Member.builder().memberId("MEM_01").fullName("Nguyễn Văn Tổ").build();
        FamilyBranch rootBranch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanId(CLAN_A)
                .branchName("Chi Gốc")
                .generationLevel(1)
                .representative(repRoot)
                .build();

        Member repChild = Member.builder().memberId("MEM_02").fullName("Nguyễn Văn Hai").build();
        FamilyBranch childBranch = FamilyBranch.builder()
                .branchId("BR_CHILD")
                .clanId(CLAN_A)
                .branchName("Chi Đệ Nhị")
                .generationLevel(2)
                .parentBranch(rootBranch)
                .representative(repChild)
                .build();

        when(branchRepository.findAllByClanIdWithRepresentative(CLAN_A)).thenReturn(List.of(rootBranch, childBranch));
        when(memberRepository.countMembersByBranchForClan(CLAN_A)).thenReturn(Collections.emptyList());

        OnboardingBranchTreeNode tree = genealogyQueryService.getOnboardingBranchTree(CLAN_A, null);

        assertNotNull(tree);
        assertEquals("BR_ROOT", tree.branchId());
        assertEquals("Dòng họ Nguyễn", tree.clanName());
        assertEquals("Chi Gốc", tree.branchName());
        assertEquals("Nguyễn Văn Tổ", tree.representativeName());
        assertEquals(1, tree.children().size());

        OnboardingBranchTreeNode childNode = tree.children().get(0);
        assertEquals("BR_CHILD", childNode.branchId());
        assertEquals("Chi Đệ Nhị", childNode.branchName());
        assertEquals("Nguyễn Văn Hai", childNode.representativeName());
    }

    @Test
    @DisplayName("getOnboardingBranchTree should filter and root tree at targetBranchId if specified")
    void testGetOnboardingBranchTree_TargetBranch_Success() {
        Clan clan = Clan.builder().clanId(CLAN_A).clanName("Dòng họ Nguyễn").build();
        when(clanRepository.findById(CLAN_A)).thenReturn(Optional.of(clan));

        FamilyBranch rootBranch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanId(CLAN_A)
                .branchName("Chi Gốc")
                .generationLevel(1)
                .build();

        FamilyBranch targetBranch = FamilyBranch.builder()
                .branchId("BR_TARGET")
                .clanId(CLAN_A)
                .branchName("Chi Mục Tiêu")
                .generationLevel(2)
                .parentBranch(rootBranch)
                .build();

        when(branchRepository.findAllByClanIdWithRepresentative(CLAN_A)).thenReturn(List.of(rootBranch, targetBranch));
        when(memberRepository.countMembersByBranchForClan(CLAN_A)).thenReturn(Collections.emptyList());

        OnboardingBranchTreeNode tree = genealogyQueryService.getOnboardingBranchTree(CLAN_A, "BR_TARGET");

        assertNotNull(tree);
        assertEquals("BR_TARGET", tree.branchId());
        assertEquals("Chi Mục Tiêu", tree.branchName());
        assertTrue(tree.children().isEmpty());
    }

    @Test
    @DisplayName("getOnboardingBranchTree with non-existent clan should throw ResourceNotFoundException")
    void testGetOnboardingBranchTree_ClanNotFound_ThrowsException() {
        when(clanRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                genealogyQueryService.getOnboardingBranchTree("UNKNOWN", null));
    }

    @Test
    @DisplayName("findMemberSummary should return member summary with clanId when found")
    void testFindMemberSummary_Success() {
        FamilyBranch branch = FamilyBranch.builder()
                .branchId("BR_001")
                .clanId(CLAN_A)
                .build();
        Member member = Member.builder()
                .memberId("MEM_001")
                .fullName("Nguyễn Văn An")
                .branch(branch)
                .build();

        when(memberRepository.findById("MEM_001")).thenReturn(Optional.of(member));

        Optional<MemberSummaryDto> result = genealogyQueryService.findMemberSummary("MEM_001");

        assertThat(result).isPresent();
        assertThat(result.get().memberId()).isEqualTo("MEM_001");
        assertThat(result.get().fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(result.get().clanId()).isEqualTo(CLAN_A);
        assertThat(result.get().branchId()).isEqualTo("BR_001");
    }

    @Test
    @DisplayName("findMemberSummary with blank or non-existent memberId should return empty Optional")
    void testFindMemberSummary_NotFound() {
        assertThat(genealogyQueryService.findMemberSummary(null)).isEmpty();
        assertThat(genealogyQueryService.findMemberSummary("   ")).isEmpty();

        when(memberRepository.findById("MEM_999")).thenReturn(Optional.empty());
        assertThat(genealogyQueryService.findMemberSummary("MEM_999")).isEmpty();
    }

    @Test
    @DisplayName("getMemberIdsByBranch returns active living members and excludes placeholders and deceased")
    void testGetMemberIdsByBranch_Success() {
        Member livingMember1 = Member.builder()
                .memberId("MEM_001")
                .status("Living")
                .isPlaceholder(false)
                .build();
        Member livingMember2 = Member.builder()
                .memberId("MEM_002")
                .status(null)
                .isPlaceholder(null)
                .build();
        Member deceasedMember = Member.builder()
                .memberId("MEM_003")
                .status("Deceased")
                .isPlaceholder(false)
                .build();
        Member placeholderMember = Member.builder()
                .memberId("MEM_004")
                .status("Living")
                .isPlaceholder(true)
                .build();

        when(memberRepository.findByBranch_ClanIdAndBranch_BranchId(CLAN_A, "BR_001"))
                .thenReturn(List.of(livingMember1, livingMember2, deceasedMember, placeholderMember));

        List<String> memberIds = genealogyQueryService.getMemberIdsByBranch(CLAN_A, "BR_001");

        assertThat(memberIds).containsExactly("MEM_001", "MEM_002");
    }

    @Test
    @DisplayName("getMemberIdsByBranch with null or blank clanId/branchId returns empty list")
    void testGetMemberIdsByBranch_BlankInputs() {
        assertThat(genealogyQueryService.getMemberIdsByBranch(null, "BR_001")).isEmpty();
        assertThat(genealogyQueryService.getMemberIdsByBranch("   ", "BR_001")).isEmpty();
        assertThat(genealogyQueryService.getMemberIdsByBranch(CLAN_A, null)).isEmpty();
        assertThat(genealogyQueryService.getMemberIdsByBranch(CLAN_A, "   ")).isEmpty();
    }
}
