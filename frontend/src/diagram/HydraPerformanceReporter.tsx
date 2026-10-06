import { useEffect } from 'react';
import { backendFetch } from '../api/backendFetch';
import type { HydraDiagramTimingSample } from './performanceTelemetry';

/** Batches privacy-preserving rendering metrics; diagram/resource identifiers never leave the browser. */
export function HydraPerformanceReporter() {
    useEffect(()=>{
        const queue: HydraDiagramTimingSample[]=[]; let timer:number|undefined;
        const flush=()=>{timer=undefined;const batch=queue.splice(0,Math.min(32,queue.length));for(const sample of batch){void backendFetch('/api/system/observability/rendering',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({phase:sample.phase,durationMs:sample.durationMs,nodeCount:sample.nodeCount??0,edgeCount:sample.edgeCount??0}),keepalive:true}).catch(()=>undefined);}if(queue.length>0)timer=window.setTimeout(flush,5000);};
        const onTiming=(event:Event)=>{const sample=(event as CustomEvent<HydraDiagramTimingSample>).detail;if(!sample)return;queue.push(sample);if(queue.length>100)queue.splice(0,queue.length-100);if(queue.length>=20)flush();else if(timer===undefined)timer=window.setTimeout(flush,10000);};
        window.addEventListener('hydra:diagram-performance-timing',onTiming);return()=>{window.removeEventListener('hydra:diagram-performance-timing',onTiming);if(timer!==undefined)window.clearTimeout(timer);};
    },[]);
    return null;
}
