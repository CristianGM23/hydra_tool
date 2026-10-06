package com.example.editor.integration.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.io.AtomicFiles;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;

/** Materializa, importa estructuralmente y, cuando se solicita, compila un proyecto tecnológico con su toolchain nativo. */
@Service
public class TechnologyProjectValidationService {
    private final TechnologyProjectStructureValidatorRegistry validators;
    private final ExternalToolProcessRunner processes;
    private final TechnologyProjectValidationProperties properties;
    private final HydraTemporaryWorkspace temporaryWorkspace;

    @Autowired
    /** Descripción: Inicializa una instancia de TechnologyProjectValidationService con sus dependencias y datos de entrada. Entradas: validators: dato de entrada necesario para la operación; processes: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; temporaryWorkspace: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyProjectValidationService(TechnologyProjectStructureValidatorRegistry validators,
            ExternalToolProcessRunner processes, TechnologyProjectValidationProperties properties, HydraTemporaryWorkspace temporaryWorkspace){
        this.validators = validators; this.processes = processes; this.properties = properties; this.temporaryWorkspace=temporaryWorkspace;
    }

    /** Compatibility constructor for focused unit tests. */
    /** Descripción: Inicializa una instancia de TechnologyProjectValidationService con sus dependencias y datos de entrada. Entradas: validators: dato de entrada necesario para la operación; processes: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyProjectValidationService(TechnologyProjectStructureValidatorRegistry validators,
            ExternalToolProcessRunner processes, TechnologyProjectValidationProperties properties){
        this(validators,processes,properties,new HydraTemporaryWorkspace(
                new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties())));
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: compilation: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyProjectValidationReport validate(TechnologyCompilationResult compilation,
            TechnologyProjectValidationRequest request){
        if(!properties.isEnabled()){throw new TechnologyCompilationException("Technology project validation is disabled");}
        TechnologyProjectValidationRequest effective = request == null ? TechnologyProjectValidationRequest.structural() : request;
        Path workspace = effective.workspaceDirectory() == null ? createWorkspace(compilation) : effective.workspaceDirectory().toAbsolutePath().normalize();
        boolean ownedWorkspace = effective.workspaceDirectory() == null;
        try{
            if(!ownedWorkspace) temporaryWorkspace.verify(workspace);
            materialize(compilation, workspace);
            temporaryWorkspace.verify(workspace);
            enforceValidationBudget(workspace);
            List<TechnologyValidationStepResult> steps = new ArrayList<>(validators.require(compilation.technology()).validate(workspace, compilation));
            if(effective.mode() != TechnologyValidationMode.STRUCTURAL){
                for(IntegrationVerificationStep step : compilation.verificationSteps()){
                    Path workingDirectory = resolveWorkingDirectory(workspace, step.workingDirectory());
                    TechnologyValidationStepResult result = processes.run(step.id(), step.description(), workingDirectory,
                            nativeCommand(step.command()), step.expectedExitCode(), effective.timeoutSeconds(), effective.environment());
                    steps.add(result);
                }
            }
            TechnologyValidationStatus status = consolidate(steps, effective.mode());
            Map<String, String> metadata = new LinkedHashMap<>(compilation.metadata());
            metadata.put("validationMode", effective.mode().name());
            metadata.put("workspace", workspace.toString());
            metadata.put("nativeSteps", Integer.toString(compilation.verificationSteps().size()));
            return new TechnologyProjectValidationReport(compilation.technology(), status, workspace, steps, metadata);
        }finally{
            if(ownedWorkspace && !effective.keepWorkspace()){temporaryWorkspace.cleanup(workspace);}
        }
    }

    /** Descripción: Crea o prepara el resultado de «createWorkspace» dentro de TechnologyProjectValidationService. Entradas: compilation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path createWorkspace(TechnologyCompilationResult compilation){
        return temporaryWorkspace.create("technology-"+compilation.technology().name().toLowerCase()+"-");
    }

    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de TechnologyProjectValidationService. Entradas: compilation: dato de entrada necesario para la operación; workspace: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void materialize(TechnologyCompilationResult compilation, Path workspace){
        if(compilation.files().size() > properties.getMaximumFiles()){throw new TechnologyCompilationException("Generated project exceeds validation file limit");}
        long total = compilation.files().stream().mapToLong(file -> file.content().length).sum();
        if(total > properties.getMaximumTotalBytes()){throw new TechnologyCompilationException("Generated project exceeds validation byte limit");}
        try{
            Files.createDirectories(workspace);
            for(TechnologyGeneratedFile file : compilation.files()){
                Path target = workspace.resolve(file.relativePath()).normalize();
                if(!target.startsWith(workspace)){throw new TechnologyCompilationException("Unsafe generated path: " + file.relativePath());}
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&Files.isSymbolicLink(target)){throw new TechnologyCompilationException("Generated target is a symbolic link: " + file.relativePath());}
                AtomicFiles.writeReplacing(target,file.content());
            }
        }catch(IOException exception){throw new TechnologyCompilationException("Could not materialize generated project", exception);}
    }

    /** Descripción: Verifica el workspace completo antes de ejecutar validadores o procesos nativos, de modo que un workspace proporcionado externamente no pueda eludir los límites de número de ficheros y bytes aplicados a proyectos generados. Entradas: workspace: raíz materializada que se va a validar. Salida: no devuelve valor; rechaza de forma temprana workspaces que exceden los presupuestos configurados. */ private void enforceValidationBudget(Path workspace){
        int maximumFiles=properties.getMaximumFiles();long maximumBytes=properties.getMaximumTotalBytes();int files=0;long bytes=0;
        try(var stream=Files.walk(workspace)){
            var iterator=stream.iterator();
            while(iterator.hasNext()){
                Path path=iterator.next();
                if(Files.isSymbolicLink(path))throw new TechnologyCompilationException("Generated project validation does not allow symbolic links: "+workspace.relativize(path));
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                files++;if(files>maximumFiles)throw new TechnologyCompilationException("Generated project exceeds validation file limit");
                bytes=Math.addExact(bytes,Files.size(path));if(bytes>maximumBytes)throw new TechnologyCompilationException("Generated project exceeds validation byte limit");
            }
        }catch(TechnologyCompilationException exception){throw exception;}
        catch(IOException|ArithmeticException exception){throw new TechnologyCompilationException("Could not measure generated project validation workspace",exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «resolveWorkingDirectory» dentro de TechnologyProjectValidationService. Entradas: workspace: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveWorkingDirectory(Path workspace, String value){
        Path result = value == null || value.isBlank() || value.equals(".") ? workspace : workspace.resolve(value).normalize();
        if(!result.startsWith(workspace)){throw new TechnologyCompilationException("Unsafe verification working directory: " + value);}
        return result;
    }

    /** Descripción: Ejecuta la operación «nativeCommand» correspondiente a la responsabilidad de TechnologyProjectValidationService. Entradas: command: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> nativeCommand(List<String> command){
        if(command == null || command.isEmpty()){return List.of();}
        if(isWindows() && command.get(0).equals("mvn")){List<String> result = new ArrayList<>(command); result.set(0, "mvn.cmd"); return result;}
        if(isWindows() && command.get(0).equals("npm")){List<String> result = new ArrayList<>(command); result.set(0, "npm.cmd"); return result;}
        if(isWindows() && command.get(0).equals("python")){List<String> result = new ArrayList<>(command); result.set(0, "python.exe"); return result;}
        return command;
    }

    /** Descripción: Ejecuta la operación «consolidate» correspondiente a la responsabilidad de TechnologyProjectValidationService. Entradas: steps: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyValidationStatus consolidate(List<TechnologyValidationStepResult> steps, TechnologyValidationMode mode){
        if(steps.stream().anyMatch(value -> value.status() == TechnologyValidationStepStatus.FAILED || value.status() == TechnologyValidationStepStatus.TIMED_OUT || value.status() == TechnologyValidationStepStatus.CANCELLED)){
            return TechnologyValidationStatus.FAILED;
        }
        boolean unavailable = steps.stream().anyMatch(value -> value.status() == TechnologyValidationStepStatus.TOOL_UNAVAILABLE || value.status() == TechnologyValidationStepStatus.SKIPPED);
        if(unavailable){return mode == TechnologyValidationMode.NATIVE_REQUIRED ? TechnologyValidationStatus.FAILED : TechnologyValidationStatus.PARTIAL;}
        return TechnologyValidationStatus.PASSED;
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteRecursively» de forma incremental y de mejor esfuerzo. Entradas: root: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; registra fallos sin materializar el árbol completo. */ private void deleteRecursively(Path root){
        if(root == null || !Files.exists(root)){return;}
        try{Files.walkFileTree(root,new SimpleFileVisitor<>(){
            /** Descripción: Elimina un fichero temporal de validación. Entradas: file: fichero; attrs: metadatos. Salida: continúa el recorrido. */ @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){try{Files.deleteIfExists(file);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/integration/verification/TechnologyProjectValidationService.java",ignored);}return FileVisitResult.CONTINUE;}
            /** Descripción: Elimina un directorio temporal una vez procesados sus hijos. Entradas: dir: directorio; exc: posible fallo. Salida: continúa el recorrido. */ @Override public FileVisitResult postVisitDirectory(Path dir,IOException exc){if(exc!=null)com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/integration/verification/TechnologyProjectValidationService.java",exc);try{Files.deleteIfExists(dir);}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/integration/verification/TechnologyProjectValidationService.java",ignored);}return FileVisitResult.CONTINUE;}
        });}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/integration/verification/TechnologyProjectValidationService.java",ignored);}
    }

    /** Descripción: Evalúa la condición «isWindows» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isWindows(){return System.getProperty("os.name", "").toLowerCase().contains("win");}
}
