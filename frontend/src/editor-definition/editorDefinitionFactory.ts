import type { MetaModel } from '../metamodel-designer/types';
import { EMPTY_CODE_GENERATION_FEATURES } from '../model-codegen/catalog';
import { detectMetamodelRoots } from './metamodelRootDetection';
import type { EditorDefinitionModel, NodeCreationTool, RepresentationDefinition } from './types';

let sequence = 0;

export function createEditorDefinitionId(prefix: string): string {
    sequence += 1;
    return `${prefix}:${Date.now().toString(36)}-${sequence.toString(36)}`;
}

export function createEmptyEditorDefinition(metamodelOrId: MetaModel | string, editorId?: string, name = 'New model editor'): EditorDefinitionModel {
    const metamodelId = typeof metamodelOrId === 'string' ? metamodelOrId : metamodelOrId.id;
    const detectedRoot = typeof metamodelOrId === 'string' ? '' : detectMetamodelRoots(metamodelOrId).primaryClassId;
    const representation = createDefaultRepresentation(detectedRoot === '' ? [] : [detectedRoot]);
    return {
        schemaVersion: '1.0',
        id: editorId ?? `${metamodelId}-editor`,
        metamodelId,
        name,
        representations: [representation],
        palette: {
            groups: [{ id: 'group:elements', name: 'Elements', order: 0, toolIds: [], initiallyCollapsed: false }],
            tools: []
        },
        forms: [],
        capabilityManifest: { capabilities: ['MODEL_EDITING', 'MULTIPLE_REPRESENTATIONS', 'CODE_GENERATION', 'UNDO_REDO'] },
        runtimePolicy: { validationTriggers: ['MANUAL'], autosave: true, recoverLatestSession: true, publishEvents: true, eventFlowsEnabled: false, gamificationEnabled: false, maximumOpenRepresentations: 2, operationTimeoutMillis: 30000 },
        codeGeneration: { enabled:true, exposeInRuntime:true, targetIds:[], defaultTargetId:'java', outputNamespace:'generated.model', includeTraceability:true, defaultFeatures:{...EMPTY_CODE_GENERATION_FEATURES}, targetFeatureOverrides:{}, annotations:{} },
        annotations: {}
    };
}

export function createAdditionalEditorDefinition(metamodelOrId: MetaModel | string): EditorDefinitionModel {
    const metamodelId = typeof metamodelOrId === 'string' ? metamodelOrId : metamodelOrId.id;
    const suffix = `${Date.now().toString(36)}-${(++sequence).toString(36)}`;
    return createEmptyEditorDefinition(metamodelOrId, `${metamodelId}-editor-${suffix}`, 'New model editor');
}

export function createDefaultRepresentation(rootClassIds: string[] = []): RepresentationDefinition {
    return {
        id: createEditorDefinitionId('representation'),
        name: 'Main diagram',
        kind: 'DIAGRAM',
        rootClassIds,
        nodeMappings: [],
        edgeMappings: [],
        toolIds: [],
        layers: [],
        filters: [],
        editable: true,
        annotations: {}
    };
}

export function createNodeTool(targetClassId: string, representationId: string, label: string): NodeCreationTool {
    return {
        kind: 'CREATE_NODE',
        id: createEditorDefinitionId('tool'),
        label,
        description: `Create ${label}`,
        icon: '',
        representationIds: [representationId],
        visibleExpressionId: '',
        enabledExpressionId: '',
        targetClassId,
        defaultValues: {},
        formId: ''
    };
}
