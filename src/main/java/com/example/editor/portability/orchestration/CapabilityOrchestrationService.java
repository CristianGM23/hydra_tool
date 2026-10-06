package com.example.editor.portability.orchestration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.merge.CapabilitySemanticMergePreview;
import com.example.editor.portability.merge.CapabilitySemanticMergeRequest;
import com.example.editor.portability.merge.CapabilitySemanticMergeService;
import com.example.editor.portability.transaction.CapabilityLayerTransactionRequest;
import com.example.editor.portability.transaction.CapabilityLayerTransactionResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Composes multiple portable packages through deterministic topological AM replay steps. */
@Service
public class CapabilityOrchestrationService {
    private final CapabilitySemanticMergeService semanticMerge;
    private final CapabilityLayerTransactionService transactions;
    private final ObjectWriter writer;
    private final Map<String, CapabilityOrchestrationPreview> previews = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de CapabilityOrchestrationService con sus dependencias y datos de entrada. Entradas: semanticMerge: dato de entrada necesario para la operación; transactions: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityOrchestrationService(CapabilitySemanticMergeService semanticMerge,
            CapabilityLayerTransactionService transactions, ObjectMapper mapper) {
        this.semanticMerge = semanticMerge; this.transactions = transactions;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public synchronized CapabilityOrchestrationPreview preview(CapabilityOrchestrationRequest request) {
        require(request);
        List<CapabilityOrchestrationSource> ordered = order(request.sources());
        HydraCapabilityPackage current = request.basePackage();
        List<CapabilityOrchestrationStep> steps = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        int index = 0;
        for(CapabilityOrchestrationSource source : ordered) {
            String inputFingerprint = fingerprint(current);
            String policyFingerprint = fingerprint(Map.of("sourceId", source.sourceId(), "precedence", source.precedence(),
                    "afterSourceIds", source.afterSourceIds(), "defaultDecision", source.defaultDecision(), "decisions", source.decisions()));
            CapabilitySemanticMergePreview merge = semanticMerge.preview(new CapabilitySemanticMergeRequest(
                    current, source.capabilityPackage(), request.targetMetamodelId(), Map.of(), request.layerOrder(),
                    source.defaultDecision(), source.decisions(), false));
            String outputFingerprint = fingerprint(merge.mergedPackage());
            steps.add(new CapabilityOrchestrationStep(index++, source.sourceId(), merge.previewId(),
                    policyFingerprint, inputFingerprint, outputFingerprint, merge.applicable(), merge.diagnostics()));
            if(!merge.applicable()) diagnostics.add("Source " + source.sourceId() + " produced a blocking AM preview");
            current = merge.mergedPackage();
        }
        List<String> precedence = ordered.stream().map(CapabilityOrchestrationSource::sourceId).toList();
        String orchestrationId = "orchestration-" + sha256(write(Map.of("order", precedence,
                "steps", steps, "output", fingerprint(current)))).substring(0, 24);
        boolean applicable = !steps.isEmpty() && steps.stream().allMatch(CapabilityOrchestrationStep::applicable);
        if(steps.isEmpty()) diagnostics.add("At least one source package is required");
        CapabilityOrchestrationPreview result = new CapabilityOrchestrationPreview(orchestrationId,
                current, precedence, steps, applicable, diagnostics);
        previews.put(orchestrationId, result);
        while(previews.size() > 200) previews.remove(previews.keySet().iterator().next());
        return result;
    }

    /** Descripción: Actualiza el estado gestionado por CapabilityOrchestrationService mediante la operación «apply». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityOrchestrationResult apply(CapabilityOrchestrationRequest request) {
        CapabilityOrchestrationPreview preview = preview(request);
        if(request.persist() && !preview.applicable()) throw new IllegalArgumentException("Multiorigin orchestration contains blocking merge steps");
        CapabilityLayerTransactionResult transaction = transactions.apply(new CapabilityLayerTransactionRequest(
                preview.mergedPackage(), request.targetMetamodelId(), request.expectedEtags(), request.layerOrder(), request.persist()));
        return new CapabilityOrchestrationResult(preview, transaction);
    }

    public synchronized CapabilityOrchestrationPreview find(String orchestrationId) {
        CapabilityOrchestrationPreview result = previews.get(text(orchestrationId));
        if(result == null) throw new IllegalArgumentException("Capability orchestration preview was not found");
        return result;
    }

    /** Descripción: Ejecuta la operación «replay» correspondiente a la responsabilidad de CapabilityOrchestrationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; expectedOrchestrationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityOrchestrationPreview replay(CapabilityOrchestrationRequest request, String expectedOrchestrationId) {
        CapabilityOrchestrationPreview replay = preview(request);
        if(!replay.orchestrationId().equals(text(expectedOrchestrationId))) {
            throw new IllegalStateException("Orchestration replay differs from the expected deterministic plan");
        }
        return replay;
    }

    /** Descripción: Ejecuta la operación «order» correspondiente a la responsabilidad de CapabilityOrchestrationService. Entradas: sources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<CapabilityOrchestrationSource> order(List<CapabilityOrchestrationSource> sources) {
        Map<String, CapabilityOrchestrationSource> byId = new LinkedHashMap<>();
        for(CapabilityOrchestrationSource source : sources) {
            if(source == null || source.sourceId().isBlank() || source.capabilityPackage() == null) {
                throw new IllegalArgumentException("Every orchestration source requires an ID and a package");
            }
            if(byId.putIfAbsent(source.sourceId(), source) != null) throw new IllegalArgumentException("Duplicate orchestration source ID: " + source.sourceId());
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        byId.keySet().forEach(id -> {indegree.put(id, 0); outgoing.put(id, new LinkedHashSet<>());});
        byId.values().forEach(source -> source.afterSourceIds().forEach(dependency -> {
            if(!byId.containsKey(dependency)) throw new IllegalArgumentException("Unknown orchestration dependency: " + dependency);
            if(outgoing.get(dependency).add(source.sourceId())) indegree.put(source.sourceId(), indegree.get(source.sourceId()) + 1);
        }));
        Comparator<String> comparator = Comparator.comparingInt((String id) -> byId.get(id).precedence()).thenComparing(id -> id);
        PriorityQueue<String> queue = new PriorityQueue<>(comparator);
        indegree.forEach((id, degree) -> {if(degree == 0) queue.add(id);});
        List<CapabilityOrchestrationSource> ordered = new ArrayList<>();
        while(!queue.isEmpty()) {
            String id = queue.remove(); ordered.add(byId.get(id));
            outgoing.get(id).stream().sorted(comparator).forEach(next -> {
                int degree = indegree.compute(next, (key, value) -> value - 1);
                if(degree == 0) queue.add(next);
            });
        }
        if(ordered.size() != sources.size()) throw new IllegalArgumentException("Orchestration precedence graph contains a cycle");
        return List.copyOf(ordered);
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(CapabilityOrchestrationRequest request) {
        if(request == null || request.basePackage() == null) throw new IllegalArgumentException("A base capability package is required");
        if(request.targetMetamodelId().isBlank()) throw new IllegalArgumentException("targetMetamodelId is required");
    }
    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de CapabilityOrchestrationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(Object value){return sha256(write(value));}
    /** Descripción: Actualiza el estado gestionado por CapabilityOrchestrationService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value){try{return writer.writeValueAsBytes(value);}catch(Exception exception){throw new IllegalArgumentException("Could not serialize orchestration evidence", exception);}}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de CapabilityOrchestrationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 is unavailable", exception);}}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilityOrchestrationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
