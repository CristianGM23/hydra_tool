package com.example.editor.integration.roundtrip.resolution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.io.AtomicFiles;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import com.example.editor.integration.roundtrip.ManualRegionPreserver;
import com.example.editor.integration.roundtrip.RoundTripChangeKind;
import com.example.editor.integration.roundtrip.RoundTripFileChange;
import com.example.editor.integration.roundtrip.RoundTripSyncPreview;
import com.example.editor.integration.roundtrip.RoundTripProperties;
import com.example.editor.integration.roundtrip.RoundTripSyncService;
import tools.jackson.databind.json.JsonMapper;

/** Preview, explicit conflict resolution, safe application, backup and rollback workflow. */
@Service
public class RoundTripResolutionWorkflowService {
    private static final String BACKUP_ROOT = ".hydra-roundtrip-backups";
    private static final String JOURNAL = "journal.json";
    private final RoundTripSyncService syncService;
    private final AdvancedRoundTripConflictResolver conflictResolver;
    private final ManualRegionPreserver manualRegions;
    private final JsonMapper mapper;
    private final RoundTripProperties properties;

    @Autowired
    /** Descripción: Inicializa RoundTripResolutionWorkflowService con un presupuesto configurable por fichero. Entradas: syncService: servicio de preview; conflictResolver: resolvedor; manualRegions: preservador; mapper: componente JSON; properties: límites round-trip. Salida: deja el flujo preparado para inspección y escritura acotadas. */ public RoundTripResolutionWorkflowService(RoundTripSyncService syncService,AdvancedRoundTripConflictResolver conflictResolver,ManualRegionPreserver manualRegions,JsonMapper mapper,RoundTripProperties properties){this.syncService=Objects.requireNonNull(syncService,"syncService");this.conflictResolver=Objects.requireNonNull(conflictResolver,"conflictResolver");this.manualRegions=Objects.requireNonNull(manualRegions,"manualRegions");this.mapper=Objects.requireNonNull(mapper,"mapper");this.properties=Objects.requireNonNull(properties,"properties");}
    /** Descripción: Constructor compatible para pruebas focales con los límites round-trip por defecto. Entradas: syncService, conflictResolver, manualRegions y mapper: colaboradores del flujo. Salida: crea el servicio con configuración segura por defecto. */ public RoundTripResolutionWorkflowService(RoundTripSyncService syncService,AdvancedRoundTripConflictResolver conflictResolver,ManualRegionPreserver manualRegions,JsonMapper mapper){this(syncService,conflictResolver,manualRegions,mapper,new RoundTripProperties());}

