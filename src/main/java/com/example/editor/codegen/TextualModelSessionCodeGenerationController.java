package com.example.editor.codegen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.textual.runtime.TextualModelSession;
import com.example.editor.textual.runtime.TextualModelSessionService;
import com.example.editor.validation.model.CanonicalModelDocument;

/**
 * Expone todas las perspectivas de generación del modelo semántico producido por el editor textual.
 * Reutiliza exactamente el catálogo/renderers del editor de modelos; no mantiene un segundo catálogo.
 */
@RestController
@RequestMapping("/api/textual-model-sessions/{sessionId}/perspectives")
public class TextualModelSessionCodeGenerationController {
    private final TextualModelSessionService sessions;
    private final CanonicalMetamodelApplicationService metamodels;
    private final GrammarCompilationService compilations;
    private final ModelCodeGenerationService generation;

    /** Descripción: Inicializa una instancia de TextualModelSessionCodeGenerationController con sus dependencias y datos de entrada. Entradas: sessions: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; compilations: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualModelSessionCodeGenerationController(TextualModelSessionService sessions,
            CanonicalMetamodelApplicationService metamodels, GrammarCompilationService compilations,
            ModelCodeGenerationService generation) {
        this.sessions = sessions;
        this.metamodels = metamodels;
        this.compilations = compilations;
        this.generation = generation;
    }

    /** Lista el catálogo completo de perspectivas soportadas por HYDRA para modelos. */
    @GetMapping("/targets")
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de PerspectiveContext. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<ModelCodeGenerationTarget> targets(@PathVariable String sessionId) {
        sessions.modelForPerspectives(sessionId);
        return generation.targets(null);
    }

    /** Previsualiza una perspectiva sin persistir, promover ni descargar el modelo. */
    @PostMapping("/{targetId}/preview")
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de PerspectiveContext. Entradas: sessionId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelCodeGenerationPreview preview(@PathVariable String sessionId, @PathVariable String targetId,
            @RequestBody(required = false) ModelCodeGenerationRequest request) {
        PerspectiveContext context = context(sessionId);
        ModelCodeGenerationOptions options = effectiveOptions(context.model(), request);
        return generation.preview(context.metamodel(), context.model(), targetId, options,
                request == null ? List.of() : request.selectionElementIds());
    }

    /** Genera la misma perspectiva que se ha podido previsualizar. */
    @PostMapping("/{targetId}")
    /** Descripción: Crea o prepara el resultado de «generate» dentro de PerspectiveContext. Entradas: sessionId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> generate(@PathVariable String sessionId, @PathVariable String targetId,
            @RequestBody(required = false) ModelCodeGenerationRequest request) {
        PerspectiveContext context = context(sessionId);
        ModelCodeGenerationOptions options = effectiveOptions(context.model(), request);
        GeneratedModelArtifact artifact = generation.generate(context.metamodel(), context.model(), targetId, options,
                request == null ? List.of() : request.selectionElementIds());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                .contentType(MediaType.parseMediaType(artifact.mediaType())).body(artifact.content());
    }

    /** Descripción: Ejecuta la operación «context» correspondiente a la responsabilidad de PerspectiveContext. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private PerspectiveContext context(String sessionId) {
        TextualModelSession session = sessions.find(sessionId).document();
        CanonicalModelDocument model = sessions.modelForPerspectives(sessionId);
        return new PerspectiveContext(resolveMetamodel(session, model), model);
    }

    /** Descripción: Obtiene la información correspondiente a «resolveMetamodel» dentro de PerspectiveContext. Entradas: session: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel resolveMetamodel(TextualModelSession session, CanonicalModelDocument model) {
        try {
            return metamodels.findById(model.metamodelId()).metamodel();
        } catch(MetamodelApplicationException exception) {
            if(exception.getReason() != MetamodelApplicationException.Reason.NOT_FOUND) throw exception;
        }
        GrammarCompilationSnapshot snapshot = compilations.find(session.grammarSnapshotId());
        MetaModel inferred = snapshot.classDiagram().inferredMetamodel();
        if(inferred == null || (inferred.classes().isEmpty() && inferred.enums().isEmpty() && inferred.dataTypes().isEmpty()))
            throw new IllegalStateException("The textual model has no persisted or inferred metamodel available for perspectives");
        Map<String,String> annotations = new LinkedHashMap<>(inferred.annotations());
        annotations.put("hydra.perspective.source", "GRAMMAR_INFERRED_METAMODEL");
        annotations.put("hydra.perspective.originalMetamodelId", inferred.id());
        return new MetaModel(inferred.schemaVersion(), model.metamodelId(), inferred.name(), inferred.classes(), inferred.enums(), inferred.dataTypes(), inferred.relations(), annotations);
    }

    /** Descripción: Ejecuta la operación «effectiveOptions» correspondiente a la responsabilidad de PerspectiveContext. Entradas: model: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static ModelCodeGenerationOptions effectiveOptions(CanonicalModelDocument model, ModelCodeGenerationRequest request) {
        if(request != null && request.options() != null) return request.options();
        String safeName = model.name().isBlank() ? "GeneratedTextualModel" : model.name();
        return new ModelCodeGenerationOptions("generated.textual", safeName, true, null);
    }

    private record PerspectiveContext(MetaModel metamodel, CanonicalModelDocument model) {}
}
