import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import { QuickFixDesigner } from './QuickFixDesigner';
import { createDefaultAction, createDefaultOperation, createEmptyActionModel, createEmptyExpressionModel } from './validationDesignerFactory';

describe('quick-fix form guidance', () => {
    it('shows the process guide and identifies required fields', () => {
        const metamodel = canonicalMetaModelFixture();
        const expressions = createEmptyExpressionModel(metamodel.id);
        const action = { ...createDefaultAction(), operations: [createDefaultOperation('SET_ATTRIBUTE', metamodel)] };
        const actions = { ...createEmptyActionModel(metamodel.id, expressions.id), actions: [action] };

        render(
            <QuickFixDesigner
                metamodel={metamodel}
                actionModel={actions}
                expressionModel={expressions}
                selectedActionId={action.id}
                onChange={vi.fn()}
                onSelectAction={vi.fn()}
            />
        );

        expect(screen.getByRole('button', { name: 'Information about Name' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Information about Transaction' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Information about Attribute' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Information about Value source' })).toBeInTheDocument();
        expect(screen.getAllByLabelText('Required field').length).toBeGreaterThanOrEqual(6);

        fireEvent.click(screen.getByRole('button', { name: 'Quick-fix guide' }));
        expect(screen.getByRole('dialog', { name: 'How to create a quick fix' })).toBeInTheDocument();
        expect(screen.getByText('5. Add and order operations')).toBeInTheDocument();
    });

    it('focuses the quick-fix name when its information button is used', () => {
        const metamodel = canonicalMetaModelFixture();
        const expressions = createEmptyExpressionModel(metamodel.id);
        const action = { ...createDefaultAction(), operations: [createDefaultOperation('DELETE_ELEMENT', metamodel)] };
        const actions = { ...createEmptyActionModel(metamodel.id, expressions.id), actions: [action] };

        render(
            <QuickFixDesigner
                metamodel={metamodel}
                actionModel={actions}
                expressionModel={expressions}
                selectedActionId={action.id}
                onChange={vi.fn()}
                onSelectAction={vi.fn()}
            />
        );

        fireEvent.click(screen.getByRole('button', { name: 'Information about Name' }));
        expect(screen.getByLabelText('Name')).toHaveFocus();
        expect(screen.getByRole('tooltip')).toHaveTextContent('Human-readable name');
    });

    it('places the guide and create button after the Quick fixes title and explains Enabled', () => {
        const metamodel = canonicalMetaModelFixture();
        const expressions = createEmptyExpressionModel(metamodel.id);
        const action = { ...createDefaultAction(), operations: [createDefaultOperation('DELETE_ELEMENT', metamodel)] };
        const actions = { ...createEmptyActionModel(metamodel.id, expressions.id), actions: [action] };

        render(
            <QuickFixDesigner
                metamodel={metamodel}
                actionModel={actions}
                expressionModel={expressions}
                selectedActionId={action.id}
                onChange={vi.fn()}
                onSelectAction={vi.fn()}
            />
        );

        const title = screen.getByRole('heading', { name: 'Quick fixes' });
        const heading = title.closest('.validation-panel-heading');
        const headingActions = heading?.querySelector('.validation-panel-heading-actions');
        expect(title.parentElement?.nextElementSibling).toBe(headingActions);
        expect(headingActions).toContainElement(screen.getByRole('button', { name: 'Quick-fix guide' }));
        expect(headingActions).toContainElement(screen.getByRole('button', { name: '+ Quick fix' }));

        fireEvent.click(screen.getByRole('button', { name: 'Information about Enabled' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/should not be executed by the runtime/i);
    });

});
