package com.familyfund.event.internal.anniversary;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Configuration of the daily death-anniversary job (US-40).
 *
 * ```yaml
 * app:
 *   death-anniversary:
 *     enabled: true
 *     cron: "0 0 7 * * *"        # chạy mỗi ngày 07:00 giờ Việt Nam (Spring: 6 trường)
 *     zone: Asia/Ho_Chi_Minh
 *     milestones: [14, 3]        # số ngày trước ngày giỗ
 *     window-days: 30            # cửa sổ của API xem trước
 *     reminder-time: "08:00"     # giờ bắt đầu gợi ý cho sự kiện "Đám giỗ"
 *     default-budget: 5000000
 *     event-name-template: "Đám giỗ {name} ({lunar})"
 * ```
 *
 * @param enabled           turn the cron pass on/off (tests keep it off)
 * @param cron              cron expression of the pass (Spring 6-field syntax)
 * @param zone              timezone the cron and "today" are evaluated in
 * @param milestones        milestone offsets in days, e.g. {@code [14, 3]}
 * @param windowDays        default window for {@link com.familyfund.event.api.DeathAnniversaryService#upcomingAnniversaries}
 * @param reminderTime      local start time suggested for the "Đám giỗ" event
 * @param defaultBudget     proposed estimated budget of the suggested event ({@code null} = leave empty)
 * @param eventNameTemplate template of the suggested event name ({@code {name}}, {@code {lunar}}, {@code {year}})
 */
@ConfigurationProperties(prefix = "app.death-anniversary")
public record DeathAnniversaryProperties(
        Boolean enabled,
        String cron,
        String zone,
        List<Integer> milestones,
        Integer windowDays,
        LocalTime reminderTime,
        BigDecimal defaultBudget,
        String eventNameTemplate
) {

    /** Default milestones: remind 14 days and 3 days before the anniversary. */
    public static final List<Integer> DEFAULT_MILESTONES = List.of(14, 3);
    public static final String DEFAULT_CRON = "0 0 7 * * *";
    public static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";
    public static final int DEFAULT_WINDOW_DAYS = 30;
    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(8, 0);
    public static final String DEFAULT_EVENT_NAME_TEMPLATE = "Đám giỗ {name} ({lunar})";

    public DeathAnniversaryProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        cron = (cron == null || cron.isBlank()) ? DEFAULT_CRON : cron.trim();
        zone = (zone == null || zone.isBlank()) ? DEFAULT_ZONE : zone.trim();
        milestones = (milestones == null || milestones.isEmpty())
                ? DEFAULT_MILESTONES
                : milestones.stream()
                        .filter(days -> days != null && days >= 0)
                        .distinct()
                        .sorted((a, b) -> Integer.compare(b, a))
                        .toList();
        if (milestones.isEmpty()) {
            milestones = DEFAULT_MILESTONES;
        }
        windowDays = (windowDays == null || windowDays <= 0) ? DEFAULT_WINDOW_DAYS : windowDays;
        reminderTime = reminderTime == null ? DEFAULT_REMINDER_TIME : reminderTime;
        eventNameTemplate = (eventNameTemplate == null || eventNameTemplate.isBlank())
                ? DEFAULT_EVENT_NAME_TEMPLATE
                : eventNameTemplate.trim();
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
