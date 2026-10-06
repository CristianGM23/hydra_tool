import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EventFlowDesigner } from './EventFlowDesigner';

vi.mock('./api', () => ({
    loadEventFlows: vi.fn(), createEventFlows: vi.fn(), replaceEventFlows: vi.fn(), checkEventFlows: vi.fn(),
    loadEventFlowDiagram: vi.fn(), saveEventFlowDiagram: vi.fn(), startEventFlowRuntime: vi.fn(),
    stopEventFlowRuntime: vi.fn(), loadEventFlowRuntimeStatus: vi.fn()
}));

describe('event flow designer forms', () => {
    it('creates flows from the left sidebar and exposes the stable flow ID', () => {
        render(<EventFlowDesigner metamodelId="people" eventDefinitionModelId="people-events" eventTypeIds={['core.model.saved']} customEventTypes={[]} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Flow' }));
        expect(screen.getByLabelText('Flow ID')).toBeInTheDocument();
        expect(screen.getByLabelText('Name')).toHaveValue('New event flow');
    });

    it('opens the event-flow process guide', () => {
        render(<EventFlowDesigner metamodelId="people" eventDefinitionModelId="people-events" eventTypeIds={[]} customEventTypes={[]} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Event flow guide' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define an event flow');
    });

    it('opens node forms with technical IDs and contextual help', () => {
        render(<EventFlowDesigner metamodelId="people" eventDefinitionModelId="people-events" eventTypeIds={[]} customEventTypes={[]} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Flow' }));
        const startNodeButton = screen.getByRole('button', { name: /Edit Start node/i });
        fireEvent.click(startNodeButton);
        expect(screen.getByLabelText('Node ID')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Information about Node ID' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/transitions, diagrams and runtime plans/i);
    });

    it('shows event triggers with descriptive names and explains each option', () => {
        render(<EventFlowDesigner metamodelId="people" eventDefinitionModelId="people-events" eventTypeIds={['core.model.saved']} customEventTypes={[]} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Flow' }));
        fireEvent.click(screen.getByRole('button', { name: 'Form / advanced' }));
        expect(screen.getByRole('option', { name: 'Model saved' })).toBeInTheDocument();
        expect(screen.queryByRole('option', { name: 'core.model.saved' })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Information about Trigger event types' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/published after a model is stored successfully/i);
    });
    it('opens node properties from the event-flow structure tree', () => {
        render(<EventFlowDesigner metamodelId="people" eventDefinitionModelId="people-events" eventTypeIds={[]} customEventTypes={[]} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Flow' }));
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        const tree = screen.getByLabelText('Event-flow structure tree');
        fireEvent.click(within(tree).getByTitle(/^Start · node:/));
        expect(screen.getByLabelText('Node ID')).toBeInTheDocument();
    });

});
