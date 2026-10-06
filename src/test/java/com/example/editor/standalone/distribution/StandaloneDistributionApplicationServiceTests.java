package com.example.editor.standalone.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class StandaloneDistributionApplicationServiceTests {
    @TempDir Path root;

    @Test
    void materializesValidatesAndPackagesOneIndependentEditorBelowTheConfiguredRoot() throws Exception{
        StandaloneDistributionApplicationService service = service();
        StandaloneDistributionRequest request = request("people-editor");
        StandaloneDistributionPreparationResult preparation = service.materialize(request);
        assertThat(preparation.materialization().writtenPaths()).contains("public/manifest.webmanifest");
        Files.writeString(root.resolve("people-editor/index.html"), "<main>People editor</main>");
        assertThat(service.validate(request).valid()).isTrue();
        StandaloneDistributionDownload download = service.packageEditor(request);
        assertThat(download.fileName()).isEqualTo("people-editor-1.0.0.zip");
        assertThat(download.content()).isNotEmpty();
        assertThat(download.sha256()).hasSize(64);
    }

    @Test
    void rejectsAbsoluteAndTraversalDirectoriesBeforeAnyWrite(){
        assertThatThrownBy(() -> service().materialize(request("../outside"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("safe relative");
        assertThatThrownBy(() -> service().materialize(request("/absolute"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("safe relative");
    }

    private StandaloneDistributionApplicationService service(){
        return new StandaloneDistributionApplicationService(new StandaloneDistributionMaterializer(),
                new StandaloneDistributionValidationService(JsonMapper.builder().build()), new StandaloneDistributionPackager(), root);
    }
    private StandaloneDistributionRequest request(String directory){
        return new StandaloneDistributionRequest(directory, new StandaloneDistributionProfile("People Editor", "1.0.0",
                Set.of(StandaloneDistributionTarget.PWA), true, List.of(".xmi"), "stable"));
    }
}
