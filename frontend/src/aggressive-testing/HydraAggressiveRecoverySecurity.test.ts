import { describe, expect, it } from 'vitest';
import { validatePrimaryRepositoryUrl } from '../repository-connections/repositoryProfileModel';
import { recommendedLayerSelection, allLayerSelection } from '../update-site-import-center/updateSiteLayerSelection';
import type { UpdateSiteImportAnalysis } from '../update-site-import-center/types';
import { parseExternalChanges, resolutionComplete } from '../representable-import-center/representableImportGuidance';

const t=(value:string)=>value;
const analysis={
    recoveredLayers:{
        palettes:[{id:'palette:trusted',label:'Trusted',order:0,sourcePath:'site.gmftool',confidence:'DERIVED',tools:[
            {id:'tool:exact',label:'Exact',kind:'NODE',targetElement:'Person',sourcePath:'site.gmftool',confidence:'EXACT'},
            {id:'tool:inferred',label:'Inferred',kind:'NODE',targetElement:'Ghost',sourcePath:'site.gmftool',confidence:'INFERRED'}]}],
        representations:[{id:'representation:recovered',name:'Recovered',technology:'GMF',kind:'DIAGRAM',rootElements:['Person'],sourcePath:'site.gmfgraph',confidence:'RECOVERED',warnings:[]},
            {id:'representation:inferred',name:'Inferred',technology:'GMF',kind:'DIAGRAM',rootElements:['Ghost'],sourcePath:'site.gmfgraph',confidence:'INFERRED',warnings:[]}],
        mappings:[{id:'mapping:clean',semanticElement:'Person',visualElement:'PersonFigure',mappingKind:'NODE',sourcePath:'site.gmfmap',confidence:'DERIVED',diagnostic:''},
            {id:'mapping:diagnostic',semanticElement:'Ghost',visualElement:'GhostFigure',mappingKind:'NODE',sourcePath:'site.gmfmap',confidence:'EXACT',diagnostic:'ambiguous'}],diagnostics:[]},
    semanticRecovery:{elements:[{id:'semantic:ok',kind:'COMMAND',label:'Open',implementation:'',sourcePath:'plugin.xml',extensionPoint:'commands',confidence:'RECOVERED',representable:true,neutralTarget:'Operation',purpose:'',warnings:[]},
        {id:'semantic:inferred',kind:'COMMAND',label:'Guess',implementation:'',sourcePath:'plugin.xml',extensionPoint:'commands',confidence:'INFERRED',representable:true,neutralTarget:'Operation',purpose:'',warnings:[]},
        {id:'semantic:blocked',kind:'UNREPRESENTABLE',label:'Blocked',implementation:'',sourcePath:'plugin.xml',extensionPoint:'custom',confidence:'EXACT',representable:false,neutralTarget:'',purpose:'',warnings:[]}],representableCount:2,partialCount:0,unrepresentableCount:1,diagnostics:[]}
} as unknown as UpdateSiteImportAnalysis;

describe('aggressive recovery and repository security campaign',()=>{
    it.each([
        'http://github.com/openai/hydra',
        'https://user:secret@github.com/openai/hydra',
        'https://github.com/',
        'not-a-url',
        'file:///etc/passwd'
    ])('rejects unsafe or incomplete repository coordinate %s',(value:string)=>{
        expect(validatePrimaryRepositoryUrl(value,t)).toBeDefined();
    });

    it.each([
        'https://github.com/openai/hydra',
        'https://gitlab.com/group/project',
        'https://drive.google.com/drive/folders/abc',
        'https://www.dropbox.com/scl/fo/abc'
    ])('accepts complete HTTPS provider coordinates %s',(value:string)=>{
        expect(validatePrimaryRepositoryUrl(value,t)).toBeUndefined();
    });

    it('fails closed when update-site evidence is inferred, ambiguous or unrepresentable',()=>{
        const safe=recommendedLayerSelection(analysis); const everything=allLayerSelection(analysis);
        expect(safe.paletteToolIds).toEqual(['tool:exact']);
        expect(safe.representationIds).toEqual(['representation:recovered']);
        expect(safe.mappingIds).toEqual(['mapping:clean']);
        expect(safe.semanticElementIds).toEqual(['semantic:ok']);
        expect(everything.paletteToolIds).toContain('tool:inferred');
        expect(everything.semanticElementIds).toContain('semantic:blocked');
    });

    it('rejects malformed representable-import batches instead of partially guessing their identity',()=>{
        const valid=JSON.stringify(Array.from({length:100},(_,index)=>({externalId:`c${index}`,changeKind:'ADD',nativeKind:'EClass',qualifiedName:`x.C${index}`,properties:{}})));
        const parsed=parseExternalChanges(valid);
        expect(parsed.valid).toBe(true); expect(parsed.changes).toHaveLength(100);
        for(const hostile of ['{}','[{"changeKind":"ADD"}]','[{"externalId":"x","changeKind":"DROP TABLE"}]','not json']) expect(parseExternalChanges(hostile).valid).toBe(false);
    });

    it('requires an explicit resolution for every class-like external change before apply',()=>{
        const changes=parseExternalChanges('[{"externalId":"c1","changeKind":"ADD","nativeKind":"EClass","qualifiedName":"x.Customer","properties":{}},{"externalId":"c2","changeKind":"ADD","nativeKind":"EClass","qualifiedName":"x.Order","properties":{}}]').changes;
        expect(resolutionComplete(changes,{c1:'CREATE_NEW'})).toBe(false);
        expect(resolutionComplete(changes,{c1:'CREATE_NEW',c2:'EXISTING:class:Order'})).toBe(true);
    });
});
