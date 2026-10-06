import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GrammarMonacoEditor } from './GrammarMonacoEditor';
import type { GrammarDiagnostic } from './types';

const diagnostic:GrammarDiagnostic={
    code:'REFERENCE_NOT_FOUND',severity:'ERROR',title:'Unresolved reference',message:'Missing cannot be resolved.',
    cause:'',consequence:'',solution:'',documentId:'main',range:{start:{line:1,column:13},end:{line:1,column:20}},
    rule:'Model',property:'elements',expectedType:'RULE',nativeCode:'',technicalDetail:'',candidates:[],codeActionIds:[],metadata:{}
};

describe('GrammarMonacoEditor safe fallback',()=>{
    it('shows line numbers and marks the exact diagnostic range when Monaco is unavailable',()=>{
        window.monaco=undefined;
        const {container}=render(<GrammarMonacoEditor value={'grammar Main\nentry Model: Missing;\n'} languageId="langium" diagnostics={[diagnostic]} onChange={()=>undefined}/>);
        const gutter=container.querySelector('.grammar-safe-gutter');
        expect(gutter).not.toBeNull();
        expect(within(gutter as HTMLElement).getByText('1')).toBeInTheDocument();
        expect(within(gutter as HTMLElement).getByText('2')).toHaveClass('has-error');
        expect(container.querySelector('mark.grammar-source-diagnostic.error')).toHaveTextContent('Missing');
        expect(screen.getByLabelText('Grammar source editor')).toBeInTheDocument();
        expect(container.querySelector('.grammar-monaco-feedback')).toBeNull();
        expect(screen.queryByText('Missing cannot be resolved.')).not.toBeInTheDocument();
    });

    it('clamps stale navigation ranges and leaves only a caret so Delete and Backspace remove normal text characters',()=>{
        window.monaco=undefined;
        const stale={start:{line:50,column:200},end:{line:75,column:300}};
        render(<GrammarMonacoEditor value={'Model: ID;'} languageId="langium" diagnostics={[]} focusRange={stale} onChange={()=>undefined}/>);
        const editor=screen.getByLabelText('Grammar source editor') as HTMLTextAreaElement;
        expect(editor.selectionStart).toBe('Model: ID;'.length);
        expect(editor.selectionEnd).toBe(editor.selectionStart);
    });

    it('keeps rendering when a diagnostic range refers to text that was just deleted',()=>{
        window.monaco=undefined;
        const staleDiagnostic={...diagnostic,range:{start:{line:12,column:40},end:{line:12,column:55}}};
        const {container}=render(<GrammarMonacoEditor value={'M'} languageId="langium" diagnostics={[staleDiagnostic]} onChange={()=>undefined}/>);
        expect(container.querySelector('.grammar-safe-editor')).not.toBeNull();
        expect(screen.getByLabelText('Grammar source editor')).toHaveValue('M');
    });

    it('opens grammar-aware suggestions with Ctrl+Space in the safe editor and applies the selected item',()=>{
        window.monaco=undefined;
        let value='ele';
        const {rerender}=render(<GrammarMonacoEditor value={value} languageId="model" diagnostics={[]} completionProvider={()=>[{label:'elementSsss',insertText:'elementSsss',kind:'LITERAL',detail:'Literal · Element',replaceStart:0,replaceEnd:3}]} onChange={next=>{value=next;}}/>);
        const editor=screen.getByLabelText('Grammar source editor') as HTMLTextAreaElement;
        editor.setSelectionRange(3,3);
        fireEvent.keyDown(editor,{code:'Space',key:' ',ctrlKey:true});
        expect(screen.getByRole('listbox',{name:'Grammar-aware suggestions'})).toBeInTheDocument();
        fireEvent.keyDown(editor,{key:'Enter',code:'Enter'});
        rerender(<GrammarMonacoEditor value={value} languageId="model" diagnostics={[]} completionProvider={()=>[]} onChange={next=>{value=next;}}/>);
        expect(screen.getByLabelText('Grammar source editor')).toHaveValue('elementSsss');
    });
});
