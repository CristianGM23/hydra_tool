import type { GrammarDefinitionModel, GrammarDiagnostic, GrammarSourceDocument, GrammarSymbol, TextPosition, TextRange } from './types';

export type GrammarLocalEntryInferenceMethod = 'EXPLICIT_ENTRY'|'XTEXT_FIRST_PARSER_RULE'|'UNREFERENCED_ENTRY_DOCUMENT_ROOT'|'UNREFERENCED_ROOT'|'FIRST_PARSER_RULE'|'NONE';

export interface GrammarLocalInspection {
    parserRules: string[];
    inferredEntryRule: string;
    inferenceMethod: GrammarLocalEntryInferenceMethod;
    confidence: number;
    reason: string;
    symbols: GrammarSymbol[];
    unusedRules: string[];
    diagnostics: GrammarDiagnostic[];
    truncated: boolean;
}

const MAX_LOCAL_SYMBOLS = 2_000;
const MAX_LOCAL_DIAGNOSTICS = 250;
const IDENTIFIER = /[A-Za-z_][A-Za-z0-9_]*/g;
const KEYWORDS = new Set(['grammar','entry','returns','infer','hidden','terminal','fragment','import','with','as','current','true','false','EOF','generate','enum','interface','extends']);
const BUILTIN_REFERENCES = new Set(['ID','INT','INTEGER','NUMBER','FLOAT','DOUBLE','DECIMAL','STRING','BOOLEAN','BOOL','WS','ANY_OTHER','ANY','EOF','String','Number','Boolean']);

/**
 * Fast, deterministic inspection used while canonical backend analysis is pending.
 * It never replaces semantic analysis. Local diagnostics remain useful after a backend
 * response arrives because they cover the exact source currently visible in the browser.
 */
