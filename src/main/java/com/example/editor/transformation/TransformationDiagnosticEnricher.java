package com.example.editor.transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationTargetPattern;

/**
 * Turns structural transformation diagnostics into actionable, case-specific
 * explanations without changing their stable codes or severity.
 */
final class TransformationDiagnosticEnricher {
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");
    private static final int LIST_LIMIT = 6;

    /** Descripción: Inicializa una instancia de TransformationDiagnosticEnricher con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TransformationDiagnosticEnricher() { }

    /** Descripción: Ejecuta la operación «enrich» correspondiente a la responsabilidad de Context. Entradas: document: dato de entrada necesario para la operación; sourceModels: dato de entrada necesario para la operación; targetModels: dato de entrada necesario para la operación; input: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static List<TransformationDiagnostic> enrich(TransformationDefinitionDocument document,
            List<MetaModel> sourceModels, List<MetaModel> targetModels, List<TransformationDiagnostic> input) {
        if(document == null || input.isEmpty()) return List.copyOf(input);
        Context context = new Context(document,
                sourceModels == null || sourceModels.isEmpty() ? null : sourceModels.get(0),
                targetModels == null || targetModels.isEmpty() ? null : targetModels.get(0));

        Set<String> rulesWithUnknownSourceType = input.stream()
                .filter(item -> "UNKNOWN_SOURCE_TYPE".equals(item.code()))
                .map(TransformationDiagnostic::ruleId).collect(Collectors.toSet());
        Set<String> rulesWithUnknownTargetType = input.stream()
                .filter(item -> "UNKNOWN_TARGET_TYPE".equals(item.code()))
                .map(TransformationDiagnostic::ruleId).collect(Collectors.toSet());

        List<TransformationDiagnostic> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for(TransformationDiagnostic diagnostic : input) {
            if(isCascade(diagnostic, rulesWithUnknownSourceType, rulesWithUnknownTargetType)) continue;
            TransformationDiagnostic enriched = enrichOne(context, diagnostic);
            String key = enriched.code() + "|" + enriched.ruleId() + "|" + enriched.bindingId() + "|" + enriched.path();
            if(seen.add(key)) result.add(enriched);
        }
        return List.copyOf(result);
    }

    /** Descripción: Evalúa la condición «isCascade» sin modificar el estado externo. Entradas: diagnostic: dato de entrada necesario para la operación; unknownSource: dato de entrada necesario para la operación; unknownTarget: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isCascade(TransformationDiagnostic diagnostic, Set<String> unknownSource, Set<String> unknownTarget) {
        if(unknownSource.contains(diagnostic.ruleId()) && Set.of("UNKNOWN_SOURCE_ATTRIBUTE", "UNKNOWN_SOURCE_RELATION").contains(diagnostic.code())) return true;
        return unknownTarget.contains(diagnostic.ruleId()) && Set.of("UNKNOWN_TARGET_ATTRIBUTE", "UNKNOWN_TARGET_RELATION", "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED").contains(diagnostic.code());
    }

    /** Descripción: Ejecuta la operación «enrichOne» correspondiente a la responsabilidad de Context. Entradas: ctx: dato de entrada necesario para la operación; d: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic enrichOne(Context ctx, TransformationDiagnostic d) {
        TransformationRule rule = ctx.rule(d.ruleId());
        if(rule == null) rule = ctx.ruleByPath(d.path());
        TransformationBinding binding = rule == null ? null : binding(rule, d.bindingId());
        if(binding == null && rule != null) binding = bindingByPath(rule, d.path());
        TransformationGenerationRule generation = ctx.generationRule(d.ruleId());
        if(generation == null) generation = ctx.generationRuleByPath(d.path());
        TransformationEndpoint endpoint = ctx.endpoint(d.path());
        TransformationTargetPattern pattern = rule == null ? null : targetPattern(rule, d.path(), binding);
        MetaModel inputModel = rule == null ? ctx.source : (rule.direction() == TransformationRuleDirection.REVERSE ? ctx.target : ctx.source);
        MetaModel outputModel = rule == null ? ctx.target : (rule.direction() == TransformationRuleDirection.REVERSE ? ctx.source : ctx.target);
        MetaClass sourceClass = rule == null ? null : findClass(inputModel, rule.sourceTypeId());
        MetaClass targetClass = pattern == null ? null : findClass(outputModel, pattern.typeId());

        return switch(d.code()) {
            case "UNSUPPORTED_SCHEMA_VERSION" -> text(d,
                    "Unsupported transformation schema '" + ctx.document.schemaVersion() + "'",
                    "Transformation '" + visibleTransformation(ctx.document) + "' uses schemaVersion='" + ctx.document.schemaVersion()
                            + "', while this HYDRA build accepts 1.0 and 1.1. Loading it as if it were compatible could change transformation semantics.",
                    "Migrate the document to schemaVersion='1.1' using HYDRA's current editor/export path. Do not only change the version string if the document came from a newer schema; review its rules, bindings and policies after migration.");
            case "TRANSFORMATION_ID_REQUIRED" -> text(d,
                    "Transformation has no stable ID",
                    "The transformation named '" + display(ctx.document.name(), "<unnamed>") + "' has an empty id. Rules, tests, traces and synchronization sessions need a stable transformation identifier.",
                    "Assign a unique ID such as 'transformation:" + slug(ctx.document.name(), "model-mapping") + "'. Keep that ID stable after the transformation is referenced by tests or synchronization sessions.");
            case "TRANSFORMATION_NAME_REQUIRED" -> text(d,
                    "Transformation '" + display(ctx.document.id(), "<no-id>") + "' has no visible name",
                    "The transformation can be stored technically, but users cannot distinguish it reliably in the catalogue, diagnostics or exported artifacts.",
                    "Set a descriptive name that states the intent, for example 'Person to Employee synchronization' or 'Domain model to Java sources'.");
            case "NON_DETERMINISTIC_EXECUTION_NOT_SUPPORTED" -> text(d,
                    "Deterministic execution is disabled but the runtime requires it",
                    "executionPolicy.deterministic=false for transformation '" + visibleTransformation(ctx.document) + "'. HYDRA currently guarantees stable rule order, artifact order and trace reproduction only in deterministic mode.",
                    "Set executionPolicy.deterministic=true. If you need parallel execution, keep the canonical definition deterministic and optimize the external runtime only when it can prove equivalent ordering and conflict semantics.");
            case "BEST_EFFORT_BINDING_EXECUTION_NOT_SUPPORTED" -> text(d,
                    "Binding failures cannot be ignored in the current runtime",
                    "executionPolicy.failOnBindingError=false requests best-effort continuation, but HYDRA's canonical runtime intentionally stops on binding, template or parse errors to avoid partially valid output.",
                    "Set executionPolicy.failOnBindingError=true. Handle optional values explicitly with value policies, guards or fallback values instead of suppressing binding failures globally.");
            case "BIDIRECTIONAL_ONLY_M2M" -> text(d,
                    "" + ctx.document.kind() + " cannot run in bidirectional mode",
                    "Transformation '" + visibleTransformation(ctx.document) + "' is " + ctx.document.kind() + " with directionality=BIDIRECTIONAL. Bidirectional correspondence and synchronization semantics are currently defined only for M2M.",
                    "Change directionality to UNIDIRECTIONAL. If the real requirement is two-way model synchronization, create an M2M transformation with explicit FORWARD and REVERSE rules sharing correspondence IDs.");
            case "BIDIRECTIONAL_TRACE_REQUIRED" -> text(d,
                    "Trace is disabled for a bidirectional transformation",
                    "Transformation '" + visibleTransformation(ctx.document) + "' is BIDIRECTIONAL but tracePolicy.enabled=false. Without trace links HYDRA cannot relate corresponding source/target elements safely during reverse or incremental synchronization.",
                    "Enable tracePolicy.enabled=true before executing the transformation. Keep trace data for synchronization runs; do not emulate bidirectionality by independently running two unrelated transformations.");
            case "SOURCE_REQUIRED" -> text(d,
                    "No source metamodel is configured",
                    "The " + ctx.document.kind() + " transformation '" + visibleTransformation(ctx.document) + "' has no source endpoint, so rules have no semantic model to read.",
                    "Select the metamodel that contains the input instances. Example: add a SOURCE endpoint with id='source', metamodelId='<your-source-metamodel-id>' and alias='source'.");
            case "TARGET_REQUIRED" -> text(d,
                    "No target metamodel is configured",
                    "M2M transformation '" + visibleTransformation(ctx.document) + "' has no target endpoint, so its rules cannot create output model elements.",
                    "Select the destination metamodel. Example: add a TARGET endpoint with id='target', metamodelId='<your-target-metamodel-id>' and alias='target'.");
            case "GENERATION_RULE_REQUIRED" -> text(d,
                    "M2T transformation has no generation rule",
                    "Transformation '" + visibleTransformation(ctx.document) + "' has zero generation rules, therefore it cannot decide which model elements generate files or what content to write.",
                    "Add a generation rule. Choose a source context class, a relative output path such as 'generated/{{attr:name}}.txt', and a content template such as 'name={{attr:name}}'.");
            case "GRAMMAR_REQUIRED" -> text(d,
                    "T2M transformation has no compiled grammar",
                    "Transformation '" + visibleTransformation(ctx.document) + "' does not reference a grammar ID with an active compilation snapshot, so input text cannot be parsed into the canonical model.",
                    "Select the grammar used by the input language, compile it successfully, activate that snapshot, and then assign its grammarId to textToModel.grammarId.");

            case "ENDPOINT_ID_REQUIRED" -> text(d,
                    "Endpoint has no stable ID",
                    "The " + endpointRole(endpoint, d.path()) + " endpoint at " + d.path() + " has an empty id. Rules refer to endpoints by this value.",
                    "Assign a unique endpoint ID such as 'source' or 'target'. If rules already exist, update their sourceEndpointId/endpointId references to the same value.");
            case "DUPLICATE_ENDPOINT_ID" -> text(d,
                    "Endpoint ID '" + value(endpoint == null ? "" : endpoint.id()) + "' is duplicated",
                    "More than one endpoint uses id='" + value(endpoint == null ? "" : endpoint.id()) + "'. HYDRA cannot resolve a rule reference unambiguously.",
                    "Keep this ID on one endpoint and rename the other, for example to '" + value(endpoint == null ? "endpoint-2" : endpoint.id() + "-2") + "'. Then update rules that should refer to the renamed endpoint.");
            case "ENDPOINT_ROLE_MISMATCH" -> text(d,
                    "Endpoint '" + endpointName(endpoint) + "' is stored on the wrong side",
                    "Endpoint role is " + (endpoint == null ? "unknown" : endpoint.role()) + " at path " + d.path() + ", which belongs to the " + expectedSide(d.path()) + " endpoint collection.",
                    "Move the endpoint to $." + expectedSide(d.path()).toLowerCase(Locale.ROOT) + "s or change its role to " + expectedSide(d.path()) + ". Do not leave role and collection disagreeing because directional rules resolve them differently.");
            case "ENDPOINT_METAMODEL_REQUIRED" -> text(d,
                    "Endpoint '" + endpointName(endpoint) + "' has no metamodel",
                    "The endpoint exists but metamodelId is empty, so HYDRA cannot resolve its classes, attributes or relations.",
                    "Choose a metamodel from the catalogue for endpoint '" + endpointName(endpoint) + "'. After selecting it, re-run Check so stale rule/type references are reported against the actual metamodel.");
            case "ENDPOINT_METAMODEL_MISMATCH" -> text(d,
                    "Endpoint '" + endpointName(endpoint) + "' resolves to a different metamodel",
                    "The endpoint declares metamodelId='" + (endpoint == null ? "" : endpoint.metamodelId()) + "' but the semantic context loaded for this position is '" + resolvedModelId(ctx, d.path()) + "'. This usually means the endpoint order or persisted reference is stale.",
                    "Re-select the metamodel for this endpoint from the catalogue. Do not copy an ID manually. Then review every rule attached to this side because class/feature IDs may have changed.");

            case "GENERATION_RULE_ID_REQUIRED_OR_DUPLICATED" -> text(d,
                    generation == null || generation.id().isBlank() ? "Generation rule has no ID" : "Generation rule ID '" + generation.id() + "' is duplicated",
                    generation == null || generation.id().isBlank()
                            ? "Generation rule at " + parentPath(d.path()) + " cannot be referenced stably because its id is empty."
                            : "At least two M2T generation rules use id='" + generation.id() + "', making diagnostics and generated evidence ambiguous.",
                    generation == null || generation.id().isBlank()
                            ? "Assign an ID such as 'generate:" + slug(generation == null ? "artifact" : generation.name(), "artifact") + "'."
                            : "Rename this rule, for example to '" + generation.id() + "-2', and keep rule IDs unique within the transformation.");
            case "UNKNOWN_SOURCE_ENDPOINT" -> text(d,
                    "Rule references unknown input endpoint '" + sourceEndpointId(rule, generation) + "'",
                    sourceEndpointReason(ctx, rule, generation),
                    "Choose one of the configured input endpoint IDs: " + availableInputEndpoints(ctx, rule) + ". Update sourceEndpointId on '" + ruleOrGenerationName(rule, generation) + "' and run Check again.");
            case "UNKNOWN_GENERATION_CONTEXT" -> text(d,
                    "Generation context type '" + value(generation == null ? "" : generation.contextTypeId()) + "' does not exist",
                    "Generation rule '" + ruleOrGenerationName(null, generation) + "' references contextTypeId='" + value(generation == null ? "" : generation.contextTypeId()) + "', but that class is not present in source metamodel '" + modelName(ctx.source) + "'.",
                    "Select an existing source class. Available classes include: " + classList(ctx.source) + ". Then review {{attr:...}} tokens because attributes belong to the selected context class.");
            case "OUTPUT_PATH_REQUIRED" -> text(d,
                    "Generation rule '" + ruleOrGenerationName(null, generation) + "' has no output path",
                    "outputPathTemplate is empty, so HYDRA cannot determine the relative file path for each instance of context type '" + value(generation == null ? "" : generation.contextTypeId()) + "'.",
                    "Provide a relative path. Example for this rule: 'generated/{{id}}.txt'. If the context has a stable name attribute, you can use 'generated/{{attr:name}}.txt'.");
            case "UNSAFE_OUTPUT_PATH" -> text(d,
                    "Output path '" + value(generation == null ? "" : generation.outputPathTemplate()) + "' is unsafe",
                    "Generation rule '" + ruleOrGenerationName(null, generation) + "' uses an absolute path, parent traversal ('..') or drive-qualified path. That could write outside the generated bundle.",
                    "Use a portable relative path with no '..' segments. Example: 'generated/{{id}}.txt' or 'src/generated/{{attr:name}}.java'.");

            case "RULE_ID_REQUIRED" -> text(d,
                    "Rule has no stable ID",
                    "Rule '" + visibleRule(rule) + "' at " + parentPath(d.path()) + " has an empty id. Traces, diagnostics, tests and correspondence links require a stable rule ID.",
                    "Assign an ID such as 'rule:" + slug(rule == null ? "mapping" : rule.name(), "mapping") + "'. Keep it stable after traces or test cases have been created.");
            case "DUPLICATE_RULE_ID" -> text(d,
                    "Rule ID '" + value(rule == null ? "" : rule.id()) + "' is duplicated",
                    "More than one transformation rule uses id='" + value(rule == null ? "" : rule.id()) + "'. Trace links and quick fixes would no longer identify a single rule.",
                    "Rename this duplicate rule, for example to '" + value(rule == null ? "rule-2" : rule.id() + "-2") + "'. Do not rename the already referenced rule unless you also migrate its tests/traces.");
            case "RULE_NAME_REQUIRED" -> text(d,
                    "Rule '" + value(rule == null ? "" : rule.id()) + "' has no readable name",
                    "The rule has a technical ID but no visible name, making diagnostics and correspondence review difficult.",
                    "Set a name that explains the mapping, for example '" + sourceType(rule) + " to " + firstTargetType(rule) + "'.");
            case "UNKNOWN_SOURCE_TYPE" -> text(d,
                    "Input type '" + sourceType(rule) + "' does not exist for rule '" + visibleRule(rule) + "'",
                    "Rule '" + visibleRule(rule) + "' is " + direction(rule) + " and reads sourceTypeId='" + sourceType(rule) + "', but that class is not present in its input metamodel '" + modelName(inputModel) + "'. Dependent binding errors are suppressed until this type is repaired.",
                    "Select an existing input class. Available classes include: " + classList(inputModel) + ". After changing the class, review all bindings because their source feature IDs belong to the old type.");
            case "REVERSE_RULE_REQUIRES_BIDIRECTIONAL", "BIDIRECTIONAL_RULE_PAIR_REQUIRED", "CORRESPONDENCE_ID_REQUIRED", "INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE" -> d;
            case "GUARD_CONTEXT_MISMATCH" -> text(d,
                    "Condition context does not match rule '" + visibleRule(rule) + "'",
                    "The guard declares contextClassId='" + (rule == null || rule.guard() == null ? "" : rule.guard().contextClassId()) + "' but the rule reads sourceTypeId='" + sourceType(rule) + "'. Evaluating the condition against a different class would make its feature references unsafe.",
                    "Set guard.contextClassId='" + sourceType(rule) + "'. Then re-open the condition and confirm every referenced attribute belongs to that class.");
            case "GUARD_MUST_BE_BOOLEAN" -> text(d,
                    "Condition of rule '" + visibleRule(rule) + "' does not return Boolean",
                    "A rule guard decides whether the rule runs, so it must evaluate to true or false. The current expression inferred a non-Boolean result.",
                    "Change the guard into a comparison or Boolean expression. Example: compare an attribute with a value (such as active == true) instead of returning the attribute/value directly.");
            case "UNKNOWN_TARGET_ENDPOINT" -> text(d,
                    "Target pattern of rule '" + visibleRule(rule) + "' references unknown endpoint '" + value(pattern == null ? "" : pattern.endpointId()) + "'",
                    "The pattern alias '" + value(pattern == null ? "" : pattern.alias()) + "' points to endpointId='" + value(pattern == null ? "" : pattern.endpointId()) + "', which is not configured on the output side of this " + direction(rule) + " rule.",
                    "Choose one of the valid output endpoint IDs: " + outputEndpointList(ctx, rule) + ". Keep the target alias unchanged if bindings already reference it.");
            case "TARGET_ALIAS_REQUIRED_OR_DUPLICATED" -> text(d,
                    pattern == null || pattern.alias().isBlank() ? "Target object has no alias" : "Target alias '" + pattern.alias() + "' is duplicated in rule '" + visibleRule(rule) + "'",
                    pattern == null || pattern.alias().isBlank()
                            ? "Bindings address each created object by alias, but this target pattern has an empty alias."
                            : "Two target patterns in rule '" + visibleRule(rule) + "' use alias='" + pattern.alias() + "', so a binding cannot identify which object should receive the value.",
                    "Use a unique alias such as '" + slug(pattern == null ? firstTargetType(rule) : pattern.typeId(), "target") + "'. If you rename an existing alias, update each binding.targetAlias that points to it.");
            case "UNKNOWN_TARGET_TYPE" -> text(d,
                    "Target type '" + value(pattern == null ? "" : pattern.typeId()) + "' does not exist for rule '" + visibleRule(rule) + "'",
                    "Target alias '" + value(pattern == null ? "" : pattern.alias()) + "' creates typeId='" + value(pattern == null ? "" : pattern.typeId()) + "', but that class is not present in output metamodel '" + modelName(outputModel) + "'. Dependent target-feature diagnostics are suppressed until this type is repaired.",
                    "Select an existing output class. Available classes include: " + classList(outputModel) + ". Then review bindings for alias '" + value(pattern == null ? "" : pattern.alias()) + "' because target feature IDs may have changed.");
            case "RULE_TARGET_REQUIRED" -> text(d,
                    "Rule '" + visibleRule(rule) + "' creates no target object",
                    "The rule reads input type '" + sourceType(rule) + "' but targets[] is empty. A model-to-model rule must allocate at least one output object before bindings can write values.",
                    "Add a target pattern with an output endpoint, an existing target type and a unique alias. Example: alias='target', typeId='<target-class-id>'.");

            case "BINDING_ID_REQUIRED_OR_DUPLICATED" -> text(d,
                    binding == null || binding.id().isBlank() ? "Binding in rule '" + visibleRule(rule) + "' has no ID" : "Binding ID '" + binding.id() + "' is duplicated",
                    binding == null || binding.id().isBlank()
                            ? "A binding that writes target alias '" + value(binding == null ? "" : binding.targetAlias()) + "' has an empty id, so diagnostics and quick fixes cannot reference it stably."
                            : "More than one binding in rule '" + visibleRule(rule) + "' uses id='" + binding.id() + "'.",
                    binding == null || binding.id().isBlank()
                            ? "Assign a unique ID such as 'binding:" + slug(value(binding == null ? "mapping" : binding.targetFeatureId()), "mapping") + "'."
                            : "Rename this binding, for example to '" + binding.id() + "-2', and keep binding IDs unique inside the rule.");
            case "UNKNOWN_TARGET_ALIAS" -> text(d,
                    "Binding '" + bindingName(binding) + "' references target alias '" + value(binding == null ? "" : binding.targetAlias()) + "' that the rule does not create",
                    "Rule '" + visibleRule(rule) + "' creates target aliases " + targetAliasList(rule) + ", but binding '" + bindingName(binding) + "' uses targetAlias='" + value(binding == null ? "" : binding.targetAlias()) + "'.",
                    "Set binding.targetAlias to one of " + targetAliasList(rule) + ". If the intended object is missing, add its target pattern first and then point the binding to that alias.");
            case "DUPLICATE_TARGET_BINDING" -> text(d,
                    "Target feature '" + targetFeatureName(targetClass, binding) + "' is written more than once",
                    "In rule '" + visibleRule(rule) + "', multiple bindings write alias '" + value(binding == null ? "" : binding.targetAlias()) + "' feature '" + targetFeatureName(targetClass, binding) + "'. The final value would depend on binding order.",
                    "Keep one authoritative binding for this feature. Merge the logic into one EXPRESSION binding if several source values are needed, or remove the obsolete duplicate mapping.");
            case "UNKNOWN_SOURCE_ATTRIBUTE" -> text(d,
                    "Source attribute '" + value(binding == null ? "" : binding.sourceFeatureId()) + "' does not exist on '" + className(sourceClass) + "'",
                    "Direct binding '" + bindingName(binding) + "' in rule '" + visibleRule(rule) + "' reads sourceFeatureId='" + value(binding == null ? "" : binding.sourceFeatureId()) + "', but class '" + className(sourceClass) + "' has attributes " + attributeList(sourceClass) + ".",
                    "Select an existing source attribute. A likely replacement must come from class '" + className(sourceClass) + "'; available IDs include " + attributeIdList(sourceClass) + ". If the old attribute was renamed, update this binding rather than recreating a duplicate attribute.");
            case "UNKNOWN_TARGET_ATTRIBUTE" -> text(d,
                    "Target attribute '" + value(binding == null ? "" : binding.targetFeatureId()) + "' does not exist on '" + className(targetClass) + "'",
                    "Binding '" + bindingName(binding) + "' writes targetFeatureId='" + value(binding == null ? "" : binding.targetFeatureId()) + "' on target alias '" + value(binding == null ? "" : binding.targetAlias()) + "' of class '" + className(targetClass) + "', but that attribute is absent. Available attributes: " + attributeList(targetClass) + ".",
                    "Choose an existing target attribute ID from " + attributeIdList(targetClass) + ". If the metamodel changed, remap the binding to the replacement attribute and re-check its type/cardinality policy.");
            case "TYPE_CONVERSION_REQUIRED" -> typeConversion(d, rule, binding, sourceClass, targetClass);
            case "COLLECTION_REDUCTION_REQUIRED" -> collectionReduction(d, rule, binding, sourceClass, targetClass);
            case "EXPLICIT_VALUE_CONVERSION" -> explicitConversion(d, rule, binding, sourceClass, targetClass);
            case "BINDING_EXPRESSION_REQUIRED" -> text(d,
                    "Expression binding '" + bindingName(binding) + "' has no expression",
                    "Binding kind is EXPRESSION but expression is null, so target feature '" + targetFeatureName(targetClass, binding) + "' has no calculation to execute.",
                    "Define an expression using the source class '" + className(sourceClass) + "' as context. For a simple copy, change the binding kind to DIRECT and select a source attribute instead.");
            case "BINDING_EXPRESSION_CONTEXT_MISMATCH" -> text(d,
                    "Expression context of binding '" + bindingName(binding) + "' does not match its rule",
                    "The expression declares contextClassId='" + (binding == null || binding.expression() == null ? "" : binding.expression().contextClassId()) + "' while rule '" + visibleRule(rule) + "' reads sourceTypeId='" + sourceType(rule) + "'.",
                    "Set expression.contextClassId='" + sourceType(rule) + "' and review the expression's feature references against class '" + className(sourceClass) + "'.");
            case "UNKNOWN_SOURCE_RELATION" -> text(d,
                    "Source relation '" + value(binding == null ? "" : binding.sourceFeatureId()) + "' cannot be navigated from '" + className(sourceClass) + "'",
                    "Trace-equivalent binding '" + bindingName(binding) + "' references source relation ID '" + value(binding == null ? "" : binding.sourceFeatureId()) + "'. Relations available from the rule source type include " + relationList(inputModel, sourceClass) + ".",
                    "Choose a relation owned by source class '" + className(sourceClass) + "'. If you intended to copy an attribute, change the binding kind from TRACE_EQUIVALENT to DIRECT.");
            case "UNKNOWN_TARGET_RELATION" -> text(d,
                    "Target relation '" + value(binding == null ? "" : binding.targetFeatureId()) + "' does not exist on '" + className(targetClass) + "'",
                    "Trace-equivalent binding '" + bindingName(binding) + "' needs a target relation on alias '" + value(binding == null ? "" : binding.targetAlias()) + "', but ID '" + value(binding == null ? "" : binding.targetFeatureId()) + "' is not owned by target class '" + className(targetClass) + "'. Available relations include " + relationList(outputModel, targetClass) + ".",
                    "Select an existing relation owned by '" + className(targetClass) + "'. Verify that its target type corresponds to the objects resolved through transformation trace.");
            case "REQUIRED_TARGET_ATTRIBUTE_UNMAPPED" -> requiredUnmapped(d, rule, sourceClass, outputModel);
            default -> dynamicExpressionOrOriginal(d, rule, binding, generation);
        };
    }

    /** Descripción: Ejecuta la operación «typeConversion» correspondiente a la responsabilidad de Context. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic typeConversion(TransformationDiagnostic d, TransformationRule rule, TransformationBinding binding,
            MetaClass sourceClass, MetaClass targetClass) {
        MetaAttribute source = attr(sourceClass, binding == null ? "" : binding.sourceFeatureId());
        MetaAttribute target = attr(targetClass, binding == null ? "" : binding.targetFeatureId());
        return text(d,
                "Binding '" + bindingName(binding) + "' needs an explicit conversion from " + type(source) + " to " + type(target),
                "Rule '" + visibleRule(rule) + "' maps '" + attributeName(source) + "' (" + type(source) + ", " + multiplicity(source) + ") to '" + attributeName(target) + "' (" + type(target) + ", " + multiplicity(target) + "). These types are not directly compatible and valuePolicy.conversionMode is NONE with no enum mapping.",
                "Choose the conversion that matches the domain meaning. Examples: TO_STRING for textual representation, TO_INTEGER/TO_LONG/TO_DOUBLE for numeric conversion, an explicit enumMapping for enumerations, or an EXPRESSION binding for custom logic. Test at least one representative value and one invalid/empty value afterwards.");
    }

    /** Descripción: Ejecuta la operación «collectionReduction» correspondiente a la responsabilidad de Context. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic collectionReduction(TransformationDiagnostic d, TransformationRule rule, TransformationBinding binding,
            MetaClass sourceClass, MetaClass targetClass) {
        MetaAttribute source = attr(sourceClass, binding == null ? "" : binding.sourceFeatureId());
        MetaAttribute target = attr(targetClass, binding == null ? "" : binding.targetFeatureId());
        return text(d,
                "Binding '" + bindingName(binding) + "' maps collection " + multiplicity(source) + " to scalar " + multiplicity(target),
                "Source attribute '" + attributeName(source) + "' can contain multiple values (" + multiplicity(source) + "), while target attribute '" + attributeName(target) + "' accepts a single value (" + multiplicity(target) + "). PRESERVE would lose the target cardinality contract.",
                "Select an explicit reduction: FIRST for the first value, LAST for the last value, COUNT when the target stores the number of values, or use an EXPRESSION for domain-specific selection. Do not choose FIRST automatically unless element ordering has business meaning.");
    }

    /** Descripción: Ejecuta la operación «explicitConversion» correspondiente a la responsabilidad de Context. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic explicitConversion(TransformationDiagnostic d, TransformationRule rule, TransformationBinding binding,
            MetaClass sourceClass, MetaClass targetClass) {
        MetaAttribute source = attr(sourceClass, binding == null ? "" : binding.sourceFeatureId());
        MetaAttribute target = attr(targetClass, binding == null ? "" : binding.targetFeatureId());
        return text(d,
                "Binding '" + bindingName(binding) + "' applies conversion policy " + (binding == null ? "" : binding.valuePolicy().conversionMode()),
                "Rule '" + visibleRule(rule) + "' converts '" + attributeName(source) + "' (" + type(source) + ") before writing '" + attributeName(target) + "' (" + type(target) + "). This is valid but changes the source value representation.",
                "Keep the conversion only if it is intentional. Preview the transformation with representative values, including null/empty and boundary values; remove the conversion if source and target types can be mapped directly.");
    }

    /** Descripción: Valida las condiciones necesarias para «requiredUnmapped» y rechaza entradas no válidas. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; outputModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic requiredUnmapped(TransformationDiagnostic d, TransformationRule rule, MetaClass sourceClass, MetaModel outputModel) {
        String detail = d.message();
        String targetName = detail.contains(" requires a value") ? detail.substring(0, detail.indexOf(" requires a value")) : "required target attribute";
        String candidate = compatibleCandidate(sourceClass, targetName);
        return text(d,
                "Required target attribute '" + targetName + "' has no binding",
                "Rule '" + visibleRule(rule) + "' creates a target object whose required attribute '" + targetName + "' has lowerBound > 0 and no default value. The runtime can therefore create an invalid/incomplete target object.",
                candidate.isBlank()
                        ? "Add a binding for '" + targetName + "'. Use DIRECT if an equivalent source attribute exists, CONSTANT for a fixed domain value, or EXPRESSION when the value must be derived. Re-run Check before execution."
                        : "A possible source candidate is '" + candidate + "'. Review its semantics and type, then map it explicitly to '" + targetName + "'. If it is not semantically equivalent, use CONSTANT or EXPRESSION instead; HYDRA will not guess the mapping.");
    }

    /** Descripción: Ejecuta la operación «dynamicExpressionOrOriginal» correspondiente a la responsabilidad de Context. Entradas: d: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic dynamicExpressionOrOriginal(TransformationDiagnostic d, TransformationRule rule,
            TransformationBinding binding, TransformationGenerationRule generation) {
        if(d.code().startsWith("GUARD_")) return text(d,
                "Invalid condition in rule '" + visibleRule(rule) + "'",
                "The condition attached to rule '" + visibleRule(rule) + "' is invalid at " + d.path() + ". Expression checker detail: " + d.message(),
                "Open the condition of this rule, correct the expression at the reported path, and verify it against source type '" + sourceType(rule) + "'. Then run Check again.");
        if(d.code().startsWith("BINDING_") && binding != null) return text(d,
                "Invalid expression in binding '" + bindingName(binding) + "'",
                "Binding '" + bindingName(binding) + "' of rule '" + visibleRule(rule) + "' has an invalid expression at " + d.path() + ". Expression checker detail: " + d.message(),
                "Edit this binding expression using the rule source type '" + sourceType(rule) + "' as context. Correct the reported node/property and re-run Check before previewing the transformation.");
        if(d.code().startsWith("GENERATION_GUARD_")) return text(d,
                "Invalid generation condition in rule '" + ruleOrGenerationName(null, generation) + "'",
                "The generation condition is invalid at " + d.path() + ". Expression checker detail: " + d.message(),
                "Edit the generation rule condition against context type '" + value(generation == null ? "" : generation.contextTypeId()) + "', correct the reported expression node and run Check again before generating files.");
        return d;
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de Context. Entradas: original: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; reason: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationDiagnostic text(TransformationDiagnostic original, String title, String reason, String solution) {
        return new TransformationDiagnostic(original.severity(), original.code(), original.ruleId(), original.bindingId(), original.path(), title, reason, solution);
    }

    /** Descripción: Ejecuta la operación «binding» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationBinding binding(TransformationRule rule, String id) {
        if(rule == null) return null;
        return rule.bindings().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    /** Descripción: Ejecuta la operación «bindingByPath» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationBinding bindingByPath(TransformationRule rule, String path) {
        if(rule == null) return null;
        int index = indexed(path, ".bindings[");
        return index >= 0 && index < rule.bindings().size() ? rule.bindings().get(index) : null;
    }

    /** Descripción: Ejecuta la operación «targetPattern» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TransformationTargetPattern targetPattern(TransformationRule rule, String path, TransformationBinding binding) {
        if(rule == null) return null;
        if(binding != null) return rule.targets().stream().filter(item -> item.alias().equals(binding.targetAlias())).findFirst().orElse(null);
        int targetIndex = indexed(path, ".targets[");
        return targetIndex >= 0 && targetIndex < rule.targets().size() ? rule.targets().get(targetIndex) : null;
    }

    /** Descripción: Ejecuta la operación «indexed» correspondiente a la responsabilidad de Context. Entradas: path: ruta o ubicación utilizada por la operación; marker: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int indexed(String path, String marker) {
        if(path == null) return -1;
        int pos = path.indexOf(marker);
        if(pos < 0) return -1;
        Matcher matcher = INDEX.matcher(path.substring(pos + marker.length() - 1));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    /** Descripción: Obtiene la información correspondiente a «findClass» dentro de Context. Entradas: model: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaClass findClass(MetaModel model, String id) {
        if(model == null) return null;
        return model.classes().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    /** Descripción: Ejecuta la operación «attr» correspondiente a la responsabilidad de Context. Entradas: owner: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaAttribute attr(MetaClass owner, String id) {
        if(owner == null) return null;
        return owner.attributes().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    /** Descripción: Ejecuta la operación «visibleTransformation» correspondiente a la responsabilidad de Context. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String visibleTransformation(TransformationDefinitionDocument document) {
        if(document == null) return "<missing>";
        return !document.name().isBlank() ? document.name() + " (id '" + display(document.id(), "<no-id>") + "')" : display(document.id(), "<unnamed>");
    }

    /** Descripción: Ejecuta la operación «visibleRule» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String visibleRule(TransformationRule rule) {
        if(rule == null) return "<unknown rule>";
        return rule.name().isBlank() ? display(rule.id(), "<unnamed rule>") : rule.name() + " (id '" + display(rule.id(), "<no-id>") + "')";
    }

    /** Descripción: Ejecuta la operación «bindingName» correspondiente a la responsabilidad de Context. Entradas: binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String bindingName(TransformationBinding binding) {
        return binding == null ? "<unknown binding>" : display(binding.id(), "<unnamed binding>");
    }

    /** Descripción: Ejecuta la operación «ruleOrGenerationName» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String ruleOrGenerationName(TransformationRule rule, TransformationGenerationRule generation) {
        if(rule != null) return visibleRule(rule);
        if(generation == null) return "<unknown generation rule>";
        return generation.name().isBlank() ? display(generation.id(), "<unnamed generation rule>") : generation.name() + " (id '" + display(generation.id(), "<no-id>") + "')";
    }

    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String sourceType(TransformationRule rule) { return value(rule == null ? "" : rule.sourceTypeId()); }
    /** Descripción: Ejecuta la operación «firstTargetType» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String firstTargetType(TransformationRule rule) { return rule == null || rule.targets().isEmpty() ? "Target" : value(rule.targets().get(0).typeId()); }
    /** Descripción: Ejecuta la operación «direction» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String direction(TransformationRule rule) { return rule == null ? "FORWARD" : rule.direction().name(); }
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de Context. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String className(MetaClass value) { return value == null ? "<unresolved class>" : display(value.name(), value.id()) + " (id '" + value.id() + "')"; }
    /** Descripción: Ejecuta la operación «attributeName» correspondiente a la responsabilidad de Context. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String attributeName(MetaAttribute value) { return value == null ? "<unresolved attribute>" : display(value.name(), value.id()) + " (id '" + value.id() + "')"; }
    /** Descripción: Ejecuta la operación «targetFeatureName» correspondiente a la responsabilidad de Context. Entradas: targetClass: dato de entrada necesario para la operación; binding: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String targetFeatureName(MetaClass targetClass, TransformationBinding binding) { return attributeName(attr(targetClass, binding == null ? "" : binding.targetFeatureId())); }
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de Context. Entradas: attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String type(MetaAttribute attribute) { return attribute == null ? "<unknown type>" : type(attribute.type()); }
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de Context. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String type(MetaTypeReference type) {
        if(type == null) return "<unknown type>";
        if(!type.targetId().isBlank()) return type.targetId();
        if(!type.externalName().isBlank()) return type.externalName();
        return type.kind().name();
    }
    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de Context. Entradas: attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String multiplicity(MetaAttribute attribute) { return attribute == null ? "?" : multiplicity(attribute.multiplicity()); }
    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de Context. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String multiplicity(Multiplicity value) { return value == null ? "unspecified" : value.lowerBound() + ".." + (value.upperBound() == null ? "*" : value.upperBound()); }
    /** Descripción: Ejecuta la operación «modelName» correspondiente a la responsabilidad de Context. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String modelName(MetaModel model) { return model == null ? "<unresolved metamodel>" : display(model.name(), model.id()) + " (id '" + model.id() + "')"; }

    /** Descripción: Ejecuta la operación «classList» correspondiente a la responsabilidad de Context. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String classList(MetaModel model) {
        if(model == null || model.classes().isEmpty()) return "<none>";
        return list(model.classes().stream().map(item -> display(item.name(), item.id()) + " [" + item.id() + "]").toList());
    }
    /** Descripción: Ejecuta la operación «attributeList» correspondiente a la responsabilidad de Context. Entradas: owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String attributeList(MetaClass owner) {
        if(owner == null || owner.attributes().isEmpty()) return "<none>";
        return list(owner.attributes().stream().map(item -> display(item.name(), item.id()) + " [" + item.id() + "]").toList());
    }
    /** Descripción: Ejecuta la operación «attributeIdList» correspondiente a la responsabilidad de Context. Entradas: owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String attributeIdList(MetaClass owner) {
        if(owner == null || owner.attributes().isEmpty()) return "<none>";
        return list(owner.attributes().stream().map(MetaAttribute::id).toList());
    }
    /** Descripción: Ejecuta la operación «relationList» correspondiente a la responsabilidad de Context. Entradas: model: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String relationList(MetaModel model, MetaClass owner) {
        if(model == null || owner == null) return "<none>";
        List<String> values = model.relations().stream().filter(item -> item.sourceTypeId().equals(owner.id()))
                .map(item -> display(item.name(), item.id()) + " [" + item.id() + "]").toList();
        return values.isEmpty() ? "<none>" : list(values);
    }
    /** Descripción: Ejecuta la operación «targetAliasList» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String targetAliasList(TransformationRule rule) {
        if(rule == null || rule.targets().isEmpty()) return "<none>";
        return list(rule.targets().stream().map(TransformationTargetPattern::alias).toList());
    }
    /** Descripción: Ejecuta la operación «outputEndpointList» correspondiente a la responsabilidad de Context. Entradas: ctx: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String outputEndpointList(Context ctx, TransformationRule rule) {
        List<TransformationEndpoint> endpoints = rule != null && rule.direction() == TransformationRuleDirection.REVERSE ? ctx.document.sources() : ctx.document.targets();
        return endpoints.isEmpty() ? "<none>" : list(endpoints.stream().map(TransformationEndpoint::id).toList());
    }
    /** Descripción: Ejecuta la operación «availableInputEndpoints» correspondiente a la responsabilidad de Context. Entradas: ctx: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String availableInputEndpoints(Context ctx, TransformationRule rule) {
        List<TransformationEndpoint> endpoints = rule != null && rule.direction() == TransformationRuleDirection.REVERSE ? ctx.document.targets() : ctx.document.sources();
        return endpoints.isEmpty() ? "<none>" : list(endpoints.stream().map(TransformationEndpoint::id).toList());
    }
    /** Descripción: Ejecuta la operación «sourceEndpointReason» correspondiente a la responsabilidad de Context. Entradas: ctx: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String sourceEndpointReason(Context ctx, TransformationRule rule, TransformationGenerationRule generation) {
        String id = sourceEndpointId(rule, generation);
        if(rule != null) return "Rule '" + visibleRule(rule) + "' is " + direction(rule) + " and declares sourceEndpointId='" + id + "', but its input side contains endpoint IDs " + availableInputEndpoints(ctx, rule) + ".";
        return "Generation rule '" + ruleOrGenerationName(null, generation) + "' declares sourceEndpointId='" + id + "', but the transformation source side contains " + list(ctx.document.sources().stream().map(TransformationEndpoint::id).toList()) + ".";
    }
    /** Descripción: Ejecuta la operación «sourceEndpointId» correspondiente a la responsabilidad de Context. Entradas: rule: dato de entrada necesario para la operación; generation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String sourceEndpointId(TransformationRule rule, TransformationGenerationRule generation) {
        return value(rule != null ? rule.sourceEndpointId() : generation == null ? "" : generation.sourceEndpointId());
    }

    /** Descripción: Ejecuta la operación «endpointRole» correspondiente a la responsabilidad de Context. Entradas: endpoint: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String endpointRole(TransformationEndpoint endpoint, String path) { return endpoint == null ? expectedSide(path) : endpoint.role().name(); }
    /** Descripción: Ejecuta la operación «endpointName» correspondiente a la responsabilidad de Context. Entradas: endpoint: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String endpointName(TransformationEndpoint endpoint) { return endpoint == null ? "<unknown endpoint>" : display(endpoint.alias(), endpoint.id()) + " (id '" + display(endpoint.id(), "<no-id>") + "')"; }
    /** Descripción: Ejecuta la operación «expectedSide» correspondiente a la responsabilidad de Context. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String expectedSide(String path) { return path != null && path.startsWith("$.targets") ? "TARGET" : "SOURCE"; }
    /** Descripción: Obtiene la información correspondiente a «resolvedModelId» dentro de Context. Entradas: ctx: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String resolvedModelId(Context ctx, String path) { MetaModel model = path != null && path.startsWith("$.targets") ? ctx.target : ctx.source; return model == null ? "<not resolved>" : model.id(); }
    /** Descripción: Ejecuta la operación «parentPath» correspondiente a la responsabilidad de Context. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String parentPath(String path) { int pos = path == null ? -1 : path.lastIndexOf('.'); return pos > 0 ? path.substring(0, pos) : value(path); }

    /** Descripción: Ejecuta la operación «compatibleCandidate» correspondiente a la responsabilidad de Context. Entradas: sourceClass: dato de entrada necesario para la operación; targetName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String compatibleCandidate(MetaClass sourceClass, String targetName) {
        if(sourceClass == null || sourceClass.attributes().isEmpty()) return "";
        String simple = targetName.contains(".") ? targetName.substring(targetName.lastIndexOf('.') + 1) : targetName;
        return sourceClass.attributes().stream().filter(a -> a.name().equalsIgnoreCase(simple) || a.id().equalsIgnoreCase(simple))
                .map(a -> display(a.name(), a.id()) + " [" + a.id() + "]").findFirst().orElse("");
    }

    /** Descripción: Obtiene la información correspondiente a «list» dentro de Context. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String list(List<String> values) {
        List<String> clean = values.stream().filter(item -> item != null && !item.isBlank()).distinct().toList();
        if(clean.isEmpty()) return "<none>";
        String joined = clean.stream().limit(LIST_LIMIT).map(item -> "'" + item + "'").collect(Collectors.joining(", "));
        return clean.size() > LIST_LIMIT ? joined + " (and " + (clean.size() - LIST_LIMIT) + " more)" : joined;
    }
    /** Descripción: Ejecuta la operación «display» correspondiente a la responsabilidad de Context. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String display(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de Context. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String value(String value) { return display(value, "<empty>"); }
    /** Descripción: Ejecuta la operación «slug» correspondiente a la responsabilidad de Context. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String slug(String value, String fallback) {
        String source = display(value, fallback).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return source.isBlank() ? fallback : source;
    }

    private record Context(TransformationDefinitionDocument document, MetaModel source, MetaModel target) {
        /** Descripción: Ejecuta la operación «rule» correspondiente a la responsabilidad de Context. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ TransformationRule rule(String id) {
            if(id == null || id.isBlank()) return null;
            return document.rules().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        }
        /** Descripción: Ejecuta la operación «generationRule» correspondiente a la responsabilidad de Context. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ TransformationGenerationRule generationRule(String id) {
            if(id == null || id.isBlank()) return null;
            return document.generationRules().stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
        }
        /** Descripción: Ejecuta la operación «ruleByPath» correspondiente a la responsabilidad de Context. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationRule ruleByPath(String path) {
            int index = indexed(path, ".rules[");
            return index >= 0 && index < document.rules().size() ? document.rules().get(index) : null;
        }
        /** Descripción: Ejecuta la operación «generationRuleByPath» correspondiente a la responsabilidad de Context. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationGenerationRule generationRuleByPath(String path) {
            int index = indexed(path, ".generationRules[");
            return index >= 0 && index < document.generationRules().size() ? document.generationRules().get(index) : null;
        }
        /** Descripción: Ejecuta la operación «endpoint» correspondiente a la responsabilidad de Context. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationEndpoint endpoint(String path) {
            if(path == null) return null;
            List<TransformationEndpoint> endpoints = path.startsWith("$.targets") ? document.targets() : path.startsWith("$.sources") ? document.sources() : List.of();
            if(endpoints.isEmpty()) return null;
            Matcher matcher = INDEX.matcher(path);
            if(!matcher.find()) return null;
            int index = Integer.parseInt(matcher.group(1));
            return index >= 0 && index < endpoints.size() ? endpoints.get(index) : null;
        }
    }
}
