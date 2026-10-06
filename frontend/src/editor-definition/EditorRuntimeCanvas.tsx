import { resolveHydraDiagramPanPolicy, resolveHydraDiagramTranslateExtent } from '../designer-ui/boundedDiagramViewport';
import {
    Background, Controls, Handle, MiniMap, NodeResizer, Position, ReactFlow
} from '@xyflow/react';
import type { Connection, Node, NodeProps, OnNodeDrag, OnSelectionChangeFunc } from '@xyflow/react';
import type { OnNodesChange } from '@xyflow/react';
import { memo, useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from 'react';
import { editorVisualAssetUrl } from './editorVisualAssetUrl';
import { useHydraFlowPerformance } from '../diagram/useHydraFlowPerformance';
import { DiagramInteractionToolbar, type DiagramInteractionMode } from '../designer-ui/DiagramInteractionToolbar';
import type { DiagramSelectionArrangement } from '../designer-ui/diagramSelection';
import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import { hydraDirectedEdgeTypes } from '../designer-ui/hydraDirectedEdgeTypes';
import { constrainChildToShape, pointInsideShape } from './editorNotationGeometry';
import { labelCss } from './editorNotationStyle';
import { EditorNodeShapeFrame } from './EditorNodeShapeFrame';
import { EditorRuntimeSelectionActions } from './EditorRuntimeSelectionActions';
import { arrangeRuntimeSelection, elkRuntimeSelection, normalizeRuntimeRotation, rotateRuntimeDiagramNodes, rotationFitScale } from './editorRuntimeLayout';
import { buildEditorRuntimeFlowProjection, hasSameRuntimeSelection, type RuntimeNodeData } from './editorRuntimeFlowProjection';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { useHydraModelEditorNodeResize } from '../designer-ui/useHydraModelEditorNodeResize';
import type { EditorRenderedEdge, EditorRenderedNode, EditorSessionDiagramDocument, EditorSessionDiagramNodeView } from './types';
import '@xyflow/react/dist/style.css';

interface Props {
    nodes:EditorRenderedNode[];
    edges:EditorRenderedEdge[];
    diagram:EditorSessionDiagramDocument;
    selectedIds:string[];
    editable:boolean;
    busy?:boolean;
    canPreviewCode?:boolean;
    onSelectNode:(elementId:string)=>void;
    onSelectionChange?:(elementIds:string[])=>void;
    onPreviewCode?:(elementIds:string[])=>void;
    onDeleteSelection?:()=>void;
    onConnect:(connection:Connection)=>void;
    onNodeDragStop:(elementId:string,x:number,y:number,width:number,height:number,containerElementId:string)=>void;
    onNodeResizeStop:(elementId:string,x:number,y:number,width:number,height:number,containerElementId:string)=>void;
    onDiagramNodesChange?:(nodes:EditorSessionDiagramNodeView[])=>void;
}

const nodeTypes={ runtimeModelNode:memo(RuntimeModelNode) };

export function EditorRuntimeCanvas({nodes,edges,diagram,selectedIds,editable,busy=false,canPreviewCode=false,onSelectNode,onSelectionChange,onPreviewCode,onDeleteSelection,onConnect,onNodeDragStop,onNodeResizeStop,onDiagramNodesChange}:Props){
    const [interactionMode, setInteractionMode] = useState<DiagramInteractionMode>('SELECT');
    const performanceProfile = useHydraFlowPerformance(`model-runtime:${diagram.id}`, nodes.length, edges.length);
    const selectionChangeRef=useRef(onSelectionChange);
    const selectNodeRef=useRef(onSelectNode);
    const selectedIdsRef=useRef(selectedIds);
    useEffect(()=>{selectionChangeRef.current=onSelectionChange;},[onSelectionChange]);
    useEffect(()=>{selectNodeRef.current=onSelectNode;},[onSelectNode]);
    useEffect(()=>{selectedIdsRef.current=selectedIds;},[selectedIds]);
    const projection=useMemo(()=>buildEditorRuntimeFlowProjection(nodes,edges,diagram,selectedIds,editable,onNodeResizeStop),[diagram,editable,edges,nodes,onNodeResizeStop,selectedIds]);
    const visualNodes=projection.nodes;
    const visualEdges=projection.edges;

    // React Flow measures and selects nodes through its internal store. Feeding a
    // freshly rebuilt controlled array straight back into that store can reset
    // those transient frames and create a StoreUpdater -> setNodes render loop.
    // Keep a local React Flow snapshot and reconcile it only when HYDRA's
    // canonical nodes actually change. Drag/resize persistence remains handled by
    // the explicit host callbacks below.
    const ignoreInternalGeometryCommit=useCallback<OnNodesChange<Node<RuntimeNodeData>>>(()=>undefined,[]);
    const {nodes:flowNodes,onNodesChange}=useHydraModelEditorNodeResize(visualNodes,ignoreInternalGeometryCommit);

    const persistVisualNodes=useCallback((nextVisualNodes:Node<RuntimeNodeData>[])=>{
        if(onDiagramNodesChange===undefined)return;
        const byId=new Map(nextVisualNodes.map(node=>[node.id,node]));
        onDiagramNodesChange(diagram.nodes.map(stored=>{
            const visual=byId.get(stored.elementId);
            if(visual===undefined)return stored;
            return {...stored,x:visual.position.x,y:visual.position.y,width:visual.measured?.width??visual.width??stored.width,height:visual.measured?.height??visual.height??stored.height};
        }));
    },[diagram.nodes,onDiagramNodesChange]);

    function elkLayoutRuntime(): void {
        if (flowNodes.length === 0||onDiagramNodesChange===undefined) return;
        const placements = layoutHydraElkStyle(
            flowNodes.map(node => ({ id: node.id, width: node.measured?.width ?? node.width ?? 180, height: node.measured?.height ?? node.height ?? 96, parentId: node.parentId })),
            visualEdges.map(edge => ({ id: edge.id, source: edge.source, target: edge.target })),
            { direction: 'RIGHT', nodeGap: 56, layerGap: 120, padding: 24, containerHeader: 34 }
        );
        persistVisualNodes(flowNodes.map(node=>{const placement=placements.get(node.id);return placement===undefined?node:{...node,position:{x:placement.x,y:placement.y},width:placement.width,height:placement.height,parentId:placement.parentId??node.parentId};}));
    }
    function arrangeSelection(arrangement:DiagramSelectionArrangement):void{
        persistVisualNodes(arrangeRuntimeSelection(flowNodes,selectedIds,arrangement));
    }
    function elkArrangeSelection():void{
        persistVisualNodes(elkRuntimeSelection(flowNodes,edges,selectedIds));
    }
    function rotateSelection(deltaDegrees:number):void{
        onDiagramNodesChange?.(rotateRuntimeDiagramNodes(diagram.nodes,selectedIds,deltaDegrees));
    }
    const dragStop:OnNodeDrag<Node<RuntimeNodeData>>=(_event,node)=>{
        const width=node.measured?.width??node.width??180,height=node.measured?.height??node.height??96;
        const absolute=absolutePosition(node,flowNodes);const center={x:absolute.x+width/2,y:absolute.y+height/2};
        const candidates=flowNodes.filter(candidate=>candidate.id!==node.id&&candidate.data.container).map(candidate=>{
            const position=absolutePosition(candidate,flowNodes);const candidateWidth=candidate.measured?.width??candidate.width??180,candidateHeight=candidate.measured?.height??candidate.height??96;
            return {candidate,position,width:candidateWidth,height:candidateHeight,area:candidateWidth*candidateHeight};
        }).filter(item=>pointInsideShape(item.candidate.data.shape,{x:center.x-item.position.x,y:center.y-item.position.y},item.width,item.height,12)).sort((left,right)=>left.area-right.area);
        const target=candidates[0];
        if(target!==undefined){
            const padding=nodes.find(item=>item.elementId===target.candidate.id)?.containerPadding??18;
            const relative=constrainChildToShape({x:absolute.x-target.position.x,y:absolute.y-target.position.y,width,height},target.width,target.height,padding,target.candidate.data.shape);
            onNodeDragStop(node.id,relative.x,relative.y,relative.width,relative.height,target.candidate.id);return;
        }
        onNodeDragStop(node.id,absolute.x,absolute.y,width,height,'');
    };
    // Keep the callbacks registered in React Flow stable. React Flow stores these
    // handlers in Zustand; rebuilding them on every selection frame can turn a
    // controlled selection update into a StoreUpdater feedback cycle.
    const selectionChange=useCallback<OnSelectionChangeFunc>(({nodes:selectedNodes})=>{
        const nextIds=selectedNodes.map(node=>node.id);
        if(hasSameRuntimeSelection(selectedIdsRef.current,nextIds))return;
        selectionChangeRef.current?.(nextIds);
    },[]);
    const nodeClick=useCallback((event:MouseEvent,node:Node<RuntimeNodeData>)=>{
        const notifySelection=selectionChangeRef.current;
        if(notifySelection===undefined){selectNodeRef.current(node.id);return;}
        const current=selectedIdsRef.current;
        const multiple=event.ctrlKey||event.metaKey;
        const nextIds=multiple
            ?(current.includes(node.id)?current.filter(id=>id!==node.id):[...current,node.id])
            :[node.id];
        if(!hasSameRuntimeSelection(current,nextIds))notifySelection(nextIds);
    },[]);
    const selectedSet=useMemo(()=>new Set(selectedIds),[selectedIds]);
    const internalRelationCount=useMemo(()=>edges.filter(edge=>selectedSet.has(edge.sourceElementId)&&selectedSet.has(edge.targetElementId)).length,[edges,selectedSet]);
    const selectionActionsAvailable=editable&&selectedIds.length>0&&onSelectionChange!==undefined;
    return <div className="editor-runtime-flow" aria-label="Generated graphical model editor">
        <DiagramInteractionToolbar performanceId={`editor-runtime:${diagram.id}`} mode={interactionMode} onModeChange={setInteractionMode} allowConnect={editable} onElkLayout={editable && flowNodes.length > 0 && onDiagramNodesChange!==undefined ? elkLayoutRuntime : undefined} />
        <ReactFlow fitView onlyRenderVisibleElements={performanceProfile.onlyRenderVisibleElements} nodes={flowNodes} edges={visualEdges} nodeTypes={nodeTypes} edgeTypes={hydraDirectedEdgeTypes}
            nodesDraggable={editable && interactionMode === 'SELECT'} nodesConnectable={editable && interactionMode === 'CONNECT'}
            elementsSelectable={interactionMode === 'SELECT'} {...resolveHydraDiagramPanPolicy(interactionMode)} translateExtent={resolveHydraDiagramTranslateExtent(flowNodes)}
            selectionOnDrag={interactionMode === 'SELECT'} multiSelectionKeyCode={['Control', 'Meta']} selectionKeyCode={null}
            onConnect={onConnect} onNodesChange={onNodesChange} onNodeClick={nodeClick} onSelectionChange={onSelectionChange===undefined?undefined:selectionChange} onNodeDragStop={dragStop}>
            <Background gap={18}/><MiniMap pannable zoomable/><Controls/>
        </ReactFlow>
        {selectionActionsAvailable&&<EditorRuntimeSelectionActions elementCount={selectedIds.length} relationCount={internalRelationCount} disabled={busy||onDiagramNodesChange===undefined} canPreviewCode={canPreviewCode&&onPreviewCode!==undefined} onArrange={arrangeSelection} onElkArrange={elkArrangeSelection} onPreviewCode={()=>onPreviewCode?.(selectedIds)} onRotate={rotateSelection} onClear={()=>onSelectionChange([])} onDelete={()=>onDeleteSelection?.()}/>} 
    </div>;
}

function RuntimeModelNode({id,data,selected}:NodeProps<Node<RuntimeNodeData>>){
    const {t}=useHydraLanguage();
    const image=data.imageAssetPath===''?null:editorVisualAssetUrl(data.imageAssetPath);const notation=data.notation;
    const rotation=normalizeRuntimeRotation(data.rotationDegrees);
    const fitScale=rotationFitScale(data.visualWidth,data.visualHeight,rotation);
    return <article aria-label={`${data.label} (${data.className})`} className={`editor-runtime-model-node shape-${String(data.shape).toLowerCase()} label-${notation.labelPosition.toLowerCase()}${selected?' selected':''}${data.container?' container':''}`}>
        <NodeResizer isVisible={Boolean(selected&&data.editable)} minWidth={data.minimumWidth} minHeight={data.minimumHeight} handleClassName="editor-runtime-resize-handle" lineClassName="editor-runtime-resize-line" onResizeEnd={(_event,params)=>data.onResizeStop(String(id),params.x,params.y,params.width,params.height,data.parentId)}/>
        <Handle type="target" position={Position.Left}/>
        <div className="editor-runtime-node-visual" data-rotation-degrees={rotation} style={{transform:`rotate(${rotation}deg) scale(${fitScale})`}}>
            <EditorNodeShapeFrame shape={data.shape} notation={notation} className="editor-runtime-node-shape"/>
            {image!==null&&<img className="editor-runtime-node-image" src={image} alt={`${data.representationName} representation`} style={{objectFit:imageFit(data.imageFit)}}/>}
            {data.container&&<div aria-hidden="true" className="editor-runtime-container-drop-zone"/>}
            <div className="editor-runtime-node-content">{notation.labelVisible&&<strong className="editor-runtime-node-label" style={labelCss(notation)} title={data.label}>{data.label}</strong>}{data.container&&<em>{t('Drop compatible elements inside')}</em>}</div>
        </div>
        <Handle type="source" position={Position.Right}/>
    </article>;
}

function absolutePosition(node:Node<RuntimeNodeData>,all:Node<RuntimeNodeData>[]):{x:number;y:number}{
    let x=node.position.x,y=node.position.y,parentId=node.parentId;const visited=new Set<string>([node.id]);
    while(parentId!==undefined&&!visited.has(parentId)){visited.add(parentId);const parent=all.find(item=>item.id===parentId);if(parent===undefined)break;x+=parent.position.x;y+=parent.position.y;parentId=parent.parentId;}
    return {x,y};
}
function imageFit(value:string):'contain'|'cover'|'fill'|'none'{return value==='COVER'?'cover':value==='FILL'?'fill':value==='NONE'?'none':'contain';}