export function inspectGrammarLocally(model: GrammarDefinitionModel): GrammarLocalInspection {
    const rules: RuleInfo[] = [];
    const symbols: GrammarSymbol[] = [];
    const diagnostics: GrammarDiagnostic[] = [];
    let truncated = false;
    const pushSymbol = (value: GrammarSymbol) => {
        if (symbols.length >= MAX_LOCAL_SYMBOLS) { truncated = true; return; }
        symbols.push(value);
    };
    const pushDiagnostic=(value:GrammarDiagnostic)=>{
        if(diagnostics.length>=MAX_LOCAL_DIAGNOSTICS){truncated=true;return;}
        diagnostics.push(value);
    };

    for (const document of model.documents) {
        const source = document.content ?? '';
        const masked = maskGrammar(source, model.technology);
        const index = createLineIndex(source);
        pushSymbol(symbol(`document:${document.id}`, document.relativePath || document.id, 'DOCUMENT', document, rangeAt(index, 0, Math.max(0, source.length)), '', 'Grammar source document'));
        collectImports(document, source, index, pushSymbol);
        const documentRules = collectRules(model, document, masked, index, pushSymbol);
        rules.push(...documentRules);
        collectDelimiterDiagnostics(document,masked,index,pushDiagnostic);
        collectMalformedHeaderDiagnostics(document,masked,index,pushDiagnostic);
        collectRuleShapeDiagnostics(document,documentRules,pushDiagnostic);
        collectUnexpectedTopLevelText(model,document,masked,index,documentRules,pushDiagnostic);
    }

    const allRuleNames=new Set(rules.map(rule=>rule.name));
    const parserNames = unique(rules.filter(rule => rule.parser).map(rule => rule.name));
    const parserSet = new Set(parserNames);
    for (const rule of rules.filter(item => item.parser)) {
        const refs = references(rule.bodyMasked, parserSet, rule.name);
        rule.references = refs;
        collectFeatures(rule, model.documents.find(document => document.id === rule.documentId), pushSymbol);
        collectUndefinedReferenceDiagnostics(rule,allRuleNames,model.documents.find(document=>document.id===rule.documentId),pushDiagnostic);
    }

    const entryDocument = (model.documents.find(document => document.id === model.entryDocumentId) ?? model.documents.find(document => document.entryPoint))?.id ?? '';
    const explicit = rules.find(rule => rule.parser && rule.explicitEntry);
    let inferredEntryRule='';
    let inferenceMethod:GrammarLocalEntryInferenceMethod='NONE';
    let confidence=0;
    let reason='No parser rule has been detected yet.';
    if(explicit){
        inferredEntryRule=explicit.name;inferenceMethod='EXPLICIT_ENTRY';confidence=1;reason='The grammar explicitly marks this parser rule as the entry point.';
    }else if(model.technology==='XTEXT'&&parserNames.length>0){
        inferredEntryRule=parserNames[0];inferenceMethod='XTEXT_FIRST_PARSER_RULE';confidence=.99;reason='Xtext uses the first parser rule as the document entry rule when no explicit HYDRA override is selected.';
    }else{
        const referenced = new Set(rules.flatMap(rule => rule.references));
        const unreferenced = rules.filter(rule => rule.parser && !referenced.has(rule.name));
        const inEntryDocument = unreferenced.filter(rule => entryDocument !== '' && rule.documentId === entryDocument);
        if(inEntryDocument.length===1){inferredEntryRule=inEntryDocument[0].name;inferenceMethod='UNREFERENCED_ENTRY_DOCUMENT_ROOT';confidence=.96;reason='This is the only parser root not referenced by another parser rule in the entry document.';}
        else if(unreferenced.length===1){inferredEntryRule=unreferenced[0].name;inferenceMethod='UNREFERENCED_ROOT';confidence=.94;reason='This is the only parser root not referenced by another parser rule.';}
        else if(parserNames.length>0){inferredEntryRule=parserNames[0];inferenceMethod='FIRST_PARSER_RULE';confidence=.70;reason='Several roots are possible; HYDRA provisionally selects the first parser rule until backend analysis completes.';}
    }

    const selected=model.entryRule.trim();
    const manual=(model.annotations['hydra.entryRule.mode']??'AUTO')==='MANUAL';
    if(parserNames.length===0){
        const document=model.documents.find(item=>item.id===model.entryDocumentId)??model.documents[0];
        if(document)pushDiagnostic(diag('GRAMMAR_LOCAL_NO_PARSER_RULE','ERROR','No se ha detectado ninguna regla de análisis','HYDRA no encuentra ninguna regla de análisis capaz de reconocer un modelo.','El borrador actual no contiene una declaración completa de regla de análisis.','La compilación y el editor de modelos textuales no pueden establecer un punto de entrada del lenguaje.','Añade o completa al menos una regla de análisis.',document,rangeAt(createLineIndex(document.content),0,Math.min(1,document.content.length))));
    }else if(manual&&selected!==''&&!parserSet.has(selected)){
        const document=model.documents.find(item=>item.id===model.entryDocumentId)??model.documents[0];
        if(document)pushDiagnostic(diag('GRAMMAR_LOCAL_ENTRY_RULE_NOT_FOUND','ERROR','La regla de entrada seleccionada no existe',`La regla de entrada seleccionada manualmente "${selected}" no está declarada como regla de análisis.`,'La selección manual apunta a una regla que no existe en el borrador actual.','HYDRA no puede validar ni compilar el lenguaje utilizando ese punto de entrada.','Selecciona una regla existente o vuelve al modo automático de selección de entrada.',document,rangeAt(createLineIndex(document.content),0,Math.min(1,document.content.length)),selected,{entryRule:selected,provisional:'true'}));
    }

    const effectiveEntry=manual&&parserSet.has(selected)?selected:inferredEntryRule;
    if(effectiveEntry!==''){
        const entryRule=rules.find(rule=>rule.parser&&rule.name===effectiveEntry);
        const document=entryRule?model.documents.find(item=>item.id===entryRule.documentId):undefined;
        if(entryRule&&document)pushDiagnostic(diag('GRAMMAR_EFFECTIVE_ENTRY_RULE','INFO','Regla de entrada efectiva',`HYDRA está utilizando "${effectiveEntry}" como regla de entrada efectiva de este borrador.`,'La regla se ha seleccionado a partir de la configuración explícita, manual o inferida del punto de entrada.','Este mensaje es informativo y no bloquea la compilación.','No es necesario realizar ninguna acción salvo que se quiera utilizar otro punto de entrada.',document,rangeAt(entryRule.index,entryRule.nameOffset,entryRule.name.length),effectiveEntry,{entryRule:effectiveEntry,method:manual?'MANUAL':inferenceMethod,provisional:'true'}));
    }

    const inspection=result(parserNames,inferredEntryRule,inferenceMethod,confidence,reason,symbols,diagnostics,truncated);
    return finalize(inspection,model,rules);
}

