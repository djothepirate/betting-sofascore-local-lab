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
    public DepartureDecision departureDecision(Instant at) {
        return departureDecision(DepartureProfile.LEGACY_V1,at);
    }

    @Override @Transactional(readOnly=true)
    public DepartureDecision departureDecision(DepartureProfile profile, Instant at) {
        return decide(readState(false),Objects.requireNonNull(profile),timestamp(at));
    }

    /**
     * A campaign-start preview proves that all of its initial departures have
     * room in the same shared windows. It is deliberately read-only: actual
     * departures still reserve atomically at worker dispatch time.
     */
    @Override @Transactional(readOnly=true)
    public DepartureDecision departureCapacityDecision(DepartureProfile profile, int requiredDepartures, Instant at) {
        DepartureProfile requestedProfile=Objects.requireNonNull(profile);
        if (requiredDepartures < 1 || requiredDepartures > requestedProfile.maximumDeparturesPerMinute()
                || requiredDepartures > requestedProfile.maximumDeparturesPerHour())
            throw new IllegalArgumentException("PROVIDER_DEPARTURE_CAPACITY_INVALID");
        return decide(readState(false),requestedProfile,timestamp(at),requiredDepartures);
    }

    @Override @Transactional
    public DepartureDecision tryReserveDeparture(UUID dispatchId, Instant at) {
        return tryReserveDeparture(dispatchId,DepartureProfile.LEGACY_V1,at);
    }

    @Override @Transactional
    public DepartureDecision tryReserveDeparture(UUID dispatchId, DepartureProfile profile, Instant at) {
        Objects.requireNonNull(dispatchId); Objects.requireNonNull(profile); Instant now=timestamp(at);
        StateRow current=readState(true);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from provider_departure_reservation where dispatch_id=?)",Boolean.class,dispatchId)))
            return new DepartureDecision(false,DepartureReason.DISPATCH_ALREADY_RESERVED,null,current.snapshot());
        DepartureDecision decision=decide(current,profile,now);
        if (!decision.allowed()) return decision;
        jdbc.update("insert into provider_departure_reservation(dispatch_id,reserved_at,policy_version,admission_profile) values (?,?,?,?)",
                dispatchId,sql(now),POLICY_VERSION,profile.persistenceValue());
        jdbc.update("update provider_resilience_state set last_departure_at=?,unresolved_dispatch_id=? where singleton_id=1",sql(now),dispatchId);
        return new DepartureDecision(true,DepartureReason.ALLOWED,now,read(false));
    }

    @Override @Transactional
    public void recordAuthenticatedV8Departure(UUID dispatchId, Instant requestedAt, Instant observedAt) {
        Objects.requireNonNull(dispatchId); Objects.requireNonNull(requestedAt); Objects.requireNonNull(observedAt);
        if (!supportedTimestamp(requestedAt) || !supportedTimestamp(observedAt))
            throw new IllegalStateException("PROVIDER_REQUESTED_TIMESTAMP_INVALID");
        // Compare the unrounded evidence first: PostgreSQL's microsecond storage
        // must never turn a worker instant that follows the parent observation
        // into an apparently valid equality.
        if (requestedAt.isAfter(observedAt))
            throw new IllegalStateException("PROVIDER_REQUESTED_TIMESTAMP_INCOHERENT");
        Instant requested=timestamp(requestedAt);
        StateRow current=readState(true);
        if (!dispatchId.equals(current.snapshot().unresolvedDispatchId()))
            throw new IllegalStateException("PROVIDER_DEPARTURE_NOT_CURRENT");
        List<AccountingRow> existing=jdbc.query("""
                select departure_at,source from provider_departure_accounting where dispatch_id=?
                """,(rs,row)->new AccountingRow(at(rs,"departure_at"),rs.getString("source")),dispatchId);
        if (!existing.isEmpty()) {
            AccountingRow row=existing.getFirst();
            if ("AUTHENTICATED_WORKER_REQUEST".equals(row.source()) && row.departureAt().equals(requested)) return;
            throw new IllegalStateException("PROVIDER_REQUESTED_TIMESTAMP_CONFLICT");
        }
        List<ReservationRow> reservations=jdbc.query("""
                select reserved_at,admission_profile from provider_departure_reservation where dispatch_id=?
                """,(rs,row)->new ReservationRow(at(rs,"reserved_at"),rs.getString("admission_profile")),dispatchId);
        if (reservations.isEmpty()) throw new IllegalStateException("PROVIDER_DEPARTURE_RESERVATION_MISSING");
        ReservationRow reservation=reservations.getFirst();
        if (DepartureProfile.fromPersistenceValue(reservation.profile()) != DepartureProfile.LIVE_V8)
            throw new IllegalStateException("PROVIDER_AUTHENTICATED_DEPARTURE_PROFILE_INVALID");
        if (requested.isBefore(reservation.reservedAt()))
            throw new IllegalStateException("PROVIDER_REQUESTED_BEFORE_RESERVATION");
        jdbc.update("""
                insert into provider_departure_accounting(dispatch_id,departure_at,source)
                    values (?,?,'AUTHENTICATED_WORKER_REQUEST')
                """,dispatchId,sql(requested));
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
        DepartureProfile profile=DepartureProfile.fromPersistenceValue(jdbc.queryForObject(
                "select admission_profile from provider_departure_reservation where dispatch_id=?",String.class,dispatchId));
        jdbc.update("insert into provider_departure_completion(dispatch_id,finished_at) values (?,?)",dispatchId,sql(finished));
        jdbc.update("""
            update provider_resilience_state set last_departure_finished_at=?,last_departure_admission_profile=?,
                unresolved_dispatch_id=null where singleton_id=1
            """,sql(finished),profile.persistenceValue());
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

    private DepartureDecision decide(StateRow stateRow, DepartureProfile requestedProfile, Instant now) {
        return decide(stateRow, requestedProfile, now, 1);
    }

    private DepartureDecision decide(StateRow stateRow, DepartureProfile requestedProfile, Instant now,
                                     int requiredDepartures) {
        Snapshot state=stateRow.snapshot();
        if (state.state()==State.SUSPENDED)
            return new DepartureDecision(false,DepartureReason.PROVIDER_SUSPENDED,state.retryNotBefore(),state);
        if (state.unresolvedDispatchId()!=null)
            return new DepartureDecision(false,DepartureReason.DEPARTURE_UNRESOLVED,null,state);
        if (state.lastDepartureAt()!=null && now.isBefore(state.lastDepartureAt()))
            return new DepartureDecision(false,DepartureReason.CLOCK_REGRESSION,null,state);
        if (state.lastDepartureFinishedAt()!=null && now.isBefore(state.lastDepartureFinishedAt()))
            return new DepartureDecision(false,DepartureReason.CLOCK_REGRESSION,null,state);
        Instant fenceDeadline=null;
        if (state.lastDepartureFinishedAt()!=null) {
            Duration fence=requestedProfile.minimumDepartureInterval().compareTo(stateRow.lastDepartureProfile().minimumDepartureInterval())>=0
                    ? requestedProfile.minimumDepartureInterval() : stateRow.lastDepartureProfile().minimumDepartureInterval();
            fenceDeadline=state.lastDepartureFinishedAt().plus(fence);
        }
        // Pressure is common to all profiles. The incoming profile controls its own
        // ceiling, while the durable windows include every completed real departure.
        Instant rateDeadline=capacityDeadline(now,Duration.ofMinutes(1),
                requestedProfile.maximumDeparturesPerMinute(),requiredDepartures);
        rateDeadline=latest(rateDeadline,capacityDeadline(now,Duration.ofHours(1),
                requestedProfile.maximumDeparturesPerHour(),requiredDepartures));
        // A rolling-window ceiling is durable pressure even when its release is
        // shorter than the local fence.  V8 alone exposes a completed-exchange
        // fence explicitly, so the scheduler can retain the same pending due
        // without recording a missed collection. Historical policies keep their
        // established RATE_LIMITED surface.
        if (rateDeadline!=null && rateDeadline.isAfter(now))
            return new DepartureDecision(false,DepartureReason.RATE_LIMITED,rateDeadline,state);
        if (fenceDeadline!=null && fenceDeadline.isAfter(now)) {
            DepartureReason reason=requestedProfile==DepartureProfile.LIVE_V8
                    ? DepartureReason.POST_EXCHANGE_FENCE : DepartureReason.RATE_LIMITED;
            return new DepartureDecision(false,reason,fenceDeadline,state);
        }
        return new DepartureDecision(true,DepartureReason.ALLOWED,now,state);
    }

    /**
     * Rolling windows are (now-window, now].  V8 charges the immutable worker
     * request evidence when present; all pre-V50 and unproven exchanges retain
     * the conservative completion fallback created by the database trigger.
     */
    private Instant windowDeadline(Instant now, Duration window, int maximum) {
        List<Instant> boundary=jdbc.query("""
            select departure_at from provider_departure_accounting where departure_at>? and departure_at<=?
                order by departure_at desc offset ? limit 1
            """,(rs,row)->at(rs,"departure_at"),sql(now.minus(window)),sql(now),maximum-1);
        return boundary.isEmpty()?null:boundary.getFirst().plus(window);
    }

    /**
     * Return the earliest release at which {@code requiredDepartures} free
     * slots exist. The range is intentionally based on the immutable provider
     * accounting table, so V8 uses authenticated REQUEST_SENT evidence when it
     * is available and the conservative completion fallback otherwise.
     */
    private Instant capacityDeadline(Instant now, Duration window, int maximum, int requiredDepartures) {
        if (requiredDepartures == 1) return windowDeadline(now, window, maximum);
        List<Instant> departures=jdbc.query("""
            select departure_at from provider_departure_accounting where departure_at>? and departure_at<=?
                order by departure_at asc
            """,(rs,row)->at(rs,"departure_at"),sql(now.minus(window)),sql(now));
        int available=maximum-departures.size();
        if (available>=requiredDepartures) return null;
        return departures.get(requiredDepartures-available-1).plus(window);
    }

    private Snapshot read(boolean lock) { return readState(lock).snapshot(); }

    private StateRow readState(boolean lock) {
        return jdbc.queryForObject("select * from provider_resilience_state where singleton_id=1"+(lock?" for update":""),
                (rs,row)->{
                    if (!POLICY_VERSION.equals(rs.getString("policy_version")))
                        throw new IllegalStateException("PROVIDER_RESILIENCE_POLICY_UNSUPPORTED");
                    Snapshot snapshot=new Snapshot(State.valueOf(rs.getString("state")),rs.getLong("version"),at(rs,"changed_at"),
                            rs.getObject("http_status",Integer.class),at(rs,"suspended_at"),at(rs,"retry_not_before"),
                            at(rs,"last_departure_at"),rs.getObject("evidence_id",UUID.class),rs.getObject("campaign_id",UUID.class),
                            at(rs,"last_departure_finished_at"),rs.getObject("unresolved_dispatch_id",UUID.class));
                    return new StateRow(snapshot,DepartureProfile.fromPersistenceValue(
                            rs.getString("last_departure_admission_profile")));
                });
    }
    private record StateRow(Snapshot snapshot, DepartureProfile lastDepartureProfile) { }
    private record ReservationRow(Instant reservedAt,String profile) { }
    private record AccountingRow(Instant departureAt,String source) { }
    private record Refusal(String kind,Integer httpStatus,Instant at,Instant retryNotBefore,UUID campaignId) { }
    private static Instant latest(Instant first,Instant second) {
        if (first==null) return second;
        return second==null || first.isAfter(second)?first:second;
    }
    private static Timestamp sql(Instant at) {return at==null?null:Timestamp.from(at);}
    private static boolean supportedTimestamp(Instant at) {
        try {
            long epochMillis=at.toEpochMilli();
            return epochMillis>=1 && epochMillis<=253_402_300_799_999L;
        } catch (ArithmeticException invalid) { return false; }
    }
    private static Instant at(ResultSet rs,String name) throws SQLException {
        Timestamp value=rs.getTimestamp(name);return value==null?null:value.toInstant();
    }
}
