package com.example.editor.project;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Resuelve dependencias entre capacidades para que un proyecto nunca quede estructuralmente incoherente. */
public final class ProjectCapabilityDependencies {
    private static final Map<ProjectCapability,Set<ProjectCapability>> REQUIRED = new EnumMap<>(ProjectCapability.class);
    static {
        require(ProjectCapability.VISUAL_MODELING, ProjectCapability.METAMODEL_DESIGN, ProjectCapability.MODEL_EDITING);
        require(ProjectCapability.TEXTUAL_MODELING, ProjectCapability.GRAMMAR_DESIGN, ProjectCapability.MODEL_EDITING);
        require(ProjectCapability.VISUAL_EDITOR, ProjectCapability.METAMODEL_DESIGN, ProjectCapability.EDITOR_DEFINITIONS);
        require(ProjectCapability.TEXTUAL_EDITOR, ProjectCapability.GRAMMAR_DESIGN, ProjectCapability.EDITOR_DEFINITIONS);
        require(ProjectCapability.SYNTAX_STYLING, ProjectCapability.GRAMMAR_DESIGN, ProjectCapability.TEXTUAL_EDITOR);
        require(ProjectCapability.CONTEXTUAL_INFORMATION, ProjectCapability.GRAMMAR_DESIGN, ProjectCapability.TEXTUAL_EDITOR);
        require(ProjectCapability.QUICK_FIXES, ProjectCapability.VALIDATIONS);
        require(ProjectCapability.EVENT_FLOWS, ProjectCapability.EVENTS);
        require(ProjectCapability.GAMIFICATION, ProjectCapability.EVENTS);
        require(ProjectCapability.TRANSFORMATION_M2M, ProjectCapability.METAMODEL_DESIGN);
        require(ProjectCapability.TRANSFORMATION_M2T, ProjectCapability.METAMODEL_DESIGN);
        require(ProjectCapability.TRANSFORMATION_T2M, ProjectCapability.GRAMMAR_DESIGN, ProjectCapability.METAMODEL_DESIGN);
        require(ProjectCapability.EDITOR_GENERATION, ProjectCapability.EDITOR_DEFINITIONS);
    }
    /** Descripción: Inicializa una instancia de ProjectCapabilityDependencies con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ProjectCapabilityDependencies() {}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: capability: dato de entrada necesario para la operación; dependencies: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void require(ProjectCapability capability,ProjectCapability... dependencies) { REQUIRED.put(capability,Set.of(dependencies)); }
    /** Descripción: Ejecuta la operación «direct» correspondiente a la responsabilidad de ProjectCapabilityDependencies. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Set<ProjectCapability> direct(ProjectCapability capability) { return REQUIRED.getOrDefault(capability,Set.of()); }
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de ProjectCapabilityDependencies. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static EnumSet<ProjectCapability> resolve(Set<ProjectCapability> requested) {
        EnumSet<ProjectCapability> result=requested==null||requested.isEmpty()?EnumSet.noneOf(ProjectCapability.class):EnumSet.copyOf(requested);
        boolean changed;
        do {
            changed=false;
            for(ProjectCapability capability:EnumSet.copyOf(result)) for(ProjectCapability dependency:direct(capability)) changed|=result.add(dependency);
        } while(changed);
        return result;
    }
}
