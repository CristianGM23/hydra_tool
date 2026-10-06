package com.example.editor.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationSupportLevel;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterRegistry;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.verification.TechnologyProjectStructureValidator;
import com.example.editor.integration.verification.TechnologyProjectStructureValidatorRegistry;
import com.example.editor.integration.verification.TechnologyValidationStepResult;

class TechnologyWorkflowServiceTests {
    @Test void distinguishesDeclaredOperationalAndProjectionTargets() {
        var service = service(List.of(adapter(IntegrationTechnology.XTEXT)), List.of(validator(IntegrationTechnology.XTEXT)));
        var xtext = service.descriptor(IntegrationTechnology.XTEXT);
        var antlr = service.descriptor(IntegrationTechnology.ANTLR);
        var plantUml = service.descriptor(IntegrationTechnology.PLANTUML);

        assertThat(xtext.adapterImplemented()).isTrue();
        assertThat(xtext.validatorImplemented()).isTrue();
        assertThat(xtext.maturity()).isIn(TechnologyWorkflowMaturity.OPERATIONAL, TechnologyWorkflowMaturity.FULL_WORKFLOW);
        assertThat(antlr.maturity()).isEqualTo(TechnologyWorkflowMaturity.DECLARED_ONLY);
        assertThat(antlr.blockers()).contains("No executable TechnologyAdapter is registered for this technology.");
        assertThat(plantUml.maturity()).isEqualTo(TechnologyWorkflowMaturity.PROJECTION_ONLY);
        assertThat(stage(plantUml, TechnologyWorkflowStage.APPLY).applicable()).isFalse();
        assertThat(stage(plantUml, TechnologyWorkflowStage.GENERATE).support()).isEqualTo(IntegrationSupportLevel.FULL);
    }

    @Test void reportsMissingGeneratedProjectValidationWithoutPretendingTheWorkflowIsComplete() {
        var service = service(List.of(adapter(IntegrationTechnology.LANGIUM)), List.of());
        var value = service.descriptor(IntegrationTechnology.LANGIUM);
        assertThat(value.maturity()).isEqualTo(TechnologyWorkflowMaturity.PARTIAL);
        assertThat(stage(value, TechnologyWorkflowStage.GENERATE).support()).isEqualTo(IntegrationSupportLevel.FULL);
        assertThat(stage(value, TechnologyWorkflowStage.VERIFY).support()).isEqualTo(IntegrationSupportLevel.UNSUPPORTED);
        assertThat(value.blockers()).contains("No generated-project validator is registered for this technology.");
    }

    @Test void externalExecutionIsExplicitAndDoesNotCountAsNativeHydraExecution() {
        var service = service(List.of(adapter(IntegrationTechnology.ECLIPSE_GLSP)), List.of(validator(IntegrationTechnology.ECLIPSE_GLSP)));
        var value = service.descriptor(IntegrationTechnology.ECLIPSE_GLSP);
        var execute = stage(value, TechnologyWorkflowStage.EXECUTE);
        assertThat(execute.support()).isEqualTo(IntegrationSupportLevel.EXTERNAL);
        assertThat(execute.action()).isEqualTo(TechnologyWorkflowAction.RUN_NATIVE_HOST);
        assertThat(value.recommendations()).anyMatch(text -> text.contains("external toolchain"));
    }

    @Test void scoresOnlyApplicableStagesAndKeepsScoresBounded() {
        var service = service(List.of(adapter(IntegrationTechnology.EMF)), List.of(validator(IntegrationTechnology.EMF)));
        for(var descriptor : service.descriptors()) {
            assertThat(descriptor.parityScore()).isBetween(0, 100);
            assertThat(descriptor.stages()).extracting(TechnologyWorkflowStageDescriptor::stage)
                    .containsExactly(TechnologyWorkflowStage.values());
        }
    }

    private TechnologyWorkflowService service(List<TechnologyAdapter> adapters, List<TechnologyProjectStructureValidator> validators) {
        return new TechnologyWorkflowService(new TechnologyAdapterRegistry(adapters),
                new TechnologyProjectStructureValidatorRegistry(validators), new TechnologyWorkflowProperties());
    }

    private TechnologyWorkflowStageDescriptor stage(TechnologyWorkflowDescriptor descriptor, TechnologyWorkflowStage stage) {
        return descriptor.stages().stream().filter(value -> value.stage() == stage).findFirst().orElseThrow();
    }

    private TechnologyAdapter adapter(IntegrationTechnology technology) {
        return new TechnologyAdapter() {
            @Override public IntegrationTechnology technology(){return technology;}
            @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.of(IntegrationArtifactKind.METAMODEL);}
            @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){throw new UnsupportedOperationException("not used");}
        };
    }

    private TechnologyProjectStructureValidator validator(IntegrationTechnology technology) {
        return new TechnologyProjectStructureValidator() {
            @Override public IntegrationTechnology technology(){return technology;}
            @Override public List<TechnologyValidationStepResult> validate(Path projectRoot, TechnologyCompilationResult compilation){return List.of();}
        };
    }
}
