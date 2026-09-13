package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.adapter.sofascore.ProviderPeopleParserSupport;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.Optional;
import java.util.function.Consumer;

/** Adds optional officials and the named round to the unchanged V3 sporting contract. */
public final class EventDetailsV4Parser {
    public static final String PARSER_VERSION = "event-details-v4";
    private static final Set<String> RECOGNIZED = Set.of("$.event.homeTeam.manager", "$.event.awayTeam.manager",
            "$.event.referee", "$.event.roundInfo.name");
    private final EventDetailsV3Parser baseParser = new EventDetailsV3Parser();
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public EventDetailsParseResult parse(long snapshotId, RawPayloadEvidence payload, Instant receivedAt) {
        JsonNode event = null;
        try {
            JsonNode root = JSON.readTree(payload.bytes());
            if (root != null && root.isObject()) event = root.get("event");
        } catch (JacksonException ignored) {
            // The unchanged base parser reports strict JSON failures against the original bytes.
        }
        Optional<String> preferredRound = event != null && event.isObject()
                ? ProviderPeopleParserSupport.text(event.path("roundInfo").get("name"), "$.event.roundInfo.name", 64, ignored -> { })
                : Optional.empty();
        var base = baseParser.parseWithPreferredRound(snapshotId, payload, receivedAt, preferredRound);
        var original = base.evidence();
        var evidence = new EventDetailsParseEvidence(original.sourceReference(), original.rawSha256(),
                original.canonicalJsonSha256(), original.recordedAt(), PARSER_VERSION);
        var warnings = new ArrayList<>(base.warnings().stream().filter(w -> !RECOGNIZED.contains(w.path())).toList());
        if (base.status() != EventDetailsParseStatus.PARSED)
            return EventDetailsParseResult.failed(base.status(), evidence, warnings, base.problems());
        Consumer<String> invalid = path -> {
            if (warnings.size() < 128) warnings.add(new EventDetailsParseWarning(
                    EventDetailsParseWarning.Code.OPTIONAL_FIELD_INVALID, path, "Invalid optional metadata ignored by event-details-v4"));
        };
        var home = ProviderPeopleParserSupport.person(event.path("homeTeam").get("manager"), "$.event.homeTeam.manager", invalid);
        var away = ProviderPeopleParserSupport.person(event.path("awayTeam").get("manager"), "$.event.awayTeam.manager", invalid);
        var referee = ProviderPeopleParserSupport.person(event.get("referee"), "$.event.referee", invalid);
        var roundName = ProviderPeopleParserSupport.text(event.path("roundInfo").get("name"), "$.event.roundInfo.name", 64, invalid);
        var d = base.details().orElseThrow();
        return EventDetailsParseResult.parsed(evidence, new EventDetails(d.providerEventId(), d.startsAt(), d.homeTeam(),
                d.awayTeam(), d.status(), d.tournament(), d.venue(), d.season(), roundName.isPresent() ? roundName : d.round(),
                d.isAwarded(), d.homeDisplayScore(), d.awayDisplayScore(), home, away, referee), warnings);
    }
}
