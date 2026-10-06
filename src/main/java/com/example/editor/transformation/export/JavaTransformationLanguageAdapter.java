package com.example.editor.transformation.export;

import org.springframework.stereotype.Component;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import tools.jackson.databind.ObjectMapper;

/** Exporta el subconjunto M2M/M2T exacto ejecutable por el bridge Java oficial. */
@Component
public final class JavaTransformationLanguageAdapter extends NativeCodeTransformationLanguageAdapterSupport {
    /** Descripción: Inicializa una instancia de JavaTransformationLanguageAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JavaTransformationLanguageAdapter(ObjectMapper mapper){super(mapper);}
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de JavaTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language(){return TransformationExportLanguage.JAVA;}
    /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de JavaTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapperPath(TransformationDefinitionDocument document){return "src/main/java/generated/HydraTransformation.java";}
    /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de JavaTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapper(TransformationDefinitionDocument document){return """
            package generated;

            import java.nio.file.Path;

            public final class HydraTransformation {
                private HydraTransformation() {}
                public static Path canonicalDefinition(Path bundleRoot) { return bundleRoot.resolve("transformation.json"); }
                public static String transformationId() { return "%s"; }
                public static String transformationKind() { return "%s"; }
            }
            """.formatted(escape(document.id()),document.kind().name());}
    /** Escapa literales Java mínimos del identificador canónico. */ /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de JavaTransformationLanguageAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
}
