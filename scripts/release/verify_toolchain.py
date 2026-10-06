from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
required = json.loads((ROOT / 'config/hydra-toolchain.json').read_text(encoding='utf-8'))
sys.path.insert(0, str(ROOT / 'scripts/toolchain'))
from hydra_environment import collect_toolchain  # noqa:E402


def parts(value: str) -> tuple[int, ...]:
    if not value:
        return ()
    try:
        return tuple(int(part) for part in value.split('.'))
    except ValueError:
        return ()


def same_major_at_least(actual: str, minimum: str) -> bool:
    a = parts(actual); r = parts(minimum)
    if not a or not r or a[0] != r[0]:
        return False
    width = max(len(a), len(r))
    return a + (0,) * (width - len(a)) >= r + (0,) * (width - len(r))


def java_feature_in_range(actual: str, minimum_feature: str, maximum_feature: str) -> bool:
    a = parts(actual); lo = parts(minimum_feature); hi = parts(maximum_feature)
    return bool(a and lo and hi and lo[0] <= a[0] <= hi[0])


def main() -> int:
    parser = argparse.ArgumentParser(description='Verifica el toolchain reproducible de HYDRA.')
    parser.add_argument('--strict', action='store_true', help='Exige versiones reproducibles de las herramientas de host.')
    parser.add_argument('--full', action='store_true', help='Exige también paquetes locales instalados con npm ci.')
    args = parser.parse_args()

    collected = collect_toolchain()
    tools = collected['tools']
    selected_jdk = collected.get('jdk')
    actual = {name: str(tools.get(name, {}).get('version') or '') for name in ('java','javac','node','npm','typescript','maven')}
    java_max = required.get('javaQualificationMax', required['java'])
    checks: list[tuple[str,bool,str,bool]] = [
        ('java', java_feature_in_range(actual['java'], required['java'], java_max), f"feature>={required['java']},<={java_max}", True),
        ('javac', java_feature_in_range(actual['javac'], required['java'], java_max), f"feature>={required['java']},<={java_max}", True),
        ('node', same_major_at_least(actual['node'], required['node']), f">={required['node']},<{parts(required['node'])[0] + 1}.0.0", True),
        ('npm', same_major_at_least(actual['npm'], required['npm']), f">={required['npm']},<{parts(required['npm'])[0] + 1}.0.0", True),
    ]
    maven_ok = actual['maven'] == required['maven'] if args.strict else actual['maven'] in {'', required['maven']}
    checks.append(('maven', maven_ok, required['maven'], True))
    ts_ok = actual['typescript'] == required['typescript']
    checks.append(('typescript', ts_ok, required['typescript'], args.full))

    inventory = {
        'schemaVersion':'2.1', 'strict':args.strict, 'full':args.full, 'contract':required,
        'selectedJdk':selected_jdk, 'hostCompatible':True, 'fullCompatible':True, 'tools':{}
    }
    required_failures = False
    for name, ok, contract, is_required in checks:
        item = tools.get(name, {})
        path = item.get('path')
        state = 'PASS' if ok else ('FAIL' if is_required else 'WARN')
        print(state, name, 'required=' + contract, 'actual=' + actual[name], 'executable=' + (str(path) if path else '<not found>'))
        inventory['tools'][name] = {
            'passed':ok, 'required':is_required, 'contract':contract, 'actual':actual[name],
            'executable':path, 'sha256':item.get('sha256'), 'probePath':item.get('probePath')
        }
        if is_required and not ok:
            required_failures = True
        if name != 'typescript' and not ok:
            inventory['hostCompatible'] = False
        if not ok:
            inventory['fullCompatible'] = False
    inventory['compatible'] = not required_failures

    out = ROOT / 'target/diagnostics/toolchain-inventory.json'; out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(inventory, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print('TOOLCHAIN_INVENTORY=' + str(out))
    if not inventory['hostCompatible']:
        print('HINT host: corrige JDK/Node/npm/Maven antes de continuar la cualificación.')
    if args.full and not ts_ok:
        print('HINT typescript: ejecuta npm ci dentro de frontend; TypeScript es una dependencia local bloqueada por package-lock.json.')
    return 1 if required_failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
