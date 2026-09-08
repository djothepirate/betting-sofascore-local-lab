package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static com.bettingproject.sofascorelocal.application.network.playwright.LiveProviderDispatchGroup.Phase;

/** Called only under the campaign's I/O lock; no group may be reopened or repeated. */
final class LiveProviderGroupTracker {
    private static final int MAXIMUM_GROUPS = 3000;
    private static final List<SofascoreEndpointType> ORDER = List.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS);
    private final UUID campaignId;
    private final Set<UUID> startedGroups = new HashSet<>();
    private LiveProviderDispatchGroup current;
    private int previousIndex = -1;
    private boolean previousResponseUsable;

    LiveProviderGroupTracker(UUID campaignId) { this.campaignId = campaignId; }

    boolean isContinuation(PlaywrightProviderRequest request, LiveProviderDispatchGroup group) {
        if (group == null) return false;
        if (!campaignId.equals(group.campaignId()) || request.eventId() != group.providerEventId()) fail();
        int index = ORDER.indexOf(request.endpoint());
        if (index < 0 || !permitted(group.phase(), index)) fail();
        if (current != null && current.groupId().equals(group.groupId())) {
            if (!previousResponseUsable || current.providerEventId() != group.providerEventId()
                    || index <= previousIndex
                    || group.phase() != Phase.PREMATCH && index != previousIndex + 1
                    || current.phase() != Phase.CHECK && current.phase() != group.phase()) fail();
            return true;
        }
        if (startedGroups.contains(group.groupId()) || startedGroups.size() >= MAXIMUM_GROUPS) fail();
        // Ordinary groups open with J4. Only a pending final collection can open
        // directly on its first remaining J5 family after a previous group ended.
        if (group.phase() != Phase.FINALIZING && (group.phase() != Phase.CHECK || index != 0)) fail();
        return false;
    }

    void dispatched(PlaywrightProviderRequest request, LiveProviderDispatchGroup group) {
        isContinuation(request, group);
        if (group == null) {
            current = null;
            previousIndex = -1;
        } else {
            startedGroups.add(group.groupId());
            current = group;
            previousIndex = ORDER.indexOf(request.endpoint());
        }
        previousResponseUsable = false;
    }

    void finished(boolean usable) { previousResponseUsable = usable; }

    private static boolean permitted(Phase phase, int index) {
        return switch (phase) {
            case CHECK -> index == 0;
            case PREMATCH -> index == 3;
            case IN_PLAY, FINALIZING -> index >= 1;
        };
    }

    private static void fail() { throw new PlaywrightProviderException(PlaywrightProviderFailure.INVALID_REQUEST); }
}
