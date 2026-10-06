package com.example.editor.transformation.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
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

import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationLoadResult;
import com.example.editor.transformation.core.TransformationSaveResponse;
import com.example.editor.transformation.core.TransformationSummary;
import com.example.editor.transformation.runtime.TransformationExecutionResult;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.export.TransformationExportRequest;
import com.example.editor.transformation.export.TransformationExportResult;
import com.example.editor.transformation.export.TransformationExportService;
import com.example.editor.transformation.export.TransformationLanguageAdapterRegistry;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.runtime.TransformationSynchronizationRequest;
import com.example.editor.transformation.runtime.TransformationSynchronizationResult;
import com.example.editor.transformation.text.TransformationGenerationResult;
import com.example.editor.transformation.text.TransformationTextGenerationService;
import com.example.editor.transformation.textmodel.TransformationTextToModelResult;
import com.example.editor.transformation.textmodel.TransformationTextToModelService;
import com.example.editor.validation.model.CanonicalModelDocument;

@RestController
@RequestMapping("/api/transformations")
public class TransformationController {
    private final TransformationApplicationService service;
    private final TransformationRuntimeService runtime;
    private final TransformationTextGenerationService textGeneration;
    private final TransformationTextToModelService textToModel;
    private final TransformationExportService exports;
    private final TransformationLanguageAdapterRegistry exportAdapters;

    /** Descripción: Inicializa una instancia de TransformationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; runtime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationController(TransformationApplicationService service, TransformationRuntimeService runtime,
            TransformationTextGenerationService textGeneration, TransformationTextToModelService textToModel,
            TransformationExportService exports, TransformationLanguageAdapterRegistry exportAdapters) {
        this.service = service;
        this.runtime = runtime;
        this.textGeneration = textGeneration;
        this.textToModel = textToModel;
        this.exports = exports;
        this.exportAdapters = exportAdapters;
    }

    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «list» dentro de TransformationController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationSummary> list() {
        return service.list();
    }

    @PostMapping
    /** Descripción: Crea o prepara el resultado de «create» dentro de TransformationController. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSaveResponse> create(@RequestBody TransformationDefinitionDocument document) {
        TransformationSaveResponse response = service.create(document);
        return ResponseEntity.created(URI.create("/api/transformations/" + response.transformation().id()))
                .header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @PostMapping("/check")
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationCheckReport check(@RequestBody TransformationDefinitionDocument document) {
        return service.check(document);
    }

    @GetMapping("/{id}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TransformationController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el documento normalizado junto con su ETag y los diagnósticos que pueden advertir cambios del metamodelo desde la última persistencia. */ public ResponseEntity<TransformationLoadResult> find(@PathVariable String id) {
        TransformationLoadResult result = service.find(id);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result);
    }

    @GetMapping("/{id}/check")
    /** Descripción: Valida las condiciones necesarias para «checkStored» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationCheckReport checkStored(@PathVariable String id) {
        return service.find(id).checkReport();
    }

    @PostMapping("/preview")
    /** Ejecuta un preview M2M del draft actual sin persistir la definición. */
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de TransformationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult preview(@RequestBody TransformationDraftExecutionRequest request) {
        return runtime.executeDraft(request.transformation(), request.sourceModel(), request.direction());
    }

    @PostMapping("/preview-model-to-text")
    /** Ejecuta M2T sobre un draft y devuelve artefactos en memoria. */
    /** Descripción: Ejecuta la operación «previewModelToText» correspondiente a la responsabilidad de TransformationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationGenerationResult previewModelToText(@RequestBody TransformationDraftExecutionRequest request) {
        return textGeneration.preview(request.transformation(), request.sourceModel());
    }

    @PostMapping("/{id}/model-to-text")
    /** Ejecuta una definición M2T persistida y devuelve artefactos sin escribir en disco. */
    /** Descripción: Ejecuta la operación «modelToText» correspondiente a la responsabilidad de TransformationController. Entradas: id: identificador que delimita el recurso objetivo; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationGenerationResult modelToText(@PathVariable String id, @RequestBody CanonicalModelDocument sourceModel) {
        return textGeneration.execute(id, sourceModel);
    }

    @PostMapping("/preview-text-to-model")
    /** Ejecuta T2M sobre el draft actual sin crear ni reemplazar documentos persistidos. */
    /** Descripción: Ejecuta la operación «previewTextToModel» correspondiente a la responsabilidad de TransformationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTextToModelResult previewTextToModel(@RequestBody TransformationDraftTextRequest request) {
        return textToModel.preview(request.transformation(), request.text());
    }

    @PostMapping("/{id}/text-to-model")
    /** Ejecuta una definición T2M persistida usando el snapshot activo de su gramática. */
    /** Descripción: Ejecuta la operación «textToModel» correspondiente a la responsabilidad de TransformationController. Entradas: id: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTextToModelResult textToModel(@PathVariable String id, @RequestBody TransformationTextRequest request) {
        return textToModel.execute(id, request.text());
    }

    @GetMapping("/export-languages")
    /** Enumera adaptadores de exportación disponibles sin inferir compatibilidad de una definición concreta. */
    /** Descripción: Ejecuta la operación «exportLanguages» correspondiente a la responsabilidad de TransformationController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationExportLanguage> exportLanguages() {
        return exportAdapters.languages();
    }

    @PostMapping("/export")
    /** Exporta el draft canónico actual sin persistirlo y devuelve diagnósticos de fidelidad junto con artefactos en memoria. */
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de TransformationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExportResult export(@RequestBody TransformationExportRequest request) {
        return exports.export(request);
    }

    @PostMapping("/{id}/execute")
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationController. Entradas: id: identificador que delimita el recurso objetivo; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult execute(@PathVariable String id, @RequestBody CanonicalModelDocument sourceModel) {
        return runtime.execute(id, sourceModel);
    }

    @PostMapping("/{id}/execute/{direction}")
    /** Ejecuta explícitamente una dirección M2M manteniendo el endpoint forward anterior intacto. */
    /** Descripción: Procesa la operación «executeDirectional» coordinando las responsabilidades de TransformationController. Entradas: id: identificador que delimita el recurso objetivo; direction: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult executeDirectional(@PathVariable String id, @PathVariable TransformationExecutionDirection direction,
            @RequestBody CanonicalModelDocument sourceModel) {
        return runtime.execute(id, sourceModel, direction);
    }

    @PostMapping("/preview-synchronize")
    /** Sincroniza un draft bidireccional en memoria sin persistir modelos ni la transformación. */
    /** Descripción: Ejecuta la operación «previewSynchronize» correspondiente a la responsabilidad de TransformationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationResult previewSynchronize(@RequestBody TransformationDraftSynchronizationRequest request) {
        return runtime.synchronizeDraft(request.transformation(), request.synchronization());
    }

    @PostMapping("/{id}/synchronize")
    /** Sincroniza una transformación persistida usando reglas forward/reverse explícitas. */
    /** Descripción: Actualiza el estado gestionado por TransformationController mediante la operación «synchronize». Entradas: id: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationResult synchronize(@PathVariable String id, @RequestBody TransformationSynchronizationRequest request) {
        return runtime.synchronize(id, request);
    }

    @PutMapping("/{id}")
    /** Descripción: Actualiza el estado gestionado por TransformationController mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<TransformationSaveResponse> replace(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @RequestBody TransformationDefinitionDocument document) {
        TransformationSaveResponse response = service.replace(id, document, etag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @DeleteMapping("/{id}")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag) {
        service.delete(id, etag);
        return ResponseEntity.noContent().build();
    }
}
