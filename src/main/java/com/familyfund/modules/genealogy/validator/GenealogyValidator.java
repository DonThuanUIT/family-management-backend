package com.familyfund.modules.genealogy.validator;

import com.familyfund.core.exception.BranchCycleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.entity.FamilyBranch;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class GenealogyValidator {

    private final FamilyBranchRepository branchRepository;

    /**
     * Thuật toán duyệt ngược từ nhánh cha được đề xuất lên nhánh gốc để ngăn chặn vòng lặp cha - con khép kín.
     *
     * @param currentBranchId        ID nhánh hiện tại (null nếu là nhánh mới chưa tạo)
     * @param proposedParentBranchId ID nhánh cha được đề xuất làm cấp trên
     * @throws BranchCycleException khi phát hiện chu trình phả hệ
     */
    public void kiemTraChuTrinh(String currentBranchId, String proposedParentBranchId) {
        if (proposedParentBranchId == null) {
            return;
        }

        if (currentBranchId != null && currentBranchId.equals(proposedParentBranchId)) {
            throw new BranchCycleException("Một nhánh không thể là cha của chính nó (branchId: " + currentBranchId + ")");
        }

        String checkId = proposedParentBranchId;
        Set<String> visited = new HashSet<>();

        while (checkId != null) {
            if (currentBranchId != null && checkId.equals(currentBranchId)) {
                throw new BranchCycleException(
                        "Phát hiện chu trình phả hệ khép kín: Nhánh cha được chọn [" + proposedParentBranchId +
                                "] là hậu duệ của nhánh hiện tại [" + currentBranchId + "]!"
                );
            }

            if (!visited.add(checkId)) {
                throw new BranchCycleException("Cơ sở dữ liệu phát hiện chu trình vòng lặp tại nhánh: " + checkId);
            }

            final String targetId = checkId;
            FamilyBranch parent = branchRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhánh cha có ID: " + targetId));

            checkId = parent.getParentBranch() != null ? parent.getParentBranch().getBranchId() : null;
        }
    }

    /**
     * Kiểm tra ràng buộc đời thứ giữa cha và con.
     * Bắt buộc doiThu_con > doiThu_cha. Cấm hoàn toàn doiThu_con <= doiThu_cha.
     *
     * @param parentGenerationLevel Đời thứ của nhánh cha
     * @param childGenerationLevel  Đời thứ của nhánh con
     * @throws InvalidGenerationException khi đời con nhỏ hơn hoặc bằng đời cha
     */
    public void kiemTraDoiThu(Integer parentGenerationLevel, Integer childGenerationLevel) {
        if (parentGenerationLevel != null && childGenerationLevel != null) {
            if (childGenerationLevel <= parentGenerationLevel) {
                throw new InvalidGenerationException(
                        "Sai lệch đời thứ: Đời thứ của nhánh con (" + childGenerationLevel +
                                ") phải lớn hơn đời thứ của nhánh cha (" + parentGenerationLevel + ")"
                );
            }
        }
    }
}
