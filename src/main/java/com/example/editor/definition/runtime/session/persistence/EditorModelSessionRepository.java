package com.example.editor.definition.runtime.session.persistence;
import java.util.List;
import java.util.Optional;
import com.example.editor.definition.runtime.session.EditorModelSession;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
public interface EditorModelSessionRepository {
    /** Descripción: Crea o prepara el resultado de «create» dentro de EditorModelSessionRepository. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EditorModelSession> create(EditorModelSession session);
    /** Descripción: Actualiza el estado gestionado por EditorModelSessionRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<EditorModelSession> replace(String id, EditorModelSession session, String etag);
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorModelSessionRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<EditorModelSession>> find(String id);
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de EditorModelSessionRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String etag);
}
