#!/usr/bin/env python3
"""WO-058: replay minimized local ledger metadata at explicit historical instants.

Standard library only; no network or database access. Input is the JSON emitted by
docs/validation/WO058-TEMPORAL-EXTRACT-20260907.sql. This describes observations in
the selected live campaigns, not every observation available elsewhere in the Lab.
"""

import argparse
from collections import defaultdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path


FAMILIES = ("EVENT_DETAILS", "EVENT_STATISTICS", "EVENT_INCIDENTS", "EVENT_LINEUPS")
INPUT_CONTRACT = "wo058-temporal-metadata-v1"
OUTPUT_CONTRACT = "wo058-temporal-analysis-v1"
LIFECYCLE_STATES = {
    "PREPARED", "RUNNING", "WAITING_START", "COLLECTING", "CHECKING_FINISH",
    "FINALIZING", "FINISHED_CONFIRMED", "COMPLETED", "CLEANUP_REQUIRED", "INTERRUPTED",
}


def instant(value):
    """Require an explicit timezone; normalize offsets without using the host locale."""
    if not isinstance(value, str):
        raise ValueError("timestamps must be ISO strings with explicit timezone")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("timestamps require an explicit timezone")
    return parsed.astimezone(timezone.utc)


def iso(value):
    return value.isoformat().replace("+00:00", "Z") if value is not None else None


def known(row, field, at):
    return row.get(field) is not None and instant(row[field]) <= at


def age(value, at):
    return round((at - instant(value)).total_seconds(), 6) if value is not None else None


def semantic_key(row):
    # Mirrors JdbcLiveCampaignStore.cursors: normalized hash + projection version
    # + projection JSON. The extraction provides the stored hash of that JSON.
    if not row.get("normalized_sha256"):
        return None
    if (row.get("projection_version") is None) != (row.get("projection_sha256") is None):
        return None
    return (row["normalized_sha256"], row.get("projection_version"), row.get("projection_sha256"))


def reference(row, publication=False):
    if row is None:
        return None
    fields = ["attempt_id", "snapshot_id", "occurrence_id", "payload_sha256", "received_at"]
    if publication:
        fields += ["resolved_at", "parser_version", "normalized_sha256", "projection_version",
                   "projection_sha256", "completeness_status", "completeness_score"]
    return {field: row.get(field) for field in fields}


def content(row, endpoint):
    if row is None:
        return None
    if endpoint == "EVENT_DETAILS":
        return {key: row.get(key) for key in ("sport_status", "status_description", "home_score", "away_score")}
    if endpoint == "EVENT_LINEUPS":
        return {key: row.get(key) for key in ("lineups_confirmed", "home_starters", "away_starters")}
    return None  # No raw statistics/incidents payload is copied into this report.


