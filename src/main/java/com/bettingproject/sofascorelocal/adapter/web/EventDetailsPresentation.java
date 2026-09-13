package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventPerson;
import java.util.Optional;

/** Display only the people and competition fields of the selected persisted J4 observation. */
public final class EventDetailsPresentation {
    private EventDetailsPresentation() { }

    public static View from(EventDetails details) {
        return new View(details.homeTeam().name(), details.awayTeam().name(),
                person(details.homeManager()), person(details.awayManager()), person(details.referee()),
                details.tournament().map(t -> t.name()).orElse("—"), details.round().orElse("—"),
                details.season().map(s -> s.name()).orElse("—"), details.venue().map(v -> v.name()).orElse("—"));
    }

    private static Person person(Optional<EventPerson> source) {
        return source.map(value -> new Person(value.name(), value.country().isPresent()
                ? CountryPresentation.of(value.country()) : null)).orElse(new Person("—", null));
    }

    public record View(String homeTeam, String awayTeam, Person homeManager, Person awayManager,
                       Person referee, String competition, String round, String season, String venue) { }
    public record Person(String name, CountryPresentation.View country) { }
}
