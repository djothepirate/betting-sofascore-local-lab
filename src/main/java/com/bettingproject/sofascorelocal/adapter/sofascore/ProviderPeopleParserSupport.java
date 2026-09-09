package com.bettingproject.sofascorelocal.adapter.sofascore;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventPerson;
import tools.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** Optional display metadata cannot invalidate the independently validated sporting event. */
public final class ProviderPeopleParserSupport {
    private ProviderPeopleParserSupport() { }

    public static Optional<String> text(JsonNode node, String path, int maximum, Consumer<String> invalid) {
        if (node == null || node.isNull() || node.isMissingNode()) return Optional.empty();
        if (node.isString()) {
            String text = node.stringValue().trim();
            if (!text.isEmpty() && text.length() <= maximum && text.chars().noneMatch(Character::isISOControl))
                return Optional.of(text);
        }
        invalid.accept(path);
        return Optional.empty();
    }

    public static Optional<ProviderCountry> country(JsonNode node, String path, Consumer<String> invalid) {
        if (node == null || node.isNull() || node.isMissingNode()) return Optional.empty();
        if (!node.isObject()) { invalid.accept(path); return Optional.empty(); }
        Optional<String> name = text(node.get("name"), path + ".name", 120, invalid);
        Optional<String> code = text(node.get("alpha2"), path + ".alpha2", 2, invalid).map(s -> s.toUpperCase(Locale.ROOT));
        if (code.filter(s -> !s.matches("[A-Z]{2}")).isPresent()) { invalid.accept(path + ".alpha2"); code = Optional.empty(); }
        return name.isEmpty() && code.isEmpty() ? Optional.empty() : Optional.of(new ProviderCountry(name, code));
    }

    public static Optional<EventPerson> person(JsonNode node, String path, Consumer<String> invalid) {
        if (node == null || node.isNull() || node.isMissingNode()) return Optional.empty();
        if (!node.isObject()) { invalid.accept(path); return Optional.empty(); }
        Optional<String> name = text(node.get("name"), path + ".name", 200, invalid);
        Optional<ProviderCountry> country = country(node.get("country"), path + ".country", invalid);
        return name.map(value -> new EventPerson(value, country));
    }
}
