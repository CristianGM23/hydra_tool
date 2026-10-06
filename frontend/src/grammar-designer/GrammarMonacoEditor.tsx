import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import type { GrammarCodeAction, GrammarDiagnostic, GrammarSeverity, GrammarSymbol, TextRange } from './types';
import { offsetToPosition } from '../grammar-language-service/monacoAdapter';
import { clampTextRange, clampedPositionToOffset, splitTextLines } from './grammarTextRange';
import { useHydraLanguage } from '../i18n';
import { HydraButton } from '../design-system';
import './grammar-editor-completion.css';
import { hoverForToken, styleForToken, type GrammarTextualCustomization, type TextualRoleStyle, type TextualStyleRole } from './textualCustomization';

type MonacoDisposable={dispose:()=>void};
type MonacoDecorationCollection={set:(decorations:Array<Record<string,unknown>>)=>void;clear:()=>void};
type MonacoUri={toString?:()=>string};
type MonacoModel={getValue:()=>string;setValue:(value:string)=>void;uri?:MonacoUri;onDidChangeContent:(callback:()=>void)=>MonacoDisposable;getPositionAt?:(offset:number)=>{lineNumber:number;column:number};getOffsetAt?:(position:{lineNumber:number;column:number})=>number;getWordAtPosition?:(position:{lineNumber:number;column:number})=>{word:string;startColumn:number;endColumn:number}|null};
type MonacoEditorInstance={getModel:()=>MonacoModel|null;getPosition:()=>{lineNumber:number;column:number}|null;setPosition:(value:{lineNumber:number;column:number})=>void;revealPositionInCenter:(value:{lineNumber:number;column:number})=>void;focus:()=>void;dispose:()=>void;updateOptions:(options:Record<string,unknown>)=>void;onDidChangeCursorPosition:(callback:()=>void)=>MonacoDisposable;deltaDecorations?:(oldDecorations:string[],newDecorations:Array<Record<string,unknown>>)=>string[];createDecorationsCollection?:(decorations?:Array<Record<string,unknown>>)=>MonacoDecorationCollection};
type MonacoLanguages={
    registerHoverProvider?:(languageId:string,provider:{provideHover:(model:MonacoModel,position:{lineNumber:number;column:number})=>unknown})=>MonacoDisposable;
    registerCompletionItemProvider?:(languageId:string,provider:{provideCompletionItems:(model:MonacoModel,position:{lineNumber:number;column:number})=>unknown})=>MonacoDisposable;
    registerDefinitionProvider?:(languageId:string,provider:{provideDefinition:(model:MonacoModel,position:{lineNumber:number;column:number})=>unknown})=>MonacoDisposable;
    registerReferenceProvider?:(languageId:string,provider:{provideReferences:(model:MonacoModel,position:{lineNumber:number;column:number})=>unknown})=>MonacoDisposable;
    registerCodeActionProvider?:(languageId:string,provider:{provideCodeActions:(model:MonacoModel,range:Record<string,number>)=>unknown})=>MonacoDisposable;
    CompletionItemKind?:{Keyword:number;Value:number;Reference:number;Field:number;Text:number};
};
type MonacoNamespace={editor:{create:(element:HTMLElement,options:Record<string,unknown>)=>MonacoEditorInstance;setModelMarkers:(model:MonacoModel,owner:string,markers:Array<Record<string,unknown>>)=>void;setModelLanguage?:(model:MonacoModel,languageId:string)=>void;remeasureFonts?:()=>void};languages?:MonacoLanguages;Range?:new(startLineNumber:number,startColumn:number,endLineNumber:number,endColumn:number)=>unknown;MarkerSeverity:{Error:number;Warning:number;Info:number}};
declare global{interface Window{monaco?:MonacoNamespace}}

export interface EditorCompletionItem{label:string;insertText:string;detail?:string;documentation?:string;kind?:'LITERAL'|'TOKEN'|'REFERENCE';replaceStart:number;replaceEnd:number;}
export type EditorCompletionProvider=(value:string,cursorOffset:number)=>readonly EditorCompletionItem[];

interface Props{
    value:string;
    languageId:string;
    diagnostics:readonly GrammarDiagnostic[];
    onChange:(value:string)=>void;
    onCursorChange?:(line:number,column:number)=>void;
    focusRange?:GrammarDiagnostic['range'];
    customization?:GrammarTextualCustomization;
    readOnly?:boolean;
    alwaysShowVerticalScrollbar?:boolean;
    completionProvider?:EditorCompletionProvider;
    codeActions?:readonly GrammarCodeAction[];
    documentId?:string;
    symbols?:readonly GrammarSymbol[];
}

const MONACO_PROBE_INTERVAL_MS=100;
const MONACO_PROBE_LIMIT=50;
const DEFAULT_SCROLLBAR_OPTIONS={vertical:'auto',horizontal:'auto',verticalScrollbarSize:12,horizontalScrollbarSize:12} as const;
const COMPLETION_SHORTCUT='Ctrl+Space';
let scopeSequence=0;

