[CmdletBinding()]
param(
    [switch]$StructuralOnly,
    [string]$NativeMatrix = '',
    [string]$GeneratedRoot = 'data/generated-projects'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA B+D BLOCK4: cross-technology parity, signed immutable publications, native bundle inventory.'
    $checks = @(
        @('python', 'scripts/validation/verify_bd_block1.py'),
        @('python', 'scripts/validation/verify_bd_block2.py'),
        @('python', 'scripts/validation/verify_bd_block3.py'),
        @('python', 'scripts/validation/verify_bd_block4.py'),
        @('node', 'scripts/validation/verify_frontend_spanish_coverage.cjs'),
        @('python', 'scripts/validation/verify_r6c4_fix171_logical_size.py'),
        @('python', 'scripts/api/verify_openapi.py'),
        @('python', 'scripts/validation/verify_manifest.py', '--allow-extra')
    )
    foreach ($check in $checks) {
        $tool = $check[0]
        $toolArgs = @($check | Select-Object -Skip 1)
        Write-Host "CHECK $tool $($toolArgs -join ' ')"
        & $tool @toolArgs
        if ($LASTEXITCODE -ne 0) { throw "BD4 structural/behavioral gate failed: $($toolArgs -join ' ')" }
    }
    if (-not $StructuralOnly) {
        & python -c 'import cryptography; print("BD4_ED25519_DEPENDENCY=PASS")'
        if ($LASTEXITCODE -ne 0) {
            throw 'Install optional signing dependency: python -m pip install -r scripts/distribution/requirements-signing.txt'
        }
        Write-Host 'CHECK real Node semantic parity -> offline -> Java ZIP -> two-key signed publication'
        & python scripts/validation/bd4_real_e2e.py
        if ($LASTEXITCODE -ne 0) { throw 'B4+D4 real E2E qualification failed' }
        if ($NativeMatrix) {
            Write-Host 'CHECK explicit, allow-listed native-project parity matrix'
            & python scripts/validation/run_native_parity_matrix.py --root $GeneratedRoot --matrix $NativeMatrix --execute --report target/bd4/native-parity-matrix.json
            if ($LASTEXITCODE -ne 0) { throw 'B4 native external toolchain matrix failed or is unavailable' }
        }
        Write-Host 'CHECK inherited full-tree qualification (single final pass)'
        & powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validation\validate_full_tree_fix173.ps1
        if ($LASTEXITCODE -ne 0) { throw 'B4+D4 inherited full-tree qualification failed' }
    }
    Write-Host 'HYDRA B+D BLOCK4=PASS' -ForegroundColor Green
} finally { Pop-Location }
