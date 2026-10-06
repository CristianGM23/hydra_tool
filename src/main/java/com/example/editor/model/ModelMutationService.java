package com.example.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.stereotype.Component;

import com.example.editor.emf.DynamicModelService;
import com.example.editor.emf.LoadedMetamodel;
import com.example.editor.emf.ModelOperationException;
import com.example.editor.model.ModelDocumentException.Reason;

/** Aplica mutaciones EMF a una única sesión ya protegida por ModelSessionManager. */
@Component
class ModelMutationService {
    private final DynamicModelService dynamicModelService;

    /** Descripción: Inicializa una instancia de ModelMutationService con sus dependencias y datos de entrada. Entradas: dynamicModelService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ ModelMutationService(DynamicModelService dynamicModelService) {
        this.dynamicModelService = dynamicModelService;
    }

    /** Descripción: Crea o prepara el resultado de «createObject» dentro de ClassifierLocation. Entradas: metamodel: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ EObject createObject(LoadedMetamodel metamodel, String classId) {
        ClassifierLocation location = parseClassId(classId);
        try {
            return dynamicModelService.createObject(metamodel, location.nsUri(), location.className());
        } catch(ModelOperationException exception) {
            throw invalid(exception);
        }
    }

    /** Descripción: Ejecuta la operación «addElement» correspondiente a la responsabilidad de ClassifierLocation. Entradas: session: dato de entrada necesario para la operación; containerId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void addElement(ModelSession session, String containerId, String referenceName, String classId) {
        EObject container = findObject(session, containerId);
        EObject child = createObject(session.loadedMetamodel, classId);
        try {
            dynamicModelService.addContained(container, referenceName, child);
            registerObject(session, child);
            session.dirty = true;
        } catch(ModelOperationException exception) {
            throw invalid(exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por ClassifierLocation mediante la operación «setAttribute». Entradas: session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; attributeName: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void setAttribute(ModelSession session, String elementId, String attributeName, String value) {
        try {
            dynamicModelService.setAttribute(findObject(session, elementId), attributeName, value);
            session.dirty = true;
        } catch(ModelOperationException exception) {
            throw invalid(exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «clearAttribute» de forma controlada. Entradas: session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo; attributeName: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void clearAttribute(ModelSession session, String elementId, String attributeName) {
        try {
            dynamicModelService.clearAttribute(findObject(session, elementId), attributeName);
            session.dirty = true;
        } catch(ModelOperationException exception) {
            throw invalid(exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por ClassifierLocation mediante la operación «setReference». Entradas: session: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void setReference(ModelSession session, String sourceId, String referenceName, String targetId) {
        try {
            dynamicModelService.setReference(findObject(session, sourceId), referenceName, findObject(session, targetId));
            session.dirty = true;
        } catch(ModelOperationException exception) {
            throw invalid(exception);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «removeReference» de forma controlada. Entradas: session: dato de entrada necesario para la operación; sourceId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void removeReference(ModelSession session, String sourceId, String referenceName, String targetId) {
        try {
            dynamicModelService.removeReference(findObject(session, sourceId), referenceName, findObject(session, targetId));
            session.dirty = true;
        } catch(ModelOperationException exception) {
            throw invalid(exception);
        }
    }

    /** Elimina un elemento conservando el contrato histórico de edición individual. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElement» de forma controlada. Entradas: session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteElement(ModelSession session, String elementId) { deleteElements(session, List.of(elementId)); }

    /**
     * Elimina una selección completa en una sola mutación de sesión. Se validan todos los IDs antes de tocar el modelo y
     * se reducen selecciones padre+hijo a sus raíces efectivas para evitar dobles borrados. Complejidad O(n + d).
     */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElements» de forma controlada. Entradas: session: dato de entrada necesario para la operación; elementIds: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void deleteElements(ModelSession session, List<String> elementIds) {
        if(elementIds == null || elementIds.isEmpty()) throw new ModelDocumentException(Reason.INVALID_REQUEST, "Select at least one model element to delete");
        List<EObject> selected = new ArrayList<>();
        Set<EObject> selectedSet = new LinkedHashSet<>();
        for(String elementId : new LinkedHashSet<>(elementIds)) {
            EObject object = findObject(session, elementId);
            if(object == session.rootObject) throw new ModelDocumentException(Reason.INVALID_REQUEST, "Root element cannot be deleted");
            selected.add(object); selectedSet.add(object);
        }
        List<EObject> deletionRoots = selected.stream().filter(object -> !hasSelectedAncestor(object, selectedSet)).toList();
        Set<EObject> deletedObjects = new LinkedHashSet<>();
        for(EObject root : deletionRoots) {
            deletedObjects.add(root);
            TreeIterator<EObject> contents = root.eAllContents();
            while(contents.hasNext()) deletedObjects.add(contents.next());
        }
        for(EObject root : deletionRoots) EcoreUtil.delete(root, true);
        for(EObject deletedObject : deletedObjects) {
            String deletedId = session.objectIds.remove(deletedObject);
            if(deletedId != null) session.objects.remove(deletedId);
        }
        session.dirty = true;
    }

    /** Descripción: Evalúa la condición «hasSelectedAncestor» sin modificar el estado externo. Entradas: object: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean hasSelectedAncestor(EObject object, Set<EObject> selected) {
        EObject current = object.eContainer();
        while(current != null) { if(selected.contains(current)) return true; current = current.eContainer(); }
        return false;
    }

    /** Descripción: Ejecuta la operación «registerObject» correspondiente a la responsabilidad de ClassifierLocation. Entradas: session: dato de entrada necesario para la operación; object: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String registerObject(ModelSession session, EObject object) {
        String id = "element-" + session.nextElementNumber++;
        session.objects.put(id, object);
        session.objectIds.put(object, id);
        return id;
    }

    /** Descripción: Ejecuta la operación «registerObjectTree» correspondiente a la responsabilidad de ClassifierLocation. Entradas: session: dato de entrada necesario para la operación; rootObject: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void registerObjectTree(ModelSession session, EObject rootObject) {
        registerObject(session, rootObject);
        TreeIterator<EObject> contents = rootObject.eAllContents();
        while(contents.hasNext()) registerObject(session, contents.next());
    }

    /** Descripción: Obtiene la información correspondiente a «findObject» dentro de ClassifierLocation. Entradas: session: dato de entrada necesario para la operación; elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ EObject findObject(ModelSession session, String elementId) {
        if(elementId == null || elementId.isBlank()) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "Element id must not be empty");
        }
        EObject object = session.objects.get(elementId);
        if(object == null) {
            throw new ModelDocumentException(Reason.NOT_FOUND, "Model element was not found: " + elementId);
        }
        return object;
    }

    /** Descripción: Ejecuta la operación «parseClassId» correspondiente a la responsabilidad de ClassifierLocation. Entradas: classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ClassifierLocation parseClassId(String classId) {
        if(classId == null || classId.isBlank()) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "EClass id must not be empty");
        }
        int separatorPosition = classId.lastIndexOf("#//");
        if(separatorPosition <= 0 || separatorPosition + 3 >= classId.length()) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "EClass id is not valid: " + classId);
        }
        return new ClassifierLocation(classId.substring(0, separatorPosition), classId.substring(separatorPosition + 3));
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de ClassifierLocation. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelDocumentException invalid(ModelOperationException exception) {
        return new ModelDocumentException(Reason.INVALID_REQUEST, exception.getMessage(), exception);
    }

    private record ClassifierLocation(String nsUri, String className) { }
}
