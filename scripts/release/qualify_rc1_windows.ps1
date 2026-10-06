param(
    [string]$HydraRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
    [Alias("Jdk24Home")]
    [string]$JdkHome = "",
    [switch]$Resume,
    [string]$From = "",
    [string]$Only = "",
    [switch]$RerunFailed
)
$canonical = Join-Path $PSScriptRoot "qualify_rc1.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $canonical -HydraRoot $HydraRoot -JdkHome $JdkHome -Resume:$Resume -From $From -Only $Only -RerunFailed:$RerunFailed
exit $LASTEXITCODE
