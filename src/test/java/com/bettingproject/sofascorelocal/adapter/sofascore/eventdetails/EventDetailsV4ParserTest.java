package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class EventDetailsV4ParserTest {
    private static final Instant RECEIVED = Instant.parse("2026-09-09T10:00:00Z");
    private static final String JSON = """
        {"event":{"id":46001,"startTimestamp":1788948000,
          "homeTeam":{"id":1,"name":"Synthetic home","manager":{"name":"Home manager","country":{"name":"Brazil","alpha2":"br"}}},
          "awayTeam":{"id":2,"name":"Synthetic away","manager":{"name":"Away manager","country":{"alpha2":"AR"}}},
          "status":{"type":"inprogress","description":"1st half"},
          "roundInfo":{"round":6,"name":"Quarter-finals"},
          "referee":{"name":"Synthetic referee","country":{"name":"France"}}}}
        """;

    @Test
    void namedRoundAndOptionalPeopleAreVersionedWithoutChangingOriginalBytesOrLegacyInterpretation() {
        var payload = payload(JSON);
        var current = parse(payload);
        var legacy = new EventDetailsV3Parser().parse(46, payload, RECEIVED);
        assertThat(current.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(current.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(current.evidence().recordedAt()).isEqualTo(RECEIVED);
        assertThat(current.evidence().parserVersion()).isEqualTo("event-details-v4");
        var details = current.details().orElseThrow();
        assertThat(details.round()).contains("Quarter-finals");
        assertThat(details.homeManager().orElseThrow().name()).isEqualTo("Home manager");
        assertThat(details.homeManager().orElseThrow().country().orElseThrow().alpha2()).contains("BR");
        assertThat(details.awayManager().orElseThrow().country().orElseThrow().name()).isEmpty();
        assertThat(details.referee().orElseThrow().country().orElseThrow().name()).contains("France");
        assertThat(legacy.details().orElseThrow().round()).contains("6");
        assertThat(legacy.details().orElseThrow().homeManager()).isEmpty();
        assertThat(legacy.details().orElseThrow().referee()).isEmpty();
        var identity = CanonicalEventIdentity.sofascore(46001);
        var source = EventSourceTrace.providerSnapshot(46, payload.sha256(), "event-details-v4", RECEIVED);
        var observation = EventDetailObservation.from(identity, details, source);
        assertThat(EventDetailObservation.from(identity, details, source)).isEqualTo(observation);
        var changed = parse(payload(JSON.replace("Synthetic referee", "Another referee"))).details().orElseThrow();
        assertThat(EventDetailObservation.from(identity, changed, source).normalizedSha256()).isNotEqualTo(observation.normalizedSha256());
        assertThatThrownBy(() -> EventDetailObservation.from(identity, details,
                EventSourceTrace.providerSnapshot(46, payload.sha256(), "event-details-v3", RECEIVED)))
                .hasMessageContaining("officials require event-details-v4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[]", "17", "{\"country\":{\"alpha2\":\"FR\"}}", "{\"name\":\" \"}"})
    void absentOrIncompleteOfficialsDoNotDiscardTheSportingResult(String person) {
        var json = JSON.replace("{\"name\":\"Synthetic referee\",\"country\":{\"name\":\"France\"}}", person);
        var result = parse(payload(json));
        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details().orElseThrow().referee()).isEmpty();
        assertThat(result.details().orElseThrow().homeManager()).isPresent();
    }

    @Test
    void invalidOptionalCountryAndRoundNameUseAvailableFallbacksWithoutInventingValues() {
        var result = parse(payload(JSON.replace("\"alpha2\":\"br\"", "\"alpha2\":\"BRA\"")
                .replace("\"name\":\"Quarter-finals\"", "\"name\":17")));
        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details().orElseThrow().round()).contains("6");
        var country = result.details().orElseThrow().homeManager().orElseThrow().country().orElseThrow();
        assertThat(country.name()).contains("Brazil");
        assertThat(country.alpha2()).isEmpty();
        assertThat(result.warnings()).extracting(EventDetailsParseWarning::code)
                .contains(EventDetailsParseWarning.Code.OPTIONAL_FIELD_INVALID);
    }

    @Test
    void preferredRoundNameAtItsBoundRetainsExactOriginalEvidence() {
        String namedRound = "N".repeat(64);
        var raw = payload(JSON.replace("\"name\":\"Quarter-finals\"", "\"name\":\"" + namedRound + "\"")
                .replace("\"round\":6", "\"round\":{}"));
        byte[] original = raw.bytes().clone();
        var result = parse(raw);
        assertThat(result.status()).isEqualTo(EventDetailsParseStatus.PARSED);
        assertThat(result.details().orElseThrow().round()).contains(namedRound);
        assertThat(result.evidence().rawSha256()).isEqualTo(raw.sha256());
        assertThat(result.evidence().canonicalJsonSha256()).isEmpty();
        assertThat(result.evidence().sourceReference()).isEqualTo("snapshot:46");
        assertThat(result.evidence().recordedAt()).isEqualTo(RECEIVED);
        assertThat(raw.bytes()).containsExactly(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "-1"})
    void validPreferredNameSkipsInvalidNumericFallbackOnlyInV4(String fallback) {
        var raw = payload(JSON.replace("\"round\":6", "\"round\":" + fallback));
        assertThat(parse(raw).details().orElseThrow().round()).contains("Quarter-finals");
        assertThat(new EventDetailsV3Parser().parse(46, raw, RECEIVED).status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(new EventDetailsV2Parser().parse(46, raw, RECEIVED).status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
        var direct = payload(JSON.replace("\"roundInfo\":", "\"round\":" + fallback + ",\"roundInfo\":"));
        assertThat(parse(direct).details().orElseThrow().round()).contains("Quarter-finals");
        assertThat(new EventDetailsV3Parser().parse(46, direct, RECEIVED).status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "-1"})
    void missingOrInvalidPreferredNameStillRequiresAValidFallback(String fallback) {
        for (String name : new String[]{"", ",\"name\":null", ",\"name\":\" \"", ",\"name\":\"" + "N".repeat(65) + "\""}) {
            var raw = payload(JSON.replace(",\"name\":\"Quarter-finals\"", name).replace("\"round\":6", "\"round\":" + fallback));
            var result = parse(raw);
            var legacy = new EventDetailsV3Parser().parse(46, raw, RECEIVED);
            assertThat(result.status()).isEqualTo(EventDetailsParseStatus.SCHEMA_INCOMPATIBLE);
            assertThat(result.details()).isEmpty();
            assertThat(result.problems()).isEqualTo(legacy.problems());
        }
    }

    @Test
    void coreSchemaFailuresAndStrictJsonRemainBlocking() {
        for (String json : new String[]{JSON.replace("\"id\":46001", "\"id\":\"46001\""), JSON + " {}",
                JSON.replace("\"round\":6", "\"round\":6,\"round\":7")}) {
            var raw = payload(json);
            var old = new EventDetailsV3Parser().parse(46, raw, RECEIVED);
            var current = parse(raw);
            assertThat(current.details()).isEmpty();
            assertThat(current.status()).isEqualTo(old.status());
            assertThat(current.problems()).isEqualTo(old.problems());
        }
    }

    private static EventDetailsParseResult parse(RawPayloadEvidence raw) { return new EventDetailsV4Parser().parse(46, raw, RECEIVED); }
    private static RawPayloadEvidence payload(String json) { return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)); }
}
