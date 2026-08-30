create table j8_benchmark_campaign (
    campaign_id uuid primary key,
    campaign_type varchar(48) not null,
    execution_mode varchar(32) not null,
    started_at timestamptz not null,
    maximum_units smallint not null,
    collection_date date,
    created_at timestamptz not null default current_timestamp,
    constraint ck_j8_benchmark_campaign_type
        check (campaign_type in (
            'J3_SCHEDULED_EVENTS',
            'J3_TOURNAMENT_DISCOVERY',
            'J4_EVENT_DETAILS_PHASE1',
            'J4_EVENT_DETAILS_PHASE2',
            'J5_EVENT_DATA'
        )),
    constraint ck_j8_benchmark_campaign_execution_mode
        check (execution_mode in (
            'GUARDED_PROVIDER',
            'MANUAL_LOCAL_JSON_IMPORT'
        )),
    constraint ck_j8_benchmark_campaign_bound
        check (
            (campaign_type = 'J3_SCHEDULED_EVENTS' and maximum_units = 25)
            or (campaign_type = 'J3_TOURNAMENT_DISCOVERY' and maximum_units = 1)
            or (campaign_type = 'J4_EVENT_DETAILS_PHASE1' and maximum_units = 2)
            or (campaign_type = 'J4_EVENT_DETAILS_PHASE2' and maximum_units = 1)
            or (campaign_type = 'J5_EVENT_DATA' and maximum_units = 3)
        ),
    constraint ck_j8_benchmark_campaign_collection_date
        check (
            (
                campaign_type in (
                    'J3_SCHEDULED_EVENTS',
                    'J3_TOURNAMENT_DISCOVERY'
                )
                and collection_date is not null
            )
            or (
                campaign_type not in (
                    'J3_SCHEDULED_EVENTS',
                    'J3_TOURNAMENT_DISCOVERY'
                )
                and collection_date is null
            )
        ),
    constraint ck_j8_benchmark_campaign_import_scope
        check (
            execution_mode = 'GUARDED_PROVIDER'
            or campaign_type in (
                'J3_SCHEDULED_EVENTS',
                'J3_TOURNAMENT_DISCOVERY',
                'J5_EVENT_DATA'
            )
        )
);

create table j8_benchmark_unit (
    id bigint generated always as identity primary key,
    campaign_id uuid not null,
    unit_ordinal smallint not null,
    logical_endpoint varchar(32) not null,
    request_key varchar(512) not null,
    canonical_event_id uuid,
    provider_event_id bigint,
    declared_at timestamptz not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_j8_benchmark_unit_campaign
        foreign key (campaign_id)
        references j8_benchmark_campaign (campaign_id)
        on delete restrict,
    constraint fk_j8_benchmark_unit_event
        foreign key (canonical_event_id)
        references canonical_event (id)
        on delete restrict,
    constraint uq_j8_benchmark_unit_campaign_ordinal
        unique (campaign_id, unit_ordinal),
    constraint uq_j8_benchmark_unit_campaign_request
        unique (campaign_id, request_key),
    constraint ck_j8_benchmark_unit_ordinal
        check (unit_ordinal > 0),
    constraint ck_j8_benchmark_unit_endpoint
        check (logical_endpoint in (
            'SCHEDULED_EVENTS',
            'TOURNAMENT_SCHEDULED_EVENTS',
            'EVENT_DETAILS',
            'EVENT_STATISTICS',
            'EVENT_INCIDENTS',
            'EVENT_LINEUPS'
        )),
    constraint ck_j8_benchmark_unit_request_key
        check (
            request_key = btrim(request_key)
            and request_key <> ''
            and request_key !~ '[[:cntrl:]]'
        ),
    constraint ck_j8_benchmark_unit_canonical_request_key
        check (
            (
                logical_endpoint in (
                    'EVENT_DETAILS',
                    'EVENT_STATISTICS',
                    'EVENT_INCIDENTS',
                    'EVENT_LINEUPS'
                )
                and request_key = logical_endpoint || '|eventId='
                        || provider_event_id::text
            )
            or (
                logical_endpoint = 'SCHEDULED_EVENTS'
                and request_key ~ '^SCHEDULED_EVENTS[|]date=[0-9]{4}-[0-9]{2}-[0-9]{2}[|]page=([1-9]|1[0-9]|2[0-5])$'
            )
            or (
                logical_endpoint = 'TOURNAMENT_SCHEDULED_EVENTS'
                and request_key ~ '^TOURNAMENT_SCHEDULED_EVENTS[|]date=[0-9]{4}-[0-9]{2}-[0-9]{2}[|]uniqueTournamentId=[1-9][0-9]{0,18}$'
            )
        ),
    constraint ck_j8_benchmark_unit_event_correlation
        check (
            (
                logical_endpoint in (
                    'EVENT_DETAILS',
                    'EVENT_STATISTICS',
                    'EVENT_INCIDENTS',
                    'EVENT_LINEUPS'
                )
                and provider_event_id is not null
                and provider_event_id between 1 and 999999999
            )
            or (
                logical_endpoint in (
                    'SCHEDULED_EVENTS',
                    'TOURNAMENT_SCHEDULED_EVENTS'
                )
                and canonical_event_id is null
                and provider_event_id is null
            )
        )
);

