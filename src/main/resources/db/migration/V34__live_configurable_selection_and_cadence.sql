-- WO-058: configurable selection ceiling; cadence frozen with each new manifest.
-- Existing live-v1 rows retain their SHA, policy, counters and original 60-second cadence.
alter table live_campaign drop constraint live_campaign_qualified_match_capacity_check;
alter table live_campaign alter column qualified_match_capacity type integer;
alter table live_campaign add constraint live_campaign_qualified_match_capacity_check
    check (qualified_match_capacity >= 1);
alter table live_campaign add constraint live_campaign_selection_size_check
    check (target_count <= 100);
alter table live_campaign add column cycle_interval_seconds integer not null default 60
    check (cycle_interval_seconds between 60 and 2970);
alter table live_campaign add constraint live_campaign_adaptive_cadence_check
    check (policy_version <> 'live-v2' or cycle_interval_seconds = greatest(60, 30 * (target_count - 1)));
alter table live_event drop constraint live_event_target_order_check;
alter table live_event add constraint live_event_target_order_check check (target_order between 0 and 99);
-- protect_live_manifest() compares every immutable column, including the new cadence.
