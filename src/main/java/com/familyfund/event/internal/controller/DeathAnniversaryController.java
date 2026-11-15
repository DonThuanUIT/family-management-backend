package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.core.exception.BadRequestException;
import com.familyfund.event.api.DeathAnniversaryService;
import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.dto.DeathAnniversaryReminderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Death-anniversary endpoints of US-40.
 *
 * <p>Separate controller (not part of {@code EventController}) because the resource is "giỗ", not "sự kiện": the
 * suggestion payload it returns is meant to be POSTed to {@code /api/v1/events} by the client after review.</p>
 */
@RestController
@RequestMapping("/api/v1/events/death-anniversaries")
@RequiredArgsConstructor
public class DeathAnniversaryController {

    private final DeathAnniversaryService deathAnniversaryService;

    /**
     * Upcoming death anniversaries of the clan, each with the suggested "Đám giỗ" event payload.
     *
     * @param from optional first day of the window (default: today in the clan timezone)
     * @param days optional window length in days (default: {@code app.death-anniversary.window-days})
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DeathAnniversaryDto>>> upcomingAnniversaries(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "days", required = false, defaultValue = "0") int days) {
        List<DeathAnniversaryDto> result = deathAnniversaryService.upcomingAnniversaries(parseDate(from, "from"), days);
        return ResponseEntity.ok(ApiResponse.success(result,
                "Lấy danh sách ngày giỗ sắp tới thành công"));
    }

    /**
     * Reminders already sent for one member (any year) — lets the UI show "đã nhắc".
     */
    @GetMapping("/{memberId}/reminders")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DeathAnniversaryReminderDto>>> remindersOfMember(
            @PathVariable String memberId) {
        List<DeathAnniversaryReminderDto> result = deathAnniversaryService.remindersOfMember(memberId);
        return ResponseEntity.ok(ApiResponse.success(result, "Lấy lịch sử nhắc giỗ thành công"));
    }

    /**
     * Runs the reminder pass immediately (Trưởng tộc / Admin).
     *
     * <p>Same code path as the cron job, exposed so an administrator can re-run a missed pass and so the behaviour is
     * demonstrable without waiting for 07:00. Idempotent by construction: already-sent milestones are skipped.</p>
     *
     * @param date optional reference day (default: today in the clan timezone)
     */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scan(
            @RequestParam(name = "date", required = false) String date) {
        LocalDate referenceDay = parseDate(date, "date");
        int sent = deathAnniversaryService.scanAndSendReminders(referenceDay);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("remindersSent", sent,
                        "referenceDate", (referenceDay != null ? referenceDay : LocalDate.now()).toString()),
                "Chạy đối chiếu ngày giỗ thành công"));
    }

    /**
     * Parses an optional ISO date parameter ({@code yyyy-MM-dd}).
     *
     * <p>Done by hand instead of {@code @DateTimeFormat} so a malformed value answers 400 with the standard error
     * envelope instead of Spring's generic type-mismatch 500.</p>
     */
    private static LocalDate parseDate(String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(
                    "Tham số '" + parameter + "' phải có định dạng yyyy-MM-dd, nhận được: '" + value.trim() + "'");
        }
    }
}
