package com.example.editor.audit;

import java.io.BufferedReader;
import java.io.IOException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/**
 * Codec específico para JSONL de auditoría.
 *
 * La configuración global de HYDRA puede usar JSON indentado para documentos humanos,
 * pero JSONL exige exactamente un objeto JSON por línea. Este codec fuerza una
 * representación compacta y valida cada entrada de forma independiente.
 */
final class AuditJsonlRecordCodec {
    private final ObjectMapper mapper;
    private final ObjectWriter compactWriter;

    /** Descripción: Inicializa una instancia de AuditJsonlRecordCodec con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ AuditJsonlRecordCodec(ObjectMapper mapper) {
        this.mapper = mapper;
        this.compactWriter = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .without(SerializationFeature.INDENT_OUTPUT);
    }

    /** Descripción: Actualiza el estado gestionado por RecoveryResult mediante la operación «write». Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ byte[] write(AuditEntry entry) throws JacksonException {
        return compactWriter.writeValueAsBytes(entry);
    }

    /** Descripción: Actualiza el estado gestionado por RecoveryResult mediante la operación «writeString». Entradas: entry: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String writeString(AuditEntry entry) throws JacksonException {
        return compactWriter.writeValueAsString(entry);
    }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de RecoveryResult. Entradas: json: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ AuditEntry read(String json) throws JacksonException {
        return mapper.readValue(json, AuditEntry.class);
    }

    /** Descripción: Evalúa la condición «isCanonicalPartition» sin modificar el estado externo. Entradas: reader: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean isCanonicalPartition(BufferedReader reader) throws IOException {
        String line;
        while((line = reader.readLine()) != null) {
            if(line.isBlank()) continue;
            try {
                AuditEntry entry = read(line);
                if(entry == null) return false;
            } catch(RuntimeException exception) {
                return false;
            }
        }
        return true;
    }

}
