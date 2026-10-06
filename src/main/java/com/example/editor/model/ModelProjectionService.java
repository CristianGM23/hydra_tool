package com.example.editor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.springframework.stereotype.Component;

import com.example.editor.model.ModelDocumentResponse.ModelElementResponse;
import com.example.editor.model.ModelDocumentResponse.ModelReferenceResponse;

/**
 * Proyecta una sesión EMF al contrato REST histórico de modelos.
 *
 * Mantener la proyección fuera de ModelDocumentService permite evolucionar más
 * adelante hacia respuestas delta sin mezclar serialización con mutaciones.
 */
@Component
class ModelProjectionService {

    /** Descripción: Ejecuta la operación «full» correspondiente a la responsabilidad de ModelProjectionService. Entradas: session: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ModelDocumentResponse full(ModelSession session) {
        List<ModelElementResponse> elements = new ArrayList<>();
        List<ModelReferenceResponse> references = new ArrayList<>();
        for(var entry : session.objects.entrySet()) {
            elements.add(element(session, entry.getKey(), entry.getValue()));
            addReferences(session, entry.getKey(), entry.getValue(), references);
        }
        return new ModelDocumentResponse(session.id, session.metamodelId, session.fileName,
                session.objectIds.get(session.rootObject), session.dirty, elements, references);
    }

    /** Descripción: Ejecuta la operación «element» correspondiente a la responsabilidad de ModelProjectionService. Entradas: session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; object: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelElementResponse element(ModelSession session, String elementId, EObject object) {
        String containerId = object.eContainer() == null ? null : session.objectIds.get(object.eContainer());
        String containmentReference = object.eContainingFeature() == null ? null : object.eContainingFeature().getName();
        Map<String, Object> attributes = new LinkedHashMap<>();
        for(EAttribute attribute : object.eClass().getEAllAttributes()) {
            attributes.put(attribute.getName(), serializeAttributeValue(attribute, object.eGet(attribute)));
        }
        return new ModelElementResponse(elementId, classId(object), object.eClass().getName(), containerId,
                containmentReference, Collections.unmodifiableMap(new LinkedHashMap<>(attributes)));
    }

    /** Descripción: Ejecuta la operación «addReferences» correspondiente a la responsabilidad de ModelProjectionService. Entradas: session: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo; source: dato de entrada necesario para la operación; responses: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addReferences(ModelSession session, String sourceId, EObject source,
            List<ModelReferenceResponse> responses) {
        for(EReference reference : source.eClass().getEAllReferences()) {
            if(reference.isContainment() || !source.eIsSet(reference)) continue;
            Object value = source.eGet(reference);
            if(reference.isMany()) {
                for(Object target : (EList<?>) value) addReference(session, sourceId, reference.getName(), target, responses);
            } else addReference(session, sourceId, reference.getName(), value, responses);
        }
    }

    /** Descripción: Ejecuta la operación «addReference» correspondiente a la responsabilidad de ModelProjectionService. Entradas: session: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; targetValue: dato de entrada necesario para la operación; responses: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addReference(ModelSession session, String sourceId, String referenceName, Object targetValue,
            List<ModelReferenceResponse> responses) {
        if(!(targetValue instanceof EObject target)) return;
        String targetId = session.objectIds.get(target);
        if(targetId != null) responses.add(new ModelReferenceResponse(sourceId, referenceName, targetId));
    }

    /** Descripción: Ejecuta la operación «serializeAttributeValue» correspondiente a la responsabilidad de ModelProjectionService. Entradas: attribute: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Object serializeAttributeValue(EAttribute attribute, Object value) {
        if(value == null) return null;
        if(attribute.isMany()) {
            List<String> values = new ArrayList<>();
            for(Object emfValue : (EList<?>) value) {
                values.add(attribute.getEAttributeType().getEPackage().getEFactoryInstance()
                        .convertToString(attribute.getEAttributeType(), emfValue));
            }
            return List.copyOf(values);
        }
        return attribute.getEAttributeType().getEPackage().getEFactoryInstance()
                .convertToString(attribute.getEAttributeType(), value);
    }

    /** Descripción: Ejecuta la operación «classId» correspondiente a la responsabilidad de ModelProjectionService. Entradas: object: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String classId(EObject object) {
        return object.eClass().getEPackage().getNsURI() + "#//" + object.eClass().getName();
    }
}
