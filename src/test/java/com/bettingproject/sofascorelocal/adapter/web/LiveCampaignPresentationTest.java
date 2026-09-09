package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService.RuntimeStatus;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

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

    @ParameterizedTest @CsvSource({"50,FRESH", "700,STALE"})
    void aDeferredEndedTimeoutKeepsItsDurableProofAndTheActualAgeOfThePreviousData(int elapsed,String expectedFreshness) {
        UUID successfulId=UUID.randomUUID(),timeoutId=UUID.randomUUID();
        var success=new Result(successfulId,new Publication("PARSED","NONE","PARSED",START,"event-details-v3",true,null),NormalizedReferences.none());
        var timeout=new Result(timeoutId,new Publication("FAILED","NONE","PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED",
                START.plusSeconds(41),null,false,null),NormalizedReferences.none());
        var cursor=new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS,timeoutId,successfulId,successfulId,successfulId,
                START,START,START,NormalizedReferences.none(),timeout,success,
                new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,START.plusSeconds(340),300,0));
        var failedAttempt=new AttemptView(new ReservedAttempt(timeoutId,EVENT,900001L,SofascoreEndpointType.EVENT_DETAILS,2,
                "NORMAL",START.plusSeconds(10),START.plusSeconds(10),false),START.plusSeconds(10),null,null,null,timeout);
        var proof=new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,30000,
                START.plusSeconds(10),START.plusSeconds(12),200,null,false)
                .withExchangeEnd(START.plusSeconds(40),PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED,true);
        var diagnostics=mock(LiveDiagnosticStore.class);
        when(diagnostics.findTransport(CAMPAIGN,timeoutId)).thenReturn(Optional.of(proof));
        var restarted=new LiveCampaignPresentation(events,data,null,Clock.fixed(START.plusSeconds(elapsed),ZoneOffset.UTC),diagnostics);
        var projected=restarted.state(campaign(List.of(cursor),List.of(
                attempt(successfulId,SofascoreEndpointType.EVENT_DETAILS,1,START,success),failedAttempt)));
        var family=projected.events().getFirst().families().getFirst();
        assertThat(projected.state()).isEqualTo("RUNNING");assertThat(projected.runtimeStatus()).isNull();
        assertThat(family.outcome()).isEqualTo("FAILED");assertThat(family.scope()).isEqualTo("NONE");
        assertThat(family.code()).isEqualTo("PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED");assertThat(family.transport()).isEqualTo(proof);
        assertThat(family.previousData()).isTrue();assertThat(family.receivedSnapshotId()).isEqualTo(1L);
        assertThat(family.lastReceivedAt()).isEqualTo(START);assertThat(family.lastSuccessfulAt()).isEqualTo(START);
        assertThat(family.schedule().nextDueAt()).isEqualTo(START.plusSeconds(340));
        assertThat(family.freshness().frozen()).isFalse();assertThat(family.freshness().state()).isEqualTo(expectedFreshness);
        assertThat(family.freshness().receivedAgeSeconds()).isEqualTo((long)elapsed);
        verify(diagnostics).findTransport(CAMPAIGN,timeoutId);verifyNoMoreInteractions(diagnostics);
    }

    @Test
    void aNonTerminalRuntimeDiagnosticDoesNotClaimThatCollectionStopped() {
        var diagnostic=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.TRANSPORT,"PLAYWRIGHT_TIMEOUT",START);
        var state=presentation.state(campaign(List.of(),List.of()),
                new RuntimeStatus("RUNNING",null,false,false,false,diagnostic,null));
        assertThat(state.runtimeStatus().collectionStopped()).isFalse();
        assertThat(state.runtimeStatus().label()).isEqualTo("Collecte en cours ; un incident a été enregistré.");
    }

    @ParameterizedTest
    @CsvSource({"PRESENT", "EMPTY", "UNAVAILABLE"})
    void incidentsKeepTheirTechnicalTableAndExactObservationWithoutChangingTheJ4Score(String mode) {
        boolean unavailable = "UNAVAILABLE".equals(mode);
        UUID id = UUID.randomUUID();
        var refs = new NormalizedReferences(null, null, 31L, "a".repeat(64));
        var result = new Result(id, new Publication(unavailable ? "UNAVAILABLE" : "PARSED", "EVENT", "OK",
                START, "event-incidents-v17", !unavailable, null, null, null, null,
                unavailable ? "UNAVAILABLE" : "COMPLETE", unavailable ? 0 : 100), refs);
        var cursor = new FamilyCursor(SofascoreEndpointType.EVENT_INCIDENTS, id, id, id, id,
                START, START, START, refs, result, result);
        var card = new EventIncident(0, "card", 64, Optional.empty(), Optional.of(false),
                Optional.empty(), Optional.of(100L), Optional.of("Joueur source"),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(1), Optional.of(0), Optional.of("red"), Optional.of("Professional handball"));
        var values = new EventIncidents(900001, "PRESENT".equals(mode) ? List.of(card) : List.of());
        when(data.findByObservationId(EVENT, SofascoreEndpointType.EVENT_INCIDENTS, 31L))
                .thenReturn(Optional.of(new J5EventDataObservationView(31, IDENTITY, values,
                        EventSourceTrace.providerSnapshot(7, "b".repeat(64), "event-incidents-v17", START),
                        unavailable ? J5CompletenessReport.unavailable() : J5CompletenessReport.measured(1, 1, List.of()),
                        "a".repeat(64))));
        var event = presentation.state(campaign(List.of(cursor), List.of(
                attempt(id, SofascoreEndpointType.EVENT_INCIDENTS, 7, START, result)))).events().getFirst();
        var family = event.families().stream().filter(value -> value.endpoint().equals("EVENT_INCIDENTS")).findFirst().orElseThrow();

        assertThat(event.score()).isEqualTo("—");
        assertThat(family.table().columns()).containsExactly("Minute", "Type", "Équipe", "Joueur", "Score", "Détail", "Motif");
        assertThat(family.dataSnapshotId()).isEqualTo(7L);
        assertThat(family.parserVersion()).isEqualTo("event-incidents-v17");
        assertThat(family.payloadSha256()).isEqualTo("b".repeat(64));
        if (unavailable) assertThat(family.incidents()).isNull();
        else assertThat(family.incidents()).isEqualTo(IncidentPresentation.from(values, "Domicile", "Extérieur"));
        if ("PRESENT".equals(mode)) {
            assertThat(family.table().rows()).containsExactly(List.of("64", "card", "AWAY", "Joueur source", "1–0", "—", "Main volontaire"));
            assertThat(family.incidents().incidents().getFirst().scoreLabel()).isEqualTo("1–0");
        } else assertThat(family.table().rows()).isEmpty();
        verify(data).findByObservationId(EVENT, SofascoreEndpointType.EVENT_INCIDENTS, 31L);
        verifyNoMoreInteractions(data);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void lineupsUseTheReferencedObservationAndKeepProviderAbsenceDistinct(boolean unavailable) {
        UUID id = UUID.randomUUID();
        var refs = new NormalizedReferences(null, null, 31L, "a".repeat(64));
        var result = new Result(id, new Publication(unavailable ? "UNAVAILABLE" : "PARSED", "EVENT", "OK",
                START, "event-lineups-v2", !unavailable, null, null, null, null,
                unavailable ? "UNAVAILABLE" : "COMPLETE", unavailable ? 0 : 100), refs);
        var cursor = new FamilyCursor(SofascoreEndpointType.EVENT_LINEUPS, id, id, id, id,
                START, START, START, refs, result, result);
        var values = new EventLineups(900001L, false,
                new TeamLineup(LineupSide.HOME, Optional.empty(), List.of()),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()));
        when(data.findByObservationId(EVENT, SofascoreEndpointType.EVENT_LINEUPS, 31L))
                .thenReturn(Optional.of(new J5EventDataObservationView(31, IDENTITY, values,
                        EventSourceTrace.providerSnapshot(7, "b".repeat(64), "event-lineups-v2", START),
                        unavailable ? J5CompletenessReport.unavailable() : J5CompletenessReport.measured(1, 1, List.of()),
                        "a".repeat(64))));

        var projected = presentation.state(campaign(List.of(cursor), List.of(
                attempt(id, SofascoreEndpointType.EVENT_LINEUPS, 7, START, result))))
                .events().getFirst().families().getLast();

        assertThat(projected.dataSnapshotId()).isEqualTo(7L);
        assertThat(projected.payloadSha256()).isEqualTo("b".repeat(64));
        if (unavailable) assertThat(projected.lineups()).isNull();
        else {
            assertThat(projected.lineups().confirmationLabel()).isEqualTo("Provisoire");
            assertThat(projected.lineups().teams()).hasSize(2).allSatisfy(team -> {
                assertThat(team.starterCount()).isZero();
                assertThat(team.substituteCount()).isZero();
            });
        }
        verify(data).findByObservationId(EVENT, SofascoreEndpointType.EVENT_LINEUPS, 31L);
        verifyNoMoreInteractions(data);
    }

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
        assertThat(result.statistics().periods()).singleElement().satisfies(period -> {
            assertThat(period.code()).isEqualTo("ALL");
            assertThat(period.groups().getFirst().metrics().getFirst().home().text()).isEqualTo("3");
        });
        assertThat(result.payloadSha256()).isEqualTo("b".repeat(64));
        verify(data).findByObservationId(EVENT, SofascoreEndpointType.EVENT_STATISTICS, 11L);
        verifyNoMoreInteractions(data);
    }

    @Test
    void aMissingDisplayScoreDoesNotProduceAPartialPair() {
        UUID id = UUID.randomUUID();
        String projection = """
                {"version":"j4-live-score-v1","homeScore":{"presence":"VALUE","value":{
                "display":{"presence":"VALUE","value":0}}},"awayScore":{"presence":"NULL"}}
                """;
        var refs = new NormalizedReferences(1L, 1L, null, "a".repeat(64));
        var result = new Result(id, new Publication("PARSED", "EVENT", "OK", START,
                "event-details-v2", true, "COLLECTING", "inprogress", projection,
                "j4-live-score-v1", null, null), refs);
        var family = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, id, id, id, id,
                START, START, START, refs, result, result);

        var event = presentation.state(campaign(List.of(family),
                List.of(attempt(id, SofascoreEndpointType.EVENT_DETAILS, 1, START, result)))).events().getFirst();

        assertThat(event.score()).isEqualTo("—");
        assertThat(event.sportStatus()).isEqualTo("inprogress");
        assertThat(event.sportStatusLabel()).isEqualTo("inprogress");
    }

    @ParameterizedTest
    @CsvSource({"finished,true,Victoire sur tapis vert", "finished,false,finished",
            "inprogress,true,inprogress"})
    void liveResultUsesDisplayPairAndAnExplicitAwardOnlyForFinished(String status, boolean awarded, String label) {
        UUID id = UUID.randomUUID();
        String projection = """
                {"version":"j4-live-score-v2","isAwarded":{"presence":"VALUE","value":%s},
                 "homeScore":{"presence":"VALUE","value":{"display":{"presence":"VALUE","value":3},
                   "current":{"presence":"VALUE","value":1}}},
                 "awayScore":{"presence":"VALUE","value":{"display":{"presence":"VALUE","value":0},
                   "current":{"presence":"VALUE","value":2}}}}
                """.formatted(awarded);
        var refs = new NormalizedReferences(1L, 1L, null, "a".repeat(64));
        var result = new Result(id, new Publication("PARSED", "EVENT", "OK", START,
                "event-details-v3", true, "COLLECTING", status, projection, "j4-live-score-v2", null, null), refs);
        var cursor = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, id, id, id, id,
                START, START, START, refs, result, result);

        var value = presentation.state(campaign(List.of(cursor), List.of())).events().getFirst();
        assertThat(value.score()).isEqualTo("3 – 0");
        assertThat(value.sportStatusLabel()).isEqualTo(label);
        assertThat(value.sportStatus()).isEqualTo(status);
    }

    @Test
    void preparedCampaignUsesMatchingManualJ4AndLiveUsesItsExactDetailObservation() {
        var store = mock(EventDetailsStore.class);
        var source = EventSourceTrace.providerSnapshot(1, "c".repeat(64), "event-details-v3", START);
        var status = new ScheduledEventStatus("finished", Optional.empty());
        var identity = new CanonicalEventObservationView(1, IDENTITY, START, new ScheduledTeam(1, "Home"),
                new ScheduledTeam(2, "Away"), status, Optional.empty(), source, "d".repeat(64), 1);
        var values = new EventDetails(900001, START, identity.homeTeam(), identity.awayTeam(), status,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(true), Optional.of(3), Optional.of(0));
        var detail = new EventDetailObservationView(8, IDENTITY, values, source, "e".repeat(64));
        when(events.findByObservationId(EVENT, 1)).thenReturn(Optional.of(identity));
        when(store.findLatest(EVENT)).thenReturn(Optional.of(detail));
        var withStore = new LiveCampaignPresentation(events, data, store);
        var prepared = withStore.state(campaign(List.of(), List.of())).events().getFirst();
        assertThat(prepared.score()).isEqualTo("3 – 0");
        assertThat(prepared.sportStatusLabel()).isEqualTo("Victoire sur tapis vert");

        clearInvocations(store);
        when(store.findByObservationId(EVENT, 8)).thenReturn(Optional.of(detail));
        var id = UUID.randomUUID();
        var refs = new NormalizedReferences(1L, 8L, null, "e".repeat(64));
        var result = new Result(id, new Publication("PARSED", "EVENT", "OK", START,
                "event-details-v3", true, "FINALIZING", "finished", null, null, null, null), refs);
        var cursor = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, id, id, id, id,
                START, START, START, refs, result, result);
        var live = withStore.state(campaign(List.of(cursor), List.of())).events().getFirst();
        assertThat(live.score()).isEqualTo("3 – 0");
        verify(store).findByObservationId(EVENT, 8);
        verify(store, never()).findLatest(EVENT);

        when(store.findLatest(EVENT)).thenReturn(Optional.of(new EventDetailObservationView(9, IDENTITY, values,
                EventSourceTrace.providerSnapshot(2, "f".repeat(64), "event-details-v3", START.plusSeconds(60)), "a".repeat(64))));
        var stale = withStore.state(campaign(List.of(), List.of())).events().getFirst();
        assertThat(stale.score()).isEqualTo("—");
        assertThat(stale.sportStatusLabel()).isEqualTo("finished");
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
        assertThat(projected.runtimeStatus().firstFailure()).isNull();
        assertThat(projected.runtimeStatus().cleanupFailure()).isNull();
        assertThat(projected.events().getFirst().families().getFirst().freshness().frozen()).isFalse();
        assertThat(projected.events().getFirst().families().getFirst().freshness().ageAsOf()).isEqualTo(at);
    }

    @Test
    void bothDiagnosticsRetainUnknownHistoricalTransportFieldsWithoutChangingTheDurableLedger() {
        var view = campaignForFreshness(SofascoreEndpointType.EVENT_DETAILS, "COLLECTING", List.of());
        var first = new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.STORAGE_CHECK,
                "LIVE_STORAGE_PROBE_TIMEOUT", START.plusSeconds(45));
        var cleanup = new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_EXCLUSION,
                "RUNTIME_OR_STORAGE_FAILURE", START.plusSeconds(46));
        var result = presentation.state(view, new RuntimeStatus("STOPPED_ERROR", "LOCAL_CLEANUP_PENDING",
                true, true, false, first, cleanup));

        assertThat(result.state()).isEqualTo("RUNNING");
        assertThat(result.revision()).isEqualTo(view.revision());
        assertThat(result.runtimeStatus().firstFailure()).isSameAs(first);
        assertThat(result.runtimeStatus().cleanupFailure()).isSameAs(cleanup);
        var json = JsonMapper.builder().build().valueToTree(result);
        var runtime = json.path("runtimeStatus");
        assertThat(runtime.path("firstFailure").path("phase").asString()).isEqualTo("STORAGE_CHECK");
        assertThat(runtime.path("firstFailure").path("code").asString()).isEqualTo("LIVE_STORAGE_PROBE_TIMEOUT");
        assertThat(runtime.path("firstFailure").path("occurredAt").asString()).isEqualTo(first.occurredAt().toString());
        assertThat(runtime.path("cleanupFailure").path("phase").asString()).isEqualTo("CLEANUP_EXCLUSION");
        assertThat(runtime.path("cleanupFailure").path("occurredAt").asString()).isEqualTo(cleanup.occurredAt().toString());
        for (String kind : List.of("firstFailure", "cleanupFailure")) {
            assertThat(runtime.path(kind).size()).isEqualTo(6);
            for (String field : List.of("attemptId", "endpoint", "transport"))
                assertThat(runtime.path(kind).path(field).isNull()).as("%s.%s remains unknown", kind, field).isTrue();
        }
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

    @ParameterizedTest
    @CsvSource({"live-v4,60,1000,3000,3.2", "live-v5,100,2500,20000,2.0"})
    void groupedReceptionAgeAndDeadlineRemainDistinctFromAnUnchangedValueAndLineupCadence(String policy,
                int interval, int eventCalls, int campaignCalls, double callsPerMinute) {
        var base = campaignForFreshness(SofascoreEndpointType.EVENT_DETAILS, "COLLECTING", List.of());
        var envelopes = new java.util.EnumMap<SofascoreEndpointType, EndpointEnvelope>(SofascoreEndpointType.class);
        for (var endpoint : List.of(SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS))
            envelopes.put(endpoint, new EndpointEnvelope(Duration.ofMillis(400), Duration.ofMillis(100)));
        var manifest = new Manifest(CAMPAIGN, "a".repeat(64), policy, START, START.plusSeconds(300),
                Duration.ofHours(4), eventCalls, campaignCalls, 1_000_000, 10, base.manifest().targets(),
                new AdmissionProfile(Duration.ofSeconds(10), Duration.ofSeconds(1), "",
                        new GroupedAdmissionProfile(envelopes, "b".repeat(64), policy)), Duration.ofSeconds(interval));
        var original = base.events().getFirst().families().getFirst();
        var j4 = new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS, original.lastAttemptId(), original.lastReceivedAttemptId(),
                original.lastSuccessfulAttemptId(), original.lastChangedAttemptId(), START.plusSeconds(120), START.plusSeconds(120),
                START, original.normalized(), original.latestResult(), original.latestSuccessfulResult(),
                new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS, START.plusSeconds(180), interval, 0));
        var lineups = new FamilyCursor(SofascoreEndpointType.EVENT_LINEUPS, original.lastAttemptId(), original.lastReceivedAttemptId(),
                original.lastSuccessfulAttemptId(), original.lastChangedAttemptId(), START, START, START,
                original.normalized(), original.latestResult(), original.latestSuccessfulResult(),
                new FamilySchedule(SofascoreEndpointType.EVENT_LINEUPS, START.plusSeconds(300), 300, 0));
        var view = new CampaignView(manifest, "RUNNING", null, START, START.plusSeconds(14400), 200, 0, 10, null,
                List.of(new EventView(manifest.targets().getFirst(), "COLLECTING", null, 200, 0,
                        START.plusSeconds(180), List.of(j4, lineups))), base.attempts(), List.of());
        var projected = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(185), ZoneOffset.UTC)).state(view);
        var family = projected.events().getFirst().families().getFirst();
        assertThat(family.freshness().state()).isEqualTo("FRESH");
        assertThat(family.lastChangedAt()).isEqualTo(START);
        assertThat(family.freshness().receivedAgeSeconds()).isEqualTo(65);
        assertThat(family.schedule().latenessMillis()).isEqualTo(5000);
        var lineup = projected.events().getFirst().families().getLast();
        assertThat(lineup.freshness().expectedIntervalSeconds()).isEqualTo(300);
        assertThat(lineup.freshness().state()).isEqualTo("FRESH");
        assertThat(lineup.schedule().latenessMillis()).isZero();
        assertThat(projected.cadence().qualifiedCapacity()).isEqualTo(10);
        assertThat(projected.cadence().policyVersion()).isEqualTo(policy);
        assertThat(projected.cadence().targetSeconds()).isEqualTo(interval);
        assertThat(projected.cadence().estimatedCallsPerMinute()).isEqualTo(callsPerMinute);
        assertThat(projected.cadence().estimatedRemainingSeconds()).isEqualTo(14215); // authorized window is tighter than call budgets

        // Cleanup failure can leave RUNNING in the ledger after the process has stopped collecting.
        for (boolean closing : List.of(false, true)) {
            var stopped = new LiveCampaignPresentation(events, data, Clock.fixed(START.plusSeconds(185), ZoneOffset.UTC))
                    .state(view, new com.bettingproject.sofascorelocal.application.live.LiveCampaignService.RuntimeStatus(
                            "STOPPED_ERROR", "LOCAL_CLEANUP_PENDING", true, true, closing));
            assertThat(stopped.state()).isEqualTo("RUNNING");
            assertThat(stopped.cadence().estimatedRemainingSeconds()).isZero();
            assertThat(stopped.cadence().estimatedCallsPerMinute()).isZero();
            assertThat(stopped.cadence().targetSeconds()).isEqualTo(interval);
        }
    }

    @ParameterizedTest @CsvSource({"INTERRUPTED,COLLECTING","RUNNING,STOPPED_POSTPONED","COMPLETED,FINISHED_CONFIRMED"})
    void terminalCollectionNeverAdvertisesAnOldFamilyDeadline(String campaignState,String eventState) {
        var base=campaign(List.of(),List.of());
        var envelopes=new java.util.EnumMap<SofascoreEndpointType,EndpointEnvelope>(SofascoreEndpointType.class);
        for(var endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_LINEUPS))
            envelopes.put(endpoint,new EndpointEnvelope(Duration.ofMillis(500),Duration.ofMillis(100)));
        var manifest=new Manifest(CAMPAIGN,"a".repeat(64),"live-v4",START,START.plusSeconds(300),
                Duration.ofHours(4),1000,3000,1_000_000,10,base.manifest().targets(),
                new AdmissionProfile(Duration.ofSeconds(10),Duration.ofSeconds(1),"",
                        new GroupedAdmissionProfile(envelopes,"b".repeat(64))),Duration.ofSeconds(60));
        var cursor=new FamilyCursor(SofascoreEndpointType.EVENT_DETAILS,null,null,null,null,null,null,null,
                NormalizedReferences.none(),null,null,new FamilySchedule(SofascoreEndpointType.EVENT_DETAILS,START.plusSeconds(60),60,2));
        var view=new CampaignView(manifest,campaignState,null,START,START.plusSeconds(14400),0,0,10,null,
                List.of(new EventView(manifest.targets().getFirst(),eventState,null,0,0,null,List.of(cursor))),List.of(),List.of());
        var family=new LiveCampaignPresentation(events,data,Clock.fixed(START.plusSeconds(180),ZoneOffset.UTC))
                .state(view).events().getFirst().families().getFirst();
        assertThat(family.schedule().nextDueAt()).isNull();
        assertThat(family.schedule().latenessMillis()).isZero();
        assertThat(family.schedule().missedCycles()).isEqualTo(2);
        assertThat(family.freshness().state()).isEqualTo("FROZEN");
        assertThat(cursor.schedule().nextDueAt()).isEqualTo(START.plusSeconds(60));
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
