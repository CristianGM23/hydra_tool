import { Fragment, useMemo, useState, type ReactNode } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { parseGrammarSyntax, type GrammarSyntaxNode } from './grammarRuleSyntax';
import type { GrammarDefinitionModel, TextRange } from './types';

interface Props { model:GrammarDefinitionModel; unusedRules:ReadonlySet<string>; onNavigate:(documentId:string,range:TextRange)=>void; }
interface Size {width:number;height:number;entryY:number;exitY:number;}
const GAP=28,BRANCH_GAP=18;

export function GrammarRuleSyntaxGraphs({model,unusedRules,onNavigate}:Props){
    const {t}=useHydraLanguage();
    const rules=useMemo(()=>parseGrammarSyntax(model),[model]);
    const [mode,setMode]=useState<'ALL'|'ONE'>('ALL');
    const [selected,setSelected]=useState('');
    const [filter,setFilter]=useState('');
    const [selectedInfo,setSelectedInfo]=useState('');
    const [hoveredInfo,setHoveredInfo]=useState('');
    const selectedName=selected!==''&&rules.some(rule=>rule.name===selected)?selected:rules[0]?.name??'';
    const visible=mode==='ONE'?rules.filter(rule=>rule.name===selectedName):rules.filter(rule=>rule.name.toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()));
    const infoRule=rules.find(rule=>rule.name===(hoveredInfo||selectedInfo));
    return <section className="grammar-rule-graphs" aria-label={t('Syntax graphs of grammar rules')}>
        <header className="grammar-rule-graphs-toolbar">
            <div><strong>{t('Syntax graphs of grammar rules')}</strong><p>{t('Explore every possible path through each grammar rule: sequences, alternatives, literals, references, optional elements and loops.')}</p></div>
            <div className="grammar-rule-graph-controls">
                <label><span>{t('View')}</span><select value={mode} onChange={event=>setMode(event.target.value as 'ALL'|'ONE')}><option value="ALL">{t('All rules')}</option><option value="ONE">{t('One rule')}</option></select></label>
                {mode==='ONE'?<label><span>{t('Rule')}</span><select value={selectedName} onChange={event=>setSelected(event.target.value)}>{rules.map(rule=><option key={`${rule.documentId}:${rule.name}`} value={rule.name}>{rule.name}</option>)}</select></label>:<label><span>{t('Filter rules')}</span><input value={filter} onChange={event=>setFilter(event.target.value)} placeholder={t('Rule name')}/></label>}
            </div>
        </header>
        {infoRule&&<div className="grammar-rule-context" role="status"><strong>{infoRule.name}</strong><span>{infoRule.parser?t('Parser rule'):t('Terminal / lexical token')}</span><span>{infoRule.parser?t('Recognizes a language construct and can contribute semantic structure.'):t('Recognizes atomic text used by parser rules.')}</span><span>{t('Line')} {infoRule.range.start.line+1}</span></div>}
        {visible.length===0?<p className="grammar-rule-graphs-empty">{t('No grammar rules match the current selection.')}</p>:<div className="grammar-rule-graphs-list">{visible.map(rule=>{
            const unused=rule.parser&&unusedRules.has(rule.name);
            const selectedInfoRule=selectedInfo===rule.name;
            return <article className={`grammar-rule-graph-card${unused?' unused':''}${selectedInfoRule?' selected':''}`} key={`${rule.documentId}:${rule.name}`} onMouseEnter={()=>setHoveredInfo(rule.name)} onMouseLeave={()=>setHoveredInfo('')} onClick={()=>setSelectedInfo(rule.name)} title={`${rule.name} — ${rule.parser?t('Parser rule'):t('Terminal / lexical token')}`}>
                <header><div><strong>{rule.name}</strong><span>{rule.parser?t('Parser rule'):t('Terminal / lexical token')}</span>{unused&&<span className="grammar-unused-rule-badge">{t('Not used')}</span>}</div><HydraButton type="button" onClick={()=>onNavigate(rule.documentId,rule.range)}>{t('Go to source')}</HydraButton></header>
                {unused&&<p className="grammar-unused-rule-help">{t('This rule is not reachable from any effective grammar entry, so it does not currently participate in the language paths.')}</p>}
                <RailroadDiagram syntax={rule.syntax} label={rule.name}/>
            </article>;
        })}</div>}
    </section>;
}

function RailroadDiagram({syntax,label}:{syntax:GrammarSyntaxNode;label:string}){
    const size=measure(syntax);const width=Math.max(180,size.width+48),height=Math.max(70,size.height+32);let serial=0;
    const elements:ReactNode[]=[];
    elements.push(<line key="entry" className="grammar-rail-line" x1={4} y1={16+size.entryY} x2={24} y2={16+size.entryY}/>);
    draw(syntax,24,16,elements,()=>`${label}-${serial++}`);
    elements.push(<line key="exit" className="grammar-rail-line" x1={24+size.width} y1={16+size.exitY} x2={width-4} y2={16+size.exitY}/>);
    elements.push(<circle key="start" className="grammar-rail-terminal" cx={4} cy={16+size.entryY} r={3}/>);
    elements.push(<circle key="end" className="grammar-rail-terminal" cx={width-4} cy={16+size.exitY} r={3}/>);
    return <div className="grammar-railroad-scroll" tabIndex={0} aria-label={label}><svg className="grammar-railroad" width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={label}>{elements}</svg></div>;
}

