"""Small synthetic-only fixtures for publish_c_run02; no real evidence is published."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import uuid

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('publisher_under_test', HERE / 'publish_c_run02.py')
pub = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = pub
SPEC.loader.exec_module(pub)
RUNROOT = HERE / 'c-run02-publisher-synthetic' / str(uuid.uuid4())
RUNROOT.mkdir(parents=True)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(pub.encode(value) if not isinstance(value, bytes) else value)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def manifest(root, filename, **fields):
    files = [{'path': p.relative_to(root).as_posix(), 'bytes': len(p.read_bytes()), 'sha256': pub.digest(p.read_bytes())}
             for p in sorted(root.rglob('*')) if p.is_file() and p.name != filename]
    write(root / filename, {**fields, 'files': files})


def fake_provenance(root, candidate, source_head):
    return dict(publisher_source_head='2' * 40, candidate_body_commit='1' * 40, candidate_package_commit='1' * 40)


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.root = RUNROOT / self._testMethodName
        self.root.mkdir()
        self.contracts = copy.deepcopy(pub.CONTRACTS)
        self.launcher = b'# synthetic launcher; never executed\n'
        self.mock_tests = b'# synthetic source representing the existing 17 mocks\n'
        self.patches = [patch.object(pub, 'CONTRACTS', self.contracts),
                        patch.object(pub, 'LAUNCHER_SHA', pub.digest(self.launcher)),
                        patch.object(pub, 'LAUNCHER_TEST_SHA', pub.digest(self.mock_tests)),
                        patch.object(pub.subprocess, 'run', side_effect=AssertionError('External process forbidden in synthetic tests')),
                        patch.object(pub.subprocess, 'check_output', side_effect=AssertionError('External process forbidden in synthetic tests'))]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)
        self.create_fixture()

    def create_fixture(self):
        temp = self.root / '.tmp'
        write(temp / 'wo062_evaluate_c.py', self.launcher)
        write(temp / 'test_c_harness.py', self.mock_tests)
        write(temp / 'c-harness-review.md', b'SYNTHETIC harness review, not a real qualification.\n')
        write(temp / 'c-harness-review.json', {'status': 'LOCAL_CORRECTIONS_VERIFIED', 'reviewed_launcher': {'final_sha256': pub.LAUNCHER_SHA}, 'validation': {'test_script_sha256': pub.LAUNCHER_TEST_SHA, 'tests_run': 17, 'failures': 0, 'errors': 0, 'exit_code': 0, 'model_calls': 0}})
        write(temp / 'c-harness-synthetic-results.json', {'launcher_sha256': pub.LAUNCHER_SHA, 'tests_run': 17, 'failures': 0, 'errors': 0, 'success': True, 'model_calls': 0, 'external_process_calls': 0})
        for skill, c in self.contracts.items():
            body = ('---\nname: ' + skill + '\nmetadata:\n  version: "' + c['version'] + '"\n---\nSynthetic only.\n').encode()
            yaml = ('interface:\n  default_prompt: "Use $' + skill + '"\n').encode()
            c['sha256'] = pub.digest(body)
            c['yaml_sha256'] = pub.digest(yaml)
            c['old_sha256'] = pub.digest(b'SYNTHETIC old Java') if skill == 'ss-java-module' else c['sha256']
            candidate = self.root / 'docs/skills/local-lab' / skill
            write(candidate / 'SKILL.md', body)
            write(candidate / 'agents/openai.yaml', yaml)
            prep = self.root / 'docs/skills/evaluations/WO-062' / skill
            write(prep / 'manifest.json', {'source_commit': '1' * 40, 'synthetic': True})
            write(prep / 'qualification.json', {'human_validation': False, 'personal_installation': False, 'synthetic': True})
            runtime = self.root / '.tmp/wo062-evaluations' / skill / 'run-02'
            pre = {'skill': skill, 'run_id': 'run-02', 'candidate_version': c['version'], 'candidate_sha256': c['sha256'], 'source_head': '1' * 40, 'case_count': 8, 'preparation_manifest_sha256': pub.digest((prep / 'manifest.json').read_bytes()), 'cases': []}
            for cid in pub.ids(c):
                base = runtime / cid
                (base / 'output').mkdir(parents=True)
                entries = []
                for rel, content in [('.agents/skills/' + skill + '/SKILL.md', body), ('.agents/skills/' + skill + '/agents/openai.yaml', yaml)]:
                    write(base / rel, content)
                    entries.append({'path': rel, 'sha256': pub.digest(content), 'bytes': len(content)})
                prompt = ('SYNTHETIC prompt ' + cid + '\n').encode()
                request = ('SYNTHETIC request ' + cid + '\n').encode()
                write(base / 'case-prompt.txt', prompt)
                write(base / 'request.txt', request)
                case = {'case_id': cid, 'kind': 'selection' if '-S' in cid else 'synthetic', 'execution_mode': 'bounded_local_runtime' if skill == 'ss-windows-runtime' and cid in pub.ids(c, True) else 'read_only', 'input_files': entries, 'prompt_sha256': pub.digest(prompt), 'request_sha256': pub.digest(request), 'oracle_supplied': False, 'design_history_supplied': False, 'candidate_body_manually_pasted': False}
                pre['cases'].append(case)
                if cid in pub.ids(c, True):
                    response = 'SYNTHETIC reviewed response ' + cid
                    events = [{'type': 'thread.started', 'thread_id': 'synthetic-' + cid}, {'type': 'turn.started'}, {'type': 'item.completed', 'item': {'type': 'command_execution', 'id': 'read', 'command': 'SYNTHETIC READ ONLY', 'exit_code': 0, 'aggregated_output': body.decode()}}, {'type': 'item.completed', 'item': {'type': 'agent_message', 'text': response}}, {'type': 'turn.completed'}]
                    raw = b''.join(pub.encode(e) .replace(b'\n', b'') + b'\n' for e in events)
                    write(base / 'output/events.jsonl', raw)
                    frozen = runtime / 'frozen' / cid
                    for name, content in [('prompt.txt', prompt), ('request.txt', request), ('response.md', response.encode()), ('catalogue.txt', b'SYNTHETIC catalogue\n'), ('native-events.json', pub.encode(events))]:
                        write(frozen / name, content)
                    sandbox = 'workspace-write' if case['execution_mode'] == 'bounded_local_runtime' else 'read-only'
                    trace = {'case_id': cid, 'skill': skill, 'run_id': 'run-02', 'candidate_sha256': c['sha256'], 'source_head': '1' * 40, 'input_files': entries, 'execution_mode': case['execution_mode'], 'oracle_supplied': False, 'design_history_supplied': False, 'automatic_retry': False, 'model': None, 'reasoning_effort': None, 'launch_arguments': ['exec', '--ephemeral', '--sandbox', sandbox, '--cd', str(base), '--json', '--output-last-message', str(base / 'output/response.md'), '-'], 'raw_events_sha256': pub.digest(raw), 'commands_completed': 1, 'exit_code': 0, 'collector_exit_code': 0, 'collection_completed': True, 'turn_completed': True, 'timed_out': False, 'diagnostics': []}
                    for name, field in [('prompt.txt', 'prompt_sha256'), ('request.txt', 'request_sha256'), ('response.md', 'response_sha256'), ('native-events.json', 'native_events_sha256'), ('catalogue.txt', 'catalogue_sha256')]:
                        trace[field] = pub.digest((frozen / name).read_bytes())
                    write(frozen / 'trace.json', trace)
                    manifest(frozen, 'freeze.json', before_independent_review=True)
                    self.review(runtime, cid, c['sha256'], response, 'PASS')
            write(runtime / 'preflight.json', pre)
            static = {'candidate_sha256': c['sha256'], 'candidate_version': c['version'], 'candidate_yaml_sha256': c['yaml_sha256'], 'verdict': 'PASS', 'synthetic': True}
            if skill == 'ss-java-module':
                write(runtime / 'review-static.json', static)
                write(runtime / 'review-static.md', b'SYNTHETIC static review\n')
            old = prep / 'run-01'
            old_cases = []
            for cid, verdict in zip(pub.ids(c), c['old_verdicts']):
                response = 'SYNTHETIC historical ' + cid
                frozen = old / 'cases' / cid
                write(frozen / 'response.md', response.encode())
                manifest(frozen, 'freeze.json', before_independent_review=True)
                self.review(old, cid, c['old_sha256'], response, verdict)
                old_cases.append({'case_id': cid, 'verdict': verdict, 'review': 'review-' + cid + '.json', 'response': 'cases/' + cid + '/response.md', 'response_sha256': pub.digest(response.encode())})
            write(old / 'review-static.json', static)
            write(old / 'review-static.md', b'SYNTHETIC historical static review\n')
            write(old / 'results.json', {'skill': skill, 'run_id': 'run-01', 'candidate_sha256': c['old_sha256'], 'case_count': 8, 'cases': old_cases, **pub.counts(old_cases)})
            manifest(old, 'artifact-manifest.json')
            c['old_manifest_sha256'] = pub.digest((old / 'artifact-manifest.json').read_bytes())
        write(temp / 'c-requalification-authorization.json', {'authorized': True, 'owner_reply': 'J’autorise ces dix sessions', 'additional_model_sessions': 10, 'first_sixteen_responses_preserved': True, 'human_validation': False, 'personal_installation': False, 'order': [{'skill': s, 'version': c['version'], 'run_id': 'run-02', 'cases': pub.ids(c, True)} for s, c in self.contracts.items()]})

    def review(self, base, cid, candidate_hash, response, verdict):
        write(base / ('review-' + cid + '.json'), {'case_id': cid, 'candidate_sha256': candidate_hash, 'response_sha256': pub.digest(response.encode()), 'verdict': verdict, 'synthetic': True})
        write(base / ('review-' + cid + '.md'), b'SYNTHETIC independent review\n')

    def runtime(self, skill='ss-java-module'):
        return self.root / '.tmp/wo062-evaluations' / skill / 'run-02'

    def build(self, skill='ss-java-module'):
        return pub.build_plan(self.root, skill, fake_provenance)

    def change_json(self, path, mutate):
        value = read(path)
        mutate(value)
        write(path, value)

    def refreeze(self, cid='JM-H01', skill='ss-java-module'):
        manifest(self.runtime(skill) / 'frozen' / cid, 'freeze.json', before_independent_review=True)

    def rejected(self, skill='ss-java-module'):
        before = {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        with self.assertRaises((pub.EvidenceError, FileNotFoundError)):
            self.build(skill)
        after = {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.assertEqual(before, after)

    def test_java_publish_current_candidate_only_and_history_immutable(self):
        old = self.root / 'docs/skills/evaluations/WO-062/ss-java-module/run-01'
        before = {p.relative_to(old).as_posix(): p.read_bytes() for p in old.rglob('*') if p.is_file()}
        plan = self.build()
        result = pub.publish(plan)
        self.assertEqual(result['case_count'], 8)
        self.assertEqual(plan.qualification['executions'], 8)
        self.assertEqual(plan.qualification['total_executions'], 16)
        self.assertEqual(plan.qualification['historical_runs'][0]['fail'], 3)
        self.assertEqual(plan.qualification['pass'], 8)
        self.assertEqual(before, {p.relative_to(old).as_posix(): p.read_bytes() for p in old.rglob('*') if p.is_file()})
        self.assertFalse(plan.qualification['human_validation'])
        pub.verify_manifest(pub.Reader(), plan.destination, 'artifact-manifest.json')

    def test_windows_aggregate_and_six_prepared_cases_excluded(self):
        plan = self.build('ss-windows-runtime')
        pub.publish(plan)
        results = read(plan.destination / 'results.json')
        self.assertEqual(results['case_count'], 2)
        self.assertEqual(len(results['excluded_prepared_cases']), 6)
        self.assertEqual(set(p.name for p in (plan.destination / 'cases').iterdir()), {'WR-H01', 'WR-N01'})
        self.assertEqual(plan.qualification['executions'], 10)
        self.assertEqual(plan.qualification['pass'], 8)
        self.assertEqual(plan.qualification['historical_runs'][0]['blocked'], 2)
        self.assertEqual(plan.qualification['execution_totals']['blocked'], 2)
        self.assertEqual(sum(x['source_run'] == 'run-01' for x in plan.qualification['current_cases']), 6)

    def test_fail_verdict_is_preserved(self):
        self.change_json(self.runtime() / 'review-JM-H01.json', lambda x: x.update(verdict='FAIL'))
        plan = self.build()
        self.assertEqual((plan.qualification['pass'], plan.qualification['fail']), (7, 1))
        self.assertEqual(plan.qualification['status'], 'INCOMPLETE_QUALIFICATION')

    def test_blocked_windows_verdict_is_preserved(self):
        self.change_json(self.runtime('ss-windows-runtime') / 'review-WR-N01.json', lambda x: x.update(verdict='BLOCKED'))
        plan = self.build('ss-windows-runtime')
        self.assertEqual((plan.qualification['pass'], plan.qualification['blocked']), (7, 1))
        self.assertEqual(plan.qualification['execution_totals']['blocked'], 3)

    def test_false_pass_collector_refused(self):
        self.change_json(self.runtime() / 'frozen/JM-H01/trace.json', lambda x: x.update(collector_exit_code=1, collection_completed=False))
        self.refreeze()
        self.rejected()

    def test_static_fail_cannot_yield_qualified_status(self):
        self.change_json(self.runtime() / 'review-static.json', lambda x: x.update(verdict='FAIL'))
        plan = self.build()
        self.assertEqual(plan.qualification['pass'], 8)
        self.assertEqual(plan.qualification['status'], 'INCOMPLETE_QUALIFICATION')

    def test_reviewed_blocked_missing_response_is_not_fabricated(self):
        frozen = self.runtime() / 'frozen/JM-H01'
        (frozen / 'response.md').unlink()
        self.change_json(frozen / 'trace.json', lambda x: x.update(response_sha256=None, collection_completed=False, collector_exit_code=1))
        self.refreeze()
        self.change_json(self.runtime() / 'review-JM-H01.json', lambda x: x.update(response_sha256=None, verdict='BLOCKED'))
        plan = self.build()
        self.assertEqual(plan.qualification['blocked'], 1)
        self.assertIsNone(json.loads(plan.files['results.json'])['cases'][0]['response'])

    def test_historical_candidate_package_details_preserved(self):
        c = self.contracts['ss-java-module']
        history = {'run_id': 'run-01', 'candidate_files': [{'path': 'SKILL.md', 'sha256': c['old_sha256'], 'bytes': 99}, {'path': 'agents/openai.yaml', 'sha256': c['yaml_sha256'], 'bytes': 55}], 'candidate_body_commit': '3' * 40}
        self.change_json(self.root / 'docs/skills/evaluations/WO-062/ss-java-module/qualification.json', lambda x: x.update(historical_runs=[history]))
        plan = self.build()
        self.assertEqual(plan.qualification['historical_runs'][0]['candidate_files'], history['candidate_files'])
        self.assertEqual(plan.qualification['historical_runs'][0]['candidate_body_commit'], '3' * 40)

    def test_wrong_review_case_refused(self):
        self.change_json(self.runtime() / 'review-JM-H01.json', lambda x: x.update(case_id='JM-N01'))
        self.rejected()

    def test_wrong_review_response_hash_refused(self):
        self.change_json(self.runtime() / 'review-JM-H01.json', lambda x: x.update(response_sha256='0' * 64))
        self.rejected()

    def test_wrong_review_candidate_hash_refused(self):
        self.change_json(self.runtime() / 'review-JM-H01.json', lambda x: x.update(candidate_sha256='0' * 64))
        self.rejected()

    def test_unlisted_frozen_file_refused(self):
        write(self.runtime() / 'frozen/JM-H01/extra.txt', b'unlisted')
        self.rejected()

    def test_frozen_hash_mismatch_refused(self):
        write(self.runtime() / 'frozen/JM-H01/response.md', b'tampered')
        self.rejected()

    def test_manifest_traversal_refused(self):
        self.change_json(self.runtime() / 'frozen/JM-H01/freeze.json', lambda x: x['files'].append({'path': '../escape.txt', 'bytes': 1, 'sha256': '0' * 64}))
        self.rejected()

    def test_duplicate_manifest_entry_refused(self):
        self.change_json(self.runtime() / 'frozen/JM-H01/freeze.json', lambda x: x['files'].append(x['files'][0]))
        self.rejected()

    def test_input_tamper_in_unexecuted_windows_copy_refused(self):
        write(self.runtime('ss-windows-runtime') / 'WR-S01/.agents/skills/ss-windows-runtime/SKILL.md', b'tampered')
        self.rejected('ss-windows-runtime')

    def test_unauthorized_windows_execution_refused(self):
        write(self.runtime('ss-windows-runtime') / 'WR-S01/output/events.jsonl', b'new unauthorized execution')
        self.rejected()

    def test_additional_run_refused(self):
        write(self.runtime().parent / 'run-03/JM-H01/output/events.jsonl', b'extra execution')
        self.rejected()

    def test_authorization_budget_change_refused(self):
        self.change_json(self.root / '.tmp/c-requalification-authorization.json', lambda x: x.update(additional_model_sessions=11))
        self.rejected()

    def test_authorized_case_change_refused(self):
        self.change_json(self.root / '.tmp/c-requalification-authorization.json', lambda x: x['order'][1]['cases'].append('WR-S01'))
        self.rejected()

    def test_java_workspace_write_mode_refused(self):
        self.change_json(self.runtime() / 'preflight.json', lambda x: x['cases'][0].update(execution_mode='bounded_local_runtime'))
        self.rejected()

    def test_static_for_wrong_candidate_refused(self):
        self.change_json(self.runtime() / 'review-static.json', lambda x: x.update(candidate_sha256='0' * 64))
        self.rejected()

    def test_launcher_change_refused(self):
        write(self.root / '.tmp/wo062_evaluate_c.py', b'tampered launcher')
        self.rejected()

    def test_missing_17_mock_success_refused(self):
        self.change_json(self.root / '.tmp/c-harness-synthetic-results.json', lambda x: x.update(tests_run=16))
        self.rejected()

    def test_resealed_history_tamper_refused(self):
        old = self.root / 'docs/skills/evaluations/WO-062/ss-java-module/run-01'
        write(old / 'review-JM-H01.md', b'tampered historical review')
        manifest(old, 'artifact-manifest.json')
        self.rejected()

    def test_raw_events_changed_refused(self):
        write(self.runtime() / 'JM-H01/output/events.jsonl', b'{}\n')
        self.rejected()

    def test_multiple_model_sessions_refused_even_when_resealed(self):
        base = self.runtime() / 'JM-H01/output/events.jsonl'
        raw = base.read_bytes() + b'{"type":"thread.started","thread_id":"another"}\n'
        write(base, raw)
        frozen = self.runtime() / 'frozen/JM-H01'
        self.change_json(frozen / 'trace.json', lambda x: x.update(raw_events_sha256=pub.digest(raw)))
        self.refreeze()
        self.rejected()

    def test_missing_authorized_case_refused(self):
        (self.runtime() / 'review-JM-S04.json').unlink()
        self.rejected()

    def test_existing_publication_refused(self):
        destination = self.root / 'docs/skills/evaluations/WO-062/ss-java-module/run-02'
        destination.mkdir()
        self.rejected()

    def test_evidence_change_after_plan_refused(self):
        plan = self.build()
        write(self.runtime() / 'review-JM-S04.md', b'changed after validation')
        with self.assertRaises(pub.EvidenceError):
            pub.publish(plan)
        self.assertFalse(plan.destination.exists())

    def test_qualification_replace_failure_rolls_back_only_new_directory(self):
        plan = self.build()
        original = plan.qualification_path.read_bytes()
        with patch.object(pub.os, 'replace', side_effect=OSError('synthetic write refusal')):
            with self.assertRaises(OSError):
                pub.publish(plan)
        self.assertFalse(plan.destination.exists())
        self.assertEqual(plan.qualification_path.read_bytes(), original)


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(PublisherTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    summary = {'schema': 'wo062-run02-publisher-synthetic-tests-v1', 'kind': 'SYNTHETIC_ONLY_NO_REAL_PUBLICATION', 'tests_run': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors), 'success': result.wasSuccessful(), 'model_calls': 0, 'external_process_calls': 0, 'real_run_publications': 0, 'fixture_root': str(RUNROOT), 'publisher_sha256': pub.digest((HERE / 'publish_c_run02.py').read_bytes()), 'test_script_sha256': pub.digest(Path(__file__).read_bytes())}
    write(RUNROOT / 'summary.json', summary)
    write(HERE / 'c-run02-publisher-synthetic-results.json', summary)
    print(json.dumps(summary))
    raise SystemExit(0 if result.wasSuccessful() else 1)
