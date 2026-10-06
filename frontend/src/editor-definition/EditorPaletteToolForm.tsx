import { HydraButton } from '../design-system/HydraButton';
import { useMemo } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { DesignerHelpedCheckbox, DesignerHelpedField, DesignerRequiredFieldsLegend } from '../designer-ui/DesignerFieldHelp';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { MetaModel } from '../metamodel-designer/types';
import type { ActionModel, ExpressionModel, ValidationRunMode } from '../validation/types';
import { ProjectImageFileField } from './ProjectImageFileField';
import { edgeToolDisplayName, semanticEdgeFeatureSummary } from './editorSemanticEdge';
import type { EdgeCreationTool, EditorDefinitionModel, EditorTool, NodeCreationTool, PaletteGroup } from './types';

export type PaletteActionKind = 'INVOKE_OPERATION' | 'RUN_ACTION' | 'VALIDATE';

const VALIDATION_MODES: ReadonlyArray<{ value: ValidationRunMode; label: string; description: string }> = [
    { value: 'SELECTION', label: 'Selected elements', description: 'Validate only the elements currently selected by the user.' },
    { value: 'INCREMENTAL', label: 'Changed elements', description: 'Validate the changed model area and its dependencies.' },
    { value: 'FULL', label: 'Complete model', description: 'Run every applicable validation over the complete model.' },
    { value: 'CLASS', label: 'Current element type', description: 'Validate elements that share the current semantic class.' },
    { value: 'MANUAL', label: 'Manual validation', description: 'Run the validations explicitly configured for manual execution.' },
    { value: 'ON_SAVE', label: 'Save checks', description: 'Run the validation set normally associated with saving.' },
    { value: 'BEFORE_EXPORT', label: 'Export checks', description: 'Run the checks that must pass before exporting.' }
];

