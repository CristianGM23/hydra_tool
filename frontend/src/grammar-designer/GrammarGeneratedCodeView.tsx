import { useMemo, useState } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { GrammarCompilationSnapshot } from './types';

export function GrammarGeneratedCodeView({snapshot}:{snapshot:GrammarCompilationSnapshot|null}){
    const {t}=useHydraLanguage();
    const files=useMemo(()=>Object.entries(snapshot?.generatedProject.files??{}).sort(([left],[right])=>left.localeCompare(right)),[snapshot]);
    const [selectedPath,setSelectedPath]=useState('');
    const active=files.find(([path])=>path===selectedPath)??files[0];
    if(snapshot===null||files.length===0)return <section className="grammar-metamodel-preview"><strong>{t('Generated code')}</strong><p>{t('Compile or generate the language project to inspect generated source files here.')}</p></section>;
    return <section className="grammar-generated-code-view" aria-label={t('Generated code')}>
        <nav className="grammar-generated-file-list" aria-label={t('Generated files')}>{files.map(([path])=><HydraButton type="button" className={active?.[0]===path?'active':''} key={path} title={path} onClick={()=>setSelectedPath(path)}>{path}</HydraButton>)}</nav>
        <pre className="grammar-generated-code-content"><code>{active?.[1]??''}</code></pre>
    </section>;
}
