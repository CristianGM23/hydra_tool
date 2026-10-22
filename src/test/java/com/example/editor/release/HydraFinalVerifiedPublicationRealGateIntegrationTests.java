package com.example.editor.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;
import com.example.editor.model.ModelSessionProperties;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.provenance.HydraDistributionProvenanceController;
import com.example.editor.provenance.HydraDistributionProvenanceProperties;
import com.example.editor.provenance.HydraDistributionProvenanceRequest;
import com.example.editor.provenance.HydraDistributionProvenanceService;
import com.example.editor.provenance.HydraLicensePolicy;
import com.example.editor.standalone.publication.DistributionPublicationController;
import com.example.editor.standalone.publication.DistributionPublicationService;
import com.example.editor.standalone.publication.DistributionPublicationValidator;
import com.example.editor.standalone.publication.PublicationArtifactRequest;
import com.example.editor.standalone.publication.PublicationChannel;
import com.example.editor.standalone.publication.PublicationRequest;
import com.example.editor.standalone.publication.PublicationSigningMode;
import com.example.editor.system.HydraPlatformReadinessService;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publicacion verificada de extremo a extremo por HTTP: puerta de release real (paquete valido, resto opcional),
 * informe de procedencia real, disponibilidad de la plataforma real y publicacion real que escribe en disco. Sin
 * simulaciones: los colaboradores de procedencia que solo se usan con evidencias opcionales (orquestacion, espejo,
 * adaptadores, rendimiento) no se aportan porque la peticion no los referencia.
 *
 * Etapa: publication_generate_publish.v.
 * Oraculos: ficheros del directorio de publicacion leidos del disco (manifiesto, copia del artefacto, SHA-256 recalculado
 * aqui) y ausencia de directorio de publicacion cuando se rechaza; informe de puerta leido por GET.
 */
class HydraFinalVerifiedPublicationRealGateIntegrationTests {
    private static final byte[] ARTIFACT = "archive-bytes-for-the-verified-release".getBytes(StandardCharsets.UTF_8);
    @TempDir Path temporary;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private Path publications;
    private MockMvc mvc;

    @BeforeEach
    void wire() throws Exception {
        Path distributions = temporary.resolve("distributions");
        publications = temporary.resolve("publications");
        Files.createDirectories(distributions.resolve("sample"));
        Files.write(distributions.resolve("sample/editor.zip"), ARTIFACT);
        HydraReleaseGateService gates = new HydraReleaseGateService(new CapabilityPackageChecker(), null, null, null, json,
                temporary.resolve("gates").toString(), 200);
        HydraPlatformReadinessService readiness = new HydraPlatformReadinessService(24,
                temporary.resolve("gates").toString(), 200, new ModelSessionProperties());
        HydraDistributionProvenanceProperties provenanceProperties = new HydraDistributionProvenanceProperties();
        provenanceProperties.setStorageRoot(temporary.resolve("provenance").toString());
        HydraDistributionProvenanceService provenance = new HydraDistributionProvenanceService(new CapabilityPackageChecker(),
                null, null, null, null, null, gates, provenanceProperties, json);
        DistributionPublicationService publicationService = new DistributionPublicationService(
                new DistributionPublicationValidator(), json, distributions.toString(), publications.toString());
        HydraFinalVerifiedPublicationService verified = new HydraFinalVerifiedPublicationService(
                new HydraReleaseCandidateService(gates, readiness), provenance, publicationService);
        mvc = MockMvcBuilders.standaloneSetup(new HydraReleaseGateController(gates),
                new HydraDistributionProvenanceController(provenance), new HydraFinalVerifiedPublicationController(verified),
                new DistributionPublicationController(publicationService))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private HydraCapabilityPackage capabilityPackage() throws Exception {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package",
                "Example package", "1.0.0", "example", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest,
                Map.of("validations.json", json.readTree("{\"rules\":[{\"id\":\"rule-name\",\"name\":\"NoEmptyName\"}]}")), Map.of());
    }

    private JsonNode postJson(String url, Object body) throws Exception {
        MockHttpServletResponse response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(url + " " + response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString());
    }

    private String gate(String version, boolean requireMirror) throws Exception {
        JsonNode report = postJson("/api/release-gates", new HydraReleaseGateRequest("hydra.example", version,
                capabilityPackage(), null, "", List.of(), "", "", false, requireMirror, false, false, Map.of()));
        assertThat(report.get("passed").asBoolean()).isEqualTo(!requireMirror);
        return report.get("reportId").asString();
    }

    private JsonNode provenance(String version, String gateId) throws Exception {
        return postJson("/api/distribution-provenance", new HydraDistributionProvenanceRequest("hydra.example", version,
                UpdateSiteMirrorChannel.INTERNAL, HydraLicensePolicy.COMPATIBLE_ONLY, capabilityPackage(), "", "",
                List.of(), "", gateId, Map.of()));
    }

    private PublicationRequest publication(String product, String version) {
        return new PublicationRequest(product, version, PublicationChannel.INTERNAL, PublicationSigningMode.CHECKSUM_ONLY,
                "sample", "", List.of(new PublicationArtifactRequest("windows-x64", "editor.zip", "application/zip")), Map.of());
    }

