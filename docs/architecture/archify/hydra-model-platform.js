/* HYDRA portal — content: platform and external capabilities
   project, recovery, toolchains, external_repositories, portability + infrastructure (persistence, audit, security).
   Sources: legacy facts (hydra-architecture-data.js / hydra-architecture.html components) + targeted source reads. */
(function(){ "use strict";
  var HM = window.HM, T = HM.T;

  // ---- local helpers (data only) ----
  function V(n, nn, d, dd, req){ return { n: T(n, nn), d: T(d, dd), req: req || "ALTERNATIVE" }; }
  function PR(n, nn, req, d, dd){ return { n: T(n, nn), req: req, d: T(d, dd) }; }
  function EV(path, line, label){ return { path: path, line: line, label: label }; }
  function N(id, t, a, r, cap, l, u, x){
    var o = { id: id, t: t, cap: cap, l: T(l[0], l[1]) };
    if (u) o.u = T(u[0], u[1]);
    if (a) o.a = a; if (r) o.r = r;
    if (x) for (var k in x) { if (Object.prototype.hasOwnProperty.call(x, k)) {
      o[k] = (k === "need" || k === "out" || k === "f" || k === "s") ? T(x[k][0], x[k][1]) : x[k]; } }
    return o;
  }
  function E(a, b, k, es, en){ return es ? [a, b, k || "next", T(es, en)] : (k ? [a, b, k] : [a, b]); }

  var P1 = "src/main/java/com/example/editor/project";
  var PREC = "src/main/java/com/example/editor/metamodel/importing";
  var PUPD = "src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteMetamodelRecoveryService.java";
  var PMAT = "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java";
  var PTC = "src/main/java/com/example/editor/integration/compatibility";
  var PNE = "src/main/java/com/example/editor/transformation/nativeexec";
  var PRP = "src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java";
  var PRA = "src/main/java/com/example/editor/remote/auth/RemoteAuthService.java";
  var PRG = "src/main/java/com/example/editor/remote/git/RemoteGitWorkspaceService.java";
  var PPK = "src/main/java/com/example/editor/portability/archive";
  var PPM = "src/main/java/com/example/editor/portability/merge";
  var PPT = "src/main/java/com/example/editor/portability/transaction";
  var PCS = "src/main/java/com/example/editor/persistence/CanonicalDocumentStore.java";
  var PCF = "src/main/java/com/example/editor/persistence/LocalCanonicalDocumentStoreFactory.java";
  var PHF = "src/main/java/com/example/editor/security/input/HydraRequestHardeningFilter.java";
  var PHR = "src/main/java/com/example/editor/security/process/HydraExternalProcessRunner.java";
  var PAU = "src/main/java/com/example/editor/audit/JsonlAuditLogStore.java";

  // =====================================================================================================
  // PROJECT
  // =====================================================================================================
  HM.cap("project", {
    name: T("Gestión de proyectos", "Project management"), group: "platform",
    user: {
      tagline: T("El contenedor de todo tu trabajo", "The container for all your work"),
      what: T("Un proyecto reúne todo lo que creas en HYDRA y decide qué áreas de la herramienta están disponibles para él. Desde aquí abres, organizas, importas y exportas tu trabajo.",
              "A project gathers everything you create in HYDRA and decides which areas of the tool are available to it. From here you open, organize, import and export your work."),
      why: T("Te permite trabajar solo con lo que necesitas y llevar tu trabajo dentro y fuera de HYDRA sin perder nada.",
             "It lets you work only with what you need and carry your work in and out of HYDRA without losing anything."),
      when: T("Al empezar a trabajar, al cambiar de proyecto, al activar o desactivar áreas y al compartir o respaldar tu trabajo.",
              "When you start working, switch project, turn areas on or off, and share or back up your work."),
      canDefine: [T("Qué áreas del proyecto están activas", "Which project areas are active"), T("El modo de trabajo: guiado o libre", "The working mode: guided or free"),
        T("El artefacto principal del proyecto", "The project's main artifact"), T("Qué se importa y de dónde", "What is imported and from where"),
        T("Qué se exporta y adónde", "What is exported and where")],
      needs: [T("Nada para empezar: se puede crear un proyecto nuevo", "Nothing to start: a new project can be created"),
        T("Para importar: una carpeta, un archivo ZIP, un repositorio remoto o un archivo de proyecto externo", "To import: a folder, a ZIP file, a remote repository or an external project file"),
        T("Para exportar a un repositorio: una conexión ya configurada", "To export to a repository: an already configured connection")],
      produces: [T("Un árbol de archivos organizado por tipo de artefacto", "A file tree organized by artifact kind"), T("Un proyecto activo con las áreas correctas habilitadas", "An active project with the right areas enabled"),
        T("Archivos ZIP o contenido enviado a un repositorio", "ZIP files or content sent to a repository"), T("Un registro de todos los cambios hechos", "A record of every change made")],
      mandatory: [T("El árbol de archivos del proyecto", "The project file tree"), T("El panel de inicio (Home)", "The Home panel")],
      optional: [T("Activar o desactivar áreas", "Turn areas on or off"), T("Exportar el proyecto o una selección", "Export the project or a selection")],
      conditional: [T("Importar un archivo de proyecto externo (lanza la recuperación semántica)", "Import an external project file (starts semantic recovery)")],
      next: [T("Definir un metamodelo", "Define a metamodel"), T("Conectar un repositorio externo", "Connect an external repository"), T("Recuperar un proyecto externo", "Recover an external project"), T("Mover capacidades a otro proyecto", "Move capabilities to another project")],
      interacts: [{ cap: "recovery", t: T("Importar un archivo de proyecto externo dispara la recuperación completa", "Importing an external project file triggers the full recovery") },
        { cap: "external_repositories", t: T("Importar y exportar con un repositorio remoto", "Import and export with a remote repository") },
        { cap: "integration", t: T("Algunas entregas exigen áreas del proyecto activas", "Some deliveries require project areas to be active") }]
    },
    func: {
      lifecycle: [T("proyecto nuevo o importado → áreas activadas → árbol navegable → trabajo → exportación", "new or imported project → areas enabled → navigable tree → work → export"),
        T("área activada ↔ desactivada: el árbol se recalcula al instante", "area enabled ↔ disabled: the tree is recomputed immediately"),
        T("cada cambio en cualquier documento queda registrado en la auditoría", "every change to any document is recorded in the audit")],
      semantics: [
        T("Hay 25 áreas posibles por proyecto (0..25 activas); la restricción la aplica el servidor, no solo la pantalla.", "There are 25 possible areas per project (0..25 active); the restriction is enforced by the server, not only on screen."),
        T("Activar un área activa las que necesita; desactivarla desactiva las que dependen de ella.", "Enabling an area enables the ones it needs; disabling it disables the ones that depend on it."),
        T("El modo de trabajo es Guiado o Libre.", "The working mode is Guided or Free."),
        T("Importar desde un archivo de proyecto externo sí dispara la recuperación semántica; importar desde un repositorio remoto es solo restaurar archivos.", "Importing from an external project file does trigger semantic recovery; importing from a remote repository only restores files."),
        T("El artefacto principal (tipo, identidad y ruta) es el punto de entrada de Home, previsualización, generación y ejecución.", "The main artifact (kind, identity and path) is the entry point for Home, preview, generation and run."),
        T("Home muestra hasta 6 proyectos recientes.", "Home shows up to 6 recent projects.")],
      states: [{ n: T("Área activa", "Area active"), d: T("Visible y utilizable en el árbol", "Visible and usable in the tree") }, { n: T("Área inactiva", "Area inactive"), d: T("Oculta y bloqueada realmente", "Hidden and truly blocked") },
        { n: T("Cambiado externamente", "Changed externally"), d: T("Indicador en el árbol cuando un archivo cambia fuera de HYDRA", "Tree indicator when a file changes outside HYDRA") }],
      cross: [T("Integración exige áreas del proyecto activas antes de entregar una tecnología.", "Integration requires project areas to be active before delivering a technology."),
        T("Todas las capacidades operan dentro del alcance de un proyecto y sus áreas activas.", "All capabilities operate within a project and its active areas.")]
    },
    tech: {
      runtime: T("Servicio de índice de proyecto en el backend que calcula la raíz lógica activa/inactiva; el frontend consume el árbol ya filtrado.", "Backend project-index service that computes the active/inactive logical root; the frontend consumes the already filtered tree."),
      services: ["ProjectIndexService", "LocalCanonicalDocumentStoreFactory", "AuditCanonicalDocumentMutationObserver"],
      apis: ["REST de proyecto (Home, árbol lógico, artefacto primario)"],
      schemas: ["ProjectCapability (enum)", "ProjectPrimaryArtifact (kind, id, relativePath)"],
      tests: [],
      evidence: [EV(P1 + "/ProjectCapability.java", 4, "ProjectCapability enum"), EV(P1, 1, "ProjectIndexService.logicalRoot / ProjectPrimaryArtifact")],
      notes: [T("Conflicto de evidencia: los datos heredados dicen 24 valores de ProjectCapability; la lectura del enum actual cuenta 25 (de METAMODEL_DESIGN a LOCAL_FILE_EDITING). Se usa 25.", "Evidence conflict: legacy data says 24 ProjectCapability values; reading the current enum counts 25 (METAMODEL_DESIGN to LOCAL_FILE_EDITING). 25 is used."),
        T("Los nombres de pestañas/diálogos y las etiquetas de la interfaz proceden de los datos heredados; no se releyeron los componentes del explorador de proyectos.", "Tab/dialog names and UI labels come from legacy data; the project explorer components were not re-read.")]
    },
    elements: [
      { id: "tree", n: T("Árbol de archivos del proyecto", "Project file tree"), kind: "concept", req: "MANDATORY",
        mean: T("La vista principal del proyecto: una fila por elemento con un icono según el tipo de artefacto.", "The project's main view: one row per item with an icon by artifact kind."),
        purpose: T("Navegar, abrir y organizar todo el contenido del proyecto.", "Navigate, open and organize all of the project's content."), when: T("Siempre que trabajas dentro de un proyecto.", "Whenever you work inside a project."),
        card: "1 proyecto ↔ 1 árbol ↔ 0..N elementos",
        props: [PR("Selección múltiple", "Multi-selection", "OPTIONAL", "Clic con Ctrl/Cmd añade elementos a la selección", "Ctrl/Cmd+click adds items to the selection"), PR("Abrir", "Open", "OPTIONAL", "Doble clic abre el elemento", "Double-click opens the item"),
          PR("Cambiado externamente", "Changed externally", "DERIVED", "Aviso cuando un archivo se modificó fuera de HYDRA", "Warning when a file was modified outside HYDRA")],
        io: { i: T("Contenido del proyecto y áreas activas", "Project content and active areas"), o: T("Elementos abiertos, seleccionados o exportables", "Items opened, selected or exportable") },
        effect: T("Cada área activa aporta sus propios tipos de artefacto al árbol.", "Each active area contributes its own artifact kinds to the tree."),
        func: { rel: [T("Se calcula a partir de las áreas activas", "Computed from the active areas"), T("Su selección alimenta la exportación", "Its selection feeds export")], lifecycle: T("Se actualiza cuando cambian las áreas o los archivos.", "Refreshes when areas or files change."), cond: T("Solo muestra áreas activas.", "Shows active areas only.") },
        tech: { type: "Árbol expandible (frontend project-explorer)", schema: "logicalRoot", evidence: [EV(P1, 1, "ProjectIndexService.logicalRoot")] } },
      { id: "areas", n: T("Áreas del proyecto", "Project areas"), kind: "variant-set", req: "OPTIONAL",
        mean: T("El catálogo cerrado de capacidades que un proyecto puede tener activas o inactivas.", "The closed catalog of capabilities a project can have active or inactive."),
        purpose: T("Limitar el proyecto a lo que realmente necesita.", "Limit the project to what it really needs."), when: T("Al preparar el proyecto o cuando cambian tus necesidades.", "When preparing the project or when your needs change."),
        card: "1 proyecto ↔ 0..25 áreas activas",
        variants: [
          V("Diseño del lenguaje", "Language design", "Diseño de metamodelo y de gramática", "Metamodel design and grammar design", "OPTIONAL"),
          V("Edición de modelos", "Model editing", "Edición de modelos, modelado visual y modelado textual", "Model editing, visual modeling and textual modeling", "OPTIONAL"),
          V("Editores", "Editors", "Definiciones de editor, editor visual, editor textual, estilo de sintaxis e información contextual", "Editor definitions, visual editor, textual editor, syntax styling and contextual information", "OPTIONAL"),
          V("Comportamiento", "Behaviour", "Validaciones, Quick Fixes, operaciones, eventos, flujos de eventos y gamificación", "Validations, Quick Fixes, operations, events, event flows and gamification", "OPTIONAL"),
          V("Transformaciones", "Transformations", "De modelo a modelo, de modelo a texto y de texto a modelo", "Model to model, model to text and text to model", "OPTIONAL"),
          V("Generación e integración", "Generation and integration", "Generación de editor, integraciones, integración con repositorios, exportación DrawIO y exportación PlantUML", "Editor generation, integrations, repository integration, DrawIO export and PlantUML export", "OPTIONAL"),
          V("Edición de archivos locales", "Local file editing", "Edición directa de archivos locales", "Direct editing of local files", "OPTIONAL")],
        props: [PR("Dependencias automáticas", "Automatic dependencies", "DERIVED", "Activar un área activa las que necesita; desactivarla apaga las que dependen de ella", "Enabling an area enables what it needs; disabling it turns off what depends on it")],
        io: { i: T("Decisión de activar o desactivar", "Decision to enable or disable"), o: T("Árbol y funciones realmente disponibles", "Tree and functions really available") },
        effect: T("La restricción se aplica en el servidor: no se puede reactivar un área manipulando la pantalla.", "The restriction is enforced on the server: an area cannot be re-enabled by tampering with the screen."),
        func: { rel: [T("Determina la raíz lógica del proyecto", "Determines the project's logical root"), T("Integración puede exigir ciertas áreas activas", "Integration may require certain areas to be active")], lifecycle: T("activa ↔ inactiva; recalcula el árbol.", "active ↔ inactive; recomputes the tree."), cond: T("Dependencias entre áreas: arrastran a otras al activar/desactivar.", "Dependencies between areas: they drag others along when enabled/disabled.") },
        tech: { type: "ProjectCapability (25 valores en el enum actual)", schema: "enum ProjectCapability", evidence: [EV(P1 + "/ProjectCapability.java", 4, "ProjectCapability")] } },
      { id: "workmode", n: T("Modo de trabajo", "Working mode"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Un interruptor en el diálogo de áreas que cambia cómo se te guía.", "A switch in the areas dialog that changes how you are guided."),
        purpose: T("Elegir entre acompañamiento paso a paso o libertad total.", "Choose between step-by-step guidance and full freedom."), when: T("Al abrir el diálogo de áreas del proyecto.", "When opening the project areas dialog."),
        card: "1 proyecto ↔ 1 modo",
        variants: [V("Guiado", "Guided", "Te acompaña en el orden de trabajo", "Accompanies you through the working order", "ALTERNATIVE"), V("Libre", "Free", "Sin orden impuesto", "No imposed order", "ALTERNATIVE")],
        io: { i: T("Elección del usuario", "User choice"), o: T("Experiencia guiada o libre", "Guided or free experience") }, effect: T("Cambia la ayuda de navegación; no cambia qué áreas están activas.", "Changes navigation help; does not change which areas are active."),
        func: { rel: [T("Vive junto a la cuadrícula de áreas", "Lives next to the areas grid")], lifecycle: T("Se puede cambiar en cualquier momento.", "Can be changed at any time."), cond: T("Efectos concretos del modo no verificados en esta revisión.", "Concrete effects of the mode were not verified in this review.") },
        tech: { type: "Interruptor Guiado/Libre del diálogo modal", schema: "—", evidence: [EV(P1, 1, "Paquete project")] } },
      { id: "logical", n: T("Árbol lógico calculado", "Computed logical tree"), kind: "derived", req: "DERIVED",
        mean: T("La versión del árbol del proyecto que respeta las áreas activas.", "The version of the project tree that respects the active areas."),
        purpose: T("Garantizar que lo que ves es lo que realmente puedes usar.", "Guarantee that what you see is what you can really use."), when: T("Cada vez que cambian las áreas o navegas.", "Every time areas change or you navigate."),
        card: "1 proyecto ↔ 1 árbol calculado",
        io: { i: T("Áreas activas", "Active areas"), o: T("Árbol navegable", "Navigable tree") }, effect: T("Se recalcula de inmediato al cambiar áreas.", "Recomputed immediately when areas change."),
        func: { rel: [T("Se deriva de las áreas; alimenta el árbol de archivos", "Derived from areas; feeds the file tree")], lifecycle: T("Se recalcula de forma reactiva.", "Recomputed reactively."), cond: T("Sin áreas activas no hay grupos navegables.", "Without active areas there are no navigable groups.") },
        tech: { type: "logicalRoot", schema: "ProjectIndexService", evidence: [EV(P1, 1, "ProjectIndexService.logicalRoot")] } },
      { id: "primary", n: T("Artefacto principal", "Main artifact"), kind: "concept", req: "OPTIONAL",
        mean: T("El elemento central que representa al proyecto, por ejemplo su metamodelo principal.", "The central item that represents the project, for example its main metamodel."),
        purpose: T("Dar un punto de entrada único a Home, previsualización, generación y ejecución.", "Give a single entry point to Home, preview, generation and run."), when: T("Al continuar trabajando o lanzar flujos de alto nivel.", "When resuming work or launching high-level flows."),
        card: "1 proyecto ↔ 1 artefacto principal (normalmente)",
        props: [PR("Tipo", "Kind", "MANDATORY", "Qué clase de artefacto es", "What kind of artifact it is"), PR("Identidad", "Identity", "MANDATORY", "Cuál es", "Which one it is"), PR("Ruta relativa", "Relative path", "MANDATORY", "Dónde vive dentro del proyecto", "Where it lives inside the project")],
        io: { i: T("Selección del artefacto central", "Choice of the central artifact"), o: T("Tarjeta Continuar trabajando", "Continue working card") }, effect: T("Home lo usa para ofrecer continuar donde lo dejaste.", "Home uses it to offer resuming where you left off."),
        func: { rel: [T("Consumido por Home, previsualización, generación y ejecución", "Consumed by Home, preview, generation and run")], lifecycle: T("Puede cambiar si el proyecto se reorganiza.", "May change if the project is reorganized."), cond: T("Normalmente uno por proyecto.", "Normally one per project.") },
        tech: { type: "ProjectPrimaryArtifact", schema: "kind, id, relativePath", evidence: [EV(P1, 1, "ProjectPrimaryArtifact")] } },
      { id: "home", n: T("Panel de inicio (Home)", "Home panel"), kind: "concept", req: "MANDATORY",
        mean: T("Un tablero real con las acciones principales y el estado del proyecto activo.", "A real dashboard with the main actions and the state of the active project."),
        purpose: T("Empezar, continuar y preparar el trabajo desde un solo sitio.", "Start, resume and prepare work from a single place."), when: T("Al abrir HYDRA o cambiar de proyecto.", "When opening HYDRA or switching project."),
        card: "1 sesión ↔ 1 Home ↔ 0..6 proyectos recientes",
        props: [PR("Acciones principales", "Primary actions", "MANDATORY", "Nuevo, abrir, importar y conectar", "New, open, import and connect"), PR("Continuar trabajando", "Continue working", "CONDITIONAL", "Tarjeta basada en el artefacto principal del proyecto activo", "Card based on the active project's main artifact"),
          PR("Resumen y áreas habilitadas", "Summary and enabled areas", "OPTIONAL", "Tarjetas del proyecto y sus áreas", "Cards for the project and its areas"), PR("Presets de creación rápida", "Quick-creation presets", "OPTIONAL", "Atajos para crear contenido típico", "Shortcuts to create typical content"),
          PR("Panel de preparación", "Preparation panel", "OPTIONAL", "Estado de preparación de capacidades embebido", "Embedded capability readiness status"), PR("Proyectos recientes", "Recent projects", "OPTIONAL", "Hasta 6", "Up to 6")],
        io: { i: T("Proyecto activo y recientes", "Active and recent projects"), o: T("Punto de partida del trabajo", "Starting point for work") }, effect: T("Es un tablero con contenido real, no solo un enrutador.", "It is a dashboard with real content, not just a router."),
        func: { rel: [T("Usa el artefacto principal; enlaza con importar y con las conexiones externas", "Uses the main artifact; links to import and to external connections")], lifecycle: T("Siempre disponible.", "Always available."), cond: T("Continuar trabajando solo aparece si hay proyecto activo.", "Continue working only appears when there is an active project.") },
        tech: { type: "Panel Home (frontend)", schema: "—", evidence: [EV(P1, 1, "Paquete project")] } },
      { id: "import", n: T("Importar al proyecto", "Import into the project"), kind: "variant-set", req: "ALTERNATIVE",
        mean: T("Traer contenido al proyecto desde uno de cuatro orígenes.", "Bring content into the project from one of four sources."),
        purpose: T("Reutilizar trabajo existente sin reescribirlo.", "Reuse existing work without rewriting it."), when: T("Al empezar con material que ya tienes.", "When starting from material you already have."),
        card: "1 importación ↔ 1 origen",
        variants: [V("Carpeta local", "Local folder", "Trae el contenido de una carpeta de tu equipo", "Brings in the content of a folder on your computer"), V("Archivo ZIP", "ZIP file", "Restaura un proyecto empaquetado", "Restores a packaged project"),
          V("Repositorio remoto", "Remote repository", "Restauración simple de archivos; no dispara la recuperación semántica", "Simple file restore; does not trigger semantic recovery"),
          V("Archivo de proyecto externo", "External project file", "Dispara la recuperación semántica completa, como vía alternativa a Recuperación externa", "Triggers the full semantic recovery, as an alternative route to External recovery")],
        io: { i: T("Origen elegido", "Chosen source"), o: T("Contenido en el proyecto", "Content in the project") }, effect: T("El archivo de proyecto externo materializa metamodelo y reglas; las otras vías copian archivos.", "The external project file materializes metamodel and rules; the other routes copy files."),
        func: { rel: [T("La vía de archivo externo se apoya en Recuperación externa", "The external file route relies on External recovery"), T("La vía remota se apoya en Repositorios externos", "The remote route relies on External repositories")], lifecycle: T("origen → importación → árbol actualizado", "source → import → updated tree"), cond: T("Solo una de las cuatro vías por importación.", "Only one of the four routes per import.") },
        tech: { type: "Diálogo de importación (4 orígenes)", schema: "—", evidence: [EV(P1, 1, "Paquete project")] } },
      { id: "export", n: T("Exportar desde el proyecto", "Export from the project"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Sacar el proyecto o una selección hacia un archivo o un repositorio.", "Take the project or a selection out to a file or a repository."),
        purpose: T("Respaldar y compartir el trabajo.", "Back up and share the work."), when: T("Al terminar un hito o para compartir.", "When finishing a milestone or sharing."),
        card: "1 exportación ↔ 1 destino",
        variants: [V("Selección como ZIP", "Selection as ZIP", "Descarga local de lo seleccionado", "Local download of the selection"), V("A un repositorio", "To a repository", "Diálogo de destino remoto; requiere una conexión configurada", "Remote destination dialog; requires a configured connection", "CONDITIONAL")],
        io: { i: T("Selección del árbol", "Tree selection"), o: T("ZIP o contenido en un repositorio", "ZIP or content in a repository") }, effect: T("No modifica el proyecto.", "Does not modify the project."),
        func: { rel: [T("Parte de la selección del árbol; el destino remoto usa Repositorios externos", "Starts from the tree selection; the remote destination uses External repositories")], lifecycle: T("selección → destino → archivo o envío", "selection → destination → file or upload"), cond: T("Exportar a repositorio solo con conexión.", "Exporting to a repository requires a connection.") },
        tech: { type: "Botones de barra/menú contextual + diálogo", schema: "—", evidence: [EV(P1, 1, "Paquete project")] } }
    ],
    map: {
      nodes: [
        { id: "proj", n: T("Proyecto", "Project"), k: "concept", req: "MANDATORY", d: T("El contenedor de todo", "The container for everything") },
        { id: "tree", elem: "tree", n: T("Árbol de archivos", "File tree"), k: "concept", req: "MANDATORY", d: T("Vista principal", "Main view") },
        { id: "areas", elem: "areas", n: T("Áreas del proyecto", "Project areas"), k: "concept", req: "OPTIONAL", d: T("Qué está activo", "What is active") },
        { id: "mode", elem: "workmode", n: T("Modo de trabajo", "Working mode"), k: "variant", req: "OPTIONAL", d: T("Guiado o libre", "Guided or free") },
        { id: "logical", elem: "logical", n: T("Árbol lógico", "Logical tree"), k: "derived", req: "DERIVED", d: T("Respeta las áreas", "Respects the areas") },
        { id: "primary", elem: "primary", n: T("Artefacto principal", "Main artifact"), k: "concept", req: "OPTIONAL", d: T("Punto de entrada", "Entry point") },
        { id: "home", elem: "home", n: T("Home", "Home"), k: "concept", req: "MANDATORY", d: T("Tablero de inicio", "Start dashboard") },
        { id: "imp", elem: "import", n: T("Importar", "Import"), k: "concept", req: "ALTERNATIVE", d: T("4 orígenes", "4 sources") },
        { id: "exp", elem: "export", n: T("Exportar", "Export"), k: "concept", req: "OPTIONAL", d: T("ZIP o repositorio", "ZIP or repository") },
        { id: "repo", n: T("Repositorio remoto", "Remote repository"), k: "external", req: "OPTIONAL", d: T("Origen o destino", "Source or destination") }
      ],
      edges: [
        { a: "proj", b: "areas", rel: "contains", card1: "1", card2: "0..25", req: "OPTIONAL" }, { a: "proj", b: "tree", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
        { a: "mode", b: "areas", rel: "isa", req: "OPTIONAL" }, { a: "areas", b: "logical", rel: "gen", req: "MANDATORY" }, { a: "logical", b: "tree", rel: "gen", req: "MANDATORY" },
        { a: "proj", b: "primary", rel: "contains", card1: "1", card2: "0..1", req: "OPTIONAL" }, { a: "home", b: "primary", rel: "ref", req: "CONDITIONAL" },
        { a: "home", b: "imp", rel: "ref", req: "OPTIONAL" }, { a: "tree", b: "exp", rel: "ref", req: "OPTIONAL" },
        { a: "imp", b: "repo", rel: "ref", req: "CONDITIONAL" }, { a: "exp", b: "repo", rel: "ref", req: "CONDITIONAL" }
      ]
    },
    define: [
      { id: "tree", n: T("Árbol de archivos", "File tree"), kind: "direct", req: "MANDATORY", elems: ["tree", "primary"],
        does: T("Despliegas el árbol, haces doble clic para abrir un elemento y Ctrl/Cmd+clic para seleccionar varios. Un indicador te avisa si un archivo cambió fuera de HYDRA.", "You expand the tree, double-click to open an item and Ctrl/Cmd+click to select several. An indicator warns you if a file changed outside HYDRA."),
        func: T("El árbol solo contiene las áreas activas; la selección múltiple alimenta la exportación.", "The tree only contains active areas; multi-selection feeds export."), tech: T("Árbol del explorador de proyectos (frontend project-explorer), alimentado por la raíz lógica.", "Project explorer tree (frontend project-explorer), fed by the logical root."), proc: "project_author_tree" },
      { id: "areas", n: T("Diálogo «Áreas del proyecto»", "\"Project areas\" dialog"), kind: "form", req: "OPTIONAL", elems: ["areas", "workmode"],
        does: T("Abres el diálogo, eliges modo Guiado o Libre y marcas o desmarcas las áreas en una cuadrícula. Las dependencias se aplican solas y confirmas.", "You open the dialog, pick Guided or Free mode and tick or untick areas in a grid. Dependencies apply by themselves and you confirm."),
        func: T("Activar arrastra las necesarias; desactivar apaga las dependientes.", "Enabling pulls in the needed ones; disabling turns off the dependents."), tech: T("Diálogo modal; el servidor recalcula la raíz lógica.", "Modal dialog; the server recomputes the logical root."), proc: "project_author_areas" },
      { id: "import", n: T("Diálogo de importación", "Import dialog"), kind: "import", req: "ALTERNATIVE", elems: ["import"],
        does: T("Desde Home o el menú abres el diálogo de importación y eliges uno de cuatro orígenes: carpeta local, ZIP, repositorio remoto o archivo de proyecto externo.", "From Home or the menu you open the import dialog and pick one of four sources: local folder, ZIP, remote repository or external project file."),
        func: T("Solo el archivo de proyecto externo dispara la recuperación semántica.", "Only the external project file triggers semantic recovery."), tech: T("Diálogo con 4 orígenes; el origen externo enlaza con la materialización de Recuperación externa.", "Dialog with 4 sources; the external source links to External recovery materialization."), proc: "project_author_import" },
      { id: "export", n: T("Exportar selección", "Export selection"), kind: "form", req: "OPTIONAL", elems: ["export"],
        does: T("Seleccionas elementos en el árbol y usas la barra o el menú contextual: «Exportar selección como ZIP» descarga; «Exportar a repositorio…» abre el diálogo de destino remoto.", "You select items in the tree and use the toolbar or context menu: \"Export selection as ZIP\" downloads; \"Export to repository…\" opens the remote destination dialog."),
        func: T("El envío remoto exige una conexión configurada.", "Remote upload requires a configured connection."), tech: T("Botones de barra/menú contextual + diálogo de destino.", "Toolbar buttons/context menu + destination dialog."), proc: "project_author_export" },
      { id: "home", n: T("Panel de inicio", "Home panel"), kind: "direct", req: "MANDATORY", elems: ["home", "primary"],
        does: T("Desde Home pulsas una acción principal (nuevo, abrir, importar, conectar), la tarjeta «Continuar trabajando» o un proyecto reciente.", "From Home you press a primary action (new, open, import, connect), the \"Continue working\" card or a recent project."),
        func: T("«Continuar trabajando» usa el artefacto principal del proyecto activo.", "\"Continue working\" uses the active project's main artifact."), tech: T("Panel Home del frontend.", "Frontend Home panel."), proc: "project_author_home" }
    ],
    rel: [
      { to: "recovery", type: "conditional", dir: "out", u: T("Importar un archivo de proyecto externo inicia la recuperación.", "Importing an external project file starts recovery."), f: T("Solo desde el origen «archivo de proyecto externo».", "Only from the \"external project file\" source."), t: T("El diálogo de importación enlaza con la materialización semántica.", "The import dialog links to semantic materialization.") },
      { to: "external_repositories", type: "optional", dir: "out", u: T("Importas o exportas con un repositorio remoto.", "You import or export with a remote repository."), f: T("Transferencia de archivos; no recuperación semántica.", "File transfer; not semantic recovery."), t: T("Export/import por la capacidad de proveedores remotos.", "Export/import through the remote providers capability.") },
      { to: "integration", type: "required", dir: "out", u: T("Algunas entregas exigen áreas del proyecto activas.", "Some deliveries require project areas to be active."), f: T("Las áreas requeridas deben estar activas antes de entregar.", "Required areas must be active before delivering."), t: T("requiredCapabilities del perfil de integración.", "requiredCapabilities of the integration profile.") },
      { to: "audit", type: "producer", dir: "out", u: T("Todo cambio queda anotado en el registro de auditoría.", "Every change is noted in the audit record."), f: T("Siempre activo; no depende de las áreas.", "Always on; independent of areas."), t: T("Observador de mutaciones conectado a cada almacén de documentos.", "Mutation observer attached to every document store.") },
      { to: "portability", type: "optional", dir: "out", u: T("Las capacidades del proyecto se pueden empaquetar y mover.", "The project's capabilities can be packaged and moved."), f: T("Es la ampliación transaccional de la preparación de capacidades.", "It is the transactional extension of capability preparation."), t: T("Módulo de preparación del proyecto respaldado por transacciones de capa.", "Project preparation module backed by layer transactions.") }
    ]
  });

  HM.proc({ id: "project_open_organize", scope: "cap", cap: "project", elems: ["home", "areas", "tree", "primary"],
    n: T("Abrir, preparar y organizar un proyecto", "Open, prepare and organize a project"), d: T("De Home a un proyecto activo con las áreas correctas y el árbol listo para trabajar.", "From Home to an active project with the right areas and the tree ready to work."),
    nodes: [
      N("s", "start", "user", null, "project", ["Abrir HYDRA", "Open HYDRA"], ["Se muestra Home, con o sin proyecto activo.", "Home is shown, with or without an active project."]),
      N("d1", "decision", "user", "MANDATORY", "project", ["¿Continuar, nuevo o importar?", "Continue, new or import?"], ["Eliges cómo entrar al trabajo para no empezar desde cero.", "You choose how to enter the work so you do not start from scratch."]),
      N("a1", "step", "user", "OPTIONAL", "project", ["Continuar el reciente", "Resume the recent one"], ["Abres el proyecto activo por su artefacto principal.", "You open the active project through its main artifact."], { e: "primary", need: ["Un proyecto reciente", "A recent project"], out: ["Proyecto activo", "Active project"] }),
      N("a2", "step", "user", "OPTIONAL", "project", ["Crear un proyecto nuevo", "Create a new project"], ["Empiezas con un proyecto vacío.", "You start with an empty project."], { out: ["Proyecto activo vacío", "Empty active project"] }),
      N("a3", "step", "user", "OPTIONAL", "project", ["Importar", "Import"], ["Traes contenido existente (ver el proceso de importación).", "You bring in existing content (see the import process)."], { out: ["Proyecto con contenido", "Project with content"] }),
      N("a4", "step", "user", "OPTIONAL", "project", ["Preparar las áreas", "Prepare the areas"], ["Activas o desactivas áreas para trabajar solo con lo que necesitas.", "You enable or disable areas to work only with what you need."], { e: "areas", need: ["Un proyecto activo", "An active project"], out: ["Conjunto de áreas activas", "Set of active areas"], f: ["Activar arrastra dependencias; desactivar apaga las dependientes.", "Enabling pulls in dependencies; disabling turns off dependents."], s: ["área: inactiva ↔ activa", "area: inactive ↔ active"], x: { rt: "ProjectIndexService", test: [], ev: [EV(P1, 1, "ProjectIndexService")] } }),
      N("a5", "step", "auto", "MANDATORY", "project", ["Recalcular el árbol", "Recompute the tree"], ["HYDRA recalcula al instante qué grupos son navegables; la restricción es real, no solo visual.", "HYDRA immediately recomputes which groups are navigable; the restriction is real, not just visual."], { e: "logical", out: ["Árbol lógico actualizado", "Updated logical tree"], x: { rt: "ProjectIndexService.logicalRoot", test: [], ev: [EV(P1, 1, "logicalRoot")] } }),
      N("a6", "step", "user", "MANDATORY", "project", ["Navegar y organizar el árbol", "Navigate and organize the tree"], ["Abres y organizas archivos; cada área activa aporta sus tipos de artefacto.", "You open and organize files; each active area contributes its artifact kinds."], { e: "tree" }),
      N("r", "artifact", null, null, "project", ["Proyecto activo y organizado", "Active, organized project"], ["Un proyecto con las áreas correctas listo para el trabajo previsto.", "A project with the right areas ready for the intended work."]),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "d1"), E("d1", "a1", "next", "Continuar", "Continue"), E("d1", "a2", "alt", "Nuevo", "New"), E("d1", "a3", "alt", "Importar", "Import"), E("a1", "a4", "opt"), E("a2", "a4", "opt"), E("a3", "a4", "opt"), E("a4", "a5"), E("a1", "a6"), E("a5", "a6"), E("a6", "a4", "loop", "Cambiar áreas", "Change areas"), E("a6", "r"), E("r", "e")] });

  HM.proc({ id: "project_import_export", scope: "cap", cap: "project", elems: ["import", "export"],
    n: T("Importar y exportar", "Import and export"), d: T("Entrar contenido al proyecto y sacarlo hacia un archivo o repositorio.", "Bring content into the project and take it out to a file or repository."),
    nodes: [
      N("s", "start", "user", null, "project", ["Necesito mover contenido", "I need to move content"], ["Quieres traer o llevar contenido del proyecto.", "You want to bring content in or take it out."]),
      N("d1", "decision", "user", "MANDATORY", "project", ["¿Importar o exportar?", "Import or export?"], ["Cada sentido usa un diálogo distinto.", "Each direction uses a different dialog."]),
      N("i1", "step", "user", "MANDATORY", "project", ["Elegir el origen", "Choose the source"], ["Carpeta local, ZIP, repositorio remoto o archivo de proyecto externo.", "Local folder, ZIP, remote repository or external project file."], { e: "import", need: ["Contenido accesible", "Accessible content"] }),
      N("i2", "decision", "auto", "MANDATORY", "project", ["¿Archivo de proyecto externo?", "External project file?"], ["Solo este origen dispara la recuperación semántica completa.", "Only this source triggers the full semantic recovery."]),
      N("i3", "step", "ext", "CONDITIONAL", "recovery", ["Recuperación semántica", "Semantic recovery"], ["Se descubre y materializa el lenguaje del proyecto externo.", "The language of the external project is discovered and materialized."], { out: ["Metamodelo y reglas recuperados", "Recovered metamodel and rules"] }),
      N("i4", "step", "auto", "MANDATORY", "project", ["Copiar archivos al proyecto", "Copy files into the project"], ["Se restauran los archivos; en esta vía no se interpreta su significado.", "Files are restored; on this route their meaning is not interpreted."], { out: ["Contenido en el árbol", "Content in the tree"] }),
      N("x1", "step", "user", "MANDATORY", "project", ["Seleccionar en el árbol", "Select in the tree"], ["Eliges qué elementos exportar.", "You choose which items to export."], { e: "export" }),
      N("x2", "decision", "user", "MANDATORY", "project", ["¿ZIP o repositorio?", "ZIP or repository?"], ["La descarga local no necesita conexión; el repositorio sí.", "A local download needs no connection; the repository does."]),
      N("x3", "step", "auto", "OPTIONAL", "project", ["Generar y descargar el ZIP", "Build and download the ZIP"], ["Se descarga un archivo con la selección.", "A file with the selection is downloaded."], { out: ["Archivo ZIP", "ZIP file"] }),
      N("x4", "step", "ext", "CONDITIONAL", "external_repositories", ["Enviar al repositorio", "Send to the repository"], ["Se escribe en el destino remoto configurado.", "It is written to the configured remote destination."], { need: ["Conexión configurada", "Configured connection"] }),
      N("r", "artifact", null, null, "project", ["Contenido movido", "Content moved"], ["El proyecto tiene el contenido nuevo, o hay un respaldo fuera.", "The project has the new content, or a backup exists outside."]),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "d1"), E("d1", "i1", "next", "Importar", "Import"), E("d1", "x1", "alt", "Exportar", "Export"), E("i1", "i2"), E("i2", "i3", "cond", "Sí", "Yes"), E("i2", "i4", "next", "No", "No"), E("i3", "r"), E("i4", "r"),
      E("x1", "x2"), E("x2", "x3", "alt", "ZIP", "ZIP"), E("x2", "x4", "alt", "Repositorio", "Repository"), E("x3", "r"), E("x4", "r"), E("r", "e")] });

  HM.proc({ id: "project_author_tree", scope: "authoring", cap: "project", elems: ["tree", "primary"],
    n: T("Navegar el árbol del proyecto", "Navigate the project tree"), d: T("Cómo abres, seleccionas y revisas elementos en el árbol.", "How you open, select and review items in the tree."),
    nodes: [
      N("s", "start", "user", null, "project", ["Abrir el árbol", "Open the tree"], ["El árbol es la vista principal del proyecto.", "The tree is the project's main view."]),
      N("a", "step", "user", "MANDATORY", "project", ["Desplegar grupos", "Expand groups"], ["Una fila por elemento con icono según el tipo de artefacto.", "One row per item with an icon by artifact kind."], { e: "tree", out: ["Elementos visibles", "Visible items"] }),
      N("b", "decision", "user", "MANDATORY", "project", ["¿Abrir o seleccionar?", "Open or select?"], ["Doble clic abre; Ctrl/Cmd+clic suma a la selección.", "Double-click opens; Ctrl/Cmd+click adds to the selection."]),
      N("c", "step", "user", "OPTIONAL", "project", ["Abrir el elemento", "Open the item"], ["Se abre en su editor.", "It opens in its editor."]),
      N("d", "step", "user", "OPTIONAL", "project", ["Seleccionar varios", "Select several"], ["Prepara una exportación o acción sobre un conjunto.", "Prepares an export or an action on a set."]),
      N("f", "step", "auto", "CONDITIONAL", "project", ["Ver aviso de cambio externo", "See the external change warning"], ["Se marca el archivo que cambió fuera de HYDRA para que lo revises.", "The file changed outside HYDRA is marked so you can review it."], { s: ["archivo: al día → cambiado externamente", "file: up to date → changed externally"] }),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c", "alt", "Abrir", "Open"), E("b", "d", "alt", "Seleccionar", "Select"), E("a", "f", "cond"), E("c", "e"), E("d", "e"), E("f", "e")] });

  HM.proc({ id: "project_author_areas", scope: "authoring", cap: "project", elems: ["areas", "workmode"],
    n: T("Activar y desactivar áreas del proyecto", "Enable and disable project areas"), d: T("Los pasos del diálogo «Áreas del proyecto».", "The steps of the \"Project areas\" dialog."),
    nodes: [
      N("s", "start", "user", null, "project", ["Abrir el diálogo de áreas", "Open the areas dialog"], ["Se abre un diálogo modal con una cuadrícula de áreas.", "A modal dialog with a grid of areas opens."]),
      N("a", "step", "user", "OPTIONAL", "project", ["Elegir modo Guiado o Libre", "Choose Guided or Free mode"], ["Un interruptor decide cuánta guía recibes.", "A switch decides how much guidance you get."], { e: "workmode", out: ["Modo elegido", "Chosen mode"] }),
      N("b", "step", "user", "MANDATORY", "project", ["Marcar o desmarcar áreas", "Tick or untick areas"], ["Una casilla por área, agrupadas por tema.", "One checkbox per area, grouped by theme."], { e: "areas", out: ["Selección de áreas", "Area selection"] }),
      N("c", "step", "auto", "MANDATORY", "project", ["Aplicar dependencias", "Apply dependencies"], ["Activar una activa las que necesita; desactivarla apaga las que dependen de ella.", "Enabling one enables what it needs; disabling it turns off what depends on it."], { f: ["Dependencias automáticas en ambos sentidos.", "Automatic dependencies in both directions."] }),
      N("d", "decision", "user", "MANDATORY", "project", ["¿Confirmar?", "Confirm?"], ["Revisas lo que se activó o apagó antes de guardar.", "You review what was enabled or turned off before saving."]),
      N("r", "artifact", null, null, "project", ["Áreas guardadas y árbol recalculado", "Areas saved and tree recomputed"], ["El servidor recalcula el árbol; lo desactivado queda bloqueado de verdad.", "The server recomputes the tree; what is disabled is truly blocked."], { s: ["área: inactiva ↔ activa", "area: inactive ↔ active"] }),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "opt"), E("s", "b"), E("b", "c"), E("c", "d"), E("d", "r", "next", "Sí", "Yes"), E("d", "b", "loop", "No: ajustar", "No: adjust"), E("r", "e")] });

  HM.proc({ id: "project_author_import", scope: "authoring", cap: "project", elems: ["import"],
    n: T("Importar con el diálogo de importación", "Import with the import dialog"), d: T("Los pasos del diálogo y lo que ocurre según el origen.", "The dialog steps and what happens depending on the source."),
    nodes: [
      N("s", "start", "user", null, "project", ["Abrir el diálogo de importación", "Open the import dialog"], ["Desde Home o el menú del proyecto.", "From Home or the project menu."]),
      N("a", "decision", "user", "MANDATORY", "project", ["Elegir el origen", "Choose the source"], ["Cuatro opciones: carpeta local, ZIP, repositorio remoto o archivo de proyecto externo.", "Four options: local folder, ZIP, remote repository or external project file."], { e: "import" }),
      N("b", "step", "user", "MANDATORY", "project", ["Seleccionar el contenido", "Select the content"], ["Indicas la carpeta o el archivo, o el repositorio conectado.", "You point to the folder or file, or the connected repository."], { need: ["Algo que importar", "Something to import"] }),
      N("c", "step", "user", "CONDITIONAL", "external_repositories", ["Usar una conexión remota", "Use a remote connection"], ["Para el origen remoto necesitas una conexión ya configurada.", "For the remote source you need an already configured connection."]),
      N("d", "step", "auto", "MANDATORY", "project", ["Importar", "Import"], ["Se restaura el contenido; el archivo externo además activa la recuperación semántica.", "The content is restored; an external file additionally activates semantic recovery."], { f: ["Solo el archivo de proyecto externo materializa metamodelo y reglas.", "Only the external project file materializes metamodel and rules."] }),
      N("r", "artifact", null, null, "project", ["Contenido importado", "Imported content"], ["Aparece en el árbol del proyecto.", "It appears in the project tree."]),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "next", "Local, ZIP o externo", "Local, ZIP or external"), E("a", "c", "cond", "Remoto", "Remote"), E("c", "b"), E("b", "d"), E("d", "r"), E("r", "e")] });

  HM.proc({ id: "project_author_export", scope: "authoring", cap: "project", elems: ["export"],
    n: T("Exportar una selección", "Export a selection"), d: T("Seleccionar, elegir destino y descargar o enviar.", "Select, choose destination and download or upload."),
    nodes: [
      N("s", "start", "user", null, "project", ["Seleccionar elementos", "Select items"], ["Marcas en el árbol lo que quieres exportar.", "You mark in the tree what you want to export."]),
      N("a", "decision", "user", "MANDATORY", "project", ["Elegir destino", "Choose destination"], ["«Exportar selección como ZIP» o «Exportar a repositorio…».", "\"Export selection as ZIP\" or \"Export to repository…\"."], { e: "export" }),
      N("b", "step", "auto", "OPTIONAL", "project", ["Descargar el ZIP", "Download the ZIP"], ["Se baja un archivo local.", "A local file is downloaded."], { out: ["Archivo ZIP", "ZIP file"] }),
      N("c", "step", "user", "CONDITIONAL", "project", ["Completar el diálogo de destino", "Complete the destination dialog"], ["Indicas el destino remoto.", "You indicate the remote destination."], { need: ["Conexión configurada", "Configured connection"] }),
      N("d", "step", "ext", "CONDITIONAL", "external_repositories", ["Enviar", "Upload"], ["El proveedor externo recibe el contenido.", "The external provider receives the content."]),
      N("r", "artifact", null, null, "project", ["Exportación completada", "Export completed"], ["Tienes un archivo o contenido respaldado fuera.", "You have a file or content backed up outside."]),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "alt", "ZIP", "ZIP"), E("a", "c", "alt", "Repositorio", "Repository"), E("c", "d"), E("b", "r"), E("d", "r"), E("r", "e")] });

  HM.proc({ id: "project_author_home", scope: "authoring", cap: "project", elems: ["home", "primary"],
    n: T("Empezar desde Home", "Start from Home"), d: T("Las entradas del panel de inicio.", "The Home panel entry points."),
    nodes: [
      N("s", "start", "user", null, "project", ["Abrir Home", "Open Home"], ["Es la pantalla de bienvenida.", "It is the welcome screen."]),
      N("a", "decision", "user", "MANDATORY", "project", ["¿Qué quieres hacer?", "What do you want to do?"], ["Acciones principales, tarjeta de continuar o un reciente.", "Primary actions, the continue card or a recent one."], { e: "home" }),
      N("b", "step", "user", "OPTIONAL", "project", ["Acción principal", "Primary action"], ["Nuevo, abrir, importar o conectar.", "New, open, import or connect."]),
      N("c", "step", "user", "CONDITIONAL", "project", ["Continuar trabajando", "Continue working"], ["Entras por el artefacto principal del proyecto activo.", "You enter through the active project's main artifact."], { e: "primary" }),
      N("d", "step", "user", "OPTIONAL", "project", ["Abrir un proyecto reciente", "Open a recent project"], ["Hasta 6 proyectos.", "Up to 6 projects."]),
      N("e", "end", null, null, "project", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "alt", "Acción", "Action"), E("a", "c", "cond", "Hay proyecto activo", "There is an active project"), E("a", "d", "alt", "Reciente", "Recent"), E("b", "e"), E("c", "e"), E("d", "e")] });

  // =====================================================================================================
  // RECOVERY
  // =====================================================================================================
  HM.cap("recovery", {
    name: T("Recuperación externa", "External recovery"), group: "platform",
    user: {
      tagline: T("Trae a HYDRA lo que ya existe fuera", "Bring into HYDRA what already exists elsewhere"),
      what: T("Reconstruye un metamodelo, las capas de editor y las reglas de validación a partir de un proyecto o sitio de actualización externo, por ejemplo un proyecto Eclipse/EMF existente.",
              "Rebuilds a metamodel, editor layers and validation rules from an external project or update site, for example an existing Eclipse/EMF project."),
      why: T("Evita reescribir a mano lo que ya tienes, y lo hace en dos fases y de forma reversible: primero ves el resultado y solo después se guarda.",
             "Avoids rewriting by hand what you already have, in two phases and reversibly: you see the result first and it is only saved afterwards."),
      when: T("Cuando quieres incorporar un lenguaje que ya existe en otra herramienta.", "When you want to bring in a language that already exists in another tool."),
      canDefine: [T("El origen del proyecto externo", "The external project source"), T("Qué capas recuperar (paleta, representaciones, mapeos)", "Which layers to recover (palette, representations, mappings)"), T("Modo estricto, sobrescribir o guardar paquete", "Strict mode, overwrite or save package"),
        T("Qué candidato de metamodelo usar", "Which metamodel candidate to use"), T("Qué evidencia conservar ante un conflicto", "Which evidence to keep on a conflict"), T("Qué exigir en la puerta de publicación verificada", "What to require in the verified publication gate")],
      needs: [T("Un proyecto externo accesible (archivo, carpeta, repositorio o sitio de actualización remoto)", "An accessible external project (file, folder, repository or remote update site)"), T("Un proyecto HYDRA donde incorporar el resultado", "A HYDRA project to bring the result into")],
      produces: [T("Un metamodelo recuperado", "A recovered metamodel"), T("Capas de editor recuperadas", "Recovered editor layers"), T("Reglas de validación recuperadas, algunas marcadas para revisión", "Recovered validation rules, some flagged for review"), T("Una atestación descargable de calidad", "A downloadable quality attestation")],
      mandatory: [T("Origen del proyecto externo", "External project source"), T("Vista previa antes de guardar", "Preview before saving")],
      optional: [T("Reconciliar conflictos", "Reconcile conflicts"), T("Puerta de publicación verificada", "Verified publication gate"), T("Deshacer la materialización", "Undo the materialization")],
      conditional: [T("Deshacer solo aparece si el resultado lo permite", "Undo only appears if the result allows it")],
      next: [T("Revisar el metamodelo recuperado", "Review the recovered metamodel"), T("Revisar las reglas marcadas para revisión", "Review the rules flagged for review"), T("Crear un perfil de integración", "Create an integration profile")],
      interacts: [{ cap: "metamodel", t: T("Produce un metamodelo real, igual que uno hecho a mano", "Produces a real metamodel, same as a hand-made one") }, { cap: "validation", t: T("Recupera reglas reales; las no traducibles quedan para revisión", "Recovers real rules; non-translatable ones are left for review") },
        { cap: "integration", t: T("El proyecto recuperado puede alimentar un perfil de integración", "The recovered project can feed an integration profile") }, { cap: "project", t: T("Se puede iniciar también desde la importación del proyecto", "Can also be started from project import") }]
    },
    func: {
      lifecycle: [T("origen → plan → análisis → (reconciliación) → vista previa → guardado → (deshacer)", "source → plan → analysis → (reconciliation) → preview → save → (undo)"),
        T("la vista previa nunca se guarda; el guardado solo ocurre si el usuario confirma y la vista previa es aplicable", "the preview is never saved; saving only happens if the user confirms and the preview is applicable"),
        T("deshacer limpia solo lo creado en esa ejecución, identificado por sus versiones", "undo cleans only what that run created, identified by their versions")],
      semantics: [
        T("Se elige el método de mayor confianza disponible entre 6, de más a menos fiable: metamodelo directo, GenModel, registro de plugins, bytecode EMF, inferencia desde código fuente e inferencia desde código.", "The highest-confidence method available out of 6 is chosen, most to least reliable: direct metamodel, GenModel, plugin registry, EMF bytecode, source inference and code inference."),
        T("La pantalla muestra «Nivel n · método» con una insignia de confianza: EXACTA, RECUPERADA, DERIVADA o INFERIDA.", "The screen shows \"Level n · method\" with a confidence badge: EXACT, RECOVERED, DERIVED or INFERRED."),
        T("La revisión se orienta por el nivel de confianza, no por una bandera aparte de «requiere revisión».", "Review is guided by the confidence level, not by a separate \"requires review\" flag."),
        T("Los elementos del editor marcados como no representables se excluyen de la recuperación.", "Editor elements marked as non-representable are excluded from recovery."),
        T("Las reglas de validación reales (EVL, OCL, Sirius, Xtext) se traducen; las que no se pueden traducir con seguridad quedan marcadas para revisión o desactivadas.", "Real validation rules (EVL, OCL, Sirius, Xtext) are translated; those that cannot be safely translated are flagged for review or disabled."),
        T("Hay una vía más ligera de importación representable que solo registra un paquete de cambio neutral, sin crear metamodelo ni modelo.", "A lighter representable-import route only records a neutral change package, without creating a metamodel or model."),
        T("La puerta de 5 comprobaciones vive en la pestaña de Gobernanza de esta capacidad, no en Publicación.", "The 5-check gate lives in this capability's Governance tab, not in Publication.")],
      states: [{ n: T("Vista previa", "Preview"), d: T("Calculada, nunca guardada", "Computed, never saved") }, { n: T("Guardado", "Saved"), d: T("Documentos reales creados", "Real documents created") }, { n: T("Deshecho", "Undone"), d: T("Limpieza de lo creado en esa ejecución", "Cleanup of what that run created") }],
      cross: [T("Metamodelo: recibe el metamodelo recuperado.", "Metamodel: receives the recovered metamodel."), T("Validación: recibe reglas recuperadas, algunas desactivadas hasta revisarse.", "Validation: receives recovered rules, some disabled until reviewed."), T("Integración: un proyecto recuperado puede alimentar un perfil nuevo.", "Integration: a recovered project can feed a new profile.")]
    },
    tech: {
      runtime: T("Coordinador de materialización semántica de proyecto externo (preview/persist/rollback) sobre los métodos de descubrimiento del paquete de importación.", "External-project semantic materialization coordinator (preview/persist/rollback) over the discovery methods of the importing package."),
      services: ["ExternalProjectSemanticMaterializationService", "UpdateSiteMetamodelRecoveryService", "RepresentableImportController"],
      apis: ["REST de importación/recuperación de proyecto externo"],
      schemas: ["UpdateSiteRecoveryMethod", "UpdateSiteConfidence", "EditorDefinitionModel (candidato)"],
      tests: [],
      evidence: [EV(PMAT, 1, "Materialización preview/persist/rollback"), EV(PREC, 1, "Métodos de descubrimiento"), EV(PUPD, 112, "SOURCE_INFERENCE, nivel 5, confianza INFERRED")],
      notes: [T("Los datos heredados hablan de 6 métodos de descubrimiento y la interfaz muestra 5 niveles; en el código leído, la inferencia desde fuente ocupa el nivel 5. No se verificó cómo se ubica el sexto método.", "Legacy data speaks of 6 discovery methods and the UI shows 5 tiers; in the code read, source inference occupies tier 5. How the sixth method is placed was not verified."),
        T("El código marca la inferencia desde código fuente como evidencia de reserva: anotaciones, semántica generada y comportamiento en ejecución requieren revisión; el código importado nunca se ejecuta.", "The code marks source inference as fallback evidence: annotations, generated semantics and runtime behaviour require review; imported code is never executed."),
        T("La importación desde un repositorio remoto no alimenta esta capacidad: exige una subida de archivo local independiente.", "Import from a remote repository does not feed this capability: it requires an independent local file upload."),
        T("Los 13 paneles del espacio avanzado se toman de datos heredados; no se releyeron los componentes del centro de importación.", "The 13 advanced workspace tabs come from legacy data; the import center components were not re-read.")]
    },
    elements: [
      { id: "source", n: T("Origen del proyecto externo", "External project source"), kind: "variant-set", req: "ALTERNATIVE",
        mean: T("Desde dónde se trae el proyecto externo.", "Where the external project is brought from."), purpose: T("Dar a HYDRA acceso al material que se va a recuperar.", "Give HYDRA access to the material to be recovered."), when: T("Primer paso del asistente.", "First step of the wizard."),
        card: "1 recuperación ↔ 1 origen",
        variants: [V("Archivo o carpeta local", "Local file or folder", "Subida desde tu equipo", "Upload from your computer"), V("Repositorio Git, GitHub o GitLab", "Git, GitHub or GitLab repository", "Se lee el contenido del repositorio", "The repository content is read"), V("Sitio de actualización remoto (p2)", "Remote update site (p2)", "Un sitio de actualización Eclipse", "An Eclipse update site")],
        io: { i: T("Ubicación del proyecto", "Project location"), o: T("Contenido accesible para el análisis", "Content accessible for analysis") }, effect: T("No cambia nada en tu proyecto todavía.", "Changes nothing in your project yet."),
        func: { rel: [T("Alimenta el análisis", "Feeds the analysis")], lifecycle: T("Se elige una vez por recuperación.", "Chosen once per recovery."), cond: T("Un solo origen por recuperación.", "One source per recovery.") },
        tech: { type: "Entrada del asistente de 5 pasos", schema: "—", evidence: [EV(PREC, 1, "Paquete importing")] } },
      { id: "plan", n: T("Plan de recuperación", "Recovery plan"), kind: "concept", req: "MANDATORY",
        mean: T("Qué se quiere recuperar y con qué rigor.", "What to recover and how strictly."), purpose: T("Acotar el análisis y decidir cómo se guardará.", "Scope the analysis and decide how it will be saved."), when: T("Segundo paso del asistente.", "Second step of the wizard."),
        card: "1 recuperación ↔ 1 plan",
        props: [PR("Capas neutrales", "Neutral layers", "OPTIONAL", "Paleta, representaciones y mapeos semánticos a recuperar", "Palette, representations and semantic mappings to recover"), PR("Modo estricto", "Strict mode", "OPTIONAL", "Más exigencia en la recuperación", "More demanding recovery"),
          PR("Sobrescribir", "Overwrite", "OPTIONAL", "Reemplazar lo existente", "Replace what exists"), PR("Guardar paquete", "Save package", "OPTIONAL", "Persistir el paquete de cambio", "Persist the change package")],
        io: { i: T("Opciones del usuario", "User options"), o: T("Alcance del análisis", "Analysis scope") }, effect: T("Condiciona qué candidatos se generan y cómo se guardan.", "Conditions which candidates are generated and how they are saved."),
        func: { rel: [T("Parte del origen; condiciona el análisis", "Starts from the source; conditions the analysis")], lifecycle: T("Se fija antes de analizar.", "Fixed before analyzing."), cond: T("Las opciones concretas se leen de datos heredados.", "Concrete options are read from legacy data.") },
        tech: { type: "Paso 2 del asistente", schema: "—", evidence: [EV(PREC, 1, "Paquete importing")] } },
      { id: "method", n: T("Método de descubrimiento", "Discovery method"), kind: "variant-set", req: "ALTERNATIVE",
        mean: T("La vía por la que se reconstruye el metamodelo, ordenada de más a menos fiable.", "The route by which the metamodel is rebuilt, ordered from most to least reliable."), purpose: T("Usar siempre la evidencia más fiable disponible.", "Always use the most reliable evidence available."), when: T("Durante el análisis.", "During analysis."),
        card: "1 recuperación ↔ 1 método elegido (el de mayor confianza)",
        variants: [V("Metamodelo directo", "Direct metamodel", "El metamodelo original está disponible", "The original metamodel is available"), V("GenModel", "GenModel", "Se parte del modelo de generación", "Starting from the generation model"), V("Registro de plugins", "Plugin registry", "Se lee el registro de plugins", "The plugin registry is read"),
          V("Bytecode EMF", "EMF bytecode", "Se reconstruye desde clases compiladas", "Rebuilt from compiled classes"), V("Inferencia desde código fuente", "Source inference", "Declaraciones de Java, Kotlin, TypeScript o Go sin ejecutar nada", "Java, Kotlin, TypeScript or Go declarations without executing anything"), V("Inferencia desde código", "Code inference", "La vía menos fiable", "The least reliable route")],
        io: { i: T("Contenido del proyecto externo", "External project content"), o: T("Metamodelo reconstruido y nivel de confianza", "Rebuilt metamodel and confidence level") }, effect: T("Si el método mejor no está disponible, se cae al siguiente.", "If the best method is not available, the next one is used."),
        func: { rel: [T("El método usado queda registrado como procedencia", "The method used is recorded as provenance")], lifecycle: T("Escalada de arriba abajo.", "Escalation from top to bottom."), cond: T("Cae al siguiente si el anterior no está disponible.", "Falls to the next if the previous one is unavailable.") },
        tech: { type: "UpdateSiteRecoveryMethod", schema: "enum + UpdateSiteConfidence", evidence: [EV(PUPD, 112, "SOURCE_INFERENCE (nivel 5, INFERRED)")] } },
      { id: "candidate", n: T("Candidato de metamodelo", "Metamodel candidate"), kind: "derived", req: "DERIVED",
        mean: T("Un metamodelo posible reconstruido por un método, con su insignia de confianza y su justificación.", "A possible metamodel rebuilt by a method, with its confidence badge and rationale."), purpose: T("Dejarte elegir con información.", "Let you choose with information."), when: T("Tras analizar.", "After analysis."),
        card: "1 análisis ↔ 0..N candidatos",
        variants: [V("Exacta", "Exact", "Ecore original", "Original Ecore", "DERIVED"), V("Recuperada", "Recovered", "Reconstruida con alta fiabilidad", "Rebuilt with high reliability", "DERIVED"), V("Derivada", "Derived", "Obtenida de otra evidencia", "Obtained from other evidence", "DERIVED"), V("Inferida", "Inferred", "Deducida; requiere revisión", "Deduced; requires review", "DERIVED")],
        io: { i: T("Análisis del proyecto", "Project analysis"), o: T("Lista de candidatos para elegir", "List of candidates to choose from") }, effect: T("Elegir uno lo lleva a la vista previa.", "Choosing one takes it to preview."),
        func: { rel: [T("Se genera por cada método disponible", "Generated per available method")], lifecycle: T("generado → elegido", "generated → chosen"), cond: T("Mostrados como lista de opciones por nivel.", "Shown as a list of options by level.") },
        tech: { type: "UpdateSiteMetamodelCandidate", schema: "confianza + justificación", evidence: [EV(PUPD, 112, "candidate(...)")] } },
      { id: "evidence", n: T("Evidencia por elemento", "Per-element evidence"), kind: "derived", req: "MANDATORY",
        mean: T("De dónde sale cada elemento recuperado y con qué fiabilidad.", "Where each recovered element comes from and how reliably."), purpose: T("Poder revisar y justificar lo recuperado.", "Be able to review and justify what was recovered."), when: T("Al revisar.", "When reviewing."),
        card: "1 elemento ↔ 1..N evidencias",
        props: [PR("Nivel de confianza", "Confidence level", "MANDATORY", "Filtro principal", "Main filter"), PR("Origen del paquete", "Package origin", "OPTIONAL", "De qué paquete procede", "Which package it comes from"), PR("Archivo fuente", "Source file", "OPTIONAL", "Dónde estaba", "Where it was"),
          PR("Elemento original", "Original element", "OPTIONAL", "Elemento del que parte", "Element it derives from"), PR("Método de recuperación", "Recovery method", "OPTIONAL", "Cómo se obtuvo", "How it was obtained"), PR("Advertencias", "Warnings", "OPTIONAL", "Avisos del análisis", "Analysis warnings")],
        io: { i: T("Elementos recuperados", "Recovered elements"), o: T("Panel filtrable", "Filterable panel") }, effect: T("No cambia el resultado: sirve para decidir.", "Does not change the result: it serves to decide."),
        func: { rel: [T("Cada elemento recuperado enlaza con su procedencia", "Each recovered element links to its provenance")], lifecycle: T("Se genera al analizar.", "Generated on analysis."), cond: T("Filtrable por seis criterios.", "Filterable by six criteria.") },
        tech: { type: "Panel de evidencia filtrable", schema: "—", evidence: [EV(PREC, 1, "Paquete importing")] } },
      { id: "conflict", n: T("Conflicto entre candidatos", "Conflict between candidates"), kind: "concept", req: "CONDITIONAL",
        mean: T("Dos candidatos que dicen cosas distintas sobre el mismo elemento.", "Two candidates that say different things about the same element."), purpose: T("Decidir qué evidencia conservar.", "Decide which evidence to keep."), when: T("Solo si hay conflictos.", "Only when there are conflicts."),
        card: "1 conflicto ↔ 1 decisión",
        variants: [V("Bloqueante", "Blocking", "Requiere tu decisión", "Requires your decision", "MANDATORY"), V("Resuelto automáticamente", "Auto-resolved", "Resuelto con seguridad sin intervención", "Safely resolved without intervention", "DERIVED")],
        props: [PR("Evidencia a conservar", "Evidence to keep", "MANDATORY", "Desplegable por conflicto", "Dropdown per conflict")],
        io: { i: T("Candidatos en conflicto", "Conflicting candidates"), o: T("Vista previa reconciliada", "Reconciled preview") }, effect: T("El botón «Construir vista previa reconciliada» aplica tus decisiones.", "The \"Build reconciled preview\" button applies your decisions."),
        func: { rel: [T("Se origina entre candidatos", "Arises between candidates")], lifecycle: T("abierto → decidido", "open → decided"), cond: T("Los bloqueantes impiden avanzar sin decisión.", "Blocking ones prevent advancing without a decision.") },
        tech: { type: "Reconciliación del espacio avanzado", schema: "—", evidence: [EV(PREC, 1, "Paquete importing")] } },
      { id: "materialize", n: T("Materialización", "Materialization"), kind: "concept", req: "MANDATORY",
        mean: T("Convertir lo recuperado en documentos reales de tu proyecto, en dos fases.", "Turn what was recovered into real documents in your project, in two phases."), purpose: T("Que nada se guarde hasta que lo confirmes, y poder deshacerlo.", "Ensure nothing is saved until you confirm, and allow undoing it."), when: T("Tras elegir y reconciliar.", "After choosing and reconciling."),
        card: "1 ejecución ↔ 1 vista previa ↔ 0..1 guardado ↔ 0..1 deshacer",
        variants: [V("Previsualizar materialización", "Preview materialization", "Nunca guarda", "Never saves", "MANDATORY"), V("Importar y materializar capas seleccionadas", "Import and materialize selected layers", "Guarda; solo si la vista previa es aplicable", "Saves; only if the preview is applicable", "CONDITIONAL"), V("Deshacer materialización", "Undo materialization", "Solo visible si el resultado lo permite", "Only visible if the result allows it", "CONDITIONAL")],
        io: { i: T("Candidato elegido y reglas", "Chosen candidate and rules"), o: T("Metamodelo y validaciones guardados", "Saved metamodel and validations") }, effect: T("Crea documentos reales con control de versión; deshacer elimina exactamente lo creado.", "Creates real documents with version control; undo removes exactly what was created."),
        func: { rel: [T("Produce metamodelo y reglas para otras capacidades", "Produces metamodel and rules for other capabilities")], lifecycle: T("vista previa → guardado → (deshacer)", "preview → save → (undo)"), cond: T("Guardado solo si confirmas y la vista previa es aplicable.", "Saving only if you confirm and the preview is applicable.") },
        tech: { type: "ExternalProjectSemanticMaterializationService", schema: "documentos con ETag", evidence: [EV(PMAT, 1, "Materialización")] } },
      { id: "rules", n: T("Reglas de validación recuperadas", "Recovered validation rules"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Reglas reales del proyecto externo traducidas a HYDRA.", "Real rules from the external project translated to HYDRA."), purpose: T("No perder las comprobaciones que ya tenías.", "Do not lose the checks you already had."), when: T("Si el proyecto externo las define.", "If the external project defines them."),
        card: "1 proyecto ↔ 0..N reglas",
        variants: [V("Traducida y activa", "Translated and active", "Se traduce con seguridad", "Translated safely", "ALTERNATIVE"), V("Para revisar", "For review", "Requiere revisión manual antes de activarse", "Requires manual review before activation", "ALTERNATIVE"), V("Desactivada", "Disabled", "No traducible con seguridad", "Not safely translatable", "ALTERNATIVE")],
        io: { i: T("Reglas EVL, OCL, Sirius o Xtext", "EVL, OCL, Sirius or Xtext rules"), o: T("Reglas de validación en HYDRA", "Validation rules in HYDRA") }, effect: T("Las traducidas quedan activas; las demás esperan tu revisión.", "Translated ones become active; the others wait for your review."),
        func: { rel: [T("Se entregan a Validación", "Delivered to Validation")], lifecycle: T("recuperada → revisada → activa", "recovered → reviewed → active"), cond: T("Un ejemplo heredado: 12 reglas, 9 traducidas y 3 para revisar.", "A legacy example: 12 rules, 9 translated and 3 for review.") },
        tech: { type: "Reglas recuperadas (EVL/OCL/Sirius/Xtext)", schema: "estado review-required/disabled", evidence: [EV(PMAT, 1, "Reglas recuperadas")] } },
      { id: "gate", n: T("Puerta de publicación verificada", "Verified publication gate"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Cinco comprobaciones de calidad con una atestación descargable.", "Five quality checks with a downloadable attestation."), purpose: T("Demostrar que la recuperación es fiable antes de publicar.", "Prove the recovery is reliable before publishing."), when: T("Pestaña de Gobernanza del espacio avanzado.", "Governance tab of the advanced workspace."),
        card: "1 puerta ↔ 5 comprobaciones",
        variants: [V("Fusión semántica profunda", "Deep semantic merge", "Primer bloque", "First block", "OPTIONAL"), V("Espejo p2 sin conexión", "Offline p2 mirror", "Se puede exigir con una casilla", "Can be required with a checkbox", "OPTIONAL"), V("Banco de pruebas del adaptador", "Adapter test bench", "Se puede exigir con una casilla", "Can be required with a checkbox", "OPTIONAL"),
          V("Regresión de rendimiento", "Performance regression", "Se puede exigir con una casilla", "Can be required with a checkbox", "OPTIONAL"), V("Compatibilidad y publicación", "Compatibility and publication", "Resultado final de la puerta", "Final result of the gate", "OPTIONAL")],
        io: { i: T("Casillas de exigencia", "Requirement checkboxes"), o: T("Resultado por comprobación y atestación HTML con SHA-256", "Result per check and HTML attestation with SHA-256") }, effect: T("Genera una atestación descargable; no publica por sí sola.", "Generates a downloadable attestation; does not publish by itself."),
        func: { rel: [T("Distinta de la validación genérica de la publicación", "Distinct from the generic validation in publication")], lifecycle: T("cinco bloques secuenciales", "five sequential blocks"), cond: T("Espejo, banco y rendimiento son exigibles.", "Mirror, bench and performance can be required.") },
        tech: { type: "Pestaña de Gobernanza", schema: "atestación HTML + SHA-256", evidence: [EV(PREC, 1, "Paquete importing")] } },
      { id: "light", n: T("Importación representable ligera", "Light representable import"), kind: "concept", req: "ALTERNATIVE",
        mean: T("Una vía más simple que solo anota un paquete de cambio neutral.", "A simpler route that only records a neutral change package."), purpose: T("Registrar un recurso para revisarlo después.", "Record a resource to review it later."), when: T("Con un recurso representable genérico, no un proyecto completo.", "With a generic representable resource, not a whole project."),
        card: "1 importación ↔ 1 paquete de cambio",
        io: { i: T("Recurso representable", "Representable resource"), o: T("Registro de cambio, no un metamodelo", "Change record, not a metamodel") }, effect: T("No crea metamodelo ni modelo.", "Does not create a metamodel or model."),
        func: { rel: [T("Distinta de la materialización completa", "Distinct from full materialization")], lifecycle: T("Requiere un paso posterior para volverse real.", "Requires a later step to become real."), cond: T("Solo importaciones representables.", "Representable imports only.") },
        tech: { type: "RepresentableImportController", schema: "JSON de cambio neutral", evidence: [EV(PREC, 1, "RepresentableImportController")] } }
    ],
    map: {
      nodes: [
        { id: "source", elem: "source", n: T("Origen externo", "External source"), k: "external", req: "ALTERNATIVE", d: T("Archivo, repositorio o p2", "File, repository or p2") },
        { id: "plan", elem: "plan", n: T("Plan", "Plan"), k: "concept", req: "MANDATORY", d: T("Qué recuperar", "What to recover") },
        { id: "method", elem: "method", n: T("Método de descubrimiento", "Discovery method"), k: "concept", req: "ALTERNATIVE", d: T("6, por confianza", "6, by confidence") },
        { id: "direct", n: T("Metamodelo directo", "Direct metamodel"), k: "variant", req: "ALTERNATIVE", d: T("Más fiable", "Most reliable") },
        { id: "infer", n: T("Inferencia desde código", "Code inference"), k: "variant", req: "ALTERNATIVE", d: T("Menos fiable", "Least reliable") },
        { id: "cand", elem: "candidate", n: T("Candidato", "Candidate"), k: "derived", req: "DERIVED", d: T("Con insignia de confianza", "With confidence badge") },
        { id: "evid", elem: "evidence", n: T("Evidencia", "Evidence"), k: "derived", req: "MANDATORY", d: T("Procedencia", "Provenance") },
        { id: "conf", elem: "conflict", n: T("Conflicto", "Conflict"), k: "concept", req: "CONDITIONAL", d: T("Decisión explícita", "Explicit decision") },
        { id: "mat", elem: "materialize", n: T("Materialización", "Materialization"), k: "concept", req: "MANDATORY", d: T("Vista previa, guardado, deshacer", "Preview, save, undo") },
        { id: "rules", elem: "rules", n: T("Reglas recuperadas", "Recovered rules"), k: "concept", req: "OPTIONAL", d: T("Traducidas o para revisar", "Translated or for review") },
        { id: "gate", elem: "gate", n: T("Puerta verificada", "Verified gate"), k: "concept", req: "OPTIONAL", d: T("5 comprobaciones", "5 checks") },
        { id: "mm", n: T("Metamodelo en HYDRA", "Metamodel in HYDRA"), k: "derived", req: "DERIVED", d: T("Resultado", "Result") }
      ],
      edges: [
        { a: "source", b: "plan", rel: "ref", req: "MANDATORY" }, { a: "plan", b: "method", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" },
        { a: "direct", b: "method", rel: "isa", req: "ALTERNATIVE" }, { a: "infer", b: "method", rel: "isa", req: "ALTERNATIVE" },
        { a: "method", b: "cand", rel: "gen", card1: "1", card2: "0..N", req: "MANDATORY" }, { a: "cand", b: "evid", rel: "contains", card1: "1", card2: "1..N", req: "MANDATORY" },
        { a: "cand", b: "conf", rel: "ref", req: "CONDITIONAL" }, { a: "cand", b: "mat", rel: "ref", req: "MANDATORY" },
        { a: "mat", b: "mm", rel: "gen", req: "MANDATORY" }, { a: "mat", b: "rules", rel: "gen", req: "OPTIONAL" }, { a: "mat", b: "gate", rel: "ref", req: "OPTIONAL" }
      ]
    },
    define: [
      { id: "wizard", n: T("Asistente guiado de 5 pasos", "5-step guided wizard"), kind: "wizard", req: "ALTERNATIVE", elems: ["source", "plan", "candidate", "materialize"],
        does: T("Eliges el origen (archivo o carpeta local, repositorio Git/GitHub/GitLab o sitio p2 remoto), defines el plan (qué capas recuperar y si es estricto, sobrescribir o guardar paquete), pulsas analizar, revisas los candidatos y confirmas la importación.", "You choose the source (local file or folder, Git/GitHub/GitLab repository or remote p2 site), define the plan (which layers to recover and strict, overwrite or save package), press analyze, review the candidates and confirm the import."),
        func: T("El análisis genera candidatos por nivel; nada se guarda hasta confirmar.", "Analysis generates candidates by level; nothing is saved until you confirm."), tech: T("Asistente de recuperación del frontend (origen → plan → analizar → revisar → importar).", "Frontend recovery wizard (source → plan → analyze → review → import)."), proc: "recovery_author_wizard" },
      { id: "workspace", n: T("Espacio de trabajo avanzado", "Advanced workspace"), kind: "form", req: "OPTIONAL", elems: ["candidate", "evidence", "conflict", "materialize", "rules"],
        does: T("Abres 13 pestañas agrupadas en Entender, Revisar y Construir. Eliges un candidato en una lista por nivel, filtras la evidencia, resuelves conflictos con un desplegable y usas tres botones: previsualizar, importar y materializar, deshacer.", "You open 13 tabs grouped as Understand, Review and Build. You pick a candidate in a list by level, filter evidence, resolve conflicts with a dropdown and use three buttons: preview, import and materialize, undo."),
        func: T("«Deshacer materialización» solo aparece si el resultado lo permite.", "\"Undo materialization\" only appears if the result allows it."), tech: T("Espacio avanzado con 13 pestañas (dato heredado).", "Advanced workspace with 13 tabs (legacy data)."), proc: "recovery_author_workspace" },
      { id: "governance", n: T("Pestaña de Gobernanza", "Governance tab"), kind: "form", req: "OPTIONAL", elems: ["gate"],
        does: T("Recorres cinco bloques seguidos, marcas casillas para exigir el espejo, el banco de pruebas o el rendimiento, ves el resultado de cada comprobación y descargas la atestación en HTML.", "You go through five sequential blocks, tick checkboxes to require the mirror, bench or performance, see each check's result and download the HTML attestation."),
        func: T("La puerta vive aquí, no en el Centro de Publicación.", "The gate lives here, not in the Publication Center."), tech: T("Pestaña Gobernanza del espacio avanzado.", "Governance tab of the advanced workspace."), proc: "recovery_author_governance" }
    ],
    rel: [
      { to: "metamodel", type: "producer", dir: "out", u: T("Entrega un metamodelo real, usable como uno hecho a mano.", "Delivers a real metamodel, usable like a hand-made one."), f: T("Solo tras guardar la materialización.", "Only after saving the materialization."), t: T("Documentos canónicos creados con ETag.", "Canonical documents created with ETag.") },
      { to: "validation", type: "producer", dir: "out", u: T("Entrega reglas recuperadas; las no seguras esperan revisión.", "Delivers recovered rules; unsafe ones wait for review."), f: T("Estados traducida, para revisar o desactivada.", "Translated, for-review or disabled states."), t: T("Reglas marcadas review-required/disabled.", "Rules flagged review-required/disabled.") },
      { to: "integration", type: "optional", dir: "out", u: T("Un proyecto recuperado puede crear un perfil de integración.", "A recovered project can create an integration profile."), f: T("Hacia la tecnología de origen.", "Toward the original technology."), t: T("Alimenta IntegrationProfileModel.", "Feeds IntegrationProfileModel.") },
      { to: "project", type: "optional", dir: "in", u: T("También se puede iniciar importando un archivo de proyecto externo.", "It can also be started by importing an external project file."), f: T("Vía alternativa de entrada.", "Alternative entry route."), t: T("El diálogo de importación del proyecto enlaza con la materialización.", "The project import dialog links to materialization.") },
      { to: "external_repositories", type: "alternative", dir: "in", u: T("Importar de un repositorio remoto no lanza esta recuperación.", "Importing from a remote repository does not launch this recovery."), f: T("Caminos separados.", "Separate paths."), t: T("Exige subida de archivo local independiente.", "Requires an independent local file upload.") }
    ]
  });

  HM.proc({ id: "recovery_lifecycle", scope: "cap", cap: "recovery", caps: ["metamodel", "validation", "integration"], elems: ["source", "method", "candidate", "materialize", "rules"],
    n: T("Recuperar un proyecto externo de principio a fin", "Recover an external project end to end"), d: T("Del inventario no destructivo a un metamodelo y reglas disponibles, con posibilidad de deshacer.", "From the non-destructive inventory to an available metamodel and rules, with the option to undo."),
    nodes: [
      N("s", "start", "user", null, "recovery", ["Apuntar HYDRA al proyecto externo", "Point HYDRA at the external project"], ["Tienes un proyecto o sitio de actualización accesible.", "You have an accessible project or update site."]),
      N("a1", "step", "user", "MANDATORY", "recovery", ["Elegir origen y plan", "Choose source and plan"], ["Decides de dónde traerlo y qué capas recuperar.", "You decide where to bring it from and which layers to recover."], { e: "plan", out: ["Plan de recuperación", "Recovery plan"] }),
      N("a2", "step", "auto", "MANDATORY", "recovery", ["Descubrir metamodelo por confianza", "Discover metamodel by confidence"], ["Se usa el método de mayor confianza disponible; si no hay, el siguiente.", "The highest-confidence method available is used; if none, the next."], { e: "method", out: ["Candidatos con insignia de confianza", "Candidates with confidence badge"], x: { rt: "UpdateSiteMetamodelRecoveryService", test: [], ev: [EV(PUPD, 112, "candidatos por método")] } }),
      N("a3", "step", "auto", "OPTIONAL", "recovery", ["Recuperar capas y editor", "Recover layers and editor"], ["Se recuperan paleta, representaciones y mapeos; los no representables se excluyen.", "Palette, representations and mappings are recovered; non-representable ones are excluded."]),
      N("a4", "step", "auto", "OPTIONAL", "recovery", ["Recuperar reglas de validación", "Recover validation rules"], ["Se traducen las reglas reales; las no seguras quedan para revisar o desactivadas.", "Real rules are translated; unsafe ones are left for review or disabled."], { e: "rules" }),
      N("d1", "decision", "auto", "MANDATORY", "recovery", ["¿Hay conflictos bloqueantes?", "Blocking conflicts?"], ["Los conflictos entre candidatos requieren tu decisión.", "Conflicts between candidates require your decision."]),
      N("a5", "step", "user", "CONDITIONAL", "recovery", ["Reconciliar conflictos", "Reconcile conflicts"], ["Eliges qué evidencia conservar y construyes la vista previa reconciliada.", "You choose which evidence to keep and build the reconciled preview."], { e: "conflict" }),
      N("a6", "step", "auto", "MANDATORY", "recovery", ["Vista previa de materialización", "Materialization preview"], ["Se calcula el resultado sin guardar nada.", "The result is computed without saving anything."], { e: "materialize", s: ["materialización: sin hacer → vista previa", "materialization: not done → preview"], x: { rt: "ExternalProjectSemanticMaterializationService", test: [], ev: [EV(PMAT, 1, "preview")] } }),
      N("d2", "decision", "user", "MANDATORY", "recovery", ["¿Confirmar y es aplicable?", "Confirm and is it applicable?"], ["Solo se guarda si confirmas y la vista previa es aplicable.", "It is only saved if you confirm and the preview is applicable."]),
      N("a7", "step", "auto", "MANDATORY", "recovery", ["Guardar documentos", "Save documents"], ["Se crean documentos reales con control de versión.", "Real documents with version control are created."], { s: ["materialización: vista previa → guardada", "materialization: preview → saved"] }),
      N("m", "artifact", "auto", "MANDATORY", "metamodel", ["Metamodelo disponible", "Metamodel available"], ["Queda disponible para HYDRA como uno hecho a mano.", "Becomes available to HYDRA like a hand-made one."]),
      N("v", "artifact", "auto", "OPTIONAL", "validation", ["Reglas disponibles", "Rules available"], ["Las traducidas están activas; las demás esperan revisión.", "Translated ones are active; others wait for review."]),
      N("e", "end", null, null, "recovery", ["Fin", "End"])
    ],
    edges: [E("s", "a1"), E("a1", "a2"), E("a2", "a3", "opt"), E("a2", "a4", "opt"), E("a2", "d1"), E("a3", "d1"), E("a4", "d1"), E("d1", "a5", "cond", "Sí", "Yes"), E("d1", "a6", "next", "No", "No"), E("a5", "a6"), E("a6", "d2"),
      E("d2", "a7", "next", "Sí", "Yes"), E("d2", "e", "alt", "No: queda en vista previa", "No: stays at preview"), E("a7", "m"), E("a7", "v", "opt"), E("m", "e"), E("v", "e")] });

  HM.proc({ id: "recovery_persist_rollback", scope: "cap", cap: "recovery", elems: ["materialize"],
    n: T("Guardar y, si hace falta, deshacer", "Save and, if needed, undo"), d: T("El guardado en dos fases y la reversión dirigida.", "Two-phase saving and targeted reversal."),
    nodes: [
      N("s", "start", "auto", null, "recovery", ["Vista previa construida", "Preview built"], ["Hay un resultado listo para valorar.", "There is a result ready to assess."]),
      N("d1", "decision", "user", "MANDATORY", "recovery", ["¿Importar y materializar?", "Import and materialize?"], ["Es la decisión explícita de guardar.", "It is the explicit decision to save."], { e: "materialize" }),
      N("d2", "decision", "auto", "MANDATORY", "recovery", ["¿La vista previa es aplicable?", "Is the preview applicable?"], ["Si no lo es, no se guarda nada y termina aquí.", "If not, nothing is saved and it ends here."]),
      N("a1", "step", "auto", "MANDATORY", "recovery", ["Guardar metamodelo y reglas", "Save metamodel and rules"], ["Se crean documentos con control de versión.", "Documents with version control are created."], { s: ["materialización: vista previa → guardada", "materialization: preview → saved"], x: { rt: "ExternalProjectSemanticMaterializationService", test: [], ev: [EV(PMAT, 1, "persist")] } }),
      N("a2", "artifact", "auto", null, "recovery", ["Documentos guardados", "Saved documents"], ["Resultado real, identificado por sus versiones.", "Real result, identified by their versions."]),
      N("d3", "decision", "user", "OPTIONAL", "recovery", ["¿Deshacer?", "Undo?"], ["Solo aparece si el resultado indica que es posible.", "Only appears if the result says it is possible."]),
      N("a3", "step", "auto", "CONDITIONAL", "recovery", ["Limpieza dirigida", "Targeted cleanup"], ["Se elimina solo lo creado en esa ejecución, no una copia completa anterior.", "Only what that run created is removed, not a full earlier copy."], { s: ["materialización: guardada → deshecha", "materialization: saved → undone"] }),
      N("e", "end", null, null, "recovery", ["Fin", "End"])
    ],
    edges: [E("s", "d1"), E("d1", "d2", "next", "Sí", "Yes"), E("d1", "e", "alt", "No", "No"), E("d2", "a1", "next", "Sí", "Yes"), E("d2", "e", "alt", "No", "No"), E("a1", "a2"), E("a2", "d3"), E("d3", "a3", "cond", "Sí", "Yes"), E("d3", "e", "next", "No", "No"), E("a3", "e")] });

  HM.proc({ id: "recovery_author_wizard", scope: "authoring", cap: "recovery", elems: ["source", "plan", "candidate", "materialize"],
    n: T("Asistente de recuperación (5 pasos)", "Recovery wizard (5 steps)"), d: T("Origen, plan, analizar, revisar e importar.", "Source, plan, analyze, review and import."),
    nodes: [
      N("s", "start", "user", null, "recovery", ["Abrir el asistente", "Open the wizard"], ["Empiezas por indicar el proyecto externo.", "You start by pointing to the external project."]),
      N("a", "step", "user", "MANDATORY", "recovery", ["1. Origen", "1. Source"], ["Archivo o carpeta local, repositorio Git/GitHub/GitLab o p2 remoto.", "Local file or folder, Git/GitHub/GitLab repository or remote p2."], { e: "source" }),
      N("b", "step", "user", "MANDATORY", "recovery", ["2. Plan de recuperación", "2. Recovery plan"], ["Eliges qué capas neutrales recuperar.", "You choose which neutral layers to recover."], { e: "plan" }),
      N("c", "step", "user", "OPTIONAL", "recovery", ["Estricto, sobrescribir, guardar paquete", "Strict, overwrite, save package"], ["Opciones de rigor y de guardado.", "Strictness and saving options."], { e: "plan" }),
      N("d", "step", "auto", "MANDATORY", "recovery", ["3. Analizar", "3. Analyze"], ["Se generan candidatos por nivel de confianza.", "Candidates are generated by confidence level."], { e: "candidate" }),
      N("f", "step", "user", "MANDATORY", "recovery", ["4. Revisar", "4. Review"], ["Eliges el candidato y miras su evidencia.", "You pick the candidate and look at its evidence."]),
      N("g", "decision", "user", "MANDATORY", "recovery", ["¿Importar?", "Import?"], ["Confirmas antes de guardar.", "You confirm before saving."]),
      N("h", "artifact", null, null, "recovery", ["5. Importación hecha", "5. Import done"], ["Metamodelo y capas incorporados al proyecto.", "Metamodel and layers added to the project."]),
      N("e", "end", null, null, "recovery", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c", "opt"), E("b", "d"), E("c", "d"), E("d", "f"), E("f", "g"), E("g", "h", "next", "Sí", "Yes"), E("g", "f", "loop", "No: revisar", "No: review"), E("h", "e")] });

  HM.proc({ id: "recovery_author_workspace", scope: "authoring", cap: "recovery", elems: ["candidate", "evidence", "conflict", "materialize"],
    n: T("Espacio de trabajo avanzado", "Advanced workspace"), d: T("Revisión fina de candidatos, evidencia y materialización.", "Fine review of candidates, evidence and materialization."),
    nodes: [
      N("s", "start", "user", null, "recovery", ["Abrir el espacio avanzado", "Open the advanced workspace"], ["Trece pestañas agrupadas en Entender, Revisar y Construir.", "Thirteen tabs grouped as Understand, Review and Build."]),
      N("a", "step", "user", "MANDATORY", "recovery", ["Elegir candidato", "Choose a candidate"], ["Lista de radio: «Nivel n · método» con insignia de confianza y justificación.", "Radio list: \"Level n · method\" with confidence badge and rationale."], { e: "candidate" }),
      N("b", "step", "user", "MANDATORY", "recovery", ["Filtrar la evidencia", "Filter the evidence"], ["Por nivel, origen, archivo, elemento, método o advertencias.", "By level, origin, file, element, method or warnings."], { e: "evidence" }),
      N("c", "decision", "auto", "MANDATORY", "recovery", ["¿Hay conflictos?", "Any conflicts?"], ["Separa los bloqueantes de los resueltos con seguridad.", "Separates blocking ones from safely resolved ones."]),
      N("d", "step", "user", "CONDITIONAL", "recovery", ["Elegir evidencia a conservar", "Choose evidence to keep"], ["Un desplegable por conflicto y «Construir vista previa reconciliada».", "A dropdown per conflict and \"Build reconciled preview\"."], { e: "conflict" }),
      N("f", "step", "user", "MANDATORY", "recovery", ["Previsualizar materialización", "Preview materialization"], ["Nunca guarda.", "Never saves."], { e: "materialize" }),
      N("g", "step", "user", "OPTIONAL", "recovery", ["Importar y materializar capas seleccionadas", "Import and materialize selected layers"], ["Guarda de verdad.", "Really saves."], { e: "materialize", s: ["materialización: vista previa → guardada", "materialization: preview → saved"] }),
      N("h", "step", "user", "CONDITIONAL", "recovery", ["Deshacer materialización", "Undo materialization"], ["Solo visible si el resultado lo permite.", "Only visible if the result allows it."]),
      N("e", "end", null, null, "recovery", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c"), E("c", "d", "cond", "Sí", "Yes"), E("c", "f", "next", "No", "No"), E("d", "f"), E("f", "g", "opt"), E("g", "h", "cond"), E("g", "e"), E("h", "e")] });

  HM.proc({ id: "recovery_author_governance", scope: "authoring", cap: "recovery", elems: ["gate"],
    n: T("Puerta de publicación verificada", "Verified publication gate"), d: T("Las cinco comprobaciones y la atestación.", "The five checks and the attestation."),
    nodes: [
      N("s", "start", "user", null, "recovery", ["Abrir la pestaña Gobernanza", "Open the Governance tab"], ["Está en el espacio avanzado.", "It is in the advanced workspace."]),
      N("a", "step", "auto", "MANDATORY", "recovery", ["Fusión semántica profunda", "Deep semantic merge"], ["Primer bloque.", "First block."], { e: "gate" }),
      N("b", "step", "user", "OPTIONAL", "recovery", ["Exigir espejo, banco o rendimiento", "Require mirror, bench or performance"], ["Casillas para endurecer la puerta.", "Checkboxes to harden the gate."], { e: "gate" }),
      N("c", "step", "auto", "MANDATORY", "recovery", ["Espejo p2, banco y rendimiento", "p2 mirror, bench and performance"], ["Tres bloques con resultado por comprobación.", "Three blocks with a result per check."]),
      N("d", "step", "auto", "MANDATORY", "recovery", ["Compatibilidad y publicación", "Compatibility and publication"], ["Quinto bloque y resultado final.", "Fifth block and final result."]),
      N("r", "artifact", null, null, "recovery", ["Atestación HTML con SHA-256", "HTML attestation with SHA-256"], ["Archivo descargable que demuestra el resultado.", "Downloadable file that proves the result."]),
      N("e", "end", null, null, "recovery", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "opt"), E("a", "c"), E("b", "c"), E("c", "d"), E("d", "r"), E("r", "e")] });

  // =====================================================================================================
  // TOOLCHAINS
  // =====================================================================================================
  HM.cap("toolchains", {
    name: T("Toolchains", "Installed tools"), group: "external",
    user: {
      tagline: T("¿Está instalada la herramienta que necesito?", "Is the tool I need installed?"),
      what: T("Comprueba las herramientas externas que HYDRA puede necesitar y, para los motores de transformación, permite instalarlas, actualizarlas o eliminarlas desde el navegador.", "Checks the external tools HYDRA may need and, for transformation engines, lets you install, update or remove them from the browser."),
      why: T("Saber de antemano si algo funcionará evita fallos a mitad de una operación.", "Knowing beforehand whether something will work avoids failures midway through an operation."),
      when: T("Antes de compilar un editor generado o de ejecutar una transformación con un motor externo.", "Before building a generated editor or running a transformation with an external engine."),
      canDefine: [T("Qué comprobaciones ejecutar", "Which checks to run"), T("Qué versión de un motor instalar", "Which engine version to install"), T("Instalar, actualizar o eliminar un motor", "Install, update or remove an engine"), T("Un comando explícito por lenguaje (solo administración)", "An explicit command per language (administrators only)")],
      needs: [T("Acceso al servidor donde se ejecutan las herramientas", "Access to the server where the tools run"), T("Para instalar: un paquete del catálogo de confianza", "To install: a package from the trusted catalog")],
      produces: [T("Un estado por tecnología: superada, parcial, fallida o no ejecutada", "A status per technology: passed, partial, failed or not run"), T("Un motor instalado, actualizado o eliminado", "An installed, updated or removed engine"), T("Un registro de evidencia de cada comprobación", "An evidence record of each check")],
      mandatory: [T("Ejecutar las comprobaciones de herramientas genéricas", "Run the generic tool checks")],
      optional: [T("Instalar, actualizar o eliminar un motor de transformación", "Install, update or remove a transformation engine"), T("Fijar un comando de motor como administrador", "Set an engine command as an administrator")],
      conditional: [],
      next: [T("Ejecutar una transformación con un motor externo", "Run a transformation with an external engine"), T("Generar y compilar un editor", "Generate and build an editor")],
      interacts: [{ cap: "transformation", t: T("Los motores nativos se instalan desde el panel de vista previa de Transformaciones", "Native engines are installed from the Transformations preview panel") }, { cap: "integration", t: T("El centro de compatibilidad comprueba herramientas que Integración necesita", "The compatibility center checks tools that Integration needs") }, { cap: "grammar", t: T("Una gramática generada necesita npm o Maven ya presentes", "A generated grammar needs npm or Maven already present") }]
    },
    func: {
      lifecycle: [T("sin comprobar → comprobado (instantánea bajo demanda)", "unchecked → checked (on-demand snapshot)"), T("motor no instalado → instalado → actualizado / eliminado", "engine not installed → installed → updated / removed"), T("instalación incompatible → revertida automáticamente", "incompatible install → automatically reverted")],
      semantics: [
        T("No existe una página única de «Toolchains»: hay dos superficies separadas, cada una con su propio catálogo y su propio sentido de «disponible».", "There is no single \"Toolchains\" page: there are two separate surfaces, each with its own catalog and its own meaning of \"available\"."),
        T("«Available tools» es un panel de solo lectura de 7 tecnologías: EMF, Sirius Desktop, Xtext, Langium, Eclipse GLSP, BESSER SDK y BESSER Web.", "\"Available tools\" is a read-only panel of 7 technologies: EMF, Sirius Desktop, Xtext, Langium, Eclipse GLSP, BESSER SDK and BESSER Web."),
        T("Cada tecnología queda en un estado: superada, parcial, fallida o no ejecutada.", "Each technology ends in a status: passed, partial, failed or not run."),
        T("Los motores nativos de Transformación son ATL, ETL, Acceleo, EGL y Xtend; se instalan por lenguaje, con una instalación activa como máximo.", "The native Transformation engines are ATL, ETL, Acceleo, EGL and Xtend; installed per language, with at most one active install."),
        T("Para un motor existen dos estados reales: la herramienta existe y se puede ejecutar ahora mismo; no hay estados propios «puente disponible» o «motor disponible».", "For an engine there are two real states: the tool exists and it can be executed right now; there are no separate \"bridge available\" or \"engine available\" states."),
        T("No hay selector de «qué motor usar» al ejecutar: el comando está fijado por lenguaje; el selector de versión solo existe al instalar.", "There is no \"which engine to use\" selector when running: the command is fixed per language; the version selector only exists when installing.")],
      states: [{ n: T("Superada", "Passed"), d: T("La herramienta responde", "The tool responds") }, { n: T("Parcial", "Partial"), d: T("Algo falta", "Something is missing") }, { n: T("Fallida", "Failed"), d: T("No funciona", "Does not work") }, { n: T("No ejecutada", "Not run"), d: T("Aún sin comprobar", "Not yet checked") }],
      cross: [T("Transformación: panel de aprovisionamiento anidado en su vista previa.", "Transformation: provisioning panel nested in its preview."), T("Integración y Gramática: comparten el centro de compatibilidad.", "Integration and Grammar: share the compatibility center.")]
    },
    tech: {
      runtime: T("Tres subsistemas independientes que comparten la etiqueta TOOL_UNAVAILABLE: aprovisionamiento de motores de Transformación, centro de compatibilidad de solo lectura y build de gramática generada.", "Three independent subsystems sharing the TOOL_UNAVAILABLE label: Transformation engine provisioning, read-only compatibility center and generated grammar build."),
      services: ["NativeToolchainProbeCatalog", "NativeToolchainProbeExecutor", "TransformationToolchainProvisioningService"],
      apis: ["Panel de aprovisionamiento por lenguaje de exportación"],
      schemas: ["TransformationToolchainPackageDescriptor"],
      tests: [],
      evidence: [EV(PTC, 1, "NativeToolchainProbeCatalog / Executor"), EV(PNE, 1, "TransformationToolchainProvisioningService")],
      notes: [T("Los sondeos solo ejecutan comandos de versión de una lista cerrada (java, mvn, node, npm, python/pip); no instalan nada.", "Probes only run version commands from a closed list (java, mvn, node, npm, python/pip); they install nothing."),
        T("La instalación verifica SHA-256 de cada fichero contra el manifiesto, copia de forma atómica y revierte si el bundle se declara incompatible de protocolo; el catálogo de confianza vive en distribution/transformation-toolchains.", "Install verifies SHA-256 of each file against the manifest, copies atomically and reverts if the bundle declares itself protocol-incompatible; the trusted catalog lives in distribution/transformation-toolchains."),
        T("Un archivo de propiedades de administrador fija comandos por lenguaje y tiene prioridad sobre cualquier paquete autoinstalado.", "An administrator properties file sets commands per language and takes priority over any auto-installed package."),
        T("La elección de tecnología de Gramática (Langium/Xtext/ANTLR) es configuración de build, no un motor instalable con versión propia.", "The Grammar technology choice (Langium/Xtext/ANTLR) is build configuration, not an installable engine with its own version."),
        T("Datos tomados de la base heredada; las rutas del frontend del centro de compatibilidad no se releyeron.", "Data taken from the legacy base; the compatibility center frontend paths were not re-read.")]
    },
    elements: [
      { id: "probe", n: T("Comprobación de herramientas genéricas", "Generic tool check"), kind: "concept", req: "MANDATORY",
        mean: T("Una prueba de solo lectura de que las herramientas de desarrollo necesarias están en el servidor.", "A read-only test that the necessary development tools are on the server."), purpose: T("Saber si puedes depender de una tecnología antes de intentarlo.", "Know whether you can depend on a technology before trying."), when: T("Antes de compilar un proyecto generado.", "Before building a generated project."),
        card: "1 catálogo de 7 tecnologías ↔ 1 comprobación por solicitud",
        variants: [V("EMF", "EMF", "Modelado Eclipse", "Eclipse modeling", "OPTIONAL"), V("Sirius Desktop", "Sirius Desktop", "Editores gráficos de escritorio", "Desktop graphical editors", "OPTIONAL"), V("Xtext", "Xtext", "Lenguajes textuales", "Textual languages", "OPTIONAL"), V("Langium", "Langium", "Lenguajes textuales en TypeScript", "Textual languages in TypeScript", "OPTIONAL"),
          V("Eclipse GLSP", "Eclipse GLSP", "Editores gráficos web", "Web graphical editors", "OPTIONAL"), V("BESSER SDK", "BESSER SDK", "Kit de BESSER", "BESSER kit", "OPTIONAL"), V("BESSER Web", "BESSER Web", "Entorno web de BESSER", "BESSER web environment", "OPTIONAL")],
        props: [PR("Qué comprobaciones ejecutar", "Which checks to run", "MANDATORY", "Seleccionas y pulsas Ejecutar", "You select and press Run")],
        io: { i: T("Selección de comprobaciones", "Selection of checks"), o: T("Estado y registro de evidencia por tecnología", "Status and evidence record per technology") }, effect: T("No instala ni modifica nada.", "Installs or modifies nothing."),
        func: { rel: [T("Compartida por Integración y Gramática", "Shared by Integration and Grammar")], lifecycle: T("Instantánea bajo demanda.", "On-demand snapshot."), cond: T("Independiente del metamodelo activo.", "Independent of the active metamodel.") },
        tech: { type: "NativeToolchainProbeCatalog", schema: "7 tecnologías con comandos de versión", evidence: [EV(PTC, 1, "Catálogo de sondeo")] } },
      { id: "probestate", n: T("Estado de una comprobación", "Check status"), kind: "variant-set", req: "DERIVED",
        mean: T("El resultado de cada comprobación.", "The result of each check."), purpose: T("Ver de un vistazo qué funciona.", "See at a glance what works."), when: T("Tras ejecutar.", "After running."), card: "1 tecnología ↔ 1 estado",
        variants: [V("Superada", "Passed", "La herramienta responde", "The tool responds", "DERIVED"), V("Parcial", "Partial", "Solo parte está disponible", "Only part is available", "DERIVED"), V("Fallida", "Failed", "No funciona", "Does not work", "DERIVED"), V("No ejecutada", "Not run", "Todavía sin comprobar", "Not yet checked", "DERIVED")],
        io: { i: T("Salida del sondeo", "Probe output"), o: T("Etiqueta de estado", "Status label") }, effect: T("Solo informa.", "Only informs."),
        func: { rel: [T("Se asigna a cada tecnología", "Assigned to each technology")], lifecycle: T("No ejecutada → superada/parcial/fallida", "Not run → passed/partial/failed"), cond: T("Cuatro valores posibles.", "Four possible values.") },
        tech: { type: "PASSED/PARTIAL/FAILED/NOT_RUN", schema: "enum de estado", evidence: [EV(PTC, 1, "Estado por tecnología")] } },
      { id: "engine", n: T("Motor nativo de Transformación", "Native Transformation engine"), kind: "variant-set", req: "OPTIONAL",
        mean: T("Un motor externo instalable con el que ejecutar transformaciones con paridad real.", "An installable external engine to run transformations with real parity."), purpose: T("Ejecutar contra el producto original y no solo contra el runtime propio.", "Run against the original product, not only against the built-in runtime."), when: T("Cuando necesitas resultados idénticos a los del motor externo.", "When you need results identical to the external engine."),
        card: "1 lenguaje ↔ 0..N versiones instalables ↔ 0..1 instalación activa",
        variants: [V("ATL", "ATL", "Transformaciones de modelo a modelo", "Model-to-model transformations", "ALTERNATIVE"), V("ETL", "ETL", "Transformación de la familia Epsilon", "Epsilon-family transformation", "ALTERNATIVE"), V("Acceleo", "Acceleo", "Generación de texto", "Text generation", "ALTERNATIVE"), V("EGL", "EGL", "Generación de texto de Epsilon", "Epsilon text generation", "ALTERNATIVE"), V("Xtend", "Xtend", "Generación en Xtend", "Xtend-based generation", "ALTERNATIVE")],
        props: [PR("Lenguaje", "Language", "MANDATORY", "A qué lenguaje de exportación pertenece", "Which export language it belongs to"), PR("Versión del paquete", "Package version", "MANDATORY", "Se elige del catálogo", "Chosen from the catalog"), PR("Puente frente a herramienta", "Bridge versus tool", "DERIVED", "Adaptador propio de HYDRA frente al producto externo; sus versiones deben coincidir", "HYDRA's own adapter versus the external product; their versions must match"),
          PR("Instalable e instalado", "Installable and installed", "DERIVED", "Estado y diagnósticos", "Status and diagnostics")],
        io: { i: T("Paquete del catálogo de confianza", "Package from the trusted catalog"), o: T("Motor instalado y verificado, o eliminado", "Installed and verified engine, or removed") }, effect: T("Instalar verifica cada fichero y revierte si es incompatible.", "Installing verifies each file and reverts if incompatible."),
        func: { rel: [T("Se opera desde el panel de vista previa de Transformaciones", "Operated from the Transformations preview panel")], lifecycle: T("no instalado → instalado → actualizado/eliminado", "not installed → installed → updated/removed"), cond: T("Un comando de administrador tiene prioridad sobre el paquete instalado.", "An administrator command takes priority over the installed package.") },
        tech: { type: "TransformationToolchainPackageDescriptor", schema: "language, packageId, packageVersion, bridge/toolchain", evidence: [EV(PNE, 1, "Aprovisionamiento")] } },
      { id: "enginestate", n: T("Disponibilidad de un motor", "Engine availability"), kind: "variant-set", req: "DERIVED",
        mean: T("Dos hechos distintos sobre un motor.", "Two distinct facts about an engine."), purpose: T("Distinguir «existe» de «puedo ejecutarlo ya».", "Tell \"it exists\" apart from \"I can run it now\"."), when: T("Al diagnosticar.", "When diagnosing."), card: "1 motor ↔ 2 estados",
        variants: [V("La herramienta existe", "The tool exists", "Está presente", "It is present", "DERIVED"), V("Se puede ejecutar ahora", "It can run now", "Listo para usarse", "Ready to use", "DERIVED")],
        io: { i: T("Diagnóstico del motor", "Engine diagnosis"), o: T("Dos indicadores", "Two indicators") }, effect: T("Solo informa; no existen estados propios de puente o motor.", "Only informs; there are no separate bridge or engine states."),
        func: { rel: [T("Ligada al motor", "Tied to the engine")], lifecycle: T("Se recalcula al diagnosticar.", "Recomputed on diagnosis."), cond: T("Dos estados; ningún otro.", "Two states; no others.") },
        tech: { type: "toolAvailable / executionAvailable", schema: "diagnóstico", evidence: [EV(PNE, 1, "Disponibilidad")] } },
      { id: "admincmd", n: T("Comando de administrador por lenguaje", "Administrator command per language"), kind: "concept", req: "OPTIONAL",
        mean: T("Un comando explícito fijado en la configuración del servidor.", "An explicit command set in the server configuration."), purpose: T("Usar un motor ya instalado a mano.", "Use an engine already installed manually."), when: T("Solo administradores.", "Administrators only."), card: "1 lenguaje ↔ 0..1 comando",
        io: { i: T("Propiedades del servidor", "Server properties"), o: T("Comando fijado", "Pinned command") }, effect: T("Tiene prioridad sobre un paquete autoinstalado.", "Takes priority over an auto-installed package."),
        func: { rel: [T("Anula al paquete instalado por HYDRA", "Overrides the package installed by HYDRA")], lifecycle: T("Sin interfaz propia.", "No UI of its own."), cond: T("Solo si el administrador lo configura.", "Only if the administrator configures it.") },
        tech: { type: "Archivo de propiedades", schema: "comando por lenguaje", evidence: [EV(PNE, 1, "Configuración explícita")] } }
    ],
    map: {
      nodes: [
        { id: "probe", elem: "probe", n: T("Comprobación genérica", "Generic check"), k: "concept", req: "MANDATORY", d: T("7 tecnologías", "7 technologies") },
        { id: "pstate", elem: "probestate", n: T("Estado", "Status"), k: "derived", req: "DERIVED", d: T("Superada, parcial, fallida, no ejecutada", "Passed, partial, failed, not run") },
        { id: "tech", n: T("Tecnología", "Technology"), k: "concept", req: "MANDATORY", d: T("EMF, Xtext, Langium…", "EMF, Xtext, Langium…") },
        { id: "engine", elem: "engine", n: T("Motor nativo", "Native engine"), k: "concept", req: "OPTIONAL", d: T("ATL, ETL, Acceleo, EGL, Xtend", "ATL, ETL, Acceleo, EGL, Xtend") },
        { id: "atl", n: T("ATL", "ATL"), k: "variant", req: "ALTERNATIVE", d: T("Un motor", "An engine") },
        { id: "acc", n: T("Acceleo", "Acceleo"), k: "variant", req: "ALTERNATIVE", d: T("Un motor", "An engine") },
        { id: "estate", elem: "enginestate", n: T("Disponibilidad", "Availability"), k: "derived", req: "DERIVED", d: T("Existe / ejecutable", "Exists / runnable") },
        { id: "admin", elem: "admincmd", n: T("Comando del administrador", "Administrator command"), k: "concept", req: "OPTIONAL", d: T("Prioritario", "Takes priority") },
        { id: "cat", n: T("Catálogo de confianza", "Trusted catalog"), k: "external", req: "OPTIONAL", d: T("Paquetes verificados", "Verified packages") }
      ],
      edges: [
        { a: "probe", b: "tech", rel: "contains", card1: "1", card2: "7", req: "MANDATORY" }, { a: "tech", b: "pstate", rel: "gen", req: "MANDATORY" },
        { a: "atl", b: "engine", rel: "isa", req: "ALTERNATIVE" }, { a: "acc", b: "engine", rel: "isa", req: "ALTERNATIVE" },
        { a: "engine", b: "estate", rel: "gen", req: "MANDATORY" }, { a: "engine", b: "cat", rel: "ref", req: "OPTIONAL" }, { a: "admin", b: "engine", rel: "ref", req: "OPTIONAL" }
      ]
    },
    define: [
      { id: "available", n: T("Centro «Available tools»", "\"Available tools\" center"), kind: "form", req: "MANDATORY", elems: ["probe", "probestate"],
        does: T("Abres el módulo «Available tools» del menú principal, seleccionas qué comprobaciones ejecutar y pulsas «Ejecutar». Ves el estado y la evidencia de cada tecnología.", "You open the \"Available tools\" module from the main menu, select which checks to run and press \"Run\". You see the status and evidence of each technology."),
        func: T("Solo lectura: no instala ni configura nada.", "Read-only: installs and configures nothing."), tech: T("Módulo de navegación de primer nivel, independiente de Transformación e Integración.", "First-level navigation module, independent of Transformation and Integration."), proc: "toolchains_author_available" },
      { id: "provision", n: T("Panel de motores nativos", "Native engines panel"), kind: "form", req: "OPTIONAL", elems: ["engine", "enginestate"],
        does: T("En el panel de vista previa de Transformación, por cada lenguaje de exportación eliges una versión del catálogo y pulsas Instalar, Actualizar o Eliminar.", "In the Transformation preview panel, for each export language you pick a catalog version and press Install, Update or Remove."),
        func: T("El selector de versión solo existe aquí; al ejecutar no se elige motor.", "The version selector only exists here; no engine is chosen when running."), tech: T("Panel anidado en la vista previa de Transformación.", "Panel nested in the Transformation preview."), proc: "toolchains_author_provision" },
      { id: "admin", n: T("Archivo de configuración del administrador", "Administrator configuration file"), kind: "property", req: "OPTIONAL", elems: ["admincmd"],
        does: T("Un administrador fija en la configuración del servidor un comando explícito por lenguaje.", "An administrator sets an explicit command per language in the server configuration."),
        func: T("Prevalece sobre un paquete autoinstalado; sin interfaz de usuario.", "Prevails over an auto-installed package; no user interface."), tech: T("Propiedades de comando por lenguaje.", "Command properties per language.") }
    ],
    rel: [
      { to: "transformation", type: "optional", dir: "in", u: T("Las transformaciones pueden usar un motor externo instalado.", "Transformations can use an installed external engine."), f: T("Comando fijado por lenguaje.", "Command fixed per language."), t: T("Aprovisionamiento anidado en la vista previa.", "Provisioning nested in the preview.") },
      { to: "integration", type: "optional", dir: "in", u: T("Integración comprueba aquí las herramientas que necesita.", "Integration checks here the tools it needs."), f: T("Centro de compatibilidad compartido.", "Shared compatibility center."), t: T("Catálogo de sondeo compartido.", "Shared probe catalog.") },
      { to: "grammar", type: "optional", dir: "in", u: T("Una gramática generada necesita herramientas de compilación presentes.", "A generated grammar needs build tools to be present."), f: T("Verificadas aquí antes del build.", "Verified here before the build."), t: T("Build real con npm/Maven.", "Real build with npm/Maven.") }
    ]
  });

  HM.proc({ id: "toolchains_check_install", scope: "cap", cap: "toolchains", elems: ["probe", "engine", "enginestate"],
    n: T("Comprobar o instalar una herramienta", "Check or install a tool"), d: T("Sondear, diagnosticar un motor y, si hace falta, instalarlo.", "Probe, diagnose an engine and, if needed, install it."),
    nodes: [
      N("s", "start", "user", null, "toolchains", ["Necesito una herramienta externa", "I need an external tool"], ["Antes de compilar o ejecutar con un motor externo.", "Before building or running with an external engine."]),
      N("d1", "decision", "user", "MANDATORY", "toolchains", ["¿Genérica o motor de transformación?", "Generic or transformation engine?"], ["Son dos superficies distintas con catálogos separados.", "They are two separate surfaces with separate catalogs."]),
      N("a1", "step", "auto", "OPTIONAL", "toolchains", ["Sondear herramientas", "Probe tools"], ["Se ejecuta un comando de versión de una lista cerrada.", "A version command from a closed list is run."], { e: "probe", out: ["Estado por tecnología", "Status per technology"], x: { rt: "NativeToolchainProbeExecutor", test: [], ev: [EV(PTC, 1, "sondeo")] } }),
      N("a2", "step", "auto", "OPTIONAL", "toolchains", ["Diagnosticar el motor", "Diagnose the engine"], ["Se ejecuta el puente con una autodescripción en un espacio temporal aislado.", "The bridge is run with a self-description flag in an isolated temporary workspace."], { e: "enginestate" }),
      N("d2", "decision", "auto", "MANDATORY", "toolchains", ["¿Existe y se puede ejecutar?", "Does it exist and can it run?"], ["Si sí, ya puedes usarlo.", "If so, you can already use it."]),
      N("a3", "step", "user", "CONDITIONAL", "toolchains", ["Elegir versión del catálogo", "Choose a catalog version"], ["Solo paquetes del catálogo de confianza.", "Only packages from the trusted catalog."], { e: "engine" }),
      N("a4", "step", "auto", "CONDITIONAL", "toolchains", ["Verificar e instalar", "Verify and install"], ["Se comprueba cada fichero contra el manifiesto y se copia de forma atómica.", "Each file is checked against the manifest and copied atomically."], { s: ["motor: no instalado → instalado", "engine: not installed → installed"], x: { rt: "TransformationToolchainProvisioningService", test: [], ev: [EV(PNE, 1, "instalación SHA-256")] } }),
      N("d3", "decision", "auto", "CONDITIONAL", "toolchains", ["¿Compatible?", "Compatible?"], ["Si el bundle se declara incompatible de protocolo, se revierte.", "If the bundle declares itself protocol-incompatible, it is reverted."]),
      N("a5", "step", "auto", "CONDITIONAL", "toolchains", ["Revertir la instalación", "Revert the install"], ["Se deja el estado anterior.", "The previous state is restored."]),
      N("r", "artifact", null, null, "toolchains", ["Estado conocido o motor listo", "Known status or engine ready"], ["Sabes qué funciona o tienes el motor instalado.", "You know what works or have the engine installed."]),
      N("e", "end", null, null, "toolchains", ["Fin", "End"])
    ],
    edges: [E("s", "d1"), E("d1", "a1", "alt", "Genérica", "Generic"), E("d1", "a2", "alt", "Motor", "Engine"), E("a1", "r"), E("a2", "d2"), E("d2", "r", "next", "Sí", "Yes"), E("d2", "a3", "cond", "No", "No"), E("a3", "a4"), E("a4", "d3"), E("d3", "r", "next", "Sí", "Yes"), E("d3", "a5", "cond", "No", "No"), E("a5", "e"), E("r", "e")] });

  HM.proc({ id: "toolchains_engine_lifecycle", scope: "cap", cap: "toolchains", elems: ["engine"],
    n: T("Ciclo de vida de un motor instalado", "Lifecycle of an installed engine"), d: T("Instalar, actualizar y eliminar.", "Install, update and remove."),
    nodes: [
      N("s", "start", "user", null, "toolchains", ["Abrir el panel de motores", "Open the engines panel"], ["Está en la vista previa de Transformación.", "It is in the Transformation preview."]),
      N("a", "decision", "user", "MANDATORY", "toolchains", ["¿Instalar, actualizar o eliminar?", "Install, update or remove?"], ["Tres acciones sobre el motor del lenguaje.", "Three actions on the language's engine."], { e: "engine" }),
      N("b", "step", "auto", "OPTIONAL", "toolchains", ["Instalar", "Install"], ["Verifica y copia el paquete elegido.", "Verifies and copies the chosen package."], { s: ["motor: no instalado → instalado", "engine: not installed → installed"] }),
      N("c", "step", "auto", "OPTIONAL", "toolchains", ["Actualizar", "Update"], ["Sustituye la instalación activa por otra versión.", "Replaces the active install with another version."], { s: ["motor: versión A → versión B", "engine: version A → version B"] }),
      N("d", "step", "auto", "OPTIONAL", "toolchains", ["Eliminar", "Remove"], ["Quita el motor gestionado.", "Removes the managed engine."], { s: ["motor: instalado → no instalado", "engine: installed → not installed"] }),
      N("r", "artifact", null, null, "toolchains", ["Estado del motor actualizado", "Updated engine status"], ["Disponibilidad refrescada.", "Availability refreshed."]),
      N("e", "end", null, null, "toolchains", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "alt", "Instalar", "Install"), E("a", "c", "alt", "Actualizar", "Update"), E("a", "d", "alt", "Eliminar", "Remove"), E("b", "r"), E("c", "r"), E("d", "r"), E("r", "e")] });

  HM.proc({ id: "toolchains_author_available", scope: "authoring", cap: "toolchains", elems: ["probe", "probestate"],
    n: T("Centro «Available tools»", "\"Available tools\" center"), d: T("Seleccionar y ejecutar comprobaciones.", "Select and run checks."),
    nodes: [
      N("s", "start", "user", null, "toolchains", ["Abrir «Available tools»", "Open \"Available tools\""], ["Módulo del menú principal.", "Main menu module."]),
      N("a", "step", "user", "MANDATORY", "toolchains", ["Seleccionar comprobaciones", "Select checks"], ["Eliges qué tecnologías comprobar.", "You choose which technologies to check."], { e: "probe" }),
      N("b", "step", "user", "MANDATORY", "toolchains", ["Pulsar «Ejecutar»", "Press \"Run\""], ["Se lanzan los sondeos.", "Probes are launched."]),
      N("c", "step", "auto", "MANDATORY", "toolchains", ["Sondear", "Probe"], ["Comandos de versión de una lista cerrada.", "Version commands from a closed list."]),
      N("r", "artifact", null, null, "toolchains", ["Estado y evidencia", "Status and evidence"], ["Por tecnología: superada, parcial, fallida o no ejecutada.", "Per technology: passed, partial, failed or not run."], { e: "probestate" }),
      N("e", "end", null, null, "toolchains", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c"), E("c", "r"), E("r", "e")] });

  HM.proc({ id: "toolchains_author_provision", scope: "authoring", cap: "toolchains", elems: ["engine", "enginestate"],
    n: T("Panel de motores nativos", "Native engines panel"), d: T("Elegir versión e instalar, actualizar o eliminar.", "Pick a version and install, update or remove."),
    nodes: [
      N("s", "start", "user", null, "toolchains", ["Abrir la vista previa de Transformación", "Open the Transformation preview"], ["El panel de aprovisionamiento está anidado aquí.", "The provisioning panel is nested here."]),
      N("a", "step", "user", "MANDATORY", "toolchains", ["Elegir lenguaje de exportación", "Choose export language"], ["Un panel por lenguaje.", "One panel per language."], { e: "engine" }),
      N("b", "step", "user", "MANDATORY", "toolchains", ["Elegir versión del catálogo", "Choose catalog version"], ["Solo versiones del catálogo de confianza.", "Only trusted catalog versions."]),
      N("c", "step", "user", "MANDATORY", "toolchains", ["Pulsar Instalar, Actualizar o Eliminar", "Press Install, Update or Remove"], ["Botón correspondiente.", "Matching button."]),
      N("d", "step", "auto", "MANDATORY", "toolchains", ["Verificar y aplicar", "Verify and apply"], ["Comprobación de ficheros y reversión si es incompatible.", "File check and reversal if incompatible."]),
      N("r", "artifact", null, null, "toolchains", ["Estado del motor", "Engine status"], ["Existe y se puede ejecutar, o no.", "Exists and can run, or not."], { e: "enginestate" }),
      N("e", "end", null, null, "toolchains", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c"), E("c", "d", "next", "Cualquiera", "Any"), E("d", "r"), E("d", "b", "loop", "Incompatible: otra versión", "Incompatible: another version"), E("r", "e")] });

  // =====================================================================================================
  // EXTERNAL REPOSITORIES
  // =====================================================================================================
  HM.cap("external_repositories", {
    name: T("Repositorios externos", "External repositories"), group: "external",
    user: {
      tagline: T("Conecta HYDRA con Git y con la nube", "Connect HYDRA to Git and the cloud"),
      what: T("Conecta HYDRA con GitHub, GitLab, One Drive, Google Drive, Dropbox o Git genérico para exportar e importar proyectos y, en los proveedores Git, trabajar con ramas, commits y fusiones.", "Connects HYDRA to GitHub, GitLab, One Drive, Google Drive, Dropbox or generic Git to export and import projects and, with Git providers, work with branches, commits and merges."),
      why: T("Respaldas, compartes y sincronizas con la infraestructura que tu equipo ya usa, sin salir de HYDRA.", "You back up, share and sync with the infrastructure your team already uses, without leaving HYDRA."),
      when: T("Cuando quieres guardar fuera, colaborar o traer contenido de un proveedor externo.", "When you want to store outside, collaborate or bring in content from an external provider."),
      canDefine: [T("Un perfil de conexión por proveedor", "A connection profile per provider"), T("Qué proveedor y qué destino", "Which provider and which destination"), T("Conectar tu cuenta mediante OAuth", "Connect your account through OAuth"), T("Operaciones Git: fetch, pull, push, commit, rama, checkout, merge", "Git operations: fetch, pull, push, commit, branch, checkout, merge"),
        T("Exportar o importar un proyecto completo como ZIP", "Export or import a whole project as ZIP"), T("Exportar o importar una capa individual", "Export or import a single layer")],
      needs: [T("Una cuenta en el proveedor", "An account with the provider"), T("Los datos de identidad y destino del proveedor", "The provider identity and destination data")],
      produces: [T("Una conexión autenticada reutilizable", "A reusable authenticated connection"), T("Un proyecto exportado o importado como archivo", "A project exported or imported as a file"), T("Estado del repositorio Git: ramas y commits", "Git repository state: branches and commits"), T("Conflictos resueltos explícitamente", "Explicitly resolved conflicts")],
      mandatory: [T("Perfil de conexión", "Connection profile")],
      optional: [T("Transferencia de archivos", "File transfer")],
      conditional: [T("Operaciones Git (solo GitHub, GitLab y Git genérico)", "Git operations (only GitHub, GitLab and generic Git)")],
      next: [T("Exportar el proyecto", "Export the project"), T("Hacer pull y resolver conflictos", "Pull and resolve conflicts"), T("Importar desde el repositorio", "Import from the repository")],
      interacts: [{ cap: "project", t: T("Importación de archivo y exportación de selección", "File import and selection export") }, { cap: "recovery", t: T("No alimenta la recuperación: son caminos separados", "Does not feed recovery: separate paths") }]
    },
    func: {
      lifecycle: [T("perfil guardado → conectado → operar o transferir", "profile saved → connected → operate or transfer"), T("conflicto de fusión → resolución explícita → preparada", "merge conflict → explicit resolution → staged"), T("perfil: guardar, probar, eliminar", "profile: save, test, delete")],
      semantics: [
        T("Exactamente 6 proveedores: GitHub, GitLab, One Drive, Google Drive, Dropbox y Git genérico.", "Exactly 6 providers: GitHub, GitLab, One Drive, Google Drive, Dropbox and generic Git."),
        T("Solo GitHub, GitLab y Git genérico admiten operaciones Git; los otros tres solo transferencia de archivos, y el código rechaza explícitamente las operaciones Git en ellos.", "Only GitHub, GitLab and generic Git support Git operations; the other three only file transfer, and the code explicitly rejects Git operations on them."),
        T("Autenticación OAuth con código de autorización y PKCE (por defecto) o Device Code (solo si lo habilita el administrador); Git genérico usa una credencial gestionada en el servidor.", "OAuth authentication with authorization code and PKCE (default) or Device Code (only if the administrator enables it); generic Git uses a credential managed on the server."),
        T("No hay autenticación por clave SSH ni por token personal escrito a mano.", "There is no SSH key authentication nor hand-typed personal token."),
        T("Nunca hay force-push ni reset arbitrario: están excluidos a propósito.", "There is never a force-push or arbitrary reset: they are deliberately excluded."),
        T("Los conflictos se muestran en un visor de 4 paneles (base, local, remoto y resultado), nunca se resuelven de forma silenciosa.", "Conflicts are shown in a 4-pane viewer (base, local, remote and result), never resolved silently."),
        T("Importar desde un repositorio remoto es restaurar archivos y no activa la recuperación semántica.", "Importing from a remote repository restores files and does not activate semantic recovery.")],
      states: [{ n: T("Sin conectar", "Not connected"), d: T("Perfil guardado sin identidad", "Profile saved without identity") }, { n: T("Conectado", "Connected"), d: T("Credencial en la bóveda del servidor", "Credential in the server vault") }, { n: T("Conflicto", "Conflict"), d: T("Fusión pendiente de resolver", "Merge pending resolution") }],
      cross: [T("Proyecto: es el origen o destino de importar y exportar.", "Project: it is the source or destination of import and export."), T("Recuperación: sin conexión; la recuperación exige subida local.", "Recovery: no connection; recovery requires a local upload.")]
    },
    tech: {
      runtime: T("Autenticación remota (OAuth/Device Code), espacio de trabajo Git con ejecutor aislado y servicio de transferencia de proyecto.", "Remote authentication (OAuth/Device Code), Git workspace with sandboxed runner and project transfer service."),
      services: ["RemoteAuthService", "RemoteGitWorkspaceService", "RemoteProjectTransferService", "RemoteStorageController"],
      apis: ["/api/remote-auth/*", "/api/remote-git/*", "/api/remote-storage/*"],
      schemas: ["RemoteStorageProviderType (6 valores)", "RemoteGitOperation"],
      tests: [],
      evidence: [EV(PRP, 4, "RemoteStorageProviderType"), EV(PRA, 38, "RemoteAuthService.start()"), EV(PRG, 199, "Git restringido a 3 proveedores")],
      notes: [T("RemoteGitOperation: STATUS, INIT, FETCH, PULL, PUSH, MERGE, ABORT_MERGE, COMMIT_ALL, CREATE_BRANCH, CHECKOUT; siempre vía un ejecutor sin shell.", "RemoteGitOperation: STATUS, INIT, FETCH, PULL, PUSH, MERGE, ABORT_MERGE, COMMIT_ALL, CREATE_BRANCH, CHECKOUT; always through a shell-less runner."),
        T("El token de acceso, el de refresco y el secreto de cliente nunca se devuelven al navegador.", "The access token, refresh token and client secret are never returned to the browser."),
        T("Para los 5 proveedores de solo archivo no hay explorador de árbol remoto: solo lectura/escritura de rutas exactas con control de versión esperada.", "For the 5 file-only providers there is no remote tree browser: only reads/writes of exact paths with expected-version control."),
        T("Detalles del formulario de conexión tomados de datos heredados; los componentes del frontend no se releyeron.", "Connection form details taken from legacy data; frontend components were not re-read.")]
    },
    elements: [
      { id: "provider", n: T("Proveedor", "Provider"), kind: "variant-set", req: "MANDATORY",
        mean: T("El servicio externo al que te conectas.", "The external service you connect to."), purpose: T("Decide qué operaciones tendrás disponibles.", "Decides which operations you will have."), when: T("Al crear el perfil.", "When creating the profile."), card: "1 perfil ↔ 1 proveedor",
        variants: [V("GitHub", "GitHub", "Git completo; owner/repositorio", "Full Git; owner/repository", "ALTERNATIVE"), V("GitLab", "GitLab", "Git completo; identificador de proyecto", "Full Git; project identifier", "ALTERNATIVE"), V("Git genérico", "Generic Git", "Git completo; URL y usuario", "Full Git; URL and user", "ALTERNATIVE"),
          V("One Drive", "One Drive", "Solo archivos; unidad y carpeta", "Files only; drive and folder", "ALTERNATIVE"), V("Google Drive", "Google Drive", "Solo archivos; unidad y carpeta", "Files only; drive and folder", "ALTERNATIVE"), V("Dropbox", "Dropbox", "Solo archivos", "Files only", "ALTERNATIVE")],
        io: { i: T("Elección del proveedor", "Provider choice"), o: T("Campos específicos a rellenar", "Specific fields to fill in") }, effect: T("Solo los tres proveedores Git habilitan el panel de operaciones Git.", "Only the three Git providers enable the Git operations panel."),
        func: { rel: [T("Determina autenticación y operaciones", "Determines authentication and operations")], lifecycle: T("Se elige al crear el perfil.", "Chosen when creating the profile."), cond: T("Exactamente 6; ninguno más.", "Exactly 6; no others.") },
        tech: { type: "RemoteStorageProviderType", schema: "enum de 6 valores", evidence: [EV(PRP, 4, "RemoteStorageProviderType")] } },
      { id: "profile", n: T("Perfil de conexión", "Connection profile"), kind: "concept", req: "MANDATORY",
        mean: T("La identidad y el destino de una conexión concreta.", "The identity and destination of a specific connection."), purpose: T("Dejar la conexión lista para reutilizarla.", "Leave the connection ready for reuse."), when: T("Una vez por proveedor o repositorio.", "Once per provider or repository."), card: "0..N perfiles guardados, uno por conexión",
        props: [PR("Identidad del perfil", "Profile identity", "MANDATORY", "Cómo se llama y a qué proveedor pertenece", "What it is called and which provider it belongs to"), PR("Destino del repositorio", "Repository destination", "MANDATORY", "Campos propios del proveedor", "Provider-specific fields"),
          PR("URL base personalizada", "Custom base URL", "OPTIONAL", "Para instancias propias", "For self-hosted instances"), PR("Tamaño máximo de artefacto", "Maximum artifact size", "OPTIONAL", "Límite por transferencia", "Limit per transfer"), PR("Conectar cuenta", "Connect account", "MANDATORY", "Widget OAuth embebido", "Embedded OAuth widget")],
        io: { i: T("Datos de identidad del proveedor", "Provider identity data"), o: T("Conexión autenticada reutilizable", "Reusable authenticated connection") }, effect: T("Guardar, probar y eliminar; la credencial no vuelve al navegador.", "Save, test and delete; the credential never returns to the browser."),
        func: { rel: [T("1 perfil ↔ 0..1 conexión activa", "1 profile ↔ 0..1 active connection")], lifecycle: T("guardado → conectado", "saved → connected"), cond: T("Campos distintos según el proveedor.", "Different fields depending on the provider.") },
        tech: { type: "Perfil de conexión remota", schema: "provider + campos específicos", evidence: [EV(PRP, 4, "Proveedor")] } },
      { id: "auth", n: T("Autenticación", "Authentication"), kind: "variant-set", req: "MANDATORY",
        mean: T("Cómo HYDRA obtiene permiso sin ver tu contraseña.", "How HYDRA gets permission without seeing your password."), purpose: T("Conectar tu identidad con el proveedor de forma segura.", "Connect your identity to the provider securely."), when: T("Al pulsar «Conectar» por primera vez.", "When pressing \"Connect\" for the first time."), card: "1 perfil ↔ 0..1 conexión activa",
        variants: [V("OAuth con código de autorización y PKCE", "OAuth authorization code with PKCE", "Opción por defecto; ventana emergente al proveedor real", "Default option; popup to the real provider", "ALTERNATIVE"), V("OAuth Device Code", "OAuth Device Code", "Solo si el administrador lo habilita; para entornos sin navegador", "Only if the administrator enables it; for browserless environments", "CONDITIONAL"),
          V("Credencial gestionada de Git genérico", "Managed generic Git credential", "Usuario y contraseña HTTPS guardados en el servidor, nunca un token en claro", "HTTPS user and password kept on the server, never a plain token", "CONDITIONAL")],
        io: { i: T("Consentimiento en la página del proveedor", "Consent on the provider's page"), o: T("Credencial cifrada en una bóveda del servidor", "Credential encrypted in a server vault") }, effect: T("HYDRA solo recibe la confirmación; los tokens no llegan al navegador.", "HYDRA only receives the confirmation; tokens never reach the browser."),
        func: { rel: [T("Aplica a los 5 proveedores OAuth; Git genérico usa credencial gestionada", "Applies to the 5 OAuth providers; generic Git uses a managed credential")], lifecycle: T("sin conectar → conectado", "not connected → connected"), cond: T("Sin SSH ni token personal manual.", "No SSH or manual personal token.") },
        tech: { type: "RemoteAuthService.start()", schema: "OAuth code+PKCE / device code", evidence: [EV(PRA, 38, "RemoteAuthService.start()")] } },
      { id: "gitops", n: T("Operaciones Git", "Git operations"), kind: "variant-set", req: "CONDITIONAL",
        mean: T("Las acciones disponibles sobre un repositorio Git real.", "The actions available on a real Git repository."), purpose: T("Trabajar con ramas y commits sin salir de HYDRA.", "Work with branches and commits without leaving HYDRA."), when: T("Solo con GitHub, GitLab o Git genérico.", "Only with GitHub, GitLab or generic Git."), card: "1 perfil Git ↔ N operaciones",
        variants: [V("Estado", "Status", "Ver el estado del repositorio", "View the repository state", "OPTIONAL"), V("Inicializar", "Initialize", "Crear un repositorio", "Create a repository", "OPTIONAL"), V("Fetch", "Fetch", "Traer cambios sin fusionar", "Bring changes without merging", "OPTIONAL"), V("Pull", "Pull", "Traer y fusionar con la estrategia elegida", "Bring and merge with the chosen strategy", "OPTIONAL"), V("Push", "Push", "Enviar commits", "Send commits", "OPTIONAL"),
          V("Merge", "Merge", "Fusionar ramas", "Merge branches", "OPTIONAL"), V("Abortar fusión", "Abort merge", "Cancelar una fusión", "Cancel a merge", "OPTIONAL"), V("Commit de todo", "Commit all", "Confirmar todos los cambios", "Commit all changes", "OPTIONAL"), V("Crear rama", "Create branch", "Nueva rama", "New branch", "OPTIONAL"), V("Checkout", "Checkout", "Cambiar de rama", "Switch branch", "OPTIONAL")],
        props: [PR("Estrategia de pull", "Pull strategy", "OPTIONAL", "Por ejemplo solo avance rápido", "For example fast-forward only"), PR("Mensaje de commit", "Commit message", "CONDITIONAL", "Al hacer commit", "When committing"), PR("Rama", "Branch", "CONDITIONAL", "Al crear o cambiar de rama", "When creating or switching branches")],
        io: { i: T("Operación y parámetros", "Operation and parameters"), o: T("Árbol de ramas local y remoto, o un conflicto", "Local and remote branch tree, or a conflict") }, effect: T("Siempre vía un ejecutor aislado, sin shell ni comandos arbitrarios; sin force-push ni reset.", "Always through an isolated runner, no shell or arbitrary commands; no force-push or reset."),
        func: { rel: [T("Un conflicto abre el resolutor visual", "A conflict opens the visual resolver")], lifecycle: T("operación → resultado o conflicto", "operation → result or conflict"), cond: T("Rechazado para One Drive, Google Drive y Dropbox.", "Rejected for One Drive, Google Drive and Dropbox.") },
        tech: { type: "RemoteGitOperation", schema: "enum de 10 valores", evidence: [EV(PRG, 199, "Restricción de Git a 3 proveedores")] } },
      { id: "resolver", n: T("Resolutor visual de conflictos", "Visual conflict resolver"), kind: "concept", req: "CONDITIONAL",
        mean: T("Una vista de cuatro paneles: base, local, remoto y resultado.", "A four-pane view: base, local, remote and result."), purpose: T("Resolver una fusión de forma explícita.", "Resolve a merge explicitly."), when: T("Cuando una fusión produce conflicto.", "When a merge produces a conflict."), card: "1 conflicto ↔ 1 resolución",
        props: [PR("Preparar resolución", "Stage the resolution", "OPTIONAL", "Opción para dejarla preparada", "Option to leave it staged")],
        io: { i: T("Archivo en conflicto", "Conflicting file"), o: T("Resolución preparada", "Staged resolution") }, effect: T("Nada se resuelve en silencio.", "Nothing is resolved silently."),
        func: { rel: [T("Nace de merge o pull", "Arises from merge or pull")], lifecycle: T("conflicto → resuelto", "conflict → resolved"), cond: T("Solo con proveedores Git.", "Only with Git providers.") },
        tech: { type: "Visor de 4 paneles", schema: "—", evidence: [EV(PRG, 199, "Git")] } },
      { id: "transfer", n: T("Transferencia de archivos", "File transfer"), kind: "variant-set", req: "ALTERNATIVE",
        mean: T("Exportar o importar sin usar operaciones Git, válido para los 6 proveedores.", "Export or import without Git operations, valid for all 6 providers."), purpose: T("Respaldar o traer contenido de forma simple.", "Back up or bring in content simply."), when: T("Con cualquier proveedor.", "With any provider."), card: "1 transferencia ↔ 1 ruta exacta",
        variants: [V("Proyecto completo como ZIP", "Whole project as ZIP", "Solo rutas exactas; sin explorador remoto", "Exact paths only; no remote browser", "ALTERNATIVE"), V("Una capa individual", "A single layer", "Un único fichero con versión esperada", "A single file with expected version", "ALTERNATIVE")],
        props: [PR("Modo de escritura", "Write mode", "OPTIONAL", "Sobrescribir, solo crear o solo actualizar", "Overwrite, create only or update only"), PR("Versión esperada", "Expected version", "OPTIONAL", "Control de concurrencia optimista", "Optimistic concurrency control")],
        io: { i: T("Proyecto o capa", "Project or layer"), o: T("Archivo en el proveedor, o contenido restaurado", "File at the provider, or restored content") }, effect: T("Restauración simple de archivos; no activa la recuperación semántica.", "Simple file restore; does not activate semantic recovery."),
        func: { rel: [T("Usada por el proyecto para importar y exportar", "Used by the project to import and export")], lifecycle: T("subida o descarga", "upload or download"), cond: T("Sin navegación de carpetas.", "No folder browsing.") },
        tech: { type: "RemoteProjectTransferService", schema: "ZIP o capa + versión esperada", evidence: [EV(PRP, 4, "Proveedores")] } }
    ],
    map: {
      nodes: [
        { id: "provider", elem: "provider", n: T("Proveedor", "Provider"), k: "external", req: "MANDATORY", d: T("6 posibles", "6 possible") },
        { id: "gh", n: T("GitHub / GitLab / Git genérico", "GitHub / GitLab / generic Git"), k: "variant", req: "ALTERNATIVE", d: T("Con Git completo", "With full Git") },
        { id: "fs", n: T("One Drive / Google Drive / Dropbox", "One Drive / Google Drive / Dropbox"), k: "variant", req: "ALTERNATIVE", d: T("Solo archivos", "Files only") },
        { id: "profile", elem: "profile", n: T("Perfil de conexión", "Connection profile"), k: "concept", req: "MANDATORY", d: T("Identidad y destino", "Identity and destination") },
        { id: "auth", elem: "auth", n: T("Autenticación", "Authentication"), k: "concept", req: "MANDATORY", d: T("OAuth o credencial gestionada", "OAuth or managed credential") },
        { id: "vault", n: T("Bóveda del servidor", "Server vault"), k: "derived", req: "DERIVED", d: T("Credencial cifrada", "Encrypted credential") },
        { id: "gitops", elem: "gitops", n: T("Operaciones Git", "Git operations"), k: "concept", req: "CONDITIONAL", d: T("10 operaciones", "10 operations") },
        { id: "resolver", elem: "resolver", n: T("Resolutor de conflictos", "Conflict resolver"), k: "concept", req: "CONDITIONAL", d: T("4 paneles", "4 panes") },
        { id: "transfer", elem: "transfer", n: T("Transferencia de archivos", "File transfer"), k: "concept", req: "ALTERNATIVE", d: T("ZIP o capa", "ZIP or layer") }
      ],
      edges: [
        { a: "gh", b: "provider", rel: "isa", req: "ALTERNATIVE" }, { a: "fs", b: "provider", rel: "isa", req: "ALTERNATIVE" }, { a: "profile", b: "provider", rel: "ref", req: "MANDATORY" },
        { a: "profile", b: "auth", rel: "contains", card1: "1", card2: "0..1", req: "MANDATORY" }, { a: "auth", b: "vault", rel: "gen", req: "MANDATORY" },
        { a: "profile", b: "gitops", rel: "contains", card1: "1", card2: "N", req: "CONDITIONAL" }, { a: "gitops", b: "resolver", rel: "gen", req: "CONDITIONAL" }, { a: "profile", b: "transfer", rel: "contains", card1: "1", card2: "N", req: "ALTERNATIVE" }
      ]
    },
    define: [
      { id: "connection", n: T("Formulario «Repositorios»", "\"Repositories\" form"), kind: "wizard", req: "MANDATORY", elems: ["provider", "profile", "auth"],
        does: T("En la pantalla «Repositorios» eliges el asistente de 3 pasos o el modo avanzado de 3 secciones: identidad del perfil, destino del repositorio con los campos del proveedor, y compatibilidad y seguridad (URL base, tamaño máximo y «Conectar cuenta»).", "On the \"Repositories\" screen you choose the 3-step wizard or the 3-section advanced mode: profile identity, repository destination with the provider's fields, and compatibility and security (base URL, maximum size and \"Connect account\")."),
        func: T("Los campos cambian según el proveedor.", "Fields change depending on the provider."), tech: T("Asistente de 3 pasos o modo manual de 3 secciones con widget OAuth embebido.", "3-step wizard or 3-section manual mode with embedded OAuth widget."), proc: "repos_author_connection" },
      { id: "gitpanel", n: T("Panel de operaciones Git", "Git operations panel"), kind: "form", req: "CONDITIONAL", elems: ["gitops", "resolver"],
        does: T("Con un proveedor Git conectado ves el árbol de ramas local y remoto con su historial y usas los botones Fetch, Pull, Push, Commit, Rama, Checkout y Merge. Si hay conflicto se abre el visor de 4 paneles.", "With a connected Git provider you see the local and remote branch tree with its history and use the Fetch, Pull, Push, Commit, Branch, Checkout and Merge buttons. If there is a conflict the 4-pane viewer opens."),
        func: T("El panel solo se muestra con proveedores Git.", "The panel is only shown for Git providers."), tech: T("Panel Git + resolutor visual.", "Git panel + visual resolver."), proc: "repos_author_git" },
      { id: "transfer", n: T("Exportar e importar archivos", "Export and import files"), kind: "import", req: "ALTERNATIVE", elems: ["transfer"],
        does: T("Con cualquier proveedor exportas o importas el proyecto completo como ZIP, o subes y bajas una capa individual eligiendo el modo de escritura.", "With any provider you export or import the whole project as ZIP, or upload and download a single layer choosing the write mode."),
        func: T("Solo rutas exactas, con control de versión esperada.", "Exact paths only, with expected-version control."), tech: T("Servicio de transferencia de proyecto.", "Project transfer service."), proc: "repos_author_transfer" }
    ],
    rel: [
      { to: "project", type: "optional", dir: "out", u: T("Permite importar archivos y exportar selecciones del proyecto.", "Enables importing files and exporting project selections."), f: T("Restauración de archivo.", "File restore."), t: T("Export/import vía transferencia remota.", "Export/import via remote transfer.") },
      { to: "recovery", type: "alternative", dir: "out", u: T("No alimenta la recuperación externa.", "Does not feed external recovery."), f: T("Caminos separados.", "Separate paths."), t: T("La recuperación exige subida de archivo local.", "Recovery requires local file upload.") },
      { to: "security", type: "required", dir: "out", u: T("Las operaciones Git se ejecutan aisladas.", "Git operations run isolated."), f: T("Sin shell ni comandos arbitrarios.", "No shell or arbitrary commands."), t: T("Ejecutor de procesos externos aislado.", "Sandboxed external process runner.") }
    ]
  });

  HM.proc({ id: "repos_connect_sync", scope: "cap", cap: "external_repositories", elems: ["profile", "auth", "gitops", "transfer"],
    n: T("Conectar un repositorio y sincronizar", "Connect a repository and sync"), d: T("Perfil, autenticación y operaciones o transferencia.", "Profile, authentication and operations or transfer."),
    nodes: [
      N("s", "start", "user", null, "external_repositories", ["Quiero respaldar o traer contenido", "I want to back up or bring in content"], ["No hay perfil para ese proveedor todavía.", "There is no profile for that provider yet."]),
      N("a1", "step", "user", "MANDATORY", "external_repositories", ["Crear el perfil", "Create the profile"], ["Identidad y destino propio del proveedor.", "Identity and provider-specific destination."], { e: "profile", out: ["Perfil guardado", "Saved profile"] }),
      N("a2", "step", "ext", "MANDATORY", "external_repositories", ["Conectar la cuenta", "Connect the account"], ["Una ventana al proveedor real; HYDRA no ve la contraseña.", "A window to the real provider; HYDRA does not see the password."], { e: "auth", s: ["perfil: sin conectar → conectado", "profile: not connected → connected"], x: { rt: "RemoteAuthService", test: [], ev: [EV(PRA, 38, "start()")] } }),
      N("d1", "decision", "auto", "MANDATORY", "external_repositories", ["¿Proveedor Git?", "Git provider?"], ["Solo tres proveedores admiten operaciones Git.", "Only three providers support Git operations."]),
      N("a3", "step", "user", "CONDITIONAL", "external_repositories", ["Operar con Git", "Operate with Git"], ["Fetch, pull, push, commit, rama, checkout o merge.", "Fetch, pull, push, commit, branch, checkout or merge."], { e: "gitops", x: { rt: "RemoteGitWorkspaceService", test: [], ev: [EV(PRG, 199, "restricción Git")] } }),
      N("d2", "decision", "auto", "CONDITIONAL", "external_repositories", ["¿Conflicto de fusión?", "Merge conflict?"], ["Si lo hay se resuelve explícitamente.", "If so, it is resolved explicitly."]),
      N("a4", "step", "user", "CONDITIONAL", "external_repositories", ["Resolver en 4 paneles", "Resolve in 4 panes"], ["Base, local, remoto y resultado.", "Base, local, remote and result."], { e: "resolver", s: ["conflicto: abierto → resuelto", "conflict: open → resolved"] }),
      N("a5", "step", "user", "OPTIONAL", "external_repositories", ["Transferir archivos", "Transfer files"], ["Exportas o importas ZIP o una capa.", "You export or import ZIP or a layer."], { e: "transfer" }),
      N("r", "artifact", null, null, "external_repositories", ["Contenido sincronizado", "Synced content"], ["El proveedor y HYDRA tienen el mismo contenido, o el conflicto queda resuelto.", "The provider and HYDRA have the same content, or the conflict is resolved."]),
      N("e", "end", null, null, "external_repositories", ["Fin", "End"])
    ],
    edges: [E("s", "a1"), E("a1", "a2"), E("a2", "d1"), E("d1", "a3", "cond", "Sí", "Yes"), E("d1", "a5", "alt", "No", "No"), E("a3", "d2"), E("d2", "a4", "cond", "Sí", "Yes"), E("d2", "r", "next", "No", "No"), E("a4", "r"), E("a3", "a5", "opt"), E("a5", "r"), E("r", "e")] });

  HM.proc({ id: "repos_oauth_connect", scope: "cap", cap: "external_repositories", elems: ["auth"],
    n: T("Autenticar con OAuth", "Authenticate with OAuth"), d: T("Del botón «Conectar» a la credencial guardada en el servidor.", "From the \"Connect\" button to the credential stored on the server."),
    nodes: [
      N("s", "start", "user", null, "external_repositories", ["Pulsar «Conectar»", "Press \"Connect\""], ["Primera conexión del proveedor.", "First connection to the provider."]),
      N("d1", "decision", "auto", "MANDATORY", "external_repositories", ["¿Qué flujo?", "Which flow?"], ["Código con PKCE por defecto; Device Code solo si el administrador lo habilita; Git genérico usa credencial gestionada.", "Code with PKCE by default; Device Code only if the administrator enables it; generic Git uses a managed credential."], { e: "auth" }),
      N("a1", "step", "ext", "OPTIONAL", "external_repositories", ["Iniciar sesión en el proveedor", "Sign in at the provider"], ["Ventana emergente a la página real; no escribes la contraseña en HYDRA.", "A popup to the real page; you do not type the password in HYDRA."]),
      N("a2", "step", "ext", "CONDITIONAL", "external_repositories", ["Introducir el código en otro dispositivo", "Enter the code on another device"], ["Para entornos sin navegador.", "For browserless environments."]),
      N("a3", "step", "auto", "MANDATORY", "external_repositories", ["Intercambiar el código en el servidor", "Exchange the code on the server"], ["HYDRA recibe solo una confirmación y obtiene los tokens en el servidor.", "HYDRA only receives a confirmation and obtains the tokens on the server."], { x: { rt: "RemoteAuthService.start()", test: [], ev: [EV(PRA, 38, "start()")] } }),
      N("a4", "step", "auto", "MANDATORY", "external_repositories", ["Guardar cifrado", "Store encrypted"], ["La credencial vive en una bóveda del servidor y nunca vuelve al navegador.", "The credential lives in a server vault and never returns to the browser."], { s: ["conexión: pendiente → activa", "connection: pending → active"] }),
      N("r", "artifact", null, null, "external_repositories", ["Conexión autenticada", "Authenticated connection"], ["Lista para operar o transferir.", "Ready to operate or transfer."]),
      N("e", "end", null, null, "external_repositories", ["Fin", "End"])
    ],
    edges: [E("s", "d1"), E("d1", "a1", "next", "Código + PKCE", "Code + PKCE"), E("d1", "a2", "cond", "Device Code", "Device Code"), E("a1", "a3"), E("a2", "a3"), E("d1", "a4", "alt", "Git genérico", "Generic Git"), E("a3", "a4"), E("a4", "r"), E("r", "e")] });

  HM.proc({ id: "repos_file_transfer", scope: "cap", cap: "external_repositories", caps: ["project"], elems: ["transfer"],
    n: T("Transferir un proyecto o una capa", "Transfer a project or a layer"), d: T("Exportar o importar archivos con control de versión.", "Export or import files with version control."),
    nodes: [
      N("s", "start", "user", null, "external_repositories", ["Elegir exportar o importar", "Choose export or import"], ["Con una conexión activa.", "With an active connection."]),
      N("a", "decision", "user", "MANDATORY", "external_repositories", ["¿Proyecto o capa?", "Project or layer?"], ["Proyecto completo en ZIP o una sola capa.", "Whole project as ZIP or a single layer."], { e: "transfer" }),
      N("b", "step", "user", "OPTIONAL", "external_repositories", ["Elegir modo de escritura", "Choose write mode"], ["Sobrescribir, solo crear o solo actualizar.", "Overwrite, create only or update only."]),
      N("c", "step", "auto", "MANDATORY", "external_repositories", ["Leer o escribir la ruta exacta", "Read or write the exact path"], ["Se comprueba la versión esperada para no pisar cambios ajenos.", "The expected version is checked so others' changes are not overwritten."]),
      N("d", "decision", "auto", "MANDATORY", "external_repositories", ["¿Versión coincidente?", "Version matches?"], ["Si no coincide, se avisa.", "If it does not match, you are warned."]),
      N("r", "artifact", null, null, "project", ["Contenido en el proyecto o respaldado", "Content in the project or backed up"], ["Importar restaura archivos; exportar los deja en el proveedor.", "Import restores files; export leaves them at the provider."]),
      N("e", "end", null, null, "external_repositories", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b", "opt", "Capa", "Layer"), E("a", "c", "next", "Proyecto", "Project"), E("b", "c"), E("c", "d"), E("d", "r", "next", "Sí", "Yes"), E("d", "a", "loop", "No: revisar", "No: review"), E("r", "e")] });

  HM.proc({ id: "repos_author_connection", scope: "authoring", cap: "external_repositories", elems: ["provider", "profile", "auth"],
    n: T("Formulario «Repositorios»", "\"Repositories\" form"), d: T("Asistente guiado o modo avanzado.", "Guided wizard or advanced mode."),
    nodes: [
      N("s", "start", "user", null, "external_repositories", ["Abrir «Repositorios»", "Open \"Repositories\""], ["Pantalla de conexiones.", "Connections screen."]),
      N("a", "step", "user", "MANDATORY", "external_repositories", ["Elegir asistente o modo avanzado", "Choose wizard or advanced mode"], ["Los mismos tres bloques de datos, guiados o en secciones.", "The same three data blocks, guided or in sections."]),
      N("b", "step", "user", "MANDATORY", "external_repositories", ["1. Identidad del perfil", "1. Profile identity"], ["Nombre y proveedor (uno de los 6).", "Name and provider (one of 6)."], { e: "provider" }),
      N("c", "step", "user", "MANDATORY", "external_repositories", ["2. Destino del repositorio", "2. Repository destination"], ["Owner/repositorio, proyecto, URL y usuario, o unidad y carpeta según el proveedor.", "Owner/repository, project, URL and user, or drive and folder depending on the provider."], { e: "profile", f: ["Los campos cambian con el proveedor.", "Fields change with the provider."] }),
      N("d", "step", "user", "OPTIONAL", "external_repositories", ["3. Compatibilidad y seguridad", "3. Compatibility and security"], ["URL base personalizada y tamaño máximo de artefacto.", "Custom base URL and maximum artifact size."]),
      N("f", "step", "ext", "MANDATORY", "external_repositories", ["Conectar cuenta", "Connect account"], ["Widget OAuth embebido.", "Embedded OAuth widget."], { e: "auth" }),
      N("r", "artifact", null, null, "external_repositories", ["Perfil guardado y conectado", "Saved and connected profile"], ["Listo para usarse.", "Ready for use."]),
      N("e", "end", null, null, "external_repositories", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c"), E("c", "d", "opt"), E("c", "f"), E("d", "f"), E("f", "r"), E("r", "e")] });

  HM.proc({ id: "repos_author_git", scope: "authoring", cap: "external_repositories", elems: ["gitops", "resolver"],
    n: T("Panel de operaciones Git", "Git operations panel"), d: T("Operar y resolver conflictos.", "Operate and resolve conflicts."),
    nodes: [
      N("s", "start", "user", null, "external_repositories", ["Abrir el panel Git", "Open the Git panel"], ["Solo se muestra con proveedores Git.", "Only shown with Git providers."]),
      N("a", "step", "user", "MANDATORY", "external_repositories", ["Ver árbol de ramas", "View the branch tree"], ["Local y remoto con commits recientes.", "Local and remote with recent commits."], { e: "gitops" }),
      N("b", "decision", "user", "MANDATORY", "external_repositories", ["Elegir operación", "Choose operation"], ["Fetch, Pull, Push, Commit, Rama, Checkout o Merge.", "Fetch, Pull, Push, Commit, Branch, Checkout or Merge."]),
      N("c", "step", "user", "CONDITIONAL", "external_repositories", ["Indicar parámetros", "Provide parameters"], ["Rama, mensaje o estrategia de pull.", "Branch, message or pull strategy."]),
      N("d", "decision", "auto", "MANDATORY", "external_repositories", ["¿Conflicto?", "Conflict?"], ["Una fusión puede dejar archivos en conflicto.", "A merge can leave files in conflict."]),
      N("f", "step", "user", "CONDITIONAL", "external_repositories", ["Resolver en 4 paneles", "Resolve in 4 panes"], ["Base, local, remoto, resultado; puedes preparar la resolución.", "Base, local, remote, result; you can stage the resolution."], { e: "resolver" }),
      N("r", "artifact", null, null, "external_repositories", ["Repositorio actualizado", "Updated repository"], ["Estado nuevo en el árbol de ramas.", "New state in the branch tree."]),
      N("e", "end", null, null, "external_repositories", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c", "opt"), E("b", "d"), E("c", "d"), E("d", "f", "cond", "Sí", "Yes"), E("d", "r", "next", "No", "No"), E("f", "r"), E("r", "e")] });

  HM.proc({ id: "repos_author_transfer", scope: "authoring", cap: "external_repositories", elems: ["transfer"],
    n: T("Exportar e importar archivos", "Export and import files"), d: T("Transferencia con cualquier proveedor.", "Transfer with any provider."),
    nodes: [
      N("s", "start", "user", null, "external_repositories", ["Abrir la transferencia", "Open the transfer"], ["Con un perfil conectado.", "With a connected profile."]),
      N("a", "step", "user", "MANDATORY", "external_repositories", ["Elegir proyecto o capa", "Choose project or layer"], ["ZIP completo o un solo fichero.", "Whole ZIP or a single file."], { e: "transfer" }),
      N("b", "step", "user", "MANDATORY", "external_repositories", ["Indicar la ruta exacta", "Provide the exact path"], ["No hay explorador de carpetas remoto.", "There is no remote folder browser."]),
      N("c", "step", "user", "OPTIONAL", "external_repositories", ["Elegir modo de escritura", "Choose write mode"], ["Sobrescribir, solo crear o solo actualizar.", "Overwrite, create only or update only."]),
      N("d", "step", "auto", "MANDATORY", "external_repositories", ["Transferir", "Transfer"], ["Se sube o baja con control de versión esperada.", "Uploaded or downloaded with expected-version control."]),
      N("r", "artifact", null, null, "external_repositories", ["Archivo transferido", "Transferred file"], ["En el proveedor o en el proyecto.", "At the provider or in the project."]),
      N("e", "end", null, null, "external_repositories", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c", "opt"), E("b", "d"), E("c", "d"), E("d", "r"), E("r", "e")] });

  // =====================================================================================================
  // PORTABILITY
  // =====================================================================================================
  HM.cap("portability", {
    name: T("Portabilidad", "Capability packaging & portability"), group: "platform",
    user: {
      tagline: T("Lleva tus capacidades a otro proyecto", "Carry your capabilities to another project"),
      what: T("Empaqueta, fusiona y aplica de forma reversible las «capas de capacidad» de un proyecto (metamodelo, gramática, editor, validaciones y más) como un paquete portable.", "Packages, merges and applies reversibly the \"capability layers\" of a project (metamodel, grammar, editor, validations and more) as a portable package."),
      why: T("Mover o actualizar el conjunto de capacidades entre entornos con garantía de poder deshacer; no es una simple copia de archivos.", "Move or update the set of capabilities between environments with a guarantee of being able to undo; it is not a plain file copy."),
      when: T("Cuando quieres reutilizar, compartir o actualizar capacidades en otro proyecto.", "When you want to reuse, share or update capabilities in another project."),
      canDefine: [T("Qué capas incluir en el paquete", "Which layers to include in the package"), T("Qué hacer ante cada conflicto de fusión", "What to do about each merge conflict"), T("Confirmar la aplicación del plan", "Confirm applying the plan"), T("Revertir una aplicación", "Revert an application")],
      needs: [T("Un proyecto con capas de capacidad activas, o un paquete a importar", "A project with active capability layers, or a package to import")],
      produces: [T("Un archivo de paquete portable", "A portable package file"), T("Una vista previa de fusión con sus conflictos", "A merge preview with its conflicts"), T("Documentos modificados con registro de transacción", "Modified documents with a transaction log")],
      mandatory: [T("Decisión explícita por cada conflicto detectado", "An explicit decision for each detected conflict"), T("Aplicar el plan como transacción", "Apply the plan as a transaction")],
      optional: [T("Exportar un paquete", "Export a package")],
      conditional: [T("Fusión: solo al importar sobre capas ya existentes", "Merge: only when importing over already existing layers")],
      next: [T("Importar el paquete en otro proyecto", "Import the package in another project"), T("Revertir si algo sale mal", "Revert if something goes wrong")],
      interacts: [{ cap: "project", t: T("Amplía la preparación de capacidades del proyecto", "Extends the project's capability preparation") }, { cap: "metamodel", t: T("Las capas incluyen metamodelo y editor", "Layers include metamodel and editor") }]
    },
    func: {
      lifecycle: [T("capas elegidas → paquete exportado", "layers chosen → package exported"), T("paquete importado → conflictos detectados → vista previa → decisiones → aplicación", "package imported → conflicts detected → preview → decisions → application"), T("aplicada → revertida si algo falla", "applied → reverted if something fails")],
      semantics: [
        T("Cada conflicto se resuelve con una de cuatro decisiones: conservar lo existente, usar lo recuperado, fusionar u omitir.", "Each conflict is resolved with one of four decisions: keep existing, use recovered, merge or skip."),
        T("Nada se aplica hasta que se confirma el plan de fusión.", "Nothing is applied until the merge plan is confirmed."),
        T("La transacción tiene vista previa, aplicación y reversión, con un registro de solo añadir; la reversión deshace solo lo que esa transacción cambió, por versión de documento.", "The transaction has preview, apply and rollback, with an append-only journal; rollback undoes only what that transaction changed, by document version."),
        T("Los problemas de un paquete se informan con gravedad ERROR, WARNING o INFO.", "Package problems are reported with severity ERROR, WARNING or INFO."),
        T("Cada capacidad del paquete declara su fidelidad: exacta, semántica, estructural, parcial, solo proyección o no soportada, y un modo de ejecución embebido, remoto, híbrido o pasivo.", "Each capability in the package declares its fidelity: exact, semantic, structural, partial, projection only or unsupported, and an execution mode embedded, remote, hybrid or passive."),
        T("Existe un empaquetado de proyecto completo aparte, distinto del de capas de capacidad.", "A whole-project package exists separately, distinct from the capability-layer one.")],
      states: [{ n: T("Vista previa", "Preview"), d: T("Plan calculado, nada aplicado", "Plan computed, nothing applied") }, { n: T("Aplicada", "Applied"), d: T("Documentos modificados con registro", "Documents modified with a log") }, { n: T("Revertida", "Reverted"), d: T("Deshecha por versión", "Undone by version") }],
      cross: [T("Proyecto: es el backend del módulo «Preparación del proyecto».", "Project: it is the backend of the \"Project preparation\" module."), T("Persistencia: el rollback se apoya en el control de versión de cada documento.", "Persistence: rollback relies on each document's version control.")]
    },
    tech: {
      runtime: T("Servicios de archivo de paquete, fusión semántica y transacción de capa con journal append-only y rollback por ETag.", "Package archive, semantic merge and layer transaction services with append-only journal and ETag rollback."),
      services: ["CapabilityPackageArchiveService", "CapabilitySemanticMergeService", "CapabilityLayerTransactionService", "CapabilityOrchestrationService"],
      apis: [],
      schemas: ["CapabilitySemanticMergeDecision (KEEP_EXISTING, USE_RECOVERED, MERGE, SKIP)", "CapabilityPackageDiagnosticSeverity (ERROR, WARNING, INFO)", "CapabilityFidelity", "CapabilityExecutionMode", "HydraCapabilityKind", "CapabilityPackageArchiveException.Reason"],
      tests: [],
      evidence: [EV(PPK, 1, "CapabilityPackageArchiveService"), EV(PPM, 1, "CapabilitySemanticMergeService"), EV(PPT, 1, "CapabilityLayerTransactionService"), EV("src/main/java/com/example/editor/portability/CapabilityFidelity.java", 4, "CapabilityFidelity")],
      notes: [T("Motivos de fallo del archivo de paquete: DISABLED, INVALID_PACKAGE, INVALID_ARCHIVE, LIMIT_EXCEEDED, SIGNATURE_INVALID, IO_ERROR.", "Package archive failure reasons: DISABLED, INVALID_PACKAGE, INVALID_ARCHIVE, LIMIT_EXCEEDED, SIGNATURE_INVALID, IO_ERROR."),
        T("HydraCapabilityKind: se leyeron METAMODEL, GRAMMAR, TEXTUAL_EDITOR_DEFINITION, TEXTUAL_SYNTAX_STYLE, VISUAL_REPRESENTATION y PALETTE; la lectura de la lista se truncó y el resto no se confirmó.", "HydraCapabilityKind: METAMODEL, GRAMMAR, TEXTUAL_EDITOR_DEFINITION, TEXTUAL_SYNTAX_STYLE, VISUAL_REPRESENTATION and PALETTE were read; the list reading was truncated and the rest was not confirmed."),
        T("No se localizó en el código leído un enum de estados de transacción; los estados vista previa/aplicada/revertida provienen del vocabulario de preview/apply/rollback de los datos heredados.", "No transaction-state enum was found in the code read; the preview/applied/reverted states come from the preview/apply/rollback vocabulary of legacy data."),
        T("Los pasos exactos de la interfaz del diálogo de paquete no se verificaron: se describen según los datos heredados («selección de capas + exportación», «vista previa + decisión por conflicto»).", "The exact UI steps of the package dialog were not verified: they are described per legacy data (\"layer selection + export\", \"preview + per-conflict decision\")."),
        T("Tipos de conflicto de fusión: el código leído solo confirma las 4 decisiones; no se enumeraron los tipos de conflicto.", "Merge conflict kinds: the code read only confirms the 4 decisions; conflict kinds were not enumerated.")]
    },
    elements: [
      { id: "package", n: T("Paquete de capacidad", "Capability package"), kind: "concept", req: "OPTIONAL",
        mean: T("Un archivo portable que reúne varias capas con sus dependencias resueltas.", "A portable file that gathers several layers with their dependencies resolved."), purpose: T("Mover capacidades como una unidad versionada.", "Move capabilities as a versioned unit."), when: T("Para exportar o importar el conjunto de capacidades.", "To export or import the set of capabilities."), card: "1 proyecto ↔ N paquetes exportables",
        props: [PR("Capas incluidas", "Included layers", "MANDATORY", "Las que eliges exportar", "The ones you choose to export"), PR("Dependencias", "Dependencies", "DERIVED", "Se resuelven al empaquetar", "Resolved when packaging"), PR("Versión y compatibilidad", "Version and compatibility", "DERIVED", "Declaradas en el paquete", "Declared in the package"), PR("Firma", "Signature", "OPTIONAL", "Se comprueba al importar", "Checked on import")],
        variants: [V("Paquete inhabilitado", "Disabled", "La función está apagada", "The function is turned off", "ALTERNATIVE"), V("Paquete inválido", "Invalid package", "El contenido no es correcto", "The content is not correct", "ALTERNATIVE"), V("Archivo inválido", "Invalid archive", "El archivo está dañado", "The archive is damaged", "ALTERNATIVE"), V("Límite excedido", "Limit exceeded", "Supera un límite permitido", "Exceeds an allowed limit", "ALTERNATIVE"), V("Firma inválida", "Invalid signature", "La firma no coincide", "The signature does not match", "ALTERNATIVE"), V("Error de lectura o escritura", "Read/write error", "Fallo de disco", "Disk failure", "ALTERNATIVE")],
        io: { i: T("Capas seleccionadas", "Selected layers"), o: T("Archivo portable", "Portable file") }, effect: T("Las variantes son los seis motivos por los que un paquete puede rechazarse.", "The variants are the six reasons a package may be rejected."),
        func: { rel: [T("Se importa y fusiona en otro proyecto", "Imported and merged into another project")], lifecycle: T("exportado → importado", "exported → imported"), cond: T("Distinto del paquete de proyecto completo.", "Distinct from the whole-project package.") },
        tech: { type: "CapabilityPackageArchiveService", schema: "CapabilityPackageArchiveException.Reason", evidence: [EV(PPK, 1, "Paquete")] } },
      { id: "layer", n: T("Capa de capacidad", "Capability layer"), kind: "concept", req: "MANDATORY",
        mean: T("Una parte del proyecto que se puede empaquetar: metamodelo, gramática, editor, validaciones…", "A part of the project that can be packaged: metamodel, grammar, editor, validations…"), purpose: T("Ser la unidad de mover y fusionar.", "Be the unit of moving and merging."), when: T("Al elegir qué incluir.", "When choosing what to include."), card: "1 paquete ↔ 1..N capas",
        variants: [V("Metamodelo", "Metamodel", "Los tipos del lenguaje", "The language's types", "OPTIONAL"), V("Gramática", "Grammar", "La sintaxis textual", "The textual syntax", "OPTIONAL"), V("Definición de editor textual", "Textual editor definition", "Cómo se edita el texto", "How text is edited", "OPTIONAL"), V("Estilo de sintaxis textual", "Textual syntax style", "Colores y formato", "Colours and formatting", "OPTIONAL"), V("Representación visual", "Visual representation", "Cómo se dibuja", "How it is drawn", "OPTIONAL"), V("Paleta", "Palette", "Herramientas del editor visual", "Visual editor tools", "OPTIONAL")],
        props: [PR("Fidelidad", "Fidelity", "DERIVED", "Exacta, semántica, estructural, parcial, solo proyección o no soportada", "Exact, semantic, structural, partial, projection only or unsupported"), PR("Modo de ejecución", "Execution mode", "DERIVED", "Embebido, remoto, híbrido o pasivo", "Embedded, remote, hybrid or passive"), PR("Origen del metamodelo", "Metamodel origin", "DERIVED", "Propio, importado, externo o sincronizado", "Owned, imported, external or synchronized")],
        io: { i: T("Contenido del proyecto", "Project content"), o: T("Elemento del paquete", "Package item") }, effect: T("La lista de variantes es parcial: solo se confirmaron seis tipos.", "The variant list is partial: only six kinds were confirmed."),
        func: { rel: [T("Se resuelven dependencias entre capas", "Dependencies between layers are resolved")], lifecycle: T("activa → empaquetada → fusionada", "active → packaged → merged"), cond: T("Depende de las áreas activas.", "Depends on the active areas.") },
        tech: { type: "HydraCapabilityKind", schema: "CapabilityFidelity, CapabilityExecutionMode, MetamodelOriginKind", evidence: [EV("src/main/java/com/example/editor/portability/CapabilityFidelity.java", 4, "CapabilityFidelity")] } },
      { id: "merge", n: T("Fusión semántica", "Semantic merge"), kind: "concept", req: "CONDITIONAL",
        mean: T("Detecta conflictos entre dos paquetes y deja previsualizar y decidir.", "Detects conflicts between two packages and lets you preview and decide."), purpose: T("Evitar pisar capas existentes sin saberlo.", "Avoid overwriting existing layers unknowingly."), when: T("Al importar sobre capas que ya existen.", "When importing over layers that already exist."), card: "1 fusión ↔ N conflictos ↔ N decisiones",
        io: { i: T("Dos paquetes", "Two packages"), o: T("Vista previa y plan de aplicación", "Preview and application plan") }, effect: T("No aplica nada hasta que confirmas.", "Applies nothing until you confirm."),
        func: { rel: [T("Alimenta la transacción de capa", "Feeds the layer transaction")], lifecycle: T("detectada → previsualizada → decidida", "detected → previewed → decided"), cond: T("Solo cuando hay solapamiento.", "Only when there is overlap.") },
        tech: { type: "CapabilitySemanticMergeService", schema: "conflictos + decisiones", evidence: [EV(PPM, 1, "Fusión semántica")] } },
      { id: "decision", n: T("Decisión por conflicto", "Per-conflict decision"), kind: "variant-set", req: "MANDATORY",
        mean: T("Lo que eliges hacer con cada conflicto.", "What you choose to do with each conflict."), purpose: T("Que ninguna fusión sea implícita.", "Ensure no merge is implicit."), when: T("Para cada conflicto detectado.", "For each detected conflict."), card: "1 conflicto ↔ 1 decisión",
        variants: [V("Conservar lo existente", "Keep existing", "Mantiene lo que ya hay", "Keeps what is already there", "ALTERNATIVE"), V("Usar lo recuperado", "Use recovered", "Toma lo del paquete", "Takes the package's content", "ALTERNATIVE"), V("Fusionar", "Merge", "Combina ambos", "Combines both", "ALTERNATIVE"), V("Omitir", "Skip", "Deja el elemento sin tocar", "Leaves the item untouched", "ALTERNATIVE")],
        io: { i: T("Conflicto detectado", "Detected conflict"), o: T("Paso del plan", "Plan step") }, effect: T("Las decisiones forman el plan que se aplicará.", "The decisions form the plan to be applied."),
        func: { rel: [T("Una por cada conflicto", "One per conflict")], lifecycle: T("pendiente → decidida", "pending → decided"), cond: T("Obligatoria para cada conflicto.", "Mandatory for each conflict.") },
        tech: { type: "CapabilitySemanticMergeDecision", schema: "enum de 4 valores", evidence: [EV(PPM, 1, "Decisiones")] } },
      { id: "diag", n: T("Problemas del paquete", "Package problems"), kind: "variant-set", req: "DERIVED",
        mean: T("Avisos sobre el contenido de un paquete, con una gravedad.", "Notices about a package's content, with a severity."), purpose: T("Saber si puedes importar con seguridad.", "Know whether you can import safely."), when: T("Al revisar un paquete.", "When reviewing a package."), card: "1 paquete ↔ 0..N problemas",
        variants: [V("Error", "Error", "Gravedad más alta", "Highest severity", "ALTERNATIVE"), V("Aviso", "Warning", "Merece atención", "Deserves attention", "ALTERNATIVE"), V("Información", "Info", "Solo informa", "Informs only", "ALTERNATIVE")],
        io: { i: T("Paquete", "Package"), o: T("Lista de problemas", "List of problems") }, effect: T("Orienta la decisión de importar.", "Guides the decision to import."),
        func: { rel: [T("Se asocia a un paquete", "Linked to a package")], lifecycle: T("generados al revisar", "generated on review"), cond: T("Tres gravedades.", "Three severities.") },
        tech: { type: "CapabilityPackageDiagnosticSeverity", schema: "ERROR / WARNING / INFO", evidence: [EV("src/main/java/com/example/editor/portability/CapabilityPackageDiagnosticSeverity.java", 3, "Gravedad")] } },
      { id: "transaction", n: T("Transacción de capa", "Layer transaction"), kind: "concept", req: "MANDATORY",
        mean: T("La aplicación del plan como un todo: vista previa, aplicar y revertir.", "Applying the plan as a whole: preview, apply and revert."), purpose: T("Poder deshacer exactamente lo que cambió.", "Be able to undo exactly what changed."), when: T("Al aplicar de verdad un paquete.", "When really applying a package."), card: "1 transacción ↔ N documentos, cada uno con su versión",
        variants: [V("Vista previa", "Preview", "Calcula sin cambiar", "Computes without changing", "MANDATORY"), V("Aplicar", "Apply", "Modifica los documentos", "Modifies the documents", "MANDATORY"), V("Revertir", "Rollback", "Deshace lo que esa transacción cambió", "Undoes what that transaction changed", "OPTIONAL")],
        io: { i: T("Plan de pasos", "Step plan"), o: T("Documentos modificados y registro", "Modified documents and log") }, effect: T("Si falla a mitad, se deshace solo lo cambiado por la transacción.", "If it fails midway, only what the transaction changed is undone."),
        func: { rel: [T("Escribe un registro de solo añadir", "Writes an append-only journal")], lifecycle: T("vista previa → aplicada → (revertida)", "preview → applied → (rolled back)"), cond: T("Se apoya en la versión de cada documento.", "Relies on each document's version.") },
        tech: { type: "CapabilityLayerTransactionService", schema: "preview/apply/rollback + journal", evidence: [EV(PPT, 1, "Transacción de capa")] } },
      { id: "journal", n: T("Registro de transacción", "Transaction journal"), kind: "derived", req: "DERIVED",
        mean: T("Un historial de solo añadir de cada transacción.", "An append-only history of each transaction."), purpose: T("Revertir y auditar.", "Revert and audit."), when: T("Se genera al aplicar.", "Generated on apply."), card: "1 transacción ↔ 1 registro",
        io: { i: T("Cambios aplicados", "Applied changes"), o: T("Historial para revertir", "History for reverting") }, effect: T("Permite el rollback por versión.", "Enables rollback by version."),
        func: { rel: [T("Pertenece a una transacción", "Belongs to a transaction")], lifecycle: T("solo crece", "only grows"), cond: T("Nunca se reescribe.", "Never rewritten.") },
        tech: { type: "Journal append-only", schema: "ETag por documento", evidence: [EV(PPT, 1, "Journal")] } }
    ],
    map: {
      nodes: [
        { id: "layer", elem: "layer", n: T("Capa de capacidad", "Capability layer"), k: "concept", req: "MANDATORY", d: T("Unidad que se mueve", "Unit that is moved") },
        { id: "mm", n: T("Metamodelo", "Metamodel"), k: "variant", req: "OPTIONAL", d: T("Una clase de capa", "A kind of layer") },
        { id: "gr", n: T("Gramática", "Grammar"), k: "variant", req: "OPTIONAL", d: T("Una clase de capa", "A kind of layer") },
        { id: "package", elem: "package", n: T("Paquete", "Package"), k: "concept", req: "OPTIONAL", d: T("Archivo portable", "Portable file") },
        { id: "diag", elem: "diag", n: T("Problemas", "Problems"), k: "derived", req: "DERIVED", d: T("Con gravedad", "With severity") },
        { id: "merge", elem: "merge", n: T("Fusión", "Merge"), k: "concept", req: "CONDITIONAL", d: T("Conflictos", "Conflicts") },
        { id: "decision", elem: "decision", n: T("Decisión", "Decision"), k: "concept", req: "MANDATORY", d: T("Conservar, usar, fusionar, omitir", "Keep, use, merge, skip") },
        { id: "tx", elem: "transaction", n: T("Transacción", "Transaction"), k: "concept", req: "MANDATORY", d: T("Vista previa, aplicar, revertir", "Preview, apply, rollback") },
        { id: "journal", elem: "journal", n: T("Registro", "Journal"), k: "derived", req: "DERIVED", d: T("Solo añadir", "Append only") },
        { id: "target", n: T("Proyecto destino", "Target project"), k: "external", req: "MANDATORY", d: T("Donde se aplica", "Where it is applied") }
      ],
      edges: [
        { a: "mm", b: "layer", rel: "isa", req: "OPTIONAL" }, { a: "gr", b: "layer", rel: "isa", req: "OPTIONAL" }, { a: "package", b: "layer", rel: "contains", card1: "1", card2: "1..N", req: "MANDATORY" },
        { a: "package", b: "diag", rel: "gen", req: "OPTIONAL" }, { a: "package", b: "merge", rel: "ref", req: "CONDITIONAL" }, { a: "merge", b: "decision", rel: "contains", card1: "1", card2: "N", req: "MANDATORY" },
        { a: "decision", b: "tx", rel: "ref", req: "MANDATORY" }, { a: "tx", b: "journal", rel: "gen", req: "MANDATORY" }, { a: "tx", b: "target", rel: "ref", req: "MANDATORY" }
      ]
    },
    define: [
      { id: "export", n: T("Selección de capas y exportación", "Layer selection and export"), kind: "form", req: "OPTIONAL", elems: ["package", "layer"],
        does: T("En el módulo «Preparación del proyecto» eliges qué capas incluir y generas el archivo portable; las dependencias entre capas se resuelven al empaquetar.", "In the \"Project preparation\" module you choose which layers to include and generate the portable file; dependencies between layers are resolved when packaging."),
        func: T("El archivo puede importarse en otro proyecto.", "The file can be imported in another project."), tech: T("Detalle de pantalla tomado de datos heredados; no verificado en componentes.", "Screen detail taken from legacy data; not verified in components."), proc: "portability_author_export" },
      { id: "merge", n: T("Vista previa de fusión y decisión por conflicto", "Merge preview and per-conflict decision"), kind: "form", req: "CONDITIONAL", elems: ["merge", "decision", "transaction"],
        does: T("Al importar sobre capas existentes previsualizas la fusión, decides para cada conflicto (conservar, usar lo recuperado, fusionar u omitir) y aplicas el plan como transacción, que podrás revertir.", "When importing over existing layers you preview the merge, decide for each conflict (keep, use recovered, merge or skip) and apply the plan as a transaction, which you can revert."),
        func: T("Nada se aplica sin confirmación explícita.", "Nothing is applied without explicit confirmation."), tech: T("Detalle de pantalla tomado de datos heredados; no verificado en componentes.", "Screen detail taken from legacy data; not verified in components."), proc: "portability_author_merge" }
    ],
    rel: [
      { to: "project", type: "optional", dir: "in", u: T("Es la ampliación de la preparación de capacidades del proyecto.", "It is the extension of the project's capability preparation."), f: T("Módulo «Preparación del proyecto».", "\"Project preparation\" module."), t: T("Backend del módulo de navegación CAPABILITIES.", "Backend of the CAPABILITIES navigation module.") },
      { to: "persistence", type: "required", dir: "out", u: T("La reversión se apoya en la versión de cada documento.", "Rollback relies on each document's version."), f: T("Cada documento afectado tiene su propia versión.", "Each affected document has its own version."), t: T("Rollback dirigido por ETag.", "ETag-driven rollback.") },
      { to: "metamodel", type: "optional", dir: "out", u: T("Las capas incluyen el metamodelo.", "Layers include the metamodel."), f: T("Una de las clases de capa.", "One of the layer kinds."), t: T("HydraCapabilityKind.METAMODEL.", "HydraCapabilityKind.METAMODEL.") }
    ]
  });

  HM.proc({ id: "portability_package_merge", scope: "cap", cap: "portability", elems: ["package", "merge", "decision", "transaction"],
    n: T("Empaquetar, fusionar y aplicar con reversión", "Package, merge and apply with rollback"), d: T("Del paquete exportado a su aplicación transaccional reversible.", "From the exported package to its reversible transactional application."),
    nodes: [
      N("s", "start", "user", null, "portability", ["Quiero mover capacidades", "I want to move capabilities"], ["Hay capas que exportar o un paquete que importar.", "There are layers to export or a package to import."]),
      N("d0", "decision", "user", "MANDATORY", "portability", ["¿Exportar o importar?", "Export or import?"], ["Son dos mitades del mismo recorrido.", "They are two halves of the same journey."]),
      N("a1", "step", "user", "OPTIONAL", "portability", ["Elegir capas y exportar", "Choose layers and export"], ["Las dependencias entre capas se resuelven al empaquetar.", "Dependencies between layers are resolved when packaging."], { e: "package", out: ["Archivo de paquete", "Package file"], x: { rt: "CapabilityPackageArchiveService", test: [], ev: [EV(PPK, 1, "empaquetado")] } }),
      N("a2", "step", "auto", "CONDITIONAL", "portability", ["Detectar conflictos", "Detect conflicts"], ["Se compara con las capas existentes.", "It is compared with the existing layers."], { e: "merge", x: { rt: "CapabilitySemanticMergeService", test: [], ev: [EV(PPM, 1, "detección")] } }),
      N("a3", "step", "auto", "CONDITIONAL", "portability", ["Previsualizar la fusión", "Preview the merge"], ["Nada se aplica todavía.", "Nothing is applied yet."]),
      N("a4", "step", "user", "MANDATORY", "portability", ["Decidir por conflicto", "Decide per conflict"], ["Conservar, usar lo recuperado, fusionar u omitir.", "Keep, use recovered, merge or skip."], { e: "decision", f: ["Cada conflicto necesita una decisión.", "Each conflict needs a decision."] }),
      N("d1", "decision", "user", "MANDATORY", "portability", ["¿Confirmar el plan?", "Confirm the plan?"], ["Solo entonces se aplica.", "Only then is it applied."]),
      N("a5", "step", "auto", "MANDATORY", "portability", ["Aplicar como transacción", "Apply as a transaction"], ["Se modifican los documentos con un registro de solo añadir.", "Documents are modified with an append-only journal."], { e: "transaction", s: ["transacción: vista previa → aplicada", "transaction: preview → applied"], x: { rt: "CapabilityLayerTransactionService", test: [], ev: [EV(PPT, 1, "preview/apply/rollback")] } }),
      N("d2", "decision", "user", "OPTIONAL", "portability", ["¿Hay que revertir?", "Must it be reverted?"], ["Si algo falla después, se puede deshacer.", "If something fails afterwards it can be undone."]),
      N("a6", "step", "auto", "CONDITIONAL", "portability", ["Revertir", "Roll back"], ["Se deshace solo lo que esa transacción cambió, por versión de documento.", "Only what that transaction changed is undone, by document version."], { s: ["transacción: aplicada → revertida", "transaction: applied → rolled back"] }),
      N("r", "artifact", null, null, "portability", ["Capas aplicadas con registro", "Layers applied with journal"], ["Resultado reversible.", "Reversible result."]),
      N("e", "end", null, null, "portability", ["Fin", "End"])
    ],
    edges: [E("s", "d0"), E("d0", "a1", "alt", "Exportar", "Export"), E("d0", "a2", "alt", "Importar", "Import"), E("a1", "e"), E("a2", "a3"), E("a3", "a4"), E("a4", "d1"), E("d1", "a5", "next", "Sí", "Yes"), E("d1", "a4", "loop", "No: ajustar", "No: adjust"), E("a5", "d2"), E("d2", "a6", "cond", "Sí", "Yes"), E("d2", "r", "next", "No", "No"), E("a6", "e"), E("r", "e")] });

  HM.proc({ id: "portability_revert", scope: "cap", cap: "portability", elems: ["transaction", "journal"],
    n: T("Revertir una aplicación", "Revert an application"), d: T("La reversión dirigida desde el registro.", "Targeted reversal from the journal."),
    nodes: [
      N("s", "start", "user", null, "portability", ["Detectar un problema", "Detect a problem"], ["Tras aplicar un paquete algo no es como esperabas.", "After applying a package something is not as expected."]),
      N("a", "step", "auto", "MANDATORY", "portability", ["Leer el registro", "Read the journal"], ["Se identifican los documentos afectados con sus versiones.", "The affected documents are identified with their versions."], { e: "journal" }),
      N("b", "decision", "auto", "MANDATORY", "portability", ["¿Siguen en la versión aplicada?", "Still at the applied version?"], ["Si otro cambio los modificó, la reversión lo detecta.", "If another change modified them, the rollback detects it."]),
      N("c", "step", "auto", "MANDATORY", "portability", ["Deshacer solo lo cambiado", "Undo only what changed"], ["No restaura una copia completa.", "It does not restore a full copy."], { e: "transaction", s: ["transacción: aplicada → revertida", "transaction: applied → rolled back"] }),
      N("r", "artifact", null, null, "portability", ["Proyecto restaurado", "Project restored"], ["Como antes de aplicar.", "As before applying."]),
      N("x", "end", null, null, "portability", ["Reversión rechazada", "Rollback refused"], ["Hay cambios posteriores sobre esos documentos.", "There are later changes on those documents."]),
      N("e", "end", null, null, "portability", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c", "next", "Sí", "Yes"), E("b", "x", "alt", "No", "No"), E("c", "r"), E("r", "e")] });

  HM.proc({ id: "portability_author_export", scope: "authoring", cap: "portability", elems: ["package", "layer"],
    n: T("Selección de capas y exportación", "Layer selection and export"), d: T("Del módulo «Preparación del proyecto» al archivo portable.", "From the \"Project preparation\" module to the portable file."),
    nodes: [
      N("s", "start", "user", null, "portability", ["Abrir «Preparación del proyecto»", "Open \"Project preparation\""], ["Módulo de navegación de capacidades.", "Capabilities navigation module."]),
      N("a", "step", "user", "MANDATORY", "portability", ["Elegir las capas", "Choose the layers"], ["Metamodelo, gramática, editor, validaciones, etc.", "Metamodel, grammar, editor, validations, etc."], { e: "layer" }),
      N("b", "step", "auto", "MANDATORY", "portability", ["Resolver dependencias", "Resolve dependencies"], ["Se añaden las capas necesarias.", "Needed layers are added."]),
      N("c", "step", "user", "MANDATORY", "portability", ["Exportar", "Export"], ["Se genera el archivo.", "The file is generated."], { e: "package" }),
      N("r", "artifact", null, null, "portability", ["Archivo de paquete", "Package file"], ["Portable y versionado.", "Portable and versioned."]),
      N("e", "end", null, null, "portability", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c"), E("c", "r"), E("r", "e")] });

  HM.proc({ id: "portability_author_merge", scope: "authoring", cap: "portability", elems: ["merge", "decision", "transaction"],
    n: T("Vista previa de fusión y aplicación", "Merge preview and application"), d: T("Importar un paquete sobre capas existentes.", "Import a package over existing layers."),
    nodes: [
      N("s", "start", "user", null, "portability", ["Importar un paquete", "Import a package"], ["Indicas el archivo del paquete.", "You point to the package file."]),
      N("a", "step", "auto", "MANDATORY", "portability", ["Revisar el paquete", "Review the package"], ["Se comprueba y se informan problemas con gravedad.", "It is checked and problems are reported with severity."]),
      N("b", "decision", "auto", "MANDATORY", "portability", ["¿Hay conflictos?", "Any conflicts?"], ["Solo si el paquete choca con capas existentes.", "Only if the package clashes with existing layers."]),
      N("c", "step", "user", "CONDITIONAL", "portability", ["Previsualizar y decidir cada conflicto", "Preview and decide each conflict"], ["Conservar, usar lo recuperado, fusionar u omitir.", "Keep, use recovered, merge or skip."], { e: "decision" }),
      N("d", "step", "user", "MANDATORY", "portability", ["Confirmar y aplicar", "Confirm and apply"], ["Se ejecuta como transacción.", "It runs as a transaction."], { e: "transaction" }),
      N("r", "artifact", null, null, "portability", ["Capas aplicadas", "Layers applied"], ["Con registro y reversión disponibles.", "With journal and rollback available."]),
      N("e", "end", null, null, "portability", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c", "cond", "Sí", "Yes"), E("b", "d", "next", "No", "No"), E("c", "d"), E("d", "r"), E("r", "e")] });

  // =====================================================================================================
  // INFRASTRUCTURE: persistence, audit, security (automatic; nothing is authored)
  // =====================================================================================================
  HM.cap("persistence", { noMicro: true,
    name: T("Persistencia canónica", "Canonical persistence"), group: "platform", noVariants: true,
    user: {
      tagline: T("Guarda todo tu trabajo sin base de datos", "Saves all your work without a database"),
      what: T("Guarda automáticamente todo lo que creas, cada cosa como su propio documento, en archivos JSON del disco local.", "Automatically saves everything you create, each as its own document, in JSON files on the local disk."),
      why: T("Si dos cambios chocan se detecta en vez de perderse en silencio, y no necesitas instalar ni mantener una base de datos.", "If two changes clash it is detected instead of silently lost, and you do not need to install or maintain a database."),
      when: T("Cada vez que guardas cualquier cosa en HYDRA.", "Every time you save anything in HYDRA."),
      canDefine: [T("Nada: es automático", "Nothing: it is automatic")],
      needs: [T("Un documento que guardar", "A document to save"), T("Espacio en el disco local", "Space on the local disk")],
      produces: [T("Un documento JSON por cada elemento guardado", "One JSON document per saved item"), T("Una versión nueva en cada guardado", "A new version on every save"), T("Un aviso si hay un choque de versiones", "A warning if versions clash")],
      mandatory: [], optional: [], conditional: [],
      next: [T("Seguir editando", "Keep editing"), T("Revisar el registro de auditoría", "Review the audit record")],
      interacts: [{ cap: "audit", t: T("Cada guardado alimenta la auditoría", "Every save feeds the audit") }, { cap: "portability", t: T("Revertir se apoya en la versión de cada documento", "Reverting relies on each document's version") }]
    },
    func: {
      lifecycle: [T("documento leído → modificado → guardado con comprobación de versión", "document read → modified → saved with a version check")],
      semantics: [T("Cada documento canónico se guarda tal y como lo define su propia capacidad.", "Each canonical document is saved exactly as its own capability defines it."), T("El control de versiones es optimista: si el documento cambió desde que lo leíste, el guardado se rechaza.", "Version control is optimistic: if the document changed since you read it, the save is rejected."),
        T("No tiene esquema de dominio propio: respeta el de cada capacidad.", "It has no domain schema of its own: it honors each capability's."), T("Todo vive en archivos JSON locales; no usa base de datos.", "Everything lives in local JSON files; no database is used.")],
      states: [{ n: T("Guardado", "Saved"), d: T("Versión actual del documento", "Current document version") }, { n: T("Choque de versiones", "Version clash"), d: T("El guardado se rechaza", "The save is rejected") }],
      cross: [T("Auditoría: cada guardado se anota automáticamente.", "Audit: every save is noted automatically."), T("Portabilidad: la reversión por versión depende de este control.", "Portability: rollback by version depends on this control.")]
    },
    tech: {
      runtime: T("Almacén de documentos canónicos con fábrica local y comprobación optimista por ETag.", "Canonical document store with a local factory and optimistic ETag check."),
      services: ["CanonicalDocumentStore", "LocalCanonicalDocumentStoreFactory"], apis: [], schemas: ["Documento JSON canónico + ETag"], tests: [],
      evidence: [EV(PCS, 15, "CanonicalDocumentStore"), EV(PCF, 14, "LocalCanonicalDocumentStoreFactory")],
      notes: [T("Sin esquema de dominio propio; la capa no modela contenido.", "No domain schema of its own; the layer models no content."), T("La fábrica local conecta la auditoría universal a cada almacén (dato heredado de la capacidad de proyecto).", "The local factory attaches the universal audit to each store (legacy fact from the project capability).")]
    },
    elements: [
      { id: "document", n: T("Documento canónico", "Canonical document"), kind: "concept", req: "MANDATORY", mean: T("Cada cosa que guardas (metamodelo, modelo, gramática…) como un documento propio.", "Each thing you save (metamodel, model, grammar…) as its own document."), purpose: T("Guardar el trabajo de forma simple y local.", "Save work in a simple, local way."), when: T("En cada guardado.", "On every save."), card: "1 elemento guardado ↔ 1 documento",
        io: { i: T("Contenido definido por cada capacidad", "Content defined by each capability"), o: T("Archivo JSON con versión", "JSON file with version") }, effect: T("Se escribe en el disco local.", "Written to the local disk."),
        func: { rel: [T("Cada documento tiene su propia versión", "Each document has its own version")], lifecycle: T("leído → modificado → guardado", "read → modified → saved"), cond: T("Sin base de datos.", "No database.") }, tech: { type: "CanonicalDocumentStore", schema: "JSON + ETag", evidence: [EV(PCS, 15, "Almacén")] } },
      { id: "version", n: T("Control de versión optimista", "Optimistic version control"), kind: "concept", req: "MANDATORY", mean: T("Una marca de versión que cambia en cada guardado.", "A version mark that changes on every save."), purpose: T("Detectar choques en vez de perder cambios.", "Detect clashes instead of losing changes."), when: T("Al guardar.", "When saving."), card: "1 documento ↔ 1 versión vigente",
        io: { i: T("Versión leída", "Version read"), o: T("Guardado aceptado o choque", "Save accepted or clash") }, effect: T("Un choque rechaza el guardado, no pierde nada en silencio.", "A clash rejects the save and loses nothing silently."),
        func: { rel: [T("Usada también por la reversión de otras capacidades", "Also used by other capabilities' rollback")], lifecycle: T("nueva versión en cada guardado", "new version on each save"), cond: T("Solo coincide si nadie cambió el documento.", "Matches only if nobody changed the document.") }, tech: { type: "ETag", schema: "versión por documento", evidence: [EV(PCS, 15, "ETag")] } },
      { id: "store", n: T("Almacén local", "Local store"), kind: "derived", req: "DERIVED", mean: T("El conjunto de archivos JSON en el disco.", "The set of JSON files on the disk."), purpose: T("Ser el lugar común donde vive todo.", "Be the common place where everything lives."), when: T("Siempre.", "Always."), card: "1 proyecto ↔ 1 almacén",
        io: { i: T("Documentos", "Documents"), o: T("Archivos en disco", "Files on disk") }, effect: T("No requiere configuración.", "Needs no configuration."),
        func: { rel: [T("Conectado a la auditoría", "Attached to the audit")], lifecycle: T("Siempre disponible.", "Always available."), cond: T("Local, no remoto.", "Local, not remote.") }, tech: { type: "LocalCanonicalDocumentStoreFactory", schema: "—", evidence: [EV(PCF, 14, "Fábrica")] } }
    ],
    map: { nodes: [{ id: "store", elem: "store", n: T("Almacén local", "Local store"), k: "derived", req: "DERIVED", d: T("Archivos JSON", "JSON files") }, { id: "doc", elem: "document", n: T("Documento", "Document"), k: "concept", req: "MANDATORY", d: T("Un elemento guardado", "A saved item") }, { id: "ver", elem: "version", n: T("Versión", "Version"), k: "concept", req: "MANDATORY", d: T("Comprobación optimista", "Optimistic check") }],
      edges: [{ a: "store", b: "doc", rel: "contains", card1: "1", card2: "0..N", req: "MANDATORY" }, { a: "doc", b: "ver", rel: "contains", card1: "1", card2: "1", req: "MANDATORY" }] },
    define: [{ id: "auto", n: T("Automático", "Automatic"), kind: "generated", req: "MANDATORY", elems: ["document", "version", "store"], does: T("No hay nada que crear ni configurar: cada vez que guardas algo, HYDRA lo escribe como un documento y comprueba la versión por ti.", "There is nothing to create or configure: every time you save something, HYDRA writes it as a document and checks the version for you."), func: T("Ocurre en cada guardado de cualquier capacidad.", "Happens on every save of any capability."), tech: T("Fábrica local de almacenes de documentos.", "Local document store factory.") }],
    rel: [{ to: "audit", type: "event", dir: "out", u: T("Cada guardado se anota en la auditoría.", "Every save is noted in the audit."), f: T("Siempre, sin activarlo.", "Always, nothing to turn on."), t: T("Observador de mutaciones por almacén.", "Mutation observer per store.") },
      { to: "portability", type: "producer", dir: "out", u: T("Ofrece la versión de cada documento para poder revertir.", "Offers each document's version so changes can be reverted."), f: T("Rollback por versión.", "Rollback by version."), t: T("ETag por documento.", "ETag per document.") }]
  });

  HM.proc({ id: "persistence_save_etag", scope: "cap", cap: "persistence", caps: ["audit"], elems: ["document", "version"],
    n: T("Guardar con comprobación de versión", "Save with a version check"), d: T("Cómo se evita perder un cambio ajeno.", "How losing someone else's change is avoided."),
    nodes: [
      N("s", "start", "auto", null, "persistence", ["Una capacidad guarda un documento", "A capability saves a document"], ["Cualquier capacidad que guarda trabajo pasa por aquí.", "Any capability that saves work goes through here."]),
      N("a", "step", "auto", "MANDATORY", "persistence", ["Leer la versión actual", "Read the current version"], ["Se compara con la versión con la que se empezó a editar.", "It is compared with the version editing started from."], { e: "version", x: { rt: "CanonicalDocumentStore", test: [], ev: [EV(PCS, 15, "ETag")] } }),
      N("d", "decision", "auto", "MANDATORY", "persistence", ["¿Coinciden?", "Do they match?"], ["Si coinciden, nadie cambió el documento mientras tanto.", "If they match, nobody changed the document in the meantime."]),
      N("b", "step", "auto", "MANDATORY", "persistence", ["Escribir el documento JSON", "Write the JSON document"], ["Se guarda y se crea una versión nueva.", "It is saved and a new version is created."], { e: "document", s: ["documento: versión n → versión n+1", "document: version n → version n+1"] }),
      N("c", "step", "auto", "MANDATORY", "audit", ["Anotar en la auditoría", "Note in the audit"], ["El guardado queda registrado.", "The save is recorded."]),
      N("x", "step", "auto", "CONDITIONAL", "persistence", ["Rechazar por choque de versiones", "Reject on version clash"], ["No se pierde nada en silencio.", "Nothing is lost silently."]),
      N("r", "artifact", null, null, "persistence", ["Documento guardado", "Saved document"], ["Versión nueva y registro anotado.", "New version and record noted."]),
      N("e", "end", null, null, "persistence", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "d"), E("d", "b", "next", "Sí", "Yes"), E("d", "x", "alt", "No", "No"), E("b", "c"), E("c", "r"), E("r", "e"), E("x", "e")] });

  HM.cap("audit", { noMicro: true,
    name: T("Auditoría", "Audit"), group: "platform", noVariants: true,
    user: {
      tagline: T("Un registro de lo que ha ocurrido", "A record of what has happened"),
      what: T("Lleva un registro de solo añadir de quién cambió qué y cuándo.", "Keeps an append-only record of who changed what and when."),
      why: T("Sirve para depurar problemas y para demostrar de dónde viene un resultado.", "It serves to debug problems and to prove where a result came from."),
      when: T("Siempre, sin que tengas que activarlo.", "Always, without you having to turn it on."),
      canDefine: [T("Nada: es automático", "Nothing: it is automatic")],
      needs: [T("Que se guarde algún cambio", "That some change is saved")],
      produces: [T("Una entrada por cada cambio de documento", "One entry per document change"), T("Evidencia del origen de un resultado", "Evidence of where a result came from")],
      mandatory: [], optional: [], conditional: [],
      next: [T("Consultar el historial al depurar", "Consult the history when debugging")],
      interacts: [{ cap: "persistence", t: T("Se alimenta de cada guardado", "Fed by every save") }, { cap: "events", t: T("Es independiente de Eventos, que es opcional", "Independent of Events, which is optional") }]
    },
    func: {
      lifecycle: [T("cambio guardado → entrada anotada (solo añadir)", "change saved → entry noted (append only)")],
      semantics: [T("Registra el hecho de que algo cambió (qué documento, cuándo), no el contenido del cambio.", "Records the fact that something changed (which document, when), not the content of the change."), T("No se puede desactivar por proyecto ni por capacidad: es una garantía de la plataforma.", "It cannot be disabled per project or capability: it is a platform guarantee."),
        T("Es distinta del rastro de Eventos, que es opcional por sesión: la auditoría es universal.", "It is distinct from the Events trail, which is opt-in per session: audit is universal."), T("No es una capacidad de modelado, sino un efecto de guardar.", "It is not a modeling capability but an effect of saving.")],
      states: [{ n: T("Anotada", "Noted"), d: T("La entrada ya existe y no se reescribe", "The entry exists and is not rewritten") }],
      cross: [T("Persistencia: cada guardado la alimenta.", "Persistence: every save feeds it.")]
    },
    tech: {
      runtime: T("Registro JSONL append-only alimentado por un observador conectado a cada almacén de documentos.", "Append-only JSONL log fed by an observer attached to every document store."),
      services: ["JsonlAuditLogStore", "AuditCanonicalDocumentMutationObserver", "HydraDistributionProvenanceService"], apis: [], schemas: ["Entrada de auditoría JSONL"], tests: [],
      evidence: [EV(PAU, 44, "JsonlAuditLogStore")],
      notes: [T("HydraDistributionProvenanceService figura como fuente en los datos heredados; su papel no se reverificó.", "HydraDistributionProvenanceService is listed as a source in legacy data; its role was not re-verified.")]
    },
    elements: [
      { id: "entry", n: T("Entrada de auditoría", "Audit entry"), kind: "concept", req: "DERIVED", mean: T("Una línea que dice que un documento cambió, con autor y momento.", "A line saying a document changed, with author and moment."), purpose: T("Dejar rastro de cada cambio.", "Leave a trace of each change."), when: T("En cada guardado.", "On every save."), card: "1 documento ↔ N entradas",
        io: { i: T("Cambio en un documento", "Change to a document"), o: T("Línea en el registro", "Line in the log") }, effect: T("No guarda el contenido del cambio.", "Does not store the change content."),
        func: { rel: [T("Cada entrada pertenece a un documento", "Each entry belongs to a document")], lifecycle: T("anotada una vez", "noted once"), cond: T("Siempre.", "Always.") }, tech: { type: "Entrada JSONL", schema: "autor + cambio", evidence: [EV(PAU, 44, "Registro")] } },
      { id: "log", n: T("Registro de solo añadir", "Append-only log"), kind: "concept", req: "MANDATORY", mean: T("El historial completo, que solo crece.", "The complete history, which only grows."), purpose: T("Depurar y demostrar el origen de un resultado.", "Debug and prove the origin of a result."), when: T("Al investigar.", "When investigating."), card: "1 plataforma ↔ 1 registro",
        io: { i: T("Entradas", "Entries"), o: T("Historial consultable", "Searchable history") }, effect: T("Nada se borra ni se reescribe.", "Nothing is deleted or rewritten."),
        func: { rel: [T("Agrupa las entradas", "Groups the entries")], lifecycle: T("solo crece", "only grows"), cond: T("Universal.", "Universal.") }, tech: { type: "JsonlAuditLogStore", schema: "JSONL", evidence: [EV(PAU, 44, "Almacén")] } },
      { id: "observer", n: T("Observador de cambios", "Change observer"), kind: "derived", req: "DERIVED", mean: T("El mecanismo que anota los cambios solo.", "The mechanism that notes changes by itself."), purpose: T("Garantizar que nada quede sin anotar.", "Guarantee nothing goes unrecorded."), when: T("Siempre.", "Always."), card: "1 almacén ↔ 1 observador",
        io: { i: T("Guardados", "Saves"), o: T("Entradas", "Entries") }, effect: T("No depende de ninguna política.", "Does not depend on any policy."),
        func: { rel: [T("Conectado a cada almacén de documentos", "Attached to every document store")], lifecycle: T("siempre activo", "always on"), cond: T("No se puede desactivar.", "Cannot be disabled.") }, tech: { type: "AuditCanonicalDocumentMutationObserver", schema: "—", evidence: [EV(PCF, 14, "Fábrica que lo conecta")] } }
    ],
    map: { nodes: [{ id: "obs", elem: "observer", n: T("Observador", "Observer"), k: "derived", req: "DERIVED", d: T("Anota solo", "Notes on its own") }, { id: "entry", elem: "entry", n: T("Entrada", "Entry"), k: "concept", req: "DERIVED", d: T("Quién y cuándo", "Who and when") }, { id: "log", elem: "log", n: T("Registro", "Log"), k: "concept", req: "MANDATORY", d: T("Solo añadir", "Append only") }, { id: "save", n: T("Guardado de documento", "Document save"), k: "external", req: "MANDATORY", d: T("Lo que dispara todo", "What triggers it all") }],
      edges: [{ a: "save", b: "obs", rel: "ref", req: "MANDATORY" }, { a: "obs", b: "entry", rel: "gen", req: "MANDATORY" }, { a: "log", b: "entry", rel: "contains", card1: "1", card2: "0..N", req: "MANDATORY" }] },
    define: [{ id: "auto", n: T("Automático", "Automatic"), kind: "generated", req: "MANDATORY", elems: ["entry", "log", "observer"], does: T("No se crea ni se configura nada: la auditoría anota por sí sola cada cambio de cada documento.", "Nothing is created or configured: the audit notes every change of every document by itself."), func: T("Universal y siempre activa.", "Universal and always on."), tech: T("Observador conectado por la fábrica de almacenes.", "Observer attached by the store factory.") }],
    rel: [{ to: "persistence", type: "event", dir: "in", u: T("Se alimenta de cada guardado.", "Fed by every save."), f: T("Sin opciones.", "No options."), t: T("Observador conectado a cada almacén.", "Observer attached to each store.") },
      { to: "events", type: "alternative", dir: "out", u: T("Es independiente del rastro de Eventos.", "Independent of the Events trail."), f: T("Eventos es opcional; la auditoría no.", "Events is optional; audit is not."), t: T("publishEvents() solo afecta a Eventos.", "publishEvents() only affects Events.") }]
  });

  HM.proc({ id: "audit_record_mutation", scope: "cap", cap: "audit", caps: ["persistence"], elems: ["entry", "observer"],
    n: T("Anotar un cambio", "Record a change"), d: T("De un guardado a una entrada en el registro.", "From a save to an entry in the log."),
    nodes: [
      N("s", "start", "auto", null, "persistence", ["Se guarda un documento", "A document is saved"], ["Cualquier capacidad.", "Any capability."]),
      N("a", "step", "auto", "MANDATORY", "audit", ["El observador detecta el cambio", "The observer detects the change"], ["Está conectado a cada almacén, sin excepción.", "It is attached to every store, without exception."], { e: "observer", x: { rt: "AuditCanonicalDocumentMutationObserver", test: [], ev: [EV(PCF, 14, "conexión")] } }),
      N("b", "step", "auto", "MANDATORY", "audit", ["Crear la entrada", "Create the entry"], ["Con el documento y el momento, no el contenido.", "With the document and moment, not the content."], { e: "entry" }),
      N("c", "step", "auto", "MANDATORY", "audit", ["Añadir al registro", "Append to the log"], ["Solo se añade; nunca se reescribe.", "Only appended; never rewritten."], { e: "log", x: { rt: "JsonlAuditLogStore", test: [], ev: [EV(PAU, 44, "append")] } }),
      N("r", "artifact", null, null, "audit", ["Entrada registrada", "Entry recorded"], ["Consultable para depurar o demostrar origen.", "Searchable to debug or prove origin."]),
      N("e", "end", null, null, "audit", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "b"), E("b", "c"), E("c", "r"), E("r", "e")] });

  HM.cap("security", { noMicro: true,
    name: T("Seguridad", "Security"), group: "platform", noVariants: true,
    user: {
      tagline: T("Filtra peticiones y aísla programas externos", "Filters requests and isolates external programs"),
      what: T("Protege HYDRA filtrando la forma y el tamaño de cada petición y aislando cualquier programa externo que se ejecute en tu nombre.", "Protects HYDRA by filtering the shape and size of every request and isolating any external program run on your behalf."),
      why: T("Evita que entradas inesperadas lleguen a las capacidades y que un programa externo haga más de lo previsto.", "Prevents unexpected input from reaching capabilities and an external program from doing more than expected."),
      when: T("En cada petición, y cada vez que HYDRA ejecuta un programa externo.", "On every request, and every time HYDRA runs an external program."),
      canDefine: [T("Nada: es automático", "Nothing: it is automatic")],
      needs: [T("Una petición entrante o un programa externo a ejecutar", "An incoming request or an external program to run")],
      produces: [T("Peticiones acotadas y comprobadas", "Bounded and checked requests"), T("Ejecuciones externas con entorno mínimo y apagado garantizado", "External runs with minimal environment and guaranteed teardown"), T("La declaración de «no disponible» si falta un motor", "A \"not available\" declaration when an engine is missing")],
      mandatory: [], optional: [], conditional: [],
      next: [T("Seguir trabajando con normalidad", "Carry on working normally")],
      interacts: [{ cap: "external_repositories", t: T("Las operaciones Git se ejecutan aisladas", "Git operations run isolated") }, { cap: "toolchains", t: T("Los motores externos se lanzan por el mismo punto", "External engines are launched through the same point") }]
    },
    func: {
      lifecycle: [T("petición recibida → filtrada → capacidad", "request received → filtered → capability"), T("programa externo solicitado → ejecutado aislado → apagado garantizado", "external program requested → run isolated → guaranteed teardown")],
      semantics: [T("Filtra y limita forma y tamaño de cada petición antes de que llegue a cualquier capacidad.", "Filters and bounds the shape and size of every request before it reaches any capability."), T("Es el único punto por el que HYDRA ejecuta programas externos: entorno mínimo, salida acotada y apagado garantizado.", "It is the single point through which HYDRA runs external programs: minimal environment, bounded output and guaranteed teardown."),
        T("Un motor externo que falta se declara no disponible; su comportamiento nunca se simula.", "A missing external engine is declared unavailable; its behaviour is never simulated."), T("No conoce el contenido de metamodelos, modelos ni reglas.", "It knows nothing about metamodel, model or rule content.")],
      states: [{ n: T("Petición aceptada", "Request accepted"), d: T("Cumple forma y tamaño", "Meets shape and size") }, { n: T("Petición rechazada", "Request rejected"), d: T("No cumple", "Does not comply") }],
      cross: [T("Repositorios externos y Toolchains: sus programas externos pasan por este punto.", "External repositories and Toolchains: their external programs go through this point.")]
    },
    tech: {
      runtime: T("Filtro de endurecimiento de peticiones y ejecutor de procesos externos con sandbox.", "Request hardening filter and sandboxed external process runner."),
      services: ["HydraRequestHardeningFilter", "HydraExternalProcessRunner"], apis: [], schemas: ["Políticas de forma/tamaño de petición"], tests: [],
      evidence: [EV(PHF, 27, "HydraRequestHardeningFilter"), EV(PHR, 22, "HydraExternalProcessRunner")],
      notes: [T("Los límites numéricos concretos (tamaños, tiempos) no se verificaron en esta revisión.", "The concrete numeric limits (sizes, timeouts) were not verified in this review."), T("Se afirma que es el único punto de ejecución de procesos externos según la base heredada; no se auditó todo el código.", "It is stated to be the only point for external process execution per the legacy base; the whole code was not audited.")]
    },
    elements: [
      { id: "filter", n: T("Filtro de peticiones", "Request filter"), kind: "concept", req: "MANDATORY", mean: T("Comprueba la forma y el tamaño de cada petición entrante.", "Checks the shape and size of every incoming request."), purpose: T("Frenar entradas anómalas antes de que lleguen a ninguna capacidad.", "Stop abnormal input before it reaches any capability."), when: T("En cada petición.", "On every request."), card: "1 petición ↔ 1 comprobación",
        io: { i: T("Petición entrante", "Incoming request"), o: T("Aceptada o rechazada", "Accepted or rejected") }, effect: T("Opera igual con cualquier capacidad.", "Works the same with any capability."),
        func: { rel: [T("Precede a todas las capacidades", "Precedes all capabilities")], lifecycle: T("recibida → aceptada/rechazada", "received → accepted/rejected"), cond: T("Siempre activo.", "Always on.") }, tech: { type: "HydraRequestHardeningFilter", schema: "políticas de petición", evidence: [EV(PHF, 27, "Filtro")] } },
      { id: "runner", n: T("Ejecutor de programas externos", "External program runner"), kind: "concept", req: "MANDATORY", mean: T("El único punto por el que se lanzan programas externos, con entorno mínimo y apagado garantizado.", "The single point through which external programs are launched, with minimal environment and guaranteed teardown."), purpose: T("Evitar que un programa externo haga más de lo previsto.", "Prevent an external program from doing more than expected."), when: T("Cada vez que HYDRA ejecuta algo externo.", "Every time HYDRA runs something external."), card: "1 ejecución ↔ 1 entorno aislado",
        props: [PR("Entorno mínimo", "Minimal environment", "DERIVED", "Solo lo imprescindible", "Only what is essential"), PR("Salida acotada", "Bounded output", "DERIVED", "Con un tope", "With a cap"), PR("Apagado garantizado", "Guaranteed teardown", "DERIVED", "El proceso siempre termina", "The process always ends")],
        io: { i: T("Programa y argumentos", "Program and arguments"), o: T("Resultado acotado o «no disponible»", "Bounded result or \"not available\"") }, effect: T("Si falta el motor se declara no disponible; nunca se simula.", "If the engine is missing it is declared unavailable; never simulated."),
        func: { rel: [T("Usado por Git y por los motores externos", "Used by Git and external engines")], lifecycle: T("lanzado → acotado → apagado", "launched → bounded → shut down"), cond: T("Sin shell ni comandos arbitrarios para Git.", "No shell or arbitrary commands for Git.") }, tech: { type: "HydraExternalProcessRunner", schema: "sandbox de procesos", evidence: [EV(PHR, 22, "Ejecutor")] } },
      { id: "unavailable", n: T("Motor no disponible", "Engine unavailable"), kind: "derived", req: "DERIVED", mean: T("La declaración honesta de que falta una herramienta.", "The honest statement that a tool is missing."), purpose: T("No fingir resultados.", "Not fake results."), when: T("Si el programa externo no existe.", "If the external program does not exist."), card: "0..1 por ejecución",
        io: { i: T("Programa ausente", "Missing program"), o: T("Estado «no disponible»", "\"Not available\" state") }, effect: T("Detiene la operación sin simular.", "Stops the operation without simulating."),
        func: { rel: [T("Se refleja en Toolchains", "Reflected in Toolchains")], lifecycle: T("detectado al ejecutar", "detected on run"), cond: T("Solo si falta el motor.", "Only if the engine is missing.") }, tech: { type: "TOOL_UNAVAILABLE (etiqueta compartida)", schema: "—", evidence: [EV(PHR, 22, "Ejecutor")] } }
    ],
    map: { nodes: [{ id: "filter", elem: "filter", n: T("Filtro de peticiones", "Request filter"), k: "concept", req: "MANDATORY", d: T("Forma y tamaño", "Shape and size") }, { id: "runner", elem: "runner", n: T("Ejecutor aislado", "Isolated runner"), k: "concept", req: "MANDATORY", d: T("Programas externos", "External programs") }, { id: "una", elem: "unavailable", n: T("No disponible", "Unavailable"), k: "derived", req: "DERIVED", d: T("Sin simular", "Never simulated") }, { id: "ext", n: T("Programa externo", "External program"), k: "external", req: "CONDITIONAL", d: T("Git, motores, build", "Git, engines, build") }],
      edges: [{ a: "runner", b: "ext", rel: "ref", req: "CONDITIONAL" }, { a: "runner", b: "una", rel: "gen", req: "CONDITIONAL" }, { a: "filter", b: "runner", rel: "ref", req: "OPTIONAL" }] },
    define: [{ id: "auto", n: T("Automático", "Automatic"), kind: "generated", req: "MANDATORY", elems: ["filter", "runner"], does: T("No hay nada que definir: cada petición se filtra y cada programa externo se lanza aislado, sin que tengas que activarlo.", "There is nothing to define: every request is filtered and every external program is launched isolated, with nothing for you to turn on."), func: T("Siempre activa.", "Always on."), tech: T("Filtro y ejecutor de procesos.", "Filter and process runner.") }],
    rel: [{ to: "external_repositories", type: "required", dir: "in", u: T("Las operaciones Git se ejecutan aisladas.", "Git operations run isolated."), f: T("Sin shell.", "No shell."), t: T("Ejecutor de procesos externos.", "External process runner.") },
      { to: "toolchains", type: "required", dir: "in", u: T("Los motores externos se lanzan de forma aislada.", "External engines are launched in isolation."), f: T("Un motor ausente se declara no disponible.", "A missing engine is declared unavailable."), t: T("Ejecutor de procesos externos.", "External process runner.") }]
  });

  HM.proc({ id: "security_filter_sandbox", scope: "cap", cap: "security", elems: ["filter", "runner"],
    n: T("Filtrar peticiones y aislar procesos externos", "Filter requests and isolate external processes"), d: T("Las dos protecciones de la capacidad.", "The capability's two protections."),
    nodes: [
      N("s", "start", "auto", null, "security", ["Llega una petición", "A request arrives"], ["Antes de cualquier capacidad.", "Before any capability."]),
      N("a", "step", "auto", "MANDATORY", "security", ["Comprobar forma y tamaño", "Check shape and size"], ["Se limita lo que puede entrar.", "What can enter is limited."], { e: "filter", x: { rt: "HydraRequestHardeningFilter", test: [], ev: [EV(PHF, 27, "filtro")] } }),
      N("d1", "decision", "auto", "MANDATORY", "security", ["¿Cumple?", "Does it comply?"], ["Si no cumple, no llega a ninguna capacidad.", "If not, it does not reach any capability."]),
      N("d2", "decision", "auto", "MANDATORY", "security", ["¿Necesita un programa externo?", "Does it need an external program?"], ["Solo algunas operaciones lo necesitan.", "Only some operations need it."]),
      N("b", "step", "auto", "CONDITIONAL", "security", ["Lanzar aislado", "Launch isolated"], ["Entorno mínimo, salida acotada y apagado garantizado.", "Minimal environment, bounded output and guaranteed teardown."], { e: "runner", x: { rt: "HydraExternalProcessRunner", test: [], ev: [EV(PHR, 22, "ejecutor")] } }),
      N("d3", "decision", "ext", "CONDITIONAL", "security", ["¿El programa existe?", "Does the program exist?"], ["Si falta, se declara no disponible y no se simula.", "If missing, it is declared unavailable and not simulated."]),
      N("c", "step", "auto", "MANDATORY", "security", ["Entregar a la capacidad", "Pass to the capability"], ["Con el resultado acotado.", "With the bounded result."]),
      N("x", "step", "auto", "CONDITIONAL", "security", ["Rechazar o declarar no disponible", "Reject or declare unavailable"], ["Sin fingir resultados.", "Without faking results."], { e: "unavailable" }),
      N("e", "end", null, null, "security", ["Fin", "End"])
    ],
    edges: [E("s", "a"), E("a", "d1"), E("d1", "d2", "next", "Sí", "Yes"), E("d1", "x", "alt", "No", "No"), E("d2", "b", "cond", "Sí", "Yes"), E("d2", "c", "next", "No", "No"), E("b", "d3"), E("d3", "c", "next", "Sí", "Yes"), E("d3", "x", "alt", "No", "No"), E("c", "e"), E("x", "e")] });
})();
