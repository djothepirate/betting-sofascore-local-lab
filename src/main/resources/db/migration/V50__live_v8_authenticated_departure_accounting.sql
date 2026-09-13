-- WO-058 live-v8: the durable shared pressure window is charged from the
-- authenticated worker REQUEST_SENT timestamp when that proof exists.  The
-- pre-dispatch reservation and its unresolved lock remain authoritative; this
-- table records only the immutable instant used to age an already charged call.
create table provider_departure_accounting (
    dispatch_id uuid primary key references provider_departure_reservation(dispatch_id) on delete restrict,
    departure_at timestamptz not null,
    source varchar(40) not null check (source in ('AUTHENTICATED_WORKER_REQUEST','COMPLETION_FALLBACK'))
);

-- Existing retained completions predate worker REQUEST_SENT evidence.  Retain
-- their conservative closing instant explicitly instead of rewriting or
-- inferring a worker timestamp.
insert into provider_departure_accounting(dispatch_id,departure_at,source)
    select dispatch_id,finished_at,'COMPLETION_FALLBACK'
    from provider_departure_completion;

create index provider_departure_accounting_at on provider_departure_accounting(departure_at desc);

create function validate_provider_departure_accounting() returns trigger language plpgsql as $$
declare reserved timestamptz; profile text; completed timestamptz;
begin
    select reserved_at,admission_profile into strict reserved,profile
        from public.provider_departure_reservation where dispatch_id=new.dispatch_id;
    if new.departure_at < reserved then
        raise exception 'provider departure accounting precedes reservation';
    end if;
    select finished_at into completed from public.provider_departure_completion where dispatch_id=new.dispatch_id;
    if new.source='AUTHENTICATED_WORKER_REQUEST' then
        if profile<>'live-v8' then
            raise exception 'authenticated worker departure requires live-v8 profile';
        end if;
        if completed is not null and new.departure_at>completed then
            raise exception 'authenticated worker departure follows completion';
        end if;
    elsif new.source='COMPLETION_FALLBACK' then
        if completed is null or new.departure_at<>completed then
            raise exception 'completion fallback must equal retained completion';
        end if;
    end if;
    return new;
end $$;
create trigger provider_departure_accounting_valid before insert on provider_departure_accounting
    for each row execute function validate_provider_departure_accounting();

-- Completion is the durable fallback when no authenticated REQUEST_SENT proof
-- arrived (including historical rows and failed exchanges).  A prior worker
-- proof wins and cannot be overwritten.
create function record_provider_departure_completion_fallback() returns trigger language plpgsql as $$
begin
    insert into provider_departure_accounting(dispatch_id,departure_at,source)
        values (new.dispatch_id,new.finished_at,'COMPLETION_FALLBACK')
        on conflict (dispatch_id) do nothing;
    return new;
end $$;
create trigger provider_departure_completion_accounting after insert on provider_departure_completion
    for each row execute function record_provider_departure_completion_fallback();

create trigger provider_departure_accounting_append_only before update or delete on provider_departure_accounting
    for each row execute function reject_live_evidence_mutation();
create trigger provider_departure_accounting_reject_truncate before truncate on provider_departure_accounting
    for each statement execute function reject_live_evidence_mutation();

comment on table provider_departure_accounting is
    'Immutable rolling-window charge: live-v8 uses authenticated worker REQUEST_SENT; retained or unproven exchanges use completion fallback.';
comment on column provider_departure_accounting.source is
    'AUTHENTICATED_WORKER_REQUEST is bounded worker evidence; COMPLETION_FALLBACK preserves conservative historical and unproven exchanges.';
