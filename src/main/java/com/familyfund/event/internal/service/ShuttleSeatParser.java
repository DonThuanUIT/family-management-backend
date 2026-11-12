package com.familyfund.event.internal.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for resolving shuttle seat counts from explicit values or transportation notes (US-42).
 */
public final class ShuttleSeatParser {

    private static final Pattern SEAT_COUNT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:chỗ|cho|ghế|ghe|vé|ve|suất|suat|người|nguoi|seat|seats)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern SHUTTLE_KEYWORD_PATTERN = Pattern.compile(
            "(?:xe\\s*đưa\\s*đón|xe\\s*du\\s*lich|xe\\s*khách|xe\\s*chung|bus|shuttle)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private ShuttleSeatParser() {
    }

    /**
     * Resolves the effective shuttle seats for a household registration.
     * Prefers positive explicit {@code shuttleSeats}, otherwise extracts seat count from notes.
     */
    public static int resolveSeats(Integer shuttleSeats, String transportationNotes, int totalAttendees) {
        if (shuttleSeats != null && shuttleSeats > 0) {
            return shuttleSeats;
        }
        if (transportationNotes == null || transportationNotes.isBlank()) {
            return 0;
        }
        Matcher seatMatcher = SEAT_COUNT_PATTERN.matcher(transportationNotes);
        if (seatMatcher.find()) {
            try {
                return Math.max(0, Integer.parseInt(seatMatcher.group(1)));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        // If user explicitly asked for shuttle bus without specifying seat count, default to household attendees
        if (SHUTTLE_KEYWORD_PATTERN.matcher(transportationNotes).find()) {
            return Math.max(0, totalAttendees);
        }
        return 0;
    }
}
