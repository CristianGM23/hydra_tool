package com.example.editor.concurrent;

import java.util.Objects;

/**
 * Conjunto fijo de locks por hash de recurso. Evita mapas de locks que crecen
 * indefinidamente manteniendo exclusión suficientemente granular.
 */
public final class ResourceStripedLock {
    private final Object[] stripes;
    /** Descripción: Inicializa una instancia de ResourceStripedLock con sus dependencias y datos de entrada. Entradas: stripeCount: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ResourceStripedLock(int stripeCount) {
        if(stripeCount < 2) throw new IllegalArgumentException("stripeCount must be at least 2");
        int size=1; while(size<stripeCount) size<<=1;
        stripes=new Object[size]; for(int i=0;i<size;i++) stripes[i]=new Object();
    }
    /** Descripción: Ejecuta la operación «lockFor» correspondiente a la responsabilidad de ResourceStripedLock. Entradas: resourceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Object lockFor(String resourceId) {
        int hash=spread(Objects.requireNonNull(resourceId,"resourceId").hashCode());
        return stripes[hash & (stripes.length-1)];
    }
    /** Descripción: Ejecuta la operación «stripeCount» correspondiente a la responsabilidad de ResourceStripedLock. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int stripeCount(){return stripes.length;}
    /** Descripción: Ejecuta la operación «spread» correspondiente a la responsabilidad de ResourceStripedLock. Entradas: hash: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int spread(int hash){return hash^(hash>>>16);}
}
