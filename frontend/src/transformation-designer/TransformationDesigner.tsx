import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraDiagnosticList, HydraFormField, HydraGuidedWorkflow, HydraSelect, HydraTooltip, type HydraCreationMode, type HydraGuidedStep } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { useHydraDialogs } from '../hooks/useHydraDialogs';
import { listCanonicalMetamodels, loadMetaModel } from '../metamodel-designer/api';
import type { CanonicalMetamodelSummary, MetaModel } from '../metamodel-designer/types';
import { listGrammars } from '../grammar-designer/api';
import type { GrammarSummary } from '../grammar-designer/types';
import { checkTransformation, createTransformation, deleteTransformation, listTransformations, loadTransformation, replaceTransformation } from './api';
import { TransformationMappingWorkspace } from './TransformationMappingWorkspace';
import { TransformationGenerationWorkspace } from './TransformationGenerationWorkspace';
import { TransformationTextToModelWorkspace } from './TransformationTextToModelWorkspace';
import { TransformationPreviewPanel } from './TransformationPreviewPanel';
import { TransformationExportPanel } from './TransformationExportPanel';
import { TransformationQuickFixPanel } from './TransformationQuickFixPanel';
import { TransformationTestingPanel } from './TransformationTestingPanel';
import { createEmptyTransformation, withEndpointMetamodel, withTransformationDirectionality, withTransformationKind } from './transformationDesignerModel';
import type { TransformationCheckReport, TransformationDefinitionDocument, TransformationDirectionality, TransformationKind, TransformationSummary } from './types';
import './transformation-designer.css';

