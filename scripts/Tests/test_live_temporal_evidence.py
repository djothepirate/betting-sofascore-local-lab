"""Hand-calculated, synthetic cases for the WO-058 offline temporal analysis."""

from copy import deepcopy
import importlib.util
from pathlib import Path
import unittest


SPEC = importlib.util.spec_from_file_location(
    "live_temporal", Path(__file__).resolve().parents[1] / "Analyze-LiveTemporalEvidence.py")
TEMPORAL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TEMPORAL)


def stamp(minutes=0, seconds=0):
    return f"2026-09-07T19:{minutes:02}:{seconds:02}Z"


def attempt(ordinal, minute, endpoint="EVENT_STATISTICS", **changes):
    row = {
        "attempt_id": f"attempt-{ordinal}", "ordinal": ordinal, "cycle_number": ordinal,
        "campaign_id": "campaign", "canonical_event_id": "event", "endpoint": endpoint,
        "kind": "J5_NORMAL", "reserved_at": stamp(minute), "received_at": stamp(minute, 1),
        "resolved_at": stamp(minute, 2), "successful": True, "outcome": "PARSED", "code": "PARSED",
        "snapshot_id": 1, "occurrence_id": ordinal, "payload_sha256": "a" * 64,
        "normalized_sha256": "b" * 64, "projection_sha256": "c" * 64,
        "projection_version": "projection-v1", "parser_version": "parser-v1",
        "completeness_status": "COMPLETE", "completeness_score": 100,
    }
    row.update(changes)
    return row


def evidence(attempts):
    return {
        "contract": "wo058-temporal-metadata-v1", "provider": "SOFASCORE", "captured_at": stamp(59),
        "campaigns": [{"campaign_id": "campaign", "started_at": stamp(),
                       "policy_version": "live-v2", "cycle_interval_seconds": 450, "state": "STOPPED_ERROR"}],
        "targets": [{"campaign_id": "campaign", "canonical_event_id": "event", "provider_event_id": 900001,
                     "title": "Synthetic Home — Synthetic Away", "starts_at": stamp(), "state": "STOPPED_ERROR"}],
        "attempts": attempts,
        "transitions": [
            {"campaign_id": "campaign", "canonical_event_id": None, "revision": 1,
             "state": "RUNNING", "changed_at": stamp()},
            {"campaign_id": "campaign", "canonical_event_id": "event", "revision": 2,
             "state": "COLLECTING", "changed_at": stamp()},
            {"campaign_id": "campaign", "canonical_event_id": "event", "revision": 3,
             "state": "RECEIVED", "changed_at": stamp(1)},
            {"campaign_id": "campaign", "canonical_event_id": "event", "revision": 4,
             "state": "STOPPED_ERROR", "changed_at": stamp(40)},
        ],
    }


def families_at(rows, checkpoint=stamp(16)):
    report = TEMPORAL.analyze(evidence(rows), [checkpoint])
    match = report["checkpoints"][0]["campaigns"][0]["matches"][0]
    return {row["family"]: row for row in match["families"]}