    private int verifiedStatus(String gateId, String provenanceId, String product, String version) throws Exception {
        return mvc.perform(post("/api/publications/final-verified/publish").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new HydraFinalVerifiedPublicationRequest(gateId, provenanceId,
                        publication(product, version))))).andReturn().getResponse().getStatus();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Path releaseDirectory(String product, String version) {
        return publications.resolve(product).resolve("internal").resolve(version);
    }

    @Test
    @DisplayName("publication_generate_publish.v: la via sencilla publica sin puerta y escribe manifiesto y artefacto")
    void theSimpleRoutePublishesWithoutAnyGate() throws Exception {
        postJson("/api/publications/publish", publication("hydra.simple", "1.0.0"));

        Path release = releaseDirectory("hydra.simple", "1.0.0");
        JsonNode manifest = json.readTree(Files.readString(release.resolve("manifest.json")));
        assertThat(manifest.get("productId").asString()).isEqualTo("hydra.simple");
        assertThat(manifest.get("version").asString()).isEqualTo("1.0.0");
        assertThat(manifest.get("artifacts").get(0).get("sha256").asString()).isEqualTo(sha256(ARTIFACT));
        assertThat(Files.readAllBytes(release.resolve("editor.zip"))).isEqualTo(ARTIFACT);
        assertThat(publications.resolve("hydra.simple/internal/latest.json")).isRegularFile();
    }

    @Test
    @DisplayName("publication_generate_publish.v: la via verificada publica solo con puerta apta, procedencia valida y plataforma lista")
    void theVerifiedRoutePublishesWhenGateProvenanceAndPlatformAreSound() throws Exception {
        String gateId = gate("1.0.0", false);
        MockHttpServletResponse gateRead = mvc.perform(get("/api/release-gates/" + gateId)).andReturn().getResponse();
        assertThat(gateRead.getStatus()).isEqualTo(200);
        assertThat(json.readTree(gateRead.getContentAsString()).get("passed").asBoolean()).isTrue();
        JsonNode sbom = provenance("1.0.0", gateId);
        assertThat(sbom.get("valid").asBoolean()).isTrue();
        assertThat(releaseDirectory("hydra.example", "1.0.0")).doesNotExist();

        JsonNode result = postJson("/api/publications/final-verified/publish", new HydraFinalVerifiedPublicationRequest(
                gateId, sbom.get("reportId").asString(), publication("hydra.example", "1.0.0")));

        Path release = releaseDirectory("hydra.example", "1.0.0");
        JsonNode onDisk = json.readTree(Files.readString(release.resolve("manifest.json")));
        assertThat(onDisk.get("productId").asString()).isEqualTo("hydra.example");
        assertThat(onDisk.get("version").asString()).isEqualTo("1.0.0");
        assertThat(onDisk.get("artifacts").get(0).get("sha256").asString()).isEqualTo(sha256(ARTIFACT));
        assertThat(Files.readAllBytes(release.resolve("editor.zip"))).isEqualTo(ARTIFACT);
        assertThat(Path.of(result.get("manifestPath").asString())).isEqualTo(release.resolve("manifest.json"));
        JsonNode latest = json.readTree(Files.readString(publications.resolve("hydra.example/internal/latest.json")));
        assertThat(latest.get("version").asString()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("publication_generate_publish.v: con la puerta no apta la via verificada rechaza y no escribe nada")
    void theVerifiedRouteRejectsABlockedGateAndWritesNothing() throws Exception {
        String blockedGate = gate("1.0.1", true);
        JsonNode sbom = provenance("1.0.1", blockedGate);
        assertThat(sbom.get("valid").asBoolean()).isFalse();

        int status = verifiedStatus(blockedGate, sbom.get("reportId").asString(), "hydra.example", "1.0.1");

        assertThat(status).isEqualTo(409);
        assertThat(publications).doesNotExist();
    }

    @Test
    @DisplayName("publication_generate_publish.v: producto distinto, procedencia de otra version o identificadores ausentes se rechazan sin escribir")
    void theVerifiedRouteRejectsMismatchedOrMissingEvidenceAndStillPublishesTheCorrectRequest() throws Exception {
        String gateId = gate("1.0.0", false);
        String sbomId = provenance("1.0.0", gateId).get("reportId").asString();
        JsonNode foreignVersion = provenance("1.0.9", gateId);
        assertThat(foreignVersion.get("valid").asBoolean()).isFalse();

        assertThat(verifiedStatus(gateId, sbomId, "hydra.other", "1.0.0")).isEqualTo(400);
        assertThat(verifiedStatus(gateId, foreignVersion.get("reportId").asString(), "hydra.example", "1.0.0")).isEqualTo(400);
        assertThat(verifiedStatus("release-gate-does-not-exist", sbomId, "hydra.example", "1.0.0")).isEqualTo(400);
        assertThat(verifiedStatus("", sbomId, "hydra.example", "1.0.0")).isEqualTo(400);
        assertThat(verifiedStatus(gateId, "", "hydra.example", "1.0.0")).isEqualTo(400);
        assertThat(publications).doesNotExist();

        assertThat(verifiedStatus(gateId, sbomId, "hydra.example", "1.0.0")).isEqualTo(200);
        assertThat(releaseDirectory("hydra.example", "1.0.0").resolve("manifest.json")).isRegularFile();
    }
}
