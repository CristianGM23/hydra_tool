param(
    [string]$HydraRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [Alias("Jdk24Home")]
    [string]$JdkHome = "",
    [switch]$Resume,
    [string]$From = "",
    [string]$Only = "",
    [switch]$RerunFailed
)

$ErrorActionPreference = "Stop"
$HydraRoot = [System.IO.Path]::GetFullPath($HydraRoot)
Push-Location $HydraRoot
try {
    $discoverArgs = @(".\scripts\toolchain\discover_jdks.py", "--json")
    if ($JdkHome) { $discoverArgs += @("--jdk-home", $JdkHome) }
    $raw = & python @discoverArgs
    if ($LASTEXITCODE -ne 0 -or !$raw) {
        Write-Host "FAIL JDK" -ForegroundColor Red
        Write-Host "No se ha encontrado un JDK 24/25 completo (java + javac)."
        Write-Host "Ejecuta para ver candidatos y motivos:"
        Write-Host "  python .\scripts\toolchain\discover_jdks.py --all"
        exit 2
    }
    $discovery = $raw | ConvertFrom-Json
    if (!$discovery.selected) {
        Write-Host "FAIL JDK" -ForegroundColor Red
        python .\scripts\toolchain\discover_jdks.py --all
        exit 2
    }
    $jdk = [string]$discovery.selected.root
    $jdkBin = Join-Path $jdk "bin"
    $env:HYDRA_JAVA_HOME = $jdk
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdkBin;$env:PATH"
    Write-Host "PASS JDK selected: $jdk" -ForegroundColor Green
    Write-Host "PASS JDK source: $($discovery.selected.source)" -ForegroundColor Green
    Write-Host "PASS Java feature: $($discovery.selected.feature) (bytecode target=24; qualification=24..25)" -ForegroundColor Green

    python .\scripts\doctor\hydra_doctor.py --strict
    if ($LASTEXITCODE -ne 0) { throw "HYDRA Environment Doctor ha detectado requisitos incumplidos." }

    $qualArgs = @(".\scripts\release\qualify_rc1.py", "--full")
    if ($Resume) { $qualArgs += "--resume" }
    if ($From) { $qualArgs += @("--from", $From) }
    if ($Only) { $qualArgs += @("--only", $Only) }
    if ($RerunFailed) { $qualArgs += "--rerun-failed" }
    & python @qualArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "=== DIAGNOSTICO DE CUALIFICACION ==="
        python .\scripts\release\diagnose_qualification.py
        exit $LASTEXITCODE
    }
} finally { Pop-Location }
