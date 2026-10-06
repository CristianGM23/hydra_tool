import { useMemo, useState } from 'react';
import { useHydraLanguage } from '../i18n';
import type { GrammarSymbol } from './types';
import { HydraButton, HydraTooltip } from '../design-system';
const order=['GRAMMAR','DOCUMENT','IMPORT','RULE','TOKEN','ASSIGNMENT','CROSS_REFERENCE'];
export function GrammarStructureTree({symbols,unusedRules,documentId,onSelect}:{symbols:readonly GrammarSymbol[];unusedRules:ReadonlySet<string>;documentId:string;onSelect:(symbol:GrammarSymbol)=>void}){
    const {t}=useHydraLanguage();
    const visible=useMemo(()=>symbols.filter(item=>(item.documentId===documentId||item.kind==='GRAMMAR')&&item.kind!=='ALTERNATIVE').sort((a,b)=>order.indexOf(a.kind)-order.indexOf(b.kind)||a.range.start.line-b.range.start.line||a.range.start.column-b.range.start.column),[documentId,symbols]);
    const [selectedId,setSelectedId]=useState('');
    const [hoveredId,setHoveredId]=useState('');
    const contextual=visible.find(item=>item.id===(hoveredId||selectedId));
    return <div className="grammar-structure-tree">
        {contextual&&<div className="grammar-symbol-inspector" role="status" aria-live="polite">
            <strong>{contextual.name}</strong><span>{t(kindLabel(contextual.kind))}</span><span>{t(kindDescription(contextual.kind))}</span><span>{contextual.range.start.line+1}:{contextual.range.start.column+1}</span>
        </div>}
        {visible.length===0?<p>{t('The structure will appear automatically while you edit.')}</p>:visible.map(symbol=>{const unused=symbol.kind==='RULE'&&unusedRules.has(symbol.name);const selected=selectedId===symbol.id;return <div className={`grammar-symbol-row kind-${symbol.kind.toLowerCase()}${unused?' unused-rule':''}${selected?' selected':''}`} key={symbol.id} onMouseEnter={()=>setHoveredId(symbol.id)} onMouseLeave={()=>setHoveredId('')}>
        <HydraButton type="button" className="grammar-symbol-main" onFocus={()=>setHoveredId(symbol.id)} onBlur={()=>setHoveredId('')} onClick={()=>{setSelectedId(symbol.id);onSelect(symbol);}}>
            <span className="grammar-symbol-copy"><strong>{symbol.name}</strong><small>{symbol.detail===''?t(kindDescription(symbol.kind)):t(symbol.detail)}</small></span>
            <span className="grammar-symbol-kind">{unused?t('Not used'):t(kindLabel(symbol.kind))}</span>
            <span className="grammar-symbol-location">{symbol.range.start.line+1}:{symbol.range.start.column+1}</span>
        </HydraButton>
        <HydraTooltip label={`${t('Information about')} ${symbol.name}`} title={t(kindLabel(symbol.kind))} resizable={false}><p>{t(kindDescription(symbol.kind))}</p></HydraTooltip>
    </div>})}</div>;
}
function kindLabel(kind:string):string{return kind==='GRAMMAR'?'Grammar':kind==='DOCUMENT'?'Grammar document':kind==='IMPORT'?'Import':kind==='RULE'?'Parser rule':kind==='TOKEN'?'Terminal or token':kind==='ALTERNATIVE'?'Alternative':kind==='ASSIGNMENT'?'Assignment':kind==='CROSS_REFERENCE'?'Cross-reference':kind;}
function kindDescription(kind:string):string{return kind==='GRAMMAR'?'Top-level language definition and semantic-analysis root.':kind==='DOCUMENT'?'Source document that participates in the grammar.':kind==='IMPORT'?'Dependency on another grammar or reusable language fragment.':kind==='RULE'?'Parser rule that recognizes a language construct and can contribute semantic structure.':kind==='TOKEN'?'Lexical rule or terminal used to recognize atomic text.':kind==='ALTERNATIVE'?'One possible branch accepted by a grammar rule.':kind==='ASSIGNMENT'?'Maps parsed text to a property of the semantic object produced by the rule.':kind==='CROSS_REFERENCE'?'Reference to another semantic element resolved through name, type and scope.':'Grammar structure element.';}
