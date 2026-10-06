import { describe,expect,it } from 'vitest';
import { normalizeTechnologyWorkflowDescriptor } from './technologyWorkflowNormalization';

const stages=['CONFIGURE','ANALYZE','GENERATE','VERIFY','PREVIEW','APPLY','EXECUTE','ROUND_TRIP','EXPORT'] as const;
function descriptor(technology='ANTLR'){
 return {technology,displayName:technology,targetKind:'TEXTUAL_LANGUAGE_TOOLING',maturity:'DECLARED_ONLY',parityScore:37,adapterImplemented:false,validatorImplemented:false,blockers:['No adapter'],recommendations:[],stages:stages.map(stage=>({stage,support:stage==='ANALYZE'?'FULL':'UNSUPPORTED',applicable:true,action:stage==='ANALYZE'?'CHECK_PROFILE':'NONE',title:stage,description:'',outcome:'',prerequisites:[]}))};
}

describe('technology workflow normalization',()=>{
 it('accepts ANTLR and preserves the complete ordered workflow',()=>{const value=normalizeTechnologyWorkflowDescriptor(descriptor());expect(value.technology).toBe('ANTLR');expect(value.stages.map(item=>item.stage)).toEqual(stages);expect(value.parityScore).toBe(37);});
 it('fails closed when a workflow omits one of the canonical stages',()=>{const value=descriptor();value.stages=value.stages.slice(0,-1);expect(()=>normalizeTechnologyWorkflowDescriptor(value)).toThrow(/every process stage/);});
 it('bounds malformed parity scores without inventing maturity',()=>{const value=descriptor('XTEXT');value.parityScore=180;value.maturity='OPERATIONAL';value.adapterImplemented=true;value.validatorImplemented=true;expect(normalizeTechnologyWorkflowDescriptor(value).parityScore).toBe(100);});
});
