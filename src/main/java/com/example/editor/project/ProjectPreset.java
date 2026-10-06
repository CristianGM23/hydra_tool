package com.example.editor.project;

import java.util.EnumSet;
import java.util.Set;

/** Plantillas de creación. Son un punto de partida; nunca restringen la evolución posterior del proyecto. */
public enum ProjectPreset {
    EMPTY("Empty project","Start with the HYDRA project container only", EnumSet.noneOf(ProjectCapability.class)),
    GRAPHICAL_LANGUAGE("Graphical language","Metamodel plus visual modeling/editor capabilities", set(ProjectCapability.METAMODEL_DESIGN,ProjectCapability.VISUAL_MODELING,ProjectCapability.VISUAL_EDITOR,ProjectCapability.EDITOR_DEFINITIONS,ProjectCapability.LOCAL_FILE_EDITING)),
    TEXTUAL_DSL("Textual language / DSL","Grammar, textual models, syntax styling and contextual help", set(ProjectCapability.GRAMMAR_DESIGN,ProjectCapability.TEXTUAL_MODELING,ProjectCapability.TEXTUAL_EDITOR,ProjectCapability.SYNTAX_STYLING,ProjectCapability.CONTEXTUAL_INFORMATION,ProjectCapability.LOCAL_FILE_EDITING)),
    HYBRID_LANGUAGE("Hybrid language","Graphical and textual language engineering in the same project", set(ProjectCapability.METAMODEL_DESIGN,ProjectCapability.GRAMMAR_DESIGN,ProjectCapability.VISUAL_MODELING,ProjectCapability.TEXTUAL_MODELING,ProjectCapability.VISUAL_EDITOR,ProjectCapability.TEXTUAL_EDITOR,ProjectCapability.SYNTAX_STYLING,ProjectCapability.CONTEXTUAL_INFORMATION,ProjectCapability.LOCAL_FILE_EDITING)),
    MODELING_VALIDATION("Modeling and validation","Metamodel, models, validation and quick fixes", set(ProjectCapability.METAMODEL_DESIGN,ProjectCapability.MODEL_EDITING,ProjectCapability.VALIDATIONS,ProjectCapability.QUICK_FIXES,ProjectCapability.LOCAL_FILE_EDITING)),
    M2M_TRANSFORMATION("Model-to-model transformation","Source/target metamodels and M2M transformation support", set(ProjectCapability.METAMODEL_DESIGN,ProjectCapability.TRANSFORMATION_M2M,ProjectCapability.LOCAL_FILE_EDITING)),
    M2T_GENERATION("Model-to-text generation","Model source plus M2T/code generation support", set(ProjectCapability.METAMODEL_DESIGN,ProjectCapability.TRANSFORMATION_M2T,ProjectCapability.LOCAL_FILE_EDITING)),
    T2M_ENGINEERING("Text-to-model engineering","Grammar/parser plus target metamodel and T2M transformation support", set(ProjectCapability.GRAMMAR_DESIGN,ProjectCapability.METAMODEL_DESIGN,ProjectCapability.TRANSFORMATION_T2M,ProjectCapability.LOCAL_FILE_EDITING)),
    COMPLETE_EDITOR("Complete editor","Language, validation, events, gamification and editor generation", set(ProjectCapability.METAMODEL_DESIGN,ProjectCapability.GRAMMAR_DESIGN,ProjectCapability.MODEL_EDITING,ProjectCapability.VISUAL_EDITOR,ProjectCapability.TEXTUAL_EDITOR,ProjectCapability.SYNTAX_STYLING,ProjectCapability.CONTEXTUAL_INFORMATION,ProjectCapability.VALIDATIONS,ProjectCapability.QUICK_FIXES,ProjectCapability.EVENTS,ProjectCapability.EVENT_FLOWS,ProjectCapability.GAMIFICATION,ProjectCapability.EDITOR_GENERATION,ProjectCapability.LOCAL_FILE_EDITING)),
    COMPLETE_MDE("Complete MDE project","All HYDRA capabilities enabled", EnumSet.allOf(ProjectCapability.class));

    private final String label;
    private final String description;
    private final Set<ProjectCapability> capabilities;
    /** Descripción: Inicializa una instancia de ProjectPreset con sus dependencias y datos de entrada. Entradas: label: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; capabilities: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ProjectPreset(String label,String description,Set<ProjectCapability> capabilities){this.label=label;this.description=description;this.capabilities=Set.copyOf(ProjectCapabilityDependencies.resolve(capabilities));}
    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de ProjectPreset. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String label(){return label;}
    /** Descripción: Ejecuta la operación «description» correspondiente a la responsabilidad de ProjectPreset. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String description(){return description;}
    /** Descripción: Ejecuta la operación «capabilities» correspondiente a la responsabilidad de ProjectPreset. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<ProjectCapability> capabilities(){return capabilities;}
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de ProjectPreset. Entradas: preset: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectPreset safe(ProjectPreset preset){return preset==null?EMPTY:preset;}
    /** Descripción: Actualiza el estado gestionado por ProjectPreset mediante la operación «set». Entradas: first: dato de entrada necesario para la operación; rest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EnumSet<ProjectCapability> set(ProjectCapability first,ProjectCapability... rest){EnumSet<ProjectCapability> set=EnumSet.of(first,rest);return set;}
}
