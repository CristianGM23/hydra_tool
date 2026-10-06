@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo ============================================================
echo HYDRA V4.32.107 - REPOSITORIOS EXTERNOS REALES (OPT-IN)
echo ============================================================
if /I not "%HYDRA_LIVE_REMOTE_TESTS%"=="true" (
  echo ERROR: define HYDRA_LIVE_REMOTE_TESTS=true para confirmar que quieres escribir artefactos efimeros de prueba en repositorios externos.
  exit /b 2
)
where java >nul 2>nul || (echo ERROR: Java no esta disponible en PATH. & exit /b 2)
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~V"
set "JAVA_VERSION=%JAVA_VERSION:\"=%"
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if not "%JAVA_MAJOR%"=="24" (echo ERROR: se requiere JDK 24. Java detectado: %JAVA_VERSION% & exit /b 2)
echo Ejecutando round-trips live. Los proveedores sin variables HYDRA_TEST_* completas se marcaran SKIPPED.
call mvnw.cmd -B -Dtest=LiveRemoteRepositoryRoundTripTests test || exit /b 1
echo ============================================================
echo REPOSITORIOS EXTERNOS REALES: PASS
 echo Revisa los artefactos bajo hydra-live-tests/ en los repositorios configurados.
echo ============================================================
exit /b 0
