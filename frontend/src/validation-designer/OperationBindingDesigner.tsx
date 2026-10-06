import type { MetaModel, MetaOperation } from '../metamodel-designer/types';
import { operationBehaviorExportUrl } from '../validation/operationBehaviorApi';
import type { ActionModel, ExpressionModel, OperationBehaviorModel, OperationBinding, OperationImplementationKind } from '../validation/types';
import { HelpedField } from './ValidationFieldHelp';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';

interface Props {
    metamodel: MetaModel;
    expressionModel: ExpressionModel;
    actionModel: ActionModel;
    behaviorModel: OperationBehaviorModel;
    onChange: (model: OperationBehaviorModel) => void;
}
const surfaces = ['BUTTON', 'FORM', 'PALETTE', 'MENU'] as const;
const IMPLEMENTATION_HELP: Record<OperationImplementationKind, { label: string; description: string; category: string }> = {
    UNIMPLEMENTED: { label: 'Not implemented', description: 'Keeps the signature available without executable behavior.', category: 'Draft' },
    EXPRESSION: { label: 'Expression', description: 'Evaluates a neutral expression and returns its value.', category: 'HYDRA implementation' },
    ACTION: { label: 'Action', description: 'Executes a transactional ActionModel command.', category: 'HYDRA implementation' },
    EXTERNAL: { label: 'External service', description: 'Delegates execution to an adapter or external runtime.', category: 'External implementation' }
};


export function OperationBindingDesigner({ metamodel, expressionModel, actionModel, behaviorModel, onChange }: Props) {
    const operations = metamodel.classes.flatMap(owner => owner.operations.map(operation => ({ owner, operation })));
    function bindingFor(operation: MetaOperation): OperationBinding {
        return behaviorModel.bindings.find(item => item.operationId === operation.id) ?? { operationId: operation.id, implementationKind: 'UNIMPLEMENTED', annotations: {} };
    }
    function update(operation: MetaOperation, binding: OperationBinding): void {
        const normalized = { ...binding, operationId: operation.id };
        onChange({ ...behaviorModel, bindings: [...behaviorModel.bindings.filter(item => item.operationId !== operation.id), normalized] });
    }
    return <section className="operation-binding-designer">
        <header className="validation-panel-heading">
            <div><h3>Operation implementations</h3><span>{operations.length} operation(s)</span></div>
            <a className="validation-export-link" href={operationBehaviorExportUrl(metamodel.id)} download>Export bindings JSON</a>
        </header>
        <p>Signatures remain in MetaModel 2.1. This document only connects each operation to an expression, action or external implementation and defines where it appears in the runtime editor.</p>
        {operations.map(({ owner, operation }) => {
            const binding = bindingFor(operation);
            const allowed = operation.behaviorKind === 'COMMAND'
                ? ['UNIMPLEMENTED', 'ACTION', 'EXTERNAL'] as OperationImplementationKind[]
                : ['UNIMPLEMENTED', 'EXPRESSION', 'EXTERNAL'] as OperationImplementationKind[];
            const selectedSurfaces = (binding.annotations?.['ui.surfaces'] ?? '').split(',').filter(Boolean);
            return <article className="operation-binding-card" key={operation.id}>
                <div><strong>{owner.name}.{operation.name}</strong><span>{operation.behaviorKind} · {operation.parameters.length} parameter(s)</span></div>
                <div className="validation-rule-fields two-columns">
                    <HelpedField label="Implementation" targetId={`binding-${safeId(operation.id)}-kind`} required help="Authoritative implementation kept outside the structural metamodel." example={operation.behaviorKind === 'COMMAND' ? 'Action' : 'Expression'} options={allowed.map(kind => ({ value: kind, label: IMPLEMENTATION_HELP[kind].label, description: IMPLEMENTATION_HELP[kind].description }))}>
                        <HydraAdaptiveSelect id={`binding-${safeId(operation.id)}-kind`} ariaLabel="Implementation" required value={binding.implementationKind} options={allowed.map(kind => ({ value: kind, ...IMPLEMENTATION_HELP[kind] }))} onChange={kind => update(operation, resetBinding(operation.id, kind as OperationImplementationKind, binding.annotations))} />
                    </HelpedField>
                    {binding.implementationKind === 'EXPRESSION' && <HelpedField label="Expression" targetId={`binding-${safeId(operation.id)}-expression`} required help="Expression evaluated with the operation target as self and its parameters as named variables." example="Display name expression" options={expressionModel.expressions.map(expression => ({ value:expression.id,label:expression.name,description:expression.description || 'Reusable neutral expression.' }))}><HydraAdaptiveSelect id={`binding-${safeId(operation.id)}-expression`} ariaLabel="Expression" required value={binding.expressionId ?? ''} placeholder="Select expression" options={expressionModel.expressions.map(expression => ({ value:expression.id,label:expression.name,description:expression.description || 'Reusable neutral expression.',category:'Expressions' }))} onChange={expressionId => update(operation, { ...binding, expressionId })} /></HelpedField>}
                    {binding.implementationKind === 'ACTION' && <HelpedField label="Action" targetId={`binding-${safeId(operation.id)}-action`} required help="Action executed transactionally when the COMMAND operation is invoked." example="Normalize customer" options={actionModel.actions.map(action => ({ value:action.id,label:action.name,description:action.description || 'Reusable transactional action.' }))}><HydraAdaptiveSelect id={`binding-${safeId(operation.id)}-action`} ariaLabel="Action" required value={binding.actionId ?? ''} placeholder="Select action" options={actionModel.actions.map(action => ({ value:action.id,label:action.name,description:action.description || 'Reusable transactional action.',category:action.enabled ? 'Enabled actions' : 'Disabled actions',disabled:!action.enabled }))} onChange={actionId => update(operation, { ...binding, actionId })} /></HelpedField>}
                    {binding.implementationKind === 'EXTERNAL' && <HelpedField label="External reference" targetId={`binding-${safeId(operation.id)}-external`} required help="Stable identifier of an implementation supplied by an external runtime." example="service:customer/displayName"><input id={`binding-${safeId(operation.id)}-external`} required value={binding.externalReference ?? ''} onChange={event => update(operation, { ...binding, externalReference: event.target.value })} /></HelpedField>}
                </div>
                <fieldset className="operation-surface-options"><legend>Runtime surfaces</legend>{surfaces.map(surface => <label key={surface}><input type="checkbox" checked={selectedSurfaces.includes(surface)} onChange={event => { const next = event.target.checked ? [...selectedSurfaces, surface] : selectedSurfaces.filter(item => item !== surface); update(operation, { ...binding, annotations: { ...(binding.annotations ?? {}), 'ui.surfaces': next.join(',') } }); }} />{surface.toLowerCase()}</label>)}</fieldset>
            </article>;
        })}
    </section>;
}
function resetBinding(operationId: string, implementationKind: OperationImplementationKind, annotations?: Record<string, string>): OperationBinding { return { operationId, implementationKind, annotations: annotations ?? {} }; }
function safeId(value: string): string { return value.replace(/[^A-Za-z0-9_-]/g, '-'); }
