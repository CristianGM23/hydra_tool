import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import type { MetaModel } from '../metamodel-designer/types';
import type {
    ActionDefinition,
    ActionModel,
    ActionOperation,
    ActionSelector,
    ActionSelectorKind,
    ActionValueSource,
    ExpressionModel,
    TextTransformation
} from '../validation/types';
import { createDefaultAction, createDefaultOperation, createValidationDesignerId } from './validationDesignerFactory';
import { FieldLabel, HelpedField, ProcessGuideButton, RequiredFieldsLegend } from './ValidationFieldHelp';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraCreationModeSwitch, HydraGuidedWorkflow } from '../design-system';
import { allAttributes } from './validationDesignerSelectors';
import { findQuickFixBlockingError } from './validationDesignerValidation';

interface QuickFixDesignerProps {
    metamodel: MetaModel;
    actionModel: ActionModel;
    expressionModel: ExpressionModel;
    selectedActionId: string;
    onChange: (model: ActionModel) => void;
    onSelectAction: (actionId: string) => void;
    onFinishGuided?: () => boolean | Promise<boolean>;
}

const operationKinds: ActionOperation['kind'][] = [
    'SET_ATTRIBUTE', 'CLEAR_ATTRIBUTE', 'COPY_ATTRIBUTE', 'ADD_RELATION', 'REMOVE_RELATION',
    'CREATE_ELEMENT', 'DELETE_ELEMENT', 'TRANSFORM_TEXT', 'EXECUTE_ACTION', 'INVOKE_OPERATION'
];

const PARAMETER_TYPE_OPTIONS = [
    { value:'STRING', label:'String', description:'Free text value.', category:'Scalar values' },
    { value:'INTEGER', label:'Integer', description:'Whole numeric value.', category:'Scalar values' },
    { value:'DECIMAL', label:'Decimal', description:'Numeric value that may contain a fractional part.', category:'Scalar values' },
    { value:'BOOLEAN', label:'Boolean', description:'True or false value.', category:'Scalar values' },
    { value:'ELEMENT_ID', label:'Model element', description:'Stable identifier of a model element.', category:'Model references' },
    { value:'CLASS_ID', label:'Class', description:'Stable identifier of a metamodel class.', category:'Metamodel references' },
    { value:'RELATION_ID', label:'Relation', description:'Stable identifier of a metamodel relation.', category:'Metamodel references' },
    { value:'ATTRIBUTE_ID', label:'Attribute', description:'Stable identifier of a metamodel attribute.', category:'Metamodel references' }
] as const;


const TRANSACTION_OPTIONS = [
    { value:'ATOMIC', label:'All or nothing', description:'Rolls back every operation if any step fails.', category:'Safe execution' },
    { value:'BEST_EFFORT', label:'Best effort', description:'Keeps successful changes and reports operations that failed.', category:'Partial execution' }
] as const;
const UNDO_OPTIONS = [
    { value:'REQUIRED', label:'Required', description:'The runtime must record enough information to undo the complete quick fix.', category:'Undo enabled' },
    { value:'OPTIONAL', label:'Optional', description:'Undo is used when the runtime can provide it, but execution may continue without it.', category:'Undo enabled' },
    { value:'NOT_SUPPORTED', label:'Not supported', description:'The quick fix cannot be reversed automatically.', category:'No undo' }
] as const;
const VALUE_SOURCE_OPTIONS = [
    { value:'LITERAL', label:'Fixed value', description:'Uses the value typed directly in this operation.', category:'Direct values' },
    { value:'EXPRESSION', label:'Expression', description:'Calculates the value at execution time with an ExpressionModel.', category:'Calculated values' },
    { value:'PARAMETER', label:'Action parameter', description:'Reads the value supplied by the user or invoking runtime.', category:'Runtime values' }
] as const;
const SELECTOR_KIND_OPTIONS = [
    { value:'SELF', label:'Current element', description:'Uses the model element currently receiving the quick fix.', category:'Current context' },
    { value:'SELECTION', label:'Current selection', description:'Uses the element selected in the active editor.', category:'Current context' },
    { value:'PARAMETER', label:'Action parameter', description:'Uses a model element supplied through an action parameter.', category:'Runtime references' },
    { value:'CREATED_ELEMENT', label:'Created element', description:'Uses the result of a previous Create element operation in this quick fix.', category:'Runtime references' },
    { value:'ELEMENT_ID', label:'Specific element ID', description:'Resolves one element by its stable identifier.', category:'Explicit references' }
] as const;

function operationKindDescription(kind: ActionOperation['kind']): string {
    const descriptions: Record<ActionOperation['kind'], string> = {
        SET_ATTRIBUTE:'Assigns a fixed, calculated or parameter value to an attribute.', CLEAR_ATTRIBUTE:'Removes the current value of an optional attribute.', COPY_ATTRIBUTE:'Copies one attribute value between selected elements.',
        ADD_RELATION:'Creates a relation link between two selected elements.', REMOVE_RELATION:'Removes a relation link between two selected elements.', CREATE_ELEMENT:'Creates a new instance of a metamodel class.',
        DELETE_ELEMENT:'Deletes a selected model element.', TRANSFORM_TEXT:'Applies a text transformation to an attribute.', EXECUTE_ACTION:'Calls another reusable action without introducing a cycle.',
        INVOKE_OPERATION:'Invokes a command operation defined by the metamodel.'
    };
    return descriptions[kind];
}

