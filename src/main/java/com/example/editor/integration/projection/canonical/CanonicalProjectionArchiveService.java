package com.example.editor.integration.projection.canonical;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.projection.ProjectionGenerationResult;
import tools.jackson.databind.json.JsonMapper;

/** Empaqueta proyecciones PlantUML/Draw.io de forma reproducible. */
@Service
public class CanonicalProjectionArchiveService {
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de CanonicalProjectionArchiveService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalProjectionArchiveService(JsonMapper mapper){this.mapper = mapper;}

    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalProjectionArchiveService. Entradas: format: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] create(CanonicalProjectionFormat format, ProjectionGenerationResult result){
        try(ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)){
            for(var artifact : result.artifacts().stream().sorted(Comparator.comparing(value -> value.relativePath())).toList()){
                ZipEntry entry = new ZipEntry(artifact.relativePath()); entry.setTime(0L); zip.putNextEntry(entry); zip.write(artifact.content()); zip.closeEntry();
            }
            ZipEntry manifest = new ZipEntry("META-INF/hydra-projection-bundle.json"); manifest.setTime(0L); zip.putNextEntry(manifest);
            zip.write(mapper.writeValueAsBytes(Map.of("schemaVersion", "1.0", "format", format.name(), "createdAt", Instant.EPOCH.toString(),
                    "artifacts", result.artifacts().stream().map(value -> Map.of("path", value.relativePath(), "mediaType", value.mediaType(), "sourceIds", value.sourceElementIds())).toList())));
            zip.closeEntry(); zip.finish(); return output.toByteArray();
        }catch(IOException | RuntimeException exception){throw new TechnologyCompilationException("Could not create projection archive", exception);}
    }
}
