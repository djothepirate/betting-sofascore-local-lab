package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Display-only country metadata recovered from one verified historical raw snapshot.
 * It never changes the normalized lineup observation that selected it.
 */
public final class LineupCountryOverlay {

    private static final LineupCountryOverlay EMPTY = new LineupCountryOverlay(Map.of());

    private final Map<PlayerKey, ProviderCountry> countries;

    private LineupCountryOverlay(Map<PlayerKey, ProviderCountry> countries) {
        this.countries = Map.copyOf(countries);
    }

    public static LineupCountryOverlay empty() {
        return EMPTY;
    }

    public static LineupCountryOverlay of(Map<PlayerKey, ProviderCountry> countries) {
        Objects.requireNonNull(countries, "countries");
        if (countries.isEmpty()) {
            return EMPTY;
        }
        var copy = new LinkedHashMap<PlayerKey, ProviderCountry>();
        countries.forEach((key, country) -> copy.put(
                Objects.requireNonNull(key, "country key"),
                Objects.requireNonNull(country, "country")));
        return new LineupCountryOverlay(copy);
    }

    public Optional<ProviderCountry> rosterCountry(LineupSide side, long providerPlayerId) {
        return Optional.ofNullable(countries.get(PlayerKey.roster(side, providerPlayerId)));
    }

    public Optional<ProviderCountry> missingPlayerCountry(LineupSide side, long providerPlayerId) {
        return Optional.ofNullable(countries.get(PlayerKey.missingPlayer(side, providerPlayerId)));
    }

    public record PlayerKey(LineupSide side, long providerPlayerId, boolean missingPlayer) {
        public PlayerKey {
            side = Objects.requireNonNull(side, "side");
            if (providerPlayerId < 1) {
                throw new IllegalArgumentException("providerPlayerId must be positive");
            }
        }

        public static PlayerKey roster(LineupSide side, long providerPlayerId) {
            return new PlayerKey(side, providerPlayerId, false);
        }

        public static PlayerKey missingPlayer(LineupSide side, long providerPlayerId) {
            return new PlayerKey(side, providerPlayerId, true);
        }
    }
}
