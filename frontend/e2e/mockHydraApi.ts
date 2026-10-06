import type { Page, Route } from '@playwright/test';

/** Deterministic browser-only API surface for UI/accessibility suites.
 * Product/live API behaviour is qualified separately against the managed backend.
 */
interface MockHydraApiOptions {
    semanticContext?: 'NONE' | 'EVENTS';
}

const eventMetamodel = {
    schemaVersion: '2.1', id: 'people', name: 'People', annotations: {},
    classes: [{ id: 'person', name: 'Person', kind: 'CLASS', annotations: {}, attributes: [], operations: [] }],
    enums: [], dataTypes: [], relations: []
};

export async function installMockHydraApi(page: Page, options: MockHydraApiOptions = {}): Promise<void> {
    const withEvents = options.semanticContext === 'EVENTS';
    await page.route('**/api/**', async (route: Route) => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        if (path === '/api/system/status') return json(route, { application: 'HYDRA', status: 'UP' });
        if (path === '/api/projects/session') return json(route, { active: false, project: null, openTabs: [] });
        if (path === '/api/projects/catalog') return json(route, { presets: [], capabilities: [] });
        if (path === '/api/metamodels') return json(route, withEvents ? [{ id: 'people', fileName: 'people.ecore', rootPackageCount: 1 }] : []);
        if (withEvents && path === '/api/canonical-metamodels/people') return json(route, eventMetamodel);
        if (withEvents && path === '/api/canonical-metamodels/events/reserved-types') return json(route, ['core.model.element-created']);
        if (withEvents && path === '/api/canonical-metamodels/people/events') return json(route, {
            definitions: { schemaVersion: '1.0', id: 'people-events', metamodelId: 'people', namespace: 'editor.people', eventTypes: [], annotations: {} },
            definitionEtag: 'events-v1',
            bindings: { schemaVersion: '1.0', id: 'people-event-bindings', metamodelId: 'people', eventDefinitionModelId: 'people-events', expressionModelId: '', bindings: [], annotations: {} },
            bindingEtag: 'bindings-v1',
            checkReport: { definitionModelId: 'people-events', bindingModelId: 'people-event-bindings', diagnostics: [] }
        });
        if (withEvents && path === '/api/canonical-metamodels/people/events/diagram') return json(route, {
            diagram: { schemaVersion: '1.1', id: 'people-event-architecture-diagram', metamodelId: 'people', eventDefinitionModelId: 'people-events', eventBindingModelId: 'people-event-bindings', nodes: [], edges: [] },
            etag: 'diagram-v1'
        });
        if (path === '/api/projects/recent' || path === '/api/grammars' || path === '/api/notations' || path === '/api/models/files') return json(route, []);
        return json(route, []);
    });
}

async function json(route: Route, body: unknown): Promise<void> {
    await route.fulfill({ status: 200, contentType: 'application/json; charset=utf-8', body: JSON.stringify(body) });
}
