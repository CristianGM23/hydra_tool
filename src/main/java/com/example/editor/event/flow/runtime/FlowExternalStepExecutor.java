package com.example.editor.event.flow.runtime;

import com.example.editor.event.flow.FlowNodeKind;

/** SPI para conectar nodos COMMAND/ACTION/OPERATION/MANUAL sin acoplar el núcleo. */
public interface FlowExternalStepExecutor {
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: kind: dato de entrada necesario para la operación; referenceId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean supports(FlowNodeKind kind, String referenceId);
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de FlowExternalStepExecutor. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ FlowExternalStepResult execute(FlowExternalStepContext context);
}
