package com.example.editor.textual.definition;

import java.util.List;
import java.util.Map;

/** Resolved runtime/capability view for one textual editor definition and its pinned grammar snapshot. */
public record TextualRuntimeNegotiation(TextualRuntimeMode requestedMode, TextualRuntimeMode effectiveMode,
        String grammarSnapshotId, String providerId, List<String> fileExtensions, String entryRule,
        List<TextualEditorFeature> supportedFeatures, List<TextualEditorFeature> unavailableFeatures,
        Map<String,String> evidence) {
    public TextualRuntimeNegotiation {
        requestedMode=requestedMode==null?TextualRuntimeMode.AUTO:requestedMode;
        effectiveMode=effectiveMode==null?TextualRuntimeMode.PARSER_BRIDGE:effectiveMode;
        grammarSnapshotId=text(grammarSnapshotId);providerId=text(providerId);
        fileExtensions=fileExtensions==null?List.of():List.copyOf(fileExtensions);entryRule=text(entryRule);
        supportedFeatures=supportedFeatures==null?List.of():List.copyOf(supportedFeatures);
        unavailableFeatures=unavailableFeatures==null?List.of():List.copyOf(unavailableFeatures);
        evidence=evidence==null?Map.of():Map.copyOf(evidence);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualRuntimeNegotiation. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
