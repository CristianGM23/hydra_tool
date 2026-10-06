import { describe, expect, it } from 'vitest';
import type { GrammarDefinitionModel, GrammarTechnology } from './types';
import { buildGrammarTextualExample } from './grammarTextualExample';

function grammar(technology:GrammarTechnology, entryRule:string, content:string):GrammarDefinitionModel {
    return { schemaVersion:'1.0', id:'main', name:'Main', technology, authoringMode:'GRAMMAR_FIRST', metamodelId:'', toolchainProfileId:'', entryDocumentId:'main', entryRule,
        documents:[{id:'main',relativePath:'main',languageId:technology.toLowerCase(),content,sourceHash:'',entryPoint:true,generated:false}], semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}}, syntaxStyleDefinitionId:'',settings:{},annotations:{} };
}

describe('grammar textual preview', () => {
    it('tracks the current Langium literals and assignments', () => {
        const model=grammar('LANGIUM','Model',`grammar Main
entry Model: elements+=Element*;
Element: 'elementSsss' name=ID surname=ID;
hidden terminal WS: /\\s+/;
terminal ID: /[_a-zA-Z][\\w_]*/;`);
        expect(buildGrammarTextualExample(model)).toBe('elementSsss name1 surname1');
        model.documents[0]={...model.documents[0],content:model.documents[0].content.replace('elementSsss','renamedElement')};
        expect(buildGrammarTextualExample(model)).toBe('renamedElement name1 surname1');
    });
    it('supports Xtext and ANTLR parser rules', () => {
        expect(buildGrammarTextualExample(grammar('XTEXT','Model',`grammar demo.Main with org.eclipse.xtext.common.Terminals
Model: elements+=Element*;
Element: 'thing' name=ID label=STRING;`))).toBe('thing name1 "label"');
        expect(buildGrammarTextualExample(grammar('ANTLR','model',`grammar Main;
model: element* EOF;
element: 'entry' ID;
ID: [a-zA-Z_][a-zA-Z0-9_]*;`))).toBe('entry value1');
    });
});
