package com.example.editor.definition.runtime.session.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.definition.runtime.session.EditorEdgeCreationRequest;
import com.example.editor.definition.runtime.session.EditorElementAppearanceRequest;
import com.example.editor.definition.runtime.session.EditorElementUpdateRequest;
import com.example.editor.definition.runtime.session.EditorElementSelectionRequest;
import com.example.editor.definition.runtime.session.EditorElementContainmentRequest;
import com.example.editor.definition.runtime.session.EditorModelSessionService;
import com.example.editor.definition.runtime.session.EditorNodeCreationRequest;
import com.example.editor.definition.runtime.session.EditorRepresentationChangeRequest;
import com.example.editor.definition.runtime.session.EditorSessionCreateRequest;
import com.example.editor.definition.runtime.session.EditorSessionImportRequest;
import com.example.editor.definition.runtime.session.EditorSessionQuickFixResult;
import com.example.editor.definition.runtime.session.EditorSessionView;
import com.example.editor.validation.quickfix.QuickFixDescriptor;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;
import com.example.editor.validation.quickfix.QuickFixPreviewResult;
import com.example.editor.validation.runtime.ValidationRunMode;

import java.util.List;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionRequest;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionResponse;

/** API de sesiones neutrales ejecutadas por editores configurables. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/editors/{editorId}/runtime/sessions")
public class EditorModelSessionController {
    private final EditorModelSessionService service;
    /** Descripción: Inicializa una instancia de EditorModelSessionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorModelSessionController(EditorModelSessionService service) { this.service = service; }

    @PostMapping
    /** Descripción: Crea o prepara el resultado de «create» dentro de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> create(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @RequestBody EditorSessionCreateRequest request) { return response(service.create(metamodelId, editorId, request)); }

    @PostMapping("/import-emf")
    /** Descripción: Ejecuta la operación «importEmf» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> importEmf(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @RequestBody EditorSessionImportRequest request) { return response(service.importEmf(metamodelId, editorId, request)); }

    @GetMapping("/{sessionId}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> find(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @RequestParam(name = "selectedElementId", defaultValue = "") String selectedElementId) {
        return response(service.find(metamodelId, editorId, sessionId, selectedElementId));
    }

    @GetMapping("/{sessionId}/representations/{representationId}")
    /** Descripción: Ejecuta la operación «representationView» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> representationView(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("representationId") String representationId,
            @RequestParam(name = "selectedElementId", defaultValue = "") String selectedElementId) {
        return response(service.renderRepresentation(metamodelId, editorId, sessionId, representationId, selectedElementId));
    }

    @DeleteMapping("/{sessionId}/representations/{representationId}")
    /** Descripción: Elimina o libera el recurso gestionado por «closeRepresentation» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; representationId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> closeRepresentation(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("representationId") String representationId,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.closeRepresentation(metamodelId, editorId, sessionId, representationId, etag));
    }

    @PostMapping("/{sessionId}/validation")
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> validate(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @RequestParam(name = "mode", defaultValue = "FULL") ValidationRunMode mode,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.validate(metamodelId, editorId, sessionId, mode, etag));
    }

    @GetMapping("/{sessionId}/problems/{diagnosticId}/quick-fixes")
    /** Descripción: Ejecuta la operación «quickFixes» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<List<QuickFixDescriptor>> quickFixes(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("diagnosticId") String diagnosticId) {
        return ResponseEntity.ok(service.quickFixes(metamodelId, editorId, sessionId, diagnosticId));
    }

    @PostMapping("/{sessionId}/problems/{diagnosticId}/quick-fixes/{actionId}/preview")
    /** Descripción: Ejecuta la operación «previewQuickFix» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<QuickFixPreviewResult> previewQuickFix(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("diagnosticId") String diagnosticId, @PathVariable("actionId") String actionId,
            @RequestBody(required = false) QuickFixExecutionRequest request) {
        return ResponseEntity.ok(service.previewQuickFix(metamodelId, editorId, sessionId, diagnosticId, actionId, request));
    }

    @PostMapping("/{sessionId}/problems/{diagnosticId}/quick-fixes/{actionId}/apply")
    /** Descripción: Actualiza el estado gestionado por EditorModelSessionController mediante la operación «applyQuickFix». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionQuickFixResult> applyQuickFix(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("diagnosticId") String diagnosticId, @PathVariable("actionId") String actionId,
            @RequestBody(required = false) QuickFixExecutionRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        EditorSessionQuickFixResult result = service.applyQuickFix(metamodelId, editorId, sessionId, diagnosticId, actionId, request, etag);
        return ResponseEntity.ok().eTag(stripQuotes(result.view().etag())).body(result);
    }

    @PostMapping("/{sessionId}/representation")
    /** Descripción: Ejecuta la operación «representation» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> representation(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @RequestBody EditorRepresentationChangeRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.switchRepresentation(metamodelId, editorId, sessionId, request, etag));
    }

    @PostMapping("/{sessionId}/tools/{toolId}/nodes")
    /** Descripción: Crea o prepara el resultado de «createNode» dentro de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; toolId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> createNode(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @PathVariable("toolId") String toolId, @RequestBody EditorNodeCreationRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.createNode(metamodelId, editorId, sessionId, toolId, request, etag));
    }

    @PostMapping("/{sessionId}/tools/{toolId}/edges")
    /** Descripción: Crea o prepara el resultado de «createEdge» dentro de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; toolId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> createEdge(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @PathVariable("toolId") String toolId, @RequestBody EditorEdgeCreationRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.createEdge(metamodelId, editorId, sessionId, toolId, request, etag));
    }

    @PostMapping("/{sessionId}/tools/{toolId}/execute")
    /** Descripción: Procesa la operación «executeTool» coordinando las responsabilidades de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; toolId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionToolExecutionResponse> executeTool(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("toolId") String toolId, @RequestBody(required = false) EditorSessionToolExecutionRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        EditorSessionToolExecutionResponse result = service.executeTool(metamodelId, editorId, sessionId, toolId, request, etag);
        return ResponseEntity.ok().eTag(stripQuotes(result.view().etag())).body(result);
    }

    @PutMapping("/{sessionId}/elements/{elementId}")
    /** Descripción: Actualiza el estado gestionado por EditorModelSessionController mediante la operación «updateElement». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> updateElement(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @PathVariable("elementId") String elementId, @RequestBody EditorElementUpdateRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.updateElement(metamodelId, editorId, sessionId, elementId, request, etag));
    }

    @PutMapping("/{sessionId}/elements/{elementId}/appearance")
    /** Descripción: Actualiza la apariencia visual de un elemento dentro de la representación activa. Entradas: identificadores de metamodelo, editor, sesión y elemento, selección de apariencia y etag. Salida: devuelve la vista de sesión actualizada. */ public ResponseEntity<EditorSessionView> changeElementAppearance(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("elementId") String elementId, @RequestBody EditorElementAppearanceRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.changeElementAppearance(metamodelId, editorId, sessionId, elementId, request, etag));
    }

    @PutMapping("/{sessionId}/elements/{elementId}/containment")
    /** Descripción: Ejecuta la operación «reparentElement» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> reparentElement(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @PathVariable("elementId") String elementId, @RequestBody EditorElementContainmentRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.reparentElement(metamodelId, editorId, sessionId, elementId, request, etag));
    }

    @PostMapping("/{sessionId}/elements/delete")
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElements» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> deleteElements(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @RequestBody EditorElementSelectionRequest request,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.deleteElements(metamodelId, editorId, sessionId, request, etag));
    }

    @DeleteMapping("/{sessionId}/elements/{elementId}")
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElement» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> deleteElement(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @PathVariable("elementId") String elementId,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.deleteElement(metamodelId, editorId, sessionId, elementId, etag));
    }

    @DeleteMapping("/{sessionId}/relations/{relationInstanceId}")
    /** Descripción: Elimina o libera el recurso gestionado por «deleteRelation» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; relationInstanceId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> deleteRelation(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @PathVariable("relationInstanceId") String relationInstanceId,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.deleteRelation(metamodelId, editorId, sessionId, relationInstanceId, etag));
    }

    @PostMapping("/{sessionId}/save")
    /** Descripción: Actualiza el estado gestionado por EditorModelSessionController mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> save(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.save(metamodelId, editorId, sessionId, etag));
    }

    @PostMapping("/{sessionId}/discard")
    /** Descripción: Elimina o libera el recurso gestionado por «discard» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> discard(@PathVariable("metamodelId") String metamodelId,
            @PathVariable("editorId") String editorId, @PathVariable("sessionId") String sessionId) {
        return response(service.discardWorkingCopy(metamodelId, editorId, sessionId));
    }

    @PostMapping("/{sessionId}/undo")
    /** Descripción: Ejecuta la operación «undo» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> undo(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.undo(metamodelId, editorId, sessionId, etag));
    }

    @PostMapping("/{sessionId}/redo")
    /** Descripción: Ejecuta la operación «redo» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EditorSessionView> redo(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @RequestHeader(value = "If-Match", required = false) String etag) {
        return response(service.redo(metamodelId, editorId, sessionId, etag));
    }

    @DeleteMapping("/{sessionId}")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable("metamodelId") String metamodelId, @PathVariable("editorId") String editorId,
            @PathVariable("sessionId") String sessionId, @RequestHeader(value = "If-Match", required = false) String etag) {
        service.delete(metamodelId, editorId, sessionId, etag); return ResponseEntity.noContent().build();
    }

    /** Descripción: Ejecuta la operación «response» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ResponseEntity<EditorSessionView> response(EditorSessionView view) {
        return ResponseEntity.ok().eTag(stripQuotes(view.etag())).body(view);
    }
    /** Descripción: Ejecuta la operación «stripQuotes» correspondiente a la responsabilidad de EditorModelSessionController. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripQuotes(String value) { return value == null ? "" : value.replace("\"", ""); }
}
