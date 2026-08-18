package com.bettingproject.sofascorelocal.domain.history;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;

import java.util.Objects;

public record J6CompletenessSummary(
        J5CompletenessStatus status,
        int scorePercent,
        int presentSignals,
        int expectedSignals) {

    public J6CompletenessSummary {
        status = Objects.requireNonNull(status, "status");
        if (scorePercent < 0 || scorePercent > 100
                || presentSignals < 0
                || expectedSignals < 0
                || presentSignals > expectedSignals) {
            throw new IllegalArgumentException("completeness summary is inconsistent");
        }
    }

    public static J6CompletenessSummary from(J5CompletenessReport report) {
        Objects.requireNonNull(report, "report");
        return new J6CompletenessSummary(
                report.status(),
                report.scorePercent(),
                report.presentSignals(),
                report.expectedSignals());
    }
}
