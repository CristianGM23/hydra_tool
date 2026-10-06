package com.example.editor.integration.verification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.editor.integration.IntegrationTechnology;

/** Registro estricto de validadores tecnológicos. */
@Component
public class TechnologyProjectStructureValidatorRegistry {
    private final Map<IntegrationTechnology, TechnologyProjectStructureValidator> validators;

    /** Descripción: Inicializa una instancia de TechnologyProjectStructureValidatorRegistry con sus dependencias y datos de entrada. Entradas: values: colección de elementos que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyProjectStructureValidatorRegistry(List<TechnologyProjectStructureValidator> values){
        EnumMap<IntegrationTechnology, TechnologyProjectStructureValidator> result = new EnumMap<>(IntegrationTechnology.class);
        for(TechnologyProjectStructureValidator value : values){
            TechnologyProjectStructureValidator previous = result.put(value.technology(), value);
            if(previous != null){throw new IllegalStateException("Duplicate project validator for " + value.technology());}
        }
        validators = Map.copyOf(result);
    }

    /** Descripción: Evalúa si la tecnología dispone de una implementación registrada sin lanzar excepciones. Entradas: technology: tecnología que debe comprobarse. Salida: devuelve un indicador que expresa si existe una implementación registrada. */ public boolean supports(IntegrationTechnology technology){return technology != null && validators.containsKey(technology);}

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyProjectStructureValidator require(IntegrationTechnology technology){
        TechnologyProjectStructureValidator value = validators.get(technology);
        if(value == null){throw new IllegalArgumentException("No project validator registered for " + technology);}
        return value;
    }
}
