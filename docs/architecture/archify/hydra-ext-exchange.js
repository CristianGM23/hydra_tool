(function () {
  "use strict";
  var HM = window.HM, T = HM.T;
  var FT = ["functional", "technical"], TECH = ["technical"];
  var NA = ["No aplica", "Not applicable"];
  var EV = "src/main/java/com/example/editor/";

  function t(x) { return T(x[0], x[1]); }
  // f = [tipo, sale, plataforma, formato, runtime, dispara, estado, impl]  (each [es,en])
  function F(f) {
    return [
      { n: T("Tipo de operación", "Operation type"), v: t(f[0]) },
      { n: T("Qué sale o entra", "What leaves or enters"), v: t(f[1]) },
      { n: T("Plataforma / herramienta destino u origen", "Target or source platform / tool"), v: t(f[2]) },
      { n: T("Formato o proyecto producido", "Format or project produced"), v: t(f[3]) },
      { n: T("Entorno externo necesario", "External environment required"), v: t(f[4]) },
      { n: T("Cómo se dispara", "How it is triggered"), v: t(f[5]), a: FT },
      { n: T("Estado de soporte", "Support status"), v: t(f[6]), a: FT },
      { n: T("Implementación", "Implementation"), v: t(f[7]), a: TECH }
    ];
  }
  // support-status helpers
  function ST(kind, es, en) {
    var m = { s: ["Soportado", "Supported"], p: ["Parcial", "Partial"], e: ["Externo", "External"], u: ["No verificado", "Unverified"] }[kind];
    return [m[0] + ": " + es, m[1] + ": " + en];
  }
  var OP = {
    imp: ["Importación", "Import"], exp: ["Exportación", "Export"], gen: ["Generación", "Generation"], exe: ["Ejecución externa", "External execution"],
    int: ["Integración", "Integration"], syn: ["Sincronización", "Synchronization"], pkg: ["Empaquetado", "Packaging"], dep: ["Despliegue", "Deployment"]
  };
  function op() { return [Array.prototype.slice.call(arguments).map(function (k) { return OP[k][0]; }).join(" + "), Array.prototype.slice.call(arguments).map(function (k) { return OP[k][1]; }).join(" + ")]; }
  var NONE_RT = ["Ninguno: todo ocurre dentro de HYDRA.", "None: everything happens inside HYDRA."];

  // spec: { n, d, k, f:[8 pairs] } -> element variant + facets + lens entry
  function build(id, specs) {
    return {
      variants: specs.map(function (s) { return { n: t(s.n), d: t(s.d), req: "ALTERNATIVE" }; }),
      facets: specs.map(function (s, i) { return { i: i, facets: F(s.f) }; }),
      lens: specs.map(function (s) { return { k: s.k, fd: T(s.d[0] + " " + s.f[6][0] + ".", s.d[1] + " " + s.f[6][1] + "."), td: t(s.f[7]) }; })
    };
  }

  // ================================================================================================
  // INTEGRATION
  // ================================================================================================
  var INT = [
    { n: ["Generar el proyecto nativo de una tecnología", "Generate the native project of a technology"],
      d: ["Compila el perfil de integración en un proyecto listo para abrir en la herramienta nativa elegida.", "Compiles the integration profile into a project ready to open in the chosen native tool."],
      k: "generate / TechnologyAdapter",
      f: [op("gen"),
        ["Sale un proyecto con los modelos, editores, paletas y pruebas del lenguaje de la persona; el proyecto no vuelve a entrar solo.", "A project leaves with the language's models, editors, palettes and tests; the project does not come back by itself."],
        ["Nueve tecnologías: EMF, Sirius de escritorio, Xtext, Langium, Eclipse GLSP, BESSER SDK, editor web de modelado BESSER, extensión de VS Code y mxGraph / Draw.io.", "Nine technologies: EMF, Sirius desktop, Xtext, Langium, Eclipse GLSP, BESSER SDK, BESSER web modeling editor, VS Code extension and mxGraph / Draw.io."],
        ["Un proyecto nativo con su propio sistema de construcción: Maven o Tycho (Eclipse, EMF, Xtext), npm (Langium, GLSP, VS Code, mxGraph, editor web BESSER) y pip (BESSER SDK).", "A native project with its own build system: Maven or Tycho (Eclipse, EMF, Xtext), npm (Langium, GLSP, VS Code, mxGraph, BESSER web editor) and pip (BESSER SDK)."],
        ["Para generar no hace falta nada externo. Para compilar o abrir el resultado: Java con Maven/Tycho, Node con npm o Python con pip, según la tecnología.", "Generating needs nothing external. To build or open the result: Java with Maven/Tycho, Node with npm or Python with pip, depending on the technology."],
        ["Etapa «Generar» del asistente de integración, tras configurar y analizar el perfil.", "“Generate” stage of the integration wizard, after configuring and analysing the profile."],
        ST("s", "la generación está implementada por tecnología; que el resultado compile se comprueba en la etapa de verificación y depende de herramientas externas.", "generation is implemented per technology; whether the result compiles is checked in the verify stage and depends on external tools."),
        ["TechnologyCompilerService con un adaptador por tecnología (TechnologyAdapterRegistry: Emf, Sirius, Xtext, Langium, EclipseGlsp, BesserSdk, BesserWebModelingEditor, VsCodeExtension, MxGraphDrawio y EclipseModelingDistribution); IntegrationBuildSystem declara MAVEN, TYCHO, NPM, PIP, etc. Resultado: TechnologyCompilationResult/TechnologyGeneratedFile.", "TechnologyCompilerService with one adapter per technology (TechnologyAdapterRegistry: Emf, Sirius, Xtext, Langium, EclipseGlsp, BesserSdk, BesserWebModelingEditor, VsCodeExtension, MxGraphDrawio and EclipseModelingDistribution); IntegrationBuildSystem declares MAVEN, TYCHO, NPM, PIP, etc. Result: TechnologyCompilationResult/TechnologyGeneratedFile."]] },
    { n: ["Comprobar las herramientas nativas instaladas", "Check the installed native tools"],
      d: ["Sondea si el equipo tiene las herramientas externas necesarias y emite un informe de preparación.", "Probes whether the machine has the required external tools and issues a readiness report."],
      k: "compatibility probe",
      f: [op("exe"),
        ["Sale una orden de comprobación hacia las herramientas del equipo; entra el informe (listo, con avisos, bloqueado, externo, no aplicable).", "A check command goes out to the machine's tools; the report comes in (ready, ready with warnings, blocked, external, not applicable)."],
        ["Cadenas de herramientas de cada tecnología (Java/Maven, Node/npm, Python, VS Code, etc.); el catálogo concreto de sondas no se revisó una a una.", "Toolchains of each technology (Java/Maven, Node/npm, Python, VS Code, etc.); the concrete probe catalog was not reviewed one by one."],
        ["Informe de compatibilidad (no se produce un proyecto).", "Compatibility report (no project is produced)."],
        ["Las propias herramientas que se sondean deben estar instaladas en el equipo donde corre el servidor de HYDRA.", "The tools being probed must be installed on the machine running the HYDRA server."],
        ["Etapa «Analizar» del asistente o una ejecución de compatibilidad bajo demanda.", "“Analyse” stage of the wizard or an on-demand compatibility run."],
        ST("p", "hay ejecutor de sondas y matriz de compatibilidad; qué sondas cubren cada tecnología no está verificado.", "there is a probe executor and a compatibility matrix; which probes cover each technology is not verified."),
        ["NativeToolchainCompatibilityOrchestrator → NativeToolchainProbeExecutor/ExternalToolchainCommandRunner; TechnologyCompatibilityMatrixService. NativeToolchainProbeCatalog no inspeccionado en detalle.", "NativeToolchainCompatibilityOrchestrator → NativeToolchainProbeExecutor/ExternalToolchainCommandRunner; TechnologyCompatibilityMatrixService. NativeToolchainProbeCatalog not inspected in detail."]] },
    { n: ["Previsualizar y aplicar sobre una carpeta destino", "Preview and apply onto a target folder"],
      d: ["Escribe los ficheros generados en un proyecto existente tras una vista previa, con registro para poder revertir.", "Writes the generated files into an existing project after a preview, with a record so it can be reverted."],
      k: "injection",
      f: [op("int", "dep"),
        ["Salen ficheros generados hacia la carpeta destino; HYDRA anota cuáles son suyos para no pisar lo escrito a mano.", "Generated files go out to the target folder; HYDRA records which ones are its own so it does not overwrite what was written by hand."],
        ["Cualquier proyecto nativo existente de las nueve tecnologías (carpeta local del servidor).", "Any existing native project of the nine technologies (a local folder on the server)."],
        ["Ficheros en la estructura del proyecto nativo, más un registro de propiedad y de reversión.", "Files in the native project's structure, plus an ownership and rollback record."],
        NONE_RT,
        ["Etapas «Previsualizar» y «Aplicar»; aplicar exige una vista previa lista y un registro de reversión.", "“Preview” and “Apply” stages; apply requires a ready preview and a rollback record."],
        ST("s", "vista previa, aplicación con política de inyección y reversión están implementadas.", "preview, application with an injection policy and rollback are implemented."),
        ["SafeTechnologyInjectionService (TechnologyInjectionPreview/ExecutionResult), InjectionPlan/InjectionPolicy/InjectionStrategy, InjectionManifest y ManagedInjectionFileIndex.", "SafeTechnologyInjectionService (TechnologyInjectionPreview/ExecutionResult), InjectionPlan/InjectionPolicy/InjectionStrategy, InjectionManifest and ManagedInjectionFileIndex."]] },
    { n: ["Ejecutar la herramienta nativa", "Run the native tool"],
      d: ["El proyecto generado se construye o arranca fuera de HYDRA, con su propia herramienta.", "The generated project is built or started outside HYDRA, with its own tool."],
      k: "execute / external",
      f: [op("exe"),
        ["Entra el resultado de esa ejecución solo como informe o como cambios que luego se sincronizan; HYDRA no controla el proceso externo.", "The outcome of that run comes in only as a report or as changes that are synchronized afterwards; HYDRA does not control the external process."],
        ["La herramienta propia de cada tecnología (Eclipse/Tycho, Maven, npm, VS Code, Python, navegador con Draw.io).", "Each technology's own tool (Eclipse/Tycho, Maven, npm, VS Code, Python, a browser with Draw.io)."],
        ["Depende de la herramienta: aplicación Eclipse, extensión instalable, servidor web local, paquete de Python.", "Depends on the tool: an Eclipse application, an installable extension, a local web server, a Python package."],
        ["Obligatorio y externo: la herramienta nativa y sus dependencias (Java, Node, Python…).", "Required and external: the native tool and its dependencies (Java, Node, Python…)."],
        ["Etapa «Ejecutar»: el informe de preparación la marca como «externa»; la persona la lanza.", "“Run” stage: the readiness report marks it as “external”; the person launches it."],
        ST("e", "se ejecuta siempre fuera; si HYDRA además puede lanzar el proceso por la persona no está verificado.", "it always runs outside; whether HYDRA can also launch the process on the person's behalf is not verified."),
        ["Etapa de entrega de ejecución y estado de preparación EXTERNAL en IntegrationCheckReport; no se verificó ningún lanzador de procesos propio más allá de ExternalToolchainCommandRunner (sondas).", "Execution delivery stage and the EXTERNAL readiness state in IntegrationCheckReport; no process launcher of its own was verified beyond ExternalToolchainCommandRunner (probes)."]] },
    { n: ["Sincronizar los cambios hechos fuera", "Synchronize changes made outside"],
      d: ["Compara el proyecto nativo con lo que HYDRA generó y propone añadir, regenerar, conservar o fusionar.", "Compares the native project with what HYDRA generated and proposes to add, regenerate, keep or merge."],
      k: "roundtrip",
      f: [op("syn"),
        ["Entran decisiones sobre los ficheros editados a mano (conservar HYDRA, fusión segura); salen ficheros regenerados. Ocho estados posibles por elemento.", "Decisions come in about hand-edited files (keep HYDRA, safe merge); regenerated files go out. Eight possible states per element."],
        ["El proyecto nativo en carpeta local (cualquiera de las nueve tecnologías).", "The native project in a local folder (any of the nine technologies)."],
        ["Los mismos ficheros del proyecto nativo, actualizados, y un manifiesto de sincronización.", "The same native-project files, updated, and a synchronization manifest."],
        NONE_RT,
        ["Etapa «Sincronizar» del asistente.", "“Synchronize” stage of the wizard."],
        ST("s", "detecta y decide; la fusión se limita a los casos seguros y el resto queda como conflicto manual.", "it detects and decides; merging is limited to safe cases and the rest remains a manual conflict."),
        ["Manifiesto de round-trip de integración con estados añadir/regenerar/sin cambios/borrado/huérfano/conflicto y decisiones KEEP_HYDRA y SAFE_MERGE; ver elemento roundtrip.", "Integration round-trip manifest with add/regenerate/unchanged/deleted/orphan/conflict states and KEEP_HYDRA and SAFE_MERGE decisions; see the roundtrip element."]] },
    { n: ["Exportar una proyección o un archivo de compilación", "Export a projection or a compilation archive"],
      d: ["Saca el proyecto generado o una vista de él (PlantUML, Draw.io) fuera de HYDRA.", "Takes the generated project or a view of it (PlantUML, Draw.io) out of HYDRA."],
      k: "export / CanonicalProjectionFormat",
      f: [op("exp", "pkg"),
        ["Sale el proyecto compilado empaquetado o una proyección del modelo para documentar.", "The compiled project packaged, or a projection of the model for documentation, goes out."],
        ["Draw.io y PlantUML para la proyección; cualquier lugar de la persona para el archivo.", "Draw.io and PlantUML for the projection; wherever the person wants for the archive."],
        ["Archivo comprimido con manifiesto de compilación; proyecciones en PlantUML o Draw.io.", "Compressed archive with a compilation manifest; projections in PlantUML or Draw.io."],
        NONE_RT,
        ["Etapa «Exportar» del asistente.", "“Export” stage of the wizard."],
        ST("s", "la exportación del archivo y las dos proyecciones existen; cuántas tecnologías exponen la proyección no se verificó.", "archive export and the two projections exist; how many technologies expose the projection was not verified."),
        ["TechnologyCompilationArchiveService/Manifest y CanonicalProjectionFormat (PLANTUML, DRAWIO) en integration.projection.canonical.", "TechnologyCompilationArchiveService/Manifest and CanonicalProjectionFormat (PLANTUML, DRAWIO) in integration.projection.canonical."]] },
    { n: ["Importar un archivo de compilación", "Import a compilation archive"],
      d: ["Vuelve a abrir un resultado de compilación exportado antes.", "Reopens a compilation result exported earlier."],
      k: "archive import",
      f: [op("imp"),
        ["Entra un archivo de compilación con su manifiesto y sus ficheros.", "A compilation archive with its manifest and files comes in."],
        ["HYDRA mismo (origen: otra instalación o una exportación previa).", "HYDRA itself (source: another installation or an earlier export)."],
        ["Archivo comprimido con manifiesto de compilación.", "Compressed archive with a compilation manifest."],
        NONE_RT,
        ["Subida de archivo desde la pantalla de integración; la pantalla concreta no se verificó.", "File upload from the integration screen; the concrete screen was not verified."],
        ST("u", "existe el resultado de importación en el código; no se verificó su punto de entrada de usuario.", "an import result exists in the code; its user entry point was not verified."),
        ["TechnologyCompilationArchiveImportResult y TechnologyCompilationArchiveService; endpoint y UI no verificados.", "TechnologyCompilationArchiveImportResult and TechnologyCompilationArchiveService; endpoint and UI not verified."]] },
    { n: ["Exportar un proyecto completo desde un metamodelo validado", "Export a complete project from a validated metamodel"],
      d: ["Ocho destinos de proyecto completo directamente desde el metamodelo, sin pasar por el asistente.", "Eight complete-project targets straight from the metamodel, without the wizard."],
      k: "MetamodelProjectExportKind",
      f: [op("exp", "gen"),
        ["Sale un proyecto completo; el metamodelo debe estar validado.", "A complete project goes out; the metamodel must be validated."],
        ["Eclipse EMF, Eclipse GLSP, distribución Eclipse Modeling, extensión de VS Code, mxGraph / Draw.io, Xtext, Langium, BESSER SDK y editor web BESSER.", "Eclipse EMF, Eclipse GLSP, Eclipse Modeling distribution, VS Code extension, mxGraph / Draw.io, Xtext, Langium, BESSER SDK and BESSER web editor."],
        ["Un espacio de trabajo por destino: Ecore y proyectos Eclipse (Tycho), cliente y servidor GLSP (npm), producto Eclipse Modeling (Tycho), extensión de VS Code (npm), proyectos Xtext (Maven), proyecto Langium con servidor, CLI y trabajador web (npm), paquete Python con B-UML (pip).", "One workspace per target: Ecore and Eclipse projects (Tycho), GLSP client and server (npm), Eclipse Modeling product (Tycho), VS Code extension (npm), Xtext projects (Maven), Langium project with server, CLI and web worker (npm), Python package with B-UML (pip)."],
        ["Para generar, ninguno. Para compilar: Maven/Tycho y Java, Node/npm o Python/pip según el destino; cinco destinos requieren además el editor de HYDRA.", "To generate, none. To build: Maven/Tycho and Java, Node/npm or Python/pip depending on the target; five targets also require the HYDRA editor."],
        ["Descarga desde el diseñador de metamodelos, en la exportación de «proyecto completo».", "Download from the metamodel designer, in the “complete project” export."],
        ST("s", "enumerado con ocho destinos; el estado de calidad de cada generador no se midió aquí.", "enumerated with eight targets; the quality state of each generator was not measured here."),
        ["MetamodelProjectExportKind (emf-eclipse, eclipse-glsp, eclipse-modeling, vscode-modeling, mxgraph-drawio, xtext, langium, besser-desktop, besser-web) con IntegrationBuildSystem y requiresEditor.", "MetamodelProjectExportKind (emf-eclipse, eclipse-glsp, eclipse-modeling, vscode-modeling, mxgraph-drawio, xtext, langium, besser-desktop, besser-web) with IntegrationBuildSystem and requiresEditor."]] },
    { n: ["Modos de entrega frente al anfitrión", "Delivery modes toward the host"],
      d: ["Cómo se integra lo generado con la aplicación anfitriona: incrustado, remoto, híbrido, módulo generado o solo proyección.", "How the generated output integrates with the host application: embedded, remote, hybrid, generated module or projection only."],
      k: "IntegrationDeliveryMode",
      f: [op("int"),
        ["Entran y salen modelos, acciones, eventos, validaciones y notificaciones entre HYDRA y el anfitrión mediante puentes.", "Models, actions, events, validations and notifications flow between HYDRA and the host through bridges."],
        ["La aplicación anfitriona de cada tecnología (por ejemplo un editor Eclipse o una extensión de VS Code).", "Each technology's host application (for example an Eclipse editor or a VS Code extension)."],
        ["Un módulo o puente según el modo: incrustado, remoto, híbrido, módulo generado o solo proyección.", "A module or bridge depending on the mode: embedded, remote, hybrid, generated module or projection only."],
        ["La aplicación anfitriona y, en modo remoto o híbrido, un servidor de HYDRA accesible.", "The host application and, in remote or hybrid mode, a reachable HYDRA server."],
        ["Se elige al crear el perfil (segundo paso del asistente).", "Chosen when creating the profile (second wizard step)."],
        ST("p", "los cinco modos están definidos; qué tecnologías admiten cada uno no se analizó.", "the five modes are defined; which technologies support each was not analysed."),
        ["IntegrationDeliveryMode (EMBEDDED, REMOTE, HYBRID, GENERATED_MODULE, PROJECTION_ONLY) y puentes Host*Bridge (modelo, acción, evento, validación, transacción, persistencia, UI).", "IntegrationDeliveryMode (EMBEDDED, REMOTE, HYBRID, GENERATED_MODULE, PROJECTION_ONLY) and the Host*Bridge classes (model, action, event, validation, transaction, persistence, UI)."]] },
    { n: ["Importar un metamodelo desde un formato externo", "Import a metamodel from an external format"],
      d: ["21 formatos de entrada con tres niveles de fidelidad: exacta, semántica y estructural.", "21 input formats with three fidelity levels: exact, semantic and structural."],
      k: "MetamodelImportFormat",
      f: [op("imp"),
        ["Entra un metamodelo, gramática, esquema, código o documentación y se reconstruye el metamodelo; según el formato se pierde algo (ver fidelidad).", "A metamodel, grammar, schema, code or documentation comes in and the metamodel is rebuilt; depending on the format something is lost (see fidelity)."],
        ["Eclipse EMF (Ecore, EMF/Emfatic, XMI), JSON, XML, Draw.io, Langium, Xtext, ANTLR, SQL DDL, Java, TypeScript, JavaScript, Go, Python, Blockly (JSON y JavaScript), GLSP, PlantUML, Markdown y Markdown para agentes.", "Eclipse EMF (Ecore, EMF/Emfatic, XMI), JSON, XML, Draw.io, Langium, Xtext, ANTLR, SQL DDL, Java, TypeScript, JavaScript, Go, Python, Blockly (JSON and JavaScript), GLSP, PlantUML, Markdown and agent Markdown."],
        ["Un metamodelo de HYDRA. Fidelidad exacta: Ecore, XMI, JSON; semántica: EMF, XML, Draw.io, SQL, Java, TypeScript, Go, Python; estructural: las demás.", "A HYDRA metamodel. Exact fidelity: Ecore, XMI, JSON; semantic: EMF, XML, Draw.io, SQL, Java, TypeScript, Go, Python; structural: the rest."],
        NONE_RT,
        ["Importación en el diseñador de metamodelos: subir un archivo o pegar texto.", "Import in the metamodel designer: upload a file or paste text."],
        ST("s", "21 formatos implementados; los de código y documentación solo recuperan lo que esos formatos expresan.", "21 formats implemented; code and documentation formats only recover what those formats express."),
        ["MetamodelImportFormat (enum con nivel EXACT/SEMANTIC/STRUCTURAL y extensiones); ver elemento import_format de metamodelo.", "MetamodelImportFormat (enum with EXACT/SEMANTIC/STRUCTURAL level and extensions); see the metamodel import_format element."]] },
    { n: ["Exportar un metamodelo a un formato externo", "Export a metamodel to an external format"],
      d: ["20 formatos individuales más un paquete completo: semánticos, gramáticas, base de datos, documentación, diagramas y código.", "20 individual formats plus a complete bundle: semantic, grammars, database, documentation, diagrams and code."],
      k: "MetamodelExportFormat",
      f: [op("exp", "gen"),
        ["Sale el metamodelo en el formato elegido, o todos juntos en un paquete.", "The metamodel goes out in the chosen format, or all together in a bundle."],
        ["Eclipse EMF (Ecore), Langium, Xtext, ANTLR, bases de datos SQL, PlantUML, Markdown, Draw.io, SVG, PNG, Java, TypeScript, JavaScript, Go, Python y Blockly.", "Eclipse EMF (Ecore), Langium, Xtext, ANTLR, SQL databases, PlantUML, Markdown, Draw.io, SVG, PNG, Java, TypeScript, JavaScript, Go, Python and Blockly."],
        ["Un archivo por formato (Ecore, XML, JSON, gramática, SQL, PlantUML, Markdown, Draw.io, SVG, PNG, Blockly) o un ZIP de código (Java, TypeScript, JavaScript, Go, Python); más un paquete ZIP con todo.", "One file per format (Ecore, XML, JSON, grammar, SQL, PlantUML, Markdown, Draw.io, SVG, PNG, Blockly) or a code ZIP (Java, TypeScript, JavaScript, Go, Python); plus a ZIP bundle with everything."],
        ["Ninguno para exportar; para usar el resultado, la herramienta del formato (Eclipse, base de datos, compilador, etc.).", "None to export; to use the result, the format's tool (Eclipse, a database, a compiler, etc.)."],
        ["Exportación en el diseñador de metamodelos.", "Export in the metamodel designer."],
        ST("s", "formatos enumerados con extensión y tipo de contenido; el paquete completo reúne los 19 marcados por defecto.", "formats enumerated with extension and content type; the complete bundle gathers the 19 marked by default."),
        ["MetamodelExportFormat (ECORE, XML, JSON, LANGIUM, XTEXT, ANTLR, SQL_DDL, PLANTUML, MARKDOWN, MARKDOWN_AGENT, DRAWIO, SVG, PNG, JAVA, TYPESCRIPT, JAVASCRIPT, GO, PYTHON, BLOCKLY_JSON, BLOCKLY_JAVASCRIPT, BUNDLE).", "MetamodelExportFormat (ECORE, XML, JSON, LANGIUM, XTEXT, ANTLR, SQL_DDL, PLANTUML, MARKDOWN, MARKDOWN_AGENT, DRAWIO, SVG, PNG, JAVA, TYPESCRIPT, JAVASCRIPT, GO, PYTHON, BLOCKLY_JSON, BLOCKLY_JAVASCRIPT, BUNDLE)."]] },
    { n: ["Exportar una transformación a otro lenguaje", "Export a transformation to another language"],
      d: ["Cuatro familias: declarativas, plantillas, código nativo y paquete con el motor de HYDRA.", "Four families: declarative, templates, native code and a package with the HYDRA engine."],
      k: "TransformationExportLanguage",
      f: [op("exp", "gen"),
        ["Sale la transformación reescrita en el lenguaje destino; algunas construcciones no se pueden expresar y la exportación es parcial.", "The transformation goes out rewritten in the target language; some constructs cannot be expressed and the export is partial."],
        ["ATL y ETL (modelo a modelo), Acceleo y EGL (modelo a texto), Java, Xtend, Python, Go, JavaScript y TypeScript, o el motor propio de HYDRA.", "ATL and ETL (model to model), Acceleo and EGL (model to text), Java, Xtend, Python, Go, JavaScript and TypeScript, or HYDRA's own engine."],
        ["Fichero o proyecto en el lenguaje elegido. Fidelidad: exacta, parcial (subconjunto) o ejecución canónica con el motor de HYDRA.", "File or project in the chosen language. Fidelity: exact, partial (subset) or canonical execution with the HYDRA engine."],
        ["El motor del lenguaje destino para ejecutarlo (por ejemplo ATL, Eclipse Acceleo o Epsilon para ETL/EGL, o la herramienta de cada lenguaje de programación).", "The target language's engine to run it (for example ATL, Eclipse Acceleo or Epsilon for ETL/EGL, or each programming language's tool)."],
        ["Exportación desde el diseñador de transformaciones.", "Export from the transformation designer."],
        ST("p", "las ramas declarativas y de plantillas son parciales; el código nativo es exacto si se genera y parcial si queda bloqueado.", "the declarative and template branches are partial; native code is exact if generated and partial if blocked."),
        ["TransformationExportLanguage (ATL, ETL, JAVA, XTEND, ACCELEO, EGL, PYTHON, GO, JAVASCRIPT, TYPESCRIPT) y TransformationExportFidelity (EXACT, CANONICAL_RUNTIME, SUBSET).", "TransformationExportLanguage (ATL, ETL, JAVA, XTEND, ACCELEO, EGL, PYTHON, GO, JAVASCRIPT, TYPESCRIPT) and TransformationExportFidelity (EXACT, CANONICAL_RUNTIME, SUBSET)."]] },
    { n: ["Generar el proyecto de un lenguaje desde una gramática", "Generate a language project from a grammar"],
      d: ["Una gramática produce un proyecto Xtext, Langium o ANTLR que se descarga como archivo.", "A grammar produces an Xtext, Langium or ANTLR project that is downloaded as an archive."],
      k: "GeneratedGrammarProject",
      f: [op("gen", "exp", "exe"),
        ["Sale un proyecto de lenguaje con analizador y servicios; la ejecución del proyecto ocurre fuera de HYDRA.", "A language project with parser and services goes out; running the project happens outside HYDRA."],
        ["Xtext (solo Java con Maven), Langium (solo TypeScript con npm) y ANTLR (Java o TypeScript).", "Xtext (Java with Maven only), Langium (TypeScript with npm only) and ANTLR (Java or TypeScript)."],
        ["Proyecto Xtext con flujo de generación y módulos Maven; proyecto Langium TypeScript con npm; proyecto ANTLR con analizador y visitante u oyente. Se descarga en un ZIP.", "Xtext project with generation workflow and Maven modules; Langium TypeScript project with npm; ANTLR project with parser and visitor or listener. Downloaded as a ZIP."],
        ["Para compilar o ejecutar: Java y Maven (Xtext), Node y npm (Langium), Java o Node (ANTLR).", "To build or run: Java and Maven (Xtext), Node and npm (Langium), Java or Node (ANTLR)."],
        ["Descarga desde el diseñador de gramáticas.", "Download from the grammar designer."],
        ST("s", "generación y descarga implementadas; la compilación del proyecto resultante es externa.", "generation and download are implemented; building the resulting project is external."),
        ["GrammarCompilationController (descarga ZIP) con el andamio de cada proveedor; ver elemento gen_project de gramática.", "GrammarCompilationController (ZIP download) with each provider's scaffold; see the grammar gen_project element."]] }
  ];

  var OVERVIEW = [
    { n: ["Importar", "Import"], d: ["Metamodelo: 21 formatos; recuperación de proyectos externos; paquetes de capacidad; proyecto o capa desde almacenamiento remoto; repositorio Git.", "Metamodel: 21 formats; external project recovery; capability packages; project or layer from remote storage; Git repository."], k: "import" },
    { n: ["Exportar", "Export"], d: ["Metamodelo: 20 formatos y paquete; transformaciones a otros lenguajes; paquetes de capacidad; copia de seguridad; proyecto o capa a almacenamiento remoto.", "Metamodel: 20 formats and bundle; transformations to other languages; capability packages; backup; project or layer to remote storage."], k: "export" },
    { n: ["Generar", "Generate"], d: ["Integración de tecnologías (nueve), proyecto completo desde el metamodelo (ocho destinos), proyecto de lenguaje desde gramática (Xtext, Langium, ANTLR), código desde transformaciones y proyectos generados.", "Technology integration (nine), complete project from the metamodel (eight targets), language project from grammar (Xtext, Langium, ANTLR), code from transformations and generated projects."], k: "generate" },
    { n: ["Ejecutar fuera", "Execute externally"], d: ["Integración (etapa «Ejecutar» y sondas de herramientas), proyectos generados de gramática y de integración, motores de transformaciones exportadas.", "Integration (“Run” stage and tool probes), generated grammar and integration projects, engines of exported transformations."], k: "execute" },
    { n: ["Integrar", "Integrate"], d: ["Integración de tecnologías: modos de entrega y puentes con la aplicación anfitriona; autenticación con proveedores externos.", "Technology integration: delivery modes and bridges with the host application; authentication with external providers."], k: "integrate" },
    { n: ["Sincronizar", "Synchronize"], d: ["Repositorios externos (Git completo y conflictos) y sincronización de proyectos nativos de integración.", "External repositories (full Git and conflicts) and synchronization of native integration projects."], k: "synchronize" },
    { n: ["Empaquetar", "Package"], d: ["Publicación (paquetes ZIP del editor, de fuentes y web desplegable; distribución autónoma) y portabilidad (paquete de capacidad).", "Publication (editor, source and deployable-web ZIP packages; standalone distribution) and portability (capability package)."], k: "package" },
    { n: ["Desplegar", "Deploy"], d: ["Publicación: web instalable (PWA) y escritorio con Tauri (Windows, macOS, Linux); aplicar un proyecto en una carpeta destino. El despliegue final en un servidor lo hace la persona.", "Publication: installable web (PWA) and desktop with Tauri (Windows, macOS, Linux); applying a project into a target folder. Final deployment to a server is done by the person."], k: "deploy" }
  ];

  // ================================================================================================
  // PORTABILITY
  // ================================================================================================
  var POR = [
    { n: ["Exportar un paquete de capacidad", "Export a capability package"],
      d: ["Empaqueta una o más capas (metamodelo, gramática, editor textual, estilo, representación visual, paleta) en un archivo.", "Packs one or more layers (metamodel, grammar, text editor, syntax style, visual representation, palette) into an archive."],
      k: "capability-package export",
      f: [op("exp", "pkg"),
        ["Salen las capas elegidas con sus dependencias y una firma de integridad.", "The chosen layers go out with their dependencies and an integrity signature."],
        ["Otra instalación de HYDRA.", "Another HYDRA installation."],
        ["Archivo comprimido propio de HYDRA (tipo de contenido propio, extensión de capacidad).", "HYDRA's own compressed archive (own content type, capability extension)."],
        NONE_RT,
        ["Descarga del paquete desde la pantalla de portabilidad; el punto de entrada de interfaz exacto no se verificó.", "Package download from the portability screen; the exact interface entry point was not verified."],
        ST("s", "exportación implementada con identificación por hash de contenido y firma; la interfaz de usuario no se verificó.", "export implemented with content-hash identification and signature; the user interface was not verified."),
        ["CapabilityPackageArchiveController (POST y GET /api/capability-packages/archives, application/vnd.hydra.capability-package+zip, nombre «<hash>.hydra-cap») y CapabilityPackageArchiveService.", "CapabilityPackageArchiveController (POST and GET /api/capability-packages/archives, application/vnd.hydra.capability-package+zip, file name “<hash>.hydra-cap”) and CapabilityPackageArchiveService."]] },
    { n: ["Importar un paquete: validar y previsualizar", "Import a package: validate and preview"],
      d: ["Comprueba firma, límites y compatibilidad y calcula qué cambiaría, sin modificar nada.", "Checks signature, limits and compatibility and computes what would change, without modifying anything."],
      k: "archive import / preview",
      f: [op("imp"),
        ["Entra un paquete de capacidad; no se toca el proyecto hasta aplicar.", "A capability package comes in; the project is not touched until applying."],
        ["Otra instalación de HYDRA (origen).", "Another HYDRA installation (source)."],
        ["Informe de problemas del paquete (error, aviso, información) y vista previa de la transacción.", "Package problem report (error, warning, information) and transaction preview."],
        NONE_RT,
        ["Subida del archivo (formulario multiparte) y petición de vista previa.", "File upload (multipart form) and a preview request."],
        ST("s", "validación y vista previa implementadas; rechaza paquetes inhabilitados, inválidos, dañados, firma errónea o límites excedidos.", "validation and preview implemented; rejects disabled, invalid, corrupt, wrongly signed or over-limit packages."),
        ["CapabilityPackageArchiveController POST /import, CapabilityPackageChecker, CapabilityLayerTransactionService (modo preview).", "CapabilityPackageArchiveController POST /import, CapabilityPackageChecker, CapabilityLayerTransactionService (preview mode)."]] },
    { n: ["Importar un paquete: aplicar con fusión semántica", "Import a package: apply with semantic merge"],
      d: ["Modifica los documentos del proyecto; ante conflictos se decide conservar, usar lo recuperado, fusionar u omitir.", "Modifies the project's documents; on conflicts you decide to keep, use the recovered one, merge or skip."],
      k: "layer transaction apply / merge",
      f: [op("imp"),
        ["Entran las capas del paquete a los documentos del proyecto.", "The package's layers come into the project's documents."],
        ["Otra instalación de HYDRA (origen).", "Another HYDRA installation (source)."],
        ["Documentos del proyecto actualizados y un registro de la transacción.", "Updated project documents and a transaction journal."],
        NONE_RT,
        ["Acción «Aplicar» tras la vista previa.", "“Apply” action after the preview."],
        ST("s", "aplicación con registro de transacción; la fusión semántica y sus decisiones por conflicto están implementadas.", "application with a transaction journal; semantic merge and its per-conflict decisions are implemented."),
        ["CapabilityLayerTransactionService (apply), CapabilitySemanticMergeService, CapabilityOrchestrationService.", "CapabilityLayerTransactionService (apply), CapabilitySemanticMergeService, CapabilityOrchestrationService."]] },
    { n: ["Revertir una importación", "Revert an import"],
      d: ["Deshace lo que cambió una transacción de capa concreta.", "Undoes what one specific layer transaction changed."],
      k: "layer transaction revert",
      f: [op("imp"),
        ["Salen de nuevo las capas que esa transacción había metido; no afecta a otros cambios.", "The layers that transaction had brought in go out again; other changes are unaffected."],
        ["No aplica: operación interna.", "Not applicable: internal operation."],
        ["Documentos del proyecto restaurados.", "Restored project documents."],
        NONE_RT,
        ["Acción «Revertir» sobre una transacción registrada.", "“Revert” action on a recorded transaction."],
        ST("s", "reversión implementada a partir del registro de transacción.", "rollback implemented from the transaction journal."),
        ["CapabilityLayerTransactionController/Service (modo revert) y registro de transacción.", "CapabilityLayerTransactionController/Service (revert mode) and the transaction journal."]] }
  ];

  // ================================================================================================
  // PUBLICATION
  // ================================================================================================
  var PUB = [
    { n: ["Preparar una distribución autónoma (web instalable o escritorio)", "Prepare a standalone distribution (installable web or desktop)"],
      d: ["Planifica, materializa y valida los ficheros de una aplicación instalable: PWA o escritorio con Tauri.", "Plans, materializes and validates the files of an installable application: PWA or desktop with Tauri."],
      k: "StandaloneDistributionTarget",
      f: [op("gen", "pkg"),
        ["Sale un conjunto de ficheros de distribución con su informe de validación (los errores bloquean el empaquetado).", "A set of distribution files goes out with its validation report (errors block packaging)."],
        ["Navegador (web instalable), Windows, macOS y Linux (Tauri).", "Browser (installable web), Windows, macOS and Linux (Tauri)."],
        ["Web instalable: manifiesto y trabajador sin conexión. Escritorio: un proyecto Tauri por sistema operativo. HYDRA no genera el instalador final.", "Installable web: manifest and offline worker. Desktop: one Tauri project per operating system. HYDRA does not build the final installer."],
        ["Para construir el instalador de escritorio: la cadena de Tauri (Rust y Node). Que HYDRA la ejecute no está verificado.", "To build the desktop installer: the Tauri toolchain (Rust and Node). Whether HYDRA runs it is not verified."],
        ["Pantalla de distribución autónoma: planificar, materializar, validar.", "Standalone distribution screen: plan, materialize, validate."],
        ST("p", "se generan y validan los ficheros del proyecto; compilar el instalador y firmarlo queda fuera.", "the project files are generated and validated; building and signing the installer is left out."),
        ["StandaloneDistributionController (/api/standalone-distributions: plan, materialize, validate, release-readiness), StandaloneDistributionPlanner y StandaloneDistributionTarget (PWA, TAURI_WINDOWS, TAURI_MACOS, TAURI_LINUX).", "StandaloneDistributionController (/api/standalone-distributions: plan, materialize, validate, release-readiness), StandaloneDistributionPlanner and StandaloneDistributionTarget (PWA, TAURI_WINDOWS, TAURI_MACOS, TAURI_LINUX)."]] },
    { n: ["Empaquetar el editor en un ZIP", "Package the editor into a ZIP"],
      d: ["Genera un paquete ZIP del editor; exige una validación válida.", "Generates a ZIP package of the editor; requires a valid validation."],
      k: "package",
      f: [op("pkg", "exp"),
        ["Sale el editor empaquetado.", "The packaged editor goes out."],
        ["Cualquier máquina que vaya a alojar o abrir el editor.", "Any machine that will host or open the editor."],
        ["Archivo ZIP.", "ZIP file."],
        NONE_RT,
        ["Acción «Empaquetar» en la pantalla de distribución, tras validar.", "“Package” action on the distribution screen, after validating."],
        ST("s", "implementado; no despliega nada.", "implemented; it deploys nothing."),
        ["StandaloneDistributionController POST /package (application/zip).", "StandaloneDistributionController POST /package (application/zip)."]] },
    { n: ["Empaquetar las fuentes de lanzamiento", "Package the release sources"],
      d: ["ZIP con las fuentes para lanzar una versión; exige preparación válida y se genera fuera de la carpeta del editor.", "ZIP with the sources to release a version; requires valid readiness and is generated outside the editor folder."],
      k: "package-release-source",
      f: [op("pkg", "exp"),
        ["Salen las fuentes de la versión.", "The version's sources go out."],
        ["Quien compile o publique la versión.", "Whoever builds or publishes the version."],
        ["Archivo ZIP de fuentes.", "Source ZIP file."],
        ["Para construir la versión a partir de ese ZIP: el entorno de compilación del propio proyecto (Java y Node).", "To build the release from that ZIP: the project's own build environment (Java and Node)."],
        ["Acción de empaquetado de fuentes tras la comprobación de preparación para producción.", "Source-packaging action after the production readiness check."],
        ST("s", "implementado.", "implemented."),
        ["StandaloneDistributionController POST /package-release-source (application/zip).", "StandaloneDistributionController POST /package-release-source (application/zip)."]] },
    { n: ["Empaquetar la web desplegable", "Package the deployable web"],
      d: ["ZIP con el editor ya construido para alojar en un servidor web; solo con destino web instalable y preparación válida.", "ZIP with the already built editor to host on a web server; only with the installable-web target and valid readiness."],
      k: "package-deployable-web",
      f: [op("pkg", "dep"),
        ["Salen los activos web con una huella y un descriptor de «activos web desplegables».", "The web assets go out with a fingerprint and a “deployable web assets” descriptor."],
        ["Cualquier servidor web estático o alojamiento web.", "Any static web server or web hosting."],
        ["Archivo ZIP con el editor construido.", "ZIP file with the built editor."],
        ["Un servidor web propio de la persona que sirva los ficheros (HTTPS para la web instalable).", "The person's own web server to serve the files (HTTPS for the installable web)."],
        ["Acción de empaquetado web, solo si el destino es web instalable.", "Web-packaging action, only if the target is installable web."],
        ST("p", "HYDRA empaqueta pero no sube ni despliega: el despliegue lo hace la persona.", "HYDRA packages but does not upload or deploy: the person deploys."),
        ["StandaloneDistributionController POST /package-deployable-web (application/zip).", "StandaloneDistributionController POST /package-deployable-web (application/zip)."]] },
    { n: ["Publicar una versión y comprobar actualizaciones", "Publish a version and check for updates"],
      d: ["Valida una petición de publicación, la publica y permite consultar si hay una versión más nueva.", "Validates a publication request, publishes it and lets you check whether a newer version exists."],
      k: "publish / check",
      f: [op("dep", "exp"),
        ["Sale una publicación; entra el resultado de la comprobación de actualización.", "A publication goes out; the update check result comes in."],
        ["No verificado: el destino concreto de publicación (canal, repositorio o sitio) no se revisó.", "Unverified: the concrete publication destination (channel, repository or site) was not reviewed."],
        ["No verificado: el resultado se devuelve como descriptor de publicación.", "Unverified: the result is returned as a publication descriptor."],
        ["No verificado.", "Unverified."],
        ["Validar, publicar y comprobar desde la pantalla de publicación; la puerta de publicación verificada actúa antes.", "Validate, publish and check from the publication screen; the verified publication gate acts first."],
        ST("u", "hay validación, publicación y comprobación de actualización; el destino real no se verificó.", "validation, publication and update check exist; the real destination was not verified."),
        ["DistributionPublicationController (/api/publications: validate, publish, check).", "DistributionPublicationController (/api/publications: validate, publish, check)."]] }
  ];

  // ================================================================================================
  // RECOVERY
  // ================================================================================================
  var REC = [
    { n: ["Recuperar un proyecto desde un archivo o carpeta local", "Recover a project from a local file or folder"],
      d: ["Sube un proyecto externo desde tu equipo y HYDRA descubre y reconstruye sus metamodelos.", "Upload an external project from your machine and HYDRA discovers and rebuilds its metamodels."],
      k: "source: local",
      f: [op("imp"),
        ["Entra el proyecto externo; se buscan metamodelos por seis métodos: metamodelo directo, modelo de generación, registro de plugins, bytecode EMF y dos inferencias desde código.", "The external project comes in; metamodels are searched by six methods: direct metamodel, generation model, plugin registry, EMF bytecode and two inferences from code."],
        ["Proyectos Eclipse/EMF, Java, Kotlin, TypeScript, Go y similares.", "Eclipse/EMF, Java, Kotlin, TypeScript, Go and similar projects."],
        ["Candidatos de metamodelo (exacta, recuperada, derivada o inferida) con evidencia y reglas de validación traducidas.", "Metamodel candidates (exact, recovered, derived or inferred) with evidence and translated validation rules."],
        ["Ninguno: no se ejecuta ni compila el proyecto externo.", "None: the external project is not run or compiled."],
        ["Asistente de recuperación: elegir origen local.", "Recovery wizard: choose a local source."],
        ST("s", "implementado; los métodos por inferencia son menos fiables y requieren revisión.", "implemented; inference methods are less reliable and need review."),
        ["Plan de recuperación y métodos de descubrimiento (elementos source, plan y method de recuperación).", "Recovery plan and discovery methods (the recovery source, plan and method elements)."]] },
    { n: ["Recuperar un proyecto desde un repositorio Git, GitHub o GitLab", "Recover a project from a Git, GitHub or GitLab repository"],
      d: ["Lee el contenido de un repositorio externo y lo trata como origen de recuperación.", "Reads the content of an external repository and treats it as a recovery source."],
      k: "source: git",
      f: [op("imp"),
        ["Entran los archivos del repositorio (solo lectura para la recuperación).", "The repository's files come in (read-only for recovery)."],
        ["GitHub, GitLab y Git genérico.", "GitHub, GitLab and generic Git."],
        ["Los mismos candidatos de metamodelo de la recuperación local.", "The same metamodel candidates as local recovery."],
        ["Acceso de red al proveedor; la conexión se prepara en repositorios externos.", "Network access to the provider; the connection is set up in external repositories."],
        ["Asistente de recuperación: elegir origen Git.", "Recovery wizard: choose a Git source."],
        ST("s", "implementado; la autenticación y las operaciones Git completas se describen en repositorios externos.", "implemented; authentication and the full Git operations are described under external repositories."),
        ["Origen «Repositorio» del plan de recuperación; lectura mediante la capa de repositorios remotos (RemoteGitController, árbol del repositorio).", "“Repository” source of the recovery plan; read through the remote repositories layer (RemoteGitController, repository tree)."]] },
    { n: ["Recuperar desde un sitio de actualización remoto (p2)", "Recover from a remote update site (p2)"],
      d: ["Descarga complementos de un sitio de actualización Eclipse para recuperar sus metamodelos.", "Downloads plug-ins from an Eclipse update site to recover their metamodels."],
      k: "source: p2",
      f: [op("imp"),
        ["Entran los complementos del sitio (con la opción de exigir un espejo sin conexión).", "The site's plug-ins come in (with the option to require an offline mirror)."],
        ["Sitios p2 de Eclipse.", "Eclipse p2 sites."],
        ["Candidatos de metamodelo, normalmente por registro de plugins o bytecode EMF.", "Metamodel candidates, usually via the plugin registry or EMF bytecode."],
        ["Acceso al sitio de actualización; no se necesita Eclipse instalado, pero no verificado para todos los sitios.", "Access to the update site; Eclipse does not need to be installed, but unverified for all sites."],
        ["Asistente de recuperación: elegir origen p2.", "Recovery wizard: choose a p2 source."],
        ST("p", "implementado con un espejo p2 y una puerta de publicación que puede exigirlo.", "implemented with a p2 mirror and a publication gate that can require it."),
        ["Origen p2 del plan de recuperación y puerta «Espejo p2 sin conexión».", "p2 source of the recovery plan and the “offline p2 mirror” gate."]] },
    { n: ["Materializar lo recuperado en el proyecto", "Materialize the recovered material into the project"],
      d: ["Previsualiza, guarda las capas elegidas en HYDRA y puede deshacerlo si el resultado lo permite.", "Previews, saves the chosen layers into HYDRA and can undo if the result allows it."],
      k: "materialize",
      f: [op("imp"),
        ["Entran a HYDRA las capas seleccionadas, nunca sin una vista previa aplicable.", "The selected layers come into HYDRA, never without an applicable preview."],
        ["No aplica: destino interno de HYDRA.", "Not applicable: HYDRA's internal target."],
        ["Metamodelo, gramática y reglas de validación guardados en el proyecto; las reglas traducidas pueden quedar activas, para revisar o desactivadas.", "Metamodel, grammar and validation rules saved in the project; translated rules can end up active, to review or disabled."],
        NONE_RT,
        ["Acciones «Previsualizar», «Importar y materializar» y «Deshacer».", "“Preview”, “Import and materialize” and “Undo” actions."],
        ST("s", "implementado con vista previa y reversión.", "implemented with preview and rollback."),
        ["Materialización en tres modos (previsualizar, importar y materializar, deshacer) sobre el plan de recuperación.", "Materialization in three modes (preview, import and materialize, undo) on the recovery plan."]] },
    { n: ["Copia de seguridad, verificación y simulacro de HYDRA", "HYDRA backup, verification and drill"],
      d: ["Crea una copia de los datos de HYDRA, comprueba que es válida y ensaya una restauración.", "Creates a copy of HYDRA's data, checks it is valid and rehearses a restore."],
      k: "backup / verify / drill",
      f: [op("exp", "syn"),
        ["Sale una copia con su manifiesto; entra, al ensayar, una restauración de prueba.", "A copy with its manifest goes out; a test restore comes in when rehearsing."],
        ["Almacenamiento del servidor; el destino exacto no se verificó.", "Server storage; the exact destination was not verified."],
        ["Copia con manifiesto de verificación; el formato interno no se revisó.", "Copy with a verification manifest; the internal format was not reviewed."],
        NONE_RT,
        ["Peticiones de copia, verificación y simulacro; interfaz de usuario no verificada.", "Backup, verify and drill requests; user interface not verified."],
        ST("u", "existe en el servidor; no se verificó interfaz ni destino de la copia.", "it exists on the server; neither the interface nor the backup destination was verified."),
        ["HydraRecoveryController (/api/recovery/backups, /backups/{id}/verify, /drills) y HydraContinuityController.", "HydraRecoveryController (/api/recovery/backups, /backups/{id}/verify, /drills) and HydraContinuityController."]] }
  ];

  // ================================================================================================
  // EXTERNAL REPOSITORIES
  // ================================================================================================
  var EXR = [
    { n: ["Conectar con un proveedor externo", "Connect to an external provider"],
      d: ["Crea una conexión autenticada con GitHub, GitLab, Git genérico, One Drive, Google Drive o Dropbox.", "Creates an authenticated connection to GitHub, GitLab, generic Git, One Drive, Google Drive or Dropbox."],
      k: "auth / profile",
      f: [op("int"),
        ["Entra la autorización de la persona; no se guarda un token en claro.", "The person's authorization comes in; no token is stored in clear."],
        ["GitHub, GitLab, Git genérico (Git completo) y One Drive, Google Drive, Dropbox (solo archivos).", "GitHub, GitLab, generic Git (full Git) and One Drive, Google Drive, Dropbox (files only)."],
        ["Una conexión autenticada por perfil.", "An authenticated connection per profile."],
        ["Navegador con ventana al proveedor (OAuth con PKCE), o un dispositivo con código si el administrador lo habilita, o usuario y contraseña HTTPS en Git genérico.", "A browser with a pop-up to the provider (OAuth with PKCE), or a device code if the administrator enables it, or HTTPS user and password for generic Git."],
        ["Configurar el perfil e iniciar sesión.", "Configure the profile and sign in."],
        ST("s", "tres flujos de identidad implementados; la comprobación de la conexión es un paso aparte.", "three identity flows implemented; testing the connection is a separate step."),
        ["RemoteAuthController, perfiles en RemoteStorageController (/api/remote-storage/profiles y /test).", "RemoteAuthController, profiles in RemoteStorageController (/api/remote-storage/profiles and /test)."]] },
    { n: ["Sincronizar con Git: estado, traer, enviar, fusionar y ramas", "Synchronize with Git: status, fetch, push, merge and branches"],
      d: ["Diez operaciones Git: estado, inicializar, fetch, pull, push, merge, abortar fusión, commit de todo, crear rama y checkout.", "Ten Git operations: status, init, fetch, pull, push, merge, abort merge, commit all, create branch and checkout."],
      k: "gitops",
      f: [op("syn"),
        ["Salen commits al repositorio remoto; entran cambios traídos de él.", "Commits go out to the remote repository; changes fetched from it come in."],
        ["GitHub, GitLab y Git genérico.", "GitHub, GitLab and generic Git."],
        ["Un repositorio Git con el proyecto de HYDRA.", "A Git repository holding the HYDRA project."],
        ["Servidor Git accesible por red; no se necesita el programa Git en el equipo de la persona (la operación corre en el servidor de HYDRA; cómo lo hace internamente no se verificó).", "A network-reachable Git server; the Git program is not needed on the person's machine (the operation runs on the HYDRA server; how it does it internally was not verified)."],
        ["Elegir la operación Git en la pantalla de repositorios externos.", "Choose the Git operation on the external repositories screen."],
        ST("s", "diez operaciones implementadas; fusiones con conflicto pasan al resolutor visual.", "ten operations implemented; conflicting merges go to the visual resolver."),
        ["RemoteGitController POST /api/remote-git/{operation}.", "RemoteGitController POST /api/remote-git/{operation}."]] },
    { n: ["Resolver conflictos de fusión", "Resolve merge conflicts"],
      d: ["Resuelve en cuatro paneles los ficheros en conflicto y confirma la resolución.", "Resolves conflicting files in four panes and confirms the resolution."],
      k: "resolver",
      f: [op("syn"),
        ["Entra la decisión de la persona; sale la versión resuelta hacia el repositorio.", "The person's decision comes in; the resolved version goes out to the repository."],
        ["GitHub, GitLab y Git genérico.", "GitHub, GitLab and generic Git."],
        ["Ficheros fusionados en el repositorio.", "Merged files in the repository."],
        NONE_RT,
        ["Se abre al encontrar conflicto o versión distinta tras un pull o merge.", "Opens when a conflict or different version is found after a pull or merge."],
        ST("s", "implementado.", "implemented."),
        ["RemoteGitController GET /conflicts/{profileId} y POST /conflicts/resolve.", "RemoteGitController GET /conflicts/{profileId} and POST /conflicts/resolve."]] },
    { n: ["Exportar o importar un proyecto completo como ZIP", "Export or import a complete project as a ZIP"],
      d: ["Transfiere el proyecto activo como un ZIP, solo por rutas exactas y sin explorador remoto.", "Transfers the active project as a ZIP, only by exact paths and without a remote browser."],
      k: "transfer: project ZIP",
      f: [op("exp", "imp"),
        ["Sale el proyecto activo o entra un proyecto previamente exportado.", "The active project goes out or a previously exported project comes in."],
        ["One Drive, Google Drive y Dropbox (y los proveedores Git para ficheros).", "One Drive, Google Drive and Dropbox (and Git providers for files)."],
        ["Archivo ZIP del proyecto en la ruta indicada.", "ZIP file of the project at the given path."],
        ["Cuenta en el proveedor y acceso de red.", "An account at the provider and network access."],
        ["Elegir «Proyecto completo como ZIP» en la transferencia de archivos.", "Choose “Complete project as ZIP” in file transfer."],
        ST("s", "implementado; no hay explorador remoto, se escribe la ruta exacta.", "implemented; there is no remote browser, the exact path is typed."),
        ["RemoteStorageController POST /api/remote-storage/projects/export y /projects/import.", "RemoteStorageController POST /api/remote-storage/projects/export and /projects/import."]] },
    { n: ["Exportar o importar una capa individual", "Export or import a single layer"],
      d: ["Transfiere un único fichero de capa con la versión esperada para no pisar cambios ajenos.", "Transfers one layer file with the expected version so as not to overwrite someone else's changes."],
      k: "transfer: layer",
      f: [op("exp", "imp", "syn"),
        ["Sale o entra una capa suelta (metamodelo, gramática, estilo, representación visual, paleta…).", "A single layer (metamodel, grammar, style, visual representation, palette…) goes out or comes in."],
        ["One Drive, Google Drive y Dropbox.", "One Drive, Google Drive and Dropbox."],
        ["Un único fichero de capa.", "A single layer file."],
        ["Cuenta en el proveedor y acceso de red.", "An account at the provider and network access."],
        ["Elegir «Una capa individual» en la transferencia de archivos.", "Choose “A single layer” in file transfer."],
        ST("s", "implementado con control de versión esperada.", "implemented with expected-version control."),
        ["RemoteStorageController POST /layers/export (multiparte) y GET /layers/import.", "RemoteStorageController POST /layers/export (multipart) and GET /layers/import."]] }
  ];

  // ================================================================================================
  // element + lens assembly
  // ================================================================================================
  var NAME = T("Intercambio con el exterior", "Exchange with the outside");
  function elem(id, specs, mean, purpose, when, card, effectT, cond, evid) {
    var b = build(id, specs);
    return {
      el: {
        id: id, n: NAME, kind: "variant-set", req: "OPTIONAL",
        mean: t(mean), purpose: t(purpose), when: t(when), card: card,
        variants: b.variants,
        props: [
          { n: T("Tipo de operación", "Operation type"), req: "MANDATORY", d: T("Importación, exportación, generación, ejecución externa, integración, sincronización, empaquetado o despliegue", "Import, export, generation, external execution, integration, synchronization, packaging or deployment") },
          { n: T("Plataforma y entorno externo", "Platform and external environment"), req: "MANDATORY", d: T("Con qué herramienta o plataforma se intercambia y qué hay que tener instalado fuera", "Which tool or platform it exchanges with and what must be installed outside") },
          { n: T("Estado de soporte", "Support status"), req: "MANDATORY", d: T("Soportado, parcial, externo o no verificado", "Supported, partial, external or unverified") }
        ],
        io: { i: T("Un modelo, capa o proyecto de HYDRA, o un archivo externo", "A HYDRA model, layer or project, or an external file"), o: T("Un archivo, proyecto o conexión fuera de HYDRA, o contenido nuevo dentro", "A file, project or connection outside HYDRA, or new content inside") },
        effect: t(effectT),
        func: { rel: [T("Complementa a las demás capacidades de intercambio: cada una cubre verbos distintos.", "Complements the other exchange capabilities: each covers different verbs.")], lifecycle: T("elegir operación → comprobar requisitos → ejecutar → resultado fuera o dentro de HYDRA", "choose operation → check requirements → run → result outside or inside HYDRA"), cond: t(cond) },
        tech: { type: "catálogo de operaciones de intercambio", schema: "variantes = operaciones reales; facetas: tipo, qué sale/entra, plataforma, formato, runtime externo, disparo, soporte, implementación", evidence: evid }
      },
      facets: b.facets, lens: b.lens
    };
  }

  var E = {};
  E.integration = elem("xch_integration", INT,
    ["Todo lo que puede salir de HYDRA, entrar, generarse, ejecutarse fuera o sincronizarse desde el diseño de integración, metamodelos, transformaciones y gramáticas: cada operación con su plataforma, formato y herramientas externas.", "Everything that can leave HYDRA, enter, be generated, run externally or be synchronized from the integration, metamodel, transformation and grammar designers: each operation with its platform, format and external tools."],
    ["Responder de un vistazo qué se puede sacar, traer, generar o ejecutar fuera, y qué hay que instalar aparte.", "Answer at a glance what can be taken out, brought in, generated or run outside, and what must be installed separately."],
    ["Cuando se quiere llevar un lenguaje a otra tecnología, traer uno existente o saber qué herramientas externas hacen falta.", "When you want to take a language to another technology, bring in an existing one, or know which external tools are needed."],
    "1 operación ↔ 1 plataforma (o varias, según la variante)",
    ["Cada operación indica con claridad qué se genera o intercambia y qué herramienta externa se necesita; ninguna ejecuta software externo por sí sola salvo las sondas de compatibilidad.", "Each operation states clearly what is generated or exchanged and which external tool is needed; none runs external software on its own except the compatibility probes."],
    ["Las operaciones de metamodelos, transformaciones y gramáticas se recogen aquí solo por sus hechos de importación, exportación y generación; su detalle está en sus capacidades.", "Metamodel, transformation and grammar operations are gathered here only for their import, export and generation facts; their detail is in their own capabilities."],
    [{ path: EV + "integration/IntegrationDeliveryMode.java", line: 3, label: "modos de entrega" }, { path: EV + "metamodel/export/project/MetamodelProjectExportKind.java", line: 9, label: "proyectos completos" }, { path: EV + "metamodel/export/MetamodelExportFormat.java", line: 11, label: "formatos de exportación" }, { path: EV + "transformation/export/TransformationExportLanguage.java", line: 4, label: "lenguajes de exportación" }]);

  E.portability = elem("xch_portability", POR,
    ["Cómo se mueven capas de capacidad entre instalaciones de HYDRA: empaquetar, importar con vista previa y fusión, y revertir.", "How capability layers move between HYDRA installations: pack, import with preview and merge, and revert."],
    ["Compartir un lenguaje (metamodelo, gramática, editor y paleta) entre equipos sin perder control.", "Share a language (metamodel, grammar, editor and palette) between teams without losing control."],
    ["Al llevar un lenguaje a otra instalación o recibir uno de otra.", "When taking a language to another installation or receiving one from another."],
    "1 paquete ↔ 1..6 capas",
    ["Los paquetes salen firmados; al entrar se previsualizan, se aplican con fusión y se pueden revertir. No necesita herramientas externas.", "Packages go out signed; on entry they are previewed, applied with merge and can be reverted. It needs no external tools."],
    ["La interfaz de usuario de cada operación no se verificó; el servidor sí tiene todas las operaciones.", "The user interface of each operation was not verified; the server does have all operations."],
    [{ path: EV + "portability/archive/CapabilityPackageArchiveController.java", line: 20, label: "archivos de paquete" }]);

  E.publication = elem("xch_publication", PUB,
    ["Cómo se empaqueta y distribuye HYDRA: web instalable, escritorio, paquetes ZIP y publicación de versiones.", "How HYDRA is packaged and distributed: installable web, desktop, ZIP packages and release publication."],
    ["Dejar claro qué paquete producir, para qué plataforma y qué parte del despliegue queda en manos de la persona.", "Make clear which package to produce, for which platform and what part of deployment remains in the person's hands."],
    ["Al preparar una versión o distribuir el editor.", "When preparing a release or distributing the editor."],
    "1 destino ↔ 1..N paquetes",
    ["Genera paquetes y ficheros de distribución; el despliegue en un servidor y la compilación del instalador de escritorio los hace la persona.", "Generates distribution packages and files; deploying to a server and building the desktop installer are done by the person."],
    ["El destino real de la publicación de versiones no se verificó.", "The real destination of release publication was not verified."],
    [{ path: EV + "standalone/distribution/StandaloneDistributionController.java", line: 15, label: "distribución autónoma" }, { path: EV + "standalone/publication/DistributionPublicationController.java", line: 10, label: "publicación" }]);

  E.recovery = elem("xch_recovery", REC,
    ["Cómo entra a HYDRA un proyecto externo (local, Git o sitio p2) para recuperar sus metamodelos, y cómo se hace copia de seguridad de HYDRA.", "How an external project (local, Git or p2 site) enters HYDRA to recover its metamodels, and how HYDRA is backed up."],
    ["Aprovechar lenguajes ya existentes sin rehacerlos y proteger los datos de HYDRA.", "Reuse existing languages without rebuilding them and protect HYDRA's data."],
    ["Cuando ya existe un proyecto Eclipse o de código con el lenguaje, o antes de operaciones delicadas.", "When an Eclipse or code project with the language already exists, or before delicate operations."],
    "1 origen ↔ 0..N candidatos de metamodelo",
    ["Los proyectos externos no se ejecutan ni compilan; se leen y se reconstruyen candidatos, y solo se guarda lo que la persona materializa.", "External projects are neither run nor compiled; they are read and candidates rebuilt, and only what the person materializes is saved."],
    ["El formato y el destino de la copia de seguridad no se verificaron.", "The format and destination of the backup were not verified."],
    [{ path: EV + "recovery/HydraRecoveryController.java", line: 13, label: "copias de seguridad" }]);

  E.external_repositories = elem("xch_external_repositories", EXR,
    ["Cómo se intercambia el proyecto con repositorios Git y almacenamiento en la nube: conexión, operaciones Git, conflictos, ZIP y capas sueltas.", "How the project is exchanged with Git repositories and cloud storage: connection, Git operations, conflicts, ZIP and single layers."],
    ["Colaborar y guardar copias fuera de HYDRA con un proveedor externo.", "Collaborate and keep copies outside HYDRA with an external provider."],
    ["Cuando se quiere versionar el proyecto o compartirlo mediante GitHub, GitLab, Git genérico, One Drive, Google Drive o Dropbox.", "When you want to version the project or share it through GitHub, GitLab, generic Git, One Drive, Google Drive or Dropbox."],
    "1 perfil ↔ 1 proveedor",
    ["Git completo en tres proveedores; los otros tres solo transfieren archivos. Requiere cuenta y acceso de red.", "Full Git on three providers; the other three only transfer files. Requires an account and network access."],
    ["El comportamiento interno de las operaciones Git en el servidor no se verificó.", "The internal behaviour of the Git operations on the server was not verified."],
    [{ path: EV + "remote/git/RemoteGitController.java", line: 15, label: "Git remoto" }, { path: EV + "remote/storage/web/RemoteStorageController.java", line: 28, label: "almacenamiento remoto" }]);

  // overview element (integration)
  var ovv = {
    id: "xch_overview", n: T("Resumen del intercambio: quién hace cada verbo", "Exchange summary: which capability handles each verb"), kind: "variant-set", req: "OPTIONAL",
    mean: T("Vista única que dice, por verbo (importar, exportar, generar, ejecutar fuera, integrar, sincronizar, empaquetar, desplegar), qué capacidad se encarga.", "A single view telling, per verb (import, export, generate, run externally, integrate, synchronize, package, deploy), which capability handles it."),
    purpose: T("Encontrar la capacidad correcta sin recorrer todo el catálogo.", "Find the right capability without walking the whole catalog."),
    when: T("Cuando la pregunta es «¿qué puede salir, entrar, generarse o desplegarse?».", "When the question is “what can leave, enter, be generated or deployed?”."),
    card: "1 verbo ↔ 1..N capacidades",
    variants: OVERVIEW.map(function (s) { return { n: t(s.n), d: t(s.d), req: "ALTERNATIVE" }; }),
    props: [{ n: T("Verbo", "Verb"), req: "MANDATORY", d: T("Importar, exportar, generar, ejecutar fuera, integrar, sincronizar, empaquetar, desplegar", "Import, export, generate, run externally, integrate, synchronize, package, deploy") }],
    io: { i: T("Una pregunta sobre intercambio", "A question about exchange"), o: T("La capacidad responsable", "The responsible capability") },
    effect: T("Solo orienta; no ejecuta nada.", "It only guides; it executes nothing."),
    func: { rel: [T("Cada verbo remite al elemento «Intercambio con el exterior» de su capacidad.", "Each verb points to the “Exchange with the outside” element of its capability.")], lifecycle: T("consultar", "consult"), cond: T("El reparto de verbos refleja el contenido revisado; operaciones de otras capacidades no revisadas pueden faltar.", "The split of verbs reflects the reviewed content; operations of other unreviewed capabilities may be missing.") },
    tech: { type: "vista de resumen", schema: "agrupa xch_integration, xch_portability, xch_publication, xch_recovery y xch_external_repositories", evidence: [{ path: EV + "integration/IntegrationDeliveryMode.java", line: 3, label: "modos de entrega" }] }
  };

  var CAPS = [["integration", "xch_integration"], ["portability", "xch_portability"], ["publication", "xch_publication"], ["recovery", "xch_recovery"], ["external_repositories", "xch_external_repositories"]];
  var FIRST = { integration: "profile", portability: "layer", publication: "codegen", recovery: "source", external_repositories: "provider" };

  var facetCaps = {}, lensCaps = {};
  CAPS.forEach(function (c) {
    var cap = c[0], eid = c[1], r = E[cap], extra = [r.el];
    if (cap === "integration") extra.push(ovv);
    HM.extend(cap, {
      elements: extra,
      mapNodes: [{ id: eid + "_node", elem: eid, n: NAME, k: "variant", req: "OPTIONAL", d: T(r.el.variants.length + " operaciones de intercambio", r.el.variants.length + " exchange operations") }].concat(cap === "integration" ? [{ id: "xch_overview_node", elem: "xch_overview", n: T("Resumen del intercambio", "Exchange summary"), k: "variant", req: "OPTIONAL", d: T("8 verbos", "8 verbs") }] : []),
      mapEdges: [{ a: FIRST[cap], b: eid + "_node", rel: "ref", card1: "1", card2: "0..N", req: "OPTIONAL" }].concat(cap === "integration" ? [{ a: "xch_integration_node", b: "xch_overview_node", rel: "ref", card1: "1", card2: "1", req: "OPTIONAL" }] : [])
    });
    facetCaps[cap] = { elements: {} }; facetCaps[cap].elements[eid] = { variants: r.facets };
    lensCaps[cap] = { elements: {} };
    lensCaps[cap].elements[eid] = {
      nf: NAME,
      fm: T("Catálogo de operaciones de intercambio con el exterior: tipo de operación, qué sale o entra, plataforma, formato, runtime externo, disparo y estado de soporte.", "Catalog of exchange operations with the outside: operation type, what leaves or enters, platform, format, external runtime, trigger and support status."),
      tm: T("Variantes = operaciones reales con su implementación (clases y endpoints) y evidencia.", "Variants = real operations with their implementation (classes and endpoints) and evidence."),
      variants: r.lens
    };
    if (cap === "integration") {
      lensCaps[cap].elements.xch_overview = {
        nf: T("Resumen del intercambio por verbo", "Exchange summary per verb"),
        fm: T("Para cada verbo de intercambio indica qué capacidad lo cubre.", "For each exchange verb it tells which capability covers it."),
        tm: T("Agrupa los elementos xch_* de integración, portabilidad, publicación, recuperación y repositorios externos.", "Groups the xch_* elements of integration, portability, publication, recovery and external repositories."),
        variants: OVERVIEW.map(function (s) { return { k: s.k, fd: T("Verbo «" + s.n[0] + "»: " + s.d[0], "Verb “" + s.n[1] + "”: " + s.d[1]), td: T("Elementos xch_* para «" + s.n[0] + "»: " + s.d[0], "xch_* elements for “" + s.n[1] + "”: " + s.d[1]) }; })
      };
    }
  });
  HM.facets({ caps: facetCaps });
  HM.lens({ caps: lensCaps });
})();
