package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Executes positive, negative and mutation fixtures without running extension code. */
@Service
public class UpdateSiteAdapterBenchService {
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteDeclarativeAdapterService adapters;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de UpdateSiteAdapterBenchService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; adapters: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAdapterBenchService(UpdateSiteImportProperties properties,
            UpdateSiteDeclarativeAdapterService adapters, ObjectMapper mapper) {
        this.properties = properties; this.adapters = adapters; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de Mutation. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterBenchReport run(UpdateSiteAdapterBenchRequest request) {
        if(request == null || request.fixtures().isEmpty()) throw new IllegalArgumentException("At least one positive or negative fixture is required");
        if(request.fixtures().size() > properties.getMaxAdapterBenchFixtures()) {
            throw new IllegalArgumentException("Adapter bench fixture count exceeds the configured limit");
        }
        byte[] documentBytes = document(request);
        UpdateSiteDeclarativeAdapterValidation validation = adapters.preview(documentBytes);
        if(!validation.valid()) throw new IllegalArgumentException("Adapter document is invalid: " + String.join("; ", validation.errors()));
        UpdateSiteDeclarativeAdapterDocument document = validation.document();
        UpdateSiteExtensionAdapterDescriptor descriptor = document.adapter();
        List<UpdateSiteAdapterBenchCaseResult> fixtureResults = new ArrayList<>();
        for(UpdateSiteAdapterBenchCase fixture : request.fixtures()) {
            UpdateSiteAdapterFixtureResult result = adapters.testFixture(documentBytes, fixture.fixture());
            fixtureResults.add(new UpdateSiteAdapterBenchCaseResult(fixture.name(), fixture.expectedMatch(),
                    result.matched(), result.validAdapter() && result.matched() == fixture.expectedMatch(),
                    result.resolvedId(), result.implementation(), result.explanations(), result.errors()));
        }
        List<UpdateSiteAdapterMutationResult> mutations = request.includeMutations()
                ? mutations(documentBytes, request.fixtures()) : List.of();
        UpdateSiteAdapterCoverage coverage = coverage(descriptor, request.fixtures());
        int passed = (int)fixtureResults.stream().filter(UpdateSiteAdapterBenchCaseResult::passed).count();
        int killed = (int)mutations.stream().filter(UpdateSiteAdapterMutationResult::killed).count();
        List<String> diagnostics = new ArrayList<>();
        fixtureResults.stream().filter(item -> !item.passed()).forEach(item -> diagnostics.add(
                "Fixture " + item.name() + " expected match=" + item.expectedMatch() + " but was " + item.actualMatch()));
        mutations.stream().filter(item -> item.baselineMatched() && !item.killed()).forEach(item -> diagnostics.add(
                "Mutation survived for " + item.fixtureName() + ": " + item.mutation()));
        coverage.redundantRules().forEach(item -> diagnostics.add("Redundant adapter rule: " + item));
        coverage.unreachableRules().forEach(item -> diagnostics.add("Unreachable adapter rule: " + item));
        String version = document.annotations().getOrDefault("version", "1.0.0");
        String reportId = "adapter-bench-" + UUID.randomUUID();
        UpdateSiteAdapterBenchReport report = new UpdateSiteAdapterBenchReport(reportId, descriptor.id(),
                version, validation.fingerprint(), Instant.now(), passed, fixtureResults.size() - passed,
                killed, mutations.size() - killed, coverage, fixtureResults, mutations, diagnostics);
        persist(report); return report;
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de Mutation. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteAdapterBenchReport> reports() {
        Path root = reportRoot(); if(!Files.isDirectory(root)) return List.of();
        try(var stream = Files.list(root)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .flatMap(path -> {try{return java.util.stream.Stream.<UpdateSiteAdapterBenchReport>of(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), UpdateSiteAdapterBenchReport.class));}
                        catch(IOException | RuntimeException exception){return java.util.stream.Stream.<UpdateSiteAdapterBenchReport>empty();}}).toList();
        } catch(IOException exception) {throw new IllegalStateException("Could not list adapter bench reports", exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de Mutation. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterBenchReport find(String reportId) {
        try {return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(reportPath(reportId)), UpdateSiteAdapterBenchReport.class);}
        catch(JacksonException | IOException exception) {throw new IllegalArgumentException("Adapter bench report was not found", exception);}
    }

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de Mutation. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] html(String reportId) {
        UpdateSiteAdapterBenchReport report = find(reportId);
        StringBuilder value = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>HYDRA adapter bench</title>")
                .append("<style>body{font-family:system-ui;margin:2rem;max-width:1100px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:.45rem;text-align:left}.ok{color:#176b2c}.bad{color:#9b1c1c}code{background:#eee;padding:.1rem .3rem}</style></head><body>")
                .append("<h1>Adapter bench: ").append(escape(report.adapterId())).append("</h1><p>Version <code>")
                .append(escape(report.adapterVersion())).append("</code> · report <code>").append(escape(report.reportId())).append("</code></p>")
                .append("<h2>Summary</h2><ul><li>").append(report.passedFixtures()).append(" fixtures passed</li><li>")
                .append(report.failedFixtures()).append(" fixtures failed</li><li>").append(report.killedMutations()).append(" mutations killed</li><li>")
                .append(report.survivingMutations()).append(" mutations survived</li></ul><h2>Fixtures</h2><table><tr><th>Name</th><th>Expected</th><th>Actual</th><th>Result</th></tr>");
        report.fixtures().forEach(item -> value.append("<tr><td>").append(escape(item.name())).append("</td><td>")
                .append(item.expectedMatch()).append("</td><td>").append(item.actualMatch()).append("</td><td class=\"")
                .append(item.passed() ? "ok\">passed" : "bad\">failed").append("</td></tr>"));
        value.append("</table><h2>Coverage</h2><pre>").append(escape(coverageText(report.coverage()))).append("</pre><h2>Diagnostics</h2><ul>");
        report.diagnostics().forEach(item -> value.append("<li>").append(escape(item)).append("</li>"));
        value.append("</ul></body></html>"); return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de Mutation. Entradas: baselineId: identificador que delimita el recurso objetivo; candidateId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterBenchComparison compare(String baselineId, String candidateId) {
        UpdateSiteAdapterBenchReport baseline = find(baselineId); UpdateSiteAdapterBenchReport candidate = find(candidateId);
        if(!baseline.adapterId().equals(candidate.adapterId())) throw new IllegalArgumentException("Only reports for the same adapter can be compared");
        int fixtureDelta = candidate.passedFixtures() - baseline.passedFixtures();
        int mutationDelta = candidate.killedMutations() - baseline.killedMutations();
        int tokenDelta = candidate.coverage().coveredTokens() - baseline.coverage().coveredTokens();
        int elementDelta = candidate.coverage().coveredElements() - baseline.coverage().coveredElements();
        boolean regression = fixtureDelta < 0 || mutationDelta < 0 || tokenDelta < 0 || elementDelta < 0;
        List<String> diagnostics = new ArrayList<>();
        if(fixtureDelta < 0) diagnostics.add("The candidate passes fewer fixtures.");
        if(mutationDelta < 0) diagnostics.add("The candidate kills fewer controlled mutations.");
        if(tokenDelta < 0 || elementDelta < 0) diagnostics.add("The candidate reduces declarative rule coverage.");
        if(!regression) diagnostics.add("No fixture, mutation or rule-coverage regression was detected.");
        return new UpdateSiteAdapterBenchComparison(baselineId, candidateId, fixtureDelta,
                mutationDelta, tokenDelta, elementDelta, regression, diagnostics);
    }

    /** Descripción: Ejecuta la operación «mutations» correspondiente a la responsabilidad de Mutation. Entradas: document: dato de entrada necesario para la operación; fixtures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteAdapterMutationResult> mutations(byte[] document,
            List<UpdateSiteAdapterBenchCase> fixtures) {
        List<UpdateSiteAdapterMutationResult> result = new ArrayList<>();
        for(UpdateSiteAdapterBenchCase test : fixtures) {
            UpdateSiteAdapterFixture fixture = test.fixture();
            UpdateSiteAdapterFixtureResult baseline = adapters.testFixture(document, fixture);
            if(!baseline.matched()) continue;
            List<Mutation> candidates = List.of(
                    new Mutation("replace-extension-point", new UpdateSiteAdapterFixture("invalid.hydra.extension", fixture.elementName(), fixture.attributes())),
                    new Mutation("replace-element-name", new UpdateSiteAdapterFixture(fixture.extensionPoint(), "invalid-hydra-element", fixture.attributes())),
                    new Mutation("remove-all-attributes", new UpdateSiteAdapterFixture(fixture.extensionPoint(), fixture.elementName(), Map.of())));
            for(Mutation mutation : candidates) {
                UpdateSiteAdapterFixtureResult mutated = adapters.testFixture(document, mutation.fixture());
                boolean killed = baseline.matched() && (!mutated.matched()
                        || ("remove-all-attributes".equals(mutation.name()) && mutated.resolvedId().isBlank() && mutated.implementation().isBlank()));
                String explanation = killed ? "The controlled change invalidated matching or required extraction."
                        : baseline.matched() ? "The adapter still accepts the controlled change; review whether the rule is too broad."
                        : "The baseline did not match, so this mutation is informational.";
                result.add(new UpdateSiteAdapterMutationResult(test.name(), mutation.name(),
                        baseline.matched(), mutated.matched(), killed, explanation));
            }
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «coverage» correspondiente a la responsabilidad de Mutation. Entradas: descriptor: dato de entrada necesario para la operación; fixtures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteAdapterCoverage coverage(UpdateSiteExtensionAdapterDescriptor descriptor,
            List<UpdateSiteAdapterBenchCase> fixtures) {
        Set<String> coveredTokens = new LinkedHashSet<>(); Set<String> coveredElements = new LinkedHashSet<>();
        Set<String> coveredIds = new LinkedHashSet<>(); Set<String> coveredImplementations = new LinkedHashSet<>();
        for(UpdateSiteAdapterBenchCase test : fixtures) {
            if(!test.expectedMatch()) continue;
            UpdateSiteAdapterFixture fixture = test.fixture(); String extension = fixture.extensionPoint().toLowerCase(Locale.ROOT);
            descriptor.extensionPointTokens().stream().filter(extension::contains).forEach(coveredTokens::add);
            String element = fixture.elementName().toLowerCase(Locale.ROOT);
            descriptor.elementNames().stream().filter(element::equals).forEach(coveredElements::add);
            Set<String> attributes = fixture.attributes().keySet().stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            descriptor.idAttributes().stream().filter(attributes::contains).forEach(coveredIds::add);
            descriptor.implementationAttributes().stream().filter(attributes::contains).forEach(coveredImplementations::add);
        }
        List<String> redundant = new ArrayList<>();
        for(String left : descriptor.extensionPointTokens()) for(String right : descriptor.extensionPointTokens()) {
            if(!left.equals(right) && right.contains(left) && !redundant.contains(right)) redundant.add(right + " is subsumed by " + left);
        }
        List<String> unreachable = new ArrayList<>();
        if(!descriptor.enabled()) unreachable.add("The adapter is disabled; every matching rule is unreachable at runtime.");
        difference(descriptor.extensionPointTokens(), coveredTokens).forEach(item -> unreachable.add("Uncovered extension token: " + item));
        difference(descriptor.elementNames(), coveredElements).forEach(item -> unreachable.add("Uncovered XML element: " + item));
        return new UpdateSiteAdapterCoverage(coveredTokens.size(), descriptor.extensionPointTokens().size(),
                coveredElements.size(), descriptor.elementNames().size(), coveredIds.size(), descriptor.idAttributes().size(),
                coveredImplementations.size(), descriptor.implementationAttributes().size(),
                difference(descriptor.extensionPointTokens(), coveredTokens), difference(descriptor.elementNames(), coveredElements),
                difference(descriptor.idAttributes(), coveredIds), difference(descriptor.implementationAttributes(), coveredImplementations),
                redundant, unreachable);
    }

    /** Descripción: Ejecuta la operación «difference» correspondiente a la responsabilidad de Mutation. Entradas: all: dato de entrada necesario para la operación; covered: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> difference(List<String> all, Set<String> covered) {return all.stream().filter(item -> !covered.contains(item)).toList();}
    /** Descripción: Ejecuta la operación «document» correspondiente a la responsabilidad de Mutation. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] document(UpdateSiteAdapterBenchRequest request) {
        if(!request.document().isBlank()) return request.document().getBytes(StandardCharsets.UTF_8);
        if(request.adapterId().isBlank()) throw new IllegalArgumentException("adapterId or inline document is required");
        return request.version().isBlank() ? adapters.exportAdapter(request.adapterId())
                : adapters.exportVersion(request.adapterId(), request.version());
    }
    /** Descripción: Ejecuta la operación «persist» correspondiente a la responsabilidad de Mutation. Entradas: report: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persist(UpdateSiteAdapterBenchReport report) {
        atomicWrite(reportPath(report.reportId()), write(report));
        trimReports();
    }
    /** Descripción: Ejecuta la operación «trimReports» correspondiente a la responsabilidad de Mutation. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimReports() {
        Path root = reportRoot(); if(!Files.isDirectory(root)) return;
        try(var stream = Files.list(root)) {
            List<Path> reports = stream.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed()).toList();
            for(int index = properties.getMaxAdapterBenchReports(); index < reports.size(); index++) {
                Files.deleteIfExists(reports.get(index));
            }
        } catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteAdapterBenchService.java",ignored);}
    }
    /** Descripción: Actualiza el estado gestionado por Mutation mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value) {try{return writer.writeValueAsBytes(value);}catch(RuntimeException exception){throw new IllegalStateException("Could not serialize adapter bench report", exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de Mutation. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content){try{AtomicFiles.writeReplacing(target,content);}catch(IOException exception){throw new IllegalStateException("Could not persist adapter bench report", exception);}}
    /** Descripción: Ejecuta la operación «reportRoot» correspondiente a la responsabilidad de Mutation. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportRoot(){return properties.storageRootPath().resolve("adapter-bench-reports").normalize();}
    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de Mutation. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String reportId){String safe = reportId == null ? "" : reportId.replaceAll("[^A-Za-z0-9._-]", "-"); if(safe.isBlank()) throw new IllegalArgumentException("reportId is required"); Path result = reportRoot().resolve(safe + ".json").normalize(); if(!result.startsWith(reportRoot())) throw new IllegalArgumentException("Unsafe reportId"); return result;}
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de Mutation. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException exception){return 0L;}}
    /** Descripción: Ejecuta la operación «coverageText» correspondiente a la responsabilidad de Mutation. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String coverageText(UpdateSiteAdapterCoverage value){return "tokens " + value.coveredTokens() + "/" + value.totalTokens() + "\nelements " + value.coveredElements() + "/" + value.totalElements() + "\nid attributes " + value.coveredIdAttributes() + "/" + value.totalIdAttributes() + "\nimplementation attributes " + value.coveredImplementationAttributes() + "/" + value.totalImplementationAttributes();}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de Mutation. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");}
    private record Mutation(String name, UpdateSiteAdapterFixture fixture) { }
}
