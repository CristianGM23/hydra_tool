package com.example.editor.transformation.export;

import org.springframework.stereotype.Component;

import com.example.editor.transformation.core.TransformationDefinitionDocument;

import tools.jackson.databind.ObjectMapper;

/** Exports managed native M2M evidence for Go and keeps canonical fallback for M2T. */
@Component
public final class GoTransformationLanguageAdapter extends NativeCodeTransformationLanguageAdapterSupport {
    /** Descripción: Inicializa una instancia de GoTransformationLanguageAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GoTransformationLanguageAdapter(ObjectMapper mapper) { super(mapper); }

    @Override
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de GoTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExportLanguage language() { return TransformationExportLanguage.GO; }

    @Override
    /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de GoTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected String wrapperPath(TransformationDefinitionDocument document) {
        return "generated/hydra_transformation.go";
    }

    @Override
    /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de GoTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected String wrapper(TransformationDefinitionDocument document) {
        return """
                package generated

                import "path/filepath"

                const TransformationID = %s
                const TransformationKind = %s

                func CanonicalDefinition(bundleRoot string) string {
                    return filepath.Join(bundleRoot, "transformation.json")
                }
                """.formatted(goString(document.id()), goString(document.kind().name()));
    }

    /** Descripción: Ejecuta la operación «goString» correspondiente a la responsabilidad de GoTransformationLanguageAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String goString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
