package com.example.editor.remote.auth;

import java.util.List;
import java.util.Optional;

/** SPI de almacenamiento de credenciales; el resto de HYDRA solo conoce handles opacos. */
public interface RemoteCredentialVault {
    /** Descripción: Obtiene la información correspondiente a «load» dentro de RemoteCredentialVault. Entradas: connectionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<RemoteCredentialMaterial> load(String connectionId);
    /** Descripción: Ejecuta la operación «store» correspondiente a la responsabilidad de RemoteCredentialVault. Entradas: connectionId: identificador que delimita el recurso objetivo; material: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void store(String connectionId,RemoteCredentialMaterial material);
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: connectionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String connectionId);
    /** Descripción: Ejecuta la operación «connectionIds» correspondiente a la responsabilidad de RemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> connectionIds();
    /** Descripción: Ejecuta la operación «persistent» correspondiente a la responsabilidad de RemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean persistent();
    /** Descripción: Ejecuta la operación «protectionMode» correspondiente a la responsabilidad de RemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String protectionMode();
    /** Descripción: Obtiene la información correspondiente a «ready» dentro de RemoteCredentialVault. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ default boolean ready(){return true;}
}
