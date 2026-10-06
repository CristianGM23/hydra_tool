import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GrammarTextualCustomization } from './GrammarTextualCustomization';
import type { GrammarDefinitionModel, GrammarSymbol } from './types';

const model:GrammarDefinitionModel={
    schemaVersion:'1.0',id:'g',name:'G',technology:'LANGIUM',authoringMode:'GRAMMAR_FIRST',metamodelId:'',toolchainProfileId:'',entryDocumentId:'main',entryRule:'Model',
    documents:[{id:'main',relativePath:'main.langium',languageId:'langium',content:"grammar G\nModel: name=ID;\nterminal ID: /[a-z]+/;",sourceHash:'',entryPoint:true,generated:false}],
    semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}},syntaxStyleDefinitionId:'',settings:{},annotations:{},
};
const range={start:{line:0,column:0},end:{line:0,column:5}};
const symbols:GrammarSymbol[]=[
    {id:'rule:Model',kind:'RULE',name:'Model',documentId:'main',range,parentId:'',detail:''},
    {id:'terminal:ID',kind:'TERMINAL',name:'ID',documentId:'main',range,parentId:'',detail:''},
];

describe('GrammarTextualCustomization',()=>{
    it('uses a supported font selector rather than arbitrary free text',()=>{
        render(<GrammarTextualCustomization mode="APPEARANCE" model={model} symbols={symbols} onChange={vi.fn()}/>);
        const font=screen.getByLabelText('Font') as HTMLSelectElement;
        expect(font.tagName).toBe('SELECT');
        expect(font).toHaveTextContent('Cascadia Code');
        expect(font).toHaveTextContent('JetBrains Mono');
        expect(font).toHaveTextContent('Fira Code');
    });

    it('stores a parser-rule-specific color without changing the general rule color',()=>{
        const onChange=vi.fn();
        render(<GrammarTextualCustomization mode="APPEARANCE" model={model} symbols={symbols} onChange={onChange}/>);
        fireEvent.change(screen.getByLabelText('Scope'),{target:{value:'RULE'}});
        expect(screen.getByLabelText('Parser rule')).toHaveValue('Model');
        fireEvent.change(screen.getByLabelText('Color') as HTMLInputElement,{target:{value:'#123456'}});
        const next=onChange.mock.calls.at(-1)?.[0] as GrammarDefinitionModel;
        expect(next.settings['hydra.textual.symbol.RULE.Model']).toBe('#123456|1|0');
        expect(next.settings['hydra.textual.role.RULE']).toBe('#7c3aed|1|0');
    });

    it('stores terminal-specific styling independently and can return to inheritance',()=>{
        const configured={...model,settings:{'hydra.textual.symbol.TERMINAL.ID':'#556677|0|1'}};
        const onChange=vi.fn();
        render(<GrammarTextualCustomization mode="APPEARANCE" model={configured} symbols={symbols} onChange={onChange}/>);
        fireEvent.change(screen.getByLabelText('Scope'),{target:{value:'TERMINAL'}});
        expect(screen.getByLabelText('Terminal')).toHaveValue('ID');
        fireEvent.click(screen.getByRole('button',{name:'Use general style'}));
        const next=onChange.mock.calls.at(-1)?.[0] as GrammarDefinitionModel;
        expect(next.settings['hydra.textual.symbol.TERMINAL.ID']).toBeUndefined();
    });

    it('keeps contextual fields bounded and targeted to grammar symbols',()=>{
        render(<GrammarTextualCustomization mode="HOVER" model={model} symbols={symbols} onChange={vi.fn()}/>);
        const selector=screen.getByLabelText('Grammar element');
        expect(selector).toHaveTextContent('Model');
        expect(selector).toHaveTextContent('ID');
        expect(screen.getByLabelText('Title')).toHaveAttribute('maxlength','96');
        expect(screen.getByLabelText('Explanation')).toHaveAttribute('maxlength','600');
    });
});
