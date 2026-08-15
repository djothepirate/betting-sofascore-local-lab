package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class J5JsonParserSupport {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private J5JsonParserSupport() {
    }

    static JsonNode readTree(byte[] payload) throws JacksonException {
        return JSON_MAPPER.readTree(payload);
    }

    static void warnUnknownFields(
            JsonNode node,
            Set<String> knownFields,
            String path,
            List<J5ParseWarning> warnings) {
        node.properties().stream()
                .map(entry -> entry.getKey())
                .filter(field -> !knownFields.contains(field))
                .sorted()
                .forEach(field -> warnings.add(warning(
                        J5ParseWarning.Code.UNKNOWN_FIELD,
                        path + "." + field,
                        "Unknown field ignored by the J5 offline parser")));
    }

    static boolean requiredObject(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required object is missing"));
            return false;
        }
        if (!node.isObject()) {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be an object"));
            return false;
        }
        return true;
    }

    static boolean requiredArray(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required array is missing"));
            return false;
        }
        if (!node.isArray()) {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be an array"));
            return false;
        }
        return true;
    }

    static Long requiredPositiveLong(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        Long value = requiredLong(node, path, problems);
        if (value != null && value < 1) {
            problems.add(problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Identifier must be positive"));
            return null;
        }
        return value;
    }

    static Integer requiredInteger(
            JsonNode node,
            String path,
            int minimum,
            int maximum,
            List<J5ParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required integer is missing"));
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be a 32-bit integer"));
            return null;
        }
        int value = node.intValue();
        if (value < minimum || value > maximum) {
            problems.add(problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Integer is outside the accepted range"));
            return null;
        }
        return value;
    }

    static Optional<Integer> optionalInteger(
            JsonNode node,
            String path,
            int minimum,
            int maximum,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        return Optional.ofNullable(requiredInteger(node, path, minimum, maximum, problems));
    }

    static Optional<Long> optionalPositiveLong(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        return Optional.ofNullable(requiredPositiveLong(node, path, problems));
    }

    static Boolean requiredBoolean(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required boolean is missing"));
            return null;
        }
        if (!node.isBoolean()) {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be a boolean"));
            return null;
        }
        return node.booleanValue();
    }

    static Optional<Boolean> optionalBoolean(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        return Optional.ofNullable(requiredBoolean(node, path, problems));
    }

    static String requiredText(
            JsonNode node,
            String path,
            int maximumLength,
            List<J5ParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required text is missing"));
            return null;
        }
        if (!node.isString()) {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be text"));
            return null;
        }
        String value = node.stringValue().trim();
        if (value.isEmpty()) {
            problems.add(problem(
                    J5ParseProblem.Code.VALUE_OUT_OF_RANGE,
                    path,
                    "Text must not be blank"));
            return null;
        }
        if (value.length() > maximumLength) {
            problems.add(problem(
                    J5ParseProblem.Code.VALUE_TOO_LONG,
                    path,
                    "Text exceeds the documented maximum length"));
            return null;
        }
        return value;
    }

    static Optional<String> optionalText(
            JsonNode node,
            String path,
            int maximumLength,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        return Optional.ofNullable(requiredText(node, path, maximumLength, problems));
    }

    static Optional<String> optionalScalarText(
            JsonNode node,
            String path,
            int maximumLength,
            List<J5ParseWarning> warnings,
            List<J5ParseProblem> problems) {
        if (isMissing(node, path, warnings)) {
            return Optional.empty();
        }
        String value;
        if (node.isString()) {
            value = node.stringValue().trim();
        }
        else if (node.isNumber()) {
            value = new BigDecimal(node.asString()).stripTrailingZeros().toPlainString();
        }
        else {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Statistic values must be text or a JSON number"));
            return Optional.empty();
        }
        if (value.isEmpty() || value.length() > maximumLength) {
            problems.add(problem(
                    value.isEmpty()
                            ? J5ParseProblem.Code.VALUE_OUT_OF_RANGE
                            : J5ParseProblem.Code.VALUE_TOO_LONG,
                    path,
                    "Statistic value must be bounded non-blank text"));
            return Optional.empty();
        }
        return Optional.of(value);
    }

    static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    static J5ParseWarning warning(
            J5ParseWarning.Code code,
            String path,
            String message) {
        return new J5ParseWarning(code, path, message);
    }

    static J5ParseProblem problem(
            J5ParseProblem.Code code,
            String path,
            String message) {
        return new J5ParseProblem(code, path, message);
    }

    private static Long requiredLong(
            JsonNode node,
            String path,
            List<J5ParseProblem> problems) {
        if (node == null || node.isNull()) {
            problems.add(problem(
                    J5ParseProblem.Code.REQUIRED_FIELD_MISSING,
                    path,
                    "Required integer is missing"));
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            problems.add(problem(
                    J5ParseProblem.Code.TYPE_MISMATCH,
                    path,
                    "Field must be a 64-bit integer"));
            return null;
        }
        return node.longValue();
    }

    private static boolean isMissing(
            JsonNode node,
            String path,
            List<J5ParseWarning> warnings) {
        if (isAbsent(node)) {
            warnings.add(warning(
                    J5ParseWarning.Code.OPTIONAL_FIELD_MISSING,
                    path,
                    "Optional J5 field is absent"));
            return true;
        }
        return false;
    }
}
