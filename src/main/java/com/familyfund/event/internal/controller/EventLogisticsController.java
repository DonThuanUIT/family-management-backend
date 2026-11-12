package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.event.internal.dto.response.EventLogisticsSummaryResponse;
import com.familyfund.event.internal.service.EventLogisticsExportService;
import com.familyfund.event.internal.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * REST Controller for Event Organizer (BTC) real-time logistics summary & export (US-42).
 * Supports both `/api/v1/events/{id}/registration-summary` and `/api/events/{id}/registration-summary`.
 */
@RestController
@RequiredArgsConstructor
public class EventLogisticsController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final RegistrationService registrationService;
    private final EventLogisticsExportService exportService;

    @GetMapping({
            "/api/v1/events/{eventId}/registration-summary",
            "/api/events/{eventId}/registration-summary"
    })
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')")
    public ResponseEntity<ApiResponse<EventLogisticsSummaryResponse>> getEventLogisticsSummary(
            @PathVariable String eventId,
            @RequestParam(required = false) String branchId) {
        EventLogisticsSummaryResponse summary = registrationService.getEventLogisticsSummary(eventId, branchId);
        return ResponseEntity.ok(ApiResponse.success(summary, "Lấy bảng tổng hợp hậu cần sự kiện thành công"));
    }

    @GetMapping({
            "/api/v1/events/{eventId}/registration-summary/export",
            "/api/events/{eventId}/registration-summary/export"
    })
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')")
    public ResponseEntity<byte[]> exportEventLogisticsSummary(
            @PathVariable String eventId,
            @RequestParam(required = false) String branchId,
            @RequestParam(defaultValue = "xlsx") String format) {
        EventLogisticsSummaryResponse summary = registrationService.getEventLogisticsSummary(eventId, branchId);
        byte[] content = exportService.exportLogisticsReport(summary, format);

        boolean isCsv = "csv".equalsIgnoreCase(format);
        String ext = isCsv ? "csv" : "xlsx";
        String suffix = (branchId != null && !branchId.isBlank()) ? "-" + branchId.trim() : "";
        String filename = "danh-sach-diem-danh-chia-co-" + eventId + suffix + "." + ext;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(isCsv ? CSV_MEDIA_TYPE : XLSX_MEDIA_TYPE);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
        );
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
