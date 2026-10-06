package com.example.editor.project.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.ProjectWorkspaceContributor;

/** Resuelve contribuciones y detecta configuraciones ambiguas al arrancar. */
@Component
public class ProjectWorkspaceContributionRegistry {
    private final List<ProjectWorkspaceContributor> contributors;

    /** Descripción: Inicializa una instancia de ProjectWorkspaceContributionRegistry con sus dependencias y datos de entrada. Entradas: contributors: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectWorkspaceContributionRegistry(List<ProjectWorkspaceContributor> contributors) {
        this.contributors=contributors==null?List.of():List.copyOf(contributors);
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de Snapshot. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Snapshot snapshot(ProjectLayout layout) {
        List<ProjectWorkspaceContribution> contributions=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        Map<ArtifactKind,Path> targets=new EnumMap<>(ArtifactKind.class);
        for(ProjectWorkspaceContributor contributor:contributors) {
            for(ProjectWorkspaceContribution contribution:contributor.contributions(layout)) {
                if(!ids.add(contribution.id())) throw new IllegalStateException("Duplicated project workspace contribution: "+contribution.id());
                contributions.add(contribution);
                for(ArtifactKind kind:contribution.artifactKinds()) {
                    Path previous=targets.putIfAbsent(kind,contribution.workspaceRoot());
                    if(previous!=null&&!previous.equals(contribution.workspaceRoot())) throw new IllegalStateException("Ambiguous workspace target for "+kind);
                }
            }
        }
        return new Snapshot(contributions,targets);
    }

    public record Snapshot(List<ProjectWorkspaceContribution> contributions,Map<ArtifactKind,Path> targets) {
        public Snapshot {
            contributions=contributions==null?List.of():List.copyOf(contributions);
            targets=targets==null?Map.of():Map.copyOf(targets);
        }
    }
}
