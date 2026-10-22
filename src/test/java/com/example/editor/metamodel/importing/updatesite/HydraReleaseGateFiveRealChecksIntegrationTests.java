package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.performance.CapabilityPerformanceProperties;
import com.example.editor.performance.CapabilityPerformanceSample;
import com.example.editor.performance.CapabilityPerformanceService;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.merge.CapabilitySemanticMergeDecision;
import com.example.editor.portability.merge.CapabilitySemanticMergePreview;
import com.example.editor.portability.merge.CapabilitySemanticMergeRequest;
import com.example.editor.portability.merge.CapabilitySemanticMergeService;
import com.example.editor.release.HydraReleaseGateController;
import com.example.editor.release.HydraReleaseGateRequest;
import com.example.editor.release.HydraReleaseGateService;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puerta de release con LAS CINCO comprobaciones reales (paquete, fusion semantica, espejo p2, banco de adaptadores y
 * rendimiento) a traves del controlador HTTP real. Lo unico simulado es la red (captura remota del repositorio p2) y el
 * analisis posterior de la instantanea; la instantanea, el espejo, el banco con fixtures positivo/negativo/mutacion, la
 * fusion y el rendimiento son los servicios reales sobre directorios temporales.
 *
 * Etapas: publication_generate_publish.{gt,ok,cert,blk}, recovery_author_governance.{a,b,c,d,r}.
 * Oraculos: informe JSON leido del disco, reportId y evidencias derivadas literalmente, SHA-256 recalculado en el test,
 * HTML descargado fila a fila y manipulacion del JSON almacenado.
 * Esta clase vive en el paquete de update-site porque el tipo de captura remota solo es visible alli.
 */
class HydraReleaseGateFiveRealChecksIntegrationTests {
    private static final String PLUGIN_PATH = "child/plugins/org.example.bundle_1.2.3.jar";
    private static final String EXISTING_LAYER = """
            {"rules":[{"id":"rule-name","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-name"}],
             "quickFixes":[{"id":"fix-name","name":"SetName"}]}
            """;
    private static final String RECOVERED_LAYER = """
            {"rules":[{"id":"rule-name","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-name"},
                      {"id":"rule-code","name":"CodeRequired","expression":"self.code <> ''","quickFixId":"fix-code"}],
             "quickFixes":[{"id":"fix-name","name":"SetName"},{"id":"fix-code","name":"SetCode"}]}
            """;
    private static final String DANGLING_LAYER = """
            {"rules":[{"id":"rule-name","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-ghost"}],
             "quickFixes":[{"id":"fix-name","name":"SetName"}]}
            """;
    private static final String ADAPTER_DOCUMENT = """
            {"schemaVersion":"1.0",
             "adapter":{"id":"org.example:custom-extension","label":"Custom extension",
               "extensionPointTokens":["org.example.custom"],"elementNames":["definition"],
               "semanticKind":"RUNTIME_EXTENSION","neutralTarget":"runtimeExtension",
               "purpose":"Recover a custom declaration without executing code.",
               "idAttributes":["id"],"implementationAttributes":["class"],"confidence":"DERIVED","enabled":true},
             "annotations":{"version":"1.0.0"}}
            """;

    @TempDir Path temporary;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private Path gates;
    private UpdateSiteAdapterBenchService bench;
    private CapabilitySemanticMergeService merge;
    private HydraReleaseGateService service;
    private MockMvc mvc;
    private String mirrorId;

