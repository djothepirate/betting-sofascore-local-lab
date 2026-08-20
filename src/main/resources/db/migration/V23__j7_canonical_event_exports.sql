alter table export_manifest
    add column export_kind varchar(32),
    add column export_uuid uuid,
    add column canonical_event_id uuid,
    add column schema_id text,
    add column generated_at timestamptz,
    add column data_sha256 varchar(64),
    add column source_set_sha256 varchar(64),
    add column candidate_content_sha256 varchar(64),
    add column content_size_bytes bigint,
    add column source_observations jsonb,
    add column decided_at timestamptz,
    add column decision_reason varchar(500),
    add column decision_intent_status varchar(32),
    add column decision_intent_at timestamptz,
    add column decision_intent_reason varchar(500),
    add column decision_intent_path text,
    add column decision_intent_content_sha256 varchar(64),
    add column decision_intent_content_size_bytes bigint;

alter table export_manifest
    add constraint fk_export_manifest_j7_event
        foreign key (canonical_event_id)
        references canonical_event (id)
        on delete restrict,
    add constraint ck_export_manifest_j7_kind
        check (export_kind is null or export_kind = 'J7_CANONICAL_EVENT'),
    add constraint ck_export_manifest_j7_shape
        check (
            (
                export_kind is null
                and export_uuid is null
                and canonical_event_id is null
                and schema_id is null
                and generated_at is null
                and data_sha256 is null
                and source_set_sha256 is null
                and candidate_content_sha256 is null
                and content_size_bytes is null
                and source_observations is null
                and decided_at is null
                and decision_reason is null
                and decision_intent_status is null
                and decision_intent_at is null
                and decision_intent_reason is null
                and decision_intent_path is null
                and decision_intent_content_sha256 is null
                and decision_intent_content_size_bytes is null
            )
            or (
                export_kind is not null
                and export_kind = 'J7_CANONICAL_EVENT'
                and export_uuid is not null
                and canonical_event_id is not null
                and schema_id is not null
                and schema_id =
                    'urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1'
                and schema_version = '1.0.0'
                and generated_at is not null
                and data_sha256 is not null
                and data_sha256 ~ '^[0-9a-f]{64}$'
                and source_set_sha256 is not null
                and source_set_sha256 ~ '^[0-9a-f]{64}$'
                and candidate_content_sha256 is not null
                and candidate_content_sha256 ~ '^[0-9a-f]{64}$'
                and content_size_bytes is not null
                and content_size_bytes between 1 and 5242880
                and source_observations is not null
                and coalesce(jsonb_typeof(source_observations), '') = 'array'
                and jsonb_array_length(source_observations) = 5
                and coalesce(source_observations #>> '{0,component}', '') = 'EVENT_STATE'
                and coalesce(source_observations #>> '{1,component}', '') = 'EVENT_DETAILS'
                and coalesce(source_observations #>> '{2,component}', '') = 'EVENT_STATISTICS'
                and coalesce(source_observations #>> '{3,component}', '') = 'EVENT_INCIDENTS'
                and coalesce(source_observations #>> '{4,component}', '') = 'EVENT_LINEUPS'
                and coalesce(jsonb_typeof(warnings), '') = 'array'
                and cardinality(source_snapshot_ids) between 0 and 5
                and array_position(source_snapshot_ids, null) is null
                and validation_status in (
                    'COHERENCE_CHECKED',
                    'HUMAN_VALIDATED',
                    'REJECTED'
                )
                and (
                    (
                        decision_intent_status is null
                        and decision_intent_at is null
                        and decision_intent_reason is null
                        and decision_intent_path is null
                        and decision_intent_content_sha256 is null
                        and decision_intent_content_size_bytes is null
                    )
                    or (
                        decision_intent_status in ('HUMAN_VALIDATED', 'REJECTED')
                        and decision_intent_at is not null
                        and decision_intent_at >= generated_at
                        and decision_intent_path =
                            'j7-' || canonical_event_id::text || '-' || export_uuid::text
                            || case decision_intent_status
                                when 'HUMAN_VALIDATED' then '.validated.json'
                                when 'REJECTED' then '.rejected.json'
                            end
                        and decision_intent_content_sha256 ~ '^[0-9a-f]{64}$'
                        and decision_intent_content_size_bytes between 1 and 5242880
                        and (
                            (
                                decision_intent_status = 'HUMAN_VALIDATED'
                                and decision_intent_reason is null
                            )
                            or (
                                decision_intent_status = 'REJECTED'
                                and decision_intent_reason is not null
                                and decision_intent_reason = btrim(decision_intent_reason)
                                and char_length(decision_intent_reason) between 1 and 500
                                and decision_intent_reason !~ '[[:cntrl:]]'
                            )
                        )
                    )
                )
                and export_path =
                    'j7-' || canonical_event_id::text || '-' || export_uuid::text
                    || case validation_status
                        when 'COHERENCE_CHECKED' then '.candidate.json'
                        when 'HUMAN_VALIDATED' then '.validated.json'
                        when 'REJECTED' then '.rejected.json'
                    end
                and (
                    (
                        validation_status = 'COHERENCE_CHECKED'
                        and content_sha256 = candidate_content_sha256
                        and decided_at is null
                        and decision_reason is null
                    )
                    or (
                        validation_status = 'HUMAN_VALIDATED'
                        and decided_at is not null
                        and decided_at >= generated_at
                        and decision_reason is null
                        and decision_intent_status = validation_status
                        and decision_intent_at = decided_at
                        and decision_intent_reason is not distinct from decision_reason
                        and decision_intent_path = export_path
                        and decision_intent_content_sha256 = content_sha256
                        and decision_intent_content_size_bytes = content_size_bytes
                    )
                    or (
                        validation_status = 'REJECTED'
                        and decided_at is not null
                        and decided_at >= generated_at
                        and decision_reason is not null
                        and decision_reason = btrim(decision_reason)
                        and char_length(decision_reason) between 1 and 500
                        and decision_reason !~ '[[:cntrl:]]'
                        and decision_intent_status = validation_status
                        and decision_intent_at = decided_at
                        and decision_intent_reason is not distinct from decision_reason
                        and decision_intent_path = export_path
                        and decision_intent_content_sha256 = content_sha256
                        and decision_intent_content_size_bytes = content_size_bytes
                    )
                )
            )
        );

create unique index uq_export_manifest_j7_export_uuid
    on export_manifest (export_uuid)
    where export_kind = 'J7_CANONICAL_EVENT';

create unique index uq_export_manifest_j7_pending
    on export_manifest (canonical_event_id, schema_id, schema_version)
    where export_kind = 'J7_CANONICAL_EVENT'
      and validation_status = 'COHERENCE_CHECKED';

create unique index uq_export_manifest_j7_validated_data
    on export_manifest (data_sha256)
    where export_kind = 'J7_CANONICAL_EVENT'
      and validation_status = 'HUMAN_VALIDATED';

create index ix_export_manifest_j7_event_created
    on export_manifest (canonical_event_id, created_at desc, id desc)
    where export_kind = 'J7_CANONICAL_EVENT';

create function guard_j7_export_manifest_mutation()
returns trigger
language plpgsql
as $$
declare
    expected_snapshot_ids bigint[];
begin
    if tg_op = 'DELETE' then
        if old.export_kind = 'J7_CANONICAL_EVENT' then
            raise exception 'J7 export manifests cannot be deleted';
        end if;
        return old;
    end if;

    if tg_op = 'INSERT' then
        if new.export_kind = 'J7_CANONICAL_EVENT' then
            if new.validation_status <> 'COHERENCE_CHECKED' then
                raise exception 'J7 export manifests must be inserted as candidates';
            end if;

            select coalesce(
                    array_agg(snapshot_id order by snapshot_id),
                    '{}'::bigint[])
            into expected_snapshot_ids
            from (
                select distinct (source.value ->> 'snapshotId')::bigint as snapshot_id
                from jsonb_array_elements(new.source_observations) as source(value)
                where source.value ->> 'snapshotId' is not null
            ) source_snapshots;

            if new.source_snapshot_ids is distinct from expected_snapshot_ids then
                raise exception 'J7 source snapshot identifiers are inconsistent';
            end if;
            if exists (
                select 1
                from unnest(expected_snapshot_ids) as snapshot_id
                where snapshot_id < 1
            ) then
                raise exception 'J7 source snapshot identifiers must be positive';
            end if;
            if exists (
                select 1
                from unnest(expected_snapshot_ids) as source_snapshot(snapshot_id)
                left join provider_snapshot snapshot
                  on snapshot.id = source_snapshot.snapshot_id
                where snapshot.id is null
            ) then
                raise exception 'J7 source snapshot identifiers must reference local snapshots';
            end if;
        end if;
        return new;
    end if;

    if old.export_kind is distinct from new.export_kind
       and (
            old.export_kind = 'J7_CANONICAL_EVENT'
            or new.export_kind = 'J7_CANONICAL_EVENT'
       ) then
        raise exception 'J7 export kind is immutable';
    end if;

    if old.export_kind is distinct from 'J7_CANONICAL_EVENT' then
        return new;
    end if;

    if new is not distinct from old then
        return new;
    end if;

    if new.id is distinct from old.id
       or new.export_kind is distinct from old.export_kind
       or new.export_uuid is distinct from old.export_uuid
       or new.canonical_event_id is distinct from old.canonical_event_id
       or new.schema_id is distinct from old.schema_id
       or new.schema_version is distinct from old.schema_version
       or new.generated_at is distinct from old.generated_at
       or new.data_sha256 is distinct from old.data_sha256
       or new.source_set_sha256 is distinct from old.source_set_sha256
       or new.candidate_content_sha256 is distinct from old.candidate_content_sha256
       or new.created_at is distinct from old.created_at
       or new.source_observations is distinct from old.source_observations
       or new.source_snapshot_ids is distinct from old.source_snapshot_ids
       or new.warnings is distinct from old.warnings then
        raise exception 'J7 export immutable evidence cannot be changed';
    end if;

    if old.validation_status <> 'COHERENCE_CHECKED' then
        raise exception 'J7 export terminal transition is not allowed';
    end if;

    if new.validation_status = 'COHERENCE_CHECKED' then
        if new.export_path is distinct from old.export_path
           or new.content_sha256 is distinct from old.content_sha256
           or new.content_size_bytes is distinct from old.content_size_bytes
           or new.decided_at is distinct from old.decided_at
           or new.decision_reason is distinct from old.decision_reason then
            raise exception 'J7 candidate state cannot change while recording decision intent';
        end if;
        if (old.decision_intent_status is null
                and new.decision_intent_status is not null)
           or (old.decision_intent_status is not null
                and new.decision_intent_status is null) then
            return new;
        end if;
        raise exception 'J7 decision intent transition is not allowed';
    end if;

    if new.validation_status not in ('HUMAN_VALIDATED', 'REJECTED')
       or old.decision_intent_status is null
       or new.decision_intent_status is distinct from old.decision_intent_status
       or new.decision_intent_at is distinct from old.decision_intent_at
       or new.decision_intent_reason is distinct from old.decision_intent_reason
       or new.decision_intent_path is distinct from old.decision_intent_path
       or new.decision_intent_content_sha256
            is distinct from old.decision_intent_content_sha256
       or new.decision_intent_content_size_bytes
            is distinct from old.decision_intent_content_size_bytes
       or new.validation_status is distinct from old.decision_intent_status
       or new.decided_at is distinct from old.decision_intent_at
       or new.decision_reason is distinct from old.decision_intent_reason
       or new.export_path is distinct from old.decision_intent_path
       or new.content_sha256 is distinct from old.decision_intent_content_sha256
       or new.content_size_bytes
            is distinct from old.decision_intent_content_size_bytes then
        raise exception 'J7 export terminal transition is not allowed';
    end if;

    return new;
end;
$$;

create trigger export_manifest_j7_guard
before insert or update or delete on export_manifest
for each row execute function guard_j7_export_manifest_mutation();

create function lock_j7_canonical_event_source_write()
returns trigger
language plpgsql
as $$
declare
    event_id uuid;
begin
    event_id := case when tg_op = 'DELETE'
        then old.canonical_event_id
        else new.canonical_event_id
    end;
    perform pg_advisory_xact_lock(hashtextextended(event_id::text, 7007));
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

create trigger canonical_event_observation_j7_source_lock
before insert or update or delete on canonical_event_observation
for each row execute function lock_j7_canonical_event_source_write();

create trigger event_detail_observation_j7_source_lock
before insert or update or delete on event_detail_observation
for each row execute function lock_j7_canonical_event_source_write();

create trigger j5_event_data_observation_j7_source_lock
before insert or update or delete on j5_event_data_observation
for each row execute function lock_j7_canonical_event_source_write();

create function lock_j7_j5_child_source_write()
returns trigger
language plpgsql
as $$
declare
    event_id uuid;
begin
    select observation.canonical_event_id
    into event_id
    from j5_event_data_observation observation
    where observation.id = new.observation_id;

    if event_id is null then
        raise exception 'J5 child source parent observation must be visible';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(event_id::text, 7007));
    return new;
end;
$$;

create trigger j5_event_metric_j7_source_lock
before insert on j5_event_metric
for each row execute function lock_j7_j5_child_source_write();

create trigger j5_event_incident_j7_source_lock
before insert on j5_event_incident
for each row execute function lock_j7_j5_child_source_write();

create trigger j5_event_lineup_side_j7_source_lock
before insert on j5_event_lineup_side
for each row execute function lock_j7_j5_child_source_write();

create trigger j5_event_lineup_player_j7_source_lock
before insert on j5_event_lineup_player
for each row execute function lock_j7_j5_child_source_write();

create function lock_j7_provider_snapshot_source_write()
returns trigger
language plpgsql
as $$
declare
    referenced_event_id uuid;
begin
    for referenced_event_id in
        select source_event.canonical_event_id
        from (
            select observation.canonical_event_id
            from canonical_event_observation observation
            where observation.source_snapshot_id = old.id
            union
            select observation.canonical_event_id
            from event_detail_observation observation
            where observation.source_snapshot_id = old.id
            union
            select observation.canonical_event_id
            from j5_event_data_observation observation
            where observation.source_snapshot_id = old.id
        ) source_event
        order by source_event.canonical_event_id
    loop
        perform pg_advisory_xact_lock(
                hashtextextended(referenced_event_id::text, 7007));
    end loop;
    return case when tg_op = 'DELETE' then old else new end;
end;
$$;

create trigger provider_snapshot_j7_source_lock
before update or delete on provider_snapshot
for each row execute function lock_j7_provider_snapshot_source_write();

comment on column export_manifest.export_kind is
    'Null for legacy generic rows; J7_CANONICAL_EVENT identifies the guarded J7 lifecycle.';
comment on column export_manifest.candidate_content_sha256 is
    'Immutable SHA-256 of the initial COHERENCE_CHECKED candidate file.';
comment on column export_manifest.content_sha256 is
    'SHA-256 of the file currently referenced by export_path.';
comment on column export_manifest.source_observations is
    'Ordered J7 metadata only: EVENT_STATE, EVENT_DETAILS, statistics, incidents and lineups.';
comment on column export_manifest.decision_intent_content_sha256 is
    'Write-ahead expected terminal SHA-256 used to authenticate exact retry recovery.';
comment on table export_manifest is
    'Generic local export registry extended by V23 with guarded, non-deletable J7 rows.';
