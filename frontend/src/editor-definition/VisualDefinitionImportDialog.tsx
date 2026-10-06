import { useEffect, useMemo, useRef, useState } from 'react';
import type { CanonicalMetamodelSummary, MetaModel } from '../metamodel-designer/types';
import { listCanonicalMetamodels } from '../metamodel-designer/api';
import { HydraActionableErrorNotice, HydraButton, HydraDiagnosticList, HydraModal, HydraTooltip, HydraSelect, openFileInputPicker } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { commitVisualImportAssociations, importDrawioEditorDefinition, importGmfEditorDefinition, importSiriusEditorDefinition, importSiriusWebEditorDefinition } from './api';
import type { EditorDefinitionModel, VisualDefinitionImportFix, VisualDefinitionImportResult, VisualMetamodelAssociationMode } from './types';
import './visual-definition-import.css';

type ImportKind='SIRIUS_DESKTOP'|'SIRIUS_WEB'|'GMF'|'DRAWIO';
interface Props { metamodel:MetaModel; onImported:(model:EditorDefinitionModel)=>void; }
const accept:Record<ImportKind,string>={
    SIRIUS_DESKTOP:'.odesign',
    SIRIUS_WEB:'.json,.zip,.java',
    GMF:'.gmfgraph,.gmftool,.gmfmap,.gmfgen,.ecore,.genmodel,.xml,.ocl,.java,.png,.gif,.svg,.zip',
    DRAWIO:'.drawio,.drawio.xml,.xml,.mxlibrary,.zip'
};
const metamodelAccept='.ecore,.xmi,.emf,.json,.xml,.zip';

export function VisualDefinitionImportButton({metamodel,onImported}:Props){
    const {t}=useHydraLanguage(); const [open,setOpen]=useState(false);
    return <><HydraButton data-hydra-menu-keep-open="true" type="button" onClick={()=>setOpen(true)}>{t('Import visual definition')}</HydraButton><VisualDefinitionImportDialog open={open} metamodel={metamodel} onClose={()=>setOpen(false)} onImported={model=>{onImported(model);setOpen(false);}}/></>;
}

