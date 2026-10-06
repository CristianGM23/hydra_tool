import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import { ValidationApiError } from '../validation/http';
import type { ActionModel, ExpressionModel, ValidationModel } from '../validation/types';
import { ValidationDesigner } from './ValidationDesigner';

const mocks = vi.hoisted(() => ({
    loadExpressions: vi.fn(), createExpressions: vi.fn(), replaceExpressions: vi.fn(), checkExpressions: vi.fn(),
    loadActions: vi.fn(), createActions: vi.fn(), replaceActions: vi.fn(), checkActions: vi.fn(),
    loadValidations: vi.fn(), createValidations: vi.fn(), replaceValidations: vi.fn(), checkValidations: vi.fn()
}));

vi.mock('../validation/api', () => ({
    loadExpressionModel: mocks.loadExpressions,
    createExpressionModel: mocks.createExpressions,
    replaceExpressionModel: mocks.replaceExpressions,
    checkExpressionModel: mocks.checkExpressions
}));
vi.mock('../validation/actionApi', () => ({
    loadActionModel: mocks.loadActions,
    createActionModel: mocks.createActions,
    replaceActionModel: mocks.replaceActions,
    checkActionModel: mocks.checkActions
}));
vi.mock('../validation/validationApi', () => ({
    loadValidationModel: mocks.loadValidations,
    createValidationModel: mocks.createValidations,
    replaceValidationModel: mocks.replaceValidations,
    checkValidationModel: mocks.checkValidations
}));

beforeEach(() => {
    Object.values(mocks).forEach(mock => (mock as ReturnType<typeof vi.fn>).mockReset());
    mocks.loadExpressions.mockRejectedValue(new ValidationApiError(404, 'Missing expressions'));
    mocks.loadActions.mockRejectedValue(new ValidationApiError(404, 'Missing actions'));
    mocks.loadValidations.mockRejectedValue(new ValidationApiError(404, 'Missing validations'));
    mocks.createExpressions.mockImplementation(async (model: ExpressionModel) => ({ expressionModel: model, checkReport: { expressionModelId: model.id, diagnostics: [], expressions: [] }, etag: 'expression-etag' }));
    mocks.createActions.mockImplementation(async (model: ActionModel) => ({ actionModel: model, checkReport: { actionModelId: model.id, diagnostics: [], actions: [] }, etag: 'action-etag' }));
    mocks.createValidations.mockImplementation(async (model: ValidationModel) => ({ validationModel: model, checkReport: { validationModelId: model.id, diagnostics: [], rules: [] }, etag: 'validation-etag' }));
});

