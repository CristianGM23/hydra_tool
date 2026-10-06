import { performance } from 'node:perf_hooks';
const sizes=[100,500,2000,5000];
let failed=false;
for(const size of sizes){
 const values=Array.from({length:size},(_,i)=>({id:`node-${i}`,value:i}));
 const started=performance.now();
 for(let round=0;round<200;round++){
  const index=new Map(values.map(value=>[value.id,value]));
  if(index.get(`node-${size-1}`)?.value!==size-1) throw new Error('index lookup failed');
 }
 const elapsed=performance.now()-started;
 const budget=size<=500?1000:2500;
 console.log(`${size} nodes :: ${elapsed.toFixed(2)} ms / budget ${budget} ms`);
 if(elapsed>budget) failed=true;
}
if(failed) process.exit(1);
