package com.example.editor.integration.adapter.emf;

import java.nio.charset.StandardCharsets;
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
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaModel;
import tools.jackson.databind.json.JsonMapper;

/** Genera un módulo EMF separado que consume los contratos canónicos de HYDRA. */
@Component
public class EmfTechnologyAdapter implements TechnologyAdapter {
    private static final Set<IntegrationArtifactKind> SUPPORTED = EnumSet.of(
            IntegrationArtifactKind.METAMODEL, IntegrationArtifactKind.EXPRESSIONS,
            IntegrationArtifactKind.VALIDATIONS, IntegrationArtifactKind.ACTIONS,
            IntegrationArtifactKind.OPERATION_BEHAVIORS, IntegrationArtifactKind.EVENT_DEFINITIONS,
            IntegrationArtifactKind.EVENT_BINDINGS, IntegrationArtifactKind.EVENT_FLOWS,
            IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, IntegrationArtifactKind.GAMIFICATION_BINDINGS,
            IntegrationArtifactKind.EDITOR_DEFINITION, IntegrationArtifactKind.CODE_GENERATION);
    private final JsonMapper mapper;
    private final EcoreMetamodelAdapter ecoreAdapter;

    /** Descripción: Inicializa una instancia de EmfTechnologyAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; ecoreAdapter: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EmfTechnologyAdapter(JsonMapper mapper, EcoreMetamodelAdapter ecoreAdapter){this.mapper = mapper; this.ecoreAdapter = ecoreAdapter;}
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de HydraEmfQuickFixCommand. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public IntegrationTechnology technology(){return IntegrationTechnology.EMF;}
    /** Descripción: Ejecuta la operación «supportedArtifactKinds» correspondiente a la responsabilidad de HydraEmfQuickFixCommand. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<IntegrationArtifactKind> supportedArtifactKinds(){return Set.copyOf(SUPPORTED);}

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de HydraEmfQuickFixCommand. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TechnologyCompilationResult compile(TechnologyCompilationRequest request){
        try{
            MetaModel metamodel = mapper.readValue(request.require(IntegrationArtifactKind.METAMODEL).content(), MetaModel.class);
            String root = TechnologyAdapterSupport.generatedRoot(request, metamodel.id() + "-emf");
            String packageName = TechnologyAdapterSupport.javaPackage(request.profile().target().settings().get("basePackage"),
                    "com.generated." + TechnologyAdapterSupport.slug(metamodel.id()) + ".emf");
            String javaPath = TechnologyAdapterSupport.javaPath(packageName);
            String typePrefix = TechnologyAdapterSupport.javaType(metamodel.name(), "HydraModel");
            List<TechnologyGeneratedFile> files = new ArrayList<>();
            List<String> sourceIds = request.artifacts().stream().map(value -> value.documentId()).distinct().toList();
            files.add(TechnologyAdapterSupport.binary(TechnologyAdapterSupport.join(root, "src/main/resources/model", metamodel.id() + ".ecore"),
                    "application/xml", ecoreAdapter.toBytes(metamodel).value(), List.of(metamodel.id())));
            files.addAll(TechnologyAdapterGenerationSupport.canonicalDocuments(root, "src/main/resources/hydra", request));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "pom.xml"), "application/xml", pom(request, metamodel, packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "README.md"), "text/markdown", readme(request, metamodel, packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, typePrefix + "HydraEmfModule.java"), "text/x-java-source", moduleSource(packageName, typePrefix, metamodel.id()), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraRuntimeActivation.java"), "text/x-java-source",
                    TechnologyAdapterGenerationSupport.javaRuntimeActivationSource(packageName, request), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraSemanticRuntime.java"), "text/x-java-source",
                    TechnologyAdapterRuntimeLifecycleSupport.javaSemanticRuntimeSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraEmfRuntime.java"), "text/x-java-source", emfRuntimeSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraEmfValidationAdapter.java"), "text/x-java-source", validationSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraEmfQuickFixCommand.java"), "text/x-java-source", quickFixSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraEmfOperationAdapter.java"), "text/x-java-source", operationSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraEmfEventAdapter.java"), "text/x-java-source", eventSource(packageName), sourceIds));
            files.add(TechnologyAdapterSupport.text(TechnologyAdapterSupport.join(root, "src/main/java", javaPath, "HydraEmfEditingDomain.java"), "text/x-java-source", editingDomainSource(packageName), sourceIds));
            List<IntegrationDiagnostic> diagnostics = new ArrayList<>(TechnologyAdapterSupport.unsupportedDiagnostics(request, SUPPORTED, "EMF"));
            List<IntegrationDependencyChange> dependencies = List.of(
                    dependency("org.eclipse.emf:org.eclipse.emf.common", "2.46.0"),
                    dependency("org.eclipse.emf:org.eclipse.emf.ecore", "2.43.0"),
                    dependency("org.eclipse.emf:org.eclipse.emf.ecore.xmi", "2.41.0"),
                    dependency("org.eclipse.emf:org.eclipse.emf.ecore.change", "2.18.0"),
                    dependency("org.eclipse.emf:org.eclipse.emf.edit", "2.24.0"));
            List<IntegrationVerificationStep> verification = List.of(new IntegrationVerificationStep("compile-emf-module",
                    "Compile the generated EMF module", root, List.of("mvn", "-q", "test"), 0, true));
            return new TechnologyCompilationResult(IntegrationTechnology.EMF, files, dependencies,
                    List.of("Add the generated module to the host build", "Register the generated EPackage URI if the host uses a global registry"),
                    List.of("Remove the generated EMF module", "Restore the previous build descriptor from the HYDRA backup"),
                    verification, diagnostics, Map.of("generatorVersion", request.generatorVersion(), "nativeLanguage", "Java/Ecore",
                            "moduleRoot", root, "basePackage", packageName));
        }catch(RuntimeException exception){
            if(exception instanceof TechnologyCompilationException){throw exception;}
            throw new TechnologyCompilationException("Could not generate EMF integration", exception);
        }
    }

    /** Descripción: Ejecuta la operación «dependency» correspondiente a la responsabilidad de HydraEmfQuickFixCommand. Entradas: coordinate: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationDependencyChange dependency(String coordinate, String version){return new IntegrationDependencyChange("maven", coordinate, version, "compile", IntegrationDependencyAction.ADD);}

    /** Descripción: Ejecuta la operación «pom» correspondiente a la responsabilidad de HydraEmfQuickFixCommand. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String pom(TechnologyCompilationRequest request, MetaModel metamodel, String packageName){
        String artifactId = TechnologyAdapterSupport.slug(request.profile().target().moduleName().isBlank() ? metamodel.id() + "-hydra-emf" : request.profile().target().moduleName());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0.0-SNAPSHOT</version>
                  <properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
                  <dependencies>
                    <dependency><groupId>org.eclipse.emf</groupId><artifactId>org.eclipse.emf.common</artifactId><version>2.46.0</version></dependency>
                    <dependency><groupId>org.eclipse.emf</groupId><artifactId>org.eclipse.emf.ecore</artifactId><version>2.43.0</version></dependency>
                    <dependency><groupId>org.eclipse.emf</groupId><artifactId>org.eclipse.emf.ecore.xmi</artifactId><version>2.41.0</version></dependency>
                    <dependency><groupId>org.eclipse.emf</groupId><artifactId>org.eclipse.emf.ecore.change</artifactId><version>2.18.0</version></dependency>
                    <dependency><groupId>org.eclipse.emf</groupId><artifactId>org.eclipse.emf.edit</artifactId><version>2.24.0</version></dependency>
                  </dependencies>
                </project>
                """.formatted(TechnologyAdapterSupport.xml(packageName), TechnologyAdapterSupport.xml(artifactId));
    }

    /** Descripción: Ejecuta la operación «moduleSource» correspondiente a la responsabilidad de EmfTechnologyAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private String moduleSource(String packageName, String typePrefix, String metamodelId){return """
            package %s;

            import java.io.IOException;
            import org.eclipse.emf.common.util.URI;
            import org.eclipse.emf.ecore.EPackage;
            import org.eclipse.emf.ecore.resource.Resource;
            import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
            import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

            /** Loads the Ecore projection generated from HYDRA MetaModel 2.1. */
            public final class %sHydraEmfModule {
                /** Descripción: Obtiene la información correspondiente a «loadPackage» dentro de HydraEmfQuickFixCommand. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public EPackage loadPackage() throws IOException {
                    Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
                    Resource resource = new ResourceSetImpl().getResource(URI.createURI("classpath:/model/%s.ecore"), true);
                    return (EPackage) resource.getContents().get(0);
                }
            }
            """.formatted(packageName, typePrefix, metamodelId);}

    /** Descripción: Genera el runtime EMF concreto que proyecta EObject a estado semántico y delega las capacidades neutrales. Entradas: packageName: paquete Java generado. Salida: devuelve el código fuente del runtime. */ private String emfRuntimeSource(String packageName){return """
            package %s;

            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.Comparator;
            import java.util.IdentityHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.Objects;
            import java.util.WeakHashMap;
            import org.eclipse.emf.ecore.EObject;
            import org.eclipse.emf.ecore.EStructuralFeature;
            import org.eclipse.emf.ecore.resource.Resource;
            import org.eclipse.emf.ecore.util.EcoreUtil;

            /** Concrete EMF host runtime. Draft validation is side-effect free and confirmed commands own side effects. */
            public final class HydraEmfRuntime {
                public interface Adapter {
                    default List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return List.of();}
                    default List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return Objects.equals(before,after)?List.of():List.of(new HydraSemanticRuntime.Event(sequence,"MODEL_CHANGED","model",Map.of("trigger",trigger)));}
                    default List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return List.of();}
                    default HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){return previous;}
                    default boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){return false;}
                    default Object invoke(EObject target,String operationId,Map<String,Object> parameters){throw new UnsupportedOperationException("No HYDRA EMF operation adapter is installed for " + operationId);}
                }
                private static final Map<Object,HydraSemanticRuntime> RUNTIMES=Collections.synchronizedMap(new WeakHashMap<>());
                private static volatile Adapter adapter=new Adapter(){};
                private HydraEmfRuntime(){ }
                public static void install(Adapter value){adapter=Objects.requireNonNull(value,"value");synchronized(RUNTIMES){RUNTIMES.clear();}}
                public static List<HydraSemanticRuntime.Diagnostic> validateHost(EObject target,String trigger){return target==null?List.of():runtime(target).validateHost(semanticState(target),trigger);}
                public static HydraSemanticRuntime.CommitResult synchronize(EObject target,String trigger){Objects.requireNonNull(target,"target");return runtime(target).synchronize(semanticState(target),trigger);}
                public static HydraSemanticRuntime.Snapshot checkpoint(EObject target){return runtime(target).checkpoint();}
                public static void restoreOrRebaseline(EObject target,HydraSemanticRuntime.Snapshot snapshot,String trigger){HydraSemanticRuntime lifecycle=runtime(target);if(HydraRuntimeActivation.isEnabled()&&snapshot.activationEpoch()==HydraRuntimeActivation.epoch())lifecycle.restore(snapshot);else lifecycle.synchronize(semanticState(target),trigger);}
                public static List<HydraSemanticRuntime.Event> events(EObject target){return runtime(target).events();}
                public static HydraSemanticRuntime.GamificationState gamification(EObject target){return runtime(target).gamification();}
                static boolean applyQuickFix(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){return adapter.applyQuickFix(target,safe(diagnosticId),safe(actionId),safeMap(parameters));}
                static Object invoke(EObject target,String operationId,Map<String,Object> parameters){return adapter.invoke(target,safe(operationId),safeMap(parameters));}
                public static String semanticState(EObject target){EObject root=EcoreUtil.getRootContainer(target);StringBuilder out=new StringBuilder(256);append(root,out,new IdentityHashMap<>());return out.toString();}
                private static void append(EObject object,StringBuilder out,IdentityHashMap<EObject,Boolean> seen){if(object==null||seen.put(object,Boolean.TRUE)!=null)return;out.append(object.eClass().getEPackage().getNsURI()).append('#').append(object.eClass().getName()).append('{');List<EStructuralFeature> features=new ArrayList<>(object.eClass().getEAllStructuralFeatures());features.sort(Comparator.comparing(EStructuralFeature::getName));for(EStructuralFeature feature:features){if(feature.isDerived()||feature.isTransient()||!object.eIsSet(feature))continue;Object value=object.eGet(feature,false);out.append(feature.getName()).append('=');appendValue(value,out,seen);out.append(';');}out.append('}');}
                private static void appendValue(Object value,StringBuilder out,IdentityHashMap<EObject,Boolean> seen){if(value instanceof EObject eo){if(eo.eContainmentFeature()!=null)append(eo,out,seen);else out.append(EcoreUtil.getURI(eo));return;}if(value instanceof Iterable<?> values){out.append('[');for(Object item:values){appendValue(item,out,seen);out.append(',');}out.append(']');return;}out.append(String.valueOf(value));}
                private static HydraSemanticRuntime runtime(EObject target){Object key=target.eResource()!=null?target.eResource():EcoreUtil.getRootContainer(target);synchronized(RUNTIMES){return RUNTIMES.computeIfAbsent(key,ignored->new HydraSemanticRuntime(new HydraSemanticRuntime.Engine(){public List<HydraSemanticRuntime.Diagnostic> validate(String state,String trigger){return adapter.validate(state,trigger);}public List<HydraSemanticRuntime.Event> deriveEvents(String before,String after,String trigger,long sequence){return adapter.deriveEvents(before,after,trigger,sequence);}public List<HydraSemanticRuntime.Event> runEventFlow(List<HydraSemanticRuntime.Event> confirmed,String state){return adapter.runEventFlow(confirmed,state);}public HydraSemanticRuntime.GamificationState updateGamification(HydraSemanticRuntime.GamificationState previous,List<HydraSemanticRuntime.Event> confirmed,List<HydraSemanticRuntime.Event> flow){return adapter.updateGamification(previous,confirmed,flow);}}));}}
                private static Map<String,Object> safeMap(Map<String,Object> value){return value==null?Map.of():Map.copyOf(value);}private static String safe(String value){return value==null?"":value;}
            }
            """.formatted(packageName);}