class TemporalEvidenceTest(unittest.TestCase):
    def test_identical_receptions_refresh_age_without_resetting_observed_change(self):
        result = families_at([attempt(1, 0), attempt(2, 7), attempt(3, 15)])["EVENT_STATISTICS"]
        self.assertEqual(59, result["readable_data_age_seconds"])
        self.assertEqual(959, result["unchanged_observation_duration_seconds"])
        self.assertEqual(stamp(0, 1), result["last_changed_at"])
        self.assertEqual(3, result["consecutive_identical_successes"])
        self.assertEqual(3, result["readable_data"]["occurrence_id"])
        self.assertEqual(1, result["readable_data"]["snapshot_id"])
        self.assertEqual("UNCLASSIFIED", result["freshness_class"])

    def test_raw_only_change_does_not_reset_semantic_change(self):
        result = families_at([attempt(1, 0), attempt(2, 7, payload_sha256="d" * 64, snapshot_id=2)])[
            "EVENT_STATISTICS"]
        self.assertEqual(stamp(0, 1), result["last_changed_at"])
        self.assertEqual(2, result["consecutive_identical_successes"])

    def test_normalized_or_projection_change_resets_observation_and_streak(self):
        for change in ({"normalized_sha256": "d" * 64}, {"projection_sha256": "d" * 64},
                       {"projection_version": "projection-v2"}):
            with self.subTest(change=change):
                result = families_at([attempt(1, 0), attempt(2, 7, **change)])["EVENT_STATISTICS"]
                self.assertEqual(stamp(7, 1), result["last_changed_at"])
                self.assertEqual(539, result["unchanged_observation_duration_seconds"])
                self.assertEqual(1, result["consecutive_identical_successes"])

    def test_parser_change_resets_streak_but_preserves_existing_cursor_semantics(self):
        result = families_at([attempt(1, 0), attempt(2, 7, parser_version="parser-v2")])["EVENT_STATISTICS"]
        self.assertEqual(stamp(0, 1), result["last_changed_at"])
        self.assertEqual(1, result["consecutive_identical_successes"])

    def test_recent_unavailable_receipt_does_not_freshen_older_readable_data(self):
        rows = [attempt(1, 0), attempt(2, 15, successful=False, outcome="ENDPOINT_UNAVAILABLE", code="HTTP_404",
                                       completeness_status="UNAVAILABLE", completeness_score=0, snapshot_id=2)]
        result = families_at(rows)["EVENT_STATISTICS"]
        self.assertEqual("UNAVAILABLE", result["latest_attempt_status"])
        self.assertEqual(59, result["latest_receipt_age_seconds"])
        self.assertEqual(959, result["readable_data_age_seconds"])
        self.assertEqual(1, result["readable_data"]["snapshot_id"])
        self.assertEqual(2, result["latest_receipt"]["snapshot_id"])
        self.assertEqual("COMPLETE", result["readable_data"]["completeness_status"])
        self.assertEqual("UNAVAILABLE", result["latest_published_result"]["completeness_status"])
        self.assertTrue(result["readable_data_from_previous_attempt"])
        self.assertEqual(0, result["consecutive_identical_successes"])
        self.assertEqual("BROKEN_BY_UNSUCCESSFUL_RESULT", result["consecutive_identical_successes_state"])

    def test_success_after_failed_result_restarts_streak_without_inventing_change(self):
        rows = [attempt(1, 0), attempt(2, 7, successful=False, outcome="HTTP_ERROR", code="HTTP_403"),
                attempt(3, 15)]
        result = families_at(rows)["EVENT_STATISTICS"]
        self.assertEqual(1, result["consecutive_identical_successes"])
        self.assertEqual(stamp(0, 1), result["last_changed_at"])

    def test_unpublished_response_exposes_only_receipt_not_future_score_or_status(self):
        rows = [attempt(1, 0, "EVENT_DETAILS", home_score="0", sport_status="inprogress"),
                attempt(2, 15, "EVENT_DETAILS", resolved_at=stamp(17), home_score="1", sport_status="finished",
                        normalized_sha256="d" * 64)]
        result = families_at(rows)["EVENT_DETAILS"]
        self.assertEqual("PENDING", result["latest_attempt_status"])
        self.assertEqual(2, result["latest_receipt"]["occurrence_id"])
        self.assertEqual(1, result["readable_data"]["occurrence_id"])
        self.assertEqual("0", result["readable_content"]["home_score"])
        self.assertEqual("inprogress", result["readable_content"]["sport_status"])
        self.assertIsNone(result["consecutive_identical_successes"])
        self.assertEqual("PENDING_RESULT", result["consecutive_identical_successes_state"])
        self.assertEqual(stamp(0, 1), result["last_changed_at"])

    def test_before_any_attempt_is_not_requested_and_has_no_invented_zero_age(self):
        result = families_at([attempt(1, 15)], stamp(10))["EVENT_STATISTICS"]
        self.assertEqual("NOT_REQUESTED", result["latest_attempt_status"])
        self.assertIsNone(result["readable_data_age_seconds"])
        self.assertIsNone(result["readable_data"])
        self.assertIsNone(result["consecutive_identical_successes"])

    def test_failed_first_attempt_is_distinct_from_not_requested(self):
        result = families_at([attempt(1, 0, successful=False, outcome="HTTP_ERROR", code="HTTP_403")])[
            "EVENT_STATISTICS"]
        self.assertEqual("FAILED", result["latest_attempt_status"])
        self.assertIsNone(result["readable_data"])
        self.assertEqual(0, result["consecutive_identical_successes"])

    def test_temporal_spread_excludes_lineups_only_from_dynamic_group(self):
        rows = [attempt(1, 1, "EVENT_LINEUPS", lineups_confirmed=True, home_starters=11, away_starters=11),
                attempt(2, 10, "EVENT_STATISTICS"), attempt(3, 14, "EVENT_INCIDENTS"),
                attempt(4, 15, "EVENT_DETAILS")]
        report = TEMPORAL.analyze(evidence(rows), [stamp(16)])
        match = report["checkpoints"][0]["campaigns"][0]["matches"][0]
        self.assertEqual(300, match["temporal_spread_dynamic"]["seconds"])
        self.assertEqual(3, match["temporal_spread_dynamic"]["available_family_count"])
        self.assertEqual(840, match["temporal_spread_all_four"]["seconds"])
        self.assertEqual(4, match["temporal_spread_all_four"]["available_family_count"])
        self.assertEqual({"lineups_confirmed": True, "home_starters": 11, "away_starters": 11},
                         match["families"][3]["readable_content"])

    def test_missing_families_do_not_produce_a_false_zero_spread(self):
        report = TEMPORAL.analyze(evidence([attempt(1, 0)]), [stamp(16)])
        spread = report["checkpoints"][0]["campaigns"][0]["matches"][0]["temporal_spread_dynamic"]
        self.assertIsNone(spread["seconds"])
        self.assertEqual(1, spread["available_family_count"])
        self.assertEqual(3, spread["expected_family_count"])

    def test_timezone_equivalence_and_historical_lifecycle_ignore_capture_state(self):
        base = evidence([attempt(1, 0)])
        first = TEMPORAL.analyze(base, ["2026-09-07T21:16:00+02:00"])
        self.assertEqual(first, TEMPORAL.analyze(base, [stamp(16)]))
        campaign = first["checkpoints"][0]["campaigns"][0]
        self.assertEqual("RUNNING", campaign["lifecycle"]["state"])
        self.assertEqual("COLLECTING", campaign["matches"][0]["lifecycle"]["state"])

    def test_terminal_ages_keep_growing_at_evaluation_time(self):
        report = TEMPORAL.analyze(evidence([attempt(1, 0)]), [stamp(45), stamp(50)])
        families = [point["campaigns"][0]["matches"][0]["families"][1] for point in report["checkpoints"]]
        self.assertEqual(2699, families[0]["readable_data_age_seconds"])
        self.assertEqual(2999, families[1]["readable_data_age_seconds"])

    def test_unknown_comparison_metadata_is_explicit(self):
        result = families_at([attempt(1, 0, normalized_sha256=None)])["EVENT_STATISTICS"]
        self.assertIsNone(result["last_changed_at"])
        self.assertIsNone(result["consecutive_identical_successes"])
        self.assertEqual("MISSING_COMPARISON_METADATA", result["consecutive_identical_successes_state"])

    def test_invalid_checkpoint_and_contract_are_rejected(self):
        for checkpoint in ("2026-09-07T19:16:00", "2026-09-07T20:00:00Z"):
            with self.subTest(checkpoint=checkpoint), self.assertRaises(ValueError):
                TEMPORAL.analyze(evidence([]), [checkpoint])
        invalid = deepcopy(evidence([]))
        invalid["provider"] = "OTHER"
        with self.assertRaises(ValueError):
            TEMPORAL.analyze(invalid, [stamp(16)])


if __name__ == "__main__":
    unittest.main()
