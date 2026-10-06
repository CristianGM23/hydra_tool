import type { CSSProperties } from 'react';
import type { GrammarDefinitionModel, GrammarSymbol } from './types';

export type TextualStyleRole =
    | 'KEYWORD' | 'RULE' | 'TOKEN' | 'REFERENCE' | 'STRING' | 'NUMBER'
    | 'COMMENT' | 'OPERATOR' | 'PUNCTUATION' | 'TYPE' | 'ATTRIBUTE' | 'ENUM_LITERAL';
export type TextualSymbolStyleKind = 'RULE' | 'TERMINAL';
export interface TextualRoleStyle { color:string; bold:boolean; italic:boolean; }
export interface TextualHoverDefinition { title:string; description:string; }
export interface TextualRuntimeHints { literalSymbols:Record<string,string>; identifierTerminal:string; numberTerminal:string; }
export interface GrammarTextualCustomization {
    fontFamily:string;
    fontSize:number;
    lineHeight:number;
    foreground:string;
    background:string;
    keywords:string[];
    roleStyles:Record<TextualStyleRole,TextualRoleStyle>;
    symbolStyles:Record<string,TextualRoleStyle>;
    hovers:Record<string,TextualHoverDefinition>;
    runtimeHints:TextualRuntimeHints;
}

const PREFIX='hydra.textual.';
const roles:TextualStyleRole[]=['KEYWORD','RULE','TOKEN','REFERENCE','STRING','NUMBER','COMMENT','OPERATOR','PUNCTUATION','TYPE','ATTRIBUTE','ENUM_LITERAL'];
const defaults:GrammarTextualCustomization={
    fontFamily:'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    fontSize:14,
    lineHeight:21,
    foreground:'#1f2937',
    background:'#ffffff',
    keywords:[],
    roleStyles:{
        KEYWORD:{color:'#1d4ed8',bold:true,italic:false},
        RULE:{color:'#7c3aed',bold:true,italic:false},
        TOKEN:{color:'#047857',bold:false,italic:false},
        REFERENCE:{color:'#b45309',bold:false,italic:false},
        STRING:{color:'#047857',bold:false,italic:false},
        NUMBER:{color:'#b45309',bold:false,italic:false},
        COMMENT:{color:'#64748b',bold:false,italic:true},
        OPERATOR:{color:'#334155',bold:false,italic:false},
        PUNCTUATION:{color:'#475569',bold:false,italic:false},
        TYPE:{color:'#7c3aed',bold:true,italic:false},
        ATTRIBUTE:{color:'#0369a1',bold:false,italic:false},
        ENUM_LITERAL:{color:'#be123c',bold:false,italic:false},
    },
    symbolStyles:{},
    hovers:{},
    runtimeHints:{literalSymbols:{},identifierTerminal:'',numberTerminal:''},
};

export function defaultTextualCustomization():GrammarTextualCustomization{return structuredClone(defaults);}

export function customizationFromGrammar(model:GrammarDefinitionModel):GrammarTextualCustomization{
    const c=defaultTextualCustomization(),s=model.settings??{};
    c.fontFamily=s[`${PREFIX}fontFamily`]||c.fontFamily;
    c.fontSize=boundedNumber(s[`${PREFIX}fontSize`],11,24,c.fontSize);
    c.lineHeight=boundedNumber(s[`${PREFIX}lineHeight`],c.fontSize,48,c.lineHeight);
    c.foreground=color(s[`${PREFIX}foreground`],c.foreground);
    c.background=color(s[`${PREFIX}background`],c.background);
    c.keywords=(s[`${PREFIX}keywords`]||'').split(',').map(v=>v.trim()).filter(Boolean).slice(0,128);
    for(const role of roles){
        const encoded=s[`${PREFIX}role.${role}`];
        if(encoded)c.roleStyles[role]=decodeStyle(encoded,c.roleStyles[role]);
    }
    for(const [key,value] of Object.entries(s)){
        if(key.startsWith(`${PREFIX}symbol.`)){
            const rest=key.slice(`${PREFIX}symbol.`.length);
            const dot=rest.indexOf('.');
            if(dot<=0)continue;
            const kind=normalizeSymbolKind(rest.slice(0,dot));
            const name=rest.slice(dot+1);
            if(kind!==null&&validSymbolName(name))c.symbolStyles[symbolStyleKey(kind,name)]=decodeStyle(value,c.roleStyles[kind==='RULE'?'RULE':'TOKEN']);
            continue;
        }
        if(!key.startsWith(`${PREFIX}hover.`))continue;
        const rest=key.slice(`${PREFIX}hover.`.length);
        const dot=rest.lastIndexOf('.');
        if(dot<=0)continue;
        const symbol=rest.slice(0,dot),field=rest.slice(dot+1);
        const current=c.hovers[symbol]??{title:'',description:''};
        c.hovers[symbol]=field==='title'?{...current,title:value}:field==='description'?{...current,description:value}:current;
    }
    c.runtimeHints=buildRuntimeHints(model);
    return c;
}

