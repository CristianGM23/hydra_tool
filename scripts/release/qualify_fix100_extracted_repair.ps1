param(
    [string]$HydraRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [Alias("Jdk24Home")]
    [string]$JdkHome = "",
    [switch]$Resume,
    [switch]$RerunFailed
)
$ErrorActionPreference = "Stop"
$HydraRoot = [System.IO.Path]::GetFullPath($HydraRoot)
Push-Location $HydraRoot
try {
    $discoverArgs = @(".\scripts\toolchain\discover_jdks.py", "--json")
    if ($JdkHome) { $discoverArgs += @("--jdk-home", $JdkHome) }
    $raw = & python @discoverArgs
    if ($LASTEXITCODE -ne 0 -or !$raw) { throw "No se ha encontrado un JDK 24/25 compatible." }
    $discovery = $raw | ConvertFrom-Json
    $jdk = [string]$discovery.selected.root
    $jdkBin = Join-Path $jdk "bin"
    $env:HYDRA_JAVA_HOME = $jdk
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdkBin;$env:PATH"
    Write-Host "PASS JDK selected: $jdk" -ForegroundColor Green

    $pythonRaw = & python .\scripts\toolchain\bootstrap_qualification_python.py --ensure --json
    if ($LASTEXITCODE -ne 0 -or !$pythonRaw) { throw "Qualification Python bootstrap FAIL" }
    $pythonToolchain = $pythonRaw | ConvertFrom-Json
    if ([string]$pythonToolchain.status -ne "PASS") { throw "Qualification Python bootstrap FAIL: $($pythonToolchain.error)" }
    $qualificationPython = [string]$pythonToolchain.interpreter
    Write-Host "PASS Python qualification environment: $qualificationPython" -ForegroundColor Green

    $qualArgs = @(".\scripts\release\qualify_fix100_extracted_repair.py")
    if ($Resume) { $qualArgs += "--resume" }
    if ($RerunFailed) { $qualArgs += "--rerun-failed" }
    & $qualificationPython @qualArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
