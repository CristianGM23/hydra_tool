package com.example.editor.remote.storage.provider;


import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.*;
import tools.jackson.databind.ObjectMapper;

/** Adaptador GitLab Repository Files API compatible con GitLab.com y self-managed. */
@Component
public class GitLabRemoteStorageProvider implements RemoteArtifactStorageProvider {
    private final RemoteStorageHttpClient http;
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de GitLabRemoteStorageProvider con sus dependencias y datos de entrada. Entradas: http: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GitLabRemoteStorageProvider(RemoteStorageHttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de GitLabFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITLAB;}

    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de GitLabFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials) {
        if(profile.getProjectId().isBlank()) throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"GitLab project id or path is required");
        String url=RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://gitlab.com/api/v4")+"/projects/"+RemotePathPolicy.encodeSegment(profile.getProjectId())+"/repository/branches/"+RemotePathPolicy.encodeSegment(branch(profile));
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(url),headers(credentials),null);
        if(response.statusCode()<200||response.statusCode()>=300) throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"GitLab repository connection test failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de GitLabFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes) {
        GitLabFile file=metadata(profile,credentials,path,false);
        if(file==null) throw notFound(path);
        if(file.size>maximumBytes) throw tooLarge(maximumBytes);
        RemoteStorageHttpClient.BoundedResponse response=http.sendBounded("GET",RemoteProviderJsonSupport.uri(rawUrl(profile,path)+"?ref="+RemotePathPolicy.encodeSegment(branch(profile))),headers(credentials),null,maximumBytes);
        requireSuccess(response,"GitLab file download",path);
        String contentType=response.headers().firstValue("content-type").orElse("application/octet-stream");
        byte[] content=response.body();
        return new RemoteArtifact(descriptor(profileId,scope,path,file.lastCommitId,content.length,contentType,false),content);
    }

    /** Descripción: Actualiza el estado gestionado por GitLabFile mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command) {
        GitLabFile existing=metadata(profile,credentials,command.path(),true);
        enforceMode(command.mode(),existing!=null,command.path());
        if(!command.expectedVersion().isBlank()&&(existing==null||!command.expectedVersion().equals(existing.lastCommitId))) throw precondition(command.path());
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("branch",branch(profile));body.put("commit_message",command.commitMessage());body.put("encoding","base64");if(existing!=null&&!existing.lastCommitId.isBlank())body.put("last_commit_id",existing.lastCommitId);
        String method=existing==null?"POST":"PUT";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmallPublisher(method,RemoteProviderJsonSupport.uri(fileUrl(profile,command.path())),headers(credentials),RemoteProviderJsonSupport.base64Json(mapper,body,"content",command.content()));
        requireSuccess(response,"GitLab file write",command.path());
        GitLabFile refreshed=metadata(profile,credentials,command.path(),false);
        String version=refreshed==null?"":refreshed.lastCommitId;
        return descriptor(profileId,scope,command.path(),version,command.contentLength(),command.contentType(),existing==null);
    }

    /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de GitLabFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; allowMissing: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GitLabFile metadata(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,boolean allowMissing) {
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("HEAD",RemoteProviderJsonSupport.uri(fileUrl(profile,path)+"?ref="+RemotePathPolicy.encodeSegment(branch(profile))),headers(credentials),null);
        if(response.statusCode()==404&&allowMissing) return null;
        if(response.statusCode()==404) return null;
        requireSuccess(response,"GitLab file metadata",path);
        long size=response.headers().firstValue("X-Gitlab-Size").map(this::parseLong).orElse(0L);
        String lastCommit=response.headers().firstValue("X-Gitlab-Last-Commit-Id").orElse("");
        return new GitLabFile(lastCommit,size);
    }

    /** Descripción: Ejecuta la operación «fileUrl» correspondiente a la responsabilidad de GitLabFile. Entradas: profile: archivo o contenido que debe procesarse; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileUrl(RemoteStorageProperties.Profile profile,String path) {
        if(profile.getProjectId().isBlank()) throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"GitLab project id or path is required");
        return RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://gitlab.com/api/v4")+"/projects/"+RemotePathPolicy.encodeSegment(profile.getProjectId())+
                "/repository/files/"+RemotePathPolicy.encodeSegment(path);
    }
    /** Descripción: Ejecuta la operación «rawUrl» correspondiente a la responsabilidad de GitLabFile. Entradas: profile: archivo o contenido que debe procesarse; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String rawUrl(RemoteStorageProperties.Profile profile,String path){return fileUrl(profile,path)+"/raw";}
    /** Descripción: Ejecuta la operación «headers» correspondiente a la responsabilidad de GitLabFile. Entradas: credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> headers(RemoteStorageCredentials credentials){return Map.of("Authorization","Bearer "+credentials.token(),"Accept","application/json","Content-Type","application/json");}
    /** Descripción: Ejecuta la operación «branch» correspondiente a la responsabilidad de GitLabFile. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String branch(RemoteStorageProperties.Profile profile){return profile.getBranch().isBlank()?"main":profile.getBranch();}
    /** Descripción: Ejecuta la operación «parseLong» correspondiente a la responsabilidad de GitLabFile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private long parseLong(String value){try{return Long.parseLong(value);}catch(NumberFormatException ignored){return 0L;}}
    /** Descripción: Ejecuta la operación «enforceMode» correspondiente a la responsabilidad de GitLabFile. Entradas: mode: dato de entrada necesario para la operación; exists: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceMode(RemoteWriteMode mode,boolean exists,String path){if(mode==RemoteWriteMode.CREATE_ONLY&&exists)throw conflict(path);if(mode==RemoteWriteMode.UPDATE_ONLY&&!exists)throw notFound(path);}
    /** Descripción: Valida las condiciones necesarias para «requireSuccess» y rechaza entradas no válidas. Entradas: response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSuccess(RemoteStorageHttpClient.BoundedResponse response,String operation,String path){if(response.statusCode()>=200&&response.statusCode()<300)return;if(response.statusCode()==404)throw notFound(path);if(response.statusCode()==400||response.statusCode()==409)throw conflict(path);if(response.statusCode()==412)throw precondition(path);throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de GitLabFile. Entradas: profileId: identificador que delimita el recurso objetivo; scope: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; version: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; created: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteArtifactDescriptor descriptor(String profileId,RemoteArtifactScope scope,String path,String version,long size,String contentType,boolean created){return new RemoteArtifactDescriptor(profileId,type(),scope,path,version,size,contentType,"",created);}
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de GitLabFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException notFound(String p){return new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"GitLab artifact was not found: "+p);}
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de GitLabFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException conflict(String p){return new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"GitLab artifact could not be written without conflict: "+p);}
    /** Descripción: Ejecuta la operación «precondition» correspondiente a la responsabilidad de GitLabFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException precondition(String p){return new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"GitLab artifact changed since it was loaded: "+p);}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de GitLabFile. Entradas: max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException tooLarge(long max){return new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"GitLab artifact exceeds the configured limit of "+max+" bytes");}
    private record GitLabFile(String lastCommitId,long size){}
}
