package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService.RuntimeStatus;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LiveCampaignPresentationTest {
    private static final UUID CAMPAIGN = UUID.fromString("00000000-0000-0000-0000-000000000058");
    private static final CanonicalEventIdentity IDENTITY = CanonicalEventIdentity.sofascore(900001L);
    private static final UUID EVENT = IDENTITY.value();
    private static final Instant START = Instant.parse("2026-09-07T12:00:00Z");
    private final CanonicalEventStore events = mock(CanonicalEventStore.class);
    private final J5EventDataStore data = mock(J5EventDataStore.class);
    private final LiveCampaignPresentation presentation = new LiveCampaignPresentation(events, data);

    @Test
    void unavailableLatestReceiptRetainsPreciselyTheLastReadableOccurrence() {
        UUID successfulId = UUID.randomUUID();
        UUID unavailableId = UUID.randomUUID();
        var refs = new NormalizedReferences(null, null, 11L, "a".repeat(64));
        var success = new Result(successfulId, new Publication("PARSED", "EVENT", "OK", START.plusSeconds(120),
                "statistics-v2", true, "COLLECTING", null, null, null, "COMPLETE", 100), refs);
        var unavailable = new Result(unavailableId, new Publication("UNAVAILABLE", "EVENT", "HTTP_404",
                START.plusSeconds(180), "statistics-v2", false, null,
                null, null, null, "UNAVAILABLE", 0), NormalizedReferences.none());
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_STATISTICS, unavailableId, unavailableId,
                successfulId, successfulId, START.plusSeconds(180), START.plusSeconds(120),
                START.plusSeconds(120), refs, unavailable, success);
        var normalizedA = new EventStatistics(900001L, List.of(new EventStatisticMetric("ALL", "Match",
                "shots", "Tirs", Optional.of("3"), Optional.of("4"))));
        when(data.findByObservationId(EVENT, SofascoreEndpointType.EVENT_STATISTICS, 11L))
                .thenReturn(Optional.of(new J5EventDataObservationView(11, IDENTITY, normalizedA,
                        EventSourceTrace.providerSnapshot(1, "b".repeat(64), "statistics-v2", START),
                        J5CompletenessReport.measured(1, 1, List.of()), "a".repeat(64))));
        var campaign = campaign(List.of(family), List.of(
                attempt(successfulId, SofascoreEndpointType.EVENT_STATISTICS, 3, START.plusSeconds(120), success),
                attempt(unavailableId, SofascoreEndpointType.EVENT_STATISTICS, 4, START.plusSeconds(180), unavailable)));

        var result = presentation.state(campaign).events().getFirst().families().stream()
                .filter(f -> f.endpoint().equals("EVENT_STATISTICS")).findFirst().orElseThrow();

        assertThat(result.outcome()).isEqualTo("UNAVAILABLE");
        assertThat(result.previousData()).isTrue();
        assertThat(result.lastReceivedAt()).isEqualTo(START.plusSeconds(180));
        assertThat(result.lastSuccessfulAt()).isEqualTo(START.plusSeconds(120));
        assertThat(result.receivedSnapshotId()).isEqualTo(4L);
        assertThat(result.dataSnapshotId()).isEqualTo(3L);
        assertThat(result.completeness()).isEqualTo("UNAVAILABLE");
        assertThat(result.table().rows()).containsExactly(List.of("ALL", "Match", "Tirs", "3", "4"));
        assertThat(result.payloadSha256()).isEqualTo("b".repeat(64));
        verify(data).findByObservationId(EVENT, SofascoreEndpointType.EVENT_STATISTICS, 11L);
        verifyNoMoreInteractions(data);
    }

    @Test
    void missingScoreRemainsUnknownWhileAnObservedZeroIsDisplayed() {
        UUID id = UUID.randomUUID();
        String projection = """
                {"version":"j4-live-score-v1","homeScore":{"presence":"VALUE","value":{
                "current":{"presence":"VALUE","value":0}}},"awayScore":{"presence":"NULL"}}
                """;
        var refs = new NormalizedReferences(1L, 1L, null, "a".repeat(64));
        var result = new Result(id, new Publication("PARSED", "EVENT", "OK", START,
                "event-details-v2", true, "COLLECTING", "inprogress", projection,
                "j4-live-score-v1", null, null), refs);
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, id, id, id, id,
                START, START, START, refs, result, result);

        var event = presentation.state(campaign(List.of(family),
                List.of(attempt(id, SofascoreEndpointType.EVENT_DETAILS, 1, START, result)))).events().getFirst();

        assertThat(event.score()).isEqualTo("0 – —");
        assertThat(event.sportStatus()).isEqualTo("inprogress");
        assertThat(event.sportStatusLabel()).isEqualTo("inprogress");
    }

    @ParameterizedTest
    @CsvSource({"inprogress,1st half,1st half", "inprogress,Halftime,Halftime",
            "inprogress,2nd half,2nd half", "inprogress,,inprogress", "finished,Ended,finished"})
    void periodLabelUsesTheCampaignsExactObservationWithoutChangingItsTechnicalType(
            String type, String description, String label) {
        var identity = new CanonicalEventObservationView(2, IDENTITY, START,
                new ScheduledTeam(1, "Home"), new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus(type, Optional.ofNullable(description)), Optional.empty(),
                EventSourceTrace.providerSnapshot(2, "a".repeat(64), "event-details-v2", START),
                "b".repeat(64), 2);
        var newerManual = new CanonicalEventObservationView(3, IDENTITY, START,
                identity.homeTeam(), identity.awayTeam(),
                new ScheduledEventStatus("inprogress", Optional.of("Newer manual description")), Optional.empty(),
                EventSourceTrace.providerSnapshot(3, "c".repeat(64), "event-details-v2", START.plusSeconds(60)),
                "d".repeat(64), 3);
        when(events.findByObservationId(EVENT, 2)).thenReturn(Optional.of(identity));
        when(events.findLatestByCanonicalId(EVENT)).thenReturn(Optional.of(newerManual));
        UUID id = UUID.randomUUID();
        var refs = new NormalizedReferences(2L, 2L, null, "a".repeat(64));
        var result = new Result(id, new Publication("PARSED", "EVENT", "OK", START,
                "event-details-v2", true, "COLLECTING", type, null, null, null, null), refs);
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, id, id, id, id,
                START, START, START, refs, result, result);

        var event = presentation.state(campaign(List.of(family),
                List.of(attempt(id, SofascoreEndpointType.EVENT_DETAILS, 2, START, result)))).events().getFirst();

        assertThat(event.sportStatus()).isEqualTo(type);
        assertThat(event.sportStatusLabel()).isEqualTo(label);
        assertThat(event.sourceSnapshotId()).isEqualTo(2L);
        assertThat(event.canonicalCurrent()).isFalse();
    }

    @Test
    void pendingAttemptDoesNotReuseThePreviousSuccessfulOutcomeAsItsOwn() {
        UUID successId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        var result = new Result(successId, new Publication("PARSED", "EVENT", "OK", START,
                "event-details-v2", true, null), NormalizedReferences.none());
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, pendingId, successId,
                successId, successId, START, START, START, NormalizedReferences.none(), result, result);
        var value = presentation.state(campaign(List.of(family),
                List.of(attempt(successId, SofascoreEndpointType.EVENT_DETAILS, 1, START, result))))
                .events().getFirst().families().getFirst();
        assertThat(value.outcome()).isEqualTo("PENDING");
        assertThat(value.previousData()).isTrue();
        assertThat(value.code()).isNull();
    }

    @Test
    void aNewerManualObservationPreventsMirroringAnOlderLiveStatusButPreservesCampaignEvidence() {
        var old = new CanonicalEventObservationView(1, IDENTITY, START,
                new ScheduledTeam(1, "Home"), new ScheduledTeam(2, "Away"),
                new ScheduledEventStatus("notstarted", Optional.empty()), Optional.empty(),
                EventSourceTrace.providerSnapshot(1, "a".repeat(64), "event-details-v2", START),
                "b".repeat(64), 2);
        var manual = new CanonicalEventObservationView(2, IDENTITY, START,
                old.homeTeam(), old.awayTeam(), new ScheduledEventStatus("inprogress", Optional.empty()),
                Optional.empty(), EventSourceTrace.providerSnapshot(2, "c".repeat(64),
                "event-details-v2", START.plusSeconds(60)), "d".repeat(64), 2);
        when(events.findByObservationId(EVENT, 1)).thenReturn(Optional.of(old));
        when(events.findLatestByCanonicalId(EVENT)).thenReturn(Optional.of(manual));

        var event = presentation.state(campaign(List.of(), List.of())).events().getFirst();

        assertThat(event.canonicalCurrent()).isFalse();
        assertThat(event.sportStatus()).isEqualTo("notstarted");
        assertThat(event.sourceSnapshotId()).isEqualTo(1L);
        assertThat(event.sourceReceivedAt()).isEqualTo(START);
        assertThat(event.canonicalEventId()).isEqualTo(EVENT);
    }

    @Test
    void allFourFamiliesRemainExplicitBeforeTheirFirstAttempt() {
        var families = presentation.state(campaign(List.of(), List.of())).events().getFirst().families();
        assertThat(families).extracting(LiveCampaignPresentation.Family::endpoint)
                .containsExactly("EVENT_DETAILS", "EVENT_STATISTICS", "EVENT_INCIDENTS", "EVENT_LINEUPS");
        assertThat(families).allSatisfy(family -> {
            assertThat(family.outcome()).isEqualTo("NOT_REQUESTED");
            assertThat(family.lastAttemptAt()).isNull();
            assertThat(family.table().rows()).isEmpty();
        });
    }

    @ParameterizedTest
    @CsvSource({"EVENT_DETAILS,WAITING_START,120,FRESH", "EVENT_DETAILS,WAITING_START,121,STALE",
            "EVENT_DETAILS,CHECKING_FINISH,121,STALE", "EVENT_DETAILS,COLLECTING,600,FRESH",
            "EVENT_DETAILS,COLLECTING,601,STALE", "EVENT_STATISTICS,COLLECTING,121,STALE",
            "EVENT_INCIDENTS,CHECKING_FINISH,121,STALE", "EVENT_LINEUPS,WAITING_START,601,NOT_EXPECTED"})
    void freshnessUsesTwoIntervalsOfTheCurrentPhase(String endpoint, String phase, long elapsed, String expected) {
        var view = campaignForFreshness(SofascoreEndpointType.valueOf(endpoint), phase, List.of());
        var result = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(elapsed), ZoneOffset.UTC))
                .state(view).events().getFirst().families().stream().filter(f -> f.endpoint().equals(endpoint))
                .findFirst().orElseThrow().freshness();
        assertThat(result.state()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"4,90,180,FRESH", "4,90,181,STALE", "5,120,240,FRESH", "5,120,241,STALE",
            "10,270,540,FRESH", "10,270,541,STALE", "25,720,1440,FRESH", "25,720,1441,STALE"})
    void adaptiveFreshnessUsesThePersistedCadence(int count, int seconds, int elapsed, String expected) {
        var base = campaignForFreshness(SofascoreEndpointType.EVENT_STATISTICS, "COLLECTING", List.of());
        var old = base.manifest();
        var targets = java.util.stream.IntStream.range(0, count).mapToObj(i -> new Target(
                CanonicalEventIdentity.sofascore(900001L + i).value(), 900001L + i, 1, 1)).toList();
        var manifest = new Manifest(old.campaignId(), old.manifestSha256(), "live-v2", old.preparedAt(), old.expiresAt(),
                old.duration(), 1000, 3000, old.maximumBytes(), 25, targets, old.admissionProfile(), Duration.ofSeconds(seconds));
        var view = new CampaignView(manifest, base.state(), null, base.startedAt(), base.endsAt(), base.reservedCalls(),
                base.receivedBytes(), base.revision(), null, base.events(), base.attempts(), base.transitions());
        var freshness = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(elapsed), ZoneOffset.UTC))
                .state(view).events().getFirst().families().stream().filter(f -> f.endpoint().equals("EVENT_STATISTICS"))
                .findFirst().orElseThrow().freshness();
        assertThat(freshness.expectedIntervalSeconds()).isEqualTo(seconds);
        assertThat(freshness.state()).isEqualTo(expected);
    }

    @Test
    void terminalAgeRemainsFrozenAtThePersistedTransition() {
        var view = campaignForFreshness(SofascoreEndpointType.EVENT_STATISTICS, "FINISHED_CONFIRMED",
                List.of(new Transition(10, EVENT, "FINISHED_CONFIRMED", null, START.plusSeconds(90), null)));
        for (long elapsed : List.of(3600L, 7200L)) {
            var result = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(elapsed), ZoneOffset.UTC))
                    .state(view).events().getFirst().families().stream()
                    .filter(f -> f.endpoint().equals("EVENT_STATISTICS")).findFirst().orElseThrow().freshness();
            assertThat(result.state()).isEqualTo("FROZEN");
            assertThat(result.receivedAgeSeconds()).isEqualTo(90L);
            assertThat(result.expectedIntervalSeconds()).isZero();
        }
    }

    @ParameterizedTest
    @CsvSource({"live-v2,EVENT_LINEUPS,NOT_EXPECTED,0", "live-v3,EVENT_LINEUPS,FRESH,60",
            "live-v3,EVENT_STATISTICS,NOT_EXPECTED,0", "live-v3,EVENT_INCIDENTS,NOT_EXPECTED,0"})
    void prematchExpectationsFollowTheFrozenPolicy(String policy, String endpoint, String expected, long interval) {
        var base = campaignForFreshness(SofascoreEndpointType.valueOf(endpoint), "WAITING_START", List.of());
        var old = base.manifest();
        var manifest = new Manifest(old.campaignId(), old.manifestSha256(), policy, old.preparedAt(), old.expiresAt(),
                old.duration(), old.maximumCallsPerEvent(), old.maximumCalls(), old.maximumBytes(),
                old.qualifiedMatchCapacity(), old.targets(), old.admissionProfile(), old.cycleInterval());
        var view = new CampaignView(manifest, base.state(), null, base.startedAt(), base.endsAt(),
                base.reservedCalls(), base.receivedBytes(), base.revision(), null, base.events(), base.attempts(), base.transitions());
        var freshness = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(60), ZoneOffset.UTC))
                .state(view).events().getFirst().families().stream().filter(f -> f.endpoint().equals(endpoint))
                .findFirst().orElseThrow().freshness();
        assertThat(freshness.state()).isEqualTo(expected);
        assertThat(freshness.expectedIntervalSeconds()).isEqualTo(interval);
    }

    @ParameterizedTest
    @CsvSource({"false,false,Collecte arrêtée.", "true,false,Collecte arrêtée / clôture locale requise.",
            "true,true,Collecte arrêtée / clôture locale en cours."})
    void runtimeObservationDoesNotReplaceDurableStateOrCreateATerminalTransition(
            boolean cleanupPending, boolean cleanupInProgress, String label) {
        var view = campaignForFreshness(SofascoreEndpointType.EVENT_DETAILS, "COLLECTING", List.of());
        var at = START.plusSeconds(45);
        var fixed = new LiveCampaignPresentation(events, data, Clock.fixed(at, ZoneOffset.UTC));
        var durable = fixed.state(view);
        var projected = fixed.state(view, new RuntimeStatus("STOPPED_ERROR", "LOCAL_CLEANUP_PENDING",
                true, cleanupPending, cleanupInProgress));

        assertThat(durable.runtimeStatus()).isNull();
        assertThat(projected.state()).isEqualTo("RUNNING");
        assertThat(projected.revision()).isEqualTo(durable.revision());
        assertThat(projected.reason()).isEqualTo(durable.reason());
        assertThat(projected.events()).isEqualTo(durable.events());
        assertThat(projected.runtimeStatus().state()).isEqualTo("STOPPED_ERROR");
        assertThat(projected.runtimeStatus().label()).isEqualTo(label);
        assertThat(projected.runtimeStatus().collectionStopped()).isTrue();
        assertThat(projected.runtimeStatus().cleanupPending()).isEqualTo(cleanupPending);
        assertThat(projected.runtimeStatus().cleanupInProgress()).isEqualTo(cleanupInProgress);
        assertThat(projected.events().getFirst().families().getFirst().freshness().frozen()).isFalse();
        assertThat(projected.events().getFirst().families().getFirst().freshness().ageAsOf()).isEqualTo(at);
    }

    @Test
    void interruptedCampaignFreezesAtItsPersistedRecoveryTransition() {
        var base = campaignForFreshness(SofascoreEndpointType.EVENT_STATISTICS, "COLLECTING", List.of());
        var interruptedAt = START.plusSeconds(125);
        var view = new CampaignView(base.manifest(), "INTERRUPTED", "INTERRUPTED", base.startedAt(),
                base.endsAt(), base.reservedCalls(), base.receivedBytes(), 11, null, base.events(), base.attempts(),
                List.of(new Transition(11, null, "INTERRUPTED", "INTERRUPTED", interruptedAt, null)));

        var result = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(7200), ZoneOffset.UTC))
                .state(view).events().getFirst().families().stream()
                .filter(f -> f.endpoint().equals("EVENT_STATISTICS")).findFirst().orElseThrow().freshness();

        assertThat(result.state()).isEqualTo("FROZEN");
        assertThat(result.ageAsOf()).isEqualTo(interruptedAt);
        assertThat(result.receivedAgeSeconds()).isEqualTo(125);
        assertThat(result.expectedIntervalSeconds()).isZero();
    }

    private static CampaignView campaignForFreshness(SofascoreEndpointType endpoint, String phase,
                                                     List<Transition> transitions) {
        UUID id = UUID.randomUUID();
        var result = new Result(id, new Publication("PARSED", "EVENT", "OK", START,
                "test-v1", true, phase), NormalizedReferences.none());
        var cursor = new FamilyCursor(endpoint, id, id, id, id, START, START, START,
                NormalizedReferences.none(), result, result);
        var base = campaign(List.of(cursor), List.of(attempt(id, endpoint, 1, START, result)));
        return new CampaignView(base.manifest(), base.state(), null, START, base.endsAt(), 1, 0, 10,
                null, List.of(new EventView(base.manifest().targets().getFirst(), phase, null,
                1, 0, null, List.of(cursor))), base.attempts(), transitions);
    }

    private static AttemptView attempt(UUID id, SofascoreEndpointType endpoint, long snapshot,
                                       Instant at, Result result) {
        return new AttemptView(new ReservedAttempt(id, EVENT, 900001L, endpoint, snapshot,
                "NORMAL", at.minusMillis(12), at.minusMillis(12), false), at,
                snapshot, snapshot, at, result);
    }

    private static CampaignView campaign(List<FamilyCursor> families, List<AttemptView> attempts) {
        var target = new Target(EVENT, 900001L, 1L, 1L);
        var manifest = new Manifest(CAMPAIGN, "a".repeat(64), "live-v1", START, START.plusSeconds(300),
                Duration.ofHours(4), 1000, 3000, 1_000_000, 1, List.of(target));
        return new CampaignView(manifest, "RUNNING", null, START, START.plusSeconds(14400),
                attempts.size(), 0, 10, null, List.of(new EventView(target, "COLLECTING", null,
                attempts.size(), 0, START.plusSeconds(240), families)), attempts, List.of());
    }
}
