import type { GrammarDefinitionModel, GrammarSourceDocument, GrammarTechnology } from './types';

function documentFor(technology: GrammarTechnology): GrammarSourceDocument {
    if (technology === 'XTEXT') return { id:'main', relativePath:'src/Main.xtext', languageId:'xtext', content:"grammar hydra.generated.Main with org.eclipse.xtext.common.Terminals\n\ngenerate main 'https://hydra.local/main'\n\nModel: elements+=Element*;\nElement: 'element' name=ID;\n", sourceHash:'', entryPoint:true, generated:false };
    if (technology === 'ANTLR') return { id:'main', relativePath:'Main.g4', languageId:'antlr', content:"grammar Main;\nmodel: element* EOF;\nelement: 'element' ID ';';\nWS: [ \\t\\r\\n]+ -> skip;\n", sourceHash:'', entryPoint:true, generated:false };
    return { id:'main', relativePath:'main.langium', languageId:'langium', content:"grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n", sourceHash:'', entryPoint:true, generated:false };
}

/** New projects use explicit, pinned profiles. Legacy profiles remain selectable for existing projects. */
export function defaultToolchain(technology: GrammarTechnology): string {
    if (technology === 'XTEXT') return 'xtext-2.44-lsp';
    if (technology === 'ANTLR') return 'antlr-4.13.2-java';
    return 'langium-4.3-browser';
}

export function createGrammar(technology: GrammarTechnology='LANGIUM'): GrammarDefinitionModel {
    const doc=documentFor(technology);
    return {schemaVersion:'1.0',id:'main-language',name:'Main language',technology,authoringMode:'GRAMMAR_FIRST',metamodelId:'',toolchainProfileId:defaultToolchain(technology),entryDocumentId:doc.id,entryRule:technology==='ANTLR'?'model':'Model',documents:[doc],semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}},syntaxStyleDefinitionId:'',settings:{},annotations:{'hydra.entryRule.mode':'AUTO'}};
}

export function changeTechnology(model: GrammarDefinitionModel, technology: GrammarTechnology): GrammarDefinitionModel {
    if(model.technology===technology)return model;
    if(model.documents.some(document=>document.content.trim()!==''))throw new Error('Existing grammar sources require the safe conversion preview; direct technology replacement is not allowed.');
    const doc=documentFor(technology);
    return {...model,technology,toolchainProfileId:defaultToolchain(technology),entryDocumentId:doc.id,entryRule:technology==='ANTLR'?'model':'Model',documents:[doc]};
}
