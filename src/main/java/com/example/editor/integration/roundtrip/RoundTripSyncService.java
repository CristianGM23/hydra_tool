package com.example.editor.integration.roundtrip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import tools.jackson.databind.json.JsonMapper;

/** Analiza cambios bidireccionales y crea compilaciones selectivas sin tocar el proyecto durante el preview. */
@Service
public class RoundTripSyncService {
    public static final String MANIFEST_PATH = "META-INF/hydra-roundtrip.json";
    private final JsonMapper mapper;
    private final ManualRegionPreserver manualRegions;
    private final RoundTripProperties properties;

    @Autowired
    /** Descripción: Inicializa RoundTripSyncService con límites configurables para no materializar ficheros nativos arbitrariamente grandes. Entradas: mapper: componente JSON; manualRegions: preservador de regiones; properties: límites round-trip. Salida: deja el servicio listo para previews y compilaciones selectivas acotadas. */ public RoundTripSyncService(JsonMapper mapper,ManualRegionPreserver manualRegions,RoundTripProperties properties){this.mapper=mapper;this.manualRegions=manualRegions;this.properties=properties;}
    /** Descripción: Constructor compatible para pruebas focales con los límites round-trip por defecto. Entradas: mapper: componente JSON; manualRegions: preservador de regiones. Salida: crea el servicio con configuración segura por defecto. */ public RoundTripSyncService(JsonMapper mapper,ManualRegionPreserver manualRegions){this(mapper,manualRegions,new RoundTripProperties());}

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de RoundTripSyncService. Entradas: generated: dato de entrada necesario para la operación; profileId: identificador que delimita el recurso objetivo; targetRoot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public RoundTripSyncPreview preview(TechnologyCompilationResult generated, String profileId, Path targetRoot){
        Path root = targetRoot.toAbsolutePath().normalize();
        RoundTripManifest previous = loadManifest(root);
        Map<String, RoundTripElementMapping> previousByPath = collapse(previous);
        Map<String, TechnologyGeneratedFile> generatedByPath = new LinkedHashMap<>();
        generated.files().forEach(file -> generatedByPath.put(file.relativePath(), file));
        Set<String> allPaths = new LinkedHashSet<>(); allPaths.addAll(previousByPath.keySet()); allPaths.addAll(generatedByPath.keySet());
        List<RoundTripFileChange> changes = new ArrayList<>(); List<String> conflicts = new ArrayList<>(); List<String> selectable = new ArrayList<>();
        for(String path : allPaths.stream().sorted().toList()){
            RoundTripElementMapping old = previousByPath.get(path); TechnologyGeneratedFile next = generatedByPath.get(path);
            Path nativePath = safeResolve(root, path); boolean exists = Files.exists(nativePath);
            String currentHash = exists ? hashFile(nativePath) : ""; String oldHash = old == null ? "" : old.generatedSha256(); String nextHash = next == null ? "" : sha256(next.content());
            RoundTripFileChange change;
            if(old == null && next != null){
                if(exists){change = new RoundTripFileChange(path, RoundTripChangeKind.ORPHAN_NATIVE, "", currentHash, nextHash, false, "A native file exists but is not owned by the previous HYDRA manifest"); conflicts.add(path);}
                else{change = new RoundTripFileChange(path, RoundTripChangeKind.ADD, "", "", nextHash, true, "New generated file"); selectable.add(path);}
            }else if(old != null && next == null){
                if(!exists){change = new RoundTripFileChange(path, RoundTripChangeKind.UNCHANGED, oldHash, "", "", false, "Already removed from the native project");}
                else if(currentHash.equals(oldHash)){change = new RoundTripFileChange(path, RoundTripChangeKind.NATIVE_DELETED, oldHash, currentHash, "", false, "Generated file is no longer required. Remove it through the managed rollback/removal operation so a backup and manifest update are preserved.");}
                else{change = new RoundTripFileChange(path, RoundTripChangeKind.MANUAL_CONFLICT, oldHash, currentHash, "", false, "Obsolete generated file contains manual changes"); conflicts.add(path);}
            }else if(!exists){
                change = new RoundTripFileChange(path, RoundTripChangeKind.NATIVE_DELETED, oldHash, "", nextHash, true, "Managed native file was deleted and can be regenerated"); selectable.add(path);
            }else if(currentHash.equals(nextHash)){
                change = new RoundTripFileChange(path, RoundTripChangeKind.UNCHANGED, oldHash, currentHash, nextHash, false, "Native file already matches the generated output");
            }else if(currentHash.equals(oldHash)){
                change = new RoundTripFileChange(path, RoundTripChangeKind.REGENERATE, oldHash, currentHash, nextHash, true, "Canonical source changed; native file is still unmodified"); selectable.add(path);
            }else{
                change = new RoundTripFileChange(path, RoundTripChangeKind.MANUAL_CONFLICT, oldHash, currentHash, nextHash, false, "Both canonical and native versions changed"); conflicts.add(path);
            }
            changes.add(change);
        }
        return new RoundTripSyncPreview(generated.technology(), profileId, changes, conflicts, selectable,
                Map.of("targetRoot", root.toString(), "previousManifest", previous == null ? "absent" : "present"));
    }

