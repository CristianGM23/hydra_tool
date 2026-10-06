import { useHydraLanguage } from '../i18n';
import type { GrammarSourceDocument } from './types';
import { HydraButton } from '../design-system';
interface Props { documents: readonly GrammarSourceDocument[]; selectedId: string; onSelect: (id: string) => void; onAdd: () => void; onRemove: (id: string) => void; }
export function GrammarSourceExplorer({ documents, selectedId, onSelect, onAdd, onRemove }: Props) {
    const { t } = useHydraLanguage();
    return <section className="grammar-source-explorer"><header><strong>{t('Files')}</strong><HydraButton type="button" onClick={onAdd}>{t('+ File')}</HydraButton></header>{documents.map(doc => <div className={doc.id === selectedId ? 'active' : ''} key={doc.id}><HydraButton type="button" onClick={() => onSelect(doc.id)}><strong>{doc.relativePath}</strong><small>{doc.entryPoint ? t('Entry document') : doc.languageId}</small></HydraButton>{documents.length > 1 && !doc.entryPoint && <HydraButton type="button" aria-label={t('Remove file')} onClick={() => onRemove(doc.id)}>×</HydraButton>}</div>)}</section>;
}
