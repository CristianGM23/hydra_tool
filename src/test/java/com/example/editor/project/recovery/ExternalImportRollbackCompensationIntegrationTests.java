package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.ProjectException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión de #36: la importación de un proyecto externo exige confirmar la vista previa concreta y, si falla después de
 * materializar capas canónicas, las deshace sin tocar lo que ya existía (los almacenes canónicos viven fuera del proyecto).
 * Los casos dependen del orden: los de fallo parten de almacenes vacíos y los de reutilización de un metamodelo ya importado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalImportRollbackCompensationIntegrationTests {
    private static final String FIXTURE = "/fixtures/sirius-complete/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ECORE = "com.hydra.sirius.fixture.metamodel/model/hydratest.ecore";
    private static final List<String> FULL = List.of(
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
            "com.hydra.sirius.fixture.validation/plugin.xml", "com.hydra.sirius.fixture.validation/META-INF/MANIFEST.MF",
            "com.hydra.sirius.fixture.validation/validation/hydratest.evl");
    private static final List<String> METAMODEL_ONLY = List.of(ECORE);

    @TempDir static Path root;
    @TempDir static Path workspaces;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) { MetamodelHttpTestEnvironment.register(registry, root); }

    @Autowired private MockMvc mvc;
    @Autowired private ExternalImportCompensationService compensation;
    @MockitoSpyBean private ProjectApplicationService projects;
    @MockitoSpyBean private EditorDefinitionApplicationService editors;

    private static byte[] resource(String relative) throws IOException {
        try (InputStream input = ExternalImportRollbackCompensationIntegrationTests.class.getResourceAsStream(FIXTURE + relative)) {
            if (input == null) throw new IOException("Missing fixture resource: " + relative);
            return input.readAllBytes();
        }
    }

    private static byte[] zip(List<String> files, String marker) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String relative : files) { zip.putNextEntry(new ZipEntry(relative)); zip.write(resource(relative)); zip.closeEntry(); }
            if (!marker.isEmpty()) { zip.putNextEntry(new ZipEntry("NOTES.txt")); zip.write(marker.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }

    /** Contenido de los almacenes canónicos, sin el registro de auditoría (solo anexado por diseño) ni el registro de proyectos. */
    private static Map<String, String> canonicalState() throws IOException {
        Map<String, String> state = new TreeMap<>();
        try (var walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.contains("audit") || relative.startsWith("projects/") || relative.startsWith("external-import-journal/")) continue;
                try { state.put(relative, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))); }
                catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
            }
        }
        return state;
    }

    private String analyse(byte[] archive) throws Exception {
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "external.zip", "application/zip", archive))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("fingerprint").asString();
    }

    private MockHttpServletResponse importArchive(byte[] archive, Path parent, String name, String fingerprint) throws Exception {
        var request = multipart("/api/external-project-recovery/import")
                .file(new MockMultipartFile("file", "external.zip", "application/zip", archive))
                .param("parentDirectory", parent.toString()).param("projectName", name);
        if (fingerprint != null) request.param("confirmedFingerprint", fingerprint);
        return mvc.perform(request).andReturn().getResponse();
    }

    private static List<String> children(Path parent) throws IOException {
        try (var list = Files.list(parent)) { return list.map(p -> p.getFileName().toString()).toList(); }
    }

    private void failAfterMaterialization() {
        doThrow(new ProjectException(ProjectException.Reason.STORAGE_ERROR, "injected failure after materialization"))
                .when(projects).upload(eq("recovery"), eq("external-project-materialization.json"), any(byte[].class));
    }

    private void restoreProjectUploads() { org.mockito.Mockito.reset(projects); }

    private static String describe(Map<String, String> before, Map<String, String> after) {
        List<String> added = new ArrayList<>(after.keySet()); added.removeAll(before.keySet());
        List<String> removed = new ArrayList<>(before.keySet()); removed.removeAll(after.keySet());
        List<String> changed = before.keySet().stream().filter(after::containsKey).filter(key -> !before.get(key).equals(after.get(key))).toList();
        return "added=" + added + " removed=" + removed + " changed=" + changed;
    }

    @Test @Order(1)
    @DisplayName("x_recover_external_project.pj: importar sin confirmacion se rechaza y no deja proyecto ni documentos")
    void importWithoutConfirmationIsRejected() throws Exception {
        Map<String, String> before = canonicalState();
        Path parent = Files.createDirectories(workspaces.resolve("no-confirmation"));
        MockHttpServletResponse response = importArchive(zip(FULL, ""), parent, "No Confirmation", null);
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isBetween(400, 499);
        assertThat(children(parent)).isEmpty();
        assertThat(canonicalState()).isEqualTo(before);
    }

    @Test @Order(2)
    @DisplayName("x_recover_external_project.pj: una confirmacion de otro contenido no se acepta")
    void aConfirmationForDifferentContentIsRejected() throws Exception {
        Map<String, String> before = canonicalState();
        String previewOfAnotherArchive = analyse(zip(METAMODEL_ONLY, ""));
        Path parent = Files.createDirectories(workspaces.resolve("other-content"));
        MockHttpServletResponse response = importArchive(zip(FULL, ""), parent, "Other Content", previewOfAnotherArchive);
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(409);
        assertThat(children(parent)).isEmpty();
        assertThat(canonicalState()).isEqualTo(before);
    }

    @Test @Order(3)
    @DisplayName("x_recover_external_project.pj: un fallo tras materializar deshace metamodelo, editor, reglas y recursos creados")
    void aFailureAfterMaterializationLeavesNoCanonicalDocumentsBehind() throws Exception {
        Map<String, String> before = canonicalState();
        byte[] archive = zip(FULL, "");
        String fingerprint = analyse(archive);
        Path parent = Files.createDirectories(workspaces.resolve("failure-after-materialization"));
        failAfterMaterialization();
        try {
            MockHttpServletResponse response = importArchive(archive, parent, "Failing Import", fingerprint);
            assertThat(response.getStatus()).as("la importacion debe fallar").isGreaterThanOrEqualTo(400);
        } finally { restoreProjectUploads(); }
        assertThat(children(parent)).as("el proyecto creado se elimina").isEmpty();
        Map<String, String> after = canonicalState();
        assertThat(after).as(describe(before, after)).isEqualTo(before);
        assertThat(compensation.pendingDescriptions()).isEmpty();
    }

    @Test @Order(4)
    @DisplayName("x_recover_external_project.pj: repetir la compensacion no tiene efectos y es segura")
    void repeatingTheCompensationIsIdempotent() throws Exception {
        Map<String, String> before = canonicalState();
        assertThat(compensation.recoverIncomplete()).allMatch(ExternalImportCompensationService.CompensationReport::complete);
        try (var ledger = compensation.begin("f".repeat(64), "f".repeat(64), "Repeated")) {
            assertThat(compensation.compensate(ledger).complete()).isTrue();
            assertThat(compensation.compensate(ledger).complete()).isTrue();
        }
        assertThat(canonicalState()).isEqualTo(before);
        assertThat(compensation.pendingDescriptions()).isEmpty();
    }

    @Test @Order(5)
    @DisplayName("x_recover_external_project.pj: si la compensacion falla se informa lo pendiente y se completa al reintentar")
    void aFailureDuringCompensationReportsPendingStateAndResumes() throws Exception {
        Map<String, String> before = canonicalState();
        byte[] archive = zip(FULL, "");
        String fingerprint = analyse(archive);
        Path parent = Files.createDirectories(workspaces.resolve("failure-during-compensation"));
        failAfterMaterialization();
        doThrow(new IllegalStateException("injected failure while compensating")).doCallRealMethod()
                .when(editors).delete(any(), any(), any());
        try {
            MockHttpServletResponse response = importArchive(archive, parent, "Compensation Failure", fingerprint);
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            assertThat(response.getStatus()).as(body).isGreaterThanOrEqualTo(400);
            assertThat(body).as("el error debe informar del estado pendiente").contains("incomplete").contains("METAMODEL:");
        } finally { restoreProjectUploads(); org.mockito.Mockito.reset(editors); }
        assertThat(compensation.pendingDescriptions()).as("lo no compensado queda registrado").isNotEmpty();
        // El diario durable deja explicito el estado de la compensacion y el motivo de lo que no pudo completarse.
        List<String> journals = ImportFixtureArchives.journals(root);
        assertThat(journals).hasSize(1);
        assertThat(ImportFixtureArchives.text(root.resolve("external-import-journal").resolve(journals.get(0))))
                .contains("COMPENSATION_FAILED").contains("injected failure while compensating").contains("\"compensation\" : \"FAILED\"");
        assertThat(children(parent)).isEmpty();

        assertThat(compensation.recoverIncomplete()).isNotEmpty().allMatch(ExternalImportCompensationService.CompensationReport::complete);
        assertThat(compensation.pendingDescriptions()).isEmpty();
        Map<String, String> after = canonicalState();
        assertThat(after).as(describe(before, after)).isEqualTo(before);
    }

    @Test @Order(6)
    @DisplayName("x_recover_external_project.pj: una importacion confirmada solo con el metamodelo conserva sus documentos")
    void aConfirmedMetamodelOnlyImportSucceeds() throws Exception {
        byte[] archive = zip(METAMODEL_ONLY, "");
        Path parent = Files.createDirectories(workspaces.resolve("metamodel-only"));
        MockHttpServletResponse response = importArchive(archive, parent, "Metamodel Only", analyse(archive));
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        assertThat(canonicalState().keySet()).anyMatch(path -> path.contains("hydratest.metamodel.json"));
    }

    @Test @Order(7)
    @DisplayName("x_recover_external_project.pj: un fallo sobre un metamodelo reutilizado restaura las capas previas y conserva el metamodelo")
    void aFailureOnAReusedMetamodelRestoresThePreviousLayers() throws Exception {
        Map<String, String> before = canonicalState();
        byte[] archive = zip(FULL, "");
        String fingerprint = analyse(archive);
        Path parent = Files.createDirectories(workspaces.resolve("reused-failure"));
        failAfterMaterialization();
        try {
            assertThat(importArchive(archive, parent, "Reused Failure", fingerprint).getStatus()).isGreaterThanOrEqualTo(400);
        } finally { restoreProjectUploads(); }
        Map<String, String> after = canonicalState();
        assertThat(after).as(describe(before, after)).isEqualTo(before);
        assertThat(after.keySet()).as("el metamodelo preexistente no se borra").anyMatch(path -> path.contains("hydratest.metamodel.json"));
    }

    @Test @Order(8)
    @DisplayName("x_recover_external_project.pj: una importacion completa confirmada materializa capas y registra la titularidad")
    void aConfirmedFullImportSucceedsAndRecordsOwnership() throws Exception {
        byte[] archive = zip(FULL, "");
        Path parent = Files.createDirectories(workspaces.resolve("full-success"));
        MockHttpServletResponse response = importArchive(archive, parent, "Full Success", analyse(archive));
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);
        assertThat(canonicalState().keySet()).anyMatch(path -> path.endsWith(".editor.json"));
        Path project = Path.of(JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("session").get("directory").asString());
        assertThat(project.resolve("recovery/external-import-ownership.json")).isRegularFile();
    }

    @Test @Order(9)
    @DisplayName("x_recover_external_project.pj: un fallo al reimportar el mismo contenido no borra ni altera lo ya existente")
    void aFailedReimportKeepsEverythingThatAlreadyExisted() throws Exception {
        Map<String, String> before = canonicalState();
        byte[] archive = zip(FULL, "");
        String fingerprint = analyse(archive);
        Path parent = Files.createDirectories(workspaces.resolve("reimport-failure"));
        failAfterMaterialization();
        try {
            assertThat(importArchive(archive, parent, "Reimport Failure", fingerprint).getStatus()).isGreaterThanOrEqualTo(400);
        } finally { restoreProjectUploads(); }
        Map<String, String> after = canonicalState();
        assertThat(after).as(describe(before, after)).isEqualTo(before);
        assertThat(after.keySet()).anyMatch(path -> path.endsWith(".editor.json"));
        assertThat(children(parent)).isEmpty();
    }
}
