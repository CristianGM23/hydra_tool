import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraFormField, HydraGuidedWorkflow, HydraProcessOverview, HydraSelect } from '../design-system';
import { executeGeneratedProject, loadGeneratedProjectProfiles, loadGeneratedProjectRegistry, registerGeneratedProject } from './api';
import type { GeneratedProjectDefinition, GeneratedProjectExecutionReport, GeneratedProjectRegistry, GeneratedProjectRuntimeProfile, GeneratedProjectStage } from './types';
import './generated-project-center.css';

const allStages: GeneratedProjectStage[] = ['GENERATE', 'VALIDATE', 'OPEN', 'LAUNCH'];
const stageLabels: Record<GeneratedProjectStage, string> = { GENERATE:'Prepare', VALIDATE:'Validate', OPEN:'Open', LAUNCH:'Launch' };

export function GeneratedProjectCenter() {
    const [profiles, setProfiles] = useState<GeneratedProjectRuntimeProfile[]>([]);
    const [registry, setRegistry] = useState<GeneratedProjectRegistry | null>(null);
    const [selectedProjectId, setSelectedProjectId] = useState('');
    const [selectedStages, setSelectedStages] = useState<GeneratedProjectStage[]>(['GENERATE', 'VALIDATE', 'OPEN']);
    const [draft, setDraft] = useState<GeneratedProjectDefinition>({ id:'', name:'', profileKey:'', relativeDirectory:'', metadata:{} });
    const [dryRun, setDryRun] = useState(true);
    const [allowOptional, setAllowOptional] = useState(false);
    const [report, setReport] = useState<GeneratedProjectExecutionReport | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('register');

    useEffect(() => {
        let active = true;
        void Promise.all([loadGeneratedProjectProfiles(), loadGeneratedProjectRegistry()]).then(([nextProfiles, nextRegistry]) => {
            if (!active) return;
            setProfiles(nextProfiles); setRegistry(nextRegistry);
            setDraft(current => ({ ...current, profileKey:current.profileKey || nextProfiles[0]?.key || '' }));
            setSelectedProjectId(nextRegistry.projects[0]?.id ?? '');
        }).catch(exception => { if (active) setError(exception instanceof Error ? exception.message : 'Could not load generated projects'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const selectedProject = registry?.projects.find(project => project.id === selectedProjectId);
    const selectedProfile = useMemo(() => profiles.find(profile => profile.key === (selectedProject?.profileKey ?? draft.profileKey)), [draft.profileKey, profiles, selectedProject?.profileKey]);
    const draftReady = draft.id.trim() !== '' && draft.profileKey !== '' && draft.relativeDirectory.trim() !== '';

    async function register(): Promise<void> {
        setLoading(true);
        try { const next=await registerGeneratedProject(draft); setRegistry(next); setSelectedProjectId(draft.id); setReport(null); setError(''); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not register generated project'); }
        finally { setLoading(false); }
    }
    async function execute(): Promise<boolean> {
        if (selectedProjectId === '') return false;
        setLoading(true);
        try { setReport(await executeGeneratedProject(selectedProjectId, selectedStages, dryRun, allowOptional)); setError(''); return true; }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not execute generated project'); return false; }
        finally { setLoading(false); }
    }
    function toggleStage(stage: GeneratedProjectStage, checked: boolean): void {
        setSelectedStages(current => checked ? [...current, stage].filter((value,index,values)=>values.indexOf(value)===index) : current.filter(value=>value!==stage)); setReport(null);
    }

    const registrationFields = <div className="generated-project-guided-fields">
        <HydraFormField label="Project ID" targetId="generated-project-id" required help="Stable identifier used by HYDRA to remember this generated project across lifecycle runs." example="besser-demo"><input id="generated-project-id" value={draft.id} onChange={event => setDraft({ ...draft,id:event.target.value })} /></HydraFormField>
        <HydraFormField label="Name" targetId="generated-project-name" help="Friendly name shown in project selectors and lifecycle summaries." example="Customer portal"><input id="generated-project-name" value={draft.name} onChange={event => setDraft({ ...draft,name:event.target.value })} /></HydraFormField>
        <HydraFormField label="Lifecycle profile" targetId="generated-project-profile" required help="Managed recipe that tells HYDRA how this generated technology is prepared, validated, opened and optionally launched." example="BESSER SDK project"><HydraSelect ariaLabel="Generated project lifecycle profile" value={draft.profileKey} options={profiles.map(profile => ({ value:profile.key,label:profile.name,description:profile.description,category:profile.technology }))} onChange={profileKey => setDraft({ ...draft,profileKey })} /></HydraFormField>
        <HydraFormField label="Generated folder (relative)" targetId="generated-project-directory" required help="Directory below HYDRA's configured generated-project root. Absolute paths and traversal are rejected."><input id="generated-project-directory" value={draft.relativeDirectory} onChange={event => setDraft({ ...draft,relativeDirectory:event.target.value })} /></HydraFormField>
        <HydraButton disabled={!draftReady} onClick={() => void register()}>Register project</HydraButton>
    </div>;
    const lifecycleControls = <div className="generated-project-guided-controls"><HydraSelect ariaLabel="Registered generated project" value={selectedProjectId} placeholder="Select project" options={(registry?.projects ?? []).map(project => ({ value:project.id,label:project.name || project.id,description:project.relativeDirectory,category:project.profileKey }))} onChange={value => { setSelectedProjectId(value); setReport(null); }} /><HydraCheckbox checked={dryRun} label="Dry run" description="Plan commands without executing native tools." onChange={event => { setDryRun(event.target.checked); setReport(null); }} /><HydraCheckbox checked={allowOptional} label="Allow optional stages" description="Required for controlled launch stages." onChange={event => { setAllowOptional(event.target.checked); setReport(null); }} /></div>;
    const stageFields = <div className="generated-project-stages" aria-label="Lifecycle stages">{allStages.map(stage => { const available=selectedProfile?.stages.some(item=>item.stage===stage)??false; return <HydraCheckbox checked={available&&selectedStages.includes(stage)} disabled={!available} key={stage} label={stageLabels[stage]} description={selectedProfile?.stages.find(item=>item.stage===stage)?.description ?? 'Not available in this profile.'} onChange={event=>toggleStage(stage,event.target.checked)} />; })}</div>;
    const guidedSteps = [
        { id:'register', title:'Register project', description:'Describe the generated project once using a stable ID, a profile and a safe relative folder.', checks:[{ id:'draft',label:'Project registration fields are complete',complete:draftReady,detail:'ID, lifecycle profile and relative folder are required.'},{ id:'registered',label:'A registered project is selected',complete:selectedProjectId !== '',detail:selectedProjectId === '' ? 'Register the draft or select an existing project.' : selectedProjectId}], content:registrationFields },
        { id:'execution', title:'Choose the project', description:'Select the registered project and start with a preview whenever possible.', checks:[{ id:'project',label:'Registered project is selected',complete:selectedProjectId !== '',detail:selectedProject?.relativeDirectory ?? 'No project selected.'},{ id:'dry-run',label:'Lifecycle intent is explicit',complete:true,detail:dryRun ? 'Dry run is enabled: native commands will only be planned.' : 'Real execution is enabled; review stages carefully.'}], content:lifecycleControls },
        { id:'stages', title:'Choose stages', description:'Select only the stages that the chosen managed profile supports.', checks:[{ id:'stage',label:'At least one available stage is selected',complete:selectedStages.length > 0 && selectedStages.some(stage => selectedProfile?.stages.some(item=>item.stage===stage)),detail:`${selectedStages.length} stage(s) selected.`},{ id:'launch',label:'Optional launch policy is explicit',complete:!selectedStages.includes('LAUNCH') || allowOptional,detail:selectedStages.includes('LAUNCH') ? 'Launch requires Allow optional stages.' : 'No launch stage selected.'}], content:stageFields },
        { id:'preview', title:'Preview lifecycle', description:'Review commands, timeouts and diagnostics before real execution.', checks:[{ id:'report',label:'A lifecycle report is available',complete:report !== null,detail:report === null ? 'Run the lifecycle in dry-run mode first.' : report.successful ? 'The lifecycle report passed.' : 'Review failed stages and diagnostics.'},{ id:'successful',label:'Selected lifecycle stages pass',complete:report?.successful === true,detail:report?.successful ? 'No blocking lifecycle step failed.' : 'Resolve lifecycle failures before continuing.'}], content:<div className="generated-project-guided-preview"><HydraButton loading={loading} variant="primary" disabled={selectedProjectId === '' || selectedStages.length === 0} onClick={() => void execute()}>{dryRun ? 'Preview lifecycle' : 'Run lifecycle'}</HydraButton>{report !== null && <span className={report.successful ? 'status-passed':'status-failed'}>{report.successful ? 'PASSED':'FAILED'}</span>}</div> },
        { id:'run', title:'Run or inspect result', description:'Run only after the selected stages and preview are understood.', checks:[{ id:'ready',label:'Lifecycle configuration is ready',complete:selectedProjectId !== '' && selectedStages.length > 0 && report?.successful === true,detail:'The same full workspace remains available for direct lifecycle control.'}], content:<div className="hydra-guided-review-summary"><strong>{selectedProject?.name || selectedProject?.id || 'No project'}</strong><span>{selectedProfile?.name ?? ''}</span><p>{selectedStages.map(stage => stageLabels[stage]).join(' → ')}</p></div> }
    ];

    return <section className="generated-project-center">
        <div className="hydra-workbench-mode-bar"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full workspace" /></div>
        {error !== '' && <HydraActionableErrorNotice message={error} context="generated project" onDismiss={() => setError('')} />}
        {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Prepare and run a generated project" description="Choose a generated project, preview what HYDRA will do, run only the stages you need and inspect one clear result. Technical commands stay available without becoming the starting point." outcome="A checked generated project that is ready to open or launch safely." recommendation="Start with Dry run. It lets you review commands, stages and timeouts without executing native tools." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel={dryRun ? 'Preview lifecycle' : 'Run lifecycle'} onFinish={execute} /> :
        <><HydraProcessOverview title="Prepare and run a generated project" summary="Choose a generated project, preview what HYDRA will do, run only the stages you need and inspect one clear result." outcome="A checked generated project that is ready to open or launch safely." recommendation="Start with Dry run. It lets you review commands, stages and timeouts without executing native tools." /><div className="generated-project-layout"><aside><h3>Register generated project</h3>{registrationFields}</aside><div className="generated-project-workspace">{lifecycleControls}{stageFields}<div className="generated-project-controls"><HydraButton loading={loading} variant="primary" disabled={selectedProjectId === '' || selectedStages.length === 0} onClick={() => void execute()}>{dryRun ? 'Preview lifecycle' : 'Run lifecycle'}</HydraButton></div>{report === null ? <div className="hydra-empty-state"><h3>No lifecycle report yet</h3><p>Use dry run first to inspect every server-owned command and timeout.</p></div> : <div className="generated-project-report"><header><span className={report.successful ? 'status-passed':'status-failed'}>{report.successful ? 'PASSED':'FAILED'}</span><strong>{report.project.name}</strong><small>{report.resolvedDirectory}</small></header>{report.steps.map(step => <article key={step.stage}><span className={`status-${step.status.toLowerCase()}`}>{step.status}</span><div><h4>{stageLabels[step.stage]}</h4><p>{step.summary}</p>{step.command.length > 0 && <code>{step.command.join(' ')}</code>}{step.diagnostics.length > 0 && <ul>{step.diagnostics.map(value => <li key={value}>{value}</li>)}</ul>}</div></article>)}</div>}</div></div></>}
    </section>;
}
