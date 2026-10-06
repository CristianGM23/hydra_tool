export type GeneratedProjectStage = 'GENERATE' | 'VALIDATE' | 'OPEN' | 'LAUNCH';
export type GeneratedProjectStepStatus = 'PASSED' | 'FAILED' | 'SKIPPED' | 'PLANNED';

export interface GeneratedProjectStageDefinition {
    stage: GeneratedProjectStage;
    description: string;
    windowsCommand: string[];
    unixCommand: string[];
    timeoutSeconds: number;
    optional: boolean;
    environment: Record<string, string>;
}

export interface GeneratedProjectRuntimeProfile {
    key: string;
    name: string;
    technology: string;
    description: string;
    stages: GeneratedProjectStageDefinition[];
}

export interface GeneratedProjectDefinition {
    id: string;
    name: string;
    profileKey: string;
    relativeDirectory: string;
    registeredAt?: string;
    metadata: Record<string, string>;
}

export interface GeneratedProjectRegistry {
    schemaVersion: string;
    modifiedAt: string;
    projects: GeneratedProjectDefinition[];
}

export interface GeneratedProjectStepResult {
    stage: GeneratedProjectStage;
    status: GeneratedProjectStepStatus;
    description: string;
    command: string[];
    exitCode: number | null;
    durationMillis: number;
    summary: string;
    diagnostics: string[];
}

export interface GeneratedProjectExecutionReport {
    runId: string;
    project: GeneratedProjectDefinition;
    resolvedDirectory: string;
    startedAt: string;
    completedAt: string;
    steps: GeneratedProjectStepResult[];
    successful: boolean;
}
