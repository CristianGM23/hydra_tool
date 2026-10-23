package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;

import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión de #36 sobre la durabilidad del diario: fallos del propio diario, concurrencia con recursos compartidos y
 * recuperación posterior. Los reinicios con procesos separados están en {@link ExternalImportCrashRecoveryTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalImportJournalPersistenceIntegrationTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir static Path root;
    @TempDir static Path workspaces;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) { MetamodelHttpTestEnvironment.register(registry, root); }

    @Autowired private MockMvc mvc;
    @Autowired private ExternalImportCompensationService compensation;
    @MockitoSpyBean private ExternalImportJournalStore store;

    private String analyse(byte[] archive) throws Exception {
        MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/analyze")
                .file(new MockMultipartFile("file", "external.zip", "application/zip", archive))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("fingerprint").asString();
    }

    private MockHttpServletResponse importArchive(byte[] archive, Path parent, String name) throws Exception {
        return mvc.perform(multipart("/api/external-project-recovery/import")
                .file(new MockMultipartFile("file", "external.zip", "application/zip", archive))
                .param("parentDirectory", parent.toString()).param("projectName", name)
                .param("confirmedFingerprint", analyse(archive))).andReturn().getResponse();
    }

    private static List<String> children(Path parent) throws Exception {
        try (var list = Files.list(parent)) { return list.map(p -> p.getFileName().toString()).toList(); }
    }

    @Test @Order(1)
    @DisplayName("#36: si el diario no se puede escribir, la importación no empieza y no toca nada")
    void aJournalThatCannotBeWrittenStopsTheImportBeforeAnyEffect() throws Exception {
        Map<String, String> before = ImportFixtureArchives.canonicalState(root);
        Path parent = Files.createDirectories(workspaces.resolve("journal-down"));
        Mockito.doThrow(new ExternalImportJournalException("injected: journal volume is read-only")).when(store).save(any());
        try {
            MockHttpServletResponse response = importArchive(ImportFixtureArchives.zip(ImportFixtureArchives.FULL), parent, "Journal Down");
            assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isGreaterThanOrEqualTo(500);
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("nothing was imported");
        } finally { Mockito.reset(store); }
        assertThat(children(parent)).isEmpty();
        Map<String, String> after = ImportFixtureArchives.canonicalState(root);
        assertThat(after).as(ImportFixtureArchives.describe(before, after)).isEqualTo(before);
        assertThat(ImportFixtureArchives.journals(root)).isEmpty();
    }

    @Test @Order(2)
    @DisplayName("#36: si el diario falla a mitad de la operación se compensa igualmente y la recuperación posterior termina de limpiar")
    void aJournalThatFailsMidOperationStillCompensatesAndIsReconciledLater() throws Exception {
        Map<String, String> before = ImportFixtureArchives.canonicalState(root);
        Path parent = Files.createDirectories(workspaces.resolve("journal-fails"));
        byte[] archive = ImportFixtureArchives.zip(ImportFixtureArchives.FULL);
        String fingerprint = analyse(archive);
        // begin, proyecto previsto, proyecto creado y metamodelo previsto se persisten; a partir de ahí el diario falla también durante la compensación.
        Mockito.doCallRealMethod().doCallRealMethod().doCallRealMethod().doCallRealMethod()
                .doThrow(new ExternalImportJournalException("injected: journal write failed")).when(store).save(any());
        // El diario tampoco se puede borrar: queda desfasado en disco y lo reconcilia la recuperación.
        Mockito.doThrow(new ExternalImportJournalException("injected: journal delete failed")).when(store).delete(any());
        try {
            MockHttpServletResponse response = mvc.perform(multipart("/api/external-project-recovery/import")
                    .file(new MockMultipartFile("file", "external.zip", "application/zip", archive))
                    .param("parentDirectory", parent.toString()).param("projectName", "Journal Fails")
                    .param("confirmedFingerprint", fingerprint)).andReturn().getResponse();
            assertThat(response.getStatus()).isGreaterThanOrEqualTo(500);
        } finally { Mockito.reset(store); }

        Map<String, String> after = ImportFixtureArchives.canonicalState(root);
        assertThat(after).as("la compensación se hace desde el estado real aunque el diario no se pueda escribir: "
                + ImportFixtureArchives.describe(before, after)).isEqualTo(before);
        assertThat(children(parent)).isEmpty();
        // El diario quedó desfasado en disco; la recuperación lo reconcilia sin efectos y lo borra.
        assertThat(ImportFixtureArchives.journals(root)).hasSize(1);
        assertThat(compensation.recoverIncomplete()).isNotEmpty().allMatch(ExternalImportCompensationService.CompensationReport::complete);
        assertThat(ImportFixtureArchives.journals(root)).isEmpty();
        assertThat(ImportFixtureArchives.canonicalState(root)).isEqualTo(before);
    }

    @Test @Order(3)
    @DisplayName("#36: dos importaciones concurrentes con recursos compartidos se serializan: una crea y la otra reutiliza")
    void twoConcurrentImportsSharingResourcesAreSerialized() throws Exception {
        byte[] archive = ImportFixtureArchives.zip(ImportFixtureArchives.FULL);
        Path parent = Files.createDirectories(workspaces.resolve("concurrent"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (String name : List.of("Concurrent One", "Concurrent Two")) {
                futures.add(executor.submit(() -> { start.await(); return importArchive(archive, parent, name); }));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) responses.add(future.get(5, TimeUnit.MINUTES));
            for (MockHttpServletResponse response : responses) assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(200);

            int created = 0; int reused = 0;
            for (MockHttpServletResponse response : responses) {
                Path project = Path.of(JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("session").get("directory").asString());
                var ownership = JSON.readTree(Files.readString(project.resolve("recovery/external-import-ownership.json")));
                if (ownership.has("METAMODEL.CREATED")) created++;
                if (ownership.has("METAMODEL.REUSED")) reused++;
            }
            assertThat(created).as("exactamente una importación crea el metamodelo compartido").isEqualTo(1);
            assertThat(reused).as("la otra lo reutiliza").isEqualTo(1);
        } finally { executor.shutdownNow(); }
        assertThat(ImportFixtureArchives.journals(root)).isEmpty();
        assertThat(compensation.pendingDescriptions()).isEmpty();
        assertThat(ImportFixtureArchives.canonicalState(root).keySet().stream().filter(path -> path.endsWith(".metamodel.json")).count()).isEqualTo(1);
    }

    @Test @Order(100)
    @DisplayName("#36: sin diarios pendientes, la recuperación no crea el directorio del diario ni su cerrojo (arrancar no escribe en el almacenamiento)")
    void recoveryWithoutJournalsWritesNothing() throws Exception {
        Path directory = root.resolve("external-import-journal");
        assertThat(ImportFixtureArchives.journals(root)).isEmpty();
        if (Files.isDirectory(directory)) {
            try (var walk = Files.walk(directory)) { walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete()); }
        }
        assertThat(compensation.recoverIncomplete()).isEmpty();
        assertThat(directory).doesNotExist();
    }
}