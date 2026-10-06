import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useHydraLanguage } from '../i18n';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';
import { hydraContentSignature, type HydraPersistedIdentity } from '../hooks/hydraOverwritePolicy';
import { applyGrammarCodeAction } from '../grammar-language-service/grammarCodeActions';
import { analyzeGrammar, cancelGrammarAnalysis, compileGrammar, createGrammarApi, listGrammarProviders, listGrammars, listToolchainProfiles, loadActiveGrammarCompilation, loadGrammar, loadGrammarCompilationProgress, prepareGrammarPlatformConversion, projectGrammarClassDiagram, replaceGrammarApi } from './api';
import { changeTechnology, createGrammar } from './grammarDesignerModel';
import { inspectGrammarLocally } from './grammarLocalInspection';
import { buildLocalGrammarFeedback } from './grammarLocalQuickFixes';
import { GrammarMonacoEditor } from './GrammarMonacoEditor';
import { GrammarSourceExplorer } from './GrammarSourceExplorer';
import { GrammarStructureTree } from './GrammarStructureTree';
import { GrammarClassDiagram } from './GrammarClassDiagram';
import { GrammarIdentityForm } from './GrammarIdentityForm';
import { GrammarInferredMetamodelView } from './GrammarInferredMetamodelView';
import { GrammarGeneratedCodeView } from './GrammarGeneratedCodeView';
import { GrammarPlatformConversionDialog } from './GrammarPlatformConversionDialog';
import { GrammarRuleSyntaxGraphs } from './GrammarRuleSyntaxGraphs';
import { GrammarTextualCustomization } from './GrammarTextualCustomization';
import { GrammarStatusDock } from './GrammarStatusDock';
import { listCanonicalMetamodels } from '../metamodel-designer/api';
import type { CanonicalMetamodelSummary } from '../metamodel-designer/types';
import type { GrammarAnalysisMode, GrammarAnalysisResult, GrammarClassDiagramProjection, GrammarCodeAction, GrammarCompilationSnapshot, GrammarDefinitionModel, GrammarDiagnostic, GrammarDiagramSourceRef, GrammarPlatformConversionPreview, GrammarProviderDescriptor, GrammarSourceDocument, GrammarSummary, GrammarSymbol, GrammarTechnology, GrammarToolchainProfile } from './types';
import './grammar-designer.css';
import './grammar-designer-responsive.css';
import { HydraButton, HydraOperationProgress, HydraTooltip } from '../design-system';

const FAST_DEBOUNCE_MS=250;
const NORMAL_IDLE_MS=1000;
const FULL_IDLE_MS=1800;
const LAST_GRAMMAR_STORAGE_KEY='hydra.grammar-designer.last-grammar.v1';

function requestId(mode:GrammarAnalysisMode,revision:number):string{
    const random=typeof crypto!=='undefined'&&typeof crypto.randomUUID==='function'?crypto.randomUUID():`${Date.now()}-${Math.random().toString(36).slice(2)}`;
    return `grammar-ui:${mode.toLowerCase()}:${revision}:${random}`;
}

