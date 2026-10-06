package com.example.editor.integration.adapter.xtext;

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

/** Genera un lenguaje Xtext modular a partir de los contratos canónicos de HYDRA. */
@Component
public class XtextTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.of(
            IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.EXPRESSIONS,
            IntegrationArtifactKind.VALIDATIONS, IntegrationArtifactKind.ACTIONS,
            IntegrationArtifactKind.OPERATION_BEHAVIORS, IntegrationArtifactKind.EVENT_DEFINITIONS,
            IntegrationArtifactKind.EVENT_BINDINGS, IntegrationArtifactKind.EVENT_FLOWS,
            IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, IntegrationArtifactKind.GAMIFICATION_BINDINGS,
            IntegrationArtifactKind.GRAMMAR, IntegrationArtifactKind.TEXTUAL_EDITOR_DEFINITION, IntegrationArtifactKind.TEXTUAL_SYNTAX_STYLE,
            IntegrationArtifactKind.CODE_GENERATION);
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de XtextTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public XtextTechnologyAdapter(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.XTEXT;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}
    /** Descripción: Declara que este adaptador puede compilar tanto desde un metamodelo como directamente desde una gramática neutral compatible. Entradas: no requiere parámetros de entrada. Salida: raíces semánticas autónomas admitidas por el adaptador. */ @Override public Set<IntegrationArtifactKind> semanticRootKinds(){return Set.of(IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.GRAMMAR);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = request.find(IntegrationArtifactKind.METAMODEL)
                    .map(value -> mapper.readValue(value.content(), MetaModel.class)).orElse(null);
            GrammarDefinitionModel grammarDefinition = request.find(IntegrationArtifactKind.GRAMMAR)
                    .map(value -> mapper.readValue(value.content(), GrammarDefinitionModel.class)).orElse(null);
            if(metamodel == null && grammarDefinition == null) throw new TechnologyCompilationException("Xtext requires METAMODEL or GRAMMAR");
            if(metamodel == null && grammarDefinition.technology() != GrammarTechnology.XTEXT) throw new TechnologyCompilationException("Grammar technology does not match Xtext");
            String semanticId = metamodel == null ? grammarDefinition.id() : metamodel.id();
            String semanticName = metamodel == null ? grammarDefinition.name() : metamodel.name();
            String root = TechnologyAdapterSupport.generatedRoot(request, semanticId + "-xtext");
            String basePackage = TechnologyAdapterSupport.javaPackage(request.profile().target().settings().get("basePackage"),
                    "com.generated." + TechnologyAdapterSupport.slug(semanticId) + ".xtext");
            String languageName = TechnologyAdapterSupport.javaType(request.profile().target().settings().get("languageName"),
                    metamodel == null ? grammarLanguageName(grammarDefinition) : TechnologyAdapterSupport.javaType(semanticName, "HydraDsl"));
            String languagePackage = basePackage + "." + languageName.toLowerCase();
            String xtextVersion = request.profile().target().settings().getOrDefault("xtextVersion", "2.44.0");
            String javaPath = TechnologyAdapterSupport.javaPath(languagePackage);
            String runtime = root + "/" + basePackage + "." + languageName.toLowerCase();
            String ide = runtime + ".ide";
            String ui = runtime + ".ui";
            List<String> sourceIds = TechnologyAdapterGenerationSupport.sourceIds(request);
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            files.add(TechnologyAdapterSupport.text(root + "/pom.xml", "application/xml", parentPom(basePackage, languageName, xtextVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/pom.xml", "application/xml", runtimePom(basePackage, languageName, xtextVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(ide + "/pom.xml", "application/xml", childPom(basePackage, languageName, "ide", xtextVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(ui + "/pom.xml", "application/xml", childPom(basePackage, languageName, "ui", xtextVersion), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/" + languageName + ".xtext",
                    "text/x-xtext", metamodel == null ? grammarSource(grammarDefinition) : grammar(metamodel, languagePackage, languageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/validation/" + languageName + "Validator.java",
                    "text/x-java-source", validatorSource(languagePackage, languageName, metamodel != null), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/validation/HydraIssueCodes.java",
                    "text/x-java-source", issueCodesSource(languagePackage), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/runtime/HydraOperationBridge.java",
                    "text/x-java-source", operationSource(languagePackage), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/runtime/HydraEventBridge.java",
                    "text/x-java-source", eventSource(languagePackage), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/runtime/HydraRuntimeActivation.java",
                        "text/x-java-source", TechnologyAdapterGenerationSupport.javaRuntimeActivationSource(languagePackage + ".runtime", request), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/runtime/HydraSemanticRuntime.java",
                        "text/x-java-source", TechnologyAdapterRuntimeLifecycleSupport.javaSemanticRuntimeSource(languagePackage + ".runtime"), sourceIds));
            files.add(TechnologyAdapterSupport.text(runtime + "/src/main/java/" + javaPath + "/runtime/HydraXtextRuntime.java",
                        "text/x-java-source", xtextRuntimeSource(languagePackage), sourceIds));
            files.add(TechnologyAdapterSupport.text(ide + "/src/main/java/" + javaPath + "/ide/HydraLanguageServer.java",
                        "text/x-java-source", languageServerSource(languagePackage), sourceIds));
            files.add(TechnologyAdapterSupport.text(ide + "/src/main/java/" + javaPath + "/ide/" + languageName + "IdeModule.java",
                    "text/x-java-source", ideModuleSource(languagePackage, languageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(ui + "/src/main/java/" + javaPath + "/ui/quickfix/" + languageName + "QuickfixProvider.java",
                    "text/x-java-source", quickFixSource(languagePackage, languageName), sourceIds));
            if(request.find(IntegrationArtifactKind.GRAMMAR).isPresent()){
                var customization=TechnologyAdapterTextualCustomizationSupport.customization(request,mapper);
                files.add(TechnologyAdapterSupport.text(ui + "/src/main/java/" + javaPath + "/ui/highlighting/HydraHighlightingConfiguration.java", "text/x-java-source", TechnologyAdapterTextualCustomizationSupport.xtextHighlightingConfiguration(languagePackage,customization), sourceIds));
                files.add(TechnologyAdapterSupport.text(ui + "/src/main/java/" + javaPath + "/ui/hover/HydraHoverProvider.java", "text/x-java-source", TechnologyAdapterTextualCustomizationSupport.xtextHoverProvider(languagePackage,customization), sourceIds));
                files.add(TechnologyAdapterSupport.text(ui + "/src/main/java/" + javaPath + "/ui/" + languageName + "UiModule.java", "text/x-java-source", textualUiModuleSource(languagePackage,languageName), sourceIds));
                files.addAll(TechnologyAdapterTextualCustomizationSupport.neutralFiles(ui,request,mapper));
            }
            files.add(TechnologyAdapterSupport.text(ui + "/META-INF/MANIFEST.MF", "text/plain", manifest(basePackage, languageName), sourceIds));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(runtime, "src/main/resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(root + "/README.md", "text/markdown", readme(request, semanticId, languagePackage, metamodel != null), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "Xtext"));
            List<IntegrationDependencyChange> dependencies = List.of(
                    dependency("org.eclipse.xtext:org.eclipse.xtext", xtextVersion),
                    dependency("org.eclipse.xtext:org.eclipse.xtext.ide", xtextVersion),
                    dependency("org.eclipse.xtext:org.eclipse.xtext.ui", xtextVersion),
                    dependency("org.eclipse.emf:org.eclipse.emf.ecore.change", "[2.14.0,3.0.0)"));
            List<IntegrationVerificationStep> verification = List.of(
                    new IntegrationVerificationStep("generate-xtext-language", "Generate and compile the Xtext language", root,
                            List.of("mvn", "-q", "verify"), 0, true));
            return new TechnologyCompilationResult(IntegrationTechnology.XTEXT, files, dependencies,
                    List.of("Add the generated reactor to the host build", "Run the Xtext generator before launching the IDE or language server"),
                    List.of("Rollback by removing the generated Xtext reactor", "Restore host build files from the HYDRA injection backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "Xtext/Java",
                            "moduleRoot", root, "languagePackage", languagePackage, "languageName", languageName, "xtextVersion", xtextVersion));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate Xtext integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String coordinate, String version){return new IntegrationDependencyChange("maven", coordinate, version, "compile", IntegrationDependencyAction.ADD);}

    /** Descripción: Ejecuta la operación «parentPom» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: basePackage: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación; xtextVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String parentPom(String basePackage, String languageName, String xtextVersion){
        String artifact = TechnologyAdapterSupport.slug(basePackage + "." + languageName);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId><artifactId>%s.parent</artifactId><version>1.0.0-SNAPSHOT</version><packaging>pom</packaging>
                  <properties><xtext.version>%s</xtext.version><maven.compiler.release>17</maven.compiler.release></properties>
                  <modules><module>%s</module><module>%s.ide</module><module>%s.ui</module></modules>
                </project>
                """.formatted(TechnologyAdapterSupport.xml(basePackage), artifact, TechnologyAdapterSupport.xml(xtextVersion), basePackage + "." + languageName.toLowerCase(), basePackage + "." + languageName.toLowerCase(), basePackage + "." + languageName.toLowerCase());
    }

    /** Descripción: Procesa la operación «runtimePom» coordinando las responsabilidades de XtextTechnologyAdapter. Entradas: basePackage: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación; xtextVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String runtimePom(String basePackage, String languageName, String xtextVersion){return modulePom(basePackage, languageName, "", "org.eclipse.xtext", xtextVersion);}
    /** Descripción: Ejecuta la operación «childPom» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: basePackage: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación; suffix: dato de entrada necesario para la operación; xtextVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String childPom(String basePackage, String languageName, String suffix, String xtextVersion){return modulePom(basePackage, languageName, "." + suffix, suffix.equals("ui") ? "org.eclipse.xtext.ui" : "org.eclipse.xtext.ide", xtextVersion);}
    /** Descripción: Ejecuta la operación «modulePom» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: basePackage: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación; suffix: dato de entrada necesario para la operación; dependency: dato de entrada necesario para la operación; xtextVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String modulePom(String basePackage, String languageName, String suffix, String dependency, String xtextVersion){
        String module = basePackage + "." + languageName.toLowerCase() + suffix;
        String runtimeDependency = suffix.isBlank() ? "" : "<dependency><groupId>" + TechnologyAdapterSupport.xml(basePackage) + "</groupId><artifactId>" + TechnologyAdapterSupport.xml(basePackage + "." + languageName.toLowerCase()) + "</artifactId><version>1.0.0-SNAPSHOT</version></dependency>";
        String changeDependency = suffix.isBlank() ? "<dependency><groupId>org.eclipse.emf</groupId><artifactId>org.eclipse.emf.ecore.change</artifactId><version>[2.14.0,3.0.0)</version></dependency>" : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId><artifactId>%s</artifactId><version>1.0.0-SNAPSHOT</version>
                  <properties><xtext.version>%s</xtext.version><maven.compiler.release>17</maven.compiler.release></properties>
                  <dependencies><dependency><groupId>org.eclipse.xtext</groupId><artifactId>%s</artifactId><version>${xtext.version}</version></dependency>%s%s</dependencies>
                </project>
                """.formatted(TechnologyAdapterSupport.xml(basePackage), TechnologyAdapterSupport.xml(module), TechnologyAdapterSupport.xml(xtextVersion), TechnologyAdapterSupport.xml(dependency), runtimeDependency, changeDependency);
    }

    /** Descripción: Ejecuta la operación «grammar» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String grammar(MetaModel metamodel, String languagePackage, String languageName){
        StringBuilder source = new StringBuilder();
        source.append("grammar ").append(languagePackage).append('.').append(languageName).append(" with org.eclipse.xtext.common.Terminals\n\n");
        source.append("generate ").append(TechnologyAdapterGenerationSupport.typescriptIdentifier(metamodel.id(), "hydraModel"))
                .append(" \"").append(metamodel.annotations().getOrDefault("nsURI", "https://hydra.generated/" + metamodel.id())).append("\"\n\n");
        source.append("Model:\n  elements+=Element*;\n\n");
        source.append("Element:\n  ");
        if(metamodel.classes().isEmpty()){source.append("GenericElement");}
        else{source.append(String.join(" | ", metamodel.classes().stream().map(MetaClass::name).toList()));}
        source.append(";\n\n");
        if(metamodel.classes().isEmpty()){source.append("GenericElement: 'element' name=ID;\n\n");}
        for(MetaClass metaClass : metamodel.classes()){
            source.append(metaClass.name()).append(":\n  '").append(metaClass.name()).append("' name=ID");
            for(MetaAttribute attribute : metaClass.attributes()){source.append(attributeFragment(attribute, metamodel));}
            for(MetaRelation relation : metamodel.relations()){
                if(relation.sourceTypeId().equals(metaClass.id()) && structural(relation.kind())){source.append(relationFragment(relation, metamodel));}
            }
            source.append(";\n\n");
        }
        metamodel.enums().forEach(metaEnum -> source.append(metaEnum.name()).append(": ")
                .append(String.join(" | ", metaEnum.literals().stream().map(value -> "'" + value.name() + "'").toList())).append(";\n\n"));
        return source.toString();
    }

    /** Descripción: Ejecuta la operación «structural» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean structural(MetaRelationKind kind){return kind == MetaRelationKind.REFERENCE || kind == MetaRelationKind.CONTAINMENT || kind == MetaRelationKind.AGGREGATION;}
    /** Descripción: Ejecuta la operación «attributeFragment» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: attribute: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String attributeFragment(MetaAttribute attribute, MetaModel metamodel){
        String terminal = xtextType(attribute.type(), metamodel);
        boolean many = attribute.multiplicity() != null && (attribute.multiplicity().unbounded() || attribute.multiplicity().upperBound() != null && attribute.multiplicity().upperBound() > 1);
        boolean optional = attribute.multiplicity() == null || attribute.multiplicity().lowerBound() == 0;
        String assignment = attribute.name() + (many ? "+=" : "=") + terminal;
        if(many){return "\n  ('" + attribute.name() + "' " + assignment + ")*";}
        if(optional){return "\n  ('" + attribute.name() + "' " + assignment + ")?";}
        return "\n  '" + attribute.name() + "' " + assignment;
    }
    /** Descripción: Ejecuta la operación «relationFragment» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: relation: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relationFragment(MetaRelation relation, MetaModel metamodel){
        String target = metamodel.classes().stream().filter(value -> value.id().equals(relation.targetTypeId())).map(MetaClass::name).findFirst().orElse("Element");
        boolean many = relation.targetMultiplicity() != null && (relation.targetMultiplicity().unbounded() || relation.targetMultiplicity().upperBound() != null && relation.targetMultiplicity().upperBound() > 1);
        boolean optional = relation.targetMultiplicity() == null || relation.targetMultiplicity().lowerBound() == 0;
        String assignment = relation.name() + (many ? "+=" : "=") + "[" + target + "|ID]";
        if(many){return "\n  ('" + relation.name() + "' " + assignment + ")*";}
        if(optional){return "\n  ('" + relation.name() + "' " + assignment + ")?";}
        return "\n  '" + relation.name() + "' " + assignment;
    }
    /** Descripción: Ejecuta la operación «xtextType» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: type: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String xtextType(MetaTypeReference type, MetaModel metamodel){
        if(type == null){return "STRING";}
        if(type.kind() == MetaTypeReferenceKind.ENUM){return metamodel.enums().stream().filter(value -> value.id().equals(type.targetId())).map(value -> value.name()).findFirst().orElse("ID");}
        if(type.kind() != MetaTypeReferenceKind.PRIMITIVE){return "STRING";}
        try{
            MetaPrimitiveType primitive = MetaPrimitiveType.valueOf(type.targetId());
            return switch(primitive){case BOOLEAN -> "('true'|'false')"; case BYTE, SHORT, INTEGER, LONG, BIG_INTEGER -> "INT"; default -> "STRING";};
        }catch(IllegalArgumentException exception){return "STRING";}
    }

    /** Descripción: Genera el runtime Xtext ejecutable con estado por recurso, validación provisional, commit confirmado y rollback EMF+HYDRA. Entradas: packageName: paquete del lenguaje. Salida: fuente Java del runtime host. */ private String xtextRuntimeSource(String packageName){return """
            package %s.runtime;
            import java.util.ArrayList;
            import java.util.Comparator;
            import java.util.List;
            import java.util.Map;
            import java.util.Objects;
            import java.util.concurrent.ConcurrentHashMap;
            import org.eclipse.emf.ecore.EObject;
            import org.eclipse.emf.ecore.EStructuralFeature;
            import org.eclipse.emf.ecore.change.ChangeDescription;
            import org.eclipse.emf.ecore.change.util.ChangeRecorder;
            import org.eclipse.emf.ecore.resource.Resource;
            import org.eclipse.emf.ecore.resource.ResourceSet;
            import org.eclipse.emf.ecore.util.EcoreUtil;
            /** Executable Xtext host runtime. Each resource owns an independent transactional HYDRA lifecycle. */
            public final class HydraXtextRuntime {
                public interface Adapter {
                    default List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return List.of();}
                    default List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return Objects.equals(before,after)?List.of():List.of(new HydraSemanticRuntime.Event(sequence,"MODEL_CHANGED","model",Map.of("trigger",trigger)));}
                    default List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return List.of();}
                    default HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){return previous;}
                    default boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){return false;}
                    default Object invoke(EObject target,String operationId,Map<String,Object> parameters){throw new UnsupportedOperationException("No HYDRA operation adapter is installed for " + operationId);}
                }
                private static final ConcurrentHashMap<String,HydraSemanticRuntime> RUNTIMES=new ConcurrentHashMap<>();
                private static volatile Adapter adapter=new Adapter(){};
                private HydraXtextRuntime(){ }
                public static void install(Adapter value){adapter=Objects.requireNonNull(value,"value");RUNTIMES.clear();}
                public static List<HydraSemanticRuntime.Diagnostic> validateHost(EObject target){if(target==null)return List.of();return runtime(target).validateHost(semanticState(target),"XTEXT_DRAFT_VALIDATE");}
                public static HydraSemanticRuntime.CommitResult synchronize(EObject target,String trigger){Objects.requireNonNull(target,"target");return runtime(target).synchronize(semanticState(target),trigger);}
                public static HydraSemanticRuntime.Snapshot checkpoint(EObject target){return runtime(target).checkpoint();}
                public static List<HydraSemanticRuntime.Event> events(EObject target){return runtime(target).events();}
                public static HydraSemanticRuntime.GamificationState gamification(EObject target){return runtime(target).gamification();}
                public static boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){
                    Objects.requireNonNull(target,"target");HydraSemanticRuntime lifecycle=runtime(target);HydraSemanticRuntime.Snapshot before=lifecycle.checkpoint();ChangeRecorder recorder=recorder(target);ChangeDescription changes=null;
                    try{boolean applied=adapter.applyQuickFix(target,safe(diagnosticId),safe(actionId),safeMap(parameters));changes=recorder.endRecording();if(!applied){rollbackHost(changes,null);lifecycle.restore(before);return false;}lifecycle.synchronize(semanticState(target),"XTEXT_QUICK_FIX");return true;}
                    catch(RuntimeException|Error failure){if(changes==null)changes=safelyEnd(recorder,failure);rollbackHost(changes,failure);restoreLifecycle(lifecycle,before,failure);throw failure;}
                    finally{recorder.dispose();}
                }
                public static Object invoke(EObject target,String operationId,Map<String,Object> parameters){
                    Objects.requireNonNull(target,"target");HydraSemanticRuntime lifecycle=runtime(target);HydraSemanticRuntime.Snapshot before=lifecycle.checkpoint();ChangeRecorder recorder=recorder(target);ChangeDescription changes=null;
                    try{Object result=adapter.invoke(target,safe(operationId),safeMap(parameters));changes=recorder.endRecording();lifecycle.synchronize(semanticState(target),"XTEXT_OPERATION");return result;}
                    catch(RuntimeException|Error failure){if(changes==null)changes=safelyEnd(recorder,failure);rollbackHost(changes,failure);restoreLifecycle(lifecycle,before,failure);throw failure;}
                    finally{recorder.dispose();}
                }
                public static void release(String resourceUri){if(resourceUri!=null)RUNTIMES.remove(resourceUri);}
                public static String semanticState(EObject target){EObject root=EcoreUtil.getRootContainer(target);StringBuilder out=new StringBuilder(256);append(root,out,new java.util.IdentityHashMap<>());return out.toString();}
                private static void append(EObject object,StringBuilder out,java.util.IdentityHashMap<EObject,Boolean> seen){if(object==null||seen.put(object,Boolean.TRUE)!=null)return;out.append(object.eClass().getEPackage().getNsURI()).append('#').append(object.eClass().getName()).append('{');List<EStructuralFeature> features=new ArrayList<>(object.eClass().getEAllStructuralFeatures());features.sort(Comparator.comparing(EStructuralFeature::getName));for(EStructuralFeature feature:features){if(feature.isDerived()||feature.isTransient()||!object.eIsSet(feature))continue;Object value=object.eGet(feature,false);out.append(feature.getName()).append('=');appendValue(value,out,seen);out.append(';');}out.append('}');}
                private static void appendValue(Object value,StringBuilder out,java.util.IdentityHashMap<EObject,Boolean> seen){if(value instanceof EObject eo){if(eo.eContainmentFeature()!=null)append(eo,out,seen);else out.append(EcoreUtil.getURI(eo));return;}if(value instanceof Iterable<?> values){out.append('[');for(Object item:values){appendValue(item,out,seen);out.append(',');}out.append(']');return;}out.append(String.valueOf(value));}
                private static HydraSemanticRuntime runtime(EObject target){String key=key(target);return RUNTIMES.computeIfAbsent(key,ignored->new HydraSemanticRuntime(new HydraSemanticRuntime.Engine(){public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return adapter.validate(state,trigger);}public List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return adapter.deriveEvents(before,after,trigger,sequence);}public List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return adapter.runEventFlow(confirmed,state);}public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){return adapter.updateGamification(previous,confirmed,flow);}}));}
                private static String key(EObject target){Resource resource=target.eResource();return resource!=null&&resource.getURI()!=null?resource.getURI().toString():"memory:"+Integer.toHexString(System.identityHashCode(EcoreUtil.getRootContainer(target)));}
                private static ChangeRecorder recorder(EObject target){Resource resource=target.eResource();ResourceSet set=resource==null?null:resource.getResourceSet();return set!=null?new ChangeRecorder(set):resource!=null?new ChangeRecorder(resource):new ChangeRecorder(EcoreUtil.getRootContainer(target));}
                private static ChangeDescription safelyEnd(ChangeRecorder recorder,Throwable failure){try{return recorder.endRecording();}catch(RuntimeException|Error rollbackFailure){failure.addSuppressed(rollbackFailure);return null;}}
                private static void rollbackHost(ChangeDescription changes,Throwable failure){if(changes==null)return;try{changes.apply();}catch(RuntimeException|Error rollbackFailure){if(failure!=null)failure.addSuppressed(rollbackFailure);else throw rollbackFailure;}}
                private static void restoreLifecycle(HydraSemanticRuntime lifecycle,HydraSemanticRuntime.Snapshot before,Throwable failure){try{lifecycle.restore(before);}catch(RuntimeException|Error rollbackFailure){failure.addSuppressed(rollbackFailure);}}
                private static Map<String,Object> safeMap(Map<String,Object> parameters){return parameters==null?Map.of():Map.copyOf(parameters);}private static String safe(String value){return value==null?"":value;}
            }
            """.formatted(packageName);}

    /** Descripción: Genera un validador Xtext que delega la validación provisional en el runtime HYDRA tanto en metamodel-first como grammar-first. Entradas: packageName, languageName y origen semántico. Salida: fuente Java del validador. */ private String validatorSource(String packageName, String languageName, boolean metamodelBacked){return """
            package %s.validation;
            import org.eclipse.emf.ecore.EObject;
            import org.eclipse.xtext.validation.Check;
            import %s.runtime.HydraXtextRuntime;
            /** Side-effect-free HYDRA validation boundary for Xtext draft objects. */
            public class %sValidator extends Abstract%sValidator {
                @Check public void validateCanonicalObject(EObject object){if(object==null)return;for(var diagnostic:HydraXtextRuntime.validateHost(object)){String message=diagnostic.message();String code=diagnostic.id().isBlank()?HydraIssueCodes.CANONICAL_VALIDATION:diagnostic.id();if("ERROR".equalsIgnoreCase(diagnostic.severity()))error(message,object,null,code);else if("WARNING".equalsIgnoreCase(diagnostic.severity()))warning(message,object,null,code);else info(message,object,null,code);}}
            }
            """.formatted(packageName,packageName,languageName,languageName);}

    /** Descripción: Recupera el documento de entrada de una gramática neutral Xtext. Entradas: grammarDefinition. Salida: contenido exacto del entry point. */ private String grammarSource(GrammarDefinitionModel grammarDefinition){
        return grammarDefinition.documents().stream().filter(value -> value.id().equals(grammarDefinition.entryDocumentId()) || value.entryPoint()).findFirst()
                .orElseThrow(() -> new TechnologyCompilationException("Grammar entry document is missing")).content();
    }

    /** Descripción: Obtiene un nombre de lenguaje estable desde la cabecera Xtext o desde el nombre neutral. Entradas: grammarDefinition. Salida: identificador seguro. */ private String grammarLanguageName(GrammarDefinitionModel grammarDefinition){
        var matcher=java.util.regex.Pattern.compile("(?m)^\\s*grammar\\s+([A-Za-z_][A-Za-z0-9_.]*)").matcher(grammarSource(grammarDefinition));
        String raw=matcher.find()?matcher.group(1).substring(matcher.group(1).lastIndexOf('.')+1):grammarDefinition.name();
        return TechnologyAdapterSupport.javaType(raw,"HydraDsl");
    }

    /** Descripción: Evalúa la condición «issueCodesSource» sin modificar el estado externo. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String issueCodesSource(String packageName){return """
            package %s.validation;
            public final class HydraIssueCodes {
                public static final String CANONICAL_VALIDATION="hydra.canonical.validation";
                private HydraIssueCodes(){ }
            }
            """.formatted(packageName);}
    /** Descripción: Genera la fachada ejecutable de operaciones Xtext sobre el lifecycle transaccional. Entradas: packageName. Salida: fuente Java del bridge. */ private String operationSource(String packageName){return """
            package %s.runtime;
            import java.util.Map;
            import org.eclipse.emf.ecore.EObject;
            public final class HydraOperationBridge { public Object invoke(EObject target,String operationId,Map<String,Object> parameters){return HydraXtextRuntime.invoke(target,operationId,parameters);} }
            """.formatted(packageName);}
    /** Descripción: Genera la fachada ejecutable de eventos Xtext sobre commits confirmados. Entradas: packageName. Salida: fuente Java del bridge. */ private String eventSource(String packageName){return """
            package %s.runtime;
            import java.util.List;
            import org.eclipse.emf.ecore.EObject;
            public final class HydraEventBridge { public HydraSemanticRuntime.CommitResult publishAfterCommit(EObject target,String trigger){return HydraXtextRuntime.synchronize(target,trigger);}public List<HydraSemanticRuntime.Event> history(EObject target){return HydraXtextRuntime.events(target);} }
            """.formatted(packageName);}
    /** Descripción: Genera el servidor Xtext LSP que anuncia y procesa didSave como frontera de commit HYDRA. Entradas: packageName. Salida: fuente Java del servidor. */ private String languageServerSource(String packageName){return """
            package %s.ide;
            import com.google.inject.Inject;
            import org.eclipse.emf.common.util.URI;
            import org.eclipse.lsp4j.DidCloseTextDocumentParams;
            import org.eclipse.lsp4j.DidSaveTextDocumentParams;
            import org.eclipse.lsp4j.InitializeParams;
            import org.eclipse.lsp4j.SaveOptions;
            import org.eclipse.lsp4j.ServerCapabilities;
            import org.eclipse.lsp4j.TextDocumentSyncKind;
            import org.eclipse.lsp4j.TextDocumentSyncOptions;
            import org.eclipse.lsp4j.jsonrpc.messages.Either;
            import org.eclipse.xtext.ide.server.LanguageServerImpl;
            import org.eclipse.xtext.ide.server.WorkspaceManager;
            import %s.runtime.HydraXtextRuntime;
            /** Language server whose save notification is the explicit HYDRA commit boundary. */
            public final class HydraLanguageServer extends LanguageServerImpl {
                @Inject private WorkspaceManager workspaceManager;
                @Override protected ServerCapabilities createServerCapabilities(InitializeParams params){ServerCapabilities capabilities=super.createServerCapabilities(params);TextDocumentSyncOptions sync=new TextDocumentSyncOptions();sync.setOpenClose(true);sync.setChange(TextDocumentSyncKind.Incremental);SaveOptions save=new SaveOptions();save.setIncludeText(false);sync.setSave(save);capabilities.setTextDocumentSync(Either.forRight(sync));return capabilities;}
                @Override public void didSave(DidSaveTextDocumentParams params){URI uri=URI.createURI(params.getTextDocument().getUri());workspaceManager.doRead(uri,(document,resource)->{if(resource!=null&&!resource.getContents().isEmpty())HydraXtextRuntime.synchronize(resource.getContents().get(0),"XTEXT_DID_SAVE");return null;});}
                @Override public void didClose(DidCloseTextDocumentParams params){super.didClose(params);HydraXtextRuntime.release(params.getTextDocument().getUri());}
            }
            """.formatted(packageName,packageName);}
    /** Descripción: Genera el módulo IDE que sustituye el LanguageServerImpl por el servidor HYDRA. Entradas: packageName y languageName. Salida: fuente Java del módulo. */ private String ideModuleSource(String packageName, String languageName){return """
            package %s.ide;
            import org.eclipse.xtext.ide.server.LanguageServerImpl;
            public class %sIdeModule extends Abstract%sIdeModule { public Class<? extends LanguageServerImpl> bindLanguageServerImpl(){return HydraLanguageServer.class;} }
            """.formatted(packageName, languageName, languageName);}
    /** Descripción: Genera el quick fix Xtext que ejecuta ActionModel a través del runtime y revierte EObject+runtime ante fallo. Entradas: packageName y languageName. Salida: fuente Java del proveedor. */ private String quickFixSource(String packageName, String languageName){return """
            package %s.ui.quickfix;
            import java.util.Map;
            import org.eclipse.xtext.ui.editor.quickfix.DefaultQuickfixProvider;
            import org.eclipse.xtext.ui.editor.quickfix.Fix;
            import org.eclipse.xtext.ui.editor.quickfix.IssueResolutionAcceptor;
            import org.eclipse.xtext.validation.Issue;
            import %s.runtime.HydraXtextRuntime;
            import %s.validation.HydraIssueCodes;
            public class %sQuickfixProvider extends DefaultQuickfixProvider {
                @Fix(HydraIssueCodes.CANONICAL_VALIDATION)
                public void applyHydraQuickFix(Issue issue,IssueResolutionAcceptor acceptor){String actionId=issue.getData()!=null&&issue.getData().length>0?issue.getData()[0]:"hydra.canonical.quickfix";acceptor.accept(issue,"Apply HYDRA quick fix","Execute the canonical ActionModel repair",null,(element,context)->HydraXtextRuntime.applyQuickFix(element,issue.getCode(),actionId,Map.of()));}
            }
            """.formatted(packageName,packageName,packageName,languageName);}
    /** Descripción: Ejecuta la operación «textualUiModuleSource» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: packageName: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textualUiModuleSource(String packageName,String languageName){return """
            package %s.ui;
            import org.eclipse.xtext.ui.editor.hover.IEObjectHoverProvider;
            import org.eclipse.xtext.ui.editor.syntaxcoloring.IHighlightingConfiguration;
            import %s.ui.highlighting.HydraHighlightingConfiguration;
            import %s.ui.hover.HydraHoverProvider;
            public class %sUiModule extends Abstract%sUiModule {
                public Class<? extends IHighlightingConfiguration> bindIHighlightingConfiguration(){return HydraHighlightingConfiguration.class;}
                public Class<? extends IEObjectHoverProvider> bindIEObjectHoverProvider(){return HydraHoverProvider.class;}
            }
            """.formatted(packageName,packageName,packageName,languageName,languageName);}
    /** Descripción: Ejecuta la operación «manifest» correspondiente a la responsabilidad de XtextTechnologyAdapter. Entradas: basePackage: dato de entrada necesario para la operación; languageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String manifest(String basePackage, String languageName){return """
            Manifest-Version: 1.0
            Bundle-ManifestVersion: 2
            Bundle-Name: HYDRA generated Xtext UI
            Bundle-SymbolicName: %s.%s.ui;singleton:=true
            Bundle-Version: 1.0.0.qualifier
            Require-Bundle: org.eclipse.xtext.ui,org.eclipse.ui
            """.formatted(basePackage, languageName.toLowerCase());}
    /** Descripción: Documenta el origen semántico del paquete Xtext generado. Entradas: request, semanticId, languagePackage y origen. Salida: README reproducible. */ private String readme(TechnologyCompilationRequest request, String semanticId, String languagePackage, boolean metamodelBacked){return """
            # HYDRA Xtext integration

            Generated from canonical semantic scope `%s` (%s).

            - Language package: `%s`.
            - Runtime, IDE and UI concerns are separated into distinct modules.
            - Draft validation is side-effect free; LSP `didSave` is the explicit commit boundary for Events → EventFlow → Gamification.
            - Quick fixes and operations are wrapped with EMF ChangeRecorder plus a full HYDRA checkpoint, so host model and runtime roll back together on failure.
            - Validation works in metamodel-first and grammar-first without requiring a synthetic `Model.elements` feature.
            - Canonical HYDRA JSON remains embedded for traceability and partial-fidelity capabilities.
            - `HydraRuntimeActivation` and `runtime-activation.json` are scoped to `semanticScopeId`.
            - Grammar-first preserves the source grammar and does not persist an artificial metamodel; the generated language does not make Xtext or Ecore the HYDRA source of truth.
            - Injection profile: `%s`.
            """.formatted(semanticId, metamodelBacked?"MetaModel 2.1":"Grammar-first", languagePackage, request.profile().id());}

}
