## R6C1 Fix124 — recuperación focalizada de workers Vitest en Windows

- Corrige el validador de R6C1 después de comprobar en Windows/JDK 25 que los 5 tests backend pasan y que los cinco ficheros frontend ligeros ejecutan 16/16 tests, mientras `IntegrationDesigner.test.tsx` no llega a arrancar por un timeout de bootstrap del pool `forks`.
- Separa el lote frontend dirigido en dos fases: cinco ficheros ligeros mantienen la ejecución ordinaria y `IntegrationDesigner.test.tsx` se ejecuta de forma aislada con `threads`, un único worker, sin paralelismo de ficheros y timeout acotado.
- Mantiene la política fail-closed: solo se cambia el mecanismo de ejecución del test que no llegó a iniciarse; aserciones, timeouts internos del test y errores de producto siguen provocando `FAIL`.
- Añade `validate_r6c1_fix124.ps1` para reanudar exactamente desde el punto fallido sin repetir Maven, CSS ni los 16 tests frontend ya superados. Tras el test pendiente ejecuta el build de producción para cerrar R6C1.
- Añade `verify_r6c1_fix124_vitest_worker_recovery.py` para impedir regresiones del aislamiento, pool y política de no repetición.

## R6C1 Fix118–123 — paridad tecnológica y recorridos guiados de extremo a extremo (post-RC2)

- Inicia una línea de desarrollo **posterior a la RC2 congelada**; no modifica ni reetiqueta la evidencia cualificada de R5C13 Fix117.
- Fix118 introduce un contrato canónico y neutral de recorrido tecnológico con nueve fases comparables: configurar, analizar, generar, verificar, previsualizar, aplicar, ejecutar, round-trip y exportar.
- Fix119 calcula la madurez desde implementaciones reales registradas (`TechnologyAdapter` + validador de proyecto) y pesos configurables, distinguiendo `FULL_WORKFLOW`, `OPERATIONAL`, `PARTIAL`, `DECLARED_ONLY` y `PROJECTION_ONLY`.
- Fix120 expone la paridad mediante una API de solo lectura y cierra la discrepancia ANTLR entre backend y frontend: ANTLR queda visible como destino textual declarado, pero HYDRA no finge generación operativa mientras no exista un adapter ejecutable registrado.
- Fix121 integra el recorrido en el asistente del diseñador de integraciones, mostrando puntuación, fases, prerrequisitos, bloqueos y recomendaciones. Las acciones de generación, preview y apply solo aparecen cuando el contrato real de la tecnología las soporta; PlantUML sigue el camino de proyección/exportación.
- Fix122 añade una receta recomendada conservadora que activa automáticamente solo capacidades `FULL`; las capacidades parciales o dependientes de herramientas externas permanecen disponibles como decisiones avanzadas explícitas.
- Fix123 añade regresiones dirigidas backend/frontend, cobertura española del nuevo recorrido y el gate `verify_r6c1_fix118_123_technology_workflow_parity.py`.
- El objetivo de R6C1 es elevar la **profundidad y homogeneidad** de las tecnologías ya soportadas antes de ampliar el catálogo con nuevos destinos.

## R5C13 Fix78 — convergencia final, lifecycle transversal y endurecimiento de release

- Corrige los dos errores TypeScript que impedían compilar el frontend tras Fix77: elimina estado no utilizado y código muerto de navegación de diagnósticos.
- Corrige además problemas de React detectados por ESLint en `GrammarMonacoEditor`: evita leer refs durante render, elimina un `setState` síncrono dentro de un effect y conserva las opciones de Monaco mediante refs para no recrear el editor innecesariamente cuando cambian fuente, modo de solo lectura o scrollbar.
- Completa físicamente la migración `validation.expression` → `semantic.expression`: elimina las 33 fuentes y 4 tests legacy, retira las exclusiones temporales del `maven-compiler-plugin` y actualiza el gate Fix27 para exigir un árbol convergido sin cuarentenas.
- Elimina tests/tombstones duplicados fuera de las rutas Maven normales y artefactos de release 4.32.106 que contaminaban el árbol 4.32.107.
- Corrige la procedencia del runtime textual para utilizar `HydraReleaseVersion.VERSION` en lugar del literal obsoleto `4.32.97`.
- Conecta el borrado canónico de metamodelos con la limpieza transversal de artefactos. Transformaciones, tests, sesiones/historial de sincronización, gramáticas, compilaciones/activaciones y sesiones/definiciones textuales se resuelven por referencias JSON explícitas (`metamodelId`, `transformationId`, `grammarId`) en vez de por coincidencias de nombre.
- Amplía el análisis previo al borrado para informar también de esos artefactos dependientes en la interfaz antes de confirmar la operación.
- Fija `desktop-local` a `127.0.0.1`, evitando que el backend de escritorio pueda quedar enlazado accidentalmente a interfaces de red.
- Regenera el manifiesto desde el inventario canónico y valida de nuevo los 54 gates estructurales: 54/54 PASS. TypeScript completo y ESLint de las superficies modificadas: PASS.
- Vitest y Maven/JUnit completos no se pueden ejecutar en este entorno concreto: el `node_modules` del paquete contiene bindings Rolldown de otra plataforma y Maven Wrapper requiere descargar Maven; estas limitaciones son de infraestructura y no se presentan como validación superada.

## R5C13 Fix77 — diagnósticos de gramática centralizados en Problemas y quick fixes visibles

- Elimina las tarjetas de diagnóstico duplicadas que se renderizaban debajo del editor de gramática y reducían innecesariamente el área útil de edición.
- Mantiene en Monaco las señales no invasivas: marcadores, gutter, subrayado/rango, contador de diagnósticos y quick fixes nativos del editor.
- Convierte la pestaña **Problemas** en la única superficie textual de incidencias de gramática.
- Muestra las correcciones rápidas asociadas directamente en cada problema, sin obligar a abrir **Detalles**; los detalles quedan reservados para causa, consecuencia, contexto, candidatos y datos técnicos.
- Añade regresiones para asegurar que los mensajes no vuelven a duplicarse en el editor y que una corrección rápida visible invoca la acción canónica correspondiente.

## R5C13 Fix76 — preservación binaria en importaciones remotas del navegador

- Corrige la importación binaria desde repositorios remotos en `loadRemoteFileArtifact`: deja de encapsular directamente el `Blob` devuelto por `Response.blob()` dentro de un nuevo `File`, evitando que entornos con distintos realms de `Blob` conviertan el contenido a la cadena `[object Blob]`.
- Construye ahora el `File` desde los bytes crudos obtenidos con `Response.arrayBuffer()`, preservando byte a byte cualquier artefacto binario y manteniendo el `Content-Type` anunciado por el backend.
- Refuerza la prueba agresiva navegador/repositorio para exigir simultáneamente nombre, versión remota, MIME, tamaño exacto y contenido binario idéntico tras `conexión -> exportación FormData -> importación`.
- Mantiene intacta la ruta de exportación, que ya demostraba preservación byte a byte en el mismo escenario.

## R5C13 Fix75 — recuperación del diseñador de transformaciones y proyecciones de gramática

- Corrige el fallo de apertura del Diseñador de transformaciones (`sourceModel is not defined`): `TransformationScope` recibe ahora de forma explícita los metamodelos origen y destino en lugar de depender de variables libres inexistentes en su ámbito.
- Refuerza las vistas **Diagrama inferido** y **Metamodelo inferido** del diseñador de gramáticas: al abrir una vista visual se solicita una proyección fresca del borrador actual y se elimina el bloqueo basado únicamente en una revisión coincidente.
- Evita colisiones entre proyecciones concurrentes de gramática mediante `requestId` únicos por petición; el análisis automático y la apertura manual de una vista ya no pueden invalidarse entre sí por `DUPLICATE_REQUEST_ID`.
- Mueve la información de **Regla de entrada efectiva** exclusivamente a la pestaña **Información**: deja de mostrarse debajo del editor Monaco y queda excluida de **Problemas**, manteniendo una única tarjeta informativa dedicada.
- Añade contratos de regresión para el alcance de metamodelos en transformaciones, refresco de proyecciones visuales, unicidad de solicitudes y ubicación de la regla de entrada.
- Actualiza dos validadores históricos de UX de gramáticas para reflejar el contrato vigente: las proyecciones inferidas son semánticamente de solo lectura, aunque permiten interacción visual transitoria de distribución/redimensionado.

## R5C13 Fix74 — compatibilidad Mockito en pruebas agresivas de proveedores

- Corrige `AggressiveRemoteProviderProtocolTests`: elimina la invocación explícita incompatible `ArgumentCaptor.<Map<String,String>>forClass(Map.class)`, que no compila con la firma genérica de Mockito usada por HYDRA.
- Sustituye el captor de cabeceras por un `argThat` tipado por contexto sobre `Map<String,String>`, manteniendo la comprobación estricta de `Authorization: Bearer secret-token` sin tipos raw ni casts no seguros.
- Conserva el `ArgumentCaptor<URI>` para demostrar que el token no aparece en la URL y mantiene intactas las aserciones de endpoint específicas de GitHub, GitLab, OneDrive, Google Drive y Dropbox.
- Revisa toda la suite de tests backend para confirmar que no existen otras invocaciones `ArgumentCaptor.<...>forClass(...)` con el mismo patrón incompatible.

## R5C13 Fix73 — repositorios externos y ciclo de vida agresivo de artefactos

- Añade pruebas nuevas de conexión/exportación/importación, concurrencia, control optimista y aislamiento para los seis proveedores remotos.
- Añade contratos específicos de protocolo para GitHub, GitLab, OneDrive, Google Drive y Dropbox y una suite live opt-in sin secretos embebidos.
- Añade una prueba agresiva en la frontera navegador/API que demuestra conexión de perfil, exportación binaria `FormData`, importación con versión remota y preservación byte a byte; también encadena `preview -> compile -> apply` para integraciones con IDs y rutas no triviales.
- Añade pruebas metamórficas de generación -> ZIP determinista -> reapertura -> comparación profunda para los diez adaptadores tecnológicos de proyecto y pruebas específicas para ANTLR y PlantUML.
- Añade corrupción deliberada de archivos tecnológicos y endurece rutas canónicas en `TechnologyGeneratedFile` y `TechnologyCompilationArchiveService`.
- El validador agresivo incorpora las nuevas suites, una matriz específica 6/6 proveedores + 12/12 tecnologías y `VALIDAR_HYDRA_REPOSITORIOS_REALES_WINDOWS.cmd`.

## R5C13 Fix70 — determinismo canónico de bundles y consolidación acumulativa de seguridad

- Corrige `MetamodelExportService` para que los formatos seleccionados se dedupliquen y ordenen según el orden canónico de `MetamodelExportFormat`, independientemente del orden recibido en la petición.
- Fija el timestamp de cada entrada ZIP a un valor reproducible para que dos solicitudes equivalentes generen el mismo artefacto byte a byte.
- Refuerza `AggressiveArtifactGenerationTests` para exigir igualdad binaria entre bundles equivalentes y añade regresión normal en `MetamodelExportServiceBundleTests` para una selección invertida `plantuml/xml`.
- Mantiene y redistribuye de forma acumulativa las defensas de Fix69: las URLs de artefactos del navegador rechazan traversal/rutas absolutas antes de construir `href`/`src`, y el runner agresivo diferencia fallos funcionales de fallos de infraestructura del worker.
- El hotfix Fix70 puede aplicarse directamente sobre Fix68 o Fix69; deja el mismo árbol canónico que el proyecto completo Fix70.

## R5C13 Fix69 — seguridad de URLs de artefactos y recuperación del runner agresivo

- Corrige el primer hallazgo funcional de la campaña agresiva: las URLs directas de descarga/visualización ya no construyen un endpoint para rutas con `..`, segmentos `.`/vacíos, rutas absolutas Windows/Unix o UNC. El frontend rechaza la ruta antes de crear `href`/`src`, mientras el backend mantiene su segunda defensa mediante `SafeProjectPathResolver`.
- Mantiene la codificación portable de rutas válidas con espacios y caracteres reservados (`#`, `?`) sin alterar su identidad relativa.
- Añade una regresión de seguridad a la suite frontend ordinaria además del escenario agresivo, para que el traversal vuelva a detectarse incluso sin ejecutar `test:aggressive`.
- Endurece la fase dedicada de `hydra-aggressive-test-runner.mjs`: por defecto comienza con `forks`; un fallo probado de bootstrap/lifecycle de Vitest reintenta la misma campaña con pools alternativos y queda registrado en `hydra-aggressive-summary.json`. Los `AssertionError`, timeouts de test, errores de sintaxis/tipo y OOM continúan siendo fallos de producto y no se ocultan como infraestructura.
- Amplía el timeout de la campaña dedicada a 120 s y conserva `threads` y `vmThreads` en las pasadas completas reordenadas, de modo que la campaña sigue buscando contaminación entre pools sin quedar bloqueada por un worker que no arranca en Windows.
- Actualiza la documentación para PowerShell usando `npm.cmd`, evitando el bloqueo de `npm.ps1` por ExecutionPolicy.

## R5C13 Fix68 — campaña agresiva integral de procesos, artefactos y sinergias

- Añade una campaña de pruebas agresivas que cubre los **23 módulos registrados** de HYDRA y falla automáticamente si aparece un módulo nuevo sin escenario de aceptación asociado.
- Incorpora seis clases de evidencia obligatoria: `ARTIFACT`, `MUTATION`, `ROUND_TRIP`, `RECOVERY`, `SECURITY` e `INVARIANT`, con entradas hostiles, límites, replay, idempotencia, determinismo y recuperación.
- Añade cuatro suites frontend dedicadas para mutaciones semánticas, entrega de artefactos, recuperación/seguridad y cobertura exhaustiva del registro de módulos.
- Añade `npm run test:aggressive`: primero ejecuta la campaña dedicada y después repite la suite frontend completa en tres órdenes deterministas distintos, con semillas 1010/2718/31415, tamaños de lote 7/13/23 y pools diferentes. Un batch que falla conjuntamente pero pasa de forma aislada se clasifica como defecto de aislamiento y es fatal en modo agresivo.
- Añade tres suites backend de integración agresiva: materialización/empaquetado de artefactos, runtime canónico de transformaciones con cientos de elementos y sinergia evento verificado → gamificación con replay idempotente.
- Demuestra artefactos finales mediante ZIP parseable, inventario de entradas, SHA-256, round-trip, trazas por elemento, ledger, feedback y ausencia de mutación del origen.
- Añade estrés específico sobre metamodelos de alto fan-out, proyección del editor con 1.200 elementos/1.199 conexiones, completion textual con 10.000 elementos y transformación canónica de 500 elementos.
- Añade límites destructivos para proyectos generados de gramáticas y entradas maliciosas/traversal para repositorios, imports, round-trip y ZIP de proyecto.
- Endurece `StandaloneDistributionPackager`: los ZIP de distribución excluyen también `coverage`, `playwright-report`, `test-results` y `.vite`, además de `node_modules`, `target`, `.git` y backups de round-trip. La campaña genera deliberadamente esos árboles y exige que ninguno llegue al artefacto final.
- Añade `scripts/testing/verify_aggressive_process_matrix.py`, ejecutable sin Node/Vitest, y `VALIDAR_HYDRA_AGRESIVO_WINDOWS.cmd` para la campaña completa con JDK 24.
- Documenta criterios de aceptación y reproducción de semillas en `docs/testing/AGGRESSIVE_PROCESS_CAMPAIGN.md`; el runner genera `frontend/test-results/hydra-aggressive-summary.json` con orden, batch, pool y clasificación de fallos.

## R5C13 Fix66 — aislamiento de tests del editor e i18n de rutas externas

- Corrige la regresión reproducible de `HydraProductionSpanishCoverage.test.ts`: las rutas de navegación de consolas externas se conservan como instrucciones técnicas completas y Microsoft Entra deja de producir una ruta híbrida parcialmente traducida.
- Añade cobertura explícita para `Microsoft Entra admin center → App registrations → New registration` en el contrato del catálogo de traducción.
- Endurece `EditorRuntimeSession.test.tsx` para que su mock de `./api` sea autocontenido y no dependa de `importOriginal`, evitando que un módulo API cargado previamente en el mismo batch de Vitest deje las llamadas de sesión apuntando al backend real.
- Reinicia de forma determinista los mocks del runtime antes de cada caso de `EditorRuntimeSession`.
- Unifica los mocks de `projectApi` de `ProjectAssetPicker.test.tsx` y `ProjectImageFileField.test.tsx` y hace que las aserciones operen sobre el módulo mockeado realmente importado por el componente, evitando colisiones entre archivos ejecutados en el mismo worker.
- Mantiene intacto el comportamiento productivo: los fallos del batch 6 eran no reproducibles al ejecutar cada fichero de forma aislada; Fix66 elimina la fragilidad de los tests sin relajar sus expectativas funcionales.

## R5C13 Fix65 — recuperación del contrato de HydraErrorBoundary

- Corrige la regresión reproducible de `HydraDesignSystem.test.tsx` tras la internacionalización del `HydraErrorBoundary`.
- Mantiene el comportamiento de producción introducido en Fix63: el límite de error compone un mensaje traducible por partes (`This area could not be rendered` + ámbito traducido) en lugar de fabricar una frase inglesa dinámica fuera del catálogo.
- Actualiza el contrato del test para esperar `This area could not be rendered: Events`, preservando la recuperación mediante `Try again`, el detalle técnico del error y la notificación `onCaughtError`.
- No revierte la traducción española: con interfaz `es`, el mismo contrato de producción puede mostrar `No se ha podido mostrar esta área: Eventos`.

## R5C13 Fix64 — recuperación sintáctica del catálogo de interfaz

- Corrige una coma ausente entre dos entradas de `frontend/src/i18n/interfaceAuditSpanishCatalog.ts` que impedía a Vite/OXC transformar el módulo y hacía caer en cascada cualquier suite que importase el sistema de i18n.
- Mantiene intactas las traducciones y ayudas incorporadas por Fix63; el cambio funcional se limita a restaurar un objeto TypeScript válido.
- Endurece `verify_hydra_i18n_full.py` para detectar entradas literales consecutivas de catálogo sin coma antes de que el error alcance Vitest o el bundler.
- Valida todos los catálogos `*SpanishCatalog*.ts` con TypeScript y realiza una comprobación sintáctica de 777 ficheros TS/TSX no declarativos.
- Prueba de regresión del gate: al retirar temporalmente la misma coma, el verificador falla en la línea exacta; al restaurarla vuelve a PASS.

## R5C13 Fix63 — finalización de ayudas contextuales y cobertura castellana de interfaz

- Amplía de forma homogénea las ayudas de campos cortas mediante una guía contextual compartida que explica cómo elegir el valor, qué cambia al modificarlo y cuándo reutilizar el contexto disponible.
- Clasifica automáticamente la orientación por tipo de decisión: identidad, texto visible, referencia semántica, expresión, ejecución, apariencia, ubicación/ruta, eventos, gamificación, integración y opciones booleanas.
- Integra la guía ampliada en `HydraFormField`, `DesignerFieldHelp`, `ValidationFieldHelp` y el panel adaptativo de propiedades del metamodelo, de modo que cientos de campos dejan de depender de descripciones de una sola frase.
- Refuerza además tooltips no asociados a campos en importación Update Site, análisis de repositorios, gobernanza de distribución/publicación y resolución de conflictos.
- Amplía el contrato de cobertura española para auditar también `outcome`, `callbackNote`, `idHelp`, `where`, `detail`, `capability`, `result`, `steps`, `context` y `scope`, incluidas ramas literales en arrays.
- Corrige textos visibles que podían permanecer en inglés en cargas, errores dinámicos, estados de compatibilidad, Git, condiciones visuales, transformaciones, importación, OAuth y mensajes de workbenches lazy.
- `HydraErrorBoundary` y `IndependentCapabilityWorkbench` dejan de construir frases dinámicas inglesas fuera del catálogo; los contextos de `HydraActionableErrorNotice` se traducen mediante el catálogo común.
- Mantiene sin traducir únicamente valores técnicos deliberados (identificadores, tecnologías, formatos, acrónimos, URLs y rutas literales de configuración de proveedores externos).
- Validación: arquitectura frontend PASS; convergencia de controles PASS; i18n completo PASS; cobertura española ampliada 9526 apariciones / 7501 textos únicos PASS; 143 valores técnicos intencionados; CSS 319/346 colores y 260/263 `!important`; accesibilidad PASS.

## R5C13 Fix52 — Home orientado a proyectos y lenguaje de alto nivel

