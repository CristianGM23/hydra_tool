package com.example.editor.remote.storage;

/** SPI tecnológica. El núcleo de proyecto/capas no conoce las APIs externas. */
public interface RemoteArtifactStorageProvider {
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de RemoteArtifactStorageProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ RemoteStorageProviderType type();
    /** Descripción: Obtiene la información correspondiente a «read» dentro de RemoteArtifactStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; normalizedPath: ruta o ubicación utilizada por la operación; scope: dato de entrada necesario para la operación; maximumBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String normalizedPath,RemoteArtifactScope scope,long maximumBytes);
    /** Descripción: Actualiza el estado gestionado por RemoteArtifactStorageProvider mediante la operación «write». Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación; command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command);

    /** Performs a non-destructive provider-level connectivity check for a persisted profile. */
    /** Descripción: Ejecuta la operación «testConnection» correspondiente a la responsabilidad de RemoteArtifactStorageProvider. Entradas: profileId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; credentials: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void testConnection(String profileId, RemoteStorageProperties.Profile profile, RemoteStorageCredentials credentials) {
        try {
            read(profileId, profile, credentials, ".hydra-connectivity-probe/non-existent", RemoteArtifactScope.LAYER, 1L);
        } catch (RemoteStorageException exception) {
            if (exception.getReason() == RemoteStorageException.Reason.NOT_FOUND) return;
            throw exception;
        }
    }
}
