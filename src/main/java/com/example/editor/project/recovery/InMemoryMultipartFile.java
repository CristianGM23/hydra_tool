package com.example.editor.project.recovery;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/** MultipartFile inmutable usado solo para reutilizar adaptadores de importación con recursos ya validados del ZIP. */
final class InMemoryMultipartFile implements MultipartFile {
    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    /** Descripción: Inicializa una instancia de InMemoryMultipartFile con sus dependencias y datos de entrada. Entradas: name: dato de entrada necesario para la operación; originalFilename: archivo o contenido que debe procesarse; contentType: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
        this.name = name == null || name.isBlank() ? "resource" : name;
        this.originalFilename = originalFilename == null ? "resource" : originalFilename;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : content.clone();
    }
    /** Descripción: Obtiene la información correspondiente a «getName» dentro de InMemoryMultipartFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String getName() { return name; }
    /** Descripción: Obtiene la información correspondiente a «getOriginalFilename» dentro de InMemoryMultipartFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String getOriginalFilename() { return originalFilename; }
    /** Descripción: Obtiene la información correspondiente a «getContentType» dentro de InMemoryMultipartFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String getContentType() { return contentType; }
    /** Descripción: Evalúa la condición «isEmpty» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ @Override public boolean isEmpty() { return content.length == 0; }
    /** Descripción: Obtiene la información correspondiente a «getSize» dentro de InMemoryMultipartFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public long getSize() { return content.length; }
    /** Descripción: Obtiene la información correspondiente a «getBytes» dentro de InMemoryMultipartFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] getBytes() { return content.clone(); }
    /** Descripción: Obtiene la información correspondiente a «getInputStream» dentro de InMemoryMultipartFile. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
    /** Descripción: Ejecuta la operación «transferTo» correspondiente a la responsabilidad de InMemoryMultipartFile. Entradas: dest: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void transferTo(java.io.File dest) throws java.io.IOException { java.nio.file.Files.write(dest.toPath(), content); }
}
