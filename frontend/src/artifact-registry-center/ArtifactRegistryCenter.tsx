import { useEffect, useEffectEvent, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { loadArtifactGraph, loadArtifactRegistry, scanArtifacts } from './api';
import { indexBy } from '../utils/indexBy';
import type { HydraArtifactDependencyGraph, HydraArtifactRegistrySnapshot } from './types';
import './artifact-registry-center.css';

type RegistryView = 'OVERVIEW' | 'DETAILS';

/** Registro orientado a tareas: estado primero; trazabilidad técnica solo bajo demanda. */
export function ArtifactRegistryCenter() {
    const { t } = useHydraLanguage();
    const [snapshot, setSnapshot] = useState<HydraArtifactRegistrySnapshot | null>(null);
    const [graph, setGraph] = useState<HydraArtifactDependencyGraph | null>(null);
    const [kind, setKind] = useState('');
    const [view, setView] = useState<RegistryView>('OVERVIEW');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const kinds = useMemo(() => Array.from(new Set((snapshot?.artifacts ?? []).map(item => item.kind))).sort(), [snapshot]);
    const artifacts = useMemo(() => (snapshot?.artifacts ?? []).filter(item => kind === '' || item.kind === kind), [kind, snapshot]);
    const graphNodeByArtifactId = useMemo(() => indexBy(graph?.nodes ?? [], node => node.artifactId), [graph?.nodes]);
    const artifactCount = snapshot?.artifacts.length ?? 0;
    const missingDependencies = graph?.nodes.filter(node => node.status === 'MISSING_DEPENDENCY').length ?? 0;
    const cycleCount = graph?.cycles.length ?? 0;
    const hasProblems = missingDependencies > 0 || cycleCount > 0 || graph?.ready === false;
    const state: 'EMPTY' | 'READY' | 'REVIEW' = artifactCount === 0 ? 'EMPTY' : hasProblems ? 'REVIEW' : 'READY';

    async function loadRegistryState(): Promise<void> {
        try {
            const [nextSnapshot, nextGraph] = await Promise.all([loadArtifactRegistry(), loadArtifactGraph()]);
            setSnapshot(nextSnapshot); setGraph(nextGraph); setError('');
        } catch (exception) { setError(exception instanceof Error ? exception.message : t('Could not load artifact registry')); }
        finally { setLoading(false); }
    }

    async function refresh(scan = false): Promise<void> {
        setLoading(true);
        try {
            if (scan) await scanArtifacts();
            await loadRegistryState();
        } catch (exception) { setError(exception instanceof Error ? exception.message : t('Could not load artifact registry')); setLoading(false); }
    }

    const loadRegistryStateEffect = useEffectEvent(loadRegistryState);
    useEffect(() => {
        void Promise.resolve().then(loadRegistryStateEffect);
    }, []);

    const copy = state === 'EMPTY'
        ? { title:t('No artifacts have been analyzed yet'), description:t('Analyze the current project to discover the portable artifacts HYDRA can validate, synchronize and export.'), action:t('Analyze project artifacts') }
        : state === 'READY'
            ? { title:t('Artifacts are ready'), description:t('HYDRA found no missing dependencies or dependency cycles. You can continue with export or synchronization.'), action:t('Analyze again') }
            : { title:t('Some artifacts need attention'), description:t('Review the missing dependencies or cycles before relying on a deterministic export or synchronization.'), action:t('Analyze again') };

    return <section className="artifact-registry-center">
        <header className={`artifact-registry-overview state-${state.toLowerCase()}`}>
            <div className="artifact-registry-overview-copy"><p>{t('Artifact readiness')}</p><h2>{copy.title}</h2><span>{copy.description}</span></div>
            <div className="artifact-registry-primary-action"><HydraButton loading={loading} variant="primary" onClick={() => void refresh(true)}>{copy.action}</HydraButton>{artifactCount > 0 && <HydraButton variant="quiet" onClick={() => setView(value => value === 'DETAILS' ? 'OVERVIEW' : 'DETAILS')}>{view === 'DETAILS' ? t('Hide technical details') : t('Review details')}</HydraButton>}</div>
        </header>
        {error !== '' && <HydraActionableErrorNotice message={error} context="artifact registry" onDismiss={() => setError('')} />}

        <section className="artifact-registry-compact-status" aria-label={t('Artifact status summary')}>
            <article><strong>{artifactCount}</strong><span>{t('Artifacts')}</span></article>
            <article className={missingDependencies > 0 ? 'needs-review' : ''}><strong>{missingDependencies}</strong><span>{t('Missing dependencies')}</span></article>
            <article className={cycleCount > 0 ? 'needs-review' : ''}><strong>{cycleCount}</strong><span>{t('Dependency cycles')}</span></article>
            <article className={`overall-${state.toLowerCase()}`}><strong>{state === 'READY' ? t('Ready') : state === 'REVIEW' ? t('Review') : t('Pending')}</strong><span>{t('Overall state')}</span></article>
        </section>

        {view === 'OVERVIEW' && <section className="artifact-registry-next-step"><strong>{t('What should I do now?')}</strong><p>{state === 'EMPTY' ? t('Run the analysis. HYDRA will build the dependency graph automatically.') : state === 'READY' ? t('No corrective action is required. Open details only if you need traceability, checksums or producer information.') : t('Open details and inspect only the entries marked for review. After correcting them, analyze the project again.')}</p>{state === 'REVIEW' && <HydraButton onClick={() => setView('DETAILS')}>{t('Review problems')}</HydraButton>}</section>}

        {view === 'DETAILS' && <section className="artifact-registry-details" aria-label={t('Artifact technical details')}>
            <header><div><h3>{t('Technical details')}</h3><p>{t('Use this view only when you need traceability, dependency diagnostics or a specific artifact type.')}</p></div><div className="artifact-registry-filter"><HydraSelect ariaLabel={t('Filter artifact kind')} value={kind} placeholder={t('All artifact kinds')} options={kinds.map(value => ({ value,label:value,category:t('Artifact kinds') }))} onChange={setKind}/>{kind !== '' && <HydraButton size="small" variant="quiet" onClick={() => setKind('')}>{t('Clear filter')}</HydraButton>}</div></header>
            {artifacts.length === 0 ? <div className="artifact-registry-empty-state">{t('No artifacts match the current filter.')}</div> : <div className="artifact-registry-list">
                {artifacts.map(artifact => { const node=graphNodeByArtifactId.get(artifact.id); const status=node?.status ?? artifact.status; const diagnostics=node?.diagnostics ?? []; return <article className="artifact-registry-card" key={artifact.id}><header><div><strong>{artifact.name}</strong><small>{artifact.relativePath}</small></div><span className={`artifact-registry-badge status-${status.toLowerCase().replaceAll('_','-')}`}>{status}</span></header><dl><div><dt>{t('Kind')}</dt><dd>{artifact.kind}</dd></div><div><dt>{t('Producer')}</dt><dd>{artifact.producer}</dd></div><div><dt>{t('Dependencies')}</dt><dd>{node?.dependencies.length ?? 0} / {node?.dependants.length ?? 0}</dd></div><div><dt>SHA-256</dt><dd><code>{artifact.sha256}</code></dd></div></dl>{diagnostics.length>0&&<ul className="artifact-registry-diagnostics">{diagnostics.map(value=><li key={value}>{value}</li>)}</ul>}</article>; })}
            </div>}
            {cycleCount > 0 && <section className="artifact-cycles"><h3>{t('Dependency cycles')}</h3>{graph?.cycles.map(cycle=><code key={cycle.join('>')}>{cycle.join(' → ')} → {cycle[0]}</code>)}</section>}
        </section>}
    </section>;
}