- Mantiene Home como dashboard visual por tarjetas y lo adapta al modelo homogéneo `HYDRA Project`.
- Cuando no hay proyecto activo, muestra acciones claras para crear, abrir, importar o conectar una ubicación externa, además de accesos rápidos por finalidad.
- Cuando hay un proyecto activo, muestra continuar trabajando, resumen del proyecto, áreas realmente habilitadas, estado del modelo/lenguaje actual y proyectos recientes.
- Home consume la sesión, proyectos recientes y catálogo de creación existentes; no mantiene una segunda definición de tipos de proyecto.
- El proyecto activo y Home se sincronizan mediante eventos internos al crear, abrir, importar, configurar, cambiar el componente principal o eliminar un proyecto.
- Las tarjetas rápidas abren directamente el mismo asistente de proyectos con la finalidad seleccionada; no existe un flujo paralelo de creación.
- Home pasa a convivir con el Explorador de proyectos, de modo que el dashboard y los archivos del proyecto forman una única experiencia.
- Sustituye terminología interna visible por expresiones orientadas a usuario: `áreas del proyecto`, `componente principal`, `herramientas disponibles`, `tipo inicial del proyecto`, `archivos generados` y `estado del proyecto`.
- Elimina ejemplos de dominio `Railway` de la interfaz de creación/importación y los sustituye por ejemplos neutros.
- Centraliza etiquetas de finalidades y áreas en `projectPresentation.ts` para que el asistente, Home y las propiedades utilicen el mismo vocabulario.
- Añade cobertura contractual para el dashboard project-first y mantiene la traducción ES completa.
- Validación estática: sintaxis TS/TSX 528 ficheros sin diagnósticos; cobertura española 9018 usos / 7078 textos únicos PASS; arquitectura frontend PASS; i18n global PASS.

## R5C13 Fix49 FINAL — cierre de toolchains de transformación

- Cierra la rama de integración de toolchains nativos de transformación para ATL, ETL, Acceleo, EGL, Java, Xtend, Python y Go.
- Unifica los bridges oficiales en la versión 2.0.0 y mantiene el protocolo de autodescripción 1.1 compatible con peticiones 1.0.
- Java, Python y Go conservan runtimes independientes incluidos para el subconjunto M2M/M2T exacto ya demostrado.
- Xtend, ATL, ETL, Acceleo y EGL usan un backend JVM gestionado `JVM_CLASSPATH` completo: manifiesto de classpath server-owned, driver principal explícito, rutas relativas, rechazo de symlinks y diagnóstico fail-closed.
- La distribución base no redistribuye motores de terceros. Sin el payload legalmente instalado, `toolAvailable=false` y `executionAvailable=false`; HYDRA no simula soporte ni cae silenciosamente al runtime canónico.
- Añade `assemble_full_transformation_toolchain.py` para construir paquetes full-toolchain checksum-verificados a partir de motores y drivers instalados por el administrador. El ensamblado es aislado por lenguaje y no destruye paquetes completos de otros toolchains.
- Añade documentación operativa del contrato de drivers, estructura de payload y ensamblado de toolchains completos.
- Actualiza gates históricos a schema 1.1 manteniendo compatibilidad 1.0 y al flujo direccional actual de `TransformationExportService`.
- Validación final: 18/18 gates acumulados PASS; gate final de toolchains 28/28 PASS; higiene pre-GA 15/15 PASS; `HydraOfficialBridge.java` y `HydraJavaNativeRuntime.java` compilan con `javac`; runtime Python pasa `py_compile`; runtime Go pasa `go build`.
- Maven/JUnit completo no se ejecuta en este entorno porque el wrapper necesita descargar Maven y no hay acceso de red. No se considera validado por este paquete hasta ejecutarlo en un entorno con Maven disponible.

## R5C13 Fix45 — Official managed bridge contract and provisionable bridge packages

- Adds HYDRA-owned provisionable bridge packages for ATL, ETL, Acceleo, EGL, Java, Xtend, Python and Go under the trusted transformation-toolchain distribution catalog.
- Introduces native bridge self-description schema 1.1 with explicit `toolAvailable`, `executionAvailable` and bounded diagnostics while preserving schema 1.0 bridge compatibility.
- Distinguishes protocol compatibility from actual server tool availability: a valid official bridge can remain installed as `TOOL_UNAVAILABLE` until its external engine/runtime payload exists.
- Blocks a previously diagnosed unavailable native runtime before execution and maps stable official bridge exit code 69 to `TOOL_UNAVAILABLE`.
- Adds the server-only `${java}` managed-command placeholder, resolved from the JVM running HYDRA, so official bridge launch does not depend on browser input or PATH configuration.
- Ships a dependency-free `HydraOfficialBridge.java` launcher executed in Java source-file mode. It performs bounded self-description and fail-closed preflight without embedding third-party ATL/Epsilon/Acceleo/Xtend engines in the HYDRA core.
- Java, Python and Go packages can already verify presence of their host toolchains, but remain `CANONICAL_BUNDLE`; ATL/ETL/Acceleo/EGL official packages deliberately report unavailable until a trusted full engine payload is provisioned.

## R5C13 Fix42 — Directional native parity for bidirectional M2M

- Extends native parity requests and runtime evidence with an explicit `FORWARD`/`REVERSE` execution direction.
- Allows ATL and ETL to verify either explicit branch of a bidirectional M2M transformation independently; HYDRA never infers an inverse rule.
- Swaps source/target metamodel evidence for reverse native execution and validates that the bridge result echoes the requested direction.
- Introduces bridge protocol 1.1 for `REVERSE` while retaining protocol 1.0 for `FORWARD` requests so existing forward-only bridges remain compatible.
- Runtime descriptors now expose `supportedDirections`, `fullBidirectionalSynchronization` and `incrementalSynchronization`. The latter two remain false until a bridge preserves baseline, correspondence identity and conflict semantics as one synchronization operation.
- The frontend reuses the same preview direction for native parity and explicitly states that directional parity is not proof of full external bidirectional/incremental synchronization.

## R5C13 Fix39 — Incremental bidirectional synchronization

- Adds explicit `BATCH` and `INCREMENTAL` synchronization modes for bidirectional M2M transformations.
- Incremental synchronization compares current source/target models against synchronized baselines and reuses transformation trace correspondences.
- Propagates only affected elements/features, preserves unrelated state, supports controlled deletion propagation, and detects concurrent structural/feature conflicts.
- Ambiguous concurrent additions without a previous correspondence fail closed.
- Frontend exposes baselines, previous trace, deletion propagation and trace-safety controls.

## R5C13 Fix41 — Managed synchronization sessions

- Persists bidirectional incremental synchronization baselines and correspondence traces so users no longer paste the previous baseline/trace manually.
- Adds immutable, bounded checkpoints per synchronization session, optimistic concurrency through session ETags, history inspection, restore-as-new-revision, and safe deletion.
- Pins every baseline/checkpoint to the exact transformation ETag; incremental synchronization fails closed after the transformation definition changes.
- Adds explicit rebase against the current transformation definition and preserves the historical checkpoints instead of rewriting them.
- Rejects source/target model identity changes inside a managed session; a different model pair requires a new session.
- Session summaries expose stale transformation bindings so the frontend disables incremental synchronization until rebase.
- Adds managed-session REST endpoints, frontend API/types, a dedicated preview panel, ES translations and OpenAPI regeneration.
- Deleting a transformation is blocked while managed synchronization sessions still reference it.

## R5C13 Fix40 — Transformation test forward compatibility

- Restores the historical two-argument `executeDraft(document, sourceModel)` path for FORWARD persisted M2M tests while retaining the explicit directional overload for REVERSE cases.
- Prevents Mockito-based legacy tests from being converted into execution errors after directional test support was introduced.

# HYDRA V4.32.107

## R5C13 Fix 36 — base unificada de toolchains de transformación y destinos Python/Go

- Amplía el catálogo de exportación neutral a ocho tecnologías: ATL, ETL, Java, Xtend, Acceleo, EGL, Python y Go.
- Añade adaptadores Python y Go que preservan íntegramente la definición canónica en bundles `CANONICAL_RUNTIME`, sin presentar todavía esos wrappers como implementaciones nativas independientes.
- Centraliza en `TransformationNativeRuntimeSupport` las capacidades por lenguaje, los tipos M2M/M2T admitidos y el nivel de garantía (`NATIVE_EXPORT` frente a `CANONICAL_BUNDLE`), eliminando listas duplicadas entre executor, paridad y frontend.
- Extiende la configuración server-owned de bridges a Java, Xtend, Python y Go, manteniendo ejecutables y argumentos fuera de las peticiones HTTP.
- El descriptor público de runtime expone `supportedKinds`, `mode` y `parityReady`; la UI consume esos metadatos y deja de codificar listas de tecnologías por tipo de transformación.
- Mantiene fail-closed la paridad: Java, Xtend, Python y Go aparecen ya como toolchains gestionables, pero el botón de paridad independiente permanece deshabilitado hasta que sus bridges oficiales de semántica nativa sean implementados.
- Añade `go` a la allowlist compartida de procesos externos para preparar el toolchain Go sin crear una vía de ejecución paralela.
- Conserva la paridad independiente actual: ATL/ETL para M2M y Acceleo/EGL para M2T.

## R5C13 Fix 35 — ejecución nativa y paridad automática de transformaciones

- Añade ejecución controlada de bridges ATL, ETL, Acceleo y EGL a partir de comandos configurados exclusivamente en servidor; el navegador nunca aporta ejecutables ni argumentos arbitrarios.
- Materializa cada ejecución en un workspace temporal aislado con transformación, modelo, metamodelos y artefactos exportados, y exige evidencia de salida bajo un protocolo de ficheros versionado 1.0.
- Compara automáticamente la evidencia nativa con el runtime canónico mediante la capa de paridad de Fix34 y mantiene `parity=null` cuando el proceso no llega a producir evidencia válida.
- Centraliza el lanzamiento de procesos en `HydraExternalProcessRunner`, reutilizado también por la validación tecnológica, conservando tres únicos puntos explícitos de `ProcessBuilder` en todo el backend.
- Mantiene política fail-closed para exportaciones no representables, runtimes no configurados, allowlist, timeout, cuotas, symlinks, escapes de ruta, resultados sobredimensionados y terminación incompleta del árbol de procesos.
- Incorpora descubrimiento de capacidad y ejecución desde el panel de preview, contratos TypeScript/OpenAPI y mensajes ES para estados de runtime/paridad.

## R5C13 Fix 27 — convergencia de semantic.expression

- La protección temporal de Maven usada durante la migración queda retirada del árbol final: las 33 fuentes semánticas legacy y los 4 tests antiguos se eliminan físicamente.
- `com.example.editor.semantic.expression` queda como única fuente de verdad para AST, checker, evaluación, diagnósticos y tipos; `validation.expression` conserva solo sus ocho clases de persistencia/aplicación compatibles.
- El `pom.xml` deja de contener exclusiones específicas de `validation.expression`, evitando que el árbol físico y el árbol realmente compilado puedan divergir.
- El gate Fix27 se redefine para exigir precisamente ese estado convergido: ausencia física de fuentes/tests legacy, ausencia de cuarentenas Maven y uso del checker neutral.

## R5C13 Fix 25 — limpieza forzada del overlay de semantic.expression

- Corrige árboles actualizados mediante descompresión/overlay que conservaban las clases semánticas antiguas bajo `validation.expression` después de Fix24.
- Mantiene la arquitectura de Fix24: el AST, checker, evaluator, diagnostics y tipos viven únicamente en `semantic.expression`; `validation.expression` conserva solo las ocho clases de persistencia/aplicación compatibles.
- Refuerza el gate Fix24 para validar el inventario completo del paquete legado, no solo seis clases representativas.
- Añade un gate Fix25 que falla antes de Maven si queda cualquier `ExpressionTypeChecker`, `ExpressionDefinition`, `ExpressionDiagnostic`, `ExpressionNode` u otro kernel migrado bajo la ruta antigua.
- El hotfix Fix25 elimina de forma explícita las 37 rutas movidas en Fix24 y verifica su ausencia antes de lanzar compilación/tests.

## R5C13 Fix 24 — kernel semántico neutral de expresiones

- Extrae AST, tipos, checker, evaluador, dependencias y fingerprint desde `validation.expression` a `semantic.expression`, eliminando la dependencia conceptual de transformaciones y otros runtimes respecto a la capa de validación.
- Mantiene `ExpressionModel`, persistencia, ETag y endpoints REST históricos en `validation.expression` como adaptador compatible; no cambia el contrato JSON ni el schema 1.0.
- Introduce `ExpressionBatchCheckResult` y `ExpressionTypeChecker.checkBatch(...)` para validar conjuntos de expresiones sin depender del documento persistente `ExpressionModel`.
- `ExpressionModelApplicationService` adapta el resultado neutral al `ExpressionModelCheckReport` existente, conservando compatibilidad hacia controladores y consumidores históricos.
- Extrae la política de IDs canónicos a `com.example.editor.contract.StableIdentifierPolicy`; la clase histórica de `validation.contract` queda como fachada delegada para evitar una migración masiva no relacionada.
- Migra los tests unitarios del kernel a `semantic.expression` y actualiza los gates O3–O5/Bloque 1 para validar la nueva frontera física.
- Añade un gate Fix24 que impide reintroducir imports desde `transformation` hacia `validation.expression` para semántica y prohíbe dependencias del kernel neutral hacia el paquete de persistencia de expresiones.

## R5C13 Fix 11 — retroalimentación portable de gamificación

- Introduce `UserFeedbackEnvelope` como contrato canónico independiente del host para puntos, penalizaciones, logros, niveles, retos, recompensas, rechazos y compensaciones.
- Mantiene el ledger compensable como fuente durable de verdad y proyecta feedback con identificadores estables, evitando un segundo outbox mutable y duplicados al reintentar/pollear.
- Añade una política configurable global y por regla (`hydra.feedback.*` / `hydra.feedback.rule.<ruleId>.*`) con superficies, duración, progreso, límites visibles, deduplicación, agrupación y rechazo opcional.
- HYDRA Web incorpora toasts y actividad persistente con deduplicación, agrupación anti-spam, pausa con pestaña oculta y backoff exponencial ante fallos del endpoint.
- El diseñador de gamificación permite configurar la política y consultar el feedback generado junto al ledger.
- El standalone visual/textual muestra el mismo feedback portable y mantiene historial acotado; su control anti-farming usa estadísticas indexadas y evita el coste cuadrático observado en ráfagas largas.
- Los hosts generados TypeScript/Java/Python exponen callbacks opcionales `notifyFeedback`/`notify_feedback`; los fallos de UI son best-effort y nunca revierten un commit semántico.
- Se añade caché LRU explícitamente acotada para proyecciones backend, schema JSON del sobre y matriz de presentación por VS Code/Langium, Eclipse/Sirius/GLSP, BESSER y standalone.
- Pruebas nuevas cubren 10.000 entradas de ledger, 12.000 premios standalone, almacenamiento indisponible, ciclos de eventos, duplicados, compensaciones, políticas corruptas y hosts de UI que fallan.

## R5C13 Fix 3 — frontend completion shortcut contract

- Keeps the grammar-aware completion behavior unchanged in Monaco and the safe editor.
- Makes the documented completion shortcut explicit as `Ctrl+Space`, so the UX contract verifies the same label shown to users without depending on string construction details.
- Includes the cumulative R5C13 Fix 1 and Fix 2 corrections.

## Extreme resilience and pre-deployment contract cleanup

This release hardens the current/future HYDRA contracts after a new destructive and scale-oriented validation campaign over V4.32.105.

### Grammar scale and complexity
- Replaced recursive SCC traversal in `GrammarRuleGraphAnalyzer` with an iterative O(V+E) implementation.
- Replaced recursive cycle-path traversal with an explicit-frame implementation.
- Replaced recursive grammar-body parenthesis parsing with an iterative parser.
- Added permanent regressions for 20,000-rule chains, 10,000-rule strongly connected grammars and 25,000 nested groups.
- Verified direct left recursion, safe recursion, nullable-prefix left recursion and ANTLR native left recursion after the parser rewrite.

### Current/future toolchain contract
- Removed pre-deployment-only legacy grammar profiles `xtext-2.38-legacy` and `langium-4.0-legacy`.
- Kept the current pinned profiles for Xtext 2.44, Langium 4.3 and ANTLR 4.13.2.

### UI, Spanish and bounded layout
- Every module-navigation option exposes its high-level description as help, including inactive options.
- Module titles, descriptions and active navigation descriptions wrap instead of being truncated with ellipsis.
- Spanish coverage and i18n gates remain green after the UI changes.

### Environment limitation
The source distribution does not include `frontend/node_modules`, and this environment has no network access. Maven Wrapper also requires downloading Maven 3.9.16. Therefore the complete Maven/Vitest/Vite/Playwright suites are not claimed as executed here; deterministic structural gates, isolated Java compilation/harnesses and executable JavaScript generation smokes were used where available.

## Correcciones V4.32.107

- `inferMetamodelStrict` rechaza fuentes actuales que no producen ningún clasificador semántico; nunca certifica un metamodelo vacío ni usa `LAST_KNOWN_GOOD`.
- La generación ANTLR mantiene `HYDRA_REFERENCE` para recuperar de forma segura el tipo destino de referencias no contenidas; los tests verifican el contrato vigente.
- Las reglas auxiliares `HYDRA_TRANSPORT_RULE` de Xtext/Langium siguen siendo transporte y no se materializan como metaclases falsas durante round-trip.
- `GrammarPlatformConversionService` elimina la línea gigante de trazabilidad y separa responsabilidades de renderizado/truncado sin cambiar el formato funcional.
- El runner frontend detecta explícitamente una instalación incompleta de dependencias y explica cómo restaurar las devDependencies con `npm.cmd ci --include=dev`.
### Hotfix de recursos del editor en classpath
- Se fuerzan como recursos Maven no filtrados `schemas/editor/editor-definition-model-1.0.schema.json` y `examples/editor/people-basic-editor.json`.
- El hotfix vuelve a transportar físicamente ambos JSON aunque coincidan con el baseline limpio, para reparar árboles actualizados mediante overlays parciales.
- Se añade `verify_editor_definition_contract_resources.py` al gate estructural permanente para comprobar presencia, JSON válido, marcadores semánticos y configuración de copia a classpath.


### Hotfix frontend: capability readiness y estabilidad de Vitest en Windows
- El test de capability readiness deja de reutilizar una única instancia WHATWG `Response`; cada petición de metamodelo o gramática recibe un body independiente y se verifica también el `contextType` retornado.
- La API de producción no introduce `Response.clone()` como workaround: se mantiene la semántica normal de `fetch` y se corrige exclusivamente el doble de prueba defectuoso.
- Se restaura el contrato semántico completo del capability dashboard para contextos `METAMODEL` y `GRAMMAR`, incluyendo controller/service, DTO, frontend, API contract y OpenAPI.
- El runner full de frontend usa por defecto `threads` para los batches en Windows, evitando los timeouts de arranque observados con forks; el pool sigue siendo configurable mediante `HYDRA_TEST_BATCH_POOL`.
- La recuperación individual continúa usando un pool configurable e independiente, por lo que un fallo real sigue siendo reproducible y no se oculta como incidencia de infraestructura.
- Se añade `verify_frontend_capability_response_and_runner.py` como regresión permanente.

### R13 — límite arquitectónico CSS y recuperación adaptativa de Vitest
- `grammar-designer.css` vuelve a quedar por debajo del límite uniforme de 1500 líneas sin elevar el ceiling ni compactar reglas: la cabecera responsive y la vista previa viva se extraen a `grammar-designer-responsive.css`; el popup compartido de completion se extrae a `grammar-editor-completion.css` y se carga desde `GrammarMonacoEditor`, de modo que también esté disponible al abrir directamente el editor textual.
- Los verificadores R11/R12 consumen ambos stylesheets para seguir comprobando el contrato funcional con la nueva separación arquitectónica.
- El runner full de Vitest clasifica exclusivamente los fallos de arranque del pool (`Failed to start ... worker` / `Timeout waiting for worker to respond`) como infraestructura recuperable.
- En Windows, un fallo probado de bootstrap se recupera de forma aislada con la secuencia `threads -> forks -> vmThreads`; un assertion failure, un timeout real de un test o un error de código no cambia de pool y sigue fallando.
- El output de batches se captura de forma acotada para poder clasificar el fallo antes de imprimirlo; los batches correctos siguen mostrando su salida y los fallos reales conservan íntegramente el diagnóstico.

