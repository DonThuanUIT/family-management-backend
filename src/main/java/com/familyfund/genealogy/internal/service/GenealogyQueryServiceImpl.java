package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal service implementation of the GenealogyQueryService public contract.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenealogyQueryServiceImpl implements GenealogyQueryService {

    private final FamilyBranchRepository familyBranchRepository;
    private final ClanRepository clanRepository;
    private final MemberRepository memberRepository;

    @Override
    public boolean existsBranch(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            return false;
        }
        String clanId = TenantContext.getClanId();
        return existsBranch(clanId, branchId.trim());
    }

    @Override
    public boolean existsBranch(String clanId, String branchId) {
        if (clanId == null || clanId.isBlank() || branchId == null || branchId.isBlank()) {
            return false;
        }
        return familyBranchRepository.existsByClanIdAndBranchId(clanId.trim(), branchId.trim());
    }

    @Override
    public Optional<String> getClanName(String clanId) {
        if (clanId == null || clanId.isBlank()) {
            return Optional.empty();
        }
        return clanRepository.findById(clanId.trim())
                .map(Clan::getClanName);
    }

    @Override
    public Optional<String> getBranchName(String clanId, String branchId) {
        if (clanId == null || clanId.isBlank() || branchId == null || branchId.isBlank()) {
            return Optional.empty();
        }
        return familyBranchRepository.findByClanIdAndBranchId(clanId.trim(), branchId.trim())
                .map(FamilyBranch::getBranchName);
    }

    @Override
    public OnboardingBranchTreeNode getOnboardingBranchTree(String clanId, String targetBranchId) {
        if (clanId == null || clanId.isBlank()) {
            throw new ResourceNotFoundException("Clan", "clanId", clanId);
        }

        Clan clan = clanRepository.findById(clanId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Clan", "clanId", clanId));

        // 1. Fetch all branches of the clan with representative in 1 single query
        List<FamilyBranch> allBranches = familyBranchRepository.findAllByClanIdWithRepresentative(clanId.trim());
        if (allBranches.isEmpty()) {
            return null;
        }

        // 2. Locate root for this onboarding view:
        FamilyBranch rootBranch;
        if (targetBranchId != null && !targetBranchId.isBlank()) {
            rootBranch = allBranches.stream()
                    .filter(b -> b.getBranchId().equals(targetBranchId.trim()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Nhánh gia phả", "branchId", targetBranchId));
        } else {
            rootBranch = allBranches.stream()
                    .filter(b -> b.getParentBranch() == null)
                    .findFirst()
                    .orElse(null);
        }

        if (rootBranch == null) {
            return null;
        }

        // 3. Group child branches by parentBranchId in-memory
        Map<String, List<FamilyBranch>> childrenByParentId = allBranches.stream()
                .filter(b -> b.getParentBranch() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getParentBranch().getBranchId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 4. Count members per branch in 1 single query
        Map<String, Integer> memberCountMap = memberRepository.countMembersByBranchForClan(clanId.trim()).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (existing, replacement) -> existing
                ));

        // 5. Build sanitized onboarding tree recursively in-memory
        return buildOnboardingNodeInMemory(rootBranch, childrenByParentId, memberCountMap, clan.getClanName());
    }

    @Override
    public List<String> getBranchIdsManagedByMember(String clanId, String memberId) {
        if (clanId == null || clanId.isBlank() || memberId == null || memberId.isBlank()) {
            return Collections.emptyList();
        }

        List<FamilyBranch> directlyManaged = familyBranchRepository
                .findByClanIdAndRepresentative_MemberId(clanId.trim(), memberId.trim());

        if (directlyManaged.isEmpty()) {
            return Collections.emptyList();
        }

        List<FamilyBranch> allBranches = familyBranchRepository.findAllByClanIdWithRepresentative(clanId.trim());
        Map<String, List<FamilyBranch>> childrenByParentId = allBranches.stream()
                .filter(b -> b.getParentBranch() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getParentBranch().getBranchId(),
                        Collectors.toList()
                ));

        Set<String> managedIds = new HashSet<>();
        for (FamilyBranch branch : directlyManaged) {
            collectBranchAndDescendants(branch.getBranchId(), childrenByParentId, managedIds);
        }

        return new ArrayList<>(managedIds);
    }

    private void collectBranchAndDescendants(
            String currentBranchId,
            Map<String, List<FamilyBranch>> childrenByParentId,
            Set<String> result
    ) {
        result.add(currentBranchId);
        List<FamilyBranch> children = childrenByParentId.getOrDefault(currentBranchId, Collections.emptyList());
        for (FamilyBranch child : children) {
            collectBranchAndDescendants(child.getBranchId(), childrenByParentId, result);
        }
    }

    private OnboardingBranchTreeNode buildOnboardingNodeInMemory(
            FamilyBranch branch,
            Map<String, List<FamilyBranch>> childrenByParentId,
            Map<String, Integer> memberCountMap,
            String clanName
    ) {
        int memberCount = memberCountMap.getOrDefault(branch.getBranchId(), 0);
        List<FamilyBranch> children = childrenByParentId.getOrDefault(branch.getBranchId(), Collections.emptyList());

        List<OnboardingBranchTreeNode> childNodes = children.stream()
                .map(child -> buildOnboardingNodeInMemory(child, childrenByParentId, memberCountMap, clanName))
                .toList();

        String representativeName = branch.getRepresentative() != null ? branch.getRepresentative().getFullName() : null;

        return OnboardingBranchTreeNode.builder()
                .branchId(branch.getBranchId())
                .clanId(branch.getClanId())
                .clanName(clanName)
                .branchName(branch.getBranchName())
                .generationLevel(branch.getGenerationLevel())
                .parentBranchId(branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null)
                .representativeName(representativeName)
                .memberCount(memberCount)
                .children(childNodes)
                .build();
    }
}
