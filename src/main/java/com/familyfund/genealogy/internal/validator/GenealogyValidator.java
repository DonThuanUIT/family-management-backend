package com.familyfund.genealogy.internal.validator;

import com.familyfund.core.exception.BranchCycleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class GenealogyValidator {

    private final FamilyBranchRepository branchRepository;

    public GenealogyValidator(FamilyBranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    /**
     * Traverses upward from the proposed parent branch to the root branch to prevent closed parent-child cycles,
     * while enforcing multi-tenant isolation by clanId.
     *
     * @param clanId                 Current clan ID (required)
     * @param currentBranchId        Current branch ID (null if creating a new branch)
     * @param proposedParentBranchId Proposed parent branch ID
     * @throws BranchCycleException when a genealogy cycle is detected
     */
    public void validateNoCycles(String clanId, String currentBranchId, String proposedParentBranchId) {
        if (proposedParentBranchId == null) {
            return;
        }

        if (clanId == null || clanId.isBlank()) {
            throw new IllegalArgumentException("Clan ID must not be null or blank for genealogy cycle validation");
        }

        if (currentBranchId != null && currentBranchId.equals(proposedParentBranchId)) {
            throw new BranchCycleException("A branch cannot be its own parent (branchId: " + currentBranchId + ")");
        }

        String checkId = proposedParentBranchId;
        Set<String> visited = new HashSet<>();

        while (checkId != null) {
            if (currentBranchId != null && checkId.equals(currentBranchId)) {
                throw new BranchCycleException(
                        "Closed genealogy cycle detected: Selected parent branch [" + proposedParentBranchId +
                                "] is a descendant of the current branch [" + currentBranchId + "]!"
                );
            }

            if (!visited.add(checkId)) {
                throw new BranchCycleException("Cycle loop detected in database hierarchy at branch: " + checkId);
            }

            final String targetId = checkId;
            FamilyBranch parent = branchRepository.findByClanIdAndBranchId(clanId, targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent branch not found with ID: " + targetId + " in clan: " + clanId));

            checkId = parent.getParentBranch() != null ? parent.getParentBranch().getBranchId() : null;
        }
    }

    /**
     * Backward-compatible alias for {@link #validateNoCycles(String, String, String)}.
     */
    @Deprecated
    public void kiemTraChuTrinh(String clanId, String currentBranchId, String proposedParentBranchId) {
        validateNoCycles(clanId, currentBranchId, proposedParentBranchId);
    }
}
