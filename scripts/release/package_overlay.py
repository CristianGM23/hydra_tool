from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path

from source_inventory import source_inventory, source_root

ROOT = Path(__file__).resolve().parents[2]
BUFFER_SIZE = 1024 * 1024


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(BUFFER_SIZE), b''):
            digest.update(chunk)
    return digest.hexdigest()


def write_zip_file(output: zipfile.ZipFile, path: Path, relative: str) -> None:
    mode = 0o100755 if relative == 'mvnw' else 0o100644
    info = zipfile.ZipInfo(relative, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = mode << 16
    with path.open('rb') as source, output.open(info, 'w') as destination:
        while chunk := source.read(BUFFER_SIZE):
            destination.write(chunk)


# Explicit cleanup for development-only sources that may survive when an overlay is
# applied over a workspace that was previously updated in place. These paths are
# not guaranteed to be present in every baseline inventory, so a normal diff cannot delete them.
STALE_PRE_GA_PATHS = (
    'src/main/java/com/example/editor/migration',
    'src/main/java/com/example/editor/metamodel/migration',
    'src/test/java/com/example/editor/migration',
    'src/test/java/com/example/editor/metamodel/migration',
    'src/test/java/com/example/editor/integration/compatibility/HydraV10CompatibilityPreservationTests.java',
    'src/test/java/com/example/editor/EditorBootstrapTests.java',
    'src/test/java/com/example/editor/editor/definition',
)
parser = argparse.ArgumentParser()
parser.add_argument('--baseline', required=True)
parser.add_argument('--baseline-version', required=True)
args = parser.parse_args()
BASE = Path(args.baseline).resolve()
OUT = ROOT / 'target/release'
OUT.mkdir(parents=True, exist_ok=True)
version = json.loads((ROOT / 'config/hydra-version.json').read_text(encoding='utf-8'))['version']

with source_root(BASE) as base_root:
    base_paths = source_inventory(base_root, args.baseline_version)
    base_hashes = {relative: sha256_file(path) for relative, path in base_paths.items()}
work_paths = source_inventory(ROOT, version)
work_hashes = {relative: sha256_file(path) for relative, path in work_paths.items()}
changed = [relative for relative in sorted(set(base_hashes) | set(work_hashes)) if base_hashes.get(relative) != work_hashes.get(relative)]
added = [relative for relative in changed if relative not in base_hashes]
deleted = [relative for relative in changed if relative not in work_hashes]
modified = [relative for relative in changed if relative in base_hashes and relative in work_hashes]
payload = sorted(work_paths)
repair_carried = [relative for relative in payload if relative not in changed]

archive = OUT / f'HYDRA_V{version}_overlay_over_V{args.baseline_version}.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
    for relative in payload:
        path = work_paths[relative]
        write_zip_file(output, path, relative)

manifest = {
    'schemaVersion': '1.0',
    'version': version,
    'baseline': args.baseline_version,
    'repairMode': 'FULL_SOURCE_SELF_HEALING',
    'changed': len(changed),
    'payloadFiles': len(payload),
    'repairCarriedForward': len(repair_carried),
    'added': added,
    'modified': modified,
    'deleted': deleted,
    'cleanupPaths': list(STALE_PRE_GA_PATHS),
    'files': {relative: work_hashes[relative] for relative in payload},
}
manifest_path = OUT / f'HYDRA_V{version}_overlay_manifest.json'
manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

apply_ps1 = OUT / f'APLICAR_HYDRA_V{version}_OVERLAY_SOBRE_V{args.baseline_version}.ps1'
apply_cmd = OUT / f'APLICAR_HYDRA_V{version}_OVERLAY_SOBRE_V{args.baseline_version}.cmd'
apply_ps1.write_text(fr'''param(
    [Parameter(Mandatory=$true)]
    [string]$Target
)
$ErrorActionPreference = 'Stop'
$targetRoot = [IO.Path]::GetFullPath($Target)
if (-not (Test-Path -LiteralPath $targetRoot -PathType Container)) {{ throw "HYDRA target directory does not exist: $targetRoot" }}
$releaseRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$manifestPath = Join-Path $releaseRoot '{manifest_path.name}'
$overlayPath = Join-Path $releaseRoot '{archive.name}'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {{ throw "Overlay manifest is missing: $manifestPath" }}
if (-not (Test-Path -LiteralPath $overlayPath -PathType Leaf)) {{ throw "Overlay archive is missing: $overlayPath" }}
$versionPath = Join-Path $targetRoot 'config\hydra-version.json'
if (-not (Test-Path -LiteralPath $versionPath -PathType Leaf)) {{ throw "Target does not contain config/hydra-version.json" }}
$current = (Get-Content -Raw -LiteralPath $versionPath | ConvertFrom-Json).version
if ($current -ne '{args.baseline_version}') {{ throw "Overlay requires baseline {args.baseline_version}; target reports $current" }}
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.version -ne '{version}' -or $manifest.baseline -ne '{args.baseline_version}') {{ throw "Overlay manifest version mismatch" }}
if ($manifest.repairMode -ne 'FULL_SOURCE_SELF_HEALING') {{ throw "Overlay repair mode is not supported" }}
$prefix = $targetRoot.TrimEnd([char[]]'\/') + [IO.Path]::DirectorySeparatorChar
foreach ($relative in @($manifest.cleanupPaths)) {{
    $candidate = [IO.Path]::GetFullPath((Join-Path $targetRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)))
    if (-not ($candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase))) {{ throw "Unsafe cleanup path in overlay manifest: $relative" }}
    if (Test-Path -LiteralPath $candidate) {{ Remove-Item -LiteralPath $candidate -Recurse -Force }}
}}
foreach ($relative in @($manifest.deleted)) {{
    $candidate = [IO.Path]::GetFullPath((Join-Path $targetRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)))
    if (-not ($candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase))) {{ throw "Unsafe deletion path in overlay manifest: $relative" }}
    if (Test-Path -LiteralPath $candidate) {{ Remove-Item -LiteralPath $candidate -Recurse -Force }}
}}
Expand-Archive -LiteralPath $overlayPath -DestinationPath $targetRoot -Force
$updated = (Get-Content -Raw -LiteralPath $versionPath | ConvertFrom-Json).version
if ($updated -ne '{version}') {{ throw "Overlay application finished with unexpected target version $updated" }}
Write-Host 'HYDRA overlay applied successfully.'
''', encoding='utf-8')
apply_cmd.write_bytes(('\r\n'.join([
    '@echo off',
    'setlocal EnableExtensions',
    'if "%~1"=="" (',
    r'  echo Usage: %~nx0 C:\path\to\HYDRA',
    '  exit /b 2',
    ')',
    f'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0{apply_ps1.name}" -Target "%~1"',
    'exit /b %ERRORLEVEL%',
    '',
])).encode('utf-8'))
sha = sha256_file(archive)
(OUT / (archive.name + '.sha256')).write_text(f'{sha}  {archive.name}\n', encoding='utf-8')
print(archive)
print(f'changed={len(changed)} payload={len(payload)} repair-carried={len(repair_carried)} added={len(added)} modified={len(modified)} deleted={len(deleted)}')
print('sha256=' + sha)
