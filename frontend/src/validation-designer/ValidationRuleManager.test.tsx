import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ValidationModel } from '../validation/types';
import { ValidationRuleManager } from './ValidationRuleManager';

const model: ValidationModel = {
    schemaVersion: '1.0', id: 'shop:validations', metamodelId: 'shop', expressionModelId: 'shop:expressions', actionModelId: 'shop:actions', annotations: {}, importedSources: [],
    ruleSets: [{ id: 'general', name: 'General', description: '', enabled: true, order: 0, ruleIds: ['required-name'], annotations: {} }],
    rules: [{ id: 'required-name', name: 'Required name', code: 'REQUIRED_NAME', enabled: true, context: { kind: 'CLASS', classId: 'customer' }, severity: 'ERROR', conditionExpressionId: 'condition', messageTemplate: 'Name is required', triggers: ['ON_SAVE'], dependencies: [], quickFixIds: [], executionMode: 'INCREMENTAL', authoritativeRepresentation: 'CANONICAL', conversionStatus: 'FULL' }]
};

describe('validation rule manager', () => {
    it('selects, filters and duplicates rules', () => {
        const onSelect = vi.fn();
        const onDuplicate = vi.fn();
        render(<ValidationRuleManager model={model} selectedRuleId="" report={null} onChange={vi.fn()} onCreateRule={vi.fn()} onDuplicateRule={onDuplicate} onSelectRule={onSelect} />);
        fireEvent.click(screen.getByText('Required name'));
        expect(onSelect).toHaveBeenCalledWith('required-name');
        fireEvent.change(screen.getByLabelText('Search validations'), { target: { value: 'missing' } });
        expect(screen.queryByText('Required name')).not.toBeInTheDocument();
    });

    it('creates a new rule in a selected group', () => {
        const onCreate = vi.fn();
        render(<ValidationRuleManager model={model} selectedRuleId="required-name" report={null} onChange={vi.fn()} onCreateRule={onCreate} onDuplicateRule={vi.fn()} onSelectRule={vi.fn()} />);
        fireEvent.click(screen.getByLabelText('Add rule to group'));
        expect(onCreate).toHaveBeenCalledWith('general');
    });
    it('opens the rule creation guide before a rule is selected', () => {
        render(<ValidationRuleManager model={{ ...model, rules: [], ruleSets: [] }} selectedRuleId="" report={null} onChange={vi.fn()} onCreateRule={vi.fn()} onDuplicateRule={vi.fn()} onSelectRule={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Rule guide' }));
        expect(screen.getByRole('dialog', { name: 'How to create a validation rule' })).toBeInTheDocument();
    });


    it('keeps the guide and create button in the action row after the Validations title', () => {
        render(<ValidationRuleManager model={model} selectedRuleId="" report={null} onChange={vi.fn()} onCreateRule={vi.fn()} onDuplicateRule={vi.fn()} onSelectRule={vi.fn()} />);
        const title = screen.getByRole('heading', { name: 'Validations' });
        const heading = title.closest('.validation-panel-heading');
        const actions = heading?.querySelector('.validation-panel-heading-actions');
        expect(heading).not.toBeNull();
        expect(actions).not.toBeNull();
        expect(title.parentElement?.nextElementSibling).toBe(actions);
        expect(actions).toContainElement(screen.getByRole('button', { name: 'Rule guide' }));
        expect(actions).toContainElement(screen.getByRole('button', { name: '+ Rule' }));
    });

});