interface RuleInfo {
    name: string;
    documentId: string;
    parser: boolean;
    explicitEntry: boolean;
    body: string;
    bodyMasked: string;
    bodyOffset: number;
    endOffset:number;
    nameOffset:number;
    terminated:boolean;
    references: string[];
    index: LineIndex;
}

type PushSymbol = (value: GrammarSymbol) => void;
type PushDiagnostic = (value:GrammarDiagnostic)=>void;
interface LineIndex { starts: number[]; sourceLength: number; }

function collectRules(model: GrammarDefinitionModel, document: GrammarSourceDocument, masked: string, index: LineIndex, push: PushSymbol): RuleInfo[] {
    const rules: RuleInfo[] = [];
    const pattern = model.technology === 'ANTLR'
        ? /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?:\[[^\]]*\]\s*)?(?:returns\s*\[[^\]]*\]\s*)?(?:locals\s*\[[^\]]*\]\s*)?:/gm
        : /^\s*(?:(entry)\s+)?(?:(hidden)\s+)?(?:(terminal|fragment)\s+)?([A-Za-z_][A-Za-z0-9_]*)\b[^\r\n:;]*:/gm;
    const headers:Array<{match:RegExpExecArray;name:string;terminal:boolean;parser:boolean;explicitEntry:boolean;nameOffset:number;colonOffset:number}>=[];
    let match:RegExpExecArray|null;
    while((match=pattern.exec(masked))!==null){
        const name=model.technology==='ANTLR'?match[1]:match[4];
        const terminal=model.technology==='ANTLR'?/^[A-Z]/.test(name):Boolean(match[3]);
        const parser=!terminal&&(model.technology!=='ANTLR'||/^[a-z]/.test(name));
        const explicitEntry=model.technology!=='ANTLR'&&Boolean(match[1]);
        headers.push({match,name,terminal,parser,explicitEntry,nameOffset:match.index+match[0].lastIndexOf(name),colonOffset:match.index+match[0].lastIndexOf(':')});
    }
    headers.forEach((header,headerIndex)=>{
        const nextHeader=headers[headerIndex+1]?.match.index??masked.length;
        const semicolon=masked.indexOf(';',header.colonOffset+1);
        const terminated=semicolon>=0&&semicolon<nextHeader;
        const end=terminated?semicolon:nextHeader;
        const bodyOffset=header.colonOffset+1;
        const body=document.content.slice(bodyOffset,end);
        const bodyMasked=masked.slice(bodyOffset,end);
        push(symbol(`rule:${document.id}:${header.nameOffset}`,header.name,header.parser?'RULE':'TOKEN',document,rangeAt(index,header.nameOffset,Math.max(1,header.name.length)),`document:${document.id}`,header.parser?(header.explicitEntry?'Entry parser rule':'Parser rule'):'Terminal/token rule'));
        rules.push({name:header.name,documentId:document.id,parser:header.parser,explicitEntry:header.explicitEntry,body,bodyMasked,bodyOffset,endOffset:end,nameOffset:header.nameOffset,terminated,references:[],index});
    });
    return rules;
}

function collectImports(document: GrammarSourceDocument, source: string, index: LineIndex, push: PushSymbol) {
    const pattern = /\bimport\s+(?:"([^"]+)"|'([^']+)'|([A-Za-z_][A-Za-z0-9_.]*))/g;
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(source)) !== null) {
        const value = match[1] || match[2] || match[3] || '';
        const local = match[0].lastIndexOf(value);
        const offset = match.index + Math.max(0, local);
        push(symbol(`import:${document.id}:${offset}`, value, 'IMPORT', document, rangeAt(index, offset, Math.max(1, value.length)), `document:${document.id}`, 'Imported grammar or namespace'));
    }
}

