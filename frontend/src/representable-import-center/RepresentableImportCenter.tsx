import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCreationModeSwitch, HydraFormField, HydraGuidedWorkflow, HydraProcessOverview, HydraSelect } from '../design-system';
import { loadMetaModel } from '../metamodel-designer/api';
import { useHydraLanguage } from '../i18n';
import type { MetaModel } from '../metamodel-designer/types';
import { applyRepresentableImport, loadRepresentableTechnologies, previewRepresentableImport } from './api';
import { RepresentableChangeReview } from './RepresentableChangeReview';
import { RepresentablePreviewPanel } from './RepresentablePreviewPanel';
import { decorateChanges, isClassLikeCandidate, parseExternalChanges, previewReviewState, recommendedResolution, resolutionComplete, type CandidateResolution, type CandidateResolutionMap } from './representableImportGuidance';
import type { RepresentableImportPreview } from './types';
import './representable-import-center.css';
import './representable-import-guided.css';

interface Props { metamodelId: string; }

const sample = JSON.stringify([
    { externalId: 'change-1', changeKind: 'ADD', nativeKind: 'EClass', qualifiedName: 'example.Customer', properties: { abstract: false } }
], null, 2);

export function RepresentableImportCenter({ metamodelId }: Props) {
    const { t } = useHydraLanguage();
    const [technologies, setTechnologies] = useState<string[]>([]);
    const [technology, setTechnology] = useState('');
    const [strategy, setStrategy] = useState('SAFE');
    const [source, setSource] = useState(sample);
    const [metamodel, setMetamodel] = useState<MetaModel | null>(null);
    const [resolutions, setResolutions] = useState<CandidateResolutionMap>({});
    const [preview, setPreview] = useState<RepresentableImportPreview | null>(null);
    const [partialReviewed, setPartialReviewed] = useState(false);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('context');
    const analysis = useMemo(() => parseExternalChanges(source), [source]);

    useEffect(() => {
        let active = true;
        void loadRepresentableTechnologies().then(values => { if (!active) return; setTechnologies(values); setTechnology(values[0] ?? ''); })
            .catch(exception => { if (active) setError(exception instanceof Error ? exception.message : 'Could not load import mappers'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        let active = true;
        if (metamodelId === '') {
            queueMicrotask(() => { if (active) setMetamodel(null); });
            return () => { active = false; };
        }
        void loadMetaModel(metamodelId).then(result => { if (active) setMetamodel(result.metamodel); })
            .catch(exception => { if (active) { setMetamodel(null); setError(exception instanceof Error ? exception.message : 'Could not load the selected metamodel'); } });
        return () => { active = false; };
    }, [metamodelId]);

    useEffect(() => {
        if (!analysis.valid) return undefined;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setResolutions(current => {
                const next: CandidateResolutionMap = {};
                for (const candidate of analysis.changes) {
                    if (!isClassLikeCandidate(candidate)) continue;
                    const resolution = current[candidate.externalId] ?? recommendedResolution(candidate, metamodel);
                    if (resolution !== undefined) next[candidate.externalId] = resolution;
                }
                return next;
            });
        });
        return () => { active = false; };
    }, [analysis, metamodel]);

    const reviewedChanges = useMemo(() => analysis.valid ? decorateChanges(analysis.changes, resolutions) : [], [analysis, resolutions]);
    const targetsReady = analysis.valid && resolutionComplete(analysis.changes, resolutions);
    const reviewState = previewReviewState(preview);
    const canPersist = preview?.applicable === true && (reviewState.reviewCount === 0 || partialReviewed);

    function resetPreview(): void { setPreview(null); setPartialReviewed(false); }
    function updateResolution(externalId: string, resolution: CandidateResolution): void { setResolutions(current => ({ ...current, [externalId]: resolution })); resetPreview(); }

    async function run(apply: boolean): Promise<boolean> {
        if (!analysis.valid) { setError(analysis.error); return false; }
        if (!targetsReady) { setError('Review the destination of every class change before generating the preview.'); return false; }
        setLoading(true);
        try {
            const next = apply ? await applyRepresentableImport(technology, metamodelId, strategy, reviewedChanges) : await previewRepresentableImport(technology, metamodelId, strategy, reviewedChanges);
            setPreview(next); if (!apply) setPartialReviewed(false); setError(''); return true;
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Representable import failed'); return false; }
        finally { setLoading(false); }
    }

    const contextFields = <HydraFormField label="Technology" targetId="representable-technology" required help="Technology that produced the external changes. HYDRA uses the matching adapter to interpret them without executing external code." example="ECORE"><HydraSelect ariaLabel="Representable import technology" value={technology} options={technologies.map(value => ({ value, label:value, category:'Installed mappers' }))} onChange={value => { setTechnology(value); resetPreview(); }} /></HydraFormField>;
    const strategyFields = <HydraFormField label="Strategy" targetId="representable-strategy" required help="Safe review allows partial mappings only after explicit human review. Strict accepts only fully representable changes." options={[{ value:'SAFE',label:'Safe review',description:'Allows amber partial mappings after they have been reviewed; unsupported red changes still block persistence.' },{ value:'STRICT',label:'Strict',description:'Requires every change to be fully representable with no amber or red compatibility result.' }]}><HydraSelect ariaLabel="Representable import strategy" value={strategy} options={[{ value:'SAFE',label:'Safe review',description:'Allow partial mappings after explicit review, but never unsupported changes.' },{ value:'STRICT',label:'Strict',description:'Every external change must be fully representable.' }]} onChange={value => { setStrategy(value); resetPreview(); }} /></HydraFormField>;
    const sourceFields = <HydraFormField label="External change candidates" targetId="representable-source" required help="Normalized technical input produced by an analyzer or integration. In Guided mode, prefer the high-level review cards instead of editing this JSON manually." error={analysis.valid ? undefined : analysis.error}><textarea id="representable-source" rows={18} value={source} onChange={event => { setSource(event.target.value); resetPreview(); }} /></HydraFormField>;
    const changeReview = <RepresentableChangeReview changes={analysis.changes} metamodel={metamodel} resolutions={resolutions} onResolutionChange={updateResolution} source={source} sourceError={analysis.error} onSourceChange={value => { setSource(value); resetPreview(); }} />;
    const previewPanel = preview === null ? <div className="hydra-empty-state"><h3>No preview yet</h3><p>HYDRA will explain what each external change means, what it proposes to do and whether anything requires review.</p></div> : <RepresentablePreviewPanel preview={preview} changes={reviewedChanges} partialReviewed={partialReviewed} onPartialReviewedChange={setPartialReviewed} />;
    const previewContent = <div className="representable-guided-preview"><HydraButton loading={loading} disabled={technology === '' || metamodelId === '' || !analysis.valid || !targetsReady} variant="primary" onClick={() => void run(false)}>Analyze proposed import</HydraButton>{preview !== null && <><span className={`representable-summary-status ${reviewState.tone}`}>{reviewState.label}</span><p>{preview.operations.length} operation(s) · {reviewState.reviewCount} {t('to review')} · {reviewState.issueCount} {t('blocking issue(s)')}</p>{previewPanel}</>}</div>;

    const guidedSteps = [
        { id:'context', title:'Choose the semantic context', description:'Select the technology adapter and confirm the metamodel that HYDRA will compare against.', checks:[{ id:'metamodel', label:'Metamodel context is available', complete:metamodelId !== '' && metamodel !== null, detail:metamodel === null ? 'Select or load the metamodel before importing.' : `${metamodel.name} · ${metamodel.classes.length} class(es)` },{ id:'technology', label:'Import technology is selected', complete:technology !== '', detail:technology === '' ? 'Choose one installed mapper.' : technology }], content:contextFields },
        { id:'strategy', title:'Choose how strict the review should be', description:'Safe review allows partial mappings only after you inspect them. Strict accepts only fully representable changes.', checks:[{ id:'strategy', label:'Review strategy is explicit', complete:strategy === 'SAFE' || strategy === 'STRICT', detail:strategy === 'SAFE' ? 'Partial mappings may continue only after explicit review.' : 'Every change must be fully compatible.' }], content:strategyFields },
        { id:'changes', title:'Understand each external change', description:'Review what the external source is asking for and choose the destination in the current metamodel. You should not need to edit technical IDs manually.', checks:[{ id:'json', label:'External change list can be read', complete:analysis.valid, detail:analysis.valid ? `${analysis.changes.length} candidate(s) detected.` : analysis.error },{ id:'targets', label:'Every class change has a destination', complete:targetsReady, detail:targetsReady ? 'Class additions and updates are resolved against the selected metamodel.' : 'Choose create-new or an existing compatible class for every class change.' }], content:changeReview },
        { id:'preview', title:'Review HYDRA’s proposal', description:'HYDRA explains what it will do and separates successful checks from amber reviews and red blockers.', checks:[{ id:'preview', label:'A read-only proposal has been generated', complete:preview !== null, detail:preview === null ? 'Analyze the proposed import first.' : preview.previewId },{ id:'applicable', label:'No blocking compatibility issue remains', complete:preview?.applicable === true, detail:preview === null ? 'No preview yet.' : reviewState.tone === 'blocked' ? 'Resolve every red compatibility issue.' : reviewState.tone === 'review' ? 'Amber mappings are allowed by Safe review but must be acknowledged.' : 'Every change is fully representable.' },{ id:'partial-reviewed', label:'Partial mappings have been reviewed', complete:reviewState.reviewCount === 0 || partialReviewed, detail:reviewState.reviewCount === 0 ? 'No partial mapping requires human review.' : partialReviewed ? 'Amber mappings explicitly reviewed.' : 'Read each amber entry and confirm the review before continuing.' }], content:previewContent },
        { id:'persist', title:'Persist the reviewed neutral package', description:'Persist only the neutral operations you have understood and reviewed. This step does not silently apply blocked external semantics.', checks:[{ id:'ready', label:'Reviewed package is ready', complete:canPersist, detail:canPersist ? `${preview?.operations.length ?? 0} neutral operation(s) ready.` : 'Complete the preview and review every amber or red result first.' }], content:<div className="hydra-guided-review-summary"><strong>{metamodel?.name ?? 'No metamodel'}</strong><span>{technology || 'No technology'} · {strategy}</span><p>{preview?.operations.length ?? 0} neutral operation(s) ready.</p></div> }
    ];

    return <section className="representable-import-center">
        <div className="hydra-workbench-mode-bar"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full workspace" /></div>
        {metamodelId === '' && <HydraActionableErrorNotice className="error-message" message="Select a metamodel context before reconstructing external changes." context="representable import" />}
        {error !== '' && <HydraActionableErrorNotice message={error} context="representable import" onDismiss={() => setError('')} />}
        {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Understand external changes before importing" description="HYDRA compares external changes with the selected metamodel, proposes a destination for each one and tells you exactly what is safe, what needs review and what cannot be imported automatically." outcome="A reviewed neutral change package with explicit destinations and actionable compatibility guidance." recommendation="Start with Safe review. Reuse an existing class when the external concept already exists; create a new class only when it represents a genuinely new domain concept." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Persist reviewed package" onFinish={() => run(true)} /> :
        <><HydraProcessOverview title="Understand external changes before importing" summary="Compare external changes with the selected metamodel, resolve destinations and inspect compatibility before persisting anything." outcome="A reviewed neutral change package with explicit destinations and compatibility evidence." recommendation="Use the high-level change cards first; edit normalized JSON only when diagnosing or integrating an external analyzer." /><div className="representable-import-layout"><aside>{contextFields}{strategyFields}{sourceFields}</aside><div className="representable-import-preview">
            <div className="hydra-center-actions"><HydraButton loading={loading} disabled={technology === '' || metamodelId === '' || !analysis.valid || !targetsReady} onClick={() => void run(false)}>Analyze proposed import</HydraButton><HydraButton loading={loading} variant="primary" disabled={!canPersist} onClick={() => void run(true)}>Persist reviewed package</HydraButton></div>
            {changeReview}{previewPanel}
        </div></div></>}
    </section>;
}
