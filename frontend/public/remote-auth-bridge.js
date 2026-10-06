(() => {
    'use strict';
    const params=new URLSearchParams(window.location.search);
    const es=params.get('lang')==='es';
    document.documentElement.lang=es?'es':'en';
    document.getElementById('title').textContent=es?'Abriendo acceso seguro…':'Opening secure sign-in…';
    document.getElementById('message').textContent=es?'HYDRA está preparando la página del proveedor. Tu contraseña y MFA permanecen en el proveedor.':'HYDRA is preparing the provider page. Your password and MFA stay with the provider.';
    const channelName=decodeURIComponent(window.location.hash.slice(1));
    history.replaceState(null,'',window.location.pathname+window.location.search);
    try { window.opener = null; } catch { /* best effort */ }
    if (!channelName || typeof BroadcastChannel === 'undefined') {
        document.getElementById('message').textContent=es?'Vuelve a HYDRA y utiliza el enlace alternativo de acceso al proveedor.':'Return to HYDRA and use the provider sign-in fallback link.';
        return;
    }
    const channel=new BroadcastChannel(channelName);
    channel.onmessage=event=>{
        if(event.data?.type!=='HYDRA_REMOTE_AUTH_NAVIGATE'||typeof event.data.url!=='string') return;
        try {
            const target=new URL(event.data.url);
            if(target.protocol!=='https:'||target.username!==''||target.password!=='') throw new Error('invalid');
            channel.close();
            window.location.replace(target.href);
        } catch {
            document.getElementById('message').textContent=es?'El proveedor devolvió una dirección de acceso no válida. Vuelve a HYDRA para revisar el problema.':'The provider returned an invalid sign-in address. Return to HYDRA to review the problem.';
        }
    };
    channel.postMessage({type:'HYDRA_REMOTE_AUTH_READY'});
})();
