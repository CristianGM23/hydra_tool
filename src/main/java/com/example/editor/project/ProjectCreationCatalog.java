package com.example.editor.project;

import java.util.Arrays;
import java.util.List;

/** Catálogo estable consumido por el wizard para no duplicar presets/dependencias en frontend. */
public record ProjectCreationCatalog(List<ProjectPresetDescriptor> presets,List<ProjectCapabilityDescriptor> capabilities) {
    /** Descripción: Ejecuta la operación «current» correspondiente a la responsabilidad de ProjectCreationCatalog. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ProjectCreationCatalog current(){
        return new ProjectCreationCatalog(
                Arrays.stream(ProjectPreset.values()).map(p->new ProjectPresetDescriptor(p.name(),p.label(),p.description(),p.capabilities())).toList(),
                Arrays.stream(ProjectCapability.values()).map(c->new ProjectCapabilityDescriptor(c.name(),ProjectCapabilityDependencies.direct(c))).toList());
    }
}
