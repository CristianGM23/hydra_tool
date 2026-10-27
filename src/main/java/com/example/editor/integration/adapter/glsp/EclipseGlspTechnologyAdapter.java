package com.example.editor.integration.adapter.glsp;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
import com.example.editor.integration.compiler.TechnologyFidelityManifestSupport;
import com.example.editor.integration.compiler.TechnologyAdapterTextualCustomizationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterSelfContainedDependencySupport;
import com.example.editor.integration.compiler.TechnologyAdapterVisualFullStackSupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.core.MetaModel;
import tools.jackson.databind.json.JsonMapper;

/** Genera un editor Eclipse GLSP con cliente TypeScript y servidor Java o TypeScript. */
@Component
public class EclipseGlspTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.allOf(IntegrationArtifactKind.class);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de EclipseGlspTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EclipseGlspTechnologyAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.ECLIPSE_GLSP;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = mapper.readValue(request.require(IntegrationArtifactKind.METAMODEL).content(), MetaModel.class);
            EditorDefinitionModel editor = mapper.readValue(request.require(IntegrationArtifactKind.EDITOR_DEFINITION).content(), EditorDefinitionModel.class);
            String root = TechnologyAdapterSupport.generatedRoot(request, metamodel.id() + "-glsp");
            String packageName = TechnologyAdapterGenerationSupport.npmPackage(request.profile().target().settings().get("packageName"),
                    "@hydra-generated/" + TechnologyAdapterSupport.slug(metamodel.id()) + "-glsp");
            String diagramType = request.profile().target().settings().getOrDefault("diagramType", TechnologyAdapterSupport.slug(editor.id()));
            String serverLanguage = request.profile().target().settings().getOrDefault("serverLanguage", "java").trim().toLowerCase();
            String glspVersion = request.profile().target().settings().getOrDefault("glspVersion", "2.5.0");
            String typescriptVersion = request.profile().target().settings().getOrDefault("typescriptVersion", "6.0.2");
            if(!serverLanguage.equals("java") && !serverLanguage.equals("typescript")){
                throw new TechnologyCompilationException("GLSP serverLanguage must be java or typescript");
            }
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            files.add(TechnologyAdapterSupport.text(root + "/package.json", "application/json", workspacePackage(packageName, serverLanguage), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/hydra-glsp.json", "application/json", descriptor(metamodel, editor, diagramType, serverLanguage), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/package.json", "application/json", clientPackage(packageName, glspVersion, typescriptVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/tsconfig.json", "application/json", tsconfig(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/src/diagram-type.ts", "text/typescript", diagramTypeSource(diagramType), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-diagram-module.ts", "text/typescript", clientModuleSource(), sourceIds));
            boolean visualFullStack = TechnologyAdapterVisualFullStackSupport.available(request);
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-views.ts", "text/typescript", viewsSource(editor, visualFullStack), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-palette.ts", "text/typescript", paletteSource(editor), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-validation.ts", "text/typescript", clientValidationSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-event-bridge.ts", "text/typescript", clientEventSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-runtime-activation.ts", "text/typescript",
                        TechnologyAdapterGenerationSupport.typescriptRuntimeActivationSource(request), sourceIds));
            if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){
                files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-textual-full-stack.ts", "text/typescript",
                        TechnologyAdapterTextualCustomizationSupport.typescriptFullStackBridgeSource(request, mapper), sourceIds));
                files.addAll(TechnologyAdapterTextualCustomizationSupport.fullStackNeutralFiles(root + "/resources", request, mapper));
            }
            if(visualFullStack){
                files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-visual-full-stack.ts", "text/typescript",
                        TechnologyAdapterVisualFullStackSupport.typescriptBridgeSource(request), sourceIds));
                files.addAll(TechnologyAdapterVisualFullStackSupport.neutralFiles(root + "/resources", request, mapper));
            }
            files.add(TechnologyAdapterSupport.text(root + "/client/src/hydra-client.test.ts", "text/typescript", clientTestSource(diagramType, visualFullStack), sourceIds));
            if(serverLanguage.equals("java")){addJavaServer(files, root, metamodel, editor, diagramType, sourceIds, request, glspVersion);}
            else{addTypeScriptServer(files, root, diagramType, sourceIds, packageName, glspVersion, typescriptVersion, request, editor);}
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown", readme(request, metamodel, editor, diagramType, serverLanguage), sourceIds));
            files.addAll(TechnologyAdapterSelfContainedDependencySupport.npmVendorFiles(root + "/client", packageName + "-client", List.of("@eclipse-glsp/client@" + glspVersion, "@eclipse-glsp/protocol@" + glspVersion, "inversify@6.1.3"), sourceIds));
            if(serverLanguage.equals("typescript")) files.addAll(TechnologyAdapterSelfContainedDependencySupport.npmVendorFiles(root + "/server", packageName + "-server", List.of("@eclipse-glsp/server@" + glspVersion, "@eclipse-glsp/graph@" + glspVersion, "@eclipse-glsp/protocol@" + glspVersion, "inversify@6.1.3", "reflect-metadata@0.2.2"), sourceIds));
            else files.addAll(TechnologyAdapterSelfContainedDependencySupport.mavenRuntimeVendorFiles(root + "/server", packageName + "-server", List.of("org.eclipse.glsp:org.eclipse.glsp.server:" + glspVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "META-INF", "hydra-fidelity-obligations.json"),
                    "application/json", TechnologyFidelityManifestSupport.json(mapper, request, editor, "ECLIPSE_GLSP"), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>();
            for(RepresentationDefinition representation : editor.representations()){
                if(representation.kind() != RepresentationKind.DIAGRAM){
                    diagnostics.add(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.WARNING, "GLSP_NON_DIAGRAM_VIEWPOINT",
                            representation.id(), "editor.representations", "The representation remains canonical; GLSP generation materializes diagram viewpoints in this block."));
                }
            }
            List<IntegrationDependencyChange> dependencies = new ArrayList<>();
            dependencies.add(dependency("npm", "@eclipse-glsp/client", glspVersion));
            dependencies.add(dependency("npm", "@eclipse-glsp/protocol", glspVersion));
            if(serverLanguage.equals("java")){dependencies.add(dependency("maven", "org.eclipse.glsp:org.eclipse.glsp.server", glspVersion));dependencies.add(dependency("maven", "com.google.code.gson:gson", "2.11.0"));}
            else{dependencies.add(dependency("npm", "@eclipse-glsp/server", glspVersion));dependencies.add(dependency("npm", "@eclipse-glsp/graph", glspVersion));}
            List<IntegrationVerificationStep> verification = new ArrayList<>();
            verification.add(new IntegrationVerificationStep("install-glsp-client", "Install GLSP client dependencies", root + "/client", List.of("npm", "install", "--ignore-scripts"), 0, true));
            verification.add(new IntegrationVerificationStep("build-glsp-client", "Compile and test the GLSP TypeScript client", root + "/client", List.of("npm", "run", "check"), 0, true));
            if(serverLanguage.equals("java")){
                verification.add(new IntegrationVerificationStep("build-glsp-server", "Compile the GLSP Java server", root + "/server", List.of("mvn", "-q", "test"), 0, true));
            }else{
                verification.add(new IntegrationVerificationStep("install-glsp-server", "Install GLSP server dependencies", root + "/server", List.of("npm", "install", "--ignore-scripts"), 0, true));
                verification.add(new IntegrationVerificationStep("build-glsp-server", "Compile and test the GLSP TypeScript server", root + "/server", List.of("npm", "run", "check"), 0, true));
            }
            return new TechnologyCompilationResult(IntegrationTechnology.ECLIPSE_GLSP, files, dependencies,
                    List.of("Prepare client/server vendor bundles once", "Build the generated client and server", "Use only bundled npm packages or target/distribution/lib at deployment", "Embed the client in standalone, VS Code, Theia or Eclipse integration glue"),
                    List.of("Rollback by removing the generated GLSP package", "Restore host manifests and registrations from the HYDRA backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "TypeScript/" + (serverLanguage.equals("java") ? "Java" : "TypeScript"),
                            "moduleRoot", root, "packageName", packageName, "diagramType", diagramType, "serverLanguage", serverLanguage, "glspVersion", glspVersion));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate Eclipse GLSP integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: manager: dato de entrada necesario para la operación; coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String manager, String coordinate, String version){return new IntegrationDependencyChange(manager, coordinate, version, "compile", IntegrationDependencyAction.ADD);}
    /** Descripción: Ejecuta la operación «workspacePackage» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String workspacePackage(String packageName, String serverLanguage){
        String workspaces = serverLanguage.equals("typescript") ? "\"client\",\"server\"" : "\"client\"";
        return """
            {"name":"%s-workspace","private":true,"version":"1.0.0","workspaces":[%s],"scripts":{"build":"npm run build --workspace client","test":"npm test --workspaces --if-present"}}
            """.formatted(packageName.replace('/', '-').replace('@', 'x'), workspaces);
    }
    /** Descripción: Ejecuta la operación «clientPackage» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; glspVersion: dato de entrada necesario para la operación; typescriptVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String clientPackage(String packageName, String glspVersion, String typescriptVersion){return """
            {"name":"%s-client","version":"1.0.0","type":"module","scripts":{"build":"tsc -b","test":"vitest run","check":"npm run build && npm test"},"dependencies":{"@eclipse-glsp/client":"^%s","@eclipse-glsp/protocol":"^%s","inversify":"^6.1.3"},"devDependencies":{"typescript":"^%s","vite":"8.3.5","vitest":"5.0.5"}}
            """.formatted(packageName, glspVersion, glspVersion, typescriptVersion);}
    /** Descripción: Ejecuta la operación «tsconfig» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String tsconfig(){return """
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"lib","rootDir":"src","skipLibCheck":true,"experimentalDecorators":true,"useDefineForClassFields":false},"include":["src/**/*.ts"]}
            """;}
    /** Descripción: Genera el tsconfig del servidor Node: TypeScript 6 ya no carga @types implícitamente, así que se declaran los tipos de Node que usan el almacenamiento y el runtime. Entradas: no requiere parámetros de entrada. Salida: JSON del tsconfig. */ private String serverTsconfig(){return """
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"lib","rootDir":"src","skipLibCheck":true,"experimentalDecorators":true,"useDefineForClassFields":false,"types":["node"]},"include":["src/**/*.ts"]}
            """;}
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; diagramType: dato de entrada necesario para la operación; serverLanguage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String descriptor(MetaModel metamodel, EditorDefinitionModel editor, String diagramType, String serverLanguage){
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "1.0"); value.put("diagramType", diagramType); value.put("metamodelId", metamodel.id());
        value.put("editorDefinitionId", editor.id()); value.put("serverLanguage", serverLanguage);
        value.put("sourceModel", "HYDRA_CANONICAL_JSON");
        value.put("representations", editor.representations().stream().map(item -> Map.of("id", item.id(), "name", item.name(), "kind", item.kind().name())).toList());
        return mapper.writeValueAsString(value);
    }
    /** Descripción: Ejecuta la operación «diagramTypeSource» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: diagramType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String diagramTypeSource(String diagramType){return "export const HYDRA_DIAGRAM_TYPE='" + diagramType.replace("'", "") + "';\n";}
    /** Descripción: Genera el módulo Inversify del cliente con la firma de inversify 6, la que exige @eclipse-glsp/client 2.x como peer; GLSP 2.x no publica un símbolo TYPES para decoradores de validación, así que el proveedor de marcadores HYDRA se registra por su clase. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String clientModuleSource(){return """
            import { ContainerModule } from 'inversify';
            import { configureDefaultModelElements } from '@eclipse-glsp/client';
            import { HydraValidationMarkerProvider } from './hydra-validation.js';
            export const hydraDiagramModule=new ContainerModule((bind,_unbind,isBound)=>{configureDefaultModelElements({bind,isBound});bind(HydraValidationMarkerProvider).toSelf().inSingletonScope();});
            """;}
    /** Descripción: Ejecuta la operación «viewsSource» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String viewsSource(EditorDefinitionModel editor, boolean visual){
        String mappings = editor.representations().stream().filter(value -> value.kind() == RepresentationKind.DIAGRAM)
                .flatMap(value -> value.nodeMappings().stream()).map(value -> "'" + value.representationName().replace("'", "") + "'").distinct().reduce((a,b)->a+","+b).orElse("");
        return """
                %s
                export const hydraNodeTypes=[%s] as const;
                export const hydraEdgeType='hydra:edge';
                export function stableCssClass(type:string):string{return `hydra-${type.replaceAll(/[^a-zA-Z0-9_-]/g,'-').toLowerCase()}`;}
                %s
                """.formatted(
                        visual ? "import {createHydraVisualFullStack,type HydraVisualHost,type HydraVisualOptions} from './hydra-visual-full-stack.js';" : "",
                        mappings,
                        visual ? "export function createHydraGlspVisualRuntime(host:HydraVisualHost,options:HydraVisualOptions={}){return createHydraVisualFullStack(host,options);}" : "");}
    /** Descripción: Ejecuta la operación «paletteSource» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String paletteSource(EditorDefinitionModel editor){
        String labels = editor.palette().tools().stream().map(tool -> "'" + tool.label().replace("'", "") + "'").reduce((a,b)->a+","+b).orElse("");
        return """
                export const hydraPaletteLabels=[%s] as const;
                export interface HydraToolDescriptor {id:string;label:string;kind:string;}
                """.formatted(labels);}
    /** Descripción: Ejecuta la operación «clientValidationSource» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String clientValidationSource(){return """
            export class HydraValidationMarkerProvider { decorate(model:unknown,diagnostics:readonly unknown[]):unknown{return {model,diagnostics};} }
            """;}
    /** Descripción: Ejecuta la operación «clientEventSource» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String clientEventSource(){return """
            export interface HydraGlspEventBridge { publishAfterCommit(eventType:string,elementId:string,payload:Record<string,unknown>):Promise<void>; }
            """;}

    /** Descripción: Ejecuta la operación «clientTestSource» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: diagramType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String clientTestSource(String diagramType, boolean visual){return """
            import { describe, expect, it } from 'vitest';
            import { HYDRA_DIAGRAM_TYPE } from './diagram-type.js';
            %s
            describe('generated GLSP client',()=>{
              it('keeps the configured diagram type',()=>expect(HYDRA_DIAGRAM_TYPE).toBe('%s'));
              %s
            });
            """.formatted(
                    visual ? "import {createHydraGlspVisualRuntime} from './hydra-views.js';" : "",
                    diagramType,
                    visual ? "it('executes HYDRA conditional visual personalization',()=>{const runtime=createHydraGlspVisualRuntime({evaluateExpression:(id)=>id==='expr:vip'});const appearance=runtime.resolveNodeAppearance({id:'node:test',shape:'RECTANGLE',styles:{fillColor:'#fff'},conditionalStyles:[{id:'style:vip',enabled:true,priority:10,conditionExpressionId:'expr:vip',styles:{fillColor:'#fc0',shape:'ELLIPSE'}}]},{id:'person-1'});expect(appearance.styles.fillColor).toBe('#fc0');expect(appearance.shape).toBe('ELLIPSE');});" : "");
    }

    /** Descripción: Ejecuta la operación «addJavaServer» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: files: archivo o contenido que debe procesarse; root: ruta o ubicación utilizada por la operación; metamodel: dato de entrada necesario para la operación; diagramType: dato de entrada necesario para la operación; sourceIds: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; glspVersion: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addJavaServer(List<TechnologyGeneratedFile> files, String root, MetaModel metamodel, EditorDefinitionModel editor, String diagramType,
            List<String> sourceIds, TechnologyCompilationRequest request, String glspVersion){
        String basePackage = TechnologyAdapterSupport.javaPackage(request.profile().target().settings().get("basePackage"),
                "com.generated." + TechnologyAdapterSupport.slug(metamodel.id()) + ".glsp");
        String path = TechnologyAdapterSupport.javaPath(basePackage);
        files.add(TechnologyAdapterSupport.text(root + "/server/pom.xml", "application/xml", javaServerPom(basePackage, glspVersion), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraDiagramModule.java", "text/x-java-source", javaDiagramModule(basePackage, diagramType), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraDiagramConfiguration.java", "text/x-java-source", javaDiagramConfiguration(basePackage, diagramType), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraGModelState.java", "text/x-java-source", javaGModelState(basePackage), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraSourceModelStorage.java", "text/x-java-source", javaStorage(basePackage), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraGModelFactory.java", "text/x-java-source", javaFactory(basePackage, diagramType, editor), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraOperationHandler.java", "text/x-java-source", javaOperation(basePackage), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraValidationBridge.java", "text/x-java-source", javaValidation(basePackage), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraEventBridge.java", "text/x-java-source", javaEvent(basePackage), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraRuntimeActivation.java", "text/x-java-source",
                TechnologyAdapterGenerationSupport.javaRuntimeActivationSource(basePackage, request), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraSemanticRuntime.java", "text/x-java-source",
                TechnologyAdapterRuntimeLifecycleSupport.javaSemanticRuntimeSource(basePackage), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraSemanticRuntimeService.java", "text/x-java-source",
                javaRuntimeService(basePackage), sourceIds));
        if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraTextualFullStack.java", "text/x-java-source", TechnologyAdapterTextualCustomizationSupport.javaFullStackBridgeSource(basePackage, request, mapper), sourceIds));}
        if(TechnologyAdapterVisualFullStackSupport.available(request)){files.add(TechnologyAdapterSupport.text(root + "/server/src/main/java/" + path + "/HydraVisualFullStack.java", "text/x-java-source", TechnologyAdapterVisualFullStackSupport.javaBridgeSource(basePackage, request), sourceIds));}
    }
    /** Genera un servidor Java cuyo empaquetado copia todas las dependencias runtime a target/distribution/lib. */
    /** Descripción: Ejecuta la operación «javaServerPom» correspondiente a la responsabilidad de EclipseGlspTechnologyAdapter. Entradas: basePackage: dato de entrada necesario para la operación; glspVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String javaServerPom(String basePackage, String glspVersion){return """
            <?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>%s</groupId><artifactId>hydra-glsp-server</artifactId><version>1.0.0-SNAPSHOT</version><properties><maven.compiler.release>17</maven.compiler.release><glsp.version>%s</glsp.version></properties><dependencies><dependency><groupId>org.eclipse.glsp</groupId><artifactId>org.eclipse.glsp.server</artifactId><version>${glsp.version}</version></dependency><dependency><groupId>com.google.code.gson</groupId><artifactId>gson</artifactId><version>2.11.0</version></dependency></dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-dependency-plugin</artifactId><version>3.8.1</version><executions><execution><id>vendor-runtime</id><phase>package</phase><goals><goal>copy-dependencies</goal></goals><configuration><includeScope>runtime</includeScope><outputDirectory>${project.build.directory}/distribution/lib</outputDirectory></configuration></execution></executions></plugin></plugins></build></project>
            """.formatted(basePackage, glspVersion);}

    /** Descripción: Genera el módulo GLSP Java compatible con la API 2.5 y enlaza almacenamiento, proyección, validación y estado transaccional. Entradas: basePackage: paquete Java; diagramType: tipo de diagrama. Salida: devuelve el código fuente del módulo. */ private String javaDiagramModule(String basePackage, String diagramType){return """
            package %s;
            import org.eclipse.glsp.server.di.DiagramModule;
            import org.eclipse.glsp.server.diagram.DiagramConfiguration;
            import org.eclipse.glsp.server.features.core.model.GModelFactory;
            import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
            import org.eclipse.glsp.server.features.validation.ModelValidator;
            import org.eclipse.glsp.server.model.GModelState;
            public final class HydraDiagramModule extends DiagramModule {
                @Override public String getDiagramType(){return "%s";}
                @Override protected Class<? extends DiagramConfiguration> bindDiagramConfiguration(){return HydraDiagramConfiguration.class;}
                @Override protected Class<? extends SourceModelStorage> bindSourceModelStorage(){return HydraSourceModelStorage.class;}
                @Override protected Class<? extends GModelFactory> bindGModelFactory(){return HydraGModelFactory.class;}
                @Override protected Class<? extends GModelState> bindGModelState(){return HydraGModelState.class;}
                @Override protected Class<? extends ModelValidator> bindModelValidator(){return HydraValidationBridge.class;}
            }
            """.formatted(basePackage, diagramType);}

    /** Descripción: Genera la configuración concreta del diagrama requerida por GLSP 2.5. Entradas: basePackage: paquete Java; diagramType: tipo de diagrama. Salida: devuelve el código fuente de configuración. */ private String javaDiagramConfiguration(String basePackage,String diagramType){return """
            package %s;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import org.eclipse.emf.ecore.EClass;
            import org.eclipse.glsp.graph.GraphExtension;
            import org.eclipse.glsp.server.diagram.DiagramConfiguration;
            import org.eclipse.glsp.server.types.EdgeTypeHint;
            import org.eclipse.glsp.server.types.ShapeTypeHint;
            public final class HydraDiagramConfiguration implements DiagramConfiguration {
                @Override public String getDiagramType(){return "%s";}
                @Override public Map<String,EClass> getTypeMappings(){return Map.of();}
                @Override public List<ShapeTypeHint> getShapeTypeHints(){return List.of();}
                @Override public List<EdgeTypeHint> getEdgeTypeHints(){return List.of();}
                @Override public Optional<GraphExtension> getGraphExtension(){return Optional.empty();}
            }
            """.formatted(basePackage, diagramType);}

    /** Descripción: Genera el estado GLSP que acopla execute/undo/redo con checkpoints completos HYDRA. Entradas: basePackage: paquete Java. Salida: devuelve el código fuente del estado. */ private String javaGModelState(String basePackage){return """
            package %s;
            import java.util.ArrayDeque;
            import java.util.Deque;
            import org.eclipse.emf.common.command.Command;
            import org.eclipse.glsp.server.model.DefaultGModelState;
            import com.google.inject.Inject;
            public final class HydraGModelState extends DefaultGModelState {
                private final Deque<Transition> undoTransitions=new ArrayDeque<>();
                private final Deque<Transition> redoTransitions=new ArrayDeque<>();
                @Inject private HydraSemanticRuntimeService hydraRuntime;
                @Override public void execute(Command command){
                    HydraSemanticRuntime.Snapshot before=hydraRuntime.checkpoint();
                    super.execute(command);
                    if(commandStack==null||commandStack.getMostRecentCommand()!=command) return;
                    try{
                        hydraRuntime.synchronize(sourceModel(),"GLSP_OPERATION");
                        undoTransitions.push(new Transition(command,before,hydraRuntime.checkpoint()));redoTransitions.clear();
                    }catch(RuntimeException exception){
                        if(commandStack.canUndo()&&commandStack.getUndoCommand()==command) commandStack.undo();
                        hydraRuntime.restore(before);throw exception;
                    }
                }
                @Override public void undo(){
                    if(!canUndo()) return;Command command=commandStack.getUndoCommand();HydraSemanticRuntime.Snapshot current=hydraRuntime.checkpoint();
                    super.undo();if(commandStack.getMostRecentCommand()!=command) return;
                    Transition transition=!undoTransitions.isEmpty()&&undoTransitions.peek().command()==command?undoTransitions.peek():null;
                    try{
                        if(transition==null) hydraRuntime.synchronize(sourceModel(),"GLSP_UNDO");
                        else if(HydraRuntimeActivation.isEnabled()&&transition.before().activationEpoch()==HydraRuntimeActivation.epoch()) hydraRuntime.restore(transition.before());
                        else hydraRuntime.synchronize(sourceModel(),"GLSP_UNDO");
                    }catch(RuntimeException exception){
                        if(commandStack.canRedo()&&commandStack.getRedoCommand()==command) commandStack.redo();hydraRuntime.restore(current);throw exception;
                    }
                    if(transition!=null){undoTransitions.pop();redoTransitions.push(transition);}
                }
                @Override public void redo(){
                    if(!canRedo()) return;Command command=commandStack.getRedoCommand();HydraSemanticRuntime.Snapshot current=hydraRuntime.checkpoint();
                    super.redo();if(commandStack.getMostRecentCommand()!=command) return;
                    Transition transition=!redoTransitions.isEmpty()&&redoTransitions.peek().command()==command?redoTransitions.peek():null;
                    try{
                        if(transition==null) hydraRuntime.synchronize(sourceModel(),"GLSP_REDO");
                        else if(HydraRuntimeActivation.isEnabled()&&transition.after().activationEpoch()==HydraRuntimeActivation.epoch()) hydraRuntime.restore(transition.after());
                        else hydraRuntime.synchronize(sourceModel(),"GLSP_REDO");
                    }catch(RuntimeException exception){
                        if(commandStack.canUndo()&&commandStack.getUndoCommand()==command) commandStack.undo();hydraRuntime.restore(current);throw exception;
                    }
                    if(transition!=null){redoTransitions.pop();undoTransitions.push(transition);}
                }
                private String sourceModel(){return getProperty(HydraSourceModelStorage.SOURCE_MODEL_PROPERTY,String.class).orElse("");}
                private record Transition(Command command,HydraSemanticRuntime.Snapshot before,HydraSemanticRuntime.Snapshot after){}
            }
            """.formatted(basePackage);}

    /** Descripción: Genera almacenamiento de modelo fuente real para GLSP, manteniendo el texto semántico como fuente de verdad. Entradas: basePackage: paquete Java. Salida: devuelve el código fuente de almacenamiento. */ private String javaStorage(String basePackage){return """
            package %s;
            import java.io.File;
            import java.io.IOException;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import org.eclipse.glsp.server.actions.SaveModelAction;
            import org.eclipse.glsp.server.features.core.model.RequestModelAction;
            import org.eclipse.glsp.server.features.core.model.SourceModelStorage;
            import org.eclipse.glsp.server.model.GModelState;
            import org.eclipse.glsp.server.utils.ClientOptionsUtil;
            import com.google.inject.Inject;
            public final class HydraSourceModelStorage implements SourceModelStorage {
                public static final String SOURCE_MODEL_PROPERTY="hydra.sourceModel";
                @Inject private GModelState modelState;
                @Inject private HydraSemanticRuntimeService hydraRuntime;
                @Override public void loadSourceModel(RequestModelAction action){
                    modelState.setClientOptions(action.getOptions());
                    File file=ClientOptionsUtil.getSourceUriAsFile(action.getOptions()).orElseThrow(()->new IllegalStateException("Missing GLSP sourceUri"));
                    try{String source=Files.readString(file.toPath(),StandardCharsets.UTF_8);replaceSourceModel(source);hydraRuntime.synchronize(source,"GLSP_LOAD");}
                    catch(IOException exception){throw new IllegalStateException("Could not load HYDRA source model",exception);}
                }
                @Override public void saveSourceModel(SaveModelAction action){
                    File file=ClientOptionsUtil.getSourceUriAsFile(modelState.getClientOptions()).orElseThrow(()->new IllegalStateException("Missing GLSP sourceUri"));
                    try{Files.writeString(file.toPath(),sourceModel(),StandardCharsets.UTF_8);hydraRuntime.synchronize(sourceModel(),"GLSP_SAVE");modelState.saveIsDone();}
                    catch(IOException exception){throw new IllegalStateException("Could not save HYDRA source model",exception);}
                }
                public String sourceModel(){return modelState.getProperty(SOURCE_MODEL_PROPERTY,String.class).orElse("");}
                public void replaceSourceModel(String source){modelState.setProperty(SOURCE_MODEL_PROPERTY,source==null?"":source);}
            }
            """.formatted(basePackage);}

    /** Descripción: Genera la proyección GModel concreta desde CanonicalModelDocument y mappings del editor; el GModel nunca sustituye al source model. Entradas: basePackage: paquete Java; diagramType: tipo; editor: definición canónica. Salida: devuelve el código fuente de fábrica. */ private String javaFactory(String basePackage,String diagramType,EditorDefinitionModel editor){
        Map<String,String> nodeTypes=new LinkedHashMap<>();
        Map<String,String> edgeTypes=new LinkedHashMap<>();
        editor.representations().stream().filter(r->r.kind()==RepresentationKind.DIAGRAM).forEach(r->{
            r.nodeMappings().forEach(m->nodeTypes.putIfAbsent(m.targetClassId(),m.representationName()));
            r.edgeMappings().stream().filter(m->!m.relationId().isBlank()).forEach(m->edgeTypes.putIfAbsent(m.relationId(),"hydra:edge"));
        });
        String nodes=javaMapEntries(nodeTypes);String edges=javaMapEntries(edgeTypes);
        return """
            package %s;
            import java.util.Map;
            import java.util.Set;
            import java.util.LinkedHashSet;
            import org.eclipse.glsp.graph.GEdge;
            import org.eclipse.glsp.graph.GGraph;
            import org.eclipse.glsp.graph.GNode;
            import org.eclipse.glsp.graph.GraphFactory;
            import org.eclipse.glsp.server.features.core.model.GModelFactory;
            import org.eclipse.glsp.server.model.GModelState;
            import com.google.gson.JsonArray;
            import com.google.gson.JsonElement;
            import com.google.gson.JsonObject;
            import com.google.gson.JsonParser;
            import com.google.inject.Inject;
            public final class HydraGModelFactory implements GModelFactory {
                private static final Map<String,String> NODE_TYPES=Map.ofEntries(%s);
                private static final Map<String,String> EDGE_TYPES=Map.ofEntries(%s);
                @Inject private GModelState modelState;
                @Override public void createGModel(){
                    GGraph root=GraphFactory.eINSTANCE.createGGraph();root.setId("hydra-root");root.setType("%s");
                    String source=modelState.getProperty(HydraSourceModelStorage.SOURCE_MODEL_PROPERTY,String.class).orElse("");
                    if(!source.isBlank()) project(root,source);
                    modelState.updateRoot(root);
                }
                private void project(GGraph root,String source){
                    try{
                        JsonObject document=JsonParser.parseString(source).getAsJsonObject();Set<String> visible=new LinkedHashSet<>();
                        JsonArray elements=array(document,"elements");
                        for(JsonElement value:elements){JsonObject element=value.getAsJsonObject();String id=text(element,"id");String classId=text(element,"classId");String type=NODE_TYPES.get(classId);if(id.isBlank()||type==null)continue;GNode node=GraphFactory.eINSTANCE.createGNode();node.setId(id);node.setType(type);node.setTrace(classId);root.getChildren().add(node);visible.add(id);}
                        JsonArray relations=array(document,"relations");
                        for(JsonElement value:relations){JsonObject relation=value.getAsJsonObject();String id=text(relation,"id");String relationId=text(relation,"relationId");String sourceId=text(relation,"sourceElementId");String targetId=text(relation,"targetElementId");String type=EDGE_TYPES.get(relationId);if(id.isBlank()||type==null||!visible.contains(sourceId)||!visible.contains(targetId))continue;GEdge edge=GraphFactory.eINSTANCE.createGEdge();edge.setId(id);edge.setType(type);edge.setTrace(relationId);edge.setSourceId(sourceId);edge.setTargetId(targetId);root.getChildren().add(edge);}
                    }catch(RuntimeException ignored){/* Invalid drafts remain source-model state; HYDRA validation reports them without inventing a GModel. */}
                }
                private static JsonArray array(JsonObject object,String key){JsonElement value=object.get(key);return value!=null&&value.isJsonArray()?value.getAsJsonArray():new JsonArray();}
                private static String text(JsonObject object,String key){JsonElement value=object.get(key);return value==null||value.isJsonNull()?"":value.getAsString();}
            }
            """.formatted(basePackage,nodes,edges,diagramType);}

    /** Descripción: Convierte un mapa determinista de strings a entradas Java seguras para Map.ofEntries. Entradas: values: mappings canónicos. Salida: devuelve expresiones Map.entry separadas. */ private String javaMapEntries(Map<String,String> values){
        if(values.isEmpty()) return "";
        return values.entrySet().stream().map(e->"Map.entry("+javaStringLiteral(e.getKey())+","+javaStringLiteral(e.getValue())+")").reduce((a,b)->a+","+b).orElse("");
    }

    /** Descripción: Escapa un literal Java generado sin introducir dependencias externas. Entradas: value: texto. Salida: literal Java entre comillas. */ private String javaStringLiteral(String value){String escaped=String.valueOf(value==null?"":value).replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n");return "\""+escaped+"\"";}

    /** Descripción: Genera un servicio de operación que modifica el modelo fuente mediante comandos reversibles del command stack GLSP. Entradas: basePackage: paquete Java. Salida: devuelve el código fuente del servicio. */ private String javaOperation(String basePackage){return """
            package %s;
            import org.eclipse.emf.common.command.AbstractCommand;
            import org.eclipse.glsp.server.model.GModelState;
            import com.google.inject.Inject;
            public final class HydraOperationHandler {
                @Inject private GModelState modelState;
                @Inject private HydraSourceModelStorage storage;
                public void replaceSourceModel(String updatedState){
                    String before=storage.sourceModel();String after=updatedState==null?"":updatedState;
                    modelState.execute(new AbstractCommand("HYDRA semantic update"){
                        @Override protected boolean prepare(){return true;}
                        @Override public void execute(){storage.replaceSourceModel(after);}
                        @Override public void redo(){storage.replaceSourceModel(after);}
                        @Override public void undo(){storage.replaceSourceModel(before);}
                    });
                }
            }
            """.formatted(basePackage);}

    /** Descripción: Genera el adaptador de validación GLSP y proyecta diagnósticos HYDRA a Marker sin confirmar cambios. Entradas: basePackage: paquete Java. Salida: devuelve el código fuente del validador. */ private String javaValidation(String basePackage){return """
            package %s;
            import java.util.List;
            import org.eclipse.glsp.graph.GModelElement;
            import org.eclipse.glsp.server.features.validation.Marker;
            import org.eclipse.glsp.server.features.validation.ModelValidator;
            import com.google.inject.Inject;
            public final class HydraValidationBridge implements ModelValidator {
                @Inject private HydraSourceModelStorage storage;
                @Inject private HydraSemanticRuntimeService hydraRuntime;
                @Override public List<Marker> doLiveValidation(GModelElement element){return markers(hydraRuntime.validateHost(storage.sourceModel(),"GLSP_LIVE_VALIDATION"),element.getId());}
                @Override public List<Marker> doBatchValidation(GModelElement element){return markers(hydraRuntime.validateHost(storage.sourceModel(),"GLSP_BATCH_VALIDATION"),element.getId());}
                private List<Marker> markers(List<HydraSemanticRuntime.Diagnostic> diagnostics,String fallbackId){return diagnostics.stream().map(d->new Marker(d.id(),d.message(),d.elementId().isBlank()?fallbackId:d.elementId(),d.severity().toLowerCase())).toList();}
            }
            """.formatted(basePackage);}

    /** Descripción: Genera la fachada observable de eventos confirmados del runtime GLSP. Entradas: basePackage: paquete Java. Salida: devuelve el código fuente de eventos. */ private String javaEvent(String basePackage){return """
            package %s;
            import java.util.List;
            import com.google.inject.Inject;
            public final class HydraEventBridge {
                @Inject private HydraSemanticRuntimeService hydraRuntime;
                public List<HydraSemanticRuntime.Event> history(){return hydraRuntime.events();}
                public HydraSemanticRuntime.CommitResult publishAfterCommit(String semanticState,String trigger){return hydraRuntime.synchronize(semanticState,trigger);}
            }
            """.formatted(basePackage);}

    /** Descripción: Genera el servicio de lifecycle por sesión que ejecuta validación, eventos, EventFlow y gamificación en orden. Entradas: basePackage: paquete Java. Salida: devuelve el código fuente del servicio. */ private String javaRuntimeService(String basePackage){return """
            package %s;
            import java.util.List;
            import java.util.Map;
            import java.util.Objects;
            import com.google.inject.Singleton;
            @Singleton public final class HydraSemanticRuntimeService {
                private volatile HydraSemanticRuntime runtime=new HydraSemanticRuntime(new NeutralEngine());
                public synchronized void install(HydraSemanticRuntime.Engine engine){runtime=new HydraSemanticRuntime(Objects.requireNonNull(engine,"engine"));}
                public List<HydraSemanticRuntime.Diagnostic> validateHost(String state,String trigger){return runtime.validateHost(state,trigger);}
                public HydraSemanticRuntime.CommitResult synchronize(String state,String trigger){return runtime.synchronize(state,trigger);}
                public HydraSemanticRuntime.Snapshot checkpoint(){return runtime.checkpoint();}
                public HydraSemanticRuntime.Snapshot checkpointBaseline(){return runtime.checkpointBaseline();}
                public void restore(HydraSemanticRuntime.Snapshot snapshot){runtime.restore(snapshot);}
                public List<HydraSemanticRuntime.Event> events(){return runtime.events();}
                public HydraSemanticRuntime.GamificationState gamification(){return runtime.gamification();}
                private static final class NeutralEngine implements HydraSemanticRuntime.Engine {
                    @Override public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return List.of();}
                    @Override public List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return List.of(new HydraSemanticRuntime.Event(sequence,"MODEL_CHANGED","model",Map.of("trigger",trigger)));}
                    @Override public List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> events,String state){return List.of();}
                    @Override public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> events,List<HydraSemanticRuntime.Event> flowEvents){return previous;}
                }
            }
            """.formatted(basePackage);}

    /** Descripción: Genera el servidor TypeScript/Node GLSP 2.5 con servicios de source model, GModel, operaciones y lifecycle HYDRA por sesión. Entradas: files: salida; root: raíz; diagramType: tipo; sourceIds: procedencia; packageName: paquete; glspVersion: versión; typescriptVersion: versión TS; request: solicitud; editor: definición visual canónica. Salida: añade los artefactos del servidor. */ private void addTypeScriptServer(List<TechnologyGeneratedFile> files, String root, String diagramType, List<String> sourceIds, String packageName, String glspVersion, String typescriptVersion, TechnologyCompilationRequest request, EditorDefinitionModel editor){
        files.add(TechnologyAdapterSupport.text(root + "/server/package.json", "application/json", nodeServerPackage(packageName, glspVersion, typescriptVersion), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/tsconfig.json", "application/json", serverTsconfig(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-diagram-configuration.ts", "text/typescript", nodeDiagramConfiguration(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-diagram-module.ts", "text/typescript", nodeDiagramModule(diagramType), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-source-model-storage.ts", "text/typescript", nodeStorage(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-gmodel-factory.ts", "text/typescript", nodeFactory(diagramType, editor), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-operation-handler.ts", "text/typescript", nodeOperation(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-event-bridge.ts", "text/typescript", nodeEvent(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-runtime-activation.ts", "text/typescript",
                    TechnologyAdapterGenerationSupport.typescriptRuntimeActivationSource(request), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-semantic-runtime.ts", "text/typescript",
                    TechnologyAdapterRuntimeLifecycleSupport.typescriptSemanticRuntimeSource(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-semantic-runtime-service.ts", "text/typescript",
                    nodeRuntimeService(), sourceIds));
        if(TechnologyAdapterVisualFullStackSupport.available(request)){files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-visual-full-stack.ts", "text/typescript", TechnologyAdapterVisualFullStackSupport.typescriptBridgeSource(request), sourceIds));}
        files.add(TechnologyAdapterSupport.text(root + "/server/src/hydra-server.test.ts", "text/typescript", nodeServerTest(diagramType), sourceIds));
    }
    /** Descripción: Genera package.json compatible con los artefactos npm publicados por GLSP 2.5 y su peer de Inversify. Entradas: packageName: nombre; glspVersion: versión; typescriptVersion: versión TS. Salida: JSON del paquete Node. */ private String nodeServerPackage(String packageName, String glspVersion, String typescriptVersion){return """
            {"name":"%s-server","version":"1.0.0","type":"module","scripts":{"build":"tsc -b","test":"vitest run","check":"npm run build && npm test"},"dependencies":{"@eclipse-glsp/server":"^%s","@eclipse-glsp/graph":"^%s","@eclipse-glsp/protocol":"^%s","inversify":"^6.1.3","reflect-metadata":"^0.2.2"},"devDependencies":{"@types/node":"^22.10.0","typescript":"^%s","vite":"8.3.5","vitest":"5.0.5"}}
            """.formatted(packageName, glspVersion, glspVersion, glspVersion, typescriptVersion);}
    /** Descripción: Genera la configuración neutral del diagrama exigida por DiagramModule de GLSP Node 2.5. Entradas: no requiere parámetros. Salida: fuente TypeScript. */ private String nodeDiagramConfiguration(){return """
            import type { DiagramConfiguration } from '@eclipse-glsp/server';
            import { ServerLayoutKind } from '@eclipse-glsp/server';
            export class HydraDiagramConfiguration implements DiagramConfiguration {
              readonly shapeTypeHints=[];readonly edgeTypeHints=[];readonly layoutKind=ServerLayoutKind.NONE;
              readonly needsClientLayout=true;readonly animatedUpdate=true;readonly typeMapping=new Map();
            }
            """;}
    /** Descripción: Genera un DiagramModule Node 2.5 completo, con bindings concretos y lifecycle por sesión. Entradas: diagramType: tipo del diagrama. Salida: fuente TypeScript. */ private String nodeDiagramModule(String diagramType){return """
            import 'reflect-metadata';
            import type { interfaces } from 'inversify';
            import { DefaultModelState, DiagramModule, type BindingTarget, type DiagramConfiguration, type GModelFactory, type ModelState, type SourceModelStorage, type InstanceMultiBinding, type OperationHandlerConstructor } from '@eclipse-glsp/server';
            import { HydraDiagramConfiguration } from './hydra-diagram-configuration.js';
            import { HydraGModelFactory } from './hydra-gmodel-factory.js';
            import { HydraOperationHandler } from './hydra-operation-handler.js';
            import { HydraSemanticRuntimeService } from './hydra-semantic-runtime-service.js';
            import { HydraSourceModelStorage } from './hydra-source-model-storage.js';
            export class HydraDiagramModule extends DiagramModule {
              readonly diagramType='%s';
              protected override configure(bind:interfaces.Bind,unbind:interfaces.Unbind,isBound:interfaces.IsBound,rebind:interfaces.Rebind):void{
                super.configure(bind,unbind,isBound,rebind);
                if(!isBound(HydraSemanticRuntimeService))bind(HydraSemanticRuntimeService).toSelf().inSingletonScope();
              }
              protected override bindSourceModelStorage():BindingTarget<SourceModelStorage>{return HydraSourceModelStorage;}
              protected override bindModelState():BindingTarget<ModelState>{return DefaultModelState;}
              protected override bindDiagramConfiguration():BindingTarget<DiagramConfiguration>{return HydraDiagramConfiguration;}
              protected override bindGModelFactory():BindingTarget<GModelFactory>{return HydraGModelFactory;}
              protected override configureOperationHandlers(binding:InstanceMultiBinding<OperationHandlerConstructor>):void{super.configureOperationHandlers(binding);binding.add(HydraOperationHandler);}
            }
            """.formatted(diagramType);}
    /** Descripción: Genera almacenamiento GLSP Node seguro: revisión optimista, bloqueo exclusivo y publicación atómica de modelos. Entrada: sin parámetros. Salida: código TypeScript del almacenamiento. */ private String nodeStorage(){return """
            import { readFile, open, lstat, rename, unlink } from 'node:fs/promises';
            import { createHash, randomUUID } from 'node:crypto';
            import { dirname, join, resolve } from 'node:path';
            import { fileURLToPath } from 'node:url';
            import { inject, injectable } from 'inversify';
            import type { RequestModelAction, SaveModelAction } from '@eclipse-glsp/protocol';
            import { ModelState, SOURCE_URI_ARG, type SourceModelStorage } from '@eclipse-glsp/server';
            import { HydraSemanticRuntimeService } from './hydra-semantic-runtime-service.js';
            export const HYDRA_SOURCE_MODEL_PROPERTY='hydra.sourceModel';
            const ORIGINAL_URI='hydra.originalSourceUri';
            const LOADED_SHA='hydra.loadedSourceSha256';
            const MAX_BYTES=32*1024*1024;
            const sha=(state:string):string=>createHash('sha256').update(state,'utf8').digest('hex');
            const pathOf=(uri:string):string=>{
              if(typeof uri!=='string'||!uri.trim()||uri.includes('\\0'))throw new Error('Invalid GLSP source URI');
              const path=uri.startsWith('file:')?fileURLToPath(uri):uri;
              if(!resolve(path) || !path.trim())throw new Error('Invalid GLSP source path');
              return resolve(path);
            };
            async function regularFile(path:string):Promise<void>{
              const item=await lstat(path);
              if(item.isSymbolicLink()||!item.isFile()||item.size>MAX_BYTES)throw new Error('GLSP model is linked, oversized or not a regular file');
            }
            @injectable()
            export class HydraSourceModelStorage implements SourceModelStorage{
              constructor(@inject(ModelState) private readonly modelState:ModelState,@inject(HydraSemanticRuntimeService) private readonly runtime:HydraSemanticRuntimeService){}
              async loadSourceModel(action:RequestModelAction):Promise<void>{
                const option=action.options?.sourceUri;
                const sourceUri=typeof option==='string'?option:this.modelState.sourceUri;
                if(!sourceUri)throw new Error('GLSP sourceUri is required');
                const path=pathOf(sourceUri);await regularFile(path);
                const state=await readFile(path,'utf8');
                if(Buffer.byteLength(state,'utf8')>MAX_BYTES)throw new Error('GLSP model exceeds size limit');
                this.modelState.set(HYDRA_SOURCE_MODEL_PROPERTY,state);
                this.modelState.set(SOURCE_URI_ARG,sourceUri);
                this.modelState.set(ORIGINAL_URI,path);
                this.modelState.set(LOADED_SHA,sha(state));
                this.runtime.synchronize(state,'GLSP_NODE_LOAD');
              }
              async saveSourceModel(action:SaveModelAction):Promise<void>{
                const target=action.fileUri??this.modelState.sourceUri;
                if(!target)throw new Error('GLSP sourceUri is required for save');
                const path=pathOf(target),original=this.modelState.get<string>(ORIGINAL_URI);
                if(!original||original!==path)throw new Error('GLSP save-as requires explicit separate authorization');
                const originalSha=this.modelState.get<string>(LOADED_SHA);
                if(!originalSha)throw new Error('GLSP model has not been loaded');
                const state=this.sourceModel();
                if(Buffer.byteLength(state,'utf8')>MAX_BYTES)throw new Error('GLSP model exceeds size limit');
                const lock=path+'.hydra-write.lock',temp=join(dirname(path),'.hydra-glsp-'+randomUUID()+'.tmp');
                // Create an exclusive lock across separate server processes (not just sessions).
                const handle=await open(lock,'wx',0o600);
                try{
                  await regularFile(path);
                  const present=await readFile(path,'utf8');
                  if(sha(present)!==originalSha)throw new Error('GLSP concurrent model revision conflict');
                  const staging=await open(temp,'wx',0o600);
                  try{await staging.writeFile(state,'utf8');await staging.sync();}finally{await staging.close();}
                  // Verify again immediately before promotion; writers cooperating with
                  // the lock cannot race. Non-cooperating writers remain a platform caveat.
                  await regularFile(path);
                  if(sha(await readFile(path,'utf8'))!==originalSha)throw new Error('GLSP concurrent external model revision conflict');
                  await rename(temp,path);
                  this.modelState.set(LOADED_SHA,sha(state));
                  this.modelState.set(SOURCE_URI_ARG,target);
                  this.runtime.synchronize(state,'GLSP_NODE_SAVE');
                }finally{
                  try{await unlink(temp).catch((error:NodeJS.ErrnoException)=>{if(error.code!=='ENOENT')throw error;});}
                  finally{try{await handle.close();}finally{await unlink(lock);}}
                }
              }
              sourceModel():string{return this.modelState.get<string>(HYDRA_SOURCE_MODEL_PROPERTY)??'';}
              replaceSourceModel(value:string):void{this.modelState.set(HYDRA_SOURCE_MODEL_PROPERTY,value??'');}
            }
            """;}
    /** Descripción: Genera una fábrica GModel Node desde CanonicalModelDocument y mappings DIAGRAM, manteniendo el JSON como fuente de verdad. Entradas: diagramType: tipo; editor: definición. Salida: fuente TypeScript. */ private String nodeFactory(String diagramType,EditorDefinitionModel editor){
        Map<String,String> nodeTypes=new LinkedHashMap<>();Map<String,String> edgeTypes=new LinkedHashMap<>();
        editor.representations().stream().filter(r->r.kind()==RepresentationKind.DIAGRAM).forEach(r->{r.nodeMappings().forEach(m->nodeTypes.putIfAbsent(m.targetClassId(),m.representationName()));r.edgeMappings().stream().filter(m->!m.relationId().isBlank()).forEach(m->edgeTypes.putIfAbsent(m.relationId(),"hydra:edge"));});
        return """
            import { GEdge, GGraph, GNode } from '@eclipse-glsp/graph';
            import { inject, injectable } from 'inversify';
            import { ModelState, type GModelFactory } from '@eclipse-glsp/server';
            import { HYDRA_SOURCE_MODEL_PROPERTY } from './hydra-source-model-storage.js';
            const NODE_TYPES:Record<string,string>=%s;
            const EDGE_TYPES:Record<string,string>=%s;
            type CanonicalElement={id?:string;classId?:string};type CanonicalRelation={id?:string;relationId?:string;sourceElementId?:string;targetElementId?:string};
            @injectable()
            export class HydraGModelFactory implements GModelFactory{
              constructor(@inject(ModelState) private readonly modelState:ModelState){}
              createModel():void{
                const root=GGraph.builder().id('hydra-root').type('%s');const visible=new Set<string>();
                const source=this.modelState.get<string>(HYDRA_SOURCE_MODEL_PROPERTY)??'';
                if(source){try{const doc=JSON.parse(source) as {elements?:CanonicalElement[];relations?:CanonicalRelation[]};for(const element of doc.elements??[]){const id=element.id??'',classId=element.classId??'',type=NODE_TYPES[classId];if(!id||!type)continue;root.add(GNode.builder().id(id).type(type).addArg('trace',classId).build());visible.add(id);}for(const relation of doc.relations??[]){const id=relation.id??'',relationId=relation.relationId??'',sourceId=relation.sourceElementId??'',targetId=relation.targetElementId??'',type=EDGE_TYPES[relationId];if(!id||!type||!visible.has(sourceId)||!visible.has(targetId))continue;root.add(GEdge.builder().id(id).type(type).sourceId(sourceId).targetId(targetId).addArg('trace',relationId).build());}}catch{/* Invalid drafts stay in source state; HYDRA validation reports them without inventing graph semantics. */}}
                this.modelState.updateRoot(root.build());
              }
            }
            """.formatted(mapper.writeValueAsString(nodeTypes),mapper.writeValueAsString(edgeTypes),diagramType);
    }
    /** Descripción: Genera un OperationHandler GLSP Node 2.5 real que devuelve Command reversible y restaura checkpoints HYDRA en undo/redo. Entradas: no requiere parámetros. Salida: fuente TypeScript. */ private String nodeOperation(){return """
            import { inject, injectable } from 'inversify';
            import type { Operation } from '@eclipse-glsp/protocol';
            import { ModelState, OperationHandler, type Command } from '@eclipse-glsp/server';
            import { hydraRuntimeActivation } from './hydra-runtime-activation.js';
            import { HydraSemanticRuntimeService } from './hydra-semantic-runtime-service.js';
            import { HYDRA_SOURCE_MODEL_PROPERTY } from './hydra-source-model-storage.js';
            type ReplaceSourceModelOperation=Operation&{newState?:string};
            @injectable()
            export class HydraOperationHandler extends OperationHandler{
              readonly operationType='hydra.replaceSourceModel';
              constructor(@inject(HydraSemanticRuntimeService) private readonly runtime:HydraSemanticRuntimeService){super();}
              createCommand(operation:Operation):Command|undefined{
                const next=(operation as ReplaceSourceModelOperation).newState;if(typeof next!=='string')return undefined;
                const state=this.modelState;const runtime=this.runtime;const before=state.get<string>(HYDRA_SOURCE_MODEL_PROPERTY)??'';const beforeRuntime=runtime.checkpoint();let afterRuntime:ReturnType<HydraSemanticRuntimeService['checkpoint']>|undefined;
                const apply=(value:string,trigger:string,rollbackState:string,rollbackRuntime:ReturnType<HydraSemanticRuntimeService['checkpoint']>)=>{state.set(HYDRA_SOURCE_MODEL_PROPERTY,value);try{runtime.synchronize(value,trigger);}catch(error){state.set(HYDRA_SOURCE_MODEL_PROPERTY,rollbackState);runtime.restore(rollbackRuntime);throw error;}};
                return{
                  execute(){apply(next,'GLSP_NODE_OPERATION',before,beforeRuntime);afterRuntime=runtime.checkpoint();},
                  undo(){const currentRuntime=runtime.checkpoint();state.set(HYDRA_SOURCE_MODEL_PROPERTY,before);try{if(beforeRuntime.activationEpoch===hydraRuntimeActivation.epoch())runtime.restore(beforeRuntime);else runtime.synchronize(before,'GLSP_NODE_UNDO_REBASELINE');}catch(error){state.set(HYDRA_SOURCE_MODEL_PROPERTY,next);runtime.restore(currentRuntime);throw error;}},
                  redo(){const currentRuntime=runtime.checkpoint();const targetRuntime=afterRuntime;state.set(HYDRA_SOURCE_MODEL_PROPERTY,next);try{if(targetRuntime&&targetRuntime.activationEpoch===hydraRuntimeActivation.epoch())runtime.restore(targetRuntime);else runtime.synchronize(next,'GLSP_NODE_REDO_REBASELINE');}catch(error){state.set(HYDRA_SOURCE_MODEL_PROPERTY,before);runtime.restore(currentRuntime);throw error;}}
                };
              }
            }
            """;}
    /** Descripción: Genera una fachada Node de eventos que usa el runtime de sesión inyectado. Entradas: no requiere parámetros. Salida: fuente TypeScript. */ private String nodeEvent(){return """
            import { inject, injectable } from 'inversify';
            import { HydraSemanticRuntimeService } from './hydra-semantic-runtime-service.js';
            @injectable()
            export class HydraEventBridge{
              constructor(@inject(HydraSemanticRuntimeService) private readonly runtime:HydraSemanticRuntimeService){}
              history(){return this.runtime.runtime().events();}gamification(){return this.runtime.runtime().gamification();}
              publishAfterCommit(state:string,trigger='GLSP_NODE_COMMIT'){return this.runtime.synchronize(state,trigger);}
            }
            """;}
    /** Descripción: Genera el servicio TypeScript de lifecycle por sesión; DiagramModule lo registra como singleton del inyector hijo de cada sesión. Entradas: no requiere parámetros. Salida: fuente TypeScript. */ private String nodeRuntimeService(){return """
            import { injectable } from 'inversify';
            import {HydraSemanticRuntime,type HydraSemanticEngine,type HydraEvent,type HydraGamificationState,type HydraRuntimeSnapshot} from './hydra-semantic-runtime.js';
            const neutralEngine:HydraSemanticEngine={validate:()=>[],deriveEvents:(_before,_after,trigger,sequence)=>[{sequence,type:'MODEL_CHANGED',elementId:'model',payload:{trigger}}],runEventFlow:()=>[],updateGamification:(previous:HydraGamificationState,_events:HydraEvent[],_flow:HydraEvent[])=>previous};
            @injectable()
            export class HydraSemanticRuntimeService{
              private current=new HydraSemanticRuntime(neutralEngine);
              runtime(){return this.current;}install(engine:HydraSemanticEngine){this.current=new HydraSemanticRuntime(engine);return this.current;}
              validateHost(state:string,trigger:string){return this.current.validateHost(state,trigger);}synchronize(state:string,trigger:string){return this.current.synchronize(state,trigger);}
              checkpoint(){return this.current.checkpoint();}restore(snapshot:HydraRuntimeSnapshot){this.current.restore(snapshot);}
            }
            """;}

    /** Descripción: Genera una prueba de contrato básica del módulo Node, sin instanciar servicios dependientes del inyector. Entradas: diagramType: tipo esperado. Salida: fuente Vitest. */ private String nodeServerTest(String diagramType){return """
            import { describe, expect, it } from 'vitest';
            import { HydraDiagramModule } from './hydra-diagram-module.js';
            describe('generated GLSP server',()=>{it('keeps the configured diagram type',()=>expect(new HydraDiagramModule().diagramType).toBe('%s'));});
            """.formatted(diagramType);
    }

    /** Descripción: Obtiene la información correspondiente a «readme» dentro de EclipseGlspTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación; diagramType: dato de entrada necesario para la operación; serverLanguage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TechnologyCompilationRequest request, MetaModel metamodel, EditorDefinitionModel editor,
            String diagramType, String serverLanguage){return """
            # HYDRA Eclipse GLSP integration

            Generated from canonical `MetaModel 2.1` `%s` and editor definition `%s`.

            - Diagram type: `%s`.
            - Client: TypeScript GLSP client module.
            - Server: %s, selectable through `target.settings.serverLanguage`.
            - Source model: canonical HYDRA JSON through an explicit SourceModelStorage boundary.
            - Node views and palette descriptors remain projections, while source-model storage, operations, validation and confirmed-event lifecycles are generated as executable services.
            - A selected grammar also contributes a neutral textual full-stack bridge so GLSP/Theia/VS Code/Eclipse hosts can consume appearance, hover, validation, quick fixes, events, EventFlow and gamification.
            - The client/server split permits standalone, web, VS Code, Eclipse Theia or Eclipse Desktop integration glue.
            - Client and server receive the same semantic-scope `HydraRuntimeActivation`/`hydraRuntimeActivation` gate with an activation epoch. Re-enabling performs a silent rebaseline so changes made while disabled are never replayed as HYDRA events.
            - GLSP GModels are derived views and never replace the HYDRA semantic model.
            - Injection profile: `%s`.
            """.formatted(metamodel.id(), editor.id(), diagramType, serverLanguage, request.profile().id());}
}
