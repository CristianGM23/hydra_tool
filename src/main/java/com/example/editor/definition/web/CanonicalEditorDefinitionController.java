package com.example.editor.definition.web;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.definition.*;
/** API de editores configurables asociados a un metamodelo. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/editors")
public class CanonicalEditorDefinitionController {
    private final EditorDefinitionApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalEditorDefinitionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalEditorDefinitionController(EditorDefinitionApplicationService service) { this.service = service; }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de CanonicalEditorDefinitionController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public List<EditorDefinitionSummary> list(@PathVariable String metamodelId) { return service.list(metamodelId); }
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalEditorDefinitionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<EditorDefinitionSaveResponse> create(@PathVariable String metamodelId, @RequestBody EditorDefinitionModel definition) {
        EditorDefinitionSaveResponse result = service.create(metamodelId, definition);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/editors/" + definition.id())).header(HttpHeaders.ETAG, result.etag()).body(result);
    }
    @GetMapping("/{editorId}") public ResponseEntity<EditorDefinitionModel> find(@PathVariable String metamodelId, @PathVariable String editorId) {
        EditorDefinitionLoadResult result = service.find(metamodelId, editorId); return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.editorDefinition());
    }
    @PutMapping("/{editorId}") public ResponseEntity<EditorDefinitionSaveResponse> replace(@PathVariable String metamodelId, @PathVariable String editorId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody EditorDefinitionModel definition) {
        EditorDefinitionSaveResponse result = service.replace(metamodelId, editorId, definition, expectedEtag); return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result);
    }
    @PostMapping("/{editorId}/check") public EditorDefinitionCheckReport check(@PathVariable String metamodelId, @PathVariable String editorId) { return service.check(metamodelId, editorId); }
    @DeleteMapping("/{editorId}") public ResponseEntity<Void> delete(@PathVariable String metamodelId, @PathVariable String editorId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) { service.delete(metamodelId, editorId, expectedEtag); return ResponseEntity.noContent().build(); }
}
