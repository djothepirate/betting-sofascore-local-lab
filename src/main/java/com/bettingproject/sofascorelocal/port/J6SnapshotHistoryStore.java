package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;

import java.util.Map;
import java.util.Set;

public interface J6SnapshotHistoryStore {

    Map<Long, J6SnapshotTrace> findTraces(Set<Long> snapshotIds);
}
