import { HydraButton } from '../design-system/HydraButton';
import type { Connection } from '@xyflow/react';
import { useCallback, useMemo, useRef, useState } from 'react';
import { GamificationFeedbackCenter } from './GamificationFeedbackCenter';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { HydraResizablePanel, HydraSelect } from '../design-system';
import { ModelCodeGenerationDialog } from '../model-codegen/ModelCodeGenerationDialog';
import { generateEditorSessionCode, listEditorSessionCodeGenerationTargets, previewEditorSessionCode } from '../model-codegen/api';
import { indexBy } from '../utils/indexBy';
import { useHydraDialogs } from '../hooks/useHydraDialogs';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import type { MetaModel, MetaOperation, MetaParameter } from '../metamodel-designer/types';
import { isSameOrSubtype } from './metamodelRootDetection';
import {
    applyEditorSessionQuickFix, closeEditorSessionRepresentation, createEditorSession, createEditorSessionEdge, createEditorSessionNode, deleteEditorSessionElement, deleteEditorSessionElements,
    discardEditorSessionChanges, executeEditorSessionTool, importEmfEditorSession, listEditorSessionQuickFixes, loadEditorSession, loadEditorSessionDiagram,
    loadEditorSessionRepresentation, previewEditorSessionQuickFix, redoEditorSession, reparentEditorSessionElement, saveEditorSession, saveEditorSessionDiagram, switchEditorSessionRepresentation,
    setEditorSessionElementAppearance, undoEditorSession, updateEditorSessionElement, validateEditorSession
} from './api';
import { EditorRuntimeRepresentation } from './EditorRuntimeRepresentation';
import './editor-definition.css';
import type {
    EditorDefinitionCompilationResult, EditorSessionDiagramDocument, EditorSessionDiagramNodeView, EditorSessionToolExecutionResponse,
    EditorSessionView, EditorTool, FormFieldDefinition, QuickFixDescriptor
} from './types';

function createSessionSuffix(): string { return Date.now().toString(36); }

interface Props {
    result: EditorDefinitionCompilationResult;
    metamodel: MetaModel;
    onError?: (message: string) => void;
    onMessage?: (message: string) => void;
    initialSourceModelId?: string;
}

