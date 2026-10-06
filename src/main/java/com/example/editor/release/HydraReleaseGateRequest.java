package com.example.editor.release;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.merge.CapabilitySemanticMergePreview;

/** Inputs required to create a reproducible release attestation. */
public record HydraReleaseGateRequest(String productId, String version,
        HydraCapabilityPackage capabilityPackage, CapabilitySemanticMergePreview semanticMerge,
        String mirrorId, List<String> adapterBenchReportIds,
        String performanceBaselineReportId, String performanceCandidateReportId,
        boolean requireSemanticMerge, boolean requireMirror, boolean requireAdapterBench,
        boolean requirePerformance, Map<String, String> annotations) {
    public HydraReleaseGateRequest {
        productId = text(productId); version = text(version); mirrorId = text(mirrorId);
        adapterBenchReportIds = adapterBenchReportIds == null ? List.of() : adapterBenchReportIds.stream().map(HydraReleaseGateRequest::text).filter(value -> !value.isBlank()).distinct().toList();
        performanceBaselineReportId = text(performanceBaselineReportId); performanceCandidateReportId = text(performanceCandidateReportId);
        annotations = annotations == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de HydraReleaseGateRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
