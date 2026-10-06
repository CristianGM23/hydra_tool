package com.example.editor.remote.storage.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.project.ProjectSession;
import com.example.editor.remote.storage.*;
import com.example.editor.remote.auth.RemoteRequestGuard;

/** API neutral de importación, exportación y guardado remoto para proyectos y capas. */
@RestController
@RequestMapping("/api/remote-storage")
public class RemoteStorageController {
    private final RemoteStorageService remote;
    private final RemoteProjectTransferService projects;
    private final RemoteRequestGuard guard;
    private final RemoteStorageProfileManager profileManager;
    /** Descripción: Inicializa una instancia de RemoteStorageController con sus dependencias y datos de entrada. Entradas: remote: dato de entrada necesario para la operación; projects: dato de entrada necesario para la operación; guard: dato de entrada necesario para la operación; profileManager: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteStorageController(RemoteStorageService remote,RemoteProjectTransferService projects,RemoteRequestGuard guard,RemoteStorageProfileManager profileManager){this.remote=remote;this.projects=projects;this.guard=guard;this.profileManager=profileManager;}

    @GetMapping("/profiles") public List<RemoteStorageProfileDescriptor> profiles(){return remote.profiles();}
    @GetMapping("/profile-configurations") public List<RemoteStorageProfileConfiguration> profileConfigurations(){return profileManager.configurations();}
    @PostMapping("/resolve-location") public RemoteStorageLocationResolution resolveLocation(@RequestBody RemoteStorageLocationResolveRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return RemoteStorageLocationResolver.resolve(request==null?"":request.url());}
    @PutMapping("/profiles/{profileId}") public RemoteStorageProfileConfiguration saveProfile(@PathVariable String profileId,@RequestBody RemoteStorageProfileUpdateRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return profileManager.upsert(profileId,request);}
    @PostMapping("/profiles/{profileId}/test") public RemoteStorageConnectionTestResult testProfile(@PathVariable String profileId,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return remote.testConnection(profileId);}
    @DeleteMapping("/profiles/{profileId}") public ResponseEntity<Void> deleteProfile(@PathVariable String profileId,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);profileManager.delete(profileId);return ResponseEntity.noContent().build();}
    @PostMapping("/projects/export") public RemoteArtifactDescriptor exportProject(@RequestBody RemoteProjectExportRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return projects.exportActive(request);}
    @PostMapping("/projects/import") public ProjectSession importProject(@RequestBody RemoteProjectImportRequest request,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite){guard.verify(token,fetchSite);return projects.importProject(request);}

    @PostMapping(path="/layers/export",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «exportLayer» correspondiente a la responsabilidad de RemoteStorageController. Entradas: profileId: identificador que delimita el recurso objetivo; remotePath: ruta o ubicación utilizada por la operación; mode: dato de entrada necesario para la operación; expectedVersion: dato de entrada necesario para la operación; commitMessage: dato de entrada necesario para la operación; file: archivo o contenido que debe procesarse; token: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RemoteArtifactDescriptor exportLayer(@RequestParam String profileId,@RequestParam String remotePath,
            @RequestParam(defaultValue="UPSERT") RemoteWriteMode mode,@RequestParam(defaultValue="") String expectedVersion,
            @RequestParam(defaultValue="") String commitMessage,@RequestPart("file") MultipartFile file,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite) throws java.io.IOException {
        guard.verify(token,fetchSite);
        remote.validateIncomingSize(profileId,file.getSize());
        String contentType=file.getContentType()==null?MediaType.APPLICATION_OCTET_STREAM_VALUE:file.getContentType();
        return remote.write(profileId,remotePath,RemoteArtifactScope.LAYER,file.getBytes(),contentType,mode,expectedVersion,commitMessage);
    }

    @GetMapping("/layers/import")
    /** Descripción: Ejecuta la operación «importLayer» correspondiente a la responsabilidad de RemoteStorageController. Entradas: profileId: identificador que delimita el recurso objetivo; remotePath: ruta o ubicación utilizada por la operación; token: dato de entrada necesario para la operación; fetchSite: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ByteArrayResource> importLayer(@RequestParam String profileId,@RequestParam String remotePath,@RequestHeader(value="X-Hydra-Request-Token",required=false) String token,@RequestHeader(value="Sec-Fetch-Site",required=false) String fetchSite) {
        guard.verify(token,fetchSite);
        RemoteArtifact artifact=remote.read(profileId,remotePath,RemoteArtifactScope.LAYER);
        String fileName=fileName(artifact.descriptor().path());
        MediaType type=mediaType(artifact.descriptor().contentType());
        byte[] content=artifact.content();
        return ResponseEntity.ok().contentType(type).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(fileName,StandardCharsets.UTF_8).build().toString())
                .header("X-Hydra-Remote-Version",artifact.descriptor().version())
                .header("X-Hydra-Remote-Profile",artifact.descriptor().profileId())
                .body(new ByteArrayResource(content));
    }

    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de RemoteStorageController. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileName(String path){int index=path.lastIndexOf('/');return index<0?path:path.substring(index+1);}
    /** Descripción: Ejecuta la operación «mediaType» correspondiente a la responsabilidad de RemoteStorageController. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MediaType mediaType(String value){try{return value==null||value.isBlank()?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(value);}catch(IllegalArgumentException ignored){return MediaType.APPLICATION_OCTET_STREAM;}}
}
