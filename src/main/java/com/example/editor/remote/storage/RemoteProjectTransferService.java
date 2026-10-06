package com.example.editor.remote.storage;

import org.springframework.stereotype.Service;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectSession;

/** Compone la exportación/importación portable existente con el almacenamiento remoto neutral. */
@Service
public class RemoteProjectTransferService {
    private final ProjectApplicationService projects;
    private final RemoteStorageService remote;
    /** Descripción: Inicializa una instancia de RemoteProjectTransferService con sus dependencias y datos de entrada. Entradas: projects: dato de entrada necesario para la operación; remote: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteProjectTransferService(ProjectApplicationService projects,RemoteStorageService remote){this.projects=projects;this.remote=remote;}

    /** Descripción: Ejecuta la operación «exportActive» correspondiente a la responsabilidad de RemoteProjectTransferService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteArtifactDescriptor exportActive(RemoteProjectExportRequest request) {
        if(request==null) throw invalid("Remote project export request is required");
        ProjectSession session=projects.session();
        if(!session.active()||session.project()==null) throw invalid("Open a HYDRA project before saving it remotely");
        String path=request.remotePath().isBlank()?session.project().id()+".hydra-project.zip":request.remotePath();
        String message=request.commitMessage().isBlank()?"Save HYDRA project "+session.project().name():request.commitMessage();
        return remote.write(request.profileId(),path,RemoteArtifactScope.PROJECT,projects.exportArchive(),"application/zip",request.mode(),request.expectedVersion(),message);
    }

    /** Descripción: Ejecuta la operación «importProject» correspondiente a la responsabilidad de RemoteProjectTransferService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectSession importProject(RemoteProjectImportRequest request) {
        if(request==null) throw invalid("Remote project import request is required");
        if(request.parentDirectory().isBlank()) throw invalid("Parent directory is required");
        if(request.projectName().isBlank()) throw invalid("Project name is required");
        RemoteArtifact artifact=remote.read(request.profileId(),request.remotePath(),RemoteArtifactScope.PROJECT);
        return projects.importArchive(request.parentDirectory(),request.projectName(),artifact.content());
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de RemoteProjectTransferService. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}
}
