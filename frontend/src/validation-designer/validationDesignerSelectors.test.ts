import { describe, expect, it } from 'vitest';
import { inferExpressionDependencies } from './validationDesignerSelectors';
import type { ExpressionDefinition } from '../validation/types';

// Regresión del fallo hallado al reactivar la E2E real «una condición construida con los 13 tipos de nodo…»: el nodo MODEL producía la
// dependencia { kind: 'MODEL', elementId: '' } y el backend (ValidationDependency → StableIdentifierPolicy) rechazaba el guardado con 400.

// Mismo patrón que StableIdentifierPolicy.ELEMENT_ID del backend.
const STABLE_ELEMENT_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,239}$/;

function expressionWith(root: unknown): ExpressionDefinition {
    return { id: 'condition', name: 'Condition', contextClassId: 'person', root } as unknown as ExpressionDefinition;
}

describe('inferExpressionDependencies', () => {
    it('la dependencia del modelo usa el id estable «model» que espera el backend', () => {
        const dependencies = inferExpressionDependencies(expressionWith({ kind: 'UNARY', operator: 'IS_NOT_NULL', operand: { kind: 'MODEL' } }));
        expect(dependencies).toContainEqual({ kind: 'MODEL', elementId: 'model' });
        for (const dependency of dependencies) expect(dependency.elementId).toMatch(STABLE_ELEMENT_ID);
    });

    it('el resto de dependencias se conservan y no se duplican', () => {
        const dependencies = inferExpressionDependencies(expressionWith({
            kind: 'BINARY', operator: 'AND',
            left: { kind: 'ATTRIBUTE_ACCESS', attributeId: 'person-age', target: { kind: 'SELF' } },
            right: { kind: 'RELATION_NAVIGATION', relationId: 'employees', target: { kind: 'CLASS_INSTANCES', classId: 'company' } },
        }));
        expect(dependencies).toEqual([
            { kind: 'CLASS', elementId: 'person' },
            { kind: 'ATTRIBUTE', elementId: 'person-age' },
            { kind: 'RELATION', elementId: 'employees' },
            { kind: 'CLASS', elementId: 'company' },
        ]);
    });
});
