import type { GrammarClassDiagramEdge, GrammarClassDiagramNode } from './types';
import type { GrammarDiagramPlacement, GrammarDiagramViewModel } from './grammarClassDiagramModel';

export type GrammarDiagramExportFormat='png'|'svg'|'jpeg'|'drawio'|'plantuml';

export async function downloadGrammarClassDiagram(view:GrammarDiagramViewModel,format:GrammarDiagramExportFormat,fileBase:string):Promise<void>{
    const safe=safeFileName(fileBase||'inferred-metamodel');
    if(format==='plantuml'){downloadText(buildGrammarPlantUml(view),`${safe}.puml`,'text/plain;charset=utf-8');return;}
    if(format==='drawio'){downloadText(buildGrammarDrawio(view),`${safe}.drawio`,'application/xml;charset=utf-8');return;}
    const svg=buildGrammarDiagramSvg(view);
    if(format==='svg'){downloadText(svg,`${safe}.svg`,'image/svg+xml;charset=utf-8');return;}
    await downloadRaster(svg,format==='png'?'image/png':'image/jpeg',`${safe}.${format==='png'?'png':'jpg'}`);
}

export function buildGrammarPlantUml(view:GrammarDiagramViewModel):string{
    const aliases=new Map(view.nodes.map((node,index)=>[node.id,`C${index+1}`]));
    const lines=['@startuml','skinparam classAttributeIconSize 0'];
    for(const node of view.nodes){
        const alias=aliases.get(node.id)!;
        const declaration=node.kind==='INTERFACE'?'interface':node.kind==='ENUM'?'enum':node.kind==='ABSTRACT_CLASS'?'abstract class':'class';
        lines.push(`${declaration} "${plantText(node.name)}" as ${alias} {`);
        for(const feature of node.features)lines.push(`  ${plantText(feature.name)} : ${plantText(feature.type)}${feature.multiplicity?` ${plantText(feature.multiplicity)}`:''}`);
        lines.push('}');
    }
    for(const edge of view.edges){
        const source=aliases.get(edge.sourceId),target=aliases.get(edge.targetId);if(!source||!target)continue;
        const arrow=edge.kind==='CONTAINMENT'?'*-->':edge.kind==='AGGREGATION'?'o-->':edge.kind==='INHERITANCE'?'--|>':edge.kind==='IMPLEMENTATION'?'..|>':edge.kind==='DEPENDENCY'?'..>':'-->';
        const multiplicity=edge.targetMultiplicity?` "${plantText(edge.targetMultiplicity)}"`:'';
        const label=edge.label?` : ${plantText(edge.label)}`:'';
        lines.push(`${source} ${arrow}${multiplicity} ${target}${label}`);
    }
    lines.push('@enduml');return lines.join('\n');
}

export function buildGrammarDrawio(view:GrammarDiagramViewModel):string{
    const cells:string[]=['<mxCell id="0"/>','<mxCell id="1" parent="0"/>'];
    for(const node of view.nodes){
        const p=view.placements.get(node.id)??{id:node.id,x:0,y:0,width:280,height:120};
        const body=node.features.map(feature=>`${feature.name} : ${feature.type}${feature.multiplicity?` ${feature.multiplicity}`:''}`);
        const value=xmlAttr([node.name,...body].join('\n'));
        cells.push(`<mxCell id="${xmlAttr(node.id)}" value="${value}" style="swimlane;fontStyle=1;childLayout=stackLayout;horizontal=1;startSize=30;html=0;rounded=0;collapsible=0;whiteSpace=wrap;" vertex="1" parent="1"><mxGeometry x="${round(p.x)}" y="${round(p.y)}" width="${round(p.width)}" height="${round(p.height)}" as="geometry"/></mxCell>`);
    }
    for(const edge of view.edges){
        const style=drawioEdgeStyle(edge);
        const value=xmlAttr(edgeLabel(edge));
        cells.push(`<mxCell id="${xmlAttr(edge.id)}" value="${value}" style="${style}" edge="1" parent="1" source="${xmlAttr(edge.sourceId)}" target="${xmlAttr(edge.targetId)}"><mxGeometry relative="1" as="geometry"/></mxCell>`);
    }
    return `<mxfile host="HYDRA" version="4.32.107"><diagram name="Inferred metamodel"><mxGraphModel><root>${cells.join('')}</root></mxGraphModel></diagram></mxfile>`;
}

