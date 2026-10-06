import { useEffect, useMemo, useState } from 'react';
import { HydraButton, HydraModal, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { loadRemoteStorageProfiles, saveRemoteLayerArtifact } from '../remote-storage/api';
import type { RemoteStorageProfileDescriptor } from '../remote-storage/types';
import { exportProjectSelection } from './projectApi';

interface Props {
    open: boolean;
    paths: string[];
    projectId: string;
    projectName: string;
    onClose: () => void;
    onExported?: () => void;
}

function safeName(value: string): string {
    const normalized=value.trim().replace(/[^A-Za-z0-9._-]+/g,'-').replace(/^-+|-+$/g,'');
    return normalized || 'project';
}

export function ProjectSelectionRemoteExportDialog(props: Props) {
    if (!props.open) return null;
    return <ProjectSelectionRemoteExportDialogContent key={`${props.projectId}:${props.projectName}`} {...props} />;
}

function ProjectSelectionRemoteExportDialogContent({ paths, projectId, projectName, onClose, onExported }: Props) {
    const { t }=useHydraLanguage();
    const [profiles,setProfiles]=useState<RemoteStorageProfileDescriptor[]>([]);
    const [profileId,setProfileId]=useState('');
    const [remotePath,setRemotePath]=useState(()=>{const file=`${safeName(projectId||projectName)}-selection.zip`;return `projects/${safeName(projectId||projectName)}/${file}`;});
    const [busy,setBusy]=useState(false);
    const [message,setMessage]=useState('');

    useEffect(()=>{
        let active=true;
        void loadRemoteStorageProfiles().then(items=>{
            if(!active)return;
            const available=items.filter(item=>item.enabled&&item.configured);
            setProfiles(available);
            setProfileId(current=>available.some(item=>item.id===current)?current:(available[0]?.id??''));
        }).catch(error=>{if(active)setMessage(error instanceof Error?error.message:t('Could not load repository configurations.'));});
        return()=>{active=false;};
    },[t]);

    const options=useMemo(()=>profiles.map(profile=>({value:profile.id,label:`${profile.displayName} · ${profile.provider}`})),[profiles]);
    const canExport=paths.length>0&&profileId!==''&&remotePath.trim()!==''&&!busy;

    async function execute(): Promise<void> {
        if(!canExport)return;
        setBusy(true);setMessage('');
        try{
            const archive=await exportProjectSelection(paths);
            const fileName=`${safeName(projectId||projectName)}-selection.zip`;
            await saveRemoteLayerArtifact(profileId,remotePath.trim(),{fileName,content:archive,mimeType:'application/zip'},{commitMessage:`Export ${paths.length} selected HYDRA project item(s)`});
            setMessage(t('Selection exported to the repository.'));
            onExported?.();
        }catch(error){setMessage(error instanceof Error?error.message:t('Could not export the selection to the repository.'));}
        finally{setBusy(false);}
    }

    const footer=<><HydraButton onClick={onClose}>{t('Close')}</HydraButton><HydraButton disabled={!canExport} variant="primary" onClick={()=>void execute()}>{busy?t('Working…'):t('Export selection')}</HydraButton></>;
    return <HydraModal open onClose={onClose} closeLabel={t('Close dialog')} title={t('Export selection to repository')} description={t('The selected project files and folders will be packaged as a ZIP and stored in the chosen repository profile.')} size="medium" footer={footer}>
        <div className="project-selection-remote-form">
            {profiles.length===0?<p className="project-selection-remote-empty">{t('No enabled and configured repository profiles are available. Configure one in Operations > Repositories.')}</p>:<>
                <label><strong>{t('Repository profile')}</strong><HydraSelect ariaLabel={t('Repository profile')} options={options} showFilter={profiles.length>6} value={profileId} onChange={setProfileId}/></label>
                <label><strong>{t('Remote path')}</strong><input value={remotePath} onChange={event=>setRemotePath(event.target.value)}/></label>
                <p>{paths.length} {t('selected items')}</p>
            </>}
            {message!==''&&<p className="project-selection-remote-message" role="status">{message}</p>}
        </div>
    </HydraModal>;
}
