package com.example.editor.remote.storage;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts one HTTPS repository/storage URL into safe provider coordinates.
 * It never accepts embedded credentials and never performs a network request.
 * Provider adapters may resolve cloud sharing URLs to authenticated item IDs
 * immediately before transfer.
 */
public final class RemoteStorageLocationResolver {
    /** Descripción: Inicializa una instancia de RemoteStorageLocationResolver con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private RemoteStorageLocationResolver() { }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de RemoteStorageLocationResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteStorageLocationResolution resolve(String value) {
        String input=value==null?"":value.trim();
        if(input.isBlank()) throw invalid("Remote repository or folder URL is required");
        URI uri;
        try { uri=URI.create(input); }
        catch(IllegalArgumentException exception) { throw invalid("Remote repository or folder URL is invalid"); }
        if(!"https".equalsIgnoreCase(uri.getScheme())) throw invalid("Remote repository or folder URL must use HTTPS");
        if(uri.getUserInfo()!=null) throw invalid("Remote repository or folder URL must not contain embedded credentials");
        if(uri.getHost()==null||uri.getHost().isBlank()) throw invalid("Remote repository or folder URL must contain a host");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        List<String> segments=segments(uri.getRawPath());

        if(host.equals("github.com")||host.equals("www.github.com")) return github(normalize(uri,false),segments);
        if(host.equals("gitlab.com")||host.equals("www.gitlab.com")) return gitlab(normalize(uri,false),segments);
        if(host.equals("drive.google.com")||host.equals("docs.google.com")) return google(normalize(uri,false),host,segments);
        if(host.equals("1drv.ms")||host.equals("onedrive.live.com")||host.endsWith(".sharepoint.com"))
            return new RemoteStorageLocationResolution(normalize(uri,true),RemoteStorageProviderType.ONEDRIVE,"","","","","","","OneDrive folder");
        if(host.equals("dropbox.com")||host.equals("www.dropbox.com")||host.equals("dl.dropboxusercontent.com"))
            return new RemoteStorageLocationResolution(normalize(uri,true),RemoteStorageProviderType.DROPBOX,"","","","","","","Dropbox folder");
        if(uri.getRawQuery()!=null||uri.getRawFragment()!=null) throw invalid("Generic Git repository URL must not contain query or fragment components");
        return new RemoteStorageLocationResolution(normalize(uri,false),RemoteStorageProviderType.GENERIC_GIT,"","","","main","","",host);
    }

    /** Descripción: Ejecuta la operación «github» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: url: dato de entrada necesario para la operación; segments: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static RemoteStorageLocationResolution github(String url,List<String> segments) {
        if(segments.size()<2) throw invalid("GitHub URL must identify a repository");
        String owner=segments.get(0); String repository=stripGit(segments.get(1));
        String branch="main"; String root="";
        if(segments.size()>=4&&(segments.get(2).equals("tree")||segments.get(2).equals("blob"))) {
            branch=segments.get(3);
            if(segments.size()>4) root=String.join("/",segments.subList(4,segments.size()));
        }
        return new RemoteStorageLocationResolution(url,RemoteStorageProviderType.GITHUB,owner,repository,"",branch,root,"",owner+"/"+repository);
    }

    /** Descripción: Ejecuta la operación «gitlab» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: url: dato de entrada necesario para la operación; segments: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static RemoteStorageLocationResolution gitlab(String url,List<String> segments) {
        if(segments.size()<2) throw invalid("GitLab URL must identify a project");
        int marker=segments.indexOf("-");
        int end=marker>=0?marker:segments.size();
        List<String> projectParts=new ArrayList<>(segments.subList(0,end));
        if(projectParts.isEmpty()) throw invalid("GitLab URL must identify a project");
        int last=projectParts.size()-1; projectParts.set(last,stripGit(projectParts.get(last)));
        String projectId=String.join("/",projectParts); String branch="main"; String root="";
        if(marker>=0&&segments.size()>marker+2&&(segments.get(marker+1).equals("tree")||segments.get(marker+1).equals("blob"))) {
            branch=segments.get(marker+2);
            if(segments.size()>marker+3) root=String.join("/",segments.subList(marker+3,segments.size()));
        }
        return new RemoteStorageLocationResolution(url,RemoteStorageProviderType.GITLAB,"","",projectId,branch,root,"",projectId);
    }

    /** Descripción: Ejecuta la operación «google» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: url: dato de entrada necesario para la operación; host: dato de entrada necesario para la operación; segments: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static RemoteStorageLocationResolution google(String url,String host,List<String> segments) {
        String id="";
        int folders=segments.indexOf("folders");
        if(folders>=0&&segments.size()>folders+1) id=segments.get(folders+1);
        if(id.isBlank()) {
            int d=segments.indexOf("d");
            if(d>=0&&segments.size()>d+1) id=segments.get(d+1);
        }
        if(id.isBlank()&&host.equals("drive.google.com")&&segments.size()>=2&&segments.get(0).equals("file")) {
            int d=segments.indexOf("d"); if(d>=0&&segments.size()>d+1) id=segments.get(d+1);
        }
        String summary=id.isBlank()?"Google Drive":"Google Drive · "+id;
        return new RemoteStorageLocationResolution(url,RemoteStorageProviderType.GOOGLE_DRIVE,"","","","","",id,summary);
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: uri: dato de entrada necesario para la operación; keepQuery: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(URI uri,boolean keepQuery) {
        try {
            URI clean=new URI("https",null,uri.getHost().toLowerCase(Locale.ROOT),uri.getPort(),uri.getPath(),keepQuery?uri.getQuery():null,null);
            return clean.toASCIIString();
        } catch(java.net.URISyntaxException exception) { throw invalid("Remote repository or folder URL is invalid"); }
    }
    /** Descripción: Ejecuta la operación «segments» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: rawPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> segments(String rawPath) {
        List<String> result=new ArrayList<>();
        if(rawPath==null) return result;
        for(String raw:rawPath.split("/")) if(!raw.isBlank()) {
            String decoded=URLDecoder.decode(raw,StandardCharsets.UTF_8);
            if(decoded.equals(".")||decoded.equals("..")||decoded.contains("/")||decoded.contains("\\")) throw invalid("Remote URL contains an unsafe path segment");
            result.add(decoded);
        }
        return List.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «stripGit» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String stripGit(String value){return value.endsWith(".git")?value.substring(0,value.length()-4):value;}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de RemoteStorageLocationResolver. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}
}
