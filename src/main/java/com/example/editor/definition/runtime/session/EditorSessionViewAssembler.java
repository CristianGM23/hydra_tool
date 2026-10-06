package com.example.editor.definition.runtime.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.ConditionalStyleRule;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.definition.runtime.ExecutableRepresentationPlan;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Construye la proyección de lectura de una sesión sin cargar el orquestador de mutaciones. */
final class EditorSessionViewAssembler {
    private final ExpressionModelApplicationService expressions;

    /** Descripción: Inicializa una instancia de EditorSessionViewAssembler con sus dependencias y datos de entrada. Entradas: expressions: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EditorSessionViewAssembler(ExpressionModelApplicationService expressions) { this.expressions=expressions; }

    /** Descripción: Ejecuta la operación «assemble» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: stored: dato de entrada necesario para la operación; plan: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo; canUndo: dato de entrada necesario para la operación; canRedo: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ EditorSessionView assemble(StoredCanonicalDocument<EditorModelSession> stored,ExecutableEditorPlan plan,MetaModel metamodel,
            ExecutableRepresentationPlan representation,String selectedElementId,boolean canUndo,boolean canRedo) {
        EditorModelSession session=stored.document();
        CanonicalMetamodelIndex index=new CanonicalMetamodelIndex(metamodel);
        List<EditorRenderedNode> nodes=renderNodes(session,representation,index);
        Set<String> visible=nodes.stream().map(EditorRenderedNode::elementId).collect(java.util.stream.Collectors.toSet());
        List<EditorRenderedEdge> edges=renderEdges(session,representation,index,visible);
        FormDefinition form=selectedElementId==null||selectedElementId.isBlank()?null:session.model().findElement(selectedElementId)
                .map(element->formFor(element.classId(),plan,index)).orElse(null);
        List<EditorRepresentationRuntimeView> representations=plan.representationsById().values().stream()
                .map(item->new EditorRepresentationRuntimeView(item.id(),item.name(),item.kind(),item.editable(),
                        item.id().equals(session.activeRepresentationId()),session.openRepresentationIds().contains(item.id()))).toList();
        Map<String,EditorRuntimeToolState> toolStates=new LinkedHashMap<>();
        representation.toolsById().values().forEach(tool->toolStates.put(tool.id(),toolState(tool,session,selectedElementId)));
        return new EditorSessionView(session,stored.etag(),representation.id(),representation.name(),representation.kind(),representation.editable(),
                nodes,edges,representation.paletteGroups(),toolStates,form,representations,session.latestValidationReport(),canUndo,canRedo);
    }

    /** Descripción: Ejecuta la operación «toolState» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: tool: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ EditorRuntimeToolState toolState(EditorTool tool,EditorModelSession session,String selectedElementId) {
        boolean visible=evaluateBoolean(tool.visibleExpressionId(),session,selectedElementId,true);
        boolean enabled=visible&&evaluateBoolean(tool.enabledExpressionId(),session,selectedElementId,true);
        String reason=!visible?"Visibility expression evaluated to false":!enabled?"Enabled expression evaluated to false":"";
        return new EditorRuntimeToolState(tool.id(),visible,enabled,reason);
    }

    /** Descripción: Ejecuta la operación «renderNodes» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: session: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<EditorRenderedNode> renderNodes(EditorModelSession session,ExecutableRepresentationPlan representation,CanonicalMetamodelIndex index) {
        List<EditorRenderedNode> nodes=new ArrayList<>();
        for(CanonicalModelElement element:session.model().elements()) {
            List<NodeMapping> compatible=compatibleMappings(element.classId(),representation,index);
            if(compatible.isEmpty()) continue;
            String preferred=session.annotations().getOrDefault(appearanceKey(representation.id(),element.id()),"");
            NodeMapping mapping=compatible.stream().filter(item->item.id().equals(preferred)).findFirst().orElse(compatible.get(0));
            MetaClass type=index.findClass(element.classId()).orElse(null);
            Map<String,String> resolvedStyles=resolveNodeStyles(mapping,session,element.id());
            List<EditorNodeAppearanceView> appearances=compatible.stream().map(item->new EditorNodeAppearanceView(
                    item.id(),item.representationName(),item.shape(),item.imageAssetPath())).toList();
            nodes.add(new EditorRenderedNode(element.id(),element.classId(),type==null?"Unknown class":type.name(),mapping.id(),
                    mapping.representationName(),resolveNodeShape(mapping.shape(),resolvedStyles),label(element,type,index),mapping.defaultWidth(),mapping.defaultHeight(),mapping.minimumWidth(),mapping.minimumHeight(),
                    element.attributes(),resolvedStyles,mapping.imageAssetPath(),mapping.imageFit(),mapping.container(),
                    mapping.containmentRelationIds(),mapping.containerPadding(),appearances));
        }
        return nodes;
    }

    /** Descripción: Obtiene la información correspondiente a «resolveNodeStyles» dentro de EditorSessionViewAssembler. Entradas: mapping: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> resolveNodeStyles(NodeMapping mapping, EditorModelSession session, String elementId) {
        return resolveConditionalStyles(mapping.styles(), mapping.conditionalStyles(), session, elementId);
    }

    /**
     * Resolves declarative style overrides without mutating the editor definition.
     * Rules are bounded, deterministic and evaluated in the semantic element context.
     * For connections the source element is the context, which keeps conditions portable
     * across runtimes while still allowing relation-specific styling through each mapping.
     */
    /** Descripción: Obtiene la información correspondiente a «resolveConditionalStyles» dentro de EditorSessionViewAssembler. Entradas: base: dato de entrada necesario para la operación; conditionalStyles: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> resolveConditionalStyles(Map<String,String> base, List<ConditionalStyleRule> conditionalStyles,
            EditorModelSession session, String elementId) {
        if(conditionalStyles == null || conditionalStyles.isEmpty() || expressions == null) return base;
        List<ConditionalStyleRule> rules = conditionalStyles.stream().filter(ConditionalStyleRule::enabled)
                .sorted(Comparator.comparingInt(ConditionalStyleRule::priority).thenComparing(ConditionalStyleRule::id)).limit(64).toList();
        if(rules.isEmpty()) return base;
        Map<String,Boolean> matches;
        try { matches = expressions.evaluateBooleans(session.metamodelId(), rules.stream().map(ConditionalStyleRule::conditionExpressionId).toList(), session.model(), elementId); }
        catch(RuntimeException exception) { return base; }
        Map<String,String> resolved = new LinkedHashMap<>(base);
        for(ConditionalStyleRule rule : rules) if(Boolean.TRUE.equals(matches.get(rule.conditionExpressionId()))) resolved.putAll(rule.styles());
        return java.util.Collections.unmodifiableMap(resolved);
    }

    /** Descripción: Obtiene la información correspondiente a «resolveNodeShape» dentro de EditorSessionViewAssembler. Entradas: fallback: dato de entrada necesario para la operación; styles: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorNodeShape resolveNodeShape(EditorNodeShape fallback, Map<String,String> styles) {
        String value=styles.get("shape");
        if(value==null||value.isBlank()) return fallback;
        try {
            EditorNodeShape candidate=EditorNodeShape.valueOf(value);
            return candidate==EditorNodeShape.PROJECT_IMAGE||candidate==EditorNodeShape.SVG_PATH?fallback:candidate;
        } catch(IllegalArgumentException exception) { return fallback; }
    }

    /** Descripción: Proyecta relaciones directas y aristas semánticas basadas en objeto sin alterar el modelo canónico. */
    /** Descripción: Ejecuta la operación «renderEdges» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: session: dato de entrada necesario para la operación; representation: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; visible: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<EditorRenderedEdge> renderEdges(EditorModelSession session,ExecutableRepresentationPlan representation,CanonicalMetamodelIndex index,Set<String> visible) {
        List<EditorRenderedEdge> edges=new ArrayList<>();
        for(CanonicalModelRelation item:session.model().relations()) {
            if(!visible.contains(item.sourceElementId())||!visible.contains(item.targetElementId())) continue;
            EdgeMapping mapping=representation.edgeMappingsByRelationId().getOrDefault(item.relationId(),List.of()).stream().findFirst().orElse(null);
            if(mapping==null) continue;
            MetaRelation relation=index.findRelation(item.relationId()).orElse(null);
            Map<String,String> resolvedStyles=resolveConditionalStyles(mapping.styles(), mapping.conditionalStyles(), session, item.sourceElementId());
            edges.add(new EditorRenderedEdge(item.id(),item.relationId(),relation==null||relation.name().isBlank()?"Relationship":relation.name(),
                    mapping.id(),item.sourceElementId(),item.targetElementId(),mapping.lineType(),relation==null?"":relation.name(),resolvedStyles));
        }
        for(CanonicalModelElement edgeElement:session.model().elements()) {
            List<EdgeMapping> mappings=semanticEdgeMappings(edgeElement.classId(),representation,index);
            for(EdgeMapping mapping:mappings) {
                var semantic=mapping.semanticEdge();
                List<CanonicalModelRelation> sourceRefs=session.model().relations().stream()
                        .filter(item->item.sourceElementId().equals(edgeElement.id())&&item.relationId().equals(semantic.sourceRelationId())).toList();
                List<CanonicalModelRelation> targetRefs=session.model().relations().stream()
                        .filter(item->item.sourceElementId().equals(edgeElement.id())&&item.relationId().equals(semantic.targetRelationId())).toList();
                if(sourceRefs.size()!=1||targetRefs.size()!=1) continue;
                String sourceId=sourceRefs.get(0).targetElementId(); String targetId=targetRefs.get(0).targetElementId();
                if(!visible.contains(sourceId)||!visible.contains(targetId)) continue;
                MetaClass edgeType=index.findClass(edgeElement.classId()).orElse(null);
                String edgeName=edgeType==null||edgeType.name().isBlank()?"Relationship":edgeType.name();
                Map<String,String> resolvedStyles=resolveConditionalStyles(mapping.styles(),mapping.conditionalStyles(),session,edgeElement.id());
                edges.add(new EditorRenderedEdge(edgeElement.id(),"",edgeName,mapping.id(),sourceId,targetId,mapping.lineType(),
                        label(edgeElement,edgeType,index),resolvedStyles));
            }
        }
        return List.copyOf(edges);
    }

    /** Descripción: Ejecuta la operación «semanticEdgeMappings» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: classId: identificador que delimita el recurso objetivo; representation: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<EdgeMapping> semanticEdgeMappings(String classId, ExecutableRepresentationPlan representation, CanonicalMetamodelIndex index) {
        List<EdgeMapping> result=new ArrayList<>();
        List<EdgeMapping> direct=representation.edgeMappingsBySemanticClassId().get(classId);
        if(direct!=null) result.addAll(direct);
        representation.edgeMappingsBySemanticClassId().entrySet().stream()
                .filter(entry->!entry.getKey().equals(classId)&&index.isSameOrSubtype(classId,entry.getKey()))
                .forEach(entry->result.addAll(entry.getValue()));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «formFor» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: classId: identificador que delimita el recurso objetivo; plan: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private FormDefinition formFor(String classId,ExecutableEditorPlan plan,CanonicalMetamodelIndex index) {
        FormDefinition direct=plan.formsByTargetClassId().get(classId);
        if(direct!=null) return direct;
        return plan.formsByTargetClassId().entrySet().stream().filter(entry->index.isSameOrSubtype(classId,entry.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /** Descripción: Obtiene los mappings visuales compatibles con una clase en la representación activa. Entradas: classId, representation e index. Salida: lista inmutable de mappings directos o heredados compatibles. */
    /** Descripción: Ejecuta la operación «compatibleMappings» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: classId: identificador que delimita el recurso objetivo; representation: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<NodeMapping> compatibleMappings(String classId,ExecutableRepresentationPlan representation,CanonicalMetamodelIndex index) {
        List<NodeMapping> result=new ArrayList<>();
        List<NodeMapping> direct=representation.nodeMappingsByClassId().get(classId);
        if(direct!=null) result.addAll(direct);
        representation.nodeMappingsByClassId().entrySet().stream()
                .filter(entry->!entry.getKey().equals(classId)&&index.isSameOrSubtype(classId,entry.getKey()))
                .forEach(entry->result.addAll(entry.getValue()));
        return List.copyOf(result);
    }

    /** Descripción: Construye la clave de sesión usada para una preferencia de apariencia. Entradas: representationId y elementId. Salida: clave estable acotada por representación y elemento. */
    /** Descripción: Ejecuta la operación «appearanceKey» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: representationId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ static String appearanceKey(String representationId,String elementId) {
        return "appearance:"+representationId+":"+elementId;
    }

    /** Descripción: Obtiene el mapping visual predeterminado compatible con una clase. Entradas: classId, representation e index. Salida: primer mapping compatible o null cuando no existe. */
    /** Descripción: Ejecuta la operación «mappingFor» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: classId: identificador que delimita el recurso objetivo; representation: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeMapping mappingFor(String classId,ExecutableRepresentationPlan representation,CanonicalMetamodelIndex index) {
        List<NodeMapping> compatible=compatibleMappings(classId,representation,index);
        return compatible.isEmpty()?null:compatible.get(0);
    }

    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: element: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String label(CanonicalModelElement element,MetaClass type,CanonicalMetamodelIndex index) {
        for(Map.Entry<String,Object> entry:element.attributes().entrySet()) {
            MetaAttribute attribute=index.findAttribute(entry.getKey()).orElse(null);
            if(attribute!=null&&("name".equalsIgnoreCase(attribute.name())||"title".equalsIgnoreCase(attribute.name()))&&entry.getValue()!=null) return entry.getValue().toString();
        }
        return element.id();
    }

    /** Descripción: Ejecuta la operación «evaluateBoolean» correspondiente a la responsabilidad de EditorSessionViewAssembler. Entradas: expressionId: identificador que delimita el recurso objetivo; session: dato de entrada necesario para la operación; selectedElementId: identificador que delimita el recurso objetivo; defaultValue: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean evaluateBoolean(String expressionId,EditorModelSession session,String selectedElementId,boolean defaultValue) {
        if(expressionId==null||expressionId.isBlank()) return defaultValue;
        if(expressions==null) return false;
        try {
            Object value=expressions.evaluate(session.metamodelId(),expressionId,session.model(),selectedElementId==null?"":selectedElementId).value();
            return value instanceof Boolean booleanValue&&booleanValue;
        } catch(RuntimeException exception) { return false; }
    }
}
