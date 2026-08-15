package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record J5ParseResult<T extends J5EventData>(
        J5ParseStatus status,
        J5ParseEvidence evidence,
        Optional<T> data,
        Optional<J5CompletenessReport> completeness,
        List<J5ParseWarning> warnings,
        List<J5ParseProblem> problems) {

    public J5ParseResult {
        status = Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence");
        data = Objects.requireNonNull(data, "data");
        completeness = Objects.requireNonNull(completeness, "completeness");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        if (status == J5ParseStatus.PARSED) {
            if (data.isEmpty() || completeness.isEmpty() || !problems.isEmpty()) {
                throw new IllegalArgumentException(
                        "a parsed J5 result requires data, completeness and no problems");
            }
        }
        else if (data.isPresent() || completeness.isPresent() || problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "a failed J5 result requires problems and no partial data");
        }
    }

    public static <T extends J5EventData> J5ParseResult<T> parsed(
            J5ParseEvidence evidence,
            T data,
            J5CompletenessReport completeness,
            List<J5ParseWarning> warnings) {
        return new J5ParseResult<>(
                J5ParseStatus.PARSED,
                evidence,
                Optional.of(data),
                Optional.of(completeness),
                warnings,
                List.of());
    }

    public static <T extends J5EventData> J5ParseResult<T> failed(
            J5ParseStatus status,
            J5ParseEvidence evidence,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        return new J5ParseResult<>(
                status,
                evidence,
                Optional.empty(),
                Optional.empty(),
                warnings,
                problems);
    }
}
