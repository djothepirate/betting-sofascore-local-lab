package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3PageResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogStatus;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J3TournamentCatalogServiceTest {

    private static final LocalDate DATE = LocalDate.parse("2026-08-20");
    private static final Instant BASE_TIME = Instant.parse("2026-08-20T10:00:00Z");

    private J3ManualCollectionEvidenceService evidenceService;
    private InMemorySnapshotStore snapshotStore;
    private J3TournamentCatalogService service;

    @BeforeEach
    void setUp() {
        evidenceService = new J3ManualCollectionEvidenceService();
        snapshotStore = new InMemorySnapshotStore();
        service = new J3TournamentCatalogService(evidenceService, snapshotStore);
    }

    @Test
    void reportsThatNoTerminalCollectionEvidenceIsAvailable() {
        var catalog = service.latest();

        assertThat(catalog.status())
                .isEqualTo(J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE);
        assertThat(catalog.available()).isFalse();
        assertThat(catalog.collectionDate()).isEmpty();
        assertThat(catalog.options()).isEmpty();
        assertThat(snapshotStore.requestedIds).isEmpty();
    }

    @Test
    void ignoresACollectionThatDidNotComplete() {
        evidenceService.publish(new J3MinimizedCollectionEvidence(
                DATE,
                J3ManualCallIntentState.FAILED,
                0,
                0,
                1,
                "HTTP_FORBIDDEN",
                BASE_TIME,
                true,
                J3CircuitState.LOCKED,
                J3CircuitReason.HTTP_FORBIDDEN,
                Duration.ofMinutes(10),
                List.of()));

        var catalog = service.latest();

        assertThat(catalog.status())
                .isEqualTo(J3TournamentCatalogStatus.COLLECTION_NOT_COMPLETED);
        assertThat(catalog.collectionDate()).contains(DATE);
        assertThat(snapshotStore.requestedIds).isEmpty();
    }

    @Test
    void rebuildsTheExactCompletedPagesDeduplicatesAndExcludesNonActionableEntries() {
        PageFixture first = page(101L, 1, true, """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 119880,
                        "name": "UEFA Champions League, Playoff Round",
                        "category": {"id": 1465, "name": "Europe"},
                        "uniqueTournament": {
                          "id": 7,
                          "name": "UEFA Champions League"
                        }
                      },
                      "timezoneEventCount": {"7200": 4}
                    },
                    {
                      "tournament": {
                        "id": 220001,
                        "name": "Non-actionable phase",
                        "category": {"id": 1465, "name": "Europe"}
                      },
                      "timezoneEventCount": []
                    }
                  ],
                  "hasNextPage": true
                }
                """);
        PageFixture second = page(102L, 2, false, """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 119880,
                        "name": "UEFA Champions League, Playoff Round",
                        "category": {"id": 1465, "name": "Europe"},
                        "uniqueTournament": {
                          "id": 7,
                          "name": "UEFA Champions League"
                        }
                      },
                      "timezoneEventCount": {"7200": 4}
                    },
                    {
                      "tournament": {
                        "id": 119881,
                        "name": "UEFA Champions League, League Phase",
                        "category": {"id": 1465, "name": "Europe"},
                        "uniqueTournament": {
                          "id": 7,
                          "name": "UEFA Champions League"
                        }
                      },
                      "timezoneEventCount": {"7200": 2}
                    }
                  ],
                  "hasNextPage": false
                }
                """);
        snapshotStore.put(source(first));
        snapshotStore.put(source(second));
        publishCompleted(first, second);

        var catalog = service.latest();

        assertThat(catalog.status()).isEqualTo(J3TournamentCatalogStatus.AVAILABLE);
        assertThat(catalog.available()).isTrue();
        assertThat(catalog.collectionDate()).contains(DATE);
        assertThat(catalog.pageSnapshotIds()).containsExactly(101L, 102L);
        assertThat(catalog.excludedNonActionableCount()).isEqualTo(1);
        assertThat(catalog.options()).extracting(option -> option.tournamentId())
                .containsExactly(119880L, 119881L);
        assertThat(catalog.options().getFirst().tournamentName())
                .isEqualTo("UEFA Champions League, Playoff Round");
        assertThat(catalog.options().getFirst().tournamentCategoryName())
                .isEqualTo("Europe");
        assertThat(catalog.options().getFirst().displayLabel())
                .isEqualTo("UEFA Champions League, Playoff Round - Europe");
        assertThat(catalog.options().getFirst().uniqueTournamentId()).isEqualTo(7L);
        assertThat(catalog.options().getFirst().uniqueTournamentName())
                .isEqualTo("UEFA Champions League");
        assertThat(catalog.options().getFirst().timezoneEventCount())
                .containsExactly(Map.entry(7200, 4));
        assertThat(catalog.options().getFirst().sourceSnapshotIds())
                .containsExactly(101L, 102L);
        assertThat(catalog.options().get(1).sourceSnapshotIds()).containsExactly(102L);
        assertThat(snapshotStore.requestedIds).containsExactly(101L, 102L);

        assertThat(service.resolve(119880L)).isPresent().get()
                .extracting(option -> option.uniqueTournamentId())
                .isEqualTo(7L);
        assertThat(service.resolve(220001L)).isEmpty();
        assertThat(service.resolve(999999L)).isEmpty();
        assertThatThrownBy(() -> service.resolve(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filtersTheSummerCatalogByTheParisOffsetKeyAndRevalidatesServerSide() {
        PageFixture only = page(151L, 1, false, """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 119880,
                        "name": "UEFA Champions League, Playoff Round",
                        "category": {"name": "Europe"},
                        "uniqueTournament": {
                          "id": 7,
                          "name": "UEFA Champions League"
                        }
                      },
                      "timezoneEventCount": {"18000": 4, "36000": 4}
                    },
                    {
                      "tournament": {
                        "id": 36,
                        "name": "LaLiga",
                        "category": {"name": "Spain"},
                        "uniqueTournament": {
                          "id": 8,
                          "name": "LaLiga"
                        }
                      },
                      "timezoneEventCount": {"0": 1, "3600": 1, "7200": 1}
                    },
                    {
                      "tournament": {
                        "id": 37,
                        "name": "Empty timezone phase",
                        "category": {"name": "Europe"},
                        "uniqueTournament": {
                          "id": 9,
                          "name": "Empty timezone tournament"
                        }
                      },
                      "timezoneEventCount": []
                    }
                  ],
                  "hasNextPage": false
                }
                """);
        snapshotStore.put(source(only));
        publishCompleted(only);

        var catalog = service.latest();

        assertThat(catalog.status()).isEqualTo(J3TournamentCatalogStatus.AVAILABLE);
        assertThat(catalog.options()).extracting(option -> option.tournamentId())
                .containsExactly(36L);
        assertThat(catalog.options().getFirst().displayLabel())
                .isEqualTo("LaLiga - Spain");
        assertThat(catalog.excludedNonActionableCount()).isEqualTo(2);
        assertThat(service.resolve(36L)).isPresent();
        assertThat(service.resolve(119880L)).isEmpty();
        assertThat(service.resolve(37L)).isEmpty();
    }

    @Test
    void excludesAnOccurrenceWithoutGeographicCategoryAndRevalidatesItsPostedId() {
        PageFixture only = page(154L, 1, false, """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 36,
                        "name": "LaLiga",
                        "category": {"id": 32, "name": "Spain"},
                        "uniqueTournament": {
                          "id": 8,
                          "name": "LaLiga"
                        }
                      },
                      "timezoneEventCount": {"7200": 1}
                    },
                    {
                      "tournament": {
                        "id": 38,
                        "name": "Geography missing phase",
                        "uniqueTournament": {
                          "id": 10,
                          "name": "Geography missing tournament"
                        }
                      },
                      "timezoneEventCount": {"7200": 1}
                    }
                  ],
                  "hasNextPage": false
                }
                """);
        snapshotStore.put(source(only));
        publishCompleted(only);

        var catalog = service.latest();

        assertThat(catalog.options()).singleElement().satisfies(option -> {
            assertThat(option.tournamentId()).isEqualTo(36L);
            assertThat(option.tournamentCategoryName()).isEqualTo("Spain");
            assertThat(option.displayLabel()).isEqualTo("LaLiga - Spain");
        });
        assertThat(catalog.excludedNonActionableCount()).isEqualTo(1);
        assertThat(service.resolve(38L)).isEmpty();
    }

    @Test
    void derivesTheWinterParisOffsetFromTheCollectionDateAndUsesKeyPresence() {
        LocalDate winterDate = LocalDate.parse("2026-01-20");
        PageFixture only = page(152L, 1, false, """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 510001,
                        "name": "Winter local phase",
                        "category": {"name": "France"},
                        "uniqueTournament": {
                          "id": 510,
                          "name": "Winter tournament"
                        }
                      },
                      "timezoneEventCount": {"3600": 0}
                    },
                    {
                      "tournament": {
                        "id": 510002,
                        "name": "Summer-only phase",
                        "category": {"name": "France"},
                        "uniqueTournament": {
                          "id": 511,
                          "name": "Summer-only tournament"
                        }
                      },
                      "timezoneEventCount": {"7200": 2}
                    }
                  ],
                  "hasNextPage": false
                }
                """);
        snapshotStore.put(source(winterDate, only));
        publishCompleted(winterDate, only);

        var catalog = service.latest();

        assertThat(catalog.collectionDate()).contains(winterDate);
        assertThat(catalog.options()).extracting(option -> option.tournamentId())
                .containsExactly(510001L);
        assertThat(catalog.options().getFirst().timezoneEventCount())
                .containsExactly(Map.entry(3600, 0));
        assertThat(catalog.excludedNonActionableCount()).isEqualTo(1);
    }

    @Test
    void acceptsEitherParisOffsetOnADstTransitionDay() {
        LocalDate transitionDate = LocalDate.parse("2026-03-29");
        PageFixture only = page(153L, 1, false, """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": 520001,
                        "name": "Before transition phase",
                        "category": {"name": "France"},
                        "uniqueTournament": {
                          "id": 520,
                          "name": "Before transition tournament"
                        }
                      },
                      "timezoneEventCount": {"3600": 1}
                    },
                    {
                      "tournament": {
                        "id": 520002,
                        "name": "After transition phase",
                        "category": {"name": "France"},
                        "uniqueTournament": {
                          "id": 521,
                          "name": "After transition tournament"
                        }
                      },
                      "timezoneEventCount": {"7200": 1}
                    },
                    {
                      "tournament": {
                        "id": 520003,
                        "name": "Unrelated timezone phase",
                        "category": {"name": "France"},
                        "uniqueTournament": {
                          "id": 522,
                          "name": "Unrelated timezone tournament"
                        }
                      },
                      "timezoneEventCount": {"18000": 1}
                    }
                  ],
                  "hasNextPage": false
                }
                """);
        snapshotStore.put(source(transitionDate, only));
        publishCompleted(transitionDate, only);

        var catalog = service.latest();

        assertThat(catalog.options()).extracting(option -> option.tournamentId())
                .containsExactly(520001L, 520002L);
        assertThat(catalog.excludedNonActionableCount()).isEqualTo(1);
        assertThat(service.resolve(520003L)).isEmpty();
    }

    @Test
    void refusesConflictingOccurrencesOfTheSameTournamentIdentity() {
        PageFixture first = page(201L, 1, true, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                true));
        PageFixture second = page(202L, 2, false, payload(
                119880L,
                "Champions League phase",
                8L,
                "Other unique tournament",
                false));
        snapshotStore.put(source(first));
        snapshotStore.put(source(second));
        publishCompleted(first, second);

        var catalog = service.latest();

        assertThat(catalog.status())
                .isEqualTo(J3TournamentCatalogStatus.TOURNAMENT_CONFLICT);
        assertThat(catalog.options()).isEmpty();
        assertThat(service.resolve(119880L)).isEmpty();
    }

    @Test
    void refusesConflictingGeographicCategoriesForTheSameTournamentIdentity() {
        PageFixture first = page(203L, 1, true, payloadWithCategory(
                119880L,
                "Champions League phase",
                "Europe",
                7L,
                "UEFA Champions League",
                true));
        PageFixture second = page(204L, 2, false, payloadWithCategory(
                119880L,
                "Champions League phase",
                "South America",
                7L,
                "UEFA Champions League",
                false));
        snapshotStore.put(source(first));
        snapshotStore.put(source(second));
        publishCompleted(first, second);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.TOURNAMENT_CONFLICT);
        assertThat(service.resolve(119880L)).isEmpty();
    }

    @Test
    void refusesWhenAnExactEvidenceSnapshotCannotBeReloaded() {
        PageFixture only = page(301L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        publishCompleted(only);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_NOT_FOUND);
        assertThat(snapshotStore.requestedIds).containsExactly(301L);
    }

    @Test
    void refusesSnapshotMetadataThatDoesNotMatchTheEvidencePageKey() {
        PageFixture only = page(401L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        RawSnapshotInspectionSource valid = source(only);
        RawSnapshotInspectionSummary summary = valid.summary();
        snapshotStore.put(new RawSnapshotInspectionSource(
                new RawSnapshotInspectionSummary(
                        summary.snapshotId(),
                        summary.logicalEndpoint(),
                        "SCHEDULED_EVENTS|date=2026-08-19|page=1",
                        summary.receivedAt(),
                        summary.httpStatus(),
                        summary.contentType(),
                        summary.payloadSizeBytes(),
                        summary.payloadSha256(),
                        summary.parserVersion(),
                        summary.schemaStatus()),
                valid.payloadRaw()));
        publishCompleted(only);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_METADATA_MISMATCH);
    }

    @Test
    void refusesAnImportedSnapshotAsEvidenceForTheJ3ProviderCollection() {
        PageFixture only = page(402L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        RawSnapshotInspectionSource valid = source(only);
        RawSnapshotInspectionSummary summary = valid.summary();
        snapshotStore.put(new RawSnapshotInspectionSource(
                new RawSnapshotInspectionSummary(
                        summary.snapshotId(),
                        RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                        summary.logicalEndpoint(),
                        summary.requestKey(),
                        summary.receivedAt(),
                        summary.httpStatus(),
                        summary.contentType(),
                        summary.payloadSizeBytes(),
                        summary.payloadSha256(),
                        summary.parserVersion(),
                        summary.schemaStatus()),
                valid.payloadRaw()));
        publishCompleted(only);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_METADATA_MISMATCH);
    }

    @Test
    void acceptsAnImportedSnapshotWhenTheCompletedJ3EvidenceMarksLocalImport() {
        PageFixture only = page(403L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        RawSnapshotInspectionSource valid = source(only);
        RawSnapshotInspectionSummary summary = valid.summary();
        snapshotStore.put(new RawSnapshotInspectionSource(
                new RawSnapshotInspectionSummary(
                        summary.snapshotId(),
                        RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                        summary.logicalEndpoint(),
                        summary.requestKey(),
                        summary.receivedAt(),
                        summary.httpStatus(),
                        summary.contentType(),
                        summary.payloadSizeBytes(),
                        summary.payloadSha256(),
                        summary.parserVersion(),
                        summary.schemaStatus()),
                valid.payloadRaw()));
        publishCompletedEvidence(importedEvidence(only));

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.AVAILABLE);
        assertThat(service.latest().options())
                .singleElement()
                .satisfies(option -> assertThat(option.tournamentId()).isEqualTo(119880L));
    }

    @Test
    void acceptsReceivedAtRoundedToPostgresMicrosecondResolution() {
        PageFixture only = page(451L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        Instant inMemoryReceivedAt = Instant.parse("2026-08-20T10:00:01.123456700Z");
        Instant persistedReceivedAt = Instant.parse("2026-08-20T10:00:01.123457Z");
        snapshotStore.put(source(only, persistedReceivedAt));
        publishCompletedEvidence(evidence(only, inMemoryReceivedAt));

        var catalog = service.latest();

        assertThat(catalog.status()).isEqualTo(J3TournamentCatalogStatus.AVAILABLE);
        assertThat(catalog.options()).extracting(option -> option.tournamentId())
                .containsExactly(119880L);
    }

    @Test
    void refusesReceivedAtDriftBeyondPostgresMicrosecondResolution() {
        PageFixture only = page(452L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        Instant inMemoryReceivedAt = Instant.parse("2026-08-20T10:00:01.123456700Z");
        Instant divergentReceivedAt = Instant.parse("2026-08-20T10:00:01.123458Z");
        snapshotStore.put(source(only, divergentReceivedAt));
        publishCompletedEvidence(evidence(only, inMemoryReceivedAt));

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_METADATA_MISMATCH);
    }

    @Test
    void recomputesSizeAndHashBeforeParsing() {
        PageFixture only = page(501L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                false));
        RawSnapshotInspectionSource valid = source(only);
        RawSnapshotInspectionSummary summary = valid.summary();
        snapshotStore.put(new RawSnapshotInspectionSource(
                new RawSnapshotInspectionSummary(
                        summary.snapshotId(),
                        summary.logicalEndpoint(),
                        summary.requestKey(),
                        summary.receivedAt(),
                        summary.httpStatus(),
                        summary.contentType(),
                        summary.payloadSizeBytes(),
                        "0".repeat(64),
                        summary.parserVersion(),
                        summary.schemaStatus()),
                valid.payloadRaw()));
        publishCompleted(only);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_INTEGRITY_FAILURE);
    }

    @Test
    void refusesAReparsedPageWhosePaginationContradictsTheTerminalEvidence() {
        PageFixture only = page(601L, 1, false, payload(
                119880L,
                "Champions League phase",
                7L,
                "UEFA Champions League",
                true));
        snapshotStore.put(source(only));
        publishCompleted(only);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_PARSE_INCOMPATIBLE);
    }

    @Test
    void refusesAParsedEventListBecauseTheCatalogueRequiresScheduledTournaments() {
        PageFixture only = page(701L, 1, false, """
                {
                  "events": [],
                  "hasNextPage": false
                }
                """);
        snapshotStore.put(source(only));
        publishCompleted(only);

        assertThat(service.latest().status())
                .isEqualTo(J3TournamentCatalogStatus.SNAPSHOT_PARSE_INCOMPATIBLE);
    }

    private void publishCompleted(PageFixture... pages) {
        publishCompleted(DATE, pages);
    }

    private void publishCompleted(LocalDate date, PageFixture... pages) {
        List<J3MinimizedPageEvidence> attempts = new ArrayList<>();
        for (PageFixture page : pages) {
            attempts.add(evidence(page));
        }
        publishCompletedEvidence(
                date,
                attempts.toArray(J3MinimizedPageEvidence[]::new));
    }

    private void publishCompletedEvidence(J3MinimizedPageEvidence... attempts) {
        publishCompletedEvidence(DATE, attempts);
    }

    private void publishCompletedEvidence(
            LocalDate date,
            J3MinimizedPageEvidence... attempts) {
        evidenceService.publish(new J3MinimizedCollectionEvidence(
                date,
                J3ManualCallIntentState.COMPLETED,
                0,
                attempts.length,
                null,
                "NONE",
                BASE_TIME.plusSeconds(100),
                true,
                J3CircuitState.LOCKED,
                J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK,
                Duration.ofMinutes(10),
                List.of(attempts)));
    }

    private static J3MinimizedPageEvidence evidence(PageFixture page) {
        return evidence(page, receivedAt(page.page()));
    }

    private static J3MinimizedPageEvidence evidence(
            PageFixture page,
            Instant receivedAt) {
        return new J3MinimizedPageEvidence(
                page.page(),
                J3PageResolutionSource.PROVIDER,
                true,
                receivedAt,
                null,
                receivedAt.minusMillis(10),
                receivedAt,
                200,
                10L,
                page.snapshotId(),
                RawSnapshotPersistenceOutcome.INSERTED,
                page.raw().length,
                Sha256.hex(page.raw()),
                RawSnapshotSchemaStatus.PARSED,
                page.hasNextPage(),
                null);
    }

    private static J3MinimizedPageEvidence importedEvidence(PageFixture page) {
        Instant receivedAt = receivedAt(page.page());
        return new J3MinimizedPageEvidence(
                page.page(),
                J3PageResolutionSource.LOCAL_JSON_IMPORT,
                false,
                receivedAt,
                null,
                receivedAt,
                receivedAt,
                200,
                0L,
                page.snapshotId(),
                RawSnapshotPersistenceOutcome.INSERTED,
                page.raw().length,
                Sha256.hex(page.raw()),
                RawSnapshotSchemaStatus.PARSED,
                page.hasNextPage(),
                null);
    }

    private static RawSnapshotInspectionSource source(PageFixture page) {
        return source(DATE, page, receivedAt(page.page()));
    }

    private static RawSnapshotInspectionSource source(
            LocalDate date,
            PageFixture page) {
        return source(date, page, receivedAt(page.page()));
    }

    private static RawSnapshotInspectionSource source(
            PageFixture page,
            Instant receivedAt) {
        return source(DATE, page, receivedAt);
    }

    private static RawSnapshotInspectionSource source(
            LocalDate date,
            PageFixture page,
            Instant receivedAt) {
        return new RawSnapshotInspectionSource(
                new RawSnapshotInspectionSummary(
                        page.snapshotId(),
                        SofascoreEndpointType.SCHEDULED_EVENTS.name(),
                        requestKey(date, page.page()),
                        receivedAt,
                        200,
                        "application/json",
                        page.raw().length,
                        Sha256.hex(page.raw()),
                        ScheduledEventsV1Parser.PARSER_VERSION,
                        RawSnapshotSchemaStatus.PARSED),
                page.raw());
    }

    private static PageFixture page(
            long snapshotId,
            int page,
            boolean hasNextPage,
            String payload) {
        return new PageFixture(
                snapshotId,
                page,
                hasNextPage,
                payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String payload(
            long tournamentId,
            String tournamentName,
            long uniqueTournamentId,
            String uniqueTournamentName,
            boolean hasNextPage) {
        return payloadWithCategory(
                tournamentId,
                tournamentName,
                "Europe",
                uniqueTournamentId,
                uniqueTournamentName,
                hasNextPage);
    }

    private static String payloadWithCategory(
            long tournamentId,
            String tournamentName,
            String tournamentCategoryName,
            long uniqueTournamentId,
            String uniqueTournamentName,
            boolean hasNextPage) {
        return """
                {
                  "scheduled": [
                    {
                      "tournament": {
                        "id": %d,
                        "name": "%s",
                        "category": {"name": "%s"},
                        "uniqueTournament": {
                          "id": %d,
                          "name": "%s"
                        }
                      },
                      "timezoneEventCount": {"7200": 1}
                    }
                  ],
                  "hasNextPage": %s
                }
                """.formatted(
                tournamentId,
                tournamentName,
                tournamentCategoryName,
                uniqueTournamentId,
                uniqueTournamentName,
                hasNextPage);
    }

    private static Instant receivedAt(int page) {
        return BASE_TIME.plusSeconds(page);
    }

    private static String requestKey(int page) {
        return requestKey(DATE, page);
    }

    private static String requestKey(LocalDate date, int page) {
        return SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=" + date + "|page=" + page;
    }

    private record PageFixture(
            long snapshotId,
            int page,
            boolean hasNextPage,
            byte[] raw) {

        private PageFixture {
            raw = raw.clone();
        }

        @Override
        public byte[] raw() {
            return raw.clone();
        }
    }

    private static final class InMemorySnapshotStore implements RawSnapshotInspectionStore {

        private final Map<Long, RawSnapshotInspectionSource> snapshots = new LinkedHashMap<>();
        private final List<Long> requestedIds = new ArrayList<>();

        private void put(RawSnapshotInspectionSource source) {
            snapshots.put(source.summary().snapshotId(), source);
        }

        @Override
        public List<RawSnapshotInspectionSummary> findRecent(int limit) {
            return List.of();
        }

        @Override
        public Optional<RawSnapshotInspectionSource> findById(long snapshotId) {
            requestedIds.add(snapshotId);
            return Optional.ofNullable(snapshots.get(snapshotId));
        }
    }
}
