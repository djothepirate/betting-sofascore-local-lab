package com.bettingproject.sofascorelocal.adapter.persistence.live;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Separate, bounded diagnostics: raw snapshots and result history are never rewritten. */
@Repository
public class JdbcLiveDiagnosticStore implements LiveDiagnosticStore {
    private final JdbcTemplate jdbc;
    public JdbcLiveDiagnosticStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void recordTransport(UUID campaignId, UUID attemptId, SofascoreEndpointType endpoint,
                                PlaywrightTransportDiagnostic d) {
        Objects.requireNonNull(d); Objects.requireNonNull(endpoint);
        // Serialize even the first insert on the reserved call, not on a possibly absent diagnostic row.
        var identity=jdbc.query("select campaign_id,endpoint from live_call where attempt_id=? for update",
                (r,n)->new Identity((UUID)r.getObject("campaign_id"),r.getString("endpoint")),attemptId);
        if(identity.isEmpty() || !identity.getFirst().equals(new Identity(campaignId,endpoint.name())))
            throw new IllegalStateException("LIVE_DIAGNOSTIC_ATTEMPT_INVALID");
        var old=jdbc.query("select * from live_attempt_transport_diagnostic where attempt_id=?",(r,n)->transport(r),attemptId);
        PlaywrightTransportDiagnostic incoming=normalize(d);
        PlaywrightTransportDiagnostic merged=old.isEmpty()?incoming:merge(old.getFirst(),incoming);
        if(!old.isEmpty() && old.getFirst().equals(merged)) return;
        jdbc.update("""
            insert into live_attempt_transport_diagnostic
                (attempt_id,campaign_id,endpoint_type,transport_phase,timeout_ms,requested_at,
                 headers_received_at,http_status,retry_not_before,response_complete,
                 exchange_ended_at,exchange_end_reason,context_reusable)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?)
            on conflict(attempt_id) do update set transport_phase=excluded.transport_phase,
                timeout_ms=excluded.timeout_ms,
                requested_at=excluded.requested_at,headers_received_at=excluded.headers_received_at,
                http_status=excluded.http_status,retry_not_before=excluded.retry_not_before,
                response_complete=excluded.response_complete,exchange_ended_at=excluded.exchange_ended_at,
                exchange_end_reason=excluded.exchange_end_reason,context_reusable=excluded.context_reusable
            """,attemptId,campaignId,endpoint.name(),merged.phase().name(),merged.requestTimeoutMillis(),
                ts(merged.requestedAt()),ts(merged.headersReceivedAt()),merged.httpStatus(),
                ts(merged.retryAfterNotBefore()),merged.responseComplete(),ts(merged.exchangeEndedAt()),
                merged.exchangeEndReason()==null?null:merged.exchangeEndReason().name(),merged.contextReusable());
    }

