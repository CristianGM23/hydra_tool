import { useEffect, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraModal } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { deleteActiveProject, getProjectDeletionImpact } from './projectApi';
import type { ProjectDeletionImpact, ProjectDeletionResult } from './projectApi';
import './project-deletion.css';

interface ProjectDeletionDialogProps {
    open: boolean;
    onClose: () => void;
    onDeleted: (result: ProjectDeletionResult) => void | Promise<void>;
}

function formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GiB`;
}

/** Confirmación específica de raíz: nunca reutiliza el DELETE de archivos/carpetas. */
export function ProjectDeletionDialog({ open, onClose, onDeleted }: ProjectDeletionDialogProps) {
    const { t } = useHydraLanguage();
    const [impact, setImpact] = useState<ProjectDeletionImpact | null>(null);
    const [deleteDependencies, setDeleteDependencies] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (!open) return undefined;
        let active = true;
        void getProjectDeletionImpact().then(result => { if (active) setImpact(result); })
            .catch(exception => { if (active) setError(exception instanceof Error ? exception.message : t('Could not analyze project deletion.')); });
        return () => { active = false; };
    }, [open, t]);

    async function confirmDelete(): Promise<void> {
        if (impact === null || busy) return;
        if (impact.dependencies.length > 0 && !deleteDependencies) return;
        setBusy(true);
        setError('');
        try {
            const result = await deleteActiveProject(deleteDependencies);
            await onDeleted(result);
            onClose();
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : t('Could not delete the complete project.'));
        } finally {
            setBusy(false);
        }
    }

    const footer = <>
        <HydraButton disabled={busy} onClick={onClose}>{t('Cancel')}</HydraButton>
        <HydraButton disabled={impact === null || busy || (impact.dependencies.length > 0 && !deleteDependencies)} variant="danger" onClick={() => void confirmDelete()}>
            {busy ? t('Deleting…') : t('Delete complete project')}
        </HydraButton>
    </>;

    return <HydraModal open={open} onClose={onClose} title={t('Delete complete project')} description={t('This operation removes the complete local HYDRA project directory and cannot be undone.')} size="large" footer={footer} className="project-delete-dialog">
        {impact === null && error === '' && <div className="project-delete-loading">{t('Analyzing project dependencies…')}</div>}
        {impact !== null && <div className="project-delete-content">
            <section className="project-delete-summary">
                <div><strong>{impact.projectName}</strong><span>{impact.directory}</span></div>
                <dl><div><dt>{t('Files')}</dt><dd>{impact.fileCount}</dd></div><div><dt>{t('Directories')}</dt><dd>{impact.directoryCount}</dd></div><div><dt>{t('Size')}</dt><dd>{formatBytes(impact.totalBytes)}</dd></div></dl>
            </section>
            {impact.dependencies.length > 0 ? <section className="project-delete-dependencies">
                <header><div><h4>{t('Project dependencies detected')}</h4><p>{t('These artifacts live inside the project and will also be removed if you continue.')}</p></div><span>{impact.dependencies.length}</span></header>
                <div>{impact.dependencies.map(item => <article key={item.path}><div><strong>{item.path}</strong><span>{item.artifactKind}</span></div><small>{item.entries} {t('entries')} · {formatBytes(item.bytes)}</small></article>)}</div>
                <label className="project-delete-confirm-dependencies"><input type="checkbox" checked={deleteDependencies} onChange={event => setDeleteDependencies(event.target.checked)} /><span><strong>{t('Also delete all dependent artifacts')}</strong><small>{t('Required because this project contains files, generated artifacts or configuration below its root.')}</small></span></label>
            </section> : <section className="project-delete-no-dependencies"><strong>{t('No additional project dependencies were found.')}</strong><span>{t('Only the project descriptor and empty project structure will be removed.')}</span></section>}
        </div>}
        {error !== '' && <HydraActionableErrorNotice message={error} context="project explorer" onDismiss={() => setError('')} />}
    </HydraModal>;
}
