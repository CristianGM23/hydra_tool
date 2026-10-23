<#
.SYNOPSIS
  Lanzador unico, reanudable y NO autoexcluyente de la certificacion consolidada de Archify V3 (Windows PowerShell 5.1).

.DESCRIPTION
  Pasos, cada uno con su registro y su punto de control:
    preflight -> selftest -> isolate -> run-core (Maven/Vitest/node/python) -> run-e2e (Playwright) -> sync -> verify -> package -> report

  AISLAMIENTO PREVENTIVO. Ninguna prueba se ejecuta contra el espacio de trabajo real. El paso isolate crea (o refresca) un sandbox fisico: una copia
  verificada byte a byte del espacio de trabajo bajo %TEMP% (sin .git, target, build, data ni node_modules; el unico enlace admitido es el de solo
  lectura frontend\node_modules) con su propio data\ inicialmente vacio. Maven, Vitest, Playwright, los servidores y las JVM hijas arrancan con ese
  directorio como directorio de trabajo, de modo que ./data resuelve DENTRO del sandbox. Antes de cada paso de ejecucion se vuelve a comprobar que el
  sandbox no es el espacio real, no esta anidado, no contiene enlaces peligrosos, que ninguna variable de entorno redirige a data\ real y que su
  contenido coincide con el del espacio de trabajo; si algo falla, el paso queda BLOCKED_ISOLATION y NO ejecuta nada. Como segunda barrera, la huella de
  data\ real (ruta, tipo, tamano, fechas, atributos y SHA-256) se compara despues de cada paso de ejecucion y siempre al final, incluso si hay fallos.

  HUELLAS. Cada paso guarda en su punto de control los componentes reales de sus entradas (SHA-256 del lanzador, del modulo de apoyo, del ejecutor, del
  manifiesto, de los parametros que cambian su comportamiento y, segun el paso, de la huella de contenido de cada tarea, del arbol espejado, del estado,
  de las fichas o de las matrices). -Resume solo salta un paso que termino OK en esta misma campana (mismo HEAD) con TODOS los componentes identicos; si
  alguno cambia, el paso se repite y se indica cual. El ejecutor V3 decide por tarea (huella de contenido y cierre de dependencias) que se reutiliza.
  El punto de control se escribe de forma atomica con suma de verificacion; uno ilegible, alterado o incompleto se aparta (auditoria) y nunca cuenta como
  PASS; un paso que quedo RUNNING se considera INTERRUPTED. Un HEAD distinto abre una campana nueva y archiva la anterior.

  Una prueba omitida o no ejecutada NUNCA se convierte en PASS. Este script no ejecuta git add, commit, push, reset, clean ni stash.

.PARAMETER Steps              Pasos separados por comas (por defecto 'all').
.PARAMETER Resume             Salta los pasos OK de esta campana con componentes identicos.
.PARAMETER ForceAll           Revalida todo (run --force "*").
.PARAMETER StrictFingerprint  Desactiva el cierre de dependencias: solo reutiliza con todas las entradas declaradas identicas.
.PARAMETER Execute            OBLIGATORIO para ejecutar de verdad (crear el sandbox, ejecutar pruebas, sync, empaquetar). Sin -Execute, -DryRun ni -Status el lanzador no hace nada.
.PARAMETER DryRun             Solo lectura: muestra pasos, parametros, componentes y decisiones; no crea sandbox, no ejecuta pruebas, no escribe puntos de control.
.PARAMETER SkipE2E            Omite run-e2e por decision explicita (SKIPPED_BY_DECISION, nunca PASS).
.PARAMETER Status             Solo lectura: resumen del punto de control y por que se repetiria cada paso (no ejecuta ningun paso).
.PARAMETER ChunkSize          Tareas Maven por invocacion (por defecto 8): el progreso se guarda por bloque, una interrupcion pierde como maximo un bloque.
.PARAMETER SandboxRoot        Directorio del sandbox (por defecto %TEMP%\hydra-certification-sandbox). Debe estar bajo %TEMP%.
.PARAMETER Checkpoint         Fichero de puntos de control (por defecto target/archify-v3/certification-checkpoint.json).
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts\archify\v3\Invoke-HydraConsolidatedCertification.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [string]$Steps = 'all',
    [switch]$Resume,
    [switch]$ForceAll,
    [switch]$StrictFingerprint,
    [switch]$DryRun,
    [switch]$Execute,
    [switch]$SkipE2E,
    [switch]$Status,
    [int]$ChunkSize = 8,
    [string]$SandboxRoot = '',
    [string]$Checkpoint = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Set-Location $root
