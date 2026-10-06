param(
    [switch]$Full,
    [switch]$Strict,
    [string]$JdkHome = ""
)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$arguments = @(".\scripts\doctor\hydra_doctor.py")
if ($Full) { $arguments += "--full" }
if ($Strict) { $arguments += "--strict" }
if ($JdkHome) { $arguments += @("--jdk-home", $JdkHome) }
Push-Location $Root
try {
    & python @arguments
    exit $LASTEXITCODE
} finally { Pop-Location }
