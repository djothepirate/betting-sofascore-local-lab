package com.bettingproject.sofascorelocal.adapter.persistence.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.port.LiveCampaignStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Repository
public class JdbcLiveCampaignStore implements LiveCampaignStore {
    private final JdbcTemplate jdbc;
    private final RawManualCallSnapshotStore rawStore;
    public JdbcLiveCampaignStore(JdbcTemplate jdbc, RawManualCallSnapshotStore rawStore) {
        this.jdbc = jdbc; this.rawStore = rawStore;
    }

    @Override
    @Transactional
    public Manifest prepare(Manifest manifest) {
        Objects.requireNonNull(manifest);
        int inserted = jdbc.update("""
            insert into live_campaign(campaign_id,manifest_sha256,policy_version,prepared_at,expires_at,
                duration_seconds,maximum_calls_per_event,maximum_calls,maximum_bytes,qualified_match_capacity,target_count,
                request_envelope_nanos,processing_envelope_nanos,qualification_sha256,cycle_interval_seconds)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(campaign_id) do nothing
            """, manifest.campaignId(), manifest.manifestSha256(), manifest.policyVersion(), time(manifest.preparedAt()),
                time(manifest.expiresAt()), manifest.duration().toSeconds(), manifest.maximumCallsPerEvent(),
                manifest.maximumCalls(), manifest.maximumBytes(), manifest.qualifiedMatchCapacity(),manifest.targets().size(),
                manifest.admissionProfile().requestEnvelope().toNanos(), manifest.admissionProfile().processingEnvelope().toNanos(),
                manifest.admissionProfile().qualificationSha256(), manifest.cycleInterval().toSeconds());
        if (inserted == 1) {
            GroupedAdmissionProfile grouped = manifest.admissionProfile().groupedProfile();
            if (grouped != null) jdbc.update("""
                insert into live_grouped_policy(campaign_id,critical_interval_seconds,lineup_interval_seconds,
                    intra_group_delay_nanos,inter_group_delay_nanos,maximum_utilization_percent,qualification_sha256,endpoint_envelopes)
                values (?,60,300,0,3000000000,90,?,cast(? as jsonb))
                """, manifest.campaignId(), grouped.qualificationSha256(), groupedEnvelopesJson(grouped));
            for (int i = 0; i < manifest.targets().size(); i++) {
                Target t = manifest.targets().get(i);
                jdbc.update("""
                    insert into live_event(campaign_id,canonical_event_id,provider_event_id,target_order,
                        source_observation_id,source_snapshot_id) values (?,?,?,?,?,?)
                    """, manifest.campaignId(), t.canonicalEventId(), t.providerEventId(), i,
                        t.sourceObservationId(), t.sourceSnapshotId());
            }
        }
        Manifest stored = manifest(campaign(manifest.campaignId(), false), eventRows(manifest.campaignId()));
        if (!stored.equals(manifest)) throw new IllegalStateException("live manifest idempotency collision");
        return stored;
    }

    @Override
    @Transactional
    public void cancelPreparation(UUID campaignId, String expectedManifestSha256, Instant cancelledAt) {
        Objects.requireNonNull(campaignId); Objects.requireNonNull(expectedManifestSha256); Objects.requireNonNull(cancelledAt);
        List<Map<String,Object>> rows=jdbc.queryForList("select * from live_campaign where campaign_id=? for update",campaignId);
        if(rows.isEmpty()) throw new NoSuchElementException("LIVE_CAMPAIGN_NOT_FOUND");
        Map<String,Object> c=rows.getFirst();
        if(!expectedManifestSha256.equals(c.get("manifest_sha256"))) throw new IllegalArgumentException("LIVE_MANIFEST_MISMATCH");
        if(c.get("started_at")!=null) throw new IllegalStateException("LIVE_PREPARATION_ALREADY_LAUNCHED");
        if("STOPPED_OPERATOR".equals(c.get("state")) && "PREPARATION_CANCELLED".equals(c.get("reason"))) return;
        if(!"PREPARED".equals(c.get("state")) || c.get("ends_at")!=null || c.get("owner_instance_id")!=null || c.get("generation")!=null
                || number(c,"reserved_calls")!=0 || number(c,"received_bytes")!=0
                || Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from live_call where campaign_id=?)",Boolean.class,campaignId)))
            throw new IllegalStateException("LIVE_PREPARATION_NOT_CANCELABLE");
        List<Map<String,Object>> targets=eventRows(campaignId);
        if(targets.size()!=number(c,"target_count") || targets.stream().anyMatch(e -> !"PREPARED".equals(e.get("state"))
                || number(e,"reserved_calls")!=0 || number(e,"received_bytes")!=0 || e.get("next_due_at")!=null))
            throw new IllegalStateException("LIVE_PREPARATION_NOT_CANCELABLE");
        Instant at=cancelledAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        for(Map<String,Object> e:targets) {
            UUID eventId=uuid(e,"canonical_event_id");
            jdbc.update("update live_event set state='STOPPED_OPERATOR',reason='PREPARATION_CANCELLED',next_due_at=null where campaign_id=? and canonical_event_id=?",campaignId,eventId);
            append(campaignId,eventId,"STOPPED_OPERATOR","PREPARATION_CANCELLED",at,null);
        }
        jdbc.update("update live_campaign set state='STOPPED_OPERATOR',reason='PREPARATION_CANCELLED' where campaign_id=?",campaignId);
        append(campaignId,null,"STOPPED_OPERATOR","PREPARATION_CANCELLED",at,null);
    }

