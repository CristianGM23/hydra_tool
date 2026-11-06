import type { HydraPersistedConnectionAppearance } from '../designer-ui/HydraConnectionVisual';
export type GamificationRepeatPolicy = 'ONCE' | 'ONCE_PER_EVENT' | 'UNLIMITED' | 'COOLDOWN' | 'DAILY_LIMIT';
export type GamificationEffectKind = 'AWARD' | 'PENALTY';
export interface PointTypeDefinition { id: string; name: string; description: string; minimumBalance: number; }
export interface LevelDefinition { id: string; name: string; pointTypeId: string; minimumPoints: number; rewardId: string; }
export interface AchievementDefinition { id: string; name: string; description: string; rewardId: string; hidden: boolean; }
export interface ChallengeDefinition { id: string; name: string; description: string; targetCount: number; rewardId: string; }
export interface RewardDefinition { id: string; name: string; description: string; payload: Record<string, unknown>; }
export interface GamificationRule {
    id: string; name: string; eventTypeId: string; conditionPath: string; operator: string; expectedValue?: unknown;
    pointsTypeId: string; points: number; achievementId: string; challengeId: string; rewardId: string;
    effectKind: GamificationEffectKind; repeatPolicy: GamificationRepeatPolicy; cooldownSeconds: number; maxAwardsPerActor: number; maxAwardsPerDay: number; enabled: boolean;
}
export interface GamificationDefinitionModel {
    schemaVersion: '1.0'; id: string; metamodelId: string; name: string; pointTypes: PointTypeDefinition[]; levels: LevelDefinition[];
    achievements: AchievementDefinition[]; challenges: ChallengeDefinition[]; rewards: RewardDefinition[]; annotations: Record<string, string>;
}
export interface GamificationBindingModel { schemaVersion: '1.0'; id: string; metamodelId: string; definitionModelId: string; rules: GamificationRule[]; annotations: Record<string, string>; }
export interface GamificationDiagnostic { severity: 'ERROR' | 'WARNING' | 'INFO'; code: string; elementId: string; path: string; message: string; }
export interface GamificationCheckReport { definitionModelId: string; bindingModelId: string; diagnostics: GamificationDiagnostic[]; valid?: boolean; }
export interface GamificationLoadResult { definitions: GamificationDefinitionModel; definitionEtag: string; bindings: GamificationBindingModel; bindingEtag: string; checkReport: GamificationCheckReport; }
export interface GamificationActorState { actorId: string; points: Record<string, number>; levels?: Record<string, string>; achievements?: string[]; challengeProgress?: Record<string, number>; rewards?: string[]; }
export interface GamificationRuntimeState { schemaVersion: '1.0'; id: string; metamodelId: string; updatedAt: string; actors: Record<string, GamificationActorState>; processedAwardKeys: string[]; }
export interface GamificationLedgerEntry { id: string; actorId: string; sourceEventId: string; ruleId: string; pointsTypeId: string; pointsDelta: number; achievementId: string; challengeId: string; rewardId: string; status: 'ACTIVE' | 'REJECTED' | 'COMPENSATED'; compensationOf: string; occurredAt: string; correlationId: string; metadata: Record<string, string>; }
export interface GamificationLedger { schemaVersion: '1.0'; id: string; metamodelId: string; updatedAt: string; entries: GamificationLedgerEntry[]; }
export interface GamificationRuntimeSnapshot { state: GamificationRuntimeState; stateEtag: string; ledger: GamificationLedger; ledgerEtag: string; }

export type GamificationDiagramNodeKind = 'EVIDENCE' | 'RULE' | 'POINT' | 'LEVEL' | 'ACHIEVEMENT' | 'CHALLENGE' | 'REWARD' | 'LEDGER';
export interface GamificationDiagramNodeView { nodeId: string; x: number; y: number; width: number; height: number; collapsed: boolean; }
export interface GamificationDiagramPoint { x: number; y: number; }
export interface GamificationDiagramEdgeView extends HydraPersistedConnectionAppearance { edgeId: string; points: GamificationDiagramPoint[]; sourceHandle?: string; targetHandle?: string; label?: string; note?: string; }
export interface GamificationDiagramDocument {
    schemaVersion: '1.0';
    id: string;
    metamodelId: string;
    definitionModelId: string;
    bindingModelId: string;
    nodes: GamificationDiagramNodeView[];
    edges: GamificationDiagramEdgeView[];
}
export interface GamificationDiagramLoadResult { diagram: GamificationDiagramDocument; etag: string; }

export type UserFeedbackKind = 'POINTS_AWARDED'|'PENALTY_APPLIED'|'ACHIEVEMENT_UNLOCKED'|'LEVEL_REACHED'|'CHALLENGE_PROGRESS'|'CHALLENGE_COMPLETED'|'REWARD_UNLOCKED'|'APPLICATION_REJECTED'|'AWARD_COMPENSATED';
export type UserFeedbackSeverity = 'SUCCESS'|'INFO'|'WARNING'|'ERROR';
export type UserFeedbackSurface = 'TOAST'|'ACTIVITY'|'STATUS'|'PROGRESS'|'DIALOG';
export interface UserFeedbackPresentationPolicy { enabled:boolean; surfaces:UserFeedbackSurface[]; durationMillis:number; persistActivity:boolean; showPoints:boolean; showProgress:boolean; maxVisible:number; dedupeWindowSeconds:number; aggregateWindowMillis:number; includeRejected:boolean; }
export interface UserFeedbackAction { id:string; label:string; kind:string; parameters:Record<string,string>; }
export interface UserFeedbackEnvelope { schemaVersion:'1.0'; id:string; semanticScopeId:string; actorId:string; kind:UserFeedbackKind; severity:UserFeedbackSeverity; title:string; message:string; reason:string; nextStep:string; pointsDelta:number; pointTypeId:string; progressCurrent:number; progressTarget:number; achievementId:string; challengeId:string; rewardId:string; levelId:string; sourceEventId:string; correlationId:string; occurredAt:string; presentation:UserFeedbackPresentationPolicy; actions:UserFeedbackAction[]; metadata:Record<string,string>; }
