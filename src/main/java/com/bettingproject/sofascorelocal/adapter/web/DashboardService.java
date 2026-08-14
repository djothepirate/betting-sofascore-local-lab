package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.application.ConnectorGate;
import com.bettingproject.sofascorelocal.application.fixture.FixtureCorpusOverview;
import com.bettingproject.sofascorelocal.application.fixture.OfflineFixtureCorpusService;
import com.bettingproject.sofascorelocal.application.network.J3ProviderQualificationPolicy;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DashboardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardService.class);
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final SofascoreProperties properties;
    private final SofascoreEndpointCatalog endpointCatalog;
    private final ConnectorGate connectorGate;
    private final JdbcTemplate jdbcTemplate;
    private final OfflineFixtureCorpusService fixtureCorpusService;
    private final J3ProviderQualificationPolicy providerPolicy;

    public DashboardService(
            SofascoreProperties properties,
            SofascoreEndpointCatalog endpointCatalog,
            ConnectorGate connectorGate,
            JdbcTemplate jdbcTemplate,
            OfflineFixtureCorpusService fixtureCorpusService,
            J3ProviderQualificationPolicy providerPolicy) {
        this.properties = properties;
        this.endpointCatalog = endpointCatalog;
        this.connectorGate = connectorGate;
        this.jdbcTemplate = jdbcTemplate;
        this.fixtureCorpusService = fixtureCorpusService;
        this.providerPolicy = providerPolicy;
    }

    public DashboardView load() {
        DatabaseOverview database = loadDatabaseOverview();
        FixtureCorpusOverview fixtureCorpus = fixtureCorpusService.loadOverview();
        boolean manualCollectionAvailable = providerPolicy.snapshot().available();
        List<DashboardView.EndpointRowView> endpointRows = endpointCatalog.list().stream()
                .map(definition -> toEndpointRow(definition, manualCollectionAvailable))
                .toList();

        return new DashboardView(
                ISO_INSTANT.format(Instant.now()),
                "EXPERIMENTAL / LOCAL_ONLY / NOT_PRODUCTION_APPROVED / NO_CRITICAL_DEPENDENCY",
                connectorGate.mode().name(),
                properties.isEnabled(),
                manualCollectionAvailable,
                "127.0.0.1:8087",
                properties.getBaseUrl().isBlank() ? "NON_CONFIGURED" : "CONFIGURED_NOT_DISPLAYED",
                properties.getMaximumConcurrency(),
                formatDuration(properties.getMinimumDelay()),
                database.state(),
                database.flywayVersion(),
                database.snapshotCount(),
                database.incidentCount(),
                toFixtureCorpusView(fixtureCorpus),
                database.lastCall(),
                endpointRows);
    }

    private static DashboardView.FixtureCorpusView toFixtureCorpusView(
            FixtureCorpusOverview source) {
        return new DashboardView.FixtureCorpusView(
                source.availability().name(),
                source.family().name(),
                source.origin().name(),
                source.providerSchemaValidated(),
                source.parserVersion(),
                source.declaredCount(),
                source.availableCount(),
                source.parsedCount(),
                source.schemaIncompatibleCount(),
                source.unexpectedContentCount(),
                source.loadingFailureCount());
    }

    private DatabaseOverview loadDatabaseOverview() {
        try {
            Integer health = jdbcTemplate.queryForObject("select 1", Integer.class);
            if (health == null || health != 1) {
                return DatabaseOverview.unavailable();
            }

            Long snapshotCount = jdbcTemplate.queryForObject(
                    "select count(*) from provider_snapshot", Long.class);
            Long incidentCount = jdbcTemplate.queryForObject(
                    """
                    select count(*)
                    from provider_snapshot
                    where http_status in (401, 403, 429)
                       or schema_status = 'SCHEMA_INCOMPATIBLE'
                       or error_code is not null
                    """,
                    Long.class);
            String flywayVersion = jdbcTemplate.query(
                    """
                    select version
                    from flyway_schema_history
                    where success = true
                    order by installed_rank desc
                    limit 1
                    """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : "NONE");

            List<DashboardView.LastCallView> lastCalls = jdbcTemplate.query(
                    """
                    select received_at, logical_endpoint, http_status, latency_ms, payload_sha256
                    from provider_snapshot
                    order by created_at desc
                    limit 1
                    """,
                    (resultSet, rowNumber) -> {
                        java.time.OffsetDateTime receivedAt = resultSet.getObject(
                                "received_at", java.time.OffsetDateTime.class);
                        Number latency = (Number) resultSet.getObject("latency_ms");
                        return new DashboardView.LastCallView(
                                receivedAt == null ? "NOT_RECEIVED" : receivedAt.toInstant().toString(),
                                resultSet.getString("logical_endpoint"),
                                resultSet.getObject("http_status", Integer.class),
                                latency == null ? null : latency.longValue(),
                                abbreviateHash(resultSet.getString("payload_sha256")));
                    });

            return new DatabaseOverview(
                    "AVAILABLE",
                    flywayVersion == null ? "NONE" : flywayVersion,
                    snapshotCount == null ? 0L : snapshotCount,
                    incidentCount == null ? 0L : incidentCount,
                    lastCalls.isEmpty() ? null : lastCalls.get(0));
        }
        catch (DataAccessException exception) {
            LOGGER.warn("Dashboard database overview is unavailable: {}", exception.getMostSpecificCause().getMessage());
            return DatabaseOverview.unavailable();
        }
    }

    private DashboardView.EndpointRowView toEndpointRow(
            SofascoreEndpointDefinition definition,
            boolean manualCollectionAvailable) {
        boolean manuallyCallable = definition.type()
                == com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.SCHEDULED_EVENTS
                && manualCollectionAvailable;
        return new DashboardView.EndpointRowView(
                definition.type().name(),
                formatDuration(definition.cacheTtl()),
                definition.manualOnly(),
                definition.callable() || manuallyCallable,
                definition.uriTemplateConfigured() || manuallyCallable,
                definition.purpose());
    }

    private static String formatDuration(Duration duration) {
        if (duration == null) {
            return "NOT_CONFIGURED";
        }
        if (duration.toHours() > 0 && duration.toMinutesPart() == 0) {
            return duration.toHours() + " h";
        }
        if (duration.toMinutes() > 0 && duration.toSecondsPart() == 0) {
            return duration.toMinutes() + " min";
        }
        return duration.toSeconds() + " s";
    }

    private static String abbreviateHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "NONE";
        }
        return hash.length() <= 16 ? hash : hash.substring(0, 16) + "...";
    }

    private record DatabaseOverview(
            String state,
            String flywayVersion,
            long snapshotCount,
            long incidentCount,
            DashboardView.LastCallView lastCall) {

        private static DatabaseOverview unavailable() {
            return new DatabaseOverview("UNAVAILABLE", "UNKNOWN", 0L, 0L, null);
        }
    }
}
