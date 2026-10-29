# Caché incremental de análisis de arquitectura

Esta carpeta **no es documentación de arquitectura** (eso sigue siendo
`docs/architecture/archify/hydra-architecture.html` + `hydra-architecture-data.js`). Es un
**acelerador de análisis**: registra contra qué estado exacto del repositorio se verificó por
última vez cada capacidad, para que una futura actualización pueda comparar (`git diff`) desde
aquí en vez de releer todo el repositorio o relanzar todos los subagentes de investigación.

## Archivos

- **`baseline.json`** (schemaVersion 2, dinámico) — el HEAD actual resuelto en el momento de la
  última sincronización (`analyzedState.head`), si el árbol de trabajo está limpio o sucio con sus
  rutas relevantes staged/unstaged/untracked clasificadas, el HEAD de la sincronización anterior
  (`previouslyAnalyzedState`) y la instrucción exacta de `git diff` a usar la próxima vez. **Nunca
  se debe asumir que un SHA citado en una conversación anterior sigue siendo el actual** — cada
  sincronización resuelve `git rev-parse HEAD` y `git status` de forma dinámica y los compara contra
  este fichero, no al revés.
- **`capabilities-cache.json`** — una entrada por cada capacidad (mismos IDs que
  `window.CAPABILITIES`), con su estado de validación (`VALIDATED` / `PARTIAL`) y qué huecos de
  `gaps.json` le afectan. No duplica el contenido de la capacidad — solo referencia sus rutas de
  evidencia.
- **`gaps.json`** — hallazgos concretos y accionables detectados al comparar commits: superficies
  nuevas sin investigar, citas de evidencia incorrectas ya confirmadas, y citas sin reverificar.
  Cada hueco tiene un `id` estable, severidad, y una `suggestedNextAction` acotada (nunca "reanaliza
  todo el repositorio").

- **`current-task.md`** — especificación vigente del refinamiento del portal (qué debe cubrir cada capacidad y cada proceso, definición de
  hecho estricta). Es la fuente de requisitos; el estado real está en `baseline.json`.
- **`portal-check.js`** — validador del contenido del portal (`hydra-model-*.js`): esquema bilingüe, valores de obligatoriedad, referencias
  cruzadas (elementos, procesos, capacidades), conectividad de los grafos de proceso, fugas de implementación en texto USUARIO y la matriz de
  cobertura de 18 dimensiones por capacidad.
- **`hydra-lens-*.js`** (junto al portal, no aquí) son las proyecciones FUNCIONAL/TÉCNICA; `portal-check.js` las valida (cobertura, no idénticas a USUARIO, sin fugas).
- **`portal-browser-check.js`** — verificación en navegador real (Chrome/Edge + `playwright-core` de `frontend/node_modules`): estructura, diferenciación por audiencia (similitud de contenido, títulos de etapas, etiquetas de mapa/overview), persistencia de contexto al cambiar audiencia/idioma, y uso del lienzo (pan, zoom, ajustar, restablecer, centrar, inspector). Escribe
  `portal-browser-check.last.json` con el último resultado (errores, fugas, solapes, cruces de líneas por diagrama).
- En `capabilities-cache.json`, cada capacidad lleva `portalContent` (cuánto contenido estructurado tiene el portal) además de su estado de
  validación; en `gaps.json` (schema 2), `sourceObservations` registra rarezas verificadas del propio código (declarado pero no cableado,
  discrepancias, ausencia de pruebas) y `remainingUnverified` lo que sigue sin comprobarse.

Desde la 8.ª pasada, `capabilities-cache.json` incluye por capacidad `technicalDocumentation`: recuentos (componentes, métodos, extractos, pruebas, trazas) y una lista concisa de responsabilidades verificadas por componente, sin extractos de código. La fuente es `hydra-tech-*.js`; `portal-check.js` comprueba que cada fichero, línea, método y extracto exista en el árbol de trabajo actual.

## Evidencia de pruebas (knowledge/tests/)

Almacén compacto de qué demuestra cada prueba (comportamiento verificado, escenario, resultado, garantía, limitaciones), mapeado a capa → capacidad → proceso → etapa, con el último estado de ejecución fiablemente conocido (PASS / FAIL / SKIPPED / NOT_EXECUTED / STALE_EVIDENCE / DISCOVERED; nunca PASS por existir). Ver knowledge/tests/README.md. capabilities-cache.json recibe solo referencias compactas (	estEvidence, processStageEvidence); aseline.json y gaps.json registran el estado y los huecos de evidencia (gap-t*).

## Cómo se usa esto en la siguiente iteración

1. Resolver el estado actual de Git de forma dinámica (`git rev-parse HEAD`, `git status`) —
   nunca asumir un SHA de una conversación anterior. Comparar ese HEAD contra
   `baseline.json.analyzedState.head.sha`: si coincide, solo hay que reclasificar el árbol de
   trabajo; si avanzó, ejecutar el `git diff` indicado en `nextIncrementalDiffInstructions`.
2. Clasificar los ficheros cambiados: si solo tocan las categorías ya listadas en
   `gaps.json.nonArchitecturallyRelevantChanges` (CI, `.gitignore`, paquetes de release,
   artefactos generados, scripts de cualificación, catálogos i18n, fixtures de test), **no**
   dispara una nueva investigación.
3. Si hay ficheros de implementación real fuera de esas categorías, comprobar primero si ya están
   cubiertos por un `evidenceRoot` de `capabilities-cache.json` con `validationState: VALIDATED` —
   si el fichero cambiado vive bajo esa raíz, reabrirlo puntualmente (no relanzar el agente
   completo) antes de asumir que sigue siendo válido.
4. Añadir cualquier hallazgo nuevo a `gaps.json` con un `id` nuevo (`gap-00N`), no sobreescribir
   los anteriores salvo que se resuelvan (entonces mover su `status` a `RESOLVED` y anotar en qué
   commit/sesión se resolvió).
5. Actualizar `baseline.json.analyzedState`/`previouslyAnalyzedState` al terminar (mover el
   `analyzedState` actual a `previouslyAnalyzedState` y escribir el nuevo `analyzedState` resuelto).

## Lo que esta caché NO hace

- No sustituye a `hydra-architecture-data.js` ni a los diagramas Archify — es un índice sobre
  ellos, no una especificación paralela.
- No se actualiza automáticamente — cada actualización es una acción explícita de una sesión de
  Claude Code, siguiendo los pasos de arriba.
- No contiene código fuente ni volcados de los informes de los subagentes — solo las conclusiones
  estructuradas y las rutas de fichero necesarias para retomar el trabajo.
