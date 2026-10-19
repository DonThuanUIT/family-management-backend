package com.familyfund.genealogy.internal.validator;

import com.familyfund.core.exception.BranchCycleException;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenealogyValidatorTest {

    @Mock
    private FamilyBranchRepository branchRepository;

    @InjectMocks
    private GenealogyValidator validator;

    @Test
    @DisplayName("Validation fails when clanId is null or blank -> Throws IllegalArgumentException")
    void testCycle_NullOrBlankClanId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                validator.kiemTraChuTrinh(null, "BR_A", "BR_B")
        );
        assertThrows(IllegalArgumentException.class, () ->
                validator.kiemTraChuTrinh("   ", "BR_A", "BR_B")
        );
    }

    @Test
    @DisplayName("Direct cycle check: Branch points to itself as parent -> Throws BranchCycleException")
    void testCycle_SelfParent_ThrowsBranchCycleException() {
        assertThrows(BranchCycleException.class, () ->
                validator.kiemTraChuTrinh("CLAN_01", "BR_01", "BR_01")
        );
    }

    @Test
    @DisplayName("Direct 2-level cycle check: A is parent of B, changing parent of A to B -> Throws BranchCycleException")
    void testCycle_DirectLoop_ThrowsBranchCycleException() {
        // Hierarchy: A is parent of B
        FamilyBranch branchA = FamilyBranch.builder().branchId("BR_A").clanId("CLAN_01").generationLevel(1).parentBranch(null).build();
        FamilyBranch branchB = FamilyBranch.builder().branchId("BR_B").clanId("CLAN_01").generationLevel(2).parentBranch(branchA).build();

        when(branchRepository.findByClanIdAndBranchId("CLAN_01", "BR_B")).thenReturn(Optional.of(branchB));

        // Attempt to change parent of A to B
        assertThrows(BranchCycleException.class, () ->
                validator.kiemTraChuTrinh("CLAN_01", "BR_A", "BR_B")
        );
    }

    @Test
    @DisplayName("Multi-level indirect cycle check: A -> B -> C -> D, assigning D as parent of A -> Throws BranchCycleException")
    void testCycle_MultiLevelLoop_ThrowsBranchCycleException() {
        // Hierarchy chain: A (gen 1) -> B (gen 2) -> C (gen 3) -> D (gen 4)
        FamilyBranch branchA = FamilyBranch.builder().branchId("BR_A").clanId("CLAN_01").generationLevel(1).parentBranch(null).build();
        FamilyBranch branchB = FamilyBranch.builder().branchId("BR_B").clanId("CLAN_01").generationLevel(2).parentBranch(branchA).build();
        FamilyBranch branchC = FamilyBranch.builder().branchId("BR_C").clanId("CLAN_01").generationLevel(3).parentBranch(branchB).build();
        FamilyBranch branchD = FamilyBranch.builder().branchId("BR_D").clanId("CLAN_01").generationLevel(4).parentBranch(branchC).build();

        when(branchRepository.findByClanIdAndBranchId("CLAN_01", "BR_D")).thenReturn(Optional.of(branchD));
        when(branchRepository.findByClanIdAndBranchId("CLAN_01", "BR_C")).thenReturn(Optional.of(branchC));
        when(branchRepository.findByClanIdAndBranchId("CLAN_01", "BR_B")).thenReturn(Optional.of(branchB));

        // Attempt to assign parent of A to D
        assertThrows(BranchCycleException.class, () ->
                validator.kiemTraChuTrinh("CLAN_01", "BR_A", "BR_D")
        );
    }

    @Test
    @DisplayName("Valid hierarchy without cycles -> Completes without throwing exception")
    void testValidHierarchy_NoCycle() {
        FamilyBranch branchRoot = FamilyBranch.builder().branchId("BR_ROOT").clanId("CLAN_01").generationLevel(1).parentBranch(null).build();
        FamilyBranch branchA = FamilyBranch.builder().branchId("BR_A").clanId("CLAN_01").generationLevel(2).parentBranch(branchRoot).build();

        when(branchRepository.findByClanIdAndBranchId("CLAN_01", "BR_A")).thenReturn(Optional.of(branchA));
        when(branchRepository.findByClanIdAndBranchId("CLAN_01", "BR_ROOT")).thenReturn(Optional.of(branchRoot));

        // Create new child branch BR_NEW under BR_A
        assertDoesNotThrow(() ->
                validator.kiemTraChuTrinh("CLAN_01", "BR_NEW", "BR_A")
        );
    }
}
