import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GrammarProblemsPanel } from './GrammarProblemsPanel';
import type { GrammarCodeAction, GrammarDiagnostic } from './types';

const diagnostic:GrammarDiagnostic={
    code:'GRAMMAR_LOCAL_MISSING_RULE_TERMINATOR',severity:'ERROR',title:'Incomplete grammar rule',message:'Rule “Test” is missing its final semicolon.',
    cause:'Missing terminator.',consequence:'Following diagnostics may be ambiguous.',solution:'Add “;” at the end of the rule.',
    documentId:'main',range:{start:{line:2,column:20},end:{line:2,column:20}},rule:'Test',property:'',expectedType:'',nativeCode:'',technicalDetail:'',candidates:[],
    codeActionIds:['fix:semicolon'],metadata:{}
};
const action:GrammarCodeAction={
    id:'fix:semicolon',title:'Add missing “;”',kind:'quickfix',preferred:true,safeAutomatic:true,
    edits:[{documentId:'main',range:diagnostic.range,replacement:';'}],preconditions:{},metadata:{automaticResolution:'true'}
};

describe('GrammarProblemsPanel quick-fix visibility',()=>{
    it('shows linked quick fixes directly in Problems without requiring Details',()=>{
        const apply=vi.fn();
        render(<GrammarProblemsPanel diagnostics={[diagnostic]} actions={[action]} onNavigate={vi.fn()} onApply={apply}/>);
        expect(screen.getByLabelText('Quick fixes')).toBeInTheDocument();
        const fix=screen.getByRole('button',{name:/Resolve automatically.*Add missing/i});
        expect(fix).toBeVisible();
        fireEvent.click(fix);
        expect(apply).toHaveBeenCalledOnce();
        expect(apply).toHaveBeenCalledWith(action);
    });

    it('keeps explanation details separate from the always-visible repair action',()=>{
        render(<GrammarProblemsPanel diagnostics={[diagnostic]} actions={[action]} onNavigate={vi.fn()} onApply={vi.fn()}/>);
        expect(screen.queryByText('Missing terminator.')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button',{name:'Details'}));
        expect(screen.getByText('Missing terminator.')).toBeInTheDocument();
        expect(screen.getAllByRole('button',{name:/Add missing/i})).toHaveLength(1);
    });
});
