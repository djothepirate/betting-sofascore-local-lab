package com.bettingproject.sofascorelocal.application.event;

import java.util.List;
import java.util.Objects;

public record J5OfflineBatchPolicySnapshot(
        boolean available,
        List<String> blockers) {

    public J5OfflineBatchPolicySnapshot {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        if (available != blockers.isEmpty()) {
            throw new IllegalArgumentException("offline availability and blockers conflict");
        }
    }
}
