package com.example.editor.transformation.export;

import org.springframework.stereotype.Component;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import tools.jackson.databind.ObjectMapper;

/** Exporta un bundle Xtend que conserva la definición canónica completa. */
@Component
public final class XtendTransformationLanguageAdapter extends NativeCodeTransformationLanguageAdapterSupport {
    /** Descripción: Inicializa una instancia de XtendTransformationLanguageAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public XtendTransformationLanguageAdapter(ObjectMapper mapper){super(mapper);}
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de XtendTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language(){return TransformationExportLanguage.XTEND;}
    /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de XtendTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapperPath(TransformationDefinitionDocument document){return "src/generated/HydraTransformation.xtend";}
    /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de XtendTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapper(TransformationDefinitionDocument document){return """
            package generated

            import java.nio.file.Path

            final class HydraTransformation {
                def static canonicalDefinition(Path bundleRoot) { bundleRoot.resolve("transformation.json") }
                def static transformationId() { "%s" }
                def static transformationKind() { "%s" }
            }
            """.formatted(escape(document.id()),document.kind().name());}
    /** Escapa literales Xtend mínimos del identificador canónico. */ /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de XtendTransformationLanguageAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
}
