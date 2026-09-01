create table j7_delivery (
    id bigint generated always as identity primary key,
    delivery_uuid uuid not null,
    export_manifest_id bigint not null,
    export_uuid uuid not null,
    file_sha256 varchar(64) not null,
    data_sha256 varchar(64) not null,
    file_size_bytes bigint not null,
    idempotency_key varchar(111) not null,
    protocol_version varchar(16) not null,
    current_state varchar(48) not null,
    created_at timestamptz not null,
    state_changed_at timestamptz not null,
    constraint fk_j7_delivery_export_manifest
        foreign key (export_manifest_id)
        references export_manifest (id)
        on delete restrict,
    constraint uq_j7_delivery_uuid unique (delivery_uuid),
    constraint uq_j7_delivery_export unique (export_manifest_id),
    constraint uq_j7_delivery_export_uuid unique (export_uuid),
    constraint uq_j7_delivery_idempotency_key unique (idempotency_key),
    constraint ck_j7_delivery_identifiers check (
        delivery_uuid <> '00000000-0000-0000-0000-000000000000'::uuid
        and export_uuid <> '00000000-0000-0000-0000-000000000000'::uuid
        and delivery_uuid::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        and export_uuid::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    constraint ck_j7_delivery_sha256 check (
        file_sha256 ~ '^[0-9a-f]{64}$'
        and data_sha256 ~ '^[0-9a-f]{64}$'
    ),
    constraint ck_j7_delivery_file_size
        check (file_size_bytes between 1 and 5242880),
    constraint ck_j7_delivery_idempotency_key check (
        idempotency_key = 'j7:' || export_uuid::text || ':sha256:' || file_sha256
    ),
    constraint ck_j7_delivery_protocol_version
        check (protocol_version = '1.0'),
    constraint ck_j7_delivery_state check (current_state in (
        'NOT_ATTEMPTED',
        'IN_FLIGHT',
        'DELIVERED',
        'DUPLICATE_CONFIRMED',
        'REJECTED_TERMINAL',
        'UNKNOWN_RECONCILIATION_REQUIRED'
    )),
    constraint ck_j7_delivery_state_time
        check (state_changed_at >= created_at)
);

create unique index uq_j7_delivery_single_in_flight
    on j7_delivery ((1))
    where current_state = 'IN_FLIGHT';

create table j7_delivery_attempt (
    id bigint generated always as identity primary key,
    attempt_uuid uuid not null,
    delivery_id bigint not null,
    attempt_number integer not null,
    started_at timestamptz not null,
    created_at timestamptz not null default clock_timestamp(),
    constraint fk_j7_delivery_attempt_delivery
        foreign key (delivery_id)
        references j7_delivery (id)
        on delete restrict,
    constraint uq_j7_delivery_attempt_uuid unique (attempt_uuid),
    constraint uq_j7_delivery_attempt_number unique (delivery_id, attempt_number),
    constraint ck_j7_delivery_attempt_uuid check (
        attempt_uuid <> '00000000-0000-0000-0000-000000000000'::uuid
        and attempt_uuid::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    constraint ck_j7_delivery_attempt_number check (attempt_number > 0)
);

create table j7_delivery_attempt_result (
    attempt_id bigint primary key,
    terminal_state varchar(48) not null,
    http_status integer,
    safe_result_code varchar(96) not null,
    acknowledgement_sha256 varchar(64),
    remote_import_id uuid,
    acknowledgement_received_at timestamptz,
    completed_at timestamptz not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_j7_delivery_attempt_result_attempt
        foreign key (attempt_id)
        references j7_delivery_attempt (id)
        on delete restrict,
    constraint ck_j7_delivery_attempt_result_state check (terminal_state in (
        'DELIVERED',
        'DUPLICATE_CONFIRMED',
        'REJECTED_TERMINAL',
        'UNKNOWN_RECONCILIATION_REQUIRED'
    )),
    constraint ck_j7_delivery_attempt_result_http
        check (http_status is null or http_status between 100 and 599),
    constraint ck_j7_delivery_attempt_result_code
        check (safe_result_code ~ '^[A-Z][A-Z0-9_]{0,95}$'),
    constraint ck_j7_delivery_attempt_result_ack_sha
        check (
            acknowledgement_sha256 is null
            or acknowledgement_sha256 ~ '^[0-9a-f]{64}$'
        ),
    constraint ck_j7_delivery_attempt_result_remote_id check (
        remote_import_id is null
        or remote_import_id <> '00000000-0000-0000-0000-000000000000'::uuid
           and remote_import_id::text ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    ),
    constraint ck_j7_delivery_attempt_result_shape check (
        (
            terminal_state = 'DELIVERED'
            and http_status = 201
            and acknowledgement_sha256 is not null
            and remote_import_id is not null
            and acknowledgement_received_at is not null
        )
        or (
            terminal_state = 'DUPLICATE_CONFIRMED'
            and http_status = 200
            and acknowledgement_sha256 is not null
            and remote_import_id is not null
            and acknowledgement_received_at is not null
        )
        or (
            terminal_state = 'REJECTED_TERMINAL'
            and http_status between 400 and 499
            and acknowledgement_sha256 is null
            and remote_import_id is null
            and acknowledgement_received_at is null
        )
        or (
            terminal_state = 'UNKNOWN_RECONCILIATION_REQUIRED'
            and (http_status is null or http_status not between 400 and 499)
            and acknowledgement_sha256 is null
            and remote_import_id is null
            and acknowledgement_received_at is null
        )
    )
);

create function guard_j7_delivery_insert()
returns trigger
language plpgsql
as $$
declare
    manifest export_manifest%rowtype;
begin
    select *
    into manifest
    from export_manifest
    where id = new.export_manifest_id
    for key share;

    if not found
       or manifest.export_kind is distinct from 'J7_CANONICAL_EVENT'
       or manifest.validation_status is distinct from 'HUMAN_VALIDATED'
       or manifest.export_uuid is distinct from new.export_uuid
       or manifest.content_sha256 is distinct from new.file_sha256
       or manifest.data_sha256 is distinct from new.data_sha256
       or manifest.content_size_bytes is distinct from new.file_size_bytes then
        raise exception 'J7 delivery requires one exact HUMAN_VALIDATED export';
    end if;

    if new.current_state <> 'NOT_ATTEMPTED'
       or new.state_changed_at <> new.created_at then
        raise exception 'J7 delivery must start as NOT_ATTEMPTED';
    end if;
    return new;
end;
$$;

create trigger j7_delivery_insert_guard
before insert on j7_delivery
for each row execute function guard_j7_delivery_insert();

create function guard_j7_delivery_attempt_insert()
returns trigger
language plpgsql
as $$
declare
    delivery_state varchar(48);
    delivery_state_changed_at timestamptz;
    expected_attempt_number integer;
begin
    select current_state, state_changed_at
    into delivery_state, delivery_state_changed_at
    from j7_delivery
    where id = new.delivery_id
    for update;

    if not found
       or delivery_state not in ('NOT_ATTEMPTED', 'UNKNOWN_RECONCILIATION_REQUIRED') then
        raise exception 'J7 delivery is not claimable';
    end if;
    if exists (
        select 1
        from j7_delivery_attempt attempt
        left join j7_delivery_attempt_result result on result.attempt_id = attempt.id
        where attempt.delivery_id = new.delivery_id
          and result.attempt_id is null
    ) then
        raise exception 'J7 delivery already has an unresolved attempt';
    end if;

    select coalesce(max(attempt_number), 0) + 1
    into expected_attempt_number
    from j7_delivery_attempt
    where delivery_id = new.delivery_id;

    if new.attempt_number <> expected_attempt_number
       or new.started_at < delivery_state_changed_at then
        raise exception 'J7 delivery attempt sequence is invalid';
    end if;
    return new;
end;
$$;

create trigger j7_delivery_attempt_insert_guard
before insert on j7_delivery_attempt
for each row execute function guard_j7_delivery_attempt_insert();

create function guard_j7_delivery_attempt_result_insert()
returns trigger
language plpgsql
as $$
declare
    attempt_record j7_delivery_attempt%rowtype;
    delivery_state varchar(48);
    latest_attempt_id bigint;
begin
    select attempt.*
    into attempt_record
    from j7_delivery_attempt attempt
    where attempt.id = new.attempt_id;

    if not found then
        raise exception 'J7 delivery attempt does not exist';
    end if;

    select current_state
    into delivery_state
    from j7_delivery
    where id = attempt_record.delivery_id
    for update;

    select id
    into latest_attempt_id
    from j7_delivery_attempt
    where delivery_id = attempt_record.delivery_id
    order by attempt_number desc
    limit 1;

    if delivery_state <> 'IN_FLIGHT'
       or latest_attempt_id is distinct from new.attempt_id
       or new.completed_at < attempt_record.started_at
       or (
            new.acknowledgement_received_at is not null
            and (
                new.acknowledgement_received_at < attempt_record.started_at
                or new.acknowledgement_received_at > new.completed_at
            )
       ) then
        raise exception 'J7 delivery attempt result is not current';
    end if;
    return new;
end;
$$;

create trigger j7_delivery_attempt_result_insert_guard
before insert on j7_delivery_attempt_result
for each row execute function guard_j7_delivery_attempt_result_insert();

create function guard_j7_delivery_update_or_delete()
returns trigger
language plpgsql
as $$
declare
    latest_attempt_id bigint;
    latest_attempt_started_at timestamptz;
    result_state varchar(48);
    result_completed_at timestamptz;
begin
    if tg_op = 'DELETE' then
        raise exception 'J7 delivery ledger rows cannot be deleted';
    end if;
    if new is not distinct from old then
        return new;
    end if;
    if new.id is distinct from old.id
       or new.delivery_uuid is distinct from old.delivery_uuid
       or new.export_manifest_id is distinct from old.export_manifest_id
       or new.export_uuid is distinct from old.export_uuid
       or new.file_sha256 is distinct from old.file_sha256
       or new.data_sha256 is distinct from old.data_sha256
       or new.file_size_bytes is distinct from old.file_size_bytes
       or new.idempotency_key is distinct from old.idempotency_key
       or new.protocol_version is distinct from old.protocol_version
       or new.created_at is distinct from old.created_at then
        raise exception 'J7 delivery identity and idempotency are immutable';
    end if;
    if new.state_changed_at < old.state_changed_at then
        raise exception 'J7 delivery state time cannot move backwards';
    end if;

    select attempt.id, attempt.started_at
    into latest_attempt_id, latest_attempt_started_at
    from j7_delivery_attempt attempt
    where attempt.delivery_id = old.id
    order by attempt.attempt_number desc
    limit 1;

    if old.current_state in ('NOT_ATTEMPTED', 'UNKNOWN_RECONCILIATION_REQUIRED')
       and new.current_state = 'IN_FLIGHT' then
        if latest_attempt_id is null
           or exists (
                select 1 from j7_delivery_attempt_result
                where attempt_id = latest_attempt_id
           )
           or latest_attempt_started_at is distinct from new.state_changed_at then
            raise exception 'J7 delivery claim has no exact append-only attempt';
        end if;
        return new;
    end if;

    if old.current_state = 'IN_FLIGHT'
       and new.current_state in (
            'DELIVERED',
            'DUPLICATE_CONFIRMED',
            'REJECTED_TERMINAL',
            'UNKNOWN_RECONCILIATION_REQUIRED'
       ) then
        select terminal_state, completed_at
        into result_state, result_completed_at
        from j7_delivery_attempt_result
        where attempt_id = latest_attempt_id;
        if result_state is distinct from new.current_state
           or result_completed_at is distinct from new.state_changed_at then
            raise exception 'J7 delivery completion has no exact append-only result';
        end if;
        return new;
    end if;

    raise exception 'J7 delivery state transition is not allowed';
end;
$$;

create trigger j7_delivery_update_delete_guard
before update or delete on j7_delivery
for each row execute function guard_j7_delivery_update_or_delete();

create function reject_j7_delivery_child_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'J7 delivery attempts and results are append-only';
end;
$$;

create trigger j7_delivery_attempt_append_only
before update or delete on j7_delivery_attempt
for each row execute function reject_j7_delivery_child_mutation();

create trigger j7_delivery_attempt_result_append_only
before update or delete on j7_delivery_attempt_result
for each row execute function reject_j7_delivery_child_mutation();

comment on table j7_delivery is
    'Immutable J7 delivery identity and separate six-state delivery ledger; no export bytes.';
comment on table j7_delivery_attempt is
    'Append-only manually initiated delivery attempts; one global IN_FLIGHT delivery.';
comment on table j7_delivery_attempt_result is
    'Append-only minimized delivery results; no acknowledgement body or transport diagnostic.';
