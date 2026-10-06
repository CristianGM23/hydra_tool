import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import * as ts from 'typescript';
import { describe, expect, it } from 'vitest';
import { translateRuntimeUiText, translateUiText } from './translationCatalog';

const RUNTIME_SETTERS = new Set(['setError','setMessage','setStatus','setNotice','setWarning','setProblem','onError']);
const TECHNICAL_RUNTIME_VALUES = new Set(['READY','BLOCKED','PASS','FAIL','ERROR','WARNING','INFO']);
const RUNTIME_SETTER_PREFILTER = /\b(?:setError|setMessage|setStatus|setNotice|setWarning|setProblem|onError)\s*\(/;

type Copy = { file:string; line:number; text:string };

function sourceRoot(): string {
    const candidates=[resolve(process.cwd(),'src'),resolve(process.cwd(),'frontend','src')];
    const root=candidates.find(existsSync);
    if(root===undefined) throw new Error('Could not locate frontend source directory.');
    return root;
}

function runtimeSetterCopy(): Copy[] {
    const root=sourceRoot();const found:Copy[]=[];
    const walk=(directory:string):void=>{for(const entry of readdirSync(directory,{withFileTypes:true})){
        const file=join(directory,entry.name);if(entry.isDirectory()){walk(file);continue;}
        if(!/\.tsx?$/.test(entry.name)||/\.test\.tsx?$/.test(entry.name)||entry.name.endsWith('.d.ts'))continue;
        const source=readFileSync(file,'utf8');
        if(!RUNTIME_SETTER_PREFILTER.test(source))continue;
        const sf=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,false,file.endsWith('.tsx')?ts.ScriptKind.TSX:ts.ScriptKind.TS);
        const visit=(node:ts.Node):void=>{
            if(ts.isCallExpression(node)&&ts.isIdentifier(node.expression)&&RUNTIME_SETTERS.has(node.expression.text)&&node.arguments.length>0){
                const arg=node.arguments[0];
                if(ts.isStringLiteralLike(arg)||ts.isNoSubstitutionTemplateLiteral(arg)){
                    const text=arg.text.trim();if(text!==''&&/[A-Za-z]/.test(text)){const loc=sf.getLineAndCharacterOfPosition(arg.getStart(sf));found.push({file:relative(root,file).replaceAll('\\','/'),line:loc.line+1,text});}
                }
            }
            ts.forEachChild(node,visit);
        };visit(sf);
    }};walk(root);return found;
}

describe('runtime Spanish coverage',()=>{
    it('translates literal error, information, status and problem messages produced at runtime',()=>{
        const seen=new Set<string>();
        const missing=runtimeSetterCopy().filter(item=>{
            if(TECHNICAL_RUNTIME_VALUES.has(item.text)||seen.has(item.text))return false;
            seen.add(item.text);
            return translateUiText(item.text,'es')===item.text;
        });
        expect(missing,missing.map(item=>`${item.file}:${item.line} · ${item.text}`).join('\n')).toEqual([]);
    },60_000);

    it('never exposes an uncatalogued backend diagnostic in English in Spanish mode',()=>{
        const source='A future backend checker returned a new diagnostic that is not catalogued yet.';
        const translated=translateRuntimeUiText(source,'es','validation','VAL-FUTURE','WARNING');
        expect(translated).not.toBe(source);
        expect(translated).toContain('Aviso de validación');
        expect(translated).toContain('VAL-FUTURE');
        expect(translateRuntimeUiText(source,'en','validation','VAL-FUTURE','WARNING')).toBe(source);
    });

    it('uses exact Spanish when a runtime message is already catalogued',()=>{
        expect(translateRuntimeUiText('The submitted data is not valid.','es','operation')).toBe('Los datos enviados no son válidos.');
    });
    it('keeps transformation diagnostics specific and translates bidirectional repair guidance', () => {
        expect(translateRuntimeUiText("Missing REVERSE rule for correspondence 'corr:person'", 'es', 'Transformation designer', 'INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE', 'ERROR'))
            .toContain("Falta la regla inversa (REVERSE) para la correspondencia 'corr:person'");
        expect(translateRuntimeUiText("Create an enabled REVERSE rule with correspondenceId='corr:person'. Example: name='Reverse Person to Person', direction=REVERSE, sourceTypeId='Person', target type='Person'. Then map the inverse attributes/relations explicitly; HYDRA will not guess inverse bindings.", 'es', 'diagnostic solution', 'INCOMPLETE_BIDIRECTIONAL_CORRESPONDENCE', 'ERROR'))
            .toContain("correspondenceId='corr:person'");
        expect(translateRuntimeUiText("A brand new diagnostic mentioning rule 'rule:42'.", 'es', 'Transformation designer', 'NEW_CODE', 'ERROR'))
            .toContain("rule 'rule:42'");
        const actionable="Binding 'binding:name' on rule 'Person to Employee' references missing source attribute 'attribute:Person:legacyName'. Available attributes: name, age.";
        expect(translateRuntimeUiText(actionable, 'es', 'Transformation designer', 'UNKNOWN_SOURCE_ATTRIBUTE', 'ERROR')).toBe(actionable);
        expect(translateRuntimeUiText(actionable, 'es', 'Transformation designer', 'UNKNOWN_SOURCE_ATTRIBUTE', 'ERROR')).not.toContain('Incidencia de');
    });

});
