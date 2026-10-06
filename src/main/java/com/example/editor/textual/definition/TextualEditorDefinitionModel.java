package com.example.editor.textual.definition;

import java.util.*;

/**
 * Neutral textual-editor definition. By default it follows the active successful grammar snapshot; PINNED can be requested explicitly through settings when reproducibility requires a fixed snapshot.
 * Cross-layer IDs are references to existing HYDRA layers; they do not duplicate their models.
 */
public record TextualEditorDefinitionModel(
        String schemaVersion,
        String id,
        String name,
        String grammarId,
        String compilationSnapshotId,
        String syntaxStyleDefinitionId,
        List<String> fileExtensions,
        String entryRule,
        TextualRuntimeMode runtimeMode,
        List<TextualEditorFeature> features,
        List<String> validationModelIds,
        List<String> actionModelIds,
        List<String> eventDefinitionIds,
        List<String> eventFlowIds,
        List<String> gamificationDefinitionIds,
        Map<String,String> settings) {

    public TextualEditorDefinitionModel {
        schemaVersion=text(schemaVersion); if(schemaVersion.isEmpty()) schemaVersion="1.0";
        id=text(id); name=text(name); grammarId=text(grammarId);
        compilationSnapshotId=text(compilationSnapshotId); syntaxStyleDefinitionId=text(syntaxStyleDefinitionId);
        fileExtensions=normalizeExtensions(fileExtensions); entryRule=text(entryRule);
        runtimeMode=runtimeMode==null?TextualRuntimeMode.AUTO:runtimeMode;
        features=features==null?List.of():features.stream().filter(Objects::nonNull).distinct().toList();
        validationModelIds=copy(validationModelIds); actionModelIds=copy(actionModelIds);
        eventDefinitionIds=copy(eventDefinitionIds); eventFlowIds=copy(eventFlowIds);
        gamificationDefinitionIds=copy(gamificationDefinitionIds);
        settings=settings==null?Map.of():Map.copyOf(settings);
    }

    /** Compatibility constructor for the initial V4.32.97 draft before runtime/file metadata became explicit. */
    /** Descripción: Inicializa una instancia de TextualEditorDefinitionModel con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; grammarId: identificador que delimita el recurso objetivo; compilationSnapshotId: identificador que delimita el recurso objetivo; syntaxStyleDefinitionId: identificador que delimita el recurso objetivo; features: dato de entrada necesario para la operación; validationModelIds: dato de entrada necesario para la operación; actionModelIds: dato de entrada necesario para la operación; eventDefinitionIds: dato de entrada necesario para la operación; eventFlowIds: dato de entrada necesario para la operación; gamificationDefinitionIds: dato de entrada necesario para la operación; settings: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualEditorDefinitionModel(String schemaVersion,String id,String name,String grammarId,String compilationSnapshotId,
            String syntaxStyleDefinitionId,List<TextualEditorFeature> features,List<String> validationModelIds,List<String> actionModelIds,
            List<String> eventDefinitionIds,List<String> eventFlowIds,List<String> gamificationDefinitionIds,Map<String,String> settings) {
        this(schemaVersion,id,name,grammarId,compilationSnapshotId,syntaxStyleDefinitionId,List.of(),"",TextualRuntimeMode.AUTO,
                features,validationModelIds,actionModelIds,eventDefinitionIds,eventFlowIds,gamificationDefinitionIds,settings);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualEditorDefinitionModel. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de TextualEditorDefinitionModel. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values){return values==null?List.of():values.stream().map(TextualEditorDefinitionModel::text).filter(v->!v.isBlank()).distinct().toList();}
    /** Descripción: Ejecuta la operación «normalizeExtensions» correspondiente a la responsabilidad de TextualEditorDefinitionModel. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalizeExtensions(List<String> values){
        if(values==null)return List.of();
        List<String> result=new ArrayList<>();
        for(String raw:values){String value=text(raw).toLowerCase(Locale.ROOT);if(value.isBlank())continue;if(!value.startsWith("."))value="."+value;if(!value.matches("\\.[a-z0-9][a-z0-9._-]{0,31}"))throw new IllegalArgumentException("Unsafe textual-model file extension: "+raw);if(!result.contains(value))result.add(value);}
        return List.copyOf(result);
    }
}
