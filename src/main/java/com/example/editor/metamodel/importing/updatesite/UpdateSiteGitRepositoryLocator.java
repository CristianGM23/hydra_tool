package com.example.editor.metamodel.importing.updatesite;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Normalizes clone URLs and common GitHub/GitLab browser tree URLs. */
@Component
public class UpdateSiteGitRepositoryLocator {
    /** Descripción: Ejecuta la operación «locate» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: rawUrl: dato de entrada necesario para la operación; explicitReference: dato de entrada necesario para la operación; explicitSubdirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public LocatedRepository locate(String rawUrl,String explicitReference,String explicitSubdirectory) {
        if(rawUrl==null||rawUrl.isBlank()) throw new IllegalArgumentException("Repository URL is required");
        URI uri;
        try { uri=URI.create(rawUrl.trim()); } catch(IllegalArgumentException exception) { throw new IllegalArgumentException("Repository URL is invalid",exception); }
        if(uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Repository URL must not contain credentials, query parameters or fragments");
        String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
        if(host.isBlank()) throw new IllegalArgumentException("Repository URL must contain a host");
        String path=uri.getPath()==null?"":uri.getPath();
        String inferredRef="";String inferredSub="";String clonePath=path;
        if(host.equals("github.com")) {
            List<String> parts=segments(path);
            if(parts.size()>=2) {
                clonePath="/"+parts.get(0)+"/"+stripGit(parts.get(1))+".git";
                if(parts.size()>=4 && "tree".equals(parts.get(2))) {
                    inferredRef=parts.get(3);
                    if(parts.size()>4) inferredSub=String.join("/",parts.subList(4,parts.size()));
                }
            }
        } else if(host.equals("gitlab.com") || host.endsWith(".gitlab.com")) {
            int marker=path.indexOf("/-/tree/");
            if(marker>=0) {
                String repositoryPath=path.substring(0,marker);
                List<String> tail=segments(path.substring(marker+"/-/tree/".length()));
                clonePath=stripTrailingSlash(repositoryPath)+".git";
                if(!tail.isEmpty()) inferredRef=tail.getFirst();
                if(tail.size()>1) inferredSub=String.join("/",tail.subList(1,tail.size()));
            }
        }
        String cloneUrl=new URIBuilder(uri.getScheme(),uri.getHost(),uri.getPort(),clonePath).toString();
        return new LocatedRepository(cloneUrl,blank(explicitReference)?inferredRef:explicitReference.trim(),
                blank(explicitSubdirectory)?inferredSub:explicitSubdirectory.replace('\\','/').trim(),provider(host));
    }

    /** Descripción: Ejecuta la operación «segments» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> segments(String path) {
        List<String> result=new ArrayList<>();
        for(String part:path.split("/")) if(!part.isBlank()) result.add(part);
        return result;
    }
    /** Descripción: Ejecuta la operación «stripGit» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripGit(String value){return value.endsWith(".git")?value.substring(0,value.length()-4):value;}
    /** Descripción: Ejecuta la operación «stripTrailingSlash» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripTrailingSlash(String value){String result=value;while(result.endsWith("/"))result=result.substring(0,result.length()-1);return result;}
    /** Descripción: Ejecuta la operación «blank» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean blank(String value){return value==null||value.isBlank();}
    /** Descripción: Ejecuta la operación «provider» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: host: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String provider(String host){if(host.equals("github.com")||host.endsWith(".github.com"))return "GITHUB";if(host.equals("gitlab.com")||host.endsWith(".gitlab.com"))return "GITLAB";return "GIT";}

    public record LocatedRepository(String cloneUrl,String reference,String subdirectory,String provider) {}

    private record URIBuilder(String scheme,String host,int port,String path) {
        /** Descripción: Ejecuta la operación «toString» correspondiente a la responsabilidad de UpdateSiteGitRepositoryLocator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String toString(){String authority=port<0?host:host+":"+port;return scheme+"://"+authority+path;}
    }
}
