export type EventFlowStructureSelection =
    | { kind: 'FLOW'; flowId: string; id: string }
    | { kind: 'NODE'; flowId: string; id: string }
    | { kind: 'TRANSITION'; flowId: string; id: string }
    | null;
