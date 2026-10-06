import type {
    UpdateSiteImportDashboard as Dashboard,
    UpdateSiteReconciliationReport,
    UpdateSiteReleaseComparison,
    UpdateSiteSemanticRecovery
} from './types';

export type UpdateSiteTab = 'OVERVIEW' | 'RECOVERY' | 'LOGS' | 'EVIDENCE' | 'RECONCILIATION' | 'MATERIALIZATION' | 'MERGE' | 'REMOTE' | 'RELEASES' | 'SEMANTIC' | 'ADAPTERS' | 'PERFORMANCE' | 'GOVERNANCE';

export const EMPTY_DASHBOARD: Dashboard = {
    inspectedFiles: 0, recoveredFiles: 0, partialFiles: 0, supportingFiles: 0,
    unrecoverableFiles: 0, failedFiles: 0, recoveryCoverage: 0, confidence: [],
    fileStatus: [], bundleRoles: [], stages: [], flowNodes: [], flowLinks: []
};

export const EMPTY_RECONCILIATION: UpdateSiteReconciliationReport = {
    candidates: [], conflicts: [], agreedElements: 0, unionElements: 0,
    agreementPercent: 0, recommendedCandidateId: '', mergeReady: false, diagnostics: []
};

export const EMPTY_RELEASES: UpdateSiteReleaseComparison = {
    compositeRepositories: [], versionFamilies: [], deltas: [],
    unresolvedCompositeChildren: 0, diagnostics: []
};

export const EMPTY_SEMANTIC: UpdateSiteSemanticRecovery = {
    elements: [], representableCount: 0, partialCount: 0,
    unrepresentableCount: 0, diagnostics: []
};


export const TAB_DEFINITIONS: ReadonlyArray<{ id: UpdateSiteTab; label: string; description: string; group: string }> = [
    { id: 'OVERVIEW', label: 'Overview', description: 'Dashboard and repository summary', group: 'Understand' },
    { id: 'RECOVERY', label: 'Recovery', description: 'Candidates, bundles and neutral layers', group: 'Understand' },
    { id: 'LOGS', label: 'Logs', description: 'Diagnostics, incomplete files and grammars', group: 'Review' },
    { id: 'EVIDENCE', label: 'Evidence', description: 'Element provenance and confidence', group: 'Review' },
    { id: 'RECONCILIATION', label: 'Reconciliation', description: 'Resolve structural differences', group: 'Act' },
    { id: 'MATERIALIZATION', label: 'Materialization', description: 'Create editable HYDRA layers', group: 'Act' },
    { id: 'MERGE', label: 'Merge', description: 'Compose into an existing editor', group: 'Act' },
    { id: 'REMOTE', label: 'Remote p2', description: 'Analyze allowlisted HTTPS repositories', group: 'Advanced' },
    { id: 'RELEASES', label: 'Releases', description: 'Composite repositories and versions', group: 'Advanced' },
    { id: 'SEMANTIC', label: 'Semantic', description: 'Commands, handlers and runtime behavior', group: 'Advanced' },
    { id: 'ADAPTERS', label: 'Adapter SDK', description: 'Author safe declarative adapters', group: 'Advanced' },
    { id: 'PERFORMANCE', label: 'Performance', description: 'Cache and module budgets', group: 'Advanced' },
    { id: 'GOVERNANCE', label: 'Governance', description: 'Release and distribution evidence, mirrors, performance and provenance', group: 'Advanced' }
];

export function formatBytes(value: number): string {
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
    return `${(value / (1024 * 1024)).toFixed(1)} MiB`;
}
