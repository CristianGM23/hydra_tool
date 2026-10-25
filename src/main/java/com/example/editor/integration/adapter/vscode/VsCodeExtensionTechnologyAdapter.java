package com.example.editor.integration.adapter.vscode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationDependencyAction;
import com.example.editor.integration.IntegrationDependencyChange;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterGenerationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterRuntimeLifecycleSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSupport;
import com.example.editor.integration.compiler.TechnologyAdapterTextualCustomizationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterVisualFullStackSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSelfContainedDependencySupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.core.MetaModel;

import tools.jackson.databind.json.JsonMapper;

/** Generates a metamodel-scoped VS Code extension hosting HYDRA portable runtime layers. */
@Component
public class VsCodeExtensionTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.allOf(IntegrationArtifactKind.class);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de VsCodeExtensionTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public VsCodeExtensionTechnologyAdapter(JsonMapper mapper) { this.mapper = mapper; }

    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology() { return IntegrationTechnology.VSCODE_EXTENSION; }
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds() { return Set.copyOf(SUPPORTED); }
    /** VS Code puede generarse desde un metamodelo o directamente desde una gramática autónoma. */ /** Descripción: Ejecuta la operación «semanticRootKinds» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> semanticRootKinds() { return Set.of(IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.GRAMMAR); }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request) {
        try {
            MetaModel metamodel = request.find(IntegrationArtifactKind.METAMODEL)
                    .map(artifact -> mapper.readValue(artifact.content(), MetaModel.class)).orElse(null);
            var grammar = TechnologyAdapterTextualCustomizationSupport.grammar(request, mapper);
            if(metamodel == null && grammar == null) throw new TechnologyCompilationException("VS Code requires a metamodel or grammar semantic root");
            String semanticScopeId = request.profile().semanticScopeId();
            if(semanticScopeId == null || semanticScopeId.isBlank()) semanticScopeId = metamodel != null ? metamodel.id() : "grammar." + grammar.id();
            String slug = TechnologyAdapterSupport.slug(semanticScopeId);
            String root = TechnologyAdapterSupport.generatedRoot(request, slug + "-vscode");
            String packageName = TechnologyAdapterGenerationSupport.npmPackage(
                    request.profile().target().settings().get("packageName"), "hydra-" + slug + "-modeling");
            String commandPrefix = TechnologyAdapterSupport.slug(request.profile().target().settings().getOrDefault("commandPrefix", "hydra." + slug));
            String modelExtension = safeExtension(request.profile().target().settings().getOrDefault("modelExtension", "hydra"));
            String glspVersion = request.profile().target().settings().getOrDefault("glspVersion", "2.7.0");
            String langiumVersion = request.profile().target().settings().getOrDefault("langiumVersion", "4.3.0");
            String vscodeEngine = request.profile().target().settings().getOrDefault("vscodeEngine", "^1.103.0");
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            var textualCustomization = TechnologyAdapterTextualCustomizationSupport.customization(request, mapper);
            boolean visualFullStack = TechnologyAdapterVisualFullStackSupport.available(request);
            String textualContribution = grammar == null ? "" : vscodeTextualContribution(TechnologyAdapterSupport.slug(grammar.id()), modelExtension, textualCustomization);

            files.add(TechnologyAdapterSupport.text(root + "/package.json", "application/json",
                    packageJson(packageName, commandPrefix, modelExtension, vscodeEngine, glspVersion, langiumVersion, textualContribution), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/tsconfig.json", "application/json", tsconfig(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/esbuild.mjs", "text/javascript", esbuildConfig(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/.vscodeignore", "text/plain", vscodeIgnore(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-runtime-activation.ts", "text/typescript",
                    TechnologyAdapterGenerationSupport.typescriptRuntimeActivationSource(request), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/hydra-semantic-runtime.ts", "text/typescript",
                    TechnologyAdapterRuntimeLifecycleSupport.typescriptSemanticRuntimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/runtimeActivation.ts", "text/typescript",
                    persistentRuntimeActivation(request.profile().semanticScopeId()), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/layerRegistry.ts", "text/typescript",
                    layerRegistry(request), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/portableRuntimeBridge.ts", "text/typescript",
                    portableRuntimeBridge(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/runtimeSessions.ts", "text/typescript",
                    runtimeSessions(modelExtension), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/languageBridge.ts", "text/typescript",
                    languageBridge(commandPrefix), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/modelEditorProvider.ts", "text/typescript",
                    modelEditorProvider(commandPrefix, visualFullStack), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/extension.ts", "text/typescript",
                    extensionSource(commandPrefix, request.profile().semanticScopeId(), grammar != null, visualFullStack), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/tests/runtime.test.ts", "text/typescript",
                    runtimeTestSource(), sourceIds));
            if(grammar != null){files.add(TechnologyAdapterSupport.text(root + "/src/hydraTextualCustomization.ts", "text/typescript", TechnologyAdapterTextualCustomizationSupport.vscodeRuntimeSource(textualCustomization, TechnologyAdapterSupport.slug(grammar.id())), sourceIds));files.addAll(TechnologyAdapterTextualCustomizationSupport.neutralFiles(root, request, mapper));}
            if(visualFullStack){files.add(TechnologyAdapterSupport.text(root + "/src/hydraVisualFullStack.ts", "text/typescript", TechnologyAdapterVisualFullStackSupport.typescriptBridgeSource(request), sourceIds));files.addAll(TechnologyAdapterVisualFullStackSupport.neutralFiles(root + "/resources", request, mapper));}
            files.add(TechnologyAdapterSupport.text(root + "/resources/hydra-host-bridges.json", "application/json",
                    bridgeDescriptor(request.profile().metamodelId(), request.profile().semanticScopeId(), commandPrefix), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown",
                    readme(semanticScopeId, commandPrefix, modelExtension), sourceIds));

            List<IntegrationDependencyChange> dependencies = List.of(
                    dependency("npm", "vscode-languageclient", "9.0.1", "runtime"),
                    dependency("npm", "@eclipse-glsp/vscode-integration", glspVersion, "runtime"),
                    dependency("npm", "langium", langiumVersion, "runtime"),
                    dependency("npm", "@types/vscode", vscodeEngine.replace("^", ""), "development"),
                    dependency("npm", "typescript", "5.9.2", "development"),
                    dependency("npm", "vitest", "4.1.10", "development"),
                    dependency("npm", "@vscode/vsce", "3.6.0", "development"),
                    dependency("npm", "esbuild", "0.25.9", "development"));
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("install-vscode-extension-dependencies", "Install deterministic VS Code extension dependencies", root,
                            List.of("npm", "install", "--ignore-scripts"), 0, true),
                    new IntegrationVerificationStep("typecheck-vscode-extension", "Compile and type-check the generated VS Code extension", root,
                            List.of("npm", "run", "check"), 0, true),
                    new IntegrationVerificationStep("test-vscode-runtime", "Execute the generated transactional runtime lifecycle suite", root,
                            List.of("npm", "test"), 0, true),
                    new IntegrationVerificationStep("package-vscode-extension", "Package an installable VSIX", root,
                            List.of("npm", "run", "package"), 0, true));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(
                    TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "VS Code extension"));
            return new TechnologyCompilationResult(technology(), files, dependencies,
                    List.of("Run npm ci once while building", "Bundle runtime dependencies into dist/extension.js", "Package the self-contained VSIX with npm run package",
                            "Install the generated VSIX in VS Code without npm dependencies", "Use the HYDRA status-bar item or command palette to enable the runtime for this metamodel"),
                    List.of("Disable the HYDRA runtime from the command palette", "Uninstall the generated VS Code extension"),
                    verification, diagnostics,
                    Map.of("generatorVersion", request.generatorVersion(), "moduleRoot", root,
                            "packageName", packageName, "commandPrefix", commandPrefix,
                            "runtimeActivation", "WORKSPACE_PERSISTENT_TOGGLEABLE",
                            "hostBridges", visualFullStack ? "LANGIUM,GLSP,XTEXT_LSP,HYDRA_CANONICAL,HYDRA_VISUAL_FULL_STACK" : "LANGIUM,GLSP,XTEXT_LSP,HYDRA_CANONICAL"));
        } catch (RuntimeException exception) {
            if (exception instanceof TechnologyCompilationException) throw exception;
            throw new TechnologyCompilationException("Could not generate VS Code extension", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: ecosystem: dato de entrada necesario para la operación; coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String ecosystem, String coordinate, String version, String scope) {
        return new IntegrationDependencyChange(ecosystem, coordinate, version, scope, IntegrationDependencyAction.ADD);
    }

    /** Descripción: Ejecuta la operación «packageJson» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; commandPrefix: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación; engine: dato de entrada necesario para la operación; glspVersion: dato de entrada necesario para la operación; langiumVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String packageJson(String packageName, String commandPrefix, String extension, String engine, String glspVersion, String langiumVersion, String textualContribution) {
        String escapedName = json(packageName);
        return """
                {
                  "name": %s,
                  "displayName": "HYDRA Modeling — %s",
                  "description": "Portable HYDRA runtime and model-editor host for VS Code.",
                  "version": "1.0.0",
                  "publisher": "hydra-generated",
                  "engines": { "vscode": %s },
                  "categories": ["Programming Languages", "Visualization", "Other"],
                  "activationEvents": [
                    "onCommand:%s.runtime.toggle",
                    "onCommand:%s.runtime.enable",
                    "onCommand:%s.runtime.disable",
                    "onCommand:%s.runtime.status",
                    "onCustomEditor:%s.modelEditor",
                    "workspaceContains:**/*.%s"
                  ],
                  "main": "./dist/extension.js",
                  "scripts": {
                    "compile": "tsc -p .",
                    "check": "tsc -p . --noEmit --pretty false",
                    "test": "vitest run",
                    "bundle": "node esbuild.mjs --production",
                    "vscode:prepublish": "npm run check && npm test && npm run bundle",
                    "package": "npm run vscode:prepublish && vsce package --no-dependencies"
                  },
                  "dependencies": {
                    "@eclipse-glsp/vscode-integration": %s,
                    "langium": %s,
                    "vscode-languageclient": "9.0.1"
                  },
                  "devDependencies": {
                    "@types/node": "22.15.0",
                    "@types/vscode": %s,
                    "@vscode/vsce": "3.6.0",
                    "esbuild": "0.25.9",
                    "typescript": "5.9.2",
                    "vitest": "4.1.10"
                  },
                  %s,
                  "contributes": {
                    "commands": [
                      {"command": "%s.runtime.toggle", "title": "HYDRA: Toggle runtime"},
                      {"command": "%s.runtime.enable", "title": "HYDRA: Enable runtime"},
                      {"command": "%s.runtime.disable", "title": "HYDRA: Disable runtime"},
                      {"command": "%s.runtime.status", "title": "HYDRA: Show runtime status"}
                    ],
                    "menus": {
                      "commandPalette": [
                        {"command": "%s.runtime.toggle"},
                        {"command": "%s.runtime.enable"},
                        {"command": "%s.runtime.disable"},
                        {"command": "%s.runtime.status"}
                      ],
                      "editor/title": [{"command": "%s.runtime.toggle", "group": "navigation@90"}]
                    },
                    "customEditors": [{
                      "viewType": "%s.modelEditor",
                      "displayName": "HYDRA Model Editor",
                      "selector": [{"filenamePattern": "*.%s"}],
                      "priority": "option"
                    }],
                    %s
                    "configuration": {
                      "title": "HYDRA Modeling",
                      "properties": {
                        "%s.xtextLanguageServer.command": {"type": "string", "default": "", "description": "Optional Xtext language-server command. Empty keeps the bridge disabled."},
                        "%s.xtextLanguageServer.args": {"type": "array", "items": {"type": "string"}, "default": [], "description": "Arguments passed to the Xtext language server."},
                        "%s.glsp.serverUrl": {"type": "string", "default": "", "description": "Optional external GLSP server endpoint."},
                        "%s.langium.embedded": {"type": "boolean", "default": true, "description": "Allow an embedded Langium runtime bridge when the generated language package is present."}
                      }
                    }
                  },
                  "hydra": {"glspIntegrationVersion": %s, "runtimeActivation": "workspace-persistent"}
                }
                """.formatted(escapedName, escapeJsonText(packageName), json(engine),
                        commandPrefix, commandPrefix, commandPrefix, commandPrefix, commandPrefix, extension,
                        json(glspVersion), json(langiumVersion), json(engine.replace("^", "")), TechnologyAdapterSupport.VITEST_VITE_OVERRIDE,
                        commandPrefix, commandPrefix, commandPrefix, commandPrefix,
                        commandPrefix, commandPrefix, commandPrefix, commandPrefix, commandPrefix,
                        commandPrefix, extension, textualContribution, commandPrefix, commandPrefix, commandPrefix, commandPrefix, json(glspVersion));
    }

    /** Descripción: Ejecuta la operación «vscodeTextualContribution» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: languageId: identificador que delimita el recurso objetivo; extension: dato de entrada necesario para la operación; c: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String vscodeTextualContribution(String languageId,String extension,com.example.editor.grammar.core.GrammarTextualEditorCustomization c){return """
                    "languages":[{"id":%s,"aliases":["HYDRA textual language"],"extensions":[".%s"]}],
                    "configurationDefaults":{"[%s]":{"editor.fontFamily":%s,"editor.fontSize":%d,"editor.lineHeight":%d}},
                    """.formatted(json(languageId),extension,languageId,json(c.fontFamily()),c.fontSize(),c.lineHeight());}

    /** Descripción: Ejecuta la operación «tsconfig» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String tsconfig() {
        return """
                {
                  "compilerOptions": {
                    "target": "ES2022", "module": "commonjs", "lib": ["ES2022", "DOM"],
                    "outDir": "out", "rootDir": "src", "strict": true, "sourceMap": true,
                    "esModuleInterop": true, "skipLibCheck": true, "moduleResolution": "node"
                  },
                  "include": ["src/**/*.ts"]
                }
                """;
    }


    /** Descripción: Ejecuta la operación «esbuildConfig» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String esbuildConfig() { return """
            import * as esbuild from 'esbuild';
            const production=process.argv.includes('--production');
            await esbuild.build({entryPoints:['src/extension.ts'],bundle:true,platform:'node',format:'cjs',target:'node20',outfile:'dist/extension.js',external:['vscode'],minify:production,sourcemap:!production,treeShaking:true,logLevel:'info'});
            """; }

    /** Descripción: Ejecuta la operación «vscodeIgnore» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String vscodeIgnore() { return "src/**\ntests/**\nout/**\ntsconfig.json\nesbuild.mjs\n**/*.map\nnode_modules/**\n"; }

    /** Descripción: Genera el adaptador persistente de activación de VS Code sobre el gate neutral con época. Entradas: semanticScopeId: identificador semántico del paquete generado. Salida: devuelve el código TypeScript que persiste el estado por workspace sin saltarse el lifecycle neutral. */ private String persistentRuntimeActivation(String semanticScopeId) {
        return """
                import * as vscode from 'vscode';
                import {HYDRA_SEMANTIC_SCOPE_ID,hydraRuntimeActivation} from './hydra-runtime-activation.js';
                const key=`hydra.runtime.${HYDRA_SEMANTIC_SCOPE_ID}.enabled`;
                export class HydraRuntimeActivation {
                  constructor(private readonly context:vscode.ExtensionContext){hydraRuntimeActivation.setEnabled(this.context.workspaceState.get<boolean>(key,false));}
                  isEnabled():boolean{return hydraRuntimeActivation.isEnabled();}
                  isEnabledFor(semanticScopeId:string):boolean{return hydraRuntimeActivation.isEnabledFor(semanticScopeId);}
                  epoch():number{return hydraRuntimeActivation.epoch();}
                  async setEnabled(value:boolean):Promise<boolean>{await this.context.workspaceState.update(key,value);return hydraRuntimeActivation.setEnabled(value);}
                  async toggle():Promise<boolean>{return this.setEnabled(!this.isEnabled());}
                }
                export const HYDRA_VSCODE_SEMANTIC_SCOPE_ID=%s;
                """.formatted(json(semanticScopeId));
    }

    /** Descripción: Ejecuta la operación «layerRegistry» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String layerRegistry(TechnologyCompilationRequest request) {
        String entries = request.artifacts().stream().sorted((a,b) -> a.kind().name().compareTo(b.kind().name()))
                .map(a -> "  " + a.kind().name() + ": 'resources/hydra/" + a.kind().name().toLowerCase() + ".json'")
                .reduce((a,b) -> a + ",\n" + b).orElse("");
        return """
                export const HYDRA_LAYERS={
                %s
                } as const;
                export type HydraLayerKind=keyof typeof HYDRA_LAYERS;
                export const hydraLayerPath=(kind:HydraLayerKind)=>HYDRA_LAYERS[kind];
                """.formatted(entries);
    }

    /** Descripción: Genera la fachada ejecutable del runtime portable de VS Code sobre el lifecycle neutral. Entradas: no requiere parámetros. Salida: devuelve código TypeScript con validación provisional, commits atómicos y checkpoints restaurables. */ private String portableRuntimeBridge() {
        return """
                import {HydraSemanticRuntime,type HydraCommitResult,type HydraDiagnostic,type HydraEvent,type HydraGamificationState,type HydraRuntimeSnapshot,type HydraSemanticEngine} from './hydra-semantic-runtime.js';
                export type {HydraCommitResult,HydraDiagnostic,HydraEvent,HydraGamificationState,HydraRuntimeSnapshot};
                export interface HydraPortableRuntimeAdapter {
                  validate?(state:string,trigger:string):HydraDiagnostic[];
                  deriveEvents?(beforeState:string,afterState:string,trigger:string,sequence:number):HydraEvent[];
                  runEventFlow?(events:HydraEvent[],state:string):HydraEvent[];
                  updateGamification?(previous:HydraGamificationState,events:HydraEvent[],flowEvents:HydraEvent[]):HydraGamificationState;
                  applyQuickFix?(state:string,diagnosticId:string,actionId:string,parameters:Readonly<Record<string,unknown>>):string;
                  invoke?(state:string,operationId:string,parameters:Readonly<Record<string,unknown>>):string;
                }
                const parseCanonical=(state:string):any|undefined=>{const text=state.trim();if(!text.startsWith('{'))return undefined;try{return JSON.parse(text);}catch{return undefined;}};
                const neutralValidate=(state:string):HydraDiagnostic[]=>{const text=state.trim();if(!text.startsWith('{'))return[];let model:any;try{model=JSON.parse(text);}catch{return[{id:'json-invalid',severity:'ERROR',message:'Model JSON is not valid.',elementId:'',quickFixIds:[]}];}const problems:HydraDiagnostic[]=[];const elements=Array.isArray(model?.elements)?model.elements:[];const ids=new Set<string>();for(const element of elements){const id=String(element?.id??'');if(!id)continue;if(ids.has(id))problems.push({id:`duplicate:${id}`,severity:'ERROR',message:`Duplicate element id ${id}.`,elementId:id,quickFixIds:[]});ids.add(id);}for(const relation of Array.isArray(model?.relations)?model.relations:[]){const source=String(relation?.sourceElementId??''),target=String(relation?.targetElementId??'');if((source&&!ids.has(source))||(target&&!ids.has(target)))problems.push({id:`broken:${String(relation?.id??'relation')}`,severity:'ERROR',message:'Relationship references an unknown element.',elementId:source,quickFixIds:[]});}return problems;};
                const signature=(value:any)=>JSON.stringify(value??null);
                const neutralEvents=(beforeState:string,afterState:string,trigger:string,sequence:number):HydraEvent[]=>{const before=parseCanonical(beforeState),after=parseCanonical(afterState);if(!before||!after)return[{sequence,type:'MODEL_CHANGED',elementId:'model',payload:{trigger}}];const result:HydraEvent[]=[];const diff=(kind:string,left:any[],right:any[])=>{const a=new Map(left.map(item=>[String(item?.id??''),item])),b=new Map(right.map(item=>[String(item?.id??''),item]));for(const [id,value] of b)if(id&&!a.has(id))result.push({sequence,type:`${kind}_CREATED`,elementId:id,payload:{trigger}});for(const [id] of a)if(id&&!b.has(id))result.push({sequence,type:`${kind}_DELETED`,elementId:id,payload:{trigger}});for(const [id,value] of b)if(id&&a.has(id)&&signature(a.get(id))!==signature(value))result.push({sequence,type:`${kind}_UPDATED`,elementId:id,payload:{trigger}});};diff('ELEMENT',Array.isArray(before.elements)?before.elements:[],Array.isArray(after.elements)?after.elements:[]);diff('RELATION',Array.isArray(before.relations)?before.relations:[],Array.isArray(after.relations)?after.relations:[]);return result.length?result:[{sequence,type:'MODEL_CHANGED',elementId:String(after?.id??'model'),payload:{trigger}}];};
                const emptyGamification=():HydraGamificationState=>({points:{},level:'',achievements:[],penalties:0});
                export class HydraPortableRuntimeBridge {
                  private readonly runtime:HydraSemanticRuntime;
                  constructor(private readonly adapter:HydraPortableRuntimeAdapter={}){const engine:HydraSemanticEngine={validate:(state,trigger)=>adapter.validate?.(state,trigger)??neutralValidate(state),deriveEvents:(before,after,trigger,sequence)=>adapter.deriveEvents?.(before,after,trigger,sequence)??neutralEvents(before,after,trigger,sequence),runEventFlow:(events,state)=>adapter.runEventFlow?.(events,state)??[],updateGamification:(previous,events,flow)=>adapter.updateGamification?.(previous,events,flow)??previous??emptyGamification()};this.runtime=new HydraSemanticRuntime(engine);}
                  validateState(state:string,trigger='VSCODE_DRAFT_VALIDATE'):HydraDiagnostic[]{return this.runtime.validateHost(state,trigger);}
                  synchronizeState(state:string,trigger='VSCODE_SAVE'):HydraCommitResult{return this.runtime.synchronize(state,trigger);}
                  checkpoint():HydraRuntimeSnapshot{return this.runtime.checkpoint();}
                  restore(snapshot:HydraRuntimeSnapshot):void{this.runtime.restore(snapshot);}
                  events():HydraEvent[]{return this.runtime.events();}
                  gamification():HydraGamificationState{return this.runtime.gamification();}
                  applyQuickFix(state:string,diagnosticId:string,actionId:string,parameters:Readonly<Record<string,unknown>>):{state:string;commit:HydraCommitResult}{if(!this.adapter.applyQuickFix)throw new Error('No HYDRA quick-fix adapter is installed.');const before=this.runtime.checkpoint();try{const next=this.adapter.applyQuickFix(state,diagnosticId,actionId,parameters);return{state:next,commit:this.runtime.synchronize(next,'VSCODE_QUICK_FIX')};}catch(error){this.runtime.restore(before);throw error;}}
                  invoke(state:string,operationId:string,parameters:Readonly<Record<string,unknown>>):{state:string;commit:HydraCommitResult}{if(!this.adapter.invoke)throw new Error('No HYDRA operation adapter is installed.');const before=this.runtime.checkpoint();try{const next=this.adapter.invoke(state,operationId,parameters);return{state:next,commit:this.runtime.synchronize(next,'VSCODE_OPERATION')};}catch(error){this.runtime.restore(before);throw error;}}
                }
                """;
    }

    /** Descripción: Genera el gestor de sesiones semánticas de VS Code por documento. Entradas: extension: extensión de modelo que delimita los documentos HYDRA. Salida: devuelve código TypeScript que valida borradores y confirma únicamente los estados guardados. */ private String runtimeSessions(String extension){return """
                import * as vscode from 'vscode';
                import {hydraRuntimeActivation} from './hydra-runtime-activation.js';
                import {HydraPortableRuntimeBridge,type HydraDiagnostic,type HydraRuntimeSnapshot} from './portableRuntimeBridge.js';
                type Session={bridge:HydraPortableRuntimeBridge;snapshots:Map<string,HydraRuntimeSnapshot>;order:string[]};
                const severity=(value:string)=>value==='ERROR'?vscode.DiagnosticSeverity.Error:value==='WARNING'?vscode.DiagnosticSeverity.Warning:vscode.DiagnosticSeverity.Information;
                export class HydraDocumentRuntimeSessions implements vscode.Disposable{
                  private readonly sessions=new Map<string,Session>();private readonly diagnostics=vscode.languages.createDiagnosticCollection('hydra');private readonly suffix='.%s';
                  accepts(document:vscode.TextDocument):boolean{return document.uri.scheme==='file'&&document.uri.path.toLowerCase().endsWith(this.suffix.toLowerCase());}
                  open(document:vscode.TextDocument):void{if(!this.accepts(document))return;const session=this.session(document);session.bridge.synchronizeState(document.getText(),'VSCODE_OPEN_REBASELINE');this.remember(session,document.getText());this.publish(document,session.bridge.validateState(document.getText(),'VSCODE_OPEN_VALIDATE'));}
                  validate(document:vscode.TextDocument):void{if(!this.accepts(document))return;this.publish(document,this.session(document).bridge.validateState(document.getText(),'VSCODE_DRAFT_VALIDATE'));}
                  commit(document:vscode.TextDocument):void{if(!this.accepts(document))return;const session=this.session(document),state=document.getText(),known=session.snapshots.get(state);if(known&&known.activationEpoch===hydraRuntimeActivation.epoch())session.bridge.restore(known);else{session.bridge.synchronizeState(state,'VSCODE_SAVE');this.remember(session,state);}this.publish(document,session.bridge.validateState(state,'VSCODE_SAVE_VALIDATE'));}
                  close(document:vscode.TextDocument):void{const key=document.uri.toString();this.sessions.delete(key);this.diagnostics.delete(document.uri);}
                  rebaselineOpenDocuments():void{for(const document of vscode.workspace.textDocuments)if(this.accepts(document)){const session=this.session(document);session.bridge.synchronizeState(document.getText(),'VSCODE_ACTIVATION_REBASELINE');this.remember(session,document.getText());this.publish(document,session.bridge.validateState(document.getText(),'VSCODE_ACTIVATION_VALIDATE'));}}
                  events(document:vscode.TextDocument){return this.session(document).bridge.events();}gamification(document:vscode.TextDocument){return this.session(document).bridge.gamification();}
                  dispose():void{this.sessions.clear();this.diagnostics.clear();this.diagnostics.dispose();}
                  private session(document:vscode.TextDocument):Session{const key=document.uri.toString();let value=this.sessions.get(key);if(!value){value={bridge:new HydraPortableRuntimeBridge(),snapshots:new Map(),order:[]};this.sessions.set(key,value);}return value;}
                  private remember(session:Session,state:string):void{const snapshot=session.bridge.checkpoint();session.snapshots.set(state,snapshot);session.order=session.order.filter(item=>item!==state);session.order.push(state);while(session.order.length>32){const oldest=session.order.shift();if(oldest!==undefined)session.snapshots.delete(oldest);}}
                  private publish(document:vscode.TextDocument,problems:HydraDiagnostic[]):void{const end=document.lineCount?document.lineAt(Math.max(0,document.lineCount-1)).range.end:new vscode.Position(0,0);this.diagnostics.set(document.uri,problems.map(problem=>{const diagnostic=new vscode.Diagnostic(new vscode.Range(new vscode.Position(0,0),end),problem.message,severity(problem.severity));diagnostic.code=problem.id;diagnostic.source='HYDRA';return diagnostic;}));}
                }
                """.formatted(extension);}

    /** Descripción: Ejecuta la operación «languageBridge» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: commandPrefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String languageBridge(String commandPrefix) {
        return """
                import * as vscode from 'vscode';
                import {LanguageClient,LanguageClientOptions,ServerOptions} from 'vscode-languageclient/node';
                export class HydraLanguageBridge {
                  private client:LanguageClient|undefined;
                  async startXtext():Promise<boolean>{
                    if(this.client!==undefined)return true;
                    const config=vscode.workspace.getConfiguration('%s');
                    const command=config.get<string>('xtextLanguageServer.command','').trim();
                    if(command==='')return false;
                    const args=config.get<string[]>('xtextLanguageServer.args',[]);
                    const serverOptions:ServerOptions={command,args};
                    const clientOptions:LanguageClientOptions={documentSelector:[{scheme:'file'}],synchronize:{}};
                    this.client=new LanguageClient('%s.xtext','HYDRA Xtext Language Server',serverOptions,clientOptions);
                    await this.client.start(); return true;
                  }
                  async stop():Promise<void>{if(this.client!==undefined){await this.client.stop();this.client=undefined;}}
                }
                """.formatted(commandPrefix, commandPrefix);
    }

    /** Descripción: Genera el proveedor de editor de modelos de VS Code con fallback visual seguro. Entradas: commandPrefix: prefijo estable de comandos/configuración; visualFullStack: indica si debe integrar el runtime visual empaquetado. Salida: devuelve el código fuente TypeScript del proveedor generado. */ private String modelEditorProvider(String commandPrefix, boolean visualFullStack) {
        String visualImport = visualFullStack ? "import {createHydraVisualFullStack} from './hydraVisualFullStack';" : "";
        String visualBody = visualFullStack ? """
                    const stack=createHydraVisualFullStack({}, {limits:{maximumElements:2000}});const rep=stack.representation();const mappings=stack.nodeMappings(rep?.id);const elements=Array.isArray(model?.elements)?model.elements:[];
                    const cards=elements.slice(0,2000).map((element:any)=>{const mapping=mappings.find((item:any)=>item?.targetClassId===element?.classId);if(!mapping)return'';const appearance=stack.resolveNodeAppearance(mapping,element,{model,representationId:rep?.id});const styles=appearance.styles||{};const fill=safeCss(styles.fillColor,'#f8fafc'),stroke=safeCss(styles.strokeColor,'#64748b'),width=safeNumber(styles.strokeWidth,1,1,12),radius=safeNumber(styles.borderRadius,12,0,80),opacity=safeOpacity(styles.opacity);const label=escapeHtml(String(element?.attributes?.name??element?.attributes?.title??element?.id??''));const type=escapeHtml(String(element?.classId??''));return `<article style="border:${width}px solid ${stroke};background:${fill};border-radius:${radius}px;opacity:${opacity};padding:12px;min-width:140px;max-width:260px"><strong>${label}</strong><small style="display:block;opacity:.7">${type}</small></article>`;}).join('');
                    visual=`<section><h3>${escapeHtml(String(rep?.name||rep?.id||'Visual representation'))}</h3><div style="display:flex;flex-wrap:wrap;gap:12px">${cards||'<em>No mapped model elements.</em>'}</div>${elements.length>2000?'<p>Visual preview limited to 2000 elements; use the GLSP host for the complete model.</p>':''}</section>`;
                """ : "";
        return """
                import * as vscode from 'vscode';
                import {HydraRuntimeActivation} from './runtimeActivation';
                %s
                const escapeHtml=(value:string)=>value.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]??c));
                const safeCss=(value:unknown,fallback:string)=>{const text=String(value??'').trim();return /^(#[0-9a-fA-F]{3,8}|[a-zA-Z]{1,32}|rgba?\\([0-9., %%]+\\)|hsla?\\([0-9., %%]+\\))$/.test(text)?text:fallback;};
                const safeNumber=(value:unknown,fallback:number,min:number,max:number)=>{const n=Number(value);return Number.isFinite(n)?Math.max(min,Math.min(max,n)):fallback;};
                const safeOpacity=(value:unknown)=>{const n=Number(value);if(!Number.isFinite(n))return 1;return Math.max(.1,Math.min(1,n>1?n/100:n));};
                export class HydraModelEditorProvider implements vscode.CustomTextEditorProvider {
                  constructor(private readonly activation:HydraRuntimeActivation){}
                  async resolveCustomTextEditor(document:vscode.TextDocument,panel:vscode.WebviewPanel):Promise<void>{
                    panel.webview.options={enableScripts:false};
                    const render=()=>{const raw=document.getText();let visual='';try{const model=JSON.parse(raw);%s}catch{const escaped=escapeHtml(raw);visual=`<pre>${escaped}</pre>`;}panel.webview.html=`<!doctype html><html><body><h2>HYDRA Model Editor</h2><p>Runtime: <strong>${this.activation.isEnabled()?'enabled':'disabled'}</strong></p><p>Configure <code>%s.glsp.serverUrl</code> for the full GLSP editor. The local fallback is script-free and applies the packaged HYDRA base visual definition safely.</p>${visual}</body></html>`;};
                    render(); const subscription=vscode.workspace.onDidChangeTextDocument(e=>{if(e.document.uri.toString()===document.uri.toString())render();});panel.onDidDispose(()=>subscription.dispose());
                  }
                }
                """.formatted(visualImport, visualBody, commandPrefix);
    }

    /** Descripción: Genera el punto de entrada de VS Code y conecta el lifecycle por documento con activación, validación de borradores y commits al guardar. Entradas: commandPrefix: prefijo de comandos; semanticScopeId: ámbito semántico estable; textualCustomization: indica si registra personalización textual; visualFullStack: indica si incorpora runtime visual. Salida: devuelve el código TypeScript de activación. */ private String extensionSource(String commandPrefix, String semanticScopeId, boolean textualCustomization, boolean visualFullStack) {
        String customizationImport=textualCustomization?"import {registerHydraTextualCustomization} from './hydraTextualCustomization';":"";
        String customizationCall=textualCustomization?"registerHydraTextualCustomization(context);":"";
        return """
                import * as vscode from 'vscode';
                import {HydraRuntimeActivation} from './runtimeActivation';
                import {HydraLanguageBridge} from './languageBridge';
                import {HydraModelEditorProvider} from './modelEditorProvider';
                import {HydraDocumentRuntimeSessions} from './runtimeSessions';
                %s
                export async function activate(context:vscode.ExtensionContext):Promise<void>{
                  const activation=new HydraRuntimeActivation(context);const languages=new HydraLanguageBridge();const sessions=new HydraDocumentRuntimeSessions();
                  %s
                  const status=vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right,90);status.command='%s.runtime.toggle';
                  const refresh=()=>{status.text=activation.isEnabled()?'$(check) HYDRA':'$(circle-slash) HYDRA';status.tooltip=`HYDRA runtime for %s: ${activation.isEnabled()?'enabled':'disabled'}`;status.show();};
                  const syncRuntime=async(value:boolean)=>{await activation.setEnabled(value);sessions.rebaselineOpenDocuments();if(value){await languages.startXtext();}else{await languages.stop();}refresh();return value;};
                  context.subscriptions.push(status,sessions,
                    vscode.commands.registerCommand('%s.runtime.toggle',async()=>{await syncRuntime(!activation.isEnabled());}),
                    vscode.commands.registerCommand('%s.runtime.enable',async()=>{await syncRuntime(true);}),
                    vscode.commands.registerCommand('%s.runtime.disable',async()=>{await syncRuntime(false);}),
                    vscode.commands.registerCommand('%s.runtime.status',()=>vscode.window.showInformationMessage(`HYDRA runtime is ${activation.isEnabled()?'enabled':'disabled'} for semantic scope %s.`)),
                    vscode.window.registerCustomEditorProvider('%s.modelEditor',new HydraModelEditorProvider(activation),{webviewOptions:{retainContextWhenHidden:true}}),
                    vscode.workspace.onDidOpenTextDocument(document=>sessions.open(document)),
                    vscode.workspace.onDidChangeTextDocument(event=>sessions.validate(event.document)),
                    vscode.workspace.onDidSaveTextDocument(document=>sessions.commit(document)),
                    vscode.workspace.onDidCloseTextDocument(document=>sessions.close(document)),
                    {dispose:()=>{void languages.stop();}}
                  );
                  for(const document of vscode.workspace.textDocuments)sessions.open(document);
                  refresh();if(activation.isEnabled())await languages.startXtext();
                }
                export function deactivate():void{}
                """.formatted(customizationImport, customizationCall, commandPrefix, escapeTemplate(semanticScopeId), commandPrefix, commandPrefix, commandPrefix, commandPrefix,
                        escapeTemplate(semanticScopeId), commandPrefix);
    }

    /** Descripción: Genera el descriptor de bridges de VS Code con ámbito semántico explícito. Entradas: metamodelId: metamodelo opcional; semanticScopeId: ámbito semántico efectivo; commandPrefix: prefijo de comandos. Salida: devuelve el descriptor JSON. */ private String bridgeDescriptor(String metamodelId, String semanticScopeId, String commandPrefix) {
        return """
                {
                  "schemaVersion":"1.0",
                  "metamodelId":%s,
                  "semanticScopeId":%s,
                  "activationCommand":%s,
                  "bridges":[
                    {"id":"langium","mode":"embedded-or-sidecar","protocol":"LSP","optional":true},
                    {"id":"eclipse-glsp","mode":"embedded-client-or-remote-server","protocol":"GLSP","optional":true},
                    {"id":"xtext","mode":"external-language-server","protocol":"LSP","optional":true},
                    {"id":"hydra-canonical-runtime","mode":"embedded","protocol":"canonical-documents","optional":false}
                  ]
                }
                """.formatted(json(metamodelId), json(semanticScopeId), json(commandPrefix + ".runtime.toggle"));
    }

    /** Descripción: Genera la suite Vitest autocontenida del lifecycle portable de VS Code. Entradas: no requiere parámetros. Salida: devuelve pruebas ejecutables de draft/commit, rollback y reactivación sin replay. */ private String runtimeTestSource(){return """
                import {afterEach,describe,it,expect} from 'vitest';
                import {hydraRuntimeActivation} from '../src/hydra-runtime-activation.js';
                import {HydraPortableRuntimeBridge} from '../src/portableRuntimeBridge.js';
                const baseline=JSON.stringify({id:'model',elements:[{id:'a',name:'A'}],relations:[]});
                const changed=JSON.stringify({id:'model',elements:[{id:'a',name:'B'}],relations:[]});
                const offChange=JSON.stringify({id:'model',elements:[{id:'a',name:'OFF'}],relations:[]});
                const adapter=()=>({
                  validate:()=>[],
                  deriveEvents:(_before:string,_after:string,trigger:string,sequence:number)=>[{sequence,type:'ELEMENT_UPDATED',elementId:'a',payload:{trigger}}],
                  runEventFlow:(events:any[])=>events.map(event=>({...event,type:'FLOW_'+event.type})),
                  updateGamification:(previous:any)=>({...previous,points:{...previous.points,xp:(previous.points.xp??0)+5}})
                });
                afterEach(()=>hydraRuntimeActivation.setEnabled(false));
                describe('HYDRA VS Code portable runtime',()=>{
                  it('keeps draft validation side-effect free and commits only saved states',()=>{
                    hydraRuntimeActivation.setEnabled(true);const runtime=new HydraPortableRuntimeBridge(adapter());
                    expect(runtime.synchronizeState(baseline,'VSCODE_OPEN_REBASELINE').rebaselined).toBe(true);
                    const before=runtime.checkpoint();expect(runtime.validateState(changed,'VSCODE_DRAFT_VALIDATE')).toEqual([]);expect(runtime.checkpoint()).toEqual(before);
                    const result=runtime.synchronizeState(changed,'VSCODE_SAVE');expect(result.changed).toBe(true);expect(result.confirmedEvents.map(event=>event.type)).toEqual(['ELEMENT_UPDATED']);expect(result.flowEvents.map(event=>event.type)).toEqual(['FLOW_ELEMENT_UPDATED']);expect(result.gamification.points.xp).toBe(5);
                  });
                  it('restores the complete checkpoint after a failed confirmed capability',()=>{
                    hydraRuntimeActivation.setEnabled(true);const runtime=new HydraPortableRuntimeBridge({...adapter(),updateGamification:()=>{throw new Error('boom');}});runtime.synchronizeState(baseline,'OPEN');const before=runtime.checkpoint();expect(()=>runtime.synchronizeState(changed,'SAVE')).toThrow('boom');expect(runtime.checkpoint()).toEqual(before);
                  });
                  it('rebaselines after OFF changes without replaying events',()=>{
                    hydraRuntimeActivation.setEnabled(true);const runtime=new HydraPortableRuntimeBridge(adapter());runtime.synchronizeState(baseline,'OPEN');runtime.synchronizeState(changed,'SAVE');const count=runtime.events().length;hydraRuntimeActivation.setEnabled(false);expect(runtime.synchronizeState(offChange,'OFF').rebaselined).toBe(true);hydraRuntimeActivation.setEnabled(true);const resumed=runtime.synchronizeState(offChange,'ON');expect(resumed.rebaselined).toBe(true);expect(resumed.confirmedEvents).toEqual([]);expect(runtime.events().length).toBe(count);
                  });
                });
                """;}

    /** Descripción: Obtiene la información correspondiente a «readme» dentro de VsCodeExtensionTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; commandPrefix: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(String semanticScopeId, String commandPrefix, String extension) {
        return """
                # HYDRA VS Code extension

                This generated extension is scoped to semantic context `%s` and embeds the canonical HYDRA layers selected by the integration profile.

                ## Runtime activation
                The runtime is disabled by default and persisted per VS Code workspace. Use the status-bar **HYDRA** item or the commands `%s.runtime.enable`, `%s.runtime.disable` and `%s.runtime.toggle`.

                ## Modeling bridges
                - Langium: embedded/sidecar language runtime boundary.
                - Eclipse GLSP: graphical-client/server boundary; an external server URL can be configured.
                - Xtext: optional external language server launched from the configured command.
                - HYDRA canonical runtime: draft validation is side-effect free; only saved/confirmed states may emit Events, EventFlow and gamification, all scoped by the semantic activation gate.

                Files matching `*.%s` can be opened with the generated HYDRA custom editor. It provides a safe textual fallback when the GLSP endpoint is unavailable instead of blanking the editor.

                The generated Vitest suite protects draft/commit separation, rollback and OFF/ON rebaseline. Build with `npm install --ignore-scripts && npm run check && npm test`; package with `npm run package`.
                """.formatted(semanticScopeId, commandPrefix, commandPrefix, commandPrefix, extension);
    }

    /** Descripción: Ejecuta la operación «safeExtension» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeExtension(String value) {
        String result = TechnologyAdapterSupport.slug(value).replace(".", "").replace("-", "");
        return result.isBlank() ? "hydra" : result;
    }

    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String json(String value) { return "\"" + escapeJsonText(value) + "\""; }
    /** Descripción: Ejecuta la operación «escapeJsonText» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escapeJsonText(String value) {
        return String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    /** Descripción: Ejecuta la operación «escapeTemplate» correspondiente a la responsabilidad de VsCodeExtensionTechnologyAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escapeTemplate(String value) { return escapeJsonText(value).replace("`", "\\`").replace("${", "\\${"); }
}
