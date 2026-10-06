package com.example.editor.transformation.parity;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.export.TransformationExportRequest;
import com.example.editor.transformation.export.TransformationExportResult;
import com.example.editor.transformation.export.TransformationExportService;
import com.example.editor.transformation.nativeexec.TransformationNativeRuntimeSupport;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.text.TransformationTextGenerationService;

/** Verifica paridad real a partir de la evidencia producida por un runtime externo. */
@Service
public class TransformationParityService {
    private final TransformationExportService exports;
    private final TransformationRuntimeService runtime;
    private final TransformationTextGenerationService textGeneration;
    private final TransformationParityComparator comparator;

    /** Descripción: Inicializa una instancia de TransformationParityService con sus dependencias y datos de entrada. Entradas: exports: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; textGeneration: dato de entrada necesario para la operación; comparator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationParityService(TransformationExportService exports, TransformationRuntimeService runtime,
            TransformationTextGenerationService textGeneration, TransformationParityComparator comparator) {
        this.exports = exports;
        this.runtime = runtime;
        this.textGeneration = textGeneration;
        this.comparator = comparator;
    }

    /** Ejecuta la referencia canónica y la contrasta con evidencia ya ejecutada por la tecnología externa. */
    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationParityReport verify(TransformationParityRequest request) {
        if(request == null || request.transformation() == null || request.language() == null)
            throw new IllegalArgumentException("Transformation and native export language are required");
        TransformationDefinitionDocument document = request.transformation();
        if(!TransformationNativeRuntimeSupport.supportsIndependentParity(request.language(), document.kind()))
            return invalid(document, request.language(), request.direction(), "$.language", TransformationNativeRuntimeSupport.nativeParityLanguageSummary(document.kind()), request.language().name());
        if(request.sourceModel() == null)
            return invalid(document, request.language(), request.direction(), "$.sourceModel", "present", "missing");

        TransformationExportResult export = exports.export(new TransformationExportRequest(document, request.language(), request.direction()));
        return verifyExported(request, export);
    }

    /** Reuses an already checked/exported native projection so automatic execution does not export twice. */
    /** Descripción: Valida las condiciones necesarias para «verifyExported» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; export: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationParityReport verifyExported(TransformationParityRequest request, TransformationExportResult export) {
        if(request == null || request.transformation() == null || request.language() == null)
            throw new IllegalArgumentException("Transformation and native export language are required");
        TransformationDefinitionDocument document = request.transformation();
        if(!TransformationNativeRuntimeSupport.supportsIndependentParity(request.language(), document.kind()))
            return invalid(document, request.language(), request.direction(), "$.language", TransformationNativeRuntimeSupport.nativeParityLanguageSummary(document.kind()), request.language().name());
        if(request.sourceModel() == null)
            return invalid(document, request.language(), request.direction(), "$.sourceModel", "present", "missing");
        if(export == null || export.language() != request.language() || !document.id().equals(export.transformationId()))
            return invalid(document, request.language(), request.direction(), "$.export", "matching native export", "missing or mismatched");
        if(!export.compatible()) {
            return new TransformationParityReport(document.id(), request.language(), document.kind(), request.direction(),
                    TransformationParityStatus.EXPORT_BLOCKED, false, false, "", "", export.diagnostics(), List.of());
        }

        TransformationParityComparator.Comparison comparison;
        if(document.kind() == TransformationKind.M2M) {
            if(request.nativeTargetModel() == null)
                return invalid(document, request.language(), request.direction(), "$.nativeTargetModel", "present for M2M parity", "missing", export);
            comparison = comparator.compareModels(runtime.executeDraft(document, request.sourceModel(), request.direction()).targetModel(), request.nativeTargetModel());
        } else if(document.kind() == TransformationKind.M2T) {
            comparison = comparator.compareArtifacts(textGeneration.preview(document, request.sourceModel()).artifacts(), request.nativeArtifacts());
        } else {
            return invalid(document, request.language(), request.direction(), "$.transformation.kind", "M2M or M2T", document.kind().name(), export);
        }
        return new TransformationParityReport(document.id(), request.language(), document.kind(), request.direction(),
                comparison.matched() ? TransformationParityStatus.MATCHED : TransformationParityStatus.MISMATCHED,
                true, comparison.matched(), comparison.canonicalFingerprint(), comparison.nativeFingerprint(), export.diagnostics(), comparison.differences());
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de TransformationParityService. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationParityReport invalid(TransformationDefinitionDocument document, TransformationExportLanguage language,
            com.example.editor.transformation.core.TransformationExecutionDirection direction, String path, String expected, String actual) {
        return invalid(document, language, direction, path, expected, actual, null);
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de TransformationParityService. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación; export: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationParityReport invalid(TransformationDefinitionDocument document, TransformationExportLanguage language,
            com.example.editor.transformation.core.TransformationExecutionDirection direction, String path, String expected, String actual, TransformationExportResult export) {
        return new TransformationParityReport(document.id(), language, document.kind(), direction, TransformationParityStatus.INVALID_EVIDENCE,
                export != null && export.compatible(), false, "", "", export == null ? List.of() : export.diagnostics(),
                List.of(new TransformationParityDifference(path, expected, actual)));
    }
}
