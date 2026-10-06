[CmdletBinding()]
param(
    [string]$JdkHome = "",
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

if ($JdkHome -ne "") {
    $java = Join-Path $JdkHome "bin\java.exe"
    if (-not (Test-Path $java)) { throw "JDK no valido: $JdkHome" }
    $env:JAVA_HOME = $JdkHome
    $env:Path = "$(Join-Path $JdkHome 'bin');$env:Path"
}

Push-Location $root
try {
    Invoke-Checked "r6c1-structural" { & $PythonExe "scripts/validation/verify_r6c1_fix118_123_technology_workflow_parity.py" }
    Invoke-Checked "backend-spanish-docs" { & $PythonExe "scripts/validation/verify_backend_spanish_method_docs.py" }
    Invoke-Checked "frontend-spanish-coverage" { & node "scripts/validation/verify_frontend_spanish_coverage.cjs" }
    Invoke-Checked "hydra-i18n-full" { & $PythonExe "scripts/validation/verify_hydra_i18n_full.py" }

    $maven = if (Test-Path ".\mvnw.cmd") { ".\mvnw.cmd" } else { "mvn.cmd" }
    Invoke-Checked "r6c1-backend-targeted" {
        & $maven "-Dtest=TechnologyWorkflowServiceTests,TechnologyWorkflowControllerTests" test
    }

    Push-Location "frontend"
    try {
        Invoke-Checked "r6c1-css-budget" { & node "scripts/check-css-budgets.mjs" }
        Invoke-Checked "r6c1-frontend-targeted-core" {
            & npm.cmd exec -- vitest run `
                src/integration-designer/technologyWorkflowNormalization.test.ts `
                src/integration-designer/integrationTechnologyRecommendations.test.ts `
                src/integration-designer/IntegrationTechnologyJourney.test.tsx `
                src/integration-designer/integrationTechnologyNormalization.test.ts `
                src/integration-designer/api.test.ts
        }

        # IntegrationDesigner es la superficie TSX mas pesada del lote. En Windows
        # delegamos la ejecucion focalizada en el runner central de HYDRA, que solo
        # cambia de pool cuando Vitest demuestra un fallo de infraestructura del worker.
        # Fix125 comienza por vmThreads para no insistir primero en forks/threads, los
        # dos pools que ya mostraron timeouts de bootstrap en esta maquina.
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
            Invoke-Checked "r6c1-frontend-integration-designer-recoverable" {
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

        Invoke-Checked "r6c1-frontend-build" { & npm.cmd run build }
    }
    finally { Pop-Location }

    Write-Host "PASS R6C1 FIX118-123 TECHNOLOGY WORKFLOW PARITY"
}
finally { Pop-Location }