export function GrammarDesigner(){
    const {t}=useHydraLanguage();
    const [model,setModel]=useState<GrammarDefinitionModel>(()=>createGrammar());
    const [etag,setEtag]=useState('');
    const [persisted,setPersisted]=useState<GrammarSummary[]>([]);
    const [profiles,setProfiles]=useState<GrammarToolchainProfile[]>([]);
    const [providers,setProviders]=useState<GrammarProviderDescriptor[]>([]);
    const [metamodels,setMetamodels]=useState<CanonicalMetamodelSummary[]>([]);
    const [selectedDocumentId,setSelectedDocumentId]=useState('main');
    const [revision,setRevision]=useState(0);
    const [analysis,setAnalysis]=useState<GrammarAnalysisResult|null>(null);
    const [snapshot,setSnapshot]=useState<GrammarCompilationSnapshot|null>(null);
    const [classDiagram,setClassDiagram]=useState<GrammarClassDiagramProjection|null>(null);
    const [projectionLoading,setProjectionLoading]=useState(false);
    const [projectionError,setProjectionError]=useState('');
    const [workspaceView,setWorkspaceView]=useState<'SOURCE'|'DIAGRAM'|'METAMODEL'|'RULE_GRAPHS'|'GENERATED_CODE'|'TEXTUAL_APPEARANCE'|'TEXTUAL_HOVER'|'TEXTUAL_PREVIEW'>('SOURCE');
    const [focusDiagnostic,setFocusDiagnostic]=useState<GrammarDiagnostic|null>(null);
    const [message,setMessage]=useState('');
    const [statusDockTab,setStatusDockTab]=useState<'PROBLEMS'|'INFORMATION'>('PROBLEMS');
    const [conversionPreview,setConversionPreview]=useState<GrammarPlatformConversionPreview|null>(null);
    const [requestedTechnology,setRequestedTechnology]=useState<GrammarTechnology|null>(null);
    const [conversionLoading,setConversionLoading]=useState(false);
    const [operation,setOperation]=useState<{kind:'CONVERSION'|'COMPILE';stage:string;detail:string;progress?:number}|null>(null);
    const [operationDiagnostics,setOperationDiagnostics]=useState<GrammarDiagnostic[]>([]);
    const analysisSerial=useRef(0);
    const diagramSerial=useRef(0);
    const activeRequests=useRef(new Set<string>());

    const currentIdentity=useMemo<HydraPersistedIdentity[]>(()=>[{layer:'GRAMMARS',entityKind:'GRAMMAR',stableId:model.id,uniqueName:model.name,displayLabel:model.name,contentSignature:hydraContentSignature(model)}],[model]);
    const persistedIdentities=useMemo<HydraPersistedIdentity[]>(()=>persisted.map(item=>({layer:'GRAMMARS',entityKind:'GRAMMAR',stableId:item.id,uniqueName:item.name,displayLabel:item.name})),[persisted]);
    const {confirmOverwrite,overwriteDialogs}=useHydraOverwriteConfirmation({current:currentIdentity,persisted:persistedIdentities});
    const selected=model.documents.find(doc=>doc.id===selectedDocumentId)??model.documents[0];
    const compatibleProfiles=profiles.filter(item=>item.technology===model.technology);
    const provider=providers.find(item=>item.technology===model.technology);
    const localInspection=useMemo(()=>inspectGrammarLocally(model),[model]);
    const localFeedback=useMemo(()=>buildLocalGrammarFeedback(localInspection.diagnostics,localInspection.symbols),[localInspection.diagnostics,localInspection.symbols]);
    const currentAnalysis=analysis!==null&&!analysis.stale&&analysis.draftRevision===revision?analysis:null;
    const effectiveCodeActions=useMemo(()=>mergeGrammarCodeActions([...(currentAnalysis?.codeActions??[]),...localFeedback.actions]),[currentAnalysis,localFeedback.actions]);
    const unusedRuleNames=useMemo(()=>new Set((currentAnalysis?.metadata['unusedRules.names']??localInspection.unusedRules.join('|')).split('|').filter(Boolean)),[currentAnalysis,localInspection.unusedRules]);
    const localUnusedDiagnostics=useMemo<GrammarDiagnostic[]>(()=>{
        if(currentAnalysis!==null)return [];
        return localInspection.symbols.filter(symbol=>symbol.kind==='RULE'&&unusedRuleNames.has(symbol.name)).map(symbol=>({
            code:'GRAMMAR_UNUSED_RULE',severity:'WARNING',title:t('Unused grammar rule'),
            message:t('This rule is not used from any effective grammar entry.'),
            cause:t('There is no call path from an effective entry rule to this parser rule.'),
            consequence:t('It does not block compilation, but it may represent obsolete or accidentally disconnected grammar code.'),
            solution:t('Connect it from a reachable rule, declare it as an intentional entry, or remove it after confirming it is unnecessary.'),
            documentId:symbol.documentId,range:symbol.range,rule:symbol.name,property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata:{rule:symbol.name,unused:'true',provisional:'true'}
        }));
    },[currentAnalysis,localInspection.symbols,t,unusedRuleNames]);
    const baseDiagnostics=useMemo(()=>mergeGrammarDiagnostics([
        ...localFeedback.diagnostics,
        ...localUnusedDiagnostics,
        ...(currentAnalysis?.diagnostics??[])
    ]),[currentAnalysis,localFeedback.diagnostics,localUnusedDiagnostics]);
    const visibleDiagnostics=useMemo(()=>mergeGrammarDiagnostics([...baseDiagnostics,...operationDiagnostics]),[baseDiagnostics,operationDiagnostics]);
    const editorDiagnostics=useMemo(()=>visibleDiagnostics.filter(item=>item.code!=='GRAMMAR_EFFECTIVE_ENTRY_RULE'),[visibleDiagnostics]);
    const blocking=baseDiagnostics.filter(item=>item.severity==='ERROR');
    const entryRuleInference={
        rule:currentAnalysis?.metadata['entryRule.inferred']??localInspection.inferredEntryRule,
        method:currentAnalysis?.metadata['entryRule.method']??localInspection.inferenceMethod,
        confidence:Math.round((currentAnalysis===null?localInspection.confidence:Number(currentAnalysis.metadata['entryRule.confidence']??localInspection.confidence))*100),
        mode:(model.annotations['hydra.entryRule.mode']??'AUTO') as 'AUTO'|'MANUAL',
        selectedRule:model.entryRule,
        provisional:currentAnalysis===null
    };

    useEffect(()=>{
        let disposed=false;
        void Promise.all([listGrammars(),listToolchainProfiles(),listGrammarProviders(),listCanonicalMetamodels()]).then(async([items,toolchains,descriptors,models])=>{
            if(disposed)return;
            setPersisted(items);setProfiles(toolchains);setProviders(descriptors);setMetamodels(models);
            let remembered='';
            try{remembered=localStorage.getItem(LAST_GRAMMAR_STORAGE_KEY)??'';}catch{/* local persistence is optional */}
            const initial=items.find(item=>item.id===remembered)??items[0];
            if(initial===undefined)return;
            try{
                const [loaded,active]=await Promise.all([loadGrammar(initial.id),loadActiveGrammarCompilation(initial.id)]);
                if(disposed)return;
                setModel(loaded.grammar);setEtag(loaded.etag);setSelectedDocumentId(loaded.grammar.entryDocumentId||loaded.grammar.documents[0]?.id||'');setRevision(0);setAnalysis(null);setSnapshot(active);setClassDiagram(active?.classDiagram??null);setWorkspaceView('SOURCE');rememberGrammar(initial.id);
            }catch(error){if(!disposed)setMessage(error instanceof Error?error.message:String(error));}
        }).catch(error=>{if(!disposed)setMessage(error instanceof Error?error.message:String(error));});
        return()=>{disposed=true;};
    },[]);

    useEffect(()=>{
        const serial=++analysisSerial.current;
        let disposed=false;
        const ids:string[]=[];
        const requests=activeRequests.current;
        const run=(analysisMode:GrammarAnalysisMode)=>{
            const id=requestId(analysisMode,revision);ids.push(id);requests.add(id);
            void analyzeGrammar(model,revision,analysisMode,id).then(result=>{
                requests.delete(id);
                if(disposed||serial!==analysisSerial.current||result.stale||result.draftRevision!==revision)return;
                const backendEntry=result.metadata['entryRule.inferred']??'';
                if((model.annotations['hydra.entryRule.mode']??'AUTO')!=='MANUAL'&&backendEntry!==''&&backendEntry!==model.entryRule){
                    setModel({...model,entryRule:backendEntry,annotations:{...model.annotations,'hydra.entryRule.mode':'AUTO'}});
                    setRevision(value=>value+1);
                    return;
                }
                setAnalysis(current=>{
                    if(current!==null&&current.draftRevision===revision){
                        if(analysisMode==='FAST'&&current.mode!=='FAST')return current;
                        if(analysisMode==='NORMAL'&&current.mode==='FULL')return current;
                    }
                    return result;
                });
            }).catch(error=>{requests.delete(id);if(!disposed&&serial===analysisSerial.current)setMessage(error instanceof Error?error.message:String(error));});
        };
        const fast=window.setTimeout(()=>run('FAST'),FAST_DEBOUNCE_MS);
        const normal=window.setTimeout(()=>run('NORMAL'),NORMAL_IDLE_MS);
        const full=window.setTimeout(()=>run('FULL'),FULL_IDLE_MS);
        return()=>{
            disposed=true;window.clearTimeout(fast);window.clearTimeout(normal);window.clearTimeout(full);
            for(const id of ids){requests.delete(id);void cancelGrammarAnalysis(id).catch(()=>false);}
        };
    },[model,revision]);

    const refreshDraftProjection=useCallback(async(showLoading:boolean,expectedSourceHash='')=>{
        const serial=++diagramSerial.current;
        if(showLoading){setProjectionLoading(true);setProjectionError('');}
        try{
            const result=await projectGrammarClassDiagram(model,revision);
            if(serial!==diagramSerial.current||result.draftRevision!==revision)return;
            if(expectedSourceHash!==''&&result.sourceHash!==expectedSourceHash)return;
            setClassDiagram(result);
            setProjectionError('');
        }catch(error){
            if(serial!==diagramSerial.current)return;
            const text=error instanceof Error?error.message:String(error);
            setProjectionError(text);setMessage(text);
        }finally{if(serial===diagramSerial.current)setProjectionLoading(false);}
    },[model,revision]);

    useEffect(()=>{
        if(analysis===null||analysis.stale||analysis.draftRevision!==revision)return;
        if(classDiagram!==null&&classDiagram.draftRevision===revision&&classDiagram.sourceHash===analysis.sourceHash)return;
        const task=window.setTimeout(()=>void refreshDraftProjection(false,analysis.sourceHash),0);
        return()=>window.clearTimeout(task);
    },[analysis,classDiagram,refreshDraftProjection,revision]);

    useEffect(()=>{
        if(workspaceView!=='DIAGRAM'&&workspaceView!=='METAMODEL')return;
        const expectedSourceHash=currentAnalysis?.sourceHash??'';
        const task=window.setTimeout(()=>void refreshDraftProjection(true,expectedSourceHash),0);
        return()=>window.clearTimeout(task);
    },[currentAnalysis?.sourceHash,refreshDraftProjection,revision,workspaceView]);


    function withLocalAutoEntry(next:GrammarDefinitionModel):GrammarDefinitionModel{
        if((next.annotations['hydra.entryRule.mode']??'AUTO')==='MANUAL')return next;
        const inferred=inspectGrammarLocally(next).inferredEntryRule;
        return inferred!==''&&inferred!==next.entryRule?{...next,entryRule:inferred,annotations:{...next.annotations,'hydra.entryRule.mode':'AUTO'}}:next;
    }
    function mutate(next:GrammarDefinitionModel){setModel(withLocalAutoEntry(next));setRevision(value=>value+1);setMessage('');}
    function updateSource(content:string){if(!selected)return;setFocusDiagnostic(null);mutate({...model,documents:model.documents.map(doc=>doc.id===selected.id?{...doc,content,sourceHash:''}:doc)});}
    function switchTechnology(technology:GrammarTechnology){
        if(technology===model.technology||conversionLoading)return;
        setRequestedTechnology(technology);
        if(model.documents.every(document=>document.content.trim()==='')){
            const next=changeTechnology(model,technology);
            setRequestedTechnology(null);setSelectedDocumentId(next.entryDocumentId);mutate(next);setAnalysis(null);setSnapshot(null);setClassDiagram(null);setWorkspaceView('SOURCE');
            setMessage(t('Technology changed because the grammar had no authored source to preserve.'));
            return;
        }
        setConversionLoading(true);setOperationDiagnostics([]);setStatusDockTab('INFORMATION');setOperation({kind:'CONVERSION',stage:'prepare',detail:t('Reading the current grammar and preparing a safe conversion preview…')});setMessage(t('Preparing a safe grammar conversion preview…'));
        void prepareGrammarPlatformConversion(model,technology).then(preview=>{
            setOperation({kind:'CONVERSION',stage:'review',detail:t('Semantic equivalence has been checked. Opening the conversion preview…')});
            setConversionPreview(preview);
            setMessage(preview.semanticallyEquivalent?t('HYDRA has transformed the grammar safely. Confirm the change to use the selected technology.'):t('The conversion cannot be applied because semantic equivalence was not demonstrated.'));
        }).catch(error=>{const text=error instanceof Error?error.message:String(error);setRequestedTechnology(null);setStatusDockTab('PROBLEMS');setOperationDiagnostics([operationDiagnostic(selected?.id??model.entryDocumentId,t('Grammar conversion could not be prepared'),text)]);setMessage(text);}).finally(()=>{setConversionLoading(false);setOperation(null);});
    }
    function applyConversion(){if(conversionPreview===null||!conversionPreview.semanticallyEquivalent)return;const next=conversionPreview.convertedGrammar;setConversionPreview(null);setRequestedTechnology(null);setSelectedDocumentId(next.documents.find(doc=>doc.id===next.entryDocumentId)?.id??next.documents[0]?.id??'');mutate(next);setAnalysis(null);setSnapshot(null);setClassDiagram(null);setWorkspaceView('SOURCE');setMessage(t('Grammar transformed to the selected technology. Review the source or compile it directly.'));}

    async function cancelPendingAnalyses():Promise<void>{
        const ids=[...activeRequests.current];
        activeRequests.current.clear();
        await Promise.allSettled(ids.map(id=>cancelGrammarAnalysis(id)));
    }

    function rememberGrammar(grammarId:string){try{if(grammarId.trim()==='')localStorage.removeItem(LAST_GRAMMAR_STORAGE_KEY);else localStorage.setItem(LAST_GRAMMAR_STORAGE_KEY,grammarId);}catch{/* local persistence is optional */}}
    async function openGrammar(grammarId:string){
        if(grammarId.trim()==='')return;
        await cancelPendingAnalyses();
        try{
            const [loaded,active]=await Promise.all([loadGrammar(grammarId),loadActiveGrammarCompilation(grammarId)]);
            setModel(loaded.grammar);setEtag(loaded.etag);setSelectedDocumentId(loaded.grammar.entryDocumentId||loaded.grammar.documents[0]?.id||'');setRevision(0);setAnalysis(null);setSnapshot(active);setClassDiagram(active?.classDiagram??null);setFocusDiagnostic(null);setOperationDiagnostics([]);setStatusDockTab('PROBLEMS');setWorkspaceView('SOURCE');rememberGrammar(grammarId);setMessage(t('Saved grammar loaded.'));
        }catch(error){setMessage(error instanceof Error?error.message:String(error));}
    }
    function newGrammar(){
        const base=createGrammar();
        const ids=new Set(persisted.map(item=>item.id));
        let id=base.id,index=2;while(ids.has(id)){id=`${base.id}-${index++}`;}
        const next=id===base.id?base:{...base,id,name:`${base.name} ${index-1}`};
        void cancelPendingAnalyses();
        setModel(next);setEtag('');setSelectedDocumentId(next.entryDocumentId);setRevision(0);setAnalysis(null);setSnapshot(null);setClassDiagram(null);setFocusDiagnostic(null);setOperationDiagnostics([]);setStatusDockTab('PROBLEMS');setWorkspaceView('SOURCE');rememberGrammar('');setMessage(t('New grammar ready.'));
    }
    async function refreshList(){setPersisted(await listGrammars());}
    async function refreshMetamodels(){setMetamodels(await listCanonicalMetamodels());setMessage(t('Metamodel imported and linked to the grammar.'));}

    async function save():Promise<GrammarDefinitionModel|null>{
        if(!(await confirmOverwrite()))return null;
        const conflict=persisted.find(item=>item.id===model.id||item.name===model.name);let target=model;
        try{
            let result:{grammar:GrammarDefinitionModel;etag:string};
            if(conflict){if(conflict.id!==model.id)target={...model,id:conflict.id};result=await replaceGrammarApi(target,conflict.etag||etag);}
            else result=await createGrammarApi(target);
            setModel(result.grammar);setEtag(result.etag);setSelectedDocumentId(result.grammar.entryDocumentId||result.grammar.documents[0]?.id||'');rememberGrammar(result.grammar.id);await refreshList();setMessage(t('Grammar saved.'));return result.grammar;
        }catch(error){setMessage(error instanceof Error?error.message:String(error));return null;}
    }

    async function compileSnapshot(activateOnSuccess:boolean):Promise<boolean>{
        if(blocking.length>0){
            setStatusDockTab('PROBLEMS');
            setOperationDiagnostics(blocking);
            setMessage(t('Resolve the blocking grammar diagnostics before compiling.'));
            return false;
        }
        await cancelPendingAnalyses();
        setOperationDiagnostics([]);setStatusDockTab('INFORMATION');setOperation({kind:'COMPILE',stage:'SAVE',detail:t('Saving the current grammar before compilation…'),progress:0});
        const saved=await save();if(saved===null){setOperation(null);return false;}
        const compileRequestId=`grammar-compile:${typeof crypto!=="undefined"&&typeof crypto.randomUUID==='function'?crypto.randomUUID():`${Date.now()}-${Math.random().toString(36).slice(2)}`}`;
        let disposed=false;
        const refreshProgress=async()=>{try{const current=await loadGrammarCompilationProgress(saved.id,compileRequestId);if(disposed||current===null)return;setOperation({kind:'COMPILE',stage:current.stage,detail:t(compilationStageDetail(current.stage)),progress:Math.round((current.completedStages/Math.max(1,current.totalStages))*100)});}catch{/* progress is advisory; the compilation response remains authoritative */}};
        const timer=window.setInterval(()=>void refreshProgress(),350);
        try{
            setOperation({kind:'COMPILE',stage:'FREEZE_DRAFT',detail:t('Preparing the current grammar for a complete compilation…'),progress:0});
            const result=await compileGrammar(saved,revision,activateOnSuccess,compileRequestId);setSnapshot(result);setClassDiagram(result.classDiagram??null);
            if(result.status==='SUCCESS'){setStatusDockTab('INFORMATION');setMessage(t(activateOnSuccess?'Snapshot compiled, generated and activated.':'Language project generated from a successful snapshot. The active snapshot was not changed.'));}
            else {setStatusDockTab('PROBLEMS');setMessage(t('The snapshot was not promoted; the last valid snapshot remains active.'));setOperationDiagnostics(result.diagnostics.filter(item=>item.severity!=='INFO'));}
            return result.status==='SUCCESS';
        }catch(error){const text=error instanceof Error?error.message:String(error);const fallbackDiagnostics=baseDiagnostics.filter(item=>item.severity!=='INFO');setStatusDockTab('PROBLEMS');setOperationDiagnostics(fallbackDiagnostics.length>0?fallbackDiagnostics:[operationDiagnostic(selected?.id??model.entryDocumentId,t('Grammar compilation failed'),text)]);setMessage(describeOperationError(text,t));return false;}
        finally{disposed=true;window.clearInterval(timer);setOperation(null);}
    }

    async function compileAndActivate():Promise<boolean>{return compileSnapshot(true);}
    async function generateLanguageProject():Promise<boolean>{return compileSnapshot(false);}

    function applyAction(action:GrammarCodeAction){
        const authoritativeRevision=action.metadata['source']==='local-inspection'?undefined:currentAnalysis?.draftRevision;
        try{mutate({...model,documents:applyGrammarCodeAction(model.documents,action,revision,authoritativeRevision)});setMessage(t(action.safeAutomatic?'Safe correction applied to the draft.':'Correction applied to the draft; review it before compiling.'));}
        catch(error){setMessage(error instanceof Error?error.message:String(error));}
    }
    function navigateDiagnostic(item:GrammarDiagnostic){setSelectedDocumentId(item.documentId);setFocusDiagnostic(item);}
    function navigateSymbol(symbol:GrammarSymbol){setSelectedDocumentId(symbol.documentId);setFocusDiagnostic({code:'NAVIGATION',severity:'INFO',title:'',message:'',cause:'',consequence:'',solution:'',documentId:symbol.documentId,range:symbol.range,rule:'',property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata:{}});}
    function navigateDiagramSource(source:GrammarDiagramSourceRef){if(source.documentId==='')return;setWorkspaceView('SOURCE');setSelectedDocumentId(source.documentId);setFocusDiagnostic({code:'DIAGRAM_NAVIGATION',severity:'INFO',title:'',message:'',cause:'',consequence:'',solution:'',documentId:source.documentId,range:source.range,rule:source.ruleName,property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata:{grammarElementId:source.grammarElementId}});}
    function navigateRuleSyntax(documentId:string,range:{start:{line:number;column:number};end:{line:number;column:number}}){setWorkspaceView('SOURCE');setSelectedDocumentId(documentId);setFocusDiagnostic({code:'RULE_GRAPH_NAVIGATION',severity:'INFO',title:'',message:'',cause:'',consequence:'',solution:'',documentId,range,rule:'',property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata:{}});}
    function addDocument(){const index=model.documents.length+1;const extension=model.technology==='ANTLR'?'g4':model.technology==='XTEXT'?'xtext':'langium';const id=`source-${index}`;const doc:GrammarSourceDocument={id,relativePath:`source-${index}.${extension}`,languageId:model.technology.toLowerCase(),content:'',sourceHash:'',entryPoint:false,generated:false};setSelectedDocumentId(id);mutate({...model,documents:[...model.documents,doc]});}
    function removeDocument(id:string){const docs=model.documents.filter(doc=>doc.id!==id);setSelectedDocumentId(docs[0]?.id??'');mutate({...model,documents:docs});}

    return <main className="grammar-designer">
        <header className="grammar-designer-header">
            <div className="grammar-designer-title-group">
                <h2>{model.name||t('New grammar')}</h2>
                <p>{t('Create the grammar and let HYDRA infer, check and generate the required infrastructure automatically.')}</p>
            </div>
            <div className="grammar-header-workspace">
                <label className="grammar-saved-selector"><span>{t('Saved grammars')}</span><select aria-label={t('Saved grammars')} value={persisted.some(item=>item.id===model.id)?model.id:''} onChange={event=>{if(event.target.value==='')newGrammar();else void openGrammar(event.target.value);}}><option value="">{t('New grammar')}</option>{persisted.map(item=><option key={item.id} value={item.id}>{item.name} · {item.technology} · {item.id}</option>)}</select></label>
                <div className="grammar-header-actions">
                    <div className="hydra-button-with-tooltip"><HydraButton type="button" onClick={newGrammar}>{t('New grammar')}</HydraButton><HydraTooltip label={t('Information about New grammar')} title={t('New grammar')}><p>{t('Create a fresh grammar draft without deleting the grammars you already saved.')}</p><p>{t('Use this action when you want to start another language definition from a clean editor state.')}</p></HydraTooltip></div>
                    <div className="hydra-button-with-tooltip"><HydraButton type="button" onClick={()=>void save()}>{t('Save grammar')}</HydraButton><HydraTooltip label={t('Information about Save grammar')} title={t('Save grammar')}><p>{t('Store the current grammar definition so you can reopen it later with the same identity and documents.')}</p><p>{t('Saving preserves the editable grammar source. It does not compile or activate it yet.')}</p></HydraTooltip></div>
                    <div className="hydra-button-with-tooltip"><HydraButton type="button" onClick={()=>void generateLanguageProject()}>{t('Generate language project')}</HydraButton><HydraTooltip label={t('Information about Generate language project')} title={t('Generate language project')}><p>{t('Generate the project files for the selected technology after running the required checks, without replacing the current active compiled version.')}</p></HydraTooltip></div>
                    <div className="hydra-button-with-tooltip"><HydraButton variant="primary" type="button" onClick={()=>void compileAndActivate()}>{t('Compile grammar')}</HydraButton><HydraTooltip label={t('Information about Compile grammar')} title={t('Compile grammar')}><p>{t('Run the complete grammar verification and activate the compiled version only if every mandatory step succeeds.')}</p><p>{t('This is the main action to validate the language, generate its infrastructure and leave it ready to use.')}</p></HydraTooltip></div>
                </div>
            </div>
        </header>
        <GrammarIdentityForm
            model={model}
            displayedTechnology={requestedTechnology??model.technology}
            analysis={currentAnalysis}
            localInspection={localInspection}
            compatibleProfiles={compatibleProfiles}
            metamodels={metamodels}
            onMutate={mutate}
            onTechnologyChange={switchTechnology}
            onMetamodelsRefresh={refreshMetamodels}
        />
        {operation&&<HydraOperationProgress active title={operation.kind==='CONVERSION'?t('Preparing grammar conversion'):t('Compiling grammar')} detail={operation.detail} progress={operation.progress} currentStageId={operation.stage} stages={operation.kind==='CONVERSION'?[{id:'prepare',label:t('Read and transform grammar')},{id:'review',label:t('Verify semantic equivalence')}]:GRAMMAR_COMPILATION_PROGRESS_STAGES.map(stage=>({id:stage.id,label:t(stage.label)}))} />}
        <section className="grammar-workbench">
            <aside>
                <GrammarSourceExplorer
                    documents={model.documents}
                    selectedId={selected?.id??''}
                    onSelect={id=>{setSelectedDocumentId(id);setWorkspaceView('SOURCE');}}
                    onAdd={addDocument}
                    onRemove={removeDocument}
                />
                <div className="grammar-outline">
                    <header><strong>{t('Structure')}</strong></header>
                    <GrammarStructureTree
                        symbols={currentAnalysis?.symbols??localInspection.symbols}
                        unusedRules={unusedRuleNames}
                        documentId={selected?.id??''}
                        onSelect={symbol=>{navigateSymbol(symbol);setWorkspaceView('SOURCE');}}
                    />
                </div>
            </aside>
            <div className="grammar-editor-column">
                <div className="grammar-workbench-view-tabs">
                    <HydraButton type="button" className={workspaceView==='SOURCE'?'active':''} onClick={()=>setWorkspaceView('SOURCE')}>{t('Grammar source')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='DIAGRAM'?'active':''} onClick={()=>setWorkspaceView('DIAGRAM')}>{t('Inferred class diagram')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='METAMODEL'?'active':''} onClick={()=>setWorkspaceView('METAMODEL')}>{t('Inferred metamodel')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='RULE_GRAPHS'?'active':''} onClick={()=>setWorkspaceView('RULE_GRAPHS')}>{t('Syntax graphs of rules')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='GENERATED_CODE'?'active':''} onClick={()=>setWorkspaceView('GENERATED_CODE')}>{t('Generated code')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='TEXTUAL_APPEARANCE'?'active':''} onClick={()=>setWorkspaceView('TEXTUAL_APPEARANCE')}>{t('Editor appearance')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='TEXTUAL_HOVER'?'active':''} onClick={()=>setWorkspaceView('TEXTUAL_HOVER')}>{t('Contextual information')}</HydraButton>
                    <HydraButton type="button" className={workspaceView==='TEXTUAL_PREVIEW'?'active':''} onClick={()=>setWorkspaceView('TEXTUAL_PREVIEW')}>{t('Editor preview')}</HydraButton>
                    <span>{classDiagram===null?'—':`${classDiagram.nodes.length} ${t('classifiers')} · ${t(diagramStatusLabel(classDiagram.status))}`}</span>
                </div>
                {workspaceView==='SOURCE'?<>
                    <div className="grammar-document-toolbar">
                        <strong>{selected?.relativePath}</strong>
                        <span>{t(providerHealthLabel(analysis?.providerHealth.status??'ANALYZING'))}</span>
                        <span>{blocking.length} {t('errors')}</span>
                        <span>{t(analysisModeLabel(analysis?.mode))}</span>
                    </div>
                    {selected&&<GrammarMonacoEditor
                        key={selected.id}
                        value={selected.content}
                        languageId={selected.languageId}
                        diagnostics={editorDiagnostics.filter(item=>item.documentId===selected.id)}
                        onChange={updateSource}
                        focusRange={focusDiagnostic?.documentId===selected.id?focusDiagnostic.range:undefined}
                        codeActions={effectiveCodeActions}
                        documentId={selected.id}
                        symbols={currentAnalysis?.symbols??localInspection.symbols}
                    />}
                </>:workspaceView==='DIAGRAM'?<GrammarClassDiagram projection={classDiagram?.draftRevision===revision?classDiagram:null} loading={projectionLoading} error={projectionError} onNavigate={navigateDiagramSource}/>:workspaceView==='METAMODEL'?<GrammarInferredMetamodelView metamodel={classDiagram?.draftRevision===revision?classDiagram.inferredMetamodel:null} loading={projectionLoading} error={projectionError}/>:workspaceView==='RULE_GRAPHS'?<GrammarRuleSyntaxGraphs model={model} unusedRules={unusedRuleNames} onNavigate={navigateRuleSyntax}/>:workspaceView==='GENERATED_CODE'?<GrammarGeneratedCodeView snapshot={snapshot}/>:workspaceView==='TEXTUAL_APPEARANCE'?<GrammarTextualCustomization mode="APPEARANCE" model={model} symbols={currentAnalysis?.symbols??localInspection.symbols} onChange={mutate}/>:workspaceView==='TEXTUAL_HOVER'?<GrammarTextualCustomization mode="HOVER" model={model} symbols={currentAnalysis?.symbols??localInspection.symbols} onChange={mutate}/>:<GrammarTextualCustomization mode="PREVIEW" model={model} symbols={currentAnalysis?.symbols??localInspection.symbols} onChange={mutate}/>} 
            </div>
        </section>
        <GrammarStatusDock diagnostics={visibleDiagnostics} actions={effectiveCodeActions} message={message} snapshot={snapshot} provider={provider} projection={classDiagram} entryRuleInference={entryRuleInference} onNavigate={navigateDiagnostic} onApply={applyAction} activeTab={statusDockTab} onTabChange={setStatusDockTab}/>
        {conversionPreview&&<GrammarPlatformConversionDialog preview={conversionPreview} onCancel={()=>{setConversionPreview(null);setRequestedTechnology(null);setMessage(t('Conversion cancelled. The current grammar was not changed.'));}} onApply={applyConversion}/>}
        {overwriteDialogs}
    </main>;
}
export default GrammarDesigner;