export function TransformationDesigner() {
    const { t } = useHydraLanguage(); const [creationMode, setCreationMode] = useState<HydraCreationMode>('GUIDED'); const [guidedStep, setGuidedStep] = useState('scope');
    const [document, setDocument] = useState<TransformationDefinitionDocument>(() => createEmptyTransformation()); const [summaries, setSummaries] = useState<TransformationSummary[]>([]); const [metamodels, setMetamodels] = useState<CanonicalMetamodelSummary[]>([]); const [grammars, setGrammars] = useState<GrammarSummary[]>([]);
    const [sourceModel, setSourceModel] = useState<MetaModel | null>(null); const [targetModel, setTargetModel] = useState<MetaModel | null>(null); const [selectedRuleId, setSelectedRuleId] = useState(''); const [etag, setEtag] = useState(''); const [dirty, setDirty] = useState(false); const [busy, setBusy] = useState(false); const [report, setReport] = useState<TransformationCheckReport | null>(null); const [message, setMessage] = useState('');
    const endpointSerial = useRef(0); const { requestDecision, dialogs } = useHydraDialogs(); const sourceId = document.sources[0]?.metamodelId ?? ''; const targetId = document.targets[0]?.metamodelId ?? '';
    const errorCount = report?.diagnostics.filter(item => item.severity === 'ERROR').length ?? 0; const warningCount = report?.diagnostics.filter(item => item.severity === 'WARNING').length ?? 0;
    const sourceOptions = useMemo(() => [{ value:'', label:t('Select source metamodel'), description:t('Choose the semantic model read by the transformation.') }, ...metamodels.map(item => ({ value:item.id, label:item.name || item.id, description:item.id }))], [metamodels, t]);
    const targetOptions = useMemo(() => [{ value:'', label:t('Select target metamodel'), description:t('Choose the semantic model created by the transformation.') }, ...metamodels.map(item => ({ value:item.id, label:item.name || item.id, description:item.id }))], [metamodels, t]);

    useEffect(() => { let active = true; void Promise.all([listCanonicalMetamodels(), listTransformations(), listGrammars()]).then(([models, existing, grammarItems]) => { if (!active) return; setMetamodels(models); setSummaries(existing); setGrammars(grammarItems); }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : String(error)); }); return () => { active = false; }; }, []);
    useEffect(() => { const serial = ++endpointSerial.current; void loadEndpointModels(document.kind==='T2M'?'':sourceId, document.kind==='M2M'?targetId:'').then(([source, target]) => { if (serial !== endpointSerial.current) return; setSourceModel(source); setTargetModel(target); }).catch(error => { if (serial !== endpointSerial.current) return; setSourceModel(null); setTargetModel(null); setMessage(error instanceof Error ? error.message : String(error)); }); }, [document.kind, sourceId, targetId]);

    function mutate(next: TransformationDefinitionDocument): void { setDocument(next); setDirty(true); setReport(null); setMessage(''); if (selectedRuleId !== '' && !next.rules.some(item => item.id === selectedRuleId)) setSelectedRuleId(next.rules[0]?.id ?? ''); }
    function newTransformation(): void { setDocument(createEmptyTransformation()); setEtag(''); setDirty(false); setReport(null); setSelectedRuleId(''); setGuidedStep('scope'); setMessage(t('New transformation ready.')); }
    async function openTransformation(id: string): Promise<void> { if (id === '') { newTransformation(); return; } setBusy(true); try { const loaded = await loadTransformation(id); setDocument(loaded.transformation); setEtag(loaded.etag); setDirty(false); setReport(loaded.checkReport); setSelectedRuleId(loaded.transformation.rules[0]?.id ?? ''); setGuidedStep('scope'); const changedMetamodel = loaded.checkReport.diagnostics.some(item => item.code === 'ENDPOINT_METAMODEL_CHANGED'); setMessage(t(changedMetamodel ? 'Saved transformation loaded. Review mappings because a linked metamodel changed.' : 'Saved transformation loaded.')); } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); } finally { setBusy(false); } }
    async function refreshSummaries(): Promise<void> { setSummaries(await listTransformations()); }
    async function check(): Promise<TransformationCheckReport | null> { setBusy(true); try { const next = await checkTransformation(document); setReport(next); const errors = next.diagnostics.filter(item => item.severity === 'ERROR').length; const warnings = next.diagnostics.filter(item => item.severity === 'WARNING').length; setMessage(errors === 0 ? t(warnings === 0 ? 'Transformation is ready.' : 'Transformation is valid with warnings.') : t('Resolve the blocking transformation diagnostics before saving.')); return next; } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); return null; } finally { setBusy(false); } }
    async function save(): Promise<boolean> { const checked = await check(); if (checked === null || checked.diagnostics.some(item => item.severity === 'ERROR')) return false; setBusy(true); try { const result = etag === '' ? await createTransformation(document) : await replaceTransformation(document, etag); setDocument(result.transformation); setEtag(result.etag); setReport(result.checkReport); setDirty(false); await refreshSummaries(); setMessage(t('Transformation saved.')); return true; } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); return false; } finally { setBusy(false); } }
    async function remove(): Promise<void> { if (etag === '') return; const confirmed = await requestDecision({ title:t('Delete transformation'), description:t('Delete this saved transformation definition? This action does not delete either metamodel.'), confirmLabel:t('Delete transformation'), cancelLabel:t('Cancel'), danger:true }); if (!confirmed) return; setBusy(true); try { await deleteTransformation(document.id, etag); await refreshSummaries(); newTransformation(); setMessage(t('Transformation deleted.')); } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); } finally { setBusy(false); } }

    const scopeFields = <TransformationScope document={document} sourceId={sourceId} targetId={targetId} sourceModel={sourceModel} targetModel={targetModel} sourceOptions={sourceOptions} targetOptions={targetOptions} onChange={mutate} />;
    const mappingWorkspace = <TransformationMappingWorkspace document={document} source={sourceModel} target={targetModel} selectedRuleId={selectedRuleId} onSelectedRuleIdChange={setSelectedRuleId} onChange={mutate} compact={creationMode === 'GUIDED'} />;
    const workspace = document.kind === 'M2M'
        ? mappingWorkspace
        : document.kind === 'M2T'
            ? <TransformationGenerationWorkspace document={document} source={sourceModel} onChange={mutate}/>
            : <TransformationTextToModelWorkspace document={document} grammars={grammars} transformations={summaries} onChange={mutate}/>;
    const diagnosticItems = (report?.diagnostics ?? []).map(item => ({ severity:item.severity, code:item.code, message:item.title || item.message, path:item.path, elementId:item.bindingId && item.ruleId ? `rule ${item.ruleId} · binding ${item.bindingId}` : item.bindingId ? `binding ${item.bindingId}` : item.ruleId ? `rule ${item.ruleId}` : undefined, reason:item.message, solution:item.solution, source:'Transformation checker' }));
    const review = <><section className="transformation-review"><div className="transformation-review-metrics"><div><span>{t(document.kind==='M2T'?'Generation rules':'Rules')}</span><strong>{document.kind==='M2T'?(document.generationRules?.length??0):document.rules.length}</strong></div><div><span>{t('Errors')}</span><strong>{errorCount}</strong></div><div><span>{t('Warnings')}</span><strong>{warningCount}</strong></div></div><HydraDiagnosticList diagnostics={diagnosticItems} context="Transformation designer" title="Transformation diagnostics" emptyText="Run Check to verify the current transformation definition." /></section><TransformationQuickFixPanel document={document} report={report} onApply={mutate}/></>;
    const testing = <TransformationTestingPanel document={document} persisted={etag!==''&&!dirty}/>;
    const steps = guidedSteps(document, sourceModel, targetModel, report, errorCount, workspace, scopeFields, review, testing, check, t);

    return <main className="transformation-designer">
        <DesignerCommandBar
            title={t('Model transformation designer')}
            description={t('Define M2M, M2T and T2M transformations over the same neutral HYDRA contract.')}
            context={<label className="transformation-saved-selector">
                <span>{t('Saved transformations')}</span>
                <select disabled={busy} value={summaries.some(item => item.id === document.id) ? document.id : ''} onChange={event => void openTransformation(event.target.value)}>
                    <option value="">{t('New transformation')}</option>
                    {summaries.map(item => <option key={item.id} value={item.id}>{item.name} · {item.kind}{item.directionality === 'BIDIRECTIONAL' ? ' · ↔' : ''}</option>)}
                </select>
            </label>}
            status={<span className={`transformation-save-state${dirty ? ' dirty' : ''}`}>{t(dirty ? 'Unsaved changes' : etag === '' ? 'Not saved yet' : 'Saved')}</span>}
            actions={<>
                <HydraButton disabled={busy} type="button" onClick={newTransformation}>{t('New transformation')}</HydraButton>
                <DesignerCommandGroup label={t('Quality')}>
                    <HydraButton disabled={busy} type="button" onClick={() => void check()}>{t('Check transformation')}</HydraButton>
                </DesignerCommandGroup>
                <DesignerCommandGroup label={t('Changes')}>
                    <HydraButton disabled={busy || (!dirty && etag !== '')} type="button" onClick={() => void save()}>{t('Save transformation')}</HydraButton>
                    {etag !== '' && <HydraButton className="hydra-danger-button" disabled={busy} type="button" onClick={() => void remove()}>{t('Delete transformation')}</HydraButton>}
                </DesignerCommandGroup>
            </>}
            viewsLabel={t('Creation mode')}
            viewsSummary={creationMode === 'GUIDED' ? t('Guided') : t('Advanced / manual')}
            views={<HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Advanced / manual" />}
        />
        {message !== '' && <div className="transformation-runtime-message" role="status" data-hydra-runtime-message="true" data-hydra-message-context="Transformation designer">{t(message)}</div>}
        {creationMode === 'GUIDED'
            ? <HydraGuidedWorkflow
                title="Guided transformation"
                description="Configure scope, rules or parser settings, verify the same canonical definition and preview it without hidden conversions between modes."
                steps={steps}
                currentStepId={guidedStep}
                onStepChange={setGuidedStep}
                finishLabel="Save transformation"
                onFinish={save}
                outcome="A portable canonical transformation definition with explicit diagnostics and preview."
                recommendation="Start with the simplest direct semantics and add conditions or adaptations only when the source data requires them."
            />
            : <>
                <section className="transformation-advanced-scope">{scopeFields}</section>
                {workspace}
                {report !== null && review}
                <TransformationPreviewPanel document={document}/>
                <TransformationExportPanel document={document}/>
                {testing}
            </>}
        {dialogs}
    </main>;
}

