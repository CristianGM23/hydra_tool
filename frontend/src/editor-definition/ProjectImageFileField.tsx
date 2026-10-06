import { HydraButton } from '../design-system/HydraButton';
import { openFileInputPicker } from '../design-system/openFilePicker';
import { useRef, useState } from 'react';
import { DesignerHelpedField } from '../designer-ui/DesignerFieldHelp';
import { projectFileVisualUrl, uploadProjectFile } from '../project-explorer/projectApi';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { isSupportedImage } from './projectImageFileSupport';

interface Props {
    id: string;
    label: string;
    help: string;
    value: string;
    required?: boolean;
    onChange: (path: string) => void;
}

/**
 * Selector de imagen orientado al usuario. El usuario elige un fichero nativo;
 * HYDRA conserva internamente una ruta portable dentro de assets/notation.
 */
export function ProjectImageFileField({ id, label, help, value, required = false, onChange }: Props) {
    const { t } = useHydraLanguage();
    const inputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState('');

    async function choose(file: File): Promise<void> {
        if (!isSupportedImage(file)) {
            setError('Choose an SVG, PNG, JPEG, GIF or WebP image.');
            return;
        }
        setUploading(true);
        setError('');
        try {
            const result = await uploadProjectFile('assets/notation', file);
            onChange(result.relativePath);
        } catch {
            setError('The image could not be added to the active project.');
        } finally {
            setUploading(false);
            if (inputRef.current !== null) inputRef.current.value = '';
        }
    }

    const fileName = value.split('/').filter(Boolean).at(-1) ?? '';
    return <DesignerHelpedField className="hydra-designer-wide" label={label} targetId={id} required={required} help={help} example="Select an SVG, PNG, JPEG, GIF or WebP from your computer">
        <div className="editor-image-file-selection">
            <input
                ref={inputRef}
                className="editor-asset-file-input"
                id={id}
                type="file"
                accept=".svg,.png,.jpg,.jpeg,.gif,.webp,image/svg+xml,image/png,image/jpeg,image/gif,image/webp"
                onChange={event => { const file = event.target.files?.[0]; if (file !== undefined) void choose(file); }}
            />
            {value === ''
                ? <div className="editor-image-file-empty"><div><strong>{t('No image selected')}</strong><span>{t('Choose an image and HYDRA will store it inside the project automatically.')}</span></div><HydraButton type="button" disabled={uploading} onClick={() => openFileInputPicker(inputRef.current)}>{uploading ? t('Adding image…') : t('Choose image…')}</HydraButton></div>
                : <div className="editor-image-file-selected"><img src={projectFileVisualUrl(value)} alt="" /><div><strong>{fileName}</strong><span>{t('Stored in the active project')}</span></div><HydraButton type="button" disabled={uploading} onClick={() => openFileInputPicker(inputRef.current)}>{uploading ? t('Adding image…') : t('Change image')}</HydraButton><HydraButton type="button" onClick={() => onChange('')}>{t('Remove image')}</HydraButton></div>}
            {error !== '' && <small className="hydra-ui-form-error" role="alert">{t(error)}</small>}
        </div>
    </DesignerHelpedField>;
}
