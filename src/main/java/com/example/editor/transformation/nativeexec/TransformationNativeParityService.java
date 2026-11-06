package com.example.editor.transformation.nativeexec;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.export.TransformationExportRequest;
import com.example.editor.transformation.export.TransformationExportResult;
import com.example.editor.transformation.export.TransformationExportService;
import com.example.editor.transformation.parity.TransformationParityDifference;
import com.example.editor.transformation.parity.TransformationParityReport;
import com.example.editor.transformation.parity.TransformationParityRequest;
import com.example.editor.transformation.parity.TransformationParityService;
import com.example.editor.transformation.parity.TransformationParityStatus;

/** Orchestrates directional native export, isolated tool execution and reference-runtime parity verification. */
@Service
public class TransformationNativeParityService {
    private final TransformationExportService exports;
    private final TransformationNativeRuntimeExecutor executor;
    private final TransformationParityService parity;

    /** Descripción: Inicializa una instancia de TransformationNativeParityService con sus dependencias y datos de entrada. Entradas: exports: dato de entrada necesario para la operación; executor: dato de entrada necesario para la operación; parity: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeParityService(TransformationExportService exports,
            TransformationNativeRuntimeExecutor executor, TransformationParityService parity) {
        this.exports = exports;
        this.executor = executor;
        this.parity = parity;
    }

    /** Returns managed runtime descriptors without exposing server command lines. */
    /** Descripción: Procesa la operación «runtimes» coordinando las responsabilidades de TransformationNativeParityService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TransformationNativeRuntimeDescriptor> runtimes(){return executor.runtimes();}

    /** Diagnoses one managed runtime without exposing its server command or installation path. */
    /** Descripción: Ejecuta la operación «diagnose» correspondiente a la responsabilidad de TransformationNativeParityService. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeRuntimeDiagnostic diagnose(TransformationExportLanguage language) {
        return executor.diagnose(language);
    }

    /** Executes one explicit native direction and compares it with the same canonical direction. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationNativeParityService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationNativeParityReport execute(TransformationNativeParityRequest request) {
        if(request == null || request.transformation() == null || request.language() == null)
            throw new IllegalArgumentException("Transformation and native language are required");
        TransformationDefinitionDocument document = request.transformation();
        TransformationExecutionDirection direction = request.direction();

        if(direction == TransformationExecutionDirection.REVERSE
                && (document.kind() != TransformationKind.M2M || document.directionality() != TransformationDirectionality.BIDIRECTIONAL)) {
            return invalidDirection(document, request.language(), direction,
                    "Reverse native parity requires a bidirectional M2M transformation with explicit reverse rules");
        }
        if(!TransformationNativeRuntimeSupport.supportsDirection(request.language(), document.kind(), direction)) {
            TransformationNativeRuntimeReport runtime = new TransformationNativeRuntimeReport(request.language(), document.kind(), direction,
                    TransformationNativeRuntimeStatus.NOT_RUN, 0L, null,
                    List.of("Selected language does not provide independent native parity for " + direction));
            TransformationParityReport invalid = new TransformationParityReport(document.id(), request.language(), document.kind(), direction,
                    TransformationParityStatus.INVALID_EVIDENCE, false, false, "", "", List.of(),
                    List.of(new TransformationParityDifference("$.direction", supportedDirectionSummary(request.language()), direction.name())));
            return new TransformationNativeParityReport(document.id(), request.language(), document.kind(), runtime, invalid);
        }
        if(request.sourceModel() == null) {
            // Mismo criterio que TransformationParityService.verify: un modelo origen ausente es evidencia inválida, no una excepción.
            TransformationNativeRuntimeReport runtime = new TransformationNativeRuntimeReport(request.language(), document.kind(), direction,
                    TransformationNativeRuntimeStatus.NOT_RUN, 0L, null,
                    List.of("Native execution was not started because the source model is missing"));
            TransformationParityReport invalid = new TransformationParityReport(document.id(), request.language(), document.kind(), direction,
                    TransformationParityStatus.INVALID_EVIDENCE, false, false, "", "", List.of(),
                    List.of(new TransformationParityDifference("$.sourceModel", "present", "missing")));
            return new TransformationNativeParityReport(document.id(), request.language(), document.kind(), runtime, invalid);
        }

        TransformationExportResult export = exports.export(new TransformationExportRequest(document, request.language(), direction));
        if(!export.compatible()) {
            TransformationNativeRuntimeReport runtime = new TransformationNativeRuntimeReport(request.language(), document.kind(), direction,
                    TransformationNativeRuntimeStatus.NOT_RUN, 0L, null,
                    List.of("Native execution was not started because export compatibility is blocked"));
            TransformationParityReport blocked = new TransformationParityReport(document.id(), request.language(), document.kind(), direction,
                    TransformationParityStatus.EXPORT_BLOCKED, false, false, "", "", export.diagnostics(), List.of());
            return new TransformationNativeParityReport(document.id(), request.language(), document.kind(), runtime, blocked);
        }

        TransformationNativeRuntimeOutcome nativeResult = executor.execute(document, request.language(), direction, request.sourceModel(), export);
        if(nativeResult.report().status() != TransformationNativeRuntimeStatus.SUCCEEDED)
            return new TransformationNativeParityReport(document.id(), request.language(), document.kind(), nativeResult.report(), null);

        TransformationParityReport comparison = parity.verifyExported(new TransformationParityRequest(document,
                request.language(), direction, request.sourceModel(), nativeResult.targetModel(), nativeResult.artifacts()), export);
        return new TransformationNativeParityReport(document.id(), request.language(), document.kind(), nativeResult.report(), comparison);
    }

    /** Descripción: Ejecuta la operación «invalidDirection» correspondiente a la responsabilidad de TransformationNativeParityService. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationNativeParityReport invalidDirection(TransformationDefinitionDocument document,
            TransformationExportLanguage language, TransformationExecutionDirection direction, String message) {
        TransformationNativeRuntimeReport runtime = new TransformationNativeRuntimeReport(language, document.kind(), direction,
                TransformationNativeRuntimeStatus.NOT_RUN, 0L, null, List.of(message));
        TransformationParityReport invalid = new TransformationParityReport(document.id(), language, document.kind(), direction,
                TransformationParityStatus.INVALID_EVIDENCE, false, false, "", "", List.of(),
                List.of(new TransformationParityDifference("$.direction", "FORWARD or explicit bidirectional REVERSE", direction.name())));
        return new TransformationNativeParityReport(document.id(), language, document.kind(), runtime, invalid);
    }

    /** Descripción: Ejecuta la operación «supportedDirectionSummary» correspondiente a la responsabilidad de TransformationNativeParityService. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String supportedDirectionSummary(TransformationExportLanguage language) {
        List<String> directions = TransformationNativeRuntimeSupport.supportedDirections(language).stream().sorted().map(Enum::name).toList();
        return directions.isEmpty() ? "none" : String.join(", ", directions);
    }
}
