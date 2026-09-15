-- WO-060: additive J3 results. No network, backfill guess or operator configuration mutation.
create table j3_collection_run (
    run_id uuid primary key,
    collection_date date not null,
    trigger_kind varchar(24) not null check (trigger_kind in ('MANUAL_PROVIDER','MANUAL_IMPORT','DAILY','DAILY_AT','SCHEDULED','LEGACY')),
    started_at timestamptz not null,
    state varchar(16) not null check (state in ('RUNNING','COMPLETED','FAILED','CANCELLED','INTERRUPTED')),
    finished_at timestamptz,
    terminal_code varchar(96),
    unique (run_id, collection_date),
    check ((state = 'RUNNING' and finished_at is null and terminal_code is null)
        or (state <> 'RUNNING' and finished_at is not null and terminal_code is not null
            and finished_at >= started_at and terminal_code ~ '^[A-Z0-9_]{1,96}$')),
    check (state <> 'COMPLETED' or terminal_code = 'NONE')
);

create table j3_collection_page (
    run_id uuid not null references j3_collection_run(run_id) on delete restrict,
    page smallint not null check (page between 1 and 35),
    snapshot_id bigint references provider_snapshot(id) on delete restrict,
    evidence jsonb not null check (jsonb_typeof(evidence) = 'object'),
    occurrence_id bigint generated always as ((evidence->>'occurrenceId')::bigint) stored,
    primary key (run_id, page),
    unique (run_id, snapshot_id),
    foreign key (occurrence_id, snapshot_id) references provider_snapshot_occurrence(id,snapshot_id) on delete restrict,
    check ((evidence->>'page')::integer = page),
    check ((evidence->>'snapshotId')::bigint is not distinct from snapshot_id)
);
create index ix_j3_collection_source on j3_collection_page(snapshot_id);

create table j3_catalog_entry (
    run_id uuid not null references j3_collection_run(run_id) on delete restrict,
    tournament_id bigint not null check (tournament_id > 0),
    entry jsonb not null check (jsonb_typeof(entry) = 'object'),
    primary key (run_id, tournament_id),
    check ((entry->>'tournamentId')::bigint = tournament_id)
);
create table j3_catalog_source (
    run_id uuid not null, tournament_id bigint not null, page smallint not null,
    primary key (run_id, tournament_id, page),
    foreign key (run_id, tournament_id) references j3_catalog_entry(run_id, tournament_id) on delete restrict,
    foreign key (run_id, page) references j3_collection_page(run_id, page) on delete restrict
);

create table j3_last_success (
    collection_date date primary key,
    run_id uuid not null,
    revision bigint not null check (revision > 0),
    foreign key (run_id, collection_date) references j3_collection_run(run_id, collection_date) on delete restrict
);
create table j3_legacy_recovery (
    campaign_id uuid primary key references j8_benchmark_campaign(campaign_id) on delete restrict,
    collection_date date not null,
    finished_at timestamptz not null,
    status varchar(40) not null check (status in ('RECOVERED','SUCCESS_PROVEN_CONTENT_UNAVAILABLE','UNPROVEN')),
    reason varchar(96) not null check (reason ~ '^[A-Z0-9_]{1,96}$'),
    recovered_at timestamptz not null
);
create index ix_j3_legacy_date on j3_legacy_recovery(collection_date, finished_at desc);
create index ix_j3_run_date on j3_collection_run(collection_date, finished_at desc, run_id) where state = 'COMPLETED';

