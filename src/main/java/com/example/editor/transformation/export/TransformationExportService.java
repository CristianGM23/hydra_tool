package com.example.editor.transformation.export;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;

/** Orquesta exportación de drafts canónicos y garantiza check estático previo. */
@Service
public class TransformationExportService {
    private final TransformationLanguageAdapterRegistry registry;
    private final TransformationDefinitionChecker checker;
    private final CanonicalMetamodelApplicationService metamodels;

    /** Descripción: Inicializa el servicio de exportación. Entradas: registry: adapters; checker: validador; metamodels: catálogo semántico. Salida: instancia lista para exportar. */
    /** Descripción: Inicializa una instancia de TransformationExportService con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación; checker: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationExportService(TransformationLanguageAdapterRegistry registry, TransformationDefinitionChecker checker,
            CanonicalMetamodelApplicationService metamodels) {
        this.registry = registry; this.checker = checker; this.metamodels = metamodels;
    }

    /** Descripción: Exporta una dirección explícita sin inferir jamás la inversa de una regla. Entradas: request: definición, lenguaje y dirección. Salida: export compatible o diagnóstico bloqueante. */
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de TransformationExportService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExportResult export(TransformationExportRequest request) {
        if(request == null || request.transformation() == null || request.language() == null)
            throw new IllegalArgumentException("Transformation and export language are required");
        TransformationDefinitionDocument document = request.transformation();
        TransformationExecutionDirection direction = request.direction();
        if(document.kind() != TransformationKind.M2M && direction != TransformationExecutionDirection.FORWARD)
            return TransformationExportDiagnosticEnricher.enrich(blocked(document, request.language(), "DIRECTION_NOT_SUPPORTED", "$.direction",
                    "Reverse export is only meaningful for M2M transformations.", "Use FORWARD for M2T/T2M."), document, null, null, direction);
        if(document.kind() == TransformationKind.M2M && direction == TransformationExecutionDirection.REVERSE
                && document.directionality() != TransformationDirectionality.BIDIRECTIONAL)
            return TransformationExportDiagnosticEnricher.enrich(blocked(document, request.language(), "REVERSE_REQUIRES_BIDIRECTIONAL", "$.directionality",
                    "Reverse export requires a bidirectional transformation.", "Enable bidirectional mode and define explicit reverse rules."), document, null, null, direction);

        MetaModel canonicalSource = document.kind() == TransformationKind.T2M ? null
                : load(document.sources().isEmpty() ? "" : document.sources().getFirst().metamodelId());
        MetaModel canonicalTarget = document.kind() == TransformationKind.M2M
                ? load(document.targets().isEmpty() ? "" : document.targets().getFirst().metamodelId()) : null;
        var report = checker.check(document, canonicalSource == null ? List.of() : List.of(canonicalSource),
                canonicalTarget == null ? List.of() : List.of(canonicalTarget));
        if(!report.valid()) return new TransformationExportResult(document.id(), request.language(), TransformationExportFidelity.SUBSET, false,
                report.diagnostics().stream().filter(item -> item.severity().name().equals("ERROR"))
                        .map(item -> new TransformationExportDiagnostic("ERROR", "CANONICAL_" + item.code(), item.path(), item.message(), item.solution())).toList(), List.of());

        TransformationDefinitionDocument exportDocument = TransformationDirectionalExportView.of(document, direction);
        MetaModel source = direction == TransformationExecutionDirection.REVERSE ? canonicalTarget : canonicalSource;
        MetaModel target = direction == TransformationExecutionDirection.REVERSE ? canonicalSource : canonicalTarget;
        TransformationLanguageAdapter adapter = registry.require(request.language());
        if(!adapter.supportedKinds().contains(document.kind()))
            return TransformationExportDiagnosticEnricher.enrich(blocked(document, request.language(), "UNSUPPORTED_KIND", "$.kind",
                    "The selected adapter does not support " + document.kind() + " transformations.", "Choose a compatible export language."),
                    document, source, target, direction);
        return TransformationExportDiagnosticEnricher.enrich(adapter.export(exportDocument, source, target), exportDocument, source, target, direction);
    }

    /** Descripción: Construye una exportación bloqueada coherente. Entradas: document/language/code/path/message/solution. Salida: resultado incompatible fail-closed. */
    /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de TransformationExportService. Entradas: document: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación; solution: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationExportResult blocked(TransformationDefinitionDocument document, TransformationExportLanguage language,
            String code, String path, String message, String solution) {
        return new TransformationExportResult(document.id(), language, TransformationExportFidelity.SUBSET, false,
                List.of(new TransformationExportDiagnostic("ERROR", code, path, message, solution)), List.of());
    }

    /** Descripción: Resuelve un metamodelo cuando el endpoint lo declara. Entradas: id: identificador canónico. Salida: metamodelo o null si no hay id. */
    /** Descripción: Obtiene la información correspondiente a «load» dentro de TransformationExportService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel load(String id) { return id == null || id.isBlank() ? null : metamodels.findById(id).metamodel(); }
}
