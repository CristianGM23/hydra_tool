package com.example.editor.event.flow.runtime;

import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.definition.ActionEditorTool;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionRequest;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolRuntime;
import com.example.editor.event.flow.FlowNodeKind;
import tools.jackson.databind.ObjectMapper;

/** Adaptador neutral de ACTION_STEP sobre CanonicalActionExecutor. */
@Component
public class ActionFlowExternalStepExecutor implements FlowExternalStepExecutor {
    private final EditorSessionToolRuntime runtime;
    private final FlowCanonicalModelSupport models;
    /** Descripción: Inicializa una instancia de ActionFlowExternalStepExecutor con sus dependencias y datos de entrada. Entradas: runtime: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ActionFlowExternalStepExecutor(EditorSessionToolRuntime runtime, ObjectMapper mapper) {
        this.runtime = runtime; this.models = new FlowCanonicalModelSupport(mapper);
    }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: kind: dato de entrada necesario para la operación; referenceId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(FlowNodeKind kind, String referenceId) { return kind == FlowNodeKind.ACTION_STEP && referenceId != null && !referenceId.isBlank(); }
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de ActionFlowExternalStepExecutor. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public FlowExternalStepResult execute(FlowExternalStepContext context) {
        try {
            var model = models.model(context.event());
            var tool = new ActionEditorTool("flow-action:" + context.node().id(), context.node().name(), "", "", List.of(), "", "", context.node().referenceId());
            var request = new EditorSessionToolExecutionRequest("", models.target(context, model), models.selection(context), models.arguments(context));
            var outcome = runtime.execute(tool, context.metamodelId(), model, request);
            return new FlowExternalStepResult(true, models.updates(outcome), String.join("; ", outcome.messages()));
        } catch(RuntimeException exception) { return new FlowExternalStepResult(false, java.util.Map.of(), message(exception)); }
    }
    /** Descripción: Ejecuta la operación «message» correspondiente a la responsabilidad de ActionFlowExternalStepExecutor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String message(Throwable value) { return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage(); }
}
