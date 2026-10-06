package com.example.editor.metamodel.importing.updatesite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.io.AtomicFiles;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Compares mirrors, manages channels and validates repository rollback. */
@Service
public class UpdateSiteMirrorUpdateService {
    private static final int MAX_REPORTS = 200;
    private final UpdateSiteP2MirrorService mirrors;
    private final UpdateSiteImportProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de UpdateSiteMirrorUpdateService con sus dependencias y datos de entrada. Entradas: mirrors: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteMirrorUpdateService(UpdateSiteP2MirrorService mirrors,
            UpdateSiteImportProperties properties, ObjectMapper mapper) {
        this.mirrors = mirrors; this.properties = properties; this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public synchronized UpdateSiteMirrorUpdateReport evaluate(UpdateSiteMirrorUpdateRequest request) {
        require(request);
        UpdateSiteP2MirrorResult baseline = mirrors.find(request.baselineMirrorId());
        UpdateSiteP2MirrorResult candidate = mirrors.find(request.candidateMirrorId());
        List<UpdateSiteMirrorDeltaItem> deltas = deltas(baseline.manifest(), candidate.manifest());
        int added = count(deltas, UpdateSiteMirrorDeltaKind.ADDED);
        int removed = count(deltas, UpdateSiteMirrorDeltaKind.REMOVED);
        int changed = count(deltas, UpdateSiteMirrorDeltaKind.CHANGED);
        int unchanged = count(deltas, UpdateSiteMirrorDeltaKind.UNCHANGED);
        List<String> diagnostics = new ArrayList<>();
        boolean rollbackValid = baseline.validation().valid() && candidate.validation().valid()
                && (!request.validateRollback() || validRollback(baseline, candidate, diagnostics));
        if(!candidate.manifest().licenseReviewComplete()) diagnostics.add("Candidate mirror license review is incomplete");
        if(request.channel() == UpdateSiteMirrorChannel.STABLE && removed > 0 && !request.allowRemovals()) {
            diagnostics.add("Stable channel promotion blocks artifact removals unless explicitly approved");
        }
        boolean promotable = candidate.validation().valid() && candidate.manifest().licenseReviewComplete()
                && rollbackValid && (request.channel() != UpdateSiteMirrorChannel.STABLE || removed == 0 || request.allowRemovals());
        boolean promoted = false;
        if(request.promote()) {
            if(!promotable) throw new IllegalArgumentException("Mirror update cannot be promoted: " + String.join("; ", diagnostics));
            promote(request.channel(), request.candidateMirrorId(), request.retainPrevious()); promoted = true;
        }
        String reportId = "mirror-update-" + UpdateSiteHashing.sha256((request.baselineMirrorId() + "\n"
                + request.candidateMirrorId() + "\n" + request.channel() + "\n" + deltas).getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 24);
        UpdateSiteMirrorUpdateReport report = new UpdateSiteMirrorUpdateReport(reportId,
                request.baselineMirrorId(), request.candidateMirrorId(), request.channel(), Instant.now(),
                added, removed, changed, unchanged, deltas, rollbackValid, promotable, promoted, diagnostics);
        atomicWrite(reportPath(reportId), write(report)); trimReports(); return report;
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de UpdateSiteMirrorUpdateService. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteMirrorUpdateReport find(String reportId) {
        try {return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(reportPath(reportId)), UpdateSiteMirrorUpdateReport.class);}
        catch(JacksonException | IOException exception){throw new IllegalArgumentException("Mirror update report was not found", exception);}
    }

    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteMirrorUpdateReport> reports() {
        Path root = root().resolve("reports"); if(!Files.isDirectory(root)) return List.of();
        try(var stream = Files.list(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .flatMap(path -> {try{return java.util.stream.Stream.of(mapper.readValue(read(path), UpdateSiteMirrorUpdateReport.class));}
                        catch(RuntimeException exception){return java.util.stream.Stream.empty();}}).limit(MAX_REPORTS).toList();
        } catch(IOException exception){throw new IllegalStateException("Could not list mirror update reports", exception);}
    }

    public synchronized UpdateSiteMirrorChannelState channel(UpdateSiteMirrorChannel channel) {
        Path path = channelPath(channel);
        if(!Files.isRegularFile(path)) return new UpdateSiteMirrorChannelState(channel, "", Instant.EPOCH, List.of());
        try {return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path), UpdateSiteMirrorChannelState.class);}
        catch(JacksonException | IOException exception){throw new IllegalStateException("Could not read mirror channel state", exception);}
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: channel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized UpdateSiteMirrorChannelState rollback(UpdateSiteMirrorChannel channel) {
        UpdateSiteMirrorChannelState current = channel(channel);
        if(current.history().isEmpty()) throw new IllegalArgumentException("Mirror channel does not have a retained rollback target");
        String target = current.history().get(0); mirrors.find(target);
        List<String> history = new ArrayList<>(current.history().subList(1, current.history().size()));
        if(!current.currentMirrorId().isBlank()) history.add(0, current.currentMirrorId());
        UpdateSiteMirrorChannelState next = new UpdateSiteMirrorChannelState(channel, target, Instant.now(), history.stream().limit(20).toList());
        atomicWrite(channelPath(channel), write(next)); return next;
    }

    /** Descripción: Ejecuta la operación «promote» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: channel: dato de entrada necesario para la operación; mirrorId: identificador que delimita el recurso objetivo; retainPrevious: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void promote(UpdateSiteMirrorChannel channel, String mirrorId, boolean retainPrevious) {
        mirrors.find(mirrorId); UpdateSiteMirrorChannelState current = channel(channel);
        List<String> history = new ArrayList<>();
        if(retainPrevious && !current.currentMirrorId().isBlank() && !current.currentMirrorId().equals(mirrorId)) history.add(current.currentMirrorId());
        current.history().stream().filter(value -> !value.equals(mirrorId)).forEach(history::add);
        UpdateSiteMirrorChannelState next = new UpdateSiteMirrorChannelState(channel, mirrorId, Instant.now(), history.stream().distinct().limit(20).toList());
        atomicWrite(channelPath(channel), write(next));
    }

    /** Descripción: Ejecuta la operación «validRollback» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean validRollback(UpdateSiteP2MirrorResult baseline, UpdateSiteP2MirrorResult candidate, List<String> diagnostics) {
        if(!baseline.manifest().licenseReviewComplete()) diagnostics.add("Baseline mirror license review is incomplete");
        if(!baseline.validation().valid()) diagnostics.add("Baseline mirror is not valid for rollback");
        if(!candidate.validation().valid()) diagnostics.add("Candidate mirror is not valid for promotion");
        return baseline.manifest().licenseReviewComplete() && baseline.validation().valid() && candidate.validation().valid();
    }

    /** Descripción: Ejecuta la operación «deltas» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<UpdateSiteMirrorDeltaItem> deltas(UpdateSiteP2MirrorManifest baseline, UpdateSiteP2MirrorManifest candidate) {
        Map<String, List<UpdateSiteP2MirrorArtifact>> left = artifacts(baseline);
        Map<String, List<UpdateSiteP2MirrorArtifact>> right = artifacts(candidate);
        java.util.Set<String> coordinates = new java.util.TreeSet<>(); coordinates.addAll(left.keySet()); coordinates.addAll(right.keySet());
        List<UpdateSiteMirrorDeltaItem> result = new ArrayList<>();
        for(String coordinate : coordinates) pairVersions(coordinate, left.getOrDefault(coordinate, List.of()),
                right.getOrDefault(coordinate, List.of()), result);
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «pairVersions» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: coordinate: dato de entrada necesario para la operación; baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void pairVersions(String coordinate, List<UpdateSiteP2MirrorArtifact> baseline,
            List<UpdateSiteP2MirrorArtifact> candidate, List<UpdateSiteMirrorDeltaItem> result) {
        Map<String, UpdateSiteP2MirrorArtifact> leftByVersion = new LinkedHashMap<>();
        Map<String, UpdateSiteP2MirrorArtifact> rightByVersion = new LinkedHashMap<>();
        baseline.forEach(item -> leftByVersion.put(item.version(), item));
        candidate.forEach(item -> rightByVersion.put(item.version(), item));
        java.util.Set<String> commonVersions = new java.util.TreeSet<>(leftByVersion.keySet()); commonVersions.retainAll(rightByVersion.keySet());
        for(String version : commonVersions) {
            UpdateSiteP2MirrorArtifact before = leftByVersion.remove(version); UpdateSiteP2MirrorArtifact after = rightByVersion.remove(version);
            UpdateSiteMirrorDeltaKind kind = before.sha256().equals(after.sha256()) ? UpdateSiteMirrorDeltaKind.UNCHANGED : UpdateSiteMirrorDeltaKind.CHANGED;
            result.add(delta(coordinate + ":" + version, before, after, kind));
        }
        List<UpdateSiteP2MirrorArtifact> remainingLeft = leftByVersion.values().stream().sorted(Comparator.comparing(UpdateSiteP2MirrorArtifact::version)).toList();
        List<UpdateSiteP2MirrorArtifact> remainingRight = rightByVersion.values().stream().sorted(Comparator.comparing(UpdateSiteP2MirrorArtifact::version)).toList();
        int pairs = Math.min(remainingLeft.size(), remainingRight.size());
        for(int index = 0; index < pairs; index++) {
            UpdateSiteP2MirrorArtifact before = remainingLeft.get(index); UpdateSiteP2MirrorArtifact after = remainingRight.get(index);
            result.add(delta(coordinate + ":" + before.version() + "->" + after.version(), before, after, UpdateSiteMirrorDeltaKind.CHANGED));
        }
        for(int index = pairs; index < remainingLeft.size(); index++) {
            UpdateSiteP2MirrorArtifact before = remainingLeft.get(index);
            result.add(delta(coordinate + ":" + before.version(), before, null, UpdateSiteMirrorDeltaKind.REMOVED));
        }
        for(int index = pairs; index < remainingRight.size(); index++) {
            UpdateSiteP2MirrorArtifact after = remainingRight.get(index);
            result.add(delta(coordinate + ":" + after.version(), null, after, UpdateSiteMirrorDeltaKind.ADDED));
        }
    }

    /** Descripción: Ejecuta la operación «delta» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: coordinate: dato de entrada necesario para la operación; before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteMirrorDeltaItem delta(String coordinate, UpdateSiteP2MirrorArtifact before,
            UpdateSiteP2MirrorArtifact after, UpdateSiteMirrorDeltaKind kind) {
        return new UpdateSiteMirrorDeltaItem(coordinate, before != null ? before.classifier() : after.classifier(),
                before == null ? "" : before.version(), after == null ? "" : after.version(),
                before == null ? "" : before.sha256(), after == null ? "" : after.sha256(), kind);
    }

    /** Descripción: Ejecuta la operación «artifacts» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, List<UpdateSiteP2MirrorArtifact>> artifacts(UpdateSiteP2MirrorManifest manifest) {
        Map<String, List<UpdateSiteP2MirrorArtifact>> result = new LinkedHashMap<>();
        manifest.artifacts().stream().sorted(Comparator.comparing(UpdateSiteP2MirrorArtifact::classifier)
                .thenComparing(UpdateSiteP2MirrorArtifact::id).thenComparing(UpdateSiteP2MirrorArtifact::version))
                .forEach(item -> result.computeIfAbsent(item.classifier() + ":" + item.id(), key -> new ArrayList<>()).add(item));
        result.replaceAll((key, value) -> List.copyOf(value)); return result;
    }
    /** Descripción: Ejecuta la operación «count» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: items: colección de elementos que debe procesarse; kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int count(List<UpdateSiteMirrorDeltaItem> items, UpdateSiteMirrorDeltaKind kind){return (int)items.stream().filter(item -> item.kind() == kind).count();}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void require(UpdateSiteMirrorUpdateRequest request){if(request == null || request.baselineMirrorId().isBlank() || request.candidateMirrorId().isBlank()) throw new IllegalArgumentException("Both baseline and candidate mirror IDs are required"); if(request.baselineMirrorId().equals(request.candidateMirrorId())) throw new IllegalArgumentException("Baseline and candidate mirrors must differ");}
    /** Descripción: Ejecuta la operación «root» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path root(){return properties.storageRootPath().resolve("mirror-updates");}
    /** Descripción: Ejecuta la operación «reportPath» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path reportPath(String id){return root().resolve("reports").resolve(safe(id) + ".json");}
    /** Descripción: Ejecuta la operación «channelPath» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: channel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Path channelPath(UpdateSiteMirrorChannel channel){UpdateSiteMirrorChannel effective = channel == null ? UpdateSiteMirrorChannel.INTERNAL : channel; return root().resolve("channels").resolve(effective.name().toLowerCase() + ".json");}
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safe(String value){String result = value == null ? "" : value.trim(); if(!result.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("Invalid mirror update identifier"); return result;}
    /** Descripción: Actualiza el estado gestionado por UpdateSiteMirrorUpdateService mediante la operación «write». Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] write(Object value){try{return writer.writeValueAsBytes(value);}catch(Exception exception){throw new IllegalStateException("Could not serialize mirror update evidence", exception);}}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de UpdateSiteMirrorUpdateService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] read(Path path){try{return com.example.editor.io.BoundedIo.readInternal(path);}catch(IOException exception){throw new IllegalStateException("Could not read mirror update evidence", exception);}}
    /** Descripción: Ejecuta la operación «atomicWrite» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: path: ruta o ubicación utilizada por la operación; bytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void atomicWrite(Path path, byte[] bytes){try{AtomicFiles.writeReplacing(path,bytes);}catch(IOException exception){throw new IllegalStateException("Could not persist mirror update evidence", exception);}}
    /** Descripción: Ejecuta la operación «trimReports» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimReports(){Path reports = root().resolve("reports"); if(!Files.isDirectory(reports)) return; try(var stream = Files.list(reports)){List<Path> paths = stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted(Comparator.comparingLong(this::modified).reversed()).toList(); for(int index = MAX_REPORTS; index < paths.size(); index++) Files.deleteIfExists(paths.get(index));}catch(IOException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/metamodel/importing/updatesite/UpdateSiteMirrorUpdateService.java",ignored);}}
    /** Descripción: Ejecuta la operación «modified» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long modified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException exception){return 0L;}}
}
