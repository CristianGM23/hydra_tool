import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import { readValidationJson } from '../validation/http';
import type { EditorDefinitionModel,EditorDefinitionSaveResponse,EditorDefinitionSummary } from './types';
import { normalizeEditorDefinitionShape } from './editorDefinitionNormalization';
import { normalizeEditorCompilationResult, normalizeEditorSessionView, normalizeEditorToolExecutionResponse } from './editorRuntimeNormalization';
import { measureHydraDiagramAsyncOperation } from '../diagram/performanceTelemetry';
const route=(metamodelId:string):string=>`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/editors`;
export async function listEditorDefinitions(metamodelId:string):Promise<EditorDefinitionSummary[]>{return readValidationJson<EditorDefinitionSummary[]>(await backendFetch(route(metamodelId)));}
export async function loadEditorDefinition(metamodelId:string,editorId:string):Promise<{model:EditorDefinitionModel;etag:string}>{const response=await backendFetch(`${route(metamodelId)}/${encodeURIComponent(editorId)}`);return {model:normalizeEditorDefinitionShape(await readValidationJson<EditorDefinitionModel>(response)),etag:response.headers.get('ETag')??''};}
export async function createEditorDefinition(model:EditorDefinitionModel):Promise<EditorDefinitionSaveResponse>{const result=await readValidationJson<EditorDefinitionSaveResponse>(await backendFetch(route(model.metamodelId),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(model)}));return {...result,editorDefinition:normalizeEditorDefinitionShape(result.editorDefinition)};}
export async function replaceEditorDefinition(model:EditorDefinitionModel,etag:string):Promise<EditorDefinitionSaveResponse>{const headers:Record<string,string>={'Content-Type':'application/json'};if(etag!=='')headers['If-Match']=etag;const result=await readValidationJson<EditorDefinitionSaveResponse>(await backendFetch(`${route(model.metamodelId)}/${encodeURIComponent(model.id)}`,{method:'PUT',headers,body:JSON.stringify(model)}));return {...result,editorDefinition:normalizeEditorDefinitionShape(result.editorDefinition)};}

export async function loadEditorRuntime(metamodelId:string,editorId:string):Promise<import('./types').EditorDefinitionCompilationResult>{
    return normalizeEditorCompilationResult(await readValidationJson<import('./types').EditorDefinitionCompilationResult>(await backendFetch(`${route(metamodelId)}/${encodeURIComponent(editorId)}/runtime`)));
}
export async function compileEditorRuntime(metamodelId:string,editorId:string):Promise<import('./types').EditorDefinitionCompilationResult>{
    return normalizeEditorCompilationResult(await readValidationJson<import('./types').EditorDefinitionCompilationResult>(await backendFetch(`${route(metamodelId)}/${encodeURIComponent(editorId)}/runtime/compile`,{method:'POST'})));
}

const runtimeSessionRoute=(metamodelId:string,editorId:string):string=>`${route(metamodelId)}/${encodeURIComponent(editorId)}/runtime/sessions`;
async function sessionJson(response:Response):Promise<import('./types').EditorSessionView>{return normalizeEditorSessionView(await readValidationJson<import('./types').EditorSessionView>(response));}
export async function createEditorSession(metamodelId:string,editorId:string,request:import('./types').EditorSessionCreateRequest):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(runtimeSessionRoute(metamodelId,editorId),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));}
export async function importEmfEditorSession(metamodelId:string,editorId:string,request:{sessionId:string;sourceModelId:string;representationId:string}):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/import-emf`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));}
export async function loadEditorSession(metamodelId:string,editorId:string,sessionId:string,selectedElementId=''):Promise<import('./types').EditorSessionView>{const query=selectedElementId===''?'':`?selectedElementId=${encodeURIComponent(selectedElementId)}`;return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}${query}`));}
export async function switchEditorSessionRepresentation(metamodelId:string,editorId:string,sessionId:string,representationId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/representation`,{method:'POST',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({representationId})}));}
export async function createEditorSessionNode(metamodelId:string,editorId:string,sessionId:string,toolId:string,representationId:string,etag:string,values:Record<string,unknown>={},containerElementId='',containmentRelationId=''):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/tools/${encodeURIComponent(toolId)}/nodes`,{method:'POST',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({representationId,containerElementId,containmentRelationId,values})}));}
export async function createEditorSessionEdge(metamodelId:string,editorId:string,sessionId:string,toolId:string,representationId:string,sourceElementId:string,targetElementId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/tools/${encodeURIComponent(toolId)}/edges`,{method:'POST',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({representationId,sourceElementId,targetElementId})}));}
export async function updateEditorSessionElement(metamodelId:string,editorId:string,sessionId:string,elementId:string,representationId:string,formId:string,values:Record<string,unknown>,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/elements/${encodeURIComponent(elementId)}`,{method:'PUT',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({representationId,formId,values})}));}
export async function setEditorSessionElementAppearance(metamodelId:string,editorId:string,sessionId:string,elementId:string,representationId:string,mappingId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/elements/${encodeURIComponent(elementId)}/appearance`,{method:'PUT',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({representationId,mappingId})}));}
export async function deleteEditorSessionElement(metamodelId:string,editorId:string,sessionId:string,elementId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/elements/${encodeURIComponent(elementId)}`,{method:'DELETE',headers:{'If-Match':etag}}));}
export async function deleteEditorSessionElements(metamodelId:string,editorId:string,sessionId:string,elementIds:string[],etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/elements/delete`,{method:'POST',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({elementIds})}));}
export async function reparentEditorSessionElement(metamodelId:string,editorId:string,sessionId:string,elementId:string,representationId:string,containerElementId:string,containmentRelationId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/elements/${encodeURIComponent(elementId)}/containment`,{method:'PUT',headers:{'Content-Type':'application/json','If-Match':etag},body:JSON.stringify({representationId,containerElementId,containmentRelationId})}));}
export async function undoEditorSession(metamodelId:string,editorId:string,sessionId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/undo`,{method:'POST',headers:{'If-Match':etag}}));}
export async function redoEditorSession(metamodelId:string,editorId:string,sessionId:string,etag:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/redo`,{method:'POST',headers:{'If-Match':etag}}));}