describe('integrated validation designer', () => {
    it('creates a rule, designs a quick fix and saves the three canonical documents in dependency order', async () => {
        const onMessage = vi.fn();
        render(<ValidationDesigner metamodel={canonicalMetaModelFixture()} persisted onError={vi.fn()} onMessage={onMessage} />);
        await waitFor(() => expect(mocks.loadValidations).toHaveBeenCalledWith('shop'));

        fireEvent.click(screen.getByText('+ Rule'));
        expect((await screen.findAllByText('New validation')).length).toBeGreaterThan(0);
        fireEvent.change(screen.getByLabelText('Stable problem code'), { target: { value: 'customer name' } });
        expect(screen.getByDisplayValue('CUSTOMER_NAME')).toBeInTheDocument();

        fireEvent.click(screen.getByText('Quick fixes'));
        fireEvent.click(screen.getByText('+ Quick fix'));
        expect((await screen.findAllByText('New quick fix')).length).toBeGreaterThan(0);
        fireEvent.click(screen.getByRole('button', { name: 'Full editor' }));
        fireEvent.change(screen.getByLabelText('Operation type'), { target: { value: 'TRANSFORM_TEXT' } });
        expect(screen.getByText('1. Transform text')).toBeInTheDocument();

        fireEvent.click(screen.getByText('Rules'));
        expect(screen.getByText('New quick fix')).toBeInTheDocument();
        fireEvent.click(screen.getByText('New quick fix'));
        fireEvent.click(screen.getByText('Save validations'));

        await waitFor(() => expect(mocks.createValidations).toHaveBeenCalledTimes(1));
        expect(mocks.createExpressions.mock.invocationCallOrder[0]).toBeLessThan(mocks.createActions.mock.invocationCallOrder[0]);
        expect(mocks.createActions.mock.invocationCallOrder[0]).toBeLessThan(mocks.createValidations.mock.invocationCallOrder[0]);
        expect(mocks.createValidations.mock.calls[0][0].rules[0].quickFixIds).toHaveLength(1);
        expect(mocks.createActions.mock.calls[0][0].id).toBe('shop-actions');
        expect(mocks.createValidations.mock.calls[0][0].id).toBe('shop-validations');
        expect(mocks.createActions.mock.calls[0][0].id).not.toContain(':');
        expect(mocks.createValidations.mock.calls[0][0].id).not.toContain(':');
        expect(onMessage).toHaveBeenCalledWith(expect.stringContaining('Validations saved'));
    });

    it('keeps saving disabled until the metamodel itself has been persisted', () => {
        render(<ValidationDesigner metamodel={canonicalMetaModelFixture()} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getAllByText('Save metamodel first').length).toBeGreaterThan(0);
        expect(screen.getByText('Save the metamodel before designing validations')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save validations' })).toBeDisabled();
    });

    it('retains successful stage ETags when a later document fails and retries only the failed stages', async () => {
        const onError = vi.fn();
        mocks.createActions.mockRejectedValueOnce(new Error('Action persistence failed'));
        render(<ValidationDesigner metamodel={canonicalMetaModelFixture()} persisted onError={onError} onMessage={vi.fn()} />);
        await waitFor(() => expect(mocks.loadValidations).toHaveBeenCalledWith('shop'));

        fireEvent.click(screen.getByText('+ Rule'));
        fireEvent.click(screen.getByText('Quick fixes'));
        fireEvent.click(screen.getByText('+ Quick fix'));
        fireEvent.click(screen.getByRole('button', { name: 'Full editor' }));
        fireEvent.change(screen.getByLabelText('Operation type'), { target: { value: 'TRANSFORM_TEXT' } });
        fireEvent.click(screen.getByText('Save validations'));

        await waitFor(() => expect(onError).toHaveBeenCalledWith(expect.stringContaining('Quick fixes')));
        expect(onError).toHaveBeenCalledWith(expect.stringContaining('Action persistence failed'));
        expect(mocks.createExpressions).toHaveBeenCalledTimes(1);
        expect(mocks.createActions).toHaveBeenCalledTimes(1);
        expect(mocks.createValidations).not.toHaveBeenCalled();

        mocks.createActions.mockImplementation(async (model: ActionModel) => ({ actionModel: model, checkReport: { actionModelId: model.id, diagnostics: [], actions: [] }, etag: 'action-etag' }));
        fireEvent.click(screen.getByText('Save validations'));

        await waitFor(() => expect(mocks.createValidations).toHaveBeenCalledTimes(1));
        expect(mocks.createExpressions).toHaveBeenCalledTimes(1);
        expect(mocks.createActions).toHaveBeenCalledTimes(2);
    });

    it('does not reload persisted documents and erase unsaved imports when shell callback identities change', async () => {
        const view = render(<ValidationDesigner metamodel={canonicalMetaModelFixture()} persisted onError={vi.fn()} onMessage={vi.fn()} />);
        await waitFor(() => expect(mocks.loadValidations).toHaveBeenCalledTimes(1));
        fireEvent.click(screen.getByText('+ Rule'));
        expect((await screen.findAllByText('New validation')).length).toBeGreaterThan(0);

        view.rerender(<ValidationDesigner metamodel={canonicalMetaModelFixture()} persisted onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getAllByText('New validation').length).toBeGreaterThan(0);
        expect(mocks.loadValidations).toHaveBeenCalledTimes(1);
    });

    it('opens rule properties from the validation structure tree', async () => {
        render(<ValidationDesigner metamodel={canonicalMetaModelFixture()} persisted onError={vi.fn()} onMessage={vi.fn()} />);
        await waitFor(() => expect(mocks.loadValidations).toHaveBeenCalledWith('shop'));
        fireEvent.click(screen.getByText('+ Rule'));
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        const tree = screen.getByLabelText('Validation structure tree');
        fireEvent.click(within(tree).getByText('New validation').closest('button')!);
        expect(screen.getByLabelText('Stable problem code')).toBeInTheDocument();
    });

});
