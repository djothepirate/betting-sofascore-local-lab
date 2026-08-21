package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV13Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventIncidentsV6Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventStatisticsV2Parser;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseResult;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.J5ParseStatus;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdata.J5UnavailableFamily;
import com.bettingproject.sofascorelocal.domain.provider.J5EventDataTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J5RealControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J5RealExecutionClaim;
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
import java.util.Set;
import java.util.UUID;

/** Executes a guarded J5 campaign from three manually supplied JSON response bodies. */
@Service
public class J5LocalJsonImportService {

    public static final int MAXIMUM_TOTAL_BYTES = 15 * 1024 * 1024;

    private static final String CONTENT_TYPE = "application/json";
    private static final Set<String> ERROR_FIELDS = Set.of("code", "message", "reason");
    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final J5RealControlService controlService;
    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final CanonicalEventStore canonicalEventStore;
    private final J5EventDataStore eventDataStore;
    private final EventStatisticsV2Parser statisticsParser;
    private final EventIncidentsV6Parser incidentsParser;
    private final EventLineupsV2Parser lineupsParser;
    private final Clock clock;

    @Autowired
    public J5LocalJsonImportService(
            J5RealControlService controlService,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore) {
        this(
                controlService,
                rawSnapshotStore,
                canonicalEventStore,
                eventDataStore,
                new EventStatisticsV2Parser(),
                new EventIncidentsV13Parser(),
                new EventLineupsV2Parser(),
                Clock.systemUTC());
    }

