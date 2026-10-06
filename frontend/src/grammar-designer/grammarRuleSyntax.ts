import type { GrammarDefinitionModel, GrammarSourceDocument, TextRange } from './types';

export type GrammarSyntaxKind = 'SEQUENCE'|'CHOICE'|'LITERAL'|'RULE'|'TOKEN'|'ASSIGNMENT'|'CROSS_REFERENCE'|'ACTION'|'OPTIONAL'|'ZERO_OR_MORE'|'ONE_OR_MORE'|'EMPTY'|'OTHER';
export interface GrammarSyntaxNode { kind: GrammarSyntaxKind; label?: string; children?: GrammarSyntaxNode[]; }
export interface GrammarSyntaxRule { name:string; documentId:string; range:TextRange; parser:boolean; body:string; syntax:GrammarSyntaxNode; }

type TokenKind='IDENT'|'LITERAL'|'REGEX'|'LPAREN'|'RPAREN'|'PIPE'|'QUESTION'|'STAR'|'PLUS'|'ASSIGN'|'CROSS'|'ACTION'|'OTHER'|'EOF';
interface Token {kind:TokenKind;text:string;}
interface OffsetRange {start:number;end:number;}

/** Parses the current grammar into a provider-neutral syntax structure used only for visualization. */
export function parseGrammarSyntax(model: GrammarDefinitionModel): GrammarSyntaxRule[] {
    const raw: Array<{name:string;document:GrammarSourceDocument;nameRange:OffsetRange;parser:boolean;body:string}> = [];
    for (const document of model.documents) raw.push(...extractRules(model, document));
    const parserNames = new Set(raw.filter(rule=>rule.parser).map(rule=>rule.name));
    const tokenNames = new Set(raw.filter(rule=>!rule.parser).map(rule=>rule.name));
    return raw.map(rule=>({
        name:rule.name,
        documentId:rule.document.id,
        range:toTextRange(rule.document.content, rule.nameRange),
        parser:rule.parser,
        body:rule.body,
        syntax:new SyntaxParser(tokenize(rule.body),parserNames,tokenNames).parse()
    }));
}

function extractRules(model:GrammarDefinitionModel,document:GrammarSourceDocument){
    const source=document.content??'';
    const masked=maskComments(source);
    const pattern=model.technology==='ANTLR'
        ? /^\s*(?:(fragment)\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:\[[^\]\r\n]*\]\s*)?(?:returns\s+\[[^\]\r\n]*\]\s*)?(?:locals\s+\[[^\]\r\n]*\]\s*)?:/gm
        : /^\s*(?:(entry)\s+)?(?:(hidden)\s+)?(?:(terminal|fragment)\s+)?([A-Za-z_][A-Za-z0-9_]*)\b[^\r\n:;]*:/gm;
    const result:Array<{name:string;document:GrammarSourceDocument;nameRange:OffsetRange;parser:boolean;body:string}>=[];
    let match:RegExpExecArray|null;
    while((match=pattern.exec(masked))!==null){
        const name=model.technology==='ANTLR'?match[2]:match[4];
        if(model.technology!=='ANTLR'&&['grammar','import'].includes(name.toLowerCase())){pattern.lastIndex=Math.max(pattern.lastIndex,source.indexOf('\n',match.index)+1);continue;}
        const terminal=model.technology==='ANTLR'?Boolean(match[1])||/^[A-Z]/.test(name):Boolean(match[3]);
        const parser=!terminal&&(model.technology!=='ANTLR'||/^[a-z]/.test(name));
        const nameOffset=match.index+match[0].lastIndexOf(name);
        const colonOffset=match.index+match[0].lastIndexOf(':');
        const end=findRuleEnd(source,colonOffset+1,model.technology==='LANGIUM');
        result.push({name,document,nameRange:{start:nameOffset,end:nameOffset+name.length},parser,body:source.slice(colonOffset+1,end)});
        pattern.lastIndex=Math.max(pattern.lastIndex,end+1);
    }
    return result;
}

