package com.example.editor.validation.orchestration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.editor.event.bus.CanonicalEventBus;
import com.example.editor.event.bus.EventPublicationBuffer;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.operation.runtime.CanonicalModelRuntimeAdapter;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationException;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;

/** Coordina validación, informes parciales, Problems, eventos y políticas de bloqueo. */
@Service
public class ValidationOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationOrchestrator.class);
    private final ValidationRuntimeApplicationService runtime;
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ModelDocumentService modelService;
    private final ValidationBlockingPolicy blockingPolicy;
    private final ValidationOrchestrationProperties properties;
    private final CanonicalEventBus eventBus;
    private final Map<String, ModelValidationReport> latestReports = new ConcurrentHashMap<>();
    private final LinkedHashSet<String> reportOrder = new LinkedHashSet<>();

    /** Descripción: Inicializa una instancia de ValidationOrchestrator con sus dependencias y datos de entrada. Entradas: runtime: dato de entrada necesario para la operación; metamodelService: servicio colaborador utilizado por la operación; modelService: servicio colaborador utilizado por la operación; blockingPolicy: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; eventBus: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationOrchestrator(ValidationRuntimeApplicationService runtime, CanonicalMetamodelApplicationService metamodelService,
            ModelDocumentService modelService, ValidationBlockingPolicy blockingPolicy, ValidationOrchestrationProperties properties,
            CanonicalEventBus eventBus) {
        this.runtime = runtime;
        this.metamodelService = metamodelService;
        this.modelService = modelService;
        this.blockingPolicy = blockingPolicy;
        this.properties = properties;
        this.eventBus = eventBus;
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; gate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OrchestratedValidationResult validate(String metamodelId, ValidationExecutionRequest request, ValidationGate gate) {
        requireEnabled();
        ModelValidationReport raw = runtime.validate(metamodelId, request);
        ModelValidationReport previous = latestReports.get(request.model().id());
        ModelValidationReport consolidated = consolidate(previous, raw, request);
        ValidationReportDelta delta = delta(previous, consolidated);
        remember(consolidated);
        ValidationBlockDecision blocking = blockingPolicy.evaluate(consolidated, gate);
        publish(consolidated, delta);
        return new OrchestratedValidationResult(consolidated, delta, blocking);
    }

    /** Descripción: Valida las condiciones necesarias para «validateLive» y rechaza entradas no válidas. Entradas: modelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OrchestratedValidationResult validateLive(String modelId, LiveValidationRequest request) {
        requireEnabled();
        if(request == null) request = LiveValidationRequest.full();
        ModelDocumentResponse live = modelService.find(modelId);
        MetaModel metamodel = metamodelService.findById(live.metamodelId()).metamodel();
        CanonicalModelDocument canonical = new CanonicalModelRuntimeAdapter(metamodel).toCanonical(live);
        ValidationExecutionRequest execution = new ValidationExecutionRequest(request.mode(), canonical, request.selectionElementIds(),
                request.classId(), request.changeSet(), request.variables());
        return validate(live.metamodelId(), execution, request.gate());
    }

    /** Descripción: Ejecuta la operación «latest» correspondiente a la responsabilidad de ValidationOrchestrator. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ModelValidationReport> latest(String modelId) { return Optional.ofNullable(latestReports.get(modelId)); }

    /** Descripción: Obtiene la información correspondiente a «findDiagnostic» dentro de ValidationOrchestrator. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ModelValidationDiagnostic> findDiagnostic(String modelId, String diagnosticId) {
        return latest(modelId).flatMap(report -> report.diagnostics().stream().filter(item -> item.id().equals(diagnosticId)).findFirst());
    }

    /** Descripción: Elimina o libera el recurso gestionado por «clear» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void clear(String modelId) {
        latestReports.remove(modelId);
        synchronized(reportOrder) { reportOrder.remove(modelId); }
    }

    /** Descripción: Ejecuta la operación «consolidate» correspondiente a la responsabilidad de ValidationOrchestrator. Entradas: previous: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelValidationReport consolidate(ModelValidationReport previous, ModelValidationReport raw, ValidationExecutionRequest request) {
        if(previous == null || replacesWholeReport(request.mode())) return raw;
        Set<String> affectedElements = affectedElements(request.model(), request.changeSet());
        Set<String> selected = new LinkedHashSet<>(request.selectionElementIds());
        Set<String> executedRules = new LinkedHashSet<>(raw.executedRuleIds());
        Map<String, ModelValidationDiagnostic> merged = new LinkedHashMap<>();
        for(ModelValidationDiagnostic diagnostic : previous.diagnostics()) {
            if(shouldRetain(diagnostic, request, executedRules, affectedElements, selected)) merged.put(diagnostic.id(), diagnostic);
        }
        raw.diagnostics().forEach(diagnostic -> merged.put(diagnostic.id(), diagnostic));
        List<ModelValidationDiagnostic> diagnostics = merged.values().stream().sorted(Comparator.comparing(ModelValidationDiagnostic::severity)
                .thenComparing(ModelValidationDiagnostic::ruleId).thenComparing(ModelValidationDiagnostic::targetElementId)
                .thenComparing(ModelValidationDiagnostic::id)).toList();
        return new ModelValidationReport(raw.schemaVersion(), raw.id(), raw.modelId(), raw.metamodelId(), raw.validationModelId(),
                raw.generatedAt(), raw.scope(), diagnostics, raw.metrics(), raw.planId(), raw.mode(), raw.startedAt(), raw.completedAt(),
                raw.executedRuleIds(), raw.skippedRuleIds());
    }

    /** Descripción: Evalúa la condición «shouldRetain» sin modificar el estado externo. Entradas: diagnostic: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; executedRules: dato de entrada necesario para la operación; affectedElements: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean shouldRetain(ModelValidationDiagnostic diagnostic, ValidationExecutionRequest request, Set<String> executedRules,
            Set<String> affectedElements, Set<String> selected) {
        if(!executedRules.contains(diagnostic.ruleId())) return true;
        return switch(request.mode()) {
            case INCREMENTAL -> !affectedElements.isEmpty() && !diagnostic.targetElementId().isBlank()
                    && !affectedElements.contains(diagnostic.targetElementId());
            case SELECTION -> !selected.contains(diagnostic.targetElementId());
            case CLASS -> !request.classId().isBlank() && !request.classId().equals(diagnostic.targetClassId());
            default -> false;
        };
    }

    /** Descripción: Actualiza el estado gestionado por ValidationOrchestrator mediante la operación «replacesWholeReport». Entradas: mode: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean replacesWholeReport(ValidationRunMode mode) {
        return mode == ValidationRunMode.FULL || mode == ValidationRunMode.ON_OPEN || mode == ValidationRunMode.ON_SAVE
                || mode == ValidationRunMode.BEFORE_EXPORT || mode == ValidationRunMode.MANUAL;
    }

    /** Descripción: Ejecuta la operación «affectedElements» correspondiente a la responsabilidad de ValidationOrchestrator. Entradas: model: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> affectedElements(CanonicalModelDocument model, ValidationChangeSet changes) {
        Set<String> result = new LinkedHashSet<>(changes.elementIds());
        if(!changes.relationIds().isEmpty()) model.relations().stream().filter(relation -> changes.relationIds().contains(relation.relationId()))
                .forEach(relation -> { result.add(relation.sourceElementId()); result.add(relation.targetElementId()); });
        return result;
    }

    /** Descripción: Ejecuta la operación «delta» correspondiente a la responsabilidad de ValidationOrchestrator. Entradas: previous: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationReportDelta delta(ModelValidationReport previous, ModelValidationReport current) {
        if(previous == null) return new ValidationReportDelta(current.diagnostics(), List.of(), List.of());
        Map<String, ModelValidationDiagnostic> before = index(previous.diagnostics());
        Map<String, ModelValidationDiagnostic> after = index(current.diagnostics());
        List<ModelValidationDiagnostic> created = new ArrayList<>();
        List<ModelValidationDiagnostic> unchanged = new ArrayList<>();
        List<ModelValidationDiagnostic> resolved = new ArrayList<>();
        after.forEach((id, diagnostic) -> { if(before.containsKey(id)) unchanged.add(diagnostic); else created.add(diagnostic); });
        before.forEach((id, diagnostic) -> { if(!after.containsKey(id)) resolved.add(diagnostic); });
        return new ValidationReportDelta(created, unchanged, resolved);
    }

    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de ValidationOrchestrator. Entradas: diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, ModelValidationDiagnostic> index(List<ModelValidationDiagnostic> diagnostics) {
        Map<String, ModelValidationDiagnostic> result = new LinkedHashMap<>();
        diagnostics.forEach(diagnostic -> result.put(diagnostic.id(), diagnostic));
        return result;
    }

    /** Descripción: Ejecuta la operación «remember» correspondiente a la responsabilidad de ValidationOrchestrator. Entradas: report: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void remember(ModelValidationReport report) {
        latestReports.put(report.modelId(), report);
        synchronized(reportOrder) {
            reportOrder.remove(report.modelId());
            reportOrder.add(report.modelId());
            while(reportOrder.size() > properties.getMaxTrackedModels()) {
                String oldest = reportOrder.iterator().next();
                reportOrder.remove(oldest);
                latestReports.remove(oldest);
            }
        }
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de ValidationOrchestrator. Entradas: report: dato de entrada necesario para la operación; delta: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publish(ModelValidationReport report, ValidationReportDelta delta) {
        EventPublicationBuffer buffer = new EventPublicationBuffer(eventBus);
        buffer.add(ValidationRuntimeEventFactory.validation("core.validation.execution-completed", report,
                Map.of("diagnosticCount", report.diagnostics().size(), "createdCount", delta.created().size(), "resolvedCount", delta.resolved().size(),
                        "mode", report.mode().name())));
        delta.created().forEach(diagnostic -> buffer.add(ValidationRuntimeEventFactory.diagnostic("core.validation.violation-created", report, diagnostic)));
        delta.resolved().forEach(diagnostic -> buffer.add(ValidationRuntimeEventFactory.diagnostic("core.validation.violation-resolved", report, diagnostic)));
        try { buffer.commit(); }
        catch(RuntimeException exception) { LOGGER.warn("Validation events could not be delivered after execution", exception); }
    }

    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() {
        if(!properties.isEnabled()) throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.NOT_EXECUTABLE,
                "Validation orchestration is disabled by configuration");
    }
}
