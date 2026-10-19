package com.familyfund.genealogy.internal.validator;

import com.familyfund.core.exception.BranchCycleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import lombok.RequiredArgsConstructor;
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
     * Thuật toán duyệt ngược từ nhánh cha được đề xuất lên nhánh gốc để ngăn chặn vòng lặp cha - con khép kín,
     * đồng thời cô lập dữ liệu theo ClanId (Multi-tenancy).
     *
     * @param clanId                 Mã dòng họ hiện tại (bắt buộc)
     * @param currentBranchId        ID nhánh hiện tại (null nếu là nhánh mới chưa tạo)
     * @param proposedParentBranchId ID nhánh cha được đề xuất làm cấp trên
     * @throws BranchCycleException khi phát hiện chu trình phả hệ
     */
    public void kiemTraChuTrinh(String clanId, String currentBranchId, String proposedParentBranchId) {
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
     * Validate generation level constraint between parent and child.
     * Requires childGeneration > parentGeneration.
     *
     * @param parentGenerationLevel Generation level of parent branch
     * @param childGenerationLevel  Generation level of child branch
     * @throws InvalidGenerationException when child generation is less than or equal to parent generation
     */
    public void kiemTraDoiThu(Integer parentGenerationLevel, Integer childGenerationLevel) {
        if (parentGenerationLevel != null && childGenerationLevel != null) {
            if (childGenerationLevel <= parentGenerationLevel) {
                throw new InvalidGenerationException(
                        "Generation level mismatch: Child branch generation (" + childGenerationLevel +
                                ") must be greater than parent branch generation (" + parentGenerationLevel + ")"
                );
            }
        }
    }
}
