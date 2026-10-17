package com.familyfund.modules.genealogy.service.impl;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.modules.genealogy.dto.request.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.dto.response.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.response.BranchTreeNodeResponse;
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
                .orElseThrow(() -> new ResourceNotFoundException("Root branch not found in the system"));
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

        // Business rule: Root branch must have generation_level = 1 and no parent branch
        if (rootBranch.getParentBranch() != null || rootBranch.getGenerationLevel() != 1) {
            throw new BusinessRuleException("This branch is not a valid root branch of the clan");
        }

        rootBranch.setClanName(request.getClanName());
        rootBranch.setBranchName(request.getBranchName());
        rootBranch.setDescription(request.getDescription());

        if (request.getRepresentativeId() != null && !request.getRepresentativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.getRepresentativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Representative/ancestor not found with ID: " + request.getRepresentativeId()
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
                        "Parent branch not found with ID: " + request.getParentBranchId()
                ));

        // Cycle check and generation level check
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
                            "Representative not found with ID: " + request.getRepresentativeId()
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
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId));

        // If reparenting
        if (request.getParentBranchId() != null) {
            String currentParentId = branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null;
            if (!request.getParentBranchId().equals(currentParentId)) {
                // Cannot assign parent to root branch
                if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
                    throw new BusinessRuleException("Cannot assign parent branch to the root branch of the clan");
                }

                // Check closed cycle
                genealogyValidator.kiemTraChuTrinh(branchId, request.getParentBranchId());

                FamilyBranch newParent = branchRepository.findById(request.getParentBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "New parent branch not found with ID: " + request.getParentBranchId()
                        ));

                int newGenLevel = newParent.getGenerationLevel() + 1;
                genealogyValidator.kiemTraDoiThu(newParent.getGenerationLevel(), newGenLevel);

                int delta = newGenLevel - branch.getGenerationLevel();
                branch.setParentBranch(newParent);
                branch.setGenerationLevel(newGenLevel);

                // Cascade update generation levels for all descendants
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
                            "Representative not found with ID: " + request.getRepresentativeId()
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
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId));

        if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
            throw new BusinessRuleException("Cannot delete the root branch of the clan");
        }

        if (branchRepository.existsByParentBranch_BranchId(branchId)) {
            throw new BusinessRuleException(
                    "Cannot delete a branch that has child branches. Please reassign or delete child branches first."
            );
        }

        if (memberRepository.existsByBranch_BranchId(branchId)) {
            throw new BusinessRuleException(
                    "Cannot delete a branch that has associated family members. Please reassign members to another branch first."
            );
        }

        branchRepository.delete(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMemberToBranch(String memberId, String branchId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));

        FamilyBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId));

        // Validate member generation is not less than branch generation level
        if (member.getGeneration() < branch.getGenerationLevel()) {
            throw new InvalidGenerationException(
                    "Generation level mismatch: Member generation (" + member.getGeneration() +
                            ") cannot be less than branch generation level (" + branch.getGenerationLevel() + ")"
            );
        }

        member.setBranch(branch);
        memberRepository.save(member);
    }
}
