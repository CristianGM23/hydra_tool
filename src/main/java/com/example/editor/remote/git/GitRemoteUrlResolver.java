package com.example.editor.remote.git;

import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;

/** Construye URLs Git sin credenciales y rechaza user-info, query y fragmentos. */
@Component
public class GitRemoteUrlResolver {
    private static final Pattern SAFE_PATH=Pattern.compile("[A-Za-z0-9._~!$&'()*+,;=:@%/-]+");
    private final NativeGitProperties properties;
    /** Descripción: Inicializa una instancia de GitRemoteUrlResolver con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GitRemoteUrlResolver(NativeGitProperties properties){this.properties=properties;}

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de GitRemoteUrlResolver. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public String resolve(RemoteStorageProperties.Profile profile){
        if(profile==null||profile.getProvider()==null)throw invalid("Remote Git profile is incomplete");
        return switch(profile.getProvider()){
            case GITHUB -> github(profile);
            case GITLAB -> gitlab(profile);
            case GENERIC_GIT -> generic(profile.getRepositoryUrl());
            default -> throw invalid("Selected profile is not a Git repository provider");
        };
    }

    /** Descripción: Ejecuta la operación «github» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String github(RemoteStorageProperties.Profile profile){
        if(profile.getOwner().isBlank()||profile.getRepository().isBlank())throw invalid("GitHub owner and repository are required");
        String base=githubBase(profile.getBaseUrl());
        return secureBase(base)+"/"+safePath(profile.getOwner())+"/"+safePath(profile.getRepository())+".git";
    }

    /** Descripción: Ejecuta la operación «gitlab» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String gitlab(RemoteStorageProperties.Profile profile){
        if(profile.getProjectId().isBlank())throw invalid("GitLab project id or path is required");
        String base=profile.getBaseUrl().isBlank()?"https://gitlab.com":stripApi(profile.getBaseUrl(),"/api/v4");
        return secureBase(base)+"/"+safePath(profile.getProjectId())+(profile.getProjectId().endsWith(".git")?"":".git");
    }

    /** Descripción: Ejecuta la operación «generic» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String generic(String value){
        if(value==null||value.isBlank())throw invalid("Generic Git repository URL is required");
        URI uri;
        try{uri=URI.create(value.trim());}catch(IllegalArgumentException exception){throw invalid("Generic Git repository URL is invalid");}
        if(!"https".equalsIgnoreCase(uri.getScheme()))throw invalid("Generic Git requires an HTTPS repository URL");
        if(uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw invalid("Generic Git repository URL must not contain credentials, query parameters or fragments");
        if(uri.getHost()==null||uri.getHost().isBlank())throw invalid("Generic Git repository URL must contain a host");
        requireAllowedHost(uri.getHost());
        return uri.toString();
    }


    /** Descripción: Ejecuta la operación «githubBase» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String githubBase(String value){
        if(value==null||value.isBlank())return "https://github.com";
        URI uri;try{uri=URI.create(value.trim());}catch(IllegalArgumentException exception){throw invalid("GitHub API base URL is invalid");}
        if("api.github.com".equalsIgnoreCase(uri.getHost()))return "https://github.com";
        return stripApi(value,"/api/v3");
    }
    /** Descripción: Ejecuta la operación «stripApi» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: value: valor de entrada que debe procesarse; suffix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String stripApi(String value,String suffix){String normalized=value.trim();while(normalized.endsWith("/"))normalized=normalized.substring(0,normalized.length()-1);if(normalized.endsWith(suffix))normalized=normalized.substring(0,normalized.length()-suffix.length());return normalized;}
    /** Descripción: Ejecuta la operación «secureBase» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String secureBase(String value){URI uri;try{uri=URI.create(value);}catch(IllegalArgumentException exception){throw invalid("Git provider base URL is invalid");}if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw invalid("Git provider base URL must use HTTPS without embedded credentials");requireAllowedHost(uri.getHost());String normalized=uri.toString();while(normalized.endsWith("/"))normalized=normalized.substring(0,normalized.length()-1);return normalized;}
    /** Descripción: Valida las condiciones necesarias para «requireAllowedHost» y rechaza entradas no válidas. Entradas: host: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireAllowedHost(String host){String normalized=host==null?"":host.toLowerCase(java.util.Locale.ROOT);boolean allowed=properties.getAllowedHosts().stream().map(item->item.toLowerCase(java.util.Locale.ROOT)).anyMatch(item->item.startsWith("*.")?normalized.endsWith(item.substring(1)):normalized.equals(item));if(!allowed)throw invalid("Git remote host is not in the server allowlist: "+host);}
    /** Descripción: Ejecuta la operación «safePath» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safePath(String value){String safe=value==null?"":value.trim().replace('\\','/');if(safe.isBlank()||safe.contains("..")||safe.startsWith("/")||!SAFE_PATH.matcher(safe).matches())throw invalid("Git repository coordinate contains unsupported characters");return safe;}
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de GitRemoteUrlResolver. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteStorageException invalid(String message){return new RemoteStorageException(RemoteStorageException.Reason.INVALID_REQUEST,message);}
}