function TransformationScope({ document, sourceId, targetId, sourceModel, targetModel, sourceOptions, targetOptions, onChange }: {
    document: TransformationDefinitionDocument;
    sourceId: string;
    targetId: string;
    sourceModel: MetaModel | null;
    targetModel: MetaModel | null;
    sourceOptions: { value:string; label:string; description:string }[];
    targetOptions: { value:string; label:string; description:string }[];
    onChange: (document:TransformationDefinitionDocument) => void;
}) {
    const { t } = useHydraLanguage();
    const kinds:{ value:TransformationKind; label:string; description:string }[] = [
        { value:'M2M', label:'M2M', description:t('Transform model instances from one metamodel into another.') },
        { value:'M2T', label:'M2T', description:t('Generate text artifacts from model instances.') },
        { value:'T2M', label:'T2M', description:t('Parse text through a grammar and optionally project the semantic model.') },
    ];
    const directionalities:{ value:TransformationDirectionality; label:string; description:string }[] = [
        { value:'UNIDIRECTIONAL', label:t('Unidirectional'), description:t('Execute source → target only.') },
        { value:'BIDIRECTIONAL', label:t('Bidirectional'), description:t('Define paired forward and backward correspondences in one transformation.') },
    ];
    return <section className="transformation-scope-card">
        <div className="transformation-identity-grid">
            <HydraFormField label="Transformation name" targetId="transformation-name" required help="A readable name used in the catalogue, diagnostics and generated artifacts." width="wide">
                <input id="transformation-name" value={document.name} onChange={event => onChange({ ...document, name:event.target.value })}/>
            </HydraFormField>
            <HydraFormField label="Transformation type" targetId="transformation-kind" required help="M2M transforms models, M2T generates text and T2M parses text through a grammar." width="normal">
                <HydraSelect ariaLabel="Transformation type" value={document.kind} options={kinds} onChange={value => onChange(withTransformationKind(document, value as TransformationKind))}/>
            </HydraFormField>
            {document.kind === 'M2M' && <HydraFormField label="Directionality" targetId="transformation-directionality" required help="Bidirectional transformations keep forward and backward rules inside one canonical definition and pair them through stable correspondences." width="normal">
                <HydraSelect ariaLabel="Directionality" value={document.directionality} options={directionalities} onChange={value => onChange(withTransformationDirectionality(document, value as TransformationDirectionality))}/>
            </HydraFormField>}
            {document.kind === 'M2M' && document.directionality === 'BIDIRECTIONAL' && <>
                <HydraFormField label="Concurrent changes" targetId="transformation-conflict-policy" required help={t('Defines what HYDRA must do when both sides have changed before synchronization. FAIL is the safest default.')} width="normal">
                    <HydraSelect ariaLabel="Concurrent changes" value={document.synchronizationPolicy.conflictPolicy} options={[
                        { value:'FAIL_ON_CONFLICT', label:t('Stop on conflict'), description:t('Require an explicit resolution when both sides changed.') },
                        { value:'FORWARD_WINS', label:t('Source side wins'), description:t('Propagate source-side state to the target side.') },
                        { value:'REVERSE_WINS', label:t('Target side wins'), description:t('Propagate target-side state back to the source side.') },
                    ]} onChange={value => onChange({ ...document, synchronizationPolicy:{ ...document.synchronizationPolicy, conflictPolicy:value as typeof document.synchronizationPolicy.conflictPolicy } })}/>
                </HydraFormField>
                <div className="transformation-sync-policy-options">
                    <HydraCheckbox label={t('Propagate deletions')} description={t('When an element disappears from one side, remove its traced counterpart during incremental synchronization.')} checked={document.synchronizationPolicy.propagateDeletions} onChange={event=>onChange({...document,synchronizationPolicy:{...document.synchronizationPolicy,propagateDeletions:event.target.checked}})}/>
                    <HydraCheckbox label={t('Require previous trace for updates')} description={t('Reject incremental updates when HYDRA cannot prove the previous correspondence between both sides.')} checked={document.synchronizationPolicy.requireTraceForUpdates} onChange={event=>onChange({...document,synchronizationPolicy:{...document.synchronizationPolicy,requireTraceForUpdates:event.target.checked}})}/>
                </div>
            </>}
        </div>
        {document.kind !== 'T2M' && <div className={`transformation-endpoint-grid ${document.kind === 'M2T' ? 'single' : ''}`}>
            <HydraFormField label="Source metamodel" targetId="transformation-source" required help="The transformation reads instances that conform to this metamodel." width="full"><div className="transformation-field-help-row">{sourceModel!==null&&<HydraTooltip label={t('Help about the selected source metamodel')} title={`${t('Source metamodel')}: ${sourceModel.name}`}><MetamodelTransformationHelp model={sourceModel} role="source" /></HydraTooltip>}</div>
                <HydraSelect ariaLabel="Source metamodel" value={sourceId} options={sourceOptions} onChange={value => onChange(withEndpointMetamodel(document, 'SOURCE', value))}/>
            </HydraFormField>
            {document.kind === 'M2M' && <>
                <span className="transformation-direction" aria-hidden="true">{document.directionality === 'BIDIRECTIONAL' ? '↔' : '→'}</span>
                <HydraFormField label="Target metamodel" targetId="transformation-target" required help="The transformation creates instances that conform to this metamodel." width="full"><div className="transformation-field-help-row">{targetModel!==null&&<HydraTooltip label={t('Help about the selected target metamodel')} title={`${t('Target metamodel')}: ${targetModel.name}`}><MetamodelTransformationHelp model={targetModel} role="target" /></HydraTooltip>}</div>
                    <HydraSelect ariaLabel="Target metamodel" value={targetId} options={targetOptions} onChange={value => onChange(withEndpointMetamodel(document, 'TARGET', value))}/>
                </HydraFormField>
            </>}
        </div>}
        <div className="transformation-scope-note">
            <strong>{t('Neutral HYDRA transformation')}</strong>
            <span>{t('The canonical definition stays independent from ATL, ETL, Java, Xtend, Acceleo, EGL, Python and Go; adapters are checked separately before export.')}</span>
        </div>
    </section>;
}

