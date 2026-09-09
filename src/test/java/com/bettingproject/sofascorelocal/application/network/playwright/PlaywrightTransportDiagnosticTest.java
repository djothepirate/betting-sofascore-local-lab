package com.bettingproject.sofascorelocal.application.network.playwright;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class PlaywrightTransportDiagnosticTest {
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    @Test void refusalHeadersRemainPartialThroughBodyAndParentTimeout() {
        var headers = new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,
                30_000, NOW, NOW.plusMillis(50), 429, NOW.plusSeconds(60), false);
        var body = headers.at(PlaywrightTransportDiagnostic.Phase.READING_BODY);
        var timeout = body.at(PlaywrightTransportDiagnostic.Phase.PARENT_IPC_WAIT);
        assertThat(timeout.httpStatus()).isEqualTo(429);
        assertThat(timeout.retryAfterNotBefore()).isEqualTo(NOW.plusSeconds(60));
        assertThat(timeout.responseComplete()).isFalse();
        assertThat(new PlaywrightProviderException(PlaywrightProviderFailure.IPC_TIMEOUT, timeout).diagnostic())
                .isEqualTo(timeout);
    }
    @Test void unknownStatusCannotBeInventedFromARequestStart() {
        var sent = new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.REQUEST_SENT,
                30_000, NOW, null, null, null, false);
        assertThat(sent.at(PlaywrightTransportDiagnostic.Phase.PARENT_IPC_WAIT).httpStatus()).isNull();
        assertThatThrownBy(() -> sent.at(PlaywrightTransportDiagnostic.Phase.COMPLETE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.REQUEST_SENT,
                30_000, NOW, null, 403, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,
                30_000, NOW, NOW.minusSeconds(1), 403, null, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
