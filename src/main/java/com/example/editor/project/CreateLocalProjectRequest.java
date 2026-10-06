package com.example.editor.project;

import java.util.Set;

/** Petición de creación de un proyecto local homogéneo con preset no restrictivo y capacidades personalizables. */
public record CreateLocalProjectRequest(
        String parentDirectory,
        String projectId,
        String name,
        String description,
        boolean initializeFromCurrentWorkspace,
        ProjectPreset preset,
        Set<ProjectCapability> enabledCapabilities,
        ProjectExperienceMode experienceMode
) {
    /** Compatibilidad con clientes/tests anteriores: reproduce el antiguo proyecto completo. */
    /** Descripción: Inicializa una instancia de CreateLocalProjectRequest con sus dependencias y datos de entrada. Entradas: parentDirectory: dato de entrada necesario para la operación; projectId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; initializeFromCurrentWorkspace: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CreateLocalProjectRequest(String parentDirectory,String projectId,String name,String description,boolean initializeFromCurrentWorkspace){
        this(parentDirectory,projectId,name,description,initializeFromCurrentWorkspace,ProjectPreset.COMPLETE_MDE,null,ProjectExperienceMode.FREE);
    }
    /** Descripción: Obtiene la información correspondiente a «resolvedPreset» dentro de CreateLocalProjectRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectPreset resolvedPreset(){
        // A request without any new 1.1 fields is a legacy 1.0 client and keeps the former all-capabilities behavior.
        return preset==null && enabledCapabilities==null && experienceMode==null ? ProjectPreset.COMPLETE_MDE : ProjectPreset.safe(preset);
    }
    /** Descripción: Obtiene la información correspondiente a «resolvedCapabilities» dentro de CreateLocalProjectRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<ProjectCapability> resolvedCapabilities(){
        Set<ProjectCapability> requested=enabledCapabilities==null?resolvedPreset().capabilities():enabledCapabilities;
        return Set.copyOf(ProjectCapabilityDependencies.resolve(requested));
    }
    /** Descripción: Obtiene la información correspondiente a «resolvedExperienceMode» dentro de CreateLocalProjectRequest. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectExperienceMode resolvedExperienceMode(){
        return experienceMode==null ? (preset==null&&enabledCapabilities==null?ProjectExperienceMode.FREE:ProjectExperienceMode.GUIDED) : experienceMode;
    }
}