create table j8_provider_call_attempt (
    id bigint generated always as identity primary key,
    unit_id bigint not null,
    started_at timestamptz not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_j8_provider_call_attempt_unit
        foreign key (unit_id)
        references j8_benchmark_unit (id)
        on delete restrict,
    constraint uq_j8_provider_call_attempt_unit unique (unit_id),
    constraint uq_j8_provider_call_attempt_id_unit unique (id, unit_id)
);

alter table provider_snapshot_occurrence
    add constraint uq_provider_snapshot_occurrence_id_snapshot
        unique (id, snapshot_id);

create table j8_benchmark_unit_result (
    unit_id bigint primary key,
    attempt_id bigint,
    resolved_at timestamptz not null,
    resolution_source varchar(32) not null,
    outcome_type varchar(48) not null,
    response_received boolean not null,
    http_status integer,
    latency_ms bigint,
    snapshot_id bigint,
    snapshot_occurrence_id bigint,
    parser_version varchar(32),
    schema_status varchar(32),
    parser_warning_count integer not null default 0,
    completeness_status varchar(32),
    completeness_score smallint,
    terminal_code varchar(96),
    created_at timestamptz not null default current_timestamp,
    constraint fk_j8_benchmark_unit_result_unit
        foreign key (unit_id)
        references j8_benchmark_unit (id)
        on delete restrict,
    constraint fk_j8_benchmark_unit_result_attempt_unit
        foreign key (attempt_id, unit_id)
        references j8_provider_call_attempt (id, unit_id)
        on delete restrict,
    constraint fk_j8_benchmark_unit_result_snapshot
        foreign key (snapshot_id)
        references provider_snapshot (id)
        on delete restrict,
    constraint fk_j8_benchmark_unit_result_occurrence_snapshot
        foreign key (snapshot_occurrence_id, snapshot_id)
        references provider_snapshot_occurrence (id, snapshot_id)
        on delete restrict,
    constraint uq_j8_benchmark_unit_result_snapshot_occurrence
        unique (snapshot_occurrence_id),
    constraint ck_j8_benchmark_unit_result_source
        check (resolution_source in (
            'PROVIDER',
            'CACHE',
            'MANUAL_LOCAL_JSON_IMPORT',
            'BLOCKED'
        )),
    constraint ck_j8_benchmark_unit_result_outcome
        check (outcome_type in (
            'PARSED',
            'ENDPOINT_UNAVAILABLE',
            'HTTP_REFUSED',
            'HTTP_ERROR',
            'SCHEMA_INCOMPATIBLE',
            'UNEXPECTED_CONTENT',
            'TRANSPORT_FAILURE',
            'PERSISTENCE_FAILURE',
            'PROCESSING_FAILURE',
            'OPERATOR_STOP',
            'NOT_REACHED_AFTER_TERMINAL_FAILURE'
        )),
    constraint ck_j8_benchmark_unit_result_http_status
        check (http_status is null or http_status between 100 and 599),
    constraint ck_j8_benchmark_unit_result_latency
        check (latency_ms is null or latency_ms >= 0),
    constraint ck_j8_benchmark_unit_result_parser
        check (
            parser_version is null
            or parser_version ~ '^[A-Za-z0-9._-]+$'
        ),
    constraint ck_j8_benchmark_unit_result_schema
        check (
            schema_status is null
            or schema_status in (
                'RAW_ONLY',
                'PARSED',
                'ENDPOINT_UNAVAILABLE',
                'SCHEMA_INCOMPATIBLE',
                'UNEXPECTED_CONTENT',
                'TRANSPORT_ERROR'
            )
        ),
    constraint ck_j8_benchmark_unit_result_warning_count
        check (parser_warning_count >= 0),
    constraint ck_j8_benchmark_unit_result_parser_evidence
        check (
            (
                snapshot_id is not null
                or (
                    parser_version is null
                    and schema_status is null
                    and parser_warning_count = 0
                )
            )
            and (parser_warning_count = 0 or parser_version is not null)
        ),
    constraint ck_j8_benchmark_unit_result_completeness
        check (
            (
                completeness_status is null
                and completeness_score is null
            )
            or (
                completeness_status is not null
                and completeness_score is not null
                and completeness_status in (
                    'COMPLETE',
                    'PARTIAL',
                    'EMPTY_VALID',
                    'UNAVAILABLE'
                )
                and completeness_score between 0 and 100
                and (
                    (completeness_status = 'COMPLETE' and completeness_score = 100)
                    or (completeness_status = 'PARTIAL' and completeness_score < 100)
                    or (completeness_status = 'EMPTY_VALID' and completeness_score = 100)
                    or (completeness_status = 'UNAVAILABLE' and completeness_score = 0)
                )
            )
        ),
    constraint ck_j8_benchmark_unit_result_terminal_code
        check (
            terminal_code is null
            or terminal_code ~ '^[A-Z0-9_]+$'
        ),
    constraint ck_j8_benchmark_unit_result_response_shape
        check (
            (
                response_received
                and resolution_source = 'PROVIDER'
                and attempt_id is not null
                and http_status is not null
                and latency_ms is not null
            )
            or (
                not response_received
                and http_status is null
                and latency_ms is null
            )
        ),
    constraint ck_j8_benchmark_unit_result_attempt_source
        check (
            (resolution_source = 'PROVIDER' and attempt_id is not null)
            or (resolution_source <> 'PROVIDER' and attempt_id is null)
        ),
    constraint ck_j8_benchmark_unit_result_snapshot_shape
        check (
            (snapshot_occurrence_id is null or snapshot_id is not null)
            and (
                resolution_source <> 'PROVIDER'
                or snapshot_id is null
                or (
                    snapshot_occurrence_id is not null
                    and response_received
                )
            )
        ),
    constraint ck_j8_benchmark_unit_result_cache_shape
        check (
            resolution_source <> 'CACHE'
            or (
                snapshot_id is not null
                and snapshot_occurrence_id is null
                and not response_received
            )
        ),
    constraint ck_j8_benchmark_unit_result_import_shape
        check (
            resolution_source <> 'MANUAL_LOCAL_JSON_IMPORT'
            or (
                not response_received
                and (
                    (
                        snapshot_id is not null
                        and snapshot_occurrence_id is not null
                    )
                    or (
                        outcome_type = 'PERSISTENCE_FAILURE'
                        and snapshot_id is null
                        and snapshot_occurrence_id is null
                        and parser_version is null
                        and schema_status is null
                    )
                )
            )
        ),
    constraint ck_j8_benchmark_unit_result_blocked_shape
        check (
            resolution_source <> 'BLOCKED'
            or (
                outcome_type in (
                    'OPERATOR_STOP',
                    'NOT_REACHED_AFTER_TERMINAL_FAILURE',
                    'PROCESSING_FAILURE'
                )
                and not response_received
                and snapshot_id is null
                and snapshot_occurrence_id is null
                and parser_version is null
                and schema_status is null
                and parser_warning_count = 0
                and completeness_status is null
                and completeness_score is null
            )
        ),
    constraint ck_j8_benchmark_unit_result_transport_shape
        check (
            outcome_type <> 'TRANSPORT_FAILURE'
            or (
                resolution_source = 'PROVIDER'
                and not response_received
                and snapshot_id is null
                and snapshot_occurrence_id is null
                and schema_status is null
            )
        ),
    constraint ck_j8_benchmark_unit_result_http_refused
        check (
            outcome_type <> 'HTTP_REFUSED'
            or (
                response_received
                and http_status in (401, 403, 429)
            )
        ),
    constraint ck_j8_benchmark_unit_result_http_error
        check (
            outcome_type <> 'HTTP_ERROR'
            or (
                response_received
                and (http_status < 200 or http_status >= 300)
                and http_status not in (401, 403, 404, 429)
            )
        ),
    constraint ck_j8_benchmark_unit_result_endpoint_unavailable
        check (
            outcome_type <> 'ENDPOINT_UNAVAILABLE'
            or resolution_source <> 'PROVIDER'
            or (
                response_received
                and http_status = 404
            )
        ),
    constraint ck_j8_benchmark_unit_result_not_reached
        check (
            outcome_type <> 'NOT_REACHED_AFTER_TERMINAL_FAILURE'
            or resolution_source = 'BLOCKED'
        ),
    constraint ck_j8_benchmark_unit_result_parser_outcome
        check (
            (
                outcome_type not in (
                    'PARSED',
                    'ENDPOINT_UNAVAILABLE',
                    'SCHEMA_INCOMPATIBLE',
                    'UNEXPECTED_CONTENT'
                )
                or (
                    snapshot_id is not null
                    and parser_version is not null
                    and (resolution_source <> 'PROVIDER' or response_received)
                )
            )
            and
            (
                outcome_type <> 'PARSED'
                or (
                    schema_status is not null
                    and schema_status = 'PARSED'
                )
            )
            and (
                outcome_type <> 'ENDPOINT_UNAVAILABLE'
                or (
                    schema_status is not null
                    and schema_status = 'ENDPOINT_UNAVAILABLE'
                )
            )
            and (
                outcome_type <> 'SCHEMA_INCOMPATIBLE'
                or (
                    schema_status is not null
                    and schema_status = 'SCHEMA_INCOMPATIBLE'
                )
            )
            and (
                outcome_type <> 'UNEXPECTED_CONTENT'
                or (
                    schema_status is not null
                    and schema_status = 'UNEXPECTED_CONTENT'
                )
            )
            and (
                resolution_source <> 'PROVIDER'
                or outcome_type not in (
                    'PARSED',
                    'SCHEMA_INCOMPATIBLE',
                    'UNEXPECTED_CONTENT'
                )
                or http_status between 200 and 299
            )
        )
);

