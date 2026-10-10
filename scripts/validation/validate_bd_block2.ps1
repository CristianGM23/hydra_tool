[CmdletBinding()]
param([switch]$StructuralOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA B+D Block 2: native E2E, independent web builds, deployable assets and security.'
    $checks = @(
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_bd_block1.py') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_bd_block2.py') },
        @{ Tool = 'node'; Arguments = @('--test', 'reference-projects/bd2-standalone-smoke/scripts/editor.test.mjs') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_backend_spanish_method_docs.py') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_r6c4_fix171_logical_size.py') },
        @{ Tool = 'python'; Arguments = @('scripts/api/verify_openapi.py') },
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_manifest.py', '--allow-extra') },
        @{ Tool = 'node'; Arguments = @('scripts/validation/verify_frontend_spanish_coverage.cjs') }
    )
    foreach ($check in $checks) {
        Write-Host "CHECK $($check.Tool) $($check.Arguments -join ' ')"
        & $check.Tool @($check.Arguments)
        if ($LASTEXITCODE -ne 0) { throw "B+D Block 2 check failed at $($check.Tool)" }
    }
    if (-not $StructuralOnly) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validation\validate_full_tree_fix173.ps1
        if ($LASTEXITCODE -ne 0) { throw 'B+D full-tree qualification failed' }
    }
    Write-Host 'HYDRA B+D BLOCK2=PASS' -ForegroundColor Green
} finally { Pop-Location }
