package com.example.editor.remote.storage.provider;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Adaptador Google Drive API v3, sin SDK externo y con credenciales solo server-side. */
@Component
public class GoogleDriveRemoteStorageProvider implements RemoteArtifactStorageProvider {
    private static final String FOLDER_MIME="application/vnd.google-apps.folder";
    private final RemoteStorageHttpClient http;
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de GoogleDriveRemoteStorageProvider con sus dependencias y datos de entrada. Entradas: http: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GoogleDriveRemoteStorageProvider(RemoteStorageHttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GOOGLE_DRIVE;}

    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials) {
        String url=apiBase(profile)+"/files/"+enc(rootId(profile,credentials))+"?fields=id,name,mimeType&supportsAllDrives=true";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(url),headers(credentials,"application/json"),null);
        if(response.statusCode()<200||response.statusCode()>=300) throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Google Drive connection test failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de GoogleDriveRemoteStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes) {
        DriveItem item=resolveItem(profile,credentials,path,false);
        if(item==null||item.folder()) throw notFound(path);
        if(item.size()>maximumBytes) throw tooLarge(maximumBytes);
        RemoteStorageHttpClient.BoundedResponse response=http.sendBounded("GET",RemoteProviderJsonSupport.uri(apiBase(profile)+"/files/"+enc(item.id())+"?alt=media&supportsAllDrives=true"),headers(credentials,"application/octet-stream"),null,maximumBytes);
        requireSuccess(response,"Google Drive file download",path);
        String contentType=response.headers().firstValue("content-type").orElse("application/octet-stream");
        byte[] content=response.body();
        return new RemoteArtifact(descriptor(profileId,scope,path,item.version(),content.length,contentType,item.webUrl(),false),content);
    }

    /** Descripción: Actualiza el estado gestionado por GoogleDriveRemoteStorageProvider mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command) {
        ParentAndName target=parent(profile,credentials,command.path(),true);
        DriveItem existing=findChild(profile,credentials,target.parentId(),target.name());
        enforceMode(command.mode(),existing!=null,command.path());
        if(existing!=null&&existing.folder()) throw conflict(command.path());
        if(!command.expectedVersion().isBlank()&&(existing==null||!command.expectedVersion().equals(existing.version()))) throw precondition(command.path());
        DriveItem written=existing==null?createFile(profile,credentials,target,command):updateFile(profile,credentials,existing,command);
        return descriptor(profileId,scope,command.path(),written.version(),command.contentLength(),command.contentType(),written.webUrl(),existing==null);
    }

    /** Descripción: Crea o prepara el resultado de «createFile» dentro de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DriveItem createFile(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,ParentAndName target,RemoteArtifactWriteCommand command) {
        String boundary="hydra-"+Long.toUnsignedString(System.nanoTime(),36);
        byte[] prefix=("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+
                jsonText(Map.of("name",target.name(),"parents",List.of(target.parentId())))+
                "\r\n--"+boundary+"\r\nContent-Type: "+safeContentType(command.contentType())+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher body=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix),HttpRequest.BodyPublishers.ofByteArray(command.content()),HttpRequest.BodyPublishers.ofByteArray(suffix));
        String url=uploadBase(profile)+"/files?uploadType=multipart&supportsAllDrives=true&fields=id,name,mimeType,size,version,webViewLink";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmallPublisher("POST",RemoteProviderJsonSupport.uri(url),headers(credentials,"multipart/related; boundary="+boundary),body);
        requireSuccess(response,"Google Drive file creation",command.path());
        return item(RemoteProviderJsonSupport.parse(mapper,response,"Google Drive file creation"));
    }

    /** Descripción: Actualiza el estado gestionado por GoogleDriveRemoteStorageProvider mediante la operación «updateFile». Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DriveItem updateFile(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,DriveItem existing,RemoteArtifactWriteCommand command) {
        String url=uploadBase(profile)+"/files/"+enc(existing.id())+"?uploadType=media&supportsAllDrives=true&fields=id,name,mimeType,size,version,webViewLink";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("PATCH",RemoteProviderJsonSupport.uri(url),headers(credentials,safeContentType(command.contentType())),command.content());
        requireSuccess(response,"Google Drive file update",command.path());
        return item(RemoteProviderJsonSupport.parse(mapper,response,"Google Drive file update"));
    }

    /** Descripción: Obtiene la información correspondiente a «resolveItem» dentro de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; createParents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DriveItem resolveItem(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,boolean createParents) {
        ParentAndName target=parent(profile,credentials,path,createParents);
        return findChild(profile,credentials,target.parentId(),target.name());
    }

    /** Descripción: Ejecuta la operación «parent» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; createParents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ParentAndName parent(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,boolean createParents) {
        String[] segments=path.split("/");
        String parent=rootId(profile,credentials);
        for(int index=0;index<segments.length-1;index++) {
            String name=segments[index];
            if(name.isBlank()) continue;
            DriveItem folder=findChild(profile,credentials,parent,name);
            if(folder==null) {
                if(!createParents) return new ParentAndName("__missing__",segments[segments.length-1]);
                folder=createFolder(profile,credentials,parent,name);
            }
            if(!folder.folder()) throw conflict("Remote parent is not a Google Drive folder: "+name);
            parent=folder.id();
        }
        return new ParentAndName(parent,segments[segments.length-1]);
    }

    /** Descripción: Crea o prepara el resultado de «createFolder» dentro de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; parent: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DriveItem createFolder(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String parent,String name) {
        Map<String,Object> body=new LinkedHashMap<>();body.put("name",name);body.put("mimeType",FOLDER_MIME);body.put("parents",List.of(parent));
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("POST",RemoteProviderJsonSupport.uri(apiBase(profile)+"/files?supportsAllDrives=true&fields=id,name,mimeType,size,version,webViewLink"),headers(credentials,"application/json"),RemoteProviderJsonSupport.json(mapper,body));
        requireSuccess(response,"Google Drive folder creation",name);
        return item(RemoteProviderJsonSupport.parse(mapper,response,"Google Drive folder creation"));
    }

    /** Descripción: Obtiene la información correspondiente a «findChild» dentro de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; parent: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DriveItem findChild(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String parent,String name) {
        if("__missing__".equals(parent)) return null;
        String query="name = '"+escapeQuery(name)+"' and '"+escapeQuery(parent)+"' in parents and trashed = false";
        StringBuilder url=new StringBuilder(apiBase(profile)).append("/files?q=").append(encQuery(query)).append("&pageSize=2&fields=files(id,name,mimeType,size,version,webViewLink,parents)&supportsAllDrives=true&includeItemsFromAllDrives=true");
        if(!profile.getDriveId().isBlank()) url.append("&corpora=drive&driveId=").append(enc(profile.getDriveId()));
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(url.toString()),headers(credentials,"application/json"),null);
        requireSuccess(response,"Google Drive metadata lookup",name);
        JsonNode files=RemoteProviderJsonSupport.parse(mapper,response,"Google Drive metadata lookup").path("files");
        if(!files.isArray()||files.isEmpty()) return null;
        if(files.size()>1) throw new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"Google Drive path is ambiguous because more than one item has the same name: "+name);
        return item(files.get(0));
    }

    /** Descripción: Ejecuta la operación «item» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: json: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DriveItem item(JsonNode json){JsonNode parents=json.path("parents");String parent=parents.isArray()&&!parents.isEmpty()?parents.get(0).asText(""):"";return new DriveItem(json.path("id").asText(""),json.path("name").asText(""),FOLDER_MIME.equals(json.path("mimeType").asText("")),json.path("size").asLong(0L),json.path("version").asText(""),json.path("webViewLink").asText(""),parent);}
    /** Descripción: Ejecuta la operación «headers» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: credentials: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> headers(RemoteStorageCredentials credentials,String contentType){Map<String,String> result=new LinkedHashMap<>();result.put("Authorization","Bearer "+credentials.token());result.put("Accept","application/json");result.put("Content-Type",contentType);return result;}
    /** Descripción: Ejecuta la operación «apiBase» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String apiBase(RemoteStorageProperties.Profile profile){return RemoteProviderJsonSupport.base(profile.getBaseUrl(),"https://www.googleapis.com/drive/v3");}
    /** Descripción: Ejecuta la operación «uploadBase» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String uploadBase(RemoteStorageProperties.Profile profile){if(profile.getBaseUrl().isBlank())return "https://www.googleapis.com/upload/drive/v3";String base=apiBase(profile);return base.contains("/drive/v3")?base.replace("/drive/v3","/upload/drive/v3"):base+"/upload";}
    /** Descripción: Ejecuta la operación «rootId» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String rootId(RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){
        if(profile.getRootItemId().isBlank()) return profile.getDriveId().isBlank()?"root":profile.getDriveId();
        String url=apiBase(profile)+"/files/"+enc(profile.getRootItemId())+"?fields=id,name,mimeType,parents&supportsAllDrives=true";
        RemoteStorageHttpClient.BoundedResponse response=http.sendSmall("GET",RemoteProviderJsonSupport.uri(url),headers(credentials,"application/json"),null);
        requireSuccess(response,"Google Drive URL resolution",profile.getRepositoryUrl());
        DriveItem selected=item(RemoteProviderJsonSupport.parse(mapper,response,"Google Drive URL resolution"));
        if(selected.folder()) return selected.id();
        if(!selected.parentId().isBlank()) return selected.parentId();
        throw new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,"Google Drive URL must identify a folder or a file with a parent folder");
    }
    /** Descripción: Ejecuta la operación «jsonText» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonText(Object value){return new String(RemoteProviderJsonSupport.json(mapper,value),StandardCharsets.UTF_8);}
    /** Descripción: Ejecuta la operación «safeContentType» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeContentType(String value){return value==null||value.isBlank()?"application/octet-stream":value;}
    /** Descripción: Ejecuta la operación «escapeQuery» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escapeQuery(String value){return value.replace("\\","\\\\").replace("'","\\'");}
    /** Descripción: Ejecuta la operación «enc» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    /** Descripción: Ejecuta la operación «encQuery» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String encQuery(String value){return enc(value);}
    /** Descripción: Ejecuta la operación «enforceMode» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: mode: dato de entrada necesario para la operación; exists: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void enforceMode(RemoteWriteMode mode,boolean exists,String path){if(mode==RemoteWriteMode.CREATE_ONLY&&exists)throw conflict(path);if(mode==RemoteWriteMode.UPDATE_ONLY&&!exists)throw notFound(path);}
    /** Descripción: Valida las condiciones necesarias para «requireSuccess» y rechaza entradas no válidas. Entradas: response: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireSuccess(RemoteStorageHttpClient.BoundedResponse response,String operation,String path){if(response.statusCode()>=200&&response.statusCode()<300)return;if(response.statusCode()==404)throw notFound(path);if(response.statusCode()==409)throw conflict(path);if(response.statusCode()==412)throw precondition(path);throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,operation+" failed with HTTP "+response.statusCode()+": "+RemoteProviderJsonSupport.text(response));}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; scope: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; version: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; contentType: dato de entrada necesario para la operación; webUrl: dato de entrada necesario para la operación; created: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteArtifactDescriptor descriptor(String profileId,RemoteArtifactScope scope,String path,String version,long size,String contentType,String webUrl,boolean created){return new RemoteArtifactDescriptor(profileId,type(),scope,path,version,size,contentType,webUrl,created);}
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException notFound(String path){return new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"Google Drive artifact was not found: "+path);}
    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException conflict(String path){return new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"Google Drive artifact could not be written without conflict: "+path);}
    /** Descripción: Ejecuta la operación «precondition» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException precondition(String path){return new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"Google Drive artifact changed since it was loaded: "+path);}
    /** Descripción: Ejecuta la operación «tooLarge» correspondiente a la responsabilidad de GoogleDriveRemoteStorageProvider. Entradas: max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException tooLarge(long max){return new RemoteStorageException(RemoteStorageException.Reason.FILE_TOO_LARGE,"Google Drive artifact exceeds the configured limit of "+max+" bytes");}
    private record DriveItem(String id,String name,boolean folder,long size,String version,String webUrl,String parentId){}
    private record ParentAndName(String parentId,String name){}
}
