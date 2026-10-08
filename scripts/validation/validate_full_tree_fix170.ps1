param(
    [switch]$StructuralOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root
$venv = Join-Path $root 'target\release\qualification-venv'
$python = Join-Path $venv 'Scripts\python.exe'
$requirements = Join-Path $root 'scripts\release\qualification-requirements.txt'

if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
    Write-Host 'HYDRA: creando entorno Python aislado para cualificacion...'
    & python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el entorno Python de cualificacion.' }
}

Write-Host 'HYDRA: comprobando/instalando dependencias Python fijadas...'
& $python -m pip install --disable-pip-version-check -r $requirements
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias Python de cualificacion.' }
& $python -c "import jsonschema, pytest; print('HYDRA PYTHON QUALIFICATION DEPENDENCIES=PASS')"
if ($LASTEXITCODE -ne 0) { throw 'Falló la comprobación de dependencias Python.' }

$arguments = @('scripts/validation/verify_final_tree.py', '--ci')
if ($StructuralOnly) { $arguments += '--structural-only' }
& $python @arguments
$code = $LASTEXITCODE
Write-Host "HYDRA FINAL TREE EXIT CODE=$code"
exit $code