    @Override
    @Transactional
    public Launch launch(UUID campaignId, String expectedManifestSha256, Ownership ownership, Instant startedAt) {
        startedAt=startedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        requireOwnership(ownership, true);
        if (!campaignId.equals(ownership.campaignId())) throw new IllegalArgumentException("campaign and ownership differ");
        Map<String,Object> row = campaign(campaignId, true);
        if (!Objects.equals(row.get("manifest_sha256"), expectedManifestSha256)) throw new IllegalStateException("manifest hash changed");
        if (row.get("started_at") != null) {
            requireExecutionOwner(row, ownership);
            return new Launch(ownership, instant(row,"started_at"), instant(row,"ends_at"), false);
        }
        if (!"PREPARED".equals(row.get("state")) || startedAt.isBefore(instant(row,"prepared_at"))
                || !startedAt.isBefore(instant(row,"expires_at"))) throw new IllegalStateException("live preparation is expired or closed");
        List<Map<String,Object>> targets = eventRows(campaignId);
        if (targets.size() != number(row,"target_count") || targets.size() > number(row,"qualified_match_capacity")) throw new IllegalStateException("live selection is incomplete");
        Instant endsAt = startedAt.plusSeconds(number(row,"duration_seconds"));
        jdbc.update("""
            update live_campaign set state='RUNNING',started_at=?,ends_at=?,owner_instance_id=?,generation=? where campaign_id=?
            """, time(startedAt), time(endsAt), ownership.instanceId(), ownership.generation(), campaignId);
        jdbc.update("update live_event set state='INITIAL_CHECK',next_due_at=? where campaign_id=?", time(startedAt), campaignId);
        append(ownership.campaignId(),null,"RUNNING","OPERATOR_LAUNCH",startedAt,null);
        return new Launch(ownership,startedAt,endsAt,true);
    }

