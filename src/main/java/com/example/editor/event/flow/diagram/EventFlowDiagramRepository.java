package com.example.editor.event.flow.diagram;

import java.util.Optional;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface EventFlowDiagramRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de EventFlowDiagramRepository. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventFlowDiagramDocument> create(EventFlowDiagramDocument document);
    /** Descripción: Actualiza el estado gestionado por EventFlowDiagramRepository mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventFlowDiagramDocument> replace(String metamodelId, EventFlowDiagramDocument document, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventFlowDiagramRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EventFlowDiagramDocument>> find(String metamodelId);
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
}
