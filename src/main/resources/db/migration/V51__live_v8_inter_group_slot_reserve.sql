-- WO-058 live-v8: V49 counted the four terminal fences but not the separate
-- one-second scheduling reservation needed between adjacent V8 event groups.
-- Historical manifests and their original validation remain immutable.

create or replace function validate_live_grouped_policy() returns trigger language plpgsql as $$
declare endpoint_name text; envelope jsonb; field_name text;
    policy text; qualified_capacity integer; cycle_interval integer; v8_group_nanos numeric := 0;
begin
    select policy_version,qualified_match_capacity,cycle_interval_seconds into strict policy,qualified_capacity,cycle_interval
        from public.live_campaign where campaign_id=new.campaign_id;
    if not (
            (policy='live-v4' and cycle_interval=60
                    and new.critical_interval_seconds=60 and new.lineup_interval_seconds=300 and new.inter_group_delay_nanos=3000000000)
                or (policy in ('live-v5','live-v6') and cycle_interval=100
                    and new.critical_interval_seconds=100 and new.lineup_interval_seconds=300 and new.inter_group_delay_nanos=1000000000)
                or (policy='live-v7' and cycle_interval=60
                    and new.critical_interval_seconds=60 and new.lineup_interval_seconds=60 and new.inter_group_delay_nanos=1000000000)
                or (policy='live-v8' and cycle_interval=60
                    and new.critical_interval_seconds=60 and new.lineup_interval_seconds=60 and new.inter_group_delay_nanos=500000000)
        ) then
        raise exception 'grouped profile cadence and delay must match its manifest policy';
    end if;
    if (select count(*) from jsonb_object_keys(new.endpoint_envelopes)) <> 4 then
        raise exception 'four grouped endpoint envelopes required';
    end if;
    foreach endpoint_name in array array['EVENT_DETAILS','EVENT_INCIDENTS','EVENT_STATISTICS','EVENT_LINEUPS'] loop
        envelope := new.endpoint_envelopes -> endpoint_name;
        if envelope is null or jsonb_typeof(envelope) <> 'object'
            or (select count(*) from jsonb_object_keys(envelope)) <> 2 then
            raise exception 'invalid grouped endpoint envelope';
        end if;
        foreach field_name in array array['requestNanos','processingNanos'] loop
            if jsonb_typeof(envelope -> field_name) is distinct from 'number'
                    or (envelope ->> field_name) !~ '^[0-9]+$' then
                raise exception 'grouped envelope must be integer nanoseconds';
            end if;
        end loop;
        if (envelope ->> 'requestNanos')::numeric not between 1 and 10000000000
            or (envelope ->> 'processingNanos')::numeric not between 0 and 60000000000 then
            raise exception 'grouped envelope outside accepted bounds';
        end if;
        if policy='live-v8' then
            v8_group_nanos := v8_group_nanos
                + (envelope ->> 'requestNanos')::numeric
                + (envelope ->> 'processingNanos')::numeric
                + 500000000;
        end if;
    end loop;
    if policy='live-v8' then
        -- One static one-second inter-group scheduling reservation, distinct
        -- from the four terminal fences already charged above. It preserves
        -- the effective 500 ms closing fence when the normal J4 uses its
        -- bounded 500 ms worker-start jitter.
        v8_group_nanos := v8_group_nanos + 1000000000;
        if qualified_capacity::numeric * v8_group_nanos > 60000000000 then
            raise exception 'live-v8 grouped envelopes plus worker-start reserve exceed the sixty-second capacity window';
        end if;
    end if;
    return new;
end $$;

comment on table public.live_grouped_policy is
    'Immutable qualified grouped policy: V4 60 s / 3 s, V5-V6 100 s / 1 s, V7 60 s / 1 s, V8 60 s / 0.5 s. V8 requires capacity times all four exchange envelopes, all four 0.5 s terminal fences, and one independent 1 s scheduling slot reserve within 60 seconds.';
