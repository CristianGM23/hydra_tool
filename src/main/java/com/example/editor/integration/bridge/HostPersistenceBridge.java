package com.example.editor.integration.bridge;
/** Acceso controlado a recursos del proyecto anfitrión mediante rutas relativas. */
public interface HostPersistenceBridge extends HostIntegrationBridge {
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(String relativePath);
    /** Descripción: Obtiene la información correspondiente a «read» dentro de HostPersistenceBridge. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ byte[] read(String relativePath);
    /** Descripción: Actualiza el estado gestionado por HostPersistenceBridge mediante la operación «write». Entradas: relativePath: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void write(String relativePath, byte[] content);
}