function operationKindCategory(kind: ActionOperation['kind']): string {
    if (kind.includes('ATTRIBUTE') || kind === 'TRANSFORM_TEXT') return 'Attributes and text';
    if (kind.includes('RELATION')) return 'Relations';
    if (kind.includes('ELEMENT')) return 'Elements';
    return 'Reusable behaviour';
}


export function QuickFixDesigner({ metamodel, actionModel, expressionModel, selectedActionId, onChange, onSelectAction, onFinishGuided }: QuickFixDesignerProps) {
    const selectedAction = actionModel.actions.find(item => item.id === selectedActionId) ?? null;

    function addAction(): void {
        const action = createDefaultAction();
        onChange({ ...actionModel, actions: [...actionModel.actions, action] });
        onSelectAction(action.id);
    }

    function duplicateAction(action: ActionDefinition): void {
        const copy: ActionDefinition = {
            ...structuredClone(action),
            id: createValidationDesignerId('action'),
            name: `${action.name} copy`,
            operations: action.operations.map(operation => ({ ...operation, operationId: createValidationDesignerId('operation') }))
        };
        onChange({ ...actionModel, actions: [...actionModel.actions, copy] });
        onSelectAction(copy.id);
    }

    function updateAction(action: ActionDefinition): void {
        onChange({ ...actionModel, actions: actionModel.actions.map(item => item.id === action.id ? action : item) });
    }

    function deleteAction(actionId: string): void {
        onChange({ ...actionModel, actions: actionModel.actions.filter(item => item.id !== actionId) });
        onSelectAction('');
    }

    return (
        <section className="quick-fix-designer">
            <aside className="quick-fix-list">
                <div className="validation-panel-heading">
                    <div><h3>Quick fixes</h3><span>{actionModel.actions.length} action(s)</span></div>
                    <div className="validation-panel-heading-actions"><ProcessGuideButton kind="QUICK_FIX" /><HydraButton type="button" onClick={addAction}>+ Quick fix</HydraButton></div>
                </div>
                {actionModel.actions.length === 0 && <div className="validation-empty-state"><strong>No quick fixes yet</strong><span>Create a declarative correction without writing code.</span></div>}
                {actionModel.actions.map(action => (
                    <HydraButton className={`quick-fix-list-item${action.id === selectedActionId ? ' selected' : ''}`} key={action.id} type="button" onClick={() => onSelectAction(action.id)}>
                        <span>⚡</span><span><strong>{action.name}</strong><small>{action.operations.length} operation(s)</small></span>{!action.enabled && <em>Off</em>}
                    </HydraButton>
                ))}
            </aside>

            {selectedAction === null ? (
                <section className="quick-fix-editor validation-empty-state"><strong>Select a quick fix</strong><span>Choose an action or create a new one.</span></section>
            ) : (
                <QuickFixEditor
                    action={selectedAction}
                    actionModel={actionModel}
                    expressionModel={expressionModel}
                    metamodel={metamodel}
                    onChange={updateAction}
                    onDelete={() => deleteAction(selectedAction.id)}
                    onDuplicate={() => duplicateAction(selectedAction)}
                    onFinishGuided={onFinishGuided}
                />
            )}
        </section>
    );
}

interface QuickFixEditorProps {
    action: ActionDefinition;
    actionModel: ActionModel;
    expressionModel: ExpressionModel;
    metamodel: MetaModel;
    onChange: (action: ActionDefinition) => void;
    onDelete: () => void;
    onDuplicate: () => void;
    onFinishGuided?: () => boolean | Promise<boolean>;
}

