package com.example.editor.event.flow.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonEventFlowModelRepository implements EventFlowModelRepository {
    private final EventFlowProperties properties;
    private final CanonicalDocumentStore<EventFlowModel> store;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonEventFlowModelRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonEventFlowModelRepository(EventFlowProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        this.properties = properties;
        this.store = stores.create(new CanonicalDocumentStoreSpec<>("event.flow.model", properties.getDirectory(), properties.getFileSuffix(), properties.isUseEtag(), mapper, EventFlowModel.class, EventFlowModel::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonEventFlowModelRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonEventFlowModelRepository(EventFlowProperties properties, ObjectMapper mapper) {
        this(properties, mapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonEventFlowModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<EventFlowModel> create(EventFlowModel model) { requireEnabled(); return store.create(model); }
    /** Descripción: Actualiza el estado gestionado por JsonEventFlowModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<EventFlowModel> replace(String id, EventFlowModel model, String etag) { requireEnabled(); return store.replace(id, model, etag); }
    /** Descripción: Obtiene la información correspondiente a «findByMetamodelId» dentro de JsonEventFlowModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<EventFlowModel>> findByMetamodelId(String id) { requireEnabled(); return store.find(id); }
    /** Descripción: Obtiene la información correspondiente a «listMetamodelIds» dentro de JsonEventFlowModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> listMetamodelIds() { requireEnabled(); return store.listIds(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String etag) { requireEnabled(); store.delete(id, etag); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) { store.deleteIfExists(id); }
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) { return store.exists(id); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de JsonEventFlowModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String fileName(String id) { return store.fileName(id); }
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new IllegalStateException("Event-flow persistence is disabled"); }
}
