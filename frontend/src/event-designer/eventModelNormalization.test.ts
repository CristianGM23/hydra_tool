import { describe, expect, it } from 'vitest';
import {
    normalizeEventBindingModel,
    normalizeEventDefinitionModel,
    normalizeEventModelLoadResult
} from './eventModelNormalization';
import type { EventBindingModel, EventDefinitionModel, EventModelLoadResult } from './types';

describe('event model transport normalization', () => {
    it('restores collections and false booleans omitted by NON_EMPTY JSON serialization', () => {
        const raw = {
            definitions: {
                schemaVersion: '1.0',
                id: 'event-definitions:people',
                metamodelId: 'people',
                namespace: 'people.events',
                eventTypes: [{
                    id: 'event:created',
                    name: 'Created',
                    namespace: 'people.events',
                    category: 'CUSTOM',
                    version: '1.0',
                    persistencePolicy: 'TRANSIENT'
                }]
            },
            definitionEtag: 'definitions-etag',
            bindings: {
                schemaVersion: '1.0',
                id: 'event-bindings:people',
                metamodelId: 'people',
                eventDefinitionModelId: 'event-definitions:people',
                bindings: [{
                    id: 'binding:created',
                    sourceEventTypeId: 'core.model.element-created',
                    targetEventTypeId: 'event:created'
                }]
            },
            bindingEtag: 'bindings-etag',
            checkReport: {
                definitionModelId: 'event-definitions:people',
                bindingModelId: 'event-bindings:people'
            }
        } as unknown as EventModelLoadResult;

        const normalized = normalizeEventModelLoadResult(raw);
        const eventType = normalized.definitions.eventTypes[0]!;
        const binding = normalized.bindings.bindings[0]!;

        expect(eventType).toMatchObject({
            description: '',
            fields: [],
            allowedSources: [],
            allowedSubjects: [],
            abstractType: false,
            deprecated: false,
            enabled: false,
            annotations: {}
        });
        expect(binding).toMatchObject({
            name: 'binding:created',
            conditionExpressionId: '',
            payloadMappings: [],
            enabled: false,
            errorPolicy: 'REPORT',
            annotations: {}
        });
        expect(normalized.bindings.expressionModelId).toBe('');
        expect(normalized.bindings.annotations).toEqual({});
        expect(normalized.definitions.annotations).toEqual({});
        expect(normalized.checkReport.diagnostics).toEqual([]);
    });

    it('normalizes models independently for Source-tab JSON before rendering', () => {
        const definitions = normalizeEventDefinitionModel({
            schemaVersion: '1.0',
            id: 'event-definitions:people',
            metamodelId: 'people',
            namespace: 'people.events'
        } as unknown as EventDefinitionModel);
        const bindings = normalizeEventBindingModel({
            schemaVersion: '1.0',
            id: 'event-bindings:people',
            metamodelId: 'people',
            eventDefinitionModelId: 'event-definitions:people'
        } as unknown as EventBindingModel);

        expect(definitions.eventTypes).toEqual([]);
        expect(definitions.annotations).toEqual({});
        expect(bindings.bindings).toEqual([]);
        expect(bindings.expressionModelId).toBe('');
        expect(bindings.annotations).toEqual({});
    });
});
