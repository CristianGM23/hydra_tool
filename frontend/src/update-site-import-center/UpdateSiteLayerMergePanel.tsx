import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { listMetamodels } from '../api/editorApi';
import { listEditorDefinitions } from '../editor-definition/api';
import { useHydraLanguage } from '../i18n';
import type { EditorDefinitionSummary } from '../editor-definition/types';
import type { MetamodelSummary } from '../types/editor';
import { mergeRemoteSnapshotLayers, mergeUpdateSiteLayers, rollbackUpdateSiteMerge } from './api';
import type { UpdateSiteImportAnalysis, UpdateSiteLayerMergeResult, UpdateSiteLayerSelection,
    UpdateSiteMergeDecision } from './types';

interface Props { files: readonly File[]; snapshotId?: string; analysis: UpdateSiteImportAnalysis; selection: UpdateSiteLayerSelection; }
const DECISIONS: UpdateSiteMergeDecision[] = ['KEEP_EXISTING', 'USE_RECOVERED', 'SKIP'];

export function UpdateSiteLayerMergePanel({ files, snapshotId, analysis, selection }: Props) {
    const { t } = useHydraLanguage();
    const [targetMetamodelId, setTargetMetamodelId] = useState('');
    const [targetEditorId, setTargetEditorId] = useState('');
    const [expectedEtag, setExpectedEtag] = useState('');
    const [metamodels, setMetamodels] = useState<MetamodelSummary[]>([]);
    const [editors, setEditors] = useState<EditorDefinitionSummary[]>([]);
    const [loadingTargets, setLoadingTargets] = useState(false);
    const [strict, setStrict] = useState(false);
    // Solo decisiones explícitas del usuario: una colisión nunca se resuelve por defecto.
    const [decisions, setDecisions] = useState<Record<string, UpdateSiteMergeDecision>>({});
    const [previewedDecisions, setPreviewedDecisions] = useState('');
    const [result, setResult] = useState<UpdateSiteLayerMergeResult | null>(null);
    const [busy, setBusy] = useState<'PREVIEW' | 'APPLY' | 'ROLLBACK' | ''>('');
    const [error, setError] = useState('');
    const canRun = (files.length > 0 || snapshotId !== undefined) && targetMetamodelId !== '' && targetEditorId !== '';
    const decisionsKey = useMemo(() => JSON.stringify(Object.entries(decisions).sort(([a], [b]) => a.localeCompare(b))), [decisions]);
    const stalePreview = result !== null && previewedDecisions !== decisionsKey;
    const pendingConflicts = useMemo(() => result?.preview.items.filter(item => item.conflict && decisions[item.key] === undefined).length ?? 0, [result, decisions]);
    const canApply = canRun && expectedEtag.trim() !== '' && result !== null && !result.persisted && result.preview.applicable && !stalePreview && pendingConflicts === 0;
    const summary = useMemo(() => result?.preview.items.reduce((values, item) => {
        values[item.selectedDecision]++; return values;
    }, { KEEP_EXISTING: 0, USE_RECOVERED: 0, SKIP: 0 } as Record<UpdateSiteMergeDecision, number>), [result]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setLoadingTargets(true);
            void listMetamodels().then(items => { if (active) setMetamodels(items); })
                .catch(exception => { if (active) setError(exception instanceof Error ? exception.message : t('Could not load destination metamodels.')); })
                .finally(() => { if (active) setLoadingTargets(false); });
        });
        return () => { active = false; };
    }, [t]);

    useEffect(() => {
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setEditors([]); setTargetEditorId(''); setExpectedEtag(''); setResult(null);
            if (targetMetamodelId === '') return;
            setLoadingTargets(true);
            void listEditorDefinitions(targetMetamodelId).then(items => { if (active) setEditors(items); })
                .catch(exception => { if (active) setError(exception instanceof Error ? exception.message : t('Could not load editors for the selected metamodel.')); })
                .finally(() => { if (active) setLoadingTargets(false); });
        });
        return () => { active = false; };
    }, [t, targetMetamodelId]);

    function chooseEditor(editorId: string): void {
        setTargetEditorId(editorId);setResult(null);setError('');
        setExpectedEtag(editors.find(item => item.id === editorId)?.etag ?? '');
    }

    async function execute(persist: boolean): Promise<void> {
        if (!canRun) { setError(t('Select a local source or verified snapshot, then choose a destination metamodel and editor.')); return; }
        if (persist && expectedEtag.trim() === '') { setError(t('HYDRA could not obtain the current editor ETag. Refresh the destination before applying the merge.')); return; }
        setBusy(persist ? 'APPLY' : 'PREVIEW');
        try {
            // Al aplicar solo se envían las decisiones de elementos de la última vista previa: una decisión obsoleta no debe bloquear la aplicación.
            const known = new Set((result?.preview.items ?? []).map(item => item.key));
            const sent = persist && result !== null ? Object.fromEntries(Object.entries(decisions).filter(([key]) => known.has(key))) : decisions;
            const request = { targetMetamodelId, targetEditorId,
                expectedEtag: expectedEtag.trim(), candidateId: analysis.selectedCandidateId, strict, persist,
                selection, decisions: sent };
            const next = snapshotId === undefined ? await mergeUpdateSiteLayers(files, request)
                : await mergeRemoteSnapshotLayers(snapshotId, request);
            setResult(next); setError(''); setPreviewedDecisions(decisionsKey);
            if (persist && next.editorEtag !== '') setExpectedEtag(next.editorEtag);
        } catch (exception) { setError(exception instanceof Error ? exception.message : t('Could not merge the recovered layers')); }
        finally { setBusy(''); }
    }

    async function rollback(): Promise<void> {
        if (result === null || !result.rollbackAvailable) return;
        setBusy('ROLLBACK');
        try {
            const rolledBack = await rollbackUpdateSiteMerge({ transactionId: result.preview.transactionId,
                expectedCurrentEtag: result.editorEtag });
            if (!rolledBack.restored) setError(rolledBack.diagnostics.join('; '));
            else { setExpectedEtag(rolledBack.editorEtag); setResult(null); setError(''); }
        } catch (exception) { setError(exception instanceof Error ? exception.message : t('Could not roll back the merge')); }
        finally { setBusy(''); }
    }

    return <section className="update-site-v36-panel">
        <header className="update-site-section-header"><div><h3>{t('Selective composition into an existing editor')}</h3><p>{t('Merge recovered palettes, tools, representations and forms without replacing the editor document as a whole.')}</p></div>
            <HydraTooltip label={t('About selective editor merge')} title={t('ETag-protected merge')}><p>{t('Choose a metamodel and one of its saved editors. HYDRA obtains the current ETag automatically, validates the preview against that exact revision and rejects persistence if another change occurs before the merge.')}</p><p>{t('The ETag is an optimistic-concurrency token, not an editor identifier. Normal users should not type it manually.')}</p></HydraTooltip></header>
        <div className="update-site-v36-form">
            <HydraFormField label={t('Destination metamodel')} targetId="merge-target-metamodel" required help={t('Choose a metamodel already saved in HYDRA. Editors are loaded only from this metamodel so an unrelated ID cannot be entered accidentally.')}>
                <HydraSelect id="merge-target-metamodel" ariaLabel={t('Destination metamodel')} disabled={loadingTargets} value={targetMetamodelId} placeholder={t('Select a metamodel')} options={metamodels.map(item => ({ value:item.id,label:item.fileName || item.id,description:`${item.id} · ${item.rootPackageCount} ${t('root package(s)')}`,category:t('Metamodels') }))} onChange={value => { setTargetMetamodelId(value); setError(''); }} />
            </HydraFormField>
            <HydraFormField label={t('Destination editor')} targetId="merge-target-editor" required help={t('Choose a saved editor belonging to the selected metamodel. HYDRA uses its real stable ID and current revision instead of asking you to type either value.')}>
                <HydraSelect id="merge-target-editor" ariaLabel={t('Destination editor')} disabled={loadingTargets || targetMetamodelId === ''} value={targetEditorId} placeholder={targetMetamodelId === '' ? t('Select a metamodel first') : t('Select an editor')} options={editors.map(item => ({ value:item.id,label:item.name || item.id,description:`${item.id} · ${item.representationCount} ${t('representations')} · ${item.toolCount} ${t('tools')}`,category:t('Editors') }))} onChange={chooseEditor} />
            </HydraFormField>
            <HydraFormField label={t('Current editor ETag')} targetId="merge-target-etag" help={t('Loaded automatically from the selected editor. HYDRA sends it as If-Match so a concurrent change blocks the merge instead of overwriting someone else’s work.')}>
                <input id="merge-target-etag" readOnly value={expectedEtag} placeholder={targetEditorId === '' ? t('Select an editor first') : t('Loading current ETag…')} />
            </HydraFormField>
            <HydraCheckbox label={t('Require exact recovery')} checked={strict} description={t('Accept only elements backed by original exact evidence. Recovered, derived and inferred candidates are blocked for this merge.')} onChange={event => setStrict(event.target.checked)} />
        </div>
        <details className="update-site-merge-advanced"><summary>{t('Advanced concurrency override')}</summary><p>{t('Use this only when diagnosing a concurrency problem. An incorrect ETag will make the server reject the write; it never disables concurrency protection.')}</p><HydraFormField label={t('Override expected ETag')} targetId="merge-target-etag-advanced" help={t('Normally leave this unchanged. The value above was read from the selected editor.')}> <input id="merge-target-etag-advanced" value={expectedEtag} onChange={event => setExpectedEtag(event.target.value)} /></HydraFormField></details>
        <div className="hydra-center-actions"><HydraButton loading={busy === 'PREVIEW'} disabled={!canRun} onClick={() => void execute(false)}>{t('Preview selective merge')}</HydraButton>
            <HydraButton variant="primary" loading={busy === 'APPLY'} disabled={!canApply} onClick={() => void execute(true)}>{t('Apply ETag-protected merge')}</HydraButton>
            {result?.rollbackAvailable && <HydraButton variant="danger" loading={busy === 'ROLLBACK'} onClick={() => void rollback()}>{t('Rollback merge')}</HydraButton>}</div>
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}
        {result !== null && <div className={`update-site-v36-result ${result.preview.applicable ? 'applicable' : 'blocked'}`}>
            <div className="update-site-v36-kpis"><article><strong>{summary?.KEEP_EXISTING ?? 0}</strong><span>{t('Existing kept')}</span></article><article><strong>{summary?.USE_RECOVERED ?? 0}</strong><span>{t('Recovered used')}</span></article><article><strong>{summary?.SKIP ?? 0}</strong><span>{t('Skipped')}</span></article><article><strong>{result.preview.items.filter(item => item.conflict).length}</strong><span>{t('Collisions')}</span></article></div>
            {result.preview.items.some(item => item.conflict) && <small role="status">{stalePreview ? t('Decisions changed: preview the merge again before applying it.')
                : pendingConflicts > 0 ? `${pendingConflicts} ${t('collision(s) still need an explicit decision before the merge can be applied.')}`
                    : t('Every collision has an explicit decision.')}</small>}
            <div className="update-site-merge-items">{result.preview.items.map(item => <article key={item.key} className={item.conflict ? 'conflict' : ''}>
                <div><strong>{item.displayName}</strong><code>{item.key}</code><small>{item.explanation}</small>
                    {item.conflict && <small>{t(decisionDescription(decisions[item.key]))}</small>}</div>
                <HydraSelect ariaLabel={`${t('Merge decision for')} ${item.displayName}`} value={decisions[item.key] ?? (item.conflict ? '' : item.selectedDecision)}
                    placeholder={t('Select a decision…')} options={DECISIONS.map(value => ({ value,label:t(human(value)),description:t(decisionDescription(value)) }))}
                    onChange={value => setDecisions(current => ({ ...current, [item.key]: value as UpdateSiteMergeDecision }))} />
            </article>)}</div>
            {result.preview.diagnostics.map(diagnostic => <small key={diagnostic}>{diagnostic}</small>)}
        </div>}
    </section>;
}

function human(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase()); }
function decisionDescription(value: UpdateSiteMergeDecision | undefined): string {
    if (value === undefined) return 'No decision yet: this blocks applying the merge.';
    if (value === 'KEEP_EXISTING') return 'Keep existing: preserve the current editor element when a collision occurs. Choose this when local customizations must win.';
    if (value === 'USE_RECOVERED') return 'Use recovered: replace the colliding target element with the recovered definition. Choose this only after reviewing its confidence and provenance.';
    return 'Skip: do not merge this colliding element. Use this when the conflict needs manual review before any change is made.';
}
