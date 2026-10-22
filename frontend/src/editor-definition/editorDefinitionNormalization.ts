import type { MetaModel } from '../metamodel-designer/types';
import { EMPTY_CODE_GENERATION_FEATURES } from '../model-codegen/catalog';
import type { CodeGenerationDefinition } from '../model-codegen/types';
import { detectMetamodelRoots } from './metamodelRootDetection';
import type {
    EdgeMapping, EditorDefinitionModel, EditorRuntimePolicy, EditorTool, FormDefinition, FormFieldDefinition,
    FormSectionDefinition, NodeMapping, PaletteGroup, RepresentationDefinition, RepresentationFilterDefinition,
    RepresentationLayerDefinition, ToolPaletteDefinition
} from './types';

const DEFAULT_RUNTIME_POLICY:EditorRuntimePolicy={
    validationTriggers:['MANUAL'], autosave:true, recoverLatestSession:true, publishEvents:true,
    eventFlowsEnabled:false, gamificationEnabled:false, maximumOpenRepresentations:2, operationTimeoutMillis:30000
};

function arrayOrEmpty<T>(value:readonly T[]|null|undefined):T[]{return value===undefined||value===null?[]:[...value];}
function stringRecord(value:Record<string,string>|null|undefined):Record<string,string>{return value??{};}
function unknownRecord(value:Record<string,unknown>|null|undefined):Record<string,unknown>{return value??{};}

export function normalizeEditorCodeGeneration(value:CodeGenerationDefinition|undefined,enabled:boolean):CodeGenerationDefinition{
    const base=value??{enabled,exposeInRuntime:enabled,targetIds:[],defaultTargetId:'java',outputNamespace:'generated.model',includeTraceability:true,defaultFeatures:EMPTY_CODE_GENERATION_FEATURES,targetFeatureOverrides:{},annotations:{}};
    return {...base,targetIds:arrayOrEmpty(base.targetIds),defaultTargetId:base.defaultTargetId||'java',outputNamespace:base.outputNamespace||'generated.model',defaultFeatures:base.defaultFeatures??EMPTY_CODE_GENERATION_FEATURES,targetFeatureOverrides:base.targetFeatureOverrides??{},annotations:stringRecord(base.annotations)};
}

export function normalizeEditorRuntimePolicy(value:EditorRuntimePolicy|undefined):EditorRuntimePolicy{
    const base=value??DEFAULT_RUNTIME_POLICY;
    return {...DEFAULT_RUNTIME_POLICY,...base,validationTriggers:arrayOrEmpty(base.validationTriggers),maximumOpenRepresentations:Math.max(1,base.maximumOpenRepresentations||DEFAULT_RUNTIME_POLICY.maximumOpenRepresentations),operationTimeoutMillis:Math.max(1,base.operationTimeoutMillis||DEFAULT_RUNTIME_POLICY.operationTimeoutMillis)};
}

function normalizeLayer(layer:RepresentationLayerDefinition):RepresentationLayerDefinition{
    return {...layer,nodeMappingIds:arrayOrEmpty(layer.nodeMappingIds),edgeMappingIds:arrayOrEmpty(layer.edgeMappingIds),toolIds:arrayOrEmpty(layer.toolIds),annotations:stringRecord(layer.annotations)};
}

function normalizeFilter(filter:RepresentationFilterDefinition):RepresentationFilterDefinition{
    return {...filter,mappingIds:arrayOrEmpty(filter.mappingIds),annotations:stringRecord(filter.annotations)};
}

export function normalizeNodeMapping(mapping: NodeMapping): NodeMapping {
    return {
        ...mapping,
        imageAssetPath: mapping.imageAssetPath ?? '',
        imageFit: mapping.imageFit ?? 'CONTAIN',
        container: mapping.container ?? false,
        containmentRelationIds: arrayOrEmpty(mapping.containmentRelationIds),
        containerPadding: mapping.containerPadding && mapping.containerPadding > 0 ? mapping.containerPadding : 18,
        styles: stringRecord(mapping.styles),
        conditionalStyles: arrayOrEmpty(mapping.conditionalStyles).map(rule=>({...rule,styles:stringRecord(rule.styles)})),
        annotations: stringRecord(mapping.annotations)
    };
}

