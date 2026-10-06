package com.example.editor.transformation.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationCollectionMode;
import com.example.editor.transformation.core.TransformationConversionMode;
import com.example.editor.transformation.core.TransformationBindingErrorPolicy;
import com.example.editor.transformation.core.TransformationValuePolicy;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import tools.jackson.databind.ObjectMapper;

class TransformationLanguageAdapterTests {
    private final MetaModel metamodel=ExpressionTestFixtures.metamodel();

    @Test
    void javaM2mExportProvidesExactManagedNativePlan() throws Exception {
        TransformationDefinitionDocument document=m2m(false);
        var result=new JavaTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,metamodel);
        assertThat(result.compatible()).isTrue();
        assertThat(result.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
        assertThat(result.artifacts()).extracting(TransformationExportArtifact::path)
                .contains("native/native-plan.json","transformation.json","src/main/java/generated/HydraTransformation.java");
        String plan=result.artifacts().stream().filter(item->item.path().equals("native/native-plan.json")).findFirst().orElseThrow().content();
        assertThat(new ObjectMapper().readTree(plan).get("rules").get(0).get("bindings").get(0).get("kind").asText()).isEqualTo("DIRECT");
    }

    @Test
    void atlExportsOnlyTheDemonstratedDirectSubset() {
        var result=new AtlTransformationLanguageAdapter().export(m2m(false),metamodel,metamodel);
        assertThat(result.compatible()).isTrue();
        assertThat(result.artifacts()).singleElement().satisfies(file->assertThat(file.content()).contains("rule Person_to_Person","name <- personSource.name"));
    }

    @Test
    void atlBlocksGuardsInsteadOfSilentlyDroppingThem() {
        var result=new AtlTransformationLanguageAdapter().export(m2m(true),metamodel,metamodel);
        assertThat(result.compatible()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("GUARD_NOT_NATIVE");
    }

    @Test
    void xtendExportsExactManagedNativePlanWithoutPretendingTheEngineIsBundled() throws Exception {
        TransformationDefinitionDocument document=m2m(false);
        var result=new XtendTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,metamodel);
        assertThat(result.compatible()).isTrue();
        assertThat(result.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
        assertThat(result.artifacts()).extracting(TransformationExportArtifact::path)
                .contains("native/native-plan.json","transformation.json","src/generated/HydraTransformation.xtend");
        String plan=result.artifacts().stream().filter(item->item.path().equals("native/native-plan.json")).findFirst().orElseThrow().content();
        assertThat(new ObjectMapper().readTree(plan).get("kind").asText()).isEqualTo("M2M");
    }


    @Test
    void pythonAndGoM2mExportsProvideExactManagedNativePlans() throws Exception {
        TransformationDefinitionDocument document=m2m(false);
        var python=new PythonTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,metamodel);
        var go=new GoTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,metamodel);

