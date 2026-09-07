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
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Target;
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

    @ParameterizedTest
    @ValueSource(ints = {-3, 1})
    void aNotstartedObservationCanPrepareWithNetworkDisabledRegardlessOfItsKickoffDate(int daysFromNow) {
        var properties = new LiveCampaignProperties();
        var capacityReads = new AtomicInteger();
        LiveStorageCapacityProbe capacity = () -> {
            capacityReads.incrementAndGet();
            return 2 * 1000L * FIVE_MIB + properties.getDiskReserveBytes();
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
        assertThat(preparation.manifest().maximumBytes()).isEqualTo(1000L * FIVE_MIB);
        assertThat(capacityReads.get()).isEqualTo(1);
        assertThat(event.status().type()).isEqualTo("notstarted");
        verify(h.store).prepare(preparation.manifest());
        verify(h.events, never()).save(any());
        h.verifyNoProviderWork();
    }

    @Test
    void anUnconfiguredDockerProbeRefusesStaleNotstartedPreparationBeforeCreatingAManifest() {
        var properties = new LiveCampaignProperties();
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
        var properties = new LiveCampaignProperties();
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
    @CsvSource({"2,false", "2,true", "3,false", "3,true"})
    void qualifiedMultipleSelectionPreservesEveryEligibleTargetAndExcludesFinishedBeforeAdmission(int count, boolean mixed) {
        var properties = new LiveCampaignProperties();
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
        assertThat(preparation.manifest().maximumBytes()).isEqualTo(count * 1000L * FIVE_MIB);
        assertThat(preparation.manifest().qualifiedMatchCapacity()).isEqualTo(count);
        assertThat(preparation.manifest().admissionProfile().requestEnvelope()).isEqualTo(properties.getRequestEnvelope());
        verify(h.store).prepare(preparation.manifest());
        verify(h.events, never()).save(any());
        h.verifyNoProviderWork();
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
