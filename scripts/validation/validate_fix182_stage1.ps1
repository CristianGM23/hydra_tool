[CmdletBinding()]
param(
    [switch]$StructuralOnly,
    [string]$NativeReleasePackage = '',
    [string]$EvidenceDirectory = '',
    [string]$TrustPolicy = '',
    [string[]]$RequiredTargets = @()
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA Fix182 Stage1 B12+B13+D12+D13: real Sirius/GLSP generator fidelity, protected native round-trip, pinned release attestations and evidence UI.'
    Write-Host 'CHECK isolated Java21 semantic path behavior and production source wiring'
    & python scripts/validation/verify_fix182_stage1.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix182 Stage1 Java/wiring contracts failed' }
    Write-Host 'CHECK deterministic atomic OpenAPI output and Windows file-lock regressions'
    & python scripts/validation/verify_fix182_openapi_windows.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix182 Windows-safe OpenAPI generation regressions failed' }
    Write-Host 'CHECK native publication evidence trust, adversarial changes, threshold and scope'
    & python scripts/validation/verify_fix182_release_gate.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix182 native release trust regressions failed' }

    $selected = @($NativeReleasePackage, $EvidenceDirectory, $TrustPolicy) | Where-Object { $_ -ne '' }
    if ($selected.Count -gt 0 -or $RequiredTargets.Count -gt 0) {
        if ($selected.Count -ne 3 -or $RequiredTargets.Count -eq 0) {
            throw 'Provide native package, evidence directory, pinned trust policy and at least one required technology/OS target together.'
        }
        $args = @('scripts/distribution/fix182_native_release_gate.py',
            '--package', $NativeReleasePackage, '--evidence-dir', $EvidenceDirectory,
            '--trust-policy', $TrustPolicy, '--canonical-manifest', 'MANIFEST_SHA256.txt')
        foreach($target in $RequiredTargets) { $args += @('--require',$target) }
        & python @args
        if ($LASTEXITCODE -ne 0) { throw 'Requested real native release attestation FAILED or BLOCKED' }
    } else {
        Write-Host 'NOTE No real official toolchain / trusted native OS attestation supplied. External technology and installer certification NOT CERTIFIED.'
    }
    $inherited = @('-NoProfile','-ExecutionPolicy','Bypass','-File',
        '.\scripts\validation\validate_fix180_megablock8_9.ps1')
    if ($StructuralOnly) { $inherited += '-StructuralOnly' }
    Write-Host 'CHECK inherited Fix174..Fix180 and single global full-tree qualification'
    & powershell @inherited
    if ($LASTEXITCODE -ne 0) { throw 'Inherited HYDRA qualification failed' }
    Write-Host 'HYDRA FIX182 STAGE1=PASS for tested subset' -ForegroundColor Green
    Write-Host 'NOT CERTIFIED: Fix181 unimplemented, native Sirius/GLSP/Xtext/ANTLR/BESSER parity and signed Tauri model lifecycle without official toolchains and trusted OS evidence.'
} finally { Pop-Location }
