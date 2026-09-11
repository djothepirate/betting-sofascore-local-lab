package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.ManualProviderRequestCoordinator;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderSupervisor;
import com.bettingproject.sofascorelocal.config.LiveCampaignProperties;
import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.LiveCampaignStore;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The real admission policy checks storage without refreshing provider observations. */
class LivePreparationAdmissionTest {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final long PROVIDER_EVENT_ID = 17000001L;
    private static final long SECOND_PROVIDER_EVENT_ID = 17000002L;
    private static final long FIVE_MIB = 5L * 1024 * 1024;
    private static final long V5_RAW_BYTES = 15_728_640_000L;

    @ParameterizedTest
    @ValueSource(ints = {-3, 1})
    void aNotstartedObservationCanPrepareWithNetworkDisabledRegardlessOfItsKickoffDate(int daysFromNow) {
        var properties = qualifiedProperties();
        var capacityReads = new AtomicInteger();
        LiveStorageCapacityProbe capacity = () -> {
            capacityReads.incrementAndGet();
            return 2 * V5_RAW_BYTES + properties.getDiskReserveBytes();
        };
        var h = new Harness(properties, capacity);
        var event = observation(PROVIDER_EVENT_ID, NOW.plus(Duration.ofDays(daysFromNow)), "notstarted");
        when(h.events.findLatestByCanonicalId(event.identity().value())).thenReturn(Optional.of(event));

        var preparation = h.service.prepareSelection(List.of(event.identity().value()));

        assertThat(preparation.excludedFinished()).isEmpty();
        assertThat(preparation.manifest().targets()).containsExactly(
                new Target(event.identity().value(), PROVIDER_EVENT_ID, event.observationId(), 23));
        assertThat(preparation.manifest().preparedAt()).isEqualTo(NOW);
        assertThat(preparation.manifest().expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(preparation.manifest().maximumBytes()).isEqualTo(V5_RAW_BYTES);
        assertThat(preparation.manifest().policyVersion()).isEqualTo("live-v9");
        assertThat(preparation.manifest().cycleInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(capacityReads.get()).isEqualTo(1);
        assertThat(event.status().type()).isEqualTo("notstarted");
        verify(h.store).prepare(preparation.manifest());
        verify(h.events, never()).save(any());
        h.verifyNoProviderWork();
    }

    @Test
    void anUnconfiguredDockerProbeRefusesStaleNotstartedPreparationBeforeCreatingAManifest() {
        var properties = qualifiedProperties();
        assertThat(properties.getDockerExecutable()).isNull();
        var h = new Harness(properties, new DockerLiveStorageCapacityProbe(properties));
        var event = observation(PROVIDER_EVENT_ID, NOW.minus(Duration.ofDays(3)), "notstarted");
        when(h.events.findLatestByCanonicalId(event.identity().value())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> h.service.prepareSelection(List.of(event.identity().value())))
                .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_STORAGE_PROBE_NOT_CONFIGURED");

        verify(h.store, never()).prepare(any());
        verify(h.events, never()).save(any());
        assertThat(event.status().type()).isEqualTo("notstarted");
        h.verifyNoProviderWork();
    }

    @Test
    void excludingAFinishedMatchDoesNotBypassTheRealStorageCheckForARemainingStaleNotstartedMatch() {
        var properties = qualifiedProperties();
        assertThat(properties.getQualifiedMatchCapacity()).isEqualTo(1);
        assertThat(properties.getDockerExecutable()).isNull();
        var h = new Harness(properties, new DockerLiveStorageCapacityProbe(properties));
        var finished = observation(PROVIDER_EVENT_ID, NOW.minus(Duration.ofDays(3)), "finished");
        var stale = observation(SECOND_PROVIDER_EVENT_ID, NOW.minus(Duration.ofDays(3)), "notstarted");
        when(h.events.findLatestByCanonicalId(finished.identity().value())).thenReturn(Optional.of(finished));
        when(h.events.findLatestByCanonicalId(stale.identity().value())).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> h.service.prepareSelection(List.of(finished.identity().value(), stale.identity().value())))
                .isInstanceOf(IllegalStateException.class).hasMessage("LIVE_STORAGE_PROBE_NOT_CONFIGURED");

        verify(h.events).findLatestByCanonicalId(finished.identity().value());
        verify(h.events).findLatestByCanonicalId(stale.identity().value());
        verify(h.store, never()).prepare(any());
        verify(h.events, never()).save(any());
        assertThat(stale.status().type()).isEqualTo("notstarted");
        h.verifyNoProviderWork();
    }

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true", "3,false", "3,true"})
    void qualifiedMultipleSelectionPreservesEveryEligibleTargetAndExcludesFinishedBeforeAdmission(int count, boolean mixed) {
        var properties = qualifiedProperties();
        properties.setQualifiedMatchCapacity(count);
        properties.setRequestEnvelope(Duration.ofMillis(count == 2 ? 3000 : 750));
        properties.setQualificationSha256("b".repeat(64));
        var h = new Harness(properties, () -> Long.MAX_VALUE);
        var selected = new ArrayList<CanonicalEventObservationView>();
        if (mixed) selected.add(observation(PROVIDER_EVENT_ID - 1, NOW.minus(Duration.ofDays(3)), "finished"));
        for (int i = 0; i < count; i++) selected.add(observation(PROVIDER_EVENT_ID + i,
                NOW.plus(Duration.ofDays(i - 1)), "notstarted"));
        selected.forEach(event -> when(h.events.findLatestByCanonicalId(event.identity().value()))
                .thenReturn(Optional.of(event)));

        var preparation = h.service.prepareSelection(selected.stream().map(e -> e.identity().value()).toList());

        var eligible = selected.stream().filter(e -> !e.status().type().equals("finished")).toList();
        assertThat(preparation.manifest().targets()).containsExactlyElementsOf(eligible.stream()
                .map(e -> new Target(e.identity().value(), e.identity().providerEventId(), e.observationId(), 23)).toList());
        assertThat(preparation.excludedFinished()).containsExactlyElementsOf(mixed ? List.of(selected.getFirst()) : List.of());
        assertThat(preparation.manifest().maximumBytes()).isEqualTo(V5_RAW_BYTES);
        assertThat(preparation.manifest().maximumCallsPerEvent()).isEqualTo(2500);
        assertThat(preparation.manifest().maximumCalls()).isEqualTo(20000);
        assertThat(preparation.manifest().qualifiedMatchCapacity()).isEqualTo(count);
        assertThat(preparation.manifest().admissionProfile().requestEnvelope()).isEqualTo(properties.getRequestEnvelope());
        assertThat(preparation.manifest().admissionProfile().groupedProfile()).isEqualTo(properties.groupedAdmissionProfileV9());
        assertThat(preparation.manifest().policyVersion()).isEqualTo("live-v9");
        assertThat(preparation.manifest().cycleInterval()).isEqualTo(Duration.ofSeconds(60));
        verify(h.store).prepare(preparation.manifest());
        verify(h.events, never()).save(any());
        h.verifyNoProviderWork();
    }

    @ParameterizedTest
    @CsvSource({"5,1", "5,2", "5,3",
            "10,1", "10,3", "20,3", "25,1", "25,2", "25,3", "1000,1", "1000,3"})
    void configuredSelectionCeilingDoesNotInvalidateSmallerQualifiedSelections(int ceiling, int count) {
        var properties = qualifiedProperties();
        properties.setQualifiedMatchCapacity(ceiling);
        properties.setRequestEnvelope(Duration.ofSeconds(1));
        properties.setQualificationSha256("b".repeat(64));
        var h = new Harness(properties, () -> Long.MAX_VALUE);
        var selected = new ArrayList<CanonicalEventObservationView>();
        for (int i = 0; i < count; i++) selected.add(observation(PROVIDER_EVENT_ID + i, NOW, "inprogress"));
        selected.forEach(event -> when(h.events.findLatestByCanonicalId(event.identity().value()))
                .thenReturn(Optional.of(event)));
        var prepared = h.service.prepareSelection(selected.stream().map(event -> event.identity().value()).toList());
        assertThat(prepared.manifest().targets()).hasSize(count);
        assertThat(prepared.manifest().qualifiedMatchCapacity()).isEqualTo(Math.min(ceiling,10));
        assertThat(prepared.manifest().cycleInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(prepared.manifest().policyVersion()).isEqualTo("live-v9");
        h.verifyNoProviderWork();
    }

    @ParameterizedTest @ValueSource(ints = {5, 7, 10, 20, 25})
    void theNextEligibleMatchAboveTheEffectiveSelectionCeilingIsRejectedWithoutProviderWork(int ceiling) {
        var properties = qualifiedProperties();
        properties.setQualifiedMatchCapacity(ceiling);
        properties.setRequestEnvelope(Duration.ofSeconds(1));
        properties.setQualificationSha256("b".repeat(64));
        var h = new Harness(properties, () -> Long.MAX_VALUE);
        var selected = new ArrayList<UUID>();
        for (int i = 0; i <= Math.min(ceiling, 10); i++) {
            var event = observation(PROVIDER_EVENT_ID + i, NOW, "inprogress");
            selected.add(event.identity().value());
            when(h.events.findLatestByCanonicalId(event.identity().value())).thenReturn(Optional.of(event));
        }
        assertThatThrownBy(() -> h.service.prepareSelection(selected))
                .hasMessage("LIVE_SELECTION_EXCEEDS_QUALIFIED_CAPACITY");
        verify(h.store, never()).prepare(any());
        h.verifyNoProviderWork();
    }

    @ParameterizedTest
    @CsvSource({"RUNNING,INITIAL_CHECK,true", "RUNNING,WAITING_START,true", "RUNNING,COLLECTING,true",
            "RUNNING,FINALIZING,true", "RUNNING,FINISHED_CONFIRMED,true", "RUNNING,STOPPED_OPERATOR,true",
            "RUNNING,STOPPED_ERROR,false", "CLEANUP_REQUIRED,COLLECTING,true", "CLEANUP_REQUIRED,STOPPED_ERROR,false",
            "STOPPED_ERROR,STOPPED_ERROR,false", "COMPLETED,STOPPED_OPERATOR,false", "PREPARED,PREPARED,false"})
    void selectionAndPreparationRespectCampaignMembershipWithTheExplicitErrorException(
            String campaignState, String eventState, boolean blocked) {
        var h = new Harness(qualifiedProperties(), () -> Long.MAX_VALUE);
        var event = observation(PROVIDER_EVENT_ID, NOW, "inprogress");
        var id = event.identity().value();
        var previous = campaign(event, campaignState, eventState);
        when(h.events.findLatestByCanonicalId(id)).thenReturn(Optional.of(event));
        when(h.store.latestForEvent(id)).thenReturn(Optional.of(previous));
        assertThat(h.service.selectionBlockedEvents(List.of(id)).contains(id)).isEqualTo(blocked);
        if (blocked) {
            assertThatThrownBy(() -> h.service.prepareSelection(List.of(id)))
                    .hasMessage("LIVE_EVENT_ALREADY_IN_CAMPAIGN");
            verify(h.store, never()).prepare(any());
        } else {
            assertThat(h.service.prepareSelection(List.of(id)).manifest().targets()).hasSize(1);
        }
        h.verifyNoProviderWork();
    }

    @Test
    void aManifestPreparedEarlierCannotLaunchAnEventThatHasSinceJoinedARunningCampaign() {
        var h = new Harness(qualifiedProperties(), () -> Long.MAX_VALUE);
        var event = observation(PROVIDER_EVENT_ID, NOW, "inprogress");
        var prepared = campaign(event, "PREPARED", "PREPARED");
        when(h.store.find(prepared.manifest().campaignId())).thenReturn(Optional.of(prepared));
        when(h.store.latestForEvent(event.identity().value())).thenReturn(Optional.of(campaign(event, "RUNNING", "COLLECTING")));
        assertThatThrownBy(() -> h.service.launch(prepared.manifest().campaignId(), prepared.manifest().manifestSha256()))
                .hasMessage("LIVE_EVENT_ALREADY_IN_CAMPAIGN");
        h.verifyNoProviderWork();
    }

    @ParameterizedTest @ValueSource(ints={20,25,1000})
    void aConfiguredCeilingAboveTheQualifiedCapacityDoesNotSilentlyLengthenTheSixtySecondCadence(int ceiling) {
        var properties=qualifiedProperties(); properties.setQualifiedMatchCapacity(ceiling);
        // This deliberately slow profile fails the production replay entirely; it cannot buy more time.
        properties.getGroupedV9().getEndpoints().values().forEach(budget -> {
            budget.setRequestEnvelope(Duration.ofSeconds(10));
            budget.setProcessingEnvelope(Duration.ofSeconds(10));
        });
        int qualifiedCapacity = LiveAdmissionPolicy.qualifiedCapacityV9(properties.groupedAdmissionProfileV9());
        assertThat(qualifiedCapacity).isZero();
        var h=new Harness(properties,()->Long.MAX_VALUE);
        var selected=new ArrayList<UUID>();
        for(int i=0;i<=qualifiedCapacity;i++) {
            var event=observation(PROVIDER_EVENT_ID+i,NOW,"inprogress");
            selected.add(event.identity().value());
            when(h.events.findLatestByCanonicalId(event.identity().value())).thenReturn(Optional.of(event));
        }
        assertThat(h.service.selectionMaximum()).isEqualTo(qualifiedCapacity);
        assertThatThrownBy(()->h.service.prepareSelection(selected)).hasMessage("LIVE_CAPACITY_REFUSED_REDUCE_SELECTION");
        verify(h.store,never()).prepare(any());
        h.verifyNoProviderWork();
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void historicalQualificationsDoNotAuthorizeANewV9Preparation(boolean includeV4Profile, boolean includeV5Profile) {
        var properties=new LiveCampaignProperties(); properties.setQualificationSha256("b".repeat(64));
        properties.getGroupedV6().setQualificationSha256("e".repeat(64));
        properties.getGroupedV6().getEndpoints().values().forEach(budget -> {
            budget.setRequestEnvelope(Duration.ofMillis(500));
            budget.setProcessingEnvelope(Duration.ofMillis(100));
        });
        if (includeV4Profile) {
            properties.getGrouped().setQualificationSha256("c".repeat(64));
            properties.getGrouped().getEndpoints().values().forEach(budget -> {
                budget.setRequestEnvelope(Duration.ofMillis(500));
                budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
        }
        if (includeV5Profile) {
            properties.getGroupedV5().setQualificationSha256("d".repeat(64));
            properties.getGroupedV5().getEndpoints().values().forEach(budget -> {
                budget.setRequestEnvelope(Duration.ofMillis(500));
                budget.setProcessingEnvelope(Duration.ofMillis(100));
            });
        }
        var capacityReads=new AtomicInteger();
        var h=new Harness(properties,()->{capacityReads.incrementAndGet();return Long.MAX_VALUE;});
        var event=observation(PROVIDER_EVENT_ID,NOW,"notstarted");
        when(h.events.findLatestByCanonicalId(event.identity().value())).thenReturn(Optional.of(event));
        assertThat(h.service.selectionMaximum()).isZero();
        assertThatThrownBy(()->h.service.prepareSelection(List.of(event.identity().value())))
                .hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(capacityReads).hasValue(0);
        verify(h.store,never()).prepare(any());
        h.verifyNoProviderWork();
    }

    @Test
    void aQualifiedV8ProfileDoesNotAuthorizeANewV9Preparation() {
        var properties = new LiveCampaignProperties();
        properties.getGroupedV8().setQualificationSha256("8".repeat(64));
        properties.getGroupedV8().getEndpoints().values().forEach(budget -> {
            budget.setRequestEnvelope(Duration.ofMillis(500));
            budget.setProcessingEnvelope(Duration.ofMillis(100));
        });
        var capacityReads = new AtomicInteger();
        var h = new Harness(properties, () -> {
            capacityReads.incrementAndGet();
            return Long.MAX_VALUE;
        });
        var event = observation(PROVIDER_EVENT_ID, NOW, "notstarted");
        when(h.events.findLatestByCanonicalId(event.identity().value())).thenReturn(Optional.of(event));

        assertThat(h.service.selectionMaximum()).isZero();
        assertThatThrownBy(() -> h.service.prepareSelection(List.of(event.identity().value())))
                .hasMessage("LIVE_GROUPED_QUALIFICATION_REQUIRED");
        assertThat(capacityReads).hasValue(0);
        verify(h.store, never()).prepare(any());
        h.verifyNoProviderWork();
    }

    /** Synthetic test evidence only; it does not qualify operator traffic or lower runtime defaults. */
    private static LiveCampaignProperties qualifiedProperties() {
        var properties=new LiveCampaignProperties();
        properties.getGroupedV9().setQualificationSha256("c".repeat(64));
        properties.getGroupedV9().getEndpoints().values().forEach(budget->{
            budget.setRequestEnvelope(Duration.ofMillis(500));
            budget.setProcessingEnvelope(Duration.ofMillis(100));
        });
        return properties;
    }

    private static CampaignView campaign(CanonicalEventObservationView event, String state, String eventState) {
        var target = new Target(event.identity().value(), event.identity().providerEventId(), event.observationId(), 23);
        var manifest = new Manifest(UUID.randomUUID(), "d".repeat(64), "live-v1", NOW, NOW.plusSeconds(300),
                Duration.ofHours(4), 1000, 3000, 1000L * FIVE_MIB, 1, List.of(target));
        return new CampaignView(manifest, state, null, "PREPARED".equals(state) ? null : NOW, NOW.plusSeconds(14400),
                0, 0, 1, null, List.of(new EventView(target, eventState, null, 0, 0, NOW, List.of())), List.of(), List.of());
    }

    private static CanonicalEventObservationView observation(long providerEventId, Instant startsAt, String status) {
        Instant receivedAt = (startsAt.isBefore(NOW) ? startsAt : NOW).minusSeconds(3600);
        return new CanonicalEventObservationView(17, CanonicalEventIdentity.sofascore(providerEventId), startsAt,
                new ScheduledTeam(1, "Synthetic home"), new ScheduledTeam(2, "Synthetic away"),
                new ScheduledEventStatus(status, Optional.empty()), Optional.empty(),
                EventSourceTrace.providerSnapshot(23, "b".repeat(64), "event-details-v2", receivedAt),
                "c".repeat(64), 1);
    }

    private static final class Harness {
        final CanonicalEventStore events = mock(CanonicalEventStore.class);
        final LiveCampaignStore store = mock(LiveCampaignStore.class);
        final ManualProviderRequestCoordinator coordinator = mock(ManualProviderRequestCoordinator.class);
        final ProviderCampaignGuardStore guard = mock(ProviderCampaignGuardStore.class);
        final PlaywrightProviderCampaignFactory factory = mock(PlaywrightProviderCampaignFactory.class);
        final PlaywrightProviderSupervisor supervisor = mock(PlaywrightProviderSupervisor.class);
        final LiveResponseProcessor processor = mock(LiveResponseProcessor.class);
        final LiveCampaignService service;

        Harness(LiveCampaignProperties properties, LiveStorageCapacityProbe capacity) {
            var provider = new SofascoreProperties();
            var playwright = new ProviderPlaywrightProperties();
            assertThat(provider.isEnabled()).isFalse();
            assertThat(playwright.isEnabled()).isFalse();
            assertThat(properties.isEnabled()).isFalse();
            when(store.prepare(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new LiveCampaignService(provider, playwright, properties,
                    new LiveAdmissionPolicy(properties, capacity), store, events, coordinator, guard,
                    factory, supervisor, processor, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        void verifyNoProviderWork() {
            verifyNoInteractions(coordinator, guard, factory, supervisor, processor);
            verify(store, never()).launch(any(), any(), any(), any());
            verify(store, never()).reserveAttempt(any());
        }
    }
}
