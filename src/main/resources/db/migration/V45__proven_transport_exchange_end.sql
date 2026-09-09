-- WO-058: a timed-out body is not a complete response. Only newly observed,
-- correlated transport termination can permit reuse of the same live context.
alter table live_attempt_transport_diagnostic
    add column exchange_ended_at timestamptz,
    add column exchange_end_reason varchar(16),
    add column context_reusable boolean not null default false,
    add constraint live_transport_exchange_end_pair check
        ((exchange_ended_at is null)=(exchange_end_reason is null)),
    add constraint live_transport_exchange_end_reason check
        (exchange_end_reason in ('FINISHED','ABORTED')),
    add constraint live_transport_exchange_end_time check
        (exchange_ended_at is null or (requested_at is not null and exchange_ended_at>=requested_at
            and (headers_received_at is null or exchange_ended_at>=headers_received_at))),
    add constraint live_transport_reusable_proof check
        (not context_reusable or (exchange_ended_at is not null and not response_complete
            and (http_status is null or http_status not in (403,429))));

-- Existing fields and newly acquired proof remain monotone. A late progress
-- callback may not erase known headers, termination, or successful cleanup.
create or replace function protect_live_transport_diagnostic() returns trigger language plpgsql as $$
begin
    if tg_op='DELETE' then raise exception 'transport diagnostic cannot be deleted'; end if;
    if (new.attempt_id,new.campaign_id,new.endpoint_type,new.timeout_ms)
            is distinct from (old.attempt_id,old.campaign_id,old.endpoint_type,old.timeout_ms)
        or (old.requested_at is not null and new.requested_at is distinct from old.requested_at)
        or (old.headers_received_at is not null and new.headers_received_at is distinct from old.headers_received_at)
        or (old.http_status is not null and new.http_status is distinct from old.http_status)
        or (old.retry_not_before is not null and new.retry_not_before is distinct from old.retry_not_before)
        or (old.exchange_ended_at is not null and new.exchange_ended_at is distinct from old.exchange_ended_at)
        or (old.exchange_end_reason is not null and new.exchange_end_reason is distinct from old.exchange_end_reason)
        or (old.context_reusable and not new.context_reusable)
        or (old.response_complete and
            (to_jsonb(new)-array['exchange_ended_at','exchange_end_reason','context_reusable'])
            is distinct from (to_jsonb(old)-array['exchange_ended_at','exchange_end_reason','context_reusable']))
        or public.live_transport_diagnostic_rank(new.transport_phase)<public.live_transport_diagnostic_rank(old.transport_phase) then
        raise exception 'known transport evidence cannot be rewritten';
    end if;
    return new;
end $$;

comment on column live_attempt_transport_diagnostic.exchange_ended_at is
    'Observed terminal event correlated to this request; null for historical or uncertain termination.';
comment on column live_attempt_transport_diagnostic.exchange_end_reason is
    'FINISHED or ABORTED proves exchange termination, not body receipt or normalized availability.';
comment on column live_attempt_transport_diagnostic.context_reusable is
    'Only after proven exchange termination and local page cleanup; never inferred from TIMEOUT or absence of HTTP status.';
