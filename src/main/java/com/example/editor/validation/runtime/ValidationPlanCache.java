package com.example.editor.validation.runtime;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Caché acotada y reemplazable de planes derivados, indexada por la huella de sus fuentes. */
@Component
public class ValidationPlanCache {
    private final ConcurrentHashMap<String, ExecutableValidationPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();
    private final int maxEntries;

    /** Descripción: Inicializa una instancia de ValidationPlanCache con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationPlanCache() { this(256); }

    @Autowired
    /** Descripción: Inicializa una instancia de ValidationPlanCache con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationPlanCache(ValidationRuntimeProperties properties) {
        this(properties == null ? 256 : properties.getMaxCachedPlans());
    }

    /** Descripción: Inicializa una instancia de ValidationPlanCache con sus dependencias y datos de entrada. Entradas: maxEntries: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationPlanCache(int maxEntries) {
        if(maxEntries < 1) throw new IllegalArgumentException("maxEntries must be at least 1");
        this.maxEntries = maxEntries;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de ValidationPlanCache. Entradas: fingerprint: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<ExecutableValidationPlan> find(String fingerprint) {
        return Optional.ofNullable(plans.get(fingerprint));
    }

    public synchronized ExecutableValidationPlan put(ExecutableValidationPlan plan) {
        String fingerprint = plan.sourceFingerprint();
        if(plans.put(fingerprint, plan) == null) insertionOrder.addLast(fingerprint);
        while(plans.size() > maxEntries) {
            String oldest = insertionOrder.pollFirst();
            if(oldest == null) break;
            plans.remove(oldest);
        }
        return plan;
    }

    public synchronized void invalidateMetamodel(String metamodelId) {
        plans.entrySet().removeIf(entry -> entry.getValue().metamodelId().equals(metamodelId));
        insertionOrder.removeIf(fingerprint -> !plans.containsKey(fingerprint));
    }

    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de ValidationPlanCache. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int size() { return plans.size(); }
    /** Descripción: Ejecuta la operación «maxEntries» correspondiente a la responsabilidad de ValidationPlanCache. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int maxEntries() { return maxEntries; }
}
