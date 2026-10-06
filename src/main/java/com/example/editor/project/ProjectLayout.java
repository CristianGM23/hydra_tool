package com.example.editor.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Describe la estructura portable de un proyecto local.
 * Todas las rutas son relativas a la raíz del proyecto.
 */
public record ProjectLayout(
        String internal,
        String metamodels,
        String editors,
        String representations,
        String palettes,
        String forms,
        String notations,
        String expressions,
        String validations,
        String actions,
        String operations,
        String eventDefinitions,
        String eventBindings,
        String eventFlows,
        String gamificationDefinitions,
        String gamificationBindings,
        String semanticModels,
        String modelDiagrams,
        String integrations,
        String generated,
        String assets,
        String tests,
        String docs
) {
    public ProjectLayout {
        internal = normalized(internal,".modeling");
        metamodels = normalized(metamodels,"metamodels");
        editors = normalized(editors,"editors");
        representations = normalized(representations,"editors/representations");
        palettes = normalized(palettes,"editors/palettes");
        forms = normalized(forms,"editors/forms");
        notations = normalized(notations,"editors/notation");
        expressions = normalized(expressions,"behavior/expressions");
        validations = normalized(validations,"behavior/validations");
        actions = normalized(actions,"behavior/actions");
        operations = normalized(operations,"behavior/operations");
        eventDefinitions = normalized(eventDefinitions,"events/definitions");
        eventBindings = normalized(eventBindings,"events/bindings");
        eventFlows = normalized(eventFlows,"events/flows");
        gamificationDefinitions = normalized(gamificationDefinitions,"gamification/definitions");
        gamificationBindings = normalized(gamificationBindings,"gamification/bindings");
        semanticModels = normalized(semanticModels,"models/semantic");
        modelDiagrams = normalized(modelDiagrams,"models/diagrams");
        integrations = normalized(integrations,"integrations");
        generated = normalized(generated,"generated");
        assets = normalized(assets,"assets");
        tests = normalized(tests,"tests");
        docs = normalized(docs,"docs");
    }

    /** Descripción: Ejecuta la operación «standard» correspondiente a la responsabilidad de ProjectLayout. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectLayout standard() {
        return new ProjectLayout(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }

    /** Raíz canónica para gramáticas. Se mantiene como método derivado para conservar compatibilidad del descriptor 1.0. */
    /** Descripción: Ejecuta la operación «grammars» correspondiente a la responsabilidad de ProjectLayout. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String grammars() { return "grammars"; }
    /** Raíz canónica para transformaciones M2M/M2T/T2M. */
    /** Descripción: Ejecuta la operación «transformations» correspondiente a la responsabilidad de ProjectLayout. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String transformations() { return "transformations"; }

    /** Descripción: Ejecuta la operación «logicalRoots» correspondiente a la responsabilidad de ProjectLayout. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String,String> logicalRoots() {
        Map<String,String> roots = new LinkedHashMap<>();
        roots.put("Metamodels",metamodels);
        roots.put("Model editors",editors);
        roots.put("Representations",representations);
        roots.put("Palettes",palettes);
        roots.put("Forms",forms);
        roots.put("Notations",notations);
        roots.put("Expressions",expressions);
        roots.put("Validations",validations);
        roots.put("Quick fixes and actions",actions);
        roots.put("Operations",operations);
        roots.put("Event definitions",eventDefinitions);
        roots.put("Event bindings",eventBindings);
        roots.put("Event flows",eventFlows);
        roots.put("Gamification definitions",gamificationDefinitions);
        roots.put("Gamification bindings",gamificationBindings);
        roots.put("Models",semanticModels);
        roots.put("Model diagrams",modelDiagrams);
        roots.put("Integrations",integrations);
        roots.put("Generated",generated);
        roots.put("Assets",assets);
        roots.put("Tests",tests);
        roots.put("Documentation",docs);
        return Collections.unmodifiableMap(roots);
    }


    /** Raíces lógicas visibles para las capacidades activas; evita módulos vacíos ajenos a la finalidad del proyecto. */
    /** Descripción: Ejecuta la operación «logicalRoots» correspondiente a la responsabilidad de ProjectLayout. Entradas: capabilities: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String,String> logicalRoots(Set<ProjectCapability> capabilities) {
        Set<ProjectCapability> enabled = capabilities == null ? Set.of() : capabilities;
        Map<String,String> roots = new LinkedHashMap<>();
        if(enabled.contains(ProjectCapability.METAMODEL_DESIGN)) roots.put("Metamodels",metamodels);
        if(enabled.contains(ProjectCapability.GRAMMAR_DESIGN)) roots.put("Grammars",grammars());
        if(enabled.contains(ProjectCapability.TRANSFORMATION_M2M)||enabled.contains(ProjectCapability.TRANSFORMATION_M2T)||enabled.contains(ProjectCapability.TRANSFORMATION_T2M)) roots.put("Transformations",transformations());
        if(enabled.contains(ProjectCapability.EDITOR_DEFINITIONS)||enabled.contains(ProjectCapability.VISUAL_EDITOR)||enabled.contains(ProjectCapability.TEXTUAL_EDITOR)) {
            roots.put("Model editors",editors); roots.put("Representations",representations); roots.put("Palettes",palettes); roots.put("Forms",forms); roots.put("Notations",notations);
        }
        if(enabled.contains(ProjectCapability.VALIDATIONS)) { roots.put("Expressions",expressions); roots.put("Validations",validations); }
        if(enabled.contains(ProjectCapability.QUICK_FIXES)) roots.put("Quick fixes and actions",actions);
        if(enabled.contains(ProjectCapability.OPERATIONS)) roots.put("Operations",operations);
        if(enabled.contains(ProjectCapability.EVENTS)) { roots.put("Event definitions",eventDefinitions); roots.put("Event bindings",eventBindings); }
        if(enabled.contains(ProjectCapability.EVENT_FLOWS)) roots.put("Event flows",eventFlows);
        if(enabled.contains(ProjectCapability.GAMIFICATION)) { roots.put("Gamification definitions",gamificationDefinitions); roots.put("Gamification bindings",gamificationBindings); }
        if(enabled.contains(ProjectCapability.MODEL_EDITING)||enabled.contains(ProjectCapability.VISUAL_MODELING)||enabled.contains(ProjectCapability.TEXTUAL_MODELING)) { roots.put("Models",semanticModels); roots.put("Model diagrams",modelDiagrams); }
        if(enabled.contains(ProjectCapability.INTEGRATIONS)||enabled.contains(ProjectCapability.REPOSITORY_INTEGRATION)) roots.put("Integrations",integrations);
        if(enabled.contains(ProjectCapability.EDITOR_GENERATION)||enabled.contains(ProjectCapability.TRANSFORMATION_M2T)) roots.put("Generated",generated);
        if(enabled.contains(ProjectCapability.LOCAL_FILE_EDITING)) { roots.put("Assets",assets); roots.put("Documentation",docs); }
        if(!enabled.isEmpty()) roots.put("Tests",tests);
        return Collections.unmodifiableMap(roots);
    }

    /** Carpetas físicas mínimas para las capacidades activas. */
    /** Descripción: Ejecuta la operación «primaryRoots» correspondiente a la responsabilidad de ProjectLayout. Entradas: capabilities: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public java.util.Set<String> primaryRoots(Set<ProjectCapability> capabilities) {
        java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<>();
        for(String path : logicalRoots(capabilities).values()) {
            String normalized = path.replace('\\','/');
            int separator = normalized.indexOf('/');
            roots.add(separator < 0 ? normalized : normalized.substring(0,separator));
        }
        return java.util.Collections.unmodifiableSet(roots);
    }
    /**
     * Physical folders created eagerly for a new project. Detailed capability
     * folders are created lazily when the first artifact is written, keeping a
     * new project small and readable without changing the portable logical map.
     */
    /** Descripción: Ejecuta la operación «primaryRoots» correspondiente a la responsabilidad de ProjectLayout. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public java.util.Set<String> primaryRoots() {
        java.util.LinkedHashSet<String> roots = new java.util.LinkedHashSet<>();
        for(String path : logicalRoots().values()) {
            String normalized = path.replace('\\','/');
            int separator = normalized.indexOf('/');
            roots.add(separator < 0 ? normalized : normalized.substring(0,separator));
        }
        return java.util.Collections.unmodifiableSet(roots);
    }

    /** Descripción: Ejecuta la operación «artifactRoots» correspondiente a la responsabilidad de ProjectLayout. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<ArtifactKind,String> artifactRoots() {
        Map<ArtifactKind,String> roots = new LinkedHashMap<>();
        roots.put(ArtifactKind.METAMODEL,metamodels);
        roots.put(ArtifactKind.METAMODEL_DIAGRAM,metamodels);
        roots.put(ArtifactKind.EDITOR_DEFINITION,editors);
        roots.put(ArtifactKind.REPRESENTATION_DEFINITION,representations);
        roots.put(ArtifactKind.PALETTE_DEFINITION,palettes);
        roots.put(ArtifactKind.FORM_DEFINITION,forms);
        roots.put(ArtifactKind.NOTATION_DEFINITION,notations);
        roots.put(ArtifactKind.EXPRESSION_MODEL,expressions);
        roots.put(ArtifactKind.VALIDATION_MODEL,validations);
        roots.put(ArtifactKind.ACTION_MODEL,actions);
        roots.put(ArtifactKind.OPERATION_BEHAVIOR_MODEL,operations);
        roots.put(ArtifactKind.EVENT_DEFINITION_MODEL,eventDefinitions);
        roots.put(ArtifactKind.EVENT_BINDING_MODEL,eventBindings);
        roots.put(ArtifactKind.EVENT_ARCHITECTURE_DIAGRAM,eventDefinitions);
        roots.put(ArtifactKind.EVENT_FLOW_MODEL,eventFlows);
        roots.put(ArtifactKind.EVENT_FLOW_DIAGRAM,eventFlows);
        roots.put(ArtifactKind.EVENT_FLOW_RUNTIME,eventFlows);
        roots.put(ArtifactKind.GAMIFICATION_DEFINITION_MODEL,gamificationDefinitions);
        roots.put(ArtifactKind.GAMIFICATION_BINDING_MODEL,gamificationBindings);
        roots.put(ArtifactKind.GAMIFICATION_DIAGRAM,gamificationDefinitions);
        roots.put(ArtifactKind.MODEL,semanticModels);
        roots.put(ArtifactKind.EDITOR_MODEL_SESSION,semanticModels);
        roots.put(ArtifactKind.EDITOR_SESSION_DIAGRAM,modelDiagrams);
        roots.put(ArtifactKind.MODEL_DIAGRAM,modelDiagrams);
        roots.put(ArtifactKind.INTEGRATION_PROFILE,integrations);
        roots.put(ArtifactKind.ASSET,assets);
        roots.put(ArtifactKind.TEST_RESOURCE,tests);
        roots.put(ArtifactKind.DOCUMENTATION,docs);
        return Collections.unmodifiableMap(roots);
    }

    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de ProjectLayout. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalized(String value,String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value.trim().replace('\\','/');
        if(selected.startsWith("/") || selected.contains("..")) {
            throw new IllegalArgumentException("Project layout paths must be safe relative paths");
        }
        return selected.replaceAll("/+","/");
    }
}