function QuickFixEditor({ action, actionModel, expressionModel, metamodel, onChange, onDelete, onDuplicate, onFinishGuided }: QuickFixEditorProps) {
    const prefix = `quick-fix-${safeId(action.id)}`;
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('ADVANCED');
    const [guidedStep, setGuidedStep] = useState('identity');

    function addOperation(kind: ActionOperation['kind']): void {
        onChange({ ...action, operations: [...action.operations, createDefaultOperation(kind, metamodel)] });
    }

    function updateOperation(index: number, operation: ActionOperation): void {
        onChange({ ...action, operations: action.operations.map((item, itemIndex) => itemIndex === index ? operation : item) });
    }

    function removeOperation(index: number): void {
        onChange({ ...action, operations: action.operations.filter((_, itemIndex) => itemIndex !== index) });
    }

    function moveOperation(index: number, direction: -1 | 1): void {
        const targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= action.operations.length) return;
        const operations = [...action.operations];
        const [moved] = operations.splice(index, 1);
        operations.splice(targetIndex, 0, moved);
        onChange({ ...action, operations });
    }

    const reviewOnly = action.annotations?.['hydra.import.reviewOnly'] === 'true';
    const identityFields = <><div className="validation-rule-fields two-columns">
        <HelpedField label="Name" targetId={`${prefix}-name`} required help="Human-readable name shown to the model user when the validation problem can be repaired. State the result of the repair, not its implementation details." example="Set age to zero"><input id={`${prefix}-name`} required value={action.name} onChange={event => onChange({ ...action, name: event.target.value })} /></HelpedField>
        <div className="validation-inline-help-field"><FieldLabel label="Enabled" targetId={`${prefix}-enabled`} help="Controls whether this action may be offered as a quick fix. When disabled, it should not be executed by the runtime or offered to the user. Keep it disabled while the operation sequence is incomplete or unsafe, then enable it after the guided checks are green." example="Enable Set age to zero once its target attribute and value are configured." /><input id={`${prefix}-enabled`} type="checkbox" checked={action.enabled} disabled={reviewOnly} onChange={event => onChange({ ...action, enabled: event.target.checked })} /></div>
    </div><HelpedField className="validation-wide-field" label="Description" targetId={`${prefix}-description`} help="Explain at a high level what the repair changes and any relevant consequence for the user." example="Replaces a negative age with the minimum accepted value."><textarea id={`${prefix}-description`} rows={2} value={action.description ?? ''} onChange={event => onChange({ ...action, description: event.target.value })} /></HelpedField></>;

    const safetyFields = <div className="validation-rule-fields two-columns">
        <HelpedField label="Precondition" targetId={`${prefix}-precondition`} help="Optional boolean expression controlling whether this quick fix can be offered for the current model element. Leave it empty when the associated diagnostic alone is enough." example="Offer Set age to zero only when age < 0." options={[{ value:'',label:'Always available',description:'Offer this quick fix whenever the associated diagnostic is present.' }, ...expressionModel.expressions.map(expression => ({ value:expression.id,label:expression.name,description:expression.description || 'Boolean expression evaluated for the current model context.' }))]}><HydraAdaptiveSelect id={`${prefix}-precondition`} ariaLabel="Precondition" value={action.preconditionExpressionId ?? ''} options={[{ value:'',label:'Always available',description:'Offer this quick fix whenever the associated diagnostic is present.',category:'Recommended' }, ...expressionModel.expressions.map(expression => ({ value:expression.id,label:expression.name,description:expression.description || 'Boolean expression evaluated for the current model context.',category:'Expressions' }))]} onChange={preconditionExpressionId => onChange({ ...action, preconditionExpressionId: preconditionExpressionId || undefined })} /></HelpedField>
        <HelpedField label="Transaction" targetId={`${prefix}-transaction`} required help="Defines what happens when any operation fails. All or nothing rolls the complete repair back; best effort preserves successful operations and reports failures. Prefer atomic behaviour for interactive fixes that must preserve model consistency." example="All or nothing for a fix that creates an object and connects it." options={[...TRANSACTION_OPTIONS]}><HydraAdaptiveSelect id={`${prefix}-transaction`} ariaLabel="Transaction" required value={action.transactionPolicy} options={TRANSACTION_OPTIONS} onChange={transactionPolicy => onChange({ ...action, transactionPolicy: transactionPolicy as ActionDefinition['transactionPolicy'] })} /></HelpedField>
        <HelpedField label="Undo support" targetId={`${prefix}-undo`} required help="Declares whether the runtime must be able to reverse the complete repair. Required is recommended for normal interactive editing; optional permits execution if undo cannot be provided; not supported is appropriate only for inherently irreversible operations." example="Required for normal interactive model editing." options={[...UNDO_OPTIONS]}><HydraAdaptiveSelect id={`${prefix}-undo`} ariaLabel="Undo support" required value={action.undoPolicy} options={UNDO_OPTIONS} onChange={undoPolicy => onChange({ ...action, undoPolicy: undoPolicy as ActionDefinition['undoPolicy'] })} /></HelpedField>
    </div>;

    const parameterSection = <section className="hydra-designer-section quick-fix-guided-section">
        <header><div><h4>Quick-fix parameters</h4><p>Optional values or model references supplied when this repair runs. Do not add parameters for values already known at design time.</p></div><HydraButton id={`${prefix}-add-parameter`} type="button" onClick={() => onChange({ ...action, parameters: [...action.parameters, { name: `parameter${action.parameters.length + 1}`, type: 'STRING', required: true }] })}>+ Parameter</HydraButton></header>
        <div className="quick-fix-parameter-list">{action.parameters.map((parameter, index) => { const parameterPrefix = `${prefix}-parameter-${index}`; return <div className="quick-fix-parameter-row" key={`${parameter.name}-${index}`}>
            <HelpedField label="Parameter name" targetId={`${parameterPrefix}-name`} required help="Stable name used by selectors and action calls to access this input. It must be unique within this quick fix." example="replacementAge"><input id={`${parameterPrefix}-name`} required aria-label="Parameter name" value={parameter.name} onChange={event => onChange({ ...action, parameters: action.parameters.map((item, itemIndex) => itemIndex === index ? { ...item, name: event.target.value } : item) })} /></HelpedField>
            <HelpedField label="Parameter type" targetId={`${parameterPrefix}-type`} required help="Type accepted by the parameter. Element and structural ID types are used for model or metamodel references; scalar types carry values." example="INTEGER for a replacement age." options={PARAMETER_TYPE_OPTIONS.map(option => ({ value:option.value,label:option.label,description:option.description }))}><HydraAdaptiveSelect id={`${parameterPrefix}-type`} ariaLabel="Parameter type" required value={parameter.type} options={PARAMETER_TYPE_OPTIONS} onChange={type => onChange({ ...action, parameters: action.parameters.map((item, itemIndex) => itemIndex === index ? { ...item, type:type as typeof parameter.type } : item) })} /></HelpedField>
            <div className="validation-inline-help-field parameter-required-field"><FieldLabel label="Required" targetId={`${parameterPrefix}-required`} help="When selected, execution cannot start until a value is supplied for this parameter." example="A replacement model element must be selected before reconnecting a relation." /><input id={`${parameterPrefix}-required`} type="checkbox" checked={parameter.required} onChange={event => onChange({ ...action, parameters: action.parameters.map((item, itemIndex) => itemIndex === index ? { ...item, required: event.target.checked } : item) })} /></div>
            <HydraButton type="button" onClick={() => onChange({ ...action, parameters: action.parameters.filter((_, itemIndex) => itemIndex !== index) })}>Remove</HydraButton>
        </div>; })}</div>
        {action.parameters.length === 0 && <div className="validation-empty-state compact"><strong>No parameters</strong><span>This is valid when every operation can obtain its values from the current element, fixed values or expressions.</span></div>}
    </section>;

    const operationSection = <section className="hydra-designer-section quick-fix-guided-section">
        <header><div><h4>Operations</h4><p>A quick fix is an ordered sequence of declarative model changes. Add the minimum operations needed to repair the problem.</p></div></header>
        <div className="quick-fix-operation-toolbar"><FieldLabel label="Operation type" targetId={`${prefix}-operation-type`} required help="Choose the next model change to append. Attribute/text operations modify values; relation operations connect elements; element operations create/delete instances; reusable behaviour can call another action or a metamodel command operation." example="1. Set attribute age to 0 · 2. Remove invalid relation" options={operationKinds.map(kind => ({ value:kind,label:operationKindLabel(kind),description:operationKindDescription(kind) }))} /><HydraAdaptiveSelect id={`${prefix}-operation-type`} ariaLabel="Operation type" value="" placeholder="Choose operation" options={operationKinds.map(kind => ({ value:kind,label:operationKindLabel(kind),description:operationKindDescription(kind),category:operationKindCategory(kind) }))} onChange={kind => { if (kind !== '') addOperation(kind as ActionOperation['kind']); }} /></div>
        {reviewOnly && <div className="validation-empty-state compact"><strong>Imported quick fix pending mapping</strong><span>HYDRA preserved the original repair body without inventing executable semantics. Map it to declarative operations before enabling it.</span><code>{action.annotations?.['hydra.import.originalFix'] ?? ''}</code></div>}<div className="quick-fix-operation-list">{action.operations.length === 0 && !reviewOnly && <div className="validation-empty-state validation-required-empty"><strong>No operations</strong><span>Add at least one operation before this quick fix can be saved as executable.</span></div>}{action.operations.map((operation, index) => <div className="quick-fix-operation-card" key={operation.operationId}><div className="quick-fix-operation-header"><strong>{index + 1}. {operationKindLabel(operation.kind)}</strong><div><HydraButton type="button" disabled={index === 0} onClick={() => moveOperation(index, -1)}>↑</HydraButton><HydraButton type="button" disabled={index === action.operations.length - 1} onClick={() => moveOperation(index, 1)}>↓</HydraButton><HydraButton type="button" onClick={() => removeOperation(index)}>Remove</HydraButton></div></div><OperationEditor action={action} actionModel={actionModel} expressionModel={expressionModel} metamodel={metamodel} operation={operation} onChange={updated => updateOperation(index, updated)} /></div>)}</div>
    </section>;

    const parameterNames = action.parameters.map(parameter => parameter.name.trim()).filter(Boolean);
    const parametersComplete = action.parameters.every(parameter => parameter.name.trim() !== '') && new Set(parameterNames).size === parameterNames.length;
    const quickFixError = findQuickFixBlockingError(action);
    const operationsComplete = reviewOnly ? action.operations.length === 0 && !action.enabled : action.operations.length > 0 && quickFixError === '';
    const steps = [
        { id: 'identity', title: 'Identity', description: 'Name the repair and explain its effect.', checks: [
            { id:'name', label:'Quick-fix name is defined', complete:action.name.trim() !== '', detail:'This is what model users see next to the diagnostic.' },
            { id:'description', label:'Repair intent is understandable', complete:(action.description ?? '').trim() !== '', detail:'A description is strongly recommended so similar fixes can be distinguished.' }
        ], content: identityFields },
        { id: 'safety', title: 'Availability and safety', description: 'Choose precondition, transaction and undo semantics.', checks: [
            { id:'transaction', label:'Transaction policy is defined', complete:true, detail:`Current policy: ${action.transactionPolicy}.` },
            { id:'undo', label:'Undo policy is defined', complete:true, detail:`Current policy: ${action.undoPolicy}.` },
            { id:'precondition', label:'Availability condition is valid', complete:true, detail:action.preconditionExpressionId === undefined ? 'Always available for the associated diagnostic.' : `Controlled by ${action.preconditionExpressionId}.` }
        ], content: safetyFields },
        { id: 'parameters', title: 'Parameters', description: 'Define runtime inputs only when the repair needs them.', checks: [
            { id:'parameters-valid', label:'Parameter definitions are complete and unique', complete:parametersComplete, detail:action.parameters.length === 0 ? 'No runtime parameters are needed.' : `${action.parameters.length} parameter(s) configured.` }
        ], content: parameterSection },
        { id: 'operations', title: 'Repair operations', description: 'Build and configure the ordered correction sequence.', checks: [
            { id:'operation-count', label:reviewOnly?'External repair body is preserved for review':'At least one repair operation exists', complete:reviewOnly || action.operations.length > 0, detail:reviewOnly?'No executable HYDRA operation is fabricated until the imported body is mapped safely.':`${action.operations.length} operation(s) configured.` },
            { id:'operation-config', label:'Operation references and values are complete', complete:operationsComplete, detail:quickFixError === '' ? 'All current operation checks pass.' : quickFixError }
        ], content: operationSection },
        { id: 'review', title: 'Review', description: 'Confirm that the action is safe to enable and expose.', checks: [
            { id:'ready', label:'Quick fix passes structural checks', complete:quickFixError === '', detail:quickFixError === '' ? 'No blocking quick-fix error was found.' : quickFixError },
            { id:'enabled', label:'Availability state has been reviewed', complete:true, detail:action.enabled ? 'Enabled: the runtime may offer this repair.' : 'Disabled: the repair remains stored but is not offered.' }
        ], content:<div className="hydra-guided-review-summary"><strong>{action.name || 'Unnamed quick fix'}</strong><span>{action.id}</span><p>{action.description || 'No description provided.'}</p><dl><div><dt>Precondition</dt><dd>{action.preconditionExpressionId ?? 'Always'}</dd></div><div><dt>Transaction</dt><dd>{action.transactionPolicy}</dd></div><div><dt>Undo</dt><dd>{action.undoPolicy}</dd></div><div><dt>Operations</dt><dd>{action.operations.length}</dd></div></dl></div> }
    ];

    return <section className="quick-fix-editor">
        <div className="validation-rule-editor-header"><div><h3>{action.name}</h3><span>{action.id}</span></div><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full editor" /><HydraButton type="button" onClick={onDuplicate}>Duplicate</HydraButton><HydraButton className="validation-danger-button" type="button" onClick={onDelete}>Delete</HydraButton></div>
        <RequiredFieldsLegend />
        {creationMode === 'GUIDED'
            ? <HydraGuidedWorkflow key={action.id} title="Guided quick-fix definition" description="HYDRA checks identity, safety, parameters and every declarative repair operation before allowing the next block." steps={steps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Save quick fix" onFinish={onFinishGuided} />
            : <>{identityFields}<section className="hydra-designer-section quick-fix-guided-section"><header><div><h4>Availability and execution safety</h4><p>Full transaction, undo and precondition controls.</p></div></header>{safetyFields}</section><details className="validation-advanced-section"><summary>Quick-fix parameters</summary>{parameterSection}</details>{operationSection}</>}
    </section>;
}