function guidedSteps(
    document:TransformationDefinitionDocument, source:MetaModel|null, target:MetaModel|null, report:TransformationCheckReport|null,
    errorCount:number, workspace:ReactNode, scope:ReactNode, review:ReactNode,
    testing:ReactNode, check:()=>Promise<TransformationCheckReport|null>, t:(key:string)=>string,
):HydraGuidedStep[] {
    const authored = document.kind === 'M2M'
        ? document.rules.length > 0
        : document.kind === 'M2T'
            ? (document.generationRules?.length ?? 0) > 0
            : (document.textToModel?.grammarId ?? '') !== '';
    const scopeReady = document.kind === 'T2M'
        ? (document.textToModel?.grammarId ?? '') !== ''
        : source !== null && (document.kind === 'M2T' || target !== null);
    return [
        {
            id:'scope', title:'Scope', description:'Choose the transformation family and its semantic context.',
            checks:[
                { id:'name', label:'Transformation name is defined', complete:document.name.trim() !== '', detail:'The name identifies this transformation in HYDRA.' },
                { id:'context', label:'Transformation context is selected', complete:scopeReady, detail:document.kind === 'T2M' ? 'Choose the grammar in the next step.' : 'Select the required metamodels.' },
            ],
            content:scope,
        },
        {
            id:'authoring',
            title:document.kind === 'M2M' ? 'Correspondences' : document.kind === 'M2T' ? 'Generation rules' : 'Parser pipeline',
            description:document.kind === 'M2M' ? 'Create class, property and relation mappings.' : document.kind === 'M2T' ? 'Create safe generation templates.' : 'Configure grammar parsing and optional M2M projection.',
            checks:[{ id:'authored', label:'Transformation behavior is defined', complete:authored, detail:'Define at least one rule or parser configuration.' }],
            content:workspace,
        },
        {
            id:'review', title:'Review', description:'Check, preview and export the current canonical draft.',
            checks:[{ id:'quality', label:'No blocking diagnostics remain', complete:report !== null && errorCount === 0, detail:report === null ? 'Run Check to verify the current definition.' : `${errorCount} blocking error(s).` }],
            content:<><div className="transformation-review-actions"><HydraButton type="button" onClick={() => void check()}>{t('Check transformation')}</HydraButton></div>{review}<TransformationPreviewPanel document={document}/><TransformationExportPanel document={document}/>{testing}</>,
        },
    ];
}

