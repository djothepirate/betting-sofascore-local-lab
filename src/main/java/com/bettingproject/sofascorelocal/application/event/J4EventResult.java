package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;

import java.util.Objects;
import java.util.Optional;

/** Display only the provider's explicit display scores, never a calculated or current fallback. */
public record J4EventResult(boolean awarded, Optional<Integer> homeScore, Optional<Integer> awayScore) {
    public J4EventResult {
        homeScore = Objects.requireNonNull(homeScore, "homeScore");
        awayScore = Objects.requireNonNull(awayScore, "awayScore");
    }

    public static J4EventResult absent() {
        return new J4EventResult(false, Optional.empty(), Optional.empty());
    }

    public static J4EventResult from(EventDetails details) {
        return new J4EventResult(details.isAwarded().orElse(false),
                details.homeDisplayScore(), details.awayDisplayScore());
    }

    public String score() {
        return homeScore.isPresent() && awayScore.isPresent()
                ? homeScore.orElseThrow() + " – " + awayScore.orElseThrow() : "—";
    }

    public String statusLabel(ScheduledEventStatus status) {
        if ("finished".equals(status.type()) && awarded) return "Victoire sur tapis vert";
        return "inprogress".equals(status.type()) ? status.description().orElse(status.type()) : status.type();
    }
}
