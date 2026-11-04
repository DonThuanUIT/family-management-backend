package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface ClanAssetService {

    ClanAssetSummaryResponse searchAssets(String keyword, Pageable pageable);

    ClanAssetResponse getAssetById(String assetId);
}
