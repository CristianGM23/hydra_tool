import type { TextPosition, TextRange } from './types';

export function splitTextLines(value:string):string[]{
    return value.replace(/\r\n/g,'\n').replace(/\r/g,'\n').split('\n');
}

export function clampTextPosition(text:string,position:TextPosition):TextPosition{
    const lines=splitTextLines(text);
    const rawLine=Number.isFinite(position.line)?Math.trunc(position.line):0;
    const line=Math.max(0,Math.min(Math.max(0,lines.length-1),rawLine));
    const length=lines[line]?.length??0;
    const rawColumn=Number.isFinite(position.column)?Math.trunc(position.column):0;
    return {line,column:Math.max(0,Math.min(length,rawColumn))};
}

export function clampTextRange(text:string,range:TextRange):TextRange{
    const start=clampTextPosition(text,range.start);
    const rawEnd=clampTextPosition(text,range.end);
    const end=rawEnd.line<start.line||(rawEnd.line===start.line&&rawEnd.column<start.column)?start:rawEnd;
    return {start,end};
}

export function clampedPositionToOffset(text:string,position:TextPosition):number{
    const safe=clampTextPosition(text,position);
    let line=0,column=0;
    for(let index=0;index<text.length;index++){
        if(line===safe.line&&column>=safe.column)return index;
        const code=text.charCodeAt(index);
        if(code===13){if(text.charCodeAt(index+1)===10)index++;line++;column=0;}
        else if(code===10){line++;column=0;}
        else column++;
    }
    return text.length;
}
