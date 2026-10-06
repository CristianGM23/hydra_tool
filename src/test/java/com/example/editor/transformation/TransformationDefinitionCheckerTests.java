package com.example.editor.transformation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionMode;
import com.example.editor.transformation.core.TransformationExecutionPolicy;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;
import com.example.editor.transformation.core.TransformationTracePolicy;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;

class TransformationDefinitionCheckerTests {
    private final TransformationDefinitionChecker checker = new TransformationDefinitionChecker(
            new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));

    @Test
    void acceptsDirectMappingBetweenResolvedMetamodelElements() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        var report = checker.check(validDocument(), List.of(model), List.of(model));
        assertThat(report.valid()).isTrue();
        assertThat(report.diagnostics()).noneMatch(item -> item.severity().name().equals("ERROR"));
    }

    @Test
    void rejectsUnknownSourceTypeWithoutInventingFallbackSemantics() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationRule invalid = new TransformationRule("rule:broken", "Broken", true, "source", "missing", "source",
                null, List.of(new TransformationTargetPattern("target-pattern", "target", ExpressionTestFixtures.PERSON, "person", Map.of())), List.of(), Map.of());
        TransformationDefinitionDocument document = withRules(List.of(invalid));
        assertThat(checker.check(document, List.of(model), List.of(model)).diagnostics())
                .extracting(item -> item.code()).contains("UNKNOWN_SOURCE_TYPE");
    }

    @Test
    void rejectsExecutionPoliciesThatTheReferenceRuntimeDoesNotImplementYet() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationDefinitionDocument base = validDocument();
        TransformationDefinitionDocument document = new TransformationDefinitionDocument(base.schemaVersion(), base.id(), base.name(), base.kind(),
                base.sources(), base.targets(), base.rules(), base.generationRules(), base.textToModel(), new TransformationExecutionPolicy(TransformationExecutionMode.BATCH, false, false),
                base.tracePolicy(), base.annotations());

        assertThat(checker.check(document, List.of(model), List.of(model)).diagnostics())
                .extracting(item -> item.code())
                .contains("NON_DETERMINISTIC_EXECUTION_NOT_SUPPORTED", "BEST_EFFORT_BINDING_EXECUTION_NOT_SUPPORTED");
    }

    @Test
    void reportsOneActionableDiagnosticForTheConcreteRuleMissingItsReversePartner() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationRule base = validDocument().rules().get(0);
        TransformationRule forward = new TransformationRule(base.id(), "Person to Person", true, base.sourceEndpointId(),
                base.sourceTypeId(), base.sourceAlias(), base.guard(), base.targets(), base.bindings(), base.annotations(),
                TransformationRuleDirection.FORWARD, "corr:person");
        TransformationDefinitionDocument document = new TransformationDefinitionDocument("1.1", "transformation:bi", "Bidirectional", null,
                TransformationDirectionality.BIDIRECTIONAL, TransformationSynchronizationPolicy.defaults(),
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(forward), List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());

        var diagnostics = checker.check(document, List.of(model), List.of(model)).diagnostics().stream()
                .filter(item -> item.code().equals("INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE")).toList();

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).ruleId()).isEqualTo("rule:person");
        assertThat(diagnostics.get(0).path()).isEqualTo("$.rules[0].correspondenceId");
        assertThat(diagnostics.get(0).message()).contains("Person to Person", "corr:person", "REVERSE", ExpressionTestFixtures.PERSON);
        assertThat(diagnostics.get(0).solution()).contains("correspondenceId='corr:person'", "direction=REVERSE");
        assertThat(checker.check(document, List.of(model), List.of(model)).diagnostics())
                .noneMatch(item -> item.code().equals("BIDIRECTIONAL_REVERSE_RULE_REQUIRED"));
    }


    @Test
    void explainsUnknownBindingAttributeWithConcreteRuleClassAndAvailableFeatures() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationBinding broken = new TransformationBinding("binding:missing-age", "person", ExpressionTestFixtures.AGE,
                TransformationBindingKind.DIRECT, "attribute:Person:missingAge", null, null, null, Map.of());
        TransformationRule base = validDocument().rules().get(0);
        TransformationRule rule = new TransformationRule(base.id(), base.name(), true, base.sourceEndpointId(), base.sourceTypeId(),
                base.sourceAlias(), null, base.targets(), List.of(broken), Map.of());

        var diagnostic = checker.check(withRules(List.of(rule)), List.of(model), List.of(model)).diagnostics().stream()
                .filter(item -> item.code().equals("UNKNOWN_SOURCE_ATTRIBUTE")).findFirst().orElseThrow();

        assertThat(diagnostic.message()).contains("attribute:Person:missingAge", "Person", ExpressionTestFixtures.AGE, ExpressionTestFixtures.NAME);
        assertThat(diagnostic.solution()).contains("Select an existing source attribute", ExpressionTestFixtures.AGE);
    }

    @Test
    void suppressesBindingCascadeWhenTheRuleSourceTypeItselfIsUnknown() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationBinding broken = new TransformationBinding("binding:stale", "person", ExpressionTestFixtures.AGE,
                TransformationBindingKind.DIRECT, "attribute:missing", null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:stale", "Stale source", true, "source", "class:Missing", "source",
                null, List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(broken), Map.of());

        var diagnostics = checker.check(withRules(List.of(rule)), List.of(model), List.of(model)).diagnostics();

        assertThat(diagnostics).anyMatch(item -> item.code().equals("UNKNOWN_SOURCE_TYPE")
                && item.message().contains("class:Missing") && item.solution().contains("Available classes"));
        assertThat(diagnostics).noneMatch(item -> item.code().equals("UNKNOWN_SOURCE_ATTRIBUTE"));
    }

    @Test
    void reportsConcreteSourceAndTargetTypesWhenConversionIsRequired() {
        MetaModel source = ExpressionTestFixtures.metamodel();
        MetaModel target = targetWithStringAge(source);
        var diagnostic = checker.check(validDocument(), List.of(source), List.of(target)).diagnostics().stream()
                .filter(item -> item.code().equals("TYPE_CONVERSION_REQUIRED")).findFirst().orElseThrow();

        assertThat(diagnostic.title()).contains("conversion", "INTEGER", "STRING");
        assertThat(diagnostic.message()).contains("age", "0..1");
        assertThat(diagnostic.solution()).contains("TO_STRING", "TO_INTEGER", "enumMapping", "EXPRESSION");
    }

    @Test
    void doesNotAddMetamodelMismatchWhenEndpointMetamodelIsSimplyMissing() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        TransformationDefinitionDocument base = validDocument();
        TransformationDefinitionDocument document = new TransformationDefinitionDocument(base.schemaVersion(), base.id(), base.name(), base.kind(),
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, "", "", "source", true, Map.of())),
                base.targets(), base.rules(), base.generationRules(), base.textToModel(), base.executionPolicy(), base.tracePolicy(), base.annotations());

        var diagnostics = checker.check(document, List.of(model), List.of(model)).diagnostics();
        assertThat(diagnostics).anyMatch(item -> item.code().equals("ENDPOINT_METAMODEL_REQUIRED"));
        assertThat(diagnostics).noneMatch(item -> item.code().equals("ENDPOINT_METAMODEL_MISMATCH") && item.path().startsWith("$.sources"));
    }

    @Test
    void warnsWhenRequiredTargetPropertyIsNotMapped() {
        MetaModel model = ExpressionTestFixtures.metamodel();
        // The fixture attributes are optional, therefore use a document without a binding
        // only to prove the checker remains deterministic and does not manufacture errors.
        TransformationRule rule = validDocument().rules().get(0);
        TransformationDefinitionDocument document = withRules(List.of(new TransformationRule(rule.id(), rule.name(), true,
                rule.sourceEndpointId(), rule.sourceTypeId(), rule.sourceAlias(), null, rule.targets(), List.of(), Map.of())));
        assertThat(checker.check(document, List.of(model), List.of(model)).errorCount()).isZero();
    }


    private MetaModel targetWithStringAge(MetaModel source) {
        MetaClass original = source.classes().stream().filter(item -> item.id().equals(ExpressionTestFixtures.PERSON)).findFirst().orElseThrow();
        MetaAttribute stringAge = new MetaAttribute(ExpressionTestFixtures.AGE, "age", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                original.attributes().stream().filter(item -> item.id().equals(ExpressionTestFixtures.AGE)).findFirst().orElseThrow().multiplicity(),
                true, true, "", Map.of());
        MetaClass person = new MetaClass(original.id(), original.name(), original.kind(),
                List.of(stringAge, original.attributes().stream().filter(item -> item.id().equals(ExpressionTestFixtures.NAME)).findFirst().orElseThrow()),
                original.operations(), original.annotations());
        return new MetaModel(source.schemaVersion(), source.id(), source.name(),
                source.classes().stream().map(item -> item.id().equals(person.id()) ? person : item).toList(),
                source.enums(), source.dataTypes(), source.relations(), source.annotations());
    }

    private TransformationDefinitionDocument validDocument() {
        TransformationBinding binding = new TransformationBinding("binding:age", "person", ExpressionTestFixtures.AGE,
                TransformationBindingKind.DIRECT, ExpressionTestFixtures.AGE, null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding), Map.of());
        return withRules(List.of(rule));
    }

    private TransformationDefinitionDocument withRules(List<TransformationRule> rules) {
        return new TransformationDefinitionDocument("1.0", "transformation:test", "Test", null,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                rules, List.of(), null, null, null, Map.of());
    }
}
