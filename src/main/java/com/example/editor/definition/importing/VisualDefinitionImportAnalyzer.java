package com.example.editor.definition.importing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaRelation;

/**
 * Deep, non-executing semantic analysis shared by Sirius, GMF, Sirius Web and draw.io imports.
 * It never mutates the metamodel. Only deterministic reference normalizations are applied automatically.
 */
@Component
public class VisualDefinitionImportAnalyzer {
    private static final Pattern TOKEN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FEATURE_ACCESS = Pattern.compile("\\b(?:self|this|[A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Set<String> BUILTIN_FEATURES = Set.of("size", "isEmpty", "notEmpty", "first", "last", "oclIsKindOf",
            "oclIsTypeOf", "oclAsType", "toString", "trim", "length", "startsWith", "endsWith", "contains", "matches",
            "collect", "select", "reject", "exists", "forAll", "any", "closure", "flatten", "asSet", "asSequence");

    /** Descripción: Obtiene la información correspondiente a «resolveClass» dentro de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; required: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String resolveClass(VisualDefinitionImportDraft draft, String raw, String source, String location, boolean required) {
        draft.referenceChecked();
        String token = fragment(raw);
        if(token.isBlank()) {
            if(required) missingClass(draft, source, location, raw);
            return "";
        }
        List<MetaClass> exact = draft.metamodel.classes().stream().filter(value -> classExact(value, token)).toList();
        if(exact.size() == 1) return exact.get(0).id();
        if(exact.size() > 1) {
            ambiguousClass(draft, source, location, raw, exact);
            return "";
        }
        String normalized = normalize(token);
        List<MetaClass> normalizedMatches = draft.metamodel.classes().stream()
                .filter(value -> normalize(value.id()).equals(normalized) || normalize(value.name()).equals(normalized)).toList();
        if(normalizedMatches.size() == 1) {
            MetaClass match = normalizedMatches.get(0);
            String fixId = stableFixId("class-normalize", source, location, token);
            draft.fix(fixId, "NORMALIZE_CLASS_REFERENCE", source, location,
                    "Use the uniquely matching metamodel class",
                    "Replace the external class reference '" + raw + "' with '" + match.name() + "' (" + match.id() + ").",
                    "SAFE", "EXACT", true, true);
            draft.diag("INFO", "NORMALIZED_CLASS_REFERENCE", source, location, raw,
                    "The class reference was normalized to '" + match.name() + "'.",
                    "The imported spelling differs only in case, separators or qualification from one existing metamodel class.",
                    "No action is required; HYDRA applied the deterministic correction.", List.of(fixId), "EXACT");
            return match.id();
        }
        if(normalizedMatches.size() > 1) {
            ambiguousClass(draft, source, location, raw, normalizedMatches);
            return "";
        }
        suggestClass(draft, source, location, raw, token);
        return "";
    }

    /** Descripción: Obtiene la información correspondiente a «resolveRelation» dentro de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; required: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaRelation resolveRelation(VisualDefinitionImportDraft draft, String raw, String source, String location, boolean required) {
        draft.referenceChecked();
        String token = fragment(raw);
        if(token.isBlank()) {
            if(required) missingRelation(draft, source, location, raw);
            return null;
        }
        List<MetaRelation> exact = draft.metamodel.relations().stream().filter(value -> relationExact(value, token)).toList();
        if(exact.size() == 1) return exact.get(0);
        if(exact.size() > 1) {
            ambiguousRelation(draft, source, location, raw, exact);
            return null;
        }
        String normalized = normalize(token);
        List<MetaRelation> normalizedMatches = draft.metamodel.relations().stream()
                .filter(value -> normalize(value.id()).equals(normalized) || normalize(value.name()).equals(normalized)).toList();
        if(normalizedMatches.size() == 1) {
            MetaRelation match = normalizedMatches.get(0);
            String fixId = stableFixId("relation-normalize", source, location, token);
            draft.fix(fixId, "NORMALIZE_RELATION_REFERENCE", source, location,
                    "Use the uniquely matching metamodel relation",
                    "Replace the external relation reference '" + raw + "' with '" + match.name() + "' (" + match.id() + ").",
                    "SAFE", "EXACT", true, true);
            draft.diag("INFO", "NORMALIZED_RELATION_REFERENCE", source, location, raw,
                    "The relation reference was normalized to '" + match.name() + "'.",
                    "The imported spelling differs only in case, separators or qualification from one existing metamodel relation.",
                    "No action is required; HYDRA applied the deterministic correction.", List.of(fixId), "EXACT");
            return match;
        }
        if(normalizedMatches.size() > 1) {
            ambiguousRelation(draft, source, location, raw, normalizedMatches);
            return null;
        }
        suggestRelation(draft, source, location, raw, token);
        return null;
    }

    /** Descripción: Ejecuta la operación «inferRelationByEndpoints» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; sourceClassId: identificador que delimita el recurso objetivo; targetClassId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaRelation inferRelationByEndpoints(VisualDefinitionImportDraft draft, String sourceClassId, String targetClassId,
            String source, String location) {
        draft.referenceChecked();
        if(sourceClassId == null || sourceClassId.isBlank() || targetClassId == null || targetClassId.isBlank()) return null;
        List<MetaRelation> matches = draft.metamodel.relations().stream()
                .filter(value -> value.sourceTypeId().equals(sourceClassId) && value.targetTypeId().equals(targetClassId)).toList();
        if(matches.size() == 1) {
            MetaRelation relation = matches.get(0);
            String fixId = stableFixId("relation-endpoints", source, location, relation.id());
            draft.fix(fixId, "INFER_RELATION_FROM_ENDPOINTS", source, location,
                    "Use the only relation compatible with both endpoints",
                    "Map this connector to '" + relation.name() + "' because it is the only metamodel relation from "
                            + className(draft, sourceClassId) + " to " + className(draft, targetClassId) + ".",
                    "SAFE", "HIGH", true, true);
            draft.diag("INFO", "RELATION_INFERRED_FROM_ENDPOINTS", source, location, relation.name(),
                    "The connector was mapped to '" + relation.name() + "'.",
                    "No relation identifier was present, but exactly one metamodel relation matches the resolved source and target classes.",
                    "No action is required; review the imported edge if the external diagram used a purely visual connector.", List.of(fixId), "HIGH");
            return relation;
        }
        if(matches.size() > 1) {
            draft.ambiguous();
            List<String> names = matches.stream().map(value -> value.name() + " (" + value.id() + ")").toList();
            String fixId = stableFixId("relation-endpoints-review", source, location, String.join("-", names));
            draft.fix(fixId, "CHOOSE_RELATION_FOR_ENDPOINTS", source, location, "Choose the connector meaning",
                    "More than one metamodel relation matches these endpoints: " + String.join(", ", names) + ".",
                    "REVIEW", "MEDIUM", false, false);
            draft.diag("WARNING", "AMBIGUOUS_RELATION_ENDPOINTS", source, location, "", "The connector meaning is ambiguous.",
                    "Several metamodel relations connect the same resolved source and target classes.",
                    "Choose the intended relation before relying on this connector semantically.", List.of(fixId), "MEDIUM");
        }
        return null;
    }

    /** Descripción: Ejecuta la operación «inspectExpression» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; expression: dato de entrada necesario para la operación; contextClassId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void inspectExpression(VisualDefinitionImportDraft draft, String source, String location, String expression, String contextClassId) {
        if(expression == null || expression.isBlank()) return;
        draft.elementInspected();
        Set<String> allowed = new LinkedHashSet<>(BUILTIN_FEATURES);
        if(contextClassId != null && !contextClassId.isBlank()) {
            draft.metamodel.classes().stream().filter(value -> value.id().equals(contextClassId)).findFirst().ifPresent(value -> {
                value.attributes().forEach(attribute -> allowed.add(attribute.name()));
                value.operations().forEach(operation -> allowed.add(operation.name()));
            });
            draft.metamodel.relations().stream().filter(value -> value.sourceTypeId().equals(contextClassId))
                    .forEach(value -> allowed.add(value.name()));
        } else {
            draft.metamodel.classes().forEach(value -> {
                value.attributes().forEach(attribute -> allowed.add(attribute.name()));
                value.operations().forEach(operation -> allowed.add(operation.name()));
            });
            draft.metamodel.relations().forEach(value -> allowed.add(value.name()));
        }
        Matcher matcher = FEATURE_ACCESS.matcher(expression);
        Set<String> reported = new HashSet<>();
        while(matcher.find()) {
            String feature = matcher.group(1);
            if(allowed.contains(feature) || !reported.add(feature)) continue;
            draft.unresolved();
            String fixId = stableFixId("expression-feature", source, location, feature);
            List<String> suggestions = nearestNames(feature, allowed, 3);
            String proposal = suggestions.isEmpty() ? "" : " Closest known features: " + String.join(", ", suggestions) + ".";
            draft.fix(fixId, "REVIEW_EXPRESSION_FEATURE", source, location, "Review undefined expression feature '" + feature + "'",
                    "The expression accesses '" + feature + "', which is not defined in the resolved metamodel context." + proposal,
                    "REVIEW", suggestions.isEmpty() ? "LOW" : "MEDIUM", false, false);
            draft.diag("WARNING", "UNDEFINED_EXPRESSION_FEATURE", source, location, feature,
                    "Expression feature '" + feature + "' is not defined in the associated metamodel context.",
                    "HYDRA only performs static inspection; the expression is never executed during import.",
                    "Map the expression to an existing attribute/relation/operation or define the missing metamodel feature intentionally.",
                    List.of(fixId), suggestions.isEmpty() ? "LOW" : "MEDIUM");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateRecoveredDraft» y rechaza entradas no válidas. Entradas: draft: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void validateRecoveredDraft(VisualDefinitionImportDraft draft) {
        Set<String> classIds = new HashSet<>();
        draft.metamodel.classes().forEach(value -> classIds.add(value.id()));
        Set<String> relationIds = new HashSet<>();
        draft.metamodel.relations().forEach(value -> relationIds.add(value.id()));
        Set<String> seen = new HashSet<>();
        for(RepresentationDefinition representation : draft.representations) {
            draft.elementInspected();
            checkDuplicate(draft, seen, representation.id(), "representation", representation.id());
            for(String root : representation.rootClassIds()) if(!classIds.contains(root)) {
                draft.structuralProblem(); draft.unresolved();
                draft.diag("ERROR", "UNKNOWN_ROOT_CLASS", "recovered editor", representation.id(), root,
                        "A recovered representation references a root class that does not exist.",
                        "The imported representation cannot establish its semantic root against the selected metamodel.",
                        "Choose an existing root class or correct the source definition.", List.of(), "EXACT");
            }
        }
        for(NodeMapping mapping : draft.nodeMappings) {
            draft.elementInspected();
            checkDuplicate(draft, seen, mapping.id(), "node mapping", mapping.representationName());
            if(!classIds.contains(mapping.targetClassId())) {
                draft.structuralProblem(); draft.unresolved();
                draft.diag("ERROR", "UNKNOWN_NODE_TARGET", "recovered editor", mapping.id(), mapping.targetClassId(),
                        "A recovered node mapping targets a class that does not exist.", "The selected metamodel has no matching class id.",
                        "Remap the node to an existing class; do not create a metamodel class automatically unless it is intentional.", List.of(), "EXACT");
            }
            if(mapping.defaultWidth() <= 0 || mapping.defaultHeight() <= 0) { draft.invalidParameter(); draft.structuralProblem(); }
        }
        for(EdgeMapping mapping : draft.edgeMappings) {
            draft.elementInspected();
            checkDuplicate(draft, seen, mapping.id(), "edge mapping", mapping.semanticObjectBacked() ? mapping.semanticEdge().edgeClassId() : mapping.relationId());
            if(mapping.semanticObjectBacked()) {
                validateSemanticEdgeMapping(draft, mapping, classIds, relationIds);
                continue;
            }
            MetaRelation relation = draft.metamodel.relations().stream().filter(value -> value.id().equals(mapping.relationId())).findFirst().orElse(null);
            if(relation == null || !relationIds.contains(mapping.relationId())) {
                draft.structuralProblem(); draft.unresolved();
                draft.diag("ERROR", "UNKNOWN_EDGE_RELATION", "recovered editor", mapping.id(), mapping.relationId(),
                        "A recovered edge mapping targets a relation that does not exist.", "The selected metamodel has no matching relation id.",
                        "Remap the edge to an existing relation.", List.of(), "EXACT");
            } else if(!relation.sourceTypeId().equals(mapping.sourceClassId()) || !relation.targetTypeId().equals(mapping.targetClassId())) {
                draft.structuralProblem();
                String fixId = stableFixId("edge-endpoints", mapping.id(), relation.id(), "repair");
                draft.fix(fixId, "REPAIR_EDGE_ENDPOINT_TYPES", "recovered editor", mapping.id(),
                        "Use the relation endpoint types from the metamodel",
                        "The relation itself is authoritative for its source and target class ids.", "SAFE", "EXACT", true, true);
                draft.diag("WARNING", "EDGE_ENDPOINT_MISMATCH", "recovered editor", mapping.id(), relation.id(),
                        "The external edge endpoint types disagree with the metamodel relation.",
                        "HYDRA keeps the selected metamodel authoritative during import.",
                        "The recovered edge uses the metamodel relation endpoints; review the external source if this changes its intended meaning.",
                        List.of(fixId), "EXACT");
            }
        }
        Set<String> toolIds = new HashSet<>();
        draft.tools.forEach(tool -> { draft.elementInspected(); checkDuplicate(draft, seen, tool.id(), "tool", tool.id()); toolIds.add(tool.id()); });
        draft.paletteGroups.forEach(group -> {
            draft.elementInspected(); checkDuplicate(draft, seen, group.id(), "palette group", group.name());
            group.toolIds().stream().filter(id -> !toolIds.contains(id)).forEach(id -> {
                draft.structuralProblem(); draft.unresolved();
                draft.diag("ERROR", "UNKNOWN_PALETTE_TOOL", "recovered editor", group.id(), id,
                        "A palette group references a tool that was not recovered.", "The external palette contains a dangling tool reference.",
                        "Remove the dangling reference or recover/map the missing tool.", List.of(), "EXACT");
            });
        });
        validateMetamodelRelations(draft, classIds);
    }

    /** Valida el patrón de arista semántica como objeto (Connection.source/target) sin inventar relaciones directas. */
    /** Descripción: Valida las condiciones necesarias para «validateSemanticEdgeMapping» y rechaza entradas no válidas. Entradas: draft: dato de entrada necesario para la operación; mapping: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación; relationIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateSemanticEdgeMapping(VisualDefinitionImportDraft draft, EdgeMapping mapping, Set<String> classIds, Set<String> relationIds) {
        var semantic = mapping.semanticEdge();
        if(!classIds.contains(semantic.edgeClassId())) {
            draft.structuralProblem(); draft.unresolved();
            draft.diag("ERROR","UNKNOWN_SEMANTIC_EDGE_CLASS","recovered editor",mapping.id(),semantic.edgeClassId(),
                    "The semantic edge object class does not exist in the selected metamodel.",
                    "The external notation models edges as semantic objects, but their domain class could not be mapped.",
                    "Map the edge domain class to an existing class; do not flatten it into an unrelated reference.",List.of(),"EXACT");
            return;
        }
        MetaRelation source = draft.metamodel.relations().stream().filter(value -> value.id().equals(semantic.sourceRelationId())).findFirst().orElse(null);
        MetaRelation target = draft.metamodel.relations().stream().filter(value -> value.id().equals(semantic.targetRelationId())).findFirst().orElse(null);
        if(source == null || !relationIds.contains(semantic.sourceRelationId()) || target == null || !relationIds.contains(semantic.targetRelationId())) {
            draft.structuralProblem(); draft.unresolved();
            draft.diag("ERROR","SEMANTIC_EDGE_ENDPOINT_RELATION_MISSING","recovered editor",mapping.id(),semantic.edgeClassId(),
                    "The semantic edge source/target references could not be mapped completely.",
                    "A semantic edge object requires two existing references from the edge object to its visual endpoints.",
                    "Map both endpoint references before materializing the edge.",List.of(),"EXACT");
            return;
        }
        boolean ownerMismatch = !source.sourceTypeId().equals(semantic.edgeClassId()) || !target.sourceTypeId().equals(semantic.edgeClassId());
        boolean endpointMismatch = !source.targetTypeId().equals(mapping.sourceClassId()) || !target.targetTypeId().equals(mapping.targetClassId());
        if(ownerMismatch || endpointMismatch) {
            draft.structuralProblem();
            draft.diag("ERROR","SEMANTIC_EDGE_ENDPOINT_MISMATCH","recovered editor",mapping.id(),semantic.edgeClassId(),
                    "The semantic edge object or one of its endpoint types disagrees with the metamodel.",
                    "HYDRA keeps the canonical metamodel authoritative instead of changing the semantic meaning of the imported edge.",
                    "Review the edge domain class and source/target finder expressions.",List.of(),"EXACT");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validateMetamodelRelations» y rechaza entradas no válidas. Entradas: draft: dato de entrada necesario para la operación; classIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateMetamodelRelations(VisualDefinitionImportDraft draft, Set<String> classIds) {
        for(MetaRelation relation : draft.metamodel.relations()) {
            if(!classIds.contains(relation.sourceTypeId()) || !classIds.contains(relation.targetTypeId())) {
                draft.structuralProblem();
                draft.diag("ERROR", "METAMODEL_RELATION_ENDPOINT_MISSING", draft.metamodel.id(), relation.id(), relation.name(),
                        "The selected metamodel itself contains a relation with a missing endpoint class.",
                        "Import cannot safely infer visual semantics from an inconsistent metamodel.",
                        "Repair the metamodel relation before accepting inferred visual mappings.", List.of(), "EXACT");
            }
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkDuplicate» y rechaza entradas no válidas. Entradas: draft: dato de entrada necesario para la operación; seen: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación; element: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkDuplicate(VisualDefinitionImportDraft draft, Set<String> seen, String id, String kind, String element) {
        if(seen.add(id)) return;
        draft.structuralProblem();
        String fixId = stableFixId("duplicate-id", kind, id, element);
        draft.fix(fixId, "REGENERATE_DUPLICATE_IMPORT_ID", "recovered editor", id, "Generate a unique imported identifier",
                "Two recovered " + kind + " elements use the same internal id. HYDRA should regenerate the later imported id deterministically.",
                "SAFE", "EXACT", true, false);
        draft.diag("ERROR", "DUPLICATE_IMPORTED_ID", "recovered editor", id, element,
                "Duplicate imported identifier '" + id + "'.", "Stable unique ids are required by the editor-definition contract.",
                "Apply the safe identifier regeneration before persisting the imported editor.", List.of(fixId), "EXACT");
    }

    /** Descripción: Ejecuta la operación «missingClass» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void missingClass(VisualDefinitionImportDraft draft, String source, String location, String raw) {
        draft.unresolved(); draft.invalidParameter();
        String fixId = stableFixId("missing-class", source, location, raw);
        draft.fix(fixId, "SELECT_MISSING_CLASS", source, location, "Select the semantic class",
                "The imported visual element does not define a class. Choose an existing metamodel class; HYDRA will not create one automatically.",
                "MANUAL", "UNKNOWN", false, false);
        draft.diag("ERROR", "MISSING_METAMODEL_CLASS", source, location, raw,
                "The visual element does not define a semantic metamodel class.",
                "A visual node must map to a class from the selected metamodel.",
                "Choose an existing class or intentionally add the missing concept to the metamodel first.", List.of(fixId), "UNKNOWN");
    }

    /** Descripción: Ejecuta la operación «suggestClass» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void suggestClass(VisualDefinitionImportDraft draft, String source, String location, String raw, String token) {
        draft.unresolved();
        List<MetaClass> nearest = nearestClasses(draft, token, 3);
        if(!nearest.isEmpty() && closeEnough(token, nearest.get(0).name())) {
            String fixId = stableFixId("class-review", source, location, token);
            String names = joinClasses(nearest);
            draft.fix(fixId, "REVIEW_CLASS_MAPPING", source, location, "Review the closest metamodel class",
                    "No exact class exists for '" + raw + "'. Possible matches: " + names + ".", "REVIEW", "MEDIUM", false, false);
            draft.diag("ERROR", "UNKNOWN_METAMODEL_CLASS", source, location, raw,
                    "The referenced class '" + raw + "' does not exist in the selected metamodel.",
                    "A similar class name was found, but HYDRA will not silently change semantic meaning.",
                    "Review the proposed class mapping or correct the source/metamodel.", List.of(fixId), "MEDIUM");
        } else {
            String fixId = stableFixId("class-manual", source, location, token);
            draft.fix(fixId, "RESOLVE_UNKNOWN_CLASS", source, location, "Resolve the missing metamodel class",
                    "No reliable class candidate was found for '" + raw + "'.", "MANUAL", "LOW", false, false);
            draft.diag("ERROR", "UNKNOWN_METAMODEL_CLASS", source, location, raw,
                    "The referenced class '" + raw + "' does not exist in the selected metamodel.",
                    "No sufficiently close class candidate was found.",
                    "Map it manually to an existing class or intentionally define the missing class in the metamodel.", List.of(fixId), "LOW");
        }
    }

    /** Descripción: Ejecuta la operación «ambiguousClass» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; matches: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ambiguousClass(VisualDefinitionImportDraft draft, String source, String location, String raw, List<MetaClass> matches) {
        draft.unresolved(); draft.ambiguous();
        String fixId = stableFixId("class-ambiguous", source, location, raw);
        draft.fix(fixId, "CHOOSE_AMBIGUOUS_CLASS", source, location, "Choose the intended metamodel class",
                "The reference matches more than one class: " + joinClasses(matches) + ".", "REVIEW", "MEDIUM", false, false);
        draft.diag("ERROR", "AMBIGUOUS_METAMODEL_CLASS", source, location, raw, "The class reference is ambiguous.",
                "Several metamodel classes match the imported identifier after normalization.",
                "Choose the intended class explicitly.", List.of(fixId), "MEDIUM");
    }

    /** Descripción: Ejecuta la operación «missingRelation» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void missingRelation(VisualDefinitionImportDraft draft, String source, String location, String raw) {
        draft.unresolved(); draft.invalidParameter();
        String fixId = stableFixId("missing-relation", source, location, raw);
        draft.fix(fixId, "SELECT_MISSING_RELATION", source, location, "Select the semantic relation",
                "The imported connector does not define a relation. Choose an existing relation; HYDRA will not create one automatically.",
                "MANUAL", "UNKNOWN", false, false);
        draft.diag("ERROR", "MISSING_METAMODEL_RELATION", source, location, raw,
                "The visual connector does not define a semantic metamodel relation.",
                "A semantic edge mapping must point to a relation from the selected metamodel.",
                "Choose an existing relation, or keep the connector only as preserved visual evidence.", List.of(fixId), "UNKNOWN");
    }

    /** Descripción: Ejecuta la operación «suggestRelation» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void suggestRelation(VisualDefinitionImportDraft draft, String source, String location, String raw, String token) {
        draft.unresolved();
        List<MetaRelation> nearest = nearestRelations(draft, token, 3);
        String fixId = stableFixId("relation-review", source, location, token);
        if(!nearest.isEmpty() && closeEnough(token, nearest.get(0).name())) {
            draft.fix(fixId, "REVIEW_RELATION_MAPPING", source, location, "Review the closest metamodel relation",
                    "No exact relation exists for '" + raw + "'. Possible matches: " + joinRelations(nearest) + ".",
                    "REVIEW", "MEDIUM", false, false);
            draft.diag("ERROR", "UNKNOWN_METAMODEL_RELATION", source, location, raw,
                    "The referenced relation '" + raw + "' does not exist in the selected metamodel.",
                    "A similar relation was found, but HYDRA will not silently substitute it.",
                    "Review the proposed mapping or correct the source/metamodel.", List.of(fixId), "MEDIUM");
        } else {
            draft.fix(fixId, "RESOLVE_UNKNOWN_RELATION", source, location, "Resolve the missing metamodel relation",
                    "No reliable relation candidate was found for '" + raw + "'.", "MANUAL", "LOW", false, false);
            draft.diag("ERROR", "UNKNOWN_METAMODEL_RELATION", source, location, raw,
                    "The referenced relation '" + raw + "' does not exist in the selected metamodel.",
                    "No sufficiently close relation candidate was found.",
                    "Map it manually to an existing relation or intentionally define the missing relation in the metamodel.", List.of(fixId), "LOW");
        }
    }

    /** Descripción: Ejecuta la operación «ambiguousRelation» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación; matches: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ambiguousRelation(VisualDefinitionImportDraft draft, String source, String location, String raw, List<MetaRelation> matches) {
        draft.unresolved(); draft.ambiguous();
        String fixId = stableFixId("relation-ambiguous", source, location, raw);
        draft.fix(fixId, "CHOOSE_AMBIGUOUS_RELATION", source, location, "Choose the intended metamodel relation",
                "The reference matches more than one relation: " + joinRelations(matches) + ".", "REVIEW", "MEDIUM", false, false);
        draft.diag("ERROR", "AMBIGUOUS_METAMODEL_RELATION", source, location, raw, "The relation reference is ambiguous.",
                "Several metamodel relations match the imported identifier after normalization.",
                "Choose the intended relation explicitly.", List.of(fixId), "MEDIUM");
    }

    /** Descripción: Ejecuta la operación «nearestClasses» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación; limit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaClass> nearestClasses(VisualDefinitionImportDraft draft, String token, int limit) {
        return draft.metamodel.classes().stream().sorted(Comparator.comparingInt(value -> distance(normalize(token), normalize(value.name())))).limit(limit).toList();
    }
    /** Descripción: Ejecuta la operación «nearestRelations» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; token: dato de entrada necesario para la operación; limit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaRelation> nearestRelations(VisualDefinitionImportDraft draft, String token, int limit) {
        return draft.metamodel.relations().stream().sorted(Comparator.comparingInt(value -> distance(normalize(token), normalize(value.name())))).limit(limit).toList();
    }
    /** Descripción: Ejecuta la operación «nearestNames» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: token: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación; limit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> nearestNames(String token, Set<String> names, int limit) {
        return names.stream().filter(value -> !BUILTIN_FEATURES.contains(value)).sorted(Comparator.comparingInt(value -> distance(normalize(token), normalize(value)))).limit(limit).toList();
    }
    /** Descripción: Ejecuta la operación «classExact» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: value: valor de entrada que debe procesarse; token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean classExact(MetaClass value, String token) { return value.id().equals(token) || value.name().equals(token) || value.id().endsWith("." + token) || value.id().endsWith(":" + token); }
    /** Descripción: Ejecuta la operación «relationExact» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: value: valor de entrada que debe procesarse; token: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean relationExact(MetaRelation value, String token) { return value.id().equals(token) || value.name().equals(token) || value.id().endsWith("." + token) || value.id().endsWith(":" + token); }
    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: draft: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String className(VisualDefinitionImportDraft draft, String id) { return draft.metamodel.classes().stream().filter(value -> value.id().equals(id)).map(MetaClass::name).findFirst().orElse(id); }
    /** Descripción: Ejecuta la operación «joinClasses» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String joinClasses(List<MetaClass> values) { return String.join(", ", values.stream().map(value -> value.name() + " (" + value.id() + ")").toList()); }
    /** Descripción: Ejecuta la operación «joinRelations» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String joinRelations(List<MetaRelation> values) { return String.join(", ", values.stream().map(value -> value.name() + " (" + value.id() + ")").toList()); }
    /** Descripción: Elimina o libera el recurso gestionado por «closeEnough» de forma controlada. Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean closeEnough(String left, String right) { String a=normalize(left), b=normalize(right); int threshold=Math.max(1,Math.min(3,Math.max(a.length(),b.length())/4)); return distance(a,b)<=threshold; }
    /** Descripción: Ejecuta la operación «fragment» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String fragment(String value) { if(value==null)return "";String s=value.trim();if(s.contains("#//"))s=s.substring(s.lastIndexOf("#//")+3);Matcher m=TOKEN.matcher(s);String last="";while(m.find())last=m.group(1);return last; }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    /** Descripción: Ejecuta la operación «stableFixId» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: parts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String stableFixId(String... parts) { String value=String.join("-",parts).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-+|-+$",""); return value.length()>120?value.substring(0,120):value; }
    /** Descripción: Ejecuta la operación «distance» correspondiente a la responsabilidad de VisualDefinitionImportAnalyzer. Entradas: a: dato de entrada necesario para la operación; b: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int distance(String a, String b) { int[] previous=new int[b.length()+1];for(int j=0;j<=b.length();j++)previous[j]=j;for(int i=1;i<=a.length();i++){int[] current=new int[b.length()+1];current[0]=i;for(int j=1;j<=b.length();j++){int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+cost);}previous=current;}return previous[b.length()]; }
}
