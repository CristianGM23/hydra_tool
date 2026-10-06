import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraModal, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { loadRemoteStorageProfiles, saveRemoteProject } from './api';
import { RemoteStorageProfileFields } from './RemoteStorageProfileFields';
import type { RemoteArtifactDescriptor, RemoteStorageProfileDescriptor, RemoteWriteMode } from './types';

interface Props {
    open: boolean;
    projectId: string;
    projectName: string;
    onClose: () => void;
}

export function RemoteProjectStorageDialog(props: Props) {
    if (!props.open) return null;
    return <RemoteProjectStorageDialogContent key={props.projectId} {...props} />;
}

function RemoteProjectStorageDialogContent({ projectId, projectName, onClose }: Props) {
    const { t } = useHydraLanguage();
    const [profiles, setProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    const [profileId, setProfileId] = useState('');
    const [remotePath, setRemotePath] = useState(() => projectId === '' ? '' : `${projectId}.hydra-project.zip`);
    const [writeMode, setWriteMode] = useState<RemoteWriteMode>('UPSERT');
    const [expectedVersion, setExpectedVersion] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [saved, setSaved] = useState<RemoteArtifactDescriptor | null>(null);

    useEffect(() => {
        let active = true;
        void loadRemoteStorageProfiles().then(next => {
            if (!active) return;
            setProfiles(next);
            const first = next.find(profile => profile.enabled && profile.configured);
            if (first !== undefined) setProfileId(current => current === '' ? first.id : current);
        }).catch(exception => { if (active) setError(exception instanceof Error ? exception.message : t('Could not load remote storage profiles.')); });
        return () => { active = false; };
    }, [t]);



    const selectedProfile = useMemo(() => profiles.find(profile => profile.id === profileId), [profileId, profiles]);
    const canRun = profileId !== '' && selectedProfile?.configured === true && remotePath.trim() !== '';

    async function execute(): Promise<void> {
        if (!canRun) return;
        setBusy(true); setError(''); setSaved(null);
        try {
            const result = await saveRemoteProject(profileId, remotePath.trim(), { mode: writeMode, expectedVersion, commitMessage: `Save HYDRA project ${projectName || projectId}` });
            setSaved(result);
            setExpectedVersion(result.version);
        } catch (exception) { setError(exception instanceof Error ? exception.message : t('Remote project transfer failed.')); }
        finally { setBusy(false); }
    }

    const footer = <><HydraButton onClick={onClose}>{t('Close')}</HydraButton><HydraButton disabled={!canRun || busy} variant="primary" onClick={() => void execute()}>{busy ? t('Working…') : t('Save project to repository')}</HydraButton></>;
    return <HydraModal open onClose={onClose} title={t('Save project to repository')} description={t('Export the complete portable HYDRA project to any configured repository or cloud-storage profile. Project import is now available from the unified Open / import project flow.')} size="large" footer={footer} className="hydra-remote-project-dialog">
        <RemoteStorageProfileFields profiles={profiles} profileId={profileId} path={remotePath} onProfileChange={value => { setProfileId(value); setExpectedVersion(''); }} onPathChange={value => { setRemotePath(value); setExpectedVersion(''); }} disabled={busy} />
        <section className="hydra-remote-project-options">
            <label><strong>{t('Write policy')}</strong><HydraSelect ariaLabel={t('Write policy')} value={writeMode} options={[{ value:'UPSERT', label:t('Create or update') }, { value:'CREATE_ONLY', label:t('Create only') }, { value:'UPDATE_ONLY', label:t('Update only') }]} onChange={value => setWriteMode(value as RemoteWriteMode)} /></label>
            {expectedVersion !== '' && <p><strong>{t('Expected remote version')}</strong><code>{expectedVersion}</code></p>}
            <p>{t('HYDRA packages the active project using the existing portable ZIP exporter before sending it to the server-side repository connector. Credentials never leave the backend.')}</p>
        </section>
        {saved !== null && <div className="hydra-remote-project-result"><strong>{saved.created ? t('Remote project created') : t('Remote project updated')}</strong><span>{saved.provider} · {saved.path}</span><small>{saved.size.toLocaleString()} {t('bytes')} · {t('version')} {saved.version || t('not reported')}</small></div>}
        {error !== '' && <HydraActionableErrorNotice message={error} context="remote storage" onDismiss={() => setError('')} />}
    </HydraModal>;
}
