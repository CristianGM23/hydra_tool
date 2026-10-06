export type ValidationStructureSelection =
    | { kind: 'RULE'; id: string }
    | { kind: 'ACTION'; id: string }
    | { kind: 'OPERATION'; id: string }
    | { kind: 'RULE_SET'; id: string }
    | { kind: 'EXPRESSION'; id: string }
    | null;
