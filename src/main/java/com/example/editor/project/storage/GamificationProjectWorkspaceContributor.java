package com.example.editor.project.storage;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.ProjectWorkspaceContributor;

/** Integra definiciones y bindings de gamificación como fuentes portables del proyecto. */
@Component
public class GamificationProjectWorkspaceContributor implements ProjectWorkspaceContributor {
    private final GamificationProperties properties;

    /** Descripción: Inicializa una instancia de GamificationProjectWorkspaceContributor con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationProjectWorkspaceContributor(GamificationProperties properties) {
        this.properties=properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «contributions» correspondiente a la responsabilidad de GamificationProjectWorkspaceContributor. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectWorkspaceContribution> contributions(ProjectLayout layout) {
        return List.of(
                new ProjectWorkspaceContribution("gamification-definitions",properties.getDirectory(),
                        Set.of(ArtifactKind.GAMIFICATION_DEFINITION_MODEL),layout.gamificationDefinitions()),
                new ProjectWorkspaceContribution("gamification-bindings",properties.getDirectory(),
                        Set.of(ArtifactKind.GAMIFICATION_BINDING_MODEL),layout.gamificationBindings()),
                new ProjectWorkspaceContribution("gamification-diagrams",properties.getDirectory(),
                        Set.of(ArtifactKind.GAMIFICATION_DIAGRAM),layout.gamificationDefinitions()));
    }
}
