package com.example.editor.metamodel.importing.canonicalization;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/** Resultado de convertir metadatos transitorios de una importación al contrato canónico actual. */
public record ImportedMetamodelCanonicalization(MetaModel metamodel, TargetConfigurationDocument targetConfiguration,
        boolean projectionMetadataConsumed) {
}
