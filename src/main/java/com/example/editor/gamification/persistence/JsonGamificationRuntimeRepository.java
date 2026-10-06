package com.example.editor.gamification.persistence;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.gamification.GamificationLedger;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.gamification.GamificationRuntimeState;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonGamificationRuntimeRepository implements GamificationRuntimeRepository {
    private final CanonicalDocumentStore<GamificationRuntimeState> states;
    private final CanonicalDocumentStore<GamificationLedger> ledgers;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonGamificationRuntimeRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonGamificationRuntimeRepository(GamificationProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        states = stores.create(new CanonicalDocumentStoreSpec<>("gamification.runtime.state", properties.getDirectory(), properties.getStateFileSuffix(), properties.isUseEtag(), mapper, GamificationRuntimeState.class, GamificationRuntimeState::metamodelId));
        ledgers = stores.create(new CanonicalDocumentStoreSpec<>("gamification.runtime.ledger", properties.getDirectory(), properties.getLedgerFileSuffix(), properties.isUseEtag(), mapper, GamificationLedger.class, GamificationLedger::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonGamificationRuntimeRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonGamificationRuntimeRepository(GamificationProperties properties, ObjectMapper mapper) {
        this(properties, mapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Obtiene la información correspondiente a «findState» dentro de JsonGamificationRuntimeRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<GamificationRuntimeState>> findState(String id) { return states.find(id); }
    /** Descripción: Obtiene la información correspondiente a «findLedger» dentro de JsonGamificationRuntimeRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<GamificationLedger>> findLedger(String id) { return ledgers.find(id); }
    /** Descripción: Actualiza el estado gestionado por JsonGamificationRuntimeRepository mediante la operación «saveState». Entradas: state: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<GamificationRuntimeState> saveState(GamificationRuntimeState state, String etag) { return states.exists(state.metamodelId()) ? states.replace(state.metamodelId(), state, etag) : states.create(state); }
    /** Descripción: Actualiza el estado gestionado por JsonGamificationRuntimeRepository mediante la operación «saveLedger». Entradas: ledger: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<GamificationLedger> saveLedger(GamificationLedger ledger, String etag) { return ledgers.exists(ledger.metamodelId()) ? ledgers.replace(ledger.metamodelId(), ledger, etag) : ledgers.create(ledger); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteState» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteState(String id, String etag) { states.delete(id, etag); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteLedger» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteLedger(String id, String etag) { ledgers.delete(id, etag); }
    /** Descripción: Ejecuta la operación «reset» correspondiente a la responsabilidad de JsonGamificationRuntimeRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void reset(String id) { states.deleteIfExists(id); ledgers.deleteIfExists(id); }
}
