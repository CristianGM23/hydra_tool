<#
  Pruebas del modulo de apoyo del lanzador de certificacion (huellas, puntos de control, data\ real, aislamiento, sandbox).
  Windows PowerShell 5.1, sin Pester ni dependencias. Trabaja SOLO en directorios temporales propios y no usa git add/commit/push/reset/clean/stash.
  Uso: powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts\archify\v3\tests\Test-HydraCertificationSupport.ps1
  Codigo de salida: 0 si todo pasa, 1 si algo falla.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\HydraCertificationSupport.ps1')

$script:results = New-Object System.Collections.Generic.List[object]
$work = Join-Path ([IO.Path]::GetTempPath()) ("hydra-support-tests-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $work | Out-Null

function Test-Case([string]$name, [scriptblock]$body) {
    try { & $body; $script:results.Add([pscustomobject]@{ name = $name; ok = $true; detail = '' }); Write-Host "  PASS  $name" -ForegroundColor Green }
    catch { $script:results.Add([pscustomobject]@{ name = $name; ok = $false; detail = $_.Exception.Message }); Write-Host "  FAIL  $name -> $($_.Exception.Message)" -ForegroundColor Red }
}
function Assert-That([bool]$condition, [string]$message) { if (-not $condition) { throw $message } }
function Assert-Equal($expected, $actual, [string]$message) { if ($expected -ne $actual) { throw "$message (esperado '$expected', obtenido '$actual')" } }
function New-Dir([string]$path) { New-Item -ItemType Directory -Force -Path $path | Out-Null; return $path }
function Write-Text([string]$path, [string]$text) { New-Dir (Split-Path $path) | Out-Null; [IO.File]::WriteAllText($path, $text, (New-Object Text.UTF8Encoding($false))) }

# Raiz falsa con los ficheros que el modulo lee para construir los componentes de cada paso
function New-FakeRoot([string]$name) {
    $r = New-Dir (Join-Path $work $name)
    foreach ($rel in 'scripts/archify/v3/Invoke-HydraConsolidatedCertification.ps1', 'scripts/archify/v3/HydraCertificationSupport.ps1', 'scripts/archify/v3/hydra_archify_v3.py', 'docs/architecture/archify/knowledge/tests/v3/MANIFEST_CERTIFICACION_V3.json') { Write-Text (Join-Path $r $rel) "contenido de $rel v1" }
    Write-Text (Join-Path $r 'README.md') 'documentacion'
    Write-Text (Join-Path $r 'pom.xml') '<project/>'
    Write-Text (Join-Path $r 'mvnw.cmd') '@echo off'
    return $r
}
function New-Context($root, [hashtable]$tasks, [bool]$force = $false, [string]$head = 'aaaa1111') {
    return @{ root = $root; head = $head; params = @{ ForceAll = $force; StrictFingerprint = $false; SkipE2E = $false }; taskFingerprints = $tasks
        mirrorTreeHash = 'tree1'; stateHash = 'state1'; syncOutputsHash = 'sync1'; fragmentsHash = 'frag1'; toolsHash = 'tools1' }
}
function Get-StepFp([string]$step, $context) { return Get-ComponentFingerprint (Get-StepComponents $step $context) }
$tasks = @{ 'maven.a' = @{ kind = 'maven'; v1 = 'h-a' }; 'maven.b' = @{ kind = 'maven'; v1 = 'h-b' }; 'pw.a' = @{ kind = 'playwright'; v1 = 'h-pw' } }

Write-Host "PowerShell $($PSVersionTable.PSVersion) (se espera 5.1)"
Write-Host 'Huellas por contenido y por paso'

Test-Case '1. un fichero que sigue "modificado" para Git pero cambia de contenido cambia la huella' {
    $r = New-Dir (Join-Path $work 'tree1')
    Write-Text (Join-Path $r 'src/A.java') 'version 1'
    $h1 = Get-FilesContentHash $r @('src/A.java')
    Write-Text (Join-Path $r 'src/A.java') 'version 2 (mismo estado M para git)'
    $h2 = Get-FilesContentHash $r @('src/A.java')
    Assert-That ($h1 -ne $h2) 'el hash no detecto el cambio de contenido'
    Assert-Equal $h2 (Get-FilesContentHash $r @('src/A.java')) 'el hash no es determinista'
}
Test-Case '2a. un cambio real en una dependencia de las tareas Maven invalida solo run-core' {
    $r = New-FakeRoot 'f2'
    $base = New-Context $r $tasks
    $changed = New-Context $r @{ 'maven.a' = @{ kind = 'maven'; v1 = 'h-a-CAMBIADO' }; 'maven.b' = $tasks['maven.b']; 'pw.a' = $tasks['pw.a'] }
    Assert-That ((Get-StepFp 'run-core' $base) -ne (Get-StepFp 'run-core' $changed)) 'run-core no se invalida'
    Assert-Equal (Get-StepFp 'run-e2e' $base) (Get-StepFp 'run-e2e' $changed) 'run-e2e no debia invalidarse por un cambio Maven'
}
Test-Case '2b. un cambio en una tarea Playwright invalida run-e2e y no run-core' {
    $r = New-FakeRoot 'f2b'
    $base = New-Context $r $tasks
    $changed = New-Context $r @{ 'maven.a' = $tasks['maven.a']; 'maven.b' = $tasks['maven.b']; 'pw.a' = @{ kind = 'playwright'; v1 = 'h-pw-NUEVO' } }
    Assert-That ((Get-StepFp 'run-e2e' $base) -ne (Get-StepFp 'run-e2e' $changed)) 'run-e2e no se invalida'
    Assert-Equal (Get-StepFp 'run-core' $base) (Get-StepFp 'run-core' $changed) 'run-core no debia invalidarse por un cambio Playwright'
}
Test-Case '3. una configuracion compartida (manifiesto) invalida todos los pasos que dependen de ella' {
    $r = New-FakeRoot 'f3'
    $ctx = New-Context $r $tasks
    $before = @{}; foreach ($s in 'run-core', 'run-e2e', 'sync', 'verify', 'package', 'isolate') { $before[$s] = Get-StepFp $s $ctx }
    Write-Text (Join-Path $r 'docs/architecture/archify/knowledge/tests/v3/MANIFEST_CERTIFICACION_V3.json') 'manifiesto v2'
    foreach ($s in $before.Keys) { Assert-That ($before[$s] -ne (Get-StepFp $s (New-Context $r $tasks))) "el paso $s no se invalido" }
}
Test-Case '3b. un cambio en el lanzador o en el ejecutor invalida los resultados que dependen de ese motor' {
    $r = New-FakeRoot 'f3b'
    $ctx = New-Context $r $tasks
    $b = Get-StepFp 'run-core' $ctx
    Write-Text (Join-Path $r 'scripts/archify/v3/hydra_archify_v3.py') 'ejecutor v2'
    Assert-That ($b -ne (Get-StepFp 'run-core' (New-Context $r $tasks))) 'un cambio del ejecutor no invalido run-core'
    $b2 = Get-StepFp 'sync' $ctx
    Write-Text (Join-Path $r 'scripts/archify/v3/Invoke-HydraConsolidatedCertification.ps1') 'lanzador v2'
    Assert-That ($b2 -ne (Get-StepFp 'sync' (New-Context $r $tasks))) 'un cambio del lanzador no invalido sync'
}
Test-Case '4. un cambio en un fichero ajeno a las tareas no invalida nada' {
    $r = New-FakeRoot 'f4'
    $ctx = New-Context $r $tasks
    $before = @{}; foreach ($s in 'preflight', 'selftest', 'run-core', 'run-e2e', 'sync', 'verify', 'package') { $before[$s] = Get-StepFp $s $ctx }
    Write-Text (Join-Path $r 'README.md') 'documentacion editada, sin relacion con ninguna tarea'
    foreach ($s in $before.Keys) { Assert-Equal $before[$s] (Get-StepFp $s (New-Context $r $tasks)) "el paso $s se invalido sin motivo" }
}
Test-Case '9. los parametros que cambian el comportamiento cambian la huella; los que no, no' {
    $r = New-FakeRoot 'f9'
    $a = Get-StepFp 'run-core' (New-Context $r $tasks $false)
    $b = Get-StepFp 'run-core' (New-Context $r $tasks $true)
    Assert-That ($a -ne $b) '-ForceAll no cambia la huella de run-core'
    Assert-Equal (Get-StepFp 'sync' (New-Context $r $tasks $false)) (Get-StepFp 'sync' (New-Context $r $tasks $true)) '-ForceAll no debia cambiar sync'
}
Test-Case '8. un HEAD distinto invalida los pasos ligados a un commit' {
    $r = New-FakeRoot 'f8'
    $a = Get-StepFp 'package' (New-Context $r $tasks $false 'aaaa1111')
    $b = Get-StepFp 'package' (New-Context $r $tasks $false 'bbbb2222')
    Assert-That ($a -ne $b) 'package no se invalida con otro HEAD'
}

Write-Host 'Puntos de control'
$cp = New-Dir (Join-Path $work 'cp')
function New-OkState([string]$head, $components) {
    $s = New-CheckpointState
    $s['campaign'] = @{ head = $head; startedAt = (Get-Date).ToUniversalTime().ToString('o') }
    $s['steps']['run-core'] = @{ status = 'OK'; head = $head; components = @{}; fingerprint = 'x' }
    foreach ($k in $components.Keys) { $s['steps']['run-core']['components'][$k] = $components[$k] }
    return $s
}
Test-Case '5. reanudar sin cambios reutiliza el paso (ida y vuelta por disco con suma de verificacion)' {
    $r = New-FakeRoot 'c5'; $comp = Get-StepComponents 'run-core' (New-Context $r $tasks)
    $file = Join-Path $cp 'c5.json'
    Save-CheckpointFile $file (New-OkState 'aaaa1111' $comp)
    $read = Read-CheckpointFile $file
    Assert-That ($null -eq $read.quarantinedAs) 'un punto de control valido se aparto'
    Assert-That (Test-StepReusable $read.state['steps']['run-core'] $comp 'aaaa1111') 'no se reutilizo un paso identico'
}
Test-Case '5b. cualquier componente distinto impide reutilizar el paso y se indica cual' {
    $r = New-FakeRoot 'c5b'; $comp = Get-StepComponents 'run-core' (New-Context $r $tasks)
    $file = Join-Path $cp 'c5b.json'
    Save-CheckpointFile $file (New-OkState 'aaaa1111' $comp)
    $prev = (Read-CheckpointFile $file).state['steps']['run-core']
    $tasks2 = @{ 'maven.a' = @{ kind = 'maven'; v1 = 'otro' }; 'maven.b' = $tasks['maven.b']; 'pw.a' = $tasks['pw.a'] }
    $comp2 = Get-StepComponents 'run-core' (New-Context $r $tasks2)
    Assert-That (-not (Test-StepReusable $prev $comp2 'aaaa1111')) 'se reutilizo un paso con entradas distintas'
    Assert-Equal 'tasks' ((Compare-Components $prev['components'] $comp2) -join ',') 'no identifico el componente que cambio'
    Assert-That (-not (Test-StepReusable $prev $comp 'bbbb2222')) 'se reutilizo un paso de otra campana (otro HEAD)'
}
Test-Case '6. un paso que quedo RUNNING tras una interrupcion pasa a INTERRUPTED y no se reutiliza' {
    $r = New-FakeRoot 'c6'; $comp = Get-StepComponents 'run-core' (New-Context $r $tasks)
    $s = New-OkState 'aaaa1111' $comp; $s['steps']['run-core']['status'] = 'RUNNING'
    $file = Join-Path $cp 'c6.json'; Save-CheckpointFile $file $s
    $read = Read-CheckpointFile $file
    Assert-Equal 'INTERRUPTED' $read.state['steps']['run-core']['status'] 'no marco la interrupcion'
    Assert-That (-not (Test-StepReusable $read.state['steps']['run-core'] $comp 'aaaa1111')) 'un paso interrumpido se considero completado'
}
Test-Case '7a. un punto de control truncado (escritura incompleta) se aparta y no se convierte en PASS' {
    $file = Join-Path $cp 'c7a.json'
    Write-Text $file '{ "schema": 2, "steps": { "run-core": { "status": "OK", "head": "aaaa'
    $read = Read-CheckpointFile $file
    Assert-That ($null -ne $read.quarantinedAs) 'no aparto el fichero truncado'
    Assert-That (-not (Test-Path -LiteralPath $file)) 'el fichero corrupto sigue en su sitio'
    Assert-That (Test-Path -LiteralPath $read.quarantinedAs) 'el fichero apartado no se conservo para auditoria'
    Assert-Equal 0 $read.state['steps'].Count 'el estado nuevo no esta vacio'
}
Test-Case '7b. un punto de control con la suma alterada (editado a mano) se aparta' {
    $r = New-FakeRoot 'c7b'; $comp = Get-StepComponents 'run-core' (New-Context $r $tasks)
    $s = New-OkState 'aaaa1111' $comp; $s['steps']['run-core']['status'] = 'FAILED'
    $file = Join-Path $cp 'c7b.json'; Save-CheckpointFile $file $s
    $text = [IO.File]::ReadAllText($file).Replace('"FAILED"', '"OK"')
    [IO.File]::WriteAllText($file, $text)
    $read = Read-CheckpointFile $file
    Assert-That ($null -ne $read.quarantinedAs) 'un FAILED convertido a OK a mano no se detecto'
    Assert-Equal 0 $read.state['steps'].Count 'el estado nuevo no esta vacio'
}
Test-Case '7c. la escritura es atomica: no queda temporal y el fichero anterior se conserva si falla el reemplazo' {
    $file = Join-Path $cp 'c7c.json'
    $s = New-CheckpointState; Save-CheckpointFile $file $s
    $first = [IO.File]::ReadAllText($file)
    $s['steps']['x'] = @{ status = 'OK' }; Save-CheckpointFile $file $s
    Assert-That (-not (Test-Path -LiteralPath "$file.tmp")) 'quedo un temporal'
    Assert-That ([IO.File]::ReadAllText($file) -ne $first) 'no se actualizo'
    Assert-Equal $first ([IO.File]::ReadAllText("$file.prev")) 'la generacion anterior no se conservo como .prev'
    Assert-That ($null -eq (Read-CheckpointFile $file).quarantinedAs) 'el fichero reescrito no valida'
}
Test-Case '8b. un HEAD distinto abre una campana nueva, archiva la anterior y no mezcla resultados' {
    $r = New-FakeRoot 'c8'; $comp = Get-StepComponents 'run-core' (New-Context $r $tasks)
    $file = Join-Path $cp 'c8.json'; Save-CheckpointFile $file (New-OkState 'aaaa1111bbbb' $comp)
    $state = (Read-CheckpointFile $file).state
    Assert-That (-not (Set-CheckpointCampaign $state 'aaaa1111bbbb' $file)) 'con el mismo HEAD no debia abrir campana nueva'
    Assert-That (Set-CheckpointCampaign $state 'cccc3333dddd' $file) 'con otro HEAD debia abrir campana nueva'
    Assert-Equal 0 $state['steps'].Count 'la campana nueva heredo resultados'
    Assert-Equal 'cccc3333dddd' $state['campaign']['head'] 'no cambio el HEAD de la campana'
    Assert-That (@(Get-ChildItem $cp -Filter 'c8.json.campaign-aaaa1111-*').Count -eq 1) 'no archivo la campana anterior'
}
Test-Case '8c. el historial conserva cada transicion de paso' {
    $file = Join-Path $cp 'c8h.json'
    Add-CheckpointHistory $file 'run-core' 'RUNNING' @{ a = 'x' } 'aaaa1111'
    Add-CheckpointHistory $file 'run-core' 'OK' @{ a = 'x' } 'aaaa1111'
    Assert-Equal 2 @(Get-Content "$file.history.jsonl").Count 'faltan entradas de historial'
}
Test-Case 'la suma de verificacion no depende del orden de enumeracion de las claves' {
    $a = @{ b = 1; a = @{ y = 2; x = 3 }; c = @(1, 2) }
    $b = [ordered]@{ c = @(1, 2); a = [ordered]@{ x = 3; y = 2 }; b = 1 }
    Assert-Equal (ConvertTo-CanonicalJson $a) (ConvertTo-CanonicalJson $b) 'el JSON canonico difiere'
}

Write-Host 'data\ real: segunda barrera'
Test-Case '10. la instantanea detecta altas, bajas, cambios de contenido y cambios de solo metadatos' {
    $r = New-Dir (Join-Path $work 'd1')
    Write-Text (Join-Path $r 'data/metamodels/a.json') 'contenido'
    Write-Text (Join-Path $r 'data/projects/registry.json') '{}'
    $before = Get-DataSnapshot $r
    Assert-Equal 0 @(Compare-DataSnapshot $before (Get-DataSnapshot $r)).Count 'sin tocar nada hay cambios'
    Write-Text (Join-Path $r 'data/metamodels/new.json') 'x'
    Assert-That (@(Compare-DataSnapshot $before (Get-DataSnapshot $r)) -contains 'NUEVO metamodels\new.json') 'no detecto el alta'
    Remove-Item (Join-Path $r 'data/metamodels/new.json')
    Write-Text (Join-Path $r 'data/metamodels/a.json') 'contenido2'
    Assert-That (@(Compare-DataSnapshot $before (Get-DataSnapshot $r)) -contains 'MODIFICADO(CONTENIDO) metamodels\a.json') 'no detecto el cambio de contenido'
    Write-Text (Join-Path $r 'data/metamodels/a.json') 'contenido'
    (Get-Item (Join-Path $r 'data/metamodels/a.json')).LastWriteTimeUtc = [datetime]'2020-01-01'
    Assert-That (@(Compare-DataSnapshot $before (Get-DataSnapshot $r)) -contains 'MODIFICADO(METADATOS) metamodels\a.json') 'no detecto el cambio de solo fecha'
    (Get-Item (Join-Path $r 'data/metamodels/a.json')).LastWriteTimeUtc = (Get-Item (Join-Path $r 'data/projects/registry.json')).LastWriteTimeUtc
    $b2 = Get-DataSnapshot $r
    Remove-Item (Join-Path $r 'data/projects/registry.json')
    Assert-That (@(Compare-DataSnapshot $b2 (Get-DataSnapshot $r)) -contains 'ELIMINADO projects\registry.json') 'no detecto la baja'
}
Test-Case '10b. un data\ inexistente da una instantanea vacia (no es un error)' {
    Assert-Equal 0 (Get-DataSnapshot (New-Dir (Join-Path $work 'd2'))).Count 'no esta vacia'
}

Write-Host 'Aislamiento'
function New-RealAndBox([string]$name) {
    $base = New-Dir (Join-Path $work $name)
    $real = New-Dir (Join-Path $base 'workspace'); Write-Text (Join-Path $real 'data/audit-log/d.jsonl') '{}'; Write-Text (Join-Path $real 'pom.xml') '<project/>'; Write-Text (Join-Path $real 'mvnw.cmd') 'x'
    $box = New-Dir (Join-Path $base 'box'); Write-Text (Join-Path $box 'pom.xml') '<project/>'; Write-Text (Join-Path $box 'mvnw.cmd') 'x'; Write-Text (Join-Path $box 'frontend/src/a.ts') 'x'
    return @{ base = $base; real = $real; box = $box }
}
function New-Junction([string]$link, [string]$target) { New-Dir (Split-Path $link) | Out-Null; & cmd /c mklink /J "$link" "$target" | Out-Null; if ($LASTEXITCODE -ne 0) { throw 'no se pudo crear el enlace de la prueba' } }
Test-Case '11a. una copia independiente se acepta' {
    $x = New-RealAndBox 'i1'; Assert-Equal 0 @(Get-IsolationProblems $x.real $x.box).Count "problemas: $(Get-IsolationProblems $x.real $x.box)"
}
Test-Case '11b. el espacio de trabajo real, un directorio anidado o uno que lo contiene se rechazan' {
    $x = New-RealAndBox 'i2'
    Assert-That (@(Get-IsolationProblems $x.real $x.real).Count -ge 1) 'se acepto el propio espacio real'
    $nested = New-Dir (Join-Path $x.real 'target/sandbox'); Write-Text (Join-Path $nested 'pom.xml') 'x'; Write-Text (Join-Path $nested 'mvnw.cmd') 'x'
    Assert-That (@(Get-IsolationProblems $x.real $nested) -match 'dentro del espacio').Count -ge 1 'se acepto un sandbox anidado'
    Assert-That (@(Get-IsolationProblems $x.real $x.base) -match 'contiene').Count -ge 1 'se acepto un directorio que contiene el espacio real'
}
Test-Case '11c. un directorio que no es copia del espacio (sin pom.xml) se rechaza' {
    $x = New-RealAndBox 'i3'; Remove-Item (Join-Path $x.box 'pom.xml'); Assert-That (@(Get-IsolationProblems $x.real $x.box) -match 'pom.xml').Count -ge 1 'se acepto'
}
Test-Case '11d. data\ enlazado al data\ real (junction) se rechaza' {
    $x = New-RealAndBox 'i4'; New-Junction (Join-Path $x.box 'data') (Join-Path $x.real 'data')
    Assert-That (@(Get-IsolationProblems $x.real $x.box)).Count -ge 1 'se acepto un data\ enlazado'
}
Test-Case '11e. cualquier otro enlace se rechaza; el de frontend\node_modules es la unica excepcion y nunca puede apuntar a data\' {
    $x = New-RealAndBox 'i5'; $store = New-Dir (Join-Path $x.base 'store')
    New-Junction (Join-Path $x.box 'frontend/node_modules') $store
    Assert-Equal 0 @(Get-IsolationProblems $x.real $x.box).Count 'la excepcion declarada no se acepto'
    New-Junction (Join-Path $x.box 'src/sneaky') (Join-Path $x.real 'data')
    Assert-That (@(Get-IsolationProblems $x.real $x.box) -match 'sneaky').Count -ge 1 'se acepto un enlace oculto'
    $y = New-RealAndBox 'i5b'; New-Junction (Join-Path $y.box 'frontend/node_modules') (Join-Path $y.real 'data')
    Assert-That (@(Get-IsolationProblems $y.real $y.box) -match 'apunta al data').Count -ge 1 'node_modules apuntando a data\ se acepto'
}
Test-Case '11f. una variable de entorno que redirige a data\ real se rechaza (tambien opciones de JVM con user.dir)' {
    $x = New-RealAndBox 'i6'
    Assert-Equal 0 @(Get-UnsafeEnvironmentProblems $x.real @{ PATH = 'C:\Windows'; HYDRA_OK = (Join-Path $x.box 'data') }).Count 'falso positivo'
    Assert-That (@(Get-UnsafeEnvironmentProblems $x.real @{ HYDRA_TRANSFORMATION_SYNC_SESSION_DIRECTORY = (Join-Path $x.real 'data\sessions') }) -match 'HYDRA_TRANSFORMATION').Count -ge 1 'no detecto la ruta directa'
    Assert-That (@(Get-UnsafeEnvironmentProblems $x.real @{ DSL_STORAGE_METAMODELS = ((Join-Path $x.real 'data') + '\..\data\metamodels') }) -match 'DSL_STORAGE').Count -ge 1 'no detecto la ruta con ..'
    Assert-That (@(Get-UnsafeEnvironmentProblems $x.real @{ MAVEN_OPTS = '-Xmx1g -Duser.dir=C:\algo' }) -match 'user.dir').Count -ge 1 'no detecto user.dir'
}
Test-Case '12. el sandbox se crea como copia exacta, sin data\ ni .git, y se verifica byte a byte' {
    $x = New-RealAndBox 'i7'
    Write-Text (Join-Path $x.real 'src/main/A.java') 'class A {}'
    Write-Text (Join-Path $x.real 'target/clase.class') 'no se copia'
    $sandbox = Join-Path ([IO.Path]::GetTempPath()) ("hydra-support-sandbox-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
    try {
        $made = New-CertificationSandbox $x.real $sandbox
        Assert-That (Test-Path (Join-Path $made 'src/main/A.java')) 'no copio un fichero fuente'
        Assert-That (-not (Test-Path (Join-Path $made 'data'))) 'copio el data\ real'
        Assert-That (-not (Test-Path (Join-Path $made 'target'))) 'copio target\'
        Assert-Equal 0 @(Compare-SandboxWithWorkspace $x.real $made).Count 'la copia no es identica'
        Assert-Equal 0 @(Get-IsolationProblems $x.real $made).Count 'el sandbox recien creado no pasa las comprobaciones'
        Write-Text (Join-Path $x.real 'src/main/A.java') 'class A { int cambiado; }'
        Assert-That (@(Compare-SandboxWithWorkspace $x.real $made | Where-Object { $_ -like 'DISTINTO*A.java' }).Count -eq 1) 'no detecto que el espacio de trabajo cambio despues de copiar'
        Write-Text (Join-Path $made 'src/main/Extra.java') 'sobra'
        Assert-That (@(Compare-SandboxWithWorkspace $x.real $made | Where-Object { $_ -like 'SOBRA*Extra.java' }).Count -eq 1) 'no detecto un fichero de mas en el sandbox'
    } finally { if (Test-Path $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue } }
}
Test-Case '12b. el sandbox solo puede crearse bajo %TEMP% y nunca anidado con el espacio real' {
    $x = New-RealAndBox 'i8'
    $threw = $false; try { New-CertificationSandbox $x.real (Join-Path $x.real 'target\sandbox') | Out-Null } catch { $threw = $true }
    Assert-That $threw 'creo un sandbox dentro del espacio real'
    $threw = $false; try { New-CertificationSandbox $x.real 'C:\hydra-fuera-de-temp' | Out-Null } catch { $threw = $true }
    Assert-That $threw 'creo un sandbox fuera de %TEMP%'
    Assert-That (-not (Test-Path 'C:\hydra-fuera-de-temp')) 'dejo un directorio fuera de %TEMP%'
}

Write-Host 'Casos hallados al probar el lanzador de verdad'
Test-Case '13. la suma de verificacion es estable aunque haya listas vacias (ConvertTo-Json las escribe como null)' {
    $r = New-FakeRoot 'c13'; $comp = Get-StepComponents 'run-core' (New-Context $r $tasks)
    $s = New-OkState 'aaaa1111' $comp
    $s['dataChecks'] = @(@{ at = (Get-Date).ToUniversalTime().ToString('o'); label = 'isolate'; changes = @(); changeCount = 0 }, @{ at = 'x'; label = 'n'; changes = @('NUEVO a'); changeCount = 1 })
    $file = Join-Path $cp 'c13.json'; Save-CheckpointFile $file $s
    $read = Read-CheckpointFile $file
    Assert-That ($null -eq $read.quarantinedAs) "un punto de control valido se aparto: $($read.reason)"
    Save-CheckpointFile $file $read.state
    Assert-That ($null -eq (Read-CheckpointFile $file).quarantinedAs) 'no es estable tras guardar de nuevo lo leido'
}
Test-Case '14. las fechas con formato ISO se leen como texto (sin coercion) y la suma sigue valiendo' {
    $file = Join-Path $cp 'c14.json'
    $s = New-CheckpointState; $s['steps']['x'] = @{ status = 'OK'; startedAt = '2026-10-23T08:39:52.7613515Z' }
    Save-CheckpointFile $file $s
    $read = Read-CheckpointFile $file
    Assert-That ($read.state['steps']['x']['startedAt'] -is [string]) 'la fecha se convirtio en otro tipo'
    Assert-That ($null -eq $read.quarantinedAs) 'se aparto un fichero valido'
}
Test-Case '15. leer en modo solo lectura (-NoQuarantine) nunca mueve ficheros, ni siquiera uno corrupto' {
    $file = Join-Path $cp 'c15.json'; Write-Text $file '{ roto'
    $read = Read-CheckpointFile $file -NoQuarantine
    Assert-That (Test-Path -LiteralPath $file) 'movio el fichero'
    Assert-That ($null -ne $read.reason) 'no informo del problema'
    Assert-Equal 0 $read.state['steps'].Count 'el estado nuevo no esta vacio'
}
Test-Case '16. el reparto en bloques conserva orden y numero y no falla con 0 o 1 elementos' {
    Assert-Equal 0 @(Split-IntoChunks @() 8).Count 'con 0 ids debe dar 0 bloques'
    $one = @(Split-IntoChunks @('a') 8); Assert-Equal 1 $one.Count 'con 1 id debe dar 1 bloque'; Assert-Equal 'a' $one[0] 'contenido'
    $ids = 1..31 | ForEach-Object { "t$_" }
    $chunks = @(Split-IntoChunks $ids 8)
    Assert-Equal 4 $chunks.Count 'se esperaban 4 bloques (8+8+8+7)'
    Assert-Equal 7 @($chunks[3] -split ',').Count 'el ultimo bloque debia tener 7'
    Assert-Equal ($ids -join ',') ($chunks -join ',') 'se perdio o reordeno algun id'
    Assert-Equal 1 @(Split-IntoChunks $ids 31).Count 'un solo bloque para todo'
}
Test-Case '17. una lista de un solo elemento sobrevive a la lectura (no se convierte en texto)' {
    $back = ConvertFrom-JsonPlain (@{ items = @('solo') } | ConvertTo-Json)
    Assert-That ($back['items'] -is [array]) 'perdio su condicion de lista'
}
Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
$failed = @($script:results | Where-Object { -not $_.ok })
Write-Host ''
Write-Host "Resultado: $($script:results.Count - $failed.Count) PASS, $($failed.Count) FAIL (PowerShell $($PSVersionTable.PSVersion))"
if ($failed.Count) { exit 1 } else { exit 0 }
