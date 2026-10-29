import { useMemo, useState } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { GrammarMonacoEditor } from './GrammarMonacoEditor';
import { generatedCodeCustomization, generatedCodeLanguageProfile } from './generatedCodeLanguage';
import type { GrammarCompilationSnapshot } from './types';

/** Read-only inspection of exactly the generated file in the selected snapshot. */
export function GrammarGeneratedCodeView({snapshot}:{snapshot:GrammarCompilationSnapshot|null}){
    const {t}=useHydraLanguage();
    const files=useMemo(()=>Object.entries(snapshot?.generatedProject.files??{}).sort(([left],[right])=>left.localeCompare(right)),[snapshot]);
    const [selectedPath,setSelectedPath]=useState('');
    const active=files.find(([path])=>path===selectedPath)??files[0];
    // Hooks must run before any early return, including empty compilation snapshots.
    const activePath=active?.[0]??'';
    const language=useMemo(()=>generatedCodeLanguageProfile(activePath),[activePath]);
    const customization=useMemo(()=>generatedCodeCustomization(language),[language]);
    if(snapshot===null||files.length===0)return <section className="grammar-metamodel-preview"><strong>{t('Generated code')}</strong><p>{t('Compile or generate the language project to inspect generated source files here.')}</p></section>;
    return <section className="grammar-generated-code-view" aria-label={t('Generated code')}>
        <nav className="grammar-generated-file-list" aria-label={t('Generated files')}>{files.map(([path])=><HydraButton type="button" className={activePath===path?'active':''} key={path} title={path} onClick={()=>setSelectedPath(path)}>{path}</HydraButton>)}</nav>
        <div className="grammar-generated-code-editor">
            <header className="grammar-generated-code-toolbar"><div><strong title={activePath}>{activePath}</strong><small>{language.label}</small></div><span>{t('Read only')}</span></header>
            <div className="grammar-generated-code-surface"><GrammarMonacoEditor value={active?.[1]??''} languageId={language.languageId} diagnostics={[]} onChange={()=>undefined} customization={customization} readOnly alwaysShowVerticalScrollbar/></div>
        </div>
    </section>;
}
