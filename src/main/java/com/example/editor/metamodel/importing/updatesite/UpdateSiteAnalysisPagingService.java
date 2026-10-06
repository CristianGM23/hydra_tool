package com.example.editor.metamodel.importing.updatesite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Produces compact responses and bounded pages without recomputing the p2 analysis. */
@Service
public class UpdateSiteAnalysisPagingService {
    private final UpdateSiteAnalysisCache cache;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de UpdateSiteAnalysisPagingService con sus dependencias y datos de entrada. Entradas: cache: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAnalysisPagingService(UpdateSiteAnalysisCache cache, ObjectMapper mapper) {
        this.cache = cache; this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Ejecuta la operación «compact» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: analysisId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteCompactAnalysis compact(String analysisId) {
        UpdateSiteImportAnalysis source = find(analysisId);
        Map<String, Integer> sizes = new LinkedHashMap<>();
        sizes.put("evidence", source.provenance().size()); sizes.put("logs", source.logs().size());
        sizes.put("files", source.fileOutcomes().size()); sizes.put("grammarInspections", source.grammarInspections().size());
        UpdateSiteImportAnalysis compact = new UpdateSiteImportAnalysis(source.analysisId(), source.generatedAt(),
                source.repository(), source.candidates(), source.selectedCandidateId(), source.metamodelPreview(),
                List.of(), source.recoveredLayers(), source.importable(), source.diagnostics(), source.dashboard(),
                List.of(), List.of(), List.of(), source.reconciliation(), source.releaseComparison(),
                source.semanticRecovery(), source.performance());
        long estimated = sumBytes(bytes(compact), estimatedBytes(source.provenance()),
                estimatedBytes(source.logs()), estimatedBytes(source.fileOutcomes()),
                estimatedBytes(source.grammarInspections()));
        return new UpdateSiteCompactAnalysis(compact, sizes, estimated);
    }

    /** Descripción: Ejecuta la operación «evidence» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: analysisId: identificador que delimita el recurso objetivo; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; query: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisPage<UpdateSiteElementProvenance> evidence(String analysisId, int page,
            int size, String query, String confidence) {
        UpdateSiteImportAnalysis source = find(analysisId);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedConfidence = confidence == null ? "ALL" : confidence.trim().toUpperCase(Locale.ROOT);
        Predicate<UpdateSiteElementProvenance> filter = item -> ("ALL".equals(normalizedConfidence)
                || item.confidence().name().equals(normalizedConfidence))
                && (normalizedQuery.isBlank() || (item.elementPath() + " " + item.origin() + " "
                        + item.sourcePath() + " " + item.originalElement() + " " + item.method())
                        .toLowerCase(Locale.ROOT).contains(normalizedQuery));
        List<UpdateSiteElementProvenance> filtered = source.provenance().stream().filter(filter).toList();
        return page(source.analysisId(), "evidence", page, size, filtered);
    }

    /** Descripción: Ejecuta la operación «logs» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: analysisId: identificador que delimita el recurso objetivo; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisPage<UpdateSiteImportLogEntry> logs(String analysisId, int page, int size) {
        UpdateSiteImportAnalysis source = find(analysisId);
        return page(source.analysisId(), "logs", page, size, source.logs());
    }

    /** Descripción: Ejecuta la operación «files» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: analysisId: identificador que delimita el recurso objetivo; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisPage<UpdateSiteFileOutcome> files(String analysisId, int page, int size) {
        UpdateSiteImportAnalysis source = find(analysisId);
        return page(source.analysisId(), "files", page, size, source.fileOutcomes());
    }

    /** Descripción: Ejecuta la operación «page» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: analysisId: identificador que delimita el recurso objetivo; collection: dato de entrada necesario para la operación; requestedPage: solicitud con los datos necesarios para ejecutar la operación; requestedSize: solicitud con los datos necesarios para ejecutar la operación; values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private <T> UpdateSiteAnalysisPage<T> page(String analysisId, String collection,
            int requestedPage, int requestedSize, List<T> values) {
        int size = Math.max(1, Math.min(500, requestedSize));
        int total = values.size(); int totalPages = total == 0 ? 0 : (int)Math.ceil(total / (double)size);
        int page = totalPages == 0 ? 0 : Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = Math.min(total, page * size); int to = Math.min(total, from + size);
        List<T> items = values.subList(from, to);
        return new UpdateSiteAnalysisPage<>(analysisId, collection, page, size, total, totalPages,
                bytes(items), items);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de UpdateSiteAnalysisPagingService. Entradas: analysisId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private UpdateSiteImportAnalysis find(String analysisId) {
        return cache.findByAnalysisId(analysisId).orElseThrow(() -> new IllegalArgumentException(
                "Analysis is no longer available in the bounded cache; run the analysis again"));
    }

    /** Descripción: Ejecuta la operación «estimatedBytes» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private long estimatedBytes(List<?> values) {
        long total = 2L;
        for(Object value : values) total = sumBytes(total, bytes(value), 1L);
        return total;
    }

    /** Descripción: Ejecuta la operación «sumBytes» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private long sumBytes(long... values) {
        long total = 0L;
        for(long value : values) {
            long safe = Math.max(0L, value);
            if(Long.MAX_VALUE - total < safe) return Long.MAX_VALUE;
            total += safe;
        }
        return total;
    }

    /** Descripción: Ejecuta la operación «bytes» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private long bytes(Object value) {
        try {return writer.writeValueAsBytes(value).length;}
        catch(RuntimeException exception) {return 0L;}
    }
}
