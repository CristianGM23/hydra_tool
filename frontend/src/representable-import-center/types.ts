export type ExternalChangeKind = 'ADD' | 'UPDATE' | 'DELETE' | 'MOVE' | 'RENAME';
export type Representability = 'FULL' | 'PARTIAL' | 'NONE';
export interface ExternalChangeCandidate {
    externalId: string; changeKind: ExternalChangeKind; nativeKind: string;
    qualifiedName: string; properties: Record<string, unknown>;
}
export interface NeutralChangeOperation {
    externalId: string; changeKind: ExternalChangeKind; elementKind: string;
    neutralPath: string; values: Record<string, unknown>;
}
export interface RepresentableImportDiagnostic {
    externalId: string; representability: Representability; code: string; message: string; suggestion: string;
}
export interface RepresentableImportPreview {
    previewId: string; technology: string; metamodelId: string; generatedAt: string;
    operations: NeutralChangeOperation[]; diagnostics: RepresentableImportDiagnostic[]; applicable: boolean;
}