create function guard_j3_collection_child() returns trigger language plpgsql as $$
declare r j3_collection_run%rowtype; s provider_snapshot%rowtype; o provider_snapshot_occurrence%rowtype; e jsonb;
begin
    if TG_OP <> 'INSERT' then raise exception 'J3 evidence is append-only'; end if;
    select * into strict r from j3_collection_run where run_id = NEW.run_id for update;
    if r.state <> 'RUNNING' then raise exception 'J3 terminal result is immutable'; end if;
    if TG_TABLE_NAME = 'j3_collection_page' then
        e := NEW.evidence;
        if coalesce((e->>'historicalCacheTimestampAbsent')::boolean,false)
            and (r.trigger_kind <> 'LEGACY' or e->>'resolutionSource' <> 'CACHE'
                or e->>'cacheStoredAt' is not null or e->>'occurrenceId' is not null) then
            raise exception 'Only legacy cache evidence may omit its unrecorded timestamp';
        end if;
        if NEW.page <> (select count(*) + 1 from j3_collection_page where run_id=NEW.run_id) then
            raise exception 'J3 pages must be contiguous';
        end if;
        if NEW.snapshot_id is not null then
            select * into strict s from provider_snapshot where id=NEW.snapshot_id for share;
            if e->>'occurrenceId' is not null then
                select * into strict o from provider_snapshot_occurrence
                    where id=(e->>'occurrenceId')::bigint and snapshot_id=NEW.snapshot_id;
                s.received_at:=o.received_at; s.requested_at:=o.requested_at; s.http_status:=o.http_status;
                s.content_type:=o.content_type; s.parser_version:=o.parser_version; s.latency_ms:=o.latency_ms;
            end if;
            if s.logical_endpoint <> 'SCHEDULED_EVENTS'
                or s.request_key <> 'SCHEDULED_EVENTS|date=' || r.collection_date || '|page=' || NEW.page
                or s.payload_sha256 is distinct from e->>'payloadSha256'
                or s.payload_size_bytes is distinct from (e->>'payloadSizeBytes')::integer
                or s.schema_status is distinct from e->>'schemaStatus'
                or s.http_status is distinct from (e->>'httpStatus')::integer
                or (e->>'receivedAt') is null or (e->>'requestedAt') is null
                -- JDBC and JSON timestamp casts may round opposite ways at PostgreSQL microsecond precision.
                -- The original nanoseconds remain in evidence; the exact occurrence FK remains authoritative.
                or abs(extract(epoch from (s.received_at - (e->>'receivedAt')::timestamptz))) > 0.000001
                or abs(extract(epoch from (s.requested_at - (e->>'requestedAt')::timestamptz))) > 0.000001
                or s.latency_ms is distinct from (e->>'latencyMillis')::bigint
                or s.parser_version <> 'scheduled-events-v1'
                or s.acquisition_mode <> (case when e->>'resolutionSource'='LOCAL_JSON_IMPORT'
                    then 'MANUAL_LOCAL_JSON_IMPORT' else 'DIRECT_LOCAL_ENDPOINT' end) then
                raise exception 'J3 snapshot metadata mismatch';
            end if;
        end if;
    end if;
    return NEW;
end $$;
create trigger j3_page_guard before insert or update or delete on j3_collection_page
    for each row execute function guard_j3_collection_child();
create trigger j3_entry_guard before insert or update or delete on j3_catalog_entry
    for each row execute function guard_j3_collection_child();
create trigger j3_source_guard before insert or update or delete on j3_catalog_source
    for each row execute function guard_j3_collection_child();

create function guard_j3_collection_terminal() returns trigger language plpgsql as $$
declare n integer;
begin
    if TG_OP = 'INSERT' then
        if NEW.state <> 'RUNNING' then raise exception 'J3 collection must begin RUNNING'; end if;
        return NEW;
    end if;
    if TG_OP = 'DELETE' or OLD.state <> 'RUNNING' then raise exception 'J3 terminal result is immutable'; end if;
    if (NEW.run_id,NEW.collection_date,NEW.trigger_kind,NEW.started_at)
        is distinct from (OLD.run_id,OLD.collection_date,OLD.trigger_kind,OLD.started_at) then
        raise exception 'J3 collection identity is immutable';
    end if;
    if NEW.state = 'COMPLETED' then
        select count(*) into n from j3_collection_page where run_id=NEW.run_id;
        if n not between 1 and 35 or exists (select 1 from j3_collection_page p where p.run_id=NEW.run_id
            and (p.snapshot_id is null or p.evidence->>'schemaStatus' is distinct from 'PARSED'
                or (p.evidence->>'hasNextPage')::boolean is distinct from (p.page < n)
                or p.evidence->>'terminalCode' is not null)) then
            raise exception 'J3 success requires complete pages';
        end if;
    elsif exists (select 1 from j3_catalog_entry where run_id=NEW.run_id) then
        raise exception 'J3 failed collection cannot publish a catalogue';
    end if;
    return NEW;
end $$;
create trigger j3_run_terminal before insert or update or delete on j3_collection_run
    for each row execute function guard_j3_collection_terminal();

create function guard_j3_last_success() returns trigger language plpgsql as $$
begin
    if TG_OP = 'DELETE' then raise exception 'J3 last success cannot be deleted'; end if;
    perform 1 from j3_collection_run where run_id=NEW.run_id and collection_date=NEW.collection_date and state='COMPLETED';
    if not found then raise exception 'J3 pointer requires a completed collection of the same date'; end if;
    if TG_OP='UPDATE' and (NEW.collection_date <> OLD.collection_date or NEW.revision <> OLD.revision+1) then
        raise exception 'J3 success revision must advance';
    end if;
    return NEW;
end $$;
create trigger j3_success_guard before insert or update or delete on j3_last_success
    for each row execute function guard_j3_last_success();

-- An ordinary J6 purge must never invalidate the last complete catalogue of a date.
create function protect_j3_latest_raw_payload() returns trigger language plpgsql as $$
begin
    if OLD.payload_raw is not null and NEW.payload_raw is null and exists (
        select 1 from j3_collection_page p join j3_last_success l on l.run_id=p.run_id where p.snapshot_id=OLD.id
    ) then raise exception 'J3 last-success raw evidence is protected'; end if;
    return NEW;
end $$;
create trigger protect_j3_raw_payload before update on provider_snapshot
    for each row execute function protect_j3_latest_raw_payload();
