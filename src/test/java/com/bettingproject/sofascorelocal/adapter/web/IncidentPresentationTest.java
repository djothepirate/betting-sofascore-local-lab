package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentPresentationTest {
    @Test
    void translatesTheExactCardReasonOnlyForDisplay() {
        EventIncident incident = incident("card", "Professional handball", null);

        assertThat(IncidentPresentation.motifLabel(incident)).isEqualTo("Main volontaire");
        assertThat(incident.reason()).contains("Professional handball");
        assertThat(incident.motifLabel()).isEqualTo("Professional handball");
        assertThat(incident.incidentClass()).contains("red");
        assertThat(incident.minute()).contains(64);
        assertThat(incident.home()).contains(false);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"professional handball", "Professional Handball", "Handball",
            "Professional foul last man", "Foul", "2nd half"})
    void retainsEveryOtherReasonAndTheMissingValue(String reason) {
        EventIncident incident = incident("card", reason, null);

        assertThat(IncidentPresentation.motifLabel(incident)).isEqualTo(incident.motifLabel());
    }

    @Test
    void doesNotApplyTheCardTranslationToOtherIncidentTypes() {
        EventIncident incident = incident("inGamePenalty", "Professional handball", null);

        assertThat(IncidentPresentation.motifLabel(incident)).isEqualTo("Professional handball");
    }

    @Test
    void preservesTheExistingDescriptionPrecedence() {
        EventIncident incident = incident("card", "Professional handball", "Provider description");

        assertThat(IncidentPresentation.motifLabel(incident)).isEqualTo("Provider description");
        assertThat(incident.reason()).contains("Professional handball");
    }

    private static EventIncident incident(String type, String reason, String description) {
        return new EventIncident(0, type, 64, Optional.empty(), Optional.of(false),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("red"),
                Optional.ofNullable(reason), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(false),
                Optional.ofNullable(description), Optional.empty());
    }
}
