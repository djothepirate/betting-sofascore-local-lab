package com.bettingproject.sofascorelocal.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "sofascore.playwright")
@Validated
public class ProviderPlaywrightProperties {

    private static final int MAXIMUM_TCP_PORT = 65_535;

    private boolean enabled;
    private Path workerJar;

    @Min(64)
    @Max(512)
    private int maximumHeapMib = 192;

    private Duration startupTimeout = Duration.ofSeconds(30);
    private Duration requestTimeout = Duration.ofSeconds(10);
    private Duration gracefulCloseTimeout = Duration.ofSeconds(5);
    private boolean loopbackQualification;
    private String loopbackOrigin = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getWorkerJar() {
        return workerJar;
    }

    public void setWorkerJar(Path workerJar) {
        this.workerJar = workerJar == null || workerJar.toString().isBlank()
                ? null
                : workerJar;
    }

    public int getMaximumHeapMib() {
        return maximumHeapMib;
    }

    public void setMaximumHeapMib(int maximumHeapMib) {
        this.maximumHeapMib = maximumHeapMib;
    }

    public Duration getStartupTimeout() {
        return startupTimeout;
    }

    public void setStartupTimeout(Duration startupTimeout) {
        this.startupTimeout = startupTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getGracefulCloseTimeout() {
        return gracefulCloseTimeout;
    }

    public void setGracefulCloseTimeout(Duration gracefulCloseTimeout) {
        this.gracefulCloseTimeout = gracefulCloseTimeout;
    }

    public boolean isLoopbackQualification() {
        return loopbackQualification;
    }

    public void setLoopbackQualification(boolean loopbackQualification) {
        this.loopbackQualification = loopbackQualification;
    }

    public String getLoopbackOrigin() {
        return loopbackOrigin;
    }

    public void setLoopbackOrigin(String loopbackOrigin) {
        this.loopbackOrigin = loopbackOrigin == null ? "" : loopbackOrigin.trim();
    }

    @AssertTrue(message = "Playwright timeouts and loopback qualification must remain bounded")
    public boolean isSafeConfiguration() {
        if (!bounded(startupTimeout, Duration.ofSeconds(60))
                || !bounded(requestTimeout, Duration.ofSeconds(60))
                || !bounded(gracefulCloseTimeout, Duration.ofSeconds(5))) {
            return false;
        }
        if (!loopbackQualification) {
            return loopbackOrigin.isEmpty();
        }
        try {
            URI origin = URI.create(loopbackOrigin);
            return "http".equals(origin.getScheme())
                    && "127.0.0.1".equals(origin.getHost())
                    && origin.getPort() >= 1
                    && origin.getPort() <= MAXIMUM_TCP_PORT
                    && origin.getRawUserInfo() == null
                    && origin.getRawQuery() == null
                    && origin.getRawFragment() == null
                    && (origin.getRawPath() == null || origin.getRawPath().isEmpty());
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean bounded(Duration value, Duration maximum) {
        return value != null
                && !value.isZero()
                && !value.isNegative()
                && value.compareTo(maximum) <= 0;
    }
}