### Hotfix de compilación sobre el árbol de usuario 2026-09-14
- Se realinean las regresiones de `SemanticContextMetamodelResolver` con la arquitectura activa de V4.32.107 (`metamodel.application` + `GrammarRepository`) y se eliminan los tests residuales de la rama incompatible `com.example.editor.semantic`.
- El stubbing Mockito de `GrammarClassDiagramProjectionService.project(grammar, null)` usa ahora dos valores exactos y elimina la mezcla `raw value + matcher` que provocaba `InvalidUseOfMatchers`.
- El gate C7 comprueba de forma permanente que no reaparezcan imports de la fachada semántica incompatible y que el mock parcial de React Flow conserve `useNodesState` y `applyNodeChanges`.
- Se corrigen dos errores TypeScript del árbol actual: el narrowing de `snapshot` en la descarga del proyecto generado y el tipo global `JSX.Element` incompatible con React 19/TypeScript 6.
- Se limpian artefactos de release 4.32.106 que habían quedado mezclados en el árbol y se restaura el validador Windows correspondiente a 4.32.107.
### R5C10 — cierre del bucle de selección de React Flow y overlay backend tolerante
- Se elimina el ciclo de realimentación observado en `EditorRuntimeSession`: la selección controlada deja de alternar entre el estado de sesión, `useNodesState` y el store Zustand interno de React Flow cuando el padre rematerializa un nodo semánticamente idéntico.
- `useHydraModelEditorNodeResize` reutiliza el nodo montado si la proyección externa conserva el mismo estado autoritativo, filtra frames `select`, `position` y `dimensions` que ya están aplicados y evita `setNodes` cuando la reconciliación no cambia referencias.
- `EditorRuntimeCanvas` mantiene estables los callbacks de selección mediante refs y evita reenviar selecciones ya activas; `EditorRuntimeSession` conserva la identidad del diagrama cuando una respuesta no altera nodos/aristas y hace idempotente la selección iniciada desde un diagnóstico.
- Se añaden regresiones específicas para selección rematerializada y frames repetidos de selección/medición, además del gate C8 que conserva los dos escenarios reportados de quick fixes y parámetros de operaciones.
- El gate C7 acepta únicamente la ausencia del antiguo test `com.example.editor.semantic.SemanticContextMetamodelResolverTests` o un tombstone inerte de overlay. El source completo permanece limpio; el hotfix R5C10 transporta un tombstone compilable y un aplicador que elimina el fichero residual, de modo que también repara árboles actualizados previamente mediante descompresión sin borrado.

### R5C11 — limpieza definitiva de overlays residuales y caché Java
- El source canónico deja de aceptar tombstones de `com.example.editor.semantic`: el gate C7 exige ahora la ausencia física del test legado.
- Se detecta también la ruta duplicada accidental `src/test/test/java/com/example/editor/semantic/SemanticContextMetamodelResolverTests.java`, observada en un árbol actualizado mediante overlays manuales.
- El hotfix R5C11 elimina ambas rutas residuales, `src/MANIFEST_SHA256.txt` y los artefactos de release 4.32.106, y restaura `VALIDAR_HYDRA_V4.32.107_WINDOWS.cmd`.
- El código productivo y frontend de R5C10 se mantiene sin cambios: la comparación con el árbol de usuario confirmó identidad byte a byte en 3.294 ficheros canónicos; los errores visibles de `SemanticContextMetamodelResolver` procedían de diagnósticos cacheados sobre un fichero legado ya sustituido por un tombstone inerte.


### R5C12 — presupuesto central de Vitest para el diseñador de editores
- `EditorDefinitionDesigner.test.tsx` deja de imponer límites locales de 10 s y 20 s en dos escenarios de integración que renderizan el diseñador completo.
- Ambas regresiones heredan ahora `testTimeout` de `vitest.config.ts` (30 s por defecto, configurable mediante `HYDRA_TEST_TIMEOUT_MS`), igual que el resto de la suite pesada.
- No se relajan aserciones ni se ocultan fallos funcionales: los escenarios siguen comprobando contenedores visuales, relaciones de contención, padding y edición de mappings desde el árbol generado.
- Se añade un gate que impide reintroducir timeouts locales inferiores al presupuesto central en esta suite, evitando falsos negativos dependientes de carga/pool en Windows.

### R5C13 — ejecución semántica grammar-first de extremo a extremo
- El bus canónico procesa publicaciones derivadas mediante una cola FIFO por publicación raíz, de modo que todos los listeners terminan el evento actual antes de consumir eventos generados por bindings o flujos. Se añade un límite de 4096 eventos para cortar ciclos patológicos sin dejar estado residual del hilo.
- `EventBinding` deja de ser únicamente configuración persistida: un runtime nativo escucha el bus canónico, resuelve el contexto semántico estable (`metamodelId` o `grammar.<id>`), evalúa condiciones y mappings, conserva correlación/causalidad y republica el evento derivado. Los bindings deshabilitados por política se mantienen en un estado acotado y pueden reactivarse mediante refresh.
- `EventFlow` utiliza un único dispatcher global y se despliega de forma perezosa al llegar el primer evento de un ámbito semántico. Un `stop` explícito bloquea el autoarranque hasta un `start` también explícito, evitando suscripciones duplicadas y diferencias entre el diseñador y la ejecución real.
- El runtime del editor independiente adopta la misma cola FIFO y el mismo límite anticiclos. Los eventos derivados por bindings alimentan EventFlow y gamificación en el orden canónico, y el estado de gamificación cae de `localStorage` a anotaciones del modelo cuando el host no ofrece almacenamiento web.
- Las distribuciones VS Code y Eclipse aceptan `GRAMMAR` como raíz semántica de primera clase. Una exportación grammar-first ya no exige un artefacto físico `METAMODEL`; Eclipse solo genera `.ecore` cuando existe un metamodelo real y no fabrica uno sintético para ocultar la ausencia.
- El catálogo de auditoría incorpora descriptores bilingües explícitos para creación, modificación y borrado de gramáticas, estilos de sintaxis y contexto/definición del editor textual.
- Se documenta la matriz `docs/GRAMMAR_FULL_STACK_RUNTIME_MATRIX.md` y se añaden los gates C10 (71 comprobaciones) y C11 (27 comprobaciones), además de un smoke ejecutable que verifica `source.saved -> domain.saved -> flow.completed` y 5 XP en un ámbito `grammar.<id>`.

### R5C13 Fix 1 — convergencia de contratos de pruebas grammar-first y toolbar
- Los tests de Xtext y Langium para compilación grammar-first se alinean con el ámbito semántico canónico `grammar.<id>` introducido por R5C13; no se revierte el runtime a identificadores ambiguos sin prefijo.
- El test de quick fixes del editor visual reproduce la interacción real del toolbar agrupado: abre `History` antes de comprobar que `Undo` queda habilitado tras aplicar una corrección segura.
- El gate C11 comprueba además que las regresiones Xtext/Langium conserven el prefijo reservado `grammar.` para evitar divergencias entre adaptadores.
- No cambia la semántica productiva del runtime de R5C13; la corrección elimina expectativas de test obsoletas frente a contratos ya activos.

### R5C13 Fix 2 — overlay acumulativo y contrato de cancelación de conversión
- El hotfix vuelve a transportar de forma acumulativa las regresiones Xtext/Langium que exigen `semanticScopeId=grammar.main-language` y el test del historial agrupado del editor, evitando que una aplicación parcial de Fix 1 deje expectativas antiguas activas.
- `GrammarDesignerUxContract.test.ts` deja de depender de una concatenación exacta de sentencias: la cancelación se valida por semántica, comprobando que cierra la vista previa, limpia la tecnología solicitada, informa al usuario y no muta el modelo de gramática.
- `verify_v43304_rar_grammar_completion.py` incorpora la misma comprobación semántica fuera de Vitest.
- El hotfix Fix 2 se publica como overlay directo con rutas de proyecto (`src/...`, `frontend/...`, `scripts/...`) en la raíz del ZIP; no requiere copiar una carpeta `payload` ni ejecutar un instalador para sobrescribir los ficheros afectados.

## R5C13 Fix 4 — grammar-first runtime and feedback preparation

- Grammar designer header normalized to a compact two-row desktop layout with uniform control spacing and action sizing.
- Textual editor definition persistence is idempotent: existing definitions are replaced with ETag/If-Match instead of being POSTed again.
- Missing validation expressions, event definitions, event flows and gamification layers are treated as normal optional absence (HTTP 404) instead of application warnings.
- Integration profiles now distinguish `semanticScopeId` from optional `metamodelId`; `grammar.<id>` can act as the semantic root without a synthetic metamodel.
- Monaco Editor 0.52.2 is an explicit frontend dependency and is bootstrapped by HYDRA with its own worker; the safe editor remains a recovery fallback.
- Future UX contract recorded: canonical user feedback (rewards, penalties, diagnostics, recommendations and progress) must be adapted natively by each host without changing the underlying semantic event.

### R5C13 Fix 5 — aislamiento Maven/IDE en Windows

- Se corrige la carrera observada durante `testCompile` en Windows donde clases válidas desaparecían de `target/classes` y `javac` producía múltiples `bad class file` / `NoSuchFileException` y errores genéricos en cascada.
- Maven CLI utiliza ahora `build/hydra-maven` mediante `.mvn/maven.config`; `target/classes` queda desacoplado para que VS Code/Eclipse JDT pueda administrar su salida sin mutar el classpath de una ejecución Maven en curso.
- El `pom.xml` conserva `target` como valor por defecto para integraciones que no lean la configuración CLI, pero permite sobreescribir de forma canónica `hydra.build.directory`.
- La limpieza Windows actúa por defecto solo sobre la salida Maven aislada y solo toca `target/` si se solicita expresamente.
- Los informes Surefire se detectan primero en la nueva salida aislada, con fallback al árbol legado.
- Se incorpora `verify_maven_output_isolation.py` como regresión permanente y se añade al validador Windows completo.

### R5C13 Fix 6 — bootstrap de Monaco y preflight de dependencias

- El bootstrap del editor deja de importar el submódulo interno `monaco-editor/esm/vs/editor/editor.api` y usa la entrada pública `monaco-editor`, manteniendo el worker ESM documentado para Vite.
- Vite separa el chunk de Monaco mediante la entrada pública del paquete, reduciendo el acoplamiento a la estructura interna de la dependencia.
- `npm run dev` y `npm run build` ejecutan un preflight que detecta `node_modules` obsoleto o una instalación incompleta y muestra la reparación exacta (`npm ci`) antes de arrancar Vite.
- Se añade `verify_monaco_vite_bootstrap.py` para impedir que reaparezcan imports internos frágiles o que el source declare Monaco sin verificar su instalación local.

## R5C13 Fix 7 — Monaco semantic presentation and inferred-diagram stabilization

- Restores the textual appearance contract on the bundled Monaco editor: configured foreground/background, syntax colors, bold/italic overrides and symbol-specific styles are now applied through scoped Monaco decoration classes instead of unsupported decoration fields.
- Reuses grammar-derived runtime hints so contextual information and parser-rule/terminal-specific styling apply to concrete textual model tokens in both the preview and HYDRA textual model editor.
- Restores grammar-authoring feedback on Monaco: rich ERROR/WARNING/INFO markers include cause, impact and recommended solution, and the Monaco surface exposes matching safe quick fixes.
- Keeps preview and runtime textual editors on the same effective customization source.
- Reworks the grammar setup header so, on desktop, saved grammar/actions form the first horizontal row and all seven grammar fields (including the optional metamodel/import control) form the second row with uniform 40 px controls.
- Stabilizes the inferred class diagram: automatic React Flow measurement events no longer overwrite explicit node dimensions, nodes never collapse to an inference meter, and the per-class confidence meter has been removed.
- Adds explicit UML-style relation markers: filled diamond for composition/containment, hollow diamond for aggregation and visible arrow heads for relationship targets, in both React Flow and the deterministic fallback renderer.
- Adds `verify_r5c13_fix7_monaco_diagram_ux.py` as a permanent regression gate for these contracts.


## R5C13 Fix 8 — UML completo, exportación del metamodelo inferido y diagnósticos Monaco visibles

- Ajusta definitivamente la cabecera del diseñador de gramáticas a dos filas funcionales en escritorio: selector y acciones arriba; los siete campos de configuración abajo. Los botones principales comparten 44 px de altura, centrado y wrapping controlado a un máximo práctico de dos líneas sin desbordar etiquetas largas.
- Monaco vuelve a proyectar visualmente cada diagnóstico además de registrarlo como marker: error, aviso, información y regla no utilizada colorean número de línea, decoración lateral y glyph margin, con hover enriquecido que conserva mensaje, causa, impacto y recomendación.
- El diagrama de clases inferido sustituye los markers SVG externos por un edge UML propio dentro del SVG de React Flow, evitando resoluciones rotas de `markerStart`/`markerEnd`. Composición usa rombo relleno, agregación rombo vacío, herencia/implementación triángulo hueco y las asociaciones/dependencias muestran navegabilidad coherente.
- Las clases inferidas se dimensionan dinámicamente según nombre, atributos y referencias. Se eliminan truncados arbitrarios: todas las características visibles se renderizan y el nodo crece con su contenido, manteniendo mínimos de legibilidad y sin barras/filas de inferencia.
- La sección del diagrama incorpora `Descargar diagrama` con exportación nativa a PNG, JPEG, SVG, draw.io y PlantUML. PNG/JPEG se rasterizan desde el SVG canónico sin añadir nuevas dependencias npm; draw.io y PlantUML conservan la semántica UML de las relaciones.
- Se añaden regresiones específicas para exportación, auto-sizing, notación UML y señalización de diagnósticos en Monaco, además del gate `verify_r5c13_fix8_uml_export_diagnostics.py`.

## R5C13 Fix 9 — convergencia real con el editor de metamodelos y semántica Monaco

- El diagrama inferido de una gramática deja de mantener un renderer UML paralelo. La vista usa directamente `MetamodelReadOnlyCanvas`, que reutiliza `buildMetamodelNodes`, `buildMetamodelEdges`, `MetamodelClassNode`, `MetamodelEnumNode`, `MetamodelDataTypeNode`, `MetamodelRelationLayer` y `metamodelElkLayout` del editor de metamodelos de HYDRA.
- Composición, agregación, referencias, herencia e implementación usan por tanto exactamente los mismos rombos, flechas, triángulos, etiquetas, cardinalidades y geometría que el Metamodel Designer. La proyección es de solo lectura respecto al modelo semántico, aunque conserva navegación, selección, zoom, pan y reposicionamiento transitorio.
- Las dimensiones iniciales de clases inferidas se calculan a partir de todos sus atributos, referencias y operaciones antes de entrar en el renderer canónico. Se ignoran mediciones automáticas de React Flow para evitar que una clase vuelva a colapsarse por una medición tardía del DOM.
- Monaco 0.52 usa `createDecorationsCollection` como ruta principal para personalización sintáctica y señalización diagnóstica, conservando `deltaDecorations` únicamente como fallback de compatibilidad. Esto restaura de forma estable colores, negrita/cursiva, foreground/background y las decoraciones de error/aviso/info en el número de línea, gutter y línea completa.
- El editor Monaco de gramática expone además `hover`, completion, go-to-definition, find-references y code actions. Los quick fixes provenientes del análisis se muestran como acciones nativas y en las tarjetas de feedback; cuando un diagnóstico ofrece candidatos seguros sin `codeActionId`, se generan sustituciones acotadas como alternativa.
- La vista previa textual y el editor textual real consumen la misma `effectiveTextualCustomization`, incluidos estilos por regla/terminal y contexto asociado a literales e identificadores de la gramática.
- Las tarjetas de Problemas diferencian visualmente `ERROR`, `WARNING` e `INFO`; la información usa la identidad visual primaria/azul y nunca hereda el fondo rojo de un error.
- `GrammarUmlEdge.tsx` queda únicamente como shim de compatibilidad de overlays antiguos y no contiene ningún renderer paralelo.
- Se añade `verify_r5c13_fix9_shared_renderer_monaco_semantics.py` y se realinean los gates Fix7/Fix8/R43308 al renderer canónico compartido.

## R5C13 Fix 10 — semantic integrations, form convergence and actionable grammar diagnostics
- Integration routes now expose an explicit `/api/semantic-contexts/{semanticScopeId}/integrations` contract while preserving the legacy canonical-metamodel alias. Grammar-first roots such as `grammar.main-language` therefore no longer depend on pretending to be physical metamodel identifiers.
- Technology compilation and canonical projection endpoints expose the same semantic-context alias, keeping the complete integration workflow coherent for grammar-backed languages.
- Update Site, standalone distribution and publication forms now inherit the common HYDRA control contract (height, padding, border, radius, background, typography and focus ring), including advanced native controls outside `HydraFormField`.
- Browser-side grammar inspection now produces deterministic quick fixes for close unresolved rule/token references, malformed `entry` keywords and missing rule terminators. Local and backend code actions are merged and exposed both in Monaco and in the Problems dock.
- Grammar diagnostic cards now keep severity-specific visual semantics: error uses danger, warning uses warning, and information uses the primary/blue information treatment rather than an error appearance.
- Added permanent Fix 10 regression gates and dedicated frontend/backend route/quick-fix tests.


## R5C13 Fix 12 — cierre de regresiones de feedback y compatibilidad de pruebas
- Se incorpora oficialmente `user-feedback-envelope-1.0.schema.json` al baseline contractual canónico, elevando el inventario revisado de 39 a 40 esquemas.
- El runtime de gamificación aplica idempotencia por `sourceEventId` antes de reconstruir estado, persistir, publicar eventos derivados o proyectar feedback, evitando notificaciones duplicadas ante reintentos del mismo evento.
- Las políticas numéricas de feedback distinguen valores malformados (fallback seguro) de valores fuera de rango (clamp determinista), incluyendo `maxVisible`, duración, deduplicación y agregación.
- Se elimina la línea CSS gigante introducida por el centro de feedback para volver a respetar el presupuesto lógico compartido de 1400 caracteres por línea.
- Se corrige el import duplicado de `UserFeedbackEnvelope` en el frontend: permanece como tipo de `types.ts` y deja de importarse erróneamente como valor desde `gamificationNormalization`, evitando el fallo de transformación Vite/OXC.
- Se añade un gate permanente Fix 12 que cubre baseline de schemas, idempotencia, políticas corruptas/acotadas, presupuesto lógico y el contrato de import type-only del frontend.

## R5C13 Fix17 — forced integration recovery
- Reissues the already-corrected Transformation Designer CSS with logical lines below the 1400-character structural ceiling.
- Reissues the module registry contract with `TRANSFORMATIONS` explicitly included as a first-class DSL capability.
- Adds no functional transformation semantics; this recovery prevents stale local files from masking the Fix16 corrections.

## R5C13 Fix18 — transformación M2M enriquecida, M2T seguro y T2M grammar-first
- Los bindings M2M separan el origen del valor de su adaptación mediante `TransformationValuePolicy`: reducción de colecciones, conversión primitiva explícita, mapping de enumeraciones y política de error/fallback.
- El runtime canónico aplica esas políticas después de calcular DIRECT/CONSTANT/EXPRESSION y antes de poblar el destino; `TRACE_EQUIVALENT` conserva su semántica relacional en dos fases.
- Se añade preview M2M sobre un draft no persistido mediante `/api/transformations/preview`, evitando que una vista previa modifique el repositorio.
- M2T se habilita con `TransformationGenerationRule`: contexto tipado, guard opcional, ruta relativa, plantilla neutral y colisión `FAIL/REPLACE`. El runtime genera artefactos exclusivamente en memoria, rechaza rutas absolutas/padres y no ejecuta código embebido.
- T2M usa un `TransformationTextParserGateway` desacoplado. La implementación inicial consume el snapshot activo de gramática a través del parser neutral de HYDRA y puede encadenar una transformación M2M persistida como postproyección.
- Se separan ejecución persistida y preview para M2M, M2T y T2M. Los previews reciben el `TransformationDefinitionDocument` actual y no realizan create/replace implícitos.
- El cliente TypeScript incorpora los contratos de value-policy y funciones de preview, manteniendo compatibilidad con documentos Fix17 al tratar las nuevas secciones como opcionales y normalizarlas en backend.

## R5C13 Fix19 — autoría M2M/M2T/T2M y exportación con fidelidad explícita
- El diseñador de transformaciones expone las tres familias sobre el mismo `TransformationDefinitionDocument`; los modos Guiado y Avanzado/manual continúan siendo dos vistas del mismo estado y cambiar de modo no convierte ni duplica la definición.
- M2M permite configurar por propiedad origen directo, constante o expresión, adaptación de colección, conversión explícita, política ante error, fallback, enum mapping y relaciones resueltas mediante `TRACE_EQUIVALENT`.
- M2T incorpora autoría visual de reglas de generación con clase de contexto, ruta de salida, plantilla segura, política de colisión y guard reutilizando los constructores de condiciones/expresiones de HYDRA.
- T2M incorpora selección de gramática, ruta lógica y proyección M2M opcional; el parsing sigue perteneciendo a la capa de gramáticas y se consume a través del gateway canónico.
- Preview M2M/M2T/T2M permanece side-effect free respecto a la persistencia y muestra el resultado canónico, artefactos o información de parsing/traza según la familia.
- Se introduce `TransformationLanguageAdapter` y su registro extensible. Java/Xtend exportan bundles `CANONICAL_RUNTIME` que conservan íntegramente `transformation.json`; ATL/ETL generan únicamente el subconjunto M2M nativo demostrado; Acceleo/EGL generan únicamente el subconjunto M2T de plantillas estructurales demostrado.
- Todos los adaptadores son fail-closed: guards, conversiones, multiplicidades, equivalencias o tokens no proyectables bloquean la exportación en vez de descartarse o aproximarse silenciosamente.
- La API incorpora catálogo de lenguajes y exportación de drafts sin persistir. OpenAPI y cliente TypeScript convergen en 511 operaciones sin rutas duplicadas.
- El exportador resuelve únicamente los metamodelos necesarios para cada familia: source+target en M2M, solo source en M2T y ninguno en T2M.
- Se amplían los contratos frontend/backend y se añade `verify_r5c13_fix19_transformation_authoring_adapters.py`; el gate específico queda en 31/31 PASS.

