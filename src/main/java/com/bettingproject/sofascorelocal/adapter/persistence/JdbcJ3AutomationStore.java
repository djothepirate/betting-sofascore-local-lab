package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.port.J3AutomationStore;
import com.bettingproject.sofascorelocal.port.J3CollectionStore;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import static com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;

/** The singleton settings lock is also the J3 admission/configuration serialization boundary. */
@Repository
public class JdbcJ3AutomationStore implements J3AutomationStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final J3CollectionStore collections;
    public JdbcJ3AutomationStore(NamedParameterJdbcTemplate jdbc,J3CollectionStore collections) {
        this.jdbc=jdbc;this.collections=collections;
    }
    @Override public Settings settings() {return readSettings(false);}
    private Settings readSettings(boolean lock) {
        if(lock)jdbc.queryForObject("select pg_advisory_xact_lock(-6060)",Map.of(),Object.class);
        return jdbc.queryForObject("select * from j3_automation_settings where singleton"+(lock?" for update":""),Map.of(),
                (r,n)->new Settings(r.getBoolean("enabled"),Mode.valueOf(r.getString("daily_mode")),
                        r.getObject("daily_time",LocalTime.class),r.getLong("revision"),r.getTimestamp("updated_at").toInstant()));
    }
    @Override @Transactional public Settings configure(long revision,boolean enabled,Mode mode,LocalTime time,Instant now) {
        var before=readSettings(true);
        if(before.revision()!=revision)throw new IllegalStateException("J3_SETTINGS_CHANGED");
        var next=new Settings(enabled,mode,time,revision+1,now);
        if(!before.enabled() || !enabled) jdbc.update("""
            update j3_order set state='MISSED',finished_at=:now,reason='AUTOMATION_DISABLED'
            where state='FUTURE' and due_at<=:now
            """,Map.of("now",ts(now)));
        jdbc.update("""
            update j3_automation_settings set enabled=:enabled,daily_mode=:mode,daily_time=:time,revision=:revision,updated_at=:now
            where singleton
            """,new MapSqlParameterSource("enabled",enabled).addValue("mode",mode.name()).addValue("time",time)
                .addValue("revision",next.revision()).addValue("now",ts(now)));
        if(!enabled) jdbc.update("""
            update j3_order set state='CANCELLED',finished_at=:now,reason='AUTOMATION_DISABLED'
            where state='QUEUED' and trigger_kind in ('DAILY','DAILY_AT','SCHEDULED')
            """,Map.of("now",ts(now)));
        // A revision may change its recurrence, but can never replay the old time.
        jdbc.update("""
            update j3_order set state='CANCELLED',finished_at=:now,reason='CONFIGURATION_CHANGED'
            where state='FUTURE' and trigger_kind='DAILY_AT'
            """,Map.of("now",ts(now)));
        return next;
    }
    @Override @Transactional public boolean lead(Owner owner,Predicate<Owner> isAlive) {
        readSettings(true);var current=currentLeader();
        if(current!=null && !current.id().equals(owner.id()) && isAlive.test(current))return false;
        jdbc.update("update j3_automation_settings set owner_id=:owner,owner_pid=:pid,owner_started_at=:processStart where singleton",owner(owner));
        return true;
    }
    @Override @Transactional public void release(Owner owner) {
        readSettings(true);
        jdbc.update("""
            update j3_automation_settings set owner_id=null,owner_pid=null,owner_started_at=null
            where singleton and owner_id=:owner
            """,owner(owner));
    }
    private Owner currentLeader() {
        return jdbc.queryForObject("select owner_id,owner_pid,owner_started_at from j3_automation_settings where singleton",Map.of(),
                (r,n)->r.getObject(1)==null?null:new Owner(r.getObject(1,UUID.class),r.getLong(2),r.getTimestamp(3).toInstant()));
    }
    @Override @Transactional public Order schedule(UUID ruleId,int revision,LocalDate date,Instant dueAt,Instant now) {
        readSettings(true);
        if(ruleId==null || revision<1 || !dueAt.isAfter(now))throw new IllegalArgumentException("J3_PLAN_MUST_BE_FUTURE");
        String key="SCHEDULED|"+ruleId+"|"+revision;
        var duplicate=byKey(key);
        if(duplicate.isPresent()) {
            var existing=duplicate.orElseThrow();
            if(!existing.date().equals(date) || !existing.dueAt().equals(micros(dueAt)))throw new IllegalStateException("J3_PLAN_IDENTITY_CONFLICT");
            return existing;
        }
        var previous=jdbc.query("select * from j3_order where rule_id=:rule order by rule_revision desc limit 1",
                Map.of("rule",ruleId),(r,n)->read(r));
        if(previous.isEmpty()?revision!=1:revision!=previous.getFirst().ruleRevision()+1)
            throw new IllegalStateException("J3_PLAN_REVISION_CONFLICT");
        if(!previous.isEmpty()) {
            var old=previous.getFirst();
            if(old.admittedAt()!=null)throw new IllegalStateException("J3_PLAN_ALREADY_ADMITTED");
            if(!old.terminal())terminal(old.id(),OrderState.CANCELLED,"PLAN_REVISED",now);
        }
        Integer count=jdbc.queryForObject("select count(*) from j3_order where trigger_kind='SCHEDULED' and state in ('FUTURE','QUEUED','RUNNING')",Map.of(),Integer.class);
        if(count>=MAXIMUM_PLANS)throw new IllegalStateException("J3_PLAN_LIMIT");
        UUID id=UUID.randomUUID();
        insert(id,key,ruleId,revision,date,Trigger.SCHEDULED,dueAt,now,null,null);
        return find(id).orElseThrow();
    }
    @Override @Transactional public void cancel(UUID id,Instant now) {
        readSettings(true);var order=find(id).orElseThrow();
        if(order.state()==OrderState.RUNNING)throw new IllegalStateException("J3_PLAN_ALREADY_ADMITTED");
        if(!order.terminal())terminal(id,OrderState.CANCELLED,"OPERATOR_CANCELLED",now);
    }
    @Override @Transactional public Order manual(UUID id,LocalDate date,Trigger trigger,String hash,Owner owner,Instant now) {
        readSettings(true);
        if(trigger!=Trigger.MANUAL_PROVIDER && trigger!=Trigger.MANUAL_IMPORT)throw new IllegalArgumentException("J3_MANUAL_TRIGGER_REQUIRED");
        var previous=find(id);
        if(previous.isPresent()) {
            var o=previous.orElseThrow();
            if(!o.date().equals(date) || o.trigger()!=trigger || !Objects.equals(o.inputSha256(),hash))
                throw new IllegalStateException("J3_MANUAL_IDENTITY_CONFLICT");
            return o;
        }
        insert(id,"MANUAL|"+id,null,0,date,trigger,now,now,owner,hash);
        // Count before commit: a malicious or repeated form cannot create an unbounded execution queue.
        Integer queued=jdbc.queryForObject("select count(*) from j3_order where state in ('QUEUED','RUNNING')",Map.of(),Integer.class);
        if(queued>100)throw new IllegalStateException("J3_MANUAL_QUEUE_FULL");
        return find(id).orElseThrow();
    }
    private void insert(UUID id,String key,UUID rule,int revision,LocalDate date,Trigger trigger,
                        Instant due,Instant now,Owner owner,String hash) {
        var p=new MapSqlParameterSource("id",id).addValue("key",key).addValue("rule",rule)
                .addValue("revision",rule==null?null:revision).addValue("date",date).addValue("trigger",trigger.name())
                .addValue("due",ts(due)).addValue("now",ts(now)).addValue("state",owner==null?"FUTURE":"QUEUED")
                .addValue("admitted",owner==null?null:ts(now)).addValue("deadline",owner==null?null:ts(now.plus(ORDER_DEADLINE)))
                .addValue("owner",owner==null?null:owner.id()).addValue("pid",owner==null?null:owner.pid())
                .addValue("processStart",owner==null?null:ts(owner.processStartedAt())).addValue("hash",hash);
        jdbc.update("""
            insert into j3_order(order_id,occurrence_key,rule_id,rule_revision,collection_date,trigger_kind,due_at,created_at,state,
                admitted_at,deadline,owner_id,owner_pid,owner_started_at,input_sha256)
            values(:id,:key,:rule,:revision,:date,:trigger,:due,:now,:state,:admitted,:deadline,:owner,:pid,:processStart,:hash)
            on conflict(occurrence_key) do nothing
            """,p);
    }
    @Override @Transactional public void tick(Owner owner,Instant previousTick,Instant now,boolean continuous,String unavailable) {
        Settings settings=readSettings(true);
        if(!normalize(owner).equals(currentLeader()))throw new IllegalStateException("J3_SCHEDULER_NOT_OWNER");
        LocalDate date=now.atZone(ZONE).toLocalDate();
        if(settings.mode()==Mode.DAILY_AT) {
            // Materialize absent days as missed evidence, in bounded batches after downtime.
            // Past instants never become provider retries; due admission below still requires continuity.
            LocalDate first=jdbc.queryForObject("""
                    select coalesce(max(collection_date)+1,cast(:since as date)) from j3_order
                    where trigger_kind='DAILY_AT' and occurrence_key like :prefix
                    """,Map.of("since",settings.updatedAt().atZone(ZONE).toLocalDate(),
                        "prefix","DAILY_AT|"+settings.revision()+"|%"),LocalDate.class);
            for(int count=0;count<31 && !first.isAfter(date);count++,first=first.plusDays(1)) {
                String key=timedKey(settings,first);
                insert(UUID.randomUUID(),key,null,0,first,Trigger.DAILY_AT,
                        dailyOccurrence(first,settings.dailyTime()),now,null,null);
            }
        }
        var due=jdbc.query("select * from j3_order where state='FUTURE' and due_at<=:now order by due_at,order_id",
                Map.of("now",ts(now)),(r,n)->read(r));
        boolean explicitToday=false;
        for(var order:due) {
            String missed=!settings.enabled()?"AUTOMATION_DISABLED":unavailable!=null?unavailable
                    :!continuous || order.dueAt().isBefore(previousTick)?"LAB_NOT_IN_SERVICE":null;
            if(missed!=null)terminal(order.id(),OrderState.MISSED,missed,now);
            else {admit(order.id(),owner,now);if(order.date().equals(date))explicitToday=true;}
        }
        if(settings.enabled() && unavailable==null && settings.mode()==Mode.STARTUP_OR_DAY_CHANGE && byKey(dailyKey(date)).isEmpty()) {
            UUID id=UUID.randomUUID();insert(id,dailyKey(date),null,0,date,Trigger.DAILY,now,now,owner,null);
            if(collections.hasSuccess(date))terminal(id,OrderState.SKIPPED,"DATE_ALREADY_SUCCESSFUL",now);
            else if(explicitToday)terminal(id,OrderState.SKIPPED,"EXPLICIT_OCCURRENCE_HAS_PRIORITY",now);
        }
    }
    private void admit(UUID id,Owner owner,Instant now) {
        jdbc.update("""
            update j3_order set state='QUEUED',admitted_at=:now,deadline=:deadline,
                owner_id=:owner,owner_pid=:pid,owner_started_at=:processStart where order_id=:id and state='FUTURE'
            """,owner(owner).addValue("now",ts(now)).addValue("deadline",ts(now.plus(ORDER_DEADLINE))).addValue("id",id));
    }
    @Override @Transactional public Optional<Order> claim(Owner owner,Instant now) {
        Settings settings=readSettings(true);
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from j3_order where state='RUNNING')",Map.of(),Boolean.class)))
            return Optional.empty();
        var queued=jdbc.query("select * from j3_order where state='QUEUED' and owner_id=:owner order by due_at,case when trigger_kind='DAILY' then 1 else 0 end,order_id",
                owner(owner),(r,n)->read(r));
        for(var order:queued) {
            if(!now.isBefore(order.deadline())) {terminal(order.id(),OrderState.MISSED,"ADMISSION_DEADLINE_EXPIRED",now);continue;}
            if(order.automatic() && !settings.enabled()) {terminal(order.id(),OrderState.CANCELLED,"AUTOMATION_DISABLED",now);continue;}
            if(order.trigger()==Trigger.DAILY && collections.hasSuccess(order.date())) {
                terminal(order.id(),OrderState.SKIPPED,"DATE_ALREADY_SUCCESSFUL",now);continue;
            }
            jdbc.update("update j3_order set state='RUNNING' where order_id=:id and state='QUEUED'",Map.of("id",order.id()));
            return find(order.id());
        }
        return Optional.empty();
    }
    @Override @Transactional public void finish(UUID id,Owner owner,OrderState state,String reason,Instant now) {
        readSettings(true);var order=find(id).orElseThrow();
        if(order.owner()==null || !order.owner().equals(normalize(owner)))throw new IllegalStateException("J3_ORDER_NOT_OWNER");
        if(order.terminal()) {
            if(order.state()!=state || !Objects.equals(order.reason(),reason))throw new IllegalStateException("J3_ORDER_TERMINAL_CONFLICT");
            return;
        }
        terminal(id,state,reason,now);
    }
    private void terminal(UUID id,OrderState state,String reason,Instant now) {
        jdbc.update("update j3_order set state=:state,reason=:reason,finished_at=:now where order_id=:id",
                new MapSqlParameterSource("id",id).addValue("state",state.name()).addValue("reason",reason).addValue("now",ts(now)));
    }
    @Override public List<Order> recent(int limit) {
        if(limit<1 || limit>1000)throw new IllegalArgumentException("J3_ORDER_LIST_LIMIT");
        return jdbc.query("select * from j3_order order by created_at desc,order_id limit :limit",Map.of("limit",limit),(r,n)->read(r));
    }
    @Override public Optional<Order> find(UUID id) {
        return jdbc.query("select * from j3_order where order_id=:id",Map.of("id",id),(r,n)->read(r)).stream().findFirst();
    }
    private Optional<Order> byKey(String key) {
        return jdbc.query("select * from j3_order where occurrence_key=:key",Map.of("key",key),(r,n)->read(r)).stream().findFirst();
    }
    @Override public List<Order> interrupted(Owner current) {
        return jdbc.query("select * from j3_order where state in ('QUEUED','RUNNING') and owner_id<>:owner order by due_at,order_id",
                owner(current),(r,n)->read(r));
    }
    private static Order read(ResultSet r)throws SQLException {
        UUID owner=r.getObject("owner_id",UUID.class);
        return new Order(r.getObject("order_id",UUID.class),r.getString("occurrence_key"),r.getObject("rule_id",UUID.class),
                r.getInt("rule_revision"),r.getObject("collection_date",LocalDate.class),Trigger.valueOf(r.getString("trigger_kind")),
                instant(r,"due_at"),instant(r,"created_at"),OrderState.valueOf(r.getString("state")),instant(r,"admitted_at"),
                instant(r,"deadline"),instant(r,"finished_at"),r.getString("reason"),owner==null?null:
                new Owner(owner,r.getLong("owner_pid"),instant(r,"owner_started_at")),r.getString("input_sha256"));
    }
    private static MapSqlParameterSource owner(Owner owner) {
        return new MapSqlParameterSource("owner",owner.id()).addValue("pid",owner.pid()).addValue("processStart",ts(owner.processStartedAt()));
    }
    private static Owner normalize(Owner o) {return new Owner(o.id(),o.pid(),micros(o.processStartedAt()));}
    private static Instant instant(ResultSet row,String column)throws SQLException {var value=row.getTimestamp(column);return value==null?null:value.toInstant();}
    private static Instant micros(Instant value) {return value.truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
    private static Timestamp ts(Instant value) {return Timestamp.from(micros(value));}
}