    /** Descripción: Ejecuta la operación «prepare» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: generated: dato de entrada necesario para la operación; profileId: identificador que delimita el recurso objetivo; targetRoot: dato de entrada necesario para la operación; decisions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripResolutionPlan prepare(TechnologyCompilationResult generated, String profileId,
            Path targetRoot, List<ElementConflictDecision> decisions){
        Objects.requireNonNull(generated, "generated");
        Path root = Objects.requireNonNull(targetRoot, "targetRoot").toAbsolutePath().normalize();
        RoundTripSyncPreview preview = syncService.preview(generated, profileId, root);
        Map<String, TechnologyGeneratedFile> generatedByPath = new LinkedHashMap<>();
        generated.files().forEach(file -> generatedByPath.put(file.relativePath(), file));
        List<ElementConflict> conflicts = preview.changes().stream().filter(this::requiresDecision)
                .map(change -> conflict(change, generatedByPath.get(change.path()), root)).toList();
        RoundTripResolutionResult resolution = conflictResolver.resolve(conflicts, decisions == null ? List.of() : decisions);
        Map<String, ResolvedElementConflict> resolvedByPath = new LinkedHashMap<>();
        resolution.resolved().forEach(value -> resolvedByPath.put(value.conflict().artifactPath(), value));
        List<RoundTripResolutionAction> actions = new ArrayList<>();
        for(RoundTripFileChange change : preview.changes().stream().sorted(Comparator.comparing(RoundTripFileChange::path)).toList()){
            TechnologyGeneratedFile file = generatedByPath.get(change.path());
            if(change.selectable() && file != null){
                actions.add(action(file, RoundTripResolutionActionKind.WRITE_HYDRA, ""));
                continue;
            }
            ResolvedElementConflict resolved = resolvedByPath.get(change.path());
            if(resolved == null || file == null){continue;}
            ConflictResolutionChoice choice = resolved.decision().choice();
            if(choice == ConflictResolutionChoice.KEEP_HYDRA){
                actions.add(action(file, RoundTripResolutionActionKind.WRITE_HYDRA, resolved.conflict().key()));
            }else if(choice == ConflictResolutionChoice.MERGE_SAFE){
                actions.add(action(file, RoundTripResolutionActionKind.MERGE_SAFE, resolved.conflict().key()));
            }
        }
        actions = actions.stream().sorted(Comparator.comparing(RoundTripResolutionAction::path)).toList();
        String operationId = operationId(generated, profileId, root, actions);
        return new RoundTripResolutionPlan(operationId, generated.technology(), profileId, root, preview, actions,
                resolution.unresolved(), resolution.canApply(), Map.of("conflictCount", Integer.toString(conflicts.size()),
                        "actionCount", Integer.toString(actions.size())));
    }

    /** Descripción: Actualiza el estado gestionado por RoundTripResolutionWorkflowService mediante la operación «apply». Entradas: plan: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripApplyResult apply(RoundTripResolutionPlan plan){
        Objects.requireNonNull(plan, "plan");
        if(!plan.canApply()){throw new TechnologyCompilationException("Round-trip plan still contains unresolved conflicts");}
        Path backupRoot = safeResolve(plan.targetRoot(), BACKUP_ROOT + "/" + plan.operationId());
        Path filesRoot = backupRoot.resolve("files"); Path journalPath = backupRoot.resolve(JOURNAL);
        List<RoundTripBackupEntry> entries = new ArrayList<>(); List<String> affected = new ArrayList<>();
        boolean writesStarted = false;
        try{
            Files.createDirectories(filesRoot);
            for(RoundTripResolutionAction action : plan.actions()){
                Path target = safeResolve(plan.targetRoot(), action.path()); boolean existed = Files.exists(target);
                entries.add(new RoundTripBackupEntry(action.path(), existed));
                if(existed){
                    Path backup=safeResolve(filesRoot,action.path());
                    AtomicFiles.copyReplacingBounded(target,backup,properties.getMaximumFileBytes());
                }
            }
            Files.createDirectories(journalPath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(journalPath.toFile(),
                    new RoundTripBackupJournal(plan.operationId(), entries.stream().sorted(Comparator.comparing(RoundTripBackupEntry::path)).toList()));
            writesStarted = true;
            for(RoundTripResolutionAction action : plan.actions()){
                Path target = safeResolve(plan.targetRoot(), action.path()); boolean existed = Files.exists(target);
                byte[] content = action.generatedContent();
                if(action.kind() == RoundTripResolutionActionKind.MERGE_SAFE && existed && isText(action.mediaType())){
                    content = manualRegions.merge(com.example.editor.io.BoundedIo.read(target,properties.getMaximumFileBytes()), content);
                }
                atomicWrite(target, content); affected.add(action.path());
            }
            return new RoundTripApplyResult(plan.operationId(), true, false, affected, journalPath,
                    Map.of("backupRoot", backupRoot.toString()));
        }catch(IOException | RuntimeException exception){
            if(writesStarted){
                try{restoreEntries(plan.targetRoot(), filesRoot, entries);}catch(IOException | RuntimeException rollbackFailure){exception.addSuppressed(rollbackFailure);}
            }
            throw new TechnologyCompilationException("Could not apply round-trip resolution plan", exception);
        }
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: targetRoot: dato de entrada necesario para la operación; operationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripApplyResult rollback(Path targetRoot, String operationId){
        Path root = Objects.requireNonNull(targetRoot, "targetRoot").toAbsolutePath().normalize();
        Path backupRoot = safeResolve(root, BACKUP_ROOT + "/" + requireText(operationId, "operationId"));
        Path journalPath = backupRoot.resolve(JOURNAL); Path filesRoot = backupRoot.resolve("files");
        if(!Files.exists(journalPath)){throw new TechnologyCompilationException("Round-trip rollback journal does not exist: " + operationId);}
        try{
            RoundTripBackupJournal journal=mapper.readValue(com.example.editor.io.BoundedIo.read(journalPath,properties.getMaximumFileBytes()),RoundTripBackupJournal.class);
            List<String> affected = restoreEntries(root, filesRoot, journal.entries());
            return new RoundTripApplyResult(operationId, true, true, affected, journalPath, Map.of("backupRoot", backupRoot.toString()));
        }catch(IOException | RuntimeException exception){
            if(exception instanceof TechnologyCompilationException compilationException){throw compilationException;}
            throw new TechnologyCompilationException("Could not rollback round-trip operation", exception);
        }
    }

    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: change: dato de entrada necesario para la operación; file: archivo o contenido que debe procesarse; root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ElementConflict conflict(RoundTripFileChange change, TechnologyGeneratedFile file, Path root){
        boolean manual = false;
        Path nativePath = safeResolve(root, change.path());
        if(Files.exists(nativePath)){
            try{manual=!manualRegions.bodies(com.example.editor.io.BoundedIo.readUtf8(nativePath,properties.getMaximumFileBytes())).isEmpty();}catch(IOException exception){throw new TechnologyCompilationException("Could not inspect native round-trip file within the configured limit: "+change.path(),exception);}
        }
        Set<ConflictResolutionChoice> choices = new LinkedHashSet<>();
        choices.add(ConflictResolutionChoice.KEEP_NATIVE); choices.add(ConflictResolutionChoice.SKIP);
        if(manual){choices.add(ConflictResolutionChoice.KEEP_MANUAL);}
        if(file != null){choices.add(ConflictResolutionChoice.KEEP_HYDRA); choices.add(ConflictResolutionChoice.MERGE_SAFE);}
        String elementId = file != null && !file.sourceDocumentIds().isEmpty() ? file.sourceDocumentIds().get(0) : "file:" + change.path();
        return new ElementConflict(elementId, change.path(), value(change.generatedSha256()), value(change.currentSha256()), manual, choices);
    }
    /** Descripción: Valida las condiciones necesarias para «requiresDecision» y rechaza entradas no válidas. Entradas: change: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean requiresDecision(RoundTripFileChange change){
        return change.kind() == RoundTripChangeKind.MANUAL_CONFLICT || change.kind() == RoundTripChangeKind.ORPHAN_NATIVE;
    }
    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: file: archivo o contenido que debe procesarse; kind: dato de entrada necesario para la operación; conflictKey: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RoundTripResolutionAction action(TechnologyGeneratedFile file, RoundTripResolutionActionKind kind, String conflictKey){
        return new RoundTripResolutionAction(file.relativePath(), kind, file.mediaType(), file.content(), conflictKey);
    }
    /** Descripción: Ejecuta la operación «operationId» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: generated: dato de entrada necesario para la operación; profileId: identificador que delimita el recurso objetivo; root: ruta o ubicación utilizada por la operación; actions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String operationId(TechnologyCompilationResult generated, String profileId, Path root, List<RoundTripResolutionAction> actions){
        StringBuilder value = new StringBuilder(generated.technology().name()).append('|').append(profileId).append('|').append(root);
        actions.forEach(action -> value.append('|').append(action.path()).append(':').append(hash(action.generatedContent())));
        return "sync-" + hash(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 16);
    }
    /** Descripción: Ejecuta la operación «restoreEntries» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: root: ruta o ubicación utilizada por la operación; filesRoot: archivo o contenido que debe procesarse; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> restoreEntries(Path root, Path filesRoot, List<RoundTripBackupEntry> entries) throws IOException{
        List<String> affected = new ArrayList<>();
        for(RoundTripBackupEntry entry : entries.stream().sorted(Comparator.comparing(RoundTripBackupEntry::path).reversed()).toList()){
            Path target = safeResolve(root, entry.path());
            if(entry.existedBefore()){
                Path backup = safeResolve(filesRoot, entry.path());
                if(!Files.exists(backup)){throw new TechnologyCompilationException("Missing backup for " + entry.path());}
                atomicWrite(target, com.example.editor.io.BoundedIo.read(backup,properties.getMaximumFileBytes()));
            }else{Files.deleteIfExists(target);}
            affected.add(entry.path());
        }
        return affected;
    }
    /** Descripción: Publica contenido round-trip mediante la utilidad atómica compartida y rechaza salidas que superen el presupuesto por fichero. Entradas: target: destino; content: bytes resueltos. Salida: no devuelve valor; evita escrituras parciales y ficheros fuera de presupuesto. */ private void atomicWrite(Path target,byte[] content) throws IOException{if(content.length>properties.getMaximumFileBytes())throw new TechnologyCompilationException("Round-trip output exceeds the configured file limit of "+properties.getMaximumFileBytes()+" bytes: "+target);AtomicFiles.writeReplacing(target,content);}
    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: root: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeResolve(Path root, String value){
        String normalized = requireText(value, "path").replace('\\', '/');
        if(normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")){
            throw new TechnologyCompilationException("Unsafe round-trip path: " + value);
        }
        Path result = root.resolve(normalized).normalize();
        if(!result.startsWith(root)){throw new TechnologyCompilationException("Round-trip path escapes target root: " + value);}
        return result;
    }
    /** Descripción: Evalúa la condición «isText» sin modificar el estado externo. Entradas: mediaType: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isText(String mediaType){return mediaType != null && (mediaType.startsWith("text/") || mediaType.contains("json") || mediaType.contains("xml") || mediaType.contains("javascript"));}
    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de RoundTripResolutionWorkflowService. Entradas: hash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String value(String hash){return hash == null || hash.isBlank() ? "absent" : hash;}
    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String hash(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String requireText(String value, String field){if(value == null || value.isBlank()){throw new IllegalArgumentException(field + " is required");}return value.trim();}
}