## R5C13 Fix20 — compatibilidad Jackson 3 en exportadores de transformaciones
- Los bundles Java/Xtend y sus pruebas dejan de importar `com.fasterxml.jackson.core/databind`, APIs de Jackson 2 no presentes en Spring Boot 4.1.1.
- `CanonicalRuntimeBundleSupport`, `JavaTransformationLanguageAdapter` y `XtendTransformationLanguageAdapter` reutilizan el `tools.jackson.databind.ObjectMapper` ya empleado por HYDRA.
- La captura de errores de serialización usa `tools.jackson.core.JacksonException`, coherente con el resto de persistencia/exportación del proyecto.
- `TransformationLanguageAdapterTests` se alinea con Jackson 3 para evitar que el build supere `compile` y falle posteriormente en `testCompile`.
- Se añade un gate permanente que prohíbe imports Jackson 2 de `core/databind` dentro del bounded context de transformaciones.

## R5C13 Fix21 — proyección de tokens de atributos M2T con IDs estables
- El traductor común de plantillas M2T reconoce ahora el alfabeto completo de IDs canónicos de elementos (`[A-Za-z0-9._:-]`) en tokens `{{attr:<id>}}`, incluyendo identificadores como `attribute:Person:name` y `attribute:class:Book:title`.
- Acceleo y EGL vuelven a proyectar esos tokens a expresiones nativas (`[e.name/]` y `[%=e.name%]`) en lugar de dejar el token HYDRA literal en el artefacto exportado.
- Los tokens canónicos de identidad `{{id}}` y `{{classId}}` continúan bloqueándose de forma fail-closed porque no representan propiedades portables del modelo externo.
- Las regresiones de exportación comprueban además que ningún artefacto Acceleo/EGL compatible conserve tokens `{{attr:...}}` sin traducir.
- Se añade `verify_r5c13_fix21_m2t_attribute_tokens.py` para mantener alineado el parser de tokens con la política oficial de identificadores estables de HYDRA.

## R5C13 Fix22 — pruebas persistentes, cobertura y quick-fixes de transformaciones
- Se incorporan `TransformationTestCase` persistentes con ETag y almacenamiento canónico independiente para M2M, M2T y T2M.
- El diseñador permite ejecutar una vista previa, capturarla como resultado esperado y guardar el escenario sin tener que construir manualmente IDs destino generados por el runtime.
- La suite calcula cobertura real de reglas y bindings M2M mediante trazabilidad detallada forzada únicamente durante testing; M2T calcula cobertura a partir de las reglas que generaron artefactos.
- Los resultados distinguen `PASS`, `FAIL` semántico y `ERROR` de ejecución, evitando confundir una diferencia respecto al esperado con un fallo del runtime.
- Se añaden quick-fixes side-effect free para conversiones explícitas, reducción de colecciones, mappings obsoletos y propiedades obligatorias con una única correspondencia exacta y conservadora.
- Ninguna sugerencia heurística modifica el draft automáticamente: la UI muestra la propuesta y requiere `Aplicar propuesta`.
- Una transformación con casos persistidos ya no puede eliminarse hasta retirar esos casos, evitando datos huérfanos sin introducir un cascade no atómico.
- El mismo panel de revisión y testing se reutiliza en modo guiado y avanzado; no existe un documento de prueba alternativo por modo de edición.
- OpenAPI y cliente TypeScript convergen en 519 operaciones sin rutas duplicadas.
- Se añade `verify_r5c13_fix22_transformation_testing_quickfixes.py`; el gate específico queda en 31/31 PASS.

## R5C13 Fix 23 — estabilización de tests del canvas runtime

- `EditorRuntimeCanvasContainer.test.tsx` deja de depender de un mock parcial de `@xyflow/react` que ya no interceptaba de forma estable React Flow con Vitest 4.1.10.
- La proyección semántica del canvas se extrae a `buildEditorRuntimeFlowProjection`, una función pura usada tanto por producción como por las regresiones de contención visual.
- Los tests verifican directamente `parentId`, el ajuste de geometría dentro del contenedor y la supresión exclusiva del edge de containment, sin depender del store interno de React Flow.
- La comprobación de imágenes usa el nombre accesible de la representación, evitando la ambigüedad introducida por el `MiniMap`, que también expone `role="img"`.
- El smoke de selección usa el contrato accesible `role="application"` de React Flow en lugar del antiguo `data-testid="react-flow"` del mock.
- Se mantiene explícita la protección contra feedback de selección mediante `hasSameRuntimeSelection` y se añade `verify_r5c13_fix23_editor_runtime_canvas_tests.py` como regresión permanente.

## R5C13 Fix26 — limpieza determinista de fuentes legacy del kernel de expresiones
- La migración `validation.expression -> semantic.expression` se blinda frente a checkouts actualizados mediante overlays ZIP que no eliminan archivos antiguos.
- Se añade un gate que exige que `ExpressionTypeChecker.java` exista únicamente bajo `semantic.expression` y que el paquete histórico `validation.expression` conserve solo los ocho contratos de persistencia/compatibilidad previstos.
- El aplicador Windows de Fix26 elimina explícitamente las 37 fuentes/tests migrados y limpia `target` antes de compilar, evitando que Maven mezcle tipos legacy y neutrales.
- No cambia la semántica del kernel, de validaciones ni de transformaciones; este fix corrige exclusivamente el estado físico del checkout tras una migración de paquetes.

## R5C13 Fix28 — Event semantic expression convergence

- EventFlow valida `conditionExpressionId` de nodos y transiciones contra `semantic.expression`, incluyendo existencia, errores semánticos y retorno BOOLEAN/ANY.
- El runtime EventFlow ejecuta guards de nodo y transición mediante `ExpressionEvaluator`; las condiciones falsas filtran el paso o la transición sin ejecutar lógica lateral.
- EventBinding reutiliza el contexto neutral común `CanonicalEventExpressionContext`, evitando una segunda proyección de variables de evento.
- Gamification conserva su JSON y UX existentes, pero compila `conditionPath/operator/expectedValue` a un AST `semantic.expression` efímero y lo ejecuta con el evaluator neutral en el runtime completo.
- Los constructores embebidos de EventFlow/Gamification conservan compatibilidad para los tests y hosts ligeros existentes.
- Añadido `verify_r5c13_fix28_event_semantic_expression.py` y regresiones específicas de EventFlow/Gamification.

## R5C13 Fix29 — corrección de compilación del contexto de eventos en gamificación

- Corrige las dos regresiones de `GamificationNeutralExpressionConditionTests` que construían `EventContext` con la constante inexistente `EventContextKind.MODEL`.
- Las pruebas usan ahora `EventContextKind.MODEL_EDITING`, que es el contexto canónico ya empleado por el editor visual y el runtime textual para eventos sobre modelos.
- No se añade un alias `MODEL` al enum: se evita duplicar semánticamente `MODEL_EDITING` y se mantiene estable el contrato serializado de `EventContextKind`.
- Se añade `verify_r5c13_fix29_event_context_kind_compile.py` para bloquear tanto referencias futuras a `EventContextKind.MODEL` como la introducción accidental de un alias `MODEL` en el enum.


## R5C13 Fix30 — alineación de fixtures EventFlow/Gamification con contratos canónicos

- Los tests añadidos durante la convergencia de expresiones dejan de utilizar `:` en identificadores de documento (`meta.test`, `expressions.test`, `flow-model.test` y `document.test`), respetando `StableIdentifierPolicy` sin relajar la política de producción.
- Se conserva `:` en identificadores de elemento como `expression:allowed`, `node:start`, `event:1` o `element:1`, donde la política canónica sí lo admite.
- `EventFlowExpressionConditionTests` importa `ExpressionModel` desde `validation.expression`, que sigue siendo el documento persistente/versionado; el AST y el checker permanecen en `semantic.expression`.
- `EventFlowRuntimeAutoDeploymentTests` deja de construir un `EventFlowLoadResult` incompleto con `checkReport = null` y suministra un `EventFlowCheckReport` válido vacío, reproduciendo el contrato real de `EventFlowApplicationService.find()`.
- No se modifica el runtime para aceptar informes ausentes: `EventFlowRuntimeService.compile()` continúa fail-closed ante diagnósticos bloqueantes y la aplicación sigue siendo la responsable de producir el informe semántico.
- Se añade `verify_r5c13_fix30_event_fixture_contracts.py` para impedir regresiones en IDs de documento, paquetes del modelo de expresiones y mocks de autodespliegue.

## R5C13 Fix31 — soporte de variables opcionales nulas en el kernel neutral de expresiones

- Corrige el `NullPointerException` observado en `GamificationNeutralExpressionConditionTests.legacyConditionIsExecutedByNeutralExpressionKernel` al construir el contexto neutral de evaluación para eventos sin actor o sujeto.
- `ExpressionEvaluationContext` deja de usar `Map.copyOf(...)` para las variables de ejecución, ya que esa API rechaza valores `null` aunque `null` sea un valor semántico válido para expresiones (`IS_NULL`, `IS_NOT_NULL`, `EQUALS null`, `NOT_EQUALS null`, `coalesce` y compatibilidad `EXISTS/NOT_EXISTS`).
- El contexto realiza ahora una copia defensiva `LinkedHashMap` envuelta como mapa no modificable: conserva valores nulos, mantiene el aislamiento frente a mutaciones del llamador y continúa rechazando nombres de variable nulos.
- La corrección beneficia de forma transversal a EventBinding, EventFlow, Gamification y cualquier otro consumidor de `semantic.expression`; no introduce un tratamiento especial de gamificación ni relaja el chequeo de tipos.
- Se añaden regresiones directas para variables nulas, copia defensiva, inmutabilidad y `_current = null`; la regresión de gamificación existente mantiene `actor` y `subject` ausentes para cubrir el recorrido real `CanonicalEventExpressionContext -> ExpressionEvaluationContext -> ExpressionEvaluator`.
- Se añade `verify_r5c13_fix31_nullable_expression_variables.py` para bloquear la reintroducción de `Map.copyOf` sobre variables de evaluación y preservar el contrato nullable del kernel.

## R5C13 Fix34 — evidencia de paridad ejecutada para transformaciones nativas
- Se incorpora `TransformationParityService` como capa neutral para contrastar resultados reales ATL/ETL/Acceleo/EGL frente al runtime canónico de HYDRA sin confundir compatibilidad estática de exportación con equivalencia ejecutada.
- La comprobación es fail-closed: una exportación bloqueada nunca puede producir un informe de paridad positivo y Java/Xtend quedan fuera de este flujo porque ya empaquetan el runtime canónico completo.
- M2M compara semántica independientemente de IDs y anotaciones generados por cada motor, pero conserva metamodelo, raíces, clases, atributos y relaciones. Los valores numéricos equivalentes se normalizan antes de comparar.
- M2T compara el conjunto de artefactos por ruta y contenido; solo normaliza separadores portables de ruta y finales de línea para evitar falsos negativos entre Windows/Linux.
- Cada contraste devuelve huellas SHA-256 canónica/nativa y un máximo acotado de diferencias para que la evidencia sea auditable sin materializar árboles completos en el informe.
- Se añade `POST /api/transformations/parity/verify`, contratos TypeScript tipados y regeneración del catálogo OpenAPI, que pasa de 519 a 520 operaciones sin rutas ni operationIds duplicados.
- Esta capa recibe evidencia ya ejecutada por el motor externo; el siguiente slice conecta bridges de ejecución nativa configurables sobre esta misma API de paridad.

## R5C13 Fix38 — transformaciones M2M unidireccionales y bidireccionales
- La direccionalidad pasa a ser parte explícita del modelo canónico (`UNIDIRECTIONAL` / `BIDIRECTIONAL`) y el esquema de transformación evoluciona a 1.1 manteniendo lectura compatible de documentos 1.0.
- Las reglas M2M declaran `FORWARD` o `REVERSE` y se emparejan mediante un `correspondenceId` estable; HYDRA nunca intenta deducir automáticamente la transformación inversa.
- El checker exige en modo bidireccional reglas activas en ambos sentidos, correspondencias completas y trazabilidad habilitada; una regla inversa activa queda bloqueada en modo unidireccional.
- El compilador y el runtime canónico ejecutan explícitamente `FORWARD` o `REVERSE`, intercambian los metamodelos de entrada/salida y registran la dirección y la correspondencia en la traza.
- Se incorpora sincronización M2M batch: origen modificado ejecuta ida, destino modificado ejecuta vuelta y cambios en ambos lados se resuelven mediante política explícita (`FAIL_ON_CONFLICT`, `FORWARD_WINS`, `REVERSE_WINS`). El comportamiento por defecto es fail-closed.
- La UI permite elegir direccionalidad, editar reglas de ida/vuelta sobre los árboles invertidos, emparejar correspondencias, previsualizar cada dirección y ejecutar una sincronización batch con ambos modelos presentes.
- La exportación M2M recibe una dirección explícita y crea una proyección unidireccional efímera del sentido elegido para reutilizar los adaptadores existentes sin alterar la definición canónica.
- La paridad manual conserva la dirección ejecutada. La paridad nativa automática de una definición bidireccional completa permanece bloqueada hasta que los bridges externos puedan preservar ambos sentidos y la identidad de correspondencias.
- Los casos de prueba M2M almacenan también la dirección de ejecución, de modo que las regresiones pueden cubrir ida y vuelta de forma independiente.
- Se añaden endpoints direccionales y de sincronización (`/execute/{direction}`, `/preview-synchronize`, `/{id}/synchronize`) y se regenera OpenAPI sin rutas duplicadas.
- La sincronización incremental basada en deltas/trazas previas, incluyendo propagación selectiva de borrados, queda preparada en el contrato pero no se presenta todavía como capacidad operativa; Fix38 implementa sincronización batch determinista.

## R5C13 Fix43 — managed transformation toolchain discovery and diagnostics

- Adds server-owned managed toolchain bundle discovery for ATL, ETL, Acceleo, EGL, Java, Xtend, Python and Go while preserving explicit runtime command precedence.
- Adds safe `toolchain.json` manifests with bundle-scoped `${bundle}` resolution, size limits and symbolic-link/traversal rejection.
- Adds bounded `--hydra-describe` bridge diagnostics for bridge/toolchain versions, protocol compatibility, transformation kinds and directions.
- Runtime diagnostics are cached only against the resolved command fingerprint; changing configuration invalidates the cached health automatically.
- Diagnosed incompatible bridges fail closed for native execution. Legacy configured bridges can still execute without claiming verified readiness.
- Adds frontend diagnosis controls and path-free health/version/capability feedback; native commands and installation paths remain server-only.

## R5C13 Fix44 — trusted transformation toolchain provisioning

- Adds optional, server-owned package provisioning for ATL, ETL, Acceleo, EGL, Java, Xtend, Python and Go toolchain bundles.
- Keeps provisioning disabled by default and never accepts browser-provided executables, archives, commands or filesystem paths.
- Verifies exact SHA-256 coverage, file-count/byte quotas, language slots, safe relative paths and symbolic-link rejection before publishing a package.
- Publishes through staging/backup slots, preserves administrator-managed bundles, supports optimistic package replacement and rolls back a newly diagnosed incompatible bridge.
- Adds catalog/install/uninstall API contracts and a responsive provisioning UI integrated with native runtime diagnosis.

## R5C13 Fix45 — official managed transformation bridge scaffolds
- Adds reproducible, trusted official HYDRA bridge bundles for ATL, ETL, Acceleo, EGL, Java, Xtend, Python and Go on top of the Fix44 provisioning lifecycle.
- Introduces the bridge self-description schema 1.1 with separate tool and execution availability while retaining schema 1.0 compatibility.
- Keeps third-party engines fail-closed when their real runtime payload is absent and executes official launchers with the exact server JVM through `${java}`.

## R5C13 Fix46 — independent managed Java, Python and Go M2M execution
- Promotes Java, Python and Go from canonical-bundle-only parity to independent native M2M parity for the demonstrated exact direct/constant attribute subset.
- Adds a neutral `native/native-plan.json` emitted only after fail-closed compatibility checks; guards, expressions, trace-equivalent relations and non-default value policies remain blocked instead of approximated.
- Adds standalone official Java, Python and Go runtimes that consume the bridge request and native plan, execute the mapping in the target language and return canonical model evidence for parity comparison.
- Official bridge 1.1.0 gains bounded Java source, Python and Go source execution backends. Commands and runtime files remain server-owned and bundle-scoped.
- FORWARD keeps protocol 1.0 compatibility and REVERSE uses protocol 1.1; directional export continues to rely on explicit HYDRA reverse rules rather than inferred inverses.
- M2T export for Java/Python/Go remains a canonical runtime bundle until an independent native text-generation backend is implemented. Xtend also remains canonical-bundle-only.
- The managed capability registry now advertises independent M2M FORWARD/REVERSE parity for Java, Python and Go without enabling full external bidirectional or incremental synchronization.

## R5C13 Fix47 — independent managed Java, Python and Go M2T execution
- Extends the official Java, Python and Go managed runtimes with independent M2T execution instead of delegating text generation to HYDRA's canonical runtime bundle.
- Emits an exact neutral `native/native-plan.json` for guard-free generation rules, preserving `contextTypeId`, output/content templates and the explicit `FAIL`/`REPLACE` collision policy.
- Reproduces the canonical token contract for `{{id}}`, `{{classId}}` and `{{attr:<stable-id>}}`, including null-to-empty rendering and portable relative-path validation.
- Keeps M2T fail-closed when a generation guard is present or its source context class cannot be resolved; no guard is silently dropped and no heuristic template conversion is introduced.
- Official bridge 1.2.0 advertises M2M and M2T for Java, Python and Go. M2T is FORWARD-only; M2M retains explicit FORWARD/REVERSE support.
- Native M2T evidence is returned through `native-result.json` as logical artifact paths plus isolated workspace files, so the existing parity service compares real target-language execution against the canonical text runtime.
- Xtend remains canonical-bundle-only and ATL/ETL/Acceleo/EGL capabilities are unchanged.


## R5C13 Fix48 — Managed Xtend native substrate and bridge-version coherence

- Xtend now exports the same exact managed `native/native-plan.json` subset used by Java, Python and Go for M2M and guard-free M2T instead of being reported as canonical-runtime parity.
- `TransformationNativeRuntimeSupport` marks Xtend as `NATIVE_EXPORT`; execution remains fail-closed until a trusted full Eclipse Xtend runtime payload is provisioned.
- The official bridge adds a bounded `JVM_CLASSPATH` backend. Its classpath is read only from a server-owned manifest, every entry is bundle-relative, regular, non-symbolic and path-confined, and the main class is validated before launch.
- The core Xtend package intentionally does not bundle Eclipse Xtend third-party binaries. It therefore self-describes with `toolAvailable=false` / `executionAvailable=false` until `engine/classpath.txt` and `engine/hydra-xtend-runtime.jar` are supplied by a trusted full package.
- All eight official bridge profiles and generated package/toolchain manifests are aligned on bridge version `1.3.0`, closing a latent self-description incompatibility caused by stale profile versions.
- Java, Python and Go native M2T execution was regression-checked again against the 1.3.0 bridge and continues to produce byte-identical expected evidence.

## R5C13 Fix49A — testCompile recovery
- Corrected invalid no-argument Optional.orElse() calls in TransformationNativeRuntimeExecutorTests; they now use orElseThrow().
- Made the native bridge subprocess fixture self-contained in TransformationNativeRuntimeExecutorTests so test compilation does not depend on implicit auxiliary-source discovery.
- Updated Java/Python runtime capability assertions to reflect the current M2M + M2T support contract.

## R5C13 Fix49B — Actionable transformation diagnostics
- Bidirectional validation no longer emits a generic missing-direction error plus a second duplicate correspondence error for the same root cause.
- Incomplete correspondences are reported once and are anchored to the concrete existing rule and `$.rules[n].correspondenceId`.
- Diagnostics include the rule name/id, correspondence id, direction, source type and target type(s).
- Repair guidance includes a case-specific FORWARD/REVERSE rule skeleton using the actual correspondence and type identifiers.
- Transformation diagnostic rendering now uses title as the short summary, message as the concrete reason and solution as the repair action.
- Spanish runtime localization preserves the original detailed message if a new diagnostic does not yet have a catalogue entry instead of replacing it with a generic sentence.
- Added regression coverage for actionable bidirectional diagnostics and localization detail preservation.

