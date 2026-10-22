package com.example.editor.definition.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * model_editor_visual_import.as / .d1 - associating an imported visual (Sirius) definition with its metamodel and the
 * preview-versus-confirm rule.
 *
 * Full Spring context with the real {@link VisualImportMetamodelResolver}, {@link com.example.editor.metamodel.importing.MetamodelImportService},
 * {@link VisualValidationAssociationService} and {@link EditorDefinitionApplicationService} over the real sirius-complete
 * fixture. The oracle is the content of the {@code @TempDir} storage directories, read directly from disk. Tests are
 * ordered because the last one persists the fixture metamodel.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VisualImportMetamodelAssociationRealStackIntegrationTests {

    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    private static final String ODESIGN = "com.hydra.sirius.fixture.design/description/hydratest.odesign";
    private static final String SERVICES = "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java";

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private VisualImportMetamodelResolver resolver;
    @Autowired private VisualDefinitionImportService importService;
    @Autowired private VisualValidationAssociationService associations;
    @Autowired private EditorDefinitionApplicationService editors;
    @Autowired private CanonicalMetamodelApplicationService metamodels;
    @Autowired private JsonMapper mapper;

    private static byte[] resource(String relative) throws IOException {
        try (InputStream in = VisualImportMetamodelAssociationRealStackIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            assertThat(in).as("fixture " + relative).isNotNull();
            return in.readAllBytes();
        }
    }

    private static MockMultipartFile upload(String field, String relative, String contentType) throws IOException {
        return new MockMultipartFile(field, relative.substring(relative.lastIndexOf('/') + 1), contentType, resource(relative));
    }

    private static byte[] zip(String entryName, byte[]... contents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            int index = 0;
            for (byte[] content : contents) {
                zip.putNextEntry(new ZipEntry(index == 0 ? entryName : index + "-" + entryName));
                zip.write(content);
                zip.closeEntry();
                index++;
            }
        }
        return out.toByteArray();
    }

    private static List<Path> filesStartingWith(Path directory, String prefix) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().startsWith(prefix)).toList();
        }
    }

    @Test
    @Order(1)
    @DisplayName("model_editor_visual_import.as: EXISTING mode checks the representation against the metamodel already stored in HYDRA")
    void existingModeUsesTheStoredMetamodel() {
        metamodels.create(CanonicalMetamodelTestFixtures.valid("visual-existing"));

        var resolution = resolver.resolve("EXISTING", "visual-existing", List.of(), List.of());

        assertThat(resolution.metamodel().id()).isEqualTo("visual-existing");
        assertThat(resolution.association().mode()).isEqualTo("EXISTING");
        assertThat(resolution.association().alreadyPersisted()).isTrue();
        assertThat(resolution.association().detectedFormat()).isEqualTo("HYDRA");
        assertThatThrownBy(() -> resolver.resolve("EXISTING", "visual-missing", List.of(), List.of()))
                .isInstanceOf(com.example.editor.metamodel.application.MetamodelApplicationException.class);
    }

    @Test
    @Order(2)
    @DisplayName("model_editor_visual_import.as / d1: EXTERNAL (.ecore) and EMBEDDED (ZIP) modes analyse the metamodel without writing anything")
    void externalAndEmbeddedModesAreSideEffectFree() throws Exception {
        byte[] ecore = resource(ECORE);

        var external = resolver.resolve("EXTERNAL", "", List.of(),
                List.of(new MockMultipartFile("metamodelResources", "hydratest.ecore", "application/xml", ecore)));
        String id = external.metamodel().id();
        assertThat(external.association().mode()).isEqualTo("EXTERNAL");
        assertThat(external.association().alreadyPersisted()).isFalse();
        assertThat(external.association().detectedFormat()).isEqualTo("ecore");
        assertThat(external.metamodel().classes()).extracting(c -> c.name()).contains("Connection");

        var embedded = resolver.resolve("EMBEDDED", "", List.of(
                new MockMultipartFile("visual", "bundle.zip", "application/zip", zip("hydratest.ecore", ecore))), List.of());
        assertThat(embedded.association().mode()).isEqualTo("EMBEDDED");
        assertThat(embedded.metamodel().id()).isEqualTo(id);
        assertThat(embedded.association().alreadyPersisted()).isFalse();

        assertThat(filesStartingWith(root.resolve("metamodels"), id + ".")).as("preview must not persist the metamodel").isEmpty();
        assertThat(filesStartingWith(root.resolve("validations"), id + ".")).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("model_editor_visual_import.as: two different metamodels, or none, are rejected instead of guessed")
    void ambiguousOrMissingMetamodelsAreRejected() throws Exception {
        byte[] ecore = resource(ECORE);
        byte[] other;
        try (InputStream in = getClass().getResourceAsStream("/metamodels/sample.ecore")) {
            assertThat(in).isNotNull();
            other = in.readAllBytes();
        }
        var ambiguous = List.<org.springframework.web.multipart.MultipartFile>of(
                new MockMultipartFile("visual", "two.zip", "application/zip", zip("a.ecore", ecore, other)));

        assertThatThrownBy(() -> resolver.resolve("EMBEDDED", "", ambiguous, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("More than one different metamodel");
        assertThatThrownBy(() -> resolver.resolve("EXTERNAL", "", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No associated metamodel could be found");
        assertThatThrownBy(() -> resolver.resolve("SOMETHING-ELSE", "", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown metamodel association mode");
    }


    private VisualDefinitionImportResult siriusPreview() throws Exception {
        MockMultipartFile odesign = upload("odesign", ODESIGN, "application/xml");
        MockMultipartFile services = upload("javaSources", SERVICES, "text/x-java-source");
        MockMultipartFile ecore = new MockMultipartFile("metamodelResources", "hydratest.ecore", "application/xml", resource(ECORE));
        return importService.importSirius("", odesign, List.of(services), "EXTERNAL", List.of(ecore));
    }

    @Test
    @Order(10)
    @DisplayName("model_editor_visual_import.d1 / as: the Sirius preview with an external metamodel persists nothing")
    void previewWritesNothing() throws Exception {
        VisualDefinitionImportResult preview = siriusPreview();

        String id = preview.associatedMetamodel().id();
        assertThat(preview.metamodelAssociation().mode()).isEqualTo("EXTERNAL");
        assertThat(preview.metamodelAssociation().alreadyPersisted()).isFalse();
        assertThat(preview.editorDefinition().metamodelId()).isEqualTo(id);
        assertThat(preview.validationDetails()).hasSize(3);
        assertThat(filesStartingWith(root.resolve("metamodels"), id + ".")).as("preview: metamodel").isEmpty();
        assertThat(filesStartingWith(root.resolve("validations"), id + ".")).as("preview: validations").isEmpty();
        assertThat(filesStartingWith(root.resolve("editors"), "")).as("preview: editors").isEmpty();
    }

    /**
     * Regression of the former defect: commit() used to build the validation document id as {@code metamodelId + ":validations"},
     * which the stable document id policy rejects, so it failed after persisting the metamodel and left no validation document.
     * The id is now a valid document id (no ':'), so the validations are persisted together with the metamodel.
     */
    @Test
    @Order(11)
    @DisplayName("model_editor_visual_import.d1: commit() with imported validations persists the metamodel and a validation document with a stable id")
    void commitWithValidationsPersistsTheValidationDocumentWithAStableId() throws Exception {
        VisualDefinitionImportResult preview = siriusPreview();
        String id = preview.associatedMetamodel().id();

        VisualImportCommitResult committed = associations.commit(new VisualImportCommitRequest(preview.editorDefinition(),
                preview.associatedMetamodel(), preview.metamodelAssociation(), preview.validationDetails()));

        assertThat(committed.validationsImported()).isEqualTo(preview.validationDetails().size());
        assertThat(filesStartingWith(root.resolve("metamodels"), id + ".metamodel.json")).as("metamodel persisted").hasSize(1);
        var validationFiles = filesStartingWith(root.resolve("validations"), id + ".validations.json");
        assertThat(validationFiles).as("validation document written").hasSize(1);
        assertThat(filesStartingWith(root.resolve("validations"), id + ".expressions.json")).as("expression document written beside it").hasSize(1);
        String documentId = mapper.readTree(Files.readAllBytes(validationFiles.get(0))).get("id").asString();
        assertThat(com.example.editor.contract.StableIdentifierPolicy.isValidDocumentId(documentId)).as(documentId).isTrue();
        assertThat(documentId).doesNotContain(":");
    }

    @Test
    @Order(12)
    @DisplayName("model_editor_visual_import.d1 / art: confirming the analysed editor (without validation details) keeps the metamodel and the definition is then saved")
    void commitWithoutValidationDetailsPersistsTheMetamodelAndTheDefinitionIsSaved() throws Exception {
        VisualDefinitionImportResult preview = siriusPreview();
        String id = preview.associatedMetamodel().id();

        VisualImportCommitResult committed = associations.commit(new VisualImportCommitRequest(preview.editorDefinition(),
                preview.associatedMetamodel(), preview.metamodelAssociation(), List.of()));

        assertThat(committed.metamodelId()).isEqualTo(id);
        assertThat(committed.validationsImported()).isZero();
        Path metamodelFile = root.resolve("metamodels").resolve(id + ".metamodel.json");
        assertThat(metamodelFile).isRegularFile();
        assertThat(mapper.readTree(Files.readAllBytes(metamodelFile)).get("id").asString()).isEqualTo(id);

        var saved = editors.create(id, committed.editorDefinition());
        assertThat(saved.editorDefinition().metamodelId()).isEqualTo(id);
        assertThat(filesStartingWith(root.resolve("editors"), saved.editorDefinition().id())).hasSize(1);
    }
}
