package com.example.editor.transformation.sync;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API REST de sesiones persistentes de sincronización bidireccional. */
@RestController
@RequestMapping("/api/transformations/{transformationId}/synchronization-sessions")
public class TransformationSynchronizationSessionController {
    private final TransformationSynchronizationSessionService service;

    /** Descripción: Inicializa una instancia de TransformationSynchronizationSessionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSynchronizationSessionController(TransformationSynchronizationSessionService service) {
        this.service = service;
    }

    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «list» dentro de TransformationSynchronizationSessionController. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationSynchronizationSessionSummary> list(@PathVariable String transformationId) {
        return service.list(transformationId);
    }

    @PostMapping
    /** Descripción: Crea o prepara el resultado de «create» dentro de TransformationSynchronizationSessionController. Entradas: transformationId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSynchronizationSessionMutationResult> create(@PathVariable String transformationId,
            @RequestBody TransformationSynchronizationSessionCreateRequest request) {
        TransformationSynchronizationSessionMutationResult result = service.create(transformationId, request);
        return ResponseEntity.created(URI.create("/api/transformations/" + transformationId + "/synchronization-sessions/" + result.session().id()))
                .header(HttpHeaders.ETAG, result.etag()).body(result);
    }

    @GetMapping("/{sessionId}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TransformationSynchronizationSessionController. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSynchronizationSession> find(@PathVariable String transformationId,
            @PathVariable String sessionId) {
        TransformationSynchronizationSessionLoadResult result = service.find(transformationId, sessionId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.session());
    }

    @PostMapping("/{sessionId}/synchronize")
    /** Descripción: Actualiza el estado gestionado por TransformationSynchronizationSessionController mediante la operación «synchronize». Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSynchronizationSessionMutationResult> synchronize(@PathVariable String transformationId,
            @PathVariable String sessionId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @RequestBody TransformationSynchronizationSessionUpdateRequest request) {
        TransformationSynchronizationSessionMutationResult result = service.synchronize(transformationId, sessionId, request, etag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result);
    }

    @PostMapping("/{sessionId}/rebase")
    /** Descripción: Ejecuta la operación «rebase» correspondiente a la responsabilidad de TransformationSynchronizationSessionController. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSynchronizationSessionMutationResult> rebase(@PathVariable String transformationId,
            @PathVariable String sessionId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @RequestBody TransformationSynchronizationSessionCreateRequest request) {
        TransformationSynchronizationSessionMutationResult result = service.rebase(transformationId, sessionId, request, etag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result);
    }

    @GetMapping("/{sessionId}/history")
    /** Descripción: Ejecuta la operación «history» correspondiente a la responsabilidad de TransformationSynchronizationSessionController. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationSynchronizationCheckpoint> history(@PathVariable String transformationId,
            @PathVariable String sessionId) {
        return service.history(transformationId, sessionId);
    }

    @PostMapping("/{sessionId}/restore/{revision}")
    /** Descripción: Ejecuta la operación «restore» correspondiente a la responsabilidad de TransformationSynchronizationSessionController. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; revision: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSynchronizationSessionMutationResult> restore(@PathVariable String transformationId,
            @PathVariable String sessionId, @PathVariable long revision,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        TransformationSynchronizationSessionMutationResult result = service.restore(transformationId, sessionId, revision, etag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result);
    }

    @DeleteMapping("/{sessionId}")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: transformationId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String transformationId, @PathVariable String sessionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        service.delete(transformationId, sessionId, etag);
        return ResponseEntity.noContent().build();
    }
}
