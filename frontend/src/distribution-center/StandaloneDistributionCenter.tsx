import { useMemo, useState } from 'react';
import { useHydraLanguage } from '../i18n';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraDiagnosticList, HydraFormField, HydraGuidedWorkflow, HydraOperationProgress, HydraSelect } from '../design-system';
import { materializeStandaloneDistribution, packageStandaloneDistribution, planStandaloneDistribution, validateStandaloneDistribution } from './api';
import type { StandaloneDistributionArtifact, StandaloneDistributionPreparationResult, StandaloneDistributionProfile, StandaloneDistributionTarget, StandaloneDistributionValidationReport } from './types';
import './distribution-center.css';

const targets: Array<{ id: StandaloneDistributionTarget; label: string; description: string }> = [
    { id: 'PWA', label: 'Progressive Web App', description: 'Installable web editor with optional offline application shell.' },
    { id: 'TAURI_WINDOWS', label: 'Tauri for Windows', description: 'Desktop configuration and file associations for Windows.' },
    { id: 'TAURI_MACOS', label: 'Tauri for macOS', description: 'Desktop configuration for macOS packaging.' },
    { id: 'TAURI_LINUX', label: 'Tauri for Linux', description: 'Desktop configuration for Linux packaging.' }
];

const associationPresets = [
    { value:'HYDRA_XMI', label:'HYDRA and XMI', description:'Associates both native HYDRA documents and XMI model files with the desktop editor.' },
    { value:'HYDRA', label:'HYDRA only', description:'Associates only .hydra files with the desktop editor.' },
    { value:'XMI', label:'XMI only', description:'Associates only .xmi model files with the desktop editor.' },
    { value:'JSON', label:'JSON models', description:'Associates .json files when the generated editor is intended to open JSON model documents.' },
    { value:'NONE', label:'No desktop association', description:'Creates the desktop package without registering file extensions.' },
    { value:'CUSTOM', label:'Custom associations…', description:'Use this only when the generated editor needs extensions that are not covered by the predefined choices.' }
] as const;

const channelPresets = [
    { value:'stable', label:'Stable', description:'Recommended for normal releases intended for end users.' },
    { value:'beta', label:'Beta', description:'Use for pre-release builds that are feature-complete but still being validated.' },
    { value:'preview', label:'Preview', description:'Use for early evaluation builds that may still change.' },
    { value:'nightly', label:'Nightly', description:'Use for frequently generated development builds.' },
    { value:'__CUSTOM__', label:'Custom channel…', description:'Use a custom channel only when your release process already defines one.' }
] as const;

function associationsForPreset(preset: string): string {
    if (preset === 'HYDRA_XMI') return '.hydra,.xmi';
    if (preset === 'HYDRA') return '.hydra';
    if (preset === 'XMI') return '.xmi';
    if (preset === 'JSON') return '.json';
    if (preset === 'NONE') return '';
    return '.hydra,.xmi';
}

