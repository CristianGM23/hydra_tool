package com.example.editor.integration.bridge;
import com.example.editor.event.bus.CanonicalEventBus;
/** Conecta los eventos de una tecnología con el bus canónico. */
public interface HostEventBridge extends HostIntegrationBridge {
    /** Descripción: Ejecuta la operación «attach» correspondiente a la responsabilidad de HostEventBridge. Entradas: eventBus: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void attach(CanonicalEventBus eventBus);
    /** Descripción: Ejecuta la operación «detach» correspondiente a la responsabilidad de HostEventBridge. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void detach();
}
