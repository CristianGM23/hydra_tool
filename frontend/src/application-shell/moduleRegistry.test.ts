import { describe, expect, it } from 'vitest';
import { HYDRA_MODULES, moduleDefinition, moduleFromHash, moduleHash, unmetModuleRequirements } from './moduleRegistry';

describe('HYDRA module registry', () => {
    it('exposes independent entry points for every portable capability', () => {
        expect(HYDRA_MODULES.map(module => module.id)).toEqual(expect.arrayContaining([
            'HOME', 'MODEL', 'METAMODEL', 'VALIDATIONS', 'QUICK_FIXES', 'EVENTS', 'EVENT_FLOWS',
            'GAMIFICATION', 'EDITORS', 'INTEGRATIONS', 'CAPABILITIES',
            'COMPATIBILITY', 'ROUND_TRIP', 'DISTRIBUTION', 'GENERATED_PROJECTS',
            'ARTIFACTS', 'REPRESENTABLE_IMPORTS', 'PUBLICATION'
        ]));
        expect(moduleDefinition('EVENTS')).toMatchObject({ requiresMetamodel: true, category: 'DSL' });
        expect(moduleDefinition('COMPATIBILITY')).toMatchObject({ requiresMetamodel: false, category: 'OPERATIONS' });
        expect(HYDRA_MODULES.filter(module => module.category === 'DSL').map(module => module.id)).toEqual(['METAMODEL','GRAMMARS','EDITORS','VALIDATIONS','QUICK_FIXES','EVENTS','EVENT_FLOWS','GAMIFICATION','TRANSFORMATIONS']);
        expect(moduleDefinition('TRANSFORMATIONS')).toMatchObject({ requiresMetamodel: false, category: 'DSL' });
        expect(moduleDefinition('REPOSITORIES')).toMatchObject({ requiresMetamodel: false, category: 'USE' });
    });

    it('round-trips stable deep-link hashes and falls back to Home', () => {
        for (const module of HYDRA_MODULES) expect(moduleFromHash(moduleHash(module.id))).toBe(module.id);
        expect(moduleFromHash('#/event-flows')).toBe('EVENT_FLOWS');
        expect(moduleFromHash('#/unknown-module')).toBe('HOME');
    });

    it('uses capability requirements for grammar-aware modules without removing legacy metamodel compatibility', () => {
        expect(moduleDefinition('GRAMMARS').requirements).toEqual({});
        expect(moduleDefinition('TEXTUAL_MODELS').requirements).toEqual({ allOf: ['SEMANTIC_CONTRACT', 'COMPILED_GRAMMAR'] });
        expect(moduleDefinition('VALIDATIONS').requirements).toEqual({ allOf: ['SEMANTIC_CONTRACT'] });
        expect(unmetModuleRequirements(moduleDefinition('TEXTUAL_MODELS'), { available: new Set(['SEMANTIC_CONTRACT']) })).toEqual(['COMPILED_GRAMMAR']);
        expect(unmetModuleRequirements(moduleDefinition('TEXTUAL_MODELS'), { available: new Set(['SEMANTIC_CONTRACT', 'COMPILED_GRAMMAR']) })).toEqual([]);
    });
});
