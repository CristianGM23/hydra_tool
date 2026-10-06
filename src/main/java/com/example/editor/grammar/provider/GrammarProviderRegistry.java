package com.example.editor.grammar.provider;

import java.util.*;
import org.springframework.stereotype.Component;
import com.example.editor.grammar.core.GrammarTechnology;

@Component
public class GrammarProviderRegistry {
    private final Map<GrammarTechnology, GrammarProvider> providers;
    /** Descripción: Inicializa una instancia de GrammarProviderRegistry con sus dependencias y datos de entrada. Entradas: providers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarProviderRegistry(List<GrammarProvider> providers) {
        Map<GrammarTechnology, GrammarProvider> indexed = new EnumMap<>(GrammarTechnology.class);
        for (GrammarProvider provider : providers) {
            GrammarProvider previous = indexed.put(provider.descriptor().technology(), provider);
            if (previous != null) throw new IllegalStateException("Duplicate grammar provider for " + provider.descriptor().technology());
        }
        this.providers = Map.copyOf(indexed);
    }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarProvider require(GrammarTechnology technology) {
        GrammarProvider provider = providers.get(technology);
        if (provider == null) throw new IllegalStateException("Grammar provider is unavailable: " + technology);
        return provider;
    }
    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de GrammarProviderRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<GrammarProviderDescriptor> descriptors() { return providers.values().stream().map(GrammarProvider::descriptor).sorted(Comparator.comparing(GrammarProviderDescriptor::id)).toList(); }
}
