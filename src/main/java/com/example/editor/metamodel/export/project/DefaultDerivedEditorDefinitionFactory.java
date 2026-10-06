package com.example.editor.metamodel.export.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.CodeGenerationDefinition;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorEdgeLineType;
import com.example.editor.definition.EditorNodeShape;
import com.example.editor.definition.NodeCreationTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.PaletteGroup;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.ToolPaletteDefinition;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;

/** Crea una representación diagramática mínima cuando un destino gráfico necesita editor y no existe uno persistido. */
@Component
public class DefaultDerivedEditorDefinitionFactory {
    /** Descripción: Crea o prepara el resultado de «create» dentro de DefaultDerivedEditorDefinitionFactory. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionModel create(MetaModel metamodel){
        String representationId = "default-diagram";
        List<NodeMapping> nodes = metamodel.classes().stream().map(metaClass -> new NodeMapping("node-" + metaClass.id(), metaClass.id(), metaClass.name(), EditorNodeShape.ROUNDED_RECTANGLE, 180, 100, 100, 60, "", "", null, false, containmentIds(metamodel, metaClass.id()), 18, Map.of(), List.of(), Map.of("hydra.derived", "true"))).toList();
        Set<String> classIds = metamodel.classes().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet());
        List<EdgeMapping> edges = metamodel.relations().stream().filter(relation -> classIds.contains(relation.sourceTypeId()) && classIds.contains(relation.targetTypeId()))
                .map(relation -> new EdgeMapping("edge-" + relation.id(), relation.id(), relation.sourceTypeId(), relation.targetTypeId(), EditorEdgeLineType.SMOOTH_STEP, "", Map.of(), List.of(), Map.of("hydra.derived", "true"))).toList();
        List<NodeCreationTool> tools = metamodel.classes().stream().filter(metaClass -> metaClass.kind() == MetaClassKind.CLASS)
                .map(metaClass -> new NodeCreationTool("create-" + metaClass.id(), "Create " + metaClass.name(),
                        "Creates a " + metaClass.name() + " instance.", "", List.of(representationId), "", "",
                        metaClass.id(), Map.of(), "")).toList();
        List<String> toolIds = tools.stream().map(NodeCreationTool::id).toList();
        ToolPaletteDefinition palette = new ToolPaletteDefinition(toolIds.isEmpty() ? List.of() : List.of(new PaletteGroup(
                "default-tools", "Elements", 0, toolIds, false)), new ArrayList<>(tools));
        RepresentationDefinition representation = new RepresentationDefinition(representationId, "Default diagram", RepresentationKind.DIAGRAM,
                metamodel.classes().stream().map(item -> item.id()).toList(), nodes, edges, toolIds, true, Map.of("hydra.derived", "true"));
        return new EditorDefinitionModel(null, metamodel.id() + "-derived-editor", metamodel.id(), metamodel.name() + " derived editor",
                List.of(representation), palette, List.of(), new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING,
                        EditorCapability.EXPORT, EditorCapability.CODE_GENERATION, EditorCapability.UNDO_REDO)),
                com.example.editor.definition.EditorRuntimePolicy.defaults(), CodeGenerationDefinition.defaults(), Map.of("hydra.derived", "true"));
    }

    /** Descripción: Ejecuta la operación «containmentIds» correspondiente a la responsabilidad de DefaultDerivedEditorDefinitionFactory. Entradas: metamodel: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> containmentIds(MetaModel metamodel, String sourceId){
        return metamodel.relations().stream().filter(relation -> relation.sourceTypeId().equals(sourceId)
                && relation.kind() == com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT).map(item -> item.id()).toList();
    }
}
