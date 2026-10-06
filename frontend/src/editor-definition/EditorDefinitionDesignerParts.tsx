import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import {
    DesignerGuideButton,
    DesignerHelpedCheckbox,
    DesignerHelpedField,
    DesignerRequiredFieldsLegend
} from '../designer-ui/DesignerFieldHelp';
import type { MetaModel } from '../metamodel-designer/types';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { createEditorDefinitionId } from './editorDefinitionFactory';
import { ProjectImageFileField } from './ProjectImageFileField';
import { PaletteGroupForm as PaletteGroupFormV43222, ToolForm as ToolFormV43222 } from './EditorPaletteToolForm';
import type { EditorDefinitionTreeInspection } from './EditorDefinitionTree';
import { resolveEdgeNotationStyle, resolveNodeNotationStyle, updateNodeStyle } from './editorNotationStyle';
import { compatibleContainmentRelations, detectMetamodelRoots } from './metamodelRootDetection';
import { ConditionalStyleEditor } from './ConditionalStyleEditor';
import { ConditionalEdgeStyleEditor } from './ConditionalEdgeStyleEditor';
import { EDITOR_CAPABILITY_HELP, capabilityHelp } from './editorCapabilityHelp';
import { edgeMappingDisplayName, semanticEdgeClassName, semanticEdgeFeatureSummary } from './editorSemanticEdge';
import { RepresentationElementMappingPanel } from './RepresentationElementMappingPanel';
import { EditorEdgeEndpointStyleFields } from './EditorEdgeEndpointStyleFields';
import type { ExpressionModel } from '../validation/types';
import type {
    EdgeMapping,
    EditorCapability,
    EditorDefinitionModel,
    FormDefinition,
    FormFieldDefinition,
    FormSectionDefinition,
    NodeMapping,
    RepresentationDefinition
} from './types';

import { EDGE_LINE_OPTIONS, NODE_SHAPE_OPTIONS, REPRESENTATION_KIND_OPTIONS } from './editorDefinitionPresentationOptions';

const WIDGET_OPTIONS = [
    { value: 'TEXT', label: 'Single-line text', description: 'Short text input.' },
    { value: 'TEXTAREA', label: 'Multi-line text', description: 'Long text input.' },
    { value: 'NUMBER', label: 'Number', description: 'Numeric input with validation.' },
    { value: 'BOOLEAN', label: 'Checkbox', description: 'True or false input.' },
    { value: 'ENUM', label: 'Enumeration', description: 'Choose a literal from the attribute enumeration.' },
    { value: 'REFERENCE', label: 'Model reference', description: 'Select another model element.' },
    { value: 'MULTI_REFERENCE', label: 'Multiple references', description: 'Select several model elements.' },
    { value: 'DATE', label: 'Date', description: 'Calendar date input.' },
    { value: 'COLOR', label: 'Color', description: 'Portable color input.' },
    { value: 'FILE', label: 'File', description: 'File or asset reference.' },
    { value: 'CODE', label: 'Code', description: 'Multi-line source editor.' },
    { value: 'EXPRESSION', label: 'Expression', description: 'Neutral expression source.' },
    { value: 'READ_ONLY', label: 'Read-only value', description: 'Displays a value without allowing edits.' },
    { value: 'CUSTOM', label: 'Custom', description: 'Widget resolved by a runtime extension.' }
] as const;


const CAPABILITY_PRESET_OPTIONS = [
    { value: 'STANDARD', label: 'Standard editor', description: 'Model editing, import/export and undo/redo.' },
    { value: 'VALIDATED', label: 'Validated editor', description: 'Standard editing plus validation and quick fixes.' },
    { value: 'EVENT_DRIVEN', label: 'Event-driven editor', description: 'Validated editing plus operations, events and EventFlow.' },
    { value: 'FULL', label: 'Full feature set', description: 'Enable every capability supported by this HYDRA build.' },
    { value: 'CUSTOM', label: 'Custom', description: 'The capability set was customized manually.' }
] as const;

