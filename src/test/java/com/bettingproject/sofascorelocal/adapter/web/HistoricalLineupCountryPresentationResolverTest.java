package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.MissingLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.Sha256;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalLineupCountryPresentationResolverTest {

    private static final long SNAPSHOT_ID = 41L;
    private static final long EVENT_ID = 900001L;
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-09T18:00:00Z");

    @Test
    void readsOnlyTheVerifiedV3SnapshotAndLeavesTheObservationUntouched() {
        byte[] payload = payload("""
                {"confirmed":true,
                 "home":{"players":[{"player":{"id":101,"name":"Home player","country":{"name":"France","alpha2":"fr"}},"substitute":false}]},
                 "away":{"players":[],"missingPlayers":[{"player":{"id":202,"name":"Away unavailable","country":{"name":"Brazil","alpha2":"br"}}}]}}
                """);
        var observation = observation(payload, "event-lineups-v3", Optional.empty(), Optional.empty());
        var store = new FakeStore(snapshot(payload, "EVENT_LINEUPS", requestKey(), "event-lineups-v3"));

        LineupCountryOverlay overlay = new HistoricalLineupCountryPresentationResolver(store).resolve(observation);

        assertThat(overlay.rosterCountry(LineupSide.HOME, 101L))
                .contains(new ProviderCountry(Optional.of("France"), Optional.of("FR")));
        assertThat(overlay.missingPlayerCountry(LineupSide.AWAY, 202L))
                .contains(new ProviderCountry(Optional.of("Brazil"), Optional.of("BR")));
        assertThat(observation.data()).isInstanceOf(EventLineups.class);
        EventLineups original = (EventLineups) observation.data();
        assertThat(original.home().players().getFirst().country()).isEmpty();
        assertThat(original.away().missingPlayers().orElseThrow().getFirst().country()).isEmpty();
        assertThat(observation.source().parserVersion()).isEqualTo("event-lineups-v3");
        assertThat(observation.source().payloadSha256()).isEqualTo(Sha256.hex(payload));
        assertThat(observation.normalizedSha256()).isEqualTo("a".repeat(64));
        assertThat(store.requestedSnapshotId).isEqualTo(SNAPSHOT_ID);
        assertThat(store.findRecentCalled).isFalse();
    }

    @Test
    void rejectsASnapshotWhoseEndpointRequestIdentityOrRawPlayerIdentityDoesNotMatch() {
        byte[] valid = payload("""
                {"confirmed":true,
                 "home":{"players":[{"player":{"id":101,"name":"Home player","country":{"name":"France","alpha2":"fr"}},"substitute":false}]},
                 "away":{"players":[],"missingPlayers":[{"player":{"id":202,"name":"Away unavailable"}}]}}
                """);
        var observation = observation(valid, "event-lineups-v3", Optional.empty(), Optional.empty());

        var wrongEndpoint = new HistoricalLineupCountryPresentationResolver(new FakeStore(
                snapshot(valid, "EVENT_STATISTICS", requestKey(), "event-lineups-v3"))).resolve(observation);
        var wrongRequest = new HistoricalLineupCountryPresentationResolver(new FakeStore(
                snapshot(valid, "EVENT_LINEUPS", "EVENT_LINEUPS|eventId=900002", "event-lineups-v3"))).resolve(observation);

        byte[] unknownPlayer = payload("""
                {"confirmed":true,
                 "home":{"players":[{"player":{"id":999,"name":"Other player","country":{"name":"France","alpha2":"fr"}},"substitute":false}]},
                 "away":{"players":[],"missingPlayers":[{"player":{"id":202,"name":"Away unavailable"}}]}}
                """);
        var wrongIdObservation = observation(unknownPlayer, "event-lineups-v3", Optional.empty(), Optional.empty());
        var wrongPlayer = new HistoricalLineupCountryPresentationResolver(new FakeStore(
                snapshot(unknownPlayer, "EVENT_LINEUPS", requestKey(), "event-lineups-v3"))).resolve(wrongIdObservation);

        assertThat(wrongEndpoint.rosterCountry(LineupSide.HOME, 101L)).isEmpty();
        assertThat(wrongRequest.rosterCountry(LineupSide.HOME, 101L)).isEmpty();
        assertThat(wrongPlayer.rosterCountry(LineupSide.HOME, 101L)).isEmpty();
    }

    @Test
    void failsClosedForMismatchedHashesAndAmbiguousJson() {
        byte[] valid = payload("""
                {"confirmed":true,
                 "home":{"players":[{"player":{"id":101,"name":"Home player","country":{"name":"France","alpha2":"fr"}},"substitute":false}]},
                 "away":{"players":[],"missingPlayers":[{"player":{"id":202,"name":"Away unavailable"}}]}}
                """);
        var observation = observation(valid, "event-lineups-v3", Optional.empty(), Optional.empty());
        var wrongHashSummary = new RawSnapshotInspectionSummary(
                SNAPSHOT_ID, RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT, "EVENT_LINEUPS", requestKey(),
                RECEIVED_AT, 200, "application/json", valid.length, "b".repeat(64),
                "event-lineups-v3", RawSnapshotSchemaStatus.PARSED);
        var wrongHash = new HistoricalLineupCountryPresentationResolver(new FakeStore(
                new RawSnapshotInspectionSource(wrongHashSummary, valid))).resolve(observation);

        byte[] duplicate = payload("""
                {"confirmed":true,"confirmed":true,
                 "home":{"players":[{"player":{"id":101,"name":"Home player","country":{"name":"France","alpha2":"fr"}},"substitute":false}]},
                 "away":{"players":[],"missingPlayers":[{"player":{"id":202,"name":"Away unavailable"}}]}}
                """);
        var duplicateObservation = observation(duplicate, "event-lineups-v3", Optional.empty(), Optional.empty());
        var ambiguous = new HistoricalLineupCountryPresentationResolver(new FakeStore(
                snapshot(duplicate, "EVENT_LINEUPS", requestKey(), "event-lineups-v3"))).resolve(duplicateObservation);

        assertThat(wrongHash.rosterCountry(LineupSide.HOME, 101L)).isEmpty();
        assertThat(ambiguous.rosterCountry(LineupSide.HOME, 101L)).isEmpty();
    }

    @Test
    void skipsV4AndNeverReplacesANormalizedCountry() {
        byte[] payload = payload("""
                {"confirmed":true,
                 "home":{"players":[{"player":{"id":101,"name":"Home player","country":{"name":"France","alpha2":"fr"}},"substitute":false}]},
                 "away":{"players":[],"missingPlayers":[{"player":{"id":202,"name":"Away unavailable"}}]}}
                """);
        var normalizedFrance = Optional.of(new ProviderCountry(Optional.of("France"), Optional.of("FR")));
        var observation = observation(payload, "event-lineups-v4", normalizedFrance, Optional.empty());
        var store = new FakeStore(snapshot(payload, "EVENT_LINEUPS", requestKey(), "event-lineups-v4"));

        LineupCountryOverlay overlay = new HistoricalLineupCountryPresentationResolver(store).resolve(observation);

        assertThat(overlay.rosterCountry(LineupSide.HOME, 101L)).isEmpty();
        assertThat(((EventLineups) observation.data()).home().players().getFirst().country()).isEqualTo(normalizedFrance);
        assertThat(store.requestedSnapshotId).isZero();
    }

    private static J5EventDataObservationView observation(
            byte[] payload,
            String parserVersion,
            Optional<ProviderCountry> homeCountry,
            Optional<ProviderCountry> missingCountry) {
        var values = new EventLineups(
                EVENT_ID,
                true,
                new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(new EventLineupPlayer(
                        101L, "Home player", Optional.empty(), Optional.empty(), true,
                        Optional.empty(), Optional.empty(), homeCountry))),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of(), Optional.of(List.of(new MissingLineupPlayer(
                        202L, "Away unavailable", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of("Knee Injury"), Optional.empty(), Optional.<OffsetDateTime>empty(), missingCountry)))));
        return new J5EventDataObservationView(
                11L,
                CanonicalEventIdentity.sofascore(EVENT_ID),
                values,
                EventSourceTrace.providerSnapshot(SNAPSHOT_ID, Sha256.hex(payload), parserVersion, RECEIVED_AT),
                J5CompletenessReport.measured(2, 2, List.of()),
                "a".repeat(64));
    }

    private static RawSnapshotInspectionSource snapshot(
            byte[] payload,
            String endpoint,
            String requestKey,
            String parserVersion) {
        return new RawSnapshotInspectionSource(
                new RawSnapshotInspectionSummary(
                        SNAPSHOT_ID,
                        RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT,
                        endpoint,
                        requestKey,
                        RECEIVED_AT,
                        200,
                        "application/json",
                        payload.length,
                        Sha256.hex(payload),
                        parserVersion,
                        RawSnapshotSchemaStatus.PARSED),
                payload);
    }

    private static String requestKey() {
        return "EVENT_LINEUPS|eventId=" + EVENT_ID;
    }

    private static byte[] payload(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeStore implements RawSnapshotInspectionStore {
        private final Optional<RawSnapshotInspectionSource> source;
        private long requestedSnapshotId;
        private boolean findRecentCalled;

        private FakeStore(RawSnapshotInspectionSource source) {
            this.source = Optional.of(source);
        }

        @Override
        public List<RawSnapshotInspectionSummary> findRecent(int limit) {
            findRecentCalled = true;
            return List.of();
        }

        @Override
        public Optional<RawSnapshotInspectionSource> findById(long snapshotId) {
            requestedSnapshotId = snapshotId;
            return source;
        }
    }
}
