import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
const ROOT=path.resolve(import.meta.dirname,'../../..');
const SOURCE=fs.readFileSync(path.join(ROOT,'src/main/resources/standalone-template/frontend/native-transport.js'),'utf8');
let passed=0;
async function test(name,action){await action();passed++;console.log('PASS '+name);}
function transport({native=false,invoke,fetch}){
  const window=native?{__TAURI__:{core:{invoke}}}:{};
  vm.runInNewContext(SOURCE,{window,fetch,console},{timeout:1000});
  return window.HydraEditorTransport.request;
}
function httpResponse(value,etag=''){
  return {ok:true,status:200,json:async()=>value,headers:{get:()=>etag}};
}
const calls=[];
await test('web mode retains HTTP GET and ETag semantics',async()=>{
  const request=transport({fetch:async(u,o)=>{calls.push([u,o]);return httpResponse({id:'book'},'"abc"')}});
  const result=await request('/api/models/book');
  assert.equal(result.etag,'abc');assert.equal(result.value.id,'book');
  assert.equal(calls.at(-1)[0],'/api/models/book');
});
await test('native GET config uses bundled descriptor, not backend API',async()=>{
  let fetched='';const request=transport({native:true,invoke:async()=>{throw Error('IPC config forbidden')},fetch:async u=>{fetched=u;return httpResponse({applicationId:'ed'})}});
  assert.equal((await request('/api/config')).value.applicationId,'ed');assert.equal(fetched,'./standalone-editor.json');
});
await test('native lists persisted model identifiers via IPC',async()=>{
  const request=transport({native:true,invoke:async(name)=>{assert.equal(name,'hydra_list_models');return ['book']}});
  assert.equal((await request('/api/models')).value[0],'book');
});
await test('native model read propagates content and ETag',async()=>{
  const request=transport({native:true,invoke:async(name,arg)=>{assert.equal(name,'hydra_read_model');assert.equal(arg.id,'book');return {value:{id:'book'},etag:'r1'}}});
  assert.equal((await request('/api/models/book')).etag,'r1');
});
await test('native conditional create sends If-None-Match',async()=>{
  const request=transport({native:true,invoke:async(name,arg)=>{assert.equal(name,'hydra_write_model');assert.equal(arg.ifNoneMatch,true);assert.equal(arg.ifMatch,null);return {value:{id:arg.id},etag:'a'}}});
  assert.equal((await request('/api/models/book',{method:'PUT',headers:{'If-None-Match':'*'},body:'{"id":"book"}'})).etag,'a');
});
await test('native conditional update forwards matching revision',async()=>{
  const request=transport({native:true,invoke:async(name,arg)=>{assert.equal(arg.ifMatch,'"r1"');assert.equal(arg.ifNoneMatch,false);return {value:{id:'book'},etag:'r2'}}});
  assert.equal((await request('/api/models/book',{method:'PUT',headers:{'If-Match':'"r1"'},body:'{"id":"book"}'})).etag,'r2');
});
await test('native delete carries If-Match',async()=>{
  const request=transport({native:true,invoke:async(name,arg)=>{assert.equal(name,'hydra_delete_model');assert.equal(arg.ifMatch,'"r2"');return null}});
  assert.equal(await request('/api/models/book',{method:'DELETE',headers:{'If-Match':'"r2"'}}),null);
});
await test('native rejects dangerous IDs before IPC',async()=>{
  let count=0;const request=transport({native:true,invoke:async()=>{count++}});
  for(const id of ['../x','%2E%2E%2Fx','%5Cbad','%ZZ','a%2Fb','']){
    await assert.rejects(()=>request('/api/models/'+id));
  }
  assert.equal(count,0);
});
await test('native errors propagate rather than silently falling back to HTTP',async()=>{
  let httpCount=0;const request=transport({native:true,invoke:async()=>{throw Error('Concurrent model change')},fetch:async()=>{httpCount++}});
  await assert.rejects(()=>request('/api/models/book'),/Concurrent model change/);
  assert.equal(httpCount,0);
});
for(const name of ['app.js','textual-app.js']){
  await test('actual generated '+name+' parses as JavaScript',async()=>{
    const source=fs.readFileSync(path.join(ROOT,'src/main/resources/standalone-template/frontend',name),'utf8');
    new vm.Script(source,{filename:name});
  });
}
await test('generated editors share the same request adapter',async()=>{
  for(const name of ['app.js','textual-app.js']){
    const s=fs.readFileSync(path.join(ROOT,'src/main/resources/standalone-template/frontend',name),'utf8');
    assert.match(s,/window\.HydraEditorTransport\.request\(path,options\)/);
  }
});
console.log('FIX183_NATIVE_TRANSPORT_JS=PASS scenarios='+passed);
