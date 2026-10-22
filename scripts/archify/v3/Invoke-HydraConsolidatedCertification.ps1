<#
.SYNOPSIS
  Lanzador único, reanudable y NO autoexcluyente de la certificación consolidada de Archify V3.

.DESCRIPTION
  Ejecuta por pasos (cada uno con su registro y su punto de control) lo que hace falta para certificar el código actual:
    preflight -> selftest -> run-core (Maven/Vitest/node/python) -> run-e2e (Playwright) -> sync -> verify -> package -> report
  Un paso fallido NO impide registrar los demás pasos independientes: queda anotado como FAILED en el punto de control y el informe final lo
  distingue de lo ejecutado con éxito, de la evidencia histórica que sigue vigente, de lo pendiente por infraestructura, de lo inestable y de lo
  no ejecutado por decisión explícita. Una prueba omitida o no ejecutada NUNCA se convierte en PASS.

  El ejecutor V3 ya es reanudable por tarea (huella + evidencia inmutable): relanzar el script solo repite lo que cambió o falló. -Resume además
  salta los pasos del lanzador ya completados con la misma huella de entrada.

  Este script NO ejecuta git add, commit, push, reset, clean ni stash. Solo escribe bajo target/archify-v3/ (ignorado por Git) y los ficheros
  que genera el pipeline oficial de Archify (sync) cuando se pide el paso sync.

.PARAMETER Steps
  Pasos a ejecutar separados por comas dentro de una sola cadena (por defecto 'all'). Ejemplo: -Steps "preflight,run-core"
.PARAMETER Resume
  Salta los pasos con estado OK en el punto de control cuyo código y entradas no cambiaron.
.PARAMETER ForceAll
  Revalida todo (run --force "*"): ignora cualquier reutilización de evidencia.
.PARAMETER StrictFingerprint
  Desactiva el esquema de huella por cierre de dependencias: solo se reutiliza con todas las entradas declaradas idénticas.
.PARAMETER DryRun
  Muestra qué haría cada paso sin ejecutar nada (excepto el plan, que es de solo lectura).
.PARAMETER SkipE2E
  Omite el paso run-e2e por decisión explícita (queda registrado como SKIPPED_BY_DECISION, nunca como PASS).
.PARAMETER Checkpoint
  Fichero de puntos de control (por defecto target/archify-v3/certification-checkpoint.json).
.EXAMPLE
  powershell -File scripts/archify/v3/Invoke-HydraConsolidatedCertification.ps1 -DryRun
.EXAMPLE
  powershell -File scripts/archify/v3/Invoke-HydraConsolidatedCertification.ps1 -Resume