interface OperationEditorProps {
    action: ActionDefinition;
    actionModel: ActionModel;
    expressionModel: ExpressionModel;
    metamodel: MetaModel;
    operation: ActionOperation;
    onChange: (operation: ActionOperation) => void;
}

function OperationEditor({ action, actionModel, expressionModel, metamodel, operation, onChange }: OperationEditorProps) {
    const prefix = `operation-${safeId(operation.operationId)}`;
    if (operation.kind === 'SET_ATTRIBUTE') {
        return <div className="quick-fix-operation-fields">
            <SelectorEditor action={action} idPrefix={`${prefix}-target`} selector={operation.target} onChange={target => onChange({ ...operation, target })} />
            <AttributeSelect id={`${prefix}-attribute`} value={operation.attributeId} metamodel={metamodel} onChange={attributeId => onChange({ ...operation, attributeId })} />
            <ValueSourceEditor action={action} expressionModel={expressionModel} idPrefix={`${prefix}-value`} value={operation.value} onChange={value => onChange({ ...operation, value })} />
        </div>;
    }
    if (operation.kind === 'CLEAR_ATTRIBUTE') {
        return <div className="quick-fix-operation-fields"><SelectorEditor action={action} idPrefix={`${prefix}-target`} selector={operation.target} onChange={target => onChange({ ...operation, target })} /><AttributeSelect id={`${prefix}-attribute`} value={operation.attributeId} metamodel={metamodel} onChange={attributeId => onChange({ ...operation, attributeId })} /></div>;
    }
    if (operation.kind === 'COPY_ATTRIBUTE') {
        return <div className="quick-fix-operation-fields"><SelectorEditor action={action} idPrefix={`${prefix}-source`} label="Source element" selector={operation.source} onChange={source => onChange({ ...operation, source })} /><AttributeSelect id={`${prefix}-source-attribute`} label="Source attribute" value={operation.sourceAttributeId} metamodel={metamodel} onChange={sourceAttributeId => onChange({ ...operation, sourceAttributeId })} /><SelectorEditor action={action} idPrefix={`${prefix}-target`} label="Target element" selector={operation.target} onChange={target => onChange({ ...operation, target })} /><AttributeSelect id={`${prefix}-target-attribute`} label="Target attribute" value={operation.targetAttributeId} metamodel={metamodel} onChange={targetAttributeId => onChange({ ...operation, targetAttributeId })} /></div>;
    }
    if (operation.kind === 'ADD_RELATION' || operation.kind === 'REMOVE_RELATION') {
        return <div className="quick-fix-operation-fields"><HelpedField label="Relation" targetId={`${prefix}-relation`} required help="Metamodel relation to create or remove between the selected source and target elements." example="Company.employees" options={metamodel.relations.map(relation => ({ value:relation.id,label:relation.name || relation.kind,description:`${relation.kind.toLowerCase()} relation between metamodel types.` }))}><HydraAdaptiveSelect id={`${prefix}-relation`} ariaLabel="Relation" required value={operation.relationId} placeholder="Select relation" options={metamodel.relations.map(relation => ({ value:relation.id,label:relation.name || relation.kind,description:`${relation.kind.toLowerCase()} relation between metamodel types.`,category:operation.kind === 'ADD_RELATION' ? 'Available relations' : 'Removable relations' }))} onChange={relationId => onChange({ ...operation, relationId })} /></HelpedField><SelectorEditor action={action} idPrefix={`${prefix}-source`} label="Source" selector={operation.source} onChange={source => onChange({ ...operation, source })} /><SelectorEditor action={action} idPrefix={`${prefix}-target`} label="Target" selector={operation.target} onChange={target => onChange({ ...operation, target })} /></div>;
    }
    if (operation.kind === 'CREATE_ELEMENT') {
        return <div className="quick-fix-operation-fields"><HelpedField label="Class" targetId={`${prefix}-class`} required help="Metamodel class of the new model element." example="Address" options={metamodel.classes.map(metaClass => ({ value:metaClass.id,label:metaClass.name,description:`${metaClass.kind.toLowerCase().replaceAll('_',' ')} in the current metamodel.` }))}><HydraAdaptiveSelect id={`${prefix}-class`} ariaLabel="Class" required value={operation.classId} placeholder="Select class" options={metamodel.classes.map(metaClass => ({ value:metaClass.id,label:metaClass.name,description:`${metaClass.kind.toLowerCase().replaceAll('_',' ')} in the current metamodel.`,category:metaClass.kind === 'CLASS' ? 'Concrete classes' : metaClass.kind === 'ABSTRACT_CLASS' ? 'Abstract classes' : 'Interfaces' }))} onChange={classId => onChange({ ...operation, classId })} /></HelpedField><HelpedField label="Result name" targetId={`${prefix}-result-name`} required help="Local stable name used by later operations in this same quick fix to refer to the created element." example="createdAddress"><input id={`${prefix}-result-name`} required value={operation.resultName} onChange={event => onChange({ ...operation, resultName: event.target.value })} /></HelpedField><HelpedField label="Containment relation" targetId={`${prefix}-containment`} help="Optional containment used to insert the new element into the model automatically." example="Person.addresses" options={[{ value:'',label:'No automatic containment',description:'Create the element without inserting it into a containment feature.' }, ...metamodel.relations.filter(relation => relation.kind === 'CONTAINMENT').map(relation => ({ value:relation.id,label:relation.name || 'Unnamed containment',description:'Containment relation that will own the newly created element.' }))]}><HydraAdaptiveSelect id={`${prefix}-containment`} ariaLabel="Containment relation" value={operation.containmentRelationId ?? ''} options={[{ value:'',label:'No automatic containment',description:'Create the element without inserting it into a containment feature.',category:'No containment' }, ...metamodel.relations.filter(relation => relation.kind === 'CONTAINMENT').map(relation => ({ value:relation.id,label:relation.name || 'Unnamed containment',description:'Containment relation that will own the newly created element.',category:'Containment relations' }))]} onChange={containmentRelationId => { onChange(containmentRelationId === '' ? { ...operation, containmentRelationId: undefined, container: undefined } : { ...operation, containmentRelationId, container: operation.container ?? { kind: 'SELF' } }); }} /></HelpedField>{operation.containmentRelationId !== undefined && <SelectorEditor action={action} idPrefix={`${prefix}-container`} required label="Container" selector={operation.container ?? { kind: 'SELF' }} onChange={container => onChange({ ...operation, container })} />}</div>;
    }
    if (operation.kind === 'DELETE_ELEMENT') {
        return <div className="quick-fix-operation-fields"><SelectorEditor action={action} idPrefix={`${prefix}-target`} selector={operation.target} onChange={target => onChange({ ...operation, target })} /></div>;
    }
    if (operation.kind === 'TRANSFORM_TEXT') {
        return <div className="quick-fix-operation-fields"><SelectorEditor action={action} idPrefix={`${prefix}-target`} selector={operation.target} onChange={target => onChange({ ...operation, target })} /><AttributeSelect id={`${prefix}-attribute`} value={operation.attributeId} metamodel={metamodel} onChange={attributeId => onChange({ ...operation, attributeId })} /><HelpedField label="Transformation" targetId={`${prefix}-transformation`} required help="Text operation applied to the selected attribute." example="Capitalize converts alice to Alice." options={[{ value:'UPPERCASE',label:'Uppercase',description:'Converts every character to uppercase.' },{ value:'LOWERCASE',label:'Lowercase',description:'Converts every character to lowercase.' },{ value:'CAPITALIZE',label:'Capitalize',description:'Uppercases the first character while preserving the remaining text.' },{ value:'TRIM',label:'Trim spaces',description:'Removes leading and trailing whitespace.' }]}><HydraAdaptiveSelect id={`${prefix}-transformation`} ariaLabel="Transformation" required value={operation.transformation} options={[{ value:'UPPERCASE',label:'Uppercase',description:'Converts every character to uppercase.',category:'Letter case' },{ value:'LOWERCASE',label:'Lowercase',description:'Converts every character to lowercase.',category:'Letter case' },{ value:'CAPITALIZE',label:'Capitalize',description:'Uppercases the first character while preserving the remaining text.',category:'Letter case' },{ value:'TRIM',label:'Trim spaces',description:'Removes leading and trailing whitespace.',category:'Whitespace' }]} onChange={transformation => onChange({ ...operation, transformation: transformation as TextTransformation })} /></HelpedField></div>;
    }
    if (operation.kind === 'EXECUTE_ACTION') {
        const nested = actionModel.actions.find(item => item.id === operation.actionId);
        return <div className="quick-fix-operation-fields">
            <HelpedField label="Action" targetId={`${prefix}-action`} required help="Another reusable action that will be executed at this point. Direct and indirect cycles are rejected." example="Normalize customer data" options={actionModel.actions.filter(item => item.id !== action.id).map(item => ({ value:item.id,label:item.name,description:item.description || 'Reusable declarative action.' }))}>
                <HydraAdaptiveSelect id={`${prefix}-action`} ariaLabel="Action" required value={operation.actionId} placeholder="Select action" options={actionModel.actions.filter(item => item.id !== action.id).map(item => ({ value:item.id,label:item.name,description:item.description || 'Reusable declarative action.',category:item.enabled ? 'Enabled actions' : 'Disabled actions',disabled:!item.enabled }))} onChange={actionId => {
                    const selected = actionModel.actions.find(item => item.id === actionId);
                    const args = Object.fromEntries((selected?.parameters ?? []).map(parameter => [parameter.name, { parameterName: action.parameters.find(item => item.name === parameter.name)?.name ?? '' }]));
                    onChange({ ...operation, actionId, arguments: args });
                }} />
            </HelpedField>
            {nested?.parameters.map(parameter => <ValueSourceEditor key={parameter.name} action={action} expressionModel={expressionModel} idPrefix={`${prefix}-argument-${safeId(parameter.name)}`} label={parameter.name} value={operation.arguments[parameter.name] ?? { parameterName: '' }} onChange={value => onChange({ ...operation, arguments: { ...operation.arguments, [parameter.name]: value } })} />)}
        </div>;
    }
    if (operation.kind === 'INVOKE_OPERATION') {
        const commandOperations = metamodel.classes.flatMap(owner => owner.operations.filter(item => item.behaviorKind === 'COMMAND').map(item => ({ owner, operation: item })));
        const selected = commandOperations.find(item => item.operation.id === operation.metamodelOperationId)?.operation;
        return <div className="quick-fix-operation-fields">
            <SelectorEditor action={action} idPrefix={`${prefix}-target`} label="Operation target" selector={operation.target} onChange={target => onChange({ ...operation, target })} />
            <HelpedField label="Command operation" targetId={`${prefix}-metamodel-operation`} required help="COMMAND operation whose independent ACTION binding will be executed on the selected target." example="Customer.normalizeName" options={commandOperations.map(({ owner, operation:item }) => ({ value:item.id,label:`${owner.name}.${item.name}`,description:`Command with ${item.parameters.length} parameter(s).` }))}>
                <HydraAdaptiveSelect id={`${prefix}-metamodel-operation`} ariaLabel="Command operation" required value={operation.metamodelOperationId} placeholder="Select command operation" options={commandOperations.map(({ owner, operation:item }) => ({ value:item.id,label:`${owner.name}.${item.name}`,description:`Command with ${item.parameters.length} parameter(s).`,category:owner.name }))} onChange={metamodelOperationId => {
                    const invoked = commandOperations.find(item => item.operation.id === metamodelOperationId)?.operation;
                    onChange({ ...operation, metamodelOperationId, arguments: invoked?.parameters.map(() => ({ literal: '' })) ?? [] });
                }} />
            </HelpedField>
            {operation.arguments.map((value, index) => <ValueSourceEditor key={selected?.parameters[index]?.id ?? index} action={action} expressionModel={expressionModel} idPrefix={`${prefix}-argument-${index}`} label={selected?.parameters[index]?.name ?? `Argument ${index + 1}`} value={value} onChange={updated => onChange({ ...operation, arguments: operation.arguments.map((item, itemIndex) => itemIndex === index ? updated : item) })} />)}
        </div>;
    }

    const exhaustive: never = operation;
    return exhaustive;
}


