package com.bettingproject.sofascorelocal.adapter.persistence.live;

import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;
import com.bettingproject.sofascorelocal.port.ProviderResilienceStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static com.bettingproject.sofascorelocal.domain.provider.ProviderResilienceData.*;

/** A database failure/missing singleton propagates: it can never grant a provider departure. */
@Repository
public class JdbcProviderResilienceStore implements ProviderResilienceStore {
    private final JdbcTemplate jdbc;
    public JdbcProviderResilienceStore(JdbcTemplate jdbc) { this.jdbc=Objects.requireNonNull(jdbc); }

    @Override @Transactional(readOnly=true)
    public Snapshot snapshot() { return read(false); }

    @Override @Transactional(readOnly=true)
    public DepartureDecision departureDecision(Instant at) { return decide(read(false),timestamp(at)); }

    @Override @Transactional
    public DepartureDecision tryReserveDeparture(UUID dispatchId, Instant at) {
        Objects.requireNonNull(dispatchId); Instant now=timestamp(at);
        Snapshot current=read(true);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from provider_departure_reservation where dispatch_id=?)",Boolean.class,dispatchId)))
            return new DepartureDecision(false,DepartureReason.DISPATCH_ALREADY_RESERVED,null,current);
        DepartureDecision decision=decide(current,now);
        if (!decision.allowed()) return decision;
        jdbc.update("insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version) values (?,?,?)",
                dispatchId,sql(now),POLICY_VERSION);
        jdbc.update("update provider_resilience_state set last_departure_at=?,unresolved_dispatch_id=? where singleton_id=1",sql(now),dispatchId);
        return new DepartureDecision(true,DepartureReason.ALLOWED,now,read(false));
    }

    @Override @Transactional
    public Snapshot markDepartureFinished(UUID dispatchId, Instant at) {
        Objects.requireNonNull(dispatchId); Instant finished=timestamp(at); Snapshot current=read(true);
        // PostgreSQL stores microseconds; round the closing fence up so precision cannot shorten the delay.
        if (finished.isBefore(at)) finished=finished.plusNanos(1000);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from provider_departure_completion where dispatch_id=?)",Boolean.class,dispatchId)))
            return current;
        if (!dispatchId.equals(current.unresolvedDispatchId()))
            throw new IllegalStateException("PROVIDER_DEPARTURE_NOT_CURRENT");
        if (finished.isBefore(current.lastDepartureAt())) throw new IllegalStateException("PROVIDER_CLOCK_REGRESSION");
        jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) values (?,?)",dispatchId,sql(finished));
        jdbc.update("""
            update provider_resilience_state set last_departure_finished_at=?,unresolved_dispatch_id=null where singleton_id=1
            """,sql(finished));
        return read(false);
    }

    @Override @Transactional
    public Snapshot suspend(UUID evidenceId, UUID campaignId, int httpStatus, Instant observedAt, Instant retryNotBefore) {
        Objects.requireNonNull(evidenceId); Instant observed=timestamp(observedAt);
        if (httpStatus!=403 && httpStatus!=429) throw new IllegalArgumentException("only a confirmed 403 or 429 suspends provider access");
        Instant retry=ProviderResilienceData.validateRetryNotBefore(observed,retryNotBefore);
        Snapshot current=read(true);
        List<Refusal> existing=jdbc.query("select * from provider_resilience_event where event_id=?",(rs,row)->
                new Refusal(rs.getString("kind"),rs.getObject("http_status",Integer.class),at(rs,"occurred_at"),
                        at(rs,"retry_not_before"),rs.getObject("campaign_id",UUID.class)),evidenceId);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().equals(new Refusal("REFUSAL",httpStatus,observed,retry,campaignId)))
                throw new IllegalArgumentException("PROVIDER_REFUSAL_EVIDENCE_COLLISION");
            return current;
        }
        long nextVersion=current.version()+1;
        jdbc.update("""
            insert into provider_resilience_event(event_id,kind,state_version,occurred_at,http_status,retry_not_before,campaign_id)
                values (?,'REFUSAL',?,?,?,?,?)
            """,evidenceId,nextVersion,sql(observed),httpStatus,sql(retry),campaignId);
        boolean alreadySuspended=current.state()==State.SUSPENDED;
        // Repeated refusals extend the hold, never replace the primary reason or shorten Retry-After.
        Instant deadline=alreadySuspended ? latest(current.retryNotBefore(),retry) : retry;
        Instant primaryAt=alreadySuspended ? current.suspendedAt() : observed;
        if (deadline!=null) deadline=latest(primaryAt,deadline);
        jdbc.update("""
            update provider_resilience_state set state='SUSPENDED',version=?,changed_at=?,http_status=?,
                suspended_at=?,retry_not_before=?,evidence_id=?,campaign_id=? where singleton_id=1
            """,nextVersion,sql(latest(current.changedAt(),observed)),alreadySuspended?current.httpStatus():httpStatus,
                sql(primaryAt),sql(deadline),alreadySuspended?current.evidenceId():evidenceId,
                alreadySuspended?current.campaignId():campaignId);
        return read(false);
    }

    @Override @Transactional
    public Snapshot rearm(long expectedVersion, Instant at) {
        Instant now=timestamp(at); Snapshot current=read(true);
        if (current.version()!=expectedVersion) throw new IllegalStateException("PROVIDER_REARM_STALE_VERSION");
        if (current.state()!=State.SUSPENDED) throw new IllegalStateException("PROVIDER_REARM_NOT_SUSPENDED");
        if (now.isBefore(current.changedAt()) || (current.lastDepartureAt()!=null && now.isBefore(current.lastDepartureAt())))
            throw new IllegalStateException("PROVIDER_CLOCK_REGRESSION");
        if (current.retryNotBefore()!=null && now.isBefore(current.retryNotBefore()))
            throw new IllegalStateException("PROVIDER_REARM_TOO_EARLY");
        long nextVersion=current.version()+1;
        jdbc.update("""
            insert into provider_resilience_event(event_id,kind,state_version,occurred_at)
                values (?,'MANUAL_REARM',?,?)
            """,UUID.randomUUID(),nextVersion,sql(now));
        jdbc.update("update provider_resilience_state set state='OPEN',version=?,changed_at=? where singleton_id=1",
                nextVersion,sql(now));
        return read(false);
    }

    private DepartureDecision decide(Snapshot state, Instant now) {
        if (state.state()==State.SUSPENDED)
            return new DepartureDecision(false,DepartureReason.PROVIDER_SUSPENDED,state.retryNotBefore(),state);
        if (state.unresolvedDispatchId()!=null)
            return new DepartureDecision(false,DepartureReason.DEPARTURE_UNRESOLVED,null,state);
        if (state.lastDepartureAt()!=null && now.isBefore(state.lastDepartureAt()))
            return new DepartureDecision(false,DepartureReason.CLOCK_REGRESSION,null,state);
        if (state.lastDepartureFinishedAt()!=null && now.isBefore(state.lastDepartureFinishedAt()))
            return new DepartureDecision(false,DepartureReason.CLOCK_REGRESSION,null,state);
        Instant eligible=now;
        if (state.lastDepartureFinishedAt()!=null) eligible=latest(eligible,state.lastDepartureFinishedAt().plus(MINIMUM_DEPARTURE_INTERVAL));
        eligible=latest(eligible,windowDeadline(now,Duration.ofMinutes(1),MAXIMUM_DEPARTURES_PER_MINUTE));
        eligible=latest(eligible,windowDeadline(now,Duration.ofHours(1),MAXIMUM_DEPARTURES_PER_HOUR));
        boolean allowed=!eligible.isAfter(now);
        return new DepartureDecision(allowed,allowed?DepartureReason.ALLOWED:DepartureReason.RATE_LIMITED,eligible,state);
    }

    /** Rolling windows are (now-window, now]; each reservation ages only from its completion. */
    private Instant windowDeadline(Instant now, Duration window, int maximum) {
        List<Instant> boundary=jdbc.query("""
            select finished_at from provider_departure_completion where finished_at>? and finished_at<=?
                order by finished_at desc offset ? limit 1
            """,(rs,row)->at(rs,"finished_at"),sql(now.minus(window)),sql(now),maximum-1);
        return boundary.isEmpty()?null:boundary.getFirst().plus(window);
    }

    private Snapshot read(boolean lock) {
        return jdbc.queryForObject("select * from provider_resilience_state where singleton_id=1"+(lock?" for update":""),
                (rs,row)->{
                    if (!POLICY_VERSION.equals(rs.getString("policy_version")))
                        throw new IllegalStateException("PROVIDER_RESILIENCE_POLICY_UNSUPPORTED");
                    return new Snapshot(State.valueOf(rs.getString("state")),rs.getLong("version"),at(rs,"changed_at"),
                            rs.getObject("http_status",Integer.class),at(rs,"suspended_at"),at(rs,"retry_not_before"),
                            at(rs,"last_departure_at"),rs.getObject("evidence_id",UUID.class),rs.getObject("campaign_id",UUID.class),
                            at(rs,"last_departure_finished_at"),rs.getObject("unresolved_dispatch_id",UUID.class));
                });
    }
    private record Refusal(String kind,Integer httpStatus,Instant at,Instant retryNotBefore,UUID campaignId) { }
    private static Instant latest(Instant first,Instant second) {
        if (first==null) return second;
        return second==null || first.isAfter(second)?first:second;
    }
    private static Timestamp sql(Instant at) {return at==null?null:Timestamp.from(at);}
    private static Instant at(ResultSet rs,String name) throws SQLException {
        Timestamp value=rs.getTimestamp(name);return value==null?null:value.toInstant();
    }
}
