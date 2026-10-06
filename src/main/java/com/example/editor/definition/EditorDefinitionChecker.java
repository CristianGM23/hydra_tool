package com.example.editor.definition;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionSemanticFingerprint;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Comprueba las referencias de representaciones, paleta y formularios. */
@Component
public class EditorDefinitionChecker {
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: definition: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCheckReport check(EditorDefinitionModel definition, MetaModel metamodel, ExpressionModel expressions, ActionModel actions) {
        Set<EditorDefinitionDiagnostic> diagnostics = new LinkedHashSet<>();
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        Map<String, ExpressionDefinition> expressionsById = expressions == null ? Map.of() : expressions.expressions().stream().collect(java.util.stream.Collectors.toMap(ExpressionDefinition::id, value -> value, (left, right) -> left, java.util.LinkedHashMap::new));
        Set<String> expressionIds = expressionsById.keySet();
        Set<String> actionIds = actions == null ? Set.of() : actions.actions().stream().map(value -> value.id()).collect(java.util.stream.Collectors.toSet());
        Set<String> representationIds = new HashSet<>();
        Set<String> nodeMappingIds = new HashSet<>();
        Set<String> edgeMappingIds = new HashSet<>();
        if(definition.representations().isEmpty()) diagnostics.add(error("NO_REPRESENTATION", definition.id(), "representations", "An editor must define at least one representation"));
        for(RepresentationDefinition representation : definition.representations()) {
            if(!representationIds.add(representation.id())) diagnostics.add(error("DUPLICATE_REPRESENTATION", representation.id(), "representations", "Duplicate representation id"));
            for(String rootClassId : representation.rootClassIds()) if(!index.hasClass(rootClassId)) diagnostics.add(error("UNKNOWN_ROOT_CLASS", representation.id(), "rootClassIds", "Root class was not found: " + rootClassId));
            for(NodeMapping mapping : representation.nodeMappings()) {
                if(!nodeMappingIds.add(mapping.id())) diagnostics.add(error("DUPLICATE_NODE_MAPPING", mapping.id(), "nodeMappings", "Duplicate node mapping id"));
                if(!index.hasClass(mapping.targetClassId())) diagnostics.add(error("UNKNOWN_NODE_CLASS", mapping.id(), "targetClassId", "Mapped class was not found"));
                if(mapping.imageBacked()) {
                    if(mapping.imageAssetPath().isBlank()) diagnostics.add(error("IMAGE_ASSET_REQUIRED", mapping.id(), "imageAssetPath", "Image node representations require a project asset path"));
                }
                checkNodeStyles(mapping, diagnostics);
                checkConditionalStyles(mapping.id(), mapping.conditionalStyles(), expressionsById, expressionIds, diagnostics, true);
                if(mapping.container()) {
                    if(mapping.containmentRelationIds().isEmpty()) diagnostics.add(error("CONTAINER_RELATION_REQUIRED", mapping.id(), "containmentRelationIds", "Container mappings require at least one containment relation"));
                    for(String relationId : mapping.containmentRelationIds()) {
                        var containment = index.findRelation(relationId).orElse(null);
                        if(containment == null) diagnostics.add(error("UNKNOWN_CONTAINER_RELATION", mapping.id(), "containmentRelationIds", "Containment relation was not found: " + relationId));
                        else if(containment.kind() != MetaRelationKind.CONTAINMENT) diagnostics.add(error("NON_CONTAINMENT_CONTAINER_RELATION", mapping.id(), "containmentRelationIds", "Container mappings only accept containment relations"));
                        else if(!index.isSameOrSubtype(mapping.targetClassId(), containment.sourceTypeId())) diagnostics.add(error("CONTAINER_SOURCE_MISMATCH", mapping.id(), "containmentRelationIds", "Containment source is incompatible with the mapped class"));
                    }
                }
                checkExpression(mapping.labelExpressionId(), mapping.id(), "labelExpressionId", expressionIds, diagnostics);
            }
            for(EdgeMapping mapping : representation.edgeMappings()) {
                if(!edgeMappingIds.add(mapping.id())) diagnostics.add(error("DUPLICATE_EDGE_MAPPING", mapping.id(), "edgeMappings", "Duplicate edge mapping id"));
                if(mapping.semanticObjectBacked()) checkSemanticEdge(mapping, index, diagnostics);
                else {
                    var relation = index.findRelation(mapping.relationId()).orElse(null);
                    if(relation == null) diagnostics.add(error("UNKNOWN_EDGE_RELATION", mapping.id(), "relationId", "Mapped relation was not found"));
                    else if(!relation.sourceTypeId().equals(mapping.sourceClassId()) || !relation.targetTypeId().equals(mapping.targetClassId())) diagnostics.add(error("EDGE_ENDPOINT_MISMATCH", mapping.id(), "relationId", "Edge endpoints do not match the canonical relation"));
                }
                checkEdgeStyles(mapping, diagnostics);
                checkConditionalStyles(mapping.id(), mapping.conditionalStyles(), expressionsById, expressionIds, diagnostics, false);
                checkExpression(mapping.labelExpressionId(), mapping.id(), "labelExpressionId", expressionIds, diagnostics);
            }
            Set<String> localNodeMappings = representation.nodeMappings().stream().map(NodeMapping::id).collect(java.util.stream.Collectors.toSet());
            Set<String> localEdgeMappings = representation.edgeMappings().stream().map(EdgeMapping::id).collect(java.util.stream.Collectors.toSet());
            Set<String> localMappingIds = new HashSet<>(localNodeMappings); localMappingIds.addAll(localEdgeMappings);
            Set<String> layerIds = new HashSet<>(); int defaultLayers = 0;
            for(RepresentationLayerDefinition layer : representation.layers()) {
                if(!layerIds.add(layer.id())) diagnostics.add(error("DUPLICATE_REPRESENTATION_LAYER", layer.id(), "layers", "Duplicate representation layer id"));
                if(layer.defaultLayer()) defaultLayers++;
                for(String id : layer.nodeMappingIds()) if(!localNodeMappings.contains(id)) diagnostics.add(error("UNKNOWN_LAYER_NODE_MAPPING", layer.id(), "nodeMappingIds", "Layer references a node mapping outside its representation: " + id));
                for(String id : layer.edgeMappingIds()) if(!localEdgeMappings.contains(id)) diagnostics.add(error("UNKNOWN_LAYER_EDGE_MAPPING", layer.id(), "edgeMappingIds", "Layer references an edge mapping outside its representation: " + id));
            }
            if(defaultLayers > 1) diagnostics.add(error("MULTIPLE_DEFAULT_REPRESENTATION_LAYERS", representation.id(), "layers", "A representation can define at most one default layer"));
            Set<String> filterIds = new HashSet<>();
            for(RepresentationFilterDefinition filter : representation.filters()) {
                if(!filterIds.add(filter.id())) diagnostics.add(error("DUPLICATE_REPRESENTATION_FILTER", filter.id(), "filters", "Duplicate representation filter id"));
                for(String id : filter.mappingIds()) if(!localMappingIds.contains(id)) diagnostics.add(error("UNKNOWN_FILTER_MAPPING", filter.id(), "mappingIds", "Filter references a mapping outside its representation: " + id));
                checkExpression(filter.conditionExpressionId(), filter.id(), "conditionExpressionId", expressionIds, diagnostics);
            }
        }
        Set<String> formIds = new HashSet<>();
        for(FormDefinition form : definition.forms()) {
            if(!formIds.add(form.id())) diagnostics.add(error("DUPLICATE_FORM", form.id(), "forms", "Duplicate form id"));
            if(!index.hasClass(form.targetClassId())) diagnostics.add(error("UNKNOWN_FORM_CLASS", form.id(), "targetClassId", "Form target class was not found"));
            Set<String> fields = new HashSet<>();
            for(FormSectionDefinition section : form.sections()) for(FormFieldDefinition field : section.fields()) {
                if(!fields.add(field.id())) diagnostics.add(error("DUPLICATE_FORM_FIELD", form.id(), "sections", "Duplicate field id: " + field.id()));
                if(!index.featureBelongsToClass(form.targetClassId(), field.featureId())) diagnostics.add(error("UNKNOWN_FORM_FEATURE", field.id(), "featureId", "Field feature is not available on the target class"));
                checkExpression(field.visibleExpressionId(), field.id(), "visibleExpressionId", expressionIds, diagnostics);
                checkExpression(field.enabledExpressionId(), field.id(), "enabledExpressionId", expressionIds, diagnostics);
            }
        }
        Set<String> toolIds = new HashSet<>();
        for(EditorTool tool : definition.palette().tools()) {
            if(!toolIds.add(tool.id())) diagnostics.add(error("DUPLICATE_TOOL", tool.id(), "palette.tools", "Duplicate tool id"));
            for(String representationId : tool.representationIds()) if(!representationIds.contains(representationId)) diagnostics.add(error("UNKNOWN_TOOL_REPRESENTATION", tool.id(), "representationIds", "Tool references an unknown representation"));
            checkExpression(tool.visibleExpressionId(), tool.id(), "visibleExpressionId", expressionIds, diagnostics);
            checkExpression(tool.enabledExpressionId(), tool.id(), "enabledExpressionId", expressionIds, diagnostics);
            switch(tool) {
                case NodeCreationTool node -> {
                    var targetClass = index.findClass(node.targetClassId()).orElse(null);
                    if(targetClass == null) diagnostics.add(error("UNKNOWN_TOOL_CLASS", tool.id(), "targetClassId", "Creation class was not found"));
                    else if(targetClass.kind() != MetaClassKind.CLASS) diagnostics.add(error("NON_INSTANTIABLE_TOOL_CLASS", tool.id(), "targetClassId", "Creation tools require a concrete class"));
                    if(!node.formId().isBlank() && !formIds.contains(node.formId())) diagnostics.add(error("UNKNOWN_TOOL_FORM", tool.id(), "formId", "Creation form was not found"));
                }
                case EdgeCreationTool edge -> {
                    if(!edgeMappingIds.contains(edge.edgeMappingId())) diagnostics.add(error("UNKNOWN_TOOL_EDGE_MAPPING", tool.id(), "edgeMappingId", "Edge mapping was not found"));
                    if(edge.semanticObjectBacked()) checkSemanticEdgeTool(edge, index, diagnostics);
                    else if(!index.hasRelation(edge.relationId())) diagnostics.add(error("UNKNOWN_TOOL_RELATION", tool.id(), "relationId", "Relation was not found"));
                }
                case OperationEditorTool operation -> { if(!index.hasOperation(operation.operationId())) diagnostics.add(error("UNKNOWN_TOOL_OPERATION", tool.id(), "operationId", "Operation was not found")); }
                case ActionEditorTool action -> { if(!actionIds.contains(action.actionId())) diagnostics.add(error("UNKNOWN_TOOL_ACTION", tool.id(), "actionId", "Action was not found")); }
                case ValidateEditorTool ignored -> { }
            }
        }
        for(PaletteGroup group : definition.palette().groups()) for(String toolId : group.toolIds()) if(!toolIds.contains(toolId)) diagnostics.add(error("UNKNOWN_GROUP_TOOL", group.id(), "toolIds", "Palette group references an unknown tool"));
        for(RepresentationDefinition representation : definition.representations()) for(String toolId : representation.toolIds()) if(!toolIds.contains(toolId)) diagnostics.add(error("UNKNOWN_REPRESENTATION_TOOL", representation.id(), "toolIds", "Representation references an unknown tool"));
        if(definition.runtimePolicy().validationTriggers().stream().anyMatch(trigger -> trigger != EditorValidationTrigger.MANUAL)
                && !definition.capabilityManifest().supports(EditorCapability.VALIDATION_RUNTIME))
            diagnostics.add(error("AUTOMATIC_VALIDATION_CAPABILITY_REQUIRED", definition.id(), "runtimePolicy.validationTriggers", "Automatic validation requires VALIDATION_RUNTIME"));
        if(definition.runtimePolicy().eventFlowsEnabled() && !definition.capabilityManifest().supports(EditorCapability.EVENT_FLOWS))
            diagnostics.add(error("EVENT_FLOW_CAPABILITY_REQUIRED", definition.id(), "runtimePolicy.eventFlowsEnabled", "EventFlow runtime requires EVENT_FLOWS"));
        if(definition.runtimePolicy().gamificationEnabled() && !definition.capabilityManifest().supports(EditorCapability.GAMIFICATION))
            diagnostics.add(error("GAMIFICATION_CAPABILITY_REQUIRED", definition.id(), "runtimePolicy.gamificationEnabled", "Gamification runtime requires GAMIFICATION"));
        CodeGenerationDefinition codeGeneration = definition.codeGeneration();
        Set<String> supportedGenerationTargets = new LinkedHashSet<>(CodeGenerationDefinition.defaultTargetIds());
        if(codeGeneration.enabled() && !definition.capabilityManifest().supports(EditorCapability.CODE_GENERATION))
            diagnostics.add(error("CODE_GENERATION_CAPABILITY_REQUIRED", definition.id(), "codeGeneration.enabled", "Code generation requires CODE_GENERATION"));
        if(codeGeneration.exposeInRuntime() && !codeGeneration.enabled())
            diagnostics.add(error("CODE_GENERATION_RUNTIME_REQUIRES_ENABLED", definition.id(), "codeGeneration.exposeInRuntime", "Runtime code generation requires code generation to be enabled"));
        if(codeGeneration.enabled() && codeGeneration.targetIds().isEmpty())
            diagnostics.add(error("CODE_GENERATION_TARGET_REQUIRED", definition.id(), "codeGeneration.targetIds", "At least one code generation target must be enabled"));
        for(String targetId : codeGeneration.targetIds()) if(!supportedGenerationTargets.contains(targetId))
            diagnostics.add(error("UNKNOWN_CODE_GENERATION_TARGET", definition.id(), "codeGeneration.targetIds", "Unknown code generation target: " + targetId));
        if(codeGeneration.enabled() && !codeGeneration.targetIds().contains(codeGeneration.defaultTargetId()))
            diagnostics.add(error("DEFAULT_CODE_GENERATION_TARGET_NOT_ENABLED", definition.id(), "codeGeneration.defaultTargetId", "Default code generation target must be enabled"));
        for(String targetId : codeGeneration.targetFeatureOverrides().keySet()) {
            if(!codeGeneration.targetIds().contains(targetId)) diagnostics.add(error("CODE_GENERATION_OVERRIDE_TARGET_NOT_ENABLED", definition.id(), "codeGeneration.targetFeatureOverrides", "Feature override target must be enabled: " + targetId));
            else if(!CodeGenerationFeatureCatalog.supportsOptionalSourceFeatures(targetId)) diagnostics.add(error("CODE_GENERATION_FEATURES_UNSUPPORTED", definition.id(), "codeGeneration.targetFeatureOverrides", "Optional source features are not supported by target: " + targetId));
        }
        for(String targetId : codeGeneration.targetIds()) {
            Set<com.example.editor.codegen.ModelCodeGenerationFeature> supportedFeatures = CodeGenerationFeatureCatalog.supportedFeatures(targetId);
            if(supportedFeatures.isEmpty()) continue;
            for(com.example.editor.codegen.ModelCodeGenerationFeature requestedFeature : codeGeneration.featuresFor(targetId).requestedFeatures())
                if(!supportedFeatures.contains(requestedFeature)) diagnostics.add(error("CODE_GENERATION_FEATURE_UNSUPPORTED", definition.id(), "codeGeneration", "Feature " + requestedFeature + " is not supported by target: " + targetId));
        }
        return new EditorDefinitionCheckReport(definition.id(), java.util.List.copyOf(diagnostics));
    }