def family_at(attempts, endpoint, at):
    visible = [row for row in attempts if row["endpoint"] == endpoint and known(row, "reserved_at", at)]
    visible.sort(key=lambda row: row["ordinal"])
    published = [row for row in visible if known(row, "resolved_at", at)]
    received = [row for row in visible if known(row, "received_at", at)]
    latest = visible[-1] if visible else None
    latest_result = published[-1] if published else None
    latest_receipt = received[-1] if received else None
    readable = changed = previous_key = None
    streak_key = None
    streak_count = None
    streak_state = "NO_OBSERVATION"
    for row in visible:
        if not known(row, "resolved_at", at):
            streak_key, streak_count, streak_state = None, None, "PENDING_RESULT"
            continue
        if row.get("successful") is not True:
            streak_key, streak_count, streak_state = None, 0, "BROKEN_BY_UNSUCCESSFUL_RESULT"
            continue
        readable = row
        next_key = semantic_key(row)
        if next_key is None:
            changed = previous_key = None
        else:
            if previous_key != next_key:
                changed = row
            previous_key = next_key
        if next_key is None or not row.get("parser_version"):
            streak_key, streak_count, streak_state = None, None, "MISSING_COMPARISON_METADATA"
        else:
            next_streak_key = (row["parser_version"],) + next_key
            streak_count = streak_count + 1 if streak_key == next_streak_key else 1
            streak_key, streak_state = next_streak_key, "KNOWN"
    if latest is None:
        latest_status = "NOT_REQUESTED"
    elif not known(latest, "resolved_at", at):
        latest_status = "PENDING"
    elif latest.get("successful") is True:
        latest_status = "PARSED"
    elif latest.get("outcome") in ("UNAVAILABLE", "ENDPOINT_UNAVAILABLE"):
        latest_status = "UNAVAILABLE"
    else:
        latest_status = "FAILED"
    result_summary = None if latest_result is None else {
        key: latest_result.get(key) for key in (
            "attempt_id", "resolved_at", "outcome", "code", "successful",
            "completeness_status", "completeness_score")
    }
    return {
        "family": endpoint,
        "latest_attempt_status": latest_status,
        "reserved_attempt_count": len(visible),
        "published_result_count": len(published),
        "latest_attempt_id": latest.get("attempt_id") if latest else None,
        "latest_attempt_reserved_at": latest.get("reserved_at") if latest else None,
        "latest_published_result": result_summary,
        "latest_receipt": reference(latest_receipt),
        "latest_receipt_age_seconds": age(latest_receipt.get("received_at"), at) if latest_receipt else None,
        "readable_data": reference(readable, publication=True),
        "readable_content": content(readable, endpoint),
        "readable_data_age_seconds": age(readable.get("received_at"), at) if readable else None,
        "readable_data_from_previous_attempt": readable is not None and readable is not latest,
        "last_changed_at": changed.get("received_at") if changed else None,
        "last_changed_published_at": changed.get("resolved_at") if changed else None,
        "last_changed_attempt_id": changed.get("attempt_id") if changed else None,
        "unchanged_observation_duration_seconds": age(changed.get("received_at"), at) if changed else None,
        "consecutive_identical_successes": streak_count,
        "consecutive_identical_successes_state": streak_state,
        "freshness_class": "UNCLASSIFIED",
        "freshness_policy_required": True,
    }


def spread(families, endpoints):
    available = [family for family in families
                 if family["family"] in endpoints and family["readable_data"] is not None]
    received = [instant(family["readable_data"]["received_at"]) for family in available]
    return {
        "seconds": round((max(received) - min(received)).total_seconds(), 6) if len(received) >= 2 else None,
        "available_family_count": len(received),
        "expected_family_count": len(endpoints),
        "available_families": [family["family"] for family in available],
        "oldest_received_at": iso(min(received)) if received else None,
        "newest_received_at": iso(max(received)) if received else None,
        "coherence_class": "UNCLASSIFIED",
    }


def lifecycle_at(transitions, event_id, at):
    states = [row for row in transitions if row.get("canonical_event_id") == event_id
              and known(row, "changed_at", at)
              and (row["state"] in LIFECYCLE_STATES or row["state"].startswith("STOPPED"))]
    latest = max(states, key=lambda row: row["revision"]) if states else None
    return {"state": latest["state"] if latest else "UNKNOWN",
            "changed_at": latest["changed_at"] if latest else None,
            "reason": latest.get("reason") if latest else None}


