package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.adapter.persistence.live.JdbcProviderResilienceStore;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.State;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Real durable admission around a simulated grouped transport. No browser, provider or wall-clock wait. */
@Testcontainers
class ProviderResilienceTransportPersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("provider_resilience_transport").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final Instant START = Instant.parse("2030-09-09T12:00:00Z");
    private static final Set<SofascoreEndpointType> LIVE = Set.of(SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_LINEUPS);

    @Test
    void durableBudgetsDeferGroupedEmissionsAcrossRestartsThenPartial403SurvivesFailedCleanup() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        var transactions = new JdbcTransactionManager(ds);
        var clock = new LogicalClock();
        var transport = new SyntheticTransport(clock);

        // Prior campaigns used 950 departures, all spaced 3.5 s and outside the current minute.
        // The first hourly charge expires at START+200 s. No runtime budget is reset below.
        jdbc.update("""
                insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version)
                    select gen_random_uuid(),?::timestamptz-interval '3400 seconds'+n*interval '3.5 seconds',
                        'provider-resilience-v1' from generate_series(0,949) n
                """, Timestamp.from(START));
        jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) select dispatch_id,reserved_at from provider_departure_reservation");
        jdbc.update("""
                update provider_resilience_state set last_departure_at=(select max(reserved_at) from provider_departure_reservation),
                    last_departure_finished_at=(select max(finished_at) from provider_departure_completion) where singleton_id=1
                """);

        for (int restart = 0; restart < 2; restart++) {
            var factory = factory(transport, newStore(jdbc, transactions), clock);
            UUID campaignId = UUID.randomUUID();
            try (var campaign = factory.openLiveGroupedV5(campaignId, LIVE)) {
                for (int call = 0; call < 25; call++) emitGrouped(campaign, campaignId);
            }
        }
        assertThat(transport.emissions).hasSize(50);
        assertThat(transport.emissions.get(0)).isEqualTo(START);
        assertThat(transport.emissions.get(24)).isEqualTo(START.plusSeconds(48));
        assertThat(transport.emissions.get(25)).isEqualTo(START.plusSeconds(60));
        assertThat(transport.emissions.get(49)).isEqualTo(START.plusSeconds(108));

        // A third factory and repository must wait for the hourly window, rather than start a new budget.
        UUID refusedCampaign = UUID.randomUUID();
        var factory = factory(transport, newStore(jdbc, transactions), clock);
        var campaign = factory.openLiveGroupedV5(refusedCampaign, LIVE);
        transport.refuseNext = true;
        assertThatThrownBy(() -> campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(17000001),
                group(refusedCampaign), new PlaywrightDispatchAdmission() {
                    public void check() { }
                    public Permit acquireDispatchPermit() { return () -> { }; }
                    public void onTransportProgress(PlaywrightTransportDiagnostic diagnostic) {
                        var alreadyCommitted = newStore(jdbc, transactions).snapshot();
                        assertThat(alreadyCommitted.state()).isEqualTo(State.SUSPENDED);
                        assertThat(alreadyCommitted.httpStatus()).isEqualTo(403);
                        assertThat(alreadyCommitted.campaignId()).isEqualTo(refusedCampaign);
                        assertThat(diagnostic.responseComplete()).isFalse();
                    }
                })).isInstanceOf(PlaywrightProviderException.class);
        assertThat(transport.emissions.get(50)).isEqualTo(START.plusSeconds(200));
        for (int i = 1; i < transport.emissions.size(); i++)
            assertThat(Duration.between(transport.emissions.get(i - 1), transport.emissions.get(i))).isGreaterThanOrEqualTo(Duration.ofSeconds(2));
        var refused = newStore(jdbc, transactions).snapshot();
        assertThat(refused.suspendedAt()).isEqualTo(START.plusSeconds(200));
        assertThat(refused.unresolvedDispatchId()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from provider_departure_reservation", Long.class)).isEqualTo(1001);
        assertThat(jdbc.queryForObject("select count(*) from provider_departure_completion", Long.class)).isEqualTo(1000);

        transport.cleanupFails = true;
        assertThatThrownBy(campaign::close).hasMessage("synthetic cleanup failed");
        assertThat(newStore(jdbc, transactions).snapshot()).isEqualTo(refused);
        clock.advance(Duration.ofSeconds(12));
        transport.cleanupFails = false;
        campaign.close();
        var cleaned = newStore(jdbc, transactions).snapshot();
        assertThat(cleaned.state()).isEqualTo(State.SUSPENDED);
        assertThat(cleaned.evidenceId()).isEqualTo(refused.evidenceId());
        assertThat(cleaned.unresolvedDispatchId()).isNull();
        assertThat(cleaned.lastDepartureFinishedAt()).isEqualTo(START.plusSeconds(242));
        assertThat(jdbc.queryForObject("select count(*) from provider_departure_completion", Long.class)).isEqualTo(1001);
        assertThat(jdbc.queryForObject("select count(*) from provider_resilience_event", Long.class)).isEqualTo(1);

        // Expired rate windows and a successful cleanup do not automatically rearm J3, J4 or J5.
        clock.advance(Duration.ofDays(1));
        var restarted = factory(transport, newStore(jdbc, transactions), clock);
        assertThatThrownBy(() -> restarted.open(UUID.randomUUID(), Set.of(SofascoreEndpointType.SCHEDULED_EVENTS)))
                .hasMessage("PROVIDER_SUSPENDED");
        assertThatThrownBy(() -> restarted.open(UUID.randomUUID(), Set.of(SofascoreEndpointType.EVENT_DETAILS)))
                .hasMessage("PROVIDER_SUSPENDED");
        assertThatThrownBy(() -> restarted.openManualJ5Grouped(UUID.randomUUID(), LIVE)).hasMessage("PROVIDER_SUSPENDED");
        assertThat(transport.opens).isEqualTo(3);
        assertThat(transport.emissions).hasSize(51);
    }

    private static void emitGrouped(PlaywrightProviderCampaign campaign, UUID campaignId) {
        campaign.executeGrouped(PlaywrightProviderRequest.eventDetails(17000001), group(campaignId), PlaywrightDispatchAdmission.UNRESTRICTED);
    }
    private static LiveProviderDispatchGroup group(UUID campaignId) {
        return new LiveProviderDispatchGroup(campaignId, UUID.randomUUID(), 17000001, LiveProviderDispatchGroup.Phase.IN_PLAY);
    }
    private static ResilientPlaywrightProviderCampaignFactory factory(SyntheticTransport transport, ProviderResilienceStore store, LogicalClock clock) {
        return new ResilientPlaywrightProviderCampaignFactory(transport, store, clock, clock::advance);
    }
    private static ProviderResilienceStore newStore(JdbcTemplate jdbc, JdbcTransactionManager transactions) {
        var proxy = new ProxyFactory(new JdbcProviderResilienceStore(jdbc));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (ProviderResilienceStore) proxy.getProxy();
    }
    private static final class LogicalClock extends Clock {
        private Instant now = START;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }
    private static final class SyntheticTransport implements PlaywrightProviderCampaignFactory {
        final LogicalClock clock;
        final List<Instant> emissions = new ArrayList<>();
        boolean refuseNext, cleanupFails;
        int opens;
        SyntheticTransport(LogicalClock clock) { this.clock = clock; }
        public PlaywrightProviderCampaign open(UUID id, Set<SofascoreEndpointType> endpoints) { throw new AssertionError("unexpected manual opening"); }
        public PlaywrightProviderCampaign openLiveGroupedV5(UUID id, Set<SofascoreEndpointType> endpoints) {
            opens++;
            return new PlaywrightProviderCampaign() {
                public PlaywrightProviderResponse execute(PlaywrightProviderRequest request) { throw new AssertionError("unguarded request"); }
                public PlaywrightProviderResponse executeGrouped(PlaywrightProviderRequest request, LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
                    admission.check();
                    try (var permit = admission.acquireDispatchPermit()) {
                        Instant at = clock.instant();
                        emissions.add(at);
                        if (refuseNext) {
                            var headers = new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,
                                    30000, at, at, 403, null, false);
                            admission.onTransportProgress(headers);
                            clock.advance(Duration.ofSeconds(30));
                            throw new PlaywrightProviderException(PlaywrightProviderFailure.TIMEOUT,
                                    headers.at(PlaywrightTransportDiagnostic.Phase.READING_BODY));
                        }
                        return new PlaywrightProviderResponse(at, at, 200, "application/json", Duration.ZERO,
                                RawPayloadEvidence.capture(new byte[] { '{', '}' }));
                    }
                }
                public void close() { if (cleanupFails) throw new IllegalStateException("synthetic cleanup failed"); }
            };
        }
    }
}
