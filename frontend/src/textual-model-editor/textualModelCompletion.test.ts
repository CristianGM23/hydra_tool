import { describe, expect, it } from 'vitest';
import type { GrammarDefinitionModel, GrammarTechnology } from '../grammar-designer/types';
import { textualModelCompletions } from './textualModelCompletion';

function grammar(technology:GrammarTechnology,entryRule:string,content:string):GrammarDefinitionModel{
    return {schemaVersion:'1.0',id:'main',name:'Main',technology,authoringMode:'GRAMMAR_FIRST',metamodelId:'',toolchainProfileId:'',entryDocumentId:'main',entryRule,
        documents:[{id:'main',relativePath:'main',languageId:technology.toLowerCase(),content,sourceHash:'',entryPoint:true,generated:false}],semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}},syntaxStyleDefinitionId:'',settings:{},annotations:{}};
}

const langium=grammar('LANGIUM','Model',`grammar Main
entry Model: elements+=Element*;
Element: 'elementSsss' name=ID surname=ID;
hidden terminal WS: /\\s+/;
terminal ID: /[_a-zA-Z][\\w_]*/;`);

describe('grammar-aware textual model completion',()=>{
    it('proposes the literal required by the entry rule and filters a partially typed literal',()=>{
        expect(textualModelCompletions(langium,'',0)[0]).toMatchObject({insertText:'elementSsss',kind:'LITERAL',ruleName:'Element'});
        expect(textualModelCompletions(langium,'ele',3)[0]).toMatchObject({insertText:'elementSsss',replaceStart:0,replaceEnd:3});
    });

    it('walks assignments in order and uses the assignment name for terminal samples',()=>{
        expect(textualModelCompletions(langium,'elementSsss ',12)[0]).toMatchObject({insertText:'name1',expected:'ID',propertyName:'name'});
        expect(textualModelCompletions(langium,'elementSsss john ',17)[0]).toMatchObject({insertText:'surname1',expected:'ID',propertyName:'surname'});
    });

    it('continues a repeated entry rule after a complete element',()=>{
        expect(textualModelCompletions(langium,'elementSsss john doe ',21)[0]).toMatchObject({insertText:'elementSsss',kind:'LITERAL'});
    });

    it('keeps alternatives and optional branches available at the exact legal cursor position',()=>{
        const rich=grammar('LANGIUM','Model',`grammar Main
entry Model: (elements+=Element | groups+=Group)*;
Element: 'element' name=ID ('age' age=INT)? ('role' role=('admin'|'user'))?;
Group: 'group' name=ID;
terminal ID: /[_a-zA-Z][\\w_]*/;
terminal INT: /[0-9]+/;`);
        expect(textualModelCompletions(rich,'',0).map(item=>item.insertText)).toEqual(['element','group']);
        expect(textualModelCompletions(rich,'element joe ',12).map(item=>item.insertText)).toEqual(expect.arrayContaining(['age','role','element','group']));
        expect(textualModelCompletions(rich,'element joe role ',17).map(item=>item.insertText)).toEqual(['admin','user']);
    });

    it('remains bounded and useful for a large repeated model',()=>{
        const text=Array.from({length:5_000},(_,index)=>`elementSsss n${index} s${index}`).join(' ')+' ';
        expect(textualModelCompletions(langium,text,text.length)[0]?.insertText).toBe('elementSsss');
    });

    it('works for Xtext and ANTLR through the neutral grammar syntax tree',()=>{
        const xtext=grammar('XTEXT','Model',`grammar demo.Main with org.eclipse.xtext.common.Terminals
Model: elements+=Element*;
Element: 'thing' name=ID label=STRING;`);
        const antlr=grammar('ANTLR','model',`grammar Main;
model: element* EOF;
element: 'entry' ID;
ID: [a-zA-Z_][a-zA-Z0-9_]*;`);
        expect(textualModelCompletions(xtext,'',0)[0]?.insertText).toBe('thing');
        expect(textualModelCompletions(xtext,'thing ',6)[0]).toMatchObject({insertText:'name1',expected:'ID'});
        expect(textualModelCompletions(antlr,'',0)[0]?.insertText).toBe('entry');
    });
});