. (Join-Path $PSScriptRoot 'HydraCertificationSupport.ps1')

$out = Join-Path $root 'target/archify-v3'
New-Item -ItemType Directory -Force -Path $out, (Join-Path $out 'launcher-logs') | Out-Null
if ($Checkpoint -eq '') { $Checkpoint = Join-Path $out 'certification-checkpoint.json' }
if ($SandboxRoot -eq '') { $SandboxRoot = Join-Path ([IO.Path]::GetTempPath()) 'hydra-certification-sandbox' }
$allSteps = @('preflight', 'selftest', 'isolate', 'run-core', 'run-e2e', 'sync', 'verify', 'package', 'report')
$stepList = if ($Steps -eq 'all') { $allSteps } else { @($Steps -split '[,;\s]+' | Where-Object { $_ }) }
$unknown = @($stepList | Where-Object { $allSteps -notcontains $_ })
if ($unknown.Count) { throw "Pasos desconocidos: $($unknown -join ', '). Validos: $($allSteps -join ', ')" }
if ($ChunkSize -lt 1) { throw 'ChunkSize debe ser >= 1' }
if ($Status) { $stepList = @() }   # -Status solo informa
if (-not ($Execute -or $DryRun -or $Status)) {
    Write-Host 'Este lanzador no ejecuta nada por accidente. Use -DryRun (simulacion de solo lectura), -Status (estado) o -Execute (ejecucion real).' -ForegroundColor Yellow
    exit 2
}
if ($Execute -and ($DryRun -or $Status)) { throw '-Execute no se combina con -DryRun ni -Status' }
$runner = 'scripts/archify/v3/hydra_archify_v3.py'
$manifestPath = 'docs/architecture/archify/knowledge/tests/v3/MANIFEST_CERTIFICACION_V3.json'
$syncOutputs = @('docs/architecture/archify/hydra-tests-data.js', 'docs/architecture/archify/knowledge/baseline.json', 'docs/architecture/archify/knowledge/capabilities-cache.json',
    'docs/architecture/archify/knowledge/gaps.json', 'docs/architecture/archify/knowledge/tests/discovery-current.json', 'docs/architecture/archify/knowledge/tests/test-catalog.json',
    'docs/architecture/archify/knowledge/tests/test-evidence-matrix.json', 'docs/architecture/archify/knowledge/tests/test-execution-state.json', 'docs/architecture/archify/knowledge/tests/test-gaps.json')

function Get-Git([string[]]$arguments) { $r = & git @arguments 2>$null; if ($LASTEXITCODE -ne 0) { return '' }; return (($r | Out-String).Trim()) }
$head = Get-Git @('rev-parse', 'HEAD')
$headShort = if ($head.Length -ge 8) { $head.Substring(0, 8) } else { 'nohead' }

# --- punto de control (lectura estricta) ------------------------------------------------------------------------------------------------
$read = Read-CheckpointFile $Checkpoint -NoQuarantine:($DryRun -or $Status)
$state = $read.state
if ($read.quarantinedAs) { Write-Host "AVISO: el punto de control era invalido ($($read.reason)); apartado en $($read.quarantinedAs). Se empieza sin resultados previos: nada se da por completado." -ForegroundColor Yellow }
if ($read.interrupted.Count) { Write-Host "AVISO: pasos interrumpidos en la ejecucion anterior (se repetiran): $($read.interrupted -join ', ')" -ForegroundColor Yellow }
function Save-State { if (-not $DryRun -and -not $Status) { Save-CheckpointFile $Checkpoint $state } }
if (-not $DryRun -and -not $Status) {
    if (Set-CheckpointCampaign $state $head $Checkpoint) { Write-Host "Campana nueva para HEAD $headShort (la anterior queda archivada junto al punto de control)." -ForegroundColor Cyan }
    Save-State
}

