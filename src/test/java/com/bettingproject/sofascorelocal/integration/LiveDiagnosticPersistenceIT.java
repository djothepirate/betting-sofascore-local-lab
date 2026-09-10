package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.persistence.*;
import com.bettingproject.sofascorelocal.adapter.persistence.live.*;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
import com.bettingproject.sofascorelocal.domain.event.*;
import com.bettingproject.sofascorelocal.domain.eventdetails.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.*;
import com.bettingproject.sofascorelocal.port.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL diagnostics and upgrade evidence; no provider request, browser or operator database. */
@Testcontainers
class LiveDiagnosticPersistenceIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("live_diagnostic_admin").withUsername("sofascore_lab").withPassword("integration-test-only");
    private static final AtomicInteger DATABASE=new AtomicInteger();
    private static final Instant T0=Instant.parse("2030-09-09T12:00:00Z");
    private static final SofascoreEndpointType DETAILS=SofascoreEndpointType.EVENT_DETAILS;

    @Test
    void upgradingPopulatedV44DoesNotInferTerminationOrContextReuseForHistoricalTimeouts() {
        Fixture f=fixture("44");Running running=f.running();
        f.jdbc.update("""
                insert into live_attempt_transport_diagnostic(attempt_id,campaign_id,endpoint_type,transport_phase,
                    timeout_ms,requested_at,headers_received_at,http_status,response_complete)
                values (?,?,'EVENT_DETAILS','READING_BODY',30000,?,?,200,false)
                """,running.attempt(),running.campaign(),Timestamp.from(T0.plusSeconds(10)),Timestamp.from(T0.plusSeconds(12)));
        f.jdbc.update("""
                insert into live_campaign_diagnostic(campaign_id,kind,phase,code,occurred_at,attempt_id,endpoint_type)
                values (?,'FIRST_FAILURE','TRANSPORT','PLAYWRIGHT_TIMEOUT',?,?,'EVENT_DETAILS')
                """,running.campaign(),Timestamp.from(T0.plusSeconds(40)),running.attempt());
        var before=f.evidence("live_campaign","live_call","live_call_receipt","live_call_result","live_campaign_diagnostic",
                "provider_campaign_guard","provider_snapshot","provider_snapshot_occurrence");
        assertThat(f.migrate("45")).isOne();f.assertEvidence(before);
        var old=f.newDiagnostics().findTransport(running.campaign(),running.attempt()).orElseThrow();
        assertThat(old).isEqualTo(diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,200));
        assertThat(old.exchangeEndedAt()).isNull();assertThat(old.exchangeEndReason()).isNull();
        assertThat(old.contextReusable()).isFalse();
        assertThat(f.newDiagnostics().find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE).orElseThrow().transport()).isEqualTo(old);
        assertThat(f.migrate("45")).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"FINISHED","ABORTED"})
    void endedTimeoutPersistsAcrossRestartWithoutAReceiptOrFreshDataAndCannotBeRewritten(String reason) {
        Fixture f=fixture("45");Running running=f.running();
        var before=f.evidence("provider_snapshot","provider_snapshot_occurrence","live_call_receipt",
                "canonical_event_observation","event_detail_observation","j5_event_data_observation");
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,200);
        var proof=headers.withExchangeEnd(T0.plusSeconds(40),PlaywrightTransportDiagnostic.ExchangeEndReason.valueOf(reason),true);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,headers);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,proof);
        var campaign=f.campaigns.find(running.campaign()).orElseThrow();
        f.campaigns.publishResult(campaign.ownership(),running.attempt(),new Publication("FAILED","NONE",
                "PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED",T0.plusSeconds(41),null,false,null),NormalizedReferences::none);
        LiveDiagnosticStore restarted=f.newDiagnostics();
        restarted.recordTransport(running.campaign(),running.attempt(),DETAILS,headers);
        restarted.recordTransport(running.campaign(),running.attempt(),DETAILS,proof);
        assertThat(restarted.findTransport(running.campaign(),running.attempt())).contains(proof);
        assertThat(restarted.findTransport(UUID.randomUUID(),running.attempt())).isEmpty();
        assertThat(restarted.find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE)).isEmpty();
        var saved=f.campaigns.find(running.campaign()).orElseThrow();
        assertThat(saved.state()).isEqualTo("RUNNING");assertThat(saved.reservedCalls()).isOne();
        assertThat(saved.attempts().getFirst().result().publication().successful()).isFalse();
        assertThat(saved.attempts().getFirst().receivedAt()).isNull();f.assertEvidence(before);
        assertThatThrownBy(()->restarted.recordTransport(running.campaign(),running.attempt(),DETAILS,
                proof.withExchangeEnd(T0.plusSeconds(42),proof.exchangeEndReason(),true)))
                .hasMessage("LIVE_DIAGNOSTIC_EVIDENCE_CONFLICT");
        for(String sql:List.of("update live_attempt_transport_diagnostic set exchange_ended_at=null,exchange_end_reason=null,context_reusable=false",
                "update live_attempt_transport_diagnostic set context_reusable=false",
                "update live_attempt_transport_diagnostic set exchange_end_reason='UNKNOWN'",
                "update live_attempt_transport_diagnostic set exchange_ended_at=requested_at-interval '1 second'"))
            assertThatThrownBy(()->f.jdbc.update(sql)).isInstanceOf(DataAccessException.class);
        assertThat(restarted.findTransport(running.campaign(),running.attempt())).contains(proof);
    }

    @ParameterizedTest @ValueSource(ints={403,429})
    void knownRefusalCannotBecomeReusableEvenWhenItsExchangeEnded(int status) {
        Fixture f=fixture("45");Running running=f.running();
        var ended=diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,status)
                .withExchangeEnd(T0.plusSeconds(40),PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED,false);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,ended);
        assertThatThrownBy(()->ended.withExchangeEnd(ended.exchangeEndedAt(),ended.exchangeEndReason(),true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->f.jdbc.update("update live_attempt_transport_diagnostic set context_reusable=true"))
                .isInstanceOf(DataAccessException.class);
        assertThat(f.newDiagnostics().findTransport(running.campaign(),running.attempt())).contains(ended);
        assertThat(f.count("live_call_receipt")).isZero();
    }

    @Test
    void concurrentLateProgressAndTerminationProofConvergeWithoutLosingTheProof() throws Exception {
        Fixture f=fixture("45");Running running=f.running();CountDownLatch start=new CountDownLatch(1);
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,200);
        var proof=headers.withExchangeEnd(T0.plusSeconds(40),PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED,true);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var late=workers.submit(()->{start.await();f.newDiagnostics().recordTransport(running.campaign(),running.attempt(),DETAILS,headers);return true;});
            var finished=workers.submit(()->{start.await();f.newDiagnostics().recordTransport(running.campaign(),running.attempt(),DETAILS,proof);return true;});
            start.countDown();assertThat(late.get(10,TimeUnit.SECONDS)).isTrue();assertThat(finished.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(f.newDiagnostics().findTransport(running.campaign(),running.attempt())).contains(proof);
        assertThat(f.count("live_attempt_transport_diagnostic")).isOne();
    }

    @ParameterizedTest @ValueSource(ints={403,429})
    void partialRefusalAndTimeoutRemainDurableWithoutFabricatingAReceiptOrReplacingTheFirstCause(int status) {
        Fixture f=fixture("45"); Running running=f.running();
        Map<String,List<Map<String,Object>>> evidence=f.evidence("provider_snapshot","provider_snapshot_occurrence",
                "live_call_receipt","canonical_event_observation","event_detail_observation","j5_event_data_observation");
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,status);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,headers);
        assertThat(f.jdbc.queryForObject("select http_status from live_attempt_transport_diagnostic",Integer.class)).isEqualTo(status);
        assertThat(f.jdbc.queryForObject("select response_complete from live_attempt_transport_diagnostic",Boolean.class)).isFalse();
        var first=failure("PLAYWRIGHT_TIMEOUT",running,headers.at(PlaywrightTransportDiagnostic.Phase.READING_BODY));
        f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,first);
        LiveDiagnosticStore restarted=f.newDiagnostics();
        assertThat(restarted.find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE)).contains(first);
        restarted.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,
                new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.NORMALIZATION,"RUNTIME_OR_STORAGE_FAILURE",T0.plusSeconds(40)));
        var cleanup=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_TRANSPORT_CLOSE,
                "LIVE_PROVIDER_CLEANUP_UNVERIFIED",T0.plusSeconds(41));
        restarted.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE,cleanup);
        assertThat(restarted.find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE)).contains(first);
        assertThat(restarted.find(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE)).contains(cleanup);
        f.assertEvidence(evidence);
    }

    @Test
    void transportProgressIsMonotonicAndAnIpcTimeoutCannotEraseAlreadyObservedHeaders() {
        Fixture f=fixture("45");Running running=f.running();
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,403);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,headers);
        var ipc=new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.PARENT_IPC_WAIT,30000,null,null,null,null,false);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,ipc);
        var first=failure("PLAYWRIGHT_IPC_TIMEOUT",running,null);
        f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,first);
        var stored=f.diagnostics.find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE).orElseThrow().transport();
        assertThat(stored.phase()).isEqualTo(PlaywrightTransportDiagnostic.Phase.PARENT_IPC_WAIT);
        assertThat(stored.httpStatus()).isEqualTo(403);
        assertThat(stored.requestedAt()).isEqualTo(headers.requestedAt());
        assertThat(stored.headersReceivedAt()).isEqualTo(headers.headersReceivedAt());
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,
                new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.NAVIGATION,30000,null,null,null,null,false));
        assertThat(f.newDiagnostics().find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE).orElseThrow().transport()).isEqualTo(stored);
    }

    @Test
    void knownHttpStatusTimeoutAndTimestampsCannotBeReplacedByConflictingEvidence() {
        Fixture f=fixture("45");Running running=f.running();
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,403);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,headers);
        var changedRequest=new PlaywrightTransportDiagnostic(headers.phase(),30000,T0.plusSeconds(11),T0.plusSeconds(12),403,null,false);
        var changedTimeout=new PlaywrightTransportDiagnostic(headers.phase(),10000,headers.requestedAt(),headers.headersReceivedAt(),403,null,false);
        for(var conflict:List.of(diagnostic(headers.phase(),200),changedRequest,changedTimeout))
            assertThatThrownBy(()->f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,conflict))
                    .hasMessage("LIVE_DIAGNOSTIC_EVIDENCE_CONFLICT");
        assertThat(f.jdbc.queryForObject("select http_status from live_attempt_transport_diagnostic",Integer.class)).isEqualTo(403);
        assertThat(f.jdbc.queryForObject("select timeout_ms from live_attempt_transport_diagnostic",Integer.class)).isEqualTo(30000);
    }

    @Test
    void completed200IsImmutableAndDoesNotItselfClaimASavedPayload() {
        Fixture f=fixture("45");Running running=f.running();
        var before=f.evidence("provider_snapshot","provider_snapshot_occurrence","live_call_receipt","event_detail_observation");
        var complete=diagnostic(PlaywrightTransportDiagnostic.Phase.COMPLETE,200);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,
                diagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,200));
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,complete);
        List<Map<String,Object>> saved=f.rows("live_attempt_transport_diagnostic");
        f.newDiagnostics().recordTransport(running.campaign(),running.attempt(),DETAILS,complete);
        f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,
                diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,200));
        assertThat(f.rows("live_attempt_transport_diagnostic")).isEqualTo(saved);
        assertThatThrownBy(()->f.diagnostics.recordTransport(running.campaign(),running.attempt(),DETAILS,
                diagnostic(PlaywrightTransportDiagnostic.Phase.COMPLETE,403))).hasMessage("LIVE_DIAGNOSTIC_EVIDENCE_CONFLICT");
        f.assertEvidence(before);
    }

    @Test
    void diagnosticCorrelationMustMatchItsReservedCampaignAttemptAndEndpointInJavaAndPostgres() {
        Fixture f=fixture("45");Running running=f.running();
        UUID otherCampaign=f.prepare("live-v1",List.of(f.seed(58002))).campaignId();
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,403);
        assertThatThrownBy(()->f.diagnostics.recordTransport(otherCampaign,running.attempt(),DETAILS,headers))
                .hasMessage("LIVE_DIAGNOSTIC_ATTEMPT_INVALID");
        assertThatThrownBy(()->f.diagnostics.recordTransport(running.campaign(),running.attempt(),SofascoreEndpointType.EVENT_LINEUPS,headers))
                .hasMessage("LIVE_DIAGNOSTIC_ATTEMPT_INVALID");
        assertThatThrownBy(()->f.diagnostics.recordTransport(running.campaign(),UUID.randomUUID(),DETAILS,headers))
                .hasMessage("LIVE_DIAGNOSTIC_ATTEMPT_INVALID");
        assertThatThrownBy(()->f.diagnostics.recordFailure(otherCampaign,LiveDiagnosticStore.Kind.FIRST_FAILURE,
                failure("PLAYWRIGHT_TIMEOUT",running,null))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("""
            insert into live_attempt_transport_diagnostic(attempt_id,campaign_id,endpoint_type,transport_phase,timeout_ms,response_complete)
                values (?,?,'EVENT_LINEUPS','NAVIGATION',30000,false)
            """,running.attempt(),running.campaign())).isInstanceOf(DataAccessException.class);
        assertThat(f.count("live_attempt_transport_diagnostic")).isZero();
        assertThat(f.count("live_campaign_diagnostic")).isZero();
    }

    @Test
    void firstFailureAndTransportEvidenceCannotBeMutatedDeletedOrTruncatedThroughSql() {
        Fixture f=fixture("45");Running running=f.running();
        f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,
                failure("PLAYWRIGHT_TIMEOUT",running,diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,403)));
        for(String sql:List.of("update live_campaign_diagnostic set code='PLAYWRIGHT_PROTOCOL_ERROR'",
                "update live_attempt_transport_diagnostic set http_status=200",
                "update live_attempt_transport_diagnostic set timeout_ms=10000",
                "update live_attempt_transport_diagnostic set transport_phase='HEADERS_RECEIVED'",
                "delete from live_campaign_diagnostic","delete from live_attempt_transport_diagnostic",
                "truncate live_campaign_diagnostic","truncate live_attempt_transport_diagnostic"))
            assertThatThrownBy(()->f.jdbc.execute(sql)).isInstanceOf(DataAccessException.class);
        assertThat(f.count("live_campaign_diagnostic")).isEqualTo(1);
        assertThat(f.count("live_attempt_transport_diagnostic")).isEqualTo(1);
    }

    @Test
    void cleanupEvidenceKeepsItsLatestTimeWithoutChangingThePrimaryDiagnostic() {
        Fixture f=fixture("45");Running running=f.running();
        var first=failure("PLAYWRIGHT_TIMEOUT",running,null);
        f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,first);
        var latest=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_LEASE_RELEASE,
                "LIVE_CLEANUP_OWNERSHIP_CHANGED",T0.plusSeconds(60));
        f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE,latest);
        f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE,
                new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_TRANSPORT_CLOSE,
                        "LIVE_PROVIDER_CLEANUP_UNVERIFIED",T0.plusSeconds(50)));
        assertThat(f.newDiagnostics().find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE)).contains(first);
        assertThat(f.newDiagnostics().find(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE)).contains(latest);
    }

    @Test
    void diagnosticTransactionRollbackCreatesNeitherFirstFailureNorPartialTransportRow() {
        Fixture f=fixture("45");Running running=f.running();
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(status->{
            f.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,
                    failure("PLAYWRIGHT_TIMEOUT",running,diagnostic(PlaywrightTransportDiagnostic.Phase.READING_BODY,403)));
            throw new IllegalStateException("synthetic rollback");
        })).hasMessage("synthetic rollback");
        assertThat(f.count("live_campaign_diagnostic")).isZero();
        assertThat(f.count("live_attempt_transport_diagnostic")).isZero();
    }

    @Test
    void concurrentFirstFailuresStoreOneDurableCause() throws Exception {
        Fixture f=fixture("45");Running running=f.running();CountDownLatch start=new CountDownLatch(1);
        var one=failure("PLAYWRIGHT_TIMEOUT",running,null);var two=failure("PLAYWRIGHT_IPC_TIMEOUT",running,null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();f.newDiagnostics().recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,one);return true;});
            var second=pool.submit(()->{start.await();f.newDiagnostics().recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,two);return true;});
            start.countDown();assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();assertThat(second.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(f.diagnostics.find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE).orElseThrow()).isIn(one,two);
        assertThat(f.count("live_campaign_diagnostic")).isEqualTo(1);
    }

    @Test
    void sqlRejectsArbitraryDiagnosticCodesAndInconsistentPhaseMetadata() {
        Fixture f=fixture("45");Running running=f.running();
        assertThatThrownBy(()->f.jdbc.update("""
            insert into live_campaign_diagnostic(campaign_id,kind,phase,code,occurred_at)
                values (?,'FIRST_FAILURE','TRANSPORT','ARBITRARY_ERROR_TEXT',?)
            """,running.campaign(),Timestamp.from(T0))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("""
            insert into live_attempt_transport_diagnostic(attempt_id,campaign_id,endpoint_type,transport_phase,timeout_ms,response_complete)
                values (?,?,'EVENT_DETAILS','REQUEST_SENT',30000,false)
            """,running.attempt(),running.campaign())).isInstanceOf(DataAccessException.class);
        assertThat(f.count("live_campaign_diagnostic")).isZero();
        assertThat(f.count("live_attempt_transport_diagnostic")).isZero();
    }

    @Test
    void upgradingPopulatedV42ToV50PreservesLegacyPoliciesExistingProviderSuspensionAndCompletionFallback() {
        Fixture f=fixture("42");Target target=f.seed(58001);
        Manifest v1=f.prepare("live-v1",List.of(target)),v4=f.prepare("live-v4",List.of(target)),v5=f.prepare("live-v5",List.of(target));
        UUID departure=UUID.randomUUID(),evidence=UUID.randomUUID();
        Instant finished=T0.plusSeconds(1),suspendedAt=T0.plusSeconds(2);
        f.jdbc.update("insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version) values (?,?,?)",
                departure,Timestamp.from(T0),ProviderResilienceData.POLICY_VERSION);
        f.jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) values (?,?)",departure,Timestamp.from(finished));
        f.jdbc.update("""
                insert into provider_resilience_event(event_id,kind,state_version,occurred_at,http_status,retry_not_before,campaign_id)
                    values (?,'REFUSAL',1,?,403,null,?)
                """,evidence,Timestamp.from(suspendedAt),v5.campaignId());
        f.jdbc.update("""
                update provider_resilience_state set state='SUSPENDED',version=1,changed_at=?,http_status=403,
                    suspended_at=?,retry_not_before=null,last_departure_at=?,last_departure_finished_at=?,
                    unresolved_dispatch_id=null,evidence_id=?,campaign_id=? where singleton_id=1
                """,Timestamp.from(suspendedAt),Timestamp.from(suspendedAt),Timestamp.from(T0),Timestamp.from(finished),evidence,v5.campaignId());
        var suspension=new ProviderResilienceData.Snapshot(ProviderResilienceData.State.SUSPENDED,1,suspendedAt,403,
                suspendedAt,null,T0,evidence,v5.campaignId(),finished,null);
        var before=f.evidence("live_campaign","live_event","live_grouped_policy","provider_snapshot","provider_snapshot_occurrence",
                "canonical_event_observation","event_detail_observation","provider_resilience_state","provider_resilience_event",
                "provider_departure_reservation","provider_departure_completion");
        assertThat(f.migrate("44")).isEqualTo(2);f.assertEvidence(before);
        for(Manifest manifest:List.of(v1,v4,v5)) assertThat(f.campaigns.find(manifest.campaignId()).orElseThrow().manifest()).isEqualTo(manifest);
        assertThat(f.migrate("48")).isEqualTo(4);
        assertThat(f.jdbc.queryForObject("select admission_profile from provider_departure_reservation where dispatch_id=?",String.class,departure))
                .isEqualTo("legacy-v1");
        assertThat(f.jdbc.queryForObject("select last_departure_admission_profile from provider_resilience_state where singleton_id=1",String.class))
                .isNull();
        assertThat(f.migrate("50")).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select source from provider_departure_accounting where dispatch_id=?",String.class,departure))
                .isEqualTo("COMPLETION_FALLBACK");
        var resilience=transactional(new JdbcProviderResilienceStore(f.jdbc),ProviderResilienceStore.class,f.transactions);
        assertThat(resilience.snapshot()).isEqualTo(suspension);
        assertThat(resilience.tryReserveDeparture(UUID.randomUUID(),T0.plusSeconds(4)).reason())
                .isEqualTo(ProviderResilienceData.DepartureReason.PROVIDER_SUSPENDED);
        assertThat(f.count("live_campaign_diagnostic")).isZero();
        assertThat(f.count("live_attempt_transport_diagnostic")).isZero();
        assertThat(f.migrate("50")).isZero();
    }

    @Test
    void nativeBackupRestorePreservesAllSevenResilienceTablesAndAFreeGuardWithoutRearmingTheProvider() throws Exception {
        Fixture source=fixture("50");Running running=source.running();
        Manifest v6=source.prepare("live-v6",List.of(source.seed(58002)));
        var campaign=source.campaigns.find(running.campaign()).orElseThrow();
        Ownership owner=campaign.ownership();
        UUID event=campaign.manifest().targets().getFirst().canonicalEventId();
        var resilience=transactional(new JdbcProviderResilienceStore(source.jdbc),ProviderResilienceStore.class,source.transactions);
        assertThat(resilience.tryReserveDeparture(running.attempt(),T0.plusSeconds(10)).allowed()).isTrue();
        source.campaigns.recordDispatch(owner,running.attempt(),T0.plusSeconds(10));
        var headers=diagnostic(PlaywrightTransportDiagnostic.Phase.HEADERS_RECEIVED,403);
        resilience.suspend(running.attempt(),running.campaign(),403,headers.headersReceivedAt(),null);
        var first=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.TRANSPORT,"PROVIDER_HTTP_403",
                headers.headersReceivedAt(),running.attempt(),DETAILS,headers.at(PlaywrightTransportDiagnostic.Phase.READING_BODY)
                        .withExchangeEnd(T0.plusSeconds(40),PlaywrightTransportDiagnostic.ExchangeEndReason.ABORTED,false));
        source.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE,first);
        source.campaigns.publishResult(owner,running.attempt(),new Publication("FAILED","CAMPAIGN","PLAYWRIGHT_TIMEOUT",
                T0.plusSeconds(40),null,false,null),NormalizedReferences::none);
        source.campaigns.transition(owner,event,"STOPPED_ERROR","PROVIDER_HTTP_403",T0.plusSeconds(40),running.attempt());
        source.campaigns.transition(owner,null,"STOPPED_ERROR","PROVIDER_HTTP_403",T0.plusSeconds(41),null);
        source.guard.requireCleanup(owner,T0.plusSeconds(42));
        var cleanup=new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.CLEANUP_TRANSPORT_CLOSE,
                "LIVE_PROVIDER_CLEANUP_UNVERIFIED",T0.plusSeconds(43));
        source.diagnostics.recordFailure(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE,cleanup);
        var suspended=resilience.markDepartureFinished(running.attempt(),T0.plusSeconds(45));
        source.guard.releaseAfterVerifiedCleanup(owner,T0.plusSeconds(46));
        var evidence=source.evidence("provider_resilience_state","provider_departure_reservation","provider_departure_completion",
                "provider_departure_accounting",
                "provider_resilience_event","live_attempt_transport_diagnostic","live_campaign_diagnostic");
        evidence.forEach((table,rows)->assertThat(rows).as(table+" is populated before native backup").isNotEmpty());
        String script=Files.readString(Path.of("scripts/Backup-Restore-J6.ps1"),StandardCharsets.UTF_8);
        String fingerprintSql=script.split("\\$liveLedgerFingerprintSql = @'\\r?\\n",2)[1].split("\\r?\\n'@",2)[0];
        String before=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.jdbc.queryForObject(fingerprintSql,String.class).getBytes(StandardCharsets.UTF_8)));
        String sourceDatabase=source.ds.getUrl().substring(source.ds.getUrl().lastIndexOf('/')+1);
        String restoredDatabase="diagnostic_restored_"+DATABASE.incrementAndGet();
        String dump="/tmp/diagnostic-"+UUID.randomUUID()+".dump";
        try {
            var backup=POSTGRES.execInContainer("pg_dump","--username",POSTGRES.getUsername(),"--dbname",sourceDatabase,
                    "--format=custom","--no-owner","--no-privileges","--file",dump);
            assertThat(backup.getExitCode()).as(backup.getStderr()).isZero();
            assertThat(POSTGRES.execInContainer("createdb","--username",POSTGRES.getUsername(),restoredDatabase).getExitCode()).isZero();
            var restore=POSTGRES.execInContainer("pg_restore","--username",POSTGRES.getUsername(),"--dbname",restoredDatabase,
                    "--exit-on-error","--no-owner","--no-privileges",dump);
            assertThat(restore.getExitCode()).as(restore.getStderr()).isZero();
            String url=POSTGRES.getJdbcUrl().substring(0,POSTGRES.getJdbcUrl().lastIndexOf('/')+1)+restoredDatabase;
            Fixture restored=new Fixture(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
            String after=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(restored.jdbc.queryForObject(fingerprintSql,String.class).getBytes(StandardCharsets.UTF_8)));
            assertThat(after).isEqualTo(before);
            restored.assertEvidence(evidence);
            Flyway.configure().dataSource(restored.ds).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("50")).load().validate();
            assertThat(restored.migrate("50")).isZero();
            assertThat(restored.guard.snapshot().state()).isEqualTo("FREE");
            assertThat(restored.campaigns.find(running.campaign()).orElseThrow().state()).isEqualTo("STOPPED_ERROR");
            assertThat(restored.campaigns.find(v6.campaignId()).orElseThrow().manifest()).isEqualTo(v6);
            assertThat(restored.diagnostics.find(running.campaign(),LiveDiagnosticStore.Kind.FIRST_FAILURE)).contains(first);
            assertThat(restored.diagnostics.find(running.campaign(),LiveDiagnosticStore.Kind.CLEANUP_FAILURE)).contains(cleanup);
            var restoredResilience=transactional(new JdbcProviderResilienceStore(restored.jdbc),ProviderResilienceStore.class,restored.transactions);
            assertThat(restoredResilience.snapshot()).isEqualTo(suspended);
            assertThat(restoredResilience.tryReserveDeparture(UUID.randomUUID(),T0.plus(Duration.ofDays(2))).reason())
                    .isEqualTo(ProviderResilienceData.DepartureReason.PROVIDER_SUSPENDED);
            restored.assertEvidence(evidence);
        } finally {
            POSTGRES.execInContainer("dropdb","--username",POSTGRES.getUsername(),"--if-exists","--force",restoredDatabase);
            POSTGRES.execInContainer("rm","-f",dump);
        }
    }

    @Test
    void v6PersistsItsSevenMatchSelectionAnd100300SecondGroupedProfileWithoutBorrowingLegacyCapacity() {
        Fixture f=fixture("44");List<Target> targets=new ArrayList<>();for(int i=0;i<7;i++) targets.add(f.seed(58001+i));
        Manifest v6=f.prepare("live-v6",targets);
        assertThat(f.campaigns.find(v6.campaignId()).orElseThrow().manifest()).isEqualTo(v6);
        assertThat(v6.qualifiedMatchCapacity()).isEqualTo(7);
        assertThat(f.jdbc.queryForMap("""
            select critical_interval_seconds,lineup_interval_seconds,intra_group_delay_nanos,inter_group_delay_nanos
                from live_grouped_policy where campaign_id=?
            """,v6.campaignId())).containsEntry("critical_interval_seconds",100).containsEntry("lineup_interval_seconds",300)
                .containsEntry("intra_group_delay_nanos",0L).containsEntry("inter_group_delay_nanos",1000000000L);
        assertThatThrownBy(()->f.insertPolicyRow(UUID.randomUUID(),"live-v6",8,100)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.insertPolicyRow(UUID.randomUUID(),"live-v6",7,60)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.insertPolicyRow(UUID.randomUUID(),"live-v6",7,100)).hasMessageContaining("grouped profile");
    }

    @ParameterizedTest @ValueSource(longs={0L,3000000000L})
    void v6RejectsAGroupPauseOtherThanItsOwnOneSecondPolicy(long invalidPause) {
        Fixture f=fixture("44");UUID campaign=UUID.randomUUID();
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).executeWithoutResult(status->{
            f.insertPolicyRow(campaign,"live-v6",7,100);
            f.jdbc.update("""
                insert into live_grouped_policy(campaign_id,critical_interval_seconds,lineup_interval_seconds,intra_group_delay_nanos,
                    inter_group_delay_nanos,maximum_utilization_percent,qualification_sha256,endpoint_envelopes)
                values (?,100,300,0,?,90,?,cast(? as jsonb))
                """,campaign,invalidPause,"a".repeat(64),envelopesJson());
        })).isInstanceOf(DataAccessException.class);
        assertThat(f.campaigns.find(campaign)).isEmpty();
    }

    private static PlaywrightTransportDiagnostic diagnostic(PlaywrightTransportDiagnostic.Phase phase,int status) {
        return new PlaywrightTransportDiagnostic(phase,30000,T0.plusSeconds(10),T0.plusSeconds(12),status,
                status==429?T0.plusSeconds(120):null,phase==PlaywrightTransportDiagnostic.Phase.COMPLETE);
    }
    private static LiveCampaignDiagnostic failure(String code,Running running,PlaywrightTransportDiagnostic transport) {
        return new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.TRANSPORT,code,T0.plusSeconds(40),running.attempt(),DETAILS,transport);
    }
    private static String envelopesJson() {
        return "{\"EVENT_DETAILS\":{\"requestNanos\":500000000,\"processingNanos\":100000000},"
                +"\"EVENT_INCIDENTS\":{\"requestNanos\":500000000,\"processingNanos\":100000000},"
                +"\"EVENT_STATISTICS\":{\"requestNanos\":500000000,\"processingNanos\":100000000},"
                +"\"EVENT_LINEUPS\":{\"requestNanos\":500000000,\"processingNanos\":100000000}}";
    }
    private record Running(UUID campaign,UUID attempt) { }
    private static Fixture fixture(String target) {
        String database="diagnostic_it_"+DATABASE.incrementAndGet();
        DriverManagerDataSource admin=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        new JdbcTemplate(admin).execute("create database "+database);
        String url=POSTGRES.getJdbcUrl().substring(0,POSTGRES.getJdbcUrl().lastIndexOf('/')+1)+database;
        Fixture fixture=new Fixture(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
        fixture.migrate(target);return fixture;
    }
    private static <T>T transactional(T object,Class<T> type,JdbcTransactionManager transactions) {
        ProxyFactory proxy=new ProxyFactory(object);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
    private static final class Fixture {
        final DriverManagerDataSource ds;final JdbcTemplate jdbc;final JdbcTransactionManager transactions;
        final LiveDiagnosticStore diagnostics;final LiveCampaignStore campaigns;final ProviderCampaignGuardStore guard;
        final RawManualCallSnapshotStore raw;final CanonicalEventStore canonical;final EventDetailsStore details;
        Fixture(DriverManagerDataSource ds) {
            this.ds=ds;jdbc=new JdbcTemplate(ds);transactions=new JdbcTransactionManager(ds);var named=new NamedParameterJdbcTemplate(jdbc);
            diagnostics=newDiagnostics();raw=transactional(new JdbcRawManualCallSnapshotStore(named),RawManualCallSnapshotStore.class,transactions);
            canonical=transactional(new JdbcCanonicalEventStore(named),CanonicalEventStore.class,transactions);
            details=transactional(new JdbcEventDetailsStore(named),EventDetailsStore.class,transactions);
            campaigns=transactional(new JdbcLiveCampaignStore(jdbc,raw),LiveCampaignStore.class,transactions);
            guard=transactional(new JdbcProviderCampaignGuardStore(jdbc),ProviderCampaignGuardStore.class,transactions);
        }
        LiveDiagnosticStore newDiagnostics() {return transactional(new JdbcLiveDiagnosticStore(jdbc),LiveDiagnosticStore.class,transactions);}
        int migrate(String version) {return Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version)).load().migrate().migrationsExecuted;}
        long count(String table) {return jdbc.queryForObject("select count(*) from "+table,Long.class);}
        List<Map<String,Object>> rows(String table) {return jdbc.queryForList("select to_jsonb(t)::text as row from "+table+" t order by to_jsonb(t)::text");}
        Map<String,List<Map<String,Object>>> evidence(String...tables) {var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:tables) result.put(table,rows(table));return result;}
        void assertEvidence(Map<String,List<Map<String,Object>>> before) {before.forEach((table,rows)->assertThat(rows(table)).as(table).isEqualTo(rows));}
        Target seed(long event) {
            var receipt=new RawManualCallSnapshot(DETAILS,"EVENT_DETAILS|eventId="+event,T0.minusSeconds(100),T0.minusSeconds(99),
                    200,"application/json",Duration.ofSeconds(1),RawPayloadEvidence.capture("{\"synthetic\":true}".getBytes(StandardCharsets.UTF_8)),
                    "event-details-v2",RawSnapshotSchemaStatus.RAW_ONLY,null);
            var saved=raw.save(receipt);
            var data=new EventDetails(event,T0,new ScheduledTeam(11,"Home"),new ScheduledTeam(22,"Away"),
                    new ScheduledEventStatus("inprogress",Optional.of("First half")),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
            var source=EventSourceTrace.providerSnapshot(saved.snapshotId(),saved.payloadSha256(),"event-details-v2",receipt.receivedAt());
            long canonicalObservation=canonical.save(CanonicalEventObservation.from(data.asScheduledEvent(),source)).observationId();
            EventDetailObservation detailObservation=EventDetailObservation.from(CanonicalEventIdentity.sofascore(event),data,source);
            if (hasEventOfficialsColumns()) details.save(detailObservation);
            else savePreV46Details(detailObservation);
            return new Target(CanonicalEventIdentity.sofascore(event).value(),event,canonicalObservation,saved.snapshotId());
        }
        private void savePreV46Details(EventDetailObservation observation) {
            EventDetails data=observation.details();EventSourceTrace source=observation.source();
            jdbc.update("""
                insert into event_detail_observation(canonical_event_id,source_kind,source_reference,source_snapshot_id,
                    source_payload_sha256,parser_version,source_received_at,starts_at,home_team_provider_id,home_team_name,
                    away_team_provider_id,away_team_name,status_type,status_description,normalized_sha256)
                values (?,'PROVIDER_SNAPSHOT',?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict(canonical_event_id,source_kind,source_reference,normalized_sha256) do nothing
                """,observation.identity().value(),source.sourceReference(),source.snapshotId().orElseThrow(),
                    source.payloadSha256(),source.parserVersion(),Timestamp.from(source.receivedAt()),
                    Timestamp.from(data.startsAt()),data.homeTeam().providerTeamId(),data.homeTeam().name(),
                    data.awayTeam().providerTeamId(),data.awayTeam().name(),data.status().type(),
                    data.status().description().orElse(null),observation.normalizedSha256());
        }
        private boolean hasEventOfficialsColumns() {
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from information_schema.columns
                    where table_schema='public' and table_name='event_detail_observation'
                        and column_name='home_manager_name'
                )
                """,Boolean.class));
        }
        Manifest prepare(String policy,List<Target> targets) {
            Manifest manifest;
            if("live-v1".equals(policy)) manifest=new Manifest(UUID.randomUUID(),"a".repeat(64),policy,T0,T0.plusSeconds(300),
                    Duration.ofHours(4),100,100,100000000,1,targets);
            else {
                var envelopes=new EnumMap<SofascoreEndpointType,EndpointEnvelope>(SofascoreEndpointType.class);
                for(var endpoint:List.of(DETAILS,SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_INCIDENTS,SofascoreEndpointType.EVENT_LINEUPS))
                    envelopes.put(endpoint,new EndpointEnvelope(Duration.ofMillis(500),Duration.ofMillis(100)));
                var grouped=new GroupedAdmissionProfile(envelopes,"b".repeat(64),policy);
                var profile=new AdmissionProfile(Duration.ofMillis(500),Duration.ofMillis(100),"b".repeat(64),grouped);
                manifest=new Manifest(UUID.randomUUID(),"a".repeat(64),policy,T0,T0.plusSeconds(300),Duration.ofHours(4),
                        "live-v4".equals(policy)?1000:2500,"live-v4".equals(policy)?3000:20000,15728640000L,
                        "live-v6".equals(policy)?7:20,targets,profile,grouped.criticalInterval());
            }
            campaigns.prepare(manifest);return manifest;
        }
        Running running() {
            Target target=seed(58001);Manifest manifest=prepare("live-v1",List.of(target));
            Ownership owner=guard.tryAcquire(manifest.campaignId(),new Owner(UUID.randomUUID(),1234,T0.minusSeconds(50)),T0).orElseThrow().ownership();
            campaigns.launch(manifest.campaignId(),manifest.manifestSha256(),owner,T0.plusSeconds(1));
            ReservedAttempt attempt=campaigns.reserveAttempt(new AttemptRequest(owner,UUID.randomUUID(),target.canonicalEventId(),0,
                    DETAILS,"J4_WAIT",T0.plusSeconds(10),T0.plusSeconds(10),false)).orElseThrow();
            return new Running(manifest.campaignId(),attempt.attemptId());
        }
        void insertPolicyRow(UUID id,String policy,int capacity,int interval) {
            jdbc.update("""
                insert into live_campaign(campaign_id,manifest_sha256,policy_version,prepared_at,expires_at,duration_seconds,
                    maximum_calls_per_event,maximum_calls,maximum_bytes,qualified_match_capacity,target_count,
                    request_envelope_nanos,processing_envelope_nanos,qualification_sha256,cycle_interval_seconds)
                values (?,?,?,?,?,14400,2500,20000,15728640000,?,1,500000000,100000000,'',?)
                """,id,"a".repeat(64),policy,Timestamp.from(T0),Timestamp.from(T0.plusSeconds(300)),capacity,interval);
        }
    }
}
