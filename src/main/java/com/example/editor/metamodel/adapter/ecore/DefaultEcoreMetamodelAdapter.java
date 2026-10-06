package com.example.editor.metamodel.adapter.ecore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.EcoreDesignIdSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.ValidationReport;
import com.example.editor.metamodel.target.EcoreAttributeConfiguration;
import com.example.editor.metamodel.target.EcoreDataTypeConfiguration;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.EcoreRelationConfiguration;

/**
 * Implementa la conversión bidireccional entre MetaModel y Ecore.
 */
@Component
public class DefaultEcoreMetamodelAdapter implements EcoreMetamodelAdapter {

    private final EcoreFactory factory = EcoreFactory.eINSTANCE;
    private final EcoreAdapterProperties properties;
    private final EcorePrimitiveTypeRegistry primitiveTypeRegistry;
    private final NeutralMetamodelValidator validator;
    private final EcoreProjectionWriter projectionWriter;
    private final EcoreProjectionReader projectionReader;

    /**
     * Descripción:
     * Crea el adaptador.
     *
     * Entrada:
     * @param properties contiene la configuración.
     * @param primitiveTypeRegistry resuelve tipos primitivos.
     * @param validator valida el núcleo neutral.
     *
     * Salida:
     * Crea una instancia del adaptador.
     */
    /** Descripción: Inicializa una instancia de DefaultEcoreMetamodelAdapter con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; primitiveTypeRegistry: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DefaultEcoreMetamodelAdapter(EcoreAdapterProperties properties, EcorePrimitiveTypeRegistry primitiveTypeRegistry, NeutralMetamodelValidator validator) {
        this.properties = properties;
        this.primitiveTypeRegistry = primitiveTypeRegistry;
        this.validator = validator;
        this.projectionWriter = new EcoreProjectionWriter(properties, primitiveTypeRegistry);
        this.projectionReader = new EcoreProjectionReader(properties, primitiveTypeRegistry);
    }

    /**
     * Descripción:
     * Convierte un metamodelo neutral en un EPackage.
     *
     * Entrada:
     * @param metamodel contiene el metamodelo.
     *
     * Salida:
     * @return el paquete y sus advertencias.
     */
    @Override
    /** Descripción: Ejecuta la operación «toEPackage» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationResult<EPackage> toEPackage(MetaModel metamodel) {
        return toEPackage(metamodel, EcoreProjectionConfiguration.defaultsFor(metamodel));
    }

    @Override
    /** Descripción: Ejecuta la operación «toEPackage» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationResult<EPackage> toEPackage(MetaModel metamodel, EcoreProjectionConfiguration configuration) {
        validate(metamodel);
        EcoreProjectionConfiguration effective = configuration == null ? EcoreProjectionConfiguration.defaultsFor(metamodel) : configuration.normalizedFor(metamodel);
        List<TransformationWarning> warnings = new ArrayList<>();
        EPackage ePackage = factory.createEPackage();
        ePackage.setName(metamodel.name());
        ePackage.setNsURI(effective.namespaceUri());
        ePackage.setNsPrefix(effective.namespacePrefix());
        EcoreDesignIdSupport.writeId(ePackage, metamodel.id());
        projectionWriter.writeAnnotations(ePackage, metamodel.annotations());

        projectionWriter.write(metamodel, ePackage, effective, warnings);
        return new TransformationResult<>(ePackage, warnings);
    }

    /**
     * Descripción:
     * Serializa un metamodelo neutral como Ecore.
     *
     * Entrada:
     * @param metamodel contiene el metamodelo.
     *
     * Salida:
     * @return los bytes y sus advertencias.
     */
    @Override
    /** Descripción: Ejecuta la operación «toBytes» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationResult<byte[]> toBytes(MetaModel metamodel) {
        return toBytes(metamodel, EcoreProjectionConfiguration.defaultsFor(metamodel));
    }

    @Override
    /** Descripción: Ejecuta la operación «toBytes» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: metamodel: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationResult<byte[]> toBytes(MetaModel metamodel, EcoreProjectionConfiguration configuration) {
        EcoreProjectionConfiguration effective = configuration == null ? EcoreProjectionConfiguration.defaultsFor(metamodel) : configuration.normalizedFor(metamodel);
        TransformationResult<EPackage> packageResult = toEPackage(metamodel, effective);
        byte[] content = serialize(effective.fileName(), packageResult.value());
        return new TransformationResult<>(content, packageResult.warnings());
    }

    /**
     * Descripción:
     * Lee un archivo Ecore.
     *
     * Entrada:
     * @param path contiene la ruta.
     *
     * Salida:
     * @return el metamodelo neutral.
     */
    @Override
    /** Descripción: Ejecuta la operación «fromPath» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationResult<MetaModel> fromPath(Path path) {
        EPackage ePackage = loadPackage(path);
        return fromEPackage(ePackage, path.getFileName().toString());
    }

    /**
     * Descripción:
     * Convierte un EPackage ya cargado.
     *
     * Entrada:
     * @param ePackage contiene el paquete.
     * @param artifactName contiene el archivo.
     *
     * Salida:
     * @return el metamodelo neutral.
     */
    @Override
    /** Descripción: Ejecuta la operación «fromEPackage» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: ePackage: dato de entrada necesario para la operación; artifactName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TransformationResult<MetaModel> fromEPackage(EPackage ePackage, String artifactName) {
        EcoreProjectionReader.ReadResult result = projectionReader.read(ePackage, artifactName);
        validate(result.metamodel());
        return new TransformationResult<>(result.metamodel(), result.warnings());
    }

    /** Descripción: Obtiene la información correspondiente a «loadPackage» dentro de DefaultEcoreMetamodelAdapter. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EPackage loadPackage(Path path) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());

        try {
            Resource resource = resourceSet.getResource(URI.createFileURI(path.toAbsolutePath().normalize().toString()), true);

            for (Object content : resource.getContents()) {
                if (content instanceof EPackage ePackage) {
                    return ePackage;
                }
            }
        } catch (RuntimeException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not load Ecore metamodel file: " + path, exception);
        }

        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Ecore metamodel file does not contain an EPackage: " + path);
    }

    /** Descripción: Ejecuta la operación «serialize» correspondiente a la responsabilidad de DefaultEcoreMetamodelAdapter. Entradas: artifactName: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] serialize(String artifactName, EPackage ePackage) {
        String fileName = projectionReader.safeText(artifactName, "metamodel.ecore");
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        Resource resource = resourceSet.createResource(URI.createURI("memory:/" + fileName));        resource.getContents().add(ePackage);
        Map<Object, Object> options = Map.of(
                XMLResource.OPTION_ENCODING, "UTF-8",
                XMLResource.OPTION_FORMATTED, Boolean.TRUE,
                XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            resource.save(output, options);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not serialize neutral metamodel as Ecore", exception);
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validate(MetaModel metamodel) {
        ValidationReport report = validator.validate(metamodel);

        if (report.hasErrors()) {
            String message = report.errors().stream().map(issue -> issue.code() + ": " + issue.message()).collect(java.util.stream.Collectors.joining("; "));
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Neutral metamodel validation failed before Ecore conversion: " + message);
        }
    }


}
