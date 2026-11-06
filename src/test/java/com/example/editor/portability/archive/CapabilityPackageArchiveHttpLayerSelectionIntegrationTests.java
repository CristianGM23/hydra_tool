package com.example.editor.portability.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportacion de capas por HTTP: el controlador real y el servicio de archivos reales (cache en disco temporal) producen un
 * paquete portable cuyo contenido se corresponde exactamente con las capas elegidas, y la importacion posterior las restaura.
 * La eleccion de capas la hace quien llama al componer el paquete (manifiesto + documentos): aqui se compara el archivo de
 * tres capas con el de solo metamodelo y validaciones.
 *
 * Etapa: portability_author_export.a.
 * Oraculos: el ZIP descargado se abre con java.util.zip; manifiesto, bytes de cada capa, huella de contenido y SHA-256 se
 * recalculan en el test con literales escritos a mano y con el fichero de cache leido del disco.
 */
class CapabilityPackageArchiveHttpLayerSelectionIntegrationTests {
    private static final String METAMODEL_JSON = "{\"id\":\"people\",\"version\":\"2.1\"}";
    private static final String VALIDATION_JSON = "{\"id\":\"people-validations\",\"rules\":\"2\"}";
    private static final String EDITOR_JSON = "{\"id\":\"people-editor\",\"views\":\"1\"}";
    private static final MediaType PACKAGE = MediaType.parseMediaType("application/vnd.hydra.capability-package+zip");

    @TempDir Path directory;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private Path cache;
    private MockMvc mvc;

    @BeforeEach
    void wire() {
        CapabilityPackageArchiveProperties properties = new CapabilityPackageArchiveProperties();
        cache = directory.resolve("cache");
        properties.setCacheDirectory(cache);
        CapabilityPackageArchiveService service = new CapabilityPackageArchiveService(properties, new CapabilityPackageChecker(),
                new CapabilityPackageDependencyResolver(), json);
        mvc = MockMvcBuilders.standaloneSetup(new CapabilityPackageArchiveController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private HydraCapabilityPackage threeLayers() throws Exception {
        return capabilityPackage(true, true, true);
    }

    private HydraCapabilityPackage metamodelAndValidations() throws Exception {
        return capabilityPackage(true, true, false);
    }

    private HydraCapabilityPackage capabilityPackage(boolean metamodel, boolean validations, boolean editor) throws Exception {
        List<CapabilitySelection> capabilities = new java.util.ArrayList<>();
        List<CapabilityDependency> dependencies = new java.util.ArrayList<>();
        Map<String, JsonNode> documents = new java.util.LinkedHashMap<>();
        if (editor) {
            capabilities.add(new CapabilitySelection("editor", HydraCapabilityKind.VISUAL_REPRESENTATION, "editor.json", "1.0", true, false, Map.of()));
            dependencies.add(new CapabilityDependency("editor", "metamodel", true, ">=2.1", "the editor resolves metamodel types"));
            documents.put("editor.json", json.readTree(EDITOR_JSON));
        }
        if (validations) {
            capabilities.add(new CapabilitySelection("validation", HydraCapabilityKind.VALIDATIONS, "validation.json", "1.0", true, false, Map.of()));
            dependencies.add(new CapabilityDependency("validation", "metamodel", true, ">=2.1", "semantic types"));
            documents.put("validation.json", json.readTree(VALIDATION_JSON));
        }
        if (metamodel) {
            capabilities.add(new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, "metamodel.json", "2.1", true, false, Map.of()));
            documents.put("metamodel.json", json.readTree(METAMODEL_JSON));
        }
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "people-tools", "People tools", "1.0.0",
                "people", new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                capabilities, dependencies, List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, documents, Map.of("icons/check.svg", "<svg/>"));
    }

