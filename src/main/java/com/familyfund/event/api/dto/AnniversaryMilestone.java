package com.familyfund.event.api.dto;

/**
 * Death-anniversary reminder milestones (US-40), stored in {@code death_anniversary_reminders.milestone_days}.
 *
 * <p>The milestone list is configurable ({@code app.death-anniversary.milestones}); the enum only names the two
 * values the product asks for so the notification text and the API can speak about them instead of raw numbers.</p>
 */
public enum AnniversaryMilestone {

    /** 14 days before the anniversary: enough lead time to plan the gathering. */
    BEFORE_14_DAYS(14),

    /** 3 days before the anniversary: the final reminder. */
    BEFORE_3_DAYS(3);

    private final int daysBefore;

    AnniversaryMilestone(int daysBefore) {
        this.daysBefore = daysBefore;
    }

    /** @return number of days between the trigger day and the anniversary itself. */
    public int daysBefore() {
        return daysBefore;
    }

    /**
     * @param daysBefore milestone as configured (any non-negative number of days)
     * @return the matching milestone, never {@code null}; unknown values are mapped to the closest named one
     */
    public static AnniversaryMilestone ofDaysBefore(int daysBefore) {
        for (AnniversaryMilestone milestone : values()) {
            if (milestone.daysBefore == daysBefore) {
                return milestone;
            }
        }
        return daysBefore >= 14 ? BEFORE_14_DAYS : BEFORE_3_DAYS;
    }
}
