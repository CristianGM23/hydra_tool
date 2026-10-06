package com.example.editor.integration.compatibility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Writes the matrix atomically below a caller-selected report root. */
@Service
public class TechnologyCompatibilityMatrixWriter {
    public static final String DEFAULT_PATH = "reports/hydra-compatibility.json";
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de TechnologyCompatibilityMatrixWriter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyCompatibilityMatrixWriter(JsonMapper mapper){this.mapper = Objects.requireNonNull(mapper, "mapper");}

    /** Descripción: Actualiza el estado gestionado por TechnologyCompatibilityMatrixWriter mediante la operación «write». Entradas: reportRoot: dato de entrada necesario para la operación; matrix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Path write(Path reportRoot, TechnologyCompatibilityMatrix matrix){return write(reportRoot, DEFAULT_PATH, matrix);}
    /** Descripción: Actualiza el estado gestionado por TechnologyCompatibilityMatrixWriter mediante la operación «write». Entradas: reportRoot: dato de entrada necesario para la operación; relativePath: ruta o ubicación utilizada por la operación; matrix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Path write(Path reportRoot, String relativePath, TechnologyCompatibilityMatrix matrix){
        Path root = Objects.requireNonNull(reportRoot, "reportRoot").toAbsolutePath().normalize();
        Path target = safeResolve(root, relativePath);
        try{
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".hydra-compatibility-", ".tmp");
            try{
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), Objects.requireNonNull(matrix, "matrix"));
                try{Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException exception){Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}
            return target;
        }catch(IOException exception){throw new IllegalStateException("Could not write compatibility matrix", exception);}
    }
    /** Descripción: Ejecuta la operación «safeResolve» correspondiente a la responsabilidad de TechnologyCompatibilityMatrixWriter. Entradas: root: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path safeResolve(Path root, String value){
        if(value == null || value.isBlank()){throw new IllegalArgumentException("relativePath is required");}
        String normalized = value.replace('\\', '/');
        if(normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")){
            throw new IllegalArgumentException("Unsafe compatibility report path: " + value);
        }
        Path resolved = root.resolve(normalized).normalize();
        if(!resolved.startsWith(root)){throw new IllegalArgumentException("Compatibility report path escapes root");}
        return resolved;
    }
}