create table j8_benchmark_campaign_result (
    campaign_id uuid primary key,
    finished_at timestamptz not null,
    terminal_state varchar(16) not null,
    terminal_code varchar(96),
    completed_units smallint not null,
    created_at timestamptz not null default current_timestamp,
    constraint fk_j8_benchmark_campaign_result_campaign
        foreign key (campaign_id)
        references j8_benchmark_campaign (campaign_id)
        on delete restrict,
    constraint ck_j8_benchmark_campaign_result_state
        check (terminal_state in ('COMPLETED', 'FAILED', 'CANCELLED')),
    constraint ck_j8_benchmark_campaign_result_terminal_code
        check (
            terminal_code is null
            or terminal_code ~ '^[A-Z0-9_]+$'
        ),
    constraint ck_j8_benchmark_campaign_result_failure_code
        check (
            terminal_state = 'COMPLETED'
            or terminal_code is not null
        ),
    constraint ck_j8_benchmark_campaign_result_completed_units
        check (completed_units >= 0)
);

create function validate_j8_benchmark_unit_insert()
returns trigger
language plpgsql
as $$
declare
    campaign j8_benchmark_campaign%rowtype;
    tournament_id_text text;
begin
    select *
    into campaign
    from j8_benchmark_campaign
    where campaign_id = new.campaign_id
    for update;

    if not found then
        raise exception 'J8 benchmark campaign does not exist';
    end if;
    if exists (
        select 1
        from j8_benchmark_campaign_result
        where campaign_id = new.campaign_id
    ) then
        raise exception 'J8 benchmark campaign is already finished';
    end if;
    if new.declared_at < campaign.started_at then
        raise exception 'J8 benchmark unit cannot predate its campaign';
    end if;
    if new.unit_ordinal > campaign.maximum_units then
        raise exception 'J8 benchmark unit exceeds its bounded campaign';
    end if;

    if (campaign.campaign_type = 'J3_SCHEDULED_EVENTS'
            and new.logical_endpoint <> 'SCHEDULED_EVENTS')
       or (campaign.campaign_type = 'J3_TOURNAMENT_DISCOVERY'
            and new.logical_endpoint <> 'TOURNAMENT_SCHEDULED_EVENTS')
       or (campaign.campaign_type in (
                'J4_EVENT_DETAILS_PHASE1',
                'J4_EVENT_DETAILS_PHASE2'
            )
            and new.logical_endpoint <> 'EVENT_DETAILS')
       or (campaign.campaign_type = 'J5_EVENT_DATA'
            and new.logical_endpoint not in (
                'EVENT_STATISTICS',
                'EVENT_INCIDENTS',
                'EVENT_LINEUPS'
            )) then
        raise exception 'J8 benchmark endpoint is outside its campaign scope';
    end if;

    if campaign.campaign_type = 'J3_SCHEDULED_EVENTS'
       and new.request_key <> (
                'SCHEDULED_EVENTS|date='
                || campaign.collection_date::text
                || '|page=' || new.unit_ordinal::text
       ) then
        raise exception 'J8 scheduled-events key must match campaign date and ordinal';
    end if;
    if campaign.campaign_type = 'J3_TOURNAMENT_DISCOVERY' then
        if new.request_key !~ (
                '^TOURNAMENT_SCHEDULED_EVENTS[|]date='
                || campaign.collection_date::text
                || '[|]uniqueTournamentId=[1-9][0-9]{0,18}$'
        ) then
            raise exception 'J8 tournament key must match campaign date and numeric id';
        end if;
        tournament_id_text := substring(
                new.request_key from 'uniqueTournamentId=([1-9][0-9]*)$');
        if tournament_id_text::numeric > 9223372036854775807 then
            raise exception 'J8 tournament id exceeds signed bigint';
        end if;
    end if;

    if campaign.campaign_type = 'J4_EVENT_DETAILS_PHASE1' then
        if new.provider_event_id not in (16386245, 16421052) then
            raise exception 'J8 phase-1 event target is outside its fixed allowlist';
        end if;
        if exists (
            select 1
            from j8_benchmark_unit existing
            where existing.campaign_id = new.campaign_id
              and existing.provider_event_id = new.provider_event_id
        ) then
            raise exception 'J8 phase-1 event targets must be distinct';
        end if;
    end if;

    if campaign.campaign_type = 'J5_EVENT_DATA' then
        if (new.unit_ordinal = 1 and new.logical_endpoint <> 'EVENT_STATISTICS')
           or (new.unit_ordinal = 2 and new.logical_endpoint <> 'EVENT_INCIDENTS')
           or (new.unit_ordinal = 3 and new.logical_endpoint <> 'EVENT_LINEUPS')
           or new.unit_ordinal not between 1 and 3 then
            raise exception 'J8 J5 ordinals have fixed endpoint families';
        end if;
        if exists (
            select 1
            from j8_benchmark_unit existing
            where existing.campaign_id = new.campaign_id
              and (
                    existing.provider_event_id is distinct from new.provider_event_id
                    or existing.canonical_event_id is distinct from new.canonical_event_id
              )
        ) then
            raise exception 'J8 J5 units must target the same canonical event';
        end if;
    end if;

    if new.canonical_event_id is not null
       and not exists (
            select 1
            from canonical_event event
            where event.id = new.canonical_event_id
              and event.provider = 'SOFASCORE'
              and event.provider_event_id = new.provider_event_id
       ) then
        raise exception 'J8 benchmark canonical/provider event correlation is inconsistent';
    end if;

    return new;
