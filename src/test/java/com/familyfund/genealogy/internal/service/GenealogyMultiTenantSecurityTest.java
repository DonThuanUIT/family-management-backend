package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.internal.dto.request.CreateChildBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateBranchRequest;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.mapper.BranchMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.validator.GenealogyValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyMultiTenantSecurityTest {

    private static final String CLAN_A = "CLAN_NGUYEN";
    private static final String CLAN_B = "CLAN_TRAN";

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClanRepository clanRepository;

    @Mock
    private GenealogyValidator genealogyValidator;

    @Mock
    private BranchMapper branchMapper;

    @InjectMocks
    private GenealogyServiceImpl genealogyService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Cross-tenant parent assignment in createChildBranch should be rejected with ResourceNotFoundException")
    void testCreateChildBranch_CrossTenantParent_ThrowsResourceNotFoundException() {
        CreateChildBranchRequest request = CreateChildBranchRequest.builder()
                .branchName("Chi Nhanh Moi")
                .parentBranchId("BR_CLAN_B_01")
                .build();

        // In Clan A context, searching for a branch belonging to Clan B must return empty
        when(branchRepository.findByClanIdAndBranchId(CLAN_A, "BR_CLAN_B_01")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                genealogyService.createChildBranch(request)
        );
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cross-tenant reparenting in updateBranch should be rejected with ResourceNotFoundException")
    void testUpdateBranch_CrossTenantReparenting_ThrowsResourceNotFoundException() {
        FamilyBranch myBranch = FamilyBranch.builder()
                .branchId("BR_CLAN_A_01")
                .clanId(CLAN_A)
                .generationLevel(2)
                .build();

        UpdateBranchRequest request = UpdateBranchRequest.builder()
                .branchName("Chi A Doi Cha")
                .parentBranchId("BR_CLAN_B_ROOT")
                .build();

        when(branchRepository.findByClanIdAndBranchId(CLAN_A, "BR_CLAN_A_01")).thenReturn(Optional.of(myBranch));
        // Cross-clan parent lookup in Clan A returns empty
        when(branchRepository.findByClanIdAndBranchId(CLAN_A, "BR_CLAN_B_ROOT")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                genealogyService.updateBranch("BR_CLAN_A_01", request)
        );
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cross-tenant member assignment in assignMemberToBranch should be rejected with ResourceNotFoundException")
    void testAssignMemberToBranch_CrossTenantMember_ThrowsResourceNotFoundException() {
        // Attempt to assign member of Clan B to branch in Clan A
        when(memberRepository.findByBranch_ClanIdAndMemberId(CLAN_A, "MEM_CLAN_B_01")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                genealogyService.assignMemberToBranch("MEM_CLAN_B_01", "BR_CLAN_A_01")
        );
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cross-tenant delete branch should be rejected with ResourceNotFoundException")
    void testDeleteBranch_CrossTenantTarget_ThrowsResourceNotFoundException() {
        // Attempting to delete a branch of Clan B while authenticated as Clan A
        when(branchRepository.findByClanIdAndBranchId(CLAN_A, "BR_CLAN_B_99")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                genealogyService.deleteBranch("BR_CLAN_B_99")
        );
        verify(branchRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Cross-tenant representative assignment should be rejected with ResourceNotFoundException")
    void testCreateChildBranch_CrossTenantRepresentative_ThrowsResourceNotFoundException() {
        FamilyBranch parent = FamilyBranch.builder()
                .branchId("BR_CLAN_A_PARENT")
                .clanId(CLAN_A)
                .clanName("Nguyen Toc")
                .generationLevel(1)
                .build();

        CreateChildBranchRequest request = CreateChildBranchRequest.builder()
                .branchName("Chi Nhanh Moi")
                .parentBranchId("BR_CLAN_A_PARENT")
                .representativeId("MEM_CLAN_B_REP")
                .build();

        when(branchRepository.findByClanIdAndBranchId(CLAN_A, "BR_CLAN_A_PARENT")).thenReturn(Optional.of(parent));
        when(memberRepository.findByBranch_ClanIdAndMemberId(CLAN_A, "MEM_CLAN_B_REP")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                genealogyService.createChildBranch(request)
        );
        verify(branchRepository, never()).save(any());
    }
}
