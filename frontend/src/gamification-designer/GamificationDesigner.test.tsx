import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GamificationDesigner } from './GamificationDesigner';

vi.mock('../event-designer/api', () => ({
    loadReservedEventTypes: vi.fn().mockResolvedValue(['core.validation.violation-resolved']),
    loadEventModels: vi.fn().mockRejectedValue(new Error('not persisted'))
}));

vi.mock('./api', () => ({
    checkGamification: vi.fn(), createGamification: vi.fn(), loadGamification: vi.fn(), loadGamificationRuntime: vi.fn(),
    replaceGamification: vi.fn(), resetGamificationRuntime: vi.fn(),
    loadGamificationDiagram: vi.fn(), saveGamificationDiagram: vi.fn()
}));


vi.mock('../metamodel-designer/api', () => ({
    loadMetaModel: vi.fn().mockResolvedValue({
        etag: 'm1',
        metamodel: {
            schemaVersion: '2.1', id: 'people', name: 'People', annotations: {},
            classes: [{ id: 'person', name: 'Person', kind: 'CLASS', annotations: {}, attributes: [], operations: [] }],
            enums: [], dataTypes: [], relations: []
        }
    })
}));

describe('gamification designer forms', () => {
    it('creates a point type from the left panel and exposes its technical ID', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Point type' }));
        expect(screen.getByLabelText('Point type ID')).toBeInTheDocument();
        expect(screen.getAllByLabelText('Name').at(-1)).toHaveValue('Quality points');
    });

    it('opens the gamification process guide', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Gamification guide' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define gamification');
        expect(screen.getByRole('dialog')).toHaveTextContent(/verified event evidence/i);
    });

    it('shows contextual help for evidence-rule identifiers', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Evidence rule' }));
        expect(screen.getByLabelText('Rule ID')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Information about Verified event type' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/Do not use a mere UI action/i);
    });
    it('opens point properties from the gamification structure tree', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Point type' }));
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        const tree = screen.getByLabelText('Gamification structure tree');
        fireEvent.click(within(tree).getByText('Quality points').closest('button')!);
        expect(screen.getByLabelText('Point type ID')).toBeInTheDocument();
    });

    it('exposes the canonical connection tool inside the palette', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Diagram' }));
        const palette = screen.getByLabelText('Gamification diagram palette');
        expect(within(palette).getByRole('button', { name: /Connect elements/ })).toBeInTheDocument();
        fireEvent.click(within(palette).getByRole('button', { name: /Connect elements/ }));
        expect(screen.getByText('Choose the source element')).toBeInTheDocument();
    });


    it('places event evidence from the palette so it can be connected to a rule with the arrow tool', async () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Diagram' }));
        const evidencePalette = await screen.findByLabelText('Event evidence palette');
        const evidence = within(evidencePalette).getByRole('button', { name: /Problem resolved/i });
        fireEvent.click(evidence);
        expect(screen.getByText('Verified evidence')).toBeInTheDocument();
        expect(screen.getByLabelText(/Source connection point for Problem resolved/i)).toBeInTheDocument();
        expect(evidence).toBeDisabled();
    });

    it('shows canonical relations from both connected gamification elements', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Point type' }));
        fireEvent.click(screen.getByRole('button', { name: '+ Evidence rule' }));
        const pointItem = screen.getAllByText('Quality points').map(value => value.closest('button')).find(Boolean);
        fireEvent.click(pointItem!);
        expect(screen.getByLabelText(/Visual connections for/)).toHaveTextContent('Incoming');
        expect(screen.getByText(/select to inspect or remove the relation/i)).toBeInTheDocument();
    });


    it('splits structure into semantic, defined and properties panes', async () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        expect(screen.getByText('Metamodel')).toBeInTheDocument();
        expect(screen.getByText('Defined gamification structure')).toBeInTheDocument();
        expect(await screen.findByLabelText('Semantic metamodel structure tree')).toHaveTextContent('Person');
        expect(screen.getByLabelText('Gamification structure properties')).toBeInTheDocument();
    });

    it('reveals rule fields only when their controlling values require them', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Point type' }));
        fireEvent.click(screen.getByRole('button', { name: '+ Evidence rule' }));
        expect(screen.queryByLabelText('Operator')).not.toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Condition path'), { target: { value: 'payload.severity' } });
        expect(screen.getByLabelText('Operator')).toBeInTheDocument();
        expect(screen.queryByLabelText('Cooldown seconds')).not.toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Repeat policy'), { target: { value: 'COOLDOWN' } });
        expect(screen.getByLabelText('Cooldown seconds')).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Effect'), { target: { value: 'PENALTY' } });
        expect(screen.queryByLabelText('Achievement')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Challenge')).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Reward')).not.toBeInTheDocument();
    });


    it('explains palette dependencies and blocks level creation until a point type exists', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        const levelButton = screen.getByRole('button', { name: '+ Level' });
        expect(levelButton).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Information about Level' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/Requires at least one point type/i);
        expect(screen.getByRole('tooltip')).toHaveTextContent(/reward is optional/i);
        expect(screen.getByRole('tooltip')).toHaveTextContent(/Blocked: create at least one point type first/i);
        fireEvent.click(screen.getByRole('button', { name: '+ Point type' }));
        expect(screen.getByRole('button', { name: '+ Level' })).toBeEnabled();
    });

    it('explains evidence-rule runtime requirements from the palette', () => {
        render(<GamificationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Information about Evidence rule' }));
        const tooltip = screen.getByRole('tooltip');
        expect(tooltip).toHaveTextContent(/known verified event/i);
        expect(tooltip).toHaveTextContent(/custom events must first be created and saved in Event Designer/i);
        expect(tooltip).toHaveTextContent(/Penalty rules can only subtract points/i);
        expect(tooltip).toHaveTextContent(/anti-farming/i);
    });

});
