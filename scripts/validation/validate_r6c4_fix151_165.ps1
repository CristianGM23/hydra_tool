[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$PythonExe = "python"
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'validate_r6c4_fix151_167.ps1') -JdkHome $JdkHome -PythonExe $PythonExe
exit $LASTEXITCODE
