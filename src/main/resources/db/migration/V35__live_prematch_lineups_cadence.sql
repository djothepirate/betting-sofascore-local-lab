-- WO-058: new live-v3 manifests retain the qualified adaptive cadence.
-- Existing live-v1/live-v2 manifests and evidence remain unchanged.
alter table live_campaign add constraint live_campaign_v3_adaptive_cadence_check
    check (policy_version <> 'live-v3' or cycle_interval_seconds = greatest(60, 30 * (target_count - 1)));
