[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$PythonExe = "python"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$previous = Join-Path $root 'scripts\validation\validate_r6c3_fix127_145.ps1'

if (-not (Test-Path -LiteralPath $previous)) {
    throw "[PACKAGE-INTEGRITY] Falta scripts\validation\validate_r6c3_fix127_145.ps1."
}

Write-Host 'FULL AUDIT MODE: este launcher repite la cualificacion acumulativa Fix145.'
Write-Host 'Para reanudar solo el test pendiente de tu ejecucion anterior utiliza validate_r6c3_fix146_pending.ps1.'
& $previous -JdkHome $JdkHome -PythonExe $PythonExe
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
