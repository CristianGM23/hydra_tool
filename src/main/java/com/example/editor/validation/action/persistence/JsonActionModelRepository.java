package com.example.editor.validation.action.persistence;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.config.StorageProperties;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.ActionModelSummary;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonActionModelRepository implements ActionModelRepository {
    private final ActionModelProperties properties;
    private final CanonicalDocumentStore<ActionModel> store;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonActionModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonActionModelRepository(StorageProperties storageProperties, ActionModelProperties properties, ObjectMapper objectMapper, CanonicalDocumentStoreFactory stores) {
        this.properties = properties;
        this.store = stores.create(new CanonicalDocumentStoreSpec<>("validation.action", storageProperties.validations(), properties.getFileSuffix(), properties.isUseEtag(), objectMapper, ActionModel.class, ActionModel::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonActionModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonActionModelRepository(StorageProperties storageProperties, ActionModelProperties properties, ObjectMapper objectMapper) {
        this(storageProperties, properties, objectMapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonActionModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<ActionModel> create(ActionModel model) { requireEnabled(); return store.create(model); }
    /** Descripción: Actualiza el estado gestionado por JsonActionModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<ActionModel> replace(String id, ActionModel model, String expectedEtag) { requireEnabled(); return store.replace(id, model, expectedEtag); }
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de JsonActionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<ActionModel>> findById(String id) { requireEnabled(); return store.find(id); }
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de JsonActionModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ActionModelSummary> findAll() { requireEnabled(); return store.listIds().stream().map(id -> store.find(id).orElseThrow()).map(item -> new ActionModelSummary(item.document().id(), item.document().metamodelId(), item.document().schemaVersion(), item.document().actions().size(), item.etag())).toList(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String expectedEtag) { requireEnabled(); store.delete(id, expectedEtag); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) { store.deleteIfExists(id); }
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) { return store.exists(id); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de JsonActionModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String fileName(String id) { return store.fileName(id); }
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new IllegalStateException("Action model persistence is disabled"); }
}
