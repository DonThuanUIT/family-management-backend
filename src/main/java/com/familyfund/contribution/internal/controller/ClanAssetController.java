package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.AuditClanAssetRequest;
import com.familyfund.contribution.internal.dto.response.AuditClanAssetResultResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetAuditResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import com.familyfund.contribution.internal.service.ClanAssetService;
import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing, querying, and auditing accumulated clan assets at the ancestral hall (US-10, US-52, Task #183).
 */
@RestController
@RequestMapping({"/api/v1/clan-assets", "/api/clan-assets"})
@RequiredArgsConstructor
public class ClanAssetController {

    private final ClanAssetService clanAssetService;

    /**
     * Search and list clan assets with pagination and summary counts (Task #73, #74, US-52).
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

    /**
     * Perform an inventory audit of a clan asset and update its condition / physical count (US-52 Task #183).
     */
    @PutMapping("/{assetId}/audit")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<AuditClanAssetResultResponse>> auditAsset(
            @PathVariable String assetId,
            @Valid @RequestBody AuditClanAssetRequest request) {
        AuditClanAssetResultResponse response = clanAssetService.auditAsset(assetId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Clan asset inventory audit recorded successfully"));
    }

    /**
     * View the full audit history of condition changes for a clan asset (US-52 Task #183).
     */
    @GetMapping("/{assetId}/audits")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ClanAssetAuditResponse>>> getAssetAuditHistory(
            @PathVariable String assetId) {
        List<ClanAssetAuditResponse> response = clanAssetService.getAssetAuditHistory(assetId);
        return ResponseEntity.ok(ApiResponse.success(response, "Clan asset audit history retrieved successfully"));
    }
}
