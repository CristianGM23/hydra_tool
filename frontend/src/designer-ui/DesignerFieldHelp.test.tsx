import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DesignerGuideButton, DesignerHelpedField, DesignerRequiredFieldsLegend } from './DesignerFieldHelp';

describe('shared HYDRA designer UI', () => {
    it('opens field help, focuses the associated control and shows an example', () => {
        render(<DesignerHelpedField label="Stable ID" targetId="stable-id" help="Used by references." example="event:created" required><input id="stable-id" /></DesignerHelpedField>);
        fireEvent.click(screen.getByRole('button', { name: 'Information about Stable ID' }));
        expect(screen.getByRole('textbox')).toHaveFocus();
        expect(screen.getByRole('tooltip')).toHaveTextContent('Used by references.');
        expect(screen.getByRole('tooltip')).toHaveTextContent('event:created');
        expect(screen.getByRole('tooltip')).toHaveClass('hydra-field-tooltip');
        expect(screen.getByRole('tooltip').parentElement).toBe(document.body);
    });

    it('marks required fields consistently', () => {
        render(<><DesignerRequiredFieldsLegend /><DesignerHelpedField label="Name" targetId="name" help="Visible name." example="Quality" required><input id="name" /></DesignerHelpedField></>);
        expect(screen.getAllByText('*')).toHaveLength(2);
        expect(screen.getByText('Required field')).toBeInTheDocument();
    });


    it('explains every selectable value inside the field tooltip', () => {
        render(<DesignerHelpedField label="Mode" targetId="mode" help="Select a mode." example="Audit" options={[
            { value: 'AUDIT', label: 'Audit', description: 'Stores durable evidence.' },
            { value: 'TRANSIENT', label: 'Transient', description: 'Keeps the event only while it is delivered.' }
        ]}><select id="mode"><option>Audit</option></select></DesignerHelpedField>);
        fireEvent.click(screen.getByRole('button', { name: 'Information about Mode' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent('Available values');
        expect(screen.getByRole('tooltip')).toHaveTextContent('Stores durable evidence.');
        expect(screen.getByRole('tooltip')).toHaveTextContent('Keeps the event only while it is delivered.');
    });

    it('opens and closes a process guide for each designer', () => {
        render(<DesignerGuideButton kind="EVENT" />);
        fireEvent.click(screen.getByRole('button', { name: 'Event guide' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define an event type');
        expect(screen.getByRole('dialog')).toHaveTextContent('1. Identify the event');
        expect(screen.getByRole('dialog')).toHaveClass('hydra-guide-dialog');
        fireEvent.click(screen.getByRole('button', { name: 'Close guide' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('offers an icon-only accessible guide control for narrow inspectors', () => {
        render(<DesignerGuideButton kind="GAMIFICATION" compact />);
        const button = screen.getByRole('button', { name: 'Gamification guide' });
        expect(button).toHaveClass('compact');
        expect(button).toHaveAttribute('title', 'Gamification guide');
        fireEvent.click(button);
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define gamification');
    });

});
