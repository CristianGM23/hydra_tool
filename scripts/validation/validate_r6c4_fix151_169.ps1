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
        throw "[PACKAGE-INTEGRITY] Falta '$RelativePath'. Reaplica el overlay completo R6C4 Fix167."
    }
}
function Invoke-Checked {
    param([string]$Label,[scriptblock]$Command)
    Write-Host "START $Label"
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "FAIL $Label exit=$LASTEXITCODE" }
    Write-Host "PASS $Label"
}
function Invoke-Phase {
    param([string]$Label,[scriptblock]$Command)
    Write-Host ""
    Write-Host "========== START $Label =========="
    try { & $Command; Write-Host "========== PASS $Label ==========" }
    catch { Write-Host "========== FAIL $Label ==========" -ForegroundColor Red; throw "[$Label] $($_.Exception.Message)" }
}

Push-Location $root
try {
    if ($JdkHome -ne '') { $env:JAVA_HOME=$JdkHome; $env:Path=(Join-Path $JdkHome 'bin')+';'+$env:Path }

    @(
        'MANIFEST_SHA256.txt',
        'scripts\validation\verify_manifest.py',
        'scripts\validation\validate_r6c4_fix151_169.ps1',
        'scripts\validation\verify_backend_spanish_method_docs.py',
        'scripts\validation\verify_r6c4_textual_interactions.py',
        'scripts\api\verify_openapi.py',
        'src\main\java\com\example\editor\textual\interaction\TextualWorkspaceIndex.java',
        'src\main\java\com\example\editor\textual\interaction\TextualLanguageFeatureService.java',
        'src\main\java\com\example\editor\textual\interaction\TextualInteractionFacade.java',
        'src\test\java\com\example\editor\metamodel\roundtrip\TextualMetamodelSemanticRoundTripTests.java',
        'src\test\java\com\example\editor\metamodel\importing\TextualGrammarImportRegressionTests.java',
        'src\test\java\com\example\editor\textual\roundtrip\TextualInstanceSemanticRoundTripTests.java',
        'src\test\java\com\example\editor\textual\roundtrip\TextualInstanceAggressiveRoundTripTests.java',
        'src\test\java\com\example\editor\textual\roundtrip\TextualRuntimeResilienceTests.java',
        'src\test\java\com\example\editor\textual\interaction\TextualLanguageFeatureServiceTests.java',
        'docs\grammar\R6C4_TEXTUAL_INTERACTION_PARITY.md',
        'frontend\src\api\generated\hydraApiClient.ts',
        'frontend\package.json',
        'mvnw.cmd'
    ) | ForEach-Object { Assert-RequiredPath $_ }

    Write-Host 'Fix169 valida hashes de todo el source canonico y conserva archivos adicionales del workspace.'
    Write-Host 'No requiere launchers ni verificadores R6C3 historicos por nombre.'
    Write-Host 'Los tests Maven focales vuelven a ejecutar comportamiento R6C3 y R6C4 sobre este mismo arbol.'

    Invoke-Phase 'SOURCE-INTEGRITY' {
        Invoke-Checked 'manifest-workspace' { & $PythonExe 'scripts/validation/verify_manifest.py' '--allow-extra' }
    }

    Invoke-Phase 'R6C4-NEW-FIX151-169' {
        Invoke-Checked 'backend-docs' { & $PythonExe 'scripts/validation/verify_backend_spanish_method_docs.py' }
        Invoke-Checked 'r6c4-structural-contract' { & $PythonExe 'scripts/validation/verify_r6c4_textual_interactions.py' }
        Invoke-Checked 'openapi-contract' { & $PythonExe 'scripts/api/verify_openapi.py' }
        Invoke-Checked 'backend-targeted-r6c3-r6c4' {
            & .\mvnw.cmd '-q' '-Dtest=TextualMetamodelSemanticRoundTripTests,TextualGrammarImportRegressionTests,TextualInstanceSemanticRoundTripTests,TextualInstanceAggressiveRoundTripTests,TextualRuntimeResilienceTests,TextualLanguageFeatureServiceTests' test
        }
        Push-Location 'frontend'
        try {
            Invoke-Checked 'frontend-build' { & npm.cmd run build }
        } finally { Pop-Location }
    }

    Write-Host ''
    Write-Host 'PASS R6C4 FIX151-169 TEXTUAL INTERACTION PARITY QUALIFICATION'
    Write-Host 'PASS canonical source hashes plus preserved workspace extras, focused R6C3/R6C4 native behavior and frontend build'
} finally { Pop-Location }
