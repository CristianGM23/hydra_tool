import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EditorRuntimeSession } from './EditorRuntimeSession';
import type { EditorDefinitionCompilationResult, EditorSessionView, NodeCreationTool, OperationEditorTool } from './types';
import * as api from './api';

const apiMocks = vi.hoisted(() => ({
    applyEditorSessionQuickFix: vi.fn(),
    closeEditorSessionRepresentation: vi.fn(),
    createEditorSession: vi.fn(),
    createEditorSessionEdge: vi.fn(),
    createEditorSessionNode: vi.fn(),
    deleteEditorSessionElement: vi.fn(),
    deleteEditorSessionElements: vi.fn(),
    discardEditorSessionChanges: vi.fn(),
    executeEditorSessionTool: vi.fn(),
    importEmfEditorSession: vi.fn(),
    listEditorSessionQuickFixes: vi.fn(),
    loadEditorSession: vi.fn(),
    loadEditorSessionDiagram: vi.fn(),
    loadEditorSessionRepresentation: vi.fn(),
    previewEditorSessionQuickFix: vi.fn(),
    redoEditorSession: vi.fn(),
    reparentEditorSessionElement: vi.fn(),
    saveEditorSession: vi.fn(),
    saveEditorSessionDiagram: vi.fn(),
    setEditorSessionElementAppearance: vi.fn(),
    switchEditorSessionRepresentation: vi.fn(),
    undoEditorSession: vi.fn(),
    updateEditorSessionElement: vi.fn(),
    validateEditorSession: vi.fn()
}));

vi.mock('./api', () => apiMocks);

const metamodel = { schemaVersion:'2.1', id:'people', name:'People', classes:[{id:'class:Person',name:'Person',kind:'CLASS',attributes:[{id:'attribute:Person:name',name:'name'}],operations:[{id:'operation:Person:rename',name:'rename',returnType:null,returnMultiplicity:null,ordered:false,unique:true,parameters:[{id:'parameter:newName',name:'newName',type:{kind:'PRIMITIVE',targetId:'STRING',externalName:''},multiplicity:{lowerBound:1,upperBound:1},ordered:false,unique:true,annotations:{}}],exceptionTypes:[],behaviorKind:'COMMAND',annotations:{}}],annotations:{}}], enums:[], dataTypes:[], relations:[], annotations:{} } as never;

const renameTool:OperationEditorTool={kind:'INVOKE_OPERATION',id:'tool:rename',label:'Rename person',description:'Runs the configured command operation',icon:'',representationIds:['representation:main'],visibleExpressionId:'',enabledExpressionId:'',operationId:'operation:Person:rename'};
const createPersonTool:NodeCreationTool={kind:'CREATE_NODE',id:'tool:create-person',label:'Person',description:'Creates a person',icon:'',representationIds:['representation:main'],visibleExpressionId:'',enabledExpressionId:'',targetClassId:'class:Person',defaultValues:{},formId:'form:person'};
const result:EditorDefinitionCompilationResult={plan:{schemaVersion:'1.0',id:'people-editor:compiled',sourceEditorDefinitionId:'people-editor',sourceEtag:'etag',metamodelId:'people',name:'People editor',compiledAt:'2026-07-28T12:00:00Z',representationsById:{'representation:main':{id:'representation:main',name:'Main',kind:'DIAGRAM',rootClassIds:[],editable:true,nodeMappingsByClassId:{},edgeMappingsByRelationId:{},toolsById:{'tool:create-person':createPersonTool},paletteGroups:[{id:'group:elements',name:'Elements',order:0,initiallyCollapsed:false,tools:[createPersonTool]}],annotations:{}}},formsById:{},formsByTargetClassId:{},capabilityManifest:{capabilities:['MODEL_EDITING']},runtimePolicy:{validationTriggers:['MANUAL'],autosave:true,recoverLatestSession:true,publishEvents:true,eventFlowsEnabled:false,gamificationEnabled:false,maximumOpenRepresentations:2,operationTimeoutMillis:30000},annotations:{}},checkReport:{editorDefinitionId:'people-editor',diagnostics:[]},cacheHit:false};
const session:EditorSessionView={session:{schemaVersion:'1.0',id:'session-one',metamodelId:'people',editorId:'people-editor',sourceEditorEtag:'etag',activeRepresentationId:'representation:main',openRepresentationIds:['representation:main'],model:{schemaVersion:'1.0',id:'model-one',metamodelId:'people',rootElementIds:[],elements:[],relations:[],annotations:{}},latestValidationReport:null,revision:0,createdAt:'2026-07-28T12:00:00Z',updatedAt:'2026-07-28T12:00:00Z',annotations:{}},etag:'etag-1',representationId:'representation:main',representationName:'Main',representationKind:'DIAGRAM',editable:true,nodes:[],edges:[],paletteGroups:[{id:'group:elements',name:'Elements',order:0,initiallyCollapsed:false,tools:[createPersonTool]}],selectedForm:null,representations:[{id:'representation:main',name:'Main',kind:'DIAGRAM',editable:true,active:true,open:true}],validationReport:null,undoAvailable:false,redoAvailable:false};