function MetamodelTransformationHelp({model,role}:{model:MetaModel;role:'source'|'target'}) {
    const {t}=useHydraLanguage();
    const relationCount=model.relations.length;
    const attributeCount=model.classes.reduce((total,item)=>total+item.attributes.length,0);
    const classPreview=model.classes.slice(0,8).map(item=>`${item.name} (${item.attributes.length})`).join(', ');
    return <div className="transformation-dynamic-help"><p>{t(role==='source'?'HYDRA will read elements from this metamodel.':'HYDRA will create elements that must conform to this metamodel.')}</p><p><strong>{model.classes.length}</strong> {t('classes')} · <strong>{attributeCount}</strong> {t('properties')} · <strong>{relationCount}</strong> {t('relations')}.</p>{classPreview!==''&&<p>{t('Classes available for mapping')}: <strong>{classPreview}{model.classes.length>8?'…':''}</strong>.</p>}<p>{t(role==='source'?'Select source classes whose data you want to transform. Their properties and relations will become the candidates shown in mapping help.':'Required target properties and their cardinalities determine which mappings must be provided. HYDRA will compare them with the selected source class and suggest compatible candidates.')}</p></div>;
}

async function loadEndpointModels(sourceId:string,targetId:string):Promise<[MetaModel|null,MetaModel|null]>{const [source,target]=await Promise.all([sourceId===''?Promise.resolve(null):loadMetaModel(sourceId).then(result=>result.metamodel),targetId===''?Promise.resolve(null):loadMetaModel(targetId).then(result=>result.metamodel)]);return [source,target];}
export default TransformationDesigner;
