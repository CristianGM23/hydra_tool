import { describe, expect, it } from 'vitest';
import { parseGrammarSyntax, type GrammarSyntaxNode } from './grammarRuleSyntax';
import type { GrammarDefinitionModel, GrammarTechnology } from './types';

describe('grammar rule syntax visualization',()=>{
    it('preserves alternatives, literals, references, assignments, optionals and loops',()=>{
        const rules=parseGrammarSyntax(model('LANGIUM',`grammar Main\nentry Model: ('begin' items+=Item+ | 'empty') ';'?;\nItem: 'item' name=ID ('parent' parent=[Item|ID])?;\nterminal ID: /[a-z]+/;`));
        expect(rules.map(rule=>rule.name)).toEqual(['Model','Item','ID']);
        const kinds=new Set<string>();walk(rules[0].syntax,node=>kinds.add(node.kind));
        expect(kinds.has('CHOICE')).toBe(true);
        expect(kinds.has('LITERAL')).toBe(true);
        expect(kinds.has('ASSIGNMENT')).toBe(true);
        expect(kinds.has('ONE_OR_MORE')).toBe(true);
        expect(kinds.has('OPTIONAL')).toBe(true);
        const itemKinds=new Set<string>();walk(rules[1].syntax,node=>itemKinds.add(node.kind));
        expect(itemKinds.has('CROSS_REFERENCE')).toBe(true);
    });
    it('parses ANTLR parser and lexer rules independently',()=>{
        const rules=parseGrammarSyntax(model('ANTLR',`grammar Main;\nmodel: element* EOF;\nelement: 'item' ID;\nID: [a-zA-Z]+;`));
        expect(rules.map(rule=>[rule.name,rule.parser])).toEqual([['model',true],['element',true],['ID',false]]);
    });
});
function walk(node:GrammarSyntaxNode,visit:(node:GrammarSyntaxNode)=>void){visit(node);for(const child of node.children??[])walk(child,visit);}
function model(technology:GrammarTechnology,source:string):GrammarDefinitionModel{return {schemaVersion:'1.0',id:'test',name:'Test',technology,authoringMode:'GRAMMAR_FIRST',metamodelId:'',toolchainProfileId:'',entryDocumentId:'main',entryRule:'Model',documents:[{id:'main',relativePath:`main.${technology==='ANTLR'?'g4':technology.toLowerCase()}`,languageId:technology.toLowerCase(),content:source,sourceHash:'',entryPoint:true,generated:false}],semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}},syntaxStyleDefinitionId:'',settings:{},annotations:{'hydra.entryRule.mode':'AUTO'}};}
