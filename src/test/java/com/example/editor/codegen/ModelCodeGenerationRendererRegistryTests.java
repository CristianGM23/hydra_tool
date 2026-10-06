package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import tools.jackson.databind.json.JsonMapper;

class ModelCodeGenerationRendererRegistryTests {
    @Test
    void builtInRegistryCoversEveryPublicTargetExactlyOnce() {
        new ModelCodeGenerationService(JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    void registryRejectsDuplicateTargetOwnershipBeforeRuntime() {
        ModelCodeGenerationRenderer duplicate = new ModelCodeGenerationRenderer() {
            @Override public Set<String> targetIds() { return Set.of("java"); }
            @Override public Set<ModelCodeGenerationFeature> supportedFeatures(String targetId) { return Set.of(ModelCodeGenerationFeature.values()); }
            @Override public GeneratedModelArtifact generate(String targetId, ModelCodeGenerationTarget target, MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) { throw new UnsupportedOperationException(); }
        };
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        assertThatThrownBy(() -> new ModelCodeGenerationRendererRegistry(List.of(new SourceModelCodeGenerationRenderer(mapper), new ProjectionModelCodeGenerationRenderer(mapper), duplicate)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate model code generation renderer").hasMessageContaining("java");
    }
}
