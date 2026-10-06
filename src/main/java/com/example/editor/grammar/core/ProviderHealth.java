package com.example.editor.grammar.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Health is capability-aware so a degraded completion service does not take parsing down with it. */
public record ProviderHealth(String status, boolean circuitOpen, String providerInstanceId, int consecutiveFailures,
        String detail, List<String> capabilitiesUnavailable, Instant checkedAt, Map<String,String> metadata) {
    public ProviderHealth {
        status = text(status);
        if(status.isEmpty()) status="HEALTHY";
        providerInstanceId = text(providerInstanceId);
        consecutiveFailures = Math.max(0, consecutiveFailures);
        detail = text(detail);
        capabilitiesUnavailable = capabilitiesUnavailable == null ? List.of() : List.copyOf(capabilitiesUnavailable);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Inicializa una instancia de ProviderHealth con sus dependencias y datos de entrada. Entradas: status: dato de entrada necesario para la operación; circuitOpen: dato de entrada necesario para la operación; detail: dato de entrada necesario para la operación; capabilitiesUnavailable: dato de entrada necesario para la operación; checkedAt: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProviderHealth(String status, boolean circuitOpen, String detail, List<String> capabilitiesUnavailable, Instant checkedAt) {
        this(status, circuitOpen, "", 0, detail, capabilitiesUnavailable, checkedAt, Map.of());
    }
    /** Descripción: Ejecuta la operación «healthy» correspondiente a la responsabilidad de ProviderHealth. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ProviderHealth healthy(){return healthy("");}
    /** Descripción: Ejecuta la operación «healthy» correspondiente a la responsabilidad de ProviderHealth. Entradas: providerInstanceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static ProviderHealth healthy(String providerInstanceId){return new ProviderHealth("HEALTHY",false,providerInstanceId,0,"",List.of(),Instant.now(),Map.of());}
    /** Descripción: Ejecuta la operación «usable» correspondiente a la responsabilidad de ProviderHealth. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean usable(){return !"UNAVAILABLE".equals(status) && !circuitOpen;}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de ProviderHealth. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