function VisualDefinitionImportDialog({open,metamodel,onClose,onImported}:{open:boolean;metamodel:MetaModel;onClose:()=>void;onImported:(model:EditorDefinitionModel)=>void}){
    const {t}=useHydraLanguage();
    const [kind,setKind]=useState<ImportKind>('SIRIUS_DESKTOP');
    const [files,setFiles]=useState<File[]>([]); const [javaFiles,setJavaFiles]=useState<File[]>([]);
    const [associationMode,setAssociationMode]=useState<VisualMetamodelAssociationMode>('EXISTING');
    const [selectedMetamodelId,setSelectedMetamodelId]=useState(metamodel.id); const [availableMetamodels,setAvailableMetamodels]=useState<CanonicalMetamodelSummary[]>([]);
    const [metamodelResources,setMetamodelResources]=useState<File[]>([]);
    const [busy,setBusy]=useState(false); const [error,setError]=useState(''); const [result,setResult]=useState<VisualDefinitionImportResult|null>(null);
    const mainRef=useRef<HTMLInputElement>(null); const javaRef=useRef<HTMLInputElement>(null); const metamodelRef=useRef<HTMLInputElement>(null); const metamodelFolderRef=useRef<HTMLInputElement>(null);
    useEffect(()=>{if(!open)return;let active=true;void listCanonicalMetamodels().then(items=>{if(active)setAvailableMetamodels(items);}).catch(()=>{if(active)setAvailableMetamodels([{id:metamodel.id,name:metamodel.name,schemaVersion:metamodel.schemaVersion}]);});return()=>{active=false;};},[open,metamodel.id,metamodel.name,metamodel.schemaVersion]);
    const summary=useMemo(()=>result===null?[]:Object.entries(result.recovered),[result]);
    const safeFixes=useMemo(()=>result?.fixes.filter(fix=>fix.safety==='SAFE')??[],[result]);
    const reviewFixes=useMemo(()=>result?.fixes.filter(fix=>fix.safety!=='SAFE')??[],[result]);
    function resetAnalysis(){setResult(null);setError('');}
    function changeKind(value:ImportKind){setKind(value);setFiles([]);setJavaFiles([]);resetAnalysis();}
    function chooseVisualFiles(next:File[]){setFiles(next);resetAnalysis();if(next.some(file=>file.name.toLowerCase().endsWith('.zip')))setAssociationMode('EMBEDDED');}
    function chooseAssociation(mode:VisualMetamodelAssociationMode){setAssociationMode(mode);setMetamodelResources([]);resetAnalysis();}
    async function analyze(){setBusy(true);setError('');setResult(null);try{
        if(associationMode==='EXTERNAL'&&metamodelResources.length===0)throw new Error(t('Choose the metamodel file, folder or ZIP that belongs to this visual representation.'));
        const association={mode:associationMode,metamodelResources}; const ownerId=associationMode==='EXISTING'?selectedMetamodelId:metamodel.id;
        let next:VisualDefinitionImportResult;
        if(kind==='SIRIUS_DESKTOP'){const odesign=files[0];if(!odesign)throw new Error(t('Choose a Sirius .odesign file first.'));next=await importSiriusEditorDefinition(ownerId,odesign,javaFiles,association);}
        else {if(files.length===0)throw new Error(t('Choose at least one import resource first.'));if(kind==='SIRIUS_WEB')next=await importSiriusWebEditorDefinition(ownerId,files,association);else if(kind==='GMF')next=await importGmfEditorDefinition(ownerId,files,association);else next=await importDrawioEditorDefinition(ownerId,files,association);}
        setResult(next);
    }catch(exception){setError(exception instanceof Error?exception.message:t('Visual definition import failed.'));}finally{setBusy(false);}}
    async function confirmImport(){if(result===null)return;setBusy(true);setError('');try{const committed=await commitVisualImportAssociations(result.associatedMetamodel.id,result);onImported(committed.editorDefinition);}catch(exception){setError(exception instanceof Error?exception.message:t('Visual definition import failed.'));}finally{setBusy(false);}}
    return <HydraModal open={open} title={t('Import visual definition')} description={t('Analyze, validate and recover a visual editor from Sirius Desktop, Sirius Web, GMF or draw.io/diagrams.net. Before mapping anything, choose the semantic metamodel that the representation belongs to.')} onClose={onClose} size="wide" className="visual-definition-import-modal" footer={<><HydraButton variant="ghost" onClick={onClose}>{t('Cancel')}</HydraButton><HydraButton disabled={result===null||busy||result.analysis.importReady!==true} loading={busy} onClick={()=>void confirmImport()}>{t('Use recovered editor')}</HydraButton></>}>
        <div className="visual-definition-import-grid">
            <section className="visual-definition-import-source"><h3>{t('Import source')}</h3><label>{t('Technology')}<HydraSelect ariaLabel="Technology" value={kind} options={[{value:'SIRIUS_DESKTOP',label:'Sirius Desktop (.odesign)',description:'Import a Sirius Desktop viewpoint specification.'},{value:'SIRIUS_WEB',label:'Sirius Web (View DSL / EMF JSON)',description:'Import Sirius Web view resources.'},{value:'GMF',label:'GMF (graph, tool, map and generator models)',description:'Import GMF visual and generator definitions.'},{value:'DRAWIO',label:'draw.io / diagrams.net (mxGraph project or library)',description:'Recover visual definitions from draw.io or mxGraph resources.'}]} onChange={value=>changeKind(value as ImportKind)} /></label>
                <fieldset className="visual-import-association"><legend>{t('Metamodel association')} <HydraTooltip label={t('Explain metamodel association')} title={t('Why is a metamodel required?')}><p>{t('A visual representation describes how semantic model elements are displayed and edited. HYDRA must know the exact metamodel behind those elements before it can validate classes, relations, tools or expressions.')}</p></HydraTooltip></legend>
                    <label><input type="radio" checked={associationMode==='EXISTING'} onChange={()=>chooseAssociation('EXISTING')}/><span><strong>{t('Use a metamodel already in HYDRA')}</strong><small>{t('Choose this when the semantic metamodel has already been imported or created in the current HYDRA installation.')}</small></span></label>
                    {associationMode==='EXISTING'&&<HydraSelect ariaLabel="Associated metamodel" value={selectedMetamodelId} options={(availableMetamodels.length===0?[{id:metamodel.id,name:metamodel.name}]:availableMetamodels).map(item=>({value:item.id,label:`${item.name} · ${item.id}`,description:'Existing HYDRA metamodel used to validate the imported visual definition.'}))} onChange={value=>{setSelectedMetamodelId(value);resetAnalysis();}} />}
                    <label><input type="radio" checked={associationMode==='EXTERNAL'} onChange={()=>chooseAssociation('EXTERNAL')}/><span><strong>{t('Import the associated metamodel from a file, folder or ZIP')}</strong><small>{t('HYDRA analyses the metamodel first and uses it to validate the visual definition. It is stored only when you confirm the import.')}</small></span></label>
                    {associationMode==='EXTERNAL'&&<div className="visual-import-metamodel-actions"><input ref={metamodelRef} hidden type="file" accept={metamodelAccept} multiple onChange={e=>{setMetamodelResources(Array.from(e.target.files??[]));resetAnalysis();}}/><HydraButton variant="ghost" onClick={()=>openFileInputPicker(metamodelRef.current)}>{t('Choose metamodel file or ZIP')}</HydraButton><input ref={metamodelFolderRef} hidden type="file" multiple onChange={e=>{setMetamodelResources(Array.from(e.target.files??[]));resetAnalysis();}}/><HydraButton variant="ghost" onClick={()=>{metamodelFolderRef.current?.setAttribute('webkitdirectory','');openFileInputPicker(metamodelFolderRef.current);}}>{t('Choose metamodel folder')}</HydraButton><small>{metamodelResources.length===0?t('No metamodel resource selected'):`${metamodelResources.length} ${t('metamodel resource(s) selected')}`}</small></div>}
                    <label><input type="radio" checked={associationMode==='EMBEDDED'} onChange={()=>chooseAssociation('EMBEDDED')}/><span><strong>{t('Detect the metamodel packaged inside the visual ZIP')}</strong><small>{t('When a visual ZIP contains its semantic metamodel, HYDRA searches the archive, analyses viable candidates and refuses to guess if several different metamodels are found.')}</small></span></label>
                </fieldset>
                {kind==='DRAWIO'&&<div className="visual-import-note"><strong>{t('draw.io recovery')}</strong><p>{t('Import .drawio/XML projects, multi-page mxGraph models, compressed pages and custom mxlibrary libraries. HYDRA recovers compatible shapes, dimensions, colors and connectors and maps them to metamodel elements when the correspondence is reliable.')}</p></div>}
                <input ref={mainRef} hidden type="file" accept={accept[kind]} multiple={kind!=='SIRIUS_DESKTOP'} onChange={e=>chooseVisualFiles(Array.from(e.target.files??[]))}/><HydraButton onClick={()=>openFileInputPicker(mainRef.current)}>{kind==='SIRIUS_DESKTOP'?t('Choose .odesign'):t('Choose files or ZIP')}</HydraButton><span className="visual-import-file-count">{files.length===0?t('No source selected'):`${files.length} ${t('file(s) selected')}`}</span>
                {kind==='SIRIUS_DESKTOP'&&<><input ref={javaRef} hidden type="file" accept=".java" multiple onChange={e=>setJavaFiles(Array.from(e.target.files??[]))}/><HydraButton variant="ghost" onClick={()=>openFileInputPicker(javaRef.current)}>{t('Add Java service sources')}</HydraButton><small>{t('Optional. Sirius stores Java extension references in the .odesign; supplying the matching .java files lets HYDRA recover public service signatures without executing them.')}</small></>}
                <HydraButton loading={busy} disabled={busy} onClick={()=>void analyze()}>{t('Analyze deeply and recover')}</HydraButton>{error!==''&&<HydraActionableErrorNotice message={error} context="visual definition import" onDismiss={()=>setError('')} />}
            </section>
            <section className="visual-definition-import-preview"><h3>{t('Analysis and recovery')}</h3>{result===null?<p>{t('Run the analysis to inspect structure, metamodel references, parameters, expressions, visual mappings and safe correction opportunities before importing anything.')}</p>:<>
                <ImportQuality result={result}/>
                <div className="visual-import-association-result"><strong>{t('Associated metamodel')}</strong><span>{result.associatedMetamodel.name} · {result.associatedMetamodel.id}</span><small>{t(result.metamodelAssociation.explanation)}</small>{!result.metamodelAssociation.alreadyPersisted&&<small>{t('This metamodel has only been analysed so far. HYDRA will store it when you confirm the visual import.')}</small>}</div>
                <div className="visual-import-metrics">{summary.map(([key,value])=><div key={key}><strong>{value}</strong><span>{key}</span></div>)}</div>
                <p><strong>{t('Technology')}:</strong> {result.technology}</p><p><strong>{t('Java services')}:</strong> {result.javaServices.length} · <strong>{t('Validations')}:</strong> {result.validations.length} · <strong>{t('Expressions')}:</strong> {result.expressions.length}</p>
                {kind==='SIRIUS_DESKTOP'&&result.validationDetails.length>0&&<div className="visual-import-validations"><strong>{t('Validations found in the .odesign')}</strong><p>{t('When you confirm the import, these validations will be added to the Validations layer and linked to the visual representation they came from. Their original Sirius/AQL expression is preserved; rules that cannot be converted safely remain external and disabled until reviewed.')}</p><ul>{result.validationDetails.slice(0,12).map((validation,index)=><li key={`${validation.source}-${validation.name}-${index}`}><strong>{validation.name}</strong><span>{validation.representationId||t('Representation not identified')}</span></li>)}</ul></div>}
                {safeFixes.length>0&&<FixList title={t('Safe corrections applied automatically')} fixes={safeFixes}/>} 
                {reviewFixes.length>0&&<FixList title={t('Corrections that require review')} fixes={reviewFixes}/>} 
                {result.diagnostics.length>0&&<details open><summary>{t('Import diagnostics')} ({result.diagnostics.length})</summary><HydraDiagnosticList diagnostics={result.diagnostics.slice(0,80).map(item=>({severity:item.severity,code:item.code,message:item.message,path:item.location,source:item.source,reason:item.explanation,solution:item.suggestedAction}))} context="visual import" title="Import diagnostics" /></details>}
                {result.preservedArtifacts.length>0&&<details><summary>{t('Preserved external semantics')} ({result.preservedArtifacts.length})</summary><p>{t('These constructs remain recorded in the imported editor metadata because HYDRA cannot translate them safely without changing their meaning.')}</p></details>}
            </>}</section>
        </div>
    </HydraModal>;
}

