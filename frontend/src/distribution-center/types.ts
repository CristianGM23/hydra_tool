export type StandaloneDistributionTarget = 'PWA' | 'TAURI_WINDOWS' | 'TAURI_MACOS' | 'TAURI_LINUX';

export interface StandaloneDistributionProfile {
    productName: string;
    version: string;
    targets: StandaloneDistributionTarget[];
    offlineEnabled: boolean;
    fileAssociations: string[];
    updateChannel: string;
}

export interface StandaloneDistributionArtifact { path: string; mediaType: string; content: string; }
export interface StandaloneDistributionDiagnostic { code: string; severity: 'INFO' | 'WARNING' | 'ERROR'; path: string; message: string; }
export interface StandaloneDistributionValidationReport { valid: boolean; diagnostics: StandaloneDistributionDiagnostic[]; metadata: Record<string, string>; }
export interface StandaloneDistributionMaterializationResult { root: string; writtenPaths: string[]; sha256ByPath: Record<string, string>; }
export interface StandaloneDistributionPreparationResult { materialization: StandaloneDistributionMaterializationResult; validation: StandaloneDistributionValidationReport; }
