import type { DesignerSelectOptionHelp } from '../designer-ui/DesignerFieldHelp';
import type { EventTypeDefinition } from './types';

export interface EventTypePresentation {
    label: string;
    description: string;
    category: string;
}

const RESERVED_EVENT_PRESENTATIONS: Record<string, EventTypePresentation> = {
    'core.metamodel.class-created': { label: 'Metamodel class created', description: 'Published after a new class is added to the metamodel.', category: 'Metamodel' },
    'core.metamodel.attribute-created': { label: 'Metamodel attribute created', description: 'Published after a new attribute is added to a metamodel class.', category: 'Metamodel' },
    'core.metamodel.reference-created': { label: 'Metamodel relation created', description: 'Published after a new relation is added between metamodel classes.', category: 'Metamodel' },
    'core.metamodel.operation-created': { label: 'Metamodel operation created', description: 'Published after a new operation is added to a class or interface.', category: 'Metamodel' },
    'core.model.element-created': { label: 'Model element created', description: 'Published after a new element is created in a model.', category: 'Model' },
    'core.model.element-updated': { label: 'Model element updated', description: 'Published after the data or relations of a model element change.', category: 'Model' },
    'core.model.element-deleted': { label: 'Model element deleted', description: 'Published after a model element is removed.', category: 'Model' },
    'core.model.saved': { label: 'Model saved', description: 'Published after a model is stored successfully.', category: 'Model' },
    'core.model.exported': { label: 'Model exported', description: 'Published after a model is exported to another format.', category: 'Model' },
    'core.validation.execution-started': { label: 'Validation started', description: 'Published when a validation execution begins.', category: 'Validation' },
    'core.validation.execution-completed': { label: 'Validation completed', description: 'Published when a validation execution finishes and its report is available.', category: 'Validation' },
    'core.validation.violation-created': { label: 'Problem detected', description: 'Published when validation finds a new problem.', category: 'Validation' },
    'core.validation.violation-resolved': { label: 'Problem resolved', description: 'Published when a previously reported validation problem disappears.', category: 'Validation' },
    'core.validation.violation-reopened': { label: 'Problem reopened', description: 'Published when a resolved validation problem appears again.', category: 'Validation' },
    'core.quickfix.started': { label: 'Quick fix started', description: 'Published when HYDRA begins applying a quick fix.', category: 'Quick fix' },
    'core.quickfix.applied': { label: 'Quick fix applied', description: 'Published after a quick-fix action changes the model; it is not proof that the problem was resolved.', category: 'Quick fix' },
    'core.quickfix.failed': { label: 'Quick fix failed', description: 'Published when a quick fix cannot be completed.', category: 'Quick fix' },
    'core.quickfix.undone': { label: 'Quick fix undone', description: 'Published after the effects of a quick fix are undone.', category: 'Quick fix' },
    'core.quickfix.redone': { label: 'Quick fix redone', description: 'Published after an undone quick fix is applied again.', category: 'Quick fix' },
    'core.operation.invoked': { label: 'Operation invoked', description: 'Published when a metamodel operation is requested.', category: 'Operation' },
    'core.operation.completed': { label: 'Operation completed', description: 'Published after a metamodel operation finishes successfully.', category: 'Operation' },
    'core.operation.failed': { label: 'Operation failed', description: 'Published when a metamodel operation finishes with an error.', category: 'Operation' },
    'core.import.completed': { label: 'Import completed', description: 'Published after an import finishes and its result is available.', category: 'Import and export' },
    'core.export.completed': { label: 'Export completed', description: 'Published after an export finishes successfully.', category: 'Import and export' },
    'core.event-flow.started': { label: 'Event flow started', description: 'Published when an event flow starts processing an input event.', category: 'Event flow' },
    'core.event-flow.completed': { label: 'Event flow completed', description: 'Published when an event flow reaches a successful ending.', category: 'Event flow' },
    'core.event-flow.failed': { label: 'Event flow failed', description: 'Published when an event flow reaches an error ending.', category: 'Event flow' },
    'core.event-flow.derived-event': { label: 'Derived event produced', description: 'Published when an event flow produces another HYDRA event.', category: 'Event flow' },
    'core.gamification.award-granted': { label: 'Gamification award granted', description: 'Published when a verified gamification rule grants points, progress or a reward.', category: 'Gamification' },
    'core.gamification.award-rejected': { label: 'Gamification award rejected', description: 'Published when a gamification award is not granted because a rule or limit prevents it.', category: 'Gamification' },
    'core.gamification.penalty-applied': { label: 'Gamification penalty applied', description: 'Published when verified evidence subtracts points while respecting the configured minimum balance.', category: 'Gamification' },
    'core.gamification.award-compensated': { label: 'Gamification award compensated', description: 'Published when an earlier gamification award is reversed.', category: 'Gamification' }
};

export function presentEventType(eventTypeId: string, customTypes: EventTypeDefinition[] = []): EventTypePresentation {
    const custom = customTypes.find(type => type.id === eventTypeId);
    if (custom !== undefined) {
        return {
            label: custom.name || humanizeIdentifier(eventTypeId),
            description: custom.description || 'Custom event defined in the current event catalog.',
            category: custom.category || 'Custom event'
        };
    }
    return RESERVED_EVENT_PRESENTATIONS[eventTypeId] ?? {
        label: humanizeIdentifier(eventTypeId),
        description: eventTypeId.startsWith('core.')
            ? 'System event published by a HYDRA module.'
            : 'Custom or external event referenced by its canonical contract.',
        category: eventTypeId.startsWith('core.') ? 'System event' : 'Custom event'
    };
}

export function eventTypeOptionHelp(eventTypeIds: string[], customTypes: EventTypeDefinition[] = []): DesignerSelectOptionHelp[] {
    return eventTypeIds.map(value => {
        const presentation = presentEventType(value, customTypes);
        return { value, label: presentation.label, description: presentation.description };
    });
}

export function eventTypeLabel(eventTypeId: string, customTypes: EventTypeDefinition[] = []): string {
    return presentEventType(eventTypeId, customTypes).label;
}

export function eventTypeDescription(eventTypeId: string, customTypes: EventTypeDefinition[] = []): string {
    return presentEventType(eventTypeId, customTypes).description;
}

function humanizeIdentifier(value: string): string {
    const tail = value.split(/[.:/]/).filter(Boolean).at(-1) ?? value;
    return tail.split(/[-_]/).filter(Boolean).map(part => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(' ');
}
