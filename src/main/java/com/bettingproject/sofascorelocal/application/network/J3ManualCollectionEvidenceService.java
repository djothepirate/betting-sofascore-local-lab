package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3PageResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Keeps only the latest minimized proof in memory. It never reads or renders raw payload bytes.
 */
@Service
public class J3ManualCollectionEvidenceService {

    private EvidenceDocument latest;

    public synchronized void publish(J3MinimizedCollectionEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        latest = new EvidenceDocument(
                evidence,
                format(evidence),
                "J3-MINIMIZED-EVIDENCE-" + evidence.collectionDate() + ".txt");
    }

    public synchronized Optional<EvidenceDocument> latestDocument() {
        return Optional.ofNullable(latest);
    }

    private static String format(J3MinimizedCollectionEvidence evidence) {
        StringBuilder report = new StringBuilder();
        line(report, "J3_MINIMIZED_EVIDENCE_VERSION", "4");
        line(report, "GENERATED_AT", evidence.generatedAt());
        line(report, "COLLECTION_DATE", evidence.collectionDate());
        line(report, "PAGINATION_MODE", "HAS_NEXT_PAGE");
        line(report, "CACHE_POLICY", "FRESH_PARSED_SNAPSHOT_FIRST");
        line(report, "CACHE_TTL_SECONDS", evidence.cacheTtl().toSeconds());
        line(report, "PROVIDER_FIRST_PAGE", ScheduledEventsProviderPageRequest.FIRST_PAGE);
        line(report, "MAXIMUM_PAGE_LIMIT",
                ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE);
        line(report, "TERMINAL_STATE", evidence.terminalState());
        line(report, "PAGES_RESOLVED", evidence.pageAttempts().stream()
                .map(attempt -> Integer.toString(attempt.page()))
                .collect(Collectors.joining(",")));
        line(report, "PROVIDER_PAGES_REQUESTED", pagesForSource(
                evidence, J3PageResolutionSource.PROVIDER));
        line(report, "CACHE_HIT_PAGES", pagesForSource(
                evidence, J3PageResolutionSource.CACHE));
        line(report, "PROVIDER_REQUEST_COUNT", evidence.pageAttempts().stream()
                .filter(attempt -> attempt.resolutionSource()
                        == J3PageResolutionSource.PROVIDER)
                .count());
        line(report, "CACHE_HIT_COUNT", evidence.pageAttempts().stream()
                .filter(attempt -> attempt.resolutionSource()
                        == J3PageResolutionSource.CACHE)
                .count());
        line(report, "PAGES_COMPLETED_COUNT", evidence.completedPages());
        line(report, "LAST_COMPLETED_PAGE",
                evidence.completedPages() == 0 ? "NONE" : evidence.completedPages());
        line(report, "FAILED_PAGE", value(evidence.failedPage()));
        line(report, "TERMINAL_CODE", evidence.terminalCode());
        line(report, "FINAL_GLOBAL_STOP", evidence.globalStopActive() ? "ACTIVE" : "INACTIVE");
        line(report, "FINAL_CIRCUIT_STATE", evidence.finalCircuitState());
        line(report, "FINAL_CIRCUIT_REASON", evidence.finalCircuitReason());
        line(report, "AUTOMATIC_RETRY_EXECUTED", "NO");
        line(report, "POLLING_OR_SCHEDULE_EXECUTED", "NO");
        line(report, "COOKIES_TOKENS_ACCOUNT_SESSION_USED", "NO");

        for (J3MinimizedPageEvidence attempt : evidence.pageAttempts()) {
            String prefix = "PAGE_" + attempt.page() + "_";
            line(report, prefix + "RESOLUTION_SOURCE", attempt.resolutionSource());
            line(report, prefix + "RESOLVED_AT", attempt.resolvedAt());
            line(report, prefix + "CACHE_STORED_AT", value(attempt.cacheStoredAt()));
            line(report, prefix + "PROVIDER_REQUEST_EXECUTED",
                    attempt.resolutionSource() == J3PageResolutionSource.PROVIDER
                            ? "YES" : "NO");
            line(report, prefix + "REQUESTED_AT", attempt.requestedAt());
            line(report, prefix + "SNAPSHOT_RECORDED", attempt.snapshotRecorded() ? "YES" : "NO");
            line(report, prefix + "RECEIVED_AT", value(attempt.receivedAt()));
            line(report, prefix + "HTTP_STATUS", value(attempt.httpStatus()));
            line(report, prefix + "LATENCY_MS", value(attempt.latencyMillis()));
            line(report, prefix + "SNAPSHOT_ID", value(attempt.snapshotId()));
            line(report, prefix + "PERSISTENCE_OUTCOME", value(attempt.persistenceOutcome()));
            line(report, prefix + "PAYLOAD_SIZE_BYTES", value(attempt.payloadSizeBytes()));
            line(report, prefix + "PAYLOAD_SHA256", value(attempt.payloadSha256()));
            line(report, prefix + "SCHEMA_STATUS", value(attempt.schemaStatus()));
            line(report, prefix + "HAS_NEXT_PAGE", value(attempt.hasNextPage()));
            line(report, prefix + "TERMINAL_CODE", value(attempt.terminalCode()));
        }

        line(report, "RAW_PAYLOAD_INCLUDED", "NO");
        line(report, "PROVIDER_URI_INCLUDED", "NO");
        line(report, "REQUEST_OR_RESPONSE_HEADERS_INCLUDED", "NO");
        line(report, "CONFIRMATION_IDENTIFIER_INCLUDED", "NO");
        return report.toString();
    }

    private static String pagesForSource(
            J3MinimizedCollectionEvidence evidence,
            J3PageResolutionSource source) {
        String pages = evidence.pageAttempts().stream()
                .filter(attempt -> attempt.resolutionSource() == source)
                .map(attempt -> Integer.toString(attempt.page()))
                .collect(Collectors.joining(","));
        return pages.isEmpty() ? "NONE" : pages;
    }

    private static void line(StringBuilder report, String key, Object value) {
        report.append(key).append('=').append(value).append(System.lineSeparator());
    }

    private static String value(Object value) {
        return value == null ? "NONE" : value.toString();
    }

    public record EvidenceDocument(
            J3MinimizedCollectionEvidence evidence,
            String reportText,
            String filename) {

        public EvidenceDocument {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(reportText, "reportText");
            Objects.requireNonNull(filename, "filename");
        }
    }
}
