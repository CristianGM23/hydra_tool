# HYDRA V4.32.107 · R6C4 · Macrobloque B2+D2 (Fix175)

## Alcance y arquitectura

- **HYDRA sigue siendo la aplicación web principal.** Esta entrega NO crea ni distribuye un segundo «HYDRA Studio».
- B conserva el compilador neutral, la API `native-parity/certify` y el motor existente. Aporta una matriz **ejecutable y acotada** para proyectos nativos generados con EMF, Sirius, Xtext, Langium, GLSP, BESSER y ANTLR.
- D separa **proyecto fuente**, **paquete web estático realmente desplegable** y **bundle/instalador Tauri realmente compilado**. Las tres categorías nunca se confunden.
- Ambos bloques reutilizan los contratos, rutas, tests y servicios de Fix174. La API anterior sigue disponible.

## B2: compatibilidad nativa medible

`python scripts/validation/run_native_e2e_matrix.py --root data/generated-projects --project <proyecto> --profile <tecnología> --execute --report target/bd2/native.json`

- Profiles permitidos: `emf`, `sirius`, `xtext`, `langium`, `glsp`, `besser`, `antlr`.
- Los comandos están definidos en código y requieren un proyecto nativo ya generado; ninguna petición web puede entregar una orden arbitraria al sistema operativo.
- Compilación y tests se ejecutan mediante subprocess sin shell, con timeout de 1–3600 segundos. Dependencias/toolchains ausentes devuelven `UNVERIFIED`/`TOOL_UNAVAILABLE`, **nunca `PASSED`**.
- `--execute` es imprescindible para demostrar ejecución real. Sin ello solo se devuelve un plan sin certificar.
- La evidencia refleja *compilación/tests*, no acredita automáticamente validaciones, Quick Fixes, eventos, gamificación ni round-trip de todos los frameworks.
- Estas capacidades avanzadas necesitan fixtures oficiales y pruebas específicas cuyo resultado se asocie a cada tecnología. Se conserva el contrato B1 que ya distingue `PROVEN`, `FAILED` y `UNVERIFIED`.

## D2: tres niveles de distribución

1. `package` (anterior): fuentes completas del editor generado.
2. `package-release-source` (Fix174): ZIP de fuentes preparadas para release, **no instalador nativo**.
3. **Nuevo** `POST /api/standalone-distributions/package-deployable-web`: ZIP de los contenidos de `dist/` solamente, con `hydra-release.sha256` por archivo, `hydra-deployment.json` y checksum SHA-256 del propio ZIP en la respuesta. No contiene fuentes Tauri, dependencias Node ni proyectos de desarrollo.

El tercer endpoint solo actúa tras la misma validación de materialización y preparación de publicación existente en HYDRA. Se ha añadido al contrato OpenAPI y al cliente de la interfaz, con botón independiente en el centro de distribución.

La clase `StandaloneWebDeploymentPackager` rechaza directorios simbólicos, archivos especiales, secretos/credenciales, sourcemaps, nombres reservados y entradas demasiado grandes. El inventario se ordena; cada archivo se verifica mientras se copia para prevenir modificaciones concurrentes; se publica mediante archivo temporal en el mismo directorio. Se limitan el número de recursos y el tamaño total.

### Construir un editor generado

Este comando trabaja sobre un editor ya generado, dentro de la raíz configurada:

```powershell
python .\scripts\distribution\build_generated_editor.py --editor mi-editor --web --offline --prepare-offline-shell --execute-project-build --report target\bd2\mi-editor-web.json
```

Para compilar además la distribución de escritorio con el CLI oficial de Tauri instalado:

```powershell
python .\scripts\distribution\build_generated_editor.py --editor mi-editor --web --native --offline --prepare-offline-shell --execute-project-build --timeout-seconds 3600 --report target\bd2\mi-editor-native.json
```

**Seguridad:** `--execute-project-build` es deliberadamente explícito. Los scripts npm/Cargo provienen del proyecto generado y deben tratarse como ejecutables: usar solo proyectos confiables. **No se expone esta ejecución por REST.**

- La caché offline usa el contenido real compilado y los assets locales (`index.html`, JS, CSS, manifiesto, etc.). El nombre de caché incluye identidad de producto, alcance del service worker y hash del contenido. Se eliminan solo versiones antiguas del **mismo** editor/alcance.
- Peticiones que no son `GET`, peticiones de otro origen y API de HYDRA no se interceptan para caché. La navegación offline usa el shell almacenado como último recurso.
- Esto no garantiza por sí solo funcionalidad completa offline si el editor necesita herramientas nativas, servicios remotos o APIs que no estén disponibles localmente.
- La construcción Tauri utiliza `cargo tauri build` y **no** informa `PASSED` cuando faltan paquetes reales bajo `target/release/bundle`.
- Los artefactos NO se consideran firmados, publicados ni actualizables automáticamente. Requieren firma, sellado, autorizaciones y pruebas reales por plataforma.

### Validar el paquete web sin descomprimir

```powershell
python .\scripts\distribution\verify_web_package.py C:\ruta\editor-web.zip
```

El verificador independiente compara todos los archivos con sus hashes, rechaza duplicados y rutas peligrosas, controla el tamaño descomprimido y comprueba el tipo de artefacto.

## Fixture y matriz CI

`reference-projects/bd2-standalone-smoke` es un editor autónomo **pequeño y real** que se compila con Node sin dependencias de terceros; prueba validaciones, Quick Fix, operaciones, eventos, puntuación y serialización. No se presenta como prueba de EMF/Sirius/Xtext/GLSP.

- CI principal GitHub: ejecuta regresiones B2+D2 y los tests del editor de referencia en Windows y Linux.
- CI manual `HYDRA generated-editor web and native smoke`: ejecuta compilación y tests Node y build de escritorio en **Windows, Linux y macOS**, y adjunta las evidencias/bundles cuando se ejecuta desde Actions.
- Solo después de que la matriz manual termine en verde puede afirmarse que ese fixture ha generado instaladores/bundles en las tres plataformas. No sustituye la comprobación de instalación y arranque en equipos reales.

## Puerta de calidad integrada

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block2.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_bd_block2.ps1
```

El segundo comando añade la cualificación final completa heredada. Las comprobaciones propias de Fix175 abarcan seguridad de rutas/ZIP, builds reales del fixture, validación de release, pruebas Node, y compilación y comportamiento real de las clases Java nuevas.

## Dependencias pendientes y límites

- Se requiere ejecutar Maven completo, Vitest/Playwright y GitHub Actions sobre la rama nueva antes de publicar o etiquetar Fix175.
- No se han ejecutado aún instaladores Tauri reales en las tres plataformas desde este entorno ni proyectos externos oficiales Xtext/Sirius/GLSP/BESSER.
- La matriz B2 prueba *compilación y tests nativos* de proyectos proporcionados. La paridad avanzada de validación/Quick Fix/eventos/transformaciones necesita sus fixtures nativos específicos (siguiente tramo del bloque B).
- Las salidas `dist`, `node_modules`, `target` y informes locales no forman parte del inventario canónico ni del hotfix. No sobrescribir ni mezclar los archivos Archify modificados en el equipo del usuario.
