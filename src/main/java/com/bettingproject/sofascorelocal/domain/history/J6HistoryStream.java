package com.bettingproject.sofascorelocal.domain.history;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Locale;
import java.util.Optional;

public enum J6HistoryStream {
    EVENT_STATE(null),
    EVENT_DETAILS(SofascoreEndpointType.EVENT_DETAILS),
    EVENT_STATISTICS(SofascoreEndpointType.EVENT_STATISTICS),
    EVENT_INCIDENTS(SofascoreEndpointType.EVENT_INCIDENTS),
    EVENT_LINEUPS(SofascoreEndpointType.EVENT_LINEUPS);

    private final SofascoreEndpointType endpointType;

    J6HistoryStream(SofascoreEndpointType endpointType) {
        this.endpointType = endpointType;
    }

    public Optional<SofascoreEndpointType> endpointType() {
        return Optional.ofNullable(endpointType);
    }

    public static J6HistoryStream parse(String value) {
        if (value == null || value.isBlank() || value.length() > 32) {
            throw new IllegalArgumentException("stream must be a bounded non-blank value");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("stream is not a J6 history stream", exception);
        }
    }
}
