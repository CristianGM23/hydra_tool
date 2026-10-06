package com.example.editor.performance;

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
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Aggregates repeated samples, rejects noise and manages approved performance baselines. */
@Service
public class CapabilityPerformanceAutomationService {
    private final CapabilityPerformanceService performance;
    private final CapabilityPerformanceProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de CapabilityPerformanceAutomationService con sus dependencias y datos de entrada. Entradas: performance: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPerformanceAutomationService(CapabilityPerformanceService performance,
            CapabilityPerformanceProperties properties, ObjectMapper mapper) {
        this.performance = performance; this.properties = properties; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public synchronized CapabilityPerformanceAutomationReport run(CapabilityPerformanceScenarioRequest request) {
        require(request);
        List<CapabilityPerformanceSample> measured = request.samples().subList(request.warmupCount(), request.samples().size());
        CapabilityPerformancePercentiles percentiles = percentiles(measured);
        int noise = observedNoise(measured, percentiles);
        boolean noisy = noise > request.allowedNoisePercent();
        CapabilityPerformanceSample aggregate = new CapabilityPerformanceSample("au-" + safe(request.scenarioId()),
                request.capabilityId(), Instant.now(), percentiles.renderP95(), percentiles.heapP95(),
                percentiles.requestsP95(), percentiles.responseP95(), percentiles.analysisP95(),
                percentiles.stageP95(), percentiles.chunkP95());
        CapabilityPerformanceReport standard = performance.evaluate(request.budget(), aggregate);
        CapabilityPerformanceBaseline baseline = baseline(request.scenarioId(), request.capabilityId(), false);
        boolean regression = baseline != null && regression(baseline.percentiles(), percentiles);
        List<String> diagnostics = new ArrayList<>();
        if(noisy) diagnostics.add("Observed sample noise " + noise + "% exceeds the allowed " + request.allowedNoisePercent() + "%");
        if(!standard.evaluation().passed()) diagnostics.addAll(standard.evaluation().violations());
        if(regression) diagnostics.add("The candidate p95 values regress against the approved scenario baseline");
        boolean passed = !noisy && standard.evaluation().passed() && !regression;
        String reportId = "performance-suite-" + sha256((request.scenarioId() + "\n" + request.capabilityId()
                + "\n" + percentiles + "\n" + measured.size()).getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        CapabilityPerformanceAutomationReport report = new CapabilityPerformanceAutomationReport(reportId,
                request.scenarioId(), request.capabilityId(), Instant.now(), request.warmupCount(), measured.size(),
                request.allowedNoisePercent(), noise, noisy, percentiles, standard.reportId(),
                baseline == null ? "" : baseline.baselineId(), regression, passed, diagnostics);
        atomicWrite(reportPath(reportId), write(report));
        if(request.approveBaseline()) approve(reportId);
        trimReports(); return report;
    }

    /** Descripción: Ejecuta la operación «approve» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized CapabilityPerformanceBaseline approve(String reportId) {
        CapabilityPerformanceAutomationReport report = find(reportId);
        if(!report.passed()) throw new IllegalArgumentException("Only a passing, stable performance suite can become a baseline");
        String baselineId = "performance-baseline-" + sha256((report.scenarioId() + "\n" + report.capabilityId()
                + "\n" + report.percentiles()).getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        CapabilityPerformanceBaseline baseline = new CapabilityPerformanceBaseline(baselineId,
                report.scenarioId(), report.capabilityId(), Instant.now(), report.reportId(),
                report.performanceReportId(), report.percentiles());
        atomicWrite(baselinePath(report.scenarioId(), report.capabilityId()), write(baseline)); return baseline;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de CapabilityPerformanceAutomationService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceAutomationReport find(String reportId) {
        try {return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(reportPath(reportId)), CapabilityPerformanceAutomationReport.class);}
        catch(JacksonException | IOException exception){throw new IllegalArgumentException("Performance automation report was not found", exception);}
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CapabilityPerformanceAutomationReport> reports() {
        Path root = reportRoot(); if(!Files.isDirectory(root)) return List.of();
        try(var stream = Files.list(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .flatMap(path -> {try{return java.util.stream.Stream.of(mapper.readValue(read(path), CapabilityPerformanceAutomationReport.class));}
                        catch(RuntimeException exception){return java.util.stream.Stream.empty();}})
                    .limit(properties.getMaximumReports()).toList();
        } catch(IOException exception){throw new IllegalStateException("Could not list performance automation reports", exception);}
    }

    /** Descripción: Ejecuta la operación «baseline» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: scenarioId: identificador que delimita el recurso objetivo; capabilityId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceBaseline baseline(String scenarioId, String capabilityId) {
        CapabilityPerformanceBaseline result = baseline(scenarioId, capabilityId, true);
        if(result == null) throw new IllegalArgumentException("Approved performance baseline was not found"); return result;
    }

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] html(String reportId) {
        CapabilityPerformanceAutomationReport report = find(reportId);
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>HYDRA performance suite</title>")
                .append("<style>body{font-family:system-ui;margin:2rem;max-width:1000px}.ok{color:#176b35}.bad{color:#a12622}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:.5rem}</style></head><body>")
                .append("<h1>Performance automation: ").append(escape(report.scenarioId())).append("</h1><p class=\"")
                .append(report.passed() ? "ok\">Passed" : "bad\">Failed").append("</p><table><tr><th>Metric</th><th>P50</th><th>P95</th></tr>")
                .append(row("Render ms", report.percentiles().renderP50(), report.percentiles().renderP95()))
                .append(row("Heap bytes", report.percentiles().heapP50(), report.percentiles().heapP95()))
                .append(row("Requests", report.percentiles().requestsP50(), report.percentiles().requestsP95()))
                .append(row("Response bytes", report.percentiles().responseP50(), report.percentiles().responseP95()))
                .append(row("Analysis ms", report.percentiles().analysisP50(), report.percentiles().analysisP95()))
                .append("</table><p>Observed noise: ").append(report.observedNoisePercent()).append("%</p></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «percentiles» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: samples: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityPerformancePercentiles percentiles(List<CapabilityPerformanceSample> samples) {
        return new CapabilityPerformancePercentiles(percent(samples.stream().map(CapabilityPerformanceSample::renderMillis).toList(), 50),
                percent(samples.stream().map(CapabilityPerformanceSample::renderMillis).toList(), 95),
                percent(samples.stream().map(CapabilityPerformanceSample::heapBytes).toList(), 50),
                percent(samples.stream().map(CapabilityPerformanceSample::heapBytes).toList(), 95),
                (int)percent(samples.stream().map(item -> (long)item.requestCount()).toList(), 50),
                (int)percent(samples.stream().map(item -> (long)item.requestCount()).toList(), 95),
                percent(samples.stream().map(CapabilityPerformanceSample::responseBytes).toList(), 50),
                percent(samples.stream().map(CapabilityPerformanceSample::responseBytes).toList(), 95),
                percent(samples.stream().map(CapabilityPerformanceSample::analysisMillis).toList(), 50),
                percent(samples.stream().map(CapabilityPerformanceSample::analysisMillis).toList(), 95),
                detailPercentiles(samples, true), detailPercentiles(samples, false));
    }

    /** Descripción: Ejecuta la operación «detailPercentiles» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: samples: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Long> detailPercentiles(List<CapabilityPerformanceSample> samples, boolean stages) {
        Set<String> keys = new TreeSet<>(); samples.forEach(item -> keys.addAll((stages ? item.stageMillis() : item.chunkBytes()).keySet()));
        Map<String, Long> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, percent(samples.stream().map(item -> (stages ? item.stageMillis() : item.chunkBytes()).getOrDefault(key, 0L)).toList(), 95)));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «percent» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: values: colección de elementos que debe procesarse; percentile: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long percent(List<Long> values, int percentile) {
        List<Long> sorted = values.stream().sorted().toList(); int index = (int)Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    /** Descripción: Ejecuta la operación «observedNoise» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: samples: dato de entrada necesario para la operación; p: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int observedNoise(List<CapabilityPerformanceSample> samples, CapabilityPerformancePercentiles p) {
        int render = spread(samples.stream().map(CapabilityPerformanceSample::renderMillis).toList(), p.renderP50());
        int heap = spread(samples.stream().map(CapabilityPerformanceSample::heapBytes).toList(), p.heapP50());
        int response = spread(samples.stream().map(CapabilityPerformanceSample::responseBytes).toList(), p.responseP50());
        int analysis = spread(samples.stream().map(CapabilityPerformanceSample::analysisMillis).toList(), p.analysisP50());
        return Math.max(Math.max(render, heap), Math.max(response, analysis));
    }

    /** Descripción: Ejecuta la operación «spread» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: values: colección de elementos que debe procesarse; median: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int spread(List<Long> values, long median) {
        long min = values.stream().mapToLong(Long::longValue).min().orElse(0); long max = values.stream().mapToLong(Long::longValue).max().orElse(0);
        if(max <= min) return 0; if(median <= 0) return 100; return (int)Math.min(100, Math.round((max - min) * 100.0 / median));
    }

    /** Descripción: Ejecuta la operación «regression» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean regression(CapabilityPerformancePercentiles baseline, CapabilityPerformancePercentiles candidate) {
        return increase(baseline.renderP95(), candidate.renderP95()) > 15
                || increase(baseline.heapP95(), candidate.heapP95()) > 15
                || increase(baseline.requestsP95(), candidate.requestsP95()) > 15
                || increase(baseline.responseP95(), candidate.responseP95()) > 15
                || increase(baseline.analysisP95(), candidate.analysisP95()) > 15
                || mapRegression(baseline.stageP95(), candidate.stageP95())
                || mapRegression(baseline.chunkP95(), candidate.chunkP95());
    }
    /** Descripción: Ejecuta la operación «mapRegression» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean mapRegression(Map<String, Long> baseline, Map<String, Long> candidate) {
        Set<String> keys = new TreeSet<>(); keys.addAll(baseline.keySet()); keys.addAll(candidate.keySet());
        return keys.stream().anyMatch(key -> increase(baseline.getOrDefault(key, 0L), candidate.getOrDefault(key, 0L)) > 15);
    }
    /** Descripción: Ejecuta la operación «increase» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long increase(long baseline, long candidate){if(candidate <= baseline) return 0; if(baseline <= 0) return 100; return Math.round((candidate - baseline) * 100.0 / baseline);}
    /** Descripción: Ejecuta la operación «baseline» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: scenarioId: identificador que delimita el recurso objetivo; capabilityId: identificador que delimita el recurso objetivo; fail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilityPerformanceBaseline baseline(String scenarioId, String capabilityId, boolean fail) {Path path = baselinePath(scenarioId, capabilityId); if(!Files.isRegularFile(path)) return null; try{return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), CapabilityPerformanceBaseline.class);}catch(JacksonException | IOException exception){if(fail) throw new IllegalStateException("Could not read approved performance baseline", exception); return null;}}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(CapabilityPerformanceScenarioRequest request){if(request == null || request.scenarioId().isBlank() || request.capabilityId().isBlank()) throw new IllegalArgumentException("scenarioId and capabilityId are required"); if(request.budget() == null || !request.capabilityId().equals(request.budget().capabilityId())) throw new IllegalArgumentException("Scenario budget must target the same capability"); if(request.samples().size() - request.warmupCount() < 3) throw new IllegalArgumentException("At least three measured samples are required after warm-up"); request.samples().forEach(sample -> {if(!request.capabilityId().equals(sample.capabilityId())) throw new IllegalArgumentException("Every sample must target the scenario capability");});}
    /** Descripción: Ejecuta la operación «root» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path root(){return properties.storageRootPath().resolve("automation");}
    /** Descripción: Ejecuta la operación «reportRoot» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportRoot(){return root().resolve("reports");}
    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String id){return reportRoot().resolve(safe(id) + ".json");}
    /** Descripción: Ejecuta la operación «baselinePath» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: scenario: dato de entrada necesario para la operación; capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path baselinePath(String scenario, String capability){return root().resolve("baselines").resolve(safe(capability) + "--" + safe(scenario) + ".json");}
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safe(String value){String result = value == null ? "" : value.trim(); if(!result.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("Invalid performance automation identifier"); return result;}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] value){return com.example.editor.metamodel.importing.updatesite.UpdateSiteHashing.sha256(value);}
    /** Descripción: Actualiza el estado gestionado por CapabilityPerformanceAutomationService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value){try{return writer.writeValueAsBytes(value);}catch(Exception exception){throw new IllegalStateException("Could not serialize performance automation evidence", exception);}}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de CapabilityPerformanceAutomationService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path){try{return com.example.editor.io.BoundedIo.readInternal(path);}catch(IOException exception){throw new IllegalStateException("Could not read performance automation evidence", exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path path, byte[] bytes){try{com.example.editor.io.AtomicFiles.writeReplacing(path,bytes);}catch(IOException exception){throw new IllegalStateException("Could not persist performance automation evidence", exception);}}
    /** Descripción: Ejecuta la operación «trimReports» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimReports(){List<Path> paths; try{Files.createDirectories(reportRoot()); try(var stream = Files.list(reportRoot())){paths = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted(Comparator.comparingLong(this::modified).reversed()).toList();}}catch(IOException exception){return;} for(int index = properties.getMaximumReports(); index < paths.size(); index++){try{Files.deleteIfExists(paths.get(index));}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/performance/CapabilityPerformanceAutomationService.java",ignored);}}}
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException exception){return 0L;}}
    /** Descripción: Ejecuta la operación «row» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: label: dato de entrada necesario para la operación; p50: dato de entrada necesario para la operación; p95: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String row(String label, long p50, long p95){return "<tr><td>" + escape(label) + "</td><td>" + p50 + "</td><td>" + p95 + "</td></tr>";}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de CapabilityPerformanceAutomationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");}
}
