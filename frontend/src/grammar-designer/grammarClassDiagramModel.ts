import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import type { GrammarClassDiagramEdge, GrammarClassDiagramNode, GrammarClassDiagramProjection } from './types';

export type GrammarDiagramFocus = 'ALL'|'CONTAINMENT'|'REFERENCES'|'INHERITANCE';
export interface GrammarDiagramFilters {
    focus: GrammarDiagramFocus;
    showEnums: boolean;
    showDataTypes: boolean;
    showAttributes: boolean;
    showReferences: boolean;
    showDiagnostics: boolean;
}
export interface GrammarDiagramPlacement { id:string; x:number; y:number; width:number; height:number; }
export interface GrammarDiagramViewModel { nodes:GrammarClassDiagramNode[]; edges:GrammarClassDiagramEdge[]; placements:Map<string,GrammarDiagramPlacement>; }

export const DEFAULT_GRAMMAR_DIAGRAM_FILTERS:GrammarDiagramFilters={focus:'ALL',showEnums:true,showDataTypes:true,showAttributes:true,showReferences:true,showDiagnostics:true};

export function buildGrammarDiagramView(projection:GrammarClassDiagramProjection,filters:GrammarDiagramFilters=DEFAULT_GRAMMAR_DIAGRAM_FILTERS):GrammarDiagramViewModel{
    const nodes=projection.nodes.filter(node=>filters.showEnums||node.kind!=='ENUM').filter(node=>filters.showDataTypes||node.kind!=='DATA_TYPE');
    const visible=new Set(nodes.map(node=>node.id));
    const edges=projection.edges.filter(edge=>visible.has(edge.sourceId)&&visible.has(edge.targetId)).filter(edge=>edgeVisible(edge,filters));
    const layoutNodes=nodes.map(node=>{
        const visibleFeatures=node.features.filter(feature=>featureVisible(feature.kind,filters));
        const size=grammarClassNodeSize(node,visibleFeatures);
        return {id:node.id,width:size.width,height:size.height};
    });
    const placements=layoutHydraElkStyle(layoutNodes,edges.map(edge=>({id:edge.id,source:edge.sourceId,target:edge.targetId})),{direction:'RIGHT',nodeGap:68,layerGap:160,componentGap:140});
    return {nodes,edges,placements:new Map([...placements].map(([id,value])=>[id,{id,x:value.x+60,y:value.y+60,width:value.width,height:value.height}]))};
}

export function grammarClassNodeSize(node:GrammarClassDiagramNode,features=node.features):{width:number;height:number}{
    const headerChars=Math.max(8,node.name.length+(node.root?8:0));
    const featureChars=features.reduce((max,feature)=>Math.max(max,feature.name.length+feature.type.length+feature.multiplicity.length+6),0);
    const longest=Math.max(headerChars,featureChars);
    const width=Math.max(node.kind==='ENUM'?240:260,Math.min(620,80+longest*7.25));
    const usableChars=Math.max(18,Math.floor((width-56)/7.1));
    const rowUnits=features.reduce((total,feature)=>{
        const chars=feature.name.length+feature.type.length+feature.multiplicity.length+6;
        return total+Math.max(1,Math.ceil(chars/usableChars));
    },0);
    const height=Math.max(96,44+(features.length===0?1:rowUnits)*29+10);
    return {width:Math.round(width),height:Math.round(height)};
}

export function edgeVisible(edge:GrammarClassDiagramEdge,filters:GrammarDiagramFilters):boolean{
    if(filters.focus==='CONTAINMENT'&&edge.kind!=='CONTAINMENT'&&edge.kind!=='AGGREGATION')return false;
    if(filters.focus==='REFERENCES'&&edge.kind!=='REFERENCE'&&edge.kind!=='DEPENDENCY')return false;
    if(filters.focus==='INHERITANCE'&&edge.kind!=='INHERITANCE'&&edge.kind!=='IMPLEMENTATION')return false;
    if(!filters.showReferences&&edge.kind!=='INHERITANCE'&&edge.kind!=='IMPLEMENTATION')return false;
    return true;
}

export function featureVisible(kind:string,filters:GrammarDiagramFilters):boolean{
    if(kind==='ATTRIBUTE'||kind==='ENUM_LITERAL')return filters.showAttributes;
    if(kind==='REFERENCE'||kind==='CONTAINMENT'||kind==='AGGREGATION'||kind==='DEPENDENCY')return filters.showReferences;
    return true;
}

export function diagramStatusAllowsNavigation(projection:GrammarClassDiagramProjection):boolean{
    return projection.status!=='FAILED'&&projection.status!=='EMPTY'&&projection.nodes.length>0;
}
