package com.bettingproject.sofascorelocal.cli;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class J8BenchmarkExportCommandTest {

    private static final Instant AS_OF = Instant.parse("2026-08-29T10:15:30Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyBoundedUtcInstantsAndRejectsMissingOrNonUtcValues() {
        assertThat(J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", " 2026-08-29T10:15:30Z "))
                .isEqualTo(AS_OF);
        assertThat(J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", "2026-08-29T10:15:30.123456789Z"))
                .isEqualTo(Instant.parse("2026-08-29T10:15:30.123456789Z"));

        assertThatThrownBy(() -> J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", " ".repeat(50) + "2026-08-29T10:15:30Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", "2026-08-29T12:15:30+02:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", "2026-08-29T10:15:30Z\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requiredUtcInstant(
                "TEST", "\t2026-08-29T10:15:30Z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEveryUnexpectedCommandLineArgument() {
        assertThatCode(() -> J8BenchmarkExportCommand.requireNoCliArguments(new String[0]))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requireNoCliArguments(
                new String[] {"--sofascore.enabled=true"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requireNoCliArguments(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repositoryLauncherKeepsTheOneShotMainClassOverrideEffective() throws IOException {
        Path repository = Path.of("").toAbsolutePath().normalize();
        String pom = Files.readString(repository.resolve("pom.xml"), StandardCharsets.UTF_8);
        String script = Files.readString(
                repository.resolve("scripts/Export-J8Benchmark.ps1"),
                StandardCharsets.UTF_8);

        assertThat(pom)
                .contains("<spring-boot.run.main-class>"
                        + "com.bettingproject.sofascorelocal.SofascoreLocalApplication"
                        + "</spring-boot.run.main-class>")
                .contains("<mainClass>${spring-boot.run.main-class}</mainClass>")
                .doesNotContain("<mainClass>com.bettingproject.sofascorelocal."
                        + "SofascoreLocalApplication</mainClass>");
        assertThat(script).contains(
                "-Dspring-boot.run.main-class="
                        + "com.bettingproject.sofascorelocal.cli.J8BenchmarkExportCommand");
    }

    @Test
    void installsTheNonWebSafetyMapAheadOfHostileEnvironmentValues() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "hostileValues",
                Map.<String, Object>of(
                        "spring.main.web-application-type", "servlet",
                        "sofascore.enabled", "true",
                        "sofascore.playwright.enabled", "true")));

        J8BenchmarkExportCommand.installSafetyPropertySource(environment);

        assertThat(environment.getPropertySources().iterator().next().getName())
                .isEqualTo(J8BenchmarkExportCommand.SAFETY_PROPERTY_SOURCE);
        assertThat(environment.getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(environment.getProperty("spring.task.scheduling.enabled"))
                .isEqualTo("false");
        for (String gate : List.of(
                "sofascore.enabled",
                "sofascore.playwright.enabled",
                "sofascore.playwright.loopback-qualification",
                "sofascore.j3-qualification-enabled",
                "sofascore.j4-event-details-qualification-enabled",
                "sofascore.j4-event-details-phase2-enabled",
                "sofascore.j5-event-data-qualification-enabled",
                "sofascore.tournament-event-discovery-enabled",
                "sofascore.automatic-refresh-enabled",
                "sofascore.live-polling-enabled")) {
            assertThat(environment.getProperty(gate)).isEqualTo("false");
        }
        assertThat(environment.getProperty("sofascore.playwright.loopback-origin"))
                .isEmpty();
    }

    @Test
    void requiresThePersistedNetworkLockWithoutHidingIncompleteAttempts() {
        SofascoreProperties properties = new SofascoreProperties();
        ProviderPlaywrightProperties playwright = new ProviderPlaywrightProperties();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        assertThatCode(() -> J8BenchmarkExportCommand.requireSafeContext(
                properties, playwright, jdbcTemplate))
                .doesNotThrowAnyException();

        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.FALSE);
        assertThatThrownBy(() -> J8BenchmarkExportCommand.requireSafeContext(
                properties, playwright, jdbcTemplate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connector control");
    }

    @Test
    void writesCreateNewUnderThePhysicalJ8DirectoryAndReturnsItsSha() throws IOException {
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));

        J8BenchmarkExportCommand.ExportedFile exported =
                J8BenchmarkExportCommand.writeCreateNew(
                        repository,
                        Path.of("exports"),
                        AS_OF,
                        "hello");

        assertThat(exported.path())
                .isEqualTo("exports/j8/J8-BENCHMARK-REPORT-20260829T101530000Z.md");
        assertThat(exported.sha256())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(Files.readString(repository.resolve(exported.path()), StandardCharsets.UTF_8))
                .isEqualTo("hello");

        assertThatThrownBy(() -> J8BenchmarkExportCommand.writeCreateNew(
                repository,
                Path.of("exports"),
                AS_OF,
                "different"))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void refusesAConfiguredExportDirectoryOutsideTheRepositoryBound() throws IOException {
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));

        assertThatThrownBy(() -> J8BenchmarkExportCommand.writeCreateNew(
                repository,
                Path.of("..", "outside"),
                AS_OF,
                "report"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("repository exports directory");
        assertThat(repository.resolve("exports")).doesNotExist();
    }

    @Test
    void refusesAnExportsSymbolicLinkOrReparsePointWhenSupported() throws IOException {
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        createSymbolicLinkOrSkip(repository.resolve("exports"), outside);

        assertThatThrownBy(() -> J8BenchmarkExportCommand.writeCreateNew(
                repository,
                Path.of("exports"),
                AS_OF,
                "report"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reparse points");
        assertThat(outside.resolve("j8")).doesNotExist();
    }

    @Test
    void refusesAJ8SymbolicLinkOrReparsePointWhenSupported() throws IOException {
        Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));
        Path exports = Files.createDirectory(repository.resolve("exports"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        createSymbolicLinkOrSkip(exports.resolve("j8"), outside);

        assertThatThrownBy(() -> J8BenchmarkExportCommand.writeCreateNew(
                repository,
                Path.of("exports"),
                AS_OF,
                "report"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reparse points");
        assertThat(outside).isEmptyDirectory();
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        }
        catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(
                    false,
                    () -> "symbolic links are unavailable: " + exception.getClass().getName());
        }
    }
}
