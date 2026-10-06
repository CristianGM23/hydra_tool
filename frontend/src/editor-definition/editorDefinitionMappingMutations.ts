import type { MetaModel } from '../metamodel-designer/types';
import { createEditorDefinitionId, createNodeTool } from './editorDefinitionFactory';
import type { EdgeCreationTool, EdgeMapping, EditorDefinitionModel, EditorTool, NodeMapping } from './types';

export interface MappingMutationResult {
    model: EditorDefinitionModel;
    representationId: string;
    mappingId: string;
    toolId: string;
    created: boolean;
}

export function addClassMappingAndTool(model: EditorDefinitionModel, metamodel: MetaModel, representationId: string, classId: string, preferredGroupId = ''): MappingMutationResult {
    const representation = model.representations.find(item => item.id === representationId);
    const metaClass = metamodel.classes.find(item => item.id === classId);
    if (representation === undefined || metaClass === undefined) return unchanged(model, representationId);
    const existing = representation.nodeMappings.find(item => item.targetClassId === classId);
    if (existing !== undefined) return { model, representationId, mappingId: existing.id, toolId: matchingNodeTool(model, representationId, classId)?.id ?? '', created: false };

    const mapping: NodeMapping = {
        id: createEditorDefinitionId('node-mapping'), targetClassId: classId, representationName: metaClass.name,
        shape: 'RECTANGLE', defaultWidth: 220, defaultHeight: 140, minimumWidth: 120, minimumHeight: 80,
        labelExpressionId: '', imageAssetPath: '', imageFit: 'CONTAIN', container: false,
        containmentRelationIds: [], containerPadding: 18, styles: {}, conditionalStyles: [], annotations: {}
    };
    const concrete = metaClass.kind === 'CLASS';
    const tool = concrete ? createNodeTool(classId, representationId, metaClass.name) : null;
    const nextTools = tool === null ? model.palette.tools : [...model.palette.tools, tool];
    const nextGroups = tool === null ? model.palette.groups : assignTool(model.palette.groups, tool, preferredGroupId);
    return {
        model: {
            ...model,
            palette: { ...model.palette, tools: nextTools, groups: nextGroups },
            representations: model.representations.map(item => item.id === representationId ? {
                ...item,
                toolIds: tool === null ? item.toolIds : appendUnique(item.toolIds, tool.id),
                nodeMappings: [...item.nodeMappings, mapping]
            } : item)
        },
        representationId, mappingId: mapping.id, toolId: tool?.id ?? '', created: true
    };
}

export function addRelationMappingAndTool(model: EditorDefinitionModel, metamodel: MetaModel, representationId: string, relationId: string, preferredGroupId = ''): MappingMutationResult {
    const representation = model.representations.find(item => item.id === representationId);
    const relation = metamodel.relations.find(item => item.id === relationId);
    if (representation === undefined || relation === undefined) return unchanged(model, representationId);
    const existing = representation.edgeMappings.find(item => item.relationId === relationId);
    if (existing !== undefined) return { model, representationId, mappingId: existing.id, toolId: matchingEdgeTool(model, representationId, relationId)?.id ?? '', created: false };

    const mapping: EdgeMapping = {
        id: createEditorDefinitionId('edge-mapping'), relationId: relation.id, sourceClassId: relation.sourceTypeId,
        targetClassId: relation.targetTypeId, lineType: 'ORTHOGONAL', labelExpressionId: '', styles: {}, conditionalStyles: [], annotations: {}
    };
    const tool: EdgeCreationTool = {
        kind: 'CREATE_EDGE', id: createEditorDefinitionId('tool'), label: relation.name || 'Create relation',
        description: `Create ${relation.name || relation.id}`, icon: '', representationIds: [representationId],
        visibleExpressionId: '', enabledExpressionId: '', edgeMappingId: mapping.id, relationId: relation.id,
        sourceClassIds: [relation.sourceTypeId], targetClassIds: [relation.targetTypeId], reconnectable: true
    };
    return {
        model: {
            ...model,
            palette: { ...model.palette, tools: [...model.palette.tools, tool], groups: assignTool(model.palette.groups, tool, preferredGroupId) },
            representations: model.representations.map(item => item.id === representationId ? {
                ...item, toolIds: appendUnique(item.toolIds, tool.id), edgeMappings: [...item.edgeMappings, mapping]
            } : item)
        },
        representationId, mappingId: mapping.id, toolId: tool.id, created: true
    };
}

function assignTool(groups: EditorDefinitionModel['palette']['groups'], tool: EditorTool, preferredGroupId: string) {
    const targetId = groups.some(group => group.id === preferredGroupId) ? preferredGroupId : groups[0]?.id ?? '';
    return groups.map(group => group.id === targetId ? { ...group, toolIds: appendUnique(group.toolIds, tool.id) } : group);
}

function matchingNodeTool(model: EditorDefinitionModel, representationId: string, classId: string) {
    return model.palette.tools.find(tool => tool.kind === 'CREATE_NODE' && tool.targetClassId === classId && tool.representationIds.includes(representationId));
}

function matchingEdgeTool(model: EditorDefinitionModel, representationId: string, relationId: string) {
    return model.palette.tools.find(tool => tool.kind === 'CREATE_EDGE' && tool.relationId === relationId && tool.representationIds.includes(representationId));
}

function appendUnique(values: string[], value: string): string[] { return values.includes(value) ? values : [...values, value]; }
function unchanged(model: EditorDefinitionModel, representationId: string): MappingMutationResult { return { model, representationId, mappingId: '', toolId: '', created: false }; }
