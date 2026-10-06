export type PublicationChannel = 'INTERNAL' | 'BETA' | 'STABLE';
export type PublicationSigningMode = 'CHECKSUM_ONLY' | 'EXTERNAL_SIGNATURE';
export interface PublicationArtifactRequest { platform: string; relativePath: string; mediaType: string; }
export interface PublicationDiagnostic { severity: string; code: string; message: string; field: string; }
export interface PublicationValidationReport { valid: boolean; diagnostics: PublicationDiagnostic[]; }
export interface PublicationArtifact {
    platform: string; fileName: string; mediaType: string; size: number; sha256: string;
    downloadUrl: string; signatureFile: string;
}
export interface PublicationManifest {
    schemaVersion: string; productId: string; version: string; channel: PublicationChannel;
    signingMode: PublicationSigningMode; publishedAt: string; artifacts: PublicationArtifact[];
    metadata: Record<string, string>;
}
export interface PublicationResult {
    manifest: PublicationManifest; manifestPath: string; channelManifestPath: string;
    validation: PublicationValidationReport;
}
export interface UpdateCheckResult {
    updateAvailable: boolean; currentVersion: string; availableVersion: string;
    artifact: PublicationArtifact | null; message: string;
}
