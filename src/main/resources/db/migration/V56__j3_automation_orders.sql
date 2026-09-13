-- WO-060: preferences only. Applying this migration cannot dispatch any provider request.
create table j3_automation_settings (
    singleton boolean primary key default true check(singleton),
    enabled boolean not null,
    daily_mode varchar(24) not null check(daily_mode in ('STARTUP_OR_DAY_CHANGE','DAILY_AT')),
    daily_time time,
    revision bigint not null check(revision>0),
    updated_at timestamptz not null,
    owner_id uuid, owner_pid bigint, owner_started_at timestamptz,
    check ((daily_mode='DAILY_AT')=(daily_time is not null)),
    check (daily_time is null or extract(second from daily_time)=0),
    check ((owner_id is null and owner_pid is null and owner_started_at is null)
        or (owner_id is not null and owner_pid>0 and owner_started_at is not null))
);
insert into j3_automation_settings(singleton,enabled,daily_mode,revision,updated_at)
    values(true,true,'STARTUP_OR_DAY_CHANGE',1,current_timestamp);

create table j3_order (
    order_id uuid primary key,
    occurrence_key varchar(192) not null unique,
    rule_id uuid,rule_revision integer,
    collection_date date not null,
    trigger_kind varchar(24) not null check(trigger_kind in ('MANUAL_PROVIDER','MANUAL_IMPORT','DAILY','DAILY_AT','SCHEDULED')),
    due_at timestamptz not null,created_at timestamptz not null,
    state varchar(16) not null check(state in ('FUTURE','QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED','INTERRUPTED','MISSED','SKIPPED')),
    admitted_at timestamptz,deadline timestamptz,finished_at timestamptz,
    reason varchar(96) check(reason ~ '^[A-Z0-9_]{1,96}$'),
    owner_id uuid,owner_pid bigint,owner_started_at timestamptz,
    input_sha256 char(64) check(input_sha256 ~ '^[0-9a-f]{64}$'),
    unique(rule_id,rule_revision),
    check((trigger_kind='SCHEDULED' and rule_id is not null and rule_revision is not null and rule_revision>0)
        or (trigger_kind<>'SCHEDULED' and rule_id is null and rule_revision is null)),
    check((admitted_at is null and deadline is null and owner_id is null and owner_pid is null and owner_started_at is null)
        or (admitted_at is not null and deadline=admitted_at+interval '20 minutes'
            and owner_id is not null and owner_pid>0 and owner_started_at is not null)),
    check(state not in ('QUEUED','RUNNING') or admitted_at is not null),
    check((state in ('FUTURE','QUEUED','RUNNING') and finished_at is null and reason is null)
        or (state not in ('FUTURE','QUEUED','RUNNING') and finished_at is not null and reason is not null)),
    check(trigger_kind <> 'MANUAL_IMPORT' or input_sha256 is not null)
);
create unique index ux_j3_single_running on j3_order((true)) where state='RUNNING';
create unique index ux_j3_exact_active_schedule on j3_order(collection_date,due_at)
    where trigger_kind='SCHEDULED' and state in ('FUTURE','QUEUED','RUNNING');
create index ix_j3_order_queue on j3_order(state,due_at,order_id);

create function guard_j3_order() returns trigger language plpgsql as $$
begin
    if TG_OP='DELETE' then raise exception 'J3 order evidence is retained'; end if;
    if NEW.state='COMPLETED' then
        perform 1 from j3_collection_run where run_id=NEW.order_id and collection_date=NEW.collection_date and state='COMPLETED';
        if not found then raise exception 'J3 completed order requires its durable success'; end if;
    end if;
    if OLD.state not in ('FUTURE','QUEUED','RUNNING') then raise exception 'J3 terminal order is immutable'; end if;
    if (NEW.order_id,NEW.occurrence_key,NEW.rule_id,NEW.rule_revision,NEW.collection_date,NEW.trigger_kind,NEW.due_at,NEW.created_at,NEW.input_sha256)
        is distinct from (OLD.order_id,OLD.occurrence_key,OLD.rule_id,OLD.rule_revision,OLD.collection_date,OLD.trigger_kind,OLD.due_at,OLD.created_at,OLD.input_sha256) then
        raise exception 'J3 order identity is immutable';
    end if;
    if OLD.admitted_at is not null and (NEW.admitted_at,NEW.deadline,NEW.owner_id,NEW.owner_pid,NEW.owner_started_at)
        is distinct from (OLD.admitted_at,OLD.deadline,OLD.owner_id,OLD.owner_pid,OLD.owner_started_at) then
        raise exception 'J3 admission is immutable';
    end if;
    if not ((OLD.state='FUTURE' and NEW.state in ('QUEUED','CANCELLED','MISSED','SKIPPED'))
        or (OLD.state='QUEUED' and NEW.state in ('RUNNING','CANCELLED','INTERRUPTED','MISSED','SKIPPED'))
        or (OLD.state='RUNNING' and NEW.state in ('COMPLETED','FAILED','CANCELLED','INTERRUPTED'))) then
        raise exception 'Invalid J3 order transition';
    end if;
    return NEW;
end $$;
create trigger j3_order_guard before update or delete on j3_order for each row execute function guard_j3_order();
