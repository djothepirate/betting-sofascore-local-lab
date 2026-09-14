package com.bettingproject.sofascorelocal.application.network;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Local display dictionary; unknown provider categories retain their original name. */
final class J3CategoryLabels {
    private static final Map<String, String> COUNTRIES = countries();

    static String french(String source) {
        String key = source.strip().toLowerCase(Locale.ROOT);
        String translated = COUNTRIES.get(key);
        if (translated != null) return translated;
        if (key.endsWith(" amateur")) {
            String country = COUNTRIES.get(key.substring(0, key.length() - " amateur".length()));
            if (country != null) return country + " Amateur";
        }
        return source;
    }

    private static Map<String, String> countries() {
        var result = new HashMap<String, String>();
        for (String code : Locale.getISOCountries()) {
            var country = new Locale.Builder().setRegion(code).build();
            result.put(country.getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT),
                    country.getDisplayCountry(Locale.FRENCH));
        }
        result.putAll(Map.ofEntries(
                Map.entry("england", "Angleterre"), Map.entry("scotland", "Écosse"),
                Map.entry("wales", "Pays de Galles"), Map.entry("northern ireland", "Irlande du Nord"),
                Map.entry("world", "Monde"), Map.entry("europe", "Europe"),
                Map.entry("usa", "États-Unis"), Map.entry("south korea", "Corée du Sud"),
                Map.entry("north korea", "Corée du Nord"), Map.entry("czech republic", "Tchéquie"),
                Map.entry("turkey", "Turquie"), Map.entry("ivory coast", "Côte d’Ivoire")));
        return Map.copyOf(result);
    }
}