    private MockHttpServletResponse export(HydraCapabilityPackage value) throws Exception {
        return mvc.perform(post("/api/capability-packages/archives").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(value))).andReturn().getResponse();
    }

    private static Map<String, byte[]> entries(byte[] archive) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) if (!entry.isDirectory()) result.put(entry.getName(), input.readAllBytes());
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String contentHash(Map<String, byte[]> archiveEntries) {
        StringBuilder source = new StringBuilder();
        new TreeMap<>(archiveEntries).forEach((path, bytes) -> {
            if (!path.equals("META-INF/hydra-package.json")) {
                source.append(path).append('\u0000').append(bytes.length).append('\u0000').append(sha256(bytes)).append('\n');
            }
        });
        return sha256(source.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("portability_author_export.a: elegir solo metamodelo y validaciones produce un archivo con exactamente esas capas")
    void exportingOnlyMetamodelAndValidationsProducesAnArchiveWithExactlyThoseLayers() throws Exception {
        MockHttpServletResponse response = export(metamodelAndValidations());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith(PACKAGE.toString());
        assertThat(response.getHeader("Content-Disposition")).contains("people-tools-1.0.0.hydra-cap");
        byte[] archive = response.getContentAsByteArray();
        Map<String, byte[]> content = entries(archive);

        assertThat(content.keySet()).containsExactly("META-INF/hydra-package.json", "canonical/metamodel.json",
                "canonical/validation.json", "manifest.json", "resources/icons/check.svg");
        assertThat(new String(content.get("canonical/metamodel.json"), StandardCharsets.UTF_8)).isEqualTo(METAMODEL_JSON);
        assertThat(new String(content.get("canonical/validation.json"), StandardCharsets.UTF_8)).isEqualTo(VALIDATION_JSON);
        assertThat(new String(content.get("resources/icons/check.svg"), StandardCharsets.UTF_8)).isEqualTo("<svg/>");
        JsonNode manifest = json.readTree(content.get("manifest.json"));
        assertThat(manifest.get("capabilities").size()).isEqualTo(2);
        assertThat(manifest.get("capabilities").get(0).get("id").asString()).isEqualTo("metamodel");
        assertThat(manifest.get("capabilities").get(1).get("id").asString()).isEqualTo("validation");
        assertThat(manifest.get("checksums").get("canonical/metamodel.json").asString()).isEqualTo(sha256(METAMODEL_JSON.getBytes(StandardCharsets.UTF_8)));
        assertThat(manifest.get("checksums").get("canonical/validation.json").asString()).isEqualTo(sha256(VALIDATION_JSON.getBytes(StandardCharsets.UTF_8)));

        assertThat(response.getHeader("X-HYDRA-Archive-SHA256")).isEqualTo(sha256(archive));
        String hash = response.getHeader("X-HYDRA-Content-Hash");
        assertThat(hash).isEqualTo(contentHash(content));
        assertThat(response.getHeader("X-HYDRA-Cache-Hit")).isEqualTo("false");
        assertThat(Files.readAllBytes(cache.resolve(hash + ".hydra-cap"))).isEqualTo(archive);
    }

    @Test
    @DisplayName("portability_author_export.a: el archivo de tres capas difiere del de dos y repetir la exportacion es reproducible y acierta en la cache")
    void addingTheEditorLayerChangesTheArchiveAndRepeatedExportsAreReproducible() throws Exception {
        MockHttpServletResponse two = export(metamodelAndValidations());
        MockHttpServletResponse three = export(threeLayers());
        MockHttpServletResponse twoAgain = export(metamodelAndValidations());

        Map<String, byte[]> threeContent = entries(three.getContentAsByteArray());
        assertThat(threeContent.keySet()).contains("canonical/editor.json");
        assertThat(new String(threeContent.get("canonical/editor.json"), StandardCharsets.UTF_8)).isEqualTo(EDITOR_JSON);
        assertThat(entries(two.getContentAsByteArray()).keySet()).doesNotContain("canonical/editor.json");
        assertThat(three.getHeader("X-HYDRA-Content-Hash")).isNotEqualTo(two.getHeader("X-HYDRA-Content-Hash"));
        assertThat(twoAgain.getContentAsByteArray()).isEqualTo(two.getContentAsByteArray());
        assertThat(twoAgain.getHeader("X-HYDRA-Cache-Hit")).isEqualTo("true");
        try (var files = Files.list(cache)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("portability_author_export.a: importar el archivo restaura exactamente las capas elegidas y un archivo manipulado se rechaza")
    void importingTheArchiveRestoresExactlyTheChosenLayersAndATamperedOneIsRejected() throws Exception {
        byte[] archive = export(metamodelAndValidations()).getContentAsByteArray();

        MockHttpServletResponse imported = mvc.perform(multipart("/api/capability-packages/archives/import")
                .file(new MockMultipartFile("file", "people-tools-1.0.0.hydra-cap", "application/octet-stream", archive)))
                .andReturn().getResponse();

        assertThat(imported.getStatus()).isEqualTo(200);
        JsonNode result = json.readTree(imported.getContentAsString());
        JsonNode documents = result.get("capabilityPackage").get("canonicalDocuments");
        assertThat(documents.size()).isEqualTo(2);
        assertThat(documents.has("metamodel.json")).isTrue();
        assertThat(documents.has("validation.json")).isTrue();
        assertThat(documents.has("editor.json")).isFalse();
        assertThat(documents.get("metamodel.json").get("id").asString()).isEqualTo("people");
        assertThat(documents.get("validation.json").get("id").asString()).isEqualTo("people-validations");
        assertThat(result.get("capabilityPackage").get("resources").get("icons/check.svg").asString()).isEqualTo("<svg/>");
        assertThat(result.get("archiveSha256").asString()).isEqualTo(sha256(archive));

        byte[] tampered = archive.clone();
        for (int index = tampered.length / 2; index < tampered.length; index++) {
            if (tampered[index] != 0) { tampered[index] ^= 1; break; }
        }
        MockHttpServletResponse rejected = mvc.perform(multipart("/api/capability-packages/archives/import")
                .file(new MockMultipartFile("file", "tampered.hydra-cap", "application/octet-stream", tampered)))
                .andReturn().getResponse();
        assertThat(rejected.getStatus()).isEqualTo(400);
        assertThat(json.readTree(rejected.getContentAsString()).get("code").asString()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("portability_author_export.a: un paquete con una capa exigida sin documento o con ruta de recurso insegura no genera archivo")
    void anInvalidPackageIsRejectedAndNothingIsCached() throws Exception {
        HydraCapabilityPackage complete = metamodelAndValidations();
        Map<String, JsonNode> withoutValidations = new java.util.LinkedHashMap<>(complete.canonicalDocuments());
        withoutValidations.remove("validation.json");
        MockHttpServletResponse missingLayer = export(new HydraCapabilityPackage(complete.manifest(), withoutValidations, complete.resources()));
        assertThat(missingLayer.getStatus()).isEqualTo(400);
        assertThat(missingLayer.getContentType()).doesNotStartWith("application/vnd.hydra.capability-package");

        MockHttpServletResponse unsafe = export(new HydraCapabilityPackage(complete.manifest(), complete.canonicalDocuments(),
                Map.of("../secret.txt", "secret")));
        assertThat(unsafe.getStatus()).isEqualTo(400);
        assertThat(unsafe.getContentType()).doesNotStartWith("application/vnd.hydra.capability-package");

        long cached = 0;
        if (Files.isDirectory(cache)) {
            try (var files = Files.list(cache)) {
                cached = files.count();
            }
        }
        assertThat(cached).isZero();
    }
}
