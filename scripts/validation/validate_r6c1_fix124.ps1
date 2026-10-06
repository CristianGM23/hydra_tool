[CmdletBinding()]
param(
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

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
    Invoke-Checked "r6c1-fix124-structural" {
        & $PythonExe "scripts/validation/verify_r6c1_fix124_vitest_worker_recovery.py"
    }

    Push-Location "frontend"
    try {
        $previousPool = $env:HYDRA_TEST_POOL
        $previousRecoveryPool = $env:HYDRA_TEST_RECOVERY_POOL
        $previousParallelism = $env:HYDRA_TEST_FILE_PARALLELISM
        $previousWorkers = $env:HYDRA_TEST_MAX_WORKERS
        $previousTimeout = $env:HYDRA_TEST_TIMEOUT_MS
        try {
            $env:HYDRA_TEST_POOL = "vmThreads"
            $env:HYDRA_TEST_RECOVERY_POOL = "vmForks"
            $env:HYDRA_TEST_FILE_PARALLELISM = "false"
            $env:HYDRA_TEST_MAX_WORKERS = "1"
            $env:HYDRA_TEST_TIMEOUT_MS = "90000"
            Invoke-Checked "r6c1-fix125-integration-designer-recoverable" {
                & node "scripts/hydra-test-runner.mjs" full "src/integration-designer/IntegrationDesigner.test.tsx"
            }
        }
        finally {
            if ($null -eq $previousPool) { Remove-Item Env:HYDRA_TEST_POOL -ErrorAction SilentlyContinue } else { $env:HYDRA_TEST_POOL = $previousPool }
            if ($null -eq $previousRecoveryPool) { Remove-Item Env:HYDRA_TEST_RECOVERY_POOL -ErrorAction SilentlyContinue } else { $env:HYDRA_TEST_RECOVERY_POOL = $previousRecoveryPool }
            if ($null -eq $previousParallelism) { Remove-Item Env:HYDRA_TEST_FILE_PARALLELISM -ErrorAction SilentlyContinue } else { $env:HYDRA_TEST_FILE_PARALLELISM = $previousParallelism }
            if ($null -eq $previousWorkers) { Remove-Item Env:HYDRA_TEST_MAX_WORKERS -ErrorAction SilentlyContinue } else { $env:HYDRA_TEST_MAX_WORKERS = $previousWorkers }
            if ($null -eq $previousTimeout) { Remove-Item Env:HYDRA_TEST_TIMEOUT_MS -ErrorAction SilentlyContinue } else { $env:HYDRA_TEST_TIMEOUT_MS = $previousTimeout }
        }

        Invoke-Checked "r6c1-fix124-frontend-build" { & npm.cmd run build }
    }
    finally { Pop-Location }

    Write-Host "PASS R6C1 FIX124 VITEST WORKER RECOVERY"
}
finally { Pop-Location }
