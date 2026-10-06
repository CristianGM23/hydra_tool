package com.example.editor.event;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Tipo de evento definido por el usuario. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EventTypeDefinition(String id, String name, String namespace, String description, String category, String version,
        List<EventFieldDefinition> fields, Set<String> allowedSources, Set<EventSubjectKind> allowedSubjects,
        EventPersistencePolicy persistencePolicy, boolean abstractType, boolean deprecated, boolean enabled, Map<String, String> annotations) {
    public EventTypeDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "eventType.id");
        name = requireText(name, "eventType.name");
        namespace = requireNamespace(namespace);
        description = normalize(description);
        category = normalize(category);
        version = version == null || version.isBlank() ? "1.0" : version.trim();
        fields = fields == null ? List.of() : List.copyOf(fields);
        allowedSources = allowedSources == null ? Set.of() : Set.copyOf(allowedSources);
        allowedSubjects = allowedSubjects == null ? Set.of() : Set.copyOf(allowedSubjects);
        if(persistencePolicy == null) persistencePolicy = EventPersistencePolicy.TRANSIENT;
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «qualifiedName» correspondiente a la responsabilidad de EventTypeDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String qualifiedName() { return namespace + "." + name; }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) { if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank"); return value.trim(); }
    /** Descripción: Valida las condiciones necesarias para «requireNamespace» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireNamespace(String value) { String result = requireText(value, "eventType.namespace"); if(!result.matches("[A-Za-z][A-Za-z0-9_.-]*")) throw new IllegalArgumentException("eventType.namespace is invalid"); return result; }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de EventTypeDefinition. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
