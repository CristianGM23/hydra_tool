import { describe, expect, it } from 'vitest';
import { toolchainProfileDescription } from './toolchainProfileDescription';
import type { GrammarToolchainProfile } from './types';

const langium = {id:'langium-4.4-browser',technology:'LANGIUM',providerVersion:'4.4.0',runtimeVersion:'4.4.0',javaVersion:'',nodeVersion:'22.12+',buildSystem:'npm',targetLanguage:'TypeScript',
    warningsAsErrors:false,analysisTimeoutMs:8000,compileTimeoutMs:60000,maximumMemoryBytes:536870912,executionMode:'HYBRID',
    settings:{languageServer:'true',browserWorker:'true',migration:'explicit',recommendedNodeVersion:'26.12.1'}} as GrammarToolchainProfile;

describe('toolchainProfileDescription',()=>{
    it('shows the minimum Node version together with the recommended runtime',()=>{
        expect(toolchainProfileDescription(langium)).toBe('Provider 4.4.0 · npm · target TypeScript · Node 22.12+ (recommended 26.12.1)');
    });

    it('shows only the declared range when the profile recommends no runtime',()=>{
        expect(toolchainProfileDescription({...langium,id:'langium-4.3-browser',nodeVersion:'22.12-24.19',settings:{}})).toBe('Provider 4.4.0 · npm · target TypeScript · Node 22.12-24.19');
    });

    it('omits Node for Java toolchains and keeps their Java requirement',()=>{
        expect(toolchainProfileDescription({...langium,providerVersion:'2.44.0',buildSystem:'maven',targetLanguage:'Java',javaVersion:'21+',nodeVersion:'',settings:{}}))
            .toBe('Provider 2.44.0 · maven · target Java · Java 21+');
    });
});