# --- contexto de huellas (solo lectura; cada campo se calcula SOLO si el paso lo necesita y una sola vez) ---------------------------------
$script:ContextFields = @{}
$script:StepNeeds = @{
    'preflight' = @(); 'selftest' = @(); 'isolate' = @('mirrorTreeHash'); 'run-core' = @('taskFingerprints'); 'run-e2e' = @('taskFingerprints')
    'sync' = @('stateHash', 'fragmentsHash', 'toolsHash'); 'verify' = @('stateHash', 'syncOutputsHash'); 'package' = @('stateHash', 'syncOutputsHash'); 'report' = @('stateHash', 'syncOutputsHash')
}
function Get-ContextField([string]$field) {
    if ($script:ContextFields.ContainsKey($field)) { return $script:ContextFields[$field] }
    switch ($field) {
        'taskFingerprints' {
            $tasks = @{}
            $json = (& python $runner fingerprints 2>$null) -join ''
            if ($json) { $parsed = $json | ConvertFrom-Json; foreach ($p in $parsed.PSObject.Properties) { $tasks[$p.Name] = @{ kind = $p.Value.kind; v1 = $p.Value.v1 } } }
            $value = $tasks
        }
        'mirrorTreeHash' { $value = Get-FilesContentHash $root (Get-MirrorFileList $root) }
        'stateHash' { $stateFile = Join-Path $out 'state.json'; $value = $(if (Test-Path $stateFile) { Get-FileSha256 $stateFile } else { 'NO_STATE' }) }
        'syncOutputsHash' { $value = Get-FilesContentHash $root $syncOutputs }
        'fragmentsHash' { $value = Get-FilesContentHash $root @(Get-ChildItem (Join-Path $root 'docs/architecture/archify/knowledge/tests/v3/fragments') -Filter *.json -File | ForEach-Object { "docs/architecture/archify/knowledge/tests/v3/fragments/$($_.Name)" }) }
        'toolsHash' { $value = Get-FilesContentHash $root @('docs/architecture/archify/knowledge/tests/discover-tests.js', 'docs/architecture/archify/knowledge/tests/build-test-evidence.js', $runner) }
        default { throw "campo de contexto desconocido: $field" }
    }
    $script:ContextFields[$field] = $value
    return $value
}
function Get-Context([string]$step = '') {
    $names = if ($step) { $script:StepNeeds[$step] } else { @($script:StepNeeds.Values | ForEach-Object { $_ } | Sort-Object -Unique) }
    $context = @{ root = $root; head = $head; params = @{ ForceAll = [bool]$ForceAll; StrictFingerprint = [bool]$StrictFingerprint; SkipE2E = [bool]$SkipE2E } }
    foreach ($n in $names) { $context[$n] = Get-ContextField $n }
    return $context
}
function Reset-Context { $script:ContextFields = @{} }
# --- segunda barrera: data\ real -------------------------------------------------------------------------------------------------------
$dataBaselineFile = Join-Path $out "data-snapshot-campaign-$headShort.json"
$script:DataBaseline = $null
function Initialize-DataBaseline {
    if ($script:DataBaseline) { return }
    if (Test-Path -LiteralPath $dataBaselineFile) { $script:DataBaseline = ConvertTo-PlainHashtable (Get-Content -LiteralPath $dataBaselineFile -Raw | ConvertFrom-Json); return }
    $script:DataBaseline = Get-DataSnapshot $root
    if (-not $DryRun) { ($script:DataBaseline | ConvertTo-Json -Depth 3) | Set-Content -LiteralPath $dataBaselineFile -Encoding UTF8 }
}
function Test-DataIntegrity([string]$label) {
    Initialize-DataBaseline
    $changes = @(Compare-DataSnapshot $script:DataBaseline (Get-DataSnapshot $root))
    if ($changes.Count -eq 0) { Write-Host "  [data] $label : data\ real sin cambios (ruta, tipo, tamano, fechas, atributos y contenido)" -ForegroundColor DarkGreen }
    else { Write-Host "  [data] $label : $($changes.Count) CAMBIOS en el data\ real" -ForegroundColor Red; $changes | Select-Object -First 15 | ForEach-Object { Write-Host "         $_" -ForegroundColor Red } }
    if (-not $state.ContainsKey('dataChecks')) { $state['dataChecks'] = @() }
    $state['dataChecks'] = @($state['dataChecks']) + @(@{ at = (Get-Date).ToUniversalTime().ToString('o'); label = $label; changes = @($changes | Select-Object -First 50); changeCount = $changes.Count })
    return $changes
}

