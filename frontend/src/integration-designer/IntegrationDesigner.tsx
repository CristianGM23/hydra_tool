import { HydraButton } from '../design-system/HydraButton';
import { artifactResolutionAdvice, integrationDiagnosticAdvice, integrationDiagnosticDisplayMessage } from './integrationDiagnosticAdvice';
import { useEffect, useRef, useState } from 'react';
import { DesignerCommandBar, DesignerCommandGroup } from '../designer-ui/DesignerCommandBar';
import { applyTechnologyIntegration, checkIntegrationProfile, compileTechnologyIntegration, createIntegrationProfile as persistIntegrationProfile, deleteIntegrationProfile, listIntegrationProfiles, listIntegrationTechnologies, listIntegrationTechnologyWorkflows, loadIntegrationProfile, previewTechnologyIntegration, replaceIntegrationProfile } from './api';
import { DesignerGuideButton, DesignerHelpedCheckbox, DesignerHelpedField, DesignerRequiredFieldsLegend } from '../designer-ui/DesignerFieldHelp';
import { useResizableDesignerSidebar } from '../designer-ui/useResizableDesignerSidebar';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraGuidedWorkflow, HydraOperationProgress, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { useHydraDialogs } from '../hooks/useHydraDialogs';
import { createIntegrationProfile, defaultBuildSystem, preferredDelivery, preferredStrategy } from './integrationProfileFactory';
import { findIntegrationProfileBlockingError } from './integrationProfileValidation';
import { generatedRootProblemMessage, toProjectRelativeGeneratedRoot, validateGeneratedRootPath } from './generatedRootPath';
import { IntegrationStructureTree } from './IntegrationStructureTree';
import { IntegrationRemoteStorage } from './IntegrationRemoteStorage';
import { IntegrationRecipeChooser } from './IntegrationRecipeChooser';
import { IntegrationTechnologyJourney } from './IntegrationTechnologyJourney';
import { recommendedIntegrationFeatures } from './integrationTechnologyRecommendations';
import { IntegrationMethodChooser } from './IntegrationMethodChooser';
import { integrationRecipe, type IntegrationRecipeId } from './integrationRecipes';
import { pickProjectPath, resolveProjectPath } from '../project-explorer/projectApi';
import { useHydraOverwriteConfirmation } from '../hooks/useHydraOverwriteConfirmation';
import type { IntegrationStructureSelection } from './IntegrationStructureTree';
import type {
    IntegrationArtifactKind,
    IntegrationArtifactSelection,
    IntegrationCheckReport,
    IntegrationElementMapping,
    IntegrationFeature,
    IntegrationLocatorStrategy,
    IntegrationMappingKind,
    IntegrationProfileModel,
    IntegrationProfileSummary,
    IntegrationTechnology,
    IntegrationTechnologyDescriptor,
    TechnologyInjectionPreview,
    TechnologyWorkflowDescriptor
} from './types';
import './integration-designer.css';
interface Props {
    metamodelId: string;
    persisted: boolean;
    onError: (message: string) => void;
    onMessage: (message: string) => void;
}
import { ARTIFACT_KINDS, BUILD_SYSTEMS, FEATURES, INTEGRATION_OPTION_DESCRIPTIONS, MAPPING_KINDS, artifactCategory, buildSystemCategory, deliveryCategory, injectionCategory, integrationDynamicDescription, integrationRelatedSectionLabel, integrationResolutionStatusLabel, locatorCategory, mappingCategory, newMapping, orderedDeliveryModes, orderedInjectionStrategies, readableIntegrationValue, technologyCategory } from './integrationDesignerOptions';
const FEATURE_INFO: Record<IntegrationFeature, { title: string; description: string }> = {
    VALIDATION_RUNTIME: { title: 'Runtime validation', description: 'Run or display HYDRA validation rules in the target technology.' },
    QUICK_FIX_RUNTIME: { title: 'Runtime quick fixes', description: 'Expose repair actions for validation problems in the target technology.' },
    OPERATION_RUNTIME: { title: 'Runtime operations', description: 'Execute model operations from the integrated technology.' },
    EVENT_PUBLICATION: { title: 'Publish events', description: 'Let the host technology publish events into HYDRA-compatible flows.' },
    EVENT_CONSUMPTION: { title: 'Consume events', description: 'Let the integration react to events produced by HYDRA or other adapters.' },
    EVENT_FLOW_RUNTIME: { title: 'Run EventFlow', description: 'Execute event flows from the integrated runtime.' },
    GAMIFICATION_RUNTIME: { title: 'Gamification runtime', description: 'Apply gamification evidence and rules in the target runtime.' },
    GAMIFICATION_UI: { title: 'Gamification UI', description: 'Add visible gamification components to the host user experience.' },
    EDITOR_UI_CONTRIBUTION: { title: 'Editor UI contribution', description: 'Add panels, actions or visual extensions to the host editor.' },
    NOTATION_CONTRIBUTION: { title: 'Notation contribution', description: 'Install or extend visual notation in the target editor.' },
    PALETTE_CONTRIBUTION: { title: 'Palette contribution', description: 'Add tools or entries to the target editor palette.' },
    VALIDATION_PROJECTION: { title: 'Validation projection', description: 'Export validation definitions for the target technology to consume.' },
    EVENT_DEFINITION_PROJECTION: { title: 'Event projection', description: 'Export the event catalog without requiring runtime execution.' },
    EVENT_FLOW_PROJECTION: { title: 'EventFlow projection', description: 'Export EventFlow definitions as reusable target artifacts.' },
    GAMIFICATION_PROJECTION: { title: 'Gamification projection', description: 'Export gamification definitions for other tools or runtimes.' },
    CODE_GENERATION: { title: 'Model-to-code generation', description: 'Generate source code or portable model projections from models edited with the exported editor.' },
    PROJECT_IMPORT: { title: 'Project import', description: 'Bring projects or artifacts from the target technology into HYDRA.' },
    PROJECT_EXPORT: { title: 'Project export', description: 'Publish HYDRA projects or artifacts to the target technology.' }
};
const INPUT_INFO: Record<IntegrationArtifactKind, { title: string; description: string }> = {
    METAMODEL: { title: 'Model structure', description: 'Classes, attributes, relations and operations that define the language.' },
    GRAMMAR: { title: 'Grammar', description: 'Grammar source plus the neutral appearance and contextual-help configuration of its textual editor.' },
    TEXTUAL_EDITOR_DEFINITION: { title: 'Textual editor', description: 'Runtime definition that links the grammar to validation, quick fixes, events, EventFlow and gamification.' },
    TEXTUAL_SYNTAX_STYLE: { title: 'Reusable textual style', description: 'Optional shared syntax-style document used by textual editors.' },
    EXPRESSIONS: { title: 'Reusable conditions', description: 'Shared expressions used by validations and repairs.' },
    VALIDATIONS: { title: 'Validation rules', description: 'Rules that detect problems in models.' },
    ACTIONS: { title: 'Quick fixes and actions', description: 'Repairs and commands that can be offered to users.' },
    OPERATION_BEHAVIORS: { title: 'Operation behavior', description: 'Executable behavior associated with model operations.' },
    EVENT_DEFINITIONS: { title: 'Event catalogue', description: 'Event types that the integration can publish or consume.' },
    EVENT_BINDINGS: { title: 'Event bindings', description: 'Rules that derive or connect events.' },
    EVENT_FLOWS: { title: 'Event flows', description: 'Event-driven processes that the target can execute or project.' },
    GAMIFICATION_DEFINITIONS: { title: 'Gamification rules', description: 'Points, achievements, levels, challenges and rewards.' },
    GAMIFICATION_BINDINGS: { title: 'Gamification evidence', description: 'Links events and model activity to gamification rules.' },
    EDITOR_DEFINITION: { title: 'Editor definition', description: 'Forms, panels and editor behavior configured in HYDRA.' },
    CODE_GENERATION: { title: 'Code generation', description: 'Allowed targets, default target, namespace and traceability for model generation.' },
    NOTATION: { title: 'Visual notation', description: 'Shapes, styles and visual representation rules.' }
};
const PORTABLE_RUNTIME_FEATURES: readonly IntegrationFeature[] = [
    'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'EVENT_PUBLICATION', 'EVENT_CONSUMPTION',
    'EVENT_FLOW_RUNTIME', 'GAMIFICATION_RUNTIME'
];

function grammarFirstScope(semanticScopeId:string):boolean{return semanticScopeId.startsWith('grammar.');}
function semanticRootKind(semanticScopeId:string):IntegrationArtifactKind{return grammarFirstScope(semanticScopeId)?'GRAMMAR':'METAMODEL';}
function defaultInputDocumentId(semanticScopeId: string, kind: IntegrationArtifactKind): string {
    if(kind==='GRAMMAR'&&grammarFirstScope(semanticScopeId))return semanticScopeId.slice('grammar.'.length);
    if(kind==='METAMODEL'&&!grammarFirstScope(semanticScopeId))return semanticScopeId;
    const suffix: Record<IntegrationArtifactKind, string> = {
        METAMODEL: '', GRAMMAR:'-grammar', TEXTUAL_EDITOR_DEFINITION:'-textual-editor', TEXTUAL_SYNTAX_STYLE:'-textual-style', EXPRESSIONS: '-expressions', VALIDATIONS: '-validations', ACTIONS: '-actions',
        OPERATION_BEHAVIORS: '-operation-behaviors', EVENT_DEFINITIONS: '-events', EVENT_BINDINGS: '-event-bindings',
        EVENT_FLOWS: '-event-flows', GAMIFICATION_DEFINITIONS: '-gamification', GAMIFICATION_BINDINGS: '-gamification-bindings',
        EDITOR_DEFINITION: '-editor', CODE_GENERATION: '-editor', NOTATION: '-notation'
    };
    return `${semanticScopeId}${suffix[kind]}`;
}
function recommendedInputs(features: readonly IntegrationFeature[], semanticScopeId:string): Set<IntegrationArtifactKind> {
    const result = new Set<IntegrationArtifactKind>([semanticRootKind(semanticScopeId)]);
    const add = (...kinds: IntegrationArtifactKind[]) => kinds.forEach(kind => result.add(kind));
    for (const feature of features) {
        if (feature === 'VALIDATION_RUNTIME' || feature === 'VALIDATION_PROJECTION') add('VALIDATIONS', 'EXPRESSIONS');
        if (feature === 'QUICK_FIX_RUNTIME') add('ACTIONS', 'EXPRESSIONS');
        if (feature === 'OPERATION_RUNTIME') add('OPERATION_BEHAVIORS');
        if (feature === 'EVENT_PUBLICATION' || feature === 'EVENT_CONSUMPTION' || feature === 'EVENT_DEFINITION_PROJECTION') add('EVENT_DEFINITIONS', 'EVENT_BINDINGS');
        if (feature === 'EVENT_FLOW_RUNTIME' || feature === 'EVENT_FLOW_PROJECTION') add('EVENT_DEFINITIONS', 'EVENT_BINDINGS', 'EVENT_FLOWS');
        if (feature === 'GAMIFICATION_RUNTIME' || feature === 'GAMIFICATION_UI' || feature === 'GAMIFICATION_PROJECTION') add('GAMIFICATION_DEFINITIONS');
        if (feature === 'GAMIFICATION_RUNTIME') add('GAMIFICATION_BINDINGS', 'EVENT_DEFINITIONS');
        if (feature === 'EDITOR_UI_CONTRIBUTION') add('EDITOR_DEFINITION','GRAMMAR','TEXTUAL_EDITOR_DEFINITION','TEXTUAL_SYNTAX_STYLE');
        if (feature === 'CODE_GENERATION') add('EDITOR_DEFINITION', 'GRAMMAR', 'TEXTUAL_EDITOR_DEFINITION', 'TEXTUAL_SYNTAX_STYLE', 'CODE_GENERATION');
        if (feature === 'NOTATION_CONTRIBUTION' || feature === 'PALETTE_CONTRIBUTION') add('EDITOR_DEFINITION', 'NOTATION');
        if (feature === 'PROJECT_EXPORT') add('GRAMMAR','TEXTUAL_EDITOR_DEFINITION','TEXTUAL_SYNTAX_STYLE');
    }
    return result;
}
export function IntegrationDesigner(props: Props) {
    return <IntegrationDesignerSession key={`${props.metamodelId}:${props.persisted}`} {...props} />;
}

