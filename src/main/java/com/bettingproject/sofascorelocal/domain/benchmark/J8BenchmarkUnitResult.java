package com.bettingproject.sofascorelocal.domain.benchmark;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record J8BenchmarkUnitResult(
        long unitId,
        OptionalLong attemptId,
        Instant resolvedAt,
        J8BenchmarkResolutionSource resolutionSource,
        J8BenchmarkOutcomeType outcomeType,
        boolean responseReceived,
        OptionalInt httpStatus,
        OptionalLong latencyMillis,
        OptionalLong snapshotId,
        OptionalLong snapshotOccurrenceId,
        Optional<String> parserVersion,
        Optional<RawSnapshotSchemaStatus> schemaStatus,
        int parserWarningCount,
        Optional<J5CompletenessStatus> completenessStatus,
        OptionalInt completenessScore,
        Optional<String> terminalCode) {

    public J8BenchmarkUnitResult {
        if (unitId < 1) {
            throw new IllegalArgumentException("unitId must be positive");
        }
        attemptId = positive(attemptId, "attemptId");
        resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
        resolutionSource = Objects.requireNonNull(resolutionSource, "resolutionSource");
        outcomeType = Objects.requireNonNull(outcomeType, "outcomeType");
        httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
        latencyMillis = nonNegative(latencyMillis, "latencyMillis");
        snapshotId = positive(snapshotId, "snapshotId");
        snapshotOccurrenceId = positive(snapshotOccurrenceId, "snapshotOccurrenceId");
        parserVersion = J8BenchmarkValidation.optionalParserVersion(parserVersion);
        schemaStatus = Objects.requireNonNull(schemaStatus, "schemaStatus");
        completenessStatus = Objects.requireNonNull(
                completenessStatus, "completenessStatus");
        completenessScore = Objects.requireNonNull(
                completenessScore, "completenessScore");
        terminalCode = J8BenchmarkValidation.optionalSafeCode(terminalCode, "terminalCode");

        if (httpStatus.isPresent()
                && (httpStatus.getAsInt() < 100 || httpStatus.getAsInt() > 599)) {
            throw new IllegalArgumentException("httpStatus must be between 100 and 599");
        }
        if (parserWarningCount < 0) {
            throw new IllegalArgumentException("parserWarningCount cannot be negative");
        }
        if (snapshotId.isEmpty()
                && (parserVersion.isPresent()
                        || schemaStatus.isPresent()
                        || parserWarningCount > 0)) {
            throw new IllegalArgumentException(
                    "parser metadata requires persisted snapshot evidence");
        }
        if (parserWarningCount > 0 && parserVersion.isEmpty()) {
            throw new IllegalArgumentException(
                    "parser warnings require a parser version");
        }
        requireCompletenessShape(completenessStatus, completenessScore);
        requireSourceShape(
                resolutionSource,
                outcomeType,
                responseReceived,
                attemptId,
                httpStatus,
                latencyMillis,
                snapshotId,
                snapshotOccurrenceId,
                parserVersion,
                schemaStatus,
                parserWarningCount,
                completenessStatus,
                completenessScore);
        requireOutcomeShape(
                outcomeType,
                responseReceived,
                httpStatus,
                resolutionSource,
                snapshotId,
                parserVersion,
                schemaStatus);
    }

    private static OptionalLong positive(OptionalLong value, String name) {
        OptionalLong checked = Objects.requireNonNull(value, name);
        if (checked.isPresent() && checked.getAsLong() < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    private static OptionalLong nonNegative(OptionalLong value, String name) {
        OptionalLong checked = Objects.requireNonNull(value, name);
        if (checked.isPresent() && checked.getAsLong() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return checked;
    }

    private static void requireCompletenessShape(
            Optional<J5CompletenessStatus> status,
            OptionalInt score) {
        if (status.isPresent() != score.isPresent()) {
            throw new IllegalArgumentException(
                    "completeness status and score must be present together");
        }
        if (score.isEmpty()) {
            return;
        }
        int value = score.getAsInt();
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("completenessScore must be between 0 and 100");
        }
        boolean valid = switch (status.orElseThrow()) {
            case COMPLETE, EMPTY_VALID -> value == 100;
            case PARTIAL -> value < 100;
            case UNAVAILABLE -> value == 0;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "completeness status and score are inconsistent");
        }
    }

    private static void requireSourceShape(
            J8BenchmarkResolutionSource source,
            J8BenchmarkOutcomeType outcome,
            boolean responseReceived,
            OptionalLong attemptId,
            OptionalInt httpStatus,
            OptionalLong latencyMillis,
            OptionalLong snapshotId,
            OptionalLong snapshotOccurrenceId,
            Optional<String> parserVersion,
            Optional<RawSnapshotSchemaStatus> schemaStatus,
            int parserWarningCount,
            Optional<J5CompletenessStatus> completenessStatus,
            OptionalInt completenessScore) {
        if ((source == J8BenchmarkResolutionSource.PROVIDER) != attemptId.isPresent()) {
            throw new IllegalArgumentException(
                    "only provider results require their exact attemptId");
        }
        if (responseReceived) {
            if (source != J8BenchmarkResolutionSource.PROVIDER
                    || httpStatus.isEmpty()
                    || latencyMillis.isEmpty()) {
                throw new IllegalArgumentException(
                        "a provider response requires HTTP and latency evidence");
            }
        }
        else if (httpStatus.isPresent() || latencyMillis.isPresent()) {
            throw new IllegalArgumentException(
                    "HTTP and latency evidence require a received provider response");
        }
        if (snapshotOccurrenceId.isPresent() && snapshotId.isEmpty()) {
            throw new IllegalArgumentException(
                    "snapshotOccurrenceId requires snapshotId");
        }
        if (source == J8BenchmarkResolutionSource.PROVIDER
                && snapshotId.isPresent()
                && snapshotOccurrenceId.isEmpty()) {
            throw new IllegalArgumentException(
                    "persisted provider snapshots require their exact occurrence");
        }
        if (source == J8BenchmarkResolutionSource.PROVIDER
                && snapshotId.isPresent()
                && !responseReceived) {
            throw new IllegalArgumentException(
                    "persisted provider snapshots require a received response");
        }
        if (source == J8BenchmarkResolutionSource.CACHE
                && (snapshotId.isEmpty()
                        || snapshotOccurrenceId.isPresent()
                        || responseReceived)) {
            throw new IllegalArgumentException(
                    "cache results require a snapshot without a new occurrence");
        }
        if (source == J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT
                && (responseReceived
                        || outcome != J8BenchmarkOutcomeType.PERSISTENCE_FAILURE
                                && (snapshotId.isEmpty() || snapshotOccurrenceId.isEmpty())
                        || outcome == J8BenchmarkOutcomeType.PERSISTENCE_FAILURE
                                && (snapshotId.isPresent() != snapshotOccurrenceId.isPresent()))) {
            throw new IllegalArgumentException(
                    "manual imports require coherent persisted snapshot evidence");
        }
        if (source == J8BenchmarkResolutionSource.BLOCKED
                && (outcome != J8BenchmarkOutcomeType.OPERATOR_STOP
                        && outcome
                                != J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE
                        && outcome != J8BenchmarkOutcomeType.PROCESSING_FAILURE
                        || responseReceived
                        || snapshotId.isPresent()
                        || snapshotOccurrenceId.isPresent()
                        || parserVersion.isPresent()
                        || schemaStatus.isPresent()
                        || parserWarningCount != 0
                        || completenessStatus.isPresent()
                        || completenessScore.isPresent())) {
            throw new IllegalArgumentException(
                    "blocked results cannot claim provider or parser evidence");
        }
        if (outcome == J8BenchmarkOutcomeType.TRANSPORT_FAILURE
                && (snapshotId.isPresent() || snapshotOccurrenceId.isPresent())) {
            throw new IllegalArgumentException(
                    "transport failure cannot claim persisted snapshot evidence");
        }
        if (completenessStatus.isPresent()
                && outcome != J8BenchmarkOutcomeType.PARSED
                && outcome != J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE) {
            throw new IllegalArgumentException(
                    "only parsed or unavailable results can claim completeness");
        }
    }

    private static void requireOutcomeShape(
            J8BenchmarkOutcomeType outcome,
            boolean responseReceived,
            OptionalInt httpStatus,
            J8BenchmarkResolutionSource source,
            OptionalLong snapshotId,
            Optional<String> parserVersion,
            Optional<RawSnapshotSchemaStatus> schemaStatus) {
        if (outcome == J8BenchmarkOutcomeType.TRANSPORT_FAILURE
                && (source != J8BenchmarkResolutionSource.PROVIDER
                        || responseReceived
                        || schemaStatus.isPresent())) {
            throw new IllegalArgumentException(
                    "transport failure cannot claim a provider response or schema outcome");
        }
        if (outcome == J8BenchmarkOutcomeType.HTTP_REFUSED
                && (!responseReceived
                        || httpStatus.isEmpty()
                        || (httpStatus.getAsInt() != 401
                                && httpStatus.getAsInt() != 403
                                && httpStatus.getAsInt() != 429))) {
            throw new IllegalArgumentException(
                    "HTTP_REFUSED requires a 401, 403 or 429 response");
        }
        if (outcome == J8BenchmarkOutcomeType.HTTP_ERROR
                && (!responseReceived
                        || httpStatus.isEmpty()
                        || httpStatus.getAsInt() >= 200 && httpStatus.getAsInt() < 300
                        || httpStatus.getAsInt() == 401
                        || httpStatus.getAsInt() == 403
                        || httpStatus.getAsInt() == 404
                        || httpStatus.getAsInt() == 429)) {
            throw new IllegalArgumentException(
                    "HTTP_ERROR requires a non-success response outside refusal and 404");
        }
        if (outcome == J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE
                && source == J8BenchmarkResolutionSource.PROVIDER
                && (!responseReceived
                        || httpStatus.isEmpty()
                        || httpStatus.getAsInt() != 404)) {
            throw new IllegalArgumentException(
                    "provider ENDPOINT_UNAVAILABLE requires an HTTP 404 response");
        }
        RawSnapshotSchemaStatus required = switch (outcome) {
            case PARSED -> RawSnapshotSchemaStatus.PARSED;
            case ENDPOINT_UNAVAILABLE -> RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE;
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case UNEXPECTED_CONTENT -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
            default -> null;
        };
        if (required != null) {
            if (snapshotId.isEmpty() || parserVersion.isEmpty()) {
                throw new IllegalArgumentException(
                        "parser outcome requires snapshot and parser version evidence");
            }
            if (source == J8BenchmarkResolutionSource.PROVIDER && !responseReceived) {
                throw new IllegalArgumentException(
                        "provider parser outcome requires a received response");
            }
            if (!schemaStatus.filter(required::equals).isPresent()) {
                throw new IllegalArgumentException(
                        "parser outcome requires its matching schemaStatus");
            }
        }
        if (source == J8BenchmarkResolutionSource.PROVIDER
                && (outcome == J8BenchmarkOutcomeType.PARSED
                        || outcome == J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE
                        || outcome == J8BenchmarkOutcomeType.UNEXPECTED_CONTENT)
                && (httpStatus.isEmpty()
                        || httpStatus.getAsInt() < 200
                        || httpStatus.getAsInt() >= 300)) {
            throw new IllegalArgumentException(
                    "provider parser outcomes require an HTTP 2xx response");
        }
        if (outcome == J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE
                && source != J8BenchmarkResolutionSource.BLOCKED) {
            throw new IllegalArgumentException(
                    "not-reached outcomes must be blocked units");
        }
    }
}