export async function executeEditorSessionTool(metamodelId:string,editorId:string,sessionId:string,toolId:string,request:import('./types').EditorSessionToolExecutionRequest,etag:string):Promise<import('./types').EditorSessionToolExecutionResponse>{
    const headers:Record<string,string>={'Content-Type':'application/json'}; if(etag!=='') headers['If-Match']=etag;
    return normalizeEditorToolExecutionResponse(await readValidationJson<import('./types').EditorSessionToolExecutionResponse>(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/tools/${encodeURIComponent(toolId)}/execute`,{method:'POST',headers,body:JSON.stringify(request)})));
}
export async function loadEditorSessionDiagram(metamodelId:string,editorId:string,sessionId:string,representationId:string):Promise<import('./types').EditorSessionDiagramLoadResult>{
    return readValidationJson<import('./types').EditorSessionDiagramLoadResult>(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/diagram?representationId=${encodeURIComponent(representationId)}`));
}
export async function saveEditorSessionDiagram(metamodelId:string,editorId:string,sessionId:string,diagram:import('./types').EditorSessionDiagramDocument,etag:string):Promise<import('./types').EditorSessionDiagramLoadResult>{
    const headers:Record<string,string>={'Content-Type':'application/json'}; if(etag!=='') headers['If-Match']=etag;
    return measureHydraDiagramAsyncOperation(`model-runtime:${sessionId}`, 'commit', async () =>
        readValidationJson<import('./types').EditorSessionDiagramLoadResult>(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/diagram`,{method:'PUT',headers,body:JSON.stringify(diagram)})),
        { nodeCount: diagram.nodes.length, edgeCount: diagram.edges.length });
}

export async function loadEditorSessionRepresentation(metamodelId:string,editorId:string,sessionId:string,representationId:string,selectedElementId=''):Promise<import('./types').EditorSessionView>{
    const query=selectedElementId===''?'':`?selectedElementId=${encodeURIComponent(selectedElementId)}`;
    return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/representations/${encodeURIComponent(representationId)}${query}`));
}
export async function closeEditorSessionRepresentation(metamodelId:string,editorId:string,sessionId:string,representationId:string,etag:string):Promise<import('./types').EditorSessionView>{
    const headers:Record<string,string>={}; if(etag!=='')headers['If-Match']=etag;
    return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/representations/${encodeURIComponent(representationId)}`,{method:'DELETE',headers}));
}
export async function validateEditorSession(metamodelId:string,editorId:string,sessionId:string,mode:import('./types').ValidationRunMode,etag:string):Promise<import('./types').EditorSessionView>{
    const headers:Record<string,string>={};if(etag!=='')headers['If-Match']=etag;
    return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/validation?mode=${encodeURIComponent(mode)}`,{method:'POST',headers}));
}
export async function listEditorSessionQuickFixes(metamodelId:string,editorId:string,sessionId:string,diagnosticId:string):Promise<import('./types').QuickFixDescriptor[]>{
    return readValidationJson<import('./types').QuickFixDescriptor[]>(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/problems/${encodeURIComponent(diagnosticId)}/quick-fixes`));
}
export async function previewEditorSessionQuickFix(metamodelId:string,editorId:string,sessionId:string,diagnosticId:string,actionId:string,request:import('./types').QuickFixExecutionRequest):Promise<import('./types').QuickFixPreviewResult>{
    return readValidationJson<import('./types').QuickFixPreviewResult>(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/problems/${encodeURIComponent(diagnosticId)}/quick-fixes/${encodeURIComponent(actionId)}/preview`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function applyEditorSessionQuickFix(metamodelId:string,editorId:string,sessionId:string,diagnosticId:string,actionId:string,request:import('./types').QuickFixExecutionRequest,etag:string):Promise<import('./types').EditorSessionQuickFixResult>{
    const headers:Record<string,string>={'Content-Type':'application/json'};if(etag!=='')headers['If-Match']=etag;
    return readValidationJson<import('./types').EditorSessionQuickFixResult>(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/problems/${encodeURIComponent(diagnosticId)}/quick-fixes/${encodeURIComponent(actionId)}/apply`,{method:'POST',headers,body:JSON.stringify(request)}));
}
export async function saveEditorSession(metamodelId:string,editorId:string,sessionId:string,etag:string):Promise<import('./types').EditorSessionView>{const headers:Record<string,string>={};if(etag!=='')headers['If-Match']=etag;return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/save`,{method:'POST',headers}));}
export async function discardEditorSessionChanges(metamodelId:string,editorId:string,sessionId:string):Promise<import('./types').EditorSessionView>{return sessionJson(await backendFetch(`${runtimeSessionRoute(metamodelId,editorId)}/${encodeURIComponent(sessionId)}/discard`,{method:'POST'}));}

export async function generateStandaloneEditor(request:unknown):Promise<{blob:Blob;fileName:string;sha256:string}>{
    const response=await backendFetch('/api/standalone-editors/generate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)});
    if(!response.ok)throw await readActionableApiError(response);
    const disposition=response.headers.get('Content-Disposition')??'';
    const match=/filename\*?=(?:UTF-8''|")?([^";]+)/i.exec(disposition);
    return {blob:await response.blob(),fileName:match===null?'hydra-standalone-editor.zip':decodeURIComponent(match[1].replaceAll('"','')),sha256:response.headers.get('X-HYDRA-Standalone-SHA256')??''};
}

const visualImportRoute=(metamodelId:string,kind:'sirius'|'sirius-web'|'gmf'|'drawio'):string=>`${route(metamodelId)}/imports/${kind}`;
export interface VisualImportAssociationInput { mode:import('./types').VisualMetamodelAssociationMode; metamodelResources?:File[]; }
function visualImportBody(resources:{name:string;files:File[]}[],association?:VisualImportAssociationInput):FormData{const body=new FormData();resources.forEach(group=>group.files.forEach(file=>body.append(group.name,file)));association?.metamodelResources?.forEach(file=>body.append('metamodelResources',file));return body;}
function visualImportUrl(metamodelId:string,kind:'sirius'|'sirius-web'|'gmf'|'drawio',association?:VisualImportAssociationInput):string{const base=visualImportRoute(metamodelId,kind);return `${base}?associationMode=${encodeURIComponent(association?.mode??'EXISTING')}`;}
export async function importSiriusEditorDefinition(metamodelId:string,odesign:File,javaSources:File[],association?:VisualImportAssociationInput):Promise<import('./types').VisualDefinitionImportResult>{
    const body=visualImportBody([{name:'odesign',files:[odesign]},{name:'javaSources',files:javaSources}],association);
    return readValidationJson<import('./types').VisualDefinitionImportResult>(await backendFetch(visualImportUrl(metamodelId,'sirius',association),{method:'POST',body}));
}
export async function importSiriusWebEditorDefinition(metamodelId:string,resources:File[],association?:VisualImportAssociationInput):Promise<import('./types').VisualDefinitionImportResult>{
    const body=visualImportBody([{name:'resources',files:resources}],association);
    return readValidationJson<import('./types').VisualDefinitionImportResult>(await backendFetch(visualImportUrl(metamodelId,'sirius-web',association),{method:'POST',body}));
}
export async function importGmfEditorDefinition(metamodelId:string,resources:File[],association?:VisualImportAssociationInput):Promise<import('./types').VisualDefinitionImportResult>{
    const body=visualImportBody([{name:'resources',files:resources}],association);
    return readValidationJson<import('./types').VisualDefinitionImportResult>(await backendFetch(visualImportUrl(metamodelId,'gmf',association),{method:'POST',body}));
}
export async function importDrawioEditorDefinition(metamodelId:string,resources:File[],association?:VisualImportAssociationInput):Promise<import('./types').VisualDefinitionImportResult>{
    const body=visualImportBody([{name:'resources',files:resources}],association);
    return readValidationJson<import('./types').VisualDefinitionImportResult>(await backendFetch(visualImportUrl(metamodelId,'drawio',association),{method:'POST',body}));
}
export async function commitVisualImportAssociations(metamodelId:string,result:import('./types').VisualDefinitionImportResult):Promise<import('./types').VisualImportCommitResult>{
    return readValidationJson<import('./types').VisualImportCommitResult>(await backendFetch(`${route(metamodelId)}/imports/commit-associations`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({editorDefinition:result.editorDefinition,associatedMetamodel:result.associatedMetamodel,metamodelAssociation:result.metamodelAssociation,validationDetails:result.validationDetails})}));
}

