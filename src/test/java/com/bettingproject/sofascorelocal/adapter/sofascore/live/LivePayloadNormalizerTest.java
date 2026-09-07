package com.bettingproject.sofascorelocal.adapter.sofascore.live;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic contract evidence only; no provider access or compatibility claim. */
class LivePayloadNormalizerTest {
    private static final long EVENT_ID = 17000001;
    private static final Instant RECEIVED = Instant.parse("2026-09-07T16:00:00Z");
    private final LivePayloadNormalizer normalizer = new LivePayloadNormalizer();

    @Test
    void keepsMissingNullAndZeroScoresDistinctAndNeverComputesCurrentFromOtherFields() {
        var absent = details("");
        var nullScore = details(",\"homeScore\":null,\"awayScore\":{\"current\":null}");
        var zero = details(",\"homeScore\":{\"current\":0,\"period1\":2,\"penalties\":4},"
                + "\"awayScore\":{\"period1\":1,\"penalties\":3}");

        assertThat(absent.status()).isEqualTo(LiveNormalizedPayload.Status.PARSED);
        assertThat(json(absent).at("/homeScore/presence").stringValue()).isEqualTo("ABSENT");
        assertThat(json(nullScore).at("/homeScore/presence").stringValue()).isEqualTo("NULL");
        assertThat(json(nullScore).at("/awayScore/value/current/presence").stringValue()).isEqualTo("NULL");
        assertThat(json(zero).at("/homeScore/value/current/value").intValue()).isZero();
        assertThat(json(zero).at("/awayScore/value/current/presence").stringValue()).isEqualTo("ABSENT");
        assertThat(json(zero).at("/homeScore/value/period1/value").intValue()).isEqualTo(2);
        assertThat(json(zero).at("/homeScore/value/penalties/value").intValue()).isEqualTo(4);
        assertThat(zero.parserVersion()).isEqualTo("event-details-v2");
        assertThat(zero.projectionVersion()).isEqualTo("j4-live-score-v1");
    }

