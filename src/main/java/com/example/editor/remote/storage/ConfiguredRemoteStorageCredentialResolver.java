package com.example.editor.remote.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.editor.remote.auth.RemoteAuthService;

/**
 * Resuelve primero una conexión OAuth cifrada e independiente de usuarios y
 * conserva el token server-side configurado únicamente como compatibilidad/CI.
 */
@Component
public class ConfiguredRemoteStorageCredentialResolver implements RemoteStorageCredentialResolver {
    private final RemoteAuthService auth;

    @Autowired
    /** Descripción: Inicializa una instancia de ConfiguredRemoteStorageCredentialResolver con sus dependencias y datos de entrada. Entradas: auth: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ConfiguredRemoteStorageCredentialResolver(RemoteAuthService auth){this.auth=auth;}

    /** Compatibility constructor for focused tests and non-Spring embedders. */
    /** Descripción: Inicializa una instancia de ConfiguredRemoteStorageCredentialResolver con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public ConfiguredRemoteStorageCredentialResolver(){this.auth=null;}

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de ConfiguredRemoteStorageCredentialResolver. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RemoteStorageCredentials resolve(String profileId,RemoteStorageProperties.Profile profile) {
        if(profile!=null&&auth!=null&&auth.connected(profileId,profile)) return new RemoteStorageCredentials(auth.credential(profileId,profile).accessToken());
        return new RemoteStorageCredentials(profile==null?"":profile.getToken());
    }
    /** Descripción: Ejecuta la operación «configured» correspondiente a la responsabilidad de ConfiguredRemoteStorageCredentialResolver. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean configured(String profileId,RemoteStorageProperties.Profile profile){return profile!=null&&((auth!=null&&auth.connected(profileId,profile))||!profile.getToken().isBlank());}
}