end;
$$;

create trigger j8_benchmark_unit_validate_insert
before insert on j8_benchmark_unit
for each row execute function validate_j8_benchmark_unit_insert();

create function validate_j8_provider_call_attempt_insert()
returns trigger
language plpgsql
as $$
declare
    unit_declared_at timestamptz;
    unit_campaign_id uuid;
    campaign_mode varchar(32);
begin
    select unit.declared_at, unit.campaign_id, campaign.execution_mode
    into unit_declared_at, unit_campaign_id, campaign_mode
    from j8_benchmark_unit unit
    join j8_benchmark_campaign campaign
      on campaign.campaign_id = unit.campaign_id
    where unit.id = new.unit_id
    for update of campaign;

    if not found then
        raise exception 'J8 benchmark unit does not exist';
    end if;
    if campaign_mode <> 'GUARDED_PROVIDER' then
        raise exception 'J8 provider attempts are forbidden for local imports';
    end if;
    if new.started_at < unit_declared_at then
        raise exception 'J8 provider attempt cannot predate its unit';
    end if;
    if exists (
        select 1
        from j8_benchmark_campaign_result
        where campaign_id = unit_campaign_id
    ) then
        raise exception 'J8 provider attempt cannot start after campaign completion';
    end if;
    if exists (
        select 1
        from j8_benchmark_unit_result
        where unit_id = new.unit_id
    ) then
        raise exception 'J8 provider attempt cannot start after unit resolution';
    end if;

    return new;