function collectFeatures(rule: RuleInfo, document: GrammarSourceDocument | undefined, push: PushSymbol) {
    if (!document) return;
    const assignment = /\b([A-Za-z_][A-Za-z0-9_]*)\s*(\+=|\?=|=)/g;
    let match: RegExpExecArray | null;
    while ((match = assignment.exec(rule.bodyMasked)) !== null) {
        const offset = rule.bodyOffset + match.index;
        push(symbol(`assignment:${document.id}:${offset}`, match[1], 'ASSIGNMENT', document, rangeAt(rule.index, offset, match[1].length), `rule:${document.id}:${rule.name}`, `Assignment ${match[2]}`));
    }
    const cross = /\[\s*([A-Za-z_][A-Za-z0-9_.]*)\s*(?:\||\])?/g;
    while ((match = cross.exec(rule.bodyMasked)) !== null) {
        const value = match[1];
        const offset = rule.bodyOffset + match.index + match[0].indexOf(value);
        push(symbol(`xref:${document.id}:${offset}`, value, 'CROSS_REFERENCE', document, rangeAt(rule.index, offset, value.length), `rule:${document.id}:${rule.name}`, 'Cross-reference target'));
    }
}

function collectUndefinedReferenceDiagnostics(rule:RuleInfo,declared:Set<string>,document:GrammarSourceDocument|undefined,push:PushDiagnostic){
    if(!document)return;
    const assignmentLhs:Array<[number,number]>=[];
    const assignment=/\b([A-Za-z_][A-Za-z0-9_]*)\s*(?:\+=|\?=|=)/g;
    for(let match=assignment.exec(rule.bodyMasked);match;match=assignment.exec(rule.bodyMasked))assignmentLhs.push([match.index,match.index+match[1].length]);
    const crossTargets:Array<[number,number]>=[];
    const cross=/\[\s*([A-Za-z_][A-Za-z0-9_.]*)/g;
    for(let match=cross.exec(rule.bodyMasked);match;match=cross.exec(rule.bodyMasked)){
        const local=match.index+match[0].indexOf(match[1]);crossTargets.push([local,local+match[1].length]);
    }
    IDENTIFIER.lastIndex=0;
    let match:RegExpExecArray|null;
    const emitted=new Set<string>();
    while((match=IDENTIFIER.exec(rule.bodyMasked))!==null){
        const value=match[0],offset=match.index;
        if(value===rule.name||KEYWORDS.has(value)||BUILTIN_REFERENCES.has(value)||declared.has(value))continue;
        if(inRanges(offset,assignmentLhs)||inRanges(offset,crossTargets))continue;
        const previous=previousSignificant(rule.bodyMasked,offset);
        if(previous==='.'||previous==='$'||previous==='@'||previous==='#')continue;
        const next=nextSignificant(rule.bodyMasked,offset+value.length);
        if(next==='='||(next==='+'&&rule.bodyMasked.slice(offset+value.length).trimStart().startsWith('+='))||(next==='?'&&rule.bodyMasked.slice(offset+value.length).trimStart().startsWith('?=')))continue;
        const key=`${value}:${offset}`;if(emitted.has(key))continue;emitted.add(key);
        const absolute=rule.bodyOffset+offset;
        push(diag('GRAMMAR_LOCAL_UNDEFINED_REFERENCE','ERROR','Referencia de gramática no reconocida',`"${value}" no corresponde a ninguna regla ni terminal conocida por la gramática actual.`,'El borrador referencia un símbolo que no está declarado localmente ni pertenece a los terminales básicos reconocidos por HYDRA.','El análisis puede quedar incompleto y la compilación puede fallar porque no se puede resolver la construcción referenciada.','Declara la regla o terminal que falta, corrige su nombre o importa la gramática que lo proporciona.',document,rangeAt(rule.index,absolute,value.length),rule.name,{reference:value,provisional:'true'}));
    }
}

function collectDelimiterDiagnostics(document:GrammarSourceDocument,masked:string,index:LineIndex,push:PushDiagnostic){
    const opening=new Map([['(',')'],['[',']'],['{','}']]);
    const closing=new Map([[')', '('],[']','['],['}','{']]);
    const stack:Array<{char:string;offset:number}>=[];
    for(let offset=0;offset<masked.length;offset++){
        const char=masked[offset];
        if(opening.has(char)){stack.push({char,offset});continue;}
        const expectedOpen=closing.get(char);if(!expectedOpen)continue;
        const top=stack[stack.length-1];
        if(!top||top.char!==expectedOpen){
            push(diag('GRAMMAR_LOCAL_UNBALANCED_DELIMITER','ERROR','Delimitador de gramática desequilibrado',`El delimitador de cierre "${char}" no tiene una apertura compatible.`,'El delimitador no cierra correctamente el grupo de gramática que está abierto.','La regla afectada no puede analizarse de forma fiable.','Elimina el delimitador o añade su apertura correspondiente en la posición correcta.',document,rangeAt(index,offset,1),'',{delimiter:char,provisional:'true'}));
            continue;
        }
        stack.pop();
    }
    for(const unclosed of stack.slice(-20))push(diag('GRAMMAR_LOCAL_UNBALANCED_DELIMITER','ERROR','Delimitador de gramática desequilibrado',`El delimitador de apertura "${unclosed.char}" no está cerrado.`,'Hay un grupo de gramática o una referencia cruzada incompletos.','La regla afectada no puede analizarse de forma fiable.','Añade el delimitador de cierre correspondiente antes de terminar la construcción.',document,rangeAt(index,unclosed.offset,1),'',{delimiter:unclosed.char,provisional:'true'}));
}

function collectMalformedHeaderDiagnostics(document:GrammarSourceDocument,masked:string,index:LineIndex,push:PushDiagnostic){
    const malformed=/^\s*(entry[A-Za-z0-9_]+)\s+[A-Za-z_][A-Za-z0-9_]*[^\r\n:;]*:/gm;
    let match:RegExpExecArray|null;
    while((match=malformed.exec(masked))!==null){
        const token=match[1],offset=match.index+match[0].indexOf(token);
        push(diag('GRAMMAR_LOCAL_MALFORMED_ENTRY','ERROR','Declaración de regla de entrada mal formada',`"${token}" no es la palabra reservada válida para declarar la regla de entrada.`,`The entry declaration contains extra characters in the "entry" keyword.`,`HYDRA can misinterpret the line as a different parser rule and infer the wrong language entry point.`,`Sustituye "${token}" por "entry" o elimínalo si se trata de una regla normal.`,document,rangeAt(index,offset,token.length),'',{token,provisional:'true'}));
    }
}

function collectRuleShapeDiagnostics(document:GrammarSourceDocument,rules:RuleInfo[],push:PushDiagnostic){
    for(const rule of rules){
        if(!rule.terminated)push(diag('GRAMMAR_LOCAL_MISSING_RULE_TERMINATOR','ERROR','La regla de gramática está incompleta',`La regla "${rule.name}" no termina con punto y coma antes de la siguiente regla o del final del fichero.`,'Falta el terminador obligatorio de la declaración de regla.','HYDRA no puede determinar con precisión dónde termina la regla y los diagnósticos posteriores pueden resultar ambiguos.','Añade ";" al final de la regla.',document,rangeAt(rule.index,Math.max(rule.bodyOffset,rule.endOffset-1),Math.min(1,Math.max(0,document.content.length-Math.max(rule.bodyOffset,rule.endOffset-1)))),rule.name,{provisional:'true'}));
        if(rule.parser&&rule.bodyMasked.trim()==='')push(diag('GRAMMAR_LOCAL_EMPTY_RULE','ERROR','La regla de análisis no tiene cuerpo',`La regla "${rule.name}" no contiene ninguna expresión del lenguaje.`,'La cabecera de la regla existe, pero no hay ninguna construcción después de los dos puntos.','La regla no puede reconocer ningún texto de modelo.','Añade los literales, referencias a reglas, grupos o alternativas que deba aceptar esta regla.',document,rangeAt(rule.index,rule.nameOffset,rule.name.length),rule.name,{provisional:'true'}));
    }
}

function collectUnexpectedTopLevelText(model:GrammarDefinitionModel,document:GrammarSourceDocument,masked:string,index:LineIndex,rules:RuleInfo[],push:PushDiagnostic){
    const ranges=rules.map(rule=>[Math.max(0,rule.nameOffset-32),Math.min(masked.length,rule.endOffset+(rule.terminated?1:0))] as [number,number]);
    let lineStart=0;
    for(const line of masked.split(/\r\n|\r|\n/)){
        const content=line.trim();
        const first=lineStart+Math.max(0,line.search(/\S/));
        const occupied=content!==''&&ranges.some(([start,end])=>first>=start&&first<end);
        if(content!==''&&!occupied&&!allowedTopLevelLine(model.technology,content)){
            push(diag('GRAMMAR_LOCAL_UNEXPECTED_TEXT','ERROR','Texto fuera de una declaración de gramática',`HYDRA ha encontrado texto que no pertenece a ninguna regla completa: "${shorten(content,80)}".`,'La línea está fuera de la cabecera de la gramática, de las importaciones y de las reglas o terminales reconocidos.','El backend puede rechazar el borrador o ignorar la construcción que se pretendía escribir.','Completa la declaración, mueve este texto dentro de una regla o elimina los caracteres accidentales.',document,rangeAt(index,first,Math.max(1,Math.min(content.length,80))),'',{provisional:'true'}));
        }
        lineStart+=line.length+lineBreakLength(document.content,lineStart+line.length);
    }
}

function allowedTopLevelLine(technology:GrammarDefinitionModel['technology'],content:string):boolean{
    if(/^(grammar|import|with|generate|hidden)\b/.test(content))return true;
    if(/^\s*[{};,]+\s*$/.test(content))return true;
    if(technology==='ANTLR'&&(/^(lexer\s+grammar|parser\s+grammar|options\b|tokens\b|channels\b|mode\b|@)/.test(content)||/^[A-Za-z_][A-Za-z0-9_]*\s*=.*;\s*$/.test(content)))return true;
    return false;
}

function references(bodyMasked: string, parserRules: Set<string>, owner: string): string[] {
    const result: string[] = [];
    IDENTIFIER.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = IDENTIFIER.exec(bodyMasked)) !== null) {
        const value = match[0];
        if (value !== owner && parserRules.has(value) && !KEYWORDS.has(value)) result.push(value);
    }
    return unique(result);
}

