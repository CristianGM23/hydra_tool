import { useHydraLanguage } from '../i18n';
import type { TextualSemanticSourceMapEntry } from './types';
export function TextualModelOutline({ sourceMap }: { sourceMap: readonly TextualSemanticSourceMapEntry[] }) {
    const { t } = useHydraLanguage();
    return <section className="textual-outline"><header><strong>{t('Valid semantic model')}</strong><span>{sourceMap.length}</span></header>{sourceMap.map(item => <div key={`${item.semanticId}:${item.documentId}`}><strong>{item.semanticId}</strong><span>{item.semanticKind}</span><small>{item.range.start.line + 1}:{item.range.start.column + 1}</small></div>)}</section>;
}
