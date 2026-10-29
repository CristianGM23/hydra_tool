package com.example.editor.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterBenchService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteHashing;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteImportProperties;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteRemoteSnapshotLicenseEntry;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteRemoteSnapshotLock;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteRemoteSnapshotResource;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteRemoteSnapshotService;
import com.example.editor.performance.CapabilityPerformanceProperties;
import com.example.editor.performance.CapabilityPerformanceSample;
import com.example.editor.performance.CapabilityPerformanceService;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puerta de release con colaboradores reales: HydraReleaseGateController + HydraReleaseGateService +
 * CapabilityPackageChecker + UpdateSiteP2MirrorService (espejo p2 construido y persistido de verdad) +
 * CapabilityPerformanceService (informes de rendimiento persistidos), todos con almacenes en directorios temporales.
 * Unicas sustituciones: la fuente de instantaneas verificadas del espejo (Mockito, solo alimenta bytes) y el banco de
 * adaptadores, que no se ejercita (sin ids de banco).
 *
 * Etapas: publication_generate_publish.gt, recovery_author_governance.{b,d,r}.
 * Oraculos: ficheros JSON de informes leidos del disco, derivacion literal del reportId desde la atestacion,
 * estados de comprobacion y HTML descargado.
 */
class HydraReleaseGateRealCollaboratorsIntegrationTests {
    @TempDir Path temporary;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private Path gates;
    private UpdateSiteP2MirrorService mirrors;
    private CapabilityPerformanceService performance;
    private HydraReleaseGateService service;
    private MockMvc mvc;
    private String mirrorId;

