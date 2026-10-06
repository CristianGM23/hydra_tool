package com.example.editor.remote.git;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.remote.auth.RemoteRequestGuard;

/** API protegida para las operaciones Git reales del proyecto local activo. */
@RestController
@RequestMapping("/api/remote-git")
public class RemoteGitController {
    private final RemoteGitWorkspaceService git;
    private final RemoteRequestGuard guard;
    /** Descripción: Inicializa una instancia de RemoteGitController con sus dependencias y datos de entrada. Entradas: git: dato de entrada necesario para la operación; guard: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteGitController(RemoteGitWorkspaceService git,RemoteRequestGuard guard){this.git=git;this.guard=guard;}

    @PostMapping("/{operation}")
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de RemoteGitController. Entradas: operation: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; token: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitOperationResult execute(@PathVariable RemoteGitOperation operation,@RequestBody RemoteGitOperationRequest request,
            @RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){
        guard.verify(token,fetchSite);return git.execute(operation,request);
    }

    @GetMapping("/repository-tree/{profileId}")
    /** Descripción: Ejecuta la operación «repositoryTree» correspondiente a la responsabilidad de RemoteGitController. Entradas: profileId: identificador que delimita el recurso objetivo; branch: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitRepositoryTree repositoryTree(@PathVariable String profileId,@RequestParam(value="branch",required=false) String branch,
            @RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){
        guard.verify(token,fetchSite);return git.repositoryTree(profileId,branch);
    }

    @GetMapping("/conflicts/{profileId}")
    /** Descripción: Ejecuta la operación «conflictView» correspondiente a la responsabilidad de RemoteGitController. Entradas: profileId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; token: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitConflictFileView conflictView(@PathVariable String profileId,@RequestParam("path") String path,
            @RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){
        guard.verify(token,fetchSite);return git.conflictView(profileId,path);
    }

    @PostMapping("/conflicts/resolve")
    /** Descripción: Obtiene la información correspondiente a «resolveConflict» dentro de RemoteGitController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; token: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteGitConflictResolutionResult resolveConflict(@RequestBody RemoteGitConflictResolutionRequest request,
            @RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){
        guard.verify(token,fetchSite);return git.resolveConflict(request);
    }
}
