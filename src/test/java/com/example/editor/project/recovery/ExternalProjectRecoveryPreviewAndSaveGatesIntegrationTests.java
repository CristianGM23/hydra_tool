package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionLoadResult;
import com.example.editor.definition.assets.EditorVisualAssetService;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recuperacion de un proyecto externo con almacenes reales: la vista previa no guarda nada, un origen ambiguo no guarda
 * capas del lenguaje y la importacion confirmada guarda metamodelo, capas del editor y reglas con su marca de version.
 *
 * <p>Etapas certificadas (directas): {@code x_recover_external_project.mat}, {@code x_recover_external_project.pers},
 * {@code project_import_export.i3}, {@code recovery_persist_rollback.a1}. Relacionadas sin demostrar:
 * {@code x_recover_external_project.integ}, {@code x_project_lifecycle_overview.rc}, {@code recovery_lifecycle.d2},
 * {@code recovery_lifecycle.a7}: en esta via no existe un indicador de «vista previa aplicable» (el analisis es la vista
 * previa y la importacion es la confirmacion), la limpieza solo borra el proyecto creado (no las capas canonicas) y
 * el perfil de integracion solo se sugiere en el informe, nunca se crea.</p>
 *
 * <p>Contexto Spring real con MockMvc y todos los almacenes en directorios temporales. Oraculos: documentos leidos del
 * disco, SHA-256 recalculado en el test, el fichero de expectativas del fixture Sirius y el recuento de reglas hecho sobre
 * el texto del fichero de reglas.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalProjectRecoveryPreviewAndSaveGatesIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    private static final String EVL = "com.hydra.sirius.fixture.validation/validation/hydratest.evl";
    private static final List<String> FILES = List.of(
            "com.hydra.sirius.fixture.design/.project", "com.hydra.sirius.fixture.design/build.properties",
            "com.hydra.sirius.fixture.design/description/hydratest.odesign", "com.hydra.sirius.fixture.design/icons/datastore.svg",
            "com.hydra.sirius.fixture.design/META-INF/MANIFEST.MF",
            "com.hydra.sirius.fixture.design/src/com/hydra/sirius/fixture/design/Services.java",
            "com.hydra.sirius.fixture.metamodel/.project", "com.hydra.sirius.fixture.metamodel/build.properties",
            "com.hydra.sirius.fixture.metamodel/META-INF/MANIFEST.MF", ECORE,
            "com.hydra.sirius.fixture.metamodel/model/hydratest.genmodel",
            "com.hydra.sirius.fixture.sample/.project", "com.hydra.sirius.fixture.sample/representations.aird",
            "com.hydra.sirius.fixture.sample/model/invalid.hydraflow", "com.hydra.sirius.fixture.sample/model/valid.hydraflow",
            "com.hydra.sirius.fixture.validation/.project", "com.hydra.sirius.fixture.validation/build.properties",
            "com.hydra.sirius.fixture.validation/plugin.xml", "com.hydra.sirius.fixture.validation/META-INF/MANIFEST.MF", EVL);
    private static final List<String> DIAGRAM_NODES = List.of("GroupContainer", "StartNode", "EndNode", "TaskSquare", "Port",
            "DecisionDiamond", "DataStoreImage", "AnnotationNote", "TaskGauge", "DisabledTaskMarker");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Almacenes canonicos de HYDRA (metamodelos, validaciones, acciones, editores...). */
    @TempDir
    static Path root;

    /** Carpeta padre de los proyectos creados por la importacion, fuera de los almacenes. */
    @TempDir
    static Path workspaces;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mvc;
    @Autowired private EditorDefinitionApplicationService editors;

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = ExternalProjectRecoveryPreviewAndSaveGatesIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] fixtureZip() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String relative : FILES) files.put(relative, resource(relative));
        return zip(files);
    }

    private static String text(JsonNode node) { return node == null || node.isNull() ? "" : node.asString(); }

    private static String body(MockHttpServletResponse response) throws Exception { return response.getContentAsString(StandardCharsets.UTF_8); }

    private static String etagOf(byte[] bytes) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\"";
    }

    /** Nombres de los documentos con este sufijo en cualquier almacen canonico. */
    private static List<String> documents(String suffix) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(suffix)).sorted().toList();
        }
    }

    private static Path documentPath(String name) throws IOException {
        try (var walk = Files.walk(root)) {
            List<Path> found = walk.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals(name)).toList();
            assertThat(found).as("documento %s", name).hasSize(1);
            return found.get(0);
        }
    }

    private static List<String> expectedNames(String... path) throws IOException {
        JsonNode node = JSON.readTree(new String(resource("expected/hydra-import-expectations.json"), StandardCharsets.UTF_8));
        for (int index = 0; index < path.length - 1; index++) node = node.get(path[index]);
        List<String> names = new ArrayList<>();
        for (JsonNode item : node) names.add(path[path.length - 1].isEmpty() ? text(item) : text(item.get(path[path.length - 1])));
        return names;
    }

    private static Set<String> evlRuleNames() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?m)^\\s*(?:constraint|critique)\\s+([A-Za-z_][\\w-]*)\\s*\\{")
                .matcher(new String(resource(EVL), StandardCharsets.UTF_8));
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    private JsonNode importProject(byte[] archive, String parentFolder, String projectName) throws Exception {
        Path parent = Files.createDirectories(workspaces.resolve(parentFolder));
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/import")
                .file(new MockMultipartFile("file", "external.zip", "application/zip", archive))
                .param("parentDirectory", parent.toString()).param("projectName", projectName)).andReturn().getResponse();
        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        return JSON.readTree(body(response));
    }

    private String httpEtag(String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        return response.getHeader("ETag");
    }

    private void assertNoLanguageLayersAreStored() throws IOException {
        assertThat(documents(".metamodel.json")).isEmpty();
        assertThat(documents(".validations.json")).isEmpty();
        assertThat(documents(".actions.json")).isEmpty();
        assertThat(documents(".editor.json")).isEmpty();
    }

    @Test
    @Order(1)
    @DisplayName("x_recover_external_project.mat: analizar es solo una vista previa; no guarda capas ni crea un proyecto")
    void analyzingIsOnlyAPreviewThatStoresNothing() throws Exception {
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "external.zip", "application/zip", fixtureZip()))).andReturn().getResponse();

        assertThat(response.getStatus()).as(body(response)).isEqualTo(200);
        JsonNode report = JSON.readTree(body(response));
        assertThat(report.get("resourceCount").asInt()).isGreaterThanOrEqualTo(FILES.size());
        assertThat(report.get("technologies").size()).isPositive();
        assertThat(report.get("integrationSuggestions").size()).as("el perfil de integracion solo se sugiere").isPositive();
        assertNoLanguageLayersAreStored();
        assertThat(documents(".integration.json")).isEmpty();
        MockHttpServletResponse session = mvc.perform(get("/api/projects/session")).andReturn().getResponse();
        assertThat(JSON.readTree(body(session)).get("active").asBoolean()).as("analizar no abre ni crea ningun proyecto").isFalse();
        try (var children = Files.list(workspaces)) {
            assertThat(children.count()).isZero();
        }
    }

    @Test
    @Order(2)
    @DisplayName("x_recover_external_project.mat: con dos fuentes del metamodelo igual de plausibles se conserva el original y no se guarda ninguna capa")
    void twoEquallyPlausibleSourcesKeepTheOriginalAndStoreNoLanguageLayer() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("first/model/hydratest.ecore", resource(ECORE));
        files.put("second/model/hydratest.ecore", resource(ECORE));

        JsonNode result = importProject(zip(files), "ambiguous", "Ambiguous Source");

        JsonNode materialization = result.get("materialization");
        assertThat(materialization.get("metamodelMaterialized").asBoolean()).isFalse();
        assertThat(text(materialization.get("metamodelId"))).isEmpty();
        assertThat(materialization.get("editorIds").size()).isZero();
        boolean refused = false;
        for (JsonNode diagnostic : materialization.get("diagnostics")) refused |= text(diagnostic).contains("Several equally plausible metamodel sources");
        assertThat(refused).as("el informe explica que no se adivina").isTrue();
        assertNoLanguageLayersAreStored();

        Path project = Path.of(text(result.get("session").get("directory")));
        assertThat(Files.readAllBytes(project.resolve("external-source/first/model/hydratest.ecore"))).isEqualTo(resource(ECORE));
        assertThat(Files.readAllBytes(project.resolve("external-source/second/model/hydratest.ecore"))).isEqualTo(resource(ECORE));
        assertThat(Files.readString(project.resolve("recovery/README.md"))).contains("not materialized automatically");
        JsonNode stored = JSON.readTree(Files.readString(project.resolve("recovery/external-project-materialization.json")));
        assertThat(stored.get("metamodelMaterialized").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("x_recover_external_project.mat/pers, project_import_export.i3, recovery_persist_rollback.a1: confirmar la importacion guarda metamodelo, capas del editor y reglas con su marca de version")
    void confirmingTheImportStoresMetamodelEditorLayersAndRulesWithVersionMarks() throws Exception {
        JsonNode result = importProject(fixtureZip(), "complete", "Sirius Recovered");

        JsonNode materialization = result.get("materialization");
        assertThat(materialization.get("metamodelMaterialized").asBoolean()).isTrue();
        String metamodelId = text(materialization.get("metamodelId"));
        assertThat(metamodelId).isNotBlank();

        // Metamodelo: clases del fichero de expectativas en el documento del disco y marca de version = SHA-256 de sus bytes.
        Path metamodelFile = documentPath(metamodelId + ".metamodel.json");
        JsonNode storedMetamodel = JSON.readTree(Files.readString(metamodelFile));
        List<String> classNames = new ArrayList<>();
        for (JsonNode metaClass : storedMetamodel.get("classes")) classNames.add(text(metaClass.get("name")));
        assertThat(classNames).containsAll(expectedNames("metamodel", "classes", ""));
        assertThat(httpEtag("/api/canonical-metamodels/" + metamodelId)).isEqualTo(etagOf(Files.readAllBytes(metamodelFile)));

        // Reglas de validacion: todas las del fichero de reglas estan guardadas y tienen su marca de version.
        Path validationFile = documentPath(metamodelId + ".validations.json");
        Set<String> storedRules = new LinkedHashSet<>();
        for (JsonNode rule : JSON.readTree(Files.readString(validationFile)).get("rules")) storedRules.add(text(rule.get("name")));
        assertThat(evlRuleNames()).as("reglas del fichero de reglas del fixture").isNotEmpty();
        assertThat(storedRules).containsAll(evlRuleNames());
        assertThat(httpEtag("/api/canonical-metamodels/" + metamodelId + "/validations")).isEqualTo(etagOf(Files.readAllBytes(validationFile)));
        assertThat(documents(".actions.json")).as("las correcciones recuperadas se guardan").hasSize(1);
        assertThat(JSON.readTree(Files.readString(documentPath(documents(".actions.json").get(0)))).get("actions").size()).isPositive();

        // Capas del editor: representaciones, mapeos y paleta del diseno recuperado.
        assertThat(materialization.get("editorIds").size()).isPositive();
        Set<String> representationNames = new LinkedHashSet<>();
        Set<String> diagramNodes = new LinkedHashSet<>();
        String diagramEditorId = "";
        for (JsonNode editorId : materialization.get("editorIds")) {
            JsonNode storedEditor = JSON.readTree(Files.readString(documentPath(text(editorId) + ".editor.json")));
            assertThat(text(storedEditor.get("metamodelId"))).isEqualTo(metamodelId);
            assertThat(storedEditor.get("palette").get("tools").size()).as("paleta del editor %s", text(editorId)).isPositive();
            for (JsonNode representation : storedEditor.get("representations")) {
                representationNames.add(text(representation.get("name")));
                if (text(representation.get("name")).equals("Hydra Workflow Diagram")) {
                    diagramEditorId = text(editorId);
                    for (JsonNode mapping : representation.get("nodeMappings")) {
                        JsonNode annotations = mapping.get("annotations");
                        diagramNodes.add(annotations == null ? "" : text(annotations.get("hydra.import.externalName")));
                    }
                }
            }
        }
        assertThat(representationNames).containsAll(expectedNames("sirius", "representations", "name"));
        assertThat(diagramNodes).containsAll(DIAGRAM_NODES);
        EditorDefinitionLoadResult loaded = editors.find(metamodelId, diagramEditorId);
        assertThat(loaded.etag()).isNotBlank();
        assertThat(loaded.editorDefinition().capabilityManifest().supports(EditorCapability.VALIDATION_RUNTIME)).isTrue();
        String dataStoreImage = "";
        for (var representation : loaded.editorDefinition().representations()) {
            for (var mapping : representation.nodeMappings()) {
                if ("DataStoreImage".equals(mapping.annotations().get("hydra.import.externalName"))) dataStoreImage = mapping.imageAssetPath();
            }
        }
        assertThat(dataStoreImage).startsWith(EditorVisualAssetService.PREFIX).endsWith(".svg");

        // Anotacion de lo creado en esta ejecucion y proyecto con el original preservado.
        Path project = Path.of(text(result.get("session").get("directory")));
        JsonNode stored = JSON.readTree(Files.readString(project.resolve("recovery/external-project-materialization.json")));
        assertThat(text(stored.get("metamodelId"))).isEqualTo(metamodelId);
        assertThat(stored.get("editorIds")).isEqualTo(materialization.get("editorIds"));
        List<String> sources = new ArrayList<>();
        for (JsonNode source : stored.get("materializedSources")) sources.add(text(source));
        assertThat(sources).contains(ECORE);
        assertThat(Files.readAllBytes(project.resolve("external-source/" + ECORE))).isEqualTo(resource(ECORE));
        assertThat(documents(".integration.json")).as("el perfil de integracion nunca se crea solo").isEmpty();
    }
}
