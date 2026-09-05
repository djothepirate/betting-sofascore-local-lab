create or replace function guard_j7_delivery_attempt_result_insert()
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
                not isfinite(new.acknowledgement_received_at)
                or new.acknowledgement_received_at
                    < timestamptz '0001-01-01 00:00:00+00'
                or new.acknowledgement_received_at
                    > timestamptz '9999-12-31 23:59:59.999999+00'
            )
       ) then
        raise exception 'J7 delivery attempt result is not current';
    end if;
    return new;
end;
$$;

comment on column j7_delivery_attempt_result.acknowledgement_received_at is
    'Canonical receivedAt declared by the receiver; its wall clock is independent from Local Lab attempt timestamps.';

comment on function guard_j7_delivery_attempt_result_insert() is
    'Guards current exactly representable append-only completion without ordering the receiver clock against Local Lab timestamps.';
