package com.example.editor.transformation.export;

import java.util.Set;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationKind;

/** SPI neutral para proyectar una definición canónica a un lenguaje externo sin convertirlo en fuente de verdad. */
public interface TransformationLanguageAdapter {
    /** Devuelve el lenguaje representado por el adaptador. */ /** Descripción: Ejecuta la operación «language» correspondiente a la responsabilidad de TransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ TransformationExportLanguage language();
    /** Declara las familias de transformación que puede proyectar. */ /** Descripción: Ejecuta la operación «supportedKinds» correspondiente a la responsabilidad de TransformationLanguageAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<TransformationKind> supportedKinds();
    /** Comprueba compatibilidad y genera únicamente cuando la fidelidad declarada puede respetarse. */ /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de TransformationLanguageAdapter. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ TransformationExportResult export(TransformationDefinitionDocument document,MetaModel source,MetaModel target);
}
