package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV17Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV6Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdata.J5UnavailableFamily;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Reusable, transport-free J5 processor for manually supplied local JSON bodies. */
@Service
public class J5LocalJsonImportProcessor {

    public static final int MAXIMUM_TOTAL_BYTES = 15 * 1024 * 1024;

    private static final String CONTENT_TYPE = "application/json";
    private static final Set<String> ERROR_FIELDS = Set.of("code", "message", "reason");
    private static final List<SofascoreEndpointType> ORDERED_ENDPOINTS = List.of(
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final CanonicalEventStore canonicalEventStore;
    private final J5EventDataStore eventDataStore;
    private final EventStatisticsV2Parser statisticsParser;
    private final EventIncidentsV6Parser incidentsParser;
    private final EventLineupsV2Parser lineupsParser;
    private final Clock clock;

    @Autowired
    public J5LocalJsonImportProcessor(
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore) {
        this(
                rawSnapshotStore,
                canonicalEventStore,
                eventDataStore,
                new EventStatisticsV2Parser(),
                new EventIncidentsV17Parser(),
                new EventLineupsV2Parser(),
                Clock.systemUTC());
    }

    J5LocalJsonImportProcessor(
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            EventStatisticsV2Parser statisticsParser,
            EventIncidentsV6Parser incidentsParser,
            EventLineupsV2Parser lineupsParser,
            Clock clock) {
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore, "canonicalEventStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.statisticsParser = Objects.requireNonNull(statisticsParser, "statisticsParser");
        this.incidentsParser = Objects.requireNonNull(incidentsParser, "incidentsParser");
        this.lineupsParser = Objects.requireNonNull(lineupsParser, "lineupsParser");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Parses all three bodies and validates their current canonical identity without writing.
     */
    public J5LocalJsonImportProcessingPlan prepare(
            UUID canonicalEventId,
            long eventId,
            RawPayloadEvidence statisticsPayload,
            RawPayloadEvidence incidentsPayload,
            RawPayloadEvidence lineupsPayload) {
        UUID canonicalId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        RawPayloadEvidence statistics = Objects.requireNonNull(
                statisticsPayload, "statisticsPayload");
        RawPayloadEvidence incidents = Objects.requireNonNull(
                incidentsPayload, "incidentsPayload");
        RawPayloadEvidence lineups = Objects.requireNonNull(
                lineupsPayload, "lineupsPayload");
        if (eventId < 1) {
            throw failure("EVENT_ID_MISMATCH", 0, List.of());
        }

        long totalBytes = (long) statistics.sizeBytes()
                + incidents.sizeBytes()
                + lineups.sizeBytes();
        if (totalBytes > MAXIMUM_TOTAL_BYTES) {
            throw failure("TOTAL_PAYLOAD_TOO_LARGE", 0, List.of());
        }

        J5LocalJsonImportProcessingPlan plan = new J5LocalJsonImportProcessingPlan(
                canonicalId, eventId, statistics, incidents, lineups);
        for (SofascoreEndpointType endpoint : ORDERED_ENDPOINTS) {
            prevalidate(endpoint, plan.payloadFor(endpoint), eventId);
        }
        requireCurrentIdentity(canonicalId, eventId, 0, List.of());
        return plan;
    }

    /**
     * Persists one prepared import in strict endpoint order. This method deliberately owns no
     * transaction so a batch caller can define the surrounding transaction boundary.
     */
    public J5LocalJsonImportProcessingResult execute(
            J5LocalJsonImportProcessingPlan prepared,
            BooleanSupplier mayContinue) {
        return execute(prepared, mayContinue, ignored -> { });
    }

    J5LocalJsonImportProcessingResult execute(
            J5LocalJsonImportProcessingPlan prepared,
            BooleanSupplier mayContinue,
            Consumer<SofascoreEndpointType> endpointCompleted) {
        return execute(prepared, mayContinue, endpointCompleted, (endpoint, raw) -> { });
    }

    J5LocalJsonImportProcessingResult execute(
            J5LocalJsonImportProcessingPlan prepared,
            BooleanSupplier mayContinue,
            Consumer<SofascoreEndpointType> endpointCompleted,
            BiConsumer<SofascoreEndpointType, RawSnapshotPersistenceResult> snapshotPersisted) {
        return execute(
                prepared,
                mayContinue,
                ignored -> { },
                endpointCompleted,
                snapshotPersisted);
    }

    J5LocalJsonImportProcessingResult execute(
            J5LocalJsonImportProcessingPlan prepared,
            BooleanSupplier mayContinue,
            Consumer<SofascoreEndpointType> endpointReached,
            Consumer<SofascoreEndpointType> endpointCompleted,
            BiConsumer<SofascoreEndpointType, RawSnapshotPersistenceResult> snapshotPersisted) {
        return execute(
                prepared,
                mayContinue,
                endpointReached,
                endpointCompleted,
                snapshotPersisted,
                ignored -> { });
    }

    J5LocalJsonImportProcessingResult execute(
            J5LocalJsonImportProcessingPlan prepared,
            BooleanSupplier mayContinue,
            Consumer<SofascoreEndpointType> endpointReached,
            Consumer<SofascoreEndpointType> endpointCompleted,
            BiConsumer<SofascoreEndpointType, RawSnapshotPersistenceResult> snapshotPersisted,
            Consumer<J5RealEndpointResult> evidenceCompleted) {
        J5LocalJsonImportProcessingPlan plan = Objects.requireNonNull(prepared, "prepared");
        BooleanSupplier continuation = Objects.requireNonNull(mayContinue, "mayContinue");
        Consumer<SofascoreEndpointType> reached = Objects.requireNonNull(
                endpointReached, "endpointReached");
        Consumer<SofascoreEndpointType> progress = Objects.requireNonNull(
                endpointCompleted, "endpointCompleted");
        BiConsumer<SofascoreEndpointType, RawSnapshotPersistenceResult> snapshotProgress =
                Objects.requireNonNull(snapshotPersisted, "snapshotPersisted");
        Consumer<J5RealEndpointResult> evidenceProgress = Objects.requireNonNull(
                evidenceCompleted, "evidenceCompleted");
        List<J5RealEndpointResult> results = new ArrayList<>();
        int imports = 0;

        requireContinuation(continuation, imports, results);
        CanonicalEventIdentity identity = requireCurrentIdentity(
                plan.canonicalEventId(), plan.eventId(), imports, results);

        for (SofascoreEndpointType endpoint : ORDERED_ENDPOINTS) {
            requireContinuation(continuation, imports, results);
            try {
                reached.accept(endpoint);
            }
            catch (RuntimeException exception) {
                throw failure("BENCHMARK_AUDIT_FAILURE", imports, results, exception);
            }
            RawPayloadEvidence payload = plan.payloadFor(endpoint);
            boolean unavailable = isExplicitUnavailable404(payload);
            Instant importedAt;
            try {
                importedAt = clock.instant();
            }
            catch (RuntimeException exception) {
                throw failure("CLOCK_ERROR", imports, results, exception);
            }

            RawSnapshotPersistenceResult raw;
            try {
                raw = Objects.requireNonNull(
                        rawSnapshotStore.save(rawOnlyImported(
                                endpoint,
                                plan.eventId(),
                                importedAt,
                                unavailable ? 404 : 200,
                                payload)),
                        "rawSnapshotStore result");
            }
            catch (RuntimeException exception) {
                throw failure("RAW_PERSISTENCE_ERROR", imports, results, exception);
            }
            try {
                snapshotProgress.accept(endpoint, raw);
            }
            catch (RuntimeException exception) {
                throw failure("BENCHMARK_AUDIT_FAILURE", imports, results, exception);
            }
            imports++;

            J5EventData data;
            J5CompletenessReport completeness;
            int warningCount;
            RawSnapshotSchemaStatus finalStatus;
            String parserVersion;
            if (unavailable) {
                data = J5UnavailableFamily.emptyObservation(endpoint, plan.eventId());
                completeness = J5CompletenessReport.unavailable();
                warningCount = 0;
                finalStatus = RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE;
                parserVersion = J5UnavailableFamily.normalizerVersion(endpoint);
            }
            else {
                J5ParseResult<? extends J5EventData> parsed;
                try {
                    parsed = Objects.requireNonNull(
                            parse(endpoint, raw.snapshotId(), plan.eventId(), payload, importedAt),
                            "parser result");
                }
                catch (RuntimeException exception) {
                    throw failure("PARSER_FAILURE", imports, results, exception);
                }
                if (parsed.status() != J5ParseStatus.PARSED) {
                    RawSnapshotSchemaStatus incompatible = parsed.status()
                            == J5ParseStatus.UNEXPECTED_CONTENT
                                    ? RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                                    : RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
                    classifySafely(raw, incompatible, incompatible.name());
                    throw failure(errorCodeFor(endpoint), imports, results);
                }
                try {
                    data = parsed.data().orElseThrow();
                    completeness = parsed.completeness().orElseThrow();
                    warningCount = parsed.warnings().size();
                }
                catch (RuntimeException exception) {
                    throw failure("PARSER_FAILURE", imports, results, exception);
                }
                finalStatus = RawSnapshotSchemaStatus.PARSED;
                parserVersion = parserVersion(endpoint);
            }

            J5EventDataPersistenceResult persisted;
            try {
                EventSourceTrace source = EventSourceTrace.providerSnapshot(
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        parserVersion,
                        importedAt);
                persisted = Objects.requireNonNull(
                        eventDataStore.save(J5EventDataObservation.from(
                                identity, data, source, completeness)),
                        "eventDataStore result");
            }
            catch (RuntimeException exception) {
                throw failure(
                        "NORMALIZATION_PERSISTENCE_ERROR", imports, results, exception);
            }
            if (!classifySafely(raw, finalStatus, null)) {
                throw failure("RAW_CLASSIFICATION_ERROR", imports, results);
            }

            J5RealEndpointResult result;
            try {
                result = new J5RealEndpointResult(
                        endpoint,
                        raw.snapshotId(),
                        raw.payloadSha256(),
                        raw.payloadSizeBytes(),
                        persisted.observationId(),
                        persisted.inserted(),
                        completeness.status(),
                        completeness.scorePercent(),
                        warningCount);
            }
            catch (RuntimeException exception) {
                throw failure("PROCESSING_RESULT_ERROR", imports, results, exception);
            }
            results.add(result);
            try {
                evidenceProgress.accept(result);
            }
            catch (RuntimeException exception) {
                throw failure("BENCHMARK_AUDIT_FAILURE", imports, results, exception);
            }
            try {
                progress.accept(endpoint);
            }
            catch (RuntimeException exception) {
                throw failure("PROGRESS_CALLBACK_ERROR", imports, results, exception);
            }
        }

        return new J5LocalJsonImportProcessingResult(imports, results);
    }

    private void prevalidate(
            SofascoreEndpointType endpoint,
            RawPayloadEvidence payload,
            long eventId) {
        if (isExplicitUnavailable404(payload)) {
            return;
        }
        J5ParseResult<? extends J5EventData> parsed;
        try {
            parsed = Objects.requireNonNull(
                    parse(endpoint, 1L, eventId, payload, Instant.EPOCH),
                    "parser result");
        }
        catch (RuntimeException exception) {
            throw failure("PARSER_FAILURE", 0, List.of(), exception);
        }
        if (parsed.status() != J5ParseStatus.PARSED) {
            throw failure(errorCodeFor(endpoint), 0, List.of());
        }
    }

    private CanonicalEventIdentity requireCurrentIdentity(
            UUID canonicalEventId,
            long eventId,
            int imports,
            List<J5RealEndpointResult> results) {
        var current = findCurrentCanonical(canonicalEventId, imports, results);
        if (current.isEmpty()) {
            throw failure("EVENT_ID_MISMATCH", imports, results);
        }
        CanonicalEventIdentity identity;
        try {
            identity = current.orElseThrow().identity();
        }
        catch (RuntimeException exception) {
            throw failure("CANONICAL_EVENT_LOOKUP_ERROR", imports, results, exception);
        }
        if (identity == null
                || identity.providerEventId() != eventId
                || !identity.value().equals(canonicalEventId)) {
            throw failure("EVENT_ID_MISMATCH", imports, results);
        }
        return identity;
    }

    private Optional<CanonicalEventObservationView> findCurrentCanonical(
            UUID canonicalEventId,
            int imports,
            List<J5RealEndpointResult> results) {
        try {
            return Objects.requireNonNull(
                    canonicalEventStore.findLatestByCanonicalId(canonicalEventId),
                    "canonicalEventStore result");
        }
        catch (RuntimeException exception) {
            throw failure("CANONICAL_EVENT_LOOKUP_ERROR", imports, results, exception);
        }
    }

    private J5ParseResult<? extends J5EventData> parse(
            SofascoreEndpointType endpoint,
            long snapshotId,
            long eventId,
            RawPayloadEvidence payload,
            Instant receivedAt) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> statisticsParser.parse(
                    snapshotId, eventId, payload, receivedAt);
            case EVENT_INCIDENTS -> incidentsParser.parse(
                    snapshotId, eventId, payload, receivedAt);
            case EVENT_LINEUPS -> lineupsParser.parse(
                    snapshotId, eventId, payload, receivedAt);
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    private static void requireContinuation(
            BooleanSupplier mayContinue,
            int imports,
            List<J5RealEndpointResult> results) {
        boolean allowed;
        try {
            allowed = mayContinue.getAsBoolean();
        }
        catch (RuntimeException exception) {
            throw failure("CONTINUATION_CHECK_ERROR", imports, results, exception);
        }
        if (!allowed) {
            throw failure("OPERATOR_STOP", imports, results);
        }
    }

    private static RawManualCallSnapshot rawOnlyImported(
            SofascoreEndpointType endpoint,
            long eventId,
            Instant importedAt,
            int status,
            RawPayloadEvidence payload) {
        return new RawManualCallSnapshot(
                endpoint,
                RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                requestKey(endpoint, eventId),
                importedAt,
                importedAt,
                status,
                CONTENT_TYPE,
                Duration.ZERO,
                payload,
                parserVersion(endpoint),
                RawSnapshotSchemaStatus.RAW_ONLY,
                null);
    }

    private boolean classifySafely(
            RawSnapshotPersistenceResult raw,
            RawSnapshotSchemaStatus status,
            String errorCode) {
        if (raw.outcome() != RawSnapshotPersistenceOutcome.INSERTED
                && raw.outcome() != RawSnapshotPersistenceOutcome.DEDUPLICATED) {
            return false;
        }
        try {
            rawSnapshotStore.classify(raw.snapshotId(), status, errorCode);
            return true;
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isExplicitUnavailable404(RawPayloadEvidence payload) {
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(payload.bytes());
        }
        catch (JacksonException exception) {
            return false;
        }
        if (root == null || !root.isObject() || root.size() != 1) {
            return false;
        }
        JsonNode error = root.get("error");
        if (error == null || !error.isObject()) {
            return false;
        }
        if (error.properties().stream()
                .map(entry -> entry.getKey())
                .anyMatch(field -> !ERROR_FIELDS.contains(field))) {
            return false;
        }
        JsonNode code = error.get("code");
        if (code == null
                || !code.isIntegralNumber()
                || !code.canConvertToInt()
                || code.intValue() != 404) {
            return false;
        }
        return boundedOptionalText(error.get("message"))
                && boundedOptionalText(error.get("reason"));
    }

    private static boolean boundedOptionalText(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        if (!node.isString()) {
            return false;
        }
        String value = node.stringValue();
        return !value.isBlank()
                && value.length() <= 200
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static String requestKey(
            SofascoreEndpointType endpoint,
            long eventId) {
        return endpoint.name() + "|eventId=" + eventId;
    }

    static String parserVersion(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> EventStatisticsV2Parser.PARSER_VERSION;
            case EVENT_INCIDENTS -> EventIncidentsV17Parser.PARSER_VERSION;
            case EVENT_LINEUPS -> EventLineupsV2Parser.PARSER_VERSION;
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    private static String errorCodeFor(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> "STATISTICS_PAYLOAD_INCOMPATIBLE";
            case EVENT_INCIDENTS -> "INCIDENTS_PAYLOAD_INCOMPATIBLE";
            case EVENT_LINEUPS -> "LINEUPS_PAYLOAD_INCOMPATIBLE";
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    private static J5LocalJsonImportProcessingException failure(
            String code,
            int imports,
            List<J5RealEndpointResult> results) {
        return new J5LocalJsonImportProcessingException(code, imports, results);
    }

    private static J5LocalJsonImportProcessingException failure(
            String code,
            int imports,
            List<J5RealEndpointResult> results,
            RuntimeException cause) {
        return new J5LocalJsonImportProcessingException(code, imports, results, cause);
    }
}