function normalizeEdgeMapping(mapping:EdgeMapping):EdgeMapping{
    return {...mapping,styles:stringRecord(mapping.styles),conditionalStyles:arrayOrEmpty(mapping.conditionalStyles).map(rule=>({...rule,styles:stringRecord(rule.styles)})),annotations:stringRecord(mapping.annotations),semanticEdge:mapping.semanticEdge==null?undefined:{...mapping.semanticEdge,defaultValues:unknownRecord(mapping.semanticEdge.defaultValues),annotations:stringRecord(mapping.semanticEdge.annotations)}};
}

function normalizeRepresentation(representation:RepresentationDefinition):RepresentationDefinition{
    return {...representation,rootClassIds:arrayOrEmpty(representation.rootClassIds),nodeMappings:arrayOrEmpty(representation.nodeMappings).map(normalizeNodeMapping),edgeMappings:arrayOrEmpty(representation.edgeMappings).map(normalizeEdgeMapping),toolIds:arrayOrEmpty(representation.toolIds),layers:arrayOrEmpty(representation.layers).map(normalizeLayer),filters:arrayOrEmpty(representation.filters).map(normalizeFilter),annotations:stringRecord(representation.annotations)};
}

function normalizeTool(tool:EditorTool):EditorTool{
    const base={...tool,representationIds:arrayOrEmpty(tool.representationIds)};
    if(base.kind==='CREATE_NODE')return {...base,defaultValues:unknownRecord(base.defaultValues)};
    if(base.kind==='CREATE_EDGE')return {...base,sourceClassIds:arrayOrEmpty(base.sourceClassIds),targetClassIds:arrayOrEmpty(base.targetClassIds),semanticEdge:base.semanticEdge==null?undefined:{...base.semanticEdge,defaultValues:unknownRecord(base.semanticEdge.defaultValues),annotations:stringRecord(base.semanticEdge.annotations)}};
    return base;
}

function normalizePaletteGroup(group:PaletteGroup):PaletteGroup{return {...group,toolIds:arrayOrEmpty(group.toolIds)};}
function normalizePalette(palette:ToolPaletteDefinition|undefined):ToolPaletteDefinition{return {groups:arrayOrEmpty(palette?.groups).map(normalizePaletteGroup),tools:arrayOrEmpty(palette?.tools).map(normalizeTool)};}
function normalizeFormField(field:FormFieldDefinition):FormFieldDefinition{return {...field};}
function normalizeFormSection(section:FormSectionDefinition):FormSectionDefinition{return {...section,fields:arrayOrEmpty(section.fields).map(normalizeFormField)};}
function normalizeForm(form:FormDefinition):FormDefinition{return {...form,sections:arrayOrEmpty(form.sections).map(normalizeFormSection)};}

/**
 * Restores the complete frontend collection contract after JSON transport.
 * The backend may legitimately omit empty collections from older persisted documents; renderers must never
 * need defensive `?.length` checks for every editor-definition field.
 */
export function normalizeEditorDefinitionShape(model:EditorDefinitionModel):EditorDefinitionModel{
    const raw=model as EditorDefinitionModel & {
        representations?:RepresentationDefinition[]; palette?:ToolPaletteDefinition; forms?:FormDefinition[];
        capabilityManifest?:{capabilities?:EditorDefinitionModel['capabilityManifest']['capabilities']}; runtimePolicy?:EditorRuntimePolicy;
        codeGeneration?:CodeGenerationDefinition; annotations?:Record<string,string>;
    };
    const capabilities=arrayOrEmpty(raw.capabilityManifest?.capabilities);
    return {...model,representations:arrayOrEmpty(raw.representations).map(normalizeRepresentation),palette:normalizePalette(raw.palette),forms:arrayOrEmpty(raw.forms).map(normalizeForm),capabilityManifest:{capabilities:capabilities.length===0?['MODEL_EDITING']:capabilities},runtimePolicy:normalizeEditorRuntimePolicy(raw.runtimePolicy),codeGeneration:normalizeEditorCodeGeneration(raw.codeGeneration,capabilities.includes('CODE_GENERATION')),annotations:stringRecord(raw.annotations)};
}

export function normalizeEditorDefinition(model: EditorDefinitionModel, metamodel: MetaModel): EditorDefinitionModel {
    const detected = detectMetamodelRoots(metamodel);
    const normalized=normalizeEditorDefinitionShape(model);
    return {
        ...normalized,
        representations: normalized.representations.map(representation => ({
            ...representation,
            rootClassIds: representation.rootClassIds.length === 0 && detected.primaryClassId !== '' ? [detected.primaryClassId] : representation.rootClassIds
        }))
    };
}
