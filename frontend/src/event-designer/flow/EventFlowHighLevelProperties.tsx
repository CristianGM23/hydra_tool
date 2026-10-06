import { HydraButton } from '../../design-system/HydraButton';
import { HydraAdaptiveSelect } from '../../components/HydraAdaptiveSelect';
import { DesignerGuideButton, DesignerHelpedCheckbox, DesignerHelpedField, DesignerRequiredFieldsLegend } from '../../designer-ui/DesignerFieldHelp';
import { useHydraLanguage } from '../../i18n';
import { eventTypeOptionHelp, presentEventType } from '../eventTypePresentation';
import type { EventTypeDefinition } from '../types';
import { FLOW_NODE_PRESENTATIONS, FLOW_TRANSITION_PRESENTATIONS } from './flowPresentation';
import type { FlowNode, FlowNodeKind, FlowTransition, FlowTransitionKind } from './types';

const NODE_KINDS: FlowNodeKind[] = [
    'START','SOURCE','EVENT_STEP','FILTER','MAP','ROUTE','MERGE','DEDUPLICATE','WINDOW','AGGREGATE','JOIN','SEQUENCE','PATTERN','ABSENCE',
    'ACTION_STEP','OPERATION_STEP','COMMAND_STEP','EMIT','SUCCESS_END','ERROR_END','COMPENSATION_STEP','ERROR_HANDLER','DECISION','PARALLEL_SPLIT',
    'TIMEOUT','RETRY','WAIT_FOR_EVENT','MANUAL_STEP','FALLBACK'
];
const TRANSITION_KINDS: FlowTransitionKind[] = ['NEXT','ON_SUCCESS','ON_ERROR','COMPENSATES','TIMEOUT','TRUE','FALSE'];
const EVENT_KINDS = new Set<FlowNodeKind>(['SOURCE','EVENT_STEP','EMIT','WAIT_FOR_EVENT','ABSENCE']);
const REFERENCE_KINDS = new Set<FlowNodeKind>(['ACTION_STEP','OPERATION_STEP','COMMAND_STEP','MANUAL_STEP','COMPENSATION_STEP','ERROR_HANDLER']);
const CONDITION_KINDS = new Set<FlowNodeKind>(['FILTER','ROUTE','DECISION','PATTERN','SEQUENCE','ABSENCE','FALLBACK']);
const RESILIENCE_KINDS = new Set<FlowNodeKind>(['ACTION_STEP','OPERATION_STEP','COMMAND_STEP','MANUAL_STEP','WAIT_FOR_EVENT','RETRY','TIMEOUT','ERROR_HANDLER','COMPENSATION_STEP']);
const TIMEOUT_KINDS = new Set<FlowNodeKind>(['ACTION_STEP','OPERATION_STEP','COMMAND_STEP','MANUAL_STEP','WAIT_FOR_EVENT','WINDOW','ABSENCE','TIMEOUT','RETRY']);
const CONFIGURATION_KINDS = new Set<FlowNodeKind>(['MAP','MERGE','DEDUPLICATE','WINDOW','AGGREGATE','JOIN','SEQUENCE','PATTERN','ABSENCE','PARALLEL_SPLIT','RETRY','TIMEOUT']);

function nodeCategory(kind: FlowNodeKind): string {
    if (['START','SUCCESS_END','ERROR_END'].includes(kind)) return 'Lifecycle';
    if (['SOURCE','EVENT_STEP','WAIT_FOR_EVENT','EMIT','MAP','FILTER','ROUTE'].includes(kind)) return 'Events and data';
    if (['MERGE','DEDUPLICATE','WINDOW','AGGREGATE','JOIN','SEQUENCE','PATTERN','ABSENCE'].includes(kind)) return 'Correlation and state';
    if (['ACTION_STEP','OPERATION_STEP','COMMAND_STEP','MANUAL_STEP'].includes(kind)) return 'Execution';
    return 'Control and resilience';
}
function transitionCategory(kind: FlowTransitionKind): string {
    if (kind === 'ON_ERROR' || kind === 'TIMEOUT' || kind === 'COMPENSATES') return 'Recovery';
    if (kind === 'TRUE' || kind === 'FALSE') return 'Decision';
    return 'Normal flow';
}