## R5C13 Fix49C — Complete actionable transformation diagnostics
- Extends Fix49B from the bidirectional case to the complete static transformation checker surface. Diagnostics now resolve the concrete endpoint, rule, generation rule, target pattern and binding whenever the current document provides that context.
- Unknown classes, attributes and relations report the failing stable ID together with bounded real alternatives from the currently resolved metamodel instead of asking the user to “review the associated element”.
- Type and cardinality diagnostics report the actual source/target types and multiplicities and propose explicit conversions, reducers or expression mappings only when they preserve the declared semantics.
- Missing required target properties identify the target class/feature and explain which mapping must be created; duplicate IDs, target aliases and duplicate writes identify the colliding element and give a concrete renaming/consolidation strategy.
- Expression/guard diagnostics preserve the semantic expression checker detail and add the owning rule/binding/generation context instead of replacing the lower-level evidence with a generic wrapper.
- Adds root-cause suppression: an unresolved rule source/target type suppresses dependent unknown-feature diagnostics for that same rule, and duplicate diagnostics with the same code/rule/binding/path are collapsed.
- Metamodel fingerprint drift now names the endpoint and metamodel, shows bounded previous/current fingerprints and tells the user to re-check type IDs, feature IDs, multiplicities, relation mappings and representative preview models before accepting the refreshed fingerprint.
- Export diagnostics are enriched for the complete native/canonical adapter surface. Unsupported guards, value policies, multi-target rules, missing features/context types, unsupported identity tokens, output-path problems and serialization failures now explain the exact rule/binding/template involved and the safe alternative.
- `CANONICAL_SERIALIZATION_FAILED` no longer ends with “Correct the canonical definition”; the diagnostic preserves the serializer evidence and distinguishes a definition failure from an internal serialization-boundary failure.
- The transformation designer’s location field distinguishes rule and binding IDs, and the export panel renders separate “Why” and “How to fix it” sections.
- Spanish runtime fallback no longer prepends generic “Incidencia de…” boilerplate to rich transformation diagnostics that have no dynamic translation pattern; it preserves the full case-specific evidence and stable IDs.
- Adds `verify_r5c13_fix49c_actionable_transformation_diagnostics.py` with complete checker/export-code coverage, cascade/deduplication assertions, UI evidence checks and regression-test coverage. Historical Fix38/Fix46/Fix47/Fix48 gates were made forward-compatible with the later rule-pair and bridge-version contracts.

## R5C13 Fix50 — global actionable diagnostics, collision-aware canvas layout and grammar visual-runtime parity
- Extends actionable error guidance beyond transformations. Shared frontend diagnostic support and local input-error explanation now identify the concrete subject/path/value when available, classify required/reference/duplicate/cardinality/mapping/cycle/flow/expression/type/syntax/size failures, preserve unknown technical evidence, and provide ordered case-oriented repair steps instead of generic “review the element” advice.
- Backend `ApiErrorAdvice` mirrors the same policy for uncatalogued API failures so remaining workbenches receive a useful what/where/why/how payload even before a feature-specific mapping exists. Existing specific advice remains authoritative.
- The shared layered canvas layout now performs bounded edge-vs-node collision avoidance after Sugiyama placement. Unrelated nodes that intersect an orthogonal connection corridor are displaced on the secondary axis and layers are re-separated, reducing unreadable connections through elements without allowing node overlap.
- Metamodel auto-layout persists explicit orthogonal route points after repositioning, so rendered relations use the collision-aware geometry instead of reverting to direct lines that can cross nodes.
- Grammar textual customization now projects persisted parser-rule styles and contextual help onto the actual literal lexemes used by generated model editors, and terminal customizations onto runtime identifier/number tokens. This closes the previous gap between grammar-definition symbols and tokens visible in model instances.
- Monaco previews and HYDRA textual model editors now remeasure fonts after customization changes, enforce the selected family/size/line-height on rendered view lines, and resolve hover context using the custom tokenizer rather than only Monaco word boundaries. Appearance and contextual-information changes are therefore visible immediately in the preview and in grammar-backed model sessions.
- Generated VS Code/editor runtimes receive terminal fallbacks for identifier/number styles and contextual information, keeping exported textual editors aligned with the HYDRA preview instead of dropping per-terminal customization.
- Includes TypeScript compatibility repairs already present in the supplied working snapshot path so the complete frontend project type-checks cleanly with the installed toolchain.

## R5C13 Fix51 — unified HYDRA Project model and IDE-style Project Explorer
- Replaces the former "all capabilities enabled" creation behavior with one stable `HYDRA Project` model plus non-restrictive creation presets. Presets only select the initial capability set; they never define incompatible project types.
- Adds presets for empty, graphical language, textual DSL, hybrid language, modeling+validation, M2M, M2T, T2M, complete editor and complete MDE workflows. The wizard retrieves the catalog from the backend so frontend/backend cannot drift.
- Adds an explicit capability dependency graph. Enabling quick fixes also enables validation; textual modeling/editor features require a grammar; visual modeling/editor features require a metamodel; event flows require events; T2M resolves both grammar and metamodel requirements; generation requires editor definitions. Dependencies are revalidated server-side.
- Adds `GUIDED` and `FREE` project experience modes. Both persist the same canonical project model; the mode is UI guidance metadata rather than a separate project format.
- Evolves `modeling-project.json` to schema 1.1 with `createdFromPreset`, `experienceMode` and an optional `primaryArtifact`. Existing schema-1.0 descriptors remain readable and preserve the previous full-capability/free-mode semantics.
- Physical and logical project roots are now capability-aware. New projects create only the folders needed for their selected purpose, while adding capabilities later materializes only newly required roots. Disabling a capability never deletes existing project files.
- Adds canonical `grammars/` and `transformations/` roots without breaking the existing `ProjectLayout` record format, allowing current projects to migrate without descriptor rewrites.
- Adds `/api/projects/catalog` and `/api/projects/active/configuration` for a single authoritative preset/capability catalog and post-creation capability management.
- Adds a Project Capabilities dialog that can add/remove capabilities after creation, resolves required dependencies immediately, and lets the user switch between guided/free modes.
- Project Explorer context menus are now node-aware and IDE-style. Project/folder/file selections expose the applicable subset of new file/folder, upload, open, cut/copy/paste, duplicate, rename, download, relative/absolute path copy, selection ZIP export, repository export, refresh, properties and delete actions.
- Project-root context actions additionally expose project ZIP export, capabilities/properties, repository/cloud integration, workspace synchronization, close and complete-project deletion.
- Files can be marked as the project's primary artifact directly from the context menu; the selection is persisted in the project descriptor for later Home/preview/generation flows.
- Adds read-only Properties UI for project, folder and file metadata (kind, ownership, status, size, modified timestamp, editability, ETag, preset, mode and primary artifact).
- Adds Spanish UI coverage for the new creation presets, capability management and context-menu operations.
- Adds backend preset/dependency regression tests and a frontend architectural contract test covering the unified project wizard, post-creation capability evolution and standard context-menu commands.

## R5C13 Fix51A — Project README contract recovery
- Recuperada la sección `## Estructura` en el README generado para proyectos locales.
- Las raíces documentadas se calculan desde las capacidades activas; `COMPLETE_MDE` vuelve a incluir `editors/` y `generated/`.
- Se mantiene la creación diferida de subcarpetas especializadas.

## R5C13 Fix52A — frontend regression recovery
- Prioriza los diagnósticos específicos de mapping/binding/correspondencia frente al clasificador genérico de referencias `unknown/not found`, conservando el valor concreto y una receta ordenada de revisión de tipo origen, tipo destino, atributo origen, atributo destino, dirección y conversión.
- El dashboard Home evita repetir de forma idéntica el nombre corto del componente principal: la tarjeta Continuar conserva el nombre del archivo y el resumen del proyecto muestra su ruta relativa dentro del proyecto.
- Recupera los contratos frontend `inputErrorExplanation.test.ts` y `HydraHome.test.tsx` detectados por la suite completa de Windows sin alterar la arquitectura project-first de Fix52.

## R5C13 Fix54 — Home card consistency, deeper help and preset-focused project creation

- Homogenizes Home dashboard cards around a shared visual/card structure while preserving their specific actions.
- Adds contextual help to internal Home sub-blocks: action cards, project summary metrics, main component, current-context selector, project-area cards and recent-project cards.
- Adds explicit guidance for every HYDRA project preset: what HYDRA prepares, recommended working mode, suggested next steps and customization advice.
- Quick-start cards now open a preset-focused creation flow instead of immediately showing every project type again. Users can explicitly choose “Change starting point” when needed.
- Keeps the general Projects entry point broad, while Home entry points remain task-focused.
- Adds regression coverage for all ten presets and the shared Home card/help contract.

## R5C13 Fix55 — full-width Home and restored layer navigation
- Removes the inherited fixed Home content width that kept the dashboard centered inside a narrow 1120px column. Home now consumes the complete editor width remaining after the Project Explorer and its own navigation rail.
- Restores the useful Home layer-navigation model alongside the new project-first dashboard: Home dashboard, language and behavior, models and connections, import and delivery, project tools, and current preparation.
- Keeps nested dashboard shortcuts in the rail for start/continue, quick creation, current work and recent projects.
- Makes the Home rail explicitly visible and independently styled on desktop while keeping a compact horizontal navigation fallback on narrow screens.
- Fixes clipped quick-start/project-area copy by overriding the shared pill-button `white-space: nowrap` rule, using readable minimum card widths and allowing normal multiline wrapping.
- Makes card/module grids responsive to the actual available width rather than forcing narrow fixed columns.
- Adds a dedicated Home layout regression contract for full-width content, visible layer rail and multiline card copy.

## R5C13 Fix56 — navegación global a la izquierda del explorador

- La navegación principal de HYDRA se vuelve a situar como primera columna del shell, a la izquierda del Explorador de proyectos.
- Home deja de renderizar una segunda barra de navegación dentro de su contenido.
- Cuando Inicio está activo, la barra global incorpora accesos a Empezar o continuar, Inicio rápido, Áreas del proyecto, Trabajo actual y Trabajo reciente.
- Las áreas principales quedan agrupadas en Lenguaje y comportamiento, Modelos y conexiones, Importación y entrega y Estado actual y herramientas.
- Los accesos de Home usan un evento de navegación para desplazar el dashboard sin duplicar la barra lateral.
- Se mantienen el ancho completo y el ajuste multilínea de las tarjetas introducidos en Fix55/55A.

## R5C13 Fix57 — Transformations UX and JavaScript/TypeScript execution

- Replaces low-level transformation UI wording (toolchains, bridges, native parity) with user-facing concepts such as execution support, execution environment and generated-transformation verification.
- Adds metamodel-aware contextual help for source/target metamodels, class mappings, target attributes, relation mappings and rule conditions.
- Adds JavaScript and TypeScript to transformation export/runtime support for exact M2M and guard-free M2T subsets.
- Adds trusted Node.js-based managed execution packages for JavaScript and TypeScript, with TypeScript source shipped beside its checksum-verified executable companion.
- Bumps official transformation execution packages to 2.1.0 and keeps ATL/ETL/Acceleo/EGL/Xtend fail-closed when their external engines are absent.

## R5C13 Fix58 — backend regression recovery and architecture split

- Restores the uniform architecture limits without raising any ceiling:
  - Home/shell Spanish copy moved from `translationCatalog.ts` into `homeShellSpanishCatalog.ts`.
  - Project-first Home evolution styles moved from `application-shell.css` into `application-shell-home-dashboard.css`.
  - Frontend contracts read the split style layers explicitly.
- Hardens event-contract replacement:
  - validates both event ETags before mutating either document;
  - treats an identical PUT as an idempotent no-op and preserves both ETags;
  - rewrites only the document that actually changed when one side of the pair is unchanged;
  - keeps rollback semantics when both event documents change.
- Does not relax `StructuralArchitectureGuardTests` or add file-specific exceptions.

## R5C13 Fix58A — Frontend Spanish coverage recovery

- Adds the missing Spanish copy for the recent-projects help in Home.
- Adds the missing Spanish copy for directional execution comparison and execution comparison details in Transformations.
- Fixes invalid single-quoted transformation help literals containing `HYDRA's` by using valid double-quoted branches.
- Routes the execution comparison field label/help through `t(...)` so visible help remains covered by the production i18n contract.
- TypeScript frontend check: PASS.
- Static regression coverage for the four reported strings: 4/4 PASS.
- Canonical manifest verified: 3860/3860 files.

## R5C13 Fix58B — Project architecture contract synchronization

- Synchronizes the project architecture contract with the current area-oriented terminology.
- Replaces stale literal expectations for `Customize capabilities`, `Project capabilities…` and `Set as primary artifact` with the current UX language.
- Tests dependency closure using the actual project-area dependency implementation instead of an obsolete display sentence.
- Adds regression coverage for contextual Project Manager entry points from Home.
- Ships the current project creation/explorer source files cumulatively to recover installations where earlier frontend hotfixes were applied selectively.

## R5C13 Fix59 — Project Manager asynchronous/catalog recovery

- Validates `/api/projects/catalog` payloads before accessing presets/capabilities, preventing malformed or endpoint-mismatched data from unmounting Project Manager.
- Separates background creation-catalog failures from the active create/open/import operation so local validation and access-flow diagnostics are not overwritten or duplicated.
- Makes preset lookup defensive throughout the dialog and restores recommended project areas explicitly when manual customization is disabled.
- Hardens actionable API error parsing for already-consumed `Response` bodies; failed `clone()` operations now fall back to stable HTTP guidance instead of leaking `Body has already been read`.
- Repairs Project Manager test mocks so every endpoint/request receives a fresh `Response`, eliminating the invalid shared-body fixture that produced cross-request failures after catalog loading was introduced.
- Adds regression coverage for consumed-response fallback.
- Removes duplicate Spanish catalogue keys already present in the supplied snapshot and fixes the escaped Windows example path, allowing the affected TypeScript surface to compile cleanly.
- Focused semantic TypeScript and ESLint checks pass. Full frontend syntax parsing also passes. Vitest execution remains delegated to the Windows runner because the supplied `node_modules` contains the Windows-only Rolldown native binding.

## R5C13 Fix60 — portability, release-quality and regression hardening

- Centralizes browser-facing backend URLs through the configurable HYDRA backend base. Project downloads/visual assets, model downloads, metamodel exports, operation-behavior exports and operation-runtime catalog downloads no longer assume same-origin `/api`; editor visual assets also use `backendFetch` instead of raw `fetch`.
- Adds a dedicated backend URL regression test covering same-origin behavior, configured backend bases with trailing slashes and non-API/absolute URLs.
- Hardens Project Explorer change monitoring: replaces the permanent 5-second interval with bounded recursive polling, pauses network work while the tab is hidden, applies exponential backoff up to 60 seconds after failures and reports recovery without flooding the user.
- Fixes a duplicate `sourcePrefix` declaration in Project Explorer rename handling that could break TypeScript compilation.
- Removes the obsolete duplicate `AppearanceSettingsContext.tsx`; the canonical settings facade/provider pair remains the single implementation path.
- Makes transformation-toolchain staging cleanup observable through `HydraBestEffortDiagnostics` instead of silently swallowing `IOException`.
- Updates concurrency and synchronization inventories for the transformation native runtime and toolchain provisioning service; the synchronization verifier now detects the Java keyword with a word boundary instead of producing false positives from identifiers such as `synchronizedState`.
- Optimizes `HydraDomTranslator` mutation batching by collapsing queued descendants below already-pending roots, reducing repeated DOM traversal while preserving the existing translation fallback behavior.
- Restores grammar class-diagram geometry persistence: the shared read-only metamodel canvas now feeds the current React Flow nodes back into projection so local position/measurement survives semantic refresh; drag/resize and persistence contracts were updated for the shared-renderer architecture.
- Closes the CSS quality budget without increasing thresholds: repeated literal colors are centralized in shared palette tokens and redundant `!important` declarations are replaced by explicit selector specificity. Final budget: 319/346 literal colors and 260/263 `!important` declarations.
- Closes backend Spanish method-documentation coverage at 9673/9673 without suppressions or debt markers.
- Removes obsolete semantic-context test tombstones, including the nested `src/test/test` duplicate, and updates the C7 contract to require the real XYFlow module plus production use of `useNodesState`/`applyNodeChanges` rather than an obsolete partial-mock workaround.
- Makes retained Fix46/Fix47/Fix48 transformation validators forward-compatible with later official bridge releases while continuing to execute the retained historical bridge packages.
- Updates the visual full-stack regression to validate the current iterative bounded event queue (`maximumQueuedEvents=4096`) instead of the removed recursive `processingDepth` implementation; executable stress remains mandatory.
- Aligns the external-process security gate with the neutral `HydraExternalProcessRunner`, where command validation and minimal-environment enforcement now live.
- Adds Maven output-isolation preflight to the Windows validation entry point and renames that entry point to the current V4.32.107 release.
- Removes the previous V4.32.106 release note from the pre-GA source tree and keeps only current release documentation.
- Static release gates pass for pre-GA hygiene, warning/debt, exception boundaries, concurrency, backend documentation, frontend architecture, control convergence, Spanish/i18n coverage, CSS, accessibility, OpenAPI, external-process security, release metadata, current invariants and Maven output isolation.
- Grammar/export regression gates pass for V4.33.04, V4.33.06, V4.33.08 and V4.33.11–V4.33.15, including the corrected visual full-stack 50/50 contract; retained transformation gates Fix31, Fix38, Fix46–Fix49C and Fix57 also pass.

## R5C13 Fix61 — guided workflow semantic color contract recovery

- Repairs the frontend regression reported by the Windows recovery runner in `GuidedWorkflowCoverage.test.ts`.
- Keeps Fix60's CSS deduplication intact: guided completion/error indicators continue to consume semantic design-system tokens instead of reintroducing duplicated literal colors into component CSS.
- Updates the visual contract so it verifies the actual `.hydra-guided-status-icon` rules, their semantic success/danger token fallbacks, the canonical palette values (`#15803d` and `#dc2626`) in `hydra-theme.css`, and the circular `50%` radius independently of whitespace/minification.
- Prevents the contract from becoming coupled again to formatting details such as `border-radius: 50%` versus `border-radius:50%`.

## R5C13 Fix62 — editor visual asset backend-fetch contract recovery

- Repairs the reproducible frontend regression in `editorVisualAssetUrl.test.ts` after editor visual asset loading was migrated from raw `fetch` to the shared `backendFetch` boundary.
- Keeps the portable/configurable backend behavior introduced by Fix60; the production loader continues to resolve the backend URL and propagate a HYDRA correlation id instead of reverting to a one-argument raw fetch call.
- Updates the regression test to validate the resolved editor-asset URL together with the request options and `X-Hydra-Correlation-Id`, so observability is now part of the protected contract.
- Restores global `fetch` deterministically with `afterEach`, preventing a failed assertion from leaking a stub into later tests.

## R5C13 Fix67 — checkbox semantic palette contract recovery

- Repairs the reproducible frontend regression in `src/settings/GlobalControlStyleContract.test.ts` reported by the Windows full-suite runner.
- Keeps the production checkbox styling on the shared `--hydra-palette-blue-600` token instead of reintroducing the duplicated `#2563eb` literal into `settings.css`.
- Updates the contract to validate the high-specificity checked-state override and independently verifies that the canonical theme resolves `--hydra-palette-blue-600` to `#2563eb`.
- Preserves the existing checked-mark visibility contract and keeps the CSS tokenization/budget improvements intact.
## R5C13 Fix71 — aggressive ZIP semantic equality recovery
- Repairs `AggressiveArtifactGenerationTests.metamodelBundleIsDeterministicParseableAndContainsOnlyRequestedArtifacts`.
- Keeps the strong binary reproducibility assertion introduced in Fix70 (`second` ZIP bytes must equal `first`).
- Replaces `Map<String, byte[]>.equals` semantics, which compare each `byte[]` by object identity, with explicit deep byte-content comparison for every decompressed ZIP entry.
- No production exporter behavior is weakened or changed: canonical format order, deduplication and fixed ZIP timestamps remain enforced.

## R5C13 Fix72 — aggressive Vitest pool-health recovery
- Extends worker-bootstrap recovery from the dedicated aggressive acceptance campaign to every shuffled full-suite batch.
- Retries the exact same batch with an alternate isolated pool before any per-file isolation, preserving file order and product semantics.
- Tracks pools that fail to bootstrap as degraded for the remainder of the run so a pool that just timed out is not immediately selected again for the next pass.
- Keeps infrastructure and product failures strictly separated: assertions, test timeouts, syntax/type failures and explicit OOM signals remain fatal and never trigger pool fallback.
- Records requested/actual pools, recovery attempts, infrastructure events and degraded pools in the aggressive JSON report (schema 1.1).
- Suppresses recovered worker stack dumps from the normal console path and emits concise recovery warnings; full Vitest output remains available in the JSON diagnostic tail and is printed when recovery is exhausted or a product failure occurs.
- Adds regression coverage for degraded-pool prioritization while preserving the existing mixed assertion+worker-timeout safety contract.

## R5C13 Fix82 — lifecycle transaccional recuperable y dependencias nuevas

- Integra transformaciones, pruebas/sincronizaciones, gramáticas, snapshots/activaciones y sesiones textuales en el análisis y resultado del borrado de metamodelos.
- Corrige `deletedFiles`/`pendingFiles` para que reflejen también artefactos de las capas nuevas y no oculten residuos bloqueados por el sistema operativo.
- Añade un journal atómico previo al borrado canónico y un endpoint explícito de recuperación idempotente para completar limpiezas parciales sin repetir la eliminación ETag del metamodelo.
- Permite recuperar de forma segura un journal `PREPARED` únicamente cuando el documento canónico ya no existe, cubriendo interrupciones entre el borrado primario y la actualización del journal.

## R5C13 Fix83 — persistencia local segura y evolución de esquemas

