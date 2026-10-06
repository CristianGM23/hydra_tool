package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Generates fixtures, performs bounded structural fuzzing and checks adapter invariants. */
@Service
public class UpdateSiteAdapterVerificationService {
    private static final List<String> DEFAULT_INVARIANTS = List.of("EXPECTED_MATCH", "REQUIRED_ID", "REQUIRED_IMPLEMENTATION");
    private final UpdateSiteImportProperties properties;
    private final UpdateSiteDeclarativeAdapterService adapters;
    private final UpdateSiteAdapterBenchService bench;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de UpdateSiteAdapterVerificationService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; adapters: dato de entrada necesario para la operación; bench: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAdapterVerificationService(UpdateSiteImportProperties properties,
            UpdateSiteDeclarativeAdapterService adapters, UpdateSiteAdapterBenchService bench,
            ObjectMapper mapper) {
        this.properties = properties; this.adapters = adapters; this.bench = bench; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterVerificationReport verify(UpdateSiteAdapterVerificationRequest request) {
        if(request == null) throw new IllegalArgumentException("Adapter verification request is required");
        byte[] document = document(request);
        UpdateSiteDeclarativeAdapterValidation validation = adapters.preview(document);
        if(!validation.valid()) throw new IllegalArgumentException("Adapter document is invalid: " + String.join("; ", validation.errors()));
        UpdateSiteDeclarativeAdapterDocument adapterDocument = validation.document();
        UpdateSiteExtensionAdapterDescriptor descriptor = adapterDocument.adapter();
        List<UpdateSiteAdapterBenchCase> generated = generatedFixtures(descriptor);
        UpdateSiteAdapterBenchReport benchReport = bench.run(new UpdateSiteAdapterBenchRequest("", "",
                new String(document, StandardCharsets.UTF_8), generated, true));
        List<String> invariants = request.invariants().isEmpty() ? DEFAULT_INVARIANTS : request.invariants();
        List<UpdateSiteAdapterVerificationFailure> failures = new ArrayList<>();
        int checks = evaluateGenerated(document, descriptor, generated, invariants, failures);
        int fuzzCases = request.fuzzCases() == 0 ? 32 : request.fuzzCases();
        checks += fuzz(document, descriptor, validation.fingerprint(), fuzzCases, invariants, failures);
        List<String> diagnostics = new ArrayList<>();
        if(benchReport.failedFixtures() > 0) diagnostics.add("Generated adapter fixtures contain failures");
        if(benchReport.survivingMutations() > 0) diagnostics.add("Controlled AO mutations survived the generated suite");
        failures.forEach(item -> diagnostics.add(item.caseName() + ": " + item.explanation()));
        boolean passed = failures.isEmpty() && benchReport.failedFixtures() == 0
                && benchReport.survivingMutations() == 0;
        String version = adapterDocument.annotations().getOrDefault("version", "1.0.0");
        String reportId = "adapter-verification-" + UpdateSiteHashing.sha256((validation.fingerprint() + "\n"
                + fuzzCases + "\n" + invariants + "\n" + failures).getBytes(StandardCharsets.UTF_8)).substring(0, 24);
        UpdateSiteAdapterVerificationReport report = new UpdateSiteAdapterVerificationReport(reportId,
                descriptor.id(), version, validation.fingerprint(), Instant.now(), benchReport.reportId(),
                generated.size(), fuzzCases, checks, failures.size(), failures, passed, diagnostics);
        atomicWrite(reportPath(reportId), write(report)); trim(); return report;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de UpdateSiteAdapterVerificationService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterVerificationReport find(String reportId) {
        try {return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(reportPath(reportId)), UpdateSiteAdapterVerificationReport.class);}
        catch(JacksonException | IOException exception){throw new IllegalArgumentException("Adapter verification report was not found", exception);}
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteAdapterVerificationReport> reports() {
        Path root = reportRoot(); if(!Files.isDirectory(root)) return List.of();
        try(var stream = Files.list(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .flatMap(path -> {try{return java.util.stream.Stream.of(mapper.readValue(read(path), UpdateSiteAdapterVerificationReport.class));}
                        catch(RuntimeException exception){return java.util.stream.Stream.empty();}})
                    .limit(properties.getMaxAdapterBenchReports()).toList();
        } catch(IOException exception){throw new IllegalStateException("Could not list adapter verification reports", exception);}
    }

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] html(String reportId) {
        UpdateSiteAdapterVerificationReport report = find(reportId);
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>HYDRA adapter verification</title>")
                .append("<style>body{font-family:system-ui;margin:2rem;max-width:1100px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:.45rem}.ok{color:#176b35}.bad{color:#a12622}code{overflow-wrap:anywhere}</style></head><body>")
                .append("<h1>Advanced adapter verification</h1><p><strong>").append(escape(report.adapterId())).append(" ")
                .append(escape(report.adapterVersion())).append("</strong></p><p class=\"").append(report.passed() ? "ok\">Passed" : "bad\">Failed")
                .append("</p><ul><li>").append(report.generatedFixtures()).append(" generated fixtures</li><li>")
                .append(report.fuzzCases()).append(" fuzz cases</li><li>").append(report.invariantChecks()).append(" invariant checks</li></ul>")
                .append("<table><tr><th>Case</th><th>Invariant</th><th>Explanation</th></tr>");
        report.failureDetails().forEach(item -> html.append("<tr><td>").append(escape(item.caseName())).append("</td><td>")
                .append(escape(item.invariant())).append("</td><td>").append(escape(item.explanation())).append("</td></tr>"));
        html.append("</table></body></html>"); return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Crea o prepara el resultado de «generatedFixtures» dentro de UpdateSiteAdapterVerificationService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteAdapterBenchCase> generatedFixtures(UpdateSiteExtensionAdapterDescriptor descriptor) {
        List<String> points = descriptor.extensionPointTokens().isEmpty() ? List.of("hydra.generated") : descriptor.extensionPointTokens();
        List<String> elements = descriptor.elementNames().isEmpty() ? List.of("extension") : descriptor.elementNames();
        int limit = properties.getMaxAdapterBenchFixtures();
        int positiveLimit = Math.min(64, Math.max(1, limit - 2));
        List<UpdateSiteAdapterBenchCase> result = new ArrayList<>(); int index = 0;
        outer: for(String point : points) for(String element : elements) {
            if(result.size() >= positiveLimit) break outer;
            Map<String, String> attributes = generatedAttributes(descriptor, index++);
            result.add(new UpdateSiteAdapterBenchCase("generated-positive-" + result.size(),
                    new UpdateSiteAdapterFixture("org.example." + point, element, attributes), true));
        }
        if(result.size() < limit) result.add(new UpdateSiteAdapterBenchCase("generated-negative-point",
                new UpdateSiteAdapterFixture("invalid.hydra.extension", elements.get(0), generatedAttributes(descriptor, index++)), false));
        if(result.size() < limit) result.add(new UpdateSiteAdapterBenchCase("generated-negative-element",
                new UpdateSiteAdapterFixture("org.example." + points.get(0), "invalid-hydra-element", generatedAttributes(descriptor, index)), false));
        return List.copyOf(result);
    }

    /** Descripción: Crea o prepara el resultado de «generatedAttributes» dentro de UpdateSiteAdapterVerificationService. Entradas: descriptor: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> generatedAttributes(UpdateSiteExtensionAdapterDescriptor descriptor, int index) {
        Map<String, String> result = new LinkedHashMap<>();
        descriptor.idAttributes().forEach(attribute -> result.put(attribute, "generated.id." + index));
        descriptor.implementationAttributes().forEach(attribute -> result.put(attribute, "generated.Implementation" + index));
        if(result.isEmpty()) result.put("id", "generated.id." + index); return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «evaluateGenerated» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: document: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; fixtures: dato de entrada necesario para la operación; invariants: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int evaluateGenerated(byte[] document, UpdateSiteExtensionAdapterDescriptor descriptor,
            List<UpdateSiteAdapterBenchCase> fixtures, List<String> invariants,
            List<UpdateSiteAdapterVerificationFailure> failures) {
        int checks = 0;
        for(UpdateSiteAdapterBenchCase test : fixtures) {
            UpdateSiteAdapterFixtureResult result = adapters.testFixture(document, test.fixture());
            checks += evaluate(test.name(), test.fixture(), result, test.expectedMatch(), descriptor, invariants, failures);
        }
        return checks;
    }

    /** Descripción: Ejecuta la operación «fuzz» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: document: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; fingerprint: dato de entrada necesario para la operación; count: dato de entrada necesario para la operación; invariants: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int fuzz(byte[] document, UpdateSiteExtensionAdapterDescriptor descriptor, String fingerprint,
            int count, List<String> invariants, List<UpdateSiteAdapterVerificationFailure> failures) {
        Random random = new Random(Long.parseUnsignedLong(fingerprint.substring(0, 16), 16)); int checks = 0;
        List<String> points = descriptor.extensionPointTokens().isEmpty() ? List.of("hydra") : descriptor.extensionPointTokens();
        List<String> elements = descriptor.elementNames().isEmpty() ? List.of("extension") : descriptor.elementNames();
        for(int index = 0; index < count; index++) {
            boolean expected = random.nextBoolean(); String point = expected ? "org.fuzz." + points.get(random.nextInt(points.size())) : "invalid." + index;
            String element = expected ? elements.get(random.nextInt(elements.size())) : "invalid-" + index;
            Map<String, String> attributes = new LinkedHashMap<>(generatedAttributes(descriptor, index + 1000));
            if(random.nextBoolean() && !attributes.isEmpty()) attributes.remove(attributes.keySet().iterator().next());
            UpdateSiteAdapterFixture fixture = new UpdateSiteAdapterFixture(point, element, attributes);
            UpdateSiteAdapterFixtureResult result = adapters.testFixture(document, fixture);
            checks += evaluate("fuzz-" + index, fixture, result, expected, descriptor, invariants, failures);
        }
        return checks;
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: name: dato de entrada necesario para la operación; fixture: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación; expected: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; invariants: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int evaluate(String name, UpdateSiteAdapterFixture fixture, UpdateSiteAdapterFixtureResult result,
            boolean expected, UpdateSiteExtensionAdapterDescriptor descriptor, List<String> invariants,
            List<UpdateSiteAdapterVerificationFailure> failures) {
        int checks = 0;
        if(invariants.contains("EXPECTED_MATCH")) {checks++; if(result.matched() != expected) addFailure(name, "EXPECTED_MATCH", fixture, "Expected match=" + expected + " but was " + result.matched(), failures);}
        if(expected && result.matched() && invariants.contains("REQUIRED_ID") && !descriptor.idAttributes().isEmpty()) {checks++; if(result.resolvedId().isBlank()) addFailure(name, "REQUIRED_ID", fixture, "Matched fixture did not resolve an identifier", failures);}
        if(expected && result.matched() && invariants.contains("REQUIRED_IMPLEMENTATION") && !descriptor.implementationAttributes().isEmpty()) {checks++; if(result.implementation().isBlank()) addFailure(name, "REQUIRED_IMPLEMENTATION", fixture, "Matched fixture did not resolve an implementation", failures);}
        return checks;
    }

    /** Descripción: Ejecuta la operación «addFailure» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: name: dato de entrada necesario para la operación; invariant: dato de entrada necesario para la operación; fixture: dato de entrada necesario para la operación; explanation: dato de entrada necesario para la operación; failures: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addFailure(String name, String invariant, UpdateSiteAdapterFixture fixture,
            String explanation, List<UpdateSiteAdapterVerificationFailure> failures) {
        if(failures.size() >= 128) return;
        UpdateSiteAdapterFixture minimized = new UpdateSiteAdapterFixture(fixture.extensionPoint(), fixture.elementName(),
                fixture.attributes().isEmpty() ? Map.of() : Map.of(fixture.attributes().keySet().iterator().next(), fixture.attributes().values().iterator().next()));
        failures.add(new UpdateSiteAdapterVerificationFailure(name, invariant, fixture, minimized, explanation));
    }

    /** Descripción: Ejecuta la operación «document» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] document(UpdateSiteAdapterVerificationRequest request) {
        if(!request.document().isBlank()) return request.document().getBytes(StandardCharsets.UTF_8);
        if(request.adapterId().isBlank()) throw new IllegalArgumentException("Provide either an inline adapter document or adapterId");
        return request.version().isBlank() ? adapters.exportAdapter(request.adapterId()) : adapters.exportVersion(request.adapterId(), request.version());
    }
    /** Descripción: Ejecuta la operación «reportRoot» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportRoot(){return properties.storageRootPath().resolve("adapter-verification");}
    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String id){String safe = id == null ? "" : id.trim(); if(!safe.matches("[A-Za-z0-9._-]{1,180}")) throw new IllegalArgumentException("Invalid adapter verification report ID"); return reportRoot().resolve(safe + ".json");}
    /** Descripción: Actualiza el estado gestionado por UpdateSiteAdapterVerificationService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value){try{return writer.writeValueAsBytes(value);}catch(Exception exception){throw new IllegalStateException("Could not serialize adapter verification report", exception);}}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de UpdateSiteAdapterVerificationService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path){try{return com.example.editor.io.BoundedIo.readInternal(path);}catch(IOException exception){throw new IllegalStateException("Could not read adapter verification report", exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path path, byte[] bytes){try{AtomicFiles.writeReplacing(path,bytes);}catch(IOException exception){throw new IllegalStateException("Could not persist adapter verification report", exception);}}
    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trim(){List<Path> paths; try(var stream = Files.list(reportRoot())){paths = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted(Comparator.comparingLong(this::modified).reversed()).toList();}catch(IOException exception){return;} for(int index = properties.getMaxAdapterBenchReports(); index < paths.size(); index++){try{Files.deleteIfExists(paths.get(index));}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteAdapterVerificationService.java",ignored);}}}
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException exception){return 0L;}}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value){return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");}
}
