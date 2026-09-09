package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup.Phase;

/** Called only under the campaign's I/O lock; no group may be reopened or repeated. */
final class LiveProviderGroupTracker {
    enum Authority { LIVE_V4, LIVE_V5, LIVE_V6, MANUAL_J5 }
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
    boolean usesOneSecondInterGroupDelay() { return authority == Authority.LIVE_V5 || authority == Authority.LIVE_V6; }
    boolean isLiveV6() { return authority == Authority.LIVE_V6; }

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
        if (startedGroups.contains(group.groupId()) || startedGroups.size()
                >= (usesOneSecondInterGroupDelay() ? MAXIMUM_V5_GROUPS : MAXIMUM_V4_GROUPS)) fail();
        // Ordinary groups open with J4. Only a pending final collection can open
        // directly on its first remaining J5 family after a previous group ended.
        if (group.phase() != Phase.FINALIZING && (group.phase() != Phase.CHECK || index != 0)) fail();
        return false;
    }

    private boolean permitsSkippedFamilies(Phase phase) {
        // Only v6 can omit an unavailable in-play family from the server's group.
        // Strictly increasing indices above still reject repeats and backtracking.
        return phase == Phase.PREMATCH || authority == Authority.LIVE_V6 && phase == Phase.IN_PLAY;
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
        if (!isLiveV6()) fail();
        // History is deliberately retained: the interrupted group can never reopen.
        current = null;
        previousIndex = -1;
        previousResponseUsable = false;
    }

    private static boolean permitted(Phase phase, int index) {
        return switch (phase) {
            case CHECK -> index == 0;
            case PREMATCH -> index == 3;
            case IN_PLAY, FINALIZING -> index >= 1;
            case MANUAL_J5 -> false;
        };
    }

    private static void fail() { throw new PlaywrightProviderException(PlaywrightProviderFailure.INVALID_REQUEST); }
}
