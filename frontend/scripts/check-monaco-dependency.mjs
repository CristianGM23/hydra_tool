import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const EXPECTED_VERSION='0.52.2';
const root=process.cwd();
const packageFile=resolve(root,'node_modules','monaco-editor','package.json');
const workerFile=resolve(root,'node_modules','monaco-editor','esm','vs','editor','editor.worker.js');

function fail(detail){
  console.error('[HYDRA] Monaco Editor no está instalado de forma coherente con este source.');
  console.error(`[HYDRA] ${detail}`);
  console.error('[HYDRA] Desde la carpeta frontend ejecuta: npm ci');
  console.error('[HYDRA] Después comprueba: npm ls monaco-editor');
  process.exit(1);
}

if(!existsSync(packageFile)) fail('Falta node_modules/monaco-editor/package.json. node_modules pertenece a una versión anterior de HYDRA.');
if(!existsSync(workerFile)) fail('La instalación de monaco-editor está incompleta: falta esm/vs/editor/editor.worker.js.');

let version='';
try{version=JSON.parse(readFileSync(packageFile,'utf8')).version??'';}catch(error){fail(`No se pudo leer la versión instalada: ${error instanceof Error?error.message:String(error)}`);}
if(version!==EXPECTED_VERSION) fail(`Versión instalada ${version||'(desconocida)'}; HYDRA requiere ${EXPECTED_VERSION}.`);

console.log(`[HYDRA] Monaco Editor ${version} disponible.`);