- Añade bloqueo de instancia mediante `FileChannel.tryLock()` para impedir que dos procesos HYDRA modifiquen simultáneamente el mismo almacenamiento local.
- El lock se deriva por defecto de la ruta real configurada para `dsl.storage.metamodels`, con override opcional y desactivación determinista en tests.
- Introduce un registro neutral de evolución de esquemas JSON para la persistencia canónica; las transformaciones de esquema son secuenciales, acotadas y no ambiguas.
- Antes de publicar un documento evolucionado, conserva una copia de los bytes anteriores y sustituye el documento de forma atómica.
- Reutiliza la infraestructura existente `HydraRecoveryService`; no se introduce un segundo sistema de backup paralelo.

## R5C13 Fix84 — trabajos transversales, capacidad y API operativa

- Añade un registro transversal y acotado de trabajos largos con estado, progreso, fase, correlación, cancelación y capacidad.
- Integra los análisis asíncronos de importación de metamodelos con el registro compartido sin permitir que una saturación de observabilidad rechace la operación de dominio.
- Expone `/api/jobs` para consulta/cancelación y añade la capacidad agregada al endpoint de runtime.
- Publica `X-Hydra-Api-Version` en las rutas `/api` y documenta una política de compatibilidad sin romper las rutas actuales.
- Los perfiles `headless-server` y `production` quedan fail-closed a loopback salvo `HYDRA_SERVER_ADDRESS` explícito mientras no exista identidad/RBAC multiusuario.

## R5C13 Fix85 — convergencia y limpieza definitiva de release

- Elimina físicamente el kernel semántico legacy de `validation.expression`; permanece únicamente la capa de aplicación/persistencia compatible y `semantic.expression` es la única fuente del kernel.
- Retira del POM la cuarentena Maven temporal de Fix81 y elimina tests/tombstones incompatibles, incluida la copia anidada `src/test/test`.
- Actualiza los inventarios de concurrencia/sincronización para el orquestador y el lock de proceso.
- Corrige el gate de terminación de procesos para validar la delegación actual a `HydraExternalProcessRunner` en vez de exigir una implementación ya refactorizada.
- Añade el gate acumulativo `verify_r5c13_fix82_85_consolidation.py`, con 29 contratos de lifecycle, persistencia, concurrencia, seguridad, API y limpieza.

## R5C13 Fix85S — recuperación de lifecycle y lock portable en Windows

- Reconcilia físicamente los artefactos de un journal de borrado `PREPARED` cuando el metamodelo canónico ya ha desaparecido, de modo que la recuperación solo reintenta residuos que siguen presentes antes de la limpieza final.
- Mantiene el comportamiento existente para journals `PRIMARY_DELETED` y `RECOVERY_REQUIRED`, preservando la recuperación idempotente de residuos ya inventariados.
- Separa el fichero legible de metadatos `.hydra.lock` de la guardia interna `.hydra.lock.guard` que recibe el `FileLock` del sistema operativo; esto conserva la exclusión entre procesos sin impedir la lectura de diagnóstico en Windows.
- Elimina los metadatos antes de liberar la guardia para evitar una carrera en la que una instancia saliente pudiera borrar la identidad publicada por la siguiente instancia.
- Activa explícitamente el perfil `test` en los dos tests `@SpringBootTest`, haciendo efectiva la configuración determinista de `application-test.properties` y evitando que el lock global interfiera entre contextos de prueba.
- Añade un gate de regresión específico para proteger la reconciliación `PREPARED`, la separación metadata/guard y la activación real del perfil de test.

## R5C13 Fix85T — recuperación del contexto Spring para el selector de rutas

- Corrige la causa raíz de los ocho errores de `EditorApplicationTests` y `SystemEndpointsTests`: con el perfil `test`, `native-path-picker-enabled=false` desactivaba el selector Swing pero el fallback `HeadlessProjectPathPicker` no llegaba a registrarse como bean.
- Sustituye la condición basada en `@ConditionalOnMissingBean` sobre el componente escaneado por condiciones mutuamente excluyentes basadas en `dsl.projects.local.native-path-picker-enabled`.
- Mantiene Swing como comportamiento por defecto de escritorio y registra el fallback headless en `test`, `headless-server`, `production` o cuando el usuario desactiva explícitamente el selector nativo.
- Añade `ProjectPathPickerConditionalBeanTests` para verificar los tres escenarios: `false`, `true` y propiedad omitida.
- Añade `verify_r5c13_fix85t_project_path_picker_context.py`: 10/10 contratos estructurales PASS.

## R5C13 Fix86 — evolución planificable de esquemas persistidos

- Extiende `HydraSchemaEvolutionService` con planificación determinista hasta una versión destino explícita, detección de rutas inalcanzables y rechazo de ciclos/ambigüedades al inicializar el registro de migraciones.
- Añade `HydraSchemaEvolutionPlan` y el endpoint de solo lectura `/api/schema-evolution/plan`, permitiendo comprobar una migración antes de modificar almacenamiento.
- Mantiene la integración existente con `AtomicJsonDocumentStore`: las migraciones reales siguen publicándose atómicamente y conservan la primera copia previa al cambio de esquema.
- Añade regresiones para planificación parcial, destino inalcanzable y ciclos.

## R5C13 Fix87 — ejecución transversal y acotada de trabajos largos

- Añade `HydraJobExecutionService`, `HydraJobContext` y un `ThreadPoolTaskExecutor` global con concurrencia y cola explícitamente acotadas.
- La saturación usa `AbortPolicy`; no se crean colas sin límite ni pools nuevos por operación.
- Integra el executor transversal en el escaneo asíncrono del registro de artefactos, compilación de gramáticas y provisionado/desinstalación administrada de toolchains.
- Conserva progreso, correlación y cancelación cooperativa mediante `HydraJobOrchestrator` y añade límites configurables `hydra.jobs.maximum-concurrent` / `maximum-queued`.
- Añade pruebas focales de finalización y propagación de fallos al estado del trabajo.

## R5C13 Fix88 — journal genérico de transacciones de artefactos

- Introduce `HydraArtifactTransactionService` con estados `PREPARED`, `RUNNING`, `RECOVERY_REQUIRED`, `COMPLETED` y `ABORTED` para mutaciones multi-artefacto.
- Publica el journal de forma atómica antes de ejecutar la mutación y conserva evidencia recuperable cuando la operación falla.
- Integra transacciones en registro/escaneo de artefactos y en `CREATE`, `UPDATE` y `DELETE` de transformaciones.
- Expone `/api/artifact-transactions/pending` y descarte explícito de journals pendientes.
- Las lecturas de journals utilizan `BoundedIo`; no se permite `Files.readAllBytes` en producción.

## R5C13 Fix89 — relaciones tipadas e impacto transitivo del grafo de artefactos

- Extiende las dependencias con `HydraArtifactRelationKind` (`USES_METAMODEL`, `GENERATED_FROM`, `VALIDATED_BY`, `TRANSFORMS_TO`, `COMPILED_FROM`, `HAS_TRACE`, `USES_GRAMMAR`, etc.) conservando compatibilidad con descriptores anteriores.
- Materializa aristas tipadas en `HydraArtifactDependencyGraph` y añade cálculo de dependientes directos/transitivos, bloqueantes y orden postorden seguro de eliminación.
- El grafo detecta además dependencias obligatorias cuya versión mínima ya no es satisfecha.
- Expone `/api/artifact-registry/impact/{id}` y regresiones de impacto transitivo/orden de eliminación.

## R5C13 Fix90 — compatibilidad y versionado de artefactos

- Añade `HydraArtifactCompatibilityService` y estados `COMPATIBLE`, `REVALIDATION_REQUIRED`, `MIGRATION_REQUIRED` e `INCOMPATIBLE`.
- Evalúa dependencias ausentes, estados inválidos/stale y versiones mínimas declaradas sin mezclar disponibilidad física con compatibilidad semántica.
- `HydraArtifactVersionPolicy` compara versiones numéricas punteadas mediante `BigInteger`, sin límites artificiales de número de componentes, y falla de forma conservadora en versiones opacas.
- Expone `/api/artifact-registry/compatibility/{id}` y añade regresiones para migración y revalidación.

## R5C13 Fix91 — consistencia incremental/bidireccional antes de aplicar deltas

- Añade `TransformationSynchronizationConsistencyValidator` y lo ejecuta antes de calcular/aplicar deltas incrementales.
- Rechaza baselines cuyo ID de modelo o metamodelo no coincide con el modelo actual y exige rebase cuando cambia la revisión/ETag del metamodelo.
- Valida la orientación FORWARD/REVERSE de la traza baseline y que todos sus elementos sigan existiendo.
- Rechaza correspondencias ambiguas uno-a-varios o varios-a-uno dentro de la misma correspondencia antes de modificar cualquiera de los lados.
- Añade regresiones para identidad, cambio de revisión y ambigüedad de traza.

- Gate acumulativo `verify_r5c13_fix86_91_platform_evolution.py`: 36/36 PASS.

## R5C13 Fix85U — ProjectPathPicker bootstrap robusto

- Sustituye los dos `@Component` condicionales del selector de rutas por una `ProjectPathPickerConfiguration` explícita con dos `@Bean` mutuamente excluyentes.
- `ProjectController` inyecta el selector mediante `ObjectProvider<ProjectPathPicker>` y dispone de un fallback `HeadlessProjectPathPicker`, por lo que una configuración externa incompleta ya no puede impedir el arranque del `ApplicationContext`.
- Las implementaciones Swing/headless quedan desacopladas del component-scan y se refuerzan las regresiones del perfil `test`.
- Mantiene Fix86–Fix91 sin cambios funcionales y evita que el fallo opcional del selector bloquee los endpoints de sistema.


## R5C13 Fix85V — bootstrap Spring sin sustituir el perfil de ejecución

- Corrige la causa de persistencia de los ocho errores de `EditorApplicationTests` y `SystemEndpointsTests`: los `@SpringBootTest` activaban `@ActiveProfiles("test")`, sustituyendo el perfil por defecto `desktop-local` completo solo para desactivar el lock de instancia.
- Retira `@ActiveProfiles("test")` de ambos tests de contexto completo y conserva `spring.profiles.default=desktop-local`, que es el modo de arranque real que ya había demostrado cargar correctamente el contexto principal.
- Desactiva únicamente `hydra.instance-lock.enabled=false` como propiedad inline de esos tests, junto con `dsl.storage.create-directories=false`, evitando que dos contextos Spring independientes compitan por `.hydra.lock` sin alterar el resto de la configuración de escritorio.
- Mantiene `application-test.properties` para pruebas focales que sí necesiten el perfil `test`; deja de utilizarlo como sustituto del perfil real en los smoke tests de contexto completo.
- Conserva las defensas de Fix85U para `ProjectPathPicker`, de modo que el selector opcional tampoco pueda tumbar el contexto aunque una configuración externa suprima su bean.
- Añade `verify_r5c13_fix85v_spring_test_bootstrap.py`, que protege la separación entre perfil real de arranque y overrides mínimos de los tests de contexto.

## R5C13 Fix85W — bootstrap real del lock de instancia

- Corrige la causa raíz extraída de los `surefire-reports`: Spring intentaba instanciar `HydraInstanceLock` mediante constructor por defecto aunque la clase solo dispone de constructores con argumentos, provocando `BeanCreationException` / `NoSuchMethodException HydraInstanceLock.<init>()`.
- Marca explícitamente con `@Autowired` el constructor de producción `HydraInstanceLock(HydraInstanceLockProperties, StorageProperties)`, eliminando la ambigüedad creada por el constructor auxiliar usado por tests.
- Condiciona el componente con `@ConditionalOnProperty(prefix = "hydra.instance-lock", name = "enabled", havingValue = "true", matchIfMissing = true)`, de modo que `hydra.instance-lock.enabled=false` impide registrar el bean en lugar de construirlo para que luego no haga nada.
- Mantiene el bloqueo activo por defecto en ejecuciones reales y conserva el constructor auxiliar package-private para pruebas unitarias directas.
- Añade una regresión que verifica tanto la condición de registro como el constructor de inyección y el gate `verify_r5c13_fix85w_instance_lock_spring_bootstrap.py`.


## R5C13 Fix92 — sandbox y presupuesto global de ejecución externa

- Añade un presupuesto global compartido de procesos externos mediante semáforo justo, además de los límites específicos que ya tenía cada bounded context.
- Limita globalmente timeout y bytes capturables por stdout/stderr, de forma que un módulo no puede solicitar una ejecución que supere la política transversal del servidor.
- Mantiene árbol de procesos, workspace temporal, argumentos y entorno bajo las defensas anteriores y añade regresiones específicas de saturación y presupuesto.
- Los resultados de procesos ejecutados dentro de una operación correlacionada incluyen su `correlationId` como evidencia diagnóstica no sensible.

## R5C13 Fix93 — observabilidad end-to-end por correlationId

- Propaga automáticamente el `correlationId` de una petición HTTP a trabajos asíncronos cuando la capa llamadora no proporciona uno explícito.
- Restaura `AuditRequestContext` y MDC dentro del thread del job, evitando perder la correlación al cruzar el executor.
- Añade un registro acotado de 2048 hitos operativos para HTTP/jobs y el endpoint `/api/observability/traces` para diagnóstico focalizado.
- Los procesos externos heredan la correlación a través de `AuditRequestContext`, permitiendo seguir HTTP → job → proceso sin almacenar payloads sensibles.

## R5C13 Fix94 — fault injection controlado y fail-closed

- Introduce `hydra.fault-injection`, desactivado por defecto, con una lista cerrada de failpoints explícitos.
- Integra un failpoint inmediatamente después del journal `PREPARED` de transacciones de artefactos para probar recuperación ante interrupciones en el peor punto posible.
- Añade failpoints reservados para portabilidad y ejecución externa sin habilitarlos implícitamente en producción.
- Las regresiones verifican que ningún fallo sintético se ejecuta mientras la función no esté activada de forma deliberada.

## R5C13 Fix95 — inventario operativo de cachés y políticas de rendimiento

- Añade `/api/runtime-caches` con ocupación, máximo, clave, invalidación, expulsión y métricas disponibles de las cachés críticas de validación, gramáticas y análisis de update sites.
- Hace explícitas las diferencias entre FIFO acotado, LRU+TTL e invalidaciones por artefacto, evitando cachés sin política observable.
- Conserva los presupuestos existentes y permite detectar saturación o tasas anómalas de misses sin inspeccionar estructuras internas.

## R5C13 Fix96 — contratos internos entre bounded contexts

- Introduce el puerto neutral `MetamodelQueryPort` como primera frontera estable entre consumidores de metamodelos y la implementación `metamodel.application`.
- `CanonicalMetamodelApplicationService` implementa el puerto y `TransformationNativeRuntimeExecutor` deja de importar la clase application concreta.
- Mantiene compatibilidad de constructores y tests porque la implementación canónica satisface directamente el nuevo contrato.
- Establece el patrón para migrar progresivamente el resto de dependencias cruzadas sin una refactorización masiva de una sola vez.

## R5C13 Fix97 — proyecto portable verificable

- Añade export/import de proyecto portable mediante un contenedor externo con `hydra-portable-manifest.json` y `project.zip`.
- El manifiesto contiene versión de esquema, release HYDRA, tamaño y SHA-256 del archivo interno; la importación verifica todos esos datos antes de materializar el proyecto.
- La exportación rechaza ficheros con nombres típicos de secretos (`.env`, credenciales, claves PEM/KEY, `id_rsa`) y el manifiesto prohíbe declarar `secretsIncluded=true`.
- Reutiliza el importador ZIP transaccional existente, conservando Zip Slip, límites de tamaño, ratio de compresión y publicación mediante staging.

## R5C13 Fix98 — compatibilidad API v1 y deprecaciones observables

- Añade un registro central de deprecaciones HTTP y soporte de cabeceras estándar `Deprecation`, `Sunset` y `Link rel=successor-version` cuando exista una ruta declarada.
- Publica `/api/compatibility` con versión contractual y deprecaciones activas para clientes y herramientas.
- Crea `config/api-contract-v1-baseline.json` y `verify_backward_compatibility.py`: una release no puede eliminar una operación v1 ni cambiar su `operationId` sin una ruptura explícita de contrato.
- Regenera OpenAPI 3.1 y el cliente TypeScript a partir del árbol final; las nuevas operaciones se permiten sin invalidar el baseline existente.

- Gate acumulativo `verify_r5c13_fix92_98_platform_hardening.py`: 13/13 PASS.


## R5C13 Fix99 — UX transversal de errores y recuperación

- Amplía el contrato `ApiError` / `application/problem+json` con impacto, reintentabilidad, acciones declarativas de recuperación, operación/recurso afectados y `correlationId`.
- Añade `HydraRecoveryAdvice` con política conservadora: un reintento genérico nunca se marca como acción segura si HYDRA no puede demostrar idempotencia; refrescar estado y abrir diagnósticos sí pueden ofrecerse como acciones seguras.
- El frontend conserva y presenta esa evidencia en `ActionableApiError` y `HydraActionableErrorNotice`: qué ocurrió, dónde, por qué, impacto, cómo corregirlo, recuperación, correlation ID y detalle técnico colapsado.
- El detalle técnico queda separado del resumen de usuario y acotado; las acciones automáticas solo aparecen cuando el llamador proporciona un callback y la recuperación fue declarada reintentable.
- Añade regresiones backend/frontend para conflicto, error interno y `problem+json` correlacionado.

## R5C13 Fix100 — Release Qualification reproducible

- Añade `config/release-qualification-policy.json` y `scripts/release/qualify_release.py` como única cualificación ejecutable de release.
- La cualificación cubre gate primario, escenarios críticos de Spring/recovery/migración/portabilidad, empaquetado fuente, SBOM, integridad del ZIP, extracción en directorio limpio, segundo gate estructural y, en modo `--full`, segundo build/test completo del paquete recibido.
- Reempaqueta el árbol extraído y exige igualdad SHA-256 byte a byte del ZIP fuente y del SBOM; una release que solo funciona en el workspace generador no puede calificarse.
- Genera `release-qualification.json`, su SHA-256 y `release-certificate.json` con evidencia de los artefactos cualificados.
- CI y el validador Windows V4.32.107 ejecutan la cualificación completa después de sus gates primarios.

- Gate acumulativo `verify_r5c13_fix99_100_release_closure.py`: 12/12 PASS.

## R5C13 Fix101 — validación E2E de producto

- Añade escenarios declarativos ejecutables para metamodelos/validaciones, gramáticas y transformaciones bidireccionales.
- `run_rc1_product_e2e.py` dispone de modo contractual y modo `--live` contra un backend real, con cleanup explícito y códigos HTTP esperados.
- La cualificación RC1 exige conservar estos flujos y sus proyectos de referencia.

## R5C13 Fix102 — auditoría UX completa

- Añade una política de superficies críticas y `audit_product_ux.py` para detectar errores genéricos, detalles técnicos expuestos y ausencia de ayuda/diagnóstico en módulos principales.
- Genera `target/release/rc1-ux-audit.json` como evidencia auditable de RC1.
- Incorpora un smoke Playwright RC1 que bloquea excepciones backend crudas en la superficie de usuario.

## R5C13 Fix103 — proyectos de referencia

- Incorpora `library-basic`, `commerce-intermediate` y `sync-advanced` como fixtures de producto versionados.
- Cubren metamodelo/modelo/validación/quick fix, grammar-first y M2M bidireccional/incremental respectivamente.
- `verify_reference_projects.py` comprueba JSON, referencias entre metamodelos, reglas forward/reverse y ausencia de nombres de secretos.

## R5C13 Fix104 — stress y escala

- Añade `Rc1TransformationScaleQualificationTests` sobre el runtime canónico con 100, 1.000 y 10.000 elementos obligatorios y 50.000 en cualificación completa.
- `run_rc1_scale.py` compara tiempos observados con presupuestos declarados en `rc1-scale-budgets.json` y emite evidencia machine-readable.

## R5C13 Fix105 — instalación y distribución

- Añade matriz de instalación limpia para rutas con espacios, Unicode y anidamiento profundo.
- `verify_installation_matrix.py` extrae el ZIP fuente en ubicaciones nuevas, repite manifiesto/metadata y opcionalmente compila desde esa copia.
- Evita declarar RC1 una entrega que solo funciona en el workspace del desarrollador.

## R5C13 Fix106 — onboarding verificable

- Añade documentación RC1 guiada para primer proyecto, validación/quick fixes, gramáticas, transformaciones, recuperación e instalación.
- Los documentos están vinculados a los proyectos de referencia y `verify_rc1_onboarding.py` protege conceptos operativos críticos.

## R5C13 Fix107 — cualificación RC1

- Introduce `rc1-qualification-policy.json` y `qualify_rc1.py` como cierre de producto sobre Fix100.
- Orquesta referencia, E2E, UX, escala, instalación, onboarding y revalidación del paquete; en `--full` añade backend live, 50.000 elementos y Playwright.
- CI y el validador Windows ejecutan la cualificación RC1 después de la cualificación reproducible Fix100.

- Gate acumulativo `verify_r5c13_fix101_107_rc1.py`.


## R5C13 Fix108 — Qualification Bootstrap & Workspace Integrity

