package com.example.editor.web;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import tools.jackson.core.JacksonException;

import com.example.editor.security.input.HydraInputValidationException;
import com.example.editor.audit.AuditRequestContext;
import com.example.editor.metamodel.registry.MetamodelRegistryException;
import com.example.editor.metamodel.registry.MetamodelRegistryException.Reason;
import com.example.editor.model.ModelDocumentException;
import com.example.editor.notation.NotationRegistryException;
import com.example.editor.diagram.DiagramStorageException;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.projection.MetamodelProjectionException;
import com.example.editor.validation.expression.ExpressionModelApplicationException;
import com.example.editor.validation.action.ActionModelApplicationException;
import com.example.editor.validation.validation.ValidationModelApplicationException;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationException;
import com.example.editor.operation.runtime.OperationRuntimeException;
import com.example.editor.event.EventModelApplicationException;
import com.example.editor.event.flow.EventFlowApplicationException;
import com.example.editor.gamification.GamificationApplicationException;
import com.example.editor.definition.EditorDefinitionApplicationException;
import com.example.editor.definition.runtime.session.EditorSessionApplicationException;
import com.example.editor.integration.IntegrationProfileApplicationException;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationException;
import com.example.editor.validation.quickfix.QuickFixRuntimeException;
import com.example.editor.project.ProjectException;
import com.example.editor.remote.storage.RemoteStorageException;
import com.example.editor.remote.auth.RemoteAuthException;
import com.example.editor.remote.auth.RemoteSecretRedactor;



