package com.example.editor.transformation.export;

import org.springframework.stereotype.Component;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import tools.jackson.databind.ObjectMapper;

/** Exports the exact managed M2M/M2T subset for the official TypeScript runtime. */
@Component
public final class TypeScriptTransformationLanguageAdapter extends NativeCodeTransformationLanguageAdapterSupport {
    /** Descripción: Inicializa una instancia de TypeScriptTransformationLanguageAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public TypeScriptTransformationLanguageAdapter(ObjectMapper mapper) { super(mapper); }
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de TypeScriptTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportLanguage language() { return TransformationExportLanguage.TYPESCRIPT; }
    /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de TypeScriptTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapperPath(TransformationDefinitionDocument document) { return "generated/hydra-transformation.ts"; }
    /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de TypeScriptTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override protected String wrapper(TransformationDefinitionDocument document) {
        return """
                export const transformationId: string = %s;
                export const transformationKind: string = %s;
                export function canonicalDefinition(bundleRoot: string): string { return `${bundleRoot}/transformation.json`; }
                """.formatted(tsString(document.id()), tsString(document.kind().name()));
    }
    /** Descripción: Ejecuta la operación «tsString» correspondiente a la responsabilidad de TypeScriptTransformationLanguageAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String tsString(String value) {
        String escaped=value.replace("\\","\\\\").replace("'","\\'").replace("\r","\\r").replace("\n","\\n");
        return "'"+escaped+"'";
    }
}
