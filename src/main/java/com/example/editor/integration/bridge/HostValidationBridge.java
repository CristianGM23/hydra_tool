package com.example.editor.integration.bridge;
import java.util.List;
/** Publica diagnósticos canónicos en la interfaz de validación del host. */
public interface HostValidationBridge extends HostIntegrationBridge {
    /** Descripción: Actualiza el estado gestionado por HostValidationBridge mediante la operación «replaceDiagnostics». Entradas: hostDocumentId: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void replaceDiagnostics(String hostDocumentId, List<HostDiagnosticMessage> diagnostics);
    /** Descripción: Elimina o libera el recurso gestionado por «clearDiagnostics» de forma controlada. Entradas: hostDocumentId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void clearDiagnostics(String hostDocumentId);
}
