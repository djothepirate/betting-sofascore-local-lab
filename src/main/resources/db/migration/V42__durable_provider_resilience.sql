-- WO-058: no previous campaign/snapshot is rewritten or inferred as a provider refusal.
-- The singleton serializes admission and manual rearming across all local JVMs/campaigns.
create table provider_resilience_state (
    singleton_id smallint primary key check (singleton_id=1),
    policy_version varchar(32) not null check (policy_version='provider-resilience-v1'),
    state varchar(16) not null check (state in ('OPEN','SUSPENDED')),
    version bigint not null default 0 check (version>=0),
    changed_at timestamptz not null,
    http_status integer check (http_status in (403,429)),
    suspended_at timestamptz,
    retry_not_before timestamptz,
    last_departure_at timestamptz,
    last_departure_finished_at timestamptz,
    unresolved_dispatch_id uuid,
    evidence_id uuid,
    -- Correlation can identify a manual J3/J4/J5 campaign absent from live_campaign.
    campaign_id uuid,
    check (((http_status is null and suspended_at is null and evidence_id is null
                and retry_not_before is null and campaign_id is null)
        or (http_status is not null and suspended_at is not null and evidence_id is not null)) is true),
    check (state<>'SUSPENDED' or (http_status is not null and suspended_at is not null and evidence_id is not null)),
    check (retry_not_before is null or retry_not_before>=suspended_at)
);
insert into provider_resilience_state(singleton_id,policy_version,state,changed_at)
    values (1,'provider-resilience-v1','OPEN',current_timestamp);

-- Reservations count even if dispatch/body subsequently fails. Nothing refunds a timeout.
create table provider_departure_reservation (
    dispatch_id uuid primary key,
    reserved_at timestamptz not null,
    policy_version varchar(32) not null check (policy_version='provider-resilience-v1')
);
create index provider_departure_reservation_at on provider_departure_reservation(reserved_at desc);

-- Closing an exchange charges its entire lifetime conservatively to the rolling windows.
-- An unresolved reservation survives JVM death and cannot expire into an authorization.
alter table provider_resilience_state add foreign key (unresolved_dispatch_id)
    references provider_departure_reservation(dispatch_id) on delete restrict;
create table provider_departure_completion (
    dispatch_id uuid primary key references provider_departure_reservation(dispatch_id) on delete restrict,
    finished_at timestamptz not null
);
create index provider_departure_completion_at on provider_departure_completion(finished_at desc);
create function validate_provider_departure_completion() returns trigger language plpgsql as $$
begin
    if new.finished_at < (select reserved_at from public.provider_departure_reservation where dispatch_id=new.dispatch_id) then
        raise exception 'provider completion precedes reservation';
    end if;
    return new;
end $$;
create trigger provider_departure_completion_valid before insert on provider_departure_completion
    for each row execute function validate_provider_departure_completion();

-- Minimized immutable state-transition evidence. No URL, raw header, payload or arbitrary exception.
create table provider_resilience_event (
    event_id uuid primary key,
    kind varchar(16) not null check (kind in ('REFUSAL','MANUAL_REARM')),
    state_version bigint not null check (state_version>0),
    occurred_at timestamptz not null,
    http_status integer check (http_status in (403,429)),
    retry_not_before timestamptz,
    campaign_id uuid,
    check (((kind='REFUSAL' and http_status is not null)
        or (kind='MANUAL_REARM' and http_status is null and retry_not_before is null and campaign_id is null)) is true),
    check (retry_not_before is null or (retry_not_before>=occurred_at
        and retry_not_before<=occurred_at+interval '365 days'))
);
create index provider_resilience_event_version on provider_resilience_event(state_version);

create trigger provider_departure_reservation_append_only before update or delete on provider_departure_reservation
    for each row execute function reject_live_evidence_mutation();
create trigger provider_departure_reservation_reject_truncate before truncate on provider_departure_reservation
    for each statement execute function reject_live_evidence_mutation();
create trigger provider_departure_completion_append_only before update or delete on provider_departure_completion
    for each row execute function reject_live_evidence_mutation();
create trigger provider_departure_completion_reject_truncate before truncate on provider_departure_completion
    for each statement execute function reject_live_evidence_mutation();
create trigger provider_resilience_event_append_only before update or delete on provider_resilience_event
    for each row execute function reject_live_evidence_mutation();
create trigger provider_resilience_event_reject_truncate before truncate on provider_resilience_event
    for each statement execute function reject_live_evidence_mutation();
