package com.example.editor.gamification.web;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationApplicationException;
import com.example.editor.gamification.GamificationCheckReport;
import com.example.editor.gamification.GamificationLoadResult;
import com.example.editor.gamification.GamificationSaveResponse;
import com.example.editor.gamification.GamificationSummary;

@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalGamificationController {
    private final GamificationApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalGamificationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalGamificationController(GamificationApplicationService service) { this.service = service; }
    @GetMapping("/gamification") public List<GamificationSummary> list() { return service.list(); }
    @PostMapping("/{metamodelId}/gamification/check") public GamificationCheckReport checkDraft(@PathVariable String metamodelId, @RequestBody GamificationModelRequest request) { return service.checkDraft(metamodelId, request.definitions(), request.bindings()); }
    @PostMapping("/{metamodelId}/gamification") public ResponseEntity<GamificationSaveResponse> create(@PathVariable String metamodelId, @RequestBody GamificationModelRequest request) {
        GamificationSaveResponse result = service.create(metamodelId, request.definitions(), request.bindings());
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/gamification"))
                .header("X-Gamification-Definitions-ETag", result.definitionEtag()).header("X-Gamification-Bindings-ETag", result.bindingEtag()).body(result);
    }
    @GetMapping("/{metamodelId}/gamification") public ResponseEntity<GamificationLoadResult> find(@PathVariable String metamodelId) { try{return ResponseEntity.ok(service.find(metamodelId));}catch(GamificationApplicationException exception){if(exception.getReason()==GamificationApplicationException.Reason.NOT_FOUND)return ResponseEntity.notFound().build();throw exception;} }
    @PutMapping("/{metamodelId}/gamification") public ResponseEntity<GamificationSaveResponse> replace(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match-Definitions", required = false) String definitionEtag,
            @RequestHeader(value = "If-Match-Bindings", required = false) String bindingEtag,
            @RequestBody GamificationModelRequest request) {
        GamificationSaveResponse result = service.replace(metamodelId, request.definitions(), definitionEtag, request.bindings(), bindingEtag);
        return ResponseEntity.ok().header("X-Gamification-Definitions-ETag", result.definitionEtag()).header("X-Gamification-Bindings-ETag", result.bindingEtag()).body(result);
    }
    @PostMapping("/{metamodelId}/gamification/check-saved") public GamificationCheckReport check(@PathVariable String metamodelId) { return service.check(metamodelId); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitionEtag: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @DeleteMapping("/{metamodelId}/gamification") public ResponseEntity<Void> delete(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match-Definitions", required = false) String definitionEtag,
            @RequestHeader(value = "If-Match-Bindings", required = false) String bindingEtag) {
        service.delete(metamodelId, definitionEtag, bindingEtag); return ResponseEntity.noContent().build();
    }
}
