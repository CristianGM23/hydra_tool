/**
 * @deprecated R5C13 Fix 9 converged inferred grammar diagrams on the canonical
 * Metamodel Designer renderer. This module remains as a source-compatibility
 * tombstone for overlays from older HYDRA builds; it intentionally contains no
 * independent edge renderer.
 */
export interface GrammarUmlEdgeData extends Record<string,unknown> {
    kind:string;
    label:string;
    diagnosticCount:number;
}

export type GrammarUmlFlowEdge=never;
export const GRAMMAR_UML_RENDERER_DEPRECATED=true as const;
