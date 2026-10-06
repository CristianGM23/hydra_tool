import { HydraButton } from '../../design-system/HydraButton';
import { useState } from 'react';
import { HydraAdaptiveSelect } from '../../components/HydraAdaptiveSelect';
import { DesignerGuideButton, DesignerHelpedCheckbox, DesignerHelpedField, DesignerRequiredFieldsLegend } from '../../designer-ui/DesignerFieldHelp';
import { HydraCreationModeSwitch, HydraGuidedWorkflow } from '../../design-system';
import { eventTypeOptionHelp, presentEventType } from '../eventTypePresentation';
import type { EventTypeDefinition } from '../types';
import { FLOW_NODE_PRESENTATIONS, FLOW_TRANSITION_PRESENTATIONS, flowNodeOptionHelp, flowTransitionOptionHelp } from './flowPresentation';
import type { FlowDefinition, FlowNode, FlowNodeKind, FlowTransition, FlowTransitionKind } from './types';
import { flowNodeCategory, flowTransitionCategory } from './eventFlowDesignerSupport';

const nodeKinds: FlowNodeKind[] = [
    'START', 'SOURCE', 'EVENT_STEP', 'FILTER', 'MAP', 'ROUTE', 'MERGE', 'DEDUPLICATE', 'WINDOW', 'AGGREGATE',
    'JOIN', 'SEQUENCE', 'PATTERN', 'ABSENCE', 'ACTION_STEP', 'OPERATION_STEP', 'COMMAND_STEP', 'EMIT',
    'SUCCESS_END', 'ERROR_END', 'COMPENSATION_STEP', 'ERROR_HANDLER', 'DECISION', 'PARALLEL_SPLIT', 'TIMEOUT',
    'RETRY', 'WAIT_FOR_EVENT', 'MANUAL_STEP', 'FALLBACK'
];
const transitionKinds: FlowTransitionKind[] = ['NEXT', 'ON_SUCCESS', 'ON_ERROR', 'COMPENSATES', 'TIMEOUT', 'TRUE', 'FALSE'];


