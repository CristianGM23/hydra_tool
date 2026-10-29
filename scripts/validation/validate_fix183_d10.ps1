[CmdletBinding()]
param(
    [switch]$StructuralOnly,
    [switch]$ExecuteTauriRustTests,
    [string]$GeneratedEditorRoot = ''
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    Write-Host 'HYDRA Fix183 D10: transporte real HTTP/Tauri, persistencia Rust, frontend empaquetado y regresiones.'
    & python scripts/validation/verify_fix183_native_bridge.py
    if ($LASTEXITCODE -ne 0) { throw 'Fix183 native project and frontend tests FAILED' }
    Write-Host 'CHECK both actual production JavaScript editor templates parse'
    & node --check src/main/resources/standalone-template/frontend/app.js
    if ($LASTEXITCODE -ne 0) { throw 'Fix183 visual editor JavaScript syntax FAILED' }
    & node --check src/main/resources/standalone-template/frontend/textual-app.js
    if ($LASTEXITCODE -ne 0) { throw 'Fix183 textual editor JavaScript syntax FAILED' }
    & node scripts/testing/fix183/native_transport.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Fix183 actual browser/native adapter tests FAILED' }
    if ($ExecuteTauriRustTests) {
        if ([string]::IsNullOrWhiteSpace($GeneratedEditorRoot)) {
            throw '-ExecuteTauriRustTests requires -GeneratedEditorRoot (a real generated editor).'
        }
        $editor = (Resolve-Path $GeneratedEditorRoot).Path
        Write-Host 'CHECK generated Tauri project and actual Rust unit tests (native OS toolchain)'
        & python scripts/distribution/prepare_generated_tauri_frontend.py --editor-root $editor
        if ($LASTEXITCODE -ne 0) { throw 'Generated Tauri frontend preparation FAILED' }
        $manifest = Join-Path $editor 'src-tauri\Cargo.toml'
        if (-not (Test-Path $manifest)) { throw 'No real generated src-tauri/Cargo.toml found' }
        & cargo test --manifest-path $manifest
        if ($LASTEXITCODE -ne 0) { throw 'Generated Rust persistence tests FAILED' }
        Write-Host 'FIX183_NATIVE_RUST_TESTS=PASS (unit tests only; installed GUI not certified)'
    } else {
        Write-Host 'NOT EXECUTED: Rust Cargo unit tests, native Tauri build and installed GUI lifecycle'
    }
    $args = @('-NoProfile','-ExecutionPolicy','Bypass','-File','.\scripts\validation\validate_fix182_stage1.ps1')
    if ($StructuralOnly) { $args += '-StructuralOnly' }
    Write-Host 'CHECK inherited Fix182 and single full-tree qualification'
    & powershell @args
    if ($LASTEXITCODE -ne 0) { throw 'Inherited HYDRA Fix182 qualification FAILED' }
    Write-Host 'HYDRA FIX183 D10 INCREMENT=PASS for executed scoped tests'
    Write-Host 'NOT CERTIFIED: installed Windows/Linux/macOS UI model lifecycle, signed updater, external MDE exporter parity'
} finally {
    Pop-Location
}
