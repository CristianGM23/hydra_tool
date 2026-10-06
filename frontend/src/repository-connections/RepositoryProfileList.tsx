import { HydraButton } from '../design-system';
import type { RemoteStorageProfileConfiguration } from '../remote-storage/types';
import { providerVisibleName, repositorySummary } from './repositoryProfileModel';

interface Props { profiles: RemoteStorageProfileConfiguration[]; selectedId: string; t: (value: string) => string; onCreate: () => void; onChoose: (profile: RemoteStorageProfileConfiguration) => void; }
function ConnectedCablesIcon() { return <svg aria-hidden="true" className="repository-connection-icon" viewBox="0 0 32 32" fill="none"><path d="M5 9h7v5H9v4c0 3 2 5 5 5h4"/><path d="M27 23h-7v-5h3v-4c0-3-2-5-5-5h-4"/><path d="M12 11h8M12 13h8"/></svg>; }
/** Compact reusable navigator for persisted repository destinations. */
export function RepositoryProfileList({ profiles, selectedId, t, onCreate, onChoose }: Props) {
    return <aside className="repository-profile-list" aria-label={t('Repository profiles')}>
        <header><div><strong>{t('Repository profiles')}</strong><small>{t('Saved connections can be reused from compatible import and export dialogs.')}</small></div><HydraButton size="small" onClick={onCreate}>{t('New repository')}</HydraButton></header>
        {profiles.length === 0 && <div className="repository-profile-empty-state"><p>{t('No repository profiles have been configured yet.')}</p><small>{t('Create one profile for each Git, GitHub, GitLab, OneDrive, Google Drive or Dropbox destination that HYDRA should reuse.')}</small></div>}
        {profiles.map(profile => <HydraButton className={profile.id === selectedId ? 'active' : ''} key={profile.id} type="button" onClick={() => onChoose(profile)}><ConnectedCablesIcon /><span className="repository-profile-list-copy"><strong>{profile.displayName}</strong><span>{providerVisibleName(profile.provider, t)} · {repositorySummary(profile)}</span><small>{profile.enabled ? t('Enabled') : t('Disabled')}</small></span><span className={`repository-profile-list-state${profile.enabled ? ' enabled' : ''}`} aria-label={profile.enabled ? t('Enabled') : t('Disabled')} /></HydraButton>)}
    </aside>;
}
