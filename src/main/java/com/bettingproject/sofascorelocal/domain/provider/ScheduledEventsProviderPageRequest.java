package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Objects;

public record ScheduledEventsProviderPageRequest(
        URI providerOrigin,
        LocalDate date,
        int page) {

    public static final int FIRST_PAGE = 1;
    /** Historical upper bound of the completed five-page J3 qualification. */
    public static final int LAST_PAGE = 5;
    /** Fail-safe bound for one explicit dynamic manual collection. */
    public static final int MAXIMUM_COLLECTION_PAGE = 25;
    public static final String EXPECTED_ORIGIN = "https://www.sofascore.com";
    public static final LocalDate QUALIFICATION_DATE = LocalDate.parse("2026-08-13");

    public ScheduledEventsProviderPageRequest {
        Objects.requireNonNull(providerOrigin, "providerOrigin");
        Objects.requireNonNull(date, "date");
        requireExactProviderOrigin(providerOrigin);
        if (page < FIRST_PAGE || page > MAXIMUM_COLLECTION_PAGE) {
            throw new IllegalArgumentException("page must be between 1 and 25");
        }
    }

    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.SCHEDULED_EVENTS;
    }

    public String requestKey() {
        return endpointType().name() + "|date=" + date + "|page=" + page;
    }

    public URI targetUri() {
        try {
            return new URI(
                    "https",
                    null,
                    "www.sofascore.com",
                    -1,
                    "/api/v1/sport/football/scheduled-tournaments/"
                            + date + "/page/" + page,
                    null,
                    null);
        }
        catch (URISyntaxException exception) {
            throw new IllegalStateException("validated provider request is not a valid URI", exception);
        }
    }

    public static URI parseExactProviderOrigin(String value) {
        Objects.requireNonNull(value, "value");
        URI origin;
        try {
            origin = URI.create(value.trim());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("provider origin is not a valid URI", exception);
        }
        requireExactProviderOrigin(origin);
        return origin;
    }

    private static void requireExactProviderOrigin(URI origin) {
        boolean rootPath = origin.getRawPath() == null
                || origin.getRawPath().isEmpty()
                || origin.getRawPath().equals("/");
        if (!origin.isAbsolute()
                || !"https".equals(origin.getScheme())
                || !"www.sofascore.com".equals(origin.getHost())
                || origin.getPort() != -1
                || origin.getRawUserInfo() != null
                || !rootPath
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "providerOrigin must be exactly " + EXPECTED_ORIGIN);
        }
    }
}
