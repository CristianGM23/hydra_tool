package com.example.editor.integration.bridge;
/** Contribución opcional de interfaz, separada del runtime headless. */
public interface HostUiBridge extends HostIntegrationBridge {
    /** Descripción: Ejecuta la operación «notify» correspondiente a la responsabilidad de HostUiBridge. Entradas: notification: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void notify(HostNotification notification);
    /** Descripción: Crea o prepara el resultado de «openView» dentro de HostUiBridge. Entradas: viewId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void openView(String viewId);
}
