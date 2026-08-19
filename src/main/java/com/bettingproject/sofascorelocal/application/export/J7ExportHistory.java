package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;

import java.util.List;
import java.util.Objects;

public record J7ExportHistory(
        CanonicalEventObservationView currentEvent,
        List<J7ExportManifest> exports) {

    public J7ExportHistory {
        currentEvent = Objects.requireNonNull(currentEvent, "currentEvent");
        exports = List.copyOf(Objects.requireNonNull(exports, "exports"));
    }
}
