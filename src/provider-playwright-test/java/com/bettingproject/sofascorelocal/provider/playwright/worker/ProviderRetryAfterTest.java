package com.bettingproject.sofascorelocal.provider.playwright.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class ProviderRetryAfterTest {
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    @Test void parsesDelayAndHttpDateWithoutKeepingRawHeaders() {
        assertThat(ProviderRetryAfter.deadline("120", NOW)).isEqualTo(NOW.plusSeconds(120).toEpochMilli());
        assertThat(ProviderRetryAfter.deadline("Wed, 09 Sep 2026 12:02:00 GMT", NOW))
                .isEqualTo(NOW.plusSeconds(120).toEpochMilli());
        assertThat(ProviderRetryAfter.deadline("0", NOW)).isEqualTo(NOW.toEpochMilli());
        assertThat(ProviderRetryAfter.deadline("Wed, 09 Sep 2026 11:59:00 GMT", NOW)).isEqualTo(NOW.toEpochMilli());
    }
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"-1", "1.5", "120, 240", "9999999999", "1\r\nsecret:value", "cookie=canary", "Wed, 09 Sep 2026 12:02:00 +0200", "Mon, 01 Jan 2040 00:00:00 GMT"})
    void rejectsUnboundedOrInvalidValuesAsUnknown(String value) {
        assertThat(ProviderRetryAfter.deadline(value, NOW)).isEqualTo(-1);
    }
}