/** Sesión neutral y gráfica ejecutada sobre un ExecutableEditorPlan. */
export function EditorRuntimeSession({ result, metamodel, onError = () => undefined, onMessage = () => undefined, initialSourceModelId = '' }: Props) {
    const { t } = useHydraLanguage();
    const { requestDecision, dialogs } = useHydraDialogs();
    const representationOptions = useMemo(() => Object.values(result.plan.representationsById).map(item => ({ value:item.id, label:item.name, description:`${t(item.kind)} · ${Object.keys(item.toolsById ?? {}).length} ${t('tools')}` })), [result.plan.representationsById,t]);
    const [launchRepresentationId,setLaunchRepresentationId]=useState(()=>representationOptions[0]?.value??'');
    const [secondaryCandidateId,setSecondaryCandidateId]=useState('');
    const [view, setView] = useState<EditorSessionView | null>(null);
    const [selectedIds, setSelectedIds] = useState<string[]>([]);
    const [draft, setDraft] = useState<Record<string, unknown>>({});
    const [sourceModelId, setSourceModelId] = useState(initialSourceModelId);
    const [busy, setBusy] = useState(false);
    const [diagram, setDiagram] = useState<EditorSessionDiagramDocument | null>(null);
    const [diagramEtag, setDiagramEtag] = useState('');
    const [toolOutcome, setToolOutcome] = useState<EditorSessionToolExecutionResponse | null>(null);
    const [pendingTool, setPendingTool] = useState<EditorTool | null>(null);
    const [toolArguments, setToolArguments] = useState<Record<string, unknown>>({});
    const [actionArgumentsSource, setActionArgumentsSource] = useState('{}');
    const [secondaryView,setSecondaryView]=useState<EditorSessionView|null>(null);
    const [secondaryDiagram,setSecondaryDiagram]=useState<EditorSessionDiagramDocument|null>(null);
    const [selectedDiagnosticId,setSelectedDiagnosticId]=useState('');
    const [quickFixes,setQuickFixes]=useState<QuickFixDescriptor[]>([]);
    const [quickFixPreview,setQuickFixPreview]=useState('');
    const [codeGenerationOpen,setCodeGenerationOpen]=useState(false);
    const [codeGenerationSelectionIds,setCodeGenerationSelectionIds]=useState<string[]>([]);
    const [inspectorTab,setInspectorTab]=useState<'PROPERTIES'|'OPERATIONS'|'PROBLEMS'>('PROPERTIES');
    const selectedIdsRef=useRef<string[]>([]);
    const selectionLoadKeyRef=useRef('');
    const runtimeCodeGeneration = result.plan.codeGeneration ?? { enabled:false, exposeInRuntime:false, targetIds:[], defaultTargetId:'java', outputNamespace:'generated.model', includeTraceability:true, defaultFeatures:{generateTests:false,generateGettersSetters:false,generateComparison:false,generateStringRepresentation:false,generateEquality:false}, targetFeatureOverrides:{}, annotations:{} };
    const representation = view === null ? null : result.plan.representationsById[view.representationId];
    const tools = useMemo(() => representation === null ? [] : Object.values(representation.toolsById).filter(tool=>view?.toolStates?.[tool.id]?.visible??true), [representation,view?.toolStates]);
    const viewNodeById = useMemo(() => indexBy(view?.nodes ?? [], node => node.elementId), [view?.nodes]);
    const selectedNode = viewNodeById.get(selectedIds[0] ?? '') ?? null;
    const selectedClassOperations = useMemo(() => {
        if (selectedNode === null) return [] as MetaOperation[];
        const classById = new Map(metamodel.classes.map(value => [value.id, value]));
        const parentsByClass = new Map<string,string[]>();
        for (const relation of metamodel.relations) {
            if (relation.kind !== 'INHERITANCE' && relation.kind !== 'IMPLEMENTATION') continue;
            parentsByClass.set(relation.sourceTypeId, [...(parentsByClass.get(relation.sourceTypeId) ?? []), relation.targetTypeId]);
        }
        const queue=[selectedNode.classId]; const visited=new Set<string>(); const operations=new Map<string,MetaOperation>();
        while(queue.length>0){const classId=queue.shift()!;if(visited.has(classId))continue;visited.add(classId);const metaClass=classById.get(classId);for(const operation of metaClass?.operations??[])if(!operations.has(operation.id))operations.set(operation.id,operation);queue.push(...(parentsByClass.get(classId)??[]));}
        return [...operations.values()];
    }, [metamodel.classes,metamodel.relations,selectedNode]);
    const operationToolsByOperationId = useMemo(() => {
        const resultById=new Map<string,EditorTool>();
        for(const tool of Object.values(representation?.toolsById??{})) if(tool.kind==='INVOKE_OPERATION') resultById.set(tool.operationId,tool);
        return resultById;
    },[representation]);

    function emptyDiagram(next:EditorSessionView):EditorSessionDiagramDocument {
        return {schemaVersion:'1.0',id:next.session.id,metamodelId:next.session.metamodelId,editorId:next.session.editorId,
            representationId:next.representationId,nodes:next.nodes.map((node,index)=>({elementId:node.elementId,x:80+(index%4)*230,y:70+Math.floor(index/4)*150,width:node.width,height:node.height})),edges:[]};
    }

    function synchronizeDiagram(current:EditorSessionDiagramDocument,next:EditorSessionView):EditorSessionDiagramDocument {
        const existing=new Map(current.nodes.map(node=>[node.elementId,node]));
        const nodes=next.nodes.map((node,index)=>existing.get(node.elementId)??{elementId:node.elementId,x:80+(index%4)*230,y:70+Math.floor(index/4)*150,width:node.width,height:node.height});
        const edges=next.edges.map(edge=>current.edges.find(item=>item.relationInstanceId===edge.relationInstanceId)??{relationInstanceId:edge.relationInstanceId,points:[]});
        const sameNodes=current.nodes.length===nodes.length&&nodes.every((node,index)=>node===current.nodes[index]);
        const sameEdges=current.edges.length===edges.length&&edges.every((edge,index)=>edge===current.edges[index]);
        if(current.representationId===next.representationId&&sameNodes&&sameEdges)return current;
        return {...current,representationId:next.representationId,nodes,edges};
    }

    async function loadDiagram(next:EditorSessionView):Promise<void>{
        try{
            const loaded=await loadEditorSessionDiagram(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,next.session.id,next.representationId);
            setDiagram(synchronizeDiagram(loaded.diagram,next)); setDiagramEtag(loaded.etag);
        }catch{ setDiagram(emptyDiagram(next)); setDiagramEtag(''); }
    }

    async function run(operation: () => Promise<EditorSessionView>, message: string, reloadDiagram=false): Promise<EditorSessionView|null> {
        setBusy(true);
        try {
            const next = await operation();
            setView(next);
            setSelectedIds(ids => {
                const filtered=ids.filter(id => next.nodes.some(node => node.elementId === id));
                selectedIdsRef.current=filtered;
                return sameStringList(ids,filtered)?ids:filtered;
            });
            setDiagram(current=>current===null?emptyDiagram(next):synchronizeDiagram(current,next));
            if(reloadDiagram) void loadDiagram(next);
            onMessage(message);
            return next;
        } catch (exception) {
            onError(exception instanceof Error ? exception.message : t('The model editor operation failed.'));
            return null;
        } finally { setBusy(false); }
    }

    async function deleteSelection(): Promise<void> {
        if(view===null||selectedIds.length===0)return;
        const accepted=await requestDecision({title:selectedIds.length===1?t('Delete selected element?'):t('Delete selected elements?'),description:selectedIds.length===1?t('The selected model element and contained descendants will be deleted.'):t('The selected model elements and their contained descendants will be deleted in one atomic operation.'),confirmLabel:t('Delete'),cancelLabel:t('Cancel'),danger:true});
        if(!accepted)return;
        const count=selectedIds.length;
        const next=await run(()=>deleteEditorSessionElements(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,selectedIds,view.etag),count===1?t('Element deleted'):t('Selected elements deleted'));
        if(next!==null){selectedIdsRef.current=[];selectionLoadKeyRef.current='';setSelectedIds([]);setDraft({});}
    }

    async function startBlank(): Promise<void> {
        const suffix = createSessionSuffix();
        await run(() => createEditorSession(result.plan.metamodelId, result.plan.sourceEditorDefinitionId, {
            sessionId: `session-${suffix}`, modelId: `model-${suffix}`, modelName: t('Untitled model'),
            representationId: launchRepresentationId || representationOptions[0]?.value || '', rootClassId: '', rootAttributes: {}
        }), t('Model session created'),true);
    }

    async function importEmf(): Promise<void> {
        if (sourceModelId.trim() === '') { onError(t('Enter the saved model ID to open.')); return; }
        const suffix = createSessionSuffix();
        await run(() => importEmfEditorSession(result.plan.metamodelId, result.plan.sourceEditorDefinitionId, {
            sessionId: `session-${suffix}`, sourceModelId: sourceModelId.trim(), representationId: launchRepresentationId || representationOptions[0]?.value || ''
        }), t('Model opened with the selected representation'),true);
    }

    function selectNodes(elementIds:string[]):void {
        if(view===null)return;
        const unique=[...new Set(elementIds)].filter(id=>view.nodes.some(node=>node.elementId===id));
        const changed=!sameStringList(selectedIdsRef.current,unique);
        selectedIdsRef.current=unique;
        setSelectedIds(current=>sameStringList(current,unique)?current:unique);
        const primary=unique[0]??'';
        const nextDraft=view.nodes.find(node=>node.elementId===primary)?.attributes??{};
        setDraft(current=>sameRecord(current,nextDraft)?current:nextDraft);
        if(!changed)return;
        if(primary===''){selectionLoadKeyRef.current='';return;}
        setInspectorTab('PROPERTIES');
        const loadKey=`${view.session.id}:${view.etag}:${primary}`;
        if(selectionLoadKeyRef.current===loadKey)return;
        selectionLoadKeyRef.current=loadKey;
        void run(()=>loadEditorSession(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,primary),t('Generated form loaded'))
            .then(next=>{if(next===null&&selectionLoadKeyRef.current===loadKey)selectionLoadKeyRef.current='';});
    }

    async function selectNode(elementId: string): Promise<void> {
        if (view === null) return;
        const currentIds=selectedIdsRef.current;
        const nextIds = currentIds.includes(elementId) ? currentIds.filter(id => id !== elementId) : [...currentIds, elementId];
        selectedIdsRef.current=nextIds;
        setSelectedIds(nextIds);
        if(nextIds.length>0)setInspectorTab('PROPERTIES');
        const primary = nextIds[0] ?? '';
        setDraft(viewNodeById.get(primary)?.attributes ?? {});
        if (primary !== '') await run(() => loadEditorSession(result.plan.metamodelId, result.plan.sourceEditorDefinitionId, view.session.id, primary), t('Generated form loaded'));
    }

    function operationFor(tool:EditorTool):MetaOperation|null {
        if(tool.kind!=='INVOKE_OPERATION') return null;
        return metamodel.classes.flatMap(value=>value.operations).find(value=>value.id===tool.operationId)??null;
    }

    async function submitExecutableTool(tool:EditorTool,argumentsValue:Record<string,unknown>):Promise<void>{
        if(view===null)return;
        setBusy(true);
        try{
            const outcome=await executeEditorSessionTool(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,tool.id,
                {representationId:view.representationId,targetElementId:selectedIds[0]??'',selectionElementIds:selectedIds,arguments:argumentsValue},view.etag);
            setToolOutcome(outcome); setView(outcome.view); setDiagram(current=>current===null?emptyDiagram(outcome.view):synchronizeDiagram(current,outcome.view));
            setPendingTool(null); setToolArguments({}); setActionArgumentsSource('{}');
            onMessage(outcome.messages.join(' · ')||`${tool.label} executed`);
        }catch(exception){onError(exception instanceof Error?exception.message:'The compiled tool failed');}
        finally{setBusy(false);}
    }

    async function executeTool(tool: EditorTool): Promise<void> {
        if (view === null) return;
        if (tool.kind === 'CREATE_NODE') {
            const containerNode = selectedIds.length === 1 ? view.nodes.find(node => node.elementId === selectedIds[0] && node.container) ?? null : null;
            const containment = containerNode === null ? null : metamodel.relations.find(relation => (containerNode.containmentRelationIds ?? []).includes(relation.id)
                && relation.kind === 'CONTAINMENT' && isSameOrSubtype(metamodel, tool.targetClassId, relation.targetTypeId)) ?? null;
            if (containerNode !== null && containment === null) {
                onError(`The selected container does not accept ${tool.label} elements.`);
                return;
            }
            await run(() => createEditorSessionNode(result.plan.metamodelId, result.plan.sourceEditorDefinitionId,
                view.session.id, tool.id, view.representationId, view.etag, {}, containerNode?.elementId ?? '', containment?.id ?? ''),
            containerNode === null ? `${tool.label} executed` : `${tool.label} created inside ${containerNode.label}`);
            return;
        }
        if (tool.kind === 'CREATE_EDGE') {
            if (selectedIds.length !== 2) { onError('Select a source and a target element before creating a relationship.'); return; }
            await run(() => createEditorSessionEdge(result.plan.metamodelId, result.plan.sourceEditorDefinitionId,
                view.session.id, tool.id, view.representationId, selectedIds[0], selectedIds[1], view.etag), `${tool.label} executed`);
            return;
        }
        if(tool.kind==='VALIDATE'){await submitExecutableTool(tool,{});return;}
        setPendingTool(tool);
        if(tool.kind==='INVOKE_OPERATION'){
            setInspectorTab('OPERATIONS');
            const operation=operationFor(tool);
            setToolArguments(Object.fromEntries((operation?.parameters??[]).map(parameter=>[parameter.name,parameter.type.kind==='PRIMITIVE'&&parameter.type.targetId==='BOOLEAN'?false:''])));
        }else{setToolArguments({});setActionArgumentsSource('{}');}
    }

    async function executePendingTool():Promise<void>{
        if(pendingTool===null)return;
        if(pendingTool.kind==='RUN_ACTION'){
            try{const parsed=JSON.parse(actionArgumentsSource) as unknown;if(parsed===null||Array.isArray(parsed)||typeof parsed!=='object')throw new Error('Action arguments must be a JSON object');await submitExecutableTool(pendingTool,parsed as Record<string,unknown>);}
            catch(exception){onError(exception instanceof Error?exception.message:'Action arguments are invalid');}
            return;
        }
        await submitExecutableTool(pendingTool,toolArguments);
    }

    async function connect(connection:Connection):Promise<void>{
        if(view===null||representation===null||connection.source===null||connection.target===null)return;
        const source=view.nodes.find(node=>node.elementId===connection.source); const target=view.nodes.find(node=>node.elementId===connection.target);
        const tool=Object.values(representation.toolsById).find(candidate=>candidate.kind==='CREATE_EDGE'&&
            (candidate.sourceClassIds.length===0||candidate.sourceClassIds.includes(source?.classId??''))&&
            (candidate.targetClassIds.length===0||candidate.targetClassIds.includes(target?.classId??'')));
        if(tool===undefined){onError('No compiled relationship tool accepts the selected source and target classes.');return;}
        await run(()=>createEditorSessionEdge(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,tool.id,
            view.representationId,connection.source!,connection.target!,view.etag),`${tool.label} executed`);
    }

    const saveNodePosition=useCallback(async(elementId:string,x:number,y:number,width:number,height:number,containerElementId:string):Promise<void>=>{
        if(view===null||diagram===null)return;
        setBusy(true);
        try{
            let effectiveView=view;
            const element=view.nodes.find(node=>node.elementId===elementId);
            if(element===undefined)throw new Error(`Model element was not found: ${elementId}`);
            const currentContainment=view.edges.find(edge=>edge.targetElementId===elementId&&metamodel.relations.some(relation=>relation.id===edge.relationId&&relation.kind==='CONTAINMENT'));
            const currentContainerId=currentContainment?.sourceElementId??'';
            if(currentContainerId!==containerElementId){
                let relationId='';
                if(containerElementId!==''){
                    const container=view.nodes.find(node=>node.elementId===containerElementId);
                    if(container===undefined||container.container!==true)throw new Error('The selected visual target is not a container.');
                    const accepted=new Set(container.containmentRelationIds??[]);
                    const relation=metamodel.relations.find(candidate=>candidate.kind==='CONTAINMENT'&&accepted.has(candidate.id)&&
                        isSameOrSubtype(metamodel,container.classId,candidate.sourceTypeId)&&isSameOrSubtype(metamodel,element.classId,candidate.targetTypeId));
                    if(relation===undefined)throw new Error(`No containment relation accepts ${element.className} inside ${container.className}.`);
                    relationId=relation.id;
                }
                effectiveView=await reparentEditorSessionElement(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,
                    elementId,view.representationId,containerElementId,relationId,view.etag);
                setView(effectiveView);
                onMessage(containerElementId===''?'Element moved to the diagram root':'Element contained by the selected visual container');
            }
            const next={...diagram,nodes:diagram.nodes.map(node=>node.elementId===elementId?{...node,x,y,width,height}:node)};
            setDiagram(next);
            const saved=await saveEditorSessionDiagram(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,effectiveView.session.id,next,diagramEtag);
            setDiagram(saved.diagram);setDiagramEtag(saved.etag);
        }catch(exception){onError(exception instanceof Error?exception.message:t('The visual layout could not be saved.'));}
        finally{setBusy(false);}
    },[diagram,diagramEtag,metamodel,onError,onMessage,result.plan.metamodelId,result.plan.sourceEditorDefinitionId,t,view]);

    async function saveDiagramNodes(nodes:EditorSessionDiagramNodeView[]):Promise<void>{
        if(view===null||diagram===null||busy)return;
        const previous=diagram;
        const next={...diagram,nodes};
        setDiagram(next);setBusy(true);
        try{
            const saved=await saveEditorSessionDiagram(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,next,diagramEtag);
            setDiagram(saved.diagram);setDiagramEtag(saved.etag);
            onMessage(t('Visual layout saved'));
        }catch(exception){setDiagram(previous);onError(exception instanceof Error?exception.message:t('The visual layout could not be saved.'));}
        finally{setBusy(false);}
    }

    async function changeRepresentation(representationId:string):Promise<void>{
        if(view===null||representationId===''||representationId===view.representationId)return;
        const next=await run(()=>switchEditorSessionRepresentation(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,representationId,view.etag),`${t('Representation changed to')} ${result.plan.representationsById[representationId]?.name??representationId}`,true);
        if(next!==null&&secondaryView?.representationId===representationId){setSecondaryView(null);setSecondaryDiagram(null);}
    }

    async function openAlongside(representationId:string):Promise<void>{
        if(view===null)return;
        setBusy(true);
        try{
            const next=await loadEditorSessionRepresentation(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,representationId,selectedIds[0]??'');
            setSecondaryView(next);
            try{const loaded=await loadEditorSessionDiagram(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,representationId);setSecondaryDiagram(synchronizeDiagram(loaded.diagram,next));}
            catch{setSecondaryDiagram(emptyDiagram(next));}
            onMessage(`${t('Opened alongside')}: ${next.representationName}`);
        }catch(exception){onError(exception instanceof Error?exception.message:t('The secondary representation could not be opened.'));}
        finally{setBusy(false);}
    }

    async function closeAlongside():Promise<void>{
        if(view===null||secondaryView===null)return;
        const closed=await run(()=>closeEditorSessionRepresentation(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,secondaryView.representationId,view.etag),t('Secondary representation closed'));
        if(closed!==null){setSecondaryView(null);setSecondaryDiagram(null);}
    }

    async function runValidation():Promise<void>{
        if(view===null)return;
        setInspectorTab('PROBLEMS');
        await run(()=>validateEditorSession(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,'FULL',view.etag),t('Model validated'));
    }

    async function selectDiagnostic(diagnosticId:string,targetElementId:string):Promise<void>{
        if(view===null)return;
        setSelectedDiagnosticId(diagnosticId);setQuickFixPreview('');setInspectorTab('PROBLEMS');
        if(targetElementId!==''){const nextSelection=[targetElementId];selectedIdsRef.current=nextSelection;setSelectedIds(current=>sameStringList(current,nextSelection)?current:nextSelection);setDraft(current=>{const nextDraft=view.nodes.find(node=>node.elementId===targetElementId)?.attributes??{};return sameRecord(current,nextDraft)?current:nextDraft;});}
        try{setQuickFixes(await listEditorSessionQuickFixes(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,diagnosticId));}
        catch{setQuickFixes([]);}
    }

    async function previewFix(actionId:string):Promise<void>{
        if(view===null||selectedDiagnosticId==='')return;
        try{const preview=await previewEditorSessionQuickFix(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,selectedDiagnosticId,actionId,{arguments:{},selectionElementIds:selectedIds});setQuickFixPreview(`${(preview.changes.createdElementIds??[]).length} created · ${(preview.changes.modifiedElementIds??[]).length} modified · ${(preview.changes.deletedElementIds??[]).length} deleted`);}
        catch(exception){onError(exception instanceof Error?exception.message:t('Quick-fix preview failed'));}
    }
    async function applyFix(actionId:string):Promise<void>{
        if(view===null||selectedDiagnosticId==='')return;
        setBusy(true);
        try{const resultValue=await applyEditorSessionQuickFix(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,selectedDiagnosticId,actionId,{arguments:{},selectionElementIds:selectedIds},view.etag);setView(resultValue.view);setQuickFixes([]);setSelectedDiagnosticId('');setQuickFixPreview('');setDiagram(current=>current===null?emptyDiagram(resultValue.view):synchronizeDiagram(current,resultValue.view));onMessage(resultValue.messages.join(' · ')||t('Quick fix applied'));}
        catch(exception){onError(exception instanceof Error?exception.message:t('Quick fix could not be applied'));}
        finally{setBusy(false);}
    }

    async function saveForm(): Promise<void> {
        if (view === null || selectedNode === null || view.selectedForm === null) return;
        await run(() => updateEditorSessionElement(result.plan.metamodelId, result.plan.sourceEditorDefinitionId,
            view.session.id, selectedNode.elementId, view.representationId, view.selectedForm?.id ?? '', draft, view.etag), t('Generated form applied'));
    }

    if (view === null) return <section className="editor-neutral-session-launcher">
        <header><div><h4>{t('Model session')}</h4><p>{t('Choose the visual representation first. Its mappings, palette and forms will be applied to the same semantic model.')}</p></div><HydraButton type="button" disabled={busy||launchRepresentationId===''} onClick={() => void startBlank()}>{t('Create model')}</HydraButton></header>
        <div className="editor-runtime-launch-options"><label htmlFor="editor-runtime-launch-representation">{t('Visual representation')}</label><HydraSelect id="editor-runtime-launch-representation" ariaLabel={t('Visual representation')} showFilter={representationOptions.length>8} value={launchRepresentationId} options={representationOptions} onChange={setLaunchRepresentationId}/></div>
        <div className="editor-neutral-import"><label htmlFor="editor-emf-model-id">{t('Open model ID')}</label><input id="editor-emf-model-id" value={sourceModelId} onChange={event => setSourceModelId(event.target.value)} placeholder="people-model"/><HydraButton type="button" disabled={busy||sourceModelId.trim()===''||launchRepresentationId===''} onClick={() => void importEmf()}>{t('Open with this representation')}</HydraButton></div>
    </section>;

    return <section className="editor-neutral-session" aria-label={t('Model editor session')}><GamificationFeedbackCenter semanticScopeId={result.plan.metamodelId} enabled={result.plan.runtimePolicy.gamificationEnabled} />
        <DesignerCommandBar
            className="editor-neutral-session-toolbar"
            headingLevel={3}
            title={view.session.model.name || view.session.model.id}
            description={<>{t('Revision')} {view.session.revision} · {t('saved with ETag')} · {view.representationName}</>}
            actions={<>
                {!result.plan.runtimePolicy.autosave && <DesignerCommandGroup label={t('Session')}><HydraButton type="button" disabled={busy||!view.etag.startsWith('draft-')} onClick={()=>void run(()=>saveEditorSession(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,view.etag),'Session saved')}>{t('Save')}</HydraButton><HydraButton type="button" disabled={busy||!view.etag.startsWith('draft-')} onClick={()=>void run(()=>discardEditorSessionChanges(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id),'Draft changes discarded',true)}>{t('Discard')}</HydraButton></DesignerCommandGroup>}
                <DesignerCommandGroup label={t('Quality')}><HydraButton type="button" disabled={busy || !result.plan.capabilityManifest.capabilities.includes('VALIDATION_RUNTIME')} onClick={() => void runValidation()}>{t('Validate')}</HydraButton></DesignerCommandGroup>
                {selectedIds.length>0&&<DesignerCommandGroup label={t('Selection')}><HydraButton type="button" disabled={busy || !result.plan.capabilityManifest.capabilities.includes('CODE_GENERATION')} onClick={()=>{setCodeGenerationSelectionIds(selectedIds);setCodeGenerationOpen(true);}}>{t('Preview code')}</HydraButton><HydraButton className="danger" type="button" disabled={busy} onClick={()=>void deleteSelection()}>{selectedIds.length===1?t('Delete selected'):`${t('Delete selected')} (${selectedIds.length})`}</HydraButton></DesignerCommandGroup>}
                <DesignerCommandGroup label={t('Generation')}><HydraButton type="button" disabled={busy || !result.plan.capabilityManifest.capabilities.includes('CODE_GENERATION')} onClick={()=>{setCodeGenerationSelectionIds([]);setCodeGenerationOpen(true);}}>{t('Generate code')}</HydraButton></DesignerCommandGroup>
                <DesignerCommandGroup label={t('History')}><HydraButton type="button" disabled={busy || !view.undoAvailable} onClick={() => void run(() => undoEditorSession(result.plan.metamodelId, result.plan.sourceEditorDefinitionId, view.session.id, view.etag), 'Session change undone')}>{t('Undo')}</HydraButton><HydraButton type="button" disabled={busy || !view.redoAvailable} onClick={() => void run(() => redoEditorSession(result.plan.metamodelId, result.plan.sourceEditorDefinitionId, view.session.id, view.etag), 'Session change redone')}>{t('Redo')}</HydraButton></DesignerCommandGroup>
            </>}
            viewsLabel={t('Visual representation')}
            viewsSummary={view.representationName}
            views={<div className="editor-runtime-representation-selectors" aria-label={t('Visual representations')}>
                <HydraSelect ariaLabel={t('Visual representation')} showFilter={representationOptions.length>8} value={view.representationId} options={representationOptions} onChange={representationId=>void changeRepresentation(representationId)} />
                {representationOptions.length>1&&result.plan.runtimePolicy.maximumOpenRepresentations>1&&<><HydraSelect ariaLabel={t('Secondary representation')} showFilter={false} value={secondaryCandidateId} placeholder={t('Compare with…')} options={representationOptions.filter(option=>option.value!==view.representationId&&option.value!==secondaryView?.representationId)} onChange={setSecondaryCandidateId}/><HydraButton type="button" className="secondary" disabled={busy||secondaryCandidateId===''||(view.session.openRepresentationIds??[]).length>=result.plan.runtimePolicy.maximumOpenRepresentations} onClick={()=>{const candidate=secondaryCandidateId;setSecondaryCandidateId('');void openAlongside(candidate);}}>{t('Open alongside')}</HydraButton></>}
            </div>}
        />
        <ModelCodeGenerationDialog open={codeGenerationOpen} modelName={view.session.model.name||view.session.model.id} selectionElementIds={codeGenerationSelectionIds} defaultTargetId={runtimeCodeGeneration.defaultTargetId} defaultNamespace={runtimeCodeGeneration.outputNamespace} defaultFeatures={runtimeCodeGeneration.defaultFeatures} featureDefaultsByTarget={runtimeCodeGeneration.targetFeatureOverrides} loadTargets={()=>listEditorSessionCodeGenerationTargets(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id)} generate={(targetId,options,selection)=>generateEditorSessionCode(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,targetId,options,selection)} preview={(targetId,options,selection)=>previewEditorSessionCode(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,targetId,options,selection)} onClose={()=>setCodeGenerationOpen(false)} onError={onError} onMessage={onMessage} />
        {dialogs}
        <div className="editor-neutral-session-grid">
            <HydraResizablePanel className="editor-neutral-toolbox" side="left" storageKey="hydra-model-editor-palette-width" initialWidth={230} minimumWidth={190} maximumWidth={420} ariaLabel={t('Resize model editor palette')}>
                <h5>{t('Executable palette')}</h5>
                {(representation?.paletteGroups ?? []).map(group => {const visibleTools=group.tools.filter(tool=>view.toolStates?.[tool.id]?.visible??true);return visibleTools.length===0?null:<section key={group.id}><strong>{group.name}</strong>{visibleTools.map(tool => {const state=view.toolStates?.[tool.id];return <HydraButton key={tool.id} type="button" disabled={busy||state?.enabled===false} title={state?.reason||tool.description} onClick={() => void executeTool(tool)}>{tool.label}<small>{state?.enabled===false?state.reason:tool.description}</small></HydraButton>;})}</section>;})}
                {tools.length === 0 && <p>{t('No tools are available.')}</p>}
                {toolOutcome!==null&&<section className="editor-neutral-tool-result"><strong>{t('Last tool result')}</strong>{toolOutcome.returnValue!==undefined&&<pre>{JSON.stringify(toolOutcome.returnValue,null,2)}</pre>}{(toolOutcome.messages??[]).map(message=><small key={message}>{message}</small>)}{toolOutcome.validationReport&&<small>{(toolOutcome.validationReport.diagnostics??[]).length} {t('validation diagnostic(s)')}</small>}</section>}
                {pendingTool!==null&&pendingTool.kind!=='INVOKE_OPERATION'&&<section className="editor-neutral-tool-arguments"><header><strong>{pendingTool.label}</strong><HydraButton aria-label={t('Close tool arguments')} className="hydra-window-close" type="button" onClick={()=>setPendingTool(null)}>×</HydraButton></header><p>{pendingTool.description||t('Provide the runtime arguments and execute the configured tool.')}</p>{pendingTool.kind==='RUN_ACTION'&&<label>{t('Arguments JSON')}<textarea aria-label={t('Action arguments JSON')} value={actionArgumentsSource} onChange={event=>setActionArgumentsSource(event.target.value)}/></label>}<HydraButton type="button" disabled={busy||selectedIds.length===0} onClick={()=>void executePendingTool()}>{t('Execute tool')}</HydraButton>{selectedIds.length===0&&<small>{t('Select a target model element first.')}</small>}</section>}
            </HydraResizablePanel>
            <main className={`editor-neutral-canvas${secondaryView!==null?' split':''}`}>
                <section className="editor-runtime-view"><header><strong>{view.representationName}</strong><small>{view.representationKind}</small></header><EditorRuntimeRepresentation view={view} diagram={diagram??emptyDiagram(view)} selectedIds={selectedIds} onSelectNode={elementId=>void selectNode(elementId)} onSelectionChange={selectNodes} onConnect={connection=>void connect(connection)} onNodeDragStop={saveNodePosition} onNodeResizeStop={saveNodePosition} onDiagramNodesChange={nodes=>void saveDiagramNodes(nodes)} onPreviewCode={ids=>{setCodeGenerationSelectionIds(ids);setCodeGenerationOpen(true);}} onDeleteSelection={()=>void deleteSelection()} canPreviewCode={result.plan.capabilityManifest.capabilities.includes('CODE_GENERATION')} busy={busy}/></section>
                {secondaryView!==null&&<section className="editor-runtime-view"><header><strong>{secondaryView.representationName}</strong><small>{secondaryView.representationKind}</small><HydraButton type="button" onClick={()=>void closeAlongside()} aria-label={t('Close secondary representation')} className="hydra-window-close">×</HydraButton></header><EditorRuntimeRepresentation view={secondaryView} diagram={secondaryDiagram??emptyDiagram(secondaryView)} selectedIds={selectedIds} onSelectNode={elementId=>void selectNode(elementId)}/></section>}
            </main>
            <HydraResizablePanel className="editor-neutral-form editor-runtime-inspector" side="right" storageKey="hydra-model-editor-inspector-width" initialWidth={330} minimumWidth={260} maximumWidth={480} ariaLabel={t('Resize model editor inspector')}>
                <nav className="editor-runtime-inspector-tabs" role="tablist" aria-label={t('Model element inspector')}>
                    <HydraButton type="button" role="tab" aria-selected={inspectorTab==='PROPERTIES'} className={inspectorTab==='PROPERTIES'?'active':''} onClick={()=>setInspectorTab('PROPERTIES')}><span className="editor-runtime-inspector-tab-label">{t('Properties')}</span></HydraButton>
                    <HydraButton type="button" role="tab" aria-selected={inspectorTab==='OPERATIONS'} className={inspectorTab==='OPERATIONS'?'active':''} onClick={()=>setInspectorTab('OPERATIONS')}><span className="editor-runtime-inspector-tab-label">{t('Operations')}</span><small>{selectedClassOperations.length}</small></HydraButton>
                    <HydraButton type="button" role="tab" aria-selected={inspectorTab==='PROBLEMS'} className={inspectorTab==='PROBLEMS'?'active':''} onClick={()=>setInspectorTab('PROBLEMS')}><span className="editor-runtime-inspector-tab-label">{t('Problems')}</span><small>{(view.validationReport?.diagnostics??[]).length}</small></HydraButton>
                </nav>
                {inspectorTab==='PROPERTIES'&&<section className="editor-runtime-inspector-panel" role="tabpanel">
                    <h5>{t('Properties')}</h5>
                    {selectedNode === null && <p>{t('Select a model element to edit it.')}</p>}
                    {selectedNode !== null && (selectedNode.availableAppearances?.length ?? 0) > 1 && <section className="editor-runtime-appearance-selector">
                        <label htmlFor="editor-element-appearance">{t('Visual appearance')}</label>
                        <HydraSelect id="editor-element-appearance" ariaLabel={t('Visual appearance')} showFilter={false} value={selectedNode.mappingId} options={(selectedNode.availableAppearances ?? []).map(appearance=>({value:appearance.mappingId,label:appearance.name,description:`${appearance.shape}${appearance.imageAssetPath?` · ${appearance.imageAssetPath}`:''}`}))} onChange={mappingId=>void run(()=>setEditorSessionElementAppearance(result.plan.metamodelId,result.plan.sourceEditorDefinitionId,view.session.id,selectedNode.elementId,view.representationId,mappingId,view.etag),t('Visual appearance changed'))} />
                        <small>{t('Only appearances associated with the selected element in the active metamodel and representation are available.')}</small>
                    </section>}
                    {selectedNode !== null && view.selectedForm === null && <p>{t('No generated form is configured for')} {selectedNode.className}.</p>}
                    {selectedNode !== null && view.selectedForm !== null && <>
                        <strong>{view.selectedForm.name}</strong>
                        {[...(view.selectedForm.sections??[])].sort((a,b) => a.order-b.order).map(section => <section key={section.id}><h6>{section.title}</h6>{(section.fields??[]).map(field => <GeneratedField key={field.id} field={field} metamodel={metamodel} value={draft[field.featureId]} onChange={value => setDraft(current => ({...current,[field.featureId]:value}))}/>)}</section>)}
                        <HydraButton type="button" disabled={busy} onClick={() => void saveForm()}>{t('Apply form')}</HydraButton>
                        <HydraButton className="danger" type="button" disabled={busy} onClick={() => void run(() => deleteEditorSessionElement(result.plan.metamodelId, result.plan.sourceEditorDefinitionId, view.session.id, selectedNode.elementId, view.etag), t('Element deleted'))}>{t('Delete element')}</HydraButton>
                    </>}
                </section>}
                {inspectorTab==='OPERATIONS'&&<section className="editor-runtime-inspector-panel editor-runtime-operations" role="tabpanel">
                    <h5>{t('Operations')}</h5>
                    {selectedNode===null&&<p>{t('Select a model element to inspect its operations.')}</p>}
                    {selectedNode!==null&&selectedClassOperations.length===0&&<p>{t('No operations are defined for the selected element.')}</p>}
                    {selectedNode!==null&&selectedClassOperations.map(operation=>{const tool=operationToolsByOperationId.get(operation.id);const state=tool===undefined?undefined:view.toolStates?.[tool.id];return <article key={operation.id}><header><strong>{operation.name}</strong><small>{operation.behaviorKind}</small></header><p>{operation.parameters.length} {t('parameter(s)')}</p>{tool===undefined?<small>{t('This operation is not exposed as a tool in the active representation.')}</small>:<HydraButton type="button" disabled={busy||state?.enabled===false} title={state?.reason||tool.description} onClick={()=>{setInspectorTab('OPERATIONS');void executeTool(tool);}}>{t('Run operation')}</HydraButton>}</article>;})}
                    {pendingTool?.kind==='INVOKE_OPERATION'&&<section className="editor-neutral-tool-arguments"><header><strong>{pendingTool.label}</strong><HydraButton aria-label={t('Close tool arguments')} className="hydra-window-close" type="button" onClick={()=>setPendingTool(null)}>×</HydraButton></header><p>{pendingTool.description||t('Provide the runtime arguments and execute the configured tool.')}</p>{operationFor(pendingTool)?.parameters.map(parameter=><OperationParameterField key={parameter.id} parameter={parameter} value={toolArguments[parameter.name]} onChange={value=>setToolArguments(current=>({...current,[parameter.name]:value}))}/>)??<p>{t('The referenced operation was not found.')}</p>}<HydraButton type="button" disabled={busy||selectedIds.length===0} onClick={()=>void executePendingTool()}>{t('Execute tool')}</HydraButton></section>}
                </section>}
                {inspectorTab==='PROBLEMS'&&<section className="editor-runtime-inspector-panel editor-session-problems" role="tabpanel"><header><h5>{t('Problems')}</h5><small>{(view.validationReport?.diagnostics??[]).length}</small></header>{(view.validationReport?.diagnostics??[]).map(diagnostic=><HydraButton type="button" key={diagnostic.id} className={selectedDiagnosticId===diagnostic.id?'selected':''} onClick={()=>void selectDiagnostic(diagnostic.id,diagnostic.location?.modelElementId??'')}><strong>{diagnostic.severity}</strong><span data-hydra-runtime-message="true" data-hydra-message-context="problems" data-hydra-message-code={diagnostic.id} data-hydra-message-severity={diagnostic.severity}>{diagnostic.message}</span></HydraButton>)}{view.validationReport!==null&&((view.validationReport?.diagnostics??[]).length)===0&&<p>{t('No problems detected.')}</p>}{quickFixes.length>0&&<div className="editor-session-quickfixes"><strong>{t('Available quick fixes')}</strong>{quickFixes.map(fix=><div key={fix.actionId}><span>{fix.name}</span><HydraButton type="button" onClick={()=>void previewFix(fix.actionId)}>{t('Preview')}</HydraButton><HydraButton type="button" disabled={!fix.enabled||busy} onClick={()=>void applyFix(fix.actionId)}>{t('Apply')}</HydraButton></div>)}{quickFixPreview!==''&&<small>{quickFixPreview}</small>}</div>}</section>}
            </HydraResizablePanel>
        </div>
    </section>;
}

