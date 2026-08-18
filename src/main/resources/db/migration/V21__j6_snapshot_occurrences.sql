create table provider_snapshot_occurrence (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null,
    requested_at timestamptz not null,
    received_at timestamptz,
    http_status integer,
    content_type varchar(160),
    latency_ms bigint,
    parser_version varchar(32),
    persistence_outcome varchar(16) not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_provider_snapshot_occurrence_snapshot
        foreign key (snapshot_id)
        references provider_snapshot (id)
        on delete restrict,
    constraint ck_provider_snapshot_occurrence_time
        check (received_at is null or received_at >= requested_at),
    constraint ck_provider_snapshot_occurrence_http_status
        check (http_status is null or http_status between 100 and 599),
    constraint ck_provider_snapshot_occurrence_latency
        check (latency_ms is null or latency_ms >= 0),
    constraint ck_provider_snapshot_occurrence_parser
        check (
            parser_version is null
            or parser_version ~ '^[A-Za-z0-9._-]+$'
        ),
    constraint ck_provider_snapshot_occurrence_outcome
        check (persistence_outcome in ('BASELINE', 'INSERTED', 'DEDUPLICATED'))
);

create index ix_provider_snapshot_occurrence_snapshot
    on provider_snapshot_occurrence (snapshot_id, received_at, id);

create index ix_provider_snapshot_occurrence_recent
    on provider_snapshot_occurrence (received_at desc nulls last, id desc);

insert into provider_snapshot_occurrence (
    snapshot_id,
    requested_at,
    received_at,
    http_status,
    content_type,
    latency_ms,
    parser_version,
    persistence_outcome,
    created_at
)
select
    id,
    requested_at,
    received_at,
    http_status,
    content_type,
    latency_ms,
    parser_version,
    'BASELINE',
    current_timestamp
from provider_snapshot;

create function reject_provider_snapshot_occurrence_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'provider_snapshot_occurrence is append-only';
end;
$$;

create trigger provider_snapshot_occurrence_append_only
before update or delete on provider_snapshot_occurrence
for each row execute function reject_provider_snapshot_occurrence_mutation();

comment on table provider_snapshot_occurrence is
    'J6 append-only acquisition occurrences. Historical snapshots receive one non-reconstructive BASELINE row.';
comment on column provider_snapshot_occurrence.persistence_outcome is
    'BASELINE is migration evidence only; INSERTED and DEDUPLICATED are recorded prospectively on every save.';
