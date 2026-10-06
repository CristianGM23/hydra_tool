import type { GrammarCodeAction, GrammarDiagnostic, GrammarSymbol, ReferenceCandidate, TextPosition } from './types';

export interface GrammarLocalFeedback {
    diagnostics: GrammarDiagnostic[];
    actions: GrammarCodeAction[];
}

/**
 * Turns deterministic browser-side grammar diagnostics into actionable feedback while
 * canonical backend analysis is still pending. These actions only edit the current
 * grammar source; they never mutate semantic state behind the user's back.
 */
export function buildLocalGrammarFeedback(diagnostics: readonly GrammarDiagnostic[], symbols: readonly GrammarSymbol[]): GrammarLocalFeedback {
    const declared = uniqueSymbols(symbols.filter(symbol => symbol.kind === 'RULE' || symbol.kind === 'TOKEN'));
    const actions: GrammarCodeAction[] = [];
    const enriched = diagnostics.map((diagnostic, index) => {
        if (diagnostic.code === 'GRAMMAR_LOCAL_UNDEFINED_REFERENCE') {
            const reference = diagnostic.metadata['reference'] ?? '';
            const candidates = nearestCandidates(reference, declared).slice(0, 4);
            const actionIds: string[] = [];
            candidates.forEach((candidate, candidateIndex) => {
                const id = localActionId(diagnostic, index, `replace-${candidateIndex}-${candidate.name}`);
                actionIds.push(id);
                actions.push({
                    id,
                    title: `Replace “${reference}” with “${candidate.name}”`,
                    kind: 'quickfix',
                    preferred: candidateIndex === 0 && candidate.score >= .72,
                    safeAutomatic: candidateIndex === 0 && candidate.score >= .82,
                    edits: [{ documentId: diagnostic.documentId, range: diagnostic.range, replacement: candidate.name }],
                    preconditions: { source: 'local-inspection', reference },
                    metadata: { source: 'local-inspection', automaticResolution: candidateIndex === 0 && candidate.score >= .82 ? 'true' : 'false' }
                });
            });
            return { ...diagnostic, candidates, codeActionIds: unique([...diagnostic.codeActionIds, ...actionIds]) };
        }
        if (diagnostic.code === 'GRAMMAR_LOCAL_MALFORMED_ENTRY') {
            const token = diagnostic.metadata['token'] ?? 'entry';
            const id = localActionId(diagnostic, index, 'repair-entry-keyword');
            actions.push({ id, title: `Replace “${token}” with “entry”`, kind:'quickfix', preferred:true, safeAutomatic:true,
                edits:[{ documentId:diagnostic.documentId, range:diagnostic.range, replacement:'entry' }], preconditions:{source:'local-inspection'}, metadata:{source:'local-inspection',automaticResolution:'true'} });
            return { ...diagnostic, codeActionIds:unique([...diagnostic.codeActionIds,id]) };
        }
        if (diagnostic.code === 'GRAMMAR_LOCAL_MISSING_RULE_TERMINATOR') {
            const id = localActionId(diagnostic, index, 'append-rule-terminator');
            const point: TextPosition = diagnostic.range.end;
            actions.push({ id, title:'Add missing “;”', kind:'quickfix', preferred:true, safeAutomatic:true,
                edits:[{ documentId:diagnostic.documentId, range:{start:point,end:point}, replacement:';' }], preconditions:{source:'local-inspection'}, metadata:{source:'local-inspection',automaticResolution:'true'} });
            return { ...diagnostic, codeActionIds:unique([...diagnostic.codeActionIds,id]) };
        }
        return diagnostic;
    });
    return { diagnostics: enriched, actions };
}

function nearestCandidates(reference:string,symbols:readonly GrammarSymbol[]):ReferenceCandidate[]{
    if(reference.trim()==='')return [];
    return symbols
        .filter(symbol=>symbol.name!==reference)
        .map(symbol=>{
            const score=similarity(reference,symbol.name);
            return {name:symbol.name,symbolId:symbol.id,kind:symbol.kind,documentId:symbol.documentId,score,reason:score>=.8?'Very similar declared grammar symbol':score>=.6?'Similar declared grammar symbol':'Declared grammar symbol',scoreBreakdown:{nameSimilarity:score}};
        })
        .filter(candidate=>candidate.score>=.45)
        .sort((a,b)=>b.score-a.score||a.name.localeCompare(b.name));
}
function similarity(left:string,right:string):number{
    const a=left.toLowerCase(),b=right.toLowerCase();
    if(a===b)return 1;
    const distance=levenshtein(a,b),denominator=Math.max(1,a.length,b.length);
    const edit=1-distance/denominator;
    const prefix=commonPrefix(a,b)/denominator;
    return Math.max(0,Math.min(1,edit*.85+prefix*.15));
}
function levenshtein(a:string,b:string):number{
    const previous=Array.from({length:b.length+1},(_,index)=>index),current=new Array<number>(b.length+1);
    for(let i=1;i<=a.length;i++){
        current[0]=i;
        for(let j=1;j<=b.length;j++)current[j]=Math.min(current[j-1]+1,previous[j]+1,previous[j-1]+(a[i-1]===b[j-1]?0:1));
        for(let j=0;j<=b.length;j++)previous[j]=current[j];
    }
    return previous[b.length];
}
function commonPrefix(a:string,b:string):number{let index=0;while(index<a.length&&index<b.length&&a[index]===b[index])index++;return index;}
function uniqueSymbols(symbols:readonly GrammarSymbol[]):GrammarSymbol[]{const seen=new Set<string>();return symbols.filter(symbol=>{const key=`${symbol.kind}:${symbol.name}`;if(seen.has(key))return false;seen.add(key);return true;});}
function unique(values:readonly string[]):string[]{return [...new Set(values)];}
function localActionId(diagnostic:GrammarDiagnostic,index:number,suffix:string):string{return `local:${diagnostic.code}:${diagnostic.documentId}:${diagnostic.range.start.line}:${diagnostic.range.start.column}:${index}:${suffix}`;}
