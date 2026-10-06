import { useEffect, useMemo, useState } from 'react';
import { HydraButton, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { CanonicalModelDocument } from '../validation/types';
import {
    createTransformationSynchronizationSession,
    deleteTransformationSynchronizationSession,
    listTransformationSynchronizationHistory,
    listTransformationSynchronizationSessions,
    loadTransformationSynchronizationSession,
    restoreTransformationSynchronizationSession,
    rebaseTransformationSynchronizationSession,
    synchronizeTransformationSession,
} from './api';
import type {
    TransformationSynchronizationCheckpoint,
    TransformationSynchronizationSession,
    TransformationSynchronizationSessionSummary,
    TransformationSynchronizationSide,
} from './types';

interface Props {
    transformationId:string;
    sourceText:string;
    targetText:string;
    onSourceTextChange:(value:string)=>void;
    onTargetTextChange:(value:string)=>void;
}

export function TransformationSynchronizationSessionsPanel({transformationId,sourceText,targetText,onSourceTextChange,onTargetTextChange}:Props){
    const {t}=useHydraLanguage();
    const [summaries,setSummaries]=useState<TransformationSynchronizationSessionSummary[]>([]);
    const [session,setSession]=useState<TransformationSynchronizationSession|null>(null);
    const [sessionId,setSessionId]=useState('');
    const [etag,setEtag]=useState('');
    const [history,setHistory]=useState<TransformationSynchronizationCheckpoint[]>([]);
    const [initializeFrom,setInitializeFrom]=useState<Exclude<TransformationSynchronizationSide,'BOTH'>>('SOURCE');
    const [busy,setBusy]=useState(false);
    const [error,setError]=useState('');
    const [message,setMessage]=useState('');

    async function refreshSummaries(){
        const items=await listTransformationSynchronizationSessions(transformationId);
        setSummaries(items);
        return items;
    }
    async function refreshHistory(id:string){setHistory(await listTransformationSynchronizationHistory(transformationId,id));}

    useEffect(()=>{let active=true;void listTransformationSynchronizationSessions(transformationId).then(items=>{if(active)setSummaries(items);}).catch(()=>{if(active)setSummaries([]);});return()=>{active=false;};},[transformationId]);

    const options=useMemo(()=>[
        {value:'',label:t('No managed session selected'),description:t('Create a session to let HYDRA manage the baseline and previous trace.')},
        ...summaries.map(item=>({value:item.id,label:`${item.id} · r${item.revision}`,description:`${item.sourceModelId} ↔ ${item.targetModelId}${item.stale?` · ${t('Rebase required')}`:''}`})),
    ],[summaries,t]);
    const selectedSummary=useMemo(()=>summaries.find(item=>item.id===sessionId)??null,[summaries,sessionId]);

    async function load(id:string){
        setSessionId(id);setSession(null);setHistory([]);setEtag('');setMessage('');setError('');
        if(id==='')return;
        setBusy(true);
        try{
            const loaded=await loadTransformationSynchronizationSession(transformationId,id);
            setSession(loaded.session);setEtag(loaded.etag);
            onSourceTextChange(JSON.stringify(loaded.session.sourceBaseline,null,2));
            onTargetTextChange(JSON.stringify(loaded.session.targetBaseline,null,2));
            await refreshHistory(id);
        }catch(reason){setError(reason instanceof Error?reason.message:String(reason));}
        finally{setBusy(false);}
    }

    async function create(){
        setBusy(true);setError('');setMessage('');
        try{
            const result=await createTransformationSynchronizationSession(transformationId,{
                sourceModel:JSON.parse(sourceText) as CanonicalModelDocument,
                targetModel:JSON.parse(targetText) as CanonicalModelDocument,
                initializeFrom,
            });
            setSession(result.session);setSessionId(result.session.id);setEtag(result.etag);
            onSourceTextChange(JSON.stringify(result.session.sourceBaseline,null,2));
            onTargetTextChange(JSON.stringify(result.session.targetBaseline,null,2));
            await refreshSummaries();await refreshHistory(result.session.id);
            setMessage(t('Managed synchronization session created.'));
        }catch(reason){setError(reason instanceof Error?reason.message:String(reason));}
        finally{setBusy(false);}
    }

    async function synchronize(){
        if(sessionId==='')return;
        setBusy(true);setError('');setMessage('');
        try{
            const result=await synchronizeTransformationSession(transformationId,sessionId,{
                sourceModel:JSON.parse(sourceText) as CanonicalModelDocument,
                targetModel:JSON.parse(targetText) as CanonicalModelDocument,
            },etag);
            setSession(result.session);setEtag(result.etag);
            if(result.synchronization!==null&&!result.synchronization.blocked){
                onSourceTextChange(JSON.stringify(result.synchronization.sourceModel,null,2));
                onTargetTextChange(JSON.stringify(result.synchronization.targetModel,null,2));
            }
            await refreshSummaries();await refreshHistory(sessionId);
            setMessage(t(result.synchronization?.blocked?'Synchronization blocked; the managed baseline was not advanced.':result.synchronization?.noOp?'No changes detected; the managed baseline remains unchanged.':'Synchronization committed and a new baseline revision was created.'));
        }catch(reason){setError(reason instanceof Error?reason.message:String(reason));}
        finally{setBusy(false);}
    }

    async function rebase(){
        if(sessionId==='')return;
        setBusy(true);setError('');setMessage('');
        try{
            const result=await rebaseTransformationSynchronizationSession(transformationId,sessionId,{
                sourceModel:JSON.parse(sourceText) as CanonicalModelDocument,
                targetModel:JSON.parse(targetText) as CanonicalModelDocument,
                initializeFrom,
            },etag);
            setSession(result.session);setEtag(result.etag);
            onSourceTextChange(JSON.stringify(result.session.sourceBaseline,null,2));
            onTargetTextChange(JSON.stringify(result.session.targetBaseline,null,2));
            await refreshSummaries();await refreshHistory(sessionId);
            setMessage(t('Managed baseline rebased against the current transformation definition.'));
        }catch(reason){setError(reason instanceof Error?reason.message:String(reason));}
        finally{setBusy(false);}
    }

    async function restore(revision:number){
        if(sessionId==='')return;
        setBusy(true);setError('');setMessage('');
        try{
            const result=await restoreTransformationSynchronizationSession(transformationId,sessionId,revision,etag);
            setSession(result.session);setEtag(result.etag);
            onSourceTextChange(JSON.stringify(result.session.sourceBaseline,null,2));
            onTargetTextChange(JSON.stringify(result.session.targetBaseline,null,2));
            await refreshSummaries();await refreshHistory(sessionId);
            setMessage(t('Historical baseline restored as a new revision.'));
        }catch(reason){setError(reason instanceof Error?reason.message:String(reason));}
        finally{setBusy(false);}
    }

    async function remove(){
        if(sessionId===''||!window.confirm(t('Delete this synchronization session and its retained history?')))return;
        setBusy(true);setError('');setMessage('');
        try{
            await deleteTransformationSynchronizationSession(transformationId,sessionId,etag);
            setSession(null);setSessionId('');setEtag('');setHistory([]);await refreshSummaries();
            setMessage(t('Managed synchronization session deleted.'));
        }catch(reason){setError(reason instanceof Error?reason.message:String(reason));}
        finally{setBusy(false);}
    }

    const operationLabel=(item:TransformationSynchronizationCheckpoint)=>item.operation.startsWith('RESTORED_FROM_')?`${t('Restored from revision')} ${item.operation.slice('RESTORED_FROM_'.length)}`:t(item.operation);

    return <section className="transformation-sync-session-panel">
        <header><div><strong>{t('Managed synchronization baseline')}</strong><span>{t('HYDRA stores the last accepted baseline and correspondence trace so incremental synchronization does not require pasting them manually.')}</span></div></header>
        <div className="transformation-sync-session-toolbar">
            <HydraSelect ariaLabel={t('Managed synchronization session')} value={sessionId} options={options} onChange={value=>void load(value)}/>
            <HydraSelect ariaLabel={t('Initial authoritative side')} value={initializeFrom} options={[
                {value:'SOURCE',label:t('Source side'),description:t('Build the first target baseline from the source model.')},
                {value:'TARGET',label:t('Target side'),description:t('Build the first source baseline from the target model.')},
            ]} onChange={value=>setInitializeFrom(value as Exclude<TransformationSynchronizationSide,'BOTH'>)}/>
            <HydraButton type="button" disabled={busy} onClick={()=>void create()}>{t('Create managed session')}</HydraButton>
            <HydraButton type="button" disabled={busy||sessionId===''||selectedSummary?.stale===true} onClick={()=>void synchronize()}>{t('Synchronize managed session')}</HydraButton>
            <HydraButton type="button" disabled={busy||sessionId===''} onClick={()=>void rebase()}>{t('Rebase with current transformation')}</HydraButton>
            <HydraButton type="button" disabled={busy||sessionId===''} onClick={()=>void remove()}>{t('Delete session')}</HydraButton>
        </div>
        {session!==null&&<div className="transformation-sync-session-status"><strong>{t('Baseline revision')}: {session.revision}</strong><span>{session.sourceBaseline.id} ↔ {session.targetBaseline.id}</span><small>{t('Transformation version')}: {session.transformationEtag}</small>{selectedSummary?.stale===true&&<small className="transformation-sync-session-stale">{t('The transformation changed. Rebase this session before incremental synchronization.')}</small>}</div>}
        {history.length>0&&<div className="transformation-sync-history"><strong>{t('Baseline history')}</strong><div>{history.map(item=><article key={item.id}><div><strong>{t('Revision')} {item.revision}</strong><span>{operationLabel(item)}</span><small>{new Date(item.createdAtEpochMillis).toLocaleString()}</small></div><HydraButton type="button" disabled={busy||session?.revision===item.revision} onClick={()=>void restore(item.revision)}>{t('Restore')}</HydraButton></article>)}</div></div>}
        {message!==''&&<div className="transformation-sync-session-message" role="status">{message}</div>}
        {error!==''&&<div className="transformation-preview-error" role="alert">{t(error)}</div>}
    </section>;
}
