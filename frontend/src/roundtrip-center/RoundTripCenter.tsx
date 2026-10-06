import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCreationModeSwitch, HydraFormField, HydraGuidedWorkflow, HydraSelect } from '../design-system';
import { listIntegrationProfiles } from '../integration-designer/api';
import type { IntegrationProfileSummary } from '../integration-designer/types';
import { applyAdvancedRoundTrip, prepareAdvancedRoundTrip, rollbackAdvancedRoundTrip } from './api';
import type { ConflictResolutionChoice, ElementConflictDecision, RoundTripApplyResult, RoundTripResolutionPlanView } from './types';
import './roundtrip-center.css';

interface Props { metamodelId: string; }

const choiceHelp: Record<ConflictResolutionChoice, string> = {
    KEEP_HYDRA: 'Use the version generated from the current HYDRA model.',
    KEEP_NATIVE: 'Keep the version that is already present in the generated project.',
    KEEP_MANUAL: 'Keep the manually edited region and do not overwrite this artifact.',
    MERGE_SAFE: 'Regenerate the artifact and preserve the protected manual regions.',
    SKIP: 'Leave this conflict out of the current synchronization.'
};
const choiceLabels: Record<ConflictResolutionChoice, string> = { KEEP_HYDRA:'Use HYDRA version', KEEP_NATIVE:'Keep generated-project version', KEEP_MANUAL:'Keep manual changes', MERGE_SAFE:'Merge safely', SKIP:'Skip for now' };

export function RoundTripCenter(props: Props) {
    return <RoundTripCenterSession key={props.metamodelId || 'none'} {...props} />;
}