export function applyCustomizationToGrammar(model:GrammarDefinitionModel,c:GrammarTextualCustomization):GrammarDefinitionModel{
    const settings=Object.fromEntries(Object.entries(model.settings??{}).filter(([key])=>!key.startsWith(PREFIX)));
    settings[`${PREFIX}fontFamily`]=c.fontFamily.slice(0,160);
    settings[`${PREFIX}fontSize`]=String(Math.max(11,Math.min(24,c.fontSize)));
    settings[`${PREFIX}lineHeight`]=String(Math.max(c.fontSize,Math.min(48,c.lineHeight)));
    settings[`${PREFIX}foreground`]=color(c.foreground,'#1f2937');
    settings[`${PREFIX}background`]=color(c.background,'#ffffff');
    settings[`${PREFIX}keywords`]=[...new Set(c.keywords.map(v=>v.trim()).filter(v=>/^[A-Za-z_][A-Za-z0-9_-]{0,47}$/.test(v)))].slice(0,128).join(',');
    for(const role of roles)settings[`${PREFIX}role.${role}`]=encodeStyle(c.roleStyles[role]);
    Object.entries(c.symbolStyles).slice(0,256).forEach(([key,style])=>{
        const parsed=parseSymbolStyleKey(key);
        if(parsed===null)return;
        settings[`${PREFIX}symbol.${parsed.kind}.${parsed.name}`]=encodeStyle(style);
    });
    Object.entries(c.hovers).slice(0,128).forEach(([symbol,hover])=>{
        if(!validSymbolName(symbol))return;
        settings[`${PREFIX}hover.${symbol}.title`]=hover.title.slice(0,96);
        settings[`${PREFIX}hover.${symbol}.description`]=hover.description.slice(0,600);
    });
    return {...model,settings};
}

