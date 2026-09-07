-- Read-only metadata extraction. No provider payload or transport credentials.
-- psql -X -q -t -A -v ON_ERROR_STOP=1; output is one JSON object.
begin transaction isolation level repeatable read read only;
set local statement_timeout = '10s';
with campaigns as (
 select campaign_id, policy_version, cycle_interval_seconds, started_at, ends_at,
        state, reserved_calls, received_bytes
 from live_campaign
 where campaign_id in ('eeffbed1-c589-485c-94fe-f09ebc51ec2d',
                       '11a22482-5997-4590-9058-3a89d55e8973')
), targets as (
 select e.campaign_id,e.canonical_event_id,e.provider_event_id,e.state,e.missed_cycles,
        o.home_team_name || ' — ' || o.away_team_name as title,o.starts_at
 from live_event e join campaigns using(campaign_id)
 join canonical_event_observation o on o.id=e.source_observation_id
), attempts as (
 select c.campaign_id,c.canonical_event_id,c.attempt_id,c.ordinal,c.cycle_number,
 c.endpoint,c.kind,c.final_cycle,c.due_at,c.reserved_at,d.authorized_at,
 p.requested_at,p.received_at,p.snapshot_id,p.occurrence_id,p.payload_sha256,
 r.resolved_at,r.outcome,r.code,r.successful,r.parser_version,r.normalized_sha256,
 r.projection_version,r.projection_sha256,r.completeness_status,r.completeness_score,
 r.next_event_state,r.sport_status,ed.status_description,
 r.projection_json #>> '{homeScore,value,current,value}' as home_score,
 r.projection_json #>> '{awayScore,value,current,value}' as away_score,
 j.lineups_confirmed,
 case when c.endpoint='EVENT_LINEUPS' and r.successful then
  (select count(*) from j5_event_lineup_player lp where lp.observation_id=j.id and lp.starter and lp.side='HOME') end as home_starters,
 case when c.endpoint='EVENT_LINEUPS' and r.successful then
  (select count(*) from j5_event_lineup_player lp where lp.observation_id=j.id and lp.starter and lp.side='AWAY') end as away_starters
 from live_call c join campaigns using(campaign_id)
 left join live_call_dispatch d using(attempt_id)
 left join live_call_receipt p using(attempt_id)
 left join live_call_result r using(attempt_id)
 left join event_detail_observation ed on ed.id=r.detail_observation_id
 left join j5_event_data_observation j on j.id=r.j5_observation_id
), transitions as (
 select t.campaign_id,t.canonical_event_id,t.revision,t.state,t.reason,t.changed_at
 from live_transition t join campaigns using(campaign_id)
)
select jsonb_build_object('captured_at',current_timestamp,'provider','SOFASCORE',
 'contract','wo058-temporal-metadata-v1',
 'campaigns',(select jsonb_agg(to_jsonb(c) order by started_at) from campaigns c),
 'targets',(select jsonb_agg(to_jsonb(t) order by campaign_id,provider_event_id) from targets t),
 'attempts',(select jsonb_agg(to_jsonb(a) order by ordinal) from attempts a),
 'transitions',(select jsonb_agg(to_jsonb(t) order by campaign_id,revision) from transitions t));
rollback;