export function FlowForm({ flow, eventTypeIds, customEventTypes, onChange, onRenameId, onAddVariable, onAddCompensation, onOpenDiagram, onDelete }: {
    flow: FlowDefinition;
    eventTypeIds: string[];
    customEventTypes: EventTypeDefinition[];
    onChange: (flow: FlowDefinition) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onAddVariable: () => void;
    onAddCompensation: () => void;
    onOpenDiagram: () => void;
    onDelete: () => void;
}) {
    const [triggerToAdd, setTriggerToAdd] = useState('');
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('identity');
    const availableEventTypeIds = [...new Set([...eventTypeIds, ...flow.triggerEventTypeIds])];
    const triggerOptions = eventTypeOptionHelp(availableEventTypeIds, customEventTypes);
    const remainingTriggers = availableEventTypeIds.filter(id => !flow.triggerEventTypeIds.includes(id));
    const nodeIds = new Set(flow.nodes.map(node => node.id));
    const variableIds = flow.variables.map(variable => variable.id.trim()).filter(Boolean);
    const variableNames = flow.variables.map(variable => variable.name.trim()).filter(Boolean);
    const variablesComplete = flow.variables.every(variable => variable.id.trim() !== '' && variable.name.trim() !== '' && variable.type.trim() !== '') && new Set(variableIds).size === variableIds.length && new Set(variableNames).size === variableNames.length;
    const startCount = flow.nodes.filter(node => node.enabled && node.kind === 'START').length;
    const endCount = flow.nodes.filter(node => node.enabled && (node.kind === 'SUCCESS_END' || node.kind === 'ERROR_END')).length;
    const transitionsComplete = flow.transitions.every(transition => transition.id.trim() !== '' && nodeIds.has(transition.sourceNodeId) && nodeIds.has(transition.targetNodeId) && transition.sourceNodeId !== transition.targetNodeId);
    const compensationComplete = flow.compensations.every(link => link.id.trim() !== '' && nodeIds.has(link.sourceNodeId) && nodeIds.has(link.compensationNodeId) && link.sourceNodeId !== link.compensationNodeId);

    const identityFields = <div className="hydra-designer-fields">
        <DesignerHelpedField label="Name" targetId="event-flow-name" required help="Visible process name used in diagrams, runtime traces and generated documentation. State the business process or reaction coordinated by the flow." example="Model quality cycle"><input id="event-flow-name" value={flow.name} onChange={event => onChange({ ...flow, name: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Flow ID" targetId="event-flow-id" required help="Stable technical identifier used by runtime plans, diagrams and integrations. Other contracts may reference it, so avoid changing it after publication." example="flow:model-quality-cycle"><input id="event-flow-id" value={flow.id} onChange={event => onRenameId(flow.id, event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="event-flow-description" help="Describe the purpose, entry conditions and expected final outcome at a high level. Node-level implementation details belong in the diagram and node properties." example="Validates a saved model and emits a quality result."><textarea id="event-flow-description" value={flow.description} onChange={event => onChange({ ...flow, description: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedCheckbox label="Enabled" targetId="event-flow-enabled" help="Controls whether this flow is compiled into runtime plans. Keep incomplete flows disabled while designing their graph." example="Enabled" checked={flow.enabled} onChange={enabled => onChange({ ...flow, enabled })} />
    </div>;

    const triggerFields = <div className="hydra-designer-fields one-column">
        <DesignerHelpedField className="hydra-designer-wide" label="Trigger event types" targetId="event-flow-trigger-picker" required help="Events that are allowed to create a new execution of this flow. Select only facts that genuinely start the process; events awaited later in the graph belong on WAIT_FOR_EVENT or other event nodes instead." example="Model saved" options={triggerOptions}>
            <div className="event-flow-trigger-picker"><HydraAdaptiveSelect showOptionHelp={false} id="event-flow-trigger-picker" ariaLabel="Trigger event types" value={triggerToAdd} placeholder="Select an event…" options={remainingTriggers.map(id => { const presentation = presentEventType(id, customEventTypes); return { value: id, label: presentation.label, description: presentation.description, category: presentation.category }; })} onChange={setTriggerToAdd} /><HydraButton type="button" disabled={triggerToAdd === ''} onClick={() => { if (triggerToAdd === '' || flow.triggerEventTypeIds.includes(triggerToAdd)) return; onChange({ ...flow, triggerEventTypeIds: [...flow.triggerEventTypeIds, triggerToAdd] }); setTriggerToAdd(''); }}>+ Trigger</HydraButton></div>
        </DesignerHelpedField>
        <div className="hydra-designer-wide event-flow-trigger-list" aria-label="Selected trigger events">{flow.triggerEventTypeIds.map(id => { const presentation = presentEventType(id, customEventTypes); return <article key={id}><div><strong>{presentation.label}</strong><small>{presentation.description}</small></div><HydraButton type="button" aria-label={`Remove ${presentation.label}`} onClick={() => onChange({ ...flow, triggerEventTypeIds: flow.triggerEventTypeIds.filter(value => value !== id) })}>Remove</HydraButton></article>; })}{flow.triggerEventTypeIds.length === 0 && <p>No trigger event selected.</p>}</div>
    </div>;

    const variableSection = <section className="hydra-designer-section"><header><div><h4>Variables</h4><p>Portable values kept for one flow execution. Add only state that several nodes need to share.</p></div><HydraButton type="button" onClick={onAddVariable}>+ Variable</HydraButton></header>
        {flow.variables.map((variable, index) => <div className="hydra-designer-row-card" key={variable.id}><header><strong>Variable {index + 1}</strong><HydraButton className="hydra-danger-button" type="button" onClick={() => onChange({ ...flow, variables: flow.variables.filter(item => item.id !== variable.id) })}>Remove</HydraButton></header><div className="hydra-designer-row-fields">
            <DesignerHelpedField label="Variable ID" targetId={`flow-variable-id-${index}`} required help="Stable identifier referenced by node configuration and expressions. IDs must be unique in this flow." example="variable:model-id"><input id={`flow-variable-id-${index}`} value={variable.id} onChange={event => onChange({ ...flow, variables: flow.variables.map(item => item.id === variable.id ? { ...item, id: event.target.value } : item) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Name" targetId={`flow-variable-name-${index}`} required help="Readable variable name used in guided documentation and diagnostics. Names must also be unique to avoid ambiguous expressions." example="modelId"><input id={`flow-variable-name-${index}`} value={variable.name} onChange={event => onChange({ ...flow, variables: flow.variables.map(item => item.id === variable.id ? { ...item, name: event.target.value } : item) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Type" targetId={`flow-variable-type-${index}`} required help="Portable type expected for the value while this flow instance is running." example="DOCUMENT_ID"><input id={`flow-variable-type-${index}`} value={variable.type} onChange={event => onChange({ ...flow, variables: flow.variables.map(item => item.id === variable.id ? { ...item, type: event.target.value } : item) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Initial value (JSON)" targetId={`flow-variable-value-${index}`} help="Optional initial JSON value. Leave it empty when a node or incoming event assigns the value later." example='"model-1"'><input id={`flow-variable-value-${index}`} value={serialize(variable.initialValue)} onChange={event => onChange({ ...flow, variables: flow.variables.map(item => item.id === variable.id ? { ...item, initialValue: parse(event.target.value) } : item) })} /></DesignerHelpedField>
        </div></div>)}
        {flow.variables.length === 0 && <div className="hydra-empty-state event-inline-empty"><strong>No shared variables</strong><span>This is valid when nodes can use event payloads and local configuration directly.</span></div>}
    </section>;

    const graphSection = <section className="hydra-designer-section event-flow-guided-graph"><header><div><h4>Process graph</h4><p>Create the executable path in the existing visual editor. Guided mode does not replace the diagram or the advanced node/transition forms.</p></div><HydraButton type="button" onClick={onOpenDiagram}>Open diagram</HydraButton></header><div className="event-flow-graph-summary"><article><strong>{flow.nodes.length}</strong><span>Nodes</span></article><article><strong>{flow.transitions.length}</strong><span>Transitions</span></article><article><strong>{startCount}</strong><span>Enabled start</span></article><article><strong>{endCount}</strong><span>Enabled endings</span></article></div><p>Use the diagram to add START, processing/decision nodes and explicit SUCCESS_END or ERROR_END nodes. The same HYDRA nodes remain editable later through Form, Structure tree and Source.</p></section>;

    const compensationSection = <section className="hydra-designer-section"><header><div><h4>Compensation links</h4><p>Optional SAGA-style reversal links. Use them only when a committed node has an explicit compensating node.</p></div><HydraButton type="button" disabled={flow.nodes.length < 2} onClick={onAddCompensation}>+ Compensation</HydraButton></header>{flow.compensations.map((link, index) => <div className="hydra-designer-row-card" key={link.id}><header><strong>Compensation {index + 1}</strong><HydraButton className="hydra-danger-button" type="button" onClick={() => onChange({ ...flow, compensations: flow.compensations.filter(item => item.id !== link.id) })}>Remove</HydraButton></header><div className="hydra-designer-row-fields">
        <DesignerHelpedField label="Link ID" targetId={`flow-compensation-id-${index}`} required help="Stable identifier of the compensation relationship." example="compensation:reserve-stock"><input id={`flow-compensation-id-${index}`} value={link.id} onChange={event => onChange({ ...flow, compensations: flow.compensations.map(item => item.id === link.id ? { ...item, id: event.target.value } : item) })} /></DesignerHelpedField>
        <DesignerHelpedField label="Source node" targetId={`flow-compensation-source-${index}`} required help="Node whose committed effect can later be reversed. This node should normally be marked compensable." example="Reserve stock" options={flow.nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description }))}><HydraAdaptiveSelect showOptionHelp={false} id={`flow-compensation-source-${index}`} ariaLabel="Source node" value={link.sourceNodeId} options={flow.nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description, category: flowNodeCategory(node.kind) }))} onChange={sourceNodeId => onChange({ ...flow, compensations: flow.compensations.map(item => item.id === link.id ? { ...item, sourceNodeId } : item) })} /></DesignerHelpedField>
        <DesignerHelpedField label="Compensation node" targetId={`flow-compensation-target-${index}`} required help="Node that reverses or neutralizes the committed source effect when compensation is required." example="Release stock" options={flow.nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description }))}><HydraAdaptiveSelect showOptionHelp={false} id={`flow-compensation-target-${index}`} ariaLabel="Compensation node" value={link.compensationNodeId} options={flow.nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description, category: flowNodeCategory(node.kind) }))} onChange={compensationNodeId => onChange({ ...flow, compensations: flow.compensations.map(item => item.id === link.id ? { ...item, compensationNodeId } : item) })} /></DesignerHelpedField>
    </div></div>)}{flow.compensations.length === 0 && <div className="hydra-empty-state event-inline-empty"><strong>No compensation links</strong><span>This is valid for flows without reversible committed effects.</span></div>}</section>;

    const graphComplete = startCount === 1 && endCount >= 1 && flow.nodes.length >= 2 && transitionsComplete;
    const steps = [
        { id:'identity', title:'Identity', description:'Name the process and explain its purpose.', checks:[
            { id:'name', label:'Flow name is defined', complete:flow.name.trim() !== '', detail:'Used by designers and runtime traces.' },
            { id:'id', label:'Stable flow ID is defined', complete:flow.id.trim() !== '', detail:'Used by plans, diagrams and integrations.' }
        ], content:identityFields },
        { id:'triggers', title:'Triggers', description:'Choose the facts that can start the process.', checks:[
            { id:'trigger', label:'At least one trigger event is selected', complete:flow.triggerEventTypeIds.length > 0, detail:`${flow.triggerEventTypeIds.length} trigger event(s) configured.` }
        ], content:triggerFields },
        { id:'variables', title:'Shared data', description:'Define optional state shared across nodes.', checks:[
            { id:'variables-valid', label:'Variable definitions are complete and unique', complete:variablesComplete, detail:flow.variables.length === 0 ? 'No shared variables are required.' : `${flow.variables.length} variable(s) checked.` }
        ], content:variableSection },
        { id:'graph', title:'Process graph', description:'Build start, processing, decisions and endings visually.', checks:[
            { id:'start', label:'Exactly one enabled START node exists', complete:startCount === 1, detail:`Current enabled START count: ${startCount}.` },
            { id:'end', label:'At least one enabled final node exists', complete:endCount >= 1, detail:`SUCCESS_END / ERROR_END count: ${endCount}.` },
            { id:'transitions', label:'Transition endpoints are valid', complete:transitionsComplete && flow.transitions.length > 0, detail:`${flow.transitions.length} transition(s) checked.` }
        ], content:graphSection },
        { id:'compensation', title:'Compensation', description:'Review optional reversal paths.', checks:[
            { id:'comp-links', label:'Compensation links are structurally valid', complete:compensationComplete, detail:flow.compensations.length === 0 ? 'No compensation is required.' : `${flow.compensations.length} compensation link(s) checked.` }
        ], content:compensationSection },
        { id:'review', title:'Review', description:'Confirm the flow is ready for Check and runtime compilation.', checks:[
            { id:'identity-ready', label:'Identity and trigger configuration is ready', complete:flow.name.trim() !== '' && flow.id.trim() !== '' && flow.triggerEventTypeIds.length > 0, detail:'The runtime can identify when to instantiate this flow.' },
            { id:'graph-ready', label:'Executable graph has start, ending and valid transitions', complete:graphComplete, detail:'Run Check from the toolbar for deeper node-kind and semantic diagnostics.' },
            { id:'aux-ready', label:'Variables and compensations are consistent', complete:variablesComplete && compensationComplete, detail:'Optional runtime state does not contain broken references.' }
        ], content:<div className="hydra-guided-review-summary"><strong>{flow.name || 'Unnamed flow'}</strong><span>{flow.id}</span><p>{flow.description || 'No description provided.'}</p><dl><div><dt>Triggers</dt><dd>{flow.triggerEventTypeIds.length}</dd></div><div><dt>Nodes</dt><dd>{flow.nodes.length}</dd></div><div><dt>Transitions</dt><dd>{flow.transitions.length}</dd></div><div><dt>Status</dt><dd>{flow.enabled ? 'Enabled' : 'Disabled'}</dd></div></dl></div> }
    ];

    return <section className="event-flow-form">
        <div className="hydra-designer-form-header"><div><h3>{flow.name}</h3><p>Event flow · <span className="hydra-id-preview">{flow.id}</span></p></div><div className="hydra-designer-form-actions"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Form / advanced" /><DesignerGuideButton kind="EVENT_FLOW" /><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete flow</HydraButton></div></div>
        <DesignerRequiredFieldsLegend />
        {creationMode === 'GUIDED'
            ? <HydraGuidedWorkflow key={flow.id} title="Guided EventFlow" description="Define the process in a safe sequence while preserving the existing diagram, structure tree, source editor and node/transition forms." steps={steps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel="Finish guide" onFinish={() => { setCreationMode('ADVANCED'); return true; }} />
            : <>{identityFields}<section className="hydra-designer-section"><header><div><h4>Trigger events</h4><p>Facts that instantiate this flow.</p></div></header>{triggerFields}</section>{variableSection}{graphSection}{compensationSection}</>}
    </section>;
}