    @BeforeEach
    void wire() {
        gates = temporary.resolve("gates");
        UpdateSiteImportProperties importProperties = new UpdateSiteImportProperties();
        importProperties.setStorageRoot(temporary.resolve("update-site").toString());
        importProperties.setRemoteMaxBytes(1_000_000);
        importProperties.setRemoteMaxTotalBytes(2_000_000);
        importProperties.setRemoteMaxResources(32);
        importProperties.setRemoteMaxDepth(4);

        byte[] plugin = "verified-plugin".getBytes(StandardCharsets.UTF_8);
        String url = "https://updates.example.org/site/" + PLUGIN_PATH;
        UpdateSiteRemoteAnalysisRequest request = new UpdateSiteRemoteAnalysisRequest(
                "https://updates.example.org/site/", "", 2, true, UpdateSiteImportOptions.defaults());
        UpdateSiteRemoteRepositoryService remote = mock(UpdateSiteRemoteRepositoryService.class);
        UpdateSiteImportApplicationService imports = mock(UpdateSiteImportApplicationService.class);
        when(remote.capture(request)).thenReturn(new UpdateSiteRemoteCapture(request.repositoryUrl(),
                List.of(new UpdateSiteInputFile(PLUGIN_PATH, plugin)),
                List.of(new UpdateSiteRemoteRepositoryFetch(url, PLUGIN_PATH, plugin.length,
                        UpdateSiteHashing.sha256(plugin), false, 1)),
                plugin.length, 0, List.of("Captured complete bounded tree")));
        when(imports.analyze(anyList(), any(UpdateSiteImportOptions.class))).thenReturn(analysis());
        UpdateSiteRemoteSnapshotService snapshots = new UpdateSiteRemoteSnapshotService(importProperties, remote, imports, json);
        String snapshotId = snapshots.create(request).lock().snapshotId();
        snapshots.reviewLicenses(snapshotId, new UpdateSiteRemoteSnapshotLicenseReviewRequest(List.of(
                new UpdateSiteRemoteSnapshotLicenseEntry(PLUGIN_PATH, "Example Foundation", url, "EPL-2.0",
                        "Preserve notices and source obligations", true, true))));
        UpdateSiteP2MirrorService mirrors = new UpdateSiteP2MirrorService(importProperties, snapshots, json);
        mirrorId = mirrors.create(snapshotId).manifest().mirrorId();

        bench = new UpdateSiteAdapterBenchService(importProperties,
                new UpdateSiteDeclarativeAdapterService(importProperties, new UpdateSiteExtensionAdapterCatalog(), json), json);
        CapabilityPerformanceProperties performanceProperties = new CapabilityPerformanceProperties();
        performanceProperties.setStorageRoot(temporary.resolve("performance").toString());
        CapabilityPerformanceService performance = new CapabilityPerformanceService(performanceProperties, json);
        performance.evaluate(sample("perf-base", 400, 300));
        performance.evaluate(sample("perf-good", 420, 310));
        merge = new CapabilitySemanticMergeService(null, json);

        service = new HydraReleaseGateService(new CapabilityPackageChecker(), mirrors, bench, performance, json,
                gates.toString(), 200);
        mvc = MockMvcBuilders.standaloneSetup(new HydraReleaseGateController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static CapabilityPerformanceSample sample(String runId, long renderMillis, long stageMillis) {
        return new CapabilityPerformanceSample(runId, "update-site-import-center", Instant.parse("2026-08-04T12:00:00Z"),
                renderMillis, 50_000_000L, 5, 1_000_000L, 2_000L, Map.of("analyze", stageMillis), Map.of("main", 400_000L));
    }

    private static UpdateSiteImportAnalysis analysis() {
        return new UpdateSiteImportAnalysis("analysis", Instant.now(), null, List.of(), "", null, List.of(),
                UpdateSiteRecoveredLayers.empty(), false, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(),
                List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(),
                UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
    }

    private HydraCapabilityPackage capabilityPackage(String validations) throws Exception {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package",
                "Example package", "1.0.0", "example", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of("validations.json", json.readTree(validations)), Map.of());
    }

    private CapabilitySemanticMergePreview preview(String existing, String recovered) throws Exception {
        return merge.preview(new CapabilitySemanticMergeRequest(capabilityPackage(existing), capabilityPackage(recovered),
                "example", Map.of(), List.of(), null,
                Map.of("validations.json#document", CapabilitySemanticMergeDecision.MERGE), false));
    }

    private String benchReportId(boolean healthy) {
        UpdateSiteAdapterFixture inside = new UpdateSiteAdapterFixture("org.example.custom", "definition",
                Map.of("id", "create", "class", "org.example.Tool"));
        UpdateSiteAdapterFixture outside = new UpdateSiteAdapterFixture("org.example.other", "other", Map.of());
        List<UpdateSiteAdapterBenchCase> cases = List.of(new UpdateSiteAdapterBenchCase("positive", inside, true),
                new UpdateSiteAdapterBenchCase(healthy ? "negative" : "claims-a-match-that-does-not-exist", outside, !healthy));
        return bench.run(new UpdateSiteAdapterBenchRequest("", "", ADAPTER_DOCUMENT, cases, true)).reportId();
    }

    private JsonNode evaluate(String version, HydraCapabilityPackage pack, CapabilitySemanticMergePreview preview,
            String mirror, List<String> benchIds, String candidate, boolean requireMerge, boolean requireMirror,
            boolean requireBench, boolean requirePerformance) throws Exception {
        HydraReleaseGateRequest request = new HydraReleaseGateRequest("hydra.example", version, pack, preview, mirror,
                benchIds, candidate.isEmpty() ? "" : "perf-base", candidate, requireMerge, requireMirror, requireBench,
                requirePerformance, Map.of());
        MockHttpServletResponse response = mvc.perform(post("/api/release-gates").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private JsonNode allFive(String version, String benchId) throws Exception {
        CapabilitySemanticMergePreview preview = preview(EXISTING_LAYER, RECOVERED_LAYER);
        return evaluate(version, preview.mergedPackage(), preview, mirrorId, List.of(benchId), "perf-good", true, true, true, true);
    }

    private static JsonNode check(JsonNode report, String code) {
        for (JsonNode check : report.get("checks")) {
            if (check.get("code").asString().equals(code)) return check;
        }
        throw new AssertionError("check not present: " + code);
    }

    private static String status(JsonNode report, String code) {
        return check(report, code).get("status").asString();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("publication_generate_publish.{gt,ok,cert} / recovery_author_governance.{a,b,c,d}: las cinco comprobaciones reales pasan y el informe queda atestiguado")
    void fiveRealChecksPassAndTheReportIsAttestedOnDisk() throws Exception {
        String benchId = benchReportId(true);
        CapabilitySemanticMergePreview preview = preview(EXISTING_LAYER, RECOVERED_LAYER);
        assertThat(preview.applicable()).isTrue();
        assertThat(preview.mergedPackage().canonicalDocuments().get("validations.json").get("rules").size()).isEqualTo(2);

        JsonNode report = evaluate("1.0.0", preview.mergedPackage(), preview, mirrorId, List.of(benchId), "perf-good",
                true, true, true, true);

        assertThat(report.get("passed").asBoolean()).isTrue();
        assertThat(report.get("checks").size()).isEqualTo(5);
        for (String code : List.of("PACKAGE", "SEMANTIC_MERGE", "P2_MIRROR", "ADAPTER_BENCH", "PERFORMANCE")) {
            assertThat(status(report, code)).as(code).isEqualTo("PASS");
        }
        String fingerprint = report.get("packageFingerprint").asString();
        assertThat(check(report, "PACKAGE").get("evidence").asString()).isEqualTo(fingerprint);
        assertThat(check(report, "SEMANTIC_MERGE").get("evidence").asString()).isEqualTo(preview.previewId() + ":" + fingerprint);
        assertThat(check(report, "ADAPTER_BENCH").get("evidence").asString()).startsWith(benchId + ":");
        assertThat(check(report, "PERFORMANCE").get("evidence").asString()).startsWith("perf-good:").contains(":baseline=perf-base:");
        assertThat(check(report, "P2_MIRROR").get("evidence").asString()).matches("[0-9a-f]{64}");

        String attestation = report.get("attestationSha256").asString();
        String reportId = report.get("reportId").asString();
        assertThat(reportId).isEqualTo("release-gate-" + attestation.substring(0, 24));
        JsonNode onDisk = json.readTree(Files.readString(gates.resolve(reportId + ".json")));
        assertThat(onDisk.get("passed").asBoolean()).isTrue();
        assertThat(onDisk.get("mirrorId").asString()).isEqualTo(mirrorId);

        // Oraculo independiente: la atestacion es el SHA-256 de la evidencia canonica recompuesta aqui desde el disco.
        Map<String, Object> evidence = new TreeMap<>();
        evidence.put("productId", onDisk.get("productId").asString());
        evidence.put("version", onDisk.get("version").asString());
        evidence.put("packageFingerprint", onDisk.get("packageFingerprint").asString());
        evidence.put("mirrorId", onDisk.get("mirrorId").asString());
        evidence.put("adapterBenchReportIds", onDisk.get("adapterBenchReportIds"));
        evidence.put("performanceBaseline", onDisk.get("performanceBaselineReportId").asString());
        evidence.put("performanceCandidate", onDisk.get("performanceCandidateReportId").asString());
        evidence.put("checks", onDisk.get("checks"));
        evidence.put("annotations", onDisk.get("annotations"));
        assertThat(sha256(json.writeValueAsBytes(evidence))).isEqualTo(attestation);

        MockHttpServletResponse byId = mvc.perform(get("/api/release-gates/" + reportId)).andReturn().getResponse();
        assertThat(byId.getStatus()).isEqualTo(200);
        assertThat(json.readTree(byId.getContentAsString()).get("attestationSha256").asString()).isEqualTo(attestation);
        assertThat(service.assertPassed(reportId).reportId()).isEqualTo(reportId);
    }

    @Test
    @DisplayName("publication_generate_publish.{ok,cert,blk} / recovery_author_governance.c: un banco con un fixture fallido bloquea el release")
    void aFailingAdapterBenchBlocksTheReleaseAndCannotBeCertified() throws Exception {
        String benchId = benchReportId(false);
        assertThat(bench.find(benchId).failedFixtures()).isEqualTo(1);

        JsonNode report = allFive("2.0.0", benchId);

        assertThat(report.get("passed").asBoolean()).isFalse();
        assertThat(status(report, "ADAPTER_BENCH")).isEqualTo("FAIL");
        for (String code : List.of("PACKAGE", "SEMANTIC_MERGE", "P2_MIRROR", "PERFORMANCE")) {
            assertThat(status(report, code)).as(code).isEqualTo("PASS");
        }
        String reportId = report.get("reportId").asString();
        assertThat(gates.resolve(reportId + ".json")).isRegularFile();
        assertThatThrownBy(() -> service.assertPassed(reportId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("blocking checks");

        JsonNode unknown = allFive("2.0.1", "adapter-bench-does-not-exist");
        assertThat(status(unknown, "ADAPTER_BENCH")).isEqualTo("FAIL");
        assertThat(check(unknown, "ADAPTER_BENCH").get("evidence").asString()).isEqualTo("adapter-bench-does-not-exist:missing");
        assertThat(unknown.get("passed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("publication_generate_publish.blk / recovery_author_governance.r: el HTML de un informe no apto detalla una fila por comprobacion")
    void theHtmlOfABlockedReportListsEveryCheckWithItsStatus() throws Exception {
        JsonNode blocked = allFive("3.0.0", benchReportId(false));
        String blockedId = blocked.get("reportId").asString();

        MockHttpServletResponse html = mvc.perform(get("/api/release-gates/" + blockedId + "/html")).andReturn().getResponse();

        assertThat(html.getStatus()).isEqualTo(200);
        assertThat(html.getContentType()).startsWith("text/html");
        assertThat(html.getHeader("Content-Disposition")).contains(blockedId + ".html");
        String body = html.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("Blocked").contains(blocked.get("attestationSha256").asString());
        assertThat(body.split("<tr><td>", -1).length - 1).isEqualTo(5);
        assertThat(body).contains("<td>ADAPTER_BENCH</td><td class=\"FAIL\">FAIL</td>");
        for (String code : List.of("PACKAGE", "SEMANTIC_MERGE", "P2_MIRROR", "PERFORMANCE")) {
            assertThat(body).contains("<td>" + code + "</td><td class=\"PASS\">PASS</td>");
        }

        JsonNode passing = allFive("3.0.1", benchReportId(true));
        String passingHtml = mvc.perform(get("/api/release-gates/" + passing.get("reportId").asString() + "/html"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(passingHtml).contains("Passed").doesNotContain("class=\"FAIL\">FAIL");
    }

    @Test
    @DisplayName("recovery_author_governance.a: la fusion semantica solo pasa con vista previa aplicable y que coincide con el paquete")
    void theSemanticMergeCheckRequiresAnApplicablePreviewMatchingTheReleasePackage() throws Exception {
        String benchId = benchReportId(true);
        CapabilitySemanticMergePreview dangling = preview(DANGLING_LAYER, DANGLING_LAYER);
        assertThat(dangling.applicable()).isFalse();
        JsonNode unresolved = evaluate("4.0.0", dangling.mergedPackage(), dangling, mirrorId, List.of(benchId), "perf-good",
                true, true, true, true);
        assertThat(status(unresolved, "SEMANTIC_MERGE")).isEqualTo("FAIL");
        assertThat(unresolved.get("passed").asBoolean()).isFalse();

        CapabilitySemanticMergePreview valid = preview(EXISTING_LAYER, RECOVERED_LAYER);
        JsonNode other = evaluate("4.0.1", capabilityPackage(EXISTING_LAYER), valid, mirrorId, List.of(benchId), "perf-good",
                true, true, true, true);
        assertThat(status(other, "SEMANTIC_MERGE")).isEqualTo("FAIL");
        assertThat(status(other, "PACKAGE")).isEqualTo("PASS");
        assertThat(other.get("passed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("recovery_author_governance.{b,d}: exigir evidencia ausente bloquea; no exigirla solo avisa")
    void requiredEvidenceThatIsMissingBlocksWhileOptionalEvidenceOnlyWarns() throws Exception {
        HydraCapabilityPackage pack = capabilityPackage(EXISTING_LAYER);

        JsonNode required = evaluate("5.0.0", pack, null, "", List.of(), "", true, true, true, true);
        assertThat(required.get("passed").asBoolean()).isFalse();
        for (String code : List.of("SEMANTIC_MERGE", "P2_MIRROR", "ADAPTER_BENCH", "PERFORMANCE")) {
            assertThat(status(required, code)).as(code).isEqualTo("FAIL");
        }
        assertThat(status(required, "PACKAGE")).isEqualTo("PASS");

        JsonNode optional = evaluate("5.0.1", pack, null, "", List.of(), "", false, false, false, false);
        assertThat(optional.get("passed").asBoolean()).isTrue();
        for (String code : List.of("SEMANTIC_MERGE", "P2_MIRROR", "ADAPTER_BENCH", "PERFORMANCE")) {
            assertThat(status(optional, code)).as(code).isEqualTo("WARNING");
        }
    }

    @Test
    @DisplayName("recovery_author_governance.r: alterar el informe almacenado invalida la atestacion en todas las lecturas")
    void tamperingWithTheStoredReportIsDetectedByEveryRead() throws Exception {
        JsonNode report = allFive("6.0.0", benchReportId(true));
        String reportId = report.get("reportId").asString();
        Path stored = gates.resolve(reportId + ".json");
        assertThat(mvc.perform(get("/api/release-gates/" + reportId)).andReturn().getResponse().getStatus()).isEqualTo(200);

        Files.writeString(stored, Files.readString(stored).replace("hydra.example", "hydra.tampered"));

        assertThat(mvc.perform(get("/api/release-gates/" + reportId)).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(mvc.perform(get("/api/release-gates/" + reportId + "/html")).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThatThrownBy(() -> service.assertPassed(reportId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("attestation");
    }
}
