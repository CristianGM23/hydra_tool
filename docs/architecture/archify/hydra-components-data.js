/* HYDRA portal - legacy Archify-panel component data (moved verbatim from hydra-architecture.html; used by the "Archify diagram" overview mode). */
window.HYDRA_COMPONENTS = (function(){
  var COMPONENTS = {
    metamodel: {
      kind: "backend", inUser: true,
      es: {
        user: ["Metamodelos", "diseño gráfico del lenguaje"],
        functional: ["Diseñador de Metamodelos", "canónico + EMF/Ecore + diagrama"],
        technical: ["Diseñador de Metamodelos", "canónico + EMF/Ecore + diagrama"]
      },
      en: {
        user: ["Metamodels", "graphical language design"],
        functional: ["Metamodel Designer", "canonical + EMF/Ecore + diagram"],
        technical: ["Metamodel Designer", "canonical + EMF/Ecore + diagram"]
      },
      detail: {
        es: [
          "Diseña el 'lenguaje' en sí: qué tipos de elementos existen, qué propiedades tienen y cómo se relacionan entre ellos.",
          "Ofrece un lienzo visual de clases, enumerados y tipos de datos, con diagramación automática.",
          "Tecnología: EMF/Ecore (el estándar de modelado de Eclipse) como núcleo canónico."
        ],
        en: [
          "Designs the 'language' itself: which element types exist, what properties they have, and how they relate to each other.",
          "Offers a visual canvas for classes, enums and data types, with automatic layout.",
          "Technology: EMF/Ecore (Eclipse's modeling standard) as the canonical core."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: clases, atributos, referencias y composiciones, herencia, enumerados, tipos de datos primitivos y multiplicidad.",
          "No se modela aquí: comportamiento ejecutable (eso son Validaciones y Eventos y Gamificación) ni sintaxis textual (eso es Diseñador de Gramáticas)."
        ],
        en: [
          "Modeled here: classes, attributes, references and compositions, inheritance, enums, primitive data types, and multiplicity.",
          "Not modeled here: executable behavior (that's Validation and Events & Gamification) or textual syntax (that's the Grammar Designer)."
        ]
      },
      relations: [],
      sources: [
        { path: "src/main/java/com/example/editor/metamodel/web/CanonicalMetamodelController.java", line: 35 },
        { path: "src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java", line: 32 }
      ]
    },
    model_editor: {
      kind: "backend", inUser: true,
      es: {
        user: ["Modelos", "crear y editar instancias"],
        functional: ["Editor de Modelos", "+ definición de editor, EMF dinámico"],
        technical: ["Editor de Modelos", "+ definición de editor, EMF dinámico"]
      },
      en: {
        user: ["Models", "create and edit instances"],
        functional: ["Model Editor", "+ editor definition, dynamic EMF"],
        technical: ["Model Editor", "+ editor definition, dynamic EMF"]
      },
      detail: {
        es: [
          "Crea y edita instancias de tu lenguaje: los documentos reales que la gente rellena con datos.",
          "Ofrece un lienzo visual con paleta de elementos y un inspector de propiedades para rellenar cada dato.",
          "Tecnología: EMF dinámico — construye esos objetos sin necesidad de generar código Java."
        ],
        en: [
          "Creates and edits instances of your language: the actual documents people fill in with data.",
          "Offers a visual canvas with an element palette and a property inspector to fill in each value.",
          "Technology: dynamic EMF — builds those objects without ever generating Java code."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: instancias concretas del metamodelo — objetos, sus valores de atributo y sus enlaces de referencia.",
          "No se modela aquí: nuevos tipos (eso es Metamodelos) ni las reglas que comprueban si la instancia es válida (eso es Validaciones)."
        ],
        en: [
          "Modeled here: concrete instances of the metamodel — objects, their attribute values, and their reference links.",
          "Not modeled here: new types (that's Metamodels) or the rules that check whether the instance is valid (that's Validation)."
        ]
      },
      relations: [
        { to: "metamodel", es: "Usa el metamodelo como su lenguaje: un modelo solo puede contener los tipos que el metamodelo define.", en: "Uses the metamodel as its language: a model can only contain the types the metamodel defines." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/model/ModelDocumentController.java", line: 23 },
        { path: "src/main/java/com/example/editor/emf/DynamicModelService.java", line: 19 }
      ]
    },
    validation: {
      kind: "backend", inUser: true,
      es: {
        user: ["Validaciones", "reglas + reparaciones automáticas"],
        functional: ["Validación y Quick Fixes", "motor declarativo"],
        technical: ["Validación y Quick Fixes", "motor declarativo"]
      },
      en: {
        user: ["Validations", "rules + automatic quick fixes"],
        functional: ["Validation & Quick Fixes", "declarative engine"],
        technical: ["Validation & Quick Fixes", "declarative engine"]
      },
      detail: {
        es: [
          "Define reglas que detectan errores en un modelo: campos obligatorios, referencias incorrectas, valores fuera de rango…",
          "Ofrece también quick fixes: reparaciones automáticas que te propone y que tú decides aplicar o no.",
          "Tecnología: un motor de expresiones propio de HYDRA; las reglas no requieren programar en Java."
        ],
        en: [
          "Defines rules that catch errors in a model: required fields, broken references, out-of-range values…",
          "Also offers quick fixes: automatic repairs it proposes, which you decide whether to apply.",
          "Technology: HYDRA's own expression engine; rules never require writing Java."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: reglas de restricción sobre tipos y propiedades ya existentes (obligatoriedad, rango, unicidad, referencias cruzadas) y quick fixes ligados a esas reglas.",
          "No se modela aquí: nuevos tipos de metamodelo; una regla solo observa un modelo y, como mucho, propone una reparación que tú decides aplicar."
        ],
        en: [
          "Modeled here: constraint rules over already-existing types and properties (required fields, range, uniqueness, cross-references) and quick fixes tied to those rules.",
          "Not modeled here: new metamodel types; a rule only observes a model and, at most, proposes a repair that you decide whether to apply."
        ]
      },
      relations: [
        { to: "metamodel", es: "Las reglas se escriben sobre los tipos y propiedades que define el metamodelo.", en: "Rules are written against the types and properties the metamodel defines." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/validation/runtime/ValidationEngine.java", line: 38 },
        { path: "src/main/java/com/example/editor/validation/quickfix/QuickFixRuntimeService.java", line: 43 }
      ]
    },
    grammar: {
      kind: "backend", inUser: true,
      es: {
        user: ["Lenguajes Textuales", "sintaxis y editor de texto"],
        functional: ["Diseñador de Gramáticas", "DSL textual · Xtext/Langium/ANTLR"],
        technical: ["Diseñador de Gramáticas", "DSL textual · Xtext/Langium/ANTLR"]
      },
      en: {
        user: ["Textual Languages", "syntax and text editor"],
        functional: ["Grammar Designer", "textual DSL · Xtext/Langium/ANTLR"],
        technical: ["Grammar Designer", "textual DSL · Xtext/Langium/ANTLR"]
      },
      detail: {
        es: [
          "Diseña la sintaxis textual de tu lenguaje: cómo se escribe en texto plano en vez de con cajas y flechas.",
          "Ofrece resaltado de sintaxis, autocompletado y detección de errores mientras escribes.",
          "Tecnología: eliges entre Xtext, Langium o ANTLR para la gramática; el editor de texto usa Monaco."
        ],
        en: [
          "Designs your language's textual syntax: how it's written as plain text instead of boxes and arrows.",
          "Offers syntax highlighting, autocompletion and error detection as you type.",
          "Technology: choose Xtext, Langium or ANTLR for the grammar; the text editor uses Monaco."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: la sintaxis concreta textual de tu lenguaje — tokens, reglas gramaticales, resaltado y autocompletado.",
          "No se modela aquí: el significado de esos tipos (sigue siendo el metamodelo); el texto siempre se interpreta como instancias del mismo metamodelo, nunca como un modelo paralelo independiente."
        ],
        en: [
          "Modeled here: your language's concrete textual syntax — tokens, grammar rules, highlighting and autocompletion.",
          "Not modeled here: the meaning of those types (that remains the metamodel); text is always interpreted as instances of the same metamodel, never as an independent parallel model."
        ]
      },
      relations: [
        { to: "metamodel", es: "El texto escrito con esta gramática se interpreta como instancias del mismo metamodelo.", en: "Text written with this grammar is interpreted as instances of the same metamodel." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/grammar/application/GrammarCompilationService.java", line: 21 },
        { path: "src/main/java/com/example/editor/textual/runtime/TextualModelSessionService.java" }
      ]
    },
    events: {
      kind: "messagebus", inUser: true,
      es: {
        user: ["Eventos", "reaccionar a cambios"],
        functional: ["Eventos", "incluye EventFlow, runtime por eventos"],
        technical: ["Eventos", "incluye EventFlow, runtime por eventos"]
      },
      en: {
        user: ["Events", "react to changes"],
        functional: ["Events", "includes EventFlow, event-driven runtime"],
        technical: ["Events", "includes EventFlow, event-driven runtime"]
      },
      detail: {
        es: [
          "Define eventos del dominio (qué puede pasar) y los encadena en flujos de eventos (EventFlow).",
          "Ofrece suscripción amplia al bus para que otras capacidades (sobre todo Gamificación) reaccionen a eventos ya comprobados.",
          "Tecnología: un runtime propio de HYDRA, headless y basado únicamente en eventos verificados; es opt-in por sesión de editor (publishEvents())."
        ],
        en: [
          "Defines domain events (what can happen) and chains them into event flows (EventFlow).",
          "Offers broad bus subscription so other capabilities (chiefly Gamification) can react to already-verified events.",
          "Technology: HYDRA's own headless runtime, driven only by verified events; it is opt-in per editor session (publishEvents())."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: eventos del dominio y sus payloads, y flujos de eventos (EventFlow) encadenados.",
          "No se modela aquí: la integridad del modelo (eso es Validaciones) ni las reglas de puntos/logros (eso es Gamificación)."
        ],
        en: [
          "Modeled here: domain events and their payloads, and chained event flows (EventFlow).",
          "Not modeled here: model integrity (that's Validation) or point/achievement rules (that's Gamification)."
        ]
      },
      relations: [
        { to: "metamodel", es: "Los eventos reservados se definen sobre los tipos del metamodelo.", en: "Reserved events are defined over the metamodel's types." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java", line: 46 },
        { path: "src/main/java/com/example/editor/definition/runtime/session/EditorSessionEventPublisher.java", line: 21 }
      ]
    },
    gamification: {
      kind: "backend", inUser: true,
      es: {
        user: ["Gamificación", "puntos y logros"],
        functional: ["Gamificación", "reglas de puntos y logros"],
        technical: ["Gamificación", "reglas de puntos y logros"]
      },
      en: {
        user: ["Gamification", "points and achievements"],
        functional: ["Gamification", "point and achievement rules"],
        technical: ["Gamification", "point and achievement rules"]
      },
      detail: {
        es: [
          "Define reglas de puntos, logros y niveles que reaccionan a eventos ya verificados de Eventos.",
          "Ofrece un runtime propio de suscripción+coincidencia, y un proyector que traduce las coincidencias en notificaciones de usuario (toasts + panel de actividad).",
          "Nunca evalúa directamente el modelo: solo reacciona a eventos ya emitidos y comprobados."
        ],
        en: [
          "Defines point, achievement and level rules that react to already-verified events from Events.",
          "Offers its own subscription+matching runtime, and a projector that translates matches into user notifications (toasts + activity panel).",
          "Never evaluates the model directly: it only reacts to events already emitted and checked."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: reglas de puntos/logros/niveles y sus condiciones de disparo sobre eventos.",
          "No se modela aquí: la definición de los eventos en sí (eso es Eventos) ni ninguna regla de validación (eso es Validación)."
        ],
        en: [
          "Modeled here: point/achievement/level rules and their trigger conditions over events.",
          "Not modeled here: the definition of the events themselves (that's Events) or any validation rule (that's Validation)."
        ]
      },
      relations: [
        { to: "events", es: "Sus reglas se disparan por eventos directos y por eventos derivados de EventFlow.", en: "Its rules are triggered by direct events and by EventFlow-derived events." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/gamification/runtime/GamificationRuntimeService.java", line: 51 }
      ]
    },
    transformation: {
      kind: "backend", inUser: true,
      es: {
        user: ["Transformaciones", "modelo ↔ modelo ↔ texto"],
        functional: ["Motor de Transformaciones", "M2M · M2T · T2M"],
        technical: ["Motor de Transformaciones", "M2M · M2T · T2M"]
      },
      en: {
        user: ["Transformations", "model ↔ model ↔ text"],
        functional: ["Transformation Engine", "M2M · M2T · T2M"],
        technical: ["Transformation Engine", "M2M · M2T · T2M"]
      },
      detail: {
        es: [
          "Transforma modelo a modelo (M2M), modelo a texto/código (M2T) y texto a modelo (T2M).",
          "Ofrece un motor de referencia propio de HYDRA, más adaptadores opcionales a motores externos.",
          "Tecnología: motores externos admitidos — ATL, ETL, Acceleo, EGL y Xtend — cuando el administrador los instala."
        ],
        en: [
          "Transforms model to model (M2M), model to text/code (M2T), and text to model (T2M).",
          "Offers HYDRA's own reference engine, plus optional adapters to external engines.",
          "Technology: supported external engines — ATL, ETL, Acceleo, EGL and Xtend — once the administrator installs them."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: reglas de correspondencia entre metamodelos origen/destino (M2M), plantillas de generación de texto/código (M2T), y la configuración de un parser hacia un metamodelo (T2M).",
          "No se modela aquí: los modelos de entrada en sí (eso es el Editor de Modelos) ni la publicación del artefacto resultante (eso es Publicación)."
        ],
        en: [
          "Modeled here: source/target metamodel correspondence rules (M2M), text/code generation templates (M2T), and a parser's configuration toward a metamodel (T2M).",
          "Not modeled here: the input models themselves (that's the Model Editor) or publishing the resulting artifact (that's Publication)."
        ]
      },
      relations: [
        { to: "model_editor", es: "Parte de modelos ya creados con el editor de modelos; nunca inventa datos de entrada.", en: "Starts from models already created with the model editor; it never invents input data." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java", line: 39 },
        { path: "src/main/java/com/example/editor/transformation/export/TransformationLanguageAdapterRegistry.java", line: 10 }
      ]
    },
    publication: {
      kind: "backend", inUser: true,
      es: {
        user: ["Publicación", "generar y distribuir proyectos"],
        functional: ["Publicación y Generación", "release gates + proyectos generados"],
        technical: ["Publicación y Generación", "release gates + proyectos generados"]
      },
      en: {
        user: ["Publication", "generate and ship projects"],
        functional: ["Publication & Generation", "release gates + generated projects"],
        technical: ["Publication & Generation", "release gates + generated projects"]
      },
      detail: {
        es: [
          "Genera el resultado final (código, proyecto, paquete) a partir de tu modelo y lo publica tras pasar comprobaciones de calidad (release gates).",
          "Ofrece varios generadores de código según la tecnología de destino elegida.",
          "Nada se publica si no supera antes sus comprobaciones automáticas."
        ],
        en: [
          "Generates the final result (code, project, package) from your model and publishes it after quality checks (release gates) pass.",
          "Offers several code generators depending on the chosen target technology.",
          "Nothing is published until it passes its automated checks."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: qué generar, hacia qué tecnología de destino, y qué comprobaciones de calidad (release gates) debe superar antes de publicarse.",
          "No se modela aquí: el contenido en sí (modelo/metamodelo); solo su empaquetado, generación y las puertas de calidad previas a publicar."
        ],
        en: [
          "Modeled here: what to generate, which target technology, and which quality checks (release gates) it must pass before publishing.",
          "Not modeled here: the content itself (model/metamodel); only its packaging, generation, and the quality gates before publishing."
        ]
      },
      relations: [
        { to: "model_editor", es: "Genera el resultado a partir del modelo y del metamodelo que lo definen.", en: "Generates the result from the model and the metamodel that define it." },
        { to: "generated_projects", es: "Los artefactos generados y verificados se registran y ejecutan en el ciclo de vida de Proyectos Generados.", en: "Generated, verified artifacts are registered and run by the Generated Projects lifecycle." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/generated/project/GeneratedProjectLifecycleController.java", line: 13 },
        { path: "src/main/java/com/example/editor/release/HydraReleaseGateService.java", line: 36 }
      ]
    },
    integration: {
      kind: "backend", inUser: true,
      es: {
        user: ["Repositorios", "conectar herramientas externas"],
        functional: ["Integraciones", "proyección tecnológica, roundtrip"],
        technical: ["Integraciones", "proyección tecnológica, roundtrip"]
      },
      en: {
        user: ["Repositories", "connect external tools"],
        functional: ["Integrations", "technology projection, roundtrip"],
        technical: ["Integrations", "technology projection, roundtrip"]
      },
      detail: {
        es: [
          "Conecta tu proyecto HYDRA con tecnologías externas: proyecta tu metamodelo/modelo hacia su formato nativo.",
          "Ofrece sincronización de ida y vuelta (roundtrip) para mantener ambos lados alineados.",
          "Se configura con perfiles de integración por tecnología, sin escribir código de integración a mano."
        ],
        en: [
          "Connects your HYDRA project to external technologies: projects your metamodel/model into their native format.",
          "Offers two-way (roundtrip) synchronization to keep both sides aligned.",
          "Configured through per-technology integration profiles, with no hand-written integration code."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: perfiles de integración — a qué tecnología externa se proyecta el metamodelo/modelo, y la política de sincronización roundtrip.",
          "No se modela aquí: código de integración a mano; el contenido proyectado sigue siendo el mismo metamodelo/modelo, no una copia independiente que pueda divergir sin control."
        ],
        en: [
          "Modeled here: integration profiles — which external technology the metamodel/model is projected into, and the roundtrip sync policy.",
          "Not modeled here: hand-written integration code; the projected content remains the same metamodel/model, not an independent copy that can drift uncontrolled."
        ]
      },
      relations: [
        { to: "metamodel", es: "Proyecta el metamodelo y el modelo hacia la representación nativa de la tecnología elegida.", en: "Projects the metamodel and model into the chosen technology's native representation." },
        { to: "toolchains", es: "La ejecución con paridad nativa exige el motor de transformación externo instalado y verificado.", en: "Native-parity execution requires the installed, verified external transformation engine." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/integration/web/TechnologyIntegrationController.java", line: 30 },
        { path: "src/main/java/com/example/editor/integration/compiler/TechnologyIntegrationApplicationService.java", line: 24 }
      ]
    },
    project: {
      kind: "backend", inUser: true,
      es: {
        user: ["Gestión de Proyectos", "Abre, organiza y gestiona el proyecto activo: su árbol de archivos y la importación/exportación de contenido."],
        functional: ["Gestión de Proyectos", "catálogo, árbol, import/export"],
        technical: ["Gestión de Proyectos", "catálogo, árbol, import/export"]
      },
      en: {
        user: ["Project Management", "Opens, organizes and manages the active project: its file tree and content import/export."],
        functional: ["Project Management", "catalog, tree, import/export"],
        technical: ["Project Management", "catalog, tree, import/export"]
      },
      detail: {
        es: [
          "Abre, crea y organiza el proyecto en el que trabajas: su árbol de archivos, sus carpetas y sus recursos.",
          "Ofrece importar y exportar el proyecto completo (o partes de él) y trabajar con varios proyectos a la vez.",
          "Es el contenedor de todo lo demás: metamodelos, modelos, gramáticas, etc. viven dentro de un proyecto."
        ],
        en: [
          "Opens, creates and organizes the project you're working in: its file tree, folders and resources.",
          "Offers importing and exporting the whole project (or parts of it), and working with several projects at once.",
          "The container for everything else: metamodels, models, grammars, etc. all live inside a project."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: la estructura del proyecto — su árbol de archivos, carpetas, metadatos y qué capacidades tiene habilitadas.",
          "No se modela aquí: ningún lenguaje ni dato de dominio; el proyecto es el contenedor, no el contenido."
        ],
        en: [
          "Modeled here: the project's structure — its file tree, folders, metadata, and which capabilities it has enabled.",
          "Not modeled here: any language or domain data; the project is the container, not the content."
        ]
      },
      relations: [],
      sources: [ { path: "src/main/java/com/example/editor/project/web/ProjectController.java", label: "API de proyectos" } ]
    },
    recovery: {
      kind: "backend", inUser: true,
      es: {
        user: ["Recuperación Externa", "Recupera e incorpora proyectos creados fuera de HYDRA, incluidos sitios de actualización de Eclipse."],
        functional: ["Recuperación Externa", "proyectos externos + update sites"],
        technical: ["Recuperación Externa", "proyectos externos + update sites"]
      },
      en: {
        user: ["External Recovery", "Recovers and brings in projects created outside HYDRA, including Eclipse update sites."],
        functional: ["External Recovery", "external projects + update sites"],
        technical: ["External Recovery", "external projects + update sites"]
      },
      detail: {
        es: [
          "Recupera proyectos creados fuera de HYDRA, incluidos sitios de actualización (update sites) de Eclipse.",
          "Ofrece un inventario no destructivo del contenido externo antes de incorporarlo, para que nada se pierda sin avisar.",
          "Preserva lo que no puede reconciliar como PROJECT_RESOURCE en vez de descartarlo."
        ],
        en: [
          "Recovers projects created outside HYDRA, including Eclipse update sites.",
          "Offers a non-destructive inventory of the external content before bringing it in, so nothing is lost silently.",
          "Preserves whatever it cannot reconcile as PROJECT_RESOURCE instead of discarding it."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: el inventario del contenido recuperado de un proyecto externo o un sitio de actualización, y su reconciliación.",
          "No se modela aquí: contenido nuevo; solo se recupera e incorpora contenido ya existente creado fuera de HYDRA."
        ],
        en: [
          "Modeled here: the inventory of content recovered from an external project or an update site, and its reconciliation.",
          "Not modeled here: new content; it only recovers and brings in content already created outside HYDRA."
        ]
      },
      relations: [],
      sources: [
        { path: "src/main/java/com/example/editor/project/recovery/ExternalProjectRecoveryController.java", line: 17 },
        { path: "src/main/java/com/example/editor/metamodel/importing/updatesite/UpdateSiteRecoveryController.java", line: 25 }
      ]
    },
    persistence: {
      kind: "database", inUser: false,
      es: {
        user: ["Persistencia Canónica", "Guarda todo tu trabajo en el disco local, en archivos JSON, sin necesidad de una base de datos."],
        functional: ["Persistencia Canónica", "documentos JSON, ETag optimista"],
        technical: ["Persistencia Canónica", "documentos JSON, ETag optimista"]
      },
      en: {
        user: ["Canonical Persistence", "Saves all your work on the local disk, as JSON files, with no database required."],
        functional: ["Canonical Persistence", "JSON documents, optimistic ETag"],
        technical: ["Canonical Persistence", "JSON documents, optimistic ETag"]
      },
      detail: {
        es: [
          "Guarda automáticamente todo lo que creas — metamodelos, modelos, gramáticas, transformaciones… — cada uno como su propio documento.",
          "Ofrece control de versiones optimista (ETag): si dos cambios chocan, se detecta en vez de perderse en silencio.",
          "Vive enteramente en tu disco local, en archivos JSON; no requiere ni usa una base de datos."
        ],
        en: [
          "Automatically saves everything you create — metamodels, models, grammars, transformations… — each as its own document.",
          "Offers optimistic version control (ETag): a clash between two changes is detected instead of silently lost.",
          "Lives entirely on your local disk, as JSON files; no database is required or used."
        ]
      },
      limits: {
        es: [
          "Nada se modela aquí directamente: es el almacén común donde cada documento canónico (metamodelo, modelo, gramática…) se guarda tal y como lo define su propia capacidad.",
          "No tiene esquema de dominio propio: respeta el que le entrega cada capacidad."
        ],
        en: [
          "Nothing is modeled here directly: it is the shared store where every canonical document (metamodel, model, grammar…) is saved exactly as its own capability defines it.",
          "It has no domain schema of its own: it honors whatever each capability hands it."
        ]
      },
      relations: [],
      sources: [
        { path: "src/main/java/com/example/editor/persistence/CanonicalDocumentStore.java", line: 15 },
        { path: "src/main/java/com/example/editor/persistence/LocalCanonicalDocumentStoreFactory.java", line: 14 }
      ]
    },
    security: {
      kind: "security", inUser: false,
      es: {
        user: ["Seguridad", "Protege HYDRA filtrando peticiones y aislando cualquier programa externo que se ejecute en tu nombre."],
        functional: ["Seguridad", "hardening + sandboxing de procesos"],
        technical: ["Seguridad", "hardening + sandboxing de procesos"]
      },
      en: {
        user: ["Security", "Protects HYDRA by filtering requests and sandboxing any external program run on your behalf."],
        functional: ["Security", "hardening + process sandboxing"],
        technical: ["Security", "hardening + process sandboxing"]
      },
      detail: {
        es: [
          "Filtra y limita la forma y el tamaño de cada petición que llega a HYDRA, antes de que llegue a ninguna capacidad.",
          "Ofrece también el único punto por el que HYDRA ejecuta programas externos: con entorno mínimo, salida acotada y apagado garantizado.",
          "Un motor externo que falte se declara no disponible; nunca se simula su comportamiento."
        ],
        en: [
          "Filters and bounds the shape and size of every request reaching HYDRA, before it reaches any capability.",
          "Also offers the single point through which HYDRA runs external programs: minimal environment, bounded output, guaranteed teardown.",
          "A missing external engine is declared unavailable; its behavior is never simulated."
        ]
      },
      limits: {
        es: [
          "Nada de dominio se modela aquí: define políticas de forma/tamaño de petición y de aislamiento de procesos externos.",
          "No conoce el contenido de metamodelos, modelos ni reglas; opera igual sin importar qué capacidad la esté usando."
        ],
        en: [
          "No domain content is modeled here: it defines request shape/size policies and external-process sandboxing.",
          "It knows nothing about metamodel, model or rule content; it behaves the same regardless of which capability is using it."
        ]
      },
      relations: [],
      sources: [
        { path: "src/main/java/com/example/editor/security/input/HydraRequestHardeningFilter.java", line: 27 },
        { path: "src/main/java/com/example/editor/security/process/HydraExternalProcessRunner.java", line: 22 }
      ]
    },
    audit: {
      kind: "backend", inUser: false,
      es: {
        user: ["Auditoría", "Lleva un registro de lo que ha ocurrido en el sistema, útil para depurar y para demostrar el origen de un resultado."],
        functional: ["Auditoría", "registro JSONL append-only"],
        technical: ["Auditoría", "registro JSONL append-only"]
      },
      en: {
        user: ["Audit", "Keeps a record of what happened in the system, useful for debugging and for proving where a result came from."],
        functional: ["Audit", "append-only JSONL log"],
        technical: ["Audit", "append-only JSONL log"]
      },
      detail: {
        es: [
          "Registra qué ha pasado en el sistema: quién cambió qué y cuándo, en un registro de solo-añadir (append-only).",
          "Ofrece esta evidencia para depurar problemas y para demostrar el origen de un resultado concreto.",
          "Se alimenta automáticamente de cada cambio guardado en la persistencia canónica, sin que tengas que activarlo."
        ],
        en: [
          "Records what happened in the system: who changed what and when, in an append-only log.",
          "Offers that evidence for debugging problems and for proving where a specific result came from.",
          "Fed automatically by every change saved in the canonical persistence, with nothing to turn on."
        ]
      },
      limits: {
        es: [
          "Nada de dominio se modela aquí: registra el hecho de que algo cambió (qué documento, cuándo), no el contenido del cambio.",
          "No es una capacidad de modelado; es un efecto automático de guardar en la persistencia canónica."
        ],
        en: [
          "No domain content is modeled here: it records the fact that something changed (which document, when), not the content of the change.",
          "It is not a modeling capability; it is an automatic side effect of saving to the canonical persistence."
        ]
      },
      relations: [],
      sources: [
        { path: "src/main/java/com/example/editor/audit/JsonlAuditLogStore.java", line: 44 },
        { path: "src/main/java/com/example/editor/provenance/HydraDistributionProvenanceService.java", line: 37 }
      ]
    },
    external_repositories: {
      kind: "external", inUser: true,
      es: {
        user: ["Repositorios Remotos", "Git, nube, control de versiones"],
        functional: ["Proveedores Remotos", "Git · almacenamiento · OAuth"],
        technical: ["Proveedores Remotos", "Git · almacenamiento · OAuth"]
      },
      en: {
        user: ["Remote Repositories", "Git, cloud, version control"],
        functional: ["Remote Providers", "Git · storage · OAuth"],
        technical: ["Remote Providers", "Git · storage · OAuth"]
      },
      detail: {
        es: [
          "Representa tus proveedores externos de control de versiones y almacenamiento: Git, la nube, etc.",
          "Ofrece autenticación mediante OAuth para conectarte sin guardar contraseñas en claro.",
          "HYDRA solo se conecta cuando tú lo configuras explícitamente; nunca por defecto."
        ],
        en: [
          "Represents your external version-control and storage providers: Git, the cloud, etc.",
          "Offers OAuth-based authentication so you connect without storing plaintext passwords.",
          "HYDRA only connects when you explicitly configure it; never by default."
        ]
      },
      limits: {
        es: [
          "Nada se modela dentro de HYDRA aquí: es la identidad de un proveedor externo (URL, credenciales OAuth) al que te conectas.",
          "El contenido que viaja por esta conexión se modela en Gestión de Proyectos/Integraciones, no aquí."
        ],
        en: [
          "Nothing inside HYDRA is modeled here: it is the identity of an external provider (URL, OAuth credentials) you connect to.",
          "The content that travels over this connection is modeled in Project Management/Integrations, not here."
        ]
      },
      relations: [
        { to: "project", es: "Permite importar/exportar el archivo del proyecto; verificablemente no alimenta Recuperación Externa (son caminos de código separados).", en: "Enables project file import/export; verifiably does not feed External Recovery (separate code paths)." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/remote/git/RemoteGitController.java", line: 16 },
        { path: "src/main/java/com/example/editor/remote/storage/web/RemoteStorageController.java", line: 29 }
      ]
    },
    toolchains: {
      kind: "backend", inUser: true,
      es: {
        user: ["Cadenas de herramientas", "motores instalados en el equipo"],
        functional: ["Toolchains", "motores instalados, sondeos"],
        technical: ["Toolchains", "ATL · ETL · Acceleo · EGL · Xtend · npm/mvn"]
      },
      en: {
        user: ["Toolchains", "engines installed on the machine"],
        functional: ["Toolchains", "installed engines, probes"],
        technical: ["Toolchains", "ATL · ETL · Acceleo · EGL · Xtend · npm/mvn"]
      },
      detail: {
        es: [
          "Comprueba qué motores de transformación de terceros (ATL, ETL, Acceleo, EGL, Xtend) y qué build tools (npm, mvn) están realmente instalados en el equipo.",
          "Ofrece sondeos de versión reales — nunca simula la presencia de un motor que falta.",
          "Si un motor o build tool falta, la operación que lo necesita falla de forma visible en vez de simular un resultado."
        ],
        en: [
          "Checks which third-party transformation engines (ATL, ETL, Acceleo, EGL, Xtend) and which build tools (npm, mvn) are actually installed on the machine.",
          "Offers real version probes — it never simulates the presence of a missing engine.",
          "If an engine or build tool is missing, the operation that needs it fails visibly instead of simulating a result."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: la identidad y el paquete verificado (checksum) de cada motor/herramienta instalada, y el resultado de su sondeo de versión.",
          "No se modela aquí: las reglas de transformación que un motor ejecuta (eso es Transformation) ni la tecnología de integración elegida (eso es Integration)."
        ],
        en: [
          "Modeled here: the identity and checksum-verified package of each installed engine/tool, and its version-probe result.",
          "Not modeled here: the transformation rules an engine executes (that's Transformation) or the chosen integration technology (that's Integration)."
        ]
      },
      relations: [
        { to: "transformation", es: "La ejecución con paridad nativa requiere el motor externo instalado y verificado aquí.", en: "Native-parity execution requires the external engine installed and verified here." },
        { to: "integration", es: "Verificar un proyecto generado requiere que las build tools estén presentes.", en: "Verifying a generated project requires the build tools to be present." },
        { to: "grammar", es: "Construir la tecnología elegida de una gramática requiere npm/mvn presentes.", en: "Building the chosen technology for a grammar requires npm/mvn to be present." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/integration/compatibility/NativeToolchainProbeExecutor.java", line: 12 },
        { path: "toolchains/official/profiles/atl.properties" }
      ]
    },
    generated_projects: {
      kind: "backend", inUser: true,
      es: {
        user: ["Proyectos generados", "ejecutar y construir lo publicado"],
        functional: ["Generated Projects", "etapas de build, comandos"],
        technical: ["Generated Projects", "GeneratedProjectLifecycleService"]
      },
      en: {
        user: ["Generated Projects", "build and run what was published"],
        functional: ["Generated Projects", "build stages, commands"],
        technical: ["Generated Projects", "GeneratedProjectLifecycleService"]
      },
      detail: {
        es: [
          "Registra, genera, valida, abre y lanza un 'proyecto generado' como una secuencia fija de etapas (GENERATE → VALIDATE → OPEN → LAUNCH), cada una resuelta a un comando real del sistema operativo.",
          "Ofrece un modo dry-run para previsualizar qué comandos se ejecutarían, sin tocar nada.",
          "LAUNCH es la única etapa opcional; no hay un estado 'en ejecución' persistente — cada invocación produce un informe y termina."
        ],
        en: [
          "Registers, generates, validates, opens and launches a 'generated project' as a fixed stage sequence (GENERATE → VALIDATE → OPEN → LAUNCH), each resolved to a real OS command.",
          "Offers a dry-run mode to preview which commands would run, without touching anything.",
          "LAUNCH is the only optional stage; there is no persistent 'running' state — each invocation produces a report and ends."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: las etapas fijas del ciclo de vida de un proyecto generado y su informe de ejecución pasa/falla por etapa.",
          "No se modela aquí: la generación del contenido en sí (eso es Publication); aquí solo se registra, construye y ejecuta lo ya generado."
        ],
        en: [
          "Modeled here: the fixed lifecycle stages of a generated project and its per-stage pass/fail execution report.",
          "Not modeled here: generating the content itself (that's Publication); this only registers, builds and runs what was already generated."
        ]
      },
      relations: [],
      sources: [
        { path: "src/main/java/com/example/editor/generated/project/GeneratedProjectLifecycleService.java", line: 19 }
      ]
    },
    portability: {
      kind: "backend", inUser: true,
      es: {
        user: ["Portabilidad", "mover capacidades entre proyectos"],
        functional: ["Portability", "paquetes de capacidad, fusión"],
        technical: ["Portability", "CapabilityPackageArchiveService"]
      },
      en: {
        user: ["Portability", "move capabilities between projects"],
        functional: ["Portability", "capability packages, merge"],
        technical: ["Portability", "CapabilityPackageArchiveService"]
      },
      detail: {
        es: [
          "Empaqueta, versiona, fusiona semánticamente y aplica/revierte de forma transaccional las 'capas de capacidad' de un proyecto (metamodelo+gramática+editor+validaciones+…) como un paquete portable.",
          "Ofrece una previsualización de conflictos de fusión antes de aplicarlos, y un journal append-only para poder revertir.",
          "No es una simple copia de ficheros: resuelve dependencias entre capas al empaquetar."
        ],
        en: [
          "Packages, versions, semantically merges and transactionally applies/reverts a project's 'capability layers' (metamodel+grammar+editor+validations+…) as a portable package.",
          "Offers a merge-conflict preview before applying it, and an append-only journal so it can be reverted.",
          "Not a plain file copy: it resolves dependencies between layers when packaging."
        ]
      },
      limits: {
        es: [
          "Sí se modela aquí: qué capas de capacidad se empaquetan, su compatibilidad/versión declarada, y la decisión de fusión aplicada o revertida.",
          "No se modela aquí: el contenido de cada capa en sí (metamodelo, reglas…); eso sigue viviendo en su propia capacidad."
        ],
        en: [
          "Modeled here: which capability layers are packaged, their declared compatibility/version, and the merge decision applied or reverted.",
          "Not modeled here: the content of each layer itself (metamodel, rules…); that still lives in its own capability."
        ]
      },
      relations: [
        { to: "project", es: "Empaqueta y mueve las capacidades activas del proyecto; las capacidades activas son la entrada de qué se puede empaquetar.", en: "Packages and moves the project's active capabilities; active capabilities are the input for what can be packaged." }
      ],
      sources: [
        { path: "src/main/java/com/example/editor/portability/archive/CapabilityPackageArchiveService.java", line: 41 }
      ]
    }
  };
  var ORDER = ["external_repositories","project","recovery","toolchains","grammar","metamodel",
    "model_editor","validation","events","transformation","gamification","integration",
    "portability","publication","generated_projects","security","persistence","audit"];
return {COMPONENTS:COMPONENTS, ORDER:ORDER};
})();
