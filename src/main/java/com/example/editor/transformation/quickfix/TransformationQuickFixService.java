package com.example.editor.transformation.quickfix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationCollectionMode;
import com.example.editor.transformation.core.TransformationConversionMode;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationValuePolicy;

/** Produce quick-fixes declarativos sin mutar el draft ni persistir decisiones automáticamente. */
@Service
public class TransformationQuickFixService {
    private final TransformationApplicationService transformations;
    private final CanonicalMetamodelApplicationService metamodels;

    /** Descripción: Inicializa el servicio de quick-fixes. Entradas: transformations: servicio canónico; metamodels: catálogo de metamodelos. Salida: deja el servicio preparado. */
    /** Descripción: Inicializa una instancia de TransformationQuickFixService con sus dependencias y datos de entrada. Entradas: transformations: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationQuickFixService(TransformationApplicationService transformations, CanonicalMetamodelApplicationService metamodels) {
        this.transformations = transformations;
        this.metamodels = metamodels;
    }

    /** Descripción: Calcula quick-fixes para el draft actual a partir de diagnósticos canónicos. Entradas: document: transformación. Salida: devuelve propuestas completas y revisables. */
    /** Descripción: Ejecuta la operación «suggest» correspondiente a la responsabilidad de Located. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationQuickFixReport suggest(TransformationDefinitionDocument document) {
        var report = transformations.check(document);
        if(document == null) return new TransformationQuickFixReport("", List.of());
        if(document.kind() != com.example.editor.transformation.core.TransformationKind.M2M) return new TransformationQuickFixReport(document.id(), List.of());
        MetaModel source = document.sources().isEmpty() || document.sources().getFirst().metamodelId().isBlank() ? null
                : metamodels.findById(document.sources().getFirst().metamodelId()).metamodel();
        MetaModel target = document.targets().isEmpty() || document.targets().getFirst().metamodelId().isBlank() ? null
                : metamodels.findById(document.targets().getFirst().metamodelId()).metamodel();
        List<TransformationQuickFix> fixes = new ArrayList<>();
        for(TransformationDiagnostic diagnostic : report.diagnostics()) {
            switch(diagnostic.code()) {
                case "TYPE_CONVERSION_REQUIRED" -> conversionFix(document, source, target, diagnostic, fixes);
                case "COLLECTION_REDUCTION_REQUIRED" -> collectionFix(document, diagnostic, fixes);
                case "UNKNOWN_SOURCE_ATTRIBUTE", "UNKNOWN_TARGET_ATTRIBUTE", "UNKNOWN_SOURCE_RELATION", "UNKNOWN_TARGET_RELATION" -> removeBrokenBindingFix(document, diagnostic, fixes);
                default -> { }
            }
        }
        if(source != null && target != null) requiredMappings(document, source, target, fixes);
        return new TransformationQuickFixReport(document.id(), fixes);
    }

    /** Descripción: Sugiere una conversión explícita según el tipo destino cuando existe una conversión canónica soportada. Entradas: document: transformación; source: metamodelo origen; target: metamodelo destino; diagnostic: diagnóstico; fixes: acumulador. Salida: no devuelve valor. */
    /** Descripción: Ejecuta la operación «conversionFix» correspondiente a la responsabilidad de Located. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void conversionFix(TransformationDefinitionDocument document, MetaModel source, MetaModel target,
            TransformationDiagnostic diagnostic, List<TransformationQuickFix> fixes) {
        Located located = locate(document, source, target, diagnostic.ruleId(), diagnostic.bindingId());
        if(located == null || located.targetAttribute() == null) return;
        TransformationConversionMode mode = conversionFor(located.targetAttribute());
        if(mode == TransformationConversionMode.NONE) return;
        TransformationValuePolicy old = located.binding().valuePolicy();
        TransformationBinding nextBinding = new TransformationBinding(located.binding().id(), located.binding().targetAlias(),
                located.binding().targetFeatureId(), located.binding().kind(), located.binding().sourceFeatureId(), located.binding().literalValue(),
                located.binding().expression(), new TransformationValuePolicy(old.collectionMode(), mode, old.enumMapping(), old.errorPolicy(), old.fallbackValue()), located.binding().annotations());
        TransformationDefinitionDocument next = replaceBinding(document, located.rule(), nextBinding);
        fixes.add(new TransformationQuickFix("fix:" + diagnostic.bindingId() + ":conversion", diagnostic.code(), diagnostic.ruleId(), diagnostic.bindingId(),
                "Define explicit type conversion", "Convert the source value explicitly before assigning it to the target property.", false, next));
    }

    /** Descripción: Sugiere FIRST para una colección que alimenta un destino escalar; requiere revisión humana. Entradas: document: transformación; diagnostic: diagnóstico; fixes: acumulador. Salida: no devuelve valor. */
    /** Descripción: Ejecuta la operación «collectionFix» correspondiente a la responsabilidad de Located. Entradas: document: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectionFix(TransformationDefinitionDocument document, TransformationDiagnostic diagnostic, List<TransformationQuickFix> fixes) {
        Located located = locate(document, null, null, diagnostic.ruleId(), diagnostic.bindingId());
        if(located == null) return;
        TransformationValuePolicy old = located.binding().valuePolicy();
        TransformationBinding nextBinding = new TransformationBinding(located.binding().id(), located.binding().targetAlias(), located.binding().targetFeatureId(),
                located.binding().kind(), located.binding().sourceFeatureId(), located.binding().literalValue(), located.binding().expression(),
                new TransformationValuePolicy(TransformationCollectionMode.FIRST, old.conversionMode(), old.enumMapping(), old.errorPolicy(), old.fallbackValue()), located.binding().annotations());
        fixes.add(new TransformationQuickFix("fix:" + diagnostic.bindingId() + ":first", diagnostic.code(), diagnostic.ruleId(), diagnostic.bindingId(),
                "Use the first source value", "Reduce the source collection with FIRST. Review this choice because it changes cardinality semantics.", false,
                replaceBinding(document, located.rule(), nextBinding)));
    }

    /** Descripción: Propone eliminar un binding que referencia un elemento desaparecido. Entradas: document: transformación; diagnostic: diagnóstico; fixes: acumulador. Salida: no devuelve valor. */
    /** Descripción: Elimina o libera el recurso gestionado por «removeBrokenBindingFix» de forma controlada. Entradas: document: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void removeBrokenBindingFix(TransformationDefinitionDocument document, TransformationDiagnostic diagnostic, List<TransformationQuickFix> fixes) {
        TransformationRule rule = document.rules().stream().filter(item -> item.id().equals(diagnostic.ruleId())).findFirst().orElse(null);
        if(rule == null || diagnostic.bindingId().isBlank() || rule.bindings().stream().noneMatch(item -> item.id().equals(diagnostic.bindingId()))) return;
        TransformationRule nextRule = new TransformationRule(rule.id(), rule.name(), rule.enabled(), rule.sourceEndpointId(), rule.sourceTypeId(),
                rule.sourceAlias(), rule.guard(), rule.targets(), rule.bindings().stream().filter(item -> !item.id().equals(diagnostic.bindingId())).toList(), rule.annotations(), rule.direction(), rule.correspondenceId());
        fixes.add(new TransformationQuickFix("fix:" + diagnostic.bindingId() + ":remove", diagnostic.code(), diagnostic.ruleId(), diagnostic.bindingId(),
                "Remove obsolete mapping", "Remove the mapping that references a metamodel element that no longer exists, then select a replacement if needed.", false,
                replaceRule(document, nextRule)));
    }

    /** Descripción: Sugiere mappings directos para atributos obligatorios sin mapping cuando existe una coincidencia exacta de nombre y tipo. Entradas: document: transformación; source: metamodelo origen; target: metamodelo destino; fixes: acumulador. Salida: no devuelve valor. */
    /** Descripción: Valida las condiciones necesarias para «requiredMappings» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requiredMappings(TransformationDefinitionDocument document, MetaModel source, MetaModel target, List<TransformationQuickFix> fixes) {
        for(TransformationRule rule : document.rules()) {
            MetaModel inputModel = rule.direction() == TransformationRuleDirection.REVERSE ? target : source;
            MetaModel outputModel = rule.direction() == TransformationRuleDirection.REVERSE ? source : target;
            MetaClass sourceClass = inputModel.classes().stream().filter(item -> item.id().equals(rule.sourceTypeId())).findFirst().orElse(null);
            MetaClass targetClass = rule.targets().isEmpty() ? null : outputModel.classes().stream().filter(item -> item.id().equals(rule.targets().getFirst().typeId())).findFirst().orElse(null);
            if(sourceClass == null || targetClass == null) continue;
            String alias = rule.targets().getFirst().alias();
            for(MetaAttribute targetAttribute : targetClass.attributes()) {
                boolean required = targetAttribute.multiplicity() != null && targetAttribute.multiplicity().lowerBound() > 0 && targetAttribute.defaultValue().isBlank();
                boolean mapped = rule.bindings().stream().anyMatch(item -> item.targetAlias().equals(alias) && item.targetFeatureId().equals(targetAttribute.id()));
                if(!required || mapped) continue;
                List<MetaAttribute> candidates = sourceClass.attributes().stream().filter(item -> sameName(item.name(), targetAttribute.name()) && sameType(item, targetAttribute)).toList();
                if(candidates.size() != 1) continue;
                MetaAttribute candidate = candidates.getFirst();
                String bindingId = uniqueBindingId(rule, "binding:" + targetAttribute.id());
                TransformationBinding binding = new TransformationBinding(bindingId, alias, targetAttribute.id(), TransformationBindingKind.DIRECT,
                        candidate.id(), null, null, TransformationValuePolicy.defaults(), Map.of("hydra.quickFix", "required-compatible-name"));
                TransformationRule nextRule = new TransformationRule(rule.id(), rule.name(), rule.enabled(), rule.sourceEndpointId(), rule.sourceTypeId(), rule.sourceAlias(),
                        rule.guard(), rule.targets(), append(rule.bindings(), binding), rule.annotations(), rule.direction(), rule.correspondenceId());
                fixes.add(new TransformationQuickFix("fix:" + rule.id() + ":map:" + targetAttribute.id(), "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED", rule.id(), bindingId,
                        "Map compatible required property", "Map the uniquely compatible source property to this required target property.", false,
                        replaceRule(document, nextRule)));
            }
        }
    }

    /** Descripción: Localiza regla, binding y atributos asociados. Entradas: document: transformación; source: metamodelo origen opcional; target: metamodelo destino opcional; ruleId: regla; bindingId: binding. Salida: devuelve la localización o null. */
    /** Descripción: Ejecuta la operación «locate» correspondiente a la responsabilidad de Located. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; bindingId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Located locate(TransformationDefinitionDocument document, MetaModel source, MetaModel target, String ruleId, String bindingId) {
        TransformationRule rule = document.rules().stream().filter(item -> item.id().equals(ruleId)).findFirst().orElse(null);
        if(rule == null) return null;
        TransformationBinding binding = rule.bindings().stream().filter(item -> item.id().equals(bindingId)).findFirst().orElse(null);
        if(binding == null) return null;
        MetaModel inputModel = rule.direction() == TransformationRuleDirection.REVERSE ? target : source;
        MetaModel outputModel = rule.direction() == TransformationRuleDirection.REVERSE ? source : target;
        MetaClass sourceClass = inputModel == null ? null : inputModel.classes().stream().filter(item -> item.id().equals(rule.sourceTypeId())).findFirst().orElse(null);
        MetaClass targetClass = outputModel == null || rule.targets().isEmpty() ? null : outputModel.classes().stream().filter(item -> item.id().equals(rule.targets().getFirst().typeId())).findFirst().orElse(null);
        MetaAttribute sourceAttribute = sourceClass == null ? null : sourceClass.attributes().stream().filter(item -> item.id().equals(binding.sourceFeatureId())).findFirst().orElse(null);
        MetaAttribute targetAttribute = targetClass == null ? null : targetClass.attributes().stream().filter(item -> item.id().equals(binding.targetFeatureId())).findFirst().orElse(null);
        return new Located(rule, binding, sourceAttribute, targetAttribute);
    }

    /** Descripción: Selecciona la conversión canónica correspondiente al tipo primitivo destino. Entradas: target: atributo destino. Salida: devuelve el modo o NONE. */
    /** Descripción: Ejecuta la operación «conversionFor» correspondiente a la responsabilidad de Located. Entradas: target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationConversionMode conversionFor(MetaAttribute target) {
        if(target.type() == null || target.type().kind() != MetaTypeReferenceKind.PRIMITIVE) return TransformationConversionMode.NONE;
        try {
            return switch(MetaPrimitiveType.valueOf(target.type().targetId())) {
                case STRING, CHARACTER, DATE -> TransformationConversionMode.TO_STRING;
                case BYTE, SHORT, INTEGER, BIG_INTEGER -> TransformationConversionMode.TO_INTEGER;
                case LONG -> TransformationConversionMode.TO_LONG;
                case FLOAT, DOUBLE, DECIMAL -> TransformationConversionMode.TO_DOUBLE;
                case BOOLEAN -> TransformationConversionMode.TO_BOOLEAN;
                default -> TransformationConversionMode.NONE;
            };
        } catch(IllegalArgumentException ignored) { return TransformationConversionMode.NONE; }
    }

    /** Descripción: Sustituye un binding dentro de su regla. Entradas: document: transformación; rule: regla; binding: binding nuevo. Salida: devuelve una copia actualizada. */
    /** Descripción: Actualiza el estado gestionado por Located mediante la operación «replaceBinding». Entradas: document: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationDefinitionDocument replaceBinding(TransformationDefinitionDocument document, TransformationRule rule, TransformationBinding binding) {
        TransformationRule next = new TransformationRule(rule.id(), rule.name(), rule.enabled(), rule.sourceEndpointId(), rule.sourceTypeId(), rule.sourceAlias(), rule.guard(),
                rule.targets(), rule.bindings().stream().map(item -> item.id().equals(binding.id()) ? binding : item).toList(), rule.annotations(), rule.direction(), rule.correspondenceId());
        return replaceRule(document, next);
    }

    /** Descripción: Sustituye una regla dentro de la definición. Entradas: document: transformación; rule: regla nueva. Salida: devuelve una copia actualizada. */
    /** Descripción: Actualiza el estado gestionado por Located mediante la operación «replaceRule». Entradas: document: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationDefinitionDocument replaceRule(TransformationDefinitionDocument document, TransformationRule rule) {
        return new TransformationDefinitionDocument(document.schemaVersion(), document.id(), document.name(), document.kind(), document.directionality(), document.synchronizationPolicy(), document.sources(), document.targets(),
                document.rules().stream().map(item -> item.id().equals(rule.id()) ? rule : item).toList(), document.generationRules(), document.textToModel(),
                document.executionPolicy(), document.tracePolicy(), document.annotations());
    }

    /** Descripción: Comprueba igualdad de nombres sin distinguir mayúsculas. Entradas: left: nombre; right: nombre. Salida: devuelve true si coinciden. */
    /** Descripción: Ejecuta la operación «sameName» correspondiente a la responsabilidad de Located. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sameName(String left, String right) { return left != null && right != null && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT)); }

    /** Descripción: Comprueba compatibilidad exacta de tipo y multiplicidad para sugerencias conservadoras. Entradas: source: atributo origen; target: atributo destino. Salida: devuelve true si preserva ambos contratos. */
    /** Descripción: Ejecuta la operación «sameType» correspondiente a la responsabilidad de Located. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sameType(MetaAttribute source, MetaAttribute target) {
        return source.type() != null && source.type().equals(target.type()) && java.util.Objects.equals(source.multiplicity(), target.multiplicity());
    }

    /** Descripción: Añade un elemento preservando el orden existente. Entradas: values: lista; value: elemento. Salida: devuelve una lista inmutable nueva. */
    /** Descripción: Ejecuta la operación «append» correspondiente a la responsabilidad de Located. Entradas: values: colección de elementos que debe procesarse; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> append(List<T> values, T value) { List<T> result = new ArrayList<>(values); result.add(value); return List.copyOf(result); }

    /** Descripción: Genera un identificador de binding que no colisiona dentro de la regla. Entradas: rule: regla; base: base del identificador. Salida: devuelve un identificador único. */
    /** Descripción: Ejecuta la operación «uniqueBindingId» correspondiente a la responsabilidad de Located. Entradas: rule: dato de entrada necesario para la operación; base: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String uniqueBindingId(TransformationRule rule, String base) { String candidate = base; int index = 2; while(containsBinding(rule, candidate)) candidate = base + ":" + index++; return candidate; }

    /** Descripción: Comprueba si la regla ya contiene el identificador indicado. Entradas: rule: regla; id: identificador. Salida: devuelve true si existe. */
    /** Descripción: Evalúa la condición «containsBinding» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean containsBinding(TransformationRule rule, String id) { return rule.bindings().stream().anyMatch(item -> item.id().equals(id)); }

    private record Located(TransformationRule rule, TransformationBinding binding, MetaAttribute sourceAttribute, MetaAttribute targetAttribute) { }
}
