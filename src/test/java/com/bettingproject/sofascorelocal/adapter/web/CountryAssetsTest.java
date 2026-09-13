package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class CountryAssetsTest {
    @Test void everyExposedFlagIsPackagedWithItsSourceLicenseAndExactDigest() throws Exception {
        Path root=Path.of("src/main/resources/static/images/flags");
        var manifest=JsonMapper.builder().build().readTree(Files.readString(root.resolve("manifest.json")));
        assertThat(manifest.path("version").asText()).isEqualTo("7.3.2");
        assertThat(Files.readString(root.resolve("LICENSE.flag-icons.txt"))).contains("MIT", "Permission is hereby granted");
        Map<String,String> hashes=new HashMap<>();
        for(var entry:manifest.path("flags")) hashes.put(entry.path("code").asText(),entry.path("sha256").asText());
        var codes=new ArrayList<>(Arrays.asList(Locale.getISOCountries())); codes.add("XK");
        for(String code:codes) {
            var view=CountryPresentation.of(Optional.of(new ProviderCountry(Optional.empty(),Optional.of(code))));
            assertThat(view.flagPath()).isEqualTo("/images/flags/4x3/"+code.toLowerCase(Locale.ROOT)+".svg");
            assertThat(hashes).containsKey(code.toLowerCase(Locale.ROOT));
        }
        for(var entry:hashes.entrySet()) {
            byte[] bytes=Files.readAllBytes(root.resolve("4x3/"+entry.getKey()+".svg"));
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .as(entry.getKey()).isEqualTo(entry.getValue());
            String svg=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            assertThat(svg).doesNotContain("<script", "<foreignObject", "<!ENTITY", "<!DOCTYPE");
            assertThat(svg).doesNotMatch("(?is).*\\s(?:on[a-z]+\\s*=|(?:xlink:)?href\\s*=\\s*[\"'](?!#)).*");
        }
        for(String country:List.of("England","Scotland","Wales","Northern Ireland")) {
            var view=CountryPresentation.of(Optional.of(new ProviderCountry(Optional.of(country),Optional.of("GB"))));
            assertThat(Files.exists(Path.of("src/main/resources/static"+view.flagPath()))).isTrue();
        }
        assertThat(CountryPresentation.of(Optional.of(new ProviderCountry(Optional.of("Pays source"),Optional.of("ZZ"))))
                .flagPath()).isEmpty();
        assertThat(CountryPresentation.of(Optional.empty())).satisfies(view -> {
            assertThat(view.available()).isFalse();
            assertThat(view.label()).isEmpty();
            assertThat(view.flagPath()).isEmpty();
        });
    }

    @Test void presentsKnownCountriesInFrenchAndKeepsAVisibleCompatibilityFallback() {
        assertThat(view("Netherlands", "NL")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Pays-Bas");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/nl.svg");
        });
        assertThat(view("Denmark", "DK")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Danemark");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/dk.svg");
        });
        assertThat(view("Greece", "GR")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Grèce");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gr.svg");
        });
        assertThat(view("Argentina", "AR")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Argentine");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/ar.svg");
        });
        assertThat(view("Côte d'Ivoire", "CI")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Côte d’Ivoire");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/ci.svg");
        });
        assertThat(view("Brazil", "")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Brésil");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/br.svg");
        });
        assertThat(view("Pays source", "ZZ")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Pays source");
            assertThat(country.flagPath()).isEmpty();
        });
    }

    @Test void prioritizesExactBritishAssociationNamesOverContradictoryCodesWithoutRewritingOrdinaryCountries() {
        assertThat(view("England", "EN")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Angleterre");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb-eng.svg");
        });
        assertThat(view("Scotland", "SX")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Écosse");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb-sct.svg");
        });
        assertThat(view("Scotland", "CZ")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Écosse");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb-sct.svg");
        });
        assertThat(view("Wales", "WA")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Pays de Galles");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb-wls.svg");
        });
        assertThat(view("Northern Ireland", "NI")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Irlande du Nord");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb.svg");
        });
        assertThat(view("Northern Ireland", "")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Irlande du Nord");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb.svg");
        });
        assertThat(view("Seychelles", "SC")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Seychelles");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/sc.svg");
        });
        assertThat(view("Czechia", "CZ")).satisfies(country -> {
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/cz.svg");
        });
        assertThat(view("Sint Maarten", "SX")).satisfies(country -> {
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/sx.svg");
        });
        assertThat(view("Nicaragua", "NI")).satisfies(country -> {
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/ni.svg");
        });
        assertThat(view("England", "US")).satisfies(country -> {
            assertThat(country.label()).isEqualTo("Angleterre");
            assertThat(country.flagPath()).isEqualTo("/images/flags/4x3/gb-eng.svg");
        });
    }

    private static CountryPresentation.View view(String name, String alpha2) {
        return CountryPresentation.of(Optional.of(new ProviderCountry(Optional.of(name),
                alpha2.isEmpty() ? Optional.empty() : Optional.of(alpha2))));
    }
}
