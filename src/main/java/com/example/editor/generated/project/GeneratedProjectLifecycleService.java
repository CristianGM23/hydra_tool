package com.example.editor.generated.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.editor.integration.compatibility.ToolchainCommandRunner;
import com.example.editor.integration.verification.TechnologyValidationStepResult;

/** Unifies registration, generation, native validation, opening and bounded launching. */
@Service
public class GeneratedProjectLifecycleService {
    private final GeneratedProjectRuntimeProfileCatalog profiles;
    private final GeneratedProjectRegistryStore store;
    private final ToolchainCommandRunner commandRunner;
    private final Path projectRoot;
    private final Path registryRoot;

    /** Descripción: Inicializa una instancia de GeneratedProjectLifecycleService con sus dependencias y datos de entrada. Entradas: profiles: archivo o contenido que debe procesarse; store: dato de entrada necesario para la operación; commandRunner: dato de entrada necesario para la operación; projectRoot: dato de entrada necesario para la operación; registryRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GeneratedProjectLifecycleService(GeneratedProjectRuntimeProfileCatalog profiles,
            GeneratedProjectRegistryStore store, ToolchainCommandRunner commandRunner,
            @Value("${hydra.generated-projects.root:data/generated-projects}") String projectRoot,
            @Value("${hydra.generated-projects.registry-root:data/generated-projects-registry}") String registryRoot){
        this.profiles = profiles; this.store = store; this.commandRunner = commandRunner;
        this.projectRoot = Path.of(projectRoot).toAbsolutePath().normalize();
        this.registryRoot = Path.of(registryRoot).toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «profiles» correspondiente a la responsabilidad de GeneratedProjectLifecycleService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<GeneratedProjectRuntimeProfile> profiles(){return profiles.profiles();}
    /** Descripción: Ejecuta la operación «registry» correspondiente a la responsabilidad de GeneratedProjectLifecycleService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedProjectRegistry registry(){return store.load(registryRoot);}

    /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de GeneratedProjectLifecycleService. Entradas: definition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedProjectRegistry register(GeneratedProjectDefinition definition){
        profiles.require(definition.profileKey());
        resolve(definition.relativeDirectory());
        Map<String, GeneratedProjectDefinition> byId = new LinkedHashMap<>();
        registry().projects().forEach(item -> byId.put(item.id(), item));
        byId.put(definition.id(), definition);
        return store.save(registryRoot, new GeneratedProjectRegistry("1.0", Instant.now(), List.copyOf(byId.values())));
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de GeneratedProjectLifecycleService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GeneratedProjectExecutionReport execute(GeneratedProjectExecutionRequest request){
        GeneratedProjectDefinition project = registry().projects().stream()
                .filter(item -> item.id().equals(request.projectId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown generated project: " + request.projectId()));
        GeneratedProjectRuntimeProfile profile = profiles.require(project.profileKey());
        Path directory = resolve(project.relativeDirectory());
        List<GeneratedProjectStage> requested = request.stages().isEmpty()
                ? profile.stages().stream().map(GeneratedProjectStageDefinition::stage).toList() : request.stages();
        Instant started = Instant.now();
        List<GeneratedProjectStepResult> results = new ArrayList<>();
        boolean failed = false;
        for(GeneratedProjectStageDefinition stage : profile.stages()){
            if(!requested.contains(stage.stage())){continue;}
            if(failed && !request.continueAfterFailure()){
                results.add(new GeneratedProjectStepResult(stage.stage(), GeneratedProjectStepStatus.SKIPPED,
                        stage.description(), List.of(), null, 0, "Skipped after a previous failure.", List.of()));
                continue;
            }
            if(stage.optional() && !request.allowOptionalStages()){
                results.add(new GeneratedProjectStepResult(stage.stage(), GeneratedProjectStepStatus.SKIPPED,
                        stage.description(), List.of(), null, 0, "Optional stage was not enabled.", List.of()));
                continue;
            }
            GeneratedProjectStepResult result = runStage(directory, stage, request.dryRun());
            results.add(result);
            failed = failed || result.status() == GeneratedProjectStepStatus.FAILED;
        }
        Instant completed = Instant.now();
        String runId = digest(project.id() + "|" + started + "|" + results);
        return new GeneratedProjectExecutionReport(runId, project, directory.toString(), started, completed,
                results, !failed);
    }

    /** Descripción: Procesa la operación «runStage» coordinando las responsabilidades de GeneratedProjectLifecycleService. Entradas: directory: ruta o ubicación utilizada por la operación; stage: dato de entrada necesario para la operación; dryRun: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GeneratedProjectStepResult runStage(Path directory, GeneratedProjectStageDefinition stage, boolean dryRun){
        if(stage.stage() == GeneratedProjectStage.OPEN){
            return new GeneratedProjectStepResult(stage.stage(),
                    Files.isDirectory(directory) ? GeneratedProjectStepStatus.PASSED : GeneratedProjectStepStatus.FAILED,
                    stage.description(), List.of(), null, 0, directory.toString(),
                    Files.isDirectory(directory) ? List.of() : List.of("Generated project directory does not exist."));
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> command = stage.command(windows);
        if(command.isEmpty()){
            return new GeneratedProjectStepResult(stage.stage(), GeneratedProjectStepStatus.SKIPPED,
                    stage.description(), List.of(), null, 0, "No command is configured for this operating system.", List.of());
        }
        if(dryRun){
            return new GeneratedProjectStepResult(stage.stage(), GeneratedProjectStepStatus.PLANNED,
                    stage.description(), command, null, 0, "Dry run: command was not executed.", List.of());
        }
        if(!Files.isDirectory(directory)){
            return new GeneratedProjectStepResult(stage.stage(), GeneratedProjectStepStatus.FAILED,
                    stage.description(), command, null, 0, "Generated project directory does not exist.", List.of());
        }
        TechnologyValidationStepResult nativeResult = commandRunner.run(
                stage.stage().name().toLowerCase(), stage.description(), directory,
                command, 0, stage.timeoutSeconds(), stage.environment());
        return new GeneratedProjectStepResult(stage.stage(),
                nativeResult.successful() ? GeneratedProjectStepStatus.PASSED : GeneratedProjectStepStatus.FAILED,
                stage.description(), nativeResult.command(), nativeResult.exitCode(), nativeResult.durationMillis(),
                nativeResult.successful() ? "Stage completed successfully." : "Stage failed.",
                nativeResult.diagnostics());
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de GeneratedProjectLifecycleService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolve(String value){
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.equals(".") || normalized.equals("..") || normalized.contains("../")){
            throw new IllegalArgumentException("relativeDirectory must remain below the generated-project root");
        }
        Path target = projectRoot.resolve(normalized).normalize();
        if(!target.startsWith(projectRoot)){throw new IllegalArgumentException("relativeDirectory escapes the generated-project root");}
        Path current = projectRoot;
        for(Path segment : projectRoot.relativize(target)){
            current = current.resolve(segment);
            if(Files.isSymbolicLink(current)){throw new IllegalArgumentException("relativeDirectory crosses a symbolic link");}
        }
        return target;
    }

    /** Descripción: Ejecuta la operación «digest» correspondiente a la responsabilidad de GeneratedProjectLifecycleService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 24);}
        catch(Exception exception){throw new IllegalStateException("SHA-256 is not available", exception);}
    }
}
