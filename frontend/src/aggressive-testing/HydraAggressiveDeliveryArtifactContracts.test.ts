import { afterEach, describe, expect, it, vi } from 'vitest';
import { planStandaloneDistribution, materializeStandaloneDistribution, validateStandaloneDistribution, packageStandaloneDistribution } from '../distribution-center/api';
import type { StandaloneDistributionProfile } from '../distribution-center/types';
import { validatePublication, publishDistribution, checkDistributionUpdate } from '../publication-center/api';
import { prepareAdvancedRoundTrip, applyAdvancedRoundTrip, rollbackAdvancedRoundTrip } from '../roundtrip-center/api';
import { previewRepresentableImport, applyRepresentableImport } from '../representable-import-center/api';
import { loadGeneratedProjectRegistry, executeGeneratedProject } from '../generated-project-center/api';
import { loadArtifactGraph, loadArtifactRegistry, scanArtifacts } from '../artifact-registry-center/api';
import { loadLatestCompatibilityMatrix, runNativeCompatibility } from '../compatibility-center/api';
import { loadCapabilityReadiness, previewCapabilityLayerTransaction, applyCapabilityLayerTransaction, rollbackCapabilityLayerTransaction } from '../capability-center/api';
import { projectArchiveDownloadUrl, projectDescriptorDrawioUrl, projectFileDownloadUrl, projectFileVisualUrl, projectTreeDrawioUrl } from '../project-explorer/projectApi';

const profile:StandaloneDistributionProfile={productName:'Aggressive Editor',version:'9.9.9',targets:['PWA'],offlineEnabled:true,fileAssociations:['.xmi'],updateChannel:'stable'};
const jsonResponse=(value:unknown,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}});

function installDispatcher() {
    const fetchMock=vi.fn(async (input:RequestInfo|URL, init?:RequestInit) => {
        const url=String(input);
        if(url.endsWith('/api/standalone-distributions/plan')) return jsonResponse([{path:'public/manifest.webmanifest',mediaType:'application/manifest+json',content:'{}'}]);
        if(url.endsWith('/api/standalone-distributions/materialize')) return jsonResponse({materialization:{root:'aggressive-editor',writtenPaths:['public/manifest.webmanifest'],sha256ByPath:{'public/manifest.webmanifest':'abc'}},validation:{valid:true,diagnostics:[],metadata:{}}});
        if(url.endsWith('/api/standalone-distributions/validate')) return jsonResponse({valid:true,diagnostics:[],metadata:{}});
        if(url.endsWith('/api/standalone-distributions/package')) return new Response(Uint8Array.from([0x50,0x4b,0x03,0x04,1,2,3]),{status:200,headers:{'Content-Disposition':'attachment; filename="aggressive.zip"','X-HYDRA-Distribution-SHA256':'deadbeef'}});
        if(url.endsWith('/api/publications/validate')) return jsonResponse({valid:true,diagnostics:[]});
        if(url.endsWith('/api/publications/publish')) return jsonResponse({manifest:{schemaVersion:'1.0',productId:'aggressive',version:'1.0.0',channel:'INTERNAL',signingMode:'CHECKSUM_ONLY',publishedAt:'2026-09-29T00:00:00Z',artifacts:[],metadata:{}},manifestPath:'manifest.json',channelManifestPath:'channels/internal.json',validation:{valid:true,diagnostics:[]}});
        if(url.endsWith('/api/publications/check')) return jsonResponse({updateAvailable:false,currentVersion:'1.0.0',availableVersion:'1.0.0',artifact:null,message:'Current'});
        if(url.includes('/technology/sync/resolve/rollback/')) return jsonResponse({operationId:'rollback',rollback:true,affectedPaths:['src/model.ts'],journalPath:'.hydra/journal.json'});
        if(url.endsWith('/technology/sync/resolve/apply')) return jsonResponse({operationId:'apply',rollback:false,affectedPaths:['src/model.ts'],journalPath:'.hydra/journal.json'});
        if(url.endsWith('/technology/sync/resolve')) return jsonResponse({operationId:'preview',actions:[],unresolved:[],canApply:true});
        if(url.endsWith('/api/representable-imports/preview')||url.endsWith('/api/representable-imports/apply')) return jsonResponse({previewId:'p1',technology:'ECORE',metamodelId:'people',generatedAt:'2026-09-29T00:00:00Z',operations:[{externalId:'c1',changeKind:'ADD',elementKind:'CLASS',neutralPath:'classes/person',values:{name:'Person'}}],diagnostics:[],applicable:true});
        if(url.endsWith('/api/generated-projects')) return jsonResponse({projects:[]});
        if(url.endsWith('/api/generated-projects/execute')) return jsonResponse({projectId:'p1',successful:true,stages:[],startedAt:'',finishedAt:'',diagnostics:[]});
        if(url.endsWith('/api/artifact-registry/graph')) return jsonResponse({nodes:[],edges:[]});
        if(url.endsWith('/api/artifact-registry/scan')||url.endsWith('/api/artifact-registry')) return jsonResponse({generatedAt:'2026-09-29T00:00:00Z',artifacts:[]});
        if(url.endsWith('/api/toolchain-compatibility/latest')) return new Response(null,{status:204});
        if(url.endsWith('/api/toolchain-compatibility/run')) return jsonResponse({generatedAt:'2026-09-29T00:00:00Z',overallStatus:'PASSED',results:[]});
        if(url.includes('/api/hydra/capabilities/')) return jsonResponse({contextId:'ctx',contextName:'Context',contextType:url.includes('/grammar/')?'GRAMMAR':'METAMODEL',semanticContextId:'ctx',score:100,configuredLayers:7,totalLayers:7,layers:[],recommendedNextSteps:[]});
        if(url.endsWith('/api/capability-packages/transactions/preview')) return jsonResponse({previewId:'pv',targetMetamodelId:'people',items:[],totalBytes:1,applicable:true,diagnostics:[]});
        if(url.endsWith('/api/capability-packages/transactions/apply')) return jsonResponse({preview:{previewId:'pv',targetMetamodelId:'people',items:[],totalBytes:1,applicable:true,diagnostics:[]},transactionId:'tx1',persisted:true,rollbackAvailable:true,appliedEtags:{},diagnostics:[]});
        if(url.endsWith('/api/capability-packages/transactions/rollback')) return jsonResponse({transactionId:'tx1',restoredDocuments:['validations.json'],skippedDocuments:[],journalClosed:true,diagnostics:[]});
        throw new Error(`Unhandled aggressive endpoint ${url} ${init?.method??'GET'}`);
    });
    vi.stubGlobal('fetch',fetchMock);
    return fetchMock;
}

