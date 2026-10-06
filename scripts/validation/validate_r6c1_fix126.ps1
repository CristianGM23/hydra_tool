[CmdletBinding()]
param(
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path

function Invoke-Checked {
    param([string]$Label, [scriptblock]$Command)
    Write-Host "START $Label"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "FAIL $Label exit=$LASTEXITCODE"
    }
    Write-Host "PASS $Label"
}

Push-Location $root
try {
    Invoke-Checked "r6c1-fix126-structural" {
        & $PythonExe "scripts/validation/verify_r6c1_fix126_typed_expectations.py"
    }

    Push-Location "frontend"
    try {
        Invoke-Checked "r6c1-fix126-frontend-build" { & npm.cmd run build }
    }
    finally { Pop-Location }

    Write-Host "PASS R6C1 FIX126 TYPED FRONTEND BUILD"
}
finally { Pop-Location }
