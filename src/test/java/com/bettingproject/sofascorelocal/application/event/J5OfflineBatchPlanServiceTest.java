package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class J5OfflineBatchPlanServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T08:00:00Z");
    private static final LocalDate DATE = LocalDate.parse("2026-08-22");
    private static final UUID REQUEST_ID = UUID.fromString(
            "51000000-0000-0000-0000-000000000010");

    private final J4EventQueryService queryService = mock(J4EventQueryService.class);
    private final CanonicalEventStore canonicalEventStore = mock(CanonicalEventStore.class);
    private final J5OfflineBatchPlanService service = new J5OfflineBatchPlanService(
            queryService,
            canonicalEventStore,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> REQUEST_ID);

    @Test
    void createsAStableProviderOrderedPlanBoundToCurrentObservations() {
        CanonicalEventObservationView laterId = event(20L, 2002L, "Home B", "Away B");
        CanonicalEventObservationView earlierId = event(10L, 1001L, "Home A", "Away A");
        J4EventSearchResult search = search(laterId, earlierId);
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search);

        J5OfflineBatchPlan plan = service.create(
                DATE,
                "Europe/Paris",
                List.of(laterId.identity().value(), earlierId.identity().value()));

        assertThat(plan.requestId()).isEqualTo(REQUEST_ID);
        assertThat(plan.events()).extracting(J5OfflineBatchPlanEvent::providerEventId)
                .containsExactly(1001L, 2002L);
        assertThat(plan.events().getFirst().expectedFileNames()).containsExactly(
                "event-1001-statistics.json",
                "event-1001-incidents.json",
                "event-1001-lineups.json");
        assertThat(plan.payloadCount()).isEqualTo(6);
        assertThat(plan.expiresAt()).isEqualTo(
                NOW.plus(J5OfflineBatchPlanService.CONFIRMATION_TTL));
        assertThat(plan.planSha256()).matches("[0-9a-f]{64}");
        assertThat(plan.confirmationPhrase()).isEqualTo(
                "IMPORTER 2 MATCHS J5 HORS LIGNE " + plan.planSha256());

        J5OfflineBatchPlan sameData = service.create(
                DATE,
                "Europe/Paris",
                List.of(earlierId.identity().value(), laterId.identity().value()));
        assertThat(sameData.planSha256()).isEqualTo(plan.planSha256());
    }

    @Test
    void createsTheMaximumTwentyFiveEventPlanAndSeventyFiveFileManifest() {
        List<CanonicalEventObservationView> events = new ArrayList<>();
        for (int index = J5OfflineBatchPlanService.MAXIMUM_EVENTS; index >= 1; index--) {
            events.add(event(index, 1000L + index, "Home " + index, "Away " + index));
        }
        when(queryService.search(DATE, "Europe/Paris"))
                .thenReturn(search(events.toArray(CanonicalEventObservationView[]::new)));

        J5OfflineBatchPlan plan = service.create(
                DATE,
                "Europe/Paris",
                events.stream().map(value -> value.identity().value()).toList());

        assertThat(plan.events()).hasSize(25);
        assertThat(plan.events()).extracting(J5OfflineBatchPlanEvent::providerEventId)
                .containsExactlyElementsOf(
                        java.util.stream.LongStream.rangeClosed(1001L, 1025L)
                                .boxed()
                                .toList());
        assertThat(plan.payloadCount()).isEqualTo(75);
        assertThat(plan.events().getLast().expectedFileNames()).containsExactly(
                "event-1025-statistics.json",
                "event-1025-incidents.json",
                "event-1025-lineups.json");
    }

    @Test
    void rejectsMissingDuplicateAndOversizedSelections() {
        CanonicalEventObservationView event = event(1L, 1001L, "Home", "Away");
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(event));

        assertError(
                () -> service.create(DATE, "Europe/Paris", List.of()),
                J5OfflineBatchError.INVALID_SELECTION);
        assertError(
                () -> service.create(DATE, "Europe/Paris", List.of(
                        event.identity().value(), event.identity().value())),
                J5OfflineBatchError.INVALID_SELECTION);
        assertError(
                () -> service.create(DATE, "Europe/Paris", List.of(
                        CanonicalEventIdentity.sofascore(9999L).value())),
                J5OfflineBatchError.EVENT_NOT_FOUND);

        List<CanonicalEventObservationView> tooMany = new ArrayList<>();
        for (int index = 1; index <= J5OfflineBatchPlanService.MAXIMUM_EVENTS + 1; index++) {
            tooMany.add(event(index, 2000L + index, "Home " + index, "Away " + index));
        }
        assertError(
                () -> service.create(
                        DATE,
                        "Europe/Paris",
                        tooMany.stream().map(value -> value.identity().value()).toList()),
                J5OfflineBatchError.INVALID_SELECTION);
    }

    @Test
    void excludesRequestIdentityAndPreparationTimeFromTheDeterministicHash() {
        CanonicalEventObservationView event = event(1L, 1001L, "Home", "Away");
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(event));
        AtomicInteger sequence = new AtomicInteger(10);
        MutableClock mutableClock = new MutableClock(NOW);
        J5OfflineBatchPlanService changingMetadataService = new J5OfflineBatchPlanService(
                queryService,
                canonicalEventStore,
                mutableClock,
                () -> new UUID(0x5100000000000000L, sequence.getAndIncrement()));

        J5OfflineBatchPlan first = changingMetadataService.create(
                DATE, "Europe/Paris", List.of(event.identity().value()));
        mutableClock.advance(java.time.Duration.ofMinutes(3));
        J5OfflineBatchPlan second = changingMetadataService.create(
                DATE, "Europe/Paris", List.of(event.identity().value()));

        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(second.preparedAt()).isAfter(first.preparedAt());
        assertThat(second.planSha256()).isEqualTo(first.planSha256());
    }

    @Test
    void protectsCanonicalObservationIdentityHashAndStartTimeInThePlanHash() {
        CanonicalEventObservationView baseline = event(1L, 1001L, "Home", "Away");
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(baseline));
        String baselineHash = service.create(
                DATE, "Europe/Paris", List.of(baseline.identity().value())).planSha256();

        CanonicalEventObservationView changedObservation = event(2L, 1001L, "Home", "Away");
        when(queryService.search(DATE, "Europe/Paris"))
                .thenReturn(search(changedObservation));
        assertThat(service.create(
                DATE,
                "Europe/Paris",
                List.of(changedObservation.identity().value())).planSha256())
                .isNotEqualTo(baselineHash);

        CanonicalEventObservationView changedProvider = event(1L, 1002L, "Home", "Away");
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(changedProvider));
        assertThat(service.create(
                DATE,
                "Europe/Paris",
                List.of(changedProvider.identity().value())).planSha256())
                .isNotEqualTo(baselineHash);

        CanonicalEventObservationView changedStart = eventAt(
                1L, 1001L, Instant.parse("2026-08-22T15:00:00Z"), "b".repeat(64));
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(changedStart));
        assertThat(service.create(
                DATE,
                "Europe/Paris",
                List.of(changedStart.identity().value())).planSha256())
                .isNotEqualTo(baselineHash);

        CanonicalEventObservationView changedHash = eventAt(
                1L, 1001L, Instant.parse("2026-08-22T14:00:00Z"), "c".repeat(64));
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(changedHash));
        assertThat(service.create(
                DATE,
                "Europe/Paris",
                List.of(changedHash.identity().value())).planSha256())
                .isNotEqualTo(baselineHash);
    }

    @Test
    void preservesShortAndLongDstDayBoundariesInThePlan() {
        assertDstDay(java.time.LocalDate.parse("2026-03-29"), 23L, 3001L);
        assertDstDay(java.time.LocalDate.parse("2026-10-25"), 25L, 3002L);
    }

    @Test
    void detectsAnyCanonicalObservationDriftBeforeExecution() {
        CanonicalEventObservationView planned = event(10L, 1001L, "Home", "Away");
        when(queryService.search(DATE, "Europe/Paris")).thenReturn(search(planned));
        J5OfflineBatchPlan plan = service.create(
                DATE, "Europe/Paris", List.of(planned.identity().value()));
        CanonicalEventObservationView changed = event(11L, 1001L, "Home", "Away");
        when(canonicalEventStore.findLatestByCanonicalId(planned.identity().value()))
                .thenReturn(Optional.of(changed));

        assertError(() -> service.requireCurrent(plan), J5OfflineBatchError.PLAN_CHANGED);
    }

    private static J4EventSearchResult search(CanonicalEventObservationView... events) {
        return search(DATE, ZoneId.of("Europe/Paris"), events);
    }

    private static J4EventSearchResult search(
            LocalDate date,
            ZoneId zone,
            CanonicalEventObservationView... events) {
        return new J4EventSearchResult(
                date,
                zone,
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
                java.util.Arrays.stream(events)
                        .map(event -> new J4EventSearchItem(
                                event, event.startsAt().atZone(zone)))
                        .toList());
    }

    private static CanonicalEventObservationView event(
            long observationId,
            long providerEventId,
            String home,
            String away) {
        return eventAt(
                observationId,
                providerEventId,
                Instant.parse("2026-08-22T14:00:00Z"),
                "%064x".formatted(observationId),
                home,
                away);
    }

    private static CanonicalEventObservationView eventAt(
            long observationId,
            long providerEventId,
            Instant startsAt,
            String normalizedSha256) {
        return eventAt(
                observationId,
                providerEventId,
                startsAt,
                normalizedSha256,
                "Home",
                "Away");
    }

    private static CanonicalEventObservationView eventAt(
            long observationId,
            long providerEventId,
            Instant startsAt,
            String normalizedSha256,
            String home,
            String away) {
        return new CanonicalEventObservationView(
                observationId,
                CanonicalEventIdentity.sofascore(providerEventId),
                startsAt,
                new ScheduledTeam(providerEventId * 10, home),
                new ScheduledTeam(providerEventId * 10 + 1, away),
                new ScheduledEventStatus("notstarted", Optional.of("Not started")),
                Optional.empty(),
                EventSourceTrace.syntheticFixture(
                        "j5-batch-plan-test",
                        "a".repeat(64),
                        "scheduled-events-v1",
                        NOW),
                normalizedSha256,
                1L);
    }

    private void assertDstDay(LocalDate date, long expectedHours, long providerEventId) {
        ZoneId zone = ZoneId.of("Europe/Paris");
        Instant startsAt = date.atTime(12, 0).atZone(zone).toInstant();
        CanonicalEventObservationView event = eventAt(
                providerEventId,
                providerEventId,
                startsAt,
                "%064x".formatted(providerEventId));
        when(queryService.search(date, zone.getId())).thenReturn(search(date, zone, event));

        J5OfflineBatchPlan plan = service.create(
                date, zone.getId(), List.of(event.identity().value()));

        assertThat(java.time.Duration.between(plan.fromInclusive(), plan.toExclusive()))
                .isEqualTo(java.time.Duration.ofHours(expectedHours));
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(java.time.Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static void assertError(Runnable action, J5OfflineBatchError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J5OfflineBatchException.class)
                .extracting(exception -> ((J5OfflineBatchException) exception).error())
                .isEqualTo(error);
    }
}
