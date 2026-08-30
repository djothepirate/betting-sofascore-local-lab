package com.bettingproject.sofascorelocal.cli;

import com.bettingproject.sofascorelocal.SofascoreLocalApplication;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkMarkdownRenderer;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkService;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;

/** Explicit one-shot, non-Web J8 Markdown export. */
public final class J8BenchmarkExportCommand {

    static final String SAFETY_PROPERTY_SOURCE = "j8BenchmarkExportSafety";
    static final String FROM_ENV = "SOFASCORE_J8_EXPORT_FROM";
    static final String TO_ENV = "SOFASCORE_J8_EXPORT_TO";
    static final String AS_OF_ENV = "SOFASCORE_J8_EXPORT_AS_OF";
    private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;
    private static final DateTimeFormatter FILE_INSTANT = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private J8BenchmarkExportCommand() {
    }

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            requireNoCliArguments(args);
            Instant from = requiredUtcInstant(FROM_ENV);
            Instant to = requiredUtcInstant(TO_ENV);
            Instant asOf = requiredUtcInstant(AS_OF_ENV);
            J8BenchmarkWindow window = J8BenchmarkWindow.between(from, to);
            if (asOf.isBefore(to)) {
                throw new IllegalArgumentException("J8 export asOf cannot precede to");
            }

