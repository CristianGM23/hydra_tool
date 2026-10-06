import { describe, expect, it } from 'vitest';
import {
    applyCustomizationToGrammar,
    customizationFromGrammar,
    defaultTextualCustomization,
    styleForToken,
    hoverForToken,
    effectiveTextualCustomization,
    symbolStyleKey,
} from './textualCustomization';
import type { GrammarDefinitionModel } from './types';

function grammar(settings:Record<string,string>={}):GrammarDefinitionModel{return {
    schemaVersion:'1.0',id:'g',name:'G',technology:'LANGIUM',authoringMode:'GRAMMAR_FIRST',metamodelId:'',toolchainProfileId:'',entryDocumentId:'main',entryRule:'Model',
    documents:[],semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}},syntaxStyleDefinitionId:'',settings,annotations:{},
};}

describe('textual customization',()=>{
    it('round-trips bounded rule and terminal style overrides',()=>{
        const base=defaultTextualCustomization();
        const customized={...base,symbolStyles:{
            [symbolStyleKey('RULE','Model')]:{color:'#123456',bold:true,italic:false},
            [symbolStyleKey('TERMINAL','ID')]:{color:'#654321',bold:false,italic:true},
        }};
        const stored=applyCustomizationToGrammar(grammar(),customized);
        expect(stored.settings['hydra.textual.symbol.RULE.Model']).toBe('#123456|1|0');
        expect(stored.settings['hydra.textual.symbol.TERMINAL.ID']).toBe('#654321|0|1');
        const restored=customizationFromGrammar(stored);
        expect(restored.symbolStyles['RULE:Model']).toEqual({color:'#123456',bold:true,italic:false});
        expect(restored.symbolStyles['TERMINAL:ID']).toEqual({color:'#654321',bold:false,italic:true});
    });

    it('normalizes legacy TOKEN overrides and prefers specific symbols over general roles',()=>{
        const restored=customizationFromGrammar(grammar({'hydra.textual.symbol.TOKEN.ID':'#abcdef|1|1'}));
        expect(restored.symbolStyles['TERMINAL:ID']).toEqual({color:'#abcdef',bold:true,italic:true});
        expect(styleForToken(restored,'TOKEN','ID')).toEqual({color:'#abcdef',bold:true,italic:true});
        expect(styleForToken(restored,'TOKEN','OTHER')).toEqual(restored.roleStyles.TOKEN);
    });


    it('projects parser-rule and terminal personalization onto actual model tokens',()=>{
        const source={...grammar({
            'hydra.textual.symbol.RULE.Person':'#112233|1|0',
            'hydra.textual.symbol.TERMINAL.ID':'#445566|0|1',
            'hydra.textual.hover.Person.title':'Person declaration',
            'hydra.textual.hover.Person.description':'Creates one person.',
            'hydra.textual.hover.ID.title':'Identifier',
            'hydra.textual.hover.ID.description':'Stable model name.',
        }),documents:[{id:'main',relativePath:'main.langium',languageId:'langium',content:"grammar G\nPerson: 'person' name=ID;\nterminal ID: /[a-z]+/;",sourceHash:'',entryPoint:true,generated:false}]};
        const c=effectiveTextualCustomization(source);
        expect(c.keywords).toContain('person');
        expect(styleForToken(c,'KEYWORD','person')).toEqual({color:'#112233',bold:true,italic:false});
        expect(styleForToken(c,'TOKEN','alice')).toEqual({color:'#445566',bold:false,italic:true});
        expect(hoverForToken(c,'person')?.title).toBe('Person declaration');
        expect(hoverForToken(c,'alice')?.title).toBe('Identifier');
    });

    it('rejects unsafe or malformed symbol-specific keys',()=>{
        const base=defaultTextualCustomization();
        const stored=applyCustomizationToGrammar(grammar(),{...base,symbolStyles:{
            '__proto__:bad':{color:'#000000',bold:true,italic:true},
            'RULE:bad key!':{color:'#000000',bold:true,italic:true},
            'RULE:Good':{color:'#123456',bold:false,italic:false},
        }});
        expect(stored.settings['hydra.textual.symbol.RULE.Good']).toBe('#123456|0|0');
        expect(Object.keys(stored.settings).some(key=>key.includes('bad key!')||key.includes('__proto__'))).toBe(false);
    });
});