# --- ejecucion de pasos ---------------------------------------------------------------------------------------------------------------
$script:DataChanged = $false
function Invoke-Step([string]$name, [scriptblock]$body) {
    if ($stepList -notcontains $name) { return }
    $components = Get-StepComponents $name (Get-Context $name)
    $prev = if ($state['steps'].ContainsKey($name)) { $state['steps'][$name] } else { $null }
    if ($DryRun) {
        $decision = if ($Resume -and (Test-StepReusable $prev $components $head)) { 'se SALTARIA (resume: OK con componentes identicos)' }
            elseif ($Resume -and $prev -and $prev['components'] -is [hashtable]) { "se REPETIRIA (cambian: $((Compare-Components $prev['components'] $components) -join ', '); estado previo: $($prev['status']))" }
            elseif ($prev) { "se EJECUTARIA (sin -Resume; estado previo: $($prev['status']))" } else { 'se EJECUTARIA (sin resultado previo)' }
        Write-Host "[$name] DRY-RUN: $decision"
        Write-Host "         componentes: $((($components.Keys | Sort-Object) | ForEach-Object { "$_=$($components[$_].Substring(0, [Math]::Min(12, $components[$_].Length)))" }) -join ' ')"
        $scratch = [IO.Path]::GetTempFileName()
        try { $null = & $body $scratch } catch { Write-Host "         DRY-RUN: el paso informaria de un error: $($_.Exception.Message) [linea $($_.InvocationInfo.ScriptLineNumber): $($_.InvocationInfo.Line.Trim())]" -ForegroundColor Yellow } finally { Remove-Item -LiteralPath $scratch -Force -ErrorAction SilentlyContinue }
        return
    }
    if ($Resume -and (Test-StepReusable $prev $components $head)) { Write-Host "[$name] ya completado en esta campana con los mismos componentes (resume)"; return }
    if ($Resume -and $prev -and $prev['status'] -eq 'OK' -and $prev['components'] -is [hashtable]) {
        Write-Host "[$name] se repite: cambian $((Compare-Components $prev['components'] $components) -join ', ')" -ForegroundColor Yellow
    }
    $log = Join-Path $out "launcher-logs/$name-$((Get-Date).ToString('yyyyMMddTHHmmss')).log"
    $record = @{ status = 'RUNNING'; startedAt = (Get-Date).ToUniversalTime().ToString('o'); head = $head; log = $log; components = @{} }
    foreach ($k in $components.Keys) { $record['components'][$k] = $components[$k] }
    $record['fingerprint'] = Get-ComponentFingerprint $components
    $state['steps'][$name] = $record; Save-State
    Add-CheckpointHistory $Checkpoint $name 'RUNNING' $record['components'] $head
    Write-Host "[$name] inicio -> $log"
    try {
        $result = & $body $log
        $record['status'] = if ($null -eq $result) { 'OK' } else { [string]$result }
    } catch {
        $record['status'] = 'FAILED'; $record['error'] = $_.Exception.Message
        Add-Content -Path $log -Value "ERROR: $($_.Exception.Message)"
    }
    if ($name -in 'isolate', 'run-core', 'run-e2e') {   # segunda barrera tras cada paso que ejecuta cosas, aunque haya fallado
        $changes = @(Test-DataIntegrity $name)
        if ($changes.Count) { $record['status'] = 'DATA_CHANGED'; $record['dataChanges'] = @($changes | Select-Object -First 50); $script:DataChanged = $true }
    }
    $record['finishedAt'] = (Get-Date).ToUniversalTime().ToString('o')
    $state['steps'][$name] = $record; Save-State
    Add-CheckpointHistory $Checkpoint $name $record['status'] $record['components'] $head
    Reset-Context
    Write-Host "[$name] $($record['status'])"
}

function Invoke-Logged([string]$log, [string]$exe, [string[]]$arguments) {
    Add-Content -Path $log -Value ("> $exe " + ($arguments -join ' '))
    if ($DryRun) { Add-Content -Path $log -Value '(dry run: no ejecutado)'; Write-Host "  (dry run) $exe $($arguments -join ' ')"; return 0 }
    & $exe @arguments *>> $log
    return $LASTEXITCODE
}

