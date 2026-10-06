import type { GrammarTextEdit, TextPosition, TextRange } from '../grammar-designer/types';

export function offsetToPosition(source:string,rawOffset:number):TextPosition{
    const offset=Math.max(0,Math.min(source.length,rawOffset));let line=0,column=0;
    for(let i=0;i<offset;i++){const ch=source.charCodeAt(i);if(ch===13){if(i+1<offset&&source.charCodeAt(i+1)===10)i++;line++;column=0;}else if(ch===10){line++;column=0;}else column++;}
    return {line,column};
}

export function positionToOffset(source:string,position:TextPosition):number{
    if(position.line<0||position.column<0)throw new Error('Text positions must be non-negative');
    let line=0,column=0;
    for(let i=0;i<source.length;i++){
        if(line===position.line&&column>=position.column)return i;
        const ch=source.charCodeAt(i);
        if(ch===13){if(source.charCodeAt(i+1)===10)i++;line++;column=0;}else if(ch===10){line++;column=0;}else column++;
    }
    if(line===position.line)return source.length;
    throw new Error(`Text position ${position.line}:${position.column} is outside the document`);
}

export function applyTextEdits(source:string,edits:readonly {range:TextRange;replacement:string}[]):string{
    const normalized=[...edits].map(edit=>({start:positionToOffset(source,edit.range.start),end:positionToOffset(source,edit.range.end),replacement:edit.replacement})).sort((a,b)=>a.start-b.start||a.end-b.end);
    for(let i=0;i<normalized.length;i++){
        const edit=normalized[i];
        if(edit.end<edit.start)throw new Error('Text edit end precedes start');
        if(i>0&&edit.start<normalized[i-1].end)throw new Error('Overlapping grammar quick-fix edits are not safe to apply');
    }
    return normalized.slice().sort((a,b)=>b.start-a.start).reduce((value,edit)=>value.slice(0,edit.start)+edit.replacement+value.slice(edit.end),source);
}

export function editsForDocument(edits:readonly GrammarTextEdit[],documentId:string):GrammarTextEdit[]{return edits.filter(edit=>edit.documentId===documentId);}
