[CmdletBinding()]
param([switch]$StructuralOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA B+D Block 1: native parity and standalone release readiness.'
    $checks = @(
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_bd_block1.py') }
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_backend_spanish_method_docs.py') }
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_r6c4_fix171_logical_size.py') }
        @{ Tool = 'python'; Arguments = @('scripts/api/verify_openapi.py') }
        @{ Tool = 'python'; Arguments = @('scripts/validation/verify_manifest.py', '--allow-extra') }
        @{ Tool = 'node'; Arguments = @('scripts/validation/verify_frontend_spanish_coverage.cjs') }
    )
    foreach ($check in $checks) {
        $program = $check.Tool
        $arguments = $check.Arguments
        Write-Host "CHECK $program $($arguments -join ' ')"
        & $program @arguments
        if ($LASTEXITCODE -ne 0) { throw "B+D qualification failed at $program $($arguments -join ' ')" }
    }
    if (-not $StructuralOnly) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validation\validate_full_tree_fix173.ps1
        if ($LASTEXITCODE -ne 0) { throw 'B+D full-tree qualification failed' }
    }
    Write-Host 'HYDRA B+D BLOCK1=PASS' -ForegroundColor Green
} finally {
    Pop-Location
}
