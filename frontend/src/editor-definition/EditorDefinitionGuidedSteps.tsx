import { HydraButton } from '../design-system/HydraButton';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { DesignerHelpedField, DesignerRequiredFieldsLegend } from '../designer-ui/DesignerFieldHelp';
import { HydraTooltip, type HydraGuidedStep } from '../design-system';
import type { MetaModel } from '../metamodel-designer/types';
import type { MetamodelRootDetection } from './metamodelRootDetection';
import { EditorDocumentForm } from './EditorDefinitionDesignerParts';
import type { EditorCapability, EditorDefinitionCompilationResult, EditorDefinitionModel, RepresentationDefinition } from './types';

const REPRESENTATION_KIND_OPTIONS = [
    { value: 'DIAGRAM', label: 'Diagram', description: 'Nodes and relations on a graphical canvas.' },
    { value: 'TREE', label: 'Tree', description: 'Hierarchical navigation through model elements.' },
    { value: 'TABLE', label: 'Table', description: 'Compact rows for comparing many elements.' },
    { value: 'FORM', label: 'Form', description: 'Property-oriented editing of one element.' },
    { value: 'MATRIX', label: 'Matrix', description: 'Two-dimensional relation-oriented view.' },
    { value: 'DASHBOARD', label: 'Dashboard', description: 'Summary widgets and high-level information.' },
] as const;

type DesignerView = 'GUIDED' | 'DESIGN' | 'STRUCTURE' | 'RUNTIME';

interface Options {
    model: EditorDefinitionModel;
    metamodel: MetaModel;
    detectedRoots: MetamodelRootDetection;
    etag: string;
    dirty: boolean;
    expressionDirty: boolean;
    compilingRuntime: boolean;
    runtimeResult: EditorDefinitionCompilationResult | null;
    definitionBlockingError: string;
    capabilities: EditorCapability[];
    changeModel: (model: EditorDefinitionModel) => void;
    updateRepresentation: (representation: RepresentationDefinition) => void;
    addRepresentation: () => void;
    addPaletteGroup: () => void;
    addForm: () => void;
    openAdvancedRepresentation: (id: string) => void;
    setDesignerView: (view: DesignerView) => void;
    save: () => Promise<boolean>;
    compileRuntime: () => Promise<void>;
}

