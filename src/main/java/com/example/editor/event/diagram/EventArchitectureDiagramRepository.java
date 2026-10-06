package com.example.editor.event.diagram;

import java.util.Optional;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface EventArchitectureDiagramRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de EventArchitectureDiagramRepository. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventArchitectureDiagramDocument> create(EventArchitectureDiagramDocument document);
    /** Descripción: Actualiza el estado gestionado por EventArchitectureDiagramRepository mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EventArchitectureDiagramDocument> replace(String metamodelId, EventArchitectureDiagramDocument document, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventArchitectureDiagramRepository. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EventArchitectureDiagramDocument>> find(String metamodelId);
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String metamodelId, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String metamodelId);
}
