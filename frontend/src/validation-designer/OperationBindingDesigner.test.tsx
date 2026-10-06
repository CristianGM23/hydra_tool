import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import type { MetaOperation } from '../metamodel-designer/types';
import { createEmptyActionModel, createEmptyExpressionModel } from './validationDesignerFactory';
import { OperationBindingDesigner } from './OperationBindingDesigner';

function operation(id: string, behaviorKind: MetaOperation['behaviorKind']): MetaOperation {
    return { id, name: id.split(':').at(-1) ?? id, returnType: behaviorKind === 'COMMAND' ? null : { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, returnMultiplicity: behaviorKind === 'COMMAND' ? null : { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, parameters: [], exceptionTypes: [], behaviorKind, annotations: {} };
}

describe('operation binding designer', () => {
    it('keeps query and command implementations separated and stores runtime surfaces', () => {
        const metamodel = canonicalMetaModelFixture();
        metamodel.classes[1] = { ...metamodel.classes[1], operations: [operation('operation:Customer:label', 'QUERY'), operation('operation:Customer:normalize', 'COMMAND')] };
        const expressions = createEmptyExpressionModel('shop');
        expressions.expressions.push({ id: 'expression:label', name: 'Label', contextClassId: 'customer', root: { kind: 'LITERAL', nodeId: 'node:label', valueType: { kind: 'STRING' }, value: 'Customer' }, annotations: {} });
        const actions = createEmptyActionModel('shop', expressions.id);
        actions.actions.push({ id: 'action:normalize', name: 'Normalize', enabled: true, parameters: [], operations: [{ kind: 'DELETE_ELEMENT', operationId: 'operation:delete', target: { kind: 'SELF' } }], transactionPolicy: 'ATOMIC', undoPolicy: 'REQUIRED', annotations: {} });
        const onChange = vi.fn();
        const { rerender } = render(<OperationBindingDesigner metamodel={metamodel} expressionModel={expressions} actionModel={actions} behaviorModel={{ schemaVersion: '1.0', metamodelId: 'shop', bindings: [], annotations: {} }} onChange={onChange} />);
        fireEvent.change(screen.getAllByLabelText('Implementation')[0], { target: { value: 'EXPRESSION' } });
        const queryModel = onChange.mock.calls.at(-1)?.[0];
        expect(queryModel.bindings[0].implementationKind).toBe('EXPRESSION');
        rerender(<OperationBindingDesigner metamodel={metamodel} expressionModel={expressions} actionModel={actions} behaviorModel={queryModel} onChange={onChange} />);
        fireEvent.click(screen.getAllByLabelText('button')[0]);
        expect(onChange.mock.calls.at(-1)?.[0].bindings[0].annotations['ui.surfaces']).toContain('BUTTON');
        expect(screen.getAllByLabelText('Implementation')[1]).not.toContainHTML('EXPRESSION');
    });
});
