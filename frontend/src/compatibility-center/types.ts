export type CompatibilityStatus = 'PASSED' | 'PARTIAL' | 'FAILED' | 'NOT_RUN';

export interface NativeToolchainCatalogEntry {
    key: string;
    technology: string;
    version: string;
    description: string;
    commandIds: string[];
}

export interface NativeToolchainProbeResult {
    technology: string;
    version: string;
    status: CompatibilityStatus;
    message: string;
    evidence: string[];
    durationMillis: number;
}

export interface TechnologyCompatibilityMatrix {
    generatedAt: string;
    overallStatus: CompatibilityStatus;
    results: NativeToolchainProbeResult[];
}
