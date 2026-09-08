package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineupPlayer;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatisticMetric;
import com.bettingproject.sofascorelocal.domain.eventdata.EventStatistics;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventSeason;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventVenue;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.domain.history.J6RawPayloadState;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7EnvelopeAssemblerTest {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Instant RECORDED_AT = Instant.parse("2026-08-18T19:30:00Z");
    private static final Instant GENERATED_AT = Instant.parse("2026-08-19T08:00:00Z");

    private final J7EnvelopeAssembler assembler = new J7EnvelopeAssembler();
    private final J7ExportIntegrityGuard integrityGuard = new J7ExportIntegrityGuard(
            new J7JsonSchemaValidator());

    @Test
    void integrityGuardAcceptsTheGeneratedCandidate() {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        J7VerifiedEnvelope verified = integrityGuard.verify(
                candidate.content(),
                candidate.canonicalEventId(),
                candidate.exportId());

        assertThat(verified.dataSha256()).isEqualTo(candidate.dataSha256());
        assertThat(verified.sourceSetSha256()).isEqualTo(candidate.sourceSetSha256());
        assertThat(verified.status()).isEqualTo(J7ExportStatus.COHERENCE_CHECKED);
    }

    @Test
    void integrityGuardRejectsAdditionalPropertiesAndInvalidFormats() throws Exception {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        assertSchemaMutationRejected(candidate, root ->
                ((ObjectNode) root.get("data")).put("unexpected", true));
        assertSchemaMutationRejected(candidate, root ->
                ((ObjectNode) root.get("manifest")).put("exportId", "not-a-uuid"));
        assertSchemaMutationRejected(candidate, root ->
                ((ObjectNode) root.get("manifest")).put("generatedAt", "2026-08-19"));
        assertSchemaMutationRejected(candidate, root ->
                ((ObjectNode) root.get("manifest")).put("dataSha256", "A".repeat(64)));
        assertSchemaMutationRejected(candidate, root ->
                ((ObjectNode) root.get("data").get("identity"))
                        .put("providerEventId", new java.math.BigInteger(
                                "18446744073709551617")));
        assertSchemaMutationRejected(candidate, root ->
                ((ObjectNode) root.get("manifest").get("sources").get(0))
                        .put("observationId", new java.math.BigInteger(
                                "9223372036854775808")));
    }

    @Test
    void verifiesV3DetailProvenanceWhileKeepingTheFrozenJ7V1DataContract() throws Exception {
        J7CurrentEventSelection selection = awardedSyntheticSelection();
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.randomUUID(), GENERATED_AT, "0.1.0-SNAPSHOT", selection);

        J7VerifiedEnvelope verified = integrityGuard.verify(
                candidate.content(), candidate.canonicalEventId(), candidate.exportId());
        JsonNode root = JSON_MAPPER.readTree(candidate.content());
        JsonNode details = root.path("data").path("eventDetails").path("details");
        assertThat(details.size()).isEqualTo(8);
        for (String field : List.of("startsAt", "homeTeam", "awayTeam", "status",
                "tournament", "venue", "season", "round")) {
            assertThat(details.has(field)).as("J7 v1 detail field %s", field).isTrue();
        }
        for (String field : List.of("isAwarded", "homeDisplayScore", "awayDisplayScore",
                "homeScore", "awayScore")) {
            assertThat(details.has(field)).as("new J4 field %s excluded from J7 v1", field).isFalse();
        }
        JsonNode detailSource = root.path("manifest").path("sources").get(1);
        assertThat(detailSource.path("parserVersion").asText()).isEqualTo("event-details-v3");
        assertThat(detailSource.path("normalizedSha256").asText())
                .isEqualTo(selection.eventDetails().orElseThrow().normalizedSha256());
        assertThat(verified.status()).isEqualTo(J7ExportStatus.COHERENCE_CHECKED);
    }

    @Test
    void rejectsAnAlteredV3DisplayScoreEvenThoughJ7V1DoesNotSerializeIt() {
        J7CurrentEventSelection valid = awardedSyntheticSelection();
        var original = valid.eventDetails().orElseThrow();
        EventDetails details = original.details();
        EventDetails altered = new EventDetails(
                details.providerEventId(), details.startsAt(), details.homeTeam(), details.awayTeam(),
                details.status(), details.tournament(), details.venue(), details.season(), details.round(),
                details.isAwarded(), Optional.of(2), details.awayDisplayScore());
        var corrupted = new EventDetailObservationView(original.observationId(), original.identity(),
                altered, original.source(), original.normalizedSha256());
        var selection = new J7CurrentEventSelection(valid.eventState(), Optional.of(corrupted),
                valid.eventData(), valid.snapshotTraces());

        assertThatThrownBy(() -> assembler.assembleCandidate(
                UUID.randomUUID(), GENERATED_AT, "0.1.0-SNAPSHOT", selection))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.INVALID_HASH));
    }

    @Test
    void integrityGuardRejectsSensitiveDecisionText() {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());
        J7AssembledEnvelope rejected = assembler.decide(
                candidate.content(),
                J7ExportStatus.REJECTED,
                GENERATED_AT.plusSeconds(30),
                "Le motif contient un session_id interdit");

        assertThatThrownBy(() -> integrityGuard.verify(
                rejected.content(),
                rejected.canonicalEventId(),
                rejected.exportId()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J7ExportError.SENSITIVE_CONTENT));
    }

    @Test
    void integrityGuardRejectsCredentialHeaderAndUrlMarkersInsideJsonValues() {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        for (String reason : List.of(
                "Authorization: Bearer opaque-value",
                "Cookie: sid=opaque-value",
                "cookies: sid=opaque-value",
                "token opaque-value",
                "tokens: opaque-value",
                "api key: opaque-value",
                "client secret: opaque-value",
                "session id: opaque-value",
                "request URI sofascore://private",
                "request URL https://provider.invalid/private")) {
            J7AssembledEnvelope rejected = assembler.decide(
                    candidate.content(),
                    J7ExportStatus.REJECTED,
                    GENERATED_AT.plusSeconds(30),
                    reason);
            assertThatThrownBy(() -> integrityGuard.verify(
                    rejected.content(),
                    rejected.canonicalEventId(),
                    rejected.exportId()))
                    .isInstanceOfSatisfying(J7ExportException.class, exception ->
                            assertThat(exception.error())
                                    .isEqualTo(J7ExportError.SENSITIVE_CONTENT));
        }
    }

    @Test
    void integrityGuardRejectsCrossFieldProvenanceCompletenessAndWarningMutations()
            throws Exception {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        assertSemanticMutationRejected(candidate, root -> {
            ObjectNode source = (ObjectNode) root.get("manifest").get("sources").get(0);
            source.put("sourceReference", "another-fixture");
            refreshSourceSetHash(root);
        }, J7ExportError.INCOHERENT_SOURCE);
        assertSemanticMutationRejected(candidate, root -> {
            ObjectNode statistics = (ObjectNode) root.get("data").get("statistics");
            statistics.put("availability", "MISSING");
            statistics.putNull("completeness");
            statistics.putNull("metrics");
            refreshDataHash(root);
        }, J7ExportError.INCOHERENT_SOURCE);
        assertSemanticMutationRejected(candidate, root -> {
            ((tools.jackson.databind.node.ArrayNode) root.get("manifest").get("warnings"))
                    .remove(1);
        }, J7ExportError.INCOHERENT_SOURCE);
        assertSemanticMutationRejected(candidate, root -> {
            ObjectNode sourceCompleteness = (ObjectNode) root.get("manifest")
                    .get("sources").get(4).get("completeness");
            ObjectNode dataCompleteness = (ObjectNode) root.get("data")
                    .get("lineups").get("completeness");
            sourceCompleteness.put("scorePercent", 74);
            dataCompleteness.put("scorePercent", 74);
            refreshSourceSetHash(root);
            refreshDataHash(root);
        }, J7ExportError.INCOHERENT_SOURCE);
        assertSemanticMutationRejected(candidate, root -> {
            ((ObjectNode) root.get("data").get("identity"))
                    .put("providerEventId", 16691019L);
            refreshDataHash(root);
        }, J7ExportError.IDENTITY_MISMATCH);
    }

    @Test
    void integrityGuardRejectsContentLargerThanFiveMebibytes() {
        byte[] oversized = new byte[Math.toIntExact(J7ExportContract.MAXIMUM_BYTES + 1)];

        assertThatThrownBy(() -> integrityGuard.verify(
                oversized,
                UUID.randomUUID(),
                UUID.randomUUID()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TOO_LARGE));
    }

    @Test
    void candidateValidatedAndRejectedEnvelopesMatchTheVersionedSchema() {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());
        J7AssembledEnvelope validated = assembler.decide(
                candidate.content(),
                J7ExportStatus.HUMAN_VALIDATED,
                GENERATED_AT.plusSeconds(30),
                null);
        J7AssembledEnvelope rejected = assembler.decide(
                candidate.content(),
                J7ExportStatus.REJECTED,
                GENERATED_AT.plusSeconds(60),
                "Rejet humain contrôlé");

        J7JsonSchemaValidator validator = new J7JsonSchemaValidator();
        assertThat(validator.parseAndValidate(candidate.content())).isNotNull();
        assertThat(validator.parseAndValidate(validated.content())).isNotNull();
        assertThat(validator.parseAndValidate(rejected.content())).isNotNull();
    }

    @Test
    void mapsEveryNormalizedFamilyAndKeepsCanonicalOrder() throws Exception {
        J7AssembledEnvelope assembled = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        JsonNode root = JSON_MAPPER.readTree(assembled.content());
        JsonNode sources = root.get("manifest").get("sources");
        assertThat(sources.valueStream()
                .map(value -> value.get("component").stringValue())
                .toList())
                .containsExactly(
                        "EVENT_STATE",
                        "EVENT_DETAILS",
                        "EVENT_STATISTICS",
                        "EVENT_INCIDENTS",
                        "EVENT_LINEUPS");
        assertThat(sources.valueStream()
                .map(value -> value.get("rawPayloadState").stringValue())
                .toList())
                .containsOnly("NOT_APPLICABLE");

        JsonNode data = root.get("data");
        assertThat(data.get("identity").get("providerEventId").longValue()).isEqualTo(16691018L);
        assertThat(data.get("eventDetails").get("details").get("venue").get("city")
                .stringValue()).isEqualTo("Paris");
        assertThat(data.get("statistics").get("metrics").get(1).get("awayValue").isNull())
                .isTrue();
        JsonNode exportedIncidents = data.get("incidents").get("incidents");
        assertThat(exportedIncidents).hasSize(2);
        assertThat(exportedIncidents.get(0).properties()).hasSize(26);
        assertThat(exportedIncidents.get(0).get("playerInName").stringValue())
                .isEqualTo("Incoming Player");
        assertThat(exportedIncidents.get(1).get("minute").isNull()).isTrue();
        assertThat(data.get("lineups").get("lineups").get("home").get("side").stringValue())
                .isEqualTo("HOME");
        assertThat(data.get("lineups").get("lineups").get("away").get("side").stringValue())
                .isEqualTo("AWAY");

        assertThat(root.get("manifest").get("warnings").valueStream()
                .map(value -> value.get("code").stringValue())
                .toList())
                .containsExactly(
                        "PARTIAL_COMPLETENESS",
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_SOURCE");
        assertThat(assembled.dataSha256())
                .isEqualTo(Sha256.hex(JSON_MAPPER.writeValueAsBytes(data)));
        assertThat(assembled.sourceSetSha256())
                .isEqualTo(Sha256.hex(JSON_MAPPER.writeValueAsBytes(sources)));
        assertThat(assembled.content()[assembled.content().length - 1]).isEqualTo((byte) '\n');
    }

    @Test
    void distinguishesMissingUnavailableAndEmptyValidFamiliesWithMixedProvenance()
            throws Exception {
        J7CurrentEventSelection complete = completeSyntheticSelection();
        CanonicalEventIdentity identity = complete.eventState().identity();
        long providerEventId = identity.providerEventId();
        EventSourceTrace unavailableSource = EventSourceTrace.providerSnapshot(
                77L,
                "9".repeat(64),
                "event-statistics-unavailable-v1",
                RECORDED_AT);
        J5EventDataObservationView unavailableStatistics = j5View(
                21L,
                identity,
                new EventStatistics(providerEventId, List.of()),
                unavailableSource,
                J5CompletenessReport.unavailable());
        J5EventDataObservationView emptyIncidents = j5View(
                22L,
                identity,
                new EventIncidents(providerEventId, List.of()),
                synthetic("j7-empty-incidents", "8", "event-incidents-v1"),
                J5CompletenessReport.emptyValid());
        J7CurrentEventSelection selection = new J7CurrentEventSelection(
                complete.eventState(),
                complete.eventDetails(),
                new J5EventDataBundle(
                        Optional.of(unavailableStatistics),
                        Optional.of(emptyIncidents),
                        Optional.empty()),
                Map.of(77L, new J6SnapshotTrace(
                        77L,
                        "9".repeat(64),
                        1,
                        0,
                        J6SnapshotOccurrenceOutcome.BASELINE,
                        Optional.of(RECORDED_AT),
                        J6RawPayloadState.PAYLOAD_PURGED)));

        J7AssembledEnvelope assembled = assembler.assembleCandidate(
                UUID.fromString("638ed708-51b7-49ee-a32a-a74a9351769a"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                selection);
        JsonNode root = JSON_MAPPER.readTree(assembled.content());
        JsonNode data = root.get("data");

        assertThat(data.get("statistics").get("availability").stringValue())
                .isEqualTo("UNAVAILABLE");
        assertThat(data.get("statistics").get("metrics").isNull()).isTrue();
        assertThat(data.get("statistics").get("completeness").get("status").stringValue())
                .isEqualTo("UNAVAILABLE");
        assertThat(data.get("incidents").get("availability").stringValue())
                .isEqualTo("EMPTY_VALID");
        assertThat(data.get("incidents").get("incidents")).isEmpty();
        assertThat(data.get("lineups").get("availability").stringValue())
                .isEqualTo("MISSING");
        assertThat(data.get("lineups").get("completeness").isNull()).isTrue();
        assertThat(data.get("lineups").get("lineups").isNull()).isTrue();
        assertThat(root.get("manifest").get("warnings").valueStream()
                .map(value -> value.get("code").stringValue())
                .toList())
                .contains(
                        "MISSING_COMPONENT",
                        "UNAVAILABLE_COMPONENT",
                        "SYNTHETIC_SOURCE",
                        "MIXED_SOURCE_KINDS",
                        "RAW_PAYLOAD_PURGED");
        assertThat(integrityGuard.verify(
                assembled.content(),
                assembled.canonicalEventId(),
                assembled.exportId()).status())
                .isEqualTo(J7ExportStatus.COHERENCE_CHECKED);
    }

    @Test
    void mapsEveryProviderRawPayloadStateAndItsRequiredWarning() throws Exception {
        for (J6RawPayloadState rawState : J6RawPayloadState.values()) {
            EventSourceTrace providerSource = EventSourceTrace.providerSnapshot(
                    77L,
                    "9".repeat(64),
                    "scheduled-events-v1",
                    RECORDED_AT);
            ScheduledEvent event = event();
            CanonicalEventObservation normalized = CanonicalEventObservation.from(
                    event,
                    providerSource);
            J7CurrentEventSelection selection = new J7CurrentEventSelection(
                    eventView(1L, event, providerSource, normalized),
                    Optional.empty(),
                    J5EventDataBundle.empty(),
                    Map.of(77L, new J6SnapshotTrace(
                            77L,
                            "9".repeat(64),
                            1,
                            0,
                            J6SnapshotOccurrenceOutcome.BASELINE,
                            Optional.of(RECORDED_AT),
                            rawState)));

            J7AssembledEnvelope assembled = assembler.assembleCandidate(
                    UUID.randomUUID(),
                    GENERATED_AT,
                    "0.1.0-SNAPSHOT",
                    selection);
            JsonNode root = JSON_MAPPER.readTree(assembled.content());
            assertThat(root.get("manifest").get("sources").get(0)
                    .get("rawPayloadState").stringValue()).isEqualTo(rawState.name());
            List<String> warningCodes = root.get("manifest").get("warnings").valueStream()
                    .map(value -> value.get("code").stringValue())
                    .toList();
            if (rawState == J6RawPayloadState.PAYLOAD_PURGED) {
                assertThat(warningCodes).contains("RAW_PAYLOAD_PURGED");
            }
            else if (rawState == J6RawPayloadState.LEGACY_ABSENT) {
                assertThat(warningCodes).contains("RAW_PAYLOAD_LEGACY_ABSENT");
            }
            else {
                assertThat(warningCodes)
                        .doesNotContain("RAW_PAYLOAD_PURGED", "RAW_PAYLOAD_LEGACY_ABSENT");
            }
            assertThat(integrityGuard.verify(
                    assembled.content(),
                    assembled.canonicalEventId(),
                    assembled.exportId()).status())
                    .isEqualTo(J7ExportStatus.COHERENCE_CHECKED);
        }
    }

    @Test
    void rejectsPresentFamiliesThatOnlyContainAnEmptyPlaceholder() {
        J7CurrentEventSelection complete = completeSyntheticSelection();
        CanonicalEventIdentity identity = complete.eventState().identity();
        long providerEventId = identity.providerEventId();
        J5EventDataObservationView emptyPresentStatistics = j5View(
                31L,
                identity,
                new EventStatistics(providerEventId, List.of()),
                synthetic("j7-empty-present-statistics", "6", "event-statistics-v1"),
                J5CompletenessReport.measured(1, 1, List.of()));
        EventLineups emptyLineups = new EventLineups(
                providerEventId,
                false,
                new TeamLineup(LineupSide.HOME, Optional.empty(), List.of()),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of()));
        J5EventDataObservationView emptyPresentLineups = j5View(
                32L,
                identity,
                emptyLineups,
                synthetic("j7-empty-present-lineups", "7", "event-lineups-v1"),
                J5CompletenessReport.measured(1, 1, List.of()));

        for (J5EventDataBundle bundle : List.of(
                new J5EventDataBundle(
                        Optional.of(emptyPresentStatistics),
                        complete.eventData().incidents(),
                        complete.eventData().lineups()),
                new J5EventDataBundle(
                        complete.eventData().statistics(),
                        complete.eventData().incidents(),
                        Optional.of(emptyPresentLineups)))) {
            J7AssembledEnvelope assembled = assembler.assembleCandidate(
                    UUID.randomUUID(),
                    GENERATED_AT,
                    "0.1.0-SNAPSHOT",
                    new J7CurrentEventSelection(
                            complete.eventState(),
                            complete.eventDetails(),
                            bundle,
                            complete.snapshotTraces()));
            assertThatThrownBy(() -> integrityGuard.verify(
                    assembled.content(),
                    assembled.canonicalEventId(),
                    assembled.exportId()))
                    .isInstanceOfSatisfying(J7ExportException.class, exception ->
                            assertThat(exception.error())
                                    .isIn(
                                            J7ExportError.INVALID_SCHEMA,
                                            J7ExportError.INCOHERENT_SOURCE));
        }
    }

    @Test
    void dataAndSourceSetHashesAreIndependentFromManifestIdentityAndTime() {
        J7CurrentEventSelection selection = completeSyntheticSelection();
        J7AssembledEnvelope first = assembler.assembleCandidate(
                UUID.fromString("03818d85-e7a4-4859-b742-b5d66703946e"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                selection);
        J7AssembledEnvelope second = assembler.assembleCandidate(
                UUID.fromString("cf89fa1f-d2c8-40bd-b152-588d0079bf23"),
                GENERATED_AT.plusSeconds(30),
                "0.1.0-SNAPSHOT",
                selection);

        assertThat(second.dataSha256()).isEqualTo(first.dataSha256());
        assertThat(second.sourceSetSha256()).isEqualTo(first.sourceSetSha256());
        assertThat(second.content()).isNotEqualTo(first.content());
    }

    @Test
    void terminalDecisionChangesOnlyValidationAndPreservesDataHash() throws Exception {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.fromString("c124eaac-0a66-48cd-b09d-b86f8a6cb278"),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        J7AssembledEnvelope validated = assembler.decide(
                candidate.content(),
                J7ExportStatus.HUMAN_VALIDATED,
                GENERATED_AT.plusSeconds(60),
                null);
        J7AssembledEnvelope rejected = assembler.decide(
                candidate.content(),
                J7ExportStatus.REJECTED,
                GENERATED_AT.plusSeconds(90),
                "Données inspectées mais volontairement refusées");

        JsonNode candidateRoot = JSON_MAPPER.readTree(candidate.content());
        JsonNode validatedRoot = JSON_MAPPER.readTree(validated.content());
        JsonNode rejectedRoot = JSON_MAPPER.readTree(rejected.content());
        assertThat(validatedRoot.get("data")).isEqualTo(candidateRoot.get("data"));
        assertThat(rejectedRoot.get("data")).isEqualTo(candidateRoot.get("data"));
        assertThat(validatedRoot.get("manifest").get("sources"))
                .isEqualTo(candidateRoot.get("manifest").get("sources"));
        assertThat(validatedRoot.get("manifest").get("warnings"))
                .isEqualTo(candidateRoot.get("manifest").get("warnings"));
        assertThat(validated.dataSha256()).isEqualTo(candidate.dataSha256());
        assertThat(rejected.dataSha256()).isEqualTo(candidate.dataSha256());
        assertThat(validated.sourceSetSha256()).isEqualTo(candidate.sourceSetSha256());
        assertThat(rejectedRoot.get("manifest").get("validation").get("rejectionReason")
                .stringValue()).isEqualTo("Données inspectées mais volontairement refusées");
    }

    @Test
    void refusesAStoredNormalizedHashThatCannotBeRecomputed() {
        J7CurrentEventSelection valid = completeSyntheticSelection();
        CanonicalEventObservationView state = valid.eventState();
        CanonicalEventObservationView corrupted = new CanonicalEventObservationView(
                state.observationId(),
                state.identity(),
                state.startsAt(),
                state.homeTeam(),
                state.awayTeam(),
                state.status(),
                state.tournament(),
                state.source(),
                "0".repeat(64),
                state.observationCount());
        J7CurrentEventSelection selection = new J7CurrentEventSelection(
                corrupted,
                valid.eventDetails(),
                valid.eventData(),
                valid.snapshotTraces());

        assertThatThrownBy(() -> assembler.assembleCandidate(
                UUID.randomUUID(),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                selection))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.INVALID_HASH));
    }

    @Test
    void refusesProviderProvenanceWithoutItsJ6SnapshotTrace() {
        EventSourceTrace providerSource = EventSourceTrace.providerSnapshot(
                77L,
                "9".repeat(64),
                "scheduled-events-v1",
                RECORDED_AT);
        ScheduledEvent event = event();
        CanonicalEventObservation normalized = CanonicalEventObservation.from(
                event,
                providerSource);
        CanonicalEventObservationView state = eventView(1L, event, providerSource, normalized);
        J7CurrentEventSelection selection = new J7CurrentEventSelection(
                state,
                Optional.empty(),
                J5EventDataBundle.empty(),
                Map.of());

        assertThatThrownBy(() -> assembler.assembleCandidate(
                UUID.randomUUID(),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                selection))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J7ExportError.INCOHERENT_SOURCE));
    }

    @Test
    void refusesInvalidTerminalReasonAndASecondDecision() {
        J7AssembledEnvelope candidate = assembler.assembleCandidate(
                UUID.randomUUID(),
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                completeSyntheticSelection());

        assertThatThrownBy(() -> assembler.decide(
                candidate.content(),
                J7ExportStatus.REJECTED,
                GENERATED_AT.plusSeconds(1),
                "   "))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J7ExportError.INVALID_REJECTION_REASON));

        J7AssembledEnvelope terminal = assembler.decide(
                candidate.content(),
                J7ExportStatus.HUMAN_VALIDATED,
                GENERATED_AT.plusSeconds(1),
                null);
        assertThatThrownBy(() -> assembler.decide(
                terminal.content(),
                J7ExportStatus.REJECTED,
                GENERATED_AT.plusSeconds(2),
                "Refus tardif"))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J7ExportError.INVALID_TRANSITION));
    }

    private static J7CurrentEventSelection completeSyntheticSelection() {
        ScheduledEvent event = event();
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(event.providerEventId());

        EventSourceTrace eventSource = synthetic(
                "j7-state",
                "1",
                "scheduled-events-v1");
        CanonicalEventObservation eventObservation = CanonicalEventObservation.from(
                event,
                eventSource);
        CanonicalEventObservationView eventView = eventView(
                11L,
                event,
                eventSource,
                eventObservation);

        EventDetails details = new EventDetails(
                event.providerEventId(),
                event.startsAt(),
                event.homeTeam(),
                event.awayTeam(),
                event.status(),
                event.tournament(),
                Optional.of(new EventVenue(300L, "Parc local", Optional.of("Paris"))),
                Optional.of(new EventSeason(2026L, "2026/2027")),
                Optional.of("7"));
        EventSourceTrace detailsSource = synthetic(
                "j7-details",
                "2",
                "event-details-v1");
        EventDetailObservation detailObservation = EventDetailObservation.from(
                identity,
                details,
                detailsSource);
        EventDetailObservationView detailView = new EventDetailObservationView(
                12L,
                identity,
                details,
                detailsSource,
                detailObservation.normalizedSha256());

        EventStatistics statistics = new EventStatistics(
                event.providerEventId(),
                List.of(
                        new EventStatisticMetric(
                                "ALL",
                                "Match overview",
                                "possession",
                                "Possession",
                                Optional.of("55%"),
                                Optional.of("45%")),
                        new EventStatisticMetric(
                                "2ND",
                                "Attack",
                                "dangerousAttacks",
                                "Dangerous attacks",
                                Optional.of("12"),
                                Optional.empty())));
        J5EventDataObservationView statisticsView = j5View(
                13L,
                identity,
                statistics,
                synthetic("j7-statistics", "3", "event-statistics-v1"),
                J5CompletenessReport.measured(2, 2, List.of()));

        EventIncidents incidents = new EventIncidents(
                event.providerEventId(),
                List.of(richIncident(), unminutedShootoutIncident()));
        J5EventDataObservationView incidentsView = j5View(
                14L,
                identity,
                incidents,
                synthetic("j7-incidents", "4", "event-incidents-v1"),
                J5CompletenessReport.measured(2, 2, List.of()));

        EventLineups lineups = new EventLineups(
                event.providerEventId(),
                true,
                new TeamLineup(
                        LineupSide.HOME,
                        Optional.of("4-3-3"),
                        List.of(
                                new EventLineupPlayer(
                                        401L,
                                        "Home Starter",
                                        Optional.of(9),
                                        Optional.of("F"),
                                        true),
                                new EventLineupPlayer(
                                        402L,
                                        "Home Substitute",
                                        Optional.empty(),
                                        Optional.empty(),
                                        false))),
                new TeamLineup(
                        LineupSide.AWAY,
                        Optional.empty(),
                        List.of(new EventLineupPlayer(
                                501L,
                                "Away Starter",
                                Optional.of(1),
                                Optional.of("G"),
                                true))));
        J5EventDataObservationView lineupsView = j5View(
                15L,
                identity,
                lineups,
                synthetic("j7-lineups", "5", "event-lineups-v1"),
                J5CompletenessReport.measured(3, 4, List.of("$.away.formation")));

        return new J7CurrentEventSelection(
                eventView,
                Optional.of(detailView),
                new J5EventDataBundle(
                        Optional.of(statisticsView),
                        Optional.of(incidentsView),
                        Optional.of(lineupsView)),
                Map.of());
    }

    private static J7CurrentEventSelection awardedSyntheticSelection() {
        J7CurrentEventSelection complete = completeSyntheticSelection();
        var previous = complete.eventDetails().orElseThrow();
        EventDetails details = previous.details();
        EventDetails awarded = new EventDetails(
                details.providerEventId(), details.startsAt(), details.homeTeam(), details.awayTeam(),
                new ScheduledEventStatus("finished", Optional.of("Ended")),
                details.tournament(), details.venue(), details.season(), details.round(),
                Optional.of(true), Optional.of(3), Optional.of(0));
        EventSourceTrace provenance = synthetic("j7-awarded-details", "6", "event-details-v3");
        var observation = EventDetailObservation.from(previous.identity(), awarded, provenance);
        var detailView = new EventDetailObservationView(previous.observationId(), previous.identity(),
                awarded, provenance, observation.normalizedSha256());
        var canonical = CanonicalEventObservation.from(awarded.asScheduledEvent(), provenance);
        var stateView = eventView(complete.eventState().observationId(), awarded.asScheduledEvent(),
                provenance, canonical);
        return new J7CurrentEventSelection(stateView, Optional.of(detailView),
                complete.eventData(), complete.snapshotTraces());
    }

    private static ScheduledEvent event() {
        return new ScheduledEvent(
                16691018L,
                Instant.parse("2026-08-20T18:45:00Z"),
                new ScheduledTeam(100L, "Home FC"),
                new ScheduledTeam(200L, "Away FC"),
                new ScheduledEventStatus("scheduled", Optional.of("Not started")),
                Optional.of(new ScheduledTournament(250L, "Local League")));
    }

    private static CanonicalEventObservationView eventView(
            long observationId,
            ScheduledEvent event,
            EventSourceTrace source,
            CanonicalEventObservation observation) {
        return new CanonicalEventObservationView(
                observationId,
                observation.identity(),
                event.startsAt(),
                event.homeTeam(),
                event.awayTeam(),
                event.status(),
                event.tournament(),
                source,
                observation.normalizedSha256(),
                1L);
    }

    private static J5EventDataObservationView j5View(
            long observationId,
            CanonicalEventIdentity identity,
            com.bettingproject.sofascorelocal.domain.eventdata.J5EventData data,
            EventSourceTrace source,
            J5CompletenessReport completeness) {
        J5EventDataObservation observation = J5EventDataObservation.from(
                identity,
                data,
                source,
                completeness);
        return new J5EventDataObservationView(
                observationId,
                identity,
                data,
                source,
                completeness,
                observation.normalizedSha256());
    }

    private static EventSourceTrace synthetic(
            String fixtureId,
            String hashDigit,
            String parserVersion) {
        return new EventSourceTrace(
                com.bettingproject.sofascorelocal.domain.event.EventSourceKind.SYNTHETIC_FIXTURE,
                OptionalLong.empty(),
                Optional.of(fixtureId),
                hashDigit.repeat(64),
                parserVersion,
                RECORDED_AT);
    }

    private static EventIncident richIncident() {
        return new EventIncident(
                0,
                "goal",
                Optional.of(12),
                Optional.of(1),
                Optional.of(true),
                Optional.of(100L),
                Optional.of(601L),
                Optional.of("Scorer"),
                Optional.of(602L),
                Optional.of("Incoming Player"),
                Optional.of(603L),
                Optional.of("Outgoing Player"),
                Optional.of(1),
                Optional.of(0),
                Optional.of("regular"),
                Optional.of("openPlay"),
                Optional.of("1ST"),
                Optional.of(false),
                Optional.of(604L),
                Optional.of("Assist Player"),
                Optional.of("regular"),
                Optional.of(3),
                Optional.of(true),
                Optional.of(false),
                Optional.of("Goal confirmed"),
                Optional.empty());
    }

    private static EventIncident unminutedShootoutIncident() {
        return new EventIncident(
                1,
                "penaltyShootout",
                Optional.empty(),
                Optional.empty(),
                Optional.of(false),
                Optional.of(200L),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(4),
                Optional.of(5),
                Optional.empty(),
                Optional.empty(),
                Optional.of("PEN"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(9));
    }

    private void assertSchemaMutationRejected(
            J7AssembledEnvelope candidate,
            Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root = (ObjectNode) JSON_MAPPER.readTree(candidate.content());
        mutation.accept(root);
        byte[] content = JSON_MAPPER.writeValueAsBytes(root);

        assertThatThrownBy(() -> integrityGuard.verify(
                content,
                candidate.canonicalEventId(),
                candidate.exportId()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isIn(
                                J7ExportError.INVALID_SCHEMA,
                                J7ExportError.INVALID_HASH,
                                J7ExportError.IDENTITY_MISMATCH));
    }

    private void assertSemanticMutationRejected(
            J7AssembledEnvelope candidate,
            Consumer<ObjectNode> mutation,
            J7ExportError expectedError) throws Exception {
        ObjectNode root = (ObjectNode) JSON_MAPPER.readTree(candidate.content());
        mutation.accept(root);
        byte[] content = JSON_MAPPER.writeValueAsBytes(root);

        assertThatThrownBy(() -> integrityGuard.verify(
                content,
                candidate.canonicalEventId(),
                candidate.exportId()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(expectedError));
    }

    private static void refreshDataHash(ObjectNode root) {
        try {
            ((ObjectNode) root.get("manifest")).put(
                    "dataSha256",
                    Sha256.hex(JSON_MAPPER.writeValueAsBytes(root.get("data"))));
        }
        catch (tools.jackson.core.JacksonException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void refreshSourceSetHash(ObjectNode root) {
        try {
            ((ObjectNode) root.get("manifest")).put(
                    "sourceSetSha256",
                    Sha256.hex(JSON_MAPPER.writeValueAsBytes(
                            root.get("manifest").get("sources"))));
        }
        catch (tools.jackson.core.JacksonException exception) {
            throw new AssertionError(exception);
        }
    }
}
