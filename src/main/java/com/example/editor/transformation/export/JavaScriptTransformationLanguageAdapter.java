package com.example.editor.transformation.export;

import org.springframework.stereotype.Component;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import tools.jackson.databind.ObjectMapper;

/** Exports the exact managed M2M/M2T subset for the official JavaScript runtime. */
@Component
public final class JavaScriptTransformationLanguageAdapter extends NativeCodeTransformationLanguageAdapterSupport {
    /** Descripción: Inicializa una instancia de JavaScriptTransformationLanguageAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JavaScriptTransformationLanguageAdapter(ObjectMapper mapper) { super(mapper); }
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de JavaScriptTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language() { return TransformationExportLanguage.JAVASCRIPT; }
    /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de JavaScriptTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapperPath(TransformationDefinitionDocument document) { return "generated/hydra-transformation.js"; }
    /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de JavaScriptTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapper(TransformationDefinitionDocument document) {
        return """
                export const transformationId = %s;
                export const transformationKind = %s;
                export const canonicalDefinition = bundleRoot => `${bundleRoot}/transformation.json`;
                """.formatted(jsString(document.id()), jsString(document.kind().name()));
    }
    /** Descripción: Ejecuta la operación «jsString» correspondiente a la responsabilidad de JavaScriptTransformationLanguageAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String jsString(String value) {
        String escaped=value.replace("\\","\\\\").replace("'","\\'").replace("\r","\\r").replace("\n","\\n");
        return "'"+escaped+"'";
    }
}
