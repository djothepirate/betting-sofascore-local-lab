-- WO-058 live-v8: profile selection is durable pressure evidence only. It does
-- not create a provider identity, separate circuit, automatic rearm or retry.
-- Existing V42 reservations resolve to the legacy default; the state profile stays null rather than being rewritten.
alter table provider_departure_reservation
    add column admission_profile varchar(16) not null default 'legacy-v1'
        check (admission_profile in ('legacy-v1','live-v8'));

alter table provider_resilience_state
    add column last_departure_admission_profile varchar(16),
    add constraint provider_resilience_state_last_departure_admission_profile_check
        check (last_departure_admission_profile is null
            or last_departure_admission_profile in ('legacy-v1','live-v8')),
    add constraint provider_resilience_state_last_departure_admission_profile_finished_check
        check (last_departure_admission_profile is null or last_departure_finished_at is not null);

comment on column provider_departure_reservation.admission_profile is
    'Closed local durable pressure profile for this real departure; defaults to legacy-v1 for all V42 history.';
comment on column provider_resilience_state.last_departure_admission_profile is
    'Profile of the last completed departure; null is pre-V48 history and is read conservatively as legacy-v1.';
