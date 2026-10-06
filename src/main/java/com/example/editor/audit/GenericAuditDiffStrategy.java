package com.example.editor.audit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Diff neutral y acotado para mapas/listas/escalares serializados. */
@Component
public class GenericAuditDiffStrategy implements AuditDiffStrategy {
    private final AuditLogProperties properties;
    private final AuditRedactionRegistry redaction;

    /** Descripción: Inicializa una instancia de GenericAuditDiffStrategy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; redaction: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GenericAuditDiffStrategy(AuditLogProperties properties, AuditRedactionRegistry redaction) {
        this.properties = properties;
        this.redaction = redaction;
    }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean supports(AuditCapturedEvent event) { return true; }

    @Override
    /** Descripción: Ejecuta la operación «diff» correspondiente a la responsabilidad de GenericAuditDiffStrategy. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditDiffResult diff(AuditCapturedEvent event) {
        List<AuditChange> changes = new ArrayList<>();
        MutableFlag truncated = new MutableFlag();
        compare("$", event.before(), event.after(), changes, truncated);
        return new AuditDiffResult(changes, truncated.value);
    }

    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de GenericAuditDiffStrategy. Entradas: path: ruta o ubicación utilizada por la operación; before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación; truncated: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void compare(String path, Object before, Object after, List<AuditChange> changes, MutableFlag truncated) {
        if(truncated.value || Objects.equals(before, after)) return;
        if(changes.size() >= properties.getMaximumChanges()) { truncated.value = true; return; }
        if(redaction.sensitive(path)) {
            changes.add(new AuditChange(path, kind(before, after), AuditRedactionRegistry.REDACTED, AuditRedactionRegistry.REDACTED));
            return;
        }
        if(before instanceof Map<?, ?> beforeMap && after instanceof Map<?, ?> afterMap) {
            Set<String> keys = new LinkedHashSet<>();
            beforeMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            afterMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            for(String key : keys) {
                compare(child(path, key), valueForStringKey(beforeMap, key), valueForStringKey(afterMap, key), changes, truncated);
                if(truncated.value) return;
            }
            return;
        }
        if(before instanceof List<?> beforeList && after instanceof List<?> afterList) {
            int maximum = Math.max(beforeList.size(), afterList.size());
            for(int index = 0; index < maximum; index++) {
                Object oldValue = index < beforeList.size() ? beforeList.get(index) : null;
                Object newValue = index < afterList.size() ? afterList.get(index) : null;
                compare(path + "[" + index + "]", oldValue, newValue, changes, truncated);
                if(truncated.value) return;
            }
            return;
        }
        changes.add(new AuditChange(path, kind(before, after), protect(path, before), protect(path, after)));
    }

    /**
     * Descripción: Obtiene el valor asociado a una clave textual sin forzar el tipo genérico del mapa.
     * Entrada: @param map contiene los valores a consultar; @param key identifica la clave normalizada.
     * Salida: @return el valor asociado, o {@code null} cuando la clave no existe.
     */
    /** Descripción: Ejecuta la operación «valueForStringKey» correspondiente a la responsabilidad de GenericAuditDiffStrategy. Entradas: map: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Object valueForStringKey(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (Objects.equals(String.valueOf(entry.getKey()), key)) return entry.getValue();
        }
        return null;
    }

    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de GenericAuditDiffStrategy. Entradas: before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuditChangeKind kind(Object before, Object after) {
        if(before == null && after != null) return AuditChangeKind.ADDED;
        if(before != null && after == null) return AuditChangeKind.REMOVED;
        return AuditChangeKind.CHANGED;
    }
    /** Descripción: Ejecuta la operación «protect» correspondiente a la responsabilidad de GenericAuditDiffStrategy. Entradas: path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Object protect(String path, Object value) { return redaction.protect(path, value); }
    /** Descripción: Ejecuta la operación «child» correspondiente a la responsabilidad de GenericAuditDiffStrategy. Entradas: path: ruta o ubicación utilizada por la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String child(String path, String key) { return "$".equals(path) ? key : path + "." + key; }
    private static final class MutableFlag { private boolean value; }
}
