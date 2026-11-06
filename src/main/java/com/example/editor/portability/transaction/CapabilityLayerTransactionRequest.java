package com.example.editor.portability.transaction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.portability.HydraCapabilityPackage;

/** Request to preview or atomically apply a portable capability package. */
public record CapabilityLayerTransactionRequest(HydraCapabilityPackage capabilityPackage,
        String targetMetamodelId, Map<String, String> expectedEtags, List<String> layerOrder,
        boolean persist, Map<String, String> decisions) {
    /** Sin decisiones registradas (transacciones que no proceden de una fusión semántica). */
    /** Descripción: Inicializa una instancia de CapabilityLayerTransactionRequest con sus dependencias y datos de entrada. Entradas: capabilityPackage: dato de entrada necesario para la operación; targetMetamodelId: identificador que delimita el recurso objetivo; expectedEtags: dato de entrada necesario para la operación; layerOrder: dato de entrada necesario para la operación; persist: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityLayerTransactionRequest(HydraCapabilityPackage capabilityPackage, String targetMetamodelId,
            Map<String, String> expectedEtags, List<String> layerOrder, boolean persist) {
        this(capabilityPackage, targetMetamodelId, expectedEtags, layerOrder, persist, Map.of());
    }
    public CapabilityLayerTransactionRequest {
        decisions = decisions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(decisions));
        targetMetamodelId = targetMetamodelId == null ? "" : targetMetamodelId.trim();
        expectedEtags = expectedEtags == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(expectedEtags));
        layerOrder = layerOrder == null ? List.of() : List.copyOf(layerOrder);
    }
}
