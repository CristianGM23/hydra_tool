import { fireEvent,render,screen,waitFor } from '@testing-library/react';
import { beforeEach,describe,expect,it,vi } from 'vitest';
import { IntegrationDesigner } from './IntegrationDesigner';
import type { IntegrationTechnologyDescriptor } from './types';
const technologies:IntegrationTechnologyDescriptor[]=[
 {technology:'ECLIPSE_GLSP',displayName:'Eclipse GLSP',targetKind:'GRAPHICAL_MODELING_EDITOR',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PACKAGE',recommendedBuildSystem:'NPM',deliveryModes:['GENERATED_MODULE','HYBRID'],injectionStrategies:['ADD_PACKAGE'],locatorStrategies:['GLSP_DIAGRAM_TYPE','GLSP_ELEMENT_ID'],capabilities:{},notes:['TypeScript client and Java or TypeScript server.']},
 {technology:'BESSER_SDK',displayName:'BESSER Python SDK',targetKind:'MODELING_RUNTIME',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PACKAGE',recommendedBuildSystem:'PIP',deliveryModes:['GENERATED_MODULE'],injectionStrategies:['ADD_PACKAGE'],locatorStrategies:['BESSER_ELEMENT_ID'],capabilities:{},notes:['Python model and generator target.']},
 {technology:'BESSER_WEB_MODELING_EDITOR',displayName:'BESSER Web Modeling Editor',targetKind:'GRAPHICAL_MODELING_EDITOR',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PACKAGE',recommendedBuildSystem:'NPM',deliveryModes:['REMOTE','HYBRID','GENERATED_MODULE'],injectionStrategies:['ADD_PACKAGE'],locatorStrategies:['BESSER_ELEMENT_ID'],capabilities:{},notes:['UI companion target.']},
 {technology:'SIRIUS_DESKTOP',displayName:'Eclipse Sirius Desktop',targetKind:'GRAPHICAL_MODELING_EDITOR',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PLUGIN',recommendedBuildSystem:'TYCHO',deliveryModes:['EMBEDDED','REMOTE','HYBRID','GENERATED_MODULE'],injectionStrategies:['ADD_PLUGIN','ADD_MODULE','GENERATE_SIDECAR','PATCH_EXISTING'],locatorStrategies:['QUALIFIED_NAME'],capabilities:{},notes:['Eclipse plug-in target.']},
 {technology:'MXGRAPH_DRAWIO',displayName:'mxGraph / draw.io',targetKind:'GRAPHICAL_MODELING_EDITOR',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PACKAGE',recommendedBuildSystem:'NPM',deliveryModes:['EMBEDDED','HYBRID','GENERATED_MODULE'],injectionStrategies:['ADD_PACKAGE','ADD_MODULE','GENERATE_SIDECAR'],locatorStrategies:['CUSTOM','QUALIFIED_NAME'],capabilities:{},notes:['Browser graphical editor target.']},
 {technology:'ANTLR',displayName:'ANTLR',targetKind:'TEXTUAL_LANGUAGE_TOOLING',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_MODULE',recommendedBuildSystem:'MAVEN',deliveryModes:['GENERATED_MODULE'],injectionStrategies:['ADD_MODULE'],locatorStrategies:['AST_PATH','QUALIFIED_NAME'],capabilities:{MODEL_READ:'FULL',PROJECT_EXPORT:'FULL'},notes:['Declared parser target.']},
 {technology:'PLANTUML',displayName:'PlantUML',targetKind:'DIAGRAM_PROJECTION',recommendedDeliveryMode:'PROJECTION_ONLY',recommendedInjectionStrategy:'GENERATE_PROJECTION',recommendedBuildSystem:'NONE',deliveryModes:['PROJECTION_ONLY'],injectionStrategies:['GENERATE_PROJECTION'],locatorStrategies:['PLANTUML_ALIAS'],capabilities:{},notes:['Projection only.']}
];
const workflowStages=['CONFIGURE','ANALYZE','GENERATE','VERIFY','PREVIEW','APPLY','EXECUTE','ROUND_TRIP','EXPORT'] as const;
const workflows=technologies.map(item=>({technology:item.technology,displayName:item.displayName,targetKind:item.targetKind,maturity:item.technology==='ANTLR'?'DECLARED_ONLY':item.technology==='PLANTUML'?'PROJECTION_ONLY':'OPERATIONAL',parityScore:item.technology==='ANTLR'?28:82,adapterImplemented:item.technology!=='ANTLR'&&item.technology!=='PLANTUML',validatorImplemented:item.technology!=='ANTLR'&&item.technology!=='PLANTUML',stages:workflowStages.map(stage=>({stage,support:item.technology==='ANTLR'&&stage!=='CONFIGURE'&&stage!=='ANALYZE'?'UNSUPPORTED':stage==='EXECUTE'?'EXTERNAL':'FULL',applicable:!(item.technology==='PLANTUML'&&['PREVIEW','APPLY','EXECUTE'].includes(stage)),action:'NONE',title:stage==='ROUND_TRIP'?'Round-trip':stage[0]+stage.slice(1).toLowerCase(),description:`${stage} description`,outcome:`${stage} outcome`,prerequisites:[]})),blockers:item.technology==='ANTLR'?['No executable TechnologyAdapter is registered for ANTLR.']:[],recommendations:[]}));
vi.mock('./api',()=>({
 listIntegrationTechnologies:vi.fn(async()=>technologies),listIntegrationTechnologyWorkflows:vi.fn(async()=>workflows),listIntegrationProfiles:vi.fn(async()=>[]),loadIntegrationProfile:vi.fn(),
 checkIntegrationProfile:vi.fn(async model=>({profileId:model.id,diagnostics:[],artifactResolutions:[],valid:true})),
 createIntegrationProfile:vi.fn(),replaceIntegrationProfile:vi.fn(),deleteIntegrationProfile:vi.fn(),
 compileTechnologyIntegration:vi.fn(async()=>new Blob()),loadTechnologyDeliveryReadiness:vi.fn(async()=>({profileId:'profile',technology:'ECLIPSE_GLSP',profileValid:true,compilationReady:true,generatedFileCount:10,nativeVerificationStepCount:1,nativeVerificationAvailable:true,actions:[],blockers:[],warnings:[]})),validateTechnologyIntegration:vi.fn(async()=>({technology:'ECLIPSE_GLSP',status:'PASSED',workspaceDirectory:'tmp',steps:[],metadata:{}})),previewTechnologyIntegration:vi.fn(async()=>({plan:{status:'READY',fileChanges:[],diagnostics:[]},unchangedFiles:[],manualConflictFiles:[],generatedBytes:0})),applyTechnologyIntegration:vi.fn(async()=>({manifest:{id:'manifest-1'},writtenFiles:[],deletedFiles:[],restoredFiles:[]}))
}));
describe('IntegrationDesigner',()=>{
 beforeEach(()=>vi.clearAllMocks());
 it('offers BESSER, Eclipse GLSP and PlantUML without changing the metamodel palette',async()=>{render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);fireEvent.click(await screen.findByRole('button',{name:'Definition'}));const select=await screen.findByLabelText('Integration technology');expect(select).toHaveValue('ECLIPSE_GLSP');fireEvent.change(select,{target:{value:'PLANTUML'}});expect(select).toHaveValue('PLANTUML');expect(screen.getByText(/Projection only/)).toBeInTheDocument();expect(screen.getByLabelText('Build system')).toHaveValue('NONE');});
 it('creates mappings using the locator strategies of the selected target',async()=>{render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);fireEvent.click(await screen.findByRole('button',{name:'Definition'}));await screen.findByLabelText('Integration technology');fireEvent.click(screen.getByRole('button',{name:'+ Mapping'}));await waitFor(()=>expect(screen.getByLabelText('Locator strategy 1')).toHaveValue('GLSP_DIAGRAM_TYPE'));});
 it('separates destination technologies from the host integration methods they support',async()=>{
   render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);
   fireEvent.click(await screen.findByRole('button',{name:'Next step'}));
   const technology=await screen.findByLabelText('Guided integration technology');
   expect(screen.getByRole('option',{name:'mxGraph / draw.io'})).toBeInTheDocument();
   fireEvent.change(technology,{target:{value:'SIRIUS_DESKTOP'}});
   expect(await screen.findByRole('radiogroup',{name:'Available integration methods'})).toBeInTheDocument();
   expect(screen.getByRole('radio',{name:/Add Plugin/})).toBeChecked();
   expect(screen.getByRole('radio',{name:/Add Module/})).toBeInTheDocument();
   expect(screen.getByRole('radio',{name:/Generate Sidecar/})).toBeInTheDocument();
   expect(screen.getByRole('radio',{name:/Patch Existing/})).toBeInTheDocument();
   fireEvent.change(technology,{target:{value:'MXGRAPH_DRAWIO'}});
   expect(screen.getByRole('radio',{name:/Add Package/})).toBeChecked();
   expect(screen.queryByRole('radio',{name:/Add Plugin/})).not.toBeInTheDocument();
 });


 it('keeps a portable-runtime preset applied when shell callback identities change',async()=>{
   const view=render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);
   fireEvent.click(await screen.findByRole('button',{name:'Next step'}));
   fireEvent.click(screen.getByRole('button',{name:'Next step'}));
   fireEvent.click(await screen.findByRole('button',{name:'Apply portable runtime preset'}));
   expect(screen.getByText('6 capabilities')).toBeInTheDocument();
   view.rerender(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);
   expect(screen.getByText('6 capabilities')).toBeInTheDocument();
 });

 it('requires a persisted metamodel',()=>{render(<IntegrationDesigner metamodelId="people" persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);expect(screen.getByText(/Save the metamodel/)).toBeInTheDocument();});
 it('uses a profile sidebar and exposes stable profile identifiers with help',async()=>{render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);expect(await screen.findByRole('button',{name:'Integration guide'})).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'Definition'}));expect(screen.getByLabelText('Profile ID')).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'Information about Profile ID'}));expect(screen.getByRole('tooltip')).toHaveTextContent(/integration manifests/i);});
 it('opens the integration process guide',async()=>{render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);fireEvent.click(await screen.findByRole('button',{name:'Integration guide'}));expect(screen.getByRole('dialog')).toHaveTextContent('How to define a technology integration');});

 it('edits target properties from the integration structure tree',async()=>{render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);fireEvent.click(await screen.findByRole('button',{name:'Structure tree'}));fireEvent.click(screen.getByText('Target project and technology').closest('button')!);fireEvent.change(screen.getByLabelText('Host project'),{target:{value:'changed-host'}});fireEvent.click(screen.getByRole('button',{name:'Definition'}));expect(screen.getByLabelText('Host project')).toHaveValue('changed-host');});

 it('shows actual workflow maturity and blocks a declared-only technology from the guided delivery path',async()=>{
   render(<IntegrationDesigner metamodelId="people" persisted onError={vi.fn()} onMessage={vi.fn()} />);
   fireEvent.click(await screen.findByRole('button',{name:'Next step'}));
   const technology=await screen.findByLabelText('Guided integration technology');
   expect(screen.getByText('82%')).toBeInTheDocument();
   fireEvent.change(technology,{target:{value:'ANTLR'}});
   expect(await screen.findByText('28%')).toBeInTheDocument();
   expect(screen.getByText('Declared only')).toBeInTheDocument();
   expect(screen.getByText(/No executable TechnologyAdapter/)).toBeInTheDocument();
   expect(screen.getByRole('button',{name:'Apply recommended technology profile'})).toBeDisabled();
   fireEvent.click(screen.getByRole('button',{name:'Definition'}));
   expect(screen.queryByRole('button',{name:'Compile module'})).not.toBeInTheDocument();
 });

});
