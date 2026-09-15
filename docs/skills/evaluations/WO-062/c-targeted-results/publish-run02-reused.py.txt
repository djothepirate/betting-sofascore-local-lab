"""Validate and publish only the ten owner-authorized C run-02 evaluations.

No model, runtime probe, application test, network, or installation is invoked.
Default CLI operation is validation only; --publish is an explicit local write.
All input validation precedes creation of a published destination. Run-01 is read-only.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
import uuid

SUFFIXES = ('H01', 'N01', 'C01', 'C02', 'S01', 'S02', 'S03', 'S04')
CONTRACTS = {
    'ss-java-module': {
        'prefix': 'JM', 'version': '0.1.0-candidate.2',
        'sha256': '255655544771ae6cf2a71b967aba8efeb7e53e64acc41ca31dc784e16a719118',
        'old_sha256': 'd20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4',
        'old_manifest_sha256': '6b6b057a742ebd8f3b064adf4be7b57ac02ff9309ad76cf7b3599b7e72e56649',
        'yaml_sha256': 'ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae',
        'authorized_suffixes': SUFFIXES,
        'old_verdicts': ('FAIL', 'FAIL', 'PASS', 'FAIL', 'PASS', 'PASS', 'PASS', 'PASS'),
    },
    'ss-windows-runtime': {
        'prefix': 'WR', 'version': '0.1.0-candidate.1',
        'sha256': 'b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6',
        'old_sha256': 'b89925d5395238010bb0afe9c1e1a22b0aa5ec4e7d1fb9946c671d241e7976c6',
        'old_manifest_sha256': '9203e756a55fbba088f4d33beb42b79894edb0a2f0aa654565b7da3e54ee6bd8',
        'yaml_sha256': '3038015980952bc18b21f45f0d4af514b35a8841394e4b80824986b7e30ccd9f',
        'authorized_suffixes': ('H01', 'N01'),
        'old_verdicts': ('BLOCKED', 'BLOCKED', 'PASS', 'PASS', 'PASS', 'PASS', 'PASS', 'PASS'),
    },
}
LAUNCHER_SHA = '8a72b9b69d452bdcc545cc85ce4931beef5a58a41625eba3e12d9c240f44425f'
LAUNCHER_TEST_SHA = '62410fea11ea79632e6633c5f81b063ffb59677d2c3369bdff037b3e316c765b'
VERDICTS = {'PASS', 'FAIL', 'BLOCKED'}
LIMITS = [
    'Qualification limited to the frozen cases; no general productivity gain measured.',
    'Model and reasoning effort use configured defaults; exact values unavailable. No model override is made by this publisher.',
    'Complete tool-mediated skill loading is reviewed separately; no automatic CLI body-injection claim.',
    'Catalogue reconstruction occurs after execution and is not a capture of the transmitted model request.',
    'These evaluations establish no application build, application test, database, provider collection, scanner or CI pipeline.',
]


class EvidenceError(ValueError):
    """A publication precondition is absent or inconsistent; no verdict is invented."""


def require(condition, message):
    if not condition:
        raise EvidenceError(str(message))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def ids(contract, authorized=False):
    suffixes = contract['authorized_suffixes'] if authorized else SUFFIXES
    return [contract['prefix'] + '-' + suffix for suffix in suffixes]


def relative(root, name):
    require(isinstance(name, str) and name and '\\' not in name and ':' not in name and '\x00' not in name, 'Unsafe evidence path')
    posix = PurePosixPath(name)
    require(not posix.is_absolute() and all(x not in ('', '.', '..') for x in name.split('/')), 'Unsafe evidence path: ' + name)
    target = root.joinpath(*posix.parts)
    require(target.resolve().is_relative_to(root.resolve()), 'Evidence escapes root: ' + name)
    cursor = target
    while cursor != root.parent:
        require(not cursor.is_symlink() and not getattr(cursor, 'is_junction', lambda: False)(), 'Linked evidence path: ' + str(cursor))
        if cursor == root:
            break
        cursor = cursor.parent
    return target


class Reader:
    def __init__(self):
        self.fingerprints = {}

    def blob(self, path):
        path = Path(path)
        require(path.is_file() and not path.is_symlink(), 'Missing or linked evidence: ' + str(path))
        data = path.read_bytes()
        key = str(path.resolve())
        value = digest(data)
        require(key not in self.fingerprints or self.fingerprints[key] == value, 'Evidence changed while reading: ' + key)
        self.fingerprints[key] = value
        return data

    def json(self, path):
        try:
            return json.loads(self.blob(path).decode('utf-8-sig'))
        except (UnicodeError, json.JSONDecodeError) as exc:
            raise EvidenceError('Invalid JSON evidence: ' + str(path)) from exc

    def checked(self, root, entry):
        require(isinstance(entry, dict) and isinstance(entry.get('bytes'), int), 'Invalid file record')
        path = relative(root, entry['path'])
        data = self.blob(path)
        require(len(data) == entry['bytes'] and digest(data) == entry['sha256'], 'Size/hash mismatch: ' + str(path))
        return path, data


def inventory(root):
    require(root.is_dir() and not root.is_symlink() and not getattr(root, 'is_junction', lambda: False)(), 'Invalid evidence directory: ' + str(root))
    found = set()
    for path in root.rglob('*'):
        rel = path.relative_to(root).as_posix()
        relative(root, rel)
        if path.is_file():
            found.add(rel)
    return found


def verify_manifest(reader, root, filename):
    manifest = reader.json(root / filename)
    entries = manifest.get('files', [])
    names = [x['path'] for x in entries]
    require(entries and len(names) == len(set(names)) and filename not in names, 'Invalid or duplicate manifest inventory')
    require(inventory(root) == set(names) | {filename}, 'Unlisted or missing evidence in ' + str(root))
    files = {filename: reader.blob(root / filename)}
    for entry in entries:
        _, data = reader.checked(root, entry)
        files[entry['path']] = data
    return manifest, files


def counts(cases):
    return {verdict.lower(): sum(x['verdict'] == verdict for x in cases) for verdict in ('PASS', 'FAIL', 'BLOCKED')}


def validate_authorization(reader, root):
    path = root / '.tmp/c-requalification-authorization.json'
    auth = reader.json(path)
    require(auth.get('authorized') is True and auth.get('additional_model_sessions') == 10, 'Ten-session authorization absent')
    require(auth.get('owner_reply') == 'J’autorise ces dix sessions', 'Owner decision does not match this authorization')
    require(auth.get('first_sixteen_responses_preserved') is True and auth.get('human_validation') is False and auth.get('personal_installation') is False, 'Authorization scope changed')
    expected = [{'skill': skill, 'version': c['version'], 'run_id': 'run-02', 'cases': ids(c, True)} for skill, c in CONTRACTS.items()]
    require(auth.get('order') == expected, 'Authorized cases/version/order changed')
    return path, auth


def validate_global_budget(root):
    """Prepared copies are not executions; any nonempty output/freeze is an attempt."""
    total = 0
    for skill, c in CONTRACTS.items():
        parent = root / '.tmp/wo062-evaluations' / skill
        require(parent.is_dir(), 'Missing prepared runtime for ' + skill)
        allowed = set(ids(c, True))
        for run in parent.glob('run-*'):
            if run.name == 'run-01':
                continue
            attempted = set()
            frozen = run / 'frozen'
            if frozen.exists():
                attempted.update(p.name for p in frozen.iterdir())
            for case in run.iterdir():
                if not case.is_dir() or case.name == 'frozen':
                    continue
                out = case / 'output'
                if out.exists() and any(out.rglob('*')):
                    attempted.add(case.name)
            require(not attempted or run.name == 'run-02', 'An additional run is outside the ten-session authorization')
            require(attempted <= allowed, 'Unauthorized executed case: ' + str(sorted(attempted - allowed)))
            total += len(attempted)
    require(total <= 10, 'Additional execution budget exceeded')
    return total


def verify_preflight(reader, root, skill):
    c = CONTRACTS[skill]
    runtime = root / '.tmp/wo062-evaluations' / skill / 'run-02'
    pre = reader.json(runtime / 'preflight.json')
    require(pre.get('skill') == skill and pre.get('run_id') == 'run-02', 'Wrong preflight identity')
    require(pre.get('candidate_version') == c['version'] and pre.get('candidate_sha256') == c['sha256'], 'Wrong preflight candidate')
    require(pre.get('case_count') == 8 and [x['case_id'] for x in pre['cases']] == ids(c), 'Prepared cases changed')
    require(re.fullmatch('[0-9a-f]{40}', pre.get('source_head', '')), 'Invalid source commit')
    prep = root / 'docs/skills/evaluations/WO-062' / skill
    require(pre['preparation_manifest_sha256'] == digest(reader.blob(prep / 'manifest.json')), 'Preparation manifest changed')
    for case in pre['cases']:
        base = runtime / case['case_id']
        expected_mode = 'bounded_local_runtime' if skill == 'ss-windows-runtime' and case['case_id'] in ('WR-H01', 'WR-N01') else 'read_only'
        require(case.get('execution_mode') == expected_mode, 'Prepared sandbox/runtime scope changed')
        require(case.get('oracle_supplied') is False and case.get('design_history_supplied') is False and case.get('candidate_body_manually_pasted') is False, 'Preflight isolation changed')
        inputs = case['input_files']
        require(len({x['path'] for x in inputs}) == len(inputs), 'Duplicate preflight inputs')
        for entry in inputs:
            reader.checked(base, entry)
        for name, key in [('request.txt', 'request_sha256'), ('case-prompt.txt', 'prompt_sha256')]:
            require(digest(reader.blob(base / name)) == case[key], 'Prepared prompt/request changed')
        for name, expected in [('SKILL.md', c['sha256']), ('agents/openai.yaml', c['yaml_sha256'])]:
            rel = '.agents/skills/' + skill + '/' + name
            entry = next((x for x in inputs if x['path'] == rel), None)
            require(entry and entry['sha256'] == expected, 'Candidate absent from prepared inputs')
    return runtime, pre


def verify_review(reader, review_path, cid, candidate_hash, frozen_files):
    review = reader.json(review_path)
    require(review.get('case_id') == cid and review.get('verdict') in VERDICTS, 'Missing/wrong independent verdict: ' + cid)
    require(review.get('candidate_sha256') == candidate_hash, 'Review refers to another candidate: ' + cid)
    response_hash = digest(frozen_files['response.md']) if 'response.md' in frozen_files else None
    require('response_sha256' in review and review['response_sha256'] == response_hash, 'Review response binding mismatch: ' + cid)
    if review.get('freeze_sha256') is not None:
        require(review['freeze_sha256'] == digest(frozen_files['freeze.json']), 'Review freeze binding mismatch: ' + cid)
    require(reader.blob(review_path.with_suffix('.md')).strip(), 'Missing written review: ' + cid)
    return review


def verify_case(reader, runtime, pre, cid):
    frozen = runtime / 'frozen' / cid
    freeze, files = verify_manifest(reader, frozen, 'freeze.json')
    require(freeze.get('before_independent_review') is True, 'Case was not frozen before review')
    require({'trace.json', 'native-events.json', 'prompt.txt', 'request.txt'} <= files.keys(), 'Mandatory frozen files absent')
    trace = reader.json(frozen / 'trace.json')
    case = next(x for x in pre['cases'] if x['case_id'] == cid)
    for key, expected in [('case_id', cid), ('skill', pre['skill']), ('run_id', 'run-02'), ('candidate_sha256', pre['candidate_sha256']), ('source_head', pre['source_head']), ('input_files', case['input_files']), ('execution_mode', case['execution_mode'])]:
        require(trace.get(key) == expected, 'Trace identity mismatch: ' + key + ' ' + cid)
    require(trace.get('oracle_supplied') is False and trace.get('design_history_supplied') is False and trace.get('automatic_retry') is False, 'Trace isolation/retry mismatch')
    require(trace.get('model') is None and trace.get('reasoning_effort') is None, 'Unexpected model override/provenance')
    sandbox = 'workspace-write' if case['execution_mode'] == 'bounded_local_runtime' else 'read-only'
    args = trace.get('launch_arguments', [])
    require(args[:4] == ['exec', '--ephemeral', '--sandbox', sandbox] and len(args) == 10, 'Unexpected launch arguments')
    require(args[4] == '--cd' and Path(args[5]).resolve() == (runtime / cid).resolve() and args[6:8] == ['--json', '--output-last-message'] and Path(args[8]).resolve() == (runtime / cid / 'output/response.md').resolve() and args[9] == '-', 'Launch destination mismatch')
    for name, key in [('prompt.txt', 'prompt_sha256'), ('request.txt', 'request_sha256')]:
        require(digest(files[name]) == case[key] == trace.get(key), 'Frozen input hash mismatch')
    for name, key in [('native-events.json', 'native_events_sha256'), ('response.md', 'response_sha256'), ('catalogue.txt', 'catalogue_sha256')]:
        require(trace.get(key) == (digest(files[name]) if name in files else None), 'Trace output hash mismatch: ' + name)
    input_json = next((x for x in case['input_files'] if x['path'] == 'input.json'), None)
    if input_json:
        require('input.json' in files and digest(files['input.json']) == input_json['sha256'], 'Frozen input.json mismatch')
    raw = reader.blob(runtime / cid / 'output/events.jsonl')
    require(digest(raw) == trace.get('raw_events_sha256'), 'Raw event hash mismatch')
    try:
        events = [json.loads(line) for line in raw.decode('utf-8-sig').splitlines() if line.strip()]
        require(all(isinstance(e, dict) and isinstance(e.get('item', {}), dict) for e in events), 'Malformed event')
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise EvidenceError('Unparseable raw events: ' + cid) from exc
    require(sum(e.get('type') == 'thread.started' for e in events) == 1, 'Missing/multiple model sessions in case')
    safe = [e for e in events if e.get('item', {}).get('type') != 'reasoning']
    require(safe == reader.json(frozen / 'native-events.json'), 'Native events differ beyond reasoning removal')
    require(sum(e.get('type') == 'item.completed' and e.get('item', {}).get('type') == 'command_execution' for e in safe) == trace.get('commands_completed'), 'Command count mismatch')
    finals = [e['item']['text'] for e in safe if e.get('type') == 'item.completed' and e.get('item', {}).get('type') == 'agent_message']
    if 'response.md' in files:
        require(finals and files['response.md'].decode('utf-8') == finals[-1], 'Final response differs from emitted message')
    for entry in trace.get('runtime_artifacts', []):
        if entry.get('frozen'):
            name = 'runtime-artifacts/' + entry['path']
            require(name in files and len(files[name]) == entry['bytes'] and digest(files[name]) == entry['sha256'], 'Runtime artifact mismatch')
    review = verify_review(reader, runtime / ('review-' + cid + '.json'), cid, pre['candidate_sha256'], files)
    if review['verdict'] == 'PASS':
        require(trace.get('exit_code') == 0 and trace.get('collector_exit_code') == 0 and trace.get('collection_completed') is True and trace.get('turn_completed') is True and trace.get('timed_out') is False and not trace.get('diagnostics') and bool(files.get('response.md', b'').strip()), 'PASS contradicts collector/completion evidence')
    return files, review


def verify_history(reader, prep, skill):
    c = CONTRACTS[skill]
    old = prep / 'run-01'
    _, all_files = verify_manifest(reader, old, 'artifact-manifest.json')
    require(digest(all_files['artifact-manifest.json']) == c['old_manifest_sha256'], 'Historical artifact manifest changed')
    results = reader.json(old / 'results.json')
    require(results.get('skill') == skill and results.get('run_id') == 'run-01' and results.get('candidate_sha256') == c['old_sha256'] and results.get('case_count') == 8, 'Wrong run-01 provenance')
    require([x['case_id'] for x in results['cases']] == ids(c) and [x['verdict'] for x in results['cases']] == list(c['old_verdicts']), 'Historical outcomes changed')
    require(all(results.get(k) == v for k, v in counts(results['cases']).items()), 'Historical totals mismatch')
    for item in results['cases']:
        cid = item['case_id']
        require(item['review'] == 'review-' + cid + '.json' and item['response'] == 'cases/' + cid + '/response.md', 'Historical case paths changed')
        _, files = verify_manifest(reader, old / 'cases' / cid, 'freeze.json')
        review = verify_review(reader, old / item['review'], cid, c['old_sha256'], files)
        require(review['verdict'] == item['verdict'] and item['response_sha256'] == review['response_sha256'], 'Historical review/results mismatch')
    return results, digest(all_files['artifact-manifest.json'])


def git_provenance(root, candidate, source_head):
    def git(*args):
        return subprocess.check_output(['git', *args], cwd=root, text=True, encoding='utf-8').strip()
    subprocess.run(['git', 'merge-base', '--is-ancestor', source_head, 'HEAD'], cwd=root, check=True, capture_output=True)
    historical_body = subprocess.check_output(['git', 'show', source_head + ':' + (candidate / 'SKILL.md').relative_to(root).as_posix()], cwd=root)
    require(digest(historical_body) == digest((candidate / 'SKILL.md').read_bytes()), 'Preflight commit did not contain this candidate')
    return {'publisher_source_head': git('rev-parse', 'HEAD'), 'candidate_body_commit': git('log', '-1', '--format=%H', '--', str(candidate / 'SKILL.md')), 'candidate_package_commit': git('log', '-1', '--format=%H', '--', str(candidate))}


@dataclass
class Plan:
    root: Path
    skill: str
    destination: Path
    qualification_path: Path
    files: dict
    qualification: dict
    fingerprints: dict


def build_plan(root, skill, provenance=git_provenance):
    root = Path(root).resolve()
    require(skill in CONTRACTS, 'Unknown skill')
    c = CONTRACTS[skill]
    reader = Reader()
    auth_path, auth = validate_authorization(reader, root)
    validate_global_budget(root)
    runtime, pre = verify_preflight(reader, root, skill)
    prep = root / 'docs/skills/evaluations/WO-062' / skill
    destination = prep / 'run-02'
    require(not destination.exists(), 'run-02 is immutable once published; destination already exists')
    require(set(p.name for p in (runtime / 'frozen').iterdir()) == set(ids(c, True)), 'Authorized frozen case set incomplete or excessive')
    old, old_manifest_hash = verify_history(reader, prep, skill)
    previous_q = reader.json(prep / 'qualification.json')
    require(previous_q.get('human_validation') is False and previous_q.get('personal_installation') is False, 'Existing owner/install state must not be overwritten')
    candidate = root / 'docs/skills/local-lab' / skill
    candidate_files = []
    for name, expected in [('SKILL.md', c['sha256']), ('agents/openai.yaml', c['yaml_sha256'])]:
        data = reader.blob(relative(candidate, name))
        require(digest(data) == expected, 'Canonical candidate changed: ' + name)
        text = data.decode('utf-8')
        require('\r' not in text and not text.startswith('\ufeff') and '\ufffd' not in text, 'Candidate encoding changed')
        if name == 'SKILL.md':
            require(re.search(r'^name: ' + re.escape(skill) + r'$', text, re.M) and 'version: "' + c['version'] + '"' in text, 'Candidate metadata identity mismatch')
        candidate_files.append({'path': name, 'bytes': len(data), 'sha256': expected})
    require(inventory(candidate) == {'SKILL.md', 'agents/openai.yaml'}, 'Unexpected candidate package files')
    static_base = runtime if (runtime / 'review-static.json').exists() else prep / 'run-01'
    static = reader.json(static_base / 'review-static.json')
    require(static.get('verdict') in VERDICTS and static.get('candidate_sha256') == c['sha256'] and static.get('candidate_version') == c['version'], 'Static review is not for exact candidate')
    static_yaml = static.get('yaml_sha256', static.get('candidate_yaml_sha256'))
    require(static_yaml == c['yaml_sha256'], 'Static metadata review mismatch')
    files = {'preflight.json': reader.blob(runtime / 'preflight.json'), 'authorization.json': reader.blob(auth_path)}
    for suffix in ('json', 'md'):
        files['review-static.' + suffix] = reader.blob(static_base / ('review-static.' + suffix))
    harness_review = reader.json(root / '.tmp/c-harness-review.json')
    harness_tests = reader.json(root / '.tmp/c-harness-synthetic-results.json')
    require(harness_review.get('status') == 'LOCAL_CORRECTIONS_VERIFIED' and harness_review['reviewed_launcher']['final_sha256'] == LAUNCHER_SHA and harness_review['validation']['test_script_sha256'] == LAUNCHER_TEST_SHA, 'Reviewed harness identity mismatch')
    require(all(harness_review['validation'].get(k) == v for k, v in {'tests_run': 17, 'failures': 0, 'errors': 0, 'exit_code': 0, 'model_calls': 0}.items()), 'Harness review does not establish 17 successful mocks')
    require(harness_tests.get('launcher_sha256') == LAUNCHER_SHA and harness_tests.get('tests_run') == 17 and harness_tests.get('failures') == 0 and harness_tests.get('errors') == 0 and harness_tests.get('success') is True and harness_tests.get('model_calls') == 0 and harness_tests.get('external_process_calls') == 0, 'Seventeen pure harness mocks not established')
    for src, name in [('c-harness-review.md', 'harness-review.md'), ('c-harness-review.json', 'harness-review.json'), ('c-harness-synthetic-results.json', 'harness-tests.json'), ('wo062_evaluate_c.py', 'harness-source.py.txt'), ('test_c_harness.py', 'harness-tests.py.txt')]:
        files[name] = reader.blob(root / '.tmp' / src)
    require(digest(files['harness-source.py.txt']) == LAUNCHER_SHA and digest(files['harness-tests.py.txt']) == LAUNCHER_TEST_SHA, 'Harness source or tests changed')
    cases = []
    for cid in ids(c, True):
        frozen, review = verify_case(reader, runtime, pre, cid)
        files.update({'cases/' + cid + '/' + name: data for name, data in frozen.items()})
        for suffix in ('json', 'md'):
            files['review-' + cid + '.' + suffix] = reader.blob(runtime / ('review-' + cid + '.' + suffix))
        cases.append({'case_id': cid, 'verdict': review['verdict'], 'source_run': 'run-02', 'review': 'review-' + cid + '.json', 'response': 'cases/' + cid + '/response.md' if 'response.md' in frozen else None, 'response_sha256': review['response_sha256']})
    stamp = datetime.now(timezone.utc).isoformat()
    excluded = [cid for cid in ids(c) if cid not in ids(c, True)]
    results = {'schema': 'wo062-frozen-skill-results-v1', 'skill': skill, 'run_id': 'run-02', 'candidate_version': c['version'], 'candidate_sha256': c['sha256'], 'source_head': pre['source_head'], 'reviewed_at_utc': stamp, 'case_count': len(cases), 'prepared_case_count': 8, 'excluded_prepared_cases': excluded, 'exclusion_reason': 'PREPARED_NOT_EXECUTED_NOT_AUTHORIZED_IN_RUN_02' if excluded else None, 'cases': cases, **counts(cases)}
    current = []
    for cid in ids(c):
        item = copy.deepcopy(next(x for x in (cases if cid in ids(c, True) else old['cases']) if x['case_id'] == cid))
        item['source_run'] = 'run-02' if cid in ids(c, True) else 'run-01'
        if item['source_run'] == 'run-01':
            item['review'] = '../run-01/' + item['review']
            item['response'] = '../run-01/' + item['response']
        current.append(item)
    historical = {'run_id': 'run-01', 'candidate_version': '0.1.0-candidate.1', 'candidate_sha256': c['old_sha256'], 'executions': 8, **counts(old['cases']), 'results': 'run-01/results.json', 'artifact_manifest_sha256': old_manifest_hash}
    previous_history = next((item for item in previous_q.get('historical_runs', []) if item.get('run_id') == 'run-01'), previous_q if skill == 'ss-windows-runtime' else {})
    if 'candidate_files' in previous_history:
        old_files = {item['path']: item for item in previous_history['candidate_files']}
        require(old_files.get('SKILL.md', {}).get('sha256') == c['old_sha256'] and old_files.get('agents/openai.yaml', {}).get('sha256') == c['yaml_sha256'], 'Historical candidate package provenance changed')
    for field in ('candidate_files', 'candidate_body_commit', 'candidate_package_commit', 'source_commit'):
        if field in previous_history:
            historical[field] = copy.deepcopy(previous_history[field])
    total_exec = 8 + len(cases)
    executions = total_exec if skill == 'ss-windows-runtime' else len(cases)
    aggregate = {'schema': 'wo062-current-skill-results-v1', 'skill': skill, 'candidate_version': c['version'], 'candidate_sha256': c['sha256'], 'case_count': 8, 'executions': executions, 'total_executions_all_versions': total_exec, 'cases': current, **counts(current), 'historical_runs': [historical]}
    files['results.json'] = encode(results)
    files['current-results.json'] = encode(aggregate)
    provenance_data = provenance(root, candidate, pre['source_head'])
    require(set(provenance_data) == {'publisher_source_head', 'candidate_body_commit', 'candidate_package_commit'} and all(re.fullmatch('[0-9a-f]{40}', value) for value in provenance_data.values()), 'Invalid Git provenance')
    limits = list(LIMITS)
    if skill == 'ss-windows-runtime':
        limits.append('WR-H01 is a controlled PowerShell7 argument capture, not Java application startup. WR-N01 is only a synthetic Java source probe under Windows PowerShell5.1. The two run-01 BLOCKED outcomes remain historical evidence; they are not rewritten.')
    files['publication-provenance.json'] = encode({'schema': 'wo062-run02-publication-provenance-v1', **provenance_data, 'evaluation_source_head': pre['source_head'], 'authorization_sha256': digest(files['authorization.json']), 'authorized_additional_sessions': 10, 'published_cases': ids(c, True), 'excluded_prepared_cases': excluded, 'static_review_source': static_base.relative_to(root).as_posix(), 'publisher_sha256': digest(Path(__file__).read_bytes()), 'model': None, 'reasoning_effort': None, 'human_validation': False, 'personal_installation': False, 'limits': limits})
    manifest = {'schema': 'wo062-skill-run-artifacts-v1', 'skill': skill, 'run_id': 'run-02', 'files': [{'path': name, 'bytes': len(data), 'sha256': digest(data)} for name, data in sorted(files.items())], 'hash_contract': 'Exact published bytes; manifest excludes itself. Only named frozen cases and corresponding reviews are published; raw internal reasoning is excluded.'}
    files['artifact-manifest.json'] = encode(manifest)
    q = {'schema': 'wo062-current-skill-qualification-v1', 'skill': skill, 'candidate_version': c['version'], 'status': 'QUALIFIED_ON_FROZEN_CASES_PENDING_OWNER_VALIDATION' if counts(current)['pass'] == 8 and static['verdict'] == 'PASS' else 'INCOMPLETE_QUALIFICATION', 'source_commit': reader.json(prep / 'manifest.json')['source_commit'], 'source_head': pre['source_head'], 'preparation_manifest_sha256': pre['preparation_manifest_sha256'], 'candidate_files': candidate_files, **provenance_data, 'distinct_cases': 8, 'executions': executions, 'total_executions': total_exec, **counts(current), 'human_validation': False, 'personal_installation': False, 'results': 'run-02/current-results.json', 'run_results': 'run-02/results.json', 'artifact_manifest_sha256': digest(files['artifact-manifest.json']), 'historical_runs': [historical], 'execution_totals': counts(old['cases'] + cases), 'current_cases': [{'case_id': x['case_id'], 'verdict': x['verdict'], 'source_run': x['source_run']} for x in current], 'static_review': 'run-02/review-static.json', 'limits': limits}
    return Plan(root, skill, destination, prep / 'qualification.json', files, q, reader.fingerprints)


def publish(plan):
    """Commit a prevalidated plan, preserving all existing run-01 bytes."""
    require(not plan.destination.exists(), 'Destination already exists')
    validate_global_budget(plan.root)
    for name, expected in plan.fingerprints.items():
        require(Path(name).is_file() and digest(Path(name).read_bytes()) == expected, 'Evidence changed after validation: ' + name)
    relative(plan.root, plan.destination.relative_to(plan.root).as_posix())
    relative(plan.root, plan.qualification_path.relative_to(plan.root).as_posix())
    staging_parent = relative(plan.root, '.tmp/run02-publisher-staging')
    staging_parent.mkdir(exist_ok=True)
    stage = staging_parent / str(uuid.uuid4())
    stage.mkdir()
    for name, data in plan.files.items():
        path = relative(stage, name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    verify_manifest(Reader(), stage, 'artifact-manifest.json')
    q_temp = staging_parent / (stage.name + '.qualification.json')
    q_temp.write_bytes(encode(plan.qualification))
    require(not plan.destination.exists(), 'Destination appeared during staging')
    validate_global_budget(plan.root)
    for name, expected in plan.fingerprints.items():
        require(Path(name).is_file() and digest(Path(name).read_bytes()) == expected, 'Evidence changed during staging: ' + name)
    # Recheck the owner-visible qualification immediately before its atomic replacement.
    require(digest(plan.qualification_path.read_bytes()) == plan.fingerprints[str(plan.qualification_path.resolve())], 'Qualification changed concurrently')
    stage.rename(plan.destination)
    try:
        os.replace(q_temp, plan.qualification_path)
    except BaseException:
        # Move only our new directory back to its exact staging path; no deletion.
        plan.destination.rename(stage)
        raise
    return {'skill': plan.skill, 'published_run': 'run-02', 'case_count': json.loads(plan.files['results.json'])['case_count'], 'current': {k: plan.qualification[k] for k in ('pass', 'fail', 'blocked', 'executions')}, 'human_validation': False, 'personal_installation': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('skill', choices=CONTRACTS)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--publish', action='store_true', help='Write the validated run-02 and atomically update qualification.json')
    args = parser.parse_args()
    plan = build_plan(args.root, args.skill)
    result = publish(plan) if args.publish else {'validation': 'PASS', 'publication': 'NOT_PERFORMED', 'skill': args.skill, 'case_count': json.loads(plan.files['results.json'])['case_count'], 'current': counts(json.loads(plan.files['current-results.json'])['cases'])}
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
