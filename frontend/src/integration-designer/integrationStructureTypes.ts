export type IntegrationStructureSelection =
    | { kind: 'PROFILE'; id: string }
    | { kind: 'TARGET'; id: string }
    | { kind: 'INJECTION'; id: string }
    | { kind: 'FEATURES'; id: string }
    | { kind: 'ARTIFACT'; id: string; index: number }
    | { kind: 'MAPPING'; id: string; index: number }
    | { kind: 'PREVIEW'; id: string }
    | null;
