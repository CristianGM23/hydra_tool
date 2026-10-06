package com.example.editor.operation.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentResponse.ModelElementResponse;
import com.example.editor.model.ModelDocumentResponse.ModelReferenceResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.validation.action.ActionExecutionException;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;
import com.example.editor.validation.support.CanonicalMetamodelIndex;

/** Convierte sesiones EMF a documento canónico y aplica de vuelta un resultado de acción. */
public final class CanonicalModelRuntimeAdapter {
    private final MetaModel metamodel;
    private final CanonicalMetamodelIndex index;
    /** Descripción: Inicializa una instancia de CanonicalModelRuntimeAdapter con sus dependencias y datos de entrada. Entradas: metamodel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalModelRuntimeAdapter(MetaModel metamodel) { this.metamodel = metamodel; this.index = new CanonicalMetamodelIndex(metamodel); }

    /** Descripción: Ejecuta la operación «toCanonical» correspondiente a la responsabilidad de Ref. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalModelDocument toCanonical(ModelDocumentResponse source) {
        List<CanonicalModelElement> elements = new ArrayList<>();
        Map<String, String> classByElement = new LinkedHashMap<>();
        for(ModelElementResponse element : source.elements()) {
            MetaClass metaClass = resolveClass(element);
            classByElement.put(element.id(), metaClass.id());
            Map<String, Object> attributes = new LinkedHashMap<>();
            for(Map.Entry<String, Object> entry : element.attributes().entrySet()) findAttribute(metaClass.id(), entry.getKey()).ifPresent(attribute -> attributes.put(attribute.id(), entry.getValue()));
            elements.add(new CanonicalModelElement(element.id(), metaClass.id(), attributes, Map.of("runtime.classId", element.classId())));
        }
        List<CanonicalModelRelation> relations = new ArrayList<>();
        int sequence = 1;
        for(ModelReferenceResponse relation : source.references()) {
            String sourceClass = classByElement.get(relation.sourceId());
            MetaRelation metaRelation = findRelation(sourceClass, relation.referenceName(), false).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Runtime reference is not represented in the canonical metamodel: " + relation.referenceName()));
            relations.add(new CanonicalModelRelation("runtime:relation:" + sequence++, metaRelation.id(), relation.sourceId(), relation.targetId(), Map.of()));
        }
        for(ModelElementResponse element : source.elements()) {
            if(element.containerId() == null || element.containerId().isBlank()) continue;
            String sourceClass = classByElement.get(element.containerId());
            MetaRelation metaRelation = findRelation(sourceClass, element.containmentReference(), true).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Runtime containment is not represented in the canonical metamodel: " + element.containmentReference()));
            relations.add(new CanonicalModelRelation("runtime:relation:" + sequence++, metaRelation.id(), element.containerId(), element.id(), Map.of()));
        }
        return new CanonicalModelDocument("1.0", source.id(), source.metamodelId(), "", source.fileName(), List.of(source.rootElementId()), elements, relations, Map.of("runtime", "EMF"));
    }

    /** Descripción: Actualiza el estado gestionado por Ref mediante la operación «apply». Entradas: modelId: identificador que delimita el recurso objetivo; before: dato de entrada necesario para la operación; desired: dato de entrada necesario para la operación; service: servicio colaborador utilizado por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse apply(String modelId, ModelDocumentResponse before, CanonicalModelDocument desired, ModelDocumentService service) {
        Map<String, String> runtimeIds = new LinkedHashMap<>();
        before.elements().forEach(element -> runtimeIds.put(element.id(), element.id()));
        Set<String> originalIds = new LinkedHashSet<>(runtimeIds.keySet());
        List<CanonicalModelElement> pending = desired.elements().stream().filter(element -> !originalIds.contains(element.id())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        while(!pending.isEmpty()) {
            boolean progressed = false;
            for(var iterator = pending.iterator(); iterator.hasNext();) {
                CanonicalModelElement created = iterator.next();
                CanonicalModelRelation containment = desired.relations().stream().filter(relation -> relation.targetElementId().equals(created.id()) && index.findRelation(relation.relationId()).map(item -> item.kind() == MetaRelationKind.CONTAINMENT).orElse(false)).findFirst().orElse(null);
                if(containment == null) throw new ActionExecutionException("Runtime cannot create an additional root element: " + created.id());
                String containerId = runtimeIds.get(containment.sourceElementId());
                if(containerId == null) continue;
                MetaRelation relation = index.findRelation(containment.relationId()).orElseThrow();
                ModelDocumentResponse previous = service.find(modelId);
                ModelDocumentResponse updated = service.addElement(modelId, containerId, relationName(relation), ecoreClassId(created.classId(), before));
                String newId = updated.elements().stream().map(ModelElementResponse::id).filter(id -> previous.elements().stream().noneMatch(item -> item.id().equals(id))).findFirst().orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Created runtime element could not be identified"));
                runtimeIds.put(created.id(), newId);
                iterator.remove(); progressed = true;
            }
            if(!progressed) throw new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Created elements contain an unresolved containment cycle");
        }

        Map<String, CanonicalModelElement> beforeElements = toCanonical(before).elements().stream().collect(java.util.stream.Collectors.toMap(CanonicalModelElement::id, item -> item));
        for(CanonicalModelElement element : desired.elements()) {
            String runtimeId = runtimeIds.get(element.id());
            Map<String, Object> oldAttributes = beforeElements.getOrDefault(element.id(), new CanonicalModelElement("placeholder", element.classId(), Map.of(), Map.of())).attributes();
            Set<String> ids = new LinkedHashSet<>(oldAttributes.keySet()); ids.addAll(element.attributes().keySet());
            for(String attributeId : ids) {
                Object oldValue = oldAttributes.get(attributeId); Object newValue = element.attributes().get(attributeId);
                if(java.util.Objects.equals(oldValue, newValue)) continue;
                String name = attributeName(attributeId);
                if(newValue == null) service.clearAttribute(modelId, runtimeId, name);
                else if(newValue instanceof Iterable<?>) throw new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Many-valued attributes are not supported by the current EMF editor mutation API: " + name);
                else service.setAttribute(modelId, runtimeId, name, String.valueOf(newValue));
            }
        }

        syncReferences(modelId, before, desired, runtimeIds, service);

        Set<String> desiredIds = desired.elements().stream().map(CanonicalModelElement::id).collect(java.util.stream.Collectors.toSet());
        List<ModelElementResponse> deletions = before.elements().stream().filter(element -> !desiredIds.contains(element.id())).sorted(Comparator.comparingInt((ModelElementResponse element) -> depth(element.id(), before)).reversed()).toList();
        for(ModelElementResponse deleted : deletions) if(!deleted.id().equals(before.rootElementId())) service.deleteElement(modelId, deleted.id());
        return service.find(modelId);
    }

    /** Descripción: Actualiza el estado gestionado por Ref mediante la operación «syncReferences». Entradas: modelId: identificador que delimita el recurso objetivo; before: dato de entrada necesario para la operación; desired: dato de entrada necesario para la operación; runtimeIds: dato de entrada necesario para la operación; service: servicio colaborador utilizado por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void syncReferences(String modelId, ModelDocumentResponse before, CanonicalModelDocument desired, Map<String, String> runtimeIds, ModelDocumentService service) {
        record Ref(String relationId, String source, String target) { }
        Set<Ref> previous = toCanonical(before).relations().stream().filter(item -> index.findRelation(item.relationId()).map(relation -> relation.kind() != MetaRelationKind.CONTAINMENT).orElse(false)).map(item -> new Ref(item.relationId(), item.sourceElementId(), item.targetElementId())).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Ref> next = desired.relations().stream().filter(item -> index.findRelation(item.relationId()).map(relation -> relation.kind() != MetaRelationKind.CONTAINMENT).orElse(false)).map(item -> new Ref(item.relationId(), runtimeIds.getOrDefault(item.sourceElementId(), item.sourceElementId()), runtimeIds.getOrDefault(item.targetElementId(), item.targetElementId()))).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for(Ref relation : previous) if(!next.contains(relation)) service.removeReference(modelId, relation.source(), relationName(index.findRelation(relation.relationId()).orElseThrow()), relation.target());
        for(Ref relation : next) if(!previous.contains(relation)) service.setReference(modelId, relation.source(), relationName(index.findRelation(relation.relationId()).orElseThrow()), relation.target());
    }

    /** Descripción: Obtiene la información correspondiente a «findAttribute» dentro de Ref. Entradas: classId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.Optional<MetaAttribute> findAttribute(String classId, String name) { return metamodel.classes().stream().filter(owner -> index.isSameOrSubtype(classId, owner.id())).flatMap(owner -> owner.attributes().stream()).filter(attribute -> attribute.name().equals(name) || attribute.annotations().getOrDefault("ecore.name", "").equals(name)).findFirst(); }
    /** Descripción: Obtiene la información correspondiente a «findRelation» dentro de Ref. Entradas: sourceClassId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; containment: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.Optional<MetaRelation> findRelation(String sourceClassId, String name, boolean containment) { return metamodel.relations().stream().filter(relation -> index.isSameOrSubtype(sourceClassId, relation.sourceTypeId())).filter(relation -> (relation.kind() == MetaRelationKind.CONTAINMENT) == containment).filter(relation -> relationName(relation).equals(name)).findFirst(); }
    /** Descripción: Obtiene la información correspondiente a «resolveClass» dentro de Ref. Entradas: element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass resolveClass(ModelElementResponse element) { return metamodel.classes().stream().filter(item -> item.id().equals(element.classId()) || item.name().equals(element.className()) || item.annotations().getOrDefault("ecore.classId", "").equals(element.classId())).findFirst().orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Runtime class is not represented in the canonical metamodel: " + element.className())); }
    /** Descripción: Ejecuta la operación «attributeName» correspondiente a la responsabilidad de Ref. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String attributeName(String id) { return index.findAttribute(id).map(item -> item.annotations().getOrDefault("ecore.name", item.name())).orElseThrow(() -> new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Attribute was not found: " + id)); }
    /** Descripción: Ejecuta la operación «relationName» correspondiente a la responsabilidad de Ref. Entradas: relation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String relationName(MetaRelation relation) { String name = relation.annotations().getOrDefault("ecore.name", relation.name()); if(name == null || name.isBlank()) throw new OperationRuntimeException(OperationRuntimeException.Reason.EXECUTION_FAILED, "Relation has no runtime name: " + relation.id()); return name; }
    /** Descripción: Ejecuta la operación «ecoreClassId» correspondiente a la responsabilidad de Ref. Entradas: canonicalClassId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ecoreClassId(String canonicalClassId, ModelDocumentResponse document) { MetaClass metaClass = index.findClass(canonicalClassId).orElseThrow(); ModelElementResponse known = document.elements().stream().filter(item -> item.className().equals(metaClass.name())).findFirst().orElse(null); if(known != null) return known.classId(); String rootClassId = document.elements().stream().filter(item -> item.id().equals(document.rootElementId())).map(ModelElementResponse::classId).findFirst().orElseThrow(); int separator = rootClassId.lastIndexOf("#//"); return rootClassId.substring(0, separator + 3) + metaClass.name(); }
    /** Descripción: Ejecuta la operación «depth» correspondiente a la responsabilidad de Ref. Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int depth(String id, ModelDocumentResponse document) { int depth = 0; String current = id; Map<String, String> parents = document.elements().stream().filter(item -> item.containerId() != null).collect(java.util.stream.Collectors.toMap(ModelElementResponse::id, ModelElementResponse::containerId)); while(parents.containsKey(current)) { depth++; current = parents.get(current); } return depth; }
}
