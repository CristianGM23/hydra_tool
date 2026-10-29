<#
  Funciones de apoyo del lanzador de certificacion consolidada (se cargan con dot-sourcing; no ejecutan nada por si solas).
  Windows PowerShell 5.1. Contiene: huellas por contenido y por paso, puntos de control atomicos con suma de verificacion, huella de data/ con
  metadatos, comprobaciones de aislamiento y creacion del sandbox fisico de la campana. Ninguna funcion usa git add/commit/push/reset/clean/stash.
#>

# ------------------------------------------------------------------------------------------------------------------ hashing
$script:ShaCache = @{}

function Get-TextSha256([string]$text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($text))) -replace '-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

# SHA-256 del contenido (en memoria por proceso, con clave ruta+tamano+fecha: cambia si cambia el fichero)
function Get-FileSha256([string]$path) {
    $item = Get-Item -LiteralPath $path -Force
    $key = "$($item.FullName)|$($item.Length)|$($item.LastWriteTimeUtc.Ticks)"
    if ($script:ShaCache.ContainsKey($key)) { return $script:ShaCache[$key] }
    $value = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $script:ShaCache[$key] = $value
    return $value
}

# Huella determinista de un conjunto de componentes etiqueta -> hash (orden ordinal; nunca depende del orden de insercion)
function Get-ComponentFingerprint($components) {
    $lines = @($components.Keys | Sort-Object -CaseSensitive | ForEach-Object { "$_=$($components[$_])" })
    return Get-TextSha256 ($lines -join "`n")
}

# Hash de un conjunto de ficheros (ruta relativa + contenido). La lista la decide quien llama (git en el lanzador, una lista explicita en las pruebas).
function Get-FilesContentHash([string]$root, [string[]]$relativePaths) {
    $lines = foreach ($relative in ($relativePaths | Sort-Object -Unique -CaseSensitive)) {
        $full = Join-Path $root $relative
        if (Test-Path -LiteralPath $full -PathType Leaf) { "$relative=$(Get-FileSha256 $full)" } else { "$relative=MISSING" }
    }
    return Get-TextSha256 (@($lines) -join "`n")
}

# Lista de ficheros del espacio de trabajo que forman el arbol espejado: versionados + no versionados no ignorados, menos las exclusiones del sandbox.
$script:SandboxExcludedRoots = @('.git', 'target', 'build', 'data', 'frontend/node_modules', 'frontend/dist', 'frontend/test-results', 'frontend/.vite')

