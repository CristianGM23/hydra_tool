import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

if(typeof self!=='undefined'){
    (self as unknown as {MonacoEnvironment?:{getWorker:(_moduleId:string,_label:string)=>Worker}}).MonacoEnvironment={getWorker:()=>new EditorWorker()};
}
if(typeof window!=='undefined'){
    (window as unknown as {monaco?:unknown}).monaco=monaco;
}
export { monaco };
