import type { Edge, Node } from '@xyflow/react';
import { constrainChildToShape } from './editorNotationGeometry';
import { nodeFrameCss, resolveEdgeNotationStyle, resolveNodeNotationStyle } from './editorNotationStyle';
import { normalizeRuntimeRotation } from './editorRuntimeLayout';
import type { EditorRenderedEdge, EditorRenderedNode, EditorSessionDiagramDocument, NodeMapping } from './types';

export interface RuntimeNodeData extends Record<string, unknown> {
    label:string;
    className:string;
    representationName:string;
    shape:NodeMapping['shape'];
    imageAssetPath:string;
    imageFit:string;
    container:boolean;
    containmentRelationIds:string[];
    notation:ReturnType<typeof resolveNodeNotationStyle>;
    editable:boolean;
    parentId:string;
    minimumWidth:number;
    minimumHeight:number;
    visualWidth:number;
    visualHeight:number;
    rotationDegrees:number;
    onResizeStop:(elementId:string,x:number,y:number,width:number,height:number,containerElementId:string)=>void;
}

export function buildEditorRuntimeFlowProjection(
    nodes:EditorRenderedNode[],
    edges:EditorRenderedEdge[],
    diagram:EditorSessionDiagramDocument,
    selectedIds:readonly string[],
    editable:boolean,
    onResizeStop:(elementId:string,x:number,y:number,width:number,height:number,containerElementId:string)=>void
):{nodes:Node<RuntimeNodeData>[];edges:Edge[]}{
    const byId=new Map(nodes.map(node=>[node.elementId,node]));
    const containmentParents=new Map<string,string>();
    for(const edge of edges){
        const source=byId.get(edge.sourceElementId);
        if(source?.container===true&&(source.containmentRelationIds??[]).includes(edge.relationId)&&!containmentParents.has(edge.targetElementId)) containmentParents.set(edge.targetElementId,edge.sourceElementId);
    }
    const ordered=[...nodes].sort((left,right)=>Number(right.container)-Number(left.container)||Number(containmentParents.has(left.elementId))-Number(containmentParents.has(right.elementId)));
    const visualNodes:Node<RuntimeNodeData>[]=ordered.map((node,index)=>{
        const stored=diagram.nodes.find(item=>item.elementId===node.elementId);
        const parentId=containmentParents.get(node.elementId);
        const parent=parentId===undefined?undefined:byId.get(parentId);
        const parentStored=parentId===undefined?undefined:diagram.nodes.find(item=>item.elementId===parentId);
        const padding=parent?.containerPadding??18;
        const width=stored?.width??node.width,height=stored?.height??node.height;
        const rawX=stored?.x??(parent===undefined?80+(index%4)*230:padding+(index%2)*(width+12));
        const rawY=stored?.y??(parent===undefined?70+Math.floor(index/4)*150:padding+34+Math.floor(index/2)*(height+12));
        const parentWidth=parentStored?.width??parent?.width??180,parentHeight=parentStored?.height??parent?.height??96;
        const constrained=parent===undefined?{x:rawX,y:rawY,width,height}:constrainChildToShape({x:rawX,y:rawY,width,height},parentWidth,parentHeight,padding,parent.shape as NodeMapping['shape']);
        const notation=resolveNodeNotationStyle(node.styles);
        return {
            id:node.elementId,type:'runtimeModelNode',position:{x:constrained.x,y:constrained.y},width:constrained.width,height:constrained.height,parentId,
            expandParent:false,selected:selectedIds.includes(node.elementId),draggable:editable,connectable:editable,zIndex:parentId===undefined?0:2,
            data:{label:node.label,className:node.className,representationName:node.representationName,shape:node.shape as NodeMapping['shape'],imageAssetPath:node.imageAssetPath??'',imageFit:node.imageFit??'CONTAIN',container:node.container===true,containmentRelationIds:node.containmentRelationIds??[],notation,editable,parentId:parentId??'',minimumWidth:Math.max(40,node.minimumWidth??80),minimumHeight:Math.max(30,node.minimumHeight??50),visualWidth:constrained.width,visualHeight:constrained.height,rotationDegrees:normalizeRuntimeRotation(stored?.rotationDegrees),onResizeStop},
            style:{width:constrained.width,height:constrained.height,minHeight:constrained.height,overflow:'visible',...nodeFrameCss(notation)}
        };
    });
    const visualEdges:Edge[]=edges.filter(edge=>containmentParents.get(edge.targetElementId)!==edge.sourceElementId).map(edge=>{
        const notation=resolveEdgeNotationStyle(edge.styles);
        const baseRoute=edge.lineType==='STRAIGHT'?'STRAIGHT':edge.lineType==='BEZIER'?'SMOOTH':'ORTHOGONAL';
        const routeStyle=edge.styles.routeStyle==='STRAIGHT'||edge.styles.routeStyle==='SMOOTH'||edge.styles.routeStyle==='ORTHOGONAL'?edge.styles.routeStyle:baseRoute;
        const lineStyle=notation.borderStyle;
        return {
            id:edge.relationInstanceId,source:edge.sourceElementId,target:edge.targetElementId,type:'hydraDirectedEdge',
            label:notation.labelVisible?(edge.label||edge.relationName):'',
            data:{routeStyle,lineStyle,lineColor:notation.strokeColor,lineWidth:notation.strokeWidth,lineOpacity:notation.strokeOpacity,lineBorderColor:notation.strokeBorderColor,lineBorderWidth:notation.strokeBorderWidth,connectionLabel:edge.label||edge.relationName,
                sourceTerminator:notation.sourceDecoration,targetTerminator:notation.targetDecoration,
                sourceTerminatorFillColor:notation.sourceDecorationFillColor,sourceTerminatorBorderColor:notation.sourceDecorationBorderColor,sourceTerminatorBorderWidth:notation.sourceDecorationBorderWidth,sourceTerminatorSize:notation.sourceDecorationSize,
                targetTerminatorFillColor:notation.targetDecorationFillColor,targetTerminatorBorderColor:notation.targetDecorationBorderColor,targetTerminatorBorderWidth:notation.targetDecorationBorderWidth,targetTerminatorSize:notation.targetDecorationSize},
            labelStyle:{fill:notation.labelColor,fontSize:notation.labelFontSize,fontWeight:notation.labelFontWeight},
            labelBgStyle:{fill:notation.labelBackgroundColor,fillOpacity:notation.labelBackgroundTransparent?0:.94},labelBgPadding:[notation.labelPadding,notation.labelPadding],labelShowBg:notation.labelVisible&&!notation.labelBackgroundTransparent
        };
    });
    return {nodes:visualNodes,edges:visualEdges};
}


export function hasSameRuntimeSelection(left:readonly string[],right:readonly string[]):boolean{
    return left.length===right.length&&left.every((value,index)=>value===right[index]);
}
