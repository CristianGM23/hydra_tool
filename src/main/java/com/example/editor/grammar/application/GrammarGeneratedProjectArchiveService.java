package com.example.editor.grammar.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GeneratedGrammarProject;

/** Builds a deterministic ZIP from the provider-generated grammar project kept in a compilation snapshot. */
@Service
public class GrammarGeneratedProjectArchiveService {
    private final GrammarProperties properties;

    /**
     * Descripción: Inicializa el empaquetador seguro de proyectos generados de gramáticas.
     * Entradas: properties: límites configurables de número de archivos y bytes generados.
     * Salida: deja el servicio preparado para empaquetar proyectos previamente validados por el pipeline.
     */
    /** Descripción: Inicializa una instancia de GrammarGeneratedProjectArchiveService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarGeneratedProjectArchiveService(GrammarProperties properties) {
        this.properties = properties;
    }

    /**
     * Descripción: Empaqueta de forma determinista los archivos ya generados por Xtext, Langium o ANTLR.
     * Entradas: project: proyecto generado y normalizado que pertenece a un snapshot de compilación.
     * Salida: devuelve un ZIP en memoria con rutas relativas seguras y timestamps estables.
     */
    /** Descripción: Ejecuta la operación «archive» correspondiente a la responsabilidad de ArchiveWriteFailure. Entradas: project: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] archive(GeneratedGrammarProject project) {
        if (project == null || project.files().isEmpty()) {
            throw new IllegalArgumentException("The grammar snapshot does not contain a generated project");
        }
        if (project.files().size() > properties.getMaximumGeneratedFiles()) {
            throw new IllegalArgumentException("Generated project file limit exceeded");
        }
        if (project.expandedBytes() > properties.getMaximumGeneratedBytes()) {
            throw new IllegalArgumentException("Generated project byte limit exceeded");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                project.files().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .forEach(entry -> write(zip, entry.getKey(), entry.getValue()));
            }
            return bytes.toByteArray();
        } catch (ArchiveWriteFailure exception) {
            throw new IllegalStateException("Could not package the generated grammar project", exception.getCause());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not package the generated grammar project", exception);
        }
    }

    /**
     * Descripción: Añade una entrada textual al ZIP manteniendo metadatos deterministas.
     * Entradas: zip: flujo ZIP abierto; path: ruta relativa ya validada por GeneratedGrammarProject; content: contenido textual.
     * Salida: escribe exactamente una entrada o comunica un fallo de empaquetado.
     */
    /** Descripción: Actualiza el estado gestionado por ArchiveWriteFailure mediante la operación «write». Entradas: zip: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void write(ZipOutputStream zip, String path, String content) {
        try {
            ZipEntry entry = new ZipEntry(path.replace('\\', '/'));
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException exception) {
            throw new ArchiveWriteFailure(exception);
        }
    }

    /** Wraps checked ZIP failures while streaming entries through a deterministic ordered pipeline. */
    private static final class ArchiveWriteFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /**
         * Descripción: Envuelve un fallo de escritura ZIP para atravesar el pipeline ordenado de entradas.
         * Entradas: cause: excepción de E/S original.
         * Salida: crea una excepción interna que conserva la causa exacta.
         */
        /** Descripción: Inicializa una instancia de ArchiveWriteFailure con sus dependencias y datos de entrada. Entradas: cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ArchiveWriteFailure(IOException cause) { super(cause); }
    }
}
