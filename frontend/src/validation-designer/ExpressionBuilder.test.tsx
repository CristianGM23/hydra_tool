import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import { ExpressionBuilder } from './ExpressionBuilder';
import { createDefaultExpression } from './validationDesignerFactory';

describe('visual expression builder', () => {
    it('changes a condition from a literal to an attribute comparison', () => {
        const onChange = vi.fn();
        const expression = createDefaultExpression('customer');
        const { rerender } = render(<ExpressionBuilder expression={expression} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: 'Advanced' }));
        fireEvent.change(screen.getByLabelText('Expression element'), { target: { value: 'ATTRIBUTE_ACCESS' } });
        const updated = onChange.mock.calls.at(-1)?.[0];
        expect(updated.root.kind).toBe('ATTRIBUTE_ACCESS');
        rerender(<ExpressionBuilder expression={updated} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: 'Advanced' }));
        fireEvent.change(screen.getByDisplayValue('Customer.name · Text'), { target: { value: 'customer-status' } });
        expect(onChange.mock.calls.at(-1)?.[0].root.attributeId).toBe('customer-status');
    });

    it('adds a nested collection predicate when EXISTS is selected', () => {
        const onChange = vi.fn();
        const expression = { ...createDefaultExpression('customer'), root: { kind: 'COLLECTION' as const, nodeId: 'collection', operator: 'COUNT' as const, source: { kind: 'CLASS_INSTANCES' as const, nodeId: 'instances', classId: 'customer' } } };
        render(<ExpressionBuilder expression={expression} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: 'Advanced' }));
        fireEvent.change(screen.getByDisplayValue('Count'), { target: { value: 'EXISTS' } });
        const root = onChange.mock.calls.at(-1)?.[0].root;
        expect(root.operator).toBe('EXISTS');
        expect(root.expression.kind).toBe('LITERAL');
    });

    it('builds a common attribute condition through the guided flow', () => {
        const onChange = vi.fn();
        const expression = createDefaultExpression('customer');
        render(<ExpressionBuilder expression={expression} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: /Check a field value/i }));
        const root = onChange.mock.calls.at(-1)?.[0].root;
        expect(root.kind).toBe('BINARY');
        expect(root.left.kind).toBe('ATTRIBUTE_ACCESS');
        expect(root.right.kind).toBe('LITERAL');
    });

    it('loads only the selected context class attributes and shows their types in the dropdown', () => {
        const onChange = vi.fn();
        const metamodel = canonicalMetaModelFixture();
        metamodel.classes = metamodel.classes.map(item => item.id === 'order' ? { ...item, attributes: [{ id:'order-total', name:'total', type:{ kind:'PRIMITIVE' as const, targetId:'INTEGER', externalName:'' }, multiplicity:{ lowerBound:1, upperBound:1 }, ordered:false, unique:true, defaultValue:'0', annotations:{} }] } : item);
        const { rerender } = render(<ExpressionBuilder expression={createDefaultExpression('customer')} metamodel={metamodel} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: /Check a field value/i }));
        const customerAttributes = screen.getByLabelText('Condition 1 attribute') as HTMLSelectElement;
        expect(Array.from(customerAttributes.options).map(option => option.text)).toEqual(expect.arrayContaining(['name · Text', 'status · Status']));
        rerender(<ExpressionBuilder expression={createDefaultExpression('order')} metamodel={metamodel} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: /Check a field value/i }));
        const orderAttributes = screen.getByLabelText('Condition 1 attribute') as HTMLSelectElement;
        const labels = Array.from(orderAttributes.options).map(option => option.text);
        expect(labels).toContain('total · Integer');
        expect(labels).not.toContain('name · Text');
    });

    it('combines guided conditions without exposing the expression tree', () => {
        const onChange = vi.fn();
        const first = { ...createDefaultExpression('customer'), root: { kind: 'BINARY' as const, nodeId: 'rule', operator: 'EQUALS' as const, left: { kind: 'ATTRIBUTE_ACCESS' as const, nodeId: 'access', target: { kind: 'SELF' as const, nodeId: 'self' }, attributeId: 'customer-name' }, right: { kind: 'LITERAL' as const, nodeId: 'literal', valueType: { kind: 'STRING' as const }, value: '' } } };
        render(<ExpressionBuilder expression={first} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Field value' }));
        expect(onChange.mock.calls.at(-1)?.[0].root.operator).toBe('AND');
        expect(screen.queryByLabelText('Expression element')).not.toBeInTheDocument();
    });

    it('preserves an advanced expression until the user explicitly chooses to replace it', () => {
        const onChange = vi.fn();
        const expression = {
            ...createDefaultExpression('customer'),
            root: { kind: 'FUNCTION' as const, nodeId: 'advanced-function', functionName: 'coalesce', arguments: [] }
        };
        render(<ExpressionBuilder expression={expression} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        expect(screen.getByText('This condition uses advanced expression elements.')).toBeInTheDocument();
        expect(onChange).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Advanced' }));
        expect(screen.getByLabelText('Expression element')).toHaveValue('FUNCTION');
    });

    it('only offers relations navigable from the validation context', () => {
        const onChange = vi.fn();
        const expression = createDefaultExpression('customer');
        render(<ExpressionBuilder expression={expression} metamodel={canonicalMetaModelFixture()} onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: /Check a relation/i }));
        const relation = screen.getByLabelText('Condition 1 relation') as HTMLSelectElement;
        const optionValues = Array.from(relation.options).map(option => option.value);
        expect(optionValues).toContain('customer-orders');
        expect(optionValues).not.toContain('order-customer');
        expect(optionValues).not.toContain('customer-named');
    });

});