describe('neutral editor runtime session',()=>{
 beforeEach(()=>{
  vi.resetAllMocks();
  vi.mocked(api.loadEditorSessionDiagram).mockRejectedValue(new Error('diagram unavailable'));
 });
 it('starts a persisted neutral session from the compiled plan',async()=>{vi.mocked(api.createEditorSession).mockResolvedValue(session);render(<EditorRuntimeSession result={result} metamodel={metamodel}/>);fireEvent.click(screen.getByRole('button',{name:'Create model'}));expect(await screen.findByLabelText('Model editor session')).toBeInTheDocument();expect(api.createEditorSession).toHaveBeenCalled();});
 it('executes a compiled node-creation tool',async()=>{vi.mocked(api.createEditorSession).mockResolvedValue(session);vi.mocked(api.createEditorSessionNode).mockResolvedValue({...session,nodes:[{elementId:'element:one',classId:'class:Person',className:'Person',mappingId:'node:person',representationName:'Person',shape:'RECTANGLE',label:'Person',width:200,height:100,attributes:{},styles:{}}]});render(<EditorRuntimeSession result={result} metamodel={metamodel}/>);fireEvent.click(screen.getByRole('button',{name:'Create model'}));await screen.findByLabelText('Model editor session');fireEvent.click(screen.getByRole('button',{name:/Person/}));await waitFor(()=>expect(api.createEditorSessionNode).toHaveBeenCalled());});

 it('switches to the selected imported representation and renders its returned projection',async()=>{const tablePlan={...result.plan.representationsById['representation:main'],id:'representation:table',name:'Imported table',kind:'TABLE' as const};const multi={...result,plan:{...result.plan,representationsById:{...result.plan.representationsById,'representation:table':tablePlan}}};const tableView={...session,representationId:'representation:table',representationName:'Imported table',representationKind:'TABLE' as const,representations:[{id:'representation:main',name:'Main',kind:'DIAGRAM' as const,editable:true,active:false,open:true},{id:'representation:table',name:'Imported table',kind:'TABLE' as const,editable:true,active:true,open:true}],nodes:[{elementId:'element:one',classId:'class:Person',className:'Person',mappingId:'node:table-person',representationName:'Table person',shape:'RECTANGLE',label:'Ada',width:200,height:100,attributes:{name:'Ada'},styles:{}}]};vi.mocked(api.createEditorSession).mockResolvedValue(session);vi.mocked(api.switchEditorSessionRepresentation).mockResolvedValue(tableView);render(<EditorRuntimeSession result={multi} metamodel={metamodel}/>);fireEvent.click(screen.getByRole('button',{name:'Create model'}));await screen.findByLabelText('Model editor session');fireEvent.change(screen.getByLabelText('Visual representation'),{target:{value:'representation:table'}});await waitFor(()=>expect(api.switchEditorSessionRepresentation).toHaveBeenCalledWith('people','people-editor','session-one','representation:table','etag-1'));expect(await screen.findByRole('table')).toBeInTheDocument();expect(screen.getByText('Ada')).toBeInTheDocument();});
 it('shows validation diagnostics and offers safe quick fixes inside the model editor',async()=>{
  const report={schemaVersion:'1.0' as const,id:'report:1',modelId:'model-one',metamodelId:'people',validationModelId:'validation:people',generatedAt:'2026-09-02T00:00:00Z',scope:{kind:'MODEL' as const},diagnostics:[{id:'diagnostic:progress',kind:'VIOLATION' as const,ruleId:'rule:progress',severity:'ERROR' as const,message:'Progress must be at most 100',location:{modelElementId:'element:one'},quickFixIds:['action:clamp']}],metrics:{evaluatedRules:1,skippedRules:0,durationMillis:1}};
  const node={elementId:'element:one',classId:'class:Person',className:'Person',mappingId:'node:person',representationName:'Person',shape:'RECTANGLE',label:'Ada',width:200,height:100,attributes:{name:'Ada'},styles:{}};
  const invalid={...session,nodes:[node],validationReport:report,session:{...session.session,latestValidationReport:report}};
  const resolvedReport={...report,diagnostics:[]};
  const fixed={...invalid,validationReport:resolvedReport,session:{...invalid.session,latestValidationReport:resolvedReport},etag:'etag-2',undoAvailable:true};
  vi.mocked(api.createEditorSession).mockResolvedValue(invalid);
  vi.mocked(api.loadEditorSession).mockResolvedValue(invalid);
  vi.mocked(api.listEditorSessionQuickFixes).mockResolvedValue([{actionId:'action:clamp',name:'Clamp progress to 100',description:'Sets progress to the maximum valid value.',enabled:true,parameters:[],transactionPolicy:'ATOMIC',undoPolicy:'REQUIRED'}]);
  vi.mocked(api.previewEditorSessionQuickFix).mockResolvedValue({diagnosticId:'diagnostic:progress',actionId:'action:clamp',previewDocument:invalid.session.model,changes:{createdElementIds:[],deletedElementIds:[],modifiedElementIds:['element:one'],attributeIds:[],relationIds:[],classIds:['class:Person'],validationChangeSet:{elementIds:['element:one'],classIds:['class:Person'],attributeIds:[],relationIds:[],operationIds:[],modelChanged:true}},messages:[]});
  vi.mocked(api.applyEditorSessionQuickFix).mockResolvedValue({view:fixed,diagnosticId:'diagnostic:progress',actionId:'action:clamp',resolved:true,messages:['Problem resolved']});
  render(<EditorRuntimeSession result={{...result,plan:{...result.plan,capabilityManifest:{capabilities:['MODEL_EDITING','VALIDATION_RUNTIME','QUICK_FIX_RUNTIME']},runtimePolicy:{...result.plan.runtimePolicy,validationTriggers:['ON_CHANGE','MANUAL']}}}} metamodel={metamodel}/>);
  fireEvent.click(screen.getByRole('button',{name:'Create model'}));
  await screen.findByLabelText('Model editor session');
  fireEvent.click(screen.getByRole('tab',{name:/Problems/}));
  fireEvent.click(screen.getByRole('button',{name:/Progress must be at most 100/}));
  await waitFor(()=>expect(api.listEditorSessionQuickFixes).toHaveBeenCalledWith('people','people-editor','session-one','diagnostic:progress'));
  expect(await screen.findByText('Clamp progress to 100')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Preview'}));
  await waitFor(()=>expect(api.previewEditorSessionQuickFix).toHaveBeenCalledWith('people','people-editor','session-one','diagnostic:progress','action:clamp',expect.objectContaining({selectionElementIds:['element:one']})));
  expect(await screen.findByText('0 created · 1 modified · 0 deleted')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Apply'}));
  await waitFor(()=>expect(api.applyEditorSessionQuickFix).toHaveBeenCalledWith('people','people-editor','session-one','diagnostic:progress','action:clamp',expect.objectContaining({selectionElementIds:['element:one']}),'etag-1'));
  await waitFor(()=>expect(screen.queryByText('Progress must be at most 100')).not.toBeInTheDocument());
  expect(screen.getByText('No problems detected.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'History'}));
  expect(await screen.findByRole('button',{name:'Undo'})).toBeEnabled();
 });
 it('collects operation parameters before executing a compiled operation tool',async()=>{const node={elementId:'element:one',classId:'class:Person',className:'Person',mappingId:'node:person',representationName:'Person',shape:'RECTANGLE',label:'Ada',width:200,height:100,attributes:{name:'Ada'},styles:{}};const operationResult={...result,plan:{...result.plan,representationsById:{'representation:main':{...result.plan.representationsById['representation:main'],toolsById:{'tool:rename':renameTool},paletteGroups:[{id:'group:operations',name:'Operations',order:0,initiallyCollapsed:false,tools:[renameTool]}]}}}};const active={...session,nodes:[node]};vi.mocked(api.createEditorSession).mockResolvedValue(active);vi.mocked(api.loadEditorSession).mockResolvedValue(active);vi.mocked(api.executeEditorSessionTool).mockResolvedValue({view:active,messages:['renamed']});render(<EditorRuntimeSession result={operationResult} metamodel={metamodel}/>);fireEvent.click(screen.getByRole('button',{name:'Create model'}));await screen.findByLabelText('Model editor session');fireEvent.click(screen.getByText('Ada'));await waitFor(()=>expect(api.loadEditorSession).toHaveBeenCalled());fireEvent.click(screen.getByRole('button',{name:/Rename person/}));fireEvent.change(screen.getByLabelText(/newName/),{target:{value:'Grace'}});fireEvent.click(screen.getByRole('button',{name:'Execute tool'}));await waitFor(()=>expect(api.executeEditorSessionTool).toHaveBeenCalledWith('people','people-editor','session-one','tool:rename',expect.objectContaining({arguments:{newName:'Grace'}}),'etag-1'));});
});