export function buildGrammarDiagramSvg(view:GrammarDiagramViewModel):string{
    const bounds=diagramBounds(view);
    const markerDefs=`<defs><marker id="arrow" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto"><path d="M1 1 L11 6 L1 11" fill="none" stroke="#334155" stroke-width="1.5"/></marker><marker id="triangle" markerWidth="14" markerHeight="14" refX="12" refY="7" orient="auto"><path d="M1 1 L12 7 L1 13 Z" fill="#fff" stroke="#334155" stroke-width="1.4"/></marker><marker id="diamond-filled" markerWidth="16" markerHeight="16" refX="1" refY="8" orient="auto-start-reverse"><path d="M1 8 L8 1 L15 8 L8 15 Z" fill="#334155" stroke="#334155"/></marker><marker id="diamond-open" markerWidth="16" markerHeight="16" refX="1" refY="8" orient="auto-start-reverse"><path d="M1 8 L8 1 L15 8 L8 15 Z" fill="#fff" stroke="#334155"/></marker></defs>`;
    const edges=view.edges.map(edge=>svgEdge(edge,view.placements)).join('');
    const nodes=view.nodes.map(node=>svgNode(node,view.placements.get(node.id))).join('');
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${bounds.width}" height="${bounds.height}" viewBox="0 0 ${bounds.width} ${bounds.height}"><rect width="100%" height="100%" fill="#f8fafc"/>${markerDefs}<g font-family="Arial, sans-serif">${edges}${nodes}</g></svg>`;
}

function svgNode(node:GrammarClassDiagramNode,placement:GrammarDiagramPlacement|undefined):string{
    if(!placement)return '';
    const {x,y,width,height}=placement;const header=34,row=25;
    const featureRows=node.features.map((feature,index)=>{const yy=y+header+(index+1)*row-8;return `<line x1="${x}" y1="${y+header+index*row}" x2="${x+width}" y2="${y+header+index*row}" stroke="#cbd5e1"/><text x="${x+10}" y="${yy}" font-size="12" fill="#0f172a">${svgText(feature.name)}</text><text x="${x+width-10}" y="${yy}" font-size="12" text-anchor="end" fill="#475569">${svgText(`${feature.type}${feature.multiplicity?` ${feature.multiplicity}`:''}`)}</text>`;}).join('');
    return `<g><rect x="${x}" y="${y}" width="${width}" height="${height}" rx="6" fill="#fff" stroke="#64748b"/><rect x="${x}" y="${y}" width="${width}" height="${header}" rx="6" fill="#f1f5f9"/><line x1="${x}" y1="${y+header}" x2="${x+width}" y2="${y+header}" stroke="#94a3b8"/><text x="${x+width/2}" y="${y+23}" font-size="16" font-weight="700" text-anchor="middle" fill="#0f172a">${svgText(node.name)}</text>${featureRows}</g>`;
}