    /** Valida una arista semántica basada en objeto sin inventar una relación directa inexistente. */
    /** Descripción: Valida las condiciones necesarias para «checkSemanticEdge» y rechaza entradas no válidas. Entradas: mapping: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkSemanticEdge(EdgeMapping mapping, CanonicalMetamodelIndex index, Set<EditorDefinitionDiagnostic> diagnostics) {
        SemanticEdgeDescriptor semantic = mapping.semanticEdge();
        var edgeClass = index.findClass(semantic.edgeClassId()).orElse(null);
        if(edgeClass == null) diagnostics.add(error("UNKNOWN_SEMANTIC_EDGE_CLASS", mapping.id(), "semanticEdge.edgeClassId", "Semantic edge class was not found"));
        var source = index.findRelation(semantic.sourceRelationId()).orElse(null);
        var target = index.findRelation(semantic.targetRelationId()).orElse(null);
        if(source == null) diagnostics.add(error("UNKNOWN_SEMANTIC_EDGE_SOURCE", mapping.id(), "semanticEdge.sourceRelationId", "Semantic edge source relation was not found"));
        else {
            if(!index.isSameOrSubtype(semantic.edgeClassId(), source.sourceTypeId())) diagnostics.add(error("SEMANTIC_EDGE_SOURCE_OWNER_MISMATCH", mapping.id(), "semanticEdge.sourceRelationId", "Source endpoint relation is not owned by the semantic edge class"));
            if(!index.isSameOrSubtype(mapping.sourceClassId(), source.targetTypeId())) diagnostics.add(error("SEMANTIC_EDGE_SOURCE_TYPE_MISMATCH", mapping.id(), "sourceClassId", "Source endpoint class is incompatible with the semantic edge source relation"));
        }
        if(target == null) diagnostics.add(error("UNKNOWN_SEMANTIC_EDGE_TARGET", mapping.id(), "semanticEdge.targetRelationId", "Semantic edge target relation was not found"));
        else {
            if(!index.isSameOrSubtype(semantic.edgeClassId(), target.sourceTypeId())) diagnostics.add(error("SEMANTIC_EDGE_TARGET_OWNER_MISMATCH", mapping.id(), "semanticEdge.targetRelationId", "Target endpoint relation is not owned by the semantic edge class"));
            if(!index.isSameOrSubtype(mapping.targetClassId(), target.targetTypeId())) diagnostics.add(error("SEMANTIC_EDGE_TARGET_TYPE_MISMATCH", mapping.id(), "targetClassId", "Target endpoint class is incompatible with the semantic edge target relation"));
        }
        if(!semantic.containmentRelationId().isBlank()) {
            var containment = index.findRelation(semantic.containmentRelationId()).orElse(null);
            if(containment == null) diagnostics.add(error("UNKNOWN_SEMANTIC_EDGE_CONTAINMENT", mapping.id(), "semanticEdge.containmentRelationId", "Semantic edge containment relation was not found"));
            else if(containment.kind() != MetaRelationKind.CONTAINMENT) diagnostics.add(error("NON_CONTAINMENT_SEMANTIC_EDGE_OWNER", mapping.id(), "semanticEdge.containmentRelationId", "Semantic edge owner relation must be containment"));
            else if(!index.isSameOrSubtype(semantic.edgeClassId(), containment.targetTypeId())) diagnostics.add(error("SEMANTIC_EDGE_CONTAINMENT_TARGET_MISMATCH", mapping.id(), "semanticEdge.containmentRelationId", "Containment relation does not contain the semantic edge class"));
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkSemanticEdgeTool» y rechaza entradas no válidas. Entradas: tool: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkSemanticEdgeTool(EdgeCreationTool tool, CanonicalMetamodelIndex index, Set<EditorDefinitionDiagnostic> diagnostics) {
        SemanticEdgeDescriptor semantic = tool.semanticEdge();
        if(!index.hasClass(semantic.edgeClassId())) diagnostics.add(error("UNKNOWN_TOOL_SEMANTIC_EDGE_CLASS", tool.id(), "semanticEdge.edgeClassId", "Semantic edge class was not found"));
        if(!index.hasRelation(semantic.sourceRelationId())) diagnostics.add(error("UNKNOWN_TOOL_SEMANTIC_EDGE_SOURCE", tool.id(), "semanticEdge.sourceRelationId", "Semantic edge source relation was not found"));
        if(!index.hasRelation(semantic.targetRelationId())) diagnostics.add(error("UNKNOWN_TOOL_SEMANTIC_EDGE_TARGET", tool.id(), "semanticEdge.targetRelationId", "Semantic edge target relation was not found"));
        if(!semantic.containmentRelationId().isBlank() && !index.hasRelation(semantic.containmentRelationId())) diagnostics.add(error("UNKNOWN_TOOL_SEMANTIC_EDGE_CONTAINMENT", tool.id(), "semanticEdge.containmentRelationId", "Semantic edge containment relation was not found"));
    }

    /** Descripción: Valida las condiciones necesarias para «checkNodeStyles» y rechaza entradas no válidas. Entradas: mapping: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkNodeStyles(NodeMapping mapping, Set<EditorDefinitionDiagnostic> diagnostics) {
        if(mapping.shape() == EditorNodeShape.SVG_PATH) {
            String svgPath = mapping.styles().getOrDefault("svgPath", "");
            if(svgPath.isBlank()) diagnostics.add(error("SVG_PATH_REQUIRED", mapping.id(), "styles.svgPath", "SVG_PATH node mappings require an SVG path"));
            else if(!svgPath.matches("[MmLlHhVvCcSsQqTtAaZz0-9+\\-.,\\sEe]+")) diagnostics.add(error("INVALID_SVG_PATH", mapping.id(), "styles.svgPath", "SVG path contains unsupported commands or external content"));
        }
        checkColor(mapping.styles(), "fillColor", mapping.id(), diagnostics);
        checkColor(mapping.styles(), "strokeColor", mapping.id(), diagnostics);
        checkColor(mapping.styles(), "labelColor", mapping.id(), diagnostics);
        checkColor(mapping.styles(), "labelBackgroundColor", mapping.id(), diagnostics);
        checkNumber(mapping.styles(), "strokeWidth", mapping.id(), 0, Double.MAX_VALUE, false, diagnostics);
        checkNumber(mapping.styles(), "borderRadius", mapping.id(), 0, Double.MAX_VALUE, true, diagnostics);
        checkNumber(mapping.styles(), "opacity", mapping.id(), 0.1, 1, true, diagnostics);
        checkNumber(mapping.styles(), "labelFontSize", mapping.id(), 1, 200, false, diagnostics);
        checkNumber(mapping.styles(), "labelFontWeight", mapping.id(), 100, 900, true, diagnostics);
        checkNumber(mapping.styles(), "labelPadding", mapping.id(), 0, 100, true, diagnostics);
        checkBoolean(mapping.styles(), "labelVisible", mapping.id(), diagnostics); checkBoolean(mapping.styles(), "labelBackgroundTransparent", mapping.id(), diagnostics);
        checkEnum(mapping.styles(), "borderStyle", mapping.id(), Set.of("SOLID", "DASHED", "DOTTED", "DOUBLE", "NONE"), diagnostics);
        checkEnum(mapping.styles(), "labelPosition", mapping.id(), Set.of("TOP", "CENTER", "BOTTOM", "OUTSIDE_TOP", "OUTSIDE_BOTTOM"), diagnostics);
    }
    /** Descripción: Valida las condiciones necesarias para «checkConditionalStyles» y rechaza entradas no válidas. Entradas: mappingId: identificador que delimita el recurso objetivo; rules: dato de entrada necesario para la operación; expressionsById: identificador que delimita el recurso objetivo; expressionIds: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; nodeStyle: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkConditionalStyles(String mappingId, java.util.List<ConditionalStyleRule> rules,
            Map<String, ExpressionDefinition> expressionsById, Set<String> expressionIds,
            Set<EditorDefinitionDiagnostic> diagnostics, boolean nodeStyle) {
        if(rules.size() > 64) diagnostics.add(error("TOO_MANY_CONDITIONAL_STYLES", mappingId, "conditionalStyles", "A visual mapping supports at most 64 conditional style rules"));
        Set<String> ids = new HashSet<>(); Set<String> conditions = new HashSet<>();
        for(ConditionalStyleRule rule : rules) {
            if(!ids.add(rule.id())) diagnostics.add(error("DUPLICATE_CONDITIONAL_STYLE", rule.id(), "conditionalStyles", "Duplicate conditional style id"));
            checkExpression(rule.conditionExpressionId(), rule.id(), "conditionExpressionId", expressionIds, diagnostics);
            ExpressionDefinition condition = expressionsById.get(rule.conditionExpressionId());
            if(condition != null && !conditions.add(ExpressionSemanticFingerprint.of(condition)))
                diagnostics.add(error("DUPLICATE_CONDITIONAL_STYLE_CONDITION", rule.id(), "conditionExpressionId", "Two conditional styles cannot use the same condition"));
            if(nodeStyle) checkConditionalNodeStyles(rule, diagnostics); else checkConditionalEdgeStyles(rule, diagnostics);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «checkConditionalNodeStyles» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkConditionalNodeStyles(ConditionalStyleRule rule, Set<EditorDefinitionDiagnostic> diagnostics) {
        Set<String> allowed = Set.of("shape", "fillColor", "strokeColor", "strokeWidth", "opacity", "borderRadius", "borderStyle", "labelColor", "labelBackgroundColor", "labelBackgroundTransparent", "labelPosition", "labelFontSize", "labelFontWeight", "labelPadding", "labelVisible");
        for(String key : rule.styles().keySet()) if(!allowed.contains(key)) diagnostics.add(error("UNSUPPORTED_CONDITIONAL_STYLE", rule.id(), "styles." + key, "Unsupported conditional style property: " + key));
        checkColor(rule.styles(), "fillColor", rule.id(), diagnostics);
        checkColor(rule.styles(), "strokeColor", rule.id(), diagnostics);
        checkColor(rule.styles(), "labelColor", rule.id(), diagnostics);
        checkColor(rule.styles(), "labelBackgroundColor", rule.id(), diagnostics);
        checkNumber(rule.styles(), "strokeWidth", rule.id(), 0, Double.MAX_VALUE, false, diagnostics);
        checkNumber(rule.styles(), "borderRadius", rule.id(), 0, Double.MAX_VALUE, true, diagnostics);
        checkNumber(rule.styles(), "opacity", rule.id(), 0.1, 1, true, diagnostics);
        checkNumber(rule.styles(), "labelFontSize", rule.id(), 7, 200, true, diagnostics);
        checkNumber(rule.styles(), "labelFontWeight", rule.id(), 100, 900, true, diagnostics);
        checkNumber(rule.styles(), "labelPadding", rule.id(), 0, 100, true, diagnostics);
        checkBoolean(rule.styles(), "labelVisible", rule.id(), diagnostics); checkBoolean(rule.styles(), "labelBackgroundTransparent", rule.id(), diagnostics);
        checkEnum(rule.styles(), "labelPosition", rule.id(), Set.of("TOP", "CENTER", "BOTTOM", "OUTSIDE_TOP", "OUTSIDE_BOTTOM"), diagnostics);
        checkEnum(rule.styles(), "borderStyle", rule.id(), Set.of("SOLID", "DASHED", "DOTTED", "DOUBLE", "NONE"), diagnostics);
        checkEnum(rule.styles(), "shape", rule.id(), Set.of("RECTANGLE", "ROUNDED_RECTANGLE", "ELLIPSE", "DIAMOND", "TRIANGLE", "PENTAGON", "HEXAGON", "OCTAGON", "PARALLELOGRAM", "TRAPEZOID", "CYLINDER", "DOCUMENT", "CLOUD", "STAR", "CROSS"), diagnostics);
    }
    private static final Set<String> EDGE_TERMINATORS = Set.of(
            "NONE", "ARROW_OPEN", "ARROW_CLOSED", "DOUBLE_ARROW_OPEN", "DOUBLE_ARROW_CLOSED",
            "TRIANGLE_OPEN", "TRIANGLE_CLOSED", "DIAMOND_OPEN", "DIAMOND_CLOSED", "CIRCLE_OPEN", "CIRCLE_CLOSED",
            "SQUARE_OPEN", "SQUARE_CLOSED", "PENTAGON_OPEN", "PENTAGON_CLOSED", "HEXAGON_OPEN", "HEXAGON_CLOSED",
            "OCTAGON_OPEN", "OCTAGON_CLOSED", "CROSS_OPEN", "CROSS_CLOSED", "BAR", "DOUBLE_BAR",
            "CROW_FOOT", "CROW_FOOT_OPTIONAL", "CROW_FOOT_REQUIRED", "ARROW", "CIRCLE", "DIAMOND");
    private static final Set<String> EDGE_PATTERNS = Set.of("SOLID", "DASHED", "DOTTED", "LONG_DASH", "DASH_DOT", "NONE");

    /** Descripción: Valida las condiciones necesarias para «checkEdgeStyles» y rechaza entradas no válidas. Entradas: mapping: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkEdgeStyles(EdgeMapping mapping, Set<EditorDefinitionDiagnostic> diagnostics) {
        checkEdgeStyleMap(mapping.styles(), mapping.id(), diagnostics, false);
    }
    /** Descripción: Valida las condiciones necesarias para «checkConditionalEdgeStyles» y rechaza entradas no válidas. Entradas: rule: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkConditionalEdgeStyles(ConditionalStyleRule rule, Set<EditorDefinitionDiagnostic> diagnostics) {
        Set<String> allowed = Set.of("routeStyle", "strokeColor", "strokeWidth", "strokeOpacity", "strokeBorderColor", "strokeBorderWidth", "borderStyle",
                "sourceDecoration", "sourceDecorationFillColor", "sourceDecorationBorderColor", "sourceDecorationBorderWidth", "sourceDecorationSize",
                "targetDecoration", "targetDecorationFillColor", "targetDecorationBorderColor", "targetDecorationBorderWidth", "targetDecorationSize",
                "labelColor", "labelBackgroundColor", "labelBackgroundTransparent", "labelVisible", "labelFontSize", "labelFontWeight", "labelPadding");
        for(String key : rule.styles().keySet()) if(!allowed.contains(key)) diagnostics.add(error("UNSUPPORTED_CONDITIONAL_STYLE", rule.id(), "styles." + key, "Unsupported conditional connection style property: " + key));
        checkEdgeStyleMap(rule.styles(), rule.id(), diagnostics, true);
        checkEnum(rule.styles(), "routeStyle", rule.id(), Set.of("STRAIGHT", "SMOOTH", "ORTHOGONAL"), diagnostics);
    }
    /** Descripción: Valida las condiciones necesarias para «checkEdgeStyleMap» y rechaza entradas no válidas. Entradas: styles: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación; conditional: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkEdgeStyleMap(Map<String,String> styles, String id, Set<EditorDefinitionDiagnostic> diagnostics, boolean conditional) {
        checkColor(styles, "strokeColor", id, diagnostics); checkColor(styles, "strokeBorderColor", id, diagnostics);
        checkColor(styles, "labelColor", id, diagnostics); checkColor(styles, "labelBackgroundColor", id, diagnostics);
        checkNumber(styles, "strokeWidth", id, 0, 64, false, diagnostics);
        checkNumber(styles, "strokeOpacity", id, .1, 1, true, diagnostics);
        checkNumber(styles, "strokeBorderWidth", id, 0, 8, true, diagnostics);
        checkNumber(styles, "labelFontSize", id, 1, 200, false, diagnostics);
        checkNumber(styles, "labelFontWeight", id, 100, 900, true, diagnostics);
        checkNumber(styles, "labelPadding", id, 0, 100, true, diagnostics);
        checkBoolean(styles, "labelVisible", id, diagnostics); checkBoolean(styles, "labelBackgroundTransparent", id, diagnostics); checkEnum(styles, "borderStyle", id, EDGE_PATTERNS, diagnostics);
        checkEnum(styles, "sourceDecoration", id, EDGE_TERMINATORS, diagnostics); checkEnum(styles, "targetDecoration", id, EDGE_TERMINATORS, diagnostics);
        checkColor(styles, "sourceDecorationFillColor", id, diagnostics); checkColor(styles, "sourceDecorationBorderColor", id, diagnostics);
        checkColor(styles, "targetDecorationFillColor", id, diagnostics); checkColor(styles, "targetDecorationBorderColor", id, diagnostics);
        checkNumber(styles, "sourceDecorationBorderWidth", id, 0, 8, true, diagnostics); checkNumber(styles, "sourceDecorationSize", id, .5, 2.5, true, diagnostics);
        checkNumber(styles, "targetDecorationBorderWidth", id, 0, 8, true, diagnostics); checkNumber(styles, "targetDecorationSize", id, .5, 2.5, true, diagnostics);
    }
    /** Descripción: Valida las condiciones necesarias para «checkColor» y rechaza entradas no válidas. Entradas: styles: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; id: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkColor(java.util.Map<String,String> styles, String key, String id, Set<EditorDefinitionDiagnostic> diagnostics) {
        String value = styles.get(key);
        if(value != null && !value.matches("#[0-9A-Fa-f]{6}")) diagnostics.add(error("INVALID_NOTATION_COLOR", id, "styles." + key, "Notation colors must use #RRGGBB"));
    }
    /** Descripción: Valida las condiciones necesarias para «checkNumber» y rechaza entradas no válidas. Entradas: styles: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; id: identificador que delimita el recurso objetivo; minimum: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación; inclusiveMinimum: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkNumber(java.util.Map<String,String> styles, String key, String id, double minimum, double maximum,
            boolean inclusiveMinimum, Set<EditorDefinitionDiagnostic> diagnostics) {
        String value = styles.get(key); if(value == null) return;
        try { double parsed = Double.parseDouble(value); if(!Double.isFinite(parsed) || (inclusiveMinimum ? parsed < minimum : parsed <= minimum) || parsed > maximum) throw new NumberFormatException(); }
        catch(NumberFormatException exception) { diagnostics.add(error("INVALID_NOTATION_NUMBER", id, "styles." + key, "Notation numeric style is outside the supported range")); }
    }
    /** Descripción: Valida las condiciones necesarias para «checkBoolean» y rechaza entradas no válidas. Entradas: styles: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; id: identificador que delimita el recurso objetivo; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkBoolean(java.util.Map<String,String> styles, String key, String id,
            Set<EditorDefinitionDiagnostic> diagnostics) {
        String value = styles.get(key);
        if(value != null && !value.equals("true") && !value.equals("false"))
            diagnostics.add(error("INVALID_NOTATION_BOOLEAN", id, "styles." + key, "Notation boolean style must be true or false"));
    }
    /** Descripción: Valida las condiciones necesarias para «checkEnum» y rechaza entradas no válidas. Entradas: styles: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; id: identificador que delimita el recurso objetivo; allowed: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkEnum(java.util.Map<String,String> styles, String key, String id, Set<String> allowed,
            Set<EditorDefinitionDiagnostic> diagnostics) {
        String value = styles.get(key); if(value != null && !allowed.contains(value)) diagnostics.add(error("INVALID_NOTATION_OPTION", id, "styles." + key, "Unsupported notation option: " + value));
    }
    /** Descripción: Valida las condiciones necesarias para «checkExpression» y rechaza entradas no válidas. Entradas: expressionId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; expressionIds: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void checkExpression(String expressionId, String elementId, String path, Set<String> expressionIds, Set<EditorDefinitionDiagnostic> diagnostics) {
        if(!expressionId.isBlank() && !expressionIds.contains(expressionId)) diagnostics.add(error("UNKNOWN_EXPRESSION", elementId, path, "Expression was not found: " + expressionId));
    }
    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de EditorDefinitionChecker. Entradas: code: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorDefinitionDiagnostic error(String code, String id, String path, String message) { return new EditorDefinitionDiagnostic(EditorDefinitionDiagnosticSeverity.ERROR, code, id, path, message); }
}