function maskGrammar(source: string, technology: GrammarDefinitionModel['technology']): string {
    const chars = [...source];
    let state: 'NORMAL'|'LINE'|'BLOCK'|'SINGLE'|'DOUBLE'|'REGEX' = 'NORMAL';
    let escaped = false;
    const canStartRegex = (index: number) => technology === 'LANGIUM' && previousSignificant(source, index) !== '/';
    for (let i = 0; i < chars.length; i++) {
        const current = source[i];
        const next = source[i + 1] ?? '';
        if (state === 'NORMAL') {
            if (current === '/' && next === '/') { blank(chars, i); blank(chars, i + 1); state = 'LINE'; i++; continue; }
            if (current === '/' && next === '*') { blank(chars, i); blank(chars, i + 1); state = 'BLOCK'; i++; continue; }
            if (current === "'") { blank(chars, i); state = 'SINGLE'; escaped = false; continue; }
            if (current === '"') { blank(chars, i); state = 'DOUBLE'; escaped = false; continue; }
            if (current === '/' && canStartRegex(i)) { blank(chars, i); state = 'REGEX'; escaped = false; continue; }
            continue;
        }
        if (state === 'LINE') { if (current === '\n' || current === '\r') state = 'NORMAL'; else blank(chars, i); continue; }
        if (state === 'BLOCK') { if (current === '*' && next === '/') { blank(chars, i); blank(chars, i + 1); state = 'NORMAL'; i++; } else blank(chars, i); continue; }
        blank(chars, i);
        if (escaped) { escaped = false; continue; }
        if (current === '\\') { escaped = true; continue; }
        if ((state === 'SINGLE' && current === "'") || (state === 'DOUBLE' && current === '"') || (state === 'REGEX' && current === '/')) state = 'NORMAL';
    }
    return chars.join('');
}

