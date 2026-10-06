package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationValuePolicy;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared exact executable subset for Java, Xtend, Python, Go, JavaScript and TypeScript.
 * M2M and guard-free M2T are emitted as small language-neutral execution plans
 * consumed by official runtimes implemented independently in the target language.
 */
abstract class NativeCodeTransformationLanguageAdapterSupport extends CanonicalRuntimeBundleSupport {
    private final ObjectMapper mapper;

    /** Descripción: Inicializa una instancia de NativeCodeTransformationLanguageAdapterSupport con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ NativeCodeTransformationLanguageAdapterSupport(ObjectMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    /** Descripción: Ejecuta la operación «supportedKinds» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<TransformationKind> supportedKinds() {
        return Set.of(TransformationKind.M2M, TransformationKind.M2T);
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExportResult export(TransformationDefinitionDocument document, MetaModel source, MetaModel target) {
        if (document == null) return super.export(null, source, target);
        if (document.kind() == TransformationKind.M2M) return exportM2m(document, source, target);
        if (document.kind() == TransformationKind.M2T) return exportM2t(document, source);
        return super.export(document, source, target);
    }

    /** Descripción: Ejecuta la operación «exportM2m» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationExportResult exportM2m(TransformationDefinitionDocument document, MetaModel source, MetaModel target) {
        List<TransformationExportDiagnostic> diagnostics = new ArrayList<>();
        if (source == null || target == null) {
            diagnostics.add(error("METAMODELS_REQUIRED", "$.sources", "Both source and target metamodels are required for native M2M export.",
                    "Select resolvable source and target metamodels."));
            return blocked(document, diagnostics);
        }
        List<NativeRulePlan> rules = new ArrayList<>();
        for (int i = 0; i < document.rules().size(); i++) {
            TransformationRule rule = document.rules().get(i);
            if (!rule.enabled()) continue;
            String path = "$.rules[" + i + "]";
            if (rule.guard() != null) {
                diagnostics.add(error("GUARD_NOT_NATIVE", path + ".guard", "The managed native M2M backend does not evaluate guards yet.",
                        "Remove the guard or use the HYDRA canonical runtime."));
                continue;
            }
            if (rule.targets().size() != 1) {
                diagnostics.add(error("MULTI_TARGET_NOT_NATIVE", path + ".targets", "Managed native M2M execution currently requires exactly one target per rule.",
                        "Split the rule or use the HYDRA canonical runtime."));
                continue;
            }
            MetaClass sourceClass = source.classes().stream().filter(item -> item.id().equals(rule.sourceTypeId())).findFirst().orElse(null);
            MetaClass targetClass = target.classes().stream().filter(item -> item.id().equals(rule.targets().getFirst().typeId())).findFirst().orElse(null);
            if (sourceClass == null || targetClass == null) {
                diagnostics.add(error("RULE_TYPE_NOT_FOUND", path, "A rule references a class that no longer exists.", "Repair the rule before exporting."));
                continue;
            }
            List<NativeBindingPlan> bindings = new ArrayList<>();
            for (int j = 0; j < rule.bindings().size(); j++) {
                TransformationBinding binding = rule.bindings().get(j);
                String bindingPath = path + ".bindings[" + j + "]";
                if (!binding.targetAlias().equals(rule.targets().getFirst().alias())) continue;
                if (binding.kind() != TransformationBindingKind.DIRECT && binding.kind() != TransformationBindingKind.CONSTANT) {
                    diagnostics.add(error("BINDING_NOT_NATIVE", bindingPath,
                            "Managed native M2M execution currently supports direct and constant attribute bindings only.",
                            "Use the HYDRA canonical runtime for expressions or trace-equivalent relations."));
                    continue;
                }
                if (!binding.valuePolicy().equals(TransformationValuePolicy.defaults())) {
                    diagnostics.add(error("VALUE_POLICY_NOT_NATIVE", bindingPath + ".valuePolicy",
                            "Conversions, enum maps, collection reducers and fallback policies are not implemented by the managed native M2M backend.",
                            "Use the canonical runtime until this policy is supported natively."));
                    continue;
                }
                MetaAttribute targetAttribute = targetClass.attributes().stream()
                        .filter(item -> item.id().equals(binding.targetFeatureId())).findFirst().orElse(null);
                MetaAttribute sourceAttribute = binding.kind() == TransformationBindingKind.DIRECT
                        ? sourceClass.attributes().stream().filter(item -> item.id().equals(binding.sourceFeatureId())).findFirst().orElse(null)
                        : null;
                if (targetAttribute == null || (binding.kind() == TransformationBindingKind.DIRECT && sourceAttribute == null)) {
                    diagnostics.add(error("BINDING_FEATURE_NOT_FOUND", bindingPath, "A binding references a missing attribute.",
                            "Repair the attribute mapping before exporting."));
                    continue;
                }
                if (sourceAttribute != null && (!sourceAttribute.type().equals(targetAttribute.type())
                        || !sourceAttribute.multiplicity().equals(targetAttribute.multiplicity()))) {
                    diagnostics.add(error("DIRECT_BINDING_NOT_EXACT", bindingPath,
                            "Native direct execution requires identical type and multiplicity.",
                            "Declare the adaptation explicitly and use the canonical runtime until it is supported natively."));
                    continue;
                }
                if (binding.kind() == TransformationBindingKind.CONSTANT
                        && (targetAttribute.multiplicity() == null || targetAttribute.multiplicity().upperBound() == null
                                || targetAttribute.multiplicity().upperBound() > 1)) {
                    diagnostics.add(error("CONSTANT_COLLECTION_NOT_NATIVE", bindingPath,
                            "Managed native constant execution is currently limited to scalar attributes.",
                            "Use the canonical runtime for collection constants."));
                    continue;
                }
                bindings.add(new NativeBindingPlan(binding.kind().name(), binding.sourceFeatureId(), binding.targetFeatureId(), binding.literalValue()));
            }
            rules.add(new NativeRulePlan(rule.id(), sourceTypes(source, sourceClass.id()), targetClass.id(), List.copyOf(bindings)));
        }
        if (hasErrors(diagnostics)) return blocked(document, diagnostics);
        try {
            String targetMetamodelId = document.targets().isEmpty() ? target.id() : document.targets().getFirst().metamodelId();
            String targetFingerprint = document.targets().isEmpty() ? "" : document.targets().getFirst().metamodelFingerprint();
            NativeM2MPlan plan = new NativeM2MPlan("1.0", "M2M", document.id(), document.name(), targetMetamodelId, targetFingerprint, List.copyOf(rules));
            return exact(document, diagnostics, plan,
                    "This M2M export is executable by the HYDRA official " + language()
                            + " bridge. The native backend proves the exact direct/constant attribute subset only.\n");
        } catch (JacksonException exception) {
            diagnostics.add(error("NATIVE_PLAN_SERIALIZATION_FAILED", "$.rules", "Could not serialize the managed native M2M execution plan.",
                    "Correct the transformation definition and retry."));
            return blocked(document, diagnostics);
        }
    }

    /** Descripción: Ejecuta la operación «exportM2t» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationExportResult exportM2t(TransformationDefinitionDocument document, MetaModel source) {
        List<TransformationExportDiagnostic> diagnostics = new ArrayList<>();
        if (source == null) {
            diagnostics.add(error("SOURCE_METAMODEL_REQUIRED", "$.sources", "The source metamodel is required for native M2T export.",
                    "Select a resolvable source metamodel."));
            return blocked(document, diagnostics);
        }
        List<NativeGenerationRulePlan> rules = new ArrayList<>();
        for (int i = 0; i < document.generationRules().size(); i++) {
            TransformationGenerationRule rule = document.generationRules().get(i);
            if (!rule.enabled()) continue;
            String path = "$.generationRules[" + i + "]";
            if (rule.guard() != null) {
                diagnostics.add(error("GUARD_NOT_NATIVE", path + ".guard",
                        "Managed native M2T execution does not evaluate generation guards yet.",
                        "Remove the guard or use the HYDRA canonical runtime."));
                continue;
            }
            boolean contextExists = source.classes().stream().anyMatch(item -> item.id().equals(rule.contextTypeId()));
            if (!contextExists) {
                diagnostics.add(error("GENERATION_CONTEXT_NOT_FOUND", path + ".contextTypeId",
                        "The M2T generation rule references a class that no longer exists.",
                        "Select an existing source class before exporting."));
                continue;
            }
            if (rule.outputPathTemplate().isBlank()) {
                diagnostics.add(error("OUTPUT_PATH_REQUIRED", path + ".outputPathTemplate",
                        "Every native M2T generation rule needs a relative output path template.",
                        "Provide a portable relative output path."));
                continue;
            }
            rules.add(new NativeGenerationRulePlan(rule.id(), rule.contextTypeId(), rule.outputPathTemplate(),
                    rule.contentTemplate(), rule.overwritePolicy().name()));
        }
        if (hasErrors(diagnostics)) return blocked(document, diagnostics);
        try {
            NativeM2TPlan plan = new NativeM2TPlan("1.0", "M2T", document.id(), document.name(), List.copyOf(rules));
            return exact(document, diagnostics, plan,
                    "This M2T export is executable by the HYDRA official " + language()
                            + " bridge. The native backend proves guard-free token rendering and FAIL/REPLACE collision semantics.\n");
        } catch (JacksonException exception) {
            diagnostics.add(error("NATIVE_PLAN_SERIALIZATION_FAILED", "$.generationRules", "Could not serialize the managed native M2T execution plan.",
                    "Correct the transformation definition and retry."));
            return blocked(document, diagnostics);
        }
    }

    /** Descripción: Ejecuta la operación «exact» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: document: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; readme: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationExportResult exact(TransformationDefinitionDocument document, List<TransformationExportDiagnostic> diagnostics,
            Object plan, String readme) throws JacksonException {
        List<TransformationExportArtifact> artifacts = new ArrayList<>();
        artifacts.add(new TransformationExportArtifact("native/native-plan.json",
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan)));
        artifacts.add(new TransformationExportArtifact("transformation.json",
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document)));
        artifacts.add(new TransformationExportArtifact(wrapperPath(document), wrapper(document)));
        artifacts.add(new TransformationExportArtifact("README.md", "# HYDRA managed native transformation\n\n" + readme));
        return new TransformationExportResult(document.id(), language(), TransformationExportFidelity.EXACT, true, diagnostics, List.copyOf(artifacts));
    }

    /** Descripción: Ejecuta la operación «sourceTypes» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: model: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> sourceTypes(MetaModel model, String expected) {
        LinkedHashSet<String> accepted = new LinkedHashSet<>();
        accepted.add(expected);
        boolean changed;
        do {
            changed = false;
            for (var relation : model.relations()) {
                if (relation.kind() != MetaRelationKind.INHERITANCE || !accepted.contains(relation.targetTypeId())) continue;
                if (accepted.add(relation.sourceTypeId())) changed = true;
            }
        } while (changed);
        return List.copyOf(accepted);
    }

    /** Descripción: Evalúa la condición «hasErrors» sin modificar el estado externo. Entradas: diagnostics: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasErrors(List<TransformationExportDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(item -> "ERROR".equals(item.severity()));
    }

    /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: document: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationExportResult blocked(TransformationDefinitionDocument document, List<TransformationExportDiagnostic> diagnostics) {
        return new TransformationExportResult(document == null ? "" : document.id(), language(), TransformationExportFidelity.SUBSET,
                false, List.copyOf(diagnostics), List.of());
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de NativeBindingPlan. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationExportDiagnostic error(String code, String path, String message, String solution) {
        return new TransformationExportDiagnostic("ERROR", code, path, message, solution);
    }

    /** Portable M2M plan consumed by the official language-specific runtimes. */
    public record NativeM2MPlan(String schemaVersion, String kind, String transformationId, String transformationName,
            String targetMetamodelId, String targetMetamodelFingerprint, List<NativeRulePlan> rules) {}

    /** One already-validated M2M allocation rule. */
    public record NativeRulePlan(String ruleId, List<String> sourceTypeIds, String targetTypeId, List<NativeBindingPlan> bindings) {}

    /** One exact direct/constant attribute binding. */
    public record NativeBindingPlan(String kind, String sourceFeatureId, String targetFeatureId, Object literalValue) {}

    /** Portable guard-free M2T plan with explicit collision semantics. */
    public record NativeM2TPlan(String schemaVersion, String kind, String transformationId, String transformationName,
            List<NativeGenerationRulePlan> generationRules) {}

    /** One exact M2T token rendering rule. */
    public record NativeGenerationRulePlan(String ruleId, String contextTypeId, String outputPathTemplate,
            String contentTemplate, String overwritePolicy) {}
}
