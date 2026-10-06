package com.example.editor.project.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectDeletionDependency;
import com.example.editor.project.ProjectDeletionImpact;
import com.example.editor.project.ProjectDeletionResult;
import com.example.editor.project.ProjectDescriptor;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectNodeType;
import com.example.editor.project.ProjectSession;
import com.example.editor.project.ProjectTreeNode;
import com.example.editor.project.ProjectTreeView;

/**
 * Ciclo de vida destructivo del proyecto local.
 *
 * Mantiene el borrado completo separado del DELETE de entradas: la raíz del proyecto
 * nunca pasa por ProjectStoragePort.delete(relativePath, ...), por lo que no se
 * debilitan las protecciones de estructura reservada del explorador.
 */
@Service
public class ProjectDeletionService {
    private final ProjectApplicationService projects;
    private final SafeProjectPathResolver paths;
    private final ProjectFileOperations files;
    private final ProjectRegistryStore registry;

    /** Descripción: Inicializa una instancia de ProjectDeletionService con sus dependencias y datos de entrada. Entradas: projects: dato de entrada necesario para la operación; paths: ruta o ubicación utilizada por la operación; files: archivo o contenido que debe procesarse; registry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectDeletionService(ProjectApplicationService projects,SafeProjectPathResolver paths,ProjectFileOperations files,ProjectRegistryStore registry) {
        this.projects=projects;
        this.paths=paths;
        this.files=files;
        this.registry=registry;
    }

    /** Descripción: Ejecuta la operación «analyzeActiveProject» correspondiente a la responsabilidad de ProjectDeletionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectDeletionImpact analyzeActiveProject() {
        ProjectSession session=requireActive();
        Path root=verifiedProjectRoot(session);
        ProjectTreeNode physical=projects.tree(ProjectTreeView.PHYSICAL);
        Aggregate total=aggregate(physical);
        List<ProjectDeletionDependency> dependencies=new ArrayList<>();
        for(ProjectTreeNode child:physical.children()) {
            if(child.relativePath().equals(ProjectDescriptorStore.FILE_NAME)) continue;
            Aggregate group=aggregate(child);
            dependencies.add(new ProjectDeletionDependency(child.relativePath(),kind(child.artifactKind()),group.entries(),group.bytes()));
        }
        ProjectDescriptor descriptor=session.project();
        return new ProjectDeletionImpact(descriptor.id(),descriptor.name(),root.toString(),total.files(),total.directories(),total.bytes(),dependencies);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteActiveProject» de forma controlada. Entradas: deleteDependencies: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectDeletionResult deleteActiveProject(boolean deleteDependencies) {
        ProjectDeletionImpact impact=analyzeActiveProject();
        if(impact.hasDependencies() && !deleteDependencies) {
            throw new ProjectException(ProjectException.Reason.CONFLICT,
                    "The project contains dependent artifacts. Confirm deletion including dependencies to remove the complete project.");
        }
        Path root=paths.projectRoot(impact.directory());
        verifyDeletionBoundary(root);

        // Close first so no tab/session writer can recreate files while the directory is being removed.
        projects.close();
        files.deleteRecursively(root);
        registry.forget(root.toString());
        return new ProjectDeletionResult(impact.projectId(),impact.projectName(),impact.directory(),impact.fileCount()+impact.directoryCount(),impact.totalBytes());
    }

    /** Descripción: Valida las condiciones necesarias para «requireActive» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectSession requireActive() {
        ProjectSession session=projects.session();
        if(!session.active() || session.project()==null || session.directory()==null || session.directory().isBlank()) {
            throw new ProjectException(ProjectException.Reason.NOT_FOUND,"No local project is open");
        }
        return session;
    }

    /** Descripción: Ejecuta la operación «verifiedProjectRoot» correspondiente a la responsabilidad de ProjectDeletionService. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path verifiedProjectRoot(ProjectSession session) {
        Path root=paths.projectRoot(session.directory());
        verifyDeletionBoundary(root);
        return root;
    }

    /** Descripción: Valida las condiciones necesarias para «verifyDeletionBoundary» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void verifyDeletionBoundary(Path root) {
        Path normalized=root.toAbsolutePath().normalize();
        if(normalized.getParent()==null || normalized.equals(normalized.getRoot())) {
            throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"Refusing to delete an unsafe project root");
        }
        if(!Files.isRegularFile(normalized.resolve(ProjectDescriptorStore.FILE_NAME))) {
            throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,"The selected directory is not a HYDRA project root");
        }
    }

    /** Descripción: Ejecuta la operación «aggregate» correspondiente a la responsabilidad de ProjectDeletionService. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Aggregate aggregate(ProjectTreeNode node) {
        int filesCount=node.nodeType()==ProjectNodeType.FILE?1:0;
        int directoryCount=node.nodeType()==ProjectNodeType.DIRECTORY || node.nodeType()==ProjectNodeType.GROUP?1:0;
        long bytes=node.nodeType()==ProjectNodeType.FILE?Math.max(0L,node.size()):0L;
        int entries=1;
        for(ProjectTreeNode child:node.children()) {
            Aggregate nested=aggregate(child);
            filesCount+=nested.files();
            directoryCount+=nested.directories();
            bytes+=nested.bytes();
            entries+=nested.entries();
        }
        return new Aggregate(filesCount,directoryCount,entries,bytes);
    }

    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de ProjectDeletionService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String kind(ArtifactKind kind) { return kind==null?"GENERIC_FILE":kind.name(); }
    private record Aggregate(int files,int directories,int entries,long bytes) { }
}