function Test-SandboxExcluded([string]$relative) {
    $normal = $relative.Replace('\', '/')
    foreach ($excluded in $script:SandboxExcludedRoots) { if ($normal -eq $excluded -or $normal.StartsWith("$excluded/")) { return $true } }
    if ($normal -match '(^|/)__pycache__(/|$)' -or $normal -match '\.pyc$') { return $true }
    return $false
}

function Get-MirrorFileList([string]$root) {
    Push-Location $root
    try {
        $insideGit = Test-Path -LiteralPath (Join-Path $root '.git')
        if ($insideGit) {
            $tracked = @(& git -c core.quotepath=false ls-files 2>$null)
            $untracked = @(& git -c core.quotepath=false ls-files --others --exclude-standard 2>$null)
        } else {   # sin repositorio (pruebas del propio modulo): todos los ficheros menos las exclusiones
            $base = (Get-Item -LiteralPath $root).FullName.TrimEnd('\')
            $tracked = @(Get-ChildItem -LiteralPath $root -Recurse -File -Force | ForEach-Object { $_.FullName.Substring($base.Length + 1).Replace('\', '/') })
            $untracked = @()
        }
    } finally { Pop-Location }
    return @(($tracked + $untracked) | Where-Object { $_ -and -not (Test-SandboxExcluded $_) -and (Test-Path -LiteralPath (Join-Path $root $_) -PathType Leaf) } | Sort-Object -Unique -CaseSensitive)
}

# Reparto de ids en bloques consecutivos de a lo sumo $size. Devuelve UNA CADENA por bloque ("id1,id2,..."): sin listas anidadas, que PowerShell desenrolla.
function Split-IntoChunks([string[]]$ids, [int]$size) {
    $ids = @($ids)
    if ($ids.Count -eq 0) { return }
    if ($size -lt 1) { throw 'el tamano de bloque debe ser >= 1' }
    for ($i = 0; $i -lt $ids.Count; $i += $size) { ($ids[$i..([Math]::Min($i + $size, $ids.Count) - 1)]) -join ',' }
}
# ------------------------------------------------------------------------------------------------------------------ fingerprints per step
$script:StepKinds = @{ 'run-core' = 'core'; 'run-e2e' = 'playwright' }

# Componentes REALES de cada paso. Un paso solo se reutiliza si TODOS sus componentes coinciden con los del punto de control.
# $context: root, head, params (hashtable), taskFingerprints (id -> @{kind; v1}), mirrorTreeHash, stateHash, syncOutputsHash, fragmentsHash, toolsHash
function Get-StepComponents([string]$step, $context) {
    $root = $context.root
    $c = [ordered]@{}
    $c['launcher'] = Get-FileSha256 (Join-Path $root 'scripts/archify/v3/Invoke-HydraConsolidatedCertification.ps1')
    $c['support'] = Get-FileSha256 (Join-Path $root 'scripts/archify/v3/HydraCertificationSupport.ps1')
    $c['executor'] = Get-FileSha256 (Join-Path $root 'scripts/archify/v3/hydra_archify_v3.py')
    $c['manifest'] = Get-FileSha256 (Join-Path $root 'docs/architecture/archify/knowledge/tests/v3/MANIFEST_CERTIFICACION_V3.json')
    $p = $context.params
    switch ($step) {
        'preflight' { $c['head'] = $context.head }
        'selftest' { }
        'isolate' { $c['mirrorTree'] = $context.mirrorTreeHash }
        'run-core' {
            $c['params'] = "ForceAll=$($p.ForceAll);StrictFingerprint=$($p.StrictFingerprint)"
            $c['tasks'] = Get-TaskSetHash $context.taskFingerprints 'core'
        }
        'run-e2e' {
            $c['params'] = "ForceAll=$($p.ForceAll);SkipE2E=$($p.SkipE2E)"
            $c['tasks'] = Get-TaskSetHash $context.taskFingerprints 'playwright'
        }
        'sync' { $c['state'] = $context.stateHash; $c['fragments'] = $context.fragmentsHash; $c['tools'] = $context.toolsHash }
        'verify' { $c['state'] = $context.stateHash; $c['syncOutputs'] = $context.syncOutputsHash }
        'package' { $c['head'] = $context.head; $c['state'] = $context.stateHash; $c['syncOutputs'] = $context.syncOutputsHash }
        'report' { $c['head'] = $context.head; $c['state'] = $context.stateHash; $c['syncOutputs'] = $context.syncOutputsHash }
        default { throw "paso desconocido: $step" }
    }
    return $c
}

function Get-TaskSetHash($taskFingerprints, [string]$kindFilter) {
    $lines = foreach ($id in ($taskFingerprints.Keys | Sort-Object -CaseSensitive)) {
        $isPlaywright = ($taskFingerprints[$id].kind -eq 'playwright')
        if (($kindFilter -eq 'playwright') -eq $isPlaywright) { "$id=$($taskFingerprints[$id].v1)" }
    }
    return Get-TextSha256 (@($lines) -join "`n")
}

# Primer componente distinto entre dos tablas (para explicar por que se invalida un paso)
function Compare-Components($previous, $current) {
    $changed = @()
    foreach ($k in $current.Keys) { if (-not $previous.ContainsKey($k) -or $previous[$k] -ne $current[$k]) { $changed += $k } }
    foreach ($k in $previous.Keys) { if (-not $current.Contains($k)) { $changed += $k } }
    return @($changed | Sort-Object -Unique)
}

# ------------------------------------------------------------------------------------------------------------------ checkpoint
function ConvertTo-PlainHashtable($value) {   # compatible con Windows PowerShell 5.1 y PowerShell 7
    if ($value -is [System.Management.Automation.PSCustomObject]) { $table = @{}; foreach ($p in $value.PSObject.Properties) { $table[$p.Name] = ConvertTo-PlainHashtable $p.Value }; return $table }
    if ($value -is [System.Collections.IDictionary]) { $table = @{}; foreach ($k in $value.Keys) { $table[[string]$k] = ConvertTo-PlainHashtable $value[$k] }; return $table }
    # la coma evita que una lista de un solo elemento (o vacia) se desenrolle al salir de la funcion y pierda su condicion de lista
    if ($value -is [System.Collections.IEnumerable] -and $value -isnot [string]) { return ,@($value | ForEach-Object { ConvertTo-PlainHashtable $_ }) }
    return $value
}

# JSON canonico (claves ordenadas, sin espacios): la suma de verificacion no puede depender del orden de enumeracion de las tablas hash
function ConvertTo-CanonicalJson($value) {
    if ($null -eq $value) { return 'null' }
    if ($value -is [string]) { return (ConvertTo-Json -InputObject $value -Compress) }
    if ($value -is [bool]) { return $(if ($value) { 'true' } else { 'false' }) }
    if ($value -is [System.Collections.IDictionary]) {
        $parts = foreach ($k in ($value.Keys | Sort-Object -CaseSensitive)) { (ConvertTo-Json -InputObject ([string]$k) -Compress) + ':' + (ConvertTo-CanonicalJson $value[$k]) }
        return '{' + (@($parts) -join ',') + '}'
    }
    if ($value -is [System.Collections.IEnumerable]) {
        $items = @($value | ForEach-Object { ConvertTo-CanonicalJson $_ })
        if ($items.Count -eq 0) { return 'null' }   # ConvertTo-Json (5.1) escribe una lista vacia como null: ambas formas deben dar la misma suma
        return '[' + ($items -join ',') + ']'
    }
    if ($value -is [ValueType]) { return ([string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0}', $value)) }
    return (ConvertTo-Json -InputObject ([string]$value) -Compress)
}

function Get-CheckpointChecksum($state) {
    $copy = @{}
    foreach ($k in $state.Keys) { if ($k -ne 'checksum') { $copy[$k] = $state[$k] } }
    return Get-TextSha256 (ConvertTo-CanonicalJson $copy)
}

function New-CheckpointState {
    return @{ schema = 2; createdAt = (Get-Date).ToUniversalTime().ToString('o'); steps = @{}; campaign = $null }
}

# Escritura atomica: temporal completo (con flush a disco) y reemplazo. Un lector nunca ve un fichero a medias.
function Save-CheckpointFile([string]$path, $state) {
    $state['updatedAt'] = (Get-Date).ToUniversalTime().ToString('o')
    $state['checksum'] = Get-CheckpointChecksum $state
    $json = $state | ConvertTo-Json -Depth 12
    $temporary = "$path.tmp"
    $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes($json)
    $stream = New-Object IO.FileStream($temporary, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    # Replace conserva la generacion anterior como .prev (auditoria) y publica el nuevo contenido de una sola vez
    if (Test-Path -LiteralPath $path) { [IO.File]::Replace($temporary, $path, "$path.prev") } else { [IO.File]::Move($temporary, $path) }
}

# Lectura estricta. Un fichero ilegible, sin suma de verificacion valida o de otro esquema NO se reutiliza: se aparta (auditoria) y se empieza limpio.
# Un paso que quedo RUNNING es una interrupcion: pasa a INTERRUPTED y nunca cuenta como completado.
# Deserializador sin coercion de fechas ni numeros (JavaScriptSerializer de .NET Framework, disponible en Windows PowerShell 5.1)
function ConvertFrom-JsonPlain([string]$json) {
    Add-Type -AssemblyName System.Web.Extensions
    $serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
    $serializer.MaxJsonLength = [int]::MaxValue
    $serializer.RecursionLimit = 64
    return ConvertTo-PlainHashtable ($serializer.DeserializeObject($json))
}

function Read-CheckpointFile([string]$path, [switch]$NoQuarantine) {
    $result = @{ state = $null; quarantinedAs = $null; reason = $null; interrupted = @() }
    if (-not (Test-Path -LiteralPath $path)) { $result.state = New-CheckpointState; return $result }
    $reason = $null; $state = $null
    try {
        $state = ConvertFrom-JsonPlain (Get-Content -LiteralPath $path -Raw -Encoding UTF8)
        if ($state -isnot [hashtable] -or $state['schema'] -ne 2 -or -not $state.ContainsKey('steps') -or $state['steps'] -isnot [hashtable]) { $reason = 'esquema desconocido' }
        elseif (-not $state.ContainsKey('checksum') -or $state['checksum'] -ne (Get-CheckpointChecksum $state)) { $reason = 'la suma de verificacion no coincide (fichero alterado o incompleto)' }
    } catch { $reason = "no se puede leer: $($_.Exception.Message)" }
    if ($reason) {
        $result.state = New-CheckpointState; $result.reason = $reason
        if ($NoQuarantine) { $result.quarantinedAs = "(sin apartar: solo lectura) $path"; return $result }   # DryRun/Status nunca mueven ficheros
        $aside = "$path.corrupt-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfff'))"
        Move-Item -LiteralPath $path -Destination $aside -Force
        $result.quarantinedAs = $aside
        return $result
    }
    foreach ($name in @($state['steps'].Keys)) {
        if ($state['steps'][$name]['status'] -eq 'RUNNING') { $state['steps'][$name]['status'] = 'INTERRUPTED'; $result.interrupted += $name }
    }
    $result.state = $state
    return $result
}

# Campana = HEAD. Con un HEAD distinto se archiva la campana anterior entera (historial de auditoria) y se empieza otra sin mezclar resultados.
function Set-CheckpointCampaign($state, [string]$head, [string]$checkpointPath) {
    if ($state['campaign'] -and $state['campaign']['head'] -eq $head) { return $false }
    if ($state['campaign'] -and (Test-Path -LiteralPath $checkpointPath)) {
        $old = ([string]$state['campaign']['head']).Substring(0, [Math]::Min(8, ([string]$state['campaign']['head']).Length))
        Copy-Item -LiteralPath $checkpointPath -Destination "$checkpointPath.campaign-$old-$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmss'))" -Force
    }
    $state['steps'] = @{}
    $state['campaign'] = @{ head = $head; startedAt = (Get-Date).ToUniversalTime().ToString('o') }
    return $true
}

# Un paso se reutiliza solo si termino OK con exactamente los mismos componentes y en esta misma campana.
function Test-StepReusable($previous, $components, [string]$head) {
    if (-not $previous -or $previous['status'] -ne 'OK') { return $false }
    if ($previous['head'] -ne $head) { return $false }
    if (-not $previous.ContainsKey('components') -or $previous['components'] -isnot [hashtable]) { return $false }
    return (@(Compare-Components $previous['components'] $components).Count -eq 0)
}

function Add-CheckpointHistory([string]$path, [string]$step, [string]$status, $components, [string]$head) {
    $line = (@{ at = (Get-Date).ToUniversalTime().ToString('o'); step = $step; status = $status; head = $head; components = $components } | ConvertTo-Json -Depth 6 -Compress)
    Add-Content -LiteralPath "$path.history.jsonl" -Value $line -Encoding UTF8
}

# ------------------------------------------------------------------------------------------------------------------ data/ (segunda barrera)
# Instantanea de solo lectura: ruta, tipo, tamano, fechas, atributos y SHA-256 del contenido de cada fichero.
function Get-DataSnapshot([string]$root) {
    $data = Join-Path $root 'data'
    $snapshot = @{}
    if (-not (Test-Path -LiteralPath $data)) { return $snapshot }
    $base = (Get-Item -LiteralPath $data -Force).FullName
    foreach ($item in (Get-ChildItem -LiteralPath $data -Recurse -Force -ErrorAction SilentlyContinue)) {
        $relative = $item.FullName.Substring($base.Length + 1)
        if ($item.PSIsContainer) { $snapshot[$relative] = "D|$([int]$item.Attributes)|$($item.CreationTimeUtc.Ticks)" }
        else { $snapshot[$relative] = "F|$($item.Length)|$($item.LastWriteTimeUtc.Ticks)|$($item.CreationTimeUtc.Ticks)|$([int]$item.Attributes)|$(Get-FileSha256 $item.FullName)" }
    }
    return $snapshot
}

# Cambios entre dos instantaneas. Un directorio que solo cambia de fecha de modificacion no se cuenta (no la guardamos); si cambia su contenido se ve en los ficheros.
function Compare-DataSnapshot($before, $after) {
    $changes = New-Object System.Collections.Generic.List[string]
    foreach ($k in ($after.Keys | Sort-Object)) {
        if (-not $before.ContainsKey($k)) { $changes.Add("NUEVO $k") }
        elseif ($before[$k] -ne $after[$k]) {
            $b = $before[$k] -split '\|'; $a = $after[$k] -split '\|'
            if ($a[0] -eq 'F') {
                $kind = if ($b[5] -ne $a[5]) { 'CONTENIDO' } else { 'METADATOS' }
                $changes.Add("MODIFICADO($kind) $k")
            } else { $changes.Add("MODIFICADO(METADATOS) $k") }
        }
    }
    foreach ($k in ($before.Keys | Sort-Object)) { if (-not $after.ContainsKey($k)) { $changes.Add("ELIMINADO $k") } }
    return @($changes)
}

# ------------------------------------------------------------------------------------------------------------------ isolation
function Test-IsReparsePoint([string]$path) {
    try { return [bool]((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) } catch { return $false }
}

# Destino real de un enlace (junction o symlink); si no es un enlace, la propia ruta
function Resolve-LinkTarget([string]$path) {
    try {
        $item = Get-Item -LiteralPath $path -Force
        $target = @($item.Target)
        if ($target.Count -gt 0 -and $target[0]) { return [string]$target[0] }
    } catch { }
    return $path
}

function Get-RealPath([string]$path) {
    try { $p = (Resolve-Path -LiteralPath $path -ErrorAction Stop).ProviderPath } catch { $p = [IO.Path]::GetFullPath($path) }
    return $p.TrimEnd('\')
}

function Test-PathUnder([string]$child, [string]$parent) {
    $c = (Get-RealPath $child).ToLowerInvariant() + '\'
    $p = (Get-RealPath $parent).ToLowerInvariant() + '\'
    return $c.StartsWith($p)
}

# Problemas que impiden confiar en $sandbox para no tocar $root\data. Lista vacia = seguro.
function Get-IsolationProblems([string]$root, [string]$sandbox, [string[]]$allowedLinks = @('frontend\node_modules')) {
    $problems = @()
    if (-not (Test-Path -LiteralPath $sandbox)) { return @("el directorio de ejecucion no existe: $sandbox") }
    if ((Get-RealPath $sandbox) -eq (Get-RealPath $root)) { return @('el directorio de ejecucion es el espacio de trabajo real: ./data se resolveria contra el almacenamiento real') }
    if (Test-PathUnder $sandbox $root) { $problems += "el directorio de ejecucion esta dentro del espacio de trabajo real: $sandbox" }
    if (Test-PathUnder $root $sandbox) { $problems += "el directorio de ejecucion contiene el espacio de trabajo real: $sandbox" }
    foreach ($need in 'pom.xml', 'mvnw.cmd') { if (-not (Test-Path -LiteralPath (Join-Path $sandbox $need))) { $problems += "falta $need en el directorio de ejecucion (no es una copia del espacio de trabajo)" } }
    $realData = Join-Path $root 'data'
    $sandboxData = Join-Path $sandbox 'data'
    if (Test-Path -LiteralPath $sandboxData) {
        if (Test-IsReparsePoint $sandboxData) { $problems += 'data\ del directorio de ejecucion es un enlace' }
        if ((Test-Path -LiteralPath $realData) -and ((Test-PathUnder $sandboxData $realData) -or -not (Test-PathUnder $sandboxData $sandbox))) { $problems += 'data\ del directorio de ejecucion resuelve fuera de el o dentro del data\ real' }
    }
    $stack = New-Object System.Collections.Generic.Stack[string]
    $stack.Push($sandbox)
    while ($stack.Count) {
        $dir = $stack.Pop()
        foreach ($entry in (Get-ChildItem -LiteralPath $dir -Force -ErrorAction SilentlyContinue)) {
            $relative = $entry.FullName.Substring($sandbox.TrimEnd('\').Length + 1)
            if (Test-IsReparsePoint $entry.FullName) {
                if ($allowedLinks -notcontains $relative) { $problems += "enlace dentro del directorio de ejecucion: $relative" }
                elseif ((Test-Path -LiteralPath $realData) -and (Test-PathUnder (Resolve-LinkTarget $entry.FullName) $realData)) { $problems += "el enlace permitido $relative apunta al data\ real" }
                continue
            }
            if ($entry.PSIsContainer -and $entry.Name -ne '.git' -and $entry.Name -ne 'node_modules') { $stack.Push($entry.FullName) }
        }
    }
    return $problems
}

# Variables de entorno heredadas por todo proceso hijo que redirigirian el almacenamiento al data\ real
function Get-UnsafeEnvironmentProblems([string]$root, $environment = $null) {
    if ($null -eq $environment) { $environment = @{}; foreach ($e in (Get-ChildItem Env:)) { $environment[$e.Name] = $e.Value } }
    $realData = (Join-Path $root 'data')
    $needle = $realData.ToLowerInvariant().Replace('/', '\')
    $jvmVars = @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS', 'MAVEN_ARGS', 'SPRING_APPLICATION_JSON')
    $problems = @()
    foreach ($name in ($environment.Keys | Sort-Object)) {
        $value = [string]$environment[$name]
        if ($value.ToLowerInvariant().Replace('/', '\').Contains($needle)) { $problems += "$name apunta al data\ real"; continue }
        if ($jvmVars -contains $name -and $value -match 'user\.dir') { $problems += "$name redefine user.dir" }
        if ($name -match '^(HYDRA_|DSL_|SPRING_)' -and $value -match '[\\/:]' -and $value.Length -lt 400) {
            try { if ((Test-Path -LiteralPath $realData) -and (Test-PathUnder $value $realData)) { $problems += "$name resuelve al data\ real" } } catch { }
        }
    }
    return $problems
}

# ------------------------------------------------------------------------------------------------------------------ sandbox
# Copia fisica del espacio de trabajo dirigida por LISTA: exactamente los ficheros versionados + no versionados no ignorados (como un clon limpio), sin
# .git, target, build, data, node_modules ni dist. Copia solo lo que falta o difiere (tamano/fecha) y purga del sandbox lo que ya no esta en la lista.
# Solo escribe dentro del sandbox, que debe estar bajo %TEMP%; nunca modifica el espacio de trabajo.
function New-CertificationSandbox([string]$root, [string]$sandbox) {
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    $full = [IO.Path]::GetFullPath($sandbox).TrimEnd('\')
    if (-not $full.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "el sandbox debe estar bajo el directorio temporal ($tempRoot): $full" }
    if ($full -eq $root.TrimEnd('\') -or (Test-PathUnder $root $full) -or (Test-PathUnder $full $root)) { throw "el sandbox no puede coincidir con el espacio de trabajo ni anidarse con el: $full" }
    New-Item -ItemType Directory -Force -Path $full | Out-Null
    $list = Get-MirrorFileList $root
    $wanted = @{}
    foreach ($relative in $list) {
        $wanted[$relative.Replace('/', '\')] = 1
        $source = Get-Item -LiteralPath (Join-Path $root $relative) -Force
        $destination = Join-Path $full $relative
        $existing = if (Test-Path -LiteralPath $destination -PathType Leaf) { Get-Item -LiteralPath $destination -Force } else { $null }
        if ($null -eq $existing -or $existing.Length -ne $source.Length -or $existing.LastWriteTimeUtc.Ticks -ne $source.LastWriteTimeUtc.Ticks) {
            New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
            Copy-Item -LiteralPath $source.FullName -Destination $destination -Force
        }
    }
    # purga: ficheros del sandbox que ya no estan en la lista (sin entrar en rutas excluidas ni en enlaces)
    $stack = New-Object System.Collections.Generic.Stack[string]
    $stack.Push($full)
    while ($stack.Count) {
        $dir = $stack.Pop()
        foreach ($entry in (Get-ChildItem -LiteralPath $dir -Force -ErrorAction SilentlyContinue)) {
            $relative = $entry.FullName.Substring($full.Length + 1)
            if (Test-SandboxExcluded $relative) { continue }
            if (Test-IsReparsePoint $entry.FullName) { continue }
            if ($entry.PSIsContainer) { $stack.Push($entry.FullName) }
            elseif (-not $wanted.ContainsKey($relative)) { Remove-Item -LiteralPath $entry.FullName -Force }
        }
    }
    # dependencia de solo lectura compartida (excepcion declarada): enlace a node_modules; nunca a data\
    $store = Join-Path $root 'frontend\node_modules'
    $link = Join-Path $full 'frontend\node_modules'
    if ((Test-Path -LiteralPath $store) -and -not (Test-Path -LiteralPath $link)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $link) | Out-Null
        & cmd /c mklink /J "$link" "$store" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'no se pudo crear el enlace a frontend\node_modules' }
    }
    return $full
}
# Compara el sandbox con el espacio de trabajo: mismos ficheros y mismo contenido (y ninguno de mas). Devuelve las diferencias.
function Compare-SandboxWithWorkspace([string]$root, [string]$sandbox) {
    $source = Get-MirrorFileList $root
    $differences = @()
    foreach ($relative in $source) {
        $mirror = Join-Path $sandbox $relative
        if (-not (Test-Path -LiteralPath $mirror -PathType Leaf)) { $differences += "FALTA $relative"; continue }
        if ((Get-FileSha256 (Join-Path $root $relative)) -ne (Get-FileSha256 $mirror)) { $differences += "DISTINTO $relative" }
    }
    $sourceSet = @{}; foreach ($s in $source) { $sourceSet[$s.Replace('/', '\')] = 1 }
    # recorrido manual: no se entra en las rutas excluidas ni en enlaces (frontend\node_modules es un enlace con cientos de miles de ficheros)
    $stack = New-Object System.Collections.Generic.Stack[string]
    $stack.Push($sandbox.TrimEnd('\'))
    while ($stack.Count) {
        $dir = $stack.Pop()
        foreach ($entry in (Get-ChildItem -LiteralPath $dir -Force -ErrorAction SilentlyContinue)) {
            $relative = $entry.FullName.Substring($sandbox.TrimEnd('\').Length + 1)
            if (Test-SandboxExcluded $relative) { continue }
            if (Test-IsReparsePoint $entry.FullName) { $differences += "ENLACE $relative"; continue }
            if ($entry.PSIsContainer) { $stack.Push($entry.FullName) }
            elseif (-not $sourceSet.ContainsKey($relative)) { $differences += "SOBRA $relative" }
        }
    }
    return $differences
}
