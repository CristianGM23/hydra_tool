package com.example.editor.integration.web;

import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.PROFILES;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.assertSameFiles;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.createProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.emfProfile;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.ensureMetamodel;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.filesUnder;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.generatedFiles;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.sha256Hex;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.technology;
import static com.example.editor.integration.web.IntegrationDeliveryHttpTestSupport.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * integration_author_views.pv, integration_author_wizard.dl and integration_deliver.rb: la vista previa de cambios, la
 * aplicacion y el registro de reversion de una tecnologia generada, por HTTP real.
 *
 * Contexto Spring completo con MockMvc; la raiz de inyeccion es una carpeta bajo @TempDir. Oraculos: el contenido del ZIP
 * que devuelve la compilacion del mismo perfil (hashes SHA-256 recalculados en el test), el manifiesto, el indice de
 * ficheros gestionados y las copias de seguridad leidos del disco con java.nio, y las ediciones manuales que hace el propio
 * test. No se ejecuta ninguna herramienta nativa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TechnologyInjectionPreviewApplyRollbackHttpIntegrationTests {

    private static final String MODULE_ROOT = "generated-integration/people-emf";
    private static final String README = MODULE_ROOT + "/README.md";
    private static final String POM = MODULE_ROOT + "/pom.xml";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper mapper;

    private Path host(String name) {
        return root.resolve("injection-targets").resolve(name);
    }

    private String seed(String profileId) throws Exception {
        ensureMetamodel(mockMvc, mapper);
        return createProfile(mockMvc, mapper, emfProfile(profileId, "people-emf", "people-emf", false, List.of()));
    }

    private void replaceProfile(String profileId, String etag, String targetModule, boolean allowModifyGeneratedFiles) throws Exception {
        String body = mapper.writeValueAsString(emfProfile(profileId, targetModule, "people-emf", allowModifyGeneratedFiles, List.of()));
        mockMvc.perform(put(PROFILES + "/" + profileId).header(HttpHeaders.IF_MATCH, etag).contentType(JSON).content(body))
                .andExpect(status().isOk());
    }

    private String postJson(String url, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(url).contentType(JSON).content(body)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static String target(String directory) {
        return "{\"targetDirectory\":\"" + directory + "\"}";
    }

    private static List<String> list(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static String planStatus(String json) {
        return JsonPath.read(json, "$.plan.status");
    }

    private static String single(String json, String path) {
        List<String> values = JsonPath.read(json, path);
        assertThat(values).as(path).hasSize(1);
        return values.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, String>> managedIndex(Path host) throws Exception {
        return (Map<String, Map<String, String>>) JsonPath.read(Files.readString(host.resolve(".hydra/injections/managed-files.json")), "$.files");
    }

    @Test
    @DisplayName("integration_author_views.pv / integration_author_wizard.dl / integration_deliver.rb: la vista previa no crea nada, aplicar deja manifiesto con los hashes del ZIP y revertir deja el anfitrion como estaba")
    void previewWritesNothingApplyRecordsTheArchiveHashesAndRollbackRestoresTheHost() throws Exception {
        String profileId = "people-emf-par-fresh";
        seed(profileId);
        Map<String, byte[]> expected = generatedFiles(mockMvc, profileId);
        Path host = host("host-par-fresh");

        // pv: la vista previa lista cada fichero del ZIP como alta, con su hash, y no crea ni la carpeta del anfitrion.
        String preview = postJson(technology(profileId) + "/preview", target("host-par-fresh"), 200);
        assertThat(planStatus(preview)).isEqualTo("READY");
        assertThat(list(preview, "$.plan.fileChanges[*].path")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(list(preview, "$.plan.fileChanges[*].changeKind")).containsOnly("ADD");
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            assertThat(single(preview, "$.plan.fileChanges[?(@.path=='" + entry.getKey() + "')].generatedSha256"))
                    .as(entry.getKey()).isEqualTo(sha256Hex(entry.getValue()));
        }
        long totalBytes = expected.values().stream().mapToLong(bytes -> bytes.length).sum();
        assertThat(((Number) JsonPath.read(preview, "$.generatedBytes")).longValue()).isEqualTo(totalBytes);
        assertThat(host).as("la vista previa no crea el directorio del anfitrion").doesNotExist();

        // dl / rb: aplicar escribe los ficheros y un manifiesto cuyos hashes coinciden con los de los ficheros en disco.
        String applied = postJson(technology(profileId) + "/apply", target("host-par-fresh"), 200);
        String manifestId = JsonPath.read(applied, "$.manifest.id");
        assertSameFiles(filesUnder(host, ".hydra"), expected);
        String manifest = Files.readString(host.resolve(".hydra/injections/manifests/" + manifestId + ".json"));
        assertThat(list(manifest, "$.files[*].path")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(list(manifest, "$.files[*].changeKind")).containsOnly("ADD");
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            assertThat(single(manifest, "$.files[?(@.path=='" + entry.getKey() + "')].generatedSha256"))
                    .as(entry.getKey()).isEqualTo(sha256Hex(Files.readAllBytes(host.resolve(entry.getKey()))));
        }
        Map<String, Map<String, String>> index = managedIndex(host);
        assertThat(index.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(index.get(POM).get("sha256")).isEqualTo(sha256Hex(expected.get(POM)));
        assertThat(index.get(POM).get("manifestId")).isEqualTo(manifestId);

        // rb: revertir con ese manifiesto borra todo lo anadido y vacia el indice de ficheros gestionados.
        String rolledBack = postJson(technology(profileId) + "/rollback/" + manifestId, target("host-par-fresh"), 200);
        assertThat(list(rolledBack, "$.deletedFiles")).containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(list(rolledBack, "$.restoredFiles")).isEmpty();
        assertThat(filesUnder(host, ".hydra")).as("solo queda la carpeta interna de HYDRA").isEmpty();
        assertThat(managedIndex(host)).isEmpty();
    }

    @Test
    @DisplayName("integration_author_views.pv / integration_deliver.rb: un cambio sobre ficheros gestionados se previsualiza como modificacion, se guarda copia y revertir devuelve los bytes originales")
    void modifiedManagedFilesArePlannedBackedUpAndRestoredByRollback() throws Exception {
        String profileId = "people-emf-par-modify";
        String etag = seed(profileId);
        Path host = host("host-par-modify");
        Map<String, byte[]> first = generatedFiles(mockMvc, profileId);
        postJson(technology(profileId) + "/apply", target("host-par-modify"), 200);
        assertSameFiles(filesUnder(host, ".hydra"), first);

        // El perfil cambia el nombre del modulo y permite regenerar ficheros gestionados.
        replaceProfile(profileId, etag, "people-emf-v2", true);
        Map<String, byte[]> second = generatedFiles(mockMvc, profileId);
        // Oraculo independiente: la clasificacion esperada sale de comparar a mano los dos ZIP.
        List<String> modified = second.keySet().stream().filter(path -> first.containsKey(path) && !Arrays.equals(first.get(path), second.get(path))).toList();
        List<String> added = second.keySet().stream().filter(path -> !first.containsKey(path)).toList();
        List<String> unchanged = second.keySet().stream().filter(path -> first.containsKey(path) && Arrays.equals(first.get(path), second.get(path))).toList();
        assertThat(modified).contains(POM);
        assertThat(text(second.get(POM))).contains("<artifactId>people-emf-v2</artifactId>");

        // pv: la vista previa distingue modificados, nuevos y sin cambios, con el hash original y el nuevo, sin escribir.
        String preview = postJson(technology(profileId) + "/preview", target("host-par-modify"), 200);
        assertThat(planStatus(preview)).isEqualTo("READY");
        assertThat(list(preview, "$.plan.fileChanges[?(@.changeKind=='MODIFY')].path")).containsExactlyInAnyOrderElementsOf(modified);
        assertThat(list(preview, "$.plan.fileChanges[?(@.changeKind=='ADD')].path")).containsExactlyInAnyOrderElementsOf(added);
        assertThat(list(preview, "$.unchangedFiles")).containsExactlyInAnyOrderElementsOf(unchanged);
        assertThat(single(preview, "$.plan.fileChanges[?(@.path=='" + POM + "')].originalSha256")).isEqualTo(sha256Hex(first.get(POM)));
        assertThat(single(preview, "$.plan.fileChanges[?(@.path=='" + POM + "')].generatedSha256")).isEqualTo(sha256Hex(second.get(POM)));
        assertSameFiles(filesUnder(host, ".hydra"), first);

        // dl: aplicar deja los ficheros nuevos y una copia de seguridad de cada modificado con los bytes anteriores.
        String applied = postJson(technology(profileId) + "/apply", target("host-par-modify"), 200);
        String manifestId = JsonPath.read(applied, "$.manifest.id");
        assertSameFiles(filesUnder(host, ".hydra"), second);
        Path backupRoot = host.resolve(".hydra/injections/backups").resolve(manifestId);
        for (String path : modified) {
            assertThat(Files.readAllBytes(backupRoot.resolve(path))).as("copia de " + path).isEqualTo(first.get(path));
        }

        // rb: revertir restaura los bytes anteriores de los modificados y borra los nuevos.
        String rolledBack = postJson(technology(profileId) + "/rollback/" + manifestId, target("host-par-modify"), 200);
        assertThat(list(rolledBack, "$.restoredFiles")).containsExactlyInAnyOrderElementsOf(modified);
        assertThat(list(rolledBack, "$.deletedFiles")).containsExactlyInAnyOrderElementsOf(added);
        assertSameFiles(filesUnder(host, ".hydra"), first);
        assertThat(managedIndex(host).get(POM).get("sha256")).isEqualTo(sha256Hex(first.get(POM)));
    }

    @Test
    @DisplayName("integration_author_views.pv / integration_author_wizard.dl: un fichero gestionado editado a mano bloquea la vista previa y aplicar se rechaza sin tocar nada; las rutas fuera de la raiz se rechazan")
    void aManuallyEditedManagedFileBlocksApplyAndUnsafeTargetsAreRejected() throws Exception {
        String profileId = "people-emf-par-edit";
        String etag = seed(profileId);
        Path host = host("host-par-edit");
        postJson(technology(profileId) + "/apply", target("host-par-edit"), 200);
        byte[] originalPom = Files.readAllBytes(host.resolve(POM));
        Files.writeString(host.resolve(README), "manual edit\n", StandardCharsets.UTF_8);
        replaceProfile(profileId, etag, "people-emf-v2", true);

        String preview = postJson(technology(profileId) + "/preview", target("host-par-edit"), 200);
        assertThat(planStatus(preview)).isEqualTo("BLOCKED");
        assertThat(list(preview, "$.manualConflictFiles")).containsExactly(README);

        postJson(technology(profileId) + "/apply", target("host-par-edit"), 422);
        assertThat(text(Files.readAllBytes(host.resolve(README)))).isEqualTo("manual edit\n");
        assertThat(Files.readAllBytes(host.resolve(POM))).as("ningun fichero se escribe a medias").isEqualTo(originalPom);

        postJson(technology(profileId) + "/preview", target("../outside"), 400);
        postJson(technology(profileId) + "/apply", target("../outside"), 400);
        assertThat(root.resolve("injection-targets").resolve("..").resolve("outside").normalize()).doesNotExist();
    }
}
