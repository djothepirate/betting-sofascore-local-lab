package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventPerson;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class EventDetailsPresentationTest {
    @Test void keepsProvidedNamesAndCountriesWithoutInventingAbsentOfficials() {
        var coach = new EventPerson("Entraîneur <source>", Optional.of(new ProviderCountry(
                Optional.of("France"), Optional.of("FR"))));
        var referee = new EventPerson("Arbitre source", Optional.empty());
        var details = new EventDetails(901, Instant.parse("2026-09-09T20:00:00Z"),
                new ScheduledTeam(1,"Domicile"),new ScheduledTeam(2,"Extérieur"),
                new ScheduledEventStatus("notstarted",Optional.empty()), Optional.empty(),Optional.empty(),
                Optional.empty(),Optional.of("Finale"),Optional.empty(),Optional.empty(),Optional.empty(),
                Optional.of(coach),Optional.empty(),Optional.of(referee));
        var view = EventDetailsPresentation.from(details);
        assertThat(view.homeManager().name()).isEqualTo("Entraîneur <source>");
        assertThat(view.homeManager().country().flagPath()).isEqualTo("/images/flags/4x3/fr.svg");
        assertThat(view.homeManager().country().label()).isEqualTo("France");
        assertThat(view.awayManager().name()).isEqualTo("—");
        assertThat(view.awayManager().country()).isNull();
        assertThat(view.referee().name()).isEqualTo("Arbitre source");
        assertThat(view.referee().country()).isNull();
        assertThat(view.round()).isEqualTo("Finale");
        assertThat(view.competition()).isEqualTo("—");
    }

    @Test void localizesOfficialsAndMapsEnglandFromItsFootballAssociationCode() {
        var england = new EventPerson("Coach anglais", Optional.of(new ProviderCountry(
                Optional.of("England"), Optional.of("EN"))));
        var greekReferee = new EventPerson("Arbitre grec", Optional.of(new ProviderCountry(
                Optional.of("Greece"), Optional.of("GR"))));
        var details = new EventDetails(902, Instant.parse("2026-09-09T20:00:00Z"),
                new ScheduledTeam(1, "Domicile"), new ScheduledTeam(2, "Extérieur"),
                new ScheduledEventStatus("notstarted", Optional.empty()), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(england), Optional.empty(), Optional.of(greekReferee));

        var view = EventDetailsPresentation.from(details);

        assertThat(view.homeManager().country()).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Angleterre");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb-eng.svg");
        });
        assertThat(view.referee().country()).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Grèce");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gr.svg");
        });
        assertThat(england.country().orElseThrow().name()).contains("England");
        assertThat(greekReferee.country().orElseThrow().name()).contains("Greece");
    }
}
