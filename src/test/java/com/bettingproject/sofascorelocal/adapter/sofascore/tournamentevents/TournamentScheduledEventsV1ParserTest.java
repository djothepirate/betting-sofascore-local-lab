package com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents;

import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventCandidate;
import com.bettingproject.sofascorelocal.fixture.ClasspathFixtureLoader;
import com.bettingproject.sofascorelocal.fixture.FixturePayloadHasher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class TournamentScheduledEventsV1ParserTest {

    private static final String NOMINAL =
            "fixtures/tournament-scheduled-events/nominal.manifest.json";
    private static final String EMPTY =
            "fixtures/tournament-scheduled-events/empty.manifest.json";
    private static final String OTHER_ENDPOINT =
            "fixtures/scheduled-events/nominal.manifest.json";

    private static final String VALID_EVENT = """
            {
              "id": 1,
              "startTimestamp": 1787079600,
              "homeTeam": {"id": 11, "name": "Home"},
              "awayTeam": {"id": 12, "name": "Away"},
              "status": {"type": "notstarted"},
              "tournament": {
                "id": 21,
                "name": "Phase",
                "uniqueTournament": {"id": 7, "name": "Competition"}
              }
            }
            """;

    private static final ClasspathFixtureLoader LOADER = new ClasspathFixtureLoader();
    private static final TournamentScheduledEventsV1Parser PARSER =
            new TournamentScheduledEventsV1Parser();

    @Test
    void parsesEveryStructuredCandidateAndKeepsBothTournamentIdentities() {
        var fixture = LOADER.load(NOMINAL);

        TournamentScheduledEventsParseResult result = PARSER.parse(fixture);

        assertThat(result.status()).isEqualTo(TournamentScheduledEventsParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.evidence())
                .isEqualTo(new TournamentScheduledEventsParseEvidence(
                        "tournament-scheduled-events-nominal",
                        fixture.rawSha256(),
                        fixture.canonicalJsonSha256(),
                        Instant.parse("2026-08-18T18:00:00Z"),
                        TournamentScheduledEventsV1Parser.PARSER_VERSION));

        var candidates = result.candidates().orElseThrow();
        assertThat(candidates).hasSize(3);
        assertThat(candidates)
                .extracting(
                        candidate -> candidate.event().providerEventId(),
                        candidate -> candidate.tournament().providerTournamentId(),
                        candidate -> candidate.uniqueTournament().providerTournamentId())
                .containsExactly(
                        tuple(16707704L, 119880L, 7L),
                        tuple(16707705L, 119881L, 7L),
                        tuple(16707706L, 119880L, 7L));

        TournamentScheduledEventCandidate first = candidates.getFirst();
        assertThat(first.event().startsAt()).isEqualTo(Instant.parse("2026-08-18T19:00:00Z"));
        assertThat(first.event().homeTeam().name()).isEqualTo("Synthetic Bosphorus FC");
        assertThat(first.event().awayTeam().name()).isEqualTo("Synthetic Rhone FC");
        assertThat(first.event().status().description()).contains("Ended");
        assertThat(first.tournament().name())
                .isEqualTo("Synthetic Champions League, Playoff Round");
        assertThat(first.uniqueTournament().name()).isEqualTo("Synthetic Champions League");
        assertThat(candidates.getLast().event().startsAt())
                .isEqualTo(Instant.parse("2026-08-19T18:00:00Z"));

        assertThat(result.warnings())
                .allMatch(warning -> warning.code()
                        == TournamentScheduledEventsParseWarning.Code.UNKNOWN_FIELD)
                .extracting(TournamentScheduledEventsParseWarning::path)
                .contains(
                        "$.events[0].eventState",
                        "$.events[0].season",
                        "$.events[0].tournament.slug",
                        "$.events[0].tournament.uniqueTournament.slug",
                        "$.events[0].status.code",
                        "$.events[0].homeTeam.slug",
                        "$.events[0].awayTeam.slug");
    }

    @Test
    void acceptsAnEmptyEventsArrayAndAbsentPaginationWithoutInventingCandidates() {
        TournamentScheduledEventsParseResult result = PARSER.parse(LOADER.load(EMPTY));

        assertThat(result.status()).isEqualTo(TournamentScheduledEventsParseStatus.PARSED);
        assertThat(result.candidates()).contains(java.util.List.of());
        assertThat(result.problems()).isEmpty();
        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.code())
                            .isEqualTo(TournamentScheduledEventsParseWarning.Code.EMPTY_EVENTS);
                    assertThat(warning.path()).isEqualTo("$.events");
                });
    }

    @Test
    void acceptsExplicitFalsePaginationAndAnAbsentStatusDescription() {
        TournamentScheduledEventsParseResult result = parseJson(
                "{\"events\":[" + VALID_EVENT + "],\"hasNextPage\":false}");

        assertThat(result.status()).isEqualTo(TournamentScheduledEventsParseStatus.PARSED);
        assertThat(result.candidates().orElseThrow())
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.event().status().description()).isEmpty());
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rejectsTruePaginationBecauseNoFollowUpPageContractExists() {
        TournamentScheduledEventsParseResult result = parseJson(
                "{\"events\":[],\"hasNextPage\":true}");

        assertFailed(result, TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(
                        TournamentScheduledEventsParseProblem::code,
                        TournamentScheduledEventsParseProblem::path)
                .containsExactly(tuple(
                        TournamentScheduledEventsParseProblem.Code.PAGINATION_UNSUPPORTED,
                        "$.hasNextPage"));
    }

    @Test
    void reportsStructuralBreaksAtTheirExactPathsWithoutReturningPartialData() {
        TournamentScheduledEventsParseResult result = parseJson("""
                {
                  "events": [
                    {
                      "startTimestamp": "1787079600",
                      "homeTeam": {"id": 0, "name": "Home"},
                      "awayTeam": {"id": 12},
                      "status": {"type": " "},
                      "tournament": {
                        "id": 21,
                        "name": "Phase"
                      }
                    }
                  ]
                }
                """);

        assertFailed(result, TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(
                        TournamentScheduledEventsParseProblem::code,
                        TournamentScheduledEventsParseProblem::path)
                .containsExactly(
                        tuple(
                                TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.events[0].id"),
                        tuple(
                                TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                                "$.events[0].startTimestamp"),
                        tuple(
                                TournamentScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                                "$.events[0].homeTeam.id"),
                        tuple(
                                TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.events[0].awayTeam.name"),
                        tuple(
                                TournamentScheduledEventsParseProblem.Code.TYPE_MISMATCH,
                                "$.events[0].status.type"),
                        tuple(
                                TournamentScheduledEventsParseProblem.Code.REQUIRED_FIELD_MISSING,
                                "$.events[0].tournament.uniqueTournament"));
    }

    @Test
    void discardsAnOtherwiseValidCandidateWhenAnotherEntryIsInvalid() {
        String invalidEvent = VALID_EVENT.replaceFirst("\"id\": 1", "\"id\": \"1\"");
        TournamentScheduledEventsParseResult result = parseJson(
                "{\"events\":[" + VALID_EVENT + "," + invalidEvent + "]}");

        assertFailed(result, TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(TournamentScheduledEventsParseProblem::path)
                .containsExactly("$.events[1].id");
    }

    @Test
    void rejectsDuplicatePropertiesAndTrailingTokensAsAmbiguousJson() {
        TournamentScheduledEventsParseResult duplicate = parseJson(
                "{\"events\":[],\"events\":[]}");
        TournamentScheduledEventsParseResult trailing = parseJson(
                "{\"events\":[]} {\"events\":[]}");

        assertFailed(duplicate, TournamentScheduledEventsParseStatus.UNEXPECTED_CONTENT);
        assertFailed(trailing, TournamentScheduledEventsParseStatus.UNEXPECTED_CONTENT);
        assertThat(duplicate.problems()).extracting(TournamentScheduledEventsParseProblem::code)
                .containsExactly(TournamentScheduledEventsParseProblem.Code.INVALID_JSON);
        assertThat(trailing.problems()).extracting(TournamentScheduledEventsParseProblem::code)
                .containsExactly(TournamentScheduledEventsParseProblem.Code.INVALID_JSON);
    }

    @Test
    void rejectsMillisecondLikeTimestampsAndControlCharacters() {
        String invalidTimestamp = VALID_EVENT.replace("1787079600", "1787079600000");
        String invalidName = VALID_EVENT.replace("\"Home\"", "\"Bad\\nHome\"");

        TournamentScheduledEventsParseResult timestampResult = parseJson(
                "{\"events\":[" + invalidTimestamp + "]}");
        TournamentScheduledEventsParseResult nameResult = parseJson(
                "{\"events\":[" + invalidName + "]}");

        assertFailed(timestampResult, TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(timestampResult.problems())
                .extracting(
                        TournamentScheduledEventsParseProblem::code,
                        TournamentScheduledEventsParseProblem::path)
                .containsExactly(tuple(
                        TournamentScheduledEventsParseProblem.Code.VALUE_OUT_OF_RANGE,
                        "$.events[0].startTimestamp"));
        assertFailed(nameResult, TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(nameResult.problems())
                .extracting(
                        TournamentScheduledEventsParseProblem::code,
                        TournamentScheduledEventsParseProblem::path)
                .containsExactly(tuple(
                        TournamentScheduledEventsParseProblem.Code.TEXT_CONTAINS_CONTROL_CHARACTER,
                        "$.events[0].homeTeam.name"));
    }

    @Test
    void boundsWarningsFromAuxiliaryFieldsAndSignalsTruncation() {
        String auxiliaryFields = IntStream.range(0, 100)
                .mapToObj(index -> ",\"auxiliary" + index + "\":true")
                .collect(java.util.stream.Collectors.joining());
        int closingBrace = VALID_EVENT.lastIndexOf('}');
        String richEvent = VALID_EVENT.substring(0, closingBrace)
                + auxiliaryFields
                + VALID_EVENT.substring(closingBrace);

        TournamentScheduledEventsParseResult result = parseJson(
                "{\"events\":[" + richEvent + "]}");

        assertThat(result.status()).isEqualTo(TournamentScheduledEventsParseStatus.PARSED);
        assertThat(result.candidates().orElseThrow()).hasSize(1);
        assertThat(result.warnings()).hasSize(TournamentScheduledEventsV1Parser.MAXIMUM_WARNINGS);
        assertThat(result.warnings().getLast().code())
                .isEqualTo(TournamentScheduledEventsParseWarning.Code.WARNING_LIMIT_REACHED);
    }

    @Test
    void refusesAFixtureDeclaredForAnotherEndpoint() {
        TournamentScheduledEventsParseResult result = PARSER.parse(LOADER.load(OTHER_ENDPOINT));

        assertFailed(result, TournamentScheduledEventsParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(TournamentScheduledEventsParseProblem::code)
                .containsExactly(TournamentScheduledEventsParseProblem.Code.UNSUPPORTED_ENDPOINT);
    }

    private static TournamentScheduledEventsParseResult parseJson(String json) {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        TournamentScheduledEventsParseEvidence evidence =
                new TournamentScheduledEventsParseEvidence(
                        "unit-test",
                        FixturePayloadHasher.rawSha256(payload),
                        Optional.empty(),
                        Instant.parse("2026-08-18T18:00:00Z"),
                        TournamentScheduledEventsV1Parser.PARSER_VERSION);
        return PARSER.parse(payload, "application/json", evidence);
    }

    private static void assertFailed(
            TournamentScheduledEventsParseResult result,
            TournamentScheduledEventsParseStatus expectedStatus) {
        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.problems()).isNotEmpty();
    }
}
