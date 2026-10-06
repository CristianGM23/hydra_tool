import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ConditionalStyleEditor } from './ConditionalStyleEditor';
import type { NodeMapping } from './types';
import type { MetaModel } from '../metamodel-designer/types';
import { createEmptyExpressionModel } from '../validation-designer/validationDesignerFactory';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'people', name: 'People', annotations: {},
    classes: [{ id: 'Person', name: 'Person', kind: 'CLASS', attributes: [], operations: [], annotations: {} }],
    enums: [], dataTypes: [], relations: []
};

const mapping: NodeMapping = {
    id: 'person-node', targetClassId: 'Person', representationName: 'Person node', shape: 'ROUNDED_RECTANGLE',
    defaultWidth: 160, defaultHeight: 80, minimumWidth: 80, minimumHeight: 40,
    labelExpressionId: '', styles: { fillColor:'#F8FAFC', strokeColor:'#334155' }, annotations: {}, conditionalStyles: []
};

describe('ConditionalStyleEditor', () => {
    it('keeps the representation form style as the unconditional base style', () => {
        render(<ConditionalStyleEditor mapping={mapping} metamodel={metamodel} expressionModel={createEmptyExpressionModel('people')} onMappingChange={vi.fn()} onExpressionModelChange={vi.fn()} />);
        expect(screen.getByLabelText('Base style')).toBeTruthy();
        expect(screen.getByText(/used whenever no conditional rule matches/i)).toBeTruthy();
    });

    it('creates a rule without silently creating a duplicate default condition', () => {
        const onMappingChange = vi.fn();
        const onExpressionModelChange = vi.fn();
        render(<ConditionalStyleEditor mapping={mapping} metamodel={metamodel} expressionModel={createEmptyExpressionModel('people')} onMappingChange={onMappingChange} onExpressionModelChange={onExpressionModelChange} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Conditional style' }));
        expect(onMappingChange.mock.calls[0][0].conditionalStyles).toHaveLength(1);
        expect(onMappingChange.mock.calls[0][0].conditionalStyles[0].conditionExpressionId).toBe('');
        expect(onExpressionModelChange).not.toHaveBeenCalled();
    });

    it('creates the condition explicitly before configuring visual overrides', () => {
        const draft = {...mapping,conditionalStyles:[{id:'style:one',name:'Rule',enabled:true,priority:0,conditionExpressionId:'',styles:{}}]};
        const onMappingChange = vi.fn();
        const onExpressionModelChange = vi.fn();
        render(<ConditionalStyleEditor mapping={draft} metamodel={metamodel} expressionModel={createEmptyExpressionModel('people')} onMappingChange={onMappingChange} onExpressionModelChange={onExpressionModelChange} />);
        fireEvent.click(screen.getByRole('button',{name:'Define condition'}));
        expect(onExpressionModelChange).toHaveBeenCalledOnce();
        expect(onMappingChange.mock.calls[0][0].conditionalStyles[0].conditionExpressionId).not.toBe('');
    });

    it('organizes one style rule into unique-condition and base-style override steps', () => {
        render(<ConditionalStyleEditor mapping={mapping} metamodel={metamodel} expressionModel={createEmptyExpressionModel('people')} onMappingChange={vi.fn()} onExpressionModelChange={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Create first conditional style' })).toBeTruthy();
        expect(screen.getByLabelText('Base style')).toBeTruthy();
    });
    it('explains quick presets and exposes the complete base-label style surface in conditional rules', () => {
        const draft = {...mapping,conditionalStyles:[{id:'style:one',name:'Rule',enabled:true,priority:0,conditionExpressionId:'',styles:{}}]};
        render(<ConditionalStyleEditor mapping={draft} metamodel={metamodel} expressionModel={createEmptyExpressionModel('people')} onMappingChange={vi.fn()} onExpressionModelChange={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Information about quick presets' }));
        expect(screen.getByText(/ready-made visual combinations for common states/i)).toBeTruthy();
        expect(screen.getByText('Label position')).toBeTruthy();
        expect(screen.getByText('Label font size')).toBeTruthy();
        expect(screen.getByText('Label weight')).toBeTruthy();
        expect(screen.getByText('Label padding')).toBeTruthy();
        expect(screen.getByText('Show label')).toBeTruthy();
    });

    it.each(['RECTANGLE','ELLIPSE','CYLINDER','DOCUMENT','CLOUD','STAR','CROSS'] as const)('previews the %s override with a real geometry', (shape: NodeMapping['shape']) => {
        const draft = {...mapping,conditionalStyles:[{id:'style:one',name:'Rule',enabled:true,priority:0,conditionExpressionId:'',styles:{shape}}]};
        const { container } = render(<ConditionalStyleEditor mapping={draft} metamodel={metamodel} expressionModel={createEmptyExpressionModel('people')} onMappingChange={vi.fn()} onExpressionModelChange={vi.fn()} />);
        expect(container.querySelector(`[aria-label="Conditional style preview"] svg[data-shape="${shape}"]`)).toBeTruthy();
    });

});
