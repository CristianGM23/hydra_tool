export type LayerMergeStrategy = 'MERGE' | 'REPLACE';

export interface LayerInterchangeFormat<TFormat extends string> {
    id: TFormat;
    label: string;
    description: string;
    extensions: readonly string[];
    mimeType?: string;
}

export type LayerMetricTone = 'NEUTRAL' | 'GOOD' | 'WARNING' | 'ERROR';

export interface LayerImportMetric {
    id: string;
    label: string;
    value: number;
    tone?: LayerMetricTone;
    detail?: string;
}

export interface LayerImportDiagnostic {
    severity: 'ERROR' | 'WARNING' | 'INFO';
    category: string;
    message: string;
    itemId?: string;
}

export interface LayerImportPreviewItem {
    id: string;
    label: string;
    category: string;
    description?: string;
    source?: string;
    warnings?: readonly string[];
    selectedByDefault?: boolean;
}

export interface LayerImportAnalysis {
    summary: string;
    canImport?: boolean;
    metrics: readonly LayerImportMetric[];
    items?: readonly LayerImportPreviewItem[];
    diagnostics?: readonly LayerImportDiagnostic[];
    warnings?: readonly string[];
}

export interface LayerImportSelection {
    itemIds: readonly string[];
    categories: readonly string[];
}

export interface LayerImportRequest<TFormat extends string> {
    format: TFormat;
    fileName: string;
    content: string;
    strategy: LayerMergeStrategy;
    selection?: LayerImportSelection;
}

export interface LayerImportAnalysisRequest<TFormat extends string> {
    format: TFormat;
    fileName: string;
    content: string;
}

export interface LayerImportResult {
    summary: string;
    details: readonly string[];
    warnings: readonly string[];
    metrics?: readonly LayerImportMetric[];
    diagnostics?: readonly LayerImportDiagnostic[];
}

export interface LayerExportArtifact {
    fileName: string;
    content: string;
    mimeType?: string;
}
