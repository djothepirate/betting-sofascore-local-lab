package com.bettingproject.sofascorelocal.provider.playwright.worker;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Only the validated deadline crosses IPC. Raw header content is never retained. */
final class ProviderRetryAfter {
    static final long MAXIMUM_SECONDS = 365L * 24 * 60 * 60;

    static long deadline(String value, Instant headersAt) {
        if (value == null || value.length() > 80 || value.chars().anyMatch(c -> c < 32 || c > 126)) return -1;
        String bounded = value.trim();
        try {
            Instant deadline;
            if (bounded.matches("[0-9]{1,10}")) {
                long seconds = Long.parseLong(bounded);
                if (seconds > MAXIMUM_SECONDS) return -1;
                deadline = headersAt.plusSeconds(seconds);
            } else {
                if (!bounded.matches("(Mon|Tue|Wed|Thu|Fri|Sat|Sun), [0-9]{2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT")) return -1;
                deadline = ZonedDateTime.parse(bounded, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                if (deadline.isBefore(headersAt)) deadline = headersAt;
                if (deadline.isAfter(headersAt.plusSeconds(MAXIMUM_SECONDS))) return -1;
            }
            return deadline.toEpochMilli();
        } catch (RuntimeException invalid) { return -1; }
    }
    private ProviderRetryAfter() { }
}
