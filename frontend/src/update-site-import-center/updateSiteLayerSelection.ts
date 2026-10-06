import type { UpdateSiteConfidence, UpdateSiteImportAnalysis, UpdateSiteLayerSelection } from './types';

export function recommendedLayerSelection(analysis: UpdateSiteImportAnalysis): UpdateSiteLayerSelection {
    const palettes = analysis.recoveredLayers.palettes.filter(item => trusted(item.confidence));
    return {
        paletteGroupIds: palettes.map(item => item.id),
        paletteToolIds: palettes.flatMap(item => item.tools).filter(item => trusted(item.confidence)).map(item => item.id),
        representationIds: analysis.recoveredLayers.representations.filter(item => trusted(item.confidence)).map(item => item.id),
        mappingIds: analysis.recoveredLayers.mappings.filter(item => trusted(item.confidence) && item.diagnostic === '').map(item => item.id),
        semanticElementIds: (analysis.semanticRecovery?.elements ?? []).filter(item => item.representable && trusted(item.confidence)).map(item => item.id),
        explicit: true
    };
}

export function allLayerSelection(analysis: UpdateSiteImportAnalysis): UpdateSiteLayerSelection {
    return {
        paletteGroupIds: analysis.recoveredLayers.palettes.map(item => item.id),
        paletteToolIds: analysis.recoveredLayers.palettes.flatMap(item => item.tools).map(item => item.id),
        representationIds: analysis.recoveredLayers.representations.map(item => item.id),
        mappingIds: analysis.recoveredLayers.mappings.map(item => item.id),
        semanticElementIds: (analysis.semanticRecovery?.elements ?? []).map(item => item.id),
        explicit: true
    };
}

export function emptyLayerSelection(): UpdateSiteLayerSelection {
    return { paletteGroupIds: [], paletteToolIds: [], representationIds: [], mappingIds: [], semanticElementIds: [], explicit: true };
}

function trusted(confidence: UpdateSiteConfidence): boolean {
    return confidence === 'EXACT' || confidence === 'RECOVERED' || confidence === 'DERIVED';
}