    @Override
    @Transactional
    public Optional<ReservedAttempt> reserveAttempt(AttemptRequest request) {
        Ownership own = request.ownership(); requireOwnership(own, true);
        Map<String,Object> c = campaign(own.campaignId(), true); requireExecutionOwner(c, own);
        List<Map<String,Object>> previous = jdbc.queryForList("select c.*,e.provider_event_id from live_call c join live_event e using(campaign_id,canonical_event_id) where attempt_id=?", request.attemptId());
        if (!previous.isEmpty()) {
            Map<String,Object> p = previous.getFirst();
            ReservedAttempt result = reserved(p);
            if (!own.campaignId().equals(uuid(p,"campaign_id")) || !result.canonicalEventId().equals(request.canonicalEventId())
                    || result.cycleNumber()!=request.cycleNumber() || result.endpoint()!=request.endpoint()
                    || !result.kind().equals(request.kind()) || !result.dueAt().equals(request.dueAt())
                    || result.finalCycle()!=request.finalCycle() || !Objects.equals(result.groupId(), request.groupId())
                    || result.groupSequence()!=request.groupSequence() || result.groupOrdinal()!=request.groupOrdinal())
                throw new IllegalStateException("live attempt idempotency collision");
            return Optional.of(result);
        }
        Map<String,Object> e = event(own.campaignId(),request.canonicalEventId(),true);
        if (!"RUNNING".equals(c.get("state")) || terminal((String)e.get("state"))
                || !request.reservedAt().isBefore(instant(c,"ends_at"))) return Optional.empty();
        long currentFinalCalls=number(jdbc.queryForMap("select count(*) as total from live_call where campaign_id=? and canonical_event_id=? and final_cycle=true",
                own.campaignId(),request.canonicalEventId()),"total");
        long currentFinalReserve=Math.max(0,4-currentFinalCalls-(request.finalCycle()?1:0));
        long globalFinalReserve=0;
        for(Map<String,Object> target:eventRows(own.campaignId())) if(!terminal((String)target.get("state"))) {
            UUID targetId=uuid(target,"canonical_event_id");
            long used=number(jdbc.queryForMap("select count(*) as total from live_call where campaign_id=? and canonical_event_id=? and final_cycle=true",own.campaignId(),targetId),"total");
            globalFinalReserve+=Math.max(0,4-used-(request.finalCycle() && targetId.equals(request.canonicalEventId())?1:0));
        }
        if (number(e,"reserved_calls") + 1 + currentFinalReserve > number(c,"maximum_calls_per_event")
                || number(c,"reserved_calls") + 1 + globalFinalReserve > number(c,"maximum_calls")
                || number(c,"received_bytes") + RawPayloadEvidence.MAXIMUM_BYTES > number(c,"maximum_bytes")) return Optional.empty();
        if (request.groupId() != null) {
            if (!"live-v4".equals(c.get("policy_version"))) throw new IllegalArgumentException("groups require live-v4");
            if (request.groupOrdinal() == 0) jdbc.update("""
                insert into live_call_group(group_id,campaign_id,canonical_event_id,group_sequence,owner_instance_id,generation,created_at)
                values (?,?,?,?,?,?,?)
                """, request.groupId(), own.campaignId(), request.canonicalEventId(), request.groupSequence(),
                    own.instanceId(), own.generation(), time(request.reservedAt()));
        } else if ("live-v4".equals(c.get("policy_version"))) throw new IllegalArgumentException("live-v4 requires a group");
        if(request.groupId()!=null) jdbc.update("""
            insert into live_call(attempt_id,campaign_id,canonical_event_id,cycle_number,endpoint,kind,due_at,reserved_at,
                final_cycle,owner_instance_id,generation,group_id,group_sequence,group_ordinal) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, request.attemptId(),own.campaignId(),request.canonicalEventId(),request.cycleNumber(),request.endpoint().name(),
                request.kind(),time(request.dueAt()),time(request.reservedAt()),request.finalCycle(),own.instanceId(),own.generation(),
                request.groupId(), request.groupId()==null?null:request.groupSequence(), request.groupId()==null?null:request.groupOrdinal());
        else jdbc.update("""
            insert into live_call(attempt_id,campaign_id,canonical_event_id,cycle_number,endpoint,kind,due_at,reserved_at,
                final_cycle,owner_instance_id,generation) values (?,?,?,?,?,?,?,?,?,?,?)
            """,request.attemptId(),own.campaignId(),request.canonicalEventId(),request.cycleNumber(),request.endpoint().name(),
                request.kind(),time(request.dueAt()),time(request.reservedAt()),request.finalCycle(),own.instanceId(),own.generation());
        jdbc.update("update live_campaign set reserved_calls=reserved_calls+1 where campaign_id=?",own.campaignId());
        jdbc.update("update live_event set reserved_calls=reserved_calls+1 where campaign_id=? and canonical_event_id=?",own.campaignId(),request.canonicalEventId());
        append(own.campaignId(),request.canonicalEventId(),"RESERVED",request.kind(),request.reservedAt(),request.attemptId());
        return Optional.of(new ReservedAttempt(request.attemptId(),request.canonicalEventId(),number(e,"provider_event_id"),
                request.endpoint(),request.cycleNumber(),request.kind(),request.dueAt(),request.reservedAt(),request.finalCycle(),
                request.groupId(),request.groupSequence(),request.groupOrdinal()));
    }

    @Override
    @Transactional
    public void recordDispatch(Ownership ownership, UUID attemptId, Instant authorizedAt) {
        requireOwnership(ownership,true);
        Map<String,Object> c=campaign(ownership.campaignId(),true); requireExecutionOwner(c,ownership);
        Map<String,Object> a=attempt(ownership,attemptId);
        Map<String,Object> e=event(ownership.campaignId(),uuid(a,"canonical_event_id"),true);
        if (!"RUNNING".equals(c.get("state")) || terminal((String)e.get("state"))
                || !authorizedAt.isBefore(instant(c,"ends_at"))) throw new IllegalStateException("live dispatch no longer authorized");
        int n=jdbc.update("insert into live_call_dispatch(attempt_id,authorized_at) values (?,?) on conflict(attempt_id) do nothing",attemptId,time(authorizedAt));
        if(n==0) throw new IllegalStateException("live dispatch authorization already consumed");
        append(ownership.campaignId(),uuid(a,"canonical_event_id"),"DISPATCH_AUTHORIZED",null,authorizedAt,attemptId);
    }

    @Override
    @Transactional
    public RawSnapshotPersistenceResult saveReceipt(Ownership ownership, UUID attemptId, RawManualCallSnapshot raw) {
        requireOwnership(ownership,false); campaign(ownership.campaignId(),true);
        Map<String,Object> a=attempt(ownership,attemptId);
        Map<String,Object> e=event(ownership.campaignId(),uuid(a,"canonical_event_id"),false);
        if (!raw.endpointType().name().equals(a.get("endpoint"))
                || !raw.requestKey().equals(raw.endpointType().name()+"|eventId="+number(e,"provider_event_id"))
                || raw.acquisitionMode()!=RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT)
            throw new IllegalArgumentException("live response identity does not match reserved request");
        List<Map<String,Object>> old=jdbc.queryForList("select * from live_call_receipt where attempt_id=?",attemptId);
        if(!old.isEmpty()) {
            Map<String,Object> r=old.getFirst();
            if(!Objects.equals(r.get("payload_sha256"),raw.payload().sha256())
                    || !sameTime(instant(r,"requested_at"),raw.requestedAt()) || !sameTime(instant(r,"received_at"),raw.receivedAt()))
                throw new IllegalStateException("live receipt idempotency collision");
            return rawResult(r);
        }
        RawSnapshotPersistenceResult persisted=rawStore.save(raw);
        Boolean rawAvailable=jdbc.queryForObject("select payload_raw is not null from provider_snapshot where id=?",Boolean.class,persisted.snapshotId());
        if(!Boolean.TRUE.equals(rawAvailable)) throw new IllegalStateException("LIVE_RAW_PREVIOUSLY_PURGED");
        jdbc.update("""
            insert into live_call_receipt(attempt_id,snapshot_id,occurrence_id,requested_at,received_at,
                payload_sha256,payload_size_bytes,persistence_outcome) values (?,?,?,?,?,?,?,?)
            """,attemptId,persisted.snapshotId(),persisted.occurrenceId().orElseThrow(),time(raw.requestedAt()),time(raw.receivedAt()),
                persisted.payloadSha256(),persisted.payloadSizeBytes(),persisted.outcome().name());
        jdbc.update("update live_campaign set received_bytes=received_bytes+? where campaign_id=?",persisted.payloadSizeBytes(),ownership.campaignId());
        jdbc.update("update live_event set received_bytes=received_bytes+? where campaign_id=? and canonical_event_id=?",persisted.payloadSizeBytes(),ownership.campaignId(),uuid(a,"canonical_event_id"));
        append(ownership.campaignId(),uuid(a,"canonical_event_id"),"RECEIVED",persisted.outcome().name(),raw.receivedAt(),attemptId);
        return persisted;
    }

    @Override
    @Transactional
    public Result publishResult(Ownership ownership, UUID attemptId, Publication publication,
                                Supplier<NormalizedReferences> persistNormalized) {
        Objects.requireNonNull(publication); Objects.requireNonNull(persistNormalized);
        requireOwnership(ownership,false); campaign(ownership.campaignId(),true);
        Map<String,Object> a=attempt(ownership,attemptId);
        List<Map<String,Object>> previous=jdbc.queryForList("select * from live_call_result where attempt_id=?",attemptId);
        if(!previous.isEmpty()) {
            Result result=result(previous.getFirst());
            if(!equivalent(result.publication(),publication)) throw new IllegalStateException("live result idempotency collision");
            return result;
        }
        NormalizedReferences refs=Objects.requireNonNull(persistNormalized.get());
        jdbc.update("""
            insert into live_call_result(attempt_id,outcome,scope,code,resolved_at,parser_version,successful,next_event_state,
                sport_status,projection_json,projection_version,projection_sha256,completeness_status,completeness_score,
                canonical_observation_id,detail_observation_id,j5_observation_id,normalized_sha256)
            values (?,?,?,?,?,?,?,?,?,cast(? as jsonb),?,encode(sha256(convert_to(cast(? as jsonb)::text,'UTF8')),'hex'),?,?,?,?,?,?)
            """,attemptId,publication.outcome(),publication.scope(),publication.code(),time(publication.resolvedAt()),publication.parserVersion(),
                publication.successful(),publication.nextEventState(),publication.sportStatus(),publication.projectionJson(),publication.projectionVersion(),
                publication.projectionJson(),publication.completenessStatus(),publication.completenessScore(),refs.canonicalObservationId(),refs.detailObservationId(),
                refs.j5ObservationId(),refs.normalizedSha256());
        UUID eventId=uuid(a,"canonical_event_id");
        Map<String,Object> e=event(ownership.campaignId(),eventId,true);
        if(publication.nextEventState()!=null && !terminal((String)e.get("state")))
            jdbc.update("update live_event set state=?,reason=?,next_due_at=case when ? then null else next_due_at end where campaign_id=? and canonical_event_id=?",
                    publication.nextEventState(),publication.code(),terminal(publication.nextEventState()),ownership.campaignId(),eventId);
        append(ownership.campaignId(),eventId,"RESULT",publication.code()==null?publication.outcome():publication.code(),publication.resolvedAt(),attemptId);
        return new Result(attemptId,publication,refs);
    }

    @Override
    @Transactional
    public void transition(Ownership ownership, UUID canonicalEventId, String state, String reason, Instant at, UUID attemptId) {
        LiveCampaignData.requireCode(state); if(reason!=null) LiveCampaignData.requireCode(reason);
        requireOwnership(ownership,false); Map<String,Object> c=campaign(ownership.campaignId(),true); requireExecutionOwner(c,ownership);
        if(attemptId!=null) {
            Map<String,Object> a=attempt(ownership,attemptId);
            if(canonicalEventId!=null && !canonicalEventId.equals(uuid(a,"canonical_event_id"))) throw new IllegalArgumentException("transition event mismatch");
        }
        if(canonicalEventId==null) {
            if(terminal((String)c.get("state")) && !Objects.equals(c.get("state"),state)) throw new IllegalStateException("terminal campaign cannot change state");
            jdbc.update("update live_campaign set state=?,reason=? where campaign_id=?",state,reason,ownership.campaignId());
        } else {
            Map<String,Object> e=event(ownership.campaignId(),canonicalEventId,true);
            if(terminal((String)e.get("state")) && !Objects.equals(e.get("state"),state)) return;
            jdbc.update("update live_event set state=?,reason=?,next_due_at=case when ? then null else next_due_at end where campaign_id=? and canonical_event_id=?",
                    state,reason,terminal(state),ownership.campaignId(),canonicalEventId);
        }
        append(ownership.campaignId(),canonicalEventId,state,reason,at,attemptId);
    }

    @Override
    @Transactional
    public void updateNextDueAt(Ownership ownership, UUID canonicalEventId, Instant nextDueAt, Instant at) {
        requireOwnership(ownership,true); Map<String,Object> c=campaign(ownership.campaignId(),true); requireExecutionOwner(c,ownership);
        Map<String,Object> e=event(ownership.campaignId(),canonicalEventId,true);
        if(terminal((String)e.get("state")) || !"RUNNING".equals(c.get("state"))) return;
        jdbc.update("update live_event set next_due_at=? where campaign_id=? and canonical_event_id=?",time(nextDueAt),ownership.campaignId(),canonicalEventId);
        append(ownership.campaignId(),canonicalEventId,"SCHEDULED",null,at,null);
    }

    @Override
    @Transactional
    public void updateScheduleMetrics(Ownership ownership,UUID canonicalEventId,Instant nextDueAt,
                                      long missedCycles,boolean finalComplete,Instant at) {
        if(missedCycles<0) throw new IllegalArgumentException("negative missed cycle count");
        requireOwnership(ownership,false); Map<String,Object> c=campaign(ownership.campaignId(),true); requireExecutionOwner(c,ownership);
        Map<String,Object> e=event(ownership.campaignId(),canonicalEventId,true);
        if(missedCycles<number(e,"missed_cycles")) throw new IllegalArgumentException("missed cycles cannot decrease");
        if(terminal((String)e.get("state"))) nextDueAt=null;
        if(nextDueAt!=null) nextDueAt=nextDueAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(Objects.equals(instant(e,"next_due_at"),nextDueAt) && missedCycles==number(e,"missed_cycles")
                && finalComplete==(Boolean)e.get("final_complete")) return;
        jdbc.update("update live_event set next_due_at=?,missed_cycles=?,final_complete=? where campaign_id=? and canonical_event_id=?",
                time(nextDueAt),missedCycles,finalComplete,ownership.campaignId(),canonicalEventId);
        append(ownership.campaignId(),canonicalEventId,"SCHEDULED",null,at,null);
    }

    @Override
    @Transactional
    public void updateFamilySchedule(Ownership ownership, UUID canonicalEventId, FamilySchedule schedule, Instant at) {
        Objects.requireNonNull(schedule); Objects.requireNonNull(at);
        requireOwnership(ownership,false);
        Map<String,Object> c=campaign(ownership.campaignId(),true); requireExecutionOwner(c,ownership);
        if (!"live-v4".equals(c.get("policy_version"))) throw new IllegalArgumentException("family schedules require live-v4");
        Map<String,Object> e=event(ownership.campaignId(),canonicalEventId,true);
        Instant nextDue=terminal((String)e.get("state")) || terminal((String)c.get("state")) ? null : schedule.nextDueAt();
        List<Map<String,Object>> previous=jdbc.queryForList("select * from live_family_schedule where campaign_id=? and canonical_event_id=? and endpoint=?",
                ownership.campaignId(),canonicalEventId,schedule.endpoint().name());
        if(!previous.isEmpty()) {
            Map<String,Object> p=previous.getFirst();
            if(schedule.missedCycles()<number(p,"missed_cycles") || at.isBefore(instant(p,"changed_at")))
                throw new IllegalArgumentException("family schedule metrics cannot move backwards");
            if(Objects.equals(nextDue,instant(p,"next_due_at")) && schedule.intervalSeconds()==number(p,"interval_seconds")
                    && schedule.missedCycles()==number(p,"missed_cycles")) return;
        }
        jdbc.update("""
            insert into live_family_schedule(campaign_id,canonical_event_id,endpoint,next_due_at,interval_seconds,missed_cycles,changed_at,owner_instance_id,generation)
            values (?,?,?,?,?,?,?,?,?) on conflict(campaign_id,canonical_event_id,endpoint) do update
            set next_due_at=excluded.next_due_at,interval_seconds=excluded.interval_seconds,missed_cycles=excluded.missed_cycles,
                changed_at=excluded.changed_at,owner_instance_id=excluded.owner_instance_id,generation=excluded.generation
            """,ownership.campaignId(),canonicalEventId,schedule.endpoint().name(),time(nextDue),schedule.intervalSeconds(),
                schedule.missedCycles(),time(at),ownership.instanceId(),ownership.generation());
        append(ownership.campaignId(),canonicalEventId,"FAMILY_SCHEDULED",schedule.endpoint().name(),at,null);
        jdbc.update("""
            insert into live_family_schedule_revision(campaign_id,revision,canonical_event_id,endpoint,next_due_at,interval_seconds,missed_cycles)
            select campaign_id,revision,?,?,?,?,? from live_campaign where campaign_id=?
            """,canonicalEventId,schedule.endpoint().name(),time(nextDue),schedule.intervalSeconds(),schedule.missedCycles(),ownership.campaignId());
    }

    @Override
    @Transactional
    public void interruptOrphan(Ownership ownership, Instant at, String reason) {
        LiveCampaignData.requireCode(reason); requireOwnership(ownership,false);
        Map<String,Object> c=campaign(ownership.campaignId(),true); requireExecutionOwner(c,ownership);
        if(terminal((String)c.get("state"))) return;
        for(Map<String,Object> e:eventRows(ownership.campaignId())) if(!terminal((String)e.get("state")))
            jdbc.update("update live_event set state='INTERRUPTED',reason=?,next_due_at=null where campaign_id=? and canonical_event_id=?",reason,ownership.campaignId(),uuid(e,"canonical_event_id"));
        for(Map<String,Object> a:jdbc.queryForList("select c.* from live_call c left join live_call_result r using(attempt_id) where c.campaign_id=? and r.attempt_id is null",ownership.campaignId()))
            jdbc.update("insert into live_call_result(attempt_id,outcome,scope,code,resolved_at,successful) values (?,'UNKNOWN','CAMPAIGN',?,?,false)",uuid(a,"attempt_id"),reason,time(at));
        jdbc.update("update live_campaign set state='INTERRUPTED',reason=? where campaign_id=?",reason,ownership.campaignId());
        jdbc.update("update provider_campaign_guard set state='CLEANUP_REQUIRED',changed_at=? where singleton_id=1",time(at));
        append(ownership.campaignId(),null,"INTERRUPTED",reason,at,null);
        if ("live-v4".equals(c.get("policy_version"))) {
            // The former scheduler cannot publish terminal deadlines. Cancel only existing
            // pending projections and preserve each prior decision in the append-only ledger.
            for (Map<String,Object> family : jdbc.queryForList("""
                    select * from live_family_schedule where campaign_id=? and next_due_at is not null
                    order by canonical_event_id,endpoint
                    """,ownership.campaignId())) {
                SofascoreEndpointType endpoint=SofascoreEndpointType.valueOf((String)family.get("endpoint"));
                updateFamilySchedule(ownership,uuid(family,"canonical_event_id"),
                        new FamilySchedule(endpoint,null,number(family,"interval_seconds"),number(family,"missed_cycles")),at);
            }
        }
    }

    @Override
    @Transactional
    public void completeOrphanCleanup(Guard expectedGuard, Instant verifiedAt) {
        Objects.requireNonNull(expectedGuard); Objects.requireNonNull(verifiedAt);
        if (!"CLEANUP_REQUIRED".equals(expectedGuard.state()) || expectedGuard.campaignId()==null
                || expectedGuard.owner()==null || expectedGuard.generation()<1 || expectedGuard.changedAt()==null
                || verifiedAt.isBefore(expectedGuard.changedAt()))
            throw new IllegalArgumentException("LIVE_ORPHAN_CLEANUP_INVALID_GUARD");
        // The singleton is always locked before the campaign, as in acquisition and reservation.
        Map<String,Object> g=jdbc.queryForMap("select * from provider_campaign_guard where singleton_id=1 for update");
        String evidence=orphanCleanupEvidence(expectedGuard);
        boolean sameGeneration=expectedGuard.generation()==number(g,"generation");
        boolean released="FREE".equals(g.get("state")) && sameGeneration
                && g.get("campaign_id")==null && g.get("owner_instance_id")==null
                && g.get("owner_process_id")==null && g.get("owner_process_started_at")==null;
        if (!released && (!expectedGuard.state().equals(g.get("state")) || !sameGeneration
                || !expectedGuard.campaignId().equals(uuid(g,"campaign_id"))
                || !expectedGuard.owner().instanceId().equals(uuid(g,"owner_instance_id"))
                || expectedGuard.owner().processId()!=number(g,"owner_process_id")
                || !expectedGuard.owner().processStartedAt().equals(instant(g,"owner_process_started_at"))
                || !expectedGuard.changedAt().equals(instant(g,"changed_at"))))
            throw new IllegalStateException("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        Map<String,Object> c=campaign(expectedGuard.campaignId(),true);
        if (!expectedGuard.owner().instanceId().equals(uuid(c,"owner_instance_id"))
                || expectedGuard.generation()!=number(c,"generation"))
            throw new IllegalStateException("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
        UUID campaignId=expectedGuard.campaignId();
        if (released) {
            boolean recorded=Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from live_transition where campaign_id=? and canonical_event_id is null
                    and state='LOCAL_CLEANUP_VERIFIED' and reason=? and attempt_id is null)
                """,Boolean.class,campaignId,evidence));
            if (!recorded || !terminal((String)c.get("state")))
                throw new IllegalStateException("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
            return; // A committed release whose response was lost; never release a newer generation.
        }
        List<Map<String,Object>> targets=eventRows(campaignId);
        if (!terminal((String)c.get("state")) || c.get("started_at")==null
                || targets.size()!=number(c,"target_count")
                || targets.stream().anyMatch(e->!terminal((String)e.get("state")) || e.get("next_due_at")!=null)
                || Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from live_call c left join live_call_result r using(attempt_id)
                        where c.campaign_id=? and r.attempt_id is null)
                    """,Boolean.class,campaignId))
                || Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from live_campaign where campaign_id<>? and state in ('RUNNING','CLEANUP_REQUIRED'))
                    """,Boolean.class,campaignId))
                || ("live-v4".equals(c.get("policy_version")) && Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from live_family_schedule where campaign_id=? and next_due_at is not null)
                    """,Boolean.class,campaignId))))
            throw new IllegalStateException("LIVE_ORPHAN_CLEANUP_INCOMPLETE");
        Instant at=verifiedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        // The campaign's interruption, counters, receipts and prior transitions remain historical evidence.
        append(campaignId,null,"LOCAL_CLEANUP_VERIFIED",evidence,at,null);
        int releasedRows=jdbc.update("""
            update provider_campaign_guard set state='FREE',campaign_id=null,owner_instance_id=null,
                owner_process_id=null,owner_process_started_at=null,changed_at=? where singleton_id=1
            """,time(at));
        if (releasedRows!=1) throw new IllegalStateException("LIVE_ORPHAN_CLEANUP_GUARD_CHANGED");
    }

    private static String orphanCleanupEvidence(Guard expected) {
        String identity=String.join("\n","orphan-cleanup-v1",expected.state(),expected.campaignId().toString(),
                expected.owner().instanceId().toString(),Long.toString(expected.owner().processId()),
                expected.owner().processStartedAt().toString(),Long.toString(expected.generation()),expected.changedAt().toString());
        try {
            return "GUARD_"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }

    @Override
    @Transactional(readOnly=true)
    public DispatchBudget dispatchBudget(Ownership ownership, UUID canonicalEventId) {
        Objects.requireNonNull(ownership); Objects.requireNonNull(canonicalEventId);
        List<Map<String,Object>> rows = jdbc.queryForList("""
            select c.reserved_calls,c.received_bytes,e.reserved_calls as event_reserved_calls,e.state as event_state
            from live_campaign c join live_event e using(campaign_id)
            join provider_campaign_guard g on g.singleton_id=1 and g.campaign_id=c.campaign_id
            where c.campaign_id=? and e.canonical_event_id=? and g.state='OWNED'
                and g.owner_instance_id=? and g.generation=?
                and c.owner_instance_id=g.owner_instance_id and c.generation=g.generation
            """, ownership.campaignId(),canonicalEventId,ownership.instanceId(),ownership.generation());
        if (rows.isEmpty()) throw new IllegalStateException("live provider ownership is stale or closed");
        Map<String,Object> row=rows.getFirst();
        return new DispatchBudget((int)number(row,"reserved_calls"),number(row,"received_bytes"),
                (int)number(row,"event_reserved_calls"),(String)row.get("event_state"));
    }

    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Optional<CampaignView> find(UUID campaignId) {
        List<Map<String,Object>> rows=jdbc.queryForList("select * from live_campaign where campaign_id=?",campaignId);
        return rows.isEmpty()?Optional.empty():Optional.of(view(rows.getFirst()));
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<CampaignView> findRecent(int limit) {
        if(limit<1 || limit>100) throw new IllegalArgumentException("live query limit outside 1..100");
        return jdbc.queryForList("select * from live_campaign order by prepared_at desc,campaign_id limit ?",limit).stream().map(this::view).toList();
    }
    @Override
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Optional<CampaignView> latestForEvent(UUID canonicalEventId) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
            select c.* from live_campaign c join live_event e using(campaign_id) where e.canonical_event_id=?
            order by case when c.state in ('RUNNING','CLEANUP_REQUIRED') then 0
                          when c.started_at is not null then 1 else 2 end,
                     c.started_at desc nulls last,c.prepared_at desc,c.campaign_id limit 1
            """,canonicalEventId);
        return rows.isEmpty()?Optional.empty():Optional.of(view(rows.getFirst()));
    }

    private CampaignView view(Map<String,Object> c) {
        UUID campaignId=uuid(c,"campaign_id"); List<Map<String,Object>> events=eventRows(campaignId);
        List<Map<String,Object>> calls=jdbc.queryForList("""
            select c.*,e.provider_event_id,d.authorized_at,q.snapshot_id,q.occurrence_id,q.received_at
            from live_call c join live_event e using(campaign_id,canonical_event_id)
            left join live_call_dispatch d using(attempt_id) left join live_call_receipt q using(attempt_id)
            where c.campaign_id=? order by c.ordinal
            """,campaignId);
        Map<UUID,Result> results=new HashMap<>();
        for(Map<String,Object> r:jdbc.queryForList("select r.* from live_call_result r join live_call c using(attempt_id) where c.campaign_id=?",campaignId))
            results.put(uuid(r,"attempt_id"),result(r));
        List<AttemptView> attempts=calls.stream().map(a->new AttemptView(reserved(a),instant(a,"authorized_at"),boxed(a,"snapshot_id"),
                boxed(a,"occurrence_id"),instant(a,"received_at"),results.get(uuid(a,"attempt_id")))).toList();
        List<EventView> eventViews=events.stream().map(e->new EventView(target(e),(String)e.get("state"),(String)e.get("reason"),
                (int)number(e,"reserved_calls"),number(e,"received_bytes"),instant(e,"next_due_at"),number(e,"missed_cycles"),
                (Boolean)e.get("final_complete"),cursors(campaignId,uuid(e,"canonical_event_id"),attempts,"live-v4".equals(c.get("policy_version"))))).toList();
        List<Transition> transitions=jdbc.queryForList("select * from live_transition where campaign_id=? order by revision",campaignId).stream()
                .map(t->new Transition(number(t,"revision"),uuid(t,"canonical_event_id"),(String)t.get("state"),(String)t.get("reason"),instant(t,"changed_at"),uuid(t,"attempt_id"))).toList();
        Ownership own=c.get("owner_instance_id")==null?null:new Ownership(campaignId,uuid(c,"owner_instance_id"),number(c,"generation"));
        return new CampaignView(manifest(c,events),(String)c.get("state"),(String)c.get("reason"),instant(c,"started_at"),instant(c,"ends_at"),
                (int)number(c,"reserved_calls"),number(c,"received_bytes"),number(c,"revision"),own,eventViews,attempts,transitions);
    }
    private List<FamilyCursor> cursors(UUID campaignId,UUID eventId,List<AttemptView> attempts,boolean grouped) {
        List<FamilyCursor> cursors=new ArrayList<>();
        Map<SofascoreEndpointType,FamilySchedule> schedules=new EnumMap<>(SofascoreEndpointType.class);
        if(grouped) for(Map<String,Object> row:jdbc.queryForList("select * from live_family_schedule where campaign_id=? and canonical_event_id=?",campaignId,eventId)) {
            SofascoreEndpointType endpoint=SofascoreEndpointType.valueOf((String)row.get("endpoint"));
            schedules.put(endpoint,new FamilySchedule(endpoint,instant(row,"next_due_at"),number(row,"interval_seconds"),number(row,"missed_cycles")));
        }
        for(SofascoreEndpointType endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_INCIDENTS,SofascoreEndpointType.EVENT_LINEUPS)) {
            AttemptView last=null,received=null,success=null,changed=null; Result latest=null; String hash=null;
            for(AttemptView a:attempts) if(a.attempt().canonicalEventId().equals(eventId) && a.attempt().endpoint()==endpoint) {
                last=a; if(a.receivedAt()!=null) received=a;
                if(a.result()!=null) latest=a.result();
                if(a.result()!=null && a.result().publication().successful()) {
                    success=a; String next=a.result().normalized().normalizedSha256()+"|"+a.result().publication().projectionVersion()+"|"+a.result().publication().projectionJson();
                    if(!Objects.equals(hash,next)){changed=a;hash=next;}
                }
            }
            if(last!=null || schedules.containsKey(endpoint)) cursors.add(new FamilyCursor(endpoint,id(last),id(received),id(success),id(changed),
                    received==null?null:received.receivedAt(),success==null?null:success.receivedAt(),changed==null?null:changed.receivedAt(),
                    success==null?NormalizedReferences.none():success.result().normalized(),latest,success==null?null:success.result(),schedules.get(endpoint)));
        }
        return List.copyOf(cursors);
    }
    private void requireOwnership(Ownership own,boolean active) {
        Objects.requireNonNull(own);
        Map<String,Object> g=jdbc.queryForMap("select * from provider_campaign_guard where singleton_id=1 for update");
        if("FREE".equals(g.get("state")) || (active && !"OWNED".equals(g.get("state")))
                || !own.campaignId().equals(uuid(g,"campaign_id")) || !own.instanceId().equals(uuid(g,"owner_instance_id"))
                || own.generation()!=number(g,"generation")) throw new IllegalStateException("live provider ownership is stale or closed");
    }
    private static void requireExecutionOwner(Map<String,Object> c,Ownership own) {
        if(!own.instanceId().equals(uuid(c,"owner_instance_id")) || own.generation()!=number(c,"generation"))
            throw new IllegalStateException("live execution belongs to another owner");
    }
    private Map<String,Object> campaign(UUID id,boolean lock) { return jdbc.queryForMap("select * from live_campaign where campaign_id=?"+(lock?" for update":""),id); }
    private Map<String,Object> event(UUID campaignId,UUID eventId,boolean lock) { return jdbc.queryForMap("select * from live_event where campaign_id=? and canonical_event_id=?"+(lock?" for update":""),campaignId,eventId); }
    private List<Map<String,Object>> eventRows(UUID id) { return jdbc.queryForList("select * from live_event where campaign_id=? order by target_order",id); }
    private Map<String,Object> attempt(Ownership own,UUID id) {
        Map<String,Object> a=jdbc.queryForMap("select * from live_call where attempt_id=?",id);
        if(!own.campaignId().equals(uuid(a,"campaign_id")) || !own.instanceId().equals(uuid(a,"owner_instance_id")) || own.generation()!=number(a,"generation"))
            throw new IllegalStateException("attempt belongs to another live execution");
        return a;
    }
    private void append(UUID campaignId,UUID eventId,String state,String reason,Instant at,UUID attemptId) {
        Long revision=jdbc.queryForObject("update live_campaign set revision=revision+1 where campaign_id=? returning revision",Long.class,campaignId);
        jdbc.update("insert into live_transition(campaign_id,revision,canonical_event_id,state,reason,changed_at,attempt_id) values (?,?,?,?,?,?,?)",
                campaignId,revision,eventId,state,reason,time(at),attemptId);
    }
    private Manifest manifest(Map<String,Object> c,List<Map<String,Object>> events) {
        return new Manifest(uuid(c,"campaign_id"),(String)c.get("manifest_sha256"),(String)c.get("policy_version"),instant(c,"prepared_at"),instant(c,"expires_at"),
                Duration.ofSeconds(number(c,"duration_seconds")),(int)number(c,"maximum_calls_per_event"),(int)number(c,"maximum_calls"),
                number(c,"maximum_bytes"),(int)number(c,"qualified_match_capacity"),events.stream().map(JdbcLiveCampaignStore::target).toList(),
                new AdmissionProfile(Duration.ofNanos(number(c,"request_envelope_nanos")),Duration.ofNanos(number(c,"processing_envelope_nanos")),
                        (String)c.get("qualification_sha256"),groupedProfile(c)), Duration.ofSeconds(number(c,"cycle_interval_seconds")));
    }
    private GroupedAdmissionProfile groupedProfile(Map<String,Object> campaign) {
        if(!"live-v4".equals(campaign.get("policy_version"))) return null;
        Map<String,Object> row=jdbc.queryForMap("select * from live_grouped_policy where campaign_id=?",uuid(campaign,"campaign_id"));
        var tree=tools.jackson.databind.json.JsonMapper.builder().build().readTree(row.get("endpoint_envelopes").toString());
        Map<SofascoreEndpointType,EndpointEnvelope> envelopes=new EnumMap<>(SofascoreEndpointType.class);
        for(SofascoreEndpointType endpoint:List.of(SofascoreEndpointType.EVENT_DETAILS,SofascoreEndpointType.EVENT_INCIDENTS,
                SofascoreEndpointType.EVENT_STATISTICS,SofascoreEndpointType.EVENT_LINEUPS)) {
            var value=tree.get(endpoint.name());
            envelopes.put(endpoint,new EndpointEnvelope(Duration.ofNanos(value.get("requestNanos").asLong()),Duration.ofNanos(value.get("processingNanos").asLong())));
        }
        return new GroupedAdmissionProfile(envelopes,(String)row.get("qualification_sha256"));
    }
    private static String groupedEnvelopesJson(GroupedAdmissionProfile profile) {
        Map<String,Object> envelopes=new TreeMap<>();
        profile.endpointEnvelopes().forEach((endpoint,envelope)->envelopes.put(endpoint.name(),
                Map.of("requestNanos",envelope.requestEnvelope().toNanos(),"processingNanos",envelope.processingEnvelope().toNanos())));
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(envelopes);
    }
    private static Target target(Map<String,Object> e) { return new Target(uuid(e,"canonical_event_id"),number(e,"provider_event_id"),number(e,"source_observation_id"),number(e,"source_snapshot_id")); }
    private static ReservedAttempt reserved(Map<String,Object> a) { return new ReservedAttempt(uuid(a,"attempt_id"),uuid(a,"canonical_event_id"),number(a,"provider_event_id"),
            SofascoreEndpointType.valueOf((String)a.get("endpoint")),number(a,"cycle_number"),(String)a.get("kind"),instant(a,"due_at"),instant(a,"reserved_at"),(Boolean)a.get("final_cycle"),
            uuid(a,"group_id"),a.get("group_id")==null?-1:number(a,"group_sequence"),a.get("group_id")==null?-1:(int)number(a,"group_ordinal")); }
    private static Result result(Map<String,Object> r) {
        Publication p=new Publication((String)r.get("outcome"),(String)r.get("scope"),(String)r.get("code"),instant(r,"resolved_at"),(String)r.get("parser_version"),
                (Boolean)r.get("successful"),(String)r.get("next_event_state"),(String)r.get("sport_status"),r.get("projection_json")==null?null:r.get("projection_json").toString(),
                (String)r.get("projection_version"),(String)r.get("completeness_status"),r.get("completeness_score")==null?null:((Number)r.get("completeness_score")).intValue());
        return new Result(uuid(r,"attempt_id"),p,new NormalizedReferences(boxed(r,"canonical_observation_id"),boxed(r,"detail_observation_id"),boxed(r,"j5_observation_id"),(String)r.get("normalized_sha256")));
    }
    private static boolean equivalent(Publication left,Publication right) {
        // PostgreSQL jsonb canonicalizes whitespace/order; compare all scalar evidence separately.
        if(left.equals(right))return true;
        return new Publication(left.outcome(),left.scope(),left.code(),left.resolvedAt(),left.parserVersion(),left.successful(),left.nextEventState(),left.sportStatus(),null,null,left.completenessStatus(),left.completenessScore())
                .equals(new Publication(right.outcome(),right.scope(),right.code(),right.resolvedAt(),right.parserVersion(),right.successful(),right.nextEventState(),right.sportStatus(),null,null,right.completenessStatus(),right.completenessScore()))
                && Objects.equals(left.projectionVersion(),right.projectionVersion()) && Objects.equals(json(left.projectionJson()),json(right.projectionJson()));
    }
    private static Object json(String value) { return value==null?null:tools.jackson.databind.json.JsonMapper.builder().build().readTree(value); }
    private static RawSnapshotPersistenceResult rawResult(Map<String,Object> r) { return new RawSnapshotPersistenceResult(number(r,"snapshot_id"),RawSnapshotPersistenceOutcome.valueOf((String)r.get("persistence_outcome")),
            (String)r.get("payload_sha256"),(int)number(r,"payload_size_bytes"),OptionalLong.of(number(r,"occurrence_id"))); }
    private static UUID id(AttemptView a){return a==null?null:a.attempt().attemptId();}
    private static boolean sameTime(Instant a,Instant b){return Duration.between(a,b).abs().compareTo(Duration.ofNanos(1000))<0;}
    private static Timestamp time(Instant at){return at==null?null:Timestamp.from(at);}
    private static Instant instant(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:((Timestamp)v).toInstant();}
    private static UUID uuid(Map<String,Object> row,String key){return (UUID)row.get(key);}
    private static long number(Map<String,Object> row,String key){Object n=row.get(key);return n==null?0:((Number)n).longValue();}
    private static Long boxed(Map<String,Object> row,String key){return row.get(key)==null?null:number(row,key);}
    private static boolean terminal(String state){return state!=null && (state.startsWith("STOPPED_") || state.equals("FINISHED_CONFIRMED") || state.equals("INTERRUPTED") || state.equals("COMPLETED") || state.equals("FAILED"));}
}