#>
[CmdletBinding()]
param(
    [string]$Steps = 'all',
    [switch]$Resume,
    [switch]$ForceAll,
    [switch]$StrictFingerprint,
    [switch]$DryRun,
    [switch]$SkipE2E,
    [string]$Checkpoint = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Set-Location $root
$out = Join-Path $root 'target/archify-v3'
New-Item -ItemType Directory -Force -Path $out, (Join-Path $out 'launcher-logs') | Out-Null
if ($Checkpoint -eq '') { $Checkpoint = Join-Path $out 'certification-checkpoint.json' }
$allSteps = @('preflight', 'selftest', 'run-core', 'run-e2e', 'sync', 'verify', 'package', 'report')
$stepList = if ($Steps -eq 'all') { $allSteps } else { @($Steps -split '[,;\s]+' | Where-Object { $_ }) }
$unknown = @($stepList | Where-Object { $allSteps -notcontains $_ })
if ($unknown.Count) { throw "Pasos desconocidos: $($unknown -join ', '). Válidos: $($allSteps -join ', ')" }
$runner = 'scripts/archify/v3/hydra_archify_v3.py'
$manifestPath = 'docs/architecture/archify/knowledge/tests/v3/MANIFEST_CERTIFICACION_V3.json'

function Get-Git([string[]]$arguments) { $r = & git @arguments 2>$null; if ($LASTEXITCODE -ne 0) { return '' }; return (($r | Out-String).Trim()) }

# --- punto de control ----------------------------------------------------------------------------------------------------------------
function ConvertTo-Hashtable($value) {   # compatible con Windows PowerShell 5.1 y PowerShell 7
    if ($value -is [System.Management.Automation.PSCustomObject]) { $table = @{}; foreach ($p in $value.PSObject.Properties) { $table[$p.Name] = ConvertTo-Hashtable $p.Value }; return $table }
    if ($value -is [System.Collections.IEnumerable] -and $value -isnot [string]) { return @($value | ForEach-Object { ConvertTo-Hashtable $_ }) }
    return $value
}
$state = if (Test-Path $Checkpoint) { ConvertTo-Hashtable (Get-Content $Checkpoint -Raw | ConvertFrom-Json) } else { @{ schema = 1; createdAt = (Get-Date).ToUniversalTime().ToString('o'); steps = @{} } }
function Save-Checkpoint { $state.updatedAt = (Get-Date).ToUniversalTime().ToString('o'); ($state | ConvertTo-Json -Depth 8) | Set-Content -Path $Checkpoint -Encoding utf8 }

# Huella de entrada del lanzador: HEAD + lista y hash de ficheros modificados/no versionados (sin target/). Cambia si cambia el código.
function Get-InputFingerprint {
    $head = Get-Git @('rev-parse', 'HEAD')
    $status = (& git status --porcelain -uall 2>$null | Where-Object { $_ -notmatch ' target/' } | Sort-Object) -join "`n"
    $bytes = [Text.Encoding]::UTF8.GetBytes("$head`n$status")
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower() } finally { $sha.Dispose() }
}
$inputFingerprint = Get-InputFingerprint

# Identidad de la campaña: el HEAD con el que empezó. Sobrevive a interrupciones y reanudaciones, de modo que lo ejecutado antes de una
# interrupción se sigue contando como «ejecutado en esta campaña» y no como evidencia histórica. Un HEAD nuevo abre una campaña nueva.
$campaignHead = Get-Git @('rev-parse', 'HEAD')
if (-not $DryRun -and ((-not $state.ContainsKey('campaign')) -or $state.campaign.head -ne $campaignHead)) {
    $state.campaign = @{ head = $campaignHead; startedAt = (Get-Date).ToUniversalTime().ToString('o') }
}

function Invoke-Step([string]$name, [scriptblock]$body) {
    if ($stepList -notcontains $name) { return }
    $prev = $state.steps[$name]
    if ($Resume -and $prev -and $prev.status -eq 'OK' -and $prev.inputFingerprint -eq $inputFingerprint) { Write-Host "[$name] ya completado con las mismas entradas (resume)"; return }
    $log = Join-Path $out "launcher-logs/$name-$((Get-Date).ToString('yyyyMMddTHHmmss')).log"
    $record = @{ status = 'RUNNING'; startedAt = (Get-Date).ToUniversalTime().ToString('o'); inputFingerprint = $inputFingerprint; log = $log }
    $state.steps[$name] = $record; Save-Checkpoint
    Write-Host "[$name] inicio -> $log"
    try {
        $result = & $body $log
        $record.status = if ($null -eq $result) { 'OK' } else { [string]$result }
    } catch {
        $record.status = 'FAILED'; $record.error = $_.Exception.Message
        Add-Content -Path $log -Value "ERROR: $($_.Exception.Message)"
    }
    $record.finishedAt = (Get-Date).ToUniversalTime().ToString('o')
    $state.steps[$name] = $record; Save-Checkpoint
    Write-Host "[$name] $($record.status)"
}

# Ejecuta un comando, vuelca su salida al log del paso y devuelve el código de salida. Nunca lanza: un fallo se anota y el lanzador sigue.
function Invoke-Logged([string]$log, [string]$exe, [string[]]$arguments) {
    Add-Content -Path $log -Value ("> $exe " + ($arguments -join ' '))
    if ($DryRun) { Add-Content -Path $log -Value '(dry run: no ejecutado)'; Write-Host "  (dry run) $exe $($arguments -join ' ')"; return 0 }
    & $exe @arguments *>> $log
    return $LASTEXITCODE
}

