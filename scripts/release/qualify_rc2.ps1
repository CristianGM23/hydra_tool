param(
    [string]$HydraRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [Alias("Jdk24Home")]
    [string]$JdkHome = "",
    [switch]$Resume,
    [string]$From = "",
    [string]$Only = "",
    [switch]$RerunFailed,
    [switch]$StructuralOnly
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
        python .\scripts\toolchain\discover_jdks.py --all
        exit 2
    }
    $discovery = $raw | ConvertFrom-Json
    $jdk = [string]$discovery.selected.root
    $jdkBin = Join-Path $jdk "bin"
    $env:HYDRA_JAVA_HOME = $jdk; $env:JAVA_HOME = $jdk; $env:PATH = "$jdkBin;$env:PATH"
    Write-Host "PASS JDK selected: $jdk" -ForegroundColor Green
    Write-Host "START Python qualification environment" -ForegroundColor Cyan
    $pythonRaw = & python .\scripts\toolchain\bootstrap_qualification_python.py --ensure --json
    if ($LASTEXITCODE -ne 0 -or !$pythonRaw) { throw "Qualification Python bootstrap FAIL" }
    $pythonToolchain = $pythonRaw | ConvertFrom-Json
    if ([string]$pythonToolchain.status -ne "PASS") { throw "Qualification Python bootstrap FAIL: $($pythonToolchain.error)" }
    $qualificationPython = [string]$pythonToolchain.interpreter
    Write-Host "PASS Python qualification environment: $qualificationPython" -ForegroundColor Green
    & $qualificationPython .\scripts\doctor\hydra_doctor.py --strict
    if ($LASTEXITCODE -ne 0) { throw "Environment Doctor FAIL" }
    $argsList = @(".\scripts\release\qualify_rc2.py")
    if (!$StructuralOnly) { $argsList += "--full" }
    if ($Resume) { $argsList += "--resume" }
    if ($From) { $argsList += @("--from", $From) }
    if ($Only) { $argsList += @("--only", $Only) }
    if ($RerunFailed) { $argsList += "--rerun-failed" }
    & $qualificationPython @argsList
    if ($LASTEXITCODE -ne 0) {
        & $qualificationPython .\scripts\release\diagnose_qualification.py
        exit $LASTEXITCODE
    }
} finally { Pop-Location }
