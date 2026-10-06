import { parseGrammarSyntax, type GrammarSyntaxNode, type GrammarSyntaxRule } from '../grammar-designer/grammarRuleSyntax';
import type { GrammarDefinitionModel } from '../grammar-designer/types';

export type TextualCompletionKind = 'LITERAL'|'TOKEN'|'REFERENCE';

export interface TextualModelCompletion {
    label:string;
    insertText:string;
    kind:TextualCompletionKind;
    ruleName:string;
    propertyName:string;
    expected:string;
    replaceStart:number;
    replaceEnd:number;
}

interface ModelToken { text:string; start:number; end:number; }
interface ExpectedAtom { kind:TextualCompletionKind; value:string; ruleName:string; propertyName:string; }
interface MatchState {
    tokens:readonly ModelToken[];
    rules:ReadonlyMap<string,GrammarSyntaxRule>;
    expected:Map<string,ExpectedAtom>;
    active:Set<string>;
    steps:number;
}

const MAX_MATCH_STEPS=250_000;
const MAX_COMPLETIONS=40;

/**
 * Neutral Ctrl+Space completion engine for textual models. It derives suggestions from the
 * current grammar itself instead of a hard-coded keyword list. The matcher intentionally
 * supports the same portable subset used by HYDRA's neutral textual grammar contract:
 * sequences, alternatives, assignments, parser-rule references and ?, * and + cardinalities.
 */
export function textualModelCompletions(model:GrammarDefinitionModel,text:string,cursorOffset:number):TextualModelCompletion[]{
    const rules=parseGrammarSyntax(model).filter(rule=>rule.parser);
    if(rules.length===0)return [];
    const ruleMap=new Map(rules.map(rule=>[rule.name,rule]));
    const entryName=model.entryRule.trim()||rules[0].name;
    const entry=ruleMap.get(entryName)??rules[0];
    const safeOffset=Math.max(0,Math.min(text.length,cursorOffset));
    const lexical=tokenizeModel(text.slice(0,safeOffset));
    const current=currentPartial(text,safeOffset,lexical);
    const completed=current===null?lexical:lexical.slice(0,-1);
    const expected=expectedAfter(entry,completed,ruleMap);

    let candidates=toCompletions(expected,current?.text??'',current?.start??safeOffset,safeOffset);

    // If the current token is already a complete grammar token, Ctrl+Space should advance to
    // what can legally follow it instead of merely proposing the exact token again.
    if(current!==null&&candidates.some(item=>item.insertText===current.text)){
        const afterCurrent=expectedAfter(entry,lexical,ruleMap);
        const continuation=toCompletions(afterCurrent,'',safeOffset,safeOffset,true);
        if(continuation.length>0)candidates=continuation;
    }else if(current!==null&&candidates.length===0&&tokenAcceptedByAnyExpectation(current.text,expected)){
        const afterCurrent=expectedAfter(entry,lexical,ruleMap);
        const continuation=toCompletions(afterCurrent,'',safeOffset,safeOffset,true);
        if(continuation.length>0)candidates=continuation;
    }
    return candidates.slice(0,MAX_COMPLETIONS);
}

function expectedAfter(entry:GrammarSyntaxRule,tokens:readonly ModelToken[],rules:ReadonlyMap<string,GrammarSyntaxRule>):ExpectedAtom[]{
    const state:MatchState={tokens,rules,expected:new Map(),active:new Set(),steps:0};
    advance(entry.syntax,new Set([0]),state,entry.name,'');
    return [...state.expected.values()];
}

