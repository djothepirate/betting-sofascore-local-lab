package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.adapter.sofascore.ProviderPeopleParserSupport;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Player country enrichment; V3 statistics retain absent, empty and zero separately. */
public final class EventLineupsV4Parser {
    public static final String PARSER_VERSION = "event-lineups-v4";
    private final EventLineupsV3Parser baseParser = new EventLineupsV3Parser();

    public J5ParseResult<EventLineups> parse(long snapshotId, long expectedEventId, RawPayloadEvidence payload, Instant receivedAt) {
        var base = baseParser.parse(snapshotId, expectedEventId, payload, receivedAt);
        var e = base.evidence();
        var evidence = new J5ParseEvidence(e.sourceReference(), e.endpointType(), e.rawSha256(), e.canonicalJsonSha256(), e.recordedAt(), PARSER_VERSION);
        if (base.status() != J5ParseStatus.PARSED)
            return J5ParseResult.failed(base.status(), evidence, base.warnings(), base.problems());
        var root = JsonMapper.builder().build().readTree(payload.bytes());
        var d = base.data().orElseThrow();
        var warnings = new ArrayList<>(base.warnings());
        return J5ParseResult.parsed(evidence, new EventLineups(expectedEventId, d.confirmed(),
                team(d.home(), root.path("home"), "$.home", warnings),
                team(d.away(), root.path("away"), "$.away", warnings)),
                base.completeness().orElseThrow(), warnings);
    }

    private static TeamLineup team(TeamLineup team, JsonNode source, String path, List<J5ParseWarning> warnings) {
        var players = new ArrayList<EventLineupPlayer>();
        for (int i = 0; i < team.players().size(); i++) {
            var player = team.players().get(i);
            var country = ProviderPeopleParserSupport.country(source.path("players").get(i).path("player").get("country"), path + ".players[" + i + "].player.country", invalid -> {
                if (warnings.size() < 128) warnings.add(new J5ParseWarning(J5ParseWarning.Code.OPTIONAL_FIELD_INVALID,
                        invalid, "Invalid optional country metadata ignored by event-lineups-v4"));
            });
            players.add(new EventLineupPlayer(player.providerPlayerId(), player.name(), player.shirtNumber(), player.position(),
                    player.starter(), player.captain(), player.statistics(), country));
        }
        var missing = team.missingPlayers().map(previous -> {
            var values = new ArrayList<MissingLineupPlayer>();
            for (int i = 0; i < previous.size(); i++) {
                var player = previous.get(i);
                var country = ProviderPeopleParserSupport.country(source.path("missingPlayers").get(i).path("player").get("country"),
                        path + ".missingPlayers[" + i + "].player.country", invalid -> {
                            if (warnings.size() < 128) warnings.add(new J5ParseWarning(J5ParseWarning.Code.OPTIONAL_FIELD_INVALID,
                                    invalid, "Invalid optional country metadata ignored by event-lineups-v4"));
                        });
                values.add(new MissingLineupPlayer(player.providerPlayerId(), player.name(), player.shirtNumber(), player.position(),
                        player.type(), player.reason(), player.description(), player.externalType(), player.expectedEndDate(), country));
            }
            return List.copyOf(values);
        });
        return new TeamLineup(team.side(), team.formation(), players, missing);
    }
}