    /** Descripción: Genera la fachada de validación provisional EMF. Entradas: packageName: paquete Java generado. Salida: devuelve el código fuente de validación. */ private String validationSource(String packageName){return """
            package %s;
            import java.util.List;
            import org.eclipse.emf.ecore.EObject;
            /** Side-effect-free validation facade for EMF drafts. */
            public final class HydraEmfValidationAdapter { public List<HydraSemanticRuntime.Diagnostic> validate(EObject object,String trigger){return HydraEmfRuntime.validateHost(object,trigger);} }
            """.formatted(packageName);}

    /** Descripción: Genera un comando reversible para quick fixes usando ChangeCommand. Entradas: packageName: paquete Java generado. Salida: devuelve el código fuente del comando. */ private String quickFixSource(String packageName){return """
            package %s;
            import java.util.Map;
            import java.util.concurrent.atomic.AtomicBoolean;
            import org.eclipse.emf.edit.command.ChangeCommand;
            import org.eclipse.emf.ecore.EObject;
            /** Reversible EMF command that applies one HYDRA quick fix and records every host-model mutation. */
            public final class HydraEmfQuickFixCommand extends ChangeCommand {
                private final EObject target;private final String diagnosticId;private final String actionId;private final Map<String,Object> parameters;private final AtomicBoolean applied=new AtomicBoolean(false);
                public HydraEmfQuickFixCommand(EObject target,String diagnosticId,String actionId,Map<String,Object> parameters){super(target);this.target=target;this.diagnosticId=diagnosticId;this.actionId=actionId;this.parameters=parameters==null?Map.of():Map.copyOf(parameters);setLabel("HYDRA quick fix");}
                @Override protected boolean prepare(){return target!=null;}
                @Override protected void doExecute(){applied.set(HydraEmfRuntime.applyQuickFix(target,diagnosticId,actionId,parameters));if(!applied.get())throw new org.eclipse.emf.common.command.AbortExecutionException();}
                public boolean applied(){return applied.get();}
            }
            """.formatted(packageName);}

