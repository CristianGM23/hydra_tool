/* HYDRA portal - AUDIENCE LENS (platform-b): external_repositories, portability, persistence, audit, security.
   Overlay for the FUNCTIONAL and TECHNICAL projections. Technical statements are grounded only in hydra-model-platform.js. */
(function(){
  "use strict";
  var HM = window.HM, T = HM.T;

  HM.lens({
    caps: {
      /* ------------------------------------------------------------------ external_repositories */
      external_repositories: {
        func: {
          title: T("Conectores de almacenamiento remoto y sincronización de proyectos", "Remote storage connectors and project synchronization"),
          summary: T("Un perfil de conexión liga un proveedor (uno de seis) con un destino y, una vez autenticado, admite dos modos de uso excluyentes por naturaleza del proveedor: operaciones Git completas (solo en los tres proveedores Git) o transferencia de ficheros por ruta exacta (en los seis). Un conflicto de fusión abre una resolución explícita de cuatro vistas y no se cierra nunca de forma implícita. Importar desde un remoto restaura ficheros y no activa la recuperación semántica.",
                     "A connection profile binds a provider (one of six) to a destination and, once authenticated, supports two modes depending on the nature of the provider: full Git operations (only the three Git providers) or file transfer by exact path (all six). A merge conflict opens an explicit four-view resolution and is never closed implicitly. Importing from a remote restores files and does not activate semantic recovery."),
          inputs: [T("Elección de proveedor y datos de destino propios de ese proveedor", "Provider choice and destination data specific to that provider"), T("Consentimiento del titular de la cuenta ante el proveedor", "Consent from the account holder at the provider"), T("Operación Git solicitada con sus parámetros (rama, mensaje, estrategia de pull)", "Requested Git operation with its parameters (branch, message, pull strategy)"), T("Proyecto completo o capa individual a transferir", "Whole project or single layer to transfer")],
          outputs: [T("Conexión autenticada y reutilizable asociada al perfil", "Authenticated, reusable connection bound to the profile"), T("Estado del repositorio: árbol de ramas local y remoto", "Repository state: local and remote branch tree"), T("Fichero exportado al proveedor o contenido restaurado en el proyecto", "File exported to the provider or content restored into the project"), T("Resolución de conflicto preparada", "Staged conflict resolution")],
          conditions: [T("Las operaciones Git solo existen para GitHub, GitLab y Git genérico; en los otros tres se rechazan", "Git operations exist only for GitHub, GitLab and generic Git; they are rejected for the other three"), T("Sin clave SSH, sin token personal manual, sin force-push ni reset arbitrario", "No SSH key, no manual personal token, no force-push or arbitrary reset"), T("La credencial nunca sale del servidor hacia el cliente", "The credential never leaves the server towards the client"), T("En los proveedores solo de ficheros no hay exploración de carpetas: solo rutas exactas con versión esperada", "With file-only providers there is no folder browsing: exact paths only, with an expected version")]
        },
        tech: {
          title: T("Autenticación remota, espacio Git con ejecutor aislado y transferencia de proyecto", "Remote authentication, Git workspace with sandboxed runner and project transfer"),
          summary: T("Se realiza con RemoteAuthService (OAuth con código y PKCE, o Device Code), RemoteGitWorkspaceService (operaciones RemoteGitOperation siempre mediante un ejecutor sin shell) y RemoteProjectTransferService, expuestos bajo /api/remote-auth/*, /api/remote-git/* y /api/remote-storage/* (RemoteStorageController). El tipo de proveedor es el enum RemoteStorageProviderType de 6 valores. Los tokens de acceso y refresco y el secreto de cliente no se devuelven al navegador.",
                     "It is built on RemoteAuthService (OAuth with code and PKCE, or Device Code), RemoteGitWorkspaceService (RemoteGitOperation operations always through a shell-less runner) and RemoteProjectTransferService, exposed under /api/remote-auth/*, /api/remote-git/* and /api/remote-storage/* (RemoteStorageController). The provider type is the 6-value enum RemoteStorageProviderType. Access and refresh tokens and the client secret are never returned to the browser.")
        },
        elements: {
          provider: {
            nf: T("Proveedor remoto (tipo de servicio externo)", "Remote provider (external service type)"),
            fm: T("Clasificador cerrado de seis valores que determina la familia de capacidades del perfil: tres proveedores con semántica Git y tres de solo ficheros. Cada perfil referencia exactamente un proveedor y no puede cambiar de familia.",
                  "Closed classifier of six values that determines the profile's capability family: three providers with Git semantics and three file-only ones. Each profile references exactly one provider and cannot switch family."),
            tm: T("Enum RemoteStorageProviderType con 6 valores.", "Enum RemoteStorageProviderType with 6 values."),
            variants: [
              { fd: T("Familia Git; el destino se expresa como propietario y repositorio.", "Git family; the destination is expressed as owner and repository.") },
              { fd: T("Familia Git; el destino se expresa con el identificador de proyecto.", "Git family; the destination is expressed with the project identifier.") },
              { fd: T("Familia Git sobre un servidor arbitrario; destino por URL y usuario, con credencial gestionada en el servidor.", "Git family over an arbitrary server; destination by URL and user, with a server-managed credential.") },
              { fd: T("Familia de ficheros; destino por unidad y carpeta; sin semántica de ramas.", "File family; destination by drive and folder; no branch semantics.") },
              { fd: T("Familia de ficheros; destino por unidad y carpeta; sin semántica de ramas.", "File family; destination by drive and folder; no branch semantics.") },
              { fd: T("Familia de ficheros mínima; solo requiere los datos de identidad básicos.", "Minimal file family; only the basic identity data is required.") }
            ]
          },
          profile: {
            nf: T("Perfil de conexión (identidad y destino persistentes)", "Connection profile (persistent identity and destination)"),
            fm: T("Registro nominal que combina un proveedor, el destino específico y parámetros opcionales de compatibilidad. Existe en estado sin conectar hasta que se asocia una identidad y pasa a conectado; admite guardar, probar y eliminar, con cardinalidad de una conexión activa como máximo.",
                  "Named record combining a provider, the specific destination and optional compatibility parameters. It exists as not connected until an identity is bound and then becomes connected; it supports save, test and delete, with at most one active connection."),
            tm: T("Perfil de conexión remota con campo de proveedor más campos específicos; formulario con widget OAuth embebido (detalle de pantalla tomado de datos heredados, no releído).", "Remote connection profile with a provider field plus specific fields; form with an embedded OAuth widget (screen detail taken from legacy data, not re-read).")
          },
          auth: {
            nf: T("Mecanismo de autenticación delegada", "Delegated authentication mechanism"),
            fm: T("Estrategia por la que el sistema obtiene autorización sin conocer la contraseña: concesión por código con prueba de posesión (por defecto), concesión por código de dispositivo (condicionada a una habilitación administrativa) o credencial administrada para Git genérico. Lleva el perfil de sin conectar a conectado.",
                  "Strategy by which the system obtains authorization without knowing the password: authorization-code grant with proof of possession (default), device-code grant (conditioned on an administrative enablement) or a managed credential for generic Git. It takes the profile from not connected to connected."),
            tm: T("RemoteAuthService.start() inicia el flujo OAuth con código y PKCE o Device Code; la credencial queda cifrada en una bóveda del servidor.", "RemoteAuthService.start() starts the OAuth code and PKCE or Device Code flow; the credential is kept encrypted in a server vault."),
            variants: [
              { fd: T("Concesión por código con PKCE: opción por defecto, con redirección al proveedor real.", "Authorization-code grant with PKCE: default option, with redirection to the real provider.") },
              { fd: T("Concesión por código de dispositivo: solo si la política administrativa la permite; pensada para contextos sin navegador.", "Device-code grant: only if administrative policy allows it; meant for browserless contexts.") },
              { fd: T("Usuario y contraseña sobre HTTPS administrados en el servidor; nunca un token en claro.", "HTTPS user and password managed on the server; never a plain token.") }
            ]
          },
          gitops: {
            nf: T("Operación Git (acción sobre un repositorio versionado)", "Git operation (action on a versioned repository)"),
            fm: T("Catálogo cerrado de diez operaciones aplicables a un perfil de familia Git. Cada una produce un nuevo estado del repositorio o un conflicto de fusión; ninguna destructiva (sin force-push ni reset) forma parte del catálogo.",
                  "Closed catalog of ten operations applicable to a Git-family profile. Each yields a new repository state or a merge conflict; no destructive operation (no force-push or reset) is part of the catalog."),
            tm: T("Enum RemoteGitOperation de 10 valores, ejecutadas por RemoteGitWorkspaceService mediante un ejecutor sin shell.", "Enum RemoteGitOperation with 10 values, run by RemoteGitWorkspaceService through a shell-less runner."),
            variants: [
              { k: "STATUS", fd: T("Consulta de solo lectura del estado de ramas y cambios.", "Read-only query of branch and change state.") },
              { k: "INIT", fd: T("Crea un repositorio nuevo en el destino.", "Creates a new repository at the destination.") },
              { k: "FETCH", fd: T("Incorpora el historial remoto sin tocar el trabajo local.", "Brings in remote history without touching local work.") },
              { k: "PULL", fd: T("Incorpora y fusiona según una estrategia elegida (p. ej. solo avance rápido); puede acabar en conflicto.", "Brings in and merges according to a chosen strategy (e.g. fast-forward only); may end in conflict.") },
              { k: "PUSH", fd: T("Publica los commits locales en el remoto.", "Publishes local commits to the remote.") },
              { k: "MERGE", fd: T("Combina dos ramas; puede producir ficheros en conflicto.", "Combines two branches; may produce conflicting files.") },
              { k: "ABORT_MERGE", fd: T("Cancela una fusión en curso y devuelve el estado previo.", "Cancels an ongoing merge and returns the previous state.") },
              { k: "COMMIT_ALL", fd: T("Confirma todos los cambios pendientes con un mensaje.", "Commits all pending changes with a message.") },
              { k: "CREATE_BRANCH", fd: T("Abre una línea de trabajo nueva con nombre.", "Opens a new named line of work.") },
              { k: "CHECKOUT", fd: T("Cambia la rama activa.", "Switches the active branch.") }
            ]
          },
          resolver: {
            nf: T("Resolución explícita de conflicto de fusión", "Explicit merge-conflict resolution"),
            fm: T("Estado abierto de un conflicto, nacido de una fusión o un pull sobre un proveedor Git, que se contrasta en cuatro vistas (base, local, remota y resultado) y termina en una resolución, opcionalmente preparada. Un conflicto corresponde a una resolución; nunca se cierra en silencio.",
                  "Open state of a conflict, born from a merge or pull on a Git provider, contrasted in four views (base, local, remote and result) and ending in a resolution, optionally staged. One conflict corresponds to one resolution; it is never closed silently."),
            tm: T("Visor de 4 paneles en el panel Git; no se documentó un esquema propio.", "4-pane viewer in the Git panel; no schema of its own was documented.")
          },
          transfer: {
            nf: T("Transferencia de ficheros por ruta exacta", "File transfer by exact path"),
            fm: T("Intercambio de una unidad de contenido (proyecto completo o una capa) con cualquier proveedor, independiente de la semántica Git. Una transferencia afecta a una ruta exacta y puede condicionarse por modo de escritura y por versión esperada para no pisar cambios ajenos.",
                  "Exchange of a unit of content (whole project or one layer) with any provider, independent of Git semantics. A transfer affects one exact path and can be conditioned by write mode and by expected version so that others' changes are not overwritten."),
            tm: T("RemoteProjectTransferService; ZIP o capa con versión esperada (control optimista) y modo de escritura.", "RemoteProjectTransferService; ZIP or layer with expected version (optimistic control) and write mode."),
            variants: [
              { fd: T("Unidad completa del proyecto empaquetada; solo rutas exactas, sin exploración remota.", "Complete project unit packaged; exact paths only, no remote browsing.") },
              { fd: T("Un único fichero de capa, protegido por una versión esperada.", "A single layer file, protected by an expected version.") }
            ]
          }
        }
      },

      /* ------------------------------------------------------------------ portability */
      portability: {
        func: {
          title: T("Empaquetado, fusión semántica y aplicación transaccional de capas de capacidad", "Packaging, semantic merge and transactional application of capability layers"),
          summary: T("Una capa es la unidad de capacidad (metamodelo, gramática, editor, estilo, representación, paleta); un paquete agrupa varias capas con sus dependencias resueltas. Al importar sobre capas ya existentes la fusión detecta conflictos y exige exactamente una decisión por conflicto, que forma el plan; solo al confirmarlo se aplica como transacción, con vista previa, aplicación y reversión sobre un diario que solo crece. Cada capacidad del paquete declara fidelidad y modo de ejecución.",
                     "A layer is the unit of capability (metamodel, grammar, editor, style, representation, palette); a package groups several layers with their dependencies resolved. When importing over already existing layers the merge detects conflicts and demands exactly one decision per conflict, which forms the plan; only once confirmed is it applied as a transaction, with preview, apply and rollback over an append-only journal. Each capability in the package declares its fidelity and execution mode."),
          inputs: [T("Capas seleccionadas del proyecto origen", "Layers selected from the source project"), T("Paquete a importar y capas ya presentes en el destino", "Package to import and layers already present at the destination"), T("Una decisión por cada conflicto detectado", "One decision per detected conflict"), T("Confirmación explícita del plan", "Explicit confirmation of the plan")],
          outputs: [T("Paquete portable con versión, dependencias y, opcionalmente, firma", "Portable package with version, dependencies and, optionally, signature"), T("Vista previa de fusión con sus conflictos y problemas graduados", "Merge preview with its conflicts and graded problems"), T("Documentos modificados más diario de transacción", "Modified documents plus transaction journal"), T("Reversión dirigida por versión de documento", "Rollback driven by document version")],
          conditions: [T("La fusión solo interviene si hay solapamiento con capas existentes", "Merge only intervenes when there is overlap with existing layers"), T("Nada se aplica sin plan confirmado", "Nothing is applied without a confirmed plan"), T("La reversión deshace únicamente lo que esa transacción cambió y se rechaza si hay cambios posteriores", "Rollback undoes only what that transaction changed and is refused if later changes exist"), T("El paquete puede rechazarse por seis motivos de integridad", "The package can be rejected for six integrity reasons")]
        },
        tech: {
          title: T("Archivo de paquete, fusión semántica y transacción de capa con journal append-only y rollback por ETag", "Package archive, semantic merge and layer transaction with append-only journal and ETag rollback"),
          summary: T("CapabilityPackageArchiveService empaqueta y valida el archivo (CapabilityPackageArchiveException.Reason), CapabilitySemanticMergeService calcula conflictos y decisiones (CapabilitySemanticMergeDecision), CapabilityLayerTransactionService ejecuta preview/apply/rollback con un journal append-only y CapabilityOrchestrationService coordina. La reversión se dirige por el ETag de cada documento. No se localizó un enum de estados de transacción ni los tipos de conflicto; el detalle de pantalla procede de datos heredados.",
                     "CapabilityPackageArchiveService packages and validates the archive (CapabilityPackageArchiveException.Reason), CapabilitySemanticMergeService computes conflicts and decisions (CapabilitySemanticMergeDecision), CapabilityLayerTransactionService runs preview/apply/rollback with an append-only journal and CapabilityOrchestrationService coordinates. Rollback is driven by each document's ETag. No transaction-state enum nor conflict kinds were located; screen detail comes from legacy data.")
        },
        elements: {
          package: {
            nf: T("Paquete de capacidad (unidad portable versionada)", "Capability package (versioned portable unit)"),
            fm: T("Agregado portable de una a N capas con dependencias resueltas, versión, compatibilidad y firma opcional. Es independiente del paquete de proyecto completo. Se acepta o se rechaza al importarse, y los motivos de rechazo son un conjunto cerrado.",
                  "Portable aggregate of one to N layers with resolved dependencies, version, compatibility and optional signature. It is independent from the whole-project package. It is accepted or rejected on import, and the rejection reasons form a closed set."),
            tm: T("CapabilityPackageArchiveService; fallos tipados con CapabilityPackageArchiveException.Reason.", "CapabilityPackageArchiveService; failures typed with CapabilityPackageArchiveException.Reason."),
            variants: [
              { k: "DISABLED", fd: T("Rechazo porque la función de paquetes está desactivada en el entorno.", "Rejection because the package feature is switched off in the environment.") },
              { k: "INVALID_PACKAGE", fd: T("Rechazo porque el contenido no respeta el contrato del paquete.", "Rejection because the content does not respect the package contract.") },
              { k: "INVALID_ARCHIVE", fd: T("Rechazo porque el contenedor está corrupto o malformado.", "Rejection because the container is corrupt or malformed.") },
              { k: "LIMIT_EXCEEDED", fd: T("Rechazo porque se sobrepasa un límite permitido de tamaño o cantidad.", "Rejection because an allowed size or quantity limit is exceeded.") },
              { k: "SIGNATURE_INVALID", fd: T("Rechazo porque la firma no corresponde al contenido.", "Rejection because the signature does not match the content.") },
              { k: "IO_ERROR", fd: T("Fallo de lectura o escritura durante el tratamiento del archivo.", "Read or write failure while processing the archive.") }
            ]
          },
          layer: {
            nf: T("Capa de capacidad (clase de contenido empaquetable)", "Capability layer (packageable content kind)"),
            fm: T("Clase de contenido del proyecto que se mueve y fusiona como unidad. Un paquete contiene de 1 a N capas y entre ellas hay dependencias. Cada capa declara fidelidad (exacta, semántica, estructural, parcial, solo proyección, no soportada), modo de ejecución (embebido, remoto, híbrido, pasivo) y, para el metamodelo, su origen (propio, importado, externo, sincronizado).",
                  "Class of project content that moves and merges as a unit. A package holds 1 to N layers and dependencies exist among them. Each layer declares fidelity (exact, semantic, structural, partial, projection only, unsupported), execution mode (embedded, remote, hybrid, passive) and, for the metamodel, its origin (owned, imported, external, synchronized)."),
            tm: T("Enum HydraCapabilityKind; fidelidad en CapabilityFidelity, modo en CapabilityExecutionMode, origen en MetamodelOriginKind. La lista de HydraCapabilityKind se leyó truncada.", "Enum HydraCapabilityKind; fidelity in CapabilityFidelity, mode in CapabilityExecutionMode, origin in MetamodelOriginKind. The HydraCapabilityKind list was read truncated."),
            variants: [
              { k: "METAMODEL", fd: T("Los tipos del lenguaje de dominio.", "The domain language's types.") },
              { k: "GRAMMAR", fd: T("La sintaxis textual del lenguaje.", "The language's textual syntax.") },
              { k: "TEXTUAL_EDITOR_DEFINITION", fd: T("Reglas de comportamiento del editor textual.", "Behaviour rules of the textual editor.") },
              { k: "TEXTUAL_SYNTAX_STYLE", fd: T("Presentación (color y formato) de la sintaxis textual.", "Presentation (colour and format) of the textual syntax.") },
              { k: "VISUAL_REPRESENTATION", fd: T("Correspondencia entre elementos del modelo y su dibujo.", "Correspondence between model elements and their drawing.") },
              { k: "PALETTE", fd: T("Conjunto de herramientas de creación del editor visual.", "Set of creation tools of the visual editor.") }
            ]
          },
          merge: {
            nf: T("Fusión semántica (comparación de dos conjuntos de capas)", "Semantic merge (comparison of two layer sets)"),
            fm: T("Cálculo que contrasta un paquete entrante con las capas existentes y enumera conflictos (0..N), cada uno a la espera de una decisión. Su ciclo es detectada, previsualizada y decidida; es condicional porque solo existe cuando hay solapamiento, y alimenta la transacción sin aplicar nada por sí misma.",
                  "Computation contrasting an incoming package with existing layers and enumerating conflicts (0..N), each awaiting a decision. Its cycle is detected, previewed and decided; it is conditional because it exists only when there is overlap, and it feeds the transaction without applying anything itself."),
            tm: T("CapabilitySemanticMergeService; los tipos de conflicto no se enumeraron en el código leído.", "CapabilitySemanticMergeService; conflict kinds were not enumerated in the code read.")
          },
          decision: {
            nf: T("Decisión de resolución por conflicto", "Per-conflict resolution decision"),
            fm: T("Elección obligatoria y única por cada conflicto detectado; el conjunto de decisiones compone el plan de aplicación. Pasa de pendiente a decidida y no existe resolución implícita.",
                  "Mandatory, single choice for each detected conflict; the set of decisions makes up the application plan. It goes from pending to decided and there is no implicit resolution."),
            tm: T("Enum CapabilitySemanticMergeDecision de 4 valores.", "Enum CapabilitySemanticMergeDecision with 4 values."),
            variants: [
              { k: "KEEP_EXISTING", fd: T("Prevalece el contenido ya presente en el destino.", "The content already present at the destination prevails.") },
              { k: "USE_RECOVERED", fd: T("Prevalece el contenido que trae el paquete.", "The content brought by the package prevails.") },
              { k: "MERGE", fd: T("Se combinan ambos contenidos.", "Both contents are combined.") },
              { k: "SKIP", fd: T("El elemento conflictivo no se toca.", "The conflicting item is left untouched.") }
            ]
          },
          diag: {
            nf: T("Hallazgo del paquete (problema graduado por severidad)", "Package finding (problem graded by severity)"),
            fm: T("Aviso derivado de la revisión de un paquete (0..N por paquete), ordenado en tres niveles de severidad que orientan si procede importar. No es una decisión del usuario, sino un resultado calculado.",
                  "Notice derived from reviewing a package (0..N per package), ordered in three severity levels that guide whether to import. It is not a user decision but a computed result."),
            tm: T("Enum CapabilityPackageDiagnosticSeverity de 3 valores.", "Enum CapabilityPackageDiagnosticSeverity with 3 values."),
            variants: [
              { k: "ERROR", fd: T("Severidad máxima: el hallazgo es bloqueante para una importación segura.", "Highest severity: the finding is blocking for a safe import.") },
              { k: "WARNING", fd: T("Severidad intermedia: requiere revisión pero no impide continuar.", "Intermediate severity: needs review but does not prevent continuing.") },
              { k: "INFO", fd: T("Severidad mínima: dato informativo sin efecto en el plan.", "Lowest severity: informative datum with no effect on the plan.") }
            ]
          },
          transaction: {
            nf: T("Transacción de capa (aplicación atómica y reversible del plan)", "Layer transaction (atomic, reversible application of the plan)"),
            fm: T("Ejecución del plan como un todo sobre N documentos, cada uno con su versión. Recorre vista previa, aplicada y, opcionalmente, revertida; si falla a mitad se deshace solo lo que ella cambió. Deja un registro ligado 1 a 1.",
                  "Execution of the plan as a whole over N documents, each with its version. It goes through preview, applied and, optionally, rolled back; if it fails midway only what it changed is undone. It leaves a record bound 1 to 1."),
            tm: T("CapabilityLayerTransactionService con preview/apply/rollback y journal; el rollback se guía por el ETag de cada documento. No se halló un enum de estados.", "CapabilityLayerTransactionService with preview/apply/rollback and journal; rollback is guided by each document's ETag. No state enum was found."),
            variants: [
              { fd: T("Fase de cálculo del efecto sin modificar ningún documento.", "Phase that computes the effect without modifying any document.") },
              { fd: T("Fase que escribe los cambios en los documentos y anota el registro.", "Phase that writes the changes into the documents and notes the record.") },
              { fd: T("Fase opcional que invierte exactamente los cambios de esta transacción, documento a documento.", "Optional phase that reverses exactly this transaction's changes, document by document.") }
            ]
          },
          journal: {
            nf: T("Diario de transacción (historia inmutable de cambios aplicados)", "Transaction journal (immutable history of applied changes)"),
            fm: T("Registro derivado, uno por transacción, que solo crece y nunca se reescribe; conserva por documento la versión resultante para poder revertir con exactitud y servir de evidencia.",
                  "Derived record, one per transaction, that only grows and is never rewritten; it keeps per document the resulting version so that rollback is exact and it can serve as evidence."),
            tm: T("Journal append-only con ETag por documento, dentro de CapabilityLayerTransactionService.", "Append-only journal with per-document ETag, inside CapabilityLayerTransactionService.")
          }
        }
      },

      /* ------------------------------------------------------------------ persistence */
      persistence: {
        func: {
          title: T("Persistencia de documentos canónicos con concurrencia optimista", "Canonical document persistence with optimistic concurrency"),
          summary: T("Cada elemento persistible de cualquier capacidad es un documento con identidad propia y una versión vigente. Un guardado es válido solo si parte de la versión actual; en caso contrario hay un choque y se rechaza, de modo que ningún cambio ajeno se pisa en silencio. La capa no posee esquema de dominio: respeta el de cada capacidad.",
                     "Every persistable item of any capability is a document with its own identity and a current version. A save is valid only if it starts from the current version; otherwise there is a clash and it is rejected, so no one else's change is silently overwritten. The layer owns no domain schema: it honors each capability's."),
          inputs: [T("Documento con contenido definido por su capacidad propietaria", "Document with content defined by its owning capability"), T("Versión con la que se empezó a editar", "Version the editing started from"), T("Solicitud de guardado de cualquier capacidad", "Save request from any capability")],
          outputs: [T("Documento almacenado con una versión nueva", "Stored document with a new version"), T("Rechazo por choque de versiones", "Rejection on version clash"), T("Efecto observable para la auditoría", "Observable effect for the audit")],
          conditions: [T("La versión enviada debe coincidir con la vigente", "The submitted version must match the current one"), T("Cada documento tiene versión independiente", "Each document has an independent version"), T("El almacén es local y no depende de una base de datos", "The store is local and does not depend on a database")]
        },
        tech: {
          title: T("CanonicalDocumentStore con fábrica local y comprobación optimista por ETag", "CanonicalDocumentStore with local factory and optimistic ETag check"),
          summary: T("Los documentos se guardan como JSON en disco local mediante CanonicalDocumentStore, creado por LocalCanonicalDocumentStoreFactory, con ETag por documento como control de concurrencia. Según la base heredada, la fábrica local también conecta la auditoría universal a cada almacén.",
                     "Documents are saved as JSON on local disk through CanonicalDocumentStore, created by LocalCanonicalDocumentStoreFactory, with a per-document ETag as the concurrency control. Per the legacy base, the local factory also attaches the universal audit to each store.")
        },
        elements: {
          document: {
            nf: T("Documento canónico (unidad persistida con identidad)", "Canonical document (persisted unit with identity)"),
            fm: T("Representación persistida de un elemento de cualquier capacidad, con relación 1 a 1 con el elemento guardado. Recorre leído, modificado y guardado, y arrastra su propia versión; su contenido lo gobierna la capacidad propietaria.",
                  "Persisted representation of an item of any capability, in a 1 to 1 relation with the saved item. It goes through read, modified and saved, and carries its own version; its content is governed by the owning capability."),
            tm: T("Archivo JSON con ETag gestionado por CanonicalDocumentStore.", "JSON file with ETag managed by CanonicalDocumentStore.")
          },
          version: {
            nf: T("Versión vigente de documento (testigo de concurrencia)", "Current document version (concurrency token)"),
            fm: T("Marca única por documento que cambia con cada guardado aceptado. Un guardado solo prospera si presenta la marca vigente; otras capacidades la reutilizan para revertir de forma dirigida.",
                  "Unique mark per document that changes with every accepted save. A save succeeds only if it presents the current mark; other capabilities reuse it to roll back in a targeted way."),
            tm: T("ETag por documento, evaluado en CanonicalDocumentStore.", "Per-document ETag, evaluated in CanonicalDocumentStore.")
          },
          store: {
            nf: T("Almacén de documentos del proyecto (colección local)", "Project document store (local collection)"),
            fm: T("Colección derivada, una por proyecto, que contiene 0..N documentos y siempre está disponible sin configuración. Está acoplada al mecanismo de auditoría.",
                  "Derived collection, one per project, holding 0..N documents and always available without configuration. It is coupled to the audit mechanism."),
            tm: T("Instanciado por LocalCanonicalDocumentStoreFactory.", "Instantiated by LocalCanonicalDocumentStoreFactory.")
          }
        }
      },

      /* ------------------------------------------------------------------ audit */
      audit: {
        func: {
          title: T("Registro universal e inmutable de mutaciones de documentos", "Universal, immutable record of document mutations"),
          summary: T("Cada cambio aceptado sobre un documento genera una entrada que anota qué documento cambió y cuándo, sin copiar el contenido. Las entradas se agrupan en un único registro que solo crece y que nadie reescribe. Es una garantía de plataforma y no una opción: no se puede desactivar por proyecto ni capacidad, y es distinta del rastro opcional de eventos.",
                     "Every accepted change to a document generates an entry noting which document changed and when, without copying the content. Entries are grouped in a single log that only grows and that nobody rewrites. It is a platform guarantee, not an option: it cannot be disabled per project or capability, and it is distinct from the optional events trail."),
          inputs: [T("Mutación aceptada de un documento", "Accepted mutation of a document"), T("Identidad del documento y momento del cambio", "Document identity and moment of the change")],
          outputs: [T("Entrada anotada en el registro", "Entry noted in the log"), T("Historial consultable para depuración y procedencia", "Searchable history for debugging and provenance")],
          conditions: [T("Se dispara siempre que persiste un documento", "Fires whenever a document is persisted"), T("Registra el hecho del cambio, no su contenido", "Records the fact of the change, not its content"), T("Las entradas jamás se modifican ni se borran", "Entries are never modified or deleted")]
        },
        tech: {
          title: T("Registro JSONL append-only alimentado por un observador de mutaciones", "Append-only JSONL log fed by a mutation observer"),
          summary: T("JsonlAuditLogStore añade líneas JSONL; AuditCanonicalDocumentMutationObserver se conecta a cada almacén de documentos (a través de la fábrica local) y emite las entradas. HydraDistributionProvenanceService figura como fuente en datos heredados, con su papel sin reverificar. publishEvents() solo afecta a Eventos, no a la auditoría.",
                     "JsonlAuditLogStore appends JSONL lines; AuditCanonicalDocumentMutationObserver attaches to every document store (through the local factory) and emits the entries. HydraDistributionProvenanceService is listed as a source in legacy data, its role not re-verified. publishEvents() only affects Events, not the audit.")
        },
        elements: {
          entry: {
            nf: T("Entrada de auditoría (hecho de mutación)", "Audit entry (mutation fact)"),
            fm: T("Unidad mínima del registro: afirma que un documento concreto cambió en un momento dado. Un documento acumula N entradas a lo largo de su vida; cada una se anota una vez y es inmutable.",
                  "Smallest unit of the log: asserts that a specific document changed at a given moment. A document accumulates N entries over its life; each is noted once and is immutable."),
            tm: T("Línea JSONL con autor y cambio, escrita por JsonlAuditLogStore.", "JSONL line with author and change, written by JsonlAuditLogStore.")
          },
          log: {
            nf: T("Registro de auditoría (secuencia ordenada de solo añadir)", "Audit log (ordered append-only sequence)"),
            fm: T("Secuencia única por plataforma que agrupa todas las entradas. Su única transición es crecer; permite reconstruir el origen de un resultado y está disponible con carácter universal.",
                  "Single sequence per platform grouping all entries. Its only transition is to grow; it makes it possible to reconstruct the origin of a result and is universally available."),
            tm: T("JsonlAuditLogStore sobre formato JSONL.", "JsonlAuditLogStore over JSONL format.")
          },
          observer: {
            nf: T("Observador de mutaciones (disparador automático)", "Mutation observer (automatic trigger)"),
            fm: T("Mecanismo derivado, uno por almacén, que convierte cada guardado en una entrada sin depender de ninguna política. Está siempre activo y no admite desactivación.",
                  "Derived mechanism, one per store, that turns every save into an entry without depending on any policy. It is always on and cannot be disabled."),
            tm: T("AuditCanonicalDocumentMutationObserver, conectado por la fábrica local de almacenes.", "AuditCanonicalDocumentMutationObserver, attached by the local store factory.")
          }
        }
      },

      /* ------------------------------------------------------------------ security */
      security: {
        func: {
          title: T("Defensa perimetral: acotación de peticiones y confinamiento de procesos externos", "Perimeter defence: request bounding and external process confinement"),
          summary: T("Dos barreras independientes: todo lo que entra se acepta o se rechaza por forma y tamaño antes de alcanzar cualquier capacidad, y toda ejecución de un programa externo se confina con un entorno mínimo, salida limitada y terminación asegurada. Si el programa necesario no existe se declara no disponible y su comportamiento no se simula. La capa ignora el contenido de metamodelos, modelos y reglas.",
                     "Two independent barriers: everything that comes in is accepted or rejected by shape and size before reaching any capability, and every run of an external program is confined with a minimal environment, bounded output and ensured termination. If the required program does not exist it is declared unavailable and its behaviour is not simulated. The layer ignores the content of metamodels, models and rules."),
          inputs: [T("Petición entrante a cualquier capacidad", "Incoming request to any capability"), T("Solicitud de ejecutar un programa externo con sus argumentos", "Request to run an external program with its arguments")],
          outputs: [T("Petición aceptada o rechazada", "Accepted or rejected request"), T("Resultado de ejecución acotado", "Bounded run result"), T("Declaración de motor no disponible", "Engine-unavailable declaration")],
          conditions: [T("Precede a todas las capacidades y está siempre activa", "Precedes all capabilities and is always on"), T("Es el único punto de lanzamiento de programas externos", "It is the single launch point for external programs"), T("Para Git no hay shell ni comandos arbitrarios", "For Git there is no shell or arbitrary commands")]
        },
        tech: {
          title: T("HydraRequestHardeningFilter y HydraExternalProcessRunner (sandbox)", "HydraRequestHardeningFilter and HydraExternalProcessRunner (sandbox)"),
          summary: T("Un filtro de endurecimiento de peticiones, HydraRequestHardeningFilter, aplica políticas de forma y tamaño, y HydraExternalProcessRunner lanza procesos externos con sandbox. La etiqueta compartida TOOL_UNAVAILABLE expresa el motor ausente. Los límites numéricos y la exclusividad del punto de ejecución provienen de la base heredada y no se verificaron.",
                     "A request hardening filter, HydraRequestHardeningFilter, applies shape and size policies, and HydraExternalProcessRunner launches external processes in a sandbox. The shared TOOL_UNAVAILABLE label expresses the missing engine. The numeric limits and the exclusivity of the execution point come from the legacy base and were not verified.")
        },
        elements: {
          filter: {
            nf: T("Control de admisión de peticiones (forma y tamaño)", "Request admission control (shape and size)"),
            fm: T("Comprobación 1 a 1 sobre cada petición, que precede a toda capacidad y actúa igual para todas. Su ciclo es recibida y luego aceptada o rechazada; una petición rechazada no llega a ninguna capacidad.",
                  "1 to 1 check on every request, preceding every capability and acting the same for all. Its cycle is received and then accepted or rejected; a rejected request reaches no capability."),
            tm: T("HydraRequestHardeningFilter con políticas de petición.", "HydraRequestHardeningFilter with request policies.")
          },
          runner: {
            nf: T("Confinamiento de ejecución externa (entorno aislado por ejecución)", "External execution confinement (one isolated environment per run)"),
            fm: T("Entorno aislado por cada ejecución: entorno mínimo, salida con tope y terminación asegurada; ciclo lanzado, acotado y apagado. Lo utilizan Git y los motores externos, y el resultado es acotado o la declaración de no disponible.",
                  "Isolated environment for each run: minimal environment, capped output and ensured termination; cycle launched, bounded and shut down. Git and the external engines use it, and the outcome is bounded or an unavailable declaration."),
            tm: T("HydraExternalProcessRunner (sandbox de procesos).", "HydraExternalProcessRunner (process sandbox).")
          },
          unavailable: {
            nf: T("Estado de ausencia de motor (resultado honesto)", "Engine-absence state (honest outcome)"),
            fm: T("Resultado derivado, 0..1 por ejecución, que se produce solo cuando el programa externo no existe. Detiene la operación y se refleja en Toolchains; sustituye a cualquier simulación.",
                  "Derived outcome, 0..1 per run, produced only when the external program does not exist. It stops the operation and is reflected in Toolchains; it replaces any simulation."),
            tm: T("Etiqueta compartida TOOL_UNAVAILABLE, detectada en HydraExternalProcessRunner.", "Shared TOOL_UNAVAILABLE label, detected in HydraExternalProcessRunner.")
          }
        }
      }
    },

    procs: {
      /* ------------------------------------------------------------------ repos_connect_sync */
      repos_connect_sync: {
        nf: T("Vincular un perfil y sincronizar contenido", "Bind a profile and synchronize content"),
        df: T("Se crea el perfil, se autentica (de sin conectar a conectado) y el proveedor decide la rama: si es de familia Git se opera sobre ramas y, ante un conflicto, se resuelve explícitamente; en todo caso puede transferirse un proyecto o una capa. Termina con el contenido alineado o el conflicto resuelto.",
              "The profile is created and authenticated (from not connected to connected) and the provider decides the branch: if it is of the Git family one operates on branches and, on a conflict, resolves it explicitly; in any case a project or a layer can be transferred. It ends with aligned content or a resolved conflict."),
        nt: T("RemoteAuthService → RemoteGitWorkspaceService / transferencia", "RemoteAuthService → RemoteGitWorkspaceService / transfer"),
        dt: T("La autenticación pasa por RemoteAuthService (/api/remote-auth/*); las operaciones Git, restringidas a 3 proveedores, por RemoteGitWorkspaceService (/api/remote-git/*) y la transferencia por RemoteProjectTransferService (/api/remote-storage/*). El detalle del formulario de conexión procede de datos heredados.",
              "Authentication goes through RemoteAuthService (/api/remote-auth/*); Git operations, restricted to 3 providers, through RemoteGitWorkspaceService (/api/remote-git/*) and transfer through RemoteProjectTransferService (/api/remote-storage/*). The connection form detail comes from legacy data."),
        nodes: {
          a1: { lf: T("Registrar el perfil de conexión", "Register the connection profile"), sf: T("proveedor + destino → perfil sin conectar", "provider + destination → not-connected profile"), fio: T("Proveedor y destino específico → perfil nominal persistente sin identidad", "Provider and specific destination → persistent named profile without identity") },
          a2: { lf: T("Establecer la identidad delegada", "Establish the delegated identity"), sf: T("perfil: sin conectar → conectado", "profile: not connected → connected"), fio: T("Perfil sin conectar y consentimiento del titular → conexión activa con credencial en custodia", "Not-connected profile and owner consent → active connection with credential in custody"), lt: "RemoteAuthService.start()", st: T("OAuth / Device Code", "OAuth / Device Code"), tx: T("Inicia el flujo de autenticación del proveedor desde RemoteAuthService; la credencial resultante queda en el servidor.", "Starts the provider authentication flow from RemoteAuthService; the resulting credential stays on the server.") },
          d1: { lf: T("Clasificar la familia del proveedor", "Classify the provider family"), sf: T("Git (3) frente a solo ficheros (3)", "Git (3) versus file-only (3)"), fio: T("Proveedor del perfil → rama Git o rama de transferencia", "Profile provider → Git branch or transfer branch") },
          a3: { lf: T("Aplicar operaciones de versionado", "Apply versioning operations"), sf: T("catálogo de 10 operaciones, sin destructivas", "catalog of 10 operations, none destructive"), fio: T("Operación y parámetros → nuevo estado del repositorio o conflicto", "Operation and parameters → new repository state or conflict"), lt: "RemoteGitWorkspaceService", st: T("ejecutor sin shell; solo 3 proveedores", "shell-less runner; 3 providers only"), tx: T("Ejecuta las operaciones RemoteGitOperation mediante un ejecutor sin shell; el código rechaza explícitamente Git en los proveedores de solo ficheros.", "Runs the RemoteGitOperation operations through a shell-less runner; the code explicitly rejects Git on file-only providers.") },
          d2: { lf: T("Detectar conflicto de fusión", "Detect merge conflict"), sf: T("fusión limpia o ficheros en conflicto", "clean merge or conflicting files"), fio: T("Resultado de la fusión → ruta sin conflicto o conflicto abierto", "Merge outcome → conflict-free path or open conflict") },
          a4: { lf: T("Cerrar el conflicto con resolución explícita", "Close the conflict with explicit resolution"), sf: T("conflicto: abierto → resuelto", "conflict: open → resolved"), fio: T("Fichero en conflicto → resolución elegida y, si se desea, preparada", "Conflicting file → chosen resolution and, if desired, staged") },
          a5: { lf: T("Intercambiar unidades de contenido", "Exchange content units"), sf: T("proyecto o capa, por ruta exacta", "project or layer, by exact path"), fio: T("Proyecto o capa → fichero en el proveedor o contenido restaurado", "Project or layer → file at the provider or restored content") },
          r: { lf: T("Contenido alineado entre ambos extremos", "Content aligned across both ends"), sf: T("sincronizado o conflicto resuelto", "synchronized or conflict resolved"), fio: T("Operaciones y transferencias completadas → mismo contenido en proveedor y proyecto", "Completed operations and transfers → same content at provider and project") }
        }
      },

      /* ------------------------------------------------------------------ repos_oauth_connect */
      repos_oauth_connect: {
        nf: T("Autenticación delegada hasta credencial custodiada", "Delegated authentication up to a custodied credential"),
        df: T("Se elige el flujo según la política: código con PKCE por defecto, código de dispositivo solo si se habilitó, o credencial gestionada en Git genérico. Los dos primeros terminan en un intercambio en el servidor; la credencial se guarda cifrada y la conexión pasa de pendiente a activa.",
              "The flow is chosen according to policy: code with PKCE by default, device code only if enabled, or managed credential for generic Git. The first two end in an exchange on the server; the credential is stored encrypted and the connection goes from pending to active."),
        nt: T("RemoteAuthService.start() → bóveda del servidor", "RemoteAuthService.start() → server vault"),
        dt: T("RemoteAuthService.start() arranca el flujo OAuth (código + PKCE o Device Code); el navegador solo recibe la confirmación, y los tokens y el secreto de cliente no se le devuelven. El detalle de la bóveda no está descrito más allá de ser una bóveda cifrada del servidor.",
              "RemoteAuthService.start() starts the OAuth flow (code + PKCE or Device Code); the browser only receives the confirmation, and tokens and client secret are not returned to it. The vault is described no further than being an encrypted server vault."),
        nodes: {
          d1: { lf: T("Seleccionar la concesión de autorización", "Select the authorization grant"), sf: T("PKCE por defecto; dispositivo condicionado", "PKCE by default; device conditioned"), fio: T("Proveedor y política administrativa → concesión por código, por dispositivo o credencial gestionada", "Provider and administrative policy → code grant, device grant or managed credential") },
          a1: { lf: T("Conceder consentimiento ante el proveedor", "Grant consent at the provider"), sf: T("redirección al proveedor real", "redirection to the real provider"), fio: T("Identidad del titular → código de autorización emitido por el proveedor", "Holder identity → authorization code issued by the provider") },
          a2: { lf: T("Autorizar mediante código de dispositivo", "Authorize through device code"), sf: T("para contextos sin navegador", "for browserless contexts"), fio: T("Código mostrado al titular → autorización concedida desde otro dispositivo", "Code shown to the holder → authorization granted from another device") },
          a3: { lf: T("Canjear la autorización por tokens", "Exchange the authorization for tokens"), sf: T("intercambio solo en servidor", "exchange on the server only"), fio: T("Código de autorización → tokens de acceso y refresco en poder del servidor", "Authorization code → access and refresh tokens held by the server"), lt: "RemoteAuthService.start()", st: T("intercambio de código en el servidor", "code exchange on the server"), tx: T("El servidor obtiene los tokens; al navegador solo llega una confirmación.", "The server obtains the tokens; only a confirmation reaches the browser.") },
          a4: { lf: T("Custodiar la credencial cifrada", "Keep the credential in encrypted custody"), sf: T("conexión: pendiente → activa", "connection: pending → active"), fio: T("Tokens o credencial gestionada → credencial en bóveda y conexión activa", "Tokens or managed credential → credential in vault and active connection"), tx: T("La credencial vive cifrada en una bóveda del servidor y nunca se devuelve al navegador.", "The credential lives encrypted in a server vault and is never returned to the browser.") },
          r: { lf: T("Conexión activa y reutilizable", "Active, reusable connection"), sf: T("lista para operar o transferir", "ready to operate or transfer"), fio: T("Credencial custodiada → conexión disponible para Git y transferencia", "Custodied credential → connection available for Git and transfer") }
        }
      },

      /* ------------------------------------------------------------------ repos_file_transfer */
      repos_file_transfer: {
        nf: T("Transferencia de unidad de contenido con control de versión", "Content-unit transfer with version control"),
        df: T("Se decide el sentido y la unidad (proyecto completo o una capa); para una capa puede fijarse el modo de escritura. La operación toca una ruta exacta y verifica que la versión esperada coincida; si no, no se pisa nada y se vuelve a revisar.",
              "The direction and unit are decided (whole project or one layer); for a layer the write mode can be set. The operation touches an exact path and checks that the expected version matches; if not, nothing is overwritten and review starts again."),
        nt: T("RemoteProjectTransferService (/api/remote-storage/*)", "RemoteProjectTransferService (/api/remote-storage/*)"),
        dt: T("La transferencia la realiza RemoteProjectTransferService, expuesta por RemoteStorageController; el control de versión esperada es optimista. No se documenta más detalle de la llamada.",
              "The transfer is done by RemoteProjectTransferService, exposed by RemoteStorageController; expected-version control is optimistic. No further call detail is documented."),
        nodes: {
          a: { lf: T("Determinar la unidad a intercambiar", "Determine the unit to exchange"), sf: T("proyecto completo o capa individual", "whole project or single layer"), fio: T("Sentido elegido → unidad de transferencia (ZIP del proyecto o capa)", "Chosen direction → transfer unit (project ZIP or layer)") },
          b: { lf: T("Fijar la política de escritura", "Set the write policy"), sf: T("sobrescribir, crear o actualizar", "overwrite, create or update"), fio: T("Capa a transferir → modo de escritura aplicable al destino", "Layer to transfer → write mode applicable to the destination") },
          c: { lf: T("Acceder a la ruta exacta con versión esperada", "Access the exact path with expected version"), sf: T("control optimista de concurrencia", "optimistic concurrency control"), fio: T("Ruta, modo y versión esperada → lectura o escritura condicionada", "Path, mode and expected version → conditioned read or write") },
          d: { lf: T("Contrastar la versión esperada", "Check the expected version"), sf: T("coincide o hay cambio ajeno", "matches or someone else changed it"), fio: T("Versión esperada y versión remota → continuar o volver a revisar", "Expected and remote version → proceed or review again") },
          r: { lf: T("Unidad respaldada o restaurada", "Unit backed up or restored"), sf: T("restauración simple, sin recuperación", "simple restore, no recovery"), fio: T("Transferencia validada → contenido en el proyecto o en el proveedor", "Validated transfer → content in the project or at the provider") }
        }
      },

      /* ------------------------------------------------------------------ repos_author_connection */
      repos_author_connection: {
        nf: T("Definición del perfil en tres bloques de datos", "Profile definition in three data blocks"),
        df: T("Los mismos tres bloques (identidad, destino, compatibilidad y seguridad) se capturan guiados o en secciones; el destino varía según el proveedor y el bloque final enlaza la cuenta, dejando el perfil guardado y conectado.",
              "The same three blocks (identity, destination, compatibility and security) are captured guided or in sections; the destination varies with the provider and the final block binds the account, leaving the profile saved and connected."),
        nt: T("Formulario de 3 pasos o 3 secciones + widget OAuth", "3-step or 3-section form + OAuth widget"),
        dt: T("Asistente de 3 pasos o modo manual de 3 secciones con widget OAuth embebido, que invoca la autenticación remota. Estos detalles proceden de datos heredados; los componentes de frontend no se releyeron.",
              "3-step wizard or 3-section manual mode with embedded OAuth widget, which invokes remote authentication. These details come from legacy data; the frontend components were not re-read."),
        nodes: {
          a: { lf: T("Elegir modalidad de captura", "Choose the capture mode"), sf: T("guiada o por secciones", "guided or by sections"), fio: T("Preferencia de uso → captura de los mismos tres bloques de datos", "Usage preference → capture of the same three data blocks") },
          b: { lf: T("Declarar nombre y tipo de proveedor", "Declare name and provider type"), sf: T("1 de 6 proveedores", "1 of 6 providers"), fio: T("Nombre y proveedor → identidad del perfil", "Name and provider → profile identity") },
          c: { lf: T("Declarar el destino específico", "Declare the specific destination"), sf: T("campos según la familia del proveedor", "fields depending on provider family"), fio: T("Proveedor elegido → propietario/repositorio, proyecto, URL o unidad/carpeta", "Chosen provider → owner/repository, project, URL or drive/folder") },
          d: { lf: T("Ajustar compatibilidad y límites", "Adjust compatibility and limits"), sf: T("base personalizada, tamaño máximo", "custom base, maximum size"), fio: T("Parámetros opcionales → perfil adaptado a una instancia propia", "Optional parameters → profile adapted to a self-hosted instance") },
          f: { lf: T("Vincular la identidad de la cuenta", "Bind the account identity"), sf: T("sin conectar → conectado", "not connected → connected"), fio: T("Perfil completo → conexión con identidad delegada", "Complete profile → connection with delegated identity") },
          r: { lf: T("Perfil persistido y conectado", "Persisted, connected profile"), sf: T("reutilizable en Git y transferencia", "reusable for Git and transfer"), fio: T("Bloques completos → perfil nominal en estado conectado", "Complete blocks → named profile in connected state") }
        }
      },

      /* ------------------------------------------------------------------ repos_author_git */
      repos_author_git: {
        nf: T("Operación sobre repositorio versionado y resolución de conflictos", "Operation on a versioned repository and conflict resolution"),
        df: T("Se observa el árbol de ramas, se elige una operación del catálogo (con parámetros opcionales) y se evalúa el resultado: si hay conflicto se resuelve explícitamente antes de dar el repositorio por actualizado. Solo existe para proveedores Git.",
              "The branch tree is observed, an operation of the catalog is chosen (with optional parameters) and the result is evaluated: if there is a conflict it is resolved explicitly before the repository counts as updated. It exists only for Git providers."),
        nt: T("Panel Git → RemoteGitWorkspaceService", "Git panel → RemoteGitWorkspaceService"),
        dt: T("El panel Git delega en RemoteGitWorkspaceService (/api/remote-git/*) con operaciones RemoteGitOperation; el visor de 4 paneles gestiona el conflicto. No se documenta la secuencia interna de llamadas.",
              "The Git panel delegates to RemoteGitWorkspaceService (/api/remote-git/*) with RemoteGitOperation operations; the 4-pane viewer handles the conflict. The internal call sequence is not documented."),
        nodes: {
          a: { lf: T("Inspeccionar el estado de ramas", "Inspect the branch state"), sf: T("árbol local y remoto con historial", "local and remote tree with history"), fio: T("Repositorio conectado → árbol de ramas con commits recientes", "Connected repository → branch tree with recent commits") },
          b: { lf: T("Seleccionar la operación de versionado", "Select the versioning operation"), sf: T("fetch, pull, push, commit, rama…", "fetch, pull, push, commit, branch…"), fio: T("Estado del árbol → operación elegida del catálogo", "Tree state → chosen operation from the catalog") },
          c: { lf: T("Parametrizar la operación", "Parametrize the operation"), sf: T("rama, mensaje o estrategia de pull", "branch, message or pull strategy"), fio: T("Operación elegida → operación con sus parámetros completos", "Chosen operation → operation with its full parameters") },
          d: { lf: T("Evaluar el resultado de la operación", "Evaluate the operation outcome"), sf: T("limpio o conflicto de fusión", "clean or merge conflict"), fio: T("Resultado → repositorio actualizado o conflicto abierto", "Outcome → updated repository or open conflict") },
          f: { lf: T("Reconciliar versiones divergentes", "Reconcile diverging versions"), sf: T("base, local, remoto, resultado", "base, local, remote, result"), fio: T("Fichero en conflicto → resolución elegida y opcionalmente preparada", "Conflicting file → chosen resolution, optionally staged") },
          r: { lf: T("Estado nuevo del repositorio", "New repository state"), sf: T("reflejado en el árbol de ramas", "reflected in the branch tree"), fio: T("Operación completada → repositorio con el nuevo historial", "Completed operation → repository with the new history") }
        }
      },

      /* ------------------------------------------------------------------ repos_author_transfer */
      repos_author_transfer: {
        nf: T("Intercambio de ficheros con cualquier proveedor", "File exchange with any provider"),
        df: T("Se elige la unidad (ZIP completo o un fichero), se da la ruta exacta (no hay exploración de carpetas), opcionalmente el modo de escritura, y se transfiere con control de versión esperada.",
              "The unit is chosen (whole ZIP or one file), the exact path is given (no folder browsing), optionally the write mode, and it is transferred with expected-version control."),
        nt: T("RemoteProjectTransferService", "RemoteProjectTransferService"),
        dt: T("Sobre RemoteProjectTransferService con ZIP o capa más versión esperada; para los 5 proveedores de solo fichero no hay explorador remoto, solo lectura/escritura de rutas exactas. La pantalla concreta no está documentada.",
              "On RemoteProjectTransferService with ZIP or layer plus expected version; for the 5 file-only providers there is no remote browser, only exact-path reads/writes. The concrete screen is not documented."),
        nodes: {
          a: { lf: T("Escoger la unidad de transferencia", "Pick the transfer unit"), sf: T("ZIP completo o fichero de capa", "complete ZIP or layer file"), fio: T("Intención de respaldo o carga → unidad de intercambio", "Backup or load intent → exchange unit") },
          b: { lf: T("Concretar la ruta exacta", "Specify the exact path"), sf: T("sin navegación de carpetas", "no folder navigation"), fio: T("Unidad elegida → ruta destino u origen en el proveedor", "Chosen unit → destination or source path at the provider") },
          c: { lf: T("Definir la política de escritura", "Define the write policy"), sf: T("sobrescribir, crear o actualizar", "overwrite, create or update"), fio: T("Ruta concreta → modo de escritura aplicable", "Specific path → applicable write mode") },
          d: { lf: T("Ejecutar la transferencia condicionada", "Run the conditioned transfer"), sf: T("sube o baja con versión esperada", "uploads or downloads with expected version"), fio: T("Ruta, modo y versión esperada → contenido movido o aviso de versión", "Path, mode and expected version → moved content or version warning") },
          r: { lf: T("Contenido en su nuevo ubicación", "Content in its new location"), sf: T("en el proveedor o en el proyecto", "at the provider or in the project"), fio: T("Transferencia completada → fichero disponible en el destino", "Completed transfer → file available at the destination") }
        }
      },

      /* ------------------------------------------------------------------ portability_package_merge */
      portability_package_merge: {
        nf: T("Ciclo de portabilidad: paquete, fusión, plan y transacción", "Portability cycle: package, merge, plan and transaction"),
        df: T("Dos mitades: exportar (capas elegidas más dependencias, a un paquete) o importar (detección de conflictos, vista previa, una decisión por conflicto, confirmación del plan). Confirmado, el plan se aplica como transacción de estado vista previa a aplicada, y si es necesario pasa a revertida.",
              "Two halves: export (chosen layers plus dependencies, into a package) or import (conflict detection, preview, one decision per conflict, plan confirmation). Once confirmed, the plan is applied as a transaction from preview to applied, and if necessary goes to rolled back."),
        nt: T("Archivo → fusión semántica → transacción de capa", "Archive → semantic merge → layer transaction"),
        dt: T("Encadena CapabilityPackageArchiveService (empaquetado), CapabilitySemanticMergeService (detección y decisiones) y CapabilityLayerTransactionService (preview/apply/rollback con journal append-only), con CapabilityOrchestrationService en la coordinación. Los estados de transacción provienen del vocabulario heredado, no de un enum hallado.",
              "Chains CapabilityPackageArchiveService (packaging), CapabilitySemanticMergeService (detection and decisions) and CapabilityLayerTransactionService (preview/apply/rollback with append-only journal), with CapabilityOrchestrationService coordinating. Transaction states come from legacy vocabulary, not from a found enum."),
        nodes: {
          d0: { lf: T("Separar exportación e importación", "Split export from import"), sf: T("dos mitades del mismo recorrido", "two halves of the same journey"), fio: T("Propósito del usuario → camino de empaquetado o camino de fusión", "User purpose → packaging path or merge path") },
          a1: { lf: T("Componer el paquete de capas", "Compose the layer package"), sf: T("capas + dependencias → paquete", "layers + dependencies → package"), fio: T("Capas elegidas → paquete portable con dependencias resueltas", "Chosen layers → portable package with resolved dependencies"), lt: "CapabilityPackageArchiveService", st: T("empaquetado de capas", "layer packaging"), tx: T("CapabilityPackageArchiveService genera el archivo del paquete; sus fallos se tipan con CapabilityPackageArchiveException.Reason.", "CapabilityPackageArchiveService generates the package archive; its failures are typed with CapabilityPackageArchiveException.Reason.") },
          a2: { lf: T("Enumerar solapamientos con capas existentes", "Enumerate overlaps with existing layers"), sf: T("paquete × capas del destino → conflictos", "package × target layers → conflicts"), fio: T("Paquete y capas presentes → conjunto de conflictos 0..N", "Package and present layers → set of 0..N conflicts"), lt: "CapabilitySemanticMergeService", st: T("detección de conflictos", "conflict detection"), tx: T("CapabilitySemanticMergeService compara el paquete con las capas existentes y expone los conflictos y las decisiones posibles.", "CapabilitySemanticMergeService compares the package with existing layers and exposes the conflicts and possible decisions.") },
          a3: { lf: T("Calcular el efecto sin aplicarlo", "Compute the effect without applying it"), sf: T("plan provisional, nada modificado", "provisional plan, nothing modified"), fio: T("Conflictos detectados → vista previa de fusión", "Detected conflicts → merge preview") },
          a4: { lf: T("Resolver cada conflicto con una decisión", "Resolve each conflict with a decision"), sf: T("conservar, recuperado, fusionar, omitir", "keep, recovered, merge, skip"), fio: T("Conflicto pendiente → decisión única que forma el plan", "Pending conflict → single decision that forms the plan"), tx: T("Cada decisión es un valor del enum CapabilitySemanticMergeDecision (KEEP_EXISTING, USE_RECOVERED, MERGE, SKIP).", "Each decision is a value of the enum CapabilitySemanticMergeDecision (KEEP_EXISTING, USE_RECOVERED, MERGE, SKIP).") },
          d1: { lf: T("Autorizar la ejecución del plan", "Authorize execution of the plan"), sf: T("puerta explícita antes de aplicar", "explicit gate before applying"), fio: T("Plan con todas sus decisiones → confirmación o ajuste", "Plan with all its decisions → confirmation or adjustment") },
          a5: { lf: T("Ejecutar el plan como transacción", "Execute the plan as a transaction"), sf: T("vista previa → aplicada, con diario", "preview → applied, with journal"), fio: T("Plan confirmado → documentos modificados y diario anotado", "Confirmed plan → modified documents and journal noted"), lt: "CapabilityLayerTransactionService", st: T("preview / apply / rollback + journal", "preview / apply / rollback + journal"), tx: T("CapabilityLayerTransactionService aplica el plan y escribe un journal append-only con el ETag de cada documento.", "CapabilityLayerTransactionService applies the plan and writes an append-only journal with each document's ETag.") },
          d2: { lf: T("Valorar si procede deshacer", "Assess whether to undo"), sf: T("opción tras observar el resultado", "option after observing the result"), fio: T("Resultado aplicado → conservar o revertir", "Applied result → keep or revert") },
          a6: { lf: T("Invertir los cambios de la transacción", "Reverse the transaction's changes"), sf: T("aplicada → revertida, por versión", "applied → rolled back, by version"), fio: T("Transacción aplicada → documentos restituidos sin copia completa", "Applied transaction → documents restored without a full copy") },
          r: { lf: T("Capas integradas y trazadas", "Layers integrated and traced"), sf: T("resultado reversible con diario", "reversible result with journal"), fio: T("Transacción aplicada → capas vigentes más diario para revertir", "Applied transaction → current layers plus journal for rollback") }
        }
      },

      /* ------------------------------------------------------------------ portability_revert */
      portability_revert: {
        nf: T("Reversión dirigida de una transacción aplicada", "Targeted rollback of an applied transaction"),
        df: T("Se lee el diario para conocer documentos y versiones afectados; si todos siguen en la versión aplicada se deshace solo lo que cambió la transacción, y si otro cambio los modificó la reversión se rechaza.",
              "The journal is read to know the affected documents and versions; if all remain at the applied version only what the transaction changed is undone, and if another change modified them the rollback is refused."),
        nt: T("Rollback por ETag desde el journal", "Rollback by ETag from the journal"),
        dt: T("La reversión se apoya en el journal append-only y en el ETag de cada documento dentro de CapabilityLayerTransactionService. No se ha localizado un enum de estados de la transacción.",
              "Rollback relies on the append-only journal and on each document's ETag within CapabilityLayerTransactionService. No transaction-state enum was located."),
        nodes: {
          a: { lf: T("Identificar documentos y versiones afectados", "Identify affected documents and versions"), sf: T("lectura del diario de la transacción", "reading the transaction journal"), fio: T("Transacción a deshacer → documentos con la versión que dejó", "Transaction to undo → documents with the version it left behind") },
          b: { lf: T("Verificar que no hay cambios posteriores", "Verify no later changes exist"), sf: T("versión vigente = versión aplicada", "current version = applied version"), fio: T("Versión vigente y registrada → reversión posible o rechazada", "Current and recorded version → rollback possible or refused") },
          c: { lf: T("Restituir únicamente lo modificado", "Restore only what was modified"), sf: T("aplicada → revertida, por versión", "applied → rolled back, by version"), fio: T("Cambios registrados → documentos devueltos a su estado previo", "Recorded changes → documents returned to their previous state") },
          r: { lf: T("Estado previo recuperado", "Previous state recovered"), sf: T("equivalente al anterior a aplicar", "equivalent to the pre-apply state"), fio: T("Reversión completada → proyecto como antes de la transacción", "Completed rollback → project as before the transaction") }
        }
      },

      /* ------------------------------------------------------------------ portability_author_export */
      portability_author_export: {
        nf: T("Composición de paquete desde la selección de capas", "Package composition from layer selection"),
        df: T("Se eligen las clases de capa a llevar, el sistema añade las capas de las que dependen y se genera el paquete portable y versionado.",
              "The layer kinds to carry are chosen, the system adds the layers they depend on and the portable, versioned package is generated."),
        nt: T("Módulo CAPABILITIES → CapabilityPackageArchiveService", "CAPABILITIES module → CapabilityPackageArchiveService"),
        dt: T("El módulo de navegación CAPABILITIES es el frontend de CapabilityPackageArchiveService. El detalle de pantalla procede de datos heredados y no se verificó en componentes.",
              "The CAPABILITIES navigation module is the frontend of CapabilityPackageArchiveService. Screen detail comes from legacy data and was not verified in components."),
        nodes: {
          a: { lf: T("Seleccionar clases de capa", "Select layer kinds"), sf: T("metamodelo, gramática, editor…", "metamodel, grammar, editor…"), fio: T("Capas activas del proyecto → subconjunto elegido para el paquete", "Active project layers → subset chosen for the package") },
          b: { lf: T("Cerrar dependencias entre capas", "Close dependencies between layers"), sf: T("selección → selección completa", "selection → complete selection"), fio: T("Capas elegidas → conjunto con dependencias transitivas incluidas", "Chosen layers → set with transitive dependencies included") },
          c: { lf: T("Emitir el paquete portable", "Emit the portable package"), sf: T("versión y compatibilidad declaradas", "declared version and compatibility"), fio: T("Conjunto cerrado de capas → archivo portable", "Closed set of layers → portable file") },
          r: { lf: T("Paquete versionado disponible", "Versioned package available"), sf: T("importable en otro proyecto", "importable in another project"), fio: T("Emisión completada → paquete listo para fusión en destino", "Completed emission → package ready for merging at the destination") }
        }
      },

      /* ------------------------------------------------------------------ portability_author_merge */
      portability_author_merge: {
        nf: T("Incorporación de un paquete sobre capas existentes", "Incorporating a package over existing layers"),
        df: T("Se revisa el paquete (hallazgos graduados), se comprueba si choca con capas existentes y, de haber conflictos, se previsualiza y se decide cada uno; con el plan confirmado se aplica como transacción reversible.",
              "The package is reviewed (graded findings), it is checked whether it clashes with existing layers and, if there are conflicts, each is previewed and decided; with the plan confirmed it is applied as a reversible transaction."),
        nt: T("Revisión → fusión semántica → transacción de capa", "Review → semantic merge → layer transaction"),
        dt: T("Combina la validación del archivo (CapabilityPackageArchiveService, con gravedades ERROR/WARNING/INFO), CapabilitySemanticMergeService y CapabilityLayerTransactionService. El detalle de pantalla procede de datos heredados.",
              "Combines archive validation (CapabilityPackageArchiveService, with ERROR/WARNING/INFO severities), CapabilitySemanticMergeService and CapabilityLayerTransactionService. Screen detail comes from legacy data."),
        nodes: {
          a: { lf: T("Evaluar la integridad del paquete", "Assess package integrity"), sf: T("hallazgos por gravedad", "findings by severity"), fio: T("Paquete entrante → lista de hallazgos ERROR, WARNING o INFO", "Incoming package → list of ERROR, WARNING or INFO findings") },
          b: { lf: T("Determinar si existe solapamiento", "Determine whether overlap exists"), sf: T("choque con capas del destino", "clash with target layers"), fio: T("Paquete y capas presentes → con conflictos o sin ellos", "Package and present layers → with or without conflicts") },
          c: { lf: T("Establecer la decisión de cada conflicto", "Establish the decision for each conflict"), sf: T("vista previa + una decisión por conflicto", "preview + one decision per conflict"), fio: T("Conflictos previsualizados → plan con una decisión por conflicto", "Previewed conflicts → plan with one decision per conflict") },
          d: { lf: T("Aplicar el plan confirmado", "Apply the confirmed plan"), sf: T("transacción con reversión disponible", "transaction with rollback available"), fio: T("Plan confirmado → documentos modificados y diario", "Confirmed plan → modified documents and journal") },
          r: { lf: T("Capas integradas con reversión disponible", "Layers integrated with rollback available"), sf: T("resultado trazable", "traceable result"), fio: T("Transacción aplicada → capas vigentes y diario para revertir", "Applied transaction → current layers and journal for rollback") }
        }
      },

      /* ------------------------------------------------------------------ persistence_save_etag */
      persistence_save_etag: {
        nf: T("Guardado con concurrencia optimista y rastro", "Save with optimistic concurrency and trace"),
        df: T("Una capacidad solicita guardar un documento; se contrasta la versión de partida con la vigente. Si coinciden se escribe y la versión avanza de n a n+1 y se anota en la auditoría; si no, hay choque y el guardado se rechaza sin perder nada.",
              "A capability requests saving a document; the starting version is compared with the current one. If they match it is written and the version advances from n to n+1 and is noted in the audit; if not, there is a clash and the save is rejected without losing anything."),
        nt: T("CanonicalDocumentStore (ETag) → observador de auditoría", "CanonicalDocumentStore (ETag) → audit observer"),
        dt: T("CanonicalDocumentStore compara el ETag; si coincide escribe el JSON y el observador de auditoría conectado al almacén anota el cambio. La fábrica LocalCanonicalDocumentStoreFactory crea el almacén; no se documentan firmas ni códigos de error del rechazo.",
              "CanonicalDocumentStore compares the ETag; if it matches it writes the JSON and the audit observer attached to the store notes the change. LocalCanonicalDocumentStoreFactory creates the store; no signatures nor error codes for the rejection are documented."),
        nodes: {
          a: { lf: T("Obtener la versión vigente del documento", "Obtain the document's current version"), sf: T("comparar con la versión de partida", "compare with the starting version"), fio: T("Documento y versión de partida → versión vigente para contraste", "Document and starting version → current version for comparison"), lt: "CanonicalDocumentStore", st: T("lectura de ETag", "ETag read") },
          d: { lf: T("Contrastar versión de partida y vigente", "Compare starting and current version"), sf: T("sin cambios ajenos intermedios", "no intervening changes by others"), fio: T("Ambas versiones → guardado admisible o choque", "Both versions → admissible save or clash") },
          b: { lf: T("Persistir el documento y avanzar versión", "Persist the document and advance the version"), sf: T("versión n → versión n+1", "version n → version n+1"), fio: T("Documento modificado → documento almacenado con versión nueva", "Modified document → stored document with new version") },
          c: { lf: T("Notificar el cambio al registro", "Notify the change to the log"), sf: T("efecto hacia la auditoría", "effect towards the audit"), fio: T("Guardado aceptado → mutación entregada al observador", "Accepted save → mutation delivered to the observer") },
          x: { lf: T("Denegar el guardado por choque", "Deny the save on clash"), sf: T("conserva el estado vigente", "keeps the current state"), fio: T("Versiones discordantes → guardado rechazado, nada perdido", "Mismatched versions → save rejected, nothing lost") },
          r: { lf: T("Estado persistido y registrado", "Persisted and recorded state"), sf: T("versión nueva con rastro", "new version with trace"), fio: T("Guardado aceptado → documento vigente y entrada de auditoría", "Accepted save → current document and audit entry") }
        }
      },

      /* ------------------------------------------------------------------ audit_record_mutation */
      audit_record_mutation: {
        nf: T("Registro automático de una mutación", "Automatic recording of a mutation"),
        df: T("Un guardado dispara al observador, que genera una entrada con el documento y el momento (sin el contenido) y la añade al registro, donde permanece inmutable y consultable para depurar o demostrar procedencia.",
              "A save triggers the observer, which generates an entry with the document and moment (without the content) and appends it to the log, where it remains immutable and searchable to debug or prove provenance."),
        nt: T("AuditCanonicalDocumentMutationObserver → JsonlAuditLogStore", "AuditCanonicalDocumentMutationObserver → JsonlAuditLogStore"),
        dt: T("El observador, conectado a cada almacén por la fábrica local, entrega la entrada a JsonlAuditLogStore, que añade una línea JSONL. Quién y cómo se rellena el autor no está documentado en el código leído.",
              "The observer, attached to every store by the local factory, hands the entry to JsonlAuditLogStore, which appends a JSONL line. How the author is filled in is not documented in the code read."),
        nodes: {
          a: { lf: T("Captar la mutación del documento", "Capture the document mutation"), sf: T("disparo automático sin política", "automatic trigger without policy"), fio: T("Guardado aceptado → mutación conocida por el mecanismo de auditoría", "Accepted save → mutation known to the audit mechanism"), lt: "AuditCanonicalDocumentMutationObserver", st: T("observador por almacén", "observer per store"), tx: T("Está conectado a cada almacén de documentos mediante la fábrica local.", "It is attached to every document store through the local factory.") },
          b: { lf: T("Formular el hecho de cambio", "Formulate the fact of change"), sf: T("documento + momento, sin contenido", "document + moment, no content"), fio: T("Mutación captada → entrada de auditoría con identidad y momento", "Captured mutation → audit entry with identity and moment") },
          c: { lf: T("Incorporar la entrada a la secuencia", "Incorporate the entry into the sequence"), sf: T("solo crece; nunca se reescribe", "only grows; never rewritten"), fio: T("Entrada formulada → registro ampliado con una entrada más", "Formulated entry → log extended by one more entry"), lt: "JsonlAuditLogStore", st: T("append de línea JSONL", "JSONL line append") },
          r: { lf: T("Hecho de cambio consultable", "Searchable fact of change"), sf: T("para depuración y procedencia", "for debugging and provenance"), fio: T("Entrada añadida → evidencia permanente del cambio", "Appended entry → permanent evidence of the change") }
        }
      },

      /* ------------------------------------------------------------------ security_filter_sandbox */
      security_filter_sandbox: {
        nf: T("Admisión de peticiones y confinamiento de ejecución externa", "Request admission and external execution confinement"),
        df: T("Primero se contrasta cada petición con las políticas de forma y tamaño; solo las aceptadas siguen. Si la operación requiere un programa externo se lanza confinado y se comprueba que exista: si falta se declara no disponible sin simular; en otro caso el resultado acotado llega a la capacidad.",
              "Each request is first checked against the shape and size policies; only accepted ones continue. If the operation requires an external program it is launched confined and its existence is checked: if missing it is declared unavailable without simulation; otherwise the bounded result reaches the capability."),
        nt: T("HydraRequestHardeningFilter → HydraExternalProcessRunner", "HydraRequestHardeningFilter → HydraExternalProcessRunner"),
        dt: T("HydraRequestHardeningFilter filtra la petición y HydraExternalProcessRunner lanza el proceso con sandbox. Los límites numéricos y la exclusividad de este punto no se verificaron en esta revisión.",
              "HydraRequestHardeningFilter filters the request and HydraExternalProcessRunner launches the process in a sandbox. The numeric limits and the exclusivity of this point were not verified in this review."),
        nodes: {
          a: { lf: T("Acotar la petición por forma y tamaño", "Bound the request by shape and size"), sf: T("admisión previa a toda capacidad", "admission before any capability"), fio: T("Petición entrante → petición evaluada frente a las políticas", "Incoming request → request evaluated against the policies"), lt: "HydraRequestHardeningFilter", st: T("filtro de endurecimiento de peticiones", "request hardening filter"), tx: T("Aplica las políticas de forma y tamaño antes de que la petición alcance cualquier capacidad.", "Applies the shape and size policies before the request reaches any capability.") },
          d1: { lf: T("Admitir o rechazar la petición", "Admit or reject the request"), sf: T("cumple o no las políticas", "meets the policies or not"), fio: T("Petición evaluada → aceptada hacia la capacidad o rechazada", "Evaluated request → accepted towards the capability or rejected") },
          d2: { lf: T("Determinar si requiere ejecución externa", "Determine whether external execution is needed"), sf: T("solo algunas operaciones", "only some operations"), fio: T("Petición aceptada → ruta directa o ruta con proceso externo", "Accepted request → direct path or path with external process") },
          b: { lf: T("Confinar la ejecución del programa", "Confine the program run"), sf: T("entorno mínimo, salida con tope, apagado", "minimal environment, capped output, shutdown"), fio: T("Programa y argumentos → resultado acotado de una ejecución aislada", "Program and arguments → bounded result of an isolated run"), lt: "HydraExternalProcessRunner", st: T("ejecutor con sandbox", "sandboxed runner"), tx: T("Único punto de ejecución de procesos externos según la base heredada; entorno mínimo, salida acotada y apagado garantizado.", "Single point of external process execution per the legacy base; minimal environment, bounded output and guaranteed teardown.") },
          d3: { lf: T("Comprobar la presencia del motor externo", "Check the presence of the external engine"), sf: T("existe o se declara ausente", "exists or is declared absent"), fio: T("Programa solicitado → ejecutable disponible o motor ausente", "Requested program → available executable or absent engine") },
          c: { lf: T("Traspasar el resultado a la capacidad", "Hand the result to the capability"), sf: T("entrada ya acotada y comprobada", "input already bounded and checked"), fio: T("Petición y resultado acotados → capacidad destinataria", "Bounded request and result → recipient capability") },
          x: { lf: T("Cerrar con rechazo o ausencia declarada", "Close with rejection or declared absence"), sf: T("sin simular resultados", "without simulating results"), fio: T("Petición inválida o motor ausente → rechazo o estado «no disponible»", "Invalid request or absent engine → rejection or \"not available\" state"), tx: T("La ausencia se expresa con la etiqueta compartida TOOL_UNAVAILABLE.", "The absence is expressed with the shared TOOL_UNAVAILABLE label.") }
        }
      }
    }
  });
})();
