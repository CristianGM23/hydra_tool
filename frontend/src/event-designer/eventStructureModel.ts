import type { DesignerStructureNode } from '../designer-ui/DesignerStructureTree';
import { presentEventType } from './eventTypePresentation';
import type { EventBindingModel, EventDefinitionModel } from './types';
import type { EventStructureSelection } from './eventStructureTypes';

export function buildEventStructure(definitions: EventDefinitionModel, bindings: EventBindingModel, reservedTypes: string[]): DesignerStructureNode<EventStructureSelection>[] {
    const customEvents: DesignerStructureNode<EventStructureSelection>[] = definitions.eventTypes.map(type => ({
        id: `event:${type.id}`,
        label: type.name || 'Unnamed event',
        detail: type.id,
        badge: type.enabled ? type.persistencePolicy : 'DISABLED',
        data: { kind: 'EVENT', id: type.id },
        children: [
            { id: `event-category:${type.id}`, label: 'Category', detail: type.category || 'CUSTOM', badge: 'PROPERTY', data: { kind: 'EVENT', id: type.id } },
            { id: `event-fields:${type.id}`, label: 'Event data', badge: String(type.fields.length), data: { kind: 'EVENT', id: type.id }, children: type.fields.map(field => ({ id: `event-field:${type.id}:${field.id}`, label: field.name, detail: `${field.type}${field.many ? '[]' : ''} · ${field.id}`, badge: field.required ? 'REQUIRED' : 'FIELD', data: { kind: 'EVENT', id: type.id } })) },
            { id: `event-subjects:${type.id}`, label: 'Allowed subjects', badge: String(type.allowedSubjects.length), data: { kind: 'EVENT', id: type.id }, children: type.allowedSubjects.map(subject => ({ id: `event-subject:${type.id}:${subject}`, label: subject, badge: 'SUBJECT', data: { kind: 'EVENT', id: type.id } })) }
        ]
    }));
    const bindingNodes: DesignerStructureNode<EventStructureSelection>[] = bindings.bindings.map(binding => ({
        id: `binding:${binding.id}`,
        label: binding.name || 'Unnamed binding',
        detail: binding.id,
        badge: binding.enabled ? 'BINDING' : 'DISABLED',
        data: { kind: 'BINDING', id: binding.id },
        children: [
            { id: `binding-source:${binding.id}`, label: 'Source', detail: presentEventType(binding.sourceEventTypeId, definitions.eventTypes).label, badge: 'EVENT', data: { kind: 'BINDING', id: binding.id } },
            { id: `binding-target:${binding.id}`, label: 'Target', detail: presentEventType(binding.targetEventTypeId, definitions.eventTypes).label, badge: 'EVENT', data: { kind: 'BINDING', id: binding.id } },
            { id: `binding-mappings:${binding.id}`, label: 'Data mappings', badge: String(binding.payloadMappings.length), data: { kind: 'BINDING', id: binding.id }, children: binding.payloadMappings.map((mapping, index) => ({ id: `binding-mapping:${binding.id}:${index}`, label: mapping.targetFieldId || `Mapping ${index + 1}`, detail: mapping.expressionId || mapping.sourcePath || String(mapping.fixedValue ?? ''), badge: 'MAPPING', data: { kind: 'BINDING', id: binding.id } })) }
        ]
    }));
    return [{
        id: 'event-document-root',
        label: definitions.namespace || 'Event contracts',
        detail: definitions.id,
        badge: 'EVENT MODEL',
        children: [
            { id: 'custom-events-root', label: 'Custom event types', badge: String(customEvents.length), children: customEvents },
            { id: 'bindings-root', label: 'Bindings and transformations', badge: String(bindingNodes.length), children: bindingNodes },
            { id: 'system-events-root', label: 'System event catalog', badge: String(reservedTypes.length), children: reservedTypes.map(id => { const presentation = presentEventType(id, definitions.eventTypes); return { id: `system-event:${id}`, label: presentation.label, detail: id, badge: presentation.category }; }) }
        ]
    }];
}
