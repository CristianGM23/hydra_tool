package com.example.editor.remote.storage.provider;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Adaptador Dropbox API v2 con transferencia acotada y credenciales solo server-side. */
@Component
public class DropboxRemoteStorageProvider implements RemoteArtifactStorageProvider {
    private final RemoteStorageHttpClient http;
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de DropboxRemoteStorageProvider con sus dependencias y datos de entrada. Entradas: http: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public DropboxRemoteStorageProvider(RemoteStorageHttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de DropboxFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.DROPBOX;}

    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de DropboxFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials) {
        if(!profile.getRepositoryUrl().isBlank()) { resolveSharedRoot(profile,credentials); return; }
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("POST",RemoteProviderJsonSupport.uri(apiBase(profile)+"/users/get_current_account"),headers(credentials,"application/json",Map.of()),new byte[0]);
        if(response.statusCode()<200||response.statusCode()>=300) throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Dropbox connection test failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de DropboxFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes) {
        DropboxFile file=metadata(profile,credentials,path);
        if(file==null) throw notFound(path);
        if(file.size()>maximumBytes) throw tooLarge(maximumBytes);
        String args=jsonText(Map.of("path",dropboxPath(profile,credentials,path)));
        RemoteStorageHttpClient.BoundedResponse response=http.sendBounded("POST",RemoteProviderJsonSupport.uri(contentBase(profile)+"/files/download"),headers(credentials,"application/octet-stream",Map.of("Dropbox-API-Arg",args)),new byte[0],maximumBytes);
        requireSuccess(response,"Dropbox file download",path);
        String contentType=response.headers().firstValue("content-type").orElse("application/octet-stream");
        byte[] content=response.body();
        return new RemoteArtifact(descriptor(profileId,scope,path,file.rev(),content.length,contentType,"",false),content);
    }

    /** Descripción: Actualiza el estado gestionado por DropboxFile mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command) {
        DropboxFile existing=metadata(profile,credentials,command.path());
        enforceMode(command.mode(),existing!=null,command.path());
        if(!command.expectedVersion().isBlank()&&(existing==null||!command.expectedVersion().equals(existing.rev()))) throw precondition(command.path());
        Object mode=uploadMode(command,existing);
        Map<String,Object> args=new LinkedHashMap<>();args.put("path",dropboxPath(profile,credentials,command.path()));args.put("mode",mode);args.put("autorename",false);args.put("mute",true);args.put("strict_conflict",true);
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("POST",RemoteProviderJsonSupport.uri(contentBase(profile)+"/files/upload"),headers(credentials,"application/octet-stream",Map.of("Dropbox-API-Arg",jsonText(args))),command.content());
        requireSuccess(response,"Dropbox file upload",command.path());
        DropboxFile written=file(RemoteProviderJsonSupport.parse(mapper,response,"Dropbox file upload"));
        return descriptor(profileId,scope,command.path(),written.rev(),written.size(),command.contentType(),"",existing==null);
    }

    /** Descripción: Ejecuta la operación «uploadMode» correspondiente a la responsabilidad de DropboxFile. Entradas: command: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object uploadMode(RemoteArtifactWriteCommand command,DropboxFile existing){
        if(!command.expectedVersion().isBlank()) return Map.of(".tag","update","update",command.expectedVersion());
        if(command.mode()==RemoteWriteMode.CREATE_ONLY) return "add";
        if(command.mode()==RemoteWriteMode.UPDATE_ONLY&&existing!=null) return Map.of(".tag","update","update",existing.rev());
        return "overwrite";
    }

    /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de DropboxFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DropboxFile metadata(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path) {
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("POST",RemoteProviderJsonSupport.uri(apiBase(profile)+"/files/get_metadata"),headers(credentials,"application/json",Map.of()),RemoteProviderJsonSupport.json(mapper,Map.of("path",dropboxPath(profile,credentials,path),"include_deleted",false)));
        if(response.statusCode()==409) return null;
        requireSuccess(response,"Dropbox file metadata",path);
        JsonNode json=RemoteProviderJsonSupport.parse(mapper,response,"Dropbox file metadata");
        if(!"file".equals(json.path(".tag").asText(""))) throw conflict("Remote Dropbox path is not a file: "+path);
        return file(json);
    }

    /** Descripción: Ejecuta la operación «file» correspondiente a la responsabilidad de DropboxFile. Entradas: json: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DropboxFile file(JsonNode json){return new DropboxFile(json.path("rev").asText(""),json.path("size").asLong(0L),json.path("path_display").asText(""));}
    /** Descripción: Ejecuta la operación «headers» correspondiente a la responsabilidad de DropboxFile. Entradas: credentials: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; extra: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> headers(RemoteStorageCredentials credentials,String contentType,Map<String,String> extra){Map<String,String> result=new LinkedHashMap<>();result.put("Authorization","Bearer "+credentials.token());result.put("Content-Type",contentType);result.putAll(extra);return result;}
    /** Descripción: Ejecuta la operación «apiBase» correspondiente a la responsabilidad de DropboxFile. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String apiBase(RemoteStorageProperties.Profile profile){return RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://api.dropboxapi.com/2");}
    /** Descripción: Ejecuta la operación «contentBase» correspondiente a la responsabilidad de DropboxFile. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String contentBase(RemoteStorageProperties.Profile profile){if(profile.getBaseUrl().isBlank())return "https://content.dropboxapi.com/2";return apiBase(profile);}
    /** Descripción: Ejecuta la operación «dropboxPath» correspondiente a la responsabilidad de DropboxFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String dropboxPath(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path){String root=resolveSharedRoot(profile,credentials);String normalized=path.startsWith("/")?path:"/"+path;String combined=root.isBlank()?normalized:root+normalized;return combined.replaceAll("/{2,}","/");}
    /** Descripción: Obtiene la información correspondiente a «resolveSharedRoot» dentro de DropboxFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolveSharedRoot(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){
        if(profile.getRepositoryUrl().isBlank()) return "";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("POST",RemoteProviderJsonSupport.uri(apiBase(profile)+"/sharing/get_shared_link_metadata"),headers(credentials,"application/json",Map.of()),RemoteProviderJsonSupport.json(mapper,Map.of("url",profile.getRepositoryUrl())));
        requireSuccess(response,"Dropbox URL resolution",profile.getRepositoryUrl());
        JsonNode json=RemoteProviderJsonSupport.parse(mapper,response,"Dropbox URL resolution");
        String path=json.path("path_lower").asText("");
        if(path.isBlank()) throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"Dropbox shared URL must resolve to an item available in the authenticated account");
        if("file".equals(json.path(".tag").asText(""))){int slash=path.lastIndexOf('/');path=slash<=0?"":path.substring(0,slash);}
        return path;
    }
    /** Descripción: Ejecuta la operación «jsonText» correspondiente a la responsabilidad de DropboxFile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonText(Object value){return new String(RemoteProviderJsonSupport.json(mapper,value),StandardCharsets.UTF_8);}
    /** Descripción: Ejecuta la operación «enforceMode» correspondiente a la responsabilidad de DropboxFile. Entradas: mode: dato de entrada necesario para la operación; exists: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceMode(RemoteWriteMode mode,boolean exists,String path){if(mode==RemoteWriteMode.CREATE_ONLY&&exists)throw conflict(path);if(mode==RemoteWriteMode.UPDATE_ONLY&&!exists)throw notFound(path);}
    /** Descripción: Valida las condiciones necesarias para «requireSuccess» y rechaza entradas no válidas. Entradas: response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSuccess(RemoteStorageHttpClient.BoundedResponse response,String operation,String path){if(response.statusCode()>=200&&response.statusCode()<300)return;if(response.statusCode()==404)throw notFound(path);if(response.statusCode()==409)throw conflict(path);if(response.statusCode()==412)throw precondition(path);throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de DropboxFile. Entradas: profileId: identificador que delimita el recurso objetivo; scope: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; version: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; webUrl: dato de entrada necesario para la operación; created: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteArtifactDescriptor descriptor(String profileId,RemoteArtifactScope scope,String path,String version,long size,String contentType,String webUrl,boolean created){return new RemoteArtifactDescriptor(profileId,type(),scope,path,version,size,contentType,webUrl,created);}
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de DropboxFile. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException notFound(String path){return new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"Dropbox artifact was not found: "+path);}
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de DropboxFile. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException conflict(String path){return new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"Dropbox artifact could not be written without conflict: "+path);}
    /** Descripción: Ejecuta la operación «precondition» correspondiente a la responsabilidad de DropboxFile. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException precondition(String path){return new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"Dropbox artifact changed since it was loaded: "+path);}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de DropboxFile. Entradas: max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException tooLarge(long max){return new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"Dropbox artifact exceeds the configured limit of "+max+" bytes");}
    private record DropboxFile(String rev,long size,String path){}
}
