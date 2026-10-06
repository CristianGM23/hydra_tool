from __future__ import annotations
from pathlib import Path
import argparse, hashlib, json, shutil, sys

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / 'distribution' / 'transformation-toolchains'
PACKAGE_ID = 'hydra-official-bridge'
VERSION = '2.0.0'
EXTERNAL = {
    'atl': ('ATL', 'hydra-atl-runtime.jar', 'hydra.atl.runtime.HydraAtlNativeRuntime'),
    'etl': ('ETL', 'hydra-etl-runtime.jar', 'hydra.etl.runtime.HydraEtlNativeRuntime'),
    'acceleo': ('ACCELEO', 'hydra-acceleo-runtime.jar', 'hydra.acceleo.runtime.HydraAcceleoNativeRuntime'),
    'egl': ('EGL', 'hydra-egl-runtime.jar', 'hydra.egl.runtime.HydraEglNativeRuntime'),
    'xtend': ('XTEND', 'hydra-xtend-runtime.jar', 'hydra.xtend.runtime.HydraXtendNativeRuntime'),
}

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def safe_engine_tree(source: Path):
    if not source.is_dir() or source.is_symlink():
        raise SystemExit('Engine payload must be a real directory')
    files=[]
    for item in sorted(source.rglob('*')):
        if item.is_symlink():
            raise SystemExit(f'Symlinks are forbidden in engine payloads: {item}')
        if item.is_file(): files.append(item)
    if not files: raise SystemExit('Engine payload is empty')
    return files

def main():
    ap=argparse.ArgumentParser(description='Assemble a trusted full HYDRA transformation toolchain package from administrator-supplied third-party engine files.')
    ap.add_argument('--language', required=True, choices=sorted(EXTERNAL))
    ap.add_argument('--engine-dir', required=True)
    ap.add_argument('--toolchain-version', required=True)
    args=ap.parse_args()
    slot=args.language
    language, driver, _ = EXTERNAL[slot]
    source=Path(args.engine_dir).resolve()
    files=safe_engine_tree(source)
    base=DIST/slot/PACKAGE_ID/VERSION
    payload=base/'payload'
    # Build the base catalog only when this exact language/version is absent.
    # Never rebuild all packages here: doing so could erase already assembled full payloads for other languages.
    if not payload.is_dir():
        import subprocess
        subprocess.run([sys.executable, str(ROOT/'scripts/toolchains/build_official_transformation_bridges.py')], check=True, cwd=ROOT)
    if not payload.is_dir():
        raise SystemExit('Base HYDRA package was not created')
    engine=payload/'engine'; engine.mkdir(parents=True, exist_ok=True)
    for item in files:
        rel=item.relative_to(source)
        target=engine/rel
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item,target)
    driver_path=engine/driver
    if not driver_path.is_file():
        raise SystemExit(f'Engine payload must include HYDRA driver {driver}')
    jars=sorted(p.relative_to(payload).as_posix() for p in engine.rglob('*.jar') if p.is_file())
    if not jars: raise SystemExit('Engine payload contains no JAR files')
    (engine/'classpath.txt').write_text('\n'.join(jars)+'\n',encoding='utf-8')
    profile=payload/'bridge.properties'
    text=profile.read_text(encoding='utf-8')
    text=text.replace('toolchain.version=\n',f'toolchain.version={args.toolchain_version.strip()}\n')
    profile.write_text(text,encoding='utf-8')
    toolchain=json.loads((payload/'toolchain.json').read_text(encoding='utf-8'))
    toolchain['toolchainVersion']=args.toolchain_version.strip()
    (payload/'toolchain.json').write_text(json.dumps(toolchain,indent=2)+'\n',encoding='utf-8')
    checksums={p.relative_to(payload).as_posix():sha(p) for p in sorted(payload.rglob('*')) if p.is_file()}
    package=json.loads((base/'package.json').read_text(encoding='utf-8'))
    package['toolchainVersion']=args.toolchain_version.strip(); package['sha256']=checksums
    (base/'package.json').write_text(json.dumps(package,indent=2)+'\n',encoding='utf-8')
    print(base)

if __name__=='__main__': main()
