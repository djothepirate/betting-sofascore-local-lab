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
        for(String country:List.of("England","Scotland","Wales")) {
            var view=CountryPresentation.of(Optional.of(new ProviderCountry(Optional.of(country),Optional.of("GB"))));
            assertThat(Files.exists(Path.of("src/main/resources/static"+view.flagPath()))).isTrue();
        }
        assertThat(CountryPresentation.of(Optional.of(new ProviderCountry(Optional.of("Pays source"),Optional.of("ZZ"))))
                .flagPath()).isEmpty();
        assertThat(CountryPresentation.of(Optional.empty()).flagPath()).isEmpty();
    }
}