function sameStringList(left:readonly string[],right:readonly string[]):boolean{
    return left.length===right.length&&left.every((value,index)=>value===right[index]);
}
function sameRecord(left:Readonly<Record<string,unknown>>,right:Readonly<Record<string,unknown>>):boolean{
    const leftKeys=Object.keys(left),rightKeys=Object.keys(right);
    return leftKeys.length===rightKeys.length&&leftKeys.every(key=>Object.prototype.hasOwnProperty.call(right,key)&&Object.is(left[key],right[key]));
}

function OperationParameterField({parameter,value,onChange}:{parameter:MetaParameter;value:unknown;onChange:(value:unknown)=>void}){
    const required=parameter.multiplicity.lowerBound>0;
    const primitive=parameter.type.kind==='PRIMITIVE'?parameter.type.targetId:'';
    if(primitive==='BOOLEAN')return <label className="editor-neutral-checkbox"><input type="checkbox" checked={Boolean(value)} onChange={event=>onChange(event.target.checked)}/>{parameter.name}{required?' *':''}</label>;
    const numeric=['BYTE','SHORT','INTEGER','LONG','FLOAT','DOUBLE','DECIMAL','BIG_INTEGER'].includes(primitive);
    return <label>{parameter.name}{required?' *':''}<input type={numeric?'number':'text'} required={required} value={value==null?'':String(value)} onChange={event=>onChange(numeric&&event.target.value!==''?Number(event.target.value):event.target.value)}/><small>{parameter.type.kind.toLowerCase()} {parameter.type.targetId||parameter.type.externalName}</small></label>;
}

