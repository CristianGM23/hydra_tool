package com.example.editor.event.flow.runtime;

import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.definition.ValidateEditorTool;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolExecutionRequest;
import com.example.editor.definition.runtime.session.tool.EditorSessionToolRuntime;
import com.example.editor.event.flow.FlowNodeKind;
import tools.jackson.databind.ObjectMapper;

/** Adaptador neutral de COMMAND_STEP con referencia validation o validation:MODE. */
@Component
public class ValidationFlowExternalStepExecutor implements FlowExternalStepExecutor {
    private final EditorSessionToolRuntime runtime;
    private final FlowCanonicalModelSupport models;
    /** Descripción: Inicializa una instancia de ValidationFlowExternalStepExecutor con sus dependencias y datos de entrada. Entradas: runtime: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationFlowExternalStepExecutor(EditorSessionToolRuntime runtime, ObjectMapper mapper) {
        this.runtime = runtime; this.models = new FlowCanonicalModelSupport(mapper);
    }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: kind: dato de entrada necesario para la operación; referenceId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(FlowNodeKind kind, String referenceId) {
        return kind == FlowNodeKind.COMMAND_STEP && referenceId != null
                && (referenceId.equalsIgnoreCase("validation") || referenceId.toLowerCase().startsWith("validation:"));
    }
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de ValidationFlowExternalStepExecutor. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public FlowExternalStepResult execute(FlowExternalStepContext context) {
        try {
            var model = models.model(context.event());
            String reference = context.node().referenceId();
            String mode = reference.contains(":") ? reference.substring(reference.indexOf(':') + 1) : "MANUAL";
            var tool = new ValidateEditorTool("flow-validation:" + context.node().id(), context.node().name(), "", "", List.of(), "", "", mode);
            var request = new EditorSessionToolExecutionRequest("", models.target(context, model), models.selection(context), models.arguments(context));
            var outcome = runtime.execute(tool, context.metamodelId(), model, request);
            boolean valid = outcome.validationReport() == null || outcome.validationReport().diagnostics().stream().noneMatch(item -> item.severity().name().equals("ERROR"));
            return new FlowExternalStepResult(valid, models.updates(outcome), String.join("; ", outcome.messages()));
        } catch(RuntimeException exception) { return new FlowExternalStepResult(false, java.util.Map.of(), message(exception)); }
    }
    /** Descripción: Ejecuta la operación «message» correspondiente a la responsabilidad de ValidationFlowExternalStepExecutor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String message(Throwable value) { return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage(); }
}
