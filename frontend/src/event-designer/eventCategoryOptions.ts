import type { HydraSelectOption } from '../components/HydraAdaptiveSelect';

export const EVENT_CATEGORY_OPTIONS: readonly HydraSelectOption[] = [
    { value: 'DOMAIN', label: 'Domain / business', description: 'Recommended for facts that belong to the modeled business or domain and are not HYDRA runtime events.', category: 'Recommended' },
    { value: 'METAMODEL', label: 'Metamodel', description: 'Structural facts about classes, attributes, relations, operations or other metamodel elements.', category: 'HYDRA families' },
    { value: 'MODEL', label: 'Model', description: 'Facts about model instances, model elements, values, relations, saving or model lifecycle.', category: 'HYDRA families' },
    { value: 'VALIDATION', label: 'Validation', description: 'Validation executions, diagnostics, quality checks or rule outcomes.', category: 'HYDRA families' },
    { value: 'QUICK_FIX', label: 'Quick fix', description: 'Facts produced while proposing, previewing or applying a repair.', category: 'HYDRA families' },
    { value: 'EVENT', label: 'Events', description: 'Facts about event publication, transformation, delivery or event-runtime processing.', category: 'HYDRA families' },
    { value: 'EVENT_FLOW', label: 'Event flow', description: 'Facts produced by an EventFlow definition, node, transition or runtime execution.', category: 'HYDRA families' },
    { value: 'GAMIFICATION', label: 'Gamification', description: 'Verified evidence about points, achievements, challenges, levels, rewards or penalties.', category: 'HYDRA families' },
    { value: 'EDITOR', label: 'Editor and representation', description: 'Facts about editor definitions, representations, tools, forms or editor-runtime interaction.', category: 'HYDRA families' },
    { value: 'IMPORT_EXPORT', label: 'Import / export', description: 'Facts emitted while analyzing, importing, exporting, generating or packaging artifacts.', category: 'Delivery and integration' },
    { value: 'INTEGRATION', label: 'Integration', description: 'Facts exchanged with configured adapters, repositories or external platforms.', category: 'Delivery and integration' },
    { value: '__CUSTOM__', label: 'Custom category…', description: 'Use a stable domain-specific category when none of the predefined families accurately describes the event.', category: 'Other' }
] as const;

export const PREDEFINED_EVENT_CATEGORIES = new Set(EVENT_CATEGORY_OPTIONS.filter(option => option.value !== '__CUSTOM__').map(option => option.value));

export function isPredefinedEventCategory(category: string): boolean {
    return PREDEFINED_EVENT_CATEGORIES.has(category.trim().toUpperCase());
}