function findRuleEnd(source:string,start:number,slashRegex:boolean):number{
    let quote='';let escaped=false;let line=false;let block=false;let regex=false;
    for(let i=start;i<source.length;i++){
        const ch=source[i],next=source[i+1]??'';
        if(line){if(ch==='\n'||ch==='\r')line=false;continue;}
        if(block){if(ch==='*'&&next==='/'){block=false;i++;}continue;}
        if(quote){if(escaped){escaped=false;continue;}if(ch==='\\'){escaped=true;continue;}if(ch===quote)quote='';continue;}
        if(regex){if(escaped){escaped=false;continue;}if(ch==='\\'){escaped=true;continue;}if(ch==='/')regex=false;continue;}
        if(ch==='/'&&next==='/'){line=true;i++;continue;} if(ch==='/'&&next==='*'){block=true;i++;continue;}
        if(ch==="'"||ch==='"'){quote=ch;continue;}
        if(slashRegex&&ch==='/'&&looksLikeRegexStart(source,i)){regex=true;continue;}
        if(ch===';')return i;
    }
    return source.length;
}
function looksLikeRegexStart(source:string,index:number){let i=index-1;while(i>=0&&/\s/.test(source[i]))i--;return i<0||':=|,(?'.includes(source[i]);}

function tokenize(source:string):Token[]{
    const tokens:Token[]=[];let i=0;
    while(i<source.length){
        const ch=source[i],next=source[i+1]??'';
        if(/\s/.test(ch)){i++;continue;}
        if(ch==='/'&&next==='/'){i+=2;while(i<source.length&&!/[\r\n]/.test(source[i]))i++;continue;}
        if(ch==='/'&&next==='*'){i+=2;while(i<source.length&&!(source[i]==='*'&&source[i+1]==='/'))i++;i=Math.min(source.length,i+2);continue;}
        if(ch==="'"||ch==='"'){const start=i;const quote=ch;i++;let escaped=false;while(i<source.length){const c=source[i++];if(escaped){escaped=false;continue;}if(c==='\\'){escaped=true;continue;}if(c===quote)break;}tokens.push({kind:'LITERAL',text:source.slice(start,i)});continue;}
        if(ch==='/'&&looksLikeRegexStart(source,i)){const start=i++;let escaped=false;while(i<source.length){const c=source[i++];if(escaped){escaped=false;continue;}if(c==='\\'){escaped=true;continue;}if(c==='/')break;}while(i<source.length&&/[a-z]/i.test(source[i]))i++;tokens.push({kind:'REGEX',text:source.slice(start,i)});continue;}
        if(/[A-Za-z_]/.test(ch)){const start=i++;while(i<source.length&&/[A-Za-z0-9_.]/.test(source[i]))i++;tokens.push({kind:'IDENT',text:source.slice(start,i)});continue;}
        if(ch==='['){const start=i++;let depth=1;let quote='';let escaped=false;while(i<source.length&&depth>0){const c=source[i++];if(quote){if(escaped){escaped=false;continue;}if(c==='\\'){escaped=true;continue;}if(c===quote)quote='';continue;}if(c==="'"||c==='"'){quote=c;continue;}if(c==='[')depth++;if(c===']')depth--;}tokens.push({kind:'CROSS',text:source.slice(start,i)});continue;}
        if(ch==='{'){const start=i++;let depth=1;while(i<source.length&&depth>0){const c=source[i++];if(c==='{')depth++;else if(c==='}')depth--;}tokens.push({kind:'ACTION',text:source.slice(start,i)});continue;}
        if(ch==='+'&&next==='='){tokens.push({kind:'ASSIGN',text:'+='});i+=2;continue;}
        if(ch==='?'&&next==='='){tokens.push({kind:'ASSIGN',text:'?='});i+=2;continue;}
        if(ch==='='){tokens.push({kind:'ASSIGN',text:'='});i++;continue;}
        const single:Record<string,TokenKind>={'(':'LPAREN',')':'RPAREN','|':'PIPE','?':'QUESTION','*':'STAR','+':'PLUS'};
        if(single[ch]){tokens.push({kind:single[ch],text:ch});i++;continue;}
        tokens.push({kind:'OTHER',text:ch});i++;
    }
    tokens.push({kind:'EOF',text:''});return tokens;
}