            SpringApplication application = new SpringApplication(
                    SofascoreLocalApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.addInitializers(context ->
                    installSafetyPropertySource(context.getEnvironment()));

            try (var context = application.run(args)) {
                SofascoreProperties properties = context.getBean(SofascoreProperties.class);
                ProviderPlaywrightProperties playwright = context.getBean(
                        ProviderPlaywrightProperties.class);
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                requireSafeContext(properties, playwright, jdbcTemplate);

                var report = context.getBean(J8BenchmarkService.class).load(window, asOf);
                String markdown = context.getBean(J8BenchmarkMarkdownRenderer.class)
                        .render(report);
                ExportedFile exported = writeCreateNew(
                        Path.of(""), properties.getExportDirectory(), asOf, markdown);
                System.out.println("J8_BENCHMARK_EXPORT_RESULT=CREATED");
                System.out.println("J8_BENCHMARK_EXPORT_PATH=" + exported.path());
                System.out.println("J8_BENCHMARK_EXPORT_SHA256=" + exported.sha256());
                System.out.println("J8_BENCHMARK_EXPORT_NETWORK_CALLS=0");
            }
        }
        catch (IllegalArgumentException exception) {
            System.err.println("J8_BENCHMARK_EXPORT_RESULT=REFUSED");
            System.err.println("J8_BENCHMARK_EXPORT_ERROR=INVALID_EXPORT_PARAMETERS");
            exitCode = 2;
        }
        catch (RuntimeException | IOException exception) {
            System.err.println("J8_BENCHMARK_EXPORT_RESULT=FAILED");
            System.err.println("J8_BENCHMARK_EXPORT_ERROR=LOCAL_EXECUTION_FAILURE");
            exitCode = 3;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static void requireNoCliArguments(String[] args) {
        if (args == null || args.length != 0) {
            throw new IllegalArgumentException(
                    "J8 export does not accept command-line arguments");
        }
    }

    static Map<String, Object> safetyProperties() {
        return Map.ofEntries(
                Map.entry("spring.main.web-application-type", "none"),
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("sofascore.enabled", "false"),
                Map.entry("sofascore.playwright.enabled", "false"),
                Map.entry("sofascore.playwright.loopback-qualification", "false"),
                Map.entry("sofascore.playwright.loopback-origin", ""),
                Map.entry("sofascore.j3-qualification-enabled", "false"),
                Map.entry("sofascore.j4-event-details-qualification-enabled", "false"),
                Map.entry("sofascore.j4-event-details-phase2-enabled", "false"),
                Map.entry("sofascore.j5-event-data-qualification-enabled", "false"),
                Map.entry("sofascore.tournament-event-discovery-enabled", "false"),
                Map.entry("sofascore.automatic-refresh-enabled", "false"),
                Map.entry("sofascore.live-polling-enabled", "false"),
                Map.entry("sofascore.export-directory", "./exports"));
    }

    static void installSafetyPropertySource(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(new MapPropertySource(
                SAFETY_PROPERTY_SOURCE,
                safetyProperties()));
    }

    static void requireSafeContext(
            SofascoreProperties properties,
            ProviderPlaywrightProperties playwright,
            JdbcTemplate jdbcTemplate) {
        if (properties.isEnabled()
                || playwright.isEnabled()
                || playwright.isLoopbackQualification()
                || properties.isJ3QualificationEnabled()
                || properties.isJ4EventDetailsQualificationEnabled()
                || properties.isJ4EventDetailsPhase2Enabled()
                || properties.isJ5EventDataQualificationEnabled()
                || properties.isTournamentEventDiscoveryEnabled()
                || properties.isAutomaticRefreshEnabled()
                || properties.isLivePollingEnabled()) {
            throw new IllegalStateException(
                    "J8 export requires every provider and automatic path to be disabled");
        }
        Boolean persistedLock = jdbcTemplate.queryForObject(
                """
                select not network_enabled and circuit_state = 'LOCKED'
                from connector_control
                where singleton_id = 1
                """,
                Boolean.class);
        if (!Boolean.TRUE.equals(persistedLock)) {
            throw new IllegalStateException(
                    "J8 export requires the persisted connector control to be locked");
        }
    }

    static ExportedFile writeCreateNew(
            Path repositoryRoot,
            Path configuredExportRoot,
            Instant asOf,
            String markdown) throws IOException {
        Path lexicalRepository = repositoryRoot.toAbsolutePath().normalize();
        Path repositoryReal = lexicalRepository.toRealPath();
        Path expectedRoot = lexicalRepository.resolve("exports").normalize();
        Path configuredRoot = configuredExportRoot.isAbsolute()
                ? configuredExportRoot.toAbsolutePath().normalize()
                : lexicalRepository.resolve(configuredExportRoot).normalize();
        if (!configuredRoot.equals(expectedRoot)) {
            throw new IllegalStateException(
                    "J8 export directory must resolve to the repository exports directory");
        }
        Path exportsReal = ensureDirectDirectory(expectedRoot, repositoryReal);
        Path j8Directory = expectedRoot.resolve("j8").normalize();
        if (!j8Directory.getParent().equals(expectedRoot)) {
            throw new IllegalStateException("J8 export path escaped its bounded directory");
        }
        Path j8Real = ensureDirectDirectory(j8Directory, exportsReal);
        Path output = j8Directory.resolve(
                "J8-BENCHMARK-REPORT-" + FILE_INSTANT.format(asOf) + ".md").normalize();
        if (!output.getParent().equals(j8Directory)) {
            throw new IllegalStateException("J8 export file escaped its bounded directory");
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(output.toString());
        }
        requireExistingDirectDirectory(j8Directory, exportsReal, j8Real);
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new ExportedFile(
                lexicalRepository.relativize(output).toString().replace('\\', '/'),
                sha256(bytes));
    }

    private static Path ensureDirectDirectory(
            Path directory,
            Path expectedRealParent) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(directory);
            }
            catch (FileAlreadyExistsException exception) {
                // A concurrent creation is acceptable only if the checks below prove it safe.
            }
        }
        return requireExistingDirectDirectory(directory, expectedRealParent, null);
    }

    private static Path requireExistingDirectDirectory(
            Path directory,
            Path expectedRealParent,
            Path expectedRealDirectory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || isReparsePoint(directory, attributes)) {
            throw new IllegalStateException(
                    "J8 export refuses symbolic links, junctions and reparse points");
        }
        Path realDirectory = directory.toRealPath();
        if (!realDirectory.getParent().equals(expectedRealParent)
                || expectedRealDirectory != null
                        && !realDirectory.equals(expectedRealDirectory)) {
            throw new IllegalStateException(
                    "J8 export directory escaped its physical parent");
        }
        BasicFileAttributes verified = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!verified.isDirectory() || isReparsePoint(directory, verified)) {
            throw new IllegalStateException(
                    "J8 export directory changed during validation");
        }
        return realDirectory;
    }

    private static boolean isReparsePoint(
            Path directory,
            BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            return true;
        }
        try {
            Object dosAttributes = Files.getAttribute(
                    directory,
                    "dos:attributes",
                    LinkOption.NOFOLLOW_LINKS);
            return dosAttributes instanceof Integer flags
                    && (flags & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
        }
        catch (UnsupportedOperationException | IllegalArgumentException exception) {
            return false;
        }
    }

    static Instant requiredUtcInstant(String name) {
        return requiredUtcInstant(name, System.getenv(name));
    }

    static Instant requiredUtcInstant(String name, String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            throw new IllegalArgumentException("missing required J8 export instant");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "J8 export instants cannot contain control characters");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || !normalized.endsWith("Z")) {
            throw new IllegalArgumentException("J8 export instants must be bounded UTC values");
        }
        try {
            return Instant.parse(normalized);
        }
        catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid J8 export instant", exception);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record ExportedFile(String path, String sha256) {
    }
}
