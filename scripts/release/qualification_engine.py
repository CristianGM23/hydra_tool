from __future__ import annotations

import argparse
import hashlib
import json
import os
import signal
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

ROOT = Path(__file__).resolve().parents[2]
POLICY = json.loads((ROOT / 'config/qualification-engine-policy.json').read_text(encoding='utf-8'))


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def workspace_fingerprint(extra: dict | None = None) -> str:
    digest = hashlib.sha256()
    for relative in POLICY.get('fingerprintInputs', []):
        path = ROOT / relative
        digest.update(relative.encode('utf-8'))
        if path.is_file():
            digest.update(path.read_bytes())
    if extra:
        digest.update(json.dumps(extra, sort_keys=True, ensure_ascii=False).encode('utf-8'))
    return digest.hexdigest()


def validate_evidence_report(
    path: Path, *, expected_statuses: Iterable[str] = ('PASS',), expected_candidate: str | None = None,
    expected_version: str | None = None, require_full: bool = False,
    fingerprint_extra_factory: Callable[[dict], dict] | None = None, required_stages: Iterable[str] = (),
) -> tuple[bool, str]:
    """Validate reusable qualification evidence against the current source fingerprint.

    Evidence is never trusted merely because a caller says it ran immediately before this
    qualification.  The report must be a PASS for the expected candidate/version, have a
    fingerprint derived from the current tree, satisfy the requested full/structural strength,
    and contain every required upstream stage.
    """
    if not path.is_file():
        return False, f'missing evidence: {path}'
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        return False, f'invalid evidence JSON {path}: {exc}'
    statuses = {str(item) for item in expected_statuses}
    status = str(data.get('status', ''))
    if status not in statuses:
        return False, f'{path.name}: status={status!r}, expected one of {sorted(statuses)}'
    if expected_candidate is not None and data.get('candidate') != expected_candidate:
        return False, f'{path.name}: candidate={data.get("candidate")!r}, expected {expected_candidate!r}'
    actual_version = data.get('releaseVersion', data.get('version'))
    if expected_version is not None and actual_version != expected_version:
        return False, f'{path.name}: version={actual_version!r}, expected {expected_version!r}'
    if require_full and not bool(data.get('full')):
        return False, f'{path.name}: full qualification evidence is required'
    if fingerprint_extra_factory is not None:
        expected_fingerprint = workspace_fingerprint(fingerprint_extra_factory(data))
        actual_fingerprint = data.get('fingerprint')
        if actual_fingerprint != expected_fingerprint:
            return False, f'{path.name}: stale/incompatible fingerprint actual={actual_fingerprint} expected={expected_fingerprint}'
    stages = {str(item.get('name')): item for item in data.get('steps', []) if isinstance(item, dict)}
    for name in required_stages:
        stage = stages.get(str(name))
        if not stage or not bool(stage.get('passed')):
            return False, f'{path.name}: required stage {name!r} is missing or not PASS'
    return True, f'validated {path.name} status={status} fingerprint={data.get("fingerprint")}'


def add_resume_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument('--resume', action='store_true', help='Reutiliza etapas PASS del mismo fingerprint.')
    parser.add_argument('--from', dest='from_stage', default='', help='Continúa desde esta etapa; las previas requieren checkpoint PASS.')
    parser.add_argument('--only', default='', help='Ejecuta solo estas etapas separadas por coma; las demás se leen del checkpoint.')
    parser.add_argument('--rerun-failed', action='store_true', help='Reutiliza PASS y vuelve a ejecutar únicamente FAIL/missing.')


def _kill_tree(process: subprocess.Popen[str], grace: int) -> None:
    if process.poll() is not None:
        return
    if os.name == 'nt':
        try:
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True, timeout=max(grace, 3))
        except Exception:
            process.kill()
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=grace)
    except Exception:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except Exception:
            process.kill()


