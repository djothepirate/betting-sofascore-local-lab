package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A bounded local asset path; a provider value is never used as an arbitrary resource URL. */
public final class CountryPresentation {
    private static final Set<String> ISO_CODES = Set.of(Locale.getISOCountries());
    private static final Map<String, String> ISO_CODES_BY_DISPLAY_NAME = indexIsoDisplayNames();

    private CountryPresentation() { }

    public static View of(Optional<ProviderCountry> country) {
        if (country.isEmpty()) return View.absent();
        ProviderCountry value = country.orElseThrow();
        String code = value.alpha2().map(String::trim).filter(text -> !text.isEmpty())
                .map(text -> text.toUpperCase(Locale.ROOT)).orElse("");
        String sourceName = value.name().map(String::trim).filter(text -> !text.isEmpty()).orElse("");
        HomeNation homeNation = homeNation(sourceName, code);
        String presentationCode = code.isEmpty() ? ISO_CODES_BY_DISPLAY_NAME.getOrDefault(normalize(sourceName), "") : code;
        String label = homeNation != null ? homeNation.label()
                : frenchLabel(presentationCode, sourceName);
        if (label.isEmpty()) return View.absent();
        String asset = homeNation == null ? localAsset(presentationCode) : homeNation.asset();
        String emoji = regionalIndicatorEmoji(homeNation == null ? presentationCode : "");
        return new View(true, label, emoji, asset.isEmpty() ? "" : "/images/flags/4x3/" + asset + ".svg");
    }

    private static String frenchLabel(String code, String sourceName) {
        if (ISO_CODES.contains(code)) {
            String localized = Locale.of("", code).getDisplayCountry(Locale.FRANCE);
            if (!localized.isBlank()) return localized;
        }
        if ("XK".equals(code)) return "Kosovo";
        return sourceName.isBlank() ? code : sourceName;
    }

    private static String localAsset(String code) {
        return ISO_CODES.contains(code) || "XK".equals(code) ? code.toLowerCase(Locale.ROOT) : "";
    }

    private static String regionalIndicatorEmoji(String code) {
        if (!(ISO_CODES.contains(code) || "XK".equals(code))) return "";
        return new String(Character.toChars(0x1f1e6 + code.charAt(0) - 'A'))
                + new String(Character.toChars(0x1f1e6 + code.charAt(1) - 'A'));
    }

    /**
     * SofaScore occasionally uses football-association names and non-ISO codes for the
     * home nations. They are deliberately matched as bounded name/code pairs so an
     * unrelated explicit ISO code, such as SC for Seychelles, is never reinterpreted.
     */
    private static HomeNation homeNation(String sourceName, String code) {
        return switch (normalize(sourceName)) {
            case "england", "angleterre" -> accepts(code, "", "GB", "EN")
                    ? new HomeNation("Angleterre", "gb-eng") : null;
            case "scotland", "ecosse" -> accepts(code, "", "GB", "SC")
                    ? new HomeNation("Écosse", "gb-sct") : null;
            case "wales", "pays de galles" -> accepts(code, "", "GB", "WA")
                    ? new HomeNation("Pays de Galles", "gb-wls") : null;
            default -> null;
        };
    }

    private static boolean accepts(String code, String... expected) {
        for (String value : expected) if (value.equals(code)) return true;
        return false;
    }

    private static Map<String, String> indexIsoDisplayNames() {
        Map<String, String> byName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (String code : ISO_CODES) {
            Locale locale = Locale.of("", code);
            index(byName, ambiguous, locale.getDisplayCountry(Locale.ENGLISH), code);
            index(byName, ambiguous, locale.getDisplayCountry(Locale.FRANCE), code);
        }
        index(byName, ambiguous, "Kosovo", "XK");
        ambiguous.forEach(byName::remove);
        return Map.copyOf(byName);
    }

    private static void index(Map<String, String> byName, Set<String> ambiguous, String name, String code) {
        String key = normalize(name);
        if (key.isEmpty()) return;
        String previous = byName.putIfAbsent(key, code);
        if (previous != null && !previous.equals(code)) ambiguous.add(key);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private record HomeNation(String label, String asset) { }

    public record View(boolean available, String label, String emoji, String flagPath) {
        private static View absent() {
            return new View(false, "", "", "");
        }
    }
}