# Ids de tareas a ejecutar ahora (accion RUN del plan) filtrados por tipo.
function Get-RunIds([string]$kindFilter) {
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    $kinds = @{}; foreach ($t in $manifest.tasks) { $kinds[$t.id] = $t.kind }
    $planArgs = @($runner, 'plan'); if ($StrictFingerprint) { $planArgs += '--strict-fingerprint' }
    if ($ForceAll) { $planArgs += @('--force', '*') }
    $lines = & python @planArgs 2>$null
    $ids = foreach ($line in $lines) { if ($line -match '^\s+RUN\s+(\S+)') { $Matches[1] } }
    return @($ids | Where-Object { $kinds.ContainsKey($_) -and (($kindFilter -eq 'playwright') -eq ($kinds[$_] -eq 'playwright')) })
}

# --- aislamiento: comprobaciones previas a cualquier paso que ejecute pruebas -----------------------------------------------------------
$script:SandboxVerified = $false
function Assert-Isolation([string]$log) {
    $problems = @(Get-IsolationProblems $root $SandboxRoot) + @(Get-UnsafeEnvironmentProblems $root)
    if (Test-Path -LiteralPath $SandboxRoot) { $diff = @(Compare-SandboxWithWorkspace $root $SandboxRoot); if ($diff.Count) { $problems += "el sandbox difiere del espacio de trabajo en $($diff.Count) ficheros (p. ej. $($diff[0]))" } }
    if ($problems.Count) { Add-Content $log ($problems -join "`n"); return $problems }
    $env:HYDRA_V3_EXEC_ROOT = $SandboxRoot; $env:HYDRA_V3_REQUIRE_ISOLATION = '1'
    $json = (& python $runner isolation-check 2>$null) -join ''
    $code = $LASTEXITCODE
    Add-Content $log "isolation-check (ejecutor): $json"
    if ($code -ne 0) { Remove-Item Env:HYDRA_V3_EXEC_ROOT, Env:HYDRA_V3_REQUIRE_ISOLATION -ErrorAction SilentlyContinue; return @("el ejecutor rechaza el aislamiento: $json") }
    return @()
}

# --- pasos ---------------------------------------------------------------------------------------------------------------------------
Invoke-Step 'preflight' {
    param($log)
    $problems = @()
    $branch = Get-Git @('branch', '--show-current'); $staged = Get-Git @('diff', '--cached', '--name-only')
    Add-Content $log "HEAD=$head`nbranch=$branch`nstaged=[$staged]`nsandbox=$SandboxRoot"
    foreach ($tool in 'python', 'node', 'git', 'robocopy') { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { $problems += "falta la herramienta $tool" } }
    if (-not (Test-Path (Join-Path $root 'mvnw.cmd'))) { $problems += 'falta mvnw.cmd' }
    if (-not (Test-Path (Join-Path $root 'frontend/node_modules'))) { $problems += 'falta frontend/node_modules (npm ci)' }
    $free = (Get-PSDrive -Name ((Get-Item $root).PSDrive.Name)).Free
    if ($free -lt 2GB) { $problems += "poco espacio libre ($([math]::Round($free/1GB,1)) GB)" }
    $envProblems = @(Get-UnsafeEnvironmentProblems $root)
    foreach ($p in $envProblems) { $problems += "entorno inseguro: $p" }
    Initialize-DataBaseline
    Add-Content $log "huella previa de data\ real: $($script:DataBaseline.Count) entradas"
    if ($problems.Count) { Add-Content $log ($problems -join "`n"); throw ($problems -join '; ') }
}

Invoke-Step 'selftest' {
    param($log)
    $code = Invoke-Logged $log 'python' @($runner, 'selftest')
    if ($code -ne 0) { throw "selftest del ejecutor fallo (codigo $code)" }
}

Invoke-Step 'isolate' {
    param($log)
    if ($DryRun) {
        Add-Content $log "(dry run) se crearia/refrescaria $SandboxRoot con robocopy /MIR desde $root (excluyendo .git, target, build, data, frontend\node_modules, frontend\dist)"
        $envProblems = @(Get-UnsafeEnvironmentProblems $root)
        Write-Host "  (dry run) sandbox: $SandboxRoot; entorno: $(if ($envProblems.Count) { 'INSEGURO -> ' + ($envProblems -join '; ') } else { 'sin variables que redirijan a data\' })"
        return
    }
    $envProblems = @(Get-UnsafeEnvironmentProblems $root)
    if ($envProblems.Count) { Add-Content $log ($envProblems -join "`n"); throw "entorno inseguro: $($envProblems -join '; ')" }
    $null = New-CertificationSandbox $root $SandboxRoot
    $problems = @(Assert-Isolation $log)
    if ($problems.Count) { throw "aislamiento rechazado: $($problems -join '; ')" }
    $script:SandboxVerified = $true
    Add-Content $log "sandbox verificado: $SandboxRoot; data efectivo: $(Join-Path $SandboxRoot 'data'); data real: $(Join-Path $root 'data')"
}

