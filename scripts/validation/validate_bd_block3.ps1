[CmdletBinding()]
param([switch]$StructuralOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA B+D Block 3: challenge-bound functional parity and immutable standalone release channels.'
    $checks = @(
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_bd_block1.py') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_bd_block2.py') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_bd_block3.py') },
        @{ Tool = 'node'; Arguments = @('--test','reference-projects/bd2-standalone-smoke/scripts/editor.test.mjs') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_r6c4_fix171_logical_size.py') },
        @{ Tool = 'python'; Arguments = @('scripts/api/verify_openapi.py') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_manifest.py','--allow-extra') },
        @{ Tool = 'node'; Arguments = @('scripts/validation/verify_frontend_spanish_coverage.cjs') }
    )
    foreach ($check in $checks) {
        Write-Host "CHECK $($check.Tool) $($check.Arguments -join ' ')"
        & $check.Tool @($check.Arguments)
        if ($LASTEXITCODE -ne 0) { throw "B+D Block 3 gate failed at $($check.Tool)" }
    }
    if (-not $StructuralOnly) {
        Write-Host 'CHECK real generated-editor native behavior -> Java deployment -> immutable release'
        & python scripts/validation/bd3_real_e2e.py
        if ($LASTEXITCODE -ne 0) { throw 'B+D Block 3 real E2E qualification failed' }
        & powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validation\validate_full_tree_fix173.ps1
        if ($LASTEXITCODE -ne 0) { throw 'B+D Block 3 full-tree qualification failed' }
    }
    Write-Host 'HYDRA B+D BLOCK3=PASS' -ForegroundColor Green
} finally { Pop-Location }
