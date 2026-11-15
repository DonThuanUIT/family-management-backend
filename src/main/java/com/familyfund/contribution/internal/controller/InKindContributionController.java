package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.request.ProcessInKindLifecycleRequest;
import com.familyfund.contribution.internal.dto.request.RecordInKindContributionRequest;
import com.familyfund.contribution.internal.dto.response.EventPendingInKindCheckResponse;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.ProcessInKindLifecycleResponse;
import com.familyfund.contribution.internal.service.InKindContributionService;
import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for in-kind contributions, electronic receipts, lifecycle processing,
 * and pre-closure audit checks (US-10, US-51, Task #180).
 */
@RestController
@RequestMapping({"/api/v1/contributions/in-kind", "/api/contributions/in-kind"})
@RequiredArgsConstructor
public class InKindContributionController {

    private final InKindContributionService inKindContributionService;

    /**
     * Record an in-kind contribution and issue an electronic receipt (Task #72).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#request.eventId(), 'EventTreasurer', 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<InKindElectronicReceiptResponse>> recordInKindContribution(
            @Valid @RequestBody RecordInKindContributionRequest request) {
        InKindElectronicReceiptResponse response = inKindContributionService.recordInKindContribution(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "In-kind contribution recorded and electronic receipt issued successfully"));
    }

    /**
     * Retrieve an electronic receipt by receipt number (Task #72, #74).
     */
    @GetMapping("/receipts/{receiptNumber}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<InKindElectronicReceiptResponse>> getReceiptByReceiptNumber(
            @PathVariable String receiptNumber) {
        InKindElectronicReceiptResponse response = inKindContributionService.getReceiptByReceiptNumber(receiptNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Electronic receipt retrieved successfully"));
    }

    /**
     * Retrieve in-kind contribution details by contribution ID.
     */
    @GetMapping("/{contributionId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<InKindContributionDetailResponse>> getInKindContributionById(
            @PathVariable String contributionId) {
        InKindContributionDetailResponse response = inKindContributionService.getInKindContributionById(contributionId);
        return ResponseEntity.ok(ApiResponse.success(response, "In-kind contribution retrieved successfully"));
    }

    /**
     * Update post-event lifecycle status for an in-kind contribution (US-51 Task #180 / US-10 Task #73).
     * Transitions status to Consumed, Returned, or AddedToCommonAssets (registering CLAN_ASSET).
     */
    @PutMapping({"/{contributionId}/status", "/{contributionId}/lifecycle"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @contributionSecurity.hasContributionRole(#contributionId, 'EventTreasurer', 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<ProcessInKindLifecycleResponse>> processLifecycle(
            @PathVariable String contributionId,
            @Valid @RequestBody ProcessInKindLifecycleRequest request) {
        ProcessInKindLifecycleResponse response = inKindContributionService.processLifecycle(contributionId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "In-kind contribution lifecycle processed successfully"));
    }

    /**
     * Retrieve in-kind contributions for an event, optionally filtered by processing status.
     */
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'EventTreasurer', 'OrganizerMember')")
    public ResponseEntity<ApiResponse<List<InKindContributionDetailResponse>>> getInKindContributionsByEvent(
            @PathVariable String eventId,
            @RequestParam(required = false) InKindProcessingStatus status) {
        List<InKindContributionDetailResponse> response = inKindContributionService.getInKindContributionsByEvent(eventId, status);
        return ResponseEntity.ok(ApiResponse.success(response, "In-kind contributions retrieved successfully"));
    }

    /**
     * Pre-closure audit check verifying all in-kind contributions are processed prior to event closure (US-51 / US-57, Task #180).
     */
    @GetMapping("/events/{eventId}/pending-check")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'EventTreasurer')")
    public ResponseEntity<ApiResponse<EventPendingInKindCheckResponse>> checkPendingInKindContributions(
            @PathVariable String eventId) {
        EventPendingInKindCheckResponse response = inKindContributionService.checkPendingInKindContributions(eventId);
        return ResponseEntity.ok(ApiResponse.success(response, "Pending in-kind contributions checked successfully"));
    }
}