# Ejecuta un tipo de tarea dentro del sandbox, por bloques; nunca arranca nada si el aislamiento no esta verificado.
function Invoke-IsolatedRun([string]$log, [string]$kind) {
    $ids = @(Get-RunIds $kind)
    Add-Content $log "tareas a ejecutar ($kind): $($ids.Count)"
    if ($ids.Count -eq 0) { return 'OK' }
    if ($DryRun) {
        $dryChunks = @(Split-IntoChunks $ids $(if ($kind -eq 'playwright') { $ids.Count } else { $ChunkSize }))
        Write-Host "  (dry run) $($ids.Count) tareas en $($dryChunks.Count) invocacion(es) del ejecutor, con HYDRA_V3_EXEC_ROOT=$SandboxRoot"
        $dryProblems = @(Assert-Isolation $log)   # mismo veredicto que tendria la ejecucion real (no ejecuta nada)
        if ($dryProblems.Count) { Write-Host "  (dry run) AISLAMIENTO RECHAZADO: la ejecucion real quedaria BLOCKED_ISOLATION. $($dryProblems | Select-Object -First 3 | ForEach-Object { "- $_" })" -ForegroundColor Yellow }
        else { Write-Host "  (dry run) aislamiento verificado: data efectivo $(Join-Path $SandboxRoot 'data'); data real $(Join-Path $root 'data') intacto" -ForegroundColor DarkGreen }
        return 'OK'
    }
    $problems = @(Assert-Isolation $log)
    if ($problems.Count) {
        Add-Content $log ("BLOCKED_ISOLATION: " + ($problems -join '; '))
        Write-Host "  AISLAMIENTO RECHAZADO: no se ejecuta ninguna prueba" -ForegroundColor Red
        $problems | Select-Object -First 5 | ForEach-Object { Write-Host "    - $_" -ForegroundColor Red }
        return 'BLOCKED_ISOLATION'
    }
    $chunks = @(Split-IntoChunks $ids $(if ($kind -eq 'playwright') { $ids.Count } else { $ChunkSize }))
    $failures = 0; $n = 0
    foreach ($chunk in $chunks) {
        $n++
        $arguments = @($runner, 'run', '--only', [string]$chunk, '--no-sync', '--no-quarantine', '--retry-failed')
        if ($StrictFingerprint) { $arguments += '--strict-fingerprint' }
        if ($ForceAll) { $arguments += @('--force', '*') }
        Add-Content $log "bloque $n/$($chunks.Count): $(@($chunk -split ',').Count) tareas"
        $code = Invoke-Logged $log 'python' $arguments
        if ($code -ne 0) { $failures++ }
        $mid = @(Compare-DataSnapshot $script:DataBaseline (Get-DataSnapshot $root))
        if ($mid.Count) { Add-Content $log "DATA_CHANGED tras el bloque $n"; return 'DATA_CHANGED' }   # se detiene: no se siguen ejecutando bloques
        Save-State
    }
    if ($failures) { return 'COMPLETED_WITH_FAILURES' }
}

Invoke-Step 'run-core' {
    param($log)
    return (Invoke-IsolatedRun $log 'core')
}

Invoke-Step 'run-e2e' {
    param($log)
    if ($SkipE2E) { Add-Content $log 'omitido por decision explicita (-SkipE2E)'; return 'SKIPPED_BY_DECISION' }
    return (Invoke-IsolatedRun $log 'playwright')
}

Invoke-Step 'sync' {
    param($log)
    $code = Invoke-Logged $log 'python' @($runner, 'sync', '--restage')
    if ($code -ne 0) { throw "sync fallo (codigo $code)" }
}

Invoke-Step 'verify' {
    param($log)
    $failed = @()
    foreach ($check in @(
        @('node', @('docs/architecture/archify/knowledge/tests/v3/category-matrix.js')),
        @('node', @('docs/architecture/archify/knowledge/tests/v3/verify-universe.js')),
        @('node', @('docs/architecture/archify/knowledge/portal-check.js')),
        @('python', @($runner, 'audit-strong')),
        @('python', @($runner, 'audit-evidence')))) {
        $code = Invoke-Logged $log $check[0] $check[1]
        if ($code -ne 0) { $failed += "$($check[0]) $($check[1] -join ' ') -> $code" }
    }
    if ($failed.Count) { Add-Content $log ($failed -join "`n"); throw ('comprobaciones fallidas: ' + ($failed -join '; ')) }
}

