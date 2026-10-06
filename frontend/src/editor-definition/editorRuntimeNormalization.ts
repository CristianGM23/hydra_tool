import type { ModelValidationReport } from '../validation/types';
import type {
    EditorDefinitionCheckReport, EditorDefinitionCompilationResult, EditorRenderedNode, EditorSessionToolExecutionResponse,
    EditorSessionView, ExecutableEditorPlan, ExecutablePaletteGroup, ExecutableRepresentationPlan, FormDefinition
} from './types';
import { normalizeEditorCodeGeneration, normalizeEditorRuntimePolicy } from './editorDefinitionNormalization';

function arrayOrEmpty<T>(value:readonly T[]|null|undefined):T[]{return value==null?[]:[...value];}
function recordOrEmpty<T>(value:Record<string,T>|null|undefined):Record<string,T>{return value??{};}
function stringRecord(value:Record<string,string>|null|undefined):Record<string,string>{return value??{};}

function normalizeValidationReport(report:ModelValidationReport|null|undefined):ModelValidationReport|null{
    if(report==null)return null;
    return {...report,diagnostics:arrayOrEmpty(report.diagnostics)};
}

function normalizeForm(form:FormDefinition):FormDefinition{
    return {...form,sections:arrayOrEmpty(form.sections).map(section=>({...section,fields:arrayOrEmpty(section.fields)}))};
}

function normalizePaletteGroup(group:ExecutablePaletteGroup):ExecutablePaletteGroup{
    return {...group,tools:arrayOrEmpty(group.tools)};
}

function normalizeRepresentationPlan(plan:ExecutableRepresentationPlan):ExecutableRepresentationPlan{
    return {
        ...plan,
        rootClassIds:arrayOrEmpty(plan.rootClassIds),
        nodeMappingsByClassId:Object.fromEntries(Object.entries(recordOrEmpty(plan.nodeMappingsByClassId)).map(([key,value])=>[key,arrayOrEmpty(value)])),
        edgeMappingsByRelationId:Object.fromEntries(Object.entries(recordOrEmpty(plan.edgeMappingsByRelationId)).map(([key,value])=>[key,arrayOrEmpty(value)])),
        edgeMappingsBySemanticClassId:plan.edgeMappingsBySemanticClassId===undefined?undefined:Object.fromEntries(Object.entries(recordOrEmpty(plan.edgeMappingsBySemanticClassId)).map(([key,value])=>[key,arrayOrEmpty(value)])),
        toolsById:recordOrEmpty(plan.toolsById),
        paletteGroups:arrayOrEmpty(plan.paletteGroups).map(normalizePaletteGroup),
        layers:arrayOrEmpty(plan.layers).map(layer=>({...layer,nodeMappingIds:arrayOrEmpty(layer.nodeMappingIds),edgeMappingIds:arrayOrEmpty(layer.edgeMappingIds),toolIds:arrayOrEmpty(layer.toolIds),annotations:stringRecord(layer.annotations)})),
        filters:arrayOrEmpty(plan.filters).map(filter=>({...filter,mappingIds:arrayOrEmpty(filter.mappingIds),annotations:stringRecord(filter.annotations)})),
        annotations:stringRecord(plan.annotations)
    };
}

function normalizeCheckReport(report:EditorDefinitionCheckReport):EditorDefinitionCheckReport{
    return {...report,diagnostics:arrayOrEmpty(report.diagnostics)};
}

function normalizePlan(plan:ExecutableEditorPlan):ExecutableEditorPlan{
    const raw=plan as ExecutableEditorPlan & {representationsById?:Record<string,ExecutableRepresentationPlan>;formsById?:Record<string,FormDefinition>;formsByTargetClassId?:Record<string,FormDefinition>;capabilityManifest?:ExecutableEditorPlan['capabilityManifest']};
    const representationEntries=Object.entries(recordOrEmpty(raw.representationsById)).map(([id,value])=>[id,normalizeRepresentationPlan(value)] as const);
    const formEntries=Object.entries(recordOrEmpty(raw.formsById)).map(([id,value])=>[id,normalizeForm(value)] as const);
    const targetFormEntries=Object.entries(recordOrEmpty(raw.formsByTargetClassId)).map(([id,value])=>[id,normalizeForm(value)] as const);
    const capabilities=arrayOrEmpty(raw.capabilityManifest?.capabilities);
    return {
        ...plan,
        representationsById:Object.fromEntries(representationEntries),
        formsById:Object.fromEntries(formEntries),
        formsByTargetClassId:Object.fromEntries(targetFormEntries),
        capabilityManifest:{capabilities:capabilities.length===0?['MODEL_EDITING']:capabilities},
        runtimePolicy:normalizeEditorRuntimePolicy(plan.runtimePolicy),
        codeGeneration:normalizeEditorCodeGeneration(plan.codeGeneration,capabilities.includes('CODE_GENERATION')),
        annotations:stringRecord(plan.annotations)
    };
}

/** Normalizes compiled editor payloads at the network boundary, including responses persisted by older HYDRA builds. */
export function normalizeEditorCompilationResult(result:EditorDefinitionCompilationResult):EditorDefinitionCompilationResult{
    return {...result,plan:normalizePlan(result.plan),checkReport:normalizeCheckReport(result.checkReport)};
}

function normalizeRenderedNode(node:EditorRenderedNode):EditorRenderedNode{
    return {...node,attributes:node.attributes??{},styles:stringRecord(node.styles),containmentRelationIds:arrayOrEmpty(node.containmentRelationIds),availableAppearances:arrayOrEmpty(node.availableAppearances)};
}

/** Keeps session rendering total: an omitted empty collection from an old response never reaches React as undefined. */
export function normalizeEditorSessionView(view:EditorSessionView):EditorSessionView{
    const session=view.session;
    const model=session.model;
    return {
        ...view,
        session:{...session,openRepresentationIds:arrayOrEmpty(session.openRepresentationIds),model:{...model,rootElementIds:arrayOrEmpty(model.rootElementIds),elements:arrayOrEmpty(model.elements),relations:arrayOrEmpty(model.relations),annotations:stringRecord(model.annotations)},latestValidationReport:normalizeValidationReport(session.latestValidationReport),annotations:stringRecord(session.annotations)},
        nodes:arrayOrEmpty(view.nodes).map(normalizeRenderedNode),
        edges:arrayOrEmpty(view.edges).map(edge=>({...edge,styles:stringRecord(edge.styles)})),
        paletteGroups:arrayOrEmpty(view.paletteGroups).map(normalizePaletteGroup),
        toolStates:recordOrEmpty(view.toolStates),
        selectedForm:view.selectedForm==null?null:normalizeForm(view.selectedForm),
        representations:arrayOrEmpty(view.representations),
        validationReport:normalizeValidationReport(view.validationReport)
    };
}

export function normalizeEditorToolExecutionResponse(response:EditorSessionToolExecutionResponse):EditorSessionToolExecutionResponse{
    return {...response,view:normalizeEditorSessionView(response.view),messages:arrayOrEmpty(response.messages),validationReport:normalizeValidationReport(response.validationReport)};
}
