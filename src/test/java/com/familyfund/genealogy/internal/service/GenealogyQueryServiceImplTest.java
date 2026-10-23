package com.familyfund.genealogy.internal.service;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyQueryServiceImplTest {

    private static final String CLAN_A = "CLAN_01";
    private static final String CLAN_B = "CLAN_02";

    @Mock
    private FamilyBranchRepository branchRepository;

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
}
