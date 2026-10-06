import type { HydraPersistedConnectionAppearance } from '../../designer-ui/HydraConnectionVisual';
export type FlowNodeKind =
    | 'START' | 'SUCCESS_END' | 'ERROR_END' | 'EVENT_STEP' | 'COMMAND_STEP' | 'ACTION_STEP'
    | 'OPERATION_STEP' | 'DECISION' | 'MERGE' | 'PARALLEL_SPLIT' | 'JOIN' | 'TIMEOUT' | 'RETRY'
    | 'WAIT_FOR_EVENT' | 'MANUAL_STEP' | 'COMPENSATION_STEP' | 'FALLBACK' | 'ERROR_HANDLER'
    | 'SOURCE' | 'FILTER' | 'MAP' | 'ROUTE' | 'DEDUPLICATE' | 'EMIT' | 'WINDOW' | 'AGGREGATE'
    | 'SEQUENCE' | 'PATTERN' | 'ABSENCE';
export type FlowTransitionKind = 'NEXT' | 'ON_SUCCESS' | 'ON_ERROR' | 'COMPENSATES' | 'TIMEOUT' | 'TRUE' | 'FALSE';
export interface RetryPolicy { maxAttempts: number; delayMillis: number; backoffMultiplier: number; }
export interface TimeoutPolicy { timeoutMillis: number; timeoutTransitionId: string; }
export interface FlowVariable { id: string; name: string; type: string; initialValue?: unknown; }
export interface FlowNode {
    id: string;
    name: string;
    kind: FlowNodeKind;
    referenceId: string;
    eventTypeId: string;
    conditionExpressionId: string;
    configuration: Record<string, unknown>;
    retryPolicy: RetryPolicy;
    timeoutPolicy: TimeoutPolicy;
    compensable: boolean;
    enabled: boolean;
}
export interface FlowTransition {
    id: string;
    sourceNodeId: string;
    targetNodeId: string;
    kind: FlowTransitionKind;
    conditionExpressionId: string;
    order: number;
    label: string;
}
export interface CompensationLink { id: string; sourceNodeId: string; compensationNodeId: string; }
export interface FlowDefinition {
    id: string;
    name: string;
    description: string;
    enabled: boolean;
    triggerEventTypeIds: string[];
    variables: FlowVariable[];
    nodes: FlowNode[];
    transitions: FlowTransition[];
    compensations: CompensationLink[];
    annotations: Record<string, string>;
}
export interface EventFlowModel {
    schemaVersion: '1.0';
    id: string;
    metamodelId: string;
    eventDefinitionModelId: string;
    expressionModelId: string;
    flows: FlowDefinition[];
    annotations: Record<string, string>;
}
export interface FlowDiagnostic { severity: 'ERROR' | 'WARNING' | 'INFO'; code: string; elementId: string; path: string; message: string; }
export interface EventFlowCheckReport { modelId: string; diagnostics: FlowDiagnostic[]; valid?: boolean; }
export interface EventFlowLoadResult { model: EventFlowModel; etag: string; checkReport: EventFlowCheckReport; }
export interface EventFlowNodeView { nodeId: string; x: number; y: number; width: number; height: number; collapsed: boolean; }
export interface EventFlowPoint { x: number; y: number; }
export interface EventFlowEdgeView extends HydraPersistedConnectionAppearance { transitionId: string; points: EventFlowPoint[]; sourceHandle?: string; targetHandle?: string; label?: string; note?: string; }
export interface EventFlowDiagramDocument {
    schemaVersion: '1.0';
    id: string;
    metamodelId: string;
    eventFlowModelId: string;
    nodes: EventFlowNodeView[];
    edges: EventFlowEdgeView[];
}
export interface EventFlowDiagramLoadResult { diagram: EventFlowDiagramDocument; etag: string; }
export interface EventFlowRuntimeStatus { metamodelId: string; active: boolean; flowCount: number; processedEvents: number; emittedEvents: number; stateEntries: number; lastFailure: string; updatedAt: string; }
export type FlowExecutionStatus='COMPLETED'|'FAILED'|'WAITING'|'FILTERED'|'DEPTH_LIMIT'|'COMPENSATED';
export interface FlowNodeExecution { nodeId:string; nodeKind:FlowNodeKind; status:FlowExecutionStatus; message:string; executedAt:string; }
export interface FlowExecutionTrace { id:string; flowId:string; sourceEventId:string; startedAt:string; completedAt:string; status:FlowExecutionStatus; nodes:FlowNodeExecution[]; emittedEventIds:string[]; message:string; }

export interface FlowWaitingContinuation { id:string; flowId:string; waitNodeId:string; awaitedEventTypeId:string; groupKey:string; createdAt:string; deadline:string|null; }
export interface FlowRetryContinuation { id:string; flowId:string; nodeId:string; attempt:number; maximumAttempts:number; dueAt:string; startedAt:string; }
export interface EventFlowRuntimeSnapshot { schemaVersion:'1.0'; id:string; metamodelId:string; updatedAt:string; waitingContinuations:FlowWaitingContinuation[]; retryContinuations:FlowRetryContinuation[]; traces:FlowExecutionTrace[]; runtimeState:Record<string,unknown>; }