# Ids de tareas a ejecutar ahora (acción RUN del plan) filtrados por tipo.
function Get-RunIds([string]$kindFilter) {
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    $kinds = @{}; foreach ($t in $manifest.tasks) { $kinds[$t.id] = $t.kind }
    $planArgs = @($runner, 'plan'); if ($StrictFingerprint) { $planArgs += '--strict-fingerprint' }
    if ($ForceAll) { $planArgs += @('--force', '*') }
    $lines = & python @planArgs 2>$null
    $ids = foreach ($line in $lines) { if ($line -match '^\s+RUN\s+(\S+)') { $Matches[1] } }
    return @($ids | Where-Object { $kinds.ContainsKey($_) -and (($kindFilter -eq 'playwright') -eq ($kinds[$_] -eq 'playwright')) })
}

# --- pasos ---------------------------------------------------------------------------------------------------------------------------
# Huella de solo lectura del data/ real (ruta, tamaño, fecha). Se toma antes de la campaña y se compara al final: las pruebas no deben tocarlo.
function Get-DataSnapshot {
    $data = Join-Path $root 'data'
    if (-not (Test-Path -LiteralPath $data)) { return @{} }
    $snapshot = @{}
    Get-ChildItem -LiteralPath $data -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
        $relative = $_.FullName.Substring($data.Length + 1)
        $snapshot[$relative] = "$($_.PSIsContainer)|$($_.Length)|$($_.LastWriteTimeUtc.Ticks)"
    }
    return $snapshot
}
$dataSnapshotFile = Join-Path $out 'data-snapshot-pre-campaign.json'

Invoke-Step 'preflight' {
    param($log)
    $problems = @()
    if (-not $DryRun) { (Get-DataSnapshot) | ConvertTo-Json -Depth 3 | Set-Content -Path $dataSnapshotFile -Encoding utf8; Add-Content $log "huella previa de data/ guardada en $dataSnapshotFile" }
    $head = Get-Git @('rev-parse', 'HEAD'); $branch = Get-Git @('branch', '--show-current'); $staged = Get-Git @('diff', '--cached', '--name-only')
    Add-Content $log "HEAD=$head`nbranch=$branch`nstaged=[$staged]"
    foreach ($tool in 'python', 'node', 'git') { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { $problems += "falta la herramienta $tool" } }
    if (-not (Test-Path (Join-Path $root 'mvnw.cmd'))) { $problems += 'falta mvnw.cmd' }
    if (-not (Test-Path (Join-Path $root 'frontend/node_modules'))) { $problems += 'falta frontend/node_modules (npm ci)' }
    $free = (Get-PSDrive -Name ((Get-Item $root).PSDrive.Name)).Free
    if ($free -lt 2GB) { $problems += "poco espacio libre ($([math]::Round($free/1GB,1)) GB)" }
    $plan = & python $runner plan 2>&1 | Select-Object -First 1; Add-Content $log $plan; Write-Host "  $plan"
    if ($problems.Count) { Add-Content $log ($problems -join "`n"); throw ($problems -join '; ') }
}

Invoke-Step 'selftest' {
    param($log)
    $code = Invoke-Logged $log 'python' @($runner, 'selftest')
    if ($code -ne 0) { throw "selftest del ejecutor falló (código $code)" }
}

Invoke-Step 'run-core' {
    param($log)
    $ids = Get-RunIds 'core'
    Add-Content $log "tareas a ejecutar: $($ids.Count)"
    if ($ids.Count -eq 0) { return 'OK' }
    $arguments = @($runner, 'run', '--only', ($ids -join ','), '--no-sync', '--retry-failed')
    if ($StrictFingerprint) { $arguments += '--strict-fingerprint' }
    if ($ForceAll) { $arguments += @('--force', '*') }
    $code = Invoke-Logged $log 'python' $arguments
    # El ejecutor es no autoexcluyente por tarea: un código distinto de cero indica que alguna tarea no pasó, no que el paso no se ejecutó.
    if ($code -ne 0) { return 'COMPLETED_WITH_FAILURES' }
}

