[CmdletBinding()]
param(
    [switch]$StructuralOnly,
    [string]$NativeMatrix = '',
    [string]$NativePolicy = '',
    [string]$GeneratedRoot = 'data/generated-projects'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA B+D Block 5: native capability portfolio, signed standalone runtime and secure web preflight.'
    $steps = @(
        @('python','scripts/validation/verify_bd_block1.py'),
        @('python','scripts/validation/verify_bd_block2.py'),
        @('python','scripts/validation/verify_bd_block3.py'),
        @('python','scripts/validation/verify_bd_block4.py'),
        @('python','scripts/validation/verify_bd_block5.py'),
        @('python','scripts/validation/verify_r6c4_fix171_logical_size.py'),
        @('python','scripts/api/verify_openapi.py'),
        @('node','scripts/validation/verify_frontend_spanish_coverage.cjs'),
        @('python','scripts/validation/verify_manifest.py','--allow-extra')
    )
    foreach ($step in $steps) {
        $cmd = $step[0]
        $arguments = @($step | Select-Object -Skip 1)
        Write-Host "CHECK $cmd $($arguments -join ' ')"
        & $cmd @arguments
        if ($LASTEXITCODE -ne 0) { throw "HYDRA B5+D5 qualification failure: $($arguments -join ' ')" }
    }
    if (-not $StructuralOnly) {
        & python -c 'from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey'
        if ($LASTEXITCODE -ne 0) {
            throw 'Install optional signer library: python -m pip install -r scripts/distribution/requirements-signing.txt'
        }
        Write-Host 'BD5_ED25519_DEPENDENCY=PASS'
        Write-Host 'CHECK real Node behavior -> Java PWA ZIP -> dual signed local publication and HTTP runtime'
        & python scripts/validation/bd5_real_e2e.py
        if ($LASTEXITCODE -ne 0) { throw 'HYDRA B5+D5 real end-to-end editor runtime failed' }
        if (($NativeMatrix -ne '') -or ($NativePolicy -ne '')) {
            if (($NativeMatrix -eq '') -or ($NativePolicy -eq '')) {
                throw 'Specify BOTH -NativeMatrix and -NativePolicy to require native interoperability certification.'
            }
            Write-Host 'CHECK external native technology portfolio (real projects and allow-listed runners)'
            & python scripts/validation/run_native_parity_portfolio.py --root $GeneratedRoot --matrix $NativeMatrix --policy $NativePolicy --execute --report target/bd5/native-parity-portfolio.json
            if ($LASTEXITCODE -ne 0) { throw 'Some requested native technology capabilities lack certified fresh evidence' }
        }
        Write-Host 'CHECK inherited global qualification (single execution at end of macrobloque)'
        & powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\validation\validate_full_tree_fix173.ps1
        if ($LASTEXITCODE -ne 0) { throw 'HYDRA global full-tree qualification failed' }
    }
    Write-Host 'HYDRA B+D BLOCK5=PASS' -ForegroundColor Green
} finally {
    Pop-Location
}
