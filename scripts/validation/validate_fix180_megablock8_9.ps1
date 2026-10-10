[CmdletBinding()]
param(
    [switch]$StructuralOnly,
    [switch]$ExecuteNativeExports,
    [string]$EmfExportZip = '',
    [string]$EmfSourceInventory = '',
    [string]$LangiumExportZip = '',
    [string]$LangiumSourceInventory = ''
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA Fix180 B8+B9+D8+D9: canonical obligations, exported source attestation, JavaScript runtime, model migrations, native OS smoke gating.'
    $steps = @(
        @('python', 'scripts/validation/verify_fix180_b8_b9.py'),
        @('node', 'scripts/validation/verify_fix180_runtime_semantics.mjs'),
        @('python', 'scripts/validation/verify_fix180_d8_d9.py'),
        @('python', 'scripts/validation/verify_fix180_native_runner.py')
    )
    foreach ($step in $steps) {
        Write-Host "CHECK $($step -join ' ')"
        $cmd = $step[0]; $arg = $step[1]
        & $cmd $arg
        if ($LASTEXITCODE -ne 0) { throw "Fix180 B8+B9+D8+D9 focal test failed: $arg" }
    }
    if (-not $StructuralOnly) {
        Write-Host 'CHECK actual HYDRA Java editor HTTP persistence, migration, restart and rollback'
        & python scripts/validation/fix180_real_editor_migration_e2e.py
        if ($LASTEXITCODE -ne 0) { throw 'Fix180 real Java generated-editor migration E2E failed' }
    }
    $selected = @($EmfExportZip, $EmfSourceInventory, $LangiumExportZip, $LangiumSourceInventory) | Where-Object { $_ -ne '' }
    if ($selected.Count -gt 0 -or $ExecuteNativeExports) {
        if ($selected.Count -ne 4) { throw 'Provide both real HYDRA export ZIPs and both exact canonical source inventories.' }
        $targets = @(@('emf',$EmfExportZip,$EmfSourceInventory),@('langium',$LangiumExportZip,$LangiumSourceInventory))
        foreach ($entry in $targets) {
            Write-Host "CHECK real exported $($entry[0]) project provenance and official toolchain"
            $args = @('scripts/validation/fix180_export_provenance.py','--zip',$entry[1],'--source-inventory',$entry[2], '--native-profile',$entry[0])
            if ($ExecuteNativeExports) { $args += @('--execute-native','--trusted-export') }
            & python @args
            if ($LASTEXITCODE -ne 0) { throw "Authentic $($entry[0]) export or native toolchain failed" }
        }
        if (-not $ExecuteNativeExports) {
            Write-Host 'NOTE Native export projects inspected only. Build and semantic parity are NOT CERTIFIED.'
        }
    }
    $inheritedArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File',
        '.\scripts\validation\validate_fix180_stage1.ps1')
    if ($StructuralOnly) { $inheritedArgs += '-StructuralOnly' }
    Write-Host 'CHECK inherited Fix174..Fix180 Stage1 + single global full-tree qualification'
    & powershell @inheritedArgs
    if ($LASTEXITCODE -ne 0) { throw 'Inherited HYDRA full-tree qualification failed' }
    Write-Host 'HYDRA FIX180 B8+B9+D8+D9=PASS for executed scoped tests' -ForegroundColor Green
    Write-Host 'NOT CERTIFIED without official generated-project outputs and independent behavior: EMF/Langium parity, native OS model round-trip, signed Tauri updaters.'
} finally { Pop-Location }
