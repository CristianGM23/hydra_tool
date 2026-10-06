package com.example.editor.definition.runtime.session.diagram;
import org.springframework.stereotype.Service;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
@Service
public class EditorSessionDiagramService {
    private final EditorSessionDiagramRepository repository;
    /** Descripción: Inicializa una instancia de EditorSessionDiagramService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionDiagramService(EditorSessionDiagramRepository repository) { this.repository = repository; }
    /** Descripción: Obtiene la información correspondiente a «findOrEmpty» dentro de EditorSessionDiagramService. Entradas: sessionId: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionDiagramLoadResult findOrEmpty(String sessionId, String metamodelId, String editorId, String representationId) {
        return repository.find(sessionId).map(value -> new EditorSessionDiagramLoadResult(value.document(), value.etag()))
                .orElseGet(() -> new EditorSessionDiagramLoadResult(new EditorSessionDiagramDocument("1.0", sessionId,
                        metamodelId, editorId, representationId, java.util.List.of(), java.util.List.of()), ""));
    }
    /** Descripción: Actualiza el estado gestionado por EditorSessionDiagramService mediante la operación «save». Entradas: document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorSessionDiagramLoadResult save(EditorSessionDiagramDocument document, String etag) {
        StoredCanonicalDocument<EditorSessionDiagramDocument> stored = repository.save(document, etag);
        return new EditorSessionDiagramLoadResult(stored.document(), stored.etag());
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String sessionId) { repository.deleteIfExists(sessionId); }
}
