package com.example.editor.metamodel.export;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.validation.ValidationReport;

/** Carga una sola vez el MetaModel, el diagrama y la configuración de destinos. */
@Component
public class DefaultNeutralMetamodelExportContextLoader implements NeutralMetamodelExportContextLoader {

    private final CanonicalMetamodelApplicationService canonicalService;
    private final MetamodelDiagramStorageService diagramStorageService;
    private final ProjectionCoordinator projectionCoordinator;

    /** Descripción: Inicializa una instancia de DefaultNeutralMetamodelExportContextLoader con sus dependencias y datos de entrada. Entradas: canonicalService: servicio colaborador utilizado por la operación; diagramStorageService: servicio colaborador utilizado por la operación; projectionCoordinator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DefaultNeutralMetamodelExportContextLoader(CanonicalMetamodelApplicationService canonicalService, MetamodelDiagramStorageService diagramStorageService, ProjectionCoordinator projectionCoordinator) {
        this.canonicalService = canonicalService;
        this.diagramStorageService = diagramStorageService;
        this.projectionCoordinator = projectionCoordinator;
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «load» dentro de DefaultNeutralMetamodelExportContextLoader. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public NeutralMetamodelExportContext load(String metamodelId) {
        return load(metamodelId, MetamodelExportOptions.defaults());
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «load» dentro de DefaultNeutralMetamodelExportContextLoader. Entradas: metamodelId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public NeutralMetamodelExportContext load(String metamodelId, MetamodelExportOptions options) {
        MetamodelLoadResult loaded = canonicalService.findById(metamodelId);
        ValidationReport validation = canonicalService.validate(loaded.metamodel());
        if(validation.hasErrors()) {
            String codes = validation.errors().stream().map(item -> item.code()).distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("UNKNOWN");
            throw new IllegalStateException("Canonical metamodel cannot be exported because validation failed: " + codes);
        }
        MetamodelDiagramDocument diagram = diagramStorageService.load(metamodelId);
        TargetConfigurationDocument targets = projectionCoordinator.findTargets(metamodelId);
        return new DefaultNeutralMetamodelExportContext(loaded.metamodel(), diagram, targets, List.of(), options);
    }
}
