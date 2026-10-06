package com.example.editor.grammar.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;
import com.example.editor.grammar.diagnostic.GrammarDiagnosticCatalog;
import com.example.editor.grammar.persistence.*;
import com.example.editor.grammar.provider.*;
import com.example.editor.grammar.runtime.*;
import com.example.editor.grammar.visualization.*;

/** Twelve-stage atomic grammar compilation pipeline with immutable failed/success snapshots and last-known-good promotion. */
@Service
public class GrammarCompilationService {
    private final GrammarApplicationService grammars;
    private final GrammarToolchainProfileService profiles;
    private final GrammarProviderRegistry providers;
    private final GrammarAnalysisCoordinator analysis;
    private final GrammarCompilationRepository compilations;
    private final GrammarActivationRepository activations;
    private final GrammarNativeToolchainService nativeToolchain;
    private final GrammarClassDiagramProjectionService classDiagrams;
    private final GrammarProperties properties;
    private final GrammarCompilationProgressService progress;
    private final BoundedPermitPool compileSlots;

    /** Descripción: Inicializa una instancia de GrammarCompilationService con sus dependencias y datos de entrada. Entradas: grammars: dato de entrada necesario para la operación; profiles: archivo o contenido que debe procesarse; providers: dato de entrada necesario para la operación; analysis: dato de entrada necesario para la operación; compilations: dato de entrada necesario para la operación; activations: dato de entrada necesario para la operación; nativeToolchain: dato de entrada necesario para la operación; classDiagrams: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarCompilationService(GrammarApplicationService grammars,GrammarToolchainProfileService profiles,
            GrammarProviderRegistry providers,GrammarAnalysisCoordinator analysis,GrammarCompilationRepository compilations,
            GrammarActivationRepository activations,GrammarNativeToolchainService nativeToolchain,
            GrammarClassDiagramProjectionService classDiagrams,GrammarProperties properties,GrammarCompilationProgressService progress){
        this.grammars=grammars;this.profiles=profiles;this.providers=providers;this.analysis=analysis;this.compilations=compilations;this.activations=activations;this.nativeToolchain=nativeToolchain;this.classDiagrams=classDiagrams;this.properties=properties;this.progress=progress;
        this.compileSlots=new BoundedPermitPool(properties.getMaximumConcurrentCompilations());
    }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammarId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarCompilationSnapshot compile(String grammarId,GrammarCompilationRequest request){
        GrammarDefinitionModel grammar=grammars.find(grammarId).grammar();
        GrammarCompilationRequest effective=normalize(grammar,request);
        if(!effective.grammarId().isBlank()&&!grammarId.equals(effective.grammarId()))throw new IllegalArgumentException("Compilation grammarId does not match path");
        GrammarToolchainProfile profile=profiles.require(effective.toolchainProfileId(),grammar.technology());
        GrammarProvider provider=providers.require(grammar.technology());
        BoundedPermitPool.Permit permit=null;
        boolean progressStarted=false;
        try{
            permit=compileSlots.tryAcquire(Duration.ofMillis(properties.getPermitAcquireTimeoutMillis()));
            if(permit==null){progress.reject(effective.requestId(),grammarId,"Compilation capacity is busy; retry later");return failedCapacity(grammar,effective,profile,provider);}
            progress.start(effective.requestId(),grammarId);
            progressStarted=true;
            return executePipeline(grammar,effective,profile,provider);
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            progress.cancelBeforeStart(effective.requestId(),grammarId,"Compilation was cancelled while waiting for capacity");
            return cancelled(grammar,effective,profile,provider);
        }finally{
            if(permit!=null)permit.close();
            if(progressStarted)progress.ensureTerminal(effective.requestId(),grammarId,"Compilation terminated unexpectedly before publishing a terminal result");
        }
    }

    /** Descripción: Procesa la operación «executePipeline» coordinando las responsabilidades de GrammarCompilationService. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; profile: archivo o contenido que debe procesarse; provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationSnapshot executePipeline(GrammarDefinitionModel grammar,GrammarCompilationRequest request,
            GrammarToolchainProfile profile,GrammarProvider provider){
        List<GrammarCompilationStageResult> stages=new ArrayList<>();
        List<GrammarDiagnostic> diagnostics=new ArrayList<>();
        List<GrammarSourceDocument> docs=List.copyOf(request.documents());
        String sourceHash=GrammarHashing.sources(docs);
        GeneratedGrammarProject project=new GeneratedGrammarProject("",Map.of(),Map.of());
        GrammarRuntimeDescriptor runtime=new GrammarRuntimeDescriptor("","",List.of(),"","",Map.of());
        Map<String,String> runtimeFiles=Map.of();
        GrammarSemanticBindingMap bindings=grammar.semanticBindings();
        GrammarClassDiagramProjection classDiagram=GrammarClassDiagramProjection.empty(grammar.id(),request.sourceRevision(),sourceHash);
        String semanticContractId="";
        String semanticContractEtag="";
        CompilationStatus status=CompilationStatus.FAILED;
        try{
            markProgress(request,GrammarCompilationStage.FREEZE_DRAFT,stages.size());
            timed(stages,GrammarCompilationStage.FREEZE_DRAFT,()->{
                if(!request.expectedSourceHash().isBlank()&&!request.expectedSourceHash().equals(sourceHash))throw new IllegalStateException("Draft changed before compilation freeze");
                validateSourceLimits(docs);return Map.of("sourceHash",sourceHash,"documents",Integer.toString(docs.size()));});

            markProgress(request,GrammarCompilationStage.FULL_ANALYSIS,stages.size());
            GrammarAnalysisResult full=timedValue(stages,GrammarCompilationStage.FULL_ANALYSIS,()->analysis.analyze(grammar,
                    new GrammarAnalysisRequest(request.requestId()+":full",grammar.id(),"",request.sourceRevision(),GrammarAnalysisMode.FULL,List.of(),docs,profile.id(),sourceHash,request.options())),
                    value->Map.of("diagnostics",Integer.toString(value.diagnostics().size()),"providerHealth",value.providerHealth().status()));
            diagnostics.addAll(full.diagnostics());
            if(full.stale())throw new IllegalStateException("FULL analysis became stale before compilation completed");
            if(full.hasErrors())throw new PipelineFailure("FULL analysis contains blocking diagnostics");

            markProgress(request,GrammarCompilationStage.RESOLVE_IMPORTS,stages.size());
            timed(stages,GrammarCompilationStage.RESOLVE_IMPORTS,()->{
                long unresolved=full.diagnostics().stream().filter(item->item.code().contains("IMPORT")||item.code().equals("REFERENCE_IMPORT_MISSING")||item.code().equals("REFERENCE_NOT_VISIBLE")).count();
                if(unresolved>0)throw new PipelineFailure("Imports/scope are not fully resolved");return Map.of("unresolved",Long.toString(unresolved));});

            markProgress(request,GrammarCompilationStage.VALIDATE_SEMANTIC_BINDING,stages.size());
            bindings=timedValue(stages,GrammarCompilationStage.VALIDATE_SEMANTIC_BINDING,()->validateSemanticBindings(grammar,full.semanticCandidate()),
                    value->Map.of("bindings",Integer.toString(value.bindings().size()),"candidateOnly",value.annotations().getOrDefault("candidateOnly","false")));
            GrammarSemanticBindingMap finalBindings=bindings;
            markProgress(request,GrammarCompilationStage.RECONCILE_SEMANTIC_CONTRACT,stages.size());
            semanticContractId=timedValue(stages,GrammarCompilationStage.RECONCILE_SEMANTIC_CONTRACT,
                    ()->semanticContractId(grammar,sourceHash,finalBindings), value->Map.of("semanticContractId",value));
            semanticContractEtag=GrammarHashing.sha256(semanticContractId+"\u0000"+semanticBindingsFingerprint(finalBindings));

            markProgress(request,GrammarCompilationStage.CHECK_HYDRA_LAYERS,stages.size());
            timed(stages,GrammarCompilationStage.CHECK_HYDRA_LAYERS,()->Map.of("mode",grammar.metamodelId().isBlank()?"SEMANTIC_CONTRACT_ONLY":"METAMODEL_LINKED","metamodelId",grammar.metamodelId()));

            markProgress(request,GrammarCompilationStage.GENERATE_PROJECT,stages.size());
            project=timedValue(stages,GrammarCompilationStage.GENERATE_PROJECT,
                    ()->provider.generateProject(new GrammarProjectGenerationRequest(grammar,profile,docs,request.options())),
                    value->{validateGeneratedProject(value);return Map.of("files",Integer.toString(value.files().size()),"expandedBytes",Long.toString(value.expandedBytes()));});

            GeneratedGrammarProject finalProject=project;
            markProgress(request,GrammarCompilationStage.GENERATE_SEMANTIC_ARTIFACTS,stages.size());
            classDiagram=timedValue(stages,GrammarCompilationStage.GENERATE_SEMANTIC_ARTIFACTS,
                    ()->classDiagrams.project(grammar,docs,full),value->{
                        if(value.status()!=GrammarDiagramProjectionStatus.COMPLETE)throw new PipelineFailure("FULL compilation did not produce a complete inferred class diagram: "+value.status());
                        if(!sourceHash.equals(value.sourceHash()))throw new PipelineFailure("Inferred class diagram was projected from a different source revision");
                        return Map.of("semanticBindingHash",GrammarHashing.sha256(semanticBindingsFingerprint(finalBindings)),
                                "ecore",supportsEcore(provider)?"provider-capable":"not-required",
                                "classDiagramHash",value.projectionHash(),"classifiers",Integer.toString(value.nodes().size()),"relations",Integer.toString(value.edges().size()));
                    });

            markProgress(request,GrammarCompilationStage.MATERIALIZE_RUNTIME,stages.size());
            GrammarRuntimeBundle bundle=timedValue(stages,GrammarCompilationStage.MATERIALIZE_RUNTIME,
                    ()->provider.materializeRuntime(grammar,profile,finalProject),value->Map.of("runtimeMode",value.descriptor().mode(),"files",Integer.toString(value.files().size())));
            runtime=bundle.descriptor();
            runtimeFiles=Map.copyOf(bundle.files());
            GrammarRuntimeDescriptor finalRuntime=runtime;

            markProgress(request,GrammarCompilationStage.SMOKE_TEST,stages.size());
            GrammarNativeValidationResult nativeEvidence=timedValue(stages,GrammarCompilationStage.SMOKE_TEST,()->{
                smoke(grammar,docs,finalProject,finalRuntime);
                GrammarNativeValidationResult evidence=nativeToolchain.validate(grammar,profile,finalProject,finalRuntime);
                if(evidence.verificationDeferred()){
                    diagnostics.add(nativeVerificationDeferredDiagnostic(grammar,profile,evidence));
                    return evidence;
                }
                if(evidence.definitiveFailure())throw new NativeToolchainFailure(evidence);
                return evidence;
            },value->{
                Map<String,String> evidence=new LinkedHashMap<>(smoke(grammar,docs,finalProject,finalRuntime));
                evidence.put("nativeValidation",value.successful()?"PASSED":"DEFERRED");
                evidence.put("nativeSteps",Integer.toString(value.steps().size()));
                evidence.put("nativeDurationMillis",Long.toString(value.steps().stream().mapToLong(GrammarNativeValidationStep::durationMillis).sum()));
                evidence.put("runtimeFilesCaptured",Integer.toString(value.runtimeFiles().size()));
                evidence.put("reproducibilityFilesCaptured",Integer.toString(value.reproducibilityFiles().size()));
                return Map.copyOf(evidence);
            });
            runtimeFiles=mergeRuntimeFiles(runtimeFiles,nativeEvidence.runtimeFiles());
            project=mergeGeneratedProject(finalProject,nativeEvidence.reproducibilityFiles());
            validateGeneratedProject(project);
            GeneratedGrammarProject registeredProject=project;
            Map<String,String> registeredRuntimeFiles=runtimeFiles;
            markProgress(request,GrammarCompilationStage.REGISTER_ARTIFACTS,stages.size());
            List<GeneratedArtifactRef> artifacts=timedValue(stages,GrammarCompilationStage.REGISTER_ARTIFACTS,
                    ()->artifactRefs(registeredProject,registeredRuntimeFiles),value->Map.of("artifacts",Integer.toString(value.size()),
                            "runtimeArtifacts",Integer.toString(registeredRuntimeFiles.size()),
                            "reproducibilityFiles",Integer.toString(nativeEvidence.reproducibilityFiles().size())));
            Map<String,String> checksums=new TreeMap<>();artifacts.forEach(ref->checksums.put(ref.path(),ref.sha256()));
            if(!classDiagram.projectionHash().isBlank())checksums.put("hydra/inferred-class-diagram",classDiagram.projectionHash());

            markProgress(request,GrammarCompilationStage.PROMOTE_SNAPSHOT,stages.size());
            timed(stages,GrammarCompilationStage.PROMOTE_SNAPSHOT,()->Map.of("activationRequested",Boolean.toString(request.activateOnSuccess())));
            status=CompilationStatus.SUCCESS;
            GrammarCompilationSnapshot snapshot=snapshot(grammar,request,profile,provider,status,diagnostics,finalBindings,classDiagram,semanticContractId,semanticContractEtag,finalRuntime,runtimeFiles,finalProject,artifacts,checksums,stages,Map.of("requestId",request.requestId(),"immutable","true","analysisMode","FULL"));
            compilations.create(snapshot);
            if(request.activateOnSuccess())activate(snapshot.id());
            progress.complete(request.requestId(),grammar.id(),request.activateOnSuccess()?"Compilation completed and activated":"Compilation completed");
            return snapshot;
        }catch(java.util.concurrent.CancellationException exception){
            status=CompilationStatus.CANCELLED;
            stages.add(new GrammarCompilationStageResult(nextStage(stages),"CANCELLED",0,List.of("Compilation was cancelled"),Map.of()));
        }catch(RuntimeException exception){
            String detail=exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage();
            GrammarCompilationStage stage=stages.stream().filter(item->!item.successful()).reduce((a,b)->b).map(GrammarCompilationStageResult::stage).orElseGet(()->nextStage(stages));
            if(stages.stream().noneMatch(item->item.stage()==stage&&!item.successful()))stages.add(new GrammarCompilationStageResult(stage,"FAILED",0,List.of(detail),Map.of()));
            if(exception instanceof NativeToolchainFailure nativeFailure){
                diagnostics.add(nativeVerificationFailureDiagnostic(grammar,profile,nativeFailure.evidence));
            }else if(diagnostics.stream().noneMatch(item->item.severity()==GrammarSeverity.ERROR)){
                diagnostics.add(compilationStageDiagnostic(stage,detail));
            }
        }
        // A failed snapshot is never activated, but deterministic artifacts already generated before the failing verification
        // remain attached to the immutable snapshot so the user can inspect/download exactly what failed.
        boolean generatedAvailable=project!=null&&!project.files().isEmpty();
        GrammarClassDiagramProjection failedDiagram=classDiagram==null?GrammarClassDiagramProjection.empty(grammar.id(),request.sourceRevision(),sourceHash):classDiagram;
        GrammarRuntimeDescriptor failedRuntime=runtime==null?new GrammarRuntimeDescriptor("","",List.of(),"","",Map.of()):runtime;
        Map<String,String> failedRuntimeFiles=runtimeFiles==null?Map.of():runtimeFiles;
        GeneratedGrammarProject failedProject=project==null?new GeneratedGrammarProject("",Map.of(),Map.of()):project;
        List<GeneratedArtifactRef> failedArtifacts=generatedAvailable?artifactRefs(failedProject,failedRuntimeFiles):List.of();
        Map<String,String> failedChecksums=new TreeMap<>();failedArtifacts.forEach(ref->failedChecksums.put(ref.path(),ref.sha256()));
        GrammarCompilationSnapshot failed=snapshot(grammar,request,profile,provider,status,diagnostics,bindings,failedDiagram,semanticContractId,semanticContractEtag,
                failedRuntime,failedRuntimeFiles,failedProject,failedArtifacts,failedChecksums,stages,
                Map.of("requestId",request.requestId(),"immutable","true","activePreserved","true","generatedArtifactsAvailable",Boolean.toString(generatedAvailable),"partialArtifactsPublished","false"));
        compilations.create(failed);
        GrammarCompilationStage failedStage=stages.stream().filter(item->!item.successful()).reduce((a,b)->b).map(GrammarCompilationStageResult::stage).orElseGet(()->nextStage(stages));
        if(status==CompilationStatus.CANCELLED)
            progress.cancel(request.requestId(),grammar.id(),"Compilation was cancelled");
        else
            progress.fail(request.requestId(),grammar.id(),failedStage,(int)stages.stream().filter(GrammarCompilationStageResult::successful).count(),diagnostics.isEmpty()?"Compilation failed":diagnostics.get(diagnostics.size()-1).message());
        return failed;
    }

    /** Descripción: Ejecuta la operación «nativeVerificationDeferredDiagnostic» correspondiente a la responsabilidad de NativeDeferredAdvice. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; evidence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarDiagnostic nativeVerificationDeferredDiagnostic(GrammarDefinitionModel grammar,GrammarToolchainProfile profile,GrammarNativeValidationResult evidence){
        GrammarNativeValidationStep step=evidence.steps().stream().filter(item->!"PASSED".equals(item.status())).findFirst().orElse(new GrammarNativeValidationStep("native-verification","TOOL_UNAVAILABLE",0,null,List.of()));
        String tool=nativeToolName(grammar,profile);
        String raw=step.diagnostics().stream().filter(value->value!=null&&!value.isBlank()).findFirst().orElse("");
        NativeDeferredAdvice advice=nativeDeferredAdvice(grammar,profile,step,tool,raw);
        String reason=advice.cause();
        String solution=advice.solution();
        return GrammarDiagnosticCatalog.simple("GRAMMAR_NATIVE_VERIFICATION_DEFERRED",GrammarSeverity.WARNING,
                "Infraestructura generada; comprobación externa pendiente",
                "HYDRA ha generado el proyecto y su runtime, pero no ha podido ejecutar la comprobación nativa de "+grammar.technology()+" en este servidor.",
                reason,"La infraestructura generada queda disponible y el snapshot puede activarse porque las comprobaciones internas deterministas han terminado correctamente.",
                solution,"",new TextRange(null,null),Map.of("stage",GrammarCompilationStage.SMOKE_TEST.name(),"nativeStep",step.id(),"nativeStatus",step.status(),"tool",tool));
    }


    /** Descripción: Ejecuta la operación «nativeDeferredAdvice» correspondiente a la responsabilidad de NativeDeferredAdvice. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; step: dato de entrada necesario para la operación; tool: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static NativeDeferredAdvice nativeDeferredAdvice(GrammarDefinitionModel grammar,GrammarToolchainProfile profile,GrammarNativeValidationStep step,String tool,String raw){
        String lower=raw.toLowerCase(Locale.ROOT);
        if(lower.contains("etarget")||lower.contains("no matching version found")){
            return new NativeDeferredAdvice(
                    "El perfil "+profile.id()+" solicita una versión de dependencia que el repositorio no ofrece. Este problema pertenece al perfil de herramientas, no a las reglas de la gramática.",
                    "Actualiza la versión fijada en el perfil "+profile.id()+" a una versión publicada y vuelve a compilar. No cambies la gramática para resolver este error de dependencias.");
        }
        if(lower.contains("enotfound")||lower.contains("eai_again")||lower.contains("econnrefused")||lower.contains("etimedout")||lower.contains("network is unreachable")||lower.contains("temporary failure in name resolution")){
            String repository=grammar.technology()==GrammarTechnology.LANGIUM?"registro npm":"repositorio Maven";
            return new NativeDeferredAdvice(
                    "El proceso backend no puede acceder al "+repository+" durante la instalación de las dependencias del proyecto generado.",
                    "Restablece el acceso de red/proxy del proceso backend al "+repository+" y vuelve a compilar. La gramática ya ha superado el análisis interno y no necesita cambios por este fallo de conectividad.");
        }
        if(lower.contains("unable to get local issuer certificate")||lower.contains("pkix path building failed")){
            return new NativeDeferredAdvice(
                    "La conexión al repositorio de dependencias ha sido rechazada por la cadena de certificados configurada para el proceso backend.",
                    "Configura en Node/npm o Maven/JDK el certificado corporativo/CA que utiliza tu red y vuelve a compilar. No modifiques las reglas de la gramática.");
        }
        if(lower.contains("could not transfer artifact")||lower.contains("failed to read artifact descriptor")){
            return new NativeDeferredAdvice(
                    "Maven no ha podido descargar uno de los artefactos necesarios para verificar el proyecto generado.",
                    "Comprueba el repositorio/proxy de Maven y la coordenada indicada en Detalles técnicos; después vuelve a compilar. El borrador de la gramática no necesita cambios por este problema de descarga.");
        }
        if(step.id().endsWith("-dependencies")){
            String manager=grammar.technology()==GrammarTechnology.LANGIUM||"TypeScript".equalsIgnoreCase(profile.targetLanguage())?"npm":"Maven";
            return new NativeDeferredAdvice(
                    manager+" no ha podido preparar las dependencias del proyecto generado. La estructura de la gramática no es la causa de este fallo.",
                    "Corrige la instalación o configuración de "+manager+" que aparece en Detalles técnicos y vuelve a compilar. HYDRA conserva la infraestructura generada mientras tanto.");
        }
        String reason=switch(step.status()){
            case "SKIPPED" -> "La comprobación externa está temporalmente ocupada o protegida por el limitador del servidor.";
            case "TIMED_OUT" -> "La comprobación externa superó el tiempo máximo configurado antes de poder certificar el proyecto generado.";
            default -> "El proceso backend no encuentra "+tool+" en su entorno de ejecución.";
        };
        String solution=switch(grammar.technology()){
            case LANGIUM -> "Instala Node.js y npm o haz que npm sea visible en PATH para el proceso backend de HYDRA; después vuelve a compilar para completar la comprobación nativa.";
            case XTEXT -> "Instala JDK 21+ y Maven y hazlos visibles en PATH/JAVA_HOME para el proceso backend; después vuelve a compilar para ejecutar la generación MWE2 y los tests Xtext.";
            case ANTLR -> "Haz visibles Maven y el JDK requerido por el perfil ANTLR para el proceso backend; si el perfil es TypeScript, añade también Node.js/npm. Después vuelve a compilar.";
        };
        return new NativeDeferredAdvice(reason,solution);
    }

    private record NativeDeferredAdvice(String cause,String solution){}

    /** Descripción: Ejecuta la operación «nativeVerificationFailureDiagnostic» correspondiente a la responsabilidad de NativeDeferredAdvice. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; evidence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarDiagnostic nativeVerificationFailureDiagnostic(GrammarDefinitionModel grammar,GrammarToolchainProfile profile,GrammarNativeValidationResult evidence){
        GrammarNativeValidationStep step=evidence.steps().stream().filter(item->!"PASSED".equals(item.status())).findFirst().orElse(new GrammarNativeValidationStep("native-verification","FAILED",0,null,List.of()));
        String raw=step.diagnostics().stream().filter(value->value!=null&&!value.isBlank()).findFirst().orElse("La herramienta nativa terminó con error sin devolver un mensaje adicional.");
        String lower=raw.toLowerCase(Locale.ROOT);
        String cause;String solution;
        if(lower.contains("etarget")||lower.contains("no matching version")){
            cause="El gestor de dependencias no encuentra una de las versiones fijadas por el perfil "+profile.id()+".";
            solution="Usa el perfil actual recomendado para "+grammar.technology()+" o corrige la versión fijada en la configuración del perfil; no cambies las reglas de la gramática para resolver este error de dependencias.";
        }else if(lower.contains("enotfound")||lower.contains("eai_again")||lower.contains("econnrefused")||lower.contains("unable to get local issuer certificate")){
            cause="La herramienta de build no puede acceder al repositorio de dependencias que necesita para verificar el proyecto generado.";
            solution="Restablece el acceso del proceso backend al registro de dependencias (proxy/certificado/red) y vuelve a compilar; la gramática no necesita cambios por este fallo de conectividad.";
        }else if("langium-build".equals(step.id())){
            cause="El proyecto Langium se generó, pero `langium generate` o TypeScript rechazó la infraestructura producida.";
            solution="No modifiques la gramática si el panel Problemas no muestra errores de reglas: este fallo corresponde al proyecto generado por HYDRA. Revisa el detalle técnico de esta etapa y corrige el generador/perfil antes de volver a compilar.";
        }else if("xtext-generate".equals(step.id())){
            cause="MWE2 no pudo generar el runtime Xtext a partir del proyecto producido por HYDRA.";
            solution="Si no hay errores de gramática en Problemas, no cambies las reglas: corrige el workflow/proyecto Xtext generado o el perfil de Xtext y vuelve a compilar.";
        }else if("xtext-build".equals(step.id())){
            cause="Maven pudo iniciar el proyecto Xtext, pero la compilación o sus pruebas generadas fallaron.";
            solution="Si la gramática ya aparece sin errores, conserva sus reglas y corrige el módulo generado que indique Maven en Detalles técnicos; después vuelve a compilar.";
        }else if(step.id().startsWith("antlr")){
            cause="ANTLR o el compilador del lenguaje destino rechazó el proyecto generado en la etapa `"+step.id()+"`.";
            solution="Corrige exactamente el archivo/regla que indique la salida de ANTLR en Detalles técnicos; si no señala una regla de usuario, el fallo está en la infraestructura generada y no debes modificar la gramática.";
        }else{
            cause="La etapa nativa `"+step.id()+"` terminó con estado "+step.status()+" después de generar el proyecto.";
            solution="Corrige el elemento concreto indicado en Detalles técnicos y vuelve a compilar. El snapshot anterior permanece activo hasta que esta comprobación termine correctamente.";
        }
        return new GrammarDiagnostic("GRAMMAR_NATIVE_VERIFICATION_FAILED",GrammarSeverity.ERROR,
                "La infraestructura generada no supera la comprobación de "+grammar.technology(),
                "La etapa `"+step.id()+"` ha fallado. HYDRA conserva el proyecto generado para que puedas inspeccionar exactamente el artefacto rechazado.",
                cause,"La nueva infraestructura no se activa, pero tampoco se pierde el proyecto generado ni se modifica el último snapshot válido.",solution,"",new TextRange(null,null),
                "","","","",raw,List.of(),List.of(),
                Map.of("stage",GrammarCompilationStage.SMOKE_TEST.name(),"nativeStep",step.id(),"nativeStatus",step.status()));
    }

    /** Descripción: Ejecuta la operación «compilationStageDiagnostic» correspondiente a la responsabilidad de NativeDeferredAdvice. Entradas: stage: dato de entrada necesario para la operación; detail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarDiagnostic compilationStageDiagnostic(GrammarCompilationStage stage,String detail){
        String title;String cause;String solution;
        switch(stage){
            case GENERATE_PROJECT -> {title="No se ha podido generar el proyecto de lenguaje";cause="El proveedor no pudo convertir el borrador validado en la estructura de proyecto requerida por la tecnología seleccionada.";solution="Revisa en Problemas el documento de entrada, el nombre de la gramática y el perfil de tecnología; corrige el dato concreto que aparezca en Detalles técnicos y vuelve a compilar.";}
            case GENERATE_SEMANTIC_ARTIFACTS -> {title="No se ha podido completar el modelo inferido";cause="La gramática pasó el análisis, pero la proyección semántica no pudo producir una estructura completa para esta revisión.";solution="Corrige la regla o referencia indicada por el diagnóstico de inferencia y vuelve a compilar.";}
            case MATERIALIZE_RUNTIME -> {title="No se ha podido preparar el entorno de ejecución";cause="El proveedor generó el proyecto, pero no pudo materializar los archivos de runtime declarados por el perfil.";solution="Revisa el perfil de tecnología y el archivo concreto indicado en Detalles técnicos; la gramática solo debe cambiarse si el diagnóstico apunta a una regla de usuario.";}
            case RESOLVE_IMPORTS -> {title="Hay una importación o referencia sin resolver";cause="La compilación encontró una dependencia de gramática que no puede resolverse de forma inequívoca.";solution="Abre el diagnóstico de la regla/importación afectada y corrige exactamente su ruta, nombre o referencia antes de volver a compilar.";}
            default -> {title="La compilación se ha detenido";cause="La etapa `"+stage.name()+"` no ha podido completar su comprobación.";solution="Corrige el problema concreto mostrado en este panel y vuelve a compilar; HYDRA no sustituirá el último snapshot válido mientras exista este error.";}
        }
        return new GrammarDiagnostic("GRAMMAR_COMPILATION_"+stage.name(),GrammarSeverity.ERROR,title,
                "HYDRA ha detenido la compilación en la etapa correspondiente para evitar activar infraestructura inconsistente.",cause,
                "El borrador y el último snapshot válido se conservan sin cambios.",solution,"",new TextRange(null,null),
                "","","","",detail,List.of(),List.of(),Map.of("stage",stage.name()));
    }

    /** Descripción: Ejecuta la operación «nativeToolName» correspondiente a la responsabilidad de NativeDeferredAdvice. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String nativeToolName(GrammarDefinitionModel grammar,GrammarToolchainProfile profile){
        if(grammar.technology()==GrammarTechnology.LANGIUM)return "npm";
        if(grammar.technology()==GrammarTechnology.XTEXT)return "Maven/JDK";
        return "TypeScript".equalsIgnoreCase(profile.targetLanguage())?"Maven + npm":"Maven/JDK";
    }

    /** Descripción: Ejecuta la operación «markProgress» correspondiente a la responsabilidad de NativeDeferredAdvice. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; stage: dato de entrada necesario para la operación; completed: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void markProgress(GrammarCompilationRequest request,GrammarCompilationStage stage,int completed){progress.stage(request.requestId(),request.grammarId(),stage,completed,stage.name());}

    /** Descripción: Obtiene la información correspondiente a «find» dentro de GrammarCompilationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarCompilationSnapshot find(String id){return compilations.findById(id).orElseThrow(()->new NoSuchElementException("Grammar compilation not found: "+id)).document();}
    /** Descripción: Obtiene la información correspondiente a «list» dentro de GrammarCompilationService. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<GrammarCompilationSnapshot> list(String grammarId){return compilations.listIds().stream().map(compilations::findById).flatMap(Optional::stream).map(value->value.document()).filter(snapshot->snapshot.grammarId().equals(grammarId)).sorted(Comparator.comparing(GrammarCompilationSnapshot::createdAt).reversed()).toList();}
    /** Descripción: Ejecuta la operación «activate» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarActivationState activate(String snapshotId){GrammarCompilationSnapshot snapshot=find(snapshotId);if(!snapshot.successful())throw new IllegalArgumentException("Only a successful grammar snapshot can be activated");GrammarActivationState state=new GrammarActivationState(snapshot.grammarId(),snapshot.id(),snapshot.sourceHash(),Instant.now());activations.save(state);return state;}
    /** Descripción: Ejecuta la operación «active» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<GrammarCompilationSnapshot> active(String grammarId){return activations.find(grammarId).map(value->find(value.document().snapshotId()));}
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedPermitPool.Snapshot capacityStatus(){return compileSlots.snapshot();}

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationRequest normalize(GrammarDefinitionModel grammar,GrammarCompilationRequest request){
        List<GrammarSourceDocument> docs=request==null||request.documents().isEmpty()?grammar.documents():request.documents();
        String profile=request==null||request.toolchainProfileId().isBlank()?grammar.toolchainProfileId():request.toolchainProfileId();
        return new GrammarCompilationRequest(request==null?"":request.requestId(),grammar.id(),request==null?0:request.sourceRevision(),docs,profile,
                request==null?GrammarHashing.sources(docs):request.expectedSourceHash(),request!=null&&request.activateOnSuccess(),request==null?Map.of():request.options());
    }
    /** Descripción: Valida las condiciones necesarias para «validateSourceLimits» y rechaza entradas no válidas. Entradas: docs: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateSourceLimits(List<GrammarSourceDocument> docs){
        if(docs.size()>properties.getMaximumDocuments())throw new PipelineFailure("Grammar document limit exceeded");
        long total=0;for(GrammarSourceDocument document:docs){long size=document.content().getBytes(StandardCharsets.UTF_8).length;if(size>properties.getMaximumDocumentBytes())throw new PipelineFailure("Grammar document size limit exceeded: "+document.relativePath());total+=size;}if(total>properties.getMaximumGrammarBytes())throw new PipelineFailure("Grammar total size limit exceeded");
    }
    /** Descripción: Valida las condiciones necesarias para «validateSemanticBindings» y rechaza entradas no válidas. Entradas: grammar: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarSemanticBindingMap validateSemanticBindings(GrammarDefinitionModel grammar,GrammarSemanticBindingMap candidate){
        GrammarSemanticBindingMap result=grammar.semanticBindings().bindings().isEmpty()?candidate:grammar.semanticBindings();
        if(result.bindings().size()>properties.getMaximumSemanticBindings())throw new PipelineFailure("Semantic binding limit exceeded");
        // Grammar-first ANTLR may use the neutral candidate inferred from parser rules. Explicit bindings are
        // required only when inference cannot produce a complete semantic projection; that is enforced by
        // the empty-binding and COMPLETE class-diagram checks below instead of blocking every ANTLR draft.
        if(result.bindings().isEmpty())throw new PipelineFailure("No semantic contract could be derived from the grammar");
        if(result.bindings().stream().anyMatch(binding->binding.grammarElementId().isBlank()||binding.semanticElementId().isBlank()))throw new PipelineFailure("Semantic binding contains an empty grammar/semantic target");
        return result;
    }
    /** Descripción: Evalúa la condición «supportsEcore» sin modificar el estado externo. Entradas: provider: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean supportsEcore(GrammarProvider provider){return provider.descriptor().capabilityDescriptor().declares(GrammarProviderCapability.ECORE_INFERENCE)||provider.descriptor().capabilityDescriptor().declares(GrammarProviderCapability.ECORE_IMPORT);}
    /** Descripción: Valida las condiciones necesarias para «validateGeneratedProject» y rechaza entradas no válidas. Entradas: project: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateGeneratedProject(GeneratedGrammarProject project){if(project.files().size()>properties.getMaximumGeneratedFiles())throw new PipelineFailure("Generated file limit exceeded");if(project.expandedBytes()>properties.getMaximumGeneratedBytes())throw new PipelineFailure("Generated byte limit exceeded");}
    /** Descripción: Ejecuta la operación «smoke» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammar: dato de entrada necesario para la operación; docs: dato de entrada necesario para la operación; project: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> smoke(GrammarDefinitionModel grammar,List<GrammarSourceDocument> docs,GeneratedGrammarProject project,GrammarRuntimeDescriptor runtime){
        if(project.files().isEmpty())throw new PipelineFailure("Provider produced no generated project");
        for(GrammarSourceDocument doc:docs){String generated=project.files().get(doc.relativePath());if(generated==null||!generated.equals(doc.content()))throw new PipelineFailure("Generated project does not preserve frozen source: "+doc.relativePath());}
        if(runtime.mode().isBlank())throw new PipelineFailure("Provider did not materialize a textual runtime descriptor");
        if(runtime.entryRule().isBlank()&&!grammar.entryRule().isBlank())throw new PipelineFailure("Runtime lost the grammar entry rule");
        return Map.of("sourceRoundTrip","PASS","runtimeDescriptor","PASS");
    }
    /** Descripción: Ejecuta la operación «artifactRefs» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: project: dato de entrada necesario para la operación; runtimeFiles: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<GeneratedArtifactRef> artifactRefs(GeneratedGrammarProject project,Map<String,String> runtimeFiles){
        List<GeneratedArtifactRef> result=new ArrayList<>();
        project.files().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->result.add(new GeneratedArtifactRef(entry.getKey(),GrammarHashing.sha256(entry.getValue()),entry.getValue().getBytes(StandardCharsets.UTF_8).length,kind(entry.getKey()))));
        runtimeFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->result.add(new GeneratedArtifactRef("runtime/"+entry.getKey(),GrammarHashing.sha256(entry.getValue()),entry.getValue().getBytes(StandardCharsets.UTF_8).length,"RUNTIME_FILE")));
        return List.copyOf(result);
    }
    /** Descripción: Actualiza el estado gestionado por GrammarCompilationService mediante la operación «mergeGeneratedProject». Entradas: project: dato de entrada necesario para la operación; captured: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GeneratedGrammarProject mergeGeneratedProject(GeneratedGrammarProject project,Map<String,String> captured){
        if(captured==null||captured.isEmpty())return project;
        Map<String,String> files=new LinkedHashMap<>(project.files());
        captured.forEach((path,content)->{String previous=files.putIfAbsent(path,content);if(previous!=null&&!Objects.equals(previous,content))throw new PipelineFailure("Native reproducibility file conflicts with generated project: "+path);});
        Map<String,String> metadata=new LinkedHashMap<>(project.metadata());metadata.put("nativeReproducibilityFiles",Integer.toString(captured.size()));
        return new GeneratedGrammarProject(project.projectId(),files,metadata);
    }
    /** Descripción: Actualiza el estado gestionado por GrammarCompilationService mediante la operación «mergeRuntimeFiles». Entradas: providerFiles: archivo o contenido que debe procesarse; nativeFiles: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> mergeRuntimeFiles(Map<String,String> providerFiles,Map<String,String> nativeFiles){
        if(nativeFiles==null||nativeFiles.isEmpty())return providerFiles==null?Map.of():Map.copyOf(providerFiles);
        Map<String,String> result=new LinkedHashMap<>();if(providerFiles!=null)result.putAll(providerFiles);
        nativeFiles.forEach((path,content)->{String previous=result.putIfAbsent(path,content);if(previous!=null&&!Objects.equals(previous,content))throw new PipelineFailure("Native runtime file conflicts with provider runtime materialization: "+path);});
        return Map.copyOf(result);
    }
    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String kind(String path){if(path.endsWith(".xtext")||path.endsWith(".langium")||path.endsWith(".g4"))return "GRAMMAR_SOURCE";if(path.endsWith("pom.xml")||path.endsWith("package.json")||path.endsWith("tsconfig.json"))return "BUILD_DESCRIPTOR";if(path.contains("PROVENANCE"))return "PROVENANCE";return "GENERATED_FILE";}
    /** Descripción: Ejecuta la operación «semanticContractId» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammar: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String semanticContractId(GrammarDefinitionModel grammar,String sourceHash,GrammarSemanticBindingMap bindings){return grammar.metamodelId().isBlank()?"grammar."+grammar.id()+"."+sourceHash.substring(0,12):grammar.metamodelId();}
    /** Descripción: Ejecuta la operación «semanticBindingsFingerprint» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: map: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String semanticBindingsFingerprint(GrammarSemanticBindingMap map){StringBuilder result=new StringBuilder();map.bindings().stream().sorted(Comparator.comparing(GrammarSemanticBinding::grammarElementId).thenComparing(GrammarSemanticBinding::semanticElementId)).forEach(binding->result.append(binding.grammarElementId()).append('\0').append(binding.grammarElementKind()).append('\0').append(binding.semanticElementId()).append('\0').append(binding.semanticElementKind()).append('\0').append(binding.confidence()).append('\n'));return result.toString();}
    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; profile: archivo o contenido que debe procesarse; provider: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; classDiagram: dato de entrada necesario para la operación; semanticContractId: identificador que delimita el recurso objetivo; semanticContractEtag: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; runtimeFiles: archivo o contenido que debe procesarse; project: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación; checksums: dato de entrada necesario para la operación; stages: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationSnapshot snapshot(GrammarDefinitionModel grammar,GrammarCompilationRequest request,GrammarToolchainProfile profile,GrammarProvider provider,CompilationStatus status,List<GrammarDiagnostic> diagnostics,GrammarSemanticBindingMap bindings,GrammarClassDiagramProjection classDiagram,String semanticContractId,String semanticContractEtag,GrammarRuntimeDescriptor runtime,Map<String,String> runtimeFiles,GeneratedGrammarProject project,List<GeneratedArtifactRef> artifacts,Map<String,String> checksums,List<GrammarCompilationStageResult> stages,Map<String,String> metadata){String sourceHash=GrammarHashing.sources(request.documents());String id=grammar.id()+"-"+Instant.now().toEpochMilli()+"-"+sourceHash.substring(0,12);return new GrammarCompilationSnapshot("1.0",id,grammar.id(),request.sourceRevision(),sourceHash,grammar.technology(),provider.descriptor().id(),profile.providerVersion(),profile.id(),GrammarHashing.toolchain(profile),semanticContractId,semanticContractEtag,status,diagnostics,bindings,classDiagram,runtime,runtimeFiles,project,artifacts,checksums,stages,Instant.now(),metadata);}
    /** Descripción: Ejecuta la operación «failedCapacity» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; profile: archivo o contenido que debe procesarse; provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationSnapshot failedCapacity(GrammarDefinitionModel grammar,GrammarCompilationRequest request,GrammarToolchainProfile profile,GrammarProvider provider){List<GrammarCompilationStageResult> stages=List.of(new GrammarCompilationStageResult(GrammarCompilationStage.FREEZE_DRAFT,"FAILED",0,List.of("Compilation capacity is busy; retry later"),Map.of()));GrammarDiagnostic diagnostic=GrammarDiagnosticCatalog.simple("GRAMMAR_COMPILATION_CAPACITY",GrammarSeverity.WARNING,"La capacidad de compilación está ocupada","No hay un slot de compilación disponible dentro del tiempo configurado.","El límite de concurrencia protege memoria y procesos nativos.","No se modifica ningún snapshot activo.","Reintentar cuando termine otra compilación o ajustar el límite del servidor.","",new TextRange(null,null),Map.of());GrammarCompilationSnapshot snapshot=snapshot(grammar,request,profile,provider,CompilationStatus.FAILED,List.of(diagnostic),grammar.semanticBindings(),GrammarClassDiagramProjection.empty(grammar.id(),request.sourceRevision(),GrammarHashing.sources(request.documents())),"","",new GrammarRuntimeDescriptor("","",List.of(),"","",Map.of()),Map.of(),new GeneratedGrammarProject("",Map.of(),Map.of()),List.of(),Map.of(),stages,Map.of("capacity","busy","immutable","true"));compilations.create(snapshot);return snapshot;}
    /** Descripción: Evalúa la condición «cancelled» sin modificar el estado externo. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; profile: archivo o contenido que debe procesarse; provider: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private GrammarCompilationSnapshot cancelled(GrammarDefinitionModel grammar,GrammarCompilationRequest request,GrammarToolchainProfile profile,GrammarProvider provider){GrammarCompilationSnapshot snapshot=snapshot(grammar,request,profile,provider,CompilationStatus.CANCELLED,List.of(),grammar.semanticBindings(),GrammarClassDiagramProjection.empty(grammar.id(),request.sourceRevision(),GrammarHashing.sources(request.documents())),"","",new GrammarRuntimeDescriptor("","",List.of(),"","",Map.of()),Map.of(),new GeneratedGrammarProject("",Map.of(),Map.of()),List.of(),Map.of(),List.of(new GrammarCompilationStageResult(GrammarCompilationStage.FREEZE_DRAFT,"CANCELLED",0,List.of("Compilation thread interrupted"),Map.of())),Map.of("immutable","true"));compilations.create(snapshot);return snapshot;}
    /** Descripción: Ejecuta la operación «nextStage» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: stages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarCompilationStage nextStage(List<GrammarCompilationStageResult> stages){Set<GrammarCompilationStage>done=new HashSet<>();stages.forEach(stage->done.add(stage.stage()));return Arrays.stream(GrammarCompilationStage.values()).filter(stage->!done.contains(stage)).findFirst().orElse(GrammarCompilationStage.PROMOTE_SNAPSHOT);}
    /** Descripción: Ejecuta la operación «timed» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: stages: dato de entrada necesario para la operación; stage: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> timed(List<GrammarCompilationStageResult> stages,GrammarCompilationStage stage,Stage action){long start=System.nanoTime();try{Map<String,String>metadata=action.run();stages.add(new GrammarCompilationStageResult(stage,"SUCCESS",elapsed(start),List.of(),metadata));return metadata;}catch(RuntimeException exception){stages.add(new GrammarCompilationStageResult(stage,"FAILED",elapsed(start),List.of(exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage()),Map.of()));throw exception;}}
    /** Descripción: Ejecuta la operación «timedValue» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: stages: dato de entrada necesario para la operación; stage: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static <T>T timedValue(List<GrammarCompilationStageResult>stages,GrammarCompilationStage stage,ValueStage<T>action,java.util.function.Function<T,Map<String,String>>metadata){long start=System.nanoTime();try{T value=action.run();stages.add(new GrammarCompilationStageResult(stage,"SUCCESS",elapsed(start),List.of(),metadata.apply(value)));return value;}catch(RuntimeException exception){stages.add(new GrammarCompilationStageResult(stage,"FAILED",elapsed(start),List.of(exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage()),Map.of()));throw exception;}}
    /** Descripción: Ejecuta la operación «elapsed» correspondiente a la responsabilidad de GrammarCompilationService. Entradas: start: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
    @FunctionalInterface private interface Stage{Map<String,String>run();}
    @FunctionalInterface private interface ValueStage<T>{T run();}
    private static final class NativeToolchainFailure extends RuntimeException{
        private final GrammarNativeValidationResult evidence;
        /** Descripción: Inicializa una instancia de NativeToolchainFailure con sus dependencias y datos de entrada. Entradas: evidence: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private NativeToolchainFailure(GrammarNativeValidationResult evidence){super("Native toolchain verification failed");this.evidence=evidence;}
    }
    private static final class PipelineFailure extends RuntimeException{private PipelineFailure(String message){super(message);}}
}