@dataclass
class StageResult:
    name: str
    passed: bool
    required: bool
    seconds: float
    outputTail: str
    log: str | None = None
    reused: bool = False
    skipped: bool = False
    command: list[str] | None = None


class QualificationRunner:
    def __init__(self, qualification: str, args: argparse.Namespace, *, fingerprint_extra: dict | None = None):
        self.qualification = qualification
        self.args = args
        self.log_dir = ROOT / POLICY['logDirectory'] / qualification
        self.state_dir = ROOT / POLICY['stateDirectory']
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self.state_dir.mkdir(parents=True, exist_ok=True)
        self.state_path = self.state_dir / f'{qualification}.json'
        self.fingerprint = workspace_fingerprint(fingerprint_extra)
        self.previous = self._load_previous()
        self.results: list[StageResult] = []
        self.only = {item.strip() for item in getattr(args, 'only', '').split(',') if item.strip()}
        self.from_stage = getattr(args, 'from_stage', '')
        self._from_reached = not bool(self.from_stage)

    def _load_previous(self) -> dict:
        if not self.state_path.is_file():
            return {'fingerprint': self.fingerprint, 'stages': {}}
        try:
            data = json.loads(self.state_path.read_text(encoding='utf-8'))
        except Exception:
            return {'fingerprint': self.fingerprint, 'stages': {}}
        if data.get('fingerprint') != self.fingerprint:
            return {'fingerprint': self.fingerprint, 'stages': {}}
        return data

    def _previous_stage(self, name: str) -> dict | None:
        stage = self.previous.get('stages', {}).get(name)
        return stage if isinstance(stage, dict) else None

    def _selection(self, name: str) -> tuple[bool, bool]:
        """Return (execute, can_reuse_previous)."""
        previous = self._previous_stage(name)
        if self.only:
            if name in self.only:
                return True, False
            return False, bool(previous and previous.get('passed'))
        if not self._from_reached:
            if name == self.from_stage:
                self._from_reached = True
                return True, False
            return False, bool(previous and previous.get('passed'))
        if getattr(self.args, 'rerun_failed', False):
            if previous and previous.get('passed'):
                return False, True
            return True, False
        if getattr(self.args, 'resume', False) and previous and previous.get('passed'):
            return False, True
        return True, False

    def _tail(self, path: Path) -> str:
        try:
            lines = path.read_text(encoding='utf-8', errors='replace').splitlines()
            return '\n'.join(lines[-int(POLICY.get('tailLines', 120)):])
        except Exception as exc:
            return str(exc)

    def _persist(self) -> None:
        stages = dict(self.previous.get('stages', {}))
        for result in self.results:
            if result.skipped and not result.reused:
                continue
            stages[result.name] = {
                'passed': result.passed,
                'required': result.required,
                'seconds': result.seconds,
                'outputTail': result.outputTail,
                'log': result.log,
                'command': result.command,
                'updatedAtEpochMs': int(time.time() * 1000),
            }
        payload = {'schemaVersion': '1.0', 'qualification': self.qualification, 'fingerprint': self.fingerprint, 'stages': stages}
        self.state_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

    def run(self, name: str, command: list[str], *, cwd: Path = ROOT, timeout: int | None = None, required: bool = True, env: dict[str, str] | None = None, outputs: Iterable[Path] = ()) -> bool:
        execute, reuse = self._selection(name)
        previous = self._previous_stage(name)
        required_outputs = [Path(item) for item in outputs]
        if reuse and required_outputs and not all(path.exists() for path in required_outputs):
            execute, reuse = True, False
        if not execute:
            if reuse and previous:
                result = StageResult(name, True, required, 0.0, str(previous.get('outputTail', '')), previous.get('log'), reused=True, command=command)
                self.results.append(result)
                print('REUSE', name)
                return True
            result = StageResult(name, False, required, 0.0, 'No valid checkpoint available for skipped stage.', skipped=True, command=command)
            self.results.append(result)
            print('SKIP', name)
            return not required
        timeout = timeout or int(POLICY.get('defaultTimeoutSeconds', 3600))
        safe_name = ''.join(ch if ch.isalnum() or ch in '-_.' else '-' for ch in name)
        log = self.log_dir / f'{safe_name}.log'
        started = time.time()
        merged_env = os.environ.copy()
        merged_env.setdefault('PYTHONUTF8', '1')
        merged_env.setdefault('PYTHONIOENCODING', 'utf-8')
        merged_env.setdefault('PYTHONUNBUFFERED', '1')
        if env:
            merged_env.update(env)
        creationflags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0
        relative_log = str(log.relative_to(ROOT))
        print('START', name, f'timeout={timeout}s', f'log={relative_log}', flush=True)
        with log.open('w', encoding='utf-8', errors='replace') as handle:
            handle.write('$ ' + subprocess.list2cmdline(command) + '\n')
            handle.flush()
            try:
                process = subprocess.Popen(
                    command, cwd=cwd, env=merged_env, text=True,
                    stdout=handle, stderr=subprocess.STDOUT,
                    creationflags=creationflags,
                    start_new_session=(os.name != 'nt'),
                )
                deadline = started + timeout
                heartbeat_seconds = max(1, int(POLICY.get('heartbeatSeconds', 30)))
                poll_seconds = max(0.1, float(POLICY.get('pollSeconds', 1)))
                next_heartbeat = started + heartbeat_seconds
                while True:
                    code = process.poll()
                    if code is not None:
                        passed = code == 0
                        break
                    now = time.time()
                    if now >= deadline:
                        handle.write(f'\nTIMEOUT after {timeout}s; terminating process tree pid={process.pid}\n')
                        handle.flush()
                        _kill_tree(process, int(POLICY.get('terminateGraceSeconds', 8)))
                        passed = False
                        break
                    if now >= next_heartbeat:
                        elapsed = int(now - started)
                        print('RUNNING', name, f'elapsed={elapsed}s', f'log={relative_log}', flush=True)
                        next_heartbeat = now + heartbeat_seconds
                    time.sleep(min(poll_seconds, max(0.1, deadline - now)))
            except Exception as exc:
                handle.write(f'\nLAUNCH ERROR: {exc}\n')
                passed = False
        result = StageResult(
            name=name, passed=passed, required=required, seconds=round(time.time() - started, 3),
            outputTail=self._tail(log), log=relative_log, command=command,
        )
        self.results.append(result)
        self._persist()
        print(('PASS' if passed else 'FAIL'), name, f'log={result.log}')
        return passed or not required

    def record(self, name: str, passed: bool, output: str, *, required: bool = True, seconds: float = 0.0, log: str | None = None) -> bool:
        execute, reuse = self._selection(name)
        previous = self._previous_stage(name)
        if not execute and reuse and previous:
            self.results.append(StageResult(name, True, required, 0.0, str(previous.get('outputTail', '')), previous.get('log'), reused=True))
            print('REUSE', name)
            return True
        if not execute:
            self.results.append(StageResult(name, False, required, 0.0, 'No valid checkpoint available for skipped stage.', skipped=True))
            print('SKIP', name)
            return not required
        self.results.append(StageResult(name, passed, required, seconds, output, log))
        self._persist()
        print(('PASS' if passed else 'FAIL'), name)
        return passed or not required

    def stage_payloads(self) -> list[dict]:
        return [result.__dict__ for result in self.results]

    def all_required_passed(self, required_names: Iterable[str] | None = None) -> bool:
        if required_names is None:
            return all(result.passed or not result.required for result in self.results)
        current = {item.name: item for item in self.results}
        previous = self.previous.get('stages', {})
        for name in required_names:
            if name in current:
                if not current[name].passed:
                    return False
            elif not bool(previous.get(name, {}).get('passed')):
                return False
        return True