function previousSignificant(source: string, index: number): string {
    for (let i = index - 1; i >= 0; i--) if (!/\s/.test(source[i])) return source[i];
    return '';
}
function nextSignificant(source:string,index:number):string{for(let i=index;i<source.length;i++)if(!/\s/.test(source[i]))return source[i];return '';}
function inRanges(offset:number,ranges:Array<[number,number]>):boolean{return ranges.some(([start,end])=>offset>=start&&offset<end);}
function blank(chars: string[], index: number) { if (chars[index] !== '\n' && chars[index] !== '\r') chars[index] = ' '; }
function unique<T>(values: T[]): T[] { return Array.from(new Set(values)); }
function result(parserRules: string[], inferredEntryRule: string, inferenceMethod: GrammarLocalEntryInferenceMethod, confidence: number, reason: string, symbols: GrammarSymbol[], diagnostics:GrammarDiagnostic[], truncated: boolean): GrammarLocalInspection {
    return {parserRules, inferredEntryRule, inferenceMethod, confidence, reason, symbols, unusedRules: [], diagnostics, truncated};
}
function finalize(inspection: GrammarLocalInspection, model: GrammarDefinitionModel, rules: RuleInfo[]): GrammarLocalInspection {
    const parserRules = rules.filter(rule => rule.parser);
    const parserNames = new Set(parserRules.map(rule => rule.name));
    const roots: string[] = [];
    const selected = model.entryRule.trim();
    if (selected !== '' && parserNames.has(selected)) roots.push(selected);
    else if (inspection.inferredEntryRule !== '') roots.push(inspection.inferredEntryRule);
    for (const rule of parserRules) if (rule.explicitEntry && !roots.includes(rule.name)) roots.push(rule.name);
    if (roots.length === 0 && parserRules.length > 0) roots.push(parserRules[0].name);
    const byName = new Map(parserRules.map(rule => [rule.name, rule] as const));
    const reachable = new Set<string>();
    const pending = [...roots];
    while (pending.length > 0) {
        const current = pending.shift() as string;
        if (reachable.has(current)) continue;
        reachable.add(current);
        for (const target of byName.get(current)?.references ?? []) if (!reachable.has(target)) pending.push(target);
    }
    return {...inspection, unusedRules: parserRules.map(rule => rule.name).filter(name => !reachable.has(name))};
}