function advance(node:GrammarSyntaxNode,positions:Set<number>,state:MatchState,ruleName:string,propertyName:string):Set<number>{
    if(positions.size===0||state.steps++>MAX_MATCH_STEPS)return new Set();
    switch(node.kind){
        case 'EMPTY': case 'ACTION': case 'OTHER': return new Set(positions);
        case 'ASSIGNMENT': return advance(node.children?.[0]??{kind:'EMPTY'},positions,state,ruleName,assignmentName(node.label)||propertyName);
        case 'LITERAL': return consumeAtom(positions,state,{kind:'LITERAL',value:unquote(node.label??''),ruleName,propertyName});
        case 'TOKEN': return consumeAtom(positions,state,{kind:'TOKEN',value:node.label??'TOKEN',ruleName,propertyName});
        case 'CROSS_REFERENCE': return consumeAtom(positions,state,{kind:'REFERENCE',value:crossReferenceTarget(node.label??''),ruleName,propertyName});
        case 'RULE': return advanceRule(node.label??'',positions,state,propertyName);
        case 'SEQUENCE': {
            let current=new Set(positions);
            for(const child of node.children??[]){current=advance(child,current,state,ruleName,propertyName);if(current.size===0)break;}
            return current;
        }
        case 'CHOICE': {
            const result=new Set<number>();
            for(const child of node.children??[])for(const position of advance(child,new Set(positions),state,ruleName,propertyName))result.add(position);
            return result;
        }
        case 'OPTIONAL': {
            const result=new Set(positions);
            for(const position of advance(node.children?.[0]??{kind:'EMPTY'},new Set(positions),state,ruleName,propertyName))result.add(position);
            return result;
        }
        case 'ZERO_OR_MORE': return repeat(node.children?.[0]??{kind:'EMPTY'},positions,state,ruleName,propertyName,false);
        case 'ONE_OR_MORE': return repeat(node.children?.[0]??{kind:'EMPTY'},positions,state,ruleName,propertyName,true);
    }
}

function advanceRule(name:string,positions:Set<number>,state:MatchState,propertyName:string):Set<number>{
    const rule=state.rules.get(name);
    if(rule===undefined)return consumeAtom(positions,state,{kind:'TOKEN',value:name||'TOKEN',ruleName:name||'Model',propertyName});
    const result=new Set<number>();
    for(const position of positions){
        const key=`${name}@${position}`;
        if(state.active.has(key))continue;
        state.active.add(key);
        try{for(const next of advance(rule.syntax,new Set([position]),state,name,propertyName))result.add(next);}finally{state.active.delete(key);}
    }
    return result;
}

function repeat(child:GrammarSyntaxNode,positions:Set<number>,state:MatchState,ruleName:string,propertyName:string,requireOne:boolean):Set<number>{
    const result=requireOne?new Set<number>():new Set(positions);
    const reached=new Set(positions);
    let frontier=new Set(positions);
    const maxRounds=Math.min(state.tokens.length+2,50_000);
    for(let round=0;round<maxRounds&&frontier.size>0;round++){
        const next=advance(child,frontier,state,ruleName,propertyName);
        const fresh=new Set<number>();
        for(const position of next){
            result.add(position);
            if(!reached.has(position)){reached.add(position);fresh.add(position);}
        }
        frontier=fresh;
    }
    return result;
}

function consumeAtom(positions:Set<number>,state:MatchState,atom:ExpectedAtom):Set<number>{
    const result=new Set<number>();
    for(const position of positions){
        if(position>=state.tokens.length){recordExpected(state,atom);continue;}
        if(matches(atom,state.tokens[position].text))result.add(position+1);
    }
    return result;
}

function recordExpected(state:MatchState,atom:ExpectedAtom):void{
    const key=`${atom.kind}|${atom.value}|${atom.ruleName}|${atom.propertyName}`;
    if(!state.expected.has(key))state.expected.set(key,atom);
}

function matches(atom:ExpectedAtom,value:string):boolean{
    if(atom.kind==='LITERAL')return value===atom.value;
    if(atom.kind==='REFERENCE')return isIdentifier(value);
    return terminalAccepts(atom.value,value);
}

