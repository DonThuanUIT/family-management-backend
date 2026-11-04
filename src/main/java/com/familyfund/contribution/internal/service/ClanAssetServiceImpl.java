package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import com.familyfund.contribution.internal.entity.ClanAsset;
import com.familyfund.contribution.internal.repository.ClanAssetRepository;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanAssetServiceImpl implements ClanAssetService {

    private final ClanAssetRepository clanAssetRepository;

    @Override
    @Transactional(readOnly = true)
    public ClanAssetSummaryResponse searchAssets(String keyword, Pageable pageable) {
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        Page<ClanAsset> page;
        if (keyword != null && !keyword.isBlank()) {
            page = clanAssetRepository.searchAssets(clanId, keyword.trim(), pageable);
        } else {
            page = clanAssetRepository.findAllByClanId(clanId, pageable);
        }
        List<ClanAssetResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        Long totalCount = clanAssetRepository.sumTotalQuantityByClanId(clanId);
        BigDecimal totalAssessedValue = clanAssetRepository.sumAssessedValueByClanId(clanId);

        return new ClanAssetSummaryResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                totalCount != null ? totalCount : 0L,
                totalAssessedValue != null ? totalAssessedValue : BigDecimal.ZERO
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ClanAssetResponse getAssetById(String assetId) {
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        ClanAsset asset = clanAssetRepository.findByAssetIdAndClanId(assetId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan asset not found with ID: " + assetId));

        return toResponse(asset);
    }

    private ClanAssetResponse toResponse(ClanAsset asset) {
        return new ClanAssetResponse(
                asset.getAssetId(),
                asset.getAssetName(),
                asset.getQuantity(),
                asset.getUnit(),
                asset.getAssessedValue(),
                asset.getStorageLocation(),
                asset.getOriginContributionId(),
                asset.getClanId(),
                asset.getCreatedAt()
        );
    }
}
