package com.example.editor.integration.adapter.besser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDependencyAction;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterGenerationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterRuntimeLifecycleSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSupport;
import com.example.editor.integration.compiler.TechnologyAdapterTextualCustomizationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSelfContainedDependencySupport;
import com.example.editor.integration.compiler.TechnologyAdapterVisualFullStackSupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.core.MetaModel;
import tools.jackson.databind.json.JsonMapper;

/** Genera una extensión TypeScript aditiva para BESSER Web Modeling Editor. */
@Component
public class BesserWebModelingEditorTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.allOf(IntegrationArtifactKind.class);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de BesserWebModelingEditorTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public BesserWebModelingEditorTechnologyAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.BESSER_WEB_MODELING_EDITOR;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = mapper.readValue(request.require(IntegrationArtifactKind.METAMODEL).content(), MetaModel.class);
            EditorDefinitionModel editor = mapper.readValue(request.require(IntegrationArtifactKind.EDITOR_DEFINITION).content(), EditorDefinitionModel.class);
            String root = TechnologyAdapterSupport.generatedRoot(request, metamodel.id() + "-besser-wme");
            String packageName = TechnologyAdapterGenerationSupport.npmPackage(request.profile().target().settings().get("packageName"),
                    "@hydra-generated/" + TechnologyAdapterSupport.slug(metamodel.id()) + "-besser-wme");
            String diagramType = request.profile().target().settings().getOrDefault("diagramType", TechnologyAdapterSupport.slug(editor.id()));
            String wmeVersion = request.profile().target().settings().getOrDefault("wmeVersion", "1.0.0");
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            files.add(TechnologyAdapterSupport.text(root + "/package.json", "application/json", packageJson(packageName, wmeVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/tsconfig.json", "application/json", tsconfig(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/index.ts", "text/typescript", indexSource(request.find(IntegrationArtifactKind.GRAMMAR).isPresent(), TechnologyAdapterVisualFullStackSupport.available(request)), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-diagram-type.ts", "text/typescript", diagramTypeSource(diagramType, editor), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-palette.ts", "text/typescript", paletteSource(editor), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-model-codec.ts", "text/typescript", codecSource(metamodel.id()), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-semantic-runtime.ts", "text/typescript", TechnologyAdapterRuntimeLifecycleSupport.typescriptSemanticRuntimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-runtime.ts", "text/typescript", runtimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-validation.ts", "text/typescript", validationSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-quickfix.ts", "text/typescript", quickFixSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-events.ts", "text/typescript", eventSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-gamification.ts", "text/typescript", gamificationSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-backend-client.ts", "text/typescript", backendSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-runtime-activation.ts", "text/typescript",
                        TechnologyAdapterGenerationSupport.typescriptRuntimeActivationSource(request), sourceIds));
            if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){
                files.add(TechnologyAdapterSupport.text(root + "/src/hydra-textual-full-stack.ts", "text/typescript",
                        TechnologyAdapterTextualCustomizationSupport.typescriptFullStackBridgeSource(request, mapper), sourceIds));
                files.addAll(TechnologyAdapterTextualCustomizationSupport.fullStackNeutralFiles(root + "/resources", request, mapper));
            }
            if(TechnologyAdapterVisualFullStackSupport.available(request)){
                files.add(TechnologyAdapterSupport.text(root + "/src/hydra-visual-full-stack.ts", "text/typescript",
                        TechnologyAdapterVisualFullStackSupport.typescriptBridgeSource(request), sourceIds));
                files.addAll(TechnologyAdapterVisualFullStackSupport.neutralFiles(root + "/resources", request, mapper));
            }
            files.add(TechnologyAdapterSupport.text(root + "/tests/extension.test.ts", "text/typescript", testSource(diagramType, TechnologyAdapterVisualFullStackSupport.available(request)), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown", readme(request, metamodel, editor, packageName, diagramType), sourceIds));
            files.addAll(TechnologyAdapterSelfContainedDependencySupport.npmVendorFiles(root, packageName, List.of("@besser/wme@" + wmeVersion, "react@19.2.0"), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>();
            for(RepresentationDefinition representation : editor.representations()){
                if(representation.kind() != RepresentationKind.DIAGRAM){
                    diagnostics.add(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, "BESSER_WME_NON_DIAGRAM_VIEWPOINT",
                            representation.id(), "editor.representations", "The representation remains canonical; the generated WME extension materializes diagram viewpoints in this block."));
                }
            }
            List<IntegrationDependencyChange> dependencies = List.of(dependency("@besser/wme", wmeVersion), dependency("react", "19.2.0"));
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("install-besser-wme-extension", "Install BESSER WME extension dependencies", root,
                            List.of("npm", "install", "--ignore-scripts"), 0, true),
                    new IntegrationVerificationStep("test-besser-wme-extension", "Compile and test the BESSER WME extension", root,
                            List.of("npm", "run", "check"), 0, true));
            return new TechnologyCompilationResult(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR, files, dependencies,
                    List.of("Prepare the local dependency vendor once with node offline/prepare-vendor.mjs", "Install only missing host packages from the local vendor with node offline/install-from-vendor.mjs", "Install/register the generated WME package and run npm run check"),
                    List.of("Rollback by removing the generated WME package and registration", "Restore host manifests from the HYDRA backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "TypeScript/BESSER WME",
                            "moduleRoot", root, "packageName", packageName, "diagramType", diagramType, "wmeVersion", wmeVersion));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate BESSER Web Modeling Editor integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String coordinate, String version){return new IntegrationDependencyChange("npm", coordinate, version, "peer", IntegrationDependencyAction.ADD);}
    /** Descripción: Ejecuta la operación «packageJson» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; wmeVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String packageJson(String packageName, String wmeVersion){return """
            {"name":"%s","version":"1.0.0","type":"module","scripts":{"build":"tsc -b","test":"vitest run","check":"npm run build && npm test"},"peerDependencies":{"@besser/wme":"^%s","react":"^19.2.0"},"devDependencies":{"typescript":"6.0.2","vite":"8.3.5","vitest":"5.0.5"}}
            """.formatted(packageName, wmeVersion);}
    /** Descripción: Ejecuta la operación «tsconfig» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String tsconfig(){return """
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"lib","rootDir":"src","declaration":true,"skipLibCheck":true},"include":["src/**/*.ts"]}
            """;}
    /** Descripción: Ejecuta la operación «indexSource» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: textual: indica si el paquete incorpora una gramática textual. Salida: devuelve el índice del paquete sin referenciar módulos inexistentes. */ private String indexSource(boolean textual, boolean visual){return """
            export * from './hydra-diagram-type.js';
            export * from './hydra-palette.js';
            export * from './hydra-model-codec.js';
            export * from './hydra-validation.js';
            export * from './hydra-quickfix.js';
            export * from './hydra-events.js';
            export * from './hydra-gamification.js';
            export * from './hydra-runtime.js';
            export * from './hydra-semantic-runtime.js';
            %s
            %s
            export * from './hydra-runtime-activation.js';
            """.formatted(textual ? "export * from './hydra-textual-full-stack.js';" : "", visual ? "export * from './hydra-visual-full-stack.js';" : "");}
    /** Descripción: Ejecuta la operación «diagramTypeSource» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: diagramType: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String diagramTypeSource(String diagramType, EditorDefinitionModel editor){
        String mappings = editor.representations().stream().filter(item -> item.kind() == RepresentationKind.DIAGRAM)
                .flatMap(item -> item.nodeMappings().stream()).map(item -> "'" + item.representationName().replace("'", "") + "'")
                .distinct().reduce((a,b)->a+","+b).orElse("");
        return """
                export const HYDRA_BESSER_DIAGRAM_TYPE='%s';
                export const HYDRA_NODE_TYPES=[%s] as const;
                export interface HydraDiagramRegistration {type:string;label:string;nodeTypes:readonly string[];source:'HYDRA_META_MODEL_2_1';}
                export const hydraDiagramRegistration:HydraDiagramRegistration={type:HYDRA_BESSER_DIAGRAM_TYPE,label:'%s',nodeTypes:HYDRA_NODE_TYPES,source:'HYDRA_META_MODEL_2_1'};
                """.formatted(diagramType, mappings, editor.name().replace("'", ""));}
    /** Descripción: Ejecuta la operación «paletteSource» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String paletteSource(EditorDefinitionModel editor){
        String tools = editor.palette().tools().stream().map(tool -> "{id:'" + tool.id().replace("'", "") + "',label:'" + tool.label().replace("'", "") + "',kind:'" + tool.getClass().getSimpleName() + "'}")
                .reduce((a,b)->a+","+b).orElse("");
        return """
                export interface HydraPaletteTool {id:string;label:string;kind:string;}
                export const hydraPalette:readonly HydraPaletteTool[]=[%s];
                """.formatted(tools);}
    /** Descripción: Ejecuta la operación «codecSource» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String codecSource(String metamodelId){return """
            export interface HydraCanonicalModel {schemaVersion:string;id:string;metamodelId:string;rootElementIds:string[];elements:unknown[];relations:unknown[];annotations:Record<string,string>;}
            export function decodeHydraModel(value:unknown):HydraCanonicalModel{const model=value as HydraCanonicalModel;if(model.metamodelId!=='%s')throw new Error('Unexpected metamodel');return model;}
            export function encodeHydraModel(model:HydraCanonicalModel):string{return JSON.stringify(model,null,2);}
            """.formatted(metamodelId);}
    /** Descripción: Genera la fachada de validación WME conectada al runtime transaccional por sesión. Entradas: no requiere parámetros. Salida: devuelve código TypeScript ejecutable. */ private String validationSource(){return """
            import {HydraBesserWmeRuntime} from './hydra-runtime.js';
            import type {HydraDiagnostic} from './hydra-semantic-runtime.js';
            export type HydraProblem=HydraDiagnostic;
            export class HydraValidationService{constructor(private readonly runtime:HydraBesserWmeRuntime){}validate(model:unknown):readonly HydraProblem[]{return this.runtime.validate(model,'BESSER_WME_DRAFT_VALIDATE');}}
            """;}
    /** Descripción: Genera la fachada de quick fixes WME con rollback del runtime y del modelo lógico devuelto por el adapter. Entradas: no requiere parámetros. Salida: devuelve código TypeScript ejecutable. */ private String quickFixSource(){return """
            import {HydraBesserWmeRuntime} from './hydra-runtime.js';
            export class HydraQuickFixService{constructor(private readonly runtime:HydraBesserWmeRuntime){}preview(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):unknown{return this.runtime.previewQuickFix(model,diagnosticId,actionId,parameters);}apply(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):unknown{return this.runtime.applyQuickFix(model,diagnosticId,actionId,parameters).model;}}
            """;}
    /** Descripción: Genera la fachada de eventos WME que publica únicamente estados confirmados a través del lifecycle neutral. Entradas: no requiere parámetros. Salida: devuelve código TypeScript ejecutable. */ private String eventSource(){return """
            import {HydraBesserWmeRuntime} from './hydra-runtime.js';
            export class HydraWmeEventService{constructor(private readonly runtime:HydraBesserWmeRuntime){}publishAfterCommit(model:unknown,trigger='BESSER_WME_COMMIT'){return this.runtime.commit(model,trigger);}history(){return this.runtime.events();}}
            """;}
    /** Descripción: Genera la fachada de gamificación WME conectada al estado confirmado del lifecycle. Entradas: no requiere parámetros. Salida: devuelve código TypeScript ejecutable. */ private String gamificationSource(){return """
            import {HydraBesserWmeRuntime} from './hydra-runtime.js';
            import type {HydraGamificationState} from './hydra-semantic-runtime.js';
            export type HydraGamificationView=HydraGamificationState;
            export class HydraGamificationService{constructor(private readonly runtime:HydraBesserWmeRuntime){}state():HydraGamificationView{return this.runtime.gamification();}}
            """;}
    /** Descripción: Genera el runtime transaccional WME por sesión sobre el lifecycle TypeScript común. Entradas: no requiere parámetros. Salida: devuelve código TypeScript que valida borradores, confirma commits y restaura checkpoints. */ private String runtimeSource(){return """
            import {HydraSemanticRuntime,type HydraCommitResult,type HydraDiagnostic,type HydraEvent,type HydraGamificationState,type HydraRuntimeSnapshot,type HydraSemanticEngine} from './hydra-semantic-runtime.js';
            export interface HydraBesserWmeAdapter{
              validate?(model:unknown,trigger:string):HydraDiagnostic[];deriveEvents?(before:unknown,after:unknown,trigger:string,sequence:number):HydraEvent[];runEventFlow?(events:HydraEvent[],model:unknown):HydraEvent[];updateGamification?(previous:HydraGamificationState,events:HydraEvent[],flow:HydraEvent[]):HydraGamificationState;
              previewQuickFix?(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):unknown;applyQuickFix?(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):unknown;invoke?(model:unknown,operationId:string,parameters:Record<string,unknown>):unknown;
            }
            const encode=(model:unknown)=>JSON.stringify(model??null);
            const decode=(state:string):unknown=>{try{return JSON.parse(state);}catch{return state;}};
            const validateStructural=(model:unknown):HydraDiagnostic[]=>{const value=model as any;if(!value||typeof value!=='object'||!Array.isArray(value.elements))return[];const diagnostics:HydraDiagnostic[]=[],ids=new Set<string>();for(const element of value.elements){const id=String(element?.id??'');if(!id)continue;if(ids.has(id))diagnostics.push({id:`duplicate:${id}`,severity:'ERROR',message:`Duplicate element id ${id}.`,elementId:id,quickFixIds:[]});ids.add(id);}for(const relation of Array.isArray(value.relations)?value.relations:[]){const source=String(relation?.sourceElementId??''),target=String(relation?.targetElementId??'');if((source&&!ids.has(source))||(target&&!ids.has(target)))diagnostics.push({id:`broken:${String(relation?.id??'relation')}`,severity:'ERROR',message:'Relationship references an unknown element.',elementId:source,quickFixIds:[]});}return diagnostics;};
            const diffEvents=(before:unknown,after:unknown,trigger:string,sequence:number):HydraEvent[]=>{const left=before as any,right=after as any;if(!left||!right||!Array.isArray(left.elements)||!Array.isArray(right.elements))return[{sequence,type:'MODEL_CHANGED',elementId:String(right?.id??'model'),payload:{trigger}}];const events:HydraEvent[]=[];const diff=(kind:string,a:any[],b:any[])=>{const oldMap=new Map(a.map(item=>[String(item?.id??''),item])),newMap=new Map(b.map(item=>[String(item?.id??''),item]));for(const [id,value] of newMap)if(id&&!oldMap.has(id))events.push({sequence,type:`${kind}_CREATED`,elementId:id,payload:{trigger}});for(const [id] of oldMap)if(id&&!newMap.has(id))events.push({sequence,type:`${kind}_DELETED`,elementId:id,payload:{trigger}});for(const [id,value] of newMap)if(id&&oldMap.has(id)&&JSON.stringify(oldMap.get(id))!==JSON.stringify(value))events.push({sequence,type:`${kind}_UPDATED`,elementId:id,payload:{trigger}});};diff('ELEMENT',left.elements,right.elements);diff('RELATION',Array.isArray(left.relations)?left.relations:[],Array.isArray(right.relations)?right.relations:[]);return events.length?events:[{sequence,type:'MODEL_CHANGED',elementId:String(right?.id??'model'),payload:{trigger}}];};
            export class HydraBesserWmeRuntime{
              private readonly lifecycle:HydraSemanticRuntime;
              constructor(private readonly adapter:HydraBesserWmeAdapter={}){const engine:HydraSemanticEngine={validate:(state,trigger)=>adapter.validate?.(decode(state),trigger)??validateStructural(decode(state)),deriveEvents:(before,after,trigger,sequence)=>adapter.deriveEvents?.(decode(before),decode(after),trigger,sequence)??diffEvents(decode(before),decode(after),trigger,sequence),runEventFlow:(events,state)=>adapter.runEventFlow?.(events,decode(state))??[],updateGamification:(previous,events,flow)=>adapter.updateGamification?.(previous,events,flow)??previous};this.lifecycle=new HydraSemanticRuntime(engine);}
              validate(model:unknown,trigger='BESSER_WME_DRAFT_VALIDATE'){return this.lifecycle.validateHost(encode(model),trigger);}commit(model:unknown,trigger='BESSER_WME_COMMIT'):HydraCommitResult{return this.lifecycle.synchronize(encode(model),trigger);}checkpoint():HydraRuntimeSnapshot{return this.lifecycle.checkpoint();}restore(snapshot:HydraRuntimeSnapshot):void{this.lifecycle.restore(snapshot);}events(){return this.lifecycle.events();}gamification(){return this.lifecycle.gamification();}
              previewQuickFix(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):unknown{if(!this.adapter.previewQuickFix)throw new Error('No HYDRA quick-fix preview adapter is installed.');return this.adapter.previewQuickFix(model,diagnosticId,actionId,parameters);}
              applyQuickFix(model:unknown,diagnosticId:string,actionId:string,parameters:Record<string,unknown>):{model:unknown;commit:HydraCommitResult}{if(!this.adapter.applyQuickFix)throw new Error('No HYDRA quick-fix adapter is installed.');const before=this.lifecycle.checkpoint();try{const next=this.adapter.applyQuickFix(model,diagnosticId,actionId,parameters);return{model:next,commit:this.commit(next,'BESSER_WME_QUICK_FIX')};}catch(error){this.lifecycle.restore(before);throw error;}}
              invoke(model:unknown,operationId:string,parameters:Record<string,unknown>):{model:unknown;commit:HydraCommitResult}{if(!this.adapter.invoke)throw new Error('No HYDRA operation adapter is installed.');const before=this.lifecycle.checkpoint();try{const next=this.adapter.invoke(model,operationId,parameters);return{model:next,commit:this.commit(next,'BESSER_WME_OPERATION')};}catch(error){this.lifecycle.restore(before);throw error;}}
            }
            """;}
    /** Descripción: Ejecuta la operación «backendSource» correspondiente a la responsabilidad de BesserWebModelingEditorTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String backendSource(){return """
            export class HydraBesserBackendClient { constructor(readonly baseUrl='http://localhost:9000/besser_api'){} async convert(diagram:unknown):Promise<unknown>{const response=await fetch(`${this.baseUrl}/hydra/convert`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(diagram)});if(!response.ok)throw new Error(`BESSER backend ${response.status}`);return response.json();} }
            """;}
    /** Descripción: Genera las pruebas Vitest autocontenidas del paquete WME, incluyendo lifecycle transaccional, rollback y reactivación. Entradas: diagramType: tipo de diagrama estable; visual: indica si se prueba también la personalización visual. Salida: devuelve código TypeScript ejecutable por el proyecto generado. */ private String testSource(String diagramType, boolean visual){return """
            import {afterEach,describe,it,expect} from 'vitest';
            import {HYDRA_BESSER_DIAGRAM_TYPE} from '../src/hydra-diagram-type.js';
            import {HydraBesserWmeRuntime} from '../src/hydra-runtime.js';
            import {hydraRuntimeActivation} from '../src/hydra-runtime-activation.js';
            %s
            const baseline={id:'model',elements:[{id:'a',name:'A'}],relations:[]};
            const changed={id:'model',elements:[{id:'a',name:'B'}],relations:[]};
            const offChange={id:'model',elements:[{id:'a',name:'OFF'}],relations:[]};
            const engine=()=>({
              validate:()=>[],
              deriveEvents:(_before:unknown,_after:unknown,trigger:string,sequence:number)=>[{sequence,type:'ELEMENT_UPDATED',elementId:'a',payload:{trigger}}],
              runEventFlow:(events:any[])=>events.map(event=>({...event,type:'FLOW_'+event.type})),
              updateGamification:(previous:any)=>({...previous,points:{...previous.points,xp:(previous.points.xp??0)+5}})
            });
            afterEach(()=>hydraRuntimeActivation.setEnabled(false));
            describe('HYDRA BESSER WME extension',()=>{
              it('keeps a stable diagram type',()=>expect(HYDRA_BESSER_DIAGRAM_TYPE).toBe('%s'));
              it('keeps draft validation side-effect free and commits only confirmed states',()=>{
                hydraRuntimeActivation.setEnabled(true);
                const runtime=new HydraBesserWmeRuntime(engine());
                expect(runtime.commit(baseline).rebaselined).toBe(true);
                const before=runtime.checkpoint();
                expect(runtime.validate(changed)).toEqual([]);
                expect(runtime.checkpoint()).toEqual(before);
                const result=runtime.commit(changed);
                expect(result.changed).toBe(true);
                expect(result.confirmedEvents.map(event=>event.type)).toEqual(['ELEMENT_UPDATED']);
                expect(result.flowEvents.map(event=>event.type)).toEqual(['FLOW_ELEMENT_UPDATED']);
                expect(result.gamification.points.xp).toBe(5);
              });
              it('rolls back the complete runtime checkpoint when a commit capability fails',()=>{
                hydraRuntimeActivation.setEnabled(true);
                const runtime=new HydraBesserWmeRuntime({...engine(),updateGamification:()=>{throw new Error('boom');}});
                runtime.commit(baseline);
                const before=runtime.checkpoint();
                expect(()=>runtime.commit(changed)).toThrow('boom');
                expect(runtime.checkpoint()).toEqual(before);
              });
              it('does not replay changes made while HYDRA is disabled',()=>{
                hydraRuntimeActivation.setEnabled(true);
                const runtime=new HydraBesserWmeRuntime(engine());
                runtime.commit(baseline); runtime.commit(changed);
                const eventCount=runtime.events().length;
                hydraRuntimeActivation.setEnabled(false);
                expect(runtime.commit(offChange).rebaselined).toBe(true);
                hydraRuntimeActivation.setEnabled(true);
                const resumed=runtime.commit(offChange);
                expect(resumed.rebaselined).toBe(true);
                expect(resumed.confirmedEvents).toEqual([]);
                expect(runtime.events().length).toBe(eventCount);
              });
              %s
            });
            """.formatted(
                    visual ? "import {createHydraVisualFullStack} from '../src/hydra-visual-full-stack.js';" : "",
                    diagramType,
                    visual ? "it('executes HYDRA visual customization inside the WME host bridge',()=>{const applied:any[]=[];const runtime=createHydraVisualFullStack({evaluateExpression:(id)=>id==='expr:vip',applyNodeAppearance:(element,mapping,appearance)=>applied.push({element,mapping,appearance})});const appearance=runtime.resolveNodeAppearance({id:'node:test',shape:'RECTANGLE',styles:{fillColor:'#fff'},conditionalStyles:[{id:'style:vip',enabled:true,priority:10,conditionExpressionId:'expr:vip',styles:{fillColor:'#fc0',shape:'ELLIPSE'}}]},{id:'person-1'});expect(appearance.styles.fillColor).toBe('#fc0');expect(appearance.shape).toBe('ELLIPSE');});" : "");}
    /** Descripción: Obtiene la información correspondiente a «readme» dentro de BesserWebModelingEditorTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación; diagramType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TechnologyCompilationRequest request, MetaModel metamodel, EditorDefinitionModel editor, String packageName, String diagramType){return """
            # HYDRA BESSER Web Modeling Editor integration

            Generated from canonical `MetaModel 2.1` `%s` and editor definition `%s`.

            - npm package: `%s`.
            - Diagram type: `%s`.
            - The extension targets the reusable `@besser/wme` editor package and keeps backend conversion behind an explicit client.
            - Palette, model codec, validation, quick fixes, confirmed events, EventFlow and gamification share one transactional TypeScript runtime per WME session.
            - When a grammar is selected, the package also consumes the neutral textual appearance/hover contract and coordinates validation, quick fixes, events, EventFlow and gamification.
            - `hydraRuntimeActivation` and `runtime-activation.json` keep runtime behavior scoped to the semantic subject and use activation epochs for silent OFF/ON rebaseline.
            - The generated Vitest suite verifies draft/commit separation, rollback and OFF/ON rebaseline inside the exported extension.
            - Canonical JSON is embedded for provenance and round-trip support.
            - BESSER diagram JSON and Redux state remain technology projections; HYDRA stays the semantic source of truth.
            - Injection profile: `%s`.
            """.formatted(metamodel.id(), editor.id(), packageName, diagramType, request.profile().id());}
}
