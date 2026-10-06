package com.example.editor.integration.bridge;
import java.util.Map;
import com.example.editor.integration.HostElementReference;
/** Ejecuta acciones canónicas a través del mecanismo de comandos del host. */
public interface HostActionBridge extends HostIntegrationBridge {
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de HostActionBridge. Entradas: actionId: identificador que delimita el recurso objetivo; target: dato de entrada necesario para la operación; parameters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ HostActionResult execute(String actionId, HostElementReference target, Map<String, Object> parameters);
}
