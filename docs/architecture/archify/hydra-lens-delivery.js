/* HYDRA portal lens — group 'delivery': integration, publication, generated_projects (FUNCTIONAL and TECHNICAL projections). */
(function(){ "use strict"; var HM = window.HM, T = HM.T;

  /* ===================================================== INTEGRATION ===================================================== */
  HM.lens({
    caps: {
      integration: {
        func: {
          title: T("Entrega a tecnologías externas con paridad nativa verificable", "Delivery to external technologies with verifiable native parity"),
          summary: T("Un perfil de integración enlaza un metamodelo (o ámbito semántico) con exactamente una tecnología destino y un modo de entrega, y recorre un flujo fijo de nueve etapas. Cada etapa tiene una acción y un estado derivado (listo, listo con avisos, bloqueado, externo, no aplicable). El modelo de HYDRA sigue siendo la fuente de verdad: lo nativo se regenera, se sincroniza por huellas y solo se declara equivalente si un catálogo cerrado de pruebas lo demuestra.",
                     "An integration profile links a metamodel (or semantic scope) to exactly one target technology and one delivery mode, and walks a fixed nine-stage flow. Each stage has an action and a derived state (ready, ready with warnings, blocked, external, not applicable). The HYDRA model remains the source of truth: native output is regenerated, synchronised by fingerprints and only declared equivalent when a closed proof catalogue demonstrates it."),
          inputs: [T("Metamodelo o ámbito semántico guardado", "Saved metamodel or semantic scope"), T("Tecnología destino y modo de entrega", "Target technology and delivery mode"), T("Características, artefactos y mapeos opcionales", "Optional features, artifacts and mappings"), T("Política de inyección sobre un destino existente", "Injection policy over an existing target"), T("Capacidades del proyecto que el perfil exige", "Project capabilities the profile requires")],
          outputs: [T("Proyecto nativo generado y verificado", "Generated, verified native project"), T("Estado y acción por cada una de las nueve etapas", "State and action for each of the nine stages"), T("Cambios previsualizados y aplicados con reversión", "Previewed and applied changes with rollback"), T("Manifiesto de sincronización elemento ↔ fichero nativo", "Element ↔ native file synchronisation manifest"), T("Certificación de paridad por capacidad", "Parity certification per capability")],
          conditions: [T("Sin metamodelo o ámbito, tecnología y modo de entrega el perfil se rechaza", "Without metamodel or scope, technology and delivery mode the profile is rejected"), T("Si la compilación falla, las etapas posteriores quedan bloqueadas", "If compilation fails, later stages are blocked"), T("Aplicar exige una previsualización lista y un registro de reversión", "Applying requires a ready preview and a rollback record"), T("Ejecutar con la herramienta nativa es siempre externo", "Running with the native tool is always external"), T("Un conflicto manual exige decisión explícita; nada se resuelve en silencio", "A manual conflict requires an explicit decision; nothing is resolved silently"), T("Capacidad sin pruebas registradas queda sin verificar (fallo cerrado)", "A capability with no registered proofs stays unverified (fail-closed)")]
        },
        tech: {
          title: T("Servicios de perfil, flujo de entrega, preparación y paridad nativa", "Profile, delivery-workflow, readiness and native-parity services"),
          summary: T("El backend modela el perfil como IntegrationProfileModel, calcula la preparación por etapa en TechnologyDeliveryReadinessService y expone el flujo vía POST .../technology/{validate,preview,apply,sync/preview} y .../native-parity/certify bajo /api/canonical-metamodels/{metamodelId}/integrations/{profileId}/technology (y la variante /api/semantic-contexts/...). La certificación la evalúa NativeParityEvidenceAssessor contra NativeParityProofCatalog. El frontend es el diseñador IntegrationDesigner.tsx con asistente de seis pasos; la certificación de paridad no tiene consumidor de interfaz fuera del cliente generado.",
                     "The backend models the profile as IntegrationProfileModel, computes per-stage readiness in TechnologyDeliveryReadinessService and exposes the flow through POST .../technology/{validate,preview,apply,sync/preview} and .../native-parity/certify under /api/canonical-metamodels/{metamodelId}/integrations/{profileId}/technology (and the /api/semantic-contexts/... variant). Certification is evaluated by NativeParityEvidenceAssessor against NativeParityProofCatalog. The frontend is the IntegrationDesigner.tsx designer with a six-step wizard; parity certification has no UI consumer outside the generated client.")
        },
        elements: {
          profile: {
            nf: T("Perfil de integración (contrato de entrega a una tecnología)", "Integration profile (delivery contract to a technology)"),
            fm: T("Entidad raíz del flujo: pertenece a un metamodelo o ámbito semántico (0..N perfiles por metamodelo, uno por tecnología) y agrega exactamente una tecnología y un modo de entrega, más colecciones opcionales de características, artefactos y mapeos. Evoluciona de borrador a analizado y guardado; si declara versión de esquema, debe coincidir con la vigente.",
                  "Root entity of the flow: it belongs to a metamodel or semantic scope (0..N profiles per metamodel, one per technology) and aggregates exactly one technology and one delivery mode, plus optional collections of features, artifacts and mappings. It evolves from draft to analysed to saved; if it declares a schema version it must match the current one."),
            tm: T("Registro IntegrationProfileModel con schemaVersion, id, metamodelId, semanticScopeId, name, target, deliveryMode, features, artifacts, mappings, injectionPolicy, requiredCapabilities y annotations; id, destino y modo son obligatorios.",
                  "IntegrationProfileModel record with schemaVersion, id, metamodelId, semanticScopeId, name, target, deliveryMode, features, artifacts, mappings, injectionPolicy, requiredCapabilities and annotations; id, target and delivery mode are mandatory.")
          },
          target: {
            nf: T("Tecnología destino (plataforma externa de entrega)", "Target technology (external delivery platform)"),
            fm: T("Parámetro exclusivo del perfil (1 perfil ↔ 1 tecnología) que decide qué etapas son aplicables, con qué nivel de soporte (completo, parcial, externo, no soportado) y qué pruebas nativas pueden certificarlo. Una etapa que la tecnología no soporta queda bloqueada.",
                  "Exclusive parameter of the profile (1 profile ↔ 1 technology) that decides which stages apply, at what support level (full, partial, external, unsupported) and which native proofs can certify it. A stage the technology does not support is blocked."),
            tm: T("Identificadores de tecnología del catálogo de pruebas (IntegrationTarget), con espejo en el frontend (integration-designer/types).",
                  "Technology identifiers from the proof catalogue (IntegrationTarget), mirrored in the frontend (integration-designer/types)."),
            variants: [
              { k: "EMF", fd: T("Entrega como módulo de modelado Eclipse; es una de las tecnologías con prueba de compilación nativa.", "Delivery as an Eclipse modelling module; one of the technologies with a native compilation proof.") },
              { k: "SIRIUS_DESKTOP", fd: T("Entrega de un editor gráfico de escritorio sobre Eclipse; su única prueba registrada es la validación del plugin.", "Delivery of a desktop graphical editor on Eclipse; its only registered proof is plugin validation.") },
              { k: "XTEXT", fd: T("Entrega de un lenguaje textual en Eclipse partiendo de una gramática; prueba de generación del lenguaje.", "Delivery of a textual language in Eclipse starting from a grammar; language generation proof.") },
              { k: "LANGIUM", fd: T("Entrega de un lenguaje textual con servicio de lenguaje; prueba de generación del lenguaje.", "Delivery of a textual language with a language service; language generation proof.") },
              { k: "ECLIPSE_GLSP", fd: T("Entrega de un editor gráfico web con parte cliente y parte servidor, probadas por separado.", "Delivery of a web graphical editor with a client part and a server part, proven separately.") },
              { k: "BESSER_SDK", fd: T("Entrega como paquete de modelado BESSER; se demuestra ejecutando las pruebas del paquete.", "Delivery as a BESSER modelling package; demonstrated by running the package tests.") },
              { k: "BESSER_WEB_MODELING_EDITOR", fd: T("Entrega como extensión del editor web de modelado BESSER, con prueba de la extensión.", "Delivery as an extension of the BESSER web modelling editor, with an extension proof.") },
              { k: "VSCODE_EXTENSION", fd: T("Entrega como extensión del editor de código; se demuestra su ciclo de vida en ejecución.", "Delivery as a code-editor extension; its runtime lifecycle is demonstrated.") },
              { k: "MXGRAPH_DRAWIO", fd: T("Entrega como lienzo gráfico y complemento de Draw.io, con pruebas de humo de ambos.", "Delivery as a graphical canvas and Draw.io plugin, with smoke proofs for both.") }
            ]
          },
          delivery_mode: {
            nf: T("Modo de entrega (forma de integrar el resultado en el destino)", "Delivery mode (how the result is integrated into the target)"),
            fm: T("Atributo obligatorio y único por perfil que distingue, por ejemplo, producir un proyecto nuevo de modificar uno existente. Determina si hace falta una carpeta destino y cómo se acotan los cambios; los valores concretos no se analizaron.",
                  "Mandatory, single attribute per profile that distinguishes, for example, producing a new project from modifying an existing one. It determines whether a target folder is needed and how changes are bounded; the concrete values were not analysed."),
            tm: T("Campo deliveryMode (cadena o enumeración) obligatorio en IntegrationProfileModel.", "Mandatory deliveryMode field (string or enum) in IntegrationProfileModel.")
          },
          features: {
            nf: T("Características, artefactos y mapeos (alcance de la entrega)", "Features, artifacts and mappings (delivery scope)"),
            fm: T("Tres colecciones opcionales del perfil, vacías por defecto: qué funcionalidades se entregan, qué documentos de entrada alimentan la generación (algunos se incluyen automáticamente) y cómo se enlazan los elementos del modelo con el proyecto destino. Pueden editarse en cualquier momento antes de generar y cambian el conjunto de ficheros producido.",
                  "Three optional collections of the profile, empty by default: which functionality is delivered, which input documents feed generation (some are included automatically) and how model elements are linked to the target project. They can be edited at any time before generating and change the set of files produced."),
            tm: T("Colecciones features, artifacts y mappings de IntegrationProfileModel, vacías por defecto.", "features, artifacts and mappings collections of IntegrationProfileModel, empty by default.")
          },
          injection: {
            nf: T("Política de inyección (límites de intervención sobre un destino vivo)", "Injection policy (intervention limits over a live target)"),
            fm: T("Conjunto de reglas, único por perfil, que acota qué partes de un proyecto ya existente puede modificar la aplicación de cambios, para proteger el trabajo nativo. Si no se declara, rige una política conservadora; sus restricciones internas no se analizaron.",
                  "A single rule set per profile that bounds which parts of an already existing project the application of changes may modify, to protect native work. If none is declared, a conservative policy applies; its internal restrictions were not analysed."),
            tm: T("Tipo InjectionPolicy; cuando falta se usa safeDefaults(). Espejo en integration-designer/types.", "InjectionPolicy type; when absent, safeDefaults() is used. Mirrored in integration-designer/types.")
          },
          required_caps: {
            nf: T("Capacidades requeridas (precondiciones del proyecto)", "Required capabilities (project preconditions)"),
            fm: T("Referencia 0..N a capacidades del proyecto que deben estar activas para entregar la tecnología. Se contrastan al analizar el perfil y pueden producir avisos o bloqueos; el efecto exacto de una ausencia no está verificado.",
                  "A 0..N reference to project capabilities that must be active to deliver the technology. They are checked when the profile is analysed and may produce warnings or blocks; the exact effect of an absence is not verified."),
            tm: T("Colección requiredCapabilities del perfil, vacía por defecto.", "requiredCapabilities collection of the profile, empty by default.")
          },
          stage: {
            nf: T("Etapa del flujo de entrega (paso fijo con acción y estado)", "Delivery workflow stage (fixed step with action and state)"),
            fm: T("Cada perfil recorre nueve etapas ordenadas, cada una con una sola acción y un estado derivado. Configurar, analizar y generar son obligatorias; verificar, previsualizar y aplicar dependen de condiciones previas; ejecutar, sincronizar y exportar son opcionales. Las no aplicables carecen de acción y las no soportadas se bloquean.",
                  "Each profile walks nine ordered stages, each with a single action and a derived state. Configure, analyse and generate are mandatory; verify, preview and apply depend on prior conditions; run, synchronise and export are optional. Non-applicable stages have no action and unsupported ones are blocked."),
            tm: T("Enumeraciones TechnologyWorkflowStage (9) y TechnologyWorkflowAction (11); las reglas por etapa están en TechnologyDeliveryReadinessService.", "TechnologyWorkflowStage (9) and TechnologyWorkflowAction (11) enums; per-stage rules live in TechnologyDeliveryReadinessService."),
            variants: [
              { k: "CONFIGURE", fd: T("Edición del perfil; siempre disponible como punto de partida.", "Profile editing; always available as the starting point."), td: T("Acción EDIT_PROFILE.", "Action EDIT_PROFILE.") },
              { k: "ANALYZE", fd: T("Contraste de compatibilidad del perfil con la tecnología; separa errores (bloqueos) de avisos.", "Compatibility check of the profile against the technology; separates errors (blockers) from warnings."), td: T("Acción CHECK_PROFILE.", "Action CHECK_PROFILE.") },
              { k: "GENERATE", fd: T("Compilación del proyecto nativo; solo se intenta con un perfil válido y su fallo bloquea lo posterior.", "Compilation of the native project; attempted only with a valid profile and its failure blocks what follows."), td: T("Acción COMPILE_ARCHIVE.", "Action COMPILE_ARCHIVE.") },
              { k: "VERIFY", fd: T("Validación del proyecto generado; condicionada a que compile.", "Validation of the generated project; conditional on it compiling."), td: T("Acción VALIDATE_PROJECT.", "Action VALIDATE_PROJECT.") },
              { k: "PREVIEW", fd: T("Cálculo de cambios sin persistirlos; condicionada a conocer una carpeta destino.", "Computation of changes without persisting them; conditional on a known target folder."), td: T("Acción PREVIEW_CHANGES.", "Action PREVIEW_CHANGES.") },
              { k: "APPLY", fd: T("Persistencia de los cambios en el destino; condicionada a una previsualización lista y a un registro de reversión.", "Persistence of the changes into the target; conditional on a ready preview and a rollback record."), td: T("Acción APPLY_CHANGES; es la única etapa con previsualización requerida.", "Action APPLY_CHANGES; the only stage with a required preview.") },
              { k: "EXECUTE", fd: T("Lanzamiento de la herramienta nativa; siempre externo, HYDRA lo señala pero no lo ejecuta.", "Launch of the native tool; always external, HYDRA flags it but does not run it."), td: T("Acción RUN_NATIVE_HOST, estado EXTERNAL.", "Action RUN_NATIVE_HOST, state EXTERNAL.") },
              { k: "ROUND_TRIP", fd: T("Detección de cambios hechos fuera y decisión sobre cada conflicto.", "Detection of changes made outside and a decision on each conflict."), td: T("Acción ROUND_TRIP_SYNC.", "Action ROUND_TRIP_SYNC.") },
              { k: "EXPORT", fd: T("Extracción del proyecto o de su proyección fuera de HYDRA.", "Extraction of the project or its projection outside HYDRA."), td: T("Acciones EXPORT_PROJECT o EXPORT_PROJECTION; la segunda solo indica el espacio de exportación.", "Actions EXPORT_PROJECT or EXPORT_PROJECTION; the latter only indicates the export space.") }
            ]
          },
          readiness: {
            nf: T("Informe de preparación (estado derivado de cada etapa)", "Readiness report (derived state of each stage)"),
            fm: T("Vista de solo lectura, recalculada al abrir el panel, que resume si el perfil es válido, si compila, cuántos ficheros salen y qué acción corresponde a cada etapa, con sus bloqueos y avisos. Un error se vuelve bloqueo y un aviso sigue siendo aviso; solo se intenta compilar con un análisis válido.",
                  "Read-only view, recalculated when the panel opens, that summarises whether the profile is valid, whether it compiles, how many files come out and which action belongs to each stage, with its blockers and warnings. An error becomes a blocker and a warning stays a warning; compilation is only attempted with a valid analysis."),
            tm: T("Registro TechnologyDeliveryReadiness (profileValid, compilationReady, generatedFileCount, nativeVerificationAvailable, actions, blockers, warnings) con TechnologyDeliveryActionDescriptor y TechnologyDeliveryActionState por etapa; se calcula en TechnologyDeliveryReadinessService y se expone en GET .../technology/readiness.",
                  "TechnologyDeliveryReadiness record (profileValid, compilationReady, generatedFileCount, nativeVerificationAvailable, actions, blockers, warnings) with a TechnologyDeliveryActionDescriptor and TechnologyDeliveryActionState per stage; computed in TechnologyDeliveryReadinessService and exposed at GET .../technology/readiness."),
            variants: [
              { fd: T("La etapa puede ejecutarse ahora con soporte completo y sin avisos.", "The stage can run now with full support and no warnings.") },
              { fd: T("La etapa es ejecutable pero con soporte parcial o avisos asociados.", "The stage is runnable but with partial support or associated warnings.") },
              { fd: T("La etapa no puede ejecutarse: errores de análisis, compilación fallida o tecnología sin soporte.", "The stage cannot run: analysis errors, failed compilation or an unsupported technology.") },
              { fd: T("La etapa la realiza una herramienta ajena a HYDRA (caso de ejecutar).", "The stage is carried out by a tool outside HYDRA (the run case).") },
              { fd: T("La etapa carece de sentido para ese perfil y no tiene acción.", "The stage makes no sense for that profile and has no action.") }
            ]
          },
          roundtrip: {
            nf: T("Manifiesto de sincronización (trazabilidad elemento ↔ fichero nativo)", "Synchronisation manifest (element ↔ native file traceability)"),
            fm: T("Registro de enlaces entre elementos canónicos y ficheros nativos con la huella de lo generado y su propiedad. Al sincronizar, cada elemento se clasifica en una categoría y los conflictos manuales reciben exactamente una decisión explícita; la sincronización es manual y por huella, no bidireccional automática, y puede revertirse con copia de seguridad.",
                  "Register of links between canonical elements and native files with the generated fingerprint and ownership. When synchronising, each element is classified into a category and manual conflicts receive exactly one explicit decision; synchronisation is manual and fingerprint-based, not automatically bidirectional, and can be reverted from a backup."),
            tm: T("RoundTripManifest con RoundTripElementMapping (canonicalDocumentId, artifactKind, nativePath, sourceElementIds, generatedSha256, ownership) y ElementConflictDecision; vive en integration/roundtrip.",
                  "RoundTripManifest with RoundTripElementMapping (canonicalDocumentId, artifactKind, nativePath, sourceElementIds, generatedSha256, ownership) and ElementConflictDecision; lives under integration/roundtrip."),
            variants: [
              { k: "ADD", fd: T("Elemento nuevo en HYDRA sin fichero nativo: se crea el fichero.", "New element in HYDRA without a native file: the file is created.") },
              { k: "REGENERATE", fd: T("Cambió el modelo y el fichero nativo sigue intacto: se regenera sin riesgo.", "The model changed and the native file is untouched: it is regenerated safely.") },
              { k: "UNCHANGED", fd: T("Modelo y fichero coinciden con la huella: no hay nada que hacer.", "Model and file match the fingerprint: nothing to do.") },
              { k: "NATIVE_DELETED", fd: T("El fichero nativo ya no existe aunque el elemento sí: hay que decidir si se restituye.", "The native file no longer exists although the element does: a decision is needed on restoring it.") },
              { k: "ORPHAN_NATIVE", fd: T("Existe un fichero nativo que ningún elemento del modelo reclama.", "A native file exists that no model element claims.") },
              { k: "MANUAL_CONFLICT", fd: T("Ambos lados cambiaron: se exige decisión explícita del usuario.", "Both sides changed: an explicit user decision is required.") },
              { k: "KEEP_HYDRA", fd: T("Decisión de conflicto: prevalece la versión generada desde el modelo.", "Conflict decision: the version generated from the model prevails.") },
              { k: "MERGE_SAFE", fd: T("Decisión de conflicto: combinar preservando el trabajo nativo.", "Conflict decision: combine while preserving native work.") }
            ]
          },
          parity: {
            nf: T("Certificación de paridad nativa (prueba real de equivalencia)", "Native parity certification (real proof of equivalence)"),
            fm: T("Evaluación de 1 a 15 capacidades de una tecnología frente a un catálogo cerrado de pruebas nativas. Cada capacidad es demostrada (todas sus pruebas pasan), fallida (alguna falla) o sin verificar (no hay pruebas registradas), y la certificación global solo se concede si el modo fue nativo obligatorio, la validación pasó y todas las pedidas están demostradas. El solicitante no puede aportar órdenes ni pruebas propias.",
                  "Assessment of 1 to 15 capabilities of a technology against a closed catalogue of native proofs. Each capability is proven (all its proofs pass), failed (one fails) or unverified (no proofs registered), and the overall certification is granted only if the mode was mandatory native, validation passed and all requested ones are proven. The requester cannot supply commands or proofs of their own."),
            tm: T("NativeParityCapability (15), NativeParityEvidenceStatus, NativeParityCertificationRequest, NativeParityProofCatalog y NativeParityEvidenceAssessor; el informe lleva technology, certified, provenCount, requestedCount, capabilities y validationStatus. Se invoca en POST .../native-parity/certify.",
                  "NativeParityCapability (15), NativeParityEvidenceStatus, NativeParityCertificationRequest, NativeParityProofCatalog and NativeParityEvidenceAssessor; the report carries technology, certified, provenCount, requestedCount, capabilities and validationStatus. Invoked via POST .../native-parity/certify."),
            variants: [
              { k: "METAMODEL", fd: T("Capacidad certificable: el metamodelo entregado se comporta como el nativo.", "Certifiable capability: the delivered metamodel behaves like the native one.") },
              { k: "GRAPHICAL_EDITOR", fd: T("Capacidad certificable: edición gráfica en la tecnología destino.", "Certifiable capability: graphical editing in the target technology.") },
              { k: "TEXTUAL_EDITOR", fd: T("Capacidad certificable: edición textual en la tecnología destino.", "Certifiable capability: textual editing in the target technology.") },
              { k: "VALIDATION", fd: T("Capacidad certificable: las reglas de validación funcionan en el destino.", "Certifiable capability: validation rules work in the target.") },
              { k: "QUICK_FIX", fd: T("Capacidad certificable: las correcciones rápidas funcionan en el destino.", "Certifiable capability: quick fixes work in the target.") },
              { k: "LANGUAGE_SERVICE_SMOKE", fd: T("Capacidad certificable: el servicio de lenguaje arranca y responde.", "Certifiable capability: the language service starts and responds.") },
              { k: "GRAPHICAL_BRIDGE_SMOKE", fd: T("Capacidad certificable: el puente gráfico arranca y responde.", "Certifiable capability: the graphical bridge starts and responds.") },
              { k: "NATIVE_BUILD", fd: T("Capacidad certificable: el proyecto compila con las herramientas nativas.", "Certifiable capability: the project builds with the native tools.") },
              { k: "RUNTIME_LIFECYCLE", fd: T("Capacidad certificable: el ciclo de vida en ejecución se comporta como el nativo.", "Certifiable capability: the runtime lifecycle behaves like the native one.") },
              { k: "OPERATION", fd: T("Capacidad certificable: las operaciones del dominio se ejecutan en el destino.", "Certifiable capability: domain operations run in the target.") },
              { k: "EVENT_FLOW", fd: T("Capacidad certificable: el flujo de eventos se reproduce en el destino.", "Certifiable capability: the event flow is reproduced in the target.") },
              { k: "GAMIFICATION", fd: T("Capacidad certificable: la gamificación funciona en el destino.", "Certifiable capability: gamification works in the target.") },
              { k: "TRANSFORMATION", fd: T("Capacidad certificable: las transformaciones funcionan en el destino.", "Certifiable capability: transformations work in the target.") },
              { k: "ROUND_TRIP", fd: T("Capacidad certificable: la sincronización con lo nativo es coherente.", "Certifiable capability: synchronisation with the native side is coherent.") },
              { k: "PERSISTENCE", fd: T("Capacidad certificable: la persistencia funciona en el destino.", "Certifiable capability: persistence works in the target.") },
              { k: "PROVEN", fd: T("Resultado de capacidad: todas sus pruebas registradas pasaron.", "Capability outcome: all its registered proofs passed.") },
              { k: "FAILED", fd: T("Resultado de capacidad: al menos una de sus pruebas falló.", "Capability outcome: at least one of its proofs failed.") },
              { k: "UNVERIFIED", fd: T("Resultado de capacidad: no hay pruebas registradas, por lo que no puede afirmarse equivalencia.", "Capability outcome: no proofs are registered, so equivalence cannot be claimed.") }
            ]
          },
          proof_catalog: {
            nf: T("Catálogo cerrado de pruebas (evidencia admisible por tecnología y capacidad)", "Closed proof catalogue (admissible evidence per technology and capability)"),
            fm: T("Tabla fija e inmutable por el usuario que asigna a cada pareja tecnología-capacidad entre una y dos pruebas nativas. Solo cubre algunas combinaciones; las demás (por ejemplo, todo lo de Sirius, Xtext o EMF salvo compilación) quedan sin verificar por diseño. Garantiza que las pruebas son conocidas de antemano.",
                  "Fixed table, immutable by the user, that assigns one or two native proofs to each technology-capability pair. It covers only some combinations; the others (for example everything for Sirius, Xtext or EMF except compilation) stay unverified by design. It guarantees that the proofs are known in advance."),
            tm: T("Mapa tecnología → capacidad → identificadores de prueba en NativeParityProofCatalog (compile-emf-module, validate-sirius-plugin, generate-xtext-language, generate-langium-language, build-glsp-client/server, test-besser-package, test-besser-wme-extension, test-vscode-runtime, mxgraph-runtime-smoke, drawio-plugin-smoke).",
                  "technology → capability → proof-identifier map in NativeParityProofCatalog (compile-emf-module, validate-sirius-plugin, generate-xtext-language, generate-langium-language, build-glsp-client/server, test-besser-package, test-besser-wme-extension, test-vscode-runtime, mxgraph-runtime-smoke, drawio-plugin-smoke).")
          }
        }
      }
    },
    procs: {
      integration_deliver: {
        nf: T("Entrega por etapas a una tecnología destino", "Staged delivery to a target technology"),
        df: T("Recorrido secuencial del perfil por las etapas de configurar a exportar. Las tres primeras son obligatorias; verificar solo ocurre si el proyecto compila; aplicar exige previsualización lista y deja un registro de reversión; ejecutar es externo y sincronizar y exportar son opcionales.",
              "Sequential walk of the profile through the stages from configure to export. The first three are mandatory; verify only happens if the project compiles; apply requires a ready preview and leaves a rollback record; run is external, and synchronise and export are optional."),
        nt: T("Cadena technology/validate → preview → apply", "technology/validate → preview → apply chain"),
        dt: T("Cada etapa se habilita según TechnologyDeliveryReadinessService (generar compila solo si el análisis es válido; ejecutar siempre EXTERNAL) y se invoca vía POST .../technology/validate, /preview y /apply. La sincronización usa POST .../technology/sync/preview; el detalle de exportar no está verificado.",
              "Each stage is enabled according to TechnologyDeliveryReadinessService (generate compiles only if the analysis is valid; run is always EXTERNAL) and is invoked via POST .../technology/validate, /preview and /apply. Synchronisation uses POST .../technology/sync/preview; the detail of export is not verified."),
        nodes: {
          conf: { lf: T("Fijar tecnología y modo en el perfil", "Fix technology and mode in the profile"), sf: T("metamodelo → perfil borrador", "metamodel → draft profile"), fio: T("Metamodelo, tecnología y modo de entrega → perfil guardado", "Metamodel, technology and delivery mode → saved profile") },
          ana: { lf: T("Contrastar perfil y tecnología", "Check profile against technology"), sf: T("errores → bloqueos; avisos → avisos", "errors → blockers; warnings → warnings"), fio: T("Perfil guardado → diagnóstico de compatibilidad", "Saved profile → compatibility diagnosis"),
                 lt: "TechnologyDeliveryReadinessService", st: T("lectura del perfil y su análisis", "reads the profile and its analysis"), tx: T("Los errores del análisis se convierten en bloqueos y los avisos se conservan como avisos en el informe de preparación.", "Analysis errors become blockers and warnings are kept as warnings in the readiness report.") },
          gen: { lf: T("Materializar el proyecto nativo", "Materialise the native project"), sf: T("perfil válido → artefactos compilados", "valid profile → compiled artifacts"), fio: T("Perfil válido → proyecto nativo compilado, o bloqueo", "Valid profile → compiled native project, or blocker"),
                 lt: "TechnologyDeliveryReadinessService", st: T("compila solo si el análisis es válido", "compiles only if the check is valid"), tx: T("La compilación se intenta únicamente cuando el análisis del perfil es válido; un fallo se anota como bloqueo que bloquea las etapas posteriores.", "Compilation is attempted only when the profile check is valid; a failure is recorded as a blocker that blocks later stages.") },
          ok: { lf: T("Bifurcar según resultado de compilación", "Branch on compilation outcome"), sf: T("compila → verificable; no → bloqueo", "compiles → verifiable; otherwise → blocker"), fio: T("Resultado de compilación → continuar o terminar bloqueado", "Compilation outcome → continue or end blocked") },
          ver: { lf: T("Validar el proyecto generado", "Validate the generated project"), sf: T("proyecto compilado → verificación nativa", "compiled project → native verification"), fio: T("Proyecto compilado → pasos de verificación nativa contados", "Compiled project → native verification steps counted"),
                 lt: "POST .../technology/validate", st: T("TechnologyDeliveryReadinessService", "TechnologyDeliveryReadinessService"), tx: T("Etapa VERIFY condicionada a que la compilación esté lista; cuenta los pasos de verificación nativa disponibles en el informe de preparación.", "The VERIFY stage is conditional on compilation being ready; it counts the native verification steps available in the readiness report.") },
          pre: { lf: T("Calcular el delta sobre el destino", "Compute the delta on the target"), sf: T("carpeta destino → cambios sin persistir", "target folder → unpersisted changes"), fio: T("Carpeta destino y proyecto → lista de cambios propuestos", "Target folder and project → list of proposed changes"),
                 lt: "POST .../technology/preview", st: T("requiere carpeta destino", "requires a target folder"), tx: T("Etapa PREVIEW que exige carpeta destino; no persiste nada en el destino.", "PREVIEW stage that requires a target folder; it persists nothing in the target.") },
          app: { lf: T("Persistir los cambios con reversión", "Persist the changes with rollback"), sf: T("delta listo → destino modificado", "ready delta → modified target"), fio: T("Previsualización lista y registro de reversión → destino modificado", "Ready preview and rollback record → modified target"),
                 lt: "POST .../technology/apply", st: T("exige previsualización lista y reversión", "requires a ready preview and rollback"), tx: T("Etapa APPLY, la única con previsualización requerida; sus prerrequisitos son una previsualización lista y un registro de reversión.", "APPLY stage, the only one with a required preview; its prerequisites are a ready preview and a rollback record.") },
          rb: { lf: T("Conservar el punto de retorno", "Keep the return point"), sf: T("estado previo del destino guardado", "prior target state saved"), fio: T("Cambios aplicados → registro para restaurar el estado anterior", "Applied changes → record to restore the previous state") },
          exe: { lf: T("Delegar la ejecución a la herramienta nativa", "Delegate execution to the native tool"), sf: T("etapa externa, fuera del alcance de HYDRA", "external stage, outside HYDRA's reach"), fio: T("Destino aplicado → ejecución externa no controlada por HYDRA", "Applied target → external execution not controlled by HYDRA"),
                 lt: "RUN_NATIVE_HOST", st: T("estado EXTERNAL", "state EXTERNAL"), tx: T("La etapa EXECUTE siempre se marca como EXTERNAL en TechnologyDeliveryReadinessService; HYDRA no lanza la herramienta.", "The EXECUTE stage is always marked EXTERNAL in TechnologyDeliveryReadinessService; HYDRA does not launch the tool.") },
          rt: { lf: T("Reconciliar lo nativo con el modelo", "Reconcile native work with the model"), sf: T("huellas → seis categorías + decisiones", "fingerprints → six categories + decisions"), fio: T("Ficheros nativos y manifiesto previo → elementos clasificados y conflictos decididos", "Native files and previous manifest → classified elements and decided conflicts"),
                 lt: "POST .../technology/sync/preview", st: T("RoundTripManifest", "RoundTripManifest"), tx: T("La clasificación usa el manifiesto de integration/roundtrip con la huella SHA-256 de lo generado; las categorías son ADD, REGENERATE, UNCHANGED, NATIVE_DELETED, ORPHAN_NATIVE y MANUAL_CONFLICT.", "Classification uses the manifest under integration/roundtrip with the SHA-256 fingerprint of what was generated; the categories are ADD, REGENERATE, UNCHANGED, NATIVE_DELETED, ORPHAN_NATIVE and MANUAL_CONFLICT.") },
          exp: { lf: T("Extraer el proyecto o su proyección", "Extract the project or its projection"), sf: T("resultado → salida fuera de HYDRA", "result → output outside HYDRA"), fio: T("Proyecto entregado → proyecto o proyección exportados", "Delivered project → exported project or projection"),
                 lt: "EXPORT_PROJECT / EXPORT_PROJECTION", st: T("acciones de exportación del flujo", "workflow export actions"), tx: T("La acción EXPORT_PROJECTION solo indica el espacio de exportación; el detalle de la exportación no se verificó.", "The EXPORT_PROJECTION action only indicates the export space; the export detail was not verified.") }
        }
      },
      integration_readiness: {
        nf: T("Derivación del estado de cada etapa", "Derivation of each stage's state"),
        df: T("Cálculo de solo lectura que, a partir del perfil y su análisis, asigna a cada etapa uno de cinco estados. Separa errores de avisos, compila solo si el análisis es válido y propaga un fallo de compilación como bloqueo de las etapas posteriores.",
              "Read-only computation that, from the profile and its analysis, assigns each stage one of five states. It separates errors from warnings, compiles only if the analysis is valid and propagates a compilation failure as a block on later stages."),
        nt: T("TechnologyDeliveryReadinessService → GET .../technology/readiness", "TechnologyDeliveryReadinessService → GET .../technology/readiness"),
        dt: T("El servicio carga el perfil y su análisis, intenta compilar solo si el análisis es válido y compone un TechnologyDeliveryReadiness con un TechnologyDeliveryActionDescriptor por etapa. No ejecuta herramientas externas.",
              "The service loads the profile and its analysis, attempts compilation only if the analysis is valid and builds a TechnologyDeliveryReadiness with one TechnologyDeliveryActionDescriptor per stage. It runs no external tools."),
        nodes: {
          load: { lf: T("Leer perfil y análisis persistidos", "Read persisted profile and analysis"), sf: T("lectura pura, sin efectos", "pure read, no side effects"), fio: T("Perfil guardado → perfil y diagnóstico en memoria", "Saved profile → profile and diagnosis in memory"),
                  lt: "TechnologyDeliveryReadinessService", st: T("carga perfil y comprobación", "loads profile and check"), tx: T("Carga el perfil y ejecuta la comprobación del perfil sin modificar nada.", "Loads the profile and runs the profile check without modifying anything.") },
          diag: { lf: T("Clasificar diagnóstico por gravedad", "Classify the diagnosis by severity"), sf: T("error → bloqueo; aviso → aviso", "error → blocker; warning → warning"), fio: T("Diagnóstico mezclado → bloqueos y avisos separados", "Mixed diagnosis → separated blockers and warnings") },
          val: { lf: T("Decidir si procede compilar", "Decide whether compiling applies"), sf: T("perfil válido → compilar; no → omitir", "valid profile → compile; otherwise → skip"), fio: T("Validez del perfil → intentar o no compilar", "Profile validity → attempt compilation or not") },
          comp: { lf: T("Comprobar que el proyecto compila", "Check that the project compiles"), sf: T("generar condiciona etapas posteriores", "generate conditions later stages"), fio: T("Perfil válido → compilación lista o bloqueo", "Valid profile → compilation ready or blocker"),
                  lt: "TechnologyDeliveryReadinessService", st: T("compila solo con comprobación válida", "compiles only with a valid check"), tx: T("La compilación se condiciona a que la comprobación del perfil sea válida y a que la etapa generar la requiera; un fallo se registra como bloqueo.", "Compilation is conditioned on the profile check being valid and on the generate stage requiring it; a failure is recorded as a blocker.") },
          stg: { lf: T("Resolver el estado de las nueve etapas", "Resolve the state of the nine stages"), sf: T("soporte × diagnóstico → estado por etapa", "support × diagnosis → state per stage"), fio: T("Soporte, avisos y compilación → estado y acción por etapa", "Support, warnings and compilation → state and action per stage"),
                  lt: "TechnologyDeliveryReadinessService.action", st: T("reglas por etapa", "per-stage rules"), tx: T("Las reglas por etapa deciden no aplicable, bloqueada si no está soportada, lista o con avisos, o externa para ejecutar; una compilación fallida bloquea las etapas posteriores y el soporte parcial o los avisos dan «listo con avisos».", "Per-stage rules decide not applicable, blocked if unsupported, ready or with warnings, or external for run; a failed compilation blocks later stages and partial support or warnings yield 'ready with warnings'.") },
          rep: { lf: T("Emitir el informe por etapa", "Emit the per-stage report"), sf: T("un estado y una acción por etapa", "one state and one action per stage"), fio: T("Estados y acciones → informe de preparación", "States and actions → readiness report"),
                 lt: "TechnologyDeliveryReadiness", st: T("registro con acciones, bloqueos y avisos", "record with actions, blockers and warnings"), tx: T("Registro con profileValid, compilationReady, generatedFileCount, nativeVerificationAvailable, actions, blockers y warnings.", "Record with profileValid, compilationReady, generatedFileCount, nativeVerificationAvailable, actions, blockers and warnings.") }
        }
      },
      integration_native_parity: {
        nf: T("Certificación de paridad por capacidades", "Capability-by-capability parity certification"),
        df: T("Se solicita certificar una lista de capacidades de una tecnología. Se compila una vez, se ejecutan las pruebas del catálogo en modo nativo obligatorio y cada capacidad queda demostrada, fallida o sin verificar. El resultado global es certificado o no certificado, nunca declarado.",
              "Certification is requested for a list of capabilities of a technology. The project is compiled once, the catalogue proofs run in mandatory native mode and each capability ends up proven, failed or unverified. The overall result is certified or not certified, never merely declared."),
        nt: T("POST .../native-parity/certify → TechnologyIntegrationApplicationService", "POST .../native-parity/certify → TechnologyIntegrationApplicationService"),
        dt: T("El controlador de integración recibe un NativeParityCertificationRequest; el servicio de aplicación compila una vez, valida en NATIVE_REQUIRED y delega en NativeParityEvidenceAssessor. Sin consumidor de interfaz fuera del cliente generado y sin pruebas propias de la capa de petición y del controlador.",
              "The integration controller receives a NativeParityCertificationRequest; the application service compiles once, validates in NATIVE_REQUIRED and delegates to NativeParityEvidenceAssessor. No UI consumer outside the generated client and no tests of their own for the request layer and controller."),
        nodes: {
          req: { lf: T("Acotar el conjunto a certificar", "Bound the set to certify"), sf: T("1..15 capacidades, sin repetidos", "1..15 capabilities, no duplicates"), fio: T("Tecnología y capacidades → petición de certificación válida", "Technology and capabilities → valid certification request"),
                 lt: "NativeParityCertificationRequest", st: T("validación de la petición", "request validation"), tx: T("La petición exige lista no vacía, sin repetidos y de hasta 15 capacidades; no acepta órdenes de shell ni identificadores de prueba.", "The request requires a non-empty list, without duplicates and of up to 15 capabilities; it accepts no shell commands or proof identifiers.") },
          to: { lf: T("Acotar la duración de la evaluación", "Bound the assessment duration"), sf: T("por defecto 180 s; máximo 900 s", "default 180 s; maximum 900 s"), fio: T("Límite opcional → límite efectivo de la evaluación", "Optional limit → effective assessment limit"),
                lt: "NativeParityCertificationRequest", st: T("tiempo límite en segundos", "timeout in seconds"), tx: T("Un valor cero o negativo usa 180 segundos y un valor superior a 900 se rechaza.", "A zero or negative value uses 180 seconds and a value above 900 is rejected.") },
          cmp: { lf: T("Generar el proyecto nativo una única vez", "Generate the native project exactly once"), sf: T("una compilación para todas las pruebas", "one compilation for all proofs"), fio: T("Petición válida → proyecto nativo compilado", "Valid request → compiled native project"),
                 lt: "TechnologyIntegrationApplicationService", st: T("compila una vez", "compiles once"), tx: T("El servicio de aplicación compila una vez, valida con NATIVE_REQUIRED y pasa el resultado al evaluador.", "The application service compiles once, validates with NATIVE_REQUIRED and passes the result to the assessor.") },
          nat: { lf: T("Contrastar con el comportamiento nativo real", "Contrast with real native behaviour"), sf: T("pruebas del catálogo, modo nativo obligatorio", "catalogue proofs, mandatory native mode"), fio: T("Proyecto compilado y catálogo → resultado de cada prueba nativa", "Compiled project and catalogue → result of each native proof"),
                 lt: "NATIVE_REQUIRED", st: T("herramientas nativas externas", "external native toolchains"), tx: T("Las pruebas las ejecutan las herramientas nativas externas; sin modo nativo obligatorio no puede haber certificación.", "The proofs are run by the external native toolchains; without mandatory native mode there can be no certification.") },
          ass: { lf: T("Clasificar cada capacidad por su evidencia", "Classify each capability by its evidence"), sf: T("pruebas → demostrada / fallida / sin verificar", "proofs → proven / failed / unverified"), fio: T("Resultados de pruebas → estado por capacidad", "Proof results → status per capability"),
                 lt: "NativeParityEvidenceAssessor", st: T("evaluador de fallo cerrado", "fail-closed assessor"), tx: T("Sin pruebas registradas la capacidad queda UNVERIFIED, si todas pasan es PROVEN y si alguna falla es FAILED.", "With no registered proofs the capability is UNVERIFIED, if all pass it is PROVEN and if any fails it is FAILED.") },
          cert: { lf: T("Conceder o denegar la certificación global", "Grant or deny overall certification"), sf: T("nativo + validación + todas demostradas", "native + validation + all proven"), fio: T("Estados por capacidad → certificado o no certificado", "Status per capability → certified or not certified"),
                  lt: "NativeParityEvidenceAssessor", st: T("regla de certificación", "certification rule"), tx: T("Se certifica solo si el modo es nativo obligatorio, la validación pasó y todas las capacidades pedidas están demostradas.", "Certified only if the mode is mandatory native, validation passed and all requested capabilities are proven.") },
          ok: { lf: T("Emitir la evidencia de equivalencia", "Issue the equivalence evidence"), sf: T("demostradas = pedidas", "proven = requested"), fio: T("Todas demostradas → informe certificado", "All proven → certified report") },
          ko: { lf: T("Emitir el diagnóstico de lo no demostrado", "Issue the diagnosis of what is not proven"), sf: T("capacidades fallidas o sin verificar", "failed or unverified capabilities"), fio: T("Alguna no demostrada → informe no certificado con causas", "Some not proven → uncertified report with causes") }
        }
      },
      integration_author_wizard: {
        nf: T("Configuración guiada del perfil de integración", "Guided configuration of the integration profile"),
        df: T("Secuencia de seis pasos que construye el perfil: identidad, tecnología y modo, características y entradas, mapeos, análisis y entrega. El análisis es una puerta: con errores se vuelve a corregir la tecnología; sin ellos se puede generar o aplicar según la preparación.",
              "Six-step sequence that builds the profile: identity, technology and mode, features and inputs, mappings, analysis and delivery. The analysis is a gate: with errors you go back to fix the technology; without them you can generate or apply depending on readiness."),
        nt: T("IntegrationDesigner.tsx (vista GUIDED)", "IntegrationDesigner.tsx (GUIDED view)"),
        dt: T("La vista inicial es GUIDED (con alternativas FORM y STRUCTURE), el paso inicial es identity y los pasos están definidos en guidedSteps; el paso final incorpora el panel IntegrationTechnologyJourney. El detalle de campos de los pasos 2 a 6 no se verificó.",
              "The initial view is GUIDED (with FORM and STRUCTURE alternatives), the initial step is identity and the steps are defined in guidedSteps; the final step embeds the IntegrationTechnologyJourney panel. The field detail of steps 2 to 6 was not verified."),
        nodes: {
          id: { lf: T("Nombrar el perfil", "Name the profile"), sf: T("identificador único + nombre visible opcional", "unique identifier + optional visible name"), fio: T("Identificador → perfil con identidad", "Identifier → profile with identity"),
                lt: "guidedSteps: identity", st: T("IntegrationDesigner.tsx", "IntegrationDesigner.tsx"), tx: T("Paso inicial de la vista guiada; si falta el nombre visible, se usa el identificador.", "Initial step of the guided view; if the visible name is missing, the identifier is used.") },
          tg: { lf: T("Elegir tecnología y modo de entrega", "Choose technology and delivery mode"), sf: T("1 tecnología ∧ 1 modo por perfil", "1 technology ∧ 1 mode per profile"), fio: T("Perfil con identidad → perfil con tecnología y modo", "Profile with identity → profile with technology and mode"),
                lt: "guidedSteps: target", st: T("IntegrationDesigner.tsx", "IntegrationDesigner.tsx") },
          cp: { lf: T("Acotar el alcance de la entrega", "Bound the delivery scope"), sf: T("características + documentos de entrada", "features + input documents"), fio: T("Tecnología elegida → características y artefactos seleccionados", "Chosen technology → selected features and artifacts"),
                lt: "guidedSteps: capabilities", st: T("IntegrationDesigner.tsx", "IntegrationDesigner.tsx") },
          mp: { lf: T("Enlazar el modelo con el proyecto anfitrión", "Link the model to the host project"), sf: T("mapeos modelo ↔ destino", "model ↔ target mappings"), fio: T("Alcance elegido → mapeos del perfil", "Chosen scope → profile mappings"),
                lt: "guidedSteps: mappings", st: T("IntegrationDesigner.tsx", "IntegrationDesigner.tsx") },
          vf: { lf: T("Contrastar y fijar el perfil", "Check and fix the profile"), sf: T("análisis de compatibilidad con persistencia", "compatibility analysis with persistence"), fio: T("Perfil completo → perfil guardado con diagnóstico", "Complete profile → saved profile with diagnosis"),
                lt: "guidedSteps: verify", st: T("IntegrationDesigner.tsx", "IntegrationDesigner.tsx") },
          ok: { lf: T("Admitir o devolver el perfil", "Admit or return the profile"), sf: T("sin errores → entrega; con errores → corregir", "no errors → delivery; errors → fix"), fio: T("Diagnóstico → avanzar a entrega o volver a corregir", "Diagnosis → move to delivery or go back to fix") },
          dl: { lf: T("Ejecutar acciones de entrega habilitadas", "Run the enabled delivery actions"), sf: T("compilar, verificar, previsualizar, aplicar", "compile, verify, preview, apply"), fio: T("Perfil válido y preparación → proyecto generado o cambios aplicados", "Valid profile and readiness → generated project or applied changes"),
                lt: "guidedSteps: delivery", st: T("IntegrationTechnologyJourney", "IntegrationTechnologyJourney"), tx: T("Cada botón se habilita según el estado de preparación; el panel recibe una propiedad opcional de preparación.", "Each button is enabled according to the readiness state; the panel receives an optional readiness property.") },
          res: { lf: T("Perfil persistido y entrega realizada", "Persisted profile and completed delivery"), sf: T("perfil guardado + resultado de la acción", "saved profile + action result"), fio: T("Acciones de entrega → perfil guardado y proyecto o cambios", "Delivery actions → saved profile and project or changes") }
        }
      },
      integration_author_views: {
        nf: T("Edición alternativa del perfil: formulario y estructura", "Alternative profile editing: form and structure"),
        df: T("El mismo perfil puede editarse en una pantalla completa de campos o navegando un árbol de nodos (perfil, destino, inyección, características, artefactos, mapeos) con panel de propiedades. Ambas vías convergen en analizar y guardar, y se puede seguir editando en cualquiera de las tres vistas.",
              "The same profile can be edited in a full-screen form or by navigating a tree of nodes (profile, target, injection, features, artifacts, mappings) with a properties panel. Both paths converge on analyse and save, and editing can continue in any of the three views."),
        nt: T("IntegrationDesigner.tsx (vistas FORM y STRUCTURE)", "IntegrationDesigner.tsx (FORM and STRUCTURE views)"),
        dt: T("FORM y STRUCTURE son vistas de IntegrationDesigner.tsx sobre el mismo perfil que la vista GUIDED. El detalle de ambas procede de documentación previa y no se reverificó.",
              "FORM and STRUCTURE are views of IntegrationDesigner.tsx over the same profile as the GUIDED view. Their detail comes from earlier documentation and was not re-verified."),
        nodes: {
          sw: { lf: T("Elegir el modo de edición", "Choose the editing mode"), sf: T("formulario ∨ estructura sobre un perfil", "form ∨ structure over one profile"), fio: T("Perfil abierto → vista de edición elegida", "Open profile → chosen editing view") },
          fm: { lf: T("Editar todos los campos a la vez", "Edit all fields at once"), sf: T("campos + informe por artefacto", "fields + per-artifact report"), fio: T("Perfil → campos modificados con diagnóstico por artefacto", "Profile → modified fields with per-artifact diagnosis") },
          tr: { lf: T("Seleccionar un componente del perfil", "Select a profile component"), sf: T("árbol: perfil, destino, inyección…", "tree: profile, target, injection…"), fio: T("Árbol del perfil → componente seleccionado", "Profile tree → selected component") },
          pn: { lf: T("Modificar las propiedades del componente", "Modify the component's properties"), sf: T("maestro/detalle del mismo perfil", "master/detail of the same profile"), fio: T("Componente seleccionado → propiedades actualizadas", "Selected component → updated properties") },
          pv: { lf: T("Consultar el delta previsto", "Consult the expected delta"), sf: T("ficheros que cambiarían", "files that would change"), fio: T("Perfil editado → lista de cambios previstos", "Edited profile → list of expected changes") },
          sv: { lf: T("Contrastar y persistir el perfil", "Check and persist the profile"), sf: T("análisis + guardado, repetible", "analysis + save, repeatable"), fio: T("Perfil editado → perfil analizado y guardado", "Edited profile → analysed and saved profile") },
          r: { lf: T("Perfil común a las tres vistas", "Profile shared by the three views"), sf: T("una sola fuente de edición", "a single editing source"), fio: T("Perfil guardado → disponible en guiado, formulario y estructura", "Saved profile → available in guided, form and structure views") }
        }
      }
    }
  });

  /* ===================================================== PUBLICATION ===================================================== */
  HM.lens({
    caps: {
      publication: {
        func: {
          title: T("Materialización y certificación de entregables del lenguaje", "Materialisation and certification of language deliverables"),
          summary: T("Tres conceptos coexisten con distinto rigor: la generación de código (un metamodelo, un modelo y un destino producen artefactos), la puerta de publicación verificada (cinco comprobaciones agregadas en un veredicto) y la distribución autónoma (un perfil de producto con 1 a 4 destinos produce artefactos planificados, validados, comprobados para producción y empaquetados). La generación sencilla y la distribución no pasan por la puerta; solo la vía verificada lo hace. El paquete resultante es siempre una fuente de lanzamiento o un conjunto de activos web, nunca un instalador nativo certificado.",
                     "Three concepts coexist with different rigour: code generation (one metamodel, one model and one target produce artifacts), the verified release gate (five checks aggregated into one verdict) and standalone distribution (a product profile with 1 to 4 targets produces artifacts that are planned, validated, checked for production and packaged). Simple generation and distribution do not go through the gate; only the verified path does. The resulting package is always a release source or a set of web assets, never a certified native installer."),
          inputs: [T("Un metamodelo, un modelo y un destino de generación", "One metamodel, one model and one generation target"), T("Perfil del producto: nombre, versión, canal, destinos", "Product profile: name, version, channel, targets"), T("Carpeta del editor ya construido", "Already built editor folder"), T("Opciones: asociaciones de ficheros, modo sin conexión, trazabilidad", "Options: file associations, offline mode, traceability")],
          outputs: [T("Artefactos de código con previsualización", "Code artifacts with preview"), T("Veredicto apto o no apto con detalle por comprobación", "Pass or fail verdict with per-check detail"), T("Informe de validación de fuentes y de preparación para producción", "Source validation report and production readiness report"), T("Paquete determinista con huella SHA-256", "Deterministic package with a SHA-256 fingerprint")],
          conditions: [T("Generar exige exactamente un metamodelo, un modelo y un destino", "Generating requires exactly one metamodel, one model and one target"), T("Sin nombre, versión, canal o al menos un destino el perfil se rechaza", "Without name, version, channel or at least one target the profile is rejected"), T("Empaquetar exige una validación válida; el paquete de lanzamiento exige preparación válida", "Packaging requires a valid validation; the release package requires valid readiness"), T("Preparación para producción es más estricta que validar fuentes", "Production readiness is stricter than validating sources"), T("La puerta no cubre seguridad ni el estado de validación del modelo", "The gate covers neither security nor the model's validation state")]
        },
        tech: {
          title: T("Generación de código, puerta de publicación y distribución autónoma sobre servicios REST", "Code generation, release gate and standalone distribution over REST services"),
          summary: T("ModelCodeGenerationService genera por metamodelId + modelId + targetId y HydraReleaseGateService.evaluate() agrega cinco comprobaciones (PACKAGE, SEMANTIC_MERGE, P2_MIRROR con licencia, ADAPTER_BENCH, PERFORMANCE). La distribución la orquesta StandaloneDistributionApplicationService sobre planner, validación, preparación para producción y packager, y se expone en POST /api/standalone-distributions/{plan,materialize,validate,release-readiness,package-release-source,package-deployable-web,package}. El frontend es StandaloneDistributionCenter.tsx, que solo tiene prueba de su cliente de API; generación y puerta proceden de documentación previa y no se reverificaron.",
                     "ModelCodeGenerationService generates by metamodelId + modelId + targetId and HydraReleaseGateService.evaluate() aggregates five checks (PACKAGE, SEMANTIC_MERGE, P2_MIRROR with license, ADAPTER_BENCH, PERFORMANCE). Distribution is orchestrated by StandaloneDistributionApplicationService over the planner, validation, production readiness and packager, and is exposed at POST /api/standalone-distributions/{plan,materialize,validate,release-readiness,package-release-source,package-deployable-web,package}. The frontend is StandaloneDistributionCenter.tsx, whose only test covers its API client; generation and the gate come from earlier documentation and were not re-verified.")
        },
        elements: {
          codegen: {
            nf: T("Generación de código (proyección del modelo a artefactos de un destino)", "Code generation (projection of the model into a target's artifacts)"),
            fm: T("Operación que combina exactamente un metamodelo, un modelo y un destino para producir cero o más artefactos. Admite espacio de nombres, nombre de artefacto, trazabilidad y características propias del destino; su ciclo es elegir, previsualizar y generar. No parte de una sesión textual y solo atraviesa la puerta de calidad en la vía verificada.",
                  "Operation that combines exactly one metamodel, one model and one target to produce zero or more artifacts. It accepts namespace, artifact name, traceability and target-specific features; its cycle is choose, preview and generate. It does not start from a text session and only crosses the quality gate on the verified path."),
            tm: T("ModelCodeGenerationService, invocado con metamodelId + modelId + targetId; la evidencia es heredada del paquete raíz y no está reverificada.", "ModelCodeGenerationService, invoked with metamodelId + modelId + targetId; the evidence is inherited from the root package and not re-verified.")
          },
          gate: {
            nf: T("Puerta de calidad de publicación (agregado de cinco comprobaciones)", "Release quality gate (aggregate of five checks)"),
            fm: T("Agregado que evalúa un paquete candidato frente a cinco comprobaciones obligatorias y devuelve un veredicto apto o no apto con detalle por comprobación. Un fallo bloquea la vía verificada pero no la generación sencilla. Excluye deliberadamente seguridad y el estado de validación del modelo.",
                  "Aggregate that evaluates a candidate package against five mandatory checks and returns a pass or fail verdict with per-check detail. A failure blocks the verified path but not simple generation. It deliberately excludes security and the model's validation state."),
            tm: T("HydraReleaseGateService.evaluate() con cinco comprobaciones; heredado de documentación previa, no reverificado.", "HydraReleaseGateService.evaluate() with five checks; inherited from earlier documentation, not re-verified."),
            variants: [
              { k: "PACKAGE", fd: T("Comprueba que el paquete candidato se construye correctamente.", "Checks that the candidate package builds correctly.") },
              { k: "SEMANTIC_MERGE", fd: T("Comprueba que la fusión de cambios conserva el significado del modelo.", "Checks that merging changes preserves the model's meaning.") },
              { k: "P2_MIRROR", fd: T("Comprueba el espejo de dependencias y que la licencia es admisible.", "Checks the dependency mirror and that the license is admissible.") },
              { k: "ADAPTER_BENCH", fd: T("Ejecuta el banco de pruebas del adaptador sobre el candidato.", "Runs the adapter test bench over the candidate.") },
              { k: "PERFORMANCE", fd: T("Contrasta el candidato con el presupuesto de rendimiento.", "Compares the candidate against the performance budget.") }
            ]
          },
          dist_profile: {
            nf: T("Perfil de distribución (definición del producto repartido)", "Distribution profile (definition of the shared product)"),
            fm: T("Entidad obligatoria que describe el producto: nombre no vacío, versión (semántica si hay escritorio), canal de actualización, 1 a 4 destinos, modo sin conexión y asociaciones de ficheros normalizadas (minúsculas, con punto, sin repetidos, ordenadas). A partir de él se planifican los artefactos y se materializan; la carpeta del editor viaja con la petición, no con el perfil.",
                  "Mandatory entity describing the product: non-empty name, version (semantic if desktop is included), update channel, 1 to 4 targets, offline mode and normalised file associations (lower case, dotted, no duplicates, sorted). The artifacts are planned and materialised from it; the editor folder travels with the request, not with the profile."),
            tm: T("StandaloneDistributionProfile (productName, version, targets, offlineEnabled, fileAssociations, updateChannel) dentro de StandaloneDistributionRequest, que añade editorDirectory.", "StandaloneDistributionProfile (productName, version, targets, offlineEnabled, fileAssociations, updateChannel) inside StandaloneDistributionRequest, which adds editorDirectory.")
          },
          dist_target: {
            nf: T("Destino de distribución (plataforma de uso final)", "Distribution target (end-use platform)"),
            fm: T("Cada perfil elige entre uno y cuatro destinos. El destino web instalable produce manifiesto y trabajador sin conexión; cualquiera de los tres de escritorio produce un único proyecto de escritorio compartido, sin diferencias por sistema operativo. Por defecto se preseleccionan web y Windows.",
                  "Each profile chooses between one and four targets. The installable web target produces a manifest and offline worker; any of the three desktop targets produces a single shared desktop project, with no per-operating-system differences. Web and Windows are preselected by default."),
            tm: T("Enumeración StandaloneDistributionTarget; el planner emite los ficheros PWA y, una sola vez, los de Tauri.", "StandaloneDistributionTarget enum; the planner emits the PWA files and, only once, the Tauri ones."),
            variants: [
              { k: "PWA", fd: T("Aplicación web instalable con manifiesto y trabajador de servicio sin conexión.", "Installable web application with a manifest and an offline service worker.") },
              { k: "TAURI_WINDOWS", fd: T("Aplicación de escritorio para Windows; comparte proyecto con las demás de escritorio.", "Desktop application for Windows; shares its project with the other desktop ones.") },
              { k: "TAURI_MACOS", fd: T("Aplicación de escritorio para macOS; comparte proyecto con las demás de escritorio.", "Desktop application for macOS; shares its project with the other desktop ones.") },
              { k: "TAURI_LINUX", fd: T("Aplicación de escritorio para Linux; comparte proyecto con las demás de escritorio.", "Desktop application for Linux; shares its project with the other desktop ones.") }
            ]
          },
          artifacts: {
            nf: T("Artefactos de distribución planificados (plan de ficheros derivado)", "Planned distribution artifacts (derived file plan)"),
            fm: T("Conjunto derivado del perfil, ordenado por ruta relativa y segura: siempre una descripción de la distribución, más los ficheros web si hay destino web y los de escritorio si hay alguno. Es el contenido que luego se materializa y se valida; no se edita directamente.",
                  "Set derived from the profile, ordered by safe relative path: always a distribution description, plus the web files if there is a web target and the desktop ones if there is any. It is the content that is later materialised and validated; it is not edited directly."),
            tm: T("StandaloneDistributionArtifact (path, mediaType, content) generado por StandaloneDistributionPlanner: distribution/hydra-distribution.json, public/manifest.webmanifest, public/service-worker.js y, en escritorio, src-tauri/tauri.conf.json, Cargo.toml, build.rs, src/main.rs y capabilities/default.json.",
                  "StandaloneDistributionArtifact (path, mediaType, content) produced by StandaloneDistributionPlanner: distribution/hydra-distribution.json, public/manifest.webmanifest, public/service-worker.js and, for desktop, src-tauri/tauri.conf.json, Cargo.toml, build.rs, src/main.rs and capabilities/default.json.")
          },
          validation_report: {
            nf: T("Informe de validación de fuentes (diagnóstico de lo materializado)", "Source validation report (diagnosis of what was materialised)"),
            fm: T("Resultado de contrastar las fuentes materializadas con el perfil: comprobaciones de la entrada de la aplicación, del producto, de cada destino y de la legibilidad de sus ficheros. Es válido si no contiene errores; los avisos informan sin bloquear. Es la condición previa del paquete normal.",
                  "Result of contrasting the materialised sources with the profile: checks of the application entry, the product, each target and the readability of their files. It is valid if it contains no errors; warnings inform without blocking. It is the precondition of the normal package."),
            tm: T("Informe de StandaloneDistributionValidationService con 13 códigos (APP_ENTRY, DIST_PRODUCT, PWA_*, TAURI_*, VALIDATED, MISSING_JSON, INVALID_JSON) y DiagnosticSeverity; se pide en POST /api/standalone-distributions/validate.",
                  "Report from StandaloneDistributionValidationService with 13 codes (APP_ENTRY, DIST_PRODUCT, PWA_*, TAURI_*, VALIDATED, MISSING_JSON, INVALID_JSON) and DiagnosticSeverity; requested via POST /api/standalone-distributions/validate."),
            variants: [
              { k: "ERROR", fd: T("Hallazgo que invalida el informe e impide empaquetar.", "Finding that invalidates the report and prevents packaging.") },
              { k: "WARNING", fd: T("Hallazgo que se comunica pero deja el informe válido.", "Finding that is communicated but leaves the report valid.") },
              { k: "INFO", fd: T("Dato informativo sin efecto sobre la validez.", "Informational datum with no effect on validity.") }
            ]
          },
          readiness_report: {
            nf: T("Preparación para producción (aptitud de despliegue estático)", "Production readiness (static deployment fitness)"),
            fm: T("Evaluación distinta y más estricta que la validación de fuentes: exige la validación válida y además la existencia del editor ya construido, el manifiesto y trabajador web (con caché de la página de entrada si hay modo sin conexión) y los ficheros del proyecto de escritorio. Declara una calificación de despliegue estático y que ningún instalador nativo está certificado.",
                  "Evaluation distinct from and stricter than source validation: it requires a valid validation and also the existence of the built editor, the web manifest and worker (caching the entry page if offline mode is on) and the desktop project files. It declares a static deployment qualification and that no native installer is certified."),
            tm: T("StandaloneProductionReadinessService con comprobaciones BUILT_ENTRY, BUILT_MANIFEST, BUILT_WORKER, OFFLINE_WORKER, OFFLINE_ENTRY y TAURI_*; metadatos de calificación STATIC_DEPLOYMENT_READINESS y nativeInstallerCertified=false; se pide en POST /api/standalone-distributions/release-readiness.",
                  "StandaloneProductionReadinessService with BUILT_ENTRY, BUILT_MANIFEST, BUILT_WORKER, OFFLINE_WORKER, OFFLINE_ENTRY and TAURI_* checks; qualification metadata STATIC_DEPLOYMENT_READINESS and nativeInstallerCertified=false; requested via POST /api/standalone-distributions/release-readiness.")
          },
          package: {
            nf: T("Paquete determinista con huella (entregable verificable)", "Deterministic package with fingerprint (verifiable deliverable)"),
            fm: T("Archivo reproducible del editor cuya huella permite comprobar que no se alteró. Existen tres niveles: el paquete del editor (tras validación válida), el de fuentes de lanzamiento (tras preparación válida, fuera de la carpeta del editor) y el de activos web desplegables (solo con destino web y preparación válida). Ninguno es un instalador nativo.",
                  "Reproducible archive of the editor whose fingerprint lets you check it was not altered. There are three levels: the editor package (after a valid validation), the release-source package (after valid readiness, outside the editor folder) and the deployable web assets package (only with a web target and valid readiness). None is a native installer."),
            tm: T("StandaloneDistributionPackager (fileName, sha256, content; cabeceras X-HYDRA-Distribution-SHA256 y X-HYDRA-Package-Level) y StandaloneWebDeploymentPackager, que rechaza enlaces simbólicos, nombres reservados o secretos y límites de tamaño.", "StandaloneDistributionPackager (fileName, sha256, content; X-HYDRA-Distribution-SHA256 and X-HYDRA-Package-Level headers) and StandaloneWebDeploymentPackager, which rejects symlinks, reserved or secret names and size limits."),
            variants: [
              { fd: T("Archivo del editor con su huella; solo si la validación de fuentes es válida.", "Archive of the editor with its fingerprint; only if source validation is valid."), td: T("POST /api/standalone-distributions/package; rechaza validación inválida.", "POST /api/standalone-distributions/package; rejects an invalid validation.") },
              { k: "RELEASE_SOURCE_NOT_NATIVE_INSTALLER", fd: T("Fuentes de lanzamiento reproducibles generadas fuera de la carpeta del editor; exige preparación válida y declara que no es instalador.", "Reproducible release sources created outside the editor folder; requires valid readiness and declares it is not an installer."), td: T("POST /api/standalone-distributions/package-release-source.", "POST /api/standalone-distributions/package-release-source.") },
              { k: "DEPLOYABLE_WEB_ASSETS", fd: T("Activos web ya construidos con huella y descriptor de nivel; solo con destino web y preparación válida.", "Already built web assets with fingerprint and level descriptor; only with a web target and valid readiness."), td: T("POST /api/standalone-distributions/package-deployable-web; StandaloneWebDeploymentPackager (no seguido en Git); nativeInstallerCertified=false.", "POST /api/standalone-distributions/package-deployable-web; StandaloneWebDeploymentPackager (untracked in Git); nativeInstallerCertified=false.") }
            ]
          }
        }
      }
    },
    procs: {
      publication_generate_publish: {
        nf: T("Generar y, opcionalmente, certificar la publicación", "Generate and optionally certify the release"),
        df: T("Una generación puede cerrarse por dos vías: la sencilla, que registra el resultado sin más, o la verificada, que lo somete a cinco comprobaciones. En la verificada, cualquier fallo produce un veredicto no apto con detalle y la generación sencilla sigue disponible; en ambos casos un resultado aceptado se registra como proyecto generado.",
              "A generation can close on two paths: the simple one, which registers the result as is, or the verified one, which submits it to five checks. On the verified path any failure yields a fail verdict with detail and simple generation remains available; in both cases an accepted result is registered as a generated project."),
        nt: T("ModelCodeGenerationService → HydraReleaseGateService.evaluate()", "ModelCodeGenerationService → HydraReleaseGateService.evaluate()"),
        dt: T("La generación usa ModelCodeGenerationService con metamodelId + modelId + targetId; la vía verificada añade HydraReleaseGateService.evaluate() con cinco comprobaciones. Heredado de documentación previa y no reverificado en esta pasada.",
              "Generation uses ModelCodeGenerationService with metamodelId + modelId + targetId; the verified path adds HydraReleaseGateService.evaluate() with five checks. Inherited from earlier documentation and not re-verified in this pass."),
        nodes: {
          t: { lf: T("Fijar la terna metamodelo-modelo-destino", "Fix the metamodel-model-target triple"), sf: T("1 metamodelo + 1 modelo + 1 destino", "1 metamodel + 1 model + 1 target"), fio: T("Metamodelo, modelo y destino → petición de generación completa", "Metamodel, model and target → complete generation request"),
                lt: "metamodelId + modelId + targetId", st: T("ModelCodeGenerationService", "ModelCodeGenerationService") },
          pv: { lf: T("Inspeccionar el resultado sin persistirlo", "Inspect the result without persisting it"), sf: T("proyección en modo lectura", "read-only projection"), fio: T("Petición de generación → código visible sin descarga", "Generation request → code visible without download") },
          g: { lf: T("Proyectar el modelo a artefactos del destino", "Project the model into target artifacts"), sf: T("modelo → artefactos", "model → artifacts"), fio: T("Petición de generación → artefactos del destino", "Generation request → target artifacts"),
               lt: "ModelCodeGenerationService", st: T("generación heredada", "inherited generation") },
          v: { lf: T("Elegir el nivel de rigor de la publicación", "Choose the release's level of rigour"), sf: T("sencilla ∨ verificada", "simple ∨ verified"), fio: T("Artefactos generados → vía sencilla o vía con puerta", "Generated artifacts → simple path or gated path") },
          gt: { lf: T("Agregar cinco comprobaciones en un veredicto", "Aggregate five checks into a verdict"), sf: T("paquete, fusión, espejo, banco, rendimiento", "package, merge, mirror, bench, performance"), fio: T("Paquete candidato → resultados de cinco comprobaciones", "Candidate package → results of five checks"),
                lt: "HydraReleaseGateService.evaluate", st: T("PACKAGE … PERFORMANCE", "PACKAGE … PERFORMANCE"), tx: T("Evalúa PACKAGE, SEMANTIC_MERGE, P2_MIRROR con licencia, ADAPTER_BENCH y PERFORMANCE; no incluye seguridad ni el estado de validación del modelo. Heredado, no reverificado.", "Evaluates PACKAGE, SEMANTIC_MERGE, P2_MIRROR with license, ADAPTER_BENCH and PERFORMANCE; it includes neither security nor the model's validation state. Inherited, not re-verified.") },
          ok: { lf: T("Exigir la superación de todas las comprobaciones", "Require all checks to pass"), sf: T("todas pasan → apto; alguna falla → no apto", "all pass → pass; any fails → fail"), fio: T("Resultados por comprobación → veredicto apto o no apto", "Per-check results → pass or fail verdict") },
          cert: { lf: T("Certificar la publicación verificada", "Certify the verified release"), sf: T("veredicto positivo", "positive verdict"), fio: T("Cinco comprobaciones superadas → publicación certificada", "Five checks passed → certified release") },
          blk: { lf: T("Rechazar la vía verificada con diagnóstico", "Reject the verified path with a diagnosis"), sf: T("detalle por comprobación fallida", "detail per failed check"), fio: T("Alguna comprobación fallida → veredicto no apto con causas", "Some check failed → fail verdict with causes") },
          reg: { lf: T("Dar identidad de proyecto al resultado", "Give project identity to the result"), sf: T("artefactos → proyecto registrable", "artifacts → registrable project"), fio: T("Artefactos aceptados → proyecto generado listo para su ciclo de vida", "Accepted artifacts → generated project ready for its lifecycle") }
        }
      },
      publication_standalone_distribution: {
        nf: T("Del perfil de producto al paquete verificable", "From product profile to verifiable package"),
        df: T("El perfil define los destinos y de él se derivan artefactos planificados que se materializan y validan. Con validación válida se puede obtener el paquete normal; de forma opcional, una comprobación de preparación para producción, si es válida, habilita el paquete de fuentes de lanzamiento. Con validación inválida se vuelve a corregir el perfil.",
              "The profile defines the targets and planned artifacts are derived from it, then materialised and validated. With a valid validation the normal package can be obtained; optionally, a production readiness check, if valid, enables the release-source package. With an invalid validation you go back to fix the profile."),
        nt: T("StandaloneDistributionApplicationService: plan → materialize → validate → package", "StandaloneDistributionApplicationService: plan → materialize → validate → package"),
        dt: T("StandaloneDistributionApplicationService orquesta StandaloneDistributionPlanner, la materialización seguida de StandaloneDistributionValidationService, StandaloneProductionReadinessService y los packagers; se expone en POST /api/standalone-distributions/{plan,materialize,validate,release-readiness,package,package-release-source}. Los detalles internos de validación y materialización no se verificaron.",
              "StandaloneDistributionApplicationService orchestrates StandaloneDistributionPlanner, materialisation followed by StandaloneDistributionValidationService, StandaloneProductionReadinessService and the packagers; it is exposed at POST /api/standalone-distributions/{plan,materialize,validate,release-readiness,package,package-release-source}. The internals of validation and materialisation were not verified."),
        nodes: {
          pr: { lf: T("Definir el producto y sus destinos", "Define the product and its targets"), sf: T("nombre, versión, canal, 1..4 destinos", "name, version, channel, 1..4 targets"), fio: T("Datos de producto y carpeta del editor → perfil de distribución válido", "Product data and editor folder → valid distribution profile"),
                lt: "StandaloneDistributionProfile", st: T("StandaloneDistributionRequest", "StandaloneDistributionRequest"), tx: T("El perfil exige productName no vacío, versión, canal de actualización y destinos; la petición añade editorDirectory.", "The profile requires a non-empty productName, version, update channel and targets; the request adds editorDirectory.") },
          pl: { lf: T("Derivar el plan de ficheros del perfil", "Derive the file plan from the profile"), sf: T("destinos → artefactos ordenados por ruta", "targets → artifacts ordered by path"), fio: T("Perfil → lista ordenada de artefactos planificados", "Profile → ordered list of planned artifacts"),
                lt: "StandaloneDistributionPlanner", st: T("POST /api/standalone-distributions/plan", "POST /api/standalone-distributions/plan"), tx: T("Siempre emite la descripción de la distribución, los ficheros PWA con destino web y un único proyecto Tauri si hay algún destino de escritorio; el resultado se ordena por ruta.", "It always emits the distribution description, the PWA files with a web target and a single Tauri project if there is any desktop target; the result is sorted by path.") },
          mt: { lf: T("Escribir el plan sobre el editor", "Write the plan onto the editor"), sf: T("plan → carpeta con distribución materializada", "plan → folder with materialised distribution"), fio: T("Artefactos planificados → ficheros escritos y validados", "Planned artifacts → written and validated files"),
                lt: "StandaloneDistributionApplicationService", st: T("POST /api/standalone-distributions/materialize", "POST /api/standalone-distributions/materialize"), tx: T("Materializa y a continuación valida; el detalle del materializador no se verificó.", "Materialises and then validates; the materialiser's detail was not verified.") },
          va: { lf: T("Contrastar las fuentes con el perfil", "Check the sources against the profile"), sf: T("fuentes → diagnósticos por gravedad", "sources → diagnoses by severity"), fio: T("Ficheros materializados → informe de validación", "Materialised files → validation report"),
                lt: "StandaloneDistributionValidationService", st: T("POST /api/standalone-distributions/validate", "POST /api/standalone-distributions/validate"), tx: T("Emite 13 códigos de diagnóstico (APP_ENTRY, DIST_PRODUCT, PWA_*, TAURI_*, VALIDATED, MISSING_JSON, INVALID_JSON).", "Emits 13 diagnostic codes (APP_ENTRY, DIST_PRODUCT, PWA_*, TAURI_*, VALIDATED, MISSING_JSON, INVALID_JSON).") },
          vk: { lf: T("Admitir solo fuentes sin errores", "Admit only error-free sources"), sf: T("sin errores → válida; con errores → corregir", "no errors → valid; errors → fix"), fio: T("Informe de validación → continuar o devolver al perfil", "Validation report → continue or return to the profile") },
          rd: { lf: T("Evaluar la aptitud de despliegue", "Evaluate deployment fitness"), sf: T("validación + editor construido", "validation + built editor"), fio: T("Validación válida y editor construido → informe de preparación", "Valid validation and built editor → readiness report"),
                lt: "StandaloneProductionReadinessService", st: T("POST /api/standalone-distributions/release-readiness", "POST /api/standalone-distributions/release-readiness"), tx: T("Comprueba BUILT_ENTRY, BUILT_MANIFEST, BUILT_WORKER, OFFLINE_WORKER, OFFLINE_ENTRY y TAURI_*; declara STATIC_DEPLOYMENT_READINESS y nativeInstallerCertified=false.", "Checks BUILT_ENTRY, BUILT_MANIFEST, BUILT_WORKER, OFFLINE_WORKER, OFFLINE_ENTRY and TAURI_*; declares STATIC_DEPLOYMENT_READINESS and nativeInstallerCertified=false.") },
          rk: { lf: T("Habilitar el nivel de lanzamiento", "Enable the release level"), sf: T("preparación válida → fuentes de lanzamiento", "valid readiness → release sources"), fio: T("Informe de preparación → permitir o impedir el paquete de lanzamiento", "Readiness report → allow or prevent the release package") },
          pk: { lf: T("Empaquetar de forma reproducible", "Package reproducibly"), sf: T("editor validado → archivo con huella", "validated editor → archive with fingerprint"), fio: T("Editor validado → archivo determinista y huella SHA-256", "Validated editor → deterministic archive and SHA-256 fingerprint"),
                lt: "StandaloneDistributionPackager", st: T("POST /api/standalone-distributions/package", "POST /api/standalone-distributions/package"), tx: T("Genera un ZIP determinista y rechaza validación inválida, destino dentro de la carpeta, enlaces simbólicos, ficheros sensibles, demasiadas entradas o tamaño excesivo.", "Produces a deterministic ZIP and rejects an invalid validation, a destination inside the folder, symbolic links, sensitive files, too many entries or excessive size.") },
          pr2: { lf: T("Empaquetar las fuentes de lanzamiento", "Package the release sources"), sf: T("preparación válida → fuentes fuera del editor", "valid readiness → sources outside the editor"), fio: T("Preparación válida → archivo de fuentes de lanzamiento", "Valid readiness → release-source archive"),
                 lt: "package-release-source", st: T("POST /api/standalone-distributions/package-release-source", "POST /api/standalone-distributions/package-release-source"), tx: T("Nivel de paquete RELEASE_SOURCE_NOT_NATIVE_INSTALLER; el archivo se crea fuera de la carpeta del editor.", "Package level RELEASE_SOURCE_NOT_NATIVE_INSTALLER; the archive is created outside the editor folder.") },
          zip: { lf: T("Entregable con integridad comprobable", "Deliverable with checkable integrity"), sf: T("archivo + huella; no es instalador", "archive + fingerprint; not an installer"), fio: T("Paquete generado → archivo repartible con su huella", "Generated package → shareable archive with its fingerprint"),
                 lt: "X-HYDRA-Distribution-SHA256", st: T("cabecera de huella de la respuesta", "fingerprint header of the response"), tx: T("La respuesta lleva las cabeceras X-HYDRA-Distribution-SHA256 y X-HYDRA-Package-Level.", "The response carries the X-HYDRA-Distribution-SHA256 and X-HYDRA-Package-Level headers.") }
        }
      },
      publication_author_codegen: {
        nf: T("Configuración de una generación desde el editor", "Configuring a generation from the editor"),
        df: T("Una configuración contextual de destino, nombre, espacio de nombres, trazabilidad y características opcionales se previsualiza y se itera hasta que el resultado conforma; entonces se materializa como descarga.",
              "A contextual configuration of target, name, namespace, traceability and optional features is previewed and iterated until the result is satisfactory; it is then materialised as a download."),
        nt: T("Diálogo modal → ModelCodeGenerationService", "Modal dialog → ModelCodeGenerationService"),
        dt: T("Diálogo modal contextual abierto desde el editor de modelos y el editor textual, que invoca la generación; descripción heredada de documentación previa y no reverificada.",
              "Contextual modal dialog opened from the model editor and the text editor, which invokes generation; description inherited from earlier documentation and not re-verified."),
        nodes: {
          t: { lf: T("Seleccionar el destino de generación", "Select the generation target"), sf: T("un destino por generación", "one target per generation"), fio: T("Modelo abierto → destino elegido", "Open model → chosen target") },
          ns: { lf: T("Nombrar el artefacto y su ámbito", "Name the artifact and its scope"), sf: T("nombre + espacio de nombres opcionales", "optional name + namespace"), fio: T("Destino elegido → identidad del código generado", "Chosen target → identity of the generated code") },
          tr: { lf: T("Activar trazabilidad y rasgos del destino", "Enable traceability and target traits"), sf: T("opciones propias de cada destino", "options specific to each target"), fio: T("Destino elegido → opciones de generación", "Chosen target → generation options") },
          pv: { lf: T("Proyectar sin persistir", "Project without persisting"), sf: T("vista previa de artefactos", "artifact preview"), fio: T("Configuración → código previsto visible", "Configuration → expected code visible") },
          ok: { lf: T("Aceptar la proyección o reconfigurar", "Accept the projection or reconfigure"), sf: T("conforme → generar; no → volver al destino", "satisfied → generate; no → back to target"), fio: T("Vista previa → generar o ajustar opciones", "Preview → generate or adjust options") },
          g: { lf: T("Materializar la proyección como descarga", "Materialise the projection as a download"), sf: T("artefactos → fichero descargado", "artifacts → downloaded file"), fio: T("Configuración aceptada → código generado entregado", "Accepted configuration → generated code delivered") }
        }
      },
      publication_author_release: {
        nf: T("Preparar y publicar una distribución en cinco pasos", "Prepare and publish a distribution in five steps"),
        df: T("Se declara la identidad, el artefacto con su plataforma y el canal con su firma; se valida con un resultado binario con diagnósticos y, si es válido, se revisa y se publica. Aparte puede consultarse si existe una versión más reciente por canal.",
              "Identity, artifact with platform, and channel with signing are declared; validation yields a binary result with diagnostics and, if valid, the release is reviewed and published. Separately, whether a newer version exists per channel can be consulted."),
        nt: T("Centro de Publicación (descripción heredada)", "Publication Center (inherited description)"),
        dt: T("Descripción heredada de la documentación previa; no se identificó en esta pasada el servicio concreto ni se reverificó. La validación se trata como un pase o fallo genérico con diagnósticos.",
              "Description inherited from earlier documentation; the concrete service was not identified in this pass and nothing was re-verified. Validation is treated as a generic pass or fail with diagnostics."),
        nodes: {
          id: { lf: T("Declarar la identidad de la distribución", "Declare the distribution's identity"), sf: T("nombre de la distribución", "distribution name"), fio: T("Nombre → distribución identificada", "Name → identified distribution") },
          ar: { lf: T("Fijar artefacto y plataforma objetivo", "Fix artifact and target platform"), sf: T("qué se publica × para qué plataforma", "what is published × for which platform"), fio: T("Distribución identificada → artefacto y plataforma definidos", "Identified distribution → defined artifact and platform") },
          ch: { lf: T("Definir canal de reparto y firma", "Define delivery channel and signing"), sf: T("canal + política de firma", "channel + signing policy"), fio: T("Artefacto y plataforma → canal y firma definidos", "Artifact and platform → defined channel and signing") },
          va: { lf: T("Contrastar la distribución completa", "Check the complete distribution"), sf: T("resultado binario + diagnósticos", "binary result + diagnoses"), fio: T("Distribución definida → válida o no, con diagnósticos", "Defined distribution → valid or not, with diagnoses") },
          ok: { lf: T("Condicionar la publicación a la validez", "Make publishing conditional on validity"), sf: T("válida → revisar; no → corregir plataforma", "valid → review; no → fix platform"), fio: T("Resultado de validación → revisar o volver a corregir", "Validation result → review or go back to fix") },
          rv: { lf: T("Confirmar el resumen y publicar", "Confirm the summary and publish"), sf: T("resumen → publicación", "summary → publication"), fio: T("Distribución válida → distribución publicada", "Valid distribution → published distribution") },
          up: { lf: T("Resolver la versión vigente por canal", "Resolve the current version per channel"), sf: T("consulta independiente del flujo", "query independent of the flow"), fio: T("Canal → versión más reciente conocida", "Channel → latest known version") },
          r: { lf: T("Distribución disponible en su canal", "Distribution available on its channel"), sf: T("estado final de la publicación", "final state of the publication"), fio: T("Revisión confirmada → distribución disponible en el canal", "Confirmed review → distribution available on the channel") }
        }
      },
      publication_author_distribution: {
        nf: T("Configurar la distribución: modos guiado y avanzado", "Configure distribution: guided and advanced modes"),
        df: T("Dos formas de rellenar el mismo perfil: una secuencia guiada (origen, identidad, destinos) o un formulario con todos los campos y las opciones avanzadas. Ambas confluyen en materializar y verificar; solo con validación válida se habilita el paquete, cuyo resultado incluye su huella.",
              "Two ways of filling in the same profile: a guided sequence (source, identity, targets) or a form with all fields and the advanced options. Both converge on materialising and verifying; only a valid validation enables the package, whose result includes its fingerprint."),
        nt: T("StandaloneDistributionCenter.tsx (GUIDED/ADVANCED) → /api/standalone-distributions", "StandaloneDistributionCenter.tsx (GUIDED/ADVANCED) → /api/standalone-distributions"),
        dt: T("El componente define los modos GUIDED y ADVANCED, la selección por defecto de destinos, los pasos guiados, los botones de acción y la descarga con huella SHA-256; solo su cliente de API tiene prueba, no el componente.",
              "The component defines the GUIDED and ADVANCED modes, the default target selection, the guided steps, the action buttons and the download with SHA-256 fingerprint; only its API client is tested, not the component."),
        nodes: {
          m: { lf: T("Seleccionar el modo de captura del perfil", "Select the profile capture mode"), sf: T("secuencia guiada ∨ formulario único", "guided sequence ∨ single form"), fio: T("Centro abierto → modo de captura elegido", "Centre opened → chosen capture mode") },
          so: { lf: T("Localizar el editor a distribuir", "Locate the editor to distribute"), sf: T("carpeta del editor en la petición", "editor folder in the request"), fio: T("Ubicación del editor → origen de la distribución", "Editor location → source of the distribution"),
                lt: "StandaloneDistributionCenter.tsx", st: T("pasos guiados", "guided steps"), tx: T("Primer paso de la secuencia guiada del centro de distribución.", "First step of the guided sequence of the distribution centre.") },
          idn: { lf: T("Identificar el producto y su versión", "Identify the product and its version"), sf: T("nombre no vacío + versión", "non-empty name + version"), fio: T("Nombre y versión → identidad del producto", "Name and version → product identity") },
          tg: { lf: T("Seleccionar plataformas de uso", "Select usage platforms"), sf: T("1..4 destinos; web y Windows por defecto", "1..4 targets; web and Windows by default"), fio: T("Identidad del producto → conjunto de destinos", "Product identity → set of targets"),
                lt: "StandaloneDistributionCenter.tsx", st: T("selección por defecto", "default selection"), tx: T("El componente preselecciona los destinos web y Windows.", "The component preselects the web and Windows targets.") },
          ad: { lf: T("Afinar asociaciones, canal y modo sin conexión", "Tune associations, channel and offline mode"), sf: T("campos opcionales y de canal", "optional and channel fields"), fio: T("Perfil base → perfil con opciones avanzadas", "Base profile → profile with advanced options"),
                lt: "StandaloneDistributionCenter.tsx", st: T("modo ADVANCED", "ADVANCED mode"), tx: T("Muestra juntos carpeta, producto, versión, preajuste y asociaciones de ficheros, canal y sin conexión.", "Shows folder, product, version, file-association preset and associations, channel and offline together.") },
          pv: { lf: T("Anticipar el plan de ficheros", "Anticipate the file plan"), sf: T("plan derivado sin escritura", "derived plan without writing"), fio: T("Perfil → artefactos que se crearían", "Profile → artifacts that would be created") },
          mv: { lf: T("Escribir el plan y contrastarlo", "Write the plan and check it"), sf: T("materialización + validación de fuentes", "materialisation + source validation"), fio: T("Perfil y plan → distribución materializada y su informe de validación", "Profile and plan → materialised distribution and its validation report"),
                lt: "POST /api/standalone-distributions/materialize", st: T("materializa y valida", "materialises and validates"), tx: T("El centro muestra el progreso mientras se materializa y se valida.", "The centre shows progress while materialising and validating.") },
          ok: { lf: T("Habilitar el paquete según la validez", "Enable the package by validity"), sf: T("válido → paquete; no → corregir identidad", "valid → package; no → fix identity"), fio: T("Informe de validación → paquete habilitado o corrección", "Validation report → package enabled or correction") },
          pk: { lf: T("Obtener el archivo reproducible", "Obtain the reproducible archive"), sf: T("editor validado → archivo + huella", "validated editor → archive + fingerprint"), fio: T("Distribución válida → archivo con huella SHA-256", "Valid distribution → archive with SHA-256 fingerprint"),
                lt: "POST /api/standalone-distributions/package", st: T("StandaloneDistributionPackager", "StandaloneDistributionPackager") },
          r: { lf: T("Archivo entregado con su huella", "Delivered archive with its fingerprint"), sf: T("integridad comprobable por el receptor", "integrity checkable by the recipient"), fio: T("Paquete generado → archivo descargado y huella visible", "Generated package → downloaded archive and visible fingerprint"),
               lt: "descarga con SHA-256", st: T("StandaloneDistributionCenter.tsx", "StandaloneDistributionCenter.tsx") }
        }
      }
    }
  });

  /* ===================================================== GENERATED PROJECTS ===================================================== */
  HM.lens({
    caps: {
      generated_projects: {
        func: {
          title: T("Ciclo de vida ejecutable de proyectos generados", "Executable lifecycle of generated projects"),
          summary: T("Un proyecto generado es una unidad registrada con identidad, un perfil de ejecución de entre cinco y una carpeta relativa, sobre la que se ejecutan de una a cuatro etapas ordenadas (generar, validar, abrir, lanzar). Cada invocación produce un informe con un resultado por etapa y termina: no existe un estado persistente de ejecución ni orquestación de reintentos o colas. Una simulación opcional planifica las etapas sin ejecutar nada.",
                     "A generated project is a registered unit with an identity, one of five runtime profiles and a relative folder, over which one to four ordered stages (generate, validate, open, launch) run. Each invocation produces a report with one result per stage and ends: there is no persistent running state nor orchestration of retries or queues. An optional dry run plans the stages without executing anything."),
          inputs: [T("Artefactos ya generados", "Already generated artifacts"), T("Identidad, perfil de ejecución y carpeta relativa", "Identity, runtime profile and relative folder"), T("Subconjunto de etapas a ejecutar", "Subset of stages to run"), T("Indicador de simulación", "Dry-run flag")],
          outputs: [T("Informe con un resultado por etapa", "Report with one result per stage"), T("Salida y diagnósticos por paso", "Output and diagnoses per step"), T("Proyecto abierto y, si se pide, lanzado", "Project opened and, if requested, launched")],
          conditions: [T("Cada etapa solo se habilita si el perfil de ejecución la soporta", "Each stage is enabled only if the runtime profile supports it"), T("Lanzar es la única etapa opcional del recorrido", "Launch is the only optional stage of the path"), T("En simulación las etapas quedan planificadas, no ejecutadas", "In a dry run the stages end up planned, not executed"), T("Validar comprueba el propio proyecto, no el modelo de dominio", "Validate checks the project itself, not the domain model"), T("Un fallo se muestra en el informe sin reintento automático", "A failure is shown in the report without automatic retry")]
        },
        tech: {
          title: T("Servicio de ciclo de vida con ejecutor de comandos con tiempo límite", "Lifecycle service with a timed command runner"),
          summary: T("GeneratedProjectLifecycleService resuelve cada etapa (GeneratedProjectStage: GENERATE, VALIDATE, OPEN, LAUNCH) a un comando de sistema operativo según el perfil de ejecución y lo lanza mediante ToolchainCommandRunner con tiempo límite, devolviendo un GeneratedProjectExecutionReport. Todo el contenido es heredado de la documentación previa (paquete generated.project) y no se reverificó; no se localizaron pruebas.",
                     "GeneratedProjectLifecycleService resolves each stage (GeneratedProjectStage: GENERATE, VALIDATE, OPEN, LAUNCH) to an operating-system command according to the runtime profile and launches it through ToolchainCommandRunner with a timeout, returning a GeneratedProjectExecutionReport. All content is inherited from earlier documentation (generated.project package) and was not re-verified; no tests were located.")
        },
        elements: {
          gp: {
            nf: T("Proyecto generado (unidad registrada de trabajo)", "Generated project (registered unit of work)"),
            fm: T("Entidad que da identidad a lo producido por una generación: identificador único, nombre visible, un perfil de ejecución y una carpeta relativa. Tiene un único perfil y de una a cuatro etapas ejecutables, de las que solo se habilitan las que el perfil soporta; tras registrarse recorre sus etapas.",
                  "Entity that gives identity to what a generation produced: a unique identifier, a visible name, one runtime profile and a relative folder. It has a single profile and one to four runnable stages, of which only those the profile supports are enabled; once registered it walks through its stages."),
            tm: T("Registro con id, name, perfil de ejecución y carpeta relativa, del paquete generated.project; heredado y sin reverificar.", "Record with id, name, runtime profile and relative folder from the generated.project package; inherited and not re-verified.")
          },
          profile: {
            nf: T("Perfil de ejecución (tecnología anfitriona del proyecto)", "Runtime profile (project's host technology)"),
            fm: T("Atributo fijo y único por proyecto que determina el comando de cada etapa y cuáles de ellas están habilitadas. Se elige al registrar y no cambia durante el ciclo de vida.",
                  "Fixed attribute, single per project, that determines each stage's command and which of them are enabled. It is chosen when registering and does not change during the lifecycle."),
            tm: T("Identificador de perfil resuelto por GeneratedProjectLifecycleService a un comando de sistema operativo por etapa.", "Profile identifier resolved by GeneratedProjectLifecycleService to an operating-system command per stage."),
            variants: [
              { k: "standalone-web", fd: T("Editor web independiente, ejecutable sin otra plataforma anfitriona.", "Independent web editor, runnable without another host platform.") },
              { k: "langium", fd: T("Lenguaje textual implementado con Langium.", "Textual language implemented with Langium.") },
              { k: "xtext", fd: T("Lenguaje textual implementado con Xtext.", "Textual language implemented with Xtext.") },
              { k: "glsp", fd: T("Editor gráfico web con arquitectura cliente-servidor.", "Web graphical editor with client-server architecture.") },
              { k: "besser-sdk", fd: T("Paquete de modelado BESSER ejecutado como proyecto.", "BESSER modelling package run as a project.") }
            ]
          },
          stg: {
            nf: T("Etapa del ciclo de vida (paso ejecutable con resultado)", "Lifecycle stage (executable step with a result)"),
            fm: T("Cada proyecto admite de una a cuatro etapas en orden fijo: generar, validar, abrir y lanzar, esta última opcional. Una etapa ejecuta un comando real y produce pasa o falla; en simulación solo queda planificada.",
                  "Each project admits one to four stages in a fixed order: generate, validate, open and launch, the latter optional. A stage runs a real command and yields passed or failed; in a dry run it is only planned."),
            tm: T("Enumeración GeneratedProjectStage (GENERATE, VALIDATE, OPEN, LAUNCH) en generated.project; el registro inicial no es una etapa del enumerado.", "GeneratedProjectStage enum (GENERATE, VALIDATE, OPEN, LAUNCH) in generated.project; the initial registration is not an enum stage."),
            variants: [
              { k: "GENERATE", fd: T("Produce o refresca los artefactos del proyecto antes de comprobarlos.", "Produces or refreshes the project artifacts before checking them.") },
              { k: "VALIDATE", fd: T("Comprueba que el proyecto se construye y su estructura es correcta; no evalúa reglas del modelo de dominio.", "Checks that the project builds and its structure is correct; it does not evaluate domain model rules.") },
              { k: "OPEN", fd: T("Deja el proyecto preparado para abrirse en su entorno de trabajo.", "Leaves the project ready to be opened in its working environment.") },
              { k: "LAUNCH", fd: T("Ejecuta el proyecto de forma acotada; es la única etapa prescindible.", "Runs the project in a bounded way; it is the only dispensable stage.") }
            ]
          },
          dry: {
            nf: T("Simulación (ensayo sin efectos)", "Dry run (rehearsal without effects)"),
            fm: T("Modalidad opcional de una ejecución, como máximo una por ejecución, que convierte las etapas elegidas en etapas planificadas y muestra qué comandos se lanzarían sin tocar nada. Precede a la ejecución real como ensayo.",
                  "Optional modality of a run, at most one per run, that turns the chosen stages into planned stages and shows which commands would be launched without touching anything. It precedes the real run as a rehearsal."),
            tm: T("Indicador booleano de simulación en la petición de ejecución del paquete generated.project.", "Boolean dry-run flag in the run request of the generated.project package.")
          },
          report: {
            nf: T("Informe de ejecución (resultado por etapa de una invocación)", "Execution report (per-stage result of one invocation)"),
            fm: T("Resultado agregado de una única invocación, con un resultado por etapa, la salida de comandos y los diagnósticos por paso. No tiene vida propia: se produce, se muestra y la ejecución termina, sin reintento automático.",
                  "Aggregate result of a single invocation, with one result per stage, the command output and per-step diagnoses. It has no life of its own: it is produced, shown and the run ends, with no automatic retry."),
            tm: T("GeneratedProjectExecutionReport con estado PASSED, FAILED, SKIPPED o PLANNED por etapa.", "GeneratedProjectExecutionReport with PASSED, FAILED, SKIPPED or PLANNED status per stage."),
            variants: [
              { k: "PASSED", fd: T("La etapa se ejecutó y terminó con éxito.", "The stage ran and finished successfully.") },
              { k: "FAILED", fd: T("La etapa se ejecutó y terminó con error; el detalle queda en el informe.", "The stage ran and ended in error; the detail stays in the report.") },
              { k: "SKIPPED", fd: T("La etapa no se ejecutó, por no elegirse o no estar soportada.", "The stage did not run, because it was not chosen or not supported.") },
              { k: "PLANNED", fd: T("La etapa se limitó a planificarse por tratarse de una simulación.", "The stage was only planned because it was a dry run.") }
            ]
          }
        }
      }
    },
    procs: {
      generated_projects_lifecycle: {
        nf: T("Recorrido de etapas de un proyecto registrado", "Stage pass of a registered project"),
        df: T("Tras registrarse, el proyecto puede ensayarse en simulación (solo planifica) o ejecutar sus etapas en orden: generar, validar y abrir, con lanzar como cierre opcional. Cualquier resultado, también un fallo, termina en un informe por etapa; no hay reintento automático.",
              "Once registered, the project can be rehearsed in a dry run (which only plans) or run its stages in order: generate, validate and open, with launch as an optional close. Any outcome, including a failure, ends in a per-stage report; there is no automatic retry."),
        nt: T("GeneratedProjectLifecycleService → ToolchainCommandRunner", "GeneratedProjectLifecycleService → ToolchainCommandRunner"),
        dt: T("El servicio resuelve cada GeneratedProjectStage a un comando de sistema operativo según el perfil y lo ejecuta con ToolchainCommandRunner con tiempo límite, componiendo un GeneratedProjectExecutionReport. Heredado de documentación previa y no reverificado.",
              "The service resolves each GeneratedProjectStage to an operating-system command by profile and runs it with ToolchainCommandRunner with a timeout, composing a GeneratedProjectExecutionReport. Inherited from earlier documentation and not re-verified."),
        nodes: {
          rg: { lf: T("Dar identidad y perfil al proyecto", "Give identity and profile to the project"), sf: T("id + nombre + perfil + carpeta relativa", "id + name + profile + relative folder"), fio: T("Artefactos generados → proyecto registrado con perfil de ejecución", "Generated artifacts → registered project with a runtime profile") },
          sim: { lf: T("Escoger entre ensayo y ejecución real", "Choose between rehearsal and real run"), sf: T("simulación → solo planificar", "dry run → plan only"), fio: T("Proyecto registrado → etapas planificadas o ejecutadas", "Registered project → planned or executed stages") },
          ge: { lf: T("Refrescar los artefactos del proyecto", "Refresh the project artifacts"), sf: T("etapa GENERATE con comando externo", "GENERATE stage with external command"), fio: T("Proyecto registrado → artefactos actualizados", "Registered project → updated artifacts"),
                lt: "GeneratedProjectStage.GENERATE", st: T("comando del perfil, con tiempo límite", "profile command, with timeout") },
          va: { lf: T("Comprobar construcción y estructura", "Check build and structure"), sf: T("proyecto, no modelo de dominio", "project, not domain model"), fio: T("Artefactos actualizados → resultado pasa o falla de la validación", "Updated artifacts → pass or fail result of validation"),
                lt: "GeneratedProjectStage.VALIDATE", st: T("comando del perfil, con tiempo límite", "profile command, with timeout") },
          op: { lf: T("Preparar el proyecto para su entorno", "Prepare the project for its environment"), sf: T("etapa OPEN", "OPEN stage"), fio: T("Proyecto validado → proyecto abierto", "Validated project → opened project"),
                lt: "GeneratedProjectStage.OPEN", st: T("comando del perfil, con tiempo límite", "profile command, with timeout") },
          la: { lf: T("Ejecutar el proyecto de forma acotada", "Run the project in a bounded way"), sf: T("única etapa opcional", "the only optional stage"), fio: T("Proyecto abierto → ejecución acotada y su resultado", "Opened project → bounded execution and its result"),
                lt: "GeneratedProjectStage.LAUNCH", st: T("comando del perfil, con tiempo límite", "profile command, with timeout") },
          ok: { lf: T("Detectar fallos en el recorrido", "Detect failures in the pass"), sf: T("fallo → informe; sin reintento", "failure → report; no retry"), fio: T("Resultados por etapa → continuar o cerrar con el fallo en el informe", "Per-stage results → continue or close with the failure in the report") },
          rp: { lf: T("Consolidar el resultado por etapa", "Consolidate the per-stage result"), sf: T("PASSED / FAILED / SKIPPED / PLANNED", "PASSED / FAILED / SKIPPED / PLANNED"), fio: T("Resultados de comandos → informe con un estado por etapa", "Command results → report with one status per stage"),
                lt: "GeneratedProjectExecutionReport", st: T("estado por etapa", "per-stage status") }
        }
      },
      generated_projects_author_wizard: {
        nf: T("Configuración guiada de una ejecución en cinco pasos", "Guided configuration of a run in five steps"),
        df: T("Se registra el proyecto, se elige sobre cuál actuar y si se ensaya, se seleccionan las etapas admitidas por su perfil, se previsualiza la simulación y se ejecuta. Un fallo permite reajustar las etapas y repetir; el éxito entrega el informe.",
              "The project is registered, the one to act on is chosen along with whether to rehearse, the stages admitted by its profile are selected, the simulation is previewed and the run is executed. A failure lets you readjust the stages and repeat; success delivers the report."),
        nt: T("Asistente generated-project-center (descripción heredada)", "generated-project-center wizard (inherited description)"),
        dt: T("Descripción heredada del frontend generated-project-center, sin reverificar en esta pasada; las casillas de etapa se habilitan según el perfil de ejecución y no hay reintento automático ni cola de trabajos visible.",
              "Description inherited from the generated-project-center frontend, not re-verified in this pass; the stage checkboxes are enabled according to the runtime profile and there is no automatic retry or visible job queue."),
        nodes: {
          rg: { lf: T("Definir la identidad del proyecto", "Define the project's identity"), sf: T("id, nombre, perfil, carpeta relativa", "id, name, profile, relative folder"), fio: T("Datos de identidad → proyecto registrado", "Identity data → registered project") },
          pj: { lf: T("Escoger el proyecto y la modalidad", "Pick the project and modality"), sf: T("proyecto × simulación sí/no", "project × dry run yes/no"), fio: T("Proyectos registrados → proyecto y modalidad seleccionados", "Registered projects → selected project and modality") },
          st: { lf: T("Seleccionar las etapas admitidas", "Select the admitted stages"), sf: T("subconjunto de GENERATE…LAUNCH", "subset of GENERATE…LAUNCH"), fio: T("Perfil del proyecto → etapas habilitadas y elegidas", "Project profile → enabled and chosen stages") },
          pv: { lf: T("Anticipar los comandos que se lanzarían", "Anticipate the commands that would run"), sf: T("etapas planificadas, sin efectos", "planned stages, no effects"), fio: T("Etapas elegidas → comandos previstos", "Chosen stages → expected commands") },
          ru: { lf: T("Lanzar las etapas elegidas", "Launch the chosen stages"), sf: T("una invocación → un informe", "one invocation → one report"), fio: T("Etapas elegidas → resultados de las etapas", "Chosen stages → stage results") },
          ok: { lf: T("Decidir entre reajustar o cerrar", "Decide between readjusting or closing"), sf: T("fallo → reajustar etapas", "failure → readjust stages"), fio: T("Resultados → informe final o nuevo ajuste de etapas", "Results → final report or a new stage adjustment") },
          rp: { lf: T("Informe de la invocación", "Invocation report"), sf: T("resultado por etapa y por paso", "result per stage and per step"), fio: T("Resultados y salida de comandos → insignias, salida y diagnósticos", "Results and command output → badges, output and diagnoses") }
        }
      }
    }
  });
})();