end;
$$;

create trigger j8_provider_call_attempt_validate_insert
before insert on j8_provider_call_attempt
for each row execute function validate_j8_provider_call_attempt_insert();

create function validate_j8_benchmark_unit_result_insert()
returns trigger
language plpgsql
as $$
declare
    unit_declared_at timestamptz;
    unit_campaign_id uuid;
    unit_endpoint varchar(32);
    unit_request_key varchar(512);
    campaign_type_value varchar(48);
    campaign_mode varchar(32);
    attempt_started_at timestamptz;
    snapshot_provider varchar(32);
    snapshot_acquisition_mode varchar(32);
    snapshot_endpoint varchar(64);
    snapshot_request_key varchar(512);
    occurrence_requested_at timestamptz;
    occurrence_received_at timestamptz;
    occurrence_http_status integer;
    occurrence_latency_ms bigint;
    occurrence_parser_version varchar(32);
    occurrence_outcome varchar(16);
begin
    select
        unit.declared_at,
        unit.campaign_id,
        unit.logical_endpoint,
        unit.request_key,
        campaign.campaign_type,
        campaign.execution_mode
    into
        unit_declared_at,
        unit_campaign_id,
        unit_endpoint,
        unit_request_key,
        campaign_type_value,
        campaign_mode
    from j8_benchmark_unit unit
    join j8_benchmark_campaign campaign
      on campaign.campaign_id = unit.campaign_id
    where unit.id = new.unit_id
    for update of campaign;

    if not found then
        raise exception 'J8 benchmark unit does not exist';
    end if;
    if exists (
        select 1
        from j8_benchmark_campaign_result
        where campaign_id = unit_campaign_id
    ) then
        raise exception 'J8 benchmark unit cannot resolve after campaign completion';
    end if;
    if new.resolved_at < unit_declared_at then
        raise exception 'J8 benchmark result cannot predate its unit';
    end if;

    if new.attempt_id is not null then
        select started_at
        into attempt_started_at
        from j8_provider_call_attempt
        where id = new.attempt_id
          and unit_id = new.unit_id;
        if not found then
            raise exception 'J8 benchmark result attempt does not belong to its unit';
        end if;
        if new.resolved_at < attempt_started_at then
            raise exception 'J8 benchmark result cannot predate its attempt';
        end if;
    end if;
    if new.resolution_source <> 'PROVIDER'
       and exists (
            select 1
            from j8_provider_call_attempt
            where unit_id = new.unit_id
       ) then
        raise exception 'J8 attempted units require a provider result';
    end if;

    if new.snapshot_id is not null then
        select provider, acquisition_mode, logical_endpoint, request_key
        into
            snapshot_provider,
            snapshot_acquisition_mode,
            snapshot_endpoint,
            snapshot_request_key
        from provider_snapshot
        where id = new.snapshot_id;
        if not found then
            raise exception 'J8 benchmark snapshot does not exist';
        end if;
        if snapshot_provider <> 'SOFASCORE'
           or snapshot_endpoint <> unit_endpoint
           or snapshot_request_key <> unit_request_key then
            raise exception 'J8 benchmark snapshot does not match its logical unit';
        end if;
        if new.resolution_source in ('PROVIDER', 'CACHE')
           and snapshot_acquisition_mode <> 'DIRECT_LOCAL_ENDPOINT' then
            raise exception 'J8 provider/cache evidence requires a direct snapshot';
        end if;
        if new.resolution_source = 'MANUAL_LOCAL_JSON_IMPORT'
           and snapshot_acquisition_mode <> 'MANUAL_LOCAL_JSON_IMPORT' then
            raise exception 'J8 import evidence requires an imported snapshot';
        end if;
    end if;

    if new.resolution_source = 'PROVIDER'
       and new.snapshot_id is not null
       and not new.response_received then
        raise exception 'J8 provider snapshots require a received response';
    end if;

    if new.snapshot_occurrence_id is not null then
        select
            requested_at,
            received_at,
            http_status,
            latency_ms,
            parser_version,
            persistence_outcome
        into
            occurrence_requested_at,
            occurrence_received_at,
            occurrence_http_status,
            occurrence_latency_ms,
            occurrence_parser_version,
            occurrence_outcome
        from provider_snapshot_occurrence
        where id = new.snapshot_occurrence_id
          and snapshot_id = new.snapshot_id;
        if not found then
            raise exception 'J8 benchmark occurrence does not belong to its snapshot';
        end if;
        if occurrence_outcome not in ('INSERTED', 'DEDUPLICATED') then
            raise exception 'J8 benchmark evidence requires a prospective occurrence';
        end if;
        if occurrence_received_at is null
           or occurrence_received_at < occurrence_requested_at
           or occurrence_received_at > new.resolved_at then
            raise exception 'J8 benchmark occurrence chronology is inconsistent';
        end if;
        if occurrence_parser_version is distinct from new.parser_version then
            raise exception 'J8 benchmark result parser must match its occurrence';
        end if;
        if new.resolution_source = 'PROVIDER' then
            if attempt_started_at is null
               or occurrence_requested_at < attempt_started_at then
                raise exception 'J8 benchmark provider occurrence cannot predate its attempt';
            end if;
            if occurrence_http_status is distinct from new.http_status
               or occurrence_latency_ms is distinct from new.latency_ms then
                raise exception 'J8 benchmark provider result must match occurrence transport evidence';
            end if;
        end if;
    end if;

    if (campaign_mode = 'GUARDED_PROVIDER'
            and new.resolution_source not in ('PROVIDER', 'CACHE', 'BLOCKED'))
       or (campaign_mode = 'MANUAL_LOCAL_JSON_IMPORT'
            and new.resolution_source not in (
                'MANUAL_LOCAL_JSON_IMPORT',
                'BLOCKED'
            )) then
        raise exception 'J8 benchmark result source is inconsistent with campaign mode';
    end if;

    if campaign_type_value = 'J5_EVENT_DATA' then
        if new.outcome_type in ('PARSED', 'ENDPOINT_UNAVAILABLE')
           and (new.completeness_status is null or new.completeness_score is null) then
            raise exception 'J8 parsed J5 results require completeness evidence';
        end if;
        if new.outcome_type not in ('PARSED', 'ENDPOINT_UNAVAILABLE')
           and (new.completeness_status is not null or new.completeness_score is not null) then
            raise exception 'J8 unparsed J5 results cannot claim completeness';
        end if;
    elsif new.completeness_status is not null or new.completeness_score is not null then
        raise exception 'J8 completeness evidence is restricted to J5';
    end if;

    return new;
