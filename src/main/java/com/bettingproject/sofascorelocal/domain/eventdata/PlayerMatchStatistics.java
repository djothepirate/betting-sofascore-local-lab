package com.bettingproject.sofascorelocal.domain.eventdata;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Numeric source observations. No zero, score or percentage is inferred. */
public record PlayerMatchStatistics(Map<String, BigDecimal> values, Map<String, BigDecimal> ratingVersions) {
    public static final int MAXIMUM_VALUES = 128;
    public static final int MAXIMUM_RATING_VERSIONS = 16;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,79}");

    public PlayerMatchStatistics {
        values = canonicalMap(values, MAXIMUM_VALUES);
        ratingVersions = canonicalMap(ratingVersions, MAXIMUM_RATING_VERSIONS);
    }

    public static boolean validKey(String key) { return key != null && KEY.matcher(key).matches(); }

    public static BigDecimal canonicalNumber(BigDecimal value) {
        BigDecimal canonical = Objects.requireNonNull(value, "numeric value").stripTrailingZeros();
        if (canonical.signum() == 0) canonical = BigDecimal.ZERO;
        if (canonical.precision() > 64 || canonical.scale() < -32 || canonical.scale() > 32)
            throw new IllegalArgumentException("numeric statistic exceeds bounded precision or scale");
        return canonical;
    }

    private static Map<String, BigDecimal> canonicalMap(Map<String, BigDecimal> source, int maximum) {
        Objects.requireNonNull(source, "statistics map");
        if (source.size() > maximum) throw new IllegalArgumentException("too many numeric statistics");
        var sorted = new TreeMap<String, BigDecimal>();
        source.forEach((key, value) -> {
            if (!validKey(key)) throw new IllegalArgumentException("invalid numeric statistic key");
            sorted.put(key, canonicalNumber(value));
        });
        return Collections.unmodifiableMap(sorted);
    }
}
