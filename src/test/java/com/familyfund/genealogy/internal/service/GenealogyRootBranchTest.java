package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.dto.request.UpdateRootBranchRequest;
import com.familyfund.genealogy.internal.dto.response.BranchDetailResponse;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.mapper.BranchMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyRootBranchTest {

    @Mock
    private FamilyBranchRepository branchRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClanRepository clanRepository;

    @Spy
    private BranchMapper branchMapper = Mappers.getMapper(BranchMapper.class);

    @InjectMocks
    private GenealogyServiceImpl genealogyService;

    private FamilyBranch mockRootBranch;
    private Clan mockClan;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        mockClan = Clan.builder()
                .clanId("CLAN_DEFAULT")
                .clanName("Default Clan")
                .build();

        mockRootBranch = FamilyBranch.builder()
                .branchId("BR_ROOT")
                .clanId("CLAN_DEFAULT")
                .clanName("Default Clan")
                .branchName("Default Root Branch")
                .generationLevel(1)
                .parentBranch(null)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Update root branch successfully when root branch exists")
    void testUpdateRootBranch_Success_WhenRootExists() {
        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Nguyen Clan")
                .branchName("Dai Ton Branch")
                .description("First branch originating from Co Am village")
                .build();

        when(clanRepository.findById("CLAN_DEFAULT")).thenReturn(Optional.of(mockClan));
        when(branchRepository.findByClanIdAndParentBranchIsNull("CLAN_DEFAULT")).thenReturn(Optional.of(mockRootBranch));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.updateRootBranch(request);

        assertNotNull(response);
        assertEquals("Nguyen Clan", response.clanName());
        assertEquals("Dai Ton Branch", response.branchName());
        assertEquals(1, response.generationLevel());
        assertNull(response.parentBranchId());
        verify(branchRepository, times(1)).save(mockRootBranch);
    }

    @Test
    @DisplayName("Update root branch with explicit clanId via TenantContext")
    void testUpdateRootBranch_WithCustomClanId() {
        TenantContext.setClanId("CLAN_TRAN");
        try {
            UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                    .clanName("Tran Clan")
                    .branchName("Root Branch Tran")
                    .build();

            Clan tranClan = Clan.builder()
                    .clanId("CLAN_TRAN")
                    .clanName("Tran Clan")
                    .build();

            FamilyBranch tranRoot = FamilyBranch.builder()
                    .branchId("BR_TRAN_ROOT")
                    .clanId("CLAN_TRAN")
                    .clanName("Tran Clan")
                    .branchName("Root Branch Tran")
                    .generationLevel(1)
                    .parentBranch(null)
                    .build();

            when(clanRepository.findById("CLAN_TRAN")).thenReturn(Optional.of(tranClan));
            when(branchRepository.findByClanIdAndParentBranchIsNull("CLAN_TRAN")).thenReturn(Optional.of(tranRoot));
            when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

            BranchDetailResponse response = genealogyService.updateRootBranch(request);

            assertNotNull(response);
            assertEquals("CLAN_TRAN", response.clanId());
            assertEquals("Tran Clan", response.clanName());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Update root branch throws ResourceNotFoundException when Clan does not exist")
    void testUpdateRootBranch_ClanNotFound_ThrowsException() {
        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Unknown Clan")
                .branchName("Root Branch")
                .build();

        when(clanRepository.findById("CLAN_DEFAULT")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.updateRootBranch(request));
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Update root branch successfully with representative/ancestor")
    void testUpdateRootBranch_Success_WithRepresentative() {
        Member mockRep = Member.builder()
                .memberId("MEM_001")
                .fullName("Grand Ancestor Nguyen Quy Cong")
                .generation(1)
                .gender("Male")
                .branch(mockRootBranch)
                .build();

        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Nguyen Clan")
                .branchName("Dai Ton Branch")
                .representativeId("MEM_001")
                .build();

        when(clanRepository.findById("CLAN_DEFAULT")).thenReturn(Optional.of(mockClan));
        when(branchRepository.findByClanIdAndParentBranchIsNull("CLAN_DEFAULT")).thenReturn(Optional.of(mockRootBranch));
        when(memberRepository.findByBranch_ClanIdAndMemberId("CLAN_DEFAULT", "MEM_001")).thenReturn(Optional.of(mockRep));
        when(branchRepository.save(any(FamilyBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BranchDetailResponse response = genealogyService.updateRootBranch(request);

        assertNotNull(response);
        assertEquals("MEM_001", response.representativeId());
        assertEquals("Grand Ancestor Nguyen Quy Cong", response.representativeName());
        verify(memberRepository, times(1)).findByBranch_ClanIdAndMemberId("CLAN_DEFAULT", "MEM_001");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when representative ID is not found")
    void testUpdateRootBranch_RepresentativeNotFound_ThrowsException() {
        UpdateRootBranchRequest request = UpdateRootBranchRequest.builder()
                .clanName("Nguyen Clan")
                .branchName("Dai Ton Branch")
                .representativeId("MEM_NON_EXIST")
                .build();

        when(clanRepository.findById("CLAN_DEFAULT")).thenReturn(Optional.of(mockClan));
        when(branchRepository.findByClanIdAndParentBranchIsNull("CLAN_DEFAULT")).thenReturn(Optional.of(mockRootBranch));
        when(memberRepository.findByBranch_ClanIdAndMemberId("CLAN_DEFAULT", "MEM_NON_EXIST")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.updateRootBranch(request));
        verify(branchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get root branch successfully")
    void testGetRootBranch_Success() {
        when(clanRepository.existsById("CLAN_DEFAULT")).thenReturn(true);
        when(branchRepository.findByClanIdAndParentBranchIsNull("CLAN_DEFAULT")).thenReturn(Optional.of(mockRootBranch));

        BranchDetailResponse response = genealogyService.getRootBranch();

        assertNotNull(response);
        assertEquals("BR_ROOT", response.branchId());
        assertEquals(1, response.generationLevel());
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when Clan does not exist on getRootBranch")
    void testGetRootBranch_ClanNotFound_ThrowsException() {
        when(clanRepository.existsById("CLAN_DEFAULT")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.getRootBranch());
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when root branch does not exist")
    void testGetRootBranch_NotFound_ThrowsException() {
        when(clanRepository.existsById("CLAN_DEFAULT")).thenReturn(true);
        when(branchRepository.findByClanIdAndParentBranchIsNull("CLAN_DEFAULT")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> genealogyService.getRootBranch());
    }
}
