import { defaultTextualCustomization, type GrammarTextualCustomization } from './textualCustomization';

export interface GeneratedCodeLanguageProfile {
    languageId:string;
    label:string;
    keywords:readonly string[];
}

const TYPESCRIPT_KEYWORDS = words(`break case catch class const continue debugger default delete do else export extends false finally for function if import in instanceof let new null return super switch this throw true try typeof var void while with yield async await interface type enum implements private protected public readonly abstract declare namespace module as satisfies keyof infer unknown never any number string boolean object symbol bigint undefined of`);
const JAVA_KEYWORDS = words(`abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null record sealed permits non-sealed var yield`);
const PYTHON_KEYWORDS = words(`False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case`);
const XTEXT_KEYWORDS = words(`grammar with hidden import generate as returns current terminal enum fragment true false`);
const LANGIUM_KEYWORDS = words(`grammar entry hidden terminal fragment returns infer infers interface type import true false extends`);
const ANTLR_KEYWORDS = words(`grammar lexer parser fragment returns locals throws catch finally mode channels tokens options import true false`);
const SHELL_KEYWORDS = words(`if then else elif fi for while until do done case esac function in select time coproc export local readonly return break continue shift source`);
const CSS_KEYWORDS = words(`@media @supports @import @font-face @keyframes from to inherit initial unset revert important`);
const YAML_KEYWORDS = words(`true false null yes no on off`);
const JSON_KEYWORDS = words(`true false null`);

/** Resolves a generated artifact to the closest editor language and syntax-keyword profile. */
export function generatedCodeLanguageProfile(path:string):GeneratedCodeLanguageProfile {
    const normalized=path.toLowerCase();
    if(normalized.endsWith('.tsx'))return profile('typescript','TypeScript React',TYPESCRIPT_KEYWORDS);
    if(normalized.endsWith('.ts')||normalized.endsWith('.mts')||normalized.endsWith('.cts'))return profile('typescript','TypeScript',TYPESCRIPT_KEYWORDS);
    if(normalized.endsWith('.jsx'))return profile('javascript','JavaScript React',TYPESCRIPT_KEYWORDS);
    if(normalized.endsWith('.js')||normalized.endsWith('.mjs')||normalized.endsWith('.cjs'))return profile('javascript','JavaScript',TYPESCRIPT_KEYWORDS);
    if(normalized.endsWith('.java'))return profile('java','Java',JAVA_KEYWORDS);
    if(normalized.endsWith('.json')||normalized.endsWith('.jsonc'))return profile('json','JSON',JSON_KEYWORDS);
    if(normalized.endsWith('.xtext'))return profile('xtext','Xtext',XTEXT_KEYWORDS);
    if(normalized.endsWith('.langium'))return profile('langium','Langium',LANGIUM_KEYWORDS);
    if(normalized.endsWith('.g4'))return profile('antlr','ANTLR',ANTLR_KEYWORDS);
    if(normalized.endsWith('.py'))return profile('python','Python',PYTHON_KEYWORDS);
    if(normalized.endsWith('.sh')||normalized.endsWith('.bash')||normalized.endsWith('.zsh'))return profile('shell','Shell',SHELL_KEYWORDS);
    if(normalized.endsWith('.css')||normalized.endsWith('.scss'))return profile('css','CSS',CSS_KEYWORDS);
    if(normalized.endsWith('.yaml')||normalized.endsWith('.yml'))return profile('yaml','YAML',YAML_KEYWORDS);
    if(normalized.endsWith('.xml')||normalized.endsWith('.ecore')||normalized.endsWith('.genmodel'))return profile('xml','XML',[]);
    if(normalized.endsWith('.html')||normalized.endsWith('.htm'))return profile('html','HTML',[]);
    if(normalized.endsWith('.md')||normalized.endsWith('.markdown'))return profile('markdown','Markdown',[]);
    if(normalized.endsWith('.properties'))return profile('properties','Properties',[]);
    if(normalized.endsWith('.gradle'))return profile('groovy','Gradle',words(`plugins repositories dependencies implementation api testImplementation id version java application group tasks`));
    return profile('plaintext','Text',[]);
}

/** Builds a read-only syntax theme for generated code without mutating the grammar's editor appearance settings. */
export function generatedCodeCustomization(profile:GeneratedCodeLanguageProfile):GrammarTextualCustomization {
    const customization=defaultTextualCustomization();
    customization.keywords=[...profile.keywords];
    customization.fontSize=13;
    customization.lineHeight=20;
    customization.roleStyles.KEYWORD={color:'#1d4ed8',bold:true,italic:false};
    customization.roleStyles.TYPE={color:'#7c3aed',bold:true,italic:false};
    customization.roleStyles.STRING={color:'#047857',bold:false,italic:false};
    customization.roleStyles.NUMBER={color:'#b45309',bold:false,italic:false};
    customization.roleStyles.COMMENT={color:'#64748b',bold:false,italic:true};
    customization.roleStyles.OPERATOR={color:'#334155',bold:false,italic:false};
    customization.roleStyles.TOKEN={color:'#0f172a',bold:false,italic:false};
    return customization;
}

function words(value:string):readonly string[]{return value.trim().split(/\s+/).filter(Boolean);}
function profile(languageId:string,label:string,keywords:readonly string[]):GeneratedCodeLanguageProfile{return {languageId,label,keywords};}
