import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { loadTechnicalSettings, updateTechnicalSettings, type TechnicalSettingsSnapshot } from './technicalSettingsApi';
import { formatTechnicalSettingValue, technicalSettingPresentation } from './technicalSettingPresentation';

export function TechnicalSettingsPanel() {
    const { t } = useHydraLanguage();
    const [snapshot,setSnapshot]=useState<TechnicalSettingsSnapshot|null|undefined>(undefined);
    const [draft,setDraft]=useState<Record<string,string>>({});
    const [error,setError]=useState('');
    const [saving,setSaving]=useState(false);
    useEffect(()=>{let active=true;void loadTechnicalSettings().then(value=>{if(active)setSnapshot(value);}).catch(reason=>{if(active)setError(reason instanceof Error?reason.message:String(reason));});return()=>{active=false;};},[]);
    const groups=useMemo(()=>{
        const result=new Map<string,NonNullable<TechnicalSettingsSnapshot>['settings']>();
        for(const setting of snapshot?.settings??[]){const group=result.get(setting.category)??[];group.push(setting);result.set(setting.category,group);}return [...result.entries()];
    },[snapshot]);
    if(snapshot===undefined&&!error)return <section className="settings-section settings-technical"><h3>{t('Technical settings')}</h3><p>{t('Loading safe runtime configuration…')}</p></section>;
    if(snapshot===null)return <section className="settings-section settings-technical"><h3>{t('Technical settings')}</h3><p>{t('Technical settings are disabled for the current runtime profile.')}</p></section>;
    if(error)return <section className="settings-section settings-technical"><h3>{t('Technical settings')}</h3><HydraActionableErrorNotice className="settings-technical-error" message={error} context="technical settings" /></section>;
    const changed=Object.keys(draft).length>0;
    async function save(){if(!changed)return;setSaving(true);setError('');try{setSnapshot(await updateTechnicalSettings(draft));setDraft({});}catch(reason){setError(reason instanceof Error?reason.message:String(reason));}finally{setSaving(false);}}
    return <section className="settings-section settings-technical">
        <header><div><h3>{t('Technical settings')}</h3><p>{t('These options control safe runtime limits and deployment behavior. HYDRA shows a plain-language name first; the technical key remains available for administrators and support.')}</p></div><span>{t('Active profile')}: {t((snapshot?.activeProfiles.length ? snapshot.activeProfiles : ['default']).join(', ') === 'default' ? 'Default' : (snapshot?.activeProfiles ?? []).join(', '))}</span></header>
        <div className="settings-technical-intro"><strong>{t('How to use this section')}</strong><span>{t('Open the information button next to any option to see what it controls, when it is useful and whether changing it requires restarting HYDRA. Read-only values are shown for diagnosis but cannot be changed from this screen.')}</span></div>
        {groups.map(([category,settings])=><details key={category}><summary>{t(category)}</summary><div className="settings-technical-grid">{settings.map(setting=>{
            const value=draft[setting.key]??setting.value; const boolean=value==='true'||value==='false'; const presentation=technicalSettingPresentation(setting.key);
            return <div className="settings-technical-row" key={setting.key}><div className="settings-technical-copy"><div className="settings-technical-title"><strong>{t(presentation.label)}</strong><HydraTooltip label={`${t('Explain')}: ${t(presentation.label)}`} title={presentation.label}><p>{presentation.help}</p><p><strong>{t('Technical key')}:</strong> <code data-hydra-no-translate="true">{setting.key}</code></p><p><strong>{t('Current value')}:</strong> {t(formatTechnicalSettingValue(value,presentation.valueKind))}</p>{!setting.mutable&&<p><strong>{t('Change policy')}:</strong> {t('Read-only here. Change it in the deployment/startup configuration and restart HYDRA when the setting is startup-bound.')}</p>}</HydraTooltip></div><small>{t(setting.description)}</small><code className="settings-technical-key" data-hydra-no-translate="true">{setting.key}</code></div><div className="settings-technical-value">{setting.mutable&&boolean?<HydraCheckbox checked={value==='true'} label={value==='true'?t('Enabled'):t('Disabled')} onChange={event=>setDraft(current=>({...current,[setting.key]:String(event.target.checked)}))}/>:setting.mutable?<><input aria-label={t(presentation.label)} inputMode="numeric" value={value} onChange={event=>setDraft(current=>({...current,[setting.key]:event.target.value}))}/>{presentation.unitLabel&&<small>{t(presentation.unitLabel)}</small>}</>:<><strong>{t(formatTechnicalSettingValue(value,presentation.valueKind))}</strong><small>{t('Read-only')}</small></>}</div></div>;
        })}</div></details>)}
        <div className="settings-technical-actions"><HydraButton disabled={!changed||saving} variant="primary" onClick={()=>void save()}>{saving?t('Applying…'):t('Apply technical settings')}</HydraButton></div>
    </section>;
}