    @Override @Transactional
    public void recordFailure(UUID campaignId, Kind kind, LiveCampaignDiagnostic d) {
        Objects.requireNonNull(kind); Objects.requireNonNull(d);
        if(jdbc.query("select campaign_id from live_campaign where campaign_id=? for update",(r,n)->r.getObject(1),campaignId).isEmpty())
            throw new IllegalStateException("LIVE_DIAGNOSTIC_CAMPAIGN_INVALID");
        if(kind==Kind.FIRST_FAILURE && Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from live_campaign_diagnostic where campaign_id=? and kind='FIRST_FAILURE')
                """,Boolean.class,campaignId))) return;
        if (d.attemptId() != null && d.transport() != null)
            recordTransport(campaignId, d.attemptId(), d.endpoint(), d.transport());
        // The first cause is immutable, including across a lost acknowledgement or restart.
        String conflict = kind == Kind.FIRST_FAILURE ? "do nothing" : """
                do update set phase=excluded.phase,code=excluded.code,occurred_at=excluded.occurred_at,
                    attempt_id=excluded.attempt_id,endpoint_type=excluded.endpoint_type
                where excluded.occurred_at>=live_campaign_diagnostic.occurred_at
                """;
        jdbc.update("""
            insert into live_campaign_diagnostic(campaign_id,kind,phase,code,occurred_at,attempt_id,endpoint_type)
            values (?,?,?,?,?,?,?) on conflict(campaign_id,kind)
            """ + conflict, campaignId, kind.name(), d.phase().name(), d.code(), ts(d.occurredAt()),
                d.attemptId(), d.endpoint() == null ? null : d.endpoint().name());
    }

    @Override @Transactional(readOnly=true)
    public Optional<LiveCampaignDiagnostic> find(UUID campaignId, Kind kind) {
        return jdbc.query("""
            select d.*,t.transport_phase,t.timeout_ms,t.requested_at,t.headers_received_at,
                t.http_status,t.retry_not_before,t.response_complete,t.exchange_ended_at,t.exchange_end_reason,t.context_reusable
            from live_campaign_diagnostic d left join live_attempt_transport_diagnostic t on t.attempt_id=d.attempt_id
            where d.campaign_id=? and d.kind=?
            """, (r,n) -> {
                var transport = r.getString("transport_phase") == null ? null : transport(r);
                String family=r.getString("endpoint_type");
                return new LiveCampaignDiagnostic(LiveCampaignDiagnostic.Phase.valueOf(r.getString("phase")),
                    r.getString("code"),instant(r,"occurred_at"),(UUID)r.getObject("attempt_id"),
                    family==null?null:SofascoreEndpointType.valueOf(family),transport);
            }, campaignId,kind.name()).stream().findFirst();
    }

    @Override @Transactional(readOnly=true)
    public Optional<PlaywrightTransportDiagnostic> findTransport(UUID campaignId, UUID attemptId) {
        return jdbc.query("select * from live_attempt_transport_diagnostic where campaign_id=? and attempt_id=?",
                (r,n)->transport(r),campaignId,attemptId).stream().findFirst();
    }

    private record Identity(UUID campaignId,String endpoint) { }
    private static PlaywrightTransportDiagnostic transport(ResultSet r) throws SQLException {
        return new PlaywrightTransportDiagnostic(PlaywrightTransportDiagnostic.Phase.valueOf(r.getString("transport_phase")),
                r.getInt("timeout_ms"),instant(r,"requested_at"),instant(r,"headers_received_at"),
                (Integer)r.getObject("http_status"),instant(r,"retry_not_before"),r.getBoolean("response_complete"),
                instant(r,"exchange_ended_at"),r.getString("exchange_end_reason")==null?null:
                    PlaywrightTransportDiagnostic.ExchangeEndReason.valueOf(r.getString("exchange_end_reason")),
                r.getBoolean("context_reusable"));
    }
    private static PlaywrightTransportDiagnostic normalize(PlaywrightTransportDiagnostic d) {
        return new PlaywrightTransportDiagnostic(d.phase(),d.requestTimeoutMillis(),micros(d.requestedAt()),
                micros(d.headersReceivedAt()),d.httpStatus(),micros(d.retryAfterNotBefore()),d.responseComplete(),
                micros(d.exchangeEndedAt()),d.exchangeEndReason(),d.contextReusable());
    }
    private static PlaywrightTransportDiagnostic merge(PlaywrightTransportDiagnostic previous,PlaywrightTransportDiagnostic incoming) {
        if(previous.requestTimeoutMillis()!=incoming.requestTimeoutMillis())
            throw new IllegalStateException("LIVE_DIAGNOSTIC_EVIDENCE_CONFLICT");
        Instant requested=known(previous.requestedAt(),incoming.requestedAt());
        Instant headers=known(previous.headersReceivedAt(),incoming.headersReceivedAt());
        Integer status=known(previous.httpStatus(),incoming.httpStatus());
        Instant retry=known(previous.retryAfterNotBefore(),incoming.retryAfterNotBefore());
        Instant ended=known(previous.exchangeEndedAt(),incoming.exchangeEndedAt());
        var endReason=known(previous.exchangeEndReason(),incoming.exchangeEndReason());
        var phase=rank(incoming.phase())>rank(previous.phase())?incoming.phase():previous.phase();
        return new PlaywrightTransportDiagnostic(phase,previous.requestTimeoutMillis(),requested,headers,status,retry,
                phase==PlaywrightTransportDiagnostic.Phase.COMPLETE,ended,endReason,
                previous.contextReusable() || incoming.contextReusable());
    }
    private static int rank(PlaywrightTransportDiagnostic.Phase phase) {
        return switch(phase) {
            case NAVIGATION->0;case REQUEST_SENT->1;case HEADERS_RECEIVED->2;case READING_BODY->3;
            case PARENT_IPC_WAIT->4;case COMPLETE->5;
        };
    }
    private static <T>T known(T previous,T incoming) {
        if(previous!=null && incoming!=null && !previous.equals(incoming))
            throw new IllegalStateException("LIVE_DIAGNOSTIC_EVIDENCE_CONFLICT");
        return previous==null?incoming:previous;
    }
    private static Instant micros(Instant at) {return at==null?null:at.truncatedTo(ChronoUnit.MICROS);}
    private static Timestamp ts(Instant at) { return at==null?null:Timestamp.from(micros(at)); }
    private static Instant instant(ResultSet r,String column) throws SQLException {
        Timestamp value=r.getTimestamp(column); return value==null?null:value.toInstant();
    }
}