        assertThat(python.compatible()).isTrue();
        assertThat(go.compatible()).isTrue();
        assertThat(python.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
        assertThat(go.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
        assertThat(python.artifacts()).extracting(TransformationExportArtifact::path)
                .contains("native/native-plan.json","transformation.json","generated/hydra_transformation.py");
        assertThat(go.artifacts()).extracting(TransformationExportArtifact::path)
                .contains("native/native-plan.json","transformation.json","generated/hydra_transformation.go");
    }

    @Test
    void managedNativeCodeAdaptersExportExactGuardFreeM2tPlans() throws Exception {
        TransformationDefinitionDocument document=m2t("generated/{{attr:"+ExpressionTestFixtures.NAME+"}}.txt","Name={{attr:"+ExpressionTestFixtures.NAME+"}}");
        var javaResult=new JavaTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,null);
        var pythonResult=new PythonTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,null);
        var goResult=new GoTransformationLanguageAdapter(new ObjectMapper()).export(document,metamodel,null);
        assertThat(List.of(javaResult,pythonResult,goResult)).allSatisfy(result -> {
            assertThat(result.compatible()).isTrue();
            assertThat(result.fidelity()).isEqualTo(TransformationExportFidelity.EXACT);
            assertThat(result.artifacts()).extracting(TransformationExportArtifact::path).contains("native/native-plan.json","transformation.json");
        });
        String plan=javaResult.artifacts().stream().filter(item->item.path().equals("native/native-plan.json")).findFirst().orElseThrow().content();
        assertThat(new ObjectMapper().readTree(plan).get("kind").asText()).isEqualTo("M2T");
        assertThat(new ObjectMapper().readTree(plan).get("generationRules").get(0).get("overwritePolicy").asText()).isEqualTo("FAIL");
    }

    @Test
    void etlExportsTheSameDemonstratedDirectSubset() {
        var result=new EtlTransformationLanguageAdapter().export(m2m(false),metamodel,metamodel);
        assertThat(result.compatible()).isTrue();
        assertThat(result.artifacts()).singleElement().satisfies(file->assertThat(file.content()).contains("transform personSource : SOURCE!Person","person.name = personSource.name"));
    }

    @Test
    void nativeM2mAdaptersBlockExplicitValueAdaptationInsteadOfApproximatingIt() {
        TransformationDefinitionDocument base=m2m(false);
        TransformationRule rule=base.rules().get(0);
        TransformationBinding original=rule.bindings().get(0);
        TransformationValuePolicy policy=new TransformationValuePolicy(TransformationCollectionMode.PRESERVE,TransformationConversionMode.TO_STRING,Map.of(),TransformationBindingErrorPolicy.FAIL,null);
        TransformationBinding adapted=new TransformationBinding(original.id(),original.targetAlias(),original.targetFeatureId(),original.kind(),original.sourceFeatureId(),original.literalValue(),original.expression(),policy,original.annotations());
        TransformationRule adaptedRule=new TransformationRule(rule.id(),rule.name(),rule.enabled(),rule.sourceEndpointId(),rule.sourceTypeId(),rule.sourceAlias(),rule.guard(),rule.targets(),List.of(adapted),rule.annotations());
        TransformationDefinitionDocument document=new TransformationDefinitionDocument(base.schemaVersion(),base.id(),base.name(),base.kind(),base.sources(),base.targets(),List.of(adaptedRule),base.generationRules(),base.textToModel(),base.executionPolicy(),base.tracePolicy(),base.annotations());
        var atl=new AtlTransformationLanguageAdapter().export(document,metamodel,metamodel);
        var etl=new EtlTransformationLanguageAdapter().export(document,metamodel,metamodel);
        assertThat(atl.compatible()).isFalse();
        assertThat(etl.compatible()).isFalse();
        assertThat(atl.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("VALUE_POLICY_NOT_NATIVE");
        assertThat(etl.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("VALUE_POLICY_NOT_NATIVE");
    }

    @Test
    void eglProjectsTheDemonstratedAttributeTemplateSubset() {
        TransformationDefinitionDocument safe=m2t("generated/{{attr:"+ExpressionTestFixtures.NAME+"}}.txt","Name={{attr:"+ExpressionTestFixtures.NAME+"}}");
        var result=new EglTransformationLanguageAdapter().export(safe,metamodel,null);
        assertThat(result.compatible()).isTrue();
        assertThat(result.artifacts()).extracting(TransformationExportArtifact::path).anyMatch(path->path.endsWith(".egx")).anyMatch(path->path.endsWith(".egl"));
        assertThat(result.artifacts().stream().filter(item->item.path().endsWith(".egl")).findFirst().orElseThrow().content()).contains("[%=e.name%]");
        assertThat(result.artifacts()).allSatisfy(file->assertThat(file.content()).doesNotContain("{{attr:"));
    }

    @Test
    void acceleoProjectsAttributeTokensButRejectsCanonicalIdentityTokens() {
        TransformationDefinitionDocument safe=m2t("generated/{{attr:"+ExpressionTestFixtures.NAME+"}}.txt","Name={{attr:"+ExpressionTestFixtures.NAME+"}}");
        var accepted=new AcceleoTransformationLanguageAdapter().export(safe,metamodel,null);
        assertThat(accepted.compatible()).isTrue();
        assertThat(accepted.artifacts()).singleElement().satisfies(file->assertThat(file.content()).contains("[e.name/]").doesNotContain("{{attr:"));

        var blocked=new AcceleoTransformationLanguageAdapter().export(m2t("generated/{{id}}.txt","{{id}}"),metamodel,null);
        assertThat(blocked.compatible()).isFalse();
        assertThat(blocked.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("CANONICAL_ID_TOKEN_NOT_NATIVE");
    }

    @Test
    void exportDiagnosticNamesTheGuardedRuleAndProvidesAConcreteAlternative() {
        TransformationDefinitionDocument document=m2m(true);
        TransformationExportResult raw=new AtlTransformationLanguageAdapter().export(document,metamodel,metamodel);
        TransformationExportResult enriched=TransformationExportDiagnosticEnricher.enrich(raw,document,metamodel,metamodel,com.example.editor.transformation.core.TransformationExecutionDirection.FORWARD);
        TransformationExportDiagnostic diagnostic=enriched.diagnostics().stream().filter(item->item.code().equals("GUARD_NOT_NATIVE")).findFirst().orElseThrow();
        assertThat(diagnostic.message()).contains("Person to Person").contains("guard");
        assertThat(diagnostic.solution()).contains("canonical runtime").contains("Do not export and silently ignore");
    }

    @Test
    void exportDiagnosticExplainsTheActualBindingAndValuePolicyInsteadOfAStaticError() {
        TransformationDefinitionDocument base=m2m(false);
        TransformationRule rule=base.rules().get(0);
        TransformationBinding original=rule.bindings().get(0);
        TransformationValuePolicy policy=new TransformationValuePolicy(TransformationCollectionMode.PRESERVE,TransformationConversionMode.TO_STRING,Map.of(),TransformationBindingErrorPolicy.FAIL,null);
        TransformationBinding adapted=new TransformationBinding(original.id(),original.targetAlias(),original.targetFeatureId(),original.kind(),original.sourceFeatureId(),original.literalValue(),original.expression(),policy,original.annotations());
        TransformationRule adaptedRule=new TransformationRule(rule.id(),rule.name(),rule.enabled(),rule.sourceEndpointId(),rule.sourceTypeId(),rule.sourceAlias(),rule.guard(),rule.targets(),List.of(adapted),rule.annotations());
        TransformationDefinitionDocument document=new TransformationDefinitionDocument(base.schemaVersion(),base.id(),base.name(),base.kind(),base.sources(),base.targets(),List.of(adaptedRule),base.generationRules(),base.textToModel(),base.executionPolicy(),base.tracePolicy(),base.annotations());
        TransformationExportResult raw=new AtlTransformationLanguageAdapter().export(document,metamodel,metamodel);
        TransformationExportResult enriched=TransformationExportDiagnosticEnricher.enrich(raw,document,metamodel,metamodel,com.example.editor.transformation.core.TransformationExecutionDirection.FORWARD);
        TransformationExportDiagnostic diagnostic=enriched.diagnostics().stream().filter(item->item.code().equals("VALUE_POLICY_NOT_NATIVE")).findFirst().orElseThrow();
        assertThat(diagnostic.message()).contains("binding:name").contains("TO_STRING");
        assertThat(diagnostic.solution()).contains("source/target type and multiplicity").contains("canonical runtime");
    }

    private TransformationDefinitionDocument m2m(boolean guarded){
        TransformationBinding binding=new TransformationBinding("binding:name","person",ExpressionTestFixtures.NAME,TransformationBindingKind.DIRECT,ExpressionTestFixtures.NAME,null,null,null,Map.of());
        TransformationRule rule=new TransformationRule("rule:person","Person to Person",true,"source",ExpressionTestFixtures.PERSON,"personSource",
                guarded?ExpressionTestFixtures.positiveAge():null,List.of(new TransformationTargetPattern("target:person","target",ExpressionTestFixtures.PERSON,"person",Map.of())),List.of(binding),Map.of());
        return new TransformationDefinitionDocument("1.0","transformation:export","Export",TransformationKind.M2M,
                List.of(new TransformationEndpoint("source",TransformationEndpointRole.SOURCE,ExpressionTestFixtures.METAMODEL_ID,"","source",true,Map.of())),
                List.of(new TransformationEndpoint("target",TransformationEndpointRole.TARGET,ExpressionTestFixtures.METAMODEL_ID,"","target",true,Map.of())),
                List.of(rule),List.of(),null,null,null,Map.of());
    }

    private TransformationDefinitionDocument m2t(String path,String content){
        TransformationGenerationRule rule=new TransformationGenerationRule("gen:person","Generate person",true,"source",ExpressionTestFixtures.PERSON,null,path,content,null,Map.of());
        return new TransformationDefinitionDocument("1.0","transformation:m2t","Generate",TransformationKind.M2T,
                List.of(new TransformationEndpoint("source",TransformationEndpointRole.SOURCE,ExpressionTestFixtures.METAMODEL_ID,"","source",true,Map.of())),
                List.of(),List.of(),List.of(rule),null,null,null,Map.of());
    }
}
