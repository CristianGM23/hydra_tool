package com.example.editor.integration.injection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.example.editor.integration.InjectionFileChange;
import com.example.editor.integration.InjectionFileChangeKind;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.InjectionManifest;
import com.example.editor.integration.InjectionPlan;
import com.example.editor.integration.InjectionPlanStatus;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationDiagnosticSeverity;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import tools.jackson.databind.json.JsonMapper;

/** Aplica integraciones con preview, límites, escritura atómica, backup, detección de cambios manuales y rollback. */
@Service
public class SafeTechnologyInjectionService {
    private static final String INTERNAL_DIRECTORY = ".hydra/injections";
    private final TechnologyInjectionProperties properties;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de SafeTechnologyInjectionService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public SafeTechnologyInjectionService(TechnologyInjectionProperties properties, JsonMapper mapper){
        this.properties = properties; this.mapper = mapper;
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: profile: archivo o contenido que debe procesarse; compilation: dato de entrada necesario para la operación; targetDirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionPreview preview(IntegrationProfileModel profile, TechnologyCompilationResult compilation, String targetDirectory){
        Path targetRoot = resolveTargetRoot(targetDirectory, false);
        validateLimits(compilation);
        ManagedInjectionFileIndex index = loadIndex(targetRoot);
        List<InjectionFileChange> changes = new ArrayList<>();
        List<IntegrationDiagnostic> diagnostics = new ArrayList<>(compilation.diagnostics());
        List<String> unchanged = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        long generatedBytes = 0L;
        for(TechnologyGeneratedFile generated : compilation.files()){
            generatedBytes += generated.content().length;
            Path target = safeResolve(targetRoot, generated.relativePath());
            String generatedHash = sha256(generated.content());
            if(!Files.exists(target)){
                changes.add(new InjectionFileChange(generated.relativePath(), InjectionFileChangeKind.ADD,
                        InjectionFileOwnership.GENERATED, "", generatedHash, true));
                continue;
            }
            String actualHash = hashFile(target);
            if(actualHash.equals(generatedHash)){
                unchanged.add(generated.relativePath());
                continue;
            }
            ManagedInjectionFileIndex.Entry managed = index.files().get(generated.relativePath());
            boolean managedAndUnmodified = managed != null && actualHash.equals(managed.sha256());
            if(managedAndUnmodified && profile.injectionPolicy().allowModifyGeneratedFiles()){
                changes.add(new InjectionFileChange(generated.relativePath(), InjectionFileChangeKind.MODIFY,
                        InjectionFileOwnership.GENERATED, actualHash, generatedHash, true));
                continue;
            }
            if(!managedAndUnmodified && profile.injectionPolicy().allowModifyExistingFiles() && !profile.injectionPolicy().preserveCustomCode()){
                changes.add(new InjectionFileChange(generated.relativePath(), InjectionFileChangeKind.MODIFY,
                        InjectionFileOwnership.PATCHED, actualHash, generatedHash, true));
                continue;
            }
            conflicts.add(generated.relativePath());
            diagnostics.add(new IntegrationDiagnostic(IntegrationDiagnosticSeverity.ERROR, "MANUAL_CODE_CONFLICT",
                    profile.id(), generated.relativePath(), "The target file differs from the last HYDRA-managed version and custom code preservation is enabled."));
        }
        InjectionPlanStatus status = diagnostics.stream().anyMatch(value -> value.severity() == IntegrationDiagnosticSeverity.ERROR)
                ? InjectionPlanStatus.BLOCKED : InjectionPlanStatus.READY;
        String planId = safeId("plan-" + profile.id() + "-" + UUID.randomUUID());
        List<String> rollback = List.of("Restore modified files from " + INTERNAL_DIRECTORY + "/backups", "Delete files added by manifest " + planId);
        InjectionPlan plan = new InjectionPlan("1.0", planId, profile.id(), compilation.technology(), status, Instant.now(),
                changes, compilation.dependencies(), compilation.installationSteps(), rollback,
                compilation.verificationSteps(), diagnostics, Map.of("targetDirectory", targetDirectory,
                        "unchangedFiles", Integer.toString(unchanged.size()), "manualConflicts", Integer.toString(conflicts.size())));
        return new TechnologyInjectionPreview(plan, unchanged, conflicts, generatedBytes);
    }

    /** Descripción: Actualiza el estado gestionado por SafeTechnologyInjectionService mediante la operación «apply». Entradas: profile: archivo o contenido que debe procesarse; compilation: dato de entrada necesario para la operación; targetDirectory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult apply(IntegrationProfileModel profile, TechnologyCompilationResult compilation, String targetDirectory){
        TechnologyInjectionPreview preview = preview(profile, compilation, targetDirectory);
        if(preview.plan().status() == InjectionPlanStatus.BLOCKED){throw new TechnologyCompilationException("Injection is blocked by preview diagnostics");}
        Path targetRoot = resolveTargetRoot(targetDirectory, true);
        String manifestId = safeId("manifest-" + profile.id() + "-" + UUID.randomUUID());
        Path backupRoot = targetRoot.resolve(INTERNAL_DIRECTORY).resolve("backups").resolve(manifestId);
        Map<String, TechnologyGeneratedFile> generatedByPath = new LinkedHashMap<>();
        compilation.files().forEach(value -> generatedByPath.put(value.relativePath(), value));
        List<String> written = new ArrayList<>();
        try{
            for(InjectionFileChange change : preview.plan().fileChanges()){
                TechnologyGeneratedFile generated = generatedByPath.get(change.path());
                if(generated == null){throw new TechnologyCompilationException("Generated content is missing for planned file: " + change.path());}
                Path target = safeResolve(targetRoot, change.path());
                if(change.changeKind() == InjectionFileChangeKind.MODIFY){
                    Path backup = safeResolve(backupRoot, change.path());
                    Files.createDirectories(backup.getParent());
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                atomicWrite(target, generated.content());
                written.add(change.path());
            }
            InjectionManifest manifest = new InjectionManifest("1.0", manifestId, profile.id(), compilation.technology(),
                    profile.target().technologyVersion(), compilation.metadata().getOrDefault("generatorVersion", "HYDRA"), Instant.now(),
                    preview.plan().fileChanges(), compilation.dependencies(), compilation.installationSteps(), preview.plan().rollbackSteps(),
                    Map.of("targetDirectory", targetDirectory, "backupDirectory", INTERNAL_DIRECTORY + "/backups/" + manifestId));
            persistManifest(targetRoot, manifest);
            updateIndexAfterApply(targetRoot, manifest);
            return new TechnologyInjectionExecutionResult(manifest, written, List.of(), List.of());
        }catch(IOException exception){
            rollbackPartial(targetRoot, preview.plan().fileChanges(), backupRoot);
            throw new TechnologyCompilationException("Could not apply technology injection; written files were compensated", exception);
        }
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: targetDirectory: dato de entrada necesario para la operación; manifestId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyInjectionExecutionResult rollback(String targetDirectory, String manifestId){
        Path targetRoot = resolveTargetRoot(targetDirectory, false);
        InjectionManifest manifest = loadManifest(targetRoot, manifestId);
        Path backupRoot = targetRoot.resolve(INTERNAL_DIRECTORY).resolve("backups").resolve(manifest.id());
        List<String> deleted = new ArrayList<>();
        List<String> restored = new ArrayList<>();
        try{
            List<InjectionFileChange> reverse = new ArrayList<>(manifest.files());
            java.util.Collections.reverse(reverse);
            for(InjectionFileChange change : reverse){
                Path target = safeResolve(targetRoot, change.path());
                if(change.changeKind() == InjectionFileChangeKind.ADD){
                    Files.deleteIfExists(target); deleted.add(change.path());
                }else if(change.changeKind() == InjectionFileChangeKind.MODIFY){
                    Path backup = safeResolve(backupRoot, change.path());
                    if(!Files.exists(backup)){throw new TechnologyCompilationException("Rollback backup is missing: " + change.path());}
                    atomicWrite(target, com.example.editor.io.BoundedIo.readInternal(backup)); restored.add(change.path());
                }
            }
            updateIndexAfterRollback(targetRoot, manifest);
            return new TechnologyInjectionExecutionResult(manifest, List.of(), deleted, restored);
        }catch(IOException exception){throw new TechnologyCompilationException("Could not rollback technology injection", exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «resolveTargetRoot» dentro de SafeTechnologyInjectionService. Entradas: targetDirectory: dato de entrada necesario para la operación; create: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveTargetRoot(String targetDirectory, boolean create){
        if(!properties.isEnabled()){throw new TechnologyCompilationException("Technology injection is disabled");}
        TechnologyInjectionRequest request = new TechnologyInjectionRequest(targetDirectory);
        Path allowed = properties.getAllowedRoot().toAbsolutePath().normalize();
        Path target = allowed.resolve(request.targetDirectory()).normalize();
        if(!target.startsWith(allowed)){throw new TechnologyCompilationException("Target directory escapes the configured injection root");}
        if(!create){return target;}
        try{Files.createDirectories(target); return target;}catch(IOException exception){throw new TechnologyCompilationException("Could not create target directory", exception);}
    }

    /** Descripción: Valida las condiciones necesarias para «validateLimits» y rechaza entradas no válidas. Entradas: compilation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateLimits(TechnologyCompilationResult compilation){
        if(compilation.files().size() > properties.getMaximumFiles()){throw new TechnologyCompilationException("Generated file count exceeds the configured limit");}
        long total = 0L;
        for(TechnologyGeneratedFile file : compilation.files()){
            if(file.content().length > properties.getMaximumFileBytes()){throw new TechnologyCompilationException("Generated file exceeds the configured limit: " + file.relativePath());}
            total += file.content().length;
            if(total > properties.getMaximumTotalBytes()){throw new TechnologyCompilationException("Generated output exceeds the configured total-size limit");}
        }
    }

    /** Descripción: Obtiene la información correspondiente a «loadIndex» dentro de SafeTechnologyInjectionService. Entradas: targetRoot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ManagedInjectionFileIndex loadIndex(Path targetRoot){
        Path path = targetRoot.resolve(INTERNAL_DIRECTORY).resolve("managed-files.json");
        if(!Files.exists(path)){return new ManagedInjectionFileIndex("1.0", Map.of());}
        try{return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), ManagedInjectionFileIndex.class);}
        catch(IOException | RuntimeException exception){throw new TechnologyCompilationException("Could not read managed-file index", exception);}
    }

    /** Descripción: Actualiza el estado gestionado por SafeTechnologyInjectionService mediante la operación «updateIndexAfterApply». Entradas: targetRoot: dato de entrada necesario para la operación; manifest: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void updateIndexAfterApply(Path targetRoot, InjectionManifest manifest) throws IOException {
        ManagedInjectionFileIndex current = loadIndex(targetRoot);
        Map<String, ManagedInjectionFileIndex.Entry> files = new LinkedHashMap<>(current.files());
        for(InjectionFileChange change : manifest.files()){
            files.put(change.path(), new ManagedInjectionFileIndex.Entry(change.generatedSha256(), manifest.id()));
        }
        atomicWrite(targetRoot.resolve(INTERNAL_DIRECTORY).resolve("managed-files.json"), mapper.writeValueAsBytes(new ManagedInjectionFileIndex("1.0", files)));
    }

    /** Descripción: Actualiza el estado gestionado por SafeTechnologyInjectionService mediante la operación «updateIndexAfterRollback». Entradas: targetRoot: dato de entrada necesario para la operación; manifest: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void updateIndexAfterRollback(Path targetRoot, InjectionManifest manifest) throws IOException {
        ManagedInjectionFileIndex current = loadIndex(targetRoot);
        Map<String, ManagedInjectionFileIndex.Entry> files = new LinkedHashMap<>(current.files());
        for(InjectionFileChange change : manifest.files()){
            if(change.changeKind() == InjectionFileChangeKind.ADD){files.remove(change.path());}
            else if(change.changeKind() == InjectionFileChangeKind.MODIFY){
                if(change.originalSha256().isBlank()){files.remove(change.path());}
                else{files.put(change.path(), new ManagedInjectionFileIndex.Entry(change.originalSha256(), "rollback:" + manifest.id()));}
            }
        }
        atomicWrite(targetRoot.resolve(INTERNAL_DIRECTORY).resolve("managed-files.json"), mapper.writeValueAsBytes(new ManagedInjectionFileIndex("1.0", files)));
    }

    /** Descripción: Ejecuta la operación «persistManifest» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: targetRoot: dato de entrada necesario para la operación; manifest: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persistManifest(Path targetRoot, InjectionManifest manifest) throws IOException {
        atomicWrite(targetRoot.resolve(INTERNAL_DIRECTORY).resolve("manifests").resolve(manifest.id() + ".json"), mapper.writeValueAsBytes(manifest));
    }

    /** Descripción: Obtiene la información correspondiente a «loadManifest» dentro de SafeTechnologyInjectionService. Entradas: targetRoot: dato de entrada necesario para la operación; manifestId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private InjectionManifest loadManifest(Path targetRoot, String manifestId){
        String safe = safeId(manifestId);
        Path path = targetRoot.resolve(INTERNAL_DIRECTORY).resolve("manifests").resolve(safe + ".json");
        if(!Files.exists(path)){throw new TechnologyCompilationException("Injection manifest was not found: " + safe);}
        try{return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), InjectionManifest.class);}
        catch(IOException | RuntimeException exception){throw new TechnologyCompilationException("Could not read injection manifest", exception);}
    }

    /** Descripción: Ejecuta la operación «rollbackPartial» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: targetRoot: dato de entrada necesario para la operación; changes: dato de entrada necesario para la operación; backupRoot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rollbackPartial(Path targetRoot, List<InjectionFileChange> changes, Path backupRoot){
        List<InjectionFileChange> reverse = new ArrayList<>(changes); java.util.Collections.reverse(reverse);
        for(InjectionFileChange change : reverse){
            try{
                Path target = safeResolve(targetRoot, change.path());
                if(change.changeKind() == InjectionFileChangeKind.ADD){Files.deleteIfExists(target);}
                else if(change.changeKind() == InjectionFileChangeKind.MODIFY){
                    Path backup = safeResolve(backupRoot, change.path());
                    if(Files.exists(backup)){atomicWrite(target, com.example.editor.io.BoundedIo.readInternal(backup));}
                }
            }catch(RuntimeException | IOException ignored){/* La causa original se conserva. */}
        }
    }

    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: root: ruta o ubicación utilizada por la operación; relative: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeResolve(Path root, String relative){
        String normalized = relative.replace('\\', '/');
        if(normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")){throw new TechnologyCompilationException("Unsafe injection path: " + relative);}
        Path result = root.resolve(normalized).normalize();
        if(!result.startsWith(root.normalize())){throw new TechnologyCompilationException("Injection path escapes the target root: " + relative);}
        return result;
    }

    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".hydra.tmp");
        Files.write(temporary, content);
        try{Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);}
        catch(java.nio.file.AtomicMoveNotSupportedException exception){Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);}
    }

    /** Descripción: Evalúa la condición «hashFile» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String hashFile(Path path){try{return sha256(com.example.editor.io.BoundedIo.readInternal(path));}catch(IOException exception){throw new TechnologyCompilationException("Could not hash target file: " + path, exception);}}
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
    /** Descripción: Ejecuta la operación «safeId» correspondiente a la responsabilidad de SafeTechnologyInjectionService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeId(String value){
        if(value == null || value.isBlank()){throw new TechnologyCompilationException("Manifest or plan id is required");}
        String normalized = value.replaceAll("[^A-Za-z0-9._:-]", "-");
        if(normalized.contains("..")){throw new TechnologyCompilationException("Unsafe identifier: " + value);}
        return normalized;
    }
}
