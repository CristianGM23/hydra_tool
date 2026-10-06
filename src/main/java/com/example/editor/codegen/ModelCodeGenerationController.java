package com.example.editor.codegen;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.operation.runtime.CanonicalModelRuntimeAdapter;

/** API de generación desde los modelos abiertos por el Editor de Modelos clásico. */
@RestController
@RequestMapping("/api/models/{modelId}/code-generation")
public class ModelCodeGenerationController {
    private final ModelDocumentService models;
    private final CanonicalMetamodelApplicationService metamodels;
    private final ModelCodeGenerationService generation;

    /** Descripción: Inicializa la API de generación con los servicios del modelo y metamodelo. Entradas: servicios colaboradores. Salida: controlador listo. */
    /** Descripción: Inicializa una instancia de ModelCodeGenerationController con sus dependencias y datos de entrada. Entradas: models: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelCodeGenerationController(ModelDocumentService models, CanonicalMetamodelApplicationService metamodels, ModelCodeGenerationService generation) {
        this.models = models; this.metamodels = metamodels; this.generation = generation;
    }

    /** Descripción: Lista los destinos disponibles para el modelo abierto. Entradas: identificador de modelo. Salida: catálogo alineado con importación de metamodelos. */
    @GetMapping("/targets")
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de ModelCodeGenerationController. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<ModelCodeGenerationTarget> targets(@PathVariable String modelId) {
        models.find(modelId);
        return generation.targets(java.util.Set.of());
    }

    /** Devuelve una vista previa textual y formateable sin descargar archivos. */
    @PostMapping("/{targetId}/preview")
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ModelCodeGenerationController. Entradas: modelId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelCodeGenerationPreview preview(@PathVariable String modelId, @PathVariable String targetId, @RequestBody(required = false) ModelCodeGenerationRequest request) {
        ModelDocumentResponse live = models.find(modelId);
        MetaModel metamodel = metamodels.findById(live.metamodelId()).metamodel();
        var canonical = new CanonicalModelRuntimeAdapter(metamodel).toCanonical(live);
        return generation.preview(metamodel, canonical, targetId, request == null ? null : request.options(), request == null ? List.of() : request.selectionElementIds());
    }

    /** Descripción: Genera y descarga código/proyección a partir del estado actual del modelo. Entradas: modelo, destino y opciones. Salida: artefacto descargable. */
    @PostMapping("/{targetId}")
    /** Descripción: Crea o prepara el resultado de «generate» dentro de ModelCodeGenerationController. Entradas: modelId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> generate(@PathVariable String modelId, @PathVariable String targetId,
            @RequestBody(required = false) ModelCodeGenerationRequest request) {
        ModelDocumentResponse live = models.find(modelId);
        MetaModel metamodel = metamodels.findById(live.metamodelId()).metamodel();
        var canonical = new CanonicalModelRuntimeAdapter(metamodel).toCanonical(live);
        GeneratedModelArtifact artifact = generation.generate(metamodel, canonical, targetId, request == null ? null : request.options(), request == null ? List.of() : request.selectionElementIds());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                .contentType(MediaType.parseMediaType(artifact.mediaType())).body(artifact.content());
    }
}
