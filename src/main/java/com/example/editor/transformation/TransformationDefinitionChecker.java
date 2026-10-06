package com.example.editor.transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationCollectionMode;
import com.example.editor.transformation.core.TransformationConversionMode;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationDiagnosticSeverity;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.semantic.expression.ExpressionCheckResult;
import com.example.editor.semantic.expression.ExpressionDiagnosticSeverity;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;

@Service
public class TransformationDefinitionChecker {
    private final ExpressionTypeChecker expressions;

    /** Descripción: Inicializa una instancia de TransformationDefinitionChecker con sus dependencias y datos de entrada. Entradas: expressions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationDefinitionChecker(ExpressionTypeChecker expressions) {
        this.expressions = expressions;
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación; sourceModels: dato de entrada necesario para la operación; targetModels: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationCheckReport check(TransformationDefinitionDocument document, List<MetaModel> sourceModels, List<MetaModel> targetModels) {
        List<TransformationDiagnostic> diagnostics = new ArrayList<>();
        if(document == null) {
            diagnostics.add(error("TRANSFORMATION_REQUIRED", "", "", "$", "No transformation definition was provided",
                    "HYDRA received an empty transformation document, so there is no kind, endpoint, rule, template or parser configuration to validate.",
                    "Create a new transformation or load an existing saved definition, then run Check again. If this appeared after opening a saved item, reload it from the catalogue instead of continuing with an empty editor state."));
            return new TransformationCheckReport("", diagnostics);
        }
        if(!TransformationDefinitionDocument.SUPPORTED_SCHEMA_VERSIONS.contains(document.schemaVersion())) {
            diagnostics.add(error("UNSUPPORTED_SCHEMA_VERSION", "", "", "$.schemaVersion", "Unsupported schema version",
                    "This HYDRA version supports transformation schemas 1.0 and 1.1.", "Open the document with a compatible version or migrate it."));
        }
        if(document.id().isBlank()) diagnostics.add(error("TRANSFORMATION_ID_REQUIRED", "", "", "$.id", "Transformation ID required",
                "The transformation needs a stable identifier.", "Provide a unique transformation ID."));
        if(document.name().isBlank()) diagnostics.add(error("TRANSFORMATION_NAME_REQUIRED", "", "", "$.name", "Transformation name required",
                "The transformation needs a visible name.", "Provide a descriptive transformation name."));
        if(!document.executionPolicy().deterministic()) diagnostics.add(error("NON_DETERMINISTIC_EXECUTION_NOT_SUPPORTED", "", "", "$.executionPolicy.deterministic",
                "Non-deterministic execution is not available yet",
                "The reference runtime guarantees deterministic rule and artifact order.",
                "Keep deterministic execution enabled until an explicit non-deterministic policy is implemented."));
        if(!document.executionPolicy().failOnBindingError()) diagnostics.add(error("BEST_EFFORT_BINDING_EXECUTION_NOT_SUPPORTED", "", "", "$.executionPolicy.failOnBindingError",
                "Best-effort binding execution is not available yet",
                "The reference runtime currently fails closed when a binding, template or parse step cannot be evaluated.",
                "Keep fail-on-binding-error enabled so the stored policy matches the runtime semantics."));

        MetaModel source = sourceModels.isEmpty() ? null : sourceModels.get(0);
        MetaModel target = targetModels.isEmpty() ? null : targetModels.get(0);
        if(document.kind() != TransformationKind.M2M && document.directionality() == TransformationDirectionality.BIDIRECTIONAL) {
            diagnostics.add(error("BIDIRECTIONAL_ONLY_M2M", "", "", "$.directionality",
                    "Bidirectional mode is only available for M2M transformations",
                    "Text generation and text-to-model transformations have a single semantic direction in the current canonical runtime.",
                    "Use unidirectional mode or change the transformation family to M2M."));
        }
        if(document.kind() == TransformationKind.M2M) {
            if(document.directionality() == TransformationDirectionality.BIDIRECTIONAL && !document.tracePolicy().enabled())
                diagnostics.add(error("BIDIRECTIONAL_TRACE_REQUIRED", "", "", "$.tracePolicy.enabled",
                        "Trace is required for bidirectional transformations",
                        "Stable trace evidence is required to relate both execution directions and prepare deterministic synchronization.",
                        "Enable transformation trace before using bidirectional mode."));
            checkEndpoints(document.sources(), TransformationEndpointRole.SOURCE, sourceModels, diagnostics, "$.sources");
            checkEndpoints(document.targets(), TransformationEndpointRole.TARGET, targetModels, diagnostics, "$.targets");
            if(document.sources().isEmpty()) diagnostics.add(error("SOURCE_REQUIRED", "", "", "$.sources", "Source metamodel required",
                    "An M2M transformation needs at least one source semantic context.", "Select a source metamodel."));
            if(document.targets().isEmpty()) diagnostics.add(error("TARGET_REQUIRED", "", "", "$.targets", "Target metamodel required",
                    "An M2M transformation needs at least one target semantic context.", "Select a target metamodel."));
            checkBidirectionality(document, diagnostics);
            checkRules(document.rules(), document.sources(), document.targets(), source, target, diagnostics);
        } else if(document.kind() == TransformationKind.M2T) {
            checkEndpoints(document.sources(), TransformationEndpointRole.SOURCE, sourceModels, diagnostics, "$.sources");
            if(document.sources().isEmpty()) diagnostics.add(error("SOURCE_REQUIRED", "", "", "$.sources", "Source metamodel required",
                    "An M2T transformation needs one source semantic context.", "Select the metamodel whose instances will be rendered."));
            checkGenerationRules(document.generationRules(), document.sources(), source, diagnostics);
            if(document.generationRules().isEmpty()) diagnostics.add(error("GENERATION_RULE_REQUIRED", "", "", "$.generationRules",
                    "Generation rule required", "An M2T transformation needs at least one generation rule.",
                    "Add a rule with context type, output path and content template."));
        } else {
            if(document.textToModel() == null || document.textToModel().grammarId().isBlank()) diagnostics.add(error(
                    "GRAMMAR_REQUIRED", "", "", "$.textToModel.grammarId", "Compiled grammar required",
                    "A T2M transformation needs a grammar whose active snapshot can parse the input text.",
                    "Select a grammar and compile/activate a valid snapshot before running T2M."));
        }
        return new TransformationCheckReport(document.id(), TransformationDiagnosticEnricher.enrich(document, sourceModels, targetModels, diagnostics));
    }

    /** Valida reglas M2T sin ejecutar plantillas ni escribir artefactos. */
    /** Descripción: Valida las condiciones necesarias para «checkGenerationRules» y rechaza entradas no válidas. Entradas: rules: dato de entrada necesario para la operación; sources: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkGenerationRules(List<TransformationGenerationRule> rules, List<TransformationEndpoint> sources, MetaModel source,
            List<TransformationDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        for(int index=0; index<rules.size(); index++) {
            TransformationGenerationRule rule = rules.get(index);
            String path = "$.generationRules[" + index + "]";
            if(rule.id().isBlank() || !ids.add(rule.id())) diagnostics.add(error("GENERATION_RULE_ID_REQUIRED_OR_DUPLICATED", rule.id(), "", path + ".id",
                    "Generation rule ID must be unique", "Every M2T rule needs a stable unique identifier.", "Provide a unique rule ID."));
            if(sources.stream().noneMatch(item -> item.id().equals(rule.sourceEndpointId()))) diagnostics.add(error("UNKNOWN_SOURCE_ENDPOINT", rule.id(), "", path + ".sourceEndpointId",
                    "Unknown source endpoint", "The generation rule references a source endpoint outside the transformation.", "Choose the configured source endpoint."));
            MetaClass context = source == null ? null : source.classes().stream().filter(item -> item.id().equals(rule.contextTypeId())).findFirst().orElse(null);
            if(source != null && context == null) diagnostics.add(error("UNKNOWN_GENERATION_CONTEXT", rule.id(), "", path + ".contextTypeId",
                    "Generation context not found", "The class used by this template no longer exists.", "Select an existing source class."));
            if(rule.outputPathTemplate().isBlank()) diagnostics.add(error("OUTPUT_PATH_REQUIRED", rule.id(), "", path + ".outputPathTemplate",
                    "Output path required", "Every generation rule needs a relative output path template.", "Provide a relative path such as src/{{attr:name}}.java."));
            if(rule.outputPathTemplate().startsWith("/") || rule.outputPathTemplate().contains("..") || rule.outputPathTemplate().matches("^[A-Za-z]:.*")) diagnostics.add(error(
                    "UNSAFE_OUTPUT_PATH", rule.id(), "", path + ".outputPathTemplate", "Unsafe output path",
                    "Generated artifacts must remain inside the export bundle and cannot use absolute or parent paths.", "Use a portable relative output path."));
            if(rule.guard() != null && source != null && context != null) {
                ExpressionCheckResult result = expressions.check(rule.guard(), source);
                result.diagnostics().forEach(item -> diagnostics.add(new TransformationDiagnostic(
                        item.severity() == ExpressionDiagnosticSeverity.ERROR ? TransformationDiagnosticSeverity.ERROR :
                        item.severity() == ExpressionDiagnosticSeverity.WARNING ? TransformationDiagnosticSeverity.WARNING : TransformationDiagnosticSeverity.INFO,
                        "GENERATION_GUARD_" + item.code(), rule.id(), "", path + ".guard" + item.path(), "Invalid generation condition", item.message(),
                        "Correct the condition before generating text.")));
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkEndpoints» y rechaza entradas no válidas. Entradas: endpoints: dato de entrada necesario para la operación; expectedRole: dato de entrada necesario para la operación; models: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkEndpoints(List<TransformationEndpoint> endpoints, TransformationEndpointRole expectedRole,
            List<MetaModel> models, List<TransformationDiagnostic> diagnostics, String path) {
        Set<String> ids = new HashSet<>();
        for(int index = 0; index < endpoints.size(); index++) {
            TransformationEndpoint endpoint = endpoints.get(index);
            String itemPath = path + "[" + index + "]";
            if(endpoint.id().isBlank()) diagnostics.add(error("ENDPOINT_ID_REQUIRED", "", "", itemPath + ".id",
                    "Endpoint ID required", "Every transformation endpoint needs a stable ID.", "Provide an endpoint ID."));
            else if(!ids.add(endpoint.id())) diagnostics.add(error("DUPLICATE_ENDPOINT_ID", "", "", itemPath + ".id",
                    "Duplicate endpoint ID", "Endpoint IDs must be unique inside one transformation.", "Rename the duplicated endpoint."));
            if(endpoint.role() != expectedRole) diagnostics.add(error("ENDPOINT_ROLE_MISMATCH", "", "", itemPath + ".role",
                    "Endpoint role mismatch", "The endpoint is stored in the wrong source/target collection.", "Move it to the matching collection."));
            if(endpoint.metamodelId().isBlank()) diagnostics.add(error("ENDPOINT_METAMODEL_REQUIRED", "", "", itemPath + ".metamodelId",
                    "Metamodel required", "The endpoint is not linked to a semantic context.", "Select a metamodel."));
            else if(index < models.size() && !endpoint.metamodelId().equals(models.get(index).id())) diagnostics.add(error(
                    "ENDPOINT_METAMODEL_MISMATCH", "", "", itemPath + ".metamodelId", "Endpoint metamodel mismatch",
                    "The resolved semantic context does not match the endpoint identifier.", "Reload the endpoint from the metamodel catalog."));
        }
    }

    /** Valida dirección, correspondencias y orientación de cada regla M2M. */
    /** Descripción: Valida las condiciones necesarias para «checkRules» y rechaza entradas no válidas. Entradas: rules: dato de entrada necesario para la operación; sources: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkRules(List<TransformationRule> rules, List<TransformationEndpoint> sources,
            List<TransformationEndpoint> targets, MetaModel source, MetaModel target, List<TransformationDiagnostic> diagnostics) {
        Set<String> ids = new HashSet<>();
        for(int index = 0; index < rules.size(); index++) {
            TransformationRule rule = rules.get(index);
            String path = "$.rules[" + index + "]";
            if(rule.id().isBlank()) diagnostics.add(error("RULE_ID_REQUIRED", rule.id(), "", path + ".id", "Rule ID required",
                    "Every transformation rule needs a stable identifier.", "Provide a rule ID."));
            else if(!ids.add(rule.id())) diagnostics.add(error("DUPLICATE_RULE_ID", rule.id(), "", path + ".id", "Duplicate rule ID",
                    "Rule IDs must be unique.", "Rename the duplicated rule."));
            if(rule.name().isBlank()) diagnostics.add(error("RULE_NAME_REQUIRED", rule.id(), "", path + ".name", "Rule name required",
                    "The rule needs a visible name.", "Provide a concise rule name."));

            boolean backward = rule.direction() == TransformationRuleDirection.REVERSE;
            List<TransformationEndpoint> inputEndpoints = backward ? targets : sources;
            List<TransformationEndpoint> outputEndpoints = backward ? sources : targets;
            MetaModel inputModel = backward ? target : source;
            MetaModel outputModel = backward ? source : target;
            if(!inputEndpoints.isEmpty() && inputEndpoints.stream().noneMatch(item -> item.id().equals(rule.sourceEndpointId()))) {
                diagnostics.add(error("UNKNOWN_SOURCE_ENDPOINT", rule.id(), "", path + ".sourceEndpointId", "Unknown source endpoint",
                        backward ? "The backward rule must read from a target-side endpoint." :
                                "The forward rule must read from a source-side endpoint.",
                        "Select one of the endpoints available on the input side of this rule."));
            }
            MetaClass inputClass = inputModel == null ? null : inputModel.classes().stream()
                    .filter(item -> item.id().equals(rule.sourceTypeId())).findFirst().orElse(null);
            if(inputModel != null && inputClass == null) diagnostics.add(error("UNKNOWN_SOURCE_TYPE", rule.id(), "", path + ".sourceTypeId",
                    "Source type not found", "The input class referenced by this directional rule no longer exists.",
                    "Select an existing input class for this direction."));
            checkGuard(rule, inputModel, inputClass, diagnostics, path);
            checkTargets(rule, outputEndpoints, outputModel, diagnostics, path);
            checkBindings(rule, inputClass, inputModel, outputModel, diagnostics, path);
        }
    }

    /** La bidireccionalidad es first-class: las reglas opuestas comparten una correspondencia estable. */
    /** Descripción: Valida las condiciones necesarias para «checkBidirectionality» y rechaza entradas no válidas. Entradas: document: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkBidirectionality(TransformationDefinitionDocument document, List<TransformationDiagnostic> diagnostics) {
        List<TransformationRule> enabled = document.rules().stream().filter(TransformationRule::enabled).toList();
        if(document.directionality() == TransformationDirectionality.UNIDIRECTIONAL) {
            for(int index = 0; index < document.rules().size(); index++) {
                TransformationRule rule = document.rules().get(index);
                if(rule.enabled() && rule.direction() == TransformationRuleDirection.REVERSE) {
                    diagnostics.add(error("REVERSE_RULE_REQUIRES_BIDIRECTIONAL", rule.id(), "", "$.rules[" + index + "].direction",
                            "Reverse rule '" + visibleRuleName(rule) + "' requires bidirectional mode",
                            "Rule '" + visibleRuleName(rule) + "' (id '" + rule.id() + "') is enabled as REVERSE, but the transformation is UNIDIRECTIONAL.",
                            "Either change the transformation directionality to BIDIRECTIONAL or disable/remove rule '" + visibleRuleName(rule) + "'."));
                }
            }
            return;
        }
        if(enabled.isEmpty()) {
            diagnostics.add(error("BIDIRECTIONAL_RULE_PAIR_REQUIRED", "", "", "$.rules",
                    "A bidirectional transformation needs a rule pair",
                    "There are no enabled rules. HYDRA needs at least one FORWARD rule and its explicit REVERSE partner to execute a bidirectional M2M transformation.",
                    "Create a FORWARD rule, assign a correspondence ID, then create a REVERSE rule with that same correspondence ID."));
            return;
        }
        Set<String> correspondenceIds = new HashSet<>();
        for(int index = 0; index < document.rules().size(); index++) {
            TransformationRule rule = document.rules().get(index);
            if(!rule.enabled()) continue;
            if(rule.correspondenceId().isBlank()) {
                diagnostics.add(error("CORRESPONDENCE_ID_REQUIRED", rule.id(), "", "$.rules[" + index + "].correspondenceId",
                        "Rule '" + visibleRuleName(rule) + "' has no correspondence ID",
                        "Rule '" + visibleRuleName(rule) + "' (id '" + rule.id() + "') cannot be paired with its opposite direction because its correspondence ID is empty.",
                        "Set a stable correspondence ID, for example 'corr:" + safeExampleId(rule.id()) + "', and reuse exactly that ID in the opposite rule."));
            } else {
                correspondenceIds.add(rule.correspondenceId());
            }
        }
        for(String correspondenceId : correspondenceIds) {
            TransformationRule forwardRule = enabled.stream().filter(rule -> correspondenceId.equals(rule.correspondenceId())
                    && rule.direction() == TransformationRuleDirection.FORWARD).findFirst().orElse(null);
            TransformationRule reverseRule = enabled.stream().filter(rule -> correspondenceId.equals(rule.correspondenceId())
                    && rule.direction() == TransformationRuleDirection.REVERSE).findFirst().orElse(null);
            if(forwardRule == null || reverseRule == null) {
                TransformationRule existing = forwardRule != null ? forwardRule : reverseRule;
                TransformationRuleDirection missing = forwardRule == null ? TransformationRuleDirection.FORWARD : TransformationRuleDirection.REVERSE;
                int ruleIndex = document.rules().indexOf(existing);
                String outputTypes = existing.targets().isEmpty() ? "the corresponding output type" : existing.targets().stream()
                        .map(TransformationTargetPattern::typeId).distinct().reduce((a, b) -> a + ", " + b).orElse("the corresponding output type");
                String missingSourceType = existing.targets().isEmpty() ? "<opposite-type>" : existing.targets().get(0).typeId();
                String missingTargetType = existing.sourceTypeId().isBlank() ? "<original-source-type>" : existing.sourceTypeId();
                String exampleName = missing == TransformationRuleDirection.REVERSE ? "Reverse " + visibleRuleName(existing) : "Forward " + visibleRuleName(existing);
                diagnostics.add(error("INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE", existing.id(), "", "$.rules[" + ruleIndex + "].correspondenceId",
                        "Missing " + missing.name() + " rule for correspondence '" + correspondenceId + "'",
                        "Correspondence '" + correspondenceId + "' is incomplete. Existing rule '" + visibleRuleName(existing) + "' (id '" + existing.id()
                                + "') is " + existing.direction().name() + " from source type '" + existing.sourceTypeId() + "' to target type(s) '" + outputTypes
                                + "', but no enabled " + missing.name() + " rule uses the same correspondence ID.",
                        "Create an enabled " + missing.name() + " rule with correspondenceId='" + correspondenceId + "'. Example: name='" + exampleName
                                + "', direction=" + missing.name() + ", sourceTypeId='" + missingSourceType + "', target type='" + missingTargetType
                                + "'. Then map the inverse attributes/relations explicitly; HYDRA will not guess inverse bindings."));
            }
        }
    }

    /** Descripción: Ejecuta la operación «visibleRuleName» correspondiente a la responsabilidad de TransformationDefinitionChecker. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String visibleRuleName(TransformationRule rule) {
        return rule.name().isBlank() ? rule.id() : rule.name();
    }

    /** Descripción: Ejecuta la operación «safeExampleId» correspondiente a la responsabilidad de TransformationDefinitionChecker. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeExampleId(String value) {
        if(value == null || value.isBlank()) return "rule";
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    /** Descripción: Valida las condiciones necesarias para «checkGuard» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkGuard(TransformationRule rule, MetaModel source, MetaClass sourceClass,
            List<TransformationDiagnostic> diagnostics, String path) {
        if(rule.guard() == null || source == null || sourceClass == null) return;
        if(!sourceClass.id().equals(rule.guard().contextClassId())) diagnostics.add(error("GUARD_CONTEXT_MISMATCH", rule.id(), "",
                path + ".guard.contextClassId", "Guard context mismatch",
                "The condition must be evaluated in the source class of the rule.", "Use the rule source class as the condition context."));
        ExpressionCheckResult result = expressions.check(rule.guard(), source);
        result.diagnostics().forEach(item -> diagnostics.add(new TransformationDiagnostic(
                item.severity() == ExpressionDiagnosticSeverity.ERROR ? TransformationDiagnosticSeverity.ERROR :
                    item.severity() == ExpressionDiagnosticSeverity.WARNING ? TransformationDiagnosticSeverity.WARNING : TransformationDiagnosticSeverity.INFO,
                "GUARD_" + item.code(), rule.id(), "", path + ".guard" + item.path(), "Invalid rule condition",
                item.message(), "Correct the condition before executing the transformation.")));
        if(result.inferredType().kind() != ExpressionValueKind.BOOLEAN && result.inferredType().kind() != ExpressionValueKind.ANY) {
            diagnostics.add(error("GUARD_MUST_BE_BOOLEAN", rule.id(), "", path + ".guard", "Rule condition must be Boolean",
                    "The rule guard does not evaluate to true/false.", "Change the expression so it returns a Boolean value."));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkTargets» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkTargets(TransformationRule rule, List<TransformationEndpoint> targets, MetaModel target,
            List<TransformationDiagnostic> diagnostics, String path) {
        Set<String> aliases = new HashSet<>();
        for(int index = 0; index < rule.targets().size(); index++) {
            TransformationTargetPattern pattern = rule.targets().get(index);
            String targetPath = path + ".targets[" + index + "]";
            if(targets.stream().noneMatch(item -> item.id().equals(pattern.endpointId()))) diagnostics.add(error(
                    "UNKNOWN_TARGET_ENDPOINT", rule.id(), "", targetPath + ".endpointId", "Unknown target endpoint",
                    "The target pattern references an endpoint outside the transformation.", "Select a configured target endpoint."));
            if(pattern.alias().isBlank() || !aliases.add(pattern.alias())) diagnostics.add(error(
                    "TARGET_ALIAS_REQUIRED_OR_DUPLICATED", rule.id(), "", targetPath + ".alias", "Target alias must be unique",
                    "Bindings address target objects by alias.", "Provide a non-empty alias that is unique inside the rule."));
            if(target != null && target.classes().stream().noneMatch(item -> item.id().equals(pattern.typeId()))) diagnostics.add(error(
                    "UNKNOWN_TARGET_TYPE", rule.id(), "", targetPath + ".typeId", "Target type not found",
                    "The target class referenced by this rule no longer exists.", "Select an existing target class."));
        }
        if(rule.targets().isEmpty()) diagnostics.add(error("RULE_TARGET_REQUIRED", rule.id(), "", path + ".targets", "Target type required",
                "An M2M rule must create at least one target object.", "Add a target class to the rule."));
    }

    /** Descripción: Valida las condiciones necesarias para «checkBindings» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkBindings(TransformationRule rule, MetaClass sourceClass, MetaModel source, MetaModel target,
            List<TransformationDiagnostic> diagnostics, String path) {
        Set<String> ids = new HashSet<>();
        Set<String> mappedTargets = new HashSet<>();
        for(int index = 0; index < rule.bindings().size(); index++) {
            TransformationBinding binding = rule.bindings().get(index);
            String bindingPath = path + ".bindings[" + index + "]";
            if(binding.id().isBlank() || !ids.add(binding.id())) diagnostics.add(error("BINDING_ID_REQUIRED_OR_DUPLICATED",
                    rule.id(), binding.id(), bindingPath + ".id", "Binding ID must be unique",
                    "Every binding needs a stable unique identifier.", "Provide a unique binding ID."));
            TransformationTargetPattern pattern = rule.targets().stream().filter(item -> item.alias().equals(binding.targetAlias())).findFirst().orElse(null);
            if(pattern == null) {
                diagnostics.add(error("UNKNOWN_TARGET_ALIAS", rule.id(), binding.id(), bindingPath + ".targetAlias",
                        "Unknown target alias", "The binding points to a target object that is not created by this rule.",
                        "Choose one of the rule target aliases."));
                continue;
            }
            MetaClass targetClass = target == null ? null : target.classes().stream().filter(item -> item.id().equals(pattern.typeId())).findFirst().orElse(null);
            String targetKey = binding.targetAlias() + "|" + binding.targetFeatureId();
            if(!mappedTargets.add(targetKey)) diagnostics.add(warning("DUPLICATE_TARGET_BINDING", rule.id(), binding.id(), bindingPath,
                    "Target property is mapped more than once", "More than one binding writes the same target feature.",
                    "Keep a single deterministic binding for this target feature."));
            if(binding.kind() == TransformationBindingKind.DIRECT) checkDirect(binding, rule, sourceClass, targetClass, diagnostics, bindingPath);
            else if(binding.kind() == TransformationBindingKind.TRACE_EQUIVALENT) checkTrace(binding, rule, source, target, targetClass, diagnostics, bindingPath);
            else if(binding.kind() == TransformationBindingKind.CONSTANT) checkTargetAttribute(binding, rule, targetClass, diagnostics, bindingPath);
            else if(binding.kind() == TransformationBindingKind.EXPRESSION) checkExpressionBinding(binding, rule, source, targetClass, diagnostics, bindingPath);
        }
        warnRequiredTargets(rule, target, mappedTargets, diagnostics, path);
    }

    /** Descripción: Valida las condiciones necesarias para «checkDirect» y rechaza entradas no válidas. Entradas: binding: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; sourceClass: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkDirect(TransformationBinding binding, TransformationRule rule, MetaClass sourceClass, MetaClass targetClass,
            List<TransformationDiagnostic> diagnostics, String path) {
        MetaAttribute sourceAttribute = sourceClass == null ? null : sourceClass.attributes().stream()
                .filter(item -> item.id().equals(binding.sourceFeatureId())).findFirst().orElse(null);
        MetaAttribute targetAttribute = targetClass == null ? null : targetClass.attributes().stream()
                .filter(item -> item.id().equals(binding.targetFeatureId())).findFirst().orElse(null);
        if(sourceAttribute == null) diagnostics.add(error("UNKNOWN_SOURCE_ATTRIBUTE", rule.id(), binding.id(), path + ".sourceFeatureId",
                "Source attribute not found", "The direct binding references an attribute that does not exist on the source class.",
                "Select an existing source attribute."));
        if(targetAttribute == null) diagnostics.add(error("UNKNOWN_TARGET_ATTRIBUTE", rule.id(), binding.id(), path + ".targetFeatureId",
                "Target attribute not found", "The direct binding references an attribute that does not exist on the target class.",
                "Select an existing target attribute."));
        if(sourceAttribute != null && targetAttribute != null) {
            boolean explicitConversion = binding.valuePolicy().conversionMode() != TransformationConversionMode.NONE
                    || !binding.valuePolicy().enumMapping().isEmpty();
            if(!compatible(sourceAttribute.type(), targetAttribute.type()) && !explicitConversion) {
                diagnostics.add(error("TYPE_CONVERSION_REQUIRED", rule.id(), binding.id(), path + ".valuePolicy",
                        "Explicit type conversion required",
                        "The source and target attribute types are not directly compatible and no conversion policy is defined.",
                        "Choose an explicit conversion, enum mapping or calculated expression."));
            }
            boolean sourceMany = sourceAttribute.multiplicity() != null && (sourceAttribute.multiplicity().upperBound() == null || sourceAttribute.multiplicity().upperBound() > 1);
            boolean targetMany = targetAttribute.multiplicity() != null && (targetAttribute.multiplicity().upperBound() == null || targetAttribute.multiplicity().upperBound() > 1);
            if(sourceMany && !targetMany && binding.valuePolicy().collectionMode() == TransformationCollectionMode.PRESERVE) {
                diagnostics.add(error("COLLECTION_REDUCTION_REQUIRED", rule.id(), binding.id(), path + ".valuePolicy.collectionMode",
                        "Collection reduction required",
                        "A multi-valued source attribute cannot be assigned to a single-valued target without an explicit reduction.",
                        "Choose FIRST, LAST or COUNT, or calculate the target value with an expression."));
            }
            if(binding.valuePolicy().conversionMode() != TransformationConversionMode.NONE) {
                diagnostics.add(warning("EXPLICIT_VALUE_CONVERSION", rule.id(), binding.id(), path + ".valuePolicy.conversionMode",
                        "Explicit conversion will be applied",
                        "The canonical runtime will convert the binding value before writing the target property.",
                        "Keep this policy only if the conversion is intentional and test representative source values."));
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkTargetAttribute» y rechaza entradas no válidas. Entradas: binding: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkTargetAttribute(TransformationBinding binding, TransformationRule rule, MetaClass targetClass,
            List<TransformationDiagnostic> diagnostics, String path) {
        MetaAttribute targetAttribute = targetClass == null ? null : targetClass.attributes().stream()
                .filter(item -> item.id().equals(binding.targetFeatureId())).findFirst().orElse(null);
        if(targetAttribute == null) diagnostics.add(error("UNKNOWN_TARGET_ATTRIBUTE", rule.id(), binding.id(), path + ".targetFeatureId",
                "Target attribute not found", "The binding references an attribute that does not exist on the target class.",
                "Select an existing target attribute."));
    }

    /** Descripción: Valida las condiciones necesarias para «checkExpressionBinding» y rechaza entradas no válidas. Entradas: binding: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkExpressionBinding(TransformationBinding binding, TransformationRule rule, MetaModel source, MetaClass targetClass,
            List<TransformationDiagnostic> diagnostics, String path) {
        checkTargetAttribute(binding, rule, targetClass, diagnostics, path);
        if(binding.expression() == null) {
            diagnostics.add(error("BINDING_EXPRESSION_REQUIRED", rule.id(), binding.id(), path + ".expression", "Expression required",
                    "Expression bindings need an expression tree.", "Define the expression that calculates the target value."));
            return;
        }
        if(!binding.expression().contextClassId().isBlank() && !binding.expression().contextClassId().equals(rule.sourceTypeId())) {
            diagnostics.add(error("BINDING_EXPRESSION_CONTEXT_MISMATCH", rule.id(), binding.id(), path + ".expression.contextClassId",
                    "Expression context mismatch", "The binding expression must be evaluated in the source class of the rule.",
                    "Use the rule source class as the expression context."));
        }
        if(source == null) return;
        ExpressionCheckResult result = expressions.check(binding.expression(), source);
        result.diagnostics().forEach(item -> diagnostics.add(new TransformationDiagnostic(
                item.severity() == ExpressionDiagnosticSeverity.ERROR ? TransformationDiagnosticSeverity.ERROR :
                    item.severity() == ExpressionDiagnosticSeverity.WARNING ? TransformationDiagnosticSeverity.WARNING : TransformationDiagnosticSeverity.INFO,
                "BINDING_" + item.code(), rule.id(), binding.id(), path + ".expression" + item.path(), "Invalid binding expression",
                item.message(), "Correct the expression before executing the transformation.")));
    }

    /** Descripción: Valida las condiciones necesarias para «checkTrace» y rechaza entradas no válidas. Entradas: binding: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; targetClass: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkTrace(TransformationBinding binding, TransformationRule rule, MetaModel source, MetaModel target,
            MetaClass targetClass, List<TransformationDiagnostic> diagnostics, String path) {
        MetaRelation sourceRelation = source == null ? null : source.relations().stream()
                .filter(item -> item.id().equals(binding.sourceFeatureId()) && item.sourceTypeId().equals(rule.sourceTypeId())).findFirst().orElse(null);
        MetaRelation targetRelation = target == null || targetClass == null ? null : target.relations().stream()
                .filter(item -> item.id().equals(binding.targetFeatureId()) && item.sourceTypeId().equals(targetClass.id())).findFirst().orElse(null);
        if(sourceRelation == null) diagnostics.add(error("UNKNOWN_SOURCE_RELATION", rule.id(), binding.id(), path + ".sourceFeatureId",
                "Source relation not found", "Trace-equivalent bindings need a navigable source relation.", "Select a relation owned by the source class."));
        if(targetRelation == null) diagnostics.add(error("UNKNOWN_TARGET_RELATION", rule.id(), binding.id(), path + ".targetFeatureId",
                "Target relation not found", "Trace-equivalent bindings need a target relation.", "Select a relation owned by the target class."));
    }

    /** Descripción: Ejecuta la operación «warnRequiredTargets» correspondiente a la responsabilidad de TransformationDefinitionChecker. Entradas: rule: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; mappedTargets: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void warnRequiredTargets(TransformationRule rule, MetaModel target, Set<String> mappedTargets,
            List<TransformationDiagnostic> diagnostics, String path) {
        if(target == null) return;
        for(TransformationTargetPattern pattern : rule.targets()) {
            MetaClass targetClass = target.classes().stream().filter(item -> item.id().equals(pattern.typeId())).findFirst().orElse(null);
            if(targetClass == null) continue;
            for(MetaAttribute attribute : targetClass.attributes()) {
                if(attribute.multiplicity() != null && attribute.multiplicity().lowerBound() > 0
                        && attribute.defaultValue().isBlank() && !mappedTargets.contains(pattern.alias() + "|" + attribute.id())) {
                    diagnostics.add(warning("REQUIRED_TARGET_ATTRIBUTE_UNMAPPED", rule.id(), "", path + ".bindings",
                            "Required target attribute is not mapped",
                            targetClass.name() + "." + attribute.name() + " requires a value but has no binding.",
                            "Map the attribute, provide a constant, or define a calculated expression."));
                }
            }
        }
    }

    /** Descripción: Ejecuta la operación «compatible» correspondiente a la responsabilidad de TransformationDefinitionChecker. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean compatible(MetaTypeReference source, MetaTypeReference target) {
        if(source == null || target == null) return true;
        if(source.kind() == target.kind() && source.targetId().equals(target.targetId()) && source.externalName().equals(target.externalName())) return true;
        if(source.kind() != MetaTypeReferenceKind.PRIMITIVE || target.kind() != MetaTypeReferenceKind.PRIMITIVE) return false;
        try {
            MetaPrimitiveType left = MetaPrimitiveType.valueOf(source.targetId());
            MetaPrimitiveType right = MetaPrimitiveType.valueOf(target.targetId());
            if(left == right) return true;
            Set<MetaPrimitiveType> numeric = Set.of(MetaPrimitiveType.BYTE, MetaPrimitiveType.SHORT, MetaPrimitiveType.INTEGER,
                    MetaPrimitiveType.LONG, MetaPrimitiveType.FLOAT, MetaPrimitiveType.DOUBLE, MetaPrimitiveType.DECIMAL,
                    MetaPrimitiveType.BIG_INTEGER);
            return numeric.contains(left) && numeric.contains(right);
        } catch(IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de TransformationDefinitionChecker. Entradas: code: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; bindingId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; title: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationDiagnostic error(String code, String ruleId, String bindingId, String path,
            String title, String message, String solution) {
        return new TransformationDiagnostic(TransformationDiagnosticSeverity.ERROR, code, ruleId, bindingId, path, title, message, solution);
    }

    /** Descripción: Ejecuta la operación «warning» correspondiente a la responsabilidad de TransformationDefinitionChecker. Entradas: code: dato de entrada necesario para la operación; ruleId: identificador que delimita el recurso objetivo; bindingId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; title: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationDiagnostic warning(String code, String ruleId, String bindingId, String path,
            String title, String message, String solution) {
        return new TransformationDiagnostic(TransformationDiagnosticSeverity.WARNING, code, ruleId, bindingId, path, title, message, solution);
    }
}
