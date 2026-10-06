import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraWindowControls } from '../design-system';
import { configureActiveProject, getProjectCreationCatalog } from './projectApi';
import type { ProjectCreationCatalog, ProjectExperienceMode, ProjectSession } from './types';
import { useHydraLanguage } from '../i18n';
import { projectCapabilityLabel } from './projectPresentation';

interface ProjectCapabilitiesDialogProps {
    open: boolean;
    session: ProjectSession;
    onClose: () => void;
    onConfigured: (session: ProjectSession) => void;
}

export function ProjectCapabilitiesDialog({ open, session, onClose, onConfigured }: ProjectCapabilitiesDialogProps) {
    if (!open) return null;
    return <ProjectCapabilitiesDialogContent session={session} onClose={onClose} onConfigured={onConfigured} />;
}

function ProjectCapabilitiesDialogContent({ session, onClose, onConfigured }: Omit<ProjectCapabilitiesDialogProps,'open'>) {
    const { t } = useHydraLanguage();
    const [catalog, setCatalog] = useState<ProjectCreationCatalog | null>(null);
    const [capabilities, setCapabilities] = useState<Set<string>>(new Set(session.project?.enabledCapabilities ?? []));
    const [mode, setMode] = useState<ProjectExperienceMode>(session.project?.experienceMode ?? 'GUIDED');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        void getProjectCreationCatalog().then(value => { if (active) setCatalog(value); }).catch(exception => {
            if (active) setError(exception instanceof Error ? exception.message : t('Could not load project areas'));
        });
        return () => { active = false; };
    }, [t]);

    const dependents = useMemo(() => {
        const result = new Map<string,string[]>();
        for (const descriptor of catalog?.capabilities ?? []) for (const required of descriptor.requires) result.set(required,[...(result.get(required) ?? []),descriptor.id]);
        return result;
    }, [catalog]);

    function enableWithDependencies(target: Set<string>, id: string): void {
        if (target.has(id)) return;
        target.add(id);
        const descriptor = catalog?.capabilities.find(item => item.id === id);
        for (const dependency of descriptor?.requires ?? []) enableWithDependencies(target,dependency);
    }

    function disableWithDependents(target: Set<string>, id: string): void {
        if (!target.delete(id)) return;
        for (const dependent of dependents.get(id) ?? []) disableWithDependents(target,dependent);
    }

    function toggle(id: string): void {
        setCapabilities(current => {
            const next = new Set(current);
            if (next.has(id)) disableWithDependents(next,id); else enableWithDependencies(next,id);
            return next;
        });
    }

    async function save(): Promise<void> {
        setBusy(true); setError('');
        try {
            const updated = await configureActiveProject({ enabledCapabilities:Array.from(capabilities), experienceMode:mode, primaryArtifact:session.project?.primaryArtifact });
            onConfigured(updated); onClose();
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : t('Could not update project areas'));
        } finally { setBusy(false); }
    }

    return <div className="project-dialog-backdrop" role="presentation">
        <section aria-modal="true" className="project-dialog project-capabilities-dialog" role="dialog">
            <header><div><h2>{t('Project areas')}</h2><p>{t('Choose the parts of HYDRA that this project needs. Related areas are added automatically when required.')}</p></div><HydraWindowControls closeLabel={t('Close')} onClose={onClose}/></header>
            <div className="project-dialog-scroll project-dialog-form">
                {error !== '' && <HydraActionableErrorNotice context="project areas" message={error} onDismiss={() => setError('')} />}
                <section className="project-experience-row"><div><strong>{t('Working mode')}</strong><span>{t('Guided mode proposes the next step; Free mode exposes the full workbench. The stored project model is identical.')}</span></div><div className="project-experience-buttons"><HydraButton className={mode==='GUIDED'?'active':''} type="button" onClick={() => setMode('GUIDED')}>{t('Guided')}</HydraButton><HydraButton className={mode==='FREE'?'active':''} type="button" onClick={() => setMode('FREE')}>{t('Free')}</HydraButton></div></section>
                <div className="project-capability-grid">{(catalog?.capabilities ?? []).map(capability => <label key={capability.id} className="project-capability-option"><input checked={capabilities.has(capability.id)} type="checkbox" onChange={() => toggle(capability.id)}/><span><strong>{t(projectCapabilityLabel(capability.id))}</strong>{capability.requires.length > 0 && <small>{t('Needs')}: {capability.requires.map(item => t(projectCapabilityLabel(item))).join(', ')}</small>}</span></label>)}</div>
                <div className="project-capability-summary"><strong>{capabilities.size} {t('active areas')}</strong><span>{t('Existing files are never deleted when an area is disabled. You can enable it again later without losing your work.')}</span></div>
            </div>
            <footer><HydraButton disabled={busy} type="button" onClick={onClose}>{t('Cancel')}</HydraButton><HydraButton className="project-primary-action" disabled={busy || catalog===null} type="button" onClick={() => void save()}>{busy?t('Saving…'):t('Apply changes')}</HydraButton></footer>
        </section>
    </div>;
}