end;
$$;

create trigger j8_benchmark_unit_result_validate_insert
before insert on j8_benchmark_unit_result
for each row execute function validate_j8_benchmark_unit_result_insert();

create function validate_j8_benchmark_campaign_result_insert()
returns trigger
language plpgsql
as $$
declare
    campaign_started_at timestamptz;
    campaign_maximum_units smallint;
    declared_units bigint;
    resolved_units bigint;
    latest_resolved_at timestamptz;
begin
    select started_at, maximum_units
    into campaign_started_at, campaign_maximum_units
    from j8_benchmark_campaign
    where campaign_id = new.campaign_id
    for update;

    if not found then
        raise exception 'J8 benchmark campaign does not exist';
    end if;
    if new.finished_at < campaign_started_at then
        raise exception 'J8 benchmark campaign result cannot predate its campaign';
    end if;
    if new.completed_units > campaign_maximum_units then
        raise exception 'J8 benchmark completed unit count exceeds campaign bound';
    end if;

    select count(*)
    into declared_units
    from j8_benchmark_unit
    where campaign_id = new.campaign_id;

    select count(*), max(result.resolved_at)
    into resolved_units, latest_resolved_at
    from j8_benchmark_unit unit
    join j8_benchmark_unit_result result
      on result.unit_id = unit.id
    where unit.campaign_id = new.campaign_id;

    if declared_units <> resolved_units
       or resolved_units <> new.completed_units then
        raise exception 'J8 benchmark campaign completion requires every declared unit result';
    end if;
    if latest_resolved_at is not null
       and new.finished_at < latest_resolved_at then
        raise exception 'J8 benchmark campaign cannot finish before its latest unit result';
    end if;

    return new;
