package com.example.editor.transformation.text;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Orquesta M2T persistido o en preview sin efectos laterales. */
@Service
public class TransformationTextGenerationService {
    private final TransformationApplicationService transformations;
    private final CanonicalMetamodelApplicationService metamodels;
    private final TransformationDefinitionChecker checker;
    private final CanonicalTextGenerationRuntime runtime;

    /** Descripción: Inicializa una instancia de TransformationTextGenerationService con sus dependencias y datos de entrada. Entradas: transformations: dato de entrada necesario para la operación; metamodels: dato de entrada necesario para la operación; checker: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTextGenerationService(TransformationApplicationService transformations,CanonicalMetamodelApplicationService metamodels,
            TransformationDefinitionChecker checker,CanonicalTextGenerationRuntime runtime){
        this.transformations=transformations;this.metamodels=metamodels;this.checker=checker;this.runtime=runtime;
    }

    /** Ejecuta una transformación M2T persistida. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationTextGenerationService. Entradas: id: identificador que delimita el recurso objetivo; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationGenerationResult execute(String id,CanonicalModelDocument sourceModel){return preview(transformations.find(id).transformation(),sourceModel);}

    /** Ejecuta un draft M2T directamente en memoria, sin crear ni reemplazar documentos persistidos. */
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de TransformationTextGenerationService. Entradas: document: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationGenerationResult preview(TransformationDefinitionDocument document,CanonicalModelDocument sourceModel){
        if(document.sources().isEmpty())throw new TransformationExecutionException("M2T source metamodel is required");
        MetaModel source=metamodels.findById(document.sources().getFirst().metamodelId()).metamodel();
        var report=checker.check(document,java.util.List.of(source),java.util.List.of());
        if(!report.valid())throw new TransformationExecutionException("M2T preview contains blocking diagnostics");
        return runtime.generate(document,source,sourceModel);
    }
}
