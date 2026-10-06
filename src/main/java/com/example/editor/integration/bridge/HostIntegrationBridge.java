package com.example.editor.integration.bridge;
import java.util.Set;
import com.example.editor.integration.IntegrationCapability;
import com.example.editor.integration.IntegrationTechnology;
/** Punto de extensión común implementado por cada adaptador tecnológico. */
public interface HostIntegrationBridge {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de HostIntegrationBridge. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationTechnology technology();
    /** Descripción: Ejecuta la operación «capabilities» correspondiente a la responsabilidad de HostIntegrationBridge. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<IntegrationCapability> capabilities();
}
