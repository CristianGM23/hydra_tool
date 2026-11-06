<#
.SYNOPSIS
  Empaqueta la evidencia ejecutada de Archify V3 en un paquete SEPARADO del repositorio, con hashes SHA-256 y referencia al commit.

.DESCRIPTION
  La evidencia inmutable vive en target/archify-v3/ (ignorada por Git a propósito: son artefactos de ejecución). Este script la conserva sin
  versionarla: crea target/archify-v3/packages/hydra-archify-v3-evidence-<commit>-<fecha>.zip con
    - evidence/ (XML de Surefire y resultados de Vitest/Playwright) y runs/*/logs, state.json, attestations/ y certification-checkpoint.json,
    - EVIDENCE-MANIFEST.sha256 (SHA-256 de cada fichero) y PACKAGE.json (commit, rama, si el árbol estaba limpio, huella de entradas, hash del manifiesto),
  y escribe junto al zip un .sha256 con el hash del paquete. No usa git add/commit/push ni escribe fuera de target/archify-v3/packages/.
.PARAMETER Destination
  Carpeta de destino (por defecto target/archify-v3/packages).
#>
[CmdletBinding()]
param([string]$Destination = '')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Set-Location $root
$out = Join-Path $root 'target/archify-v3'
if ($Destination -eq '') { $Destination = Join-Path $out 'packages' }
New-Item -ItemType Directory -Force -Path $Destination | Out-Null

function Get-Git([string[]]$arguments) { $r = & git @arguments 2>$null; if ($LASTEXITCODE -ne 0) { return '' }; return (($r | Out-String).Trim()) }
$head = Get-Git @('rev-parse', 'HEAD'); $branch = Get-Git @('branch', '--show-current')
$dirty = @(& git status --porcelain 2>$null | Where-Object { $_ -notmatch ' target/' }).Count
$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmss') + 'Z'
# Solo los ficheros GENERADOS aqui van a un directorio temporal corto; la evidencia se lee en su sitio y se escribe directamente en el ZIP.
# Las rutas de la evidencia pueden superar 260 caracteres: se leen con el prefijo \\?\ (Windows PowerShell 5.1 / .NET Framework).
$staging = Join-Path ([IO.Path]::GetTempPath()) ("hev-" + $stamp.Substring(9))
New-Item -ItemType Directory -Force -Path $staging | Out-Null
function ConvertTo-LongPath([string]$path) { $full = [IO.Path]::GetFullPath($path); if ($full.StartsWith('\\?\')) { return $full }; return '\\?\' + $full }
function Get-LongFiles([string]$dir) {
    $long = ConvertTo-LongPath $dir
    if (-not [IO.Directory]::Exists($long)) { return @() }
    return @([IO.Directory]::EnumerateFiles($long, '*', [IO.SearchOption]::AllDirectories))
}
function Get-StreamSha256([string]$longPath) {
    $stream = [IO.File]::OpenRead($longPath); $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream)) -replace '-', '').ToLowerInvariant() } finally { $sha.Dispose(); $stream.Dispose() }
}
$outLong = (ConvertTo-LongPath $out).TrimEnd('\')
$entries = New-Object System.Collections.Generic.List[object]   # @{ source = ruta larga; name = nombre en el ZIP }
try {
    foreach ($item in 'evidence', 'attestations') { foreach ($file in (Get-LongFiles (Join-Path $out $item))) { $entries.Add(@{ source = $file; name = $file.Substring($outLong.Length + 1).Replace('\', '/') }) } }
    foreach ($item in 'state.json', 'certification-checkpoint.json') { $p = Join-Path $out $item; if (Test-Path -LiteralPath $p) { $entries.Add(@{ source = (ConvertTo-LongPath $p); name = $item }) } }
    Get-ChildItem (Join-Path $out 'runs') -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        foreach ($file in (Get-LongFiles (Join-Path $_.FullName 'logs'))) { $entries.Add(@{ source = $file; name = $file.Substring($outLong.Length + 1).Replace('\', '/') }) }
    }
    # --- trazabilidad: estado del árbol, plan vigente, auditorías y vigencia de cada tarea -----------------------------------------------
    $runner = 'scripts/archify/v3/hydra_archify_v3.py'
    $treeStatus = @(& git status --porcelain -uall 2>$null | Where-Object { $_ -notmatch ' target/' })
    $staged = @(& git diff --cached --name-only 2>$null)
    $treeLines = @("HEAD=$head", "branch=$branch", "staged=$($staged.Count)", "workingTreeChanges=$($treeStatus.Count)", '--- git status --porcelain (sin target/) ---') + $treeStatus
    $treeLines | Set-Content -Path (Join-Path $staging 'ESTADO-ARBOL.txt') -Encoding utf8
    $previousPreference = $ErrorActionPreference; $ErrorActionPreference = 'Continue'   # el ejecutor escribe su registro por stderr
    $planOutput = @(& python $runner plan 2>&1 | ForEach-Object { "$_" })
    $planOutput | Set-Content -Path (Join-Path $staging 'PLAN.txt') -Encoding utf8
    $planAction = @{}; foreach ($line in $planOutput) { if ($line -match '^\s+(RUN|REUSE|BLOCKED|PLANNED|SKIP)\s+(\S+)') { $planAction[$Matches[2]] = $Matches[1] } }
    $auditEvidence = @(& python $runner audit-evidence 2>&1 | ForEach-Object { "$_" }); $auditEvidenceCode = $LASTEXITCODE
    $auditEvidence | Set-Content -Path (Join-Path $staging 'AUDIT-EVIDENCE.txt') -Encoding utf8
    $auditStrong = @(& python $runner audit-strong 2>&1 | ForEach-Object { "$_" }); $auditStrongCode = $LASTEXITCODE
    $auditStrong | Set-Content -Path (Join-Path $staging 'AUDIT-STRONG.txt') -Encoding utf8
    $ErrorActionPreference = $previousPreference
    $campaignStart = [datetime]::MinValue
    $checkpoint = Join-Path $out 'certification-checkpoint.json'
    if (Test-Path $checkpoint) { $cp = Get-Content $checkpoint -Raw | ConvertFrom-Json; if ($cp.PSObject.Properties['campaign'] -and $cp.campaign.head -eq $head) { $campaignStart = [datetime]::Parse($cp.campaign.startedAt).ToUniversalTime() } }
    $stateFile = Join-Path $out 'state.json'
    $tasks = if (Test-Path $stateFile) { (Get-Content $stateFile -Raw | ConvertFrom-Json).tasks } else { $null }
    $rows = @(); $counts = @{}
    if ($tasks) {
        foreach ($p in $tasks.PSObject.Properties) {
            $r = $p.Value; $action = if ($planAction.ContainsKey($p.Name)) { $planAction[$p.Name] } else { 'FUERA_DEL_MANIFIESTO' }
            $finished = if ($r.PSObject.Properties['finishedAt'] -and $r.finishedAt) { [datetime]::Parse($r.finishedAt).ToUniversalTime() } else { [datetime]::MinValue }
            $vigencia = if ($action -eq 'FUERA_DEL_MANIFIESTO') { 'NO_APLICA' }
                elseif ($action -eq 'RUN') { 'PENDIENTE_O_OBSOLETA' }
                elseif (-not $r.PSObject.Properties['verdict'] -or $r.verdict -ne 'PASS') { 'SIN_EVIDENCIA_VALIDA' }
                elseif ($finished -ge $campaignStart -and $campaignStart -gt [datetime]::MinValue) { 'EJECUTADA_EN_ESTA_CAMPANA' }
                else { 'HISTORICA_VIGENTE_NO_REEJECUTADA' }
            $counts[$vigencia] = 1 + $(if ($counts.ContainsKey($vigencia)) { $counts[$vigencia] } else { 0 })
            $fp = if ($r.PSObject.Properties['fingerprintV2'] -and $r.fingerprintV2) { [string]$r.fingerprintV2 } else { [string]$r.fingerprint }
            $rows += [pscustomobject]@{ id = $p.Name; kind = $r.kind; verdict = $r.verdict; plan = $action; vigencia = $vigencia; finishedAt = $(if ($r.PSObject.Properties['finishedAt']) { $r.finishedAt } else { '' }); runId = $(if ($r.PSObject.Properties['runId']) { $r.runId } else { '' }); attempts = $(if ($r.PSObject.Properties['attempts']) { $r.attempts } else { '' }); fingerprint = $(if ($fp.Length -gt 16) { $fp.Substring(0, 16) } else { $fp }); reason = $(if ($r.PSObject.Properties['reason']) { [string]$r.reason } else { '' }) }
        }
        $rows | Sort-Object id | Export-Csv -Path (Join-Path $staging 'TAREAS.csv') -NoTypeInformation -Encoding utf8
    }
    $notPass = @($rows | Where-Object { $_.verdict -ne 'PASS' })
    $reasons = @()
    if (@($planAction.Values | Where-Object { $_ -eq 'RUN' }).Count -gt 0) { $reasons += 'quedan tareas por ejecutar en el plan (RUN)' }
    if ($auditEvidenceCode -ne 0) { $reasons += 'audit-evidence no esta limpio' }
    if ($auditStrongCode -ne 0) { $reasons += 'audit-strong no esta limpio' }
    if ($notPass.Count) { $reasons += "$($notPass.Count) tareas sin veredicto PASS" }
    if ($campaignStart -eq [datetime]::MinValue) { $reasons += 'no hay una campana registrada para este HEAD (certification-checkpoint.json)' }
    # el codigo probado debe estar versionado: con cambios sin commit en codigo, pruebas o manifiesto, el HEAD no describe por si solo lo certificado
    $codeChanges = @($treeStatus | Where-Object { $_.Length -gt 3 -and $_.Substring(3).Trim('"') -match '^(src/|frontend/src/|frontend/e2e/|frontend/package|pom\.xml|scripts/archify/|docs/architecture/archify/knowledge/tests/v3/)' })
    if ($codeChanges.Count) { $reasons += "$($codeChanges.Count) ficheros de codigo, pruebas o manifiesto sin versionar (p. ej. $($codeChanges[0].Substring(3))): el commit no describe por si solo lo probado" }
    $latestReport = Get-ChildItem (Join-Path $out 'estabilizacion') -Filter 'CERTIFICACION_CONSOLIDADA_*.md' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime | Select-Object -Last 1
    if ($latestReport) { Copy-Item $latestReport.FullName (Join-Path $staging $latestReport.Name) }
    foreach ($generated in (Get-ChildItem -Path $staging -File)) { $entries.Add(@{ source = (ConvertTo-LongPath $generated.FullName); name = $generated.Name }) }
    if ($entries.Count -eq 0) { throw 'no hay evidencia que empaquetar en target/archify-v3' }
    $sorted = @($entries | Sort-Object { $_.name })
    $lines = foreach ($e in $sorted) { '{0}  {1}' -f (Get-StreamSha256 $e.source), $e.name }
    $manifestFile = Join-Path $staging 'EVIDENCE-MANIFEST.sha256'
    $lines | Set-Content -Path $manifestFile -Encoding ascii
    $info = [ordered]@{ schema = 2; createdAt = (Get-Date).ToUniversalTime().ToString('o'); commit = $head; branch = $branch; workingTreeChanges = $dirty
        status = $(if ($reasons.Count) { 'PROVISIONAL' } else { 'COMPLETE' }); provisionalReasons = $reasons
        planRun = @($planAction.Values | Where-Object { $_ -eq 'RUN' }).Count; planReuse = @($planAction.Values | Where-Object { $_ -eq 'REUSE' }).Count
        auditEvidenceExitCode = $auditEvidenceCode; auditStrongExitCode = $auditStrongCode; campaignStartedAt = $(if ($campaignStart -gt [datetime]::MinValue) { $campaignStart.ToString('o') } else { $null })
        vigencia = $counts
        note = 'La evidencia corresponde al código del árbol de trabajo en el momento de crearse el paquete; si workingTreeChanges > 0 el commit no la describe por sí solo: vuelva a empaquetar tras versionar.'
        files = $sorted.Count; evidenceManifestSha256 = (Get-FileHash -Algorithm SHA256 -Path $manifestFile).Hash.ToLower() }
    ($info | ConvertTo-Json) | Set-Content -Path (Join-Path $staging 'PACKAGE.json') -Encoding utf8
    $short = if ($head.Length -ge 8) { $head.Substring(0, 8) } else { 'nocommit' }
    $zip = Join-Path $Destination "hydra-archify-v3-evidence-$short-$stamp.zip"
    Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
    $all = @($sorted) + @(@{ source = (ConvertTo-LongPath $manifestFile); name = 'EVIDENCE-MANIFEST.sha256' }, @{ source = (ConvertTo-LongPath (Join-Path $staging 'PACKAGE.json')); name = 'PACKAGE.json' })
    $zipStream = [IO.File]::Open($zip, [IO.FileMode]::CreateNew)
    $archive = New-Object IO.Compression.ZipArchive($zipStream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($e in $all) {
            $entry = $archive.CreateEntry($e.name, [IO.Compression.CompressionLevel]::Optimal)
            $target = $entry.Open(); $source = [IO.File]::OpenRead($e.source)
            try { $source.CopyTo($target) } finally { $source.Dispose(); $target.Dispose() }
        }
    } finally { $archive.Dispose(); $zipStream.Dispose() }
    $hash = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLower()
    "$hash  $(Split-Path $zip -Leaf)" | Set-Content -Path "$zip.sha256" -Encoding ascii
    Write-Host "paquete: $zip"; Write-Host "sha256 : $hash"; Write-Host "commit : $head (cambios sin versionar fuera de target/: $dirty)"
} finally {
    Remove-Item -Recurse -Force -Path $staging -ErrorAction SilentlyContinue
}