    @BeforeEach
    void wire() {
        gates = temporary.resolve("gates");
        byte[] plugin = "verified-plugin".getBytes(StandardCharsets.UTF_8);
        String path = "child/plugins/org.example.bundle_1.2.3.jar";
        UpdateSiteRemoteSnapshotResource resource = new UpdateSiteRemoteSnapshotResource(
                "https://updates.example.org/" + path, path, plugin.length,
                UpdateSiteHashing.sha256(plugin), 1, "objects/" + UpdateSiteHashing.sha256(plugin));
        UpdateSiteRemoteSnapshotLicenseEntry license = new UpdateSiteRemoteSnapshotLicenseEntry(path,
                "Example Foundation", "https://updates.example.org", "EPL-2.0",
                "Preserve notices and source obligations", true, true);
        UpdateSiteRemoteSnapshotLock lock = new UpdateSiteRemoteSnapshotLock("1.0", "snapshot-1",
                "https://updates.example.org", "a".repeat(64), Instant.parse("2026-08-04T12:00:00Z"),
                plugin.length, List.of(resource), List.of(license));
        UpdateSiteRemoteSnapshotService snapshots = mock(UpdateSiteRemoteSnapshotService.class);
        when(snapshots.find("snapshot-1")).thenReturn(lock);
        when(snapshots.verifiedResources("snapshot-1")).thenReturn(Map.of(path, plugin));
        UpdateSiteImportProperties importProperties = new UpdateSiteImportProperties();
        importProperties.setStorageRoot(temporary.resolve("update-site").toString());
        mirrors = new UpdateSiteP2MirrorService(importProperties, snapshots, json);
        mirrorId = mirrors.create("snapshot-1").manifest().mirrorId();

        CapabilityPerformanceProperties performanceProperties = new CapabilityPerformanceProperties();
        performanceProperties.setStorageRoot(temporary.resolve("performance").toString());
        performance = new CapabilityPerformanceService(performanceProperties, json);
        performance.evaluate(sample("perf-base", 400, 300));
        performance.evaluate(sample("perf-good", 420, 310));
        performance.evaluate(sample("perf-bad", 5000, 300));

        service = new HydraReleaseGateService(new CapabilityPackageChecker(), mirrors,
                mock(UpdateSiteAdapterBenchService.class), performance, json, gates.toString(), 200);
        mvc = MockMvcBuilders.standaloneSetup(new HydraReleaseGateController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static CapabilityPerformanceSample sample(String runId, long renderMillis, long stageMillis) {
        return new CapabilityPerformanceSample(runId, "update-site-import-center", Instant.parse("2026-08-04T12:00:00Z"),
                renderMillis, 50_000_000L, 5, 1_000_000L, 2_000L, Map.of("analyze", stageMillis), Map.of("main", 400_000L));
    }

    private static HydraCapabilityPackage capabilityPackage() {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package",
                "Example package", "1.0.0", "example", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of(), Map.of());
    }

    private JsonNode evaluate(String version, String mirror, String baseline, String candidate,
            boolean requireMerge, boolean requireMirror, boolean requireBench, boolean requirePerformance) throws Exception {
        HydraReleaseGateRequest request = new HydraReleaseGateRequest("hydra.example", version, capabilityPackage(), null,
                mirror, List.of(), baseline, candidate, requireMerge, requireMirror, requireBench, requirePerformance, Map.of());
        MockHttpServletResponse response = mvc.perform(post("/api/release-gates").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private static String status(JsonNode report, String code) {
        for (JsonNode check : report.get("checks")) {
            if (check.get("code").toString().equals("\"" + code + "\"")) return check.get("status").toString().replace("\"", "");
        }
        throw new AssertionError("check not present: " + code);
    }

    @Test
    @DisplayName("publication_generate_publish.gt / recovery_author_governance.{b,d}: todo exigido y superado")
    void everythingRequiredAndPassingProducesAPassedAttestedReport() throws Exception {
        JsonNode report = evaluate("1.0.0", mirrorId, "perf-base", "perf-good", false, true, false, true);

        assertThat(report.get("passed").asBoolean()).isTrue();
        assertThat(status(report, "PACKAGE")).isEqualTo("PASS");
        assertThat(status(report, "P2_MIRROR")).isEqualTo("PASS");
        assertThat(status(report, "PERFORMANCE")).isEqualTo("PASS");
        assertThat(status(report, "SEMANTIC_MERGE")).isEqualTo("WARNING");
        assertThat(status(report, "ADAPTER_BENCH")).isEqualTo("WARNING");
        String attestation = report.get("attestationSha256").toString().replace("\"", "");
        assertThat(attestation).matches("[0-9a-f]{64}");
        String reportId = report.get("reportId").toString().replace("\"", "");
        assertThat(reportId).isEqualTo("release-gate-" + attestation.substring(0, 24));

        // Oraculo: el informe persistido en disco coincide con la respuesta.
        Path stored = gates.resolve(reportId + ".json");
        assertThat(stored).isRegularFile();
        JsonNode onDisk = json.readTree(Files.readString(stored));
        assertThat(onDisk.get("attestationSha256").toString()).isEqualTo("\"" + attestation + "\"");
        assertThat(onDisk.get("passed").asBoolean()).isTrue();
        assertThat(onDisk.get("mirrorId").toString()).isEqualTo("\"" + mirrorId + "\"");

        MockHttpServletResponse byId = mvc.perform(get("/api/release-gates/" + reportId)).andReturn().getResponse();
        assertThat(byId.getStatus()).isEqualTo(200);
        assertThat(json.readTree(byId.getContentAsString()).get("attestationSha256").toString()).isEqualTo("\"" + attestation + "\"");
        assertThat(service.assertPassed(reportId).reportId()).isEqualTo(reportId);

        // Idempotencia: la misma evidencia produce el mismo identificador y no duplica el fichero.
        assertThat(evaluate("1.0.0", mirrorId, "perf-base", "perf-good", false, true, false, true).get("reportId").toString())
                .isEqualTo("\"" + reportId + "\"");
        try (var files = Files.list(gates)) { assertThat(files.count()).isEqualTo(1); }
    }

    @Test
    @DisplayName("recovery_author_governance.{b,d}: un requisito exigido que falla bloquea el release")
    void aRequiredCheckThatFailsBlocksTheRelease() throws Exception {
        JsonNode unknownMirror = evaluate("1.0.1", "no-such-mirror", "", "", false, true, false, false);
        assertThat(unknownMirror.get("passed").asBoolean()).isFalse();
        assertThat(status(unknownMirror, "P2_MIRROR")).isEqualTo("FAIL");

        JsonNode missingMirror = evaluate("1.0.2", "", "", "", false, true, false, false);
        assertThat(status(missingMirror, "P2_MIRROR")).isEqualTo("FAIL");
        assertThat(missingMirror.get("passed").asBoolean()).isFalse();

        JsonNode regression = evaluate("1.0.3", mirrorId, "perf-base", "perf-bad", false, true, false, true);
        assertThat(status(regression, "PERFORMANCE")).isEqualTo("FAIL");
        assertThat(status(regression, "P2_MIRROR")).isEqualTo("PASS");
        assertThat(regression.get("passed").asBoolean()).isFalse();

        JsonNode noBench = evaluate("1.0.4", "", "", "", true, false, true, false);
        assertThat(status(noBench, "ADAPTER_BENCH")).isEqualTo("FAIL");
        assertThat(status(noBench, "SEMANTIC_MERGE")).isEqualTo("FAIL");
        assertThat(noBench.get("passed").asBoolean()).isFalse();

        String blockedId = regression.get("reportId").toString().replace("\"", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.assertPassed(blockedId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("blocking checks");
        assertThat(gates.resolve(blockedId + ".json")).isRegularFile();
    }

    @Test
    @DisplayName("recovery_author_governance.d: requisitos opcionales ausentes solo producen advertencias")
    void absentOptionalRequirementsOnlyProduceWarnings() throws Exception {
        JsonNode report = evaluate("1.0.5", "", "", "", false, false, false, false);
        assertThat(report.get("passed").asBoolean()).isTrue();
        assertThat(status(report, "PACKAGE")).isEqualTo("PASS");
        assertThat(status(report, "P2_MIRROR")).isEqualTo("WARNING");
        assertThat(status(report, "ADAPTER_BENCH")).isEqualTo("WARNING");
        assertThat(status(report, "PERFORMANCE")).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("recovery_author_governance.r: atestacion HTML descargable y deteccion de manipulacion del JSON almacenado")
    void htmlAttestationIsDownloadableAndTamperingWithTheStoredJsonIsDetected() throws Exception {
        JsonNode report = evaluate("2.0.0", mirrorId, "perf-base", "perf-good", false, true, false, true);
        String reportId = report.get("reportId").toString().replace("\"", "");
        String attestation = report.get("attestationSha256").toString().replace("\"", "");

        MockHttpServletResponse html = mvc.perform(get("/api/release-gates/" + reportId + "/html")).andReturn().getResponse();
        assertThat(html.getStatus()).isEqualTo(200);
        assertThat(html.getContentType()).startsWith("text/html");
        assertThat(html.getHeader("Content-Disposition")).contains(reportId + ".html");
        String body = html.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains(attestation).contains("hydra.example").contains("Passed")
                .contains("P2_MIRROR").contains("PERFORMANCE");

        // Manipulacion: cambiar el producto en disco invalida la atestacion y ninguna lectura la acepta.
        Path stored = gates.resolve(reportId + ".json");
        Files.writeString(stored, Files.readString(stored).replace("hydra.example", "hydra.tampered"));
        assertThat(mvc.perform(get("/api/release-gates/" + reportId)).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(mvc.perform(get("/api/release-gates/" + reportId + "/html")).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(mvc.perform(get("/api/release-gates")).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.assertPassed(reportId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("attestation");
    }
}
