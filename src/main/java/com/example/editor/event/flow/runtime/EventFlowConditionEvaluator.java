package com.example.editor.event.flow.runtime;

import org.springframework.stereotype.Service;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.expression.CanonicalEventExpressionContext;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;

/** Evalúa guards EventFlow con el mismo kernel semantic.expression del resto de HYDRA. */
@Service
public class EventFlowConditionEvaluator {
    private final ExpressionModelRepository expressionModels;
    private final CanonicalMetamodelApplicationService metamodels;
    private final ExpressionEvaluator evaluator;

    /** Descripción: Inicializa el evaluador de condiciones EventFlow. Entradas: repositorio de expresiones, servicio de metamodelos y evaluator neutral. Salida: deja el servicio listo para ejecutar guards. */ public EventFlowConditionEvaluator(ExpressionModelRepository expressionModels, CanonicalMetamodelApplicationService metamodels,
            ExpressionEvaluator evaluator) {
        this.expressionModels = expressionModels;
        this.metamodels = metamodels;
        this.evaluator = evaluator;
    }

    /** Descripción: Evalúa una condición booleana EventFlow. Entradas: metamodelId, expressionModelId, expressionId y event. Salida: devuelve true cuando la condición se cumple. */ public boolean matches(String metamodelId, String expressionModelId, String expressionId, CanonicalEvent event) {
        if(expressionId == null || expressionId.isBlank()) return true;
        if(expressionModelId == null || expressionModelId.isBlank()) throw new IllegalStateException("EventFlow condition requires expressionModelId");
        ExpressionModel model = expressionModels.findById(metamodelId).map(stored -> stored.expressionModel())
                .filter(value -> expressionModelId.equals(value.id()))
                .orElseThrow(() -> new IllegalStateException("EventFlow expression model was not found: " + expressionModelId));
        ExpressionDefinition expression = model.findExpression(expressionId)
                .orElseThrow(() -> new IllegalStateException("EventFlow condition expression was not found: " + expressionId));
        var metamodel = metamodels.findById(metamodelId).metamodel();
        Object value = evaluator.evaluate(expression, CanonicalEventExpressionContext.create(metamodel, event)).value();
        if(!(value instanceof Boolean result)) throw new IllegalStateException("EventFlow condition must evaluate to BOOLEAN: " + expressionId);
        return result;
    }
}
