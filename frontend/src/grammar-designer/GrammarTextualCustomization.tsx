import { useMemo, useState } from 'react';
import { HydraButton, HydraCheckbox, HydraFormField } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { GrammarDefinitionModel, GrammarDiagnostic, GrammarSymbol } from './types';
import { GrammarMonacoEditor } from './GrammarMonacoEditor';
import { buildGrammarTextualExample } from './grammarTextualExample';
import {
    applyCustomizationToGrammar,
    customizableSymbols,
    effectiveTextualCustomization,
    defaultTextualCustomization,
    inferKeywords,
    symbolSpecificStyle,
    symbolStyleKey,
    type GrammarTextualCustomization as Customization,
    type TextualRoleStyle,
    type TextualStyleRole,
    type TextualSymbolStyleKind,
} from './textualCustomization';

const STYLE_ROLES:TextualStyleRole[]=['KEYWORD','RULE','TOKEN','REFERENCE','STRING','NUMBER','COMMENT','OPERATOR','PUNCTUATION','TYPE','ATTRIBUTE','ENUM_LITERAL'];
const FONT_OPTIONS=[
    {label:'System monospace',value:'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'},
    {label:'Cascadia Code',value:'"Cascadia Code", Consolas, monospace'},
    {label:'JetBrains Mono',value:'"JetBrains Mono", Consolas, monospace'},
    {label:'Fira Code',value:'"Fira Code", Consolas, monospace'},
    {label:'Consolas',value:'Consolas, "Courier New", monospace'},
    {label:'Menlo / Monaco',value:'Menlo, Monaco, monospace'},
    {label:'Source Code Pro',value:'"Source Code Pro", Consolas, monospace'},
] as const;

type StyleScope='GENERAL'|'RULE'|'TERMINAL';
interface Props{mode:'APPEARANCE'|'HOVER'|'PREVIEW';model:GrammarDefinitionModel;symbols:readonly GrammarSymbol[];onChange:(model:GrammarDefinitionModel)=>void;}

interface HelpTexts {
    label:string;
    help:string;
    hint?:string;
}


function createFieldHelp(t:(value:string)=>string):{
    font:HelpTexts;fontSize:HelpTexts;lineHeight:HelpTexts;textColor:HelpTexts;background:HelpTexts;keywords:HelpTexts;
    scope:HelpTexts;elementType:HelpTexts;symbol:HelpTexts;color:HelpTexts;bold:HelpTexts;italic:HelpTexts;
    hoverSymbol:HelpTexts;hoverTitle:HelpTexts;hoverDescription:HelpTexts;
}{
    return {
        font:{label:t('Font'),help:t('Choose the base font family used by HYDRA and by the textual editors exported from this grammar.')},
        fontSize:{label:t('Font size'),help:t('Base font size used to render the textual editor content.')},
        lineHeight:{label:t('Line height'),help:t('Vertical spacing between lines so the code remains readable.')},
        textColor:{label:t('Text color'),help:t('Main text color for the editor when no specific syntax override applies.')},
        background:{label:t('Background'),help:t('Background color of the textual editor surface.')},
        keywords:{label:t('Reserved words'),help:t('Keywords that HYDRA will highlight as reserved words in the textual editor.'),hint:t('HYDRA can infer these words from quoted literals in the grammar, and you can refine the list manually.')},
        scope:{label:t('Scope'),help:t('Choose whether the style applies globally or only to one parser rule or terminal.')},
        elementType:{label:t('Element type'),help:t('General syntax category whose style you want to customize.')},
        symbol:{label:t('Element'),help:t('Specific grammar element that will receive its own visual override.')},
        color:{label:t('Color'),help:t('Foreground color applied to the selected syntax element.')},
        bold:{label:t('Bold'),help:t('Use bold emphasis for the selected syntax element.')},
        italic:{label:t('Italic'),help:t('Use italic emphasis for the selected syntax element.')},
        hoverSymbol:{label:t('Grammar element'),help:t('Element of the grammar whose contextual information you want to edit.')},
        hoverTitle:{label:t('Title'),help:t('Short title shown at the top of the contextual information tooltip.')},
        hoverDescription:{label:t('Explanation'),help:t('Explain what the element means, how it is used and when it is relevant.')}
    };
}


