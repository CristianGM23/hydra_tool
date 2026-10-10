[CmdletBinding()]
param([switch]$StructuralOnly)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA R6C4 Fix173: comprobando limite entre workspace y fuente canonica.'
    & python .\scripts\validation\verify_r6c4_fix173_release_boundary.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix173: fallo del gate de integridad y empaquetado.' }
    $qualified = Join-Path $PSScriptRoot 'validate_full_tree_fix171.ps1'
    if (-not (Test-Path -LiteralPath $qualified -PathType Leaf)) {
        throw 'Fix173: falta el validador global Fix171.'
    }
    if ($StructuralOnly) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $qualified -StructuralOnly
    } else {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $qualified
    }
    $result = $LASTEXITCODE
    if ($result -ne 0) { Write-Host "HYDRA R6C4 FIX173 QUALIFICATION=FAIL exit=$result" -ForegroundColor Red; exit $result }
    Write-Host 'HYDRA R6C4 FIX173 QUALIFICATION=PASS'
} finally { Pop-Location }
