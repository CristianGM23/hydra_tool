package com.example.editor.integration.web;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.integration.*;
/** API de perfiles de integración asociados a un metamodelo. */
@RestController
@RequestMapping({
    "/api/canonical-metamodels/{metamodelId}/integrations",
    "/api/semantic-contexts/{metamodelId}/integrations"
})
public class CanonicalIntegrationProfileController {
    private final IntegrationProfileApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalIntegrationProfileController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalIntegrationProfileController(IntegrationProfileApplicationService service) { this.service = service; }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de CanonicalIntegrationProfileController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public List<IntegrationProfileSummary> list(@PathVariable String metamodelId) { return service.list(metamodelId); }
    @GetMapping("/technologies") public List<IntegrationTechnologyDescriptor> technologies(@PathVariable String metamodelId) { return service.technologies(); }
    @PostMapping("/check") public IntegrationCheckReport checkDraft(@PathVariable String metamodelId, @RequestBody IntegrationProfileModel profile) { return service.checkDraft(metamodelId, profile); }
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalIntegrationProfileController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<IntegrationProfileSaveResponse> create(@PathVariable String metamodelId, @RequestBody IntegrationProfileModel profile) {
        IntegrationProfileSaveResponse result = service.create(metamodelId, profile);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/integrations/" + profile.id())).header(HttpHeaders.ETAG, result.etag()).body(result);
    }
    @GetMapping("/{profileId}") public ResponseEntity<IntegrationProfileModel> find(@PathVariable String metamodelId, @PathVariable String profileId) {
        IntegrationProfileLoadResult result = service.find(metamodelId, profileId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.profile());
    }
    @PutMapping("/{profileId}") public ResponseEntity<IntegrationProfileSaveResponse> replace(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody IntegrationProfileModel profile) {
        IntegrationProfileSaveResponse result = service.replace(metamodelId, profileId, profile, expectedEtag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result);
    }
    @PostMapping("/{profileId}/check") public IntegrationCheckReport check(@PathVariable String metamodelId, @PathVariable String profileId) { return service.check(metamodelId, profileId); }
    @DeleteMapping("/{profileId}") public ResponseEntity<Void> delete(@PathVariable String metamodelId, @PathVariable String profileId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) {
        service.delete(metamodelId, profileId, expectedEtag); return ResponseEntity.noContent().build();
    }
}
