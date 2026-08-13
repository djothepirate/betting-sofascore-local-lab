package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedQualificationEvidence;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Keeps only the latest minimized proof in memory. It never reads or renders raw payload bytes.
 */
@Service
public class J3QualificationEvidenceService {

    private EvidenceDocument latest;

    public synchronized void publish(J3MinimizedQualificationEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        latest = new EvidenceDocument(
                evidence,
                format(evidence),
                "J3-MINIMIZED-EVIDENCE-" + evidence.qualificationDate() + ".txt");
    }

    public synchronized Optional<EvidenceDocument> latestDocument() {
        return Optional.ofNullable(latest);
    }

    private static String format(J3MinimizedQualificationEvidence evidence) {
        StringBuilder report = new StringBuilder();
        line(report, "J3_MINIMIZED_EVIDENCE_VERSION", "2");
        line(report, "GENERATED_AT", evidence.generatedAt());
        line(report, "QUALIFICATION_DATE", evidence.qualificationDate());
        line(report, "QUALIFICATION_SCOPE", "PAGES_1_2_3_4_5");
        line(report, "VERIFIED_LOCAL_CHECKPOINT_PAGES", pageRange(
                1,
                evidence.initialCompletedPages()));
        line(report, "PROVIDER_RESUME_FIRST_PAGE", evidence.initialCompletedPages() + 1);
        line(report, "TERMINAL_STATE", evidence.terminalState());
        line(report, "PAGES_ATTEMPTED", evidence.pageAttempts().stream()
                .map(attempt -> Integer.toString(attempt.page()))
                .collect(Collectors.joining(",")));
        line(report, "PAGES_COMPLETED", evidence.completedPages());
        line(report, "FAILED_PAGE", value(evidence.failedPage()));
        line(report, "TERMINAL_CODE", evidence.terminalCode());
        line(report, "FINAL_GLOBAL_STOP", evidence.globalStopActive() ? "ACTIVE" : "INACTIVE");
        line(report, "FINAL_CIRCUIT_STATE", evidence.finalCircuitState());
        line(report, "FINAL_CIRCUIT_REASON", evidence.finalCircuitReason());
        line(report, "AUTOMATIC_RETRY_EXECUTED", "NO");
        line(report, "COOKIES_TOKENS_ACCOUNT_SESSION_USED", "NO");

        for (J3MinimizedPageEvidence attempt : evidence.pageAttempts()) {
            String prefix = "PAGE_" + attempt.page() + "_";
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
            line(report, prefix + "TERMINAL_CODE", value(attempt.terminalCode()));
        }

        line(report, "RAW_PAYLOAD_INCLUDED", "NO");
        line(report, "PROVIDER_URI_INCLUDED", "NO");
        line(report, "REQUEST_OR_RESPONSE_HEADERS_INCLUDED", "NO");
        line(report, "CONFIRMATION_IDENTIFIER_INCLUDED", "NO");
        return report.toString();
    }

    private static String pageRange(int firstPage, int lastPage) {
        if (lastPage < firstPage) {
            return "NONE";
        }
        return java.util.stream.IntStream.rangeClosed(firstPage, lastPage)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
    }

    private static void line(StringBuilder report, String key, Object value) {
        report.append(key).append('=').append(value).append(System.lineSeparator());
    }

    private static String value(Object value) {
        return value == null ? "NONE" : value.toString();
    }

    public record EvidenceDocument(
            J3MinimizedQualificationEvidence evidence,
            String reportText,
            String filename) {

        public EvidenceDocument {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(reportText, "reportText");
            Objects.requireNonNull(filename, "filename");
        }
    }
}
