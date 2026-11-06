(()=>{
  'use strict';
  // Un único contrato de transporte para los editores gráfico y textual generados.
  // Browser/PWA => HTTP Java original; escritorio Tauri => comandos acotados de Rust.
  const nativeInvoke=()=>window.__TAURI__?.core?.invoke;
  const modelPath=/^\/api\/models\/([^/]+)$/;
  const parseId=path=>{
    const match=modelPath.exec(path);
    if(!match)throw new Error('Unsupported native model endpoint');
    let id;
    try{id=decodeURIComponent(match[1]);}catch{throw new Error('Invalid model identifier');}
    if(!/^[A-Za-z0-9_-][A-Za-z0-9._-]{0,119}$/.test(id) || id==='.' || id==='..')throw new Error('Invalid model identifier');
    return id;
  };
  const header=(options,name)=>{
    const headers=options.headers||{};
    if(typeof headers.get==='function')return headers.get(name);
    const key=Object.keys(headers).find(k=>k.toLowerCase()===name.toLowerCase());
    return key?headers[key]:null;
  };
  const http=async(path,options={})=>{
    const response=await fetch(path,options);
    if(!response.ok)throw new Error((await response.text())||`HTTP ${response.status}`);
    return {value:response.status===204?null:await response.json(),etag:(response.headers.get('ETag')||'').replaceAll('"','')};
  };
  const request=async(path,options={})=>{
    const invoke=nativeInvoke();
    if(typeof invoke!=='function')return http(path,options);
    const method=(options.method||'GET').toUpperCase();
    if(path==='/api/config'&&method==='GET')return http('./standalone-editor.json');
    if(path==='/api/models'&&method==='GET')return {value:await invoke('hydra_list_models'),etag:''};
    const id=parseId(path);
    if(method==='GET')return invoke('hydra_read_model',{id});
    if(method==='PUT')return invoke('hydra_write_model',{
      id, body:options.body||'',
      ifMatch:header(options,'If-Match'),
      ifNoneMatch:header(options,'If-None-Match')==='*'
    });
    if(method==='DELETE')return invoke('hydra_delete_model',{id,ifMatch:header(options,'If-Match')});
    throw new Error('Unsupported native model operation');
  };
  Object.defineProperty(window,'HydraEditorTransport',{value:Object.freeze({request}),writable:false,configurable:false});
})();
