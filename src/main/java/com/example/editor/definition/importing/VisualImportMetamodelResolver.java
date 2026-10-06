package com.example.editor.definition.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportPreview;
import com.example.editor.metamodel.importing.MetamodelImportService;

/** Resolves the semantic metamodel before any visual mapping is accepted. Preview is side-effect free. */
@Service
public class VisualImportMetamodelResolver {
    public enum AssociationMode { EXISTING, EXTERNAL, EMBEDDED }
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 60L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1200;
    private static final List<String> CANDIDATE_EXTENSIONS = List.of(".ecore", ".xmi", ".emf", ".json", ".xml");

    private final CanonicalMetamodelApplicationService metamodels;
    private final MetamodelImportService importer;

    /** Descripción: Inicializa una instancia de VisualImportMetamodelResolver con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; importer: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public VisualImportMetamodelResolver(CanonicalMetamodelApplicationService metamodels, MetamodelImportService importer) {
        this.metamodels = metamodels;
        this.importer = importer;
    }

    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de AssociationMode. Entradas: requestedMode: solicitud con los datos necesarios para ejecutar la operación; existingMetamodelId: identificador que delimita el recurso objetivo; visualResources: dato de entrada necesario para la operación; metamodelResources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Resolution resolve(String requestedMode, String existingMetamodelId, List<MultipartFile> visualResources,
            List<MultipartFile> metamodelResources) {
        AssociationMode mode = parseMode(requestedMode);
        if(mode == AssociationMode.EXISTING) {
            MetaModel metamodel = metamodels.findById(existingMetamodelId).metamodel();
            return new Resolution(metamodel, new VisualMetamodelAssociation(mode.name(), metamodel.name(), true, "HYDRA",
                    "The visual representation was checked against the metamodel already stored in HYDRA."));
        }
        List<Candidate> candidates = mode == AssociationMode.EXTERNAL
                ? candidatesFromUploads(metamodelResources)
                : candidatesFromEmbeddedZip(visualResources);
        if(candidates.isEmpty()) {
            String where = mode == AssociationMode.EXTERNAL ? "the selected metamodel file/folder/ZIP" : "the visual ZIP";
            throw new IllegalArgumentException("No associated metamodel could be found in " + where
                    + ". Add an .ecore, XMI, JSON or XML metamodel, or choose a metamodel that is already stored in HYDRA.");
        }
        List<ResolvedCandidate> resolved = new ArrayList<>();
        for(Candidate candidate : candidates) {
            try {
                MetamodelImportPreview preview = importer.preview(candidate.name(), candidate.mediaType(), candidate.bytes(), MetamodelImportOptions.defaults());
                resolved.add(new ResolvedCandidate(candidate.name(), preview));
            } catch(RuntimeException ignored) {
                // A visual project can contain many XML/JSON files. Only successfully parsed metamodels are candidates.
            }
        }
        Map<String,ResolvedCandidate> unique = new LinkedHashMap<>();
        for(ResolvedCandidate candidate : resolved) unique.putIfAbsent(candidate.preview().metamodel().id(), candidate);
        if(unique.isEmpty()) {
            throw new IllegalArgumentException("HYDRA found possible metamodel files but none could be read as a valid metamodel. Check the file format, or associate an existing HYDRA metamodel explicitly.");
        }
        if(unique.size() > 1) {
            String names = String.join(", ", unique.values().stream().map(ResolvedCandidate::name).limit(8).toList());
            throw new IllegalArgumentException("More than one different metamodel was found (" + names
                    + "). Choose the correct metamodel explicitly so HYDRA does not guess the semantic model behind the representation.");
        }
        ResolvedCandidate selected = unique.values().iterator().next();
        String explanation = mode == AssociationMode.EMBEDDED
                ? "HYDRA found and analysed the metamodel packaged with the visual representation. It will be stored only when you confirm the import."
                : "HYDRA analysed the selected external metamodel. It will be stored only when you confirm the visual import.";
        return new Resolution(selected.preview().metamodel(), new VisualMetamodelAssociation(mode.name(), selected.name(), false,
                selected.preview().detectedFormat(), explanation));
    }

    /** Descripción: Ejecuta la operación «parseMode» correspondiente a la responsabilidad de AssociationMode. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private AssociationMode parseMode(String value) {
        if(value == null || value.isBlank()) return AssociationMode.EXISTING;
        try { return AssociationMode.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException exception) { throw new IllegalArgumentException("Unknown metamodel association mode: " + value); }
    }

    /** Descripción: Evalúa la condición «candidatesFromUploads» sin modificar el estado externo. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private List<Candidate> candidatesFromUploads(List<MultipartFile> resources) {
        if(resources == null || resources.isEmpty()) return List.of();
        List<Candidate> result = new ArrayList<>();
        for(MultipartFile resource : resources) {
            if(resource == null || resource.isEmpty()) continue;
            String name = safeName(resource);
            byte[] bytes = bytes(resource);
            if(name.toLowerCase(Locale.ROOT).endsWith(".zip")) result.addAll(readZip(bytes));
            else if(isCandidate(name)) result.add(new Candidate(name, resource.getContentType(), bytes));
        }
        return result;
    }

    /** Descripción: Evalúa la condición «candidatesFromEmbeddedZip» sin modificar el estado externo. Entradas: resources: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private List<Candidate> candidatesFromEmbeddedZip(List<MultipartFile> resources) {
        if(resources == null || resources.isEmpty()) return List.of();
        List<Candidate> result = new ArrayList<>();
        for(MultipartFile resource : resources) {
            if(resource == null || resource.isEmpty()) continue;
            String name = safeName(resource);
            if(name.toLowerCase(Locale.ROOT).endsWith(".zip")) result.addAll(readZip(bytes(resource)));
        }
        return result;
    }

    /** Descripción: Obtiene la información correspondiente a «readZip» dentro de AssociationMode. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<Candidate> readZip(byte[] content) {
        List<Candidate> result = new ArrayList<>();
        long total = 0; int entries = 0;
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                if(entry.isDirectory()) continue;
                if(++entries > MAX_ENTRIES) throw new IllegalArgumentException("The ZIP contains too many entries to analyse safely.");
                String name = entry.getName().replace('\\','/');
                if(name.startsWith("/") || name.contains("../")) throw new IllegalArgumentException("The ZIP contains an unsafe path: " + name);
                if(!isCandidate(name)) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read; long size = 0;
                while((read = zip.read(buffer)) != -1) {
                    size += read; total += read;
                    if(size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw new IllegalArgumentException("The ZIP exceeds the safe metamodel analysis limits.");
                    out.write(buffer,0,read);
                }
                result.add(new Candidate(name, mediaType(name), out.toByteArray()));
            }
        } catch(IOException exception) { throw new IllegalArgumentException("The ZIP containing the visual/metamodel resources could not be read.", exception); }
        return result;
    }

    /** Descripción: Evalúa la condición «isCandidate» sin modificar el estado externo. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isCandidate(String name) { String lower = name.toLowerCase(Locale.ROOT); return CANDIDATE_EXTENSIONS.stream().anyMatch(lower::endsWith); }
    /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de AssociationMode. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] bytes(MultipartFile file) { try { if(file.getSize() > MAX_TOTAL_BYTES) throw new IllegalArgumentException("Metamodel resource is too large: " + safeName(file)); return file.getBytes(); } catch(IOException e) { throw new IllegalArgumentException("Could not read metamodel resource: " + safeName(file),e); } }
    /** Descripción: Ejecuta la operación «safeName» correspondiente a la responsabilidad de AssociationMode. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeName(MultipartFile file) { String name=file.getOriginalFilename(); return name==null||name.isBlank()?"upload":name.replace('\\','/').replaceAll("^.*/",""); }
    /** Descripción: Ejecuta la operación «mediaType» correspondiente a la responsabilidad de AssociationMode. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mediaType(String name) { String lower=name.toLowerCase(Locale.ROOT); if(lower.endsWith(".json")) return "application/json"; if(lower.endsWith(".xml")||lower.endsWith(".ecore")||lower.endsWith(".xmi")) return "application/xml"; return "application/octet-stream"; }

    public record Resolution(MetaModel metamodel, VisualMetamodelAssociation association) {}
    private record Candidate(String name, String mediaType, byte[] bytes) { Candidate { mediaType = mediaType == null ? "application/octet-stream" : mediaType; } }
    private record ResolvedCandidate(String name, MetamodelImportPreview preview) {}
}
