package com.example.editor.integration.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.projection.CanonicalArtifactContent;

class TechnologyAdapterVisualFullStackSupportTests {
    @Test void decodesCanonicalVisualDefinitionBytesAsUtf8() {
        var request = TechnologyAdapterTestFixtures.glspRequest();
        byte[] content = request.require(IntegrationArtifactKind.EDITOR_DEFINITION).content();

        assertThat(TechnologyAdapterVisualFullStackSupport.visualDefinitionJson(request))
                .isEqualTo(new String(content, StandardCharsets.UTF_8))
                .contains("people-editor");
    }

    @Test void rejectsMalformedUtf8BeforeGeneratingHostSources() {
        var source = TechnologyAdapterTestFixtures.glspRequest();
        var artifacts = new ArrayList<>(source.artifacts());
        artifacts.removeIf(value -> value.kind() == IntegrationArtifactKind.EDITOR_DEFINITION);
        artifacts.add(new CanonicalArtifactContent(IntegrationArtifactKind.EDITOR_DEFINITION, "people-editor", "application/json", new byte[]{(byte) 0xc3, 0x28}));
        var malformed = new TechnologyCompilationRequest(source.profile(), artifacts, source.generatorVersion(), source.options());

        assertThatThrownBy(() -> TechnologyAdapterVisualFullStackSupport.visualDefinitionJson(malformed))
                .isInstanceOf(TechnologyCompilationException.class)
                .hasMessageContaining("valid UTF-8");
    }
}
