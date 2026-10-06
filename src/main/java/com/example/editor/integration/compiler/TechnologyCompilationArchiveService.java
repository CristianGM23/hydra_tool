package com.example.editor.integration.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.verification.TechnologyProjectValidationDescriptor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Empaqueta resultados tecnológicos de forma determinista y sin escribir en el destino. */
@Service
public class TechnologyCompilationArchiveService {
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de TechnologyCompilationArchiveService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompilationArchiveService(JsonMapper mapper){this.mapper = mapper;}

    /** Descripción: Crea o prepara el resultado de «createArchive» dentro de TechnologyCompilationArchiveService. Entradas: result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] createArchive(TechnologyCompilationResult result){
        try(ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)){
            for(TechnologyGeneratedFile file : result.files().stream().sorted(Comparator.comparing(TechnologyGeneratedFile::relativePath)).toList()){
                ZipEntry entry = new ZipEntry(file.relativePath()); entry.setTime(0L); zip.putNextEntry(entry); zip.write(file.content()); zip.closeEntry();
            }
            ZipEntry manifest = new ZipEntry("META-INF/hydra-technology-compilation.json"); manifest.setTime(0L); zip.putNextEntry(manifest);
            zip.write(mapper.writeValueAsBytes(new TechnologyCompilationArchiveManifest(result.technology(), "1.0", Instant.EPOCH,
                    result.files().stream().map(file -> new TechnologyCompilationArchiveManifest.FileEntry(file.relativePath(), file.mediaType(), sha256(file.content()))).toList(), result.metadata())));
            zip.closeEntry(); zip.finish(); return output.toByteArray();
        }catch(IOException | RuntimeException exception){throw new TechnologyCompilationException("Could not create technology compilation archive", exception);}
    }


    /** Descripción: Ejecuta la operación «importArchive» correspondiente a la responsabilidad de TechnologyCompilationArchiveService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyCompilationArchiveImportResult importArchive(byte[] content){
        if(content == null || content.length == 0){throw new TechnologyCompilationException("Technology archive is empty");}
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)){
            Map<String, byte[]> entries = new LinkedHashMap<>(); ZipEntry entry;
            while((entry = zip.getNextEntry()) != null){
                String path = safeEntry(entry.getName());
                if(entry.isDirectory()){continue;}
                if(entries.put(path, com.example.editor.io.BoundedIo.read(zip, 64L * 1024L * 1024L)) != null){throw new TechnologyCompilationException("Duplicate technology archive entry: " + path);}
                if(entries.size() > 8192){throw new TechnologyCompilationException("Technology archive contains too many entries");}
            }
            byte[] manifestBytes = entries.remove("META-INF/hydra-technology-compilation.json");
            if(manifestBytes == null){throw new TechnologyCompilationException("Technology archive manifest is missing");}
            TechnologyCompilationArchiveManifest manifest = mapper.readValue(manifestBytes, TechnologyCompilationArchiveManifest.class);
            Map<String, TechnologyCompilationArchiveManifest.FileEntry> expected = new LinkedHashMap<>();
            manifest.files().forEach(value -> expected.put(value.path(), value));
            List<TechnologyGeneratedFile> files = new ArrayList<>(); List<String> verified = new ArrayList<>();
            for(var expectedEntry : expected.entrySet()){
                byte[] bytes = entries.remove(expectedEntry.getKey());
                if(bytes == null){throw new TechnologyCompilationException("Technology archive entry is missing: " + expectedEntry.getKey());}
                if(!sha256(bytes).equals(expectedEntry.getValue().sha256())){throw new TechnologyCompilationException("Technology archive checksum mismatch: " + expectedEntry.getKey());}
                files.add(new TechnologyGeneratedFile(expectedEntry.getKey(), expectedEntry.getValue().mediaType(), bytes,
                        InjectionFileOwnership.GENERATED, true, List.of(), Map.of("archiveVerified", "true")));
                verified.add(expectedEntry.getKey());
            }
            if(!entries.isEmpty()){throw new TechnologyCompilationException("Technology archive contains undeclared entries: " + entries.keySet());}
            TechnologyProjectValidationDescriptor descriptor = readValidationDescriptor(files, manifest);
            TechnologyCompilationResult compilation = new TechnologyCompilationResult(manifest.technology(), files, List.of(), List.of(), List.of(),
                    descriptor.verificationSteps(), List.of(), manifest.metadata());
            return new TechnologyCompilationArchiveImportResult(compilation, manifest, verified);
        }catch(IOException | RuntimeException exception){
            if(exception instanceof TechnologyCompilationException technologyException){throw technologyException;}
            throw new TechnologyCompilationException("Could not import technology compilation archive", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readValidationDescriptor» dentro de TechnologyCompilationArchiveService. Entradas: files: archivo o contenido que debe procesarse; manifest: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TechnologyProjectValidationDescriptor readValidationDescriptor(List<TechnologyGeneratedFile> files, TechnologyCompilationArchiveManifest manifest){
        return files.stream().filter(file -> file.relativePath().equals("META-INF/hydra-project-validation.json")).findFirst()
                .map(file -> {
                    try{return mapper.readValue(file.content(), TechnologyProjectValidationDescriptor.class);}
                    catch(RuntimeException exception){throw new TechnologyCompilationException("Could not read project validation descriptor", exception);}
                }).orElse(new TechnologyProjectValidationDescriptor("1.0", manifest.technology(), List.of(), Map.of()));
    }

    /** Descripción: Ejecuta la operación «safeEntry» correspondiente a la responsabilidad de TechnologyCompilationArchiveService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safeEntry(String value){
        String normalized = value == null ? "" : value.replace('\\', '/');
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.startsWith("//") || normalized.matches("^[A-Za-z]:.*") || normalized.contains("//")){
            throw new TechnologyCompilationException("Unsafe technology archive entry: " + value);
        }
        String[] segments = normalized.split("/", -1);
        for(String segment : segments){
            if(segment.isBlank() || segment.equals(".") || segment.equals("..")){
                throw new TechnologyCompilationException("Unsafe technology archive entry: " + value);
            }
        }
        return String.join("/", segments);
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de TechnologyCompilationArchiveService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] content){
        try{byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(content); return java.util.HexFormat.of().formatHex(hash);}
        catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
}
