from __future__ import annotations

import argparse
import importlib.metadata
import json
import os
import shutil
import socket
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/toolchain'))
from hydra_environment import collect_toolchain, discover_jdks, port_available, write_probe  # noqa: E402

parser = argparse.ArgumentParser(description='Diagnóstico integral del entorno de HYDRA.')
parser.add_argument('--full', action='store_true', help='Incluye requisitos de la cualificación dinámica completa.')
parser.add_argument('--strict', action='store_true', help='Devuelve error si falla cualquier requisito obligatorio.')
parser.add_argument('--jdk-home', default='', help='JDK preferido para el diagnóstico.')
parser.add_argument('--json-only', action='store_true')
args = parser.parse_args()
policy = json.loads((ROOT / 'config/environment-doctor-policy.json').read_text(encoding='utf-8'))
out = ROOT / 'target/diagnostics/environment-doctor.json'; out.parent.mkdir(parents=True, exist_ok=True)
checks: list[dict] = []


def add(name: str, passed: bool, detail: str, *, required: bool = True, remediation: str | None = None) -> None:
    checks.append({'name': name, 'passed': bool(passed), 'required': required, 'detail': detail, 'remediation': remediation})
    if not args.json_only:
        print(('PASS' if passed else ('FAIL' if required else 'WARN')), name, '-', detail)


toolchain = collect_toolchain(args.jdk_home or None)
tools = toolchain['tools']
selected = toolchain.get('jdk')
add('jdk', bool(selected and selected.get('accepted')), f"selected={selected.get('root') if selected else '<none>'} version={selected.get('version') if selected else ''}", remediation='Instala un JDK permitido por config/toolchain-discovery-policy.json o usa --jdk-home.')
required_tools = list(dict.fromkeys(str(name) for name in policy.get('requiredTools', [])))
for name in required_tools:
    item = tools.get(name)
    available = bool(item and item.get('available'))
    detail = f"path={item.get('path')} version={item.get('version')}" if item else 'tool-not-described-by-collector'
    remediation = 'El candidato Java debe ser un JDK completo con javac.' if name == 'javac' else f'Repara o instala {name} según config/hydra-toolchain.json.'
    add(name, available, detail, remediation=remediation)
full_tools = {str(name) for name in policy.get('fullQualificationTools', [])}
for name in sorted(full_tools - set(required_tools) - {'playwright'}):
    item = tools.get(name)
    available = bool(item and item.get('available'))
    detail = f"path={item.get('path')} version={item.get('version')}" if item else 'tool-not-described-by-collector'
    add(name, available, detail, required=args.full, remediation=f'Ejecuta npm ci en frontend para instalar {name} de forma reproducible.')
playwright_required = args.full and 'playwright' in full_tools
add('playwright-package', bool(tools['playwright']['available']), f"path={tools['playwright']['path']} version={tools['playwright']['version']}", required=playwright_required, remediation='Ejecuta npm ci en frontend y npx playwright install chromium.')

playwright_browser_ok = False
playwright_browser_detail = 'not-checked'
if playwright_required and tools['node']['available']:
    try:
        node_path = tools['node']['path']
        script = "const {chromium}=require('./frontend/node_modules/playwright');const p=chromium.executablePath();console.log(p);"
        completed = __import__('subprocess').run([node_path, '-e', script], cwd=ROOT, text=True, capture_output=True, timeout=20)
        candidate = Path(completed.stdout.strip()) if completed.returncode == 0 and completed.stdout.strip() else None
        playwright_browser_ok = bool(candidate and candidate.is_file())
        playwright_browser_detail = str(candidate) if candidate else (completed.stderr.strip() or 'chromium executable unresolved')
    except Exception as exc:
        playwright_browser_detail = str(exc)
add('playwright-chromium', playwright_browser_ok, playwright_browser_detail, required=playwright_required, remediation='Ejecuta npx playwright install chromium desde frontend.')

workspace_ok, workspace_detail = write_probe(ROOT / 'target/diagnostics/write-probe')
add('workspace-write', workspace_ok, workspace_detail, required=bool(policy.get('requireWritableWorkspace', True)), remediation='Comprueba permisos del directorio HYDRA.')
temp_ok, temp_detail = write_probe(Path(tempfile.gettempdir()))
add('temp-write', temp_ok, temp_detail, required=bool(policy.get('requireWritableTemp', True)), remediation='Comprueba TEMP/TMP y permisos.')

free = shutil.disk_usage(ROOT).free
minimum = int(policy.get('minimumFreeDiskBytes', 0))
add('free-disk', free >= minimum, f'freeBytes={free} requiredBytes={minimum}', remediation='Libera espacio antes de cualificar HYDRA.')

for port in policy.get('requiredPorts', []):
    available = port_available(int(port))
    add(f'port-{port}', available, 'available' if available else 'already-in-use', required=False, remediation=f'Libera el puerto {port} o usa un puerto alternativo.')

python_major = sys.version_info.major
add('python', python_major >= int(policy.get('requirePythonMajor', 3)), sys.version.split()[0])
for package, expected_version in policy.get('requiredPythonPackages', {}).items():
    try:
        actual_version = importlib.metadata.version(str(package))
        add(f'python-package-{package}', actual_version == str(expected_version), f'version={actual_version} expected={expected_version}', remediation='Ejecuta scripts/toolchain/bootstrap_qualification_python.py --ensure para restaurar el entorno Python hermético de cualificación.')
    except importlib.metadata.PackageNotFoundError:
        add(f'python-package-{package}', False, f'version=<missing> expected={expected_version}', remediation='Ejecuta scripts/toolchain/bootstrap_qualification_python.py --ensure para instalar las dependencias Python bloqueadas de cualificación.')

if os.name == 'nt':
    long_paths = None
    try:
        import winreg  # type: ignore
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'SYSTEM\CurrentControlSet\Control\FileSystem') as key:
            long_paths = int(winreg.QueryValueEx(key, 'LongPathsEnabled')[0]) == 1
    except Exception:
        pass
    add('windows-long-paths', long_paths is True, f'enabled={long_paths}', required=False, remediation='Activa LongPathsEnabled si trabajas con rutas profundas.')

candidates = [candidate.__dict__ for candidate in discover_jdks(args.jdk_home or None)]
required_failures = [item for item in checks if item['required'] and not item['passed']]
report = {
    'schemaVersion': '1.0',
    'status': 'PASS' if not required_failures else 'FAIL',
    'full': args.full,
    'checks': checks,
    'toolchain': toolchain,
    'jdkCandidates': candidates,
}
out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
if not args.json_only:
    print(out)
raise SystemExit(1 if args.strict and required_failures else 0)
