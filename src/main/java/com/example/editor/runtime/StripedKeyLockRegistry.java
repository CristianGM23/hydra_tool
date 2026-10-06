package com.example.editor.runtime;

import java.util.concurrent.locks.ReentrantLock;

/** Registro de locks de memoria constante. Evita mapas de locks que crecen para siempre por identificador. */
public final class StripedKeyLockRegistry {
    private final ReentrantLock[] stripes;
    /** Descripción: Inicializa una instancia de StripedKeyLockRegistry con sus dependencias y datos de entrada. Entradas: stripeCount: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StripedKeyLockRegistry(int stripeCount){
        int size = Math.max(16, stripeCount); stripes = new ReentrantLock[size];
        for(int index=0; index<size; index++){stripes[index] = new ReentrantLock();}
    }
    /** Descripción: Ejecuta la operación «lockFor» correspondiente a la responsabilidad de StripedKeyLockRegistry. Entradas: key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ReentrantLock lockFor(String key){int hash = key == null ? 0 : key.hashCode(); return stripes[Math.floorMod(hash, stripes.length)];}
    /** Descripción: Ejecuta la operación «stripeCount» correspondiente a la responsabilidad de StripedKeyLockRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int stripeCount(){return stripes.length;}
}