function measure(node:GrammarSyntaxNode):Size{
    const children=node.children??[];
    if(node.kind==='SEQUENCE'){
        const sizes=children.map(measure);const h=Math.max(38,...sizes.map(s=>s.height));return {width:sizes.reduce((sum,s)=>sum+s.width,0)+Math.max(0,sizes.length-1)*GAP,height:h,entryY:h/2,exitY:h/2};
    }
    if(node.kind==='CHOICE'){
        const sizes=children.map(measure);const max=Math.max(80,...sizes.map(s=>s.width));const height=sizes.reduce((sum,s)=>sum+s.height,0)+Math.max(0,sizes.length-1)*BRANCH_GAP;return {width:max+72,height,entryY:height/2,exitY:height/2};
    }
    if(['OPTIONAL','ZERO_OR_MORE','ONE_OR_MORE'].includes(node.kind)){
        const child=measure(children[0]??{kind:'EMPTY'});const extra=node.kind==='OPTIONAL'?24:38;return {width:child.width+44,height:child.height+extra,entryY:child.height/2+(node.kind==='OPTIONAL'?24:0),exitY:child.height/2+(node.kind==='OPTIONAL'?24:0)};
    }
    if(node.kind==='ASSIGNMENT'&&children[0]){const child=measure(children[0]);const own=leafWidth(node.label??'');return {width:own+GAP+child.width,height:Math.max(38,child.height),entryY:Math.max(38,child.height)/2,exitY:Math.max(38,child.height)/2};}
    const w=leafWidth(node.label??(node.kind==='EMPTY'?'ε':node.kind));return {width:w,height:38,entryY:19,exitY:19};
}
function leafWidth(label:string){return Math.max(64,Math.min(280,label.length*7+28));}
function draw(node:GrammarSyntaxNode,x:number,y:number,out:ReactNode[],key:()=>string):Size{
    const size=measure(node),children=node.children??[];
    if(node.kind==='SEQUENCE'){
        let cursor=x;for(const child of children){const cs=measure(child),cy=y+(size.height-cs.height)/2;draw(child,cursor,cy,out,key);cursor+=cs.width;if(cursor<x+size.width){out.push(<line key={key()} className="grammar-rail-line" x1={cursor} y1={y+size.entryY} x2={cursor+GAP} y2={y+size.entryY}/>);cursor+=GAP;}}return size;
    }
    if(node.kind==='CHOICE'){
        const branchX=x+36,mergeX=x+size.width;let by=y;for(const child of children){const cs=measure(child),cy=by;const branchY=cy+cs.entryY;out.push(<path key={key()} className="grammar-rail-line" d={`M ${x} ${y+size.entryY} H ${x+16} L ${branchX} ${branchY}`}/>);draw(child,branchX,cy,out,key);out.push(<path key={key()} className="grammar-rail-line" d={`M ${branchX+cs.width} ${cy+cs.exitY} L ${mergeX-16} ${y+size.exitY} H ${mergeX}`}/>);by+=cs.height+BRANCH_GAP;}return size;
    }
    if(['OPTIONAL','ZERO_OR_MORE','ONE_OR_MORE'].includes(node.kind)){
        const child=children[0]??{kind:'EMPTY'};const cs=measure(child);const childY=node.kind==='OPTIONAL'?y+24:y;const centerY=childY+cs.entryY;const childX=x+22;
        out.push(<line key={key()} className="grammar-rail-line" x1={x} y1={y+size.entryY} x2={childX} y2={centerY}/>);draw(child,childX,childY,out,key);out.push(<line key={key()} className="grammar-rail-line" x1={childX+cs.width} y1={centerY} x2={x+size.width} y2={y+size.exitY}/>);
        if(node.kind==='OPTIONAL'||node.kind==='ZERO_OR_MORE'){const bypassY=y+6;out.push(<path key={key()} className="grammar-rail-bypass" d={`M ${x} ${y+size.entryY} V ${bypassY} H ${x+size.width} V ${y+size.exitY}`}/>);}
        if(node.kind==='ZERO_OR_MORE'||node.kind==='ONE_OR_MORE'){const loopY=y+size.height-6;out.push(<path key={key()} className="grammar-rail-loop" d={`M ${childX+cs.width} ${centerY} V ${loopY} H ${childX} V ${centerY}`}/>);out.push(<text key={key()} className="grammar-rail-quantifier" x={x+size.width/2} y={loopY-3}>{node.kind==='ZERO_OR_MORE'?'0..*':'1..*'}</text>);}
        return size;
    }
    if(node.kind==='ASSIGNMENT'&&children[0]){const labelSize={...size,width:leafWidth(node.label??'')};const center=y+size.entryY;drawLeaf(node,x,y+(size.height-38)/2,out,key);const nextX=x+labelSize.width+GAP;out.push(<line key={key()} className="grammar-rail-line" x1={x+labelSize.width} y1={center} x2={nextX} y2={center}/>);draw(children[0],nextX,y+(size.height-measure(children[0]).height)/2,out,key);return size;}
    drawLeaf(node,x,y,out,key);return size;
}
function drawLeaf(node:GrammarSyntaxNode,x:number,y:number,out:ReactNode[],key:()=>string){const width=leafWidth(node.label??(node.kind==='EMPTY'?'ε':node.kind));const cls=`grammar-rail-node kind-${node.kind.toLowerCase()}`;out.push(<Fragment key={key()}><rect className={cls} x={x} y={y} width={width} height={38} rx={node.kind==='LITERAL'?18:6}/><text className="grammar-rail-label" x={x+width/2} y={y+24} textAnchor="middle">{clip(node.label??(node.kind==='EMPTY'?'ε':node.kind),36)}</text></Fragment>);}
function clip(value:string,max:number){return value.length<=max?value:`${value.slice(0,max-1)}…`;}
