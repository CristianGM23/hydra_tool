'use strict';
// Runs the emitted GLSP TypeScript storage logic against actual temporary files.
// The dependency injection surface is stubbed; the official GLSP server is NOT run.
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const os=require('node:os');
const path=require('node:path');
const crypto=require('node:crypto');
// Resolve the project's pinned TypeScript dependency before any global npm install.
// An explicit HYDRA_TYPESCRIPT override is strict: a bad override is an error.
function loadTypeScript(){
 const configured=process.env.HYDRA_TYPESCRIPT;
 const candidates=configured ? [configured] : [
  path.resolve(__dirname,'../../../frontend/node_modules/typescript'),
  path.resolve(__dirname,'../../../node_modules/typescript'),
  'typescript'
 ];
 for(const candidate of candidates){
  try{
   const compiler=require(candidate);
   if(typeof compiler.transpileModule==='function' && compiler.ScriptTarget)return compiler;
   throw new Error('Invalid TypeScript module at '+candidate);
  }catch(error){
   if(error.code!=='MODULE_NOT_FOUND')throw error;
  }
 }
 throw new Error('TypeScript not found. Install project dependencies with npm ci --prefix frontend, '+
  'or set HYDRA_TYPESCRIPT to an existing TypeScript package directory. Checked: '+candidates.join(', '));
}
const ts=loadTypeScript();
const source=require('node:fs').readFileSync(process.argv[2],'utf8');
const result=ts.transpileModule(source,{fileName:'hydra-source-model-storage.ts',reportDiagnostics:true,compilerOptions:{
 target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,experimentalDecorators:true,strict:true}});
const problems=(result.diagnostics||[]).filter(d=>d.category===ts.DiagnosticCategory.Error);
if(problems.length)throw new Error('Generated TS syntax invalid: '+problems.map(d=>ts.flattenDiagnosticMessageText(d.messageText,' ')).join(' | '));
const fakeRequire=(name)=>{
 if(name==='inversify')return {injectable:()=>cls=>cls,inject:()=>()=>{}};
 if(name==='@eclipse-glsp/server')return {ModelState:'ModelState',SOURCE_URI_ARG:'sourceUri'};
 if(name==='./hydra-semantic-runtime-service.js')return {HydraSemanticRuntimeService:class{}};
 return require(name);
};
const mod={exports:{}};new Function('require','module','exports','Buffer',result.outputText)(fakeRequire,mod,mod.exports,Buffer);
const {HydraSourceModelStorage,HYDRA_SOURCE_MODEL_PROPERTY}=mod.exports;
class ModelState{
 constructor(){this.map=new Map();this.sourceUri='';}
 get(key){return this.map.get(key);}
 set(key,value){this.map.set(key,value);if(key==='sourceUri')this.sourceUri=value;}
}
(async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'hydra-fix185-glsp-'));
 try{
  const filename=path.join(dir,'source.json');await fs.writeFile(filename,JSON.stringify({elements:[{id:'n1'}],relations:[]}));
  let commits=0;const state=new ModelState();const runtime={synchronize:()=>commits++};
  const store=new HydraSourceModelStorage(state,runtime);
  await store.loadSourceModel({options:{sourceUri:filename}});
  assert.equal(commits,1);
  state.set(HYDRA_SOURCE_MODEL_PROPERTY,JSON.stringify({elements:[{id:'n1'},{id:'n2'}],relations:[]}));
  await store.saveSourceModel({});assert.equal(commits,2);
  assert.equal(JSON.parse(await fs.readFile(filename,'utf8')).elements.length,2);
  // Reload shows committed bytes are durable and match the document state.
  const reopened=new HydraSourceModelStorage(new ModelState(),runtime);
  await reopened.loadSourceModel({options:{sourceUri:filename}});
  assert.equal(JSON.parse(reopened.sourceModel()).elements.length,2);
  // External writer invalidates the loaded revision; no overwrite/commit permitted.
  await fs.writeFile(filename,JSON.stringify({elements:[{id:'external'}],relations:[]}));
  await assert.rejects(store.saveSourceModel({}),/concurrent model revision conflict/);
  assert.equal(JSON.parse(await fs.readFile(filename,'utf8')).elements[0].id,'external');
  assert.equal(commits,3);
  await assert.rejects(store.saveSourceModel({fileUri:path.join(dir,'other.json')}),/save-as/);
  // A separate process' advisory lock must also be honored.
  const holder=new HydraSourceModelStorage(new ModelState(),runtime);
  await holder.loadSourceModel({options:{sourceUri:filename}});
  await fs.writeFile(filename+'.hydra-write.lock','external-process');
  await assert.rejects(holder.saveSourceModel({}));
  assert.equal((await fs.readFile(filename+'.hydra-write.lock','utf8')),'external-process');
  await fs.unlink(filename+'.hydra-write.lock');
  // Linked destination is rejected; do not follow arbitrary files.
  const linked=path.join(dir,'symbolic.json');
  try {await fs.symlink(filename,linked); await assert.rejects(holder.loadSourceModel({options:{sourceUri:linked}}),/linked/);} catch(error){if(!['EPERM','EACCES'].includes(error.code))throw error;}
  console.log('FIX185_REAL_GENERATED_GLSP_STORAGE=PASS persisted=PASS reopen=PASS external_conflict=BLOCKED lock=BLOCKED save_as=BLOCKED symlink=BLOCKED');
 }finally{await fs.rm(dir,{recursive:true,force:true});}
})().catch(e=>{console.error(e);process.exitCode=1});
