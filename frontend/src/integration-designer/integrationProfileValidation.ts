import type { IntegrationArtifactKind, IntegrationFeature, IntegrationProfileModel } from './types';
import { generatedRootProblemMessage, validateGeneratedRootPath } from './generatedRootPath';

const requirements: Partial<Record<IntegrationFeature, IntegrationArtifactKind>> = {
    VALIDATION_RUNTIME: 'VALIDATIONS', VALIDATION_PROJECTION: 'VALIDATIONS', QUICK_FIX_RUNTIME: 'ACTIONS',
    OPERATION_RUNTIME: 'OPERATION_BEHAVIORS', EVENT_PUBLICATION: 'EVENT_DEFINITIONS', EVENT_CONSUMPTION: 'EVENT_DEFINITIONS',
    EVENT_DEFINITION_PROJECTION: 'EVENT_DEFINITIONS', EVENT_FLOW_RUNTIME: 'EVENT_FLOWS', EVENT_FLOW_PROJECTION: 'EVENT_FLOWS',
    GAMIFICATION_RUNTIME: 'GAMIFICATION_DEFINITIONS', GAMIFICATION_UI: 'GAMIFICATION_DEFINITIONS',
    GAMIFICATION_PROJECTION: 'GAMIFICATION_DEFINITIONS', EDITOR_UI_CONTRIBUTION: 'EDITOR_DEFINITION',
    NOTATION_CONTRIBUTION: 'EDITOR_DEFINITION', PALETTE_CONTRIBUTION: 'EDITOR_DEFINITION', CODE_GENERATION: 'CODE_GENERATION'
};

export function findIntegrationProfileBlockingError(model: IntegrationProfileModel): string {
    if (model.id.trim() === '' || model.name.trim() === '') return 'Profile ID and name are required.';
    if (!model.artifacts.some(item => item.kind === 'METAMODEL' && item.documentId === model.metamodelId)) return 'The owning metamodel must be selected as an artifact.';
    const generatedRoot = validateGeneratedRootPath(model.injectionPolicy.generatedRoot);
    if (!generatedRoot.valid) return generatedRootProblemMessage(generatedRoot.problem);
    if (!model.injectionPolicy.previewRequired || !model.injectionPolicy.rollbackRequired) return 'Preview and rollback are mandatory.';
    if (model.target.technology === 'PLANTUML') {
        if (model.deliveryMode !== 'PROJECTION_ONLY' || model.injectionPolicy.strategy !== 'GENERATE_PROJECTION') return 'PlantUML must use projection-only delivery.';
        const runtime = model.features.find(item => item.endsWith('RUNTIME') || ['EVENT_PUBLICATION', 'EVENT_CONSUMPTION', 'GAMIFICATION_UI', 'EDITOR_UI_CONTRIBUTION', 'NOTATION_CONTRIBUTION', 'PALETTE_CONTRIBUTION'].includes(item));
        if (runtime !== undefined) return 'PlantUML cannot execute runtime features.';
    }
    if (model.target.technology === 'BESSER_SDK' && model.features.some(item => ['GAMIFICATION_UI', 'EDITOR_UI_CONTRIBUTION', 'PALETTE_CONTRIBUTION'].includes(item))) return 'BESSER UI features require the Web Modeling Editor target.';

    for (const mapping of model.mappings) {
        if (mapping.id.trim() === '' || mapping.canonicalDocumentId.trim() === '') return 'Every mapping requires an ID and HYDRA document.';
        const host = mapping.hostReference;
        if ([host.documentUri, host.namespace, host.qualifiedName, host.externalId, host.path].every(value => value.trim() === '')) return `Mapping ${mapping.id} requires a host locator.`;
    }
    const kinds = new Set(model.artifacts.map(item => item.kind));
    for (const feature of model.features) {
        const required = requirements[feature];
        if (required !== undefined && !kinds.has(required)) return `${required} is required by ${feature}.`;
    }
    return '';
}
