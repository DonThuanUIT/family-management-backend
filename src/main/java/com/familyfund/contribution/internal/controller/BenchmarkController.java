package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.response.EventItemBenchmarkResponse;
import com.familyfund.contribution.internal.service.EventItemBenchmarkService;
import com.familyfund.core.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for accessing clan-wide item benchmarks (US-10).
 */
@RestController
@RequestMapping("/api/v1/benchmarks")
@RequiredArgsConstructor
public class BenchmarkController {

    private final EventItemBenchmarkService benchmarkService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EventItemBenchmarkResponse>>> getAllBenchmarks() {
        List<EventItemBenchmarkResponse> response = benchmarkService.getAllBenchmarks();
        return ResponseEntity.ok(ApiResponse.success(response, "Clan item benchmarks retrieved successfully"));
    }
}
