[CmdletBinding()]
param([switch]$StructuralOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA Fix180 Stage 1: REAL generated standalone Java server, model persistence and conditional writes.'
    Write-Host 'CHECK Java21 generated-server compilation and real HTTP model lifecycle'
    & python scripts/validation/verify_fix180_generated_model_lifecycle.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix180 real editor lifecycle tests failed' }
    Write-Host 'CHECK generated JavaScript syntax (visual/textual)'
    & node --check src/main/resources/standalone-template/frontend/app.js
    if ($LASTEXITCODE -ne 0) { throw 'Fix180 visual editor JavaScript syntax failed' }
    & node --check src/main/resources/standalone-template/frontend/textual-app.js
    if ($LASTEXITCODE -ne 0) { throw 'Fix180 textual editor JavaScript syntax failed' }
    $inheritedArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File',
              '.\scripts\validation\validate_bd_megablock6_7.ps1')
    if ($StructuralOnly) { $inheritedArgs += '-StructuralOnly' }
    Write-Host 'CHECK inherited Fix174..Fix179 and SINGLE inherited global qualification'
    & powershell @inheritedArgs
    if ($LASTEXITCODE -ne 0) { throw 'Inherited Fix179 qualification failed' }
    Write-Host 'HYDRA FIX180 STAGE1=PASS' -ForegroundColor Green
    Write-Host 'NOT CERTIFIED: EMF/Langium native exporter parity and Windows/Linux/macOS Tauri installation.'
} finally {
    Pop-Location
}
