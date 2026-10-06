package com.example.editor.event.flow.runtime.persistence;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.event.flow.EventFlowProperties;
import com.example.editor.event.flow.runtime.EventFlowRuntimeSnapshot;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonEventFlowRuntimeSnapshotRepository implements EventFlowRuntimeSnapshotRepository {
    private final CanonicalDocumentStore<EventFlowRuntimeSnapshot> store;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonEventFlowRuntimeSnapshotRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonEventFlowRuntimeSnapshotRepository(EventFlowProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        store = stores.create(new CanonicalDocumentStoreSpec<>("event.flow.runtime", properties.getDirectory(), properties.getRuntimeFileSuffix(), properties.isUseEtag(), mapper, EventFlowRuntimeSnapshot.class, EventFlowRuntimeSnapshot::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonEventFlowRuntimeSnapshotRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonEventFlowRuntimeSnapshotRepository(EventFlowProperties properties, ObjectMapper mapper) {
        this(properties, mapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de JsonEventFlowRuntimeSnapshotRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<EventFlowRuntimeSnapshot>> find(String id) { return store.find(id); }
    /** Descripción: Actualiza el estado gestionado por JsonEventFlowRuntimeSnapshotRepository mediante la operación «save». Entradas: snapshot: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<EventFlowRuntimeSnapshot> save(EventFlowRuntimeSnapshot snapshot, String etag) {
        return store.exists(snapshot.metamodelId()) ? store.replace(snapshot.metamodelId(), snapshot, etag) : store.create(snapshot);
    }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) { store.deleteIfExists(id); }
}
