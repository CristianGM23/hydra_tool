package com.example.editor.validation.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;

/** Indice neutral compartido por los comprobadores de validaciones y acciones. */
public final class CanonicalMetamodelIndex {
    private final Map<String, MetaClass> classes = new LinkedHashMap<>();
    private final Map<String, MetaAttribute> attributes = new LinkedHashMap<>();
    private final Map<String, String> attributeOwners = new LinkedHashMap<>();
    private final Map<String, MetaRelation> relations = new LinkedHashMap<>();
    private final Map<String, MetaOperation> operations = new LinkedHashMap<>();
    private final Map<String, String> operationOwners = new LinkedHashMap<>();
    private final Map<String, Set<String>> directSuperTypes = new LinkedHashMap<>();

    /** Descripción: Inicializa una instancia de CanonicalMetamodelIndex con sus dependencias y datos de entrada. Entradas: metamodel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelIndex(MetaModel metamodel) {
        if(metamodel == null) return;
        for(MetaClass metaClass : metamodel.classes()) {
            classes.put(metaClass.id(), metaClass);
            for(MetaAttribute attribute : metaClass.attributes()) {
                attributes.put(attribute.id(), attribute);
                attributeOwners.put(attribute.id(), metaClass.id());
            }
            for(MetaOperation operation : metaClass.operations()) {
                operations.put(operation.id(), operation);
                operationOwners.put(operation.id(), metaClass.id());
            }
        }
        for(MetaRelation relation : metamodel.relations()) {
            relations.put(relation.id(), relation);
            if(relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION) {
                directSuperTypes.computeIfAbsent(relation.sourceTypeId(), ignored -> new LinkedHashSet<>()).add(relation.targetTypeId());
            }
        }
    }

    /** Descripción: Evalúa la condición «hasClass» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasClass(String id) { return classes.containsKey(id); }
    /** Descripción: Evalúa la condición «hasAttribute» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasAttribute(String id) { return attributes.containsKey(id); }
    /** Descripción: Evalúa la condición «hasRelation» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasRelation(String id) { return relations.containsKey(id); }
    /** Descripción: Evalúa la condición «hasOperation» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasOperation(String id) { return operations.containsKey(id); }
    /** Descripción: Obtiene la información correspondiente a «findClass» dentro de CanonicalMetamodelIndex. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<MetaClass> findClass(String id) { return Optional.ofNullable(classes.get(id)); }
    /** Descripción: Obtiene la información correspondiente a «findAttribute» dentro de CanonicalMetamodelIndex. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<MetaAttribute> findAttribute(String id) { return Optional.ofNullable(attributes.get(id)); }
    /** Descripción: Obtiene la información correspondiente a «findRelation» dentro de CanonicalMetamodelIndex. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<MetaRelation> findRelation(String id) { return Optional.ofNullable(relations.get(id)); }
    /** Descripción: Obtiene la información correspondiente a «findOperation» dentro de CanonicalMetamodelIndex. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<MetaOperation> findOperation(String id) { return Optional.ofNullable(operations.get(id)); }
    /** Descripción: Ejecuta la operación «attributeOwner» correspondiente a la responsabilidad de CanonicalMetamodelIndex. Entradas: attributeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String attributeOwner(String attributeId) { return attributeOwners.getOrDefault(attributeId, ""); }
    /** Descripción: Ejecuta la operación «operationOwner» correspondiente a la responsabilidad de CanonicalMetamodelIndex. Entradas: operationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String operationOwner(String operationId) { return operationOwners.getOrDefault(operationId, ""); }

    /** Devuelve true cuando candidateClassId es la misma clase o un subtipo de expectedClassId. */
    /** Descripción: Evalúa la condición «isSameOrSubtype» sin modificar el estado externo. Entradas: candidateClassId: identificador que delimita el recurso objetivo; expectedClassId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isSameOrSubtype(String candidateClassId, String expectedClassId) {
        if(candidateClassId == null || candidateClassId.isBlank() || expectedClassId == null || expectedClassId.isBlank()) return false;
        if(candidateClassId.equals(expectedClassId)) return true;
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(candidateClassId);
        while(!pending.isEmpty()) {
            String current = pending.removeFirst();
            if(!visited.add(current)) continue;
            for(String parent : directSuperTypes.getOrDefault(current, Set.of())) {
                if(parent.equals(expectedClassId)) return true;
                pending.addLast(parent);
            }
        }
        return false;
    }

    /** Acepta atributos y relaciones heredados por la clase de contexto. */
    /** Descripción: Ejecuta la operación «featureBelongsToClass» correspondiente a la responsabilidad de CanonicalMetamodelIndex. Entradas: classId: identificador que delimita el recurso objetivo; featureId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean featureBelongsToClass(String classId, String featureId) {
        if(classId == null || classId.isBlank() || featureId == null || featureId.isBlank()) return false;
        String owner = attributeOwner(featureId);
        if(!owner.isBlank()) return isSameOrSubtype(classId, owner);
        MetaRelation relation = relations.get(featureId);
        return relation != null && isSameOrSubtype(classId, relation.sourceTypeId());
    }
}
