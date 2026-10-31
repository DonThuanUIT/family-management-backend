package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.CreateEventItemBenchmarkRequest;
import com.familyfund.contribution.internal.dto.response.EventItemBenchmarkResponse;
import com.familyfund.contribution.internal.service.EventItemBenchmarkService;
import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing event item benchmarks (US-10, Task #72).
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/benchmarks")
@RequiredArgsConstructor
public class EventItemBenchmarkController {

    private final EventItemBenchmarkService benchmarkService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @nhanSuSuKien.xacThucQuyenTheoNguCanh(#eventId, 'HeadOfOrganizer', 'EventTreasurer')")
    public ResponseEntity<ApiResponse<EventItemBenchmarkResponse>> createBenchmark(
            @PathVariable String eventId,
            @Valid @RequestBody CreateEventItemBenchmarkRequest request) {
        EventItemBenchmarkResponse response = benchmarkService.createBenchmark(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Event item benchmark created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN') or @nhanSuSuKien.xacThucQuyenTheoNguCanh(#eventId, 'HeadOfOrganizer', 'EventTreasurer', 'OrganizerMember')")
    public ResponseEntity<ApiResponse<List<EventItemBenchmarkResponse>>> getBenchmarksByEvent(
            @PathVariable String eventId) {
        List<EventItemBenchmarkResponse> response = benchmarkService.getBenchmarksByEvent(eventId);
        return ResponseEntity.ok(ApiResponse.success(response, "Event item benchmarks retrieved successfully"));
    }

    @DeleteMapping("/{benchmarkId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @nhanSuSuKien.xacThucQuyenTheoNguCanh(#eventId, 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<Void>> deleteBenchmark(
            @PathVariable String eventId,
            @PathVariable String benchmarkId) {
        benchmarkService.deleteBenchmark(eventId, benchmarkId);
        return ResponseEntity.ok(ApiResponse.success(null, "Event item benchmark deleted successfully"));
    }
}
