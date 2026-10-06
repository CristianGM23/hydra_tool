package com.example.editor.project.storage;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.ProjectWorkspaceContributor;

/** Contribuye perfiles de integración e inyección. */
@Component
public class IntegrationProjectWorkspaceContributor implements ProjectWorkspaceContributor {
    private final IntegrationProfileProperties properties;

    /** Descripción: Inicializa una instancia de IntegrationProjectWorkspaceContributor con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public IntegrationProjectWorkspaceContributor(IntegrationProfileProperties properties) {
        this.properties=properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «contributions» correspondiente a la responsabilidad de IntegrationProjectWorkspaceContributor. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectWorkspaceContribution> contributions(ProjectLayout layout) {
        return List.of(new ProjectWorkspaceContribution("integrations",properties.getDirectory(),Set.of(ArtifactKind.INTEGRATION_PROFILE),layout.integrations()));
    }
}
