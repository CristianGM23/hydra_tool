$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'validate_r6c4_fix151_169.ps1') @args
exit $LASTEXITCODE
