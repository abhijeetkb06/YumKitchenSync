package com.kitchensync.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Time formatting utilities for displaying elapsed order durations
 * in the kitchen display and status board.
 */
public final class TimeUtils {

    private TimeUtils() {}

    public static String getElapsedTime(String isoTimestamp) {
        if (isoTimestamp == null) return "";
        try {
            Instant then = Instant.parse(isoTimestamp);
            Duration duration = Duration.between(then, Instant.now());
            long minutes = duration.toMinutes();
            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + "m ago";
            long hours = duration.toHours();
            return hours + "h " + (minutes % 60) + "m ago";
        } catch (Exception e) {
            return "";
        }
    }

    public static String getElapsedMinutes(String isoTimestamp) {
        if (isoTimestamp == null) return "0:00";
        try {
            Instant then = Instant.parse(isoTimestamp);
            Duration duration = Duration.between(then, Instant.now());
            long totalSeconds = duration.getSeconds();
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        } catch (Exception e) {
            return "0:00";
        }
    }
}
