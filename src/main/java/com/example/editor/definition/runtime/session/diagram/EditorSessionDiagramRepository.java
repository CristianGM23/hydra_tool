package com.example.editor.definition.runtime.session.diagram;
import java.util.Optional;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface EditorSessionDiagramRepository {
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorSessionDiagramRepository. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EditorSessionDiagramDocument>> find(String sessionId);
    /** Descripción: Actualiza el estado gestionado por EditorSessionDiagramRepository mediante la operación «save». Entradas: document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EditorSessionDiagramDocument> save(EditorSessionDiagramDocument document, String etag);
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteIfExists(String sessionId);
}