function GeneratedField({ field, metamodel, value, onChange }: { field: FormFieldDefinition; metamodel: MetaModel; value: unknown; onChange: (value: unknown) => void }) {
    const attribute = metamodel.classes.flatMap(item => item.attributes).find(item => item.id === field.featureId);
    const label = field.label || attribute?.name || 'Field';
    const help=field.helpText!==''?<small>{field.helpText}</small>:null;
    if(field.widget==='BOOLEAN') return <label className="editor-neutral-checkbox"><input type="checkbox" checked={Boolean(value)} disabled={field.readOnly} onChange={event=>onChange(event.target.checked)}/>{label}{help}</label>;
    if(field.widget==='TEXTAREA'||field.widget==='CODE'||field.widget==='EXPRESSION') return <label>{label}<textarea value={value==null?'':String(value)} readOnly={field.readOnly} required={field.required} onChange={event=>onChange(event.target.value)}/>{help}</label>;
    if(field.widget==='NUMBER') return <label>{label}<input type="number" value={value==null?'':String(value)} readOnly={field.readOnly} required={field.required} onChange={event=>onChange(event.target.value===''?'':Number(event.target.value))}/>{help}</label>;
    if(field.widget==='DATE'||field.widget==='COLOR'||field.widget==='FILE') return <label>{label}<input type={field.widget==='DATE'?'date':field.widget==='COLOR'?'color':'text'} value={value==null?'':String(value)} readOnly={field.readOnly} required={field.required} onChange={event=>onChange(event.target.value)}/>{help}</label>;
    if(field.widget==='ENUM') {const enumType=metamodel.enums.find(item=>item.id===attribute?.type.targetId);return <label>{label}<HydraSelect ariaLabel={field.label || attribute?.name || 'Enumeration value'} value={value==null?'':String(value)} disabled={field.readOnly} required={field.required} placeholder="Select…" options={(enumType?.literals ?? []).map(literal=>({value:literal.name,label:literal.name,description:'Enumeration value defined by the metamodel.'}))} onChange={onChange} />{help}</label>}
    if(field.widget==='REFERENCE'||field.widget==='MULTI_REFERENCE') return <label>{label}<input value={Array.isArray(value)?value.join(', '):value==null?'':String(value)} readOnly={field.readOnly} required={field.required} placeholder={field.widget==='MULTI_REFERENCE'?'element:id, element:id':'element:id'} onChange={event=>onChange(field.widget==='MULTI_REFERENCE'?event.target.value.split(',').map(item=>item.trim()).filter(Boolean):event.target.value)}/>{help}</label>;
    return <label>{label}<input value={value==null?'':String(value)} readOnly={field.readOnly||field.widget==='READ_ONLY'} required={field.required} onChange={event=>onChange(event.target.value)}/>{help}</label>;
}