Invoke-Step 'package' {
    param($log)
    if ($DryRun) { Add-Content $log '(dry run)'; return }
    & (Join-Path $PSScriptRoot 'New-HydraEvidencePackage.ps1') *>> $log
    if ($LASTEXITCODE -ne 0) { throw 'el empaquetado de evidencia fallo' }
}

Invoke-Step 'report' {
    param($log)
    $stateFile = Join-Path $out 'state.json'
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    $records = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw | ConvertFrom-Json).tasks } else { $null }
    $campaignStart = if ($state['campaign']) { [datetime]::Parse($state['campaign']['startedAt']).ToUniversalTime() } else { [datetime]::UtcNow }
    $groups = [ordered]@{ 'Ejecutadas y satisfactorias en esta campana' = @(); 'Evidencia historica que sigue vigente (no reejecutada)' = @(); 'Pendientes por infraestructura o herramientas externas' = @();
        'Fallidas' = @(); 'Inestables (reintentos o resultados distintos)' = @(); 'No ejecutadas por decision explicita' = @(); 'Sin evidencia vigente (pendientes de ejecutar)' = @() }
    $plan = & python $runner plan 2>$null
    $planAction = @{}; foreach ($line in $plan) { if ($line -match '^\s+(RUN|REUSE|BLOCKED|PLANNED|SKIP)\s+(\S+)') { $planAction[$Matches[2]] = $Matches[1] } }
    foreach ($t in $manifest.tasks) {
        $rec = if ($records) { $records.PSObject.Properties[$t.id] } else { $null }; $rec = if ($rec) { $rec.Value } else { $null }
        $action = if ($planAction.ContainsKey($t.id)) { $planAction[$t.id] } else { 'N/D' }
        $verdict = if ($rec) { $rec.verdict } else { 'NONE' }
        $finished = if ($rec -and $rec.PSObject.Properties['finishedAt'] -and $rec.finishedAt) { [datetime]::Parse($rec.finishedAt).ToUniversalTime() } else { [datetime]::MinValue }
        $attempts = if ($rec -and $rec.PSObject.Properties['attempts']) { [int]$rec.attempts } else { 0 }
        $reason = if ($rec -and $rec.PSObject.Properties['reason']) { [string]$rec.reason } else { '' }
        $line = "$($t.id) [$($t.kind)] veredicto=$verdict plan=$action"
        if ($t.kind -eq 'playwright' -and $SkipE2E -and $action -eq 'RUN') { $groups['No ejecutadas por decision explicita'] += $line }
        elseif ($verdict -in 'BLOCKED', 'PLANNED' -or $reason -match 'missing prerequisite') { $groups['Pendientes por infraestructura o herramientas externas'] += $line }
        elseif ($verdict -in 'FAIL', 'COMPILE_ERROR') { $groups['Fallidas'] += $line }
        elseif ($verdict -eq 'NOT_EXECUTED') { $groups['Sin evidencia vigente (pendientes de ejecutar)'] += $line }
        elseif ($verdict -eq 'PASS' -and $action -eq 'REUSE' -and $finished -ge $campaignStart) { $groups['Ejecutadas y satisfactorias en esta campana'] += $line; if ($attempts -gt 1) { $groups['Inestables (reintentos o resultados distintos)'] += "$line (intentos=$attempts)" } }
        elseif ($verdict -eq 'PASS' -and $action -eq 'REUSE') { $groups['Evidencia historica que sigue vigente (no reejecutada)'] += $line }
        else { $groups['Sin evidencia vigente (pendientes de ejecutar)'] += $line }
    }
    $md = "# Certificacion consolidada (informe del lanzador)`n`nGenerado: $((Get-Date).ToString('s')). HEAD: $head. Campana iniciada: $($campaignStart.ToString('o')).`n`n"
    $md += "Este informe se calcula con el plan actual y el estado del ejecutor: **una tarea solo cuenta como satisfactoria si su evidencia es vigente para el codigo actual**. Lo no ejecutado no es PASS.`n`n| Categoria | Tareas |`n|---|---|`n"
    foreach ($g in $groups.Keys) { $md += "| $g | $($groups[$g].Count) |`n" }
    foreach ($g in $groups.Keys) { $md += "`n## $g ($($groups[$g].Count))`n`n"; foreach ($l in ($groups[$g] | Select-Object -First 200)) { $md += "- $l`n" } }
    $md += "`n## Aislamiento y integridad del data\ real`n`n"
    $md += "- Sandbox de ejecucion: ``$SandboxRoot`` (data efectivo ``$(Join-Path $SandboxRoot 'data')``); data real: ``$(Join-Path $root 'data')``.`n"
    $iso = @(Get-ChildItem (Join-Path $out 'runs') -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | ForEach-Object { Join-Path $_.FullName 'isolation-report.json' } | Where-Object { Test-Path $_ })
    $md += "- Informes de aislamiento por ejecucion del ejecutor: $($iso.Count)" + $(if ($iso.Count) { " (el ultimo: ``$($iso[-1])``)" } else { '' }) + ".`n"
    $checks = @(if ($state.ContainsKey('dataChecks')) { $state['dataChecks'] })
    $bad = @($checks | Where-Object { $_['changeCount'] -gt 0 })
    $md += "- Comprobaciones de data\ real en esta campana: $($checks.Count); con cambios: $($bad.Count).`n"
    foreach ($b in $bad) { $md += "  - **$($b['label'])**: $($b['changeCount']) cambios (p. ej. $((@($b['changes']) | Select-Object -First 3) -join '; '))`n" }
    $md += "`n## Pasos del lanzador`n`n| Paso | Estado | Inicio | Fin | Huella |`n|---|---|---|---|---|`n"
    foreach ($name in $allSteps) {
        $s = if ($state['steps'].ContainsKey($name)) { $state['steps'][$name] } else { $null }
        if ($s) { $md += "| $name | $($s['status']) | $($s['startedAt']) | $(if ($s.ContainsKey('finishedAt')) { $s['finishedAt'] } else { '' }) | $(([string]$s['fingerprint']).Substring(0, [Math]::Min(12, ([string]$s['fingerprint']).Length))) |`n" }
        else { $md += "| $name | NO_EJECUTADO | | | |`n" }
    }
    $reportPath = Join-Path $root "target/archify-v3/estabilizacion/CERTIFICACION_CONSOLIDADA_$((Get-Date).ToString('yyyyMMddTHHmmss')).md"
    New-Item -ItemType Directory -Force -Path (Split-Path $reportPath) | Out-Null
    if (-not $DryRun) { $md | Set-Content -Path $reportPath -Encoding UTF8 }
    Add-Content $log "informe: $reportPath"
    Write-Host "  informe: $reportPath"
}

