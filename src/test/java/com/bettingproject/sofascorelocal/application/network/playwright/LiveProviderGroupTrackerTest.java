package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest
    @EnumSource(value = LiveProviderGroupTracker.Authority.class, names = {"LIVE_V4", "LIVE_V5"})
    void historicalAuthoritiesStillRejectDeferredInPlayFamilies(LiveProviderGroupTracker.Authority authority) {
        var historical = new LiveProviderGroupTracker(campaign, authority);
        historical.dispatched(PlaywrightProviderRequest.eventDetails(event), context(CHECK));
        historical.finished(true);
        assertInvalid(() -> historical.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY)));
        assertInvalid(() -> historical.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY)));
        historical.dispatched(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY));
        historical.finished(true);
        assertInvalid(() -> historical.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY)));
    }

    @Test
    void liveV6AcceptsEveryOrderedSubsetAfterJ4WithoutAllowingAnyFamilyTwice() {
        var families = java.util.List.of(PlaywrightProviderRequest.eventIncidents(event),
                PlaywrightProviderRequest.eventStatistics(event), PlaywrightProviderRequest.eventLineups(event));
        for (int selected = 1; selected < 8; selected++) {
            var v6 = new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.LIVE_V6);
            assertThat(v6.isContinuation(PlaywrightProviderRequest.eventDetails(event), context(CHECK))).isFalse();
            v6.dispatched(PlaywrightProviderRequest.eventDetails(event), context(CHECK));
            v6.finished(true);
            for (int index = 0; index < families.size(); index++) {
                if ((selected & (1 << index)) == 0) continue;
                var request = families.get(index);
                assertThat(v6.isContinuation(request, context(IN_PLAY))).isTrue();
                v6.dispatched(request, context(IN_PLAY));
                v6.finished(true);
                assertInvalid(() -> v6.isContinuation(request, context(IN_PLAY)));
            }
        }
    }

    @Test
    void liveV6SkippedFamiliesKeepIdentityUsableResponsePhaseAndClosedGroupGuards() {
        var v6 = new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.LIVE_V6);
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY)));
        v6.dispatched(PlaywrightProviderRequest.eventDetails(event), context(CHECK));
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY)));
        v6.finished(true);
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventStatistics(event),
                new LiveProviderDispatchGroup(UUID.randomUUID(), group, event, IN_PLAY)));
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventStatistics(event + 1),
                new LiveProviderDispatchGroup(campaign, group, event + 1, IN_PLAY)));
        v6.dispatched(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY));
        v6.finished(true);
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY)));
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(FINALIZING)));
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(PREMATCH)));
        var next = new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, CHECK);
        v6.dispatched(PlaywrightProviderRequest.eventDetails(event), next);
        v6.finished(true);
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventLineups(event), context(IN_PLAY)));

        var finalContext = new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, FINALIZING);
        v6.dispatched(PlaywrightProviderRequest.eventIncidents(event), finalContext);
        v6.finished(true);
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventLineups(event), finalContext));
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(MANUAL_J5)));
    }

    @Test
    void aProvenV6TimeoutClosesItsGroupPermanentlyButAllowsAFutureGroup() {
        var v6 = new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.LIVE_V6);
        v6.dispatched(PlaywrightProviderRequest.eventDetails(event), context(CHECK));
        v6.finished(true);
        v6.dispatched(PlaywrightProviderRequest.eventIncidents(event), context(IN_PLAY));
        v6.finishedRecoverableTimeout();
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventStatistics(event), context(IN_PLAY)));
        assertInvalid(() -> v6.isContinuation(PlaywrightProviderRequest.eventDetails(event), context(CHECK)));
        var next = new LiveProviderDispatchGroup(campaign, UUID.randomUUID(), event, CHECK);
        assertThat(v6.isContinuation(PlaywrightProviderRequest.eventDetails(event), next)).isFalse();
        v6.dispatched(PlaywrightProviderRequest.eventDetails(event), next);
        v6.finished(true);
        assertThat(v6.isContinuation(PlaywrightProviderRequest.eventIncidents(event),
                new LiveProviderDispatchGroup(campaign, next.groupId(), event, IN_PLAY))).isTrue();
        assertInvalid(tracker::finishedRecoverableTimeout);
        assertInvalid(new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.LIVE_V5)
                ::finishedRecoverableTimeout);
        assertInvalid(new LiveProviderGroupTracker(campaign, LiveProviderGroupTracker.Authority.MANUAL_J5)
                ::finishedRecoverableTimeout);
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

    @ParameterizedTest
    @EnumSource(value = LiveProviderGroupTracker.Authority.class, names = {"LIVE_V5", "LIVE_V6"})
    void laterLiveHistoryAllowsExactlyTwentyThousandGroupsWithoutReopeningAny(LiveProviderGroupTracker.Authority authority) {
        var v5 = new LiveProviderGroupTracker(campaign, authority);
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
