package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.InvalidGenerationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.dto.request.CreateChildBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateRootBranchRequest;
import com.familyfund.genealogy.internal.dto.response.BranchDetailResponse;
import com.familyfund.genealogy.internal.dto.response.BranchTreeNodeResponse;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.mapper.BranchMapper;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.genealogy.internal.validator.GenealogyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenealogyServiceImpl implements GenealogyService {

    private final FamilyBranchRepository branchRepository;
    private final MemberRepository memberRepository;
    private final ClanRepository clanRepository;
    private final GenealogyValidator genealogyValidator;
    private final BranchMapper branchMapper;

    @Override
    @Transactional(readOnly = true)
    public BranchDetailResponse getRootBranch() {
        String clanId = TenantContext.getClanId();
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));

        FamilyBranch rootBranch = branchRepository.findByClanIdAndParentBranchIsNull(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Root branch not found for clan: " + clanId));
        return branchMapper.toDetailResponse(rootBranch, clan.getClanName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse updateRootBranch(UpdateRootBranchRequest request) {
        String clanId = TenantContext.getClanId();
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId + ". Please create clan first."));

        FamilyBranch rootBranch = branchRepository.findByClanIdAndParentBranchIsNull(clanId)
                .orElseGet(() -> FamilyBranch.builder()
                        .branchId("BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .clanId(clanId)
                        .generationLevel(1)
                        .parentBranch(null)
                        .build());

        // Business rule: Root branch must have generation_level = 1 and no parent branch
        if (rootBranch.getParentBranch() != null || rootBranch.getGenerationLevel() != 1) {
            throw new BusinessRuleException("This branch is not a valid root branch of the clan");
        }

        rootBranch.setClanId(clanId);
        rootBranch.setBranchName(request.branchName().trim());

        if (request.description() != null) {
            String cleanDesc = request.description().trim();
            rootBranch.setDescription(cleanDesc.isEmpty() ? null : cleanDesc);
        }

        if (request.representativeId() != null && !request.representativeId().trim().isEmpty()) {
            Member rep = memberRepository.findByBranch_ClanIdAndMemberId(clanId, request.representativeId().trim())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Representative/ancestor not found with ID: " + request.representativeId() + " in clan: " + clanId
                    ));
            rootBranch.setRepresentative(rep);
        } else {
            rootBranch.setRepresentative(null);
        }

        FamilyBranch saved = branchRepository.save(rootBranch);
        return branchMapper.toDetailResponse(saved, clan.getClanName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse createChildBranch(CreateChildBranchRequest request) {
        String clanId = TenantContext.getClanId();
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));

        FamilyBranch parent = branchRepository.findByClanIdAndBranchId(clanId, request.parentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent branch not found with ID: " + request.parentBranchId() + " in clan: " + clanId
                ));

        // Cycle check within clan
        genealogyValidator.validateNoCycles(clanId, null, request.parentBranchId());
        int childGenerationLevel = parent.getGenerationLevel() + 1;

        FamilyBranch childBranch = FamilyBranch.builder()
                .branchId("BR_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .clanId(clanId)
                .branchName(request.branchName().trim())
                .generationLevel(childGenerationLevel)
                .parentBranch(parent)
                .description(request.description() != null && !request.description().trim().isEmpty() ? request.description().trim() : null)
                .build();

        if (request.representativeId() != null && !request.representativeId().trim().isEmpty()) {
            Member rep = memberRepository.findByBranch_ClanIdAndMemberId(clanId, request.representativeId().trim())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Representative not found with ID: " + request.representativeId() + " in clan: " + clanId
                    ));
            childBranch.setRepresentative(rep);
        }

        FamilyBranch saved = branchRepository.save(childBranch);
        return branchMapper.toDetailResponse(saved, clan.getClanName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchDetailResponse updateBranch(String branchId, UpdateBranchRequest request) {
        String clanId = TenantContext.getClanId();
        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(clanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + clanId));

        // If reparenting
        if (request.parentBranchId() != null && !request.parentBranchId().trim().isEmpty()) {
            String currentParentId = branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null;
            if (!request.parentBranchId().equals(currentParentId)) {
                // Cannot assign parent to root branch
                if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
                    throw new BusinessRuleException("Cannot assign parent branch to the root branch of the clan");
                }

                // 1. Verify and fetch new parent branch within the same clan FIRST
                FamilyBranch newParent = branchRepository.findByClanIdAndBranchId(clanId, request.parentBranchId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "New parent branch not found with ID: " + request.parentBranchId() + " in clan: " + clanId
                        ));

                // 2. Check closed cycle within the clan
                genealogyValidator.validateNoCycles(clanId, branchId, request.parentBranchId());

                if (newParent.getGenerationLevel() == null) {
                    throw new BusinessRuleException("Parent branch generation level cannot be null");
                }

                int newGenLevel = newParent.getGenerationLevel() + 1;
                int delta = newGenLevel - branch.getGenerationLevel();
                branch.setParentBranch(newParent);
                branch.setGenerationLevel(newGenLevel);

                // Cascade update generation levels for all descendants and their members
                if (delta != 0) {
                    cascadeUpdateGenerationLevel(clanId, branch, delta);
                }
            }
        }

        if (request.branchName() != null) {
            if (request.branchName().trim().isEmpty()) {
                throw new BusinessRuleException("Branch name cannot be blank");
            }
            branch.setBranchName(request.branchName().trim());
        }
        if (request.description() != null) {
            String cleanDesc = request.description().trim();
            branch.setDescription(cleanDesc.isEmpty() ? null : cleanDesc);
        }

        // Representative handling:
        // - If non-empty string provided -> assign representative (must belong to this clan)
        // - If empty string ("") provided -> unassign current representative
        // - If null -> keep current representative unchanged
        if (request.representativeId() != null) {
            if (!request.representativeId().trim().isEmpty()) {
                Member rep = memberRepository.findByBranch_ClanIdAndMemberId(clanId, request.representativeId().trim())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Representative not found with ID: " + request.representativeId() + " in clan: " + clanId
                        ));
                branch.setRepresentative(rep);
            } else {
                branch.setRepresentative(null);
            }
        }

        FamilyBranch saved = branchRepository.save(branch);
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));
        return branchMapper.toDetailResponse(saved, clan.getClanName());
    }

    private void cascadeUpdateGenerationLevel(String clanId, FamilyBranch current, int delta) {
        // Update member generations for current branch
        List<Member> members = memberRepository.findByBranch_ClanIdAndBranch_BranchId(clanId, current.getBranchId());
        for (Member member : members) {
            member.setGeneration(member.getGeneration() + delta);
        }

        List<FamilyBranch> children = branchRepository.findByClanIdAndParentBranch_BranchId(clanId, current.getBranchId());
        for (FamilyBranch child : children) {
            child.setGenerationLevel(child.getGenerationLevel() + delta);
            cascadeUpdateGenerationLevel(clanId, child, delta);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BranchTreeNodeResponse getBranchTree() {
        String clanId = TenantContext.getClanId();
        Clan clan = clanRepository.findById(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan not found with ID: " + clanId));

        // 1. Fetch all branches of the clan with representative in 1 single query
        List<FamilyBranch> allBranches = branchRepository.findAllByClanIdWithRepresentative(clanId);
        if (allBranches.isEmpty()) {
            return null;
        }

        // 2. Locate the root branch (parentBranch == null)
        FamilyBranch root = allBranches.stream()
                .filter(b -> b.getParentBranch() == null)
                .findFirst()
                .orElse(null);
        if (root == null) {
            return null;
        }

        // 3. Aggregate member counts per branch in 1 single GROUP BY query
        Map<String, Integer> memberCountMap = memberRepository.countMembersByBranchForClan(clanId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (existing, replacement) -> existing
                ));

        // 4. Group child branches by parentBranchId in-memory with deterministic ordering
        Map<String, List<FamilyBranch>> childrenByParentId = allBranches.stream()
                .filter(b -> b.getParentBranch() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getParentBranch().getBranchId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 5. Build tree recursively entirely IN-MEMORY (0 extra database queries!)
        return buildTreeNodeInMemory(root, childrenByParentId, memberCountMap, clan.getClanName());
    }

    private BranchTreeNodeResponse buildTreeNodeInMemory(
            FamilyBranch branch,
            Map<String, List<FamilyBranch>> childrenByParentId,
            Map<String, Integer> memberCountMap,
            String clanName
    ) {
        int memberCount = memberCountMap.getOrDefault(branch.getBranchId(), 0);
        List<FamilyBranch> children = childrenByParentId.getOrDefault(branch.getBranchId(), Collections.emptyList());

        List<BranchTreeNodeResponse> childNodes = children.stream()
                .map(child -> buildTreeNodeInMemory(child, childrenByParentId, memberCountMap, clanName))
                .toList();

        return branchMapper.toTreeNodeResponse(branch, clanName, memberCount, childNodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBranch(String branchId) {
        String clanId = TenantContext.getClanId();
        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(clanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + clanId));

        if (branch.getParentBranch() == null && branch.getGenerationLevel() == 1) {
            throw new BusinessRuleException("Cannot delete the root branch of the clan");
        }

        if (branchRepository.existsByClanIdAndParentBranch_BranchId(clanId, branchId)) {
            throw new BusinessRuleException(
                    "Cannot delete a branch that has child branches. Please reassign or delete child branches first."
            );
        }

        if (memberRepository.existsByBranch_ClanIdAndBranch_BranchId(clanId, branchId)) {
            throw new BusinessRuleException(
                    "Cannot delete a branch that has associated family members. Please reassign members to another branch first."
            );
        }

        branchRepository.delete(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMemberToBranch(String memberId, String branchId) {
        String clanId = TenantContext.getClanId();
        Member member = memberRepository.findByBranch_ClanIdAndMemberId(clanId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with ID: " + memberId + " in clan: " + clanId));

        FamilyBranch branch = branchRepository.findByClanIdAndBranchId(clanId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + branchId + " in clan: " + clanId));

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
