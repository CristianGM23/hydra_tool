import { useEffect, useMemo, useState } from 'react';
import { HydraSelect } from '../design-system';
import { loadMetaModel } from '../metamodel-designer/api';
import type { MetaModel } from '../metamodel-designer/types';
import { listEditorDefinitions, loadEditorRuntime } from '../editor-definition/api';
import { EditorRuntimeSession } from '../editor-definition/EditorRuntimeSession';
import type { EditorDefinitionCompilationResult, EditorDefinitionSummary } from '../editor-definition/types';
import { useHydraLanguage } from '../i18n';

interface Props {
    metamodelId:string;
    sourceModelId?:string;
    onAvailabilityChange:(available:boolean)=>void;
    onError:(message:string)=>void;
    onMessage:(message:string)=>void;
}
interface DiscoveryState { metamodelId:string; editors:EditorDefinitionSummary[]; metamodel:MetaModel|null; }
interface RuntimeState { key:string; value:EditorDefinitionCompilationResult|null; failed:boolean; }
const EMPTY_EDITORS:EditorDefinitionSummary[]=[];

/**
 * Resolves the configured editing profile for the active metamodel. This is not a second model editor:
 * it is the visual/palette/form configuration consumed by the single Model Editor workbench.
 * Availability becomes true only after the compiled runtime is actually usable; a broken configuration
 * therefore falls back to the basic editor instead of leaving the workbench blank.
 */
export function ContextualModelEditor({metamodelId,sourceModelId='',onAvailabilityChange,onError,onMessage}:Props){
    const {t}=useHydraLanguage();
    const [discovery,setDiscovery]=useState<DiscoveryState>({metamodelId:'',editors:EMPTY_EDITORS,metamodel:null});
    const [selectedEditorId,setSelectedEditorId]=useState('');
    const [runtimeState,setRuntimeState]=useState<RuntimeState>({key:'',value:null,failed:false});
    const scoped=discovery.metamodelId===metamodelId?discovery:null;
    const editors=scoped?.editors??EMPTY_EDITORS;
    const selectedEditor=editors.find(summary=>summary.id===selectedEditorId)??editors[0]??null;
    const runtimeKey=selectedEditor===null?'':`${metamodelId}:${selectedEditor.id}:${selectedEditor.etag}`;
    const currentRuntimeState=runtimeState.key===runtimeKey?runtimeState:null;
    const runtime=currentRuntimeState?.value??null;
    const runtimeFailed=currentRuntimeState?.failed===true;
    const loading=scoped===null;
    const compiling=runtimeKey!==''&&runtime===null&&!runtimeFailed;
    const options=useMemo(()=>editors.map(summary=>({value:summary.id,label:summary.name,description:`${summary.representationCount} ${t('representations')} · ${summary.toolCount} ${t('tools')}`,category:t('Editor configurations')})),[editors,t]);

    useEffect(()=>{
        let live=true;
        listEditorDefinitions(metamodelId).then(async summaries=>{
            if(!live)return;
            const associated=summaries.filter(summary=>summary.metamodelId===metamodelId);
            if(associated.length===0){
                setDiscovery({metamodelId,editors:EMPTY_EDITORS,metamodel:null});
                setSelectedEditorId('');
                onAvailabilityChange(false);
                return;
            }
            const loaded=await loadMetaModel(metamodelId);
            if(!live)return;
            setDiscovery({metamodelId,editors:associated,metamodel:loaded.metamodel});
            setSelectedEditorId(current=>associated.some(item=>item.id===current)?current:associated[0]?.id??'');
            // Do not report availability yet: the selected compiled runtime must load successfully first.
        }).catch(exception=>{
            if(!live)return;
            setDiscovery({metamodelId,editors:EMPTY_EDITORS,metamodel:null});
            setSelectedEditorId('');
            setRuntimeState({key:'',value:null,failed:true});
            onAvailabilityChange(false);
            onError(exception instanceof Error?exception.message:'The configured model editor could not be discovered.');
        });
        return()=>{live=false;};
    },[metamodelId,onAvailabilityChange,onError]);

    useEffect(()=>{
        if(runtimeKey===''||selectedEditor===null)return;
        let live=true;
        loadEditorRuntime(metamodelId,selectedEditor.id).then(value=>{
            if(!live)return;
            setRuntimeState({key:runtimeKey,value,failed:false});
            onAvailabilityChange(true);
        }).catch(exception=>{
            if(!live)return;
            setRuntimeState({key:runtimeKey,value:null,failed:true});
            onAvailabilityChange(false);
            onError(exception instanceof Error?exception.message:'The configured editor runtime could not be loaded.');
        });
        return()=>{live=false;};
    },[metamodelId,onAvailabilityChange,onError,runtimeKey,selectedEditor]);

    if(loading)return <section className="contextual-model-editor-status">{t('Loading visual representations for the selected metamodel…')}</section>;
    if(editors.length===0||runtimeFailed)return null;
    return <section className="contextual-model-editor" aria-label={t('Configured visual model editor')}>
        <header className="contextual-model-editor-profile"><div><strong>{t('Model editor configuration')}</strong><small>{t('This configuration groups the palette, forms, behavior and visual representations used by the same model editor. Changing the visual representation does not create another model.')}</small></div>{editors.length>1?<HydraSelect ariaLabel={t('Model editor configuration')} showFilter={editors.length>8} value={selectedEditor?.id??''} options={options} onChange={setSelectedEditorId}/>:<span className="contextual-model-editor-profile-name">{selectedEditor?.name}</span>}</header>
        {compiling&&<div className="contextual-model-editor-status">{t('Preparing the selected visual representations…')}</div>}
        {runtime!==null&&scoped?.metamodel!==null&&<EditorRuntimeSession key={runtimeKey} result={runtime} metamodel={scoped.metamodel} initialSourceModelId={sourceModelId} onError={onError} onMessage={onMessage}/>} 
    </section>;
}
