package com.bettingproject.sofascorelocal.cli;

import com.bettingproject.sofascorelocal.SofascoreLocalApplication;
import com.bettingproject.sofascorelocal.application.retention.J6RawPayloadRetentionService;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionException;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.retention.J6BackupEvidence;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionExecutionRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * One-shot, non-web operator entrypoint. It is intentionally absent from the dashboard.
 */
public final class J6RetentionCommand {

    private static final String MODE_ENV = "SOFASCORE_J6_RETENTION_MODE";

    private J6RetentionCommand() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SofascoreLocalApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "spring.main.banner-mode", "off",
                "sofascore.enabled", "false",
                "sofascore.j3-qualification-enabled", "false",
                "sofascore.j4-event-details-qualification-enabled", "false",
                "sofascore.j4-event-details-phase2-enabled", "false",
                "sofascore.j5-event-data-qualification-enabled", "false"));
        int exitCode = 0;
        try (var context = application.run(args)) {
            requireSafeContext(
                    context.getBean(SofascoreProperties.class),
                    context.getBean(JdbcTemplate.class));
            J6RawPayloadRetentionService service = context.getBean(
                    J6RawPayloadRetentionService.class);
            String mode = environment(MODE_ENV, "PREVIEW").toUpperCase(Locale.ROOT);
            if ("PREVIEW".equals(mode)) {
                printPreview(service);
            }
            else if ("EXECUTE".equals(mode)) {
                execute(service);
            }
            else {
                throw new IllegalArgumentException("unsupported J6 retention mode");
            }
        }
        catch (J6RetentionException exception) {
            System.err.println("J6_RETENTION_RESULT=REFUSED");
            System.err.println("J6_RETENTION_ERROR=" + exception.error().name());
            exitCode = 2;
        }
        catch (RuntimeException exception) {
            System.err.println("J6_RETENTION_RESULT=FAILED");
            System.err.println("J6_RETENTION_ERROR=LOCAL_EXECUTION_FAILURE");
            exitCode = 3;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void printPreview(J6RawPayloadRetentionService service) {
        var preview = service.preview();
        System.out.println("J6_RETENTION_MODE=PREVIEW");
        System.out.println("J6_RETENTION_DAYS=" + preview.retentionDays());
        System.out.println("J6_RETENTION_GENERATED_AT=" + preview.generatedAt());
        System.out.println("J6_RETENTION_CUTOFF_AT=" + preview.cutoffAt());
        System.out.println("J6_RETENTION_TOTAL_ELIGIBLE=" + preview.totalEligibleCount());
        System.out.println("J6_RETENTION_TOTAL_BYTES=" + preview.totalEligibleBytes());
        System.out.println("J6_RETENTION_SELECTED=" + preview.selectedCount());
        System.out.println("J6_RETENTION_SELECTED_BYTES=" + preview.selectedPayloadBytes());
        System.out.println("J6_RETENTION_TRUNCATED=" + preview.truncated());
        System.out.println("J6_RETENTION_PLAN_SHA256=" + preview.planSha256());
        if (preview.selectedCount() > 0) {
            System.out.println("J6_RETENTION_CONFIRMATION=" + preview.confirmationPhrase());
        }
        System.out.println("J6_RETENTION_MUTATION=NO");
    }

    private static void execute(J6RawPayloadRetentionService service) {
        J6BackupEvidence backup = new J6BackupEvidence(
                required("SOFASCORE_J6_BACKUP_MANIFEST_SHA256"),
                required("SOFASCORE_J6_BACKUP_CIPHER_SHA256"),
                Instant.parse(required("SOFASCORE_J6_BACKUP_QUALIFIED_AT")),
                Long.parseLong(required("SOFASCORE_J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID")),
                Instant.parse(required("SOFASCORE_J6_BACKUP_COVERAGE_RECEIVED_AT")),
                Boolean.parseBoolean(required(
                        "SOFASCORE_J6_BACKUP_RESTORED_AND_QUALIFIED")));
        var result = service.execute(new J6RetentionExecutionRequest(
                Instant.parse(required("SOFASCORE_J6_RETENTION_CUTOFF_AT")),
                required("SOFASCORE_J6_RETENTION_PLAN_SHA256"),
                required("SOFASCORE_J6_RETENTION_CONFIRMATION"),
                backup));
        System.out.println("J6_RETENTION_MODE=EXECUTE");
        System.out.println("J6_RETENTION_RESULT=PURGED");
        System.out.println("J6_RETENTION_BATCH_ID=" + result.batchId());
        System.out.println("J6_RETENTION_PLAN_SHA256=" + result.planSha256());
        System.out.println("J6_RETENTION_PURGED=" + result.purgedPayloadCount());
        System.out.println("J6_RETENTION_PURGED_BYTES=" + result.purgedPayloadBytes());
        System.out.println("J6_RETENTION_EXECUTED_AT=" + result.executedAt());
    }

    private static void requireSafeContext(
            SofascoreProperties properties,
            JdbcTemplate jdbcTemplate) {
        if (properties.isEnabled()
                || properties.isJ3QualificationEnabled()
                || properties.isJ4EventDetailsQualificationEnabled()
                || properties.isJ4EventDetailsPhase2Enabled()
                || properties.isJ5EventDataQualificationEnabled()
                || properties.isAutomaticRefreshEnabled()
                || properties.isLivePollingEnabled()) {
            throw new IllegalStateException(
                    "J6 retention requires every connector and automatic path to be disabled");
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
                    "J6 retention requires the persisted connector control to be locked");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required J6 retention environment value");
        }
        return value.trim();
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