/** Panel de propiedades progresivo: muestra primero intención y solo después parámetros específicos/técnicos. */
export function FlowNodeProperties({ node, transitions, eventTypeIds, customEventTypes, onChange, onRenameId, onDelete }: {
    node: FlowNode;
    transitions: FlowTransition[];
    eventTypeIds: string[];
    customEventTypes: EventTypeDefinition[];
    onChange: (node: FlowNode) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onDelete: () => void;
}) {
    const { t } = useHydraLanguage();
    const eventIds = [...new Set([...eventTypeIds, node.eventTypeId].filter(Boolean))];
    const presentation = FLOW_NODE_PRESENTATIONS[node.kind];
    const showEvent = EVENT_KINDS.has(node.kind);
    const showReference = REFERENCE_KINDS.has(node.kind);
    const showCondition = CONDITION_KINDS.has(node.kind);
    const showResilience = RESILIENCE_KINDS.has(node.kind);
    const showTimeout = TIMEOUT_KINDS.has(node.kind);
    const showConfiguration = CONFIGURATION_KINDS.has(node.kind);

    return <section className="event-flow-form event-flow-progressive-properties">
        <div className="hydra-designer-form-header"><div><h3>{node.name}</h3><p>{t(presentation.label)} · {t(nodeCategory(node.kind))}</p></div><div className="hydra-designer-form-actions">
            <DesignerHelpedCheckbox className="compact" label={t('Enabled')} targetId="flow-node-enabled" help={t('Turns this step on or off without deleting it. Disable it temporarily while testing an alternative route.')} example={t('Enabled while the flow is in production.')} checked={node.enabled} onChange={enabled => onChange({ ...node, enabled })} />
            <DesignerGuideButton kind="EVENT_FLOW"/><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>{t('Delete node')}</HydraButton>
        </div></div>
        <div className="event-flow-property-summary"><strong>{t(presentation.label)}</strong><span>{t(presentation.description)}</span><small>{t('Only the properties that affect this kind of step are shown below. Technical identifiers remain available under Advanced.')}</small></div>
        <DesignerRequiredFieldsLegend/>

        <section className="event-flow-property-section"><header><div><h4>{t('Identity and purpose')}</h4><p>{t('Name the step and choose what role it has in the process.')}</p></div></header><div className="hydra-designer-fields">
            <DesignerHelpedField label={t('Name')} targetId="flow-node-name" required help={t('Human-readable name shown on the diagram and in execution traces. Use a short verb phrase that tells the user what happens here.')} example={t('Aggregate order events')}><input id="flow-node-name" value={node.name} onChange={event => onChange({ ...node, name: event.target.value })}/></DesignerHelpedField>
            <DesignerHelpedField label={t('Step type')} targetId="flow-node-kind" required help={t('Defines the behavior of this card. Changing the type also changes which properties HYDRA asks you to configure.')} example={t('Aggregate for accumulating several related events.')} options={NODE_KINDS.map(kind => ({ value:kind,label:t(FLOW_NODE_PRESENTATIONS[kind].label),description:t(FLOW_NODE_PRESENTATIONS[kind].description) }))}><HydraAdaptiveSelect showOptionHelp={false} id="flow-node-kind" ariaLabel={t('Step type')} value={node.kind} options={NODE_KINDS.map(kind => ({ value:kind,label:t(FLOW_NODE_PRESENTATIONS[kind].label),description:t(FLOW_NODE_PRESENTATIONS[kind].description),category:t(nodeCategory(kind)) }))} onChange={kind => onChange({ ...node, kind:kind as FlowNodeKind })}/></DesignerHelpedField>
        </div></section>

        {(showEvent || showReference || showCondition) && <section className="event-flow-property-section"><header><div><h4>{t('What this step uses')}</h4><p>{t('Connect the step to the event, operation or condition that gives it meaning.')}</p></div></header><div className="hydra-designer-fields">
            {showEvent && <DesignerHelpedField label={t('Event')} targetId="flow-node-event-type" help={t('Select the event consumed, awaited or emitted by this step. For example, a Wait for event step can pause until “Payment received” arrives.')} example={t('Payment received')} options={[{value:'',label:t('No event selected'),description:t('Use this only when the step does not depend on one event.')},...eventTypeOptionHelp(eventIds,customEventTypes)]}><HydraAdaptiveSelect showOptionHelp={false} id="flow-node-event-type" ariaLabel={t('Event')} value={node.eventTypeId} options={[{value:'',label:t('No event selected'),description:t('No event is associated with this step.'),category:t('Optional')},...eventIds.map(id=>{const item=presentEventType(id,customEventTypes);return {value:id,label:item.label,description:item.description,category:item.category};})]} onChange={eventTypeId=>onChange({...node,eventTypeId})}/></DesignerHelpedField>}
            {showReference && <DesignerHelpedField label={t('Action or operation')} targetId="flow-node-reference" help={t('Stable reference to the action, operation or command executed by this step. Choose the logical operation, not a physical file path.')} example="operation:recalculate-score"><input id="flow-node-reference" value={node.referenceId} onChange={event=>onChange({...node,referenceId:event.target.value})}/></DesignerHelpedField>}
            {showCondition && <DesignerHelpedField label={t('Condition')} targetId="flow-node-condition" help={t('Optional boolean expression that decides whether this step or branch applies. Example: continue only when the model has no validation errors.')} example="expression:model-is-valid"><input id="flow-node-condition" value={node.conditionExpressionId} onChange={event=>onChange({...node,conditionExpressionId:event.target.value})}/></DesignerHelpedField>}
        </div></section>}

        {(showResilience || showTimeout) && <section className="event-flow-property-section"><header><div><h4>{t('Reliability and timing')}</h4><p>{t('Configure retries and time limits only for steps that may wait or fail.')}</p></div></header><div className="hydra-designer-fields">
            {showResilience && <><DesignerHelpedField label={t('Retry attempts')} targetId="flow-node-retry-attempts" help={t('How many automatic retries are allowed after a failure. Use 0 when repeating the operation could be unsafe.')} example="3"><input id="flow-node-retry-attempts" type="number" min={0} value={node.retryPolicy.maxAttempts} onChange={event=>onChange({...node,retryPolicy:{...node.retryPolicy,maxAttempts:Number(event.target.value)}})}/></DesignerHelpedField>
            {node.retryPolicy.maxAttempts > 0 && <><DesignerHelpedField label={t('Initial retry delay (ms)')} targetId="flow-node-retry-delay" help={t('Wait before the first retry. Example: 1000 waits one second, avoiding an immediate retry storm.')} example="1000"><input id="flow-node-retry-delay" type="number" min={0} value={node.retryPolicy.delayMillis} onChange={event=>onChange({...node,retryPolicy:{...node.retryPolicy,delayMillis:Number(event.target.value)}})}/></DesignerHelpedField><DesignerHelpedField label={t('Delay multiplier')} targetId="flow-node-retry-backoff" help={t('Multiplies the wait after each retry. A value of 2 produces exponential backoff: 1s, 2s, 4s…')} example="2"><input id="flow-node-retry-backoff" type="number" min={1} step="0.1" value={node.retryPolicy.backoffMultiplier} onChange={event=>onChange({...node,retryPolicy:{...node.retryPolicy,backoffMultiplier:Number(event.target.value)}})}/></DesignerHelpedField></>}</>}
            {showTimeout && <><DesignerHelpedField label={t('Maximum wait (ms)')} targetId="flow-node-timeout" help={t('Maximum time this step may run or wait before timing out. Use 0 when no automatic timeout is required.')} example="30000"><input id="flow-node-timeout" type="number" min={0} value={node.timeoutPolicy.timeoutMillis} onChange={event=>onChange({...node,timeoutPolicy:{...node.timeoutPolicy,timeoutMillis:Number(event.target.value)}})}/></DesignerHelpedField>
            {node.timeoutPolicy.timeoutMillis > 0 && <DesignerHelpedField label={t('When time runs out')} targetId="flow-node-timeout-transition" help={t('Optional transition followed if the time limit expires. Example: route to an error handler or fallback step.')} example={t('On timeout → Handle error')}><HydraAdaptiveSelect showOptionHelp={false} id="flow-node-timeout-transition" ariaLabel={t('Timeout transition')} value={node.timeoutPolicy.timeoutTransitionId} options={[{value:'',label:t('No timeout transition'),description:t('Stop timeout routing here.'),category:t('Optional')},...transitions.map(item=>({value:item.id,label:item.label||t(FLOW_TRANSITION_PRESENTATIONS[item.kind].label),description:t(FLOW_TRANSITION_PRESENTATIONS[item.kind].description),category:t(transitionCategory(item.kind))}))]} onChange={timeoutTransitionId=>onChange({...node,timeoutPolicy:{...node.timeoutPolicy,timeoutTransitionId}})}/></DesignerHelpedField>}</>}
            {showResilience && <DesignerHelpedCheckbox label={t('Can be compensated')} targetId="flow-node-compensable" help={t('Enable this when the step changes external state and a later failure may need an explicit undo/compensation step. Example: reserving stock can be compensated by releasing it.')} example={t('Reserve stock → Release stock')} checked={node.compensable} onChange={compensable=>onChange({...node,compensable})}/>} 
        </div></section>}

        <details className="event-flow-property-section event-flow-property-advanced"><summary>{t('Advanced and technical')}</summary><div className="hydra-designer-fields">
            <DesignerHelpedField label={t('Node ID')} targetId="flow-node-id" required help={t('Stable identifier used by transitions, diagrams and runtime plans, including compensation links.')} example="node:aggregate-orders"><input id="flow-node-id" value={node.id} onChange={event=>onRenameId(node.id,event.target.value)}/></DesignerHelpedField>
            {showConfiguration && <DesignerHelpedField className="hydra-designer-wide" label={t('Advanced configuration (JSON)')} targetId="flow-node-configuration" help={t('Optional node-specific parameters that do not have a dedicated high-level field yet. Example for a window: { "windowSeconds": 60 }. Invalid JSON is never applied.')} example='{ "windowSeconds": 60 }'><textarea id="flow-node-configuration" value={JSON.stringify(node.configuration,null,2)} onChange={event=>{try{onChange({...node,configuration:JSON.parse(event.target.value) as Record<string,unknown>});}catch{/* keep last valid value */}}}/></DesignerHelpedField>}
        </div></details>
    </section>;
}

