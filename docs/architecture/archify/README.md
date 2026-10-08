# HYDRA — arquitectura viva (Archify)

## Punto de entrada único

```
docs/architecture/archify/hydra-architecture.html
```

Abre **ese** archivo. Es un portal estático (HTML/CSS/JS simple, sin dependencias, sin build) con
tres secciones de nivel superior, seleccionables con la barra **[ VISIÓN GENERAL | CAPACIDADES |
PROCESOS ]**, y el idioma **[ ES | EN ]** aplicado a las tres:

- **VISIÓN GENERAL** — el portal original: nivel **[ USUARIO | FUNCIONAL | TÉCNICO ]** cargado en un
  `<iframe>` con el diagrama Archify real correspondiente (los ocho HTML ya validados viven en
  `internal/` — ver más abajo), sin perder ninguna de sus funciones nativas (Semantic Lens, Pasaporte
  semántico, Ruta, Radar, exportación, temas claro/oscuro…), más un panel lateral de **exploración de
  componentes** con tarjetas expandibles por el mismo ID canónico en los tres niveles, con la
  progresión **QUÉ HACE → LÍMITES DE MODELADO → RELACIONES FUNCIONALES → CÓMO FUNCIONA → CÓMO ESTÁ
  IMPLEMENTADO → EVIDENCIA EN EL CÓDIGO**, y ahora también un enlace **"Ver vista de capacidad →"**
  que salta a la vista dedicada de CAPACIDADES para ese mismo componente.
- **CAPACIDADES** — catálogo de **15** capacidades principales de HYDRA (11 + 4 descubiertas en esta
  pasada: Toolchains, Repositorios Externos, Ciclo de Vida de Proyectos Generados, Empaquetado y
  Portabilidad), cada una con su propia vista dedicada en 5 subvistas con pestañas — **Visión general
  | Elementos | Proceso | Relaciones | Implementación** — en vez de un único scroll largo.
- **PROCESOS** — catálogo de **23** procesos reales: **15 propios de una capacidad** (su ciclo de vida
  interno: crear/definir → configurar → validar → guardar/ejecutar) y **8 cruzados** entre varias
  capacidades, agrupados visualmente en el panel ("Por capacidad" / "Cruzados"). Cada uno se muestra
  como un flujo START → ETAPAS (con leyenda visual OBLIGATORIO/OPCIONAL) → RAMAS → RESULTADO.

Admite enlaces directos a cualquier combinación de sección/nivel/idioma/capacidad/proceso, por ejemplo:
`hydra-architecture.html?section=capabilities&capability=validation&lang=en` o
`hydra-architecture.html?section=processes&process=transformation_execution_sync`.

El panel lateral no inventa arquitectura nueva: republica, en una capa de datos bilingüe, el mismo
`label`/`sublabel`/`sources` que ya pasó las cuatro puertas de `finalize` en cada nivel — es una
proyección de los JSON ya validados en `internal/`, no una fuente de verdad independiente. Los tiers
QUÉ HACE, LÍMITES DE MODELADO y RELACIONES FUNCIONALES parafrasean en lenguaje llano hechos ya
verificados (p. ej. que `GamificationRuntimeService` depende de
`CanonicalMetamodelApplicationService`), no inventan dependencias ni límites nuevos.

### Nivel CÓMO SE HACE (diagrama de proceso)

A diferencia de los otros tres niveles (que son proyecciones del mismo diagrama de arquitectura),
CÓMO SE HACE es un **diagrama de flujo de trabajo** (`diagram_type: "workflow"` de Archify, no
`"architecture"`) — un tipo de diagrama distinto, con su propio esquema (carriles/lanes, columnas,
fases, ruta principal). Muestra un proceso real combinando varias capacidades, con límites explícitos
en cada paso:

`hydra-howto-model-editor.es/.en.html` — **cómo diseñar un Editor de Modelos**: Metamodelo → Notación
gráfica → Validaciones → Quick Fixes → Paleta/formularios del editor → Generar y probar. Cada paso
indica qué se modela ahí y qué no (p. ej. "Notación: iconos, formas, colores — no cambia el
metamodelo"), e incluye dos flechas de retroalimentación (ajustar una regla, ajustar el estilo
visual) para reflejar que el proceso real es iterativo, no estrictamente lineal.

## Vista CAPACIDADES (15 vistas dedicadas, datos en `hydra-architecture-data.js`)