export function GrammarMonacoEditor({value,languageId,diagnostics,onChange,onCursorChange,focusRange,customization,readOnly=false,alwaysShowVerticalScrollbar=false,completionProvider,codeActions=[],documentId='',symbols=[]}:Props){
    const {t}=useHydraLanguage();
    const host=useRef<HTMLDivElement>(null);
    const textarea=useRef<HTMLTextAreaElement>(null);
    const fallbackHighlight=useRef<HTMLPreElement>(null);
    const fallbackGutter=useRef<HTMLDivElement>(null);
    const instance=useRef<MonacoEditorInstance|null>(null);
    const suppress=useRef(false);
    const onChangeRef=useRef(onChange);
    const onCursorChangeRef=useRef(onCursorChange);
    const completionProviderRef=useRef(completionProvider);
    const valueRef=useRef(value);
    const languageIdRef=useRef(languageId);
    const customizationRef=useRef(customization);
    const symbolsRef=useRef(symbols);
    const actionsRef=useRef(codeActions);
    const editorOptionsRef=useRef({effectiveFontSize:customization?.fontSize??readFontSize(),lineHeight:customization?.lineHeight??Math.round((customization?.fontSize??readFontSize())*1.5),fontFamily:customization?.fontFamily,readOnly,alwaysShowVerticalScrollbar});
    const [scopeClass]=useState(()=>`hydra-monaco-scope-${++scopeSequence}`);
    const [monacoAvailable,setMonacoAvailable]=useState(()=>typeof window!=='undefined'&&window.monaco!==undefined);
    const [cursor,setCursor]=useState({line:1,column:1});
    const [safeCompletion,setSafeCompletion]=useState<{items:readonly EditorCompletionItem[];selected:number}|null>(null);
    const [fontSize,setFontSize]=useState(()=>readFontSize());
    const effectiveFontSize=customization?.fontSize??fontSize;
    const lineHeight=customization?.lineHeight??Math.round(effectiveFontSize*1.5);
    const decorations=useRef<string[]>([]);
    const diagnosticDecorations=useRef<string[]>([]);
    const syntaxDecorationCollection=useRef<MonacoDecorationCollection|null>(null);
    const diagnosticDecorationCollection=useRef<MonacoDecorationCollection|null>(null);
    const lines=useMemo(()=>splitTextLines(value),[value]);
    const visibleDiagnostics=useMemo(()=>diagnostics.filter(item=>documentId===''||item.documentId===''||item.documentId===documentId),[diagnostics,documentId]);
    const customizationCss=useMemo(()=>customization?monacoCustomizationCss(scopeClass,customization):'',[customization,scopeClass]);

    useEffect(()=>{onChangeRef.current=onChange;},[onChange]);
    useEffect(()=>{onCursorChangeRef.current=onCursorChange;},[onCursorChange]);
    useEffect(()=>{completionProviderRef.current=completionProvider;},[completionProvider]);
    useEffect(()=>{valueRef.current=value;},[value]);
    useEffect(()=>{languageIdRef.current=languageId;},[languageId]);
    useEffect(()=>{customizationRef.current=customization;},[customization]);
    useEffect(()=>{symbolsRef.current=symbols;},[symbols]);
    useEffect(()=>{actionsRef.current=codeActions;},[codeActions]);
    useEffect(()=>{editorOptionsRef.current={effectiveFontSize,lineHeight,fontFamily:customization?.fontFamily,readOnly,alwaysShowVerticalScrollbar};},[alwaysShowVerticalScrollbar,customization?.fontFamily,effectiveFontSize,lineHeight,readOnly]);
    useEffect(()=>{
        if(monacoAvailable||typeof window==='undefined')return;
        let probes=0;
        const timer=window.setInterval(()=>{
            probes++;
            if(window.monaco!==undefined){setMonacoAvailable(true);window.clearInterval(timer);}
            else if(probes>=MONACO_PROBE_LIMIT)window.clearInterval(timer);
        },MONACO_PROBE_INTERVAL_MS);
        return()=>window.clearInterval(timer);
    },[monacoAvailable]);

    useEffect(()=>{
        if(!monacoAvailable||!host.current||!window.monaco||instance.current)return;
        const monaco=window.monaco;
        const options=editorOptionsRef.current;
        const editor=monaco.editor.create(host.current,{value:valueRef.current,language:languageIdRef.current,automaticLayout:true,minimap:{enabled:false},fontSize:options.effectiveFontSize,lineHeight:options.lineHeight,fontFamily:options.fontFamily,readOnly:options.readOnly,lineNumbers:'on',lineNumbersMinChars:3,glyphMargin:true,lineDecorationsWidth:16,renderValidationDecorations:'on',scrollBeyondLastLine:false,scrollbar:{...DEFAULT_SCROLLBAR_OPTIONS,vertical:options.alwaysShowVerticalScrollbar?'visible':'auto'},tabSize:4,wordWrap:'off',fixedOverflowWidgets:true});
        instance.current=editor;
        syntaxDecorationCollection.current=editor.createDecorationsCollection?.([])??null;
        diagnosticDecorationCollection.current=editor.createDecorationsCollection?.([])??null;
        const model=editor.getModel();
        const change=model?.onDidChangeContent(()=>{if(!suppress.current&&model)onChangeRef.current(model.getValue());});
        const move=editor.onDidChangeCursorPosition(()=>{const p=editor.getPosition();if(p){setCursor({line:p.lineNumber,column:p.column});onCursorChangeRef.current?.(p.lineNumber,p.column);}});
        return()=>{change?.dispose();move.dispose();syntaxDecorationCollection.current?.clear();diagnosticDecorationCollection.current?.clear();syntaxDecorationCollection.current=null;diagnosticDecorationCollection.current=null;editor.dispose();instance.current=null;};
    },[monacoAvailable]);

    useEffect(()=>{instance.current?.updateOptions({fontSize:effectiveFontSize,lineHeight,fontFamily:customization?.fontFamily,readOnly,scrollbar:{...DEFAULT_SCROLLBAR_OPTIONS,vertical:alwaysShowVerticalScrollbar?'visible':'auto'}});window.monaco?.editor.remeasureFonts?.();if(customization===undefined){try{localStorage.setItem('hydra.grammar.editorFontSize',String(fontSize));}catch{/* optional persistence */}}},[alwaysShowVerticalScrollbar,customization,effectiveFontSize,fontSize,lineHeight,readOnly]);
    useEffect(()=>{const model=instance.current?.getModel();if(!model||model.getValue()===value)return;suppress.current=true;try{model.setValue(value);}finally{suppress.current=false;}},[value]);
    useEffect(()=>{const monaco=window.monaco,model=instance.current?.getModel();if(monaco&&model)monaco.editor.setModelLanguage?.(model,languageId);},[languageId]);
    useEffect(()=>{
        const monaco=window.monaco,model=instance.current?.getModel(),editor=instance.current;if(!monaco||!model||!editor)return;
        const next=monacoDiagnosticDecorations(visibleDiagnostics,valueRef.current,monaco);
        monaco.editor.setModelMarkers(model,'hydra-grammar',visibleDiagnostics.map(item=>markerFor(item,valueRef.current,monaco)));
        if(diagnosticDecorationCollection.current)diagnosticDecorationCollection.current.set(next);
        else if(editor.deltaDecorations)diagnosticDecorations.current=editor.deltaDecorations(diagnosticDecorations.current,next);
        return()=>{if(diagnosticDecorationCollection.current)diagnosticDecorationCollection.current.set([]);else if(editor.deltaDecorations)diagnosticDecorations.current=editor.deltaDecorations(diagnosticDecorations.current,[]);};
    },[visibleDiagnostics,value,monacoAvailable]);
    useEffect(()=>{
        const editor=instance.current,monaco=window.monaco;if(!editor||!monaco)return;
        const next=customization?monacoDecorations(value,customization,monaco):[];
        if(syntaxDecorationCollection.current)syntaxDecorationCollection.current.set(next);
        else if(editor.deltaDecorations)decorations.current=editor.deltaDecorations(decorations.current,next);
        return()=>{if(syntaxDecorationCollection.current)syntaxDecorationCollection.current.set([]);else if(editor.deltaDecorations)decorations.current=editor.deltaDecorations(decorations.current,[]);};
    },[customization,value,monacoAvailable]);
    useEffect(()=>{
        const monaco=window.monaco;if(!monaco?.languages?.registerHoverProvider)return;
        const disposable=monaco.languages.registerHoverProvider(languageId,{provideHover:(model,position)=>{
            if(model!==instance.current?.getModel())return null;
            const current=customizationRef.current;
            const word=model.getWordAtPosition?.(position)?.word??'';
            const token=current?customTokenAtPosition(model.getValue(),position.lineNumber,position.column,current):'';
            const value=token||word;if(!value)return null;
            const contents:Array<{value:string}>=[];
            const hover=current?hoverForToken(current,value):undefined;
            if(hover)contents.push({value:`**${hover.title||value}**`},{value:hover.description||''});
            const symbol=symbolsRef.current.find(item=>item.name===value||item.name===word);
            if(symbol)contents.push({value:`**${symbol.name}** · ${symbol.kind}`},{value:symbol.detail||`Grammar ${symbol.kind.toLowerCase()} defined in ${symbol.documentId}.`});
            return contents.length===0?null:{contents};
        }});return()=>disposable.dispose();
    },[languageId,monacoAvailable]);
    useEffect(()=>{
        const monaco=window.monaco;if(!monaco?.languages?.registerCompletionItemProvider||completionProvider===undefined)return;
        const disposable=monaco.languages.registerCompletionItemProvider(languageId,{provideCompletionItems:(model,position)=>{
            if(model!==instance.current?.getModel())return {suggestions:[]};
            const source=model.getValue();
            const offset=model.getOffsetAt?.(position)??clampedPositionToOffset(source,{line:Math.max(0,position.lineNumber-1),column:Math.max(0,position.column-1)});
            const items=completionProviderRef.current?.(source,offset)??[];
            return {suggestions:items.map(item=>monacoCompletion(item,source,monaco))};
        }});
        return()=>disposable.dispose();
    },[completionProvider,languageId,monacoAvailable]);
    useEffect(()=>{
        const monaco=window.monaco;if(!monaco?.languages?.registerDefinitionProvider)return;
        const disposable=monaco.languages.registerDefinitionProvider(languageId,{provideDefinition:(model,position)=>{
            if(model!==instance.current?.getModel()||!monaco.Range||model.uri===undefined)return null;
            const word=model.getWordAtPosition?.(position)?.word??'';
            const symbol=symbolsRef.current.find(item=>item.name===word&&(documentId===''||item.documentId===documentId));
            return symbol===undefined?null:{uri:model.uri,range:monacoRange(symbol.range,monaco)};
        }});return()=>disposable.dispose();
    },[documentId,languageId,monacoAvailable]);
    useEffect(()=>{
        const monaco=window.monaco;if(!monaco?.languages?.registerReferenceProvider)return;
        const disposable=monaco.languages.registerReferenceProvider(languageId,{provideReferences:(model,position)=>{
            if(model!==instance.current?.getModel()||!monaco.Range||model.uri===undefined)return [];
            const word=model.getWordAtPosition?.(position)?.word??'';if(!word)return [];
            return wordLocations(model.getValue(),word,model.uri,monaco);
        }});return()=>disposable.dispose();
    },[languageId,monacoAvailable]);
    useEffect(()=>{
        const monaco=window.monaco;if(!monaco?.languages?.registerCodeActionProvider||readOnly)return;
        const disposable=monaco.languages.registerCodeActionProvider(languageId,{provideCodeActions:(model,range)=>{
            if(model!==instance.current?.getModel()||!monaco.Range||model.uri===undefined)return {actions:[],dispose:()=>{}};
            const actions=monacoQuickFixes(model,range,visibleDiagnostics,actionsRef.current,documentId,monaco);
            return {actions,dispose:()=>{}};
        }});return()=>disposable.dispose();
    },[documentId,languageId,monacoAvailable,readOnly,visibleDiagnostics]);
    const syncFallbackScroll=useCallback((element:HTMLTextAreaElement)=>{
        if(fallbackHighlight.current){fallbackHighlight.current.scrollTop=element.scrollTop;fallbackHighlight.current.scrollLeft=element.scrollLeft;}
        if(fallbackGutter.current)fallbackGutter.current.scrollTop=element.scrollTop;
    },[]);
    const scrollFallbackToLine=useCallback((line:number)=>{
        const target=textarea.current;if(target===null)return;
        target.scrollTop=Math.max(0,line*lineHeight-target.clientHeight/3);
        syncFallbackScroll(target);
    },[lineHeight,syncFallbackScroll]);

    useEffect(()=>{
        if(!focusRange)return;
        const safeRange=clampTextRange(value,focusRange);
        if(instance.current){const p={lineNumber:safeRange.start.line+1,column:safeRange.start.column+1};instance.current.setPosition(p);instance.current.revealPositionInCenter(p);instance.current.focus();return;}
        const target=textarea.current;if(target===null)return;
        const start=clampedPositionToOffset(value,safeRange.start);
        target.focus();target.setSelectionRange(start,start);scrollFallbackToLine(safeRange.start.line);
    },[focusRange,scrollFallbackToLine,value]);

    function textareaCursor(element:HTMLTextAreaElement){const p=offsetToPosition(element.value,element.selectionStart);const next={line:p.line+1,column:p.column+1};setCursor(next);onCursorChangeRef.current?.(next.line,next.column);}
    function requestSafeCompletion(element:HTMLTextAreaElement){
        const items=completionProviderRef.current?.(element.value,element.selectionStart)??[];
        setSafeCompletion(items.length===0?null:{items,selected:0});
    }
    function applySafeCompletion(item:EditorCompletionItem){
        const target=textarea.current;if(target===null)return;
        const source=target.value;
        const start=Math.max(0,Math.min(source.length,item.replaceStart));
        const end=Math.max(start,Math.min(source.length,item.replaceEnd));
        const next=source.slice(0,start)+item.insertText+source.slice(end);
        const caret=start+item.insertText.length;
        onChangeRef.current(next);setSafeCompletion(null);
        window.setTimeout(()=>{const current=textarea.current;if(current!==null){current.focus();current.setSelectionRange(caret,caret);textareaCursor(current);}},0);
    }
    function safeEditorKeyDown(event:React.KeyboardEvent<HTMLTextAreaElement>){
        if((event.ctrlKey||event.metaKey)&&event.code==='Space'){
            event.preventDefault();requestSafeCompletion(event.currentTarget);return;
        }
        if(safeCompletion===null)return;
        if(event.key==='Escape'){event.preventDefault();setSafeCompletion(null);return;}
        if(event.key==='ArrowDown'){event.preventDefault();setSafeCompletion(current=>current===null?null:{...current,selected:(current.selected+1)%current.items.length});return;}
        if(event.key==='ArrowUp'){event.preventDefault();setSafeCompletion(current=>current===null?null:{...current,selected:(current.selected-1+current.items.length)%current.items.length});return;}
        if(event.key==='Enter'||event.key==='Tab'){event.preventDefault();const item=safeCompletion.items[safeCompletion.selected];if(item!==undefined)applySafeCompletion(item);return;}
        setSafeCompletion(null);
    }
    return <div className="grammar-editor-stack" data-hydra-i18n-skip="true">
        {customizationCss!==''&&<style>{customizationCss}</style>}
        {!readOnly&&customization===undefined&&<div className="grammar-editor-view-controls"><span>{t('Font size')}</span><HydraButton type="button" onClick={()=>setFontSize(value=>Math.max(11,value-1))} aria-label={t('Decrease editor font size')}>−</HydraButton><strong>{fontSize}</strong><HydraButton type="button" onClick={()=>setFontSize(value=>Math.min(24,value+1))} aria-label={t('Increase editor font size')}>+</HydraButton></div>}
        {monacoAvailable?<div className={`grammar-monaco-host ${scopeClass}`} ref={host} style={customization?customizationVariables(customization):undefined}/>:<div className="grammar-safe-editor" data-testid="grammar-safe-editor" style={{fontSize:effectiveFontSize,lineHeight:`${lineHeight}px`,fontFamily:customization?.fontFamily,color:customization?.foreground,background:customization?.background}}>
            <div className="grammar-safe-gutter" ref={fallbackGutter} aria-hidden="true">{lines.map((_line,index)=><span style={{height:lineHeight,lineHeight:`${lineHeight}px`}} className={lineDiagnosticClass(index,visibleDiagnostics)} key={index} title={lineDiagnosticTitle(index,visibleDiagnostics)}>{index+1}</span>)}</div>
            <pre className="grammar-safe-highlight" ref={fallbackHighlight} aria-hidden="true" style={{fontSize:effectiveFontSize,lineHeight:`${lineHeight}px`,fontFamily:customization?.fontFamily,color:customization?.foreground,background:customization?.background}}>{lines.map((line,index)=><Fragment key={index}>{customization?renderCustomizedLine(line,customization):renderHighlightedLine(line,index,visibleDiagnostics)}{index<lines.length-1?'\n':''}</Fragment>)}</pre>
            <textarea ref={textarea} className={`grammar-textarea-fallback${alwaysShowVerticalScrollbar?' always-vertical-scroll':''}`} style={{fontSize:effectiveFontSize,lineHeight:`${lineHeight}px`,fontFamily:customization?.fontFamily}} spellCheck={false} value={value} readOnly={readOnly} onChange={event=>{setSafeCompletion(null);onChangeRef.current(event.target.value);}} onClick={event=>{setSafeCompletion(null);textareaCursor(event.currentTarget);}} onKeyDown={safeEditorKeyDown} onKeyUp={event=>textareaCursor(event.currentTarget)} onScroll={event=>syncFallbackScroll(event.currentTarget)} aria-label={t('Grammar source editor')}/>
            {safeCompletion!==null&&<div className="grammar-safe-completion" role="listbox" aria-label={t('Grammar-aware suggestions')}>{safeCompletion.items.map((item,index)=><HydraButton type="button" role="option" aria-selected={safeCompletion.selected===index} className={safeCompletion.selected===index?'selected':''} key={`${item.label}:${index}`} onMouseDown={event=>event.preventDefault()} onClick={()=>applySafeCompletion(item)}><strong>{item.label}</strong>{item.detail&&<span>{item.detail}</span>}{item.documentation&&<small>{item.documentation}</small>}</HydraButton>)}</div>}
        </div>}
        {/* Diagnostic text and HYDRA quick-fix buttons live in the Problems dock. Monaco keeps only markers, gutter feedback and native code actions so source space stays available for editing. */}
        <footer className="grammar-editor-status"><span>{monacoAvailable?'Monaco':t('Safe editor (Monaco is not available in this host)')}</span><span>{languageId}</span><span>{t('Line')} {cursor.line}, {t('column')} {cursor.column}</span><span>{visibleDiagnostics.length} {t(visibleDiagnostics.length===1?'diagnostic':'diagnostics')}</span>{completionProvider!==undefined&&!readOnly&&<span>{t('Suggestions')}: {COMPLETION_SHORTCUT}</span>}</footer>
    </div>;
}

