package com.familyfund.genealogy.internal.service;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyQueryServiceImplTest {

    private static final String CLAN_A = "CLAN_01";
    private static final String CLAN_B = "CLAN_02";

    @Mock
    private FamilyBranchRepository branchRepository;

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
}
