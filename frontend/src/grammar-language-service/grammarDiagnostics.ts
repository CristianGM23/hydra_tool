import type { GrammarDiagnostic } from '../grammar-designer/types';
export function oneBasedLocation(item:GrammarDiagnostic):string{return `${item.range.start.line+1}:${item.range.start.column+1}`;}
export function diagnosticCount(items:readonly GrammarDiagnostic[],severity:'ERROR'|'WARNING'|'INFO'):number{return items.filter(item=>item.severity===severity).length;}
