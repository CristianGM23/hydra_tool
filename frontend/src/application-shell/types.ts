export type HydraModuleId =
    | 'HOME'
    | 'METAMODEL'
    | 'GRAMMARS'
    | 'TEXTUAL_MODELS'
    | 'EDITORS'
    | 'VALIDATIONS'
    | 'QUICK_FIXES'
    | 'EVENTS'
    | 'EVENT_FLOWS'
    | 'GAMIFICATION'
    | 'TRANSFORMATIONS'
    | 'MODEL'
    | 'INTEGRATIONS'
    | 'ROUND_TRIP'
    | 'REPRESENTABLE_IMPORTS'
    | 'UPDATE_SITE_IMPORTS'
    | 'DISTRIBUTION'
    | 'PUBLICATION'
    | 'CAPABILITIES'
    | 'COMPATIBILITY'
    | 'GENERATED_PROJECTS'
    | 'ARTIFACTS'
    | 'REPOSITORIES';

export type HydraModuleCategory = 'START' | 'DSL' | 'USE' | 'DELIVERY' | 'OPERATIONS';

export type HydraCapabilityRequirement = 'METAMODEL' | 'SEMANTIC_CONTRACT' | 'COMPILED_GRAMMAR' | 'EDITOR_DEFINITION' | 'TEXTUAL_EDITOR_DEFINITION';

export interface HydraModuleRequirements {
    allOf?: readonly HydraCapabilityRequirement[];
    anyOf?: readonly HydraCapabilityRequirement[];
}

export interface HydraModuleDefinition {
    id: HydraModuleId;
    title: string;
    shortTitle: string;
    description: string;
    category: HydraModuleCategory;
    /** @deprecated Compatibility flag while older shell consumers migrate to requirements. */
    requiresMetamodel: boolean;
    requirements?: HydraModuleRequirements;
    accent: string;
}
