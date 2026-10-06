import type { DesignerSelectOptionHelp } from '../../designer-ui/DesignerFieldHelp';
import { presentEventType } from '../eventTypePresentation';
import type { EventTypeDefinition } from '../types';
import type { FlowNode, FlowNodeKind, FlowTransitionKind } from './types';

interface Presentation { label: string; description: string; }

export const FLOW_NODE_PRESENTATIONS: Record<FlowNodeKind, Presentation> = {
    START: { label: 'Start', description: 'Entry point of the flow.' },
    SOURCE: { label: 'Event source', description: 'Receives the event that starts or feeds the flow.' },
    EVENT_STEP: { label: 'Event step', description: 'Processes a specific event inside the flow.' },
    FILTER: { label: 'Filter', description: 'Continues only when a condition is satisfied.' },
    MAP: { label: 'Transform data', description: 'Builds a new payload or changes data carried by the flow.' },
    ROUTE: { label: 'Route', description: 'Chooses one of several outgoing paths.' },
    MERGE: { label: 'Merge', description: 'Combines compatible branches into one stream.' },
    DEDUPLICATE: { label: 'Remove duplicates', description: 'Prevents repeated events from being processed more than once.' },
    WINDOW: { label: 'Time window', description: 'Groups events that arrive within a time interval.' },
    AGGREGATE: { label: 'Aggregate', description: 'Calculates a combined value from several events.' },
    JOIN: { label: 'Join events', description: 'Matches related events from different inputs.' },
    SEQUENCE: { label: 'Sequence', description: 'Waits for events to occur in a defined order.' },
    PATTERN: { label: 'Pattern', description: 'Detects a configured event pattern.' },
    ABSENCE: { label: 'Event absence', description: 'Continues when an expected event does not arrive in time.' },
    ACTION_STEP: { label: 'Execute action', description: 'Runs a reusable canonical action.' },
    OPERATION_STEP: { label: 'Invoke operation', description: 'Invokes an operation defined by the metamodel.' },
    COMMAND_STEP: { label: 'Execute command', description: 'Runs a command that may change model state.' },
    EMIT: { label: 'Publish event', description: 'Publishes a derived HYDRA event.' },
    SUCCESS_END: { label: 'Successful end', description: 'Marks successful completion of the flow.' },
    ERROR_END: { label: 'Error end', description: 'Marks flow completion with an error.' },
    COMPENSATION_STEP: { label: 'Compensate action', description: 'Reverses a previously committed effect.' },
    ERROR_HANDLER: { label: 'Handle error', description: 'Processes an error before recovery or termination.' },
    DECISION: { label: 'Decision', description: 'Evaluates a condition and chooses a true or false path.' },
    PARALLEL_SPLIT: { label: 'Parallel split', description: 'Starts several branches from the same point.' },
    TIMEOUT: { label: 'Timeout', description: 'Continues when a configured waiting time expires.' },
    RETRY: { label: 'Retry', description: 'Repeats a failed step according to a retry policy.' },
    WAIT_FOR_EVENT: { label: 'Wait for event', description: 'Pauses until a specific event is received.' },
    MANUAL_STEP: { label: 'Manual step', description: 'Represents work that must be completed outside the automatic runtime.' },
    FALLBACK: { label: 'Fallback', description: 'Executes an alternative path after a failure.' }
};

export const FLOW_TRANSITION_PRESENTATIONS: Record<FlowTransitionKind, Presentation> = {
    NEXT: { label: 'Continue', description: 'Normal transition to the next node.' },
    ON_SUCCESS: { label: 'On success', description: 'Taken after the source node completes successfully.' },
    ON_ERROR: { label: 'On error', description: 'Taken when the source node fails.' },
    COMPENSATES: { label: 'Compensates', description: 'Links an effect with the node that reverses it.' },
    TIMEOUT: { label: 'On timeout', description: 'Taken when the configured timeout expires.' },
    TRUE: { label: 'Condition true', description: 'Taken when the decision condition is true.' },
    FALSE: { label: 'Condition false', description: 'Taken when the decision condition is false.' }
};

export function flowNodeOptionHelp(kinds: FlowNodeKind[]): DesignerSelectOptionHelp[] {
    return kinds.map(value => ({ value, ...FLOW_NODE_PRESENTATIONS[value] }));
}

export function flowTransitionOptionHelp(kinds: FlowTransitionKind[]): DesignerSelectOptionHelp[] {
    return kinds.map(value => ({ value, ...FLOW_TRANSITION_PRESENTATIONS[value] }));
}

export function describeFlowNode(node: FlowNode, customTypes: EventTypeDefinition[] = []): string {
    const base = FLOW_NODE_PRESENTATIONS[node.kind];
    if (node.eventTypeId !== '') {
        const event = presentEventType(node.eventTypeId, customTypes);
        if (node.kind === 'EMIT') return `Publishes “${event.label}”. ${event.description}`;
        if (node.kind === 'WAIT_FOR_EVENT' || node.kind === 'ABSENCE') return `Observes “${event.label}”. ${event.description}`;
        return `Uses “${event.label}”. ${event.description}`;
    }
    if (node.referenceId !== '') {
        if (node.kind === 'ACTION_STEP') return 'Runs the action selected in the node properties.';
        if (node.kind === 'OPERATION_STEP') return 'Invokes the operation selected in the node properties.';
        if (node.kind === 'COMMAND_STEP') return 'Executes the command selected in the node properties.';
    }
    return base.description;
}