    /** Descripción: Ejecuta la operación «selectiveCompilation» correspondiente a la responsabilidad de RoundTripSyncService. Entradas: generated: dato de entrada necesario para la operación; preview: dato de entrada necesario para la operación; targetRoot: dato de entrada necesario para la operación; selectedPaths: ruta o ubicación utilizada por la operación; preserveManualRegions: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompilationResult selectiveCompilation(TechnologyCompilationResult generated,
            RoundTripSyncPreview preview, Path targetRoot, Set<String> selectedPaths, boolean preserveManualRegions){
        Set<String> selected = selectedPaths == null || selectedPaths.isEmpty() ? Set.copyOf(preview.selectablePaths()) : Set.copyOf(selectedPaths);
        if(selected.stream().anyMatch(path -> !preview.selectablePaths().contains(path))){throw new TechnologyCompilationException("Selection contains blocked or unknown round-trip paths");}
        List<TechnologyGeneratedFile> files = new ArrayList<>();
        for(TechnologyGeneratedFile file : generated.files()){
            if(!selected.contains(file.relativePath())){continue;}
            byte[] content = file.content();
            Path existing = safeResolve(targetRoot.toAbsolutePath().normalize(), file.relativePath());
            if(preserveManualRegions && Files.exists(existing) && isText(file.mediaType())){
                try{content = manualRegions.merge(com.example.editor.io.BoundedIo.read(existing,properties.getMaximumFileBytes()), content);}catch(IOException exception){throw new TechnologyCompilationException("Could not preserve manual regions: " + file.relativePath(), exception);}
            }
            files.add(new TechnologyGeneratedFile(file.relativePath(), file.mediaType(), content, file.ownership(), file.replaceable(), file.sourceDocumentIds(), file.metadata()));
        }
        return new TechnologyCompilationResult(generated.technology(), files, generated.dependencies(), generated.installationSteps(),
                generated.rollbackSteps(), generated.verificationSteps(), generated.diagnostics(), generated.metadata());
    }

    /** Descripción: Ejecuta la operación «manifestBytes» correspondiente a la responsabilidad de RoundTripSyncService. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] manifestBytes(RoundTripManifest manifest){
        try{return mapper.writeValueAsBytes(manifest);}catch(RuntimeException exception){throw new TechnologyCompilationException("Could not serialize round-trip manifest", exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «loadManifest» dentro de RoundTripSyncService. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private RoundTripManifest loadManifest(Path root){
        Path path = safeResolve(root, MANIFEST_PATH);
        if(!Files.exists(path)){return null;}
        try{return mapper.readValue(com.example.editor.io.BoundedIo.read(path,properties.getMaximumFileBytes()), RoundTripManifest.class);}
        catch(IOException | RuntimeException exception){throw new TechnologyCompilationException("Could not read round-trip manifest", exception);}
    }

    /** Descripción: Ejecuta la operación «collapse» correspondiente a la responsabilidad de RoundTripSyncService. Entradas: manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, RoundTripElementMapping> collapse(RoundTripManifest manifest){
        if(manifest == null){return Map.of();}
        Map<String, RoundTripElementMapping> result = new LinkedHashMap<>();
        manifest.mappings().stream().sorted(Comparator.comparing(RoundTripElementMapping::nativePath)).forEach(mapping -> result.putIfAbsent(mapping.nativePath(), mapping));
        return result;
    }

    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de RoundTripSyncService. Entradas: root: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeResolve(Path root, String value){
        String normalized = value.replace('\\', '/');
        if(normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")){throw new TechnologyCompilationException("Unsafe round-trip path: " + value);}
        Path result = root.resolve(normalized).normalize(); if(!result.startsWith(root)){throw new TechnologyCompilationException("Round-trip path escapes target root: " + value);} return result;
    }
    /** Descripción: Calcula SHA-256 de un fichero nativo en streaming y detiene la lectura si supera el presupuesto round-trip. Entradas: path: fichero que debe compararse. Salida: devuelve el hash hexadecimal sin materializar el fichero completo en memoria. */ private String hashFile(Path path){
        try(InputStream input=Files.newInputStream(path)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[64*1024];long total=0;int read;
            while((read=input.read(buffer))>=0){if(read==0)continue;total=Math.addExact(total,read);if(total>properties.getMaximumFileBytes())throw new TechnologyCompilationException("Native round-trip file exceeds the configured limit of "+properties.getMaximumFileBytes()+" bytes: "+path);digest.update(buffer,0,read);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(TechnologyCompilationException exception){throw exception;}
        catch(IOException|NoSuchAlgorithmException|ArithmeticException exception){throw new TechnologyCompilationException("Could not hash native file: " + path, exception);}
    }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de RoundTripSyncService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}}
    /** Descripción: Evalúa la condición «isText» sin modificar el estado externo. Entradas: mediaType: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isText(String mediaType){return mediaType != null && (mediaType.startsWith("text/") || mediaType.contains("json") || mediaType.contains("xml") || mediaType.contains("javascript"));}
}
