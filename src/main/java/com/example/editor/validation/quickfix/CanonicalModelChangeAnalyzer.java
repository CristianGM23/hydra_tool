package com.example.editor.validation.quickfix;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.runtime.ValidationChangeSet;

/** Calcula el alcance mínimo necesario para revalidar tras una acción. */
@Component
public class CanonicalModelChangeAnalyzer {
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de RelationKey. Entradas: before: dato de entrada necesario para la operación; after: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalModelChangeSummary analyze(CanonicalModelDocument before, CanonicalModelDocument after) {
        if(before == null || after == null) throw new IllegalArgumentException("before and after documents are required");
        Map<String, CanonicalModelElement> oldElements = elements(before);
        Map<String, CanonicalModelElement> newElements = elements(after);
        Set<String> created = new LinkedHashSet<>(newElements.keySet()); created.removeAll(oldElements.keySet());
        Set<String> deleted = new LinkedHashSet<>(oldElements.keySet()); deleted.removeAll(newElements.keySet());
        Set<String> modified = new LinkedHashSet<>();
        Set<String> attributes = new LinkedHashSet<>();
        Set<String> classes = new LinkedHashSet<>();
        for(String id : oldElements.keySet()) {
            if(!newElements.containsKey(id)) continue;
            CanonicalModelElement oldElement = oldElements.get(id);
            CanonicalModelElement newElement = newElements.get(id);
            if(!oldElement.classId().equals(newElement.classId())) {
                modified.add(id); classes.add(oldElement.classId()); classes.add(newElement.classId());
            }
            Set<String> ids = new LinkedHashSet<>(oldElement.attributes().keySet()); ids.addAll(newElement.attributes().keySet());
            for(String attributeId : ids) {
                if(!Objects.equals(oldElement.attributes().get(attributeId), newElement.attributes().get(attributeId))) {
                    modified.add(id); attributes.add(attributeId); classes.add(oldElement.classId()); classes.add(newElement.classId());
                }
            }
        }
        created.forEach(id -> classes.add(newElements.get(id).classId()));
        deleted.forEach(id -> classes.add(oldElements.get(id).classId()));

        Map<RelationKey, CanonicalModelRelation> oldRelations = relations(before);
        Map<RelationKey, CanonicalModelRelation> newRelations = relations(after);
        Set<RelationKey> changedRelations = new LinkedHashSet<>(oldRelations.keySet());
        changedRelations.addAll(newRelations.keySet());
        changedRelations.removeIf(key -> oldRelations.containsKey(key) && newRelations.containsKey(key));
        Set<String> relationIds = new LinkedHashSet<>();
        Set<String> elements = new LinkedHashSet<>();
        elements.addAll(created); elements.addAll(deleted); elements.addAll(modified);
        for(RelationKey relation : changedRelations) {
            relationIds.add(relation.relationId());
            elements.add(relation.sourceElementId());
            elements.add(relation.targetElementId());
        }
        boolean modelChanged = !created.isEmpty() || !deleted.isEmpty() || !modified.isEmpty() || !changedRelations.isEmpty()
                || !Objects.equals(before.rootElementIds(), after.rootElementIds()) || !Objects.equals(before.name(), after.name());
        ValidationChangeSet changeSet = new ValidationChangeSet(List.copyOf(elements), List.copyOf(classes), List.copyOf(attributes),
                List.copyOf(relationIds), List.of(), modelChanged);
        return new CanonicalModelChangeSummary(List.copyOf(created), List.copyOf(deleted), List.copyOf(modified),
                List.copyOf(attributes), List.copyOf(relationIds), List.copyOf(classes), changeSet);
    }

    /** Descripción: Ejecuta la operación «elements» correspondiente a la responsabilidad de RelationKey. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, CanonicalModelElement> elements(CanonicalModelDocument document) {
        Map<String, CanonicalModelElement> result = new LinkedHashMap<>();
        document.elements().forEach(element -> result.put(element.id(), element));
        return result;
    }

    /** Descripción: Ejecuta la operación «relations» correspondiente a la responsabilidad de RelationKey. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<RelationKey, CanonicalModelRelation> relations(CanonicalModelDocument document) {
        Map<RelationKey, CanonicalModelRelation> result = new LinkedHashMap<>();
        document.relations().forEach(relation -> result.put(new RelationKey(relation.relationId(), relation.sourceElementId(), relation.targetElementId()), relation));
        return result;
    }

    private record RelationKey(String relationId, String sourceElementId, String targetElementId) { }
}
