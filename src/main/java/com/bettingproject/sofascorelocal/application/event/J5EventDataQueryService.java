package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class J5EventDataQueryService {

    private final J4EventQueryService eventQueryService;
    private final J5EventDataStore eventDataStore;

    public J5EventDataQueryService(
            J4EventQueryService eventQueryService,
            J5EventDataStore eventDataStore) {
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
    }

    public Optional<J5EventDataPage> find(UUID canonicalEventId, String zoneId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        return eventQueryService.findDetail(canonicalEventId, zoneId)
                .map(detail -> new J5EventDataPage(
                        detail.zoneId(),
                        detail.current(),
                        eventDataStore.findLatest(canonicalEventId)));
    }
}