function ColorField({ id, label, help, value, disabled = false, onChange }:{ id:string; label:string; help:string; value:string; disabled?:boolean; onChange:(value:string)=>void; }){
    return <HydraFormField label={label} targetId={id} help={help}>
        <span className="grammar-color-control"><input id={id} type="color" disabled={disabled} value={value} onChange={event=>onChange(event.target.value)}/><code>{value}</code></span>
    </HydraFormField>;
}

function ToggleField({ id, label, help, checked, disabled = false, onChange }:{ id:string; label:string; help:string; checked:boolean; disabled?:boolean; onChange:(checked:boolean)=>void; }){
    return <HydraFormField label={label} targetId={id} help={help} className="grammar-toggle-field">
        <HydraCheckbox id={id} label={label} checked={checked} disabled={disabled} onChange={event=>onChange(event.target.checked)}/>
    </HydraFormField>;
}

export function GrammarTextualCustomization({mode,model,symbols,onChange}:Props){
    const { t } = useHydraLanguage();
    const fieldHelp = useMemo(() => createFieldHelp(t), [t]);
    const customization=useMemo(()=>effectiveTextualCustomization(model),[model]);
    const available=useMemo(()=>customizableSymbols(symbols),[symbols]);
    const rules=useMemo(()=>available.filter(item=>item.kind==='RULE'),[available]);
    const terminals=useMemo(()=>available.filter(item=>item.kind==='TERMINAL'||item.kind==='TOKEN'),[available]);
    const [role,setRole]=useState<TextualStyleRole>('KEYWORD');
    const [styleScope,setStyleScope]=useState<StyleScope>('GENERAL');
    const [styleSymbol,setStyleSymbol]=useState('');
    const [hoverSymbol,setHoverSymbol]=useState('');

    function commit(next:Customization){onChange(applyCustomizationToGrammar(model,next));}
    function update<K extends keyof Customization>(key:K,value:Customization[K]){commit({...customization,[key]:value});}

    if(mode==='APPEARANCE'){
        const targetKind:TextualSymbolStyleKind=styleScope==='RULE'?'RULE':'TERMINAL';
        const candidates=styleScope==='RULE'?rules:terminals;
        const activeSymbol=styleSymbol!==''&&candidates.some(item=>item.name===styleSymbol)?styleSymbol:candidates[0]?.name??'';
        const generalRole=styleScope==='RULE'?'RULE':'TOKEN';
        const fallbackStyle=customization.roleStyles[generalRole];
        const specificStyle=styleScope==='GENERAL'?undefined:symbolSpecificStyle(customization,targetKind,activeSymbol);
        const current=styleScope==='GENERAL'?customization.roleStyles[role]:specificStyle??fallbackStyle;
        const hasSpecificOverride=styleScope!=='GENERAL'&&specificStyle!==undefined;
        const setCurrentStyle=(next:TextualRoleStyle)=>{
            if(styleScope==='GENERAL')commit({...customization,roleStyles:{...customization.roleStyles,[role]:next}});
            else if(activeSymbol!=='')commit({...customization,symbolStyles:{...customization.symbolStyles,[symbolStyleKey(targetKind,activeSymbol)]:next}});
        };
        const clearSpecific=()=>{
            if(styleScope==='GENERAL'||activeSymbol==='')return;
            const next={...customization.symbolStyles};delete next[symbolStyleKey(targetKind,activeSymbol)];commit({...customization,symbolStyles:next});
        };
        const fontOptions=FONT_OPTIONS.some(item=>item.value===customization.fontFamily)
            ? FONT_OPTIONS
            : [{label:'Current compatible configuration',value:customization.fontFamily},...FONT_OPTIONS];

        return <section className="grammar-textual-customization grammar-textual-appearance">
            <header className="grammar-customization-heading">
                <div><h3>{t('Textual editor appearance')}</h3><p>{t('Define how models written with this grammar will look. The same configuration is reused by HYDRA and exported editors.')}</p></div>
                <HydraButton type="button" onClick={()=>commit({...defaultTextualCustomization(),keywords:inferKeywords(model)})}>{t('Restore defaults')}</HydraButton>
            </header>
            <div className="grammar-customization-card grammar-customization-general">
                <div className="grammar-customization-card-title"><strong>{t('General editor style')}</strong><span>{t('Choose the typography and base colors used by every generated textual editor.')}</span></div>
                <div className="grammar-customization-grid hydra-ui-form-grid">
                    <HydraFormField className="grammar-font-field" label={fieldHelp.font.label} targetId="grammar-textual-font-family" help={fieldHelp.font.help}>
                        <select value={customization.fontFamily} id="grammar-textual-font-family" onChange={e=>update('fontFamily',e.target.value)}>{fontOptions.map(item=><option key={item.value} value={item.value}>{t(item.label)}</option>)}</select>
                    </HydraFormField>
                    <HydraFormField label={fieldHelp.fontSize.label} targetId="grammar-textual-font-size" help={fieldHelp.fontSize.help} width="compact">
                        <input id="grammar-textual-font-size" type="number" min={11} max={24} value={customization.fontSize} onChange={e=>update('fontSize',Number(e.target.value))}/>
                    </HydraFormField>
                    <HydraFormField label={fieldHelp.lineHeight.label} targetId="grammar-textual-line-height" help={fieldHelp.lineHeight.help} width="compact">
                        <input id="grammar-textual-line-height" type="number" min={customization.fontSize} max={48} value={customization.lineHeight} onChange={e=>update('lineHeight',Number(e.target.value))}/>
                    </HydraFormField>
                    <ColorField id="grammar-textual-foreground" label={fieldHelp.textColor.label} help={fieldHelp.textColor.help} value={customization.foreground} onChange={value=>update('foreground',value)}/>
                    <ColorField id="grammar-textual-background" label={fieldHelp.background.label} help={fieldHelp.background.help} value={customization.background} onChange={value=>update('background',value)}/>
                    <HydraFormField className="grammar-customization-keywords" label={fieldHelp.keywords.label} targetId="grammar-textual-keywords" help={fieldHelp.keywords.help} hint={fieldHelp.keywords.hint} width="full">
                        <textarea id="grammar-textual-keywords" rows={3} value={customization.keywords.join(', ')} placeholder={t('For example: model, element, import')} onChange={e=>update('keywords',e.target.value.split(/[,\s]+/).filter(Boolean).slice(0,128))}/>
                    </HydraFormField>
                </div>
            </div>
            <div className="grammar-customization-card grammar-style-target-card">
                <div className="grammar-customization-card-title"><strong>{t('Syntax colors and emphasis')}</strong><span>{t('Apply a general style or override it only for one parser rule or terminal.')}</span></div>
                <div className="grammar-role-editor hydra-ui-form-grid dense">
                    <HydraFormField label={fieldHelp.scope.label} targetId="grammar-style-scope" help={fieldHelp.scope.help}>
                        <select id="grammar-style-scope" value={styleScope} onChange={e=>{setStyleScope(e.target.value as StyleScope);setStyleSymbol('');}}><option value="GENERAL">{t('General')}</option><option value="RULE">{t('Specific parser rule')}</option><option value="TERMINAL">{t('Specific terminal')}</option></select>
                    </HydraFormField>
                    {styleScope==='GENERAL'
                        ? <HydraFormField label={fieldHelp.elementType.label} targetId="grammar-style-role" help={fieldHelp.elementType.help}>
                            <select id="grammar-style-role" value={role} onChange={e=>setRole(e.target.value as TextualStyleRole)}>{STYLE_ROLES.map(item=><option key={item} value={item}>{t(roleLabel(item))}</option>)}</select>
                        </HydraFormField>
                        : <HydraFormField label={t(styleScope==='RULE'?'Parser rule':'Terminal')} targetId="grammar-style-symbol" help={fieldHelp.symbol.help}>
                            <select id="grammar-style-symbol" value={activeSymbol} disabled={candidates.length===0} onChange={e=>setStyleSymbol(e.target.value)}>{candidates.length===0?<option value="">{t('No compatible elements')}</option>:candidates.map(item=><option key={`${item.kind}:${item.name}`} value={item.name}>{item.name}</option>)}</select>
                        </HydraFormField>}
                    <ColorField id="grammar-style-color" label={fieldHelp.color.label} help={fieldHelp.color.help} value={current.color} disabled={styleScope!=='GENERAL'&&activeSymbol===''} onChange={value=>setCurrentStyle({...current,color:value})}/>
                    <ToggleField id="grammar-style-bold" label={fieldHelp.bold.label} help={fieldHelp.bold.help} checked={current.bold} disabled={styleScope!=='GENERAL'&&activeSymbol===''} onChange={checked=>setCurrentStyle({...current,bold:checked})}/>
                    <ToggleField id="grammar-style-italic" label={fieldHelp.italic.label} help={fieldHelp.italic.help} checked={current.italic} disabled={styleScope!=='GENERAL'&&activeSymbol===''} onChange={checked=>setCurrentStyle({...current,italic:checked})}/>
                    {styleScope!=='GENERAL'&&<div className="grammar-role-actions"><HydraButton type="button" disabled={!hasSpecificOverride} onClick={clearSpecific}>{t('Use general style')}</HydraButton></div>}
                </div>
                {styleScope!=='GENERAL'&&activeSymbol!==''&&<p className="grammar-style-inheritance-note">{hasSpecificOverride?t('This element has its own style.'):t('This element currently inherits the general style.')}</p>}
            </div>
        </section>;
    }

    if(mode==='HOVER'){
        const active=hoverSymbol!==''&&available.some(item=>item.name===hoverSymbol)?hoverSymbol:available[0]?.name??'';
        const hover=customization.hovers[active]??{title:'',description:''};
        const setHover=(next:{title:string;description:string})=>commit({...customization,hovers:{...customization.hovers,[active]:next}});
        return <section className="grammar-textual-customization grammar-textual-hover">
            <header className="grammar-customization-heading"><div><h3>{t('Contextual information')}</h3><p>{t('Choose the explanation shown when a user places the pointer over a grammar element while editing a textual model.')}</p></div></header>
            {available.length===0?<div className="grammar-customization-empty">{t('Add a parser rule or terminal to the grammar to customize its contextual information.')}</div>:<div className="grammar-customization-card grammar-hover-editor">
                <div className="grammar-hover-fields hydra-ui-form-grid">
                    <HydraFormField label={fieldHelp.hoverSymbol.label} targetId="grammar-hover-symbol" help={fieldHelp.hoverSymbol.help}>
                        <select id="grammar-hover-symbol" value={active} onChange={e=>setHoverSymbol(e.target.value)}>{available.map(item=><option key={`${item.kind}:${item.name}`} value={item.name}>{item.name} · {t(item.kind==='RULE'?'Rule':'Terminal')}</option>)}</select>
                    </HydraFormField>
                    <HydraFormField label={fieldHelp.hoverTitle.label} targetId="grammar-hover-title" help={fieldHelp.hoverTitle.help}>
                        <input id="grammar-hover-title" value={hover.title} maxLength={96} placeholder={active} onChange={e=>setHover({...hover,title:e.target.value})}/>
                    </HydraFormField>
                    <HydraFormField className="grammar-hover-description" label={fieldHelp.hoverDescription.label} targetId="grammar-hover-description" help={fieldHelp.hoverDescription.help} width="full">
                        <textarea id="grammar-hover-description" rows={6} maxLength={600} value={hover.description} placeholder={t('Explain what this element represents and when it should be used.')} onChange={e=>setHover({...hover,description:e.target.value})}/>
                    </HydraFormField>
                </div>
                <aside className="grammar-hover-preview"><small>{t('Preview')}</small><strong>{hover.title||active}</strong><p>{hover.description||t('HYDRA will use an automatic explanation when no custom text is provided.')}</p></aside>
            </div>}
        </section>;
    }

    const preview=buildGrammarTextualExample(model);const diagnostics:GrammarDiagnostic[]=[];
    return <section className="grammar-textual-customization grammar-textual-preview"><header className="grammar-customization-heading"><div><h3>{t('Textual editor preview')}</h3><p>{t('Preview the same appearance and contextual information that HYDRA and exported textual editors will use.')}</p></div></header><div className="grammar-preview-editor"><GrammarMonacoEditor value={preview} languageId="hydra-textual-preview" diagnostics={diagnostics} onChange={()=>{}} customization={customization} symbols={symbols} readOnly/></div></section>;
}

function roleLabel(role:TextualStyleRole):string{return ({KEYWORD:'Reserved words',RULE:'Rules',TOKEN:'Terminals and tokens',REFERENCE:'References',STRING:'Strings',NUMBER:'Numbers',COMMENT:'Comments',OPERATOR:'Operators',PUNCTUATION:'Punctuation',TYPE:'Types',ATTRIBUTE:'Attributes',ENUM_LITERAL:'Enumeration literals'} as Record<TextualStyleRole,string>)[role];}