/**
 * Convierte excepciones de la aplicación en respuestas HTTP JSON.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Descripción:
     * Convierte los errores del registro de metamodelos en códigos HTTP.
     *
     * Entrada:
     * @param exception contiene el error producido.
     *
     * Salida:
     * @return una respuesta con el código y el mensaje del error.
     */
    @ExceptionHandler(MetamodelRegistryException.class)
    /** Descripción: Procesa la operación «handleMetamodelRegistryException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleMetamodelRegistryException(MetamodelRegistryException exception) {
        HttpStatus status;
        Reason reason = exception.getReason();

        if (reason == Reason.INVALID_REQUEST) {
            status = HttpStatus.BAD_REQUEST;
        } else if (reason == Reason.NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else if (reason == Reason.CONFLICT) {
            status = HttpStatus.CONFLICT;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ApiError error = new ApiError(reason.name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Representa una respuesta de error enviada por la API.
     *
     * @param code contiene el código estable del error.
     * @param message contiene el mensaje explicativo en inglés.
     */
    public record ApiError(String code, String message, String field, String reason, String solution, java.util.List<String> suggestions, String technicalDetail, String impact, boolean retryable, java.util.List<HydraRecoveryAction> recoveryActions, String correlationId) {
        /** Descripción: Inicializa una instancia de ApiError con sus dependencias y datos de entrada. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ApiError(String code, String message) {
            this(code, message, ApiErrorAdvice.from(code, message));
        }

        /** Descripción: Inicializa una instancia de ApiError con sus dependencias y datos de entrada. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación; technicalDetail: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ApiError(String code, String message, String field, String technicalDetail) {
            this(code, message, ApiErrorAdvice.from(code, technicalDetail, field), technicalDetail);
        }

        /** Descripción: Inicializa una instancia de ApiError con sus dependencias y datos de entrada. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; advice: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private ApiError(String code, String message, ApiErrorAdvice.Details advice) {
            this(code, message, advice, message);
        }

        /** Descripción: Inicializa una instancia de ApiError con sus dependencias y datos de entrada. Entradas: code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; advice: explicación orientada al usuario; technicalDetail: detalle técnico separado. Salida: deja la instancia inicializada con impacto, recuperación conservadora y correlationId. */ private ApiError(String code, String message, ApiErrorAdvice.Details advice, String technicalDetail) {
            this(code, message, advice, technicalDetail, HydraRecoveryAdvice.from(code));
        }

        /** Descripción: Combina el consejo de validación y la política transversal de recuperación sin marcar reintentos de escritura como acciones automáticas seguras. Entradas: code: código estable; message: resumen; advice: explicación de campo; technicalDetail: evidencia técnica; recovery: política de recuperación. Salida: construye el error API completo. */ private ApiError(String code,String message,ApiErrorAdvice.Details advice,String technicalDetail,HydraRecoveryAdvice.Details recovery) {
            this(code, message, advice.field(), advice.reason(), advice.solution(), advice.suggestions(), technicalDetail == null ? "" : technicalDetail, recovery.impact(), recovery.retryable(), recovery.actions(), AuditRequestContext.correlationId());
        }
    }

    /**
     * Descripción:
     * Convierte los errores de documentos semánticos en respuestas HTTP.
     *
     * Entrada:
     * @param exception contiene el error producido.
     *
     * Salida:
     * @return una respuesta JSON con el estado correspondiente.
     */
    @ExceptionHandler(ModelDocumentException.class)
    /** Descripción: Procesa la operación «handleModelDocumentException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleModelDocumentException(ModelDocumentException exception) {
        HttpStatus status;

        if (exception.getReason() == ModelDocumentException.Reason.INVALID_REQUEST) {
            status = HttpStatus.BAD_REQUEST;
        } else if (exception.getReason() == ModelDocumentException.Reason.NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ApiError error = new ApiError(exception.getReason().name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Descripción:
     * Convierte los errores de notación en respuestas HTTP.
     *
     * Entrada:
     * @param exception contiene el error.
     *
     * Salida:
     * @return la respuesta HTTP correspondiente.
     */
    @ExceptionHandler(NotationRegistryException.class)
    /** Descripción: Procesa la operación «handleNotationRegistryException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleNotationRegistryException(NotationRegistryException exception) {
        HttpStatus status;

        if (exception.getReason() == NotationRegistryException.Reason.INVALID_REQUEST) {
            status = HttpStatus.BAD_REQUEST;
        } else if (exception.getReason() == NotationRegistryException.Reason.NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else if (exception.getReason() == NotationRegistryException.Reason.CONFLICT) {
            status = HttpStatus.CONFLICT;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ApiError error = new ApiError(exception.getReason().name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Descripción:
     * Convierte errores de diagramas en respuestas HTTP.
     *
     * Entrada:
     * @param exception contiene el error.
     *
     * Salida:
     * @return la respuesta HTTP correspondiente.
     */
    @ExceptionHandler(DiagramStorageException.class)
    /** Descripción: Procesa la operación «handleDiagramStorageException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleDiagramStorageException(DiagramStorageException exception) {
        HttpStatus status;

        if (exception.getReason() == DiagramStorageException.Reason.INVALID_REQUEST) {
            status = HttpStatus.BAD_REQUEST;
        } else if (exception.getReason() == DiagramStorageException.Reason.NOT_FOUND) {
            status = HttpStatus.NOT_FOUND;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ApiError error = new ApiError(exception.getReason().name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(MetamodelDesignException.class)
    /** Descripción: Procesa la operación «handleMetamodelDesignException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleMetamodelDesignException(MetamodelDesignException exception) {
        HttpStatus status;

        if (exception.getReason() == MetamodelDesignException.Reason.INVALID_REQUEST) {
            status = HttpStatus.BAD_REQUEST;
        } else if (exception.getReason() == MetamodelDesignException.Reason.CONFLICT) {
            status = HttpStatus.CONFLICT;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ApiError error = new ApiError(exception.getReason().name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }


    /**
     * Convierte los errores de la aplicación canónica en respuestas HTTP.
     */
    @ExceptionHandler(MetamodelApplicationException.class)
    /** Descripción: Procesa la operación «handleMetamodelApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleMetamodelApplicationException(MetamodelApplicationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION_ERROR -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        ApiError error = new ApiError(exception.getReason().name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Convierte los errores del subsistema de proyecciones en respuestas HTTP estables.
     */
    @ExceptionHandler(MetamodelProjectionException.class)
    /** Descripción: Procesa la operación «handleMetamodelProjectionException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleMetamodelProjectionException(MetamodelProjectionException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        ApiError error = new ApiError(exception.getReason().name(), exception.getMessage());
        return ResponseEntity.status(status).body(error);
    }


    /** Convierte los errores del subsistema canónico de expresiones. */
    @ExceptionHandler(ExpressionModelApplicationException.class)
    /** Descripción: Procesa la operación «handleExpressionModelApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleExpressionModelApplicationException(ExpressionModelApplicationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case NOT_EXECUTABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }


    /** Convierte los errores del subsistema canónico de acciones. */
    @ExceptionHandler(ActionModelApplicationException.class)
    /** Descripción: Procesa la operación «handleActionModelApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleActionModelApplicationException(ActionModelApplicationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores del subsistema canónico de validaciones. */
    @ExceptionHandler(ValidationModelApplicationException.class)
    /** Descripción: Procesa la operación «handleValidationModelApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleValidationModelApplicationException(ValidationModelApplicationException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores de los bindings de operaciones. */
    @ExceptionHandler(OperationBehaviorModelApplicationException.class)
    /** Descripción: Procesa la operación «handleOperationBehaviorModelApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleOperationBehaviorModelApplicationException(OperationBehaviorModelApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case VALIDATION_ERROR -> HttpStatus.UNPROCESSABLE_CONTENT;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores de ejecución de operaciones del editor. */
    @ExceptionHandler(OperationRuntimeException.class)
    /** Descripción: Procesa la operación «handleOperationRuntimeException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleOperationRuntimeException(OperationRuntimeException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_EXECUTABLE, EXECUTION_FAILED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNDO_NOT_AVAILABLE -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores de definiciones y bindings de eventos. */
    @ExceptionHandler(EventModelApplicationException.class)
    /** Descripción: Procesa la operación «handleEventModelApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleEventModelApplicationException(EventModelApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores del contrato y runtime de flujos. */
    @ExceptionHandler(EventFlowApplicationException.class)
    /** Descripción: Procesa la operación «handleEventFlowApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleEventFlowApplicationException(EventFlowApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case NOT_EXECUTABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case STORAGE_ERROR, EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores del módulo opcional de gamificación. */
    @ExceptionHandler(GamificationApplicationException.class)
    /** Descripción: Procesa la operación «handleGamificationApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleGamificationApplicationException(GamificationApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR, EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores de las definiciones de editores configurables. */
    @ExceptionHandler(EditorDefinitionApplicationException.class)
    /** Descripción: Procesa la operación «handleEditorDefinitionApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleEditorDefinitionApplicationException(EditorDefinitionApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }


    /** Convierte los errores de las sesiones neutrales de editores configurables. */
    @ExceptionHandler(EditorSessionApplicationException.class)
    /** Descripción: Procesa la operación «handleEditorSessionApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleEditorSessionApplicationException(EditorSessionApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case NOT_EXECUTABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores de perfiles de integración. */
    @ExceptionHandler(IntegrationProfileApplicationException.class)
    /** Descripción: Procesa la operación «handleIntegrationProfileApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleIntegrationProfileApplicationException(IntegrationProfileApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte errores del compilador y motor de validaciones. */
    @ExceptionHandler(ValidationRuntimeApplicationException.class)
    /** Descripción: Procesa la operación «handleValidationRuntimeApplicationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleValidationRuntimeApplicationException(ValidationRuntimeApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_EXECUTABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }


    /** Convierte errores del runtime transaccional de quick fixes. */
    @ExceptionHandler(QuickFixRuntimeException.class)
    /** Descripción: Procesa la operación «handleQuickFixRuntimeException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleQuickFixRuntimeException(QuickFixRuntimeException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_EXECUTABLE, EXECUTION_FAILED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case UNDO_NOT_AVAILABLE, REDO_NOT_AVAILABLE -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

    /** Convierte los errores del módulo de proyectos locales. */
    @ExceptionHandler(ProjectException.class)
    /** Descripción: Procesa la operación «handleProjectException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleProjectException(ProjectException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_REQUEST,UNSUPPORTED_MEDIA_TYPE -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(),exception.getMessage()));
    }


    /** Convierte fallos de proveedores remotos sin exponer tokens ni respuestas completas. */
    @ExceptionHandler(RemoteStorageException.class)
    /** Descripción: Procesa la operación «handleRemoteStorageException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleRemoteStorageException(RemoteStorageException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case CREDENTIALS_MISSING -> HttpStatus.SERVICE_UNAVAILABLE;
            case PROFILE_NOT_FOUND,NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case CAPACITY_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case REMOTE_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(),exception.getMessage()));
    }

    /** Convierte errores de autenticación remota sin exponer material OAuth. */
    @ExceptionHandler(RemoteAuthException.class)
    /** Descripción: Procesa la operación «handleRemoteAuthException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleRemoteAuthException(RemoteAuthException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case DISABLED, PROVIDER_NOT_CONFIGURED, VAULT_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_REQUEST, FLOW_EXPIRED, STATE_MISMATCH -> HttpStatus.BAD_REQUEST;
            case CONNECTION_NOT_FOUND, REFRESH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case TOKEN_EXCHANGE_FAILED, REMOTE_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(),RemoteSecretRedactor.redact(exception.getMessage())));
    }

    /** Convierte errores de compilación, preview, inyección y rollback tecnológicos. */
    @ExceptionHandler(TechnologyCompilationException.class)
    /** Descripción: Procesa la operación «handleTechnologyCompilationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleTechnologyCompilationException(TechnologyCompilationException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ApiError("TECHNOLOGY_COMPILATION_ERROR", exception.getMessage()));
    }

    /** Convierte rechazos de la política transversal de entrada en un 400 accionable. */
    @ExceptionHandler(HydraInputValidationException.class)
    /** Descripción: Procesa la operación «handleHydraInputValidationException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleHydraInputValidationException(HydraInputValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", exception.getMessage(), exception.field(), exception.getMessage()));
    }

    /** Convierte errores de deserialización JSON en una explicación vinculada al campo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    /** Descripción: Procesa la operación «handleHttpMessageNotReadableException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        Throwable cause = exception;
        JacksonException jacksonException = null;
        while(cause != null && cause.getCause() != cause) {
            if(cause instanceof JacksonException candidate) { jacksonException = candidate; break; }
            cause = cause.getCause();
        }
        String field = jacksonPath(jacksonException);
        Throwable root = exception;
        while(root.getCause() != null && root.getCause() != root) root = root.getCause();
        String technicalDetail = root.getMessage() == null ? exception.getMessage() : root.getMessage();
        ApiErrorAdvice.Details advice = ApiErrorAdvice.from("INVALID_JSON", technicalDetail, field);
        String label = advice.field().isBlank() ? "the submitted form" : "field '" + advice.field() + "'";
        String message = "The value entered in " + label + " is not valid and the document was not saved.";
        return ResponseEntity.badRequest().body(new ApiError("INVALID_JSON", message, advice.field(), technicalDetail));
    }

    /** Obtiene una ruta estable de formulario desde las referencias de Jackson 3. */
    /** Descripción: Ejecuta la operación «jacksonPath» correspondiente a la responsabilidad de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String jacksonPath(JacksonException exception) {
        if(exception == null || exception.getPath() == null || exception.getPath().isEmpty()) return "";
        StringBuilder path = new StringBuilder();
        for(JacksonException.Reference reference : exception.getPath()) {
            String propertyName = reference.getPropertyName();
            if(propertyName != null && !propertyName.isBlank()) {
                if(path.length() > 0) path.append('.');
                path.append(propertyName);
            } else if(reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    @ExceptionHandler(NoSuchElementException.class)
    /** Descripción: Procesa la operación «handleNoSuchElementException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleNoSuchElementException(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", exception.getMessage()));
    }

    /** Converts invalid allowlisted technical/configuration values into a stable client error. */
    @ExceptionHandler(IllegalArgumentException.class)
    /** Descripción: Procesa la operación «handleIllegalArgumentException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", exception.getMessage()));
    }

    /** Last-resort API boundary: details stay server-side and are correlated for diagnosis. */
    @ExceptionHandler(Exception.class)
    /** Descripción: Procesa la operación «handleUnexpectedException» coordinando las responsabilidades de ApiExceptionHandler. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        LOG.error("hydra.api_unexpected_error correlationId={} exception={}", AuditRequestContext.correlationId(), exception.getClass().getName(), exception);
        String suffix = AuditRequestContext.correlationId().isBlank() ? "" : " Correlation ID: " + AuditRequestContext.correlationId();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "An unexpected internal error occurred." + suffix));
    }

    /** Convierte los errores de ejecución de transformaciones en una respuesta estable y accionable. */
    @ExceptionHandler(TransformationExecutionException.class)
    /** Descripción: Procesa la operación «handleTransformationExecution» coordinando las responsabilidades de ApiError. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleTransformationExecution(TransformationExecutionException exception) {
        return ResponseEntity.badRequest().body(new ApiError("TRANSFORMATION_EXECUTION_FAILED", exception.getMessage()));
    }

    /** Convierte los errores de la capa canónica de transformaciones. */
    @ExceptionHandler(TransformationApplicationException.class)
    /** Descripción: Procesa la operación «handleTransformationApplicationException» coordinando las responsabilidades de ApiError. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ApiError> handleTransformationApplicationException(TransformationApplicationException exception) {
        HttpStatus status = switch(exception.getReason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.getReason().name(), exception.getMessage()));
    }

}