function monacoDiagnosticDecorations(diagnostics:readonly GrammarDiagnostic[],text:string,monaco:MonacoNamespace):Array<Record<string,unknown>>{
    const Range=monaco.Range;if(!Range)return[];
    return diagnostics.map(item=>{
        const range=clampTextRange(text,item.range);
        const startLine=range.start.line+1;
        const kind=item.code==='GRAMMAR_UNUSED_RULE'?'unused':item.severity.toLowerCase();
        const detail=[item.title,item.message,item.cause?`Cause: ${item.cause}`:'',item.consequence?`Impact: ${item.consequence}`:'',item.solution?`Recommendation: ${item.solution}`:''].filter(Boolean).join('\n');
        return {range:new Range(startLine,1,startLine,1),options:{isWholeLine:true,className:`hydra-monaco-line-${kind}`,linesDecorationsClassName:`hydra-monaco-line-decoration-${kind}`,glyphMarginClassName:`hydra-monaco-glyph-${kind}`,lineNumberClassName:`hydra-monaco-line-number-${kind}`,glyphMarginHoverMessage:{value:detail}}};
    });
}

function monacoCompletion(item:EditorCompletionItem,text:string,monaco:MonacoNamespace):Record<string,unknown>{
    const start=positionFromOffset(text,item.replaceStart),end=positionFromOffset(text,item.replaceEnd);
    const kinds=monaco.languages?.CompletionItemKind;
    const kind=item.kind==='LITERAL'?(kinds?.Keyword??17):item.kind==='REFERENCE'?(kinds?.Reference??18):(kinds?.Value??12);
    return {label:item.label,insertText:item.insertText,kind,detail:item.detail,documentation:item.documentation===undefined?undefined:{value:item.documentation},range:monaco.Range?new monaco.Range(start.line+1,start.column+1,end.line+1,end.column+1):undefined,sortText:item.kind==='LITERAL'?'0':item.kind==='REFERENCE'?'1':'2'};
}