# --- resumen, estado y comprobacion final (siempre) ------------------------------------------------------------------------------------
if ($Status) {
    Write-Host "Campana: HEAD $($state['campaign'] | ForEach-Object { if ($_) { $_['head'] } else { '(ninguna)' } })"
    foreach ($name in $allSteps) {
        $s = if ($state['steps'].ContainsKey($name)) { $state['steps'][$name] } else { $null }
        if (-not $s) { Write-Host ("  {0,-10} sin resultado" -f $name); continue }
        $why = ''
        if ($s['components'] -is [hashtable]) { $changed = @(Compare-Components $s['components'] (Get-StepComponents $name (Get-Context $name))); if ($changed.Count) { $why = " (se repetiria: cambian $($changed -join ', '))" } elseif ($s['status'] -eq 'OK') { $why = ' (vigente)' } }
        Write-Host ("  {0,-10} {1,-24} {2}{3}" -f $name, $s['status'], $s['finishedAt'], $why)
    }
    return
}
Write-Host "`nResumen de pasos:"
foreach ($name in $allSteps) {
    $s = if ($state['steps'].ContainsKey($name)) { $state['steps'][$name] } else { $null }; Write-Host ("  {0,-10} {1}" -f $name, $(if ($s) { $s['status'] } else { '-' }))
}
if (-not $DryRun) {
    $final = @(Test-DataIntegrity 'final')
    Save-State
    if ($final.Count) { $script:DataChanged = $true }
}
$bad = @($state['steps'].Values | Where-Object { $_['status'] -in 'FAILED', 'BLOCKED_ISOLATION', 'DATA_CHANGED' })
if ($bad.Count -or $script:DataChanged) { exit 1 } else { exit 0 }