export function StandaloneDistributionCenter() {
    const { t } = useHydraLanguage();
    const [editorDirectory, setEditorDirectory] = useState('generated-editor');
    const [productName, setProductName] = useState('HYDRA Generated Editor');
    const [version, setVersion] = useState('1.0.0');
    const [selectedTargets, setSelectedTargets] = useState<StandaloneDistributionTarget[]>(['PWA', 'TAURI_WINDOWS']);
    const [offlineEnabled, setOfflineEnabled] = useState(true);
    const [associationPreset, setAssociationPreset] = useState('HYDRA_XMI');
    const [associations, setAssociations] = useState('.hydra,.xmi');
    const [channelPreset, setChannelPreset] = useState('stable');
    const [updateChannel, setUpdateChannel] = useState('stable');
    const [artifacts, setArtifacts] = useState<StandaloneDistributionArtifact[]>([]);
    const [preparation, setPreparation] = useState<StandaloneDistributionPreparationResult | null>(null);
    const [validation, setValidation] = useState<StandaloneDistributionValidationReport | null>(null);
    const [downloadSha, setDownloadSha] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('source');
    const [operation, setOperation] = useState<{title:string;detail:string;stage:string}|null>(null);

    const profile = useMemo<StandaloneDistributionProfile>(() => ({ productName, version, targets: selectedTargets, offlineEnabled,
        fileAssociations: associations.split(',').map(value => value.trim()).filter(Boolean), updateChannel }),
        [productName, version, selectedTargets, offlineEnabled, associations, updateChannel]);
    const request = useMemo(() => ({ editorDirectory, profile }), [editorDirectory, profile]);
    const validationProblems = validation?.diagnostics.filter(diagnostic => diagnostic.severity !== 'INFO') ?? [];

    function clearVerification(): void { setValidation(null); setDownloadSha(''); }
    function toggleTarget(target: StandaloneDistributionTarget, checked: boolean): void {
        setSelectedTargets(current => checked ? [...current, target].filter((value, index, values) => values.indexOf(value) === index) : current.filter(value => value !== target));
        clearVerification();
    }
    function chooseAssociationPreset(value: string): void {
        setAssociationPreset(value);
        if (value !== 'CUSTOM') setAssociations(associationsForPreset(value));
        clearVerification();
    }
    function chooseChannel(value: string): void {
        setChannelPreset(value);
        if (value !== '__CUSTOM__') setUpdateChannel(value);
        clearVerification();
    }
    async function execute<T>(action: () => Promise<T>, apply: (result: T) => void, stage = 'prepare'): Promise<void> {
        setLoading(true);
        setOperation({title:'Preparing standalone distribution',detail:stage === 'validate' ? 'Validating the existing editor and distribution metadata.' : stage === 'materialize' ? 'Generating distribution artifacts and validating the materialized result.' : 'Planning deterministic distribution artifacts.',stage});
        try { apply(await action()); setError(''); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Distribution operation failed'); }
        finally { setLoading(false); setOperation(null); }
    }
    async function download(): Promise<boolean> {
        setLoading(true);
        setOperation({title:'Packaging standalone distribution',detail:'Creating the verified deterministic archive.',stage:'package'});
        try {
            const result = await packageStandaloneDistribution(request);
            setOperation({title:'Packaging standalone distribution',detail:'Archive created. Preparing the download.',stage:'download'});
            const url = URL.createObjectURL(result.blob); const anchor = document.createElement('a');
            anchor.href = url; anchor.download = result.fileName; anchor.click(); URL.revokeObjectURL(url);
            setDownloadSha(result.sha256); setError(''); return true;
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not package standalone editor'); return false; }
        finally { setLoading(false); setOperation(null); }
    }

    const sourceFields = <HydraFormField label="Editor folder (relative)" targetId="distribution-directory" required help="Folder of the generated editor, relative to HYDRA’s configured safe distribution root. HYDRA reads this folder during validation and never needs an absolute system path." example="people-editor">
        <input id="distribution-directory" value={editorDirectory} onChange={event => { setEditorDirectory(event.target.value); clearVerification(); }} />
    </HydraFormField>;
    const identityFields = <div className="distribution-form-grid">
        <HydraFormField label="Product name" targetId="distribution-product" required help="Name that users will see in the installed web or desktop application. It is copied to the generated manifest and desktop configuration." example="People Modeling Editor"><input id="distribution-product" value={productName} onChange={event => { setProductName(event.target.value); clearVerification(); }} /></HydraFormField>
        <HydraFormField label="Version" targetId="distribution-version" required help="Version written to every generated distribution descriptor. Use the version of the editor you are packaging, normally in major.minor.patch form." example="1.0.0"><input id="distribution-version" value={version} onChange={event => { setVersion(event.target.value); clearVerification(); }} /></HydraFormField>
        <HydraFormField label="File associations" targetId="distribution-associations-preset" help="Choose which model files the desktop package should register with the operating system. Use a predefined option unless the generated editor really needs another extension." options={associationPresets}>
            <HydraSelect id="distribution-associations-preset" ariaLabel="File associations" value={associationPreset} options={associationPresets} onChange={chooseAssociationPreset} />
        </HydraFormField>
        {associationPreset === 'CUSTOM' && <HydraFormField label="Custom file associations" targetId="distribution-associations" help="Comma-separated file extensions including the leading dot. Add only formats that the generated editor can actually open." example=".modelo,.xmi"><input id="distribution-associations" value={associations} onChange={event => { setAssociations(event.target.value); clearVerification(); }} /></HydraFormField>}
        <HydraFormField label="Update channel" targetId="distribution-channel-preset" help="Release channel recorded in the distribution descriptor. Stable is recommended for normal releases; use Beta, Preview or Nightly only when users should expect a pre-release build." options={channelPresets}>
            <HydraSelect id="distribution-channel-preset" ariaLabel="Update channel" value={channelPreset} options={channelPresets} onChange={chooseChannel} />
        </HydraFormField>
        {channelPreset === '__CUSTOM__' && <HydraFormField label="Custom update channel" targetId="distribution-channel" help="Short stable channel identifier used by your own release process. Avoid changing it between builds of the same channel." example="internal"><input id="distribution-channel" value={updateChannel} onChange={event => { setUpdateChannel(event.target.value); clearVerification(); }} /></HydraFormField>}
    </div>;
    const targetFields = <><fieldset><legend>{t('Distribution targets')}</legend>{targets.map(target => <HydraCheckbox checked={selectedTargets.includes(target.id)} description={target.description} key={target.id} label={target.label} onChange={event => toggleTarget(target.id, event.target.checked)} />)}</fieldset>
        <HydraCheckbox checked={offlineEnabled} description="Caches the application shell so the generated PWA can start without a network connection after it has been installed." label="Offline application shell" onChange={event => { setOfflineEnabled(event.target.checked); clearVerification(); }} /></>;
    const verificationActions = <div className="distribution-guided-verification"><div className="distribution-actions">
        <HydraButton loading={loading} disabled={selectedTargets.length === 0} onClick={() => void execute(() => planStandaloneDistribution(request), setArtifacts)}>Preview artifacts</HydraButton>
        <HydraButton loading={loading} disabled={selectedTargets.length === 0} variant="primary" onClick={() => void execute(() => materializeStandaloneDistribution(request), result => { setPreparation(result); setValidation(result.validation); }, 'materialize')}>Materialize and validate</HydraButton>
        <HydraButton loading={loading} disabled={selectedTargets.length === 0} onClick={() => void execute(() => validateStandaloneDistribution(request), setValidation, 'validate')}>Validate existing editor</HydraButton>
    </div>{validation !== null && <><div className={validation.valid ? 'distribution-guided-result valid' : 'distribution-guided-result invalid'}><strong>{t(validation.valid ? 'Distribution valid' : 'Distribution blocked')}</strong><span>{validationProblems.length} {t('problem(s)')}</span></div><HydraDiagnosticList diagnostics={validationProblems} context="distribution" title={t('Distribution diagnostics')} emptyText={t('No distribution problem remains.')} /></>}</div>;

    const guidedSteps = [
        { id:'source', title:'Editor source', description:'Identify the generated editor directory that will be packaged.', checks:[{ id:'directory', label:'Editor directory is defined', complete:editorDirectory.trim() !== '', detail:'Use a relative folder inside HYDRA’s configured safe distribution root.' }], content:sourceFields },
        { id:'identity', title:'Product identity', description:'Define the application name, version and desktop metadata using presets where possible.', checks:[{ id:'name', label:'Product name is defined', complete:productName.trim() !== '', detail:'This is the application name users will see.' }, { id:'version', label:'Distribution version is defined', complete:version.trim() !== '', detail:'The same version will be written to all generated descriptors.' }], content:identityFields },
        { id:'targets', title:'Distribution targets', description:'Choose only the web and desktop packages that you really intend to deliver.', checks:[{ id:'target', label:'At least one distribution target is selected', complete:selectedTargets.length > 0, detail:`${selectedTargets.length} target(s) selected.` }], content:targetFields },
        { id:'verify', title:'Materialize and verify', description:'Generate the distribution safely and show every validation problem here before packaging.', checks:[{ id:'validated', label:'The materialized distribution passes validation', complete:validation?.valid === true, detail:validation === null ? 'Run Materialize and validate. HYDRA will list every problem in this step.' : validation.valid ? 'No blocking distribution diagnostic remains.' : 'The diagnostics below explain what failed, where it failed and what to correct.' }], content:verificationActions },
        { id:'package', title:'Package', description:'Create the deterministic ZIP only after the validation step is green.', checks:[{ id:'ready', label:'Distribution is ready to package', complete:validation?.valid === true, detail:'Packaging stays disabled until validation has no blocking error.' }], content:<div className="hydra-guided-review-summary"><strong>{productName || t('Unnamed product')}</strong><span>{version}</span><p>{selectedTargets.join(', ') || t('No targets')}</p>{downloadSha !== '' && <code>SHA-256 {downloadSha}</code>}</div> }
    ];

    return <section className="standalone-distribution-center">
        <div className="hydra-workbench-mode-bar"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full workspace" /></div>
        <HydraOperationProgress active={operation !== null} title={operation?.title ?? ''} detail={operation?.detail} currentStageId={operation?.stage ?? ''} stages={[{id:'prepare',label:'Plan artifacts'},{id:'materialize',label:'Materialize distribution'},{id:'validate',label:'Validate output'},{id:'package',label:'Package archive'},{id:'download',label:'Prepare download'}]} />
        {error !== '' && <HydraActionableErrorNotice message={error} context="distribution" onDismiss={() => setError('')} />}
        {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Guided distribution" description="Prepare a standalone distribution in a verified order. Presets cover the normal case and the complete workspace remains available for advanced configuration." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Package ZIP" onFinish={download} /> :
        <div className="distribution-layout">
            <form onSubmit={event => event.preventDefault()}>
                {sourceFields}{identityFields}{targetFields}
                <div className="distribution-actions">
                    <HydraButton loading={loading} disabled={selectedTargets.length === 0} onClick={() => void execute(() => planStandaloneDistribution(request), setArtifacts)}>Preview artifacts</HydraButton>
                    <HydraButton loading={loading} disabled={selectedTargets.length === 0} variant="primary" onClick={() => void execute(() => materializeStandaloneDistribution(request), result => { setPreparation(result); setValidation(result.validation); }, 'materialize')}>Materialize and validate</HydraButton>
                    <HydraButton loading={loading} disabled={selectedTargets.length === 0} onClick={() => void execute(() => validateStandaloneDistribution(request), setValidation, 'validate')}>Validate existing editor</HydraButton>
                    <HydraButton loading={loading} disabled={validation?.valid !== true} variant="primary" onClick={() => void download()}>Package ZIP</HydraButton>
                </div>
            </form>
            <aside>
                <section><h3>{t('Planned artifacts')}</h3>{artifacts.length === 0 ? <p>{t('No preview yet.')}</p> : <ul>{artifacts.map(artifact => <li key={artifact.path}><code>{artifact.path}</code><span>{artifact.mediaType}</span></li>)}</ul>}</section>
                {preparation !== null && <section><h3>{t('Materialization')}</h3><p>{t(`${preparation.materialization.writtenPaths.length} file(s) written below:`)}</p><code>{preparation.materialization.root}</code></section>}
                {validation !== null && <section className={validation.valid ? 'valid' : 'invalid'}><h3>{t(validation.valid ? 'Distribution valid' : 'Distribution blocked')}</h3><HydraDiagnosticList diagnostics={validationProblems} context="distribution" title={t('Distribution diagnostics')} emptyText={t('No distribution problem remains.')} /></section>}
                {downloadSha !== '' && <section className="distribution-download-result"><h3>{t('Package downloaded')}</h3><span>SHA-256</span><code>{downloadSha}</code></section>}
            </aside>
        </div>}
    </section>;
}
