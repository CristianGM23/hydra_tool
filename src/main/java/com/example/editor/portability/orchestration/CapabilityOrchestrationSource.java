package com.example.editor.portability.orchestration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.merge.CapabilitySemanticMergeDecision;

/** One portable source and its deterministic precedence constraints. */
public record CapabilityOrchestrationSource(String sourceId, int precedence,
        List<String> afterSourceIds, HydraCapabilityPackage capabilityPackage,
        CapabilitySemanticMergeDecision defaultDecision,
        Map<String, CapabilitySemanticMergeDecision> decisions) {
    public CapabilityOrchestrationSource {
        sourceId = text(sourceId);
        afterSourceIds = afterSourceIds == null ? List.of() : afterSourceIds.stream()
                .map(CapabilityOrchestrationSource::text).filter(value -> !value.isBlank()).distinct().toList();
        defaultDecision = defaultDecision == null ? CapabilitySemanticMergeDecision.MERGE : defaultDecision;
        decisions = decisions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilityOrchestrationSource. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
