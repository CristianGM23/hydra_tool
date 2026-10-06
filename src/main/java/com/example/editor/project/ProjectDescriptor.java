package com.example.editor.project;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Documento raíz portable de un proyecto HYDRA. */
public record ProjectDescriptor(
        String schemaVersion,
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        ProjectStorageMode storageMode,
        ProjectLayout layout,
        Set<ProjectCapability> enabledCapabilities,
        Map<String,String> annotations,
        ProjectPreset createdFromPreset,
        ProjectExperienceMode experienceMode,
        ProjectPrimaryArtifact primaryArtifact
) {
    public ProjectDescriptor {
        schemaVersion = required(schemaVersion,"schemaVersion");
        id = required(id,"id");
        name = required(name,"name");
        description = description == null ? "" : description;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        storageMode = storageMode == null ? ProjectStorageMode.LOCAL_DIRECTORY : storageMode;
        layout = layout == null ? ProjectLayout.standard() : layout;
        enabledCapabilities = enabledCapabilities == null ? Set.of() : Set.copyOf(ProjectCapabilityDependencies.resolve(enabledCapabilities));
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
        boolean legacy = "1.0".equals(schemaVersion);
        createdFromPreset = createdFromPreset == null ? (legacy ? ProjectPreset.COMPLETE_MDE : ProjectPreset.EMPTY) : createdFromPreset;
        experienceMode = experienceMode == null ? (legacy ? ProjectExperienceMode.FREE : ProjectExperienceMode.GUIDED) : experienceMode;
        primaryArtifact = primaryArtifact == null ? new ProjectPrimaryArtifact("","","") : primaryArtifact;
    }

    /** Compatibilidad binaria/fuente con descriptores construidos antes del esquema 1.1. */
    /** Descripción: Inicializa una instancia de ProjectDescriptor con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; createdAt: dato de entrada necesario para la operación; updatedAt: dato de entrada necesario para la operación; storageMode: dato de entrada necesario para la operación; layout: dato de entrada necesario para la operación; enabledCapabilities: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectDescriptor(String schemaVersion,String id,String name,String description,Instant createdAt,Instant updatedAt,ProjectStorageMode storageMode,ProjectLayout layout,Set<ProjectCapability> enabledCapabilities,Map<String,String> annotations){
        this(schemaVersion,id,name,description,createdAt,updatedAt,storageMode,layout,enabledCapabilities,annotations,ProjectPreset.COMPLETE_MDE,ProjectExperienceMode.FREE,new ProjectPrimaryArtifact("","",""));
    }

    /** Descripción: Ejecuta la operación «touch» correspondiente a la responsabilidad de ProjectDescriptor. Entradas: instant: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectDescriptor touch(Instant instant) {
        return new ProjectDescriptor(schemaVersion,id,name,description,createdAt,instant,storageMode,layout,enabledCapabilities,annotations,createdFromPreset,experienceMode,primaryArtifact);
    }

    /** Descripción: Ejecuta la operación «configure» correspondiente a la responsabilidad de ProjectDescriptor. Entradas: capabilities: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; primary: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectDescriptor configure(Set<ProjectCapability> capabilities,ProjectExperienceMode mode,ProjectPrimaryArtifact primary) {
        return new ProjectDescriptor(schemaVersion,id,name,description,createdAt,Instant.now(),storageMode,layout,
                capabilities==null?enabledCapabilities:capabilities,annotations,createdFromPreset,mode==null?experienceMode:mode,primary==null?primaryArtifact:primary);
    }

    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value,String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
