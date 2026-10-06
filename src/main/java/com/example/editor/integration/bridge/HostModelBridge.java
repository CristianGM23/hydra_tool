package com.example.editor.integration.bridge;
import java.util.Optional;
import com.example.editor.integration.HostElementReference;
import com.example.editor.validation.model.CanonicalModelDocument;
/** Traduce modelos entre el host y el documento canónico. */
public interface HostModelBridge extends HostIntegrationBridge {
    /** Descripción: Obtiene la información correspondiente a «loadModel» dentro de HostModelBridge. Entradas: hostDocumentId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ CanonicalModelDocument loadModel(String hostDocumentId);
    /** Descripción: Actualiza el estado gestionado por HostModelBridge mediante la operación «saveModel». Entradas: document: dato de entrada necesario para la operación; expectedRevision: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ HostWriteResult saveModel(CanonicalModelDocument document, String expectedRevision);
    /** Descripción: Ejecuta la operación «locate» correspondiente a la responsabilidad de HostModelBridge. Entradas: canonicalDocumentId: identificador que delimita el recurso objetivo; canonicalElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<HostElementReference> locate(String canonicalDocumentId, String canonicalElementId);
}
