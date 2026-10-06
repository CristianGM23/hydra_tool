package com.example.editor.transformation.runtime;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationRuleDirection;

/** Compila una definición canónica ya resoluble a un plan batch determinista. */
@Service
public class TransformationPlanCompiler {
    private final TransformationDefinitionChecker checker;

    /** Descripción: Inicializa el compilador con el checker canónico. Entradas: checker: validador de definiciones. Salida: instancia lista para compilar. */
    /** Descripción: Inicializa una instancia de TransformationPlanCompiler con sus dependencias y datos de entrada. Entradas: checker: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationPlanCompiler(TransformationDefinitionChecker checker) { this.checker = checker; }

    /** Descripción: Compila la dirección histórica source→target. Entradas: document: definición; source: metamodelo origen; target: metamodelo destino. Salida: plan forward validado. */
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de TransformationPlanCompiler. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CompiledTransformationPlan compile(TransformationDefinitionDocument document, MetaModel source, MetaModel target) {
        return compile(document, source, target, TransformationExecutionDirection.FORWARD);
    }

    /** Descripción: Compila una dirección M2M explícita sin inferir su inversa. Entradas: document: definición; source/target: contextos declarados; direction: sentido solicitado. Salida: plan direccional validado. */
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de TransformationPlanCompiler. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CompiledTransformationPlan compile(TransformationDefinitionDocument document, MetaModel source, MetaModel target,
            TransformationExecutionDirection direction) {
        TransformationExecutionDirection requested = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        var report = checker.check(document, List.of(source), List.of(target));
        if(!report.valid()) {
            String codes = report.diagnostics().stream().filter(item -> item.severity().name().equals("ERROR"))
                    .map(item -> item.code()).distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("UNKNOWN");
            throw new TransformationExecutionException("Transformation cannot be compiled because it contains blocking diagnostics: " + codes);
        }
        if(requested == TransformationExecutionDirection.REVERSE && document.directionality() != TransformationDirectionality.BIDIRECTIONAL)
            throw new TransformationExecutionException("Reverse execution requires a bidirectional transformation");
        TransformationRuleDirection ruleDirection = requested == TransformationExecutionDirection.REVERSE
                ? TransformationRuleDirection.REVERSE : TransformationRuleDirection.FORWARD;
        List<com.example.editor.transformation.core.TransformationRule> executable = document.rules().stream()
                .filter(rule -> rule.enabled() && rule.direction() == ruleDirection).toList();
        if(executable.isEmpty()) throw new TransformationExecutionException("No enabled " + requested.name().toLowerCase() + " rules are available");
        TransformationEndpoint sourceEndpoint = requested == TransformationExecutionDirection.REVERSE
                ? document.targets().getFirst() : document.sources().getFirst();
        TransformationEndpoint targetEndpoint = requested == TransformationExecutionDirection.REVERSE
                ? document.sources().getFirst() : document.targets().getFirst();
        return new CompiledTransformationPlan(document.id(), document.name(), document.directionality(), requested,
                sourceEndpoint.metamodelId(), targetEndpoint.metamodelId(), targetEndpoint.metamodelFingerprint(), executable,
                document.tracePolicy());
    }
}
