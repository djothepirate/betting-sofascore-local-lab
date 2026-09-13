-- WO-058: bounded local diagnostics, separate from snapshots, receipts and normalized data.
-- Older campaigns remain unknown; no retrospective diagnostic or provider receipt is fabricated.
create table live_attempt_transport_diagnostic (
    attempt_id uuid primary key references live_call(attempt_id) on delete restrict,
    campaign_id uuid not null references live_campaign(campaign_id) on delete restrict,
    endpoint_type varchar(32) not null check(endpoint_type in ('EVENT_DETAILS','EVENT_INCIDENTS','EVENT_STATISTICS','EVENT_LINEUPS')),
    transport_phase varchar(32) not null check(transport_phase in
        ('NAVIGATION','REQUEST_SENT','HEADERS_RECEIVED','READING_BODY','COMPLETE','PARENT_IPC_WAIT')),
    timeout_ms integer not null check(timeout_ms between 1 and 60000),
    requested_at timestamptz,
    headers_received_at timestamptz,
    http_status integer check(http_status between 100 and 599),
    retry_not_before timestamptz,
    response_complete boolean not null,
    check((headers_received_at is null) = (http_status is null)),
    check(headers_received_at is null or (requested_at is not null and headers_received_at>=requested_at)),
    check(retry_not_before is null or (headers_received_at is not null and retry_not_before>=headers_received_at)),
    check(response_complete = (transport_phase='COMPLETE')),
    check(not response_complete or headers_received_at is not null),
    check(transport_phase<>'NAVIGATION' or requested_at is null),
    check(transport_phase<>'REQUEST_SENT' or (requested_at is not null and headers_received_at is null)),
    check(transport_phase not in ('HEADERS_RECEIVED','READING_BODY') or headers_received_at is not null)
);
create index live_attempt_transport_campaign on live_attempt_transport_diagnostic(campaign_id);

create table live_campaign_diagnostic (
    campaign_id uuid not null references live_campaign(campaign_id) on delete restrict,
    kind varchar(32) not null check(kind in ('FIRST_FAILURE','CLEANUP_FAILURE')),
    phase varchar(64) not null check(phase in
        ('LEASE_ACQUISITION','CAMPAIGN_LAUNCH','SELECTION_RECHECK','TRANSPORT_OPEN','SCHEDULING',
         'BUDGET_READ','STORAGE_CHECK','ATTEMPT_RESERVATION','TRANSPORT','RAW_SAVE','NORMALIZATION',
         'RESULT_PUBLICATION','SCHEDULE_PUBLICATION','CLEANUP_TRANSPORT_CLOSE','CLEANUP_TRANSPORT_ABSENCE',
         'CLEANUP_DIAGNOSTIC_PUBLICATION','CLEANUP_EXCLUSION','CLEANUP_STATE_READ','CLEANUP_OWNERSHIP_CHECK','CLEANUP_SCHEDULE_PUBLICATION',
         'CLEANUP_ATTEMPT_RECONCILIATION','CLEANUP_TERMINAL_PUBLICATION','CLEANUP_LEASE_RELEASE')),
    code varchar(96) not null check(code in (
        'LIVE_STORAGE_PROBE_NOT_CONFIGURED','LIVE_STORAGE_PROBE_TIMEOUT','LIVE_STORAGE_PROBE_FAILED',
        'LIVE_STORAGE_PROBE_INVALID','LIVE_STORAGE_PROBE_INTERRUPTED','LIVE_STORAGE_CAPACITY_REFUSED',
        'LIVE_RAW_PREVIOUSLY_PURGED','LIVE_PROVIDER_CLEANUP_UNVERIFIED','LIVE_CLEANUP_OWNERSHIP_CHANGED',
        'PROVIDER_SUSPENDED','PROVIDER_CLOCK_REGRESSION','PROVIDER_DIAGNOSTIC_PERSISTENCE_FAILED',
        'PROVIDER_DEPARTURE_UNRESOLVED','PROVIDER_HTTP_403','PROVIDER_HTTP_429',
        'RUNTIME_OR_STORAGE_FAILURE','PROVIDER_COORDINATION_FAILURE',
        'PLAYWRIGHT_DISABLED','PLAYWRIGHT_WORKER_ARTIFACT_INVALID','PLAYWRIGHT_CAMPAIGN_ALREADY_ACTIVE',
        'PLAYWRIGHT_STARTUP_FAILED','PLAYWRIGHT_AUTHENTICATION_FAILED','PLAYWRIGHT_PROTOCOL_ERROR',
        'PLAYWRIGHT_INVALID_ENDPOINT','PLAYWRIGHT_INVALID_REQUEST','PLAYWRIGHT_TIMEOUT','PLAYWRIGHT_IPC_TIMEOUT',
        'PLAYWRIGHT_PAYLOAD_TOO_LARGE','PLAYWRIGHT_SENSITIVE_CONTENT_REJECTED','PLAYWRIGHT_UNEXPECTED_ROUTE',
        'PLAYWRIGHT_REDIRECT_BLOCKED','PLAYWRIGHT_UNEXPECTED_CONTENT','PLAYWRIGHT_OPERATOR_STOP','PLAYWRIGHT_RUNTIME_FAILURE')),
    occurred_at timestamptz not null,
    attempt_id uuid references live_call(attempt_id) on delete restrict,
    endpoint_type varchar(32),
    primary key(campaign_id,kind),
    check((attempt_id is null)=(endpoint_type is null))
);

