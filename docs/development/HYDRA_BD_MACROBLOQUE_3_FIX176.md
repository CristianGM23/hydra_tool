# HYDRA V4.32.107 — B3+D3 Macrobloque 3 (Fix176)

## Objetivo

Reforzar de forma incremental la equivalencia funcional comprobable de los editores generados y la distribución independiente en canales inmutables. HYDRA continúa siendo la **aplicación web principal**, sin «HYDRA Studio» paralelo.

## B3 — Pruebas semánticas nativas vinculadas a cada ejecución

- `run_native_parity_suite.py` reutiliza los perfiles de compilación y tests de Fix175, sin aceptar comandos arbitrarios de la API o entradas de usuario.
- Un escenario declara un ID estable, capacidad de HYDRA, entrada JSON, salida canónica esperada y ruta de evidencia. La ejecución nativa **debe crear un registro nuevo** con desafío aleatorio, SHA-256 de entrada y resultado real. El verificador compara JSON estructuralmente.
- No se concede paridad a partir de compilar, de un `npm test` genérico, de una ejecución simulada, de evidencias antiguas ni de resultados divergentes.
- La certificación es **por escenario y capacidad**, nunca una afirmación genérica de paridad total. Todos los objetivos restantes se declaran no certificados. Los perfiles externos EMF/Sirius/Xtext/Langium/GLSP/BESSER/ANTLR necesitan sus propios fixtures y pruebas oficiales para emitir evidencias equivalentes.
- Fixture de referencia: siete escenarios reales de modelo (validación, quick fix, operación, eventos, puntuación, persistencia y round-trip) ejecutados en Node. Este fixture no acredita compatibilidad con las plataformas externas.
- Ejecución de comandos solo local/CI y con `--execute` explícito; no disponible como endpoint REST.

## D3 — Versiones inmutables, integridad de despliegues y rollback

- `editor_release_registry.py` valida los ZIP web de Fix175 con `verify_web_package.py` antes de incorporarlos a un registro local de versiones. Descomprime únicamente artefactos verificados en directorios separados e inmutables.
- Acciones `promote`, `verify` y `rollback`; canales `stable`, `beta`, `nightly`; versiones `major.minor.patch` con ascenso monotónico en cada canal, escritura atómica de punteros y exclusión de promociones concurrentes.
- Al verificar una versión, se comprueban **todos los archivos realmente desplegados** contra el ZIP, además del SHA-256 del propio ZIP. Impide modificación silenciosa de artefactos y cambios de inventario.
- El rollback revierte el puntero al último despliegue comprobado y deja los directorios inmutables, sin borrar proyectos de usuario.
- Los ZIP web se revisan también frente a colisiones de nombres ignorando mayúsculas/minúsculas y nombres prohibidos de Windows.
- Los SHA-256 acreditan integridad, **no autoría**. La firma de artefactos, el transporte autenticado y las actualizaciones remotas no están implementadas aquí. El registro opera solo con archivos locales explícitamente suministrados por el operador.
- No se afirma que estos artefactos sean instaladores nativos ni que Tauri esté compilado en todas las plataformas.

## Validación y uso

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block3.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block3.ps1
```

La segunda orden ejecuta también el E2E real de Node+Java y la cualificación global heredada.

Certificar un proyecto externo concreto exige crear sus propios escenarios compatibles:

```powershell
python scripts/validation/run_native_parity_suite.py --root data/generated-projects --project <project> --profile xtext --execute --report target/parity-xtext.json
```

La herramienta no otorgará un PASS de capacidades si las pruebas Xtext no generan evidencias específicas de la ejecución.

Promoción local de un editor web compilado (ZIP firmado solo con checksums, no con firma criptográfica de autoría):

```powershell
python scripts/distribution/editor_release_registry.py promote --registry data/release-registry --product mi-editor --channel stable --package target/mi-editor-web.zip
python scripts/distribution/editor_release_registry.py verify --registry data/release-registry --product mi-editor --channel stable
python scripts/distribution/editor_release_registry.py rollback --registry data/release-registry --product mi-editor --channel stable
```

El rollback solo está disponible cuando ya se ha publicado una versión anterior en ese canal.

## Puntos pendientes de certificación

1. Pruebas semánticas nativas oficiales por tecnología y capacidad, no solo el fixture neutral.
2. Instaladores Tauri reales Windows/macOS/Linux, arranque y actualización probados en máquina limpia.
3. Firma de publicación, verificación de origen y mecanismo seguro de descarga remota.
4. Compatibilidad de migraciones de datos y decisiones de actualización no regresivas.
5. Maven global, Vitest, Playwright y GitHub Actions multiplataforma sobre el commit que se publique.

La matriz GitHub Actions para el editor generado es **manual** y no se declara PASS sin ejecución real.
