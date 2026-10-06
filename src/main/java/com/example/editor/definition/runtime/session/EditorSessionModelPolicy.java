package com.example.editor.definition.runtime.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.FormFieldDefinition;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.runtime.ExecutableEditorPlan;
import com.example.editor.definition.runtime.ExecutableRepresentationPlan;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/**
 * Centraliza las reglas semánticas puras usadas al editar un modelo desde una
 * sesión. Mantiene estas comprobaciones fuera del orquestador de sesión para que
 * nuevas reglas de metamodelo no hagan crecer {@link EditorModelSessionService}.
 */
@Component
public class EditorSessionModelPolicy {

    /** Descripción: Ejecuta la operación «formFor» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: classId: identificador que delimita el recurso objetivo; plan: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ FormDefinition formFor(String classId, ExecutableEditorPlan plan, CanonicalMetamodelIndex index) {
        FormDefinition direct = plan.formsByTargetClassId().get(classId);
        if(direct != null) return direct;
        return plan.formsByTargetClassId().entrySet().stream()
                .filter(entry -> index.isSameOrSubtype(classId, entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Descripción: Ejecuta la operación «mappingFor» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: classId: identificador que delimita el recurso objetivo; representation: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ NodeMapping mappingFor(String classId, ExecutableRepresentationPlan representation, CanonicalMetamodelIndex index) {
        List<NodeMapping> direct = representation.nodeMappingsByClassId().get(classId);
        if(direct != null && !direct.isEmpty()) return direct.get(0);
        return representation.nodeMappingsByClassId().entrySet().stream()
                .filter(entry -> index.isSameOrSubtype(classId, entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    /** Descripción: Ejecuta la operación «concreteClass» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: metamodel: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ MetaClass concreteClass(MetaModel metamodel, String classId) {
        MetaClass type = new CanonicalMetamodelIndex(metamodel).findClass(classId)
                .orElseThrow(() -> notFound("Class was not found: " + classId));
        if(type.kind() != MetaClassKind.CLASS) throw notExecutable("Only concrete classes can be instantiated: " + type.name());
        return type;
    }

    /** Descripción: Valida las condiciones necesarias para «ensureRootCompatible» y rechaza entradas no válidas. Entradas: representation: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void ensureRootCompatible(ExecutableRepresentationPlan representation, String classId, MetaModel metamodel) {
        if(representation.rootClassIds().isEmpty()) return;
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        if(representation.rootClassIds().stream().noneMatch(root -> index.isSameOrSubtype(classId, root))) {
            throw invalid("Class cannot be used as a root in this representation");
        }
    }

    /** Descripción: Obtiene la información correspondiente a «resolveContainment» dentro de EditorSessionModelPolicy. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación; containerClassId: identificador que delimita el recurso objetivo; targetClassId: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaRelation resolveContainment(String requested, String containerClassId, String targetClassId, MetaModel metamodel) {
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        if(requested != null && !requested.isBlank()) {
            MetaRelation relation = index.findRelation(requested)
                    .orElseThrow(() -> notFound("Containment relation was not found: " + requested));
            if(relation.kind() != MetaRelationKind.CONTAINMENT
                    || !index.isSameOrSubtype(containerClassId, relation.sourceTypeId())
                    || !index.isSameOrSubtype(targetClassId, relation.targetTypeId())) {
                throw invalid("Containment relation is incompatible with the selected elements");
            }
            return relation;
        }
        List<MetaRelation> candidates = metamodel.relations().stream()
                .filter(item -> item.kind() == MetaRelationKind.CONTAINMENT)
                .filter(item -> index.isSameOrSubtype(containerClassId, item.sourceTypeId())
                        && index.isSameOrSubtype(targetClassId, item.targetTypeId()))
                .toList();
        if(candidates.size() != 1) {
            throw invalid(candidates.isEmpty()
                    ? "No compatible containment relation exists"
                    : "Several containment relations are compatible; select one explicitly");
        }
        return candidates.get(0);
    }

    /** Descripción: Valida las condiciones necesarias para «ensureMultiplicity» y rechaza entradas no válidas. Entradas: model: dato de entrada necesario para la operación; relation: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void ensureMultiplicity(CanonicalModelDocument model, MetaRelation relation, String sourceId) {
        Integer upper = relation.targetMultiplicity() == null ? null : relation.targetMultiplicity().upperBound();
        if(upper == null) return;
        long count = model.relations().stream()
                .filter(item -> item.relationId().equals(relation.id()) && item.sourceElementId().equals(sourceId))
                .count();
        if(count >= upper) throw conflict("Relationship multiplicity would be exceeded: " + relation.name());
    }

    /** Descripción: Ejecuta la operación «normalizedAttributes» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: classId: identificador que delimita el recurso objetivo; values: colección de elementos que debe procesarse; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Map<String,Object> normalizedAttributes(String classId, Map<String,Object> values, MetaModel metamodel) {
        if(values == null || values.isEmpty()) return Map.of();
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        Map<String,Object> result = new LinkedHashMap<>();
        values.forEach((id, value) -> {
            if(!index.hasAttribute(id) || !index.featureBelongsToClass(classId, id)) {
                throw invalid("Attribute is not available for the selected class: " + id);
            }
            result.put(id, value);
        });
        return result;
    }

    /** Descripción: Ejecuta la operación «containmentClosure» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: model: dato de entrada necesario para la operación; root: ruta o ubicación utilizada por la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Set<String> containmentClosure(CanonicalModelDocument model, String root, MetaModel metamodel) {
        CanonicalMetamodelIndex index = new CanonicalMetamodelIndex(metamodel);
        Set<String> result = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(root);
        while(!pending.isEmpty()) {
            String id = pending.removeFirst();
            if(!result.add(id)) continue;
            model.relations().stream()
                    .filter(item -> item.sourceElementId().equals(id))
                    .filter(item -> index.findRelation(item.relationId())
                            .map(relation -> relation.kind() == MetaRelationKind.CONTAINMENT)
                            .orElse(false))
                    .map(CanonicalModelRelation::targetElementId)
                    .forEach(pending::addLast);
        }
        return result;
    }

    /** Descripción: Ejecuta la operación «copyModel» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: source: dato de entrada necesario para la operación; roots: dato de entrada necesario para la operación; elements: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ CanonicalModelDocument copyModel(CanonicalModelDocument source, List<String> roots,
            List<CanonicalModelElement> elements, List<CanonicalModelRelation> relations) {
        return new CanonicalModelDocument(source.schemaVersion(), source.id(), source.metamodelId(), source.metamodelEtag(),
                source.name(), roots, elements, relations, source.annotations());
    }

    /** Descripción: Valida las condiciones necesarias para «ensureRequiredFormValues» y rechaza entradas no válidas. Entradas: form: dato de entrada necesario para la operación; attributes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void ensureRequiredFormValues(FormDefinition form, Map<String,Object> attributes) {
        if(form == null) return;
        form.sections().forEach(section -> section.fields().stream().filter(FormFieldDefinition::required).forEach(field -> {
            Object value = attributes.get(field.featureId());
            if(value == null || value instanceof String text && text.isBlank()) {
                throw invalid("Required field must have a value: " + field.label());
            }
        }));
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException invalid(String message) {
        return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.INVALID_REQUEST, message);
    }

    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException notFound(String message) {
        return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_FOUND, message);
    }

    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException conflict(String message) {
        return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.CONFLICT, message);
    }

    /** Descripción: Ejecuta la operación «notExecutable» correspondiente a la responsabilidad de EditorSessionModelPolicy. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException notExecutable(String message) {
        return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.NOT_EXECUTABLE, message);
    }
}