export function FlowNodeForm({ node, transitions, eventTypeIds, customEventTypes, onChange, onRenameId, onDelete }: {
    node: FlowNode;
    transitions: FlowTransition[];
    eventTypeIds: string[];
    customEventTypes: EventTypeDefinition[];
    onChange: (node: FlowNode) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onDelete: () => void;
}) {
    return <section className="event-flow-form"><div className="hydra-designer-form-header"><div><h3>{node.name}</h3><p>Flow node · <span className="hydra-id-preview">{node.id}</span></p></div><div className="hydra-designer-form-actions"><DesignerHelpedCheckbox className="compact" label="Enabled" targetId="flow-node-enabled" help="Controls whether this node participates in flow compilation and execution." example="Enabled" checked={node.enabled} onChange={enabled => onChange({ ...node, enabled })} /><DesignerGuideButton kind="EVENT_FLOW" /><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete node</HydraButton></div></div><DesignerRequiredFieldsLegend /><div className="hydra-designer-fields">
        <DesignerHelpedField label="Name" targetId="flow-node-name" required help="Readable node name used in the diagram and diagnostics." example="Validate model"><input id="flow-node-name" value={node.name} onChange={event => onChange({ ...node, name: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Node ID" targetId="flow-node-id" required help="Stable identifier used by transitions, diagrams and runtime plans, including compensation links." example="node:validate-model"><input id="flow-node-id" value={node.id} onChange={event => onRenameId(node.id, event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Kind" targetId="flow-node-kind" required help="Runtime operation represented by this node." example="Filter" options={flowNodeOptionHelp(nodeKinds)}><HydraAdaptiveSelect showOptionHelp={false} id="flow-node-kind" ariaLabel="Kind" value={node.kind} options={nodeKinds.map(kind => ({ value: kind, label: FLOW_NODE_PRESENTATIONS[kind].label, description: FLOW_NODE_PRESENTATIONS[kind].description, category: flowNodeCategory(kind) }))} onChange={kind => onChange({ ...node, kind: kind as FlowNodeKind })} /></DesignerHelpedField>
        <DesignerHelpedField label="Reference ID" targetId="flow-node-reference" help="Action, operation, command or external reference used by step nodes." example="operation:recalculate-score"><input id="flow-node-reference" value={node.referenceId} onChange={event => onChange({ ...node, referenceId: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Event" targetId="flow-node-event-type" help="Event consumed, awaited or published by event-oriented nodes. The interface shows a descriptive name while HYDRA stores the stable identifier." example="Validation completed" options={[{ value: '', label: 'No event selected', description: 'This node does not consume or publish a specific event.' }, ...eventTypeOptionHelp([...new Set([...eventTypeIds, node.eventTypeId].filter(Boolean))], customEventTypes)]}>
            <HydraAdaptiveSelect showOptionHelp={false} id="flow-node-event-type" ariaLabel="Event" value={node.eventTypeId} options={[{ value: '', label: 'No event selected', description: 'This node does not consume or publish a specific event.', category: 'Recommended' }, ...[...new Set([...eventTypeIds, node.eventTypeId].filter(Boolean))].map(id => { const presentation = presentEventType(id, customEventTypes); return { value: id, label: presentation.label, description: presentation.description, category: presentation.category }; })]} onChange={eventTypeId => onChange({ ...node, eventTypeId })} />
        </DesignerHelpedField>
        <DesignerHelpedField label="Condition expression ID" targetId="flow-node-condition" help="Optional boolean expression used by filters, decisions or guarded steps." example="expression:model-is-complete"><input id="flow-node-condition" value={node.conditionExpressionId} onChange={event => onChange({ ...node, conditionExpressionId: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Retry attempts" targetId="flow-node-retry-attempts" help="Maximum attempts after the first execution. Zero disables automatic retries." example="3"><input id="flow-node-retry-attempts" type="number" min={0} value={node.retryPolicy.maxAttempts} onChange={event => onChange({ ...node, retryPolicy: { ...node.retryPolicy, maxAttempts: Number(event.target.value) } })} /></DesignerHelpedField>
        <DesignerHelpedField label="Retry delay (ms)" targetId="flow-node-retry-delay" help="Initial delay before retrying a failed node." example="1000"><input id="flow-node-retry-delay" type="number" min={0} value={node.retryPolicy.delayMillis} onChange={event => onChange({ ...node, retryPolicy: { ...node.retryPolicy, delayMillis: Number(event.target.value) } })} /></DesignerHelpedField>
        <DesignerHelpedField label="Backoff multiplier" targetId="flow-node-retry-backoff" help="Multiplier applied to the delay between retries." example="2"><input id="flow-node-retry-backoff" type="number" min={1} step="0.1" value={node.retryPolicy.backoffMultiplier} onChange={event => onChange({ ...node, retryPolicy: { ...node.retryPolicy, backoffMultiplier: Number(event.target.value) } })} /></DesignerHelpedField>
        <DesignerHelpedField label="Timeout (ms)" targetId="flow-node-timeout" help="Maximum execution or wait time. Zero disables timeout handling." example="30000"><input id="flow-node-timeout" type="number" min={0} value={node.timeoutPolicy.timeoutMillis} onChange={event => onChange({ ...node, timeoutPolicy: { ...node.timeoutPolicy, timeoutMillis: Number(event.target.value) } })} /></DesignerHelpedField>
        <DesignerHelpedField label="Timeout transition" targetId="flow-node-timeout-transition" help="Transition followed when the timeout expires." example="On timeout" options={[{ value: '', label: 'No timeout transition', description: 'Do not redirect the flow when the timeout expires.' }, ...transitions.map(transition => ({ value: transition.id, label: transition.label || FLOW_TRANSITION_PRESENTATIONS[transition.kind].label, description: FLOW_TRANSITION_PRESENTATIONS[transition.kind].description }))]}><HydraAdaptiveSelect showOptionHelp={false} id="flow-node-timeout-transition" ariaLabel="Timeout transition" value={node.timeoutPolicy.timeoutTransitionId} options={[{ value: '', label: 'No timeout transition', description: 'Do not redirect the flow when the timeout expires.', category: 'Recommended' }, ...transitions.map(transition => ({ value: transition.id, label: transition.label || FLOW_TRANSITION_PRESENTATIONS[transition.kind].label, description: FLOW_TRANSITION_PRESENTATIONS[transition.kind].description, category: flowTransitionCategory(transition.kind) }))]} onChange={timeoutTransitionId => onChange({ ...node, timeoutPolicy: { ...node.timeoutPolicy, timeoutTransitionId } })} /></DesignerHelpedField>
        <DesignerHelpedCheckbox label="Compensable" targetId="flow-node-compensable" help="Marks this node as producing an effect that may require an explicit compensation node." example="true" checked={node.compensable} onChange={compensable => onChange({ ...node, compensable })} />
        <DesignerHelpedField className="hydra-designer-wide" label="Configuration JSON" targetId="flow-node-configuration" help="Node-kind-specific portable configuration read by the compiler and runtime." example='{ "windowSeconds": 60 }'><textarea id="flow-node-configuration" value={JSON.stringify(node.configuration, null, 2)} onChange={event => { try { onChange({ ...node, configuration: JSON.parse(event.target.value) as Record<string, unknown> }); } catch { /* Preserve the last valid canonical configuration. */ } }} /></DesignerHelpedField>
    </div></section>;
}

export function FlowTransitionForm({ transition, nodes, onChange, onRenameId, onDelete }: {
    transition: FlowTransition;
    nodes: FlowNode[];
    onChange: (transition: FlowTransition) => void;
    onRenameId: (previousId: string, nextId: string) => void;
    onDelete: () => void;
}) {
    return <section className="event-flow-form"><div className="hydra-designer-form-header"><div><h3>{transition.label || FLOW_TRANSITION_PRESENTATIONS[transition.kind].label}</h3><p>Flow transition · <span className="hydra-id-preview">{transition.id}</span></p></div><div className="hydra-designer-form-actions"><DesignerGuideButton kind="EVENT_FLOW" /><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete transition</HydraButton></div></div><DesignerRequiredFieldsLegend /><div className="hydra-designer-fields">
        <DesignerHelpedField label="Transition ID" targetId="flow-transition-id" required help="Stable identifier referenced by timeout policies and diagram edges." example="transition:validation-success"><input id="flow-transition-id" value={transition.id} onChange={event => onRenameId(transition.id, event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField label="Label" targetId="flow-transition-label" help="Visible label shown on the diagram." example="Valid"><input id="flow-transition-label" value={transition.label} onChange={event => onChange({ ...transition, label: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Source node" targetId="flow-transition-source" required help="Node from which the transition starts." example="Validate model" options={nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description }))}><HydraAdaptiveSelect showOptionHelp={false} id="flow-transition-source" ariaLabel="Source node" value={transition.sourceNodeId} options={nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description, category: flowNodeCategory(node.kind) }))} onChange={sourceNodeId => onChange({ ...transition, sourceNodeId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Target node" targetId="flow-transition-target" required help="Node reached when the transition is selected." example="Publish accepted event" options={nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description }))}><HydraAdaptiveSelect showOptionHelp={false} id="flow-transition-target" ariaLabel="Target node" value={transition.targetNodeId} options={nodes.map(node => ({ value: node.id, label: node.name, description: FLOW_NODE_PRESENTATIONS[node.kind].description, category: flowNodeCategory(node.kind) }))} onChange={targetNodeId => onChange({ ...transition, targetNodeId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Kind" targetId="flow-transition-kind" required help="Semantic reason for following this transition." example="On success" options={flowTransitionOptionHelp(transitionKinds)}><HydraAdaptiveSelect showOptionHelp={false} id="flow-transition-kind" ariaLabel="Kind" value={transition.kind} options={transitionKinds.map(kind => ({ value: kind, label: FLOW_TRANSITION_PRESENTATIONS[kind].label, description: FLOW_TRANSITION_PRESENTATIONS[kind].description, category: flowTransitionCategory(kind) }))} onChange={kind => onChange({ ...transition, kind: kind as FlowTransitionKind })} /></DesignerHelpedField>
        <DesignerHelpedField label="Order" targetId="flow-transition-order" help="Evaluation order when several outgoing transitions may match." example="0"><input id="flow-transition-order" type="number" min={0} value={transition.order} onChange={event => onChange({ ...transition, order: Number(event.target.value) })} /></DesignerHelpedField>
        <DesignerHelpedField className="hydra-designer-wide" label="Condition expression ID" targetId="flow-transition-condition" help="Optional boolean expression that guards the transition." example="expression:validation-has-no-errors"><input id="flow-transition-condition" value={transition.conditionExpressionId} onChange={event => onChange({ ...transition, conditionExpressionId: event.target.value })} /></DesignerHelpedField>
    </div></section>;
}

function serialize(value: unknown): string { return value === undefined ? '' : JSON.stringify(value); }
function parse(value: string): unknown { if (value.trim() === '') return undefined; try { return JSON.parse(value) as unknown; } catch { return value; } }
