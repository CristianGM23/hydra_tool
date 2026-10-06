import { HydraButton } from '../design-system/HydraButton';
import { HydraWindowControls, openFileInputPicker } from '../design-system';
import { useEffect, useMemo, useRef, useState } from 'react';
import { loadProjectTree, projectFileVisualUrl, uploadProjectFile } from '../project-explorer/projectApi';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { ProjectTreeNode } from '../project-explorer/types';
import { isImageAsset } from './projectAssetSupport';

interface Props {
    open: boolean;
    value: string;
    onSelect: (path: string) => void;
    onClose: () => void;
}

export function ProjectAssetPicker({ open, value, onSelect, onClose }: Props) {
    const { t } = useHydraLanguage();
    const [assets, setAssets] = useState<ProjectTreeNode[]>([]);
    const [filter, setFilter] = useState('');
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (!open) return;
        void refresh();
    }, [open]);

    const visible = useMemo(() => {
        const normalized = filter.trim().toLowerCase();
        return normalized === '' ? assets : assets.filter(item => [item.name, item.relativePath].some(text => text.toLowerCase().includes(normalized)));
    }, [assets, filter]);

    async function refresh(): Promise<void> {
        setLoading(true); setError('');
        try {
            const tree = await loadProjectTree('PHYSICAL');
            setAssets(flatten(tree).filter(item => item.nodeType === 'FILE' && isImageAsset(item.relativePath)));
        } catch (exception) {
            setAssets([]);
            setError(exception instanceof Error ? exception.message : 'Project assets could not be loaded');
        } finally { setLoading(false); }
    }

    async function upload(file: File): Promise<void> {
        if (!isImageAsset(file.name)) { setError('Choose an SVG, PNG, JPEG, GIF or WebP image.'); return; }
        setUploading(true); setError('');
        try {
            const result = await uploadProjectFile('assets/notation', file);
            onSelect(result.relativePath);
            await refresh();
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : 'The image could not be uploaded to the project');
        } finally { setUploading(false); if (inputRef.current !== null) inputRef.current.value = ''; }
    }

    if (!open) return null;
    return <div className="editor-asset-picker-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
        <section className="editor-asset-picker" role="dialog" aria-modal="true" aria-labelledby="editor-asset-picker-title">
            <header><div><h3 id="editor-asset-picker-title">{t('Project image assets')}</h3><p>{t('Choose an SVG, PNG, JPEG, GIF or WebP stored inside the active HYDRA project.')}</p></div><HydraWindowControls closeLabel={t('Close project image assets')} onClose={onClose} /></header>
            <div className="editor-asset-picker-actions">
                <input aria-label={t('Filter project image assets')} placeholder={t('Filter assets...')} value={filter} onChange={event => setFilter(event.target.value)} />
                <HydraButton type="button" disabled={uploading} onClick={() => openFileInputPicker(inputRef.current)}>{uploading ? t('Uploading…') : t('Upload image')}</HydraButton>
                <input ref={inputRef} className="editor-asset-file-input" type="file" accept=".svg,.png,.jpg,.jpeg,.gif,.webp,image/svg+xml,image/png,image/jpeg,image/gif,image/webp" onChange={event => { const file = event.target.files?.[0]; if (file !== undefined) void upload(file); }} />
                <HydraButton type="button" disabled={loading} onClick={() => void refresh()}>{t('Refresh')}</HydraButton>
            </div>
            {error !== '' && <p className="editor-asset-picker-error">{error}</p>}
            <div className="editor-asset-picker-grid">
                {visible.map(asset => <HydraButton type="button" className={asset.relativePath === value ? 'selected' : ''} key={asset.id} onClick={() => onSelect(asset.relativePath)}>
                    <img src={projectFileVisualUrl(asset.relativePath)} alt="" />
                    <strong>{asset.name}</strong><small>{asset.relativePath}</small>
                </HydraButton>)}
                {!loading && visible.length === 0 && <div className="hydra-empty-state"><strong>{t('No project images found')}</strong><span>{t('Upload an SVG, PNG, JPEG, GIF or WebP to assets/notation, or open a project containing image assets.')}</span></div>}
                {loading && <div className="hydra-empty-state"><strong>{t('Loading assets…')}</strong></div>}
            </div>
            <footer><span>{value === '' ? t('No image selected') : value}</span><HydraButton type="button" disabled={value === ''} onClick={() => { onSelect(''); onClose(); }}>{t('Clear image')}</HydraButton><HydraButton type="button" onClick={onClose}>{t('Use selected image')}</HydraButton></footer>
        </section>
    </div>;
}

function flatten(root: ProjectTreeNode): ProjectTreeNode[] { return [root, ...root.children.flatMap(flatten)]; }
