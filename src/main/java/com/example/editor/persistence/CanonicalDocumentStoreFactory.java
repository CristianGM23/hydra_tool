package com.example.editor.persistence;

import com.example.editor.scope.ResourceScope;

/**
 * Creates backend-neutral typed stores. The local JSON implementation is the default
 * provider; a future remote provider can implement this SPI without changing domains.
 */
public interface CanonicalDocumentStoreFactory {
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalDocumentStoreFactory. Entradas: spec: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ <T> CanonicalDocumentStore<T> create(CanonicalDocumentStoreSpec<T> spec);

    /** Crea un store aislado por scope sin acoplar los repositorios canónicos a la estructura de workspaces. */
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalDocumentStoreFactory. Entradas: spec: dato de entrada necesario para la operación; scope: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ default <T> CanonicalDocumentStore<T> create(CanonicalDocumentStoreSpec<T> spec, ResourceScope scope) {
        if(scope == null || scope.isLocal()){return create(spec);}
        throw new UnsupportedOperationException("This canonical document store factory does not support explicit resource scopes");
    }
}