El nivel de detalle de los tres diagramas de arquitectura (USUARIO/FUNCIONAL/TÉCNICO) está limitado
deliberadamente a lo que un diagrama Archify de tipo `architecture` puede representar de forma legible
(componentes + conexiones). Para explicar **qué se puede definir dentro de cada capacidad** (sus
conceptos de dominio, cardinalidades, ciclo de vida y relación con el resto de HYDRA) sin sobrecargar
ese diagrama, se añadió una segunda fuente de datos, `hydra-architecture-data.js`
(`window.CAPABILITIES`), cargada por el portal antes de su script principal. No es un nuevo tipo de
artefacto Archify: es contenido HTML/CSS/JS renderizado directamente por el portal, igual que ya
ocurría con el panel lateral de VISIÓN GENERAL.

Las 11 capacidades (cada una ligada al mismo ID canónico de componente que ya usan los tres niveles de
arquitectura; **Eventos** y **Gamificación** comparten el nodo `b_event_gami` pero tienen vista propia
porque el usuario las trata como capacidades separadas) son: **Metamodelos, Editor de Modelos,
Validación y Quick Fixes, Gramáticas y Lenguajes Textuales, Transformaciones, Eventos, Gamificación,
Integración de Tecnologías, Recuperación Externa, Publicación y Generación, Gestión de Proyectos.**

Cada vista de capacidad tiene 6 niveles fijos, derivados directamente de 5 informes de exploración
dirigida sobre el código fuente actual (no extrapolados de la arquitectura previa):

1. **QUÉ ES Y QUÉ APORTA** — propósito, valor, entradas y salidas de alto nivel.
2. **QUÉ SE PUEDE DEFINIR** — los conceptos de dominio reales (p. ej. `ValidationRule`,
   `TransformationRule`, `EventTypeDefinition`): propósito, cuándo se usa, propiedades principales
   (conceptuales, no todos los campos), relaciones, cardinalidad, entradas/salidas, rol en el ciclo de
   vida, un ejemplo real (no inventado) y evidencia de código con archivo y línea.
3. **CÓMO SE RELACIONAN LOS ELEMENTOS** — el modelo de dominio conceptual de la capacidad.
4. **CÓMO FUNCIONA POR DENTRO** — su ciclo de vida/ejecución interno real.
5. **CÓMO INTERACTÚA CON EL RESTO DE HYDRA** — de qué depende (entrante) y a qué alimenta/dispara
   (saliente).
6. **CÓMO ESTÁ IMPLEMENTADO** — servicios/clases de runtime, APIs y evidencia de código.

Cada vista de capacidad termina, si aplica, con un bloque **"Procesos relacionados"** que enlaza a la
vista de PROCESOS filtrada por esa capacidad.

### Hallazgos no triviales que corrigen suposiciones de la arquitectura previa

La exploración dirigida para esta fase confirmó o corrigió explícitamente varios puntos que la
arquitectura anterior (o una lectura superficial del código) habría asumido de forma distinta — todos
documentados con evidencia de archivo+línea dentro de `hydra-architecture-data.js`:

- El registro `NotationDefinition` existe en el código pero está **desconectado** del compilador real
  del editor (`EditorDefinitionCompiler` nunca lo referencia); toda la apariencia visual vive en
  `NodeMapping`/`EdgeMapping` dentro de `EditorDefinitionModel`.
- Un Quick Fix de Validación **sí muta el modelo real** al aplicarse (`apply()`), contra la suposición
  de "sin efectos secundarios siempre"; solo `preview()` es genuinamente side-effect-free. Además,
  `apply()` revalida automáticamente en la misma respuesta.
- El motor de Transformación de HYDRA soporta **sincronización incremental bidireccional real**
  (diff + conflictos + merge sobre un baseline rodante), no solo "paridad direccional" — esa limitación
  aplica solo a los puentes con toolchains nativos externos (ATL, etc.).
- Compilar una gramática **infiere un metamodelo real** (no ilustrativo): una etapa bloqueante del
  pipeline de 12 etapas falla toda la compilación si esa proyección no se completa.
- La publicación de eventos **no es automática** para toda mutación del modelo: es *opt-in* por sesión
  de editor (`runtimePolicy().publishEvents()`); el camino EMF legado no tiene ningún gancho de eventos.
- Existe un **rastro de auditoría universal**, siempre activo e independiente del sistema de Events
  (que sí es opcional), conectado a todo almacén de documentos canónicos de la plataforma.
