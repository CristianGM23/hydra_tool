[CmdletBinding()]
param(
    [string]$JdkHome = "",
    [string]$PythonExe = "python"
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Invoke-Checked { param([string]$Label,[scriptblock]$Command) Write-Host "START $Label"; & $Command; if($LASTEXITCODE -ne 0){throw "FAIL $Label exit=$LASTEXITCODE"}; Write-Host "PASS $Label" }
Push-Location $root
try {
  if($JdkHome -ne ''){$env:JAVA_HOME=$JdkHome;$env:Path=(Join-Path $JdkHome 'bin')+';'+$env:Path}
  Invoke-Checked 'r6c2-structural' { & $PythonExe 'scripts/validation/verify_r6c2_fix127_132_delivery_readiness.py' }
  Invoke-Checked 'r6c2-openapi-contract' { & $PythonExe 'scripts/api/verify_openapi.py' }
  Invoke-Checked 'r6c2-backend-docs' { & $PythonExe 'scripts/validation/verify_backend_spanish_method_docs.py' }
  Invoke-Checked 'r6c2-i18n-full' { & $PythonExe 'scripts/validation/verify_hydra_i18n_full.py' }
  Invoke-Checked 'r6c2-frontend-spanish' { & node 'scripts/validation/verify_frontend_spanish_coverage.cjs' }
  Invoke-Checked 'r6c2-css-budget' { & node 'frontend/scripts/check-css-budgets.mjs' }
  Invoke-Checked 'r6c2-backend-targeted' { & .\mvnw.cmd '-q' '-Dtest=TechnologyDeliveryReadinessServiceTests,TechnologyDeliveryReadinessControllerTests' test }
  Push-Location 'frontend'
  try {
    $env:HYDRA_TEST_POOL='vmThreads'; $env:HYDRA_TEST_RECOVERY_POOL='vmForks,threads,forks'; $env:HYDRA_TEST_MAX_WORKERS='1'; $env:HYDRA_TEST_FILE_PARALLELISM='false'
    Invoke-Checked 'r6c2-frontend-targeted' { & node 'scripts/hydra-test-runner.mjs' 'deterministic' 'src/integration-designer/technologyDeliveryReadinessNormalization.test.ts' 'src/integration-designer/IntegrationTechnologyJourney.test.tsx' 'src/integration-designer/api.test.ts' 'src/integration-designer/IntegrationDesigner.test.tsx' }
    Invoke-Checked 'r6c2-frontend-build' { & npm.cmd run build }
  } finally { Pop-Location }
  Write-Host 'PASS R6C2 FIX127-132 TECHNOLOGY DELIVERY READINESS'
} finally { Pop-Location }
