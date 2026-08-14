package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.event.J4ScheduledEventsSnapshotSource;

import java.util.Optional;

public interface J4ScheduledEventsSnapshotSourceStore {

    Optional<J4ScheduledEventsSnapshotSource> findById(long snapshotId);
}
