package com.example.editor.integration.adapter.langium;

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
import com.example.editor.integration.compiler.TechnologyAdapterSupport;
import com.example.editor.integration.compiler.TechnologyAdapterTextualCustomizationSupport;
import com.example.editor.integration.compiler.TechnologyAdapterRuntimeLifecycleSupport;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarTechnology;
import tools.jackson.databind.json.JsonMapper;

/** Genera un paquete Langium/TypeScript y una extensión VS Code opcional. */
@Component
public class LangiumTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.of(
            IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.EXPRESSIONS,
            IntegrationArtifactKind.VALIDATIONS, IntegrationArtifactKind.ACTIONS,
            IntegrationArtifactKind.OPERATION_BEHAVIORS, IntegrationArtifactKind.EVENT_DEFINITIONS,
            IntegrationArtifactKind.EVENT_BINDINGS, IntegrationArtifactKind.EVENT_FLOWS,
            IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, IntegrationArtifactKind.GAMIFICATION_BINDINGS,
            IntegrationArtifactKind.GRAMMAR, IntegrationArtifactKind.TEXTUAL_EDITOR_DEFINITION, IntegrationArtifactKind.TEXTUAL_SYNTAX_STYLE,
            IntegrationArtifactKind.CODE_GENERATION);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de LangiumTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public LangiumTechnologyAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.LANGIUM;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}
    /** Descripción: Declara que este adaptador puede compilar tanto desde un metamodelo como directamente desde una gramática neutral compatible. Entradas: no requiere parámetros de entrada. Salida: raíces semánticas autónomas admitidas por el adaptador. */ @Override public Set<IntegrationArtifactKind> semanticRootKinds(){return Set.of(IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.GRAMMAR);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = request.find(IntegrationArtifactKind.METAMODEL)
                    .map(value -> mapper.readValue(value.content(), MetaModel.class)).orElse(null);
            GrammarDefinitionModel grammarDefinition = request.find(IntegrationArtifactKind.GRAMMAR)
                    .map(value -> mapper.readValue(value.content(), GrammarDefinitionModel.class)).orElse(null);
            if(metamodel == null && grammarDefinition == null) throw new TechnologyCompilationException("Langium requires METAMODEL or GRAMMAR");
            if(metamodel == null && grammarDefinition.technology() != GrammarTechnology.LANGIUM) throw new TechnologyCompilationException("Grammar technology does not match Langium");
            String semanticId = metamodel == null ? grammarDefinition.id() : metamodel.id();
            String semanticName = metamodel == null ? grammarDefinition.name() : metamodel.name();
            String root = TechnologyAdapterSupport.generatedRoot(request, semanticId + "-langium");
            String languageName = TechnologyAdapterSupport.javaType(request.profile().target().settings().get("languageName"),
                    metamodel == null ? grammarLanguageName(grammarDefinition) : TechnologyAdapterSupport.javaType(semanticName, "HydraDsl"));
            String packageName = TechnologyAdapterGenerationSupport.npmPackage(request.profile().target().settings().get("packageName"),
                    "@hydra-generated/" + TechnologyAdapterSupport.slug(semanticId) + "-langium");
            String extension = request.profile().target().settings().getOrDefault("fileExtension", TechnologyAdapterSupport.slug(semanticId));
            String langiumVersion = request.profile().target().settings().getOrDefault("langiumVersion", "4.3.0");
            String typescriptVersion = request.profile().target().settings().getOrDefault("typescriptVersion", "6.0.2");
            String sourceRoot = root + "/src/language";
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            boolean textualCustomization = request.find(IntegrationArtifactKind.GRAMMAR).isPresent();
            files.add(TechnologyAdapterSupport.text(root + "/package.json", "application/json", packageJson(packageName, languageName, extension, langiumVersion, typescriptVersion, textualCustomization), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/tsconfig.json", "application/json", tsconfig(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/langium-config.json", "application/json", langiumConfig(languageName, extension), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/" + languageName.toLowerCase() + ".langium", "text/plain",
                    (metamodel == null ? grammarSource(grammarDefinition) : grammar(metamodel, languageName)), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/" + languageName.toLowerCase() + "-module.ts", "text/typescript",
                    moduleSource(languageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/" + languageName.toLowerCase() + "-validator.ts", "text/typescript",
                    validatorSource(languageName, metamodel != null), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-code-action-provider.ts", "text/typescript", codeActionSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-operation-service.ts", "text/typescript", operationSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-event-bridge.ts", "text/typescript", eventSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-runtime-activation.ts", "text/typescript",
                        TechnologyAdapterGenerationSupport.typescriptRuntimeActivationSource(request), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-semantic-runtime.ts", "text/typescript",
                        TechnologyAdapterRuntimeLifecycleSupport.typescriptSemanticRuntimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-language-runtime.ts", "text/typescript", languageRuntimeSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(sourceRoot + "/hydra-document-update-handler.ts", "text/typescript", documentUpdateHandlerSource(), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/main.ts", "text/typescript", mainSource(languageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(root + "/src/extension.ts", "text/typescript", extensionSource(languageName), sourceIds));
            if(textualCustomization){var customization=TechnologyAdapterTextualCustomizationSupport.customization(request,mapper);files.addAll(TechnologyAdapterTextualCustomizationSupport.neutralFiles(root,request,mapper));files.add(TechnologyAdapterSupport.text(root + "/syntaxes/hydra-custom.tmLanguage.json","application/json",textMateCustomization(languageName,customization),sourceIds));}
            files.add(TechnologyAdapterSupport.text(root + "/src/language/" + languageName.toLowerCase() + ".test.ts", "text/typescript", parserTestSource(languageName, metamodel != null), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown", readme(request, semanticId, packageName, metamodel != null), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "Langium"));
            List<IntegrationDependencyChange> dependencies = List.of(
                    dependency("langium", langiumVersion), dependency("vscode-languageserver", "9.0.1"),
                    dependency("vscode-languageserver-textdocument", "1.0.12"), dependency("typescript", typescriptVersion));
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("install-langium-dependencies", "Install pinned Langium dependencies", root,
                            List.of("npm", "install", "--ignore-scripts"), 0, true),
                    new IntegrationVerificationStep("generate-langium-language", "Generate Langium AST, compile TypeScript and execute parser tests", root,
                            List.of("npm", "run", "check"), 0, true));
            return new TechnologyCompilationResult(IntegrationTechnology.LANGIUM, files, dependencies,
                    List.of("Run npm install", "Run npm run langium:generate", "Package or launch the generated language server/VS Code extension"),
                    List.of("Rollback by removing the generated npm package", "Restore host package manifests from HYDRA backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "TypeScript/Langium",
                            "moduleRoot", root, "packageName", packageName, "languageName", languageName, "fileExtension", extension, "langiumVersion", langiumVersion));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate Langium integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String coordinate, String version){return new IntegrationDependencyChange("npm", coordinate, version, "dependency", IntegrationDependencyAction.ADD);}
    /** Descripción: Ejecuta la operación «packageJson» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación; langiumVersion: dato de entrada necesario para la operación; typescriptVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String packageJson(String packageName, String languageName, String extension, String langiumVersion, String typescriptVersion, boolean textualCustomization){
        String grammarContribution = textualCustomization
                ? "\n                \"grammars\": [{\"language\":\"" + languageName.toLowerCase() + "\",\"scopeName\":\"source." + languageName.toLowerCase() + ".hydra\",\"path\":\"./syntaxes/hydra-custom.tmLanguage.json\"}],"
                : "";
        return """
            {
              "name": "%s",
              "version": "1.0.0",
              "type": "module",
              "private": true,
              "scripts": {
                "langium:generate": "langium generate",
                "build": "tsc -b",
                "test": "vitest run",
                "check": "npm run langium:generate && npm run build && npm test"
              },
              "dependencies": {
                "langium": "^%s",
                "vscode-languageserver": "^9.0.1",
                "vscode-languageserver-textdocument": "^1.0.12"
              },
              "devDependencies": {"typescript": "^%s", "vitest": "^4.1.10", "langium-cli": "^%s"},
              "contributes": {
                "languages": [{"id": "%s", "extensions": [".%s"], "aliases": ["%s"]}],%s
                "commands": [{"command": "hydra.toggleRuntime", "title": "HYDRA: Enable/disable runtime"}]
              }
            }
            """.formatted(packageName, langiumVersion, typescriptVersion, langiumVersion, languageName.toLowerCase(), extension, languageName, grammarContribution);
    }
    /** Descripción: Ejecuta la operación «textMateCustomization» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: languageName: dato de entrada necesario para la operación; c: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textMateCustomization(String languageName,com.example.editor.grammar.core.GrammarTextualEditorCustomization c){String keywords=java.util.Arrays.stream(c.keywords().split(",")).filter(v->!v.isBlank()).map(java.util.regex.Pattern::quote).reduce((a,b)->a+"|"+b).orElse("(?!)");return """
            {"name":"HYDRA %s","scopeName":"source.%s.hydra","patterns":[{"name":"keyword.control.hydra","match":"\\b(?:%s)\\b"},{"name":"string.quoted.double.hydra","begin":"\"","end":"\""},{"name":"comment.line.double-slash.hydra","match":"//.*$"},{"name":"constant.numeric.hydra","match":"\\b[0-9]+(?:\\.[0-9]+)?\\b"}]}
            """.formatted(languageName,languageName.toLowerCase(),keywords);}
    /** Descripción: Ejecuta la operación «tsconfig» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String tsconfig(){return """
            {"compilerOptions":{"target":"ES2022","module":"NodeNext","moduleResolution":"NodeNext","strict":true,"outDir":"lib","rootDir":"src","declaration":true,"skipLibCheck":true},"include":["src/**/*.ts"]}
            """;}
    /** Descripción: Ejecuta la operación «langiumConfig» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: languageName: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String langiumConfig(String languageName, String extension){return """
            {"projectName":"%s","languages":[{"id":"%s","grammar":"src/language/%s.langium","fileExtensions":[".%s"],"textMate":{"out":"syntaxes/%s.tmLanguage.json"}}],"out":"src/language/generated"}
            """.formatted(languageName, languageName.toLowerCase(), languageName.toLowerCase(), extension, languageName.toLowerCase());}

    /** Descripción: Ejecuta la operación «grammar» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: metamodel: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String grammar(MetaModel metamodel, String languageName){
        StringBuilder source = new StringBuilder();
        source.append("grammar ").append(languageName).append("\n\n");
        source.append("entry Model: elements+=Element*;\n\n");
        source.append("Element: ");
        source.append(metamodel.classes().isEmpty() ? "GenericElement" : String.join(" | ", metamodel.classes().stream().map(MetaClass::name).toList()));
        source.append(";\n\n");
        if(metamodel.classes().isEmpty()){source.append("GenericElement: 'element' name=ID;\n\n");}
        for(MetaClass metaClass : metamodel.classes()){
            source.append(metaClass.name()).append(": '").append(metaClass.name()).append("' name=ID");
            for(MetaAttribute attribute : metaClass.attributes()){source.append(attributeFragment(attribute, metamodel));}
            for(MetaRelation relation : metamodel.relations()){
                if(relation.sourceTypeId().equals(metaClass.id()) && structural(relation.kind())){source.append(relationFragment(relation, metamodel));}
            }
            source.append(";\n\n");
        }
        metamodel.enums().forEach(metaEnum -> source.append(metaEnum.name()).append(": ")
                .append(String.join(" | ", metaEnum.literals().stream().map(value -> "'" + value.name() + "'").toList())).append(";\n\n"));
        source.append("hidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\nterminal STRING: /\"[^\"]*\"/;\nterminal INT returns number: /[0-9]+/;\n");
        return source.toString();
    }
    /** Descripción: Ejecuta la operación «structural» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean structural(MetaRelationKind kind){return kind == MetaRelationKind.REFERENCE || kind == MetaRelationKind.CONTAINMENT || kind == MetaRelationKind.AGGREGATION;}
    /** Descripción: Ejecuta la operación «attributeFragment» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: attribute: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attributeFragment(MetaAttribute attribute, MetaModel metamodel){
        String terminal = langiumType(attribute.type(), metamodel);
        boolean many = attribute.multiplicity() != null && (attribute.multiplicity().unbounded() || attribute.multiplicity().upperBound() != null && attribute.multiplicity().upperBound() > 1);
        boolean optional = attribute.multiplicity() == null || attribute.multiplicity().lowerBound() == 0;
        String assignment = attribute.name() + (many ? "+=" : "=") + terminal;
        if(many){return " ('" + attribute.name() + "' " + assignment + ")*";}
        if(optional){return " ('" + attribute.name() + "' " + assignment + ")?";}
        return " '" + attribute.name() + "' " + assignment;
    }
    /** Descripción: Ejecuta la operación «relationFragment» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: relation: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relationFragment(MetaRelation relation, MetaModel metamodel){
        String target = metamodel.classes().stream().filter(value -> value.id().equals(relation.targetTypeId())).map(MetaClass::name).findFirst().orElse("Element");
        boolean many = relation.targetMultiplicity() != null && (relation.targetMultiplicity().unbounded() || relation.targetMultiplicity().upperBound() != null && relation.targetMultiplicity().upperBound() > 1);
        boolean optional = relation.targetMultiplicity() == null || relation.targetMultiplicity().lowerBound() == 0;
        String assignment = relation.name() + (many ? "+=" : "=") + "[" + target + ":ID]";
        if(many){return " ('" + relation.name() + "' " + assignment + ")*";}
        if(optional){return " ('" + relation.name() + "' " + assignment + ")?";}
        return " '" + relation.name() + "' " + assignment;
    }
    /** Descripción: Ejecuta la operación «langiumType» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: type: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String langiumType(MetaTypeReference type, MetaModel metamodel){
        if(type == null){return "STRING";}
        if(type.kind() == MetaTypeReferenceKind.ENUM){return metamodel.enums().stream().filter(value -> value.id().equals(type.targetId())).map(value -> value.name()).findFirst().orElse("ID");}
        if(type.kind() != MetaTypeReferenceKind.PRIMITIVE){return "STRING";}
        try{
            MetaPrimitiveType primitive = MetaPrimitiveType.valueOf(type.targetId());
            return switch(primitive){case BOOLEAN -> "('true'|'false')"; case BYTE, SHORT, INTEGER, LONG, BIG_INTEGER -> "INT"; default -> "STRING";};
        }catch(IllegalArgumentException exception){return "STRING";}
    }

    /** Descripción: Genera el módulo Langium y sustituye el DocumentUpdateHandler compartido por el lifecycle HYDRA. Entradas: languageName. Salida: fuente TypeScript del módulo. */ private String moduleSource(String languageName){return """
            import type { LangiumServices, Module, PartialLangiumServices } from 'langium';
            import { createDefaultModule, createDefaultSharedModule, inject } from 'langium/lsp';
            import { %sGeneratedModule, %sGeneratedSharedModule } from './generated/module.js';
            import { %sValidator, registerValidationChecks } from './%s-validator.js';
            import { HydraCodeActionProvider } from './hydra-code-action-provider.js';
            import { HydraDocumentUpdateHandler } from './hydra-document-update-handler.js';
            export type %sAddedServices={validation:{%sValidator:%sValidator},lsp:{CodeActionProvider:HydraCodeActionProvider}};
            export type %sServices=LangiumServices & %sAddedServices;
            export const %sModule:Module<PartialLangiumServices & %sAddedServices>={validation:{%sValidator:()=>new %sValidator()},lsp:{CodeActionProvider:()=>new HydraCodeActionProvider()}};
            const HydraSharedModule:any={lsp:{DocumentUpdateHandler:(services:any)=>new HydraDocumentUpdateHandler(services)}};
            export function create%sServices(context:any){const shared=inject(createDefaultSharedModule(context),%sGeneratedSharedModule,HydraSharedModule);const services=inject(createDefaultModule({shared}),%sGeneratedModule,%sModule);shared.ServiceRegistry.register(services);registerValidationChecks(services);return {shared,%s:services};}
            """.formatted(languageName, languageName, languageName, languageName.toLowerCase(), languageName, languageName, languageName,
                    languageName, languageName, languageName, languageName, languageName, languageName, languageName, languageName, languageName, languageName, languageName, languageName);}
    /** Descripción: Genera el límite de validación Langium sin asumir una regla Model artificial en grammar-first. Entradas: languageName: nombre del lenguaje; metamodelBacked: indica si la gramática fue derivada del metamodelo canónico. Salida: fuente TypeScript del validador. */ private String validatorSource(String languageName, boolean metamodelBacked){
        if(!metamodelBacked) return """
            import type { ValidationChecks } from 'langium';
            import type { %sAstType } from './generated/ast.js';
            import type { %sServices } from './%s-module.js';
            export function registerValidationChecks(services:%sServices):void { const checks:ValidationChecks<%sAstType>={}; services.validation.ValidationRegistry.register(checks); }
            export class %sValidator { }
            """.formatted(languageName, languageName, languageName.toLowerCase(), languageName, languageName, languageName);
        return """
            import type { ValidationAcceptor, ValidationChecks } from 'langium';
            import type { %sAstType, Model } from './generated/ast.js';
            import type { %sServices } from './%s-module.js';
            export function registerValidationChecks(services:%sServices){const checks:ValidationChecks<%sAstType>={Model:services.validation.%sValidator.checkCanonicalModel};services.validation.ValidationRegistry.register(checks);}
            export class %sValidator { checkCanonicalModel(model:Model,accept:ValidationAcceptor):void { if(model == null){accept('warning','HYDRA canonical model is unavailable',{node:model,code:'hydra.canonical.validation'}); } } }
            """.formatted(languageName, languageName, languageName.toLowerCase(), languageName, languageName, languageName, languageName);
    }
    /** Descripción: Ejecuta la operación «codeActionSource» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String codeActionSource(){return """
            import type { LangiumDocument } from 'langium';
            import type { CodeAction, CodeActionParams } from 'vscode-languageserver';
            export class HydraCodeActionProvider { getCodeActions(document:LangiumDocument,params:CodeActionParams):CodeAction[]{return (document.diagnostics??[]).filter(d=>d.code==='hydra.canonical.validation').map(d=>({title:'Apply HYDRA quick fix',kind:'quickfix',diagnostics:[d],data:{uri:document.uri.toString(),diagnostic:d.code,range:params.range}}));} }
            """;}
    /** Descripción: Genera el runtime Langium por URI sobre el lifecycle transaccional neutral. Entradas: no requiere parámetros. Salida: fuente TypeScript del runtime. */ private String languageRuntimeSource(){return """
            import {HydraSemanticRuntime,type HydraCommitResult,type HydraDiagnostic,type HydraEvent,type HydraGamificationState,type HydraRuntimeSnapshot,type HydraSemanticEngine} from './hydra-semantic-runtime.js';
            export type {HydraCommitResult,HydraDiagnostic,HydraEvent,HydraGamificationState,HydraRuntimeSnapshot};
            export interface HydraLanguageRuntimeAdapter{validate?(state:string,trigger:string):HydraDiagnostic[];deriveEvents?(before:string,after:string,trigger:string,sequence:number):HydraEvent[];runEventFlow?(events:HydraEvent[],state:string):HydraEvent[];updateGamification?(previous:HydraGamificationState,events:HydraEvent[],flow:HydraEvent[]):HydraGamificationState;applyQuickFix?(state:string,diagnosticId:string,actionId:string,parameters:Readonly<Record<string,unknown>>):string|Promise<string>;invoke?(state:string,operationId:string,parameters:Readonly<Record<string,unknown>>):{state:string;result:unknown}|Promise<{state:string;result:unknown}>;}
            type Session={runtime:HydraSemanticRuntime;state:string};
            const sessions=new Map<string,Session>();let adapter:HydraLanguageRuntimeAdapter={};
            const empty=():HydraGamificationState=>({points:{},level:'',achievements:[],penalties:0});
            const engine=():HydraSemanticEngine=>({validate:(state,trigger)=>adapter.validate?.(state,trigger)??[],deriveEvents:(before,after,trigger,sequence)=>adapter.deriveEvents?.(before,after,trigger,sequence)??(before===after?[]:[{sequence,type:'MODEL_CHANGED',elementId:'document',payload:{trigger}}]),runEventFlow:(events,state)=>adapter.runEventFlow?.(events,state)??[],updateGamification:(previous,events,flow)=>adapter.updateGamification?.(previous,events,flow)??previous??empty()});
            const session=(uri:string):Session=>{let value=sessions.get(uri);if(!value){value={runtime:new HydraSemanticRuntime(engine()),state:''};sessions.set(uri,value);}return value;};
            export const hydraLanguageRuntime={
              install(value:HydraLanguageRuntimeAdapter){adapter=value??{};sessions.clear();},
              validate(uri:string,state:string,trigger='LANGIUM_DRAFT_VALIDATE'){const current=session(uri);current.state=state??'';return current.runtime.validateHost(current.state,trigger);},
              commit(uri:string,state:string,trigger='LANGIUM_DID_SAVE'){const current=session(uri);current.state=state??'';return current.runtime.synchronize(current.state,trigger);},
              checkpoint(uri:string){return session(uri).runtime.checkpoint();},restore(uri:string,snapshot:HydraRuntimeSnapshot){session(uri).runtime.restore(snapshot);},
              events(uri:string){return session(uri).runtime.events();},gamification(uri:string){return session(uri).runtime.gamification();},release(uri:string){sessions.delete(uri);},
              async applyQuickFix(uri:string,state:string,diagnosticId:string,actionId:string,parameters:Readonly<Record<string,unknown>>){if(!adapter.applyQuickFix)throw new Error('No HYDRA quick-fix adapter is installed.');const current=session(uri),before=current.runtime.checkpoint(),hostBefore=state;try{const next=await adapter.applyQuickFix(hostBefore,diagnosticId,actionId,parameters);current.state=next;return{state:next,commit:current.runtime.synchronize(next,'LANGIUM_QUICK_FIX')};}catch(error){current.state=hostBefore;current.runtime.restore(before);throw error;}},
              async invoke(uri:string,state:string,operationId:string,parameters:Readonly<Record<string,unknown>>){if(!adapter.invoke)throw new Error('No HYDRA operation adapter is installed.');const current=session(uri),before=current.runtime.checkpoint(),hostBefore=state;try{const outcome=await adapter.invoke(hostBefore,operationId,parameters);current.state=outcome.state;const commit=current.runtime.synchronize(outcome.state,'LANGIUM_OPERATION');return{result:outcome.result,state:outcome.state,commit};}catch(error){current.state=hostBefore;current.runtime.restore(before);throw error;}}
            } as const;
            """;}
    /** Descripción: Genera el DocumentUpdateHandler Langium que separa cambios de borrador y commit de guardado. Entradas: no requiere parámetros. Salida: fuente TypeScript del handler. */ private String documentUpdateHandlerSource(){return """
            import type {TextDocumentChangeEvent} from 'vscode-languageserver';
            import type {TextDocument} from 'vscode-languageserver-textdocument';
            import {DefaultDocumentUpdateHandler,type LangiumSharedServices} from 'langium/lsp';
            import {hydraLanguageRuntime} from './hydra-language-runtime.js';
            export class HydraDocumentUpdateHandler extends DefaultDocumentUpdateHandler{
              constructor(services:LangiumSharedServices){super(services);}
              override didChangeContent(change:TextDocumentChangeEvent<TextDocument>):void{super.didChangeContent(change);hydraLanguageRuntime.validate(change.document.uri,change.document.getText(),'LANGIUM_DRAFT_VALIDATE');}
              didSaveDocument(change:TextDocumentChangeEvent<TextDocument>):void{hydraLanguageRuntime.commit(change.document.uri,change.document.getText(),'LANGIUM_DID_SAVE');}
              didCloseDocument(change:TextDocumentChangeEvent<TextDocument>):void{hydraLanguageRuntime.release(change.document.uri);}
            }
            """;}
    /** Descripción: Genera el servicio ejecutable de operaciones Langium con rollback de lifecycle. Entradas: no requiere parámetros. Salida: fuente TypeScript. */ private String operationSource(){return """
            import {hydraLanguageRuntime} from './hydra-language-runtime.js';
            export class HydraOperationService { async invoke(uri:string,state:string,operationId:string,parameters:Record<string,unknown>):Promise<unknown>{return (await hydraLanguageRuntime.invoke(uri,state,operationId,parameters)).result;} }
            """;}
    /** Descripción: Genera la fachada ejecutable de eventos Langium sobre commits confirmados. Entradas: no requiere parámetros. Salida: fuente TypeScript. */ private String eventSource(){return """
            import {hydraLanguageRuntime} from './hydra-language-runtime.js';
            export class HydraEventBridge { publishAfterCommit(uri:string,state:string,trigger='LANGIUM_COMMIT'){return hydraLanguageRuntime.commit(uri,state,trigger);}history(uri:string){return hydraLanguageRuntime.events(uri);} }
            """;}
    /** Descripción: Ejecuta la operación «mainSource» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: languageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mainSource(String languageName){return """
            import { NodeFileSystem } from 'langium/node';
            import { createConnection, ProposedFeatures } from 'vscode-languageserver/node.js';
            import { startLanguageServer } from 'langium/lsp';
            import { create%sServices } from './language/%s-module.js';
            const connection=createConnection(ProposedFeatures.all);const services=create%sServices({connection,...NodeFileSystem});startLanguageServer(services.shared);
            """.formatted(languageName, languageName.toLowerCase(), languageName);}
    /** Host-neutral VS Code/web extension boundary with an explicit runtime toggle hook. */ /** Descripción: Ejecuta la operación «extensionSource» correspondiente a la responsabilidad de LangiumTechnologyAdapter. Entradas: languageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String extensionSource(String languageName){return """
            import { hydraRuntimeActivation } from './language/hydra-runtime-activation.js';
            export const languageId='%s';
            export function toggleHydraRuntime():boolean{return hydraRuntimeActivation.toggle();}
            export function setHydraRuntimeEnabled(enabled:boolean):boolean{return hydraRuntimeActivation.setEnabled(enabled);}
            export function activate(){return {languageId,source:'HYDRA MetaModel 2.1',toggleHydraRuntime,setHydraRuntimeEnabled};}
            export function deactivate():void { hydraRuntimeActivation.setEnabled(false); }
            """.formatted(languageName.toLowerCase());}
    /** Descripción: Genera una prueba reproducible del lenguaje sin asumir que una gramática grammar-first acepte un modelo vacío. Entradas: languageName y origen semántico. Salida: fuente Vitest. */ private String parserTestSource(String languageName, boolean metamodelBacked){
        if(!metamodelBacked) return """
            import { describe, expect, it } from 'vitest';
            import { EmptyFileSystem } from 'langium';
            import { create%sServices } from './%s-module.js';
            describe('generated %s language',()=>{it('creates the grammar-first services',()=>{const services=create%sServices(EmptyFileSystem);expect(services.%s).toBeDefined();});});
            """.formatted(languageName, languageName.toLowerCase(), languageName, languageName, languageName);
        return """
            import { describe, expect, it } from 'vitest';
            import { EmptyFileSystem } from 'langium';
            import { parseHelper } from 'langium/test';
            import type { Model } from './generated/ast.js';
            import { create%sServices } from './%s-module.js';
            describe('generated %s language',()=>{it('parses an empty canonical model',async()=>{const services=create%sServices(EmptyFileSystem);const parse=parseHelper<Model>(services.%s);const document=await parse('');expect(document.parseResult.parserErrors).toHaveLength(0);});});
            """.formatted(languageName, languageName.toLowerCase(), languageName, languageName, languageName);
    }

    /** Descripción: Recupera el documento de entrada de una gramática neutral. Entradas: grammarDefinition. Salida: contenido fuente exacto del entry point. */ private String grammarSource(GrammarDefinitionModel grammarDefinition){
        return grammarDefinition.documents().stream().filter(value -> value.id().equals(grammarDefinition.entryDocumentId()) || value.entryPoint()).findFirst()
                .orElseThrow(() -> new TechnologyCompilationException("Grammar entry document is missing")).content();
    }

    /** Descripción: Obtiene un nombre de lenguaje estable desde la cabecera de la gramática o desde su nombre neutral. Entradas: grammarDefinition. Salida: identificador Java/TypeScript seguro. */ private String grammarLanguageName(GrammarDefinitionModel grammarDefinition){
        var matcher=java.util.regex.Pattern.compile("(?m)^\\s*grammar\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(grammarSource(grammarDefinition));
        return TechnologyAdapterSupport.javaType(matcher.find()?matcher.group(1):grammarDefinition.name(),"HydraDsl");
    }

    /** Descripción: Documenta el origen semántico del paquete Langium generado. Entradas: request, semanticId, packageName y origen. Salida: README reproducible. */ private String readme(TechnologyCompilationRequest request, String semanticId, String packageName, boolean metamodelBacked){return """
            # HYDRA Langium integration

            Generated from canonical semantic scope `%s` (%s).

            - npm package: `%s`.
            - Grammar, validation registry and code actions are generated in TypeScript.
            - `didChangeContent` performs side-effect-free draft validation; `didSaveDocument` is the explicit commit boundary for Events → EventFlow → Gamification.
            - Runtime state is isolated per URI/document and released on close; operation/quick-fix failures restore their full lifecycle checkpoint.
            - The package can be embedded in a web application or packaged as a VS Code language extension.
            - The runtime activation gate is scoped to `semanticScopeId`; grammar-first does not persist an artificial metamodel.
            - Canonical JSON is preserved under `resources/hydra` for traceability and partial-fidelity capabilities.
            - Langium AST nodes are a technology projection; they do not replace HYDRA neutral semantics and do not replace the HYDRA metamodel when one exists.
            - Injection profile: `%s`.
            """.formatted(semanticId, metamodelBacked?"MetaModel 2.1":"Grammar-first", packageName, request.profile().id());}

}
