import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { generateStandaloneEditor } from './api';
import type { MetaModel } from '../metamodel-designer/types';
import type { EditorDefinitionCompilationResult, ExecutableRepresentationPlan } from './types';
import { EditorRuntimeSession } from './EditorRuntimeSession';
import { editorVisualAssetUrl } from './editorVisualAssetUrl';
import { buildStandaloneCapabilityPackage } from './standaloneCapabilityPackage';
import { HydraCreationModeSwitch, HydraGuidedWorkflow, HydraOperationProgress, HydraTooltip, HydraSelect } from '../design-system';
import { EditorNodeShapeFrame } from './EditorNodeShapeFrame';
import { resolveNodeNotationStyle } from './editorNotationStyle';
import { edgeMappingDisplayName } from './editorSemanticEdge';

interface Props {
    result: EditorDefinitionCompilationResult | null;
    metamodel: MetaModel;
    compiling: boolean;
    onCompile: () => void;
    onError?: (message: string) => void;
    onMessage?: (message: string) => void;
}

export function EditorRuntimePreview({ result, metamodel, compiling, onCompile, onError, onMessage }: Props) {
    const [selectedRepresentationId, setSelectedRepresentationId] = useState('');
    const [generatingStandalone, setGeneratingStandalone] = useState(false);
    const [deploymentTarget, setDeploymentTarget] = useState<'SOURCE' | 'DOCKER' | 'KUBERNETES'>('SOURCE');
    const [exportMode, setExportMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [exportStep, setExportStep] = useState('runtime');
    const [generationStage, setGenerationStage] = useState('');
    const representations = result === null ? [] : Object.values(result.plan.representationsById);
    const active = representations.find(item => item.id === selectedRepresentationId) ?? representations[0] ?? null;
    async function downloadStandalone(): Promise<boolean> {
        if(result === null)return false;
        setGeneratingStandalone(true);
        setGenerationStage('layers');
        try {
            const portable = await buildStandaloneCapabilityPackage(metamodel, result);
            setGenerationStage('generate');
            const generated=await generateStandaloneEditor({
                applicationId:result.plan.sourceEditorDefinitionId,
                name:result.plan.name,
                metamodelId:metamodel.id,
                editorId:result.plan.sourceEditorDefinitionId,
                port:4173,
                runtimeMode:'EMBEDDED_LOCAL',
                runtimeBaseUrl:'',
                includeCapabilityArchive:true,
                includeAllCanonicalDocuments:true,
                maximumModelBytes:10485760,
                includedViewpointIds:representations.map(item=>item.id),
                allowedOperationIds:[],
                branding:{},
                deploymentTargets:[deploymentTarget],
                capabilityPackage:portable.capabilityPackage
            });
            setGenerationStage('download');
            const url=URL.createObjectURL(generated.blob);
            const anchor=document.createElement('a');anchor.href=url;anchor.download=generated.fileName;anchor.click();URL.revokeObjectURL(url);
            const targetLabel=deploymentTarget==='DOCKER'?'Docker':deploymentTarget==='KUBERNETES'?'Kubernetes':'standalone source';
            const warningText=portable.warnings.length===0?'':` · ${portable.warnings.length} optional layer(s) were not available`;
            onMessage?.(`${targetLabel} editor package generated · ${portable.includedCapabilities.length} portable capabilities${warningText}${generated.sha256===''?'':` · SHA-256 ${generated.sha256}`}`);
            return true;
        } catch(error) { onError?.(error instanceof Error?error.message:'Standalone editor generation failed'); return false; }
        finally { setGeneratingStandalone(false); setGenerationStage(''); }
    }
    const deploymentLabel = deploymentTarget === 'DOCKER' ? 'Docker container' : deploymentTarget === 'KUBERNETES' ? 'Kubernetes deployment' : 'Standalone ZIP';
    const exportSteps = result === null ? [] : [
        { id: 'runtime', title: 'Verify runtime', description: 'Confirm that the generated editor has a compiled executable plan.', checks: [
            { id: 'compiled', label: 'Editor runtime is compiled', complete: true, detail: result.cacheHit ? 'Verified cached compilation' : 'Fresh compilation' },
            { id: 'representations', label: 'At least one representation is available', complete: representations.length > 0, detail: `${representations.length} representation(s)` }
        ], content: <section className="editor-runtime-export-guide-summary"><strong>{result.plan.name}</strong><span>{Object.keys(result.plan.formsById).length} form(s) · {representations.length} representation(s)</span><p>The exported editor uses this immutable compilation plan. It does not include HYDRA's design screens.</p></section> },
        { id: 'target', title: 'Choose deployment', description: 'Select how the generated model editor will be delivered.', checks: [
            { id: 'target-selected', label: 'Deployment target selected', complete: ['SOURCE', 'DOCKER', 'KUBERNETES'].includes(deploymentTarget), detail: deploymentLabel }
        ], content: <div className="editor-runtime-deployment-cards">
            <label className={deploymentTarget === 'SOURCE' ? 'selected' : ''}><input checked={deploymentTarget === 'SOURCE'} name="guided-deployment-target" type="radio" onChange={() => setDeploymentTarget('SOURCE')} /><span><strong>Standalone ZIP</strong><small>Portable source/runtime package. Start it locally with the included launcher.</small></span></label>
            <label className={deploymentTarget === 'DOCKER' ? 'selected' : ''}><input checked={deploymentTarget === 'DOCKER'} name="guided-deployment-target" type="radio" onChange={() => setDeploymentTarget('DOCKER')} /><span><strong>Docker container</strong><small>Includes Dockerfile, Compose and start scripts. Models are persisted in a host volume.</small></span></label>
            <label className={deploymentTarget === 'KUBERNETES' ? 'selected' : ''}><input checked={deploymentTarget === 'KUBERNETES'} name="guided-deployment-target" type="radio" onChange={() => setDeploymentTarget('KUBERNETES')} /><span><strong>Kubernetes deployment</strong><small>Includes the Docker image definition plus Deployment, Service, PVC and health probes.</small></span></label>
        </div> },
        { id: 'capabilities', title: 'Portable capabilities', description: 'Review which HYDRA layers are requested for the exported editor.', checks: [
            { id: 'canonical', label: 'HYDRA capability archive enabled', complete: true, detail: 'Metamodel + editor definition + available runtime layers' },
            { id: 'models', label: 'Model persistence is enabled', complete: true, detail: deploymentTarget === 'SOURCE' ? 'Local runtime storage' : 'Persistent container volume' }
        ], content: <section className="editor-runtime-capability-guide"><header><div><h4>What travels with the editor?</h4><p>HYDRA asks the standalone generator to include all available HYDRA documents.</p></div><HydraTooltip label="About portable capabilities" title="Portable editor capabilities"><p>The package requests the metamodel, compiled editor, validations, quick fixes, operations/actions, events, EventFlow and gamification documents when they exist. Optional missing layers are reported as warnings instead of silently invented.</p></HydraTooltip></header><div><span>Metamodel</span><span>Representations and forms</span><span>Validations / quick fixes</span><span>Events / EventFlow</span><span>Gamification</span><span>Operations / actions</span></div></section> },
        { id: 'review', title: 'Review deployment', description: 'Confirm the target and expected startup model before generating files.', checks: [
            { id: 'review-ready', label: 'Deployment package is ready to generate', complete: !generatingStandalone, detail: generatingStandalone ? 'Generation in progress' : deploymentLabel },
            { id: 'k8s-image', label: deploymentTarget === 'KUBERNETES' ? 'Kubernetes image workflow understood' : 'Local startup workflow available', complete: true, detail: deploymentTarget === 'KUBERNETES' ? 'Build/publish or load the generated image before kubectl apply' : deploymentTarget === 'DOCKER' ? 'Run start-docker.cmd / start-docker.sh' : 'Use the included standalone launcher' }
        ], content: <section className="editor-runtime-export-review"><strong>{deploymentLabel}</strong><p>{deploymentTarget === 'DOCKER' ? 'The generated start script builds and starts Docker Compose, waits for the service and opens the editor in the browser.' : deploymentTarget === 'KUBERNETES' ? 'The package contains Kubernetes manifests and persistent storage. The image must be available to the target cluster before deployment.' : 'The standalone package can run without the HYDRA authoring application.'}</p></section> }
    ];
    return <section className="editor-runtime-preview">
        <header className="editor-runtime-preview-header">
            <div>
                <h3>Compiled editor runtime</h3>
                <p>Immutable indexes used by generated model editors. Compilation never changes the source definition.</p>
            </div>
            <div className="editor-runtime-header-actions"><HydraButton type="button" disabled={compiling} onClick={onCompile}>{compiling ? 'Compiling…' : result === null ? 'Compile runtime' : 'Recompile runtime'}</HydraButton>{result !== null && <HydraCreationModeSwitch mode={exportMode} onChange={setExportMode} guidedLabel="Guided deployment" advancedLabel="Direct export" />}{result !== null && exportMode === 'ADVANCED' && <><label className="editor-runtime-export-target"><span>Export as</span><HydraSelect ariaLabel="Standalone editor export format" disabled={generatingStandalone} value={deploymentTarget} options={[{value:'SOURCE',label:'Standalone ZIP',description:'Generate the standalone editor source package.'},{value:'DOCKER',label:'Docker container',description:'Generate files for a containerized standalone editor.'},{value:'KUBERNETES',label:'Kubernetes deployment',description:'Generate deployment files for Kubernetes.'}]} onChange={value => setDeploymentTarget(value as 'SOURCE' | 'DOCKER' | 'KUBERNETES')} /></label><HydraButton type="button" disabled={generatingStandalone} onClick={() => void downloadStandalone()}>{generatingStandalone ? 'Generating…' : 'Export editor'}</HydraButton></>}</div>
        </header>
        <HydraOperationProgress active={generatingStandalone} title="Generating standalone editor" detail={generationStage === 'layers' ? 'Collecting metamodel, representations and available runtime layers.' : generationStage === 'generate' ? 'Generating the independent editor and selected deployment assets.' : 'Preparing the verified archive for download.'} currentStageId={generationStage} stages={[{id:'layers',label:'Collect portable capabilities'},{id:'generate',label:'Generate editor application'},{id:'download',label:'Prepare download'}]} />
        {result === null && <div className="hydra-empty-state"><strong>No executable plan compiled</strong><span>Save the editor definition and compile it to inspect representations, palette tools, mappings and generated forms.</span></div>}
        {result !== null && <>
            {exportMode === 'GUIDED' && <div className="editor-runtime-guided-export"><HydraGuidedWorkflow title="Package the generated editor step by step" description="Verify the compiled runtime, choose a deployment target and confirm each required step before generation. Direct export remains available as an alternative." steps={exportSteps} currentStepId={exportStep} onStepChange={setExportStep} onFinish={downloadStandalone} finishLabel={generatingStandalone ? 'Generating…' : `Generate ${deploymentLabel}`} /></div>}
            <div className="editor-runtime-metadata">
                <span><strong>{result.plan.name}</strong></span>
                <span>{representations.length} representation(s)</span>
                <span>{Object.keys(result.plan.formsById).length} generated form(s)</span>
                <span>{result.cacheHit ? 'Loaded from cache' : 'Fresh compilation'}</span>
                <span>Compiled {new Date(result.plan.compiledAt).toLocaleString()}</span>
            </div>
            <nav className="editor-runtime-representation-tabs" aria-label="Compiled representations">
                {representations.map(representation => <HydraButton className={active?.id === representation.id ? 'active' : ''} type="button" key={representation.id} onClick={() => setSelectedRepresentationId(representation.id)}>{representation.name}<small>{representation.kind}</small></HydraButton>)}
            </nav>
            {active !== null && <RuntimeRepresentation representation={active} metamodel={metamodel} />}
            <EditorRuntimeSession result={result} metamodel={metamodel} onError={onError} onMessage={onMessage} />
        </>}
    </section>;
}

function RuntimeRepresentation({ representation, metamodel }: { representation: ExecutableRepresentationPlan; metamodel: MetaModel }) {
    const nodeMappings = Object.values(representation.nodeMappingsByClassId).flat();
    const edgeMappings = [...Object.values(representation.edgeMappingsByRelationId).flat(), ...Object.values(representation.edgeMappingsBySemanticClassId ?? {}).flat()];
    return <div className="editor-runtime-grid">
        <aside className="editor-runtime-palette-preview">
            <header><strong>{representation.name}</strong><small>{representation.kind} · {representation.editable ? 'Editable' : 'Read only'}</small></header>
            {representation.paletteGroups.map(group => <section key={group.id}>
                <h4>{group.name}</h4>
                {group.tools.map(tool => <article key={tool.id}><strong>{tool.label}</strong><small>{tool.description || highLevelToolKind(tool.kind)}</small></article>)}
            </section>)}
            {representation.paletteGroups.length === 0 && <p>No tools are active in this representation.</p>}
        </aside>
        <main className="editor-runtime-canvas-preview">
            <header><strong>Representation mappings</strong><small>The compiled runtime resolves mappings without scanning the source document.</small></header>
            <div className="editor-runtime-node-grid">
                {nodeMappings.map(mapping => { const notation=resolveNodeNotationStyle(mapping.styles); return <article className={`with-shape-frame shape-${mapping.shape.toLowerCase()}${mapping.container === true ? ' container' : ''}`} key={mapping.id} style={{opacity:notation.opacity}}>
                    <EditorNodeShapeFrame shape={mapping.shape} notation={notation} className="editor-runtime-preview-node-shape"/>
                    {(mapping.imageAssetPath ?? '') !== '' && <img className="editor-runtime-node-image" src={editorVisualAssetUrl(mapping.imageAssetPath ?? '')} alt="" style={{ objectFit: imageFit(mapping.imageFit ?? 'CONTAIN') }} />}
                    <span>Model element</span>
                    <strong>{mapping.representationName}</strong>
                    <small>Represents {className(metamodel, mapping.targetClassId)} instances.</small>
                    {mapping.container === true && <em>Contains elements through {(mapping.containmentRelationIds ?? []).length} relation(s)</em>}
                </article>;})}
                {nodeMappings.length === 0 && <div className="hydra-empty-state"><strong>No node mappings</strong><span>This representation does not render model classes as cards.</span></div>}
            </div>
            {edgeMappings.length > 0 && <section className="editor-runtime-edge-list"><h4>Connections</h4>{edgeMappings.map(mapping => <article key={mapping.id}><strong>{mapping.semanticEdge != null ? edgeMappingDisplayName(metamodel, mapping) : relationName(metamodel, mapping.relationId)}</strong><span>{className(metamodel, mapping.sourceClassId)} → {className(metamodel, mapping.targetClassId)}</span><small>{highLevelLineType(mapping.lineType)}</small></article>)}</section>}
            {((representation.layers?.length ?? 0) > 0 || (representation.filters?.length ?? 0) > 0) && <section className="editor-runtime-edge-list"><h4>Layers and filters</h4>
                {(representation.layers ?? []).map(layer => <article key={layer.id}><strong>{layer.name}</strong><span>{layer.defaultLayer ? 'Default layer' : layer.activeByDefault ? 'Active when the view opens' : 'Optional layer preserved but inactive by default'}</span><small>{layer.nodeMappingIds.length + layer.edgeMappingIds.length} mapping(s) · {layer.toolIds.length} tool(s)</small></article>)}
                {(representation.filters ?? []).map(filter => <article key={filter.id}><strong>{filter.name}</strong><span>{filter.conditionExpressionId !== '' ? 'HYDRA filter condition' : 'External condition preserved for review; HYDRA does not execute it'}</span><small>{filter.mappingIds.length} mapping(s){filter.externalCondition === '' ? '' : ` · ${filter.externalCondition}`}</small></article>)}
            </section>}
        </main>
        <aside className="editor-runtime-index-preview">
            <h4>Runtime indexes</h4>
            <dl>
                <div><dt>Root classes</dt><dd>{representation.rootClassIds.map(id => className(metamodel, id)).join(', ') || 'Any compatible root'}</dd></div>
                <div><dt>Class indexes</dt><dd>{Object.keys(representation.nodeMappingsByClassId).length}</dd></div>
                <div><dt>Relation indexes</dt><dd>{Object.keys(representation.edgeMappingsByRelationId).length}</dd></div>
                <div><dt>Semantic edge indexes</dt><dd>{Object.keys(representation.edgeMappingsBySemanticClassId ?? {}).length}</dd></div>
                <div><dt>Active tools</dt><dd>{Object.keys(representation.toolsById).length}</dd></div>
                <div><dt>Visual layers</dt><dd>{representation.layers?.length ?? 0}</dd></div>
                <div><dt>External/review filters</dt><dd>{(representation.filters ?? []).filter(filter => filter.conditionExpressionId === '').length}</dd></div>
            </dl>
        </aside>
    </div>;
}

function className(metamodel: MetaModel, classId: string): string {
    return metamodel.classes.find(item => item.id === classId)?.name ?? 'Unknown class';
}

function relationName(metamodel: MetaModel, relationId: string): string {
    return metamodel.relations.find(item => item.id === relationId)?.name || 'Unnamed relation';
}

function highLevelToolKind(kind: string): string {
    return ({ CREATE_NODE: 'Creates a model element', CREATE_EDGE: 'Creates a relationship', INVOKE_OPERATION: 'Runs a model operation', RUN_ACTION: 'Runs a configured action', VALIDATE: 'Validates the model' } as Record<string, string>)[kind] ?? 'Editor tool';
}

function highLevelLineType(lineType: string): string {
    return ({ BEZIER: 'Curved line', STRAIGHT: 'Straight line', STEP: 'Stepped line', SMOOTH_STEP: 'Smooth stepped line', ORTHOGONAL: 'Orthogonal route' } as Record<string, string>)[lineType] ?? 'Connection';
}

function imageFit(value: string): 'contain' | 'cover' | 'fill' | 'none' { return value === 'COVER' ? 'cover' : value === 'FILL' ? 'fill' : value === 'NONE' ? 'none' : 'contain'; }