function terminalAccepts(terminal:string,value:string):boolean{
    const normalized=terminal.trim();
    const upper=normalized.toUpperCase();
    if(upper==='ID'||upper.endsWith('.ID'))return isIdentifier(value);
    if(upper.includes('STRING'))return /^(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')$/.test(value);
    if(upper.includes('INT')||upper.includes('NUMBER')||upper.includes('FLOAT')||upper.includes('DECIMAL'))return /^[-+]?\d+(?:\.\d+)?$/.test(value);
    if(upper.includes('BOOL'))return /^(?:true|false)$/i.test(value);
    const regex=regexFromGrammarToken(normalized);if(regex!==null)return regex.test(value);
    return value.length>0&&!/^\s+$/.test(value);
}

function toCompletions(atoms:readonly ExpectedAtom[],prefix:string,replaceStart:number,replaceEnd:number,continuation=false):TextualModelCompletion[]{
    const normalizedPrefix=prefix.toLowerCase();
    const unique=new Map<string,TextualModelCompletion>();
    for(const atom of atoms){
        const insertion=sampleFor(atom);
        const label=labelFor(atom,insertion);
        if(normalizedPrefix!==''&&!insertion.toLowerCase().startsWith(normalizedPrefix)&&!label.toLowerCase().includes(normalizedPrefix))continue;
        const insertText=continuation&&replaceStart===replaceEnd?` ${insertion}`:insertion;
        const item:TextualModelCompletion={label,insertText,kind:atom.kind,ruleName:atom.ruleName,propertyName:atom.propertyName,expected:atom.value,replaceStart,replaceEnd};
        const key=`${atom.kind}|${insertion}|${atom.ruleName}|${atom.propertyName}`;
        if(!unique.has(key))unique.set(key,item);
    }
    return [...unique.values()].sort((a,b)=>completionRank(a)-completionRank(b)||a.label.localeCompare(b.label));
}

function completionRank(item:TextualModelCompletion):number{return item.kind==='LITERAL'?0:item.kind==='REFERENCE'?1:2;}
function labelFor(atom:ExpectedAtom,insertion:string):string{
    if(atom.kind==='LITERAL')return insertion;
    if(atom.propertyName!=='')return `${insertion} · ${atom.propertyName}: ${atom.value}`;
    return `${insertion} · ${atom.value}`;
}
function sampleFor(atom:ExpectedAtom):string{
    if(atom.kind==='LITERAL')return atom.value;
    const property=atom.propertyName||'value';
    const upper=atom.value.toUpperCase();
    if(atom.kind==='REFERENCE')return sampleIdentifier(property||'reference');
    if(upper.includes('STRING'))return `"${property}"`;
    if(upper.includes('INT')||upper.includes('NUMBER')||upper.includes('FLOAT')||upper.includes('DECIMAL'))return '1';
    if(upper.includes('BOOL'))return 'true';
    return sampleIdentifier(property);
}

function tokenizeModel(text:string):ModelToken[]{
    const result:ModelToken[]=[];
    const pattern=/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_][A-Za-z0-9_.:-]*|[-+]?\d+(?:\.\d+)?|[^\s]/g;
    for(const match of text.matchAll(pattern)){const start=match.index??0;result.push({text:match[0],start,end:start+match[0].length});}
    return result;
}
function currentPartial(text:string,cursor:number,tokens:readonly ModelToken[]):ModelToken|null{
    const last=tokens[tokens.length-1];if(last===undefined||last.end!==cursor)return null;
    if(cursor<text.length&&!/\s/.test(text[cursor]??''))return last;
    if(cursor>0&&/\s/.test(text[cursor-1]??''))return null;
    return last;
}
function tokenAcceptedByAnyExpectation(value:string,atoms:readonly ExpectedAtom[]):boolean{return atoms.some(atom=>matches(atom,value));}
function assignmentName(label:string|undefined):string{return (label??'').replace(/\s*(?:\+=|\?=|=)\s*$/,'').trim();}
function crossReferenceTarget(label:string):string{const cleaned=label.replace(/^\[/,'').replace(/\]$/,'');const pipe=cleaned.indexOf('|');return (pipe>=0?cleaned.slice(0,pipe):cleaned).trim()||'ID';}
function unquote(value:string):string{return value.length>=2&&((value.startsWith("'")&&value.endsWith("'"))||(value.startsWith('"')&&value.endsWith('"')))?value.slice(1,-1).replace(/\\(['"\\])/g,'$1'):value;}
function isIdentifier(value:string):boolean{return /^[A-Za-z_][A-Za-z0-9_.:-]*$/.test(value);}
function sampleIdentifier(value:string):string{const cleaned=value.replace(/[^A-Za-z0-9_]/g,'').replace(/^[^A-Za-z_]+/,'');return `${cleaned||'value'}1`;}
function regexFromGrammarToken(value:string):RegExp|null{
    if(!value.startsWith('/'))return null;
    const last=value.lastIndexOf('/');if(last<=0)return null;
    try{return new RegExp(`^(?:${value.slice(1,last)})$`,value.slice(last+1).replace(/[gy]/g,''));}catch{return null;}
}
