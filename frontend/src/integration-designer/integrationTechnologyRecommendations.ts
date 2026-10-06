import type { IntegrationCapability, IntegrationFeature, IntegrationTechnologyDescriptor } from './types';

const REQUIREMENTS:Readonly<Record<IntegrationFeature,readonly IntegrationCapability[]>>={
    VALIDATION_RUNTIME:['VALIDATION_BRIDGE'], QUICK_FIX_RUNTIME:['QUICK_FIX_BRIDGE'], OPERATION_RUNTIME:['OPERATION_BRIDGE'],
    EVENT_PUBLICATION:['EVENT_PUBLISH'], EVENT_CONSUMPTION:['EVENT_CONSUME'], EVENT_FLOW_RUNTIME:['EVENT_FLOW_RUNTIME'],
    GAMIFICATION_RUNTIME:['GAMIFICATION_RUNTIME'], GAMIFICATION_UI:['GAMIFICATION_RUNTIME','UI_CONTRIBUTION'],
    EDITOR_UI_CONTRIBUTION:['UI_CONTRIBUTION'], NOTATION_CONTRIBUTION:['NOTATION_CONTRIBUTION'], PALETTE_CONTRIBUTION:['PALETTE_CONTRIBUTION'],
    VALIDATION_PROJECTION:['VALIDATION_PROJECTION'], EVENT_DEFINITION_PROJECTION:['EVENT_DEFINITION_PROJECTION'], EVENT_FLOW_PROJECTION:['EVENT_FLOW_PROJECTION'],
    GAMIFICATION_PROJECTION:['GAMIFICATION_PROJECTION'], CODE_GENERATION:['CODE_GENERATION'], PROJECT_IMPORT:['PROJECT_IMPORT'], PROJECT_EXPORT:['PROJECT_EXPORT']
};
const PROJECTIONS=new Set<IntegrationFeature>(['VALIDATION_PROJECTION','EVENT_DEFINITION_PROJECTION','EVENT_FLOW_PROJECTION','GAMIFICATION_PROJECTION']);
const RUNTIME_ONLY=new Set<IntegrationFeature>(['VALIDATION_RUNTIME','QUICK_FIX_RUNTIME','OPERATION_RUNTIME','EVENT_PUBLICATION','EVENT_CONSUMPTION','EVENT_FLOW_RUNTIME','GAMIFICATION_RUNTIME','GAMIFICATION_UI','EDITOR_UI_CONTRIBUTION','NOTATION_CONTRIBUTION','PALETTE_CONTRIBUTION','CODE_GENERATION']);

/** Selects only fully supported capabilities so a quick recommendation never silently opts users into partial or external behavior. */
export function recommendedIntegrationFeatures(descriptor:IntegrationTechnologyDescriptor):IntegrationFeature[]{
    const projection=descriptor.targetKind==='DIAGRAM_PROJECTION';
    return (Object.keys(REQUIREMENTS) as IntegrationFeature[]).filter(feature=>{
        if(projection&&RUNTIME_ONLY.has(feature))return false;
        if(!projection&&PROJECTIONS.has(feature))return false;
        return REQUIREMENTS[feature].every(capability=>descriptor.capabilities[capability]==='FULL');
    });
}
