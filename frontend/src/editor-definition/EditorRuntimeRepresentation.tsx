import { HydraButton } from '../design-system/HydraButton';
import type { Connection } from '@xyflow/react';
import type { EditorSessionDiagramDocument, EditorSessionView } from './types';
import { EditorRuntimeCanvas } from './EditorRuntimeCanvas';

interface Props {
    view: EditorSessionView;
    diagram: EditorSessionDiagramDocument;
    selectedIds: string[];
    onSelectNode: (elementId: string) => void;
    onSelectionChange?: (elementIds:string[])=>void;
    onConnect?: (connection: Connection) => void;
    onNodeDragStop?: (elementId:string,x:number,y:number,width:number,height:number,containerElementId:string)=>void;
    onNodeResizeStop?: (elementId:string,x:number,y:number,width:number,height:number,containerElementId:string)=>void;
    onDiagramNodesChange?: (nodes:import('./types').EditorSessionDiagramNodeView[])=>void;
    onPreviewCode?: (elementIds:string[])=>void;
    onDeleteSelection?: ()=>void;
    canPreviewCode?: boolean;
    busy?: boolean;
}

/** Renders every neutral representation kind over the same main HYDRA model session. */
export function EditorRuntimeRepresentation({view,diagram,selectedIds,onSelectNode,onSelectionChange,onConnect=()=>undefined,onNodeDragStop=()=>undefined,onNodeResizeStop=()=>undefined,onDiagramNodesChange,onPreviewCode,onDeleteSelection,canPreviewCode=false,busy=false}:Props){
    if(view.representationKind==='DIAGRAM') return <EditorRuntimeCanvas nodes={view.nodes} edges={view.edges} diagram={diagram} selectedIds={selectedIds} editable={view.editable} busy={busy} canPreviewCode={canPreviewCode} onSelectNode={onSelectNode} onSelectionChange={onSelectionChange} onPreviewCode={onPreviewCode} onDeleteSelection={onDeleteSelection} onConnect={onConnect} onNodeDragStop={onNodeDragStop} onNodeResizeStop={onNodeResizeStop} onDiagramNodesChange={onDiagramNodesChange}/>;
    if(view.representationKind==='TREE') return <TreeRepresentation view={view} selectedIds={selectedIds} onSelectNode={onSelectNode}/>;
    if(view.representationKind==='TABLE') return <TableRepresentation view={view} selectedIds={selectedIds} onSelectNode={onSelectNode}/>;
    if(view.representationKind==='FORM') return <FormRepresentation view={view} selectedIds={selectedIds} onSelectNode={onSelectNode}/>;
    if(view.representationKind==='MATRIX') return <MatrixRepresentation view={view} selectedIds={selectedIds} onSelectNode={onSelectNode}/>;
    return <DashboardRepresentation view={view} selectedIds={selectedIds} onSelectNode={onSelectNode}/>;
}

