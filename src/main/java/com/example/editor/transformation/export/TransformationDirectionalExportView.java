package com.example.editor.transformation.export;

import java.util.List;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;

/** Builds a forward-shaped ephemeral projection for one explicit M2M execution direction. */
final class TransformationDirectionalExportView {
    /** Descripción: Inicializa una instancia de TransformationDirectionalExportView con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TransformationDirectionalExportView() {}

    /** Descripción: Proyecta una definición M2M sobre el sentido solicitado sin inventar reglas inversas. Entradas: document: definición completa; direction: sentido de exportación. Salida: vista unidireccional efímera. */
    /** Descripción: Ejecuta la operación «of» correspondiente a la responsabilidad de TransformationDirectionalExportView. Entradas: document: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static TransformationDefinitionDocument of(TransformationDefinitionDocument document, TransformationExecutionDirection direction) {
        if(document.kind() != TransformationKind.M2M) return document;
        boolean reverse = direction == TransformationExecutionDirection.REVERSE;
        TransformationRuleDirection expected = reverse ? TransformationRuleDirection.REVERSE : TransformationRuleDirection.FORWARD;
        List<TransformationRule> rules = document.rules().stream().filter(rule -> rule.direction() == expected)
                .map(TransformationDirectionalExportView::asForwardRule).toList();
        List<TransformationEndpoint> sources = (reverse ? document.targets() : document.sources()).stream()
                .map(endpoint -> role(endpoint, TransformationEndpointRole.SOURCE)).toList();
        List<TransformationEndpoint> targets = (reverse ? document.sources() : document.targets()).stream()
                .map(endpoint -> role(endpoint, TransformationEndpointRole.TARGET)).toList();
        return new TransformationDefinitionDocument(document.schemaVersion(), document.id(), document.name(), document.kind(),
                TransformationDirectionality.UNIDIRECTIONAL, TransformationSynchronizationPolicy.defaults(), sources, targets, rules,
                document.generationRules(), document.textToModel(), document.executionPolicy(), document.tracePolicy(), document.annotations());
    }

    /** Descripción: Reetiqueta un endpoint conservando identidad y metamodelo. Entradas: endpoint: endpoint original; role: rol de la vista. Salida: copia con rol direccional. */
    /** Descripción: Ejecuta la operación «role» correspondiente a la responsabilidad de TransformationDirectionalExportView. Entradas: endpoint: dato de entrada necesario para la operación; role: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationEndpoint role(TransformationEndpoint endpoint, TransformationEndpointRole role) {
        return new TransformationEndpoint(endpoint.id(), role, endpoint.metamodelId(), endpoint.metamodelFingerprint(), endpoint.alias(),
                endpoint.required(), endpoint.annotations());
    }

    /** Descripción: Convierte una regla ya seleccionada en forma forward para reutilizar adapters existentes. Entradas: rule: regla explícita del sentido seleccionado. Salida: copia forward sin alterar correspondencia ni bindings. */
    /** Descripción: Ejecuta la operación «asForwardRule» correspondiente a la responsabilidad de TransformationDirectionalExportView. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationRule asForwardRule(TransformationRule rule) {
        return new TransformationRule(rule.id(), rule.name(), rule.enabled(), rule.sourceEndpointId(), rule.sourceTypeId(),
                rule.sourceAlias(), rule.guard(), rule.targets(), rule.bindings(), rule.annotations(),
                TransformationRuleDirection.FORWARD, rule.correspondenceId());
    }
}
