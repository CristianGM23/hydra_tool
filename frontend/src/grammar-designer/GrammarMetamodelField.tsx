import { useRef, useState } from 'react';
import { HydraButton, HydraFormField, openFileInputPicker } from '../design-system';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { useHydraLanguage } from '../i18n';
import { importAndSaveMetamodel, importMetamodelBrowserDirectory } from '../metamodel-designer/importApi';
import type { CanonicalMetamodelSummary } from '../metamodel-designer/types';
import type { GrammarDefinitionModel } from './types';

interface Props {
    value:string;
    authoringMode:GrammarDefinitionModel['authoringMode'];
    metamodels:readonly CanonicalMetamodelSummary[];
    onChange:(metamodelId:string)=>void;
    onImported:(metamodelId:string)=>Promise<void>|void;
}

const IMPORT_OPTIONS={formatId:'auto',targetId:'',overwrite:false,preserveLayout:true,preserveStyles:true,strict:false} as const;

export function GrammarMetamodelField({value,authoringMode,metamodels,onChange,onImported}:Props){
    const {t}=useHydraLanguage();
    const fileRef=useRef<HTMLInputElement>(null);
    const folderRef=useRef<HTMLInputElement>(null);
    const [busy,setBusy]=useState(false);
    const [error,setError]=useState('');
    const required=authoringMode!=='GRAMMAR_FIRST';
    const options=[
        {value:'',label:'No linked metamodel',description:'Use grammar-first inference. HYDRA derives a candidate semantic structure directly from the grammar.',category:'Recommended'},
        ...metamodels.map(item=>({value:item.id,label:item.name||item.id,description:`${item.id} · ${item.schemaVersion}`,category:'Metamodels'}))
    ];

    async function importFile(file:File|null){
        if(file===null)return;
        setBusy(true);setError('');
        try{const result=await importAndSaveMetamodel(file,IMPORT_OPTIONS);onChange(result.id);await onImported(result.id);}
        catch(exception){setError(exception instanceof Error?exception.message:String(exception));}
        finally{setBusy(false);if(fileRef.current)fileRef.current.value='';}
    }
    async function importFolder(list:FileList|null){
        const files=Array.from(list??[]);if(files.length===0)return;
        const paths=files.map(file=>file.webkitRelativePath||file.name);
        setBusy(true);setError('');
        try{const result=await importMetamodelBrowserDirectory(files,paths,IMPORT_OPTIONS);onChange(result.id);await onImported(result.id);}
        catch(exception){setError(exception instanceof Error?exception.message:String(exception));}
        finally{setBusy(false);if(folderRef.current)folderRef.current.value='';}
    }
    function openFolder(){const input=folderRef.current;if(input===null)return;input.setAttribute('webkitdirectory','');input.setAttribute('directory','');openFileInputPicker(input);}

    return <HydraFormField className="grammar-metamodel-field" width="full" label="Optional metamodel" targetId="grammar-metamodel" required={required}
        help="Link the grammar to an existing HYDRA metamodel when the language must reproduce or reconcile against a known semantic structure. Leave it empty for grammar-first inference. You can also import a metamodel file or a complete source folder; both paths reuse HYDRA's safe metamodel import pipeline."
        example="people · company.ecore · src/model/" error={error||undefined}
        hint={value===''?"Grammar-first mode: HYDRA infers a candidate metamodel from the grammar.":"The selected metamodel strengthens semantic binding and reference ranking."}
        options={options.map(item=>({value:item.value,label:item.label,description:item.description}))}>
        <div className="grammar-metamodel-control">
            <HydraAdaptiveSelect id="grammar-metamodel" ariaLabel="Optional metamodel" value={value} options={options} onChange={onChange} placeholder="No linked metamodel" showOptionHelp={false}/>
            <div className="grammar-metamodel-actions">
                <input ref={fileRef} hidden type="file" accept=".ecore,.emf,.emfatic,.xmi,.json,.xml,.plantuml,.puml,.xtext,.langium,.g4,.zip,.jar" onChange={event=>void importFile(event.target.files?.[0]??null)}/>
                <input ref={folderRef} hidden type="file" multiple onChange={event=>void importFolder(event.target.files)}/>
                <details className="grammar-import-menu"><summary aria-label={t('Import metamodel')}>{busy?t('Importing…'):`${t('Import')} ▾`}</summary><div><HydraButton type="button" variant="ghost" disabled={busy} onClick={()=>openFileInputPicker(fileRef.current)}>{t('Import metamodel file')}</HydraButton><HydraButton type="button" variant="ghost" disabled={busy} onClick={openFolder}>{t('Import metamodel folder')}</HydraButton></div></details>
            </div>
        </div>
    </HydraFormField>;
}