- La activación/desactivación de capacidades de proyecto (`ProjectCapability`, 24 valores) se aplica
  en el **backend** (`ProjectIndexService.logicalRoot`), no solo visualmente en el frontend.

## Vista PROCESOS (catálogo de 8 procesos reales, datos en `hydra-architecture-data.js`)

El proceso "cómo diseñar un Editor de Modelos" (nivel CÓMO SE HACE, ver abajo) fue el primer ejemplo
construido, pero **no pretendía representar el flujo completo de HYDRA**: es un único proceso entre
varios. Esta fase amplió el catálogo a 8 procesos reales (`window.PROCESSES`), cada uno descubierto por
exploración dirigida del código (no extrapolado del ejemplo anterior), cubriendo Metamodelos, Modelos,
Validación, Quick Fixes, Transformaciones, Gramáticas, Eventos, Gamificación, Recuperación Externa,
Integración y Publicación:

| Proceso | Capacidades que combina |
|---|---|
| Validar un modelo, reparar con un quick fix y revalidar | Validación, Editor de Modelos, Eventos, Gamificación |
| Ejecutar una transformación y, si es bidireccional, sincronizar de forma incremental | Metamodelos, Transformaciones, Integración |
| Compilar una gramática, inferir el metamodelo y editar texto conforme a ella | Gramáticas, Metamodelos, Editor de Modelos, Transformaciones |
| De una acción en el editor a una recompensa (y su compensación) vía Events y EventFlow | Editor de Modelos, Eventos, Gamificación |
| Guardar y, eventualmente, borrar un metamodelo (cascada de limpieza) | Metamodelos, Editor de Modelos, Validación, Transformaciones |
| Recuperar un proyecto/update-site externo: preview, materializar y, si hace falta, deshacer | Recuperación Externa, Metamodelos, Validación, Integración |
| Generar un proyecto y, opcionalmente, certificarlo mediante publicación verificada | Metamodelos, Editor de Modelos, Publicación, Integración, Gestión de Proyectos |
| Cómo diseñar un editor de modelos (ejemplo detallado, diagrama Archify dedicado) | Metamodelos, Gramáticas, Validación, Editor de Modelos |

