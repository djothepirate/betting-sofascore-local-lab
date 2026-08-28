package com.bettingproject.sofascorelocal.provider.playwright.worker;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

final class ProviderPlaywrightWorkerConfiguration {

    static final String IPC_PORT = "SOFASCORE_PLAYWRIGHT_IPC_PORT";
    static final String IPC_TOKEN = "SOFASCORE_PLAYWRIGHT_IPC_TOKEN";
    static final String LOOPBACK_ORIGIN = "SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN";
    static final String LOOPBACK_QUALIFICATION = "SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION";
    static final URI PROVIDER_ORIGIN = URI.create("https://www.sofascore.com");

    private final int ipcPort;
    private final String token;
    private final URI origin;

    private ProviderPlaywrightWorkerConfiguration(int ipcPort, String token, URI origin) {
        this.ipcPort = ipcPort;
        this.token = token;
        this.origin = origin;
    }

    static ProviderPlaywrightWorkerConfiguration fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        int ipcPort = parsePort(environment.get(IPC_PORT));
        String token = environment.get(IPC_TOKEN);
        ProviderPlaywrightWorkerProtocol.validateToken(token);
        URI origin = parseOrigin(environment);
        return new ProviderPlaywrightWorkerConfiguration(ipcPort, token, origin);
    }

    int ipcPort() {
        return ipcPort;
    }

    String token() {
        return token;
    }

    URI uriFor(ProviderPlaywrightWorkerProtocol.GetCommand command) {
        String path = switch (command.endpoint()) {
            case SCHEDULED_EVENTS -> "/api/v1/sport/football/scheduled-tournaments/"
                    + isoDate(command.date()) + "/page/" + command.page();
            case TOURNAMENT_SCHEDULED_EVENTS -> "/api/v1/unique-tournament/"
                    + command.tournamentId() + "/scheduled-events/" + isoDate(command.date());
            case EVENT_DETAILS -> "/api/v1/event/" + command.eventId();
        };
        return origin.resolve(path);
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException();
            }
            return port;
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    ProviderPlaywrightWorkerProtocol.FailureCode.INVALID_CONFIGURATION.name(), exception);
        }
    }

    private static URI parseOrigin(Map<String, String> environment) {
        String qualification = environment.get(LOOPBACK_QUALIFICATION);
        String configuredOrigin = environment.get(LOOPBACK_ORIGIN);
        if (!"true".equals(qualification)) {
            if (configuredOrigin != null && !configuredOrigin.isBlank()) {
                throw invalidConfiguration();
            }
            return PROVIDER_ORIGIN;
        }
        if (configuredOrigin == null || configuredOrigin.isBlank()) {
            throw invalidConfiguration();
        }
        try {
            URI candidate = new URI(configuredOrigin);
            if (!"http".equals(candidate.getScheme())
                    || !"127.0.0.1".equals(candidate.getHost())
                    || candidate.getPort() < 1
                    || candidate.getRawUserInfo() != null
                    || candidate.getRawQuery() != null
                    || candidate.getRawFragment() != null
                    || candidate.getRawPath() == null
                    || !candidate.getRawPath().isEmpty()) {
                throw invalidConfiguration();
            }
            return candidate;
        }
        catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    ProviderPlaywrightWorkerProtocol.FailureCode.INVALID_CONFIGURATION.name(), exception);
        }
    }

    private static String isoDate(LocalDate date) {
        return date.toString();
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException(
                ProviderPlaywrightWorkerProtocol.FailureCode.INVALID_CONFIGURATION.name());
    }
}