- Corrige el orden de la cualificación completa: antes del `primary-full-tree`, Fix100 verifica el manifiesto del workspace, ejecuta `npm ci`, provisiona Chromium de Playwright y valida el toolchain. Los gates frontend dejan de fallar en cascada por dependencias no instaladas.
- `verify_toolchain.py` deja de exigir cadenas de versión exactas para Java/Node/npm: Java exige la feature 24; Node/npm aceptan versiones iguales o superiores dentro del mismo major; TypeScript y Maven wrapper permanecen fijados.
- Los gates Xtext/Langium y portable host resuelven primero `frontend/node_modules/typescript/bin/tsc`, evitando depender de un `tsc` global ajeno al lockfile del proyecto.
- RC1 ejecuta un preflight de integridad antes de entrar en Fix100; un árbol contaminado por overlays falla en segundos mostrando `missing/unlisted/changed` en vez de producir múltiples gates secundarios rojos.
- Añade `diagnose_qualification.py` para desplegar de una vez los fallos anidados de RC1, Fix100 y `release-readiness.json`.
- Gate `verify_r5c13_fix108_qualification_bootstrap.py` protege estas garantías.

## R5C13 Fix109 — Windows Toolchain Resolution

- Corrige la resolución de herramientas en Windows: `verify_toolchain.py` deja de invocar `npm` como si fuese un binario Unix y resuelve `npm.cmd`/`node.exe` mediante `PATHEXT`, ejecutando `.cmd`/`.bat` de forma segura a través de `COMSPEC`.
- Java se resuelve primero desde `HYDRA_JAVA_HOME`, después desde `JAVA_HOME` y finalmente desde `PATH`. Esto permite cualificar HYDRA con JDK 24 sin modificar globalmente una instalación Java 25 existente.
- El diagnóstico del toolchain muestra el ejecutable exacto utilizado para Java, Node, npm, TypeScript y Maven, y conserva la política RC1: Java feature 24, Node/npm compatibles dentro del mismo major, TypeScript exacto y Maven wrapper exacto.
- Si Java no es 24.x, el gate explica cómo seleccionar un JDK 24 mediante `HYDRA_JAVA_HOME`; no se rebaja el contrato para aceptar Java 25 sin cualificación explícita.
- Gate `verify_r5c13_fix109_windows_toolchain_resolution.py` protege estas garantías.


### Fix109 — lanzador Windows para JDK 24 local

- Añade `scripts/release/qualify_rc1_windows.ps1`, que localiza un JDK 24 instalado en rutas habituales de Windows o acepta `-Jdk24Home` explícito.
- El lanzador establece `HYDRA_JAVA_HOME` solo dentro de la sesión de cualificación, de modo que Java 25 puede seguir siendo el Java global del equipo.
- Antes de iniciar RC1 ejecuta `verify_toolchain.py --strict`; si no existe un JDK 24, falla inmediatamente con una instrucción concreta en lugar de entrar en una cualificación larga.

### R5C13 Fix109B — Lanzador RC1 Windows canónico

- `scripts/release/qualify_rc1.ps1` es el lanzador canónico de Windows.
- `qualify_rc1_windows.ps1` queda como alias compatible.
- El JDK 24 seleccionado se propaga a `HYDRA_JAVA_HOME`, `JAVA_HOME` y `PATH` para que Maven y los procesos E2E usen el mismo runtime.
- Se mantiene Java 24 como contrato de compilación/cualificación; Java 25 puede seguir instalado globalmente.


### Fix110 — separación entre bytecode Java 24 y JDK de cualificación 24/25

- Mantiene `<java.version>24</java.version>` y `maven-compiler-plugin` con `<release>${java.version}</release>`: los artefactos siguen apuntando a Java 24.
- Amplía el entorno de cualificación RC1 a JDK 24.x o 25.x mediante `javaQualificationMax=25`; no cambia el nivel de bytecode.
- `verify_toolchain.py` valida ahora el rango de feature Java 24..25 y conserva Node/npm/TypeScript/Maven con sus contratos previos.
- `qualify_rc1.ps1` detecta primero el JDK explícito, `HYDRA_JAVA_HOME`, `JAVA_HOME` y el `java.exe` real del PATH; por tanto un Java 25 ya instalado puede cualificar HYDRA sin instalar JDK 24.
- `-Jdk24Home` se conserva como alias compatible de `-JdkHome`.
- El JDK elegido sigue propagándose a `HYDRA_JAVA_HOME`, `JAVA_HOME` y `PATH`, de modo que Maven, Spring Boot y los E2E ejecutan la misma JVM.
- Spring Boot 4.1.1 declara compatibilidad con Java hasta 26; Java 25 queda dentro de su rango soportado.

## R5C13 Fix111 — Windows JDK Discovery v2

- Sustituye heurísticas PowerShell por un motor Python compartido y configurable en `config/toolchain-discovery-policy.json`.
- Descubre JDK mediante override explícito, `HYDRA_JAVA_HOME`/`JAVA_HOME`, `PATH`, propiedad real `java.home`, registro de Windows y raíces de Oracle/Adoptium/Microsoft/Azul/Corretto/BellSoft.
- Un candidato solo es aceptado como JDK si contiene `java` y `javac`; se registran proveedor, versión, fuente de descubrimiento y motivos de descarte.
- Java 24/25 siguen siendo JDK de cualificación permitidos y el bytecode permanece Java 24.
- La política puede declarar raíces JDK locales al proyecto (`projectLocalRoots`) y su profundidad de exploración, respetando el orden de resolución configurable.

## R5C13 Fix112 — HYDRA Environment Doctor

- Añade `scripts/doctor/hydra_doctor.py` y wrapper PowerShell.
- Diagnostica JDK/Javac, Maven, Node, npm, TypeScript, Playwright, Python, espacio en disco, permisos de workspace/TEMP, puertos y Windows Long Paths.
- Genera `target/diagnostics/environment-doctor.json` y usa `config/environment-doctor-policy.json` para separar requisitos, avisos y remediaciones.
- `requiredTools` y `fullQualificationTools` gobiernan realmente qué herramientas son obligatorias; el doctor ya no mantiene una lista paralela codificada.

## R5C13 Fix113 — Qualification Resume & Checkpoints

- Introduce `qualification_engine.py` como motor compartido de etapas con fingerprint de fuentes/toolchain y checkpoints persistentes.
- `qualify_release.py`, `qualify_rc1.py` y `qualify_rc2.py` soportan `--resume`, `--from`, `--only` y `--rerun-failed`.
- Una etapa PASS solo se reutiliza cuando el fingerprint coincide; cambiar fuentes o contratos invalida automáticamente la evidencia previa.
- `--only`/`--from` fallan de forma cerrada cuando una etapa obligatoria omitida no tiene checkpoint PASS compatible; la evidencia importada de Fix100/RC1/final-tree se valida por estado, versión, fuerza (`full`) y fingerprint antes de reutilizarse.

## R5C13 Fix114 — aislamiento y diagnóstico por etapa

- Cada etapa escribe su propio log en `target/release/logs/<qualification>/` y el informe conserva la ruta del log junto con `outputTail`.
- Los timeouts terminan el árbol de procesos (`taskkill /T /F` en Windows, process groups en POSIX), evitando procesos Maven/Node/Playwright huérfanos.
- `diagnose_qualification.py` incluye RC2, RC1, Fix100, final-tree y checkpoints fallidos.

## R5C13 Fix115 — Toolchain hermético

- Maven permanece fijado al wrapper del proyecto y TypeScript se obtiene del `package-lock`/`frontend/node_modules` del proyecto.
- El inventario `target/diagnostics/toolchain-inventory.json` conserva ejecutable real, versión, contrato y hash cuando procede.
- La misma resolución de JDK/toolchain es reutilizada por doctor, lanzadores y evidencia de procedencia.

## R5C13 Fix116 — Release Provenance & Compatibility Evidence

- Añade `generate_provenance.py` y `release-provenance.json` con manifiesto, ZIP fuente, SBOM, resultados de cualificación, plataforma y toolchain efectivo.
- Se diferencia explícitamente `bytecodeTarget=24` de los JDK 24/25 permitidos para construir/cualificar.
- La evidencia recibe SHA-256 independiente para poder auditar una candidata sin depender del workspace generador.
- El contrato Java de procedencia se deriva de `toolchain-discovery-policy.json`, evitando duplicar en código los valores de bytecode y JDK admitidos.

## R5C13 Fix117 — RC2 Stabilization Gate

- Añade `config/rc2-qualification-policy.json`, `qualify_rc2.py` y `qualify_rc2.ps1` como cierre sin nuevas funcionalidades.
- RC2 compone integridad, doctor, toolchain, RC1, proyectos de referencia, recuperación/fault injection, instalación, procedencia, paquete reextraído y reproducibilidad.
- En modo completo exige evidencia live/scale/Playwright/Maven y una segunda validación completa del paquete recibido.
- Un PASS genera `rc2-certificate.json` y `rc2-freeze.json`; a partir de ahí la rama queda en feature freeze.
- Los atajos `--primary-rc1-qualified`, `--primary-release-qualified` y `--primary-already-verified` ya no son declaraciones de confianza: exigen evidencia compatible del árbol actual y rechazan informes obsoletos o incompletos.
- La entrega mantiene un único `MANIFEST_SHA256.txt` canónico en la raíz; se elimina el snapshot duplicado bajo `src/` para evitar dos fuentes de verdad.

### Fix117 — corrección de cualificación de entorno en Windows

- `-JdkHome` admite ahora la raíz del JDK, su directorio `bin` o la ruta directa a `java/java.exe`/`javac/javac.exe`; la selección explícita ya no cae silenciosamente a otro JDK válido por una forma de ruta ambigua.
- El diagnóstico de `npm` dispone de un fallback determinista mediante `node .../npm-cli.js`, evitando falsos negativos de `npm.cmd` en rutas de Windows con espacios.
- El Environment Doctor separa prerrequisitos de host de dependencias locales: TypeScript y Playwright solo son obligatorios en `--full`, después de `npm ci`.
- `verify_toolchain.py` consume el colector compartido y diferencia validación base y `--full`, eliminando duplicación y evitando exigir `node_modules` sobre un árbol fuente limpio.

### Fix117 — cierre de cualificación completa y portabilidad de los gates

- La cualificación Python queda declarada y bloqueada en `scripts/release/qualification-requirements.txt`; el lanzador Windows provisiona un entorno virtual HYDRA administrado fuera del árbol fuente y no modifica el Python global del usuario.
- `jsonschema` y `pytest` pasan a ser prerrequisitos verificables del entorno de cualificación. Los gates Python generados/portables reutilizan siempre el intérprete activo (`sys.executable`) y dejan de depender del alias `python3` de Windows.
- Los gates TypeScript portables invocan el `tsc` bloqueado del proyecto mediante Node, evitando diferencias de permisos y semántica entre shims `.bin` de npm en Windows/POSIX.
- El motor de cualificación fuerza UTF-8 y salida no bufferizada para procesos Python anidados y muestra `START`/`RUNNING` periódicos con etapa, tiempo transcurrido y log activo; los timeouts y el cierre fail-closed no se relajan.
- El full gate de frontend usa el runner por lotes de HYDRA para Vitest, con aislamiento y recuperación por fichero ante fallos de worker, en lugar de una única ejecución monolítica susceptible de agotar el timeout global.
- Playwright de accesibilidad/UX usa un API determinista en navegador; el comportamiento real contra backend permanece cubierto por la etapa live de producto, separando tests de interfaz de disponibilidad de servicios.
- Los presupuestos de bundle distinguen JavaScript de aplicación y vendors pesados explícitos. Monaco mantiene un presupuesto propio estricto y deja de consumir el presupuesto de código propio; se eliminan expectativas de chunks de evolución update-site que ya no existen como módulos independientes.
- Se corrigen las infracciones reales de ESLint/React Hooks detectadas por la cualificación, incluida la identidad estable del callback de resize del runtime canvas, evitando ciclos de React Flow sin desactivar ninguna regla de lint.
- CI declara Python 3.13 y las dependencias Python de cualificación, instala Chromium de Playwright y ejecuta la misma política hermética que la cualificación local.

### Fix117 — cierre residual de la cualificación RC2 en Windows

La segunda pasada real de RC2 sobre Windows cerró tres regresiones que solo aparecían después de completar los gates de entorno y backend: el callback de redimensionado del editor gráfico deja de usar una indirección por `ref` durante la proyección de render para cumplir las reglas de React Hooks; el test del presupuesto de bundle valida por separado el total físico y el total de JavaScript de aplicación; y el E2E de arquitectura de eventos entra explícitamente en `#/events` con un contexto semántico determinista y selecciona la subvista del diseñador, evitando confundirla con la navegación global. Estas correcciones no rebajan ningún presupuesto, timeout ni regla de lint.

### Fix117 — cierre final de regresiones observadas en RC2 Windows

- El runtime del editor gráfico estabiliza `saveNodePosition` con `useCallback` en `EditorRuntimeSession` y pasa la misma referencia a drag y resize. Se conserva la proyección sin indirecciones por `ref` durante render y se evita reconstruir el callback únicamente por cambios de selección.
- El test de Home respeta el contrato controlado del selector semántico: primero verifica la notificación `GRAMMAR:<id>` al propietario y después rerenderiza con el nuevo valor antes de exigir que el panel de readiness cambie de contexto. No se introduce estado local duplicado en `HydraHome`.
- El E2E de arquitectura de eventos reproduce la interacción real de la paleta: localiza una categoría con eventos disponibles, expande su `<details>` y solo entonces selecciona un evento visible. No se altera el CSS ni se fuerza a elementos colapsados a considerarse visibles.
- Los tres casos quedan cubiertos también por los gates C5/Fix111–117 para impedir regresiones posteriores durante RC2.

### Fix117 — alineación final del contrato Vitest del runtime de modelos

- `ModelEditorRuntimeWorkspaceContract.test.ts` deja de exigir la envoltura inline de `onNodeResizeStop`, eliminada deliberadamente al estabilizar el callback de geometría. El contrato ahora exige `saveNodePosition` creado con `useCallback` y reutilizado directamente por `onNodeDragStop` y `onNodeResizeStop`.
- `verify_v43287_model_editor_workspace.py` aplica el mismo contrato estable, evitando que una validación histórica vuelva a exigir la implementación anterior.
- El gate Fix111–117 comprueba explícitamente que el test contractual protege la identidad estable del callback y rechaza la antigua expectativa positiva.

### Fix117 — cierre del contrato de proyección del diseñador de gramáticas
- Se alinea `GrammarDesignerUxContract.test.ts` con la semántica real del refresco de proyecciones: el efecto captura `expectedSourceHash` antes de programar el refresco y reutiliza ese valor al invocar `refreshDraftProjection`, evitando comparar contra un análisis que pueda cambiar entre renders.
- Los verificadores históricos Fix75 y V4.33.08 exigen el mismo contrato, eliminando expectativas textuales obsoletas.
- El gate Fix111–117 protege explícitamente esta convergencia para impedir futuras divergencias entre implementación, Vitest y validaciones históricas.

### Fix117 — cierre del contrato de validación en vivo del editor textual
- `TextualModelEditorUxContract.test.ts` se alinea con la dependencia real del efecto de validación en vivo: `drainParseQueue` forma parte del grafo estable de dependencias junto con `source` y `sessionId`.
- El contrato conserva explícitamente la exclusión del ETag del grafo de dependencias, evitando revalidaciones en bucle por cada respuesta del backend.
- `verify_r10_textual_grammar_reentry.py` y el gate Fix111–117 exigen la misma semántica para impedir futuras divergencias entre implementación, Vitest y validaciones históricas.

### Fix117 — preflight Vitest y convergencia del editor textual
- El contrato UX del editor textual exige ahora el grafo real de dependencias `[drainParseQueue, source, sessionId]`, manteniendo el ETag fuera del efecto para evitar revalidaciones encadenadas.
- El gate R10 reconoce la creación segura de la definición por defecto mediante `uniqueDraft`, seguida de recarga canónica y adopción de `loaded.definition`, en lugar de exigir la forma histórica anterior.
- La cualificación RC2 completa ejecuta `npm ci` y toda la suite Vitest por lotes como preflight antes de entrar en RC1. Los mismos tests siguen ejecutándose en el final-tree, por lo que se gana feedback temprano sin rebajar ni eliminar ninguna comprobación final.

### Fix117 — reanudación focalizada del paquete extraído

- Corrige el orden de cualificación de Fix100: el paquete extraído restaura ahora las dependencias `dev` del frontend mediante `npm ci --include=dev` antes de ejecutar cualquier gate estructural que necesite TypeScript.
- Añade `scripts/release/qualify_fix100_extracted_repair.py` y su launcher PowerShell para revalidar exclusivamente el tramo que había fallado, sin repetir `primary-full-tree`, Vitest, Playwright, Axe, Maven ni los gates ya superados.
- La reparación focalizada ejecuta únicamente integridad del paquete, restauración de dependencias y los cuatro gates previamente fallidos: Xtext/Langium, portable host, proyectos portable host generados y cobertura visible/runtime en español.
- El reparador usa checkpoints propios y admite `--resume` / `--rerun-failed`, de modo que incluso dentro de este tramo no vuelve a ejecutar una etapa que ya haya pasado con el mismo fingerprint.
- Tras los cuatro gates realiza solo una reempaquetación determinista y verifica la reproducibilidad byte a byte del source package; no reabre la batería funcional ya validada.


## R6C1 Fix125 — recuperación centralizada de ejecuciones Vitest focalizadas

- El runner `frontend/scripts/hydra-test-runner.mjs` aplica ahora la misma clasificación fail-closed de fallos de infraestructura también a ejecuciones explícitas de uno o varios ficheros, no solo a lotes de la suite completa.
- Una aserción, timeout de test, error de sintaxis, excepción funcional u OOM continúa siendo un fallo real y no provoca rotación de pools. Solo los errores reconocidos de bootstrap/ciclo de vida del worker avanzan al siguiente pool aislado.
- `IntegrationDesigner.test.tsx` deja de depender de un pool hardcoded. En Windows R6C1 comienza por `vmThreads`, conserva un único worker y puede recuperar mediante la política común si Vitest vuelve a informar de un fallo de infraestructura.
- `validate_r6c1_fix125.ps1` reanuda exclusivamente el fichero que no llegó a ejecutar tests y el build de producción; no repite Maven ni los 16 tests frontend ya demostrados PASS.

## R6C1 Fix126 — cierre de tipado de recomendaciones tecnológicas

- Corrige dos aserciones de `integrationTechnologyRecommendations.test.ts` que pasaban dos valores a `toContain`, una forma tolerada por la ejecución transpile-only de Vitest pero rechazada correctamente por `tsc -b`.
- Cada capacidad se valida ahora mediante una aserción independiente, conservando exactamente el mismo contrato funcional: solo capacidades `FULL`, exclusión de `PARTIAL` y semántica específica de destinos de proyección.
- `validate_r6c1_fix126.ps1` reanuda exclusivamente desde el build de producción porque backend, CSS, 16 tests frontend dirigidos e `IntegrationDesigner.test.tsx` 9/9 ya disponen de evidencia PASS en esta campaña.
- No se modifica código de producto ni se relajan reglas TypeScript, Vitest o build.

## R6C2 Fix127 — contrato neutral de preparación de entrega

- Introduce `TechnologyDeliveryReadiness` y un estado por fase (`READY`, `READY_WITH_WARNINGS`, `BLOCKED`, `EXTERNAL`, `NOT_APPLICABLE`) separado de la madurez global de la tecnología.
- Cada acción declara si HYDRA la ejecuta, si modifica el host, si necesita directorio destino, si exige preview y qué superficie REST la materializa.

## R6C2 Fix128 — readiness derivado del perfil y de una compilación real

- El cálculo reutiliza el `IntegrationCheckReport` del perfil persistido y, cuando la generación está implementada, realiza una compilación tecnológica en memoria sin ejecutar toolchains ni escribir en el host.
- Un fallo de compilación conocido se convierte en bloqueo accionable del recorrido en lugar de ofrecer fases posteriores que no pueden cumplirse.
- El número real de ficheros generados y de pasos nativos de verificación pasa a formar parte de la evidencia de readiness.

## R6C2 Fix129 — API de readiness por perfil

- Añade una API GET específica por ámbito/perfil bajo `/technology/readiness`, disponible tanto en rutas `semantic-contexts` como canónicas.
- La consulta es estrictamente de solo lectura; la ejecución de toolchains continúa reservada al endpoint explícito `/technology/validate`.
- El endpoint entra en el OpenAPI canónico y en `hydraApiClient.ts`, evitando una API manual no reflejada en el contrato público.

## R6C2 Fix130 — recorrido guiado accionable

- `IntegrationTechnologyJourney` superpone la preparación del perfil a la madurez tecnológica y muestra por etapa el estado real y la evidencia disponible.
- Generar, previsualizar y aplicar respetan los bloqueos de readiness tanto en controles visuales como en los handlers, de forma que una invocación programática tampoco atraviesa un estado `BLOCKED`. La UI no ofrece una acción como operativa solo porque figure en el catálogo.
- El resumen de entrega deriva la disponibilidad de la fase `GENERATE` y no de `compilationReady`; así una proyección PlantUML puede estar preparada sin fingir que existe un módulo compilado.

