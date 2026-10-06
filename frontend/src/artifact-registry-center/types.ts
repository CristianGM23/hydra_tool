export type HydraArtifactStatus = 'AVAILABLE' | 'STALE' | 'MISSING_DEPENDENCY' | 'CONFLICTED' | 'INVALID';
export interface HydraArtifactDependency { artifactId: string; required: boolean; minimumVersion: string; }
export interface HydraArtifactDescriptor {
    id: string; name: string; kind: string; version: string; producer: string; relativePath: string;
    sha256: string; status: HydraArtifactStatus; modifiedAt: string;
    dependencies: HydraArtifactDependency[]; metadata: Record<string, string>;
}
export interface HydraArtifactRegistrySnapshot { schemaVersion: string; generatedAt: string; artifacts: HydraArtifactDescriptor[]; }
export interface HydraArtifactGraphNode {
    artifactId: string; status: HydraArtifactStatus; dependencies: string[]; dependants: string[]; diagnostics: string[];
}
export interface HydraArtifactDependencyGraph { nodes: HydraArtifactGraphNode[]; cycles: string[][]; ready: boolean; }
