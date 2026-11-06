package com.example.editor.grammar.provider.langium;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.provider.*;
import com.example.editor.integration.adapter.langium.LangiumTechnologyAdapter;

/** Langium provider with generated Node LSP, browser worker and validation CLI hosts. */
@Component
public class LangiumGrammarProvider extends AbstractNeutralGrammarProvider {
    private static final Pattern GRAMMAR_NAME=Pattern.compile("(?m)^\\s*grammar\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    /** Descripción: Inicializa una instancia de LangiumGrammarProvider con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public LangiumGrammarProvider(GrammarProperties properties) { super(properties); }

    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GrammarProviderDescriptor descriptor() {
        return new GrammarProviderDescriptor("langium", GrammarTechnology.LANGIUM, "Langium", "hydra-provider-1.0",
                new GrammarProviderCapabilities(EnumSet.of(
                        GrammarProviderCapability.ANALYZE, GrammarProviderCapability.CODE_ACTIONS,
                        GrammarProviderCapability.SEMANTIC_MAPPING, GrammarProviderCapability.PROJECT_GENERATION,
                        GrammarProviderCapability.TEXTUAL_RUNTIME, GrammarProviderCapability.COMPLETION,
                        GrammarProviderCapability.HOVER, GrammarProviderCapability.DEFINITION, GrammarProviderCapability.REFERENCES,
                        GrammarProviderCapability.RENAME, GrammarProviderCapability.FORMATTING, GrammarProviderCapability.FOLDING,
                        GrammarProviderCapability.DOCUMENT_SYMBOLS, GrammarProviderCapability.SEMANTIC_TOKENS,
                        GrammarProviderCapability.LANGUAGE_SERVER, GrammarProviderCapability.BROWSER_WORKER,
                        GrammarProviderCapability.STANDALONE_WEB, GrammarProviderCapability.STANDALONE_DESKTOP),
                        List.of("TypeScript"), List.of("npm"),
                        EnumSet.of(GrammarProviderCapability.ANALYZE, GrammarProviderCapability.CODE_ACTIONS,
                                GrammarProviderCapability.SEMANTIC_MAPPING, GrammarProviderCapability.PROJECT_GENERATION,
                                GrammarProviderCapability.TEXTUAL_RUNTIME),
                        EnumSet.of(GrammarProviderCapability.COMPLETION, GrammarProviderCapability.HOVER,
                                GrammarProviderCapability.DEFINITION, GrammarProviderCapability.REFERENCES, GrammarProviderCapability.RENAME,
                                GrammarProviderCapability.FORMATTING, GrammarProviderCapability.FOLDING,
                                GrammarProviderCapability.DOCUMENT_SYMBOLS, GrammarProviderCapability.SEMANTIC_TOKENS,
                                GrammarProviderCapability.LANGUAGE_SERVER, GrammarProviderCapability.BROWSER_WORKER,
                                GrammarProviderCapability.STANDALONE_WEB, GrammarProviderCapability.STANDALONE_DESKTOP)),
                List.of("FAST","NORMAL","FULL"),
                Map.of("nativeValidation","LANGIUM_CLI_AND_TSC","deployment.browser","FULL","deployment.desktop","FULL",
                        "deployment.offlinePwa","SUPPORTED_WHEN_BUNDLED","generatedHosts","NODE_LSP,BROWSER_WORKER,CLI"));
    }

    /** Descripción: Crea o prepara el resultado de «generateProject» dentro de LangiumGrammarProvider. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GeneratedGrammarProject generateProject(GrammarProjectGenerationRequest request) {
        if(request.documents()==null||request.documents().isEmpty()) throw new IllegalArgumentException("Langium project generation requires source documents");
        String packageName = npmName(request.grammar().id().isBlank()?"hydra-langium-language":request.grammar().id());
        String projectName = tsIdentifier(request.grammar().name().isBlank()?request.grammar().id():request.grammar().name());
        String grammarName = declaredGrammarName(request,projectName);
        String languageId = extensionId(request.grammar().id());
        String extension = "."+languageId;
        String langiumVersion = request.profile().runtimeVersion();
        Map<String,String> scaffold = new LinkedHashMap<>();
        scaffold.put("package.json", packageJson(packageName,langiumVersion));
        scaffold.put("langium-config.json", config(projectName,languageId,entryPath(request),extension));
        scaffold.put("tsconfig.json", nodeTsConfig());
        scaffold.put("tsconfig.browser.json", browserTsConfig());
        scaffold.put("src/language/"+languageId+"-module.ts", serviceModule(projectName,grammarName,languageId));
        scaffold.put("src/language/main.ts", nodeLanguageServer(projectName,languageId,request.profile().id(),langiumVersion));
        scaffold.put("src/language/browser-worker.ts", browserWorker(projectName,languageId,langiumVersion));
        scaffold.put("src/cli/main.ts", cli(projectName,languageId));
        scaffold.put("test/generated-hosts.test.ts", smoke(request,projectName,grammarName,languageId));
        scaffold.put("HYDRA_PROVENANCE.json", provenance(request,projectName,languageId));
        return project(request, scaffold);
    }

    /** Descripción: Procesa la operación «runtimeDescriptor» coordinando las responsabilidades de LangiumGrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GrammarRuntimeDescriptor runtimeDescriptor(GrammarDefinitionModel grammar, GrammarToolchainProfile profile) {
        boolean browser="true".equalsIgnoreCase(profile.settings().getOrDefault("browserWorker","false"));
        return new GrammarRuntimeDescriptor(browser?"LANGIUM_BROWSER_WORKER_OR_LSP":"LANGIUM_LSP", grammar.entryRule(),
                List.of("."+extensionId(grammar.id())), "node ./dist/language/main.js --stdio", browser?"./dist-browser/hydra-language-worker.js":"",
                Map.of("provider","LANGIUM","runtimeProfileKey","langium","providerVersion",profile.providerVersion(),
                        "offlinePwa",Boolean.toString(browser),"cli","./dist/cli/main.js","generatedServices","true"));
    }
    /** Descripción: Ejecuta la operación «syntaxMetadata» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GrammarSyntaxMetadata syntaxMetadata() { return new GrammarSyntaxMetadata("langium", List.of("grammar","entry","terminal","hidden","interface","type","returns","import","fragment"), Map.of("rule","type","terminal","regexp"), List.of(".langium")); }

    /** Descripción: Ejecuta la operación «packageJson» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: packageName: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String packageJson(String packageName,String version){
        return "{\n"+
                "  \"name\": \""+escape(packageName)+"\",\n"+
                "  \"private\": true,\n  \"type\": \"module\",\n"+
                "  \"scripts\": {\n"+
                "    \"langium:generate\": \"langium generate\",\n"+
                "    \"build:node\": \"tsc -p tsconfig.json\",\n"+
                "    \"build:browser\": \"tsc -p tsconfig.browser.json && esbuild dist-browser/language/browser-worker.js --bundle --format=esm --platform=browser --target=es2022 --outfile=dist-browser/hydra-language-worker.js\",\n"+
                "    \"build\": \"npm run langium:generate && npm run build:node && npm run build:browser\",\n"+
                "    \"test\": \"vitest run\",\n"+
                "    \"language-server\": \"node dist/language/main.js --stdio\",\n"+
                "    \"cli\": \"node dist/cli/main.js\"\n"+
                "  },\n"+
                "  \"dependencies\": {\"langium\": \""+escape(version)+"\", \"vscode-languageserver\": \""+LangiumTechnologyAdapter.languageServerVersion(version)+"\", \"vscode-uri\": \"^3.1.0\"},\n"+
                "  \"devDependencies\": {\"@types/node\": \"^22.0.0\", \"esbuild\": \"0.28.2\", \"langium-cli\": \""+escape(version)+"\", \"typescript\": \"^5.8.0\", \"vite\": \"8.3.5\", \"vitest\": \"5.0.5\"}\n}\n";
    }
    /** Descripción: Ejecuta la operación «config» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: projectName: dato de entrada necesario para la operación; languageId: identificador que delimita el recurso objetivo; grammarPath: ruta o ubicación utilizada por la operación; extension: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método; «out» es obligatorio en el esquema de langium-cli y coincide con el '../generated/module.js' que importa el módulo de servicios. */ private static String config(String projectName,String languageId,String grammarPath,String extension){
        return "{\n  \"projectName\": \""+escape(projectName)+"\",\n  \"languages\": [{\"id\": \""+escape(languageId)+"\", \"grammar\": \""+escape(grammarPath)+"\", \"fileExtensions\": [\""+escape(extension)+"\"]}],\n  \"out\": \"src/generated\"\n}\n";
    }
    /** Descripción: Ejecuta la operación «nodeTsConfig» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String nodeTsConfig(){return "{\n  \"compilerOptions\": {\"target\": \"ES2022\", \"module\": \"NodeNext\", \"moduleResolution\": \"NodeNext\", \"strict\": true, \"skipLibCheck\": true, \"outDir\": \"dist\", \"lib\": [\"ES2022\"], \"types\": [\"node\"]},\n  \"include\": [\"src/generated/**/*.ts\", \"src/language/"+"**/*.ts\", \"src/cli/**/*.ts\"],\n  \"exclude\": [\"src/language/browser-worker.ts\"]\n}\n";}
    /** Descripción: Ejecuta la operación «browserTsConfig» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String browserTsConfig(){return "{\n  \"compilerOptions\": {\"target\": \"ES2022\", \"module\": \"NodeNext\", \"moduleResolution\": \"NodeNext\", \"strict\": true, \"skipLibCheck\": true, \"outDir\": \"dist-browser\", \"lib\": [\"ES2022\", \"WebWorker\"]},\n  \"include\": [\"src/generated/**/*.ts\", \"src/language/*-module.ts\", \"src/language/browser-worker.ts\"]\n}\n";}
    /** Descripción: Ejecuta la operación «serviceModule» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: projectName: dato de entrada necesario para la operación; grammarName: nombre declarado en la gramática, con el que langium generate nombra el módulo del lenguaje (el compartido usa projectName); languageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String serviceModule(String projectName,String grammarName,String languageId){
        return "import { inject, type Module } from 'langium';\n"+
                "import { createDefaultModule, createDefaultSharedModule, type DefaultSharedModuleContext, type LangiumServices, type LangiumSharedServices, type PartialLangiumServices } from 'langium/lsp';\n"+
                "import { "+grammarName+"GeneratedModule, "+projectName+"GeneratedSharedModule } from '../generated/module.js';\n\n"+
                "export type "+projectName+"AddedServices = Record<string, never>;\n"+
                "export type "+projectName+"Services = LangiumServices & "+projectName+"AddedServices;\n"+
                "export const "+projectName+"Module: Module<"+projectName+"Services, PartialLangiumServices & "+projectName+"AddedServices> = {};\n\n"+
                "export function create"+projectName+"Services(context: DefaultSharedModuleContext): { shared: LangiumSharedServices; "+languageId+": "+projectName+"Services } {\n"+
                "  const shared = inject(createDefaultSharedModule(context), "+projectName+"GeneratedSharedModule);\n"+
                "  const services = inject(createDefaultModule({ shared }), "+grammarName+"GeneratedModule, "+projectName+"Module);\n"+
                "  shared.ServiceRegistry.register(services);\n"+
                "  return { shared, "+languageId+": services };\n}\n";
    }
    /** Descripción: Ejecuta la operación «nodeLanguageServer» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: projectName: dato de entrada necesario para la operación; languageId: identificador que delimita el recurso objetivo; profile: archivo o contenido que debe procesarse; langiumVersion: línea de Langium, que fija la entrada de vscode-languageserver. Salida: devuelve el resultado definido por el contrato del método. */ private static String nodeLanguageServer(String projectName,String languageId,String profile,String langiumVersion){
        return "import { startLanguageServer } from 'langium/lsp';\n"+
                "import { NodeFileSystem } from 'langium/node';\n"+
                "import { createConnection, ProposedFeatures } from 'vscode-languageserver/"+languageServerEntry("node",langiumVersion)+"';\n"+
                "import { create"+projectName+"Services } from './"+languageId+"-module.js';\n\n"+
                "export const hydraToolchainProfile = '"+escapeTs(profile)+"';\n"+
                "const connection = createConnection(ProposedFeatures.all);\n"+
                "const { shared } = create"+projectName+"Services({ connection, ...NodeFileSystem });\n"+
                "startLanguageServer(shared);\n";
    }
    /** Descripción: Ejecuta la operación «browserWorker» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: projectName: dato de entrada necesario para la operación; languageId: identificador que delimita el recurso objetivo; langiumVersion: línea de Langium, que fija la entrada de vscode-languageserver. Salida: devuelve el resultado definido por el contrato del método. */ private static String browserWorker(String projectName,String languageId,String langiumVersion){
        return "import { EmptyFileSystem } from 'langium';\n"+
                "import { startLanguageServer } from 'langium/lsp';\n"+
                "import { BrowserMessageReader, BrowserMessageWriter, createConnection } from 'vscode-languageserver/"+languageServerEntry("browser",langiumVersion)+"';\n"+
                "import { create"+projectName+"Services } from './"+languageId+"-module.js';\n\n"+
                "declare const self: DedicatedWorkerGlobalScope;\n"+
                "const connection = createConnection(new BrowserMessageReader(self), new BrowserMessageWriter(self));\n"+
                "const { shared } = create"+projectName+"Services({ connection, ...EmptyFileSystem });\n"+
                "startLanguageServer(shared);\n";
    }
    /** Descripción: Ejecuta la operación «cli» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: projectName: dato de entrada necesario para la operación; languageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String cli(String projectName,String languageId){
        return "import { NodeFileSystem } from 'langium/node';\n"+
                "import { URI } from 'vscode-uri';\n"+
                "import { create"+projectName+"Services } from '../language/"+languageId+"-module.js';\n\n"+
                "const file = process.argv[2];\n"+
                "if (!file) { console.error('Usage: node dist/cli/main.js <model-file>'); process.exitCode = 2; } else {\n"+
                "  const { shared } = create"+projectName+"Services(NodeFileSystem);\n"+
                "  const document = await shared.workspace.LangiumDocuments.getOrCreateDocument(URI.file(file));\n"+
                "  await shared.workspace.DocumentBuilder.build([document], { validation: true });\n"+
                "  const diagnostics = document.diagnostics ?? [];\n"+
                "  for (const diagnostic of diagnostics) console.error(`${diagnostic.severity ?? ''} ${diagnostic.message}`);\n"+
                "  if (diagnostics.some(diagnostic => (diagnostic.severity ?? 0) === 1)) process.exitCode = 1;\n"+
                "}\n";
    }
    /** Descripción: Ejecuta la operación «smoke» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; projectName: dato de entrada necesario para la operación; grammarName: nombre declarado en la gramática; languageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String smoke(GrammarProjectGenerationRequest request,String projectName,String grammarName,String languageId){
        return "import { describe, expect, it } from 'vitest';\n"+
                "import { readFileSync } from 'node:fs';\n"+
                "describe('HYDRA generated Langium hosts', () => {\n"+
                "  it('keeps the toolchain pinned and emits real service hosts', () => {\n"+
                "    expect('"+escapeTs(request.profile().id())+"').not.toContain('auto');\n"+
                "    expect(readFileSync('src/language/main.ts','utf8')).toContain('startLanguageServer');\n"+
                "    expect(readFileSync('src/language/browser-worker.ts','utf8')).toContain('BrowserMessageReader');\n"+
                "    expect(readFileSync('src/language/"+languageId+"-module.ts','utf8')).toContain('"+grammarName+"GeneratedModule').toContain('"+projectName+"GeneratedSharedModule');\n"+
                "  });\n"+
                "});\n";
    }
    /** Descripción: Resuelve la entrada de vscode-languageserver: la línea 10 (Langium 4) solo la publica como «./node»/«./browser» en su mapa exports; la 9 (Langium 3), como fichero .js. Entradas: host: «node» o «browser»; langiumVersion: versión de Langium declarada. Salida: subruta importable. */ private static String languageServerEntry(String host,String langiumVersion){return langiumVersion.startsWith("3.")?host+".js":host;}
    /** Descripción: Ejecuta la operación «entryPath» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String entryPath(GrammarProjectGenerationRequest request){return request.documents().stream().filter(GrammarSourceDocument::entryPoint).findFirst().orElse(request.documents().get(0)).relativePath();}
    /** Descripción: Lee el nombre declarado («grammar X») en el documento de entrada. Entradas: request: solicitud de generación; fallback: nombre si la entrada no lo declara. Salida: nombre de la gramática. */ private static String declaredGrammarName(GrammarProjectGenerationRequest request,String fallback){GrammarSourceDocument entry=request.documents().stream().filter(GrammarSourceDocument::entryPoint).findFirst().orElse(request.documents().get(0));Matcher matcher=GRAMMAR_NAME.matcher(entry.content()==null?"":entry.content());return matcher.find()?matcher.group(1):fallback;}
    /** Descripción: Ejecuta la operación «npmName» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String npmName(String value){String result=(value==null?"":value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+","-").replaceAll("^-+|-+$","");return result.isBlank()?"hydra-langium-language":result;}
    /** Descripción: Ejecuta la operación «extensionId» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String extensionId(String value){String result=(value==null?"":value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","");return result.isBlank()?"dsl":result;}
    /** Descripción: Ejecuta la operación «tsIdentifier» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String tsIdentifier(String value){String[] parts=(value==null?"":value).split("[^A-Za-z0-9]+");StringBuilder out=new StringBuilder();for(String part:parts)if(!part.isBlank())out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).replaceAll("[^A-Za-z0-9]",""));String result=out.toString();if(result.isBlank())result="HydraLanguage";if(Character.isDigit(result.charAt(0)))result="Hydra"+result;return result;}
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    /** Descripción: Ejecuta la operación «escapeTs» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String escapeTs(String value){return value==null?"":value.replace("\\","\\\\").replace("'","\\'").replace("\n"," ").replace("\r"," ");}
    /** Descripción: Ejecuta la operación «provenance» correspondiente a la responsabilidad de LangiumGrammarProvider. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; projectName: dato de entrada necesario para la operación; languageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String provenance(GrammarProjectGenerationRequest request,String projectName,String languageId){return "{\n  \"grammarId\": \""+escape(request.grammar().id())+"\",\n  \"sourceHash\": \""+GrammarHashing.sources(request.documents())+"\",\n  \"toolchainProfile\": \""+escape(request.profile().id())+"\",\n  \"providerVersion\": \""+escape(request.profile().providerVersion())+"\",\n  \"projectName\": \""+escape(projectName)+"\",\n  \"languageId\": \""+escape(languageId)+"\",\n  \"generatedHosts\": [\"NODE_LSP\", \"BROWSER_WORKER\", \"CLI\"]\n}\n";}
}
