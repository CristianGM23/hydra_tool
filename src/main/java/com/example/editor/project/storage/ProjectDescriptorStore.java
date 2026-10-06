package com.example.editor.project.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import com.example.editor.project.ProjectDescriptor;
import com.example.editor.project.ProjectException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Persiste el descriptor raíz mediante sustitución atómica. */
@Component
public class ProjectDescriptorStore {
    public static final String FILE_NAME = "modeling-project.json";
    private final ObjectMapper mapper;
    private final ProjectFileOperations files;
    /** Descripción: Inicializa una instancia de ProjectDescriptorStore con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; files: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectDescriptorStore(ObjectMapper mapper,ProjectFileOperations files) { this.mapper=mapper; this.files=files; }
    /** Descripción: Obtiene la información correspondiente a «read» dentro de ProjectDescriptorStore. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectDescriptor read(Path root) {
        Path path = root.resolve(FILE_NAME);
        if(!Files.isRegularFile(path)) throw new ProjectException(ProjectException.Reason.NOT_FOUND,"The directory does not contain modeling-project.json");
        try { return mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),ProjectDescriptor.class); }
        catch(JacksonException | IllegalArgumentException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not parse the project descriptor",exception); }
        catch(IOException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not read the project descriptor",exception); }
    }
    /** Descripción: Actualiza el estado gestionado por ProjectDescriptorStore mediante la operación «write». Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void write(Path root,ProjectDescriptor descriptor) {
        try { files.writeAtomic(root.resolve(FILE_NAME),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(descriptor)); }
        catch(JacksonException exception) { throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,"Could not serialize the project descriptor",exception); }
    }
}
