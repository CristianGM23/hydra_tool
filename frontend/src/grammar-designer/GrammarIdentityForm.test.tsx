import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GrammarIdentityForm } from './GrammarIdentityForm';
import { inspectGrammarLocally } from './grammarLocalInspection';
import type { GrammarAnalysisResult, GrammarDefinitionModel, GrammarToolchainProfile } from './types';

const model: GrammarDefinitionModel = {
    schemaVersion:'1.0', id:'language', name:'Language', technology:'LANGIUM', authoringMode:'GRAMMAR_FIRST',
    metamodelId:'', toolchainProfileId:'langium-4.3-browser', entryDocumentId:'main', entryRule:'Model',
    documents:[{id:'main',relativePath:'main.langium',languageId:'langium',content:'',sourceHash:'hash',entryPoint:true,generated:false}],
    semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}}, syntaxStyleDefinitionId:'', settings:{}, annotations:{'hydra.entryRule.mode':'AUTO'}
};
const analysis = {
    requestId:'grammar-ui:test', draftRevision:1, sourceHash:'hash', mode:'NORMAL', diagnostics:[], symbols:[], semanticCandidate:{schemaVersion:'1.0',bindings:[],annotations:{}},
    providerInstanceId:'neutral', stale:false, providerHealth:{status:'HEALTHY',circuitOpen:false,detail:'',capabilitiesUnavailable:[],checkedAt:''}, codeActions:[],
    metadata:{'entryRule.inferred':'Model','entryRule.method':'EXPLICIT_ENTRY','entryRule.confidence':'1.000','entryRule.reason':'Explicit entry marker.','entryRule.candidates':'Model|Element|Expression'}
} as GrammarAnalysisResult;
const profile = {id:'langium-4.3-browser',technology:'LANGIUM',providerVersion:'4.3',runtimeVersion:'4.3',javaVersion:'',nodeVersion:'22',buildSystem:'NPM',targetLanguage:'TypeScript',warningsAsErrors:false,analysisTimeoutMs:1000,compileTimeoutMs:1000,maximumMemoryBytes:1000,executionMode:'HYBRID',settings:{}} as GrammarToolchainProfile;

function renderForm(current:GrammarDefinitionModel=model, onMutate=vi.fn()){
    render(<GrammarIdentityForm model={current} displayedTechnology={current.technology} analysis={analysis} localInspection={inspectGrammarLocally(current)} compatibleProfiles={[profile]} metamodels={[]} onMutate={onMutate}
        onTechnologyChange={vi.fn()} onMetamodelsRefresh={vi.fn()}/>);
    return onMutate;
}

describe('GrammarIdentityForm entry-rule inference',()=>{
    it('renders entry rule as a dropdown with automatic inference and every parser rule as an override',()=>{
        renderForm();
        const select=screen.getByLabelText('Entry rule') as HTMLSelectElement;
        expect(select.tagName).toBe('SELECT');
        expect(select).toHaveTextContent('Automatic · Model');
        expect(select).toHaveTextContent('Element');
        expect(select).toHaveTextContent('Expression');
        expect(screen.queryByText(/Inferred entry rule:/i)).not.toBeInTheDocument();
    });

    it('switches to manual mode when the user selects another parser rule',()=>{
        const onMutate=renderForm();
        fireEvent.change(screen.getByLabelText('Entry rule'),{target:{value:'Expression'}});
        expect(onMutate).toHaveBeenCalledWith(expect.objectContaining({entryRule:'Expression',annotations:expect.objectContaining({'hydra.entryRule.mode':'MANUAL'})}));
    });

    it('returns to inferred mode without losing the inferred rule',()=>{
        const manual={...model,entryRule:'Expression',annotations:{...model.annotations,'hydra.entryRule.mode':'MANUAL'}};
        const onMutate=renderForm(manual);
        fireEvent.change(screen.getByLabelText('Entry rule'),{target:{value:'__HYDRA_AUTOMATIC_ENTRY_RULE__'}});
        expect(onMutate).toHaveBeenCalledWith(expect.objectContaining({entryRule:'Model',annotations:expect.objectContaining({'hydra.entryRule.mode':'AUTO'})}));
    });

    it('offers parser rules immediately while backend analysis is still pending',()=>{
        const current={...model,documents:[{...model.documents[0],content:'grammar Demo\nentry Model: elements+=Element*;\nElement: name=ID;'}]};
        render(<GrammarIdentityForm model={current} displayedTechnology={current.technology} analysis={null} localInspection={inspectGrammarLocally(current)} compatibleProfiles={[profile]} metamodels={[]} onMutate={vi.fn()} onTechnologyChange={vi.fn()} onMetamodelsRefresh={vi.fn()}/>);
        const select=screen.getByLabelText('Entry rule');
        expect(select).toHaveTextContent('Automatic · Model');
        expect(select).toHaveTextContent('Element');
        expect(screen.queryByText(/provisional until backend analysis confirms it/i)).not.toBeInTheDocument();
    });

    it('shows a requested conversion target without mutating the persisted source technology',()=>{
        render(<GrammarIdentityForm model={model} displayedTechnology="XTEXT" analysis={analysis} localInspection={inspectGrammarLocally(model)} compatibleProfiles={[profile]} metamodels={[]} onMutate={vi.fn()} onTechnologyChange={vi.fn()} onMetamodelsRefresh={vi.fn()}/>);
        expect((screen.getByLabelText('Technology') as HTMLSelectElement).value).toBe('XTEXT');
        expect(model.technology).toBe('LANGIUM');
    });
});
