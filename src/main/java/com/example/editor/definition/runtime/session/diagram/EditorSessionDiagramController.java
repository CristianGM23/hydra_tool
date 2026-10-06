package com.example.editor.definition.runtime.session.diagram;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.definition.runtime.session.EditorModelSessionService;
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/editors/{editorId}/runtime/sessions/{sessionId}/diagram")
public class EditorSessionDiagramController {
    private final EditorModelSessionService sessions;
    private final EditorSessionDiagramService diagrams;
    /** Descripción: Inicializa una instancia de EditorSessionDiagramController con sus dependencias y datos de entrada. Entradas: sessions: dato de entrada necesario para la operación; diagrams: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionDiagramController(EditorModelSessionService sessions, EditorSessionDiagramService diagrams) {
        this.sessions = sessions; this.diagrams = diagrams;
    }
    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorSessionDiagramController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionDiagramLoadResult> find(@PathVariable String metamodelId, @PathVariable String editorId,
            @PathVariable String sessionId, @RequestParam(defaultValue = "") String representationId) {
        var view = sessions.find(metamodelId, editorId, sessionId, "");
        String representation = representationId.isBlank() ? view.session().activeRepresentationId() : representationId;
        var result = diagrams.findOrEmpty(sessionId, metamodelId, editorId, representation);
        return ResponseEntity.ok().eTag(strip(result.etag())).body(result);
    }
    @PutMapping
    /** Descripción: Actualiza el estado gestionado por EditorSessionDiagramController mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionDiagramLoadResult> save(@PathVariable String metamodelId, @PathVariable String editorId,
            @PathVariable String sessionId, @RequestBody EditorSessionDiagramDocument document,
            @RequestHeader(value="If-Match", required=false) String etag) {
        sessions.find(metamodelId, editorId, sessionId, "");
        if(!sessionId.equals(document.id()) || !metamodelId.equals(document.metamodelId()) || !editorId.equals(document.editorId()))
            throw new IllegalArgumentException("Diagram path and document identifiers must match");
        var result = diagrams.save(document, etag);
        return ResponseEntity.ok().eTag(strip(result.etag())).body(result);
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @DeleteMapping public ResponseEntity<Void> delete(@PathVariable String metamodelId, @PathVariable String editorId,
            @PathVariable String sessionId) { sessions.find(metamodelId, editorId, sessionId, ""); diagrams.delete(sessionId); return ResponseEntity.noContent().build(); }
    /** Descripción: Ejecuta la operación «strip» correspondiente a la responsabilidad de EditorSessionDiagramController. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String strip(String value) { return value == null ? "" : value.replace("\"", ""); }
}
