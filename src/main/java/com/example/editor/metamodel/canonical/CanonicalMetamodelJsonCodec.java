package com.example.editor.metamodel.canonical;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Lee y escribe exclusivamente el contrato interno actual del metamodelo. */
@Component
public class CanonicalMetamodelJsonCodec {
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;

    /** Descripción: Inicializa una instancia de CanonicalMetamodelJsonCodec con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de CanonicalMetamodelJsonCodec. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public MetaModel read(byte[] content) throws JacksonException {
        MetaModel metamodel = objectMapper.readValue(content, MetaModel.class);
        if (!MetaModel.CURRENT_SCHEMA_VERSION.equals(metamodel.schemaVersion())) {
            throw new IllegalArgumentException("Unsupported MetaModel schema version: " + metamodel.schemaVersion()
                    + ". HYDRA accepts only the current internal metamodel contract.");
        }
        return metamodel;
    }

    /** Descripción: Actualiza el estado gestionado por CanonicalMetamodelJsonCodec mediante la operación «write». Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] write(MetaModel metamodel) throws JacksonException {
        if (metamodel == null || !MetaModel.CURRENT_SCHEMA_VERSION.equals(metamodel.schemaVersion())) {
            throw new IllegalArgumentException("Only the current MetaModel schema can be persisted");
        }
        return canonicalWriter.writeValueAsBytes(metamodel);
    }
}