    /** Descripción: Genera la fachada concreta de operaciones EMF sobre el lifecycle reversible. Entradas: packageName: paquete Java generado. Salida: devuelve el código fuente de operaciones. */ private String operationSource(String packageName){return """
            package %s;
            import java.util.Map;
            import java.util.concurrent.atomic.AtomicReference;
            import org.eclipse.emf.edit.command.ChangeCommand;
            import org.eclipse.emf.ecore.EObject;
            /** Executes HYDRA operations through ChangeCommand so COMMAND operations participate in undo/redo and rollback. */
            public final class HydraEmfOperationAdapter {
                private final HydraEmfEditingDomain lifecycle;
                public HydraEmfOperationAdapter(HydraEmfEditingDomain lifecycle){this.lifecycle=java.util.Objects.requireNonNull(lifecycle,"lifecycle");}
                public Result invoke(EObject target,String operationId,Map<String,Object> arguments){AtomicReference<Object> value=new AtomicReference<>();ChangeCommand command=new ChangeCommand(target){@Override protected void doExecute(){value.set(HydraEmfRuntime.invoke(target,operationId,arguments));}};lifecycle.execute(command,target,"EMF_OPERATION");return new Result(true,value.get(),"Committed");}
                public record Result(boolean committed,Object value,String message){}
            }
            """.formatted(packageName);}

