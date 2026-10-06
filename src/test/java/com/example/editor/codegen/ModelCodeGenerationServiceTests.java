package com.example.editor.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.CodeGenerationFeatureOptions;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.json.JsonMapper;

class ModelCodeGenerationServiceTests {
    private final ModelCodeGenerationService service = new ModelCodeGenerationService(JsonMapper.builder().findAndAddModules().build());

    @Test
    void javaCanGenerateTestsAndConventionalHelpersAsOnePortableBundle() throws IOException {
        var features = new CodeGenerationFeatureOptions(true, true, true, true, true);
        var artifact = service.generate(metamodel(), model(), "java",
                new ModelCodeGenerationOptions("example.generated", "PeopleModel", true, features));

        assertThat(artifact.mediaType()).isEqualTo("application/zip");
        assertThat(artifact.fileName()).isEqualTo("PeopleModel-java.zip");
        Map<String,String> files = unzip(artifact.content());
        assertThat(files).containsKeys("src/main/java/example/generated/PeopleModel.java", "src/test/java/example/generated/PeopleModelTest.java");
        String source = files.get("src/main/java/example/generated/PeopleModel.java");
        assertThat(source).contains("getId()", "setId(String id)", "compareTo(Element other)", "toString()", "equals(Object other)", "hashCode()");
        assertThat(files.get("src/test/java/example/generated/PeopleModelTest.java")).contains("Generated model collections must exist");
    }

    @Test
    void portableProjectionRejectsSourceOnlyFeaturesInsteadOfIgnoringThem() {
        var features = new CodeGenerationFeatureOptions(false, false, false, false, true);
        assertThatThrownBy(() -> service.generate(metamodel(), model(), "json",
                new ModelCodeGenerationOptions("example.generated", "PeopleModel", true, features)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EQUALITY")
                .hasMessageContaining("json");
    }

    @Test
    void traceabilityIsExplicitAndOptionalWithoutRemovingSemanticModelIdentifiers() {
        var on = service.generate(metamodel(), model(), "xml",
                new ModelCodeGenerationOptions("example.generated", "PeopleModel", true, CodeGenerationFeatureOptions.none()));
        var off = service.generate(metamodel(), model(), "xml",
                new ModelCodeGenerationOptions("example.generated", "PeopleModel", false, CodeGenerationFeatureOptions.none()));
        String traced = new String(on.content(), StandardCharsets.UTF_8);
        String untraced = new String(off.content(), StandardCharsets.UTF_8);
        assertThat(traced).contains("hydraMetamodelId=\"people\"", "hydraModelId=\"people-model\"");
        assertThat(untraced).doesNotContain("hydraMetamodelId", "hydraModelId");
        assertThat(untraced).contains("xmi:id=\"person-one\"", "classId=\"Person\"");

        var javaOff = service.generate(metamodel(), model(), "java",
                new ModelCodeGenerationOptions("example.generated", "PeopleModel", false, CodeGenerationFeatureOptions.none()));
        assertThat(new String(javaOff.content(), StandardCharsets.UTF_8)).doesNotContain("HYDRA trace:").contains("person-one", "Person");
    }

    @Test
    void previewAndGenerationCanBeScopedToAStableElementSelection() {
        var meta = metamodel();
        var first = new CanonicalModelElement("person-one", "Person", Map.of("name", "Alice"), Map.of());
        var second = new CanonicalModelElement("person-two", "Person", Map.of("name", "Bob"), Map.of());
        var document = new CanonicalModelDocument("1.0", "people-model", "people", "", "PeopleModel", List.of("person-one", "person-two"), List.of(first, second), List.of(), Map.of());
        var preview = service.preview(meta, document, "java", new ModelCodeGenerationOptions("example.generated", "PeopleModel", true, CodeGenerationFeatureOptions.none()), List.of("person-two"));
        assertThat(preview.selectedElementCount()).isEqualTo(1);
        assertThat(preview.content()).contains("person-two", "Bob").doesNotContain("person-one", "Alice");
        var artifact = service.generate(meta, document, "json", ModelCodeGenerationOptions.defaults(), List.of("person-one"));
        assertThat(new String(artifact.content(), StandardCharsets.UTF_8)).contains("person-one").doesNotContain("person-two");
    }

    @Test
    void selectedGenerationRejectsUnknownElementIdsInsteadOfSilentlyExpandingScope() {
        assertThatThrownBy(() -> service.generate(metamodel(), model(), "json", ModelCodeGenerationOptions.defaults(), List.of("missing-element")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing-element");
    }

    @Test
    void sourceTargetsAdvertiseFeaturesWhilePortableTargetsRemainClean() {
        var targets = ModelCodeGenerationTarget.catalog();
        assertThat(targets.stream().filter(target -> target.id().equals("java")).findFirst().orElseThrow().supportedFeatures())
                .containsExactlyInAnyOrder(ModelCodeGenerationFeature.values());
        assertThat(targets.stream().filter(target -> target.id().equals("xmi")).findFirst().orElseThrow().supportedFeatures()).isEmpty();
    }

    private MetaModel metamodel() {
        return new MetaModel("2.1", "people", "People", List.of(new MetaClass("Person", "Person", MetaClassKind.CLASS, List.of(), List.of(), Map.of())), List.of(), List.of(), List.of(), Map.of());
    }

    private CanonicalModelDocument model() {
        var person = new CanonicalModelElement("person-one", "Person", Map.of("name", "Alice"), Map.of());
        return new CanonicalModelDocument("1.0", "people-model", "people", "", "PeopleModel", List.of("person-one"), List.of(person), List.of(), Map.of());
    }

    private Map<String,String> unzip(byte[] bytes) throws IOException {
        Map<String,String> files = new LinkedHashMap<>();
        try(var input = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for(var entry=input.getNextEntry(); entry!=null; entry=input.getNextEntry()) files.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return files;
    }
}
