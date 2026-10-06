import { render,screen } from '@testing-library/react';
import { describe,expect,it } from 'vitest';
import { IntegrationTechnologyJourney } from './IntegrationTechnologyJourney';
import type { TechnologyWorkflowDescriptor,TechnologyWorkflowStage } from './types';
const stages:TechnologyWorkflowStage[]=['CONFIGURE','ANALYZE','GENERATE','VERIFY','PREVIEW','APPLY','EXECUTE','ROUND_TRIP','EXPORT'];
const workflow:TechnologyWorkflowDescriptor={technology:'ANTLR',displayName:'ANTLR',targetKind:'TEXTUAL_LANGUAGE_TOOLING',maturity:'DECLARED_ONLY',parityScore:31,adapterImplemented:false,validatorImplemented:false,blockers:['No executable TechnologyAdapter is registered for this technology.'],recommendations:['Implement and register the technology adapter before offering generated-project delivery.'],stages:stages.map(stage=>({stage,support:stage==='CONFIGURE'||stage==='ANALYZE'?'FULL':'UNSUPPORTED',applicable:true,action:stage==='CONFIGURE'?'EDIT_PROFILE':stage==='ANALYZE'?'CHECK_PROFILE':'NONE',title:stage==='ROUND_TRIP'?'Round-trip':stage[0]+stage.slice(1).toLowerCase(),description:`${stage} description`,outcome:`${stage} outcome`,prerequisites:[]}))};
describe('IntegrationTechnologyJourney',()=>{
 it('makes declared-only maturity and blockers explicit',()=>{render(<IntegrationTechnologyJourney workflow={workflow}/>);expect(screen.getByText('31%')).toBeInTheDocument();expect(screen.getByText('Declared only')).toBeInTheDocument();expect(screen.getByText(/No executable TechnologyAdapter/)).toBeInTheDocument();expect(screen.getByText('Generate')).toBeInTheDocument();expect(screen.getAllByText('Not available').length).toBeGreaterThan(0);});
});
