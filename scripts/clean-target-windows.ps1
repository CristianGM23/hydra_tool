[CmdletBinding()]
param(
    [switch]$StopProjectProcesses,
    [switch]$IncludeIdeOutput,
    [int]$Retries = 8,
    [int]$DelayMilliseconds = 500
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cliBuildPath = Join-Path $projectRoot 'build\hydra-maven'
$legacyTargetPath = Join-Path $projectRoot 'target'

function Get-HydraJavaProcesses {
    $rootNeedle = $projectRoot.TrimEnd('\\')
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'javaw.exe') -and
        -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
        $_.CommandLine.IndexOf($rootNeedle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
    }
}

function Remove-HydraTree([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host ("No existe '{0}'. Nada que limpiar." -f $path) -ForegroundColor DarkGray
        return
    }
    for ($attempt = 1; $attempt -le [Math]::Max(1, $Retries); $attempt++) {
        try {
            Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop
            Write-Host ("Eliminado correctamente: {0}" -f $path) -ForegroundColor Green
            return
        } catch {
            if ($attempt -ge $Retries) {
                throw ("No se pudo eliminar '{0}' tras {1} intento(s). Cierra procesos que mantengan ficheros abiertos y vuelve a intentarlo. Último error: {2}" -f $path, $attempt, $_.Exception.Message)
            }
            Start-Sleep -Milliseconds $DelayMilliseconds
        }
    }
}

$projectProcesses = @(Get-HydraJavaProcesses)
if ($projectProcesses.Count -gt 0) {
    Write-Host 'Se han encontrado procesos Java de HYDRA abiertos:' -ForegroundColor Yellow
    $projectProcesses | ForEach-Object { Write-Host ("  PID {0}: {1}" -f $_.ProcessId, $_.CommandLine) }
    if ($StopProjectProcesses) {
        foreach ($process in $projectProcesses) {
            Write-Host ("Deteniendo PID {0}..." -f $process.ProcessId)
            Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
        }
    } else {
        Write-Host 'No se detienen automáticamente. La salida Maven CLI está aislada y puede limpiarse sin tocar target/classes del IDE.' -ForegroundColor Yellow
    }
}

# Maven CLI compila en build/hydra-maven mediante .mvn/maven.config. Limpiar esta ruta
# no interfiere con Eclipse JDT/VS Code, que puede mantener su propio target/classes.
Remove-HydraTree $cliBuildPath

if ($IncludeIdeOutput) {
    Write-Host 'Se solicitó también limpiar el output legado/IDE target/.' -ForegroundColor Yellow
    Remove-HydraTree $legacyTargetPath
}
