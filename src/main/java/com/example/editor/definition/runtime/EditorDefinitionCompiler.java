package com.example.editor.definition.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.definition.EditorDefinitionApplicationException;
import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.PaletteGroup;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationLayerDefinition;

/** Compila contratos de editores en índices inmutables y ejecutables. */
@Component
public class EditorDefinitionCompiler {

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de EditorDefinitionCompiler. Entradas: definition: dato de entrada necesario para la operación; sourceEtag: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExecutableEditorPlan compile(EditorDefinitionModel definition, String sourceEtag,
            EditorDefinitionCheckReport report) {
        if(definition == null) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Editor definition is required");
        if(report == null || !report.valid()) throw new EditorDefinitionApplicationException(EditorDefinitionApplicationException.Reason.INVALID_REQUEST, "Editor definition contains blocking diagnostics");

        Map<String, EditorTool> globalTools = new LinkedHashMap<>();
        definition.palette().tools().forEach(tool -> globalTools.put(tool.id(), tool));
        Map<String, FormDefinition> formsById = new LinkedHashMap<>();
        Map<String, FormDefinition> formsByClass = new LinkedHashMap<>();
        for(FormDefinition form : definition.forms()) {
            formsById.put(form.id(), form);
            formsByClass.putIfAbsent(form.targetClassId(), form);
        }

        Map<String, ExecutableRepresentationPlan> representations = new LinkedHashMap<>();
        for(RepresentationDefinition representation : definition.representations()) {
            Set<String> activeMappingIds = representation.defaultActiveMappingIds();
            Set<String> activeToolIds = activeToolIds(representation, globalTools);
            Map<String, EditorTool> tools = new LinkedHashMap<>();
            activeToolIds.forEach(id -> {
                EditorTool tool = globalTools.get(id);
                if(tool != null) tools.put(id, tool);
            });
            List<ExecutablePaletteGroup> groups = compileGroups(definition.palette().groups(), tools);
            Map<String, List<com.example.editor.definition.NodeMapping>> nodesByClass = new LinkedHashMap<>();
            representation.nodeMappings().stream().filter(mapping -> activeMappingIds.contains(mapping.id()))
                    .forEach(mapping -> nodesByClass.computeIfAbsent(mapping.targetClassId(), ignored -> new ArrayList<>()).add(mapping));
            Map<String, List<com.example.editor.definition.EdgeMapping>> edgesByRelation = new LinkedHashMap<>();
            Map<String, List<com.example.editor.definition.EdgeMapping>> edgesBySemanticClass = new LinkedHashMap<>();
            representation.edgeMappings().stream().filter(mapping -> activeMappingIds.contains(mapping.id())).forEach(mapping -> {
                if(mapping.semanticObjectBacked()) edgesBySemanticClass.computeIfAbsent(mapping.semanticEdge().edgeClassId(), ignored -> new ArrayList<>()).add(mapping);
                else edgesByRelation.computeIfAbsent(mapping.relationId(), ignored -> new ArrayList<>()).add(mapping);
            });
            Map<String,String> representationAnnotations = new LinkedHashMap<>(representation.annotations());
            long inactiveLayers = representation.layers().stream().filter(layer -> !layer.activeByDefault()).count();
            long externalFilters = representation.filters().stream().filter(filter -> !filter.executable()).count();
            if(inactiveLayers > 0) representationAnnotations.put("hydra.runtime.inactiveLayerCount", Long.toString(inactiveLayers));
            if(externalFilters > 0) representationAnnotations.put("hydra.runtime.externalFilterCount", Long.toString(externalFilters));
            representations.put(representation.id(), new ExecutableRepresentationPlan(representation.id(), representation.name(),
                    representation.kind(), representation.rootClassIds(), representation.editable(), nodesByClass,
                    edgesByRelation, edgesBySemanticClass, tools, groups, representation.layers(), representation.filters(), representationAnnotations));
        }

        return new ExecutableEditorPlan(EditorRuntimeContractVersions.EXECUTABLE_EDITOR_PLAN,
                definition.id() + ":compiled", definition.id(), sourceEtag, definition.metamodelId(), definition.name(),
                Instant.now(), representations, formsById, formsByClass, definition.capabilityManifest(), definition.runtimePolicy(), definition.codeGeneration(), definition.annotations());
    }

    /** Descripción: Ejecuta la operación «activeToolIds» correspondiente a la responsabilidad de EditorDefinitionCompiler. Entradas: representation: dato de entrada necesario para la operación; globalTools: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Set<String> activeToolIds(RepresentationDefinition representation, Map<String, EditorTool> globalTools) {
        Set<String> candidates = new LinkedHashSet<>(representation.toolIds());
        for(EditorTool tool : globalTools.values()) {
            if(tool.representationIds().isEmpty() || tool.representationIds().contains(representation.id())) candidates.add(tool.id());
        }
        if(representation.layers().isEmpty()) return candidates;
        Set<String> referencedByLayers = new LinkedHashSet<>();
        Set<String> activeLayerTools = new LinkedHashSet<>();
        for(RepresentationLayerDefinition layer : representation.layers()) {
            referencedByLayers.addAll(layer.toolIds());
            if(layer.activeByDefault()) activeLayerTools.addAll(layer.toolIds());
        }
        return candidates.stream().filter(id -> !referencedByLayers.contains(id) || activeLayerTools.contains(id))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** Descripción: Ejecuta la operación «compileGroups» correspondiente a la responsabilidad de EditorDefinitionCompiler. Entradas: sourceGroups: dato de entrada necesario para la operación; activeTools: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ExecutablePaletteGroup> compileGroups(List<PaletteGroup> sourceGroups, Map<String, EditorTool> activeTools) {
        List<ExecutablePaletteGroup> result = new ArrayList<>();
        Set<String> grouped = new LinkedHashSet<>();
        sourceGroups.stream().sorted(Comparator.comparingInt(PaletteGroup::order).thenComparing(PaletteGroup::name)).forEach(group -> {
            List<EditorTool> tools = group.toolIds().stream().map(activeTools::get).filter(java.util.Objects::nonNull).toList();
            if(!tools.isEmpty()) {
                grouped.addAll(tools.stream().map(EditorTool::id).toList());
                result.add(new ExecutablePaletteGroup(group.id(), group.name(), group.order(), group.initiallyCollapsed(), tools));
            }
        });
        List<EditorTool> ungrouped = activeTools.values().stream().filter(tool -> !grouped.contains(tool.id())).toList();
        if(!ungrouped.isEmpty()) result.add(new ExecutablePaletteGroup("group:other", "Other tools", Integer.MAX_VALUE, false, ungrouped));
        return List.copyOf(result);
    }
}
