import type { GrammarDefinitionModel, GrammarTechnology } from './types';

interface RuleSource { name:string; body:string; entry:boolean; }

type Token = { kind:'LITERAL'|'IDENT'|'OP'; value:string };

/**
 * Builds a compact example directly from the current grammar draft. The preview deliberately
 * reads model.documents on every model change; it never reuses a compilation snapshot.
 */
export function buildGrammarTextualExample(model:GrammarDefinitionModel):string {
    const rules=extractParserRules(model);
    if(rules.length===0)return model.technology==='ANTLR'?'// Define a parser rule to preview an example.':'// Define a parser rule to preview an example.';
    const byName=new Map(rules.map(rule=>[rule.name,rule]));
    const entry=model.entryRule.trim()||rules.find(rule=>rule.entry)?.name||rules[0].name;
    const generated=generateRule(entry,byName,new Set(),0).trim();
    return generated===''?`// ${entry}`:generated;
}

function extractParserRules(model:GrammarDefinitionModel):RuleSource[]{
    const rules:RuleSource[]=[];
    for(const document of model.documents){
        rules.push(...extractDocumentRules(document.content,model.technology));
    }
    return rules;
}

function extractDocumentRules(source:string,technology:GrammarTechnology):RuleSource[]{
    const header=technology==='ANTLR'
        ? /^\s*(fragment\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*:/gm
        : /^\s*(entry\s+)?(?:(hidden)\s+)?(?:(terminal|fragment)\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:returns\s+[^:]+)?\s*:/gm;
    const result:RuleSource[]=[];
    for(let match=header.exec(source);match;match=header.exec(source)){
        const name=technology==='ANTLR'?(match[2]??''):(match[4]??'');
        const entry=technology==='ANTLR'?false:(match[1]??'')!=='';
        const terminal=technology==='ANTLR'
            ?(match[1]??'')!==''||/^[A-Z]/.test(name)
            :(match[2]??'')!==''||(match[3]??'')!=='';
        const bodyStart=header.lastIndex;
        const bodyEnd=findRuleEnd(source,bodyStart);
        if(!terminal&&name!=='')result.push({name,body:source.slice(bodyStart,bodyEnd),entry});
        header.lastIndex=bodyEnd<source.length?bodyEnd+1:source.length;
    }
    return result;
}

function findRuleEnd(source:string,start:number):number{
    let quote='';let escaped=false;let bracketDepth=0;
    for(let index=start;index<source.length;index++){
        const char=source[index];
        if(quote!==''){
            if(escaped){escaped=false;continue;}
            if(char==='\\'){escaped=true;continue;}
            if(char===quote)quote='';
            continue;
        }
        if(char==='\''||char==='"'){quote=char;continue;}
        if(char==='['||char==='('||char==='{')bracketDepth++;
        else if(char===']'||char===')'||char==='}')bracketDepth=Math.max(0,bracketDepth-1);
        else if(char===';'&&bracketDepth===0)return index;
    }
    return source.length;
}

function tokenize(body:string):Token[]{
    const result:Token[]=[];
    const pattern=/'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"|\+=|\?=|=>|->|[A-Za-z_][A-Za-z0-9_]*|[|()*+?=[\],.:]/g;
    for(const match of body.matchAll(pattern)){
        const value=match[0];
        if(value.startsWith("'")||value.startsWith('"'))result.push({kind:'LITERAL',value:unquote(value)});
        else if(/^[A-Za-z_]/.test(value))result.push({kind:'IDENT',value});
        else result.push({kind:'OP',value});
    }
    return result;
}

function generateRule(name:string,rules:Map<string,RuleSource>,visiting:Set<string>,depth:number):string{
    if(depth>12||visiting.has(name))return sampleIdentifier(name);
    const rule=rules.get(name);if(rule===undefined)return sampleTerminal(name,'value');
    const nextVisiting=new Set(visiting);nextVisiting.add(name);
    const tokens=tokenize(rule.body);
    const generated=generateAlternative(tokens,0,tokens.length,rules,nextVisiting,depth+1);
    return normalizeWhitespace(generated);
}

function generateAlternative(tokens:Token[],start:number,end:number,rules:Map<string,RuleSource>,visiting:Set<string>,depth:number):string{
    const pipe=findTopLevel(tokens,start,end,'|');
    return generateSequence(tokens,start,pipe<0?end:pipe,rules,visiting,depth);
}

function generateSequence(tokens:Token[],start:number,end:number,rules:Map<string,RuleSource>,visiting:Set<string>,depth:number):string{
    const parts:string[]=[];let index=start;
    while(index<end){
        const token=tokens[index];
        if(token.kind==='OP'&&[',','.'].includes(token.value)){index++;continue;}
        if(token.kind==='OP'&&token.value==='('){
            const close=findMatching(tokens,index,end,'(',')');
            if(close<0)break;
            const nested=generateAlternative(tokens,index+1,close,rules,visiting,depth);
            if(nested!=='')parts.push(nested);
            index=skipCardinality(tokens,close+1,end);continue;
        }
        if(token.kind==='OP'&&token.value==='['){
            const close=findMatching(tokens,index,end,'[',']');
            if(close<0)break;
            const propertyHint=nearestAssignmentProperty(tokens,start,index)??'reference';
            parts.push(sampleIdentifier(propertyHint));
            index=skipCardinality(tokens,close+1,end);continue;
        }
        if(token.kind==='LITERAL'){
            parts.push(token.value);index=skipCardinality(tokens,index+1,end);continue;
        }
        if(token.kind==='IDENT'){
            const assignment=tokens[index+1];
            if(assignment?.kind==='OP'&&['=','+=','?='].includes(assignment.value)){
                const property=token.value;
                const valueToken=tokens[index+2];
                if(valueToken===undefined)break;
                if(valueToken.kind==='IDENT')parts.push(rules.has(valueToken.value)?generateRule(valueToken.value,rules,visiting,depth):sampleTerminal(valueToken.value,property));
                else if(valueToken.kind==='LITERAL')parts.push(valueToken.value);
                else if(valueToken.value==='['){
                    const close=findMatching(tokens,index+2,end,'[',']');parts.push(sampleIdentifier(property));index=skipCardinality(tokens,close<0?index+3:close+1,end);continue;
                }
                index=skipCardinality(tokens,index+3,end);continue;
            }
            // Ignore grammar-only keywords/actions. A parser-rule reference is useful in the sample.
            if(rules.has(token.value))parts.push(generateRule(token.value,rules,visiting,depth));
            else if(!['returns','infer','current','EOF'].includes(token.value))parts.push(sampleTerminal(token.value,'value'));
            index=skipCardinality(tokens,index+1,end);continue;
        }
        index++;
    }
    return parts.filter(Boolean).join(' ');
}

function findTopLevel(tokens:Token[],start:number,end:number,value:string):number{
    let round=0,square=0;
    for(let index=start;index<end;index++){
        const token=tokens[index];if(token.kind!=='OP')continue;
        if(token.value==='(')round++;else if(token.value===')')round=Math.max(0,round-1);
        else if(token.value==='[')square++;else if(token.value===']')square=Math.max(0,square-1);
        else if(token.value===value&&round===0&&square===0)return index;
    }
    return -1;
}
function findMatching(tokens:Token[],start:number,end:number,open:string,close:string):number{
    let depth=0;for(let index=start;index<end;index++){const token=tokens[index];if(token.kind!=='OP')continue;if(token.value===open)depth++;else if(token.value===close&&--depth===0)return index;}return -1;
}
function skipCardinality(tokens:Token[],index:number,end:number):number{return index<end&&tokens[index].kind==='OP'&&['?','*','+'].includes(tokens[index].value)?index+1:index;}
function nearestAssignmentProperty(tokens:Token[],start:number,index:number):string|undefined{for(let current=index-1;current>=start;current--){if(tokens[current].kind==='IDENT'&&tokens[current+1]?.kind==='OP'&&['=','+=','?='].includes(tokens[current+1].value))return tokens[current].value;}return undefined;}
function sampleTerminal(terminal:string,property:string):string{
    const upper=terminal.toUpperCase();
    if(upper.includes('STRING'))return `"${property||'text'}"`;
    if(upper.includes('INT')||upper.includes('NUMBER')||upper.includes('FLOAT')||upper.includes('DECIMAL'))return '1';
    if(upper.includes('BOOL'))return 'true';
    return sampleIdentifier(property||terminal);
}
function sampleIdentifier(value:string):string{const normalized=value.replace(/[^A-Za-z0-9_]/g,'').replace(/^[^A-Za-z_]+/,'');return `${normalized||'value'}1`;}
function unquote(value:string):string{return value.length>=2?value.slice(1,-1).replace(/\\(['"\\])/g,'$1'):value;}
function normalizeWhitespace(value:string):string{return value.replace(/\s+/g,' ').trim();}
