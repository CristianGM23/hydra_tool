import { describe, expect, it } from 'vitest';
import { createGrammar } from './grammarDesignerModel';
import { inspectGrammarLocally } from './grammarLocalInspection';

function grammar(technology:'LANGIUM'|'XTEXT'|'ANTLR', content:string){
    const base=createGrammar(technology);
    return {...base,documents:[{...base.documents[0],content}]};
}

describe('grammarLocalInspection',()=>{
    it('finds an explicit Langium entry immediately',()=>{
        const result=inspectGrammarLocally(grammar('LANGIUM',`grammar Demo\nentry Model: elements+=Element*;\nElement: name=ID;`));
        expect(result.inferredEntryRule).toBe('Model');
        expect(result.inferenceMethod).toBe('EXPLICIT_ENTRY');
        expect(result.parserRules).toEqual(['Model','Element']);
    });
    it('uses the first Xtext parser rule and ignores terminal rules',()=>{
        const result=inspectGrammarLocally(grammar('XTEXT',`grammar demo.Demo with org.eclipse.xtext.common.Terminals\nModel: elements+=Element*;\nElement: name=ID;\nterminal OTHER: 'x';`));
        expect(result.inferredEntryRule).toBe('Model');
        expect(result.parserRules).toEqual(['Model','Element']);
    });
    it('uses an unreferenced ANTLR parser root and excludes lexer rules',()=>{
        const result=inspectGrammarLocally(grammar('ANTLR',`grammar Demo;\nmodel: element* EOF;\nelement: ID;\nID: [a-z]+;`));
        expect(result.inferredEntryRule).toBe('model');
        expect(result.parserRules).toEqual(['model','element']);
    });
    it('does not end rules at semicolons inside comments, strings or Langium regexes',()=>{
        const result=inspectGrammarLocally(grammar('LANGIUM',`grammar Demo\nentry Model: value=';' items+=Item*; // ; ignored\nItem: name=ID pattern=/a;b/; /* ; */`));
        expect(result.parserRules).toEqual(['Model','Item']);
    });
    it('reports CRLF positions using zero-based line and column coordinates',()=>{
        const result=inspectGrammarLocally(grammar('LANGIUM',`grammar Demo\r\nentry Model:\r\n  value=ID;`));
        const assignment=result.symbols.find(symbol=>symbol.kind==='ASSIGNMENT');
        expect(assignment?.range.start).toEqual({line:2,column:2});
    });
    it('caps the local outline to keep editing responsive',()=>{
        const rules=Array.from({length:2100},(_,index)=>`Rule${index}: value=ID;`).join('\n');
        const result=inspectGrammarLocally(grammar('LANGIUM',`grammar Demo\nentry Root: item=Rule0;\n${rules}`));
        expect(result.symbols.length).toBeLessThanOrEqual(2000);
        expect(result.truncated).toBe(true);
    });
});

describe('unused parser rules',()=>{
    it('marks complete unreachable rule islands while preserving reachable rules',()=>{
        const inspection=inspectGrammarLocally(grammar('LANGIUM',`grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nLegacy: 'legacy';\nDeadA: DeadB;\nDeadB: 'dead';`));
        expect(inspection.unusedRules).toEqual(['Legacy','DeadA','DeadB']);
    });
});
