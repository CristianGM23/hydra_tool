import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import { HydraLanguageProvider } from '../i18n/HydraLanguageContext';
import { ValidationRuleEditor } from './ValidationRuleEditor';
import {
    createDefaultExpression,
    createDefaultValidationRule,
    createEmptyActionModel,
    createEmptyExpressionModel,
    createEmptyValidationModel
} from './validationDesignerFactory';

describe('validation rule form guidance', () => {
    it('shows information buttons, required markers and the rule guide', () => {
        const metamodel = canonicalMetaModelFixture();
        const expression = createDefaultExpression(metamodel.classes[0]?.id);
        const rule = createDefaultValidationRule(metamodel, expression);
        const expressions = createEmptyExpressionModel(metamodel.id);
        const actions = createEmptyActionModel(metamodel.id, expressions.id);
        const validations = {
            ...createEmptyValidationModel(metamodel.id, expressions.id, actions.id),
            rules: [rule]
        };

        render(
            <ValidationRuleEditor
                metamodel={metamodel}
                validationModel={validations}
                actionModel={actions}
                rule={rule}
                expression={expression}
                onChangeRule={vi.fn()}
                onChangeExpression={vi.fn()}
                onDelete={vi.fn()}
                onMoveToGroup={vi.fn()}
            />
        );

        fireEvent.click(screen.getByRole('button', { name: 'Full form' }));
        expect(screen.getByRole('button', { name: 'Information about Name' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Information about Stable problem code' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Information about Message shown when the rule fails' })).toBeInTheDocument();
        expect(screen.getAllByLabelText('Required field').length).toBeGreaterThanOrEqual(8);

    });

    it('marks the target feature as required for attribute rules', () => {
        const metamodel = canonicalMetaModelFixture();
        const expression = createDefaultExpression('customer');
        const baseRule = createDefaultValidationRule(metamodel, expression);
        const rule = { ...baseRule, context: { kind: 'ATTRIBUTE' as const, classId: 'customer', featureId: 'customer-name' } };
        const expressions = createEmptyExpressionModel(metamodel.id);
        const actions = createEmptyActionModel(metamodel.id, expressions.id);
        const validations = { ...createEmptyValidationModel(metamodel.id, expressions.id, actions.id), rules: [rule] };

        render(
            <ValidationRuleEditor
                metamodel={metamodel}
                validationModel={validations}
                actionModel={actions}
                rule={rule}
                expression={expression}
                onChangeRule={vi.fn()}
                onChangeExpression={vi.fn()}
                onDelete={vi.fn()}
                onMoveToGroup={vi.fn()}
            />
        );

        fireEvent.click(screen.getByRole('button', { name: 'Full form' }));
        expect(screen.getByLabelText('Target feature')).toBeRequired();
        expect(screen.getByRole('button', { name: 'Information about Target feature' })).toBeInTheDocument();
    });

    it('explains the validation scope, grouping, activation and associated repairs in detail', () => {
        const metamodel = canonicalMetaModelFixture();
        const expression = createDefaultExpression(metamodel.classes[0]?.id);
        const rule = createDefaultValidationRule(metamodel, expression);
        const expressions = createEmptyExpressionModel(metamodel.id);
        const actions = createEmptyActionModel(metamodel.id, expressions.id);
        const validations = {
            ...createEmptyValidationModel(metamodel.id, expressions.id, actions.id),
            rules: [rule]
        };

        render(
            <ValidationRuleEditor
                metamodel={metamodel}
                validationModel={validations}
                actionModel={actions}
                rule={rule}
                expression={expression}
                onChangeRule={vi.fn()}
                onChangeExpression={vi.fn()}
                onDelete={vi.fn()}
                onMoveToGroup={vi.fn()}
            />
        );

        fireEvent.click(screen.getByRole('button', { name: 'Full form' }));
        const checks: Array<[string, RegExp]> = [
            ['Enabled', /does not produce diagnostics/i],
            ['Group', /enable or disable the contained rules as a set/i],
            ['Context', /what part of the model this rule is about/i],
            ['Context class', /show the attributes and relations of that type/i],
            ['Available quick fixes', /not executed automatically/i]
        ];

        for (const [label, expectedText] of checks) {
            fireEvent.click(screen.getByRole('button', { name: `Information about ${label}` }));
            expect(screen.getByRole('tooltip')).toHaveTextContent(expectedText);
            fireEvent.keyDown(document, { key: 'Escape' });
        }
    });


    it('shows the step 5 and step 6 checklist details completely in Spanish', () => {
        window.localStorage.setItem('hydra.language.v1', 'es');
        const metamodel = canonicalMetaModelFixture();
        const expression = createDefaultExpression(metamodel.classes[0]?.id);
        const rule = createDefaultValidationRule(metamodel, expression);
        const expressions = createEmptyExpressionModel(metamodel.id);
        const actions = createEmptyActionModel(metamodel.id, expressions.id);
        const validations = { ...createEmptyValidationModel(metamodel.id, expressions.id, actions.id), rules: [rule] };

        render(
            <HydraLanguageProvider>
                <ValidationRuleEditor
                    metamodel={metamodel}
                    validationModel={validations}
                    actionModel={actions}
                    rule={rule}
                    expression={expression}
                    onChangeRule={vi.fn()}
                    onChangeExpression={vi.fn()}
                    onDelete={vi.fn()}
                    onMoveToGroup={vi.fn()}
                    allDefinitionsReady
                />
            </HydraLanguageProvider>
        );

        for (let step = 0; step < 4; step += 1) fireEvent.click(screen.getByRole('button', { name: 'Paso siguiente' }));
        expect(screen.getByText('No hay ninguna reparación asociada; es un estado válido.')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Paso siguiente' }));
        expect(screen.getByText('El nombre, el código y el mensaje de fallo son obligatorios.')).toBeInTheDocument();
        expect(screen.getByText('El runtime dispone de la información necesaria para decidir dónde y cuándo ejecutar la validación.')).toBeInTheDocument();
        expect(screen.getByText('No se permiten asociaciones rotas con correcciones rápidas.')).toBeInTheDocument();
        expect(screen.getByText('Ninguna otra regla, expresión o corrección rápida puede contener un error bloqueante.')).toBeInTheDocument();
        expect(screen.queryByText('Name, code and failure message are mandatory.')).not.toBeInTheDocument();
        expect(screen.queryByText('The runtime has enough information to decide where and when to execute.')).not.toBeInTheDocument();
        window.localStorage.removeItem('hydra.language.v1');
    });

});
