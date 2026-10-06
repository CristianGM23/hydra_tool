package com.example.editor.transformation.runtime;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Orquesta la compilación y ejecución del runtime canónico sobre una transformación persistida. */
@Service
public class TransformationRuntimeService {
    private final TransformationApplicationService transformations;
    private final CanonicalMetamodelApplicationService metamodels;
    private final TransformationPlanCompiler compiler;
    private final CanonicalTransformationRuntime runtime;

    /** Descripción: Inicializa una instancia de TransformationRuntimeService con sus dependencias y datos de entrada. Entradas: transformations: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; compiler: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationRuntimeService(TransformationApplicationService transformations,
            CanonicalMetamodelApplicationService metamodels,
            TransformationPlanCompiler compiler,
            CanonicalTransformationRuntime runtime) {
        this.transformations = transformations;
        this.metamodels = metamodels;
        this.compiler = compiler;
        this.runtime = runtime;
    }

    /** Ejecuta un draft M2M forward para mantener compatibilidad con consumidores existentes. */
    /** Descripción: Procesa la operación «executeDraft» coordinando las responsabilidades de TransformationRuntimeService. Entradas: document: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult executeDraft(TransformationDefinitionDocument document, CanonicalModelDocument sourceModel) {
        return executeDraft(document, sourceModel, TransformationExecutionDirection.FORWARD);
    }

    /** Ejecuta un draft M2M en la dirección solicitada sin persistir la definición. */
    /** Descripción: Procesa la operación «executeDraft» coordinando las responsabilidades de TransformationRuntimeService. Entradas: document: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult executeDraft(TransformationDefinitionDocument document, CanonicalModelDocument sourceModel,
            TransformationExecutionDirection direction) {
        if(document == null || sourceModel == null) throw new TransformationExecutionException("Transformation draft and source model are required");
        if(document.sources().isEmpty() || document.targets().isEmpty()) throw new TransformationExecutionException("Transformation endpoints are incomplete");
        MetaModel declaredSource = metamodels.findById(document.sources().getFirst().metamodelId()).metamodel();
        MetaModel declaredTarget = metamodels.findById(document.targets().getFirst().metamodelId()).metamodel();
        TransformationExecutionDirection effective = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        MetaModel input = effective == TransformationExecutionDirection.REVERSE ? declaredTarget : declaredSource;
        MetaModel output = effective == TransformationExecutionDirection.REVERSE ? declaredSource : declaredTarget;
        return runtime.execute(compiler.compile(document, declaredSource, declaredTarget, effective), input, output, sourceModel);
    }

    /** Ejecuta una transformación persistida forward para mantener el contrato previo. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationRuntimeService. Entradas: transformationId: identificador que delimita el recurso objetivo; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult execute(String transformationId, CanonicalModelDocument sourceModel) {
        return execute(transformationId, sourceModel, TransformationExecutionDirection.FORWARD);
    }

    /** Ejecuta una transformación persistida en una dirección explícita. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationRuntimeService. Entradas: transformationId: identificador que delimita el recurso objetivo; sourceModel: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationExecutionResult execute(String transformationId, CanonicalModelDocument sourceModel,
            TransformationExecutionDirection direction) {
        if(sourceModel == null) throw new TransformationExecutionException("Source model is required");
        TransformationDefinitionDocument document = transformations.find(transformationId).transformation();
        if(document.sources().isEmpty() || document.targets().isEmpty()) throw new TransformationExecutionException("Transformation endpoints are incomplete");
        MetaModel declaredSource = metamodels.findById(document.sources().getFirst().metamodelId()).metamodel();
        MetaModel declaredTarget = metamodels.findById(document.targets().getFirst().metamodelId()).metamodel();
        TransformationExecutionDirection effective = direction == null ? TransformationExecutionDirection.FORWARD : direction;
        MetaModel input = effective == TransformationExecutionDirection.REVERSE ? declaredTarget : declaredSource;
        MetaModel output = effective == TransformationExecutionDirection.REVERSE ? declaredSource : declaredTarget;
        return runtime.execute(compiler.compile(document, declaredSource, declaredTarget, effective), input, output, sourceModel);
    }
    /** Sincroniza un draft bidireccional en batch usando una política explícita cuando ambos lados han cambiado. */
    /** Descripción: Actualiza el estado gestionado por TransformationRuntimeService mediante la operación «synchronizeDraft». Entradas: document: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationResult synchronizeDraft(TransformationDefinitionDocument document, TransformationSynchronizationRequest request) {
        if(document == null || request == null) throw new TransformationExecutionException("Transformation draft and synchronization request are required");
        return synchronize(document, request);
    }

    /** Sincroniza una transformación persistida en batch. */
    /** Descripción: Actualiza el estado gestionado por TransformationRuntimeService mediante la operación «synchronize». Entradas: transformationId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationSynchronizationResult synchronize(String transformationId, TransformationSynchronizationRequest request) {
        if(request == null) throw new TransformationExecutionException("Synchronization request is required");
        return synchronize(transformations.find(transformationId).transformation(), request);
    }

    /** Descripción: Actualiza el estado gestionado por TransformationRuntimeService mediante la operación «synchronize». Entradas: document: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TransformationSynchronizationResult synchronize(TransformationDefinitionDocument document, TransformationSynchronizationRequest request) {
        if(document.directionality() != TransformationDirectionality.BIDIRECTIONAL)
            throw new TransformationExecutionException("Synchronization requires a bidirectional transformation");
        if(request.sourceModel() == null || request.targetModel() == null)
            throw new TransformationExecutionException("Both source-side and target-side models are required for synchronization");
        if(request.mode() == TransformationSynchronizationMode.INCREMENTAL) {
            TransformationIncrementalSynchronizationEngine incremental = new TransformationIncrementalSynchronizationEngine();
            return incremental.synchronize(document, request,
                    (model, direction) -> executeDraft(document, model, direction));
        }
        TransformationExecutionDirection direction;
        String resolution = "";
        if(request.changedSide() == TransformationSynchronizationSide.SOURCE) direction = TransformationExecutionDirection.FORWARD;
        else if(request.changedSide() == TransformationSynchronizationSide.TARGET) direction = TransformationExecutionDirection.REVERSE;
        else {
            TransformationConflictPolicy policy = document.synchronizationPolicy().conflictPolicy();
            if(policy == TransformationConflictPolicy.FAIL_ON_CONFLICT)
                throw new TransformationExecutionException("Both sides changed and the synchronization conflict policy is FAIL_ON_CONFLICT");
            direction = policy == TransformationConflictPolicy.FORWARD_WINS
                    ? TransformationExecutionDirection.FORWARD : TransformationExecutionDirection.REVERSE;
            resolution = policy.name();
        }
        CanonicalModelDocument input = direction == TransformationExecutionDirection.FORWARD ? request.sourceModel() : request.targetModel();
        TransformationExecutionResult executed = executeDraft(document, input, direction);
        CanonicalModelDocument source = direction == TransformationExecutionDirection.FORWARD ? request.sourceModel() : executed.targetModel();
        CanonicalModelDocument target = direction == TransformationExecutionDirection.FORWARD ? executed.targetModel() : request.targetModel();
        return new TransformationSynchronizationResult(source, target, direction, resolution, executed.trace());
    }

}
