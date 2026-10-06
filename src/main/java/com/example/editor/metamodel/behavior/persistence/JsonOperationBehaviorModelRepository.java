package com.example.editor.metamodel.behavior.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.behavior.OperationBehaviorModelProperties;
import com.example.editor.metamodel.behavior.OperationBehaviorModelSummary;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;

/** Persistencia JSON atómica de OperationBehaviorModel 1.0. */
@Repository
public class JsonOperationBehaviorModelRepository implements OperationBehaviorModelRepository {
    private final OperationBehaviorModelProperties properties;
    private final CanonicalDocumentStore<OperationBehaviorModel> store;

    @Autowired
    /** Descripción: Inicializa una instancia de JsonOperationBehaviorModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonOperationBehaviorModelRepository(StorageProperties storageProperties, OperationBehaviorModelProperties properties, ObjectMapper objectMapper, CanonicalDocumentStoreFactory stores) {
        this.properties = properties;
        this.store = stores.create(new CanonicalDocumentStoreSpec<>("metamodel.operation.behavior", storageProperties.validations(), properties.getFileSuffix(), properties.isUseEtag(), objectMapper, OperationBehaviorModel.class, OperationBehaviorModel::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonOperationBehaviorModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonOperationBehaviorModelRepository(StorageProperties storageProperties, OperationBehaviorModelProperties properties, ObjectMapper objectMapper) {
        this(storageProperties, properties, objectMapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonOperationBehaviorModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<OperationBehaviorModel> create(OperationBehaviorModel model) { requireEnabled(); return store.create(model); }
    /** Descripción: Actualiza el estado gestionado por JsonOperationBehaviorModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<OperationBehaviorModel> replace(String id, OperationBehaviorModel model, String expectedEtag) { requireEnabled(); return store.replace(id, model, expectedEtag); }
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de JsonOperationBehaviorModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<OperationBehaviorModel>> findById(String id) { requireEnabled(); return store.find(id); }
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de JsonOperationBehaviorModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<OperationBehaviorModelSummary> findAll() { requireEnabled(); return store.listIds().stream().map(id -> store.find(id).orElseThrow()).map(item -> new OperationBehaviorModelSummary(item.document().metamodelId(), item.document().schemaVersion(), item.document().bindings().size(), item.etag())).toList(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String expectedEtag) { requireEnabled(); store.delete(id, expectedEtag); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) { store.deleteIfExists(id); }
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) { return store.exists(id); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de JsonOperationBehaviorModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String fileName(String id) { return store.fileName(id); }
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new IllegalStateException("Operation behavior persistence is disabled"); }
}
