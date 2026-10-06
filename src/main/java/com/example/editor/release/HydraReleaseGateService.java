package com.example.editor.release;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterBenchReport;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterBenchService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorResult;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorService;
import com.example.editor.performance.CapabilityPerformanceComparison;
import com.example.editor.performance.CapabilityPerformanceReport;
import com.example.editor.performance.CapabilityPerformanceService;
import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.merge.CapabilitySemanticMergeDependency;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Aggregates AM-AP evidence into an immutable, reproducible release decision. */
@Service
public class HydraReleaseGateService {
    private final CapabilityPackageChecker packageChecker;
    private final UpdateSiteP2MirrorService mirrors;
    private final UpdateSiteAdapterBenchService adapterBench;
    private final CapabilityPerformanceService performance;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;
    private final Path storageRoot;
    private final int maximumReports;

    /** Descripción: Inicializa una instancia de HydraReleaseGateService con sus dependencias y datos de entrada. Entradas: packageChecker: dato de entrada necesario para la operación; mirrors: dato de entrada necesario para la operación; adapterBench: dato de entrada necesario para la operación; performance: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; storageRoot: dato de entrada necesario para la operación; maximumReports: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraReleaseGateService(CapabilityPackageChecker packageChecker,
            UpdateSiteP2MirrorService mirrors, UpdateSiteAdapterBenchService adapterBench,
            CapabilityPerformanceService performance, ObjectMapper mapper,
            @Value("${hydra.release-gates.root:data/release-gates}") String storageRoot,
            @Value("${hydra.release-gates.maximum-reports:200}") int maximumReports) {
        this.packageChecker = packageChecker; this.mirrors = mirrors; this.adapterBench = adapterBench;
        this.performance = performance; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maximumReports = Math.max(1, Math.min(10_000, maximumReports));
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraReleaseGateReport evaluate(HydraReleaseGateRequest request) {
        require(request);
        List<HydraReleaseGateCheck> checks = new ArrayList<>();
        String packageFingerprint = fingerprint(request.capabilityPackage());
        checkPackage(request.capabilityPackage(), packageFingerprint, checks);
        checkMerge(request, packageFingerprint, checks);
        checkMirror(request, checks);
        checkAdapters(request, checks);
        checkPerformance(request, checks);
        boolean passed = checks.stream().noneMatch(item -> item.status() == HydraReleaseGateStatus.FAIL);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("productId", request.productId()); evidence.put("version", request.version());
        evidence.put("packageFingerprint", packageFingerprint); evidence.put("mirrorId", request.mirrorId());
        evidence.put("adapterBenchReportIds", request.adapterBenchReportIds());
        evidence.put("performanceBaseline", request.performanceBaselineReportId());
        evidence.put("performanceCandidate", request.performanceCandidateReportId()); evidence.put("checks", checks);
        evidence.put("annotations", request.annotations());
        String attestation = sha256(write(evidence));
        String reportId = "release-gate-" + attestation.substring(0, 24);
        HydraReleaseGateReport report = new HydraReleaseGateReport(reportId, request.productId(), request.version(),
                Instant.now(), passed, packageFingerprint, request.mirrorId(), request.adapterBenchReportIds(),
                request.performanceBaselineReportId(), request.performanceCandidateReportId(), checks,
                attestation, request.annotations());
        Path target = reportPath(reportId);
        if(Files.isRegularFile(target)) {
            HydraReleaseGateReport current = read(target);
            if(!current.attestationSha256().equals(attestation)
                    || !attestation(current).equals(current.attestationSha256())) {
                throw new IllegalStateException("Release gate identifier collision or persisted evidence corruption");
            }
            return current;
        }
        atomicWrite(target, write(report));
        trimReports();
        return report;
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraReleaseGateReport> reports() {
        if(!Files.isDirectory(storageRoot)) return List.of();
        try(var stream = Files.list(storageRoot)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .map(path -> {
                        try { return verified(read(path)); }
                        catch(RuntimeException exception) { throw new IllegalStateException("Corrupt or unreadable release gate report: " + path.getFileName(), exception); }
                    }).toList();
        } catch(IOException exception) {throw new IllegalStateException("Could not list release gate reports", exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de HydraReleaseGateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraReleaseGateReport find(String reportId) {return verified(read(reportPath(reportId)));}

    /** Descripción: Ejecuta la operación «assertPassed» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraReleaseGateReport assertPassed(String reportId) {
        HydraReleaseGateReport report = find(reportId);
        if(!report.passed()) throw new IllegalStateException("Release gate contains blocking checks");
        return report;
    }

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] html(String reportId) {
        HydraReleaseGateReport report = find(reportId);
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>HYDRA release gate</title>")
                .append("<style>body{font-family:system-ui;margin:2rem;max-width:1100px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:.5rem;text-align:left}.PASS{color:#176b35}.WARNING{color:#8a5a00}.FAIL{color:#a12622}code{overflow-wrap:anywhere}</style></head><body>")
                .append("<h1>HYDRA release gate</h1><p><strong>").append(escape(report.productId())).append(" ")
                .append(escape(report.version())).append("</strong></p><p class=\"").append(report.passed() ? "PASS\">Passed" : "FAIL\">Blocked")
                .append("</p><p>Attestation <code>").append(report.attestationSha256()).append("</code></p>")
                .append("<table><tr><th>Check</th><th>Status</th><th>Message</th><th>Evidence</th></tr>");
        report.checks().forEach(check -> html.append("<tr><td>").append(escape(check.code())).append("</td><td class=\"")
                .append(check.status()).append("\">").append(check.status()).append("</td><td>")
                .append(escape(check.message())).append("</td><td><code>").append(escape(check.evidence())).append("</code></td></tr>"));
        html.append("</table></body></html>"); return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Valida las condiciones necesarias para «checkPackage» y rechaza entradas no válidas. Entradas: capabilityPackage: dato de entrada necesario para la operación; fingerprint: dato de entrada necesario para la operación; checks: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkPackage(HydraCapabilityPackage capabilityPackage, String fingerprint,
            List<HydraReleaseGateCheck> checks) {
        CapabilityPackageCheckReport report = packageChecker.check(capabilityPackage);
        HydraReleaseGateStatus status = report.valid() ? HydraReleaseGateStatus.PASS : HydraReleaseGateStatus.FAIL;
        String message = report.valid() ? "Capability package identity, dependencies, compatibility and checksums are valid."
                : report.diagnostics().stream().map(item -> item.code() + ": " + item.message()).limit(8).reduce((left, right) -> left + "; " + right).orElse("Capability package is invalid.");
        checks.add(new HydraReleaseGateCheck("PACKAGE", status, message, fingerprint));
    }

    /** Descripción: Valida las condiciones necesarias para «checkMerge» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; packageFingerprint: dato de entrada necesario para la operación; checks: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkMerge(HydraReleaseGateRequest request, String packageFingerprint,
            List<HydraReleaseGateCheck> checks) {
        if(request.semanticMerge() == null) {
            checks.add(new HydraReleaseGateCheck("SEMANTIC_MERGE", request.requireSemanticMerge()
                    ? HydraReleaseGateStatus.FAIL : HydraReleaseGateStatus.WARNING,
                    request.requireSemanticMerge() ? "A semantic merge preview is required." : "No semantic merge evidence was supplied.", ""));
            return;
        }
        long unresolved = request.semanticMerge().dependencies().stream().filter(item -> !item.resolved()).count();
        String mergedFingerprint = fingerprint(request.semanticMerge().mergedPackage());
        boolean valid = request.semanticMerge().applicable() && unresolved == 0 && mergedFingerprint.equals(packageFingerprint);
        checks.add(new HydraReleaseGateCheck("SEMANTIC_MERGE", valid ? HydraReleaseGateStatus.PASS : HydraReleaseGateStatus.FAIL,
                valid ? "AM preview is applicable, dependency-complete and matches the package selected for release."
                        : "AM preview is blocked, contains unresolved dependencies or differs from the release package.",
                request.semanticMerge().previewId() + ":" + mergedFingerprint));
    }

    /** Descripción: Valida las condiciones necesarias para «checkMirror» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; checks: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkMirror(HydraReleaseGateRequest request, List<HydraReleaseGateCheck> checks) {
        if(request.mirrorId().isBlank()) {
            checks.add(new HydraReleaseGateCheck("P2_MIRROR", request.requireMirror() ? HydraReleaseGateStatus.FAIL : HydraReleaseGateStatus.WARNING,
                    request.requireMirror() ? "A validated offline p2 mirror is required." : "No p2 mirror is attached to this release.", "")); return;
        }
        try {
            UpdateSiteP2MirrorResult result = mirrors.find(request.mirrorId());
            boolean valid = result.validation().valid() && result.manifest().licenseReviewComplete();
            checks.add(new HydraReleaseGateCheck("P2_MIRROR", valid ? HydraReleaseGateStatus.PASS : HydraReleaseGateStatus.FAIL,
                    valid ? "AN mirror is self-contained, checksum-valid and redistribution-approved."
                            : "AN mirror validation or license review is incomplete.", result.manifest().mirrorSha256()));
        } catch(RuntimeException exception) {
            checks.add(new HydraReleaseGateCheck("P2_MIRROR", HydraReleaseGateStatus.FAIL,
                    "Mirror could not be verified: " + safeMessage(exception), request.mirrorId()));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkAdapters» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; checks: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkAdapters(HydraReleaseGateRequest request, List<HydraReleaseGateCheck> checks) {
        if(request.adapterBenchReportIds().isEmpty()) {
            checks.add(new HydraReleaseGateCheck("ADAPTER_BENCH", request.requireAdapterBench() ? HydraReleaseGateStatus.FAIL : HydraReleaseGateStatus.WARNING,
                    request.requireAdapterBench() ? "At least one adapter bench report is required." : "No adapter bench evidence was supplied.", "")); return;
        }
        boolean failed = false; boolean warnings = false; List<String> evidence = new ArrayList<>();
        for(String reportId : request.adapterBenchReportIds()) {
            try {
                UpdateSiteAdapterBenchReport report = adapterBench.find(reportId);
                evidence.add(report.reportId() + ":" + fingerprint(report));
                if(report.failedFixtures() > 0) failed = true;
                if(report.survivingMutations() > 0 || !report.coverage().unreachableRules().isEmpty()) warnings = true;
            } catch(RuntimeException exception) {failed = true; evidence.add(reportId + ":missing");}
        }
        HydraReleaseGateStatus status = failed ? HydraReleaseGateStatus.FAIL : warnings ? HydraReleaseGateStatus.WARNING : HydraReleaseGateStatus.PASS;
        String message = failed ? "One or more AO suites contain failed fixtures or are unavailable."
                : warnings ? "AO suites pass, but surviving mutations or unreachable rules require review."
                : "All AO fixture suites pass without surviving mutations or unreachable rules.";
        checks.add(new HydraReleaseGateCheck("ADAPTER_BENCH", status, message, String.join(",", evidence)));
    }

    /** Descripción: Valida las condiciones necesarias para «checkPerformance» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; checks: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkPerformance(HydraReleaseGateRequest request, List<HydraReleaseGateCheck> checks) {
        if(request.performanceCandidateReportId().isBlank()) {
            checks.add(new HydraReleaseGateCheck("PERFORMANCE", request.requirePerformance() ? HydraReleaseGateStatus.FAIL : HydraReleaseGateStatus.WARNING,
                    request.requirePerformance() ? "A candidate performance report is required." : "No performance candidate was supplied.", "")); return;
        }
        try {
            CapabilityPerformanceReport candidate = performance.find(request.performanceCandidateReportId());
            boolean regression = false;
            String evidence = candidate.reportId() + ":" + fingerprint(candidate);
            List<String> messages = new ArrayList<>();
            if(!candidate.evaluation().passed()) messages.add("Candidate exceeds its AL capability budget.");
            if(!request.performanceBaselineReportId().isBlank()) {
                CapabilityPerformanceReport baseline = performance.find(request.performanceBaselineReportId());
                CapabilityPerformanceComparison comparison = performance.compare(baseline.reportId(), candidate.reportId());
                regression = comparison.regression();
                evidence += ":baseline=" + baseline.reportId() + ":" + fingerprint(baseline)
                        + ":comparison=" + fingerprint(comparison);
                messages.addAll(comparison.diagnostics());
            }
            boolean valid = candidate.evaluation().passed() && !regression;
            checks.add(new HydraReleaseGateCheck("PERFORMANCE", valid ? HydraReleaseGateStatus.PASS : HydraReleaseGateStatus.FAIL,
                    valid ? "AP comparison is within budget and has no material regression." : String.join(" ", messages), evidence));
        } catch(RuntimeException exception) {
            checks.add(new HydraReleaseGateCheck("PERFORMANCE", HydraReleaseGateStatus.FAIL,
                    "Performance evidence could not be verified: " + safeMessage(exception), request.performanceCandidateReportId()));
        }
    }

    /** Descripción: Ejecuta la operación «trimReports» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimReports() {
        if(!Files.isDirectory(storageRoot)) return;
        try(var stream = Files.list(storageRoot)) {
            List<Path> reports = stream.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed()).toList();
            for(int index = maximumReports; index < reports.size(); index++) Files.deleteIfExists(reports.get(index));
        } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/release/HydraReleaseGateService.java",ignored);}
    }

    /** Descripción: Ejecuta la operación «verified» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraReleaseGateReport verified(HydraReleaseGateReport report) {
        if(report == null || !attestation(report).equals(report.attestationSha256())) {
            throw new IllegalStateException("Release gate attestation no longer matches its evidence");
        }
        return report;
    }

    /** Descripción: Ejecuta la operación «attestation» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attestation(HydraReleaseGateReport report) {
        Map<String, Object> evidence = new LinkedHashMap<>(); evidence.put("productId", report.productId());
        evidence.put("version", report.version()); evidence.put("packageFingerprint", report.packageFingerprint());
        evidence.put("mirrorId", report.mirrorId()); evidence.put("adapterBenchReportIds", report.adapterBenchReportIds());
        evidence.put("performanceBaseline", report.performanceBaselineReportId()); evidence.put("performanceCandidate", report.performanceCandidateReportId());
        evidence.put("checks", report.checks()); evidence.put("annotations", report.annotations());
        return sha256(write(evidence));
    }
    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(Object value){return sha256(write(value));}
    /** Descripción: Actualiza el estado gestionado por HydraReleaseGateService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value){try{return writer.writeValueAsBytes(value);}catch(RuntimeException exception){throw new IllegalStateException("Could not canonicalize release evidence", exception);}}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 must be available", exception);}}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de HydraReleaseGateService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraReleaseGateReport read(Path path){try{return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), HydraReleaseGateReport.class);}catch(JacksonException | IOException exception){throw new IllegalArgumentException("Release gate report was not found", exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content){try{com.example.editor.io.AtomicFiles.writeReplacing(target,content);}catch(IOException exception){throw new IllegalStateException("Could not persist release gate report", exception);}}
    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String reportId){String safe = reportId == null ? "" : reportId.replaceAll("[^A-Za-z0-9._-]", "-"); if(safe.isBlank()) throw new IllegalArgumentException("reportId is required"); Path result = storageRoot.resolve(safe + ".json").normalize(); if(!result.startsWith(storageRoot)) throw new IllegalArgumentException("Unsafe reportId"); return result;}
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException exception){return 0L;}}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(HydraReleaseGateRequest request){if(request == null || request.productId().isBlank() || request.version().isBlank() || request.capabilityPackage() == null) throw new IllegalArgumentException("productId, version and capabilityPackage are required");}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");}
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de HydraReleaseGateService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception){return exception == null || exception.getMessage() == null || exception.getMessage().isBlank() ? "unknown verification error" : exception.getMessage();}
}
