package com.example.editor.remote.storage;

/**
 * Frontera para resolver credenciales. La implementación local usa configuración
 * server-side; una futura integración OAuth puede sustituirla sin modificar los
 * adaptadores GitHub/GitLab/OneDrive ni los contratos de dominio.
 */
public interface RemoteStorageCredentialResolver {
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de RemoteStorageCredentialResolver. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ RemoteStorageCredentials resolve(String profileId,RemoteStorageProperties.Profile profile);
    /** Descripción: Ejecuta la operación «configured» correspondiente a la responsabilidad de RemoteStorageCredentialResolver. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ default boolean configured(String profileId,RemoteStorageProperties.Profile profile){return resolve(profileId,profile).configured();}
}
