package com.example.editor.transformation.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTracePolicy;
import com.example.editor.transformation.runtime.TransformationBindingTrace;
import com.example.editor.transformation.runtime.TransformationExecutionResult;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.runtime.TransformationTrace;
import com.example.editor.transformation.text.TransformationGenerationResult;
import com.example.editor.transformation.text.TransformationTextGenerationService;
import com.example.editor.transformation.testing.persistence.TransformationTestCaseRepository;
import com.example.editor.transformation.textmodel.TransformationTextToModelResult;
import com.example.editor.transformation.textmodel.TransformationTextToModelService;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Ejecuta, compara y agrega cobertura de los casos de prueba persistidos de una transformación. */
@Service
public class TransformationTestService {
    private final TransformationTestCaseRepository repository;
    private final TransformationApplicationService transformations;
    private final TransformationRuntimeService m2m;
    private final TransformationTextGenerationService m2t;
    private final TransformationTextToModelService t2m;

    /** Descripción: Inicializa el servicio de pruebas de transformaciones. Entradas: repository: repositorio de casos; transformations: catálogo canónico; m2m: runtime M2M; m2t: runtime M2T; t2m: runtime T2M. Salida: deja el servicio preparado. */
    /** Descripción: Inicializa una instancia de TransformationTestService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; transformations: dato de entrada necesario para la operación; m2m: dato de entrada necesario para la operación; m2t: dato de entrada necesario para la operación; t2m: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTestService(TransformationTestCaseRepository repository, TransformationApplicationService transformations,
            TransformationRuntimeService m2m, TransformationTextGenerationService m2t, TransformationTextToModelService t2m) {
        this.repository = repository;
        this.transformations = transformations;
        this.m2m = m2m;
        this.m2t = m2t;
        this.t2m = t2m;
    }

    /** Descripción: Lista los casos asociados a una transformación. Entradas: transformationId: transformación propietaria. Salida: devuelve resúmenes ordenados por nombre. */
    /** Descripción: Obtiene la información correspondiente a «list» dentro de Execution. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationTestCaseSummary> list(String transformationId) {
        requireTransformation(transformationId);
        try {
            return repository.listIds().stream().map(repository::find).flatMap(java.util.Optional::stream)
                    .filter(stored -> transformationId.equals(stored.document().transformationId()))
                    .map(stored -> new TransformationTestCaseSummary(stored.document().id(), transformationId,
                            stored.document().name(), stored.document().enabled(), stored.etag()))
                    .sorted(java.util.Comparator.comparing(TransformationTestCaseSummary::name).thenComparing(TransformationTestCaseSummary::id))
                    .toList();
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Carga un caso persistido comprobando que pertenece a la transformación indicada. Entradas: transformationId: transformación; testCaseId: caso. Salida: devuelve el caso y su ETag. */
    /** Descripción: Obtiene la información correspondiente a «find» dentro de Execution. Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestCaseLoadResult find(String transformationId, String testCaseId) {
        requireTransformation(transformationId);
        StoredCanonicalDocument<TransformationTestCase> stored = stored(testCaseId);
        requireOwnership(transformationId, stored.document());
        return new TransformationTestCaseLoadResult(stored.document(), stored.etag());
    }

    /** Descripción: Crea un caso de prueba después de validar su contrato mínimo. Entradas: transformationId: transformación; testCase: caso. Salida: devuelve el caso persistido. */
    /** Descripción: Crea o prepara el resultado de «create» dentro de Execution. Entradas: transformationId: identificador que delimita el recurso objetivo; testCase: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestCaseSaveResponse create(String transformationId, TransformationTestCase testCase) {
        requireTransformation(transformationId);
        validate(transformationId, testCase);
        try {
            StoredCanonicalDocument<TransformationTestCase> stored = repository.create(testCase);
            return new TransformationTestCaseSaveResponse(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Reemplaza un caso existente con control optimista. Entradas: transformationId: transformación; testCaseId: caso; testCase: documento; etag: versión esperada. Salida: devuelve el caso persistido. */
    /** Descripción: Actualiza el estado gestionado por Execution mediante la operación «replace». Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo; testCase: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestCaseSaveResponse replace(String transformationId, String testCaseId, TransformationTestCase testCase, String etag) {
        requireTransformation(transformationId);
        if(testCase == null || !testCaseId.equals(testCase.id())) throw invalid("Path id and transformation test id must match");
        validate(transformationId, testCase);
        StoredCanonicalDocument<TransformationTestCase> existing = stored(testCaseId);
        requireOwnership(transformationId, existing.document());
        try {
            StoredCanonicalDocument<TransformationTestCase> stored = repository.replace(testCaseId, testCase, etag);
            return new TransformationTestCaseSaveResponse(stored.document(), stored.etag());
        } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Elimina un caso persistido de la transformación indicada. Entradas: transformationId: transformación; testCaseId: caso; etag: versión esperada. Salida: no devuelve valor. */
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String transformationId, String testCaseId, String etag) {
        StoredCanonicalDocument<TransformationTestCase> existing = stored(testCaseId);
        requireOwnership(transformationId, existing.document());
        try { repository.delete(testCaseId, etag); } catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Ejecuta un caso concreto y calcula cobertura únicamente a partir de esa ejecución. Entradas: transformationId: transformación; testCaseId: caso. Salida: devuelve un informe de suite de un único caso. */
    /** Descripción: Procesa la operación «runOne» coordinando las responsabilidades de Execution. Entradas: transformationId: identificador que delimita el recurso objetivo; testCaseId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestRunReport runOne(String transformationId, String testCaseId) {
        TransformationDefinitionDocument document = requireTransformation(transformationId);
        TransformationTestCase testCase = find(transformationId, testCaseId).testCase();
        return run(document, List.of(testCase));
    }

    /** Descripción: Ejecuta todos los casos habilitados y agrega su cobertura. Entradas: transformationId: transformación. Salida: devuelve resultados y cobertura acumulada. */
    /** Descripción: Procesa la operación «runAll» coordinando las responsabilidades de Execution. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTestRunReport runAll(String transformationId) {
        TransformationDefinitionDocument document = requireTransformation(transformationId);
        List<TransformationTestCase> cases = list(transformationId).stream().map(item -> find(transformationId, item.id()).testCase())
                .filter(TransformationTestCase::enabled).toList();
        return run(document, cases);
    }

    /** Descripción: Ejecuta una colección de casos contra la misma definición y acumula observaciones. Entradas: document: transformación; cases: casos. Salida: devuelve el informe agregado. */
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de Execution. Entradas: document: dato de entrada necesario para la operación; cases: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationTestRunReport run(TransformationDefinitionDocument document, List<TransformationTestCase> cases) {
        List<TransformationTestCaseResult> results = new ArrayList<>();
        Map<String,Integer> ruleHits = new LinkedHashMap<>();
        Map<String,Integer> bindingHits = new LinkedHashMap<>();
        for(TransformationTestCase testCase : cases) {
            try {
                Execution observation = execute(document, testCase);
                mergeHits(ruleHits, observation.ruleHits());
                mergeHits(bindingHits, observation.bindingHits());
                List<String> differences = compare(document.kind(), testCase, observation);
                results.add(new TransformationTestCaseResult(testCase.id(), testCase.name(),
                        differences.isEmpty() ? TransformationTestStatus.PASS : TransformationTestStatus.FAIL, differences));
            } catch(RuntimeException exception) {
                results.add(new TransformationTestCaseResult(testCase.id(), testCase.name(), TransformationTestStatus.ERROR,
                        List.of(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())));
            }
        }
        TransformationCoverageReport coverage = coverage(document, ruleHits, bindingHits);
        int passed = (int) results.stream().filter(item -> item.status() == TransformationTestStatus.PASS).count();
        int failed = (int) results.stream().filter(item -> item.status() == TransformationTestStatus.FAIL).count();
        int errors = (int) results.stream().filter(item -> item.status() == TransformationTestStatus.ERROR).count();
        return new TransformationTestRunReport(document.id(), results.size(), passed, failed, errors, results, coverage);
    }

    /** Descripción: Ejecuta un caso usando el runtime correspondiente y recoge hits de reglas/bindings. Entradas: document: transformación; testCase: caso. Salida: devuelve la observación neutral. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de Execution. Entradas: document: dato de entrada necesario para la operación; testCase: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Execution execute(TransformationDefinitionDocument document, TransformationTestCase testCase) {
        if(document.kind() == TransformationKind.M2M) {
            if(testCase.sourceModel() == null) throw invalid("M2M test case requires a source model");
            TransformationDefinitionDocument traced = forceDetailedTrace(document);
            TransformationExecutionResult result = testCase.executionDirection() == TransformationExecutionDirection.FORWARD
                    ? m2m.executeDraft(traced, testCase.sourceModel())
                    : m2m.executeDraft(traced, testCase.sourceModel(), testCase.executionDirection());
            return new Execution(result.targetModel(), List.of(), ruleHits(result.trace()), bindingHits(result.trace()));
        }
        if(document.kind() == TransformationKind.M2T) {
            if(testCase.sourceModel() == null) throw invalid("M2T test case requires a source model");
            TransformationGenerationResult result = m2t.preview(document, testCase.sourceModel());
            Map<String,Integer> rules = new LinkedHashMap<>();
            result.artifacts().forEach(item -> rules.merge(item.ruleId(), 1, Integer::sum));
            return new Execution(null, result.artifacts().stream().map(item -> new TransformationExpectedArtifact(item.path(), item.content())).toList(), rules, Map.of());
        }
        TransformationTextToModelResult result = t2m.preview(document, testCase.sourceText());
        return new Execution(result.projectedModel() != null ? result.projectedModel() : result.parsedModel(), List.of(), Map.of(), Map.of());
    }

    /** Descripción: Compara el resultado ejecutado con la expectativa persistida. Entradas: kind: familia; testCase: caso; execution: observación. Salida: devuelve diferencias legibles. */
    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de Execution. Entradas: kind: dato de entrada necesario para la operación; testCase: dato de entrada necesario para la operación; execution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> compare(TransformationKind kind, TransformationTestCase testCase, Execution execution) {
        List<String> differences = new ArrayList<>();
        if(kind == TransformationKind.M2T) {
            Map<String,String> expected = new LinkedHashMap<>();
            testCase.expectedArtifacts().forEach(item -> expected.put(item.path(), item.content()));
            Map<String,String> actual = new LinkedHashMap<>();
            execution.artifacts().forEach(item -> actual.put(item.path(), item.content()));
            if(!expected.keySet().equals(actual.keySet())) differences.add("Generated artifact paths differ. Expected " + expected.keySet() + " but got " + actual.keySet());
            for(String path : expected.keySet()) if(actual.containsKey(path) && !expected.get(path).equals(actual.get(path))) differences.add("Generated content differs for " + path);
            return differences;
        }
        if(testCase.expectedModel() == null) return List.of("Expected canonical model is not defined");
        if(!testCase.expectedModel().equals(execution.model())) differences.add("Canonical model result differs from the expected model");
        return differences;
    }

    /** Descripción: Construye cobertura sobre reglas habilitadas y bindings M2M habilitados. Entradas: document: transformación; ruleHits: impactos por regla; bindingHits: impactos por binding. Salida: devuelve cobertura agregada. */
    /** Descripción: Ejecuta la operación «coverage» correspondiente a la responsabilidad de Execution. Entradas: document: dato de entrada necesario para la operación; ruleHits: dato de entrada necesario para la operación; bindingHits: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationCoverageReport coverage(TransformationDefinitionDocument document, Map<String,Integer> ruleHits, Map<String,Integer> bindingHits) {
        List<TransformationRuleCoverage> rules = new ArrayList<>();
        List<TransformationBindingCoverage> bindings = new ArrayList<>();
        if(document.kind() == TransformationKind.M2M) {
            for(TransformationRule rule : document.rules()) if(rule.enabled()) {
                int hits = ruleHits.getOrDefault(rule.id(), 0);
                rules.add(new TransformationRuleCoverage(rule.id(), rule.name(), hits, hits > 0));
                rule.bindings().forEach(binding -> {
                    int bindingCount = bindingHits.getOrDefault(binding.id(), 0);
                    bindings.add(new TransformationBindingCoverage(rule.id(), binding.id(), bindingCount, bindingCount > 0));
                });
            }
        } else if(document.kind() == TransformationKind.M2T) {
            document.generationRules().stream().filter(item -> item.enabled()).forEach(rule -> {
                int hits = ruleHits.getOrDefault(rule.id(), 0);
                rules.add(new TransformationRuleCoverage(rule.id(), rule.name(), hits, hits > 0));
            });
        }
        int ruleCovered = (int) rules.stream().filter(TransformationRuleCoverage::covered).count();
        int bindingCovered = (int) bindings.stream().filter(TransformationBindingCoverage::covered).count();
        return new TransformationCoverageReport(rules.size(), ruleCovered, bindings.size(), bindingCovered, rules, bindings);
    }

    /** Descripción: Fuerza traza detallada únicamente en la copia usada por testing. Entradas: document: definición original. Salida: devuelve una copia semánticamente equivalente con detalle de traza activo. */
    /** Descripción: Ejecuta la operación «forceDetailedTrace» correspondiente a la responsabilidad de Execution. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationDefinitionDocument forceDetailedTrace(TransformationDefinitionDocument document) {
        return new TransformationDefinitionDocument(document.schemaVersion(), document.id(), document.name(), document.kind(), document.directionality(), document.synchronizationPolicy(),
                document.sources(), document.targets(), document.rules(), document.generationRules(), document.textToModel(),
                document.executionPolicy(), new TransformationTracePolicy(true, true), document.annotations());
    }

    /** Descripción: Cuenta impactos de reglas a partir de enlaces de traza. Entradas: trace: traza canónica. Salida: devuelve impactos por regla. */
    /** Descripción: Ejecuta la operación «ruleHits» correspondiente a la responsabilidad de Execution. Entradas: trace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Integer> ruleHits(TransformationTrace trace) {
        Map<String,Integer> result = new LinkedHashMap<>();
        trace.links().forEach(item -> result.merge(item.ruleId(), 1, Integer::sum));
        return result;
    }

    /** Descripción: Cuenta impactos de bindings a partir de la traza detallada. Entradas: trace: traza canónica. Salida: devuelve impactos por binding. */
    /** Descripción: Ejecuta la operación «bindingHits» correspondiente a la responsabilidad de Execution. Entradas: trace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Integer> bindingHits(TransformationTrace trace) {
        Map<String,Integer> result = new LinkedHashMap<>();
        for(TransformationBindingTrace item : trace.bindingTraces()) result.merge(item.bindingId(), 1, Integer::sum);
        return result;
    }

    /** Descripción: Acumula contadores observados. Entradas: target: acumulador; source: impactos nuevos. Salida: no devuelve valor. */
    /** Descripción: Actualiza el estado gestionado por Execution mediante la operación «mergeHits». Entradas: target: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void mergeHits(Map<String,Integer> target, Map<String,Integer> source) { source.forEach((key,value) -> target.merge(key, value, Integer::sum)); }

    /** Descripción: Valida el contrato mínimo de un caso persistente. Entradas: transformationId: transformación; testCase: caso. Salida: no devuelve valor. */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: transformationId: identificador que delimita el recurso objetivo; testCase: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validate(String transformationId, TransformationTestCase testCase) {
        if(testCase == null) throw invalid("Transformation test case is required");
        if(!TransformationTestCase.SUPPORTED_SCHEMA_VERSIONS.contains(testCase.schemaVersion())) throw invalid("Unsupported transformation test schemaVersion");
        if(testCase.id().isBlank()) throw invalid("Transformation test id is required");
        if(testCase.name().isBlank()) throw invalid("Transformation test name is required");
        if(!transformationId.equals(testCase.transformationId())) throw invalid("Test case transformationId must match the path transformation id");
    }

    /** Descripción: Obtiene y valida la transformación propietaria. Entradas: transformationId: identificador. Salida: devuelve la definición persistida. */
    /** Descripción: Valida las condiciones necesarias para «requireTransformation» y rechaza entradas no válidas. Entradas: transformationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationDefinitionDocument requireTransformation(String transformationId) { return transformations.find(transformationId).transformation(); }

    /** Descripción: Carga un caso o produce NOT_FOUND estable. Entradas: id: identificador. Salida: devuelve el documento persistido. */
    /** Descripción: Ejecuta la operación «stored» correspondiente a la responsabilidad de Execution. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private StoredCanonicalDocument<TransformationTestCase> stored(String id) {
        try { return repository.find(id).orElseThrow(() -> new TransformationApplicationException(TransformationApplicationException.Reason.NOT_FOUND, "Transformation test was not found: " + id)); }
        catch(CanonicalDocumentStoreException exception) { throw translate(exception); }
    }

    /** Descripción: Comprueba que un caso pertenece a la transformación solicitada. Entradas: transformationId: transformación; testCase: caso. Salida: no devuelve valor. */
    /** Descripción: Valida las condiciones necesarias para «requireOwnership» y rechaza entradas no válidas. Entradas: transformationId: identificador que delimita el recurso objetivo; testCase: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireOwnership(String transformationId, TransformationTestCase testCase) {
        if(!transformationId.equals(testCase.transformationId())) throw new TransformationApplicationException(TransformationApplicationException.Reason.NOT_FOUND, "Transformation test was not found in transformation: " + transformationId);
    }

    /** Descripción: Traduce errores del almacén al contrato de aplicación existente. Entradas: exception: error de persistencia. Salida: devuelve la excepción de aplicación equivalente. */
    /** Descripción: Ejecuta la operación «translate» correspondiente a la responsabilidad de Execution. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationApplicationException translate(CanonicalDocumentStoreException exception) {
        TransformationApplicationException.Reason reason = switch(exception.getReason()) {
            case INVALID_REQUEST -> TransformationApplicationException.Reason.INVALID_REQUEST;
            case NOT_FOUND -> TransformationApplicationException.Reason.NOT_FOUND;
            case CONFLICT -> TransformationApplicationException.Reason.CONFLICT;
            case PRECONDITION_FAILED -> TransformationApplicationException.Reason.PRECONDITION_FAILED;
            case STORAGE_ERROR -> TransformationApplicationException.Reason.STORAGE_ERROR;
        };
        return new TransformationApplicationException(reason, exception.getMessage(), exception);
    }

    /** Descripción: Crea un error de solicitud inválida con el contrato compartido. Entradas: message: detalle del error. Salida: devuelve la excepción preparada. */
    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de Execution. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationApplicationException invalid(String message) { return new TransformationApplicationException(TransformationApplicationException.Reason.INVALID_REQUEST, message); }

    private record Execution(com.example.editor.validation.model.CanonicalModelDocument model,
            List<TransformationExpectedArtifact> artifacts, Map<String,Integer> ruleHits, Map<String,Integer> bindingHits) { }
}
