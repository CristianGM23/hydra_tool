package com.example.editor.metamodel.validation;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaModel;
import tools.jackson.databind.json.JsonMapper;

/** Calcula una huella estable de la versión semántica que fue validada. */
@Component
public class MetamodelValidationFingerprint {
    private final JsonMapper mapper;
    /** Descripción: Inicializa una instancia de MetamodelValidationFingerprint con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelValidationFingerprint(JsonMapper mapper){this.mapper = mapper;}
    /** Descripción: Ejecuta la operación «of» correspondiente a la responsabilidad de MetamodelValidationFingerprint. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String of(MetaModel metamodel){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(metamodel)));}
        catch(Exception exception){throw new IllegalStateException("Could not fingerprint canonical metamodel", exception);}
    }
}