    J5LocalJsonImportService(
            J5RealControlService controlService,
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            J5EventDataStore eventDataStore,
            EventStatisticsV2Parser statisticsParser,
            EventIncidentsV6Parser incidentsParser,
            EventLineupsV2Parser lineupsParser,
            Clock clock) {
        this.controlService = Objects.requireNonNull(controlService, "controlService");
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.canonicalEventStore = Objects.requireNonNull(canonicalEventStore, "canonicalEventStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.statisticsParser = Objects.requireNonNull(statisticsParser, "statisticsParser");
        this.incidentsParser = Objects.requireNonNull(incidentsParser, "incidentsParser");
        this.lineupsParser = Objects.requireNonNull(lineupsParser, "lineupsParser");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized J5RealCampaignResult importCampaign(
            UUID canonicalEventId,
            UUID requestId,
            String confirmationText,
            boolean acknowledged,
            RawPayloadEvidence statisticsPayload,
            RawPayloadEvidence incidentsPayload,
            RawPayloadEvidence lineupsPayload) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        Objects.requireNonNull(requestId, "requestId");
        List<PreparedPayload> prepared = prevalidate(
                canonicalEventId,
                requestId,
                statisticsPayload,
                incidentsPayload,
                lineupsPayload);

        J5RealExecutionClaim claim = controlService.confirmAndClaim(
                requestId, confirmationText, acknowledged);
        if (!claim.canonicalEventId().equals(canonicalEventId)) {
            return failAndLock(claim, "EVENT_ID_MISMATCH", 0, List.of());
        }
        return execute(claim, prepared);
    }

    private List<PreparedPayload> prevalidate(
            UUID canonicalEventId,
            UUID requestId,
            RawPayloadEvidence statisticsPayload,
            RawPayloadEvidence incidentsPayload,
            RawPayloadEvidence lineupsPayload) {
        RawPayloadEvidence statistics = Objects.requireNonNull(
                statisticsPayload, "statisticsPayload");
        RawPayloadEvidence incidents = Objects.requireNonNull(
                incidentsPayload, "incidentsPayload");
        RawPayloadEvidence lineups = Objects.requireNonNull(
                lineupsPayload, "lineupsPayload");
        long totalBytes = (long) statistics.sizeBytes()
                + incidents.sizeBytes()
                + lineups.sizeBytes();
        if (totalBytes > MAXIMUM_TOTAL_BYTES) {
            throw rejected(J5LocalJsonImportError.LINEUPS_PAYLOAD_INCOMPATIBLE);
        }

        J5RealControlSnapshot pending = controlService.snapshot();
        if (!pending.awaitingConfirmation()
                || pending.requestId() == null
                || pending.canonicalEventId() == null
                || pending.eventId() == null) {
            throw rejected(J5LocalJsonImportError.NO_PENDING_CAMPAIGN);
        }
        if (!pending.requestId().equals(requestId)) {
            throw rejected(J5LocalJsonImportError.REQUEST_ID_MISMATCH);
        }
        if (!pending.canonicalEventId().equals(canonicalEventId)) {
            throw rejected(J5LocalJsonImportError.CANONICAL_EVENT_MISMATCH);
        }

        long eventId = pending.eventId();
        Instant inspectedAt = Instant.EPOCH;
        List<PreparedPayload> values = List.of(
                prepare(SofascoreEndpointType.EVENT_STATISTICS, statistics, eventId, inspectedAt),
                prepare(SofascoreEndpointType.EVENT_INCIDENTS, incidents, eventId, inspectedAt),
                prepare(SofascoreEndpointType.EVENT_LINEUPS, lineups, eventId, inspectedAt));
        return List.copyOf(values);
    }

    private PreparedPayload prepare(
            SofascoreEndpointType endpoint,
            RawPayloadEvidence payload,
            long eventId,
            Instant inspectedAt) {
        if (isExplicitUnavailable404(payload)) {
            return new PreparedPayload(endpoint, payload, true);
        }
        J5ParseResult<? extends J5EventData> parsed = parse(
                endpoint, 1L, eventId, payload, inspectedAt);
        if (parsed.status() != J5ParseStatus.PARSED) {
            throw rejected(errorFor(endpoint));
        }
        return new PreparedPayload(endpoint, payload, false);
    }

    private J5RealCampaignResult execute(
            J5RealExecutionClaim claim,
            List<PreparedPayload> preparedPayloads) {
        List<J5RealEndpointResult> results = new ArrayList<>();
        int imports = 0;
        if (!controlService.executionMayContinue(claim.requestId())) {
            return failed(claim, "OPERATOR_STOP", imports, results);
        }
        var canonical = canonicalEventStore.findLatestByCanonicalId(claim.canonicalEventId());
        if (canonical.isEmpty()
                || canonical.orElseThrow().identity().providerEventId() != claim.eventId()
                || !canonical.orElseThrow().identity().value().equals(claim.canonicalEventId())) {
            return failAndLock(claim, "EVENT_ID_MISMATCH", imports, results);
        }

        for (PreparedPayload prepared : preparedPayloads) {
            if (!controlService.executionMayContinue(claim.requestId())) {
                return failed(claim, "OPERATOR_STOP", imports, results);
            }
            Instant importedAt = clock.instant();
            J5EventDataTransportResponse response = new J5EventDataTransportResponse(
                    prepared.endpoint(),
                    requestKey(prepared.endpoint(), claim.eventId()),
                    importedAt,
                    importedAt,
                    prepared.unavailable() ? 404 : 200,
                    CONTENT_TYPE,
                    Duration.ZERO,
                    prepared.payload());
            RawSnapshotPersistenceResult raw;
            try {
                raw = rawSnapshotStore.save(rawOnlyImported(response));
                imports++;
            }
            catch (RuntimeException exception) {
                return failAndLock(claim, "RAW_PERSISTENCE_ERROR", imports, results);
            }

            J5ParseResult<? extends J5EventData> parsed = null;
            J5EventData data;
            J5CompletenessReport completeness;
            int warningCount;
            RawSnapshotSchemaStatus finalStatus;
            String parserVersion;
            if (prepared.unavailable()) {
                data = J5UnavailableFamily.emptyObservation(
                        prepared.endpoint(), claim.eventId());
                completeness = J5CompletenessReport.unavailable();
                warningCount = 0;
                finalStatus = RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE;
                parserVersion = J5UnavailableFamily.normalizerVersion(prepared.endpoint());
            }
            else {
                try {
                    parsed = parse(
                            prepared.endpoint(),
                            raw.snapshotId(),
                            claim.eventId(),
                            prepared.payload(),
                            importedAt);
                }
                catch (RuntimeException exception) {
                    return failAndLock(claim, "PARSER_FAILURE", imports, results);
                }
                if (parsed.status() != J5ParseStatus.PARSED) {
                    RawSnapshotSchemaStatus incompatible = parsed.status()
                            == J5ParseStatus.UNEXPECTED_CONTENT
                                    ? RawSnapshotSchemaStatus.UNEXPECTED_CONTENT
                                    : RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
                    classifyInsertedSafely(raw, incompatible, incompatible.name());
                    return failAndLock(
                            claim, errorFor(prepared.endpoint()).name(), imports, results);
                }
                data = parsed.data().orElseThrow();
                completeness = parsed.completeness().orElseThrow();
                warningCount = parsed.warnings().size();
                finalStatus = RawSnapshotSchemaStatus.PARSED;
                parserVersion = parserVersion(prepared.endpoint());
            }

            EventSourceTrace source = EventSourceTrace.providerSnapshot(
                    raw.snapshotId(),
                    raw.payloadSha256(),
                    parserVersion,
                    importedAt);
            J5EventDataPersistenceResult persisted;
            try {
                persisted = eventDataStore.save(J5EventDataObservation.from(
                        canonical.orElseThrow().identity(), data, source, completeness));
            }
            catch (RuntimeException exception) {
                return failAndLock(
                        claim, "NORMALIZATION_PERSISTENCE_ERROR", imports, results);
            }
            if (!classifyInsertedSafely(raw, finalStatus, null)) {
                return failAndLock(claim, "RAW_CLASSIFICATION_ERROR", imports, results);
            }
            results.add(new J5RealEndpointResult(
                    prepared.endpoint(),
                    raw.snapshotId(),
                    raw.payloadSha256(),
                    raw.payloadSizeBytes(),
                    persisted.observationId(),
                    persisted.inserted(),
                    completeness.status(),
                    completeness.scorePercent(),
                    warningCount));
            try {
                controlService.recordEndpointCompleted(
                        claim.requestId(), prepared.endpoint());
            }
            catch (J5RealControlException exception) {
                return failed(claim, "OPERATOR_STOP", imports, results);
            }
        }

        try {
            controlService.complete(claim.requestId());
        }
        catch (J5RealControlException exception) {
            return failed(claim, "OPERATOR_STOP", imports, results);
        }
        return new J5RealCampaignResult(
                claim.requestId(),
                claim.canonicalEventId(),
                claim.eventId(),
                true,
                "COMPLETED",
                0,
                imports,
                results);
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

    private J5RealCampaignResult failAndLock(
            J5RealExecutionClaim claim,
            String code,
            int imports,
            List<J5RealEndpointResult> results) {
        if (controlService.executionMayContinue(claim.requestId())) {
            try {
                controlService.fail(claim.requestId(), code);
            }
            catch (J5RealControlException exception) {
                return failed(claim, "OPERATOR_STOP", imports, results);
            }
        }
        return failed(claim, code, imports, results);
    }

    private static J5RealCampaignResult failed(
            J5RealExecutionClaim claim,
            String code,
            int imports,
            List<J5RealEndpointResult> results) {
        return new J5RealCampaignResult(
                claim.requestId(),
                claim.canonicalEventId(),
                claim.eventId(),
                false,
                code,
                0,
                imports,
                results);
    }

    private static RawManualCallSnapshot rawOnlyImported(
            J5EventDataTransportResponse response) {
        return new RawManualCallSnapshot(
                response.endpointType(),
                RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT,
                response.requestKey(),
                response.requestedAt(),
                response.receivedAt(),
                response.httpStatus(),
                response.contentType(),
                response.latency(),
                response.payload(),
                parserVersion(response.endpointType()),
                RawSnapshotSchemaStatus.RAW_ONLY,
                null);
    }

    private boolean classifyInsertedSafely(
            RawSnapshotPersistenceResult raw,
            RawSnapshotSchemaStatus status,
            String errorCode) {
        if (raw.outcome() == RawSnapshotPersistenceOutcome.DEDUPLICATED) {
            return true;
        }
        if (raw.outcome() != RawSnapshotPersistenceOutcome.INSERTED) {
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
        if (code == null || !code.isIntegralNumber() || code.intValue() != 404) {
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

    private static String parserVersion(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> EventStatisticsV2Parser.PARSER_VERSION;
            case EVENT_INCIDENTS -> EventIncidentsV13Parser.PARSER_VERSION;
            case EVENT_LINEUPS -> EventLineupsV2Parser.PARSER_VERSION;
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    private static J5LocalJsonImportError errorFor(
            SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_STATISTICS -> J5LocalJsonImportError.STATISTICS_PAYLOAD_INCOMPATIBLE;
            case EVENT_INCIDENTS -> J5LocalJsonImportError.INCIDENTS_PAYLOAD_INCOMPATIBLE;
            case EVENT_LINEUPS -> J5LocalJsonImportError.LINEUPS_PAYLOAD_INCOMPATIBLE;
            default -> throw new IllegalArgumentException("unsupported J5 endpoint");
        };
    }

    private static J5LocalJsonImportException rejected(
            J5LocalJsonImportError error) {
        return new J5LocalJsonImportException(error);
    }

    private record PreparedPayload(
            SofascoreEndpointType endpoint,
            RawPayloadEvidence payload,
            boolean unavailable) {

        private PreparedPayload {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(payload, "payload");
        }
    }
}
