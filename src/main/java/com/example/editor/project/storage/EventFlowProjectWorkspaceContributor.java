package com.example.editor.project.storage;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.ProjectWorkspaceContributor;

/** Integra los documentos EventFlowModel con el proyecto local sin acoplar su runtime. */
@Component
public class EventFlowProjectWorkspaceContributor implements ProjectWorkspaceContributor {
    private final EventFlowProperties properties;

    /** Descripción: Inicializa una instancia de EventFlowProjectWorkspaceContributor con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowProjectWorkspaceContributor(EventFlowProperties properties) {
        this.properties=properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «contributions» correspondiente a la responsabilidad de EventFlowProjectWorkspaceContributor. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectWorkspaceContribution> contributions(ProjectLayout layout) {
        return List.of(
                new ProjectWorkspaceContribution("event-flows",properties.getDirectory(),
                        Set.of(ArtifactKind.EVENT_FLOW_MODEL),layout.eventFlows()),
                new ProjectWorkspaceContribution("event-flow-diagrams",properties.getDirectory(),
                        Set.of(ArtifactKind.EVENT_FLOW_DIAGRAM,ArtifactKind.EVENT_FLOW_RUNTIME),layout.eventFlows()));
    }
}
