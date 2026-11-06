param([switch]$NativeRequired)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Check-Python([string]$Script) {
    & python $Script
    if ($LASTEXITCODE -ne 0) { throw "FIX185 FAIL: $Script returned $LASTEXITCODE" }
}
& python '.\scripts\validation\verify_manifest.py' --allow-extra
if ($LASTEXITCODE -ne 0) { throw 'FIX185 FAIL: canonical manifest failed' } # Preserve Archify and other workspace extras
Check-Python '.\scripts\validation\verify_fix185_langium_multiplicity.py'
Check-Python '.\scripts\validation\verify_fix185_glsp_storage.py'
Check-Python '.\scripts\validation\verify_fix185_native_closure.py'
Check-Python '.\scripts\validation\verify_fix184_interop_distribution.py'
Check-Python '.\scripts\validation\verify_fix182_release_gate.py'
Check-Python '.\scripts\validation\verify_fix183_native_bridge.py'
if ($NativeRequired) {
    $required = @('HYDRA_RELEASE_PACKAGE','HYDRA_NATIVE_EVIDENCE_DIR','HYDRA_NATIVE_TRUST_POLICY',
                  'HYDRA_NATIVE_ORACLE_DIR','HYDRA_NATIVE_OBSERVED_DIR')
    foreach ($name in $required) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            throw "FIX185 NOT_CERTIFIED: $name must be set to independently collected trusted native evidence"
        }
    }
    & python '.\scripts\distribution\fix185_complete_line_gate.py' `
        --package $env:HYDRA_RELEASE_PACKAGE `
        --canonical-manifest '.\MANIFEST_SHA256.txt' `
        --evidence-dir $env:HYDRA_NATIVE_EVIDENCE_DIR `
        --trust-policy $env:HYDRA_NATIVE_TRUST_POLICY `
        --oracle-root $env:HYDRA_NATIVE_ORACLE_DIR `
        --observed-root $env:HYDRA_NATIVE_OBSERVED_DIR `
        --report '.\target\release\fix185-native-closure.json'
    if ($LASTEXITCODE -ne 0) { throw 'FIX185 NOT_CERTIFIED: at least one required official native/E2E acceptance case is blocked' }
    Write-Host 'HYDRA FIX185 NATIVE COMPLETE=PASS (signed real runner attestations + matched independent oracles)'
} else {
    Write-Host 'HYDRA FIX185 LOCAL REGRESSIONS=PASS'
    Write-Host 'HYDRA FIX185 FULL NATIVE CERTIFICATION=NOT_EXECUTED (use -NativeRequired with verified CI evidence)'
}
