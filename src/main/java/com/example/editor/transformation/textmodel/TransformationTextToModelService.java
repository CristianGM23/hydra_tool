package com.example.editor.transformation.textmodel;

import org.springframework.stereotype.Service;

import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.transformation.runtime.TransformationExecutionResult;
import com.example.editor.transformation.runtime.TransformationRuntimeService;

/** Orquesta T2M como parsing por gramática y proyección M2M opcional, sin duplicar reglas semánticas. */
@Service
public class TransformationTextToModelService {
    private final TransformationApplicationService transformations;
    private final TransformationDefinitionChecker checker;
    private final TransformationTextParserGateway parser;
    private final TransformationRuntimeService m2m;

    /** Descripción: Inicializa una instancia de TransformationTextToModelService con sus dependencias y datos de entrada. Entradas: transformations: dato de entrada necesario para la operación; checker: dato de entrada necesario para la operación; parser: componente de conversión utilizado durante el procesamiento; m2m: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTextToModelService(TransformationApplicationService transformations,TransformationDefinitionChecker checker,
            TransformationTextParserGateway parser,TransformationRuntimeService m2m){
        this.transformations=transformations;this.checker=checker;this.parser=parser;this.m2m=m2m;
    }

    /** Ejecuta una definición T2M persistida. */
    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de TransformationTextToModelService. Entradas: id: identificador que delimita el recurso objetivo; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTextToModelResult execute(String id,String text){return preview(transformations.find(id).transformation(),text);}

    /** Ejecuta un draft T2M en memoria y solo reutiliza una transformación M2M persistida si se configuró como postproyección. */
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de TransformationTextToModelService. Entradas: document: dato de entrada necesario para la operación; text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationTextToModelResult preview(TransformationDefinitionDocument document,String text){
        if(document==null||document.kind()!=TransformationKind.T2M||document.textToModel()==null)throw new TransformationExecutionException("T2M transformation definition is required");
        var report=checker.check(document,java.util.List.of(),java.util.List.of());
        if(!report.valid())throw new TransformationExecutionException("T2M preview contains blocking diagnostics");
        var parsed=parser.parse(document.textToModel().grammarId(),document.textToModel().relativePath(),text==null?"":text);
        if(!parsed.valid()||parsed.semanticModel()==null)return new TransformationTextToModelResult(null,null,null,parsed.diagnostics(),parsed.grammarSnapshotId());
        if(document.textToModel().postTransformationId().isBlank())return new TransformationTextToModelResult(parsed.semanticModel(),parsed.semanticModel(),null,parsed.diagnostics(),parsed.grammarSnapshotId());
        TransformationExecutionResult projected=m2m.execute(document.textToModel().postTransformationId(),parsed.semanticModel());
        return new TransformationTextToModelResult(parsed.semanticModel(),projected.targetModel(),projected.trace(),parsed.diagnostics(),parsed.grammarSnapshotId());
    }
}
