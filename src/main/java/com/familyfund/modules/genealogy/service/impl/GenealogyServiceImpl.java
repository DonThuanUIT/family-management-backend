package com.familyfund.modules.genealogy.service.impl;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.dto.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.entity.FamilyBranch;
import com.familyfund.modules.genealogy.entity.Member;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import com.familyfund.modules.genealogy.repository.MemberRepository;
import com.familyfund.modules.genealogy.service.GenealogyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenealogyServiceImpl implements GenealogyService {

    private final FamilyBranchRepository branchRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public BranchDetailResponse getRootBranch() {
        FamilyBranch rootBranch = branchRepository.findByParentBranchIsNull()
                .orElseThrow(() -> new ResourceNotFoundException("Chưa tìm thấy nhánh gốc trong hệ thống"));
        return BranchDetailResponse.fromEntity(rootBranch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse updateRootBranch(UpdateRootBranchRequest request) {
        FamilyBranch rootBranch = branchRepository.findByParentBranchIsNull()
                .orElseGet(() -> FamilyBranch.builder()
                        .branchId("BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .generationLevel(1)
                        .parentBranch(null)
                        .build());

        // Ràng buộc nghiệp vụ: Nhánh gốc bắt buộc generation_level = 1 và không có nhánh cha
        if (rootBranch.getParentBranch() != null || rootBranch.getGenerationLevel() != 1) {
            throw new BusinessRuleException("Nhánh này không phải là nhánh gốc hợp lệ của dòng họ");
        }

        rootBranch.setClanName(request.getClanName());
        rootBranch.setBranchName(request.getBranchName());
        rootBranch.setDescription(request.getDescription());

        if (request.getRepresentativeId() != null && !request.getRepresentativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.getRepresentativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy thông tin Thủy tổ/Người đại diện có ID: " + request.getRepresentativeId()
                    ));
            rootBranch.setRepresentative(rep);
        } else {
            rootBranch.setRepresentative(null);
        }

        FamilyBranch saved = branchRepository.save(rootBranch);
        return BranchDetailResponse.fromEntity(saved);
    }
}
