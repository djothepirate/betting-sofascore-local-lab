package com.bettingproject.sofascorelocal.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class BuildIdentityTest {

    @Test
    void generatedBuildMetadataUsesTheBettingProjectGroup() throws IOException {
        URL resource = getClass().getClassLoader()
                .getResource("META-INF/build-info.properties");

        assertThat(resource)
                .as("Spring Boot build metadata")
                .isNotNull();

        Properties build = new Properties();
        try (InputStream input = resource.openStream()) {
            build.load(input);
        }

        assertThat(build)
                .containsEntry("build.group", "com.bettingproject")
                .doesNotContainValue("com.geoffrey.betting");
    }
}
