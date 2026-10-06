import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import { QuickFixDesigner } from './QuickFixDesigner';
import { createEmptyActionModel, createEmptyExpressionModel } from './validationDesignerFactory';

describe('visual quick-fix designer', () => {
    it('creates a quick fix and adds a declarative operation', () => {
        const onChange = vi.fn();
        const onSelect = vi.fn();
        const expressions = createEmptyExpressionModel('shop');
        const actions = createEmptyActionModel('shop', expressions.id);
        const { rerender } = render(<QuickFixDesigner metamodel={canonicalMetaModelFixture()} actionModel={actions} expressionModel={expressions} selectedActionId="" onChange={onChange} onSelectAction={onSelect} />);
        fireEvent.click(screen.getByText('+ Quick fix'));
        const updatedActions = onChange.mock.calls.at(-1)?.[0];
        const actionId = updatedActions.actions[0].id;
        rerender(<QuickFixDesigner metamodel={canonicalMetaModelFixture()} actionModel={updatedActions} expressionModel={expressions} selectedActionId={actionId} onChange={onChange} onSelectAction={onSelect} />);
        fireEvent.change(screen.getByLabelText('Operation type'), { target: { value: 'SET_ATTRIBUTE' } });
        expect(onChange.mock.calls.at(-1)?.[0].actions[0].operations[0].kind).toBe('SET_ATTRIBUTE');
    });
});
