package com.example.editor.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.DynamicModelService;
import com.example.editor.emf.LoadedMetamodel;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.emf.XmiModelRepository;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;
import com.example.editor.model.ModelDocumentException.Reason;

/**
 * Fachada compatible para la edición de modelos EMF.
 *
 * El estado, las mutaciones, la proyección REST y el almacenamiento se delegan
 * en componentes acotados. El lock pertenece a cada sesión de modelo, de modo
 * que dos modelos independientes no quedan serializados por un monitor global.
 */
@Service
public class ModelDocumentService {
    private final StorageProperties storage;
    private final MetamodelRegistryService registry;
    private final MetamodelLoader metamodelLoader;
    private final XmiModelRepository xmiRepository;
    private final ModelSessionManager sessionManager;
    private final ModelMutationService mutations;
    private final ModelProjectionService projection;
    private final ModelFileStorageService files;

    /** Constructor principal utilizado por Spring. */
    @Autowired
    /** Descripción: Inicializa una instancia de ModelDocumentService con sus dependencias y datos de entrada. Entradas: storage: dato de entrada necesario para la operación; registry: dato de entrada necesario para la operación; metamodelLoader: dato de entrada necesario para la operación; xmiRepository: dato de entrada necesario para la operación; sessionManager: dato de entrada necesario para la operación; mutations: dato de entrada necesario para la operación; projection: dato de entrada necesario para la operación; files: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public ModelDocumentService(StorageProperties storage, MetamodelRegistryService registry, MetamodelLoader metamodelLoader,
            XmiModelRepository xmiRepository, ModelSessionManager sessionManager, ModelMutationService mutations,
            ModelProjectionService projection, ModelFileStorageService files) {
        this.storage = storage;
        this.registry = registry;
        this.metamodelLoader = metamodelLoader;
        this.xmiRepository = xmiRepository;
        this.sessionManager = sessionManager;
        this.mutations = mutations;
        this.projection = projection;
        this.files = files;
    }

    /** Firma histórica conservada para tests, extensiones y construcción manual. */
    /** Descripción: Inicializa una instancia de ModelDocumentService con sus dependencias y datos de entrada. Entradas: storage: dato de entrada necesario para la operación; registry: dato de entrada necesario para la operación; metamodelLoader: dato de entrada necesario para la operación; dynamicModelService: servicio colaborador utilizado por la operación; xmiRepository: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelDocumentService(StorageProperties storage, MetamodelRegistryService registry, MetamodelLoader metamodelLoader,
            DynamicModelService dynamicModelService, XmiModelRepository xmiRepository) {
        this(storage, registry, metamodelLoader, xmiRepository,
                new ModelSessionManager(new ModelSessionProperties()), new ModelMutationService(dynamicModelService),
                new ModelProjectionService(), new ModelFileStorageService(storage));
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de ModelDocumentService. Entradas: metamodelId: identificador que delimita el recurso objetivo; rootClassId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse create(String metamodelId, String rootClassId) {
        RegisteredMetamodel registered = registry.find(metamodelId);
        LoadedMetamodel loaded = loadMetamodel(registered);
        EObject root = mutations.createObject(loaded, rootClassId);
        String modelId = sessionManager.nextModelId();
        ModelSession session = new ModelSession(modelId, registered.id(), modelId + ".xmi", loaded, root);
        mutations.registerObject(session, root);
        sessionManager.register(session);
        return sessionManager.withSession(modelId, projection::full);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de ModelDocumentService. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse find(String modelId) {
        return sessionManager.withSession(modelId, projection::full);
    }

    /** Descripción: Ejecuta la operación «addElement» correspondiente a la responsabilidad de ModelDocumentService. Entradas: modelId: identificador que delimita el recurso objetivo; containerId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse addElement(String modelId, String containerId, String referenceName, String classId) {
        return mutate(modelId, session -> mutations.addElement(session, containerId, referenceName, classId));
    }

    /** Descripción: Actualiza el estado gestionado por ModelDocumentService mediante la operación «setAttribute». Entradas: modelId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; attributeName: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse setAttribute(String modelId, String elementId, String attributeName, String value) {
        return mutate(modelId, session -> mutations.setAttribute(session, elementId, attributeName, value));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «clearAttribute» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo; attributeName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse clearAttribute(String modelId, String elementId, String attributeName) {
        return mutate(modelId, session -> mutations.clearAttribute(session, elementId, attributeName));
    }

    /** Descripción: Actualiza el estado gestionado por ModelDocumentService mediante la operación «setReference». Entradas: modelId: identificador que delimita el recurso objetivo; sourceId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse setReference(String modelId, String sourceId, String referenceName, String targetId) {
        return mutate(modelId, session -> mutations.setReference(session, sourceId, referenceName, targetId));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «removeReference» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo; sourceId: identificador que delimita el recurso objetivo; referenceName: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse removeReference(String modelId, String sourceId, String referenceName, String targetId) {
        return mutate(modelId, session -> mutations.removeReference(session, sourceId, referenceName, targetId));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «deleteElement» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo; elementId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse deleteElement(String modelId, String elementId) {
        return mutate(modelId, session -> mutations.deleteElement(session, elementId));
    }

    /** Elimina una selección de elementos mediante una única mutación atómica de la sesión. */
    /** Descripción: Elimina o libera el recurso gestionado por «deleteElements» de forma controlada. Entradas: modelId: identificador que delimita el recurso objetivo; elementIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse deleteElements(String modelId, List<String> elementIds) {
        return mutate(modelId, session -> mutations.deleteElements(session, elementIds));
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de ModelDocumentService. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelSnapshot snapshot(String modelId) {
        return sessionManager.withSession(modelId, session -> {
            List<String> ids = new ArrayList<>();
            ids.add(session.objectIds.get(session.rootObject));
            TreeIterator<EObject> contents = session.rootObject.eAllContents();
            while(contents.hasNext()) ids.add(session.objectIds.get(contents.next()));
            return new ModelSnapshot(EcoreUtil.copy(session.rootObject), List.copyOf(ids), session.nextElementNumber, session.dirty);
        });
    }

    /** Descripción: Ejecuta la operación «restore» correspondiente a la responsabilidad de ModelDocumentService. Entradas: modelId: identificador que delimita el recurso objetivo; snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse restore(String modelId, ModelSnapshot snapshot) {
        if(snapshot == null) throw new ModelDocumentException(Reason.INVALID_REQUEST, "Model snapshot must not be null");
        return sessionManager.withSession(modelId, session -> {
            session.rootObject = EcoreUtil.copy(snapshot.rootObject);
            session.objects.clear();
            session.objectIds.clear();
            List<EObject> objects = new ArrayList<>();
            objects.add(session.rootObject);
            TreeIterator<EObject> contents = session.rootObject.eAllContents();
            while(contents.hasNext()) objects.add(contents.next());
            if(objects.size() != snapshot.elementIds.size()) {
                throw new ModelDocumentException(Reason.STORAGE_ERROR, "Model snapshot structure is inconsistent");
            }
            for(int index = 0; index < objects.size(); index++) {
                EObject object = objects.get(index);
                String id = snapshot.elementIds.get(index);
                session.objects.put(id, object);
                session.objectIds.put(object, id);
            }
            session.nextElementNumber = snapshot.nextElementNumber;
            session.dirty = snapshot.dirty;
            return projection.full(session);
        });
    }

    /** Descripción: Actualiza el estado gestionado por ModelDocumentService mediante la operación «save». Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse save(String modelId) {
        return sessionManager.withSession(modelId, session -> {
            try {
                xmiRepository.save(session.loadedMetamodel, session.rootObject, files.resolveModelPath(session.fileName));
                session.dirty = false;
                return projection.full(session);
            } catch(RuntimeException exception) {
                throw new ModelDocumentException(Reason.STORAGE_ERROR, "Could not save model document: " + modelId, exception);
            }
        });
    }

    /** Descripción: Crea o prepara el resultado de «open» dentro de ModelDocumentService. Entradas: metamodelId: identificador que delimita el recurso objetivo; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ModelDocumentResponse open(String metamodelId, String fileName) {
        RegisteredMetamodel registered = registry.find(metamodelId);
        LoadedMetamodel loaded = loadMetamodel(registered);
        Path source = files.resolveExistingModelPath(fileName);
        EObject root;
        try {
            root = xmiRepository.load(loaded, source);
        } catch(RuntimeException exception) {
            throw new ModelDocumentException(Reason.INVALID_REQUEST, "Could not open model file: " + fileName, exception);
        }
        String modelId = sessionManager.nextModelId();
        ModelSession session = new ModelSession(modelId, registered.id(), fileName, loaded, root);
        mutations.registerObjectTree(session, root);
        session.dirty = false;
        sessionManager.register(session);
        return sessionManager.withSession(modelId, projection::full);
    }

    /** Descripción: Obtiene la información correspondiente a «listSavedModels» dentro de ModelDocumentService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<SavedModelSummary> listSavedModels() { return files.listSavedModels(); }
    /** Descripción: Obtiene la información correspondiente a «readSavedModel» dentro de ModelDocumentService. Entradas: fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] readSavedModel(String fileName) { return files.readSavedModel(fileName); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteSavedModel» de forma controlada. Entradas: fileName: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteSavedModel(String fileName) {
        files.deleteSavedModel(fileName);
        sessionManager.invalidateByFileName(fileName);
    }

    /** Descripción: Ejecuta la operación «mutate» correspondiente a la responsabilidad de ModelDocumentService. Entradas: modelId: identificador que delimita el recurso objetivo; mutation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelDocumentResponse mutate(String modelId, java.util.function.Consumer<ModelSession> mutation) {
        return sessionManager.withSession(modelId, session -> { mutation.accept(session); return projection.full(session); });
    }

    /** Descripción: Obtiene la información correspondiente a «loadMetamodel» dentro de ModelDocumentService. Entradas: registered: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private LoadedMetamodel loadMetamodel(RegisteredMetamodel registered) {
        Path path = storage.metamodels().resolve(registered.fileName()).toAbsolutePath().normalize();
        return metamodelLoader.load(path);
    }

    /** Copia opaca empleada por transacciones y undo/redo. */
    public static final class ModelSnapshot {
        private final EObject rootObject;
        private final List<String> elementIds;
        private final long nextElementNumber;
        private final boolean dirty;
        /** Descripción: Inicializa una instancia de ModelSnapshot con sus dependencias y datos de entrada. Entradas: rootObject: dato de entrada necesario para la operación; elementIds: dato de entrada necesario para la operación; nextElementNumber: dato de entrada necesario para la operación; dirty: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ModelSnapshot(EObject rootObject, List<String> elementIds, long nextElementNumber, boolean dirty) {
            this.rootObject = rootObject;
            this.elementIds = elementIds;
            this.nextElementNumber = nextElementNumber;
            this.dirty = dirty;
        }
    }
}
