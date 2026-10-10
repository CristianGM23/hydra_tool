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
    Write-Host 'HYDRA Fix179: megabloque B6+B7+D6+D7 (native runtime parity and standalone deployment lifecycle).'
    Write-Host 'CHECK new B6/B7/D6/D7 regression tests'
    & python scripts/validation/verify_bd_megablock6_7.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix179 regression tests failed' }
    if (-not $StructuralOnly) {
        Write-Host 'CHECK real Java/Node semantic parity, Java ZIP, signed install, HTTP, upgrade and rollback'
        & python scripts/validation/bd67_real_e2e.py
        if ($LASTEXITCODE -ne 0) { throw 'Fix179 integrated real E2E failed' }
    }
    # Reuse the inherited full-tree gate rather than running Maven, Vitest,
    # Playwright or older B+D macroblocks repeatedly.
    $inheritedArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File',
        '.\scripts\validation\validate_bd_block5.ps1')
    if ($StructuralOnly) { $inheritedArgs += '-StructuralOnly' }
    if (-not $StructuralOnly -and ($NativeMatrix -ne '' -or $NativePolicy -ne '')) {
        if ($NativeMatrix -eq '' -or $NativePolicy -eq '') {
            throw 'Provide -NativeMatrix and -NativePolicy together.'
        }
        $inheritedArgs += @('-NativeMatrix',$NativeMatrix,'-NativePolicy',$NativePolicy,
                            '-GeneratedRoot',$GeneratedRoot)
    }
    Write-Host 'CHECK inherited B+D1..B+D5 and single full-tree qualification at end'
    & powershell @inheritedArgs
    if ($LASTEXITCODE -ne 0) { throw 'Inherited HYDRA B+D qualification failed' }
    Write-Host 'HYDRA FIX179 B6+B7+D6+D7=PASS' -ForegroundColor Green
    Write-Host 'NOTE External EMF/Sirius/Xtext/GLSP and OS installer certification requires their official toolchains.'
} finally {
    Pop-Location
}