    /** Descripción: Genera acceso al historial de eventos confirmado sin escuchar notificaciones de borrador. Entradas: packageName: paquete Java generado. Salida: devuelve el código fuente de eventos. */ private String eventSource(String packageName){return """
            package %s;
            import java.util.List;
            import org.eclipse.emf.ecore.EObject;
            /** Exposes only events confirmed by the EMF command lifecycle; raw EMF notifications are never published as HYDRA commits. */
            public final class HydraEmfEventAdapter { public List<HydraSemanticRuntime.Event> history(EObject target){return HydraEmfRuntime.events(target);}public HydraSemanticRuntime.GamificationState gamification(EObject target){return HydraEmfRuntime.gamification(target);} }
            """.formatted(packageName);}

    /** Descripción: Genera el wrapper de CommandStack EMF que mantiene modelo y runtime alineados en execute/undo/redo. Entradas: packageName: paquete Java generado. Salida: devuelve el código fuente del lifecycle. */ private String editingDomainSource(String packageName){return """
            package %s;
            import java.util.ArrayDeque;
            import java.util.Deque;
            import java.util.Objects;
            import org.eclipse.emf.common.command.Command;
            import org.eclipse.emf.common.command.CommandStack;
            import org.eclipse.emf.ecore.EObject;
            import org.eclipse.emf.ecore.change.ChangeDescription;
            import org.eclipse.emf.ecore.change.util.ChangeRecorder;
            import org.eclipse.emf.ecore.util.EcoreUtil;
            import org.eclipse.emf.edit.domain.EditingDomain;
            /** Transaction boundary that keeps EMF host state and HYDRA observable state aligned. */
            public final class HydraEmfEditingDomain {
                private final EditingDomain domain;private final Deque<Transition> undoTransitions=new ArrayDeque<>();private final Deque<Transition> redoTransitions=new ArrayDeque<>();
                public HydraEmfEditingDomain(EditingDomain domain){this.domain=Objects.requireNonNull(domain,"domain");}
                public void baseline(EObject root){HydraEmfRuntime.synchronize(root,"EMF_BASELINE");}
                public void execute(Command command,EObject root){execute(command,root,"EMF_COMMAND");}
                public void execute(Command command,EObject root,String trigger){Objects.requireNonNull(command,"command");Objects.requireNonNull(root,"root");CommandStack stack=domain.getCommandStack();HydraSemanticRuntime.Snapshot before=HydraEmfRuntime.checkpoint(root);ChangeRecorder recorder=new ChangeRecorder(EcoreUtil.getRootContainer(root));ChangeDescription fallback=null;try{try{stack.execute(command);}catch(RuntimeException failure){fallback=recorder.endRecording();rollbackUnstackedChange(fallback,failure);HydraEmfRuntime.restoreOrRebaseline(root,before,"EMF_EXECUTE_COMPENSATE");throw failure;}fallback=recorder.endRecording();if(stack.getMostRecentCommand()!=command){rollbackUnstackedChange(fallback,null);HydraEmfRuntime.restoreOrRebaseline(root,before,"EMF_ABORT_COMPENSATE");return;}try{HydraEmfRuntime.synchronize(root,trigger);undoTransitions.push(new Transition(command,before,HydraEmfRuntime.checkpoint(root)));redoTransitions.clear();}catch(RuntimeException failure){rollbackExecutedCommand(stack,command,fallback,failure);HydraEmfRuntime.restoreOrRebaseline(root,before,"EMF_ROLLBACK");throw failure;}}finally{recorder.dispose();}}
                public void undo(EObject root){CommandStack stack=domain.getCommandStack();if(!stack.canUndo())return;Command command=stack.getUndoCommand();HydraSemanticRuntime.Snapshot current=HydraEmfRuntime.checkpoint(root);stack.undo();if(stack.getMostRecentCommand()!=command)return;Transition transition=!undoTransitions.isEmpty()&&undoTransitions.peek().command()==command?undoTransitions.peek():null;try{if(transition==null)HydraEmfRuntime.synchronize(root,"EMF_UNDO");else HydraEmfRuntime.restoreOrRebaseline(root,transition.before(),"EMF_UNDO");}catch(RuntimeException failure){if(stack.canRedo()&&stack.getRedoCommand()==command)stack.redo();HydraEmfRuntime.restoreOrRebaseline(root,current,"EMF_UNDO_COMPENSATE");throw failure;}if(transition!=null){undoTransitions.pop();redoTransitions.push(transition);}}
                public void redo(EObject root){CommandStack stack=domain.getCommandStack();if(!stack.canRedo())return;Command command=stack.getRedoCommand();HydraSemanticRuntime.Snapshot current=HydraEmfRuntime.checkpoint(root);stack.redo();if(stack.getMostRecentCommand()!=command)return;Transition transition=!redoTransitions.isEmpty()&&redoTransitions.peek().command()==command?redoTransitions.peek():null;try{if(transition==null)HydraEmfRuntime.synchronize(root,"EMF_REDO");else HydraEmfRuntime.restoreOrRebaseline(root,transition.after(),"EMF_REDO");}catch(RuntimeException failure){if(stack.canUndo()&&stack.getUndoCommand()==command)stack.undo();HydraEmfRuntime.restoreOrRebaseline(root,current,"EMF_REDO_COMPENSATE");throw failure;}if(transition!=null){redoTransitions.pop();undoTransitions.push(transition);}}
                public CommandStack commandStack(){return domain.getCommandStack();}
                private static void rollbackExecutedCommand(CommandStack stack,Command command,ChangeDescription fallback,RuntimeException failure){boolean reverted=false;try{if(stack.canUndo()&&stack.getUndoCommand()==command){stack.undo();reverted=true;}}catch(RuntimeException rollbackFailure){failure.addSuppressed(rollbackFailure);}if(!reverted)rollbackUnstackedChange(fallback,failure);}
                private static void rollbackUnstackedChange(ChangeDescription fallback,RuntimeException primary){if(fallback==null)return;try{fallback.apply();}catch(RuntimeException rollbackFailure){if(primary!=null)primary.addSuppressed(rollbackFailure);else throw rollbackFailure;}}
                private record Transition(Command command,HydraSemanticRuntime.Snapshot before,HydraSemanticRuntime.Snapshot after){}
            }
            """.formatted(packageName);}

    /** Descripción: Obtiene la información correspondiente a «readme» dentro de EmfTechnologyAdapter. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; metamodel: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TechnologyCompilationRequest request, MetaModel metamodel, String packageName){return """
            # HYDRA EMF integration

            Generated from `%s` using the canonical `MetaModel 2.1` document.

            - Native format: Ecore and Java.
            - Base package: `%s`.
            - Integration profile: `%s`.
            - Canonical JSON documents remain under `src/main/resources/hydra` for traceability.
            - `runtime-activation.json` and `HydraRuntimeActivation` provide a metamodel-scoped enable/disable gate with an activation epoch.
            - `HydraSemanticRuntime` + `HydraEmfRuntime` own side-effect-free draft validation and confirmed Events → EventFlow → Gamification commits.
            - `HydraEmfEditingDomain` aligns the EMF command stack with full HYDRA checkpoints; execute/undo/redo restore model and runtime together, while `ChangeCommand`/`ChangeRecorder` provide reversible host-model compensation.
            - Raw EMF notifications are not treated as commits, so typing/intermediate mutations cannot farm events or gamification.
            - Build with Java 21 and `mvn test` before installing the module in the host application.
            """.formatted(metamodel.id(), packageName, request.profile().id());}
}