end;
$$;

create trigger j8_benchmark_campaign_result_validate_insert
before insert on j8_benchmark_campaign_result
for each row execute function validate_j8_benchmark_campaign_result_insert();

create function reject_j8_benchmark_evidence_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception '% is append-only', tg_table_name;
end;
$$;

create trigger j8_benchmark_campaign_append_only
before update or delete on j8_benchmark_campaign
for each row execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_campaign_reject_truncate
before truncate on j8_benchmark_campaign
for each statement execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_unit_append_only
before update or delete on j8_benchmark_unit
for each row execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_unit_reject_truncate
before truncate on j8_benchmark_unit
for each statement execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_provider_call_attempt_append_only
before update or delete on j8_provider_call_attempt
for each row execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_provider_call_attempt_reject_truncate
before truncate on j8_provider_call_attempt
for each statement execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_unit_result_append_only
before update or delete on j8_benchmark_unit_result
for each row execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_unit_result_reject_truncate
before truncate on j8_benchmark_unit_result
for each statement execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_campaign_result_append_only
before update or delete on j8_benchmark_campaign_result
for each row execute function reject_j8_benchmark_evidence_mutation();

create trigger j8_benchmark_campaign_result_reject_truncate
before truncate on j8_benchmark_campaign_result
for each statement execute function reject_j8_benchmark_evidence_mutation();