function TreeRepresentation({view,selectedIds,onSelectNode}:Pick<Props,'view'|'selectedIds'|'onSelectNode'>){
    const children=new Map<string,string[]>(); const incoming=new Set<string>();
    view.edges.forEach(edge=>{children.set(edge.sourceElementId,[...(children.get(edge.sourceElementId)??[]),edge.targetElementId]);incoming.add(edge.targetElementId);});
    const roots=view.nodes.filter(node=>!incoming.has(node.elementId));
    const render=(id:string,path:Set<string>):React.ReactNode=>{const node=view.nodes.find(item=>item.elementId===id);if(!node)return null;const cycle=path.has(id);const next=new Set(path);next.add(id);return <li key={`${[...path].join('/')}/${id}`}><HydraButton type="button" className={selectedIds.includes(id)?'selected':''} onClick={()=>onSelectNode(id)}><strong>{node.label}</strong><small>{node.className}</small></HydraButton>{!cycle&&(children.get(id)?.length??0)>0&&<ul>{children.get(id)?.map(child=>render(child,next))}</ul>}{cycle&&<small>Cycle reference</small>}</li>};
    return <div className="editor-runtime-tree" role="tree"><ul>{(roots.length>0?roots:view.nodes).map(node=>render(node.elementId,new Set()))}</ul>{view.nodes.length===0&&<p>No elements in this representation.</p>}</div>;
}
function TableRepresentation({view,selectedIds,onSelectNode}:Pick<Props,'view'|'selectedIds'|'onSelectNode'>){
    const featureIds=[...new Set(view.nodes.flatMap(node=>Object.keys(node.attributes)))].slice(0,20);
    return <div className="editor-runtime-table-wrap"><table><thead><tr><th>Element</th><th>Class</th>{featureIds.map(id=><th key={id}>{id}</th>)}</tr></thead><tbody>{view.nodes.map(node=><tr key={node.elementId} className={selectedIds.includes(node.elementId)?'selected':''} onClick={()=>onSelectNode(node.elementId)}><th>{node.label}</th><td>{node.className}</td>{featureIds.map(id=>{const value=formatValue(node.attributes[id]);return <td key={id}>{value===node.label?'':value}</td>;})}</tr>)}</tbody></table></div>;
}
function FormRepresentation({view,selectedIds,onSelectNode}:Pick<Props,'view'|'selectedIds'|'onSelectNode'>){
    const selected=view.nodes.find(node=>selectedIds.includes(node.elementId))??view.nodes[0];
    return <div className="editor-runtime-form-view"><nav>{view.nodes.map(node=><HydraButton type="button" key={node.elementId} className={selected?.elementId===node.elementId?'selected':''} onClick={()=>onSelectNode(node.elementId)}>{node.label}</HydraButton>)}</nav>{selected&&<article><h5>{selected.label}</h5><small><span>{selected.classId}</span><span> · {selected.className} · {selected.elementId}</span></small><dl>{Object.entries(selected.attributes).map(([key,value])=><div key={key}><dt>{key}</dt><dd>{formatValue(value)}</dd></div>)}</dl></article>}</div>;
}
function MatrixRepresentation({view,selectedIds,onSelectNode}:Pick<Props,'view'|'selectedIds'|'onSelectNode'>){
    const ids=view.nodes.slice(0,50);const connected=new Set(view.edges.map(edge=>`${edge.sourceElementId}|${edge.targetElementId}`));
    return <div className="editor-runtime-table-wrap"><table className="editor-runtime-matrix"><thead><tr><th>Source \ Target</th>{ids.map(node=><th key={node.elementId} title={node.label}>{node.label}</th>)}</tr></thead><tbody>{ids.map(source=><tr key={source.elementId} className={selectedIds.includes(source.elementId)?'selected':''}><th><HydraButton type="button" onClick={()=>onSelectNode(source.elementId)}>{source.label}</HydraButton></th>{ids.map(target=><td key={target.elementId}>{connected.has(`${source.elementId}|${target.elementId}`)?'●':'·'}</td>)}</tr>)}</tbody></table></div>;
}
function DashboardRepresentation({view,selectedIds,onSelectNode}:Pick<Props,'view'|'selectedIds'|'onSelectNode'>){
    const byClass=Object.entries(view.nodes.reduce<Record<string,number>>((result,node)=>({...result,[node.className]:(result[node.className]??0)+1}),{}));
    return <div className="editor-runtime-dashboard"><article><strong>{view.nodes.length}</strong><span>Visible elements</span></article><article><strong>{view.edges.length}</strong><span>Visible relations</span></article><article><strong>{view.validationReport?.diagnostics.length??0}</strong><span>Problems</span></article>{byClass.map(([name,count])=><HydraButton type="button" key={name} onClick={()=>{const node=view.nodes.find(item=>item.className===name);if(node)onSelectNode(node.elementId)}} className={view.nodes.some(node=>node.className===name&&selectedIds.includes(node.elementId))?'selected':''}><strong>{count}</strong><span>{name}</span></HydraButton>)}</div>;
}
function formatValue(value:unknown):string{if(value===null||value===undefined)return '';if(typeof value==='object')return JSON.stringify(value);return String(value);}
