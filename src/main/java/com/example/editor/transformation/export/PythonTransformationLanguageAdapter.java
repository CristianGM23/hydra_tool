package com.example.editor.transformation.export;

import org.springframework.stereotype.Component;

import com.example.editor.transformation.core.TransformationDefinitionDocument;

import tools.jackson.databind.ObjectMapper;

/** Exports the exact managed M2M/M2T subset for the official Python bridge. */
@Component
public final class PythonTransformationLanguageAdapter extends NativeCodeTransformationLanguageAdapterSupport {
    /** Descripción: Inicializa una instancia de PythonTransformationLanguageAdapter con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public PythonTransformationLanguageAdapter(ObjectMapper mapper) { super(mapper); }

    @Override
    /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de PythonTransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExportLanguage language() { return TransformationExportLanguage.PYTHON; }

    @Override
    /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de PythonTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected String wrapperPath(TransformationDefinitionDocument document) {
        return "generated/hydra_transformation.py";
    }

    @Override
    /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de PythonTransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected String wrapper(TransformationDefinitionDocument document) {
        return """
                from pathlib import Path

                TRANSFORMATION_ID = %s
                TRANSFORMATION_KIND = %s

                def canonical_definition(bundle_root: str | Path) -> Path:
                    return Path(bundle_root) / "transformation.json"
                """.formatted(pythonString(document.id()), pythonString(document.kind().name()));
    }

    /** Descripción: Ejecuta la operación «pythonString» correspondiente a la responsabilidad de PythonTransformationLanguageAdapter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String pythonString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\r", "\\r").replace("\n", "\\n");
        return "'" + escaped + "'";
    }
}