create index ix_j8_benchmark_campaign_started
    on j8_benchmark_campaign (started_at, campaign_id);

create index ix_j8_benchmark_unit_campaign
    on j8_benchmark_unit (campaign_id, unit_ordinal, id);

create index ix_j8_benchmark_unit_provider_event
    on j8_benchmark_unit (provider_event_id, declared_at, id)
    where provider_event_id is not null;

create index ix_j8_benchmark_unit_endpoint
    on j8_benchmark_unit (logical_endpoint, declared_at, id);

create index ix_j8_provider_call_attempt_started
    on j8_provider_call_attempt (started_at, id);

create index ix_j8_benchmark_unit_result_resolved
    on j8_benchmark_unit_result (resolved_at, unit_id);

create index ix_j8_benchmark_unit_result_outcome
    on j8_benchmark_unit_result (outcome_type, resolved_at, unit_id);

create index ix_j8_benchmark_campaign_result_finished
    on j8_benchmark_campaign_result (finished_at, campaign_id);

create index ix_provider_snapshot_occurrence_j8_requested
    on provider_snapshot_occurrence (requested_at, id);

create index ix_canonical_event_observation_j8_received
    on canonical_event_observation (source_received_at, canonical_event_id, id);

create index ix_event_detail_observation_j8_received
    on event_detail_observation (source_received_at, canonical_event_id, id);

create index ix_j5_event_data_observation_j8_received
    on j5_event_data_observation (
        source_received_at,
        endpoint_type,
        canonical_event_id,
        id
    );

comment on table j8_benchmark_campaign is
    'J8 append-only boundary for a manually authorized, bounded J3/J4/J5 campaign.';
comment on table j8_benchmark_unit is
    'J8 append-only logical page or endpoint-family target; declaration does not imply a provider call.';
comment on table j8_provider_call_attempt is
    'J8 exact provider-call ledger. One row is inserted immediately before transport execution; no retry is permitted per unit.';
comment on table j8_benchmark_unit_result is
    'J8 append-only terminal unit evidence. Provider responses, cache hits, imports and blocked units remain distinct.';
comment on table j8_benchmark_campaign_result is
    'J8 append-only campaign terminal evidence; every declared unit must already have one terminal result.';
comment on column j8_benchmark_unit_result.response_received is
    'True only for a direct provider response; cache and manual-import resolution never contribute response latency.';
comment on column j8_benchmark_unit_result.schema_status is
    'Parser/schema outcome for this execution, independent from the immutable classification of a deduplicated raw snapshot.';
