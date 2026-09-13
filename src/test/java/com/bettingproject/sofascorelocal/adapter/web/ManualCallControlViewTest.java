package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManualCallControlViewTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 27);

    @Test
    void keepsPreparationAndLocalImportAvailableWhenOnlyPlaywrightIsBlocked() {
        List<String> playwrightBlockers = List.of(
                "PLAYWRIGHT_RUNTIME_DISABLED",
                "PLAYWRIGHT_WORKER_ARTIFACT_INVALID");
        var readyForPreparation = ManualCallControlView.from(
                new J3ManualCallControlSnapshot(
                        false,
                        J3CircuitState.CLOSED,
                        J3CircuitReason.NONE,
                        NOW,
                        null,
                        DATE,
                        null,
                        false,
                        true,
                        playwrightBlockers));

        assertThat(readyForPreparation.canPrepare()).isTrue();
        assertThat(readyForPreparation.providerTransportAvailable()).isFalse();

        var confirmedForLocalImport = ManualCallControlView.from(
                new J3ManualCallControlSnapshot(
                        false,
                        J3CircuitState.CLOSED,
                        J3CircuitReason.NONE,
                        NOW,
                        null,
                        DATE,
                        confirmedIntent(),
                        false,
                        true,
                        playwrightBlockers));

        assertThat(confirmedForLocalImport.realCallEnabled()).isFalse();
        assertThat(confirmedForLocalImport.localImportEnabled()).isTrue();
        assertThat(confirmedForLocalImport.intent().pageRange()).isEqualTo("1-N (max 35)");
        assertThat(confirmedForLocalImport.providerBlockers())
                .containsExactlyElementsOf(playwrightBlockers);
    }

    private static J3ManualCallIntentSnapshot confirmedIntent() {
        return new J3ManualCallIntentSnapshot(
                UUID.fromString("11f6062f-ab5e-4bcc-bf4e-3647a2823baa"),
                DATE,
                "SCHEDULED_EVENTS|date=2026-08-27|pagination=has-next-page|max=35",
                1,
                J3ManualCallIntentState.CONFIRMED_READY,
                null,
                NOW,
                NOW.plusSeconds(300),
                NOW.plusSeconds(1),
                0,
                null,
                null);
    }
}