export function inferKeywords(model:GrammarDefinitionModel):string[]{
    const literal=/['"]([A-Za-z_][A-Za-z0-9_-]*)['"]/g;
    const out=new Set<string>();
    for(const doc of model.documents){for(let match=literal.exec(doc.content);match;match=literal.exec(doc.content))out.add(match[1]);}
    return [...out].slice(0,128);
}

/** Combina la personalización persistida con las palabras reservadas inferidas de la gramática actual. */
export function effectiveTextualCustomization(model:GrammarDefinitionModel):GrammarTextualCustomization{
    const persisted=customizationFromGrammar(model);
    const keywords=[...new Set([...persisted.keywords,...inferKeywords(model)])].slice(0,128);
    return {...persisted,keywords};
}

export function customizableSymbols(symbols:readonly GrammarSymbol[]):GrammarSymbol[]{
    return symbols
        .filter(item=>item.kind==='RULE'||item.kind==='TERMINAL'||item.kind==='TOKEN')
        .filter((item,index,array)=>array.findIndex(other=>other.name===item.name&&normalizeSymbolKind(other.kind)===normalizeSymbolKind(item.kind))===index)
        .slice(0,256);
}

export function symbolStyleKey(kind:TextualSymbolStyleKind,name:string):string{return `${kind}:${name}`;}
export function symbolSpecificStyle(c:GrammarTextualCustomization,kind:TextualSymbolStyleKind,name:string):TextualRoleStyle|undefined{return c.symbolStyles[symbolStyleKey(kind,name)];}
export function runtimeSymbolForToken(c:GrammarTextualCustomization,text:string):{kind:TextualSymbolStyleKind;name:string}|null{
    const literal=c.runtimeHints.literalSymbols[text];
    if(literal)return {kind:'RULE',name:literal};
    if(/^[A-Za-z_][A-Za-z0-9_.:-]*$/.test(text)&&c.runtimeHints.identifierTerminal)return {kind:'TERMINAL',name:c.runtimeHints.identifierTerminal};
    if(/^[-+]?\d+(?:\.\d+)?$/.test(text)&&c.runtimeHints.numberTerminal)return {kind:'TERMINAL',name:c.runtimeHints.numberTerminal};
    return null;
}
export function hoverForToken(c:GrammarTextualCustomization,text:string):TextualHoverDefinition|undefined{
    const direct=c.hovers[text];if(direct)return direct;
    const symbol=runtimeSymbolForToken(c,text);return symbol?c.hovers[symbol.name]:undefined;
}
export function styleForToken(c:GrammarTextualCustomization,role:TextualStyleRole,text:string):TextualRoleStyle{
    const symbol=runtimeSymbolForToken(c,text);
    const specific=symbol?c.symbolStyles[symbolStyleKey(symbol.kind,symbol.name)]:undefined;
    return specific??c.symbolStyles[symbolStyleKey('RULE',text)]??c.symbolStyles[symbolStyleKey('TERMINAL',text)]??c.roleStyles[role];
}
export function roleStyle(c:GrammarTextualCustomization,role:TextualStyleRole):CSSProperties{
    const s=c.roleStyles[role];return {color:s.color,fontWeight:s.bold?700:400,fontStyle:s.italic?'italic':'normal'};
}


function buildRuntimeHints(model:GrammarDefinitionModel):TextualRuntimeHints{
    const literalSymbols:Record<string,string>={};
    const terminalNames:string[]=[];
    for(const document of model.documents){
        const source=document.content.replace(/\r\n?/g,'\n');
        const rule=/^(?!\s*(?:terminal|hidden\s+terminal)\b)\s*(?:entry\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:returns\s+[^:]+)?\s*:\s*([\s\S]*?);/gm;
        for(let match=rule.exec(source);match;match=rule.exec(source)){
            const name=match[1],body=match[2];
            const literal=/['"]([^'"\r\n]+)['"]/g;
            for(let token=literal.exec(body);token;token=literal.exec(body)){if(token[1].trim()!=='')literalSymbols[token[1]]=name;}
        }
        const terminal=/(?:^|\n)\s*(?:hidden\s+)?terminal\s+([A-Za-z_][A-Za-z0-9_]*)\s*:/g;
        for(let match=terminal.exec(source);match;match=terminal.exec(source))terminalNames.push(match[1]);
        const antlrLexer=/(?:^|\n)\s*([A-Z][A-Z0-9_]*)\s*:/g;
        for(let match=antlrLexer.exec(source);match;match=antlrLexer.exec(source))terminalNames.push(match[1]);
    }
    const unique=[...new Set(terminalNames)];
    const identifierTerminal=unique.find(name=>/^(ID|IDENTIFIER|IDENT|NAME|WORD)$/i.test(name))??'';
    const numberTerminal=unique.find(name=>/^(INT|INTEGER|NUMBER|NUM|DECIMAL|FLOAT|DOUBLE)$/i.test(name))??'';
    return {literalSymbols,identifierTerminal,numberTerminal};
}

function parseSymbolStyleKey(key:string):{kind:TextualSymbolStyleKind;name:string}|null{
    const colon=key.indexOf(':');if(colon<=0)return null;
    const kind=normalizeSymbolKind(key.slice(0,colon)),name=key.slice(colon+1);
    return kind!==null&&validSymbolName(name)?{kind,name}:null;
}
function normalizeSymbolKind(value:string):TextualSymbolStyleKind|null{return value==='RULE'?'RULE':value==='TERMINAL'||value==='TOKEN'?'TERMINAL':null;}
function encodeStyle(style:TextualRoleStyle):string{return `${color(style.color,'#1f2937')}|${style.bold?'1':'0'}|${style.italic?'1':'0'}`;}
function decodeStyle(value:string,fallback:TextualRoleStyle):TextualRoleStyle{
    const [raw,bold,italic]=value.split('|');return {color:color(raw,fallback.color),bold:bold==='1',italic:italic==='1'};
}
function validSymbolName(value:string):boolean{return /^[A-Za-z_][A-Za-z0-9_.:-]{0,79}$/.test(value);}
function color(value:string|undefined,fallback:string){return value&&/^#[0-9a-fA-F]{6}$/.test(value)?value.toLowerCase():fallback;}
function boundedNumber(value:string|undefined,min:number,max:number,fallback:number){const n=Number(value);return Number.isFinite(n)&&n>=min&&n<=max?Math.round(n):fallback;}