## R6C2 Fix131 — verificación estructural y nativa desde el diseñador

- El diseñador conecta el endpoint de validación de proyectos generado que ya existía en backend.
- La verificación estructural es homogénea para todos los destinos con validador registrado; la verificación nativa es una acción explícita y solo aparece cuando el paquete generado declara pasos Maven/npm/Python/etc.
- La ejecución del editor/runtime nativo continúa marcada como `EXTERNAL`; HYDRA no finge abrir o controlar un host externo.

## R6C2 Fix132 — cierre dirigido de paridad de entrega

- Añade pruebas backend del cálculo fail-closed, API, proyecciones y errores de compilación; añade normalización frontend fail-closed, tests de journey y API.
- El gate `validate_r6c2_fix127_132.ps1` ejecuta exclusivamente los tests nuevos/afectados y el build frontend, sin reabrir R6C1 ni la cualificación RC2 congelada.
- Documenta el contrato en `docs/integration/TECHNOLOGY_DELIVERY_READINESS.md` y añade cobertura española de toda la nueva superficie visible.
- El generador OpenAPI fija UTF-8 y LF al escribir contrato, YAML y cliente TypeScript para que verificar la API en Windows no altere bytes canónicos por diferencias CRLF/LF.

## R6C3 Fix133 — contrato neutral de equivalencia semántica

- Añade `SemanticFidelity`, `SemanticDifference`, `SemanticEquivalenceReport` y `MetamodelSemanticComparator`.
- La identidad semántica deja de depender de ids regenerados, provenance o igualdad textual y compara clases, atributos, tipos, enums, multiplicidades, relaciones y root explícito.
- Una pérdida estructural se clasifica como `LOSSY`; la ausencia de material suficiente se clasifica como `UNAVAILABLE`.

## R6C3 Fix134 — transporte no semántico y root estable

- Conserva los wrappers auxiliares únicamente como reglas `HYDRA_TRANSPORT_RULE`, que se retiran antes de inferir el dominio.
- Añade `HYDRA_SEMANTIC_ROOT` para preservar el entry/root real a través de exportación y reimportación.
- Corrige Xtext para no emitir un `ID` anónimo cuando una clase no declara realmente `name`.

## R6C3 Fix135 — round-trip neutral Xtext/Langium

- Añade `TextualMetamodelRoundTripService` y `TextualMetamodelRoundTripResult`.
- Ejecuta `MetaModel -> gramática -> reimportación -> comparación semántica` para Xtext y Langium.
- `requireLossless` falla de forma cerrada cuando la proyección pierde significado.

## R6C3 Fix136 — paridad cruzada de providers

- Añade `TextualProviderParityService` para contrastar fuente↔Xtext, fuente↔Langium y Xtext↔Langium.
- La paridad se declara únicamente cuando las tres reconstrucciones son semánticamente compatibles.

## R6C3 Fix137 — capacidad nativa sin semántica sintética

- Añade `TextualSemanticCapabilityAnalyzer` para separar fidelidad estructural de ejecutabilidad nativa.
- Una cross-reference cuyo destino no tiene atributo string `name` se preserva, pero queda diagnosticada como `REFERENCE_SCOPE_REQUIRES_EXTERNAL_PROVIDER`; HYDRA no inventa `name=ID` para aparentar linking nativo.
- Añade tests focales, documentación y los gates `verify_r6c3_semantic_roundtrip.py` / `validate_r6c3_fix133_137.ps1`.
- La cualificación R6C3 es acumulativa respecto a R6C2: como Fix127–132 todavía no disponían de validación del usuario en el host objetivo, `validate_r6c3_fix133_137.ps1` ejecuta primero el gate completo R6C2 y solo después los tests R6C3. Las fases se etiquetan `R6C2-INHERITED-FIX127-132` y `R6C3-NEW-FIX133-137` para localizar el origen del primer fallo sin dar por probado el baseline heredado.


## R6C3 Fix138 — hotfix acumulativo autosuficiente y cualificación heredada

- Corrige el empaquetado de R6C3: el hotfix anterior se había calculado contra Fix132 y por tanto podía omitir los archivos de R6C2 en un workspace que todavía estuviera en R6C1 Fix126. El nuevo hotfix se calcula explícitamente desde R6C1 Fix126 e incluye R6C2 Fix127–132 y R6C3 Fix133–138 en una sola superposición.
- `validate_r6c3_fix133_137.ps1` conserva su ruta por compatibilidad con el comando ya entregado, pero deja de invocar `validate_r6c2_fix127_132.ps1` como dependencia externa: ejecuta directamente toda la fase heredada R6C2 y después R6C3.
- Añade un preflight de integridad del payload que comprueba verificadores Python, runner frontend, wrapper Maven y `package.json`. Una instalación incompleta falla con `[PACKAGE-INTEGRITY]` y una instrucción accionable antes de ejecutar tests.
- La fase heredada continúa etiquetada `R6C2-INHERITED-FIX127-132`; la fase nueva pasa a `R6C3-NEW-FIX133-138`. Solo tras ambas aparece el PASS acumulativo.
- No se altera el código semántico de Fix133–137; Fix138 corrige distribución y cualificación para el caso real en el que R6C2 no llegó a instalarse/probarse por separado.

## R6C3 Fix139 — cierre del contrato documental y launcher canónico

- Corrige el fallo real observado en Windows durante `r6c2-backend-docs`: los 25 métodos incorporados por R6C3 reciben documentación española conforme al contrato obligatorio `Descripción / Entradas / Salida`, sin introducir `@SuppressWarnings` ni marcadores de deuda.
- `verify_backend_spanish_method_docs.py` vuelve a cubrir el 100 % del backend mantenido: 9856/9856 métodos documentados.
- Añade el launcher canónico `validate_r6c3_fix127_139.ps1`; la ruta histórica `validate_r6c3_fix133_137.ps1` se conserva como wrapper compatible para que el comando ya utilizado por el usuario siga funcionando.
- La cualificación ejecuta ahora el gate documental antes de OpenAPI, Maven, Vitest y build, de modo que una infracción barata de contrato falle inmediatamente y no consuma fases costosas.
- `verify_r6c3_semantic_roundtrip.py` protege de forma permanente la documentación de las clases R6C3, el nuevo launcher y la compatibilidad del comando antiguo. No cambia la semántica Xtext/Langium de Fix133–137.

## R6C3 Fix140 — equivalencia semántica de instancias canónicas

- Añade `CanonicalModelSemanticComparator` y validación de invariantes para `CanonicalModelDocument`.
- La equivalencia compara raíces, IDs estables de elementos, clases, atributos y destinos de relaciones; los IDs efímeros de instancias de relación y las anotaciones de transporte no forman parte de la identidad semántica.
- Las invariantes bloquean clases/features inexistentes, cardinalidades inválidas, targets incompatibles, duplicados, múltiples contenedores, ciclos de contención y raíces incoherentes.

## R6C3 Fix141 — codec de instancias alineado con la gramática generada

- Añade `GeneratedTextualInstanceCodec` para Xtext y Langium sin introducir `name` sintético.
- La identidad nativa solo es exacta cuando existe un atributo string `name`, su valor coincide con el ID canónico y cumple el subconjunto portable de `ID`.
- Colisiones con keywords o features generadas, IDs no portables y cualquier ambigüedad léxica fallan de forma cerrada.

## R6C3 Fix142 — contención profunda y referencias diferidas

- Las relaciones `CONTAINMENT` se serializan como anidación real y se reconstruyen preservando la topología y las raíces.
- Las referencias no contenedoras se resuelven después de analizar todos los documentos, permitiendo targets forward/backward y ciclos no contenedores.
- Un ciclo de contención continúa siendo inválido y se rechaza antes de generar texto.

## R6C3 Fix143 — multiarchivo y dependencias de workspace

- Añade estrategias `SINGLE_DOCUMENT` y `ONE_DOCUMENT_PER_ROOT`.
- Una contención nunca cruza documentos; las referencias sí pueden hacerlo y generan `TextualInstanceDocumentDependency` con `resolutionMode=WORKSPACE_SCOPE`.
- No se inventan reglas `import` que las gramáticas actuales no publican. La necesidad de índice/scope de workspace queda declarada como evidencia explícita.

## R6C3 Fix144 — source maps y bindings completos

- El round-trip genera bindings `MODEL_ELEMENT`, `MODEL_FEATURE` y `MODEL_RELATION` con rango, metamodel element y grammar rule.
- La identidad `name=ID` conserva su binding de feature además del rango del elemento completo.
- Un source map incompleto bloquea `lossless`; no se declara fidelidad exacta si falta trazabilidad semántica reconstruible.

## R6C3 Fix145 — paridad de instancias Xtext/Langium y campaña agresiva

- `TextualInstanceRoundTripService` ejecuta invariantes → encode → decode → reconstrucción → equivalencia y ofrece `requireLossless` fail-closed.
- `TextualInstanceProviderParityService` exige equivalencia source↔Xtext, source↔Langium y Xtext↔Langium.
- Los tests cubren tres niveles de contención, referencias forward/backward, ciclos no contenedores, multiarchivo y una campaña determinista de 40 raíces/40 documentos.

## R6C3 Fix146 — revisiones textuales idempotentes

- Endurece `TextualModelSessionService.parse`: una revisión inferior continúa siendo stale; repetir la misma revisión con exactamente los mismos documentos es idempotente y no vuelve a parsear ni persistir.
- Reutilizar la misma `draftRevision` con contenido distinto se rechaza explícitamente para impedir que una revisión lógica represente dos estados diferentes.

## R6C3 Fix147 — protección last-known-good y snapshot fijado

- Se consolida como contrato R6C3 que un borrador inválido conserva `lastValidSemanticModel` y `lastValidSourceMap`.
- Las perspectivas continúan leyendo únicamente el último modelo válido y la sesión conserva el `grammarSnapshotId` fijado durante su creación.

## R6C3 Fix148 — cancelación, límites y seguridad de documentos

- La cualificación comprueba el `BoundedPermitPool`, la cancelación por interrupción, los límites de documentos/bytes/diagnósticos/source-map y el rechazo de rutas con traversal.
- Los snapshots de sesión mantienen copias defensivas de colecciones y metadatos para evitar mutaciones posteriores desde el llamador.

## R6C3 Fix149 — cierre acumulativo de resiliencia

- Añade `TextualRuntimeResilienceTests`, `verify_r6c3_runtime_resilience.py` y `docs/grammar/R6C3_RUNTIME_RESILIENCE.md`.
- El launcher canónico pasa a `validate_r6c3_fix127_149.ps1`; Fix145, Fix139 y el launcher histórico Fix133–137 permanecen como cadena de compatibilidad.
- La cualificación acumulativa ejecuta R6C2 heredado, contrato de metamodelos, contrato de instancias, resiliencia runtime y los tests backend focales antes de declarar PASS.

## R6C3 Fix150 — fixture canónico para ID no portable y cierre nativo del gate

- Corrige el test `failsClosedForNonPortableNativeIds`: el fixture anterior (`../evil`) era inválido para `StableIdentifierPolicy` y fallaba al construir `CanonicalModelElement`, antes de alcanzar la validación Xtext/Langium que pretendía comprobar.
- El nuevo fixture utiliza `node-1`, que es un identificador canónico HYDRA válido pero queda fuera del subconjunto portable común de `ID` (`[A-Za-z_][A-Za-z0-9_]*`), por lo que el test alcanza realmente `INSTANCE_ID_NOT_PORTABLE_ID`.
- El gate `verify_r6c3_instance_roundtrip.py` protege esta precondición de forma permanente y rechaza fixtures que vuelvan a ser inválidos a nivel canónico.
- Añade `validate_r6c3_fix127_150.ps1` como launcher canónico; Fix149 y los launchers anteriores permanecen en la cadena de compatibilidad.
- Fix150 no cambia la semántica de producción del codec: corrige evidencia de test y cualificación para que el comportamiento fail-closed se valide en la capa correcta.


## R6C4 Fix151 — contrato neutral de capacidades de interacción

- Separa `HYDRA_NEUTRAL`, `PROVIDER_NATIVE_EXTERNAL` y `UNAVAILABLE`, evitando presentar un LSP externo como si estuviera embebido en HYDRA.

## R6C4 Fix152 — índice semántico único de workspace

- Añade `TextualWorkspaceIndex` inmutable para documentos, modelo canónico, elementos y source maps. Todas las capacidades leen el mismo snapshot.

## R6C4 Fix153 — completion canónico y acotado

- Reutiliza tipos, atributos, relaciones e identidades del `CanonicalModelDocument`, con filtrado por prefijo y orden determinista.

## R6C4 Fix154 — hover contextual

- Resuelve la entrada de source map más específica y muestra identidad, clase, metamodel binding y regla gramatical.

## R6C4 Fix155 — navegación a definición

- Las referencias navegan al `MODEL_ELEMENT` destino incluso entre documentos distintos.

## R6C4 Fix156 — búsqueda de referencias

- Reutiliza `targetElementId`, limita resultados y conserva posiciones estables compatibles con Monaco/LSP.

## R6C4 Fix157 — rename preview-first

- Valida la política de IDs, detecta colisiones y genera `GrammarTextEdit` para declaración y referencias sin persistir cambios ni saltarse ETag.

## R6C4 Fix158 — formato determinista

- Normaliza LF y espacios finales sin reindentar ni eliminar líneas vacías; solo devuelve una edición cuando existe un cambio real.

## R6C4 Fix159 — document symbols

- Deriva símbolos del source map canónico y mantiene rango, identidad y clase semántica.

## R6C4 Fix160 — folding semántico

- Solo pliega spans semánticos multilínea, sin inventar regiones a partir de texto no trazado.

## R6C4 Fix161 — semantic tokens neutrales

- Proyecta elementos, features y relaciones a `class`, `property` y `reference`; la UI conserva la responsabilidad de estilo.

## R6C4 Fix162 — invalidación incremental cross-file

- Construye el grafo inverso de referencias y calcula el cierre transitivo de documentos afectados antes del siguiente parse.

## R6C4 Fix163 — compatibilidad y migración de snapshots

- Distingue `EXACT`, `REPARSE_SAFE` y `MIGRATION_REQUIRED` según identidad de gramática, contrato semántico y provider.

## R6C4 Fix164 — límites, cancelación y fijación a last-known-good

- Completion, referencias, símbolos, tokens y rename están acotados; los bucles cooperan con interrupción y la fachada nunca usa un borrador roto como base semántica.

## R6C4 Fix165 — REST, OpenAPI y cualificación focal

- Añade 12 operaciones de interacción a `/api/textual-model-sessions/{id}/interactions/*`, regenera OpenAPI/cliente TypeScript, incorpora JUnit focal, documentación y `validate_r6c4_fix151_165.ps1`.

## R6C4 Fix166 — cualificación autosuficiente y recuperación de empaquetado

- Elimina la precondición accidental de `validate_r6c4_fix151_165.ps1` sobre `validate_r6c3_fix127_150.ps1`.
- Añade `validate_r6c4_fix151_166.ps1`, que verifica directamente `verify_r6c3_semantic_roundtrip.py`, `verify_r6c3_instance_roundtrip.py` y `verify_r6c3_runtime_resilience.py` antes de cualificar R6C4.
- Mantiene `validate_r6c4_fix151_165.ps1` como wrapper compatible para que el comando ya entregado siga siendo válido.
- Endurece `verify_r6c4_textual_interactions.py` para impedir que futuras acumulativas vuelvan a depender de launchers históricos como sustituto de una comprobación real de capacidades.
- No cambia la lógica de producción de las capacidades textuales; Fix166 corrige exclusivamente cualificación, preflight y empaquetado acumulativo.


## R6C4 Fix167 — cualificación por inventario y overlay completo de recuperación

- El launcher canónico pasa a `validate_r6c4_fix151_167.ps1` y deja de exigir por nombre cualquier `validate_r6c3_*` o `verify_r6c3_*`.
- `MANIFEST_SHA256.txt` se convierte en la única precondición de inventario: si falta o diverge una ruta fuente canónica, la cualificación falla antes de Maven.
- El JUnit focal ejecuta conjuntamente metamodel round-trip, import regression, instance semantic/aggressive round-trip, runtime resilience y R6C4 language features sobre el mismo árbol.
- Los launchers Fix165 y Fix166 permanecen como wrappers compatibles hacia Fix167.
- Se publica un overlay de source completo para recuperar workspaces que hayan recibido parcialmente overlays anteriores, sin borrar outputs o archivos propios fuera del inventario canónico.

## R6C4 Fix168 — integridad canónica compatible con workspaces enriquecidos

- Corrige el preflight de Fix167: `verify_manifest.py` ya no obliga al workspace de desarrollo a ser idéntico a un ZIP limpio cuando se ejecuta con `--allow-extra`.
- El modo estricto permanece como comportamiento por defecto para validar artefactos extraídos y reproducibilidad; cualquier ruta canónica ausente o con hash distinto sigue bloqueando la cualificación.
- `validate_r6c4_fix151_168.ps1` utiliza el modo workspace: valida todos los hashes canónicos y preserva archivos adicionales como documentación Archify, scripts de recuperación o desarrollos locales no incluidos todavía en el release congelado.
- `validate_r6c4_fix151_167.ps1` permanece como wrapper compatible hacia Fix168.
- La corrección no modifica la lógica Java/REST de R6C4; endurece la separación entre integridad de artefacto limpio e integridad de un repositorio de trabajo enriquecido.

## R6C4 Fix169 — resolución precisa de definición y referencias cross-document

- Corrige `TextualLanguageFeatureService.definition/references`: un cursor situado en el whitespace inmediatamente anterior a una referencia ya no cae en el rango amplio del `MODEL_ELEMENT` contenedor.
- Para navegación se prioriza el `MODEL_RELATION` adyacente únicamente cuando está en la misma línea, a como máximo dos offsets y el texto intermedio es exclusivamente whitespace; no se modifican los rangos globales de source map usados por hover, folding o semantic tokens.
- Añade una regresión que compara la navegación desde la posición adyacente y desde el token exacto y exige el mismo destino cross-file.
- Añade `validate_r6c4_fix151_169.ps1`; Fix168 queda como wrapper compatible hacia el nuevo launcher.


### R6C4 Fix170 — Global release qualification environment and React readiness

- Corrects integration delivery-readiness state by binding asynchronous responses to the saved profile/revision. Invalid or unsaved models do not display readiness from an earlier revision; the effect no longer performs a synchronous state reset (React ESLint).
- Adds `scripts/validation/validate_full_tree_fix170.ps1`, which creates an isolated Python qualification environment under `target/release`, installs the version-pinned `jsonschema` and `pytest` dependencies only when needed, and runs the unchanged global qualification gates.
- `verify_final_tree.py` records complete failure output under `target/release/logs/final-tree` and includes a `logPath` in the readiness JSON. This prevents repeated intentional XML parser diagnostics at the end of Maven output from hiding the actual failing tests. No test is skipped, downgraded, or silenced.
- R6C4 Fix169 textual interaction qualification remains available unchanged. Full Maven verification must pass on the target Windows host before release certification.

### R6C4 Fix171 — Corrección del guard arquitectónico de Maven

- Reestructura exclusivamente el formato del método `Parser.parseElement` en `GeneratedTextualInstanceCodec.java`, conservando todos sus tokens Java (salvo espacios de formato), tipos, mensajes, identificadores, decisiones semánticas y orden de operaciones. No cambia API, esquema ni funcionalidades Xtext/Langium.
- El fallo Windows original era `StructuralArchitectureGuardTests.productionSourceFilesDoNotHideComplexityInGiantLines`: dos líneas de 2.672 y 2.562 caracteres rebasaban el techo compartido Java de 2.400 caracteres.
- Añade `verify_r6c4_fix171_logical_size.py`, que valida antes de Maven los presupuestos globales Java/TS/TSX/CSS sin excepciones individuales y reproduce la medición UTF-16 del guard Java.
- Añade `validate_full_tree_fix171.ps1`, que comprueba los hashes canónicos (preservando extras), ejecuta el nuevo preflight arquitectónico y delega en la cualificación global Fix170 íntegra; no deshabilita pruebas ni amplía presupuestos.
- La aceptación definitiva requiere ejecutar el comando Fix171 en el Windows destino y obtener `FINAL_TREE_GATE=PASS`, incluyendo Maven completo.


## R6C4 Fix172 - GitHub Actions Windows/Linux parity

- Anclaje del checkout de archivos canónicos a sus bytes originales con una política de `.gitattributes` portable: LF por defecto y excepciones CRLF explícitas para los archivos históricos ya firmados.
- El wrapper de Maven se invoca mediante Bash en CI para evitar errores de permisos de ejecución tras checkout en Ubuntu; se recomienda guardar también el modo ejecutable del archivo `mvnw` en el índice Git.
- Gate de integridad del manifiesto inmediatamente después de configurar Python en CI, antes de ejecutar las pruebas completas.
- Se mantienen sin modificaciones los 40 esquemas JSON y sus referencias SHA-256 previamente aprobadas.
