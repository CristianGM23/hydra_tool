package com.example.editor.rc1;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionMode;

/** Regresiones de los proyectos de referencia que forman parte del contrato RC1. */
class Rc1ReferenceProjectTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shipsBasicIntermediateAndAdvancedProjectsWithAllRequiredArtifacts() throws Exception {
        for (String id : List.of("library-basic", "commerce-intermediate", "sync-advanced")) {
            Path root = Path.of("reference-projects", id);
            JsonNode manifest = mapper.readTree(Files.readString(root.resolve("reference-project.json")));
            assertThat(manifest.get("id").asString()).isEqualTo(id);
            assertThat(manifest.get("expectedArtifacts").size()).isGreaterThanOrEqualTo(3);
            for (JsonNode artifact : manifest.get("expectedArtifacts")) {
                if (artifact.get("required").asBoolean()) {
                    assertThat(root.resolve(artifact.get("path").asString())).exists();
                }
            }
        }
    }

    @Test
    void advancedProjectKeepsExplicitForwardAndReverseRules() throws Exception {
        Path path = Path.of("reference-projects", "sync-advanced", "artifacts", "people-contacts.transformation.json");
        JsonNode transformation = mapper.readTree(Files.readString(path));
        assertThat(transformation.get("directionality").asString()).isEqualTo("BIDIRECTIONAL");
        assertThat(transformation.get("executionPolicy").get("mode").asString()).isEqualTo("INCREMENTAL");
        assertThat(transformation.get("rules").get(0).get("direction").asString()).isEqualTo("FORWARD");
        assertThat(transformation.get("rules").get(1).get("direction").asString()).isEqualTo("REVERSE");
    }

    @Test
    void deserializesAdvancedIncrementalTransformation() throws Exception {
        Path path = Path.of("reference-projects", "sync-advanced", "artifacts", "people-contacts.transformation.json");
        TransformationDefinitionDocument transformation = mapper.readValue(Files.readString(path), TransformationDefinitionDocument.class);

        assertThat(transformation.executionPolicy().mode()).isEqualTo(TransformationExecutionMode.INCREMENTAL);
    }

    @Test
    void referenceProjectsDoNotShipSecretLikeFiles() throws Exception {
        try (var files = Files.walk(Path.of("reference-projects"))) {
            assertThat(files.filter(Files::isRegularFile).map(path -> path.getFileName().toString().toLowerCase()).toList())
                    .noneMatch(name -> name.equals(".env") || name.equals("credentials.json") || name.endsWith(".pem") || name.endsWith(".key") || name.equals("id_rsa"));
        }
    }
}