const GRAMMAR_COMPILATION_PROGRESS_STAGES=[
    {id:'FREEZE_DRAFT',label:'Prepare current grammar'},{id:'FULL_ANALYSIS',label:'Check the complete grammar'},{id:'RESOLVE_IMPORTS',label:'Resolve imports and references'},
    {id:'VALIDATE_SEMANTIC_BINDING',label:'Check inferred model links'},{id:'RECONCILE_SEMANTIC_CONTRACT',label:'Consolidate inferred model'},{id:'CHECK_HYDRA_LAYERS',label:'Check HYDRA features'},
    {id:'GENERATE_PROJECT',label:'Generate language project'},{id:'GENERATE_SEMANTIC_ARTIFACTS',label:'Generate model and diagram'},{id:'MATERIALIZE_RUNTIME',label:'Prepare execution services'},
    {id:'SMOKE_TEST',label:'Verify generated infrastructure'},{id:'REGISTER_ARTIFACTS',label:'Register generated files'},{id:'PROMOTE_SNAPSHOT',label:'Activate successful compilation'}
] as const;
function mergeGrammarCodeActions(items:readonly GrammarCodeAction[]):GrammarCodeAction[]{const merged=new Map<string,GrammarCodeAction>();for(const item of items)merged.set(item.id,item);return [...merged.values()];}
function mergeGrammarDiagnostics(items:readonly GrammarDiagnostic[]):GrammarDiagnostic[]{
    const merged=new Map<string,GrammarDiagnostic>();
    for(const item of items){
        const key=[item.code,item.documentId,item.range.start.line,item.range.start.column,item.range.end.line,item.range.end.column,item.rule,item.property].join('|');
        const existing=merged.get(key);
        if(existing===undefined||diagnosticPriority(item)>diagnosticPriority(existing))merged.set(key,item);
    }
    return [...merged.values()].sort((a,b)=>diagnosticPriority(b)-diagnosticPriority(a)||a.documentId.localeCompare(b.documentId)||a.range.start.line-b.range.start.line||a.range.start.column-b.range.start.column||a.code.localeCompare(b.code));
}
function diagnosticPriority(item:GrammarDiagnostic):number{return item.severity==='ERROR'?3:item.severity==='WARNING'?2:1;}

