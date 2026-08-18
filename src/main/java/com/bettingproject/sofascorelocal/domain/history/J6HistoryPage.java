package com.bettingproject.sofascorelocal.domain.history;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record J6HistoryPage(
        CanonicalEventObservationView currentEvent,
        Optional<J6HistoryStream> selectedStream,
        int page,
        int size,
        long totalVersions,
        int totalPages,
        List<J6HistoryVersion> versions) {

    public J6HistoryPage {
        currentEvent = Objects.requireNonNull(currentEvent, "currentEvent");
        selectedStream = Objects.requireNonNull(selectedStream, "selectedStream");
        if (page < 0 || size < 1 || size > 100
                || totalVersions < 0 || totalPages < 0) {
            throw new IllegalArgumentException("history pagination is invalid");
        }
        versions = List.copyOf(Objects.requireNonNull(versions, "versions"));
        int expectedPages = totalVersions == 0
                ? 0
                : Math.toIntExact((totalVersions + size - 1) / size);
        if (totalPages != expectedPages || versions.size() > size) {
            throw new IllegalArgumentException("history page counts are inconsistent");
        }
    }

    public boolean hasPreviousPage() {
        return page > 0;
    }

    public boolean hasNextPage() {
        return page + 1 < totalPages;
    }
}
