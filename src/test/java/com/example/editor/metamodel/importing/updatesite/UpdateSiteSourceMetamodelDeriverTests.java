package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClassKind;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteSourceMetamodelDeriverTests {
    @Test
    void derivesClassesRelationsCollectionsAndOperationsFromJavaAndKotlinAsFallbackEvidence() {
        byte[] java = ("public abstract class Project {\n"
                + "  private java.util.List<Task> tasks;\n"
                + "  public Task find(String name) { return null; }\n"
                + "}\n").getBytes(StandardCharsets.UTF_8);
        byte[] kotlin = ("class Task { var name: String = \"\"\n fun complete(): Boolean = true }").getBytes(StandardCharsets.UTF_8);
        var repository = new UpdateSiteRepositoryContent(Map.of("src/Project.java", java, "src/Task.kt", kotlin), "f", java.length + kotlin.length);

        var model = new UpdateSiteSourceMetamodelDeriver().derive(repository, "recovered", "src/Project.java");

        assertThat(model.annotations()).containsEntry("hydra.recovery.inference", "JAVA_KOTLIN_SOURCE");
        assertThat(model.classes()).extracting(value -> value.name()).contains("Project", "Task");
        assertThat(model.classes()).filteredOn(value -> value.name().equals("Project")).singleElement()
                .satisfies(value -> {
                    assertThat(value.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
                    assertThat(value.operations()).extracting(item -> item.name()).contains("find");
                });
        assertThat(model.relations()).extracting(value -> value.name()).contains("tasks");
    }
}
