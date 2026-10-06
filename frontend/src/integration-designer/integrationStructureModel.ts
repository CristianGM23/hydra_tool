import type { DesignerStructureNode } from '../designer-ui/DesignerStructureTree';
import type { IntegrationProfileModel, TechnologyInjectionPreview } from './types';
import type { IntegrationStructureSelection } from './integrationStructureTypes';

export function buildIntegrationStructure(model: IntegrationProfileModel, preview: TechnologyInjectionPreview | null): DesignerStructureNode<IntegrationStructureSelection>[] {
    const artifacts = model.artifacts.map((artifact, index) => ({
        id: `integration:ARTIFACT:${index}`,
        label: artifact.kind,
        detail: artifact.documentId,
        badge: artifact.required ? 'REQUIRED' : 'OPTIONAL',
        data: { kind: 'ARTIFACT' as const, id: String(index), index }
    }));
    const mappings = model.mappings.map((mapping, index) => ({
        id: `integration:MAPPING:${mapping.id || index}`,
        label: mapping.id || `Mapping ${index + 1}`,
        detail: `${mapping.canonicalElementId || 'unresolved'} → ${mapping.hostReference.qualifiedName || mapping.hostReference.path || 'unresolved'}`,
        badge: mapping.kind,
        data: { kind: 'MAPPING' as const, id: mapping.id || String(index), index },
        children: [
            { id: `integration-mapping-source:${index}`, label: 'HYDRA element', detail: mapping.canonicalElementId, badge: 'SOURCE', data: { kind: 'MAPPING' as const, id: mapping.id || String(index), index } },
            { id: `integration-mapping-target:${index}`, label: 'Host element', detail: mapping.hostReference.qualifiedName || mapping.hostReference.path || mapping.hostReference.externalId, badge: mapping.hostReference.strategy, data: { kind: 'MAPPING' as const, id: mapping.id || String(index), index } }
        ]
    }));
    return [{
        id: 'integration-root',
        label: model.name || 'Integration profile',
        detail: model.id,
        badge: model.target.technology,
        data: { kind: 'PROFILE', id: model.id },
        children: [
            { id: 'integration:TARGET:target', label: 'Target project and technology', detail: `${model.target.technology} · ${model.target.projectId}`, badge: model.target.buildSystem, data: { kind: 'TARGET', id: 'target' } },
            { id: 'integration:INJECTION:policy', label: 'Host integration', detail: model.injectionPolicy.generatedRoot, badge: model.injectionPolicy.strategy, data: { kind: 'INJECTION', id: 'policy' } },
            { id: 'integration:FEATURES:features', label: 'Enabled features', badge: String(model.features.length), data: { kind: 'FEATURES', id: 'features' }, children: model.features.map(feature => ({ id: `integration-feature:${feature}`, label: feature, badge: 'FEATURE', data: { kind: 'FEATURES' as const, id: 'features' } })) },
            { id: 'integration-artifacts', label: 'HYDRA input documents', badge: String(artifacts.length), children: artifacts },
            { id: 'integration-mappings', label: 'Host mappings', badge: String(mappings.length), children: mappings },
            ...(preview === null ? [] : [{ id: 'integration:PREVIEW:preview', label: 'Change preview', detail: `${preview.plan.fileChanges.length} changes`, badge: preview.plan.status, data: { kind: 'PREVIEW' as const, id: 'preview' }, children: preview.plan.fileChanges.map(change => ({ id: `integration-preview:${change.changeKind}:${change.path}`, label: change.path, detail: change.changeKind, badge: change.ownership, data: { kind: 'PREVIEW' as const, id: 'preview' } })) }])
        ]
    }];
}