function markerFor(item:GrammarDiagnostic,text:string,monaco:MonacoNamespace):Record<string,unknown>{
    const range=clampTextRange(text,item.range);
    const startLine=range.start.line+1,startColumn=range.start.column+1;
    const same=range.end.line===range.start.line&&range.end.column<=range.start.column;
    const lineLength=splitTextLines(text)[range.end.line]?.length??0;
    const endColumn=same?Math.min(lineLength+1,startColumn+1):range.end.column+1;
    const detail=[item.title,item.message,item.cause?`Cause: ${item.cause}`:'',item.consequence?`Impact: ${item.consequence}`:'',item.solution?`Recommendation: ${item.solution}`:''].filter(Boolean).join('\n');
    return {startLineNumber:startLine,startColumn,endLineNumber:range.end.line+1,endColumn,severity:item.severity==='ERROR'?monaco.MarkerSeverity.Error:item.severity==='WARNING'?monaco.MarkerSeverity.Warning:monaco.MarkerSeverity.Info,message:detail,code:item.code,source:'HYDRA'};
}
function severityRank(value:GrammarSeverity):number{return value==='ERROR'?3:value==='WARNING'?2:1;}
function diagnosticsForLine(line:number,diagnostics:readonly GrammarDiagnostic[]):GrammarDiagnostic[]{return diagnostics.filter(item=>item.range.start.line<=line&&item.range.end.line>=line).sort((a,b)=>severityRank(b.severity)-severityRank(a.severity));}
function lineDiagnosticClass(line:number,diagnostics:readonly GrammarDiagnostic[]):string{const item=diagnosticsForLine(line,diagnostics)[0];return item===undefined?'':`has-${item.severity.toLowerCase()}`;}
function lineDiagnosticTitle(line:number,diagnostics:readonly GrammarDiagnostic[]):string{const items=diagnosticsForLine(line,diagnostics);return items.map(item=>`${item.title}: ${item.message}${item.solution?`\nRecommendation: ${item.solution}`:''}`).join('\n');}
function renderHighlightedLine(line:string,lineIndex:number,diagnostics:readonly GrammarDiagnostic[]){
    const items=diagnosticsForLine(lineIndex,diagnostics);if(items.length===0)return line||' ';
    const ranges=items.map(item=>lineRange(item.range,lineIndex,line.length,item.severity)).filter((item):item is {start:number;end:number;severity:GrammarSeverity}=>item!==null).sort((a,b)=>a.start-b.start||b.end-a.end||severityRank(b.severity)-severityRank(a.severity));
    if(ranges.length===0)return line||' ';
    const points=new Set<number>([0,line.length]);ranges.forEach(item=>{points.add(item.start);points.add(item.end);});const sorted=[...points].sort((a,b)=>a-b);const output=[];
    for(let index=0;index<sorted.length-1;index++){const start=sorted[index],end=sorted[index+1];const active=ranges.filter(item=>item.start<end&&item.end>start).sort((a,b)=>severityRank(b.severity)-severityRank(a.severity))[0];const text=line.slice(start,end)||' ';output.push(active===undefined?<Fragment key={`${start}-${end}`}>{text}</Fragment>:<mark className={`grammar-source-diagnostic ${active.severity.toLowerCase()}`} key={`${start}-${end}`}>{text}</mark>);}
    return output;
}
function lineRange(range:TextRange,line:number,lineLength:number,severity:GrammarSeverity):{start:number;end:number;severity:GrammarSeverity}|null{
    if(line<range.start.line||line>range.end.line)return null;const start=line===range.start.line?Math.min(lineLength,range.start.column):0;let end=line===range.end.line?Math.min(lineLength,range.end.column):lineLength;if(end<=start)end=Math.min(lineLength,Math.max(start+1,lineLength===0?1:start+1));return {start,end,severity};
}

