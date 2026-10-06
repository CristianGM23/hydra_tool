import { useState } from 'react';
import { HYDRA_VERSION } from '../generated/hydraVersion';
import { HydraActionableErrorNotice, HydraButton, HydraCreationModeSwitch, HydraFormField, HydraGuidedWorkflow, HydraProcessOverview, HydraSelect } from '../design-system';
import { checkDistributionUpdate, publishDistribution, validatePublication, type PublicationDraft } from './api';
import type { PublicationResult, PublicationValidationReport, UpdateCheckResult } from './types';
import './publication-center.css';

const initial: PublicationDraft = {
    productId: 'hydra-editor', version: HYDRA_VERSION, channel: 'INTERNAL', signingMode: 'CHECKSUM_ONLY',
    distributionDirectory: 'hydra-editor', updateBaseUrl: '',
    artifacts: [{ platform: 'windows-x64', relativePath: 'hydra-editor.zip', mediaType: 'application/zip' }],
    metadata: {}
};
const platformOptions = [
    { value:'windows-x64', label:'Windows x64' },
    { value:'linux-x64', label:'Linux x64' },
    { value:'macos-x64', label:'macOS x64' },
    { value:'macos-arm64', label:'macOS ARM64' },
];

export function PublicationCenter() {
    const [draft, setDraft] = useState(initial);
    const [platform, setPlatform] = useState(initial.artifacts[0].platform);
    const [artifactPath, setArtifactPath] = useState(initial.artifacts[0].relativePath);
    const [validation, setValidation] = useState<PublicationValidationReport | null>(null);
    const [result, setResult] = useState<PublicationResult | null>(null);
    const [update, setUpdate] = useState<UpdateCheckResult | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('identity');

    function invalidate(): void { setValidation(null); setResult(null); }
    function request(): PublicationDraft { return { ...draft, artifacts: [{ platform, relativePath: artifactPath, mediaType: 'application/zip' }] }; }

    async function validate(): Promise<void> {
        setLoading(true);
        try { setValidation(await validatePublication(request())); setError(''); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Publication validation failed'); }
        finally { setLoading(false); }
    }
    async function publish(): Promise<boolean> {
        setLoading(true);
        try { const next = await publishDistribution(request()); setResult(next); setValidation(next.validation); setError(''); return true; }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Publication failed'); return false; }
        finally { setLoading(false); }
    }
    async function check(): Promise<void> {
        setLoading(true);
        try { setUpdate(await checkDistributionUpdate(draft.productId, HYDRA_VERSION, draft.channel, platform)); setError(''); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Update check failed'); }
        finally { setLoading(false); }
    }

    const identityFields = <div className="publication-guided-grid">
        <HydraFormField label="Product ID" targetId="publication-product" required help="Stable product identifier used by release manifests and update checks. It should stay the same across versions." example="hydra-editor"><input id="publication-product" value={draft.productId} onChange={event => { setDraft({ ...draft, productId:event.target.value }); invalidate(); }} /></HydraFormField>
    </div>;
    const artifactFields = <div className="publication-guided-grid">
        <HydraFormField label="Distribution folder (relative)" targetId="publication-directory" required help="Folder below HYDRA's configured publication root. It is not a physical operating-system path." example="hydra-editor"><input id="publication-directory" value={draft.distributionDirectory} onChange={event => { setDraft({ ...draft, distributionDirectory:event.target.value }); invalidate(); }} /></HydraFormField>
        <HydraFormField label="Platform" targetId="publication-platform" required help="Choose the packaged target platform instead of entering a free-text value."><HydraSelect ariaLabel="Publication platform" value={platform} options={platformOptions} onChange={value => { setPlatform(value); invalidate(); }} /></HydraFormField>
        <HydraFormField label="Artifact path (relative)" targetId="publication-artifact" required help="File path relative to the distribution folder." example="hydra-editor.zip"><input id="publication-artifact" value={artifactPath} onChange={event => { setArtifactPath(event.target.value); invalidate(); }} /></HydraFormField>
    </div>;
    const deliveryFields = <div className="publication-guided-grid">
        <HydraFormField label="Channel" targetId="publication-channel"><HydraSelect ariaLabel="Publication channel" value={draft.channel} options={[{ value:'INTERNAL',label:'Internal' },{ value:'BETA',label:'Beta' },{ value:'STABLE',label:'Stable' }]} onChange={value => { setDraft({ ...draft, channel:value as PublicationDraft['channel'] }); invalidate(); }} /></HydraFormField>
        <HydraFormField label="Signing mode" targetId="publication-signing"><HydraSelect ariaLabel="Publication signing mode" value={draft.signingMode} options={[{ value:'CHECKSUM_ONLY',label:'Checksum only',description:'Publish SHA-256 evidence without private keys.' },{ value:'EXTERNAL_SIGNATURE',label:'External signature',description:'Require a companion .sig file generated outside HYDRA.' }]} onChange={value => { setDraft({ ...draft, signingMode:value as PublicationDraft['signingMode'] }); invalidate(); }} /></HydraFormField>
        <HydraFormField label="Update base URL" targetId="publication-update-url" help="Required for beta and stable channels. HYDRA stores only the public base URL."><input id="publication-update-url" value={draft.updateBaseUrl} onChange={event => { setDraft({ ...draft, updateBaseUrl:event.target.value }); invalidate(); }} /></HydraFormField>
    </div>;
    const channelNeedsUrl = draft.channel !== 'INTERNAL';
    const guidedSteps = [
        { id:'identity', title:'Release identity', description:'Choose the product that will be delivered. HYDRA supplies release metadata automatically.', checks:[{ id:'product', label:'Product ID is defined', complete:draft.productId.trim() !== '', detail:'The update service uses this stable product identifier.' }], content:identityFields },
        { id:'artifact', title:'Distribution package', description:'Point HYDRA to the packaged build and select its target platform.', checks:[{ id:'directory', label:'Distribution folder is defined', complete:draft.distributionDirectory.trim() !== '', detail:'HYDRA resolves the package below this managed distribution root.' },{ id:'platform', label:'Platform is selected', complete:platform.trim() !== '', detail:'Pick the packaged target platform from the list.' },{ id:'path', label:'Artifact path is defined', complete:artifactPath.trim() !== '', detail:'The file path is relative to the distribution folder.' }], content:artifactFields },
        { id:'delivery', title:'Delivery settings', description:'Select the release channel, integrity policy and public update address.', checks:[{ id:'channel', label:'Publication channel is selected', complete:draft.channel !== undefined, detail:`Current channel: ${draft.channel}.` },{ id:'url', label:'Update URL is valid for the selected channel', complete:!channelNeedsUrl || draft.updateBaseUrl.trim() !== '', detail:channelNeedsUrl ? 'Beta and stable channels require a public update base URL.' : 'Internal releases do not require a public update URL.' }], content:deliveryFields },
        { id:'validate', title:'Validate release', description:'Check the package and metadata before publishing anything.', checks:[{ id:'validation', label:'Publication validation passes', complete:validation?.valid === true, detail:validation === null ? 'Run validation to inspect checksums, paths and channel requirements.' : validation.valid ? 'No blocking publication issue remains.' : 'Resolve the reported issues.' }], content:<div className="publication-guided-verification"><HydraButton loading={loading} variant="primary" onClick={() => void validate()}>Validate release</HydraButton>{validation !== null && <span className={validation.valid ? 'status-passed' : 'status-failed'}>{validation.valid ? 'VALID' : 'BLOCKED'}</span>}</div> },
        { id:'publish', title:'Review and publish', description:'Review the prepared release and publish the reproducible manifest.', checks:[{ id:'ready', label:'Validated release is ready for publication', complete:validation?.valid === true, detail:'Publishing remains disabled until validation is green.' }], content:<div className="hydra-guided-review-summary"><strong>{draft.productId}</strong><span>{draft.channel} · {platform}</span><p>{artifactPath}</p>{result !== null && <code>{result.manifestPath}</code>}</div> }
    ];

    return <section className="publication-center">
        <div className="hydra-workbench-mode-bar"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full workspace" /></div>
        {error !== '' && <HydraActionableErrorNotice message={error} context="publication" onDismiss={() => setError('')} />}
        {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Publish a release with confidence" description="Prepare one delivery, validate it before anything is published, create the release manifest and finally check what an installed application would see." outcome="A validated release manifest with traceable artifacts and an update path you can verify." recommendation="Keep the first pass simple: identify the release and package, validate it, then publish. Open delivery settings only when the channel needs them." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Publish release" onFinish={publish} /> :
        <><HydraProcessOverview title="Publish a release with confidence" summary="Prepare one delivery, validate it before anything is published, create the release manifest and finally check what an installed application would see." outcome="A validated release manifest with traceable artifacts and an update path you can verify." recommendation="Keep the first pass simple: identify the release and package, validate it, then publish. Open delivery settings only when the channel needs them." /><div className="publication-layout">
            <aside>{identityFields}{deliveryFields}{artifactFields}</aside>
            <div className="publication-results">
                <section><h3>Validation</h3><div className="hydra-center-actions"><HydraButton loading={loading} onClick={() => void validate()}>Validate</HydraButton><HydraButton loading={loading} variant="primary" disabled={validation !== null && !validation.valid} onClick={() => void publish()}>Publish release</HydraButton></div>{validation === null ? <p>Validate the release before publishing.</p> : <><span className={validation.valid ? 'status-passed' : 'status-failed'}>{validation.valid ? 'VALID' : 'BLOCKED'}</span>{validation.diagnostics.map(item => <article key={`${item.code}:${item.field}`}><strong>{item.code}</strong><p data-hydra-runtime-message="true" data-hydra-message-context="publication" data-hydra-message-code={item.code}>{item.message}</p></article>)}</>}</section>
                <section><h3>Published manifest</h3>{result === null ? <p>No release has been published in this session.</p> : <><strong>{result.manifest.productId}</strong><small>{result.manifest.channel} · {result.manifestPath}</small>{result.manifest.artifacts.map(item => <article key={item.platform}><strong>{item.platform}</strong><code>{item.sha256}</code><span>{item.size} bytes</span></article>)}</>}</section>
                <section className="publication-update-check"><h3>Update resolver</h3><p>HYDRA compares the installed build metadata automatically.</p><HydraButton onClick={() => void check()}>Check channel</HydraButton>{update !== null && <article><span className={update.updateAvailable ? 'status-passed' : 'status-not-run'}>{update.updateAvailable ? 'UPDATE AVAILABLE' : 'NO UPDATE'}</span><p data-hydra-runtime-message="true" data-hydra-message-context="publication">{update.message}</p>{update.artifact !== null && <code>{update.artifact.downloadUrl || update.artifact.fileName}</code>}</article>}</section>
            </div>
        </div></>}
    </section>;
}
