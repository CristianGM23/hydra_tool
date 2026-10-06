import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import type { MetaModel, MetamodelDiagramDocument } from './types';
import type { MetamodelLayoutDirection } from './layoutTypes';

/** Applies the shared ELK-style layered engine to a neutral metamodel diagram. */
export function metamodelElkLayout(metamodel: MetaModel, diagram: MetamodelDiagramDocument, direction: MetamodelLayoutDirection): MetamodelDiagramDocument {
    if (diagram.nodes.length === 0) return diagram;
    const visible = new Set(diagram.nodes.map(node => node.elementId));
    const placements = layoutHydraElkStyle(
        diagram.nodes.map(node => ({ id: node.elementId, width: node.width, height: node.height })),
        metamodel.relations.filter(relation => visible.has(relation.sourceTypeId) && visible.has(relation.targetTypeId))
            .map(relation => ({ id: relation.id, source: relation.sourceTypeId, target: relation.targetTypeId })),
        { direction: direction === 'TOP_TO_BOTTOM' ? 'DOWN' : 'RIGHT', nodeGap: 64, layerGap: 130 }
    );
    const nodes = diagram.nodes.map(node => { const placement = placements.get(node.elementId); return placement ? { ...node, x: placement.x + 60, y: placement.y + 60, width: placement.width, height: placement.height } : node; });
    const nodeById = new Map(nodes.map(node => [node.elementId, node]));
    const relationById = new Map(metamodel.relations.map(relation => [relation.id, relation]));
    return {
        ...diagram,
        nodes,
        edges: diagram.edges.map(edge => {
            const relation=relationById.get(edge.relationId);
            const source=relation?nodeById.get(relation.sourceTypeId):undefined;
            const target=relation?nodeById.get(relation.targetTypeId):undefined;
            return { ...edge, routePoints: source&&target?orthogonalRoute(source,target,direction):[], labelPosition: undefined };
        })
    };
}

function orthogonalRoute(source:{x:number;y:number;width:number;height:number},target:{x:number;y:number;width:number;height:number},direction:MetamodelLayoutDirection){
    if(direction==='TOP_TO_BOTTOM'){
        const start={x:source.x+source.width/2,y:source.y+source.height};
        const end={x:target.x+target.width/2,y:target.y};
        const middle=(start.y+end.y)/2;
        return [start,{x:start.x,y:middle},{x:end.x,y:middle},end];
    }
    const start={x:source.x+source.width,y:source.y+source.height/2};
    const end={x:target.x,y:target.y+target.height/2};
    const middle=(start.x+end.x)/2;
    return [start,{x:middle,y:start.y},{x:middle,y:end.y},end];
}
