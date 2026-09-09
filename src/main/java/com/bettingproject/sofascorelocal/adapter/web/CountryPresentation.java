package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** A bounded local asset path; a provider value is never used as an arbitrary resource URL. */
public final class CountryPresentation {
    private static final Set<String> ISO_CODES = Set.of(Locale.getISOCountries());

    private CountryPresentation() { }

    public static View of(Optional<ProviderCountry> country) {
        if (country.isEmpty()) return new View("Pays non renseigné", "", "");
        ProviderCountry value = country.orElseThrow();
        String code = value.alpha2().orElse("");
        String label = value.name().orElse(code);
        String asset = "";
        if (ISO_CODES.contains(code) || code.equals("XK")) asset = code.toLowerCase(Locale.ROOT);
        // These provider names designate football associations within the United Kingdom.
        // Do not replace an explicitly different country code based on a coincidentally equal name.
        if (code.isEmpty() || code.equals("GB")) {
            asset = switch (label.toLowerCase(Locale.ROOT)) {
                case "england", "angleterre" -> "gb-eng";
                case "scotland", "écosse" -> "gb-sct";
                case "wales", "pays de galles" -> "gb-wls";
                default -> asset;
            };
        }
        String emoji = code.length() == 2 && !asset.isEmpty()
                ? new String(Character.toChars(0x1f1e6 + code.charAt(0) - 'A'))
                    + new String(Character.toChars(0x1f1e6 + code.charAt(1) - 'A')) : "";
        return new View(label, emoji, asset.isEmpty() ? "" : "/images/flags/4x3/" + asset + ".svg");
    }

    public record View(String label, String emoji, String flagPath) { }
}
