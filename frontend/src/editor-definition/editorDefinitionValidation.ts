import type { EditorDefinitionModel } from './types';
export function findEditorDefinitionBlockingError(model:EditorDefinitionModel):string {
    if(!/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/.test(model.id)) return 'The editor identifier is invalid.';
    if(model.name.trim()==='') return 'The editor requires a name.';
    if(model.representations.length===0) return 'The editor requires at least one representation.';
    const representationIds=new Set<string>();
    for(const representation of model.representations){if(representationIds.has(representation.id)) return `Representation identifier "${representation.id}" is duplicated.`; representationIds.add(representation.id);}
    for(const representation of model.representations) for(const mapping of representation.nodeMappings) {
        const conditionIds=new Set<string>();
        for(const rule of mapping.conditionalStyles ?? []) {
            if(rule.conditionExpressionId.trim()==='') return `Conditional style "${rule.name}" requires a condition.`;
            if(conditionIds.has(rule.conditionExpressionId)) return `Conditional style "${rule.name}" reuses a condition already used by this representation.`;
            conditionIds.add(rule.conditionExpressionId);
        }
    }
    const toolIds=new Set<string>();
    for(const tool of model.palette.tools){if(toolIds.has(tool.id)) return `Tool identifier "${tool.id}" is duplicated.`; toolIds.add(tool.id); for(const representationId of tool.representationIds) if(!representationIds.has(representationId)) return `Tool "${tool.label}" references an unknown representation.`;}
    for(const group of model.palette.groups) for(const toolId of group.toolIds) if(!toolIds.has(toolId)) return `Palette group "${group.name}" references an unknown tool.`;
    return '';
}
