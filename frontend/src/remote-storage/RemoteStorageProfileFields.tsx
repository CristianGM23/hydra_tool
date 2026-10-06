import { HydraFormField, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { RemoteStorageProfileDescriptor } from './types';

interface Props {
    profiles: readonly RemoteStorageProfileDescriptor[];
    profileId: string;
    path: string;
    onProfileChange: (value: string) => void;
    onPathChange: (value: string) => void;
    disabled?: boolean;
}

export function RemoteStorageProfileFields({ profiles, profileId, path, onProfileChange, onPathChange, disabled = false }: Props) {
    const { t } = useHydraLanguage();
    const selected = profiles.find(profile => profile.id === profileId);
    return <div className="hydra-remote-storage-fields">
        <HydraFormField
            help={t('Choose one enabled repository profile. HYDRA resolves its provider coordinates and credentials on the server; disabled or unauthorised profiles cannot be selected for a transfer.')}
            label={t('Remote profile')}
            required
            targetId="remote-storage-profile"
        >
            <HydraSelect id="remote-storage-profile" ariaLabel="Remote profile" disabled={disabled} value={profileId} placeholder="Choose a configured provider…" options={profiles.map(profile => ({ value:profile.id,label:`${profile.displayName}${profile.configured ? '' : ` · ${t('not configured')}`}`,description:`${profile.provider} · ${profile.enabled ? t('Enabled') : t('Disabled')}`,disabled:!profile.enabled || !profile.configured }))} onChange={onProfileChange} />
        </HydraFormField>
        <HydraFormField
            help={t('Path relative to the configured remote root. The selected provider normalizes this path and rejects traversal outside the profile root.')}
            label={t('Remote path')}
            required
            targetId="remote-storage-path"
        >
            <input id="remote-storage-path" disabled={disabled} placeholder="projects/my-project.hydra-project.zip" value={path} onChange={event => onPathChange(event.target.value)} />
        </HydraFormField>
        {selected !== undefined && <p className="hydra-remote-storage-profile-summary"><strong>{selected.displayName}</strong><span>{selected.repository || selected.provider}{selected.branch === '' ? '' : ` · ${selected.branch}`} · {t('root')} {selected.rootPath || '/'}</span><small>{t('Maximum artifact size')}: {formatBytes(selected.maximumArtifactBytes)}</small></p>}
    </div>;
}

function formatBytes(value: number): string {
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
    return `${(value / 1024 / 1024).toFixed(1)} MiB`;
}