function createLineIndex(source: string): LineIndex {
    const starts = [0];
    for (let index = 0; index < source.length; index++) {
        if (source[index] === '\r') { if (source[index + 1] === '\n') index++; starts.push(index + 1); }
        else if (source[index] === '\n') starts.push(index + 1);
    }
    return {starts, sourceLength: source.length};
}
function positionAt(index: LineIndex, offset: number): TextPosition {
    const bounded = Math.max(0, Math.min(offset, index.sourceLength));
    let low = 0, high = index.starts.length - 1;
    while (low <= high) { const middle = (low + high) >>> 1; if (index.starts[middle] <= bounded) low = middle + 1; else high = middle - 1; }
    const line = Math.max(0, high); return {line, column: bounded - index.starts[line]};
}
function rangeAt(index: LineIndex, offset: number, length: number): TextRange { return {start: positionAt(index, offset), end: positionAt(index, offset + Math.max(0, length))}; }
function symbol(id: string, name: string, kind: string, document: GrammarSourceDocument, range: TextRange, parentId: string, detail: string): GrammarSymbol {
    return {id, name, kind, documentId: document.id, range, parentId, detail};
}
function diag(code:GrammarDiagnostic['code'],severity:GrammarDiagnostic['severity'],title:string,message:string,cause:string,consequence:string,solution:string,document:GrammarSourceDocument,range:TextRange,rule='',metadata:Record<string,string>={}):GrammarDiagnostic{
    return {code,severity,title,message,cause,consequence,solution,documentId:document.id,range,rule,property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata};
}
function shorten(value:string,max:number):string{return value.length<=max?value:`${value.slice(0,Math.max(0,max-1))}…`;}
function lineBreakLength(source:string,index:number):number{return source.slice(index,index+2)==='\r\n'?2:source[index]==='\r'||source[index]==='\n'?1:0;}
