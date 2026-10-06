package com.example.editor.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Evaluates and persists JSON/HTML performance reports per independently loaded capability. */
@Service
public class CapabilityPerformanceService {
    private final CapabilityPerformanceProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;
    private final Map<String, CapabilityPerformanceBudget> budgets;

    /** Descripción: Inicializa una instancia de CapabilityPerformanceService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPerformanceService(CapabilityPerformanceProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.budgets = defaultBudgets();
    }

    /** Descripción: Ejecuta la operación «budgets» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CapabilityPerformanceBudget> budgets() {
        return budgets.values().stream().sorted(Comparator.comparing(CapabilityPerformanceBudget::capabilityId)).toList();
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceReport evaluate(CapabilityPerformanceSample source) {
        if(source == null || source.capabilityId().isBlank()) throw new IllegalArgumentException("capabilityId is required");
        CapabilityPerformanceBudget budget = budgets.getOrDefault(source.capabilityId(),
                new CapabilityPerformanceBudget(source.capabilityId(), 1200, 268_435_456L, 24, 16_777_216L, 30_000));
        return evaluate(budget, source);
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: budget: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceReport evaluate(CapabilityPerformanceBudget budget, CapabilityPerformanceSample source) {
        if(source == null || source.capabilityId().isBlank()) throw new IllegalArgumentException("capabilityId is required");
        if(budget == null || !source.capabilityId().equals(budget.capabilityId())) {
            throw new IllegalArgumentException("Performance budget must target the same capability");
        }
        String reportId = source.runId().isBlank() ? "perf-" + UUID.randomUUID() : safeId(source.runId());
        CapabilityPerformanceSample sample = new CapabilityPerformanceSample(reportId, source.capabilityId(), source.capturedAt(),
                source.renderMillis(), source.heapBytes(), source.requestCount(), source.responseBytes(),
                source.analysisMillis(), source.stageMillis(), source.chunkBytes());
        List<String> violations = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        if(sample.renderMillis() > budget.maxRenderMillis()) {
            violations.add("Render time exceeds " + budget.maxRenderMillis() + " ms");
            recommendations.add("Split heavy panels behind lazy boundaries and virtualize long lists.");
        }
        if(sample.heapBytes() > budget.maxHeapBytes()) {
            violations.add("Heap usage exceeds " + budget.maxHeapBytes() + " bytes");
            recommendations.add("Release parsed repositories after analysis and retain only normalized summaries.");
        }
        if(sample.requestCount() > budget.maxRequests()) {
            violations.add("Request count exceeds " + budget.maxRequests());
            recommendations.add("Batch related reads and reuse capability-level cache entries.");
        }
        if(sample.responseBytes() > budget.maxResponseBytes()) {
            violations.add("Response volume exceeds " + budget.maxResponseBytes() + " bytes");
            recommendations.add("Paginate evidence and move large binary resources to content-addressed downloads.");
        }
        if(sample.analysisMillis() > budget.maxAnalysisMillis()) {
            violations.add("Analysis time exceeds " + budget.maxAnalysisMillis() + " ms");
            recommendations.add("Inspect stage timings and cache the slowest deterministic stage.");
        }
        sample.stageMillis().entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(entry -> {
            if(entry.getValue() > Math.max(250, budget.maxAnalysisMillis() / 3)) {
                recommendations.add("The slowest stage is " + entry.getKey() + " (" + entry.getValue() + " ms); isolate or cache it first.");
            }
        });
        sample.chunkBytes().entrySet().stream().filter(entry -> entry.getValue() > 850_000)
                .forEach(entry -> recommendations.add("Split chunk " + entry.getKey() + " because it is " + entry.getValue() + " bytes."));
        if(violations.isEmpty()) recommendations.add("All measured values are within the configured capability budget.");
        CapabilityPerformanceReport report = new CapabilityPerformanceReport(reportId, budget, sample,
                new CapabilityPerformanceEvaluation(violations.isEmpty(), violations, recommendations.stream().distinct().toList()));
        persist(report);
        return report;
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<CapabilityPerformanceReport> reports() {
        Path root = properties.storageRootPath();
        if(!Files.isDirectory(root)) return List.of();
        try(var stream = Files.list(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(this::newestFirst).flatMap(path -> {
                        try {
                            return java.util.stream.Stream.<CapabilityPerformanceReport>of(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), CapabilityPerformanceReport.class));
                        } catch(IOException | RuntimeException exception) {
                            return java.util.stream.Stream.<CapabilityPerformanceReport>empty();
                        }
                    }).limit(properties.getMaximumReports()).toList();
        } catch(IOException exception) {
            throw new IllegalStateException("Could not list capability performance reports", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de CapabilityPerformanceService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceReport find(String reportId) {
        try {
            return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(reportPath(reportId)), CapabilityPerformanceReport.class);
        } catch(JacksonException | IOException exception) {
            throw new IllegalArgumentException("Capability performance report was not found", exception);
        }
    }

    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: baselineId: identificador que delimita el recurso objetivo; candidateId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceComparison compare(String baselineId, String candidateId) {
        CapabilityPerformanceReport baseline = find(baselineId);
        CapabilityPerformanceReport candidate = find(candidateId);
        if(!baseline.sample().capabilityId().equals(candidate.sample().capabilityId())) {
            throw new IllegalArgumentException("Only reports for the same capability can be compared");
        }
        long render = candidate.sample().renderMillis() - baseline.sample().renderMillis();
        long heap = candidate.sample().heapBytes() - baseline.sample().heapBytes();
        int requests = candidate.sample().requestCount() - baseline.sample().requestCount();
        long response = candidate.sample().responseBytes() - baseline.sample().responseBytes();
        long analysis = candidate.sample().analysisMillis() - baseline.sample().analysisMillis();
        Map<String, Long> stages = deltas(baseline.sample().stageMillis(), candidate.sample().stageMillis());
        Map<String, Long> chunks = deltas(baseline.sample().chunkBytes(), candidate.sample().chunkBytes());
        List<String> diagnostics = new ArrayList<>();
        CapabilityPerformanceBudget budget = candidate.budget();
        boolean stageRegression = stages.entrySet().stream().anyMatch(entry -> entry.getValue() > 250
                && percentageIncrease(baseline.sample().stageMillis().getOrDefault(entry.getKey(), 0L),
                        candidate.sample().stageMillis().getOrDefault(entry.getKey(), 0L)) > 20);
        boolean chunkRegression = chunks.entrySet().stream().anyMatch(entry -> entry.getValue() > 131_072
                && percentageIncrease(baseline.sample().chunkBytes().getOrDefault(entry.getKey(), 0L),
                        candidate.sample().chunkBytes().getOrDefault(entry.getKey(), 0L)) > 15);
        boolean regression = percentageIncrease(baseline.sample().renderMillis(), candidate.sample().renderMillis()) > 15
                || percentageIncrease(baseline.sample().heapBytes(), candidate.sample().heapBytes()) > 15
                || percentageIncrease(baseline.sample().requestCount(), candidate.sample().requestCount()) > 15
                || percentageIncrease(baseline.sample().responseBytes(), candidate.sample().responseBytes()) > 15
                || percentageIncrease(baseline.sample().analysisMillis(), candidate.sample().analysisMillis()) > 15
                || stageRegression || chunkRegression
                || baseline.evaluation().passed() && !candidate.evaluation().passed();
        if(render > Math.max(100, budget.maxRenderMillis() / 10)) diagnostics.add("Render time regressed by " + render + " ms.");
        if(heap > Math.max(8_388_608L, budget.maxHeapBytes() / 10)) diagnostics.add("Heap usage regressed by " + heap + " bytes.");
        if(response > Math.max(262_144L, budget.maxResponseBytes() / 10)) diagnostics.add("Response volume regressed by " + response + " bytes.");
        if(analysis > Math.max(250, budget.maxAnalysisMillis() / 10)) diagnostics.add("Analysis time regressed by " + analysis + " ms.");
        stages.entrySet().stream().filter(entry -> entry.getValue() > 250).sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).forEach(entry -> diagnostics.add("Stage " + entry.getKey() + " regressed by " + entry.getValue() + " ms."));
        chunks.entrySet().stream().filter(entry -> entry.getValue() > 131_072).sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).forEach(entry -> diagnostics.add("Chunk " + entry.getKey() + " grew by " + entry.getValue() + " bytes."));
        if(!regression && diagnostics.isEmpty()) diagnostics.add("No material regression was detected against the baseline execution.");
        return new CapabilityPerformanceComparison(baselineId, candidateId, candidate.sample().capabilityId(),
                render, heap, requests, response, analysis, stages, chunks, regression, diagnostics);
    }

    /** Descripción: Ejecuta la operación «deltas» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Long> deltas(Map<String, Long> baseline, Map<String, Long> candidate) {
        Map<String, Long> result = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.TreeSet<>(); keys.addAll(baseline.keySet()); keys.addAll(candidate.keySet());
        keys.forEach(key -> result.put(key, candidate.getOrDefault(key, 0L) - baseline.getOrDefault(key, 0L)));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «percentageIncrease» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long percentageIncrease(long baseline, long candidate) {
        if(candidate <= baseline) return 0L;
        if(baseline <= 0) return 100L;
        return Math.round((candidate - baseline) * 100.0 / baseline);
    }

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] html(String reportId) {
        CapabilityPerformanceReport report = find(reportId);
        String stateClass = report.evaluation().passed() ? "ok" : "bad";
        String stateLabel = report.evaluation().passed() ? "Passed" : "Budget exceeded";
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>HYDRA performance report</title>")
                .append("<style>body{font-family:system-ui;margin:2rem;max-width:960px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #bbb;padding:.5rem;text-align:left}.ok{color:#176b35}.bad{color:#a12622}code{overflow-wrap:anywhere}</style></head><body>")
                .append("<h1>HYDRA capability performance</h1><p>Report <code>").append(escape(report.reportId())).append("</code></p>")
                .append("<h2>").append(escape(report.sample().capabilityId())).append("</h2><p class=\"")
                .append(stateClass).append("\">").append(stateLabel).append("</p>")
                .append("<table><tr><th>Metric</th><th>Measured</th><th>Budget</th></tr>")
                .append(row("Render ms", report.sample().renderMillis(), report.budget().maxRenderMillis()))
                .append(row("Heap bytes", report.sample().heapBytes(), report.budget().maxHeapBytes()))
                .append(row("Requests", report.sample().requestCount(), report.budget().maxRequests()))
                .append(row("Response bytes", report.sample().responseBytes(), report.budget().maxResponseBytes()))
                .append(row("Analysis ms", report.sample().analysisMillis(), report.budget().maxAnalysisMillis()))
                .append("</table><h3>Violations</h3><ul>");
        report.evaluation().violations().forEach(value -> html.append("<li>").append(escape(value)).append("</li>"));
        html.append("</ul><h3>Recommendations</h3><ul>");
        report.evaluation().recommendations().forEach(value -> html.append("<li>").append(escape(value)).append("</li>"));
        html.append("</ul><h3>Stage timings</h3><table><tr><th>Stage</th><th>Milliseconds</th></tr>");
        report.sample().stageMillis().forEach((name, value) -> html.append("<tr><td>").append(escape(name))
                .append("</td><td>").append(value).append("</td></tr>"));
        html.append("</table><h3>Chunk sizes</h3><table><tr><th>Chunk</th><th>Bytes</th></tr>");
        report.sample().chunkBytes().forEach((name, value) -> html.append("<tr><td>").append(escape(name))
                .append("</td><td>").append(value).append("</td></tr>"));
        return html.append("</table></body></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «persist» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: report: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persist(CapabilityPerformanceReport report) {
        Path target = reportPath(report.reportId());
        try {
            Files.createDirectories(target.getParent());
            byte[] content = writer.writeValueAsBytes(report);
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try {
                Files.write(temporary, content);
                AtomicFiles.moveReplacing(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            trim();
        } catch(IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not persist capability performance report", exception);
        }
    }

    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trim() {
        Path root = properties.storageRootPath();
        if(!Files.isDirectory(root)) return;
        try(var stream = Files.list(root)) {
            List<Path> files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(this::newestFirst).toList();
            for(int index = properties.getMaximumReports(); index < files.size(); index++) Files.deleteIfExists(files.get(index));
        } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/performance/CapabilityPerformanceService.java",ignored);}
    }

    /** Descripción: Crea o prepara el resultado de «newestFirst» dentro de CapabilityPerformanceService. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int newestFirst(Path left, Path right) {
        try {
            int modified = Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left));
            return modified != 0 ? modified : right.getFileName().toString().compareTo(left.getFileName().toString());
        } catch(IOException exception) {
            return right.getFileName().toString().compareTo(left.getFileName().toString());
        }
    }

    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String id) {
        String safe = safeId(id);
        Path root = properties.storageRootPath();
        Path result = root.resolve(safe + ".json").normalize();
        if(!result.startsWith(root)) throw new IllegalArgumentException("Unsafe reportId");
        return result;
    }

    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeId(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        if(safe.isBlank()) throw new IllegalArgumentException("reportId is required");
        return safe;
    }

    /** Descripción: Ejecuta la operación «row» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: name: dato de entrada necesario para la operación; measured: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String row(String name, long measured, long budget) {
        return "<tr><td>" + escape(name) + "</td><td>" + measured + "</td><td>" + budget + "</td></tr>";
    }

    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Descripción: Ejecuta la operación «defaultBudgets» correspondiente a la responsabilidad de CapabilityPerformanceService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, CapabilityPerformanceBudget> defaultBudgets() {
        Map<String, CapabilityPerformanceBudget> result = new LinkedHashMap<>();
        result.put("update-site-import-center", new CapabilityPerformanceBudget("update-site-import-center", 1200, 268_435_456L, 24, 16_777_216L, 30_000));
        result.put("metamodel-designer", new CapabilityPerformanceBudget("metamodel-designer", 900, 201_326_592L, 16, 8_388_608L, 10_000));
        result.put("editor-definition-designer", new CapabilityPerformanceBudget("editor-definition-designer", 1200, 268_435_456L, 20, 12_582_912L, 15_000));
        result.put("event-designer", new CapabilityPerformanceBudget("event-designer", 900, 201_326_592L, 16, 8_388_608L, 10_000));
        result.put("gamification-designer", new CapabilityPerformanceBudget("gamification-designer", 900, 201_326_592L, 16, 8_388_608L, 10_000));
        result.put("capability-center", new CapabilityPerformanceBudget("capability-center", 700, 134_217_728L, 12, 4_194_304L, 5_000));
        return Map.copyOf(result);
    }
}
