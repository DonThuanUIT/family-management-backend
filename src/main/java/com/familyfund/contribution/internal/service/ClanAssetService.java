package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.AuditClanAssetRequest;
import com.familyfund.contribution.internal.dto.response.AuditClanAssetResultResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetAuditResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClanAssetService {

    ClanAssetSummaryResponse searchAssets(String keyword, Pageable pageable);

    ClanAssetResponse getAssetById(String assetId);

    /**
     * Conducts an inventory audit for a clan asset (US-52 Task #183).
     */
    AuditClanAssetResultResponse auditAsset(String assetId, AuditClanAssetRequest request);

    /**
     * Retrieves the audit history for a clan asset (US-52 Task #183).
     */
    List<ClanAssetAuditResponse> getAssetAuditHistory(String assetId);
}
