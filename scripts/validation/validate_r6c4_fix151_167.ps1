[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$PythonExe = "python"
)
$ErrorActionPreference = 'Stop'
$next = Join-Path $PSScriptRoot 'validate_r6c4_fix151_168.ps1'
if (-not (Test-Path -LiteralPath $next -PathType Leaf)) { throw "[PACKAGE-INTEGRITY] Falta 'scripts\validation\validate_r6c4_fix151_168.ps1'. Aplica R6C4 Fix168." }
& $next -JdkHome $JdkHome -PythonExe $PythonExe
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
