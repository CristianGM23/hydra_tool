package com.example.editor.provenance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterVerificationReport;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterVerificationService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorUpdateReport;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorUpdateService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorArtifact;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorResult;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorService;
import com.example.editor.performance.CapabilityPerformanceAutomationReport;
import com.example.editor.performance.CapabilityPerformanceAutomationService;
import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.orchestration.CapabilityOrchestrationPreview;
import com.example.editor.portability.orchestration.CapabilityOrchestrationService;
import com.example.editor.release.HydraReleaseGateReport;
import com.example.editor.release.HydraReleaseGateService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Builds and verifies a distribution inventory linking package, mirror, adapters, performance and AQ. */
@Service
public class HydraDistributionProvenanceService {
    private final CapabilityPackageChecker packageChecker;
    private final CapabilityOrchestrationService orchestrations;
    private final UpdateSiteMirrorUpdateService mirrorUpdates;
    private final UpdateSiteP2MirrorService mirrors;
    private final UpdateSiteAdapterVerificationService adapters;
    private final CapabilityPerformanceAutomationService performance;
    private final HydraReleaseGateService gates;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;
    private final HydraDistributionProvenanceProperties properties;

    /** Descripción: Inicializa una instancia de HydraDistributionProvenanceService con sus dependencias y datos de entrada. Entradas: packageChecker: dato de entrada necesario para la operación; orchestrations: dato de entrada necesario para la operación; mirrorUpdates: dato de entrada necesario para la operación; mirrors: dato de entrada necesario para la operación; adapters: dato de entrada necesario para la operación; performance: dato de entrada necesario para la operación; gates: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraDistributionProvenanceService(CapabilityPackageChecker packageChecker,
            CapabilityOrchestrationService orchestrations, UpdateSiteMirrorUpdateService mirrorUpdates,
            UpdateSiteP2MirrorService mirrors, UpdateSiteAdapterVerificationService adapters,
            CapabilityPerformanceAutomationService performance, HydraReleaseGateService gates,
            HydraDistributionProvenanceProperties properties, ObjectMapper mapper) {
        this.packageChecker = packageChecker; this.orchestrations = orchestrations;
        this.mirrorUpdates = mirrorUpdates; this.mirrors = mirrors; this.adapters = adapters;
        this.performance = performance; this.gates = gates; this.properties = properties; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public synchronized HydraDistributionProvenanceReport evaluate(HydraDistributionProvenanceRequest request) {
        require(request); List<HydraSbomComponent> components = new ArrayList<>(); List<String> diagnostics = new ArrayList<>();
        String packageFingerprint = fingerprint(request.capabilityPackage());
        CapabilityPackageCheckReport packageReport = packageChecker.check(request.capabilityPackage());
        if(!packageReport.valid()) diagnostics.add("Capability package validation failed");
        addPackageComponents(request.capabilityPackage(), packageFingerprint, components);
        if(!request.orchestrationId().isBlank()) checkOrchestration(request.orchestrationId(), packageFingerprint, components, diagnostics);
        if(!request.mirrorUpdateReportId().isBlank()) checkMirrorUpdate(request, components, diagnostics);
        for(String reportId : request.adapterVerificationReportIds()) checkAdapter(reportId, components, diagnostics);
        if(!request.performanceAutomationReportId().isBlank()) checkPerformance(request.performanceAutomationReportId(), components, diagnostics);
        checkGate(request, components, diagnostics);
        checkLicenses(request, components, diagnostics);
        components.sort(Comparator.comparing(HydraSbomComponent::componentId));
        boolean valid = diagnostics.isEmpty();
        Map<String, Object> evidence = new LinkedHashMap<>(); evidence.put("schemaVersion", "1.0"); evidence.put("productId", request.productId()); evidence.put("version", request.version());
        evidence.put("channel", request.channel()); evidence.put("licensePolicy", request.licensePolicy()); evidence.put("components", components); evidence.put("valid", valid);
        evidence.put("diagnostics", diagnostics); evidence.put("annotations", request.annotations());
        String attestation = fingerprint(evidence); String reportId = "distribution-sbom-" + attestation.substring(0, 24);
        HydraDistributionProvenanceReport report = new HydraDistributionProvenanceReport(reportId, "1.0",
                request.productId(), request.version(), request.channel(), request.licensePolicy(), Instant.now(),
                components, valid, diagnostics, attestation, request.annotations());
        atomicWrite(reportPath(reportId), write(report)); trim(); return report;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de HydraDistributionProvenanceService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraDistributionProvenanceReport find(String reportId) {
        try {return verified(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(reportPath(reportId)), HydraDistributionProvenanceReport.class));}
        catch(JacksonException | IOException exception){throw new IllegalArgumentException("Distribution provenance report was not found", exception);}
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraDistributionProvenanceReport> reports() {
        if(!Files.isDirectory(storageRoot())) return List.of();
        try(var stream = Files.list(storageRoot())) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .flatMap(path -> {try{return java.util.stream.Stream.of(verified(mapper.readValue(read(path), HydraDistributionProvenanceReport.class)));}
                        catch(RuntimeException exception){return java.util.stream.Stream.empty();}}).limit(properties.getMaximumReports()).toList();
        } catch(IOException exception){throw new IllegalStateException("Could not list distribution provenance reports", exception);}
    }

    /** Descripción: Ejecuta la operación «assertValid» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: reportId: identificador que delimita el recurso objetivo; productId: identificador que delimita el recurso objetivo; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraDistributionProvenanceReport assertValid(String reportId, String productId, String version) {
        HydraDistributionProvenanceReport report = find(reportId);
        if(!report.valid()) throw new IllegalArgumentException("Distribution provenance report contains blocking diagnostics");
        if(!report.productId().equals(text(productId)) || !report.version().equals(text(version))) throw new IllegalArgumentException("Publication identity differs from the provenance report");
        return report;
    }

    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] export(String reportId) {return write(find(reportId));}

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] html(String reportId) {
        HydraDistributionProvenanceReport report = find(reportId);
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>HYDRA distribution SBOM</title>")
                .append("<style>body{font-family:system-ui;margin:2rem;max-width:1200px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:.45rem;text-align:left}.ok{color:#176b35}.bad{color:#a12622}code{overflow-wrap:anywhere}</style></head><body>")
                .append("<h1>HYDRA distribution inventory</h1><p><strong>").append(escape(report.productId())).append(" ").append(escape(report.version())).append("</strong></p><p class=\"")
                .append(report.valid() ? "ok\">Verified" : "bad\">Blocked").append("</p><p>Attestation <code>").append(report.attestationSha256()).append("</code></p>")
                .append("<table><tr><th>Component</th><th>Type</th><th>Version</th><th>License</th><th>SHA-256</th></tr>");
        report.components().forEach(item -> html.append("<tr><td>").append(escape(item.name())).append("</td><td>")
                .append(escape(item.type())).append("</td><td>").append(escape(item.version())).append("</td><td>")
                .append(escape(item.license())).append("</td><td><code>").append(escape(item.sha256())).append("</code></td></tr>"));
        html.append("</table></body></html>"); return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «addPackageComponents» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: capabilityPackage: dato de entrada necesario para la operación; fingerprint: dato de entrada necesario para la operación; components: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addPackageComponents(HydraCapabilityPackage capabilityPackage, String fingerprint, List<HydraSbomComponent> components) {
        String packageId = capabilityPackage.manifest() == null ? "package" : capabilityPackage.manifest().id();
        String version = capabilityPackage.manifest() == null ? "" : capabilityPackage.manifest().packageVersion();
        String origin = capabilityPackage.manifest() == null ? "" : capabilityPackage.manifest().metamodelOrigin().sourceUri();
        components.add(new HydraSbomComponent("pkg:" + packageId, "HYDRA_PACKAGE", packageId, version,
                fingerprint, "", origin, List.of(), Map.of("metamodelId", capabilityPackage.manifest() == null ? "" : capabilityPackage.manifest().metamodelId())));
        capabilityPackage.canonicalDocuments().forEach((path, node) -> components.add(new HydraSbomComponent("document:" + path,
                "CANONICAL_DOCUMENT", path, "", fingerprint(node), "", origin, List.of("pkg:" + packageId), Map.of())));
        capabilityPackage.resources().forEach((path, value) -> components.add(new HydraSbomComponent("resource:" + path,
                "RESOURCE", path, "", sha256((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)), "", origin, List.of("pkg:" + packageId), Map.of())));
    }

    /** Descripción: Valida las condiciones necesarias para «checkOrchestration» y rechaza entradas no válidas. Entradas: orchestrationId: identificador que delimita el recurso objetivo; packageFingerprint: dato de entrada necesario para la operación; components: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkOrchestration(String orchestrationId, String packageFingerprint,
            List<HydraSbomComponent> components, List<String> diagnostics) {
        try {
            CapabilityOrchestrationPreview preview = orchestrations.find(orchestrationId);
            String output = fingerprint(preview.mergedPackage());
            if(!preview.applicable() || !output.equals(packageFingerprint)) diagnostics.add("AR orchestration is blocked or differs from the selected package");
            components.add(new HydraSbomComponent("orchestration:" + orchestrationId, "ORCHESTRATION", orchestrationId,
                    "", output, "", "HYDRA AR", List.of(), Map.of("steps", String.valueOf(preview.steps().size()))));
        } catch(RuntimeException exception){diagnostics.add("AR orchestration evidence could not be verified: " + safeMessage(exception));}
    }

    /** Descripción: Valida las condiciones necesarias para «checkMirrorUpdate» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; components: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkMirrorUpdate(HydraDistributionProvenanceRequest request,
            List<HydraSbomComponent> components, List<String> diagnostics) {
        try {
            UpdateSiteMirrorUpdateReport update = mirrorUpdates.find(request.mirrorUpdateReportId());
            if(!update.promotable() || update.channel() != request.channel()) diagnostics.add("AS mirror update is not promotable for the selected channel");
            UpdateSiteP2MirrorResult mirror = mirrors.find(update.candidateMirrorId());
            components.add(new HydraSbomComponent("mirror:" + update.candidateMirrorId(), "P2_MIRROR",
                    update.candidateMirrorId(), "", mirror.manifest().mirrorSha256(), "", "HYDRA AN/AS",
                    List.of(), Map.of("channel", update.channel().name(), "baseline", update.baselineMirrorId())));
            for(UpdateSiteP2MirrorArtifact artifact : mirror.manifest().artifacts()) {
                components.add(new HydraSbomComponent("p2:" + artifact.classifier() + ":" + artifact.id(), "P2_ARTIFACT",
                        artifact.id(), artifact.version(), artifact.sha256(), artifact.license(), artifact.path(),
                        List.of("mirror:" + update.candidateMirrorId()), Map.of("classifier", artifact.classifier(), "bytes", String.valueOf(artifact.bytes()))));
            }
        } catch(RuntimeException exception){diagnostics.add("AS mirror evidence could not be verified: " + safeMessage(exception));}
    }

    /** Descripción: Valida las condiciones necesarias para «checkAdapter» y rechaza entradas no válidas. Entradas: reportId: identificador que delimita el recurso objetivo; components: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkAdapter(String reportId, List<HydraSbomComponent> components, List<String> diagnostics) {
        try {
            UpdateSiteAdapterVerificationReport report = adapters.find(reportId);
            if(!report.passed()) diagnostics.add("AT adapter verification failed for " + report.adapterId());
            components.add(new HydraSbomComponent("adapter:" + report.adapterId() + ":" + report.adapterVersion(),
                    "DECLARATIVE_ADAPTER", report.adapterId(), report.adapterVersion(), report.adapterFingerprint(),
                    "", "HYDRA adapter SDK", List.of(), Map.of("verificationReportId", report.reportId(), "benchReportId", report.benchReportId())));
        } catch(RuntimeException exception){diagnostics.add("AT adapter evidence could not be verified: " + safeMessage(exception));}
    }

    /** Descripción: Valida las condiciones necesarias para «checkPerformance» y rechaza entradas no válidas. Entradas: reportId: identificador que delimita el recurso objetivo; components: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkPerformance(String reportId, List<HydraSbomComponent> components, List<String> diagnostics) {
        try {
            CapabilityPerformanceAutomationReport report = performance.find(reportId);
            if(!report.passed()) diagnostics.add("AU performance automation failed or is noisy");
            components.add(new HydraSbomComponent("performance:" + report.reportId(), "PERFORMANCE_SUITE",
                    report.scenarioId(), "", fingerprint(report), "", "HYDRA AU", List.of(),
                    Map.of("capabilityId", report.capabilityId(), "baselineId", report.baselineId())));
        } catch(RuntimeException exception){diagnostics.add("AU performance evidence could not be verified: " + safeMessage(exception));}
    }

    /** Descripción: Valida las condiciones necesarias para «checkGate» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; components: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkGate(HydraDistributionProvenanceRequest request, List<HydraSbomComponent> components, List<String> diagnostics) {
        if(request.releaseGateReportId().isBlank()) {diagnostics.add("AQ release gate evidence is required"); return;}
        try {
            HydraReleaseGateReport gate = gates.find(request.releaseGateReportId());
            if(!gate.passed() || !gate.productId().equals(request.productId()) || !gate.version().equals(request.version())) diagnostics.add("AQ release gate is blocked or targets another product/version");
            components.add(new HydraSbomComponent("release-gate:" + gate.reportId(), "RELEASE_GATE", gate.productId(),
                    gate.version(), gate.attestationSha256(), "", "HYDRA AQ", List.of(), Map.of("passed", String.valueOf(gate.passed()))));
        } catch(RuntimeException exception){diagnostics.add("AQ release gate evidence could not be verified: " + safeMessage(exception));}
    }

    /** Descripción: Valida las condiciones necesarias para «checkLicenses» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; components: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkLicenses(HydraDistributionProvenanceRequest request, List<HydraSbomComponent> components, List<String> diagnostics) {
        long unknown = components.stream().filter(item -> "P2_ARTIFACT".equals(item.type()) && item.license().isBlank()).count();
        if(request.licensePolicy() == HydraLicensePolicy.STRICT && unknown > 0) diagnostics.add("Strict license policy rejects " + unknown + " p2 artifact(s) without an explicit license");
        if(request.channel().name().equals("STABLE") && request.licensePolicy() == HydraLicensePolicy.ALLOW_UNKNOWN) diagnostics.add("Stable channel cannot use ALLOW_UNKNOWN license policy");
    }

    /** Descripción: Ejecuta la operación «verified» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraDistributionProvenanceReport verified(HydraDistributionProvenanceReport report) {
        Map<String, Object> evidence = new LinkedHashMap<>(); evidence.put("schemaVersion", report.schemaVersion()); evidence.put("productId", report.productId()); evidence.put("version", report.version());
        evidence.put("channel", report.channel()); evidence.put("licensePolicy", report.licensePolicy()); evidence.put("components", report.components()); evidence.put("valid", report.valid());
        evidence.put("diagnostics", report.diagnostics()); evidence.put("annotations", report.annotations());
        if(!fingerprint(evidence).equals(report.attestationSha256())) throw new IllegalStateException("Distribution provenance attestation no longer matches its evidence"); return report;
    }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(HydraDistributionProvenanceRequest request){if(request == null || request.productId().isBlank() || request.version().isBlank() || request.capabilityPackage() == null) throw new IllegalArgumentException("productId, version and capabilityPackage are required");}
    /** Descripción: Ejecuta la operación «storageRoot» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path storageRoot(){return properties.storageRootPath();}
    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String id){String safe = text(id); if(!safe.matches("[A-Za-z0-9._-]{1,180}")) throw new IllegalArgumentException("Invalid distribution provenance report ID"); return storageRoot().resolve(safe + ".json");}
    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(Object value){return sha256(write(value));}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] value){return com.example.editor.metamodel.importing.updatesite.UpdateSiteHashing.sha256(value);}
    /** Descripción: Actualiza el estado gestionado por HydraDistributionProvenanceService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value){try{return writer.writeValueAsBytes(value);}catch(Exception exception){throw new IllegalStateException("Could not serialize distribution provenance evidence", exception);}}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de HydraDistributionProvenanceService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path){try{return com.example.editor.io.BoundedIo.readInternal(path);}catch(IOException exception){throw new IllegalStateException("Could not read distribution provenance evidence", exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path path, byte[] bytes){try{com.example.editor.io.AtomicFiles.writeReplacing(path,bytes);}catch(IOException exception){throw new IllegalStateException("Could not persist distribution provenance evidence", exception);}}
    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trim(){List<Path> paths; try{Files.createDirectories(storageRoot()); try(var stream = Files.list(storageRoot())){paths = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted(Comparator.comparingLong(this::modified).reversed()).toList();}}catch(IOException exception){return;} for(int index = properties.getMaximumReports(); index < paths.size(); index++){try{Files.deleteIfExists(paths.get(index));}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/provenance/HydraDistributionProvenanceService.java",ignored);}}}
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException exception){return 0L;}}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");}
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: throwable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable throwable){String value = throwable.getMessage(); return value == null || value.isBlank() ? throwable.getClass().getSimpleName() : value;}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraDistributionProvenanceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
