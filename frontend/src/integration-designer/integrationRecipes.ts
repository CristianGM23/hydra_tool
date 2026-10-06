import type { InjectionStrategy, IntegrationDeliveryMode } from './types';

export type IntegrationRecipeId = 'ECLIPSE_MODELING_DISTRIBUTION' | 'VSCODE_EXTENSION' | 'MXGRAPH_DRAWIO';

export interface IntegrationRecipeDefinition {
    id: IntegrationRecipeId;
    name: string;
    description: string;
    moduleSuffix: string;
    appliedMessage: string;
    deliveryMode: IntegrationDeliveryMode;
    strategy: InjectionStrategy;
    runtimePreset: 'PORTABLE_MODELING_IDE' | 'PORTABLE_GRAPHICAL_INJECTABLE';
}

/**
 * Declarative catalogue of common multi-field presets. These presets never
 * replace the technology catalogue or its supported integration methods.
 */
export const INTEGRATION_RECIPES: readonly IntegrationRecipeDefinition[] = [
    {
        id: 'ECLIPSE_MODELING_DISTRIBUTION',
        name: 'Eclipse Modeling distribution',
        description: 'Select Eclipse Modeling and prepare a generated distribution using a reversible plug-in boundary.',
        moduleSuffix: 'eclipse-modeling',
        appliedMessage: 'Eclipse Modeling quick configuration applied.',
        deliveryMode: 'GENERATED_MODULE',
        strategy: 'ADD_PLUGIN',
        runtimePreset: 'PORTABLE_MODELING_IDE'
    },
    {
        id: 'VSCODE_EXTENSION',
        name: 'VS Code modeling extension',
        description: 'Select VS Code and prepare an installable generated extension using an additive package boundary.',
        moduleSuffix: 'vscode',
        appliedMessage: 'VS Code quick configuration applied.',
        deliveryMode: 'GENERATED_MODULE',
        strategy: 'ADD_PACKAGE',
        runtimePreset: 'PORTABLE_MODELING_IDE'
    },
    {
        id: 'MXGRAPH_DRAWIO',
        name: 'mxGraph / draw.io',
        description: 'Select mxGraph / draw.io and prepare a reversible package integration that can be previewed before changing the host project.',
        moduleSuffix: 'mxgraph-drawio',
        appliedMessage: 'mxGraph / draw.io quick configuration applied.',
        deliveryMode: 'GENERATED_MODULE',
        strategy: 'ADD_PACKAGE',
        runtimePreset: 'PORTABLE_GRAPHICAL_INJECTABLE'
    }
];

export function integrationRecipe(id: IntegrationRecipeId): IntegrationRecipeDefinition | undefined {
    return INTEGRATION_RECIPES.find(recipe => recipe.id === id);
}
