package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallControlSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;

import java.util.List;

public record ManualCallControlView(
        boolean globalStopActive,
        boolean operatorActivated,
        boolean incidentActive,
        String circuitState,
        String circuitReason,
        String circuitChangedAt,
        String retryNotBefore,
        String suggestedDate,
        boolean canRearm,
        boolean canActivate,
        boolean canPrepare,
        boolean providerTransportAvailable,
        boolean realCallEnabled,
        List<String> providerBlockers,
        IntentView intent) {

    public ManualCallControlView {
        providerBlockers = List.copyOf(providerBlockers);
    }

    public static ManualCallControlView from(J3ManualCallControlSnapshot source) {
        J3ManualCallIntentSnapshot sourceIntent = source.intent();
        boolean activeIntent = sourceIntent != null
                && (sourceIntent.state() == J3ManualCallIntentState.AWAITING_CONFIRMATION
                || sourceIntent.state() == J3ManualCallIntentState.CONFIRMED_BLOCKED
                || sourceIntent.state() == J3ManualCallIntentState.CONFIRMED_READY
                || sourceIntent.state() == J3ManualCallIntentState.EXECUTING);
        boolean qualificationConsumed = source.providerBlockers().contains(
                "J3_QUALIFICATION_ALREADY_CONSUMED");
        boolean realCallEnabled = source.providerTransportAvailable()
                && sourceIntent != null
                && sourceIntent.state() == J3ManualCallIntentState.CONFIRMED_READY;
        return new ManualCallControlView(
                source.globalStopActive(),
                source.operatorActivated(),
                source.incidentActive(),
                source.circuitState().name(),
                source.circuitReason().name(),
                source.circuitChangedAt().toString(),
                source.retryNotBefore() == null ? "NONE" : source.retryNotBefore().toString(),
                source.suggestedDate().toString(),
                source.globalStopActive(),
                !source.globalStopActive() && source.circuitState() == J3CircuitState.LOCKED,
                source.operatorActivated() && !activeIntent && !qualificationConsumed,
                source.providerTransportAvailable(),
                realCallEnabled,
                source.providerBlockers(),
                sourceIntent == null ? null : IntentView.from(sourceIntent));
    }

    public record IntentView(
            String requestId,
            String date,
            String requestKey,
            String state,
            String confirmationPhrase,
            String preparedAt,
            String expiresAt,
            String confirmedAt,
            int completedPages,
            String failedPage,
            String terminalCode,
            boolean awaitingConfirmation,
            boolean confirmedBlocked,
            boolean confirmedReady,
            boolean executing,
            boolean completed,
            boolean failed) {

        private static IntentView from(J3ManualCallIntentSnapshot source) {
            return new IntentView(
                    source.requestId().toString(),
                    source.date().toString(),
                    source.requestKey(),
                    source.state().name(),
                    source.confirmationPhrase(),
                    source.preparedAt().toString(),
                    source.expiresAt().toString(),
                    source.confirmedAt() == null ? "NONE" : source.confirmedAt().toString(),
                    source.completedPages(),
                    source.failedPage() == null ? "NONE" : source.failedPage().toString(),
                    source.terminalCode() == null ? "NONE" : source.terminalCode(),
                    source.state() == J3ManualCallIntentState.AWAITING_CONFIRMATION,
                    source.state() == J3ManualCallIntentState.CONFIRMED_BLOCKED,
                    source.state() == J3ManualCallIntentState.CONFIRMED_READY,
                    source.state() == J3ManualCallIntentState.EXECUTING,
                    source.state() == J3ManualCallIntentState.COMPLETED,
                    source.state() == J3ManualCallIntentState.FAILED);
        }
    }
}
