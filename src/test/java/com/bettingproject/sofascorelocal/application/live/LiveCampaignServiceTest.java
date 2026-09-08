package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightDispatchAdmission;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaign;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderRequest;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.RawManualCallSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveCampaignStore;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Owner-thread wiring tests using only in-process transport and persistence fakes. */
class LiveCampaignServiceTest {
    private static final long A = 17000001L;
    private static final long B = 17000002L;

    @Test
    void emptyAndDuplicateSelectionsAreRejectedBeforeAdmissionOrProviderWork() throws Exception {
        try (Harness h = new Harness()) {
            assertThatThrownBy(() -> h.service.prepare(List.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_SELECTION_INVALID");
            assertThatThrownBy(() -> h.service.prepare(List.of(id(A), id(A))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_SELECTION_INVALID");
            verifyNoInteractions(h.admission, h.events, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void aSyntheticFixtureCannotAuthorizeALiveProviderCampaign() throws Exception {
        try (Harness h = new Harness()) {
            var source = EventSourceTrace.syntheticFixture("live-test-only", "b".repeat(64),
                    "event-details-v2", Instant.now());
            var selected = observation(A, source);
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(selected));
            assertThatThrownBy(() -> h.service.prepare(List.of(id(A))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_PROVIDER_PROVENANCE_REQUIRED");
            verify(h.store, never()).prepare(any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void anUncallableProviderIdIsRejectedWhileStillPreparing() throws Exception {
        try (Harness h = new Harness()) {
            long outOfRange = 1_000_000_000L;
            var source = EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", Instant.now());
            var selected = observation(outOfRange, source);
            when(h.events.findLatestByCanonicalId(id(outOfRange)))
                    .thenReturn(Optional.of(selected));
            assertThatThrownBy(() -> h.service.prepare(List.of(id(outOfRange))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_EVENT_ID_OUT_OF_RANGE");
            verify(h.store, never()).prepare(any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void preparationFreezesTheSelectedObservationAndSnapshotWithoutOpeningATransport() throws Exception {
        try (Harness h = new Harness()) {
            var source = EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", Instant.now());
            var selected = observation(A, source);
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(selected));
            when(h.admission.maximumBytes(1)).thenReturn(5_242_880_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            Manifest prepared = h.service.prepare(List.of(id(A)));

            assertThat(prepared.targets()).containsExactly(new Target(id(A), A, 17, 23));
            assertThat(prepared.maximumBytes()).isEqualTo(5_242_880_000L);
            assertThat(prepared.manifestSha256()).matches("[0-9a-f]{64}");
            assertThat(Duration.between(prepared.preparedAt(), prepared.expiresAt())).isEqualTo(Duration.ofMinutes(5));
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void aLocallyFinishedMatchIsExcludedWithoutAdmissionEvenWhenAllProviderSwitchesAreDisabled() throws Exception {
        try (Harness h = new Harness()) {
            var finished = providerObservation(A, "finished");
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(finished));
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            var preparation = h.service.prepareSelection(List.of(id(A)));

            assertThat(preparation.manifest()).isNull();
            assertThat(preparation.excludedFinished()).containsExactly(finished);
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void anEntireHistoricalDayIsExcludedBeforeTheOneMatchCapacityAndStorageChecks() throws Exception {
        try (Harness h = new Harness()) {
            h.properties.setQualifiedMatchCapacity(1);
            var finished = LongStream.range(A, A + 15).mapToObj(providerId -> {
                var event = providerObservation(providerId, "finished");
                when(h.events.findLatestByCanonicalId(id(providerId))).thenReturn(Optional.of(event));
                return event;
            }).toList();

            var preparation = h.service.prepareSelection(finished.stream().map(event -> event.identity().value()).toList());

            assertThat(preparation.manifest()).isNull();
            assertThat(preparation.excludedFinished()).containsExactlyElementsOf(finished);
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void mixedSelectionAdmitsAndFreezesOnlyTheUnfinishedMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.properties.setQualifiedMatchCapacity(1);
            var finished = providerObservation(A, "finished");
            var active = providerObservation(B, "inprogress");
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(finished));
            when(h.events.findLatestByCanonicalId(id(B))).thenReturn(Optional.of(active));
            when(h.admission.maximumBytes(1)).thenReturn(5_242_880_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var preparation = h.service.prepareSelection(List.of(id(A), id(B)));

            assertThat(preparation.excludedFinished()).containsExactly(finished);
            assertThat(preparation.manifest().targets()).containsExactly(new Target(id(B), B, 17, 23));
            assertThat(preparation.manifest().maximumBytes()).isEqualTo(5_242_880_000L);
            assertThat(preparation.manifest().qualifiedMatchCapacity()).isEqualTo(1);
            verify(h.admission).admit(1);
            verify(h.admission, never()).admit(2);
            verify(h.store).prepare(preparation.manifest());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void anEntirelyPostponedOrFinishedSelectionCreatesNoCampaignOrProviderWork(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            var postponed = providerObservation(A, "postponed");
            var other = providerObservation(B, otherStatus);
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(postponed));
            when(h.events.findLatestByCanonicalId(id(B))).thenReturn(Optional.of(other));
            h.properties.setQualifiedMatchCapacity(1);
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            var preparation = h.service.prepareSelection(List.of(id(A), id(B)));

            assertThat(preparation.manifest()).isNull();
            assertThat(preparation.excludedPostponed()).containsExactlyElementsOf(
                    "postponed".equals(otherStatus) ? List.of(postponed, other) : List.of(postponed));
            assertThat(preparation.excludedFinished()).containsExactlyElementsOf(
                    "finished".equals(otherStatus) ? List.of(other) : List.of());
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void mixedSelectionExcludesFinishedAndPostponedBeforeComputingTheManifestCapacityAndCadence() throws Exception {
        try (Harness h = new Harness()) {
            var finished = providerObservation(A, "finished");
            var postponed = providerObservation(B, "postponed");
            long eligible = B + 1;
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.of(finished));
            when(h.events.findLatestByCanonicalId(id(B))).thenReturn(Optional.of(postponed));
            h.observe(eligible, "notstarted");
            h.properties.setQualifiedMatchCapacity(1);
            when(h.admission.maximumBytes(1)).thenReturn(5_242_880_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var preparation = h.service.prepareSelection(List.of(id(A), id(eligible), id(B)));

            assertThat(preparation.excludedFinished()).containsExactly(finished);
            assertThat(preparation.excludedPostponed()).containsExactly(postponed);
            assertThat(preparation.manifest().targets()).containsExactly(new Target(id(eligible), eligible, 17, 23));
            assertThat(preparation.manifest().qualifiedMatchCapacity()).isEqualTo(1);
            assertThat(preparation.manifest().cycleInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(preparation.manifest().maximumBytes()).isEqualTo(5_242_880_000L);
            verify(h.admission).admit(1);
            verify(h.admission, never()).admit(3);
            verify(h.store).prepare(preparation.manifest());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void theManifestOnlyApiExplainsThatAPostponedOrMixedExcludedSelectionIsIneligible(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "postponed");
            h.observe(B, otherStatus);

            assertThatThrownBy(() -> h.service.prepare(List.of(id(A), id(B))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_ALL_EVENTS_INELIGIBLE");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void canceledRemainsAdmissibleToPreparationWithoutExtendingThePostponedRule() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "canceled");
            when(h.admission.maximumBytes(1)).thenReturn(5_242_880_000L);
            when(h.store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var preparation = h.service.prepareSelection(List.of(id(A)));

            assertThat(preparation.excludedFinished()).isEmpty();
            assertThat(preparation.excludedPostponed()).isEmpty();
            assertThat(preparation.manifest().targets()).containsExactly(new Target(id(A), A, 17, 23));
            verify(h.admission).admit(1);
            verify(h.store).prepare(preparation.manifest());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void aForgedEventInAMixedSelectionIsRejectedBeforeAdmissionAndPersistence() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            UUID missing = id(B + 1);
            when(h.events.findLatestByCanonicalId(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> h.service.prepareSelection(List.of(id(A), id(B), missing)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_EVENT_NOT_FOUND");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void theRawSelectionLimitStillRejectsMoreThanOneHundredEventsBeforeLookingThemUp() throws Exception {
        try (Harness h = new Harness()) {
            var selected = LongStream.range(A, A + 101).mapToObj(LiveCampaignServiceTest::id).toList();
            assertThatThrownBy(() -> h.service.prepareSelection(selected))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_SELECTION_INVALID");
            verifyNoInteractions(h.events, h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void theManifestOnlyPreparationApiDoesNotReturnAnEmptyCampaignForFinishedMatches() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            assertThatThrownBy(() -> h.service.prepare(List.of(id(A))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_ALL_EVENTS_FINISHED");
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).prepare(any());
        }
    }

    @Test
    void matchesFinishedSincePreparationPreventLaunchBeforeOptInAdmissionAndOwnership() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            h.observe(B, "finished");
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_ALL_EVENTS_FINISHED");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).launch(any(), any(), any(), any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void postponedLatestObservationsPreventAnEntirelyExcludedLaunchBeforeOptInOrOwnership(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "postponed");
            h.observe(B, otherStatus);
            h.provider.setEnabled(false);
            h.playwright.setEnabled(false);
            h.properties.setEnabled(false);

            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_ALL_EVENTS_INELIGIBLE");

            assertThat(h.campaignState).hasValue("PREPARED");
            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).launch(any(), any(), any(), any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3"})
    void aMatchPostponedSincePreparationIsSkippedWithoutRewritingTheManifest(String policy) throws Exception {
        try (Harness h = new Harness(false, policy)) {
            h.observe(A, "postponed");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_POSTPONED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.receipts).hasSize(4);
            assertThat(h.reservations.get()).isEqualTo(4);
            assertThat(h.service.state(h.manifest.campaignId()).manifest()).isEqualTo(h.manifest);
            verify(h.admission).admit(1, h.manifest.cycleInterval());
            verify(h.store, never()).prepare(any());
            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.campaign).close();
        }
    }

    @Test
    void aMatchFinishedSincePreparationIsSkippedWhileTheOtherMatchCompletes() throws Exception {
        try (Harness h = new Harness()) {
            h.observe(A, "finished");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.receipts).hasSize(4);
            assertThat(h.reservations.get()).isEqualTo(4);
            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.campaign).close();
        }
    }

    @Test
    void completionDuringOwnerLaunchIsRecheckedBeforeTheBrowserCanOpen() throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> {
                h.observe(A, "finished");
                h.observe(B, "finished");
            };

            h.launch();
            h.awaitFinished();

            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.dispatched).isEmpty();
            assertThat(h.reservations.get()).isZero();
            assertThat(h.receipts).isEmpty();
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"postponed", "finished"})
    void postponementDuringOwnerLaunchIsRecheckedBeforeAnyBrowserOrReservation(String otherStatus) throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> {
                h.observe(A, "postponed");
                h.observe(B, otherStatus);
            };

            h.launch();
            h.awaitFinished();

            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_POSTPONED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("postponed".equals(otherStatus)
                    ? "STOPPED_ALREADY_POSTPONED" : "STOPPED_ALREADY_FINISHED");
            assertThat(h.campaignState).hasValue("COMPLETED");
            assertThat(h.dispatched).isEmpty();
            assertThat(h.reservations.get()).isZero();
            assertThat(h.receipts).isEmpty();
            verifyNoInteractions(h.factory, h.campaign);
        }
    }

    @Test
    void postponementOfOneMatchDuringOwnerLaunchDoesNotDispatchThatMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> h.observe(A, "postponed");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_POSTPONED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.reservations.get()).isEqualTo(4);
        }
    }

    @Test
    void aPostponedJ4ResponseIsPreservedAndStopsOnlyItsMatchWithoutJ5OrFinalCollection() throws Exception {
        try (Harness h = new Harness(false, "live-v3")) {
            h.reply = request -> request.eventId() == A ? response("""
                    {"event":{"id":%d,"startTimestamp":1788796800,
                      "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                      "status":{"type":"postponed"}}}
                    """.formatted(A), 200) : normalFinishedReply(request);

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(A, B, B, B, B);
            assertThat(h.dispatched.getFirst().endpoint()).isEqualTo(EVENT_DETAILS);
            assertThat(h.receipts).hasSize(5);
            assertThat(h.receipts.getFirst().parserVersion()).isEqualTo("event-details-v3");
            assertThat(h.reservations.get()).isEqualTo(5);
            assertThat(h.publications).anySatisfy(publication -> {
                assertThat(publication.sportStatus()).isEqualTo("postponed");
                assertThat(publication.outcome()).isEqualTo("PARSED");
                assertThat(publication.successful()).isTrue();
                assertThat(publication.nextEventState()).isEqualTo("STOPPED_POSTPONED");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_POSTPONED");
            assertThat(h.finalCompleteness.get(id(A))).isFalse();
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.campaignState).hasValue("COMPLETED");
            verify(h.campaign).close();
        }
    }

    @Test
    void completionOfOneMatchDuringOwnerLaunchCannotDispatchThatMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.afterOwnerLaunch = () -> h.observe(A, "finished");
            h.reply = LiveCampaignServiceTest::normalFinishedReply;

            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ALREADY_FINISHED");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.reservations.get()).isEqualTo(4);
        }
    }

    @Test
    void aMissingLocalObservationCannotAuthorizeLaunchingAnOlderManifest() throws Exception {
        try (Harness h = new Harness()) {
            when(h.events.findLatestByCanonicalId(id(A))).thenReturn(Optional.empty());

            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_EVENT_NOT_FOUND");

            verifyNoInteractions(h.admission, h.factory, h.coordinator);
            verify(h.store, never()).launch(any(), any(), any(), any());
            assertThat(h.dispatched).isEmpty();
        }
    }

    @Test
    void anExpiredManifestCannotAcquireTheProviderOrOpenATransport() throws Exception {
        try (Harness h = new Harness(true)) {
            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class).hasMessage("LIVE_MANIFEST_EXPIRED");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @Test
    void changingThePreparedEnvelopeRequiresANewManifestBeforeAnyProviderAcquisition() throws Exception {
        try (Harness h = new Harness()) {
            h.properties.setProcessingEnvelope(Duration.ofSeconds(2));
            assertThatThrownBy(h::launch).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("LIVE_PREPARED_POLICY_CHANGED");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @Test
    void aLiveRequestTimeoutAboveTenSecondsIsRejectedBeforeProviderAcquisition() throws Exception {
        try (Harness h = new Harness()) {
            h.playwright.setRequestTimeout(Duration.ofSeconds(11));
            assertThatThrownBy(h::launch).isInstanceOf(IllegalStateException.class)
                    .hasMessage("LIVE_REQUEST_TIMEOUT_EXCEEDS_POLICY");
            verifyNoInteractions(h.factory, h.coordinator, h.admission);
            verify(h.store, never()).launch(any(), any(), any(), any());
        }
    }

    @Test
    void theHardDeadlineIsRecheckedAfterSqlAdmissionAndBeforeProviderDispatch() throws Exception {
        try (Harness h = new Harness()) {
            h.expireDuringDispatchAuthorization.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatchAuthorizations).hasSize(1);
            assertThat(h.dispatched).isEmpty();
            assertThat(h.receipts).isEmpty();
            assertThat(h.publications).singleElement().satisfies(publication ->
                    assertThat(publication.code()).isEqualTo("DISPATCH_CANCELLED"));
        }
    }

    @Test
    void launchingTheSameManifestTwiceWhileItIsRunningOpensExactlyOneTransport() throws Exception {
        try (Harness h = new Harness()) {
            h.holdBeforeDispatch.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            assertThat(h.waitingAtFence.await(3, TimeUnit.SECONDS)).isTrue();

            h.launch();
            h.releaseFence.countDown();
            h.awaitFinished();

            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
            verify(h.coordinator, times(1)).acquireLiveCampaign(h.manifest.campaignId());
            verify(h.store, times(1)).launch(any(), any(), any(), any());
            assertThat(h.dispatched).hasSize(8);
        }
    }

    @Test
    void recoveryLeavesAConfirmedActiveOwnerAloneAndNeverStartsPlaywright() throws Exception {
        try (Harness h = new Harness()) {
            ProcessHandle current = ProcessHandle.current();
            Instant started = current.info().startInstant().orElseThrow();
            Owner owner = new Owner(h.ownership.instanceId(), current.pid(), started);
            when(h.guard.snapshot()).thenReturn(new Guard("OWNED", h.manifest.campaignId(), owner, 1, Instant.now()));

            h.service.markProvenOrphanWithoutRestart();

            verify(h.store, never()).interruptOrphan(any(), any(), any());
            verify(h.guard, never()).requireCleanup(any(), any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void recoveryDoesNotTreatAnInaccessibleProcessStartTimeAsProofOfDeath() throws Exception {
        try (Harness h = new Harness(); var processes = mockStatic(ProcessHandle.class)) {
            ProcessHandle process = mock(ProcessHandle.class);
            ProcessHandle.Info info = mock(ProcessHandle.Info.class);
            Owner owner = new Owner(h.ownership.instanceId(), 1234, Instant.now().minusSeconds(60));
            when(h.guard.snapshot()).thenReturn(new Guard("OWNED", h.manifest.campaignId(), owner, 1, Instant.now()));
            processes.when(() -> ProcessHandle.of(1234)).thenReturn(Optional.of(process));
            when(process.isAlive()).thenReturn(true);
            when(process.info()).thenReturn(info);
            when(info.startInstant()).thenReturn(Optional.empty());

            h.service.markProvenOrphanWithoutRestart();

            verify(h.store, never()).interruptOrphan(any(), any(), any());
            verify(h.guard, never()).requireCleanup(any(), any());
            verifyNoInteractions(h.factory, h.coordinator);
        }
    }

    @Test
    void anIsolatedSchemaStopsItsMatchButTheOtherMatchReachesAllFinalFamilies() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = request -> request.eventId() == A
                    ? response("{\"event\":{\"id\":" + A + ",\"status\":null}}", 200)
                    : normalFinishedReply(request);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId)
                    .containsExactly(A, B, B, B, B);
            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::endpoint)
                    .containsExactly(EVENT_DETAILS, EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_SCHEMA_INCOMPATIBLE");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.publications).anySatisfy(publication -> {
                assertThat(publication.outcome()).isEqualTo("SCHEMA_INCOMPATIBLE");
                assertThat(publication.scope()).isEqualTo("EVENT");
            });
            assertThat(h.receipts).hasSize(5);
            verify(h.campaign).close();
        }
    }

    @Test
    void identityAndSchemaMismatchHasGlobalPriorityAndNeverReachesTheSecondMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = ignored -> response("{\"event\":{\"id\":19000000,\"status\":null}}", 200);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
                assertThat(publication.code()).isEqualTo("EVENT_ID_MISMATCH");
            });
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            assertThat(h.receipts).hasSize(1);
            verify(h.campaign).close();
        }
    }

    @Test
    void stopWhileWaitingAtTheDispatchFenceCancelsOnlyThatMatchBeforeAnyGet() throws Exception {
        try (Harness h = new Harness()) {
            h.holdBeforeDispatch.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            assertThat(h.waitingAtFence.await(3, TimeUnit.SECONDS)).isTrue();
            h.service.stop(h.manifest.campaignId(), id(A));
            h.releaseFence.countDown();
            h.awaitFinished();

            assertThat(h.dispatched).extracting(PlaywrightProviderRequest::eventId).containsExactly(B, B, B, B);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("FINISHED_CONFIRMED");
            assertThat(h.publications).anySatisfy(publication -> {
                assertThat(publication.outcome()).isEqualTo("NOT_DISPATCHED");
                assertThat(publication.code()).isEqualTo("DISPATCH_CANCELLED");
            });
            assertThat(h.dispatchAuthorizations).hasSize(4);
            assertThat(h.receipts).hasSize(4);
            verify(h.supervisor, never()).stopCampaign(any(), any());
        }
    }

    @Test
    void publicationFailureKeepsTheRawReceiptAndStopsAllFurtherProviderWork() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.failFirstPublication.set(true);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
                assertThat(publication.code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            verify(h.campaign).close();
        }
    }

    @Test
    void aFailedLastFinalPublicationRevokesCompletenessButKeepsTheEarlierFinishedProof() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = request -> switch (request.endpoint()) {
                case EVENT_DETAILS -> normalFinishedReply(request);
                case EVENT_STATISTICS -> response("{\"statistics\":[]}", 200);
                case EVENT_INCIDENTS -> response("{\"incidents\":[]}", 200);
                case EVENT_LINEUPS -> response("{\"confirmed\":false}", 200);
                default -> throw new IllegalArgumentException("unexpected test endpoint");
            };
            h.failFinalLineupsPublication.set(true);
            h.launch();
            h.awaitFinished();

            assertThat(h.failedFinalPublication.get()).satisfies(publication -> {
                assertThat(publication.successful()).isTrue();
                assertThat(publication.nextEventState()).isEqualTo("FINISHED_CONFIRMED");
            });
            assertThat(h.dispatched).hasSize(5);
            assertThat(h.dispatched.getLast().eventId()).isEqualTo(A);
            assertThat(h.dispatched.getLast().endpoint()).isEqualTo(EVENT_LINEUPS);
            assertThat(h.receipts).hasSize(5);
            assertThat(h.publications).anySatisfy(publication ->
                    assertThat(publication.sportStatus()).isEqualTo("finished"));
            assertThat(h.publications.getLast().code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
            assertThat(h.publications.getLast().scope()).isEqualTo("CAMPAIGN");
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            assertThat(h.finalCompleteness.get(id(A))).isFalse();
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
            verify(h.campaign).close();
        }
    }

    @Test
    void failingToPublishAnIsolatedSchemaCannotLeaveAFalseDurableSchemaStop() throws Exception {
        try (Harness h = new Harness()) {
            h.reply = ignored -> response("{\"event\":{\"id\":" + A + ",\"status\":null}}", 200);
            h.failFirstPublication.set(true);
            h.launch();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.publications).singleElement().satisfies(publication -> {
                assertThat(publication.code()).isEqualTo("RUNTIME_OR_STORAGE_FAILURE");
                assertThat(publication.scope()).isEqualTo("CAMPAIGN");
            });
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_ERROR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_ERROR");
            assertThat(h.campaignState.get()).isEqualTo("STOPPED_ERROR");
        }
    }

    @Test
    void aGlobalStopAfterDispatchPreservesTheResponseWithoutSchedulingAnotherMatch() throws Exception {
        try (Harness h = new Harness()) {
            h.holdAfterDispatch.set(true);
            h.reply = LiveCampaignServiceTest::normalFinishedReply;
            h.launch();
            assertThat(h.getInFlight.await(3, TimeUnit.SECONDS)).isTrue();
            h.service.stop(h.manifest.campaignId(), null);
            h.releaseGet.countDown();
            h.awaitFinished();

            assertThat(h.dispatched).hasSize(1);
            assertThat(h.receipts).hasSize(1);
            assertThat(h.eventStates.get(id(A))).isEqualTo("STOPPED_OPERATOR");
            assertThat(h.eventStates.get(id(B))).isEqualTo("STOPPED_OPERATOR");
            verify(h.supervisor).stopCampaign(eq(h.manifest.campaignId()), eq(LiveProviderSession.ENDPOINTS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"live-v1", "live-v2", "live-v3"})
    void launchRespectsThePreparedPolicyForPrematchLineupsAndPersistsUnavailableEvidence(String policy) throws Exception {
        try (Harness h = new Harness(false, policy)) {
            AtomicInteger details = new AtomicInteger(), lineups = new AtomicInteger();
            h.reply = request -> {
                if (request.endpoint() == EVENT_DETAILS) {
                    if (details.incrementAndGet() == 2 && !"live-v3".equals(policy))
                        h.service.stop(h.manifest.campaignId(), null);
                    return response("""
                            {"event":{"id":%d,"startTimestamp":1788796800,
                            "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                            "status":{"type":"notstarted"}}}
                            """.formatted(request.eventId()), 200);
                }
                assertThat(request.endpoint()).isEqualTo(EVENT_LINEUPS);
                if (lineups.incrementAndGet() == 2) h.service.stop(h.manifest.campaignId(), null);
                return response("unavailable", 404);
            };
            h.launch();
            h.awaitFinished();
            assertThat(details).hasValue(2);
            assertThat(lineups).hasValue("live-v3".equals(policy) ? 2 : 0);
            assertThat(h.dispatched).noneSatisfy(request ->
                    assertThat(request.endpoint()).isIn(EVENT_STATISTICS, EVENT_INCIDENTS));
            assertThat(h.receipts).hasSize("live-v3".equals(policy) ? 4 : 2);
            assertThat(h.publications.stream().filter(p -> "HTTP_404".equals(p.code())).count())
                    .isEqualTo("live-v3".equals(policy) ? 2 : 0);
            assertThat(h.eventStates.values()).containsOnly("STOPPED_OPERATOR");
            verify(h.factory, times(1)).open(h.manifest.campaignId(), LiveProviderSession.ENDPOINTS);
        }
    }

    private static UUID id(long providerId) { return CanonicalEventIdentity.sofascore(providerId).value(); }

    private static CanonicalEventObservationView observation(long providerId, EventSourceTrace source) {
        var event = mock(CanonicalEventObservationView.class);
        when(event.observationId()).thenReturn(17L);
        when(event.identity()).thenReturn(CanonicalEventIdentity.sofascore(providerId));
        when(event.source()).thenReturn(source);
        when(event.status()).thenReturn(new ScheduledEventStatus("notstarted", Optional.empty()));
        return event;
    }

    private static CanonicalEventObservationView providerObservation(long providerId, String status) {
        var event = observation(providerId,
                EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", Instant.now()));
        when(event.status()).thenReturn(new ScheduledEventStatus(status, Optional.empty()));
        return event;
    }

    private static PlaywrightProviderResponse normalFinishedReply(PlaywrightProviderRequest request) {
        if (request.endpoint() != EVENT_DETAILS) return response("unavailable", 404);
        return response("""
                {"event":{"id":%d,"startTimestamp":1788796800,
                "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                "status":{"type":"finished"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                """.formatted(request.eventId()), 200);
    }

    private static PlaywrightProviderResponse response(String body, int status) {
        Instant now = Instant.now();
        return new PlaywrightProviderResponse(now, now, status, "application/json", Duration.ZERO,
                RawPayloadEvidence.capture(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class Harness implements AutoCloseable {
        final LiveCampaignStore store = mock(LiveCampaignStore.class);
        final PlaywrightProviderCampaign campaign = mock(PlaywrightProviderCampaign.class);
        final PlaywrightProviderSupervisor supervisor = mock(PlaywrightProviderSupervisor.class);
        final LiveResponseProcessor processor = mock(LiveResponseProcessor.class);
        final LiveAdmissionPolicy admission = mock(LiveAdmissionPolicy.class);
        final CanonicalEventStore events = mock(CanonicalEventStore.class);
        final ManualProviderRequestCoordinator coordinator = mock(ManualProviderRequestCoordinator.class);
        final ProviderCampaignGuardStore guard = mock(ProviderCampaignGuardStore.class);
        final PlaywrightProviderCampaignFactory factory = mock(PlaywrightProviderCampaignFactory.class);
        final LiveCampaignProperties properties = new LiveCampaignProperties();
        final ProviderPlaywrightProperties playwright = new ProviderPlaywrightProperties();
        final SofascoreProperties provider = new SofascoreProperties();
        final LiveCampaignService service;
        final Manifest manifest;
        final Ownership ownership;
        final AtomicReference<String> campaignState = new AtomicReference<>("PREPARED");
        final AtomicInteger reservations = new AtomicInteger();
        final Map<UUID, String> eventStates = new ConcurrentHashMap<>();
        final Map<UUID, Boolean> finalCompleteness = new ConcurrentHashMap<>();
        final List<PlaywrightProviderRequest> dispatched = new CopyOnWriteArrayList<>();
        final List<UUID> dispatchAuthorizations = new CopyOnWriteArrayList<>();
        final List<RawManualCallSnapshot> receipts = new CopyOnWriteArrayList<>();
        final List<Publication> publications = new CopyOnWriteArrayList<>();
        final AtomicBoolean failFirstPublication = new AtomicBoolean();
        final AtomicBoolean failFinalLineupsPublication = new AtomicBoolean();
        final AtomicReference<Publication> failedFinalPublication = new AtomicReference<>();
        final AtomicBoolean holdBeforeDispatch = new AtomicBoolean();
        final AtomicBoolean holdAfterDispatch = new AtomicBoolean();
        final AtomicBoolean expireDuringDispatchAuthorization = new AtomicBoolean();
        final AtomicLong clockOffsetSeconds = new AtomicLong();
        final CountDownLatch waitingAtFence = new CountDownLatch(1), releaseFence = new CountDownLatch(1);
        final CountDownLatch getInFlight = new CountDownLatch(1), releaseGet = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        volatile Function<PlaywrightProviderRequest, PlaywrightProviderResponse> reply;
        volatile Runnable afterOwnerLaunch = () -> {};
        boolean launched;

        Harness() { this(false); }
        Harness(boolean expired) {
            this(expired, "live-v1");
        }
        Harness(boolean expired, String policy) {
            Instant now = Instant.now().minusSeconds(expired ? 600 : 0);
            properties.setEnabled(true);
            properties.setDuration(Duration.ofMinutes(5));
            properties.setQualifiedMatchCapacity(2);
            properties.setRequestEnvelope(Duration.ofSeconds(3));
            properties.setQualificationSha256("a".repeat(64));
            manifest = new Manifest(UUID.randomUUID(), "a".repeat(64), policy, now, now.plusSeconds(300),
                    Duration.ofMinutes(5), 1000, 3000, 20_000_000, 2,
                    List.of(new Target(id(A), A, 1, 1), new Target(id(B), B, 2, 2)),
                    new AdmissionProfile(properties.getRequestEnvelope(), properties.getProcessingEnvelope(),
                            properties.getQualificationSha256()));
            ownership = new Ownership(manifest.campaignId(), UUID.randomUUID(), 1);
            eventStates.put(id(A), "WAITING_START"); eventStates.put(id(B), "WAITING_START");
            provider.setEnabled(true);
            observe(A, "notstarted");
            observe(B, "notstarted");
            playwright.setEnabled(true);
            var lease = mock(ManualProviderRequestCoordinator.CampaignLease.class);
            when(coordinator.acquireLiveCampaign(manifest.campaignId())).thenReturn(lease);
            when(lease.ownership()).thenReturn(ownership);
            doAnswer(invocation -> { finished.countDown(); return null; }).when(lease).close();
            when(guard.isOwned(ownership)).thenReturn(true);
            when(factory.open(manifest.campaignId(), LiveProviderSession.ENDPOINTS)).thenReturn(campaign);
            when(supervisor.activeCampaignId()).thenReturn(Optional.empty());
            when(store.find(manifest.campaignId())).thenAnswer(invocation -> Optional.of(view()));
            when(store.launch(eq(manifest.campaignId()), eq(manifest.manifestSha256()), eq(ownership), any()))
                    .thenAnswer(invocation -> {
                        campaignState.set("RUNNING"); Instant start = invocation.getArgument(3);
                        afterOwnerLaunch.run();
                        return new Launch(ownership, start, start.plus(manifest.duration()), true);
                    });
            when(store.reserveAttempt(any())).thenAnswer(invocation -> {
                AttemptRequest request = invocation.getArgument(0); reservations.incrementAndGet();
                long providerId = request.canonicalEventId().equals(id(A)) ? A : B;
                return Optional.of(new ReservedAttempt(request.attemptId(), request.canonicalEventId(), providerId,
                        request.endpoint(), request.cycleNumber(), request.kind(), request.dueAt(), request.reservedAt(), request.finalCycle()));
            });
            doAnswer(invocation -> {
                dispatchAuthorizations.add(invocation.getArgument(1));
                if (expireDuringDispatchAuthorization.compareAndSet(true, false))
                    clockOffsetSeconds.set(manifest.duration().toSeconds() + 1);
                return null;
            })
                    .when(store).recordDispatch(any(), any(), any());
            when(store.saveReceipt(any(), any(), any())).thenAnswer(invocation -> {
                RawManualCallSnapshot raw = invocation.getArgument(2); receipts.add(raw);
                return new RawSnapshotPersistenceResult(receipts.size(), RawSnapshotPersistenceOutcome.INSERTED,
                        raw.payload().sha256(), raw.payload().sizeBytes(), OptionalLong.of(receipts.size()));
            });
            when(store.publishResult(any(), any(), any(), any())).thenAnswer(invocation -> {
                Publication publication = invocation.getArgument(2);
                if (failFirstPublication.compareAndSet(true, false)) throw new IllegalStateException("publication transaction failed");
                if (publication.successful() && "FINISHED_CONFIRMED".equals(publication.nextEventState())
                        && dispatched.getLast().endpoint() == EVENT_LINEUPS
                        && failFinalLineupsPublication.compareAndSet(true, false)) {
                    failedFinalPublication.set(publication);
                    throw new IllegalStateException("final publication transaction failed");
                }
                Supplier<NormalizedReferences> callback = invocation.getArgument(3);
                NormalizedReferences normalized = callback.get(); publications.add(publication);
                return new Result(invocation.getArgument(1), publication, normalized);
            });
            doAnswer(invocation -> {
                UUID eventId = invocation.getArgument(1); String state = invocation.getArgument(2);
                if (eventId == null) campaignState.set(state); else eventStates.put(eventId, state);
                return null;
            }).when(store).transition(any(), any(), any(), any(), any(), any());
            doAnswer(invocation -> {
                finalCompleteness.put(invocation.getArgument(1), invocation.getArgument(4));
                return null;
            }).when(store).updateScheduleMetrics(any(), any(), any(), anyLong(), anyBoolean(), any());
            LiveResponseProcessor pure = new LiveResponseProcessor(events, mock(EventDetailsStore.class),
                    mock(J5EventDataStore.class), mock(RawManualCallSnapshotStore.class));
            when(processor.process(any(), any(), any(), any())).thenAnswer(invocation -> pure.process(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
            when(processor.persistProcessed(any())).thenReturn(NormalizedReferences.none());
            when(campaign.execute(any(), any())).thenAnswer(invocation -> {
                PlaywrightProviderRequest request = invocation.getArgument(0);
                PlaywrightDispatchAdmission dispatch = invocation.getArgument(1);
                if (holdBeforeDispatch.compareAndSet(true, false)) {
                    waitingAtFence.countDown();
                    if (!releaseFence.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test fence timed out");
                }
                dispatch.check();
                try (var permit = dispatch.acquireDispatchPermit()) { dispatched.add(request); }
                if (holdAfterDispatch.compareAndSet(true, false)) {
                    getInFlight.countDown();
                    if (!releaseGet.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test get timed out");
                }
                return reply.apply(request);
            });
            Clock clock = mock(Clock.class);
            when(clock.instant()).thenAnswer(invocation -> Instant.now().plusSeconds(clockOffsetSeconds.get()));
            service = new LiveCampaignService(provider, playwright, properties, admission, store, events,
                    coordinator, guard, factory, supervisor, processor, clock);
        }

        void observe(long providerId, String status) {
            var event = providerObservation(providerId, status);
            when(events.findLatestByCanonicalId(id(providerId))).thenReturn(Optional.of(event));
        }

        CampaignView view() {
            List<EventView> events = manifest.targets().stream().map(target -> new EventView(target,
                    eventStates.get(target.canonicalEventId()), null, 0, 0, null, List.of())).toList();
            return new CampaignView(manifest, campaignState.get(), null, manifest.preparedAt(),
                    manifest.preparedAt().plus(manifest.duration()), reservations.get(), 0, publications.size(),
                    ownership, events, List.of(), List.of());
        }

        void launch() { service.launch(manifest.campaignId(), manifest.manifestSha256()); launched = true; }
        void awaitFinished() throws InterruptedException { assertThat(finished.await(4, TimeUnit.SECONDS)).isTrue(); }
        @Override public void close() throws InterruptedException {
            releaseFence.countDown(); releaseGet.countDown(); service.shutdown();
            if (launched) assertThat(finished.await(4, TimeUnit.SECONDS)).isTrue();
        }
    }
}
