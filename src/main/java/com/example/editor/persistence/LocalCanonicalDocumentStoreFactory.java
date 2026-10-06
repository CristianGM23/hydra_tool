package com.example.editor.persistence;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.editor.scope.ResourceScope;
import com.example.editor.persistence.evolution.HydraSchemaEvolutionService;
import com.example.editor.validation.persistence.AtomicJsonDocumentStore;

/** Default local, atomic JSON implementation of the canonical persistence SPI. */
@Component
public class LocalCanonicalDocumentStoreFactory implements CanonicalDocumentStoreFactory {
    private final List<CanonicalDocumentMutationObserver> mutationObservers;
    private final HydraSchemaEvolutionService schemaEvolution;

    /** Descripción: Inicializa una instancia de LocalCanonicalDocumentStoreFactory con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public LocalCanonicalDocumentStoreFactory() {
        this(List.of(), HydraSchemaEvolutionService.none());
    }

    /** Constructor de compatibilidad para pruebas unitarias focales. */
    /** Descripción: Inicializa una instancia de LocalCanonicalDocumentStoreFactory con sus dependencias y datos de entrada. Entradas: mutationObservers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public LocalCanonicalDocumentStoreFactory(List<CanonicalDocumentMutationObserver> mutationObservers) {
        this(mutationObservers, HydraSchemaEvolutionService.none());
    }

    @Autowired
    /** Descripción: Inicializa una instancia de LocalCanonicalDocumentStoreFactory con sus dependencias y datos de entrada. Entradas: mutationObservers: dato de entrada necesario para la operación; schemaEvolution: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public LocalCanonicalDocumentStoreFactory(List<CanonicalDocumentMutationObserver> mutationObservers, HydraSchemaEvolutionService schemaEvolution) {
        this.mutationObservers = mutationObservers == null ? List.of() : List.copyOf(mutationObservers);
        this.schemaEvolution = schemaEvolution == null ? HydraSchemaEvolutionService.none() : schemaEvolution;
    }

    @Override
    /** Descripción: Crea o prepara el resultado de «create» dentro de LocalCanonicalDocumentStoreFactory. Entradas: spec: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public <T> CanonicalDocumentStore<T> create(CanonicalDocumentStoreSpec<T> spec) {
        return create(spec, ResourceScope.local());
    }

    @Override
    /** Descripción: Crea o prepara el resultado de «create» dentro de LocalCanonicalDocumentStoreFactory. Entradas: spec: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public <T> CanonicalDocumentStore<T> create(CanonicalDocumentStoreSpec<T> spec, ResourceScope scope) {
        ResourceScope effective = scope == null ? ResourceScope.local() : scope;
        return new AtomicJsonDocumentStore<>(effective.localDirectory(spec.localDirectory()), spec.suffix(), spec.useEtag(), spec.objectMapper(),
                spec.type(), spec.idExtractor(), spec.namespace(), effective, this::notifyObservers, schemaEvolution);
    }

    /** Descripción: Ejecuta la operación «notifyObservers» correspondiente a la responsabilidad de LocalCanonicalDocumentStoreFactory. Entradas: mutation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void notifyObservers(CanonicalDocumentMutation mutation) {
        for(CanonicalDocumentMutationObserver observer : mutationObservers) {
            try { observer.onMutation(mutation); }
            catch(RuntimeException ignored) { /* A transversal observer never changes persistence semantics. */ }
        }
    }
}
