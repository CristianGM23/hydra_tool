from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import os
import subprocess
import sys
import venv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIREMENTS = ROOT / 'scripts/release/qualification-requirements.txt'
REQUIRED_PACKAGES = ('jsonschema', 'pytest')


def requirements_sha256() -> str:
    return hashlib.sha256(REQUIREMENTS.read_bytes()).hexdigest()


def default_cache_root() -> Path:
    override = os.environ.get('HYDRA_PYTHON_TOOLCHAIN_ROOT', '').strip()
    if override:
        return Path(override).expanduser().resolve()
    if os.name == 'nt':
        base = Path(os.environ.get('LOCALAPPDATA') or (Path.home() / 'AppData' / 'Local'))
    else:
        base = Path(os.environ.get('XDG_CACHE_HOME') or (Path.home() / '.cache'))
    return base / 'HYDRA' / 'toolchains' / 'python'


def environment_directory() -> Path:
    digest = requirements_sha256()[:16]
    tag = f'cp{sys.version_info.major}{sys.version_info.minor}-{digest}'
    return default_cache_root() / tag


def environment_python(directory: Path) -> Path:
    return directory / ('Scripts/python.exe' if os.name == 'nt' else 'bin/python')


def package_versions(interpreter: Path) -> dict[str, str]:
    code = (
        "import importlib.metadata,json;"
        "print(json.dumps({name:importlib.metadata.version(name) for name in ('jsonschema','pytest')}))"
    )
    completed = subprocess.run(
        [str(interpreter), '-c', code], cwd=ROOT, text=True, capture_output=True,
        timeout=30, env={**os.environ, 'PYTHONUTF8': '1', 'PYTHONIOENCODING': 'utf-8'},
    )
    if completed.returncode != 0:
        raise RuntimeError((completed.stdout + '\n' + completed.stderr).strip())
    data = json.loads(completed.stdout)
    return {str(name): str(version) for name, version in data.items()}


def expected_versions() -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in REQUIREMENTS.read_text(encoding='utf-8').splitlines():
        line = raw.strip()
        if not line or line.startswith('#') or '==' not in line:
            continue
        name, version = line.split('==', 1)
        result[name.strip().lower()] = version.strip()
    return result


def marker_matches(directory: Path, interpreter: Path) -> tuple[bool, dict[str, str]]:
    marker = directory / '.hydra-qualification-python.json'
    expected = expected_versions()
    if not marker.is_file() or not interpreter.is_file():
        return False, {}
    try:
        data = json.loads(marker.read_text(encoding='utf-8'))
        if data.get('requirementsSha256') != requirements_sha256():
            return False, {}
        if data.get('hostPython') != str(Path(sys.executable).resolve()):
            return False, {}
        versions = package_versions(interpreter)
        if any(versions.get(name) != version for name, version in expected.items()):
            return False, versions
        return True, versions
    except Exception:
        return False, {}


def ensure() -> dict[str, object]:
    if sys.version_info < (3, 10):
        raise RuntimeError(f'HYDRA qualification requires Python >= 3.10; found {sys.version.split()[0]}')
    directory = environment_directory()
    interpreter = environment_python(directory)
    reusable, versions = marker_matches(directory, interpreter)
    if not reusable:
        directory.parent.mkdir(parents=True, exist_ok=True)
        if not interpreter.is_file():
            venv.EnvBuilder(with_pip=True, clear=True).create(directory)
        command = [
            str(interpreter), '-m', 'pip', 'install', '--disable-pip-version-check', '--no-input',
            '--requirement', str(REQUIREMENTS),
        ]
        completed = subprocess.run(
            command, cwd=ROOT, text=True, capture_output=True, timeout=900,
            env={**os.environ, 'PYTHONUTF8': '1', 'PYTHONIOENCODING': 'utf-8', 'PIP_DISABLE_PIP_VERSION_CHECK': '1'},
        )
        if completed.returncode != 0:
            detail = (completed.stdout + '\n' + completed.stderr).strip()
            raise RuntimeError(f'Could not provision HYDRA qualification Python environment.\n{detail}')
        versions = package_versions(interpreter)
        expected = expected_versions()
        mismatches = {name: {'expected': version, 'actual': versions.get(name)} for name, version in expected.items() if versions.get(name) != version}
        if mismatches:
            raise RuntimeError(f'Qualification Python package versions do not match the lock: {mismatches}')
        marker = {
            'schemaVersion': '1.0',
            'requirementsSha256': requirements_sha256(),
            'hostPython': str(Path(sys.executable).resolve()),
            'hostPythonVersion': sys.version.split()[0],
            'interpreter': str(interpreter),
            'packages': versions,
        }
        (directory / '.hydra-qualification-python.json').write_text(json.dumps(marker, indent=2) + '\n', encoding='utf-8')
    return {
        'schemaVersion': '1.0',
        'status': 'PASS',
        'reused': reusable,
        'directory': str(directory),
        'interpreter': str(interpreter),
        'requirementsSha256': requirements_sha256(),
        'packages': versions,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description='Provisiona el Python hermético usado por los gates de cualificación de HYDRA.')
    parser.add_argument('--ensure', action='store_true', help='Crea o actualiza el entorno si es necesario.')
    parser.add_argument('--json', action='store_true', help='Emite un único objeto JSON en stdout.')
    args = parser.parse_args()
    try:
        result = ensure() if args.ensure else {
            'schemaVersion': '1.0',
            'status': 'PASS' if marker_matches(environment_directory(), environment_python(environment_directory()))[0] else 'MISSING',
            'directory': str(environment_directory()),
            'interpreter': str(environment_python(environment_directory())),
        }
    except Exception as exc:
        if args.json:
            print(json.dumps({'schemaVersion': '1.0', 'status': 'FAIL', 'error': str(exc)}, ensure_ascii=False))
        else:
            print(f'FAIL qualification-python - {exc}', file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(result, ensure_ascii=False))
    else:
        print(f"PASS qualification-python - interpreter={result['interpreter']} reused={result.get('reused', False)}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
