@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================================
echo HYDRA V4.32.107 - CAMPANA AGRESIVA DE PROCESOS Y ARTEFACTOS
echo ============================================================
where java >nul 2>nul || (echo ERROR: Java no esta disponible en PATH. & exit /b 2)
where node >nul 2>nul || (echo ERROR: Node.js no esta disponible en PATH. & exit /b 2)
where npm >nul 2>nul || (echo ERROR: npm no esta disponible en PATH. & exit /b 2)
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~V"
set "JAVA_VERSION=%JAVA_VERSION:\"=%"
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if not "%JAVA_MAJOR%"=="24" (echo ERROR: la campana backend requiere JDK 24. Java detectado: %JAVA_VERSION% & exit /b 2)

if not exist frontend\node_modules\vitest\vitest.mjs (
  echo [0/5] Instalando dependencias frontend bloqueadas...
  pushd frontend
  call npm ci || (popd & exit /b 1)
  popd
)

echo [1/6] Cobertura obligatoria de procesos registrados...
python scripts\testing\verify_aggressive_process_matrix.py || exit /b 1
python scripts\testing\verify_external_repo_and_integration_campaign.py || exit /b 1

echo [2/6] Pruebas backend agresivas de artefactos, transformaciones y sinergias...
call mvnw.cmd -B -Dtest=AggressiveArtifactGenerationTests,AggressiveTransformationRuntimeTests,AggressiveEventGamificationSynergyTests,AggressiveRemoteRepositoryRoundTripTests,AggressiveRemoteProviderProtocolTests,AggressiveTechnologyArchiveSecurityIntegrationTests,AggressiveGeneratedArtifactLifecycleTests,AggressiveSpecialIntegrationArtifactTests test || exit /b 1

echo [3/6] Campana frontend dedicada + orden aleatorio + lotes variables...
pushd frontend
call npm run test:aggressive || (popd & exit /b 1)
popd

echo [4/6] Invariantes estructurales, manifest e i18n...
python scripts\validation\verify_manifest.py || exit /b 1
python scripts\validation\verify_invariants.py || exit /b 1
python scripts\validation\verify_hydra_i18n_full.py || exit /b 1

echo [5/6] Arquitectura, accesibilidad y presupuestos CSS...
python scripts\validation\verify_frontend_architecture.py || exit /b 1
pushd frontend
call node scripts\check-css-budgets.mjs || (popd & exit /b 1)
call node scripts\check-accessibility.mjs || (popd & exit /b 1)
popd

echo [6/6] Resumen...
echo Informe frontend: frontend\test-results\hydra-aggressive-summary.json
if exist frontend\test-results\hydra-aggressive-summary.json type frontend\test-results\hydra-aggressive-summary.json

echo ============================================================
echo CAMPANA AGRESIVA HYDRA: PASS
echo ============================================================
exit /b 0
