package com.example.editor.validation.validation.persistence;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.config.StorageProperties;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.ValidationModelSummary;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonValidationModelRepository implements ValidationModelRepository {
    private final ValidationModelProperties properties;
    private final CanonicalDocumentStore<ValidationModel> store;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonValidationModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonValidationModelRepository(StorageProperties storageProperties, ValidationModelProperties properties, ObjectMapper objectMapper, CanonicalDocumentStoreFactory stores) {
        this.properties = properties;
        this.store = stores.create(new CanonicalDocumentStoreSpec<>("validation.model", storageProperties.validations(), properties.getFileSuffix(), properties.isUseEtag(), objectMapper, ValidationModel.class, ValidationModel::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonValidationModelRepository con sus dependencias y datos de entrada. Entradas: storageProperties: configuración que gobierna el comportamiento; properties: configuración que gobierna el comportamiento; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonValidationModelRepository(StorageProperties storageProperties, ValidationModelProperties properties, ObjectMapper objectMapper) {
        this(storageProperties, properties, objectMapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonValidationModelRepository. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<ValidationModel> create(ValidationModel model) { requireEnabled(); return store.create(model); }
    /** Descripción: Actualiza el estado gestionado por JsonValidationModelRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<ValidationModel> replace(String id, ValidationModel model, String expectedEtag) { requireEnabled(); return store.replace(id, model, expectedEtag); }
    /** Descripción: Obtiene la información correspondiente a «findById» dentro de JsonValidationModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<ValidationModel>> findById(String id) { requireEnabled(); return store.find(id); }
    /** Descripción: Obtiene la información correspondiente a «findAll» dentro de JsonValidationModelRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ValidationModelSummary> findAll() { requireEnabled(); return store.listIds().stream().map(id -> store.find(id).orElseThrow()).map(item -> new ValidationModelSummary(item.document().id(), item.document().metamodelId(), item.document().schemaVersion(), item.document().rules().size(), item.document().ruleSets().size(), item.etag())).toList(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String expectedEtag) { requireEnabled(); store.delete(id, expectedEtag); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) { store.deleteIfExists(id); }
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean exists(String id) { return store.exists(id); }
    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de JsonValidationModelRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String fileName(String id) { return store.fileName(id); }
    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() { if(!properties.isEnabled()) throw new IllegalStateException("Validation model persistence is disabled"); }
}
