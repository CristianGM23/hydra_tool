package com.example.editor.project.storage;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.runtime.session.EditorSessionProperties;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.ProjectWorkspaceContributor;

/** Contribuye las definiciones configurables de editores y sus elementos visuales. */
@Component
public class EditorProjectWorkspaceContributor implements ProjectWorkspaceContributor {
    private final EditorDefinitionProperties properties;
    private final EditorSessionProperties sessionProperties;

    /** Descripción: Inicializa una instancia de EditorProjectWorkspaceContributor con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; sessionProperties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EditorProjectWorkspaceContributor(EditorDefinitionProperties properties, EditorSessionProperties sessionProperties) {
        this.properties=properties;
        this.sessionProperties=sessionProperties;
    }

    @Override
    /** Descripción: Ejecuta la operación «contributions» correspondiente a la responsabilidad de EditorProjectWorkspaceContributor. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectWorkspaceContribution> contributions(ProjectLayout layout) {
        return List.of(
                new ProjectWorkspaceContribution("editors",properties.getDirectory(),Set.of(
                        ArtifactKind.EDITOR_DEFINITION,ArtifactKind.REPRESENTATION_DEFINITION,ArtifactKind.PALETTE_DEFINITION,ArtifactKind.FORM_DEFINITION),layout.editors()),
                new ProjectWorkspaceContribution("editor-sessions",sessionProperties.getDirectory(),Set.of(ArtifactKind.EDITOR_MODEL_SESSION,ArtifactKind.EDITOR_SESSION_DIAGRAM),layout.semanticModels()));
    }
}
