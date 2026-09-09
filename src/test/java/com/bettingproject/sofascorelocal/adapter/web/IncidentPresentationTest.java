package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentPresentationTest {
    @Test
    void periodsKeepSourceIndicesAndAddedTimeInItsRegulationHalf() {
        var source = new EventIncidents(900001, List.of(
                timedIncident(0, 90, 8), timedIncident(1, 45, 8), timedIncident(2, 105, 2),
                timedIncident(3, 120, 1), timedIncident(4, 44, 0),
                visualIncident(5, "penaltyShootout", "scored", null, null), timedIncident(6, 140, 0)));
        var view = IncidentPresentation.from(source);
        assertThat(view.incidents()).extracting(IncidentPresentation.Item::minuteLabel)
                .containsExactly("90+8", "45+8", "105+2", "120+1", "44", "—", "140");
        assertThat(view.periods()).extracting(IncidentPresentation.Period::key)
                .containsExactly("SECOND_HALF", "FIRST_HALF", "EXTRA_FIRST", "EXTRA_SECOND", "SHOOTOUT", "OTHER");
        assertThat(view.periods().get(1).incidentIndexes()).containsExactly(1, 4);
        assertThat(view.periods().stream().flatMap(period -> period.incidentIndexes().stream()))
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6);
        assertThat(IncidentPresentation.from(new EventIncidents(900001, List.of())).periods()).isEmpty();
    }

    private static EventIncident timedIncident(int sequence, int minute, int added) {
        return new EventIncident(sequence, "card", minute, added == 0 ? Optional.empty() : Optional.of(added), Optional.of(false),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("yellow"), Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({"Foul,Faute", "Argument,Contestation", "Violent conduct,Comportement violent",
            "Professional handball,Main volontaire"})
    void graphicalCardMotifsAreFrenchWhileTheTechnicalTableAndAddedMinuteStayFaithful(String reason, String label) {
        var incident = new EventIncident(0, "card", 90, Optional.of(3), Optional.of(false),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("red"), Optional.of(reason));
        var displayed = IncidentPresentation.from(new EventIncidents(900001, List.of(incident))).incidents().getFirst();
        assertThat(displayed.minuteLabel()).isEqualTo("90+3");
        assertThat(displayed.motifLabel()).isEqualTo(label);
        assertThat(incident.reason()).contains(reason);
        assertThat(IncidentPresentation.motifLabel(incident))
                .isEqualTo("Professional handball".equals(reason) ? "Main volontaire" : reason);
    }

    @ParameterizedTest
    @CsvSource({
            "goal,regular,But,⚽,goal",
            "goal,penalty,But sur penalty,⚽,goal",
            "goal,ownGoal,But contre son camp,⚽,goal",
            "card,yellow,Carton jaune,🟨,yellow",
            "card,red,Carton rouge,🟥,red",
            "card,yellowRed,Second carton jaune · Expulsion,🟨🟥,red",
            "substitution,regular,Remplacement,🏃🏻‍♂️,substitution",
            "inGamePenalty,awarded,Penalty accordé,🥅,penalty",
            "penaltyShootout,scored,Tir au but marqué,⚽,goal",
            "period,,Repère de période,⏱,period",
            "injuryTime,,Temps additionnel,⏱,period",
            "varDecision,cardUpgrade,Décision VAR,📺,var"
    })
    void explicitClassesHaveDistinctFrenchVisualLabels(String type, String category,
                                                       String label, String icon, String tone) {
        var source = visualIncident(0, type, category, null, null);
        var view = IncidentPresentation.from(new EventIncidents(900001, List.of(source)), "Équipe A", "Équipe B");
        assertThat(view.incidents()).singleElement().satisfies(item -> {
            assertThat(item.typeLabel()).isEqualTo(label);
            assertThat(item.icon()).isEqualTo(icon);
            assertThat(item.tone()).isEqualTo(tone);
            assertThat(item.minuteLabel()).isEqualTo(source.minuteLabel());
            assertThat(item.scoreLabel()).isEqualTo("1–0");
            assertThat(item.teamLabel()).isEqualTo("Équipe B");
            assertThat(item.teamSide()).isEqualTo("AWAY");
        });
        assertThat(source.incidentClass()).isEqualTo(Optional.ofNullable(category));
    }

    @ParameterizedTest
    @CsvSource({
            "inGamePenalty,missed,goalkeeperSave,,Penalty arrêté,🧤",
            "inGamePenalty,missed,,Goalkeeper save,Penalty arrêté,🧤",
            "inGamePenalty,missed,,,Penalty manqué,❌",
            "inGamePenalty,missed,offTarget,Off target,Penalty manqué,❌",
            "inGamePenalty,missed,woodwork,Woodwork,Penalty manqué,❌",
            "inGamePenalty,missed,GoalkeeperSave,,Penalty manqué,❌",
            "inGamePenalty,awarded,goalkeeperSave,,Penalty accordé,🥅",
            "penaltyShootout,missed,goalkeeperSave,,Tir au but arrêté,🧤",
            "penaltyShootout,missed,,,Tir au but manqué,❌"
    })
    void aSaveRequiresAnExplicitMissedOutcomeAndExactKeeperEvidence(String type, String category,
            String reason, String description, String label, String icon) {
        var source = visualIncident(0, type, category, reason, description);
        var item = IncidentPresentation.from(new EventIncidents(900001, List.of(source))).incidents().getFirst();
        assertThat(item.typeLabel()).isEqualTo(label);
        assertThat(item.icon()).isEqualTo(icon);
        assertThat(item.teamSide()).isEqualTo("AWAY");
        assertThat(item.teamLabel()).isEqualTo("Extérieur");
        assertThat(item.playerLabel()).isEqualTo("Joueur source");
        assertThat(item.motifLabel()).isEqualTo(source.motifLabel());
        if ("penaltyShootout".equals(type)) {
            assertThat(item.minuteLabel()).isEqualTo("—");
            assertThat(item.detailLabel()).isEqualTo("Tir n° 2");
        }
    }

    @Test
    void sourceOrderAndRepeatedIncidentsRemainIntactAndPeriodDoesNotDeclareTheMatchFinished() {
        var source = new EventIncidents(900001, List.of(
                visualIncident(0, "goal", "regular", null, null),
                visualIncident(1, "period", null, null, null),
                visualIncident(2, "goal", "regular", null, null)));
        var items = IncidentPresentation.from(source).incidents();
        assertThat(items).extracting(IncidentPresentation.Item::minuteLabel).containsExactly("64+2", "45", "64+2");
        assertThat(items.getFirst()).isEqualTo(items.getLast());
        assertThat(items.get(1).typeLabel()).isEqualTo("Repère de période");
        assertThat(items.get(1).detailLabel()).isEqualTo("Première mi-temps");
        assertThat(items.get(1).typeLabel() + items.get(1).detailLabel()).doesNotContain("terminé", "Fin", "finished");
        assertThat(source.incidents().get(1).periodText()).contains("First half");
    }

    @Test
    void replacementDirectionAndVarDecisionAreExplicitWithoutInventingARedCard() {
        var substitution = IncidentPresentation.from(new EventIncidents(900001,
                List.of(visualIncident(0, "substitution", "regular", null, null)))).incidents().getFirst();
        assertThat(substitution.playerInLabel()).isEqualTo("Joueur entrant");
        assertThat(substitution.playerOutLabel()).isEqualTo("Joueur sortant");
        assertThat(substitution.detailLabel()).isEqualTo("Remplacement sur blessure");

        var decision = IncidentPresentation.from(new EventIncidents(900001,
                List.of(visualIncident(0, "varDecision", "cardUpgrade", null, null)))).incidents().getFirst();
        assertThat(decision.typeLabel()).isEqualTo("Décision VAR");
        assertThat(decision.detailLabel()).isEqualTo("Sanction aggravée · Décision confirmée");
        assertThat(decision.icon()).isEqualTo("📺");
        assertThat(decision.tone()).isEqualTo("var");
        assertThat(decision.detailLabel()).doesNotContain("rouge", "Expulsion");
    }

    @Test
    void absentValuesUnknownTypesAndMarkupStayExplicitInertPresentationText() {
        var source = new EventIncident(0, "future<type>", 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        var view = IncidentPresentation.from(new EventIncidents(900001, List.of(source)), "<b>A</b>", "<b>B</b>");
        assertThat(view.incidents()).singleElement().satisfies(item -> {
            assertThat(item.typeLabel()).isEqualTo("Incident non reconnu · future<type>");
            assertThat(item.tone()).isEqualTo("neutral");
            assertThat(item.icon()).isEqualTo("•");
            assertThat(item.teamSide()).isEqualTo("UNKNOWN");
            assertThat(item.teamLabel()).isEqualTo("Équipe non renseignée");
            assertThat(item.playerLabel()).isEqualTo("—");
            assertThat(item.scoreLabel()).isEqualTo("—");
            assertThat(item.detailLabel()).isEqualTo("—");
            assertThat(item.playerInLabel()).isEqualTo("—");
            assertThat(item.playerOutLabel()).isEqualTo("—");
        });
        assertThat(IncidentPresentation.from(new EventIncidents(900001, List.of())).incidents()).isEmpty();
    }

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

    private static EventIncident visualIncident(int sequence, String type, String category, String reason, String description) {
        boolean shootout = "penaltyShootout".equals(type), period = "period".equals(type);
        boolean substitution = "substitution".equals(type), goal = "goal".equals(type), var = "varDecision".equals(type);
        return new EventIncident(sequence, type, shootout ? Optional.empty() : Optional.of(period ? 45 : 64),
                shootout || period ? Optional.empty() : Optional.of(2), Optional.of(false),
                Optional.empty(), Optional.of(100L), Optional.of("Joueur source"),
                substitution ? Optional.of(101L) : Optional.empty(), substitution ? Optional.of("Joueur entrant") : Optional.empty(),
                substitution ? Optional.of(102L) : Optional.empty(), substitution ? Optional.of("Joueur sortant") : Optional.empty(),
                Optional.of(1), Optional.of(0), Optional.ofNullable(category), Optional.ofNullable(reason),
                period ? Optional.of("First half") : Optional.empty(), substitution ? Optional.of(true) : Optional.empty(),
                goal ? Optional.of(103L) : Optional.empty(), goal ? Optional.of("Passeur source") : Optional.empty(),
                goal && List.of("penalty", "ownGoal").contains(category) ? Optional.of(category) : Optional.empty(),
                "injuryTime".equals(type) ? Optional.of(4) : Optional.empty(), var ? Optional.of(true) : Optional.empty(),
                Optional.of(false), Optional.ofNullable(description), shootout ? Optional.of(2) : Optional.empty());
    }
}
