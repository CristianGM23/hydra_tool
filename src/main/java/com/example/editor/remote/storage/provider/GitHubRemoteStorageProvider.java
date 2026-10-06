package com.example.editor.remote.storage.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Adaptador GitHub Contents API, sin SDK y con credenciales solo server-side. */
@Component
public class GitHubRemoteStorageProvider implements RemoteArtifactStorageProvider {
    private final RemoteStorageHttpClient http;
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de GitHubRemoteStorageProvider con sus dependencias y datos de entrada. Entradas: http: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GitHubRemoteStorageProvider(RemoteStorageHttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de GitHubFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITHUB;}

    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de GitHubFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials) {
        requireRepository(profile);
        String url=RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://api.github.com")+"/repos/"+RemotePathPolicy.encodeSegment(profile.getOwner())+"/"+RemotePathPolicy.encodeSegment(profile.getRepository())+"/branches/"+RemotePathPolicy.encodeSegment(branch(profile));
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(url),headers(profile,credentials,"application/vnd.github+json"),null);
        if(response.statusCode()<200||response.statusCode()>=300) throw remote("GitHub repository connection test",response.statusCode(),RemoteProviderJsonSupport.text(response));
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de GitHubFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes) {
        GitHubFile file=metadata(profile,credentials,path,false);
        if(file==null) throw notFound(path);
        if(file.size>maximumBytes) throw tooLarge(maximumBytes);
        RemoteStorageHttpClient.BoundedResponse raw=http.sendBounded("GET",RemoteProviderJsonSupport.uri(contentsUrl(profile,path)+"?ref="+RemotePathPolicy.encodeSegment(branch(profile))),
                headers(profile,credentials,"application/vnd.github.raw+json"),null,maximumBytes);
        requireSuccess(raw,"GitHub file download",path);
        String contentType=raw.headers().firstValue("content-type").orElse("application/octet-stream");
        byte[] content=raw.body();
        return new RemoteArtifact(descriptor(profileId,scope,path,file.sha,content.length,contentType,file.htmlUrl,false),content);
    }

    /** Descripción: Actualiza el estado gestionado por GitHubFile mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command) {
        GitHubFile existing=metadata(profile,credentials,command.path(),true);
        enforceMode(command.mode(),existing!=null,command.path());
        if(!command.expectedVersion().isBlank()&&(existing==null||!command.expectedVersion().equals(existing.sha))) throw precondition(command.path());
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("message",command.commitMessage());body.put("branch",branch(profile));if(existing!=null)body.put("sha",existing.sha);
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmallPublisher("PUT",RemoteProviderJsonSupport.uri(contentsUrl(profile,command.path())),headers(profile,credentials,"application/vnd.github+json"),RemoteProviderJsonSupport.base64Json(mapper,body,"content",command.content()));
        requireSuccess(response,"GitHub file write",command.path());
        JsonNode json=RemoteProviderJsonSupport.parse(mapper,response,"GitHub file write");
        String sha=json.path("content").path("sha").asText(existing==null?"":existing.sha);
        String html=json.path("content").path("html_url").asText("");
        return descriptor(profileId,scope,command.path(),sha,command.contentLength(),command.contentType(),html,existing==null);
    }

    /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de GitHubFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; allowMissing: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GitHubFile metadata(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,boolean allowMissing) {
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(contentsUrl(profile,path)+"?ref="+RemotePathPolicy.encodeSegment(branch(profile))),headers(profile,credentials,"application/vnd.github.object+json"),null);
        if(response.statusCode()==404&&allowMissing) return null;
        if(response.statusCode()==404) return null;
        requireSuccess(response,"GitHub file metadata",path);
        JsonNode json=RemoteProviderJsonSupport.parse(mapper,response,"GitHub file metadata");
        return new GitHubFile(json.path("sha").asText(""),json.path("size").asLong(0),json.path("html_url").asText(""));
    }

    /** Descripción: Ejecuta la operación «contentsUrl» correspondiente a la responsabilidad de GitHubFile. Entradas: profile: archivo o contenido que debe procesarse; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String contentsUrl(RemoteStorageProperties.Profile profile,String path) {
        requireRepository(profile);
        return RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://api.github.com")+"/repos/"+RemotePathPolicy.encodeSegment(profile.getOwner())+"/"+
                RemotePathPolicy.encodeSegment(profile.getRepository())+"/contents/"+RemotePathPolicy.encodePath(path);
    }
    /** Descripción: Ejecuta la operación «headers» correspondiente a la responsabilidad de GitHubFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; accept: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> headers(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String accept) {
        Map<String,String> result=new LinkedHashMap<>();
        result.put("Accept",accept);
        result.put("Authorization","Bearer "+credentials.token());
        if(!profile.getApiVersion().isBlank()) result.put("X-GitHub-Api-Version",profile.getApiVersion());
        result.put("Content-Type","application/json");
        return result;
    }
    /** Descripción: Ejecuta la operación «branch» correspondiente a la responsabilidad de GitHubFile. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String branch(RemoteStorageProperties.Profile profile){return profile.getBranch().isBlank()?"main":profile.getBranch();}
    /** Descripción: Valida las condiciones necesarias para «requireRepository» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireRepository(RemoteStorageProperties.Profile profile){if(profile.getOwner().isBlank()||profile.getRepository().isBlank()) throw invalid("GitHub owner and repository are required");}
    /** Descripción: Ejecuta la operación «enforceMode» correspondiente a la responsabilidad de GitHubFile. Entradas: mode: dato de entrada necesario para la operación; exists: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceMode(RemoteWriteMode mode,boolean exists,String path){if(mode==RemoteWriteMode.CREATE_ONLY&&exists) throw conflict(path);if(mode==RemoteWriteMode.UPDATE_ONLY&&!exists) throw notFound(path);}
    /** Descripción: Valida las condiciones necesarias para «requireSuccess» y rechaza entradas no válidas. Entradas: response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSuccess(RemoteStorageHttpClient.BoundedResponse response,String operation,String path){if(response.statusCode()>=200&&response.statusCode()<300)return;if(response.statusCode()==404)throw notFound(path);if(response.statusCode()==409||response.statusCode()==422)throw conflict(path);if(response.statusCode()==412)throw precondition(path);throw remote(operation,response.statusCode(),RemoteProviderJsonSupport.text(response));}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de GitHubFile. Entradas: profileId: identificador que delimita el recurso objetivo; scope: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; version: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; webUrl: dato de entrada necesario para la operación; created: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteArtifactDescriptor descriptor(String profileId,RemoteArtifactScope scope,String path,String version,long size,String contentType,String webUrl,boolean created){return new RemoteArtifactDescriptor(profileId,type(),scope,path,version,size,contentType,webUrl,created);}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de GitHubFile. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String m){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,m);}
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de GitHubFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException notFound(String p){return new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"GitHub artifact was not found: "+p);}
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de GitHubFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException conflict(String p){return new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"GitHub artifact could not be written without conflict: "+p);}
    /** Descripción: Ejecuta la operación «precondition» correspondiente a la responsabilidad de GitHubFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException precondition(String p){return new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"GitHub artifact changed since it was loaded: "+p);}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de GitHubFile. Entradas: max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException tooLarge(long max){return new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"GitHub artifact exceeds the configured limit of "+max+" bytes");}
    /** Descripción: Ejecuta la operación «remote» correspondiente a la responsabilidad de GitHubFile. Entradas: op: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException remote(String op,int status,String text){return new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,op+" failed with HTTP "+status+": "+text);}
    private record GitHubFile(String sha,long size,String htmlUrl){}
}