function ValueSourceEditor({ action, expressionModel, value, onChange, idPrefix, label = 'Value source' }: { action: ActionDefinition; expressionModel: ExpressionModel; value: ActionValueSource; onChange: (value: ActionValueSource) => void; idPrefix: string; label?: string }) {
    const mode = value.expressionId !== undefined ? 'EXPRESSION' : value.parameterName !== undefined ? 'PARAMETER' : 'LITERAL';
    return <div className="validation-value-source-editor">
        <HelpedField label={label} targetId={`${idPrefix}-mode`} required help="Value can be fixed, calculated by an expression or received from the current action parameters." example="Parameter replacementAge" options={[...VALUE_SOURCE_OPTIONS]}>
            <HydraAdaptiveSelect id={`${idPrefix}-mode`} ariaLabel={label} required value={mode} options={VALUE_SOURCE_OPTIONS} onChange={nextMode => {
                if (nextMode === 'EXPRESSION') onChange({ expressionId: expressionModel.expressions[0]?.id ?? '' });
                else if (nextMode === 'PARAMETER') onChange({ parameterName: action.parameters[0]?.name ?? '' });
                else onChange({ literal: '' });
            }} />
        </HelpedField>
        {mode === 'LITERAL' && <HelpedField label="Fixed value" targetId={`${idPrefix}-literal`} required help="Value used directly by the action." example="0"><input id={`${idPrefix}-literal`} value={value.literal === undefined || value.literal === null ? '' : String(value.literal)} onChange={event => onChange({ literal: event.target.value })} /></HelpedField>}
        {mode === 'EXPRESSION' && <HelpedField label="Expression" targetId={`${idPrefix}-expression`} required help="Expression evaluated at execution time using the current action context." example="Normalized name" options={expressionModel.expressions.map(expression => ({ value:expression.id,label:expression.name,description:expression.description || 'Reusable expression.' }))}><HydraAdaptiveSelect id={`${idPrefix}-expression`} ariaLabel="Expression" required value={value.expressionId ?? ''} placeholder="Select expression" options={expressionModel.expressions.map(expression => ({ value:expression.id,label:expression.name,description:expression.description || 'Reusable expression.',category:'Expressions' }))} onChange={expressionId => onChange({ expressionId })} /></HelpedField>}
        {mode === 'PARAMETER' && <HelpedField label="Parameter" targetId={`${idPrefix}-parameter`} required help="Current action parameter that supplies this value." example="replacementAge" options={action.parameters.map(parameter => ({ value:parameter.name,label:parameter.name,description:`${parameter.type.toLowerCase().replaceAll('_',' ')} parameter${parameter.required ? ' (required)' : ''}.` }))}><HydraAdaptiveSelect id={`${idPrefix}-parameter`} ariaLabel="Parameter" required value={value.parameterName ?? ''} placeholder="Select parameter" options={action.parameters.map(parameter => ({ value:parameter.name,label:parameter.name,description:`${parameter.type.toLowerCase().replaceAll('_',' ')} parameter${parameter.required ? ' (required)' : ''}.`,category:parameter.required ? 'Required parameters' : 'Optional parameters' }))} onChange={parameterName => onChange({ parameterName })} /></HelpedField>}
    </div>;
}