function detectCapabilityPreset(selected: readonly EditorCapability[], available: readonly EditorCapability[]): string {
    const same = (values: readonly EditorCapability[]): boolean => values.length === selected.length && values.every(value => selected.includes(value));
    if (same(['MODEL_EDITING', 'IMPORT', 'EXPORT', 'UNDO_REDO'])) return 'STANDARD';
    if (same(['MODEL_EDITING', 'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'IMPORT', 'EXPORT', 'UNDO_REDO'])) return 'VALIDATED';
    if (same(['MODEL_EDITING', 'OPERATIONS', 'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'EVENTS', 'EVENT_FLOWS', 'IMPORT', 'EXPORT', 'UNDO_REDO'])) return 'EVENT_DRIVEN';
    if (same(available)) return 'FULL';
    return 'CUSTOM';
}

export function StructureInspectionSummary({ item }: { item: EditorDefinitionTreeInspection }) {
    return <section className="editor-structure-inspection-summary">
        <h3>{item.label}</h3>
        <p>{item.detail ?? 'Generated editor structure element'}</p>
        <dl><dt>Kind</dt><dd>{item.badge ?? 'STRUCTURE'}</dd><dt>Tree ID</dt><dd>{item.id}</dd></dl>
        <small>Semantic metamodel elements remain editable in the metamodel designer. Mappings, representations, tools, groups and forms are edited directly from this properties tab.</small>
    </section>;
}

export function EditorDocumentForm({ model, etag, capabilities, onChange, showHeader = true }: { model: EditorDefinitionModel; etag: string; capabilities: EditorCapability[]; onChange: (model: EditorDefinitionModel) => void; showHeader?: boolean }) {
    const { t } = useHydraLanguage();
    const [advancedOpen, setAdvancedOpen] = useState(false);
    const selectedCapabilities = model.capabilityManifest.capabilities;
    const capabilityEnabled = (capability: EditorCapability): boolean => selectedCapabilities.includes(capability);
    const preset = detectCapabilityPreset(selectedCapabilities, capabilities);

    function applyPreset(nextPreset: string): void {
        if (nextPreset === 'CUSTOM') return;
        const nextCapabilities: EditorCapability[] = nextPreset === 'STANDARD'
            ? ['MODEL_EDITING', 'IMPORT', 'EXPORT', 'UNDO_REDO']
            : nextPreset === 'VALIDATED'
                ? ['MODEL_EDITING', 'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'IMPORT', 'EXPORT', 'UNDO_REDO']
                : nextPreset === 'EVENT_DRIVEN'
                    ? ['MODEL_EDITING', 'OPERATIONS', 'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'EVENTS', 'EVENT_FLOWS', 'IMPORT', 'EXPORT', 'UNDO_REDO']
                    : [...capabilities];
        onChange({
            ...model,
            capabilityManifest: { capabilities: nextCapabilities },
            runtimePolicy: {
                ...model.runtimePolicy,
                validationTriggers: nextCapabilities.includes('VALIDATION_RUNTIME') ? model.runtimePolicy.validationTriggers.length === 0 ? ['MANUAL'] : model.runtimePolicy.validationTriggers : [],
                publishEvents: nextCapabilities.includes('EVENTS') || nextCapabilities.includes('EVENT_FLOWS') || nextCapabilities.includes('GAMIFICATION'),
                eventFlowsEnabled: nextCapabilities.includes('EVENT_FLOWS'),
                gamificationEnabled: nextCapabilities.includes('GAMIFICATION')
            }
        });
    }

    function toggleCapability(capability: EditorCapability): void {
        const enabled = capabilityEnabled(capability);
        const nextCapabilities = enabled ? selectedCapabilities.filter(item => item !== capability) : [...selectedCapabilities, capability];
        onChange({
            ...model,
            capabilityManifest: { capabilities: nextCapabilities },
            runtimePolicy: {
                ...model.runtimePolicy,
                validationTriggers: capability === 'VALIDATION_RUNTIME' && enabled ? [] : model.runtimePolicy.validationTriggers,
                publishEvents: capability === 'EVENTS' && enabled && !nextCapabilities.includes('EVENT_FLOWS') && !nextCapabilities.includes('GAMIFICATION') ? false : model.runtimePolicy.publishEvents,
                eventFlowsEnabled: capability === 'EVENT_FLOWS' && enabled ? false : model.runtimePolicy.eventFlowsEnabled,
                gamificationEnabled: capability === 'GAMIFICATION' && enabled ? false : model.runtimePolicy.gamificationEnabled
            }
        });
    }

    return <section className="editor-document-settings editor-progressive-form">
        {showHeader && <header className="editor-progressive-header"><div><h3>Editor settings</h3><p>Configure the editor at a high level first. Technical runtime options appear only when they are relevant.</p></div><HydraButton className="editor-progressive-toggle" type="button" onClick={() => setAdvancedOpen(value => !value)}>{advancedOpen ? 'Hide advanced' : 'Advanced options'}</HydraButton></header>}
        {!showHeader && <div className="editor-progressive-inline-actions"><HydraButton className="editor-progressive-toggle" type="button" onClick={() => setAdvancedOpen(value => !value)}>{advancedOpen ? t('Hide advanced') : t('Advanced options')}</HydraButton></div>}
        <div className="hydra-designer-fields editor-essential-fields">
            <DesignerHelpedField label="Editor name" targetId="editor-definition-name" required help="Visible name shown when users choose a viewpoint or model editor." example="People administration"><input id="editor-definition-name" value={model.name} onChange={event => onChange({ ...model, name: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="Editor profile" targetId="editor-capability-preset" help="Choose a high-level runtime profile. You can customize individual capabilities afterwards." example="Validated editor"><HydraAdaptiveSelect id="editor-capability-preset" ariaLabel="Editor profile" value={preset} options={CAPABILITY_PRESET_OPTIONS} onChange={applyPreset} /></DesignerHelpedField>
            <DesignerHelpedCheckbox checked={model.runtimePolicy.autosave} label="Autosave" targetId="editor-runtime-autosave" help="Persists committed semantic changes automatically." example="Recommended for normal editing." onChange={autosave => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, autosave } })} />
            <DesignerHelpedCheckbox checked={model.runtimePolicy.recoverLatestSession} label="Recover latest session" targetId="editor-runtime-recover" help="Restores the latest persisted session after restart." example="Useful for desktop and standalone editors." onChange={recoverLatestSession => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, recoverLatestSession } })} />
        </div>

        {capabilityEnabled('VALIDATION_RUNTIME') && <section className="editor-progressive-context"><header><strong>Validation</strong><span>Visible because validation runtime is enabled.</span></header><DesignerHelpedField className="hydra-designer-wide" label="Validation triggers" targetId="editor-validation-triggers" help="Choose when validation runs." example="On change and manual"><div id="editor-validation-triggers" className="editor-capabilities compact-capabilities">{(['ON_OPEN','ON_CHANGE','ON_REPRESENTATION_CHANGE','MANUAL'] as const).map(trigger => <label className="hydra-inline-checkbox" key={trigger}><input type="checkbox" checked={model.runtimePolicy.validationTriggers.includes(trigger)} onChange={() => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, validationTriggers: model.runtimePolicy.validationTriggers.includes(trigger) ? model.runtimePolicy.validationTriggers.filter(item => item !== trigger) : [...model.runtimePolicy.validationTriggers, trigger] } })} />{trigger}</label>)}</div></DesignerHelpedField></section>}

        {(capabilityEnabled('EVENTS') || capabilityEnabled('EVENT_FLOWS') || capabilityEnabled('GAMIFICATION')) && <section className="editor-progressive-context"><header><strong>Events and reactions</strong><span>Only options supported by the selected capabilities are shown.</span></header><div className="editor-progressive-checkbox-grid">
            <DesignerHelpedCheckbox checked={model.runtimePolicy.publishEvents} label="Publish editor events" targetId="editor-runtime-events" help="Publishes neutral events after a semantic commit succeeds." example="Required by EventFlow or gamification evidence." onChange={publishEvents => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, publishEvents } })} />
            {capabilityEnabled('EVENT_FLOWS') && <DesignerHelpedCheckbox checked={model.runtimePolicy.eventFlowsEnabled} label="Enable EventFlow" targetId="editor-runtime-flows" help="Consumes editor events through configured EventFlows." example="Shown because EVENT_FLOWS is enabled." onChange={eventFlowsEnabled => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, eventFlowsEnabled } })} />}
            {capabilityEnabled('GAMIFICATION') && <DesignerHelpedCheckbox checked={model.runtimePolicy.gamificationEnabled} label="Enable gamification" targetId="editor-runtime-gamification" help="Uses verified runtime evidence to update the gamification ledger." example="Shown because GAMIFICATION is enabled." onChange={gamificationEnabled => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, gamificationEnabled } })} />}
        </div></section>}

        {advancedOpen && <section className="editor-progressive-advanced">
            <header><div><strong>Advanced editor contract</strong><span>Stable IDs, individual capabilities and runtime limits.</span></div></header>
            <div className="hydra-designer-fields">
                <DesignerHelpedField label="Editor ID" targetId="editor-definition-id" required help="Stable identifier for an editing configuration. Create another configuration only when the same metamodel needs a genuinely different editing workflow, palette, forms or behavior. Alternative visual styles belong as representations inside the same configuration." example="people-administration-editor"><input id="editor-definition-id" aria-label="Editor ID" value={model.id} readOnly={etag !== ''} onChange={event => onChange({ ...model, id: event.target.value })} /></DesignerHelpedField>
                {capabilityEnabled('MULTIPLE_REPRESENTATIONS') && <DesignerHelpedField label="Open representations" targetId="editor-runtime-open-representations" required help="Maximum number of simultaneous viewpoints retained by one session." example="2"><input id="editor-runtime-open-representations" type="number" min={1} max={8} value={model.runtimePolicy.maximumOpenRepresentations} onChange={event => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, maximumOpenRepresentations: Math.max(1, Number(event.target.value) || 1) } })} /></DesignerHelpedField>}
                {capabilityEnabled('OPERATIONS') && <DesignerHelpedField label="Operation timeout (ms)" targetId="editor-runtime-timeout" required help="Upper bound declared for long-running operation adapters." example="30000"><input id="editor-runtime-timeout" type="number" min={100} value={model.runtimePolicy.operationTimeoutMillis} onChange={event => onChange({ ...model, runtimePolicy: { ...model.runtimePolicy, operationTimeoutMillis: Math.max(100, Number(event.target.value) || 100) } })} /></DesignerHelpedField>}
                <DesignerHelpedField className="hydra-designer-wide" label="Capabilities" targetId="editor-capabilities" help="Choose what people can actually do in the generated editor. Profiles above select sensible combinations automatically; customize individual capabilities only when the editor needs a different behavior." example="Validated editor: editing, validation, quick fixes, import/export and undo/redo" options={EDITOR_CAPABILITY_HELP}><div className="editor-capabilities compact-capabilities editor-capability-cards" id="editor-capabilities" tabIndex={-1}>{capabilities.map(capability => { const help = capabilityHelp(capability); return <label className="hydra-inline-checkbox editor-capability-card" key={capability}><input type="checkbox" checked={capabilityEnabled(capability)} onChange={() => toggleCapability(capability)} /><span><strong>{t(help.label)}</strong><small>{t(help.description)}</small></span></label>; })}</div></DesignerHelpedField>
            </div>
        </section>}
    </section>;
}

