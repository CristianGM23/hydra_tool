import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isWorkerInfrastructureFailure, prioritizeRecoveryPools } from './hydra-test-runner-policy.mjs';

const frontendRoot=resolve(fileURLToPath(new URL('..',import.meta.url)));
const vitestCli=resolve(frontendRoot,'node_modules','vitest','vitest.mjs');
const reportPath=resolve(frontendRoot,process.env.HYDRA_AGGRESSIVE_REPORT ?? 'test-results/hydra-aggressive-summary.json');
const seeds=(process.env.HYDRA_AGGRESSIVE_SEEDS ?? '1010,2718,31415').split(',').map(v=>Number.parseInt(v.trim(),10)).filter(Number.isFinite);
const batchSizes=(process.env.HYDRA_AGGRESSIVE_BATCH_SIZES ?? '7,13,23').split(',').map(v=>Number.parseInt(v.trim(),10)).filter(v=>Number.isFinite(v)&&v>0);
const pools=(process.env.HYDRA_AGGRESSIVE_POOLS ?? 'forks,threads,vmThreads').split(',').map(v=>v.trim()).filter(Boolean);
const degradedPools=new Set();

if(!existsSync(vitestCli)){
    console.error('[HYDRA][AGGRESSIVE] Vitest is not installed. Run npm ci --include=dev first.');
    process.exit(2);
}

function collectTests(directory){
    const tests=[];
    for(const entry of readdirSync(directory,{withFileTypes:true})){
        const absolute=join(directory,entry.name);
        if(entry.isDirectory()) tests.push(...collectTests(absolute));
        else if(/\.(?:test|spec)\.(?:[cm]?[jt]s|tsx|jsx)$/.test(entry.name)) tests.push(relative(frontendRoot,absolute));
    }
    return tests;
}

