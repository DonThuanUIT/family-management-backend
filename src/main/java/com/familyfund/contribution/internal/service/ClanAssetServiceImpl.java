package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.AuditClanAssetRequest;
import com.familyfund.contribution.internal.dto.response.AuditClanAssetResultResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetAuditResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import com.familyfund.contribution.internal.entity.ClanAsset;
import com.familyfund.contribution.internal.entity.ClanAssetAudit;
import com.familyfund.contribution.internal.repository.ClanAssetAuditRepository;
import com.familyfund.contribution.internal.repository.ClanAssetRepository;
import com.familyfund.contribution.internal.util.AssetAuditIdGenerator;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanAssetServiceImpl implements ClanAssetService {

    private final ClanAssetRepository clanAssetRepository;
    private final ClanAssetAuditRepository clanAssetAuditRepository;
    private final AssetAuditIdGenerator assetAuditIdGenerator;
    private final CurrentUserProvider currentUserProvider;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuditClanAssetResultResponse auditAsset(String assetId, AuditClanAssetRequest request) {
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        ClanAsset asset = clanAssetRepository.findByAssetIdAndClanId(assetId, clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan asset not found with ID: " + assetId));

        int prevQty = asset.getQuantity();
        String prevCondition = asset.getCurrentCondition() != null ? asset.getCurrentCondition() : "Good";

        String auditorId = currentUserProvider.currentAccountId().orElse("System");
        Instant now = Instant.now();

        // 1. Generate audit record
        String auditId = assetAuditIdGenerator.generate();
        ClanAssetAudit auditLog = ClanAssetAudit.builder()
                .auditId(auditId)
                .assetId(assetId)
                .auditDate(now)
                .previousQuantity(prevQty)
                .actualQuantity(request.actualQuantity())
                .previousCondition(prevCondition)
                .conditionStatus(request.conditionStatus())
                .storageLocation(request.storageLocation() != null ? request.storageLocation() : asset.getStorageLocation())
                .auditorAccountId(auditorId)
                .notes(request.notes())
                .build();

        clanAssetAuditRepository.save(auditLog);

        // 2. Update current asset state
        asset.setQuantity(request.actualQuantity());
        asset.setCurrentCondition(request.conditionStatus());
        if (request.storageLocation() != null && !request.storageLocation().isBlank()) {
            asset.setStorageLocation(request.storageLocation().trim());
        }
        asset.setLastAuditedAt(now);
        asset.setLastAuditedBy(auditorId);
        clanAssetRepository.save(asset);

        ClanAssetAuditResponse auditResponse = new ClanAssetAuditResponse(
                auditLog.getAuditId(),
                auditLog.getAssetId(),
                auditLog.getAuditDate(),
                auditLog.getPreviousQuantity(),
                auditLog.getActualQuantity(),
                auditLog.getPreviousCondition(),
                auditLog.getConditionStatus(),
                auditLog.getStorageLocation(),
                auditLog.getAuditorAccountId(),
                auditLog.getNotes(),
                auditLog.getCreatedAt()
        );

        return new AuditClanAssetResultResponse(
                toResponse(asset),
                auditResponse,
                "Clan asset inventory audit recorded successfully"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClanAssetAuditResponse> getAssetAuditHistory(String assetId) {
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            clanId = "CLAN_DEFAULT";
        }

        if (!clanAssetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Clan asset not found with ID: " + assetId);
        }

        return clanAssetAuditRepository.findByAssetIdOrderByAuditDateDesc(assetId).stream()
                .map(a -> new ClanAssetAuditResponse(
                        a.getAuditId(),
                        a.getAssetId(),
                        a.getAuditDate(),
                        a.getPreviousQuantity(),
                        a.getActualQuantity(),
                        a.getPreviousCondition(),
                        a.getConditionStatus(),
                        a.getStorageLocation(),
                        a.getAuditorAccountId(),
                        a.getNotes(),
                        a.getCreatedAt()
                ))
                .toList();
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
                asset.getCurrentCondition() != null ? asset.getCurrentCondition() : "Good",
                asset.getLastAuditedAt(),
                asset.getLastAuditedBy(),
                asset.getCreatedAt()
        );
    }
}