def analyze(evidence, checkpoints, input_sha256=None):
    if evidence.get("contract") != INPUT_CONTRACT or evidence.get("provider") != "SOFASCORE":
        raise ValueError("unsupported metadata contract or provider")
    captured_at = instant(evidence["captured_at"])
    checkpoints = sorted(set(instant(value) for value in checkpoints))
    if not checkpoints or checkpoints[-1] > captured_at:
        raise ValueError("explicit checkpoints must not exceed the evidence capture time")
    by_campaign = defaultdict(list)
    by_target = defaultdict(list)
    transitions = defaultdict(list)
    for target in evidence["targets"]:
        by_campaign[target["campaign_id"]].append(target)
    seen_ids = set()
    for row in evidence["attempts"]:
        if row["attempt_id"] in seen_ids or row["endpoint"] not in FAMILIES:
            raise ValueError("duplicate attempt or family outside the live contract")
        seen_ids.add(row["attempt_id"])
        if known(row, "resolved_at", captured_at) and row.get("successful") is True:
            if not known(row, "received_at", instant(row["resolved_at"])):
                raise ValueError("successful publication requires a prior receipt")
        by_target[(row["campaign_id"], row["canonical_event_id"])].append(row)
    for row in evidence["transitions"]:
        transitions[row["campaign_id"]].append(row)
    evaluations = []
    for at in checkpoints:
        campaigns = []
        for campaign in evidence["campaigns"]:
            if not known(campaign, "started_at", at):
                continue  # Do not disclose campaigns not launched at the checkpoint.
            campaign_id = campaign["campaign_id"]
            matches = []
            for target in sorted(by_campaign[campaign_id], key=lambda row: row["provider_event_id"]):
                event_id = target["canonical_event_id"]
                families = [family_at(by_target[(campaign_id, event_id)], endpoint, at) for endpoint in FAMILIES]
                matches.append({
                    "canonical_event_id": event_id, "provider_event_id": target["provider_event_id"],
                    "title": target["title"], "starts_at": target["starts_at"],
                    "lifecycle": lifecycle_at(transitions[campaign_id], event_id, at),
                    "families": families,
                    "temporal_spread_dynamic": spread(families, FAMILIES[:3]),
                    "temporal_spread_all_four": spread(families, FAMILIES),
                })
            campaigns.append({
                "campaign_id": campaign_id, "policy_version": campaign["policy_version"],
                "cycle_interval_seconds": campaign["cycle_interval_seconds"],
                "started_at": campaign["started_at"],
                "lifecycle": lifecycle_at(transitions[campaign_id], None, at), "matches": matches,
            })
        evaluations.append({"evaluated_at": iso(at), "campaigns": campaigns})
    return {
        "contract": OUTPUT_CONTRACT, "provider": evidence["provider"],
        "source_contract": evidence["contract"], "source_captured_at": evidence["captured_at"],
        "source_sha256": input_sha256,
        "scope": "SELECTED_LOCAL_LIVE_CAMPAIGNS_ONLY",
        "limits": [
            "Only results resolved by the checkpoint can supply readable content; a receipt alone cannot.",
            "Times are ledger source timestamps, not a reconstruction of database commit visibility.",
            "Ages continue to increase after campaign termination; no UI frozen-age rule is used.",
            "Unchanged duration measures time since the last observed semantic change, not continuous source immutability.",
            "Identical-success streak includes its first observation and requires the same parser version.",
            "Dynamic spread excludes lineups; missing families are counted and never treated as simultaneous.",
            "Freshness/coherence classifications need a usage-specific policy; no thresholds are inferred.",
            "No manual J4/J5, earlier campaigns, Highlightly or other provider evidence is inferred.",
        ],
        "checkpoints": evaluations,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="minimized JSON metadata extraction")
    parser.add_argument("--at", action="append", required=True, help="ISO evaluation time with timezone; repeatable")
    parser.add_argument("--output", type=Path, required=True, help="analysis JSON output")
    args = parser.parse_args()
    try:
        if args.input.resolve() == args.output.resolve():
            raise ValueError("output must not overwrite the input evidence")
        raw = args.input.read_bytes()
        report = analyze(json.loads(raw.decode("utf-8-sig")), args.at, hashlib.sha256(raw).hexdigest())
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.error(str(error))
    print(f"Offline temporal analysis written: {len(report['checkpoints'])} checkpoints")


if __name__ == "__main__":
    main()
