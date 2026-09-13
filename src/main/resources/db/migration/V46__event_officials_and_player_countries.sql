-- No backfill: historical parser versions, values, hashes and snapshot provenance remain unchanged.
alter table event_detail_observation
    add column home_manager_name varchar(200),
    add column home_manager_country_name varchar(120),
    add column home_manager_country_alpha2 varchar(2),
    add column away_manager_name varchar(200),
    add column away_manager_country_name varchar(120),
    add column away_manager_country_alpha2 varchar(2),
    add column referee_name varchar(200),
    add column referee_country_name varchar(120),
    add column referee_country_alpha2 varchar(2);
alter table j5_event_lineup_player
    add column country_name varchar(120),
    add column country_alpha2 varchar(2);

create function v46_valid_optional_name(value text, maximum integer)
returns boolean language sql immutable as $$
    select value is null or (length(value) between 1 and maximum and btrim(value) <> '' and value !~ '[[:cntrl:]]')
$$;
create function v46_valid_country(country_name text, alpha2 text)
returns boolean language sql immutable as $$
    select public.v46_valid_optional_name(country_name,120) and (alpha2 is null or alpha2 collate "C" ~ '^[A-Z]{2}$')
$$;
create function v46_valid_person(person_name text, country_name text, alpha2 text)
returns boolean language sql immutable as $$
    select public.v46_valid_optional_name(person_name,200)
        and public.v46_valid_country(country_name,alpha2)
        and (person_name is not null or (country_name is null and alpha2 is null))
$$;

alter table event_detail_observation
    drop constraint ck_event_detail_observation_parser,
    drop constraint ck_event_detail_observation_display_score_contract,
    add constraint ck_event_detail_observation_parser check
        (parser_version in ('event-details-v1','event-details-v2','event-details-v3','event-details-v4')),
    add constraint ck_event_detail_observation_display_score_contract check
        (parser_version in ('event-details-v3','event-details-v4')
            or (is_awarded is null and home_display_score is null and away_display_score is null)),
    add constraint ck_event_detail_officials_valid check
        (public.v46_valid_person(home_manager_name,home_manager_country_name,home_manager_country_alpha2)
            and public.v46_valid_person(away_manager_name,away_manager_country_name,away_manager_country_alpha2)
            and public.v46_valid_person(referee_name,referee_country_name,referee_country_alpha2)),
    add constraint ck_event_detail_officials_provenance check
        ((home_manager_name is null and away_manager_name is null and referee_name is null)
            or (parser_version='event-details-v4' and source_kind='PROVIDER_SNAPSHOT'));

alter table j5_event_lineup_player add constraint ck_j5_lineup_country_valid check
    (public.v46_valid_country(country_name,country_alpha2));
-- Leave the immutable V41 JSON validator available for historical contracts.
-- V46 permits one bounded optional country object; all other keys keep V41 validation.
create function j5_v46_valid_missing_players(value jsonb)
returns boolean language plpgsql immutable as $$
declare player jsonb; country jsonb; legacy jsonb := '[]'::jsonb;
begin
    if value is null or jsonb_typeof(value)<>'array' or octet_length(value::text)>262144 then return false; end if;
    if jsonb_array_length(value)>128 then return false; end if;
    for player in select item from jsonb_array_elements(value) as entry(item) loop
        if jsonb_typeof(player)<>'object' then return false; end if;
        if player ? 'country' then
            country := player -> 'country';
            if jsonb_typeof(country)<>'object'
                or not country ?& array['name','alpha2']
                or country - array['name','alpha2'] <> '{}'::jsonb
                or not public.j5_v41_optional_text(country -> 'name',120)
                or not public.j5_v41_optional_text(country -> 'alpha2',2)
                or ((country ->> 'name') is null and (country ->> 'alpha2') is null)
                or not public.v46_valid_country(country ->> 'name',country ->> 'alpha2') then return false; end if;
        end if;
        legacy := legacy || jsonb_build_array(player - 'country');
    end loop;
    return public.j5_v41_valid_missing_players(legacy);
end $$;
alter table j5_event_lineup_side drop constraint ck_j5_lineup_missing_players;
alter table j5_event_lineup_side add constraint ck_j5_lineup_missing_players check
    (missing_players is null or public.j5_v46_valid_missing_players(missing_players));
alter table j5_event_data_observation drop constraint ck_j5_event_data_parser;
alter table j5_event_data_observation add constraint ck_j5_event_data_parser check (
    (endpoint_type='EVENT_STATISTICS' and parser_version in ('event-statistics-v1','event-statistics-v2','event-statistics-unavailable-v1'))
    or (endpoint_type='EVENT_INCIDENTS' and parser_version in (
        'event-incidents-v1','event-incidents-v2','event-incidents-v3','event-incidents-v4','event-incidents-v5',
        'event-incidents-v6','event-incidents-v7','event-incidents-v8','event-incidents-v9','event-incidents-v10',
        'event-incidents-v11','event-incidents-v12','event-incidents-v13','event-incidents-v14','event-incidents-v15',
        'event-incidents-v16','event-incidents-v17','event-incidents-unavailable-v1'))
    or (endpoint_type='EVENT_LINEUPS' and parser_version in
        ('event-lineups-v1','event-lineups-v2','event-lineups-v3','event-lineups-v4','event-lineups-unavailable-v1'))
);

create or replace function j5_v41_require_enriched_lineup_provenance()
returns trigger language plpgsql as $$
declare enriched boolean; countries boolean := false; parser text; origin text; completeness text;
begin
    if tg_table_name='j5_event_lineup_player' then
        countries := new.country_name is not null or new.country_alpha2 is not null;
        enriched := countries or new.captain is not null or new.statistics is not null;
    else
        enriched := new.missing_players is not null;
        if jsonb_typeof(new.missing_players)='array' then
            select exists(select 1 from jsonb_array_elements(new.missing_players) player where player ? 'country') into countries;
        end if;
    end if;
    if enriched then
        select parser_version,source_kind,completeness_status into parser,origin,completeness
            from public.j5_event_data_observation where id=new.observation_id;
        if not found or parser not in ('event-lineups-v3','event-lineups-v4') or origin<>'PROVIDER_SNAPSHOT'
                or completeness='UNAVAILABLE' then
            raise exception 'enriched lineup values require an available event-lineups-v3 or event-lineups-v4 provider observation';
        end if;
        if countries and parser<>'event-lineups-v4' then
            raise exception 'player countries require an available event-lineups-v4 provider observation';
        end if;
    end if;
    return new;
end $$;

comment on column event_detail_observation.event_round is
    'V4 prefers event.roundInfo.name when present and valid, then preserves the V3 round fallback. Historical values are unchanged.';
comment on column event_detail_observation.home_manager_name is 'Optional event.homeTeam.manager.name from V4; no historical inference.';
comment on column event_detail_observation.away_manager_name is 'Optional event.awayTeam.manager.name from V4; no historical inference.';
comment on column event_detail_observation.referee_name is 'Optional event.referee.name from V4; no historical inference.';
comment on column j5_event_lineup_player.country_name is 'Optional player.country.name from V4; independent of team country.';
comment on column j5_event_lineup_player.country_alpha2 is 'Optional provider player.country.alpha2, normalized uppercase; no inferred country.';