create function validate_live_diagnostic_identity() returns trigger language plpgsql as $$
begin
    if new.attempt_id is not null and not exists(select 1 from public.live_call
        where attempt_id=new.attempt_id and campaign_id=new.campaign_id and endpoint=new.endpoint_type) then
        raise exception 'diagnostic identity differs from its reserved attempt';
    end if;
    return new;
end $$;
create trigger live_attempt_transport_identity before insert or update on live_attempt_transport_diagnostic
    for each row execute function validate_live_diagnostic_identity();
create trigger live_campaign_diagnostic_identity before insert or update on live_campaign_diagnostic
    for each row execute function validate_live_diagnostic_identity();

create function protect_live_primary_diagnostic() returns trigger language plpgsql as $$
begin
    if tg_op='DELETE' or old.kind='FIRST_FAILURE' and new is distinct from old
        or new.campaign_id is distinct from old.campaign_id or new.kind is distinct from old.kind then
        raise exception 'primary diagnostic is immutable';
    end if;
    return new;
end $$;
create trigger live_primary_diagnostic_immutable before update or delete on live_campaign_diagnostic
    for each row execute function protect_live_primary_diagnostic();

create function live_transport_diagnostic_rank(phase text) returns integer language sql immutable as $$
    select case phase when 'NAVIGATION' then 0 when 'REQUEST_SENT' then 1 when 'HEADERS_RECEIVED' then 2
        when 'READING_BODY' then 3 when 'PARENT_IPC_WAIT' then 4 when 'COMPLETE' then 5 else -1 end
$$;
create function protect_live_transport_diagnostic() returns trigger language plpgsql as $$
begin
    if tg_op='DELETE' then raise exception 'transport diagnostic cannot be deleted'; end if;
    if (new.attempt_id,new.campaign_id,new.endpoint_type,new.timeout_ms)
            is distinct from (old.attempt_id,old.campaign_id,old.endpoint_type,old.timeout_ms)
        or (old.requested_at is not null and new.requested_at is distinct from old.requested_at)
        or (old.headers_received_at is not null and new.headers_received_at is distinct from old.headers_received_at)
        or (old.http_status is not null and new.http_status is distinct from old.http_status)
        or (old.retry_not_before is not null and new.retry_not_before is distinct from old.retry_not_before)
        or (old.response_complete and new is distinct from old)
        or public.live_transport_diagnostic_rank(new.transport_phase)<public.live_transport_diagnostic_rank(old.transport_phase) then
        raise exception 'known transport evidence cannot be rewritten';
    end if;
    return new;
end $$;
create trigger live_transport_diagnostic_monotonic before update or delete on live_attempt_transport_diagnostic
    for each row execute function protect_live_transport_diagnostic();
create trigger live_transport_diagnostic_reject_truncate before truncate on live_attempt_transport_diagnostic
    for each statement execute function reject_live_evidence_mutation();
create trigger live_campaign_diagnostic_reject_truncate before truncate on live_campaign_diagnostic
    for each statement execute function reject_live_evidence_mutation();

comment on table live_attempt_transport_diagnostic is 'Partial transport metadata only: a known HTTP status is not proof of a complete saved response.';
comment on table live_campaign_diagnostic is 'Durable bounded first failure and latest cleanup failure; historical absence remains unknown.';
