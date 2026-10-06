package com.example.editor.validation.runtime;

import org.springframework.stereotype.Service;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.persistence.OperationBehaviorModelRepository;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.projection.drawio.DrawioExportOptions;
import com.example.editor.projection.drawio.DrawioProjectionService;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelLoadResult;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelLoadResult;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelLoadResult;

/** Caso de uso que carga fuentes canónicas, compila, ejecuta y proyecta a draw.io. */
@Service
public class ValidationRuntimeApplicationService {
    private final CanonicalMetamodelApplicationService metamodelService;
    private final ExpressionModelApplicationService expressionService;
    private final ActionModelApplicationService actionService;
    private final ValidationModelApplicationService validationService;
    private final OperationBehaviorModelApplicationService behaviorService;
    private final OperationBehaviorModelRepository behaviorRepository;
    private final ValidationCompiler compiler;
    private final ValidationEngine engine;
    private final DrawioProjectionService drawio;
    private final ValidationRuntimeProperties properties;

    /** Descripción: Inicializa una instancia de ValidationRuntimeApplicationService con sus dependencias y datos de entrada. Entradas: metamodelService: servicio colaborador utilizado por la operación; expressionService: servicio colaborador utilizado por la operación; actionService: servicio colaborador utilizado por la operación; validationService: servicio colaborador utilizado por la operación; behaviorService: servicio colaborador utilizado por la operación; behaviorRepository: dato de entrada necesario para la operación; compiler: dato de entrada necesario para la operación; engine: dato de entrada necesario para la operación; drawio: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationRuntimeApplicationService(CanonicalMetamodelApplicationService metamodelService,
            ExpressionModelApplicationService expressionService, ActionModelApplicationService actionService,
            ValidationModelApplicationService validationService, OperationBehaviorModelApplicationService behaviorService,
            OperationBehaviorModelRepository behaviorRepository, ValidationCompiler compiler, ValidationEngine engine,
            DrawioProjectionService drawio, ValidationRuntimeProperties properties) {
        this.metamodelService = metamodelService;
        this.expressionService = expressionService;
        this.actionService = actionService;
        this.validationService = validationService;
        this.behaviorService = behaviorService;
        this.behaviorRepository = behaviorRepository;
        this.compiler = compiler;
        this.engine = engine;
        this.drawio = drawio;
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de ValidationRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationCompilationResult compile(String metamodelId) {
        requireEnabled();
        Sources sources = sources(metamodelId);
        return compiler.compile(sources.metamodel().metamodel(), sources.expressions().expressionModel(), sources.actions(),
                sources.validations().validationModel(), sources.versions());
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelValidationReport validate(String metamodelId, ValidationExecutionRequest request) {
        requireEnabled();
        if(request == null) throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.INVALID_REQUEST, "Validation execution request must not be null");
        if(!metamodelId.equals(request.model().metamodelId())) throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.INVALID_REQUEST, "Model document metamodelId must match the requested metamodel");
        Sources sources = sources(metamodelId);
        ValidationCompilationResult compilation = compiler.compile(sources.metamodel().metamodel(), sources.expressions().expressionModel(), sources.actions(),
                sources.validations().validationModel(), sources.versions());
        if(compilation.hasErrors() && !properties.isAllowPartialPlanExecution()) {
            throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.NOT_EXECUTABLE,
                    "Validation plan contains compilation errors and partial execution is disabled");
        }
        try {
            return engine.validate(compilation.plan(), sources.metamodel().metamodel(), sources.expressions().expressionModel(), sources.behaviors(), request);
        } catch(IllegalArgumentException exception) {
            throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.INVALID_REQUEST, exception.getMessage(), exception);
        } catch(RuntimeException exception) {
            throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.EXECUTION_FAILED, exception.getMessage(), exception);
        }
    }

    /** Descripción: Ejecuta la operación «exportDefinitionDrawio» correspondiente a la responsabilidad de ValidationRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String exportDefinitionDrawio(String metamodelId) {
        requireDrawio();
        return drawio.export(ValidationModelDrawioExporter.ARTIFACT_TYPE, validationService.find(metamodelId).validationModel(), new DrawioExportOptions("Validation definitions", true));
    }
    /** Descripción: Ejecuta la operación «exportPlanDrawio» correspondiente a la responsabilidad de ValidationRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public String exportPlanDrawio(String metamodelId) {
        requireDrawio();
        return drawio.export(ValidationPlanDrawioExporter.ARTIFACT_TYPE, compile(metamodelId).plan(), new DrawioExportOptions("Executable validation plan", true));
    }
    /** Descripción: Ejecuta la operación «exportReportDrawio» correspondiente a la responsabilidad de ValidationRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String exportReportDrawio(String metamodelId, ValidationExecutionRequest request) {
        requireDrawio();
        return drawio.export(ValidationReportDrawioExporter.ARTIFACT_TYPE, validate(metamodelId, request), new DrawioExportOptions("Validation report", true));
    }

    /** Descripción: Valida las condiciones necesarias para «requireEnabled» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireEnabled() {
        if(properties != null && !properties.isEnabled()) {
            throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.NOT_EXECUTABLE,
                    "Validation runtime is disabled by configuration");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «requireDrawio» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireDrawio() {
        requireEnabled();
        if(properties != null && !properties.isDrawioEnabled()) {
            throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.NOT_EXECUTABLE,
                    "Validation draw.io projection is disabled by configuration");
        }
    }

    /** Descripción: Ejecuta la operación «sources» correspondiente a la responsabilidad de ValidationRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Sources sources(String metamodelId) {
        if(metamodelId == null || metamodelId.isBlank()) throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.INVALID_REQUEST, "Metamodel id must not be blank");
        try {
            MetamodelLoadResult metamodel = metamodelService.findById(metamodelId);
            ExpressionModelLoadResult expressions = expressionService.find(metamodelId);
            ValidationModelLoadResult validations = validationService.find(metamodelId);
            ActionModel actions = null;
            String actionEtag = "";
            if(!validations.validationModel().actionModelId().isBlank()) {
                ActionModelLoadResult loadedActions = actionService.find(metamodelId);
                actions = loadedActions.actionModel();
                actionEtag = loadedActions.etag() == null ? "" : loadedActions.etag();
            }
            OperationBehaviorModel behaviors = behaviorService.findOrEmpty(metamodelId);
            String behaviorEtag = behaviorRepository.findById(metamodelId).map(item -> item.etag()).orElse("");
            ValidationPlanSourceVersions versions = new ValidationPlanSourceVersions(metamodel.etag(), expressions.etag(), actionEtag,
                    validations.etag(), behaviorEtag);
            return new Sources(metamodel, expressions, validations, actions, behaviors, versions);
        } catch(ValidationRuntimeApplicationException exception) { throw exception; }
        catch(RuntimeException exception) { throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.NOT_FOUND, exception.getMessage(), exception); }
    }

    private record Sources(MetamodelLoadResult metamodel, ExpressionModelLoadResult expressions, ValidationModelLoadResult validations,
            ActionModel actions, OperationBehaviorModel behaviors, ValidationPlanSourceVersions versions) { }
}
