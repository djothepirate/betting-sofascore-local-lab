package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventIncidentsV7ParserTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-17T08:00:00Z");
    private static final long EVENT_ID = 16248427L;
    private final EventIncidentsV7Parser parser = new EventIncidentsV7Parser();

    @Test
    void acceptsTheObservedInjurySubstitutionWithoutLosingItsPlayersOrFlag() {
        var result = parse(71L, injurySubstitution("true"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion())
                .isEqualTo(EventIncidentsV7Parser.PARSER_VERSION);
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
        assertThat(result.data()).hasValueSatisfying(data -> {
            assertThat(data.incidents()).hasSize(1);
            var substitution = data.incidents().getFirst();
            assertThat(substitution.incidentType()).isEqualTo("substitution");
            assertThat(substitution.incidentClass()).contains("injury");
            assertThat(substitution.injury()).contains(true);
            assertThat(substitution.home()).contains(false);
            assertThat(substitution.playerInName()).contains("Jack Grealish");
            assertThat(substitution.playerOutName()).contains("Jérémy Doku");
        });
    }

    @Test
    void rejectsAnExplicitContradictionBetweenInjuryClassAndFalseInjuryFlag() {
        var result = parse(72L, injurySubstitution("false"));

        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.problems())
                .extracting(J5ParseProblem::code)
                .contains(J5ParseProblem.Code.ATOMIC_FIELD_MISMATCH);
    }

    @Test
    void measuresAMissingInjuryFlagAsPartialInsteadOfInventingIt() {
        var result = parse(73L, injurySubstitution(null));

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.problems()).isEmpty();
        assertThat(result.completeness()).hasValueSatisfying(completeness -> {
            assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.PARTIAL);
            assertThat(completeness.missingPaths())
                    .containsExactly("$.incidents[0].injury");
        });
        assertThat(result.data()).hasValueSatisfying(data ->
                assertThat(data.incidents().getFirst().injury()).isEmpty());
    }

    @Test
    void preservesTheRegularSubstitutionContractInheritedFromV6() {
        String json = """
                {"incidents":[{
                  "incidentType":"substitution",
                  "incidentClass":"regular",
                  "time":85,
                  "isHome":true,
                  "injury":false,
                  "playerIn":{"id":1,"name":"Incoming Player"},
                  "playerOut":{"id":2,"name":"Outgoing Player"}
                }]}
                """;

        var result = parse(74L, json);

        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).hasValueSatisfying(completeness ->
                assertThat(completeness.status()).isEqualTo(J5CompletenessStatus.COMPLETE));
    }

    private static String injurySubstitution(String injuryValue) {
        String injury = injuryValue == null ? "" : ",\"injury\":" + injuryValue;
        return """
                {"incidents":[{
                  "incidentType":"substitution",
                  "incidentClass":"injury",
                  "time":46,
                  "isHome":false%s,
                  "playerIn":{"id":864921,"name":"Jack Grealish"},
                  "playerOut":{"id":851005,"name":"Jérémy Doku"}
                }]}
                """.formatted(injury);
    }

    private J5ParseResult<EventIncidents> parse(long snapshotId, String json) {
        return parser.parse(
                snapshotId,
                EVENT_ID,
                RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)),
                RECEIVED_AT);
    }
}