    @Test
    void rejectsAnExplicitMalformedScoreWithoutPublishingPartialDetails() {
        var result = details(",\"homeScore\":{\"current\":\"0\"}");
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.SCHEMA_INCOMPATIBLE);
        assertThat(result.code()).isEqualTo("LIVE_SCORE_VALUE_INCOMPATIBLE");
        assertThat(result.details()).isEmpty();
    }

    @Test
    void checksJ4IdentityBeforeAnUnrelatedSchemaError() {
        var result = normalize(SofascoreEndpointType.EVENT_DETAILS,
                "{\"event\":{\"id\":17000002,\"status\":null}}", 1, RECEIVED);
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.IDENTITY_MISMATCH);
        assertThat(result.code()).isEqualTo("EVENT_ID_MISMATCH");
        var missingId = normalize(SofascoreEndpointType.EVENT_DETAILS,
                "{\"event\":{\"status\":null}}", 1, RECEIVED);
        assertThat(missingId.status()).isEqualTo(LiveNormalizedPayload.Status.IDENTITY_MISMATCH);
        assertThat(missingId.code()).isEqualTo("IDENTITY_NOT_VERIFIABLE");
    }

    @Test
    void checksAnyExplicitJ5EventIdentityBeforeIsolatingItsSchema() {
        var result = normalize(SofascoreEndpointType.EVENT_INCIDENTS,
                "{\"eventId\":17000002,\"incidents\":null}", 1, RECEIVED);
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.IDENTITY_MISMATCH);
        var admissible = normalize(SofascoreEndpointType.EVENT_INCIDENTS,
                "{\"incidents\":null}", 1, RECEIVED);
        assertThat(admissible.status()).isEqualTo(LiveNormalizedPayload.Status.SCHEMA_INCOMPATIBLE);
    }

    @ParameterizedTest
    @CsvSource({"45,PERIOD_CHECK", "90,FINISH_CHECK", "105,PERIOD_CHECK", "120,FINISH_CHECK"})
    void mapsAnnouncedAddedTimeOnlyToAJ4Check(int minute, String kind) {
        var result = incidents(injury(minute, 3));
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.PARSED);
        assertThat(result.signals()).singleElement().satisfies(signal ->
                assertThat(signal.kind().name()).isEqualTo(kind));
        assertThat(result.details()).isEmpty();
    }

    @Test
    void keepsTheLiveExtraTimeBoundSeparateFromAFinishSignal() {
        var result = incidents("""
                {"incidentType":"period","text":"Extra time","isLive":true,
                 "time":120,"addedTime":999,"timeSeconds":7200,"periodTimeSeconds":900,
                 "homeScore":1,"awayScore":1}
                """);
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.PARSED);
        assertThat(result.signals()).isEmpty();
        assertThat(json(result).at("/phaseObservations/0/isLive/value").booleanValue()).isTrue();
        assertThat(json(result).at("/phaseObservations/0/addedTime/value").intValue()).isEqualTo(999);
        assertThat(json(result).at("/phaseObservations/0/time/value").intValue()).isEqualTo(120);
    }

    @Test
    void anAwardedInGamePenaltyUsesTheCurrentParserWithoutInventingAGoalOrAFinishSignal() {
        var result = incidents("""
                {"incidentType":"inGamePenalty","incidentClass":"awarded",
                 "time":83,"isHome":true,"confirmed":true}
                """);
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.PARSED);
        assertThat(result.parserVersion()).isEqualTo("event-incidents-v17");
        assertThat(result.signals()).isEmpty();
        assertThat(result.details()).isEmpty();
        assertThat(result.eventData()).hasValueSatisfying(data -> {
            var incidents = (com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents) data;
            assertThat(incidents.incidents()).singleElement().satisfies(incident -> {
                assertThat(incident.incidentType()).isEqualTo("inGamePenalty");
                assertThat(incident.incidentClass()).contains("awarded");
                assertThat(incident.homeScore()).isEmpty();
                assertThat(incident.awayScore()).isEmpty();
            });
        });
    }

    @Test
    void aProfessionalHandballCardUsesV17WithoutInventingASportingSignal() {
        var result = incidents("""
                {"incidentType":"card","incidentClass":"red","reason":"Professional handball",
                 "time":64,"isHome":false,"rescinded":false,"player":{"name":"Synthetic player"}}
                """);
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.PARSED);
        assertThat(result.parserVersion()).isEqualTo("event-incidents-v17");
        assertThat(result.signals()).isEmpty();
        assertThat(result.details()).isEmpty();
        assertThat(result.eventData()).hasValueSatisfying(data -> {
            var incidents = (com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents) data;
            assertThat(incidents.incidents().getFirst().reason()).contains("Professional handball");
            assertThat(incidents.incidents().getFirst().incidentClass()).contains("red");
        });
    }

    @Test
    void keepsSignalIdentityStableAcrossOrderChangesAndAnnouncedLengthRevisions() {
        var a = incidents(injury(45, 2) + "," + injury(90, 4));
        var b = incidents(injury(90, 6) + "," + injury(45, 2));
        assertThat(a.signals()).containsExactlyInAnyOrderElementsOf(b.signals());
        assertThat(json(a).at("/phaseObservations/1/revisionSha256").stringValue())
                .isNotEqualTo(json(b).at("/phaseObservations/0/revisionSha256").stringValue());
        assertThat(json(a).at("/phaseObservations/0/revisionSha256").stringValue())
                .isEqualTo(json(b).at("/phaseObservations/1/revisionSha256").stringValue());
    }

    @Test
    void rejectsAmbiguousJsonInsteadOfCallingItAnIsolableSchema() {
        var result = normalize(SofascoreEndpointType.EVENT_INCIDENTS,
                "{\"incidents\":[],\"incidents\":[1]}", 1, RECEIVED);
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.UNEXPECTED_CONTENT);
    }

    @Test
    void anEmptyIncidentArrayDoesNotInventASportingStateOrSignal() {
        var result = incidents("");
        assertThat(result.status()).isEqualTo(LiveNormalizedPayload.Status.PARSED);
        assertThat(result.signals()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(report ->
                assertThat(report.status().name()).isEqualTo("EMPTY_VALID"));
    }

    @Test
    void contentProjectionDoesNotConfuseNewReceptionMetadataWithChangedContent() {
        String a = "{\"incidents\":[" + injury(90, 4) + "]}";
        String b = "{\"incidents\":[" + injury(90, 6) + "]}";
        var first = normalize(SofascoreEndpointType.EVENT_INCIDENTS, a, 11, RECEIVED);
        var identical = normalize(SofascoreEndpointType.EVENT_INCIDENTS, a, 12, RECEIVED.plusSeconds(60));
        var changed = normalize(SofascoreEndpointType.EVENT_INCIDENTS, b, 13, RECEIVED.plusSeconds(120));
        var back = normalize(SofascoreEndpointType.EVENT_INCIDENTS, a, 14, RECEIVED.plusSeconds(180));
        assertThat(first.eventData()).isEqualTo(identical.eventData()).isEqualTo(back.eventData());
        assertThat(changed.eventData()).isNotEqualTo(back.eventData());
        assertThat(json(back).at("/phaseObservations/0/length/value").intValue()).isEqualTo(4);
        assertThat(first.projectionJson()).isEqualTo(identical.projectionJson()).isEqualTo(back.projectionJson());
        assertThat(changed.projectionJson()).isNotEqualTo(back.projectionJson());
        assertThat(json(back).has("receivedAt")).isFalse();
        assertThat(json(back).has("occurrenceId")).isFalse();
    }

    private LiveNormalizedPayload details(String extra) {
        return normalize(SofascoreEndpointType.EVENT_DETAILS, """
                {"event":{"id":17000001,"startTimestamp":1788796800,
                "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                "status":{"type":"inprogress"}%s}}
                """.formatted(extra), 1, RECEIVED);
    }

    private LiveNormalizedPayload incidents(String items) {
        return normalize(SofascoreEndpointType.EVENT_INCIDENTS,
                "{\"incidents\":[" + items + "]}", 1, RECEIVED);
    }

    private LiveNormalizedPayload normalize(SofascoreEndpointType endpoint, String body,
                                            long occurrence, Instant received) {
        return normalizer.normalize(EVENT_ID, endpoint, 1, occurrence,
                RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)), received);
    }

    private static String injury(int minute, int length) {
        return "{\"incidentType\":\"injuryTime\",\"time\":" + minute
                + ",\"length\":" + length + ",\"addedTime\":0}";
    }

    private static JsonNode json(LiveNormalizedPayload result) {
        return JsonMapper.builder().build().readTree(result.projectionJson());
    }
}
