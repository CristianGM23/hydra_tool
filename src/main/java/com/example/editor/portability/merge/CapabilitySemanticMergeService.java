package com.example.editor.portability.merge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import com.example.editor.portability.CapabilityDependency;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.TargetCapabilityCompatibility;
import com.example.editor.portability.transaction.CapabilityLayerRollbackRequest;
import com.example.editor.portability.transaction.CapabilityLayerRollbackResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionRequest;
import com.example.editor.portability.transaction.CapabilityLayerTransactionResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Performs identifier-aware and semantic-signature-aware merges across canonical HYDRA layers. */
@Service
public class CapabilitySemanticMergeService {
    private static final Set<String> REFERENCE_FIELDS = Set.of("validationid", "quickfixid", "actionid",
            "operationid", "eventid", "eventtypeid", "flowid", "nodeid", "sourcenodeid", "targetnodeid",
            "ruleid", "mappingid", "bindingid", "toolid", "representationid", "classid", "sourceid",
            "targetid", "dependson", "references");
    private final CapabilityLayerTransactionService transactions;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de CapabilitySemanticMergeService con sus dependencias y datos de entrada. Entradas: transactions: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilitySemanticMergeService(CapabilityLayerTransactionService transactions, ObjectMapper mapper) {
        this.transactions = transactions;
        this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de Pair. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilitySemanticMergePreview preview(CapabilitySemanticMergeRequest request) {
        require(request);
        MergeState state = new MergeState(request);
        Set<String> paths = new TreeSet<>();
        paths.addAll(request.existingPackage().canonicalDocuments().keySet());
        paths.addAll(request.recoveredPackage().canonicalDocuments().keySet());
        Map<String, JsonNode> mergedDocuments = new LinkedHashMap<>();
        for(String path : paths) {
            JsonNode existing = request.existingPackage().canonicalDocuments().get(path);
            JsonNode recovered = request.recoveredPackage().canonicalDocuments().get(path);
            JsonNode merged = mergeDocument(path, existing, recovered, state);
            if(merged != null) mergedDocuments.put(path, merged);
        }
        Set<String> identities = new LinkedHashSet<>();
        identities.add(request.targetMetamodelId());
        mergedDocuments.forEach((path, node) -> collectIdentities(node, identities));
        mergedDocuments.forEach((path, node) -> collectDependencies(path, node, identities, state));
        state.dependencies.sort(Comparator.comparing(CapabilitySemanticMergeDependency::sourceKey)
                .thenComparing(CapabilitySemanticMergeDependency::targetReference));
        List<CapabilitySemanticMergeDependency> unresolved = state.dependencies.stream()
                .filter(item -> !item.resolved()).toList();
        if(!unresolved.isEmpty()) state.diagnostics.add(unresolved.size() + " unresolved cross-layer reference(s) block the merge");
        HydraCapabilityPackage mergedPackage = packageWithChecksums(request, mergedDocuments, state);
        String previewId = "semantic-merge-" + sha256(write(mergedPackage)).substring(0, 24);
        int kept = count(state.items, CapabilitySemanticMergeDecision.KEEP_EXISTING);
        int recovered = count(state.items, CapabilitySemanticMergeDecision.USE_RECOVERED);
        int merged = count(state.items, CapabilitySemanticMergeDecision.MERGE);
        int skipped = count(state.items, CapabilitySemanticMergeDecision.SKIP);
        boolean applicable = unresolved.isEmpty() && mergedDocuments.size() > 0
                && state.conflicts.stream().noneMatch(item -> "ERROR".equals(item.severity()));
        if(mergedDocuments.isEmpty()) state.diagnostics.add("The merge does not contain any canonical document");
        return new CapabilitySemanticMergePreview(previewId, mergedPackage, state.items, state.conflicts,
                state.dependencies, kept, recovered, merged, skipped, applicable, state.diagnostics);
    }

    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «apply». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilitySemanticMergeResult apply(CapabilitySemanticMergeRequest request) {
        CapabilitySemanticMergePreview preview = preview(request);
        if(request.persist() && !preview.applicable()) throw new IllegalArgumentException("Deep semantic merge contains blocking dependencies or conflicts");
        CapabilityLayerTransactionRequest transactionRequest = new CapabilityLayerTransactionRequest(
                preview.mergedPackage(), request.targetMetamodelId(), request.expectedEtags(), request.layerOrder(), request.persist());
        CapabilityLayerTransactionResult transaction = transactions.apply(transactionRequest);
        return new CapabilitySemanticMergeResult(preview, transaction);
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de Pair. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerRollbackResult rollback(CapabilityLayerRollbackRequest request) {
        return transactions.rollback(request);
    }

    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «mergeDocument». Entradas: path: ruta o ubicación utilizada por la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JsonNode mergeDocument(String path, JsonNode existing, JsonNode recovered, MergeState state) {
        String key = path + "#document";
        CapabilitySemanticMergeDecision decision = state.decision(key, recommended(existing, recovered));
        if(existing == null || existing.isNull()) return chooseSingle(path, "", recovered, false, decision, state);
        if(recovered == null || recovered.isNull()) return chooseSingle(path, "", existing, true, decision, state);
        if(existing.equals(recovered)) {
            state.addItem(key, path, "", semanticKind(path, "document"), display(existing, path), true, true,
                    false, CapabilitySemanticMergeDecision.KEEP_EXISTING, decision, references(existing),
                    "The canonical documents are semantically identical.");
            return decision == CapabilitySemanticMergeDecision.SKIP ? null : existing.deepCopy();
        }
        state.addItem(key, path, "", semanticKind(path, "document"), display(existing, path), true, true,
                true, CapabilitySemanticMergeDecision.MERGE, decision, references(recovered),
                "The same canonical layer exists in both packages and requires a semantic decision.");
        state.conflicts.add(conflict(key, "DOCUMENT", existing, recovered,
                "The canonical layer differs between the existing and recovered packages."));
        return switch(decision) {
            case KEEP_EXISTING -> existing.deepCopy();
            case USE_RECOVERED -> recovered.deepCopy();
            case SKIP -> null;
            case MERGE -> mergeNode(path, "", existing, recovered, state);
        };
    }

    /** Descripción: Ejecuta la operación «chooseSingle» correspondiente a la responsabilidad de Pair. Entradas: path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; existing: dato de entrada necesario para la operación; decision: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JsonNode chooseSingle(String path, String pointer, JsonNode value, boolean existing,
            CapabilitySemanticMergeDecision decision, MergeState state) {
        String key = path + (pointer.isBlank() ? "#document" : pointer);
        CapabilitySemanticMergeDecision effective = decision;
        if(existing && decision == CapabilitySemanticMergeDecision.USE_RECOVERED) effective = CapabilitySemanticMergeDecision.SKIP;
        if(!existing && decision == CapabilitySemanticMergeDecision.KEEP_EXISTING) effective = CapabilitySemanticMergeDecision.SKIP;
        CapabilitySemanticMergeDecision recommended = existing ? CapabilitySemanticMergeDecision.KEEP_EXISTING
                : CapabilitySemanticMergeDecision.USE_RECOVERED;
        state.addItem(key, path, pointer, semanticKind(path, last(pointer)), display(value, last(pointer)), existing,
                !existing, false, recommended, effective, references(value),
                existing ? "Only the existing package provides this semantic entity."
                        : "Only the recovered package provides this semantic entity.");
        return effective == CapabilitySemanticMergeDecision.SKIP ? null : value.deepCopy();
    }

    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «mergeNode». Entradas: path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JsonNode mergeNode(String path, String pointer, JsonNode existing, JsonNode recovered, MergeState state) {
        if(existing == null || existing.isNull()) return recovered == null ? null : recovered.deepCopy();
        if(recovered == null || recovered.isNull()) return existing.deepCopy();
        if(existing.isObject() && recovered.isObject()) return mergeObject(path, pointer, existing, recovered, state);
        if(existing.isArray() && recovered.isArray()) return mergeArray(path, pointer, existing, recovered, state);
        if(existing.equals(recovered)) return existing.deepCopy();
        String key = path + pointer;
        CapabilitySemanticMergeDecision selected = state.decision(key, CapabilitySemanticMergeDecision.KEEP_EXISTING);
        state.addItem(key, path, pointer, semanticKind(path, last(pointer)), last(pointer), true, true, true,
                CapabilitySemanticMergeDecision.KEEP_EXISTING, selected, references(recovered),
                "The scalar value differs; MERGE keeps the existing value unless USE_RECOVERED is selected.");
        state.conflicts.add(conflict(key, "VALUE", existing, recovered, "A scalar semantic property differs."));
        return selected == CapabilitySemanticMergeDecision.USE_RECOVERED ? recovered.deepCopy()
                : selected == CapabilitySemanticMergeDecision.SKIP ? null : existing.deepCopy();
    }

    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «mergeObject». Entradas: path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ObjectNode mergeObject(String path, String pointer, JsonNode existing, JsonNode recovered, MergeState state) {
        ObjectNode result = mapper.createObjectNode();
        Set<String> fields = new TreeSet<>();
        existing.properties().forEach(entry -> fields.add(entry.getKey()));
        recovered.properties().forEach(entry -> fields.add(entry.getKey()));
        for(String field : fields) {
            JsonNode merged = mergeNode(path, pointer + "/" + escape(field), existing.get(field), recovered.get(field), state);
            if(merged != null) result.set(field, merged);
        }
        return result;
    }

    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «mergeArray». Entradas: path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ArrayNode mergeArray(String path, String pointer, JsonNode existing, JsonNode recovered, MergeState state) {
        if(objectArray(existing) && objectArray(recovered)) return mergeObjectArray(path, pointer, existing, recovered, state);
        ArrayNode result = mapper.createArrayNode();
        Map<String, JsonNode> unique = new LinkedHashMap<>();
        existing.forEach(item -> unique.putIfAbsent(fingerprint(item), item));
        recovered.forEach(item -> unique.putIfAbsent(fingerprint(item), item));
        unique.values().forEach(item -> result.add(item.deepCopy()));
        if(!existing.equals(recovered)) {
            String key = path + pointer;
            state.addItem(key, path, pointer, semanticKind(path, last(pointer)), last(pointer), true, true, true,
                    CapabilitySemanticMergeDecision.MERGE, state.decision(key, CapabilitySemanticMergeDecision.MERGE),
                    references(result), "Primitive values were combined deterministically without duplicates.");
        }
        return result;
    }

    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «mergeObjectArray». Entradas: path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ArrayNode mergeObjectArray(String path, String pointer, JsonNode existing, JsonNode recovered, MergeState state) {
        List<JsonNode> existingItems = elements(existing);
        List<JsonNode> recoveredItems = elements(recovered);
        Map<String, JsonNode> existingById = index(existingItems);
        Map<String, List<Map.Entry<String, JsonNode>>> existingBySignature = signatureIndex(existingById);
        Set<String> consumed = new LinkedHashSet<>();
        List<Pair> pairs = new ArrayList<>();
        for(JsonNode recoveredItem : recoveredItems) {
            String recoveredId = identity(recoveredItem);
            String existingId = recoveredId;
            JsonNode matching = recoveredId.isBlank() || consumed.contains(recoveredId) ? null : existingById.get(recoveredId);
            boolean signatureMatch = false;
            if(matching == null) {
                String recoveredSignature = signature(recoveredItem);
                List<Map.Entry<String, JsonNode>> candidates = availableCandidates(existingBySignature.get(recoveredSignature), consumed);
                if(candidates.size() == 1) {
                    Map.Entry<String, JsonNode> candidate = candidates.get(0);
                    existingId = candidate.getKey(); matching = candidate.getValue(); signatureMatch = true;
                }
            }
            if(matching != null) consumed.add(existingId);
            pairs.add(new Pair(existingId, matching, recoveredItem, signatureMatch));
        }
        existingById.forEach((id, item) -> {if(!consumed.contains(id)) pairs.add(new Pair(id, item, null, false));});
        pairs.sort(Comparator.comparing(pair -> pair.id == null ? "" : pair.id));
        ArrayNode result = mapper.createArrayNode();
        for(Pair pair : pairs) {
            String id = pair.id == null || pair.id.isBlank() ? shortFingerprint(pair.recovered) : pair.id;
            String entityPointer = pointer + "/" + escape(id);
            String key = path + entityPointer;
            CapabilitySemanticMergeDecision recommendation = recommended(pair.existing, pair.recovered);
            CapabilitySemanticMergeDecision selected = state.decision(key, recommendation);
            boolean conflict = pair.existing != null && pair.recovered != null && !pair.existing.equals(pair.recovered);
            String explanation = pair.signatureMatch
                    ? "The entity has a different identifier but the same semantic signature."
                    : conflict ? "The same semantic entity differs between packages."
                    : pair.existing == null ? "The entity is new in the recovered package."
                    : pair.recovered == null ? "The entity only exists in the current package."
                    : "The entity is identical in both packages.";
            state.addItem(key, path, entityPointer, semanticKind(path, last(pointer)),
                    display(pair.recovered != null ? pair.recovered : pair.existing, id), pair.existing != null,
                    pair.recovered != null, conflict || pair.signatureMatch, recommendation, selected,
                    references(pair.recovered != null ? pair.recovered : pair.existing), explanation);
            if(pair.signatureMatch) state.conflicts.add(conflict(key, "SEMANTIC_SIGNATURE", pair.existing,
                    pair.recovered, "Different identifiers describe the same semantic role."));
            else if(conflict) state.conflicts.add(conflict(key, "ENTITY", pair.existing, pair.recovered,
                    "The entity payload differs for the same stable identifier."));
            JsonNode selectedNode = switch(selected) {
                case KEEP_EXISTING -> pair.existing == null ? null : pair.existing.deepCopy();
                case USE_RECOVERED -> pair.recovered == null ? null : pair.recovered.deepCopy();
                case SKIP -> null;
                case MERGE -> mergeNode(path, entityPointer, pair.existing, pair.recovered, state);
            };
            if(selectedNode != null) result.add(selectedNode);
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «packageWithChecksums» correspondiente a la responsabilidad de Pair. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; documents: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraCapabilityPackage packageWithChecksums(CapabilitySemanticMergeRequest request,
            Map<String, JsonNode> documents, MergeState state) {
        HydraCapabilityPackageManifest base = request.existingPackage().manifest() != null
                ? request.existingPackage().manifest() : request.recoveredPackage().manifest();
        Map<String, String> checksums = new LinkedHashMap<>();
        documents.forEach((path, node) -> checksums.put(path, sha256(write(node))));
        Map<String, String> annotations = new LinkedHashMap<>();
        if(base != null) annotations.putAll(base.annotations());
        annotations.put("hydra.semanticMerge", "semantic-deep-merge");
        annotations.put("hydra.semanticMerge.conflicts", Integer.toString(state.conflicts.size()));
        annotations.put("hydra.semanticMerge.dependencies", Integer.toString(state.dependencies.size()));
        List<CapabilitySelection> capabilities = union(base == null ? List.of() : base.capabilities(),
                request.recoveredPackage().manifest() == null ? List.of() : request.recoveredPackage().manifest().capabilities());
        List<CapabilityDependency> dependencies = union(base == null ? List.of() : base.dependencies(),
                request.recoveredPackage().manifest() == null ? List.of() : request.recoveredPackage().manifest().dependencies());
        List<TargetCapabilityCompatibility> compatibility = union(base == null ? List.of() : base.compatibility(),
                request.recoveredPackage().manifest() == null ? List.of() : request.recoveredPackage().manifest().compatibility());
        HydraCapabilityPackageManifest manifest = base == null ? null : new HydraCapabilityPackageManifest(
                base.schemaVersion(), base.id(), base.name(), base.packageVersion(), request.targetMetamodelId(),
                base.metamodelOrigin(), capabilities, dependencies, compatibility, checksums, annotations);
        Map<String, String> resources = new LinkedHashMap<>(request.existingPackage().resources());
        request.recoveredPackage().resources().forEach((path, value) -> {
            String existing = resources.get(path);
            if(existing != null && !existing.equals(value)) {
                state.conflicts.add(new CapabilitySemanticMergeConflict("resource:" + path, "RESOURCE", "ERROR",
                        sha256(existing.getBytes(StandardCharsets.UTF_8)).substring(0, 16),
                        sha256(value.getBytes(StandardCharsets.UTF_8)).substring(0, 16),
                        "The same resource path contains different bytes in both packages."));
                state.diagnostics.add("Resource collision blocks the merge: " + path);
            } else resources.putIfAbsent(path, value);
        });
        return new HydraCapabilityPackage(manifest, documents, resources);
    }

    /** Descripción: Ejecuta la operación «collectIdentities» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación; identities: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectIdentities(JsonNode node, Set<String> identities) {
        if(node == null) return;
        if(node.isObject()) {
            JsonNode own = node.get("id");
            if(own != null && own.isTextual() && !own.asText().isBlank()) identities.add(own.asText().trim());
            else for(String field : List.of("ruleId", "operationId", "eventId", "nodeId", "mappingId", "bindingId")) {
                // Sin "id" propio, solo los campos que identity() acepta como identidad definen el nodo;
                // validationId/quickFixId/actionId/... son siempre referencias y nunca se registran como identidad.
                JsonNode id = node.get(field);
                if(id != null && id.isTextual() && !id.asText().isBlank()) identities.add(id.asText().trim());
            }
            node.properties().forEach(entry -> collectIdentities(entry.getValue(), identities));
        } else if(node.isArray()) node.forEach(item -> collectIdentities(item, identities));
    }

    /** Descripción: Ejecuta la operación «collectDependencies» correspondiente a la responsabilidad de Pair. Entradas: path: ruta o ubicación utilizada por la operación; node: dato de entrada necesario para la operación; identities: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectDependencies(String path, JsonNode node, Set<String> identities, MergeState state) {
        collectDependencies(path, "", node, identities, state);
    }

    /** Descripción: Ejecuta la operación «collectDependencies» correspondiente a la responsabilidad de Pair. Entradas: path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; identities: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectDependencies(String path, String pointer, JsonNode node, Set<String> identities, MergeState state) {
        if(node == null) return;
        if(node.isObject()) {
            String source = path + pointer;
            node.properties().forEach(entry -> {
                String field = entry.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if(REFERENCE_FIELDS.contains(field) || field.endsWith("ids")) {
                    dependencyValues(value).forEach(reference -> {
                        boolean resolved = reference.isBlank() || identities.contains(reference) || externalReference(reference);
                        state.dependencies.add(new CapabilitySemanticMergeDependency(source, reference, resolved,
                                resolved ? "Reference resolves inside the merged package or is explicitly external."
                                        : "Reference does not resolve to any stable identifier in the merged package."));
                    });
                }
                collectDependencies(path, pointer + "/" + escape(entry.getKey()), value, identities, state);
            });
        } else if(node.isArray()) {
            int index = 0; for(JsonNode item : node) collectDependencies(path, pointer + "/" + index++, item, identities, state);
        }
    }

    /** Descripción: Ejecuta la operación «dependencyValues» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> dependencyValues(JsonNode node) {
        if(node == null) return List.of();
        if(node.isTextual()) return List.of(node.asText().trim()).stream().filter(value -> !value.isBlank()).toList();
        if(node.isArray()) {List<String> values = new ArrayList<>(); node.forEach(item -> {if(item.isTextual() && !item.asText().isBlank()) values.add(item.asText().trim());}); return values;}
        return List.of();
    }

    /** Descripción: Ejecuta la operación «externalReference» correspondiente a la responsabilidad de Pair. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean externalReference(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("platform:/")
                || value.startsWith("urn:") || value.contains("#//") || value.startsWith("java:");
    }

    /** Descripción: Ejecuta la operación «references» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> references(JsonNode node) {
        if(node == null) return List.of();
        Set<String> result = new LinkedHashSet<>();
        collectReferenceValues(node, result); return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «collectReferenceValues» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectReferenceValues(JsonNode node, Set<String> result) {
        if(node == null) return;
        if(node.isObject()) node.properties().forEach(entry -> {
            String field = entry.getKey().toLowerCase(Locale.ROOT);
            if(REFERENCE_FIELDS.contains(field) || field.endsWith("ids")) result.addAll(dependencyValues(entry.getValue()));
            collectReferenceValues(entry.getValue(), result);
        });
        else if(node.isArray()) node.forEach(item -> collectReferenceValues(item, result));
    }

    /** Descripción: Ejecuta la operación «objectArray» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean objectArray(JsonNode node) {
        if(node == null || !node.isArray()) return false;
        for(JsonNode item : node) if(!item.isObject()) return false;
        return true;
    }

    /** Descripción: Ejecuta la operación «elements» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<JsonNode> elements(JsonNode node) {List<JsonNode> result = new ArrayList<>(); node.forEach(result::add); return result;}

    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de Pair. Entradas: items: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, JsonNode> index(List<JsonNode> items) {
        Map<String, JsonNode> result = new LinkedHashMap<>(); int index = 0;
        for(JsonNode item : items) {
            String id = identity(item); if(id.isBlank()) id = "anonymous-" + index + "-" + shortFingerprint(item);
            while(result.containsKey(id)) id = id + "-duplicate"; result.put(id, item); index++;
        }
        return result;
    }

    /** Descripción: Construye un índice estable de firma semántica a candidatos existentes para evitar recalcular hashes y serializaciones en cada elemento recuperado. Entradas: existingById: entidades existentes indexadas por identificador. Salida: devuelve las candidatas agrupadas por firma preservando el orden determinista. */ private Map<String, List<Map.Entry<String, JsonNode>>> signatureIndex(Map<String, JsonNode> existingById) {
        Map<String, List<Map.Entry<String, JsonNode>>> result = new LinkedHashMap<>();
        existingById.forEach((id, node) -> result.computeIfAbsent(signature(node), ignored -> new ArrayList<>()).add(Map.entry(id, node)));
        return result;
    }

    /** Descripción: Obtiene las candidatas de una firma que todavía no han sido consumidas, manteniendo la ambigüedad cuando existe más de una. Entradas: candidates: candidatas precalculadas; consumed: identificadores ya emparejados. Salida: devuelve únicamente candidatas disponibles. */ private List<Map.Entry<String, JsonNode>> availableCandidates(List<Map.Entry<String, JsonNode>> candidates, Set<String> consumed) {
        if(candidates == null || candidates.isEmpty()) return List.of();
        List<Map.Entry<String, JsonNode>> available = new ArrayList<>(Math.min(candidates.size(), 2));
        for(Map.Entry<String, JsonNode> candidate : candidates) {
            if(consumed.contains(candidate.getKey())) continue;
            available.add(candidate);
            if(available.size() > 1) break;
        }
        return List.copyOf(available);
    }

    /** Descripción: Ejecuta la operación «identity» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String identity(JsonNode node) {
        if(node == null || !node.isObject()) return "";
        for(String field : List.of("id", "ruleId", "operationId", "eventId", "nodeId", "mappingId", "bindingId", "name")) {
            JsonNode value = node.get(field); if(value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText().trim();
        }
        return "";
    }

    /** Descripción: Ejecuta la operación «signature» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String signature(JsonNode node) {
        if(node == null || !node.isObject()) return fingerprint(node);
        List<String> fields = List.of("name", "label", "kind", "type", "event", "operation", "source", "target", "expression", "condition");
        StringBuilder value = new StringBuilder();
        for(String field : fields) {JsonNode item = node.get(field); if(item != null && item.isValueNode()) value.append(field).append('=').append(item.asText().trim().toLowerCase(Locale.ROOT)).append(';');}
        return value.isEmpty() ? fingerprint(node) : value.toString();
    }

    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de Pair. Entradas: key: identificador que delimita el recurso objetivo; type: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; explanation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilitySemanticMergeConflict conflict(String key, String type, JsonNode existing,
            JsonNode recovered, String explanation) {
        return new CapabilitySemanticMergeConflict(key, type, "WARNING", shortFingerprint(existing),
                shortFingerprint(recovered), explanation);
    }

    /** Descripción: Ejecuta la operación «recommended» correspondiente a la responsabilidad de Pair. Entradas: existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilitySemanticMergeDecision recommended(JsonNode existing, JsonNode recovered) {
        if(existing == null || existing.isNull()) return CapabilitySemanticMergeDecision.USE_RECOVERED;
        if(recovered == null || recovered.isNull()) return CapabilitySemanticMergeDecision.KEEP_EXISTING;
        return existing.equals(recovered) ? CapabilitySemanticMergeDecision.KEEP_EXISTING : CapabilitySemanticMergeDecision.MERGE;
    }

    /** Descripción: Ejecuta la operación «semanticKind» correspondiente a la responsabilidad de Pair. Entradas: path: ruta o ubicación utilizada por la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String semanticKind(String path, String field) {
        String value = (path + "/" + field).toLowerCase(Locale.ROOT);
        if(value.contains("quick")) return "QUICK_FIX";
        if(value.contains("validation") || value.contains("rule")) return "VALIDATION_RULE";
        if(value.contains("operation")) return "OPERATION";
        if(value.contains("action")) return "ACTION";
        if(value.contains("event-flow") || value.contains("eventflow") || value.contains("nodes")) return "EVENT_FLOW_NODE";
        if(value.contains("event")) return "EVENT";
        if(value.contains("gamification") || value.contains("reward") || value.contains("penalt")) return "GAMIFICATION_RULE";
        if(value.contains("mapping")) return "MAPPING";
        if(value.contains("binding")) return "BINDING";
        if(value.contains("palette") || value.contains("tools")) return "PALETTE_TOOL";
        if(value.contains("form")) return "FORM";
        return "CANONICAL_ENTITY";
    }

    /** Descripción: Ejecuta la operación «display» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String display(JsonNode node, String fallback) {
        if(node != null && node.isObject()) for(String field : List.of("name", "label", "id", "title")) {
            JsonNode value = node.get(field); if(value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText();
        }
        return fallback == null || fallback.isBlank() ? "Canonical entity" : fallback;
    }

    /** Descripción: Ejecuta la operación «last» correspondiente a la responsabilidad de Pair. Entradas: pointer: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String last(String pointer) {if(pointer == null || pointer.isBlank()) return "document"; int index = pointer.lastIndexOf('/'); return pointer.substring(index + 1).replace("~1", "/").replace("~0", "~");}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de Pair. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value) {return value.replace("~", "~0").replace("/", "~1");}
    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fingerprint(JsonNode node) {return sha256(write(node));}
    /** Descripción: Ejecuta la operación «shortFingerprint» correspondiente a la responsabilidad de Pair. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String shortFingerprint(JsonNode node) {return node == null ? "missing" : fingerprint(node).substring(0, 16);}
    /** Descripción: Actualiza el estado gestionado por Pair mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value) {try{return writer.writeValueAsBytes(value);}catch(RuntimeException exception){throw new IllegalArgumentException("Could not canonicalize semantic merge content", exception);}}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de Pair. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException("SHA-256 must be available", exception);}}
    /** Descripción: Ejecuta la operación «count» correspondiente a la responsabilidad de Pair. Entradas: items: colección de elementos que debe procesarse; decision: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int count(List<CapabilitySemanticMergeItem> items, CapabilitySemanticMergeDecision decision) {return (int)items.stream().filter(item -> item.selectedDecision() == decision).count();}
    /** Descripción: Ejecuta la unión ordenada de dos listas sin búsquedas lineales repetidas. Entradas: left: valores prioritarios; right: valores adicionales. Salida: devuelve una lista sin duplicados conservando el primer orden de aparición. */ private <T> List<T> union(List<T> left, List<T> right) {LinkedHashSet<T> result = new LinkedHashSet<>(left); result.addAll(right); return List.copyOf(result);}

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(CapabilitySemanticMergeRequest request) {
        if(request == null || request.existingPackage() == null || request.recoveredPackage() == null) throw new IllegalArgumentException("existingPackage and recoveredPackage are required");
        if(request.targetMetamodelId().isBlank()) throw new IllegalArgumentException("targetMetamodelId is required");
        if(request.existingPackage().canonicalDocuments().isEmpty() && request.recoveredPackage().canonicalDocuments().isEmpty()) throw new IllegalArgumentException("At least one canonical document is required");
    }

    private static final class MergeState {
        private final CapabilitySemanticMergeRequest request;
        private final List<CapabilitySemanticMergeItem> items = new ArrayList<>();
        private final List<CapabilitySemanticMergeConflict> conflicts = new ArrayList<>();
        private final List<CapabilitySemanticMergeDependency> dependencies = new ArrayList<>();
        private final List<String> diagnostics = new ArrayList<>();
        /** Descripción: Inicializa una instancia de MergeState con sus dependencias y datos de entrada. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: deja la instancia inicializada y lista para su uso. */ private MergeState(CapabilitySemanticMergeRequest request){this.request = request;}
        /** Descripción: Ejecuta la operación «decision» correspondiente a la responsabilidad de Pair. Entradas: key: identificador que delimita el recurso objetivo; recommended: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilitySemanticMergeDecision decision(String key, CapabilitySemanticMergeDecision recommended){return request.decisions().getOrDefault(key, request.defaultDecision() == null ? recommended : request.defaultDecision());}
        /** Descripción: Ejecuta la operación «addItem» correspondiente a la responsabilidad de Pair. Entradas: key: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; pointer: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; display: dato de entrada necesario para la operación; existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación; conflict: dato de entrada necesario para la operación; recommended: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación; references: dato de entrada necesario para la operación; explanation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addItem(String key, String path, String pointer, String kind, String display,
                boolean existing, boolean recovered, boolean conflict, CapabilitySemanticMergeDecision recommended,
                CapabilitySemanticMergeDecision selected, List<String> references, String explanation) {
            items.add(new CapabilitySemanticMergeItem(key, path, pointer, kind, display, existing, recovered,
                    conflict, recommended, selected, references, explanation));
        }
    }
    private record Pair(String id, JsonNode existing, JsonNode recovered, boolean signatureMatch) { }
}