function customizationVariables(c:GrammarTextualCustomization):CSSProperties{const values:Record<string,string>={};for(const [role,style] of Object.entries(c.roleStyles)){const key=role.toLowerCase();values[`--hydra-textual-${key}`]=style.color;values[`--hydra-textual-${key}-weight`]=style.bold?'700':'400';values[`--hydra-textual-${key}-style`]=style.italic?'italic':'normal';}values['--hydra-textual-foreground']=c.foreground;values['--hydra-textual-background']=c.background;values.background=c.background;values.color=c.foreground;return values as CSSProperties;}
function renderCustomizedLine(line:string,c:GrammarTextualCustomization){const tokens=tokenizeCustom(line,c);if(tokens.length===0)return line||' ';return tokens.map((token,index)=><span key={`${index}:${token.text}`} style={customStyle(c,token.role,token.text)} title={token.hover}>{token.text}</span>);}
function customStyle(c:GrammarTextualCustomization,role:TextualStyleRole,text:string):React.CSSProperties{const style=styleForToken(c,role,text);return {color:style.color,fontWeight:style.bold?700:400,fontStyle:style.italic?'italic':'normal'};}
function tokenizeCustom(line:string,c:GrammarTextualCustomization):Array<{text:string;role:TextualStyleRole;hover?:string}>{const pattern=/(\/\/.*$|#[^\n]*$|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_.:-]*\b|[^A-Za-z0-9_\s]+|\s+)/g;const keywords=new Set(c.keywords);const out=[] as Array<{text:string;role:TextualStyleRole;hover?:string}>;for(const match of line.matchAll(pattern)){const text=match[0];let role:TextualStyleRole='PUNCTUATION';if(/^\s+$/.test(text))role='PUNCTUATION';else if(text.startsWith('//')||text.startsWith('#'))role='COMMENT';else if(/^['"]/.test(text))role='STRING';else if(/^\d/.test(text))role='NUMBER';else if(keywords.has(text))role='KEYWORD';else if(/^[A-Z][A-Za-z0-9_]*$/.test(text))role='TYPE';else if(/^[A-Za-z_]/.test(text))role='TOKEN';else if(/[=+*?|:-]/.test(text))role='OPERATOR';const hover=hoverForToken(c,text);out.push({text,role,hover:hover?`${hover.title||text}\n${hover.description}`:undefined});}return out;}
function customTokenAtPosition(value:string,lineNumber:number,column:number,c:GrammarTextualCustomization):string{const lines=value.replace(/\r\n/g,'\n').replace(/\r/g,'\n').split('\n');const line=lines[Math.max(0,lineNumber-1)]??'';const offset=Math.max(0,column-1);let cursor=0;for(const token of tokenizeCustom(line,c)){const end=cursor+token.text.length;if(offset>=cursor&&offset<end&&!/^\s+$/.test(token.text))return token.text;cursor=end;}return '';}
function monacoDecorations(value:string,c:GrammarTextualCustomization,monaco:MonacoNamespace):Array<Record<string,unknown>>{if(!monaco.Range)return[];const result:Array<Record<string,unknown>>=[];let offset=0;for(const line of value.replace(/\r\n/g,'\n').replace(/\r/g,'\n').split('\n')){for(const token of tokenizeCustom(line,c)){const start=offset;const end=start+token.text.length;offset=end;if(/^\s+$/.test(token.text))continue;const startPos=positionFromOffset(value,start),endPos=positionFromOffset(value,end);const style=styleForToken(c,token.role,token.text);result.push({range:new monaco.Range(startPos.line+1,startPos.column+1,endPos.line+1,endPos.column+1),options:{inlineClassName:styleClassName(style),inlineClassNameAffectsLetterSpacing:true,hoverMessage:token.hover?{value:token.hover}:undefined}});}offset++;if(result.length>=50000)break;}return result;}
function styleClassName(style:TextualRoleStyle):string{return `hydra-textual-style-${style.color.slice(1).toLowerCase()}-${style.bold?'b':'n'}-${style.italic?'i':'n'}`;}
function monacoCustomizationCss(scopeClass:string,c:GrammarTextualCustomization):string{
    const styles=[...Object.values(c.roleStyles),...Object.values(c.symbolStyles)];
    const unique=new Map(styles.map(style=>[styleClassName(style),style]));
    const prefix=`.${scopeClass}`;
    const family=c.fontFamily.replace(/;/g,'');
    const base=`${prefix}.grammar-monaco-host .monaco-editor,${prefix}.grammar-monaco-host .monaco-editor-background,${prefix}.grammar-monaco-host .monaco-editor .margin{background-color:${c.background}!important;}\n${prefix}.grammar-monaco-host .monaco-editor .view-lines,${prefix}.grammar-monaco-host .monaco-editor .view-line,${prefix}.grammar-monaco-host .monaco-editor .view-line span{font-family:${family}!important;font-size:${c.fontSize}px!important;line-height:${c.lineHeight}px!important;}\n${prefix}.grammar-monaco-host .monaco-editor .view-line span{color:${c.foreground}!important;}`;
    const rules=[...unique].map(([name,style])=>`${prefix}.grammar-monaco-host .monaco-editor .${name},${prefix}.grammar-monaco-host .monaco-editor .${name} *{color:${style.color}!important;font-weight:${style.bold?'700':'400'}!important;font-style:${style.italic?'italic':'normal'}!important;}`).join('\n');
    return `${base}\n${rules}`;
}
function monacoRange(range:TextRange,monaco:MonacoNamespace):unknown{return monaco.Range?new monaco.Range(range.start.line+1,range.start.column+1,range.end.line+1,Math.max(range.start.column+2,range.end.column+1)):undefined;}
function wordLocations(text:string,word:string,uri:MonacoUri,monaco:MonacoNamespace):Array<Record<string,unknown>>{
    if(!monaco.Range||!/^[A-Za-z_][A-Za-z0-9_]*$/.test(word))return[];
    const pattern=new RegExp(`\\b${escapeRegex(word)}\\b`,'g'),result:Array<Record<string,unknown>>=[];
    for(let match=pattern.exec(text);match;match=pattern.exec(text)){
        const start=positionFromOffset(text,match.index),end=positionFromOffset(text,match.index+word.length);
        result.push({uri,range:new monaco.Range(start.line+1,start.column+1,end.line+1,end.column+1)});
        if(result.length>=256)break;
    }
    return result;
}
function monacoQuickFixes(model:MonacoModel,range:Record<string,number>,diagnostics:readonly GrammarDiagnostic[],actions:readonly GrammarCodeAction[],documentId:string,monaco:MonacoNamespace):Array<Record<string,unknown>>{
    if(!monaco.Range||model.uri===undefined)return[];
    const startLine=Number(range.startLineNumber??1)-1,endLine=Number(range.endLineNumber??startLine+1)-1;
    const relevant=diagnostics.filter(item=>item.range.start.line<=endLine&&item.range.end.line>=startLine);
    const byId=new Map(actions.map(action=>[action.id,action]));
    const out:Array<Record<string,unknown>>=[];
    for(const diagnostic of relevant){
        const linked=diagnostic.codeActionIds.map(id=>byId.get(id)).filter((item):item is GrammarCodeAction=>item!==undefined);
        for(const action of linked){
            const edits=action.edits.filter(edit=>documentId===''||edit.documentId===documentId).map(edit=>({resource:model.uri,textEdit:{range:monacoRange(edit.range,monaco),text:edit.replacement}}));
            if(edits.length>0)out.push({title:action.title,kind:'quickfix',isPreferred:action.preferred,edit:{edits}});
        }
        if(linked.length===0){for(const candidate of diagnostic.candidates.slice(0,3)){out.push({title:`Replace with ${candidate.name}`,kind:'quickfix',isPreferred:false,edit:{edits:[{resource:model.uri,textEdit:{range:monacoRange(diagnostic.range,monaco),text:candidate.name}}]}});}}
    }
    return out;
}
function escapeRegex(value:string):string{return value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');}
function positionFromOffset(text:string,offset:number):{line:number;column:number}{return offsetToPosition(text,Math.max(0,Math.min(text.length,offset)));}
function readFontSize():number{try{const value=Number(localStorage.getItem('hydra.grammar.editorFontSize'));return Number.isFinite(value)&&value>=11&&value<=24?value:14;}catch{return 14;}}