function mulberry32(seed){let value=seed>>>0;return()=>{value|=0;value=value+0x6D2B79F5|0;let t=Math.imul(value^value>>>15,1|value);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296;};}
function shuffled(values,seed){const copy=[...values];const random=mulberry32(seed);for(let i=copy.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[copy[i],copy[j]]=[copy[j],copy[i]];}return copy;}
function run(files,pool,timeout='120000'){
    const result=spawnSync(process.execPath,[vitestCli,'run',...files],{cwd:frontendRoot,env:{...process.env,HYDRA_TEST_PROFILE:'aggressive',HYDRA_TEST_POOL:pool,HYDRA_TEST_FILE_PARALLELISM:'false',HYDRA_TEST_MAX_WORKERS:'1',HYDRA_TEST_TIMEOUT_MS:timeout},encoding:'utf8',maxBuffer:32*1024*1024,stdio:'pipe',shell:false,windowsHide:true});
    return {status:result.status??1,output:`${result.stdout??''}${result.stderr??''}`};
}
function print(result){if(result.output) process.stdout.write(result.output.endsWith('\n')?result.output:`${result.output}\n`);}
function persist(report){mkdirSync(dirname(reportPath),{recursive:true});writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n','utf8');}
function outputTail(output,limit=2400){const value=String(output??'');return value.length<=limit?value:value.slice(-limit);}

function runWithInfrastructureRecovery(files,preferredPool,timeout,scope,report){
    const recoveryPools=prioritizeRecoveryPools(process.platform,preferredPool,[...degradedPools]);
    let lastResult={status:1,output:''};
    const attempts=[];
    let sawInfrastructureFailure=false;
    for(const recoveryPool of recoveryPools){
        lastResult=run(files,recoveryPool,timeout);
        const infrastructureFailure=lastResult.status!==0&&isWorkerInfrastructureFailure(lastResult.output);
        attempts.push({pool:recoveryPool,status:lastResult.status===0?'PASSED':infrastructureFailure?'INFRASTRUCTURE_FAILED':'FAILED'});
        if(lastResult.status===0){
            return {result:lastResult,pool:recoveryPool,recovered:sawInfrastructureFailure,attempts,infrastructureExhausted:false};
        }
        if(!infrastructureFailure){
            return {result:lastResult,pool:recoveryPool,recovered:false,attempts,infrastructureExhausted:false};
        }
        sawInfrastructureFailure=true;
        degradedPools.add(recoveryPool);
        report.infrastructureEvents.push({scope,pool:recoveryPool,files:[...files],diagnostic:outputTail(lastResult.output)});
        console.warn(`[HYDRA][AGGRESSIVE] ${scope}: Vitest worker infrastructure failed with pool=${recoveryPool}; retrying the same tests with another pool.`);
    }
    return {result:lastResult,pool:recoveryPools.at(-1)??preferredPool,recovered:sawInfrastructureFailure,attempts,infrastructureExhausted:true};
}

const all=[...collectTests(resolve(frontendRoot,'src')),...collectTests(resolve(frontendRoot,'scripts'))].sort();
const campaign=all.filter(file=>file.includes('aggressive-testing'));
const report={schemaVersion:'1.1',startedAt:new Date().toISOString(),frontendFiles:all.length,campaignFiles:campaign.length,seeds,batchSizes,pools,infrastructureEvents:[],passes:[],status:'RUNNING'};
persist(report);

console.log(`[HYDRA][AGGRESSIVE] Dedicated acceptance campaign: ${campaign.length} files.`);
const dedicatedAttempt=runWithInfrastructureRecovery(campaign,pools[0]??'forks','120000','Dedicated campaign',report);
const dedicated=dedicatedAttempt.result;
if(dedicated.status===0||!dedicatedAttempt.recovered) print(dedicated);
report.dedicatedCampaign={requestedPool:pools[0]??'forks',pool:dedicatedAttempt.pool,attempts:dedicatedAttempt.attempts,recoveredFromInfrastructureFailure:dedicatedAttempt.recovered,status:dedicated.status===0?'PASSED':dedicatedAttempt.infrastructureExhausted?'INFRASTRUCTURE_FAILED':'FAILED'};
persist(report);
if(dedicatedAttempt.recovered&&dedicated.status===0) console.warn(`[HYDRA][AGGRESSIVE] Dedicated campaign recovered with pool=${dedicatedAttempt.pool}; degraded pools for this run: ${[...degradedPools].join(', ')||'none'}.`);
if(dedicated.status!==0){
    if(dedicatedAttempt.infrastructureExhausted){
        print(dedicated);
        report.status='FAILED_INFRASTRUCTURE';
        console.error('[HYDRA][AGGRESSIVE] Dedicated campaign could not be certified because every worker pool failed to bootstrap. No product assertion is being reported as the cause.');
    }else report.status='FAILED_DEDICATED_CAMPAIGN';
    report.finishedAt=new Date().toISOString();persist(report);process.exit(dedicated.status||1);
}

for(let passIndex=0;passIndex<seeds.length;passIndex++){
    const seed=seeds[passIndex]; const size=batchSizes[passIndex%batchSizes.length]; const requestedPool=pools[passIndex%pools.length]??'threads';
    const ordered=shuffled(all,seed); const pass={seed,batchSize:size,requestedPool,batches:[],status:'RUNNING'}; report.passes.push(pass); persist(report);
    console.log(`[HYDRA][AGGRESSIVE] Pass ${passIndex+1}/${seeds.length}: seed=${seed}, batch=${size}, requestedPool=${requestedPool}, files=${ordered.length}.`);
    for(let offset=0;offset<ordered.length;offset+=size){
        const files=ordered.slice(offset,offset+size); const number=Math.floor(offset/size)+1;
        const batchAttempt=runWithInfrastructureRecovery(files,requestedPool,'120000',`Pass ${passIndex+1} batch ${number}`,report);
        const result=batchAttempt.result;
        const batch={number,files,requestedPool,pool:batchAttempt.pool,attempts:batchAttempt.attempts,recoveredFromInfrastructureFailure:batchAttempt.recovered,status:result.status===0?'PASSED':batchAttempt.infrastructureExhausted?'INFRASTRUCTURE_FAILED':'FAILED',isolated:[]}; pass.batches.push(batch); persist(report);
        if(result.status===0){
            print(result);
            if(batchAttempt.recovered) console.warn(`[HYDRA][AGGRESSIVE] Pass ${passIndex+1} batch ${number} recovered with pool=${batchAttempt.pool}; degraded pools: ${[...degradedPools].join(', ')||'none'}.`);
            continue;
        }
        if(batchAttempt.infrastructureExhausted){
            print(result);
            pass.status='FAILED_INFRASTRUCTURE';report.status='FAILED_INFRASTRUCTURE';report.finishedAt=new Date().toISOString();persist(report);
            console.error(`[HYDRA][AGGRESSIVE] Pass ${passIndex+1} batch ${number} could not be certified because all worker pools failed to bootstrap.`);
            process.exit(1);
        }

        print(result);
        console.warn(`[HYDRA][AGGRESSIVE] Pass ${passIndex+1} batch ${number} has a product/order-dependent failure. Isolating ${files.length} files.`);
        let functionalFailure=false;
        for(const file of files){
            const isolatedAttempt=runWithInfrastructureRecovery([file],batchAttempt.pool,'120000',`Pass ${passIndex+1} batch ${number} isolated ${file}`,report);
            const isolatedResult=isolatedAttempt.result;
            if(isolatedResult.status===0){batch.isolated.push({file,status:'PASSED',pool:isolatedAttempt.pool,recoveredFromInfrastructureFailure:isolatedAttempt.recovered});continue;}
            if(isolatedAttempt.infrastructureExhausted){
                print(isolatedResult);batch.isolated.push({file,status:'INFRASTRUCTURE_FAILED',pool:isolatedAttempt.pool});
                pass.status='FAILED_INFRASTRUCTURE';report.status='FAILED_INFRASTRUCTURE';report.finishedAt=new Date().toISOString();persist(report);
                console.error(`[HYDRA][AGGRESSIVE] Could not certify isolated test ${file}: every worker pool failed to bootstrap.`);
                process.exit(1);
            }
            print(isolatedResult);batch.isolated.push({file,status:'FAILED',pool:isolatedAttempt.pool});functionalFailure=true;break;
        }
        if(functionalFailure){pass.status='FAILED_REPRODUCIBLE';report.status='FAILED_REPRODUCIBLE';report.finishedAt=new Date().toISOString();persist(report);process.exit(1);}

        pass.status='FAILED_ORDER_DEPENDENT';report.status='FAILED_ORDER_DEPENDENT';report.finishedAt=new Date().toISOString();persist(report);
        console.error('[HYDRA][AGGRESSIVE] All implicated files pass alone but failed together. This is a test-isolation/order-dependence defect and is intentionally fatal in aggressive mode.');
        process.exit(1);
    }
    pass.status='PASSED'; persist(report);
}
report.status='PASSED';report.degradedPools=[...degradedPools];report.finishedAt=new Date().toISOString();persist(report);
console.log(`[HYDRA][AGGRESSIVE] PASS: ${seeds.length} shuffled full-suite passes plus dedicated process/artifact tests. Infrastructure recoveries=${report.infrastructureEvents.length}; degraded pools=${report.degradedPools.join(', ')||'none'}. Report: ${relative(frontendRoot,reportPath)}`);
