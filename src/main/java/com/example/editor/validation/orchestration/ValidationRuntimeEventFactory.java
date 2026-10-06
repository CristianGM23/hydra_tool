package com.example.editor.validation.orchestration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.EventSubject;
import com.example.editor.event.EventSubjectKind;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ModelValidationReport;

/** Construye eventos reservados del runtime de validaciones y quick fixes. */
public final class ValidationRuntimeEventFactory {
    /** Descripción: Inicializa una instancia de ValidationRuntimeEventFactory con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ValidationRuntimeEventFactory() { }

    /** Descripción: Ejecuta la operación «validation» correspondiente a la responsabilidad de ValidationRuntimeEventFactory. Entradas: eventTypeId: identificador que delimita el recurso objetivo; report: dato de entrada necesario para la operación; payload: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static CanonicalEvent validation(String eventTypeId, ModelValidationReport report, Map<String, Object> payload) {
        return event(eventTypeId, report.metamodelId(), report.modelId(), EventSubjectKind.MODEL, report.modelId(), "", payload, "");
    }

    /** Descripción: Ejecuta la operación «diagnostic» correspondiente a la responsabilidad de ValidationRuntimeEventFactory. Entradas: eventTypeId: identificador que delimita el recurso objetivo; report: dato de entrada necesario para la operación; diagnostic: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static CanonicalEvent diagnostic(String eventTypeId, ModelValidationReport report, ModelValidationDiagnostic diagnostic) {
        return event(eventTypeId, report.metamodelId(), report.modelId(), EventSubjectKind.VALIDATION_DIAGNOSTIC,
                diagnostic.id(), diagnostic.targetFeatureId(), Map.of("ruleId", diagnostic.ruleId(), "severity", diagnostic.severity().name(),
                        "elementId", diagnostic.targetElementId(), "message", diagnostic.message()), "");
    }

    /** Descripción: Ejecuta la operación «quickFix» correspondiente a la responsabilidad de ValidationRuntimeEventFactory. Entradas: eventTypeId: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; modelId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; payload: valor de entrada que debe procesarse; transactionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static CanonicalEvent quickFix(String eventTypeId, String metamodelId, String modelId, String actionId,
            String diagnosticId, Map<String, Object> payload, String transactionId) {
        return event(eventTypeId, metamodelId, modelId, EventSubjectKind.QUICK_FIX, actionId, "", merge(payload, diagnosticId), transactionId);
    }

    /** Descripción: Ejecuta la operación «event» correspondiente a la responsabilidad de ValidationRuntimeEventFactory. Entradas: eventTypeId: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; modelId: identificador que delimita el recurso objetivo; subjectKind: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; featureId: identificador que delimita el recurso objetivo; payload: valor de entrada que debe procesarse; transactionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static CanonicalEvent event(String eventTypeId, String metamodelId, String modelId, EventSubjectKind subjectKind,
            String elementId, String featureId, Map<String, Object> payload, String transactionId) {
        String eventId = "event:" + UUID.randomUUID();
        EventSource source = new EventSource("HYDRA", "validation-runtime", "", "1.0");
        EventContext context = new EventContext(eventTypeId.startsWith("core.quickfix") ? EventContextKind.QUICK_FIX : EventContextKind.VALIDATION,
                "", "", metamodelId, modelId, "", "");
        String subjectElementId = subjectKind == EventSubjectKind.MODEL ? eventDocumentId(modelId) : elementId;
        EventSubject subject = new EventSubject(subjectKind, eventDocumentId(modelId), subjectElementId, "", featureId, "");
        return new CanonicalEvent("1.0", eventId, eventTypeId, "1.0", Instant.now(), source, context, null, subject,
                transactionId, transactionId, "", payload, Map.of());
    }

    /** Descripción: Ejecuta la operación «eventDocumentId» correspondiente a la responsabilidad de ValidationRuntimeEventFactory. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String eventDocumentId(String modelId) {
        if(StableIdentifierPolicy.isValidDocumentId(modelId)) return modelId;
        String source = modelId == null ? "" : modelId;
        return "runtime-model-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Actualiza el estado gestionado por ValidationRuntimeEventFactory mediante la operación «merge». Entradas: payload: valor de entrada que debe procesarse; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Object> merge(Map<String, Object> payload, String diagnosticId) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        if(payload != null) result.putAll(payload);
        result.put("diagnosticId", diagnosticId == null ? "" : diagnosticId);
        return Map.copyOf(result);
    }
}
