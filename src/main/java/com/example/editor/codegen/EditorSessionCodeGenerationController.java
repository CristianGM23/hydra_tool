package com.example.editor.codegen;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.definition.CodeGenerationDefinition;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.runtime.session.EditorModelSessionService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;

/** Expone la misma generación dentro del runtime de un editor configurable/exportable. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/editors/{editorId}/runtime/sessions/{sessionId}/code-generation")
public class EditorSessionCodeGenerationController {
    private final EditorModelSessionService sessions;
    private final EditorDefinitionApplicationService editors;
    private final CanonicalMetamodelApplicationService metamodels;
    private final ModelCodeGenerationService generation;

    /** Descripción: Inicializa la API contextual de generación. Entradas: sesiones, editor, metamodelo y generador. Salida: controlador listo. */
    /** Descripción: Inicializa una instancia de EditorSessionCodeGenerationController con sus dependencias y datos de entrada. Entradas: sessions: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionCodeGenerationController(EditorModelSessionService sessions, EditorDefinitionApplicationService editors,
            CanonicalMetamodelApplicationService metamodels, ModelCodeGenerationService generation) {
        this.sessions = sessions; this.editors = editors; this.metamodels = metamodels; this.generation = generation;
    }

    /** Descripción: Lista únicamente los generadores permitidos por el editor exportable activo. Entradas: contexto de sesión. Salida: destinos visibles. */
    @GetMapping("/targets")
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de EditorSessionCodeGenerationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<ModelCodeGenerationTarget> targets(@PathVariable String metamodelId, @PathVariable String editorId, @PathVariable String sessionId) {
        sessions.modelForCodeGeneration(metamodelId, editorId, sessionId);
        CodeGenerationDefinition definition = editors.find(metamodelId, editorId).editorDefinition().codeGeneration();
        if(!definition.enabled() || !definition.exposeInRuntime()) return List.of();
        return generation.targets(new LinkedHashSet<>(definition.targetIds()));
    }

    /** Devuelve una vista previa textual de la selección activa sin descargar ni alterar la sesión. */
    @PostMapping("/{targetId}/preview")
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de EditorSessionCodeGenerationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelCodeGenerationPreview preview(@PathVariable String metamodelId, @PathVariable String editorId, @PathVariable String sessionId,
            @PathVariable String targetId, @RequestBody(required = false) ModelCodeGenerationRequest request) {
        CodeGenerationDefinition definition = editors.find(metamodelId, editorId).editorDefinition().codeGeneration();
        Set<String> allowed = new LinkedHashSet<>(definition.targetIds());
        if(!definition.enabled() || !definition.exposeInRuntime() || !allowed.contains(targetId)) throw new IllegalArgumentException("Code generation target is not enabled by this model editor: " + targetId);
        var model = sessions.modelForCodeGeneration(metamodelId, editorId, sessionId);
        var metamodel = metamodels.findById(metamodelId).metamodel();
        ModelCodeGenerationOptions requested = request == null ? null : request.options();
        ModelCodeGenerationOptions effective = requested == null
                ? new ModelCodeGenerationOptions(definition.outputNamespace(), model.name(), definition.includeTraceability(), definition.featuresFor(targetId)) : requested;
        return generation.preview(metamodel, model, targetId, effective, request == null ? List.of() : request.selectionElementIds());
    }

    /** Descripción: Genera un artefacto usando exactamente el modelo de la sesión y la configuración exportada con el editor. Entradas: contexto, destino y opciones. Salida: descarga generada. */
    @PostMapping("/{targetId}")
    /** Descripción: Crea o prepara el resultado de «generate» dentro de EditorSessionCodeGenerationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> generate(@PathVariable String metamodelId, @PathVariable String editorId, @PathVariable String sessionId,
            @PathVariable String targetId, @RequestBody(required = false) ModelCodeGenerationRequest request) {
        CodeGenerationDefinition definition = editors.find(metamodelId, editorId).editorDefinition().codeGeneration();
        Set<String> allowed = new LinkedHashSet<>(definition.targetIds());
        if(!definition.enabled() || !definition.exposeInRuntime() || !allowed.contains(targetId)) throw new IllegalArgumentException("Code generation target is not enabled by this model editor: " + targetId);
        var model = sessions.modelForCodeGeneration(metamodelId, editorId, sessionId);
        var metamodel = metamodels.findById(metamodelId).metamodel();
        ModelCodeGenerationOptions requested = request == null ? null : request.options();
        ModelCodeGenerationOptions effective = requested == null
                ? new ModelCodeGenerationOptions(definition.outputNamespace(), model.name(), definition.includeTraceability(), definition.featuresFor(targetId)) : requested;
        GeneratedModelArtifact artifact = generation.generate(metamodel, model, targetId, effective, request == null ? List.of() : request.selectionElementIds());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                .contentType(MediaType.parseMediaType(artifact.mediaType())).body(artifact.content());
    }
}