export function RepresentationForm({ representation, metamodel, model, detectedRoots, onChange, onRenameId, onAddClassTool, onAddRelationTool, onEditNodeMapping, onEditEdgeMapping, onDelete }: { representation: RepresentationDefinition; metamodel: MetaModel; model: EditorDefinitionModel; detectedRoots: ReturnType<typeof detectMetamodelRoots>; onChange: (value: RepresentationDefinition) => void; onRenameId: (previousId: string, nextId: string) => void; onAddClassTool: (classId: string, className: string) => void; onAddRelationTool: (relationId: string) => void; onEditNodeMapping: (mappingId: string) => void; onEditEdgeMapping: (mappingId: string) => void; onDelete: () => void }) {
    const { t } = useHydraLanguage();
    return <section className="representation-editor editor-progressive-form">
        <div className="hydra-designer-form-header"><div><h3>{representation.name}</h3><p>{t(representationKindLabel(representation.kind))}</p></div><div className="hydra-designer-form-actions"><DesignerGuideButton kind="REPRESENTATION" /><DesignerHelpedCheckbox checked={representation.editable} className="compact" label="Editable" targetId="representation-editable" help="Allows users to create, edit and remove model elements from this representation." example="Disable for read-only viewpoints." onChange={checked => onChange({ ...representation, editable: checked })} /><HydraButton className="hydra-danger-button" type="button" disabled={model.representations.length === 1} onClick={onDelete}>Delete representation</HydraButton></div></div>
        <DesignerRequiredFieldsLegend />
        <div className="hydra-designer-fields editor-essential-fields">
            <DesignerHelpedField label="Name" targetId="representation-name" required help="Visible name of this view." example="Main diagram"><input id="representation-name" value={representation.name} onChange={event => onChange({ ...representation, name: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="View type" targetId="representation-kind" required help="Choose how model instances are presented. HYDRA only shows configuration relevant to this choice." example="Diagram" options={[...REPRESENTATION_KIND_OPTIONS]}><HydraAdaptiveSelect id="representation-kind" ariaLabel="Representation kind" value={representation.kind} options={REPRESENTATION_KIND_OPTIONS} onChange={kind => onChange({ ...representation, kind: kind as RepresentationDefinition['kind'] })} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Root classes" targetId="representation-root-classes" help="Classes whose instances can be opened as roots. HYDRA proposes one automatically." example="Organization"><div className="representation-root-classes compact-capabilities" id="representation-root-classes" tabIndex={-1}>{metamodel.classes.map(metaClass => <label className="hydra-inline-checkbox" key={metaClass.id}><input type="checkbox" checked={representation.rootClassIds.includes(metaClass.id)} onChange={() => onChange({ ...representation, rootClassIds: representation.rootClassIds.includes(metaClass.id) ? representation.rootClassIds.filter(id => id !== metaClass.id) : [...representation.rootClassIds, metaClass.id] })} /> {metaClass.name}{detectedRoots.primaryClassId === metaClass.id && <em>automatic</em>}</label>)}</div></DesignerHelpedField>
        </div>
        <div className="representation-root-suggestion compact"><div><strong>Suggested root</strong><span>{rootDetectionDescriptionForUi(detectedRoots, metamodel, t)}</span></div><HydraButton type="button" disabled={detectedRoots.primaryClassId === '' || representation.rootClassIds.length === 1 && representation.rootClassIds[0] === detectedRoots.primaryClassId} onClick={() => onChange({ ...representation, rootClassIds: detectedRoots.primaryClassId === '' ? [] : [detectedRoots.primaryClassId] })}>Use suggestion</HydraButton></div>

        <RepresentationElementMappingPanel representation={representation} metamodel={metamodel} onAddClass={onAddClassTool} onAddRelation={onAddRelationTool} onEditNode={onEditNodeMapping} onEditEdge={onEditEdgeMapping} />
        <details className="editor-progressive-details"><summary>Technical representation settings</summary><div className="hydra-designer-fields"><DesignerHelpedField label="Representation ID" targetId="representation-id" required help="Stable identifier referenced by mappings, palette tools and persisted diagrams." example="representation:main-diagram"><input id="representation-id" value={representation.id} onChange={event => onRenameId(representation.id, event.target.value)} /></DesignerHelpedField></div></details>
    </section>;
}

export function NodeMappingForm({ mapping, metamodel, expressionModel, onChange, onExpressionModelChange, onRenameId, onDelete }: { mapping: NodeMapping; metamodel: MetaModel; expressionModel:ExpressionModel; onChange: (mapping: NodeMapping) => void; onExpressionModelChange:(model:ExpressionModel)=>void; onRenameId: (previousId: string, nextId: string) => void; onDelete: () => void }) {
    const containmentRelations = compatibleContainmentRelations(metamodel, mapping.targetClassId);
    const imageBacked = mapping.shape === 'PROJECT_IMAGE';
    function changeMappedClass(targetClassId: string): void {
        const available = compatibleContainmentRelations(metamodel, targetClassId);
        onChange({ ...mapping, targetClassId, containmentRelationIds: mapping.container ? available.map(item => item.id) : [] });
    }
    function toggleContainer(container: boolean): void {
        onChange({ ...mapping, container, containmentRelationIds: container ? containmentRelations.map(item => item.id) : [] });
    }
    return <section className="representation-editor editor-progressive-form">
        <div className="hydra-designer-form-header"><div><h3>{mapping.representationName}</h3><p>{metamodel.classes.find(item => item.id === mapping.targetClassId)?.name ?? 'Model element'} · {mapping.shape}</p></div><div className="hydra-designer-form-actions"><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete mapping</HydraButton></div></div>
        <DesignerRequiredFieldsLegend />
        <div className="hydra-designer-fields editor-essential-fields">
            <DesignerHelpedField label="Mapped class" targetId="node-mapping-class" required help="Metamodel class represented by this element." example="Person" options={metamodel.classes.map(item => ({ value: item.id, label: item.name, description: `Instances of ${item.name} use this visual representation.` }))}><HydraAdaptiveSelect id="node-mapping-class" ariaLabel="Mapped class" value={mapping.targetClassId} options={metamodel.classes.map(item => ({ value: item.id, label: item.name, category: item.kind === 'CLASS' ? 'Concrete classes' : 'Other classifiers', description: `Instances of ${item.name} use this visual representation.` }))} onChange={changeMappedClass} /></DesignerHelpedField>
            <DesignerHelpedField label="Visible name" targetId="node-mapping-name" required help="Name shown for this element in editor configuration and diagrams." example="Person"><input id="node-mapping-name" value={mapping.representationName} onChange={event => onChange({ ...mapping, representationName: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="Shape" targetId="node-mapping-shape" required help="Choose the visual shape. Image controls appear for project image choices." example="Rounded rectangle" options={[...NODE_SHAPE_OPTIONS]}><HydraAdaptiveSelect id="node-mapping-shape" ariaLabel="Node shape" value={mapping.shape} options={NODE_SHAPE_OPTIONS} onChange={shape => onChange({ ...mapping, shape: shape as NodeMapping['shape'], imageAssetPath: shape === 'PROJECT_IMAGE' ? mapping.imageAssetPath : '' })} /></DesignerHelpedField>
            <DesignerHelpedCheckbox checked={mapping.container === true} label="Can contain other elements" targetId="node-mapping-container" help="Show containment controls only when this element acts as a visual container." example="A Department contains Employee nodes." onChange={toggleContainer} />
            {imageBacked && <><ProjectImageFileField id="node-mapping-image" label="Project image" required help="Choose an SVG, PNG, JPEG, GIF or WebP from your computer. HYDRA stores it in the active project automatically, so you never need to type a path." value={mapping.imageAssetPath ?? ''} onChange={imageAssetPath => onChange({ ...mapping, imageAssetPath })} /><DesignerHelpedField label="Image fit" targetId="node-mapping-image-fit" required help="How the selected image fills the node." example="Contain"><HydraAdaptiveSelect id="node-mapping-image-fit" ariaLabel="Image fit" value={mapping.imageFit ?? 'CONTAIN'} options={[{ value: 'CONTAIN', label: 'Contain', description: 'Show the complete image while preserving proportions.' }, { value: 'COVER', label: 'Cover', description: 'Fill the node and crop overflow.' }, { value: 'FILL', label: 'Fill', description: 'Stretch the image to the node bounds.' }, { value: 'NONE', label: 'Natural size', description: 'Keep the natural image size.' }]} onChange={imageFit => onChange({ ...mapping, imageFit: imageFit as NodeMapping['imageFit'] })} /></DesignerHelpedField></>}
            {mapping.container && <><DesignerHelpedField className="hydra-designer-wide" label="Accepted containment relations" targetId="node-mapping-containment-relations" required help="Only children connected through these containment relations may appear inside this element." example="Department.employees"><div className="representation-root-classes compact-capabilities" id="node-mapping-containment-relations" tabIndex={-1}>{containmentRelations.map(relation => <label className="hydra-inline-checkbox" key={relation.id}><input type="checkbox" checked={(mapping.containmentRelationIds ?? []).includes(relation.id)} onChange={() => onChange({ ...mapping, containmentRelationIds: (mapping.containmentRelationIds ?? []).includes(relation.id) ? (mapping.containmentRelationIds ?? []).filter(id => id !== relation.id) : [...(mapping.containmentRelationIds ?? []), relation.id] })} /> {relation.name || relation.id} <small>{metamodel.classes.find(item => item.id === relation.targetTypeId)?.name ?? relation.targetTypeId}</small></label>)}{containmentRelations.length === 0 && <span>No compatible containment relations are defined by the metamodel.</span>}</div></DesignerHelpedField><DesignerHelpedField label="Container padding" targetId="node-mapping-container-padding" required help="Internal spacing around contained nodes." example="18"><input id="node-mapping-container-padding" type="number" min={1} value={mapping.containerPadding ?? 18} onChange={event => onChange({ ...mapping, containerPadding: Math.max(1, Number(event.target.value) || 1) })} /></DesignerHelpedField></>}
        </div>
        <details className="editor-progressive-details"><summary>Size, style and technical mapping settings</summary><div className="hydra-designer-fields">
            <DesignerHelpedField label="Node mapping ID" targetId="node-mapping-id" required help="Stable identifier of the visual mapping." example="node-mapping:person"><input id="node-mapping-id" value={mapping.id} onChange={event => onRenameId(mapping.id, event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Default width" targetId="node-mapping-width" required help="Initial node width in pixels." example="220"><input id="node-mapping-width" type="number" min={1} value={mapping.defaultWidth} onChange={event => onChange({ ...mapping, defaultWidth: Number(event.target.value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Default height" targetId="node-mapping-height" required help="Initial node height in pixels." example="140"><input id="node-mapping-height" type="number" min={1} value={mapping.defaultHeight} onChange={event => onChange({ ...mapping, defaultHeight: Number(event.target.value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Minimum width" targetId="node-mapping-min-width" required help="Smallest width permitted by the editor." example="120"><input id="node-mapping-min-width" type="number" min={1} value={mapping.minimumWidth} onChange={event => onChange({ ...mapping, minimumWidth: Number(event.target.value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Minimum height" targetId="node-mapping-min-height" required help="Smallest height permitted by the editor." example="80"><input id="node-mapping-min-height" type="number" min={1} value={mapping.minimumHeight} onChange={event => onChange({ ...mapping, minimumHeight: Number(event.target.value) })} /></DesignerHelpedField>
            <NodeNotationStyleFields mapping={mapping} onChange={onChange} />
        <ConditionalStyleEditor mapping={mapping} metamodel={metamodel} expressionModel={expressionModel} onMappingChange={onChange} onExpressionModelChange={onExpressionModelChange} />
            <DesignerHelpedField className="hydra-designer-wide" label="Node label" targetId="node-mapping-label-expression" help="Choose a configured expression for the visible label, or use the default element label." example="Default label" options={expressionSelectOptions(expressionModel)}><HydraAdaptiveSelect id="node-mapping-label-expression" ariaLabel="Node label expression" value={mapping.labelExpressionId} options={expressionSelectOptions(expressionModel)} onChange={labelExpressionId => onChange({ ...mapping, labelExpressionId })} /></DesignerHelpedField>
        </div></details>
    </section>;
}

export function EdgeMappingForm({ mapping, metamodel, expressionModel, onChange, onExpressionModelChange, onRenameId, onDelete }: { mapping: EdgeMapping; metamodel: MetaModel; expressionModel: ExpressionModel; onChange: (mapping: EdgeMapping) => void; onExpressionModelChange: (model: ExpressionModel) => void; onRenameId: (previousId: string, nextId: string) => void; onDelete: () => void }) {
    const { t } = useHydraLanguage();
    const semantic = mapping.semanticEdge;
    return <section className="representation-editor editor-progressive-form"><div className="hydra-designer-form-header"><div><h3>{edgeMappingDisplayName(metamodel, mapping)}</h3><p>{metamodel.classes.find(item => item.id === mapping.sourceClassId)?.name ?? mapping.sourceClassId} → {metamodel.classes.find(item => item.id === mapping.targetClassId)?.name ?? mapping.targetClassId}</p></div><div className="hydra-designer-form-actions"><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>{t('Delete mapping')}</HydraButton></div></div><DesignerRequiredFieldsLegend /><div className="hydra-designer-fields editor-essential-fields">
        {semantic === undefined ? <DesignerHelpedField label="Relationship" targetId="edge-mapping-relation" required help="Metamodel relation represented by this connection. Endpoints are updated automatically." example="Works for" options={metamodel.relations.map(item => ({ value: item.id, label: item.name || 'Unnamed relation', description: `Connects ${metamodel.classes.find(type => type.id === item.sourceTypeId)?.name ?? 'source elements'} to ${metamodel.classes.find(type => type.id === item.targetTypeId)?.name ?? 'target elements'}.` }))}><HydraAdaptiveSelect id="edge-mapping-relation" ariaLabel="Model relation" value={mapping.relationId} options={metamodel.relations.map(item => ({ value: item.id, label: item.name || 'Unnamed relation', category: item.kind === 'CONTAINMENT' ? 'Containment relations' : 'References and associations', description: `Connects ${metamodel.classes.find(type => type.id === item.sourceTypeId)?.name ?? 'source elements'} to ${metamodel.classes.find(type => type.id === item.targetTypeId)?.name ?? 'target elements'}.` }))} onChange={relationId => { const nextRelation = metamodel.relations.find(item => item.id === relationId); if (nextRelation !== undefined) onChange({ ...mapping, relationId: nextRelation.id, semanticEdge: undefined, sourceClassId: nextRelation.sourceTypeId, targetClassId: nextRelation.targetTypeId }); }} /></DesignerHelpedField> : <div className="editor-context-note hydra-designer-wide"><strong>{t('Semantic connection object')}</strong><span>{t('This imported connection is a real model element, not a direct reference. HYDRA preserves its endpoint features instead of inventing a different metamodel relationship.')}</span><small>{semanticEdgeClassName(metamodel, semantic)} · {semanticEdgeFeatureSummary(metamodel, semantic)}</small></div>}
        <DesignerHelpedField label="Line routing" targetId="edge-mapping-line" required help="How the connection is routed visually." example="Orthogonal route" options={[...EDGE_LINE_OPTIONS]}><HydraAdaptiveSelect id="edge-mapping-line" ariaLabel="Line type" value={mapping.lineType} options={EDGE_LINE_OPTIONS} onChange={lineType => onChange({ ...mapping, lineType: lineType as EdgeMapping['lineType'] })} /></DesignerHelpedField>
    </div><details className="editor-progressive-details"><summary>Style, label and technical mapping settings</summary><div className="hydra-designer-fields">
        <DesignerHelpedField label="Edge mapping ID" targetId="edge-mapping-id" required help="Stable identifier referenced by edge creation tools." example="edge-mapping:works-for"><input id="edge-mapping-id" value={mapping.id} onChange={event => onRenameId(mapping.id, event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Source class ID" targetId="edge-mapping-source" required help="Source endpoint inherited from the model relation." example="class:Person"><input id="edge-mapping-source" value={mapping.sourceClassId} readOnly /></DesignerHelpedField>
        <DesignerHelpedField label="Target class ID" targetId="edge-mapping-target" required help="Target endpoint inherited from the model relation." example="class:Company"><input id="edge-mapping-target" value={mapping.targetClassId} readOnly /></DesignerHelpedField>
        <EdgeNotationStyleFields mapping={mapping} onChange={onChange} />
        <ConditionalEdgeStyleEditor mapping={mapping} metamodel={metamodel} expressionModel={expressionModel} onMappingChange={onChange} onExpressionModelChange={onExpressionModelChange} />
        <DesignerHelpedField className="hydra-designer-wide" label="Connection label" targetId="edge-mapping-label-expression" help="Choose a configured expression for the visible connection label, or keep the default relationship label." example="Default label" options={expressionSelectOptions(expressionModel)}><HydraAdaptiveSelect id="edge-mapping-label-expression" ariaLabel="Connection label expression" value={mapping.labelExpressionId} options={expressionSelectOptions(expressionModel)} onChange={labelExpressionId => onChange({ ...mapping, labelExpressionId })} /></DesignerHelpedField>
    </div></details></section>;
}

export function NodeNotationStyleFields({ mapping, onChange }: { mapping: NodeMapping; onChange: (mapping: NodeMapping) => void }) {
    const { t } = useHydraLanguage();
    const style = resolveNodeNotationStyle(mapping.styles);
    const update = (key: Parameters<typeof updateNodeStyle>[1], value: string | number | boolean) => onChange(updateNodeStyle(mapping, key, value));
    return <section className="hydra-designer-section hydra-designer-wide"><header><div><h4>Figure, border and label style</h4><p>Configure portable colors, outline, opacity and label placement for this mapping.</p></div></header><div className="hydra-designer-fields">
        <DesignerHelpedField label="Fill color" targetId="node-style-fill" help="Interior color of the geometric figure." example="#FFFFFF"><input id="node-style-fill" type="color" value={style.fillColor} onChange={event => update('fillColor', event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Border color" targetId="node-style-stroke" help="Outline color of the figure." example="#475569"><input id="node-style-stroke" type="color" value={style.strokeColor} onChange={event => update('strokeColor', event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Border width" targetId="node-style-stroke-width" help="Outline width in pixels." example="2"><input id="node-style-stroke-width" type="number" min={0.5} step={0.5} value={style.strokeWidth} onChange={event => update('strokeWidth', Math.max(.5, Number(event.target.value) || .5))} /></DesignerHelpedField>
        <DesignerHelpedField label="Border pattern" targetId="node-style-border" help="Solid, dashed, dotted, double or hidden outline." example="Dashed"><HydraAdaptiveSelect id="node-style-border" ariaLabel="Node border pattern" value={style.borderStyle} options={[{value:'SOLID',label:'Solid'},{value:'DASHED',label:'Dashed'},{value:'DOTTED',label:'Dotted'},{value:'DOUBLE',label:'Double'},{value:'NONE',label:'No border'}]} onChange={value => update('borderStyle', value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Corner radius" targetId="node-style-radius" help="Corner radius for rectangular and document-like figures." example="12"><input id="node-style-radius" type="number" min={0} value={style.borderRadius} onChange={event => update('borderRadius', Math.max(0, Number(event.target.value) || 0))} /></DesignerHelpedField>
        <DesignerHelpedField label="Figure opacity" targetId="node-style-opacity" help="Opacity from 0.1 to 1." example="1"><input id="node-style-opacity" type="number" min={0.1} max={1} step={0.05} value={style.opacity} onChange={event => update('opacity', Math.max(.1, Math.min(1, Number(event.target.value) || 1)))} /></DesignerHelpedField>
        {mapping.shape === 'SVG_PATH' && <DesignerHelpedField className="hydra-designer-wide" label="Portable SVG path" targetId="node-style-svg-path" required help="SVG path data interpreted inside a 100 × 100 neutral viewBox. Scripts and external references are not accepted." example="M 10 10 L 90 10 L 50 90 Z"><textarea id="node-style-svg-path" value={style.svgPath} onChange={event => update('svgPath', event.target.value)} /></DesignerHelpedField>}
        <DesignerHelpedField label="Label color" targetId="node-style-label-color" help="Text color of the node label." example="#0F172A"><input id="node-style-label-color" type="color" value={style.labelColor} onChange={event => update('labelColor', event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Label background" targetId="node-style-label-background" help="Background color behind the label. It is preserved while transparent mode is enabled." example="#FFFFFF"><input id="node-style-label-background" type="color" disabled={style.labelBackgroundTransparent} value={style.labelBackgroundColor} onChange={event => update('labelBackgroundColor', event.target.value)} /></DesignerHelpedField><DesignerHelpedField label="Transparent label background" targetId="node-label-background-transparent" help="Removes the label background completely so only the text is shown." example="Transparent"><label className="hydra-inline-checkbox" id="node-label-background-transparent"><input type="checkbox" checked={style.labelBackgroundTransparent} onChange={event => update('labelBackgroundTransparent', event.target.checked)} /> {t('Transparent')}</label></DesignerHelpedField>
        <DesignerHelpedField label="Label position" targetId="node-style-label-position" help="Places the main label inside or outside the figure." example="Bottom"><HydraAdaptiveSelect id="node-style-label-position" ariaLabel="Node label position" value={style.labelPosition} options={[{value:'TOP',label:'Inside top'},{value:'CENTER',label:'Centered'},{value:'BOTTOM',label:'Inside bottom'},{value:'OUTSIDE_TOP',label:'Outside top'},{value:'OUTSIDE_BOTTOM',label:'Outside bottom'}]} onChange={value => update('labelPosition', value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Label font size" targetId="node-style-label-size" help="Label text size in pixels." example="14"><input id="node-style-label-size" type="number" min={7} value={style.labelFontSize} onChange={event => update('labelFontSize', Math.max(7, Number(event.target.value) || 7))} /></DesignerHelpedField>
        <DesignerHelpedField label="Label weight" targetId="node-style-label-weight" help="CSS numeric font weight." example="600"><input id="node-style-label-weight" type="number" min={100} max={900} step={100} value={style.labelFontWeight} onChange={event => update('labelFontWeight', Math.max(100, Math.min(900, Number(event.target.value) || 600)))} /></DesignerHelpedField>
        <DesignerHelpedField label="Label padding" targetId="node-style-label-padding" help="Internal label padding in pixels." example="4"><input id="node-style-label-padding" type="number" min={0} value={style.labelPadding} onChange={event => update('labelPadding', Math.max(0, Number(event.target.value) || 0))} /></DesignerHelpedField>
        <DesignerHelpedCheckbox checked={style.labelVisible} label="Show node label" targetId="node-style-label-visible" help="Hides only the visible label; the semantic name remains available to accessibility and tooling." example="Enabled by default." onChange={value => update('labelVisible', value)} />
    </div></section>;
}

export function EdgeNotationStyleFields({ mapping, onChange }: { mapping: EdgeMapping; onChange: (mapping: EdgeMapping) => void }) {
    const { t } = useHydraLanguage();
    const style = resolveEdgeNotationStyle(mapping.styles);
    const update = (key: string, value: string | number | boolean) => onChange({ ...mapping, styles: { ...mapping.styles, [key]: String(value) } });
    const noMarkers=style.sourceDecoration==='NONE'&&style.targetDecoration==='NONE';
    return <section className="hydra-designer-section hydra-designer-wide editor-edge-style-system">
        <header><div><h4>{t('Connection appearance')}</h4><p>{t('Define the line and both ends independently. A connection may have a marker on either end, on both ends, or on neither end.')}</p></div><div className="editor-edge-style-summary"><span>{t(style.borderStyle.toLowerCase())}</span><span>{style.strokeWidth} {t('pixels')}</span><span>{t(noMarkers?'No end markers':'Independent end markers')}</span></div></header>
        <div className="editor-edge-style-line-card"><header><div><strong>{t('Connection line')}</strong><small>{t('Start with the line itself, then refine each endpoint below.')}</small></div></header><div className="hydra-designer-fields">
            <DesignerHelpedField label="Line color" targetId="edge-style-color" help="Main color of the connection. Endpoint colors can be configured independently." example="#334155"><input id="edge-style-color" type="color" value={style.strokeColor} onChange={event => update('strokeColor', event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Line width" targetId="edge-style-width" help="Visible connection thickness in pixels. Endpoint size is controlled separately." example="2"><input id="edge-style-width" type="number" min={0.5} step={0.5} value={style.strokeWidth} onChange={event => update('strokeWidth', Math.max(.5, Number(event.target.value) || .5))} /></DesignerHelpedField>
            <DesignerHelpedField label="Line opacity" targetId="edge-style-opacity" help="Transparency from 0.1 to 1. Lower values de-emphasize secondary connections without hiding them." example="1"><input id="edge-style-opacity" type="number" min={0.1} max={1} step={0.05} value={style.strokeOpacity} onChange={event => update('strokeOpacity', Math.max(.1, Math.min(1, Number(event.target.value) || 1)))} /></DesignerHelpedField>
            <DesignerHelpedField label="Line border color" targetId="edge-style-border-color" help="Optional contrast outline behind the connection line. Use border width 0 to disable it." example="#FFFFFF"><input id="edge-style-border-color" type="color" value={style.strokeBorderColor} onChange={event => update('strokeBorderColor', event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Line border width" targetId="edge-style-border-width" help="Extra outline around the line. Useful when diagrams have dense or colored backgrounds." example="0"><input id="edge-style-border-width" type="number" min={0} max={8} step={0.5} value={style.strokeBorderWidth} onChange={event => update('strokeBorderWidth', Math.max(0, Math.min(8, Number(event.target.value) || 0)))} /></DesignerHelpedField>
            <DesignerHelpedField label="Line pattern" targetId="edge-style-pattern" help="Choose the visual rhythm of the connection line, or hide the line while retaining semantic endpoints." example="Dashed"><HydraAdaptiveSelect id="edge-style-pattern" ariaLabel="Edge line pattern" value={style.borderStyle} options={[{value:'SOLID',label:'Solid',description:'Continuous connection line.'},{value:'DASHED',label:'Dashed',description:'Repeated dashes.'},{value:'DOTTED',label:'Dotted',description:'Repeated compact dots.'},{value:'LONG_DASH',label:'Long dash',description:'Longer separated dashes for secondary or external links.'},{value:'DASH_DOT',label:'Dash dot',description:'Alternating dash and dot pattern.'},{value:'NONE',label:'Hidden',description:'Hide the line while preserving the semantic relation.'}]} onChange={value => update('borderStyle', value)} /></DesignerHelpedField>
        </div></div>
        <div className="editor-edge-end-grid"><EditorEdgeEndpointStyleFields endpoint="SOURCE" style={style} update={update}/><EditorEdgeEndpointStyleFields endpoint="TARGET" style={style} update={update}/></div>
        <details className="editor-progressive-details"><summary>{t('Connection label')}</summary><div className="hydra-designer-fields">
            <DesignerHelpedField label="Label color" targetId="edge-label-color" help="Text color used for the connection label." example="#334155"><input id="edge-label-color" type="color" value={style.labelColor} onChange={event => update('labelColor', event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Label background" targetId="edge-label-background" help="Background color behind the connection label. It is preserved while transparent mode is enabled." example="#FFFFFF"><input id="edge-label-background" type="color" disabled={style.labelBackgroundTransparent} value={style.labelBackgroundColor} onChange={event => update('labelBackgroundColor', event.target.value)} /></DesignerHelpedField><DesignerHelpedField label="Transparent label background" targetId="edge-label-background-transparent" help="Removes the connection-label background completely so only the text is shown." example="Transparent"><label className="hydra-inline-checkbox" id="edge-label-background-transparent"><input type="checkbox" checked={style.labelBackgroundTransparent} onChange={event => update('labelBackgroundTransparent', event.target.checked)} /> {t('Transparent')}</label></DesignerHelpedField>
            <DesignerHelpedField label="Label font size" targetId="edge-label-size" help="Connection label font size in pixels." example="12"><input id="edge-label-size" type="number" min={7} value={style.labelFontSize} onChange={event => update('labelFontSize', Math.max(7, Number(event.target.value) || 7))} /></DesignerHelpedField>
            <DesignerHelpedField label="Label weight" targetId="edge-label-weight" help="Numeric font weight for the connection label." example="600"><input id="edge-label-weight" type="number" min={100} max={900} step={100} value={style.labelFontWeight} onChange={event => update('labelFontWeight', Math.max(100, Math.min(900, Number(event.target.value) || 600)))} /></DesignerHelpedField>
            <DesignerHelpedField label="Label padding" targetId="edge-label-padding" help="Background padding around the connection label in pixels." example="3"><input id="edge-label-padding" type="number" min={0} value={style.labelPadding} onChange={event => update('labelPadding', Math.max(0, Number(event.target.value) || 0))} /></DesignerHelpedField>
            <DesignerHelpedCheckbox checked={style.labelVisible} label="Show edge label" targetId="edge-label-visible" help="Keeps the semantic relation while hiding only its visible label." example="Enabled by default." onChange={value => update('labelVisible', value)} />
        </div></details>
    </section>;
}

export function PaletteGroupForm(props: Parameters<typeof PaletteGroupFormV43222>[0]) {
    return <PaletteGroupFormV43222 {...props} />;
}

export function ToolForm(props: Parameters<typeof ToolFormV43222>[0]) {
    return <ToolFormV43222 {...props} />;
}

export function FormEditor({ form, metamodel, expressionModel, onChange, onRenameId, onDelete }: { form: FormDefinition; metamodel: MetaModel; expressionModel?: ExpressionModel; onChange: (form: FormDefinition) => void; onRenameId: (previousId: string, nextId: string) => void; onDelete: () => void }) {
    const availableExpressions: ExpressionModel = expressionModel ?? { schemaVersion: '1.0', id: metamodel.id, metamodelId: metamodel.id, expressions: [], annotations: {} };
    const targetClass = metamodel.classes.find(item => item.id === form.targetClassId);
    const features = targetClass === undefined ? [] : [...targetClass.attributes.map(attribute => ({ id: attribute.id, name: attribute.name, kind: 'ATTRIBUTE' as const })), ...metamodel.relations.filter(relation => relation.sourceTypeId === targetClass.id).map(relation => ({ id: relation.id, name: relation.name || relation.id, kind: 'RELATION' as const }))];
    function addSection(): void { const section: FormSectionDefinition = { id: createEditorDefinitionId('form-section'), title: 'General', order: form.sections.length, columns: 1, fields: [] }; onChange({ ...form, sections: [...form.sections, section] }); }
    function updateSection(section: FormSectionDefinition): void { onChange({ ...form, sections: form.sections.map(item => item.id === section.id ? section : item) }); }
    function addField(section: FormSectionDefinition): void { const feature = features.find(item => !form.sections.flatMap(item => item.fields).some(field => field.featureId === item.id)); if (feature === undefined) return; const field: FormFieldDefinition = { id: createEditorDefinitionId('form-field'), featureId: feature.id, label: feature.name, widget: feature.kind === 'RELATION' ? 'REFERENCE' : 'TEXT', required: false, readOnly: false, visibleExpressionId: '', enabledExpressionId: '', helpText: '' }; updateSection({ ...section, fields: [...section.fields, field] }); }
    function updateField(section: FormSectionDefinition, field: FormFieldDefinition, patch: Partial<FormFieldDefinition>): void { updateSection({ ...section, fields: section.fields.map(item => item.id === field.id ? { ...item, ...patch } : item) }); }
    function changeWidget(section: FormSectionDefinition, field: FormFieldDefinition, widget: FormFieldDefinition['widget']): void { updateField(section, field, widget === 'READ_ONLY' ? { widget, readOnly: true, required: false, enabledExpressionId: '' } : { widget }); }
    return <section className="representation-editor editor-progressive-form"><div className="hydra-designer-form-header"><div><h3>{form.name}</h3><p>Form for {targetClass?.name ?? 'metamodel class'}</p></div><div className="hydra-designer-form-actions"><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete form</HydraButton></div></div><DesignerRequiredFieldsLegend />
        <div className="hydra-designer-fields editor-essential-fields">
            <DesignerHelpedField label="Form name" targetId="editor-form-name" required help="Visible name used by creation tools and property editors." example="Person properties"><input id="editor-form-name" value={form.name} onChange={event => onChange({ ...form, name: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="Edits" targetId="editor-form-class" required help="Metamodel class edited by this form. Selecting another class clears incompatible sections so the form can be rebuilt safely." example="Person" options={metamodel.classes.map(item => ({ value: item.id, label: item.name, description: `Edits the attributes and relations of ${item.name}.` }))}><HydraAdaptiveSelect id="editor-form-class" ariaLabel="Target class" value={form.targetClassId} options={metamodel.classes.map(item => ({ value: item.id, label: item.name, category: item.kind === 'CLASS' ? 'Concrete classes' : 'Other classifiers', description: `Edits the attributes and relations of ${item.name}.` }))} onChange={targetClassId => onChange({ ...form, targetClassId, sections: [] })} /></DesignerHelpedField>
        </div>
        <details className="editor-progressive-details"><summary>Technical form identity</summary><div className="hydra-designer-fields"><DesignerHelpedField label="Form ID" targetId="editor-form-id" required help="Stable identifier referenced by node creation tools." example="form:person"><input id="editor-form-id" value={form.id} onChange={event => onRenameId(form.id, event.target.value)} /></DesignerHelpedField></div></details>
        <section className="hydra-designer-section editor-form-builder"><header><div><h4>Sections and fields</h4><p>Build the form from semantic features. Technical IDs and conditional expressions stay hidden until requested.</p></div><HydraButton type="button" onClick={addSection}>+ Section</HydraButton></header>
            {form.sections.length === 0 && <div className="editor-context-note"><strong>No sections yet</strong><span>Add a section, then add semantic fields. HYDRA chooses a sensible widget for references automatically.</span></div>}
            {form.sections.map((section, sectionIndex) => <div className="hydra-designer-row-card editor-form-section-card" key={section.id}><header><div><strong>{section.title || `Section ${sectionIndex + 1}`}</strong><small>{section.fields.length} field(s) · {section.columns} column(s)</small></div><div><HydraButton type="button" onClick={() => addField(section)}>+ Field</HydraButton><HydraButton className="hydra-danger-button" type="button" onClick={() => onChange({ ...form, sections: form.sections.filter(item => item.id !== section.id) })}>Remove section</HydraButton></div></header>
                <div className="hydra-designer-row-fields editor-section-essential"><DesignerHelpedField label="Section title" targetId={`form-section-title-${sectionIndex}`} required help="Visible heading shown above the fields." example="General"><input id={`form-section-title-${sectionIndex}`} value={section.title} onChange={event => updateSection({ ...section, title: event.target.value })} /></DesignerHelpedField></div>
                <details className="editor-progressive-details compact"><summary>Section layout and ID</summary><div className="hydra-designer-row-fields"><DesignerHelpedField label="Section ID" targetId={`form-section-id-${sectionIndex}`} required help="Stable identifier of the form section." example="section:general"><input id={`form-section-id-${sectionIndex}`} value={section.id} onChange={event => updateSection({ ...section, id: event.target.value })} /></DesignerHelpedField><DesignerHelpedField label="Order" targetId={`form-section-order-${sectionIndex}`} required help="Sorting order among form sections." example="0"><input id={`form-section-order-${sectionIndex}`} type="number" min={0} value={section.order} onChange={event => updateSection({ ...section, order: Number(event.target.value) })} /></DesignerHelpedField><DesignerHelpedField label="Columns" targetId={`form-section-columns-${sectionIndex}`} required help="Number of responsive columns used by the section." example="2"><input id={`form-section-columns-${sectionIndex}`} type="number" min={1} max={4} value={section.columns} onChange={event => updateSection({ ...section, columns: Math.max(1, Math.min(4, Number(event.target.value) || 1)) })} /></DesignerHelpedField></div></details>
                {section.fields.map((field, fieldIndex) => {
                    const readOnlyWidget = field.widget === 'READ_ONLY';
                    return <div className="editor-form-field compact" key={field.id}><header><div><strong>{field.label || `Field ${fieldIndex + 1}`}</strong><small>{field.widget}</small></div><HydraButton className="hydra-danger-button" type="button" onClick={() => updateSection({ ...section, fields: section.fields.filter(item => item.id !== field.id) })}>Remove</HydraButton></header><div className="hydra-designer-row-fields editor-field-essential">
                        <DesignerHelpedField label="Semantic feature" targetId={`form-field-feature-${sectionIndex}-${fieldIndex}`} required help="Attribute or relation edited by this field." example="Name" options={features.map(feature => ({ value: feature.id, label: feature.name, description: 'Semantic feature edited by this generated field.' }))}><HydraAdaptiveSelect id={`form-field-feature-${sectionIndex}-${fieldIndex}`} ariaLabel="Semantic feature" value={field.featureId} options={features.map(feature => ({ value: feature.id, label: feature.name, category: feature.kind === 'ATTRIBUTE' ? 'Attributes' : 'Relations', description: 'Semantic feature edited by this generated field.' }))} onChange={featureId => updateField(section, field, { featureId })} /></DesignerHelpedField>
                        <DesignerHelpedField label="Label" targetId={`form-field-label-${sectionIndex}-${fieldIndex}`} required help="Visible text displayed beside the generated widget." example="Name"><input id={`form-field-label-${sectionIndex}-${fieldIndex}`} value={field.label} onChange={event => updateField(section, field, { label: event.target.value })} /></DesignerHelpedField>
                        <DesignerHelpedField label="Widget" targetId={`form-field-widget-${sectionIndex}-${fieldIndex}`} required help="Neutral widget used to edit the semantic feature. Selecting read-only hides edit-only options." example="Single-line text" options={[...WIDGET_OPTIONS]}><HydraAdaptiveSelect id={`form-field-widget-${sectionIndex}-${fieldIndex}`} ariaLabel="Widget" value={field.widget} options={WIDGET_OPTIONS.map(option => ({ ...option, category: widgetCategory(option.value) }))} onChange={widget => changeWidget(section, field, widget as FormFieldDefinition['widget'])} /></DesignerHelpedField>
                        {!readOnlyWidget && <DesignerHelpedCheckbox checked={field.readOnly} label="Read only" targetId={`form-field-read-only-${sectionIndex}-${fieldIndex}`} help="Displays the value but prevents editing it." example="Useful for generated identifiers." onChange={checked => updateField(section, field, { readOnly: checked, required: checked ? false : field.required })} />}
                        {!readOnlyWidget && !field.readOnly && <DesignerHelpedCheckbox checked={field.required} label="Required" targetId={`form-field-required-${sectionIndex}-${fieldIndex}`} help="Requires a value before the generated form may be submitted." example="A person name is required." onChange={checked => updateField(section, field, { required: checked })} />}
                    </div><details className="editor-progressive-details compact"><summary>Field conditions, help and ID</summary><div className="hydra-designer-row-fields">
                        <DesignerHelpedField label="Field ID" targetId={`form-field-id-${sectionIndex}-${fieldIndex}`} required help="Stable field definition identifier." example="field:name"><input id={`form-field-id-${sectionIndex}-${fieldIndex}`} value={field.id} onChange={event => updateField(section, field, { id: event.target.value })} /></DesignerHelpedField>
                        <DesignerHelpedField label="Visible when" targetId={`form-field-visible-${sectionIndex}-${fieldIndex}`} help="Choose a configured condition, or Always to keep the field visible." example="Always" options={expressionSelectOptions(availableExpressions)}><HydraAdaptiveSelect id={`form-field-visible-${sectionIndex}-${fieldIndex}`} ariaLabel="Field visibility condition" value={field.visibleExpressionId} options={expressionSelectOptions(availableExpressions)} onChange={visibleExpressionId => updateField(section, field, { visibleExpressionId })} /></DesignerHelpedField>
                        {!readOnlyWidget && !field.readOnly && <DesignerHelpedField label="Enabled when" targetId={`form-field-enabled-${sectionIndex}-${fieldIndex}`} help="Choose a configured condition, or Always to keep the visible field editable." example="Always" options={expressionSelectOptions(availableExpressions)}><HydraAdaptiveSelect id={`form-field-enabled-${sectionIndex}-${fieldIndex}`} ariaLabel="Field enabled condition" value={field.enabledExpressionId} options={expressionSelectOptions(availableExpressions)} onChange={enabledExpressionId => updateField(section, field, { enabledExpressionId })} /></DesignerHelpedField>}
                        <DesignerHelpedField className="hydra-designer-wide" label="Help text" targetId={`form-field-help-${sectionIndex}-${fieldIndex}`} help="Text shown to users of the generated form." example="Enter the public display name."><input id={`form-field-help-${sectionIndex}-${fieldIndex}`} value={field.helpText} onChange={event => updateField(section, field, { helpText: event.target.value })} /></DesignerHelpedField>
                    </div></details></div>;
                })}
            </div>)}
        </section>
    </section>;
}

function representationKindLabel(kind: RepresentationDefinition['kind']): string { return REPRESENTATION_KIND_OPTIONS.find(item => item.value === kind)?.label ?? kind; }


function expressionSelectOptions(model: ExpressionModel) {
    return [{ value: '', label: 'Default / always', category: 'Recommended', description: 'Do not use an expression for this setting.' }, ...model.expressions.map(expression => ({ value: expression.id, label: expression.name || expression.id, category: 'Configured expressions', description: expression.description || 'Configured reusable expression.' }))];
}

function rootDetectionDescriptionForUi(result: ReturnType<typeof detectMetamodelRoots>, metamodel: MetaModel, t: (text: string) => string): string {
    const name = metamodel.classes.find(item => item.id === result.primaryClassId)?.name ?? t('No root');
    const reason = ({ EXPLICIT_ANNOTATION: 'marked explicitly as a root', NO_INCOMING_CONTAINMENT: 'is not contained by another class', CONTAINMENT_OWNER: 'contains other model elements', FIRST_CONCRETE_CLASS: 'is the first concrete class available', NO_CONCRETE_CLASS: 'cannot be detected because there are no concrete classes' } as const)[result.reason];
    return `${name} ${t(reason)}.`;
}

function widgetCategory(value: string): string {
    if (['TEXT', 'TEXTAREA', 'NUMBER', 'BOOLEAN'].includes(value)) return 'Basic inputs';
    if (['ENUM', 'REFERENCE', 'MULTI_REFERENCE'].includes(value)) return 'Choice and references';
    if (value === 'DATE') return 'Temporal values';
    if (['COLOR', 'FILE'].includes(value)) return 'Visual and file values';
    if (['CODE', 'EXPRESSION', 'CUSTOM'].includes(value)) return 'Advanced inputs';
    return 'Display only';
}
