import type { HydraPersistedConnectionAppearance } from '../designer-ui/HydraConnectionVisual';
export type EventPersistencePolicy = 'TRANSIENT' | 'SESSION' | 'AUDIT' | 'GAMIFICATION' | 'EXTERNAL';
export type EventSubjectKind = 'METAMODEL' | 'META_CLASS' | 'META_ATTRIBUTE' | 'META_REFERENCE' | 'META_OPERATION' | 'MODEL' | 'MODEL_ELEMENT' | 'MODEL_FEATURE' | 'VALIDATION_RULE' | 'VALIDATION_DIAGNOSTIC' | 'QUICK_FIX' | 'ACTION' | 'EVENT_DEFINITION' | 'EVENT_FLOW' | 'EDITOR' | 'GAMIFICATION_RULE' | 'GAMIFICATION_REWARD';
export type EventFieldType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'DURATION' | 'DOCUMENT_ID' | 'ELEMENT_ID' | 'ENUM' | 'OBJECT' | 'ARRAY' | 'ANY';
export interface EventFieldDefinition { id: string; name: string; type: EventFieldType; required: boolean; many: boolean; enumTypeId: string; objectTypeId: string; description: string; }
export interface EventTypeDefinition { id: string; name: string; namespace: string; description: string; category: string; version: string; fields: EventFieldDefinition[]; allowedSources: string[]; allowedSubjects: EventSubjectKind[]; persistencePolicy: EventPersistencePolicy; abstractType: boolean; deprecated: boolean; enabled: boolean; annotations: Record<string, string>; }
export interface EventDefinitionModel { schemaVersion: '1.0'; id: string; metamodelId: string; namespace: string; eventTypes: EventTypeDefinition[]; annotations: Record<string, string>; }
export type EventBindingErrorPolicy = 'SKIP' | 'REPORT' | 'DISABLE_BINDING';
export interface EventPayloadMapping { targetFieldId: string; sourcePath: string; expressionId: string; fixedValue?: unknown; }
export interface EventBinding { id: string; name: string; sourceEventTypeId: string; targetEventTypeId: string; conditionExpressionId: string; payloadMappings: EventPayloadMapping[]; enabled: boolean; errorPolicy: EventBindingErrorPolicy; annotations: Record<string, string>; }
export interface EventBindingModel { schemaVersion: '1.0'; id: string; metamodelId: string; eventDefinitionModelId: string; expressionModelId: string; bindings: EventBinding[]; annotations: Record<string, string>; }
export interface EventDiagnostic { severity: 'ERROR' | 'WARNING' | 'INFO'; code: string; elementId: string; path: string; message: string; }
export interface EventModelCheckReport { definitionModelId: string; bindingModelId: string; diagnostics: EventDiagnostic[]; valid?: boolean; }
export interface EventModelLoadResult { definitions: EventDefinitionModel; definitionEtag: string; bindings: EventBindingModel; bindingEtag: string; checkReport: EventModelCheckReport; }
export type EventModelSaveResponse = EventModelLoadResult;

export type EventArchitectureDiagramNodeKind = 'RESERVED_EVENT' | 'CUSTOM_EVENT';
export interface EventArchitectureDiagramPoint { x: number; y: number; }
export interface EventArchitectureDiagramNodeView { nodeId: string; x: number; y: number; width: number; height: number; collapsed: boolean; }
export interface EventArchitectureDiagramEdgeView extends HydraPersistedConnectionAppearance { edgeId: string; points: EventArchitectureDiagramPoint[]; sourceHandle?: string; targetHandle?: string; label?: string; note?: string; }
export interface EventArchitectureDiagramDocument {
    schemaVersion: '1.1';
    id: string;
    metamodelId: string;
    eventDefinitionModelId: string;
    eventBindingModelId: string;
    nodes: EventArchitectureDiagramNodeView[];
    edges: EventArchitectureDiagramEdgeView[];
}
export interface EventArchitectureDiagramLoadResult { diagram: EventArchitectureDiagramDocument; etag: string; }
