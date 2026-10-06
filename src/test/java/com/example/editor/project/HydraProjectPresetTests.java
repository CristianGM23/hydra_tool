package com.example.editor.project;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HydraProjectPresetTests {
    @Test void textualPresetIsModularAndIncludesRequiredDependencies() {
        Set<ProjectCapability> capabilities=ProjectPreset.TEXTUAL_DSL.capabilities();
        assertThat(capabilities).contains(ProjectCapability.GRAMMAR_DESIGN,ProjectCapability.TEXTUAL_MODELING,ProjectCapability.TEXTUAL_EDITOR,ProjectCapability.SYNTAX_STYLING,ProjectCapability.CONTEXTUAL_INFORMATION);
        assertThat(capabilities).doesNotContain(ProjectCapability.TRANSFORMATION_M2M,ProjectCapability.GAMIFICATION);
    }

    @Test void quickFixAutomaticallyRequiresValidation() {
        assertThat(ProjectCapabilityDependencies.resolve(Set.of(ProjectCapability.QUICK_FIXES)))
                .contains(ProjectCapability.QUICK_FIXES,ProjectCapability.VALIDATIONS);
    }

    @Test void presetIsCreationMetadataNotARestriction() {
        ProjectDescriptor descriptor=new ProjectDescriptor("1.1","dsl","DSL","",Instant.now(),Instant.now(),ProjectStorageMode.LOCAL_DIRECTORY,
                ProjectLayout.standard(),ProjectPreset.TEXTUAL_DSL.capabilities(),java.util.Map.of(),ProjectPreset.TEXTUAL_DSL,ProjectExperienceMode.GUIDED,new ProjectPrimaryArtifact("","",""));
        ProjectDescriptor extended=descriptor.configure(Set.of(ProjectCapability.GRAMMAR_DESIGN,ProjectCapability.TRANSFORMATION_M2M),ProjectExperienceMode.FREE,null);
        assertThat(extended.createdFromPreset()).isEqualTo(ProjectPreset.TEXTUAL_DSL);
        assertThat(extended.enabledCapabilities()).contains(ProjectCapability.TRANSFORMATION_M2M,ProjectCapability.METAMODEL_DESIGN);
        assertThat(extended.experienceMode()).isEqualTo(ProjectExperienceMode.FREE);
    }

    @Test void emptyPresetCreatesNoLogicalCapabilityRoots() {
        assertThat(ProjectLayout.standard().logicalRoots(ProjectPreset.EMPTY.capabilities())).isEmpty();
    }
}