Invoke-Step 'run-e2e' {
    param($log)
    if ($SkipE2E) { Add-Content $log 'omitido por decisión explícita (-SkipE2E)'; return 'SKIPPED_BY_DECISION' }
    $ids = Get-RunIds 'playwright'
    Add-Content $log "especificaciones a ejecutar: $($ids.Count)"
    if ($ids.Count -eq 0) { return 'OK' }
    $arguments = @($runner, 'run', '--only', ($ids -join ','), '--no-sync', '--retry-failed')
    if ($ForceAll) { $arguments += @('--force', '*') }
    $code = Invoke-Logged $log 'python' $arguments
    if ($code -ne 0) { return 'COMPLETED_WITH_FAILURES' }
}

Invoke-Step 'sync' {
    param($log)
    $code = Invoke-Logged $log 'python' @($runner, 'sync', '--restage')
    if ($code -ne 0) { throw "sync falló (código $code)" }
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
    if ($LASTEXITCODE -ne 0) { throw 'el empaquetado de evidencia falló' }
}

Invoke-Step 'report' {
    param($log)
    $stateFile = Join-Path $out 'state.json'
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    $records = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw | ConvertFrom-Json).tasks } else { $null }
    $lastRunStart = if ($state.ContainsKey('campaign')) { $state.campaign } else { $state.steps['run-core'] }
    $groups = [ordered]@{ 'Ejecutadas y satisfactorias en esta campaña' = @(); 'Evidencia histórica que sigue vigente (no reejecutada)' = @(); 'Pendientes por infraestructura o herramientas externas' = @();
        'Fallidas' = @(); 'Inestables (reintentos o resultados distintos)' = @(); 'No ejecutadas por decisión explícita' = @(); 'Sin evidencia vigente (pendientes de ejecutar)' = @() }
    $plan = & python $runner plan 2>$null
    $planAction = @{}; foreach ($line in $plan) { if ($line -match '^\s+(RUN|REUSE|BLOCKED|PLANNED|SKIP)\s+(\S+)') { $planAction[$Matches[2]] = $Matches[1] } }
    $campaignStart = if ($lastRunStart -and $lastRunStart.startedAt) { [datetime]::Parse($lastRunStart.startedAt).ToUniversalTime() } else { [datetime]::UtcNow }
    foreach ($t in $manifest.tasks) {
        $rec = if ($records) { $records.PSObject.Properties[$t.id] } else { $null }; $rec = if ($rec) { $rec.Value } else { $null }
        $action = $planAction[$t.id]; $verdict = if ($rec) { $rec.verdict } else { 'NONE' }
        $finished = if ($rec -and $rec.finishedAt) { [datetime]::Parse($rec.finishedAt).ToUniversalTime() } else { [datetime]::MinValue }
        $attempts = if ($rec -and $rec.PSObject.Properties['attempts']) { [int]$rec.attempts } else { 0 }
        $line = "$($t.id) [$($t.kind)] veredicto=$verdict plan=$action"
        if ($t.kind -eq 'playwright' -and $SkipE2E -and $action -eq 'RUN') { $groups['No ejecutadas por decisión explícita'] += $line }
        elseif ($verdict -in 'BLOCKED', 'PLANNED' -or ($rec -and $rec.reason -match 'missing prerequisite')) { $groups['Pendientes por infraestructura o herramientas externas'] += $line }
        elseif ($verdict -in 'FAIL', 'COMPILE_ERROR') { $groups['Fallidas'] += $line }
        elseif ($verdict -eq 'NOT_EXECUTED') { $groups['Sin evidencia vigente (pendientes de ejecutar)'] += $line }
        elseif ($verdict -eq 'PASS' -and $action -eq 'REUSE' -and $finished -ge $campaignStart) { $groups['Ejecutadas y satisfactorias en esta campaña'] += $line; if ($attempts -gt 1) { $groups['Inestables (reintentos o resultados distintos)'] += "$line (intentos=$attempts)" } }
        elseif ($verdict -eq 'PASS' -and $action -eq 'REUSE') { $groups['Evidencia histórica que sigue vigente (no reejecutada)'] += $line }
        else { $groups['Sin evidencia vigente (pendientes de ejecutar)'] += $line }
    }
    $md = "# Certificación consolidada (informe del lanzador)`n`nGenerado: $((Get-Date).ToString('s')). HEAD: $(Get-Git @('rev-parse','HEAD')). Huella de entradas: $inputFingerprint.`n`n"
    $md += "Este informe se calcula con el plan actual y el estado del ejecutor: **una tarea solo cuenta como satisfactoria si su evidencia es vigente para el código actual**. Lo no ejecutado no es PASS.`n`n| Categoría | Tareas |`n|---|---|`n"
    foreach ($g in $groups.Keys) { $md += "| $g | $($groups[$g].Count) |`n" }
    foreach ($g in $groups.Keys) { $md += "`n## $g ($($groups[$g].Count))`n`n"; foreach ($l in ($groups[$g] | Select-Object -First 200)) { $md += "- $l`n" } }
    $md += "`n## Integridad del data/ real durante la campaña`n`n"
    if (Test-Path $dataSnapshotFile) {
        $before = ConvertTo-Hashtable (Get-Content $dataSnapshotFile -Raw | ConvertFrom-Json); $after = Get-DataSnapshot
        $changes = @(); foreach ($k in $after.Keys) { if (-not $before.ContainsKey($k)) { $changes += "NUEVO $k" } elseif ($before[$k] -ne $after[$k] -and -not $k.EndsWith('\')) { if ($after[$k] -notmatch '^True') { $changes += "MODIFICADO $k" } } }
        foreach ($k in $before.Keys) { if (-not $after.ContainsKey($k)) { $changes += "ELIMINADO $k" } }
        if ($changes.Count -eq 0) { $md += "Sin cambios en data/ entre el inicio (preflight) y este informe.`n" }
        else { $md += "**ATENCIÓN: $($changes.Count) cambios en data/ durante la campaña; alguna prueba no está aislada.**`n`n"; foreach ($c in ($changes | Select-Object -First 100)) { $md += "- $c`n" } }
    } else { $md += "No hay huella previa de data/ (el preflight no se ejecutó en esta campaña): integridad no verificada.`n" }
    $md += "`n## Pasos del lanzador`n`n| Paso | Estado | Inicio | Fin |`n|---|---|---|---|`n"
    foreach ($name in 'preflight', 'selftest', 'run-core', 'run-e2e', 'sync', 'verify', 'package') { $s = $state.steps[$name]; if ($s) { $md += "| $name | $($s.status) | $($s.startedAt) | $($s.finishedAt) |`n" } else { $md += "| $name | NO_EJECUTADO | | |`n" } }
    $reportPath = Join-Path $root "target/archify-v3/estabilizacion/CERTIFICACION_CONSOLIDADA_$((Get-Date).ToString('yyyyMMddTHHmmss')).md"
    New-Item -ItemType Directory -Force -Path (Split-Path $reportPath) | Out-Null
    if (-not $DryRun) { $md | Set-Content -Path $reportPath -Encoding utf8 }
    Add-Content $log "informe: $reportPath"
    Write-Host "  informe: $reportPath"
}

# --- resumen -------------------------------------------------------------------------------------------------------------------------
Write-Host "`nResumen de pasos:"
foreach ($name in 'preflight', 'selftest', 'run-core', 'run-e2e', 'sync', 'verify', 'package', 'report') {
    $s = $state.steps[$name]; Write-Host ("  {0,-10} {1}" -f $name, $(if ($s) { $s.status } else { '-' }))
}
$bad = @($state.steps.Values | Where-Object { $_.status -in 'FAILED' })
if ($bad.Count) { exit 1 } else { exit 0 }