function compilationStageDetail(stage:string):string{const labels:Record<string,string>={FREEZE_DRAFT:'Preparing the current grammar…',FULL_ANALYSIS:'Checking the complete grammar and all its rules…',RESOLVE_IMPORTS:'Resolving imports and references…',VALIDATE_SEMANTIC_BINDING:'Checking how grammar elements map to the inferred model…',RECONCILE_SEMANTIC_CONTRACT:'Consolidating the inferred model…',CHECK_HYDRA_LAYERS:'Checking validations, events, flows and gamification linked to this language…',GENERATE_PROJECT:'Generating the language project…',GENERATE_SEMANTIC_ARTIFACTS:'Generating the inferred model and class diagram…',MATERIALIZE_RUNTIME:'Preparing parser and editor execution services…',SMOKE_TEST:'Running checks on the generated infrastructure…',REGISTER_ARTIFACTS:'Registering generated files…',PROMOTE_SNAPSHOT:'Activating the successful compilation…'};return labels[stage]??'Compiling the grammar…';}
function providerHealthLabel(status:string):string{
    if(status==='HEALTHY')return 'Provider healthy';
    if(status==='DEGRADED')return 'Provider degraded';
    if(status==='UNAVAILABLE')return 'Provider unavailable';
    return 'Analyzing';
}
function analysisModeLabel(mode:GrammarAnalysisMode|undefined):string{return mode===undefined?'Analyzing':mode==='FAST'?'Fast analysis':mode==='NORMAL'?'Normal analysis':'Full analysis';}
function operationDiagnostic(documentId:string,title:string,message:string):GrammarDiagnostic{const detail=normalizeOperationError(message);return {code:'GRAMMAR_OPERATION_ERROR',severity:'ERROR',title:detail.title===message?title:detail.title,message:detail.message,cause:detail.cause,consequence:detail.consequence,solution:detail.solution,documentId,range:{start:{line:0,column:0},end:{line:0,column:1}},rule:'',property:'',expectedType:'',nativeCode:'',technicalDetail:detail.technicalDetail,candidates:[],codeActionIds:[],metadata:{source:'ui-operation'}};}
function diagramStatusLabel(status:GrammarClassDiagramProjection['status']):string{return status==='COMPLETE'?'Complete inference':status==='PARTIAL'?'Partial inference':status==='LAST_KNOWN_GOOD'?'Last valid inference':status==='EMPTY'?'No inferred structure':'Inference failed';}
function describeOperationError(message:string,t:(key:string)=>string):string{return t(normalizeOperationError(message).message);}
function normalizeOperationError(message:string):{title:string;message:string;cause:string;consequence:string;solution:string;technicalDetail:string}{
    const raw=message.trim();
    if(raw==='FULL analysis became stale before compilation completed'){
        return {
            title:'The grammar changed during compilation',
            message:'HYDRA cancelled the compilation because the draft changed while the complete analysis was still running.',
            cause:'The source was edited or another analysis request superseded the version being compiled.',
            consequence:'The last valid snapshot stays active and the inconsistent generation is discarded.',
            solution:'Wait until the automatic analysis finishes, then compile again without editing the grammar during the process.',
            technicalDetail:raw
        };
    }
    if(raw==='FULL analysis contains blocking diagnostics'){
        return {
            title:'The grammar still has blocking errors',
            message:'HYDRA detected grammar errors that must be corrected before compiling.',
            cause:'At least one grammar diagnostic with severity ERROR is still active.',
            consequence:'The generated infrastructure is not promoted so the last valid snapshot remains active.',
            solution:'Review the diagnostics in the Problems panel, apply the available quick fixes when appropriate, and compile again after correcting the grammar.',
            technicalDetail:raw
        };
    }
    return {title:raw,message:raw,cause:'',consequence:'',solution:'',technicalDetail:raw};
}