afterEach(()=>vi.unstubAllGlobals());

describe('aggressive delivery and artifact proof campaign',()=>{
    it('proves standalone distribution through plan -> materialize -> validate -> package with non-empty ZIP evidence',async()=>{
        const fetchMock=installDispatcher(); const request={editorDirectory:'aggressive-editor',profile};
        const plan=await planStandaloneDistribution(request);
        const materialized=await materializeStandaloneDistribution(request);
        const validation=await validateStandaloneDistribution(request);
        const packaged=await packageStandaloneDistribution(request);
        expect(plan.map(item=>item.path)).toContain('public/manifest.webmanifest');
        expect(materialized.materialization.writtenPaths).toContain('public/manifest.webmanifest');
        expect(validation.valid).toBe(true);
        expect(packaged).toMatchObject({fileName:'aggressive.zip',sha256:'deadbeef'});
        expect(Array.from(new Uint8Array(await packaged.blob.arrayBuffer())).slice(0,4)).toEqual([0x50,0x4b,0x03,0x04]);
        expect(fetchMock.mock.calls.map(call=>String(call[0]))).toEqual(expect.arrayContaining([
            expect.stringContaining('/plan'),expect.stringContaining('/materialize'),expect.stringContaining('/validate'),expect.stringContaining('/package')
        ]));
    });

    it('keeps publication metadata-only and never serializes signing secrets across validate/publish/check',async()=>{
        const fetchMock=installDispatcher();
        const draft={productId:'aggressive',version:'1.0.0',channel:'INTERNAL' as const,signingMode:'CHECKSUM_ONLY' as const,distributionDirectory:'dist',updateBaseUrl:'',artifacts:[{platform:'windows-x64',relativePath:'editor.zip',mediaType:'application/zip'}],metadata:{commit:'abc'}};
        await expect(validatePublication(draft)).resolves.toMatchObject({valid:true});
        const published=await publishDistribution(draft);
        await expect(checkDistributionUpdate('aggressive','1.0.0','INTERNAL','windows-x64')).resolves.toMatchObject({updateAvailable:false});
        expect(published.manifestPath).toBe('manifest.json');
        const bodies=fetchMock.mock.calls.map(call=>String(call[1]?.body??''));
        expect(bodies.join('\n').toLowerCase()).not.toMatch(/private[_-]?key|secret|password/);
    });

    it('separates round-trip preview/apply/rollback and safely encodes hostile identifiers',async()=>{
        const fetchMock=installDispatcher();
        await prepareAdvancedRoundTrip('people model','profile/../../x','generated dir',[{conflictKey:'A@a.ts',choice:'MERGE_SAFE',rationale:'keep'}]);
        await applyAdvancedRoundTrip('people model','profile/../../x','generated dir',[]);
        await rollbackAdvancedRoundTrip('people model','profile/../../x','generated dir','sync/../1');
        const urls=fetchMock.mock.calls.map(call=>String(call[0]));
        expect(urls[0]).toContain('people%20model'); expect(urls[0]).toContain('profile%2F..%2F..%2Fx');
        expect(urls[2]).toContain('rollback/sync%2F..%2F1');
        expect(urls[0]).not.toContain('/../');
    });

    it('proves representable imports preserve the candidate identity from preview to apply',async()=>{
        const fetchMock=installDispatcher();
        const changes=[{externalId:'c1',changeKind:'ADD' as const,nativeKind:'EClass',qualifiedName:'people.Person',properties:{name:'Person'}}];
        const preview=await previewRepresentableImport('ECORE','people','SAFE',changes);
        const applied=await applyRepresentableImport('ECORE','people','SAFE',changes);
        expect(preview.operations[0]).toMatchObject({externalId:'c1',neutralPath:'classes/person'});
        expect(applied.operations).toEqual(preview.operations);
        const requestBodies=fetchMock.mock.calls.filter(call=>String(call[0]).includes('representable-imports')).map(call=>JSON.parse(String(call[1]?.body)));
        expect(requestBodies).toHaveLength(2); expect(requestBodies[0].changes).toEqual(requestBodies[1].changes);
    });

    it('restricts generated project execution to lifecycle stages and never transports an arbitrary command',async()=>{
        const fetchMock=installDispatcher();
        await loadGeneratedProjectRegistry();
        await executeGeneratedProject('project; rm -rf /',['VALIDATE'],true,false);
        const call=fetchMock.mock.calls.find(item=>String(item[0]).endsWith('/api/generated-projects/execute'))!;
        const body=JSON.parse(String(call[1]?.body));
        expect(body).toMatchObject({projectId:'project; rm -rf /',stages:['VALIDATE'],dryRun:true,continueAfterFailure:false});
        expect(body).not.toHaveProperty('command');
    });

    it('keeps artifact discovery server-owned and separates registry, graph and rescan operations',async()=>{
        const fetchMock=installDispatcher();
        await loadArtifactRegistry(); await loadArtifactGraph(); await scanArtifacts();
        const urls=fetchMock.mock.calls.map(call=>String(call[0]));
        expect(urls).toEqual(expect.arrayContaining(['/api/artifact-registry','/api/artifact-registry/graph','/api/artifact-registry/scan']));
        const scan=fetchMock.mock.calls.find(call=>String(call[0]).endsWith('/scan'))!;
        expect(scan[1]).toMatchObject({method:'POST'});
    });

    it('treats compatibility as catalog-key execution and never as a command channel',async()=>{
        const fetchMock=installDispatcher();
        await expect(loadLatestCompatibilityMatrix()).resolves.toBeNull();
        await runNativeCompatibility(['langium:configured','text that looks like && cmd.exe']);
        const call=fetchMock.mock.calls.find(item=>String(item[0]).endsWith('/toolchain-compatibility/run'))!;
        const body=JSON.parse(String(call[1]?.body));
        expect(body).toEqual({selectedKeys:['langium:configured','text that looks like && cmd.exe']});
        expect(body).not.toHaveProperty('command');
    });

    it('proves capability changes use preview/apply/rollback and keep grammar and metamodel contexts separate',async()=>{
        const fetchMock=installDispatcher();
        await expect(loadCapabilityReadiness('people model')).resolves.toMatchObject({contextType:'METAMODEL'});
        await expect(loadCapabilityReadiness('GRAMMAR','orders.dsl')).resolves.toMatchObject({contextType:'GRAMMAR'});
        const request={capabilityPackage:{layers:['validation']},targetMetamodelId:'people',expectedEtags:{},layerOrder:['validation'],persist:true};
        const preview=await previewCapabilityLayerTransaction(request); const applied=await applyCapabilityLayerTransaction(request); const rollback=await rollbackCapabilityLayerTransaction(applied.transactionId,['validations.json'],{});
        expect(preview.applicable).toBe(true); expect(applied).toMatchObject({persisted:true,rollbackAvailable:true}); expect(rollback).toMatchObject({journalClosed:true});
        const txBodies=fetchMock.mock.calls.filter(call=>String(call[0]).includes('/transactions/')).map(call=>JSON.parse(String(call[1]?.body??'{}')));
        expect(txBodies[0].persist).toBe(false); expect(txBodies[1].persist).toBe(true);
    });

    it('rejects traversal before browser artifact URL construction and safely encodes valid relative paths',()=>{
        const hostile='../model folder/a#b?.json';
        expect(()=>projectFileDownloadUrl(hostile)).toThrow('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
        expect(()=>projectFileVisualUrl(hostile)).toThrow('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
        expect(()=>projectFileDownloadUrl('models/../secret.json')).toThrow('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
        expect(()=>projectFileVisualUrl('C:\\temp\\secret.svg')).toThrow('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');

        const valid='model folder/a#b?.json';
        for(const url of [projectFileDownloadUrl(valid),projectFileVisualUrl(valid)]) {
            expect(url).toContain('/api/');
            expect(url).toContain('model%20folder%2Fa%23b%3F.json');
            expect(url).not.toContain(' ');
            expect(url).not.toContain('../');
        }
        for(const url of [projectArchiveDownloadUrl(),projectDescriptorDrawioUrl(),projectTreeDrawioUrl('LOGICAL')]) {
            expect(url).toContain('/api/');
            expect(url).not.toContain(' ');
        }
    });
});
