-- Nullable additions deliberately leave every previous observation and hash unchanged.
ALTER TABLE j5_event_lineup_player
    ADD COLUMN captain boolean,
    ADD COLUMN statistics jsonb;
ALTER TABLE j5_event_lineup_side
    ADD COLUMN missing_players jsonb;

CREATE FUNCTION j5_v41_valid_decimal_map(value jsonb, maximum_fields integer)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    item record;
    plain text;
    digits text;
    decimal_scale integer;
    trailing_zeroes integer;
BEGIN
    IF value IS NULL OR jsonb_typeof(value) <> 'object'
            OR octet_length(value::text) > 32768 THEN RETURN false; END IF;
    IF (SELECT count(*) FROM jsonb_object_keys(value)) > maximum_fields THEN RETURN false; END IF;
    FOR item IN SELECT key, val FROM jsonb_each(value) AS entry(key, val) LOOP
        IF item.key COLLATE "C" !~ '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,79}$'
                OR jsonb_typeof(item.val) <> 'number' THEN RETURN false; END IF;
        IF (item.val #>> '{}')::numeric <> 0 THEN
            plain := trim_scale(abs((item.val #>> '{}')::numeric))::text;
            decimal_scale := CASE WHEN strpos(plain, '.') = 0 THEN 0
                ELSE length(plain) - strpos(plain, '.') END;
            digits := ltrim(replace(plain, '.', ''), '0');
            trailing_zeroes := length(digits) - length(rtrim(digits, '0'));
            IF length(digits) - trailing_zeroes > 64
                    OR abs(decimal_scale - trailing_zeroes) > 32 THEN RETURN false; END IF;
        END IF;
    END LOOP;
    RETURN true;
END;
$$;

-- pg_restore evaluates CHECK constraints with an empty search_path. Keep every
-- application-function dependency schema-qualified, including nested validators.
CREATE FUNCTION j5_v41_valid_player_statistics(value jsonb)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
    IF value IS NULL OR jsonb_typeof(value) <> 'object' THEN RETURN false; END IF;
    RETURN value ?& ARRAY['values', 'ratingVersions']
        AND value - ARRAY['values', 'ratingVersions'] = '{}'::jsonb
        AND public.j5_v41_valid_decimal_map(value -> 'values', 128)
        AND public.j5_v41_valid_decimal_map(value -> 'ratingVersions', 16);
END;
$$;

CREATE FUNCTION j5_v41_optional_text(value jsonb, maximum_length integer)
RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT coalesce(jsonb_typeof(value) = 'null' OR (
        jsonb_typeof(value) = 'string'
        AND length(value #>> '{}') BETWEEN 1 AND maximum_length
        AND btrim(value #>> '{}') <> ''
        AND (value #>> '{}') !~ '[[:cntrl:]]'), false)
$$;

CREATE FUNCTION j5_v41_optional_integer(value jsonb, minimum_value numeric, maximum_value numeric)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE number numeric;
BEGIN
    IF value IS NULL THEN RETURN false; END IF;
    IF jsonb_typeof(value) = 'null' THEN RETURN true; END IF;
    IF jsonb_typeof(value) <> 'number' THEN RETURN false; END IF;
    number := (value #>> '{}')::numeric;
    RETURN number = trunc(number) AND number BETWEEN minimum_value AND maximum_value;
END;
$$;

-- Validate the calendar without casting to timestamptz: Java OffsetDateTime permits
-- years beyond PostgreSQL timestamps and its original offset must remain unchanged.
CREATE FUNCTION j5_v41_valid_offset_datetime(value jsonb)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    parts text[];
    clock_parts text[];
    year_value bigint;
    month_value integer;
    day_value integer;
    days integer[] := ARRAY[31,28,31,30,31,30,31,31,30,31,30,31];
BEGIN
    IF value IS NULL THEN RETURN false; END IF;
    IF jsonb_typeof(value) = 'null' THEN RETURN true; END IF;
    IF NOT public.j5_v41_optional_text(value, 64) THEN RETURN false; END IF;
    parts := regexp_match(value #>> '{}', '^([+-]?[0-9]{4,9})-([0-9]{2})-([0-9]{2})T(.+)$');
    IF parts IS NULL THEN RETURN false; END IF;
    year_value := parts[1]::bigint;
    IF (left(parts[1],1) = '+' AND year_value < 10000)
            OR (left(parts[1],1) = '-' AND year_value = 0)
            OR (length(parts[1]) > 4 AND left(parts[1],1) NOT IN ('+', '-')) THEN RETURN false; END IF;
    month_value := parts[2]::integer;
    day_value := parts[3]::integer;
    IF month_value NOT BETWEEN 1 AND 12 OR day_value < 1 THEN RETURN false; END IF;
    IF mod(year_value,4)=0 AND (mod(year_value,100)<>0 OR mod(year_value,400)=0) THEN days[2] := 29; END IF;
    IF day_value > days[month_value] THEN RETURN false; END IF;
    clock_parts := regexp_match(parts[4],
        '^([0-9]{2}):([0-9]{2})(?::([0-9]{2})(?:\.([0-9]{1,9}))?)?(?:Z|([+-])([0-9]{2}):([0-9]{2})(?::([0-9]{2}))?)$');
    IF clock_parts IS NULL OR clock_parts[1]::integer > 23 OR clock_parts[2]::integer > 59
            OR coalesce(clock_parts[3]::integer, 0) > 59 THEN RETURN false; END IF;
    IF clock_parts[5] IS NOT NULL AND (
            clock_parts[6]::integer > 18 OR clock_parts[7]::integer > 59
            OR coalesce(clock_parts[8]::integer, 0) > 59
            OR (clock_parts[6]::integer = 18 AND (clock_parts[7]::integer <> 0
                OR coalesce(clock_parts[8]::integer, 0) <> 0))) THEN RETURN false; END IF;
    RETURN true;
END;
$$;

CREATE FUNCTION j5_v41_valid_missing_players(value jsonb)
RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE player jsonb;
BEGIN
    IF value IS NULL OR jsonb_typeof(value) <> 'array'
            OR octet_length(value::text) > 262144 THEN RETURN false; END IF;
    IF jsonb_array_length(value) > 128 THEN RETURN false; END IF;
    FOR player IN SELECT item FROM jsonb_array_elements(value) AS entry(item) LOOP
        IF jsonb_typeof(player) <> 'object'
                OR NOT player ?& ARRAY['providerPlayerId', 'name', 'shirtNumber', 'position',
                    'type', 'reason', 'description', 'externalType', 'expectedEndDate']
                OR player - ARRAY['providerPlayerId', 'name', 'shirtNumber', 'position',
                    'type', 'reason', 'description', 'externalType', 'expectedEndDate'] <> '{}'::jsonb
                THEN RETURN false; END IF;
        IF jsonb_typeof(player -> 'providerPlayerId') <> 'number'
                OR NOT public.j5_v41_optional_integer(player -> 'providerPlayerId', 1, 9223372036854775807)
                OR jsonb_typeof(player -> 'name') <> 'string'
                OR NOT public.j5_v41_optional_text(player -> 'name', 200)
                OR NOT public.j5_v41_optional_integer(player -> 'shirtNumber', 1, 999)
                OR NOT public.j5_v41_optional_text(player -> 'position', 32)
                OR NOT public.j5_v41_optional_text(player -> 'type', 64)
                OR NOT public.j5_v41_optional_integer(player -> 'reason', -2147483648, 2147483647)
                OR NOT public.j5_v41_optional_text(player -> 'description', 300)
                OR NOT public.j5_v41_optional_integer(player -> 'externalType', -2147483648, 2147483647)
                OR NOT public.j5_v41_valid_offset_datetime(player -> 'expectedEndDate')
                THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
END;
$$;

ALTER TABLE j5_event_lineup_player ADD CONSTRAINT ck_j5_lineup_player_statistics
    CHECK (statistics IS NULL OR j5_v41_valid_player_statistics(statistics));
ALTER TABLE j5_event_lineup_side ADD CONSTRAINT ck_j5_lineup_missing_players
    CHECK (missing_players IS NULL OR j5_v41_valid_missing_players(missing_players));

CREATE FUNCTION j5_v41_require_enriched_lineup_provenance()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    enriched boolean;
    parser text;
    origin text;
    completeness text;
BEGIN
    IF TG_TABLE_NAME = 'j5_event_lineup_player' THEN
        enriched := NEW.captain IS NOT NULL OR NEW.statistics IS NOT NULL;
    ELSE
        enriched := NEW.missing_players IS NOT NULL;
    END IF;
    IF enriched THEN
        SELECT parser_version, source_kind, completeness_status INTO parser, origin, completeness
            FROM public.j5_event_data_observation WHERE id = NEW.observation_id;
        IF NOT FOUND OR parser <> 'event-lineups-v3' OR origin <> 'PROVIDER_SNAPSHOT'
                OR completeness = 'UNAVAILABLE' THEN
            RAISE EXCEPTION 'enriched lineup values require an available event-lineups-v3 provider observation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- The existing *_j7_source_lock triggers run first; neither they nor the
-- append-only triggers are replaced. Empty supplied objects/lists are enriched too.
CREATE TRIGGER j5_event_lineup_player_v3_provenance BEFORE INSERT ON j5_event_lineup_player
    FOR EACH ROW EXECUTE FUNCTION j5_v41_require_enriched_lineup_provenance();
CREATE TRIGGER j5_event_lineup_side_v3_provenance BEFORE INSERT ON j5_event_lineup_side
    FOR EACH ROW EXECUTE FUNCTION j5_v41_require_enriched_lineup_provenance();

ALTER TABLE j5_event_data_observation DROP CONSTRAINT ck_j5_event_data_parser;
ALTER TABLE j5_event_data_observation ADD CONSTRAINT ck_j5_event_data_parser CHECK (
    (endpoint_type = 'EVENT_STATISTICS' AND parser_version IN (
        'event-statistics-v1', 'event-statistics-v2', 'event-statistics-unavailable-v1'))
    OR (endpoint_type = 'EVENT_INCIDENTS' AND parser_version IN (
        'event-incidents-v1', 'event-incidents-v2', 'event-incidents-v3', 'event-incidents-v4',
        'event-incidents-v5', 'event-incidents-v6', 'event-incidents-v7', 'event-incidents-v8',
        'event-incidents-v9', 'event-incidents-v10', 'event-incidents-v11', 'event-incidents-v12',
        'event-incidents-v13', 'event-incidents-v14', 'event-incidents-v15', 'event-incidents-v16',
        'event-incidents-v17', 'event-incidents-unavailable-v1'))
    OR (endpoint_type = 'EVENT_LINEUPS' AND parser_version IN (
        'event-lineups-v1', 'event-lineups-v2', 'event-lineups-v3', 'event-lineups-unavailable-v1'))
);

COMMENT ON COLUMN j5_event_lineup_player.captain IS
    'Optional provider captain signal: NULL is unobserved, false and true remain distinct.';
COMMENT ON COLUMN j5_event_lineup_player.statistics IS
    'V3 normalized bounded decimal maps; NULL differs from an explicitly empty statistics object. No raw player payload.';
COMMENT ON COLUMN j5_event_lineup_side.missing_players IS
    'V3 normalized missing-player descriptors in source order; NULL differs from an explicit empty list. No historical backfill.';
COMMENT ON COLUMN j5_event_data_observation.parser_version IS
    'Versioned normalizer. event-lineups-v3 preserves optional captain, individual statistics and team missing players; older observations remain unchanged.';
