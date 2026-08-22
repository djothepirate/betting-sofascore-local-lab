package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class J5OfflineBatchPlanService {

    public static final String PLAN_VERSION = "j5-offline-multi-match-plan-v1";
    public static final int MAXIMUM_EVENTS = 25;
    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(15);

    private final J4EventQueryService eventQueryService;
    private final CanonicalEventStore canonicalEventStore;
    private final Clock clock;
    private final Supplier<UUID> requestIdSupplier;

    @Autowired
    public J5OfflineBatchPlanService(
            J4EventQueryService eventQueryService,
            CanonicalEventStore canonicalEventStore) {
        this(eventQueryService, canonicalEventStore, Clock.systemUTC(), UUID::randomUUID);
    }

    J5OfflineBatchPlanService(
            J4EventQueryService eventQueryService,
            CanonicalEventStore canonicalEventStore,
            Clock clock,
            Supplier<UUID> requestIdSupplier) {
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService");
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore, "canonicalEventStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestIdSupplier = Objects.requireNonNull(requestIdSupplier, "requestIdSupplier");
    }

    public J5OfflineBatchPlan create(
            LocalDate date,
            String zoneId,
            List<UUID> selectedCanonicalEventIds) {
        Objects.requireNonNull(date, "date");
        List<UUID> selected = List.copyOf(Objects.requireNonNull(
                selectedCanonicalEventIds, "selectedCanonicalEventIds"));
        Set<UUID> unique = new HashSet<>(selected);
        if (selected.isEmpty()
                || selected.size() > MAXIMUM_EVENTS
                || unique.size() != selected.size()) {
            throw rejected(J5OfflineBatchError.INVALID_SELECTION);
        }

        J4EventSearchResult search = eventQueryService.search(date, zoneId);
        Map<UUID, CanonicalEventObservationView> available = new HashMap<>();
        search.events().forEach(item -> available.put(
                item.event().identity().value(), item.event()));
        List<J5OfflineBatchPlanEvent> events = new ArrayList<>();
        for (UUID canonicalEventId : selected) {
            CanonicalEventObservationView current = available.get(canonicalEventId);
            if (current == null) {
                throw rejected(J5OfflineBatchError.EVENT_NOT_FOUND);
            }
            long providerEventId = current.identity().providerEventId();
            events.add(new J5OfflineBatchPlanEvent(
                    current.identity().value(),
                    providerEventId,
                    current.observationId(),
                    current.startsAt(),
                    current.homeTeam().name(),
                    current.awayTeam().name(),
                    current.normalizedSha256(),
                    J5OfflineBatchPlanEvent.expectedFileNames(providerEventId)));
        }
        events.sort(Comparator.comparingLong(J5OfflineBatchPlanEvent::providerEventId));

        UUID requestId = Objects.requireNonNull(requestIdSupplier.get(), "requestId");
        Instant preparedAt = clock.instant();
        String planSha256 = hash(
                search.date(),
                search.zoneId().getId(),
                search.fromInclusive(),
                search.toExclusive(),
                events);
        String phrase = "IMPORTER " + events.size()
                + " MATCHS J5 HORS LIGNE " + planSha256;
        return new J5OfflineBatchPlan(
                requestId,
                search.date(),
                search.zoneId(),
                search.fromInclusive(),
                search.toExclusive(),
                preparedAt,
                preparedAt.plus(CONFIRMATION_TTL),
                events,
                planSha256,
                phrase);
    }

    public void requireCurrent(J5OfflineBatchPlan plan) {
        Objects.requireNonNull(plan, "plan");
        for (J5OfflineBatchPlanEvent expected : plan.events()) {
            CanonicalEventObservationView current = canonicalEventStore
                    .findLatestByCanonicalId(expected.canonicalEventId())
                    .orElseThrow(() -> rejected(J5OfflineBatchError.PLAN_CHANGED));
            if (current.identity().providerEventId() != expected.providerEventId()
                    || current.observationId() != expected.canonicalObservationId()
                    || !current.normalizedSha256().equals(expected.canonicalNormalizedSha256())
                    || !current.startsAt().equals(expected.startsAt())
                    || current.startsAt().isBefore(plan.fromInclusive())
                    || !current.startsAt().isBefore(plan.toExclusive())) {
                throw rejected(J5OfflineBatchError.PLAN_CHANGED);
            }
        }
    }

    private static String hash(
            LocalDate date,
            String zoneId,
            Instant fromInclusive,
            Instant toExclusive,
            List<J5OfflineBatchPlanEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(PLAN_VERSION);
                output.writeUTF(date.toString());
                output.writeUTF(zoneId);
                writeInstant(output, fromInclusive);
                writeInstant(output, toExclusive);
                output.writeInt(events.size());
                for (J5OfflineBatchPlanEvent event : events) {
                    output.writeLong(event.canonicalEventId().getMostSignificantBits());
                    output.writeLong(event.canonicalEventId().getLeastSignificantBits());
                    output.writeLong(event.providerEventId());
                    output.writeLong(event.canonicalObservationId());
                    writeInstant(output, event.startsAt());
                    output.writeUTF(event.canonicalNormalizedSha256());
                    for (String fileName : event.expectedFileNames()) {
                        output.writeUTF(fileName);
                    }
                }
            }
            return Sha256.hex(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("unable to hash offline batch plan", exception);
        }
    }

    private static void writeInstant(DataOutputStream output, Instant value) throws IOException {
        output.writeLong(value.getEpochSecond());
        output.writeInt(value.getNano());
    }

    private static J5OfflineBatchException rejected(J5OfflineBatchError error) {
        return new J5OfflineBatchException(error);
    }
}
