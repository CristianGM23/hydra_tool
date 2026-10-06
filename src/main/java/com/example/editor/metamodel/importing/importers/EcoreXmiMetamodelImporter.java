package com.example.editor.metamodel.importing.importers;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

/**
 * Importa Ecore y XMI cuyo elemento raíz es un EPackage.
 */
public abstract class EcoreXmiMetamodelImporter implements MetamodelImporter {

    private final EcoreMetamodelAdapter adapter;
    private final MetamodelImportFormat format;

    /** Descripción: Inicializa una instancia de EcoreXmiMetamodelImporter con sus dependencias y datos de entrada. Entradas: adapter: dato de entrada necesario para la operación; format: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ protected EcoreXmiMetamodelImporter(EcoreMetamodelAdapter adapter, MetamodelImportFormat format) {
        this.adapter = adapter;
        this.format = format;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de EcoreXmiMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return format;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de EcoreXmiMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        SecureXmlSupport.parse(source.content(), "Ecore or EPackage XMI import: " + source.fileName());
        org.eclipse.emf.ecore.EcorePackage.eINSTANCE.eClass();
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        Resource resource = resourceSet.createResource(URI.createURI("memory:/" + source.fileName()));

        try {
            resource.load(new ByteArrayInputStream(source.content()), Map.of());
        } catch(Exception exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not parse Ecore or EPackage XMI import: " + source.fileName(), exception);
        }

        EPackage ePackage = resource.getContents().stream().filter(EPackage.class::isInstance).map(EPackage.class::cast).findFirst().orElseThrow(() -> new MetamodelDesignException(Reason.INVALID_REQUEST, "Imported XMI does not contain an EPackage root: " + source.fileName()));
        TransformationResult<com.example.editor.metamodel.core.MetaModel> result = adapter.fromEPackage(ePackage, source.fileName());
        return new MetamodelImportOutput(result.value(), null, result.warnings());
    }
}
