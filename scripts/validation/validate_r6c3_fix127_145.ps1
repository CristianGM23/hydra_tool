[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$PythonExe = "python"
)
$ErrorActionPreference = 'Stop'
$latest = Join-Path $PSScriptRoot 'validate_r6c3_fix127_149.ps1'
if (-not (Test-Path -LiteralPath $latest)) {
    throw "[PACKAGE-INTEGRITY] Falta 'scripts\validation\validate_r6c3_fix127_149.ps1'. Reaplica la acumulativa R6C3 Fix149."
}
& $latest -JdkHome $JdkHome -PythonExe $PythonExe
