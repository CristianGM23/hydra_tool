import { useState } from 'react';
import { ResetIcon } from '../components/AppIcons';
import { HydraButton, HydraCheckbox, HydraModal, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { recordAuditActivity } from '../audit-log';
import { DEFAULT_APPEARANCE_SETTINGS, FONT_FAMILY_OPTIONS, FONT_SIZE_OPTIONS, type AppearanceSettings } from './appearanceSettings';
import { useAppearanceSettings } from './useAppearanceSettings';
import { TechnicalSettingsPanel } from './TechnicalSettingsPanel';
import './settings.css';

interface SettingsDialogProps {
    open: boolean;
    onClose: () => void;
}

export function SettingsDialog({ open, onClose }: SettingsDialogProps) {
    return open ? <SettingsDialogContent onClose={onClose} /> : null;
}

function SettingsDialogContent({ onClose }: Pick<SettingsDialogProps, 'onClose'>) {
    const { settings, updateSettings } = useAppearanceSettings();
    const { language, setLanguage, t } = useHydraLanguage();
    const [draft, setDraft] = useState<AppearanceSettings>(settings);


    function update<K extends keyof AppearanceSettings>(key: K, value: AppearanceSettings[K]): void {
        setDraft(current => ({ ...current, [key]: value }));
    }

    function apply(): void {
        void recordAuditActivity({
            activityType: 'settings.appearance.updated', moduleId: 'settings', action: 'CONFIGURE',
            resourceType: 'settings', resourceId: 'appearance', displayName: 'Appearance settings',
            before: settings, after: draft, dedupeKey: 'appearance'
        });
        updateSettings(draft);
        onClose();
    }

    function reset(): void {
        setDraft(DEFAULT_APPEARANCE_SETTINGS);
    }

    return <HydraModal
        className="settings-dialog"
        description={t('Configure language, theme, typography and responsive sizing for the complete application. Repository connections are managed from their dedicated workspace.')}
        footer={<>
            <HydraButton className="settings-reset" icon={<ResetIcon/>} variant="quiet" onClick={reset}>{t('Reset defaults')}</HydraButton>
            <span className="settings-footer-spacer" />
            <HydraButton onClick={onClose}>{t('Cancel')}</HydraButton>
            <HydraButton className="settings-primary" variant="primary" onClick={apply}>{t('Apply settings')}</HydraButton>
        </>}
        open
        size="large"
        title={t('Settings')}
        onClose={onClose}
    >
        <div className="settings-content">
            <section className="settings-section">
                <h3>{t('Interface language')}</h3>
                <label htmlFor="settings-language">{t('Application language')}</label>
                <HydraSelect id="settings-language" ariaLabel={t('Application language')} value={language}
                    options={[
                        { value: 'en', label: 'English', category: 'Language' },
                        { value: 'es', label: 'Español', category: 'Language' }
                    ]}
                    onChange={value => setLanguage(value === 'es' ? 'es' : 'en')} />
                <p className="settings-language-help">{t('The language changes only visible interface text. Stable IDs, technical names, persisted values and API contracts are preserved.')}</p>
            </section>
            <section className="settings-section">
                <h3>{t('Color scheme')}</h3>
                <label htmlFor="settings-color-scheme">{t('Application theme')}</label>
                <div className="settings-theme-choice" role="group" aria-label={t('Application theme')}>
                    {(['SYSTEM', 'LIGHT', 'DARK'] as const).map(value => <HydraButton aria-pressed={draft.colorScheme === value} className={draft.colorScheme === value ? 'active' : ''} key={value} type="button" onClick={() => update('colorScheme', value)}>{t(value === 'SYSTEM' ? 'System' : value === 'LIGHT' ? 'Light' : 'Dark')}</HydraButton>)}
                </div>
                <HydraSelect id="settings-color-scheme" className="settings-theme-select" ariaLabel={t('Application theme')} value={draft.colorScheme}
                    options={[
                        { value: 'SYSTEM', label: t('System'), category: t('Appearance'), description: t('Follow the operating-system light or dark preference.') },
                        { value: 'LIGHT', label: t('Light'), category: t('Appearance'), description: t('Always use the light HYDRA interface.') },
                        { value: 'DARK', label: t('Dark'), category: t('Appearance'), description: t('Always use the dark HYDRA interface.') },
                    ]}
                    onChange={value => update('colorScheme', value as AppearanceSettings['colorScheme'])} />
                <p className="settings-language-help">{t('The interface theme never changes semantic colors stored in diagrams, notations or exported artifacts.')}</p>
            </section>
            <section className="settings-section">
                <h3>{t('Application font')}</h3>
                <label htmlFor="settings-font-family">{t('Font family')}</label>
                <HydraSelect id="settings-font-family" ariaLabel={t('Font family')} value={draft.fontFamily} options={FONT_FAMILY_OPTIONS.map(option => ({ value: option.id, label: option.label, category: t(fontFamilyCategory(option.id)), description: t(fontFamilyDescription(option.id)) }))} onChange={value => update('fontFamily', value as AppearanceSettings['fontFamily'])} />
            </section>
            <section className="settings-section settings-responsive-section">
                <h3>{t('Responsive sizing')}</h3>
                <HydraCheckbox aria-label={t('Adapt sizes automatically to screen')} checked={draft.responsiveSizing} label={t('Adapt sizes automatically to screen')} description={t('Preserves your selected sizes as the baseline and scales fonts, controls and spacing within safe limits.')} onChange={event => update('responsiveSizing', event.target.checked)} />
            </section>
            <section className="settings-section settings-size-grid">
                <h3>{t('Font sizes')}</h3>
                <FontSizeField id="settings-base-size" label={t('General application')} value={draft.baseFontSize} onChange={value => update('baseFontSize', value)} />
                <FontSizeField id="settings-window-size" label={t('Windows and panels')} value={draft.windowFontSize} onChange={value => update('windowFontSize', value)} />
                <FontSizeField id="settings-tab-size" label={t('Tabs and command bars')} value={draft.tabFontSize} onChange={value => update('tabFontSize', value)} />
                <FontSizeField id="settings-form-size" label={t('Forms and fields')} value={draft.formFontSize} onChange={value => update('formFontSize', value)} />
                <FontSizeField id="settings-diagram-size" label={t('Diagram cards and palettes')} value={draft.diagramFontSize} onChange={value => update('diagramFontSize', value)} />
                <FontSizeField id="settings-tooltip-size" label={t('Tooltips and guides')} value={draft.tooltipFontSize} onChange={value => update('tooltipFontSize', value)} />
            </section>
            <TechnicalSettingsPanel />
            <section className="settings-preview" style={{ fontFamily: FONT_FAMILY_OPTIONS.find(option => option.id === draft.fontFamily)?.css }}>
                <strong style={{ fontSize: draft.formFontSize }}>{t('Preview')}</strong>
                <p style={{ fontSize: draft.formFontSize }}>{t('Forms and fields use the selected form size.')}</p>
                <label style={{ fontSize: draft.formFontSize }}>{t('Example field')}<input readOnly value={t('Editable project setting')} /></label>
                <div className="settings-diagram-preview" style={{ fontSize: draft.diagramFontSize }}><strong>{t('Diagram card')}</strong><small>{t('Palette descriptions and visual cards use this size.')}</small></div>
                <div className="settings-tooltip-preview" style={{ fontSize: draft.tooltipFontSize }}><strong>{t('Tooltip preview')}</strong><span>{t('Help dialogs and field explanations use this size.')}</span></div>
            </section>
        </div>
    </HydraModal>;
}

function FontSizeField({ id, label, value, onChange }: { id: string; label: string; value: number; onChange: (value: number) => void }) {
    const { t } = useHydraLanguage();
    return <div className="settings-size-field"><label htmlFor={id}>{label}</label><HydraSelect id={id} ariaLabel={label} value={String(value)} options={FONT_SIZE_OPTIONS.map(size => ({ value: String(size), label: `${size}px`, category: t(sizeCategory(size)), description: t(sizeDescription(size)) }))} onChange={next => onChange(Number(next))} /></div>;
}

function sizeCategory(size: number): string {
    if (size <= 16) return 'Compact';
    if (size <= 20) return 'Comfortable';
    return 'Large';
}

function sizeDescription(size: number): string {
    if (size <= 16) return 'Compact density for large workspaces.';
    if (size <= 20) return 'Balanced size for ordinary desktop use.';
    return 'Large and highly readable text for long editing sessions.';
}

function fontFamilyCategory(id: AppearanceSettings['fontFamily']): string {
    if (id === 'GEORGIA') return 'Serif';
    if (id === 'MONOSPACE') return 'Monospace';
    if (id === 'SYSTEM' || id === 'SEGOE_UI') return 'Recommended';
    return 'Sans-serif';
}

function fontFamilyDescription(id: AppearanceSettings['fontFamily']): string {
    const descriptions: Record<AppearanceSettings['fontFamily'], string> = {
        SYSTEM: 'Uses the operating-system interface font and adapts naturally to Windows, macOS and Linux.',
        SEGOE_UI: 'Windows-oriented interface font with high readability at ordinary sizes.',
        ARIAL: 'Widely available sans-serif font with compact proportions.',
        VERDANA: 'Wide sans-serif font designed for screen readability.',
        TAHOMA: 'Compact interface-oriented sans-serif font.',
        GEORGIA: 'Serif font suited to users who prefer stronger letter shapes.',
        MONOSPACE: 'Fixed-width font useful for technical values and source-oriented work.'
    };
    return descriptions[id];
}