function svgEdge(edge:GrammarClassDiagramEdge,placements:Map<string,GrammarDiagramPlacement>):string{
    const s=placements.get(edge.sourceId),t=placements.get(edge.targetId);if(!s||!t)return '';
    const x1=s.x+s.width,y1=s.y+s.height/2,x2=t.x,y2=t.y+t.height/2,mid=x1+(x2-x1)/2;
    const start=edge.kind==='CONTAINMENT'?' marker-start="url(#diamond-filled)"':edge.kind==='AGGREGATION'?' marker-start="url(#diamond-open)"':'';
    const end=edge.kind==='INHERITANCE'||edge.kind==='IMPLEMENTATION'?'url(#triangle)':'url(#arrow)';
    const dash=edge.kind==='IMPLEMENTATION'||edge.kind==='DEPENDENCY'?' stroke-dasharray="8 5"':'';
    const label=edgeLabel(edge);
    return `<g><path d="M${x1} ${y1} H${mid} V${y2} H${x2}" fill="none" stroke="#334155" stroke-width="2"${dash}${start} marker-end="${end}"/>${label?`<rect x="${mid-55}" y="${(y1+y2)/2-18}" width="110" height="20" rx="4" fill="#fff" opacity=".94"/><text x="${mid}" y="${(y1+y2)/2-4}" font-size="11" text-anchor="middle" fill="#0f172a">${svgText(label)}</text>`:''}</g>`;
}

function diagramBounds(view:GrammarDiagramViewModel){const p=[...view.placements.values()];const width=Math.ceil(Math.max(720,...p.map(v=>v.x+v.width+80)));const height=Math.ceil(Math.max(420,...p.map(v=>v.y+v.height+80)));return{width,height};}
function edgeLabel(edge:GrammarClassDiagramEdge){return `${edge.label}${edge.targetMultiplicity?` ${edge.targetMultiplicity}`:''}`.trim();}
function drawioEdgeStyle(edge:GrammarClassDiagramEdge){const base='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;';if(edge.kind==='CONTAINMENT')return `${base}startArrow=diamond;startFill=1;endArrow=open;endFill=0;`;if(edge.kind==='AGGREGATION')return `${base}startArrow=diamond;startFill=0;endArrow=open;endFill=0;`;if(edge.kind==='INHERITANCE')return `${base}endArrow=block;endFill=0;`;if(edge.kind==='IMPLEMENTATION')return `${base}endArrow=block;endFill=0;dashed=1;`;if(edge.kind==='DEPENDENCY')return `${base}endArrow=open;endFill=0;dashed=1;`;return `${base}endArrow=open;endFill=0;`;}
async function downloadRaster(svg:string,mime:string,fileName:string){const blob=new Blob([svg],{type:'image/svg+xml;charset=utf-8'});const url=URL.createObjectURL(blob);try{const image=await loadImage(url);const canvas=document.createElement('canvas');canvas.width=Math.max(1,image.naturalWidth||image.width);canvas.height=Math.max(1,image.naturalHeight||image.height);const context=canvas.getContext('2d');if(context===null)throw new Error('Canvas 2D is not available');if(mime==='image/jpeg'){context.fillStyle='#ffffff';context.fillRect(0,0,canvas.width,canvas.height);}context.drawImage(image,0,0);const output=await new Promise<Blob>((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(new Error('Diagram rasterization failed')),mime,mime==='image/jpeg' ? .94 : undefined));downloadBlob(output,fileName);}finally{URL.revokeObjectURL(url);}}
function loadImage(url:string):Promise<HTMLImageElement>{return new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=()=>reject(new Error('Diagram image could not be rendered'));image.src=url;});}
function downloadText(text:string,fileName:string,mime:string){downloadBlob(new Blob([text],{type:mime}),fileName);}
function downloadBlob(blob:Blob,fileName:string){const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=fileName;anchor.style.display='none';document.body.appendChild(anchor);anchor.click();anchor.remove();window.setTimeout(()=>URL.revokeObjectURL(url),0);}
function safeFileName(value:string){return value.trim().replace(/[^A-Za-z0-9._-]+/g,'-').replace(/^-+|-+$/g,'')||'inferred-metamodel';}
function plantText(value:string){return value.replace(/[\r\n]+/g,' ').replace(/"/g,"'");}
function xmlText(value:string){return value.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
function xmlAttr(value:string){return xmlText(value).replace(/'/g,'&apos;');}
function svgText(value:string){return xmlText(value).replace(/'/g,'&apos;');}
function round(value:number){return Math.round(value*100)/100;}