function IntegrationDesignerSession({ metamodelId, persisted, onError, onMessage }: Props) {
    const { t, language } = useHydraLanguage();
    const { requestDecision, dialogs } = useHydraDialogs();
    const sidebar = useResizableDesignerSidebar({ storageKey: 'hydra:integration-designer:sidebar-width', ariaLabel: 'Resize integration profiles palette', initialWidth: 340 });
    const [descriptors, setDescriptors] = useState<IntegrationTechnologyDescriptor[]>([]);
    const [workflows, setWorkflows] = useState<TechnologyWorkflowDescriptor[]>([]);
    const [summaries, setSummaries] = useState<IntegrationProfileSummary[]>([]);
    const [model, setModel] = useState<IntegrationProfileModel | null>(null);
    const [etag, setEtag] = useState('');
    const { confirmOverwrite, overwriteDialogs } = useHydraOverwriteConfirmation({
        current: model === null ? [] : [{ layer: 'Integration profile', entityKind: 'INTEGRATION_PROFILE', stableId: model.id, uniqueName: model.name, displayLabel: model.name || model.id }],
        persisted: summaries.map(item => ({ layer: 'Integration profile', entityKind: 'INTEGRATION_PROFILE', stableId: item.id, uniqueName: item.name, displayLabel: item.name || item.id }))
    });
    const [report, setReport] = useState<IntegrationCheckReport | null>(null);
    const [dirty, setDirty] = useState(false);
    const [busy, setBusy] = useState(false);
    const [operation, setOperation] = useState<{title:string;detail:string;stage:string}|null>(null);
    const [query, setQuery] = useState('');
    const [injectionTarget, setInjectionTarget] = useState('generated-target');
    const [injectionPreview, setInjectionPreview] = useState<TechnologyInjectionPreview | null>(null);
    const [view, setView] = useState<'GUIDED' | 'FORM' | 'STRUCTURE'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('identity');
    const [structureTabOpen, setStructureTabOpen] = useState(true);
    const [structureCollapsed, setStructureCollapsed] = useState(false);
    const [structurePropertiesOpen, setStructurePropertiesOpen] = useState(true);
    const [structurePropertiesCollapsed, setStructurePropertiesCollapsed] = useState(false);
    const [structureFilter, setStructureFilter] = useState('');
    const [structureSelection, setStructureSelection] = useState<IntegrationStructureSelection>(null);
    const onErrorRef = useRef(onError);
    const onMessageRef = useRef(onMessage);
    useEffect(() => { onErrorRef.current = onError; onMessageRef.current = onMessage; }, [onError, onMessage]);
    useEffect(() => {
        let active = true;
    if(!persisted) return () => { active = false; };
        void (async () => {
            const [technologyItems, workflowItems, profileItems] = await Promise.all([
                listIntegrationTechnologies(metamodelId),
                listIntegrationTechnologyWorkflows(metamodelId),
                listIntegrationProfiles(metamodelId)
            ]);
            if(!active) return;
            setDescriptors(technologyItems);
            setWorkflows(workflowItems);
            setSummaries(profileItems);
            if(profileItems[0] !== undefined) {
                const loaded = await loadIntegrationProfile(metamodelId, profileItems[0].id);
                if(!active) return;
                setModel(loaded.model);
                setEtag(loaded.etag);
                setInjectionTarget(loaded.model.target.projectId || 'generated-target');
            } else if(technologyItems[0] !== undefined) {
                const created = createIntegrationProfile(metamodelId, technologyItems[0]);
                setModel(created);
                setInjectionTarget(created.target.projectId || 'generated-target');
                setDirty(true);
            }
        })().catch(error => { if(active) onErrorRef.current(error instanceof Error ? error.message : 'Could not load integration profiles'); });
        return () => { active = false; };
    }, [metamodelId, persisted]);
    const descriptor = descriptors.find(item => item.technology === model?.target.technology) ?? null;
    const technologyWorkflow = workflows.find(item => item.technology === model?.target.technology) ?? null;
    const generationStage = technologyWorkflow?.stages.find(item => item.stage === 'GENERATE') ?? null;
    const previewStage = technologyWorkflow?.stages.find(item => item.stage === 'PREVIEW') ?? null;
    const applyStage = technologyWorkflow?.stages.find(item => item.stage === 'APPLY') ?? null;
    const canCompileTechnology = generationStage?.applicable === true && generationStage.action === 'COMPILE_ARCHIVE' && generationStage.support === 'FULL';
    const projectionGeneration = generationStage?.applicable === true && generationStage.action === 'EXPORT_PROJECTION' && generationStage.support !== 'UNSUPPORTED';
    const canPreviewInjection = previewStage?.applicable === true && previewStage.action === 'PREVIEW_CHANGES' && previewStage.support === 'FULL';
    const canApplyInjection = applyStage?.applicable === true && applyStage.action === 'APPLY_CHANGES' && applyStage.support === 'FULL';
    const hostChangesApplicable = previewStage?.applicable ?? true;
    const visibleSummaries = summaries.filter(item => `${item.name} ${item.id} ${item.technology}`.toLowerCase().includes(query.trim().toLowerCase()));
    function renderFeatureSelector() {
        return <div className="integration-feature-card-grid" id="integration-features" tabIndex={-1}>{FEATURES.map(feature => {
            const info = FEATURE_INFO[feature];
            return <label className={`integration-feature-card${model?.features.includes(feature) ? ' selected' : ''}`} key={feature}><input checked={model?.features.includes(feature) ?? false} type="checkbox" onChange={() => toggleFeature(feature)} /><div><strong>{info.title}</strong><small>{info.description}</small><span>{feature}</span></div></label>;
        })}</div>;
    }
    function overviewBadges() {
        return <div className="integration-overview-badges"><span>{descriptor?.displayName ?? 'Technology'}</span><span>{model?.features.length ?? 0} capabilities</span><span>{model?.artifacts.length ?? 0} input documents</span><span>{model?.mappings.length ?? 0} host mappings</span></div>;
    }
    function changeModel(mutator: (current: IntegrationProfileModel) => IntegrationProfileModel): void {
        setModel(current => current === null ? current : mutator(current));
        setDirty(true);
        setReport(null);
        setInjectionPreview(null);
    }
    function createNew(technology?: IntegrationTechnology): void {
        const selected = descriptors.find(item => item.technology === technology) ?? descriptors[0];
        if(selected === undefined) return;
        const created = createIntegrationProfile(metamodelId, selected);
        setModel(created);
        setInjectionTarget(created.target.projectId || 'generated-target');
        setInjectionPreview(null);
        setEtag('');
        setReport(null);
        setDirty(true);
    }
    async function selectProfile(id: string): Promise<void> {
        if(model?.id === id && etag !== '') return;
        try {
            setBusy(true);
            const loaded = await loadIntegrationProfile(metamodelId, id);
            setModel(loaded.model);
            setEtag(loaded.etag);
            setInjectionTarget(loaded.model.target.projectId || 'generated-target');
            setInjectionPreview(null);
            setReport(null);
            setDirty(false);
        } catch(error) { onError(error instanceof Error ? error.message : 'Could not load integration profile'); }
        finally { setBusy(false); }
    }
    function changeTechnology(technology: IntegrationTechnology): void {
        const next = descriptors.find(item => item.technology === technology);
        if(next === undefined) return;
        changeModel(current => ({
            ...current,
            target:{ ...current.target, technology, kind:next.targetKind, buildSystem:defaultBuildSystem(next) },
            deliveryMode:preferredDelivery(next),
            injectionPolicy:{ ...current.injectionPolicy, strategy:preferredStrategy(next), generatedRoot:technology === 'PLANTUML' ? 'docs/diagrams' : 'generated-integration' },
            features:technology === 'PLANTUML' ? ['EVENT_DEFINITION_PROJECTION','PROJECT_EXPORT'] : current.features.filter(feature => !feature.endsWith('_PROJECTION')),
            requiredCapabilities:[]
        }));
    }
    function toggleFeature(feature: IntegrationFeature): void {
        changeModel(current => ({ ...current, features:current.features.includes(feature) ? current.features.filter(item => item !== feature) : [...current.features, feature] }));
    }
    function addArtifact(kind: IntegrationArtifactKind = 'EVENT_DEFINITIONS', required = false): void {
        if (model?.artifacts.some(item => item.kind === kind)) {
            onMessage(t('That HYDRA input is already included.'));
            return;
        }
        changeModel(current => ({ ...current, artifacts:[...current.artifacts, { kind, documentId:defaultInputDocumentId(metamodelId, kind), required:kind === semanticRootKind(metamodelId) ? true : required }] }));
    }
    function toggleGuidedInput(kind: IntegrationArtifactKind): void {
        if (kind === semanticRootKind(metamodelId)) return;
        const selected = model?.artifacts.some(item => item.kind === kind) ?? false;
        if (selected) changeModel(current => ({ ...current, artifacts:current.artifacts.filter(item => item.kind !== kind) }));
        else addArtifact(kind, recommendedInputs(model?.features ?? [],metamodelId).has(kind));
    }
    function configureInputsAutomatically(): void {
        const recommended = recommendedInputs(model?.features ?? [],metamodelId);
        changeModel(current => {
            const byKind = new Map(current.artifacts.map(item => [item.kind, item] as const));
            if(grammarFirstScope(metamodelId))byKind.delete('METAMODEL');
            for (const kind of recommended) {
                const existing = byKind.get(kind);
                byKind.set(kind, existing === undefined
                    ? { kind, documentId:defaultInputDocumentId(metamodelId, kind), required:true }
                    : { ...existing, required:true });
            }
            return { ...current, artifacts:ARTIFACT_KINDS.flatMap(kind => byKind.has(kind) ? [byKind.get(kind)!] : []) };
        });
        onMessage(t('HYDRA inputs were adapted to the selected capabilities.'));
    }
    function applyGuidedModelingRecipe(technology: IntegrationRecipeId): void {
        const next = descriptors.find(item => item.technology === technology);
        const recipe = integrationRecipe(technology);
        if (next === undefined || recipe === undefined) return;
        if (!next.deliveryModes.includes(recipe.deliveryMode) || !next.injectionStrategies.includes(recipe.strategy)) {
            onError(t('The selected quick configuration is not supported by the current technology catalogue. Reload the integration catalogue and try again.'));
            return;
        }
        const moduleName = `${metamodelId}-${recipe.moduleSuffix}`;
        const features: IntegrationFeature[] = [...PORTABLE_RUNTIME_FEATURES, 'EDITOR_UI_CONTRIBUTION', 'NOTATION_CONTRIBUTION', 'PALETTE_CONTRIBUTION', 'PROJECT_EXPORT'];
        const required = recommendedInputs(features,metamodelId);
        changeModel(current => {
            const byKind = new Map(current.artifacts.map(item => [item.kind, item] as const));
            if(grammarFirstScope(metamodelId))byKind.delete('METAMODEL');
            for (const kind of required) {
                const existing = byKind.get(kind);
                byKind.set(kind, existing === undefined ? { kind, documentId:defaultInputDocumentId(metamodelId, kind), required:true } : { ...existing, required:true });
            }
            return {
                ...current,
                name: recipe.name,
                target:{ ...current.target, technology, kind:next.targetKind, buildSystem:defaultBuildSystem(next), moduleName },
                deliveryMode:recipe.deliveryMode,
                features,
                artifacts:ARTIFACT_KINDS.flatMap(kind => byKind.has(kind) ? [byKind.get(kind)!] : []),
                injectionPolicy:{ ...current.injectionPolicy, strategy:recipe.strategy, generatedRoot:'generated-integration', moduleName, previewRequired:true, rollbackRequired:true, preserveCustomCode:true },
                annotations:{ ...current.annotations, 'hydra.runtime.activation':'TOGGLEABLE', 'hydra.runtime.enabledByDefault':'false', 'hydra.runtime.preset':recipe.runtimePreset }
            };
        });
        setGuidedStep('capabilities');
        onMessage(t(recipe.appliedMessage));
    }

    function applyRecommendedTechnologyProfile(): void {
        if(descriptor === null || technologyWorkflow === null || technologyWorkflow.maturity === 'DECLARED_ONLY') return;
        const features = recommendedIntegrationFeatures(descriptor);
        const required = recommendedInputs(features,metamodelId);
        changeModel(current => {
            const byKind = new Map(current.artifacts.map(item => [item.kind, item] as const));
            if(grammarFirstScope(metamodelId))byKind.delete('METAMODEL');
            for(const kind of required){
                const existing=byKind.get(kind);
                byKind.set(kind,existing===undefined?{kind,documentId:defaultInputDocumentId(metamodelId,kind),required:true}:{...existing,required:true});
            }
            const projection=descriptor.targetKind==='DIAGRAM_PROJECTION';
            return {...current,deliveryMode:preferredDelivery(descriptor),features,artifacts:ARTIFACT_KINDS.flatMap(kind=>byKind.has(kind)?[byKind.get(kind)!]:[]),injectionPolicy:{...current.injectionPolicy,strategy:preferredStrategy(descriptor),generatedRoot:projection?'docs/diagrams':'generated-integration',previewRequired:!projection,rollbackRequired:!projection},annotations:{...current.annotations,'hydra.workflow.recommendation':'FULL_SUPPORTED_ONLY'}};
        });
        setGuidedStep('capabilities');
        onMessage(t('Recommended technology profile applied.'));
    }

    function applyPortableRuntimePreset(): void {
        changeModel(current => {
            const features = [...PORTABLE_RUNTIME_FEATURES];
            const recommended = recommendedInputs(features,metamodelId);
            const byKind = new Map(current.artifacts.map(item => [item.kind, item] as const));
            if(grammarFirstScope(metamodelId))byKind.delete('METAMODEL');
            for (const kind of recommended) {
                const existing = byKind.get(kind);
                byKind.set(kind, existing === undefined
                    ? { kind, documentId:defaultInputDocumentId(metamodelId, kind), required:true }
                    : { ...existing, required:true });
            }
            return {
                ...current,
                features,
                artifacts:ARTIFACT_KINDS.flatMap(kind => byKind.has(kind) ? [byKind.get(kind)!] : []),
                annotations:{
                    ...current.annotations,
                    'hydra.runtime.activation':'TOGGLEABLE',
                    'hydra.runtime.enabledByDefault':'false',
                    'hydra.runtime.preset':'PORTABLE_BEHAVIOR_RUNTIME'
                }
            };
        });
        onMessageRef.current(t('Portable runtime configured. It is disabled by default until the target host enables it for this semantic context.'));
    }

    async function browseGeneratedRoot(): Promise<void> {
        try {
            const root = await resolveProjectPath('');
            if (!root.exists || !root.directory || root.absolutePath.trim() === '') {
                onErrorRef.current(`${t('What went wrong')}: ${t('The generated folder could not be selected because no active project root is available.')}\n${t('Field')}: ${t('Generated folder (relative)')}\n${t('Why')}: ${t('HYDRA stores this value as a portable path relative to the active host project, not as an absolute operating-system path.')}\n${t('How to fix it')}: ${t('Open or import the host project first and use Select folder again, or type a safe relative path such as generated-integration.')}`);
                return;
            }
            const picked = await pickProjectPath('DIRECTORY', root.absolutePath, t('Select generated folder inside the active project'));
            if (!picked.available) {
                onErrorRef.current(picked.message || t('The native directory picker is not available in this runtime.'));
                return;
            }
            if (picked.cancelled) return;
            const relative = toProjectRelativeGeneratedRoot(root.absolutePath, picked.selectedPath || picked.selectedDirectory);
            if (!relative.valid) {
                onErrorRef.current(`${t('What went wrong')}: ${t(relative.message)}\n${t('Field')}: ${t('Generated folder (relative)')}\n${t('Why')}: ${t('Generated integration files must remain inside a safe project subfolder so preview, rollback and repository portability remain deterministic.')}\n${t('How to fix it')}: ${t('Choose a subfolder inside the active project. HYDRA stores only its project-relative path and never an absolute machine-specific location.')}`);
                return;
            }
            changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, generatedRoot:relative.relativePath } }));
            onMessageRef.current(`${t('Generated folder selected')}: ${relative.relativePath}`);
        } catch (error) {
            onErrorRef.current(error instanceof Error ? error.message : t('Could not open the directory selector'));
        }
    }

    function generatedRootControl(id: string) {
        const validation = validateGeneratedRootPath(model?.injectionPolicy.generatedRoot ?? '');
        return <div className="integration-generated-root-control">
            <input
                id={id}
                aria-invalid={!validation.valid}
                value={model?.injectionPolicy.generatedRoot ?? ''}
                onBlur={event => {
                    const checked = validateGeneratedRootPath(event.currentTarget.value);
                    if (checked.valid && checked.normalized !== event.currentTarget.value) {
                        changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, generatedRoot:checked.normalized } }));
                    }
                }}
                onChange={event => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,generatedRoot:event.target.value} }))}
            />
            <HydraButton type="button" onClick={() => void browseGeneratedRoot()}>{t('Select folder')}</HydraButton>
            {!validation.valid && <small className="integration-generated-root-error">{t(generatedRootProblemMessage(validation.problem))}</small>}
        </div>;
    }

    function updateArtifact(index: number, artifact: IntegrationArtifactSelection): void {
        changeModel(current => ({ ...current, artifacts:current.artifacts.map((item, itemIndex) => itemIndex === index ? artifact : item) }));
    }
    function deleteArtifact(index: number): void {
        changeModel(current => ({ ...current, artifacts:current.artifacts.filter((_, itemIndex) => itemIndex !== index) }));
    }
    function addMapping(): void {
        if(descriptor === null) return;
        changeModel(current => ({ ...current, mappings:[...current.mappings, newMapping(descriptor, current.mappings.length + 1)] }));
    }
    function updateMapping(index: number, mapping: IntegrationElementMapping): void {
        changeModel(current => ({ ...current, mappings:current.mappings.map((item, itemIndex) => itemIndex === index ? mapping : item) }));
    }
    async function analyze(): Promise<void> {
        if(model === null) return;
        const blocking = findIntegrationProfileBlockingError(model);
        if(blocking !== '') { onError(blocking); return; }
        try { setBusy(true); setReport(await checkIntegrationProfile(model)); onMessage('Integration compatibility analyzed'); }
        catch(error) { onError(error instanceof Error ? error.message : 'Could not analyze integration profile'); }
        finally { setBusy(false); }
    }
    async function save(): Promise<boolean> {
        if(model === null) return false;
        if(!persisted) { onError('Save the metamodel before configuring integrations.'); return false; }
        const blocking = findIntegrationProfileBlockingError(model);
        if(blocking !== '') { onError(blocking); return false; }
        if(!await confirmOverwrite()) return false;
        try {
            setBusy(true);
            const result = etag === '' ? await persistIntegrationProfile(model) : await replaceIntegrationProfile(model, etag);
            setModel(result.profile);
            setEtag(result.etag);
            setReport(result.checkReport);
            setDirty(false);
            setSummaries(current => {
                const summary: IntegrationProfileSummary = { id:result.profile.id,metamodelId:result.profile.metamodelId,name:result.profile.name,technology:result.profile.target.technology,deliveryMode:result.profile.deliveryMode,featureCount:result.profile.features.length,mappingCount:result.profile.mappings.length,etag:result.etag };
                return [...current.filter(item => item.id !== summary.id), summary].sort((left,right) => left.name.localeCompare(right.name));
            });
            onMessage('Integration profile saved');
            return true;
        } catch(error) {
            onError(error instanceof Error ? error.message : 'Could not save integration profile');
            return false;
        } finally { setBusy(false); }
    }
    async function compileTechnology(): Promise<void> {
        if(model === null || etag === '' || dirty) { onError('Save the integration profile before compiling it.'); return; }
        try {
            setBusy(true);
            setOperation({ title:'Generating technology package', detail:'Collecting the required HYDRA layers and compiling the target project.', stage:'compile' });
            const blob = await compileTechnologyIntegration(metamodelId, model.id);
            setOperation({ title:'Generating technology package', detail:'The package is ready. Preparing the download.', stage:'download' });
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = `${model.id}-technology.zip`;
            link.click();
            URL.revokeObjectURL(link.href);
            onMessage('Technology module compiled');
        } catch(error) { onError(error instanceof Error ? error.message : 'Could not compile technology module'); }
        finally { setBusy(false); setOperation(null); }
    }
    async function previewInjection(): Promise<void> {
        if(model === null || etag === '' || dirty) { onError('Save the integration profile before previewing host-project changes.'); return; }
        try {
            setBusy(true);
            setOperation({ title:'Preparing change preview', detail:'Resolving the generated module against the authorized host folder.', stage:'resolve' });
            const preview = await previewTechnologyIntegration(metamodelId, model.id, injectionTarget);
            setOperation({ title:'Preparing change preview', detail:'Checking conflicts, rollback information and file changes.', stage:'review' });
            setInjectionPreview(preview);
            onMessage(preview.plan.status === 'BLOCKED' ? 'Change preview found blocking conflicts' : 'Change preview ready');
        } catch(error) { onError(error instanceof Error ? error.message : 'Could not preview technology integration'); }
        finally { setBusy(false); setOperation(null); }
    }
    async function applyInjection(): Promise<void> {
        if(model === null || injectionPreview?.plan.status !== 'READY') { onError('Create a successful preview before applying the integration.'); return; }
        if(!await requestDecision({ title:t('Apply integration changes?'), description:`${injectionPreview.plan.fileChanges.length} ${t('file changes will be applied to')} ${injectionTarget}.`, confirmLabel:t('Apply changes'), cancelLabel:t('Cancel') })) return;
        try {
            setBusy(true);
            setOperation({ title:'Applying reversible integration', detail:'Writing generated files and recording the rollback manifest.', stage:'write' });
            const result = await applyTechnologyIntegration(metamodelId, model.id, injectionTarget);
            setOperation({ title:'Applying reversible integration', detail:'Integration written. Finalizing the manifest.', stage:'manifest' });
            setInjectionPreview(null);
            onMessage(`Integration applied with manifest ${result.manifest.id}`);
        } catch(error) { onError(error instanceof Error ? error.message : 'Could not apply technology integration'); }
        finally { setBusy(false); setOperation(null); }
    }
    async function remove(): Promise<void> {
        if(model === null || etag === '') return;
        try {
            setBusy(true);
            await deleteIntegrationProfile(metamodelId, model.id, etag);
            const remaining = summaries.filter(item => item.id !== model.id);
            setSummaries(remaining);
            if(remaining[0] !== undefined) {
                const loaded = await loadIntegrationProfile(metamodelId, remaining[0].id);
                setModel(loaded.model);
                setEtag(loaded.etag);
                setInjectionTarget(loaded.model.target.projectId || 'generated-target');
                setInjectionPreview(null);
                setDirty(false);
            } else { createNew(model.target.technology); }
            setReport(null);
            onMessage('Integration profile deleted');
        } catch(error) { onError(error instanceof Error ? error.message : 'Could not delete integration profile'); }
        finally { setBusy(false); }
    }
    function selectStructureItem(selection: Exclude<IntegrationStructureSelection, null>): void {
        setStructureSelection(selection);
        setStructurePropertiesOpen(true);
        setStructurePropertiesCollapsed(false);
    }
    function structureProperties() {
        if (model === null || descriptor === null) return null;
        if (structureSelection === null || structureSelection.kind === 'PROFILE') return <section className="integration-focused-properties"><h3>Profile properties</h3><DesignerRequiredFieldsLegend /><DesignerHelpedField label="Profile ID" targetId="structure-integration-profile-id" help="Stable identifier of this integration profile." example="people-glsp" required><input id="structure-integration-profile-id" readOnly={etag !== ''} value={model.id} onChange={event => changeModel(current => ({ ...current, id: event.target.value }))} /></DesignerHelpedField><DesignerHelpedField label="Name" targetId="structure-integration-profile-name" help="Human-readable integration name." example="People GLSP integration" required><input id="structure-integration-profile-name" value={model.name} onChange={event => changeModel(current => ({ ...current, name: event.target.value }))} /></DesignerHelpedField><DesignerHelpedField label="Delivery mode" targetId="structure-integration-delivery" help="Defines the deployment boundary between HYDRA, generated artifacts and the host project. Review the available option descriptions: Embedded runs with the host, Remote uses a separately deployed runtime, Hybrid combines local and remote parts, Generated module creates an explicit host module, and Projection only emits files without installing runtime behavior." example="Generated module" required><HydraAdaptiveSelect id="structure-integration-delivery" ariaLabel="Structure delivery mode" value={model.deliveryMode} options={descriptor.deliveryModes.map(item => ({ value:item, label:readableIntegrationValue(item), description:integrationDynamicDescription(item), category:deliveryCategory(item) }))} onChange={value => changeModel(current => ({ ...current, deliveryMode:value as IntegrationProfileModel['deliveryMode'] }))} /></DesignerHelpedField></section>;
        if (structureSelection.kind === 'TARGET') return <section className="integration-focused-properties"><h3>Target properties</h3><DesignerHelpedField label="Technology" targetId="structure-integration-technology" help="External target technology." example="Eclipse GLSP" required><HydraAdaptiveSelect id="structure-integration-technology" ariaLabel="Structure integration technology" value={model.target.technology} options={descriptors.map(item => ({ value:item.technology, label:item.displayName, description:item.notes.join(' '), category:technologyCategory(item.technology) }))} onChange={value => changeTechnology(value as IntegrationTechnology)} /></DesignerHelpedField><DesignerHelpedField label="Target version" targetId="structure-integration-version" help="Version constraint for the technology." example="2.4.x"><input id="structure-integration-version" value={model.target.technologyVersion} onChange={event => changeModel(current => ({ ...current, target:{ ...current.target, technologyVersion:event.target.value } }))} /></DesignerHelpedField><DesignerHelpedField label="Host project" targetId="structure-integration-project" help="Project that receives the generated integration." example="people-tools" required><input id="structure-integration-project" value={model.target.projectId} onChange={event => changeModel(current => ({ ...current, target:{ ...current.target, projectId:event.target.value } }))} /></DesignerHelpedField><DesignerHelpedField label="Host module" targetId="structure-integration-module" help="Optional module inside the host project." example="people-editor"><input id="structure-integration-module" value={model.target.moduleName} onChange={event => changeModel(current => ({ ...current, target:{ ...current.target, moduleName:event.target.value } }))} /></DesignerHelpedField><DesignerHelpedField label="Build system" targetId="structure-integration-build" help="Native host build system." example="Maven" required><HydraAdaptiveSelect id="structure-integration-build" ariaLabel="Structure build system" value={model.target.buildSystem} options={BUILD_SYSTEMS.map(item => ({ value:item, label:readableIntegrationValue(item), description:INTEGRATION_OPTION_DESCRIPTIONS[item], category:buildSystemCategory(item) }))} onChange={value => changeModel(current => ({ ...current, target:{ ...current.target, buildSystem:value as IntegrationProfileModel['target']['buildSystem'] } }))} /></DesignerHelpedField></section>;
        if (structureSelection.kind === 'INJECTION') return <section className="integration-focused-properties"><h3>Host integration</h3><DesignerHelpedField label="Strategy" targetId="structure-injection-strategy" help="Reversible strategy used to add the generated artifacts." example="Add module" required><HydraAdaptiveSelect id="structure-injection-strategy" ariaLabel="Structure integration method" value={model.injectionPolicy.strategy} options={descriptor.injectionStrategies.map(item => ({ value:item, label:readableIntegrationValue(item), description:integrationDynamicDescription(item), category:injectionCategory(item) }))} onChange={value => changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, strategy:value as IntegrationProfileModel['injectionPolicy']['strategy'] } }))} /></DesignerHelpedField><DesignerHelpedField label="Generated folder (relative)" targetId="structure-generated-root" help="Portable project-relative output folder. Select a directory inside the active host project or enter the relative path manually; absolute paths are intentionally not stored." example="generated-integration" required>{generatedRootControl('structure-generated-root')}</DesignerHelpedField><DesignerHelpedField label="Generated module" targetId="structure-generated-module" help="Generated module name." example="people-glsp"><input id="structure-generated-module" value={model.injectionPolicy.moduleName} onChange={event => changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, moduleName:event.target.value } }))} /></DesignerHelpedField><DesignerHelpedCheckbox checked={model.injectionPolicy.previewRequired} label="Preview required" targetId="structure-preview-required" help="Requires preview before applying files." example="Enabled" onChange={value => changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, previewRequired:value } }))} /><DesignerHelpedCheckbox checked={model.injectionPolicy.rollbackRequired} label="Rollback required" targetId="structure-rollback-required" help="Requires a reversible installation plan." example="Enabled" onChange={value => changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, rollbackRequired:value } }))} /><DesignerHelpedCheckbox checked={model.injectionPolicy.preserveCustomCode} label="Preserve custom code" targetId="structure-preserve-code" help="Preserves declared manual regions during regeneration." example="Enabled" onChange={value => changeModel(current => ({ ...current, injectionPolicy:{ ...current.injectionPolicy, preserveCustomCode:value } }))} /></section>;
        if (structureSelection.kind === 'FEATURES') return <section className="integration-focused-properties"><h3>Enabled features</h3>{renderFeatureSelector()}</section>;
        if (structureSelection.kind === 'ARTIFACT') {
            const artifact = model.artifacts[structureSelection.index];
            if (artifact === undefined) return <p>{t('The selected HYDRA document no longer exists.')}</p>;
            return <section className="integration-focused-properties"><h3>{t('HYDRA input document')}</h3><DesignerHelpedField label="Document type" targetId="structure-artifact-kind" help="Type of HYDRA document that this integration reads as input." example="Event flows" required><HydraAdaptiveSelect id="structure-artifact-kind" ariaLabel="HYDRA document type" value={artifact.kind} options={ARTIFACT_KINDS.map(item => ({ value:item, label:readableIntegrationValue(item), description:INTEGRATION_OPTION_DESCRIPTIONS[item], category:artifactCategory(item) }))} onChange={value => updateArtifact(structureSelection.index, { ...artifact, kind:value as IntegrationArtifactKind })} /></DesignerHelpedField><DesignerHelpedField label="Document ID" targetId="structure-artifact-document" help="Stable identifier of the HYDRA document used as input." example="people-events" required><input id="structure-artifact-document" value={artifact.documentId} onChange={event => updateArtifact(structureSelection.index, { ...artifact, documentId:event.target.value })} /></DesignerHelpedField><DesignerHelpedCheckbox checked={artifact.required} label="Required" targetId="structure-artifact-required" help="Makes this HYDRA document mandatory for compilation." example="Required" onChange={value => updateArtifact(structureSelection.index, { ...artifact, required:value })} /><HydraButton className="hydra-danger-button" disabled={artifact.kind === 'METAMODEL'} type="button" onClick={() => { deleteArtifact(structureSelection.index); setStructureSelection({ kind:'PROFILE', id:model.id }); }}>{t('Remove document')}</HydraButton></section>;
        }
        if (structureSelection.kind === 'MAPPING') {
            const mapping = model.mappings[structureSelection.index];
            if (mapping === undefined) return <p>The selected mapping no longer exists.</p>;
            return <section className="integration-focused-properties"><h3>Host mapping</h3><DesignerHelpedField label="Mapping ID" targetId="structure-mapping-id" help="Stable mapping identifier." example="person-class" required><input id="structure-mapping-id" value={mapping.id} onChange={event => updateMapping(structureSelection.index, { ...mapping, id:event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="Mapping kind" targetId="structure-mapping-kind" help="Semantic kind of the mapped element." example="Type" required><HydraAdaptiveSelect id="structure-mapping-kind" ariaLabel="Structure mapping kind" value={mapping.kind} options={MAPPING_KINDS.map(item => ({ value:item, label:readableIntegrationValue(item), description:INTEGRATION_OPTION_DESCRIPTIONS[item], category:mappingCategory(item) }))} onChange={value => updateMapping(structureSelection.index, { ...mapping, kind:value as IntegrationMappingKind })} /></DesignerHelpedField><DesignerHelpedField label="HYDRA document" targetId="structure-mapping-document" help="HYDRA document containing the source element." example="people-metamodel"><input id="structure-mapping-document" value={mapping.canonicalDocumentId} onChange={event => updateMapping(structureSelection.index, { ...mapping, canonicalDocumentId:event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="HYDRA element" targetId="structure-mapping-element" help="Stable HYDRA element ID used as the mapping source." example="class:person" required><input id="structure-mapping-element" value={mapping.canonicalElementId} onChange={event => updateMapping(structureSelection.index, { ...mapping, canonicalElementId:event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="Locator strategy" targetId="structure-mapping-locator" help="How the host target is resolved." example="Qualified name" required><HydraAdaptiveSelect id="structure-mapping-locator" ariaLabel="Structure locator strategy" value={mapping.hostReference.strategy} options={descriptor.locatorStrategies.map(item => ({ value:item, label:readableIntegrationValue(item), description:integrationDynamicDescription(item), category:locatorCategory(item) }))} onChange={value => updateMapping(structureSelection.index, { ...mapping, hostReference:{ ...mapping.hostReference, strategy:value as IntegrationLocatorStrategy } })} /></DesignerHelpedField><DesignerHelpedField label="Host qualified name" targetId="structure-mapping-qualified" help="Qualified host element name." example="people.Person"><input id="structure-mapping-qualified" value={mapping.hostReference.qualifiedName} onChange={event => updateMapping(structureSelection.index, { ...mapping, hostReference:{ ...mapping.hostReference, qualifiedName:event.target.value } })} /></DesignerHelpedField><DesignerHelpedField label="Host path" targetId="structure-mapping-path" help="Document or AST path to the host element." example="src/model.py#/Person"><input id="structure-mapping-path" value={mapping.hostReference.path} onChange={event => updateMapping(structureSelection.index, { ...mapping, hostReference:{ ...mapping.hostReference, path:event.target.value } })} /></DesignerHelpedField><DesignerHelpedCheckbox checked={mapping.required} label="Required mapping" targetId="structure-mapping-required" help="Blocks compilation when the host element cannot be resolved." example="Required" onChange={value => updateMapping(structureSelection.index, { ...mapping, required:value })} /><HydraButton className="hydra-danger-button" type="button" onClick={() => { changeModel(current => ({ ...current, mappings:current.mappings.filter((_, index) => index !== structureSelection.index) })); setStructureSelection({ kind:'PROFILE', id:model.id }); }}>Remove mapping</HydraButton></section>;
        }
        return <section className="integration-focused-properties"><h3>Change preview</h3>{injectionPreview === null ? <p>No preview has been generated.</p> : <><p>{injectionPreview.plan.status}: {injectionPreview.plan.fileChanges.length} file changes.</p><ul>{injectionPreview.plan.fileChanges.map(change => <li key={`${change.changeKind}-${change.path}`}><strong>{change.changeKind}</strong> {change.path}</li>)}</ul></>}</section>;
    }
    if(!persisted) return <section className="integration-designer"><p>Save the metamodel before defining technology integrations.</p></section>;
    if(model === null || descriptor === null) return <section className="integration-designer"><p>Loading integration targets…</p></section>;
    const integrationBlockingError = findIntegrationProfileBlockingError(model);
    const generatedRootValidation = validateGeneratedRootPath(model.injectionPolicy.generatedRoot);
    const mappingsValid = model.mappings.every(item => item.id.trim() !== '' && item.canonicalElementId.trim() !== '');
    const compatibilityReady = report !== null && report.diagnostics.every(item => item.severity !== 'ERROR');
    const guidedSteps = [
        {
            id: 'identity', title: 'Profile identity', description: 'Name the integration and establish its stable technical identifier.',
            checks: [
                { id: 'profile-id', label: 'Stable profile ID defined', complete: model.id.trim() !== '', detail: model.id || 'Required' },
                { id: 'profile-name', label: 'Visible profile name defined', complete: model.name.trim() !== '', detail: model.name || 'Required' }
            ],
            content: <section className="integration-guided-fields"><DesignerRequiredFieldsLegend /><DesignerHelpedField label="Profile ID" targetId="guided-integration-profile-id" help="Stable identifier shared with the complete Definition and Structure tree views." example="people-glsp-integration" required><input id="guided-integration-profile-id" readOnly={etag !== ''} value={model.id} onChange={event => changeModel(current => ({ ...current, id:event.target.value }))} /></DesignerHelpedField><DesignerHelpedField label="Name" targetId="guided-integration-profile-name" help="Human-readable integration name." example="People GLSP integration" required><input id="guided-integration-profile-name" value={model.name} onChange={event => changeModel(current => ({ ...current, name:event.target.value }))} /></DesignerHelpedField></section>
        },
        {
            id: 'target', title: 'Technology and target', description: 'Choose where the HYDRA capabilities will be delivered and which host project will receive them.',
            checks: [
                { id: 'technology', label: 'Target technology selected', complete: true, detail: descriptor.displayName },
                { id: 'workflow-implementation', label: 'Delivery implementation is available', complete: technologyWorkflow !== null && technologyWorkflow.maturity !== 'DECLARED_ONLY', detail: technologyWorkflow === null ? 'Loading workflow maturity' : `${technologyWorkflow.parityScore}% · ${technologyWorkflow.maturity}` },
                { id: 'project', label: 'Host project identified', complete: model.target.projectId.trim() !== '', detail: model.target.projectId || 'Required' },
                { id: 'generated-root', label: 'Safe generated root defined', complete: generatedRootValidation.valid, detail: generatedRootValidation.valid ? generatedRootValidation.normalized : generatedRootProblemMessage(generatedRootValidation.problem) }
            ],
            content: <section className="integration-guided-fields"><DesignerHelpedField label="Integration technology" targetId="guided-integration-technology" help="External editor, runtime or projection target." example="Eclipse GLSP" required><HydraAdaptiveSelect id="guided-integration-technology" ariaLabel="Guided integration technology" value={model.target.technology} options={descriptors.map(item => ({ value:item.technology,label:item.displayName,description:item.notes.join(' ') || `Integrates HYDRA with ${item.displayName}.`,category:technologyCategory(item.technology) }))} onChange={technology => changeTechnology(technology as IntegrationTechnology)} /></DesignerHelpedField><DesignerHelpedField label="Host project" targetId="guided-integration-project" help="Project that will receive the generated integration." example="people-tools" required><input id="guided-integration-project" value={model.target.projectId} onChange={event => changeModel(current => ({ ...current,target:{...current.target,projectId:event.target.value} }))} /></DesignerHelpedField><DesignerHelpedField label="Delivery mode" targetId="guided-integration-delivery" help="Defines where the integration runtime and generated artifacts live in relation to the host project. Use the option descriptions below to choose the deployment boundary, not the implementation technology." example="Generated module" required options={orderedDeliveryModes(descriptor).map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item) }))}><HydraAdaptiveSelect id="guided-integration-delivery" ariaLabel="Guided delivery mode" value={model.deliveryMode} options={orderedDeliveryModes(descriptor).map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item),category:deliveryCategory(item) }))} onChange={deliveryMode => changeModel(current => ({ ...current,deliveryMode:deliveryMode as IntegrationProfileModel['deliveryMode'] }))} /></DesignerHelpedField><DesignerHelpedField label="Generated folder (relative)" targetId="guided-integration-root" help="Portable folder inside the active host project where HYDRA writes generated integration files. It is stored as a project-relative path so the profile works on another machine or repository checkout. Use Select folder when the host project is open, or type the relative path manually." example="generated-integration" required>{generatedRootControl('guided-integration-root')}</DesignerHelpedField><IntegrationMethodChooser descriptor={descriptor} value={model.injectionPolicy.strategy} onChange={strategy => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,strategy} }))} /><IntegrationTechnologyJourney workflow={technologyWorkflow} /><div className="integration-guided-actions"><HydraButton className="hydra-primary-button" disabled={technologyWorkflow === null || technologyWorkflow.maturity === 'DECLARED_ONLY'} type="button" onClick={applyRecommendedTechnologyProfile}>{t('Apply recommended technology profile')}</HydraButton></div><IntegrationRecipeChooser onApply={applyGuidedModelingRecipe} /></section>
        },
        {
            id: 'capabilities', title: 'Capabilities and inputs', description: 'Choose what the integration must do. HYDRA can select the required model information automatically.',
            checks: [
                { id: 'semantic-root-artifact', label: grammarFirstScope(metamodelId) ? 'Grammar root is included' : 'Model structure is included', complete: model.artifacts.some(item => item.kind === semanticRootKind(metamodelId)), detail: `${model.artifacts.length} ${t(model.artifacts.length === 1 ? 'selected input' : 'selected inputs')}` },
                { id: 'features-reviewed', label: 'Capabilities have been reviewed', complete: true, detail: `${model.features.length} ${t(model.features.length === 1 ? 'enabled capability' : 'enabled capabilities')}` }
            ],
            content: <section className="integration-guided-summary integration-guided-inputs"><header><div><h4>{t('What does this integration need from HYDRA?')}</h4><p>{t('Choose the capabilities first. HYDRA can then include the model structure, validations, events, gamification or editor information that those capabilities actually require.')}</p></div><HydraTooltip label="About integration inputs" title="Information used by the integration"><p>These are the parts of your HYDRA project that the generated integration needs to read. They are not files you must create manually.</p><p>Use automatic configuration for the normal case. Open the complete form only when a target needs an unusual additional input.</p></HydraTooltip></header><div className="integration-guided-input-badges"><span>{model.features.length} {t(model.features.length === 1 ? 'capability' : 'capabilities')}</span><span>{model.artifacts.length} {t(model.artifacts.length === 1 ? 'selected input' : 'selected inputs')}</span></div><div className="integration-guided-input-grid">{ARTIFACT_KINDS.filter(kind=>!(grammarFirstScope(metamodelId)&&kind==='METAMODEL')).map(kind => { const selected=model.artifacts.some(item => item.kind === kind); const suggested=recommendedInputs(model.features,metamodelId).has(kind); const info=INPUT_INFO[kind]; return <label className={`integration-guided-input-card${selected ? ' selected' : ''}${suggested ? ' suggested' : ''}`} key={kind}><input type="checkbox" checked={selected} disabled={kind === semanticRootKind(metamodelId)} onChange={() => toggleGuidedInput(kind)} /><span><strong>{t(info.title)}</strong><small>{t(info.description)}</small>{suggested && <em>{t(kind === semanticRootKind(metamodelId) ? 'Always required' : 'Recommended for the selected capabilities')}</em>}</span></label>; })}</div><div className="integration-guided-actions"><HydraButton className="hydra-primary-button" type="button" onClick={applyPortableRuntimePreset}>{t('Apply portable runtime preset')}</HydraButton><HydraButton type="button" onClick={configureInputsAutomatically}>{t('Configure inputs automatically')}</HydraButton><HydraButton type="button" onClick={() => setView('FORM')}>{t('Open detailed input configuration')}</HydraButton></div><p className="integration-guided-runtime-note">{t('The portable runtime preset includes validations, quick fixes, events, EventFlow and gamification evidence. Generated targets receive a semantic-context activation gate that is disabled by default.')}</p></section>
        },
        {
            id: 'mappings', title: 'Host mappings', description: 'Connect stable HYDRA element IDs to resolvable elements in the host technology.',
            checks: [
                { id: 'mapping-shape', label: 'Every mapping has an ID and HYDRA element', complete: mappingsValid, detail: model.mappings.length === 0 ? 'No mappings required yet' : `${model.mappings.length} mapping(s)` },
                { id: 'locator-support', label: 'Locator strategies are provided by the selected target', complete: descriptor.locatorStrategies.length > 0, detail: `${descriptor.locatorStrategies.length} locator strategy option(s)` }
            ],
            content: <section className="integration-guided-summary"><header><div><h4>Host mapping layer</h4><p>Mappings connect stable HYDRA element IDs to elements that can be found again in the host technology.</p></div><HydraTooltip label="About host mappings" title="Host mappings"><p>Each mapping connects one HYDRA element to a stable host locator. Required mappings become blocking diagnostics when the target element cannot be found.</p></HydraTooltip></header><div className="integration-guided-buttons"><HydraButton type="button" onClick={addMapping}>+ Mapping</HydraButton><HydraButton type="button" onClick={() => setView('STRUCTURE')}>Edit mappings in structure tree</HydraButton><HydraButton type="button" onClick={() => setView('FORM')}>Edit detailed mapping fields</HydraButton></div></section>
        },
        {
            id: 'verify', title: 'Analyze compatibility', description: 'Check the profile and let the target planner report missing inputs, mappings or unsupported choices.',
            checks: [
                { id: 'profile-valid', label: 'Profile has no blocking definition error', complete: integrationBlockingError === '', detail: integrationBlockingError || 'Profile definition is consistent' },
                { id: 'compatibility', label: 'Compatibility analysis contains no ERROR diagnostics', complete: compatibilityReady, detail: report === null ? 'Run Analyze' : compatibilityReady ? 'Compatibility verified' : 'Resolve the reported ERROR diagnostics' },
                { id: 'saved', label: 'Verified profile is saved', complete: etag !== '' && !dirty, detail: dirty ? 'Save pending changes' : etag === '' ? 'Save the profile' : 'Saved' }
            ],
            content: <section className="integration-guided-review"><strong>{integrationBlockingError || (compatibilityReady ? 'Target compatibility is verified.' : 'Analyze the target before generating or applying an integration.')}</strong><div><HydraButton disabled={busy || integrationBlockingError !== ''} type="button" onClick={() => void analyze()}>Analyze</HydraButton><HydraButton disabled={busy || !dirty || integrationBlockingError !== ''} type="button" onClick={() => void save()}>Save profile</HydraButton>{report !== null && <HydraButton type="button" onClick={() => setView('FORM')}>Open compatibility report</HydraButton>}</div></section>
        },
        {
            id: 'delivery', title: 'Generate or apply', description: 'Generate a technology module or preview reversible host-project changes before applying the selected integration.',
            checks: [
                { id: 'saved-delivery', label: 'Saved profile available', complete: etag !== '' && !dirty, detail: etag !== '' && !dirty ? 'Compile and preview enabled' : 'Save verified changes first' },
                { id: 'preview-safety', label: 'Host-project changes remain preview-first and reversible', complete: !hostChangesApplicable || (model.injectionPolicy.previewRequired && model.injectionPolicy.rollbackRequired), detail: hostChangesApplicable ? 'Preview and rollback are mandatory' : 'Not applicable to projection-only delivery' }
            ],
            content: <section className="integration-guided-delivery"><IntegrationTechnologyJourney workflow={technologyWorkflow} compact />{canCompileTechnology&&<article><strong>Generate module</strong><span>Create the technology-specific integration ZIP without modifying the host project.</span><HydraButton disabled={busy || dirty || etag === ''} type="button" onClick={() => void compileTechnology()}>Compile module</HydraButton></article>}{projectionGeneration&&<article><strong>{t('Generate projection')}</strong><span>{t("This technology is delivered through HYDRA's projection/export workflow rather than a host integration module.")}</span><small>{t('Use the metamodel export workspace to materialize the projection. Preview and host-project injection do not apply.')}</small></article>}{canPreviewInjection&&<article><strong>Apply to a host project</strong><span>Preview exact file changes first. Apply remains disabled until the preview reports READY.</span><div><HydraButton disabled={busy || dirty || etag === ''} type="button" onClick={() => void previewInjection()}>Preview changes</HydraButton>{canApplyInjection&&<HydraButton disabled={busy || injectionPreview?.plan.status !== 'READY'} type="button" onClick={() => void applyInjection()}>Apply integration</HydraButton>}</div></article>}{technologyWorkflow?.maturity==='DECLARED_ONLY'&&<article className="integration-guided-delivery-blocked"><strong>{t('Generation is not available yet')}</strong><span>{t('HYDRA knows this technology in the catalogue, but no executable adapter is registered. The profile remains inspectable without pretending that generation is operational.')}</span></article>}</section>
        }
    ];
    return <section className="integration-designer">
        <DesignerCommandBar className="integration-designer-header hydra-unified-workbench-toolbar" title="Technology integrations" description="Choose what HYDRA should expose to an external technology, review compatibility and deliver it through a reversible integration." status={<span className="integration-dirty-state">{dirty ? 'Unsaved changes' : 'Saved'}</span>} actions={<><DesignerCommandGroup label="Interchange">{model !== null && <IntegrationRemoteStorage model={model} metamodelId={metamodelId} onImported={next => { setModel(next); setEtag(''); setDirty(true); setReport(null); setInjectionPreview(null); }} />}</DesignerCommandGroup><DesignerCommandGroup label="Quality"><HydraButton disabled={busy} type="button" onClick={() => void analyze()}>Analyze</HydraButton></DesignerCommandGroup>{(canCompileTechnology||canPreviewInjection||canApplyInjection)&&<DesignerCommandGroup label="Generation">{canCompileTechnology&&<HydraButton disabled={busy || dirty || etag === ''} type="button" onClick={() => void compileTechnology()}>Compile module</HydraButton>}{canPreviewInjection&&<HydraButton disabled={busy || dirty || etag === ''} type="button" onClick={() => void previewInjection()}>Preview changes</HydraButton>}{canApplyInjection&&<HydraButton disabled={busy || injectionPreview?.plan.status !== 'READY'} type="button" onClick={() => void applyInjection()}>Apply integration</HydraButton>}</DesignerCommandGroup>}<DesignerCommandGroup label="Changes"><HydraButton disabled={busy || !dirty} type="button" onClick={() => void save()}>Save profile</HydraButton><HydraButton className="hydra-danger-button" disabled={busy || etag === ''} type="button" onClick={() => void remove()}>Delete</HydraButton></DesignerCommandGroup></>} viewsLabel="Workspace" viewsSummary={view} views={<nav className="event-subtabs integration-designer-tabs" aria-label="Integration designer views"><HydraButton className={view === 'GUIDED' ? 'active' : ''} type="button" onClick={() => setView('GUIDED')}>Guided</HydraButton><HydraButton className={view === 'FORM' ? 'active' : ''} type="button" onClick={() => setView('FORM')}>Definition</HydraButton>{structureTabOpen ? <span className={`designer-structure-tab-entry${view === 'STRUCTURE' ? ' active' : ''}`}><HydraButton className={view === 'STRUCTURE' ? 'active' : ''} type="button" onClick={() => setView('STRUCTURE')}>Structure tree</HydraButton><HydraButton aria-label="Close integration structure tree" className="designer-structure-tab-close hydra-window-close" type="button" onClick={() => { setStructureTabOpen(false); if (view === 'STRUCTURE') setView('FORM'); }}>×</HydraButton></span> : <HydraButton type="button" onClick={() => { setStructureTabOpen(true); setView('STRUCTURE'); }}>Structure tree</HydraButton>}</nav>} />
        <div className="hydra-designer-master-detail integration-master-detail" style={sidebar.layoutStyle}>
            <aside className="hydra-designer-sidebar">
                <div className="hydra-sidebar-heading">
                    <div><h3>Integration profiles</h3><small>{summaries.length + (etag === '' ? 1 : 0)} profile(s)</small></div>
                    <div className="hydra-sidebar-heading-actions"><DesignerGuideButton kind="INTEGRATION" /><HydraButton type="button" onClick={() => createNew()}>+ Profile</HydraButton></div>
                </div>
                <div className="hydra-sidebar-filter"><input aria-label="Search integration profiles" placeholder="Search profiles…" value={query} onChange={event => setQuery(event.target.value)} /></div>
                <div className="hydra-sidebar-scroll">
                    {etag === '' && <HydraButton className="hydra-designer-list-item selected" type="button"><span><strong>{model.name || 'New integration profile'}</strong><small>{model.id}</small></span><span className="hydra-designer-list-badge">NEW</span></HydraButton>}
                    {visibleSummaries.map(item => <HydraButton className={`hydra-designer-list-item${etag !== '' && model.id === item.id ? ' selected' : ''}`} key={item.id} type="button" onClick={() => void selectProfile(item.id)}><span><strong>{item.name}</strong><small>{item.id}</small></span><span className="hydra-designer-list-badge">{item.technology}</span></HydraButton>)}
                    {visibleSummaries.length === 0 && etag !== '' && <div className="hydra-empty-state"><strong>No profiles found</strong><span>Change the search or create a new profile.</span></div>}
                </div>
            </aside>
            <div {...sidebar.separatorProps} />
            <main className="hydra-designer-form">
                <HydraOperationProgress active={operation !== null} title={operation?.title ?? ''} detail={operation?.detail} currentStageId={operation?.stage ?? ''} stages={[{id:'resolve',label:'Resolve target'},{id:'compile',label:'Compile package'},{id:'review',label:'Review changes'},{id:'write',label:'Write integration'},{id:'manifest',label:'Finalize manifest'},{id:'download',label:'Prepare download'}]} />
                {view === 'GUIDED' ? <HydraGuidedWorkflow key={model.id} title="Create a technology integration step by step" description="Start with the high-level decisions. Definition and Structure tree remain available when you need advanced target, mapping or host-integration details." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} onFinish={() => save()} finishLabel="Save integration profile" className="integration-guided-workflow" /> : view === 'STRUCTURE' ? <div className={`designer-structure-workspace designer-structure-direct-layout integration-structure-workspace${structureCollapsed ? ' collapsed' : ''}`}>
                    <section className="designer-structure-tree-pane"><header><div><h3>Integration structure</h3><p>Target, capabilities, HYDRA input documents, host mappings and change plan.</p></div><HydraButton type="button" onClick={() => setStructureCollapsed(value => !value)}>{structureCollapsed ? 'Expand tree' : 'Collapse tree'}</HydraButton></header>{!structureCollapsed && <><input aria-label="Filter integration structure" placeholder="Filter structure..." value={structureFilter} onChange={event => setStructureFilter(event.target.value)} /><IntegrationStructureTree model={model} preview={injectionPreview} selection={structureSelection} filter={structureFilter} onSelect={selectStructureItem} /></>}</section>
                    {structurePropertiesOpen ? <aside className={`designer-structure-properties-pane${structurePropertiesCollapsed ? ' collapsed' : ''}`}><header><div><h3>Properties</h3><p>Changes update the same integration profile used by every view.</p></div><div><HydraButton type="button" onClick={() => setStructurePropertiesCollapsed(value => !value)}>{structurePropertiesCollapsed ? 'Expand' : 'Collapse'}</HydraButton><HydraButton aria-label="Close integration properties" className="hydra-form-close-button" type="button" onClick={() => setStructurePropertiesOpen(false)}>×</HydraButton></div></header>{!structurePropertiesCollapsed && structureProperties()}</aside> : <HydraButton className="designer-structure-properties-reopen" type="button" onClick={() => { setStructurePropertiesOpen(true); setStructurePropertiesCollapsed(false); }}>Open properties</HydraButton>}
                </div> : <>
                <div className="hydra-designer-form-header"><div><h3>{model.name || 'New integration profile'}</h3><p>{model.id}</p><div className="integration-overview"><strong>Integration summary</strong><p>Choose the target, enable only the capabilities you need, review safety and analyze compatibility before generation or host integration.</p>{overviewBadges()}</div></div></div>
                <DesignerRequiredFieldsLegend />
                <div className="hydra-designer-fields">
                    <DesignerHelpedField label="Profile ID" targetId="integration-profile-id" help="Stable technical identifier used by persistence, APIs and integration manifests. It can only be edited before the first save." example="people-besser-sdk-integration" required><input id="integration-profile-id" readOnly={etag !== ''} value={model.id} onChange={event => changeModel(current => ({ ...current,id:event.target.value }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Name" targetId="integration-profile-name" help="Human-readable name shown in the workbench and project explorer." example="BESSER Python SDK integration" required><input id="integration-profile-name" value={model.name} onChange={event => changeModel(current => ({ ...current,name:event.target.value }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Integration technology" targetId="integration-technology" help="External runtime, editor or projection target that receives the generated integration layer." example="BESSER SDK" required options={descriptors.map(item => ({ value:item.technology,label:item.displayName,description:item.notes.join(' ') || `Integrates HYDRA with ${item.displayName}.` }))}><HydraAdaptiveSelect ariaLabel="Integration technology" id="integration-technology" required value={model.target.technology} options={descriptors.map(item => ({ value:item.technology,label:item.displayName,description:item.notes.join(' ') || `Integrates HYDRA with ${item.displayName}.`,category:technologyCategory(item.technology) }))} onChange={technology => changeTechnology(technology as IntegrationTechnology)} /></DesignerHelpedField>
                    <DesignerHelpedField label="Target version" targetId="integration-target-version" help="Optional version constraint for the selected host technology." example="2.4.x"><input id="integration-target-version" value={model.target.technologyVersion} onChange={event => changeModel(current => ({ ...current,target:{...current.target,technologyVersion:event.target.value} }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Host project" targetId="integration-host-project" help="Stable identifier or name of the project where the integration will be installed." example="people-modeling-tools" required><input id="integration-host-project" value={model.target.projectId} onChange={event => changeModel(current => ({ ...current,target:{...current.target,projectId:event.target.value} }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Host module" targetId="integration-host-module" help="Optional module, package or plugin inside the host project." example="people-editor-plugin"><input id="integration-host-module" value={model.target.moduleName} onChange={event => changeModel(current => ({ ...current,target:{...current.target,moduleName:event.target.value} }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Delivery mode" targetId="integration-delivery-mode" help="Defines the deployment boundary between HYDRA, generated artifacts and the host project. Review each available value below: Embedded runs inside the host, Remote connects to a separate service, Hybrid combines both, Generated module creates a self-contained host module, and Projection only emits portable files without runtime installation." example="Generated module" required options={descriptor.deliveryModes.map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item) }))}><HydraAdaptiveSelect id="integration-delivery-mode" ariaLabel="Delivery mode" required value={model.deliveryMode} options={orderedDeliveryModes(descriptor).map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item),category:deliveryCategory(item) }))} onChange={deliveryMode => changeModel(current => ({ ...current,deliveryMode:deliveryMode as IntegrationProfileModel['deliveryMode'] }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Build system" targetId="integration-build-system" help="Build ecosystem used by the host project. HYDRA uses it when planning dependencies and generated modules." example="Maven" required options={BUILD_SYSTEMS.map(item => ({ value:item,label:readableIntegrationValue(item),description:INTEGRATION_OPTION_DESCRIPTIONS[item] }))}><HydraAdaptiveSelect ariaLabel="Build system" id="integration-build-system" required value={model.target.buildSystem} options={BUILD_SYSTEMS.map(item => ({ value:item,label:readableIntegrationValue(item),description:INTEGRATION_OPTION_DESCRIPTIONS[item],category:buildSystemCategory(item) }))} onChange={buildSystem => changeModel(current => ({ ...current,target:{...current.target,buildSystem:buildSystem as IntegrationProfileModel['target']['buildSystem']} }))} /></DesignerHelpedField>
                    <DesignerHelpedField label="Generated folder (relative)" targetId="integration-generated-root" help="Portable project-relative directory where generated integration artifacts are written. Use Select folder when the host project is open; HYDRA stores only the relative path so the integration remains portable." example="generated-integration" required>{generatedRootControl('integration-generated-root')}</DesignerHelpedField>
                    <DesignerHelpedField label="Integration method" targetId="integration-injection-strategy" help="Supported method used to contribute generated files, packages, plug-ins or modules to the host project." example="Add package" required options={orderedInjectionStrategies(descriptor).map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item) }))}><HydraAdaptiveSelect id="integration-injection-strategy" ariaLabel="Integration method" required value={model.injectionPolicy.strategy} options={orderedInjectionStrategies(descriptor).map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item),category:injectionCategory(item) }))} onChange={strategy => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,strategy:strategy as IntegrationProfileModel['injectionPolicy']['strategy']} }))} /></DesignerHelpedField>
                    <DesignerHelpedField className="hydra-designer-wide" label="Target notes" targetId="integration-target-notes" help="High-level guidance from the selected technology descriptor, including its intended integration style and important limitations." example="Projection only; no runtime integration."><textarea id="integration-target-notes" readOnly value={descriptor.notes.join(' ')} /></DesignerHelpedField>
                </div>
                <section className="hydra-designer-section"><header><div><h4>Safety policy</h4><p>Preview and rollback remain mandatory. The other controls define how cautiously HYDRA may update host-project files.</p></div></header><div className="integration-check-grid">
                    <DesignerHelpedCheckbox checked={model.injectionPolicy.previewRequired} disabled label="Preview mandatory" targetId="integration-preview-required" help="Requires a change preview before any target files may be changed." example="Always enabled for safe integrations." onChange={() => undefined} />
                    <DesignerHelpedCheckbox checked={model.injectionPolicy.rollbackRequired} disabled label="Rollback mandatory" targetId="integration-rollback-required" help="Requires a rollback plan and manifest for every applied integration." example="Always enabled for reversible integrations." onChange={() => undefined} />
                    <DesignerHelpedCheckbox checked={model.injectionPolicy.preserveCustomCode} label="Preserve custom code" targetId="integration-preserve-custom-code" help="Protects user-written regions while generated integration files are updated." example="Keep methods outside HYDRA generated markers." onChange={checked => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,preserveCustomCode:checked} }))} />
                    <DesignerHelpedCheckbox checked={model.injectionPolicy.allowModifyExistingFiles} label="Allow existing-file changes" targetId="integration-allow-existing-files" help="Permits the integration change plan to modify files that already exist in the host project." example="Update pom.xml to add a dependency." onChange={checked => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,allowModifyExistingFiles:checked} }))} />
                    <DesignerHelpedCheckbox checked={model.injectionPolicy.allowModifyGeneratedFiles} label="Allow generated-file changes" targetId="integration-allow-generated-files" help="Permits replacement of files previously generated and tracked by HYDRA." example="Regenerate a Sirius viewpoint descriptor." onChange={checked => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,allowModifyGeneratedFiles:checked} }))} />
                    <DesignerHelpedCheckbox checked={model.injectionPolicy.failOnPartialMappings} label="Fail on partial mappings" targetId="integration-fail-partial-mappings" help="Stops planning when any required HYDRA element has no valid host mapping." example="Recommended for runtime integration profiles." onChange={checked => changeModel(current => ({ ...current,injectionPolicy:{...current.injectionPolicy,failOnPartialMappings:checked} }))} />
                </div></section>
                <section className="hydra-designer-section integration-injection-section"><header><div><h4>Technology compilation and host integration</h4><p>Generate a native module or review a reversible change plan before any host-project modification is applied.</p></div></header>
                    <DesignerHelpedField label="Authorized output folder (relative)" targetId="integration-target-directory" help="Relative directory below the authorized integration root. Absolute paths and parent traversal are rejected." example="people-eclipse-workspace"><input id="integration-target-directory" value={injectionTarget} onChange={event => { setInjectionTarget(event.target.value); setInjectionPreview(null); }} /></DesignerHelpedField>
                    {injectionPreview !== null && <div className={`integration-injection-preview ${injectionPreview.plan.status.toLowerCase()}`}><strong>{injectionPreview.plan.status}</strong><span>{injectionPreview.plan.fileChanges.length} change(s), {injectionPreview.unchangedFiles.length} unchanged, {injectionPreview.manualConflictFiles.length} conflict(s).</span><ul>{injectionPreview.plan.fileChanges.map(change => <li key={`${change.changeKind}-${change.path}`}><strong>{change.changeKind}</strong> {change.path}</li>)}</ul>{injectionPreview.plan.diagnostics.map((item,index) => <p className={item.severity.toLowerCase()} key={`${item.code}-${index}`}><strong>{item.severity}</strong> · <span data-hydra-runtime-message="true" data-hydra-message-context="integration" data-hydra-message-code={item.code} data-hydra-message-severity={item.severity}>{item.message}</span></p>)}</div>}
                </section>
                <section className="hydra-designer-section"><header><div><h4>Enabled features</h4><p>Select only capabilities that the generated adapter must expose.</p></div><HydraButton type="button" onClick={applyPortableRuntimePreset}>Apply portable runtime preset</HydraButton></header><DesignerHelpedField className="hydra-designer-wide" label="Enabled features" targetId="integration-features" help="High-level HYDRA capabilities that this integration exposes or projects in the target technology." example="EVENT_FLOW_RUNTIME and PROJECT_EXPORT">{renderFeatureSelector()}</DesignerHelpedField></section>
                <section className="hydra-designer-section"><header><div><h4>{t('HYDRA inputs')}</h4><p>{t('Choose the project information that the integration needs to read. These entries are linked HYDRA definitions, not documents you must create manually.')}</p></div><HydraButton type="button" onClick={() => addArtifact()}>{t('+ Add input')}</HydraButton></header>
                    {model.artifacts.map((artifact,index) => <div className="hydra-designer-row-card" key={`${artifact.kind}-${index}`}><header><strong>{t('HYDRA document')} {index + 1}</strong><HydraButton className="hydra-danger-button" disabled={artifact.kind === 'METAMODEL'} type="button" onClick={() => deleteArtifact(index)}>Remove</HydraButton></header><div className="hydra-designer-row-fields">
                        <DesignerHelpedField label="Document type" targetId={`integration-artifact-kind-${index}`} help="Type of HYDRA document that this integration needs as an input." example="Event flows" required options={ARTIFACT_KINDS.map(item => ({ value:item,label:readableIntegrationValue(item),description:INTEGRATION_OPTION_DESCRIPTIONS[item] }))}><HydraAdaptiveSelect ariaLabel={`HYDRA document type ${index + 1}`} id={`integration-artifact-kind-${index}`} required value={artifact.kind} options={ARTIFACT_KINDS.map(item => ({ value:item,label:readableIntegrationValue(item),description:INTEGRATION_OPTION_DESCRIPTIONS[item],category:artifactCategory(item) }))} onChange={kind => updateArtifact(index,{...artifact,kind:kind as IntegrationArtifactKind})} /></DesignerHelpedField>
                        <DesignerHelpedField label="Document ID" targetId={`integration-artifact-document-${index}`} help="Stable identifier of the selected HYDRA input document." example="people-events" required><input aria-label={`${t('HYDRA document')} ${index + 1}`} id={`integration-artifact-document-${index}`} value={artifact.documentId} onChange={event => updateArtifact(index,{...artifact,documentId:event.target.value})} /></DesignerHelpedField>
                        <DesignerHelpedCheckbox checked={artifact.required} label="Required for a valid plan" targetId={`integration-artifact-required-${index}`} help="Marks this HYDRA document as mandatory before the integration plan can be considered executable." example="The metamodel is required; an optional notation may be omitted." onChange={checked => updateArtifact(index,{...artifact,required:checked})} />
                    </div></div>)}
                </section>
                <section className="hydra-designer-section"><header><div><h4>Host mappings</h4><p>Connect HYDRA elements to stable elements of the target technology only when the integration needs them.</p></div><HydraButton type="button" onClick={addMapping}>+ Mapping</HydraButton></header>
                    {model.mappings.length === 0 && <div className="hydra-empty-state"><strong>No mappings yet</strong><span>Runtime features remain unresolved until host elements are mapped.</span></div>}
                    {model.mappings.map((mapping,index) => <div className="hydra-designer-row-card" key={`${mapping.id}-${index}`}><header><strong>{mapping.id || `Mapping ${index + 1}`}</strong><HydraButton className="hydra-danger-button" type="button" onClick={() => changeModel(current => ({ ...current,mappings:current.mappings.filter((_,itemIndex) => itemIndex !== index) }))}>Remove</HydraButton></header><div className="hydra-designer-row-fields">
                        <DesignerHelpedField label="Mapping ID" targetId={`integration-mapping-id-${index}`} help="Stable identifier of this HYDRA-to-host mapping." example="person-class-mapping" required><input aria-label={`Mapping ID ${index + 1}`} id={`integration-mapping-id-${index}`} value={mapping.id} onChange={event => updateMapping(index,{...mapping,id:event.target.value})} /></DesignerHelpedField>
                        <DesignerHelpedField label="Mapping kind" targetId={`integration-mapping-kind-${index}`} help="Semantic kind of the element being connected." example="Type" required options={MAPPING_KINDS.map(item => ({ value:item,label:readableIntegrationValue(item),description:INTEGRATION_OPTION_DESCRIPTIONS[item] }))}><HydraAdaptiveSelect ariaLabel={`Mapping kind ${index + 1}`} id={`integration-mapping-kind-${index}`} required value={mapping.kind} options={MAPPING_KINDS.map(item => ({ value:item,label:readableIntegrationValue(item),description:INTEGRATION_OPTION_DESCRIPTIONS[item],category:mappingCategory(item) }))} onChange={kind => updateMapping(index,{...mapping,kind:kind as IntegrationMappingKind})} /></DesignerHelpedField>
                        <DesignerHelpedField label="HYDRA document" targetId={`integration-mapping-document-${index}`} help="HYDRA document that contains the source element for this mapping." example="people-metamodel"><input aria-label={`HYDRA document ${index + 1}`} id={`integration-mapping-document-${index}`} value={mapping.canonicalDocumentId} onChange={event => updateMapping(index,{...mapping,canonicalDocumentId:event.target.value})} /></DesignerHelpedField>
                        <DesignerHelpedField label="HYDRA element" targetId={`integration-mapping-element-${index}`} help="Stable HYDRA element ID represented by the host element." example="class:person" required><input aria-label={`HYDRA element ${index + 1}`} id={`integration-mapping-element-${index}`} value={mapping.canonicalElementId} onChange={event => updateMapping(index,{...mapping,canonicalElementId:event.target.value})} /></DesignerHelpedField>
                        <DesignerHelpedField label="Locator strategy" targetId={`integration-locator-${index}`} help="Target-specific method used to find the host element again during preview, application and rollback." example="BESSER element ID" required options={descriptor.locatorStrategies.map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item) }))}><HydraAdaptiveSelect ariaLabel={`Locator strategy ${index + 1}`} id={`integration-locator-${index}`} required value={mapping.hostReference.strategy} options={descriptor.locatorStrategies.map(item => ({ value:item,label:readableIntegrationValue(item),description:integrationDynamicDescription(item),category:locatorCategory(item) }))} onChange={strategy => updateMapping(index,{...mapping,hostReference:{...mapping.hostReference,strategy:strategy as IntegrationLocatorStrategy}})} /></DesignerHelpedField>
                        <DesignerHelpedField label="Host qualified name" targetId={`integration-host-qualified-${index}`} help="Qualified name, alias or namespace-dependent name of the target element." example="people.Person"><input aria-label={`Host qualified name ${index + 1}`} id={`integration-host-qualified-${index}`} value={mapping.hostReference.qualifiedName} onChange={event => updateMapping(index,{...mapping,hostReference:{...mapping.hostReference,qualifiedName:event.target.value}})} /></DesignerHelpedField>
                        <DesignerHelpedField label="Host path" targetId={`integration-host-path-${index}`} help="Document, AST or URI-fragment path when the locator strategy requires one." example="src/model/person.py#/Person"><input aria-label={`Host path ${index + 1}`} id={`integration-host-path-${index}`} value={mapping.hostReference.path} onChange={event => updateMapping(index,{...mapping,hostReference:{...mapping.hostReference,path:event.target.value}})} /></DesignerHelpedField>
                        <DesignerHelpedCheckbox checked={mapping.required} label="Required mapping" targetId={`integration-mapping-required-${index}`} help="Makes failure to resolve this host element a blocking integration diagnostic." example="The HYDRA root type must map to an existing host class." onChange={checked => updateMapping(index,{...mapping,required:checked})} />
                    </div></div>)}
                </section>
                {report !== null && <section className="hydra-designer-section integration-report"><header><div><h4>{t('Compatibility report')}</h4><p>{t(report.diagnostics.every(item => item.severity !== 'ERROR') ? 'No blocking incompatibilities.' : 'Blocking incompatibilities were found. Resolve every ERROR before compiling or applying the integration.')}</p></div></header><ul>{report.diagnostics.map((item,index) => { const advice=integrationDiagnosticAdvice(item); return <li className={item.severity.toLowerCase()} key={`${item.code}-${index}`}><strong>{t(item.severity)} · {item.code}</strong><span>{t(integrationDiagnosticDisplayMessage(item, language))}</span><div className="integration-resolution-advice"><p><strong>{t('Why:')}</strong> {t(advice.cause)}</p><p><strong>{t('How to resolve it:')}</strong> {t(advice.action)}</p>{advice.optionalAction !== undefined && <p><strong>{t('Alternative:')}</strong> {t(advice.optionalAction)}</p>}{item.path !== '' && <small>{t('Related section:')} {t(integrationRelatedSectionLabel(item.path))}</small>}</div></li>; })}</ul><h4>{t('HYDRA input documents')}</h4><ul>{report.artifactResolutions.map((item,index) => { const advice=artifactResolutionAdvice(item); return <li className={`artifact-${item.status.toLowerCase()}`} key={`${item.artifact.kind}-${index}`}><strong>{t(integrationResolutionStatusLabel(item.status))}</strong><span>{t(readableIntegrationValue(item.artifact.kind))}: {t(item.message)}</span><div className="integration-resolution-advice"><p><strong>{t('Why:')}</strong> {t(advice.cause)}</p><p><strong>{t('Next step:')}</strong> {t(advice.action)}</p>{advice.optionalAction !== undefined && <p><strong>{t('Alternative:')}</strong> {t(advice.optionalAction)}</p>}</div></li>; })}</ul></section>}
                </>}
            </main>
        </div>
        {dialogs}
        {overwriteDialogs}
    </section>;
}