function RoundTripCenterSession({ metamodelId }: Props) {
    const [profiles, setProfiles] = useState<IntegrationProfileSummary[]>([]);
    const [profilesMetamodelId, setProfilesMetamodelId] = useState('');
    const [profileId, setProfileId] = useState('');
    const [targetDirectory, setTargetDirectory] = useState('generated-host');
    const [plan, setPlan] = useState<RoundTripResolutionPlanView | null>(null);
    const [decisions, setDecisions] = useState<Record<string, ElementConflictDecision>>({});
    const [result, setResult] = useState<RoundTripApplyResult | null>(null);
    const [rollbackOperationId, setRollbackOperationId] = useState('');
    const [loading, setLoading] = useState(metamodelId !== '');
    const [error, setError] = useState('');
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('target');
    const decisionList = useMemo(() => Object.values(decisions), [decisions]);
    const everyConflictDecided = plan !== null && plan.unresolved.every(conflict => decisions[`${conflict.elementId}@${conflict.artifactPath}`] !== undefined);

    useEffect(() => {
        let active = true;
        if (metamodelId === '') return () => { active = false; };
        void listIntegrationProfiles(metamodelId).then(items => {
            if (!active) return;
            setProfiles(items); setProfilesMetamodelId(metamodelId); setProfileId(current => items.some(item => item.id === current) ? current : items[0]?.id ?? ''); setError('');
        }).catch(exception => { if (active) setError(exception instanceof Error ? exception.message : 'Could not load integration profiles'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [metamodelId]);

    async function preview(nextDecisions = decisionList, preserveResult = false): Promise<void> {
        if (metamodelId === '' || profileId === '') return;
        setLoading(true);
        try {
            const next = await prepareAdvancedRoundTrip(metamodelId, profileId, targetDirectory, nextDecisions);
            setPlan(next); setError('');
            if (!preserveResult) setResult(null);
            setDecisions(current => Object.fromEntries(Object.entries(current).filter(([key]) => next.unresolved.some(conflict => `${conflict.elementId}@${conflict.artifactPath}` === key))));
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not prepare round-trip synchronization'); }
        finally { setLoading(false); }
    }

    function updateDecision(key: string, choice: ConflictResolutionChoice): void {
        setDecisions(current => ({ ...current, [key]: { conflictKey:key, choice, rationale:current[key]?.rationale || 'Selected in HYDRA round-trip center' } }));
    }

    async function apply(): Promise<boolean> {
        setLoading(true);
        try {
            const applied = await applyAdvancedRoundTrip(metamodelId, profileId, targetDirectory, decisionList);
            setResult(applied); setRollbackOperationId(applied.operationId); setError(''); await preview(decisionList, true); return true;
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not apply round-trip synchronization'); return false; }
        finally { setLoading(false); }
    }

    async function rollback(): Promise<void> {
        if (rollbackOperationId.trim() === '') return;
        setLoading(true);
        try { setResult(await rollbackAdvancedRoundTrip(metamodelId, profileId, targetDirectory, rollbackOperationId.trim())); setError(''); await preview(decisionList, true); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not roll back round-trip synchronization'); }
        finally { setLoading(false); }
    }

    if (metamodelId === '') return <section className="hydra-empty-state"><h2>Select a metamodel</h2><p>Synchronizing a generated project starts from a saved integration profile.</p></section>;
    if (error !== '' && profilesMetamodelId !== metamodelId) return <section className="hydra-empty-state error"><h2>Round-trip profiles unavailable</h2><HydraActionableErrorNotice message={error} context="round-trip" /></section>;
    if (profilesMetamodelId !== metamodelId) return <section className="hydra-empty-state"><h2>Loading integration profiles…</h2></section>;

    const targetFields = <div className="roundtrip-configuration">
        <HydraFormField label="Integration profile" targetId="roundtrip-profile" help="The adapter profile determines the target technology and generated artifacts." example="people-langium"><HydraSelect ariaLabel="Round-trip integration profile" id="roundtrip-profile" showFilter={profiles.length > 6} value={profileId} placeholder="Select profile" options={profiles.map(profile => ({ value:profile.id,label:profile.name,description:`${profile.technology} · ${profile.deliveryMode}`,category:'Saved profiles' }))} onChange={value => { setProfileId(value); setPlan(null); setDecisions({}); }} /></HydraFormField>
        <HydraFormField label="Generated project folder (relative)" targetId="roundtrip-target" help="Folder inside HYDRA's configured generation area. This is a safe relative folder, not a physical operating-system path." example="clients/people-langium"><input id="roundtrip-target" value={targetDirectory} onChange={event => { setTargetDirectory(event.target.value); setPlan(null); setDecisions({}); }} /></HydraFormField>
    </div>;
    const conflictsContent = <div className="roundtrip-guided-conflicts">
        {plan === null ? <p>Generate a preview first.</p> : plan.unresolved.length === 0 ? <p>No unresolved element conflict remains.</p> : <>{plan.unresolved.map(conflict => { const key=`${conflict.elementId}@${conflict.artifactPath}`; const decision=decisions[key]; return <article key={key}><div><strong>{conflict.elementId}</strong><code>{conflict.artifactPath}</code>{conflict.containsManualRegion && <span>Manual region detected</span>}</div><HydraSelect ariaLabel={`Decision for ${conflict.artifactPath}`} value={decision?.choice ?? ''} placeholder="Select decision" options={conflict.supportedChoices.map(choice => ({ value:choice,label:choiceLabels[choice],description:choiceHelp[choice],category:'Conflict decisions' }))} onChange={value => updateDecision(key, value as ConflictResolutionChoice)} /></article>; })}<HydraButton loading={loading} disabled={!everyConflictDecided} onClick={() => void preview(decisionList)}>Recalculate resolved plan</HydraButton></>}
    </div>;
    const guidedSteps = [
        { id:'target', title:'Choose the generated project', description:'Tell HYDRA which generated project should be compared with the current model.', checks:[{ id:'profile',label:'Integration profile is selected',complete:profileId !== '',detail:profileId === '' ? 'Create or select an integration profile first.' : profileId},{ id:'directory',label:'Generated project folder is defined',complete:targetDirectory.trim() !== '',detail:'HYDRA uses a safe project-relative folder below its configured generation area.'}], content:targetFields },
        { id:'preview', title:'See what changed', description:'Compare HYDRA with the generated project without modifying any file.', checks:[{ id:'plan',label:'Synchronization preview is available',complete:plan !== null,detail:plan === null ? 'Run preview before making conflict decisions.' : `${plan.actions.length} action(s) planned.`}], content:<div className="roundtrip-guided-preview"><HydraButton loading={loading} variant="primary" disabled={profileId === '' || targetDirectory.trim() === ''} onClick={() => void preview()}>Compare changes</HydraButton>{plan !== null && <span className={plan.canApply ? 'status-passed' : 'status-partial'}>{plan.canApply ? 'READY' : 'DECISIONS REQUIRED'}</span>}</div> },
        { id:'conflicts', title:'Choose what to keep', description:'When HYDRA and the generated project changed the same element, choose the version to preserve.', checks:[{ id:'decisions',label:'Every unresolved conflict has a decision',complete:plan !== null && (plan.unresolved.length === 0 || everyConflictDecided),detail:plan === null ? 'Preview the synchronization first.' : `${plan.unresolved.length} conflict(s) require review.`},{ id:'resolved-plan',label:'Resolved plan is ready to apply',complete:plan?.canApply === true,detail:plan?.canApply ? 'The server plan accepts all decisions.' : 'Recalculate after selecting conflict decisions.'}], content:conflictsContent },
        { id:'review', title:'Review the result', description:'Check the files that HYDRA will update before applying the synchronization.', checks:[{ id:'ready',label:'Plan is explicitly ready',complete:plan?.canApply === true,detail:'No write is performed while the plan is partial.'}], content:<div className="hydra-guided-review-summary"><strong>{plan?.operationId ?? 'No plan'}</strong><span>{plan?.technology ?? ''}</span><p>{plan?.actions.length ?? 0} action(s) · {plan?.preview.changes.length ?? 0} file change(s)</p></div> },
        { id:'apply', title:'Synchronize safely', description:'Apply the reviewed changes and keep an automatic backup for undo.', checks:[{ id:'apply-ready',label:'Reviewed plan can be applied with backup',complete:plan?.canApply === true,detail:'HYDRA writes only after the preview and conflict gates are green.'}], content:<div className="roundtrip-guided-apply"><p>The apply operation creates backup/journal evidence before changing the target. You can use the rollback tool afterwards.</p>{result !== null && <div className="hydra-operation-result"><strong>{result.rollback ? 'Rollback' : 'Synchronization'} completed</strong><span>{result.affectedPaths.length} affected path(s)</span><code>{result.journalPath}</code></div>}</div> }
    ];

    return <section className="roundtrip-center">
        <div className="hydra-workbench-mode-bar roundtrip-friendly-header"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Technical details" /></div>
        {error !== '' && <HydraActionableErrorNotice message={error} context="round trip" onDismiss={() => setError('')} />}
        {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Synchronize changes with a generated project" description="Compare the HYDRA model with its generated project in five clear steps. Nothing is overwritten until you review the result and confirm the synchronization." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Synchronize with backup" onFinish={apply} /> : <>
            {targetFields}<HydraButton loading={loading} variant="primary" disabled={profileId === '' || targetDirectory.trim() === ''} onClick={() => void preview()}>Compare changes</HydraButton>
            {plan !== null && <div className="roundtrip-plan"><header><div><span className={plan.canApply ? 'status-passed' : 'status-partial'}>{plan.canApply ? 'READY' : 'DECISIONS REQUIRED'}</span><h3>{plan.operationId}</h3><small>{plan.technology} · {plan.targetRoot}</small></div><strong>{plan.actions.length} action(s)</strong></header><div className="roundtrip-change-list">{plan.preview.changes.map(change => <article key={change.path}><span className={`change-${change.kind.toLowerCase()}`}>{change.kind}</span><code>{change.path}</code><p data-hydra-runtime-message="true" data-hydra-message-context="roundtrip">{change.message}</p></article>)}</div>{plan.unresolved.length > 0 && <section className="roundtrip-conflicts"><h3>Element conflicts</h3>{conflictsContent}</section>}<div className="roundtrip-actions"><HydraButton loading={loading} variant="primary" disabled={!plan.canApply} onClick={() => void apply()}>Synchronize with backup</HydraButton></div></div>}
        </>}
        <details className="roundtrip-rollback"><summary>Undo a previous synchronization</summary><div className="roundtrip-rollback-body"><input aria-label="Round-trip operation ID" value={rollbackOperationId} placeholder="sync-…" onChange={event => setRollbackOperationId(event.target.value)} /><HydraButton loading={loading} variant="danger" disabled={profileId === '' || rollbackOperationId.trim() === ''} onClick={() => void rollback()}>Restore backup</HydraButton></div></details>
        {creationMode === 'ADVANCED' && result !== null && <div className="hydra-operation-result"><strong>{result.rollback ? 'Rollback' : 'Synchronization'} completed</strong><span>{result.affectedPaths.length} affected path(s)</span><code>{result.journalPath}</code></div>}
    </section>;
}
