package com.example.editor.integration.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Utilidades seguras compartidas por los validadores de proyectos generados. */
public final class TechnologyProjectValidationSupport {
    /** Descripción: Inicializa una instancia de TechnologyProjectValidationSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyProjectValidationSupport(){ }

    /** Descripción: Ejecuta la operación «files» correspondiente a la responsabilidad de TechnologyProjectValidationSupport. Entradas: root: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<Path> files(Path root){
        try(Stream<Path> stream = Files.walk(root)){return stream.filter(Files::isRegularFile).sorted().toList();}
        catch(IOException exception){throw new IllegalStateException("Could not inspect generated project", exception);}
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; predicate: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Path require(Path root, Predicate<Path> predicate, String description){
        return files(root).stream().filter(predicate).findFirst().orElseThrow(() -> new IllegalStateException("Missing " + description));
    }

    /** Descripción: Valida las condiciones necesarias para «requireSuffix» y rechaza entradas no válidas. Entradas: root: ruta o ubicación utilizada por la operación; suffix: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Path requireSuffix(Path root, String suffix, String description){
        return require(root, path -> path.toString().replace('\\', '/').endsWith(suffix), description);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TechnologyProjectValidationSupport. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String text(Path path){
        try{return com.example.editor.io.BoundedIo.readUtf8Internal(path);}catch(IOException exception){throw new IllegalStateException("Could not read " + path, exception);}
    }

    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de TechnologyProjectValidationSupport. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static JsonNode json(Path path){
        try{return JsonMapper.builder().findAndAddModules().build().readTree(com.example.editor.io.BoundedIo.readInternal(path));}
        catch(IOException | RuntimeException exception){throw new IllegalStateException("Invalid JSON: " + path, exception);}
    }

    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de TechnologyProjectValidationSupport. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void xml(Path path){
        try{SecureXmlSupport.parse(com.example.editor.io.BoundedIo.readInternal(path), path.toString());}
        catch(IOException exception){throw new IllegalStateException("Could not read XML: " + path, exception);}
    }

    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo; description: dato de entrada necesario para la operación; validation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TechnologyValidationStepResult check(String id, String description, Runnable validation){
        long started = System.nanoTime();
        try{
            validation.run();
            return new TechnologyValidationStepResult(id, description, TechnologyValidationStepStatus.PASSED,
                    List.of(), 0, elapsed(started), "", "", List.of());
        }catch(RuntimeException exception){
            List<String> diagnostics = new ArrayList<>(); diagnostics.add(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return new TechnologyValidationStepResult(id, description, TechnologyValidationStepStatus.FAILED,
                    List.of(), null, elapsed(started), "", "", diagnostics);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requireContains» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; expected: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void requireContains(String value, String expected, String description){
        if(value == null || !value.contains(expected)){throw new IllegalStateException(description + " must contain " + expected);}
    }

    /** Descripción: Valida las condiciones necesarias para «requireNonEmpty» y rechaza entradas no válidas. Entradas: values: colección de elementos que debe procesarse; description: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void requireNonEmpty(List<?> values, String description){
        if(values == null || values.isEmpty()){throw new IllegalStateException(description + " must not be empty");}
    }

    /** Descripción: Ejecuta la operación «elapsed» correspondiente a la responsabilidad de TechnologyProjectValidationSupport. Entradas: started: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long elapsed(long started){return (System.nanoTime() - started) / 1_000_000L;}
}
