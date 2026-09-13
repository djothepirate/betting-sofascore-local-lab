package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Collection;
import com.bettingproject.sofascorelocal.port.J3CollectionStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcJ3CollectionStore implements J3CollectionStore {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcJ3CollectionStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void begin(UUID id, LocalDate date, Trigger trigger, Instant startedAt) {
        var p = params(id).addValue("date", date).addValue("trigger", trigger.name()).addValue("start", ts(startedAt));
        jdbc.update("""
                insert into j3_collection_run(run_id,collection_date,trigger_kind,started_at,state)
                values (:id,:date,:trigger,:start,'RUNNING') on conflict (run_id) do nothing
                """, p);
        Integer matches = jdbc.queryForObject("""
                select count(*) from j3_collection_run where run_id=:id and collection_date=:date
                and trigger_kind=:trigger and started_at=:start
                """, p, Integer.class);
        if (matches == null || matches != 1) throw new IllegalStateException("J3_RUN_IDENTITY_CONFLICT");
    }

    @Override @Transactional
    public void appendPage(UUID id, J3MinimizedPageEvidence page) {
        var p = params(id).addValue("page", page.page()).addValue("snapshot", page.snapshotId())
                .addValue("json", JSON.writeValueAsString(page));
        // The run lock serializes append against terminal publication and duplicate completion.
        jdbc.queryForObject("select run_id from j3_collection_run where run_id=:id for update", p, UUID.class);
        List<Boolean> existing = jdbc.query("""
                select evidence=cast(:json as jsonb) from j3_collection_page where run_id=:id and page=:page
                """, p, (rs, n) -> rs.getBoolean(1));
        if (!existing.isEmpty()) {
            if (!existing.getFirst()) throw new IllegalStateException("J3_PAGE_IDENTITY_CONFLICT");
            return;
        }
        jdbc.update("""
                insert into j3_collection_page(run_id,page,snapshot_id,evidence)
                values (:id,:page,:snapshot,cast(:json as jsonb))
                """, p);
    }

    @Override @Transactional
    public void publish(Proof proof, List<Entry> entries) {
        // Shared with admission: a manual success committed before a daily claim prevents its dispatch.
        jdbc.queryForObject("select pg_advisory_xact_lock(-6060)",Map.of(),Object.class);
        var p = params(proof.runId()).addValue("state", proof.state().name())
                .addValue("finish", ts(proof.finishedAt())).addValue("code", proof.terminalCode())
                .addValue("date", proof.date());
        String state = jdbc.queryForObject("select state from j3_collection_run where run_id=:id for update", p, String.class);
        if (!"RUNNING".equals(state)) {
            var existing = find(proof.runId()).orElseThrow();
            // JSONB compares timestamps/metadata independent of Java map iteration order.
            Proof normalized = new Proof(proof.runId(),proof.date(),proof.trigger(),micros(proof.startedAt()),
                    micros(proof.finishedAt()),proof.state(),proof.terminalCode(),proof.pages());
            if (!existing.proof().equals(normalized) || !new java.util.HashSet<>(existing.entries()).equals(new java.util.HashSet<>(entries)))
                throw new IllegalStateException("J3_TERMINAL_IDENTITY_CONFLICT");
            return;
        }
        var row = jdbc.queryForMap("select collection_date,trigger_kind,started_at from j3_collection_run where run_id=:id", p);
        if (!row.get("collection_date").toString().equals(proof.date().toString())
                || !row.get("trigger_kind").equals(proof.trigger().name())
                || !((Timestamp) row.get("started_at")).toInstant().equals(micros(proof.startedAt())))
            throw new IllegalStateException("J3_RUN_IDENTITY_CONFLICT");
        for (var page : proof.pages()) appendPage(proof.runId(), page);
        if (jdbc.queryForObject("select count(*) from j3_collection_page where run_id=:id", p, Integer.class) != proof.pages().size())
            throw new IllegalStateException("J3_PAGE_COUNT_MISMATCH");
        if (!proof.successful() && !entries.isEmpty()) throw new IllegalArgumentException("J3_FAILED_CATALOGUE");
        if (proof.successful()) {
            List<Boolean> rawPresent = jdbc.query("""
                    select s.payload_raw is not null from provider_snapshot s join j3_collection_page p on p.snapshot_id=s.id
                    where p.run_id=:id for share of s
                    """, p, (rs, n) -> rs.getBoolean(1));
            if (rawPresent.size() != proof.pages().size() || rawPresent.contains(false))
                throw new IllegalStateException("J3_RAW_EVIDENCE_UNAVAILABLE");
            for (Entry entry : entries) {
                jdbc.update("insert into j3_catalog_entry(run_id,tournament_id,entry) values (:id,:t,cast(:json as jsonb))",
                        params(proof.runId()).addValue("t", entry.tournamentId()).addValue("json", JSON.writeValueAsString(entry)));
                for (Long snapshot : entry.sourceSnapshotIds()) {
                    int page = proof.pages().stream().filter(it -> snapshot.equals(it.snapshotId())).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("J3_ENTRY_SOURCE_MISMATCH")).page();
                    jdbc.update("insert into j3_catalog_source(run_id,tournament_id,page) values (:id,:t,:page)",
                            params(proof.runId()).addValue("t", entry.tournamentId()).addValue("page", page));
                }
            }
        }
        jdbc.update("update j3_collection_run set state=:state,finished_at=:finish,terminal_code=:code where run_id=:id", p);
        if (proof.successful()) {
            jdbc.update("""
                    insert into j3_last_success(collection_date,run_id,revision) values (:date,:id,1)
                    on conflict (collection_date) do update set run_id=excluded.run_id,revision=j3_last_success.revision+1
                    where (select finished_at from j3_collection_run where run_id=j3_last_success.run_id) <= :finish
                    """, p);
        }
    }

    @Override @Transactional(readOnly=true)
    public Optional<Collection> find(UUID id) {
        var p = params(id);
        return jdbc.query("select * from j3_collection_run where run_id=:id and state<>'RUNNING'", p,
                (rs,n) -> read(rs)).stream().findFirst();
    }
    @Override @Transactional
    public void interrupt(UUID id,Instant now,String reason) {
        jdbc.queryForObject("select pg_advisory_xact_lock(-6060)",java.util.Map.of(),Object.class);
        var rows=jdbc.queryForList("select * from j3_collection_run where run_id=:id for update",params(id));
        if(rows.isEmpty() || !"RUNNING".equals(rows.getFirst().get("state"))) return;
        var row=rows.getFirst();
        var pages=jdbc.query("select evidence::text from j3_collection_page where run_id=:id order by page",params(id),
                (r,n)->JSON.readValue(r.getString(1),J3MinimizedPageEvidence.class));
        Instant start=((java.sql.Timestamp)row.get("started_at")).toInstant();
        publish(new Proof(id,((java.sql.Date)row.get("collection_date")).toLocalDate(),
                Trigger.valueOf((String)row.get("trigger_kind")),start,now.isBefore(start)?start:now,
                State.INTERRUPTED,reason,pages),java.util.List.of());
    }
    private Collection read(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("run_id", UUID.class);
        var pages = jdbc.query("select evidence::text from j3_collection_page where run_id=:id order by page", params(id),
                (r,n) -> JSON.readValue(r.getString(1), J3MinimizedPageEvidence.class));
        var entries = jdbc.query("""
                select entry::text from j3_catalog_entry where run_id=:id
                order by entry->>'category' collate "C",entry->>'name' collate "C",tournament_id
                """, params(id), (r,n) -> JSON.readValue(r.getString(1), Entry.class));
        return new Collection(new Proof(id, rs.getObject("collection_date", LocalDate.class),
                Trigger.valueOf(rs.getString("trigger_kind")), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at").toInstant(), State.valueOf(rs.getString("state")),
                rs.getString("terminal_code"), pages), entries);
    }
    @Override @Transactional(readOnly=true)
    public Optional<Collection> latest(LocalDate date) {
        var ids = jdbc.query("""
                select l.run_id from j3_last_success l join j3_collection_run r on r.run_id=l.run_id
                where l.collection_date=:date and not exists (select 1 from j3_legacy_recovery h
                    where h.collection_date=:date and h.status='SUCCESS_PROVEN_CONTENT_UNAVAILABLE' and h.finished_at>r.finished_at)
                """, Map.of("date",date), (rs,n) -> rs.getObject(1,UUID.class));
        return ids.isEmpty() ? Optional.empty() : find(ids.getFirst());
    }
    @Override @Transactional(readOnly=true)
    public Optional<Collection> latest() {
        var dates = dates(1);
        return dates.isEmpty() ? Optional.empty() : latest(dates.getFirst().date());
    }
    @Override
    public boolean hasSuccess(LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from j3_last_success where collection_date=:date)
                    or exists(select 1 from j8_benchmark_campaign c join j8_benchmark_campaign_result r using(campaign_id)
                        where c.campaign_type='J3_SCHEDULED_EVENTS' and c.collection_date=:date and r.terminal_state='COMPLETED')
                """, Map.of("date", date), Boolean.class));
    }
    @Override
    public List<DateSummary> dates(int limit) {
        if (limit < 1 || limit > 3660) throw new IllegalArgumentException("J3_DATE_LIMIT");
        return jdbc.query("""
                select distinct on (collection_date) collection_date,run_id,finished_at,status from (
                    select r.collection_date,r.run_id,r.finished_at,'AVAILABLE' status from j3_last_success l
                        join j3_collection_run r on r.run_id=l.run_id
                    union all select collection_date,null::uuid,finished_at,status from j3_legacy_recovery
                        where status='SUCCESS_PROVEN_CONTENT_UNAVAILABLE'
                ) d order by collection_date desc,finished_at desc,run_id nulls last limit :limit
                """, Map.of("limit",limit), (r,n) -> new DateSummary(r.getObject(1,LocalDate.class),
                r.getObject(2,UUID.class),r.getTimestamp(3).toInstant(),r.getString(4)));
    }
    @Override
    public List<Legacy> legacyCandidates(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("J3_RECOVERY_LIMIT");
        return jdbc.query("""
                select c.campaign_id,c.collection_date,c.started_at,c.maximum_units,r.finished_at,r.completed_units
                from j8_benchmark_campaign c join j8_benchmark_campaign_result r using(campaign_id)
                where c.campaign_type='J3_SCHEDULED_EVENTS' and r.terminal_state='COMPLETED'
                    and not exists(select 1 from j3_legacy_recovery h where h.campaign_id=c.campaign_id)
                    and not exists(select 1 from j3_collection_run j where j.run_id=c.campaign_id and j.trigger_kind<>'LEGACY')
                order by r.finished_at,c.campaign_id limit :limit
                """, Map.of("limit",limit), (r,n) -> {
            UUID id = r.getObject("campaign_id",UUID.class);
            var pages = jdbc.query("""
                    select u.unit_ordinal,u.request_key,r.resolution_source,r.outcome_type,r.snapshot_id,r.parser_version,r.resolved_at,
                        r.snapshot_occurrence_id,
                        case when r.snapshot_occurrence_id is null then s.requested_at else o.requested_at end requested_at,
                        case when r.snapshot_occurrence_id is null then s.received_at else o.received_at end received_at,
                        case when r.snapshot_occurrence_id is null then s.latency_ms else o.latency_ms end latency_ms,
                        o.persistence_outcome
                    from j8_benchmark_unit u left join j8_benchmark_unit_result r on r.unit_id=u.id
                    left join provider_snapshot s on s.id=r.snapshot_id
                    left join provider_snapshot_occurrence o on o.id=r.snapshot_occurrence_id and o.snapshot_id=r.snapshot_id
                    where u.campaign_id=:id order by u.unit_ordinal
                    """, params(id), (p,m) -> new LegacyPage(p.getInt(1),p.getString(2),p.getString(3),p.getString(4),
                    p.getObject(5,Long.class),p.getString(6),instant(p,7),p.getObject(8,Long.class),
                    instant(p,9),instant(p,10),p.getObject(11,Long.class),p.getString(12)));
            return new Legacy(id,r.getObject("collection_date",LocalDate.class),r.getTimestamp("started_at").toInstant(),
                    r.getTimestamp("finished_at").toInstant(),r.getInt("completed_units"),r.getInt("maximum_units"),pages);
        });
    }
    @Override @Transactional(readOnly=true)
    public Optional<CatalogPage> page(UUID id,LocalDate date,int page,int pageSize) {
        if(page<1 || page>1_000_000 || (pageSize!=25 && pageSize!=50 && pageSize!=100))
            throw new IllegalArgumentException("J3_PAGINATION_INVALID");
        var p=params(id).addValue("date",date).addValue("limit",pageSize).addValue("offset",(long)(page-1)*pageSize);
        var completed=jdbc.query("select finished_at from j3_collection_run where run_id=:id and collection_date=:date and state='COMPLETED'",
                p,(r,n)->r.getTimestamp(1).toInstant());
        if(completed.isEmpty())return Optional.empty();
        long total=jdbc.queryForObject("select count(*) from j3_catalog_entry where run_id=:id",p,Long.class);
        if(page>Math.max(1,(total+pageSize-1)/pageSize))throw new IllegalArgumentException("J3_PAGE_OUT_OF_RANGE");
        var entries=jdbc.query("""
            select entry::text from j3_catalog_entry where run_id=:id
            order by entry->>'category' collate "C",entry->>'name' collate "C",tournament_id limit :limit offset :offset
            """,p,(row,n)->JSON.readValue(row.getString(1),Entry.class));
        return Optional.of(new CatalogPage(id,date,completed.getFirst(),page,pageSize,total,entries));
    }
    @Override
    public void recordRecovery(Legacy source, String status, String reason, Instant recoveredAt) {
        jdbc.update("""
                insert into j3_legacy_recovery(campaign_id,collection_date,finished_at,status,reason,recovered_at)
                values(:id,:date,:finish,:status,:reason,:recovered) on conflict(campaign_id) do nothing
                """, params(source.campaignId()).addValue("date",source.date()).addValue("finish",ts(source.finishedAt()))
                .addValue("status",status).addValue("reason",reason).addValue("recovered",ts(recoveredAt)));
    }
    private static MapSqlParameterSource params(UUID id) { return new MapSqlParameterSource("id",id); }
    private static Instant instant(ResultSet row,int column) throws SQLException {
        Timestamp value=row.getTimestamp(column); return value==null?null:value.toInstant();
    }
    private static Instant micros(Instant value) { return value.truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private static Timestamp ts(Instant value) { return Timestamp.from(micros(value)); }
}
