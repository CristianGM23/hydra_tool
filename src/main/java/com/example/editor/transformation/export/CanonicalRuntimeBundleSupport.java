package com.example.editor.transformation.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Base para wrappers que conservan la definición completa y delegan en el runtime canónico HYDRA. */
abstract class CanonicalRuntimeBundleSupport implements TransformationLanguageAdapter {
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de CanonicalRuntimeBundleSupport con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ CanonicalRuntimeBundleSupport(ObjectMapper mapper){this.mapper=mapper;}
    /** Descripción: Ejecuta la operación «supportedKinds» correspondiente a la responsabilidad de CanonicalRuntimeBundleSupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<TransformationKind> supportedKinds(){return Set.of(TransformationKind.M2M,TransformationKind.M2T,TransformationKind.T2M);}
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de CanonicalRuntimeBundleSupport. Entradas: document: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public TransformationExportResult export(TransformationDefinitionDocument document,MetaModel source,MetaModel target){
        try{
            String json=mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
            List<TransformationExportArtifact> files=new ArrayList<>();
            files.add(new TransformationExportArtifact("transformation.json",json));
            files.add(new TransformationExportArtifact(wrapperPath(document),wrapper(document)));
            files.add(new TransformationExportArtifact("README.md",readme(document)));
            return new TransformationExportResult(document.id(),language(),TransformationExportFidelity.CANONICAL_RUNTIME,true,List.of(),files);
        }catch(JacksonException exception){
            return new TransformationExportResult(document==null?"":document.id(),language(),TransformationExportFidelity.CANONICAL_RUNTIME,false,
                    List.of(new TransformationExportDiagnostic("ERROR","CANONICAL_SERIALIZATION_FAILED","$",exception.getMessage(),"Correct the canonical definition before exporting.")),List.of());
        }
    }
    /** Ruta del wrapper específico de tecnología. */ /** Descripción: Ejecuta la operación «wrapperPath» correspondiente a la responsabilidad de CanonicalRuntimeBundleSupport. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected abstract String wrapperPath(TransformationDefinitionDocument document);
    /** Fuente del wrapper específico de tecnología. */ /** Descripción: Ejecuta la operación «wrapper» correspondiente a la responsabilidad de CanonicalRuntimeBundleSupport. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ protected abstract String wrapper(TransformationDefinitionDocument document);
    /** Describe el contrato portable y evita presentar el wrapper como runtime independiente. */ /** Descripción: Obtiene la información correspondiente a «readme» dentro de CanonicalRuntimeBundleSupport. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(TransformationDefinitionDocument document){
        return "# HYDRA transformation bundle\n\nDefinition: `"+document.id()+"` (`"+document.kind()+"`).\n\nThis export preserves the complete canonical projection for the selected execution direction in `transformation.json`. The wrapper intentionally delegates execution to the HYDRA canonical transformation runtime; it does not re-implement or approximate transformation semantics.\n";
    }
}
