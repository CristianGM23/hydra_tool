[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$PythonExe = "python"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Assert-RequiredPath {
    param([string]$RelativePath)
    $fullPath = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw "[PACKAGE-INTEGRITY] Falta '$RelativePath'. Aplica el hotfix acumulativo R6C1 Fix126 -> R6C3 Fix150 antes de ejecutar la cualificacion."
    }
}

function Invoke-Checked {
    param(
        [string]$Label,
        [scriptblock]$Command
    )
    Write-Host "START $Label"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "FAIL $Label exit=$LASTEXITCODE"
    }
    Write-Host "PASS $Label"
}

function Invoke-QualificationPhase {
    param(
        [string]$Label,
        [scriptblock]$Command
    )
    Write-Host ""
    Write-Host "========== START $Label =========="
    try {
        & $Command
        Write-Host "========== PASS $Label =========="
    } catch {
        Write-Host "========== FAIL $Label ==========" -ForegroundColor Red
        throw "[$Label] $($_.Exception.Message)"
    }
}

function Invoke-R6C2InheritedQualification {
    # Ejecuta primero los contratos de fuente rápidos para fallar antes de cualquier
    # regeneración OpenAPI, Maven, Vitest o build cuando el árbol no cumple la política.
    Invoke-Checked 'r6c2-backend-docs' {
        & $PythonExe 'scripts/validation/verify_backend_spanish_method_docs.py'
    }
    Invoke-Checked 'r6c2-structural' {
        & $PythonExe 'scripts/validation/verify_r6c2_fix127_132_delivery_readiness.py'
    }
    Invoke-Checked 'r6c2-openapi-contract' {
        & $PythonExe 'scripts/api/verify_openapi.py'
    }
    Invoke-Checked 'r6c2-i18n-full' {
        & $PythonExe 'scripts/validation/verify_hydra_i18n_full.py'
    }
    Invoke-Checked 'r6c2-frontend-spanish' {
        & node 'scripts/validation/verify_frontend_spanish_coverage.cjs'
    }
    Invoke-Checked 'r6c2-css-budget' {
        & node 'frontend/scripts/check-css-budgets.mjs'
    }
    Invoke-Checked 'r6c2-backend-targeted' {
        & .\mvnw.cmd '-q' '-Dtest=TechnologyDeliveryReadinessServiceTests,TechnologyDeliveryReadinessControllerTests' test
    }
    Push-Location 'frontend'
    try {
        $env:HYDRA_TEST_POOL = 'vmThreads'
        $env:HYDRA_TEST_RECOVERY_POOL = 'vmForks,threads,forks'
        $env:HYDRA_TEST_MAX_WORKERS = '1'
        $env:HYDRA_TEST_FILE_PARALLELISM = 'false'
        Invoke-Checked 'r6c2-frontend-targeted' {
            & node 'scripts/hydra-test-runner.mjs' 'deterministic' 'src/integration-designer/technologyDeliveryReadinessNormalization.test.ts' 'src/integration-designer/IntegrationTechnologyJourney.test.tsx' 'src/integration-designer/api.test.ts' 'src/integration-designer/IntegrationDesigner.test.tsx'
        }
        Invoke-Checked 'r6c2-frontend-build' {
            & npm.cmd run build
        }
    } finally {
        Pop-Location
    }
}

Push-Location $root
try {
    if ($JdkHome -ne '') {
        $env:JAVA_HOME = $JdkHome
        $env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path
    }

    # Fix150: package-integrity preflight. The previous R6C3 overlay was computed
    # only against Fix132 and could therefore omit inherited R6C2 files on a host
    # that had not installed R6C2 yet. This gate is now self-contained with
    # respect to the R6C2 orchestration and fails with an actionable message if
    # any cumulative payload dependency is missing.
    @(
        'scripts\validation\verify_r6c2_fix127_132_delivery_readiness.py',
        'scripts\api\verify_openapi.py',
        'scripts\validation\verify_backend_spanish_method_docs.py',
        'scripts\validation\verify_hydra_i18n_full.py',
        'scripts\validation\verify_frontend_spanish_coverage.cjs',
        'frontend\scripts\check-css-budgets.mjs',
        'frontend\scripts\hydra-test-runner.mjs',
        'frontend\package.json',
        'mvnw.cmd',
        'scripts\validation\verify_r6c3_semantic_roundtrip.py',
        'scripts\validation\verify_r6c3_instance_roundtrip.py',
        'scripts\validation\verify_r6c3_runtime_resilience.py',
        'src\main\java\com\example\editor\textual\roundtrip\GeneratedTextualInstanceCodec.java',
        'src\main\java\com\example\editor\textual\roundtrip\TextualInstanceDocumentDependency.java',
        'src\main\java\com\example\editor\textual\roundtrip\TextualInstanceRoundTripService.java',
        'src\test\java\com\example\editor\textual\roundtrip\TextualInstanceSemanticRoundTripTests.java',
        'src\test\java\com\example\editor\textual\roundtrip\TextualInstanceAggressiveRoundTripTests.java',
        'src\test\java\com\example\editor\textual\roundtrip\TextualRuntimeResilienceTests.java',
        'docs\grammar\R6C3_INSTANCE_SEMANTIC_ROUNDTRIP.md'
    ) | ForEach-Object { Assert-RequiredPath $_ }

    Invoke-QualificationPhase 'R6C2-INHERITED-FIX127-132' {
        Invoke-R6C2InheritedQualification
    }

    Invoke-QualificationPhase 'R6C3-NEW-FIX133-150' {
        Invoke-Checked 'r6c3-semantic-contract' {
            & $PythonExe 'scripts/validation/verify_r6c3_semantic_roundtrip.py'
        }
        Invoke-Checked 'r6c3-instance-contract' {
            & $PythonExe 'scripts/validation/verify_r6c3_instance_roundtrip.py'
        }
        Invoke-Checked 'r6c3-runtime-resilience-contract' {
            & $PythonExe 'scripts/validation/verify_r6c3_runtime_resilience.py'
        }
        Invoke-Checked 'r6c3-backend-targeted' {
            & .\mvnw.cmd '-q' '-Dtest=TextualMetamodelSemanticRoundTripTests,TextualGrammarImportRegressionTests,TextualInstanceSemanticRoundTripTests,TextualInstanceAggressiveRoundTripTests,TextualRuntimeResilienceTests' test
        }
    }

    Write-Host ""
    Write-Host 'PASS R6C2 FIX127-132 + R6C3 FIX133-150 CUMULATIVE QUALIFICATION'
    Write-Host 'PASS inherited readiness baseline plus Xtext/Langium metamodel and canonical-instance semantic round-trip block'
} finally {
    Pop-Location
}