class SyntaxParser{
    private index=0;
    private readonly tokens:Token[];
    private readonly parserNames:Set<string>;
    private readonly tokenNames:Set<string>;
    constructor(tokens:Token[],parserNames:Set<string>,tokenNames:Set<string>){
        this.tokens=tokens;
        this.parserNames=parserNames;
        this.tokenNames=tokenNames;
    }
    parse():GrammarSyntaxNode{return this.choice();}
    private peek(offset=0){return this.tokens[Math.min(this.tokens.length-1,this.index+offset)];}
    private take(){return this.tokens[this.index++];}
    private choice():GrammarSyntaxNode{
        const branches=[this.sequence()];
        while(this.peek().kind==='PIPE'){this.take();branches.push(this.sequence());}
        return branches.length===1?branches[0]:{kind:'CHOICE',children:branches};
    }
    private sequence():GrammarSyntaxNode{
        const children:GrammarSyntaxNode[]=[];
        while(!['EOF','RPAREN','PIPE'].includes(this.peek().kind))children.push(this.quantified());
        if(children.length===0)return {kind:'EMPTY',label:'ε'};
        return children.length===1?children[0]:{kind:'SEQUENCE',children};
    }
    private quantified():GrammarSyntaxNode{
        let child=this.primary();
        const q=this.peek().kind;
        if(q==='QUESTION'||q==='STAR'||q==='PLUS'){
            this.take();
            child={kind:q==='QUESTION'?'OPTIONAL':q==='STAR'?'ZERO_OR_MORE':'ONE_OR_MORE',children:[child]};
        }
        return child;
    }
    private primary():GrammarSyntaxNode{
        const token=this.peek();
        if(token.kind==='LPAREN'){this.take();const child=this.choice();if(this.peek().kind==='RPAREN')this.take();return child;}
        if(token.kind==='IDENT'&&this.peek(1).kind==='ASSIGN'){
            const name=this.take().text,op=this.take().text;const rhs=this.quantified();
            return {kind:'ASSIGNMENT',label:`${name} ${op}`,children:[rhs]};
        }
        this.take();
        if(token.kind==='LITERAL')return {kind:'LITERAL',label:token.text};
        if(token.kind==='REGEX')return {kind:'TOKEN',label:token.text};
        if(token.kind==='CROSS')return {kind:'CROSS_REFERENCE',label:token.text};
        if(token.kind==='ACTION')return {kind:'ACTION',label:token.text};
        if(token.kind==='IDENT'){
            if(this.parserNames.has(token.text))return {kind:'RULE',label:token.text};
            if(this.tokenNames.has(token.text)||/^[A-Z_][A-Z0-9_]*$/.test(token.text))return {kind:'TOKEN',label:token.text};
            return {kind:'OTHER',label:token.text};
        }
        return {kind:'OTHER',label:token.text};
    }
}

function maskComments(source:string){
    const chars=[...source];let line=false,block=false,quote='';let escaped=false;
    for(let i=0;i<chars.length;i++){const ch=source[i],next=source[i+1]??'';
        if(line){if(ch==='\n'||ch==='\r')line=false;else if(chars[i]!==undefined)chars[i]=' ';continue;}
        if(block){if(ch==='*'&&next==='/'){chars[i]=' ';chars[i+1]=' ';block=false;i++;}else if(chars[i]!==undefined&&!/[\r\n]/.test(ch))chars[i]=' ';continue;}
        if(quote){if(escaped){escaped=false;continue;}if(ch==='\\'){escaped=true;continue;}if(ch===quote)quote='';continue;}
        if(ch==="'"||ch==='"'){quote=ch;continue;}if(ch==='/'&&next==='/'){chars[i]=' ';chars[i+1]=' ';line=true;i++;continue;}if(ch==='/'&&next==='*'){chars[i]=' ';chars[i+1]=' ';block=true;i++;continue;}
    }return chars.join('');
}
function toTextRange(source:string,range:OffsetRange):TextRange{return {start:positionAt(source,range.start),end:positionAt(source,range.end)};}
function positionAt(source:string,offset:number){let line=0,column=0;for(let i=0;i<Math.min(offset,source.length);i++){if(source[i]==='\r'){if(source[i+1]==='\n')i++;line++;column=0;}else if(source[i]==='\n'){line++;column=0;}else column++;}return {line,column};}
