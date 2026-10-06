import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HelpedField, ProcessGuideButton, RequiredFieldsLegend } from './ValidationFieldHelp';

describe('validation contextual help', () => {
    it('focuses the associated field and shows purpose and example', () => {
        render(
            <HelpedField
                label="Stable problem code"
                targetId="problem-code"
                required
                help="Technical identifier used by diagnostics."
                example="PERSON_AGE_POSITIVE"
            >
                <input id="problem-code" />
            </HelpedField>
        );

        fireEvent.click(screen.getByRole('button', { name: 'Information about Stable problem code' }));

        expect(screen.getByRole('textbox')).toHaveFocus();
        expect(screen.getByRole('tooltip')).toHaveTextContent('Technical identifier used by diagnostics.');
        expect(screen.getByRole('tooltip')).toHaveTextContent('PERSON_AGE_POSITIVE');
        const informationButton = screen.getByRole('button', { name: 'Information about Stable problem code' });
        const labelRow = informationButton.closest('.validation-field-label-row');
        expect(labelRow).not.toBeNull();
        expect(labelRow).toContainElement(screen.getByText('Stable problem code'));
        expect(labelRow).toContainElement(screen.getByLabelText('Required field'));
        expect(labelRow).toContainElement(informationButton);
    });

    it('closes the tooltip with Escape', () => {
        render(
            <HelpedField label="Name" targetId="name" help="Visible name." example="Positive age">
                <input id="name" />
            </HelpedField>
        );
        fireEvent.click(screen.getByRole('button', { name: 'Information about Name' }));
        expect(screen.getByRole('tooltip')).toBeInTheDocument();
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    });

    it('opens the complete rule process guide and closes it with Escape', () => {
        render(<ProcessGuideButton kind="RULE" />);
        fireEvent.click(screen.getByRole('button', { name: 'Rule guide' }));
        expect(screen.getByRole('dialog', { name: 'How to create a validation rule' })).toBeInTheDocument();
        expect(screen.getByText('1. Identify the rule')).toBeInTheDocument();
        expect(screen.getByText(/PERSON_AGE_POSITIVE/)).toBeInTheDocument();
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('identifies the required-field convention', () => {
        render(<RequiredFieldsLegend />);
        expect(screen.getByText(/Required field/)).toBeInTheDocument();
    });
});
