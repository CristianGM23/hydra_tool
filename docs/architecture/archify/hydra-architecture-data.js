/* HYDRA architecture portal — capability & process data model.
   Synthesized from source-backed discovery (java packages under src/main/java/com/example/editor/
   and frontend/src). Each `sources` entry is a real repo-relative path citation; this file holds
   content only — rendering logic lives in hydra-architecture.html. */

window.CAPABILITIES = {

  metamodel: {
    archComponentId: "b_metamodel",
    kind: "backend",
    es: {
      level1: {
        what: "Define el lenguaje: qué tipos de elementos existen en un modelo HYDRA, sus atributos, sus relaciones y sus operaciones.",
        value: "Es la base de todo lo demás: validación, transformación, gramática, editor e integración trabajan siempre sobre un metamodelo.",
        inputs: ["Definición manual (formulario/canvas)", "Importación EMF/Ecore (genmodel, XMI)", "Proyección inferida desde una gramática", "Recuperación desde un proyecto externo (update-site)"],
        outputs: ["MetaModel persistido (documento canónico)", "Fingerprint de validez para generación", "Eventos core.metamodel.* (si la sesión los publica)"]
      },
      level2: [
        {
          name: "MetaModel",
          purpose: "Raíz de la definición de un lenguaje: agrupa clases, enumerados, tipos de datos y relaciones.",
          whenUsed: "Se crea una vez por lenguaje; se reabre cada vez que se edita el lenguaje o se valida/genera un editor.",
          properties: ["schemaVersion (actualmente \"2.1\")", "id, name, annotations", "classes[], enums[], dataTypes[], relations[] (listas planas, sin anidamiento por paquete)"],
          relationships: ["Contiene MetaClass, MetaEnum, MetaDataType", "Declara MetaRelation a nivel de metamodelo, no dentro de la clase origen"],
          cardinality: "1 MetaModel ↔ 0..N MetaClass/MetaEnum/MetaDataType/MetaRelation",
          io: { inputs: "Edición manual o importación EMF", outputs: "Documento canónico versionado" },
          lifecycle: "Guardar SIEMPRE dispara validación bloqueante (CanonicalMetamodelApplicationService.prepareForSave); no existen estados borrador/publicado — solo un flujo de borrado con diario de recuperación.",
          example: "El metamodelo de un DSL de procesos de negocio: clases Proceso, Tarea, Decision y una relación de contención Proceso→Tarea.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaModel.java", line: 1, label: "MetaModel" }]
        },
        {
          name: "MetaClass",
          purpose: "Tipo de elemento del lenguaje (clase, clase abstracta o interfaz).",
          whenUsed: "Cada 'sustantivo' del dominio modelado se define como una MetaClass.",
          properties: ["id, name, kind ∈ {CLASS, ABSTRACT_CLASS, INTERFACE}", "attributes[]: MetaAttribute", "operations[]: MetaOperation"],
          relationships: ["La herencia NO es un campo de MetaClass: se expresa como MetaRelation de kind INHERITANCE/IMPLEMENTATION entre dos clases"],
          cardinality: "1 MetaModel ↔ 0..N MetaClass; 1 MetaClass ↔ 0..N MetaAttribute/MetaOperation",
          io: { inputs: "Definición manual o inferencia desde gramática/EMF", outputs: "Instancias de modelo de ese tipo" },
          lifecycle: "Al borrar una MetaClass se dispara un cascade automático (MetamodelDependentArtifactCleanupService) que limpia validaciones, editor-definitions, transformaciones, etc. que la referencian.",
          example: "La clase Tarea, con atributos nombre y duracion y una relación de herencia hacia TareaBase.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaClass.java", line: 1, label: "MetaClass" }]
        },
        {
          name: "MetaAttribute",
          purpose: "Dato simple de una clase (texto, número, booleano, enumerado, tipo de datos propio).",
          whenUsed: "Para representar valores que no son relaciones a otro elemento del modelo.",
          properties: ["id, name, type: MetaTypeReference", "multiplicity, ordered, unique, defaultValue"],
          relationships: ["Las banderas específicas de Ecore (p. ej. volatile/transient) viven aparte, en EcoreAttributeConfiguration, no en MetaAttribute"],
          cardinality: "1 MetaClass ↔ 0..N MetaAttribute",
          io: { inputs: "Definición manual", outputs: "Campo editable en formularios del editor generado" },
          lifecycle: "Se valida junto con el resto del metamodelo al guardar.",
          example: "Tarea.duracion: entero, multiplicidad 1..1.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaAttribute.java", line: 1, label: "MetaAttribute" }]
        },
        {
          name: "MetaRelation",
          purpose: "Vínculo entre dos clases: referencia, contención, agregación, dependencia, herencia o implementación de interfaz.",
          whenUsed: "Para conectar dos tipos del lenguaje (no para valores simples, que son MetaAttribute).",
          properties: ["id, sourceTypeId, targetTypeId", "sourceMultiplicity, targetMultiplicity", "oppositeRelationId (relación inversa opcional)", "ordered, unique", "kind ∈ {REFERENCE, CONTAINMENT, AGGREGATION, DEPENDENCY, INHERITANCE, IMPLEMENTATION}"],
          relationships: ["Se declara a nivel de MetaModel.relations, no anidada dentro de la clase de origen"],
          cardinality: "1 MetaModel ↔ 0..N MetaRelation; cada relación conecta exactamente 2 MetaClass",
          io: { inputs: "Definición manual", outputs: "Estructura de contención/referencia navegable en instancias de modelo" },
          lifecycle: "Participa en el fingerprint de validación; cambiarla puede invalidar editores/transformaciones que dependen de ella.",
          example: "Proceso --CONTAINMENT--> Tarea (1..1 → 0..*).",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaRelation.java", line: 1, label: "MetaRelation" }]
        },
        {
          name: "MetaOperation + OperationBehaviorModel",
          purpose: "Firma de una operación del tipo (MetaOperation) separada deliberadamente de su implementación (OperationBehaviorModel/OperationBinding).",
          whenUsed: "Cuando el lenguaje necesita comportamiento ejecutable asociado a una clase (p. ej. un cálculo).",
          properties: ["MetaOperation: signature (nombre, parámetros, tipo de retorno)", "OperationBinding: operationId → UNIMPLEMENTED | EXPRESSION | ACTION | EXTERNAL"],
          relationships: ["Documento de comportamiento SEPARADO y persistido aparte del MetaModel"],
          cardinality: "1 MetaClass ↔ 0..N MetaOperation; 1 MetaOperation ↔ 0..1 OperationBinding",
          io: { inputs: "Firma definida junto a la clase; implementación definida aparte", outputs: "Comportamiento invocable en tiempo de ejecución del modelo" },
          lifecycle: "La firma puede existir sin implementación (UNIMPLEMENTED) sin bloquear el guardado del metamodelo.",
          example: "Tarea.calcularRetraso(): entero, implementado como EXPRESSION usando el kernel semantic.expression.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaOperation.java", line: 1, label: "MetaOperation" }]
        }
      ],
      authoring: {
        summary: "El lienzo (React Flow) y el panel de propiedades son dos caras del mismo editor: el lienzo sirve para crear y conectar elementos (clic en la paleta, clic-clic o arrastre entre puertos), y TODA la edición detallada de campos ocurre en el panel de propiedades, nunca en el propio nodo del lienzo (los nodos son de solo lectura visual). Existe además un modo 'Guiado' alternativo (asistente lineal) y una vía de importación completa.",
        elements: [
          { element: "MetaClass / MetaEnum / MetaDataType", requiredness: "ALTERNATIVE", modes: [
            { mode: "Lienzo (clic en paleta)", detail: "Botones de la paleta (Clase, Clase abstracta, Interfaz, Enumerado, Tipo de dato) añaden el elemento con un clic; no es arrastrar-y-soltar. El nodo resultante es de solo lectura en el lienzo." },
            { mode: "Asistente guiado", detail: "Modo 'Guiado' alternativo: pasos lineales identidad→clasificadores→estructura→validación→guardar, con botones simples para añadir clases/enumerados/tipos de dato." },
            { mode: "Importación", detail: "Diálogo de 4 pasos (origen→estrategia→análisis→revisión) contra una lista de formatos que entrega el backend dinámicamente; incluye recuperación desde Ecore/GenModel/Xtext y desde código fuente (TS/JS/Go) o bloques Blockly." }
          ]},
          { element: "Atributos, operaciones y relaciones de una clase", requiredness: "OPTIONAL", modes: [
            { mode: "Panel de propiedades (formulario)", detail: "Única vía de edición: nombre, tipo, multiplicidad, ordered/unique, valor por defecto (atributo); nombre, comportamiento, tipo de retorno, parámetros, excepciones (operación); tipo, origen/destino, multiplicidad, opuesta (relación)." }
          ]},
          { element: "Conexión entre clases (MetaRelation)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Lienzo: clic-clic", detail: "Se elige antes el tipo de relación en la paleta (de los 6 tipos), luego clic en un puerto origen (azul) y clic en un puerto destino (verde)." },
            { mode: "Lienzo: arrastrar", detail: "Arrastrar directamente entre dos puertos usando la conexión nativa de React Flow — mismo resultado, mismo tipo preseleccionado." },
            { mode: "Panel de propiedades: 'Añadir referencia'", detail: "Crea directamente una relación de tipo REFERENCE sin pasar por el lienzo; el tipo puede cambiarse después en el panel." }
          ]},
          { element: "Herencia / Implementación de interfaz", requiredness: "OPTIONAL", modes: [
            { mode: "Lienzo (mismo mecanismo que cualquier relación)", detail: "Se valida en vivo: solo clase↔interfaz puede usar IMPLEMENTATION; INHERITANCE exige que ambos extremos sean del mismo tipo (clase↔clase o interfaz↔interfaz); se bloquean los ciclos de herencia." }
          ]}
        ]
      },
      level3: "Un MetaModel es un grafo plano de MetaClass/MetaEnum/MetaDataType conectados por MetaRelation declaradas aparte (no anidadas). La herencia es un caso particular de MetaRelation, no un campo propio de MetaClass. El comportamiento ejecutable de una operación vive en un documento independiente (OperationBehaviorModel), de modo que la estructura del lenguaje y su comportamiento evolucionan por separado.",
      level4: "Guardar un metamodelo dispara SIEMPRE una validación bloqueante de su propia consistencia estructural (CanonicalMetamodelApplicationService.prepareForSave). Aparte existe una comprobación de 'preparación para generación' basada en comparación de fingerprint (MetamodelValidationApplicationService). No hay estados borrador/publicado/obsoleto: solo un flujo de borrado con diario de recuperación (MetamodelDeletionJournal: PREPARED → PRIMARY_DELETED → RECOVERY_REQUIRED/COMPLETED/ABORTED). Borrar una MetaClass dispara un cascade automático que limpia artefactos dependientes en 9+ tipos de capa, escaneando tanto por convención de nombre de archivo como por referencias JSON.",
      level5: {
        incoming: ["Gramática (Grammar): la compilación de una gramática puede inferir y proyectar un MetaModel real (no ilustrativo)", "Recuperación externa: update-sites y proyectos externos pueden materializar un MetaModel real a partir de EMF/genmodel/bytecode/inferencia"],
        outgoing: ["Model Editor (EditorDefinitionModel.metamodelId es FK obligatoria)", "Validación (ValidationRule.context referencia clases/atributos del metamodelo)", "Transformación (endpoints de origen/destino referencian un metamodelo)", "Generación/Publicación (ModelCodeGenerationService exige metamodelo + modelo + target)"]
      },
      level6: {
        runtime: "CanonicalMetamodelApplicationService coordina guardado+validación; MetamodelDependentArtifactCleanupService ejecuta el cascade de borrado.",
        services: ["CanonicalMetamodelApplicationService", "MetamodelValidationApplicationService", "MetamodelDependentArtifactCleanupService"],
        apis: ["REST sobre /metamodels (CRUD + guardado con validación embebida)"],
        sources: [
          { path: "src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java", line: 147, end_line: 179, label: "prepareForSave: validación bloqueante al guardar" },
          { path: "src/main/java/com/example/editor/metamodel/core/MetaModel.java", line: 1, label: "Estructura raíz del metamodelo" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines the language: which element types exist in a HYDRA model, their attributes, relationships and operations.",
        value: "It is the foundation everything else builds on: validation, transformation, grammar, editor and integration all work over a metamodel.",
        inputs: ["Manual definition (form/canvas)", "EMF/Ecore import (genmodel, XMI)", "Inferred projection from a grammar", "Recovery from an external project (update-site)"],
        outputs: ["Persisted MetaModel (canonical document)", "Validity fingerprint for generation", "core.metamodel.* events (if the session publishes them)"]
      },
      level2: [
        {
          name: "MetaModel",
          purpose: "Root of a language definition: groups classes, enums, data types and relations.",
          whenUsed: "Created once per language; reopened whenever the language is edited or an editor is validated/generated.",
          properties: ["schemaVersion (currently \"2.1\")", "id, name, annotations", "classes[], enums[], dataTypes[], relations[] (flat lists, no package nesting)"],
          relationships: ["Contains MetaClass, MetaEnum, MetaDataType", "Declares MetaRelation at model level, not nested inside the source class"],
          cardinality: "1 MetaModel ↔ 0..N MetaClass/MetaEnum/MetaDataType/MetaRelation",
          io: { inputs: "Manual edit or EMF import", outputs: "Versioned canonical document" },
          lifecycle: "Saving ALWAYS triggers blocking validation (CanonicalMetamodelApplicationService.prepareForSave); there is no draft/published state — only a deletion flow with a recovery journal.",
          example: "A business-process DSL metamodel: classes Process, Task, Decision and a Process→Task containment relation.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaModel.java", line: 1, label: "MetaModel" }]
        },
        {
          name: "MetaClass",
          purpose: "A language element type (class, abstract class or interface).",
          whenUsed: "Every domain 'noun' being modeled is defined as a MetaClass.",
          properties: ["id, name, kind ∈ {CLASS, ABSTRACT_CLASS, INTERFACE}", "attributes[]: MetaAttribute", "operations[]: MetaOperation"],
          relationships: ["Inheritance is NOT a MetaClass field: it is expressed as a MetaRelation of kind INHERITANCE/IMPLEMENTATION between two classes"],
          cardinality: "1 MetaModel ↔ 0..N MetaClass; 1 MetaClass ↔ 0..N MetaAttribute/MetaOperation",
          io: { inputs: "Manual definition or inference from grammar/EMF", outputs: "Model instances of that type" },
          lifecycle: "Deleting a MetaClass triggers an automatic cascade (MetamodelDependentArtifactCleanupService) cleaning up validations, editor-definitions, transformations, etc. that reference it.",
          example: "Class Task, with attributes name and duration and an inheritance relation to TaskBase.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaClass.java", line: 1, label: "MetaClass" }]
        },
        {
          name: "MetaAttribute",
          purpose: "Simple data held by a class (text, number, boolean, enum, custom data type).",
          whenUsed: "To represent values that are not references to another model element.",
          properties: ["id, name, type: MetaTypeReference", "multiplicity, ordered, unique, defaultValue"],
          relationships: ["Ecore-specific flags (e.g. volatile/transient) live separately in EcoreAttributeConfiguration, not in MetaAttribute"],
          cardinality: "1 MetaClass ↔ 0..N MetaAttribute",
          io: { inputs: "Manual definition", outputs: "Editable field in the generated editor's forms" },
          lifecycle: "Validated together with the rest of the metamodel on save.",
          example: "Task.duration: integer, multiplicity 1..1.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaAttribute.java", line: 1, label: "MetaAttribute" }]
        },
        {
          name: "MetaRelation",
          purpose: "Link between two classes: reference, containment, aggregation, dependency, inheritance or interface implementation.",
          whenUsed: "To connect two language types (not for simple values, which are MetaAttribute).",
          properties: ["id, sourceTypeId, targetTypeId", "sourceMultiplicity, targetMultiplicity", "oppositeRelationId (optional inverse relation)", "ordered, unique", "kind ∈ {REFERENCE, CONTAINMENT, AGGREGATION, DEPENDENCY, INHERITANCE, IMPLEMENTATION}"],
          relationships: ["Declared at MetaModel.relations level, not nested inside the source class"],
          cardinality: "1 MetaModel ↔ 0..N MetaRelation; each relation connects exactly 2 MetaClass",
          io: { inputs: "Manual definition", outputs: "Navigable containment/reference structure in model instances" },
          lifecycle: "Participates in the validation fingerprint; changing it can invalidate editors/transformations that depend on it.",
          example: "Process --CONTAINMENT--> Task (1..1 → 0..*).",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaRelation.java", line: 1, label: "MetaRelation" }]
        },
        {
          name: "MetaOperation + OperationBehaviorModel",
          purpose: "A type's operation signature (MetaOperation) deliberately kept separate from its implementation (OperationBehaviorModel/OperationBinding).",
          whenUsed: "When the language needs executable behavior attached to a class (e.g. a computation).",
          properties: ["MetaOperation: signature (name, parameters, return type)", "OperationBinding: operationId → UNIMPLEMENTED | EXPRESSION | ACTION | EXTERNAL"],
          relationships: ["Behavior document persisted SEPARATELY from the MetaModel"],
          cardinality: "1 MetaClass ↔ 0..N MetaOperation; 1 MetaOperation ↔ 0..1 OperationBinding",
          io: { inputs: "Signature defined with the class; implementation defined separately", outputs: "Behavior invokable at model-instance runtime" },
          lifecycle: "A signature can exist without an implementation (UNIMPLEMENTED) without blocking the metamodel save.",
          example: "Task.computeDelay(): integer, implemented as EXPRESSION using the shared semantic.expression kernel.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/core/MetaOperation.java", line: 1, label: "MetaOperation" }]
        }
      ],
      authoring: {
        summary: "The canvas (React Flow) and the property panel are two sides of the same editor: the canvas is for creating and connecting elements (palette click, click-click or drag between ports), and ALL detailed field editing happens in the property panel, never on the canvas node itself (nodes are visually read-only). There is also an alternative 'Guided' wizard mode and a full import path.",
        elements: [
          { element: "MetaClass / MetaEnum / MetaDataType", requiredness: "ALTERNATIVE", modes: [
            { mode: "Canvas (palette click)", detail: "Palette buttons (Class, Abstract class, Interface, Enum, Data type) add the element with one click; not drag-and-drop. The resulting node is visually read-only on the canvas." },
            { mode: "Guided wizard", detail: "Alternative 'Guided' mode: linear steps identity→classifiers→structure→validation→save, with simple buttons to add classes/enums/data types." },
            { mode: "Import", detail: "A 4-step dialog (source→strategy→analysis→review) against a format list the backend supplies dynamically; includes recovery from Ecore/GenModel/Xtext and from source code (TS/JS/Go) or Blockly blocks." }
          ]},
          { element: "A class's attributes, operations and relations", requiredness: "OPTIONAL", modes: [
            { mode: "Property panel (form)", detail: "The only editing path: name, type, multiplicity, ordered/unique, default value (attribute); name, behavior, return type, parameters, exceptions (operation); type, source/target, multiplicity, opposite (relation)." }
          ]},
          { element: "Connection between classes (MetaRelation)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Canvas: click-click", detail: "The relation kind is chosen first in the palette (one of 6 kinds), then click a source port (blue) and a target port (green)." },
            { mode: "Canvas: drag", detail: "Dragging directly between two ports using React Flow's native connect — same result, same preselected kind." },
            { mode: "Property panel: 'Add reference'", detail: "Directly creates a REFERENCE-kind relation without using the canvas; the kind can be changed afterward in the panel." }
          ]},
          { element: "Inheritance / interface implementation", requiredness: "OPTIONAL", modes: [
            { mode: "Canvas (same mechanism as any relation)", detail: "Validated live: only class↔interface can use IMPLEMENTATION; INHERITANCE requires both endpoints to be the same kind (class↔class or interface↔interface); inheritance cycles are blocked." }
          ]}
        ]
      },
      level3: "A MetaModel is a flat graph of MetaClass/MetaEnum/MetaDataType connected by MetaRelation declared separately (not nested). Inheritance is a special case of MetaRelation, not a MetaClass field of its own. An operation's executable behavior lives in an independent document (OperationBehaviorModel), so the language's structure and its behavior evolve separately.",
      level4: "Saving a metamodel ALWAYS triggers blocking validation of its own structural consistency (CanonicalMetamodelApplicationService.prepareForSave). Separately, a 'generation readiness' check exists based on fingerprint comparison (MetamodelValidationApplicationService). There is no draft/published/deprecated state: only a deletion flow with a recovery journal (MetamodelDeletionJournal: PREPARED → PRIMARY_DELETED → RECOVERY_REQUIRED/COMPLETED/ABORTED). Deleting a MetaClass triggers an automatic cascade cleaning up dependent artifacts across 9+ layer types, scanning both by file-naming convention and JSON reference.",
      level5: {
        incoming: ["Grammar: compiling a grammar can infer and project a real (not illustrative) MetaModel", "External recovery: update-sites and external projects can materialize a real MetaModel from EMF/genmodel/bytecode/inference"],
        outgoing: ["Model Editor (EditorDefinitionModel.metamodelId is a required FK)", "Validation (ValidationRule.context references model classes/attributes)", "Transformation (source/target endpoints reference a metamodel)", "Generation/Publication (ModelCodeGenerationService requires metamodel + model + target)"]
      },
      level6: {
        runtime: "CanonicalMetamodelApplicationService coordinates save+validation; MetamodelDependentArtifactCleanupService runs the deletion cascade.",
        services: ["CanonicalMetamodelApplicationService", "MetamodelValidationApplicationService", "MetamodelDependentArtifactCleanupService"],
        apis: ["REST over /metamodels (CRUD + save with embedded validation)"],
        sources: [
          { path: "src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java", line: 147, end_line: 179, label: "prepareForSave: blocking validation on save" },
          { path: "src/main/java/com/example/editor/metamodel/core/MetaModel.java", line: 1, label: "Metamodel root structure" }
        ]
      }
    }
  },

  model_editor: {
    archComponentId: "b_model_editor",
    kind: "frontend",
    es: {
      level1: {
        what: "Define cómo se ve y se edita un modelo: representaciones gráficas/árbol/tabla/formulario, paleta de herramientas y políticas de ejecución.",
        value: "Convierte un metamodelo abstracto en un editor de modelos concreto y usable, sin tocar la estructura del lenguaje.",
        inputs: ["MetaModel ya guardado (FK obligatoria)", "Notación visual (iconos, formas, colores) definida por el autor del editor"],
        outputs: ["EditorDefinitionModel compilado", "Sesión de edición (EditorModelSession) con un CanonicalModelDocument"]
      },
      level2: [
        {
          name: "EditorDefinitionModel",
          purpose: "Documento raíz que define un editor completo para un metamodelo: representaciones, paleta, formularios y política de ejecución.",
          whenUsed: "Una vez por combinación editor+metamodelo; se recompila cuando cambia la definición o el metamodelo.",
          properties: ["schemaVersion, id, metamodelId (FK obligatoria)", "name, representations[]", "palette: ToolPaletteDefinition", "forms[]", "capabilityManifest", "runtimePolicy: EditorRuntimePolicy", "codeGeneration"],
          relationships: ["metamodelId referencia un MetaModel existente", "representations[] contiene RepresentationDefinition"],
          cardinality: "1 MetaModel ↔ 0..N EditorDefinitionModel",
          io: { inputs: "Metamodelo + decisiones de mapeo visual", outputs: "Editor compilado, abrible como EditorModelSession" },
          lifecycle: "Se compila (EditorDefinitionCompiler) antes de poder abrir una sesión; la compilación NO consulta el registro NotationDefinition aparte — toda la notación vive en NodeMapping/EdgeMapping.",
          example: "Editor de diagramas de Proceso: representación 'diagram' con nodos Tarea/Decision y aristas de flujo.",
          evidence: [{ path: "src/main/java/com/example/editor/definition/EditorDefinitionModel.java", line: 11, label: "EditorDefinitionModel" }]
        },
        {
          name: "RepresentationDefinition",
          purpose: "Una vista concreta del modelo: diagrama, árbol, tabla, formulario, matriz o dashboard.",
          whenUsed: "Cuando el editor necesita mostrar el modelo de una forma determinada (p. ej. un diagrama más una tabla de detalle).",
          properties: ["kind ∈ {diagram, tree, table, form, matrix, dashboard}", "rootClassIds[]", "nodeMappings[], edgeMappings[]", "toolIds[], layers[], filters[]"],
          relationships: ["Contiene NodeMapping y EdgeMapping; referencia MetaClass/MetaRelation del metamodelo"],
          cardinality: "1 EditorDefinitionModel ↔ 1..N RepresentationDefinition",
          io: { inputs: "Selección de clases raíz + mapeos", outputs: "Vista interactiva en el workbench" },
          lifecycle: "Puede abrirse simultáneamente más de una representación sobre el mismo modelo (openRepresentationIds[] en la sesión).",
          example: "Representación 'diagram' con nodos Tarea (forma rectángulo) y aristas de secuencia.",
          evidence: [{ path: "src/main/java/com/example/editor/definition/RepresentationDefinition.java", line: 1, label: "RepresentationDefinition" }]
        },
        {
          name: "NodeMapping / EdgeMapping / ConditionalStyleRule",
          purpose: "Mapeo visual de una clase o relación: forma, tamaño, expresión de etiqueta, contención, estilos condicionales.",
          whenUsed: "Para decidir cómo se dibuja cada tipo de elemento y arista en una representación de diagrama.",
          properties: ["NodeMapping: targetClassId, shape, width/height, labelExpressionId, imageAssetPath, container, containmentRelationIds, styles, conditionalStyles[]", "EdgeMapping: relationId o SemanticEdgeDescriptor, sourceClassId, targetClassId, lineType, labelExpressionId, styles, conditionalStyles", "ConditionalStyleRule: conditionExpressionId, styles, priority"],
          relationships: ["TODA la apariencia visual vive aquí, no en el registro NotationDefinition (que solo usan NotationController/NotationRegistryService; el compilador del editor nunca lo referencia)"],
          cardinality: "1 RepresentationDefinition ↔ 0..N NodeMapping/EdgeMapping; 1 mapping ↔ 0..N ConditionalStyleRule",
          io: { inputs: "Clase/relación objetivo + reglas de estilo", outputs: "Nodo o arista dibujada en el canvas" },
          lifecycle: "Las reglas condicionales se evalúan en tiempo real contra el estado del modelo (vía semantic.expression).",
          example: "Un NodeMapping para Tarea cuyo color cambia a rojo si está atrasada (ConditionalStyleRule).",
          evidence: [{ path: "src/main/java/com/example/editor/definition/NodeMapping.java", line: 1, label: "NodeMapping" }]
        },
        {
          name: "EditorModelSession",
          purpose: "Sesión activa de edición: une un modelo canónico, un editor compilado y el estado de validación más reciente.",
          whenUsed: "Se crea al abrir un modelo en el workbench.",
          properties: ["id, metamodelId, editorId, sourceEditorEtag", "activeRepresentationId, openRepresentationIds[]", "model: CanonicalModelDocument", "latestValidationReport, revision"],
          relationships: ["model es un CanonicalModelDocument (camino moderno, independiente de EMF); existe un camino LEGACY paralelo vía ModelDocumentService/DynamicModelService para EMF/XMI"],
          cardinality: "1 sesión ↔ 1 modelo canónico ↔ 1 editor compilado",
          io: { inputs: "Apertura de un modelo existente o nuevo", outputs: "Representaciones interactivas + diagnósticos" },
          lifecycle: "La validación automática al cambiar/abrir NO ocurre por defecto: depende de EditorRuntimePolicy.validationTriggers (por defecto solo MANUAL); el camino legacy EMF no tiene ningún gancho de validación.",
          example: "Sesión abierta sobre el modelo 'Proceso de Onboarding', con la representación diagram activa.",
          evidence: [{ path: "src/main/java/com/example/editor/definition/runtime/session/EditorModelSession.java", line: 18, label: "EditorModelSession" }]
        }
      ],
      authoring: {
        summary: "Hay dos superficies de autoría distintas y en cadena: (1) el Diseñador de Editor-Definition, donde se configura CÓMO se verá y editará el modelo (árbol + formularios, nunca un lienzo libre); y (2) la sesión de edición de modelos en sí, que SÍ es un lienzo real de arrastrar/conectar — pero solo aparece una vez existe una Editor-Definition compilada para ese metamodelo; si no, se usa un lienzo básico de respaldo (paleta + React Flow, basado en la antigua 'notación', sin relación con NodeMapping/EdgeMapping).",
        elements: [
          { element: "Representaciones, mapeos de nodo/arista y paleta (EditorDefinitionModel)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Árbol + formulario (vista 'Definición')", detail: "Patrón dominante: árbol jerárquico a la izquierda (metamodelo→clases/relaciones, representaciones→mapeos, paleta→herramientas, formularios→secciones); al seleccionar un nodo del árbol se abre su formulario de propiedades a la derecha. No existe un meta-lienzo donde se dibuje la representación." },
            { mode: "Asistente guiado", detail: "6 pasos: identidad → representaciones → herramientas/formularios → capacidades → revisión → runtime, con comprobaciones de completitud por paso." },
            { mode: "Vista 'Representaciones de elemento'", detail: "Tercera forma de navegar: lista de clases/relaciones del metamodelo con, por cada una, botones '+ Mostrarla en esta vista' / 'Editar apariencia' — edición basada en formulario igual que el árbol." },
            { mode: "Importación visual", detail: "Diálogo dedicado que recupera definiciones visuales existentes de Sirius Desktop (.odesign), Sirius Web, GMF o Draw.io, con métricas de calidad de recuperación (elementos revisados, referencias sin resolver, correcciones seguras vs. que necesitan revisión) antes de confirmar." }
          ]},
          { element: "Estilo visual de un nodo/arista (forma, color, iconos) y estilos condicionales", requiredness: "OPTIONAL", modes: [
            { mode: "Formulario con vista previa SVG estática", detail: "Campos de estilo (forma, color de relleno/borde, grosor, posición de etiqueta…) con una vista previa SVG que se actualiza en vivo pero no es interactiva (no se arrastra ni se dibuja sobre ella)." },
            { mode: "Reglas condicionales", detail: "Lista de reglas con nombre, prioridad y una condición construida con el mismo ExpressionBuilder (guiado por cláusulas o árbol avanzado) que usan Validación y Transformación; detección automática de condiciones duplicadas." }
          ]},
          { element: "Herramientas de la paleta (crear nodo, crear arista, invocar operación, ejecutar acción, validar)", requiredness: "CONDITIONAL", modes: [
            { mode: "Formulario por tipo de herramienta", detail: "Cada tipo de herramienta tiene su propio formulario (clase destino para crear nodo, relación+mapeo para crear arista, operación a invocar, acción a ejecutar, alcance de validación)." },
            { mode: "Generadas automáticamente", detail: "Las herramientas de creación de nodo/arista NO se crean sueltas desde el formulario de paleta: se generan automáticamente al mapear una clase o relación, para que la paleta nunca se desincronice del metamodelo." }
          ]},
          { element: "Instancia de modelo: nodos y aristas del diagrama", requiredness: "ALTERNATIVE", modes: [
            { mode: "Lienzo dirigido por Editor-Definition (EditorRuntimeCanvas)", detail: "SI existe una Editor-Definition compilada: lienzo real de arrastrar/conectar — crear nodo con una herramienta de la paleta ejecutable, crear arista arrastrando entre nodos o con una herramienta CREATE_EDGE, mover/redimensionar con persistencia de layout independiente del modelo semántico. Este lienzo ocupa todo el workbench." },
            { mode: "Lienzo básico de respaldo (paleta + React Flow)", detail: "SOLO si no hay Editor-Definition compilada disponible para ese metamodelo: paleta antigua basada en 'notación' con arrastrar-y-soltar al lienzo y conexión nativa de React Flow." },
            { mode: "Importación EMF por ID", detail: "Abrir un modelo ya guardado como sesión EMF indicando su ID, en vez de crear uno en blanco." },
            { mode: "Formulario generado", detail: "Seleccionar un elemento abre un formulario generado específico para su clase, para editar sus valores de atributo." }
          ]}
        ]
      },
      level3: "Un EditorDefinitionModel agrupa N RepresentationDefinition, cada una con su propio conjunto de NodeMapping/EdgeMapping que referencian clases y relaciones del MetaModel asociado. El registro NotationDefinition existe en el código pero está desconectado del camino de compilación real del editor (confirmado por grep: solo lo usan NotationController/NotationRegistryService). Una EditorModelSession es la instancia en memoria que une modelo + editor + último informe de validación.",
      level4: "El flujo es: (1) se define el EditorDefinitionModel sobre un metamodelo ya existente; (2) EditorDefinitionCompiler lo compila; (3) al abrir un modelo se crea una EditorModelSession; (4) la validación se dispara según la política configurada (MANUAL por defecto) — no es automática salvo que el autor del editor la active explícitamente para ON_CHANGE/ON_OPEN. El camino legado EMF (ModelMutationService) no tiene ningún gancho de validación ni de eventos.",
      level5: {
        incoming: ["Metamodel (FK obligatoria sobre la que se define todo el mapeo visual)", "Grammar (el editor de diagrama de clases inferido usa el mismo componente MetamodelReadOnlyCanvas)"],
        outgoing: ["Validación (dispara diagnósticos sobre el CanonicalModelDocument de la sesión)", "Eventos (solo si runtimePolicy().publishEvents() está activado para esa sesión)", "Transformación/Generación (consumen el modelo editado como entrada)"]
      },
      level6: {
        runtime: "EditorDefinitionCompiler compila la definición; EditorModelSession vive en memoria por sesión de edición abierta.",
        services: ["EditorDefinitionCompiler", "EditorModelSession", "EditorRuntimePolicy"],
        apis: ["REST de apertura/guardado de sesión de editor; frontend ProblemsPanel/QuickFixDesigner consumen esta capa"],
        sources: [
          { path: "src/main/java/com/example/editor/definition/EditorDefinitionModel.java", line: 11, label: "EditorDefinitionModel" },
          { path: "src/main/java/com/example/editor/definition/runtime/session/EditorModelSession.java", line: 18, label: "EditorModelSession" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines how a model looks and is edited: diagram/tree/table/form representations, tool palette and runtime policy.",
        value: "Turns an abstract metamodel into a concrete, usable model editor, without touching the language's structure.",
        inputs: ["An already-saved MetaModel (required FK)", "Visual notation (icons, shapes, colors) defined by the editor's author"],
        outputs: ["Compiled EditorDefinitionModel", "Editing session (EditorModelSession) with a CanonicalModelDocument"]
      },
      level2: [
        {
          name: "EditorDefinitionModel",
          purpose: "Root document defining a complete editor for a metamodel: representations, palette, forms and runtime policy.",
          whenUsed: "Once per editor+metamodel combination; recompiled whenever the definition or the metamodel changes.",
          properties: ["schemaVersion, id, metamodelId (required FK)", "name, representations[]", "palette: ToolPaletteDefinition", "forms[]", "capabilityManifest", "runtimePolicy: EditorRuntimePolicy", "codeGeneration"],
          relationships: ["metamodelId references an existing MetaModel", "representations[] holds RepresentationDefinition entries"],
          cardinality: "1 MetaModel ↔ 0..N EditorDefinitionModel",
          io: { inputs: "Metamodel + visual mapping decisions", outputs: "Compiled editor, openable as an EditorModelSession" },
          lifecycle: "Compiled (EditorDefinitionCompiler) before a session can be opened; compilation does NOT consult the separate NotationDefinition registry — all notation lives in NodeMapping/EdgeMapping.",
          example: "Process diagram editor: a 'diagram' representation with Task/Decision nodes and flow edges.",
          evidence: [{ path: "src/main/java/com/example/editor/definition/EditorDefinitionModel.java", line: 11, label: "EditorDefinitionModel" }]
        },
        {
          name: "RepresentationDefinition",
          purpose: "A concrete model view: diagram, tree, table, form, matrix or dashboard.",
          whenUsed: "Whenever the editor needs to show the model in a given way (e.g. a diagram plus a detail table).",
          properties: ["kind ∈ {diagram, tree, table, form, matrix, dashboard}", "rootClassIds[]", "nodeMappings[], edgeMappings[]", "toolIds[], layers[], filters[]"],
          relationships: ["Holds NodeMapping and EdgeMapping; references MetaClass/MetaRelation from the metamodel"],
          cardinality: "1 EditorDefinitionModel ↔ 1..N RepresentationDefinition",
          io: { inputs: "Root-class selection + mappings", outputs: "Interactive view in the workbench" },
          lifecycle: "More than one representation can be open simultaneously over the same model (openRepresentationIds[] in the session).",
          example: "A 'diagram' representation with Task nodes (rectangle shape) and sequence edges.",
          evidence: [{ path: "src/main/java/com/example/editor/definition/RepresentationDefinition.java", line: 1, label: "RepresentationDefinition" }]
        },
        {
          name: "NodeMapping / EdgeMapping / ConditionalStyleRule",
          purpose: "Visual mapping of a class or relation: shape, size, label expression, containment, conditional styles.",
          whenUsed: "To decide how each element and edge type is drawn in a diagram representation.",
          properties: ["NodeMapping: targetClassId, shape, width/height, labelExpressionId, imageAssetPath, container, containmentRelationIds, styles, conditionalStyles[]", "EdgeMapping: relationId or SemanticEdgeDescriptor, sourceClassId, targetClassId, lineType, labelExpressionId, styles, conditionalStyles", "ConditionalStyleRule: conditionExpressionId, styles, priority"],
          relationships: ["ALL visual appearance lives here, not in the separate NotationDefinition registry (used only by NotationController/NotationRegistryService; the editor compiler never references it)"],
          cardinality: "1 RepresentationDefinition ↔ 0..N NodeMapping/EdgeMapping; 1 mapping ↔ 0..N ConditionalStyleRule",
          io: { inputs: "Target class/relation + styling rules", outputs: "Node or edge drawn on the canvas" },
          lifecycle: "Conditional rules are evaluated live against model state (via semantic.expression).",
          example: "A NodeMapping for Task whose color turns red when it is overdue (ConditionalStyleRule).",
          evidence: [{ path: "src/main/java/com/example/editor/definition/NodeMapping.java", line: 1, label: "NodeMapping" }]
        },
        {
          name: "EditorModelSession",
          purpose: "Active editing session: ties a canonical model, a compiled editor and the most recent validation state together.",
          whenUsed: "Created when a model is opened in the workbench.",
          properties: ["id, metamodelId, editorId, sourceEditorEtag", "activeRepresentationId, openRepresentationIds[]", "model: CanonicalModelDocument", "latestValidationReport, revision"],
          relationships: ["model is a CanonicalModelDocument (modern, EMF-independent path); a parallel LEGACY path exists via ModelDocumentService/DynamicModelService for EMF/XMI"],
          cardinality: "1 session ↔ 1 canonical model ↔ 1 compiled editor",
          io: { inputs: "Opening an existing or new model", outputs: "Interactive representations + diagnostics" },
          lifecycle: "Auto-validation on change/open does NOT happen by default: it depends on EditorRuntimePolicy.validationTriggers (default MANUAL only); the legacy EMF path has no validation hook at all.",
          example: "A session open on the 'Onboarding Process' model, with the diagram representation active.",
          evidence: [{ path: "src/main/java/com/example/editor/definition/runtime/session/EditorModelSession.java", line: 18, label: "EditorModelSession" }]
        }
      ],
      authoring: {
        summary: "There are two distinct, chained authoring surfaces: (1) the Editor Definition Designer, where HOW the model will look and be edited gets configured (tree + forms, never a freeform canvas); and (2) the model-editing session itself, which IS a real drag/connect canvas — but only once a compiled Editor Definition exists for that metamodel; otherwise a basic fallback canvas is used (palette + React Flow, based on the older 'notation' concept, unrelated to NodeMapping/EdgeMapping).",
        elements: [
          { element: "Representations, node/edge mappings and palette (EditorDefinitionModel)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Tree + form ('Definition' view)", detail: "Dominant pattern: hierarchical tree on the left (metamodel→classes/relations, representations→mappings, palette→tools, forms→sections); selecting a tree node opens its property form on the right. There is no meta-canvas where the representation itself is drawn." },
            { mode: "Guided wizard", detail: "6 steps: identity → representations → tools/forms → capabilities → review → runtime, with per-step completeness checks." },
            { mode: "'Element representations' view", detail: "A third navigation style: a list of metamodel classes/relations, each with '+ Show in this view' / 'Edit appearance' buttons — form-based editing, same as the tree." },
            { mode: "Visual import", detail: "A dedicated dialog recovers existing visual definitions from Sirius Desktop (.odesign), Sirius Web, GMF or Draw.io, with recovery-quality metrics (elements inspected, unresolved references, safe fixes vs. needs-review) before confirming." }
          ]},
          { element: "A node/edge's visual style (shape, color, icons) and conditional styles", requiredness: "OPTIONAL", modes: [
            { mode: "Form with a static SVG preview", detail: "Style fields (shape, fill/border color, width, label position…) with an SVG preview that updates live but is not itself interactive (no dragging or drawing on it)." },
            { mode: "Conditional rules", detail: "A rule list with name, priority and a condition built with the same ExpressionBuilder (clause-guided or advanced tree) used by Validation and Transformation; duplicate-condition detection built in." }
          ]},
          { element: "Palette tools (create node, create edge, invoke operation, run action, validate)", requiredness: "CONDITIONAL", modes: [
            { mode: "Per-tool-kind form", detail: "Each tool kind has its own form (target class for create-node, relation+mapping for create-edge, operation to invoke, action to run, validation scope)." },
            { mode: "Auto-generated", detail: "Create-node/create-edge tools are NOT created standalone from the palette form: they're generated automatically when a class or relation is mapped, so the palette can never drift from the metamodel." }
          ]},
          { element: "A model instance's diagram nodes and edges", requiredness: "ALTERNATIVE", modes: [
            { mode: "Editor-Definition-driven canvas (EditorRuntimeCanvas)", detail: "IF a compiled Editor Definition exists: a real drag/connect canvas — create a node with an executable palette tool, create an edge by dragging between nodes or with a CREATE_EDGE tool, move/resize with layout persisted separately from the semantic model. This canvas takes over the entire workbench." },
            { mode: "Basic fallback canvas (palette + React Flow)", detail: "ONLY when no compiled Editor Definition is available for that metamodel: an older 'notation'-based palette with drag-and-drop onto the canvas and React Flow's native connect." },
            { mode: "EMF import by ID", detail: "Open an already-saved model as an EMF-backed session by entering its ID, instead of starting blank." },
            { mode: "Generated form", detail: "Selecting an element opens a generated form specific to its class, to edit its attribute values." }
          ]}
        ]
      },
      level3: "An EditorDefinitionModel groups N RepresentationDefinition entries, each with its own set of NodeMapping/EdgeMapping referencing classes and relations from the associated MetaModel. The NotationDefinition registry exists in the code but is disconnected from the editor's real compilation path (confirmed by grep: only NotationController/NotationRegistryService use it). An EditorModelSession is the in-memory instance tying model + editor + latest validation report together.",
      level4: "The flow is: (1) the EditorDefinitionModel is defined over an existing metamodel; (2) EditorDefinitionCompiler compiles it; (3) opening a model creates an EditorModelSession; (4) validation fires according to the configured policy (MANUAL by default) — it is not automatic unless the editor's author explicitly opts into ON_CHANGE/ON_OPEN. The legacy EMF path (ModelMutationService) has no validation or event hook at all.",
      level5: {
        incoming: ["Metamodel (required FK that all visual mapping is defined over)", "Grammar (the inferred class-diagram editor reuses the same MetamodelReadOnlyCanvas component)"],
        outgoing: ["Validation (triggers diagnostics over the session's CanonicalModelDocument)", "Events (only if runtimePolicy().publishEvents() is enabled for that session)", "Transformation/Generation (consume the edited model as input)"]
      },
      level6: {
        runtime: "EditorDefinitionCompiler compiles the definition; EditorModelSession lives in memory per open editing session.",
        services: ["EditorDefinitionCompiler", "EditorModelSession", "EditorRuntimePolicy"],
        apis: ["REST for opening/saving an editor session; frontend ProblemsPanel/QuickFixDesigner consume this layer"],
        sources: [
          { path: "src/main/java/com/example/editor/definition/EditorDefinitionModel.java", line: 11, label: "EditorDefinitionModel" },
          { path: "src/main/java/com/example/editor/definition/runtime/session/EditorModelSession.java", line: 18, label: "EditorModelSession" }
        ]
      }
    }
  },

  validation: {
    archComponentId: "b_validation",
    kind: "security",
    es: {
      level1: {
        what: "Define reglas de calidad sobre instancias de un metamodelo y, cuando existe una reparación conocida, las enlaza con un quick fix.",
        value: "Detecta violaciones del modelo en ejecución y ofrece una vía de reparación automática coherente con la regla que falló.",
        inputs: ["Metamodelo sobre el que se define la regla", "Modelo instancia (sesión de editor) sobre el que se evalúa"],
        outputs: ["ModelValidationDiagnostic (violación, error de ejecución de regla o regla rota)", "Modelo reparado + revalidación (si se aplica un quick fix)"]
      },
      level2: [
        {
          name: "ValidationRule",
          purpose: "Regla de calidad declarativa evaluada sobre un contexto del metamodelo.",
          whenUsed: "Para expresar una restricción que el modelo debe cumplir (de clase, atributo, relación, objeto, modelo completo, representación, selección o proyecto).",
          properties: ["id, name, code, description, enabled", "context: ValidationContext (kind ∈ CLASS/ATTRIBUTE/RELATION/OBJECT/MODEL/REPRESENTATION/SELECTION/PROJECT, classId, featureId)", "severity ∈ ERROR/WARNING/INFO", "conditionExpressionId (puntero al kernel semantic.expression)", "messageTemplate con ${placeholders}", "triggers, dependencies", "quickFixIds: List<String> (0..N ids de reparación, DECLARADOS, no calculados)", "executionMode, provenance"],
          relationships: ["context apunta a un MetaClass/MetaAttribute del metamodelo", "quickFixIds enlaza, por declaración explícita, con 0..N ActionDefinition"],
          cardinality: "1 MetaModel ↔ 0..N ValidationRule; 1 ValidationRule ↔ 0..N quickFixIds",
          io: { inputs: "Expresión de condición + metamodelo", outputs: "ModelValidationDiagnostic cuando la condición se incumple" },
          lifecycle: "Se evalúa mediante ExpressionEvaluator (semantic.expression) sobre cada instancia pertinente del modelo.",
          example: "Regla 'Tarea.duracion > 0', severidad ERROR, con quickFixIds=[\"fijar-duracion-minima\"].",
          evidence: [{ path: "src/main/java/com/example/editor/validation/validation/ValidationRule.java", line: 12, label: "ValidationRule" }]
        },
        {
          name: "ModelValidationDiagnostic",
          purpose: "Resultado de evaluar una ValidationRule contra el modelo: la violación concreta encontrada.",
          whenUsed: "Se genera cada vez que se ejecuta la validación (manual o, si la política lo permite, automática) sobre una sesión de modelo.",
          properties: ["id, kind ∈ VIOLATION/RULE_EXECUTION_ERROR/BROKEN_RULE", "ruleId, severity, message", "location (modelElementId, featureId, relationInstanceId)", "quickFixIds (copiados literalmente de la regla)", "details"],
          relationships: ["NO tiene referencia inversa a un QuickFixDescriptor: el enlace diagnóstico→reparación es solo esta lista de ids, resuelta contra un ActionModel/ActionDefinition con alcance de metamodelo"],
          cardinality: "1 evaluación de ValidationRule ↔ 0..N ModelValidationDiagnostic",
          io: { inputs: "Modelo instancia + reglas activas", outputs: "Lista de diagnósticos mostrada en el panel de problemas" },
          lifecycle: "Vive mientras la sesión no se revalida; se reemplaza por una nueva lista tras cada validación.",
          example: "Diagnóstico ERROR sobre la Tarea 'Revisión' por duración negativa, con quickFixIds=[\"fijar-duracion-minima\"].",
          evidence: [{ path: "src/main/java/com/example/editor/validation/runtime/ModelValidationDiagnostic.java", line: 10, label: "ModelValidationDiagnostic" }]
        },
        {
          name: "QuickFixDescriptor / ActionDefinition",
          purpose: "Reparación automática o asistida para una o varias reglas de validación.",
          whenUsed: "Cuando existe una forma conocida y segura de corregir la causa de un diagnóstico.",
          properties: ["Definido con alcance de metamodelo, como ActionDefinition dentro de un ActionModel", "preview(): genuinamente sin efectos secundarios (devuelve una copia)", "apply(): SÍ muta el modelo real; toma una instantánea para poder deshacer"],
          relationships: ["El enlace con el diagnóstico es unidireccional: el diagnóstico declara quickFixIds; el quick fix no sabe qué diagnóstico lo invocó"],
          cardinality: "1 ValidationRule ↔ 0..N QuickFix (vía quickFixIds); 1 QuickFix puede servir a varias reglas",
          io: { inputs: "Diagnóstico + modelo actual", outputs: "Modelo modificado (apply) o propuesta de cambio (preview)" },
          lifecycle: "apply() invoca automáticamente orchestrator.validateLive(...) en la MISMA respuesta: el frontend nunca necesita volver a pedir /validate tras aplicar un quick fix.",
          example: "Quick fix 'fijar-duracion-minima' que pone duracion=1 y revalida la Tarea en la misma llamada.",
          evidence: [{ path: "src/main/java/com/example/editor/validation", line: 1, label: "Paquete validation — ActionDefinition / QuickFix runtime" }]
        }
      ],
      authoring: {
        summary: "Validación y Quick Fixes comparten el mismo patrón: un interruptor GUIADO/AVANZADO en cada formulario, y — lo más importante — un constructor de expresiones recursivo reutilizado literalmente en 3 sitios distintos de HYDRA (condición de una regla, precondición de un quick fix, guard de una regla de transformación): en modo guiado se compone por cláusulas en lenguaje casi natural (atributo/relación/elementos del modelo + operador + valor, con combinador Y/O); en modo avanzado es un árbol de nodos de expresión totalmente recursivo (12 tipos: literal, self, variable, acceso a atributo, navegación de relación, llamada a operación, operadores unarios/binarios, colección, condicional, función). No existe ninguna caja de texto libre para OCL/EVL/Java en ningún punto.",
        elements: [
          { element: "ValidationRule", requiredness: "ALTERNATIVE", modes: [
            { mode: "Asistente guiado (6 pasos)", detail: "identidad → contexto → diagnóstico (mensaje/triggers) → condición → quick fix → revisión, cada paso bloqueado hasta completarse." },
            { mode: "Formulario completo", detail: "Todos los grupos de campos a la vez, sin pasos: identidad, contexto, diagnóstico, condición, quick fix, avanzado (incluye banderas de reglas importadas: OCL/EVL/ATL/Sirius AQL/Java/Xtext con su estado de conversión)." }
          ]},
          { element: "Condición de la regla (conditionExpressionId)", requiredness: "MANDATORY", modes: [
            { mode: "Constructor guiado por cláusulas", detail: "Una fila por cláusula: qué comprobar (valor de atributo / cardinalidad de relación / elementos del modelo) + operador (filtrado por tipo) + valor esperado, con resumen en lenguaje llano en vivo (p. ej. 'Person.age es mayor o igual que 0')." },
            { mode: "Árbol de expresión avanzado", detail: "Editor recursivo nodo a nodo con 12 tipos de elemento de expresión; si el árbol existente no encaja en el patrón guiado, se ofrece sustituirlo por una plantilla guiada." }
          ]},
          { element: "Asociación con un Quick Fix (quickFixIds)", requiredness: "OPTIONAL", modes: [
            { mode: "Cuadrícula de casillas en el lado de la regla", detail: "Se marca/desmarca cada ActionDefinition disponible; el Quick Fix en sí no tiene ningún campo para elegir qué regla(s) lo usan — el enlace es unidireccional desde la regla." }
          ]},
          { element: "QuickFixDescriptor / ActionDefinition", requiredness: "ALTERNATIVE", modes: [
            { mode: "Asistente guiado (5 pasos)", detail: "identidad → seguridad (precondición, política transaccional, política de deshacer) → parámetros → operaciones → revisión." },
            { mode: "Formulario completo", detail: "Mismo contenido sin pasos; modo por defecto para quick fixes (a diferencia de las reglas, que arrancan en modo guiado)." }
          ]},
          { element: "Operaciones de un Quick Fix", requiredness: "REPEATABLE", modes: [
            { mode: "Lista ordenable de tarjetas", detail: "Se añaden una a una eligiendo un tipo (de 10: fijar/limpiar/copiar atributo, añadir/quitar relación, crear/borrar elemento, transformar texto, ejecutar acción, invocar operación), cada una con su propio formulario y botones de reordenar/eliminar." }
          ]}
        ]
      },
      level3: "Una ValidationRule se define sobre un contexto del metamodelo y declara explícitamente qué quick fixes la pueden reparar (quickFixIds). Un diagnóstico (ModelValidationDiagnostic) es la instancia concreta de esa regla fallando sobre un elemento del modelo, y hereda la misma lista de quickFixIds de su regla — el quick fix en sí no conoce al diagnóstico que lo disparó; la relación es unidireccional y vive en el lado de la regla/diagnóstico, no en el lado de la acción. Transformación tiene su PROPIO mecanismo de quick fix (TransformationQuickFixService), completamente separado y sin método apply/mutate — solo suggest().",
      level4: "El flujo típico: se evalúa ValidationRule.conditionExpressionId vía ExpressionEvaluator → si falla, se emite un ModelValidationDiagnostic con los quickFixIds heredados → el usuario puede pedir preview() (sin efectos) de un quick fix → o apply(), que SÍ muta el modelo, toma una instantánea de reversión y dispara una revalidación automática en la misma respuesta. Tanto ValidationRuntimeController (alcance metamodelo) como ModelValidationRuntimeController (alcance modelo/sesión, con endpoints de quick fix) validan siempre una INSTANCIA de modelo — no existe un endpoint de 'autovalidación' de la estructura del propio metamodelo.",
      level5: {
        incoming: ["Metamodel (contexto de la regla)", "Model Editor (la sesión de edición es lo que se valida)"],
        outgoing: ["Gamification (eventos core.validation.* y core.quickfix.* pueden disparar reglas de puntos/logros)", "Frontend ProblemsPanel (invocación runtime) vs QuickFixDesigner (autoría de ActionDefinition) — dos componentes distintos"]
      },
      level6: {
        runtime: "ValidationEngine.evaluate() invoca directamente ExpressionEvaluator del kernel semantic.expression compartido.",
        services: ["ValidationEngine", "ValidationRuntimeController", "ModelValidationRuntimeController"],
        apis: ["REST de validación por metamodelo y por modelo/sesión, con endpoints de preview/apply de quick fix"],
        sources: [
          { path: "src/main/java/com/example/editor/validation/validation/ValidationRule.java", line: 12, label: "ValidationRule" },
          { path: "src/main/java/com/example/editor/validation/runtime/ModelValidationDiagnostic.java", line: 10, label: "ModelValidationDiagnostic" },
          { path: "src/main/java/com/example/editor/validation/engine/ValidationEngine.java", line: 144, label: "evaluate() → ExpressionEvaluator" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines quality rules over instances of a metamodel and, where a known repair exists, links them to a quick fix.",
        value: "Detects live model violations and offers an automatic repair path consistent with the rule that failed.",
        inputs: ["The metamodel the rule is defined over", "The model instance (editor session) being evaluated"],
        outputs: ["ModelValidationDiagnostic (violation, rule execution error, or broken rule)", "Repaired model + revalidation (if a quick fix is applied)"]
      },
      level2: [
        {
          name: "ValidationRule",
          purpose: "Declarative quality rule evaluated over a metamodel context.",
          whenUsed: "To express a constraint the model must satisfy (class, attribute, relation, object, whole model, representation, selection or project level).",
          properties: ["id, name, code, description, enabled", "context: ValidationContext (kind ∈ CLASS/ATTRIBUTE/RELATION/OBJECT/MODEL/REPRESENTATION/SELECTION/PROJECT, classId, featureId)", "severity ∈ ERROR/WARNING/INFO", "conditionExpressionId (pointer into the shared semantic.expression kernel)", "messageTemplate with ${placeholders}", "triggers, dependencies", "quickFixIds: List<String> (0..N repair ids, DECLARED, not computed)", "executionMode, provenance"],
          relationships: ["context points to a MetaClass/MetaAttribute of the metamodel", "quickFixIds explicitly links to 0..N ActionDefinition"],
          cardinality: "1 MetaModel ↔ 0..N ValidationRule; 1 ValidationRule ↔ 0..N quickFixIds",
          io: { inputs: "Condition expression + metamodel", outputs: "ModelValidationDiagnostic when the condition is violated" },
          lifecycle: "Evaluated via ExpressionEvaluator (semantic.expression) over each relevant model instance.",
          example: "Rule 'Task.duration > 0', severity ERROR, with quickFixIds=[\"set-minimum-duration\"].",
          evidence: [{ path: "src/main/java/com/example/editor/validation/validation/ValidationRule.java", line: 12, label: "ValidationRule" }]
        },
        {
          name: "ModelValidationDiagnostic",
          purpose: "Result of evaluating a ValidationRule against the model: the concrete violation found.",
          whenUsed: "Generated every time validation runs (manual, or automatic if policy allows it) over a model session.",
          properties: ["id, kind ∈ VIOLATION/RULE_EXECUTION_ERROR/BROKEN_RULE", "ruleId, severity, message", "location (modelElementId, featureId, relationInstanceId)", "quickFixIds (copied verbatim from the rule)", "details"],
          relationships: ["Has NO back-reference to a QuickFixDescriptor: the diagnostic→repair link is only this id list, resolved against a metamodel-scoped ActionModel/ActionDefinition"],
          cardinality: "1 ValidationRule evaluation ↔ 0..N ModelValidationDiagnostic",
          io: { inputs: "Model instance + active rules", outputs: "List of diagnostics shown in the problems panel" },
          lifecycle: "Lives until the session is revalidated; replaced by a new list after every validation run.",
          example: "ERROR diagnostic on the 'Review' Task for negative duration, with quickFixIds=[\"set-minimum-duration\"].",
          evidence: [{ path: "src/main/java/com/example/editor/validation/runtime/ModelValidationDiagnostic.java", line: 10, label: "ModelValidationDiagnostic" }]
        },
        {
          name: "QuickFixDescriptor / ActionDefinition",
          purpose: "Automatic or assisted repair for one or several validation rules.",
          whenUsed: "When a known, safe way exists to fix the cause of a diagnostic.",
          properties: ["Defined at metamodel scope, as an ActionDefinition inside an ActionModel", "preview(): genuinely side-effect-free (returns a copy)", "apply(): DOES mutate the real model; snapshots first so it can be rolled back"],
          relationships: ["The link to the diagnostic is one-directional: the diagnostic declares quickFixIds; the quick fix itself does not know which diagnostic invoked it"],
          cardinality: "1 ValidationRule ↔ 0..N QuickFix (via quickFixIds); 1 QuickFix can serve several rules",
          io: { inputs: "Diagnostic + current model", outputs: "Modified model (apply) or a change proposal (preview)" },
          lifecycle: "apply() automatically calls orchestrator.validateLive(...) in the SAME response: the frontend never needs to re-request /validate after applying a quick fix.",
          example: "Quick fix 'set-minimum-duration' that sets duration=1 and revalidates the Task in the same call.",
          evidence: [{ path: "src/main/java/com/example/editor/validation", line: 1, label: "validation package — ActionDefinition / QuickFix runtime" }]
        }
      ],
      authoring: {
        summary: "Validation and Quick Fixes share the same pattern: a GUIDED/ADVANCED switch on every form, and — most importantly — a recursive expression builder reused literally in 3 different places in HYDRA (a rule's condition, a quick fix's precondition, a transformation rule's guard): in guided mode it's composed of near-natural-language clauses (attribute/relation/model-elements + operator + value, with an AND/OR combinator); in advanced mode it's a fully recursive expression-node tree (12 kinds: literal, self, variable, attribute access, relation navigation, operation call, unary/binary operators, collection, conditional, function). There is no free-text OCL/EVL/Java box anywhere.",
        elements: [
          { element: "ValidationRule", requiredness: "ALTERNATIVE", modes: [
            { mode: "Guided wizard (6 steps)", detail: "identity → context → diagnostic (message/triggers) → condition → quick fix → review, each step gated until complete." },
            { mode: "Full form", detail: "All field groups at once, no steps: identity, context, diagnostic, condition, quick fix, advanced (includes imported-rule flags: OCL/EVL/ATL/Sirius AQL/Java/Xtext with conversion status)." }
          ]},
          { element: "Rule condition (conditionExpressionId)", requiredness: "MANDATORY", modes: [
            { mode: "Clause-guided builder", detail: "One row per clause: what to check (attribute value / relation cardinality / model elements) + operator (type-filtered) + expected value, with a live plain-language summary (e.g. 'Person.age is greater than or equal to 0')." },
            { mode: "Advanced expression tree", detail: "Fully recursive node-by-node editor with 12 expression-element kinds; if an existing tree doesn't fit the guided pattern, it offers replacing it with a guided template." }
          ]},
          { element: "Association with a Quick Fix (quickFixIds)", requiredness: "OPTIONAL", modes: [
            { mode: "Checkbox chip-grid on the rule's side", detail: "Every available ActionDefinition is toggled on/off; the quick fix itself has no field to pick which rule(s) use it — the link is one-directional from the rule." }
          ]},
          { element: "QuickFixDescriptor / ActionDefinition", requiredness: "ALTERNATIVE", modes: [
            { mode: "Guided wizard (5 steps)", detail: "identity → safety (precondition, transaction policy, undo policy) → parameters → operations → review." },
            { mode: "Full form", detail: "Same content without steps; the default mode for quick fixes (unlike rules, which default to guided)." }
          ]},
          { element: "A Quick Fix's operations", requiredness: "REPEATABLE", modes: [
            { mode: "Reorderable card list", detail: "Added one at a time by picking a type (of 10: set/clear/copy attribute, add/remove relation, create/delete element, transform text, execute action, invoke operation), each with its own fields and reorder/remove buttons." }
          ]}
        ]
      },
      level3: "A ValidationRule is defined over a metamodel context and explicitly declares which quick fixes can repair it (quickFixIds). A diagnostic (ModelValidationDiagnostic) is the concrete instance of that rule failing on a model element, and inherits the same quickFixIds list from its rule — the quick fix itself does not know which diagnostic triggered it; the relationship is one-directional and lives on the rule/diagnostic side, not the action side. Transformation has its OWN, completely separate quick-fix mechanism (TransformationQuickFixService), with no apply/mutate method at all — only suggest().",
      level4: "Typical flow: ValidationRule.conditionExpressionId is evaluated via ExpressionEvaluator → on failure, a ModelValidationDiagnostic is emitted carrying the inherited quickFixIds → the user can request preview() (no side effects) of a quick fix → or apply(), which DOES mutate the model, snapshots for rollback, and triggers an automatic revalidation in the same response. Both ValidationRuntimeController (metamodel-scoped) and ModelValidationRuntimeController (model/session-scoped, with quick-fix endpoints) always validate a model INSTANCE — there is no 'self-validation' endpoint for the metamodel's own structure.",
      level5: {
        incoming: ["Metamodel (the rule's context)", "Model Editor (the editing session is what gets validated)"],
        outgoing: ["Gamification (core.validation.* and core.quickfix.* events can trigger point/achievement rules)", "Frontend ProblemsPanel (runtime invocation) vs QuickFixDesigner (ActionDefinition authoring) — two distinct components"]
      },
      level6: {
        runtime: "ValidationEngine.evaluate() calls directly into the shared semantic.expression kernel's ExpressionEvaluator.",
        services: ["ValidationEngine", "ValidationRuntimeController", "ModelValidationRuntimeController"],
        apis: ["REST validation endpoints scoped by metamodel and by model/session, with quick-fix preview/apply endpoints"],
        sources: [
          { path: "src/main/java/com/example/editor/validation/validation/ValidationRule.java", line: 12, label: "ValidationRule" },
          { path: "src/main/java/com/example/editor/validation/runtime/ModelValidationDiagnostic.java", line: 10, label: "ModelValidationDiagnostic" },
          { path: "src/main/java/com/example/editor/validation/engine/ValidationEngine.java", line: 144, label: "evaluate() → ExpressionEvaluator" }
        ]
      }
    }
  },

  grammar: {
    archComponentId: "b_grammar",
    kind: "backend",
    es: {
      level1: {
        what: "Define un lenguaje textual (gramática) y permite inferir desde ella un metamodelo real, un editor textual y acciones de código.",
        value: "Permite diseñar un DSL escribiendo primero su sintaxis concreta, con el metamodelo derivado automáticamente en vez de modelado a mano.",
        inputs: ["Documentos de gramática (uno o varios ficheros, tecnología Langium por defecto)", "Texto escrito por el usuario en el editor textual"],
        outputs: ["MetaModel inferido (proyección real, no ilustrativa)", "CanonicalModelDocument (vía parseo texto→modelo)", "Diagnósticos y code actions propios de la gramática"]
      },
      level2: [
        {
          name: "GrammarDefinitionModel",
          purpose: "Definición de una gramática: tecnología, modo de autoría y documentos fuente.",
          whenUsed: "Cuando se quiere definir un lenguaje por su sintaxis textual en vez de por formulario de clases.",
          properties: ["technology (por defecto LANGIUM)", "authoringMode (por defecto GRAMMAR_FIRST)", "documents[]: GrammarSourceDocument (multi-fichero)"],
          relationships: ["Se compila produciendo, entre otros artefactos, un MetaModel inferido"],
          cardinality: "1 GrammarDefinitionModel ↔ 1..N GrammarSourceDocument",
          io: { inputs: "Reglas de gramática en texto", outputs: "Pipeline de compilación con 12 etapas" },
          lifecycle: "La compilación completa debe alcanzar estado COMPLETE o falla entera.",
          example: "Gramática de un lenguaje de reglas de negocio con reglas Rule, Condition, Action.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "Paquete grammar — GrammarDefinitionModel" }]
        },
        {
          name: "GrammarCompilationService (pipeline de 12 etapas)",
          purpose: "Compila los documentos de gramática y produce, entre otros artefactos, una proyección de metamodelo inferido.",
          whenUsed: "Cada vez que se guarda/recompila la gramática.",
          properties: ["12 etapas; incluye una etapa bloqueante GENERATE_SEMANTIC_ARTIFACTS", "GrammarClassDiagramProjectionService.project(...) produce el MetaModel inferido"],
          relationships: ["Si GENERATE_SEMANTIC_ARTIFACTS no llega a COMPLETE, TODA la compilación falla — el metamodelo inferido es obligatorio, no decorativo"],
          cardinality: "1 compilación ↔ 1 MetaModel inferido (cuando tiene éxito)",
          io: { inputs: "Documentos de gramática", outputs: "Metamodelo inferido + artefactos semánticos" },
          lifecycle: "El frontend GrammarClassDiagram.tsx renderiza projection.inferredMetamodel con el MISMO componente MetamodelReadOnlyCanvas que usa un metamodelo manual real.",
          example: "Compilar la gramática produce una clase Rule con atributos condition y action inferidos de la sintaxis.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarCompilationService — pipeline de 12 etapas" }]
        },
        {
          name: "Texto → Modelo (T2M) y encadenamiento opcional a una transformación M2M",
          purpose: "Parsear texto conforme a la gramática activa y producir un CanonicalModelDocument; opcionalmente encadenar una transformación modelo-a-modelo persistida.",
          whenUsed: "Al escribir/guardar en el editor textual generado desde la gramática.",
          properties: ["GrammarTransformationTextParserGateway resuelve el snapshot de gramática activo/último válido", "TransformationTextToModelConfiguration.postTransformationId (encadenamiento opcional, verificado en código)"],
          relationships: ["parse() = re-parseo de borrador bajo demanda; save() = promoción explícita a canónico, solo tras revalidar vía sharedLayers.validateBeforeCommit"],
          cardinality: "1 texto ↔ 1 CanonicalModelDocument; 0..1 transformación M2M encadenada",
          io: { inputs: "Texto del usuario", outputs: "Modelo canónico (y, si aplica, modelo transformado)" },
          lifecycle: "Draft (parse) vs commit (save) son dos operaciones explícitamente distintas.",
          example: "Escribir 'rule R1 when x>0 then approve' produce una instancia Rule, que opcionalmente se transforma a un modelo de aprobación.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarTransformationTextParserGateway" }]
        },
        {
          name: "GrammarCodeAction",
          purpose: "Mecanismo de quick-fix/code-action propio de la gramática, independiente del de Validación de modelos.",
          whenUsed: "Cuando el texto tiene un error de sintaxis o de regla gramatical reparable.",
          properties: ["Incluye ediciones de texto y un booleano safeAutomatic que SÍ puede ser true"],
          relationships: ["Mecanismo paralelo y sin referencias cruzadas con ValidationRule/QuickFixDescriptor de Validación; tampoco con TransformationQuickFixService"],
          cardinality: "1 diagnóstico de gramática ↔ 0..N GrammarCodeAction",
          io: { inputs: "Diagnóstico de parseo/gramática", outputs: "Edición de texto aplicada" },
          lifecycle: "safeAutomatic=true permite aplicación sin confirmación explícita (a diferencia de Transformation, cuyo quick-fix es siempre manual).",
          example: "Code action que autocompleta un paréntesis o palabra clave faltante.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarCodeAction" }]
        }
      ],
      authoring: {
        summary: "Gramática es puramente textual: se autora escribiendo en un editor Monaco real (no una caja de texto simplificada), con diagnósticos locales instantáneos (sin ida y vuelta al backend) fusionados con diagnósticos confirmados por el backend, quick-fixes nativos de Monaco, y resaltado/hover/autocompletado de símbolos de la propia gramática. El diagrama de clases y la vista de metamodelo inferido son proyecciones de SOLO LECTURA, nunca una superficie de autoría — reutilizan literalmente el mismo lienzo de solo lectura del Diseñador de Metamodelos; no existe ningún camino que escriba de vuelta del diagrama al texto.",
        elements: [
          { element: "Reglas de la gramática (texto Langium/Xtext/ANTLR)", requiredness: "MANDATORY", modes: [
            { mode: "Editor Monaco", detail: "Edición de texto plano en la sintaxis real de la tecnología elegida; marcadores (squiggles) y glifos de margen para diagnósticos, proveedores de definición/referencia ('ir a definición'), quick-fixes nativos de Monaco." },
            { mode: "Inspección local instantánea", detail: "Un escáner determinista basado en regex detecta en cada pulsación: delimitadores desbalanceados, palabra clave 'entry' malformada, regla sin terminador ';', referencias no definidas — sin esperar al backend." }
          ]},
          { element: "Diagrama de clases / metamodelo inferido", requiredness: "MANDATORY", modes: [
            { mode: "Proyección automática de solo lectura", detail: "Se recalcula automáticamente al editar el texto (sin necesidad de compilar); reutiliza el mismo renderizador que el Diseñador de Metamodelos. Clic en un nodo salta al texto de la gramática que lo originó — la relación es inversa a 'dibujar genera gramática'." }
          ]},
          { element: "Tecnología de la gramática (Langium/Xtext/ANTLR)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Selector en el formulario de identidad", detail: "Cambiar la tecnología con contenido ya escrito abre un diálogo de 'conversión de plataforma': vista previa de equivalencia semántica, mapeo de regla de entrada, elementos preservados/advertencias, y un botón 'Aplicar' (deshabilitado si no es semánticamente equivalente) o 'Mantener la gramática actual'. No es un asistente de varios pasos ni un selector de archivo." }
          ]},
          { element: "Personalización textual (apariencia, hover, ejemplo)", requiredness: "OPTIONAL", modes: [
            { mode: "Formulario de estilo", detail: "Fuente/tamaño/colores, palabras reservadas auto-inferidas de literales entre comillas, colores/negrita/cursiva por rol o por símbolo concreto, y texto de hover por símbolo (lo que luego muestra el propio proveedor de hover de Monaco)." }
          ]}
        ]
      },
      level3: "Una gramática (GrammarDefinitionModel) se compila en un pipeline de 12 etapas que, de forma obligatoria, proyecta un MetaModel inferido real — el mismo tipo de objeto que produciría la definición manual de un metamodelo, consumido por el mismo lienzo de solo lectura en el frontend. El texto conforme a esa gramática se parsea a un CanonicalModelDocument y, opcionalmente, encadena una transformación M2M persistida. La gramática tiene su propio sistema de diagnósticos/code-actions, sin relación con el de Validación de modelos.",
      level4: "Guardar/compilar la gramática ejecuta las 12 etapas; si GENERATE_SEMANTIC_ARTIFACTS no llega a COMPLETE, la compilación entera falla (no hay metamodelo inferido parcial). Al escribir texto: parse() es un re-análisis de borrador bajo demanda (no persiste); save() promueve el resultado a canónico, pero solo después de pasar sharedLayers.validateBeforeCommit. Si existe postTransformationId configurado, el modelo resultante se transforma automáticamente a otro modelo de destino.",
      level5: {
        incoming: ["Ninguna dependencia de entrada obligatoria — la gramática es un punto de origen alternativo al metamodelo manual"],
        outgoing: ["Metamodel (produce un MetaModel inferido real)", "Model Editor (el diagrama de clases inferido reutiliza MetamodelReadOnlyCanvas)", "Transformation (encadenamiento opcional T2M→M2M vía postTransformationId)"]
      },
      level6: {
        runtime: "GrammarCompilationService ejecuta el pipeline; GrammarClassDiagramProjectionService proyecta el metamodelo inferido.",
        services: ["GrammarCompilationService", "GrammarClassDiagramProjectionService", "GrammarTransformationTextParserGateway"],
        apis: ["REST de compilación de gramática + parseo/guardado de texto"],
        sources: [
          { path: "src/main/java/com/example/editor/grammar", line: 1, label: "Paquete grammar" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines a textual language (grammar) and lets a real metamodel, a textual editor and code actions be inferred from it.",
        value: "Lets a DSL be designed by writing its concrete syntax first, with the metamodel derived automatically instead of modeled by hand.",
        inputs: ["Grammar documents (one or several files, Langium technology by default)", "Text written by the user in the textual editor"],
        outputs: ["Inferred MetaModel (a real projection, not illustrative)", "CanonicalModelDocument (via text→model parsing)", "Grammar-specific diagnostics and code actions"]
      },
      level2: [
        {
          name: "GrammarDefinitionModel",
          purpose: "Definition of a grammar: technology, authoring mode and source documents.",
          whenUsed: "When a language should be defined by its textual syntax instead of a class form.",
          properties: ["technology (default LANGIUM)", "authoringMode (default GRAMMAR_FIRST)", "documents[]: GrammarSourceDocument (multi-file)"],
          relationships: ["Compiles into, among other artifacts, an inferred MetaModel"],
          cardinality: "1 GrammarDefinitionModel ↔ 1..N GrammarSourceDocument",
          io: { inputs: "Grammar rules as text", outputs: "A 12-stage compilation pipeline" },
          lifecycle: "The whole compilation must reach COMPLETE status or the whole thing fails.",
          example: "A business-rule language grammar with Rule, Condition, Action productions.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "grammar package — GrammarDefinitionModel" }]
        },
        {
          name: "GrammarCompilationService (12-stage pipeline)",
          purpose: "Compiles the grammar documents and produces, among other artifacts, an inferred metamodel projection.",
          whenUsed: "Every time the grammar is saved/recompiled.",
          properties: ["12 stages; includes a blocking GENERATE_SEMANTIC_ARTIFACTS stage", "GrammarClassDiagramProjectionService.project(...) produces the inferred MetaModel"],
          relationships: ["If GENERATE_SEMANTIC_ARTIFACTS does not reach COMPLETE, the WHOLE compilation fails — the inferred metamodel is mandatory, not decorative"],
          cardinality: "1 compilation ↔ 1 inferred MetaModel (on success)",
          io: { inputs: "Grammar documents", outputs: "Inferred metamodel + semantic artifacts" },
          lifecycle: "The frontend GrammarClassDiagram.tsx renders projection.inferredMetamodel with the SAME MetamodelReadOnlyCanvas component used for a real manual metamodel.",
          example: "Compiling the grammar produces a Rule class with condition and action attributes inferred from the syntax.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarCompilationService — 12-stage pipeline" }]
        },
        {
          name: "Text → Model (T2M) and optional chaining into an M2M transformation",
          purpose: "Parse text conforming to the active grammar into a CanonicalModelDocument; optionally chain into a persisted model-to-model transformation.",
          whenUsed: "When writing/saving in the textual editor generated from the grammar.",
          properties: ["GrammarTransformationTextParserGateway resolves the active/last-known-good grammar snapshot", "TransformationTextToModelConfiguration.postTransformationId (optional chaining, code-verified)"],
          relationships: ["parse() = draft-only re-parse-on-demand; save() = explicit promotion to canonical, only after revalidating via sharedLayers.validateBeforeCommit"],
          cardinality: "1 text ↔ 1 CanonicalModelDocument; 0..1 chained M2M transformation",
          io: { inputs: "User text", outputs: "Canonical model (and, if configured, a transformed model)" },
          lifecycle: "Draft (parse) vs commit (save) are two explicitly distinct operations.",
          example: "Writing 'rule R1 when x>0 then approve' produces a Rule instance, optionally transformed into an approval model.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarTransformationTextParserGateway" }]
        },
        {
          name: "GrammarCodeAction",
          purpose: "Grammar's own quick-fix/code-action mechanism, independent of model Validation's.",
          whenUsed: "When text has a repairable syntax or grammar-rule error.",
          properties: ["Includes text edits and a safeAutomatic boolean that CAN be true"],
          relationships: ["A parallel mechanism with zero cross-references to Validation's ValidationRule/QuickFixDescriptor, nor to TransformationQuickFixService"],
          cardinality: "1 grammar diagnostic ↔ 0..N GrammarCodeAction",
          io: { inputs: "Parse/grammar diagnostic", outputs: "Applied text edit" },
          lifecycle: "safeAutomatic=true allows applying without explicit confirmation (unlike Transformation, whose quick-fix is always manual).",
          example: "A code action that auto-completes a missing parenthesis or keyword.",
          evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarCodeAction" }]
        }
      ],
      authoring: {
        summary: "Grammar is purely textual: authored by typing in a real Monaco editor (not a simplified text box), with instant local diagnostics (no backend round-trip) merged with backend-confirmed diagnostics, native Monaco quick-fixes, and symbol highlighting/hover/autocomplete over the grammar itself. The class diagram and inferred-metamodel view are READ-ONLY projections, never an authoring surface — they literally reuse the same read-only canvas as the Metamodel Designer; there is no path writing back from the diagram into the text.",
        elements: [
          { element: "Grammar rules (Langium/Xtext/ANTLR text)", requiredness: "MANDATORY", modes: [
            { mode: "Monaco editor", detail: "Plain-text editing in the chosen technology's real syntax; markers/squiggles and gutter glyphs for diagnostics, definition/reference providers ('go to definition'), native Monaco quick-fixes." },
            { mode: "Instant local inspection", detail: "A deterministic regex-based scanner detects on every keystroke: unbalanced delimiters, malformed 'entry' keyword, a rule missing its ';' terminator, undefined references — without waiting on the backend." }
          ]},
          { element: "Class diagram / inferred metamodel", requiredness: "MANDATORY", modes: [
            { mode: "Automatic read-only projection", detail: "Recomputed automatically as the text is edited (no compile needed); reuses the same renderer as the Metamodel Designer. Clicking a node jumps to the grammar text that produced it — the inverse of 'drawing generates grammar'." }
          ]},
          { element: "Grammar technology (Langium/Xtext/ANTLR)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Selector in the identity form", detail: "Changing technology with content already written opens a 'platform conversion' dialog: semantic-equivalence preview, entry-rule mapping, preserved elements/warnings, and an 'Apply' button (disabled unless semantically equivalent) or 'Keep current grammar'. Not a multi-step wizard or a file picker." }
          ]},
          { element: "Textual customization (appearance, hover, example)", requiredness: "OPTIONAL", modes: [
            { mode: "Style form", detail: "Font/size/colors, reserved words auto-inferred from quoted literals, per-role or per-symbol color/bold/italic overrides, and per-symbol hover text (later shown by Monaco's own hover provider)." }
          ]}
        ]
      },
      level3: "A grammar (GrammarDefinitionModel) compiles through a 12-stage pipeline that mandatorily projects a real inferred MetaModel — the same kind of object a manual metamodel definition would produce, consumed by the same read-only canvas in the frontend. Text conforming to that grammar is parsed into a CanonicalModelDocument and optionally chains into a persisted M2M transformation. Grammar has its own diagnostics/code-action system, unrelated to model Validation's.",
      level4: "Saving/compiling the grammar runs the 12 stages; if GENERATE_SEMANTIC_ARTIFACTS does not reach COMPLETE, the whole compilation fails (there is no partial inferred metamodel). On text entry: parse() is an on-demand draft re-analysis (not persisted); save() promotes the result to canonical, but only after passing sharedLayers.validateBeforeCommit. If a postTransformationId is configured, the resulting model is automatically transformed into another target model.",
      level5: {
        incoming: ["No mandatory incoming dependency — grammar is an alternative starting point to a manual metamodel"],
        outgoing: ["Metamodel (produces a real inferred MetaModel)", "Model Editor (the inferred class diagram reuses MetamodelReadOnlyCanvas)", "Transformation (optional T2M→M2M chaining via postTransformationId)"]
      },
      level6: {
        runtime: "GrammarCompilationService runs the pipeline; GrammarClassDiagramProjectionService projects the inferred metamodel.",
        services: ["GrammarCompilationService", "GrammarClassDiagramProjectionService", "GrammarTransformationTextParserGateway"],
        apis: ["REST for grammar compilation + text parse/save"],
        sources: [
          { path: "src/main/java/com/example/editor/grammar", line: 1, label: "grammar package" }
        ]
      }
    }
  },

  transformation: {
    archComponentId: "b_transformation",
    kind: "backend",
    es: {
      level1: {
        what: "Define reglas declarativas para transformar modelos (y, de forma incremental y bidireccional, sincronizarlos) entre endpoints de origen y destino.",
        value: "Permite mantener dos representaciones de un mismo conocimiento (p. ej. dos metamodelos distintos) en sincronía, con trazabilidad y resolución de conflictos.",
        inputs: ["Modelo(s) origen conforme(s) a su(s) metamodelo(s)", "Definición de reglas de transformación"],
        outputs: ["Modelo(s) destino", "Traza de transformación (TransformationTrace)", "Sesión de sincronización incremental (si se usa el modo bidireccional)"]
      },
      level2: [
        {
          name: "TransformationDefinitionDocument",
          purpose: "Documento raíz de una transformación: tipo, direccionalidad, endpoints, reglas y políticas de ejecución/traza.",
          whenUsed: "Para declarar cómo se deriva o sincroniza un modelo destino a partir de uno o varios modelos origen.",
          properties: ["schemaVersion (\"1.1\"), id, name, kind: TransformationKind", "directionality: TransformationDirectionality (UNIDIRECTIONAL/BIDIRECTIONAL)", "synchronizationPolicy", "sources[]/targets[]: TransformationEndpoint", "rules[]: TransformationRule", "generationRules[], textToModel, executionPolicy, tracePolicy"],
          relationships: ["sources/targets referencian metamodelos; rules referencia clases de esos metamodelos"],
          cardinality: "1 TransformationDefinitionDocument ↔ 1..N endpoints ↔ 1..N TransformationRule",
          io: { inputs: "Modelo(s) origen", outputs: "Modelo(s) destino" },
          lifecycle: "Ejecución en dos fases: allocate() (resolución de IDs deterministas) y populate() (ejecución de bindings).",
          example: "Transformación bidireccional entre un modelo de Proceso y un modelo de Tareas de un sistema externo.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation/core", line: 9, end_line: 23, label: "TransformationDefinitionDocument" }]
        },
        {
          name: "TransformationRule / TransformationBinding / TransformationValuePolicy",
          purpose: "Una regla mapea un patrón origen a uno o varios patrones destino mediante bindings de campo a campo.",
          whenUsed: "Por cada tipo de elemento origen que debe producir uno o varios elementos destino.",
          properties: ["TransformationRule: guard (ExpressionDefinition anulable), targets: List<TransformationTargetPattern> (fan-out), bindings[], direction ∈ FORWARD/REVERSE, correspondenceId", "TransformationBinding: kind ∈ DIRECT/CONSTANT/EXPRESSION/TRACE_EQUIVALENT", "TransformationValuePolicy (ortogonal): collectionMode, conversionMode, enumMapping, errorPolicy, fallbackValue"],
          relationships: ["Las reglas REVERSE se autoran por separado — no se invierten automáticamente desde una regla FORWARD"],
          cardinality: "1 TransformationRule ↔ 1..N TransformationTargetPattern ↔ 1..N TransformationBinding",
          io: { inputs: "Elemento origen que cumple el guard", outputs: "Uno o varios elementos destino poblados" },
          lifecycle: "Fase 1 (allocate): recorre, resuelve herencia, evalúa guard y asigna IDs deterministas SHA-256. Fase 2 (populate): ejecuta los bindings, resolviendo TRACE_EQUIVALENT contra el índice de asignación ya construido.",
          example: "Regla Tarea→TareaExterna con binding DIRECT para nombre y EXPRESSION para estado calculado.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java", line: 76, end_line: 119, label: "allocate()/populate()" }]
        },
        {
          name: "TransformationIncrementalSynchronizationEngine + TransformationSynchronizationSession",
          purpose: "Sincronización incremental y genuinamente bidireccional entre origen y destino, con detección y resolución de conflictos.",
          whenUsed: "Cuando la transformación es BIDIRECTIONAL y ambos lados pueden cambiar de forma independiente.",
          properties: ["Motor: diff contra baseline, conflicts(), TransformationConflictPolicy (FAIL_ON_CONFLICT/FORWARD_WINS/REVERSE_WINS), fusión a nivel de característica (feature-level merge)", "Sesión: guarda solo un baseline rodante (sourceBaseline, targetBaseline, baselineTrace, revision) + historial de checkpoints para rollback"],
          relationships: ["Esto es MÁS que 'paridad direccional': es sincronización bidireccional real del runtime propio de HYDRA (no aplica a puentes con toolchains nativos externos, que solo ofrecen paridad direccional)"],
          cardinality: "1 TransformationDefinitionDocument bidireccional ↔ 1 TransformationSynchronizationSession activa",
          io: { inputs: "Cambios en origen y/o destino desde el último baseline", outputs: "Modelo(s) actualizados + conflictos resueltos o reportados" },
          lifecycle: "Cada sincronización actualiza el baseline rodante y añade un checkpoint al historial para poder deshacer.",
          example: "Editar una Tarea en el modelo externo y propagar el cambio de vuelta al modelo de Proceso sin perder ediciones locales no conflictivas.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation", line: 1, label: "TransformationIncrementalSynchronizationEngine (499 líneas)" }]
        },
        {
          name: "TransformationNativeParityController / toolchain nativo",
          purpose: "Ejecución explícita de una transformación usando un motor de toolchain externo (p. ej. ATL) en vez del runtime canónico de HYDRA.",
          whenUsed: "Cuando se requiere paridad con una herramienta externa específica, no sincronización incremental propia.",
          properties: ["Requiere bundles de motor aprovisionados por un administrador", "Ejecución en proceso externo aislado", "Validación de protocolo de auto-descripción del motor"],
          relationships: ["Es una acción EXPLÍCITA separada, no un simple flag de modo sobre la misma transformación"],
          cardinality: "1 definición de transformación ↔ 0..1 ejecución vía toolchain nativo por invocación",
          io: { inputs: "Modelo origen + bundle de motor externo", outputs: "Modelo destino producido por el motor externo" },
          lifecycle: "Paridad direccional únicamente (no sincronización bidireccional) cuando se usa un motor externo.",
          example: "Ejecutar la misma transformación con un motor ATL real para verificar paridad de resultados.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation", line: 1, label: "TransformationNativeParityController" }]
        }
      ],
      authoring: {
        summary: "La transformación se autora con un diseñador de TRES ZONAS (árbol origen | taller de reglas | árbol destino), dirigido por botones (seleccionar + '¡Mapear propiedades!'), no por líneas dibujadas a mano — no hay lienzo con conectores arrastrables entre los dos árboles. El guard de una regla y los bindings de tipo EXPRESSION reutilizan el mismo ExpressionBuilder que Validación. TRACE_EQUIVALENT nunca se expone como opción nombrada al usuario: solo se asigna implícitamente al mapear una relación.",
        elements: [
          { element: "Alcance (M2M/M2T/T2M, origen/destino, direccionalidad)", requiredness: "MANDATORY", modes: [
            { mode: "Formulario (paso 'Alcance' del asistente)", detail: "Nombre, tipo, direccionalidad, selección de metamodelo origen/destino; con política de conflicto (detener/gana-origen/gana-destino), 'propagar borrados' y 'exigir traza previa' cuando es BIDIRECTIONAL." }
          ]},
          { element: "Regla de transformación (M2M)", requiredness: "REPEATABLE", modes: [
            { mode: "Árbol origen + árbol destino + botones", detail: "Seleccionar una clase en cada árbol y pulsar 'Crear regla a partir de las clases seleccionadas' o 'Mapear propiedades seleccionadas' — el mapeo se hace por selección y botón, no dibujando una línea." },
            { mode: "Tarjeta de regla + editor de bindings", detail: "Cada regla mapea atributos (binding DIRECT/CONSTANT/EXPRESSION, con 'Mapear nombres compatibles' automático) y relaciones (siempre TRACE_EQUIVALENT, asignado implícitamente al elegir la relación origen) por separado." },
            { mode: "Guard (condición de aplicación)", detail: "Botón 'Añadir condición' que reutiliza el mismo constructor guiado/avanzado de expresiones de Validación, con alcance a la clase origen de la regla." }
          ]},
          { element: "Sincronización incremental bidireccional", requiredness: "CONDITIONAL", modes: [
            { mode: "Panel de sesiones gestionadas", detail: "Solo si directionality=BIDIRECTIONAL: crear una sesión (elige qué lado es 'autoritativo' inicial), sincronizar, rebase contra una definición cambiada, restaurar un baseline anterior del historial." },
            { mode: "Sincronización puntual (ad hoc)", detail: "Modo BATCH/INCREMENTAL con textos JSON pegados de modelo origen/destino (y baseline si es incremental) — para pruebas rápidas sin crear una sesión persistente." }
          ]},
          { element: "Ejecución con toolchain nativo (ATL, etc.)", requiredness: "OPTIONAL", modes: [
            { mode: "Panel de verificación + comparación", detail: "Elegir lenguaje de ejecución, 'Comprobar soporte de ejecución' (diagnóstico de salud), 'Ejecutar y comparar' (corre el motor externo real y compara el resultado con el runtime propio de HYDRA)." },
            { mode: "Panel de aprovisionamiento", detail: "Instalar/actualizar/eliminar el paquete de motor externo para un lenguaje directamente desde el navegador, contra un catálogo de confianza administrado." }
          ]}
        ]
      },
      level3: "Una transformación conecta N endpoints origen con N endpoints destino mediante reglas que mapean patrones (con fan-out a varios patrones destino) y bindings de 4 tipos exactos (DIRECT/CONSTANT/EXPRESSION/TRACE_EQUIVALENT), más una política de valores ortogonal. Las reglas REVERSE se escriben aparte, no se derivan automáticamente. Cuando la direccionalidad es BIDIRECTIONAL, el motor de sincronización incremental del propio HYDRA ofrece diff+conflictos+merge reales sobre un baseline rodante — bastante más que una simple comparación direccional. La ejecución contra un motor de toolchain externo (ATL, etc.) es una vía EXPLÍCITA distinta, limitada a paridad direccional.",
      level4: "Ejecución en dos fases: allocate() recorre el grafo origen, resuelve herencia de reglas, evalúa el guard de cada regla y asigna IDs destino deterministas (SHA-256) — construyendo así un índice de asignación completo ANTES de poblar ningún valor. populate() ejecuta después los bindings de cada regla, pudiendo resolver bindings TRACE_EQUIVALENT contra ese índice ya construido. La traza (TransformationTrace/TraceLink/BindingTrace) se registra salvo que tracePolicy la desactive (activada por defecto). El quick-fix de Transformación (TransformationQuickFixService) es un mecanismo aparte, sin mutación directa: solo propone (suggest()) una copia completa del documento con el cambio sugerido.",
      level5: {
        incoming: ["Metamodel (define los endpoints origen/destino)", "Grammar (encadenamiento opcional T2M→M2M vía postTransformationId)", "Model Editor (el modelo editado es a menudo el origen de una transformación)"],
        outgoing: ["Integration (toolchains nativos externos para ejecución con paridad direccional)", "Generación/Publicación (el modelo destino puede alimentar generación de código)"]
      },
      level6: {
        runtime: "CanonicalTransformationRuntime ejecuta allocate()/populate(); TransformationIncrementalSynchronizationEngine gestiona el modo bidireccional.",
        services: ["CanonicalTransformationRuntime", "TransformationIncrementalSynchronizationEngine", "TransformationNativeParityController", "TransformationQuickFixService"],
        apis: ["REST de ejecución de transformación, sincronización incremental y quick-fix de transformación"],
        sources: [
          { path: "src/main/java/com/example/editor/transformation/core", line: 9, end_line: 23, label: "TransformationDefinitionDocument / TransformationRule" },
          { path: "src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java", line: 76, end_line: 119, label: "allocate()/populate()" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines declarative rules to transform models (and, incrementally and bidirectionally, keep them synchronized) between source and target endpoints.",
        value: "Keeps two representations of the same knowledge (e.g. two different metamodels) in sync, with traceability and conflict resolution.",
        inputs: ["Source model(s) conforming to their metamodel(s)", "Transformation rule definition"],
        outputs: ["Target model(s)", "Transformation trace (TransformationTrace)", "Incremental synchronization session (if bidirectional mode is used)"]
      },
      level2: [
        {
          name: "TransformationDefinitionDocument",
          purpose: "Root document of a transformation: kind, directionality, endpoints, rules and execution/trace policies.",
          whenUsed: "To declare how a target model is derived from, or kept in sync with, one or more source models.",
          properties: ["schemaVersion (\"1.1\"), id, name, kind: TransformationKind", "directionality: TransformationDirectionality (UNIDIRECTIONAL/BIDIRECTIONAL)", "synchronizationPolicy", "sources[]/targets[]: TransformationEndpoint", "rules[]: TransformationRule", "generationRules[], textToModel, executionPolicy, tracePolicy"],
          relationships: ["sources/targets reference metamodels; rules reference classes from those metamodels"],
          cardinality: "1 TransformationDefinitionDocument ↔ 1..N endpoints ↔ 1..N TransformationRule",
          io: { inputs: "Source model(s)", outputs: "Target model(s)" },
          lifecycle: "Two-phase execution: allocate() (deterministic ID resolution) and populate() (binding execution).",
          example: "A bidirectional transformation between a Process model and an external system's Task model.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation/core", line: 9, end_line: 23, label: "TransformationDefinitionDocument" }]
        },
        {
          name: "TransformationRule / TransformationBinding / TransformationValuePolicy",
          purpose: "A rule maps a source pattern to one or several target patterns via field-to-field bindings.",
          whenUsed: "For every source element type that must produce one or more target elements.",
          properties: ["TransformationRule: guard (nullable ExpressionDefinition), targets: List<TransformationTargetPattern> (fan-out), bindings[], direction ∈ FORWARD/REVERSE, correspondenceId", "TransformationBinding: kind ∈ DIRECT/CONSTANT/EXPRESSION/TRACE_EQUIVALENT", "TransformationValuePolicy (orthogonal): collectionMode, conversionMode, enumMapping, errorPolicy, fallbackValue"],
          relationships: ["REVERSE rules are authored separately — they are not auto-derived from a FORWARD rule"],
          cardinality: "1 TransformationRule ↔ 1..N TransformationTargetPattern ↔ 1..N TransformationBinding",
          io: { inputs: "Source element matching the guard", outputs: "One or more populated target elements" },
          lifecycle: "Phase 1 (allocate): traverses, resolves inheritance, evaluates the guard and assigns deterministic SHA-256 ids. Phase 2 (populate): executes bindings, resolving TRACE_EQUIVALENT against the already-built allocation index.",
          example: "Task→ExternalTask rule with a DIRECT binding for name and an EXPRESSION binding for a computed status.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java", line: 76, end_line: 119, label: "allocate()/populate()" }]
        },
        {
          name: "TransformationIncrementalSynchronizationEngine + TransformationSynchronizationSession",
          purpose: "Incremental and genuinely bidirectional synchronization between source and target, with conflict detection and resolution.",
          whenUsed: "When the transformation is BIDIRECTIONAL and both sides can change independently.",
          properties: ["Engine: baseline diff, conflicts(), TransformationConflictPolicy (FAIL_ON_CONFLICT/FORWARD_WINS/REVERSE_WINS), feature-level merge", "Session: stores only a rolling baseline (sourceBaseline, targetBaseline, baselineTrace, revision) + checkpoint history for rollback"],
          relationships: ["This is MORE than 'directional parity': it is real bidirectional synchronization in HYDRA's own runtime (does not apply to bridges with external native toolchains, which only offer directional parity)"],
          cardinality: "1 bidirectional TransformationDefinitionDocument ↔ 1 active TransformationSynchronizationSession",
          io: { inputs: "Changes on source and/or target since the last baseline", outputs: "Updated model(s) + resolved or reported conflicts" },
          lifecycle: "Each sync updates the rolling baseline and appends a checkpoint to the history for undo.",
          example: "Editing a Task in the external model and propagating the change back to the Process model without losing non-conflicting local edits.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation", line: 1, label: "TransformationIncrementalSynchronizationEngine (499 lines)" }]
        },
        {
          name: "TransformationNativeParityController / native toolchain",
          purpose: "Explicit execution of a transformation using an external toolchain engine (e.g. ATL) instead of HYDRA's canonical runtime.",
          whenUsed: "When parity with a specific external tool is required, not HYDRA's own incremental synchronization.",
          properties: ["Requires admin-provisioned engine bundles", "Isolated external process execution", "Engine self-description protocol validation"],
          relationships: ["An EXPLICIT, separate action — not a simple mode flag on the same transformation"],
          cardinality: "1 transformation definition ↔ 0..1 native-toolchain execution per invocation",
          io: { inputs: "Source model + external engine bundle", outputs: "Target model produced by the external engine" },
          lifecycle: "Directional parity only (not bidirectional sync) when using an external engine.",
          example: "Running the same transformation through a real ATL engine to verify result parity.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation", line: 1, label: "TransformationNativeParityController" }]
        }
      ],
      authoring: {
        summary: "Transformations are authored with a THREE-ZONE designer (source tree | rule workspace | target tree), button-driven (select + 'Map properties!'), not hand-drawn lines — there is no canvas with draggable connectors between the two trees. A rule's guard and EXPRESSION-kind bindings reuse the same ExpressionBuilder as Validation. TRACE_EQUIVALENT is never exposed as a named user option: it's only assigned implicitly when mapping a relation.",
        elements: [
          { element: "Scope (M2M/M2T/T2M, source/target, directionality)", requiredness: "MANDATORY", modes: [
            { mode: "Form (wizard's 'Scope' step)", detail: "Name, kind, directionality, source/target metamodel pickers; with conflict policy (stop/source-wins/target-wins), 'propagate deletions' and 'require prior trace' when BIDIRECTIONAL." }
          ]},
          { element: "Transformation rule (M2M)", requiredness: "REPEATABLE", modes: [
            { mode: "Source tree + target tree + buttons", detail: "Select a class in each tree and click 'Create rule from selected classes' or 'Map selected properties' — mapping happens by selection and button, not by drawing a line." },
            { mode: "Rule card + binding editor", detail: "Each rule maps attributes (DIRECT/CONSTANT/EXPRESSION binding, with automatic 'Map compatible names') and relations (always TRACE_EQUIVALENT, implicitly assigned on picking the source relation) separately." },
            { mode: "Guard (application condition)", detail: "An 'Add condition' button reusing the same guided/advanced expression builder as Validation, scoped to the rule's source class." }
          ]},
          { element: "Bidirectional incremental synchronization", requiredness: "CONDITIONAL", modes: [
            { mode: "Managed-sessions panel", detail: "Only if directionality=BIDIRECTIONAL: create a session (choosing the initial 'authoritative' side), synchronize, rebase against a changed definition, restore a prior baseline from history." },
            { mode: "Ad-hoc preview sync", detail: "A BATCH/INCREMENTAL mode with pasted JSON source/target models (and baseline if incremental) — for quick testing without creating a persistent session." }
          ]},
          { element: "Execution via a native toolchain (ATL, etc.)", requiredness: "OPTIONAL", modes: [
            { mode: "Verify-and-compare panel", detail: "Pick an execution language, 'Check execution support' (health diagnosis), 'Run and compare' (runs the real external engine and diffs the result against HYDRA's own runtime)." },
            { mode: "Provisioning panel", detail: "Install/update/remove the external engine package for a language directly from the browser, against an admin-trusted catalog." }
          ]}
        ]
      },
      level3: "A transformation connects N source endpoints to N target endpoints through rules that map patterns (with fan-out to several target patterns) and bindings of exactly 4 kinds (DIRECT/CONSTANT/EXPRESSION/TRACE_EQUIVALENT), plus an orthogonal value policy. REVERSE rules are written separately, not auto-derived. When directionality is BIDIRECTIONAL, HYDRA's own incremental synchronization engine offers real diff+conflicts+merge over a rolling baseline — considerably more than a simple directional comparison. Execution against an external toolchain engine (ATL, etc.) is a distinct EXPLICIT path, limited to directional parity.",
      level4: "Two-phase execution: allocate() traverses the source graph, resolves rule inheritance, evaluates each rule's guard and assigns deterministic (SHA-256) target ids — building a complete allocation index BEFORE populating any value. populate() then executes each rule's bindings, able to resolve TRACE_EQUIVALENT bindings against that already-built index. Trace (TransformationTrace/TraceLink/BindingTrace) is recorded unless tracePolicy disables it (enabled by default). Transformation's quick-fix (TransformationQuickFixService) is a separate mechanism with no direct mutation: it only proposes (suggest()) a full document copy with the suggested change.",
      level5: {
        incoming: ["Metamodel (defines source/target endpoints)", "Grammar (optional T2M→M2M chaining via postTransformationId)", "Model Editor (the edited model is often a transformation's source)"],
        outgoing: ["Integration (external native toolchains for directional-parity execution)", "Generation/Publication (the target model can feed code generation)"]
      },
      level6: {
        runtime: "CanonicalTransformationRuntime runs allocate()/populate(); TransformationIncrementalSynchronizationEngine manages bidirectional mode.",
        services: ["CanonicalTransformationRuntime", "TransformationIncrementalSynchronizationEngine", "TransformationNativeParityController", "TransformationQuickFixService"],
        apis: ["REST for transformation execution, incremental synchronization and transformation quick-fix"],
        sources: [
          { path: "src/main/java/com/example/editor/transformation/core", line: 9, end_line: 23, label: "TransformationDefinitionDocument / TransformationRule" },
          { path: "src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java", line: 76, end_line: 119, label: "allocate()/populate()" }
        ]
      }
    }
  },

  events: {
    archComponentId: "b_event_gami",
    kind: "messagebus",
    es: {
      level1: {
        what: "Define tipos de evento del dominio y un motor de flujo (EventFlow) que encadena pasos con ramas, reintentos, timeouts y compensación.",
        value: "Permite reaccionar a cambios del sistema (validación, quick fix, metamodelo, etc.) de forma desacoplada, incluyendo flujos con lógica tipo saga.",
        inputs: ["Eventos publicados por una sesión de editor con publishEvents() activado", "Eventos reservados del propio sistema (core.*)"],
        outputs: ["CanonicalEvent en el bus", "Progreso/resultado de un EventFlow (éxito, error, compensación)"]
      },
      level2: [
        {
          name: "EventTypeDefinition",
          purpose: "Define un tipo de evento del dominio: nombre, espacio de nombres, campos, orígenes y sujetos permitidos.",
          whenUsed: "Para declarar un nuevo tipo de suceso que el sistema o el usuario quiere observar.",
          properties: ["id, name, namespace, description, category, version", "fields[]: EventFieldDefinition", "allowedSources", "allowedSubjects: EventSubjectKind (16 valores, incl. METAMODEL/META_CLASS/MODEL_ELEMENT/VALIDATION_RULE/QUICK_FIX/GAMIFICATION_RULE/EDITOR)", "persistencePolicy"],
          relationships: ["CanonicalEvent.subject referencia el elemento afectado vía EventSubject, no vía EventBinding"],
          cardinality: "0..N EventTypeDefinition por instalación; ~24 ya reservados (ReservedEventCatalog)",
          io: { inputs: "Definición declarativa", outputs: "Instancias CanonicalEvent publicadas en el bus" },
          lifecycle: "Un tipo de evento puede ser reservado del sistema (core.*) o definido por el usuario.",
          example: "core.validation.violation-reopened, con subject apuntando al elemento de modelo reabierto.",
          evidence: [{ path: "src/main/java/com/example/editor/event/EventTypeDefinition.java", line: 11, label: "EventTypeDefinition" }]
        },
        {
          name: "EventBinding",
          purpose: "Regla de transformación evento→evento: de un tipo origen a un tipo destino, con condición y mapeo de payload.",
          whenUsed: "Cuando un evento debe derivar en otro evento distinto bajo cierta condición (no para enlazar un evento a un elemento de modelo).",
          properties: ["sourceEventTypeId → targetEventTypeId", "conditionExpressionId", "payloadMappings"],
          relationships: ["NO es un enlace evento→elemento de metamodelo; ese enlace es CanonicalEvent.subject"],
          cardinality: "1 EventTypeDefinition origen ↔ 0..N EventBinding",
          io: { inputs: "CanonicalEvent origen que cumple la condición", outputs: "Nuevo CanonicalEvent del tipo destino" },
          lifecycle: "Se evalúa cada vez que se publica un evento del tipo origen.",
          example: "Transformar core.quickfix.applied en core.gamification.points-candidate si la condición de payload se cumple.",
          evidence: [{ path: "src/main/java/com/example/editor/event", line: 1, label: "EventBinding" }]
        },
        {
          name: "FlowNode / FlowNodeKind (EventFlow)",
          purpose: "Grafo de flujo tipo BPMN/CEP con 28 clases de nodo reales para ramificación, espera, reintento y compensación.",
          whenUsed: "Cuando una reacción a eventos necesita más que 'si pasa X, hacer Y': pasos secuenciales, decisiones, timeouts, sagas.",
          properties: ["FlowNodeKind (28 valores): START, SUCCESS_END, ERROR_END, DECISION, MERGE, PARALLEL_SPLIT, JOIN, TIMEOUT, RETRY, WAIT_FOR_EVENT, COMPENSATION_STEP, FALLBACK, SOURCE, FILTER, MAP, ROUTE, AGGREGATE, …"],
          relationships: ["EventFlowRuntimeService.executeQueue ejecuta el grafo en modo worklist; los eventos que produce se republican en el MISMO bus con tipos reservados (p. ej. core.event-flow.completed)"],
          cardinality: "1 definición de flujo ↔ N FlowNode conectados",
          io: { inputs: "Evento disparador + estado del flujo", outputs: "Eventos de progreso/fin + efectos de compensación" },
          lifecycle: "Soporta reintentos, timeouts y pasos de compensación — un motor de flujo genuino, no una simple lista de reglas.",
          example: "Flujo que espera confirmación externa (WAIT_FOR_EVENT), reintenta 3 veces (RETRY) y compensa si falla definitivamente.",
          evidence: [{ path: "src/main/java/com/example/editor/event/flow/FlowNodeKind.java", line: 4, label: "FlowNodeKind (28 valores)" }]
        }
      ],
      authoring: {
        summary: "Eventos tiene un verdadero diseñador visual de flujos tipo BPMN/CEP (lienzo @xyflow con paleta de arrastrar/soltar y los 28 tipos de nodo reales), además de formularios y un editor visual aparte para la 'arquitectura de eventos' (bindings dibujados como conexiones entre tipos de evento). No es solo formularios/JSON como podría asumirse de un backend basado en eventos.",
        elements: [
          { element: "EventTypeDefinition", requiredness: "MANDATORY", modes: [
            { mode: "Formulario guiado/avanzado", detail: "Contrato (nombre, ID estable, categoría, versión, persistencia), alcance (orígenes/sujetos permitidos como chips multi-selección), campos de datos del evento (tipo entre 13 opciones), ciclo de vida (habilitado/abstracto/obsoleto)." },
            { mode: "Árbol de estructura / Fuente JSON", detail: "Vista de árbol (metamodelo + eventos definidos) y una vista de código fuente JSON crudo con 'Aplicar origen' como vías alternativas al mismo formulario." }
          ]},
          { element: "Flujo de eventos (EventFlow): nodos y transiciones", requiredness: "ALTERNATIVE", modes: [
            { mode: "Lienzo visual real (arrastrar/soltar/conectar)", detail: "Paleta con 5 categorías de nodo (ciclo de vida, eventos y datos, CEP y estado, ejecución, control y resiliencia) cubriendo los 28 FlowNodeKind; nodos añadidos por arrastre o clic; conexiones dibujadas arrastrando o en modo CONEXIÓN clic-origen→clic-destino; deshacer/rehacer, duplicar, auto-layout (incl. ELK)." },
            { mode: "Inspector de propiedades por nodo", detail: "Divulgación progresiva según el tipo de nodo: siempre nombre/tipo/habilitado; referencia a evento si aplica; condición si aplica; reintentos/timeout/compensación si es un nodo de resiliencia; configuración avanzada JSON para parámetros sin campo dedicado aún." },
            { mode: "Formulario / Árbol / Fuente (vistas alternativas)", detail: "El mismo modelo subyacente también se puede ver y editar como formulario plano, árbol de estructura o JSON crudo — 5 vistas en total sobre el mismo flujo." }
          ]},
          { element: "EventBinding (evento→evento)", requiredness: "OPTIONAL", modes: [
            { mode: "Formulario guiado/avanzado", detail: "Identidad → origen/destino → condición (solo un campo de texto libre con el ID de una expresión ya definida en otro sitio) → mapeo de payload → política de error." },
            { mode: "Lienzo de 'Arquitectura de eventos'", detail: "Un lienzo @xyflow dedicado, distinto del de EventFlow: paleta de tipos de evento (arrastrar o botón '+ Nuevo evento personalizado'); arrastrar una conexión de un evento a un evento personalizado crea el EventBinding directamente a partir de la flecha dibujada — aquí SÍ hay dibujo de conexiones, a diferencia de Transformación." }
          ]}
        ]
      },
      level3: "Un EventTypeDefinition declara la forma de un evento y qué sujetos del dominio puede referenciar (vía EventSubjectKind). Un EventBinding transforma eventos de un tipo a otro bajo condición — es una relación evento→evento, no evento→elemento (esa relación vive en CanonicalEvent.subject). El motor EventFlow añade una capa de orquestación con grafo de 28 tipos de nodo, ejecutado por EventFlowRuntimeService en modo worklist; sus propios eventos de resultado se republican en el mismo bus, por lo que Gamification puede reaccionar tanto a eventos de dominio directos como a eventos derivados de un flujo.",
      level4: "La publicación de eventos NO es automática para toda mutación del modelo: el camino legado (ModelMutationService) no tiene ninguna referencia al bus de eventos. Solo el camino moderno (EditorSessionEventPublisher) publica, y únicamente si el autor del editor activó explícitamente runtimePolicy().publishEvents() (opt-in, por sesión). Un EventFlow se ejecuta como cola de trabajo: avanza nodo a nodo, puede esperar un evento externo, reintentar con política propia, o ejecutar un paso de compensación si algo falla aguas abajo.",
      level5: {
        incoming: ["Model Editor (si publishEvents() está activo)", "Validación/Quick Fix (eventos reservados core.validation.*, core.quickfix.*)", "Metamodel (eventos reservados core.metamodel.*)"],
        outgoing: ["Gamification (consumidor principal, vía suscripción amplia al bus)", "Auditoría universal (AuditCanonicalDocumentMutationObserver, pero esta es independiente del bus de eventos opt-in — ver nota en Project)"]
      },
      level6: {
        runtime: "EditorSessionEventPublisher publica; EventFlowRuntimeService.executeQueue ejecuta flujos; ReservedEventCatalog centraliza ~24 tipos reservados.",
        services: ["EditorSessionEventPublisher", "EventFlowRuntimeService", "ReservedEventCatalog"],
        apis: ["Bus de eventos interno (CanonicalEventBus) + REST de consulta/depuración de flujos"],
        sources: [
          { path: "src/main/java/com/example/editor/event/EventTypeDefinition.java", line: 11, label: "EventTypeDefinition" },
          { path: "src/main/java/com/example/editor/event/flow/FlowNodeKind.java", line: 4, label: "FlowNodeKind" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines domain event types and a flow engine (EventFlow) that chains steps with branches, retries, timeouts and compensation.",
        value: "Lets the system react to changes (validation, quick fix, metamodel, etc.) in a decoupled way, including saga-like flows.",
        inputs: ["Events published by an editor session with publishEvents() enabled", "The system's own reserved events (core.*)"],
        outputs: ["CanonicalEvent on the bus", "EventFlow progress/result (success, error, compensation)"]
      },
      level2: [
        {
          name: "EventTypeDefinition",
          purpose: "Defines a domain event type: name, namespace, fields, allowed sources and subjects.",
          whenUsed: "To declare a new kind of occurrence the system or the user wants to observe.",
          properties: ["id, name, namespace, description, category, version", "fields[]: EventFieldDefinition", "allowedSources", "allowedSubjects: EventSubjectKind (16 values, incl. METAMODEL/META_CLASS/MODEL_ELEMENT/VALIDATION_RULE/QUICK_FIX/GAMIFICATION_RULE/EDITOR)", "persistencePolicy"],
          relationships: ["CanonicalEvent.subject references the affected element via EventSubject, not via EventBinding"],
          cardinality: "0..N EventTypeDefinition per installation; ~24 already reserved (ReservedEventCatalog)",
          io: { inputs: "Declarative definition", outputs: "CanonicalEvent instances published on the bus" },
          lifecycle: "An event type can be system-reserved (core.*) or user-defined.",
          example: "core.validation.violation-reopened, with subject pointing at the reopened model element.",
          evidence: [{ path: "src/main/java/com/example/editor/event/EventTypeDefinition.java", line: 11, label: "EventTypeDefinition" }]
        },
        {
          name: "EventBinding",
          purpose: "Event→event transformation rule: from a source type to a target type, with a condition and payload mapping.",
          whenUsed: "When one event should produce a different event under a given condition (not for linking an event to a model element).",
          properties: ["sourceEventTypeId → targetEventTypeId", "conditionExpressionId", "payloadMappings"],
          relationships: ["NOT an event→metamodel-element link; that link is CanonicalEvent.subject"],
          cardinality: "1 source EventTypeDefinition ↔ 0..N EventBinding",
          io: { inputs: "Source CanonicalEvent matching the condition", outputs: "New CanonicalEvent of the target type" },
          lifecycle: "Evaluated every time an event of the source type is published.",
          example: "Transforming core.quickfix.applied into core.gamification.points-candidate if the payload condition holds.",
          evidence: [{ path: "src/main/java/com/example/editor/event", line: 1, label: "EventBinding" }]
        },
        {
          name: "FlowNode / FlowNodeKind (EventFlow)",
          purpose: "A BPMN/CEP-style flow graph with 28 real node kinds for branching, waiting, retrying and compensating.",
          whenUsed: "When reacting to events needs more than 'if X happens, do Y': sequential steps, decisions, timeouts, sagas.",
          properties: ["FlowNodeKind (28 values): START, SUCCESS_END, ERROR_END, DECISION, MERGE, PARALLEL_SPLIT, JOIN, TIMEOUT, RETRY, WAIT_FOR_EVENT, COMPENSATION_STEP, FALLBACK, SOURCE, FILTER, MAP, ROUTE, AGGREGATE, …"],
          relationships: ["EventFlowRuntimeService.executeQueue runs the graph worklist-style; the events it produces are republished on the SAME bus with reserved types (e.g. core.event-flow.completed)"],
          cardinality: "1 flow definition ↔ N connected FlowNode",
          io: { inputs: "Triggering event + flow state", outputs: "Progress/completion events + compensation effects" },
          lifecycle: "Supports retries, timeouts and compensation steps — a genuine flow engine, not a simple rule list.",
          example: "A flow that waits for external confirmation (WAIT_FOR_EVENT), retries 3 times (RETRY) and compensates if it ultimately fails.",
          evidence: [{ path: "src/main/java/com/example/editor/event/flow/FlowNodeKind.java", line: 4, label: "FlowNodeKind (28 values)" }]
        }
      ],
      authoring: {
        summary: "Events has a genuine BPMN/CEP-style visual flow designer (an @xyflow canvas with a drag-and-drop palette and all 28 real node kinds), plus forms and a separate visual editor for 'event architecture' (bindings drawn as connections between event types). It is not form/JSON-only as one might assume from an event-driven backend.",
        elements: [
          { element: "EventTypeDefinition", requiredness: "MANDATORY", modes: [
            { mode: "Guided/advanced form", detail: "Contract (name, stable ID, category, version, persistence), scope (allowed sources/subjects as multi-select chips), event data fields (type from 13 options), lifecycle (enabled/abstract/deprecated)." },
            { mode: "Structure tree / Raw JSON source", detail: "A tree view (metamodel + defined events) and a raw JSON source view with 'Apply source' as alternative paths to the same underlying form." }
          ]},
          { element: "Event flow (EventFlow): nodes and transitions", requiredness: "ALTERNATIVE", modes: [
            { mode: "Real visual canvas (drag/drop/connect)", detail: "A palette with 5 node categories (lifecycle, events and data, CEP and state, execution, control and resilience) covering all 28 FlowNodeKind; nodes added by drag or click; connections drawn by dragging or in CONNECT mode (click source→click target); undo/redo, duplicate, auto-layout (incl. ELK)." },
            { mode: "Per-node properties inspector", detail: "Progressive disclosure by node kind: always name/kind/enabled; an event reference if applicable; a condition if applicable; retry/timeout/compensation fields for resilience nodes; a raw JSON 'advanced configuration' for parameters with no dedicated field yet." },
            { mode: "Form / Tree / Source (alternate views)", detail: "The same underlying model can also be viewed/edited as a flat form, a structure tree, or raw JSON — 5 views total over the same flow." }
          ]},
          { element: "EventBinding (event→event)", requiredness: "OPTIONAL", modes: [
            { mode: "Guided/advanced form", detail: "Identity → source/target → condition (just a free-text field referencing an expression ID defined elsewhere) → payload mapping → error policy." },
            { mode: "'Event architecture' canvas", detail: "A dedicated @xyflow canvas, distinct from the EventFlow one: an event-type palette (drag or '+ New custom event' button); dragging a connection from one event to a custom event creates the EventBinding directly from the drawn arrow — here connections ARE drawn, unlike Transformation." }
          ]}
        ]
      },
      level3: "An EventTypeDefinition declares an event's shape and which domain subjects it can reference (via EventSubjectKind). An EventBinding transforms events from one type to another under a condition — an event→event relationship, not event→element (that relationship lives in CanonicalEvent.subject). The EventFlow engine adds an orchestration layer with a 28-node-kind graph, run by EventFlowRuntimeService worklist-style; its own result events are republished on the same bus, so Gamification can react to both direct domain events and flow-derived events.",
      level4: "Event publication is NOT automatic for every model mutation: the legacy path (ModelMutationService) has zero references to the event bus. Only the modern path (EditorSessionEventPublisher) publishes, and only if the editor's author explicitly enabled runtimePolicy().publishEvents() (opt-in, per session). An EventFlow runs as a worklist: it advances node by node, can wait for an external event, retry under its own policy, or run a compensation step if something fails downstream.",
      level5: {
        incoming: ["Model Editor (if publishEvents() is enabled)", "Validation/Quick Fix (reserved core.validation.*, core.quickfix.* events)", "Metamodel (reserved core.metamodel.* events)"],
        outgoing: ["Gamification (primary consumer, via a broad bus subscription)", "Universal audit trail (AuditCanonicalDocumentMutationObserver, but this is independent of the opt-in event bus — see the Project note)"]
      },
      level6: {
        runtime: "EditorSessionEventPublisher publishes; EventFlowRuntimeService.executeQueue runs flows; ReservedEventCatalog centralizes ~24 reserved types.",
        services: ["EditorSessionEventPublisher", "EventFlowRuntimeService", "ReservedEventCatalog"],
        apis: ["Internal event bus (CanonicalEventBus) + REST for flow inspection/debugging"],
        sources: [
          { path: "src/main/java/com/example/editor/event/EventTypeDefinition.java", line: 11, label: "EventTypeDefinition" },
          { path: "src/main/java/com/example/editor/event/flow/FlowNodeKind.java", line: 4, label: "FlowNodeKind" }
        ]
      }
    }
  },

  gamification: {
    archComponentId: "b_event_gami",
    kind: "messagebus",
    es: {
      level1: {
        what: "Reacciona a eventos del bus (directos o derivados de un EventFlow) para otorgar puntos y logros, con un ledger auditable y compensación automática.",
        value: "Añade motivación e incentivos medibles sobre la actividad real del usuario en HYDRA, sin acoplar la lógica de dominio a la lógica de puntuación.",
        inputs: ["Eventos del bus (core.* o definidos por el usuario)", "Reglas de gamificación (condición + recompensa)"],
        outputs: ["Entradas en el ledger de puntos/logros por actor", "Notificaciones al usuario (toast + panel de actividad)"]
      },
      level2: [
        {
          name: "Regla de gamificación (condición → recompensa)",
          purpose: "Vincula un tipo de evento y una condición a una recompensa (puntos, logro, nivel, reto).",
          whenUsed: "Para definir qué acción del usuario/sistema otorga qué recompensa.",
          properties: ["eventTypeId de referencia", "conditionExpressionId (kernel semantic.expression compartido)", "recompensa: puntos / achievement / nivel / challenge"],
          relationships: ["GamificationRuntimeService.subscribe() escucha TODO el bus (filtro amplio) y luego hace coincidir cada evento contra rule.eventTypeId() — mecanismo híbrido, no suscripción fina por regla"],
          cardinality: "0..N reglas por instalación; 1 regla ↔ 1 eventTypeId de disparo",
          io: { inputs: "CanonicalEvent que coincide con eventTypeId + condición", outputs: "Entrada de ledger + notificación" },
          lifecycle: "Puede dispararse por eventos de dominio directos o por eventos republicados por un EventFlow (p. ej. core.event-flow.completed).",
          example: "Regla que otorga 10 puntos cuando se aplica un quick fix (core.quickfix.applied) sobre una regla de severidad ERROR.",
          evidence: [{ path: "src/main/java/com/example/editor/gamification", line: 1, label: "GamificationRuntimeService.subscribe()" }]
        },
        {
          name: "GamificationRuntimeState / GamificationLedger",
          purpose: "Estado persistente por actor (puntos acumulados, logros, nivel) y registro de auditoría append-only de cada recompensa otorgada.",
          whenUsed: "Se actualiza cada vez que una regla coincide con un evento.",
          properties: ["Persistente, por actor, protegido con ETag (optimistic concurrency)", "Ledger append-only: nunca se sobreescribe, solo se añade"],
          relationships: ["Confirmadas exactamente 3 clases de repositorio / 4 documentos físicos: definición, binding, runtime-state+ledger"],
          cardinality: "1 actor ↔ 1 GamificationRuntimeState ↔ N entradas de ledger",
          io: { inputs: "Evento coincidente + regla", outputs: "Nueva entrada de ledger + estado actualizado" },
          lifecycle: "compensate() revierte automáticamente entradas previas del ledger cuando llega core.quickfix.undone o core.validation.violation-reopened — el sistema de puntos se autocorrige si la acción original se deshace.",
          example: "Si se deshace un quick fix que había otorgado puntos, compensate() resta esos puntos y dentro del ledger queda constancia de la reversión.",
          evidence: [{ path: "src/main/java/com/example/editor/gamification", line: 1, label: "GamificationRuntimeState / GamificationLedger" }]
        },
        {
          name: "GamificationFeedbackProjector → UserFeedbackEnvelope",
          purpose: "Proyecta cambios de estado de gamificación en notificaciones consumibles por el frontend.",
          whenUsed: "Cada vez que se otorga o revierte una recompensa.",
          properties: ["UserFeedbackEnvelope: payload de notificación de usuario"],
          relationships: ["Consumido por frontend/src/editor-definition/GamificationFeedbackCenter.tsx (toasts + panel de actividad persistente)"],
          cardinality: "1 cambio de ledger ↔ 1 UserFeedbackEnvelope",
          io: { inputs: "Entrada de ledger nueva", outputs: "Notificación visible en el frontend" },
          lifecycle: "Entrega en tiempo real al workbench si la sesión está activa.",
          example: "Toast '¡+10 puntos por reparar una validación!' tras aplicar un quick fix.",
          evidence: [{ path: "frontend/src/editor-definition/GamificationFeedbackCenter.tsx", line: 1, label: "GamificationFeedbackCenter" }]
        }
      ],
      authoring: {
        summary: "Gamificación tiene 6 vistas sobre el mismo modelo: Guiado, Formulario, Árbol de estructura, Diagrama, Fuente y 'Runtime y ledger'. Las reglas de evidencia son un único formulario flexible (no una pestaña distinta por tipo de recompensa): una misma regla de otorgar puede, a la vez, dar puntos, avanzar un reto, desbloquear un logro Y conceder una recompensa directa. Las definiciones de catálogo (puntos/niveles/logros/retos/recompensas) son formularios propios y separados de las reglas.",
        elements: [
          { element: "Regla de evidencia (condición → recompensa)", requiredness: "REPEATABLE", modes: [
            { mode: "Formulario único flexible", detail: "Evento verificado (nunca un clic de botón) + condición opcional (ruta + operador + valor esperado) + tipo de efecto Otorgar/Penalizar: Otorgar revela puntos + logro/reto/recompensa opcionales simultáneos; Penalizar solo afecta a puntos. Política de repetición (una vez/una vez por evento/ilimitada/cooldown/límite diario)." },
            { mode: "Diagrama", detail: "Paleta con 'Reglas de evidencia' y 'Progresión' (puntos/nivel/logro/reto) más recompensas; arrastrar y conectar evidencia→regla→puntos/logro/reto/recompensa, nivel→tipo de punto, etc." },
            { mode: "Asistente guiado", detail: "Catálogo (nombre + tipos de punto) → progresión y recompensas (niveles/logros/retos/recompensas) → reglas de evidencia → arquitectura → revisión/guardar." }
          ]},
          { element: "Definiciones de catálogo (Punto/Nivel/Logro/Reto/Recompensa)", requiredness: "OPTIONAL", modes: [
            { mode: "Formulario dedicado por tipo", detail: "Cada uno con sus propios campos (p. ej. Logro: nombre/ID/descripción/recompensa opcional/'oculto hasta conseguirse'; Recompensa: nombre/ID/descripción + un JSON de payload arbitrario — sin selector de icono ni imagen, solo texto/JSON)." }
          ]},
          { element: "Inspección del ledger (historial por actor)", requiredness: "MANDATORY", modes: [
            { mode: "Vista 'Runtime y ledger'", detail: "Tarjetas por actor (puntos por tipo, logros, recompensas), lista de notificaciones de usuario ya emitidas, y una tabla cruda del ledger (evento/regla/actor/efecto/puntos/estado ACTIVA·RECHAZADA·COMPENSADA/motivo) con botones Actualizar y Reiniciar." }
          ]}
        ]
      },
      level3: "Gamification se suscribe de forma amplia al bus de eventos y filtra internamente por regla (eventTypeId + condición), en vez de suscribirse de forma fina por tipo. El estado (puntos/logros/nivel) y el ledger de auditoría son persistentes y por actor, protegidos con ETag. Existe un mecanismo de compensación explícito que revierte entradas del ledger cuando la acción que las originó se deshace — p. ej. deshacer un quick fix o reabrir una violación ya reparada.",
      level4: "GamificationRuntimeService.subscribe() escucha el bus completo; por cada CanonicalEvent, comprueba si alguna regla coincide (eventTypeId + conditionExpressionId); si coincide, actualiza GamificationRuntimeState y añade una entrada append-only a GamificationLedger; GamificationFeedbackProjector traduce ese cambio en un UserFeedbackEnvelope consumido por el frontend. Si posteriormente llega un evento de reversión (core.quickfix.undone, core.validation.violation-reopened), compensate() añade una entrada de reversión equivalente.",
      level5: {
        incoming: ["Events (consumidor principal: eventos directos y eventos derivados de EventFlow)"],
        outgoing: ["Frontend GamificationFeedbackCenter.tsx (toasts + panel de actividad)"]
      },
      level6: {
        runtime: "GamificationRuntimeService gestiona suscripción+coincidencia; GamificationFeedbackProjector traduce a notificaciones de usuario.",
        services: ["GamificationRuntimeService", "GamificationFeedbackProjector"],
        apis: ["Bus de eventos interno (consumo); REST de consulta de estado/ledger por actor"],
        sources: [
          { path: "src/main/java/com/example/editor/gamification", line: 1, label: "Paquete gamification" },
          { path: "frontend/src/editor-definition/GamificationFeedbackCenter.tsx", line: 1, label: "GamificationFeedbackCenter.tsx" }
        ]
      }
    },
    en: {
      level1: {
        what: "Reacts to bus events (direct or EventFlow-derived) to award points and achievements, with an auditable ledger and automatic compensation.",
        value: "Adds measurable motivation and incentives on top of the user's real activity in HYDRA, without coupling domain logic to scoring logic.",
        inputs: ["Bus events (core.* or user-defined)", "Gamification rules (condition + reward)"],
        outputs: ["Point/achievement ledger entries per actor", "User notifications (toast + activity panel)"]
      },
      level2: [
        {
          name: "Gamification rule (condition → reward)",
          purpose: "Links an event type and a condition to a reward (points, achievement, level, challenge).",
          whenUsed: "To define which user/system action grants which reward.",
          properties: ["Reference eventTypeId", "conditionExpressionId (shared semantic.expression kernel)", "reward: points / achievement / level / challenge"],
          relationships: ["GamificationRuntimeService.subscribe() listens to the ENTIRE bus (broad filter) then matches each event against rule.eventTypeId() — a hybrid mechanism, not fine-grained per-rule subscription"],
          cardinality: "0..N rules per installation; 1 rule ↔ 1 triggering eventTypeId",
          io: { inputs: "CanonicalEvent matching eventTypeId + condition", outputs: "Ledger entry + notification" },
          lifecycle: "Can be triggered by direct domain events or by events republished by an EventFlow (e.g. core.event-flow.completed).",
          example: "A rule awarding 10 points when a quick fix is applied (core.quickfix.applied) to an ERROR-severity rule.",
          evidence: [{ path: "src/main/java/com/example/editor/gamification", line: 1, label: "GamificationRuntimeService.subscribe()" }]
        },
        {
          name: "GamificationRuntimeState / GamificationLedger",
          purpose: "Persistent per-actor state (accumulated points, achievements, level) and an append-only audit log of every reward granted.",
          whenUsed: "Updated every time a rule matches an event.",
          properties: ["Persistent, per-actor, ETag-protected (optimistic concurrency)", "Append-only ledger: never overwritten, only appended"],
          relationships: ["Confirmed exactly 3 repository classes / 4 physical documents: definition, binding, runtime-state+ledger"],
          cardinality: "1 actor ↔ 1 GamificationRuntimeState ↔ N ledger entries",
          io: { inputs: "Matching event + rule", outputs: "New ledger entry + updated state" },
          lifecycle: "compensate() automatically reverses prior ledger entries when core.quickfix.undone or core.validation.violation-reopened arrives — the scoring system self-corrects if the original action is undone.",
          example: "If a quick fix that had awarded points is undone, compensate() subtracts those points and the ledger records the reversal.",
          evidence: [{ path: "src/main/java/com/example/editor/gamification", line: 1, label: "GamificationRuntimeState / GamificationLedger" }]
        },
        {
          name: "GamificationFeedbackProjector → UserFeedbackEnvelope",
          purpose: "Projects gamification state changes into notifications consumable by the frontend.",
          whenUsed: "Every time a reward is granted or reversed.",
          properties: ["UserFeedbackEnvelope: user notification payload"],
          relationships: ["Consumed by frontend/src/editor-definition/GamificationFeedbackCenter.tsx (toasts + persistent activity panel)"],
          cardinality: "1 ledger change ↔ 1 UserFeedbackEnvelope",
          io: { inputs: "New ledger entry", outputs: "Notification visible in the frontend" },
          lifecycle: "Delivered live to the workbench if the session is active.",
          example: "A '+10 points for fixing a validation!' toast after applying a quick fix.",
          evidence: [{ path: "frontend/src/editor-definition/GamificationFeedbackCenter.tsx", line: 1, label: "GamificationFeedbackCenter" }]
        }
      ],
      authoring: {
        summary: "Gamification has 6 views over the same model: Guided, Form, Structure tree, Diagram, Source and 'Runtime & ledger'. Evidence rules are a single flexible form (not a separate tab per reward type): one award rule can simultaneously grant points, advance a challenge, unlock an achievement AND grant a direct reward. Catalog definitions (points/levels/achievements/challenges/rewards) are their own forms, separate from rules.",
        elements: [
          { element: "Evidence rule (condition → reward)", requiredness: "REPEATABLE", modes: [
            { mode: "Single flexible form", detail: "Verified event (never a button click) + optional condition (path + operator + expected value) + Award/Penalty effect toggle: Award reveals simultaneous optional achievement/challenge/reward fields; Penalty only affects points. Repeat policy (once/once-per-event/unlimited/cooldown/daily-limit)." },
            { mode: "Diagram", detail: "Palette with 'Evidence rules' and 'Progression' (points/level/achievement/challenge) plus rewards; drag and connect evidence→rule→points/achievement/challenge/reward, level→point type, etc." },
            { mode: "Guided wizard", detail: "Catalog (name + point types) → progression and rewards (levels/achievements/challenges/rewards) → evidence rules → architecture → review/save." }
          ]},
          { element: "Catalog definitions (Point/Level/Achievement/Challenge/Reward)", requiredness: "OPTIONAL", modes: [
            { mode: "Dedicated per-kind form", detail: "Each with its own fields (e.g. Achievement: name/ID/description/optional reward/'hidden until earned'; Reward: name/ID/description + an arbitrary JSON payload — no icon/image picker, text/JSON only)." }
          ]},
          { element: "Ledger inspection (per-actor history)", requiredness: "MANDATORY", modes: [
            { mode: "'Runtime & ledger' view", detail: "Per-actor cards (points per type, achievements, rewards), a list of already-issued user feedback notifications, and a raw ledger table (event/rule/actor/effect/points/status ACTIVE·REJECTED·COMPENSATED/reason) with Refresh and Reset buttons." }
          ]}
        ]
      },
      level3: "Gamification subscribes broadly to the event bus and filters internally per rule (eventTypeId + condition), rather than subscribing narrowly per type. State (points/achievements/level) and the audit ledger are persistent and per-actor, ETag-protected. An explicit compensation mechanism reverses ledger entries when the action that caused them is undone — e.g. undoing a quick fix or reopening an already-fixed violation.",
      level4: "GamificationRuntimeService.subscribe() listens to the whole bus; for each CanonicalEvent, it checks whether any rule matches (eventTypeId + conditionExpressionId); on a match, it updates GamificationRuntimeState and appends an entry to GamificationLedger; GamificationFeedbackProjector translates that change into a UserFeedbackEnvelope consumed by the frontend. If a reversal event later arrives (core.quickfix.undone, core.validation.violation-reopened), compensate() appends an equivalent reversal entry.",
      level5: {
        incoming: ["Events (primary consumer: direct events and EventFlow-derived events)"],
        outgoing: ["Frontend GamificationFeedbackCenter.tsx (toasts + activity panel)"]
      },
      level6: {
        runtime: "GamificationRuntimeService manages subscription+matching; GamificationFeedbackProjector translates into user notifications.",
        services: ["GamificationRuntimeService", "GamificationFeedbackProjector"],
        apis: ["Internal event bus (consumption); REST for querying per-actor state/ledger"],
        sources: [
          { path: "src/main/java/com/example/editor/gamification", line: 1, label: "gamification package" },
          { path: "frontend/src/editor-definition/GamificationFeedbackCenter.tsx", line: 1, label: "GamificationFeedbackCenter.tsx" }
        ]
      }
    }
  },

  integration: {
    archComponentId: "b_integration",
    kind: "cloud",
    es: {
      level1: {
        what: "Define cómo se entrega una tecnología/toolchain externa para un proyecto HYDRA (perfil de integración) y cómo se sincroniza (round-trip) con artefactos nativos de esa tecnología.",
        value: "Permite que HYDRA conviva con un toolchain externo (p. ej. Eclipse/Langium/Xtext) sin perder la fuente de verdad canónica.",
        inputs: ["IntegrationProfileModel (target, modo de entrega, artefactos requeridos)", "Artefactos nativos existentes de la tecnología destino"],
        outputs: ["Artefactos generados para el toolchain", "RoundTripManifest con el mapeo elemento↔artefacto nativo"]
      },
      level2: [
        {
          name: "IntegrationProfileModel",
          purpose: "Describe el objetivo de integración: tecnología destino, modo de entrega, artefactos y mapeos requeridos.",
          whenUsed: "Al conectar un proyecto HYDRA con un toolchain/tecnología externa concreta.",
          properties: ["target, deliveryMode, features", "artifacts[], mappings[]", "injectionPolicy, requiredCapabilities"],
          relationships: ["Gobierna qué TechnologyWorkflowStage son aplicables para ese target"],
          cardinality: "1 proyecto ↔ 0..N IntegrationProfileModel (uno por tecnología destino)",
          io: { inputs: "Selección de tecnología + artefactos disponibles", outputs: "Flujo de 9 etapas configurado" },
          lifecycle: "Determina qué capacidades (requiredCapabilities) deben estar activas en el proyecto (ver Project/ProjectCapability).",
          example: "Perfil para generar un editor Langium standalone a partir de una gramática HYDRA.",
          evidence: [{ path: "src/main/java/com/example/editor/integration", line: 1, label: "IntegrationProfileModel" }]
        },
        {
          name: "TechnologyWorkflowStage (9 etapas)",
          purpose: "Secuencia fija de etapas para entregar una tecnología: configurar, analizar, generar, verificar, previsualizar, aplicar, ejecutar, sincronizar, exportar.",
          whenUsed: "Siempre que se ejecuta un flujo de entrega de tecnología completo.",
          properties: ["CONFIGURE → ANALYZE → GENERATE → VERIFY → PREVIEW → APPLY → EXECUTE → ROUND_TRIP → EXPORT"],
          relationships: ["ROUND_TRIP opera sobre RoundTripManifest/RoundTripElementMapping"],
          cardinality: "1 IntegrationProfileModel ↔ 1 recorrido de las 9 etapas (algunas pueden omitirse según el perfil)",
          io: { inputs: "Modelo HYDRA + perfil de integración", outputs: "Artefactos nativos entregados y, opcionalmente, sincronizados" },
          lifecycle: "Cada etapa puede fallar y detener el flujo antes de EXECUTE/APPLY (verificación explícita antes de aplicar cambios).",
          example: "GENERATE produce el proyecto Langium; VERIFY lo compila; PREVIEW muestra el diff antes de APPLY.",
          evidence: [{ path: "src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadiness.java", line: 1, label: "TechnologyWorkflowStage" }]
        },
        {
          name: "RoundTripManifest / RoundTripElementMapping / ElementConflictDecision",
          purpose: "Mapea cada elemento canónico a un artefacto nativo y clasifica/resuelve conflictos entre ambos lados.",
          whenUsed: "En la etapa ROUND_TRIP, cuando el artefacto nativo pudo cambiar fuera de HYDRA.",
          properties: ["RoundTripElementMapping: canonicalDocumentId, artifactKind, nativePath, sourceElementIds, generatedSha256, ownership", "Clasificación de conflicto: ADD/REGENERATE/UNCHANGED/NATIVE_DELETED/ORPHAN_NATIVE/MANUAL_CONFLICT", "ElementConflictDecision: KEEP_HYDRA / MERGE_SAFE"],
          relationships: ["Con journal de backup/rollback explícito si la decisión se revierte"],
          cardinality: "1 RoundTripManifest ↔ N RoundTripElementMapping; 1 conflicto ↔ 1 ElementConflictDecision",
          io: { inputs: "Estado actual del artefacto nativo + manifiesto previo", outputs: "Manifiesto actualizado + decisiones de conflicto aplicadas" },
          lifecycle: "Es manual y basado en hash de fichero (generatedSha256), one-directional en la preservación: no es una sincronización bidireccional automática como la de Transformation.",
          example: "El fichero .langium se editó manualmente fuera de HYDRA: se detecta MANUAL_CONFLICT y el usuario elige KEEP_HYDRA o MERGE_SAFE.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/roundtrip", line: 1, label: "RoundTripManifest" }]
        }
      ],
      authoring: {
        summary: "Integración es un híbrido de verdad: un asistente guiado de 6 pasos, un formulario completo de una sola página y un árbol de estructura con panel de propiedades — los tres intercambiables en cualquier momento sobre el mismo perfil, con un panel de madurez de las 9 etapas embebido dentro del propio asistente.",
        elements: [
          { element: "IntegrationProfileModel", requiredness: "MANDATORY", modes: [
            { mode: "Asistente guiado (6 pasos)", detail: "identidad → destino (tecnología, modo de entrega, perfil recomendado, presets de arranque rápido) → capacidades (casillas de características + documentos de entrada 'automáticos') → mapeos → verificar (analizar + guardar) → entrega (compilar/verificar/previsualizar/aplicar, cada botón bloqueado según el estado de preparación)." },
            { mode: "Formulario completo ('Definición')", detail: "Todos los campos en una sola pantalla, incluido el informe de compatibilidad completo con diagnóstico 'por qué / cómo resolver / alternativa' por artefacto." },
            { mode: "Árbol de estructura + panel", detail: "Maestro/detalle: seleccionar perfil/destino/inyección/características/artefacto/mapeo en el árbol abre su panel de propiedades; el nodo 'Vista previa de cambios' muestra la lista de ficheros que cambiarían." }
          ]},
          { element: "Las 9 etapas del flujo de entrega", requiredness: "CONDITIONAL", modes: [
            { mode: "Tarjetas de madurez/preparación", detail: "Una tarjeta de solo lectura por etapa (CONFIGURE…EXPORT) con nivel de soporte (completo/parcial/externo/no-soportado) y, si hay perfil guardado, preparación (listo/listo-con-avisos/bloqueado) — embebida dentro del asistente, no una pantalla aparte." }
          ]}
        ]
      },
      level3: "Un IntegrationProfileModel define el objetivo (tecnología + modo de entrega) y las capacidades de proyecto requeridas. La entrega real recorre 9 etapas fijas (CONFIGURE→...→EXPORT), de las cuales ROUND_TRIP es la única con estado persistente propio (RoundTripManifest), mapeando cada elemento canónico a un artefacto nativo y registrando su hash para detectar cambios externos. Los conflictos se clasifican automáticamente en 6 categorías y se resuelven con una decisión explícita del usuario (KEEP_HYDRA/MERGE_SAFE), nunca de forma silenciosa.",
      level4: "El flujo típico recorre CONFIGURE (elegir perfil) → ANALYZE (inspeccionar artefactos existentes) → GENERATE (producir artefactos nativos) → VERIFY (comprobar que compilan/son válidos) → PREVIEW (mostrar diff, nunca persistido) → APPLY (persistir, solo si la previsualización es aplicable) → EXECUTE (ejecutar el toolchain) → ROUND_TRIP (detectar y resolver conflictos con cambios nativos externos) → EXPORT. El diseño preview/apply es deliberadamente de dos fases: nada se persiste hasta APPLY.",
      level5: {
        incoming: ["Transformation (ejecución vía toolchain nativo para paridad direccional)", "Project (requiredCapabilities debe estar activo en el proyecto)"],
        outgoing: ["Recovery (un proyecto externo recuperado puede, a su vez, alimentar un nuevo perfil de integración)", "Publication (artefactos generados pueden alimentar el empaquetado/distribución)"]
      },
      level6: {
        runtime: "TechnologyDeliveryReadinessService coordina el recorrido de las 9 etapas; RoundTripManifest persiste el estado de sincronización.",
        services: ["TechnologyDeliveryReadinessService", "TechnologyDeliveryReadinessController"],
        apis: ["REST de entrega de tecnología (frontend integration-designer / IntegrationTechnologyJourney.tsx)"],
        sources: [
          { path: "src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadiness.java", line: 1, label: "TechnologyWorkflowStage" },
          { path: "src/main/java/com/example/editor/metamodel/roundtrip", line: 1, label: "RoundTripManifest" },
          { path: "frontend/src/integration-designer/IntegrationTechnologyJourney.tsx", line: 1, label: "IntegrationTechnologyJourney (frontend)" }
        ]
      }
    },
    en: {
      level1: {
        what: "Defines how an external technology/toolchain is delivered for a HYDRA project (integration profile) and how it is round-tripped with that technology's native artifacts.",
        value: "Lets HYDRA coexist with an external toolchain (e.g. Eclipse/Langium/Xtext) without losing the canonical source of truth.",
        inputs: ["IntegrationProfileModel (target, delivery mode, required artifacts)", "Existing native artifacts of the target technology"],
        outputs: ["Generated artifacts for the toolchain", "RoundTripManifest with the element↔native-artifact mapping"]
      },
      level2: [
        {
          name: "IntegrationProfileModel",
          purpose: "Describes the integration goal: target technology, delivery mode, required artifacts and mappings.",
          whenUsed: "When connecting a HYDRA project to a specific external toolchain/technology.",
          properties: ["target, deliveryMode, features", "artifacts[], mappings[]", "injectionPolicy, requiredCapabilities"],
          relationships: ["Governs which TechnologyWorkflowStage apply to that target"],
          cardinality: "1 project ↔ 0..N IntegrationProfileModel (one per target technology)",
          io: { inputs: "Technology selection + available artifacts", outputs: "A configured 9-stage workflow" },
          lifecycle: "Determines which capabilities (requiredCapabilities) must be active in the project (see Project/ProjectCapability).",
          example: "A profile to generate a standalone Langium editor from a HYDRA grammar.",
          evidence: [{ path: "src/main/java/com/example/editor/integration", line: 1, label: "IntegrationProfileModel" }]
        },
        {
          name: "TechnologyWorkflowStage (9 stages)",
          purpose: "Fixed sequence of stages to deliver a technology: configure, analyze, generate, verify, preview, apply, execute, round-trip, export.",
          whenUsed: "Whenever a full technology-delivery workflow runs.",
          properties: ["CONFIGURE → ANALYZE → GENERATE → VERIFY → PREVIEW → APPLY → EXECUTE → ROUND_TRIP → EXPORT"],
          relationships: ["ROUND_TRIP operates over RoundTripManifest/RoundTripElementMapping"],
          cardinality: "1 IntegrationProfileModel ↔ 1 pass through the 9 stages (some may be skipped depending on the profile)",
          io: { inputs: "HYDRA model + integration profile", outputs: "Delivered, and optionally synchronized, native artifacts" },
          lifecycle: "Any stage can fail and halt the flow before EXECUTE/APPLY (explicit verification before applying changes).",
          example: "GENERATE produces the Langium project; VERIFY compiles it; PREVIEW shows the diff before APPLY.",
          evidence: [{ path: "src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadiness.java", line: 1, label: "TechnologyWorkflowStage" }]
        },
        {
          name: "RoundTripManifest / RoundTripElementMapping / ElementConflictDecision",
          purpose: "Maps each canonical element to a native artifact and classifies/resolves conflicts between both sides.",
          whenUsed: "During the ROUND_TRIP stage, when the native artifact may have changed outside HYDRA.",
          properties: ["RoundTripElementMapping: canonicalDocumentId, artifactKind, nativePath, sourceElementIds, generatedSha256, ownership", "Conflict classification: ADD/REGENERATE/UNCHANGED/NATIVE_DELETED/ORPHAN_NATIVE/MANUAL_CONFLICT", "ElementConflictDecision: KEEP_HYDRA / MERGE_SAFE"],
          relationships: ["Backed by an explicit backup/rollback journal if a decision is reverted"],
          cardinality: "1 RoundTripManifest ↔ N RoundTripElementMapping; 1 conflict ↔ 1 ElementConflictDecision",
          io: { inputs: "Current native artifact state + previous manifest", outputs: "Updated manifest + applied conflict decisions" },
          lifecycle: "Manual and file-hash-based (generatedSha256), one-directional in preservation: not an automatic bidirectional sync like Transformation's.",
          example: "The .langium file was manually edited outside HYDRA: a MANUAL_CONFLICT is detected and the user chooses KEEP_HYDRA or MERGE_SAFE.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/roundtrip", line: 1, label: "RoundTripManifest" }]
        }
      ],
      authoring: {
        summary: "Integration is a genuine hybrid: a 6-step guided wizard, a full single-page form, and a structure tree with a properties panel — all three interchangeable at any time over the same profile, with a 9-stage maturity dashboard embedded right inside the wizard itself.",
        elements: [
          { element: "IntegrationProfileModel", requiredness: "MANDATORY", modes: [
            { mode: "Guided wizard (6 steps)", detail: "identity → target (technology, delivery mode, recommended profile, quick-start presets) → capabilities (feature checkboxes + 'automatic' input documents) → mappings → verify (analyze + save) → delivery (compile/verify/preview/apply, each button gated by readiness state)." },
            { mode: "Full form ('Definition')", detail: "Every field on one screen, including the full compatibility report with per-artifact 'why / how to resolve / alternative' advice." },
            { mode: "Structure tree + panel", detail: "Master/detail: selecting profile/target/injection/features/artifact/mapping in the tree opens its properties panel; the 'Change preview' node shows the list of files that would change." }
          ]},
          { element: "The 9 delivery-workflow stages", requiredness: "CONDITIONAL", modes: [
            { mode: "Maturity/readiness cards", detail: "One read-only card per stage (CONFIGURE…EXPORT) with support level (full/partial/external/unsupported) and, once a profile is saved, readiness (ready/ready-with-warnings/blocked) — embedded inside the wizard, not a separate screen." }
          ]}
        ]
      },
      level3: "An IntegrationProfileModel defines the goal (technology + delivery mode) and the required project capabilities. The actual delivery runs through 9 fixed stages (CONFIGURE→...→EXPORT), of which ROUND_TRIP is the only one with its own persistent state (RoundTripManifest), mapping each canonical element to a native artifact and recording its hash to detect external changes. Conflicts are automatically classified into 6 categories and resolved with an explicit user decision (KEEP_HYDRA/MERGE_SAFE), never silently.",
      level4: "The typical flow runs CONFIGURE (choose profile) → ANALYZE (inspect existing artifacts) → GENERATE (produce native artifacts) → VERIFY (check they compile/are valid) → PREVIEW (show diff, never persisted) → APPLY (persist, only if the preview is applicable) → EXECUTE (run the toolchain) → ROUND_TRIP (detect and resolve conflicts with external native changes) → EXPORT. The preview/apply design is deliberately two-phase: nothing is persisted until APPLY.",
      level5: {
        incoming: ["Transformation (native-toolchain execution for directional parity)", "Project (requiredCapabilities must be active on the project)"],
        outgoing: ["Recovery (a recovered external project can in turn feed a new integration profile)", "Publication (generated artifacts can feed packaging/distribution)"]
      },
      level6: {
        runtime: "TechnologyDeliveryReadinessService coordinates the 9-stage pass; RoundTripManifest persists synchronization state.",
        services: ["TechnologyDeliveryReadinessService", "TechnologyDeliveryReadinessController"],
        apis: ["REST for technology delivery (frontend integration-designer / IntegrationTechnologyJourney.tsx)"],
        sources: [
          { path: "src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadiness.java", line: 1, label: "TechnologyWorkflowStage" },
          { path: "src/main/java/com/example/editor/metamodel/roundtrip", line: 1, label: "RoundTripManifest" },
          { path: "frontend/src/integration-designer/IntegrationTechnologyJourney.tsx", line: 1, label: "IntegrationTechnologyJourney (frontend)" }
        ]
      }
    }
  },

  recovery: {
    archComponentId: "b_recovery",
    kind: "external",
    es: {
      level1: {
        what: "Recupera metamodelo, capas de editor y reglas de validación a partir de un proyecto/update-site externo (p. ej. un proyecto Eclipse/EMF existente).",
        value: "Permite incorporar artefactos ya existentes fuera de HYDRA sin reescribirlos a mano, con materialización en dos fases y reversible.",
        inputs: ["Update-site o proyecto externo (plugins, genmodel, bytecode EMF, código fuente)"],
        outputs: ["MetaModel + capas (paleta/representaciones/mapeos semánticos) + reglas de validación recuperadas", "Paquete de cambio neutral (si es solo importación representable)"]
      },
      level2: [
        {
          name: "Métodos de descubrimiento (6, por confianza decreciente)",
          purpose: "Determinar el origen más fiable disponible para reconstruir un metamodelo desde un proyecto externo.",
          whenUsed: "Al iniciar la recuperación de un update-site/proyecto externo.",
          properties: ["DIRECT_METAMODEL → GENMODEL → PLUGIN_REGISTRY → EMF_BYTECODE → SOURCE_INFERENCE → CODE_INFERENCE (orden de confianza decreciente)"],
          relationships: ["El método usado queda registrado como procedencia (provenance) del metamodelo recuperado"],
          cardinality: "1 recuperación ↔ 1 método de descubrimiento elegido (el de mayor confianza disponible)",
          io: { inputs: "Contenido del proyecto externo", outputs: "MetaModel reconstruido + nivel de confianza" },
          lifecycle: "Si el método de mayor confianza no está disponible, se cae al siguiente en la lista.",
          example: "Un proyecto con genmodel disponible usa GENMODEL en vez de recurrir a CODE_INFERENCE.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "Discovery methods (6, por confianza)" }]
        },
        {
          name: "Recuperación de capas y editor semántico",
          purpose: "Además del metamodelo, recupera paleta, representaciones y mapeos semánticos, y detecta extension points de Eclipse marcados representable=true/false.",
          whenUsed: "Cuando el proyecto externo también define un editor/notación que se quiere preservar.",
          properties: ["Recuperación de capas: palette/representations/semantic-mappings", "Recuperación de editor semántico: extension points representable=true/false"],
          relationships: ["Alimenta Model Editor con un EditorDefinitionModel candidato, no uno final — requiere revisión"],
          cardinality: "1 proyecto externo ↔ 0..N capas/editor recuperados",
          io: { inputs: "Extension points y configuración del IDE externo", outputs: "EditorDefinitionModel candidato" },
          lifecycle: "Los elementos marcados representable=false se excluyen explícitamente de la recuperación de editor.",
          example: "Un editor GMF existente aporta su paleta de herramientas como candidato de ToolPaletteDefinition.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "Layer/semantic-editor recovery" }]
        },
        {
          name: "ExternalProjectSemanticMaterializationService (materialización en dos fases)",
          purpose: "Preview (nunca persistido) → solo si persist()==true y preview.applicable(), se vuelve real (persist) con posibilidad de rollback.",
          whenUsed: "Al aplicar de verdad una recuperación de metamodelo + reglas de validación recuperadas.",
          properties: ["preview: nunca se persiste", "persist: solo si aplicable; produce documentos con ETag", "rollback: limpieza compensatoria dirigida exactamente a los documentos creados en esa ejecución (no snapshot de bytes)"],
          relationships: ["También recupera reglas de validación reales (EVL/OCL/Sirius/Xtext @Check, etc.), marcadas review-required/disabled cuando no son traducibles de forma segura"],
          cardinality: "1 ejecución de materialización ↔ 1 preview ↔ 0..1 persist ↔ 0..1 rollback",
          io: { inputs: "Candidatos de metamodelo/reglas descubiertos", outputs: "Documentos canónicos persistidos (metamodelo + validación)" },
          lifecycle: "El rollback deshace limpiamente solo lo creado por esa ejecución, identificado por sus ETags.",
          example: "Se recuperan 12 reglas OCL; 9 se traducen automáticamente y 3 quedan marcadas review-required.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java", line: 1, label: "Materialization preview/persist/rollback" }]
        },
        {
          name: "RepresentableImportController (importación representable)",
          purpose: "apply() registra un 'paquete de cambio neutral' en JSON — NO crea directamente un metamodelo/modelo.",
          whenUsed: "Cuando la importación es de un recurso representable genérico, no de un proyecto completo a materializar.",
          properties: ["Salida: registro de paquete de cambio neutral (JSON)"],
          relationships: ["Distinto del camino de materialización completa de ExternalProjectSemanticMaterializationService"],
          cardinality: "1 importación representable ↔ 1 paquete de cambio neutral",
          io: { inputs: "Recurso externo representable", outputs: "Registro JSON de cambio, no una entidad de dominio directa" },
          lifecycle: "Requiere un paso posterior para convertirse en metamodelo/modelo real, si corresponde.",
          example: "Importar un recurso representable que luego se revisa manualmente antes de materializarlo.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "RepresentableImportController" }]
        }
      ],
      authoring: {
        summary: "Recuperación Externa es, con diferencia, la capacidad con el workspace más rico: un asistente guiado de 5 pasos más un 'espacio de trabajo avanzado' con 13 pestañas agrupadas en 'Entender / Revisar / Construir'. La puerta de calidad de 5 comprobaciones de publicación verificada vive, de hecho, dentro de su pestaña de Gobernanza, no en el Centro de Publicación.",
        elements: [
          { element: "Origen del proyecto externo", requiredness: "ALTERNATIVE", modes: [
            { mode: "Asistente guiado (5 pasos)", detail: "origen (archivo/carpeta local, repositorio Git/GitHub/GitLab, o p2 remoto) → plan de recuperación (qué capas neutrales recuperar + estricto/sobrescribir/persistir-paquete) → analizar → revisar → importar." }
          ]},
          { element: "Candidatos de metamodelo descubiertos", requiredness: "ALTERNATIVE", modes: [
            { mode: "Lista de radio por nivel de confianza", detail: "Cada candidato muestra 'Nivel {n} · {método}' (5 niveles de escalada: Ecore original → GenModel → registro de plugins → bytecode → inferencia de código) con una insignia de confianza (EXACTA/RECUPERADA/DERIVADA/INFERIDA) y una justificación." }
          ]},
          { element: "Evidencia/procedencia por elemento", requiredness: "MANDATORY", modes: [
            { mode: "Panel de evidencia filtrable", detail: "Filtro por nivel de confianza, origen del paquete, archivo fuente, elemento original, método de recuperación y advertencias — la orientación de revisión se basa en el nivel de confianza, no en una bandera booleana 'requiere revisión' separada." }
          ]},
          { element: "Reconciliación de conflictos entre candidatos", requiredness: "CONDITIONAL", modes: [
            { mode: "Desplegable 'evidencia a conservar' por conflicto", detail: "Distingue conflictos bloqueantes de los resueltos automáticamente de forma segura; botón 'Construir vista previa reconciliada'." }
          ]},
          { element: "Materialización (preview/persist/rollback)", requiredness: "MANDATORY", modes: [
            { mode: "Tres botones directos", detail: "'Previsualizar materialización' (nunca persiste) / 'Importar y materializar capas seleccionadas' (persiste) / 'Deshacer materialización' (solo visible si el resultado indica que es posible)." }
          ]},
          { element: "Puerta de publicación verificada (5 comprobaciones)", requiredness: "OPTIONAL", modes: [
            { mode: "Pestaña 'Gobernanza' del espacio avanzado", detail: "5 bloques secuenciales (fusión semántica profunda, espejo p2 offline, banco de pruebas de adaptador, regresión de rendimiento, puerta de compatibilidad/publicación) con casillas para exigir espejo/banco/rendimiento, resultado por comprobación y una atestación HTML descargable con SHA-256 — ubicada aquí, no en el Centro de Publicación." }
          ]}
        ]
      },
      level3: "La recuperación encadena: (1) descubrimiento del metamodelo por el método de mayor confianza disponible de 6 posibles; (2) recuperación opcional de capas de editor y reglas de validación reales del proyecto externo; (3) materialización en dos fases (preview nunca persistido, persist solo si aplicable, con rollback dirigido). RepresentableImportController es un camino más ligero y distinto: solo registra un paquete de cambio neutral, sin crear directamente metamodelo/modelo.",
      level4: "El flujo de materialización completa es: descubrir (elige el método de mayor confianza) → recuperar reglas y capas candidatas → preview (sin persistir) → si el usuario confirma y preview.applicable()==true → persist (documentos reales con ETag) → en caso de error o decisión posterior, rollback (limpieza compensatoria dirigida solo a lo creado en esa ejecución, no un snapshot de bytes).",
      level5: {
        incoming: ["Ninguna dependencia de entrada de otras capacidades HYDRA — el origen es siempre externo al sistema"],
        outgoing: ["Metamodel (produce un MetaModel real, no un marcador opaco)", "Validation (recupera reglas reales, marcadas review-required/disabled si no son traducibles con seguridad)", "Integration (un proyecto recuperado puede alimentar un nuevo perfil de integración)"]
      },
      level6: {
        runtime: "ExternalProjectSemanticMaterializationService coordina preview/persist/rollback; los 6 discovery methods viven en el paquete metamodel.importing.",
        services: ["ExternalProjectSemanticMaterializationService", "RepresentableImportController"],
        apis: ["REST de importación/recuperación de proyecto externo"],
        sources: [
          { path: "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java", line: 1, label: "Soporte de importación/recuperación" },
          { path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "Paquete importing — discovery methods" }
        ]
      }
    },
    en: {
      level1: {
        what: "Recovers a metamodel, editor layers and validation rules from an external project/update-site (e.g. an existing Eclipse/EMF project).",
        value: "Lets artifacts that already exist outside HYDRA be brought in without hand-rewriting them, with a reversible, two-phase materialization.",
        inputs: ["External update-site or project (plugins, genmodel, EMF bytecode, source code)"],
        outputs: ["Recovered MetaModel + layers (palette/representations/semantic mappings) + validation rules", "Neutral change package (if it is a representable-only import)"]
      },
      level2: [
        {
          name: "Discovery methods (6, decreasing confidence)",
          purpose: "Determine the most reliable available source to reconstruct a metamodel from an external project.",
          whenUsed: "When starting recovery of an external update-site/project.",
          properties: ["DIRECT_METAMODEL → GENMODEL → PLUGIN_REGISTRY → EMF_BYTECODE → SOURCE_INFERENCE → CODE_INFERENCE (decreasing confidence order)"],
          relationships: ["The method used is recorded as the recovered metamodel's provenance"],
          cardinality: "1 recovery ↔ 1 chosen discovery method (the highest-confidence one available)",
          io: { inputs: "External project content", outputs: "Reconstructed MetaModel + confidence level" },
          lifecycle: "If the highest-confidence method is unavailable, it falls back to the next one in the list.",
          example: "A project with a genmodel available uses GENMODEL instead of falling back to CODE_INFERENCE.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "Discovery methods (6, by confidence)" }]
        },
        {
          name: "Layer and semantic-editor recovery",
          purpose: "Beyond the metamodel, recovers palette, representations and semantic mappings, and detects Eclipse extension points tagged representable=true/false.",
          whenUsed: "When the external project also defines an editor/notation that should be preserved.",
          properties: ["Layer recovery: palette/representations/semantic-mappings", "Semantic-editor recovery: extension points representable=true/false"],
          relationships: ["Feeds Model Editor with a candidate EditorDefinitionModel, not a final one — review required"],
          cardinality: "1 external project ↔ 0..N recovered layers/editor",
          io: { inputs: "External IDE's extension points and configuration", outputs: "Candidate EditorDefinitionModel" },
          lifecycle: "Elements tagged representable=false are explicitly excluded from editor recovery.",
          example: "An existing GMF editor contributes its tool palette as a ToolPaletteDefinition candidate.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "Layer/semantic-editor recovery" }]
        },
        {
          name: "ExternalProjectSemanticMaterializationService (two-phase materialization)",
          purpose: "Preview (never persisted) → only if persist()==true and preview.applicable(), it becomes real (persist), with rollback available.",
          whenUsed: "When actually applying a recovered metamodel + recovered validation rules.",
          properties: ["preview: never persisted", "persist: only if applicable; produces ETag-bearing documents", "rollback: compensating cleanup targeted exactly at the documents created by that run (not a byte snapshot)"],
          relationships: ["Also recovers real validation rules (EVL/OCL/Sirius/Xtext @Check etc.), tagged review-required/disabled when not safely translatable"],
          cardinality: "1 materialization run ↔ 1 preview ↔ 0..1 persist ↔ 0..1 rollback",
          io: { inputs: "Discovered metamodel/rule candidates", outputs: "Persisted canonical documents (metamodel + validation)" },
          lifecycle: "Rollback cleanly undoes only what that run created, identified by its ETags.",
          example: "12 OCL rules are recovered; 9 auto-translate and 3 are tagged review-required.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java", line: 1, label: "Materialization preview/persist/rollback" }]
        },
        {
          name: "RepresentableImportController (representable import)",
          purpose: "apply() registers a 'neutral change package' in JSON — it does NOT directly create a metamodel/model.",
          whenUsed: "When the import is of a generic representable resource, not a full project to materialize.",
          properties: ["Output: neutral change-package record (JSON)"],
          relationships: ["Distinct from ExternalProjectSemanticMaterializationService's full materialization path"],
          cardinality: "1 representable import ↔ 1 neutral change package",
          io: { inputs: "External representable resource", outputs: "JSON change record, not a direct domain entity" },
          lifecycle: "Requires a later step to become a real metamodel/model, if applicable.",
          example: "Importing a representable resource that is later manually reviewed before materializing it.",
          evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "RepresentableImportController" }]
        }
      ],
      authoring: {
        summary: "External Recovery is by far the richest-workspace capability: a 5-step guided wizard plus an 'advanced workspace' with 13 tabs grouped into 'Understand / Review / Build'. The 5-check verified-publication quality gate actually lives inside its Governance tab, not in the Publication Center.",
        elements: [
          { element: "External project source", requiredness: "ALTERNATIVE", modes: [
            { mode: "Guided wizard (5 steps)", detail: "source (local archive/directory, Git/GitHub/GitLab repo, or remote p2) → recovery plan (which neutral layers to recover + strict/overwrite/persist-package) → analyze → review → import." }
          ]},
          { element: "Discovered metamodel candidates", requiredness: "ALTERNATIVE", modes: [
            { mode: "Confidence-ranked radio list", detail: "Each candidate shows 'Level {n} · {method}' (5 escalation tiers: original Ecore → GenModel → plugin registry → bytecode → code inference) with a confidence badge (EXACT/RECOVERED/DERIVED/INFERRED) and a rationale." }
          ]},
          { element: "Per-element evidence/provenance", requiredness: "MANDATORY", modes: [
            { mode: "Filterable evidence panel", detail: "Filter by confidence level, origin bundle, source file, original element, recovery method and warnings — review guidance is confidence-level-driven, not a separate boolean 'review-required' flag." }
          ]},
          { element: "Conflict reconciliation across candidates", requiredness: "CONDITIONAL", modes: [
            { mode: "Per-conflict 'evidence to keep' dropdown", detail: "Distinguishes blocking conflicts from safely auto-resolved ones; a 'Build reconciled preview' button." }
          ]},
          { element: "Materialization (preview/persist/rollback)", requiredness: "MANDATORY", modes: [
            { mode: "Three direct buttons", detail: "'Preview materialization' (never persists) / 'Import and materialize selected layers' (persists) / 'Rollback materialization' (only shown when the result says it's possible)." }
          ]},
          { element: "Verified-publication gate (5 checks)", requiredness: "OPTIONAL", modes: [
            { mode: "Advanced workspace's 'Governance' tab", detail: "5 sequential blocks (deep semantic merge, offline p2 mirror, adapter test bench, performance regression, compatibility/publication gate) with checkboxes requiring mirror/bench/performance, per-check pass/fail, and a downloadable SHA-256-stamped HTML attestation — located here, not in the Publication Center." }
          ]}
        ]
      },
      level3: "Recovery chains: (1) metamodel discovery by the highest-confidence of 6 available methods; (2) optional recovery of editor layers and real validation rules from the external project; (3) two-phase materialization (preview never persisted, persist only if applicable, with targeted rollback). RepresentableImportController is a lighter, distinct path: it only records a neutral change package, without directly creating a metamodel/model.",
      level4: "The full materialization flow is: discover (pick the highest-confidence method) → recover candidate rules and layers → preview (not persisted) → if the user confirms and preview.applicable()==true → persist (real, ETag-bearing documents) → on error or later decision, rollback (compensating cleanup targeted only at what that run created, not a byte snapshot).",
      level5: {
        incoming: ["No incoming dependency from other HYDRA capabilities — the source is always external to the system"],
        outgoing: ["Metamodel (produces a real MetaModel, not an opaque marker)", "Validation (recovers real rules, tagged review-required/disabled when not safely translatable)", "Integration (a recovered project can feed a new integration profile)"]
      },
      level6: {
        runtime: "ExternalProjectSemanticMaterializationService coordinates preview/persist/rollback; the 6 discovery methods live in the metamodel.importing package.",
        services: ["ExternalProjectSemanticMaterializationService", "RepresentableImportController"],
        apis: ["REST for external project import/recovery"],
        sources: [
          { path: "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java", line: 1, label: "Import/recovery support" },
          { path: "src/main/java/com/example/editor/metamodel/importing", line: 1, label: "importing package — discovery methods" }
        ]
      }
    }
  },

  publication: {
    archComponentId: "b_publication",
    kind: "cloud",
    es: {
      level1: {
        what: "Genera, valida y empaqueta un editor/proyecto para su distribución (editor standalone, proyecto generado, o publicación verificada a través de una puerta de calidad).",
        value: "Convierte un metamodelo + editor + modelo en algo ejecutable y distribuible, con distintos niveles de rigor según la vía elegida.",
        inputs: ["Metamodelo + modelo + target de generación seleccionados por el usuario"],
        outputs: ["Proyecto generado abrible/lanzable", "Editor standalone autocontenido", "Veredicto de la puerta de publicación verificada (si se usa esa vía)"]
      },
      level2: [
        {
          name: "ModelCodeGenerationService",
          purpose: "Genera código a partir de un metamodelo y un modelo concretos, para un target explícito elegido por el usuario.",
          whenUsed: "Cuando se quiere producir artefactos de código reales a partir del modelo.",
          properties: ["Requiere explícitamente metamodelId + modelId + targetId", "NO existe una entrada de 'sesión textual' en el paquete de generación de código"],
          relationships: ["El modelo de entrada puede proceder de Model Editor o de un M2M de Transformation"],
          cardinality: "1 generación ↔ 1 metamodelo + 1 modelo + 1 target",
          io: { inputs: "Metamodelo + modelo + target", outputs: "Código/artefactos generados" },
          lifecycle: "No pasa automáticamente por la puerta de release (HydraReleaseGateService) salvo que se use la vía de publicación verificada.",
          example: "Generar el backend de un editor standalone a partir del modelo de dominio.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "ModelCodeGenerationService" }]
        },
        {
          name: "GeneratedProjectLifecycleService",
          purpose: "Gestiona el ciclo de vida de un proyecto generado: registrar, generar, validar, abrir, lanzar.",
          whenUsed: "Tras generar un proyecto, para poder abrirlo/ejecutarlo desde HYDRA.",
          properties: ["Etapas exactas: register → GENERATE → VALIDATE → OPEN → LAUNCH (opcional)", "Exactamente 5 perfiles de runtime: standalone-web, langium, xtext, glsp, besser-sdk"],
          relationships: ["VALIDATE aquí es validación del propio proyecto generado (compilación/estructura), no la Validación de modelos de dominio"],
          cardinality: "1 proyecto generado ↔ 1 perfil de runtime de los 5 soportados",
          io: { inputs: "Artefactos generados", outputs: "Proyecto abierto/lanzado en su runtime" },
          lifecycle: "LAUNCH es opcional: un proyecto puede quedar solo registrado/validado sin lanzarse.",
          example: "Un proyecto generado con perfil 'glsp' se valida y se lanza como editor web gráfico.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "GeneratedProjectLifecycleService" }]
        },
        {
          name: "StandaloneEditorGeneratorService",
          purpose: "Empaqueta un editor web autocontenido y ejecutable para UNA combinación metamodelo/gramática + editor-definition.",
          whenUsed: "Cuando se quiere distribuir un editor ya listo para usar, sin el resto del entorno HYDRA.",
          properties: ["Combina exactamente un metamodelo/gramática + un editor-definition", "designerIncluded: false (el editor distribuido no incluye el diseñador de HYDRA)"],
          relationships: ["Consume EditorDefinitionModel + MetaModel ya validados"],
          cardinality: "1 combinación metamodelo+editor ↔ 1 paquete standalone",
          io: { inputs: "Metamodelo/gramática + editor-definition", outputs: "Paquete de editor web ejecutable, sin el diseñador" },
          lifecycle: "No requiere pasar por la puerta de release.",
          example: "Distribuir un editor de diagramas de proceso como aplicación web independiente para usuarios finales.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "StandaloneEditorGeneratorService" }]
        },
        {
          name: "HydraReleaseGateService (publicación verificada, 5 checks)",
          purpose: "Puerta de calidad opcional y más estricta: agrega exactamente 5 comprobaciones antes de certificar una publicación.",
          whenUsed: "Solo en la vía de 'publicación verificada' — el camino de proyecto generado/distribución simple NO la exige.",
          properties: ["5 checks exactos: PACKAGE, SEMANTIC_MERGE, P2_MIRROR+license, ADAPTER_BENCH/tests, PERFORMANCE/budget", "NO incluye ningún check literal de seguridad/CVE", "NO incluye el estado de validación del modelo/metamodelo"],
          relationships: ["Es independiente de Validation: una publicación verificada puede aprobar los 5 checks aunque el modelo tenga violaciones de validación activas"],
          cardinality: "1 publicación verificada ↔ evaluación de los 5 checks",
          io: { inputs: "Paquete candidato a publicar", outputs: "Veredicto agregado (apto / no apto) con detalle por check" },
          lifecycle: "evaluate() agrega los 5 resultados; cualquier check no superado bloquea la certificación de esa vía (pero no bloquea la generación simple).",
          example: "Un paquete pasa PACKAGE/SEMANTIC_MERGE/P2_MIRROR/ADAPTER_BENCH pero falla PERFORMANCE por exceder el presupuesto de tiempo de arranque.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "HydraReleaseGateService.evaluate() — 5 checks" }]
        }
      ],
      authoring: {
        summary: "Publicación no es una única pantalla sino varias superficies especializadas: un Centro de Publicación propio (release/distribución), un diálogo modal de generación de código invocado desde dentro del editor de modelos (no una página independiente), y un Centro de Distribución para empaquetado standalone (PWA/Tauri). El ciclo de vida de proyecto generado (registrar→generar→validar→abrir→lanzar) tiene su PROPIA capacidad dedicada — ver 'Ciclo de Vida de Proyectos Generados'.",
        elements: [
          { element: "Generación de código desde un modelo (ModelCodeGenerationService)", requiredness: "CONDITIONAL", modes: [
            { mode: "Diálogo modal contextual", detail: "Se abre desde dentro del Editor de Modelos/Editor Textual, no desde una página de 'elegir destino de generación' propia: dropdown de target/lenguaje, namespace, nombre de artefacto, casilla de trazabilidad, casillas de características opcionales por target, 'Previsualizar código' y 'Generar y descargar'." }
          ]},
          { element: "Publicación de una distribución (release)", requiredness: "MANDATORY", modes: [
            { mode: "Asistente guiado (5 pasos)", detail: "identidad → artefacto/plataforma → canal de entrega/firma → 'Validar release' → 'Revisar y publicar'; o un formulario avanzado de una sola pantalla. Esta validación es un pase/fallo genérico con diagnósticos — NO es la puerta de 5 comprobaciones (esa vive en Recuperación Externa)." },
            { mode: "Comprobador de actualizaciones", detail: "Una tarjeta separada 'Resolver actualización' que consulta si hay una versión más nueva disponible por canal." }
          ]},
          { element: "Empaquetado standalone (PWA/Tauri)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Asistente guiado", detail: "carpeta origen → identidad/versión/asociaciones de archivo/presets de canal → casillas de plataforma destino (PWA/Tauri-Windows/macOS/Linux) → 'Materializar y validar' → 'Empaquetar ZIP' con SHA-256 mostrado." }
          ]}
        ]
      },
      level3: "Hay dos vías de publicación claramente distintas: (1) generación/distribución simple (ModelCodeGenerationService + GeneratedProjectLifecycleService + StandaloneEditorGeneratorService), que NO exige pasar ninguna puerta de calidad adicional; (2) publicación verificada, que además agrega HydraReleaseGateService con 5 checks fijos (empaquetado, fusión semántica, espejo P2+licencia, banco de pruebas de adaptador, presupuesto de rendimiento) — ninguno de los cuales es un check de seguridad/CVE ni del estado de validación del modelo.",
      level4: "Camino simple: generar código (target explícito) → registrar el proyecto → GENERATE → VALIDATE (del propio proyecto, no del modelo de dominio) → OPEN → LAUNCH opcional. Camino verificado: además de lo anterior, se agregan los 5 checks de HydraReleaseGateService.evaluate(); si alguno falla, la publicación verificada se bloquea, aunque la generación simple del mismo proyecto seguiría siendo posible por separado.",
      level5: {
        incoming: ["Metamodel + Model Editor (metamodelo y modelo de entrada)", "Transformation (modelo destino puede alimentar la generación)", "Integration (artefactos de integración pueden empaquetarse junto al proyecto generado)"],
        outgoing: ["Ninguna — es un punto terminal del flujo (entrega/distribución)"]
      },
      level6: {
        runtime: "GeneratedProjectLifecycleService gestiona el ciclo de vida; HydraReleaseGateService.evaluate() agrega los 5 checks de publicación verificada.",
        services: ["ModelCodeGenerationService", "GeneratedProjectLifecycleService", "StandaloneEditorGeneratorService", "HydraReleaseGateService"],
        apis: ["REST de generación/publicación de proyecto"],
        sources: [
          { path: "src/main/java/com/example/editor", line: 1, label: "Generación y publicación — paquetes principales" }
        ]
      }
    },
    en: {
      level1: {
        what: "Generates, validates and packages an editor/project for distribution (standalone editor, generated project, or verified publication through a quality gate).",
        value: "Turns a metamodel + editor + model into something executable and distributable, with different rigor levels depending on the chosen path.",
        inputs: ["Metamodel + model + generation target selected by the user"],
        outputs: ["Openable/launchable generated project", "Self-contained standalone editor", "Verified-publication gate verdict (if that path is used)"]
      },
      level2: [
        {
          name: "ModelCodeGenerationService",
          purpose: "Generates code from a concrete metamodel and model, for an explicit target chosen by the user.",
          whenUsed: "When real code artifacts need to be produced from the model.",
          properties: ["Explicitly requires metamodelId + modelId + targetId", "There is NO 'textual session' input in the code-generation package"],
          relationships: ["The input model can come from Model Editor or from a Transformation M2M"],
          cardinality: "1 generation ↔ 1 metamodel + 1 model + 1 target",
          io: { inputs: "Metamodel + model + target", outputs: "Generated code/artifacts" },
          lifecycle: "Does not automatically go through the release gate (HydraReleaseGateService) unless the verified-publication path is used.",
          example: "Generating a standalone editor's backend from the domain model.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "ModelCodeGenerationService" }]
        },
        {
          name: "GeneratedProjectLifecycleService",
          purpose: "Manages a generated project's lifecycle: register, generate, validate, open, launch.",
          whenUsed: "After generating a project, to open/run it from HYDRA.",
          properties: ["Exact stages: register → GENERATE → VALIDATE → OPEN → LAUNCH (optional)", "Exactly 5 runtime profiles: standalone-web, langium, xtext, glsp, besser-sdk"],
          relationships: ["VALIDATE here is validation of the generated project itself (compilation/structure), not domain-model Validation"],
          cardinality: "1 generated project ↔ 1 of the 5 supported runtime profiles",
          io: { inputs: "Generated artifacts", outputs: "Project opened/launched in its runtime" },
          lifecycle: "LAUNCH is optional: a project can remain only registered/validated without being launched.",
          example: "A project generated with the 'glsp' profile is validated and launched as a graphical web editor.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "GeneratedProjectLifecycleService" }]
        },
        {
          name: "StandaloneEditorGeneratorService",
          purpose: "Packages a self-contained, runnable web editor for ONE metamodel/grammar + editor-definition combination.",
          whenUsed: "To distribute a ready-to-use editor without the rest of the HYDRA environment.",
          properties: ["Combines exactly one metamodel/grammar + one editor-definition", "designerIncluded: false (the distributed editor does not include HYDRA's designer)"],
          relationships: ["Consumes an already-validated EditorDefinitionModel + MetaModel"],
          cardinality: "1 metamodel+editor combination ↔ 1 standalone package",
          io: { inputs: "Metamodel/grammar + editor-definition", outputs: "Runnable web editor package, without the designer" },
          lifecycle: "Does not require going through the release gate.",
          example: "Distributing a process-diagram editor as a standalone web app for end users.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "StandaloneEditorGeneratorService" }]
        },
        {
          name: "HydraReleaseGateService (verified publication, 5 checks)",
          purpose: "Optional, stricter quality gate: aggregates exactly 5 checks before certifying a publication.",
          whenUsed: "Only on the 'verified publication' path — the plain generated-project/distribution path does NOT require it.",
          properties: ["Exactly 5 checks: PACKAGE, SEMANTIC_MERGE, P2_MIRROR+license, ADAPTER_BENCH/tests, PERFORMANCE/budget", "Includes NO literal security/CVE check", "Includes NO model/metamodel validation status"],
          relationships: ["Independent of Validation: a verified publication can pass all 5 checks even if the model has active validation violations"],
          cardinality: "1 verified publication ↔ evaluation of the 5 checks",
          io: { inputs: "Candidate publication package", outputs: "Aggregated verdict (pass/fail) with per-check detail" },
          lifecycle: "evaluate() aggregates the 5 results; any failed check blocks certification on that path (but not plain generation).",
          example: "A package passes PACKAGE/SEMANTIC_MERGE/P2_MIRROR/ADAPTER_BENCH but fails PERFORMANCE for exceeding the startup-time budget.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "HydraReleaseGateService.evaluate() — 5 checks" }]
        }
      ],
      authoring: {
        summary: "Publication is not one screen but several specialized surfaces: its own Publication Center (release/distribution), a code-generation modal dialog invoked from inside the model editor (not a standalone page), and a Distribution Center for standalone (PWA/Tauri) packaging. The generated-project lifecycle (register→generate→validate→open→launch) has its OWN dedicated capability — see 'Generated Project Lifecycle'.",
        elements: [
          { element: "Code generation from a model (ModelCodeGenerationService)", requiredness: "CONDITIONAL", modes: [
            { mode: "Contextual modal dialog", detail: "Opened from inside the Model Editor/Textual Editor, not from its own 'pick a generation target' page: target/language dropdown, namespace, artifact name, traceability checkbox, per-target optional feature checkboxes, 'Preview code' and 'Generate and download'." }
          ]},
          { element: "Publishing a release/distribution", requiredness: "MANDATORY", modes: [
            { mode: "Guided wizard (5 steps)", detail: "identity → artifact/platform → delivery channel/signing → 'Validate release' → 'Review and publish'; or a single-screen advanced form. This validation is a generic pass/fail with diagnostics — NOT the 5-check gate (that lives in External Recovery)." },
            { mode: "Update resolver", detail: "A separate card checking whether a newer version is available per channel." }
          ]},
          { element: "Standalone packaging (PWA/Tauri)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Guided wizard", detail: "source folder → identity/version/file-associations/channel presets → target-platform checkboxes (PWA/Tauri-Windows/macOS/Linux) → 'Materialize and validate' → 'Package ZIP' with SHA-256 shown." }
          ]}
        ]
      },
      level3: "There are two clearly distinct publication paths: (1) plain generation/distribution (ModelCodeGenerationService + GeneratedProjectLifecycleService + StandaloneEditorGeneratorService), which does NOT require passing any extra quality gate; (2) verified publication, which additionally runs HydraReleaseGateService's 5 fixed checks (packaging, semantic merge, P2 mirror+license, adapter test bench, performance budget) — none of which is a security/CVE check nor the model's validation status.",
      level4: "Plain path: generate code (explicit target) → register the project → GENERATE → VALIDATE (of the project itself, not the domain model) → OPEN → optional LAUNCH. Verified path: in addition to the above, HydraReleaseGateService.evaluate()'s 5 checks are run; if any fails, verified publication is blocked, though plain generation of the same project would still be possible separately.",
      level5: {
        incoming: ["Metamodel + Model Editor (input metamodel and model)", "Transformation (target model can feed generation)", "Integration (integration artifacts can be packaged alongside the generated project)"]
        ,
        outgoing: ["None — this is a terminal point of the flow (delivery/distribution)"]
      },
      level6: {
        runtime: "GeneratedProjectLifecycleService manages the lifecycle; HydraReleaseGateService.evaluate() aggregates the 5 verified-publication checks.",
        services: ["ModelCodeGenerationService", "GeneratedProjectLifecycleService", "StandaloneEditorGeneratorService", "HydraReleaseGateService"],
        apis: ["REST for project generation/publication"],
        sources: [
          { path: "src/main/java/com/example/editor", line: 1, label: "Generation and publication — main packages" }
        ]
      }
    }
  },

  project: {
    archComponentId: "b_project",
    kind: "backend",
    es: {
      level1: {
        what: "Contenedor raíz de un proyecto HYDRA: qué capacidades están activas, cuáles son sus artefactos principales, y la auditoría universal de cambios.",
        value: "Organiza y delimita qué puede hacerse dentro de un proyecto concreto, de forma reforzada en el backend (no solo visual en el frontend).",
        inputs: ["Selección de capacidades activas por el usuario/administrador del proyecto"],
        outputs: ["Árbol lógico de artefactos activo/inactivo", "Artefacto principal navegable (Home/preview/generación/ejecución)", "Rastro de auditoría universal de todas las mutaciones"]
      },
      level2: [
        {
          name: "ProjectCapability (24 valores)",
          purpose: "Catálogo cerrado de capacidades que un proyecto puede tener activas o inactivas.",
          whenUsed: "Al configurar qué partes de HYDRA están disponibles para un proyecto concreto.",
          properties: ["Enum con exactamente 24 valores"],
          relationships: ["IntegrationProfileModel.requiredCapabilities exige que ciertas ProjectCapability estén activas antes de permitir una entrega de tecnología"],
          cardinality: "1 proyecto ↔ 0..24 ProjectCapability activas",
          io: { inputs: "Decisión de activación/desactivación", outputs: "Disponibilidad real (no solo visual) de funciones del proyecto" },
          lifecycle: "La activación/desactivación se aplica en el backend: ProjectIndexService recalcula el árbol lógico activo/inactivo.",
          example: "Desactivar la capacidad de Transformación en un proyecto que no la necesita, ocultando y bloqueando realmente ese flujo.",
          evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectCapability (24 valores)" }]
        },
        {
          name: "ProjectIndexService (logicalRoot)",
          purpose: "Calcula en el backend qué grupos del árbol lógico del proyecto están activos según las capacidades habilitadas.",
          whenUsed: "Cada vez que cambian las capacidades activas o se navega el proyecto.",
          properties: ["logicalRoot: árbol de grupos activos/inactivos"],
          relationships: ["La aplicación es server-side; el frontend no puede reactivar una capacidad desactivada solo manipulando la UI"],
          cardinality: "1 proyecto ↔ 1 logicalRoot calculado",
          io: { inputs: "Conjunto de ProjectCapability activas", outputs: "Árbol lógico navegable" },
          lifecycle: "Se recalcula de forma reactiva ante cualquier cambio de capacidades.",
          example: "Al desactivar Gamificación, el grupo correspondiente desaparece del árbol lógico servido al frontend.",
          evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectIndexService.logicalRoot" }]
        },
        {
          name: "ProjectPrimaryArtifact",
          purpose: "Artefacto principal del proyecto (kind, id, relativePath) consumido por Home, previsualización, generación y ejecución.",
          whenUsed: "Para saber cuál es 'el' artefacto central que representa al proyecto en flujos de alto nivel.",
          properties: ["kind, id, relativePath"],
          relationships: ["Consumido transversalmente por Home/preview/generation/run"],
          cardinality: "1 proyecto ↔ 1 ProjectPrimaryArtifact (normalmente)",
          io: { inputs: "Selección del artefacto principal", outputs: "Punto de entrada único para flujos de alto nivel" },
          lifecycle: "Puede cambiar si el proyecto reorganiza cuál es su artefacto central.",
          example: "El metamodelo principal de un proyecto de modelado de procesos como artefacto primario.",
          evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectPrimaryArtifact" }]
        },
        {
          name: "Auditoría universal (AuditCanonicalDocumentMutationObserver)",
          purpose: "Rastro de 'quién cambió qué' para TODO documento canónico del sistema, de forma incondicional.",
          whenUsed: "Siempre — no es opt-in ni depende de ninguna política de capacidad.",
          properties: ["Conectado por LocalCanonicalDocumentStoreFactory a CADA CanonicalDocumentStore de la plataforma"],
          relationships: ["Es independiente del mecanismo de Events (que sí es opt-in por sesión vía publishEvents()) — existen dos rastros de cambios distintos: el de dominio (Events, opcional) y el de auditoría (universal, siempre activo)"],
          cardinality: "1 documento canónico ↔ N entradas de auditoría a lo largo de su vida",
          io: { inputs: "Cualquier mutación de cualquier documento canónico", outputs: "Entrada de auditoría con autor y cambio" },
          lifecycle: "No puede desactivarse por proyecto ni por capacidad: es una garantía de plataforma.",
          example: "Un cambio en una regla de validación queda auditado aunque esa sesión de editor no tenga publishEvents() activado.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "LocalCanonicalDocumentStoreFactory → AuditCanonicalDocumentMutationObserver" }]
        }
      ],
      authoring: {
        summary: "Gestión de Proyectos es navegación (árbol) + diálogos modales, no un asistente: el árbol de archivos es la vista principal, y crear/importar/exportar/activar-capacidades son todas acciones puntuales por diálogo, no un flujo guiado paso a paso. El panel Home es un dashboard real (no solo enrutado): continuar el proyecto activo por su artefacto principal, proyectos recientes, y un panel de 'preparación' embebido.",
        elements: [
          { element: "Árbol de archivos del proyecto", requiredness: "MANDATORY", modes: [
            { mode: "Árbol expandible de navegación", detail: "Una fila por nodo con icono según el tipo de artefacto; clic+Ctrl/Cmd para selección múltiple aditiva; doble clic para abrir; indicador de 'cambiado externamente'." }
          ]},
          { element: "Activar/desactivar ProjectCapability", requiredness: "OPTIONAL", modes: [
            { mode: "Diálogo modal 'Áreas del proyecto'", detail: "Interruptor de modo de trabajo Guiado/Libre + una cuadrícula de casillas por capacidad, con dependencias automáticas (activar una activa las que requiere; desactivar una desactiva las que dependen de ella)." }
          ]},
          { element: "Importar/exportar el proyecto o parte de él", requiredness: "ALTERNATIVE", modes: [
            { mode: "Botones de barra/menú contextual + diálogo", detail: "Exportar: 'Exportar selección como ZIP' (descarga local) o 'Exportar a repositorio…' (diálogo de destino remoto). Importar: diálogo con 4 opciones de origen — carpeta local, archivo ZIP, repositorio remoto, o archivo de proyecto externo (esta última SÍ dispara la recuperación/materialización semántica completa, como una vía de entrada alternativa a la de Recuperación Externa)." }
          ]},
          { element: "Panel Home / continuar trabajando", requiredness: "MANDATORY", modes: [
            { mode: "Dashboard real", detail: "Acciones primarias (nuevo/abrir/importar/conectar), tarjeta 'Continuar trabajando' basada en el artefacto principal del proyecto activo, resumen del proyecto, tarjetas de áreas habilitadas, presets de creación rápida, panel de preparación de capacidades embebido, y una cuadrícula de hasta 6 proyectos recientes." }
          ]}
        ]
      },
      level3: "Un proyecto tiene un conjunto de ProjectCapability activas (de 24 posibles) que determinan, vía ProjectIndexService.logicalRoot, qué parte del árbol lógico del proyecto es realmente navegable y utilizable — la restricción es del backend, no cosmética. Un ProjectPrimaryArtifact identifica el artefacto central consumido por los flujos de alto nivel (Home, preview, generación, ejecución). De forma ortogonal y SIEMPRE activa (no ligada a ninguna capacidad), toda mutación de cualquier documento canónico de la plataforma queda registrada en un rastro de auditoría universal, distinto e independiente del sistema de Events (que sí es opt-in).",
      level4: "Al activar/desactivar una ProjectCapability, ProjectIndexService recalcula de inmediato el logicalRoot; el frontend consume ese árbol ya filtrado, por lo que una capacidad desactivada no puede sortearse manipulando la interfaz. En paralelo, cada escritura sobre cualquier CanonicalDocumentStore de cualquier capacidad pasa, sin excepción, por AuditCanonicalDocumentMutationObserver, generando una entrada de auditoría — esto ocurre incluso si esa capacidad no tiene activado el sistema de Events de dominio.",
      level5: {
        incoming: ["Todas las capacidades (cada una opera dentro del alcance de un proyecto y sus capacidades activas)"],
        outgoing: ["Integration (requiredCapabilities exige capacidades de proyecto activas antes de entregar una tecnología)"]
      },
      level6: {
        runtime: "ProjectIndexService calcula logicalRoot; LocalCanonicalDocumentStoreFactory conecta la auditoría universal a cada store.",
        services: ["ProjectIndexService", "LocalCanonicalDocumentStoreFactory", "AuditCanonicalDocumentMutationObserver"],
        apis: ["REST de proyecto (Home, árbol lógico, artefacto primario)"],
        sources: [
          { path: "src/main/java/com/example/editor/project", line: 1, label: "Paquete project — ProjectCapability / ProjectIndexService" }
        ]
      }
    },
    en: {
      level1: {
        what: "Root container of a HYDRA project: which capabilities are active, what its primary artifacts are, and the universal change audit trail.",
        value: "Organizes and bounds what can be done inside a given project, enforced on the backend (not merely visual in the frontend).",
        inputs: ["Active-capability selection by the project's user/administrator"],
        outputs: ["Active/inactive logical artifact tree", "Navigable primary artifact (Home/preview/generation/run)", "Universal audit trail of every mutation"]
      },
      level2: [
        {
          name: "ProjectCapability (24 values)",
          purpose: "Closed catalog of capabilities a project can have active or inactive.",
          whenUsed: "When configuring which parts of HYDRA are available for a given project.",
          properties: ["Enum with exactly 24 values"],
          relationships: ["IntegrationProfileModel.requiredCapabilities requires certain ProjectCapability to be active before allowing a technology delivery"],
          cardinality: "1 project ↔ 0..24 active ProjectCapability",
          io: { inputs: "Enable/disable decision", outputs: "Real (not just visual) availability of project features" },
          lifecycle: "Enabling/disabling is enforced on the backend: ProjectIndexService recomputes the active/inactive logical tree.",
          example: "Disabling the Transformation capability on a project that does not need it, really hiding and blocking that flow.",
          evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectCapability (24 values)" }]
        },
        {
          name: "ProjectIndexService (logicalRoot)",
          purpose: "Computes on the backend which groups of the project's logical tree are active, based on enabled capabilities.",
          whenUsed: "Whenever active capabilities change or the project is navigated.",
          properties: ["logicalRoot: tree of active/inactive groups"],
          relationships: ["Enforcement is server-side; the frontend cannot re-enable a disabled capability by manipulating the UI alone"],
          cardinality: "1 project ↔ 1 computed logicalRoot",
          io: { inputs: "Set of active ProjectCapability", outputs: "Navigable logical tree" },
          lifecycle: "Reactively recomputed on any capability change.",
          example: "Disabling Gamification removes its group from the logical tree served to the frontend.",
          evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectIndexService.logicalRoot" }]
        },
        {
          name: "ProjectPrimaryArtifact",
          purpose: "A project's primary artifact (kind, id, relativePath) consumed by Home, preview, generation and run.",
          whenUsed: "To know which artifact is 'the' central one representing the project in high-level flows.",
          properties: ["kind, id, relativePath"],
          relationships: ["Consumed across Home/preview/generation/run"],
          cardinality: "1 project ↔ 1 ProjectPrimaryArtifact (normally)",
          io: { inputs: "Primary-artifact selection", outputs: "Single entry point for high-level flows" },
          lifecycle: "Can change if the project reorganizes which artifact is central.",
          example: "A process-modeling project's main metamodel as its primary artifact.",
          evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectPrimaryArtifact" }]
        },
        {
          name: "Universal audit (AuditCanonicalDocumentMutationObserver)",
          purpose: "A 'who changed what' trail for EVERY canonical document in the system, unconditionally.",
          whenUsed: "Always — it is not opt-in and does not depend on any capability policy.",
          properties: ["Wired by LocalCanonicalDocumentStoreFactory into EVERY CanonicalDocumentStore in the platform"],
          relationships: ["Independent of the Events mechanism (which IS opt-in per session via publishEvents()) — there are two distinct change trails: the domain one (Events, optional) and the audit one (universal, always on)"],
          cardinality: "1 canonical document ↔ N audit entries over its lifetime",
          io: { inputs: "Any mutation of any canonical document", outputs: "Audit entry with author and change" },
          lifecycle: "Cannot be disabled per project or per capability: it is a platform guarantee.",
          example: "A change to a validation rule gets audited even if that editor session does not have publishEvents() enabled.",
          evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "LocalCanonicalDocumentStoreFactory → AuditCanonicalDocumentMutationObserver" }]
        }
      ],
      authoring: {
        summary: "Project Management is navigation (tree) + modal dialogs, not a wizard: the file tree is the primary view, and create/import/export/toggle-capabilities are all one-shot dialog actions, not a guided step-by-step flow. The Home panel is a real dashboard (not just routing): continuing the active project via its primary artifact, recent projects, and an embedded 'readiness' panel.",
        elements: [
          { element: "Project file tree", requiredness: "MANDATORY", modes: [
            { mode: "Expandable navigation tree", detail: "One row per node with an icon badge per artifact kind; Ctrl/Cmd-click for additive multi-select; double-click to open; a 'changed externally' indicator." }
          ]},
          { element: "Enable/disable a ProjectCapability", requiredness: "OPTIONAL", modes: [
            { mode: "'Project areas' modal dialog", detail: "A Guided/Free working-mode toggle plus a checkbox grid per capability, with automatic dependencies (enabling one auto-enables what it requires; disabling one auto-disables what depends on it)." }
          ]},
          { element: "Import/export the project or part of it", requiredness: "ALTERNATIVE", modes: [
            { mode: "Toolbar/context-menu buttons + dialog", detail: "Export: 'Export selection as ZIP' (local download) or 'Export to repository…' (remote-destination dialog). Import: a dialog with 4 source options — local folder, ZIP file, remote repository, or external project archive (this last one DOES trigger the full semantic recovery/materialization pipeline, as an alternative entry point to External Recovery's own wizard)." }
          ]},
          { element: "Home panel / continue working", requiredness: "MANDATORY", modes: [
            { mode: "Real dashboard", detail: "Primary actions (new/open/import/connect), a 'Continue working' card based on the active project's primary artifact, a project-overview summary, enabled-area cards, quick-create presets, an embedded capability-readiness panel, and a grid of up to 6 recent projects." }
          ]}
        ]
      },
      level3: "A project has a set of active ProjectCapability (out of 24 possible) that determine, via ProjectIndexService.logicalRoot, which part of the project's logical tree is actually navigable and usable — the restriction is backend-enforced, not cosmetic. A ProjectPrimaryArtifact identifies the central artifact consumed by high-level flows (Home, preview, generation, run). Orthogonally and ALWAYS active (not tied to any capability), every mutation of any canonical document on the platform is recorded in a universal audit trail, distinct and independent from the Events system (which is opt-in).",
      level4: "When a ProjectCapability is enabled/disabled, ProjectIndexService immediately recomputes logicalRoot; the frontend consumes that already-filtered tree, so a disabled capability cannot be bypassed by manipulating the UI. In parallel, every write to any CanonicalDocumentStore of any capability passes, without exception, through AuditCanonicalDocumentMutationObserver, generating an audit entry — this happens even if that capability does not have the domain Events system enabled.",
      level5: {
        incoming: ["All capabilities (each operates within the scope of a project and its active capabilities)"],
        outgoing: ["Integration (requiredCapabilities demands active project capabilities before delivering a technology)"]
      },
      level6: {
        runtime: "ProjectIndexService computes logicalRoot; LocalCanonicalDocumentStoreFactory wires universal audit into every store.",
        services: ["ProjectIndexService", "LocalCanonicalDocumentStoreFactory", "AuditCanonicalDocumentMutationObserver"],
        apis: ["Project REST (Home, logical tree, primary artifact)"],
        sources: [
          { path: "src/main/java/com/example/editor/project", line: 1, label: "project package — ProjectCapability / ProjectIndexService" }
        ]
      }
    }
  },

  toolchains: {
    archComponentId: "ext_toolchains",
    kind: "external",
    es: {
      level1: {
        what: "Comprueba y gestiona las herramientas externas (motores de transformación, tecnologías de Integración, build tools genéricos) que HYDRA puede necesitar para ejecutar o verificar algo fuera de sí mismo.",
        value: "Permite saber, antes de intentar una operación, si la herramienta externa necesaria está realmente instalada y funcionando — y, para algunas, instalarla directamente desde el navegador.",
        inputs: ["Catálogo de paquetes de confianza administrados (para motores de transformación)", "Comandos de sondeo de versión (java/mvn/node/npm/python) para herramientas de desarrollo genéricas"],
        outputs: ["Estado de disponibilidad por tecnología/motor", "Paquete de motor instalado/actualizado/eliminado"]
      },
      level2: [
        {
          name: "Centro de compatibilidad 'Available tools'",
          purpose: "Panel de solo lectura que comprueba si las herramientas de desarrollo genéricas necesarias (EMF, Sirius Desktop, Xtext, Langium, Eclipse GLSP, BESSER SDK, BESSER Web) están instaladas en el servidor.",
          whenUsed: "Antes de depender de una tecnología que requiere un entorno de build externo (p. ej. compilar un proyecto Langium generado).",
          properties: ["Catálogo fijo de 7 tecnologías con sus comandos de sondeo (java -version, mvn -version, node --version, npm --version, python --version/pip --version)", "Estado PASSED/PARTIAL/FAILED/NOT_RUN por tecnología"],
          relationships: ["Es su propio módulo de navegación de primer nivel ('Available tools'), independiente de Transformación y de Integración", "No instala nada: solo ejecuta comandos de sondeo de versión en una lista cerrada"],
          cardinality: "1 catálogo fijo de 7 tecnologías ↔ 1 ejecución de sondeo por comprobación solicitada",
          io: { inputs: "Selección de qué comprobaciones ejecutar", outputs: "Estado pasa/parcial/falla + registro de evidencia por tecnología" },
          lifecycle: "No hay instalación ni modificación de estado: es una fotografía de disponibilidad bajo demanda.",
          example: "Comprobar que 'npm' y 'node' están disponibles antes de generar un editor Langium.",
          evidence: [{ path: "src/main/java/com/example/editor/integration/compatibility", line: 1, label: "NativeToolchainProbeCatalog / NativeToolchainProbeExecutor" }]
        },
        {
          name: "Catálogo de motores nativos de Transformación (TransformationToolchainPackageDescriptor)",
          purpose: "Representa un motor externo instalable (ATL, ETL, Acceleo, EGL, Xtend…) distinguiendo explícitamente el 'bridge' (el adaptador propio de HYDRA) del 'toolchain' (el producto externo que envuelve).",
          whenUsed: "Al querer ejecutar una transformación con paridad real contra un motor externo, en vez de (o además de) el runtime propio de HYDRA.",
          properties: ["language, packageId, packageVersion", "bridgeName/bridgeVersion vs. toolchainName/toolchainVersion (deben coincidir)", "installable, installed, diagnostics"],
          relationships: ["Catálogo de confianza administrado en distribution/transformation-toolchains/<lenguaje>/<paquete>/<versión>/", "Dos estados reales y distintos: toolAvailable (el motor existe) y executionAvailable (se puede ejecutar ahora mismo) — NO existen 'bridge available' ni 'engine available' como estados propios en el código"],
          cardinality: "1 lenguaje de exportación ↔ 0..N versiones de paquete instalables; 0..1 instalación activa por lenguaje",
          io: { inputs: "Selección de un paquete del catálogo de confianza", outputs: "Motor instalado (verificado por SHA-256) o desinstalado" },
          lifecycle: "La instalación verifica checksums de cada fichero del payload contra el manifiesto del paquete y revierte automáticamente si el bundle instalado se autodescribe como incompatible de protocolo.",
          example: "Instalar la versión 2.1.0 del puente oficial de ATL para poder ejecutar una transformación con paridad nativa.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation/nativeexec", line: 1, label: "TransformationToolchainProvisioningService" }]
        }
      ],
      authoring: {
        summary: "No existe una página única de 'Toolchains': hay dos superficies reales y separadas, cada una con su propio catálogo y su propio significado de 'disponible'. Tampoco existe un selector de 'qué motor instalado usar' en el momento de ejecutar una transformación — el comando está fijado por lenguaje en el servidor; el selector de paquete solo existe en el panel de instalación.",
        elements: [
          { element: "Disponibilidad de herramientas de desarrollo genéricas", requiredness: "MANDATORY", modes: [
            { mode: "Panel de solo lectura ('Available tools')", detail: "Seleccionar qué comprobaciones ejecutar y pulsar 'Ejecutar'; sin instalación, sin configuración — un módulo de navegación de primer nivel, independiente de metamodelo activo." }
          ]},
          { element: "Motor nativo de Transformación instalado", requiredness: "OPTIONAL", modes: [
            { mode: "Panel de aprovisionamiento anidado", detail: "Incrustado dentro del panel de vista previa de Transformación, por lenguaje de exportación: elegir versión del catálogo, Instalar/Actualizar/Eliminar." },
            { mode: "Archivo de configuración explícito (administrador)", detail: "Propiedades de comando explícitas por lenguaje, que tienen prioridad sobre cualquier paquete autoinstalado por HYDRA." }
          ]}
        ]
      },
      level3: "No hay un concepto unificado de 'toolchain' en el código: son 3 subsistemas independientes que casualmente reutilizan la etiqueta TOOL_UNAVAILABLE. (A) Los motores nativos instalables de Transformación, con checksum, catálogo de confianza y dos estados reales (toolAvailable/executionAvailable). (B) El centro de compatibilidad de solo lectura que sondea versiones de herramientas de desarrollo genéricas compartidas por Integración y Gramática. (C) La elección de tecnología de Gramática (Langium/Xtext/ANTLR), que es una configuración de pipeline de build, no un motor instalable con versión propia.",
      level4: "Para (A): instalar verifica SHA-256 de cada fichero del payload contra el manifiesto, copia atómicamente al directorio gestionado y, si el bundle se autodescribe incompatible de protocolo, revierte automáticamente la instalación. El diagnóstico de salud ejecuta el bridge con un flag de autodescripción en un workspace temporal aislado. Para (B): cada comprobación ejecuta un comando de versión permitido en una lista cerrada y clasifica el resultado. Para (C): se materializa el proyecto generado en un workspace temporal y se ejecuta el build real (npm/mvn) asumiendo que esas herramientas ya están presentes (verificadas, precisamente, por el centro de compatibilidad (B)).",
      level5: {
        incoming: ["Ninguno — Toolchains es consultado, no depende de otras capacidades de dominio"],
        outgoing: ["Transformación (ejecución con paridad nativa vía motor instalado)", "Integración (verificación de proyecto generado requiere build tools presentes)", "Gramática (el build de la tecnología elegida requiere npm/mvn presentes)"]
      },
      level6: {
        runtime: "TransformationNativeRuntimeExecutor ejecuta diagnóstico/exportación; NativeToolchainProbeExecutor ejecuta sondeos de versión; GrammarNativeToolchainService materializa y construye el proyecto generado.",
        services: ["TransformationToolchainProvisioningService", "TransformationNativeRuntimeExecutor", "NativeToolchainProbeExecutor", "GrammarNativeToolchainService"],
        apis: ["/api/transformations/parity/native/toolchains/*", "/api/toolchain-compatibility/*"],
        sources: [
          { path: "src/main/java/com/example/editor/transformation/nativeexec", line: 1, label: "Native engine bridges (A)" },
          { path: "src/main/java/com/example/editor/integration/compatibility", line: 1, label: "Compatibility center (B)" }
        ]
      }
    },
    en: {
      level1: {
        what: "Checks and manages the external tools (transformation engines, Integration technologies, generic build tools) HYDRA may need to run or verify something outside itself.",
        value: "Lets you know, before attempting an operation, whether the needed external tool is actually installed and working — and, for some of them, install it directly from the browser.",
        inputs: ["Admin-trusted managed package catalog (for transformation engines)", "Version-probe commands (java/mvn/node/npm/python) for generic development tools"],
        outputs: ["Availability status per technology/engine", "Installed/updated/removed engine package"]
      },
      level2: [
        {
          name: "'Available tools' compatibility center",
          purpose: "A read-only panel checking whether the needed generic development tools (EMF, Sirius Desktop, Xtext, Langium, Eclipse GLSP, BESSER SDK, BESSER Web) are installed on the server.",
          whenUsed: "Before depending on a technology that requires an external build environment (e.g. compiling a generated Langium project).",
          properties: ["A fixed catalog of 7 technologies with their probe commands (java -version, mvn -version, node --version, npm --version, python --version/pip --version)", "PASSED/PARTIAL/FAILED/NOT_RUN status per technology"],
          relationships: ["Its own top-level navigation module ('Available tools'), independent of Transformation and Integration", "Installs nothing: only runs allow-listed version-probe commands"],
          cardinality: "1 fixed catalog of 7 technologies ↔ 1 probe run per requested check",
          io: { inputs: "Selecting which checks to run", outputs: "Pass/partial/fail status + evidence log per technology" },
          lifecycle: "No installation or state change: an on-demand availability snapshot.",
          example: "Checking that 'npm' and 'node' are available before generating a Langium editor.",
          evidence: [{ path: "src/main/java/com/example/editor/integration/compatibility", line: 1, label: "NativeToolchainProbeCatalog / NativeToolchainProbeExecutor" }]
        },
        {
          name: "Transformation's native-engine catalog (TransformationToolchainPackageDescriptor)",
          purpose: "Represents an installable external engine (ATL, ETL, Acceleo, EGL, Xtend…), explicitly distinguishing the 'bridge' (HYDRA's own adapter) from the 'toolchain' (the external product it wraps).",
          whenUsed: "When wanting to run a transformation with real parity against an external engine, instead of (or alongside) HYDRA's own runtime.",
          properties: ["language, packageId, packageVersion", "bridgeName/bridgeVersion vs. toolchainName/toolchainVersion (must cross-validate)", "installable, installed, diagnostics"],
          relationships: ["Admin-trusted catalog under distribution/transformation-toolchains/<language>/<package>/<version>/", "Two real, distinct states: toolAvailable (the engine exists) and executionAvailable (it can run right now) — there is NO 'bridge available' or 'engine available' as their own coded states"],
          cardinality: "1 export language ↔ 0..N installable package versions; 0..1 active installation per language",
          io: { inputs: "Picking a package from the trusted catalog", outputs: "An installed (SHA-256-verified) or uninstalled engine" },
          lifecycle: "Install verifies every payload file's checksum against the package manifest and automatically rolls back if the installed bundle self-describes as protocol-incompatible.",
          example: "Installing version 2.1.0 of the official ATL bridge to run a transformation with native parity.",
          evidence: [{ path: "src/main/java/com/example/editor/transformation/nativeexec", line: 1, label: "TransformationToolchainProvisioningService" }]
        }
      ],
      authoring: {
        summary: "There is no single 'Toolchains' page: there are two real, separate surfaces, each with its own catalog and its own meaning of 'available'. There is also no 'which installed engine to use' selector at transformation-execution time — the command is fixed per language server-side; the package picker only exists in the install panel.",
        elements: [
          { element: "Generic development-tool availability", requiredness: "MANDATORY", modes: [
            { mode: "Read-only panel ('Available tools')", detail: "Select which checks to run and click 'Run'; no install, no configuration — a top-level navigation module, independent of the active metamodel." }
          ]},
          { element: "Installed native Transformation engine", requiredness: "OPTIONAL", modes: [
            { mode: "Nested provisioning panel", detail: "Embedded inside Transformation's preview panel, per export language: pick a catalog version, Install/Update/Remove." },
            { mode: "Explicit config file (administrator)", detail: "Explicit per-language command properties, which take priority over any HYDRA-self-installed package." }
          ]}
        ]
      },
      level3: "There is no unified 'toolchain' concept in the code: 3 independent subsystems happen to reuse the TOOL_UNAVAILABLE label. (A) Transformation's installable native engines, with checksums, a trusted catalog and two real states (toolAvailable/executionAvailable). (B) The read-only compatibility center probing generic development-tool versions shared by Integration and Grammar. (C) Grammar's technology choice (Langium/Xtext/ANTLR), which is a build-pipeline configuration, not an installable engine with its own version.",
      level4: "For (A): install verifies every payload file's SHA-256 against the package manifest, atomically copies into the managed directory and, if the bundle self-describes as protocol-incompatible, auto-rolls back. Health diagnosis runs the bridge with a self-description flag in an isolated temp workspace. For (B): each check runs an allow-listed version command and classifies the result. For (C): the generated project is materialized into a temp workspace and the real build (npm/mvn) runs, assuming those tools are already present (verified, precisely, by compatibility center (B)).",
      level5: {
        incoming: ["None — Toolchains is consulted, it does not depend on other domain capabilities"],
        outgoing: ["Transformation (native-parity execution via an installed engine)", "Integration (verifying a generated project requires build tools to be present)", "Grammar (building the chosen technology requires npm/mvn to be present)"]
      },
      level6: {
        runtime: "TransformationNativeRuntimeExecutor runs diagnosis/export; NativeToolchainProbeExecutor runs version probes; GrammarNativeToolchainService materializes and builds the generated project.",
        services: ["TransformationToolchainProvisioningService", "TransformationNativeRuntimeExecutor", "NativeToolchainProbeExecutor", "GrammarNativeToolchainService"],
        apis: ["/api/transformations/parity/native/toolchains/*", "/api/toolchain-compatibility/*"],
        sources: [
          { path: "src/main/java/com/example/editor/transformation/nativeexec", line: 1, label: "Native engine bridges (A)" },
          { path: "src/main/java/com/example/editor/integration/compatibility", line: 1, label: "Compatibility center (B)" }
        ]
      }
    }
  },

  external_repositories: {
    archComponentId: "ext_remote",
    kind: "external",
    es: {
      level1: {
        what: "Conecta HYDRA con proveedores externos de control de versiones y almacenamiento (GitHub, GitLab, OneDrive, Google Drive, Dropbox, Git genérico) para exportar/importar proyectos y, en los proveedores Git, operar ramas/commits/fusiones.",
        value: "Permite respaldar, compartir y sincronizar proyectos HYDRA con la infraestructura externa que el equipo ya usa, sin salir de la aplicación.",
        inputs: ["Perfil de conexión por proveedor (identidad + destino + credenciales gestionadas por HYDRA)"],
        outputs: ["Proyecto exportado/importado como archivo", "Estado de repositorio Git (rama, commits) y conflictos resueltos"]
      },
      level2: [
        {
          name: "Perfil de conexión (RemoteStorageProviderType)",
          purpose: "Identidad y destino de una conexión a un proveedor externo concreto.",
          whenUsed: "Una vez por proveedor/repositorio que se quiera usar.",
          properties: ["provider ∈ {GITHUB, GITLAB, ONEDRIVE, GOOGLE_DRIVE, DROPBOX, GENERIC_GIT} (exactamente 6, ninguno más)", "Campos específicos por proveedor (owner/repo para GitHub, ID de proyecto para GitLab, URL+usuario para Git genérico, ID de unidad/carpeta para OneDrive/Google Drive)"],
          relationships: ["Determina qué operaciones están disponibles: solo GITHUB/GITLAB/GENERIC_GIT soportan operaciones Git completas; los otros 3 solo transferencia de archivos genérica"],
          cardinality: "0..N perfiles guardados, uno por conexión",
          io: { inputs: "Datos de identidad del proveedor", outputs: "Conexión autenticada reutilizable" },
          lifecycle: "Guardar/probar/eliminar; la credencial nunca se devuelve al navegador tras autenticar.",
          example: "Un perfil GitHub apuntando a owner/repo='miequipo/proyecto-hydra'.",
          evidence: [{ path: "src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java", line: 4, label: "RemoteStorageProviderType (6 valores)" }]
        },
        {
          name: "Autenticación (OAuth código+PKCE / Device Code)",
          purpose: "Conectar la identidad del usuario con el proveedor externo sin que HYDRA vea ni guarde la contraseña.",
          whenUsed: "Al pulsar 'Conectar' por primera vez para un proveedor OAuth (los 5 salvo GENERIC_GIT).",
          properties: ["Código de autorización + PKCE (por defecto)", "Device Code (solo si el administrador lo habilita explícitamente, para entornos sin navegador)", "Para GENERIC_GIT: credencial de usuario/contraseña HTTPS gestionada en servidor, nunca un token en claro"],
          relationships: ["No existe autenticación por clave SSH ni por token de acceso personal introducido a mano (salvo el caso gestionado de Git genérico)"],
          cardinality: "1 perfil ↔ 0..1 conexión autenticada activa",
          io: { inputs: "Consentimiento del usuario en la página real del proveedor (nunca en HYDRA)", outputs: "Credencial cifrada en una bóveda del servidor" },
          lifecycle: "El token de acceso/refresco/secreto de cliente nunca se devuelve al navegador.",
          example: "Conectar con GitHub: se abre una ventana emergente a github.com, el usuario inicia sesión allí, HYDRA solo recibe la confirmación.",
          evidence: [{ path: "src/main/java/com/example/editor/remote/auth/RemoteAuthService.java", line: 38, end_line: 52, label: "RemoteAuthService.start()" }]
        },
        {
          name: "Operaciones Git reales (solo GitHub/GitLab/Git genérico)",
          purpose: "Estado, fetch, pull (con estrategia), push, merge, commit, crear rama, checkout — sobre un repositorio real.",
          whenUsed: "Cuando el perfil conectado es uno de los 3 proveedores Git.",
          properties: ["Enum RemoteGitOperation: STATUS/INIT/FETCH/PULL/PUSH/MERGE/ABORT_MERGE/COMMIT_ALL/CREATE_BRANCH/CHECKOUT", "Ejecución siempre vía un runner sandboxed, sin shell ni comandos arbitrarios"],
          relationships: ["Rechazado explícitamente en código para OneDrive/Google Drive/Dropbox"],
          cardinality: "1 perfil Git ↔ N operaciones ejecutables",
          io: { inputs: "Operación + parámetros (rama, mensaje de commit, estrategia de pull)", outputs: "Árbol de ramas local+remoto con commits recientes, o un conflicto a resolver" },
          lifecycle: "Nunca force-push ni reset arbitrario — excluidos deliberadamente.",
          example: "Hacer pull con estrategia FF_ONLY y, si hay conflicto, resolverlo en el visor de 4 paneles (base/local/remoto/resultado).",
          evidence: [{ path: "src/main/java/com/example/editor/remote/git/RemoteGitWorkspaceService.java", line: 199, label: "Restricción de operaciones Git a 3 proveedores" }]
        }
      ],
      authoring: {
        summary: "La pantalla real se llama 'Repositorios' (no 'Remote'/'Git'): combina un asistente guiado de 3 pasos con un modo avanzado/manual de 3 secciones, y añade el panel de operaciones Git completo (árbol de ramas + conflicto de 4 paneles) solo cuando el proveedor conectado es uno de los 3 que soportan Git.",
        elements: [
          { element: "Perfil de conexión", requiredness: "MANDATORY", modes: [
            { mode: "Asistente guiado (3 pasos) o modo avanzado", detail: "Identidad del perfil → destino del repositorio (campos específicos por proveedor) → compatibilidad y seguridad (URL base personalizada, tamaño máximo de artefacto, más el widget de 'Conectar cuenta' OAuth embebido)." }
          ]},
          { element: "Operaciones Git (solo proveedores Git)", requiredness: "CONDITIONAL", modes: [
            { mode: "Panel de operaciones Git", detail: "Mostrado solo si el proveedor es Git: árbol de ramas local/remoto con historial, botones Fetch/Pull/Push/Commit/Rama/Checkout/Merge." },
            { mode: "Resolutor visual de conflictos", detail: "Vista de 4 paneles (base/local/remoto/resultado) para resolver un conflicto de fusión, con opción de preparar (stage) la resolución." }
          ]},
          { element: "Transferencia genérica de artefactos (todos los proveedores)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Exportar/importar proyecto completo como ZIP", detail: "Sin navegación de carpetas — solo lectura/escritura de rutas exactas; no hay explorador de árbol remoto para los 5 proveedores de solo-archivo." },
            { mode: "Exportar/importar una capa individual", detail: "Subida/descarga de un único fichero con concurrencia optimista (versión esperada) y modo de escritura (sobrescribir/solo-crear/solo-actualizar)." }
          ]}
        ]
      },
      level3: "Un perfil de conexión fija un proveedor (de 6 posibles) y su destino; la autenticación OAuth (código+PKCE o device-code) o la credencial gestionada de Git genérico conecta la identidad sin exponer secretos al navegador. Según el proveedor, el perfil habilita o no un conjunto de operaciones Git reales (solo 3 de los 6 proveedores) además de la transferencia genérica de archivos (los 6 proveedores). Importar desde un repositorio remoto es una restauración de archivo simple — NO alimenta la recuperación semántica de Recuperación Externa, que exige una subida de archivo local independiente.",
      level4: "Conectar: el usuario pulsa 'Conectar', se abre una ventana al proveedor real, HYDRA solo recibe un código de confirmación que intercambia en el servidor por tokens (nunca expuestos al navegador). Usar: para Git, cada operación pasa por un runner sandboxed sin shell; para los demás proveedores, cada export/import es una lectura/escritura de ruta exacta con verificación de versión. Los conflictos de fusión se muestran en un visor de 4 paneles con resolución explícita, nunca automática silenciosa.",
      level5: {
        incoming: ["Ninguna — es un punto de entrada/salida externo"],
        outgoing: ["Project Management (importación de archivo de proyecto, exportación de selección)", "NO alimenta Recuperación Externa — son caminos verificadamente separados en el código"]
      },
      level6: {
        runtime: "RemoteAuthService coordina OAuth/Device Code; RemoteGitWorkspaceService ejecuta operaciones Git sandboxed; RemoteProjectTransferService hace export/import de archivo.",
        services: ["RemoteAuthService", "RemoteGitWorkspaceService", "RemoteProjectTransferService", "RemoteStorageController"],
        apis: ["/api/remote-auth/*", "/api/remote-git/*", "/api/remote-storage/*"],
        sources: [
          { path: "src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java", line: 4, label: "RemoteStorageProviderType" },
          { path: "src/main/java/com/example/editor/remote/git/RemoteGitWorkspaceService.java", line: 199, label: "Restricción de Git a 3 proveedores" }
        ]
      }
    },
    en: {
      level1: {
        what: "Connects HYDRA to external version-control and storage providers (GitHub, GitLab, OneDrive, Google Drive, Dropbox, generic Git) to export/import projects and, for Git providers, operate branches/commits/merges.",
        value: "Lets a team back up, share and synchronize HYDRA projects with the external infrastructure it already uses, without leaving the app.",
        inputs: ["A per-provider connection profile (identity + target + HYDRA-managed credentials)"],
        outputs: ["An exported/imported project file", "Git repository state (branch, commits) and resolved conflicts"]
      },
      level2: [
        {
          name: "Connection profile (RemoteStorageProviderType)",
          purpose: "Identity and target of a connection to a specific external provider.",
          whenUsed: "Once per provider/repository to be used.",
          properties: ["provider ∈ {GITHUB, GITLAB, ONEDRIVE, GOOGLE_DRIVE, DROPBOX, GENERIC_GIT} (exactly 6, no more)", "Provider-specific fields (owner/repo for GitHub, project ID for GitLab, URL+username for generic Git, drive/folder ID for OneDrive/Google Drive)"],
          relationships: ["Determines which operations are available: only GITHUB/GITLAB/GENERIC_GIT support full Git operations; the other 3 only generic file transfer"],
          cardinality: "0..N saved profiles, one per connection",
          io: { inputs: "Provider identity data", outputs: "A reusable authenticated connection" },
          lifecycle: "Save/test/delete; the credential is never returned to the browser after authenticating.",
          example: "A GitHub profile pointing at owner/repo='myteam/hydra-project'.",
          evidence: [{ path: "src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java", line: 4, label: "RemoteStorageProviderType (6 values)" }]
        },
        {
          name: "Authentication (OAuth code+PKCE / Device Code)",
          purpose: "Connects the user's identity to the external provider without HYDRA ever seeing or storing the password.",
          whenUsed: "The first time 'Connect' is clicked for an OAuth provider (the 5 except GENERIC_GIT).",
          properties: ["Authorization Code + PKCE (default)", "Device Code (only if the admin explicitly enables it, for browser-less environments)", "For GENERIC_GIT: a server-managed HTTPS username/credential, never a raw token"],
          relationships: ["No SSH-key auth and no hand-entered personal access token exist (except the managed generic-Git case)"],
          cardinality: "1 profile ↔ 0..1 active authenticated connection",
          io: { inputs: "User consent on the provider's own real page (never on HYDRA)", outputs: "An encrypted credential in a server-side vault" },
          lifecycle: "The access/refresh token/client secret is never returned to the browser.",
          example: "Connecting to GitHub: a popup opens to github.com, the user signs in there, HYDRA only receives the confirmation.",
          evidence: [{ path: "src/main/java/com/example/editor/remote/auth/RemoteAuthService.java", line: 38, end_line: 52, label: "RemoteAuthService.start()" }]
        },
        {
          name: "Real Git operations (GitHub/GitLab/generic Git only)",
          purpose: "Status, fetch, pull (with strategy), push, merge, commit, create branch, checkout — over a real repository.",
          whenUsed: "When the connected profile is one of the 3 Git providers.",
          properties: ["RemoteGitOperation enum: STATUS/INIT/FETCH/PULL/PUSH/MERGE/ABORT_MERGE/COMMIT_ALL/CREATE_BRANCH/CHECKOUT", "Always executed via a sandboxed runner, no shell, no arbitrary commands"],
          relationships: ["Explicitly rejected in code for OneDrive/Google Drive/Dropbox"],
          cardinality: "1 Git profile ↔ N executable operations",
          io: { inputs: "Operation + parameters (branch, commit message, pull strategy)", outputs: "Local+remote branch tree with recent commits, or a conflict to resolve" },
          lifecycle: "Never force-push or arbitrary reset — deliberately excluded.",
          example: "Pulling with an FF_ONLY strategy and, on conflict, resolving it in the 4-pane viewer (base/local/remote/result).",
          evidence: [{ path: "src/main/java/com/example/editor/remote/git/RemoteGitWorkspaceService.java", line: 199, label: "Git operations restricted to 3 providers" }]
        }
      ],
      authoring: {
        summary: "The real screen is called 'Repositories' (not 'Remote'/'Git'): it combines a 3-step guided wizard with a 3-section advanced/manual mode, and adds the full Git operations panel (branch tree + 4-pane conflict view) only when the connected provider is one of the 3 that support Git.",
        elements: [
          { element: "Connection profile", requiredness: "MANDATORY", modes: [
            { mode: "Guided wizard (3 steps) or advanced mode", detail: "Profile identity → repository target (provider-specific fields) → compatibility and security (custom base URL, max artifact size, plus the embedded OAuth 'Connect account' widget)." }
          ]},
          { element: "Git operations (Git providers only)", requiredness: "CONDITIONAL", modes: [
            { mode: "Git operations panel", detail: "Shown only if the provider is Git: local/remote branch tree with history, Fetch/Pull/Push/Commit/Branch/Checkout/Merge buttons." },
            { mode: "Visual conflict resolver", detail: "A 4-pane view (base/local/remote/result) to resolve a merge conflict, with an option to stage the resolution." }
          ]},
          { element: "Generic artifact transfer (all providers)", requiredness: "ALTERNATIVE", modes: [
            { mode: "Export/import the whole project as a ZIP", detail: "No folder browsing — only exact-path read/write; no remote tree browser exists for the 5 file-only providers." },
            { mode: "Export/import a single layer", detail: "Single-file upload/download with optimistic concurrency (expected version) and a write mode (overwrite/create-only/update-only)." }
          ]}
        ]
      },
      level3: "A connection profile fixes a provider (of 6 possible) and its target; OAuth authentication (code+PKCE or device-code) or generic Git's managed credential connects the identity without exposing secrets to the browser. Depending on the provider, the profile enables or not a set of real Git operations (only 3 of the 6 providers) besides generic file transfer (all 6 providers). Importing from a remote repository is a simple file restore — it does NOT feed External Recovery's semantic recovery, which requires an independent local file upload.",
      level4: "Connect: the user clicks 'Connect', a window opens to the real provider, HYDRA only receives a confirmation code it exchanges server-side for tokens (never exposed to the browser). Use: for Git, every operation runs through a sandboxed, shell-less runner; for other providers, every export/import is an exact-path read/write with version checking. Merge conflicts are shown in a 4-pane viewer with explicit, never silent-automatic, resolution.",
      level5: {
        incoming: ["None — this is an external entry/exit point"],
        outgoing: ["Project Management (project file import, selection export)", "Does NOT feed External Recovery — these are verifiably separate code paths"]
      },
      level6: {
        runtime: "RemoteAuthService coordinates OAuth/Device Code; RemoteGitWorkspaceService runs sandboxed Git operations; RemoteProjectTransferService does file export/import.",
        services: ["RemoteAuthService", "RemoteGitWorkspaceService", "RemoteProjectTransferService", "RemoteStorageController"],
        apis: ["/api/remote-auth/*", "/api/remote-git/*", "/api/remote-storage/*"],
        sources: [
          { path: "src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java", line: 4, label: "RemoteStorageProviderType" },
          { path: "src/main/java/com/example/editor/remote/git/RemoteGitWorkspaceService.java", line: 199, label: "Git restricted to 3 providers" }
        ]
      }
    }
  },

  generated_projects: {
    archComponentId: "b_project",
    kind: "backend",
    es: {
      level1: {
        what: "Registra, genera, valida, abre y lanza un 'proyecto generado' — el resultado empaquetado de un generador (editor standalone, servidor, etc.) — como una secuencia de etapas con resultado pasa/falla por etapa.",
        value: "Da un ciclo de vida operativo claro a lo que la generación de código produce: no basta con generar ficheros, hay que poder validarlos, abrirlos y lanzarlos de forma repetible.",
        inputs: ["Artefactos ya generados (por Publicación/generación de código)", "Perfil de runtime del proyecto (uno de varios soportados, p. ej. standalone-web/langium/xtext/glsp/besser-sdk)"],
        outputs: ["Informe de ejecución por etapa (PASSED/FAILED/SKIPPED/PLANNED)", "Proyecto abierto y, opcionalmente, lanzado"]
      },
      level2: [
        {
          name: "GeneratedProjectStage (register → GENERATE → VALIDATE → OPEN → LAUNCH)",
          purpose: "Las etapas fijas del ciclo de vida de un proyecto generado.",
          whenUsed: "Siempre, en este orden, para cualquier proyecto generado que se registre.",
          properties: ["Enum fijo: GENERATE, VALIDATE, OPEN, LAUNCH (más el registro inicial, que no es una 'etapa' del enum)", "Cada etapa se resuelve a un comando específico del SO vía un perfil de runtime"],
          relationships: ["Ejecutado por GeneratedProjectLifecycleService a través de un ToolchainCommandRunner con timeout"],
          cardinality: "1 proyecto generado ↔ 1 recorrido de las etapas (LAUNCH es opcional)",
          io: { inputs: "Carpeta del proyecto generado + perfil de runtime", outputs: "GeneratedProjectExecutionReport con resultado por etapa" },
          lifecycle: "Puede ejecutarse como simulación (dry-run) antes de ejecutar de verdad.",
          example: "Registrar un editor standalone generado, generarlo, validarlo (build), abrirlo y lanzarlo como aplicación web.",
          evidence: [{ path: "src/main/java/com/example/editor/generated/project", line: 1, label: "GeneratedProjectLifecycleService / GeneratedProjectStage" }]
        }
      ],
      authoring: {
        summary: "Es un ejecutor de comandos por etapas con informe, no un monitor de procesos en vivo: no hay lista de procesos corriendo, no hay 'detener instancia', no hay gráfico continuo de recursos. Es 'ejecutar comandos de build/abrir/lanzar y mostrar pasa/falla', una vez por invocación.",
        elements: [
          { element: "Registro de un proyecto generado", requiredness: "MANDATORY", modes: [
            { mode: "Asistente guiado (5 pasos)", detail: "registrar (ID/nombre/perfil de runtime/carpeta relativa) → elegir proyecto + casilla de simulación → elegir etapas (casillas GENERATE/VALIDATE/OPEN/LAUNCH, cada una habilitada solo si el perfil de runtime la soporta) → previsualizar (dry-run) → ejecutar e inspeccionar resultado." }
          ]},
          { element: "Ejecución de etapas", requiredness: "CONDITIONAL", modes: [
            { mode: "Botón de ejecución con informe", detail: "Insignias de estado por etapa (pasa/falla) y, por paso, salida de comando/diagnósticos — sin reintento automático ni cola de trabajos visible." }
          ]}
        ]
      },
      level3: "Un proyecto generado se mueve por una secuencia fija de etapas, cada una resuelta a un comando real de sistema operativo según su perfil de runtime; LAUNCH es la única etapa opcional. No hay estado intermedio 'en ejecución' persistente — cada invocación produce un informe y termina.",
      level4: "register() crea la entrada; GENERATE produce o actualiza los artefactos; VALIDATE ejecuta el build/verificación nativa real; OPEN prepara el proyecto para abrirse en su entorno; LAUNCH (opcional) lo ejecuta de forma acotada. El modo dry-run permite previsualizar qué comandos se ejecutarían sin tocar nada.",
      level5: {
        incoming: ["Publicación (los artefactos generados que este ciclo de vida registra y ejecuta provienen de ModelCodeGenerationService/StandaloneEditorGeneratorService)"],
        outgoing: ["Ninguna — es un punto terminal operativo"]
      },
      level6: {
        runtime: "GeneratedProjectLifecycleService coordina el recorrido de etapas vía ToolchainCommandRunner.",
        services: ["GeneratedProjectLifecycleService"],
        apis: ["REST de registro/ejecución de proyecto generado (frontend generated-project-center)"],
        sources: [{ path: "src/main/java/com/example/editor/generated/project", line: 1, label: "generated.project package" }]
      }
    },
    en: {
      level1: {
        what: "Registers, generates, validates, opens and launches a 'generated project' — the packaged output of a generator (standalone editor, server, etc.) — as a sequence of stages with a pass/fail result per stage.",
        value: "Gives a clear operational lifecycle to what code generation produces: generating files isn't enough — they must be validatable, openable and launchable in a repeatable way.",
        inputs: ["Already-generated artifacts (from Publication/code generation)", "The project's runtime profile (one of several supported, e.g. standalone-web/langium/xtext/glsp/besser-sdk)"],
        outputs: ["A per-stage execution report (PASSED/FAILED/SKIPPED/PLANNED)", "An opened and, optionally, launched project"]
      },
      level2: [
        {
          name: "GeneratedProjectStage (register → GENERATE → VALIDATE → OPEN → LAUNCH)",
          purpose: "The fixed lifecycle stages of a generated project.",
          whenUsed: "Always, in this order, for any registered generated project.",
          properties: ["Fixed enum: GENERATE, VALIDATE, OPEN, LAUNCH (plus the initial registration, which is not itself an enum 'stage')", "Each stage resolves to an OS-specific command via a runtime profile"],
          relationships: ["Run by GeneratedProjectLifecycleService through a timed ToolchainCommandRunner"],
          cardinality: "1 generated project ↔ 1 pass through the stages (LAUNCH is optional)",
          io: { inputs: "Generated project folder + runtime profile", outputs: "A GeneratedProjectExecutionReport with a per-stage result" },
          lifecycle: "Can run as a dry-run before actually executing.",
          example: "Registering a generated standalone editor, generating it, validating it (build), opening it and launching it as a web app.",
          evidence: [{ path: "src/main/java/com/example/editor/generated/project", line: 1, label: "GeneratedProjectLifecycleService / GeneratedProjectStage" }]
        }
      ],
      authoring: {
        summary: "It is a staged command runner with a report, not a live process monitor: there is no running-process list, no 'stop instance', no continuous resource graph. It is 'run build/open/launch commands and show pass/fail', once per invocation.",
        elements: [
          { element: "Registering a generated project", requiredness: "MANDATORY", modes: [
            { mode: "Guided wizard (5 steps)", detail: "register (ID/name/runtime profile/relative folder) → choose project + dry-run checkbox → choose stages (GENERATE/VALIDATE/OPEN/LAUNCH checkboxes, each enabled only if the runtime profile supports it) → preview (dry-run) → run and inspect result." }
          ]},
          { element: "Stage execution", requiredness: "CONDITIONAL", modes: [
            { mode: "Run button with report", detail: "Per-stage status badges (pass/fail) and, per step, command output/diagnostics — no automatic retry or visible job queue." }
          ]}
        ]
      },
      level3: "A generated project moves through a fixed stage sequence, each resolved to a real OS command per its runtime profile; LAUNCH is the only optional stage. There is no persistent intermediate 'running' state — each invocation produces a report and ends.",
      level4: "register() creates the entry; GENERATE produces or updates the artifacts; VALIDATE runs the real native build/check; OPEN prepares the project to be opened in its environment; LAUNCH (optional) runs it in a bounded way. Dry-run mode previews which commands would execute without touching anything.",
      level5: {
        incoming: ["Publication (the generated artifacts this lifecycle registers and runs come from ModelCodeGenerationService/StandaloneEditorGeneratorService)"],
        outgoing: ["None — an operational terminal point"]
      },
      level6: {
        runtime: "GeneratedProjectLifecycleService coordinates the stage pass via ToolchainCommandRunner.",
        services: ["GeneratedProjectLifecycleService"],
        apis: ["REST for generated-project registration/execution (frontend generated-project-center)"],
        sources: [{ path: "src/main/java/com/example/editor/generated/project", line: 1, label: "generated.project package" }]
      }
    }
  },

  portability: {
    archComponentId: "b_project",
    kind: "backend",
    es: {
      level1: {
        what: "Empaqueta, versiona, fusiona semánticamente y aplica/revierte de forma transaccional las 'capas de capacidad' de un proyecto (metamodelo+gramática+editor+validaciones+…) como un paquete portable.",
        value: "Permite mover, compartir o actualizar de forma segura el conjunto de capacidades de un proyecto entre entornos, con capacidad real de deshacer si algo sale mal — no es una simple copia de ficheros.",
        inputs: ["Capas de capacidad activas del proyecto origen", "Un paquete de capacidad a importar/fusionar (local o remoto)"],
        outputs: ["Archivo de paquete de capacidad exportado", "Decisión de fusión aplicada (o revertida) con journal append-only"]
      },
      level2: [
        {
          name: "Paquete de capacidad (CapabilityPackageArchiveService)",
          purpose: "Empaqueta un conjunto de capas de capacidad (metamodelo, gramática, editor, validaciones, etc.) como un archivo portable con sus dependencias resueltas.",
          whenUsed: "Para exportar o importar el conjunto de capacidades de un proyecto como una unidad versionada.",
          properties: ["Resolución de dependencias entre capas al empaquetar", "Compatibilidad/versión declarada"],
          relationships: ["Existe también un empaquetado de proyecto completo aparte (ProjectPortablePackageController), distinto del de capas de capacidad"],
          cardinality: "1 proyecto ↔ N paquetes de capacidad exportables (uno por conjunto de capas elegido)",
          io: { inputs: "Selección de capas de capacidad", outputs: "Archivo portable" },
          lifecycle: "El archivo puede luego importarse/fusionarse en otro proyecto.",
          example: "Exportar las capas de Validación + Editor de un proyecto para reutilizarlas en otro con el mismo metamodelo.",
          evidence: [{ path: "src/main/java/com/example/editor/portability/archive", line: 1, label: "CapabilityPackageArchiveService" }]
        },
        {
          name: "Fusión semántica (CapabilitySemanticMergeService)",
          purpose: "Detecta conflictos entre dos paquetes de capacidad y permite previsualizar y decidir cómo fusionarlos.",
          whenUsed: "Al importar un paquete de capacidad sobre un proyecto que ya tiene capas propias potencialmente solapadas.",
          properties: ["Detección de conflicto", "Vista previa de fusión", "Decisión explícita por conflicto"],
          relationships: ["Se apoya en CapabilityOrchestrationService para aplicar la secuencia de pasos resultante"],
          cardinality: "1 fusión ↔ N conflictos detectados ↔ N decisiones",
          io: { inputs: "Dos paquetes de capacidad a fusionar", outputs: "Vista previa de fusión + plan de aplicación" },
          lifecycle: "Nada se aplica hasta que el plan de fusión se confirma explícitamente.",
          example: "Importar un paquete con una versión actualizada de las reglas de validación, revisando qué reglas chocan con las ya existentes.",
          evidence: [{ path: "src/main/java/com/example/editor/portability/merge", line: 1, label: "CapabilitySemanticMergeService" }]
        },
        {
          name: "Transacción de capa (CapabilityLayerTransactionService)",
          purpose: "Aplica una secuencia de pasos de capacidad con posibilidad de vista previa, aplicación real y reversión, con journal append-only y rollback por ETag.",
          whenUsed: "Al aplicar de verdad un paquete de capacidad (tras la fusión) o al orquestar varios pasos de capacidad en secuencia.",
          properties: ["preview/apply/rollback", "Journal append-only de cada transacción", "Rollback dirigido por ETag de cada documento afectado"],
          relationships: ["Backend del módulo de navegación CAPABILITIES ('Project preparation'), más allá de la mera lectura de preparación"],
          cardinality: "1 transacción ↔ N documentos afectados, cada uno con su propio ETag",
          io: { inputs: "Plan de pasos de capacidad", outputs: "Documentos modificados + registro de transacción" },
          lifecycle: "Si algo falla a mitad de la transacción, el rollback deshace solo lo que esa transacción cambió, identificado por ETag.",
          example: "Aplicar un paquete de capacidad y, al detectar un problema después, revertirlo limpiamente desde el journal.",
          evidence: [{ path: "src/main/java/com/example/editor/portability/transaction", line: 1, label: "CapabilityLayerTransactionService" }]
        }
      ],
      authoring: {
        summary: "Es el backend real del módulo de navegación 'Preparación del proyecto' (CAPABILITIES), más allá de la simple cuadrícula de activar/desactivar capacidades documentada en Gestión de Proyectos: aquí es donde esas capacidades se empaquetan, versionan y fusionan como unidades transaccionales con historial y reversión.",
        elements: [
          { element: "Exportar un paquete de capacidad", requiredness: "OPTIONAL", modes: [
            { mode: "Selección de capas + exportación", detail: "Elegir qué capas de capacidad incluir y generar el archivo portable con sus dependencias resueltas." }
          ]},
          { element: "Importar/fusionar un paquete de capacidad", requiredness: "CONDITIONAL", modes: [
            { mode: "Vista previa de fusión + decisión por conflicto", detail: "Previsualizar antes de aplicar nada; cada conflicto detectado requiere una decisión explícita." },
            { mode: "Transacción con preview/apply/rollback", detail: "Aplicar el plan resultante como una transacción con journal, reversible por ETag si algo sale mal después." }
          ]}
        ]
      },
      level3: "Un paquete de capacidad agrupa varias capas (metamodelo, gramática, editor, validaciones…) con sus dependencias resueltas. Fusionarlo con las capas ya existentes de un proyecto pasa primero por detección de conflictos y vista previa, nunca por una escritura directa. Aplicar la fusión (u orquestar cualquier secuencia de pasos de capacidad) es siempre una transacción con journal append-only y rollback dirigido por ETag — no una simple sobrescritura de ficheros.",
      level4: "export → (selección de capas + resolución de dependencias) → archivo portable. import/merge → detección de conflictos → vista previa de fusión → decisión explícita por conflicto → plan de pasos de capacidad → transacción (preview → apply) → journal de la transacción, consultable para rollback posterior.",
      level5: {
        incoming: ["Project Management (las capacidades activas del proyecto son la entrada de qué se puede empaquetar)"],
        outgoing: ["Cualquier capacidad cuya capa se vea afectada por la fusión/transacción aplicada (metamodelo, validación, editor, etc.)"]
      },
      level6: {
        runtime: "CapabilityPackageArchiveService empaqueta; CapabilitySemanticMergeService detecta/previsualiza conflictos; CapabilityOrchestrationService aplica secuencias; CapabilityLayerTransactionService gestiona preview/apply/rollback con journal.",
        services: ["CapabilityPackageArchiveService", "CapabilitySemanticMergeService", "CapabilityOrchestrationService", "CapabilityLayerTransactionService"],
        apis: ["/api/capability-packages/*", "/api/projects/portable (empaquetado de proyecto completo, controlador separado)"],
        sources: [{ path: "src/main/java/com/example/editor/portability", line: 1, label: "portability package (archive/merge/orchestration/transaction)" }]
      }
    },
    en: {
      level1: {
        what: "Packages, versions, semantically merges and transactionally applies/rolls back a project's 'capability layers' (metamodel+grammar+editor+validations+…) as a portable package.",
        value: "Lets a project's set of capabilities move, be shared, or be safely updated across environments, with real undo if something goes wrong — not a plain file copy.",
        inputs: ["The source project's active capability layers", "A capability package to import/merge (local or remote)"],
        outputs: ["An exported capability-package file", "An applied (or rolled-back) merge decision with an append-only journal"]
      },
      level2: [
        {
          name: "Capability package (CapabilityPackageArchiveService)",
          purpose: "Packages a set of capability layers (metamodel, grammar, editor, validations, etc.) as a portable archive with dependencies resolved.",
          whenUsed: "To export or import a project's set of capabilities as one versioned unit.",
          properties: ["Dependency resolution across layers when packaging", "A declared compatibility/version"],
          relationships: ["A separate whole-project packaging path also exists (ProjectPortablePackageController), distinct from capability-layer packaging"],
          cardinality: "1 project ↔ N exportable capability packages (one per chosen layer set)",
          io: { inputs: "Capability-layer selection", outputs: "A portable archive" },
          lifecycle: "The archive can later be imported/merged into another project.",
          example: "Exporting a project's Validation + Editor layers to reuse them in another project sharing the same metamodel.",
          evidence: [{ path: "src/main/java/com/example/editor/portability/archive", line: 1, label: "CapabilityPackageArchiveService" }]
        },
        {
          name: "Semantic merge (CapabilitySemanticMergeService)",
          purpose: "Detects conflicts between two capability packages and lets the user preview and decide how to merge them.",
          whenUsed: "When importing a capability package into a project that already has potentially overlapping layers of its own.",
          properties: ["Conflict detection", "Merge preview", "Explicit per-conflict decision"],
          relationships: ["Relies on CapabilityOrchestrationService to apply the resulting step sequence"],
          cardinality: "1 merge ↔ N detected conflicts ↔ N decisions",
          io: { inputs: "Two capability packages to merge", outputs: "A merge preview + application plan" },
          lifecycle: "Nothing is applied until the merge plan is explicitly confirmed.",
          example: "Importing a package with updated validation rules, reviewing which rules clash with the existing ones.",
          evidence: [{ path: "src/main/java/com/example/editor/portability/merge", line: 1, label: "CapabilitySemanticMergeService" }]
        },
        {
          name: "Layer transaction (CapabilityLayerTransactionService)",
          purpose: "Applies a sequence of capability steps with preview, real apply and rollback, backed by an append-only journal and per-document ETag rollback.",
          whenUsed: "When actually applying a capability package (after merging) or orchestrating several capability steps in sequence.",
          properties: ["preview/apply/rollback", "An append-only journal of every transaction", "ETag-directed rollback per affected document"],
          relationships: ["The backend of the CAPABILITIES ('Project preparation') navigation module, beyond mere readiness reading"],
          cardinality: "1 transaction ↔ N affected documents, each with its own ETag",
          io: { inputs: "A plan of capability steps", outputs: "Modified documents + a transaction record" },
          lifecycle: "If something fails mid-transaction, rollback undoes only what that transaction changed, identified by ETag.",
          example: "Applying a capability package and, on detecting a problem afterward, cleanly reverting it from the journal.",
          evidence: [{ path: "src/main/java/com/example/editor/portability/transaction", line: 1, label: "CapabilityLayerTransactionService" }]
        }
      ],
      authoring: {
        summary: "This is the real backend of the 'Project preparation' (CAPABILITIES) navigation module, beyond the simple enable/disable capability grid documented under Project Management: this is where those capabilities get packaged, versioned and merged as transactional, history-backed, reversible units.",
        elements: [
          { element: "Exporting a capability package", requiredness: "OPTIONAL", modes: [
            { mode: "Layer selection + export", detail: "Choose which capability layers to include and generate the portable archive with dependencies resolved." }
          ]},
          { element: "Importing/merging a capability package", requiredness: "CONDITIONAL", modes: [
            { mode: "Merge preview + per-conflict decision", detail: "Preview before applying anything; every detected conflict requires an explicit decision." },
            { mode: "Transaction with preview/apply/rollback", detail: "Apply the resulting plan as a journaled transaction, ETag-reversible if something goes wrong afterward." }
          ]}
        ]
      },
      level3: "A capability package groups several layers (metamodel, grammar, editor, validations…) with dependencies resolved. Merging it with a project's existing layers goes through conflict detection and preview first, never a direct write. Applying the merge (or orchestrating any capability-step sequence) is always a journaled, ETag-directed-rollback transaction — not a plain file overwrite.",
      level4: "export → (layer selection + dependency resolution) → portable archive. import/merge → conflict detection → merge preview → explicit per-conflict decision → a plan of capability steps → transaction (preview → apply) → a transaction journal, queryable for later rollback.",
      level5: {
        incoming: ["Project Management (the project's active capabilities are the input for what can be packaged)"],
        outgoing: ["Any capability whose layer is affected by the applied merge/transaction (metamodel, validation, editor, etc.)"]
      },
      level6: {
        runtime: "CapabilityPackageArchiveService packages; CapabilitySemanticMergeService detects/previews conflicts; CapabilityOrchestrationService applies sequences; CapabilityLayerTransactionService manages preview/apply/rollback with a journal.",
        services: ["CapabilityPackageArchiveService", "CapabilitySemanticMergeService", "CapabilityOrchestrationService", "CapabilityLayerTransactionService"],
        apis: ["/api/capability-packages/*", "/api/projects/portable (whole-project packaging, separate controller)"],
        sources: [{ path: "src/main/java/com/example/editor/portability", line: 1, label: "portability package (archive/merge/orchestration/transaction)" }]
      }
    }
  }

};

window.PROCESSES = [

  {
    id: "cap_metamodel_lifecycle",
    capabilities: ["metamodel"],
    es: { title: "Definir, importar o recuperar un metamodelo y guardarlo", trigger: "El usuario abre el Diseñador de Metamodelos para crear uno nuevo, importar uno existente, o recuperarlo de una gramática/proyecto externo.", startState: "Ningún metamodelo abierto, o uno ya guardado para seguir editando." },
    en: { title: "Define, import or recover a metamodel and save it", trigger: "The user opens the Metamodel Designer to create a new one, import an existing one, or recover it from a grammar/external project.", startState: "No metamodel open, or an already-saved one to keep editing." },
    stages: [
      { id: "s1", capability: "metamodel", optional: false, es: { label: "Crear o importar", detail: "Añadir clases/enumerados/tipos de dato en el lienzo (clic en paleta), o importar desde un archivo/carpeta con análisis asíncrono por etapas." }, en: { label: "Create or import", detail: "Add classes/enums/data types on the canvas (palette click), or import from a file/folder with staged async analysis." } },
      { id: "s2", capability: "metamodel", optional: false, es: { label: "Detallar en el panel de propiedades", detail: "Atributos, operaciones, relaciones (6 tipos) con validación en vivo de conexión (ciclos de herencia, compatibilidad clase/interfaz)." }, en: { label: "Detail in the property panel", detail: "Attributes, operations, relations (6 kinds) with live connection validation (inheritance cycles, class/interface compatibility)." } },
      { id: "s3", capability: "metamodel", optional: true, es: { label: "Organizar el lienzo", detail: "Auto-layout ELK o layout completo vía backend; enrutado de relaciones." }, en: { label: "Organize the canvas", detail: "ELK auto-layout or full backend layout; relation routing." } },
      { id: "s4", capability: "metamodel", optional: false, es: { label: "Guardar", detail: "Dispara SIEMPRE validación estructural bloqueante (prepareForSave)." }, en: { label: "Save", detail: "ALWAYS triggers blocking structural validation (prepareForSave)." } },
      { id: "s5", capability: "metamodel", optional: true, es: { label: "Exportar / generar proyecto completo", detail: "Formatos dinámicos (ecore/json/drawio recomendados, antlr, java, svg…) o un proyecto completo (p. ej. eclipse-glsp), bloqueado hasta que la validación de preparación-para-generación pase." }, en: { label: "Export / generate complete project", detail: "Dynamic formats (ecore/json/drawio recommended, antlr, java, svg…) or a complete project (e.g. eclipse-glsp), blocked until generation-readiness validation passes." } }
    ],
    branches: [
      { fromStageId: "s1", es: { label: "Alternativa: el metamodelo también puede llegar ya proyectado desde Gramática o materializado desde Recuperación Externa, sin pasar por creación manual" }, en: { label: "Alternative: the metamodel can also arrive already projected from Grammar or materialized from External Recovery, without manual creation" } }
    ],
    result: { es: "Un MetaModel guardado y validado, listo para que Editor de Modelos/Validación/Transformación/Integración lo usen.", en: "A saved, validated MetaModel, ready for Model Editor/Validation/Transformation/Integration to use." },
    evidence: [{ path: "src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java", line: 147, label: "prepareForSave" }]
  },

  {
    id: "cap_model_editor_lifecycle",
    capabilities: ["model_editor"],
    es: { title: "Diseñar un editor y luego crear/editar un modelo con él", trigger: "El usuario necesita un editor de instancias para un metamodelo ya guardado.", startState: "Un MetaModel guardado, sin editor compilado todavía." },
    en: { title: "Design an editor and then create/edit a model with it", trigger: "The user needs an instance editor for an already-saved metamodel.", startState: "A saved MetaModel, with no compiled editor yet." },
    stages: [
      { id: "s1", capability: "model_editor", optional: false, es: { label: "Configurar representaciones y mapeos", detail: "Árbol+formulario: representación, NodeMapping/EdgeMapping por clase/relación; las herramientas de crear-nodo/crear-arista se generan automáticamente al mapear." }, en: { label: "Configure representations and mappings", detail: "Tree+form: representation, NodeMapping/EdgeMapping per class/relation; create-node/create-edge tools auto-generate when mapped." } },
      { id: "s2", capability: "model_editor", optional: true, es: { label: "Estilos condicionales", detail: "Reglas con condición (mismo ExpressionBuilder de Validación) y prioridad; detección de condiciones duplicadas." }, en: { label: "Conditional styles", detail: "Rules with a condition (same ExpressionBuilder as Validation) and priority; duplicate-condition detection." } },
      { id: "s3", capability: "model_editor", optional: false, es: { label: "Compilar el editor", detail: "Produce un runtime ejecutable; compilar NUNCA modifica la definición de autoría." }, en: { label: "Compile the editor", detail: "Produces an executable runtime; compiling NEVER modifies the authoring definition." } },
      { id: "s4", capability: "model_editor", optional: false, es: { label: "Abrir una sesión de modelo", detail: "Crear en blanco o importar por EMF; el lienzo real (dirigido por el editor compilado) ocupa todo el workbench." }, en: { label: "Open a model session", detail: "Start blank or import via EMF; the real canvas (driven by the compiled editor) takes over the whole workbench." } },
      { id: "s5", capability: "model_editor", optional: true, es: { label: "Crear/conectar/editar", detail: "Herramientas de paleta ejecutables; formularios generados por clase; guardado explícito u autosave según la política del editor." }, en: { label: "Create/connect/edit", detail: "Executable palette tools; per-class generated forms; explicit save or autosave per the editor's policy." } }
    ],
    branches: [
      { fromStageId: "s3", es: { label: "Si no hay editor compilado disponible, la sesión usa el lienzo básico de respaldo (paleta+React Flow sobre la antigua notación), no el runtime real" }, en: { label: "If no compiled editor is available, the session falls back to the basic canvas (palette+React Flow over the older notation), not the real runtime" } }
    ],
    result: { es: "Un modelo instancia editado, con su layout persistido por separado del contenido semántico.", en: "An edited model instance, with its layout persisted separately from the semantic content." },
    evidence: [{ path: "src/main/java/com/example/editor/definition", line: 1, label: "EditorDefinitionCompiler / EditorRuntimeSession" }]
  },

  {
    id: "cap_grammar_lifecycle",
    capabilities: ["grammar"],
    es: { title: "Escribir una gramática, compilarla y usar su editor textual", trigger: "El usuario quiere definir un lenguaje por su sintaxis textual en vez de por formulario de clases.", startState: "Ningún documento de gramática, o uno ya existente para seguir editando." },
    en: { title: "Write a grammar, compile it and use its textual editor", trigger: "The user wants to define a language by its textual syntax instead of a class form.", startState: "No grammar document, or an already-existing one to keep editing." },
    stages: [
      { id: "s1", capability: "grammar", optional: false, es: { label: "Escribir en Monaco", detail: "Sintaxis real de Langium/Xtext/ANTLR; diagnósticos locales instantáneos + confirmados por backend fusionados; quick-fixes nativos de Monaco." }, en: { label: "Write in Monaco", detail: "Real Langium/Xtext/ANTLR syntax; instant local diagnostics merged with backend-confirmed ones; native Monaco quick-fixes." } },
      { id: "s2", capability: "grammar", optional: true, es: { label: "Revisar el diagrama inferido", detail: "Proyección de solo lectura (reutiliza el lienzo de Metamodelos) que se recalcula automáticamente al editar." }, en: { label: "Review the inferred diagram", detail: "A read-only projection (reusing the Metamodel canvas) that recomputes automatically as text is edited." } },
      { id: "s3", capability: "grammar", optional: false, es: { label: "Compilar (12 etapas)", detail: "Si GENERATE_SEMANTIC_ARTIFACTS no llega a COMPLETE, toda la compilación falla." }, en: { label: "Compile (12 stages)", detail: "If GENERATE_SEMANTIC_ARTIFACTS does not reach COMPLETE, the whole compilation fails." } },
      { id: "s4", capability: "grammar", optional: true, es: { label: "Usar el editor textual generado", detail: "Escribir texto conforme a la gramática compilada; parse() re-analiza en borrador, save() promueve a canónico tras validar." }, en: { label: "Use the generated textual editor", detail: "Write text conforming to the compiled grammar; parse() re-analyzes as a draft, save() promotes to canonical after validating." } }
    ],
    branches: [
      { fromStageId: "s1", es: { label: "Cambiar de tecnología con contenido ya escrito abre una vista previa de conversión de plataforma (no un asistente de importación)" }, en: { label: "Changing technology with content already written opens a platform-conversion preview (not an import wizard)" } }
    ],
    result: { es: "Un metamodelo inferido real disponible para el resto de HYDRA, y un editor textual utilizable.", en: "A real inferred metamodel available to the rest of HYDRA, and a usable textual editor." },
    evidence: [{ path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarCompilationService" }]
  },

  {
    id: "cap_validation_authoring_lifecycle",
    capabilities: ["validation"],
    es: { title: "Definir una regla de validación y, opcionalmente, su quick fix", trigger: "El usuario quiere expresar una restricción de calidad sobre el metamodelo.", startState: "Un metamodelo guardado sobre el que definir la regla." },
    en: { title: "Define a validation rule and, optionally, its quick fix", trigger: "The user wants to express a quality constraint over the metamodel.", startState: "A saved metamodel to define the rule against." },
    stages: [
      { id: "s1", capability: "validation", optional: false, es: { label: "Identidad y contexto", detail: "Nombre, severidad, contexto (clase/atributo/relación/modelo/…)." }, en: { label: "Identity and context", detail: "Name, severity, context (class/attribute/relation/model/…)." } },
      { id: "s2", capability: "validation", optional: false, es: { label: "Construir la condición", detail: "Guiado por cláusulas o árbol de expresión avanzado — nunca texto libre OCL/EVL." }, en: { label: "Build the condition", detail: "Clause-guided or advanced expression tree — never free-text OCL/EVL." } },
      { id: "s3", capability: "validation", optional: true, es: { label: "Asociar un quick fix existente", detail: "Cuadrícula de casillas; o crear uno nuevo (asistente de 5 pasos: identidad/seguridad/parámetros/operaciones/revisión)." }, en: { label: "Associate an existing quick fix", detail: "Checkbox grid; or create a new one (5-step wizard: identity/safety/parameters/operations/review)." } },
      { id: "s4", capability: "validation", optional: false, es: { label: "Guardar la regla", detail: "Queda disponible para evaluarse en cualquier sesión de modelo de ese metamodelo." }, en: { label: "Save the rule", detail: "Becomes available for evaluation in any model session of that metamodel." } }
    ],
    branches: [
      { fromStageId: "s3", es: { label: "Sin quick fix asociado, la regla sigue siendo válida: solo informa, no ofrece reparación" }, en: { label: "Without an associated quick fix, the rule is still valid: it only reports, offering no repair" } }
    ],
    result: { es: "Una regla activa (y, opcionalmente, un quick fix enlazado) lista para el ciclo de validación de instancias.", en: "An active rule (and, optionally, a linked quick fix) ready for the instance-validation cycle." },
    evidence: [{ path: "src/main/java/com/example/editor/validation/validation/ValidationRule.java", line: 12, label: "ValidationRule" }]
  },

  {
    id: "cap_transformation_authoring_lifecycle",
    capabilities: ["transformation"],
    es: { title: "Definir una transformación de principio a fin (alcance → reglas → verificación)", trigger: "El usuario necesita derivar o sincronizar un modelo destino a partir de uno origen.", startState: "Al menos un metamodelo origen (y, si no es T2M desde cero, uno destino) ya guardados." },
    en: { title: "Define a transformation end-to-end (scope → rules → verify)", trigger: "The user needs to derive or synchronize a target model from a source one.", startState: "At least a source metamodel (and, unless starting T2M from scratch, a target one) already saved." },
    stages: [
      { id: "s1", capability: "transformation", optional: false, es: { label: "Alcance", detail: "Tipo (M2M/M2T/T2M), direccionalidad, metamodelos origen/destino." }, en: { label: "Scope", detail: "Kind (M2M/M2T/T2M), directionality, source/target metamodels." } },
      { id: "s2", capability: "transformation", optional: false, es: { label: "Autoría de reglas (taller de 3 zonas)", detail: "Seleccionar clases en ambos árboles + 'Mapear propiedades'; bindings DIRECT/CONSTANT/EXPRESSION por atributo, TRACE_EQUIVALENT implícito por relación." }, en: { label: "Rule authoring (3-zone workspace)", detail: "Select classes in both trees + 'Map properties'; DIRECT/CONSTANT/EXPRESSION bindings per attribute, implicit TRACE_EQUIVALENT per relation." } },
      { id: "s3", capability: "transformation", optional: true, es: { label: "Guard de la regla", detail: "Condición opcional de aplicación, mismo constructor de expresiones que Validación." }, en: { label: "Rule guard", detail: "Optional application condition, same expression builder as Validation." } },
      { id: "s4", capability: "transformation", optional: false, es: { label: "Verificar", detail: "Diagnósticos + vista previa de ejecución antes de dar por buena la definición." }, en: { label: "Verify", detail: "Diagnostics + execution preview before considering the definition good." } },
      { id: "s5", capability: "transformation", optional: true, es: { label: "Configurar sincronización / ejecución nativa", detail: "Política de conflicto si es bidireccional; motor de toolchain nativo si se requiere paridad externa." }, en: { label: "Configure sync / native execution", detail: "Conflict policy if bidirectional; native toolchain engine if external parity is required." } }
    ],
    branches: [],
    result: { es: "Una TransformationDefinitionDocument lista para ejecutarse (ver el proceso cruzado de ejecución).", en: "A TransformationDefinitionDocument ready to run (see the cross-capability execution process)." },
    evidence: [{ path: "src/main/java/com/example/editor/transformation/core", line: 9, label: "TransformationDefinitionDocument" }]
  },

  {
    id: "cap_events_authoring_lifecycle",
    capabilities: ["events"],
    es: { title: "Definir un tipo de evento y, opcionalmente, un flujo (EventFlow) para él", trigger: "El usuario necesita modelar algo que 'puede pasar' en el dominio, y opcionalmente orquestar una reacción.", startState: "Un metamodelo guardado (los eventos pueden referenciar sus elementos)." },
    en: { title: "Define an event type and, optionally, a flow (EventFlow) for it", trigger: "The user needs to model something that 'can happen' in the domain, and optionally orchestrate a reaction.", startState: "A saved metamodel (events can reference its elements)." },
    stages: [
      { id: "s1", capability: "events", optional: false, es: { label: "Definir el tipo de evento", detail: "Contrato, alcance (orígenes/sujetos), campos de datos, ciclo de vida." }, en: { label: "Define the event type", detail: "Contract, scope (sources/subjects), data fields, lifecycle." } },
      { id: "s2", capability: "events", optional: true, es: { label: "Diseñar un EventFlow", detail: "Lienzo real de arrastrar/soltar/conectar con los 28 tipos de nodo (decisión, espera, reintento, compensación…)." }, en: { label: "Design an EventFlow", detail: "A real drag/drop/connect canvas with all 28 node kinds (decision, wait, retry, compensation…)." } },
      { id: "s3", capability: "events", optional: true, es: { label: "Definir bindings evento→evento", detail: "Formulario, o dibujando una conexión en el lienzo de 'Arquitectura de eventos'." }, en: { label: "Define event→event bindings", detail: "A form, or by drawing a connection on the 'Event architecture' canvas." } },
      { id: "s4", capability: "events", optional: false, es: { label: "Guardar", detail: "El tipo (y flujo/bindings) queda disponible para publicarse en el bus cuando una sesión de editor lo active." }, en: { label: "Save", detail: "The type (and flow/bindings) becomes available to publish on the bus once an editor session enables it." } }
    ],
    branches: [],
    result: { es: "Eventos/flujos definidos, listos para el proceso cruzado de publicación→EventFlow→Gamificación.", en: "Defined events/flows, ready for the cross-capability publish→EventFlow→Gamification process." },
    evidence: [{ path: "src/main/java/com/example/editor/event/EventTypeDefinition.java", line: 11, label: "EventTypeDefinition" }]
  },

  {
    id: "cap_gamification_authoring_lifecycle",
    capabilities: ["gamification"],
    es: { title: "Definir el catálogo de progresión y las reglas de evidencia", trigger: "El usuario quiere añadir motivación/incentivos sobre eventos ya verificados.", startState: "Al menos un tipo de evento ya definido para usar como evidencia." },
    en: { title: "Define the progression catalog and evidence rules", trigger: "The user wants to add motivation/incentives on top of already-verified events.", startState: "At least one event type already defined to use as evidence." },
    stages: [
      { id: "s1", capability: "gamification", optional: false, es: { label: "Catálogo de progresión", detail: "Tipos de punto, niveles (umbral + tipo de punto), logros ('oculto hasta conseguirse'), retos (conteo objetivo), recompensas (payload JSON libre)." }, en: { label: "Progression catalog", detail: "Point types, levels (threshold + point type), achievements ('hidden until earned'), challenges (target count), rewards (free JSON payload)." } },
      { id: "s2", capability: "gamification", optional: false, es: { label: "Regla de evidencia", detail: "Evento verificado + condición opcional + efecto Otorgar (puntos + logro/reto/recompensa a la vez) o Penalizar (solo puntos) + política de repetición." }, en: { label: "Evidence rule", detail: "Verified event + optional condition + Award effect (points + achievement/challenge/reward at once) or Penalty (points only) + repeat policy." } },
      { id: "s3", capability: "gamification", optional: true, es: { label: "Revisar en el diagrama", detail: "Vista de diagrama con la misma información como nodos conectados (evidencia→regla→recompensas)." }, en: { label: "Review in the diagram", detail: "A diagram view with the same information as connected nodes (evidence→rule→rewards)." } }
    ],
    branches: [],
    result: { es: "Reglas de evidencia activas, listas para que el runtime las compare contra eventos reales.", en: "Active evidence rules, ready for the runtime to match against real events." },
    evidence: [{ path: "src/main/java/com/example/editor/gamification", line: 1, label: "GamificationRuntimeService" }]
  },

  {
    id: "cap_integration_lifecycle",
    capabilities: ["integration"],
    es: { title: "Configurar y entregar un perfil de integración (9 etapas)", trigger: "El usuario quiere proyectar su metamodelo/modelo hacia una tecnología externa.", startState: "Un metamodelo (y opcionalmente modelo) ya guardados." },
    en: { title: "Configure and deliver an integration profile (9 stages)", trigger: "The user wants to project their metamodel/model into an external technology.", startState: "An already-saved metamodel (and optionally model)." },
    stages: [
      { id: "s1", capability: "integration", optional: false, es: { label: "Configurar", detail: "Tecnología destino, modo de entrega, documentos de entrada requeridos." }, en: { label: "Configure", detail: "Target technology, delivery mode, required input documents." } },
      { id: "s2", capability: "integration", optional: false, es: { label: "Analizar", detail: "Compatibilidad de artefactos, con diagnóstico 'por qué/cómo resolver/alternativa'." }, en: { label: "Analyze", detail: "Artifact compatibility, with 'why/how to resolve/alternative' diagnostics." } },
      { id: "s3", capability: "integration", optional: false, es: { label: "Generar / Verificar", detail: "Compilar el módulo de integración; verificar estructura o toolchain nativo." }, en: { label: "Generate / Verify", detail: "Compile the integration module; verify structure or native toolchain." } },
      { id: "s4", capability: "integration", optional: false, es: { label: "Previsualizar / Aplicar", detail: "Ver el plan de cambios de fichero antes de aplicar la inyección al proyecto host." }, en: { label: "Preview / Apply", detail: "See the file-change plan before applying the injection to the host project." } },
      { id: "s5", capability: "integration", optional: true, es: { label: "Round-trip", detail: "Detectar y resolver conflictos con cambios nativos externos (ver proceso cruzado de transformación/integración)." }, en: { label: "Round-trip", detail: "Detect and resolve conflicts with external native changes (see the cross-capability transformation/integration process)." } }
    ],
    branches: [
      { fromStageId: "s3", es: { label: "Cualquier etapa puede quedar BLOCKED según el estado de preparación, deteniendo el asistente antes de EXECUTE/APPLY" }, en: { label: "Any stage can end up BLOCKED per readiness state, halting the wizard before EXECUTE/APPLY" } }
    ],
    result: { es: "Un perfil de integración entregado, con su mapeo de artefactos nativos registrado.", en: "A delivered integration profile, with its native-artifact mapping recorded." },
    evidence: [{ path: "src/main/java/com/example/editor/integration/workflow/TechnologyDeliveryReadiness.java", line: 1, label: "TechnologyWorkflowStage" }]
  },

  {
    id: "cap_recovery_lifecycle",
    capabilities: ["recovery"],
    es: { title: "Recuperar un proyecto externo de principio a fin (asistente + workspace avanzado)", trigger: "El usuario apunta HYDRA a un proyecto/update-site externo.", startState: "Un proyecto externo accesible (archivo, carpeta, repositorio o p2 remoto)." },
    en: { title: "Recover an external project end-to-end (wizard + advanced workspace)", trigger: "The user points HYDRA at an external project/update-site.", startState: "An accessible external project (file, folder, repository or remote p2)." },
    stages: [
      { id: "s1", capability: "recovery", optional: false, es: { label: "Origen + plan de recuperación", detail: "Elegir fuente y qué capas neutrales recuperar (paleta/representaciones/mapeos)." }, en: { label: "Source + recovery plan", detail: "Choose the source and which neutral layers to recover (palette/representations/mappings)." } },
      { id: "s2", capability: "recovery", optional: false, es: { label: "Analizar", detail: "Genera candidatos de metamodelo por nivel de confianza (5 niveles) con evidencia por elemento." }, en: { label: "Analyze", detail: "Generates confidence-ranked metamodel candidates (5 tiers) with per-element evidence." } },
      { id: "s3", capability: "recovery", optional: true, es: { label: "Reconciliar conflictos", detail: "Decisión explícita por conflicto entre candidatos; vista previa reconciliada." }, en: { label: "Reconcile conflicts", detail: "Explicit per-conflict decision across candidates; a reconciled preview." } },
      { id: "s4", capability: "recovery", optional: false, es: { label: "Materializar (preview → persist)", detail: "Nunca se persiste nada hasta confirmar explícitamente." }, en: { label: "Materialize (preview → persist)", detail: "Nothing is ever persisted until explicitly confirmed." } },
      { id: "s5", capability: "recovery", optional: true, es: { label: "Gobernanza / publicación verificada", detail: "5 bloques (fusión semántica, espejo p2, banco de adaptador, rendimiento, puerta de publicación) con atestación descargable." }, en: { label: "Governance / verified publication", detail: "5 blocks (semantic merge, p2 mirror, adapter bench, performance, publication gate) with a downloadable attestation." } }
    ],
    branches: [
      { fromStageId: "s4", es: { label: "Rollback disponible tras persistir, si el resultado lo permite" }, en: { label: "Rollback available after persisting, if the result allows it" } }
    ],
    result: { es: "Un metamodelo (y, opcionalmente, reglas) recuperados y disponibles, con posibilidad de deshacer.", en: "A recovered, available metamodel (and, optionally, rules), with the ability to undo." },
    evidence: [{ path: "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java", line: 1, label: "Materialization preview/persist/rollback" }]
  },

  {
    id: "cap_publication_lifecycle",
    capabilities: ["publication"],
    es: { title: "Generar código, empaquetar y publicar una distribución", trigger: "El usuario quiere producir y distribuir el resultado de un metamodelo+modelo+editor.", startState: "Un metamodelo+modelo+editor ya validados." },
    en: { title: "Generate code, package and publish a distribution", trigger: "The user wants to produce and distribute the output of a metamodel+model+editor.", startState: "An already-validated metamodel+model+editor." },
    stages: [
      { id: "s1", capability: "publication", optional: true, es: { label: "Generar código", detail: "Diálogo modal contextual desde el Editor de Modelos; target/lenguaje + opciones por característica." }, en: { label: "Generate code", detail: "A contextual modal from the Model Editor; target/language + per-feature options." } },
      { id: "s2", capability: "publication", optional: true, es: { label: "Empaquetar standalone", detail: "Asistente: carpeta → identidad/versión → plataformas (PWA/Tauri) → materializar y validar → empaquetar ZIP." }, en: { label: "Package standalone", detail: "Wizard: folder → identity/version → platforms (PWA/Tauri) → materialize and validate → package ZIP." } },
      { id: "s3", capability: "publication", optional: false, es: { label: "Validar el release", detail: "Pase/fallo genérico con diagnósticos — distinto de la puerta de 5 comprobaciones de Recuperación Externa." }, en: { label: "Validate the release", detail: "Generic pass/fail with diagnostics — distinct from External Recovery's 5-check gate." } },
      { id: "s4", capability: "publication", optional: false, es: { label: "Publicar", detail: "Canal de entrega + firma; comprobador de actualización por canal disponible aparte." }, en: { label: "Publish", detail: "Delivery channel + signing; a per-channel update checker is available separately." } }
    ],
    branches: [],
    result: { es: "Un artefacto publicado y, opcionalmente, un paquete standalone distribuible.", en: "A published artifact and, optionally, a distributable standalone package." },
    evidence: [{ path: "src/main/java/com/example/editor", line: 1, label: "Publication/distribution services" }]
  },

  {
    id: "cap_project_lifecycle",
    capabilities: ["project"],
    es: { title: "Abrir, preparar y organizar un proyecto", trigger: "El usuario abre HYDRA o necesita cambiar de proyecto activo.", startState: "El panel Home, con o sin proyecto activo." },
    en: { title: "Open, prepare and organize a project", trigger: "The user opens HYDRA or needs to switch the active project.", startState: "The Home panel, with or without an active project." },
    stages: [
      { id: "s1", capability: "project", optional: false, es: { label: "Abrir / crear / importar", detail: "Desde Home: continuar el reciente, nuevo proyecto, o importar (carpeta/ZIP/remoto/archivo externo)." }, en: { label: "Open / create / import", detail: "From Home: continue a recent one, create new, or import (folder/ZIP/remote/external archive)." } },
      { id: "s2", capability: "project", optional: true, es: { label: "Preparar capacidades", detail: "Activar/desactivar áreas del proyecto en el diálogo de capacidades, con dependencias automáticas." }, en: { label: "Prepare capabilities", detail: "Enable/disable project areas in the capabilities dialog, with automatic dependencies." } },
      { id: "s3", capability: "project", optional: false, es: { label: "Navegar el árbol", detail: "Abrir/organizar archivos; cada capacidad habilitada aporta sus propios tipos de artefacto al árbol." }, en: { label: "Navigate the tree", detail: "Open/organize files; each enabled capability contributes its own artifact kinds to the tree." } },
      { id: "s4", capability: "project", optional: true, es: { label: "Exportar", detail: "Selección como ZIP local o a un repositorio remoto configurado." }, en: { label: "Export", detail: "Selection as a local ZIP or to a configured remote repository." } }
    ],
    branches: [],
    result: { es: "Un proyecto activo y organizado, con las capacidades correctas habilitadas para el trabajo que se quiere hacer.", en: "An active, organized project, with the right capabilities enabled for the intended work." },
    evidence: [{ path: "src/main/java/com/example/editor/project", line: 1, label: "ProjectIndexService" }]
  },

  {
    id: "cap_toolchains_check",
    capabilities: ["toolchains"],
    es: { title: "Comprobar o instalar una herramienta/motor externo", trigger: "Antes de depender de un build externo, o antes de ejecutar una transformación con paridad nativa.", startState: "Ninguna comprobación realizada todavía en esta sesión." },
    en: { title: "Check or install an external tool/engine", trigger: "Before depending on an external build, or before running a transformation with native parity.", startState: "No check performed yet in this session." },
    stages: [
      { id: "s1", capability: "toolchains", optional: true, es: { label: "Sondear herramientas genéricas", detail: "Centro 'Available tools': ejecutar sondeos de versión (java/mvn/node/npm/python) por tecnología." }, en: { label: "Probe generic tools", detail: "'Available tools' center: run version probes (java/mvn/node/npm/python) per technology." } },
      { id: "s2", capability: "toolchains", optional: true, es: { label: "Diagnosticar un motor nativo", detail: "Para Transformación: comprobar toolAvailable/executionAvailable de un lenguaje de ejecución concreto." }, en: { label: "Diagnose a native engine", detail: "For Transformation: check toolAvailable/executionAvailable for a specific execution language." } },
      { id: "s3", capability: "toolchains", optional: true, es: { label: "Instalar/actualizar/eliminar", detail: "Elegir versión del catálogo de confianza; verificación SHA-256 por fichero; rollback automático si es incompatible." }, en: { label: "Install/update/remove", detail: "Pick a version from the trusted catalog; per-file SHA-256 verification; automatic rollback if incompatible." } }
    ],
    branches: [],
    result: { es: "Un estado de disponibilidad conocido, o un motor instalado listo para usarse.", en: "A known availability state, or an installed engine ready to use." },
    evidence: [{ path: "src/main/java/com/example/editor/transformation/nativeexec", line: 1, label: "TransformationToolchainProvisioningService" }]
  },

  {
    id: "cap_external_repo_connect",
    capabilities: ["external_repositories"],
    es: { title: "Conectar un repositorio externo y transferir/sincronizar contenido", trigger: "El usuario quiere respaldar, compartir o traer contenido de un proveedor externo.", startState: "Ningún perfil de conexión configurado todavía para ese proveedor." },
    en: { title: "Connect an external repository and transfer/sync content", trigger: "The user wants to back up, share or bring in content from an external provider.", startState: "No connection profile configured yet for that provider." },
    stages: [
      { id: "s1", capability: "external_repositories", optional: false, es: { label: "Crear perfil", detail: "Identidad + destino específico del proveedor (de 6 posibles)." }, en: { label: "Create profile", detail: "Identity + provider-specific target (of 6 possible)." } },
      { id: "s2", capability: "external_repositories", optional: false, es: { label: "Conectar (OAuth / credencial gestionada)", detail: "Ventana al proveedor real; HYDRA nunca ve la contraseña ni recibe el token en el navegador." }, en: { label: "Connect (OAuth / managed credential)", detail: "A window to the real provider; HYDRA never sees the password or receives the token in the browser." } },
      { id: "s3", capability: "external_repositories", optional: true, es: { label: "Operar (Git) o transferir (archivo)", detail: "Si es GitHub/GitLab/Git genérico: fetch/pull/push/merge con resolución visual de conflictos. Si no: exportar/importar proyecto o capa como archivo." }, en: { label: "Operate (Git) or transfer (file)", detail: "If GitHub/GitLab/generic Git: fetch/pull/push/merge with visual conflict resolution. Otherwise: export/import a project or layer as a file." } }
    ],
    branches: [
      { fromStageId: "s3", es: { label: "La importación de archivo aquí NUNCA dispara la materialización semántica de Recuperación Externa — son caminos separados" }, en: { label: "File import here NEVER triggers External Recovery's semantic materialization — these are separate paths" } }
    ],
    result: { es: "Contenido sincronizado con el proveedor externo, o un conflicto resuelto explícitamente.", en: "Content synchronized with the external provider, or an explicitly resolved conflict." },
    evidence: [{ path: "src/main/java/com/example/editor/remote/auth/RemoteAuthService.java", line: 38, label: "RemoteAuthService.start()" }]
  },

  {
    id: "cap_generated_project_run",
    capabilities: ["generated_projects"],
    es: { title: "Registrar y ejecutar el ciclo de vida de un proyecto generado", trigger: "Existen artefactos ya generados (editor standalone, servidor…) que hay que validar/abrir/lanzar.", startState: "Artefactos generados disponibles, aún no registrados como proyecto generado." },
    en: { title: "Register and run a generated project's lifecycle", trigger: "Already-generated artifacts (standalone editor, server…) exist that need validating/opening/launching.", startState: "Generated artifacts available, not yet registered as a generated project." },
    stages: [
      { id: "s1", capability: "generated_projects", optional: false, es: { label: "Registrar", detail: "ID/nombre/perfil de runtime/carpeta relativa." }, en: { label: "Register", detail: "ID/name/runtime profile/relative folder." } },
      { id: "s2", capability: "generated_projects", optional: true, es: { label: "Elegir etapas + simulación", detail: "Casillas GENERATE/VALIDATE/OPEN/LAUNCH (habilitadas según el perfil de runtime) + dry-run opcional." }, en: { label: "Choose stages + dry-run", detail: "GENERATE/VALIDATE/OPEN/LAUNCH checkboxes (enabled per runtime profile) + optional dry-run." } },
      { id: "s3", capability: "generated_projects", optional: false, es: { label: "Ejecutar", detail: "Resultado pasa/falla por etapa, con salida de comando/diagnósticos." }, en: { label: "Run", detail: "Pass/fail result per stage, with command output/diagnostics." } }
    ],
    branches: [
      { fromStageId: "s2", es: { label: "LAUNCH es la única etapa realmente opcional del enum; un proyecto puede quedar solo validado/abierto sin lanzarse" }, en: { label: "LAUNCH is the only truly optional enum stage; a project can remain only validated/opened without launching" } }
    ],
    result: { es: "Un informe de ejecución por etapa, y opcionalmente el proyecto abierto/lanzado.", en: "A per-stage execution report, and optionally the project opened/launched." },
    evidence: [{ path: "src/main/java/com/example/editor/generated/project", line: 1, label: "GeneratedProjectLifecycleService" }]
  },

  {
    id: "cap_portability_package_merge",
    capabilities: ["portability"],
    es: { title: "Empaquetar capas de capacidad, fusionarlas e importarlas con reversión", trigger: "El usuario quiere mover/compartir/actualizar el conjunto de capacidades de un proyecto.", startState: "Un proyecto con capas de capacidad activas que exportar, o un paquete externo que importar." },
    en: { title: "Package capability layers, merge them and import with rollback", trigger: "The user wants to move/share/update a project's set of capabilities.", startState: "A project with active capability layers to export, or an external package to import." },
    stages: [
      { id: "s1", capability: "portability", optional: true, es: { label: "Exportar", detail: "Elegir capas de capacidad; se resuelven dependencias entre ellas al empaquetar." }, en: { label: "Export", detail: "Choose capability layers; dependencies between them are resolved when packaging." } },
      { id: "s2", capability: "portability", optional: true, es: { label: "Detectar conflictos y previsualizar fusión", detail: "Al importar sobre capas ya existentes; nada se aplica todavía." }, en: { label: "Detect conflicts and preview merge", detail: "When importing over already-existing layers; nothing is applied yet." } },
      { id: "s3", capability: "portability", optional: false, es: { label: "Decidir por conflicto", detail: "Cada conflicto detectado requiere una decisión explícita del usuario." }, en: { label: "Decide per conflict", detail: "Every detected conflict requires an explicit user decision." } },
      { id: "s4", capability: "portability", optional: false, es: { label: "Aplicar como transacción", detail: "preview → apply, con journal append-only de cada documento afectado (por ETag)." }, en: { label: "Apply as a transaction", detail: "preview → apply, with an append-only journal of every affected document (by ETag)." } }
    ],
    branches: [
      { fromStageId: "s4", es: { label: "Si algo falla después, rollback deshace solo lo que esa transacción cambió" }, en: { label: "If something fails afterward, rollback undoes only what that transaction changed" } }
    ],
    result: { es: "Capas de capacidad empaquetadas, fusionadas y aplicadas de forma reversible.", en: "Capability layers packaged, merged and applied reversibly." },
    evidence: [{ path: "src/main/java/com/example/editor/portability/transaction", line: 1, label: "CapabilityLayerTransactionService" }]
  },

  {
    id: "validation_quickfix_cycle",
    capabilities: ["validation", "model_editor", "events", "gamification"],
    es: {
      title: "Validar un modelo, reparar con un quick fix y revalidar",
      trigger: "El usuario pide validar (o, si la política del editor lo permite, se dispara automáticamente al cambiar/abrir el modelo).",
      startState: "Una sesión de edición (EditorModelSession) con un CanonicalModelDocument abierto."
    },
    en: {
      title: "Validate a model, repair with a quick fix, and revalidate",
      trigger: "The user requests validation (or, if the editor's policy allows it, it fires automatically on model change/open).",
      startState: "An editing session (EditorModelSession) with an open CanonicalModelDocument."
    },
    stages: [
      { id: "s1", capability: "validation", optional: false, es: { label: "Evaluar reglas", detail: "ValidationEngine evalúa cada ValidationRule activa vía ExpressionEvaluator (semantic.expression) sobre el modelo instancia." }, en: { label: "Evaluate rules", detail: "ValidationEngine evaluates each active ValidationRule via ExpressionEvaluator (semantic.expression) over the model instance." } },
      { id: "s2", capability: "validation", optional: false, es: { label: "Emitir diagnósticos", detail: "Cada violación produce un ModelValidationDiagnostic con quickFixIds heredados de la regla." }, en: { label: "Emit diagnostics", detail: "Each violation produces a ModelValidationDiagnostic carrying quickFixIds inherited from the rule." } },
      { id: "s3", capability: "model_editor", optional: true, es: { label: "Previsualizar reparación", detail: "El usuario pide preview() de un quick fix (sin efectos secundarios)." }, en: { label: "Preview the repair", detail: "The user requests preview() of a quick fix (side-effect-free)." } },
      { id: "s4", capability: "validation", optional: true, es: { label: "Aplicar quick fix", detail: "apply() muta el modelo real, toma una instantánea de reversión y llama a orchestrator.validateLive(...) en la misma respuesta." }, en: { label: "Apply the quick fix", detail: "apply() mutates the real model, snapshots for rollback and calls orchestrator.validateLive(...) in the same response." } },
      { id: "s5", capability: "validation", optional: false, es: { label: "Revalidar", detail: "Se genera una nueva lista de diagnósticos reflejando el estado reparado." }, en: { label: "Revalidate", detail: "A new diagnostic list is generated reflecting the repaired state." } },
      { id: "s6", capability: "events", optional: true, es: { label: "Publicar eventos", detail: "Si la sesión tiene publishEvents() activado, se publican core.validation.* / core.quickfix.applied." }, en: { label: "Publish events", detail: "If the session has publishEvents() enabled, core.validation.* / core.quickfix.applied are published." } },
      { id: "s7", capability: "gamification", optional: true, es: { label: "Otorgar recompensa", detail: "Si una regla de gamificación coincide con el evento, se añade una entrada al ledger." }, en: { label: "Grant reward", detail: "If a gamification rule matches the event, an entry is added to the ledger." } }
    ],
    branches: [
      { fromStageId: "s2", es: { label: "Sin violaciones → fin del proceso, no hay quick fix que ofrecer" }, en: { label: "No violations → process ends, no quick fix to offer" } },
      { fromStageId: "s4", es: { label: "El usuario deshace el quick fix después → se publica core.quickfix.undone y Gamification compensa (resta la recompensa otorgada)" }, en: { label: "The user later undoes the quick fix → core.quickfix.undone is published and Gamification compensates (subtracts the granted reward)" } }
    ],
    result: {
      es: "El modelo queda, o bien sin violaciones reparables pendientes, o bien con un conjunto de diagnósticos actualizado; si hubo recompensa, queda registrada de forma reversible en el ledger.",
      en: "The model ends up either with no pending repairable violations, or with an updated diagnostic set; if a reward was granted, it is recorded reversibly in the ledger."
    },
    evidence: [
      { path: "src/main/java/com/example/editor/validation/engine/ValidationEngine.java", line: 144, label: "evaluate()" },
      { path: "src/main/java/com/example/editor/validation/runtime/ModelValidationDiagnostic.java", line: 10, label: "ModelValidationDiagnostic" }
    ]
  },

  {
    id: "transformation_execution_sync",
    capabilities: ["metamodel", "transformation", "integration"],
    es: {
      title: "Ejecutar una transformación y, si es bidireccional, sincronizar de forma incremental",
      trigger: "El usuario ejecuta una transformación definida entre un modelo origen y uno o varios modelos destino.",
      startState: "Un TransformationDefinitionDocument válido con sus endpoints resueltos contra metamodelos existentes."
    },
    en: {
      title: "Execute a transformation and, if bidirectional, synchronize incrementally",
      trigger: "The user runs a transformation defined between a source model and one or more target models.",
      startState: "A valid TransformationDefinitionDocument with its endpoints resolved against existing metamodels."
    },
    stages: [
      { id: "s1", capability: "transformation", optional: false, es: { label: "allocate()", detail: "Recorre el modelo origen, resuelve herencia de reglas, evalúa guards y asigna IDs destino deterministas (SHA-256)." }, en: { label: "allocate()", detail: "Traverses the source model, resolves rule inheritance, evaluates guards and assigns deterministic (SHA-256) target ids." } },
      { id: "s2", capability: "transformation", optional: false, es: { label: "populate()", detail: "Ejecuta los bindings (DIRECT/CONSTANT/EXPRESSION/TRACE_EQUIVALENT) resolviendo TRACE_EQUIVALENT contra el índice ya construido." }, en: { label: "populate()", detail: "Executes the bindings (DIRECT/CONSTANT/EXPRESSION/TRACE_EQUIVALENT), resolving TRACE_EQUIVALENT against the already-built index." } },
      { id: "s3", capability: "transformation", optional: false, es: { label: "Registrar traza", detail: "Si tracePolicy lo permite (activado por defecto), se registra TransformationTrace/TraceLink/BindingTrace." }, en: { label: "Record trace", detail: "If tracePolicy allows it (enabled by default), TransformationTrace/TraceLink/BindingTrace are recorded." } },
      { id: "s4", capability: "transformation", optional: true, es: { label: "Sincronización incremental", detail: "Si directionality=BIDIRECTIONAL: diff contra el baseline rodante, detección de conflictos (conflicts()) y fusión a nivel de característica." }, en: { label: "Incremental synchronization", detail: "If directionality=BIDIRECTIONAL: diff against the rolling baseline, conflict detection (conflicts()) and feature-level merge." } },
      { id: "s5", capability: "transformation", optional: true, es: { label: "Resolver conflicto", detail: "TransformationConflictPolicy decide: FAIL_ON_CONFLICT, FORWARD_WINS o REVERSE_WINS." }, en: { label: "Resolve conflict", detail: "TransformationConflictPolicy decides: FAIL_ON_CONFLICT, FORWARD_WINS or REVERSE_WINS." } },
      { id: "s6", capability: "integration", optional: true, es: { label: "Ejecutar con toolchain nativo", detail: "Como acción explícita y separada, se puede ejecutar la misma transformación con un motor externo (p. ej. ATL) para verificar paridad direccional." }, en: { label: "Run with a native toolchain", detail: "As a separate, explicit action, the same transformation can be executed with an external engine (e.g. ATL) to verify directional parity." } }
    ],
    branches: [
      { fromStageId: "s4", es: { label: "Si directionality=UNIDIRECTIONAL, el proceso termina en s3 (no hay sincronización incremental)" }, en: { label: "If directionality=UNIDIRECTIONAL, the process ends at s3 (no incremental synchronization)" } }
    ],
    result: {
      es: "Modelo(s) destino poblados y trazados; si era bidireccional, el baseline rodante y el historial de checkpoints quedan actualizados para la próxima sincronización.",
      en: "Target model(s) populated and traced; if bidirectional, the rolling baseline and checkpoint history are updated for the next synchronization."
    },
    evidence: [
      { path: "src/main/java/com/example/editor/transformation/runtime/CanonicalTransformationRuntime.java", line: 76, end_line: 119, label: "allocate()/populate()" },
      { path: "src/main/java/com/example/editor/transformation", line: 1, label: "TransformationIncrementalSynchronizationEngine" }
    ]
  },

  {
    id: "grammar_compile_infer_edit",
    capabilities: ["grammar", "metamodel", "model_editor", "transformation"],
    es: {
      title: "Compilar una gramática, inferir el metamodelo y editar texto conforme a ella",
      trigger: "El usuario guarda/recompila los documentos de una gramática, o escribe texto en el editor textual generado.",
      startState: "Uno o varios GrammarSourceDocument pertenecientes a un GrammarDefinitionModel."
    },
    en: {
      title: "Compile a grammar, infer its metamodel, and edit text conforming to it",
      trigger: "The user saves/recompiles the grammar's documents, or writes text in the generated textual editor.",
      startState: "One or more GrammarSourceDocument belonging to a GrammarDefinitionModel."
    },
    stages: [
      { id: "s1", capability: "grammar", optional: false, es: { label: "Pipeline de 12 etapas", detail: "GrammarCompilationService ejecuta las 12 etapas de compilación." }, en: { label: "12-stage pipeline", detail: "GrammarCompilationService runs the 12 compilation stages." } },
      { id: "s2", capability: "grammar", optional: false, es: { label: "GENERATE_SEMANTIC_ARTIFACTS", detail: "Etapa bloqueante: si no llega a COMPLETE, toda la compilación falla." }, en: { label: "GENERATE_SEMANTIC_ARTIFACTS", detail: "Blocking stage: if it does not reach COMPLETE, the whole compilation fails." } },
      { id: "s3", capability: "metamodel", optional: false, es: { label: "Proyectar metamodelo inferido", detail: "GrammarClassDiagramProjectionService.project(...) produce un MetaModel real, renderizado con el mismo MetamodelReadOnlyCanvas que un metamodelo manual." }, en: { label: "Project the inferred metamodel", detail: "GrammarClassDiagramProjectionService.project(...) produces a real MetaModel, rendered with the same MetamodelReadOnlyCanvas used for a manual metamodel." } },
      { id: "s4", capability: "grammar", optional: true, es: { label: "Escribir texto (parse)", detail: "El usuario escribe en el editor textual; parse() re-analiza el borrador bajo demanda, sin persistir." }, en: { label: "Write text (parse)", detail: "The user writes in the textual editor; parse() re-analyzes the draft on demand, without persisting." } },
      { id: "s5", capability: "grammar", optional: true, es: { label: "Guardar (save → T2M)", detail: "save() valida vía sharedLayers.validateBeforeCommit y promueve el resultado a un CanonicalModelDocument canónico." }, en: { label: "Save (save → T2M)", detail: "save() validates via sharedLayers.validateBeforeCommit and promotes the result to a canonical CanonicalModelDocument." } },
      { id: "s6", capability: "transformation", optional: true, es: { label: "Encadenar M2M", detail: "Si postTransformationId está configurado, el modelo resultante se transforma automáticamente a un modelo destino." }, en: { label: "Chain M2M", detail: "If postTransformationId is configured, the resulting model is automatically transformed into a target model." } }
    ],
    branches: [
      { fromStageId: "s2", es: { label: "Si GENERATE_SEMANTIC_ARTIFACTS falla, no hay metamodelo inferido ni es posible continuar a edición de texto" }, en: { label: "If GENERATE_SEMANTIC_ARTIFACTS fails, there is no inferred metamodel and text editing cannot proceed" } }
    ],
    result: {
      es: "Un metamodelo inferido real disponible para el resto de HYDRA, y opcionalmente un modelo canónico (y, encadenado, un modelo transformado) producido a partir del texto.",
      en: "A real inferred metamodel available to the rest of HYDRA, and optionally a canonical model (and, chained, a transformed model) produced from the text."
    },
    evidence: [
      { path: "src/main/java/com/example/editor/grammar", line: 1, label: "GrammarCompilationService — 12-stage pipeline" }
    ]
  },

  {
    id: "event_flow_gamification",
    capabilities: ["model_editor", "events", "gamification"],
    es: {
      title: "De una acción en el editor a una recompensa (y su compensación) vía Events y EventFlow",
      trigger: "Una sesión de editor con publishEvents() activado realiza una acción relevante, o se dispara un EventFlow previamente definido.",
      startState: "Una sesión de edición activa con la política runtimePolicy().publishEvents() habilitada."
    },
    en: {
      title: "From an editor action to a reward (and its compensation) via Events and EventFlow",
      trigger: "An editor session with publishEvents() enabled performs a relevant action, or a previously defined EventFlow is triggered.",
      startState: "An active editing session with runtimePolicy().publishEvents() enabled."
    },
    stages: [
      { id: "s1", capability: "events", optional: false, es: { label: "Publicar evento", detail: "EditorSessionEventPublisher publica un CanonicalEvent (de dominio o reservado core.*)." }, en: { label: "Publish event", detail: "EditorSessionEventPublisher publishes a CanonicalEvent (domain or reserved core.*)." } },
      { id: "s2", capability: "events", optional: true, es: { label: "Transformar evento (EventBinding)", detail: "Si existe un EventBinding cuya condición se cumple, se deriva un nuevo evento de otro tipo." }, en: { label: "Transform event (EventBinding)", detail: "If an EventBinding's condition holds, a new event of another type is derived." } },
      { id: "s3", capability: "events", optional: true, es: { label: "Ejecutar EventFlow", detail: "EventFlowRuntimeService.executeQueue avanza un grafo de FlowNode (decisión, espera, reintento, compensación) en modo worklist." }, en: { label: "Run EventFlow", detail: "EventFlowRuntimeService.executeQueue advances a FlowNode graph (decision, wait, retry, compensation) worklist-style." } },
      { id: "s4", capability: "events", optional: true, es: { label: "Republicar resultado del flujo", detail: "Los eventos de resultado del flujo (p. ej. core.event-flow.completed) se republican en el mismo bus." }, en: { label: "Republish flow result", detail: "The flow's result events (e.g. core.event-flow.completed) are republished on the same bus." } },
      { id: "s5", capability: "gamification", optional: true, es: { label: "Coincidir regla de gamificación", detail: "GamificationRuntimeService.subscribe() compara el evento (directo o derivado del flujo) contra cada regla." }, en: { label: "Match gamification rule", detail: "GamificationRuntimeService.subscribe() matches the event (direct or flow-derived) against each rule." } },
      { id: "s6", capability: "gamification", optional: true, es: { label: "Otorgar y notificar", detail: "Se añade una entrada al ledger y GamificationFeedbackProjector emite un UserFeedbackEnvelope (toast + panel de actividad)." }, en: { label: "Grant and notify", detail: "A ledger entry is added and GamificationFeedbackProjector emits a UserFeedbackEnvelope (toast + activity panel)." } },
      { id: "s7", capability: "gamification", optional: true, es: { label: "Compensar", detail: "Si llega después un evento de reversión (core.quickfix.undone, core.validation.violation-reopened), compensate() revierte la entrada del ledger." }, en: { label: "Compensate", detail: "If a reversal event later arrives (core.quickfix.undone, core.validation.violation-reopened), compensate() reverses the ledger entry." } }
    ],
    branches: [
      { fromStageId: "s1", es: { label: "Si la sesión no tiene publishEvents() activado, el proceso no comienza (ningún evento se publica)" }, en: { label: "If the session does not have publishEvents() enabled, the process never starts (no event is published)" } },
      { fromStageId: "s3", es: { label: "El EventFlow puede fallar definitivamente tras agotar reintentos → ejecuta su paso de compensación propio (COMPENSATION_STEP), independiente de la compensación de Gamification" }, en: { label: "The EventFlow can ultimately fail after exhausting retries → runs its own compensation step (COMPENSATION_STEP), independent of Gamification's compensation" } }
    ],
    result: {
      es: "Un evento observable en el bus, opcionalmente orquestado por un EventFlow, y opcionalmente reflejado como una recompensa (reversible) en el ledger de gamificación del actor.",
      en: "An observable event on the bus, optionally orchestrated by an EventFlow, and optionally reflected as a (reversible) reward in the actor's gamification ledger."
    },
    evidence: [
      { path: "src/main/java/com/example/editor/event/flow/FlowNodeKind.java", line: 4, label: "FlowNodeKind" },
      { path: "src/main/java/com/example/editor/gamification", line: 1, label: "GamificationRuntimeService.subscribe()" }
    ]
  },

  {
    id: "metamodel_lifecycle_delete_cascade",
    capabilities: ["metamodel", "model_editor", "validation", "transformation"],
    es: {
      title: "Guardar y, eventualmente, borrar un metamodelo (cascada de limpieza)",
      trigger: "El usuario guarda un metamodelo editado, o decide borrar una MetaClass/el MetaModel completo.",
      startState: "Un MetaModel abierto para edición, con 0..N artefactos dependientes (editores, reglas de validación, transformaciones)."
    },
    en: {
      title: "Save, and eventually delete, a metamodel (cleanup cascade)",
      trigger: "The user saves an edited metamodel, or decides to delete a MetaClass/the whole MetaModel.",
      startState: "A MetaModel open for editing, with 0..N dependent artifacts (editors, validation rules, transformations)."
    },
    stages: [
      { id: "s1", capability: "metamodel", optional: false, es: { label: "Guardar", detail: "CanonicalMetamodelApplicationService.prepareForSave dispara SIEMPRE validación estructural bloqueante del propio metamodelo." }, en: { label: "Save", detail: "CanonicalMetamodelApplicationService.prepareForSave ALWAYS triggers blocking structural validation of the metamodel itself." } },
      { id: "s2", capability: "metamodel", optional: true, es: { label: "Comprobar preparación para generación", detail: "MetamodelValidationApplicationService compara fingerprint para decidir si el metamodelo está listo para generar un editor/código." }, en: { label: "Check generation readiness", detail: "MetamodelValidationApplicationService compares fingerprints to decide if the metamodel is ready to generate an editor/code." } },
      { id: "s3", capability: "metamodel", optional: true, es: { label: "Iniciar borrado", detail: "Se marca el diario de borrado: MetamodelDeletionJournal.Status = PREPARED." }, en: { label: "Start deletion", detail: "The deletion journal is marked: MetamodelDeletionJournal.Status = PREPARED." } },
      { id: "s4", capability: "metamodel", optional: true, es: { label: "Borrar elemento primario", detail: "Se borra la MetaClass/MetaModel; estado → PRIMARY_DELETED." }, en: { label: "Delete primary element", detail: "The MetaClass/MetaModel is deleted; state → PRIMARY_DELETED." } },
      { id: "s5", capability: "model_editor", optional: true, es: { label: "Cascade sobre Model Editor", detail: "MetamodelDependentArtifactCleanupService limpia EditorDefinitionModel que referencian el elemento borrado (por convención de nombre Y por escaneo de referencias JSON)." }, en: { label: "Cascade into Model Editor", detail: "MetamodelDependentArtifactCleanupService cleans up EditorDefinitionModel referencing the deleted element (by naming convention AND JSON reference scan)." } },
      { id: "s6", capability: "validation", optional: true, es: { label: "Cascade sobre Validación", detail: "Se limpian ValidationRule cuyo context referenciaba el elemento borrado." }, en: { label: "Cascade into Validation", detail: "ValidationRule entries whose context referenced the deleted element are cleaned up." } },
      { id: "s7", capability: "transformation", optional: true, es: { label: "Cascade sobre Transformación", detail: "Se limpian reglas/endpoints de TransformationDefinitionDocument que referenciaban el elemento borrado." }, en: { label: "Cascade into Transformation", detail: "TransformationDefinitionDocument rules/endpoints that referenced the deleted element are cleaned up." } },
      { id: "s8", capability: "metamodel", optional: true, es: { label: "Completar o abortar", detail: "Estado final: COMPLETED si todo el cascade tuvo éxito, RECOVERY_REQUIRED si quedó a medias, o ABORTED." }, en: { label: "Complete or abort", detail: "Final state: COMPLETED if the whole cascade succeeded, RECOVERY_REQUIRED if it was left half-done, or ABORTED." } }
    ],
    branches: [
      { fromStageId: "s1", es: { label: "Si la validación estructural falla, el guardado se rechaza y no hay cambios persistidos" }, en: { label: "If structural validation fails, the save is rejected and nothing is persisted" } },
      { fromStageId: "s8", es: { label: "RECOVERY_REQUIRED exige intervención manual antes de poder reintentar el borrado" }, en: { label: "RECOVERY_REQUIRED requires manual intervention before the deletion can be retried" } }
    ],
    result: {
      es: "Un metamodelo guardado y validado, o un metamodelo/clase borrado con todos sus artefactos dependientes limpiados de forma consistente en 9+ tipos de capa.",
      en: "A saved and validated metamodel, or a deleted metamodel/class with all its dependent artifacts consistently cleaned up across 9+ layer types."
    },
    evidence: [
      { path: "src/main/java/com/example/editor/metamodel/application/CanonicalMetamodelApplicationService.java", line: 147, end_line: 179, label: "prepareForSave" }
    ]
  },

  {
    id: "external_recovery_preview_persist_rollback",
    capabilities: ["recovery", "metamodel", "validation", "integration"],
    es: {
      title: "Recuperar un proyecto/update-site externo: preview, materializar y, si hace falta, deshacer",
      trigger: "El usuario apunta HYDRA a un proyecto o update-site externo para incorporar su lenguaje y reglas.",
      startState: "Un proyecto externo (plugins, genmodel, bytecode EMF, código fuente) accesible para HYDRA."
    },
    en: {
      title: "Recover an external project/update-site: preview, materialize and, if needed, undo",
      trigger: "The user points HYDRA at an external project or update-site to bring in its language and rules.",
      startState: "An external project (plugins, genmodel, EMF bytecode, source code) accessible to HYDRA."
    },
    stages: [
      { id: "s1", capability: "recovery", optional: false, es: { label: "Descubrir metamodelo", detail: "Se elige el método de mayor confianza disponible de los 6 (DIRECT_METAMODEL→...→CODE_INFERENCE)." }, en: { label: "Discover metamodel", detail: "The highest-confidence of the 6 methods available is chosen (DIRECT_METAMODEL→...→CODE_INFERENCE)." } },
      { id: "s2", capability: "recovery", optional: true, es: { label: "Recuperar capas y editor", detail: "Se recuperan paleta/representaciones/mapeos semánticos y extension points marcados representable=true." }, en: { label: "Recover layers and editor", detail: "Palette/representations/semantic mappings and extension points tagged representable=true are recovered." } },
      { id: "s3", capability: "recovery", optional: true, es: { label: "Recuperar reglas de validación", detail: "Se traducen reglas EVL/OCL/Sirius/Xtext @Check reales; las no traducibles con seguridad quedan review-required/disabled." }, en: { label: "Recover validation rules", detail: "Real EVL/OCL/Sirius/Xtext @Check rules are translated; those not safely translatable are tagged review-required/disabled." } },
      { id: "s4", capability: "recovery", optional: false, es: { label: "Preview", detail: "ExternalProjectSemanticMaterializationService construye una previsualización que nunca se persiste." }, en: { label: "Preview", detail: "ExternalProjectSemanticMaterializationService builds a preview that is never persisted." } },
      { id: "s5", capability: "recovery", optional: true, es: { label: "Persistir", detail: "Solo si el usuario confirma y preview.applicable()==true: se crean documentos reales con ETag (metamodelo + reglas)." }, en: { label: "Persist", detail: "Only if the user confirms and preview.applicable()==true: real, ETag-bearing documents are created (metamodel + rules)." } },
      { id: "s6", capability: "metamodel", optional: false, es: { label: "Metamodelo disponible", detail: "El MetaModel recuperado queda disponible para el resto de HYDRA igual que uno definido a mano." }, en: { label: "Metamodel available", detail: "The recovered MetaModel becomes available to the rest of HYDRA exactly like a hand-defined one." } },
      { id: "s7", capability: "validation", optional: true, es: { label: "Reglas disponibles", detail: "Las reglas recuperadas y traducidas quedan activas; las review-required requieren revisión manual antes de activarse." }, en: { label: "Rules available", detail: "Recovered, translated rules become active; review-required ones need manual review before activation." } },
      { id: "s8", capability: "recovery", optional: true, es: { label: "Rollback", detail: "Si algo falla o se decide revertir, se deshace limpiamente solo lo creado en esa ejecución (identificado por sus ETags), no un snapshot completo." }, en: { label: "Rollback", detail: "If something fails or a revert is decided, only what that run created (identified by its ETags) is cleanly undone — not a full snapshot." } },
      { id: "s9", capability: "integration", optional: true, es: { label: "Nuevo perfil de integración", detail: "El proyecto recuperado puede, a su vez, alimentar un IntegrationProfileModel hacia la tecnología de origen." }, en: { label: "New integration profile", detail: "The recovered project can, in turn, feed an IntegrationProfileModel back toward the original technology." } }
    ],
    branches: [
      { fromStageId: "s5", es: { label: "Si preview.applicable()==false, no se persiste nada y el flujo termina en preview" }, en: { label: "If preview.applicable()==false, nothing is persisted and the flow ends at preview" } }
    ],
    result: {
      es: "Un metamodelo real (con su procedencia registrada) y, opcionalmente, reglas de validación recuperadas, con posibilidad de deshacer limpiamente toda la operación.",
      en: "A real metamodel (with recorded provenance) and, optionally, recovered validation rules, with the ability to cleanly undo the whole operation."
    },
    evidence: [
      { path: "src/main/java/com/example/editor/metamodel/importing/support/GeneratedGrammarImportSupport.java", line: 1, label: "Materialization preview/persist/rollback" }
    ]
  },

  {
    id: "release_gate_verified_publication",
    capabilities: ["metamodel", "model_editor", "publication", "integration", "project"],
    es: {
      title: "Generar un proyecto y, opcionalmente, certificarlo mediante publicación verificada",
      trigger: "El usuario decide distribuir un editor/proyecto, eligiendo entre generación simple o publicación verificada.",
      startState: "Un metamodelo + modelo + editor-definition ya validados y un target de generación elegido."
    },
    en: {
      title: "Generate a project and, optionally, certify it via verified publication",
      trigger: "The user decides to distribute an editor/project, choosing between plain generation or verified publication.",
      startState: "An already-validated metamodel + model + editor-definition and a chosen generation target."
    },
    stages: [
      { id: "s1", capability: "publication", optional: false, es: { label: "Generar código", detail: "ModelCodeGenerationService exige explícitamente metamodelId + modelId + targetId." }, en: { label: "Generate code", detail: "ModelCodeGenerationService explicitly requires metamodelId + modelId + targetId." } },
      { id: "s2", capability: "publication", optional: false, es: { label: "Registrar proyecto generado", detail: "GeneratedProjectLifecycleService: register → GENERATE → VALIDATE (del propio proyecto) → OPEN." }, en: { label: "Register generated project", detail: "GeneratedProjectLifecycleService: register → GENERATE → VALIDATE (of the project itself) → OPEN." } },
      { id: "s3", capability: "publication", optional: true, es: { label: "Lanzar", detail: "LAUNCH es opcional, en uno de los 5 perfiles de runtime soportados." }, en: { label: "Launch", detail: "LAUNCH is optional, in one of the 5 supported runtime profiles." } },
      { id: "s4", capability: "publication", optional: true, es: { label: "Empaquetar standalone", detail: "Alternativa: StandaloneEditorGeneratorService empaqueta un editor web autocontenido (sin el diseñador) para una combinación metamodelo/gramática + editor-definition." }, en: { label: "Package standalone", detail: "Alternative: StandaloneEditorGeneratorService packages a self-contained web editor (without the designer) for one metamodel/grammar + editor-definition combination." } },
      { id: "s5", capability: "publication", optional: true, es: { label: "Publicación verificada", detail: "Solo si se elige esta vía: HydraReleaseGateService.evaluate() agrega 5 checks (PACKAGE, SEMANTIC_MERGE, P2_MIRROR+license, ADAPTER_BENCH, PERFORMANCE)." }, en: { label: "Verified publication", detail: "Only if this path is chosen: HydraReleaseGateService.evaluate() aggregates 5 checks (PACKAGE, SEMANTIC_MERGE, P2_MIRROR+license, ADAPTER_BENCH, PERFORMANCE)." } },
      { id: "s6", capability: "integration", optional: true, es: { label: "Empaquetar artefactos de integración", detail: "Si el proyecto usa un IntegrationProfileModel, sus artefactos pueden incorporarse al paquete final." }, en: { label: "Package integration artifacts", detail: "If the project uses an IntegrationProfileModel, its artifacts can be bundled into the final package." } }
    ],
    branches: [
      { fromStageId: "s5", es: { label: "Cualquier check fallido bloquea SOLO la certificación de publicación verificada; la generación/distribución simple del mismo proyecto sigue siendo posible por separado" }, en: { label: "Any failed check blocks ONLY verified-publication certification; plain generation/distribution of the same project remains possible separately" } },
      { fromStageId: "s5", es: { label: "Importante: ningún check de la puerta de release comprueba el estado de validación del modelo ni realiza un check de seguridad/CVE" }, en: { label: "Important: none of the release-gate checks inspects the model's validation status or performs a security/CVE check" } }
    ],
    result: {
      es: "Un proyecto generado abrible/lanzable, o un editor standalone distribuible, y opcionalmente un veredicto certificado de publicación verificada con detalle por check.",
      en: "An openable/launchable generated project, or a distributable standalone editor, and optionally a certified verified-publication verdict with per-check detail."
    },
    evidence: [
      { path: "src/main/java/com/example/editor", line: 1, label: "HydraReleaseGateService.evaluate() — 5 checks" }
    ]
  },

  {
    id: "howto_model_editor_design",
    capabilities: ["metamodel", "grammar", "validation", "model_editor"],
    es: {
      title: "Cómo diseñar un editor de modelos (ejemplo detallado, diagrama Archify dedicado)",
      trigger: "El usuario quiere crear un nuevo editor de modelos de principio a fin.",
      startState: "Ninguna definición previa; se parte de la necesidad de un nuevo lenguaje y su editor."
    },
    en: {
      title: "How to design a model editor (detailed example, dedicated Archify diagram)",
      trigger: "The user wants to create a new model editor from scratch.",
      startState: "No prior definition; starting from the need for a new language and its editor."
    },
    stages: [
      { id: "s1", capability: "metamodel", optional: false, es: { label: "Clases y relaciones", detail: "Se define el metamodelo (EMF/Ecore): tipos, atributos, referencias, herencia." }, en: { label: "Classes & relationships", detail: "The metamodel (EMF/Ecore) is defined: types, attributes, references, inheritance." } },
      { id: "s2", capability: "model_editor", optional: false, es: { label: "Notación gráfica", detail: "Se diseña la apariencia visual (iconos, formas, colores) vía NodeMapping/EdgeMapping — no cambia el metamodelo." }, en: { label: "Graphical notation", detail: "Visual appearance is designed (icons, shapes, colors) via NodeMapping/EdgeMapping — it does not change the metamodel." } },
      { id: "s3", capability: "validation", optional: false, es: { label: "Validaciones", detail: "Se definen reglas sobre los tipos ya existentes del metamodelo." }, en: { label: "Validations", detail: "Rules are defined over the metamodel's already-existing types." } },
      { id: "s4", capability: "validation", optional: false, es: { label: "Quick fixes", detail: "Se enlazan reparaciones a las reglas, nunca se definen nuevos tipos en este paso." }, en: { label: "Quick fixes", detail: "Repairs are linked to the rules; no new types are defined at this step." } },
      { id: "s5", capability: "model_editor", optional: false, es: { label: "Paleta y formularios", detail: "Se mapea cómo se edita cada tipo en el canvas (no qué es ese tipo)." }, en: { label: "Palette & forms", detail: "How each type is edited on the canvas is mapped (not what that type is)." } },
      { id: "s6", capability: "model_editor", optional: false, es: { label: "Generar y probar", detail: "Se compila el EditorDefinitionModel y se abre una EditorModelSession de prueba." }, en: { label: "Generate & test", detail: "The EditorDefinitionModel is compiled and a test EditorModelSession is opened." } }
    ],
    branches: [
      { fromStageId: "s4", es: { label: "Retroalimentación: si una regla no es suficiente, se ajusta antes de seguir a la paleta" }, en: { label: "Feedback: if a rule is not enough, it is adjusted before moving on to the palette" } },
      { fromStageId: "s6", es: { label: "Retroalimentación: si el estilo visual no convence al probar, se vuelve a la notación gráfica" }, en: { label: "Feedback: if the visual style is unsatisfying when testing, go back to the graphical notation" } }
    ],
    result: {
      es: "Un editor de modelos completo y probado: metamodelo + notación + reglas de calidad con sus reparaciones + editor compilado y abierto.",
      en: "A complete, tested model editor: metamodel + notation + quality rules with their repairs + compiled, opened editor."
    },
    evidence: [
      { path: "docs/architecture/archify/internal/hydra-howto-model-editor.es.workflow.json", line: 1, label: "Archify workflow diagram (hydra-howto-model-editor)" }
    ],
    archifyDiagram: { es: "internal/hydra-howto-model-editor.es.html", en: "internal/hydra-howto-model-editor.en.html" }
  }

];
