from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'toolchains' / 'official'
DIST = ROOT / 'distribution' / 'transformation-toolchains'
VERSION = '2.1.0'
PACKAGE_ID = 'hydra-official-bridge'

META = {
    'atl': ('ATL', 'HYDRA Official ATL Bridge', 'Eclipse ATL'),
    'etl': ('ETL', 'HYDRA Official ETL Bridge', 'Epsilon ETL'),
    'acceleo': ('ACCELEO', 'HYDRA Official Acceleo Bridge', 'Eclipse Acceleo'),
    'egl': ('EGL', 'HYDRA Official EGL Bridge', 'Epsilon EGL'),
    'java': ('JAVA', 'HYDRA Official Java Bridge', 'Java JDK'),
    'xtend': ('XTEND', 'HYDRA Official Xtend Bridge', 'Eclipse Xtend'),
    'python': ('PYTHON', 'HYDRA Official Python Bridge', 'Python'),
    'go': ('GO', 'HYDRA Official Go Bridge', 'Go'),
    'javascript': ('JAVASCRIPT', 'HYDRA Official JavaScript Bridge', 'Node.js'),
    'typescript': ('TYPESCRIPT', 'HYDRA Official TypeScript Bridge', 'Node.js + TypeScript runtime source'),
}

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

for slot, (language, bridge_name, toolchain_name) in META.items():
    profile = SOURCE / 'profiles' / f'{slot}.properties'
    if not profile.is_file():
        raise SystemExit(f'Missing official bridge profile: {profile}')
    base = DIST / slot / PACKAGE_ID / VERSION
    payload = base / 'payload'
    if base.exists():
        shutil.rmtree(base)
    payload.mkdir(parents=True)
    shutil.copy2(SOURCE / 'HydraOfficialBridge.java', payload / 'HydraOfficialBridge.java')
    shutil.copy2(profile, payload / 'bridge.properties')
    runtime_sources = {
        'java': 'HydraJavaNativeRuntime.java',
        'python': 'HydraPythonNativeRuntime.py',
        'go': 'HydraGoNativeRuntime.go',
        'javascript': 'HydraJavaScriptNativeRuntime.js',
        'typescript': 'HydraTypeScriptNativeRuntime.js',
    }
    runtime_source = runtime_sources.get(slot)
    if runtime_source:
        shutil.copy2(SOURCE / runtime_source, payload / runtime_source)
    if slot == 'typescript':
        shutil.copy2(SOURCE / 'HydraTypeScriptNativeRuntime.ts', payload / 'HydraTypeScriptNativeRuntime.ts')
    toolchain = {
        'schemaVersion': '1.0',
        'language': language,
        'bridgeName': bridge_name,
        'bridgeVersion': VERSION,
        'toolchainName': toolchain_name,
        'toolchainVersion': '',
        'command': ['${java}', '${bundle}/HydraOfficialBridge.java', '${bundle}/bridge.properties'],
    }
    (payload / 'toolchain.json').write_text(json.dumps(toolchain, indent=2) + '\n', encoding='utf-8')
    checksums = {
        path.relative_to(payload).as_posix(): digest(path)
        for path in sorted(payload.rglob('*')) if path.is_file()
    }
    package = {
        'schemaVersion': '1.0',
        'packageId': PACKAGE_ID,
        'packageVersion': VERSION,
        'language': language,
        'bridgeName': bridge_name,
        'bridgeVersion': VERSION,
        'toolchainName': toolchain_name,
        'toolchainVersion': '',
        'sha256': checksums,
    }
    (base / 'package.json').write_text(json.dumps(package, indent=2) + '\n', encoding='utf-8')

print(f'Built {len(META)} official HYDRA transformation bridge packages at {DIST}')
