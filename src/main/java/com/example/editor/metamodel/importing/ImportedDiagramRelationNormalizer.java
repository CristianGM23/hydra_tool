package com.example.editor.metamodel.importing;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/**
 * Recupera durante la importación relaciones semánticas presentes únicamente
 * en el formato visual. Fuera de esta frontera el diagrama nunca es fuente de
 * verdad y solo conserva geometría y estilo.
 */
@Component
public class ImportedDiagramRelationNormalizer {

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de ImportedDiagramRelationNormalizer. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetaModel normalize(MetaModel metamodel, MetamodelDiagramDocument diagram) {
        if(metamodel == null || diagram == null || diagram.edges().isEmpty()) {
            return metamodel;
        }

        Map<String, MetaRelation> relations = new LinkedHashMap<>();
        metamodel.relations().forEach(item -> relations.put(item.id(), item));

        for(MetamodelDiagramDocument.EdgeDesign edge : diagram.edges()) {
            MetaRelationKind kind = parseKind(edge.kind());
            if(kind == null || edge.sourceClassId() == null || edge.sourceClassId().isBlank() || edge.targetClassId() == null || edge.targetClassId().isBlank()) {
                continue;
            }

            MetaRelation existing = relations.get(edge.relationId());
            if(existing != null) {
                if(existing.kind() != kind) {
                    Map<String, String> annotations = new LinkedHashMap<>(existing.annotations());
                    annotations.put("import:recoveredDiagramKind", kind.name());
                    relations.put(existing.id(), new MetaRelation(existing.id(), existing.name(), existing.sourceTypeId(), existing.targetTypeId(), kind, existing.sourceMultiplicity(), existing.targetMultiplicity(), existing.oppositeRelationId(), existing.ordered(), existing.unique(), annotations));
                }
                continue;
            }

            if(containsEquivalent(relations, edge.sourceClassId(), edge.targetClassId(), kind)) {
                continue;
            }

            String relationId = edge.relationId() == null || edge.relationId().isBlank()
                    ? "relation:imported:" + Integer.toUnsignedString((edge.sourceClassId() + ":" + edge.targetClassId() + ":" + kind).hashCode())
                    : edge.relationId();
            Map<String, String> annotations = Map.of("import:origin", "diagram");
            relations.put(relationId, new MetaRelation(relationId, edge.label() == null ? "" : edge.label(), edge.sourceClassId(), edge.targetClassId(), kind, null, null, "", true, true, annotations));
        }

        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, metamodel.id(), metamodel.name(), metamodel.classes(), metamodel.enums(), metamodel.dataTypes(), relations.values().stream().toList(), metamodel.annotations());
    }

    /** Descripción: Evalúa la condición «containsEquivalent» sin modificar el estado externo. Entradas: relations: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean containsEquivalent(Map<String, MetaRelation> relations, String sourceId, String targetId, MetaRelationKind kind) {
        return relations.values().stream().anyMatch(item -> item.sourceTypeId().equals(sourceId) && item.targetTypeId().equals(targetId) && item.kind() == kind);
    }

    /** Descripción: Ejecuta la operación «parseKind» correspondiente a la responsabilidad de ImportedDiagramRelationNormalizer. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelationKind parseKind(String value) {
        if(value == null || value.isBlank()) {
            return null;
        }
        try {
            return MetaRelationKind.valueOf(value);
        } catch(IllegalArgumentException exception) {
            return null;
        }
    }
}
