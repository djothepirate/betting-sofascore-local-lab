package com.bettingproject.sofascorelocal.application.snapshot;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.Sha256;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawSnapshotJsonInspectionServiceTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-14T09:31:23Z");
    private static final Instant INSPECTED_AT = Instant.parse("2026-08-14T10:00:00Z");
    private static final byte[] NOMINAL_PAYLOAD =
            "{\"events\":[],\"hasNextPage\":false}".getBytes(StandardCharsets.UTF_8);

    @Test
    void verifiesAndFormatsOnlyTheExplicitlySelectedLocalSnapshot() {
        FakeStore store = new FakeStore(source(NOMINAL_PAYLOAD));
        RawSnapshotJsonInspectionService service = service(store);

        RawSnapshotJsonInspection inspection = service.inspect(41L);

        assertThat(inspection.summary().snapshotId()).isEqualTo(41L);
        assertThat(inspection.inspectedAt()).isEqualTo(INSPECTED_AT);
        assertThat(inspection.formattedJson())
                .contains("\n")
                .contains("\"events\" : [ ]")
                .contains("\"hasNextPage\" : false");
        assertThat(store.requestedSnapshotId).isEqualTo(41L);
        assertThat(store.source.orElseThrow().payloadRaw()).isEqualTo(NOMINAL_PAYLOAD);
    }

    @Test
    void loadsABoundedMetadataOnlyCatalog() {
        FakeStore store = new FakeStore(source(NOMINAL_PAYLOAD));

        RawSnapshotInspectionCatalog catalog = service(store).loadCatalog();

        assertThat(catalog.available()).isTrue();
        assertThat(catalog.snapshots()).singleElement()
                .extracting(RawSnapshotInspectionSummary::snapshotId)
                .isEqualTo(41L);
        assertThat(store.requestedLimit)
                .isEqualTo(RawSnapshotJsonInspectionService.RECENT_SNAPSHOT_LIMIT);
        assertThat(store.findByIdCalled).isFalse();
    }

    @Test
    void degradesTheCatalogSafelyWhenTheLocalDatabaseIsUnavailable() {
        FakeStore store = new FakeStore(Optional.empty());
        store.catalogFailure = new DataRetrievalFailureException("database unavailable");

        RawSnapshotInspectionCatalog catalog = service(store).loadCatalog();

        assertThat(catalog.available()).isFalse();
        assertThat(catalog.snapshots()).isEmpty();
    }

    @Test
    void blocksInspectionWhenPersistedIntegrityMetadataDoesNotMatch() {
        RawSnapshotInspectionSummary invalidSummary = summary(
                NOMINAL_PAYLOAD.length,
                "a".repeat(64));
        FakeStore store = new FakeStore(Optional.of(
                new RawSnapshotInspectionSource(invalidSummary, NOMINAL_PAYLOAD)));

        assertThatThrownBy(() -> service(store).inspect(41L))
                .isInstanceOfSatisfying(
                        RawSnapshotInspectionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(RawSnapshotInspectionError.PAYLOAD_INTEGRITY_FAILURE));
    }

    @Test
    void blocksSensitiveContentBeforeItCanBeFormatted() {
        byte[] sensitive = "{\"access_token\":\"local-secret\"}"
                .getBytes(StandardCharsets.UTF_8);
        FakeStore store = new FakeStore(source(sensitive));

        assertThatThrownBy(() -> service(store).inspect(41L))
                .isInstanceOfSatisfying(
                        RawSnapshotInspectionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(RawSnapshotInspectionError.SENSITIVE_CONTENT_BLOCKED));
    }

    @Test
    void rejectsDuplicateFieldsAndTrailingJson() {
        byte[] duplicate = "{\"events\":[],\"events\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] trailing = "{\"events\":[]} trailing"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service(new FakeStore(source(duplicate))).inspect(41L))
                .isInstanceOfSatisfying(
                        RawSnapshotInspectionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(RawSnapshotInspectionError.INVALID_JSON));
        assertThatThrownBy(() -> service(new FakeStore(source(trailing))).inspect(41L))
                .isInstanceOfSatisfying(
                        RawSnapshotInspectionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(RawSnapshotInspectionError.INVALID_JSON));
    }

    @Test
    void rejectsAnUnknownOrInvalidSelectionWithoutReturningContent() {
        FakeStore store = new FakeStore(Optional.empty());

        assertThatThrownBy(() -> service(store).inspect(41L))
                .isInstanceOfSatisfying(
                        RawSnapshotInspectionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(RawSnapshotInspectionError.SNAPSHOT_NOT_FOUND));
        assertThatThrownBy(() -> service(store).inspect(0L))
                .isInstanceOfSatisfying(
                        RawSnapshotInspectionException.class,
                        exception -> assertThat(exception.error())
                                .isEqualTo(RawSnapshotInspectionError.INVALID_SELECTION));
    }

    private static RawSnapshotJsonInspectionService service(FakeStore store) {
        return new RawSnapshotJsonInspectionService(
                store,
                Clock.fixed(INSPECTED_AT, ZoneOffset.UTC));
    }

    private static Optional<RawSnapshotInspectionSource> source(byte[] payload) {
        return Optional.of(new RawSnapshotInspectionSource(
                summary(payload.length, Sha256.hex(payload)),
                payload));
    }

    private static RawSnapshotInspectionSummary summary(long size, String sha256) {
        return new RawSnapshotInspectionSummary(
                41L,
                "SCHEDULED_EVENTS",
                "SCHEDULED_EVENTS|date=2026-08-14|page=1",
                RECEIVED_AT,
                200,
                "application/json; charset=utf-8",
                size,
                sha256,
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED);
    }

    private static final class FakeStore implements RawSnapshotInspectionStore {

        private final Optional<RawSnapshotInspectionSource> source;
        private RuntimeException catalogFailure;
        private int requestedLimit;
        private long requestedSnapshotId;
        private boolean findByIdCalled;

        private FakeStore(Optional<RawSnapshotInspectionSource> source) {
            this.source = source;
        }

        @Override
        public List<RawSnapshotInspectionSummary> findRecent(int limit) {
            requestedLimit = limit;
            if (catalogFailure != null) {
                throw catalogFailure;
            }
            return source.stream().map(RawSnapshotInspectionSource::summary).toList();
        }

        @Override
        public Optional<RawSnapshotInspectionSource> findById(long snapshotId) {
            findByIdCalled = true;
            requestedSnapshotId = snapshotId;
            return source;
        }
    }
}
