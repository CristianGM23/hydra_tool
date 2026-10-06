import { describe, expect, it } from 'vitest';
import { buildLocalGrammarFeedback } from './grammarLocalQuickFixes';
import type { GrammarDiagnostic, GrammarSymbol } from './types';

const range={start:{line:1,column:18},end:{line:1,column:26}};
const diagnostic:GrammarDiagnostic={code:'GRAMMAR_LOCAL_UNDEFINED_REFERENCE',severity:'ERROR',title:'Unknown reference',message:'',cause:'',consequence:'',solution:'',documentId:'main',range,rule:'Model',property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata:{reference:'Elemennt'}};
const symbol:GrammarSymbol={id:'rule:main:20',name:'Element',kind:'RULE',documentId:'main',range:{start:{line:2,column:0},end:{line:2,column:7}},parentId:'document:main',detail:'Parser rule'};

describe('local grammar quick fixes',()=>{
  it('offers a safe typo correction for an unresolved rule reference',()=>{
    const result=buildLocalGrammarFeedback([diagnostic],[symbol]);
    expect(result.diagnostics[0]?.candidates[0]?.name).toBe('Element');
    expect(result.diagnostics[0]?.codeActionIds.length).toBeGreaterThan(0);
    expect(result.actions[0]?.edits[0]?.replacement).toBe('Element');
    expect(result.actions[0]?.safeAutomatic).toBe(true);
  });
  it('offers deterministic fixes for a malformed entry keyword and missing terminator',()=>{
    const malformed={...diagnostic,code:'GRAMMAR_LOCAL_MALFORMED_ENTRY',metadata:{token:'entryy'},range:{start:{line:0,column:0},end:{line:0,column:6}}};
    const terminator={...diagnostic,code:'GRAMMAR_LOCAL_MISSING_RULE_TERMINATOR',metadata:{},range:{start:{line:2,column:10},end:{line:2,column:11}}};
    const result=buildLocalGrammarFeedback([malformed,terminator],[]);
    expect(result.actions.some(action=>action.edits.some(edit=>edit.replacement==='entry'))).toBe(true);
    expect(result.actions.some(action=>action.edits.some(edit=>edit.replacement===';'))).toBe(true);
  });
});