Cada proceso se renderiza como **START (disparador + estado de partida) → ETAPAS (con capacidad
propietaria por etapa, coloreada, y borde discontinuo si es opcional) → RAMAS OPCIONALES/CONDICIONALES
→ RESULTADO**, más su evidencia de código. Ninguna etapa, rama o relación de este catálogo fue
extrapolada del ejemplo original: cada una está respaldada por evidencia de archivo+línea del código
actual, y donde el código no confirmaba una hipótesis inicial (p. ej. "Validación siempre dispara
Eventos") se documentó la condición real (opt-in) en vez de forzar la hipótesis.

### Decisión de diseño: contenido en el portal, no diagramas Archify adicionales

Para los 11 catálogos de capacidad y los 8 procesos **no** se generaron 19 nuevos artefactos Archify
(`*.architecture.json`/`*.workflow.json` con su ciclo completo `validate`/`deliver`/`check`/
`browser-check`). Se optó por representarlos como contenido HTML/CSS/JS data-driven dentro del propio
portal, por dos razones: (1) el nivel de detalle pedido (listas de conceptos con 8-10 campos cada uno,
narrativas de ciclo de vida) excede lo que un diagrama de cajas-y-flechas puede mostrar con legibilidad;
(2) evita 19 ciclos adicionales de iteración de diagnósticos de layout por una ganancia de legibilidad
marginal frente a un documento estructurado. El único diagrama Archify de proceso que se mantiene es
`hydra-howto-model-editor.es/.en.html` (nivel CÓMO SE HACE), enlazado desde la vista de PROCESOS como
**"Abrir el diagrama Archify detallado de este proceso"** — un ejemplo profundo, no el único proceso.
Esta decisión es reversible: si en el futuro se necesita un diagrama Archify dedicado para otro proceso,
basta con añadir su `archifyDiagram: {es, en}` a la entrada correspondiente en `window.PROCESSES`.

## Cuarta pasada: subvistas por pestañas, modos de creación, obligatoriedad, y 4 capacidades nuevas

El resultado anterior (fases 1-7) seguía siendo insuficiente en tres frentes concretos que esta pasada
corrige con descubrimiento dirigido adicional (6 agentes de investigación de frontend+backend, uno de
ellos relanzado tras completarse parcialmente):

**1. Modos de creación reales.** Cada capacidad documenta ahora, en su subvista **Elementos**, un bloque
**"CÓMO SE DEFINE CADA ELEMENTO"** con el modo o modos de creación reales verificados en el frontend
(formulario guiado/avanzado, lienzo de arrastrar-conectar, árbol+formulario, editor textual Monaco,
importación, generado automáticamente…), nunca inventados. Hallazgos no triviales documentados: Eventos
sí tiene un diseñador visual real de flujos (lienzo @xyflow con los 28 tipos de nodo), pero Transformación
NO dibuja líneas de mapeo (es un taller de tres zonas dirigido por botones); Validación, Quick Fixes y los
guards de Transformación comparten literalmente el mismo constructor de expresiones (guiado por cláusulas
o árbol avanzado), sin ninguna caja de texto libre OCL/EVL en ningún punto; el Diseñador de Metamodelos es
clic-en-paleta (no arrastrar-soltar) con toda la edición de detalle en el panel de propiedades, nunca en
el propio nodo.

**2. Obligatoriedad/opcionalidad explícita.** Se añadió una taxonomía de 5 valores — **OBLIGATORIO /
OPCIONAL / CONDICIONAL / ALTERNATIVA / REPETIBLE** — con una leyenda visual consistente (badge con
borde sólido/discontinuo/punteado/doble/sólido-morado) aplicada a cada elemento definible de cada
capacidad. En la vista de Procesos se aplica la distinción OBLIGATORIO/OPCIONAL a nivel de etapa (la
única granularidad que el modelo de datos existente de procesos soportaba sin una reescritura completa
a un esquema de grafo — ver limitaciones más abajo).

**3. Cuatro capacidades que faltaban.** Un barrido de completitud sobre los 23 módulos del frontend y
los 47 paquetes del backend confirmó que **Toolchains** y **Repositorios Externos** ya tenían presencia
como nodo de arquitectura (`ext_toolchains`/`ext_remote`) pero nunca una vista de capacidad dedicada con
sus 6 niveles, y que **Ciclo de Vida de Proyectos Generados** (registrar→generar→validar→abrir→lanzar,
backend `generated.project`) y **Empaquetado y Portabilidad** (archivar/fusionar semánticamente/aplicar-
revertir transaccionalmente "capas de capacidad", backend `portability`) no estaban representadas en
absoluto. Las 4 se añadieron completas (6 niveles + modos de creación) y enlazadas al nodo de arquitectura
más cercano (`ext_toolchains`, `ext_remote`, `b_project` para las dos últimas — mismo patrón que Eventos/
Gamificación compartiendo `b_event_gami`).

**4. Subvistas con pestañas.** La vista de capacidad dejó de ser un único scroll largo: ahora tiene 5
pestañas — **Visión general** (qué es/para qué sirve, lenguaje llano primero) · **Elementos** (modos de
creación + catálogo de conceptos definibles) · **Proceso** (narrativa de ciclo de vida + enlace al proceso
propio de esa capacidad en el catálogo de Procesos) · **Relaciones** (entrante/saliente) · **Implementación**
(servicios/APIs/evidencia) — para que la profundidad técnica nunca aparezca antes que la explicación
conceptual, como pedía la revisión.

**5. Procesos por capacidad además de cruzados.** Se añadieron 15 procesos propios de capacidad (uno por
cada una) describiendo su ciclo de vida interno real (p. ej. "Crear o importar → Detallar en el panel →
Organizar el lienzo → Guardar → Exportar/generar" para Metamodelos), agrupados visualmente como "Por
capacidad" frente a los 8 "Cruzados" ya existentes en el panel de Procesos.

### Limitaciones conocidas de esta pasada (transparencia, no gates verdes)

Siguiendo la instrucción explícita de no declarar el trabajo completo solo porque las validaciones pasan,
estas son las simplificaciones deliberadas frente al pedido original, con su razón:

- **No se generaron diagramas de dominio conceptual dibujados (nodo-arista) por capacidad.** La relación
  entre conceptos definibles (p. ej. MetaClass↔MetaAttribute, ValidationRule↔QuickFix) se documenta como
  texto estructurado (`relationships`, `cardinality` por concepto) dentro de la subvista Elementos, no
  como un lienzo SVG dibujado. Construir 15 diagramas de layout automático añadía una cantidad de trabajo
  de ingeniería de visualización considerable para una ganancia de legibilidad marginal frente al texto
  estructurado ya presente.
- **Los procesos no usan nodos de decisión con ramas sí/no dibujadas.** Las ramas opcionales/condicionales
  siguen siendo notas textuales bajo el diagrama de etapas (como en la fase anterior), no rombos de
  decisión conectados visualmente a dos caminos. Esto habría requerido migrar las 23 entradas de
  `window.PROCESSES` de un esquema plano `stages[]+branches[]` a un grafo `nodes[]+edges[]`.
  Documentado como la limitación más clara frente a la sección 16 del encargo.
- **La obligatoriedad de etapa solo distingue OBLIGATORIO/OPCIONAL, no las 5 categorías completas.**
  Las 5 categorías sí se aplican a nivel de elemento definible dentro de cada capacidad (subvista
  Elementos), que es donde más importaban para responder "¿necesito esto?".
- **Los 3 diagramas Archify de arquitectura (USUARIO/FUNCIONAL/TÉCNICO) no se regeneraron** con una
  clasificación visual de relaciones REQUERIDA/OPCIONAL/CONDICIONAL/ALTERNATIVA/EXTERNA en el propio SVG.
  Se mantiene la aproximación de fases anteriores: solo las 2 relaciones de dependencia más centrales se
  dibujan como flecha en el SVG; el resto se documenta en texto (ahora, además, en las subvistas Elementos/
  Relaciones de Capacidades). Regenerar esos 6 artefactos Archify arriesgaba reabrir el ciclo de
  iteración de diagnósticos de layout ya cerrado en fases anteriores, para una ganancia visual acotada.

### Relaciones entre capacidades, visibles en el propio diagrama

Los niveles USUARIO y FUNCIONAL ya no cuelgan todas las capacidades únicamente del workbench: dibujan
también las dos relaciones de dependencia real más centrales, con flecha verde sólida distinta de la
navegación gris:

- **Metamodelos → Modelos** ("define el lenguaje de"): un modelo solo puede contener los tipos que su
  metamodelo define.
- **Modelos → Transformaciones** ("parte de modelos ya creados"): el motor de transformaciones nunca
  inventa datos de entrada.

Las otras cuatro relaciones reales (Metamodelos → Validaciones/Gramáticas/Eventos, Modelos →
Publicación) no se dibujan como flecha adicional en el SVG — habría exigido canales de enrutado que
generaban cruces en cadena con el abanico del workbench — pero están documentadas, con su explicación,
en el tier RELACIONES FUNCIONALES del panel lateral de cada componente.

No necesitas abrir manualmente ningún archivo `.es.html` / `.en.html` suelto. Esos siguen existiendo
(Archify los necesita para renderizar/validar) pero son **artefactos de implementación del portal**,
movidos a `internal/`.

## Procedencia

- Repositorio: `https://github.com/CristianGM23/hydra_tool.git`
- Rama: `main` · commit `864ed521e8d4e5fde8a0ed087c37bc3e5b89de36`
- Árbol de trabajo: limpio (sin cambios locales) en el momento de la generación
- Modo: generación inicial — no existía arquitectura Archify previa que comparar
  (ver `hydra-architecture-changes.json`)
- Versión de HYDRA: `4.32.107` (`config/hydra-version.json`)

## Estructura de archivos

```
docs/architecture/archify/
├── hydra-architecture.html          ← ÚNICO punto de entrada público (portal)
├── hydra-architecture-data.js       ← datos de CAPACIDADES (11) y PROCESOS (8), cargados por el portal
├── hydra-architecture-changes.json  ← resumen de cambios de esta ejecución
├── README.md                        ← este archivo
└── internal/                        ← artefactos de implementación (Archify)
    ├── hydra-architecture.es.architecture.json   (+ .html, .finalize.json, .finalize-summary.json,
    │                                                .browser-check.json, .delivery.json)
    ├── hydra-architecture.en.*
    ├── hydra-functional-overview.es.*
    ├── hydra-functional-overview.en.*
    ├── hydra-user-overview.es.*
    ├── hydra-user-overview.en.*
    ├── hydra-howto-model-editor.es.workflow.json  (+ .html, .finalize.json, .finalize-summary.json,
    │                                                 .browser-check.json, .delivery.json)
    ├── hydra-howto-model-editor.en.*
    └── visual-check-es/             ← capturas reales de la revisión visual (ver más abajo)
```

Los seis `*.architecture.json` dentro de `internal/` son la **especificación canónica editable** de
cada diagrama de arquitectura (ver más abajo); los dos `*.workflow.json` lo son para el diagrama de
proceso. Los ocho `*.html` son su render verificado. Los `*.finalize*.json`, `*.browser-check.json` y
`*.delivery.json` son los recibos deterministas de Archify (SHA-256, procedencia, evidencia de
navegador).

## Los tres niveles conceptuales

Archify 3.0.1 no ofrece un selector de audiencia nativo, así que los tres niveles pedidos se
resuelven como **tres pares de artefactos Archify independientes** (ES/EN cada uno) dentro de
`internal/`, unificados de cara al usuario por el portal `hydra-architecture.html` de arriba. Los
tres son proyecciones de la **misma arquitectura descubierta** y comparten los mismos IDs canónicos
de componente (`fe_workbench`, `b_project`, `b_metamodel`, `b_validation`, `b_model_editor`,
`b_grammar`, `b_event_gami`, `b_integration`, `b_transformation`, `b_recovery`, `b_publication`,
`b_persistence`, `b_security`, `b_audit`, `ext_remote`, `ext_toolchains`, `user`):

| Nivel | Archivos en `internal/` | Responde a | Evidencia de código |
|---|---|---|---|
| **USUARIO** | `hydra-user-overview.es/.en.html` | **QUÉ** puede hacer HYDRA, en lenguaje no técnico | No |
| **FUNCIONAL** | `hydra-functional-overview.es/.en.html` | **CÓMO** encajan los subsistemas y qué se dicen entre sí, sin bajar a clases/paquetes | No |
| **TÉCNICO** | `hydra-architecture.es/.en.html` | **CÓMO está implementado**: APIs REST reales, persistencia, seguridad | Sí — cada nodo cita archivo y línea reales |

El nivel FUNCIONAL muestra la misma topología de 16+1 nodos que el nivel TÉCNICO, pero sustituye las
rutas REST (`/api/transformations`) por descripciones de la interacción (`transforma`), y omite
`meta.repository`/`sources` por completo — Archify **exige** al menos una cita de fuente verificada
por componente en cuanto se declara `meta.repository` (comprobado al intentarlo:
`repository-evidence/source-required`), así que la ausencia deliberada de ambas en FUNCIONAL es la
forma correcta de expresar "sin detalle de clase/paquete" en el formato real de Archify.

El nivel USUARIO usa una topología más simple (12 nodos de los 17 canónicos) porque persistencia,
seguridad y auditoría son detalles de implementación que no responden a "qué puede hacer el
usuario"; reutiliza los mismos IDs canónicos para las capacidades que sí comparte con los otros dos
niveles.

## Especificación canónica / fuente editable

Se verificó explícitamente qué usa Archify 3.0.1 como formato fuente editable: cada uno de los cinco
modos (`architecture`, `workflow`, `sequence`, `dataflow`, `lifecycle`) consume un **JSON-IR
tipado y validado contra JSON Schema** (`schemas/architecture.schema.json` +
`schemas/common.schema.json`, ver `schemas/README.md` del skill instalado) antes de cualquier
trabajo de layout. Ese JSON-IR — los seis `*.architecture.json` en `internal/` — **es** la
especificación canónica editable de Archify; el HTML es una salida derivada y verificada
(`deliver`/`check`/`browser-check`), nunca la fuente de edición. No se ha inventado ningún otro
formato.

## Niveles de profundidad (MAP/READ/FULL) y selector de audiencia

Archify 3.0.1 **no** ofrece un selector de audiencia ni una "profundidad semántica" controlable por
el autor. Lo que sí ofrece es un detalle que se revela por **zoom**: `label` siempre visible,
`sublabel` a partir de 100% de zoom (READ), `tag`/notas solo por encima de 175% (FULL). La
distinción USUARIO/FUNCIONAL/TÉCNICO y el drill-down "alto nivel ↔ bajo nivel" por componente que
pedía el usuario se resolvieron con el **portal** (`hydra-architecture.html`): pestañas de nivel +
tarjetas expandibles por componente con progresión QUÉ HACE → CÓMO FUNCIONA → CÓMO ESTÁ IMPLEMENTADO
→ EVIDENCIA. Esto es una extensión construida encima de los HTML ya validados, no una función nativa
de Archify — Archify en sí mismo no se ha modificado.

## Idioma (ES/EN)

El visor de Archify no tiene conmutador de idioma en tiempo de ejecución: cada HTML fija su
`meta.locale` y su catálogo de traducciones de la UI al generarse. Por eso cada nivel tiene dos
artefactos (`.es.html` y `.en.html`). Los tres niveles ES usan ahora el catálogo completo de 438
claves de `examples/locales/es.json` del skill (no solo las de leyenda), así que la UI del visor
(botones, pestañas, atajos) sale también en español, no solo las etiquetas de los nodos — esto se
corrigió tras detectarlo en la revisión visual (ver abajo). El contenido propio del dominio se
tradujo manualmente conservando los mismos IDs, posiciones y topología; se verificó
programáticamente que ES y EN de cada nivel comparten exactamente los mismos nodos, conexiones
(`from`/`to`) y boundaries.

## Revisión visual realizada

Se generaron capturas reales con `visual-check` (Chrome real, no una aproximación) para el nivel
técnico ES y el funcional ES, guardadas en `internal/visual-check-es/`, y se inspeccionaron
directamente (no solo el recibo JSON):

- **Legibilidad**: todas las etiquetas de nodo, sublabel y conexión se leen sin solaparse, en ambos
  temas (claro/oscuro) y en los cuatro viewports (1440×900 a 2048×1320).
- **Hallazgo real y corregido**: la UI del visor en los artefactos FUNCIONAL/USUARIO en español salía
  en inglés ("Classic", "Export") porque solo se habían traducido unas pocas claves de leyenda. Se
  corrigió usando el catálogo `es.json` completo en los tres niveles ES y se volvió a verificar con
  una nueva captura.
- **Observación persistente, no bloqueante**: los ocho artefactos (3 niveles × 2 idiomas + técnico
  × 2) reportan la misma recomendación *advisory* `visualReviewRecommendation` — varias líneas de la
  fan-out del nodo del workbench se cruzan cerca de su propio borde. Inspeccionado visualmente: cada
  conexión sigue siendo trazable por su etiqueta y color, no hay texto ilegible ni superposición de
  cajas; es una zona visualmente densa, no rota. No se ha intentado un re-layout adicional para no
  arriesgar las validaciones ya en verde.

## Fuera de alcance de este diagrama

CI/CD (`.github/workflows/hydra-ci.yml`), los scripts de `scripts/release/`, `scripts/doctor/` y
`scripts/toolchain/`, y las políticas de `config/*.json` relacionadas con el build son
infraestructura de construcción y cualificación de releases, no arquitectura en ejecución. No están
representados como nodos.

## Cómo regenerar / actualizar

Sigue la rutina de la sección 25 del prompt de arquitectura: reconfirmar el estado de Git,
redescubrir la arquitectura desde el código actual, cargar **esta** carpeta como línea base previa,
comparar semánticamente, y ejecutar `finalize` **desde dentro de `internal/`** (los `meta.output` son
relativos a esa carpeta):

```bash
cd docs/architecture/archify/internal
node <ruta-al-skill-archify>/bin/archify.mjs finalize architecture \
  hydra-architecture.es.architecture.json hydra-architecture.es.html \
  --repo-root <ruta-al-repo-hydra> --quality showcase --json
```

(y análogamente para `.en.`, y para `hydra-functional-overview.*`/`hydra-user-overview.*` sin
`--repo-root`, porque ambos son proyecciones conceptuales sin citas de código). El portal
`hydra-architecture.html` no necesita regenerarse salvo que cambien los IDs canónicos, las
etiquetas o la ruta de los artefactos internos — su capa de datos vive en el propio archivo.

El contenido de las vistas CAPACIDADES y PROCESOS vive por separado, en
`hydra-architecture-data.js` (`window.CAPABILITIES`, `window.CAPABILITIES[id].archComponentId` enlaza
cada capacidad con su nodo de arquitectura; `window.PROCESSES`). No requiere `finalize` de Archify — es
JSON de datos cargado directamente por el portal — pero sí debe mantenerse con evidencia real de
archivo+línea si se amplía: verificar con `node --check hydra-architecture-data.js` tras cualquier
edición manual, y confirmar en el navegador (headless o no) que `Object.keys(window.CAPABILITIES)` y
`window.PROCESSES` reflejan el recuento esperado antes de considerar el cambio terminado.

No hacer commit/push automáticamente: la revisión humana del diff es obligatoria antes de versionar.
