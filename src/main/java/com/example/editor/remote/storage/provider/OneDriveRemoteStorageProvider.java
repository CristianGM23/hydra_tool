package com.example.editor.remote.storage.provider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Adaptador Microsoft Graph para OneDrive mediante rutas de driveItem. */
@Component
public class OneDriveRemoteStorageProvider implements RemoteArtifactStorageProvider {
    private final RemoteStorageHttpClient http;
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de OneDriveRemoteStorageProvider con sus dependencias y datos de entrada. Entradas: http: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public OneDriveRemoteStorageProvider(RemoteStorageHttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de OneDriveFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.ONEDRIVE;}

    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de OneDriveFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials) {
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(rootBase(profile,credentials)+"?$select=id,name,webUrl,folder"),headers(credentials,"application/json",Map.of()),null);
        if(response.statusCode()<200||response.statusCode()>=300) throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"OneDrive connection test failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de OneDriveFile. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes) {
        OneDriveFile file=metadata(profile,credentials,path);
        if(file==null) throw notFound(path);
        if(file.size>maximumBytes) throw tooLarge(maximumBytes);
        RemoteStorageHttpClient.BoundedResponse response=http.sendBounded("GET",RemoteProviderJsonSupport.uri(contentUrl(profile,credentials,path)),headers(credentials,"application/octet-stream",Map.of()),null,maximumBytes);
        if(response.statusCode()>=300&&response.statusCode()<400){
            String location=response.headers().firstValue("location").orElse("");
            if(location.isBlank()) throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"OneDrive download redirect did not include a safe location");
            response=http.sendUnauthenticatedBounded(RemoteProviderJsonSupport.uri(location),maximumBytes);
        }
        requireSuccess(response,"OneDrive file download",path);
        String contentType=response.headers().firstValue("content-type").orElse("application/octet-stream");
        byte[] content=response.body();
        return new RemoteArtifact(descriptor(profileId,scope,path,file.eTag,content.length,contentType,file.webUrl,false),content);
    }

    /** Descripción: Actualiza el estado gestionado por OneDriveFile mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command) {
        OneDriveFile existing=metadata(profile,credentials,command.path());
        enforceMode(command.mode(),existing!=null,command.path());
        if(existing==null) ensureParentFolders(profile,credentials,command.path());
        if(!command.expectedVersion().isBlank()&&(existing==null||!command.expectedVersion().equals(existing.eTag))) throw precondition(command.path());
        Map<String,String> extra=new LinkedHashMap<>();
        if(!command.expectedVersion().isBlank()) extra.put("If-Match",command.expectedVersion());
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("PUT",RemoteProviderJsonSupport.uri(contentUrl(profile,credentials,command.path())),headers(credentials,command.contentType(),extra),command.content());
        requireSuccess(response,"OneDrive file write",command.path());
        JsonNode json=RemoteProviderJsonSupport.parse(mapper,response,"OneDrive file write");
        String version=json.path("eTag").asText("");
        String web=json.path("webUrl").asText("");
        long size=json.path("size").asLong(command.contentLength());
        return descriptor(profileId,scope,command.path(),version,size,command.contentType(),web,existing==null);
    }

    /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de OneDriveFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private OneDriveFile metadata(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path) {
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(itemUrl(profile,credentials,path)+"?$select=id,name,size,eTag,webUrl,folder"),headers(credentials,"application/json",Map.of()),null);
        if(response.statusCode()==404) return null;
        requireSuccess(response,"OneDrive file metadata",path);
        JsonNode json=RemoteProviderJsonSupport.parse(mapper,response,"OneDrive file metadata");
        return new OneDriveFile(json.path("eTag").asText(""),json.path("webUrl").asText(""),json.path("size").asLong(0),json.has("folder"));
    }

    /** Descripción: Valida las condiciones necesarias para «ensureParentFolders» y rechaza entradas no válidas. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureParentFolders(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path) {
        String[] segments=path.split("/");
        if(segments.length<2) return;
        String parent="";
        for(int index=0;index<segments.length-1;index++) {
            String segment=segments[index];
            String current=parent.isBlank()?segment:parent+"/"+segment;
            OneDriveFile existing=metadata(profile,credentials,current);
            if(existing!=null) {
                if(!existing.folder) throw conflict("Remote parent is not a folder: "+current);
                parent=current;
                continue;
            }
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("name",segment);
            body.put("folder",Map.of());
            body.put("@microsoft.graph.conflictBehavior","fail");
            RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("POST",RemoteProviderJsonSupport.uri(childrenUrl(profile,credentials,parent)),headers(credentials,"application/json",Map.of()),RemoteProviderJsonSupport.json(mapper,body));
            if(response.statusCode()==409) {
                OneDriveFile raced=metadata(profile,credentials,current);
                if(raced==null||!raced.folder) throw conflict(current);
            } else requireSuccess(response,"OneDrive folder creation",current);
            parent=current;
        }
    }

    /** Descripción: Ejecuta la operación «childrenUrl» correspondiente a la responsabilidad de OneDriveFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; parent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String childrenUrl(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String parent) {
        String root=rootBase(profile,credentials);
        return parent==null||parent.isBlank()?root+"/children":root+":/"+RemotePathPolicy.encodePath(parent)+":/children";
    }
    /** Descripción: Ejecuta la operación «itemUrl» correspondiente a la responsabilidad de OneDriveFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String itemUrl(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path){return rootBase(profile,credentials)+":/"+RemotePathPolicy.encodePath(path)+":";}
    /** Descripción: Ejecuta la operación «contentUrl» correspondiente a la responsabilidad de OneDriveFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String contentUrl(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path){return itemUrl(profile,credentials,path)+"/content";}
    /** Descripción: Ejecuta la operación «rootBase» correspondiente a la responsabilidad de OneDriveFile. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String rootBase(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){
        String drive=base(profile);
        if(profile.isOneDriveAppFolder()) return drive+"/special/approot";
        if(!profile.getRootItemId().isBlank()) return drive+"/items/"+RemotePathPolicy.encodeSegment(profile.getRootItemId());
        if(profile.getRepositoryUrl().isBlank()) return drive+"/root";
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(profile.getRepositoryUrl().getBytes(StandardCharsets.UTF_8));
        String graph=RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://graph.microsoft.com/v1.0");
        String url=graph+"/shares/u!"+encoded+"/driveItem?$select=id,name,webUrl,folder,parentReference";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(url),headers(credentials,"application/json",Map.of()),null);
        requireSuccess(response,"OneDrive URL resolution",profile.getRepositoryUrl());
        JsonNode item=RemoteProviderJsonSupport.parse(mapper,response,"OneDrive URL resolution");
        JsonNode parent=item.path("parentReference");
        String driveId=parent.path("driveId").asText(profile.getDriveId());
        String itemId=item.path("id").asText("");
        if(!item.has("folder")) itemId=parent.path("id").asText("");
        if(itemId.isBlank()) throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"OneDrive URL could not be resolved to a folder");
        String resolvedDrive=driveId.isBlank()?graph+"/me/drive":graph+"/drives/"+RemotePathPolicy.encodeSegment(driveId);
        return resolvedDrive+"/items/"+RemotePathPolicy.encodeSegment(itemId);
    }
    /** Descripción: Ejecuta la operación «base» correspondiente a la responsabilidad de OneDriveFile. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String base(RemoteStorageProperties.Profile profile){String graph=RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://graph.microsoft.com/v1.0");return profile.getDriveId().isBlank()?graph+"/me/drive":graph+"/drives/"+RemotePathPolicy.encodeSegment(profile.getDriveId());}
    /** Descripción: Ejecuta la operación «headers» correspondiente a la responsabilidad de OneDriveFile. Entradas: credentials: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; extra: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> headers(RemoteStorageCredentials credentials,String contentType,Map<String,String> extra){Map<String,String> result=new LinkedHashMap<>();result.put("Authorization","Bearer "+credentials.token());result.put("Accept","application/json");result.put("Content-Type",contentType);result.putAll(extra);return result;}
    /** Descripción: Ejecuta la operación «enforceMode» correspondiente a la responsabilidad de OneDriveFile. Entradas: mode: dato de entrada necesario para la operación; exists: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceMode(RemoteWriteMode mode,boolean exists,String path){if(mode==RemoteWriteMode.CREATE_ONLY&&exists)throw conflict(path);if(mode==RemoteWriteMode.UPDATE_ONLY&&!exists)throw notFound(path);}
    /** Descripción: Valida las condiciones necesarias para «requireSuccess» y rechaza entradas no válidas. Entradas: response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSuccess(RemoteStorageHttpClient.BoundedResponse response,String operation,String path){if(response.statusCode()>=200&&response.statusCode()<300)return;if(response.statusCode()==404)throw notFound(path);if(response.statusCode()==409)throw conflict(path);if(response.statusCode()==412)throw precondition(path);if(response.statusCode()==507)throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"OneDrive does not have enough remote storage space for: "+path);throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de OneDriveFile. Entradas: profileId: identificador que delimita el recurso objetivo; scope: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; version: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; webUrl: dato de entrada necesario para la operación; created: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteArtifactDescriptor descriptor(String profileId,RemoteArtifactScope scope,String path,String version,long size,String contentType,String webUrl,boolean created){return new RemoteArtifactDescriptor(profileId,type(),scope,path,version,size,contentType,webUrl,created);}
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de OneDriveFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException notFound(String p){return new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"OneDrive artifact was not found: "+p);}
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de OneDriveFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException conflict(String p){return new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"OneDrive artifact could not be written without conflict: "+p);}
    /** Descripción: Ejecuta la operación «precondition» correspondiente a la responsabilidad de OneDriveFile. Entradas: p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException precondition(String p){return new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"OneDrive artifact changed since it was loaded: "+p);}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de OneDriveFile. Entradas: max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException tooLarge(long max){return new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"OneDrive artifact exceeds the configured limit of "+max+" bytes");}
    private record OneDriveFile(String eTag,String webUrl,long size,boolean folder){}
}
