import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraGuidedWorkflow } from '../design-system';
import { loadLatestCompatibilityMatrix, loadNativeToolchainCatalog, runNativeCompatibility } from './api';
import type { NativeToolchainCatalogEntry, TechnologyCompatibilityMatrix } from './types';
import './compatibility-center.css';

export function NativeCompatibilityCenter() {
    const [catalog, setCatalog] = useState<NativeToolchainCatalogEntry[]>([]);
    const [selected, setSelected] = useState<string[]>([]);
    const [matrix, setMatrix] = useState<TechnologyCompatibilityMatrix | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('select');
    const selectedSet = useMemo(() => new Set(selected), [selected]);

    useEffect(() => {
        let active = true;
        void Promise.all([loadNativeToolchainCatalog(), loadLatestCompatibilityMatrix()]).then(([entries, latest]) => {
            if (!active) return;
            setCatalog(entries); setSelected(entries.map(entry => entry.key)); setMatrix(latest); setError('');
        }).catch(exception => { if (!active) return; setError(exception instanceof Error ? exception.message : 'Could not initialize native verification'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    function toggle(key: string, checked: boolean): void {
        setSelected(current => checked ? [...current,key].filter((value,index,values)=>values.indexOf(value)===index) : current.filter(value=>value!==key));
    }
    async function run(): Promise<void> {
        setLoading(true);
        try { setMatrix(await runNativeCompatibility(selected)); setError(''); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Native verification failed'); }
        finally { setLoading(false); }
    }

    const selectionContent = <div className="native-guided-selection"><div className="native-selection-actions"><HydraButton compact size="small" onClick={() => setSelected(catalog.map(entry=>entry.key))}>Select all</HydraButton><HydraButton compact size="small" variant="quiet" onClick={() => setSelected([])}>Clear</HydraButton></div>{catalog.map(entry => <HydraCheckbox checked={selectedSet.has(entry.key)} description={`${entry.version} · ${entry.commandIds.join(', ')}`} key={entry.key} label={entry.technology} onChange={event=>toggle(entry.key,event.target.checked)} />)}</div>;
    const selectedResults = matrix?.results.filter(result => selected.some(key => catalog.find(entry=>entry.key===key)?.technology === result.technology)) ?? [];
    const guidedSteps = [
        { id:'select', title:'Select checks', description:'Choose only the managed technology checks that are relevant to this environment.', checks:[{ id:'probe',label:'At least one bounded native probe is selected',complete:selected.length > 0,detail:`${selected.length} probe(s) selected.`}], content:selectionContent },
        { id:'run', title:'Run checks', description:'Execute only predefined version and compatibility checks; the browser never supplies arbitrary commands.', checks:[{ id:'matrix',label:'A verification matrix is available',complete:matrix !== null,detail:matrix === null ? 'Run the selected probes to record evidence.' : `${matrix.results.length} result(s) recorded.`}], content:<div className="native-guided-run"><HydraButton loading={loading} variant="primary" disabled={selected.length===0} onClick={() => void run()}>Run selected probes</HydraButton>{matrix !== null && <span className={`status-${matrix.overallStatus.toLowerCase()}`}>{matrix.overallStatus}</span>}</div> },
        { id:'review', title:'Review results', description:'Inspect status, versions, duration and evidence before relying on a native toolchain.', checks:[{ id:'results',label:'Selected probe results are available',complete:selectedResults.length > 0,detail:`${selectedResults.length} selected result(s) available.`},{ id:'reviewed',label:'Compatibility status is explicit',complete:matrix !== null,detail:matrix?.overallStatus ?? 'No matrix yet.'}], content:<div className="hydra-guided-review-summary"><strong>{matrix?.overallStatus ?? 'No matrix'}</strong><p>Use the full workspace to inspect every evidence record and diagnostic.</p></div> }
    ];

    return <section className="native-compatibility-center">
        <div className="hydra-workbench-mode-bar"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full workspace" /></div>
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="native compatibility" />}
        {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Step-by-step native verification" description="Select the relevant checks, run them and review the results in order. The full matrix workspace remains available for detailed evidence inspection." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Open full workspace" onFinish={() => { setCreationMode('ADVANCED'); return true; }} /> :
        <div className="native-compatibility-grid"><aside>{selectionContent}<HydraButton loading={loading} variant="primary" disabled={selected.length===0} onClick={() => void run()}>Run selected probes</HydraButton></aside><div className="native-results">{matrix === null ? <div className="hydra-empty-state"><h3>No compatibility matrix yet</h3><p>Run at least one selected technology check to record auditable evidence.</p></div> : <><header><div><span className={`status-${matrix.overallStatus.toLowerCase()}`}>{matrix.overallStatus}</span><h3>Native toolchains</h3></div><time>{new Date(matrix.generatedAt).toLocaleString()}</time></header><div>{matrix.results.map(result => <article key={`${result.technology}:${result.version}`}><div><span className={`status-${result.status.toLowerCase()}`}>{result.status}</span><h4>{result.technology}</h4><small>{result.version} · {result.durationMillis} ms</small></div><p data-hydra-runtime-message="true" data-hydra-message-context="compatibility" data-hydra-message-severity={result.status}>{result.message}</p>{result.evidence.length > 0 && <details><summary>Evidence ({result.evidence.length})</summary><ul>{result.evidence.map((value,index)=><li key={`${value}:${index}`}>{value}</li>)}</ul></details>}</article>)}</div></>}</div></div>}
    </section>;
}
