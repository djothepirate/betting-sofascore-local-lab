package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Objects;

public record ScheduledEventsTransportRequest(
        URI loopbackOrigin,
        LocalDate date) {

    static final String SIMULATED_PATH = "/simulated/scheduled-events";

    public ScheduledEventsTransportRequest {
        Objects.requireNonNull(loopbackOrigin, "loopbackOrigin");
        Objects.requireNonNull(date, "date");
        requireExactLoopbackOrigin(loopbackOrigin);
    }

    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.SCHEDULED_EVENTS;
    }

    public String requestKey() {
        return endpointType().name() + "|date=" + date;
    }

    public URI targetUri() {
        try {
            return new URI(
                    "http",
                    null,
                    "127.0.0.1",
                    loopbackOrigin.getPort(),
                    SIMULATED_PATH,
                    "date=" + date,
                    null);
        }
        catch (URISyntaxException exception) {
            throw new IllegalStateException("validated loopback request is not a valid URI", exception);
        }
    }

    private static void requireExactLoopbackOrigin(URI origin) {
        boolean rootPath = origin.getRawPath() == null
                || origin.getRawPath().isEmpty()
                || origin.getRawPath().equals("/");
        if (!origin.isAbsolute()
                || !"http".equals(origin.getScheme())
                || !"127.0.0.1".equals(origin.getHost())
                || origin.getPort() < 1
                || origin.getPort() > 65_535
                || origin.getRawUserInfo() != null
                || !rootPath
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "loopbackOrigin must be exactly http://127.0.0.1:<port>");
        }
    }
}
