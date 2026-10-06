package com.example.editor.integration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catálogo conservador de capacidades previstas para cada tecnología. */
public final class IntegrationTechnologyCatalog {
    private static final Map<IntegrationTechnology, IntegrationTechnologyDescriptor> DESCRIPTORS = createDescriptors();
    /** Descripción: Inicializa una instancia de IntegrationTechnologyCatalog con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private IntegrationTechnologyCatalog() { }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de IntegrationTechnologyCatalog. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static IntegrationTechnologyDescriptor descriptor(IntegrationTechnology technology) {
        IntegrationTechnologyDescriptor descriptor = DESCRIPTORS.get(technology);
        if(descriptor == null) throw new IllegalArgumentException("Unknown integration technology: " + technology);
        return descriptor;
    }

    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de IntegrationTechnologyCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static List<IntegrationTechnologyDescriptor> descriptors() { return Arrays.stream(IntegrationTechnology.values()).map(IntegrationTechnologyCatalog::descriptor).toList(); }

    /** Descripción: Crea o prepara el resultado de «createDescriptors» dentro de IntegrationTechnologyCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<IntegrationTechnology, IntegrationTechnologyDescriptor> createDescriptors() {
        Map<IntegrationTechnology, IntegrationTechnologyDescriptor> result = new EnumMap<>(IntegrationTechnology.class);
        result.put(IntegrationTechnology.EMF, descriptor(IntegrationTechnology.EMF, "Eclipse Modeling Framework", IntegrationTargetKind.MODELING_RUNTIME,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_MODULE, IntegrationBuildSystem.MAVEN,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_MODULE, InjectionStrategy.ADD_PLUGIN, InjectionStrategy.GENERATE_SIDECAR, InjectionStrategy.PATCH_EXISTING),
                Set.of(IntegrationLocatorStrategy.NS_URI_AND_CLASSIFIER, IntegrationLocatorStrategy.URI_FRAGMENT, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_PUBLISH, IntegrationCapability.EVENT_CONSUME, IntegrationCapability.CODE_GENERATION,
                        IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.PROJECT_EXPORT, IntegrationCapability.ROUND_TRIP),
                partial(IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.EVENT_FLOW_RUNTIME,
                        IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.UI_CONTRIBUTION),
                List.of("HYDRA generates a separate Maven EMF module with Ecore, Java bridge boundaries and reversible injection.", "Transactions and undo/redo require the generated editing-domain adapter to be connected to the host.")));
        result.put(IntegrationTechnology.SIRIUS_DESKTOP, descriptor(IntegrationTechnology.SIRIUS_DESKTOP, "Eclipse Sirius Desktop", IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PLUGIN, IntegrationBuildSystem.TYCHO,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PLUGIN, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR, InjectionStrategy.PATCH_EXISTING),
                Set.of(IntegrationLocatorStrategy.NS_URI_AND_CLASSIFIER, IntegrationLocatorStrategy.URI_FRAGMENT, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.VALIDATION_BRIDGE,
                        IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE, IntegrationCapability.EVENT_PUBLISH,
                        IntegrationCapability.EVENT_CONSUME, IntegrationCapability.UI_CONTRIBUTION, IntegrationCapability.NOTATION_CONTRIBUTION,
                        IntegrationCapability.PALETTE_CONTRIBUTION, IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.PROJECT_EXPORT,
                        IntegrationCapability.ROUND_TRIP, IntegrationCapability.CODE_GENERATION),
                partial(IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME),
                List.of("Sirius integration is generated as a separate Tycho/Eclipse plug-in and does not modify generated EMF classes.", "Diagram viewpoints are translated natively; non-diagram HYDRA representations remain available with an explicit partial-fidelity diagnostic.")));
        result.put(IntegrationTechnology.XTEXT, descriptor(IntegrationTechnology.XTEXT, "Eclipse Xtext", IntegrationTargetKind.TEXTUAL_LANGUAGE_TOOLING,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_MODULE, IntegrationBuildSystem.MAVEN,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_MODULE, InjectionStrategy.ADD_PLUGIN, InjectionStrategy.GENERATE_SIDECAR, InjectionStrategy.PATCH_EXISTING),
                Set.of(IntegrationLocatorStrategy.NS_URI_AND_CLASSIFIER, IntegrationLocatorStrategy.URI_FRAGMENT, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.EVENT_PUBLISH,
                        IntegrationCapability.EVENT_CONSUME, IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT,
                        IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.UI_CONTRIBUTION,
                        IntegrationCapability.ROUND_TRIP),
                List.of("The generated integration must target runtime, IDE and UI modules separately.")));
        result.put(IntegrationTechnology.LANGIUM, descriptor(IntegrationTechnology.LANGIUM, "Langium", IntegrationTargetKind.TEXTUAL_LANGUAGE_TOOLING,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PACKAGE, IntegrationBuildSystem.NPM,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR, InjectionStrategy.PATCH_EXISTING),
                Set.of(IntegrationLocatorStrategy.AST_PATH, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.EVENT_PUBLISH,
                        IntegrationCapability.EVENT_CONSUME, IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT,
                        IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.UI_CONTRIBUTION,
                        IntegrationCapability.ROUND_TRIP),
                List.of("The adapter is generated as TypeScript services and optional VS Code contributions.")));
        result.put(IntegrationTechnology.ANTLR, descriptor(IntegrationTechnology.ANTLR, "ANTLR", IntegrationTargetKind.TEXTUAL_LANGUAGE_TOOLING,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_MODULE, IntegrationBuildSystem.MAVEN,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_MODULE, InjectionStrategy.ADD_PACKAGE, InjectionStrategy.GENERATE_SIDECAR, InjectionStrategy.PATCH_EXISTING),
                Set.of(IntegrationLocatorStrategy.AST_PATH, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.CODE_GENERATION,
                        IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_PUBLISH, IntegrationCapability.EVENT_CONSUME, IntegrationCapability.EVENT_FLOW_RUNTIME,
                        IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.UI_CONTRIBUTION, IntegrationCapability.ROUND_TRIP),
                List.of("ANTLR is integrated through generated lexer/parser projects plus a HYDRA semantic bridge; parse-tree nodes are never treated as the semantic model by themselves.")));
        result.put(IntegrationTechnology.BESSER_SDK, descriptor(IntegrationTechnology.BESSER_SDK, "BESSER Python SDK", IntegrationTargetKind.MODELING_RUNTIME,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PACKAGE, IntegrationBuildSystem.PIP,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR),
                Set.of(IntegrationLocatorStrategy.BESSER_ELEMENT_ID, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_PUBLISH, IntegrationCapability.EVENT_CONSUME, IntegrationCapability.GAMIFICATION_RUNTIME,
                        IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS, IntegrationCapability.VALIDATION_BRIDGE,
                        IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.NOTATION_CONTRIBUTION,
                        IntegrationCapability.ROUND_TRIP),
                List.of("The package targets B-UML Python models and BESSER generators; editor UI contributions require the web-editor target.")));
        result.put(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR, descriptor(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR, "BESSER Web Modeling Editor", IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PACKAGE, IntegrationBuildSystem.NPM,
                Set.of(IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR),
                Set.of(IntegrationLocatorStrategy.BESSER_ELEMENT_ID, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.EVENT_PUBLISH,
                        IntegrationCapability.EVENT_CONSUME, IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT,
                        IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS, IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO,
                        IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.UI_CONTRIBUTION,
                        IntegrationCapability.NOTATION_CONTRIBUTION, IntegrationCapability.PALETTE_CONTRIBUTION, IntegrationCapability.ROUND_TRIP),
                List.of("UI, notation and palette support requires a companion extension for the BESSER web editor.")));
        result.put(IntegrationTechnology.ECLIPSE_GLSP, descriptor(IntegrationTechnology.ECLIPSE_GLSP, "Eclipse GLSP", IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PACKAGE, IntegrationBuildSystem.NPM,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR, InjectionStrategy.PATCH_EXISTING),
                Set.of(IntegrationLocatorStrategy.GLSP_DIAGRAM_TYPE, IntegrationLocatorStrategy.GLSP_ELEMENT_ID, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH, IntegrationLocatorStrategy.AST_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.UNDO_REDO, IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE,
                        IntegrationCapability.OPERATION_BRIDGE, IntegrationCapability.EVENT_PUBLISH, IntegrationCapability.EVENT_CONSUME,
                        IntegrationCapability.UI_CONTRIBUTION, IntegrationCapability.NOTATION_CONTRIBUTION, IntegrationCapability.PALETTE_CONTRIBUTION,
                        IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.TRANSACTIONS, IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME,
                        IntegrationCapability.ROUND_TRIP),
                List.of("Generates a reusable TypeScript client and a Java or TypeScript server module.",
                        "The source-model adapter remains configurable for HYDRA JSON, EMF or an external storage service.")));
        result.put(IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION, descriptor(IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION, "Eclipse Modeling Distribution", IntegrationTargetKind.IDE_DISTRIBUTION,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PLUGIN, IntegrationBuildSystem.TYCHO,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PLUGIN, InjectionStrategy.ADD_MODULE),
                Set.of(IntegrationLocatorStrategy.NS_URI_AND_CLASSIFIER, IntegrationLocatorStrategy.URI_FRAGMENT, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.VALIDATION_BRIDGE,
                        IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE, IntegrationCapability.EVENT_PUBLISH,
                        IntegrationCapability.EVENT_CONSUME, IntegrationCapability.UI_CONTRIBUTION, IntegrationCapability.NOTATION_CONTRIBUTION, IntegrationCapability.PALETTE_CONTRIBUTION,
                        IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.PROJECT_EXPORT,
                        IntegrationCapability.ROUND_TRIP),
                partial(IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME,
                        IntegrationCapability.DIAGRAM_RENDERING, IntegrationCapability.TEXTUAL_SOURCE_GENERATION),
                List.of("Generates a feature-based Eclipse product with EMF, GMF Runtime, Sirius Desktop, Xtext and Xtend dependencies plus a metamodel-scoped HYDRA runtime plug-in.",
                        "The generated feature is additive and reversible; host modeling bundles are resolved from p2 instead of being copied or patched.")));
        result.put(IntegrationTechnology.VSCODE_EXTENSION, descriptor(IntegrationTechnology.VSCODE_EXTENSION, "VS Code Modeling Extension", IntegrationTargetKind.IDE_EXTENSION,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PACKAGE, IntegrationBuildSystem.NPM,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.REMOTE, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR),
                Set.of(IntegrationLocatorStrategy.AST_PATH, IntegrationLocatorStrategy.GLSP_DIAGRAM_TYPE, IntegrationLocatorStrategy.GLSP_ELEMENT_ID, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_PUBLISH, IntegrationCapability.EVENT_CONSUME, IntegrationCapability.UI_CONTRIBUTION, IntegrationCapability.NOTATION_CONTRIBUTION,
                        IntegrationCapability.PALETTE_CONTRIBUTION, IntegrationCapability.CODE_GENERATION, IntegrationCapability.PROJECT_IMPORT,
                        IntegrationCapability.PROJECT_EXPORT),
                partial(IntegrationCapability.EVENT_FLOW_RUNTIME, IntegrationCapability.GAMIFICATION_RUNTIME,
                        IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.ROUND_TRIP, IntegrationCapability.DIAGRAM_RENDERING, IntegrationCapability.TEXTUAL_SOURCE_GENERATION),
                List.of("Generates an installable VS Code extension with workspace-persistent HYDRA runtime activation and explicit Langium, GLSP and Xtext/LSP bridges.",
                        "A safe custom-editor fallback remains available when an external graphical server is not configured.")));
        result.put(IntegrationTechnology.MXGRAPH_DRAWIO, descriptor(IntegrationTechnology.MXGRAPH_DRAWIO, "mxGraph / draw.io", IntegrationTargetKind.GRAPHICAL_MODELING_EDITOR,
                IntegrationDeliveryMode.GENERATED_MODULE, InjectionStrategy.ADD_PACKAGE, IntegrationBuildSystem.NPM,
                Set.of(IntegrationDeliveryMode.EMBEDDED, IntegrationDeliveryMode.HYBRID, IntegrationDeliveryMode.GENERATED_MODULE),
                Set.of(InjectionStrategy.ADD_PACKAGE, InjectionStrategy.ADD_MODULE, InjectionStrategy.GENERATE_SIDECAR),
                Set.of(IntegrationLocatorStrategy.CUSTOM, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.MODEL_READ, IntegrationCapability.MODEL_WRITE, IntegrationCapability.MODEL_CHANGE_NOTIFICATIONS,
                        IntegrationCapability.VALIDATION_BRIDGE, IntegrationCapability.QUICK_FIX_BRIDGE, IntegrationCapability.OPERATION_BRIDGE,
                        IntegrationCapability.EVENT_PUBLISH, IntegrationCapability.EVENT_CONSUME, IntegrationCapability.UI_CONTRIBUTION,
                        IntegrationCapability.NOTATION_CONTRIBUTION, IntegrationCapability.PALETTE_CONTRIBUTION, IntegrationCapability.DIAGRAM_RENDERING,
                        IntegrationCapability.PROJECT_EXPORT, IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.ROUND_TRIP),
                partial(IntegrationCapability.TRANSACTIONS, IntegrationCapability.UNDO_REDO, IntegrationCapability.EVENT_FLOW_RUNTIME,
                        IntegrationCapability.GAMIFICATION_RUNTIME, IntegrationCapability.CODE_GENERATION),
                List.of("Generates a reusable mxGraph-compatible JavaScript runtime plus a self-contained diagrams.net/draw.io plug-in and representation-specific custom libraries.",
                        "Executable validation, quick-fix, event and gamification behavior is bridged explicitly; generated JavaScript never evaluates imported expressions as arbitrary code.")));
        result.put(IntegrationTechnology.PLANTUML, descriptor(IntegrationTechnology.PLANTUML, "PlantUML", IntegrationTargetKind.DIAGRAM_PROJECTION,
                IntegrationDeliveryMode.PROJECTION_ONLY, InjectionStrategy.GENERATE_PROJECTION, IntegrationBuildSystem.NONE,
                Set.of(IntegrationDeliveryMode.PROJECTION_ONLY),
                Set.of(InjectionStrategy.GENERATE_PROJECTION),
                Set.of(IntegrationLocatorStrategy.PLANTUML_ALIAS, IntegrationLocatorStrategy.QUALIFIED_NAME, IntegrationLocatorStrategy.DOCUMENT_PATH),
                full(IntegrationCapability.DIAGRAM_RENDERING, IntegrationCapability.TEXTUAL_SOURCE_GENERATION,
                        IntegrationCapability.PROJECT_EXPORT, IntegrationCapability.VALIDATION_PROJECTION,
                        IntegrationCapability.EVENT_DEFINITION_PROJECTION, IntegrationCapability.EVENT_FLOW_PROJECTION,
                        IntegrationCapability.GAMIFICATION_PROJECTION),
                partial(IntegrationCapability.PROJECT_IMPORT, IntegrationCapability.ROUND_TRIP),
                List.of("PlantUML is a projection target. It can visualize definitions but cannot execute runtimes or mutate models.")));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de IntegrationTechnologyCatalog. Entradas: technology: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; modes: dato de entrada necesario para la operación; strategies: dato de entrada necesario para la operación; locators: dato de entrada necesario para la operación; full: dato de entrada necesario para la operación; partial: dato de entrada necesario para la operación; notes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static IntegrationTechnologyDescriptor descriptor(IntegrationTechnology technology, String name, IntegrationTargetKind kind,
            IntegrationDeliveryMode recommendedDeliveryMode, InjectionStrategy recommendedInjectionStrategy, IntegrationBuildSystem recommendedBuildSystem,
            Set<IntegrationDeliveryMode> modes, Set<InjectionStrategy> strategies, Set<IntegrationLocatorStrategy> locators,
            Map<IntegrationCapability, IntegrationSupportLevel> full, Map<IntegrationCapability, IntegrationSupportLevel> partial, List<String> notes) {
        Map<IntegrationCapability, IntegrationSupportLevel> capabilities = new EnumMap<>(IntegrationCapability.class);
        capabilities.putAll(full); capabilities.putAll(partial);
        return new IntegrationTechnologyDescriptor(technology, name, kind, recommendedDeliveryMode, recommendedInjectionStrategy, recommendedBuildSystem,
                modes, strategies, locators, capabilities, notes);
    }

    /** Descripción: Ejecuta la operación «full» correspondiente a la responsabilidad de IntegrationTechnologyCatalog. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<IntegrationCapability, IntegrationSupportLevel> full(IntegrationCapability... values) { return levels(IntegrationSupportLevel.FULL, values); }
    /** Descripción: Ejecuta la operación «partial» correspondiente a la responsabilidad de IntegrationTechnologyCatalog. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<IntegrationCapability, IntegrationSupportLevel> partial(IntegrationCapability... values) { return levels(IntegrationSupportLevel.PARTIAL, values); }
    /** Descripción: Ejecuta la operación «levels» correspondiente a la responsabilidad de IntegrationTechnologyCatalog. Entradas: level: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<IntegrationCapability, IntegrationSupportLevel> levels(IntegrationSupportLevel level, IntegrationCapability... values) {
        Map<IntegrationCapability, IntegrationSupportLevel> result = new EnumMap<>(IntegrationCapability.class);
        for(IntegrationCapability value : values) result.put(value, level);
        return result;
    }
}
