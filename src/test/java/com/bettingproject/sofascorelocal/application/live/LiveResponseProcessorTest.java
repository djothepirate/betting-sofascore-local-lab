package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.adapter.sofascore.live.LivePayloadNormalizer;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventObservationPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataPersistenceResult;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveResponseProcessorTest {
    private static final CanonicalEventIdentity IDENTITY = CanonicalEventIdentity.sofascore(17000001);
    private static final Instant RECEIVED = Instant.parse("2026-09-07T16:00:00Z");
    private final CanonicalEventStore events = mock(CanonicalEventStore.class);
    private final EventDetailsStore details = mock(EventDetailsStore.class);
    private final J5EventDataStore data = mock(J5EventDataStore.class);
    private final RawManualCallSnapshotStore rawStore = mock(RawManualCallSnapshotStore.class);
    private final LiveResponseProcessor processor = new LiveResponseProcessor(events, details, data, rawStore);

    @Test
    void normalizationIsPureAndKeepsUnknownSportingStatusForTheStateMachine() {
        var result = process(SofascoreEndpointType.EVENT_DETAILS, detailsBody("suspended"), 200);
        assertThat(result.outcome()).isEqualTo(LiveProcessedResponse.Outcome.PARSED);
        assertThat(result.scope()).isEqualTo(LiveProcessedResponse.FailureScope.NONE);
        assertThat(result.sportStatus()).contains("suspended");
        verifyNoInteractions(events, details, data, rawStore);
    }

    @Test
    void isolatesOnlyAClassifiedSchemaAndKeepsIdentityMismatchGlobal() {
        var schema = process(SofascoreEndpointType.EVENT_DETAILS,
                "{\"event\":{\"id\":17000001,\"status\":null}}", 200);
        assertThat(schema.outcome()).isEqualTo(LiveProcessedResponse.Outcome.SCHEMA_INCOMPATIBLE);
        assertThat(schema.stopEvent()).isTrue();
        var identityAndSchema = process(SofascoreEndpointType.EVENT_DETAILS,
                "{\"event\":{\"id\":17000002,\"status\":null}}", 200);
        assertThat(identityAndSchema.code()).isEqualTo("EVENT_ID_MISMATCH");
        assertThat(identityAndSchema.stopCampaign()).isTrue();
        assertThat(identityAndSchema.eventDetails()).isEmpty();
        verifyNoInteractions(events, details, data, rawStore);
    }

    @Test
    void doesNotParseA404EvenWhenTheBodyIsNotJson() {
        LivePayloadNormalizer normalizer = mock(LivePayloadNormalizer.class);
        var service = new LiveResponseProcessor(events, details, data, rawStore, normalizer);
        var response = response("not a JSON document", 404, "text/plain", RECEIVED);
        var unavailable = service.process(IDENTITY, SofascoreEndpointType.EVENT_INCIDENTS,
                response, raw(response, RawSnapshotPersistenceOutcome.INSERTED, 1));
        assertThat(unavailable.outcome()).isEqualTo(LiveProcessedResponse.Outcome.ENDPOINT_UNAVAILABLE);
        assertThat(unavailable.scope()).isEqualTo(LiveProcessedResponse.FailureScope.NONE);
        assertThat(unavailable.completeness()).hasValueSatisfying(value ->
                assertThat(value.status().name()).isEqualTo("UNAVAILABLE"));
        assertThat(unavailable.parserVersion()).isEqualTo("event-incidents-unavailable-v1");
        var j4Unavailable = service.process(IDENTITY, SofascoreEndpointType.EVENT_DETAILS,
                response, raw(response, RawSnapshotPersistenceOutcome.INSERTED, 2));
        assertThat(j4Unavailable.stopEvent()).isTrue();
        assertThat(j4Unavailable.sportStatus()).isEmpty();
        verifyNoInteractions(normalizer, events, details, data, rawStore);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void keepsEveryOtherHttpFailureGlobal(int status) {
        var result = process(SofascoreEndpointType.EVENT_INCIDENTS, "{}", status);
        assertThat(result.stopCampaign()).isTrue();
        assertThat(result.code()).isEqualTo("HTTP_" + status);
    }

    @Test
    void treatsContentAndInternalParserFailuresAsGlobalWithoutPublishingExceptionText() {
        var malformed = process(SofascoreEndpointType.EVENT_INCIDENTS, "{no-json", 200);
        assertThat(malformed.stopCampaign()).isTrue();
        assertThat(malformed.code()).isEqualTo("INVALID_JSON");
        var html = response("<html>unexpected</html>", 200, "text/html", RECEIVED);
        assertThat(processor.process(IDENTITY, SofascoreEndpointType.EVENT_INCIDENTS,
                html, raw(html, RawSnapshotPersistenceOutcome.INSERTED, 2)).code())
                .isEqualTo("UNEXPECTED_CONTENT");
        LivePayloadNormalizer normalizer = mock(LivePayloadNormalizer.class);
        when(normalizer.normalize(anyLong(), any(), anyLong(), anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("untrusted exception details"));
        var service = new LiveResponseProcessor(events, details, data, rawStore, normalizer);
        var response = response("{\"incidents\":[]}", 200, "application/json", RECEIVED);
        var failure = service.process(IDENTITY, SofascoreEndpointType.EVENT_INCIDENTS,
                response, raw(response, RawSnapshotPersistenceOutcome.INSERTED, 3));
        assertThat(failure.stopCampaign()).isTrue();
        assertThat(failure.code()).isEqualTo("PARSER_FAILURE");
        assertThat(failure.projectionJson()).isEqualTo("{}");
        verifyNoInteractions(events, details, data, rawStore);
    }

    @Test
    void mismatchedRawEvidenceCannotReachTheNormalizerOrRewriteAnotherSnapshot() {
        LivePayloadNormalizer normalizer = mock(LivePayloadNormalizer.class);
        var service = new LiveResponseProcessor(events, details, data, rawStore, normalizer);
        var response = response("{\"incidents\":[]}", 200, "application/json", RECEIVED);
        var wrongRaw = raw(response("{}", 200, "application/json", RECEIVED),
                RawSnapshotPersistenceOutcome.INSERTED, 1);
        var failure = service.process(IDENTITY, SofascoreEndpointType.EVENT_INCIDENTS, response, wrongRaw);
        assertThat(failure.code()).isEqualTo("RAW_RESPONSE_MISMATCH");
        assertThat(failure.stopCampaign()).isTrue();
        assertThat(service.persistProcessed(failure).j5ObservationId()).isNull();
        verifyNoInteractions(normalizer, events, details, data, rawStore);
    }

    @Test
    void publishesBothJ4ObservationsAndReturnsExactReferencesWithoutTouchingAManualControl() {
        var result = process(SofascoreEndpointType.EVENT_DETAILS, detailsBody("inprogress"), 200);
        when(events.save(any())).thenReturn(new EventObservationPersistenceResult(11, IDENTITY.value(), 1, true));
        when(details.save(any())).thenReturn(new EventDetailPersistenceResult(12, IDENTITY.value(), true));
        var saved = processor.persistProcessed(result);
        assertThat(saved.canonicalObservationId()).isEqualTo(11);
        assertThat(saved.detailObservationId()).isEqualTo(12);
        assertThat(saved.j5ObservationId()).isNull();
        assertThat(saved.normalizedSha256()).matches("[0-9a-f]{64}");
        ArgumentCaptor<CanonicalEventObservation> captured = ArgumentCaptor.forClass(CanonicalEventObservation.class);
        verify(events).save(captured.capture());
        assertThat(captured.getValue().source().receivedAt()).isEqualTo(RECEIVED);
        assertThat(captured.getValue().source().parserVersion()).isEqualTo("event-details-v4");
        verify(rawStore).classify(1, RawSnapshotSchemaStatus.PARSED, null);
        verifyNoInteractions(data);
    }

    @Test
    void doesNotReclassifyDeduplicatedRawEvidenceAndKeepsTheNewReception() {
        var response = response("{\"incidents\":[]}", 200, "application/json", RECEIVED.plusSeconds(180));
        var result = processor.process(IDENTITY, SofascoreEndpointType.EVENT_INCIDENTS,
                response, raw(response, RawSnapshotPersistenceOutcome.DEDUPLICATED, 4));
        when(data.save(any())).thenReturn(new J5EventDataPersistenceResult(
                20, IDENTITY.value(), SofascoreEndpointType.EVENT_INCIDENTS, false));
        var saved = processor.persistProcessed(result);
        assertThat(saved.j5ObservationId()).isEqualTo(20);
        ArgumentCaptor<J5EventDataObservation> captured = ArgumentCaptor.forClass(J5EventDataObservation.class);
        verify(data).save(captured.capture());
        assertThat(captured.getValue().source().receivedAt()).isEqualTo(RECEIVED.plusSeconds(180));
        assertThat(result.raw().occurrenceId()).hasValue(4);
        verifyNoInteractions(rawStore, events, details);
    }

    @Test
    void publicationFailureEscapesSoTheEnclosingLedgerTransactionCanRollback() {
        var schema = process(SofascoreEndpointType.EVENT_INCIDENTS, "{\"incidents\":null}", 200);
        doThrow(new IllegalStateException("database unavailable"))
                .when(rawStore).classify(1, RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE, "SCHEMA_INCOMPATIBLE");
        assertThatThrownBy(() -> processor.persistProcessed(schema)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(events, details, data);
    }

    private LiveProcessedResponse process(SofascoreEndpointType endpoint, String body, int status) {
        var response = response(body, status, "application/json", RECEIVED);
        return processor.process(IDENTITY, endpoint, response,
                raw(response, RawSnapshotPersistenceOutcome.INSERTED, 1));
    }

    private static PlaywrightProviderResponse response(String body, int status, String type, Instant received) {
        return new PlaywrightProviderResponse(received.minusMillis(20), received, status, type,
                Duration.ofMillis(20), RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static RawSnapshotPersistenceResult raw(PlaywrightProviderResponse response,
                                                     RawSnapshotPersistenceOutcome outcome, long occurrence) {
        return new RawSnapshotPersistenceResult(1, outcome, response.payload().sha256(),
                response.payload().sizeBytes(), OptionalLong.of(occurrence));
    }

    private static String detailsBody(String status) {
        return """
                {"event":{"id":17000001,"startTimestamp":1788796800,
                "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                "status":{"type":"%s"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                """.formatted(status);
    }
}
