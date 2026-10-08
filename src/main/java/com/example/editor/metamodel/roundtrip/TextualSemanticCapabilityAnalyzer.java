package com.example.editor.metamodel.roundtrip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.export.MetamodelExportSupport;

/**
 * Separa equivalencia estructural de ejecutabilidad nativa. Xtext y Langium pueden conservar una
 * referencia en la gramática aunque necesiten un name provider/scope externo para resolverla.
 */
@Component
public class TextualSemanticCapabilityAnalyzer {
    /** Descripción: Evalúa si las referencias del metamodelo pueden resolverse nativamente en Xtext/Langium sin introducir atributos sintéticos ni alterar el dominio. Entradas: model: metamodelo canónico cuyas referencias y tipos destino se inspeccionan. Salida: devuelve el nivel de fidelidad y los requisitos externos que bloquean o condicionan el linking nativo. */
    public TextualSemanticCapabilityReport analyze(MetaModel model) {
        if(model == null) return new TextualSemanticCapabilityReport(SemanticFidelity.UNAVAILABLE,
                List.of(new TextualSemanticIssue("MODEL_UNAVAILABLE", "$", "No canonical metamodel is available", true)));
        Map<String,MetaClass> classes = new LinkedHashMap<>();
        model.classes().forEach(item -> classes.put(item.id(), item));
        List<TextualSemanticIssue> issues = new ArrayList<>();
        model.relations().stream().filter(item -> item.kind() == MetaRelationKind.REFERENCE || item.kind() == MetaRelationKind.AGGREGATION).forEach(relation -> {
            MetaClass target = classes.get(relation.targetTypeId());
            if(target == null) {
                issues.add(new TextualSemanticIssue("REFERENCE_TARGET_UNAVAILABLE", "$.relations["+relation.name()+"]", "Reference target is not present in the canonical class set", true));
                return;
            }
            if(MetamodelExportSupport.findNameAttribute(target) == null) {
                issues.add(new TextualSemanticIssue("REFERENCE_SCOPE_REQUIRES_EXTERNAL_PROVIDER", "$.relations["+relation.name()+"]",
                        "Target type '"+target.name()+"' has no canonical string attribute named 'name'; the reference is preserved, but native linking requires an explicit scope/name provider instead of a synthetic attribute", true));
            }
        });
        SemanticFidelity fidelity = issues.isEmpty() ? SemanticFidelity.EXACT : SemanticFidelity.HIGH;
        return new TextualSemanticCapabilityReport(fidelity, issues);
    }
}
