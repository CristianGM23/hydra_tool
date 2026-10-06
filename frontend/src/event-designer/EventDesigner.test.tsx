import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EventDesigner } from './EventDesigner';
import { loadEventArchitectureDiagram, loadEventModels } from './api';

vi.mock('./api', () => ({
    loadReservedEventTypes: vi.fn().mockResolvedValue(['core.validation.violation-resolved']),
    loadEventModels: vi.fn(),
    createEventModels: vi.fn(),
    replaceEventModels: vi.fn(),
    loadEventArchitectureDiagram: vi.fn(),
    saveEventArchitectureDiagram: vi.fn()
}));


vi.mock('../metamodel-designer/api', () => ({
    loadMetaModel: vi.fn().mockResolvedValue({
        etag: 'm1',
        metamodel: {
            schemaVersion: '2.1', id: 'people', name: 'People', annotations: {},
            classes: [{ id: 'person', name: 'Person', kind: 'CLASS', annotations: {}, attributes: [{ id: 'name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: false, unique: true, defaultValue: '', annotations: {} }], operations: [] }],
            enums: [{ id: 'severity', name: 'Severity', annotations: {}, literals: [{ id: 'warning', name: 'Warning', value: 0, annotations: {} }] }], dataTypes: [], relations: []
        }
    })
}));

describe('event designer', () => {
    beforeEach(() => { vi.clearAllMocks(); });


    it('opens a persisted current event model without blanking the Events screen', async () => {
        vi.mocked(loadEventModels).mockResolvedValueOnce({
            definitions: {
                schemaVersion: '1.0', id: 'event-definitions:people', metamodelId: 'people', namespace: 'people.events',
                eventTypes: [{
                    id: 'event:created', name: 'Created', namespace: 'people.events', category: 'CUSTOM', version: '1.0',
                    persistencePolicy: 'TRANSIENT'
                }]
            },
            definitionEtag: 'definitions-etag',
            bindings: {
                schemaVersion: '1.0', id: 'event-bindings:people', metamodelId: 'people',
                eventDefinitionModelId: 'event-definitions:people',
                bindings: [{
                    id: 'binding:current', sourceEventTypeId: 'core.validation.violation-resolved', targetEventTypeId: 'event:created'
                }]
            },
            bindingEtag: 'bindings-etag',
            checkReport: { definitionModelId: 'event-definitions:people', bindingModelId: 'event-bindings:people' }
        } as unknown as Awaited<ReturnType<typeof loadEventModels>>);
        vi.mocked(loadEventArchitectureDiagram).mockResolvedValueOnce({
            diagram: {
                schemaVersion: '1.1', id: 'people-event-architecture-diagram', metamodelId: 'people',
                eventDefinitionModelId: 'event-definitions:people', eventBindingModelId: 'event-bindings:people',
                nodes: [
                    { nodeId: 'core.validation.violation-resolved', x: 60, y: 80, width: 230, height: 92, collapsed: false },
                    { nodeId: 'event:created', x: 420, y: 80, width: 230, height: 92, collapsed: false }
                ],
                edges: [{ edgeId: 'binding:current', points: [] }]
            },
            etag: 'diagram-etag'
        });

        render(<EventDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);

        expect((await screen.findAllByText('Created', {}, { timeout:10_000 })).length).toBeGreaterThan(0);
        expect(await screen.findByLabelText('Event designer', {}, { timeout:10_000 })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Event architecture' }));
        expect(await screen.findByLabelText('Event architecture diagram palette', {}, { timeout:10_000 })).toBeInTheDocument();
    }, 20_000);

    it('keeps custom events independent from gamification', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(await screen.findByLabelText('Event designer', {}, { timeout:10_000 })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /gamification/i })).not.toBeInTheDocument();
    }, 15_000);

    it('creates a custom event type and explains its event data at a high level', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        expect(screen.getByText('What is this information for?')).toBeInTheDocument();
        expect(screen.getByText(/orderId can identify what changed/i)).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: '+ Data field' }));
        expect(screen.getByLabelText('Field name')).toHaveValue('value');
    });

    it('creates more than one custom event from the current event form', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        fireEvent.click(screen.getByRole('button', { name: 'Create another event' }));
        expect(screen.getByText('2 event(s) · 0 binding(s)')).toBeInTheDocument();
        expect(screen.getAllByText('NewEvent 2').length).toBeGreaterThan(0);
    });

    it('creates a binding after an event type exists', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        fireEvent.click(screen.getByRole('button', { name: '+ Binding' }));
        expect(screen.getByText('Selected binding')).toBeInTheDocument();
        expect(screen.getByLabelText('Source event')).toBeInTheDocument();
        expect(screen.getByLabelText('Target event')).toBeInTheDocument();
    });

    it('uses the validation-style sidebar and exposes stable identifiers with help', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Event guide' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        expect(screen.getByLabelText('Stable event ID')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Information about Stable event ID' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/bindings, flows, integrations/i);
    });


    it('uses one unified event architecture view for catalog entries and bindings', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Event architecture' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Catalog diagram' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Binding diagram' })).not.toBeInTheDocument();
    });

    it('opens the binding process guide from the left panel', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Binding guide' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define an event binding');
    });

    it('opens with an empty curated canvas and keeps the complete catalog in a grouped palette', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Event architecture' }));
        expect(screen.getByLabelText('Empty event architecture canvas')).toHaveTextContent('Start with an empty architecture');
        expect(screen.getByLabelText('Search event palette')).toBeInTheDocument();
        const validationCategory = await screen.findByText('Validation');
        fireEvent.click(validationCategory.closest('summary') ?? validationCategory);
        expect(await screen.findByRole('button', { name: /Problem resolved/i })).toBeInTheDocument();
        expect(screen.getByText('0 event(s) · 0 binding(s)')).toBeInTheDocument();
    });

    it('renders a catalog event as a real React Flow node when the palette item is clicked', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Event architecture' }));
        const validationCategory = await screen.findByText('Validation');
        fireEvent.click(validationCategory.closest('summary') ?? validationCategory);
        fireEvent.click(await screen.findByRole('button', { name: /Problem resolved/i }));
        expect(await screen.findByLabelText('Source connection point for Problem resolved')).toBeInTheDocument();
        expect(screen.queryByLabelText('Empty event architecture canvas')).not.toBeInTheDocument();
        expect(screen.getByText('1 event(s) · 0 binding(s)')).toBeInTheDocument();
    });

    it('opens event properties from the reusable structure tree', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        const tree = screen.getByLabelText('Event designer structure tree');
        fireEvent.click(within(tree).getByText('NewEvent').closest('button')!);
        expect(screen.getByLabelText('Stable event ID')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Close event structure properties' }));
        expect(screen.getByRole('button', { name: '+ Properties' })).toBeInTheDocument();
    });

    it('exposes visual binding through the shared diagram pointer tools', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Event architecture' }));
        const tools = screen.getByLabelText('Diagram tools');
        fireEvent.click(within(tools).getByRole('button', { name: 'Connect' }));
        expect(screen.getByText('Choose a source connector and then a compatible target connector.')).toBeInTheDocument();
        expect(document.querySelector('.designer-connection-help')).toBeNull();
    });


    it('creates an event binding by using shared pointer tools and node ports', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Event architecture' }));
        fireEvent.click(screen.getByRole('button', { name: /New custom event/i }));
        const validationCategory = await screen.findByText('Validation');
        fireEvent.click(validationCategory.closest('summary') ?? validationCategory);
        fireEvent.click(await screen.findByRole('button', { name: /Problem resolved/i }));
        const tools = screen.getByLabelText('Diagram tools');
        fireEvent.click(within(tools).getByRole('button', { name: 'Connect' }));
        fireEvent.click(await screen.findByLabelText('Source connection point for Problem resolved'));
        fireEvent.click(await screen.findByLabelText('Target connection point for NewEvent'));
        expect(await screen.findByText('2 event(s) · 1 binding(s)', {}, { timeout:10_000 })).toBeInTheDocument();
        expect(await screen.findByText('Selected binding', {}, { timeout:10_000 })).toBeInTheDocument();
    }, 20_000);

    it('creates several custom events from the architecture palette', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Event architecture' }));
        fireEvent.click(screen.getByRole('button', { name: /New custom event/i }));
        fireEvent.click(screen.getByRole('button', { name: /New custom event/i }));
        expect(await screen.findByText('2 event(s) · 0 binding(s)', {}, { timeout:10_000 })).toBeInTheDocument();
        expect(await screen.findByLabelText('Target connection point for NewEvent 2', {}, { timeout:10_000 })).toBeInTheDocument();
    }, 20_000);

    it('shows canonical incoming and outgoing bindings in event properties', () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        fireEvent.click(screen.getByRole('button', { name: '+ Binding' }));
        const customEvent = screen.getAllByText('NewEvent').map(value => value.closest('button')).find(Boolean);
        fireEvent.click(customEvent!);
        expect(screen.getByLabelText(/Visual connections for/)).toHaveTextContent('Incoming');
        expect(screen.getByText(/select to edit the binding/i)).toBeInTheDocument();
    });


    it('splits structure into metamodel, defined elements and properties', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        expect(screen.getByText('Metamodel')).toBeInTheDocument();
        expect(screen.getByText('Defined event structure')).toBeInTheDocument();
        expect(await screen.findByLabelText('Semantic metamodel structure tree')).toHaveTextContent('Person');
        expect(screen.getByLabelText('Event structure properties')).toBeInTheDocument();
    });

    it('shows dependent enumeration properties only for enumeration fields', async () => {
        render(<EventDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Event type' }));
        fireEvent.click(screen.getByRole('button', { name: 'Advanced / manual' }));
        fireEvent.click(screen.getByRole('button', { name: '+ Data field' }));
        expect(screen.queryByLabelText('Enumeration')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Object contract')).not.toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Field type'), { target: { value: 'ENUM' } });
        expect(await screen.findByLabelText('Enumeration')).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Field type'), { target: { value: 'OBJECT' } });
        await waitFor(() => expect(screen.queryByLabelText('Enumeration')).not.toBeInTheDocument(), { timeout:10_000 });
        expect(await screen.findByLabelText('Object contract')).toBeInTheDocument();
    }, 20_000);

});