function SelectorEditor({ action, selector, onChange, label = 'Element', idPrefix, required = true }: { action: ActionDefinition; selector: ActionSelector; onChange: (selector: ActionSelector) => void; label?: string; idPrefix: string; required?: boolean }) {
    const createdNames = action.operations.filter(operation => operation.kind === 'CREATE_ELEMENT').map(operation => operation.resultName);
    const selectorId = `${idPrefix}-selector`;
    return <div className="validation-selector-editor"><HelpedField label={label} targetId={selectorId} required={required} help="Chooses which model element the operation reads or changes. Conditional selector details become required for parameters, created elements and explicit IDs." example="Current element" options={[...SELECTOR_KIND_OPTIONS]}><HydraAdaptiveSelect id={selectorId} ariaLabel={label} required={required} value={selector.kind} options={SELECTOR_KIND_OPTIONS} onChange={kind => onChange(selectorForKind(kind as ActionSelectorKind))} /></HelpedField>{selector.kind === 'PARAMETER' && <HelpedField label="Selected parameter" targetId={`${idPrefix}-parameter`} required help="Parameter that supplies the model element at execution time." example="replacementElement" options={action.parameters.map(parameter => ({ value:parameter.name,label:parameter.name,description:`${parameter.type.toLowerCase().replaceAll('_',' ')} parameter${parameter.required ? ' (required)' : ''}.` }))}><HydraAdaptiveSelect id={`${idPrefix}-parameter`} required ariaLabel="Selected parameter" value={selector.parameterName ?? ''} placeholder="Select parameter" options={action.parameters.map(parameter => ({ value:parameter.name,label:parameter.name,description:`${parameter.type.toLowerCase().replaceAll('_',' ')} parameter${parameter.required ? ' (required)' : ''}.`,category:parameter.required ? 'Required parameters' : 'Optional parameters' }))} onChange={parameterName => onChange({ ...selector, parameterName })} /></HelpedField>}{selector.kind === 'CREATED_ELEMENT' && <HelpedField label="Created element name" targetId={`${idPrefix}-created`} required help="Result name of a previous Create element operation in this quick fix." example="createdAddress" options={createdNames.map(name => ({ value:name,label:name,description:'Result produced by a previous Create element operation.' }))}><HydraAdaptiveSelect id={`${idPrefix}-created`} required ariaLabel="Created element name" value={selector.parameterName ?? ''} placeholder="Select created element" options={createdNames.map(name => ({ value:name,label:name,description:'Result produced by a previous Create element operation.',category:'Created elements' }))} onChange={parameterName => onChange({ ...selector, parameterName })} /></HelpedField>}{selector.kind === 'ELEMENT_ID' && <HelpedField label="Element ID" targetId={`${idPrefix}-element-id`} required help="Stable identifier of a specific model element. Prefer contextual selectors when the ID is not known at design time." example="object:main-customer"><input id={`${idPrefix}-element-id`} required placeholder="Element ID" value={selector.elementId ?? ''} onChange={event => onChange({ ...selector, elementId: event.target.value })} /></HelpedField>}</div>;
}

