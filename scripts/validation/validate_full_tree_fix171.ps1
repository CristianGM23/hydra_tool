[CmdletBinding()]
param([switch]$StructuralOnly)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA R6C4 Fix171: verificando integridad canonica y limites logicos antes de la cualificacion global.'
    & python .\scripts\validation\verify_manifest.py --allow-extra
    if ($LASTEXITCODE -ne 0) { throw 'Fix171: fallo de integridad de source canonico.' }
    & python .\scripts\validation\verify_r6c4_fix171_logical_size.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix171: fuente de produccion excede limites logicos.' }
    $qualified = Join-Path $PSScriptRoot 'validate_full_tree_fix170.ps1'
    if (-not (Test-Path -LiteralPath $qualified -PathType Leaf)) {
        throw 'Fix171: falta el validador global Fix170, necesario para conservar la cobertura completa.'
    }
    if ($StructuralOnly) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $qualified -StructuralOnly
    } else {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $qualified
    }
    $result = $LASTEXITCODE
    if ($result -ne 0) { Write-Host "HYDRA R6C4 FIX171 QUALIFICATION=FAIL exit=$result" -ForegroundColor Red; exit $result }
    Write-Host 'HYDRA R6C4 FIX171 QUALIFICATION=PASS'
} finally { Pop-Location }
