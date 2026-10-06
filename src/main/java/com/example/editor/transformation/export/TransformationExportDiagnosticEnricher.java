package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;

/** Adds element-specific evidence and repair guidance to export diagnostics. */
final class TransformationExportDiagnosticEnricher {
    private static final Pattern RULE_INDEX = Pattern.compile("\\.rules\\[(\\d+)]");
    private static final Pattern GENERATION_INDEX = Pattern.compile("\\.generationRules\\[(\\d+)]");
    private static final Pattern BINDING_INDEX = Pattern.compile("\\.bindings\\[(\\d+)]");
    private static final int LIMIT = 6;

    /** Descripción: Inicializa una instancia de TransformationExportDiagnosticEnricher con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TransformationExportDiagnosticEnricher() { }

    /** Descripción: Ejecuta la operación «enrich» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: result: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static TransformationExportResult enrich(TransformationExportResult result, TransformationDefinitionDocument document,
            MetaModel source, MetaModel target, TransformationExecutionDirection direction) {
        if(result == null || document == null || result.diagnostics().isEmpty()) return result;
        List<TransformationExportDiagnostic> diagnostics = result.diagnostics().stream()
                .map(item -> enrichOne(item, document, source, target, direction)).toList();
        return new TransformationExportResult(result.transformationId(), result.language(), result.fidelity(), result.compatible(), diagnostics, result.artifacts());
    }

    /** Descripción: Ejecuta la operación «enrichOne» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: d: dato de entrada necesario para la operación; document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationExportDiagnostic enrichOne(TransformationExportDiagnostic d, TransformationDefinitionDocument document,
            MetaModel source, MetaModel target, TransformationExecutionDirection direction) {
        TransformationRule rule = rule(document, d.path());
        TransformationBinding binding = binding(rule, d.path());
        TransformationGenerationRule generation = generation(document, d.path());
        TransformationTargetPattern pattern = rule == null || rule.targets().isEmpty() ? null : rule.targets().getFirst();
        MetaClass sourceClass = rule == null ? null : clazz(source, rule.sourceTypeId());
        MetaClass targetClass = pattern == null ? null : clazz(target, pattern.typeId());

        return switch(d.code()) {
            case "DIRECTION_NOT_SUPPORTED" -> text(d,
                    "Cannot export " + document.kind() + " in " + direction + " direction. Only M2M has explicit reverse semantics.",
                    "Select FORWARD. If you need reverse model reconstruction, model the requirement as a BIDIRECTIONAL M2M transformation with explicit REVERSE rules; M2T/T2M cannot infer an inverse safely.");
            case "REVERSE_REQUIRES_BIDIRECTIONAL" -> text(d,
                    "Reverse export was requested for transformation '" + visible(document) + "', but directionality is " + document.directionality() + ". No explicit reverse branch exists to export.",
                    "Change the transformation to BIDIRECTIONAL and define paired FORWARD/REVERSE rules with shared correspondence IDs, then export REVERSE again. Do not reverse FORWARD bindings automatically.");
            case "UNSUPPORTED_KIND" -> text(d,
                    "The selected export adapter cannot represent transformation kind " + document.kind() + " for '" + visible(document) + "'.",
                    "Choose an export language whose adapter declares support for " + document.kind() + ", or use the canonical HYDRA bundle to preserve the complete definition without semantic loss.");
            case "BIDIRECTIONAL_NOT_NATIVE" -> text(d,
                    "Transformation '" + visible(document) + "' is bidirectional, but this declarative native adapter cannot preserve its paired correspondence/trace synchronization contract.",
                    "Export one explicit direction only through a direction-capable adapter, or use the canonical runtime bundle. Do not merge FORWARD and REVERSE rules into one native rule unless the target engine can preserve correspondence IDs and trace semantics.");
            case "METAMODELS_REQUIRED" -> text(d,
                    "Native export needs both metamodels, but source=" + model(source) + " and target=" + model(target) + ". The adapter cannot resolve rule types/features without both semantic contracts.",
                    "Re-select the missing endpoint metamodel in the transformation and run Check first. Export only after both endpoint IDs resolve to actual metamodels.");
            case "SOURCE_METAMODEL_REQUIRED" -> text(d,
                    "Native M2T export cannot resolve the source metamodel for transformation '" + visible(document) + "'. Generation context types and {{attr:...}} tokens therefore cannot be validated.",
                    "Select a resolvable SOURCE metamodel, run Check, then export again. Keep the generation rule contextTypeId and template attribute IDs aligned with that metamodel.");
            case "GUARD_NOT_NATIVE" -> text(d,
                    "Rule '" + ruleName(rule, generation) + "' has a guard at " + d.path() + ", but the selected native subset does not execute that guard.",
                    "To preserve semantics, either remove the guard only if the rule is truly unconditional, or use the canonical runtime / an adapter that supports expression guards. Do not export and silently ignore the condition.");
            case "MULTI_TARGET_NOT_NATIVE" -> text(d,
                    "Rule '" + ruleName(rule, null) + "' creates " + (rule == null ? 0 : rule.targets().size()) + " target objects " + targetList(rule) + ", while this native subset supports exactly one target allocation per rule.",
                    "Split the rule into one rule per target object only when that preserves allocation/reference semantics, or use the canonical runtime. If targets depend on each other through trace/references, keep the canonical rule intact.");
            case "RULE_TYPE_NOT_FOUND" -> text(d,
                    "Rule '" + ruleName(rule, null) + "' references source type '" + (rule == null ? "<unknown>" : rule.sourceTypeId()) + "' and target type '" + (pattern == null ? "<unknown>" : pattern.typeId()) + "', but at least one class no longer exists in the resolved metamodels.",
                    "Repair the rule in HYDRA before exporting. Source classes include " + classes(source) + "; target classes include " + classes(target) + ". Then review bindings because feature IDs may also have changed.");
            case "BINDING_NOT_NATIVE" -> text(d,
                    "Binding '" + bindingName(binding) + "' in rule '" + ruleName(rule, null) + "' uses kind " + (binding == null ? "<unknown>" : binding.kind()) + ", but this native subset supports only DIRECT and CONSTANT attribute bindings.",
                    "Use the canonical runtime for EXPRESSION/TRACE_EQUIVALENT semantics. If the mapping is actually a simple copy, change it to DIRECT and choose an explicit source attribute; do not simplify a relation/expression merely to make export pass.");
            case "VALUE_POLICY_NOT_NATIVE" -> text(d,
                    "Binding '" + bindingName(binding) + "' uses a non-default value policy (collection=" + policyCollection(binding) + ", conversion=" + policyConversion(binding) + "). The selected native backend cannot reproduce it exactly.",
                    "Use the canonical runtime, or remove the policy only if source/target type and multiplicity are already exactly compatible. For conversions, enum mappings, reducers or fallbacks, keep the policy and choose an adapter/runtime that implements it.");
            case "BINDING_FEATURE_NOT_FOUND" -> missingFeature(d, rule, binding, sourceClass, targetClass);
            case "DIRECT_BINDING_NOT_EXACT" -> directNotExact(d, rule, binding, sourceClass, targetClass);
            case "CONSTANT_COLLECTION_NOT_NATIVE" -> text(d,
                    "Constant binding '" + bindingName(binding) + "' writes target feature '" + targetAttributeName(targetClass, binding) + "' with multiplicity " + targetMultiplicity(targetClass, binding) + ", but this native backend only supports scalar constant assignments.",
                    "Use the canonical runtime for collection constants, or remodel the target as scalar only if that is correct for the domain. Do not drop collection values during export.");
            case "CONTEXT_TYPE_NOT_FOUND", "GENERATION_CONTEXT_NOT_FOUND" -> text(d,
                    "Generation rule '" + ruleName(null, generation) + "' references contextTypeId='" + (generation == null ? "<unknown>" : generation.contextTypeId()) + "', which does not exist in source metamodel " + model(source) + ".",
                    "Select an existing source class: " + classes(source) + ". Then revalidate every {{attr:...}} token because attribute IDs are scoped to the selected context class.");
            case "OUTPUT_PATH_REQUIRED" -> text(d,
                    "Generation rule '" + ruleName(null, generation) + "' has an empty outputPathTemplate, so the native runtime cannot determine where to write each generated artifact.",
                    "Set a safe relative path such as 'generated/{{id}}.txt' or 'src/generated/{{attr:name}}.java'. Avoid absolute paths and '..' segments.");
            case "CANONICAL_ID_TOKEN_NOT_NATIVE" -> text(d,
                    "Template of generation rule '" + ruleName(null, generation) + "' uses canonical token at " + d.path() + " that the target template engine cannot obtain as a portable model property.",
                    "Map an explicit model attribute (for example {{attr:name}} or another stable identifier attribute), or use the canonical runtime where {{id}}/{{classId}} are defined metadata tokens.");
            case "TEMPLATE_ATTRIBUTE_NOT_FOUND" -> text(d,
                    d.message() + " Generation rule '" + ruleName(null, generation) + "' uses context type '" + (generation == null ? "<unknown>" : generation.contextTypeId()) + "'.",
                    "Replace the token with an attribute that exists on the context class. Available attributes: " + attributes(source, generation) + ". Use the stable attribute ID inside {{attr:ATTRIBUTE_ID}}.");
            case "CANONICAL_SERIALIZATION_FAILED" -> text(d,
                    "HYDRA could not serialize the canonical transformation bundle for '" + visible(document) + "'. The serializer reported: " + safe(d.message()) + ". No bundle was emitted, so there is no safe artifact to execute.",
                    "Run Check and correct the first reported definition error before exporting again. If Check is clean, retry once and inspect the server exception using transformation ID '" + document.id() + "'. Keep the canonical document unchanged and report the serializer message; do not remove fields merely to make JSON generation succeed.");
            case "NATIVE_PLAN_SERIALIZATION_FAILED" -> text(d,
                    "HYDRA validated the transformation but could not serialize the managed native execution plan for '" + visible(document) + "'. This is an internal export boundary failure, not a request to ignore transformation semantics.",
                    "Re-run Check and retry once. If it persists, inspect the server log for the serialization exception and report the transformation ID '" + document.id() + "' plus path " + d.path() + "; do not hand-edit native-plan.json.");
            case "NATIVE_COLLISION_POLICY_REQUIRES_ENGINE_GUARD" -> text(d,
                    "The generated template project needs orchestration that preserves each generation rule's FAIL/REPLACE collision policy; the template language alone does not enforce HYDRA artifact collisions.",
                    "Keep native parity tests enabled and enforce the same output-path collision policy in the external engine wrapper. A duplicate path with FAIL must stop generation; REPLACE must deterministically keep the last stable result.");
            default -> d;
        };
    }

    /** Descripción: Ejecuta la operación «missingFeature» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationExportDiagnostic missingFeature(TransformationExportDiagnostic d, TransformationRule rule,
            TransformationBinding binding, MetaClass sourceClass, MetaClass targetClass) {
        MetaAttribute sourceAttribute = attr(sourceClass, binding == null ? "" : binding.sourceFeatureId());
        MetaAttribute targetAttribute = attr(targetClass, binding == null ? "" : binding.targetFeatureId());
        String missing = sourceAttribute == null && binding != null && binding.kind().name().equals("DIRECT")
                ? "source attribute '" + binding.sourceFeatureId() + "' on class '" + className(sourceClass) + "'"
                : "target attribute '" + (binding == null ? "<unknown>" : binding.targetFeatureId()) + "' on class '" + className(targetClass) + "'";
        return text(d,
                "Binding '" + bindingName(binding) + "' cannot be exported because " + missing + " does not exist.",
                "Repair the mapping in HYDRA instead of editing generated code. Source attributes: " + attributeList(sourceClass) + "; target attributes: " + attributeList(targetClass) + ". Then run Check and export again.");
    }

    /** Descripción: Ejecuta la operación «directNotExact» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationExportDiagnostic directNotExact(TransformationExportDiagnostic d, TransformationRule rule,
            TransformationBinding binding, MetaClass sourceClass, MetaClass targetClass) {
        MetaAttribute source = attr(sourceClass, binding == null ? "" : binding.sourceFeatureId());
        MetaAttribute target = attr(targetClass, binding == null ? "" : binding.targetFeatureId());
        return text(d,
                "Direct binding '" + bindingName(binding) + "' maps " + attrName(source) + " [" + type(source) + ", " + mult(source) + "] to " + attrName(target) + " [" + type(target) + ", " + mult(target) + "], but this native subset requires exact type and multiplicity equality.",
                "Declare the required conversion/reduction explicitly in HYDRA and use the canonical runtime, or change the metamodel mapping only if source and target are genuinely equivalent. Native export will not invent a cast or cardinality reduction.");
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: original: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationExportDiagnostic text(TransformationExportDiagnostic original, String message, String solution) {
        return new TransformationExportDiagnostic(original.severity(), original.code(), original.path(), message, solution);
    }

    /** Descripción: Ejecuta la operación «rule» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: document: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationRule rule(TransformationDefinitionDocument document, String path) {
        int index = index(RULE_INDEX, path);
        return index >= 0 && index < document.rules().size() ? document.rules().get(index) : null;
    }
    /** Descripción: Ejecuta la operación «generation» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: document: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationGenerationRule generation(TransformationDefinitionDocument document, String path) {
        int index = index(GENERATION_INDEX, path);
        return index >= 0 && index < document.generationRules().size() ? document.generationRules().get(index) : null;
    }
    /** Descripción: Ejecuta la operación «binding» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: rule: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationBinding binding(TransformationRule rule, String path) {
        if(rule == null) return null;
        int index = index(BINDING_INDEX, path);
        return index >= 0 && index < rule.bindings().size() ? rule.bindings().get(index) : null;
    }
    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: pattern: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int index(Pattern pattern, String path) {
        if(path == null) return -1;
        Matcher matcher = pattern.matcher(path);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
    /** Descripción: Ejecuta la operación «clazz» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: model: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaClass clazz(MetaModel model, String id) { return model == null ? null : model.classes().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null); }
    /** Descripción: Ejecuta la operación «attr» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: owner: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaAttribute attr(MetaClass owner, String id) { return owner == null ? null : owner.attributes().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null); }

    /** Descripción: Ejecuta la operación «visible» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String visible(TransformationDefinitionDocument document) { return document.name().isBlank() ? document.id() : document.name() + " (id '" + document.id() + "')"; }
    /** Descripción: Ejecuta la operación «ruleName» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: rule: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String ruleName(TransformationRule rule, TransformationGenerationRule generation) {
        if(rule != null) return rule.name().isBlank() ? rule.id() : rule.name() + " (id '" + rule.id() + "')";
        if(generation != null) return generation.name().isBlank() ? generation.id() : generation.name() + " (id '" + generation.id() + "')";
        return "<unknown rule>";
    }
    /** Descripción: Ejecuta la operación «bindingName» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String bindingName(TransformationBinding binding) { return binding == null ? "<unknown binding>" : binding.id(); }
    /** Descripción: Ejecuta la operación «model» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String model(MetaModel model) { return model == null ? "<not resolved>" : "'" + (model.name().isBlank() ? model.id() : model.name()) + "' [" + model.id() + "]"; }
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String className(MetaClass value) { return value == null ? "<unresolved>" : (value.name().isBlank() ? value.id() : value.name()) + " [" + value.id() + "]"; }
    /** Descripción: Ejecuta la operación «attrName» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String attrName(MetaAttribute value) { return value == null ? "<unresolved attribute>" : (value.name().isBlank() ? value.id() : value.name()) + " [" + value.id() + "]"; }
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String type(MetaAttribute value) { return value == null ? "?" : type(value.type()); }
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String type(MetaTypeReference value) { if(value == null) return "?"; if(!value.targetId().isBlank()) return value.targetId(); if(!value.externalName().isBlank()) return value.externalName(); return value.kind().name(); }
    /** Descripción: Ejecuta la operación «mult» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String mult(MetaAttribute value) { return value == null ? "?" : mult(value.multiplicity()); }
    /** Descripción: Ejecuta la operación «mult» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String mult(Multiplicity value) { return value == null ? "unspecified" : value.lowerBound() + ".." + (value.upperBound() == null ? "*" : value.upperBound()); }
    /** Descripción: Ejecuta la operación «targetAttributeName» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: owner: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String targetAttributeName(MetaClass owner, TransformationBinding binding) { return attrName(attr(owner, binding == null ? "" : binding.targetFeatureId())); }
    /** Descripción: Ejecuta la operación «targetMultiplicity» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: owner: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String targetMultiplicity(MetaClass owner, TransformationBinding binding) { return mult(attr(owner, binding == null ? "" : binding.targetFeatureId())); }
    /** Descripción: Ejecuta la operación «policyCollection» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String policyCollection(TransformationBinding binding) { return binding == null ? "?" : binding.valuePolicy().collectionMode().name(); }
    /** Descripción: Ejecuta la operación «policyConversion» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String policyConversion(TransformationBinding binding) { return binding == null ? "?" : binding.valuePolicy().conversionMode().name(); }

    /** Descripción: Ejecuta la operación «classes» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String classes(MetaModel model) {
        if(model == null || model.classes().isEmpty()) return "<none>";
        return list(model.classes().stream().map(item -> className(item)).toList());
    }
    /** Descripción: Ejecuta la operación «attributeList» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String attributeList(MetaClass owner) {
        if(owner == null || owner.attributes().isEmpty()) return "<none>";
        return list(owner.attributes().stream().map(TransformationExportDiagnosticEnricher::attrName).toList());
    }
    /** Descripción: Ejecuta la operación «targetList» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String targetList(TransformationRule rule) {
        if(rule == null || rule.targets().isEmpty()) return "<none>";
        return list(rule.targets().stream().map(item -> item.alias() + "→" + item.typeId()).toList());
    }
    /** Descripción: Ejecuta la operación «attributes» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: source: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String attributes(MetaModel source, TransformationGenerationRule generation) {
        if(source == null || generation == null) return "<none>";
        MetaClass context = clazz(source, generation.contextTypeId());
        return attributeList(context);
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de TransformationExportDiagnosticEnricher. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null || value.isBlank() ? "<no serializer detail>" : value; }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de TransformationExportDiagnosticEnricher. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String list(List<String> values) {
        List<String> clean = values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if(clean.isEmpty()) return "<none>";
        String joined = clean.stream().limit(LIMIT).map(value -> "'" + value + "'").collect(Collectors.joining(", "));
        return clean.size() > LIMIT ? joined + " (and " + (clean.size() - LIMIT) + " more)" : joined;
    }
}
