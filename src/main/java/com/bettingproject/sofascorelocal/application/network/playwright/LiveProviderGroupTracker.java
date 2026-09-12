package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup.Phase;

/** Called only under the campaign's I/O lock; no group may be reopened or repeated. */
final class LiveProviderGroupTracker {
    enum Authority { LIVE_V4, LIVE_V5, LIVE_V6, LIVE_V7, LIVE_V8, LIVE_V9, LIVE_V10, MANUAL_J5 }
    private static final int MAXIMUM_V4_GROUPS = 3000;
    private static final int MAXIMUM_V5_GROUPS = 20000;
    private static final List<SofascoreEndpointType> ORDER = List.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS);
    private final UUID campaignId;
    private final Authority authority;
    private static final List<SofascoreEndpointType> MANUAL_ORDER = List.of(
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);
    private final Set<UUID> startedGroups = new HashSet<>();
    private LiveProviderDispatchGroup current;
    private int previousIndex = -1;
    private boolean previousResponseUsable;

    LiveProviderGroupTracker(UUID campaignId) { this(campaignId, Authority.LIVE_V4); }
    LiveProviderGroupTracker(UUID campaignId, Authority authority) {
        this.campaignId = campaignId;
        this.authority = authority;
    }

    /** The tracker instance is also the supervisor-owned session identity. */
    boolean usesOneSecondInterGroupDelay() { return authority == Authority.LIVE_V5 || authority == Authority.LIVE_V6 || authority == Authority.LIVE_V7; }
    java.time.Duration interGroupMinimumDelay() {
        return authority == Authority.LIVE_V8 || authority == Authority.LIVE_V9 || authority == Authority.LIVE_V10
                ? java.time.Duration.ofMillis(500)
                : usesOneSecondInterGroupDelay() ? java.time.Duration.ofSeconds(1) : null;
    }
    /** V8, V9 and V10 charge a qualified terminal fence after every family exchange. */
    boolean requiresPostExchangeFenceForContinuation() {
        return authority == Authority.LIVE_V8 || authority == Authority.LIVE_V9 || authority == Authority.LIVE_V10;
    }
    boolean isLiveV6() { return authority == Authority.LIVE_V6; }
    boolean isLiveV9() { return authority == Authority.LIVE_V9; }
    boolean usesConditionalRevalidation() { return authority == Authority.LIVE_V9 || authority == Authority.LIVE_V10; }
    boolean supportsProvenTimeoutRecovery() {
        return authority == Authority.LIVE_V6 || authority == Authority.LIVE_V7
                || authority == Authority.LIVE_V8 || authority == Authority.LIVE_V9 || authority == Authority.LIVE_V10;
    }

    boolean isContinuation(PlaywrightProviderRequest request, LiveProviderDispatchGroup group) {
        if (authority == Authority.MANUAL_J5) return manualContinuation(request, group);
        if (group == null) return false;
        if (!campaignId.equals(group.campaignId()) || request.eventId() != group.providerEventId()) fail();
        int index = ORDER.indexOf(request.endpoint());
        if (index < 0 || !permitted(group.phase(), index)) fail();
        if (current != null && current.groupId().equals(group.groupId())) {
            if (!previousResponseUsable || current.providerEventId() != group.providerEventId()
                    || index <= previousIndex
                    || !permitsSkippedFamilies(group.phase()) && index != previousIndex + 1
                    || current.phase() != Phase.CHECK && current.phase() != group.phase()) fail();
            return true;
        }
        if (startedGroups.contains(group.groupId()) || startedGroups.size() >= maximumGroups()) fail();
        // Ordinary groups open with J4. Only a pending final collection can open
        // directly on its first remaining J5 family after a previous group ended.
        boolean standalonePrematchLineups = (authority == Authority.LIVE_V7 || authority == Authority.LIVE_V8
                || authority == Authority.LIVE_V9 || authority == Authority.LIVE_V10)
                && group.phase() == Phase.PREMATCH && index == 3;
        if (!standalonePrematchLineups && group.phase() != Phase.FINALIZING && (group.phase() != Phase.CHECK || index != 0)) fail();
        return false;
    }

    private boolean permitsSkippedFamilies(Phase phase) {
        // Only v6 can omit an unavailable in-play family from the server's group.
        // Strictly increasing indices above still reject repeats and backtracking.
        return phase == Phase.PREMATCH || supportsProvenTimeoutRecovery() && phase == Phase.IN_PLAY;
    }

    /** The V8/V9/V10 rate envelopes are enforced by the durable departure store, while the
     * tracker still retains no more than the manifest's 20,000 possible groups. */
    private int maximumGroups() {
        return switch (authority) {
            case LIVE_V5, LIVE_V6, LIVE_V7, LIVE_V8, LIVE_V9, LIVE_V10 -> MAXIMUM_V5_GROUPS;
            case LIVE_V4, MANUAL_J5 -> MAXIMUM_V4_GROUPS;
        };
    }

    private boolean manualContinuation(PlaywrightProviderRequest request, LiveProviderDispatchGroup group) {
        if (group == null || group.phase() != Phase.MANUAL_J5
                || !campaignId.equals(group.campaignId()) || request.eventId() != group.providerEventId()) fail();
        int index = MANUAL_ORDER.indexOf(request.endpoint());
        if (index < 0) fail();
        if (current == null) {
            if (!startedGroups.isEmpty() || index != 0) fail();
            return false;
        }
        if (!current.groupId().equals(group.groupId()) || current.providerEventId() != group.providerEventId()
                || !previousResponseUsable || index != previousIndex + 1) fail();
        return true;
    }

    void dispatched(PlaywrightProviderRequest request, LiveProviderDispatchGroup group) {
        isContinuation(request, group);
        if (group == null) {
            current = null;
            previousIndex = -1;
        } else {
            startedGroups.add(group.groupId());
            current = group;
            previousIndex = (authority == Authority.MANUAL_J5 ? MANUAL_ORDER : ORDER).indexOf(request.endpoint());
        }
        previousResponseUsable = false;
    }

    void finished(boolean usable) { previousResponseUsable = usable; }

    /** Only after the supervisor proves that a v6 timeout exchange has terminated. */
    void finishedRecoverableTimeout() {
        if (!supportsProvenTimeoutRecovery()) fail();
        // History is deliberately retained: the interrupted group can never reopen.
        current = null;
        previousIndex = -1;
        previousResponseUsable = false;
    }

    private boolean permitted(Phase phase, int index) {
        return switch (phase) {
            case CHECK -> index == 0;
            case PREMATCH -> authority == Authority.LIVE_V7 || authority == Authority.LIVE_V8
                    || authority == Authority.LIVE_V9 || authority == Authority.LIVE_V10 ? index >= 1 : index == 3;
            case IN_PLAY, FINALIZING -> index >= 1;
            case MANUAL_J5 -> false;
        };
    }

    private static void fail() { throw new PlaywrightProviderException(PlaywrightProviderFailure.INVALID_REQUEST); }
}
