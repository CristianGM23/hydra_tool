/* HYDRA portal — audience lens (FUNCTIONAL + TECHNICAL projections) for group 'definition': metamodel + model_editor. */
(function(){ "use strict"; var HM = window.HM, T = HM.T;
  // variant: V(k, fdEs, fdEn [, tdEs, tdEn])
  function V(k, fe, fn, te, tn){ var o = { fd: T(fe, fn) }; if (k) o.k = k; if (te) o.td = T(te, tn); return o; }
  // node: N(lfEs, lfEn, sfEs, sfEn, fioEs, fioEn [, tech]) with tech = { lt: str|[es,en], st: [es,en], tx: [es,en] }
  function N(le, ln, se, sn, ioe, ion, t){
    var o = { lf: T(le, ln), sf: T(se, sn), fio: T(ioe, ion) };
    if (t) {
      if (t.lt) o.lt = typeof t.lt === "string" ? t.lt : T(t.lt[0], t.lt[1]);
      if (t.st) o.st = T(t.st[0], t.st[1]);
      if (t.tx) o.tx = T(t.tx[0], t.tx[1]);
    }
    return o;
  }
  function L(e, n){ return T(e, n); }

  var J = "";
  HM.lens({
    caps: {
      /* ================================================================ METAMODELO */
      metamodel: {
        func: {
          title: L("Lenguaje de modelado: tipos, estructura, relaciones y comportamiento declarados", "Modelling language: declared types, structure, relations and behaviour"),
          summary: L("Un metamodelo es un conjunto cerrado de clasificadores (clases, enumerados, tipos de datos) más las relaciones que los conectan; cada clase posee atributos con multiplicidad y operaciones con una implementación opcional. Las relaciones son entidades de primer nivel del metamodelo, no campos de la clase de origen, de modo que la herencia es una relación más sometida a reglas de coherencia. No existen estados de borrador ni de publicación: un metamodelo o está guardado y es consistente, o no está guardado, y su guardado deja una marca de validez que habilita la generación.",
            "A metamodel is a closed set of classifiers (classes, enumerations, data types) plus the relations that connect them; each class owns attributes with multiplicity and operations with an optional implementation. Relations are first-class entities of the metamodel, not fields of the source class, so inheritance is just another relation subject to coherence rules. There are no draft or published states: a metamodel is either saved and consistent, or not saved, and saving leaves a validity mark that enables generation."),
          inputs: [L("Conceptos del dominio y sus vínculos", "Domain concepts and their links"), L("Definiciones externas de un lenguaje (21 formatos con fidelidad exacta, semántica o estructural)", "External language definitions (21 formats with exact, semantic or structural fidelity)"), L("Proyecciones desde gramáticas y materializaciones desde proyectos recuperados", "Projections from grammars and materializations from recovered projects"), L("Órdenes de borrado de clases o del metamodelo entero", "Orders to delete classes or the whole metamodel")],
          outputs: [L("Metamodelo consistente con marca de validez", "Consistent metamodel with a validity mark"), L("Estructura de tipos sobre la que se definen editores, reglas y transformaciones", "Type structure on which editors, rules and transformations are defined"), L("Informe de limpieza en cascada con diario de recuperación", "Cascade clean-up report with a recovery journal"), L("Aptitud para generar código del lenguaje", "Readiness to generate code for the language")],
          conditions: [L("Un error de consistencia impide guardar", "A consistency error prevents saving"), L("Implementación solo de clase hacia interfaz; herencia solo entre clasificadores del mismo tipo y sin ciclos", "Implementation only from class to interface; inheritance only between like classifiers and without cycles"), L("Una operación sin implementar no bloquea el guardado", "An unimplemented operation does not block saving"), L("La generación exige que el contenido coincida con la marca de validez", "Generation requires the content to match the validity mark"), L("Al borrar una clase se limpian en cascada las definiciones que la usan", "Deleting a class cascades a clean-up of the definitions that use it")]
        },
        tech: {
          title: L("Servicio de aplicación del metamodelo canónico (MetaModel schemaVersion 2.1)", "Canonical metamodel application service (MetaModel schemaVersion 2.1)"),
          summary: L("CanonicalMetamodelApplicationService guarda tras una validación bloqueante (prepareForSave) delegada en MetamodelValidationApplicationService; MetamodelDependentArtifactCleanupService limpia los artefactos dependientes y MetamodelDeletionJournal registra el borrado. Expuesto por REST bajo /metamodels (CRUD con validación embebida) sobre el esquema MetaModel con MetaClass, MetaAttribute, MetaRelation, MetaOperation y OperationBinding; la preparación para generar compara una huella (fingerprint) del contenido y la importación dispone de un registro de 21 formatos (MetamodelImportFormat).",
            "CanonicalMetamodelApplicationService saves after a blocking validation (prepareForSave) delegated to MetamodelValidationApplicationService; MetamodelDependentArtifactCleanupService cleans dependent artifacts and MetamodelDeletionJournal records the deletion. Exposed over REST under /metamodels (CRUD with embedded validation) on the MetaModel schema with MetaClass, MetaAttribute, MetaRelation, MetaOperation and OperationBinding; generation readiness compares a content fingerprint and import has a registry of 21 formats (MetamodelImportFormat).")
        },
        elements: {
          metamodel: {
            nf: L("Metamodelo (lenguaje completo de un dominio)", "Metamodel (complete language of a domain)"),
            fm: L("Raíz del lenguaje: contiene listas planas de clases, enumerados, tipos de datos y relaciones, con cardinalidad 0..N cada una. Carece de ciclo editorial: su única transición es de no guardado a guardado y exige consistencia para hacerla.", "Root of the language: holds flat lists of classes, enumerations, data types and relations, each 0..N. It has no editorial cycle: its only transition is from unsaved to saved, and consistency is required to make it."),
            tm: L("Documento MetaModel (schemaVersion 2.1) con listas planas classes, enums, dataTypes y relations.", "MetaModel document (schemaVersion 2.1) with flat lists classes, enums, dataTypes and relations.")
          },
          "class": {
            nf: L("Clase (clasificador estructural con atributos y operaciones)", "Class (structural classifier with attributes and operations)"),
            fm: L("Concepto nominal del lenguaje que agrupa 0..N atributos y 0..N operaciones. Se conecta con otras clases solo a través de relaciones del metamodelo y su ciclo termina en un borrado que arrastra a sus dependientes.", "Nominal concept of the language grouping 0..N attributes and 0..N operations. It connects with other classes only through metamodel relations and its life ends in a deletion that drags its dependents along."),
            tm: L("Registro MetaClass con campo kind de tipo MetaClassKind (CLASS, ABSTRACT_CLASS, INTERFACE).", "MetaClass record with a kind field of type MetaClassKind (CLASS, ABSTRACT_CLASS, INTERFACE)."),
            variants: [
              V("CLASS", "Clasificador instanciable: de ella se crean elementos en los modelos.", "Instantiable classifier: elements of the models are created from it."),
              V("ABSTRACT_CLASS", "Clasificador no instanciable que solo aporta estructura compartida a través de la herencia.", "Non-instantiable classifier that only contributes shared structure through inheritance."),
              V("INTERFACE", "Contrato sin estado propio; solo una clase puede implementarlo y la herencia con él exige otra interfaz.", "Contract without its own state; only a class can implement it and inheritance with it requires another interface.")
            ]
          },
          attribute: {
            nf: L("Atributo (propiedad de valor tipada con multiplicidad)", "Attribute (typed value property with multiplicity)"),
            fm: L("Propiedad no relacional de una clase: pertenece a exactamente una clase, tiene un tipo de valor y una multiplicidad, y puede ser ordenada, única y llevar un valor inicial compatible con su tipo. Se modifica siempre fuera del nodo gráfico.", "Non-relational property of a class: it belongs to exactly one class, has a value type and a multiplicity, and may be ordered, unique and carry an initial value compatible with its type. It is always modified outside the graphic node."),
            tm: L("MetaAttribute(type: MetaTypeReference, multiplicity, ordered, unique, defaultValue).", "MetaAttribute(type: MetaTypeReference, multiplicity, ordered, unique, defaultValue)."),
            variants: [
              V("PRIMITIVE", "El valor es uno de los 14 tipos predefinidos del lenguaje.", "The value is one of the 14 predefined types of the language."),
              V("ENUM", "El valor se restringe a los literales de un enumerado declarado en el mismo metamodelo.", "The value is restricted to the literals of an enumeration declared in the same metamodel."),
              V("DATATYPE", "El valor sigue un tipo de datos con nombre propio, apoyado en un tipo básico.", "The value follows a named data type built on a primitive type."),
              V("CLASS", "El valor es una instancia de una clase del metamodelo usada como tipo.", "The value is an instance of a metamodel class used as a type."),
              V("EXTERNAL", "El tipo se define fuera del metamodelo y solo se referencia.", "The type is defined outside the metamodel and is merely referenced.")
            ]
          },
          primitive_types: {
            nf: L("Tipos básicos (catálogo cerrado de 14 valores)", "Primitive types (closed catalogue of 14 values)"),
            fm: L("Conjunto fijo y no ampliable de tipos escalares entre los que un atributo escoge exactamente uno. Cualquier necesidad fuera del catálogo se resuelve con un enumerado, un tipo de datos propio o una clase.", "Fixed, non-extensible set of scalar types from which an attribute picks exactly one. Any need outside the catalogue is solved with an enumeration, a custom data type or a class."),
            tm: L("Enumerado MetaPrimitiveType con 14 constantes.", "MetaPrimitiveType enumeration with 14 constants."),
            variants: [
              V("STRING", "Texto de longitud libre.", "Free-length text."), V("BOOLEAN", "Dos valores lógicos.", "Two logical values."), V("BYTE", "Entero de rango mínimo.", "Smallest-range integer."), V("SHORT", "Entero de rango reducido.", "Reduced-range integer."),
              V("INTEGER", "Entero de uso general.", "General-purpose integer."), V("LONG", "Entero de rango amplio.", "Wide-range integer."), V("FLOAT", "Real en coma flotante de precisión simple.", "Single-precision floating-point real."), V("DOUBLE", "Real en coma flotante de precisión doble.", "Double-precision floating-point real."),
              V("DECIMAL", "Real exacto, adecuado para importes sin error de redondeo.", "Exact real, suited to amounts without rounding error."), V("BIG_INTEGER", "Entero sin cota de tamaño.", "Integer with no size bound."), V("CHARACTER", "Un único símbolo.", "A single symbol."), V("DATE", "Instante o día del calendario.", "Calendar day or instant."),
              V("BINARY", "Carga opaca de bytes.", "Opaque byte payload."), V("OBJECT", "Valor sin tipo declarado, comodín.", "Value with no declared type, wildcard.")
            ]
          },
          enumeration: {
            nf: L("Enumerado (dominio cerrado de literales con nombre)", "Enumeration (closed domain of named literals)"),
            fm: L("Tipo de valor definido por extensión: una lista de 1..N literales. Lo utilizan 0..N atributos y nunca puede quedar vacío; su nombre identifica el tipo dentro del metamodelo.", "Value type defined by extension: a list of 1..N literals. 0..N attributes use it and it can never be empty; its name identifies the type within the metamodel."),
            tm: L("Registro MetaEnum en la lista metamodel.enums.", "MetaEnum record in the metamodel.enums list.")
          },
          datatype: {
            nf: L("Tipo de datos propio (tipo de valor nominal sobre uno básico)", "Custom data type (nominal value type over a primitive one)"),
            fm: L("Tipo de valor nombrado que da un significado de dominio a un valor escalar compartido por varios atributos. Pertenece al metamodelo (0..N) y su nombre no puede repetirse.", "Named value type that gives a domain meaning to a scalar value shared by several attributes. It belongs to the metamodel (0..N) and its name cannot be repeated."),
            tm: L("Registro MetaDataType en la lista metamodel.dataTypes.", "MetaDataType record in the metamodel.dataTypes list.")
          },
          relation: {
            nf: L("Relación (vínculo dirigido entre dos clases con multiplicidades)", "Relation (directed link between two classes with multiplicities)"),
            fm: L("Entidad del metamodelo que une exactamente dos clases, con una multiplicidad en cada extremo, y que puede tener una relación inversa (0..1) y ser ordenada o única. Su clase determina la semántica del vínculo y su validez se comprueba en cuanto se crea.", "Metamodel entity joining exactly two classes, with a multiplicity at each end, which may have an opposite relation (0..1) and be ordered or unique. Its kind determines the link's semantics and its validity is checked as soon as it is created."),
            tm: L("MetaRelation(kind: MetaRelationKind, sourceMultiplicity, targetMultiplicity, oppositeRelationId, ordered, unique).", "MetaRelation(kind: MetaRelationKind, sourceMultiplicity, targetMultiplicity, oppositeRelationId, ordered, unique)."),
            variants: [
              V(null, "Vínculo de navegación sin propiedad: el destino existe con independencia del origen.", "Navigation link without ownership: the target exists independently of the source."),
              V(null, "Propiedad fuerte: el contenido nace y muere con el contenedor, lo que define el anidado de los elementos.", "Strong ownership: the content is born and dies with the container, which defines how elements nest."),
              V(null, "Pertenencia débil: el todo agrupa partes que sobreviven sin él.", "Weak membership: the whole groups parts that survive without it."),
              V(null, "Uso sin posesión: el origen requiere al destino para funcionar.", "Use without ownership: the source requires the target in order to work."),
              V(null, "Especialización: exige extremos del mismo tipo (clase con clase, interfaz con interfaz) y prohíbe ciclos.", "Specialization: requires ends of the same kind (class with class, interface with interface) and forbids cycles."),
              V(null, "Realización: solo es válida de una clase hacia una interfaz.", "Realization: valid only from a class to an interface.")
            ]
          },
          multiplicity: {
            nf: L("Multiplicidad (cota inferior y superior de cardinalidad)", "Multiplicity (lower and upper cardinality bound)"),
            fm: L("Par de cotas, mínimo obligatorio y máximo opcional (ausente = ilimitado), que se aplica a cada atributo y a cada extremo de relación. El máximo nunca puede ser inferior al mínimo.", "Pair of bounds, mandatory minimum and optional maximum (absent = unbounded), applied to each attribute and each relation end. The maximum can never be lower than the minimum."),
            tm: L("Multiplicity(lowerBound:int, upperBound:Integer con null = ilimitado) con factorías optional, one, many y unbounded.", "Multiplicity(lowerBound:int, upperBound:Integer with null = unbounded) with factories optional, one, many and unbounded."),
            variants: [
              V("0..1", "Presencia opcional con a lo sumo un valor.", "Optional presence with at most one value."),
              V("1..1", "Presencia obligatoria con exactamente un valor.", "Mandatory presence with exactly one value."),
              V("0..*", "Colección sin mínimo ni máximo.", "Collection with no minimum or maximum."),
              V(null, "Mínimo fijado y máximo ausente, es decir, ilimitado.", "Fixed minimum with an absent, that is unbounded, maximum."),
              V(null, "Cualquier pareja de cotas coherente, como 2..5.", "Any coherent pair of bounds, such as 2..5.")
            ]
          },
          operation: {
            nf: L("Operación (firma de comportamiento de una clase)", "Operation (behaviour signature of a class)"),
            fm: L("Declaración de comportamiento con nombre, parámetros, retorno y excepciones que pertenece a una clase y tiene a lo sumo una implementación. Estructura y comportamiento evolucionan por separado y solo una operación implementada es ejecutable.", "Behaviour declaration with name, parameters, return and exceptions that belongs to a class and has at most one implementation. Structure and behaviour evolve separately and only an implemented operation is executable."),
            tm: L("MetaOperation con MetaOperationBehaviorKind; su implementación vive en OperationBinding.", "MetaOperation with MetaOperationBehaviorKind; its implementation lives in OperationBinding."),
            variants: [
              V("UNSPECIFIED", "No se declara el efecto sobre el modelo.", "The effect on the model is not declared."),
              V("QUERY", "Contrato de solo lectura: no altera el modelo.", "Read-only contract: it does not alter the model."),
              V("COMMAND", "Contrato con efectos: puede modificar el modelo.", "Contract with side effects: it may modify the model.")
            ]
          },
          binding: {
            nf: L("Implementación de operación (vínculo firma–comportamiento)", "Operation implementation (signature-to-behaviour link)"),
            fm: L("Asociación de una operación con la forma en que se ejecuta, guardada aparte de la estructura. Pasa de sin implementar a implementada, y cada forma rellena solo el campo que le corresponde.", "Association of an operation with how it runs, stored apart from the structure. It moves from unimplemented to implemented, and each kind fills only the field that corresponds to it."),
            tm: L("OperationBinding(operationId, implementationKind, expressionId, actionId, externalReference).", "OperationBinding(operationId, implementationKind, expressionId, actionId, externalReference)."),
            variants: [
              V("UNIMPLEMENTED", "Estado inicial: la operación existe pero carece de efecto.", "Initial state: the operation exists but has no effect."),
              V("EXPRESSION", "Resultado derivado evaluando una expresión del lenguaje de expresiones.", "Result derived by evaluating an expression of the expression language."),
              V("ACTION", "Comportamiento delegado en una acción declarada en otro artefacto.", "Behaviour delegated to an action declared in another artifact."),
              V("EXTERNAL", "Comportamiento delegado en una referencia a un recurso ajeno al lenguaje.", "Behaviour delegated to a reference to a resource outside the language.")
            ]
          },
          import_format: {
            nf: L("Formato de importación (origen reconocible de un lenguaje)", "Import format (recognisable source of a language)"),
            fm: L("Cada uno de los 21 orígenes admitidos, clasificado por categoría y por nivel de fidelidad: EXACT recupera todo lo que el formato expresa, SEMANTIC conserva el significado y STRUCTURAL solo la forma. Una importación usa exactamente un formato y propone 0..N clases, enumerados y relaciones.", "Each of the 21 supported sources, classified by category and fidelity level: EXACT recovers everything the format expresses, SEMANTIC keeps the meaning and STRUCTURAL only the shape. An import uses exactly one format and proposes 0..N classes, enumerations and relations."),
            tm: L("Registro MetamodelImportFormat de 21 entradas (id, categoría, MetamodelImportSupportLevel).", "MetamodelImportFormat registry of 21 entries (id, category, MetamodelImportSupportLevel)."),
            variants: [
              V(null, "Modelo de ecosistema EMF con fidelidad EXACT.", "EMF ecosystem model with EXACT fidelity."), V(null, "Modelo EMF en general, con fidelidad SEMANTIC.", "General EMF model, with SEMANTIC fidelity."),
              V(null, "Intercambio de modelos con fidelidad EXACT.", "Model interchange with EXACT fidelity."), V(null, "Datos estructurados con fidelidad EXACT.", "Structured data with EXACT fidelity."),
              V(null, "Datos en marcado con fidelidad SEMANTIC.", "Markup data with SEMANTIC fidelity."), V(null, "Diagrama; fidelidad SEMANTIC.", "Diagram; SEMANTIC fidelity."),
              V(null, "Gramática declarativa; fidelidad STRUCTURAL.", "Declarative grammar; STRUCTURAL fidelity."), V(null, "Gramática declarativa; fidelidad STRUCTURAL.", "Declarative grammar; STRUCTURAL fidelity."),
              V(null, "Gramática declarativa; fidelidad STRUCTURAL.", "Declarative grammar; STRUCTURAL fidelity."), V(null, "Esquema relacional; fidelidad SEMANTIC.", "Relational schema; SEMANTIC fidelity."),
              V(null, "Código fuente; fidelidad SEMANTIC.", "Source code; SEMANTIC fidelity."), V(null, "Código fuente; fidelidad SEMANTIC.", "Source code; SEMANTIC fidelity."),
              V(null, "Código fuente; solo la forma, STRUCTURAL.", "Source code; shape only, STRUCTURAL."), V(null, "Código fuente; fidelidad SEMANTIC.", "Source code; SEMANTIC fidelity."),
              V(null, "Código fuente; fidelidad SEMANTIC.", "Source code; SEMANTIC fidelity."), V(null, "Bloques visuales serializados; STRUCTURAL.", "Serialized visual blocks; STRUCTURAL."),
              V(null, "Bloques visuales generados; STRUCTURAL.", "Generated visual blocks; STRUCTURAL."), V(null, "Proyecto de editor gráfico; STRUCTURAL.", "Graphical editor project; STRUCTURAL."),
              V(null, "Documentación diagramática; STRUCTURAL.", "Diagrammatic documentation; STRUCTURAL."), V(null, "Documentación textual; STRUCTURAL.", "Textual documentation; STRUCTURAL."),
              V(null, "Documentación dirigida a agentes; STRUCTURAL.", "Agent-oriented documentation; STRUCTURAL.")
            ]
          },
          deletion: {
            nf: L("Borrado en cascada (transacción con diario de recuperación)", "Cascade deletion (transaction with recovery journal)"),
            fm: L("Proceso derivado que, al eliminar una clase o un metamodelo, retira también las definiciones dependientes (al menos nueve tipos) localizadas por convención de nombre y por referencias internas. Avanza por estados registrados en un diario, de modo que un fallo a medias puede reanudarse.", "Derived process that, when a class or metamodel is removed, also removes the dependent definitions (at least nine kinds) found by naming convention and internal references. It advances through states recorded in a journal, so a midway failure can be resumed."),
            tm: L("MetamodelDeletionJournal con estados PREPARED, PRIMARY_DELETED, RECOVERY_REQUIRED, COMPLETED y ABORTED; limpieza en MetamodelDependentArtifactCleanupService.", "MetamodelDeletionJournal with states PREPARED, PRIMARY_DELETED, RECOVERY_REQUIRED, COMPLETED and ABORTED; clean-up in MetamodelDependentArtifactCleanupService."),
            variants: [
              V("PREPARED", "Intención registrada antes de modificar nada.", "Intent recorded before anything is modified."),
              V("PRIMARY_DELETED", "El elemento principal ya no existe y la limpieza de dependientes está pendiente.", "The primary element no longer exists and dependent clean-up is pending."),
              V("RECOVERY_REQUIRED", "Estado intermedio inconsistente que obliga a reanudar.", "Inconsistent intermediate state that forces a resume."),
              V("COMPLETED", "Estado terminal: principal y dependientes retirados.", "Terminal state: primary and dependents removed."),
              V("ABORTED", "Estado terminal alcanzado desde PREPARED sin efecto alguno.", "Terminal state reached from PREPARED with no effect.")
            ]
          }
        }
      },
      /* ================================================================ EDITOR DE MODELOS */
      model_editor: {
        func: {
          title: L("Definición declarativa de editores y sesiones de edición de modelos", "Declarative editor definition and model-editing sessions"),
          summary: L("Un editor es una definición ligada a un metamodelo: contiene 1..N representaciones, cada una con mapeos que proyectan clases y relaciones sobre nodos y aristas, además de paleta, formularios, capacidades y una política de ejecución. La definición se compila sin alterarse y el resultado abre sesiones, cada una enlazando un modelo, un editor compilado y el último informe de validación. La política decide cuándo se valida, si se guarda solo y qué eventos se emiten; sin editor compilado existe un lienzo básico de respaldo.",
            "An editor is a definition bound to a metamodel: it holds 1..N representations, each with mappings that project classes and relations onto nodes and edges, plus palette, forms, capabilities and a runtime policy. The definition is compiled without being altered and the result opens sessions, each linking a model, a compiled editor and the latest validation report. The policy decides when to validate, whether to autosave and which events to emit; without a compiled editor a basic fallback canvas exists."),
          inputs: [L("Metamodelo guardado del que depende el editor", "Saved metamodel the editor depends on"), L("Decisiones de diseño: vistas, mapeos, paleta, formularios, capacidades y política", "Design decisions: views, mappings, palette, forms, capabilities and policy"), L("Definiciones visuales ajenas (Sirius, GMF, DrawIO) con asociación a un metamodelo", "Foreign visual definitions (Sirius, GMF, DrawIO) with a metamodel association"), L("Modelos nuevos o existentes que se abren para trabajar", "New or existing models opened for work")],
          outputs: [L("Definición de editor válida, guardada y compilada", "Valid, saved and compiled editor definition"), L("Sesión con modelo, vistas abiertas y diagnósticos", "Session with model, open views and diagnostics"), L("Modelos persistidos y su disposición visual, almacenada aparte", "Persisted models and their visual layout, stored apart"), L("Eventos de edición retenidos hasta un guardado correcto cuando no hay guardado automático", "Editing events held until a successful save when autosave is off")],
          conditions: [L("Identificador, metamodelo, al menos una representación y mapeos completos son obligatorios", "Identifier, metamodel, at least one representation and complete mappings are mandatory"), L("Compilar nunca modifica la definición de diseño", "Compiling never modifies the design definition"), L("Por defecto la validación es solo manual; aplicar una corrección exige la capacidad de Quick Fix", "By default validation is manual only; applying a fix requires the Quick Fix capability"), L("Una arista exige relación salvo que sea semántica; una capa por defecto debe estar activa", "An edge requires a relation unless it is semantic; a default layer must be active"), L("Sin publicar eventos no se emite ninguno aunque flujos de eventos y gamificación estén activos", "Without event publishing none is emitted even if event flows and gamification are on")]
        },
        tech: {
          title: L("EditorDefinitionCompiler y EditorModelSessionService sobre modelo canónico", "EditorDefinitionCompiler and EditorModelSessionService over a canonical model"),
          summary: L("La definición es un EditorDefinitionModel (schemaVersion 1.0) con RepresentationDefinition, NodeMapping, EdgeMapping, EditorTool, FormDefinition, EditorRuntimePolicy y CodeGenerationDefinition; EditorDefinitionCompiler la compila y EditorModelSessionService gestiona las sesiones sobre un modelo canónico independiente de EMF (existe un camino heredado EMF/XMI en paralelo). Se publican eventos mediante EditorSessionEventPublisher; la importación visual se resuelve con VisualImportMetamodelResolver. APIs: REST de editor-definition (CRUD y compilación) y REST de sesiones (abrir, mutar, guardar, validar, quick-fix). Discrepancia no resuelta: las banderas de puerta de eventos se escriben en el payload en la sesión pero el publicador las lee de los metadatos.",
            "The definition is an EditorDefinitionModel (schemaVersion 1.0) with RepresentationDefinition, NodeMapping, EdgeMapping, EditorTool, FormDefinition, EditorRuntimePolicy and CodeGenerationDefinition; EditorDefinitionCompiler compiles it and EditorModelSessionService manages sessions over a canonical model independent of EMF (a legacy EMF/XMI path exists in parallel). Events are published through EditorSessionEventPublisher; visual import is resolved with VisualImportMetamodelResolver. APIs: editor-definition REST (CRUD and compile) and session REST (open, mutate, save, validate, quick-fix). Unresolved discrepancy: the event gating flags are written into the payload in the session but the publisher reads them from metadata.")
        },
        elements: {
          editor_def: {
            nf: L("Definición de editor (especificación completa de una experiencia de edición)", "Editor definition (complete specification of an editing experience)"),
            fm: L("Agregado raíz que referencia un metamodelo y contiene 1..N representaciones, 0..N formularios, 0..1 paleta, capacidades, política y generación opcional. Transita de en diseño a guardada a compilada; cada cambio de definición o de metamodelo exige recompilar.", "Root aggregate that references a metamodel and holds 1..N representations, 0..N forms, 0..1 palette, capabilities, policy and optional generation. It moves from in design to saved to compiled; every change of definition or metamodel requires recompiling."),
            tm: L("EditorDefinitionModel con id y metamodelId obligatorios y schemaVersion 1.0; sin capacidades equivale a solo edición de modelos.", "EditorDefinitionModel with mandatory id and metamodelId and schemaVersion 1.0; no capabilities means model editing only.")
          },
          capabilities: {
            nf: L("Capacidades (conmutadores de funciones de sesión)", "Capabilities (session feature switches)"),
            fm: L("Subconjunto de 12 funciones que el editor declara y que habilita la sesión una por una. Se fija en diseño; sin ninguna se asume solo la edición de modelos, y algunas funciones dependen de otras, como la corrección sobre la validación.", "Subset of 12 features the editor declares and the session enables one by one. Fixed at design time; with none, model editing only is assumed, and some features depend on others, such as fixing on validation."),
            tm: L("Enumerado EditorCapability de 12 constantes.", "EditorCapability enumeration of 12 constants."),
            variants: [
              V("MODEL_EDITING", "Crear, conectar y modificar elementos; capacidad base.", "Create, connect and modify elements; base capability."),
              V("MULTIPLE_REPRESENTATIONS", "Mantener abiertas varias vistas del mismo modelo dentro del máximo fijado.", "Keep several views of the same model open within the set maximum."),
              V("OPERATIONS", "Ejecutar las operaciones implementadas del metamodelo.", "Run the metamodel's implemented operations."),
              V("VALIDATION_RUNTIME", "Evaluar reglas sobre el modelo editado y obtener diagnósticos.", "Evaluate rules on the edited model and obtain diagnostics."),
              V("QUICK_FIX_RUNTIME", "Proponer y aplicar correcciones a los diagnósticos, con revalidación posterior.", "Propose and apply fixes to diagnostics, followed by revalidation."),
              V("EVENTS", "Emitir hechos de edición hacia otros mecanismos.", "Emit editing facts toward other mechanisms."),
              V("EVENT_FLOWS", "Permitir que esos hechos desencadenen flujos reactivos.", "Allow those facts to trigger reactive flows."),
              V("GAMIFICATION", "Otorgar puntuación y logros a partir de la actividad de edición.", "Award scoring and achievements from editing activity."),
              V("IMPORT", "Incorporar contenido externo al modelo abierto.", "Bring external content into the open model."),
              V("EXPORT", "Obtener el modelo en formatos externos.", "Obtain the model in external formats."),
              V("CODE_GENERATION", "Producir código a partir del modelo según la definición de generación.", "Produce code from the model according to the generation definition."),
              V("UNDO_REDO", "Revertir y rehacer cambios de la sesión.", "Revert and redo session changes.")
            ]
          },
          representation: {
            nf: L("Representación (proyección visual y de interacción del modelo)", "Representation (visual and interaction projection of the model)"),
            fm: L("Vista de un tipo concreto que arranca desde unas clases raíz y agrupa 0..N mapeos de nodo y de arista, capas, filtros y herramientas visibles. Un editor tiene 1..N, nunca se borra la última y pueden convivir abiertas varias dentro de un máximo configurable.", "View of a concrete kind starting from some root classes and grouping 0..N node and edge mappings, layers, filters and visible tools. An editor has 1..N, the last one is never deleted and several can be open at once up to a configurable maximum."),
            tm: L("RepresentationDefinition(id, name obligatorios; kind por defecto DIAGRAM) con RepresentationKind de seis valores.", "RepresentationDefinition(id, name mandatory; kind defaults to DIAGRAM) with a six-value RepresentationKind."),
            variants: [
              V("DIAGRAM", "Grafo de nodos y aristas; es el tipo asumido cuando no se indica.", "Graph of nodes and edges; the kind assumed when none is stated."),
              V(null, "Contenido organizado por jerarquía de contención.", "Content organised by containment hierarchy."),
              V(null, "Elementos como filas con propiedades por columna.", "Elements as rows with properties per column."),
              V(null, "Edición campo a campo de un solo elemento.", "Field-by-field editing of a single element."),
              V(null, "Cruce de dos conjuntos de elementos.", "Crossing of two sets of elements."),
              V(null, "Resumen agregado de indicadores del modelo.", "Aggregated summary of model indicators.")
            ],
          },
          node_mapping: {
            nf: L("Mapeo de nodo (regla clase → figura)", "Node mapping (class → shape rule)"),
            fm: L("Regla que declara cómo una clase del metamodelo se representa como nodo en una representación: figura, tamaños, etiqueta, imagen y carácter contenedor. Puede llevar 0..N reglas de estilo condicional y su creación genera a la vez la herramienta de creación de nodo asociada.", "Rule declaring how a metamodel class is represented as a node in a representation: shape, sizes, label, image and container nature. It may carry 0..N conditional style rules and creating it also generates the associated create-node tool."),
            tm: L("NodeMapping(id, targetClassId, representationName) con EditorNodeShape y EditorImageFit; alta mediante addClassMappingAndTool.", "NodeMapping(id, targetClassId, representationName) with EditorNodeShape and EditorImageFit; added via addClassMappingAndTool."),
            variants: [
              V(null, "Figura básica rectangular.", "Basic rectangular figure."), V(null, "Figura rectangular de esquinas suaves; es la forma por defecto.", "Rectangular figure with soft corners; the default shape."), V(null, "Figura elíptica.", "Elliptical figure."), V(null, "Figura en rombo, típica de decisiones.", "Diamond figure, typical of decisions."),
              V(null, "Figura triangular.", "Triangular figure."), V(null, "Figura de cinco lados.", "Five-sided figure."), V(null, "Figura de seis lados.", "Six-sided figure."), V(null, "Figura de ocho lados.", "Eight-sided figure."),
              V(null, "Figura oblicua de lados paralelos, típica de entradas y salidas.", "Slanted figure with parallel sides, typical of inputs and outputs."), V(null, "Figura con base mayor que la parte superior.", "Figure whose base is wider than its top."), V(null, "Figura de almacén de datos.", "Data-store figure."), V(null, "Figura de documento.", "Document figure."),
              V(null, "Figura de contorno nuboso.", "Cloud-outlined figure."), V(null, "Figura estrellada.", "Star-shaped figure."), V(null, "Figura en cruz.", "Cross-shaped figure."), V(null, "Contorno definido libremente mediante un trazado vectorial.", "Outline freely defined through a vector path."),
              V(null, "Figura sustituida por una imagen del proyecto, con ajuste contener, cubrir, rellenar o ninguno.", "Figure replaced by a project image, with fit contain, cover, fill or none.")
            ]
          },
          edge_mapping: {
            nf: L("Mapeo de arista (regla relación → línea)", "Edge mapping (relation → line rule)"),
            fm: L("Regla que dibuja una relación del metamodelo como línea entre los nodos de dos clases, o bien una arista semántica en la que la propia conexión es un objeto del modelo con origen y destino. Exige relación salvo en el caso semántico y su creación genera la herramienta de creación de arista.", "Rule drawing a metamodel relation as a line between the nodes of two classes, or a semantic edge where the connection is itself a model object with source and target. It requires a relation except in the semantic case and creating it generates the create-edge tool."),
            tm: L("EdgeMapping(id, sourceClassId, targetClassId; relationId o SemanticEdgeDescriptor; EditorEdgeLineType).", "EdgeMapping(id, sourceClassId, targetClassId; relationId or SemanticEdgeDescriptor; EditorEdgeLineType)."),
            variants: [
              V(null, "Trazado en curva continua.", "Continuous-curve routing."), V(null, "Trazado en segmento recto.", "Straight-segment routing."), V(null, "Trazado con giros en ángulo recto.", "Routing with right-angle turns."), V(null, "Trazado con giros redondeados; opción por defecto.", "Routing with rounded turns; the default option."), V(null, "Trazado ortogonal automático.", "Automatic orthogonal routing."),
              V(null, "La conexión es un elemento del modelo con tipo, origen y destino propios, no una simple relación.", "The connection is a model element with its own type, source and target, not a plain relation.", "Modelado con SemanticEdgeDescriptor (patrón de Sirius y GMF).", "Modelled with SemanticEdgeDescriptor (Sirius and GMF pattern).")
            ]
          },
          layers_filters: {
            nf: L("Capas y filtros (modularización de la vista)", "Layers and filters (view modularisation)"),
            fm: L("Capa: agrupación de mapeos y herramientas que se activa o desactiva como bloque. Filtro: condición que oculta o muestra partes. Ambos pertenecen a una representación, nacen con un estado inicial y solo algunos admiten conmutación por el usuario.", "Layer: grouping of mappings and tools turned on or off as a block. Filter: condition hiding or showing parts. Both belong to a representation, start with an initial state and only some allow user toggling."),
            tm: L("RepresentationLayerDefinition (defaultLayer, activeByDefault, userToggleable) y RepresentationFilterDefinition (activeByDefault, mappingIds, conditionExpressionId, externalCondition).", "RepresentationLayerDefinition (defaultLayer, activeByDefault, userToggleable) and RepresentationFilterDefinition (activeByDefault, mappingIds, conditionExpressionId, externalCondition)."),
            variants: [
              V(null, "Capa base de la vista, siempre activa.", "Base layer of the view, always active."),
              V(null, "Capa adicional que el usuario enciende o apaga.", "Additional layer the user switches on or off."),
              V(null, "Filtro gobernado por una expresión evaluable del lenguaje de expresiones.", "Filter governed by an evaluable expression of the expression language."),
              V(null, "Filtro cuyo criterio es solo una anotación textual sin efecto de evaluación.", "Filter whose criterion is merely a textual note with no evaluation effect.")
            ]
          },
          styles: {
            nf: L("Estilo y regla de estilo condicional (apariencia dependiente del estado)", "Style and conditional style rule (state-dependent appearance)"),
            fm: L("Descripción del aspecto de un nodo o arista junto con reglas que lo sustituyen cuando una condición sobre el modelo se cumple. Cada regla tiene prioridad (de -100000 a 100000), se evalúa en cada cambio y la de mayor prioridad prevalece; pertenece a un único mapeo.", "Description of a node's or edge's look together with rules that replace it when a condition on the model holds. Each rule has a priority (-100000 to 100000), is evaluated on every change and the highest priority prevails; it belongs to a single mapping."),
            tm: L("ConditionalStyleRule(id, name, enabled, priority, conditionExpressionId, styles) con estilos como mapas clave/valor libres; reutiliza el constructor de expresiones de la validación.", "ConditionalStyleRule(id, name, enabled, priority, conditionExpressionId, styles) with styles as free key/value maps; reuses the validation expression builder."),
            variants: [
              V(null, "Énfasis visual para un elemento relevante.", "Visual emphasis for a relevant element."), V(null, "Marca de estado satisfactorio.", "Mark of a satisfactory state."), V(null, "Marca de estado a vigilar.", "Mark of a state to watch."), V(null, "Marca de estado defectuoso.", "Mark of a faulty state."), V(null, "Reducción de énfasis visual.", "Reduction of visual emphasis.")
            ]
          },
          tools: {
            nf: L("Paleta y herramientas (acciones de edición disponibles)", "Palette and tools (available editing actions)"),
            fm: L("Paleta con 0..N grupos, cada uno con herramientas de cinco clases, visibles o habilitadas según expresiones y asignables a 0..N representaciones. Las de crear nodo y arista no se declaran sueltas, nacen de un mapeo; las de acción requieren un grupo destino.", "Palette with 0..N groups, each with tools of five kinds, visible or enabled depending on expressions and assignable to 0..N representations. Create-node and create-edge tools are not declared on their own, they are born from a mapping; action tools require a target group."),
            tm: L("Jerarquía sellada EditorTool con ToolPaletteDefinition y PaletteGroup; campos comunes en EditorToolSupport.", "Sealed EditorTool hierarchy with ToolPaletteDefinition and PaletteGroup; common fields in EditorToolSupport."),
            variants: [
              V("CREATE_NODE", "Instancia una clase en la vista; puede abrir un formulario y aportar valores iniciales.", "Instantiates a class in the view; it may open a form and supply initial values."),
              V("CREATE_EDGE", "Conecta dos nodos según un mapeo de arista y puede permitir reconexión.", "Connects two nodes according to an edge mapping and may allow reconnection."),
              V("INVOKE_OPERATION", "Ejecuta una operación implementada del metamodelo.", "Runs an implemented metamodel operation."),
              V("RUN_ACTION", "Dispara una acción declarada.", "Triggers a declared action."),
              V("VALIDATE", "Lanza la validación del modelo, manual por defecto.", "Launches model validation, manual by default.")
            ]
          },
          forms: {
            nf: L("Formulario (edición estructurada de los atributos de una clase)", "Form (structured editing of a class's attributes)"),
            fm: L("Definición ligada a una clase concreta compuesta por 1..N secciones de 1 a 4 columnas, con campos que enlazan cada característica con un tipo de control. Una herramienta de crear nodo puede abrirlo y cada sección y campo necesita identificador.", "Definition tied to a concrete class made of 1..N sections of 1 to 4 columns, with fields linking each feature to a control type. A create-node tool may open it and each section and field needs an identifier."),
            tm: L("FormDefinition(id, targetClassId) con FormSectionDefinition (columnas 1..4) y FormFieldDefinition(id, featureId, widget: FormFieldWidget).", "FormDefinition(id, targetClassId) with FormSectionDefinition (columns 1..4) and FormFieldDefinition(id, featureId, widget: FormFieldWidget)."),
            variants: [
              V(null, "Control de una línea; es el control por defecto.", "Single-line control; the default control."), V(null, "Control de varias líneas.", "Multi-line control."), V(null, "Control para valores numéricos.", "Control for numeric values."), V(null, "Control binario de marcado.", "Binary tick control."),
              V(null, "Selección de un literal de un enumerado.", "Selection of one literal of an enumeration."), V(null, "Control calendario.", "Calendar control."), V(null, "Control cromático.", "Colour control."), V(null, "Selección de un elemento del modelo.", "Selection of one model element."),
              V(null, "Selección de varios elementos del modelo.", "Selection of several model elements."), V(null, "Adjunción de un recurso binario.", "Attachment of a binary resource."), V(null, "Control de texto con resaltado de sintaxis.", "Text control with syntax highlighting."), V(null, "Control para componer expresiones guiadas.", "Control for composing guided expressions."),
              V(null, "Presentación sin posibilidad de modificación.", "Presentation with no way to modify."), V(null, "Control sustituible por una pieza propia.", "Control replaceable by a custom piece.")
            ]
          },
          codegen: {
            nf: L("Definición de generación de código (destino y alcance de la salida)", "Code generation definition (target and scope of the output)"),
            fm: L("Parámetros opcionales (0..1 por editor) que fijan el lenguaje destino, el espacio de nombres y los destinos de generación. Tiene sentido solo si la capacidad de generación está declarada y, sin tocarse, asume Java, 'generated.model' y todos los formatos del metamodelo.", "Optional parameters (0..1 per editor) fixing the target language, the namespace and the generation targets. It only makes sense if the generation capability is declared and, untouched, assumes Java, 'generated.model' and all the metamodel's formats."),
            tm: L("CodeGenerationDefinition con valores por defecto java, namespace 'generated.model' y targetIds = ids de formatos del metamodelo.", "CodeGenerationDefinition with defaults java, namespace 'generated.model' and targetIds = metamodel format ids.")
          },
          runtime_policy: {
            nf: L("Política de ejecución (reglas de comportamiento de las sesiones)", "Runtime policy (behaviour rules of sessions)"),
            fm: L("Conjunto de ajustes único por editor que gobierna todas sus sesiones: cuándo se valida, guardado automático, recuperación de la última sesión, publicación de eventos, flujos y gamificación, representaciones abiertas (2 por defecto, mínimo 1) y tiempo máximo de operación (30000 ms, mínimo 100). Publicar eventos actúa como puerta de los demás mecanismos reactivos.", "Single settings set per editor governing all its sessions: when to validate, autosave, latest-session recovery, event publishing, flows and gamification, open representations (2 by default, minimum 1) and operation timeout (30000 ms, minimum 100). Event publishing acts as the gate of the other reactive mechanisms."),
            tm: L("EditorRuntimePolicy(validationTriggers, autosave, recoverLatestSession, publishEvents, eventFlowsEnabled, gamificationEnabled, maximumOpenRepresentations, operationTimeoutMillis).", "EditorRuntimePolicy(validationTriggers, autosave, recoverLatestSession, publishEvents, eventFlowsEnabled, gamificationEnabled, maximumOpenRepresentations, operationTimeoutMillis)."),
            variants: [
              V("ON_OPEN", "Disparador de validación completa en el momento de abrir la sesión.", "Full-validation trigger at the moment the session opens."),
              V("ON_CHANGE", "Disparador de validación incremental tras cada modificación del modelo.", "Incremental-validation trigger after each model modification."),
              V("ON_REPRESENTATION_CHANGE", "Disparador de validación al pasar de una vista a otra.", "Validation trigger when moving from one view to another."),
              V("MANUAL", "Sin disparadores automáticos; la validación es completa y solo bajo petición. Valor por defecto.", "No automatic triggers; validation is full and only on request. Default value.")
            ]
          },
          session: {
            nf: L("Sesión de edición (modelo abierto en un editor compilado)", "Editing session (model open in a compiled editor)"),
            fm: L("Instancia de trabajo que enlaza exactamente un modelo, un editor compilado y 1..N vistas abiertas, y mantiene el último informe de validación y un contador de revisión. Transita de abierta a con cambios a guardada y registra la disposición visual aparte del contenido.", "Working instance linking exactly one model, one compiled editor and 1..N open views, and keeping the latest validation report and a revision counter. It moves from open to dirty to saved and records the visual layout apart from the content."),
            tm: L("EditorModelSession gestionada por EditorModelSessionService (validación, quick fix, eventos con búfer); errores clasificados en siete causas.", "EditorModelSession managed by EditorModelSessionService (validation, quick fix, buffered events); errors classified into seven causes."),
            variants: [
              V(null, "Alta de la sesión con validación inicial si la política la prevé.", "Session creation with initial validation if the policy provides for it."),
              V(null, "Alteración del modelo mediante creación, conexión o modificación, con emisión de eventos de cambio.", "Alteration of the model through creation, connection or modification, with emission of change events."),
              V(null, "Evaluación de reglas, completa si es manual e incremental si se dispara por cambio.", "Rule evaluation, full if manual and incremental if triggered by change."),
              V(null, "Aplicación de una corrección asociada a un diagnóstico, seguida de revalidación incremental.", "Application of a fix tied to a diagnostic, followed by incremental revalidation."),
              V(null, "Persistencia del modelo y liberación de los eventos retenidos.", "Model persistence and release of the held events."),
              V(null, "Reversión de cambios dentro de la sesión.", "Reversion of changes within the session.")
            ]
          },
          visual_import: {
            nf: L("Importación de definición visual (conversión con informe de calidad)", "Visual definition import (conversion with quality report)"),
            fm: L("Conversión de una definición de editor creada en otra tecnología en una definición propia, con un informe de diagnósticos y correcciones por revisar. Combina una tecnología de origen, un modo de asociación al metamodelo y correcciones clasificadas por riesgo; produce exactamente una definición de editor.", "Conversion of an editor definition created in another technology into an own definition, with a report of diagnostics and fixes to review. It combines a source technology, a metamodel association mode and fixes classified by risk; it produces exactly one editor definition."),
            tm: L("VisualDefinitionImportDialog y VisualImportMetamodelResolver, con tecnologías SIRIUS_DESKTOP, SIRIUS_WEB, GMF, DRAWIO, modos EXISTING, EXTERNAL, EMBEDDED y seguridad SAFE, REVIEW, MANUAL.", "VisualDefinitionImportDialog and VisualImportMetamodelResolver, with technologies SIRIUS_DESKTOP, SIRIUS_WEB, GMF, DRAWIO, modes EXISTING, EXTERNAL, EMBEDDED and safety SAFE, REVIEW, MANUAL."),
            variants: [
              V("SIRIUS_DESKTOP", "Origen: definición de escritorio de la familia Sirius.", "Source: desktop definition from the Sirius family."),
              V("SIRIUS_WEB", "Origen: definición web de la familia Sirius.", "Source: web definition from the Sirius family."),
              V("GMF", "Origen: definición de un editor gráfico de Eclipse.", "Source: definition of an Eclipse graphical editor."),
              V("DRAWIO", "Origen: diagrama dibujado a mano alzada convertido en definición.", "Source: freehand-drawn diagram turned into a definition."),
              V("EXISTING", "El metamodelo de la importación es uno ya guardado.", "The import's metamodel is one already saved."),
              V("EXTERNAL", "El metamodelo se resuelve fuera del origen.", "The metamodel is resolved outside the source."),
              V("EMBEDDED", "El metamodelo se extrae del propio origen.", "The metamodel is extracted from the source itself."),
              V("SAFE", "Ajuste sin riesgo semántico que se aplica solo.", "Adjustment without semantic risk that applies by itself."),
              V("REVIEW", "Ajuste que se aplica solo tras confirmación.", "Adjustment applied only after confirmation."),
              V("MANUAL", "Divergencia que no puede resolverse automáticamente.", "Divergence that cannot be resolved automatically.")
            ]
          }
        }
      }
    },

    procs: {
      /* ---------------------------------------------------------------- METAMODELO: procesos de capacidad */
      metamodel_define_save: {
        nf: L("Construcción y consolidación de un metamodelo", "Building and consolidating a metamodel"),
        df: L("Un lenguaje parte de cero, de una definición externa importada o de un proyecto recuperado, converge en una fase común de completado y se somete a la comprobación de consistencia. Si falla, se corrige y se repite; al pasar, el metamodelo pasa de en edición a guardado y queda con su marca de validez.", "A language starts from scratch, from an imported external definition or from a recovered project, converges on a common completion phase and undergoes the consistency check. If it fails it is corrected and repeated; on passing, the metamodel moves from being edited to saved and carries its validity mark."),
        nt: L("CanonicalMetamodelApplicationService.prepareForSave vía REST /metamodels", "CanonicalMetamodelApplicationService.prepareForSave via REST /metamodels"),
        dt: L("El guardado pasa por prepareForSave, que ejecuta la validación bloqueante del servicio de validación del metamodelo antes de aceptar el cambio. Para la importación consta un registro de 21 formatos; el enrutado de cada formato a su importador no se ha trazado.", "Saving goes through prepareForSave, which runs the blocking validation of the metamodel validation service before accepting the change. For import a registry of 21 formats exists; the routing of each format to its importer was not traced."),
        nodes: {
          d1: N("Elegir el origen del lenguaje", "Choose the language's origin", "bifurcación entre definir, importar o recuperar", "fork between define, import or recover", "Intención de partida → ruta de construcción (manual, importación o recuperación)", "Starting intent → construction path (manual, import or recovery)"),
          def: N("Declarar tipos, estructura y relaciones", "Declare types, structure and relations", "clases × atributos × relaciones → metamodelo en edición", "classes × attributes × relations → metamodel being edited", "Concepto del dominio → clases, atributos y relaciones declarados", "Domain concept → declared classes, attributes and relations"),
          imp: N("Convertir una definición externa", "Convert an external definition", "formato × fidelidad → propuesta de estructura", "format × fidelity → structure proposal", "Definición en uno de 21 formatos → clases y relaciones propuestas", "Definition in one of 21 formats → proposed classes and relations"),
          rec: N("Materializar desde un proyecto recuperado", "Materialize from a recovered project", "Ecore, GenModel o inferencia → metamodelo", "Ecore, GenModel or inference → metamodel", "Proyecto externo recuperado → metamodelo materializado", "Recovered external project → materialized metamodel"),
          rev: N("Completar y garantizar la coherencia", "Complete and ensure coherence", "huecos de fidelidad × reglas de herencia", "fidelity gaps × inheritance rules", "Estructura propuesta → estructura completa y coherente", "Proposed structure → complete, coherent structure"),
          sv: N("Consolidar el metamodelo", "Consolidate the metamodel", "metamodelo en edición → comprobación de consistencia", "metamodel being edited → consistency check", "Metamodelo en edición → solicitud de guardado validada", "Metamodel being edited → validated save request",
            { lt: "CanonicalMetamodelApplicationService.prepareForSave", st: ["servicio de aplicación · REST /metamodels", "application service · REST /metamodels"], tx: ["El guardado llama a prepareForSave, que valida de forma bloqueante antes de persistir el metamodelo.", "Saving calls prepareForSave, which validates in a blocking manner before persisting the metamodel."] }),
          ok: N("Decidir la admisibilidad", "Decide admissibility", "errores de consistencia → bloquea o admite", "consistency errors → blocks or admits", "Resultado de la comprobación → admitido o bloqueado", "Check result → admitted or blocked"),
          fix: N("Resolver las inconsistencias", "Resolve the inconsistencies", "errores señalados → estructura corregida", "reported errors → corrected structure", "Errores de consistencia → estructura corregida lista para reintentar", "Consistency errors → corrected structure ready to retry"),
          art: N("Lenguaje consolidado con marca de validez", "Consolidated language with validity mark", "estado: en edición → guardado", "state: being edited → saved", "Metamodelo admitido → metamodelo guardado con marca de validez", "Admitted metamodel → saved metamodel with a validity mark")
        }
      },
      metamodel_delete_cascade: {
        nf: L("Eliminación transaccional con limpieza de dependientes", "Transactional removal with dependent clean-up"),
        df: L("La retirada de una clase se registra como intención, retira el elemento principal y después limpia las definiciones que lo referencian. Si la limpieza se interrumpe queda en recuperación necesaria y se reanuda desde el diario; también puede cancelarse antes de cualquier efecto.", "Removing a class is recorded as an intent, removes the primary element and then cleans the definitions that reference it. If the clean-up is interrupted it stays in recovery required and is resumed from the journal; it can also be cancelled before any effect."),
        nt: L("MetamodelDependentArtifactCleanupService + MetamodelDeletionJournal", "MetamodelDependentArtifactCleanupService + MetamodelDeletionJournal"),
        dt: L("Un servicio dedicado limpia los artefactos dependientes mientras MetamodelDeletionJournal registra los estados PREPARED, PRIMARY_DELETED, RECOVERY_REQUIRED, COMPLETED y ABORTED. La búsqueda combina convención de nombre de fichero y referencias internas (al menos nueve tipos de definición); no se ha trazado el detalle de cada limpiador.", "A dedicated service cleans the dependent artifacts while MetamodelDeletionJournal records the states PREPARED, PRIMARY_DELETED, RECOVERY_REQUIRED, COMPLETED and ABORTED. The lookup combines file-name convention and internal references (at least nine kinds of definition); the detail of each cleaner was not traced."),
        nodes: {
          prep: N("Registrar la intención de borrado", "Record the deletion intent", "estado del diario: ninguno → preparado", "journal state: none → prepared", "Orden de borrar la clase → entrada de diario en estado preparado", "Order to delete the class → journal entry in prepared state", { lt: "MetamodelDeletionJournal", st: ["diario de borrado · estado PREPARED", "deletion journal · PREPARED state"], tx: ["Antes de modificar nada se anota la operación en el diario con el estado PREPARED.", "Before anything is modified the operation is noted in the journal with state PREPARED."] }),
          scan: N("Descubrir artefactos dependientes", "Discover dependent artifacts", "clase × definiciones que la referencian", "class × definitions referencing it", "Clase a borrar → conjunto de definiciones que dependen de ella", "Class to delete → set of definitions depending on it", { lt: "MetamodelDependentArtifactCleanupService", st: ["búsqueda por nombre de fichero y referencias", "lookup by file name and references"], tx: ["Localiza las definiciones afectadas por convención de nombre de fichero y por referencias internas.", "Finds the affected definitions by file-name convention and by internal references."] }),
          prim: N("Retirar el elemento principal", "Remove the primary element", "estado: preparado → principal borrado", "state: prepared → primary deleted", "Clase → metamodelo sin esa clase y diario avanzado", "Class → metamodel without that class and advanced journal"),
          clean: N("Depurar definiciones dependientes", "Purge dependent definitions", "reglas, editores y transformaciones afectados", "affected rules, editors and transformations", "Conjunto de dependientes → artefactos eliminados o ajustados", "Set of dependents → removed or adjusted artifacts", { lt: "MetamodelDependentArtifactCleanupService", st: ["limpieza en cascada de artefactos dependientes", "cascade clean-up of dependent artifacts"] }),
          d1: N("Determinar si la limpieza fue íntegra", "Determine whether clean-up was complete", "limpieza completa o interrumpida", "clean-up complete or interrupted", "Resultado de la limpieza → completado o recuperación necesaria", "Clean-up result → completed or recovery required"),
          rec: N("Reanudar desde el diario", "Resume from the journal", "estado: recuperación necesaria → completado", "state: recovery required → completed", "Diario con operación a medias → limpieza reanudada hasta completarse", "Journal with a half-done operation → clean-up resumed until completion"),
          ab: N("Cancelar sin efectos", "Cancel without effects", "estado: preparado → abortado", "state: prepared → aborted", "Intención aún no ejecutada → operación abortada sin cambios", "Intent not yet executed → operation aborted with no changes"),
          art: N("Borrado completado y registrado", "Completed, recorded deletion", "estado terminal: completado", "terminal state: completed", "Limpieza íntegra → registro final de lo retirado y limpiado", "Complete clean-up → final record of what was removed and cleaned")
        }
      },
      metamodel_export_generate: {
        nf: L("Habilitación y generación de código desde un metamodelo válido", "Enabling and generating code from a valid metamodel"),
        df: L("La generación solo es posible si el contenido actual coincide con la marca de validez dejada por el último guardado correcto; en caso contrario hay que volver a guardar y validar. Con el metamodelo apto, un modelo y un destino se obtiene el código del lenguaje.", "Generation is only possible if the current content matches the validity mark left by the last successful save; otherwise it must be saved and validated again. With a fit metamodel, a model and a target the language code is obtained."),
        nt: L("MetamodelValidationApplicationService + generación de la publicación", "MetamodelValidationApplicationService + publication generation"),
        dt: L("La comprobación de aptitud compara una huella (fingerprint) del contenido con la guardada; consta MetamodelValidationApplicationService como punto de entrada. La generación es responsabilidad de la capacidad de publicación y su cadena de llamadas no se ha trazado en esta capacidad.", "The fitness check compares a content fingerprint with the saved one; MetamodelValidationApplicationService is the entry point. Generation belongs to the publication capability and its call chain was not traced in this capability."),
        nodes: {
          chk: N("Contrastar el contenido con la marca de validez", "Compare content with the validity mark", "huella actual × huella validada", "current fingerprint × validated fingerprint", "Metamodelo guardado → veredicto de aptitud para generar", "Saved metamodel → verdict of readiness to generate", { lt: "MetamodelValidationApplicationService", st: ["comparación de huella (fingerprint)", "fingerprint comparison"], tx: ["Compara una huella del contenido actual con la registrada al validar para decidir si es apto para generar.", "Compares a fingerprint of the current content with the one recorded on validation to decide whether it is fit for generation."] }),
          d1: N("Decidir la aptitud para generar", "Decide readiness to generate", "coincide o ha cambiado", "matches or has changed", "Veredicto de la comparación → apto o necesita revalidar", "Comparison verdict → ready or needs revalidation"),
          fix: N("Renovar la marca de validez", "Renew the validity mark", "guardado × validación → nueva huella", "save × validation → new fingerprint", "Contenido modificado → marca de validez renovada", "Modified content → renewed validity mark"),
          "in": N("Fijar el alcance de la generación", "Set the generation scope", "metamodelo × modelo × destino", "metamodel × model × target", "Metamodelo apto → combinación completa de metamodelo, modelo y destino", "Fit metamodel → complete combination of metamodel, model and target"),
          gen: N("Producir el código del lenguaje", "Produce the language's code", "plantillas del destino × estructura del lenguaje", "target templates × language structure", "Alcance de generación → código del lenguaje", "Generation scope → language code"),
          art: N("Código del lenguaje disponible", "Language code available", "resultado listo para entregar", "result ready for delivery", "Código producido → artefacto generado disponible para la entrega", "Produced code → generated artifact available for delivery")
        }
      },
      /* ---------------------------------------------------------------- METAMODELO: autoría */
      metamodel_author_canvas: {
        nf: L("Composición gráfica de la estructura del lenguaje", "Graphic composition of the language structure"),
        df: L("Los clasificadores se instancian desde una paleta y se enlazan con relaciones de una de seis clases. La conexión se comprueba al crearse (herencia entre iguales y sin ciclos; implementación de clase a interfaz) y el detalle de datos se completa fuera del nodo, que es de solo lectura.", "Classifiers are instantiated from a palette and linked with relations of one of six kinds. The connection is checked on creation (inheritance between like kinds and without cycles; implementation from class to interface) and data detail is completed outside the node, which is read-only."),
        nt: L("Lienzo React Flow con paleta y conexión nativa por puertos", "React Flow canvas with palette and native port connection"),
        dt: L("El lienzo está basado en React Flow con paleta y conexión nativa por puertos, dentro del editor de metamodelos del frontend. Los nodos son de solo lectura; la edición ocurre en el panel de propiedades. La regla de validez de la conexión se aplica en vivo; el detalle del código que la implementa no se ha trazado.", "The canvas is based on React Flow with a palette and native port connection, inside the frontend metamodel editor. Nodes are read-only; editing happens in the property panel. The connection validity rule is applied live; the detail of the code implementing it was not traced."),
        nodes: {
          pal: N("Instanciar un clasificador", "Instantiate a classifier", "paleta → clase, abstracta, interfaz, enumerado o tipo", "palette → class, abstract, interface, enumeration or type", "Elección en la paleta → nuevo clasificador en el metamodelo", "Palette choice → new classifier in the metamodel", { st: ["lienzo React Flow + paleta", "React Flow canvas + palette"] }),
          d1: N("Decidir si se vinculan clasificadores", "Decide whether to link classifiers", "estructura aislada o conectada", "isolated or connected structure", "Estado del lienzo → ruta de conexión o de completado", "Canvas state → connection or completion path"),
          kind: N("Seleccionar la semántica del vínculo", "Select the link semantics", "una de las seis clases de relación", "one of the six relation kinds", "Intención de vínculo → clase de relación preseleccionada", "Link intent → preselected relation kind"),
          d2: N("Escoger el gesto de conexión", "Pick the connection gesture", "dos gestos con idéntico resultado", "two gestures with identical result", "Clase de relación elegida → gesto de conexión", "Chosen relation kind → connection gesture"),
          cc: N("Conectar por selección sucesiva de extremos", "Connect by successive end selection", "puerto de origen → puerto de destino", "source port → target port", "Extremo origen y extremo destino elegidos → relación propuesta", "Chosen source and target ends → proposed relation", { st: ["conexión nativa por puertos", "native port connection"] }),
          dr: N("Conectar arrastrando entre extremos", "Connect by dragging between ends", "arrastre origen → destino", "drag source → target", "Arrastre entre dos extremos → relación propuesta del tipo preseleccionado", "Drag between two ends → proposed relation of the preselected kind", { st: ["conexión nativa por puertos", "native port connection"] }),
          val: N("Verificar la coherencia del vínculo", "Verify the link's coherence", "herencia entre iguales · implementación a interfaz", "inheritance between likes · implementation to interface", "Relación propuesta → relación admitida o rechazada por incoherente", "Proposed relation → relation admitted or rejected as incoherent"),
          ed: N("Detallar datos fuera del nodo", "Detail data outside the node", "atributos, multiplicidades, valores por defecto", "attributes, multiplicities, default values", "Clasificador seleccionado → detalle de atributos y relaciones completado", "Selected classifier → completed attribute and relation detail"),
          art: N("Estructura lista para consolidar", "Structure ready to consolidate", "estado: en edición", "state: being edited", "Composición gráfica → metamodelo en edición pendiente de guardar", "Graphic composition → metamodel being edited pending save")
        }
      },
      metamodel_author_wizard: {
        nf: L("Construcción secuencial del lenguaje por fases", "Sequential phase-by-phase building of the language"),
        df: L("Una vía lineal obtiene el mismo metamodelo que el lienzo: identidad, clasificadores, estructura, comprobación y guardado. Si la comprobación detecta inconsistencias se retrocede a la estructura.", "A linear path yields the same metamodel as the canvas: identity, classifiers, structure, check and save. If the check detects inconsistencies one steps back to the structure."),
        nt: L("Modo 'Guiado' del editor de metamodelos", "'Guided' mode of the metamodel editor"),
        dt: L("Se trata del modo 'Guiado' del editor de metamodelos del frontend; sobre su implementación solo consta que es una alternativa lineal al lienzo y que guarda a través del mismo guardado con validación del metamodelo.", "It is the 'Guided' mode of the frontend metamodel editor; on its implementation it is only known that it is a linear alternative to the canvas and that it saves through the same validated metamodel save."),
        nodes: {
          id: N("Identificar el lenguaje", "Identify the language", "nombre del metamodelo", "metamodel name", "Nombre elegido → metamodelo identificado", "Chosen name → identified metamodel"),
          cl: N("Declarar los clasificadores", "Declare the classifiers", "clases, enumerados, tipos de datos", "classes, enumerations, data types", "Conceptos del dominio → clasificadores creados", "Domain concepts → created classifiers"),
          st: N("Articular la estructura interna", "Articulate the internal structure", "atributos × relaciones por clase", "attributes × relations per class", "Clasificadores → atributos y relaciones añadidos", "Classifiers → added attributes and relations"),
          va: N("Comprobar la consistencia previa", "Check consistency beforehand", "reglas de coherencia del metamodelo", "metamodel coherence rules", "Estructura completa → resultado de coherencia", "Complete structure → coherence result"),
          d1: N("Decidir si se puede consolidar", "Decide whether it can be consolidated", "válido o con inconsistencias", "valid or with inconsistencies", "Resultado de coherencia → consolidar o volver a la estructura", "Coherence result → consolidate or return to the structure"),
          sv: N("Consolidar el metamodelo", "Consolidate the metamodel", "metamodelo consistente → guardado", "consistent metamodel → saved", "Metamodelo coherente → metamodelo guardado", "Coherent metamodel → saved metamodel"),
          art: N("Metamodelo consolidado", "Consolidated metamodel", "estado: guardado", "state: saved", "Recorrido completado → metamodelo guardado", "Completed path → saved metamodel")
        }
      },
      metamodel_author_import: {
        nf: L("Incorporación asistida de una definición externa", "Assisted incorporation of an external definition"),
        df: L("La importación recorre origen, estrategia de fusión, análisis, revisión y confirmación. La fidelidad EXACT, SEMANTIC o STRUCTURAL del formato determina cuánto debe completarse a mano; el resultado queda en edición, sin guardar.", "Import goes through source, merge strategy, analysis, review and confirmation. The format's EXACT, SEMANTIC or STRUCTURAL fidelity determines how much must be completed by hand; the result stays in edition, unsaved."),
        nt: L("Diálogo de importación + registro MetamodelImportFormat (21 formatos)", "Import dialog + MetamodelImportFormat registry (21 formats)"),
        dt: L("La lista de formatos la entrega el backend dinámicamente a partir del registro MetamodelImportFormat (id, categoría, MetamodelImportSupportLevel) y cubre, entre otros, recuperación desde Ecore, GenModel y Xtext, código fuente y Blockly. Del enrutado de cada formato a su importador solo consta que existe una clase importadora por identificador.", "The backend delivers the format list dynamically from the MetamodelImportFormat registry (id, category, MetamodelImportSupportLevel) and it covers, among others, recovery from Ecore, GenModel and Xtext, source code and Blockly. On routing each format to its importer it is only known that one importer class exists per id."),
        nodes: {
          src: N("Declarar el origen y su formato", "Declare the source and its format", "uno de 21 formatos × contenido", "one of 21 formats × content", "Formato y contenido de origen → origen identificado", "Format and source content → identified source", { st: ["formatos servidos por el backend", "formats served by the backend"] }),
          str: N("Fijar la política de fusión", "Set the merge policy", "cómo convivir con lo existente", "how to coexist with what exists", "Origen identificado → estrategia de fusión elegida", "Identified source → chosen merge strategy"),
          an: N("Extraer la estructura propuesta", "Extract the proposed structure", "origen × estrategia → clases, atributos, relaciones", "source × strategy → classes, attributes, relations", "Origen y estrategia → propuesta de metamodelo", "Source and strategy → metamodel proposal"),
          rv: N("Contrastar la propuesta con su fidelidad", "Contrast the proposal with its fidelity", "EXACT · SEMANTIC · STRUCTURAL", "EXACT · SEMANTIC · STRUCTURAL", "Propuesta de metamodelo → propuesta revisada y completada", "Metamodel proposal → reviewed and completed proposal"),
          d1: N("Decidir si se acepta la propuesta", "Decide whether to accept the proposal", "aceptar o revisar la estrategia", "accept or revisit the strategy", "Propuesta revisada → confirmación o retorno a la estrategia", "Reviewed proposal → confirmation or return to the strategy"),
          art: N("Metamodelo incorporado sin guardar", "Incorporated metamodel, unsaved", "estado: en edición", "state: being edited", "Propuesta confirmada → metamodelo en edición", "Confirmed proposal → metamodel being edited")
        }
      },
      metamodel_author_properties: {
        nf: L("Precisión de los datos de una clase seleccionada", "Refinement of the data of a selected class"),
        df: L("Sobre una clase se detallan atributos (tipo, multiplicidad, orden, unicidad, valor inicial), operaciones (firma y clase de comportamiento) y relaciones (clase, extremos, multiplicidades, inversa). La implementación de una operación es un paso condicional separado y las referencias pueden crearse sin pasar por el lienzo.", "On a class, attributes (type, multiplicity, order, uniqueness, initial value), operations (signature and behaviour kind) and relations (kind, ends, multiplicities, opposite) are detailed. An operation's implementation is a separate conditional step and references can be created without the canvas."),
        nt: L("Panel de propiedades del editor de metamodelos", "Metamodel editor property panel"),
        dt: L("Se realiza en el panel de propiedades del editor de metamodelos del frontend; es la única vía de edición de atributos y operaciones. Los cambios se validan al guardar el metamodelo; la implementación de la operación se almacena aparte como OperationBinding.", "It is done in the property panel of the frontend metamodel editor; it is the only way to edit attributes and operations. Changes are validated on saving the metamodel; the operation's implementation is stored apart as OperationBinding."),
        nodes: {
          d1: N("Elegir qué faceta precisar", "Choose which facet to refine", "atributo, operación o relación", "attribute, operation or relation", "Clase seleccionada → faceta a precisar", "Selected class → facet to refine"),
          at: N("Tipar y acotar un atributo", "Type and bound an attribute", "tipo × multiplicidad", "type × multiplicity", "Nombre y tipo deseados → atributo con tipo y multiplicidad", "Desired name and type → attribute with type and multiplicity", { lt: "MetaAttribute", st: ["MetaTypeReference + Multiplicity", "MetaTypeReference + Multiplicity"] }),
          op: N("Declarar la firma de una operación", "Declare an operation signature", "parámetros × retorno × comportamiento", "parameters × return × behaviour", "Datos de la operación → firma con clase de comportamiento", "Operation data → signature with behaviour kind", { lt: "MetaOperation", st: ["MetaOperationBehaviorKind", "MetaOperationBehaviorKind"] }),
          bd: N("Asignar el comportamiento ejecutable", "Assign executable behaviour", "sin implementar · expresión · acción · externa", "unimplemented · expression · action · external", "Firma de operación → implementación asociada, opcional", "Operation signature → associated implementation, optional", { lt: "OperationBinding", st: ["se guarda aparte del metamodelo", "stored apart from the metamodel"] }),
          rl: N("Definir un vínculo entre clases", "Define a link between classes", "clase × extremos × inversa", "kind × ends × opposite", "Datos del vínculo → relación con extremos y multiplicidades", "Link data → relation with ends and multiplicities", { lt: "MetaRelation", st: ["MetaRelationKind + Multiplicity", "MetaRelationKind + Multiplicity"] }),
          opt: N("Afinar orden, unicidad y valor inicial", "Tune order, uniqueness and initial value", "ajustes opcionales de atributos y relaciones", "optional attribute and relation settings", "Atributo o relación → propiedades opcionales fijadas", "Attribute or relation → optional properties set"),
          art: N("Clase con su detalle actualizado", "Class with updated detail", "pendiente de validación al guardar", "pending validation on save", "Cambios de la faceta → clase actualizada en el metamodelo en edición", "Facet changes → updated class in the metamodel being edited")
        }
      },
      /* ---------------------------------------------------------------- EDITOR: procesos de capacidad */
      model_editor_design_flow: {
        nf: L("Diseño guiado de la definición de un editor", "Guided design of an editor definition"),
        df: L("Una definición se construye por fases sobre un metamodelo guardado: identidad, representaciones, herramientas y formularios, y capacidades con política de ejecución. Se revisa la validez de la definición completa y solo entonces puede guardarse, pasando de en diseño a guardada.", "A definition is built in phases over a saved metamodel: identity, representations, tools and forms, and capabilities with runtime policy. The completeness of the definition is reviewed and only then can it be saved, moving from in design to saved."),
        nt: L("Asistente de diseño → REST de editor-definition (CRUD)", "Design wizard → editor-definition REST (CRUD)"),
        dt: L("Se materializa en el asistente guiado del diseñador del frontend, que construye un EditorDefinitionModel (id y metamodelId obligatorios, schemaVersion 1.0) y lo persiste por el REST de editor-definition. La implementación de la comprobación de validez en backend no se ha trazado.", "It is realised in the guided wizard of the frontend designer, which builds an EditorDefinitionModel (id and metamodelId mandatory, schemaVersion 1.0) and persists it through the editor-definition REST. The backend validity check implementation was not traced."),
        nodes: {
          id: N("Vincular el editor a su lenguaje", "Bind the editor to its language", "identificador × metamodelo guardado", "identifier × saved metamodel", "Metamodelo guardado → editor identificado y ligado a ese metamodelo", "Saved metamodel → identified editor bound to that metamodel"),
          rp: N("Concretar las vistas del modelo", "Specify the model views", "1..N representaciones con identificador", "1..N representations with an identifier", "Editor identificado → al menos una representación declarada", "Identified editor → at least one declared representation"),
          tf: N("Ofrecer acciones y edición por campos", "Offer actions and field-based editing", "paleta × formularios", "palette × forms", "Representaciones → paleta y formularios definidos", "Representations → defined palette and forms"),
          cp: N("Fijar capacidades y política de sesión", "Set capabilities and session policy", "12 capacidades × política de ejecución", "12 capabilities × runtime policy", "Diseño del editor → capacidades y reglas de ejecución declaradas", "Editor design → declared capabilities and runtime rules"),
          rv: N("Auditar la integridad de la definición", "Audit the definition's integrity", "completitud de identificador, vistas y mapeos", "completeness of identifier, views and mappings", "Definición completa → informe de validez", "Complete definition → validity report"),
          d1: N("Decidir si la definición es admisible", "Decide whether the definition is admissible", "válida o con carencias", "valid or with gaps", "Informe de validez → guardar o volver a las representaciones", "Validity report → save or return to representations"),
          sv: N("Persistir la definición", "Persist the definition", "estado: en diseño → guardada", "state: in design → saved", "Definición válida → definición guardada", "Valid definition → saved definition"),
          art: N("Definición pendiente de compilar", "Definition pending compilation", "estado: guardada", "state: saved", "Definición guardada → especificación lista para compilar", "Saved definition → specification ready to compile")
        }
      },
      model_editor_compile: {
        nf: L("Transformación de la definición en editor ejecutable", "Turning the definition into a runnable editor"),
        df: L("Se verifican las referencias de la definición contra el metamodelo y, si resuelven, se genera el editor ejecutable sin alterar la definición de diseño; la definición pasa de guardada a compilada. Si no compila, los errores vuelven al diseño y mientras tanto rige un lienzo básico de respaldo.", "The definition's references are verified against the metamodel and, if they resolve, the runnable editor is generated without altering the design definition; the definition moves from saved to compiled. If it does not compile, errors return to design and meanwhile a basic fallback canvas applies."),
        nt: L("EditorDefinitionCompiler vía REST de editor-definition (compilación)", "EditorDefinitionCompiler via editor-definition REST (compile)"),
        dt: L("La compilación la realiza EditorDefinitionCompiler, expuesta en el REST de editor-definition; en el diseñador, compileRuntime cambia a la vista RUNTIME. El registro de notación independiente no interviene: toda la apariencia viene de los mapeos de nodo y arista.", "Compilation is done by EditorDefinitionCompiler, exposed in the editor-definition REST; in the designer, compileRuntime switches to the RUNTIME view. The separate notation registry plays no part: all appearance comes from node and edge mappings."),
        nodes: {
          chk: N("Resolver las referencias de la definición", "Resolve the definition's references", "clases y relaciones citadas × metamodelo", "cited classes and relations × metamodel", "Definición y metamodelo → verdicto de resolución de referencias", "Definition and metamodel → reference-resolution verdict"),
          d1: N("Decidir si es compilable", "Decide whether it is compilable", "referencias resueltas o rotas", "references resolved or broken", "Verdicto de resolución → compilar o recurrir al respaldo", "Resolution verdict → compile or fall back"),
          cmp: N("Generar el editor ejecutable", "Generate the runnable editor", "definición inmutable → editor compilado", "immutable definition → compiled editor", "Definición válida → editor ejecutable; la definición permanece igual", "Valid definition → runnable editor; the definition stays as it is", { lt: "EditorDefinitionCompiler", st: ["compilador de definición de editor", "editor definition compiler"], tx: ["EditorDefinitionCompiler produce el editor ejecutable sin modificar la definición; en el diseñador, compileRuntime fija la vista RUNTIME.", "EditorDefinitionCompiler produces the runnable editor without modifying the definition; in the designer, compileRuntime sets the RUNTIME view."] }),
          vw: N("Exponer el resultado de la compilación", "Expose the compilation outcome", "resultado × política de ejecución", "result × runtime policy", "Editor compilado → resultado y política visibles para el autor", "Compiled editor → result and policy visible to the author", { lt: "compileRuntime", st: ["vista RUNTIME del diseñador", "designer RUNTIME view"] }),
          art: N("Editor ejecutable disponible", "Runnable editor available", "estado: guardada → compilada", "state: saved → compiled", "Compilación correcta → editor apto para abrir sesiones", "Successful compilation → editor fit to open sessions"),
          fb: N("Recurrir al lienzo de respaldo", "Fall back to the basic canvas", "paleta y conexión simple por metamodelo", "palette and simple connection per metamodel", "Ausencia de editor compilado → edición básica del metamodelo", "No compiled editor → basic editing of the metamodel")
        }
      },
      model_editor_session: {
        nf: L("Ciclo de vida de una sesión de edición", "Lifecycle of an editing session"),
        df: L("Se abre un modelo con el editor compilado, se edita con herramientas y formularios, se valida según la política, se aplican correcciones con revalidación incremental y se guarda. Los hechos de edición se emiten como eventos si la política lo permite y, sin guardado automático, se retienen hasta un guardado correcto.", "A model is opened with the compiled editor, edited with tools and forms, validated according to policy, fixes are applied with incremental revalidation and it is saved. Editing facts are emitted as events if the policy allows and, without autosave, are held until a successful save."),
        nt: L("EditorModelSessionService (REST de sesiones de edición)", "EditorModelSessionService (editing-session REST)"),
        dt: L("EditorModelSessionService centraliza la sesión: validación al abrir (línea 126 y 150), al cambiar de representación (175), incremental al cambiar (626 y 716), manual completa (538-546), quick fix (567-585), publicación de eventos (739) y búfer de eventos (746-748). Se expone por REST (abrir, mutar, guardar, validar, quick-fix) y los eventos pasan por EditorSessionEventPublisher.", "EditorModelSessionService centralises the session: validation on open (lines 126 and 150), on view change (175), incremental on change (626 and 716), full manual (538-546), quick fix (567-585), event publication (739) and event buffer (746-748). It is exposed over REST (open, mutate, save, validate, quick-fix) and events go through EditorSessionEventPublisher."),
        nodes: {
          op: N("Instanciar la sesión de trabajo", "Instantiate the working session", "modelo × editor compilado → sesión abierta", "model × compiled editor → open session", "Modelo y editor compilado → sesión abierta, con validación inicial si procede", "Model and compiled editor → open session, with initial validation if applicable", { lt: "EditorModelSessionService", st: ["apertura con validación ON_OPEN (:126, :150)", "open with ON_OPEN validation (:126, :150)"], tx: ["La apertura crea la sesión y, si la política incluye ON_OPEN, valida el modelo; la validación al cambiar de vista figura en la línea 175.", "Opening creates the session and, if the policy includes ON_OPEN, validates the model; validation on view change is at line 175."] }),
          ed: N("Mutar el modelo con las herramientas", "Mutate the model with the tools", "crear · conectar · editar → sesión con cambios", "create · connect · edit → dirty session", "Gestos de edición → cambios del modelo y sesión con cambios", "Editing gestures → model changes and dirty session"),
          ev: N("Emitir los hechos de edición", "Emit the editing facts", "creado, actualizado, borrado, reubicado", "created, updated, deleted, moved", "Cambios del modelo → eventos emitidos o retenidos hasta el guardado", "Model changes → events emitted or held until save", { lt: "EditorModelSessionService", st: ["publicación (:739) y búfer (:746-748)", "publication (:739) and buffer (:746-748)"], tx: ["El servicio publica los eventos (línea 739) y, sin guardado automático, los acumula en un búfer (746-748) que se vacía tras guardar. Banderas de puerta: payload en la sesión frente a metadatos en EditorSessionEventPublisher; discrepancia no resuelta.", "The service publishes the events (line 739) and, without autosave, accumulates them in a buffer (746-748) flushed after save. Gating flags: payload in the session versus metadata in EditorSessionEventPublisher; unresolved discrepancy."] }),
          va: N("Evaluar las reglas sobre el modelo", "Evaluate the rules on the model", "disparador de política → informe de validación", "policy trigger → validation report", "Modelo editado → diagnósticos, completos si es manual e incrementales si es por cambio", "Edited model → diagnostics, full if manual and incremental if by change", { lt: "EditorModelSessionService", st: ["manual completa :538-546 · incremental :626, :716", "full manual :538-546 · incremental :626, :716"] }),
          d1: N("Discriminar diagnósticos corregibles", "Discriminate fixable diagnostics", "diagnóstico con o sin corrección", "diagnostic with or without a fix", "Informe de validación → corrección posible o continuar", "Validation report → possible fix or continue"),
          qf: N("Aplicar la corrección y revalidar", "Apply the fix and revalidate", "capacidad Quick Fix → revalidación incremental", "Quick Fix capability → incremental revalidation", "Diagnóstico con corrección → modelo corregido y diagnósticos actualizados", "Diagnostic with a fix → corrected model and updated diagnostics", { lt: "EditorModelSessionService", st: ["quick fix (:567-585)", "quick fix (:567-585)"], tx: ["La aplicación de la corrección está en las líneas 567-585 y exige que el editor declare QUICK_FIX_RUNTIME; después revalida de forma incremental.", "Fix application is at lines 567-585 and requires the editor to declare QUICK_FIX_RUNTIME; it then revalidates incrementally."] }),
          ud: N("Revertir cambios de la sesión", "Revert session changes", "capacidad UNDO_REDO", "UNDO_REDO capability", "Cambios no deseados → modelo en un estado anterior", "Unwanted changes → model in an earlier state"),
          sv: N("Persistir el modelo y liberar eventos", "Persist the model and release events", "estado: con cambios → guardada", "state: dirty → saved", "Modelo con cambios → modelo guardado y eventos enviados", "Model with changes → saved model and sent events"),
          art: N("Modelo persistido", "Persisted model", "contenido guardado, disposición aparte", "content saved, layout apart", "Guardado correcto → contenido del modelo persistido", "Successful save → persisted model content")
        }
      },
      /* ---------------------------------------------------------------- EDITOR: autoría */
      model_editor_author_design: {
        nf: L("Configuración de mapeos, herramientas y estilos", "Configuring mappings, tools and styles"),
        df: L("Desde el árbol de la definición se añaden representaciones y se proyectan clases y relaciones del metamodelo mediante mapeos de nodo y de arista, que generan a la vez sus herramientas. Se pueden añadir herramientas de acción dentro de un grupo, formularios y reglas de estilo condicional con prioridad.", "From the definition tree, representations are added and metamodel classes and relations are projected through node and edge mappings, which generate their tools at the same time. Action tools within a group, forms and conditional style rules with priority can be added."),
        nt: L("EditorDefinitionDesigner + EditorDefinitionTree (editorDefinitionMappingMutations)", "EditorDefinitionDesigner + EditorDefinitionTree (editorDefinitionMappingMutations)"),
        dt: L("Vista Diseño del frontend con árbol maestro-detalle; la alta de un mapeo de clase usa addClassMappingAndTool, que crea mapeo y herramienta a la vez para que la paleta no se desincronice. Los estilos condicionales se editan con ConditionalStyleEditor y ConditionalEdgeStyleEditor.", "Frontend Design view with a master-detail tree; adding a class mapping uses addClassMappingAndTool, which creates mapping and tool together so the palette never goes out of sync. Conditional styles are edited with ConditionalStyleEditor and ConditionalEdgeStyleEditor."),
        nodes: {
          rp: N("Declarar una vista del editor", "Declare a view of the editor", "clase raíz principal detectada · mínimo una", "detected primary root class · minimum one", "Editor en diseño → representación con su clase raíz", "Editor in design → representation with its root class", { st: ["EditorDefinitionDesigner", "EditorDefinitionDesigner"] }),
          d1: N("Elegir qué proyectar o añadir", "Choose what to project or add", "clase, relación, herramienta o formulario", "class, relation, tool or form", "Representación declarada → parte del editor a configurar", "Declared representation → part of the editor to configure"),
          nm: N("Proyectar una clase como nodo", "Project a class as a node", "clase → mapeo de nodo + herramienta de crear nodo", "class → node mapping + create-node tool", "Clase del metamodelo → nodo con figura y herramienta de creación", "Metamodel class → node with shape and creation tool", { lt: "addClassMappingAndTool", st: ["editorDefinitionMappingMutations", "editorDefinitionMappingMutations"] }),
          em: N("Proyectar una relación como arista", "Project a relation as an edge", "relación → mapeo de arista + herramienta de crear arista", "relation → edge mapping + create-edge tool", "Relación del metamodelo → arista con trazado y herramienta de creación", "Metamodel relation → edge with routing and creation tool"),
          pt: N("Añadir una acción a un grupo de paleta", "Add an action to a palette group", "invocar operación · ejecutar acción · validar", "invoke operation · run action · validate", "Grupo de paleta seleccionado → herramienta de acción añadida al grupo", "Selected palette group → action tool added to the group"),
          fm: N("Dotar de edición por campos a una clase", "Provide field-based editing for a class", "primera clase concreta → formulario", "first concrete class → form", "Clase concreta del metamodelo → formulario con secciones y campos", "Concrete metamodel class → form with sections and fields"),
          cs: N("Condicionar la apariencia al estado", "Condition the appearance on state", "condición × prioridad × preajuste", "condition × priority × preset", "Mapeo existente → regla de estilo condicional con prioridad", "Existing mapping → conditional style rule with priority", { st: ["ConditionalStyleEditor", "ConditionalStyleEditor"] }),
          art: N("Definición en diseño enriquecida", "Enriched definition in design", "estado: en diseño", "state: in design", "Mapeos, herramientas y estilos → definición de diseño actualizada", "Mappings, tools and styles → updated design definition")
        }
      },
      model_editor_author_guided: {
        nf: L("Definición del editor en seis fases verificadas", "Defining the editor in six verified phases"),
        df: L("Seis fases (identidad, representaciones, herramientas y formularios, capacidades, revisión, ejecución) con comprobación de completitud en cada una. La fase final de ejecución, que compila y despliega, es opcional; el resultado es la misma definición que produce el diseño libre.", "Six phases (identity, representations, tools and forms, capabilities, review, runtime) with a completeness check in each. The final runtime phase, which compiles and deploys, is optional; the result is the same definition the free design produces."),
        nt: L("EditorDefinitionGuidedSteps (6 pasos)", "EditorDefinitionGuidedSteps (6 steps)"),
        dt: L("Implementado en el componente EditorDefinitionGuidedSteps (líneas 44-94), con seis pasos y su comprobación de completitud; el guardado final y la compilación reutilizan los servicios de definición y compilación del backend.", "Implemented in the EditorDefinitionGuidedSteps component (lines 44-94), with six steps and their completeness checks; final saving and compilation reuse the backend definition and compilation services."),
        nodes: {
          p1: N("Fase 1: nombrar el editor", "Phase 1: name the editor", "identificador × nombre", "identifier × name", "Intención de editor → identificador y nombre fijados", "Editor intent → fixed identifier and name"),
          p2: N("Fase 2: declarar las vistas", "Phase 2: declare the views", "al menos una representación válida", "at least one valid representation", "Editor identificado → representaciones con identificadores válidos", "Identified editor → representations with valid identifiers"),
          p3: N("Fase 3: elegir paleta y formularios", "Phase 3: choose palette and forms", "estrategia de herramientas y edición por campos", "tool strategy and field-based editing", "Representaciones → estrategia de paleta y de formularios", "Representations → palette and form strategy"),
          p4: N("Fase 4: activar capacidades y límites", "Phase 4: enable capabilities and limits", "capacidades × política de ejecución", "capabilities × runtime policy", "Diseño parcial → capacidades y límites de ejecución declarados", "Partial design → declared capabilities and runtime limits"),
          p5: N("Fase 5: auditar y confirmar", "Phase 5: audit and confirm", "definición válida y guardada", "definition valid and saved", "Definición por fases → informe de completitud y validez", "Phased definition → completeness and validity report"),
          d1: N("Verificar la completitud de las fases", "Verify the completeness of the phases", "fases completas o pendientes", "complete or pending phases", "Estado de las fases → guardar o volver a completar", "Phase status → save or go back to complete"),
          sv: N("Persistir el editor de modelos", "Persist the model editor", "estado: en diseño → guardada", "state: in design → saved", "Definición completa → definición guardada", "Complete definition → saved definition"),
          p6: N("Fase 6: compilar y desplegar", "Phase 6: compile and deploy", "estado: guardada → compilada", "state: saved → compiled", "Definición guardada → editor compilado", "Saved definition → compiled editor"),
          art: N("Editor definido y operativo", "Defined, operational editor", "guardado y, opcionalmente, compilado", "saved and, optionally, compiled", "Fases completadas → editor guardado y compilado", "Completed phases → saved, compiled editor")
        }
      },
      model_editor_author_structure: {
        nf: L("Ajuste fino de la definición por propiedades", "Fine adjustment of the definition by properties"),
        df: L("Cualquier elemento de la definición puede inspeccionarse y modificarse campo a campo para ajustes que el diseño visual no expone. Se rechazan valores fuera de rango, como tamaños negativos o el tamaño por defecto menor que el mínimo.", "Any definition element can be inspected and modified field by field for adjustments the visual design does not expose. Out-of-range values are rejected, such as negative sizes or a default size lower than the minimum."),
        nt: L("Inspector de propiedades del diseñador", "Designer property inspector"),
        dt: L("Se realiza en el inspector de propiedades del diseñador del frontend; de su implementación solo consta que edita el árbol de la definición en diseño y rechaza valores fuera de rango.", "It happens in the frontend designer's property inspector; of its implementation it is only known that it edits the definition tree under design and rejects out-of-range values."),
        nodes: {
          sel: N("Localizar un elemento de la definición", "Locate a definition element", "árbol de la definición → nodo elegido", "definition tree → chosen node", "Árbol de la definición → elemento seleccionado", "Definition tree → selected element"),
          ed: N("Modificar los campos del elemento", "Modify the element's fields", "valores directos por campo", "direct per-field values", "Elemento seleccionado → valores de campo modificados", "Selected element → modified field values"),
          val: N("Verificar los rangos admisibles", "Verify admissible ranges", "valores dentro o fuera de rango", "in-range or out-of-range values", "Valores propuestos → valores admitidos o rechazados", "Proposed values → admitted or rejected values"),
          art: N("Definición con valores ajustados", "Definition with adjusted values", "estado: en diseño", "state: in design", "Cambios aceptados → definición de diseño actualizada", "Accepted changes → updated design definition")
        }
      },
      model_editor_author_code: {
        nf: L("Parametrización de la salida de código", "Parametrising the code output"),
        df: L("Se fijan el lenguaje destino, el espacio de nombres y los destinos de generación del editor. Si no se toca, rigen los valores por defecto: Java, 'generated.model' y todos los formatos del metamodelo.", "The target language, the namespace and the generation targets of the editor are set. If left untouched, defaults apply: Java, 'generated.model' and all the metamodel's formats."),
        nt: L("CodeGenerationDesigner + CodeGenerationDefinition", "CodeGenerationDesigner + CodeGenerationDefinition"),
        dt: L("Vista Código del diseñador (CodeGenerationDesigner) que edita la CodeGenerationDefinition con valores por defecto java, 'generated.model' y targetIds igual a los ids de formatos del metamodelo.", "Designer Code view (CodeGenerationDesigner) editing the CodeGenerationDefinition with defaults java, 'generated.model' and targetIds equal to the metamodel's format ids."),
        nodes: {
          tg: N("Fijar lenguaje y ámbito de nombres", "Set language and naming scope", "lenguaje destino × espacio de nombres", "target language × namespace", "Valores por defecto → lenguaje y espacio de nombres elegidos", "Default values → chosen language and namespace"),
          dt: N("Delimitar los destinos de generación", "Delimit the generation targets", "formatos del metamodelo por defecto", "metamodel formats by default", "Conjunto de formatos → destinos de generación seleccionados", "Set of formats → selected generation targets"),
          art: N("Salida de código parametrizada", "Parametrised code output", "forma parte de la definición", "part of the definition", "Ajustes elegidos → definición de generación incorporada al editor", "Chosen settings → generation definition incorporated into the editor")
        }
      },
      model_editor_author_runtime: {
        nf: L("Compilación y verificación del editor desde el diseñador", "Compiling and verifying the editor from the designer"),
        df: L("El autor lanza la compilación de la definición guardada; si tiene éxito se muestra el resultado junto con la política de ejecución, y si no, los errores permanecen visibles. La definición de diseño no se modifica en ningún caso.", "The author launches compilation of the saved definition; if it succeeds, the result is shown together with the runtime policy, and if not, the errors stay visible. The design definition is never modified."),
        nt: L("compileRuntime → vista RUNTIME del diseñador", "compileRuntime → designer RUNTIME view"),
        dt: L("El diseñador ejecuta compileRuntime, que fija la vista RUNTIME (EditorDefinitionDesigner, línea 472) tras invocar la compilación del backend; no se ha trazado cómo se muestran los errores.", "The designer runs compileRuntime, which sets the RUNTIME view (EditorDefinitionDesigner, line 472) after invoking backend compilation; how errors are shown was not traced."),
        nodes: {
          cmp: N("Compilar la definición guardada", "Compile the saved definition", "definición guardada → editor ejecutable", "saved definition → runnable editor", "Definición guardada → editor generado sin cambiar la definición", "Saved definition → generated editor without changing the definition", { lt: "compileRuntime", st: ["diseñador → compilación de editor-definition", "designer → editor-definition compile"] }),
          d1: N("Evaluar el éxito de la compilación", "Evaluate the compilation's success", "correcta o con errores", "successful or with errors", "Resultado de compilación → mostrar resultado o dejar errores", "Compile result → show result or leave errors"),
          vw: N("Presentar compilación y política", "Present compilation and policy", "resultado × política de ejecución", "result × runtime policy", "Editor compilado → vista con resultado y política", "Compiled editor → view with result and policy", { lt: "RUNTIME", st: ["vista del diseñador (línea 472)", "designer view (line 472)"] }),
          art: N("Editor compilado y revisado", "Compiled, reviewed editor", "estado: compilada", "state: compiled", "Compilación correcta → editor listo para sesiones", "Successful compilation → editor ready for sessions")
        }
      },
      model_editor_visual_import: {
        nf: L("Conversión de una definición visual ajena con informe", "Converting a foreign visual definition with a report"),
        df: L("Se elige la tecnología de origen y el modo de asociación al metamodelo (existente, externo o incrustado); el análisis produce diagnósticos y correcciones clasificadas en seguras, a revisar o manuales. Tras revisarlas se confirma y la definición resultante queda en diseño.", "The source technology and the metamodel association mode (existing, external or embedded) are chosen; analysis yields diagnostics and fixes classified as safe, to review or manual. After reviewing them it is confirmed and the resulting definition is left in design."),
        nt: L("VisualDefinitionImportDialog + VisualImportMetamodelResolver", "VisualDefinitionImportDialog + VisualImportMetamodelResolver"),
        dt: L("Diálogo del frontend (VisualDefinitionImportDialog) y resolución del metamodelo en backend (VisualImportMetamodelResolver, modos EXISTING, EXTERNAL y EMBEDDED). Las tecnologías son SIRIUS_DESKTOP, SIRIUS_WEB, GMF y DRAWIO y las correcciones SAFE, REVIEW y MANUAL; el orden exacto de los pasos del diálogo no se ha trazado.", "Frontend dialog (VisualDefinitionImportDialog) and backend metamodel resolution (VisualImportMetamodelResolver, modes EXISTING, EXTERNAL and EMBEDDED). Technologies are SIRIUS_DESKTOP, SIRIUS_WEB, GMF and DRAWIO and fixes SAFE, REVIEW and MANUAL; the exact order of the dialog steps was not traced."),
        nodes: {
          tc: N("Identificar la tecnología de origen", "Identify the source technology", "Sirius, GMF o DrawIO × fichero", "Sirius, GMF or DrawIO × file", "Fichero de la definición ajena → tecnología de origen identificada", "Foreign definition file → identified source technology", { st: ["VisualDefinitionImportDialog", "VisualDefinitionImportDialog"] }),
          as: N("Vincular con el metamodelo", "Bind to the metamodel", "existente · externo · incrustado", "existing · external · embedded", "Origen identificado → metamodelo resuelto para la importación", "Identified source → metamodel resolved for the import", { lt: "VisualImportMetamodelResolver", st: ["modos EXISTING, EXTERNAL, EMBEDDED", "modes EXISTING, EXTERNAL, EMBEDDED"], tx: ["VisualImportMetamodelResolver resuelve el metamodelo según el modo de asociación EXISTING, EXTERNAL o EMBEDDED.", "VisualImportMetamodelResolver resolves the metamodel according to the association mode EXISTING, EXTERNAL or EMBEDDED."] }),
          an: N("Producir diagnósticos y correcciones", "Produce diagnostics and fixes", "origen × metamodelo → informe de calidad", "source × metamodel → quality report", "Definición ajena y metamodelo → informe con diagnósticos y correcciones", "Foreign definition and metamodel → report with diagnostics and fixes"),
          rv: N("Evaluar las correcciones por riesgo", "Evaluate fixes by risk", "seguras · a revisar · manuales", "safe · to review · manual", "Informe de calidad → correcciones aceptadas, revisadas o pendientes a mano", "Quality report → accepted, reviewed or manual-pending fixes"),
          d1: N("Decidir la confirmación de la conversión", "Decide whether to confirm the conversion", "aceptar o seguir revisando", "accept or keep reviewing", "Correcciones evaluadas → confirmar o volver a revisar", "Evaluated fixes → confirm or review again"),
          art: N("Definición convertida en diseño", "Converted definition in design", "estado: en diseño", "state: in design", "Conversión confirmada → definición de editor en diseño", "Confirmed conversion → editor definition in design")
        }
      }
    }
  });
})();
