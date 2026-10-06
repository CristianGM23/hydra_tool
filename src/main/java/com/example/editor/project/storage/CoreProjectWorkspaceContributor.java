package com.example.editor.project.storage;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.config.StorageProperties;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.ProjectWorkspaceContribution;
import com.example.editor.project.ProjectWorkspaceContributor;

/** Contribuye los repositorios actuales de metamodelos, modelos, validaciones, activos y exportaciones. */
@Component
public class CoreProjectWorkspaceContributor implements ProjectWorkspaceContributor {
    private final StorageProperties storage;

    /** Descripción: Inicializa una instancia de CoreProjectWorkspaceContributor con sus dependencias y datos de entrada. Entradas: storage: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CoreProjectWorkspaceContributor(StorageProperties storage) {
        this.storage=storage;
    }

    @Override
    /** Descripción: Ejecuta la operación «contributions» correspondiente a la responsabilidad de CoreProjectWorkspaceContributor. Entradas: layout: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<ProjectWorkspaceContribution> contributions(ProjectLayout layout) {
        return List.of(
                contribution("metamodels",storage.metamodels(),Set.of(ArtifactKind.METAMODEL,ArtifactKind.METAMODEL_DIAGRAM,ArtifactKind.GENERATED_ECORE),layout.metamodels()),
                contribution("models",storage.models(),Set.of(ArtifactKind.MODEL,ArtifactKind.MODEL_DIAGRAM),layout.semanticModels()),
                contribution("notations",storage.notations(),Set.of(ArtifactKind.NOTATION_DEFINITION),layout.notations()),
                contribution("validations",storage.validations(),Set.of(ArtifactKind.EXPRESSION_MODEL,ArtifactKind.VALIDATION_MODEL,ArtifactKind.ACTION_MODEL,ArtifactKind.OPERATION_BEHAVIOR_MODEL),layout.validations()),
                contribution("assets",storage.assets(),Set.of(ArtifactKind.ASSET),layout.assets()),
                contribution("exports",storage.exports(),Set.of(ArtifactKind.GENERATED_DRAWIO,ArtifactKind.GENERATED_PLANTUML,ArtifactKind.GENERATED_JAVA,ArtifactKind.GENERATED_PYTHON,ArtifactKind.GENERATED_IMAGE),layout.generated())
        );
    }

    /** Descripción: Ejecuta la operación «contribution» correspondiente a la responsabilidad de CoreProjectWorkspaceContributor. Entradas: id: identificador que delimita el recurso objetivo; root: ruta o ubicación utilizada por la operación; kinds: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectWorkspaceContribution contribution(String id,java.nio.file.Path root,Set<ArtifactKind> kinds,String fallback) {
        return new ProjectWorkspaceContribution(id,root,kinds,fallback);
    }
}
