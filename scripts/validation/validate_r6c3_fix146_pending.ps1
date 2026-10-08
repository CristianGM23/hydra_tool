[CmdletBinding()]
param(
    [string]$JdkHome = ""
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Assert-RequiredPath {
    param([string]$RelativePath)
    $fullPath = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw "[PACKAGE-INTEGRITY] Falta '$RelativePath'. Aplica R6C3 Fix146 sobre Fix145 antes de reanudar la cualificacion."
    }
}

function Invoke-Checked {
    param([string]$Label,[scriptblock]$Command)
    Write-Host "START $Label"
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "FAIL $Label exit=$LASTEXITCODE" }
    Write-Host "PASS $Label"
}

Push-Location $root
try {
    if ($JdkHome -ne '') {
        $env:JAVA_HOME = $JdkHome
        $env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path
    }

    @(
        'mvnw.cmd',
        'src\test\java\com\example\editor\textual\roundtrip\TextualInstanceSemanticRoundTripTests.java'
    ) | ForEach-Object { Assert-RequiredPath $_ }

    Write-Host '========== RESUME R6C3 FIX146: SOLO BLOQUE PENDIENTE =========='
    Write-Host 'REUSED PASS: R6C2 Fix127-132, OpenAPI, i18n, frontend Spanish, CSS, frontend tests/build, R6C3 semantic 22/22, R6C3 instance contract 41/41.'
    Write-Host 'RUN ONLY: TextualInstanceSemanticRoundTripTests#failsClosedForNonPortableNativeIds'

    Invoke-Checked 'r6c3-fix146-formerly-failing-test' {
        & .\mvnw.cmd '-q' '-Dtest=TextualInstanceSemanticRoundTripTests#failsClosedForNonPortableNativeIds' test
    }

    Write-Host ''
    Write-Host 'PASS R6C3 FIX146 PENDING-ONLY QUALIFICATION'
    Write-Host 'PASS previous host-validated checkpoints reused; formerly failing native-id portability test is resolved'
} finally {
    Pop-Location
}