export function PaletteGroupForm({ group, toolCount, availableOperations, availableActions, onAddTool, onChange, onRenameId, onDelete }: {
    group: PaletteGroup;
    toolCount: number;
    availableOperations: number;
    availableActions: number;
    onAddTool: (kind: PaletteActionKind) => void;
    onChange: (group: PaletteGroup) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onDelete: () => void;
}) {
    const { t } = useHydraLanguage();
    return <section className="representation-editor editor-progressive-form">
        <div className="hydra-designer-form-header"><div><h3>{group.name}</h3><p>{toolCount} {t('assigned tool(s)')}</p></div><div className="hydra-designer-form-actions"><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>{t('Delete group')}</HydraButton></div></div>
        <section className="editor-palette-purpose-card">
            <div><span className="editor-palette-purpose-kicker">{t('Palette group')}</span><h4>{t('Organize actions by what the user wants to do')}</h4><p>{t('Keep everyday actions together and move advanced or infrequent actions into separate groups. The generated editor uses this same organization.')}</p></div>
            <div className="editor-palette-purpose-stat"><strong>{toolCount}</strong><span>{t('actions in this group')}</span></div>
        </section>
        <DesignerRequiredFieldsLegend />
        <div className="hydra-designer-fields editor-essential-fields">
            <DesignerHelpedField label="Group name" targetId="palette-group-name" required help="Visible title of the collapsible tool group in the generated palette." example="Elements"><input id="palette-group-name" value={group.name} onChange={event => onChange({ ...group, name: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedCheckbox checked={group.initiallyCollapsed} label="Initially collapsed" targetId="palette-group-collapsed" help="Starts this group collapsed when the generated editor opens." example="Useful for advanced or infrequent tools." onChange={initiallyCollapsed => onChange({ ...group, initiallyCollapsed })} />
        </div>
        <section className="editor-palette-action-starter" aria-label={t('Add a user action')}>
            <header><div><span className="editor-palette-purpose-kicker">{t('Next')}</span><h4>{t('Add a user action')}</h4><p>{t('Choose the outcome first. HYDRA will then show only the settings needed for that action.')}</p></div></header>
            <div className="editor-palette-action-options">
                <ActionStarterCard title="Run a metamodel operation" description="Expose an operation already defined on a metamodel class." detail={availableOperations === 0 ? 'Define a metamodel operation first.' : 'operation(s) available'} detailCount={availableOperations === 0 ? undefined : availableOperations} disabled={availableOperations === 0} onClick={() => onAddTool('INVOKE_OPERATION')} />
                <ActionStarterCard title="Run a configured action" description="Expose a transactional action already defined in Validations and actions." detail={availableActions === 0 ? 'Define an action first.' : 'action(s) available'} detailCount={availableActions === 0 ? undefined : availableActions} disabled={availableActions === 0} onClick={() => onAddTool('RUN_ACTION')} />
                <ActionStarterCard title="Validate the model" description="Give the user an explicit validation action and choose its scope afterwards." detail="Safe default: selected elements" disabled={false} onClick={() => onAddTool('VALIDATE')} />
            </div>
            <p className="editor-palette-action-note">{t('Creation actions for elements and relationships are added from their visual mappings so the palette cannot drift away from the metamodel.')}</p>
        </section>
        <details className="editor-progressive-details"><summary>{t('Order and technical group identity')}</summary><div className="hydra-designer-fields"><DesignerHelpedField label="Group ID" targetId="palette-group-id" required help="Stable identifier of the palette group." example="group:elements"><input id="palette-group-id" value={group.id} onChange={event => onRenameId(group.id, event.target.value)} /></DesignerHelpedField><DesignerHelpedField label="Order" targetId="palette-group-order" required help="Sorting position of the group in the generated palette." example="0"><input id="palette-group-order" type="number" min={0} value={group.order} onChange={event => onChange({ ...group, order: Math.max(0, Number(event.target.value) || 0) })} /></DesignerHelpedField><DesignerHelpedField label="Assigned tools" targetId="palette-group-tools" help="Number of tools currently organized under this palette group." example="4"><input id="palette-group-tools" value={toolCount} readOnly /></DesignerHelpedField></div></details>
    </section>;
}

function ActionStarterCard({ title, description, detail, detailCount, disabled, onClick }: { title: string; description: string; detail: string; detailCount?: number; disabled: boolean; onClick: () => void }) {
    const { t } = useHydraLanguage();
    return <article className={`editor-palette-action-card${disabled ? ' disabled' : ''}`}><div><strong>{t(title)}</strong><p>{t(description)}</p><small>{detailCount === undefined ? t(detail) : <><b>{detailCount}</b> {t(detail)}</>}</small></div><HydraButton type="button" disabled={disabled} onClick={onClick}>{t('Add')}</HydraButton></article>;
}

export function ToolForm({ tool, model, metamodel, expressions, actions, onChange, onRenameId, onDelete }: {
    tool: EditorTool;
    model: EditorDefinitionModel;
    metamodel: MetaModel;
    expressions: ExpressionModel;
    actions: ActionModel;
    onChange: (tool: EditorTool) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onDelete: () => void;
}) {
    const { t } = useHydraLanguage();
    const operations = useMemo(() => metamodel.classes.flatMap(owner => owner.operations.map(operation => ({ owner, operation }))), [metamodel.classes]);
    const edgeMappings = useMemo(() => model.representations.flatMap(rep => rep.edgeMappings.map(mapping => ({ rep, mapping }))), [model.representations]);
    const expressionOptions = [{ value: '', label: 'Always', category: 'Recommended', description: 'No condition. The action remains available whenever its view is open.' }, ...expressions.expressions.map(expression => ({ value: expression.id, label: expression.name || expression.id, category: 'Configured expressions', description: expression.description || 'Use this configured expression as the condition.' }))];
    const summary = describeTool(tool, model, metamodel, actions);

    return <section className="representation-editor editor-progressive-form">
        <div className="hydra-designer-form-header"><div><h3>{tool.label}</h3><p>{t(toolKindLabel(tool.kind))}</p></div><div className="hydra-designer-form-actions"><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>{t('Delete tool')}</HydraButton></div></div>
        <section className="editor-tool-outcome-summary">
            <div><span>{t('What the user can do')}</span><strong>{t(summary.outcome)}{summary.subject === '' ? '' : ` ${summary.subject}`}</strong><p>{t(summary.detail)}</p></div>
            <dl><div><dt>{t('Shown in')}</dt><dd>{summary.views}</dd></div><div><dt>{t('Availability')}</dt><dd>{t(summary.availability)}</dd></div><div><dt>{t('Result')}</dt><dd>{t(summary.result)}</dd></div></dl>
        </section>
        <DesignerRequiredFieldsLegend />
        <div className="hydra-designer-fields editor-essential-fields">
            <DesignerHelpedField label="Tool label" targetId="editor-tool-label" required help="Visible action name shown to users in the generated palette." example="Create Person"><input id="editor-tool-label" value={tool.label} onChange={event => onChange({ ...tool, label: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Available in views" targetId="editor-tool-representations" help="Choose only the views where this action should be offered." example="Main diagram"><div className="editor-tool-representations compact-capabilities" id="editor-tool-representations" tabIndex={-1}>{model.representations.map(rep => <label className="hydra-inline-checkbox" key={rep.id}><input type="checkbox" checked={tool.representationIds.includes(rep.id)} onChange={() => onChange({ ...tool, representationIds: tool.representationIds.includes(rep.id) ? tool.representationIds.filter(id => id !== rep.id) : [...tool.representationIds, rep.id] })} /> {rep.name}</label>)}</div></DesignerHelpedField>
            {tool.kind === 'CREATE_NODE' && <NodeToolSpecific tool={tool} classes={metamodel.classes} forms={model.forms} onChange={onChange} />}
            {tool.kind === 'CREATE_EDGE' && <EdgeToolSpecific tool={tool} metamodel={metamodel} edgeMappings={edgeMappings} onChange={onChange} />}
            {tool.kind === 'INVOKE_OPERATION' && <DesignerHelpedField label="Operation" targetId="editor-tool-operation" required help="Choose an operation already defined in the metamodel. HYDRA keeps the technical ID internally." example="Customer · recalculate" options={operations.map(({ owner, operation }) => ({ value: operation.id, label: `${owner.name} · ${operation.name}`, description: operation.behaviorKind === 'QUERY' ? 'Query operation.' : operation.behaviorKind === 'COMMAND' ? 'Command operation.' : 'Metamodel operation.' }))}><HydraAdaptiveSelect id="editor-tool-operation" ariaLabel="Operation" value={tool.operationId} options={operations.map(({ owner, operation }) => ({ value: operation.id, label: operation.name, category: owner.name, description: operation.behaviorKind === 'QUERY' ? 'Read-only query operation.' : operation.behaviorKind === 'COMMAND' ? 'Command that may change model state.' : 'Metamodel operation.' }))} onChange={operationId => onChange({ ...tool, operationId })} emptyMessage="No metamodel operations available" /></DesignerHelpedField>}
            {tool.kind === 'RUN_ACTION' && <DesignerHelpedField label="Configured action" targetId="editor-tool-action" required help="Choose an action already defined in Validations and actions. Its transaction and undo policies remain authoritative." example="Normalize name" options={actions.actions.map(action => ({ value: action.id, label: action.name, description: action.description || 'Configured transactional action.' }))}><HydraAdaptiveSelect id="editor-tool-action" ariaLabel="Configured action" value={tool.actionId} options={actions.actions.map(action => ({ value: action.id, label: action.name, category: action.enabled ? 'Enabled actions' : 'Disabled actions', description: action.description || 'Configured transactional action.', disabled: !action.enabled }))} onChange={actionId => onChange({ ...tool, actionId })} emptyMessage="No configured actions available" /></DesignerHelpedField>}
            {tool.kind === 'VALIDATE' && <DesignerHelpedField label="Validation scope" targetId="editor-tool-validation-mode" required help="Choose how much of the model should be checked when the user runs this action." example="Selected elements" options={VALIDATION_MODES}><HydraAdaptiveSelect id="editor-tool-validation-mode" ariaLabel="Validation scope" value={tool.validationMode} options={VALIDATION_MODES.map(option => ({ ...option, category: option.value === 'SELECTION' || option.value === 'INCREMENTAL' ? 'Recommended' : 'Other scopes' }))} onChange={validationMode => onChange({ ...tool, validationMode })} /></DesignerHelpedField>}
        </div>
        <details className="editor-progressive-details"><summary>{t('Icon, availability, description and technical details')}</summary><div className="hydra-designer-fields">
            <ProjectImageFileField id="editor-tool-icon" label="Tool icon" help="Choose an optional SVG, PNG, JPEG, GIF or WebP. HYDRA stores it inside the active project and keeps the portable reference automatically." value={tool.icon} onChange={icon => onChange({ ...tool, icon })} />
            <DesignerHelpedField label="Visible when" targetId="editor-tool-visible-expression" help="Choose a configured condition, or Always to keep the action visible." example="Always" options={expressionOptions}><HydraAdaptiveSelect id="editor-tool-visible-expression" ariaLabel="Visible when" value={tool.visibleExpressionId} options={expressionOptions} onChange={visibleExpressionId => onChange({ ...tool, visibleExpressionId })} /></DesignerHelpedField>
            <DesignerHelpedField label="Enabled when" targetId="editor-tool-enabled-expression" help="Choose a configured condition, or Always to keep a visible action enabled." example="Always" options={expressionOptions}><HydraAdaptiveSelect id="editor-tool-enabled-expression" ariaLabel="Enabled when" value={tool.enabledExpressionId} options={expressionOptions} onChange={enabledExpressionId => onChange({ ...tool, enabledExpressionId })} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="editor-tool-description" help="Short user-facing explanation shown as help for this palette action." example="Create a Person in the active representation."><textarea id="editor-tool-description" value={tool.description} onChange={event => onChange({ ...tool, description: event.target.value })} /></DesignerHelpedField>
            <DesignerHelpedField label="Tool ID" targetId="editor-tool-id" required help="Stable technical identifier. Most users do not need to change it." example="tool:create-person"><input id="editor-tool-id" value={tool.id} onChange={event => onRenameId(tool.id, event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Action type" targetId="editor-tool-kind" required help="Technical category used by the runtime. It is determined when the action is created." example="CREATE_NODE"><input id="editor-tool-kind" value={tool.kind} readOnly /></DesignerHelpedField>
        </div></details>
    </section>;
}

function NodeToolSpecific({ tool, classes, forms, onChange }: { tool: NodeCreationTool; classes: MetaModel['classes']; forms: EditorDefinitionModel['forms']; onChange: (tool: EditorTool) => void }) {
    const concrete = classes.filter(item => item.kind === 'CLASS');
    return <><DesignerHelpedField label="Target class" targetId="node-tool-class" required help="Choose the concrete model element created by this action." example="Person" options={concrete.map(item => ({ value: item.id, label: item.name, description: `Creates a new ${item.name} model element.` }))}><HydraAdaptiveSelect id="node-tool-class" ariaLabel="Target class" value={tool.targetClassId} options={concrete.map(item => ({ value: item.id, label: item.name, category: 'Concrete classes', description: `Creates a new ${item.name} model element.` }))} onChange={targetClassId => onChange({ ...tool, targetClassId })} /></DesignerHelpedField><DesignerHelpedField label="Creation form" targetId="node-tool-form" help="Choose the form to open after creation, or create the element directly." example="No form" options={[{ value: '', label: 'No form', description: 'Create the element without opening a generated form.' }, ...forms.map(form => ({ value: form.id, label: form.name, description: `Generated form for ${form.name}.` }))]}><HydraAdaptiveSelect id="node-tool-form" ariaLabel="Creation form" value={tool.formId} options={[{ value: '', label: 'No form', category: 'Recommended', description: 'Create the element without opening a generated form.' }, ...forms.map(form => ({ value: form.id, label: form.name, category: 'Generated forms', description: `Generated form for ${form.name}.` }))]} onChange={formId => onChange({ ...tool, formId })} /></DesignerHelpedField><DesignerHelpedField className="hydra-designer-wide" label="Default values JSON" targetId="node-tool-defaults" help="Optional initial semantic values. Keep this empty unless creation needs explicit defaults." example='{ "active": true }'><textarea id="node-tool-defaults" value={JSON.stringify(tool.defaultValues, null, 2)} onChange={event => { try { onChange({ ...tool, defaultValues: JSON.parse(event.target.value) as Record<string, unknown> }); } catch { /* Keep the last valid object while the user edits. */ } }} /></DesignerHelpedField></>;
}

function EdgeToolSpecific({ tool, metamodel, edgeMappings, onChange }: { tool: EdgeCreationTool; metamodel: MetaModel; edgeMappings: Array<{ rep: EditorDefinitionModel['representations'][number]; mapping: EditorDefinitionModel['representations'][number]['edgeMappings'][number] }>; onChange: (tool: EditorTool) => void }) {
    const relations = metamodel.relations;
    if (tool.semanticEdge !== undefined) {
        const compatibleMappings = edgeMappings.filter(item => item.mapping.semanticEdge?.edgeClassId === tool.semanticEdge?.edgeClassId);
        return <><div className="editor-context-note hydra-designer-wide"><strong>Semantic connection object · {edgeToolDisplayName(metamodel, tool)}</strong><span>This imported action creates a model element that owns explicit source and target references. HYDRA preserves that structure and does not convert it into a different direct relationship.</span><small>{semanticEdgeFeatureSummary(metamodel, tool.semanticEdge)}</small></div><DesignerHelpedField label="Visual connection" targetId="edge-tool-mapping" required help="Choose the visual mapping for this semantic connection object." example="Main diagram · Connection"><HydraAdaptiveSelect id="edge-tool-mapping" ariaLabel="Visual connection" value={tool.edgeMappingId} options={compatibleMappings.map(({ rep, mapping }) => ({ value: mapping.id, label: mapping.id, category: rep.name, description: `Uses the ${rep.name} mapping while preserving the semantic connection object.` }))} onChange={edgeMappingId => onChange({ ...tool, edgeMappingId })} emptyMessage="No compatible semantic-edge mapping" /></DesignerHelpedField><DesignerHelpedCheckbox checked={false} label="Allow reconnecting" targetId="edge-tool-reconnectable" help="Imported semantic connection objects are recreated transactionally instead of mutating endpoints in place until their external reconnect semantics are proven equivalent." example="Disabled for safely recovered semantic-edge tools." onChange={() => undefined} /></>;
    }
    const compatibleMappings = edgeMappings.filter(item => item.mapping.semanticEdge === undefined && (item.mapping.relationId === tool.relationId || tool.relationId === ''));
    return <><DesignerHelpedField label="Relationship" targetId="edge-tool-relation" required help="Choose the semantic metamodel relationship this action creates." example="Works for" options={relations.map(item => ({ value: item.id, label: item.name || 'Unnamed relation', description: 'Creates this semantic relationship between the selected source and target.' }))}><HydraAdaptiveSelect id="edge-tool-relation" ariaLabel="Relationship" value={tool.relationId} options={relations.map(item => ({ value: item.id, label: item.name || 'Unnamed relation', category: item.kind === 'CONTAINMENT' ? 'Containment relationships' : 'References and associations', description: 'Creates this semantic relationship between the selected source and target.' }))} onChange={relationId => { const firstCompatible = edgeMappings.find(item => item.mapping.semanticEdge === undefined && item.mapping.relationId === relationId); onChange({ ...tool, relationId, semanticEdge: undefined, edgeMappingId: firstCompatible?.mapping.id ?? '' }); }} /></DesignerHelpedField><DesignerHelpedField label="Visual connection" targetId="edge-tool-mapping" required help="Choose how that relationship is represented visually. Only mappings from the current editor are offered." example="Main diagram · Works for"><HydraAdaptiveSelect id="edge-tool-mapping" ariaLabel="Visual connection" value={tool.edgeMappingId} options={compatibleMappings.map(({ rep, mapping }) => ({ value: mapping.id, label: mapping.id, category: rep.name, description: `Uses the ${rep.name} visual mapping for this relationship.` }))} onChange={edgeMappingId => onChange({ ...tool, edgeMappingId })} emptyMessage="No visual mapping for this relationship" /></DesignerHelpedField><DesignerHelpedCheckbox checked={tool.reconnectable} label="Allow reconnecting" targetId="edge-tool-reconnectable" help="Allows users to move either endpoint of an existing relationship when the metamodel permits it." example="Useful for associations whose endpoints may change." onChange={checked => onChange({ ...tool, reconnectable: checked })} /></>;
}

function describeTool(tool: EditorTool, model: EditorDefinitionModel, metamodel: MetaModel, actions: ActionModel): { outcome: string; subject: string; detail: string; views: string; availability: string; result: string } {
    const views = model.representations.filter(rep => tool.representationIds.includes(rep.id)).map(rep => rep.name).join(', ') || 'No view selected';
    const availability = tool.visibleExpressionId === '' && tool.enabledExpressionId === '' ? 'Always available in its selected views' : 'Controlled by configured conditions';
    if (tool.kind === 'CREATE_NODE') {
        const target = metamodel.classes.find(item => item.id === tool.targetClassId)?.name ?? 'model element';
        return { outcome: 'Create', subject: target, detail: 'Adds a new semantic element and shows it through the selected representation.', views, availability, result: 'New model element' };
    }
    if (tool.kind === 'CREATE_EDGE') {
        const relation = edgeToolDisplayName(metamodel, tool);
        return { outcome: 'Create', subject: relation, detail: tool.semanticEdge === undefined ? 'Connects two compatible model elements using an existing metamodel relationship.' : 'Creates a semantic connection object with explicit source and target references.', views, availability, result: tool.semanticEdge === undefined ? 'New model relationship' : 'New semantic connection element' };
    }
    if (tool.kind === 'INVOKE_OPERATION') {
        const operation = metamodel.classes.flatMap(owner => owner.operations.map(item => ({ owner, item }))).find(entry => entry.item.id === tool.operationId);
        return { outcome: operation === undefined ? 'Run a metamodel operation' : 'Run', subject: operation?.item.name ?? '', detail: 'Executes behavior already defined by the metamodel instead of duplicating it in the palette.', views, availability, result: operation?.item.behaviorKind === 'QUERY' ? 'Operation result' : 'Updated model state' };
    }
    if (tool.kind === 'RUN_ACTION') {
        const action = actions.actions.find(item => item.id === tool.actionId);
        return { outcome: action === undefined ? 'Run a configured action' : 'Run', subject: action?.name ?? '', detail: 'Executes the selected transactional action with its existing safety and undo policies.', views, availability, result: 'Action result' };
    }
    return { outcome: 'Validate the model', subject: '', detail: 'Runs the configured validation rules at the selected scope and reports actionable diagnostics.', views, availability, result: 'Validation report' };
}

function toolKindLabel(kind: EditorTool['kind']): string {
    return ({ CREATE_NODE: 'Create element', CREATE_EDGE: 'Create relationship', INVOKE_OPERATION: 'Run operation', RUN_ACTION: 'Run action', VALIDATE: 'Validate' } as Record<EditorTool['kind'], string>)[kind];
}
