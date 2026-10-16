package com.familyfund.modules.genealogy.service.impl;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.dto.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.BranchTreeNodeResponse;
import com.familyfund.modules.genealogy.dto.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.UpdateBranchRequest;
import com.familyfund.modules.genealogy.dto.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.entity.FamilyBranch;
import com.familyfund.modules.genealogy.entity.Member;
import com.familyfund.modules.genealogy.repository.FamilyBranchRepository;
import com.familyfund.modules.genealogy.repository.MemberRepository;
import com.familyfund.modules.genealogy.service.GenealogyService;
import com.familyfund.modules.genealogy.validator.GenealogyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenealogyServiceImpl implements GenealogyService {

    private final FamilyBranchRepository branchRepository;
    private final MemberRepository memberRepository;
    private final GenealogyValidator genealogyValidator;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse createChildBranch(CreateChildBranchRequest request) {
        FamilyBranch parent = branchRepository.findById(request.getParentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhánh cha có ID: " + request.getParentBranchId()
                ));

        // Kiểm tra chống chu trình và kiểm tra đời thứ
        genealogyValidator.kiemTraChuTrinh(null, request.getParentBranchId());
        int childGenerationLevel = parent.getGenerationLevel() + 1;
        genealogyValidator.kiemTraDoiThu(parent.getGenerationLevel(), childGenerationLevel);

        FamilyBranch childBranch = FamilyBranch.builder()
                .branchId("BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .clanName(parent.getClanName())
                .branchName(request.getBranchName())
                .generationLevel(childGenerationLevel)
                .parentBranch(parent)
                .description(request.getDescription())
                .build();

        if (request.getRepresentativeId() != null && !request.getRepresentativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.getRepresentativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy người đại diện có ID: " + request.getRepresentativeId()
                    ));
            childBranch.setRepresentative(rep);
        }

        FamilyBranch saved = branchRepository.save(childBranch);
        return BranchDetailResponse.fromEntity(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse updateBranch(String branchId, UpdateBranchRequest request) {
        FamilyBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhánh có ID: " + branchId));

        // Nếu chuyển nhánh cha
        if (request.getParentBranchId() != null) {
            String currentParentId = branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null;
            if (!request.getParentBranchId().equals(currentParentId)) {
                // Không cho phép gán cha cho nhánh gốc
                if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
                    throw new BusinessRuleException("Không thể gán nhánh cha cho Nhánh Gốc của dòng họ");
                }

                // Kiểm tra chống chu trình khép kín
                genealogyValidator.kiemTraChuTrinh(branchId, request.getParentBranchId());

                FamilyBranch newParent = branchRepository.findById(request.getParentBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy nhánh cha mới có ID: " + request.getParentBranchId()
                        ));

                int newGenLevel = newParent.getGenerationLevel() + 1;
                genealogyValidator.kiemTraDoiThu(newParent.getGenerationLevel(), newGenLevel);

                int delta = newGenLevel - branch.getGenerationLevel();
                branch.setParentBranch(newParent);
                branch.setGenerationLevel(newGenLevel);

                // Cập nhật đời thứ đệ quy cho toàn bộ cây con cháu bên dưới
                if (delta != 0) {
                    cascadeUpdateGenerationLevel(branch, delta);
                }
            }
        }

        branch.setBranchName(request.getBranchName());
        branch.setDescription(request.getDescription());

        if (request.getRepresentativeId() != null && !request.getRepresentativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.getRepresentativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy người đại diện có ID: " + request.getRepresentativeId()
                    ));
            branch.setRepresentative(rep);
        } else {
            branch.setRepresentative(null);
        }

        FamilyBranch saved = branchRepository.save(branch);
        return BranchDetailResponse.fromEntity(saved);
    }

    private void cascadeUpdateGenerationLevel(FamilyBranch current, int delta) {
        List<FamilyBranch> children = branchRepository.findByParentBranch_BranchId(current.getBranchId());
        for (FamilyBranch child : children) {
            child.setGenerationLevel(child.getGenerationLevel() + delta);
            branchRepository.save(child);
            cascadeUpdateGenerationLevel(child, delta);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BranchTreeNodeResponse getBranchTree() {
        FamilyBranch root = branchRepository.findByParentBranchIsNull()
                .orElse(null);
        if (root == null) {
            return null;
        }
        return buildTreeNode(root);
    }

    private BranchTreeNodeResponse buildTreeNode(FamilyBranch branch) {
        int memberCount = memberRepository.findByBranch_BranchId(branch.getBranchId()).size();
        List<FamilyBranch> children = branchRepository.findByParentBranch_BranchId(branch.getBranchId());
        List<BranchTreeNodeResponse> childNodes = children.stream()
                .map(this::buildTreeNode)
                .toList();

        return BranchTreeNodeResponse.builder()
                .branchId(branch.getBranchId())
                .branchName(branch.getBranchName())
                .clanName(branch.getClanName())
                .generationLevel(branch.getGenerationLevel())
                .parentBranchId(branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null)
                .representativeId(branch.getRepresentative() != null ? branch.getRepresentative().getMemberId() : null)
                .representativeName(branch.getRepresentative() != null ? branch.getRepresentative().getFullName() : null)
                .memberCount(memberCount)
                .children(childNodes)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBranch(String branchId) {
        FamilyBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhánh có ID: " + branchId));

        if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
            throw new BusinessRuleException("Không được phép xóa Nhánh Gốc của dòng họ");
        }

        if (branchRepository.existsByParentBranch_BranchId(branchId)) {
            throw new BusinessRuleException(
                    "Không thể xóa nhánh đang có nhánh con trực thuộc. Vui lòng chuyển hoặc xóa các nhánh con trước."
            );
        }

        if (memberRepository.existsByBranch_BranchId(branchId)) {
            throw new BusinessRuleException(
                    "Không thể xóa nhánh đang có thành viên gia đình trực thuộc. Vui lòng chuyển các thành viên sang nhánh khác trước."
            );
        }

        branchRepository.delete(branch);
    }
}
