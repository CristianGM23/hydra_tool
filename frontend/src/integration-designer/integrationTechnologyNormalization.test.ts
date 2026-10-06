import { describe, expect, it } from 'vitest';
import { normalizeIntegrationTechnologyDescriptor, normalizeIntegrationTechnologyDescriptors } from './integrationTechnologyNormalization';

describe('integration technology catalogue normalization', () => {
    it('keeps server recommendations when they are supported', () => {
        const descriptor = normalizeIntegrationTechnologyDescriptor({ technology:'SIRIUS_DESKTOP', displayName:'Sirius', targetKind:'GRAPHICAL_MODELING_EDITOR',
            recommendedDeliveryMode:'GENERATED_MODULE', recommendedInjectionStrategy:'ADD_PLUGIN', recommendedBuildSystem:'TYCHO',
            deliveryModes:['GENERATED_MODULE','EMBEDDED'], injectionStrategies:['ADD_PLUGIN','ADD_MODULE'], locatorStrategies:['QUALIFIED_NAME'], capabilities:{}, notes:[] });
        expect(descriptor.recommendedInjectionStrategy).toBe('ADD_PLUGIN');
        expect(descriptor.recommendedBuildSystem).toBe('TYCHO');
    });

    it('degrades sparse older metadata to declared safe choices without technology branches', () => {
        const descriptor = normalizeIntegrationTechnologyDescriptor({ technology:'MXGRAPH_DRAWIO', displayName:'mxGraph / draw.io', targetKind:'GRAPHICAL_MODELING_EDITOR',
            deliveryModes:['HYBRID','GENERATED_MODULE'], injectionStrategies:['GENERATE_SIDECAR','ADD_PACKAGE'], locatorStrategies:['QUALIFIED_NAME'] });
        expect(descriptor.recommendedDeliveryMode).toBe('GENERATED_MODULE');
        expect(descriptor.recommendedInjectionStrategy).toBe('ADD_PACKAGE');
        expect(descriptor.recommendedBuildSystem).toBe('NONE');
        expect(descriptor.notes.join(' ')).toMatch(/recommendations were not provided/i);
    });

    it('rejects unusable catalogue entries with a deterministic error instead of exposing undefined values', () => {
        expect(() => normalizeIntegrationTechnologyDescriptor({ technology:'MXGRAPH_DRAWIO', targetKind:'GRAPHICAL_MODELING_EDITOR', deliveryModes:[], injectionStrategies:[] }))
            .toThrow(/does not declare any delivery mode/i);
    });

    it('deduplicates repeated technologies while preserving server order', () => {
        const value = { technology:'PLANTUML', displayName:'PlantUML', targetKind:'DIAGRAM_PROJECTION', recommendedDeliveryMode:'PROJECTION_ONLY',
            recommendedInjectionStrategy:'GENERATE_PROJECTION', recommendedBuildSystem:'NONE', deliveryModes:['PROJECTION_ONLY'],
            injectionStrategies:['GENERATE_PROJECTION'], locatorStrategies:['PLANTUML_ALIAS'], capabilities:{}, notes:[] };
        expect(normalizeIntegrationTechnologyDescriptors([value, value])).toHaveLength(1);
    });
    it('recognizes ANTLR as a declared textual-language target instead of dropping it at the HTTP boundary', () => {
        const descriptor = normalizeIntegrationTechnologyDescriptor({ technology:'ANTLR', displayName:'ANTLR', targetKind:'TEXTUAL_LANGUAGE_TOOLING',
            recommendedDeliveryMode:'GENERATED_MODULE', recommendedInjectionStrategy:'ADD_MODULE', recommendedBuildSystem:'MAVEN',
            deliveryModes:['GENERATED_MODULE'], injectionStrategies:['ADD_MODULE'], locatorStrategies:['AST_PATH','QUALIFIED_NAME'], capabilities:{PROJECT_EXPORT:'FULL'}, notes:[] });
        expect(descriptor.technology).toBe('ANTLR');
        expect(descriptor.targetKind).toBe('TEXTUAL_LANGUAGE_TOOLING');
        expect(descriptor.recommendedBuildSystem).toBe('MAVEN');
    });

});
