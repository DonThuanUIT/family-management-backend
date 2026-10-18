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
import com.familyfund.modules.genealogy.mapper.BranchMapper;
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
    private final BranchMapper branchMapper;

    private String resolveClanId(String clanId) {
        if (clanId != null && !clanId.isBlank()) {
            return clanId.trim();
        }
        return DEFAULT_CLAN_ID;
    }

    @Override
    @Transactional(readOnly = true)
    public BranchDetailResponse getRootBranch(String clanId) {
        String effectiveClanId = resolveClanId(clanId);
        FamilyBranch rootBranch = branchRepository.findByClanIdAndParentBranchIsNull(effectiveClanId)
                .orElseThrow(() -> new ResourceNotFoundException("Root branch not found for clan: " + effectiveClanId));
        return branchMapper.toDetailResponse(rootBranch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse updateRootBranch(String clanId, UpdateRootBranchRequest request) {
        String effectiveClanId = resolveClanId(clanId);
        FamilyBranch rootBranch = branchRepository.findByClanIdAndParentBranchIsNull(effectiveClanId)
                .orElseGet(() -> FamilyBranch.builder()
                        .branchId("BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .clanId(effectiveClanId)
                        .generationLevel(1)
                        .parentBranch(null)
                        .build());

        // Business rule: Root branch must have generation_level = 1 and no parent branch
        if (rootBranch.getParentBranch() != null || rootBranch.getGenerationLevel() != 1) {
            throw new BusinessRuleException("This branch is not a valid root branch of the clan");
        }

        rootBranch.setClanId(effectiveClanId);
        rootBranch.setClanName(request.clanName());
        rootBranch.setBranchName(request.branchName());
        rootBranch.setDescription(request.description());

        if (request.representativeId() != null && !request.representativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.representativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Representative/ancestor not found with ID: " + request.representativeId()
                    ));
            rootBranch.setRepresentative(rep);
        } else {
            rootBranch.setRepresentative(null);
        }

        FamilyBranch saved = branchRepository.save(rootBranch);
        return branchMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse createChildBranch(String clanId, CreateChildBranchRequest request) {
        String effectiveClanId = resolveClanId(clanId);
        FamilyBranch parent = branchRepository.findByClanIdAndBranchId(effectiveClanId, request.parentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent branch not found with ID: " + request.parentBranchId() + " in clan: " + effectiveClanId
                ));

        // Cycle check and generation level check
        genealogyValidator.kiemTraChuTrinh(null, request.parentBranchId());
        int childGenerationLevel = parent.getGenerationLevel() + 1;
        genealogyValidator.kiemTraDoiThu(parent.getGenerationLevel(), childGenerationLevel);

        FamilyBranch childBranch = FamilyBranch.builder()
                .branchId("BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .clanId(effectiveClanId)
                .clanName(parent.getClanName())
                .branchName(request.branchName())
                .generationLevel(childGenerationLevel)
                .parentBranch(parent)
                .description(request.description())
                .build();

        if (request.representativeId() != null && !request.representativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.representativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Representative not found with ID: " + request.representativeId()
                    ));
            childBranch.setRepresentative(rep);
        }

        FamilyBranch saved = branchRepository.save(childBranch);
        return branchMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse updateBranch(String clanId, String branchId, UpdateBranchRequest request) {
        String effectiveClanId = resolveClanId(clanId);
        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(effectiveClanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + effectiveClanId));

        // If reparenting
        if (request.parentBranchId() != null) {
            String currentParentId = branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null;
            if (!request.parentBranchId().equals(currentParentId)) {
                // Cannot assign parent to root branch
                if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
                    throw new BusinessRuleException("Cannot assign parent branch to the root branch of the clan");
                }

                // Check closed cycle
                genealogyValidator.kiemTraChuTrinh(branchId, request.parentBranchId());

                FamilyBranch newParent = branchRepository.findByClanIdAndBranchId(effectiveClanId, request.parentBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "New parent branch not found with ID: " + request.parentBranchId() + " in clan: " + effectiveClanId
                        ));

                int newGenLevel = newParent.getGenerationLevel() + 1;
                genealogyValidator.kiemTraDoiThu(newParent.getGenerationLevel(), newGenLevel);

                int delta = newGenLevel - branch.getGenerationLevel();
                branch.setParentBranch(newParent);
                branch.setGenerationLevel(newGenLevel);

                // Cascade update generation levels for all descendants
                if (delta != 0) {
                    cascadeUpdateGenerationLevel(effectiveClanId, branch, delta);
                }
            }
        }

        branch.setBranchName(request.branchName());
        branch.setDescription(request.description());

        if (request.representativeId() != null && !request.representativeId().trim().isEmpty()) {
            Member rep = memberRepository.findById(request.representativeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Representative not found with ID: " + request.representativeId()
                    ));
            branch.setRepresentative(rep);
        } else {
            branch.setRepresentative(null);
        }

        FamilyBranch saved = branchRepository.save(branch);
        return branchMapper.toDetailResponse(saved);
    }

    private void cascadeUpdateGenerationLevel(String clanId, FamilyBranch current, int delta) {
        List<FamilyBranch> children = branchRepository.findByClanIdAndParentBranch_BranchId(clanId, current.getBranchId());
        for (FamilyBranch child : children) {
            child.setGenerationLevel(child.getGenerationLevel() + delta);
            branchRepository.save(child);
            cascadeUpdateGenerationLevel(clanId, child, delta);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BranchTreeNodeResponse getBranchTree(String clanId) {
        String effectiveClanId = resolveClanId(clanId);
        FamilyBranch root = branchRepository.findByClanIdAndParentBranchIsNull(effectiveClanId)
                .orElse(null);
        if (root == null) {
            return null;
        }
        return buildTreeNode(effectiveClanId, root);
    }

    private BranchTreeNodeResponse buildTreeNode(String clanId, FamilyBranch branch) {
        int memberCount = memberRepository.findByBranch_BranchId(branch.getBranchId()).size();
        List<FamilyBranch> children = branchRepository.findByClanIdAndParentBranch_BranchId(clanId, branch.getBranchId());
        List<BranchTreeNodeResponse> childNodes = children.stream()
                .map(child -> buildTreeNode(clanId, child))
                .toList();

        return branchMapper.toTreeNodeResponse(branch, memberCount, childNodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBranch(String clanId, String branchId) {
        String effectiveClanId = resolveClanId(clanId);
        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(effectiveClanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + effectiveClanId));

        if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
            throw new BusinessRuleException("Cannot delete the root branch of the clan");
        }

        if (branchRepository.existsByClanIdAndParentBranch_BranchId(effectiveClanId, branchId)) {
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
    public void assignMemberToBranch(String clanId, String memberId, String branchId) {
        String effectiveClanId = resolveClanId(clanId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId));

        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(effectiveClanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + effectiveClanId));

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
