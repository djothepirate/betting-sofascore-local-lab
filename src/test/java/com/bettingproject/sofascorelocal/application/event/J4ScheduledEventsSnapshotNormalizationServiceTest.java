package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventObservationPersistenceResult;
import com.bettingproject.sofascorelocal.domain.event.J4ScheduledEventsSnapshotSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.LoadedFixture;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J4ScheduledEventsSnapshotSourceStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class J4ScheduledEventsSnapshotNormalizationServiceTest {

    private final J4ScheduledEventsSnapshotSourceStore sourceStore =
            mock(J4ScheduledEventsSnapshotSourceStore.class);
    private final CanonicalEventStore canonicalEventStore = mock(CanonicalEventStore.class);
    private final J4ScheduledEventsSnapshotNormalizationService service =
            new J4ScheduledEventsSnapshotNormalizationService(
                    sourceStore,
                    canonicalEventStore,
                    new ScheduledEventsV1Parser());
    private final ClasspathFixtureLoader fixtureLoader = new ClasspathFixtureLoader();

    @Test
    void reparsesAHistoricalSnapshotAndPersistsCompleteSnapshotProvenance() {
        LoadedFixture fixture = fixtureLoader.load(
                "fixtures/scheduled-events/nominal.manifest.json");
        var source = source(41L, fixture, RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
        var identity = CanonicalEventIdentity.sofascore(900001L);
        when(sourceStore.findById(41L)).thenReturn(Optional.of(source));
        when(canonicalEventStore.save(any())).thenReturn(
                new EventObservationPersistenceResult(
                        1L,
                        identity.value(),
                        1L,
                        true));

        var result = service.normalize(41L);

        assertThat(result.historicalSchemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.currentParseStatus()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.payloadShape()).isEqualTo("EVENT_LIST");
        assertThat(result.parsedEventCount()).isEqualTo(1);
        assertThat(result.scheduledTournamentCount()).isZero();
        assertThat(result.insertedObservationCount()).isEqualTo(1);
        assertThat(result.canonicalEventIds()).containsExactly(identity.value());
        verify(canonicalEventStore).save(org.mockito.ArgumentMatchers.argThat(observation ->
                observation.source().snapshotId().orElseThrow() == 41L
                        && observation.source().payloadSha256().equals(fixture.rawSha256())
                        && observation.source().parserVersion().equals("scheduled-events-v1")));
    }

    @Test
    void treatsAQualifiedScheduledTournamentPageAsCompatibleWithoutInventingEvents() {
        LoadedFixture fixture = fixtureLoader.load(
                "fixtures/scheduled-events/qualified-provider-shape.manifest.json");
        when(sourceStore.findById(42L)).thenReturn(Optional.of(source(
                42L,
                fixture,
                RawSnapshotSchemaStatus.PARSED)));

        var result = service.normalize(42L);

        assertThat(result.currentParseStatus()).isEqualTo(ScheduledEventsParseStatus.PARSED);
        assertThat(result.payloadShape()).isEqualTo("SCHEDULED_TOURNAMENT_LIST");
        assertThat(result.parsedEventCount()).isZero();
        assertThat(result.scheduledTournamentCount()).isEqualTo(2);
        assertThat(result.insertedObservationCount()).isZero();
        assertThat(result.deduplicatedObservationCount()).isZero();
        assertThat(result.canonicalEventIds()).isEmpty();
        verify(canonicalEventStore, never()).save(any());
    }

    @Test
    void blocksAHashMismatchBeforeParsingOrPersistence() {
        LoadedFixture fixture = fixtureLoader.load(
                "fixtures/scheduled-events/nominal.manifest.json");
        J4ScheduledEventsSnapshotSource inconsistent = new J4ScheduledEventsSnapshotSource(
                43L,
                "SCHEDULED_EVENTS|date=2026-08-12|page=1",
                Instant.parse("2026-08-12T13:59:59Z"),
                Instant.parse("2026-08-12T14:00:00Z"),
                200,
                "application/json",
                Duration.ofSeconds(1),
                fixture.rawPayload(),
                "f".repeat(64),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED);
        when(sourceStore.findById(43L)).thenReturn(Optional.of(inconsistent));

        assertThatThrownBy(() -> service.normalize(43L))
                .isInstanceOf(J4SnapshotNormalizationException.class)
                .satisfies(exception -> assertThat(
                        ((J4SnapshotNormalizationException) exception).error())
                        .isEqualTo(J4SnapshotNormalizationError.PAYLOAD_INTEGRITY_FAILURE));
        verify(canonicalEventStore, never()).save(any());
    }

    private static J4ScheduledEventsSnapshotSource source(
            long snapshotId,
            LoadedFixture fixture,
            RawSnapshotSchemaStatus historicalStatus) {
        return new J4ScheduledEventsSnapshotSource(
                snapshotId,
                "SCHEDULED_EVENTS|date=2026-08-12|page=1",
                Instant.parse("2026-08-12T13:59:59Z"),
                Instant.parse("2026-08-12T14:00:00Z"),
                200,
                "application/json",
                Duration.ofSeconds(1),
                fixture.rawPayload(),
                fixture.rawSha256(),
                "scheduled-events-v1",
                historicalStatus);
    }
}
