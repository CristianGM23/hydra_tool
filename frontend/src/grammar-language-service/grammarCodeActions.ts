import type { GrammarCodeAction, GrammarSourceDocument } from '../grammar-designer/types';
import { applyTextEdits, editsForDocument } from './monacoAdapter';

export function applyGrammarCodeAction(documents:readonly GrammarSourceDocument[],action:GrammarCodeAction,currentRevision?:number,analysisRevision?:number):GrammarSourceDocument[]{
    if(currentRevision!==undefined&&analysisRevision!==undefined&&currentRevision!==analysisRevision)throw new Error('The quick fix belongs to an older grammar revision. Analyze the current draft again.');
    const known=new Set(documents.map(document=>document.id));
    if(action.edits.some(edit=>!known.has(edit.documentId)))throw new Error('The quick fix targets a document that is no longer part of this grammar.');
    return documents.map(document=>{
        const edits=editsForDocument(action.edits,document.id);
        return edits.length===0?document:{...document,content:applyTextEdits(document.content,edits),sourceHash:''};
    });
}