function ImportQuality({result}:{result:VisualDefinitionImportResult}){
    const {t}=useHydraLanguage(); const a=result.analysis; const needsReview=a.unresolvedReferences+a.ambiguousReferences+a.structuralProblems>0;
    return <div className={`visual-import-quality ${needsReview?'review':'ready'}`}><div><strong>{needsReview?t('Review recommended before import'):t('Import analysis is clean')}</strong><span>{a.elementsInspected} {t('elements inspected')} · {a.referencesChecked} {t('references checked')}</span></div><div className="visual-import-quality-grid"><Metric label={t('Unresolved references')} value={a.unresolvedReferences}/><Metric label={t('Ambiguous references')} value={a.ambiguousReferences}/><Metric label={t('Invalid parameters')} value={a.invalidParameters}/><Metric label={t('Structural problems')} value={a.structuralProblems}/><Metric label={t('Safe corrections')} value={a.safeCorrections}/><Metric label={t('Needs review')} value={a.reviewCorrections+a.manualActions}/></div><small>{t('HYDRA never creates or changes metamodel elements automatically. Safe corrections are limited to deterministic import normalization; semantic guesses remain reviewable.')}</small></div>;
}
function Metric({label,value}:{label:string;value:number}){return <span><strong>{value}</strong>{label}</span>;}
function FixList({title,fixes}:{title:string;fixes:VisualDefinitionImportFix[]}){const {t}=useHydraLanguage();return <details open><summary>{title} ({fixes.length})</summary><ul className="visual-import-fixes">{fixes.slice(0,60).map(fix=><li key={fix.id}><strong data-hydra-runtime-message="true" data-hydra-message-context="import" data-hydra-message-code={fix.code}>{fix.title}</strong><span data-hydra-runtime-message="true" data-hydra-message-context="import" data-hydra-message-code={fix.code}>{fix.description}</span><small>{t(fix.safety)} · {t(fix.confidence)}{fix.applied?` · ${t('applied')}`:''}</small></li>)}</ul></details>;}