/** Guided presentation extracted from the orchestration component to keep its architecture budget stable. */
export function buildEditorDefinitionGuidedSteps(options: Options): HydraGuidedStep[] {
    const { model, metamodel, detectedRoots, etag, dirty, expressionDirty, compilingRuntime, runtimeResult, definitionBlockingError, capabilities,
        changeModel, updateRepresentation, addRepresentation, addPaletteGroup, addForm, openAdvancedRepresentation, setDesignerView, save, compileRuntime } = options;
    const representationBasicsValid = model.representations.length > 0 && model.representations.every(item => item.id.trim() !== '' && item.name.trim() !== '');
    return [
        {
            id: 'identity', title: 'Editor identity', description: 'Name the generated model editor and confirm its stable identifier.',
            checks: [
                { id: 'editor-id', label: 'Stable editor ID defined', complete: model.id.trim() !== '', detail: model.id || 'Required' },
                { id: 'editor-name', label: 'Visible editor name defined', complete: model.name.trim() !== '', detail: model.name || 'Required' },
            ],
            content: <section className="editor-definition-guided-fields"><DesignerRequiredFieldsLegend /><DesignerHelpedField label="Editor ID" targetId="guided-editor-definition-id" required help="Stable document identifier. It is shared by guided, full-definition, structure and runtime views." example="people-administration-editor"><input id="guided-editor-definition-id" value={model.id} readOnly={etag !== ''} onChange={event => changeModel({ ...model, id: event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="Editor name" targetId="guided-editor-definition-name" required help="Human-readable name exposed by the generated editor." example="People administration"><input id="guided-editor-definition-name" value={model.name} onChange={event => changeModel({ ...model, name: event.target.value })} /></DesignerHelpedField></section>,
        },
        {
            id: 'representations', title: 'Representations', description: 'Define at least one way to view or edit instances of the metamodel.',
            checks: [
                { id: 'representation-count', label: 'At least one representation exists', complete: model.representations.length > 0, detail: `${model.representations.length} representation(s)` },
                { id: 'representation-basic', label: 'Representation identities are complete', complete: representationBasicsValid, detail: representationBasicsValid ? 'IDs and names are valid' : 'Open the full designer to complete the red items' },
            ],
            content: <section className="editor-definition-guided-list editor-guided-representations"><header><div><h4>Views users will see</h4><p>Choose a name, a view type and its main root. Configuration that does not apply to the chosen type stays hidden.</p></div><HydraTooltip label="About editor representations" title="Editor representations"><p>A single metamodel may expose several viewpoints. Guided mode keeps only the decisions that normally matter; detailed mappings remain available in Definition.</p></HydraTooltip></header><div>{model.representations.map(item => {
                const rootOptions = [{ value: '', label: item.rootClassIds.length > 1 ? 'Multiple roots (advanced)' : 'Automatic root', category: 'Recommended', description: 'Use the metamodel root detected by HYDRA.' }, ...metamodel.classes.filter(metaClass => metaClass.kind === 'CLASS').map(metaClass => ({ value: metaClass.id, label: metaClass.name, category: metaClass.id === detectedRoots.primaryClassId ? 'Recommended' : 'Metamodel classes', description: metaClass.id === detectedRoots.primaryClassId ? 'Root class automatically detected by HYDRA.' : `Open ${metaClass.name} instances as roots.` }))];
                return <article className="editor-guided-representation-card" key={item.id}><div className="editor-guided-representation-heading"><strong>{item.name || 'Unnamed representation'}</strong><span>{item.nodeMappings.length} element mapping(s) · {item.edgeMappings.length} relation mapping(s)</span></div><div className="editor-guided-card-fields"><DesignerHelpedField label="View name" targetId={`guided-representation-name-${item.id}`} required help="Visible name used by people who open this view." example="Main diagram"><input id={`guided-representation-name-${item.id}`} value={item.name} onChange={event => updateRepresentation({ ...item, name: event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="View type" targetId={`guided-representation-kind-${item.id}`} required help="The presentation style. Detailed controls adapt to this choice." example="Diagram"><HydraAdaptiveSelect id={`guided-representation-kind-${item.id}`} ariaLabel="View type" value={item.kind} options={REPRESENTATION_KIND_OPTIONS} onChange={kind => updateRepresentation({ ...item, kind: kind as RepresentationDefinition['kind'] })} /></DesignerHelpedField><DesignerHelpedField label="Main root" targetId={`guided-representation-root-${item.id}`} help="The class users normally open first in this view." example="Project"><HydraAdaptiveSelect id={`guided-representation-root-${item.id}`} ariaLabel="Main root" value={item.rootClassIds.length === 1 ? item.rootClassIds[0] : ''} options={rootOptions} onChange={rootClassId => updateRepresentation({ ...item, rootClassIds: rootClassId === '' ? (detectedRoots.primaryClassId === '' ? item.rootClassIds : [detectedRoots.primaryClassId]) : [rootClassId] })} /></DesignerHelpedField></div><HydraButton type="button" onClick={() => openAdvancedRepresentation(item.id)}>Advanced configuration</HydraButton></article>;
            })}</div><HydraButton type="button" onClick={addRepresentation}>+ Representation</HydraButton></section>,
        },
        {
            id: 'tools-forms', title: 'Tools and forms', description: 'Prepare creation tools and property forms for the generated editor.',
            checks: [
                { id: 'palette-groups', label: 'Palette strategy reviewed', complete: model.palette.groups.length > 0 || model.palette.tools.length === 0, detail: `${model.palette.groups.length} group(s) · ${model.palette.tools.length} tool(s)` },
                { id: 'forms', label: 'Form strategy reviewed', complete: true, detail: `${model.forms.length} generated form(s)` },
            ],
            content: <section className="editor-definition-guided-actions"><article><strong>Palette</strong><span>{model.palette.groups.length} group(s) · {model.palette.tools.length} tool(s)</span><HydraButton type="button" onClick={addPaletteGroup}>+ Palette group</HydraButton></article><article><strong>Forms</strong><span>{model.forms.length} form(s)</span><HydraButton type="button" onClick={addForm}>+ Form</HydraButton></article><article><strong>Advanced mappings and notation</strong><span>Node/edge mappings, containers, SVG/PNG figures and detailed palette tools stay in the full editor.</span><HydraButton type="button" onClick={() => setDesignerView('DESIGN')}>Open full definition</HydraButton></article></section>,
        },
        {
            id: 'capabilities', title: 'Runtime capabilities', description: 'Review which services the generated editor exposes.',
            checks: [
                { id: 'model-editing', label: 'Capability manifest is defined', complete: model.capabilityManifest.capabilities.length > 0, detail: `${model.capabilityManifest.capabilities.length} capability(ies)` },
                { id: 'runtime-policy', label: 'Runtime limits are valid', complete: model.runtimePolicy.maximumOpenRepresentations >= 1 && model.runtimePolicy.operationTimeoutMillis >= 100, detail: `${model.runtimePolicy.maximumOpenRepresentations} open view(s) · ${model.runtimePolicy.operationTimeoutMillis} ms operation timeout` },
            ],
            content: <section className="editor-definition-guided-capabilities"><EditorDocumentForm model={model} etag={etag} capabilities={capabilities} onChange={changeModel} showHeader={false} /></section>,
        },
        {
            id: 'review', title: 'Verify definition', description: 'Run the editor-definition validation before saving and compiling.',
            checks: [
                { id: 'definition-valid', label: 'Editor definition has no blocking errors', complete: definitionBlockingError === '', detail: definitionBlockingError || 'Definition is internally consistent' },
                { id: 'saved', label: 'Definition is saved before compilation', complete: etag !== '' && !dirty, detail: dirty ? 'Save pending changes' : etag === '' ? 'Save the new editor' : 'Saved' },
            ],
            content: <section className="editor-definition-guided-review"><strong>{definitionBlockingError === '' ? 'The definition can be saved and compiled.' : 'Resolve the blocking definition error.'}</strong><p>{definitionBlockingError || 'Use Structure tree for a neutral inspection, or the full Definition view for detailed mappings and notation.'}</p><div><HydraButton type="button" onClick={() => setDesignerView('STRUCTURE')}>Inspect structure</HydraButton><HydraButton type="button" disabled={!dirty} onClick={() => void save()}>Save editor</HydraButton></div></section>,
        },
        {
            id: 'runtime', title: 'Compile and deploy', description: 'Compile immutable runtime indexes, test the editor and export it as ZIP, Docker or Kubernetes.',
            checks: [{ id: 'saved-runtime', label: 'Saved source definition available', complete: etag !== '' && !dirty, detail: dirty ? 'Save changes first' : etag === '' ? 'No saved definition yet' : 'Ready to compile' }],
            content: <section className="editor-definition-guided-runtime"><p>Compilation never modifies the authoring definition. The Runtime preview keeps its own guided deployment assistant and the original direct export controls.</p><HydraButton type="button" disabled={dirty || expressionDirty || etag === '' || compilingRuntime} onClick={() => void compileRuntime()}>{compilingRuntime ? 'Compiling…' : runtimeResult === null ? 'Compile runtime' : 'Recompile runtime'}</HydraButton>{runtimeResult !== null && <HydraButton type="button" onClick={() => setDesignerView('RUNTIME')}>Open runtime and deployment</HydraButton>}</section>,
        },
    ];
}
