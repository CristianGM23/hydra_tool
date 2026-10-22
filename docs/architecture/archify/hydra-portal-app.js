/* HYDRA architecture portal — application (UI). Content: hydra-portal-model.js + hydra-model-*.js. Engine: hydra-portal-graph.js.
   Legacy narrative data (technical evidence reused as-is): hydra-architecture-data.js, hydra-components-data.js. */
(function(){
  "use strict";
  var HM = window.HM, G = window.HydraGraph, LEG = window.CAPABILITIES || {};
  var HC = window.HYDRA_COMPONENTS || { COMPONENTS: {}, ORDER: [] };
  var COMPONENTS = HC.COMPONENTS, ORDER = HC.ORDER;

  // ------------------------------------------------------------------ UI strings
  var UI = {
    es: {
      secOverview: "VISIÓN GENERAL", secCaps: "CAPACIDADES", secProcs: "PROCESOS", secTests: "PRUEBAS Y EVIDENCIAS",
      lvUser: "USUARIO", lvFunc: "FUNCIONAL", lvTech: "TÉCNICO",
      modeMap: "Mapa interactivo", modeArchify: "Diagrama Archify",
      tabs: { overview: "Visión general", elements: "Elementos", map: "Mapa de dominio", define: "Cómo se define", processes: "Procesos", relationships: "Relaciones", implementation: "Implementación", evidence: "Evidencia / Pruebas" },
      lvHint: { user: "Vista USUARIO: qué es cada cosa y para qué sirve, sin jerga técnica.",
        functional: "Vista FUNCIONAL: conceptos, variantes, cardinalidades, ciclo de vida y relaciones entre capacidades.",
        technical: "Vista TÉCNICA: servicios, tipos, APIs, pruebas y evidencia en el código." },
      what: "Qué es", why: "Para qué sirve", when: "Cuándo usarla", canDefine: "Qué puedes definir", needs: "Qué necesita", produces: "Qué produce",
      mandatory: "Obligatorio", optional: "Opcional", conditional: "Condicional", next: "Qué puede pasar después", interacts: "Con qué otras capacidades se relaciona",
      howDefined: "Cómo se define", lifecycle: "Ciclo de vida", semantics: "Semántica", states: "Estados", cross: "Comportamiento entre capacidades",
      runtime: "Runtime", services: "Servicios", apis: "APIs", schemas: "Esquemas / tipos", tests: "Pruebas", notes: "Notas y límites verificados", evidence: "Evidencia en el código",
      coverage: "Cobertura de documentación (definición de hecho estricta)", covOk: "cubierto", covMissing: "pendiente",
      purpose: "Propósito", whenUsed: "Cuándo se usa", variants: "Variantes reales", props: "Opciones de configuración", card: "Cardinalidad", io: "Entradas → salidas", effect: "Efecto en ejecución",
      relations: "Relaciones", lifecycleOf: "Ciclo de vida", condition: "Condición", techType: "Tipo técnico", authoredBy: "Se define con", participates: "Procesos donde participa", viewInElements: "Ver en Elementos", viewInMap: "Ver en el mapa",
      noElement: "Selecciona un elemento del mapa para ver su significado, variantes y procesos.", noNode: "Selecciona una etapa del proceso para ver su detalle.",
      mapIntro: "Qué existe y cómo se relaciona (no es un diagrama de clases). Haz clic en un elemento.",
      defineIntro: "Mecanismos reales con los que se crea y configura. Cada uno tiene su micro-proceso gráfico.", showProc: "Ver micro-proceso", hideProc: "Ocultar micro-proceso",
      whatYouDo: "Qué haces", funcSem: "Semántica de autoría", techImpl: "Implementación en la interfaz",
      capLabel: "Capacidad", procLabel: "Proceso", crossLabel: "Procesos entre capacidades", capProcs: "Procesos de la capacidad", authProcs: "Micro-procesos de definición", crossHere: "Cruzan esta capacidad",
      openCap: "Abrir capacidad", openProcess: "Ver proceso", openMapAt: "Abrir el mapa de dominio",
      capRuntime: "Runtime de la capacidad propietaria", capRuntimeNote: "No hay un servicio específico verificado para esta etapa; se muestra el runtime de su capacidad.", endDefault: "Aquí termina el proceso; el resultado de este camino es lo que se ha producido en las etapas anteriores.", stepKind: { start: "Inicio", end: "Fin", step: "Paso", decision: "Decisión", artifact: "Resultado / artefacto" },
      actor: { user: "Lo haces tú", auto: "HYDRA lo hace automáticamente", ext: "Sistema externo" },
      ownerCap: "Capacidad propietaria", concept: "Concepto de dominio", stage: "Etapa", cond: "Condiciones", stateT: "Transición de estado", prev: "Viene de", needs2: "Necesita", produces2: "Produce", why2: "Qué ocurre y por qué",
      rt: "Runtime / servicio", nextEdge: { next: "siguiente", opt: "opcional", cond: "condicional", alt: "alternativa", loop: "repetir / revalidar", event: "por evento", x: "pasa a otra capacidad" },
      legendProc: "Leyenda del proceso", legendMap: "Leyenda del mapa", legendOv: "Leyenda de relaciones",
      relType: { required: "Obligatoria", optional: "Opcional", conditional: "Condicional", alternative: "Alternativa", event: "Por evento", producer: "Productor → consumidor", external: "Sistema externo" },
      relTypeHelp: { required: "No funciona sin ella", optional: "Puede usarse o no", conditional: "Solo si se cumple una condición", alternative: "Otra vía para lograr lo mismo", event: "Se activa por un evento", producer: "Una capacidad crea lo que otra consume", external: "Cruza el límite de HYDRA" },
      req: { MANDATORY: "Obligatorio", OPTIONAL: "Opcional", CONDITIONAL: "Condicional", ALTERNATIVE: "Alternativa", REPEATABLE: "Repetible", DERIVED: "Generado / derivado" },
      reqTag: { MANDATORY: "oblig.", OPTIONAL: "opc.", CONDITIONAL: "cond.", ALTERNATIVE: "alt.", REPEATABLE: "×N", DERIVED: "gen." },
      reqHelp: { MANDATORY: "Imprescindible", OPTIONAL: "Se puede omitir", CONDITIONAL: "Solo si aplica una condición", ALTERNATIVE: "Una de varias formas posibles", REPEATABLE: "Puede repetirse varias veces", DERIVED: "HYDRA lo genera o lo deduce" },
      mapEdge: { contains: "contiene", ref: "referencia", isa: "es un tipo de", gen: "genera / deriva" },
      mapNode: { concept: "Concepto", variant: "Variante", derived: "Generado / derivado", external: "Externo" },
      relsIntro: "Relaciones con otras capacidades de HYDRA (tipadas según el código).", dirOut: "Esta capacidad →", dirIn: "→ Esta capacidad",
      techOnly: "El detalle de implementación (clases, servicios, APIs, evidencia con archivo y línea) solo se muestra en el nivel TÉCNICO.", switchTech: "Cambiar a nivel TÉCNICO",
      selCap: "Selecciona una capacidad.", search: "Buscar elemento…", fit: "Ajustar", emptyProcs: "Esta capacidad no tiene procesos propios registrados.",
      ovHint: "Haz clic en una capacidad para ver su resumen y sus relaciones tipadas.", allCaps: "Todas las capacidades", groupLabel: "Grupo",
      sidebarTitle: "Explorar componentes", sidebarSub: "Mismo ID canónico en los tres niveles. Progresión: qué hace → cómo funciona → cómo está implementado → evidencia.", searchComp: "Buscar componente…",
      tierWhat: "QUÉ HACE", tierLimits: "LÍMITES DE MODELADO", tierRelations: "RELACIONES FUNCIONALES", tierHow: "CÓMO FUNCIONA", tierImpl: "CÓMO ESTÁ IMPLEMENTADO", tierSrc: "EVIDENCIA EN EL CÓDIGO",
      revealHow: "+ ver cómo funciona", revealImpl: "+ ver cómo está implementado", revealSrc: "+ ver evidencia en el código", notInLevel: "No aparece como nodo en los niveles usuario/funcional; solo en el técnico, como infraestructura transversal.",
      noRelations: "No depende funcionalmente de otra capacidad ni la alimenta de forma directa; otras pueden depender de esta.", goCap: "Ver vista de capacidad",
      archifyNote: "Diagrama Archify validado (proyección del mismo sistema). Para el mapa con relaciones tipadas usa «Mapa interactivo».", openTab: "Abrir diagrama técnico en pestaña nueva",
      toggleShow: "Componentes ▸", toggleHide: "Componentes ◂", provenance: "Estado fuente: árbol de trabajo local (HEAD resuelto dinámicamente; ver knowledge/baseline.json) · contenido verificado contra el código actual",
      archifyProc: "Abrir el diagrama Archify detallado", noProcs: "No hay procesos.", fromSrc: "Evidencia", dbl: "Cobertura"
    },
    en: {
      secOverview: "OVERVIEW", secCaps: "CAPABILITIES", secProcs: "PROCESSES", secTests: "TESTS & EVIDENCE",
      lvUser: "USER", lvFunc: "FUNCTIONAL", lvTech: "TECHNICAL",
      modeMap: "Interactive map", modeArchify: "Archify diagram",
      tabs: { overview: "Overview", elements: "Elements", map: "Domain map", define: "How to define", processes: "Processes", relationships: "Relationships", implementation: "Implementation", evidence: "Evidence / Tests" },
      lvHint: { user: "USER view: what each thing is and what it is for, without technical jargon.",
        functional: "FUNCTIONAL view: concepts, variants, cardinalities, lifecycle and capability relationships.",
        technical: "TECHNICAL view: services, types, APIs, tests and source evidence." },
      what: "What it is", why: "What it is for", when: "When to use it", canDefine: "What you can define", needs: "What it needs", produces: "What it produces",
      mandatory: "Mandatory", optional: "Optional", conditional: "Conditional", next: "What can happen next", interacts: "Which other capabilities it works with",
      howDefined: "How it is defined", lifecycle: "Lifecycle", semantics: "Semantics", states: "States", cross: "Cross-capability behaviour",
      runtime: "Runtime", services: "Services", apis: "APIs", schemas: "Schemas / types", tests: "Tests", notes: "Verified notes and limits", evidence: "Source evidence",
      coverage: "Documentation coverage (strict definition of done)", covOk: "covered", covMissing: "pending",
      purpose: "Purpose", whenUsed: "When it is used", variants: "Real variants", props: "Configuration options", card: "Cardinality", io: "Inputs → outputs", effect: "Runtime effect",
      relations: "Relationships", lifecycleOf: "Lifecycle", condition: "Condition", techType: "Technical type", authoredBy: "Defined with", participates: "Processes where it takes part", viewInElements: "View in Elements", viewInMap: "View on the map",
      noElement: "Select an element on the map to see its meaning, variants and processes.", noNode: "Select a process stage to see its detail.",
      mapIntro: "What exists and how it relates (not a class diagram). Click an element.",
      defineIntro: "The real mechanisms used to create and configure it. Each one has its own graphical micro-process.", showProc: "Show micro-process", hideProc: "Hide micro-process",
      whatYouDo: "What you do", funcSem: "Authoring semantics", techImpl: "UI implementation",
      capLabel: "Capability", procLabel: "Process", crossLabel: "Cross-capability processes", capProcs: "Capability processes", authProcs: "Authoring micro-processes", crossHere: "Crossing this capability",
      openCap: "Open capability", openProcess: "View process", openMapAt: "Open the domain map",
      capRuntime: "Owning capability runtime", capRuntimeNote: "No stage-specific service was verified; the capability runtime is shown instead.", endDefault: "The process ends here; the result of this path is what the previous stages produced.", stepKind: { start: "Start", end: "End", step: "Step", decision: "Decision", artifact: "Result / artifact" },
      actor: { user: "You do it", auto: "HYDRA does it automatically", ext: "External system" },
      ownerCap: "Owning capability", concept: "Domain concept", stage: "Stage", cond: "Conditions", stateT: "State transition", prev: "Comes from", needs2: "Needs", produces2: "Produces", why2: "What happens and why",
      rt: "Runtime / service", nextEdge: { next: "next", opt: "optional", cond: "conditional", alt: "alternative", loop: "repeat / revalidate", event: "by event", x: "hands over to another capability" },
      legendProc: "Process legend", legendMap: "Map legend", legendOv: "Relationship legend",
      relType: { required: "Required", optional: "Optional", conditional: "Conditional", alternative: "Alternative", event: "Event-driven", producer: "Producer → consumer", external: "External system" },
      relTypeHelp: { required: "Does not work without it", optional: "May or may not be used", conditional: "Only when a condition holds", alternative: "Another way to achieve the same", event: "Triggered by an event", producer: "One capability creates what another consumes", external: "Crosses HYDRA's boundary" },
      req: { MANDATORY: "Mandatory", OPTIONAL: "Optional", CONDITIONAL: "Conditional", ALTERNATIVE: "Alternative", REPEATABLE: "Repeatable", DERIVED: "Generated / derived" },
      reqTag: { MANDATORY: "mand.", OPTIONAL: "opt.", CONDITIONAL: "cond.", ALTERNATIVE: "alt.", REPEATABLE: "×N", DERIVED: "gen." },
      reqHelp: { MANDATORY: "Indispensable", OPTIONAL: "Can be omitted", CONDITIONAL: "Only if a condition applies", ALTERNATIVE: "One of several possible ways", REPEATABLE: "Can occur several times", DERIVED: "HYDRA generates or infers it" },
      mapEdge: { contains: "contains", ref: "references", isa: "is a kind of", gen: "generates / derives" },
      mapNode: { concept: "Concept", variant: "Variant", derived: "Generated / derived", external: "External" },
      relsIntro: "Relationships with other HYDRA capabilities (typed from the code).", dirOut: "This capability →", dirIn: "→ This capability",
      techOnly: "Implementation detail (classes, services, APIs, evidence with file and line) is only shown in the TECHNICAL level.", switchTech: "Switch to TECHNICAL level",
      selCap: "Select a capability.", search: "Search element…", fit: "Fit", emptyProcs: "This capability has no processes registered.",
      ovHint: "Click a capability to see its summary and typed relationships.", allCaps: "All capabilities", groupLabel: "Group",
      sidebarTitle: "Explore components", sidebarSub: "Same canonical ID across all three levels. Progression: what it does → how it works → how it's implemented → evidence.", searchComp: "Search component…",
      tierWhat: "WHAT IT DOES", tierLimits: "MODELING SCOPE & LIMITS", tierRelations: "FUNCTIONAL RELATIONSHIPS", tierHow: "HOW IT WORKS", tierImpl: "HOW IT IS IMPLEMENTED", tierSrc: "SOURCE EVIDENCE",
      revealHow: "+ show how it works", revealImpl: "+ show how it's implemented", revealSrc: "+ show source evidence", notInLevel: "Not drawn as a node in user/functional levels; only in the technical one, as cross-cutting infrastructure.",
      noRelations: "Does not functionally depend on or directly feed another capability; others may depend on this one.", goCap: "View capability",
      archifyNote: "Validated Archify diagram (a projection of the same system). For typed relationships use “Interactive map”.", openTab: "Open technical diagram in a new tab",
      toggleShow: "Components ▸", toggleHide: "Components ◂", provenance: "Source state: local working tree (HEAD resolved dynamically; see knowledge/baseline.json) · content verified against the current code",
      archifyProc: "Open the detailed Archify diagram", noProcs: "No processes.", fromSrc: "Evidence", dbl: "Coverage"
    }
  };
  var COV_LABELS = {
    es: { user: "Explicación para USUARIO", functional: "Explicación FUNCIONAL", technical: "Mapeo TÉCNICO", concepts: "Conceptos definibles", variants: "Variantes / tipos", purpose: "Propósito de cada elemento", requiredness: "Obligatorio / opcional / condicional", authoring: "Métodos de autoría", microprocess: "Micro-procesos de autoría", domainmap: "Mapa de dominio", internal: "Proceso interno / ciclo de vida", graphical: "Procesos gráficos", clickable: "Etapas seleccionables (inspector)", relationships: "Relaciones", inputs: "Entradas", outputs: "Salidas", crosscap: "Interacciones entre capacidades", evidence: "Evidencia técnica" },
    en: { user: "USER explanation", functional: "FUNCTIONAL explanation", technical: "TECHNICAL mapping", concepts: "Definable concepts", variants: "Variants / types", purpose: "Purpose of each element", requiredness: "Mandatory / optional / conditional", authoring: "Authoring methods", microprocess: "Authoring micro-processes", domainmap: "Domain map", internal: "Internal process / lifecycle", graphical: "Graphical processes", clickable: "Selectable stages (inspector)", relationships: "Relationships", inputs: "Inputs", outputs: "Outputs", crosscap: "Cross-capability interactions", evidence: "Technical evidence" }
  };
  var COV_KEYS = ["user", "functional", "technical", "concepts", "variants", "purpose", "requiredness", "authoring", "microprocess", "domainmap", "internal", "graphical", "clickable", "relationships", "inputs", "outputs", "crosscap", "evidence"];


  // ------------------------------------------------------------------ additional UI strings (audience projections, toolbar, legend)
  function addUI(lang, o){ var t = UI[lang]; Object.keys(o).forEach(function(k){ t[k] = o[k]; }); }
  addUI("es", {
    kindLabel: { user: "Acción tuya", auto: "Automático", ext: "Sistema externo", step: "Paso", start: "Inicio", end: "Fin", decision: "Decisión", artifact: "Resultado" },
    tb: { fit: "Ajustar", fitT: "Ajustar el diagrama a la vista", out: "Alejar", "in": "Acercar", reset: "Restablecer la vista", focus: "Centrar", focusT: "Centrar en el nodo seleccionado" },
    legendBtn: "Leyenda", inspHide: "Ocultar detalle", inspShow: "Mostrar detalle",
    fSummary: "Resumen funcional", fIo: "Entrada → Salida", fCond: "Condiciones", fState: "Transición de estado", fConcept: "Concepto de dominio", fOwner: "Capacidad propietaria", fFrom: "Viene de", fTo: "Continúa en",
    fInputs: "Entradas", fOutputs: "Salidas", fConditions: "Condiciones", fRel: "Relaciones semánticas",
    tImplements: "Realiza el paso funcional", tImpl: "Cómo se realiza", tRuntime: "Runtime / servicio", tTests: "Pruebas", tEvidence: "Evidencia en el código", tOwnerRuntime: "Runtime de la capacidad propietaria",
    tOwnerRuntimeNote: "No hay un servicio específico verificado para esta etapa; se muestra el runtime de su capacidad.",
    mapTitle: "Mapa funcional → técnico", mapCols: ["Concepto", "Tipo técnico", "Esquema"],
    conceptsIdx: "Conceptos de la capacidad", lvlUserNote: "En palabras llanas",
    optHint: { MANDATORY: "Paso imprescindible", OPTIONAL: "Se puede omitir", CONDITIONAL: "Solo si se cumple una condición", ALTERNATIVE: "Una de varias vías posibles", REPEATABLE: "Puede repetirse", DERIVED: "Lo genera o deduce HYDRA" },
    hideDetail: "Ocultar detalle", showDetail: "Mostrar detalle", ovFuncHead: "Modelo funcional", ovTechHead: "Realización técnica"
  });
  addUI("en", {
    kindLabel: { user: "Your action", auto: "Automatic", ext: "External system", step: "Step", start: "Start", end: "End", decision: "Decision", artifact: "Result" },
    tb: { fit: "Fit", fitT: "Fit the diagram to the view", out: "Zoom out", "in": "Zoom in", reset: "Reset view", focus: "Centre", focusT: "Centre on the selected node" },
    legendBtn: "Legend", inspHide: "Hide detail", inspShow: "Show detail",
    fSummary: "Functional summary", fIo: "Input → Output", fCond: "Conditions", fState: "State transition", fConcept: "Domain concept", fOwner: "Owning capability", fFrom: "Comes from", fTo: "Continues to",
    fInputs: "Inputs", fOutputs: "Outputs", fConditions: "Conditions", fRel: "Semantic relationships",
    tImplements: "Realises the functional step", tImpl: "How it is realised", tRuntime: "Runtime / service", tTests: "Tests", tEvidence: "Source evidence", tOwnerRuntime: "Owning capability runtime",
    tOwnerRuntimeNote: "No stage-specific service was verified; the capability runtime is shown instead.",
    mapTitle: "Functional → technical map", mapCols: ["Concept", "Technical type", "Schema"],
    conceptsIdx: "Capability concepts", lvlUserNote: "In plain words",
    optHint: { MANDATORY: "Indispensable step", OPTIONAL: "Can be omitted", CONDITIONAL: "Only if a condition holds", ALTERNATIVE: "One of several possible routes", REPEATABLE: "Can repeat", DERIVED: "Generated or inferred by HYDRA" },
    hideDetail: "Hide detail", showDetail: "Show detail", ovFuncHead: "Functional model", ovTechHead: "Technical realisation"
  });

  addUI("es", {
    td: { what: "Qué es", does: "Qué hace", why: "Por qué existe", resp: "Responsabilidad", where: "Dónde participa", recv: "Recibe", prod: "Produce", calls: "Qué llama / de qué depende", usedBy: "Quién lo usa", fx: "Efectos secundarios", rel: "Por qué importa en HYDRA", rat: "Razón de diseño", ratProven: "Demostrada en el código", ratObs: "Rol arquitectónico observable en la implementación",
      methods: "Métodos importantes", excerpts: "Fragmentos de código explicados", src: "Evidencia en el código (archivo:línea)", tests: "Pruebas que lo protegen", implBy: "Implementado por", none: "Sin documentación técnica para esta capacidad todavía.",
      mWhen: "Cuándo se invoca", mIn: "Entrada (significado)", mOut: "Salida (significado)", mDec: "Decisiones", mFail: "Si falla", mNext: "Siguiente paso",
      xWhat: "Qué muestra", xWhy: "Por qué importa", xRole: "Papel en el proceso", xIn: "Entradas", xOut: "Salidas", xBehavior: "Comportamiento", xRelated: "Relacionado",
      tContract: "Contrato que verifica", tWhy: "Por qué importa", tProtects: "Qué protege",
      traceT: "Trazabilidad: USUARIO → FUNCIONAL → TÉCNICO → CÓDIGO → ROL EN EJECUCIÓN", cUser: "Concepto del usuario", cFunc: "Concepto funcional", cComp: "Componente", cMethod: "Método", cSrc: "Código", cRole: "Rol en ejecución",
      mapT: "Mapa de implementación por responsabilidad", mapHint: "Cada columna agrupa los componentes por responsabilidad. Haz clic para ver su explicación completa.", testsT: "Pruebas explicadas",
      viewList: "Catálogo", viewMap: "Mapa", stageT: "Etapa", stageResp: "Responsabilidad funcional", stageBy: "Implementada por", stageWhy: "Por qué participa aquí", stageDoes: "Qué hace el código", stageIn: "Entradas en este punto", stageOut: "Salidas", stageFx: "Efectos secundarios", stageNext: "Siguiente interacción técnica", stageFail: "Si falla", kindWord: "tipo" },
    pb: { restart: "Reiniciar", prev: "Anterior", play: "Reproducir", pause: "Pausa", next: "Siguiente", speed: "Velocidad", opt: "Incluir pasos opcionales", step: "Paso", branch: "Elige la rama:", cross: "Pasa a otra capacidad", done: "Fin del recorrido", hint: "Reproducir el proceso paso a paso" },
    flt: { label: "Relaciones", main: "Principal", all: "Todas", required: "Obligatorias", optional: "Opcionales", event: "Eventos", gen: "Generación", ext: "Externas", struct: "Estructura", ref: "Referencias", showing: "Mostrando" },
    capList: "Capacidades", capListHide: "◂ Ocultar lista", capListShow: "▸ Capacidades", elList: "Elementos", defList: "Mecanismos", pickEl: "Selecciona un elemento del catálogo.", pickDef: "Selecciona un mecanismo.", noVariantFacets: "Sin ficha ampliada",
    facetT: "Ficha", variantsOf: "Variantes y tipos"
  });
  addUI("en", {
    td: { what: "What it is", does: "What it does", why: "Why it exists", resp: "Responsibility", where: "Where it takes part", recv: "Receives", prod: "Produces", calls: "What it calls / depends on", usedBy: "Used by", fx: "Side effects", rel: "Why it matters in HYDRA", rat: "Design rationale", ratProven: "Proven in the code", ratObs: "Architectural role observable in the implementation",
      methods: "Important methods", excerpts: "Explained code excerpts", src: "Source evidence (file:line)", tests: "Tests that protect it", implBy: "Implemented by", none: "No technical documentation for this capability yet.",
      mWhen: "When it is called", mIn: "Input (meaning)", mOut: "Output (meaning)", mDec: "Decisions", mFail: "On failure", mNext: "Next step",
      xWhat: "What it shows", xWhy: "Why it matters", xRole: "Role in the process", xIn: "Inputs", xOut: "Outputs", xBehavior: "Behaviour", xRelated: "Related",
      tContract: "Contract verified", tWhy: "Why it matters", tProtects: "What it protects",
      traceT: "Traceability: USER → FUNCTIONAL → TECHNICAL → SOURCE → RUNTIME ROLE", cUser: "User concept", cFunc: "Functional concept", cComp: "Component", cMethod: "Method", cSrc: "Source", cRole: "Runtime role",
      mapT: "Implementation map by responsibility", mapHint: "Each column groups components by responsibility. Click one for its full explanation.", testsT: "Explained tests",
      viewList: "Catalogue", viewMap: "Map", stageT: "Stage", stageResp: "Functional responsibility", stageBy: "Implemented by", stageWhy: "Why it takes part here", stageDoes: "What the code does", stageIn: "Inputs at this point", stageOut: "Outputs", stageFx: "Side effects", stageNext: "Next technical interaction", stageFail: "On failure", kindWord: "kind" },
    pb: { restart: "Restart", prev: "Previous", play: "Play", pause: "Pause", next: "Next", speed: "Speed", opt: "Include optional steps", step: "Step", branch: "Choose the branch:", cross: "Hands over to another capability", done: "End of the run", hint: "Play the process step by step" },
    flt: { label: "Relationships", main: "Main", all: "All", required: "Required", optional: "Optional", event: "Events", gen: "Generation", ext: "External", struct: "Structure", ref: "References", showing: "Showing" },
    capList: "Capabilities", capListHide: "◂ Hide list", capListShow: "▸ Capabilities", elList: "Elements", defList: "Mechanisms", pickEl: "Select an element from the catalogue.", pickDef: "Select a mechanism.", noVariantFacets: "No extended card",
    facetT: "Card", variantsOf: "Variants and types"
  });

  // ------------------------------------------------------------------ state
  var state = { section: "overview", level: "user", lang: "es", mode: "map", cap: null, tab: "overview", proc: null, node: null, mapSel: null, ovSel: null, sidebar: true, legend: false, insp: true, openMech: {}, capList: true, elSel: {}, defSel: {}, compSel: {}, implView: "list", flt: {}, elQ: "" };
  var TABS = ["overview", "elements", "map", "define", "processes", "relationships", "implementation", "evidence"];
  try {
    var saved = JSON.parse(localStorage.getItem("hydra-portal-state-v3") || "{}");
    if (saved.level) state.level = saved.level;
    if (saved.lang === "en") state.lang = "en";
    if (saved.legend === true) state.legend = true;
    if (saved.insp === false) state.insp = false;
    if (saved.capList === false) state.capList = false;
    if (saved.implView === "map") state.implView = "map";
  } catch (e) {}
  try {
    var q = new URLSearchParams(location.search);
    if (q.get("level") && /^(user|functional|technical)$/.test(q.get("level"))) state.level = q.get("level");
    if (q.get("lang") === "en" || q.get("lang") === "es") state.lang = q.get("lang");
    if (q.get("section") && /^(overview|capabilities|processes|tests)$/.test(q.get("section"))) state.section = q.get("section");
    if (q.get("mode") === "archify") state.mode = "archify";
    if (q.get("capability") && HM.caps[q.get("capability")]) { state.cap = q.get("capability"); if (!q.get("section")) state.section = "capabilities"; }
    if (q.get("tab") && TABS.indexOf(q.get("tab")) >= 0) state.tab = q.get("tab");
    if (q.get("process")) { state.proc = q.get("process"); if (!q.get("section")) state.section = "processes"; }
    if (q.get("node")) state.node = q.get("node");
    state._tparams = { tn: q.get("tn"), tt: q.get("tt"), tv: q.get("tv") };
  } catch (e) {}
  function save(){ try { localStorage.setItem("hydra-portal-state-v3", JSON.stringify({ level: state.level, lang: state.lang, legend: state.legend, insp: state.insp, capList: state.capList, implView: state.implView })); } catch (e) {} }

  // ------------------------------------------------------------------ helpers
  function u(k){ var p = k.split("."), n = UI[state.lang]; for (var i = 0; i < p.length; i++) n = n && n[p[i]]; return n == null ? k : n; }
  function esc(s){ return String(s == null ? "" : s).replace(/[&<>"']/g, function(c){ return ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]; }); }
  function tx(t){ if (t == null) return ""; if (typeof t === "string") return t; return t[state.lang] || t.es || t.en || ""; }
  function $(id){ return document.getElementById(id); }
  function ovNode(id){ for (var i = 0; i < HM.overview.nodes.length; i++) if (HM.overview.nodes[i].id === id) return HM.overview.nodes[i]; return null; }
  function capName(id){ var c = HM.caps[id]; if (c && c.name) return tx(c.name); var o = ovNode(id); if (o) return tx(o.nm.functional); return id; }
  function capNameL(id){ var o = ovNode(id); return o ? tx(o.nm[state.level]) : capName(id); }
  function capShort(id){ var o = ovNode(id); return o ? tx(o.nm[state.level === "user" ? "user" : "functional"]) : capName(id); }
  function capGroup(id){ var c = HM.caps[id]; if (c) return c.group; var o = ovNode(id); return o ? o.group : "platform"; }
  function capColor(id){ return HM.groupColor[capGroup(id)] || "#7a8590"; }
  function isUser(){ return state.level === "user"; }
  function isFunc(){ return state.level === "functional"; }
  function isTech(){ return state.level === "technical"; }
  function firstSentence(s, max){
    s = String(s || "").replace(/\s+/g, " ").trim(); var m = s.match(/^(.+?[.!?])(\s|$)/), r = m ? m[1] : s;
    if (r.length > max) r = r.slice(0, max - 1).replace(/\s+\S*$/, "") + "…"; return r;
  }
  function shortName(s){ s = String(s || ""); var i = s.indexOf(" ("); return i > 8 ? s.slice(0, i) : s; }
  function reqBadge(r){ if (!r || !UI[state.lang].req[r]) return ""; return '<span class="req-badge" data-req="' + r + '" title="' + esc(u("reqHelp." + r)) + '">' + esc(u("req." + r)) + "</span>"; }
  function ul(arr){ arr = (arr || []).filter(Boolean); return arr.length ? "<ul>" + arr.map(function(x){ return "<li>" + esc(typeof x === "string" ? x : tx(x)) + "</li>"; }).join("") + "</ul>" : ""; }
  function field(label, html){ return html ? '<div class="fld"><b>' + esc(label) + "</b><div>" + html + "</div></div>" : ""; }
  function p1(t){ t = typeof t === "string" ? t : tx(t); return t ? "<p>" + esc(t) + "</p>" : ""; }
  function evRows(arr){ return (arr || []).map(function(ev){ var loc = ev.path + (ev.line ? ":" + ev.line + (ev.end_line ? "-" + ev.end_line : "") : ""); return '<div class="evidence-row">' + (ev.label ? "<strong>" + esc(ev.label) + "</strong><br>" : "") + esc(loc) + "</div>"; }).join(""); }
  function monoList(arr){ arr = (arr || []).filter(Boolean); return arr.length ? '<ul class="mono">' + arr.map(function(x){ return "<li>" + esc(x) + "</li>"; }).join("") + "</ul>" : ""; }
  function btnGo(attrs, label){ return '<button type="button" class="link" ' + attrs + ">" + esc(label) + "</button>"; }
  function capLink(id, label){ return '<button type="button" class="link" data-go-cap="' + id + '">' + esc(label || capShort(id)) + "</button>"; }
  function procById(id){ for (var i = 0; i < HM.procs.length; i++) if (HM.procs[i].id === id) return HM.procs[i]; return null; }
  function procsForElem(elId){ return HM.procs.filter(function(p){ return (p.elems || []).indexOf(elId) >= 0 || p.nodes.some(function(n){ return n.e === elId; }); }); }
  function elemById(id){ for (var c in HM.caps) { var els = HM.caps[c].elements || []; for (var i = 0; i < els.length; i++) if (els[i].id === id) return { cap: c, el: els[i] }; } return null; }
  function ensureCap(){ if (!state.cap || !HM.caps[state.cap]) state.cap = Object.keys(HM.caps)[0]; }

  // audience-specific titles / summaries ------------------------------------------------------------
  function pTitle(p){ return tx(isUser() ? p.n : isFunc() ? (p.nf || p.n) : (p.nt || p.nf || p.n)); }
  function pDesc(p){ return tx(isUser() ? p.d : isFunc() ? (p.df || p.d) : (p.dt || p.df || p.d)); }
  function nodeTitle(n){
    if (isUser() || n.t === "start" || n.t === "end") return tx(n.l);
    if (isFunc()) return tx(n.lf) || tx(n.l);
    return n.lt ? tx(n.lt) : (n.x && n.x.rt ? n.x.rt : (tx(n.lf) || tx(n.l)));
  }
  function nodeSub(n){
    if (n.t === "start" || n.t === "end") return "";
    if (isUser()) return firstSentence(tx(n.u), 74);
    if (isFunc()) return tx(n.sf) || firstSentence(tx(n.f) || tx(n.u), 74);
    if (n.st) return tx(n.st);
    if (n.lt || (n.x && n.x.rt)) return tx(n.lf) || tx(n.l);
    return (n.x && n.x.ev && n.x.ev[0] && n.x.ev[0].label) ? n.x.ev[0].label : "";
  }
  function elTitle(e){ return isUser() ? tx(e.n) : isFunc() ? (tx(e.nf) || tx(e.n)) : ((e.tech && e.tech.type) || tx(e.nf) || tx(e.n)); }

  // ------------------------------------------------------------------ legends (compact, shown inside the canvas)
  var SVGW = 'xmlns="http://www.w3.org/2000/svg"';
  function swEdge(dash, w, dbl, mark){
    return '<svg ' + SVGW + ' width="40" height="14" viewBox="0 0 40 14" aria-hidden="true">' +
      (dbl ? '<path d="M2 7H30" stroke="currentColor" stroke-width="5" opacity=".45"/><path d="M2 7H30" stroke="var(--card)" stroke-width="2"/>' : "") +
      '<path d="M2 7H30" stroke="currentColor" stroke-width="' + w + '" stroke-dasharray="' + dash + '" fill="none"/>' +
      (mark === "arrow" ? '<path d="M30 2.5L39 7L30 11.5L32 7z" fill="currentColor"/>' : mark === "dia" ? '<path d="M1 7L6 3L11 7L6 11z" fill="var(--card)" stroke="currentColor"/>' : mark === "tri" ? '<path d="M1 3L11 7L1 11z" fill="var(--card)" stroke="currentColor"/>' : mark === "sq" ? '<rect x="31" y="3" width="8" height="8" fill="currentColor"/>' : "") + "</svg>";
  }
  function swIcon(kind){ var ic = G.GraphView.ICONS[kind]; return '<svg ' + SVGW + ' width="22" height="22" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="11" fill="var(--chip)" stroke="var(--card-line)"/><g transform="translate(4,4) scale(.66)" fill="' + (ic.fill ? "currentColor" : "none") + '" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="' + ic.d + '"/></g></svg>'; }
  function swShape(kind, req){
    var dash = req === "OPTIONAL" ? "5 3" : req === "CONDITIONAL" ? "2 3" : req === "DERIVED" ? "7 3 2 3" : "";
    var s = 'fill="var(--card)" stroke="currentColor" stroke-width="1.5" stroke-dasharray="' + dash + '"', inner = "";
    if (kind === "start") inner = '<rect x="2" y="3" width="32" height="16" rx="8" fill="var(--accent)"/><path d="M11 7l7 4-7 4z" fill="var(--accent-ink)"/>';
    else if (kind === "end") inner = '<rect x="2" y="3" width="32" height="16" rx="8" fill="var(--accent)"/><rect x="5" y="6" width="26" height="10" rx="5" fill="none" stroke="var(--accent-ink)" opacity=".7"/><path d="M12 7v8M12 7h9l-1.5 2 1.5 2h-9" fill="none" stroke="var(--accent-ink)"/>';
    else if (kind === "decision") inner = '<path d="M18 1L34 11L18 21L2 11z" fill="var(--decision)" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>';
    else if (kind === "artifact") inner = '<path d="M4 3H23L32 12V19H4z" fill="var(--artifact)" stroke="currentColor" stroke-width="1.5"/><path d="M23 3V12H32" fill="none" stroke="currentColor"/>';
    else if (kind === "alt") inner = '<rect x="2" y="3" width="32" height="16" rx="5" ' + s + '/><rect x="5" y="6" width="26" height="10" rx="3" fill="none" stroke="currentColor" opacity=".7"/>';
    else if (kind === "rep") inner = '<rect x="7" y="1" width="27" height="15" rx="5" fill="var(--chip)" stroke="currentColor"/><rect x="2" y="5" width="27" height="15" rx="5" ' + s + "/>";
    else if (kind === "ext") inner = '<rect x="2" y="3" width="32" height="16" rx="5" fill="var(--card)" stroke="currentColor" stroke-width="1.5" stroke-dasharray="5 3"/><path d="M6 17L14 5M12 17L20 5M18 17L26 5M24 17L32 5" stroke="currentColor" opacity=".25"/>';
    else if (kind === "gen") inner = '<rect x="2" y="3" width="32" height="16" rx="5" fill="var(--artifact)" stroke="currentColor" stroke-width="1.5" stroke-dasharray="7 3 2 3"/>';
    else if (kind === "variant") inner = '<rect x="2" y="3" width="32" height="16" rx="8" fill="var(--chip)" stroke="currentColor" stroke-width="1.5"/>';
    else inner = '<rect x="2" y="3" width="32" height="16" rx="5" ' + s + '/><rect x="2" y="3" width="32" height="3.5" rx="1.5" fill="var(--accent)" stroke="none"/>';
    return '<svg ' + SVGW + ' width="38" height="22" viewBox="0 0 38 22" aria-hidden="true">' + inner + "</svg>";
  }
  function legendItem(sw, t){ return '<span class="lg">' + sw + "<span>" + esc(t) + "</span></span>"; }
  function legendHtml(kind){
    var L = state.lang === "es";
    if (kind === "process") {
      return '<div class="lg-grid">' +
        '<div class="lg-col"><div class="lg-h">' + (L ? "Nodos" : "Nodes") + "</div>" +
        legendItem(swShape("start"), L ? "Inicio" : "Start") + legendItem(swShape("end"), L ? "Fin" : "End") +
        legendItem(swIcon("user"), L ? "Acción tuya" : "Your action") + legendItem(swIcon("auto"), L ? "Acción automática de HYDRA" : "Automatic HYDRA action") + legendItem(swShape("ext"), L ? "Sistema externo (rayado)" : "External system (hatched)") +
        legendItem(swShape("decision"), L ? "Decisión (ramas con etiqueta)" : "Decision (labelled branches)") + legendItem(swShape("artifact"), L ? "Resultado / artefacto" : "Result / artifact") + "</div>" +
        '<div class="lg-col"><div class="lg-h">' + (L ? "Obligatoriedad" : "Requiredness") + "</div>" +
        legendItem(swShape("step", "MANDATORY"), L ? "Obligatorio (borde continuo)" : "Mandatory (solid border)") + legendItem(swShape("step", "OPTIONAL"), L ? "Opcional (discontinuo + etiqueta)" : "Optional (dashed + tag)") +
        legendItem(swShape("step", "CONDITIONAL"), L ? "Condicional (punteado + etiqueta)" : "Conditional (dotted + tag)") + legendItem(swShape("alt"), L ? "Alternativa (doble borde)" : "Alternative (double border)") + legendItem(swShape("rep"), L ? "Repetible (apilado)" : "Repeatable (stacked)") + "</div>" +
        '<div class="lg-col"><div class="lg-h">' + (L ? "Conexiones" : "Connections") + "</div>" +
        legendItem(swEdge("", 1.9, false, "arrow"), L ? "Siguiente" : "Next") + legendItem(swEdge("7 5", 1.7, false, "arrow"), L ? "Opcional" : "Optional") + legendItem(swEdge("2 5", 2.4, false, "arrow"), L ? "Condicional (?)" : "Conditional (?)") +
        legendItem(swEdge("", 1.9, true, "arrow"), L ? "Alternativa (alt)" : "Alternative (alt)") + legendItem('<span class="glyph">↻</span>' + swEdge("6 4", 1.9, false, "arrow"), L ? "Repetir / revalidar" : "Repeat / revalidate") +
        legendItem(swEdge("", 2.8, false, "arrow") + '<span class="glyph">»</span>', L ? "Traspaso a otra capacidad" : "Hand-over to another capability") + legendItem(swEdge("9 3 2 3", 1.9, false, "arrow"), L ? "Por evento (evt)" : "Event-driven (evt)") + "</div>" +
        '<div class="lg-col"><div class="lg-h">' + (L ? "Evidencia de pruebas" : "Test evidence") + "</div>" + legendItem('<span class="evb-sw ok">✓</span>', L ? "Evidencia automatizada disponible" : "Automated evidence available") + legendItem('<span class="evb-sw pa">◐</span>', L ? "Evidencia automatizada parcial" : "Partial automated evidence") + legendItem('<span class="evb-sw st">⚠</span>', L ? "Evidencia obsoleta" : "Stale evidence") + legendItem('<span class="evb-sw no">○</span>', L ? "Sin evidencia directa mapeada (no implica fallo)" : "No direct evidence mapped (not a failure)") + "</div></div>";
    }
    if (kind === "map") {
      return '<div class="lg-grid"><div class="lg-col"><div class="lg-h">' + (L ? "Elementos" : "Elements") + "</div>" +
        legendItem(swShape("concept", "MANDATORY"), L ? "Concepto" : "Concept") + legendItem(swShape("variant"), L ? "Variante" : "Variant") + legendItem(swShape("gen"), L ? "Generado / derivado" : "Generated / derived") + legendItem(swShape("ext"), L ? "Externo" : "External") +
        legendItem(swShape("concept", "OPTIONAL"), L ? "Opcional (discontinuo)" : "Optional (dashed)") + legendItem(swShape("concept", "CONDITIONAL"), L ? "Condicional (punteado)" : "Conditional (dotted)") + "</div>" +
        '<div class="lg-col"><div class="lg-h">' + (L ? "Relaciones" : "Relations") + "</div>" +
        legendItem(swEdge("", 1.9, false, "dia"), L ? "contiene" : "contains") + legendItem(swEdge("", 1.6, false, "arrow"), L ? "referencia" : "references") + legendItem(swEdge("", 1.6, false, "tri"), L ? "es un tipo de" : "is a kind of") + legendItem(swEdge("5 4", 1.6, false, "arrow"), L ? "genera / deriva" : "generates / derives") +
        '<span class="lg"><span class="mono">1..*</span><span>' + (L ? "cardinalidad" : "cardinality") + "</span></span></div></div>";
    }
    if (kind === "impl") {
      var gs = [["def", "Definición / configuración", "Definition / configuration"], ["app", "Aplicación / orquestación", "Application / orchestration"], ["dom", "Dominio / semántica", "Domain / semantics"], ["rt", "Runtime / ejecución", "Runtime / execution"], ["adp", "Adaptador / integración", "Adapter / integration"], ["per", "Persistencia", "Persistence"], ["api", "API", "API"], ["ui", "Frontend", "Frontend"]];
      return '<div class="lg-grid"><div class="lg-col"><div class="lg-h">' + (L ? "Responsabilidad (color)" : "Responsibility (colour)") + "</div>" + gs.map(function(g){ return legendItem('<span class="sw-dot" style="background:' + GROUP_COLOR[g[0]] + '"></span>', L ? g[1] : g[2]); }).join("") + "</div>" +
        '<div class="lg-col"><div class="lg-h">' + (L ? "Conexiones" : "Connections") + "</div>" + legendItem(swEdge("", 1.6, false, "arrow"), L ? "llama a / depende de" : "calls / depends on") + legendItem(swShape("concept", "MANDATORY"), L ? "Servicio, runtime, controlador…" : "Service, runtime, controller…") + legendItem(swShape("variant"), L ? "Registro, esquema, enumeración" : "Record, schema, enum") + "</div></div>";
    }
    var t = ["required", "optional", "conditional", "alternative", "event", "producer", "external"];
    var sw = { required: swEdge("", 2.3, false, "arrow"), optional: swEdge("7 5", 1.7, false, "arrow"), conditional: swEdge("2 5", 2.4, false, "arrow"), alternative: swEdge("", 1.9, true, "arrow"), event: swEdge("9 3 2 3", 1.9, false, "arrow"), producer: swEdge("", 1.9, false, "arrow"), external: swEdge("3 4", 1.6, false, "sq") };
    return '<div class="lg-grid"><div class="lg-col"><div class="lg-h">' + esc(u("legendOv")) + "</div>" + t.map(function(k){ return legendItem(sw[k], u("relType." + k) + " — " + u("relTypeHelp." + k)); }).join("") + "</div></div>";
  }

  // ------------------------------------------------------------------ graph builders (audience projections of ONE semantic model)
  function vis(x){ return !x.lv || x.lv.indexOf(state.level) >= 0; }
  function collapse(nodes, edges){
    var hidden = {}; nodes.forEach(function(n){ if (!vis(n)) hidden[n.id] = 1; });
    var out = edges.filter(function(e){ return vis(e); }).map(function(e){ return { a: e.a, b: e.b, kind: e.kind, l: e.l }; });
    Object.keys(hidden).forEach(function(h){
      var ins = out.filter(function(e){ return e.b === h; }), outs = out.filter(function(e){ return e.a === h; });
      out = out.filter(function(e){ return e.a !== h && e.b !== h; });
      ins.forEach(function(i){ outs.forEach(function(o){ if (i.a !== o.b) out.push({ a: i.a, b: o.b, kind: i.kind === "next" ? o.kind : i.kind, l: o.l || i.l }); }); });
    });
    return { nodes: nodes.filter(vis), edges: out };
  }
  function procGraph(p){
    var c = collapse(p.nodes, p.edges), byId = {}, cross = p.scope === "cross", crossIn = {};
    p.nodes.forEach(function(n){ byId[n.id] = n; });
    c.edges.forEach(function(e){ if (byId[e.a] && byId[e.b] && byId[e.a].cap !== byId[e.b].cap) crossIn[e.b] = 1; });
    var nodes = c.nodes.map(function(n){
      var t = n.t || "step", fam = t === "step" ? (n.a === "ext" ? "external" : "step") : t;
      var showCap = t !== "start" && t !== "end" && (cross || n.cap !== p.cap);
      var tag = (t === "step" || t === "artifact" || t === "decision") && n.r && n.r !== "MANDATORY" ? u("reqTag." + n.r) : "";
      var kl = t === "step" ? u("kindLabel." + (n.a || "step")) : u("kindLabel." + t);
      return { id: n.id, shape: fam, label: nodeTitle(n), sub: nodeSub(n), capLabel: showCap ? capShort(n.cap) : "", kindLabel: kl, color: capColor(n.cap), cap: n.cap, evid: (t !== "start" && t !== "end" && window.HydraTestsUI) ? window.HydraTestsUI.stageState(p.id, n.id) : null, actor: n.a || "", req: n.r || "", tag: tag, xmark: crossIn[n.id] ? "»" : "" };
    });
    var edges = c.edges.map(function(e){
      var a = byId[e.a], b = byId[e.b], kind = e.kind || "next", label = tx(e.l);
      if (kind === "next" && a && b && a.cap !== b.cap) kind = "x";
      return { a: e.a, b: e.b, kind: kind, label: label };
    });
    return { nodes: nodes, edges: edges };
  }
  function mapNodeParts(n){
    var ei = n.elem ? elemById(n.elem) : null, e = ei && ei.el;
    var title = isUser() ? tx(n.n) : isFunc() ? shortName(e && e.nf ? tx(e.nf) : tx(n.n)) : ((e && e.tech && e.tech.type) || tx(n.n));
    var sub = isUser() ? firstSentence(tx(n.d), 70) : isFunc() ? (e ? (e.card || firstSentence(tx(e.fm), 70)) : firstSentence(tx(n.d), 70)) : (e ? firstSentence(tx(e.tm), 70) : "");
    return { title: title, sub: sub };
  }
  var MAPF = { all: null, struct: ["contains", "isa"], ref: ["ref"], gen: ["gen"] };
  function mapFilters(c){
    var kinds = {}; ((c.map || {}).edges || []).forEach(function(e){ kinds[e.rel] = 1; });
    var list = ["all"]; if (kinds.contains || kinds.isa) list.push("struct"); if (kinds.ref) list.push("ref"); if (kinds.gen) list.push("gen");
    return { key: "map:" + c.id, list: list, def: (c.map.edges.length > 34 && list.indexOf("struct") >= 0) ? "struct" : "all", data: function(f){ return mapGraph(c, f); } };
  }
  function mapGraph(c, f){
    var m = c.map || { nodes: [], edges: [] };
    if (f && MAPF[f]) { var keepK = MAPF[f], ed = m.edges.filter(function(e){ return keepK.indexOf(e.rel) >= 0; }), tc = {}; ed.forEach(function(e){ tc[e.a] = tc[e.b] = 1; }); m = { nodes: m.nodes.filter(function(n){ return tc[n.id]; }), edges: ed }; }
    var cn = collapse(m.nodes, m.edges.map(function(e){ return { a: e.a, b: e.b, kind: e.rel, l: e.l, lv: e.lv, req: e.req, card1: e.card1, card2: e.card2, rel: e.rel }; }));
    var raw = {}; m.edges.forEach(function(e){ raw[e.a + ">" + e.b + ">" + e.rel] = e; });
    var nodes = cn.nodes.map(function(n){
      var k = n.k || "concept", fam = k === "variant" ? "variant" : k === "derived" ? "derived" : k === "external" ? "external" : "concept", part = mapNodeParts(n);
      var req = n.req === "OPTIONAL" || n.req === "CONDITIONAL" || n.req === "DERIVED" ? n.req : (k === "derived" ? "DERIVED" : "");
      return { id: n.id, shape: fam, label: part.title, sub: part.sub, kindLabel: u("mapNode." + k), color: capColor(c.id), req: req, tag: n.req && n.req !== "MANDATORY" ? u("reqTag." + n.req) : "" };
    });
    var edges = cn.edges.map(function(e){
      var r = raw[e.a + ">" + e.b + ">" + e.kind] || {}, kind = e.kind === "contains" || e.kind === "ref" || e.kind === "isa" || e.kind === "gen" ? e.kind : "ref";
      return { a: e.a, b: e.b, kind: kind, label: tx(e.l) + (r.req && r.req !== "MANDATORY" ? (e.l ? " · " : "") + u("reqTag." + r.req) : ""), card1: r.card1 || "", card2: r.card2 || "" };
    });
    return { nodes: nodes, edges: edges, family: "map" };
  }
  var OV_KIND = { required: "req", optional: "opt", conditional: "cond", alternative: "alt", event: "event", producer: "prod", external: "ext" };
  var OVF = { main: ["required", "producer"], all: null, required: ["required"], optional: ["optional", "conditional", "alternative"], event: ["event"], gen: ["producer"], ext: ["external"] };
  var OV_FILTERS = ["main", "all", "required", "optional", "event", "gen", "ext"];
  function overviewGraph(f){
    var Lv = state.level, ids = {}, allow = f && OVF[f] ? OVF[f] : null;
    var nodes = HM.overview.nodes.filter(function(n){ return n.lv.indexOf(Lv) >= 0; }).map(function(n){
      ids[n.id] = 1;
      return { id: n.id, shape: n.group === "external" ? "external" : "cap", label: tx(n.nm[Lv]), sub: tx(n.sb[Lv]), kindLabel: tx(HM.groups[n.group]), color: HM.groupColor[n.group], x: n.x * 1.12 + 20, y: n.y * 1.18 + 30, tag: "" };
    });
    var edges = HM.overview.edges.filter(function(e){ return e.lv.indexOf(Lv) >= 0 && ids[e.a] && ids[e.b] && (!allow || allow.indexOf(e.type) >= 0); }).map(function(e){
      return { a: e.a, b: e.b, kind: OV_KIND[e.type], label: tx(e.l[Lv]), bend: e.bend || 0, type: e.type };
    });
    if (allow) { var touched = {}; edges.forEach(function(e){ touched[e.a] = touched[e.b] = 1; }); nodes = nodes.filter(function(n){ return touched[n.id]; }); }
    return { nodes: nodes, edges: edges, family: "overview", hideLabels: true };
  }

  // ------------------------------------------------------------------ inspectors (non-cumulative: each audience gets its own content)
  function inspHead(kicker, title, badges){ return '<div class="insp-head"><div class="insp-kicker">' + esc(kicker) + "</div><h3>" + esc(title) + '</h3><div class="insp-badges">' + (badges || "") + "</div></div>"; }
  function stepNames(p, ids, edges){ return ids; }
  function nodeInspector(p, id){
    var h = nodeInspectorBase(p, id), nd = null; p.nodes.forEach(function(x){ if (x.id === id) nd = x; });
    if (!window.HydraTestsUI || !nd || nd.t === "start" || nd.t === "end") return h;
    var ev = window.HydraTestsUI.stageInspector(p.id, id);
    return ev ? h.replace(/<\/div>$/, function(){ return ev + "</div>"; }) : h;
  }
  function nodeInspectorBase(p, id){
    var n = null; p.nodes.forEach(function(x){ if (x.id === id) n = x; });
    if (!n) return '<p class="empty">' + esc(u("noNode")) + "</p>";
    var t = n.t || "step", byId = {}; p.nodes.forEach(function(x){ byId[x.id] = x; });
    var outs = p.edges.filter(function(e){ return e.a === n.id; }), ins = p.edges.filter(function(e){ return e.b === n.id; });
    var kicker = u("kindLabel." + (t === "step" ? (n.a || "step") : t)) + (n.cap && t !== "start" && t !== "end" ? " · " + capShort(n.cap) : "");
    var badges = (n.r ? reqBadge(n.r) : "") + (n.a ? '<span class="actor-badge" data-actor="' + n.a + '">' + esc(u("actor." + n.a)) + "</span>" : "");
    var h = "", title = nodeTitle(n);
    function flowList(list, dirNext){
      return "<ul>" + list.map(function(e){ var o = byId[dirNext ? e.b : e.a]; return "<li>" + btnGo('data-sel-node="' + o.id + '"', nodeTitle(o)) + (dirNext ? ' <span class="dim">(' + esc(u("nextEdge." + (e.kind || "next"))) + (e.l ? ": " + esc(tx(e.l)) : "") + ")</span>" : "") + "</li>"; }).join("") + "</ul>";
    }
    if (isUser()) {
      h += inspHead(kicker, title, badges) + '<div class="insp-body">';
      var uText = n.u ? tx(n.u) : (t === "start" ? tx(p.d) : (t === "end" ? u("endDefault") : ""));
      h += field(u("why2"), p1(uText)) + field(u("needs2"), p1(n.need)) + field(u("produces2"), p1(n.out));
      h += field(u("optHint." + (n.r || "MANDATORY")) ? u("req." + (n.r || "MANDATORY")) : "", p1(u("optHint." + (n.r || "MANDATORY"))));
      if (outs.length) h += field(u("next"), flowList(outs, true));
    } else if (isFunc()) {
      h += inspHead(kicker, title, badges) + '<div class="insp-body">';
      h += field(u("fSummary"), p1(n.sf) || p1(firstSentence(tx(n.f), 120)));
      h += field(u("fIo"), p1(n.fio));
      h += field(u("fCond"), p1(n.f)) + field(u("fState"), p1(n.s));
      if (n.e) { var ei = elemById(n.e); if (ei) h += field(u("fConcept"), esc(elTitle(ei.el)) + "<br>" + btnGo('data-go-el="' + n.e + '" data-go-cap="' + ei.cap + '"', u("viewInElements")) + " · " + btnGo('data-go-map="' + n.e + '" data-go-cap="' + ei.cap + '"', u("viewInMap"))); }
      h += field(u("fOwner"), capLink(n.cap));
      if (ins.length) h += field(u("fFrom"), flowList(ins, false));
      if (outs.length) h += field(u("fTo"), flowList(outs, true));
      h += field(u("req." + (n.r || "MANDATORY")), p1(u("reqHelp." + (n.r || "MANDATORY"))));
    } else {
      var x = n.x || {}, tp = HM.tech.procs[p.id] && HM.tech.procs[p.id][n.id], cf = tp && tp.tc ? compFind(tp.tc, n.cap) : null;
      h += inspHead(kicker, title, badges) + '<div class="insp-body">';
      if (cf) {
        var cc = cf.comp;
        h += field(u("td.stageResp"), p1(n.lf || n.l));
        h += field(u("td.stageBy"), btnGo('data-go-comp="' + cf.cap + ":" + cc.id + '"', cc.n) + ' <span class="kind-tag">' + esc(cc.kind) + "</span>" + (tp.tm ? ' <code class="mono">' + esc(tp.tm) + "()</code>" : "") + p1(cc.what));
        h += field(u("td.stageWhy"), p1(tp.tw || cc.why)) + field(u("td.stageDoes"), p1(cc.does));
        h += field(u("td.stageIn"), p1(tp.ti || cc.recv)) + field(u("td.stageOut"), p1(tp.to || cc.prod)) + field(u("td.stageFx"), p1(tp.te || cc.fx));
        h += field(u("td.stageNext"), p1(tp.tn)) + field(u("td.stageFail"), p1(tp.tf));
        if (x.test && x.test.length) h += field(u("tTests"), monoList(x.test));
        if ((x.ev || []).length || (cc.src || []).length) h += '<details class="drill"><summary>' + esc(u("td.src")) + "</summary>" + evRows(x.ev && x.ev.length ? x.ev : cc.src) + "</details>";
        h += field(u("fOwner"), capLink(n.cap));
        if (outs.length) h += field(u("fTo"), flowList(outs, true));
        return h + "</div>";
      }
      h += field(u("tImplements"), p1(n.lf || n.l));
      h += field(u("tImpl"), p1(n.tx));
      h += field(u("tRuntime"), x.rt ? '<code class="mono">' + esc(x.rt) + "</code>" : "");
      h += field(u("tTests"), monoList(x.test)) + field(u("tEvidence"), evRows(x.ev));
      if (!x.rt && !(x.ev || []).length && !n.tx) {
        var oc = HM.caps[n.cap], ot = oc && oc.tech;
        if (ot) h += field(u("tOwnerRuntime"), p1(ot.runtime) + monoList((ot.services || []).slice(0, 6)) + '<p class="dim">' + esc(u("tOwnerRuntimeNote")) + "</p>");
      }
      h += field(u("fOwner"), capLink(n.cap));
      if (outs.length) h += field(u("fTo"), flowList(outs, true));
    }
    return h + "</div>";
  }
  function variantsHtml(e){
    var hasF = e.variants.some(function(v){ return (v.facets || []).length; });
    function facets(v){
      var fs = (v.facets || []).filter(function(f){ return !f.a || f.a.indexOf(state.level) >= 0; });
      return fs.length ? '<dl class="vc-f">' + fs.map(function(f){ return "<dt>" + esc(tx(f.n)) + "</dt><dd>" + esc(tx(f.v)) + "</dd>"; }).join("") + "</dl>" : "";
    }
    if (hasF || e.variants.length > 6) return '<div class="vgrid">' + e.variants.map(function(v){
      var head = (v.req ? reqBadge(v.req) : "") + "<b>" + esc(tx(v.n)) + "</b>" + (!isUser() && v.k ? ' <code class="mono">' + esc(v.k) + "</code>" : "");
      var d = isUser() ? tx(v.d) : isFunc() ? (tx(v.fd) || tx(v.d)) : (tx(v.td) || tx(v.fd) || tx(v.d));
      return '<div class="vcard"><div class="vc-h">' + head + "</div>" + (d ? "<p>" + esc(d) + "</p>" : "") + facets(v) + "</div>";
    }).join("") + "</div>";
    return '<ul class="variants">' + e.variants.map(function(v){
      if (isUser()) return "<li>" + (v.req ? reqBadge(v.req) : "") + "<b>" + esc(tx(v.n)) + "</b> — " + esc(tx(v.d)) + "</li>";
      if (isFunc()) return "<li>" + (v.req ? reqBadge(v.req) : "") + "<b>" + esc(tx(v.n)) + "</b>" + (v.k ? ' <code class="mono">' + esc(v.k) + "</code>" : "") + " — " + esc(tx(v.fd) || tx(v.d)) + "</li>";
      return "<li>" + (v.k ? '<code class="mono">' + esc(v.k) + "</code> " : "<b>" + esc(tx(v.n)) + "</b> ") + (tx(v.td) ? "— " + esc(tx(v.td)) : "") + "</li>";
    }).join("") + "</ul>";
  }

  // ------------------------------------------------------------------ technical documentation (HM.techdoc) rendering
  var GROUP_COLOR = { def: "#4a90c9", app: "#8a5fc9", dom: "#1f8a6f", rt: "#c9994a", adp: "#2aa7a0", per: "#7a8590", api: "#c25555", ui: "#5a7ac9", val: "#8a5fc9", prj: "#7a8590" };
  function tdCap(capId){ return HM.tech && HM.tech.caps[capId] || null; }
  function compFind(id, cap){
    var d = tdCap(cap), i;
    if (d) for (i = 0; i < d.comps.length; i++) if (d.comps[i].id === id) return { cap: cap, comp: d.comps[i] };
    for (var c in HM.tech.caps) { var dd = HM.tech.caps[c]; for (i = 0; i < dd.comps.length; i++) if (dd.comps[i].id === id) return { cap: c, comp: dd.comps[i] }; }
    return null;
  }
  function compRef(r, cap){
    if (typeof r === "string") { var f = compFind(r, cap); return f ? btnGo('data-go-comp="' + f.cap + ":" + f.comp.id + '"', f.comp.n) : esc(r); }
    return '<span class="mono">' + esc(r.n) + "</span>" + (r.d ? ' <span class="dim">— ' + esc(tx(r.d)) + "</span>" : "");
  }
  function compRefs(arr, cap){ arr = arr || []; return arr.length ? '<ul class="refs">' + arr.map(function(r){ return "<li>" + compRef(r, cap) + "</li>"; }).join("") + "</ul>" : ""; }
  function srcLoc(s){ return s.path + (s.line ? ":" + s.line + (s.end_line ? "-" + s.end_line : "") : ""); }
  function testCard(t){
    return '<div class="tcard"><div class="tc-h"><b>' + esc(t.n) + '</b> <span class="mono dim">' + esc(t.path + (t.line ? ":" + t.line : "")) + "</span></div>" +
      field(u("td.tContract"), p1(t.contract)) + field(u("td.tWhy"), p1(t.why)) + field(u("td.tProtects"), p1(t.protects)) + "</div>";
  }
  function compCard(cap, c){
    var d = tdCap(cap), rat = c.rat || {}, h = '<article class="comp" id="comp-' + esc(c.id) + '"><h4>' + esc(c.n) + ' <span class="kind-tag">' + esc(c.kind) + "</span></h4>";
    h += '<p class="lead">' + esc(tx(c.what)) + "</p>";
    h += '<div class="comp-grid">' + field(u("td.does"), p1(c.does)) + field(u("td.why"), p1(c.why)) + field(u("td.resp"), p1(c.resp)) + field(u("td.where"), p1(c.where)) +
      field(u("td.recv"), p1(c.recv)) + field(u("td.prod"), p1(c.prod)) + field(u("td.calls"), compRefs(c.calls, cap)) + field(u("td.usedBy"), compRefs(c.usedBy, cap)) +
      field(u("td.fx"), p1(c.fx)) + field(u("td.rel"), p1(c.rel)) + "</div>";
    if (rat.t) h += field(u("td.rat"), '<span class="rat-tag" data-k="' + esc(rat.k) + '">' + esc(rat.k === "proven" ? u("td.ratProven") : u("td.ratObs")) + "</span>" + p1(rat.t) + ((rat.src || []).length ? evRows(rat.src) : ""));
    var els = (c.elems || []).map(function(id){ var ei = elemById(id); return ei ? btnGo('data-go-el="' + id + '" data-go-cap="' + ei.cap + '"', elTitle(ei.el)) : ""; }).filter(Boolean);
    if (els.length) h += field(u("authoredBy"), els.join(" "));
    if ((c.methods || []).length) h += field(u("td.methods"), c.methods.map(function(m){
      return '<details class="drill"><summary><code class="mono">' + esc(m.n) + "()</code> — " + esc(firstSentence(tx(m.purpose), 110)) + '</summary><div class="comp-grid">' + field(u("td.does"), p1(m.purpose)) + field(u("td.mWhen"), p1(m.when)) + field(u("td.mIn"), p1(m.in)) + field(u("td.mOut"), p1(m.out)) +
        field(u("td.mDec"), p1(m.decisions)) + field(u("td.fx"), p1(m.fx)) + field(u("td.mFail"), p1(m.fail)) + field(u("td.mNext"), p1(m.next)) + "</div>" + ((m.src || []).length ? evRows(m.src) : "") + "</details>";
    }).join(""));
    if ((c.excerpts || []).length) h += field(u("td.excerpts"), c.excerpts.map(function(x){
      return '<details class="drill"><summary>' + esc(firstSentence(tx(x.what), 120)) + ' <span class="mono dim">' + esc(x.path + ":" + x.from + "-" + x.to) + '</span></summary><div class="comp-grid">' + field(u("td.xWhat"), p1(x.what)) + field(u("td.xWhy"), p1(x.why)) + field(u("td.xRole"), p1(x.role)) +
        field(u("td.xIn"), p1(x.in)) + field(u("td.xOut"), p1(x.out)) + field(u("td.xBehavior"), p1(x.behavior)) + '</div><pre class="code">' + esc(x.code) + "</pre>" + ((x.related || []).length ? field(u("td.xRelated"), compRefs(x.related, cap)) : "") + "</details>";
    }).join(""));
    var tests = (c.tests || []).map(function(id){ return d && d.tests.filter(function(t){ return t.id === id; })[0]; }).filter(Boolean);
    if (tests.length) h += field(u("td.tests"), tests.map(testCard).join(""));
    if ((c.src || []).length) h += '<details class="drill"><summary>' + esc(u("td.src")) + "</summary>" + c.src.map(function(s){ return '<div class="evidence-row">' + (s.label ? "<strong>" + esc(s.label) + "</strong><br>" : "") + esc(srcLoc(s)) + "</div>"; }).join("") + "</details>";
    return h + "</article>";
  }
  function traceTable(cap){
    var d = tdCap(cap); if (!d || !d.trace.length) return "";
    var cols = ["cUser", "cFunc", "cComp", "cMethod", "cSrc", "cRole"].map(function(k){ return "<th>" + esc(u("td." + k)) + "</th>"; }).join("");
    return '<div class="lvl-card span-all"><h4>' + esc(u("td.traceT")) + '</h4><div class="tbl-wrap"><table class="tbl trace"><thead><tr>' + cols + "</tr></thead><tbody>" + d.trace.map(function(r){
      var f = compFind(r.comp, cap);
      return "<tr><td>" + esc(tx(r.user)) + "</td><td>" + esc(tx(r.func)) + "</td><td>" + (f ? btnGo('data-go-comp="' + f.cap + ":" + f.comp.id + '"', f.comp.n) : esc(r.comp)) + '</td><td class="mono">' + esc(r.method || "") + '</td><td class="mono dim">' + esc(r.src ? srcLoc(r.src) : "") + "</td><td>" + esc(tx(r.role)) + "</td></tr>";
    }).join("") + "</tbody></table></div></div>";
  }
  function implMapHtml(cap){
    var d = tdCap(cap); if (!d) return "";
    var cols = d.groups.map(function(g){
      var cs = d.comps.filter(function(c){ return c.g === g.id; }); if (!cs.length) return "";
      return '<div class="im-col" style="border-top:4px solid ' + (GROUP_COLOR[g.id] || "#7a8590") + '"><div class="im-h">' + esc(tx(g.n)) + '</div><p class="dim im-d">' + esc(tx(g.d)) + "</p>" + cs.map(function(c){
        return '<button type="button" class="im-comp" data-go-comp="' + cap + ":" + c.id + '"><b>' + esc(c.n) + '</b> <span class="kind-tag">' + esc(c.kind) + "</span><span>" + esc(firstSentence(tx(c.what), 96)) + "</span></button>";
      }).join("") + "</div>";
    }).join("");
    return '<div class="lvl-card span-all"><h4>' + esc(u("td.mapT")) + '</h4><p class="dim">' + esc(u("td.mapHint")) + '</p><div class="im-grid">' + cols + "</div></div>";
  }
  function techTests(cap){
    var d = tdCap(cap); if (!d || !d.tests.length) return "";
    return '<div class="lvl-card span-all"><h4>' + esc(u("td.testsT")) + '</h4><div class="tgrid">' + d.tests.map(testCard).join("") + "</div></div>";
  }
  function compsOfElem(cap, elId){ var d = tdCap(cap); return d ? d.comps.filter(function(c){ return (c.elems || []).indexOf(elId) >= 0; }) : []; }
  function elementBody(capId, e, withLinks){
    var h = "";
    if (isUser()) {
      h += '<p class="lead">' + esc(tx(e.mean)) + "</p>" + field(u("purpose"), p1(e.purpose)) + field(u("whenUsed"), p1(e.when));
      if ((e.variants || []).length) h += field(u("variants"), variantsHtml(e));
      if ((e.props || []).length) h += field(u("props"), '<ul class="variants">' + e.props.map(function(p){ return "<li>" + reqBadge(p.req) + "<b>" + esc(tx(p.n)) + "</b> — " + esc(tx(p.d)) + "</li>"; }).join("") + "</ul>");
    } else if (isFunc()) {
      var f = e.func || {};
      h += '<p class="lead">' + esc(tx(e.fm) || tx(e.mean)) + "</p>";
      h += field(u("card"), e.card ? '<span class="mono">' + esc(e.card) + "</span>" : "");
      if (e.io) h += field(u("fIo"), p1(tx(e.io.i) + " → " + tx(e.io.o)));
      if ((e.variants || []).length) h += field(u("variants"), variantsHtml(e));
      h += field(u("fRel"), ul(f.rel)) + field(u("lifecycleOf"), p1(f.lifecycle)) + field(u("condition"), p1(f.cond)) + field(u("effect"), p1(e.effect));
      if ((e.props || []).length) h += field(u("props"), '<ul class="variants">' + e.props.map(function(p){ return "<li>" + reqBadge(p.req) + "<b>" + esc(tx(p.n)) + "</b></li>"; }).join("") + "</ul>");
    } else {
      h += '<p class="lead">' + esc(tx(e.tm) || "") + "</p>";
      h += field(u("techType"), e.tech && e.tech.type ? '<code class="mono">' + esc(e.tech.type) + "</code>" + (e.tech.schema ? '<div class="dim mono">' + esc(e.tech.schema) + "</div>" : "") : "");
      if ((e.variants || []).length) h += field(u("variants"), variantsHtml(e));
      var impl = compsOfElem(capId, e.id);
      if (impl.length) h += field(u("td.implBy"), '<ul class="refs">' + impl.map(function(c){ return "<li>" + btnGo('data-go-comp="' + capId + ":" + c.id + '"', c.n) + ' <span class="kind-tag">' + esc(c.kind) + "</span> — " + esc(firstSentence(tx(c.what), 110)) + "</li>"; }).join("") + "</ul>");
      if (e.tech && (e.tech.evidence || []).length) h += '<details class="drill"><summary>' + esc(u("td.src")) + "</summary>" + evRows(e.tech.evidence) + "</details>";
    }
    var c = HM.caps[capId], mechs = (c && c.define || []).filter(function(d){ return (d.elems || []).indexOf(e.id) >= 0; });
    if (mechs.length) h += field(u("authoredBy"), mechs.map(function(d){ return btnGo('data-go-define="' + d.id + '" data-go-cap="' + capId + '"', tx(d.n)); }).join(" "));
    var ps = procsForElem(e.id);
    if (ps.length) h += field(u("participates"), ps.map(function(p){ return btnGo('data-go-proc="' + p.id + '"', pTitle(p)); }).join(" "));
    if (withLinks) h += '<div class="insp-actions">' + btnGo('data-go-el="' + e.id + '" data-go-cap="' + capId + '"', u("viewInElements")) + "</div>";
    return h;
  }

  // ------------------------------------------------------------------ reusable graph panel (canvas + inspector + legend + toolbar)
  var live = [];
  function tbLabels(){ return { fit: u("tb.fit"), fitT: u("tb.fitT"), out: u("tb.out"), "in": u("tb.in"), reset: u("tb.reset"), focus: u("tb.focus"), focusT: u("tb.focusT") }; }
  function mountGraphPanel(root, cfg){
    root.innerHTML = '<div class="gv-top"></div><div class="gv-layout' + (state.insp ? "" : " insp-collapsed") + '"><div class="gv-canvas"></div><aside class="insp" aria-live="polite"></aside></div>';
    var layoutEl = root.querySelector(".gv-layout"), canvas = root.querySelector(".gv-canvas"), insp = root.querySelector(".insp"), top = root.querySelector(".gv-top");
    insp.innerHTML = cfg.empty;
    var view = new G.GraphView(canvas, { label: cfg.label, labels: tbLabels(), minFit: cfg.minFit, onSelect: function(id){ insp.innerHTML = id ? cfg.inspector(id) : cfg.empty; if (cfg.onSelect) cfg.onSelect(id); } });
    var fkey = cfg.filters && cfg.filters.key, fcur = cfg.filters ? ((fkey && state.flt[fkey]) || cfg.filters.def) : null;
    view.setData(cfg.filters ? cfg.filters.data(fcur) : cfg.data());
    if (cfg.filters) {
      var fbar = document.createElement("div"); fbar.className = "flt-bar";
      var drawF = function(){
        fbar.innerHTML = '<span class="dim flt-l">' + esc(u("flt.label")) + ':</span>' + cfg.filters.list.map(function(f){ return '<button type="button" class="flt" data-f="' + f + '" aria-pressed="' + (f === fcur) + '">' + esc(u("flt." + f)) + "</button>"; }).join("");
      };
      drawF();
      fbar.addEventListener("click", function(e){
        var b = e.target.closest("[data-f]"); if (!b) return; fcur = b.dataset.f; if (fkey) state.flt[fkey] = fcur;
        var keep = view.sel; view.stopPlayback(); drawF(); view.setData(cfg.filters.data(fcur)).then(function(){ if (keep && view.byId[keep]) { view.select(keep, true); } else { insp.innerHTML = cfg.empty; if (cfg.onSelect) cfg.onSelect(null); } });
      });
      top.appendChild(fbar);
    }
    if (cfg.playback) {
      var pb = view.playback(), bar = document.createElement("div"); bar.className = "pb-bar";
      bar.innerHTML = '<span class="pb-t">' + esc(u("pb.hint")) + '</span><button type="button" data-pb="restart" title="' + esc(u("pb.restart")) + '">⏮ ' + esc(u("pb.restart")) + '</button><button type="button" data-pb="prev" title="' + esc(u("pb.prev")) + '">◀ ' + esc(u("pb.prev")) + '</button><button type="button" class="primary" data-pb="play">▶ ' + esc(u("pb.play")) + '</button><button type="button" data-pb="next" title="' + esc(u("pb.next")) + '">' + esc(u("pb.next")) + ' ▶|</button>' +
        '<label class="pb-l">' + esc(u("pb.speed")) + ' <select data-pb="speed"><option value="0.5">0.5×</option><option value="1" selected>1×</option><option value="2">2×</option></select></label><label class="pb-l"><input type="checkbox" data-pb="opt"> ' + esc(u("pb.opt")) + '</label><span class="pb-info" aria-live="polite"></span><span class="pb-branch"></span>';
      top.appendChild(bar);
      var playBtn = bar.querySelector('[data-pb="play"]'), info = bar.querySelector(".pb-info"), br = bar.querySelector(".pb-branch");
      pb.cb = function(st){
        playBtn.textContent = st.playing ? "⏸ " + u("pb.pause") : "▶ " + u("pb.play");
        var nd = st.node && view.byId[st.node];
        info.textContent = st.node ? u("pb.step") + " " + st.step + ": " + (nd ? nd.label : st.node) + (st.cross ? "  » " + u("pb.cross") + " (" + capShort(st.cross.from) + " → " + capShort(st.cross.to) + ")" : "") + (st.finished ? "  · " + u("pb.done") : "") : "";
        br.innerHTML = st.pending ? '<span class="dim">' + esc(u("pb.branch")) + "</span> " + st.pending.map(function(o){ var t = view.byId[o.to]; return '<button type="button" class="flt" data-pbc="' + o.i + '">' + esc((o.label ? o.label + " → " : "") + (t ? t.label : o.to)) + "</button>"; }).join("") : "";
        if (st.node && cfg.inspector) { insp.innerHTML = cfg.inspector(st.node); if (cfg.onSelect) cfg.onSelect(st.node); }
      };
      bar.addEventListener("click", function(e){
        var b = e.target.closest("[data-pb],[data-pbc]"); if (!b) return;
        if (b.dataset.pbc != null) { pb.choose(+b.dataset.pbc); return; }
        var a = b.dataset.pb;
        if (a === "restart") pb.restart(); else if (a === "prev") pb.prev(); else if (a === "next") pb.next(); else if (a === "play") { if (pb.playing) pb.pause(); else pb.play(); }
      });
      bar.addEventListener("change", function(e){ var a = e.target.dataset.pb; if (a === "speed") pb.setSpeed(+e.target.value); else if (a === "opt") pb.setOptional(e.target.checked); });
    }
    live.push(view);
    // inspector toggle (top-right of the canvas)
    var tg = document.createElement("button"); tg.type = "button"; tg.className = "gv-keep gv-insp-toggle";
    function tgLabel(){ tg.textContent = layoutEl.classList.contains("insp-collapsed") ? "◂ " + u("inspShow") : u("inspHide") + " ▸"; }
    tgLabel();
    tg.addEventListener("click", function(){ layoutEl.classList.toggle("insp-collapsed"); state.insp = !layoutEl.classList.contains("insp-collapsed"); save(); tgLabel(); setTimeout(function(){ view.fit(); }, 30); });
    canvas.appendChild(tg);
    // legend card (bottom-left)
    var lg = document.createElement("div"); lg.className = "gv-keep gv-legend-card" + (state.legend ? " open" : "");
    lg.innerHTML = '<button type="button" class="gv-legend-btn" aria-expanded="' + state.legend + '">' + esc(u("legendBtn")) + ' <span class="gv-legend-caret">▾</span></button><div class="gv-legend-body">' + legendHtml(cfg.legend) + "</div>";
    lg.querySelector("button").addEventListener("click", function(){ state.legend = !state.legend; save(); lg.classList.toggle("open", state.legend); this.setAttribute("aria-expanded", String(state.legend)); });
    canvas.appendChild(lg);
    root._view = view;
    root._select = function(id){ if (id && view.byId && view.byId[id]) { view.select(id, true); view.focus(id); } };
    if (cfg.node) view.ready.then(function(){ root._select(cfg.node); });
    return view;
  }
  function mountProcView(root, p, opts){
    opts = opts || {};
    return mountGraphPanel(root, {
      label: pTitle(p), legend: "process", node: opts.node, playback: true,
      empty: '<p class="empty"><b>' + esc(pTitle(p)) + "</b><br>" + esc(pDesc(p)) + "<br><br>" + esc(u("noNode")) + "</p>",
      data: function(){ return procGraph(p); }, inspector: function(id){ return nodeInspector(p, id); },
      onSelect: function(id){ if (opts.persist !== false) state.node = id; }
    });
  }

  function selectOptions(arr, sel){ return arr.map(function(o){ return '<option value="' + esc(o.v) + '"' + (o.v === sel ? " selected" : "") + ">" + esc(o.l) + "</option>"; }).join(""); }
  function procsOfCap(capId){
    var own = HM.procs.filter(function(p){ return p.cap === capId && p.scope === "cap"; });
    var auth = HM.procs.filter(function(p){ return p.cap === capId && p.scope === "authoring"; });
    var cross = HM.procs.filter(function(p){ return p.scope === "cross" && (p.nodes.some(function(n){ return n.cap === capId; }) || (p.caps || []).indexOf(capId) >= 0); });
    return { own: own, auth: auth, cross: cross };
  }
  function explorerCaps(){
    var ids = {}; HM.procs.forEach(function(p){ if (p.cap) ids[p.cap] = 1; });
    var arr = Object.keys(HM.caps).filter(function(c){ return ids[c]; }).map(function(c){ return { v: c, l: capShort(c) }; });
    arr.push({ v: "__cross", l: "⇄ " + u("crossLabel") });
    return arr;
  }
  function capsOfProc(p){ var s = {}, out = []; p.nodes.forEach(function(n){ if (n.t !== "start" && n.t !== "end" && !s[n.cap]) { s[n.cap] = 1; out.push(n.cap); } }); return out; }
  function mountExplorer(root, opts){
    opts = opts || {};
    var cur0 = state.proc && procById(state.proc);
    var capSel = opts.fixedCap || (cur0 ? (cur0.scope === "cross" ? "__cross" : cur0.cap) : null) || "validation";
    if (capSel !== "__cross" && !HM.caps[capSel]) capSel = Object.keys(HM.caps)[0];
    function procChoices(cap){
      if (cap === "__cross") return [{ g: u("crossLabel"), items: HM.procs.filter(function(p){ return p.scope === "cross"; }) }];
      var g = procsOfCap(cap), out = [];
      if (g.own.length) out.push({ g: u("capProcs"), items: g.own });
      if (g.auth.length) out.push({ g: u("authProcs"), items: g.auth });
      if (g.cross.length) out.push({ g: u("crossHere"), items: g.cross });
      return out;
    }
    function render(){
      live = [];
      var groups = procChoices(capSel), flat = []; groups.forEach(function(g){ g.items.forEach(function(p){ flat.push(p); }); });
      var cur = state.proc && flat.some(function(p){ return p.id === state.proc; }) ? state.proc : (flat[0] && flat[0].id);
      state.proc = cur;
      var capOpts = opts.fixedCap ? "" : '<label>' + esc(u("capLabel")) + ': <select class="sel" id="px-cap">' + selectOptions(explorerCaps(), capSel) + "</select></label>";
      var procOpts = '<label>' + esc(u("procLabel")) + ': <select class="sel" id="px-proc">' + groups.map(function(g){
        return '<optgroup label="' + esc(g.g) + '">' + g.items.map(function(p){ return '<option value="' + p.id + '"' + (p.id === cur ? " selected" : "") + ">" + esc(pTitle(p)) + "</option>"; }).join("") + "</optgroup>";
      }).join("") + "</select></label>";
      var p = cur ? procById(cur) : null;
      root.innerHTML = '<div class="px-bar">' + capOpts + procOpts + (p && p.archify ? '<a class="ghost-btn small" target="_blank" rel="noopener" href="' + esc(tx(p.archify)) + '">' + esc(u("archifyProc")) + " ↗</a>" : "") + "</div>" +
        (p ? '<div class="px-meta"><b>' + esc(pTitle(p)) + "</b> — " + esc(pDesc(p)) + (p.scope === "cross" ? '<span class="chips">' + capsOfProc(p).map(function(c){ return '<span class="chip" style="border-left:4px solid ' + capColor(c) + '">' + esc(capShort(c)) + "</span>"; }).join("") + "</span>" : "") + '</div><div class="px-view"></div>' : '<p class="empty">' + esc(u("emptyProcs")) + "</p>");
      if (p) { var view = root.querySelector(".px-view"); mountProcView(view, p, { node: state.node }); root._pv = view; }
      var cs = root.querySelector("#px-cap"), ps = root.querySelector("#px-proc");
      if (cs) cs.addEventListener("change", function(){ capSel = cs.value; state.proc = null; state.node = null; render(); });
      if (ps) ps.addEventListener("change", function(){ state.proc = ps.value; state.node = null; render(); });
    }
    render();
  }

  function mountDomainMap(root, capId){
    var c = HM.caps[capId];
    if (!c.map || !c.map.nodes.length) { root.innerHTML = '<p class="empty">—</p>'; return; }
    root.innerHTML = '<p class="dim">' + esc(u("mapIntro")) + '</p><div class="map-host"></div>';
    function show(id){
      if (!id) return '<p class="empty">' + esc(u("noElement")) + "</p>";
      var n = null; c.map.nodes.forEach(function(x){ if (x.id === id) n = x; });
      var ei = n && n.elem ? elemById(n.elem) : null, part = n ? mapNodeParts(n) : { title: id };
      var h = inspHead(u("mapNode." + ((n && n.k) || "concept")), part.title, n && n.req ? reqBadge(n.req) : "") + '<div class="insp-body">';
      if (ei) h += elementBody(ei.cap, ei.el, false); else if (n) h += '<p class="lead">' + esc(tx(n.d)) + "</p>";
      var rel = c.map.edges.filter(function(e){ return e.a === id || e.b === id; }), nm = {}; c.map.nodes.forEach(function(x){ nm[x.id] = x; });
      if (rel.length) h += field(u("relations"), "<ul>" + rel.map(function(e){
        var other = e.a === id ? e.b : e.a, dir = e.a === id;
        return "<li>" + (dir ? "" : "← ") + '<span class="dim">' + esc(u("mapEdge." + e.rel)) + (e.card1 || e.card2 ? " [" + esc((e.card1 || "") + (e.card1 && e.card2 ? " → " : "") + (e.card2 || "")) + "]" : "") + "</span> " + btnGo('data-map-sel="' + other + '"', mapNodeParts(nm[other]).title) + (e.req && e.req !== "MANDATORY" ? " " + reqBadge(e.req) : "") + "</li>";
      }).join("") + "</ul>");
      return h + "</div>";
    }
    var host = root.querySelector(".map-host");
    mountGraphPanel(host, { label: capNameL(capId), legend: "map", minFit: 0.36, node: state.mapSel, empty: '<p class="empty">' + esc(u("noElement")) + "</p>", data: function(){ return mapGraph(c); }, filters: mapFilters(c), inspector: show, onSelect: function(id){ state.mapSel = id; } });
    host.querySelector(".insp").addEventListener("click", function(e){ var b = e.target.closest("[data-map-sel]"); if (b) { e.stopPropagation(); host._select(b.dataset.mapSel); } });
    root._select = host._select;
  }

  // ------------------------------------------------------------------ capability workspace
  function coverageHtml(capId){
    var r = HM.completeness(capId), ok = r.filter(function(x){ return x.ok; }).length;
    return '<details class="cov"><summary><b>' + esc(u("coverage")) + "</b> — " + ok + "/" + r.length + '</summary><ul class="cov-list">' + r.map(function(x, i){
      return '<li class="' + (x.ok ? "ok" : "miss") + '"><span aria-hidden="true">' + (x.ok ? "✔" : "✖") + "</span> " + esc(COV_LABELS[state.lang][COV_KEYS[i]]) + ' <span class="dim">(' + esc(x.ok ? u("covOk") : u("covMissing")) + ")</span></li>";
    }).join("") + "</ul></details>";
  }
  function tabOverview(capId){
    var c = HM.caps[capId], us = c.user || {}, f = c.func || {}, fl = c.funcL || {}, tl = c.techL || {}, t = c.tech || {}, h = "";
    if (isUser()) {
      h += '<div class="lvl-card span-all"><p class="tagline">' + esc(tx(us.tagline)) + "</p><div class=\"cols\">" +
        "<div>" + field(u("what"), p1(us.what)) + field(u("why"), p1(us.why)) + field(u("when"), p1(us.when)) + "</div>" +
        "<div>" + field(u("canDefine"), ul(us.canDefine)) + field(u("needs"), ul(us.needs)) + field(u("produces"), ul(us.produces)) + "</div></div></div>";
      h += '<div class="lvl-card"><div class="cols3"><div><h4>' + reqBadge("MANDATORY") + "</h4>" + ul(us.mandatory) + "</div><div><h4>" + reqBadge("OPTIONAL") + "</h4>" + ul(us.optional) + "</div><div><h4>" + reqBadge("CONDITIONAL") + "</h4>" + ul(us.conditional) + "</div></div></div>";
      h += '<div class="lvl-card">' + field(u("next"), ul(us.next)) +
        field(u("interacts"), (us.interacts || []).length ? "<ul>" + us.interacts.map(function(i){ return "<li>" + capLink(i.cap) + " — " + esc(tx(i.t)) + "</li>"; }).join("") + "</ul>" : "") +
        field(u("howDefined"), (c.define || []).length ? "<ul>" + c.define.map(function(d){ return "<li>" + reqBadge(d.req) + btnGo('data-go-define="' + d.id + '"', tx(d.n)) + "</li>"; }).join("") + "</ul>" : "") + "</div>";
    } else if (isFunc()) {
      h += '<div class="lvl-card span-all"><div class="lvl-tag">' + esc(u("ovFuncHead")) + '</div><p class="tagline">' + esc(tx(fl.title)) + "</p>" + p1(fl.summary) +
        '<div class="cols3">' + "<div>" + field(u("fInputs"), ul(fl.inputs)) + "</div><div>" + field(u("fOutputs"), ul(fl.outputs)) + "</div><div>" + field(u("fConditions"), ul(fl.conditions)) + "</div></div></div>";
      h += '<div class="lvl-card">' + field(u("lifecycle"), ul(f.lifecycle)) + field(u("semantics"), ul(f.semantics)) +
        ((f.states || []).length ? field(u("states"), '<table class="tbl">' + f.states.map(function(s){ return "<tr><td><b>" + esc(tx(s.n)) + "</b></td><td>" + esc(tx(s.d)) + "</td></tr>"; }).join("") + "</table>") : "") + field(u("cross"), ul(f.cross)) + "</div>";
      h += '<div class="lvl-card"><h4>' + esc(u("conceptsIdx")) + "</h4><table class=\"tbl\">" + (c.elements || []).map(function(e){ return "<tr><td>" + reqBadge(e.req) + "</td><td>" + btnGo('data-go-el="' + e.id + '" data-go-cap="' + capId + '"', elTitle(e)) + '</td><td class="dim mono">' + esc(e.card || "") + "</td></tr>"; }).join("") + "</table></div>";
    } else {
      h += '<div class="lvl-card span-all"><div class="lvl-tag">' + esc(u("ovTechHead")) + '</div><p class="tagline">' + esc(tx(tl.title)) + "</p>" + p1(tl.summary) +
        field(u("runtime"), p1(t.runtime)) + '<div class="cols">' + "<div>" + field(u("services"), monoList(t.services)) + field(u("apis"), monoList(t.apis)) + "</div><div>" + field(u("schemas"), monoList(t.schemas)) + field(u("tests"), monoList(t.tests)) + "</div></div>" + field(u("notes"), ul(t.notes)) + "</div>";
      h += '<div class="lvl-card"><h4>' + esc(u("mapTitle")) + '</h4><table class="tbl"><tr><td class="dim">' + esc(u("mapCols")[0]) + '</td><td class="dim">' + esc(u("mapCols")[1]) + '</td><td class="dim">' + esc(u("mapCols")[2]) + "</td></tr>" +
        (c.elements || []).map(function(e){ return "<tr><td>" + btnGo('data-go-el="' + e.id + '" data-go-cap="' + capId + '"', shortName(tx(e.nf) || tx(e.n))) + '</td><td class="mono">' + esc((e.tech && e.tech.type) || "") + '</td><td class="dim mono">' + esc(firstSentence((e.tech && e.tech.schema) || "", 80)) + "</td></tr>"; }).join("") + "</table></div>";
      h += implMapHtml(capId) + traceTable(capId) + techTests(capId);
      if (!tdCap(capId)) h += '<div class="lvl-card span-all"><p class="dim">' + esc(u("td.none")) + "</p></div>";
      h += '<div class="lvl-card span-all"><details class="drill"><summary>' + esc(u("evidence")) + "</summary>" + evRows((t.evidence || []).slice(0, 12)) + "</details></div>";
    }
    return '<div class="ov-grid">' + h + "</div>" + coverageHtml(capId);
  }
  function tabElements(capId){
    var c = HM.caps[capId], els = c.elements || [];
    var h = '<div class="req-legend">' + ["MANDATORY", "OPTIONAL", "CONDITIONAL", "ALTERNATIVE", "REPEATABLE", "DERIVED"].map(function(r){ return reqBadge(r) + '<span class="dim">' + esc(u("reqHelp." + r)) + "</span>"; }).join(" ") + "</div>";
    var sel = state.elSel[capId]; if (!sel || !els.some(function(e){ return e.id === sel; })) sel = els.length ? els[0].id : null; state.elSel[capId] = sel;
    h += '<div class="md"><nav class="md-list"><div class="search-row"><input type="search" id="el-filter" value="' + esc(state.elQ) + '" placeholder="' + esc(u("search")) + '"></div><div id="el-list">';
    h += els.map(function(e){
      var nv = (e.variants || []).length;
      return '<button type="button" class="md-item' + (e.id === sel ? " active" : "") + '" data-el-sel="' + e.id + '" data-s="' + esc((elTitle(e) + " " + tx(e.mean) + " " + tx(e.fm)).toLowerCase()) + '"><span class="mi-t">' + esc(shortName(elTitle(e))) + '</span><span class="mi-b">' + reqBadge(e.req) + (nv ? '<span class="dim">' + nv + " " + esc(u("variantsOf").toLowerCase()) + "</span>" : "") + '</span><span class="mi-d dim">' + esc(firstSentence(isUser() ? tx(e.mean) : (tx(e.fm) || tx(e.mean)), 90)) + "</span></button>";
    }).join("") + '</div></nav><section class="md-detail" id="el-detail">' + elDetailHtml(capId, sel) + "</section></div>";
    return h;
  }
  function elDetailHtml(capId, id){
    var c = HM.caps[capId], e = (c.elements || []).filter(function(x){ return x.id === id; })[0];
    if (!e) return '<p class="empty">' + esc(u("pickEl")) + "</p>";
    return '<article class="el" id="el-' + e.id + '"><h4>' + esc(elTitle(e)) + reqBadge(e.req) + (e.kind === "derived" ? reqBadge("DERIVED") : "") + "</h4>" + elementBody(capId, e, false) + "</article>";
  }
  function tabDefine(capId){
    var c = HM.caps[capId], d = c.define || [], h = '<p class="dim">' + esc(u("defineIntro")) + "</p>";
    var sel = state.defSel[capId]; if (!sel || !d.some(function(m){ return m.id === sel; })) sel = d.length ? d[0].id : null; state.defSel[capId] = sel;
    h += '<div class="md md-define"><nav class="md-list">' + d.map(function(m){
      return '<button type="button" class="md-item' + (m.id === sel ? " active" : "") + '" data-def-sel="' + m.id + '"><span class="mi-t">' + esc(tx(m.n)) + '</span><span class="mi-b"><span class="kind-tag">' + esc(m.kind) + "</span>" + reqBadge(m.req) + "</span></button>";
    }).join("") + '</nav><section class="md-detail">';
    var m = d.filter(function(x){ return x.id === sel; })[0];
    if (!m) return h + '<p class="empty">' + esc(u("pickDef")) + "</p></section></div>";
    var p = m.proc ? procById(m.proc) : null;
    var elN = (m.elems || []).map(function(id){ var ei = elemById(id); return ei ? btnGo('data-go-el="' + id + '" data-go-cap="' + ei.cap + '"', elTitle(ei.el)) : ""; }).join(" ");
    var body = isUser() ? field(u("whatYouDo"), p1(m.does)) : isFunc() ? field(u("funcSem"), p1(m.func || firstSentence(tx(m.does), 220))) : field(u("techImpl"), p1(m.tech || m.func));
    h += '<article class="mech" id="mech-' + m.id + '"><h4><span class="kind-tag">' + esc(m.kind) + "</span>" + esc(tx(m.n)) + reqBadge(m.req) + "</h4>" + body + (elN ? field(u("conceptsIdx"), elN) : "") + "</article>" + (p ? '<div class="mech-proc wide" id="def-proc"></div>' : "") + "</section></div>";
    return h;
  }
  function tabRelations(capId){
    var c = HM.caps[capId], rel = c.rel || [];
    function row(r){ var t = isUser() ? r.u : (isTech() ? (r.t || r.f || r.u) : (r.f || r.u)); return "<li><span class=\"rel-t\" data-rt=\"" + r.type + '">' + esc(u("relType." + r.type)) + "</span> " + capLink(r.to) + " — " + esc(tx(t)) + "</li>"; }
    var out = rel.filter(function(r){ return r.dir !== "in"; }), inn = rel.filter(function(r){ return r.dir === "in"; });
    var h = '<p class="dim">' + esc(u("relsIntro")) + '</p><div class="cols"><div><h4>' + esc(u("dirOut")) + "</h4><ul class=\"rels\">" + out.map(row).join("") + "</ul></div><div><h4>" + esc(u("dirIn")) + "</h4><ul class=\"rels\">" + inn.map(row).join("") + "</ul></div></div>";
    h += '<div id="rel-graph" class="rel-host"></div>';
    return h;
  }
  function tabImplementation(capId){
    if (!isTech()) return '<div class="lvl-card"><p>' + esc(u("techOnly")) + '</p><button type="button" class="ghost-btn" data-set-level="technical">' + esc(u("switchTech")) + "</button></div>";
    var c = HM.caps[capId], t = c.tech || {}, d = tdCap(capId), h = "";
    var seg = '<div class="seg impl-seg"><button type="button" data-impl-view="list" aria-pressed="' + (state.implView === "list") + '">' + esc(u("td.viewList")) + '</button><button type="button" data-impl-view="map" aria-pressed="' + (state.implView === "map") + '">' + esc(u("td.viewMap")) + "</button></div>";
    if (!d || !d.comps.length) return '<div class="lvl-card"><p class="dim">' + esc(u("td.none")) + "</p>" + field(u("runtime"), p1(t.runtime)) + field(u("services"), monoList(t.services)) + field(u("evidence"), evRows(t.evidence)) + "</div>";
    var sel = state.compSel[capId]; if (!sel || !d.comps.some(function(x){ return x.id === sel; })) sel = d.comps[0].id; state.compSel[capId] = sel;
    h += seg;
    if (state.implView === "map") return h + '<div id="impl-map" class="impl-map"></div>';
    h += '<div class="md md-impl"><nav class="md-list"><div class="search-row"><input type="search" id="comp-filter" placeholder="' + esc(u("search")) + '"></div>';
    d.groups.forEach(function(g){
      var cs = d.comps.filter(function(c2){ return c2.g === g.id; }); if (!cs.length) return;
      h += '<div class="group-label" style="border-left:4px solid ' + (GROUP_COLOR[g.id] || "#7a8590") + ';padding-left:6px">' + esc(tx(g.n)) + "</div>";
      cs.forEach(function(c2){ h += '<button type="button" class="md-item' + (c2.id === sel ? " active" : "") + '" data-comp-sel="' + esc(c2.id) + '" data-s="' + esc((c2.n + " " + tx(c2.what)).toLowerCase()) + '"><span class="mi-t">' + esc(c2.n) + '</span><span class="mi-b"><span class="kind-tag">' + esc(c2.kind) + '</span></span><span class="mi-d dim">' + esc(firstSentence(tx(c2.what), 90)) + "</span></button>"; });
    });
    var cur = d.comps.filter(function(x){ return x.id === sel; })[0];
    return h + '</nav><section class="md-detail" id="comp-detail">' + compCard(capId, cur) + "</section></div>";
  }
  function mountImplMap(root, capId){
    var d = tdCap(capId); if (!d) return;
    var nodes = d.comps.map(function(c){ return { id: c.id, shape: c.kind === "record" || c.kind === "schema" || c.kind === "enum" ? "variant" : "concept", label: c.n, sub: firstSentence(tx(c.what), 70), kindLabel: tx((d.groups.filter(function(g){ return g.id === c.g; })[0] || { n: c.g }).n || c.g), color: GROUP_COLOR[c.g] || "#7a8590", req: "", tag: c.kind }; });
    var ids = {}; nodes.forEach(function(n){ ids[n.id] = 1; });
    var edges = [], seen = {};
    d.comps.forEach(function(c){ (c.calls || []).forEach(function(r){ if (typeof r === "string" && ids[r] && !seen[c.id + ">" + r]) { seen[c.id + ">" + r] = 1; edges.push({ a: c.id, b: r, kind: "ref", label: "" }); } }); });
    mountGraphPanel(root, { label: capNameL(capId), legend: "impl", minFit: 0.36, empty: '<p class="empty">' + esc(u("td.mapHint")) + "</p>", data: function(){ return { nodes: nodes, edges: edges, family: "map" }; }, inspector: function(id){ var f = compFind(id, capId); return f ? inspHead(u("td.implBy"), f.comp.n, '<span class="kind-tag">' + esc(f.comp.kind) + "</span>") + '<div class="insp-body">' + compCard(f.cap, f.comp) + "</div>" : ""; } });
  }
  var TAB_FN = { overview: tabOverview, elements: tabElements, map: function(){ return '<div id="cap-map"></div>'; }, define: tabDefine, processes: function(){ return '<div id="cap-explorer" class="px"></div>'; }, relationships: tabRelations, implementation: tabImplementation, evidence: function(capId){ return window.HydraTestsUI ? window.HydraTestsUI.capabilityTab(capId) : ""; } };

  function renderCapList(){
    var el = $("cap-list"), h = "";
    ["definition", "behavior", "delivery", "platform", "external"].forEach(function(g){
      var ids = Object.keys(HM.caps).filter(function(id){ return HM.caps[id].group === g; });
      if (!ids.length) return;
      h += '<div class="group-label">' + esc(tx(HM.groups[g])) + "</div>";
      ids.forEach(function(id){
        var r = HM.completeness(id), ok = r.filter(function(x){ return x.ok; }).length, o = ovNode(id);
        h += '<div class="nav-card' + (state.cap === id ? " active" : "") + '" role="button" tabindex="0" data-cap-id="' + id + '" style="border-left:5px solid ' + HM.groupColor[g] + '"><div class="nav-title">' + esc(shortName(capNameL(id))) + '</div><div class="nav-sub">' + esc(o ? tx(o.sb[state.level]) : "") + '</div><div class="nav-cov" title="' + esc(u("dbl")) + '"><span style="width:' + Math.round(100 * ok / r.length) + '%"></span></div></div>';
      });
    });
    el.innerHTML = h;
  }
  function renderCapDetail(){
    ensureCap();
    var capId = state.cap, c = HM.caps[capId], el = $("cap-detail");
    live = [];
    $("cap-list").classList.toggle("hidden", !state.capList);
    var h = '<div class="detail-title"><button type="button" class="ghost-btn small" id="cap-list-toggle">' + esc(state.capList ? u("capListHide") : u("capListShow")) + '</button><span class="kind-dot" style="background:' + capColor(capId) + '"></span><h2>' + esc(capNameL(capId)) + '</h2><span class="tag">' + esc(tx(HM.groups[c.group])) + "</span></div>";
    h += '<div class="seg" id="cap-tabs" role="tablist" style="margin-bottom:14px;flex-wrap:wrap;">' + TABS.map(function(tb){ return '<button type="button" role="tab" data-tab="' + tb + '" aria-pressed="' + (tb === state.tab) + '">' + esc(u("tabs." + tb)) + "</button>"; }).join("") + "</div>";
    h += '<div id="cap-panel">' + TAB_FN[state.tab](capId) + "</div>";
    el.innerHTML = h;
    if (state.tab === "map") mountDomainMap($("cap-map"), capId);
    if (state.tab === "processes") mountExplorer($("cap-explorer"), { fixedCap: capId });
    if (state.tab === "relationships") {
      var box = $("rel-graph"), ov = overviewGraph(), keep = {}; keep[capId] = 1;
      ov.edges.forEach(function(e){ if (e.a === capId) keep[e.b] = 1; if (e.b === capId) keep[e.a] = 1; });
      var nodes = ov.nodes.filter(function(n){ return keep[n.id]; });
      if (nodes.length > 1) {
        var posn = {}; nodes.forEach(function(n){ posn[n.id] = n; });
        var relData = function(f){
          var allow = f && OVF[f] ? OVF[f] : null;
          var es = ov.edges.filter(function(e){ return keep[e.a] && keep[e.b] && (e.a === capId || e.b === capId) && (!allow || allow.indexOf(e.type) >= 0); });
          var tc = {}; es.forEach(function(e){ tc[e.a] = tc[e.b] = 1; });
          return { nodes: allow ? nodes.filter(function(n){ return tc[n.id]; }) : nodes, edges: es, family: "overview" };
        };
        mountGraphPanel(box, { label: capNameL(capId), legend: "overview", empty: '<p class="empty">' + esc(u("ovHint")) + "</p>", data: relData, filters: { key: "rel", list: OV_FILTERS.filter(function(f){ return f === "all" || relData(f).edges.length; }), def: "all", data: relData }, inspector: function(id){ return overviewInspector(id); } });
      }
    }
    if (state.tab === "define" && $("def-proc")) {
      var dm = (c.define || []).filter(function(x){ return x.id === state.defSel[capId]; })[0], dp = dm && dm.proc ? procById(dm.proc) : null;
      if (dp) mountProcView($("def-proc"), dp, { persist: false });
    }
    if (state.tab === "implementation" && $("impl-map")) mountImplMap($("impl-map"), capId);
    if (state.tab === "elements" && state.pendingEl) { var t = document.getElementById("el-" + state.pendingEl); if (t) { t.scrollIntoView({ block: "start" }); t.classList.add("flash"); } state.pendingEl = null; }
    if (state.tab === "define") {
      Object.keys(state.openMech).forEach(function(mid){ var b = document.querySelector('[data-toggle-proc][data-mech="' + mid + '"]'); if (b && state.openMech[mid]) toggleMech(b, true); });
      if (state.pendingMech) { var m = document.getElementById("mech-" + state.pendingMech); if (m) { m.scrollIntoView({ block: "start" }); m.classList.add("flash"); var b2 = m.querySelector("[data-toggle-proc]"); if (b2) toggleMech(b2, true); } state.pendingMech = null; }
    }
  }
  function toggleMech(btn, forceOpen){
    var box = $("mp-" + btn.dataset.mech), open = forceOpen ? true : box.classList.contains("hidden");
    box.classList.toggle("hidden", !open); state.openMech[btn.dataset.mech] = open;
    btn.textContent = open ? "▼ " + u("hideProc") : "▶ " + u("showProc");
    if (open && !box._mounted) { box._mounted = true; mountProcView(box, procById(btn.dataset.toggleProc), { persist: false }); }
    if (open && box._view) setTimeout(function(){ box._view.fit(); }, 30);
  }

  // ------------------------------------------------------------------ overview section
  function overviewInspector(id){
    if (!id) return '<p class="empty">' + esc(u("ovHint")) + "</p>";
    var Lv = state.level, n = ovNode(id), c = HM.caps[id]; if (!n) return "";
    var h = inspHead(tx(HM.groups[n.group]), tx(n.nm[Lv]), "") + '<div class="insp-body">';
    if (c) {
      if (isUser()) h += '<p class="lead">' + esc(tx(c.user.what)) + "</p>" + field(u("why"), p1(c.user.why)) + field(u("canDefine"), ul(c.user.canDefine)) + field(u("produces"), ul(c.user.produces));
      else if (isFunc()) { var fl = c.funcL || {}; h += '<p class="lead">' + esc(tx(fl.title)) + "</p>" + p1(fl.summary) + field(u("fInputs"), ul(fl.inputs)) + field(u("fOutputs"), ul(fl.outputs)) + field(u("lifecycle"), ul((c.func || {}).lifecycle)); }
      else { var tl = c.techL || {}; h += '<p class="lead">' + esc(tx(tl.title)) + "</p>" + p1(tl.summary) + field(u("runtime"), p1((c.tech || {}).runtime)) + field(u("services"), monoList(((c.tech || {}).services || []).slice(0, 8))); }
    } else {
      var cm = COMPONENTS[id], dl = cm && cm.detail && cm.detail[state.lang] || [];
      h += '<p class="lead">' + esc(tx(n.sb[Lv])) + "</p>";
    }
    var rel = HM.overview.edges.filter(function(e){ return (e.a === id || e.b === id) && e.lv.indexOf(Lv) >= 0; });
    if (rel.length) h += field(u("relations"), "<ul>" + rel.map(function(e){
      var other = e.a === id ? e.b : e.a, o = ovNode(other); if (!o || o.lv.indexOf(Lv) < 0) return "";
      return "<li>" + (e.a === id ? "→ " : "← ") + '<span class="rel-t" data-rt="' + e.type + '">' + esc(u("relType." + e.type)) + "</span> " + btnGo('data-ov-sel="' + other + '"', tx(o.nm[Lv])) + (tx(e.l[Lv]) ? ' <span class="dim">— ' + esc(tx(e.l[Lv])) + "</span>" : "") + "</li>";
    }).join("") + "</ul>");
    if (c) {
      var ps = procsOfCap(id), all = ps.own.concat(ps.cross);
      if (all.length) h += field(u("participates"), all.map(function(p){ return btnGo('data-go-proc="' + p.id + '"', pTitle(p)); }).join(" "));
      h += '<div class="insp-actions"><button type="button" class="ghost-btn" data-go-cap="' + id + '">' + esc(u("openCap")) + " →</button></div>";
    }
    return h + "</div>";
  }
  var CAP_BY_ARCH = {};
  Object.keys(LEG).forEach(function(k){ (CAP_BY_ARCH[LEG[k].archComponentId] = CAP_BY_ARCH[LEG[k].archComponentId] || []).push(k); });
  var FRAME_SRC = { user: { es: "internal/hydra-user-overview.es.html", en: "internal/hydra-user-overview.en.html" }, functional: { es: "internal/hydra-functional-overview.es.html", en: "internal/hydra-functional-overview.en.html" }, technical: { es: "internal/hydra-architecture.es.html", en: "internal/hydra-architecture.en.html" } };
  function renderComponentList(filter){
    var lang = state.lang, q = (filter || "").trim().toLowerCase(), h = "";
    ORDER.forEach(function(id){
      var c = COMPONENTS[id], uPair = c[lang].user, fPair = c[lang].functional, tPair = c[lang].technical, rel = c.relations || [];
      var safe = function(a){ return state.level === "user" ? (a || []).filter(function(s){ return !HM.leak(s); }) : (a || []); };
      var det = safe(c.detail && c.detail[lang]), lim = safe(c.limits && c.limits[lang]);
      if (q && (id + " " + uPair[0] + " " + fPair[0] + " " + det.join(" ")).toLowerCase().indexOf(q) < 0) return;
      var shown = state.level === "user" ? uPair : fPair;
      h += '<details class="card" data-kind="' + c.kind + '"><summary><span class="kind-dot ' + c.kind + '"></span><span class="label">' + esc(shown[0]) + '</span></summary><div class="tier-body">';
      h += '<div class="tier"><div class="tier-label">' + u("tierWhat") + "</div>" + ul(det) + (c.inUser ? "" : '<p class="tier-sub">' + esc(u("notInLevel")) + "</p>") + "</div>";
      h += '<div class="tier"><div class="tier-label">' + u("tierLimits") + "</div>" + ul(lim) + "</div>";
      if (state.level !== "user") h += '<div class="tier"><div class="tier-label">' + u("tierRelations") + "</div>" + (rel.length ? "<ul>" + rel.map(function(r){ var tl = COMPONENTS[r.to] ? COMPONENTS[r.to][lang].functional[0] : r.to; return "<li><strong>→ " + esc(tl) + ":</strong> " + esc(r[lang]) + "</li>"; }).join("") + "</ul>" : '<p class="tier-sub">' + esc(u("noRelations")) + "</p>") + "</div>";
      if (state.level === "technical") h += '<div class="tier"><div class="tier-label">' + u("tierImpl") + '</div><p class="tier-text">' + esc(tPair[0]) + '</p><p class="tier-sub">' + esc(tPair[1]) + '</p></div><div class="tier"><div class="tier-label">' + u("tierSrc") + "</div>" + evRows((c.sources || []).map(function(s){ return { path: s.path, line: s.line, label: s.label }; })) + "</div>";
      var caps = CAP_BY_ARCH[id] || [];
      if (caps.length) h += '<div class="tier">' + caps.map(function(cid){ return HM.caps[cid] ? '<button type="button" class="ghost-btn small" data-go-cap="' + cid + '">' + esc(u("goCap")) + ": " + esc(capShort(cid)) + " →</button>" : ""; }).join(" ") + "</div>";
      h += "</div></details>";
    });
    $("comp-list").innerHTML = h || '<p class="dim">—</p>';
  }
  function renderOverview(){
    var map = state.mode === "map";
    $("ov-mapview").classList.toggle("hidden", !map); $("ov-archify").classList.toggle("hidden", map);
    document.querySelectorAll("#mode-seg button").forEach(function(b){ b.setAttribute("aria-pressed", String(b.dataset.mode === state.mode)); b.textContent = b.dataset.mode === "map" ? u("modeMap") : u("modeArchify"); });
    if (map) {
      live = [];
      var host = $("ov-host");
      var v = mountGraphPanel(host, { label: "HYDRA", legend: "overview", minFit: 0.4, node: state.ovSel, empty: '<p class="empty">' + esc(u("ovHint")) + "</p>", data: overviewGraph, filters: { key: "ov", list: OV_FILTERS, def: "main", data: overviewGraph }, inspector: overviewInspector, onSelect: function(id){ state.ovSel = id; } });
      host._ovView = v;
    } else {
      $("level-frame").src = FRAME_SRC[state.level][state.lang]; $("stage-hint").textContent = u("lvHint." + state.level);
      $("sidebar-title").textContent = u("sidebarTitle"); $("sidebar-sub").textContent = u("sidebarSub"); $("comp-search").placeholder = u("searchComp");
      $("toggle-sidebar").textContent = state.sidebar ? u("toggleHide") : u("toggleShow"); $("sidebar").classList.toggle("hidden", !state.sidebar);
      $("archify-note").textContent = u("archifyNote"); renderComponentList($("comp-search").value);
    }
  }
  function renderProcessesSection(){ live = []; mountExplorer($("proc-explorer"), {}); }

  // ------------------------------------------------------------------ chrome + render
  function renderChrome(){
    document.documentElement.lang = state.lang;
    var secKeys = { overview: "secOverview", capabilities: "secCaps", processes: "secProcs", tests: "secTests" };
    document.querySelectorAll("#section-seg button").forEach(function(b){ b.setAttribute("aria-pressed", String(b.dataset.section === state.section)); b.textContent = u(secKeys[b.dataset.section]); });
    var lvKeys = { user: "lvUser", functional: "lvFunc", technical: "lvTech" };
    document.querySelectorAll("#level-seg button").forEach(function(b){ b.setAttribute("aria-pressed", String(b.dataset.level === state.level)); b.textContent = u(lvKeys[b.dataset.level]); b.title = u("lvHint." + b.dataset.level); });
    document.querySelectorAll("#lang-seg button").forEach(function(b){ b.setAttribute("aria-pressed", String(b.dataset.lang === state.lang)); });
    ["overview", "capabilities", "processes", "tests"].forEach(function(s){ $("view-" + s).classList.toggle("hidden", state.section !== s); });
    $("footer-provenance").textContent = u("provenance"); $("footer-link-tech").textContent = u("openTab"); $("footer-link-tech").href = FRAME_SRC.technical[state.lang];
    $("level-banner").textContent = u("lvHint." + state.level); $("level-banner").setAttribute("data-level", state.level);
    document.body.setAttribute("data-section", state.section); document.body.setAttribute("data-level", state.level);
  }
  function renderAll(){
    renderChrome();
    if (state.section === "overview") renderOverview();
    else if (state.section === "capabilities") { ensureCap(); renderCapList(); renderCapDetail(); }
    else if (state.section === "tests") { live = []; if (window.HydraTestsUI) window.HydraTestsUI.render($("view-tests")); }
    else renderProcessesSection();
    updateUrl();
  }
  function updateUrl(){
    try {
      var p = new URLSearchParams(); if (/[?&]layoutdebug=1/.test(location.search)) p.set("layoutdebug", "1"); p.set("section", state.section); p.set("level", state.level); p.set("lang", state.lang);
      if (state.section === "capabilities" && state.cap) { p.set("capability", state.cap); p.set("tab", state.tab); }
      if (state.section === "processes" && state.proc) { p.set("process", state.proc); if (state.node) p.set("node", state.node); }
      if (state.section === "overview" && state.mode === "archify") p.set("mode", "archify");
      if (state.section === "tests" && window.HydraTestsUI) { var tp = window.HydraTestsUI.urlParams(); Object.keys(tp).forEach(function(k){ if (tp[k]) p.set(k, tp[k]); }); }
      history.replaceState(null, "", "?" + p.toString());
    } catch (e) {}
  }
  function go(o){
    if (o.cap) { state.cap = o.cap; state.section = "capabilities"; }
    if (o.tab) state.tab = o.tab;
    if (o.section) state.section = o.section;
    if (o.proc) { state.proc = o.proc; state.node = o.node || null; if (!o.tab) state.section = "processes"; }
    if (o.el) { state.pendingEl = o.el; state.elSel[o.cap || state.cap] = o.el; state.elQ = ""; }
    if (o.mech) { state.defSel[o.cap || state.cap] = o.mech; }
    if (o.comp) { state.compSel[o.cap || state.cap] = o.comp; state.implView = "list"; }
    if (o.mapSel) state.mapSel = o.mapSel;
    renderAll(); window.scrollTo(0, 0);
  }
  function elemMapNode(capId, elId){ var c = HM.caps[capId], m = c && c.map; if (!m) return null; for (var i = 0; i < m.nodes.length; i++) if (m.nodes[i].elem === elId) return m.nodes[i].id; return null; }

  function wire(){
    $("section-seg").addEventListener("click", function(e){ var b = e.target.closest("button[data-section]"); if (!b) return; state.section = b.dataset.section; renderAll(); });
    $("level-seg").addEventListener("click", function(e){ var b = e.target.closest("button[data-level]"); if (!b) return; state.level = b.dataset.level; save(); renderAll(); });
    $("lang-seg").addEventListener("click", function(e){ var b = e.target.closest("button[data-lang]"); if (!b) return; state.lang = b.dataset.lang; save(); renderAll(); });
    $("mode-seg").addEventListener("click", function(e){ var b = e.target.closest("button[data-mode]"); if (!b) return; state.mode = b.dataset.mode; renderAll(); });
    $("toggle-sidebar").addEventListener("click", function(){ state.sidebar = !state.sidebar; renderOverview(); });
    $("comp-search").addEventListener("input", function(e){ renderComponentList(e.target.value); });
    $("cap-list").addEventListener("click", function(e){ var c = e.target.closest("[data-cap-id]"); if (!c) return; state.cap = c.dataset.capId; state.tab = "overview"; state.mapSel = null; state.openMech = {}; renderAll(); });
    $("cap-list").addEventListener("keydown", function(e){ if (e.key === "Enter") { var c = e.target.closest("[data-cap-id]"); if (c) c.click(); } });
    document.body.addEventListener("input", function(e){
      if (e.target.id === "el-filter") { var q = e.target.value.trim().toLowerCase(); state.elQ = e.target.value; document.querySelectorAll("#el-list .md-item").forEach(function(a){ a.classList.toggle("hidden", !!q && a.dataset.s.indexOf(q) < 0); }); }
      if (e.target.id === "comp-filter") { var q2 = e.target.value.trim().toLowerCase(); document.querySelectorAll(".md-impl .md-item").forEach(function(a){ a.classList.toggle("hidden", !!q2 && a.dataset.s.indexOf(q2) < 0); }); }
    });
    document.body.addEventListener("click", function(e){
      var t = e.target;
      var tab = t.closest("#cap-tabs [data-tab]"); if (tab) { state.tab = tab.dataset.tab; renderCapDetail(); updateUrl(); return; }
      var sl = t.closest("[data-set-level]"); if (sl) { state.level = sl.dataset.setLevel; save(); renderAll(); return; }
      var gcomp = t.closest("[data-go-comp]"); if (gcomp) { var pr = gcomp.dataset.goComp.split(":"); go({ cap: pr[0], tab: "implementation", comp: pr[1] }); return; }
      var es = t.closest("[data-el-sel]"); if (es) { state.elSel[state.cap] = es.dataset.elSel; document.querySelectorAll("#el-list .md-item").forEach(function(a){ a.classList.toggle("active", a === es); }); $("el-detail").innerHTML = elDetailHtml(state.cap, es.dataset.elSel); return; }
      var ds = t.closest("[data-def-sel]"); if (ds) { state.defSel[state.cap] = ds.dataset.defSel; renderCapDetail(); return; }
      var cs = t.closest("[data-comp-sel]"); if (cs) { state.compSel[state.cap] = cs.dataset.compSel; var ff = compFind(cs.dataset.compSel, state.cap); document.querySelectorAll(".md-impl .md-item").forEach(function(a){ a.classList.toggle("active", a === cs); }); if (ff) $("comp-detail").innerHTML = compCard(ff.cap, ff.comp); return; }
      var iv = t.closest("[data-impl-view]"); if (iv) { state.implView = iv.dataset.implView; save(); renderCapDetail(); return; }
      if (t.closest("#cap-list-toggle")) { state.capList = !state.capList; save(); renderCapDetail(); return; }
      var ge = t.closest("[data-go-el]"); if (ge) { go({ cap: ge.dataset.goCap, tab: "elements", el: ge.dataset.goEl }); return; }
      var gm = t.closest("[data-go-map]"); if (gm) { go({ cap: gm.dataset.goCap, tab: "map", mapSel: elemMapNode(gm.dataset.goCap, gm.dataset.goMap) }); return; }
      var gd = t.closest("[data-go-define]"); if (gd) { go({ cap: gd.dataset.goCap || state.cap, tab: "define", mech: gd.dataset.goDefine }); return; }
      var gp = t.closest("[data-go-proc]"); if (gp) { go({ proc: gp.dataset.goProc, section: "processes" }); return; }
      var gc = t.closest("[data-go-cap]"); if (gc && !gc.hasAttribute("data-go-el") && !gc.hasAttribute("data-go-map") && !gc.hasAttribute("data-go-define")) { var id = gc.dataset.goCap; if (HM.caps[id]) go({ cap: id, tab: "overview" }); return; }
      var ov = t.closest("[data-ov-sel]"); if (ov) { var host = ov.closest(".gv-layout") ? ov.closest(".gv-layout").parentElement : null; if (host && host._select) host._select(ov.dataset.ovSel); return; }
      var sn = t.closest("[data-sel-node]");
      if (sn) { var holder = sn.closest(".px-view, .mech-proc"); if (holder && holder._select) holder._select(sn.dataset.selNode); return; }
      var tp = t.closest("[data-toggle-proc]"); if (tp) { toggleMech(tp, false); return; }
    });
  }

  function boot(){ if (state._tparams && window.HydraTestsUI) window.HydraTestsUI.restore(state._tparams); state._tparams = null; }
  document.addEventListener("DOMContentLoaded", function(){ wire(); boot(); renderAll(); });
  window.HydraPortalCtx = { state: state, u: u, esc: esc, tx: tx, capNameL: capNameL, pTitle: pTitle, nodeTitle: nodeTitle, procById: procById, isUser: isUser, isFunc: isFunc, isTech: isTech, renderAll: renderAll, save: save, urlSync: updateUrl };
  window.HydraPortal = { ready: function(){ return Promise.all(live.map(function(v){ return v.ready; })); }, views: function(){ return live; }, state: state, go: go, renderAll: renderAll, procGraph: procGraph, mapGraph: mapGraph, overviewGraph: overviewGraph, procById: procById, elemById: elemById, nodeTitle: nodeTitle, setState: function(o){ for (var k in o) state[k] = o[k]; renderAll(); } };
  if (document.readyState !== "loading") { wire(); boot(); renderAll(); }
})();
