import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraModal, HydraSelect } from '../design-system';
import { loadRemoteLayerArtifact, loadRemoteStorageProfiles, saveRemoteLayerArtifact } from './api';
import { RemoteStorageProfileFields } from './RemoteStorageProfileFields';
import type { RemoteArtifactDescriptor, RemoteStorageProfileDescriptor, RemoteWriteMode } from './types';

interface Props<T> {
    open: boolean;
    title: string;
    description: string;
    defaultFileName: string;
    document: T;
    onClose: () => void;
    onImport: (document: unknown) => void;
}

/** Transferencia JSON neutral reutilizable para capas canónicas que no tienen un formato interchange propio. */
export function RemoteJsonDocumentDialog<T>({ open, title, description, defaultFileName, document, onClose, onImport }: Props<T>) {
    const [mode, setMode] = useState<'SAVE'|'IMPORT'>('SAVE');
    const [profiles, setProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    const [profileId, setProfileId] = useState('');
    const [path, setPath] = useState(defaultFileName);
    const [writeMode, setWriteMode] = useState<RemoteWriteMode>('UPSERT');
    const [expectedVersion, setExpectedVersion] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState<RemoteArtifactDescriptor | null>(null);

    useEffect(() => {
        if(!open) return undefined;
        let active=true;
        void loadRemoteStorageProfiles().then(next=>{
            if(!active)return;
            setProfiles(next);
            setProfileId(current=>current||next.find(profile=>profile.enabled&&profile.configured)?.id||'');
        }).catch(exception=>{if(active)setError(exception instanceof Error?exception.message:'Could not load remote storage profiles.');});
        return()=>{active=false;};
    },[defaultFileName,open]);

    const selected=useMemo(()=>profiles.find(profile=>profile.id===profileId),[profileId,profiles]);
    const canRun=profileId!==''&&selected?.configured===true&&path.trim()!=='';

    async function execute(): Promise<void> {
        if(!canRun)return;
        setBusy(true);setError('');setResult(null);
        try {
            if(mode==='SAVE') {
                const content=JSON.stringify(document,null,2);
                const saved=await saveRemoteLayerArtifact(profileId,path.trim(),{fileName:defaultFileName,content,mimeType:'application/json'},{mode:writeMode,expectedVersion,commitMessage:`Save HYDRA ${title}`});
                setResult(saved);setExpectedVersion(saved.version);
            } else {
                const loaded=await loadRemoteLayerArtifact(profileId,path.trim());
                onImport(JSON.parse(loaded.content) as unknown);
                setExpectedVersion(loaded.version);
                onClose();
            }
        } catch(exception) { setError(exception instanceof Error?exception.message:'Remote layer transfer failed.'); }
        finally { setBusy(false); }
    }

    const footer=<><HydraButton onClick={onClose}>Close</HydraButton><HydraButton disabled={!canRun||busy} variant="primary" onClick={()=>void execute()}>{busy?'Working…':mode==='SAVE'?'Save layer remotely':'Import remote layer'}</HydraButton></>;
    return <HydraModal open={open} onClose={onClose} title={title} description={description} size="large" footer={footer} className="hydra-remote-json-dialog">
        <nav className="hydra-remote-project-tabs" aria-label="Remote layer operation"><HydraButton className={mode==='SAVE'?'active':''} type="button" onClick={()=>setMode('SAVE')}>Save / export</HydraButton><HydraButton className={mode==='IMPORT'?'active':''} type="button" onClick={()=>setMode('IMPORT')}>Import</HydraButton></nav>
        <RemoteStorageProfileFields profiles={profiles} profileId={profileId} path={path} disabled={busy} onProfileChange={value=>{setProfileId(value);setExpectedVersion('');}} onPathChange={value=>{setPath(value);setExpectedVersion('');}} />
        {mode==='SAVE'&&<section className="hydra-remote-project-options"><label><strong>Write policy</strong><HydraSelect ariaLabel="Write policy" disabled={busy} value={writeMode} options={[{value:'UPSERT',label:'Create or update',description:'Create the remote document when missing or update the existing one.'},{value:'CREATE_ONLY',label:'Create only',description:'Fail instead of replacing an existing remote document.'},{value:'UPDATE_ONLY',label:'Update only',description:'Fail when the remote document does not already exist.'}]} onChange={value=>setWriteMode(value as RemoteWriteMode)} /></label><p>This saves the HYDRA layer document independently from the complete HYDRA project. It does not change the local persisted version.</p></section>}
        {result!==null&&<div className="hydra-remote-project-result"><strong>{result.created?'Remote layer created':'Remote layer updated'}</strong><span>{result.provider} · {result.path}</span><small>{result.size.toLocaleString()} bytes · version {result.version||'not reported'}</small></div>}
        {error!==''&&<HydraActionableErrorNotice message={error} context="remote storage" onDismiss={()=>setError('')} />}
    </HydraModal>;
}