export function FlowTransitionProperties({ transition, nodes, onChange, onRenameId, onDelete }: {
    transition: FlowTransition;
    nodes: FlowNode[];
    onChange: (transition: FlowTransition) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onDelete: () => void;
}) {
    const { t } = useHydraLanguage();
    const presentation = FLOW_TRANSITION_PRESENTATIONS[transition.kind];
    const conditional = ['TRUE','FALSE'].includes(transition.kind) || transition.conditionExpressionId.trim() !== '';
    return <section className="event-flow-form event-flow-progressive-properties">
        <div className="hydra-designer-form-header"><div><h3>{transition.label || t(presentation.label)}</h3><p>{t('Connection')} · {t(transitionCategory(transition.kind))}</p></div><div className="hydra-designer-form-actions"><DesignerGuideButton kind="EVENT_FLOW"/><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>{t('Delete transition')}</HydraButton></div></div>
        <div className="event-flow-property-summary"><strong>{t(presentation.label)}</strong><span>{t(presentation.description)}</span><small>{t('A transition explains why execution moves from one step to another.')}</small></div><DesignerRequiredFieldsLegend/>
        <section className="event-flow-property-section"><header><div><h4>{t('Route')}</h4><p>{t('Choose where this connection starts, where it goes and what it means.')}</p></div></header><div className="hydra-designer-fields">
            <DesignerHelpedField label={t('Visible label')} targetId="flow-transition-label" help={t('Short text printed on the arrow. Use wording users can understand, such as “Approved” or “On error”.')} example={t('Approved')}><input id="flow-transition-label" value={transition.label} onChange={event=>onChange({...transition,label:event.target.value})}/></DesignerHelpedField>
            <DesignerHelpedField label={t('Meaning')} targetId="flow-transition-kind" required help={t('Why this path is followed: normal continuation, success, error, timeout, compensation or a true/false decision branch.')} example={t('On success')}><HydraAdaptiveSelect showOptionHelp={false} id="flow-transition-kind" ariaLabel={t('Transition meaning')} value={transition.kind} options={TRANSITION_KINDS.map(kind=>({value:kind,label:t(FLOW_TRANSITION_PRESENTATIONS[kind].label),description:t(FLOW_TRANSITION_PRESENTATIONS[kind].description),category:t(transitionCategory(kind))}))} onChange={kind=>onChange({...transition,kind:kind as FlowTransitionKind})}/></DesignerHelpedField>
            <DesignerHelpedField label={t('From')} targetId="flow-transition-source" required help={t('Step that finishes before this route is evaluated.')} example={t('Validate model')}><HydraAdaptiveSelect showOptionHelp={false} id="flow-transition-source" ariaLabel={t('Source node')} value={transition.sourceNodeId} options={nodes.map(node=>({value:node.id,label:node.name,description:t(FLOW_NODE_PRESENTATIONS[node.kind].description),category:t(nodeCategory(node.kind))}))} onChange={sourceNodeId=>onChange({...transition,sourceNodeId})}/></DesignerHelpedField>
            <DesignerHelpedField label={t('To')} targetId="flow-transition-target" required help={t('Step that runs next when this route is selected.')} example={t('Publish accepted event')}><HydraAdaptiveSelect showOptionHelp={false} id="flow-transition-target" ariaLabel={t('Target node')} value={transition.targetNodeId} options={nodes.map(node=>({value:node.id,label:node.name,description:t(FLOW_NODE_PRESENTATIONS[node.kind].description),category:t(nodeCategory(node.kind))}))} onChange={targetNodeId=>onChange({...transition,targetNodeId})}/></DesignerHelpedField>
            {conditional && <DesignerHelpedField className="hydra-designer-wide" label={t('Condition')} targetId="flow-transition-condition" help={t('Optional boolean expression required for this route. Example: “validation has no errors”. Leave empty when the transition kind alone determines the path.')} example="expression:validation-has-no-errors"><input id="flow-transition-condition" value={transition.conditionExpressionId} onChange={event=>onChange({...transition,conditionExpressionId:event.target.value})}/></DesignerHelpedField>}
        </div></section>
        <details className="event-flow-property-section event-flow-property-advanced"><summary>{t('Advanced and technical')}</summary><div className="hydra-designer-fields"><DesignerHelpedField label={t('Transition ID')} targetId="flow-transition-id" required help={t('Stable identifier referenced by timeout policies and persisted diagram edges.')} example="transition:approved"><input id="flow-transition-id" value={transition.id} onChange={event=>onRenameId(transition.id,event.target.value)}/></DesignerHelpedField><DesignerHelpedField label={t('Evaluation order')} targetId="flow-transition-order" help={t('When several outgoing routes can match, smaller numbers are evaluated first. Usually leave 0 unless ordering is intentional.')} example="0"><input id="flow-transition-order" type="number" min={0} value={transition.order} onChange={event=>onChange({...transition,order:Number(event.target.value)})}/></DesignerHelpedField></div></details>
    </section>;
}