function AttributeSelect({ value, metamodel, onChange, label = 'Attribute', id }: { value: string; metamodel: MetaModel; onChange: (attributeId: string) => void; label?: string; id: string }) {
    const attributes = allAttributes(metamodel);
    return <HelpedField label={label} targetId={id} required help="Metamodel attribute read or modified by this operation." example="Person.age" options={attributes.map(({ owner, attribute }) => ({ value:attribute.id,label:`${owner.name}.${attribute.name}`,description:`Attribute declared by ${owner.name}.` }))}><HydraAdaptiveSelect id={id} ariaLabel={label} required value={value} placeholder="Select attribute" options={attributes.map(({ owner, attribute }) => ({ value:attribute.id,label:`${owner.name}.${attribute.name}`,description:`Attribute declared by ${owner.name}.`,category:owner.name }))} onChange={onChange} /></HelpedField>;
}

function selectorForKind(kind: ActionSelectorKind): ActionSelector {
    if (kind === 'PARAMETER' || kind === 'CREATED_ELEMENT') return { kind, parameterName: '' };
    if (kind === 'ELEMENT_ID') return { kind, elementId: '' };
    return { kind };
}

function operationKindLabel(kind: ActionOperation['kind']): string {
    const labels: Record<ActionOperation['kind'], string> = {
        SET_ATTRIBUTE: 'Set attribute', CLEAR_ATTRIBUTE: 'Clear attribute', COPY_ATTRIBUTE: 'Copy attribute',
        ADD_RELATION: 'Add relation', REMOVE_RELATION: 'Remove relation', CREATE_ELEMENT: 'Create element',
        DELETE_ELEMENT: 'Delete element', TRANSFORM_TEXT: 'Transform text', EXECUTE_ACTION: 'Execute another action',
        INVOKE_OPERATION: 'Invoke command operation'
    };
    return labels[kind];
}

function safeId(value: string): string {
    return value.replace(/[^A-Za-z0-9_-]/g, '-');
}
