package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup.Phase.*;
import static org.assertj.core.api.Assertions.*;

class LiveProviderGroupTrackerTest {
    private final UUID campaign = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();
    private final long event = 16416319L;
    private final LiveProviderGroupTracker tracker = new LiveProviderGroupTracker(campaign);

    @Test
    void orderedFourFamiliesContinueOnlyAfterAUsableResponse() {
        var check = context(CHECK);
        assertThat(tracker.isContinuation(PlaywrightProviderRequest.eventDetails(event), check)).isFalse();
        tracker.dispatched(PlaywrightProviderRequest.eventDetails(event), check);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY)));
        tracker.finished(true);
        dispatch(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY), true);
        dispatch(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY), true);
        dispatch(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY), true);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY)));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventDetails(event), check));
    }

    @Test
    void prematchSkipsIncidentsAndStatisticsButDoesNotPermitThem() {
        dispatch(PlaywrightProviderRequest.eventDetails(event), context(CHECK), false);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event), context(PREMATCH)));
        dispatch(PlaywrightProviderRequest.eventLineups(event), context(PREMATCH), true);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY)));
    }

    @Test
    void inPlayCannotSkipToStatisticsOrLineupsAndCannotChangePhaseMidGroup() {
        dispatch(PlaywrightProviderRequest.eventDetails(event), context(CHECK), false);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY)));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY)));
        dispatch(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY), true);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY)));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(FINALIZING)));
    }

    @Test
    void rejectsCrossCampaignCrossEventAndAnOrdinaryGroupStartingWithoutJ4() {
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventDetails(event),
                new LiveProviderDispatchGroup(UUID.randomUUID(), group, event, CHECK)));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventDetails(event + 1), context(CHECK)));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(PREMATCH)));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY)));
        dispatch(PlaywrightProviderRequest.eventDetails(event), context(CHECK), false);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event + 1),
                new LiveProviderDispatchGroup(campaign, group, event + 1, IN_PLAY)));
    }

    @Test
    void newGroupsAndNormalDispatchCloseThePreviousGroupPermanently() {
        dispatch(PlaywrightProviderRequest.eventDetails(event), context(CHECK), false);
        var next = new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, CHECK);
        dispatch(PlaywrightProviderRequest.eventDetails(event), next, false);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY)));
        tracker.dispatched(PlaywrightProviderRequest.eventDetails(event), null);
        tracker.finished(true);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event),
                new LiveProviderDispatchGroup(campaign, next.groupId(), event, IN_PLAY)));
    }

    @Test
    void finalizationCanStartAtItsFirstRemainingFamilyButKeepsStrictOrder() {
        dispatch(PlaywrightProviderRequest.eventStatistics(event), context(FINALIZING), false);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventIncidents(event), context(FINALIZING)));
        dispatch(PlaywrightProviderRequest.eventLineups(event), context(FINALIZING), true);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(FINALIZING)));
    }

    @Test
    void groupHistoryIsBoundedByTheCampaignAttemptCeiling() {
        for (int index = 0; index < 3000; index++)
            dispatch(PlaywrightProviderRequest.eventDetails(event),
                    new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, CHECK), false);
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventDetails(event), context(CHECK)));
    }

    @Test
    void liveV5HistoryAllowsExactlyTwentyThousandGroupsWithoutReopeningAny() {
        var v5 = new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.LIVE_V5);
        var first = context(CHECK);
        for (int index = 0; index < 20000; index++) {
            var scope = index == 0 ? first
                    : new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, CHECK);
            v5.dispatched(PlaywrightProviderRequest.eventDetails(event), scope);
            v5.finished(true);
        }
        assertInvalid(() -> v5.isContinuation(PlaywrightProviderRequest.eventDetails(event), first));
        assertInvalid(() -> v5.isContinuation(PlaywrightProviderRequest.eventDetails(event),
                new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, CHECK)));
        assertInvalid(() -> v5.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(MANUAL_J5)));
    }

    @Test
    void manualAuthorityIsOneExactJ5TripletAndCannotBeUsedForLiveOrJ4() {
        var manual = new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.MANUAL_J5);
        var scope = context(MANUAL_J5);
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventStatistics(event), null));
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventDetails(event), context(CHECK)));
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventIncidents(event), scope));
        assertInvalid(() -> tracker.isContinuation(PlaywrightProviderRequest.eventStatistics(event), scope));
        assertThat(manual.isContinuation(PlaywrightProviderRequest.eventStatistics(event), scope)).isFalse();
        manual.dispatched(PlaywrightProviderRequest.eventStatistics(event), scope);
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventIncidents(event), scope));
        manual.finished(true);
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventLineups(event), scope));
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventIncidents(event + 1),
                new LiveProviderDispatchGroup(campaign, group, event + 1, MANUAL_J5)));
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventIncidents(event),
                new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, MANUAL_J5)));
        for (var request : java.util.List.of(PlaywrightProviderRequest.eventIncidents(event),
                PlaywrightProviderRequest.eventLineups(event))) {
            assertThat(manual.isContinuation(request, scope)).isTrue();
            manual.dispatched(request, scope);
            manual.finished(true);
        }
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventLineups(event), scope));
        assertInvalid(() -> manual.isContinuation(PlaywrightProviderRequest.eventStatistics(event),
                new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, MANUAL_J5)));
    }

    private LiveProviderDispatchGroup context(LiveProviderDispatchGroup.Phase phase) {
        return new LiveProviderDispatchGroup(campaign, group, event, phase);
    }

    private void dispatch(PlaywrightProviderRequest request, LiveProviderDispatchGroup scope, boolean continuation) {
        assertThat(tracker.isContinuation(request, scope)).isEqualTo(continuation);
        tracker.dispatched(request, scope);
        tracker.finished(true);
    }

    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(PlaywrightProviderException.class)
                .extracting("failure").isEqualTo(PlaywrightProviderFailure.INVALID_REQUEST);
    }
}
