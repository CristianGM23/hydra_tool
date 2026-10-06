package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaRelation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Compara y fusiona candidatos sin promover confianza de forma automática. */
@Component
public class UpdateSiteMetamodelReconciliationService {
    private static final JsonMapper SAMPLE_MAPPER = JsonMapper.builder().build();
    private final UpdateSiteMetamodelRecoveryService recoveryService;
    private final UpdateSiteImportProperties properties;

    /** Descripción: Inicializa una instancia de UpdateSiteMetamodelReconciliationService con sus dependencias y datos de entrada. Entradas: recoveryService: servicio colaborador utilizado por la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteMetamodelReconciliationService(UpdateSiteMetamodelRecoveryService recoveryService,
            UpdateSiteImportProperties properties) {
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de ReconciliationState. Entradas: candidates: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ ReconciliationState analyze(List<UpdateSiteRecoveryCandidate> candidates, UpdateSiteRepositoryContent repository,
            String targetId) {
        List<UpdateSiteRecoveryCandidate> selected = candidates.stream().filter(UpdateSiteRecoveryCandidate::importable)
                .limit(properties.getMaxReconciliationCandidates()).toList();
        Map<String, CandidateModel> models = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for(UpdateSiteRecoveryCandidate candidate : selected) {
            try {
                MetaModel model = recoveryService.recover(candidate, repository, targetId);
                models.put(candidate.id(), new CandidateModel(candidate, model, index(model)));
            } catch(RuntimeException exception) {
                diagnostics.add("Candidate " + candidate.id() + " could not participate in reconciliation: " + exception.getMessage());
            }
        }
        if(models.isEmpty()) return new ReconciliationState(UpdateSiteReconciliationReport.empty(), Map.of());

        Set<String> union = new LinkedHashSet<>();
        models.values().forEach(value -> union.addAll(value.elements().keySet()));
        List<UpdateSiteReconciliationConflict> conflicts = new ArrayList<>();
        int agreed = 0;
        Map<String, Integer> unique = new LinkedHashMap<>();
        Map<String, Integer> conflictCounts = new LinkedHashMap<>();
        for(String key : union.stream().sorted().toList()) {
            Map<String, String> signatures = new LinkedHashMap<>();
            models.forEach((candidateId, candidate) -> {
                ElementValue value = candidate.elements().get(key);
                if(value != null) signatures.put(candidateId, value.signature());
            });
            if(signatures.size() == 1) {
                unique.merge(signatures.keySet().iterator().next(), 1, Integer::sum);
                continue;
            }
            if(new LinkedHashSet<>(signatures.values()).size() <= 1) {
                agreed++;
                continue;
            }
            signatures.keySet().forEach(candidateId -> conflictCounts.merge(candidateId, 1, Integer::sum));
            CandidateModel recommended = signatures.keySet().stream().map(models::get)
                    .min(Comparator.comparingInt((CandidateModel value) -> confidenceRank(value.candidate().confidence()))
                            .thenComparing((CandidateModel value) -> -value.candidate().score())
                            .thenComparing(value -> value.candidate().id())).orElseThrow();
            ElementValue sample = models.get(signatures.keySet().iterator().next()).elements().get(key);
            Map<String, String> structuralByCandidate = new LinkedHashMap<>();
            signatures.keySet().forEach(candidateId -> structuralByCandidate.put(candidateId,
                    structuralSignature(models.get(candidateId).elements().get(key).value())));
            Set<String> structuralSignatures = new LinkedHashSet<>(structuralByCandidate.values());
            Consensus consensus = safeStructuralConsensus(structuralByCandidate, models);
            boolean complementaryClass = safeComplementaryClass(signatures.keySet(), key, models)
                    && sourceSamplesCompatible(signatures.keySet(), key, models, repository);
            boolean blocking = structuralSignatures.size() > 1 && !consensus.safe() && !complementaryClass;
            if(consensus.safe()) recommended = models.get(consensus.recommendedCandidateId());
            String explanation = structuralSignatures.size() <= 1
                    ? "Candidates agree on the neutral structure and differ only in descriptive metadata. HYDRA can safely use the recommended higher-confidence evidence unless you override it."
                    : complementaryClass
                            ? "Candidates describe the same class with compatible, complementary features. HYDRA can safely union non-conflicting attributes and operations while preserving provenance."
                    : consensus.safe()
                            ? "Independent higher-confidence sources agree on the same structure while the dissenting evidence is lower confidence. HYDRA can safely use the consensus candidate unless you override it."
                            : "The same neutral element was reconstructed with different structure. Review the source and choose which evidence to keep.";
            conflicts.add(new UpdateSiteReconciliationConflict(key, sample.kind(), sample.name(), signatures,
                    recommended.candidate().id(), explanation, blocking));
        }
        int unionSize = union.size();
        List<UpdateSiteCandidateCoverage> coverages = new ArrayList<>();
        for(CandidateModel candidate : models.values()) {
            double coverage = unionSize == 0 ? 0 : candidate.elements().size() * 100.0 / unionSize;
            coverages.add(new UpdateSiteCandidateCoverage(candidate.candidate().id(), candidate.candidate().confidence(),
                    candidate.elements().size(), unique.getOrDefault(candidate.candidate().id(), 0),
                    conflictCounts.getOrDefault(candidate.candidate().id(), 0), coverage,
                    recommendation(candidate.candidate(), coverage)));
        }
        coverages.sort(Comparator.comparingInt((UpdateSiteCandidateCoverage value) -> confidenceRank(value.confidence()))
                .thenComparing(UpdateSiteCandidateCoverage::coveragePercent, Comparator.reverseOrder())
                .thenComparing(UpdateSiteCandidateCoverage::candidateId));
        String recommendedId = coverages.isEmpty() ? "" : coverages.getFirst().candidateId();
        double agreement = unionSize == 0 ? 0 : (agreed + Math.max(0, unionSize - conflicts.size() - unique.values().stream().mapToInt(Integer::intValue).sum())) * 100.0 / unionSize;
        if(models.size() == 1) diagnostics.add("Only one recoverable candidate was available; no cross-source comparison could be performed.");
        long blockingConflicts = conflicts.stream().filter(UpdateSiteReconciliationConflict::blocking).count();
        long safeConflicts = conflicts.size() - blockingConflicts;
        if(blockingConflicts > 0) diagnostics.add(blockingConflicts + " structural discrepancy or discrepancies require an explicit decision before merging.");
        if(safeConflicts > 0) diagnostics.add(safeConflicts + " descriptive conflict(s) can be resolved automatically from the recommended higher-confidence candidate.");
        UpdateSiteReconciliationReport report = new UpdateSiteReconciliationReport(coverages, conflicts, agreed,
                unionSize, agreement, recommendedId, blockingConflicts == 0, diagnostics);
        return new ReconciliationState(report, Map.copyOf(models));
    }

    /** Descripción: Actualiza el estado gestionado por ReconciliationState mediante la operación «merge». Entradas: state: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaModel merge(ReconciliationState state, UpdateSiteReconciliationRequest request) {
        if(state.models().isEmpty()) throw new IllegalArgumentException("No candidate model is available for reconciliation");
        String primaryId = request.primaryCandidateId().isBlank() ? state.report().recommendedCandidateId() : request.primaryCandidateId();
        CandidateModel primary = state.models().get(primaryId);
        if(primary == null) throw new IllegalArgumentException("Unknown primary reconciliation candidate: " + primaryId);
        Map<String, ElementValue> merged = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        state.models().values().forEach(value -> keys.addAll(value.elements().keySet()));
        Map<String, UpdateSiteReconciliationConflict> conflicts = state.report().conflicts().stream()
                .collect(java.util.stream.Collectors.toMap(UpdateSiteReconciliationConflict::elementKey, value -> value));
        for(String key : keys.stream().sorted().toList()) {
            String candidateId = primaryId;
            if(conflicts.containsKey(key)) {
                UpdateSiteReconciliationConflict conflict = conflicts.get(key);
                if(!conflict.blocking() && safeComplementaryClass(conflict.candidateSignatures().keySet(), key, state.models())) {
                    merged.put(key, unionClassElement(conflict.candidateSignatures().keySet(), key, state.models()));
                    continue;
                }
                candidateId = request.conflictCandidateIds().getOrDefault(key, "");
                if(candidateId.isBlank() && !conflict.blocking()) candidateId = conflict.recommendedCandidateId();
            }
            if(candidateId.isBlank()) throw new IllegalArgumentException("A reconciliation decision is required for " + key);
            CandidateModel candidate = state.models().get(candidateId);
            if(candidate == null || !candidate.elements().containsKey(key)) throw new IllegalArgumentException("Candidate " + candidateId + " does not provide " + key);
            merged.put(key, candidate.elements().get(key));
        }
        List<MetaClass> classes = values(merged, "CLASS", MetaClass.class);
        List<MetaEnum> enums = values(merged, "ENUM", MetaEnum.class);
        List<MetaDataType> dataTypes = values(merged, "DATATYPE", MetaDataType.class);
        List<MetaRelation> relations = values(merged, "RELATION", MetaRelation.class);
        String targetId = request.targetId().isBlank() ? primary.model().id() : request.targetId();
        Map<String, String> annotations = new LinkedHashMap<>(primary.model().annotations());
        annotations.put("hydra.reconciliation.primaryCandidate", primaryId);
        annotations.put("hydra.reconciliation.conflictsResolved", Integer.toString(state.report().conflicts().size()));
        return new MetaModel(primary.model().schemaVersion(), targetId, primary.model().name(), classes, enums, dataTypes,
                relations, annotations);
    }

    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de ReconciliationState. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, ElementValue> index(MetaModel model) {
        Map<String, ElementValue> result = new LinkedHashMap<>();
        model.classes().forEach(value -> result.put(elementKey("CLASS", value.id()), new ElementValue("CLASS", value.name(), signature(value), value)));
        model.enums().forEach(value -> result.put(elementKey("ENUM", value.id()), new ElementValue("ENUM", value.name(), signature(value), value)));
        model.dataTypes().forEach(value -> result.put(elementKey("DATATYPE", value.id()), new ElementValue("DATATYPE", value.name(), signature(value), value)));
        model.relations().forEach(value -> result.put(elementKey("RELATION", value.id()), new ElementValue("RELATION", value.name(), signature(value), value)));
        return Map.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «elementKey» correspondiente a la responsabilidad de ReconciliationState. Entradas: kind: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String elementKey(String kind, String id) {
        String safeId = id == null ? "" : id.trim();
        String technicalPrefix = kind.toLowerCase(Locale.ROOT) + ':';
        if(safeId.regionMatches(true, 0, technicalPrefix, 0, technicalPrefix.length())) {
            safeId = safeId.substring(technicalPrefix.length());
        }
        return kind + ':' + safeId;
    }

    /** Descripción: Ejecuta la operación «signature» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String signature(MetaClass value) {
        return value.kind() + "|" + value.name() + "|A:" + value.attributes().stream().map(item -> item.id() + ':' + item.type() + ':' + item.multiplicity()).sorted().toList()
                + "|O:" + value.operations().stream().map(item -> item.id() + ':' + item.returnType() + ':' + item.parameters().size()).sorted().toList();
    }
    /** Descripción: Ejecuta la operación «signature» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String signature(MetaEnum value) { return value.name() + '|' + value.literals().stream().map(item -> item.id() + ':' + item.name()).sorted().toList(); }
    /** Descripción: Ejecuta la operación «signature» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String signature(MetaDataType value) { return value.name() + '|' + value.annotations(); }
    /** Descripción: Ejecuta la operación «signature» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String signature(MetaRelation value) { return value.name() + '|' + value.sourceTypeId() + '|' + value.targetTypeId() + '|' + value.kind() + '|' + value.sourceMultiplicity() + '|' + value.targetMultiplicity(); }


    /** Descripción: Ejecuta la operación «structuralSignature» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String structuralSignature(Object value) {
        if(value instanceof MetaClass item) {
            return item.kind() + "|A:" + item.attributes().stream().map(attribute -> attribute.id() + ':' + attribute.type() + ':' + attribute.multiplicity()).sorted().toList()
                    + "|O:" + item.operations().stream().map(operation -> operation.id() + ':' + operation.returnType() + ':' + operation.parameters().stream().map(parameter -> parameter.type() + ":" + parameter.multiplicity()).toList()).sorted().toList();
        }
        if(value instanceof MetaEnum item) return item.literals().stream().map(literal -> literal.id() + ':' + literal.value()).sorted().toList().toString();
        if(value instanceof MetaDataType item) return item.annotations().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList().toString();
        if(value instanceof MetaRelation item) return item.sourceTypeId() + '|' + item.targetTypeId() + '|' + item.kind() + '|' + item.sourceMultiplicity() + '|' + item.targetMultiplicity() + '|' + item.oppositeRelationId();
        return String.valueOf(value);
    }

    /** Descripción: Ejecuta la operación «values» correspondiente a la responsabilidad de ReconciliationState. Entradas: source: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> values(Map<String, ElementValue> source, String kind, Class<T> type) {
        return source.values().stream().filter(value -> value.kind().equals(kind)).map(ElementValue::value)
                .filter(type::isInstance).map(type::cast)
                .sorted(Comparator.comparing(this::idOf)).toList();
    }
    /** Descripción: Ejecuta la operación «idOf» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String idOf(Object value) {
        if(value instanceof MetaClass item) return item.id();
        if(value instanceof MetaEnum item) return item.id();
        if(value instanceof MetaDataType item) return item.id();
        if(value instanceof MetaRelation item) return item.id();
        return value.toString();
    }
    /** Descripción: Ejecuta la operación «safeComplementaryClass» correspondiente a la responsabilidad de ReconciliationState. Entradas: candidateIds: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; models: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean safeComplementaryClass(Set<String> candidateIds, String key, Map<String, CandidateModel> models) {
        List<MetaClass> classes = candidateIds.stream().map(models::get).filter(java.util.Objects::nonNull)
                .map(candidate -> candidate.elements().get(key)).filter(java.util.Objects::nonNull)
                .map(ElementValue::value).filter(MetaClass.class::isInstance).map(MetaClass.class::cast).toList();
        if(classes.size() < 2 || classes.size() != candidateIds.size()) return false;
        MetaClass first = classes.getFirst();
        if(classes.stream().anyMatch(value -> value.kind() != first.kind() || !value.name().equals(first.name()))) return false;
        Map<String, String> attributes = new LinkedHashMap<>();
        Map<String, String> operations = new LinkedHashMap<>();
        for(MetaClass value : classes) {
            for(MetaAttribute attribute : value.attributes()) {
                String signature = attribute.type() + "|" + attribute.multiplicity();
                String previous = attributes.putIfAbsent(attribute.name(), signature);
                if(previous != null && !previous.equals(signature)) return false;
            }
            for(MetaOperation operation : value.operations()) {
                String signature = operation.returnType() + "|" + operation.returnMultiplicity() + "|"
                        + operation.parameters().stream().map(parameter -> parameter.type() + ":" + parameter.multiplicity()).toList();
                String previous = operations.putIfAbsent(operation.name(), signature);
                if(previous != null && !previous.equals(signature)) return false;
            }
        }
        return classes.stream().map(value -> value.attributes().size() + value.operations().size()).distinct().count() > 1
                || classes.stream().map(this::structuralSignature).distinct().count() > 1;
    }

    /**
     * JSON data recovery uses concrete documents as structural evidence. A complementary merge is safe only
     * when scalar fields shared by every recovered class do not carry contradictory sample values.
     */
    /** Descripción: Ejecuta la operación «sourceSamplesCompatible» correspondiente a la responsabilidad de ReconciliationState. Entradas: candidateIds: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; models: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sourceSamplesCompatible(Set<String> candidateIds, String key, Map<String, CandidateModel> models,
            UpdateSiteRepositoryContent repository) {
        if(!key.startsWith("CLASS:")) return true;
        List<CandidateModel> candidates = candidateIds.stream().map(models::get).filter(java.util.Objects::nonNull).toList();
        if(candidates.size() < 2 || candidates.stream().anyMatch(value -> !"JSON_DATA".equalsIgnoreCase(value.candidate().format()))) {
            return true;
        }
        List<MetaClass> classes = candidates.stream().map(value -> value.elements().get(key))
                .filter(java.util.Objects::nonNull).map(ElementValue::value)
                .filter(MetaClass.class::isInstance).map(MetaClass.class::cast).toList();
        if(classes.size() != candidates.size()) return false;
        Set<String> sharedAttributes = new LinkedHashSet<>(
                classes.getFirst().attributes().stream().map(MetaAttribute::name).toList());
        for(MetaClass value : classes.subList(1, classes.size())) {
            sharedAttributes.retainAll(value.attributes().stream().map(MetaAttribute::name).toList());
        }
        if(sharedAttributes.isEmpty()) return true;
        Map<String, String> observed = new LinkedHashMap<>();
        for(CandidateModel candidate : candidates) {
            byte[] content = repository.find(candidate.candidate().sourcePath()).orElse(null);
            if(content == null) return false;
            try {
                JsonNode root = SAMPLE_MAPPER.readTree(content);
                if(root == null || !root.isObject()) return false;
                for(String field : sharedAttributes) {
                    JsonNode sample = root.get(field);
                    if(sample == null || sample.isObject() || sample.isArray()) continue;
                    String previous = observed.putIfAbsent(field, sample.toString());
                    if(previous != null && !previous.equals(sample.toString())) return false;
                }
            } catch(Exception exception) {
                return false;
            }
        }
        return true;
    }

    /** Descripción: Ejecuta la operación «unionClassElement» correspondiente a la responsabilidad de ReconciliationState. Entradas: candidateIds: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; models: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ElementValue unionClassElement(Set<String> candidateIds, String key, Map<String, CandidateModel> models) {
        List<CandidateModel> candidates = candidateIds.stream().map(models::get).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt((CandidateModel value) -> confidenceRank(value.candidate().confidence()))
                        .thenComparing((CandidateModel value) -> -value.candidate().score()).thenComparing(value -> value.candidate().id())).toList();
        MetaClass base = (MetaClass) candidates.getFirst().elements().get(key).value();
        Map<String, MetaAttribute> attributes = new LinkedHashMap<>();
        Map<String, MetaOperation> operations = new LinkedHashMap<>();
        for(CandidateModel candidate : candidates) {
            MetaClass value = (MetaClass) candidate.elements().get(key).value();
            value.attributes().forEach(attribute -> attributes.putIfAbsent(attribute.name(), attribute));
            value.operations().forEach(operation -> operations.putIfAbsent(operation.name(), operation));
        }
        MetaClass union = new MetaClass(base.id(), base.name(), base.kind(),
                attributes.values().stream().sorted(Comparator.comparing(MetaAttribute::id)).toList(),
                operations.values().stream().sorted(Comparator.comparing(MetaOperation::id)).toList(), base.annotations());
        return new ElementValue("CLASS", union.name(), signature(union), union);
    }

    /** Descripción: Ejecuta la operación «safeStructuralConsensus» correspondiente a la responsabilidad de ReconciliationState. Entradas: structuralByCandidate: dato de entrada necesario para la operación; models: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Consensus safeStructuralConsensus(Map<String, String> structuralByCandidate,
            Map<String, CandidateModel> models) {
        if(structuralByCandidate.size() < 3) return Consensus.none();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        structuralByCandidate.forEach((candidateId, signature) -> groups.computeIfAbsent(signature, ignored -> new ArrayList<>()).add(candidateId));
        List<List<String>> ordered = groups.values().stream().sorted(Comparator.<List<String>>comparingInt(List::size).reversed()).toList();
        if(ordered.size() < 2 || ordered.getFirst().size() < 2 || ordered.getFirst().size() == ordered.get(1).size()) return Consensus.none();
        List<String> majority = ordered.getFirst();
        CandidateModel bestMajority = majority.stream().map(models::get).min(Comparator.comparingInt((CandidateModel value) -> confidenceRank(value.candidate().confidence())).thenComparing((CandidateModel value) -> -value.candidate().score())).orElseThrow();
        int majorityRank = confidenceRank(bestMajority.candidate().confidence());
        if(majorityRank > confidenceRank(UpdateSiteConfidence.RECOVERED)) return Consensus.none();
        int bestDissent = structuralByCandidate.keySet().stream().filter(id -> !majority.contains(id)).map(models::get).mapToInt(value -> confidenceRank(value.candidate().confidence())).min().orElse(Integer.MAX_VALUE);
        if(bestDissent <= majorityRank) return Consensus.none();
        return new Consensus(true, bestMajority.candidate().id());
    }

    /** Descripción: Ejecuta la operación «confidenceRank» correspondiente a la responsabilidad de ReconciliationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private int confidenceRank(UpdateSiteConfidence value) {
        return switch(value) { case EXACT -> 0; case RECOVERED -> 1; case DERIVED -> 2; case INFERRED -> 3; };
    }
    /** Descripción: Ejecuta la operación «recommendation» correspondiente a la responsabilidad de ReconciliationState. Entradas: candidate: dato de entrada necesario para la operación; coverage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String recommendation(UpdateSiteRecoveryCandidate candidate, double coverage) {
        return candidate.confidence() + " evidence with " + Math.round(coverage) + "% of the union of recovered elements.";
    }

    record ReconciliationState(UpdateSiteReconciliationReport report, Map<String, CandidateModel> models) { }
    private record Consensus(boolean safe, String recommendedCandidateId) { static Consensus none(){ return new Consensus(false, ""); } }
    private record CandidateModel(UpdateSiteRecoveryCandidate candidate, MetaModel model, Map<String, ElementValue> elements) { }
    private record ElementValue(String kind, String name, String signature, Object value) { }
}
