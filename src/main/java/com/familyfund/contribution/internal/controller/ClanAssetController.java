package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import com.familyfund.contribution.internal.service.ClanAssetService;
import com.familyfund.core.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing and querying accumulated clan assets at the ancestral hall (US-10, Task #73, #74).
 */
@RestController
@RequestMapping("/api/v1/clan-assets")
@RequiredArgsConstructor
public class ClanAssetController {

    private final ClanAssetService clanAssetService;

    /**
     * Search and list clan assets with pagination and summary counts (Task #73, #74).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ClanAssetSummaryResponse>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        ClanAssetSummaryResponse response = clanAssetService.searchAssets(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Clan assets retrieved successfully"));
    }

    /**
     * Get clan asset details by asset ID.
     */
    @GetMapping("/{assetId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ClanAssetResponse>> getAssetById(
            @PathVariable String assetId) {
        ClanAssetResponse response = clanAssetService.getAssetById(assetId);
        return ResponseEntity.ok(ApiResponse.success(response, "Clan asset retrieved successfully"));
    }
}
