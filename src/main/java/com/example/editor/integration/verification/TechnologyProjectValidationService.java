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
        IsolatedPython python = null;
        try{
            if(!ownedWorkspace) temporaryWorkspace.verify(workspace);
            materialize(compilation, workspace);
            temporaryWorkspace.verify(workspace);
            enforceValidationBudget(workspace);
            List<TechnologyValidationStepResult> steps = new ArrayList<>(validators.require(compilation.technology()).validate(workspace, compilation));
            if(effective.mode() != TechnologyValidationMode.STRUCTURAL){
                for(IntegrationVerificationStep step : compilation.verificationSteps()){
                    Path workingDirectory = resolveWorkingDirectory(workspace, step.workingDirectory());
                    List<String> command = step.command();
                    if(isPython(command)){
                        // pip installs the generated package: never into the interpreter of the host, only into an environment owned by this validation
                        if(python == null){python = createIsolatedPython(effective, steps);}
                        if(python.interpreter() == null){steps.add(notRunWithoutIsolatedPython(step)); continue;}
                        command = new ArrayList<>(command); command.set(0, python.interpreter().toString());
                    }
                    TechnologyValidationStepResult result = processes.run(step.id(), step.description(), workingDirectory,
                            nativeCommand(command), step.expectedExitCode(), effective.timeoutSeconds(), effective.environment());
                    steps.add(result);
                }
            }
            TechnologyValidationStatus status = consolidate(steps, effective.mode());
            Map<String, String> metadata = new LinkedHashMap<>(compilation.metadata());
            metadata.put("validationMode", effective.mode().name());
            metadata.put("workspace", workspace.toString());
            metadata.put("nativeSteps", Integer.toString(compilation.verificationSteps().size()));
            if(python != null && python.interpreter() != null) metadata.put("pythonEnvironment", python.root().toString());
            return new TechnologyProjectValidationReport(compilation.technology(), status, workspace, steps, metadata);
        }finally{
            if(python != null && !effective.keepWorkspace()){temporaryWorkspace.cleanup(python.root());}
            if(ownedWorkspace && !effective.keepWorkspace()){temporaryWorkspace.cleanup(workspace);}
        }
    }

    /** Entorno Python propio de una validación: raíz temporal y su intérprete, o null si no se pudo crear. */
    private record IsolatedPython(Path root, Path interpreter){}

    /** Descripción: Crea con «python -m venv» un entorno Python en un directorio temporal de HYDRA, fuera del proyecto validado. Entradas: request: solicitud efectiva (timeout y entorno); steps: resultados a los que se añade el de la creación. Salida: el entorno, con intérprete null si no se pudo crear. */ private IsolatedPython createIsolatedPython(TechnologyProjectValidationRequest request, List<TechnologyValidationStepResult> steps){
        Path root = temporaryWorkspace.create("python-env-");
        Path environment = root.resolve("venv");
        TechnologyValidationStepResult created = processes.run("python-isolated-environment",
                "Create an isolated Python environment so the generated package is never installed into the host interpreter", root,
                nativeCommand(List.of("python", "-m", "venv", environment.toString())), 0, request.timeoutSeconds(), request.environment());
        steps.add(created);
        Path interpreter = isWindows() ? environment.resolve("Scripts").resolve("python.exe") : environment.resolve("bin").resolve("python");
        boolean ready = created.status() == TechnologyValidationStepStatus.PASSED && Files.isRegularFile(interpreter);
        return new IsolatedPython(root, ready ? interpreter : null);
    }

    /** Descripción: Resultado de un paso Python que no se ejecuta porque no hay entorno aislado. Entradas: step: paso declarado. Salida: resultado SKIPPED con el motivo. */ private TechnologyValidationStepResult notRunWithoutIsolatedPython(IntegrationVerificationStep step){
        return new TechnologyValidationStepResult(step.id(), step.description(), TechnologyValidationStepStatus.SKIPPED, step.command(), null, 0, "", "",
                List.of("Not run: the isolated Python environment could not be created, and HYDRA does not install generated packages into the host interpreter"));
    }

    /** Descripción: Indica si el paso se ejecuta con el intérprete Python. Entradas: command: orden declarada. Salida: verdadero para python o python3 (con o sin .exe). */ private boolean isPython(List<String> command){
        if(command == null || command.isEmpty() || command.get(0) == null) return false;
        String executable = Path.of(command.get(0).trim()).getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if(executable.endsWith(".exe")) executable = executable.substring(0, executable.length() - 4);
        return executable.equals("python") || executable.equals("python3");
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
