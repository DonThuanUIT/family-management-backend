package com.familyfund.genealogy.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.genealogy.api.dto.BranchHierarchyNodeDto;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    @Deprecated
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

    @Override
    public Optional<MemberSummaryDto> findMemberSummary(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return Optional.empty();
        }
        return memberRepository.findById(memberId.trim())
                .map(member -> new MemberSummaryDto(
                        member.getMemberId(),
                        member.getFullName(),
                        member.getBranch() != null ? member.getBranch().getClanId() : null,
                        member.getBranch() != null ? member.getBranch().getBranchId() : null
                ));
    }

    @Override
    public List<String> getMemberIdsByBranch(String clanId, String branchId) {
        if (clanId == null || clanId.isBlank() || branchId == null || branchId.isBlank()) {
            return Collections.emptyList();
        }
        return memberRepository.findByBranch_ClanIdAndBranch_BranchId(clanId.trim(), branchId.trim())
                .stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsPlaceholder()))
                .filter(m -> m.getStatus() == null || Member.STATUS_LIVING.equalsIgnoreCase(m.getStatus()))
                .map(Member::getMemberId)
                .toList();
    }

    @Override
    public Optional<BranchSummaryDto> findBranchByIdAndClanId(String branchId, String clanId) {
        if (branchId == null || branchId.isBlank() || clanId == null || clanId.isBlank()) {
            return Optional.empty();
        }
        return familyBranchRepository.findByClanIdAndBranchId(clanId.trim(), branchId.trim())
                .map(this::toBranchSummaryDto);
    }

    @Override
    public List<BranchSummaryDto> findAllBranchesByClanId(String clanId) {
        if (clanId == null || clanId.isBlank()) {
            return Collections.emptyList();
        }
        return familyBranchRepository.findAllByClanIdWithRepresentative(clanId.trim())
                .stream()
                .map(this::toBranchSummaryDto)
                .toList();
    }

    @Override
    public List<MemberSummaryDto> findAllAliveMembersByClanId(String clanId) {
        if (clanId == null || clanId.isBlank()) {
            return Collections.emptyList();
        }
        return memberRepository.findByBranch_ClanId(clanId.trim())
                .stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsPlaceholder()))
                .filter(m -> m.getStatus() == null || Member.STATUS_LIVING.equalsIgnoreCase(m.getStatus()))
                .map(m -> new MemberSummaryDto(
                        m.getMemberId(),
                        m.getFullName(),
                        m.getBranch() != null ? m.getBranch().getClanId() : null,
                        m.getBranch() != null ? m.getBranch().getBranchId() : null
                ))
                .toList();
    }

    @Override
    public boolean isMemberRepresentativeOfAnyBranch(String memberId, String clanId) {
        if (memberId == null || memberId.isBlank() || clanId == null || clanId.isBlank()) {
            return false;
        }
        return !familyBranchRepository.findByClanIdAndRepresentative_MemberId(clanId.trim(), memberId.trim()).isEmpty();
    }

    @Override
    public Optional<BranchHierarchyNodeDto> findBranchSubtree(String clanId, String rootBranchId) {
        if (clanId == null || clanId.isBlank() || rootBranchId == null || rootBranchId.isBlank()) {
            return Optional.empty();
        }
        String normalizedClanId = clanId.trim();
        String normalizedRootId = rootBranchId.trim();

        List<FamilyBranch> subtreeBranches = familyBranchRepository.findSubtreeByClanIdAndRootBranchId(
                normalizedClanId, normalizedRootId
        );
        if (subtreeBranches == null || subtreeBranches.isEmpty()) {
            return Optional.empty();
        }

        FamilyBranch rootBranch = subtreeBranches.stream()
                .filter(b -> normalizedRootId.equals(b.getBranchId()))
                .findFirst()
                .orElse(null);
        if (rootBranch == null) {
            return Optional.empty();
        }

        Map<String, List<FamilyBranch>> childrenByParentId = subtreeBranches.stream()
                .filter(b -> b.getParentBranch() != null && !normalizedRootId.equals(b.getBranchId()))
                .collect(Collectors.groupingBy(
                        b -> b.getParentBranch().getBranchId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, Integer> memberCountMap = memberRepository.countMembersByBranchForClan(normalizedClanId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (existing, replacement) -> existing
                ));

        return Optional.of(buildHierarchyNodeDto(rootBranch, childrenByParentId, memberCountMap, new HashSet<>()));
    }

    @Override
    public BigDecimal tinhTongDongGopDeQuy(
            String clanId,
            String rootBranchId,
            String maSuKien,
            Map<String, BigDecimal> directBranchContributions
    ) {
        if (clanId == null || clanId.isBlank() || rootBranchId == null || rootBranchId.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalizedClanId = clanId.trim();
        String normalizedRootId = rootBranchId.trim();

        List<FamilyBranch> subtreeBranches = familyBranchRepository.findSubtreeByClanIdAndRootBranchId(
                normalizedClanId, normalizedRootId
        );
        if (subtreeBranches == null || subtreeBranches.isEmpty()) {
            throw new ResourceNotFoundException("Nhánh gia phả", "branchId", normalizedRootId);
        }

        FamilyBranch domainRoot = assembleDomainSubtree(normalizedRootId, subtreeBranches, directBranchContributions, maSuKien);
        if (domainRoot == null) {
            throw new ResourceNotFoundException("Nhánh gia phả", "branchId", normalizedRootId);
        }

        // Gọi trực tiếp giải thuật đệ quy tinhTongDongGopDeQuy(maSuKien) trên thực thể FamilyBranch (NhanhGiaDinh)
        return domainRoot.tinhTongDongGopDeQuy(maSuKien);
    }

    private FamilyBranch assembleDomainSubtree(
            String rootBranchId,
            List<FamilyBranch> subtreeBranches,
            Map<String, BigDecimal> directBranchContributions,
            String maSuKien
    ) {
        Map<String, FamilyBranch> detachedNodes = new LinkedHashMap<>();
        for (FamilyBranch entity : subtreeBranches) {
            BigDecimal directAmount = (directBranchContributions != null)
                    ? directBranchContributions.getOrDefault(entity.getBranchId(), BigDecimal.ZERO)
                    : BigDecimal.ZERO;

            FamilyBranch copy = FamilyBranch.builder()
                    .branchId(entity.getBranchId())
                    .clanId(entity.getClanId())
                    .branchName(entity.getBranchName())
                    .generationLevel(entity.getGenerationLevel())
                    .description(entity.getDescription())
                    .representative(entity.getRepresentative())
                    .childBranches(new ArrayList<>())
                    .build();
            copy.ganDongGopTrucTiep(maSuKien, directAmount);
            detachedNodes.put(entity.getBranchId(), copy);
        }

        for (FamilyBranch entity : subtreeBranches) {
            if (!rootBranchId.equals(entity.getBranchId()) && entity.getParentBranch() != null) {
                String parentId = entity.getParentBranch().getBranchId();
                FamilyBranch parentCopy = detachedNodes.get(parentId);
                FamilyBranch childCopy = detachedNodes.get(entity.getBranchId());
                if (parentCopy != null && childCopy != null) {
                    childCopy.setParentBranch(parentCopy);
                    parentCopy.getChildBranches().add(childCopy);
                }
            }
        }

        return detachedNodes.get(rootBranchId);
    }

    private BranchHierarchyNodeDto buildHierarchyNodeDto(
            FamilyBranch branch,
            Map<String, List<FamilyBranch>> childrenByParentId,
            Map<String, Integer> memberCountMap,
            Set<String> visited
    ) {
        if (branch.getBranchId() == null || !visited.add(branch.getBranchId())) {
            return null;
        }
        int directMemberCount = memberCountMap.getOrDefault(branch.getBranchId(), 0);
        List<FamilyBranch> children = childrenByParentId.getOrDefault(branch.getBranchId(), Collections.emptyList());

        List<BranchHierarchyNodeDto> childDtos = new ArrayList<>();
        for (FamilyBranch child : children) {
            BranchHierarchyNodeDto childDto = buildHierarchyNodeDto(child, childrenByParentId, memberCountMap, visited);
            if (childDto != null) {
                childDtos.add(childDto);
            }
        }

        String repId = branch.getRepresentative() != null ? branch.getRepresentative().getMemberId() : null;
        String repName = branch.getRepresentative() != null ? branch.getRepresentative().getFullName() : null;
        String parentId = branch.getParentBranch() != null ? branch.getParentBranch().getBranchId() : null;

        return new BranchHierarchyNodeDto(
                branch.getBranchId(),
                branch.getClanId(),
                branch.getBranchName(),
                branch.getGenerationLevel(),
                parentId,
                repId,
                repName,
                branch.getDescription(),
                directMemberCount,
                childDtos
        );
    }

    private BranchSummaryDto toBranchSummaryDto(FamilyBranch branch) {
        String repId = branch.getRepresentative() != null ? branch.getRepresentative().getMemberId() : null;
        String repName = branch.getRepresentative() != null ? branch.getRepresentative().getFullName() : null;
        return new BranchSummaryDto(
                branch.getBranchId(),
                branch.getClanId(),
                branch.getBranchName(),
                branch.getGenerationLevel(),
                repId,
                repName
        );
    }
}
