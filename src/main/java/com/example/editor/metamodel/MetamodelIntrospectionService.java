package com.example.editor.metamodel;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.springframework.stereotype.Service;

import com.example.editor.emf.LoadedMetamodel;
import com.example.editor.metamodel.MetamodelDescriptor.AttributeDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.ClassDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.DataTypeDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.EnumDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.EnumLiteralDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.PackageDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.ReferenceDescriptor;

/**
 * Convierte un metamodelo Ecore cargado en una descripción neutral.
 *
 * La descripción contiene clases, atributos, referencias, enumeraciones,
 * tipos de datos, herencia, cardinalidades y containment. Más adelante será
 * utilizada para generar dinámicamente la interfaz del editor.
 */
@Service
public class MetamodelIntrospectionService {

    /**
     * Descripción:
     * Interpreta todos los paquetes raíz de un metamodelo cargado.
     *
     * Entrada:
     * @param loadedMetamodel contiene el recurso Ecore y sus paquetes registrados.
     *
     * Salida:
     * @return una descripción completa y serializable del metamodelo.
     *
     * @throws MetamodelIntrospectionException cuando el metamodelo es nulo.
     */
    /** Descripción: Ejecuta la operación «describe» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: loadedMetamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDescriptor describe(LoadedMetamodel loadedMetamodel) {
        if (loadedMetamodel == null) {
            throw new MetamodelIntrospectionException("Loaded metamodel must not be null");
        }

        List<PackageDescriptor> packages = new ArrayList<>();

        for (EPackage rootPackage : loadedMetamodel.getRootPackages()) {
            packages.add(describePackage(rootPackage));
        }

        String sourceUri = "";

        if (loadedMetamodel.getResource().getURI() != null) {
            sourceUri = loadedMetamodel.getResource().getURI().toString();
        }

        return new MetamodelDescriptor(sourceUri, List.copyOf(packages));
    }

    /**
     * Descripción:
     * Interpreta un paquete Ecore, sus clasificadores y sus subpaquetes.
     *
     * Entrada:
     * @param ePackage es el paquete que se debe interpretar.
     *
     * Salida:
     * @return una descripción del paquete.
     */
    /** Descripción: Ejecuta la operación «describePackage» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PackageDescriptor describePackage(EPackage ePackage) {
        List<ClassDescriptor> classes = new ArrayList<>();
        List<EnumDescriptor> enums = new ArrayList<>();
        List<DataTypeDescriptor> dataTypes = new ArrayList<>();
        List<PackageDescriptor> subpackages = new ArrayList<>();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                classes.add(describeClass(eClass));
            } else if (classifier instanceof EEnum eEnum) {
                enums.add(describeEnum(eEnum));
            } else if (classifier instanceof EDataType eDataType) {
                dataTypes.add(describeDataType(eDataType));
            }
        }

        for (EPackage subpackage : ePackage.getESubpackages()) {
            subpackages.add(describePackage(subpackage));
        }

        return new PackageDescriptor(
            ePackage.getName(),
            ePackage.getNsURI(),
            ePackage.getNsPrefix(),
            List.copyOf(classes),
            List.copyOf(enums),
            List.copyOf(dataTypes),
            List.copyOf(subpackages)
        );
    }

    /**
     * Descripción:
     * Interpreta una EClass y todas sus características, incluidas las heredadas.
     *
     * Entrada:
     * @param eClass es la clase que se debe interpretar.
     *
     * Salida:
     * @return una descripción de la clase.
     */
    /** Descripción: Ejecuta la operación «describeClass» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: eClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ClassDescriptor describeClass(EClass eClass) {
        List<String> superTypeIds = new ArrayList<>();
        List<AttributeDescriptor> attributes = new ArrayList<>();
        List<ReferenceDescriptor> references = new ArrayList<>();

        for (EClass superType : eClass.getESuperTypes()) {
            superTypeIds.add(buildClassifierId(superType));
        }

        for (EAttribute attribute : eClass.getEAllAttributes()) {
            attributes.add(describeAttribute(eClass, attribute));
        }

        for (EReference reference : eClass.getEAllReferences()) {
            references.add(describeReference(eClass, reference));
        }

        boolean instantiable = true;

        if (eClass.isAbstract()) {
            instantiable = false;
        } else if (eClass.isInterface()) {
            instantiable = false;
        }

        return new ClassDescriptor(
            buildClassifierId(eClass),
            eClass.getName(),
            eClass.isAbstract(),
            eClass.isInterface(),
            instantiable,
            List.copyOf(superTypeIds),
            List.copyOf(attributes),
            List.copyOf(references)
        );
    }

    /**
     * Descripción:
     * Interpreta un atributo desde el punto de vista de una clase concreta.
     *
     * Entrada:
     * @param viewedClass es la clase cuya información se está construyendo.
     * @param attribute es el atributo que se debe interpretar.
     *
     * Salida:
     * @return una descripción del atributo.
     */
    /** Descripción: Ejecuta la operación «describeAttribute» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: viewedClass: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AttributeDescriptor describeAttribute(EClass viewedClass, EAttribute attribute) {
        boolean inherited = attribute.getEContainingClass() != viewedClass;

        return new AttributeDescriptor(
            buildFeatureId(attribute),
            attribute.getName(),
            buildClassifierId(attribute.getEContainingClass()),
            inherited,
            buildClassifierId(attribute.getEAttributeType()),
            attribute.getEAttributeType().getName(),
            attribute.getLowerBound(),
            attribute.getUpperBound(),
            attribute.isMany(),
            attribute.getLowerBound() > 0,
            attribute.isChangeable(),
            attribute.isDerived(),
            attribute.isTransient(),
            attribute.isVolatile(),
            attribute.isUnsettable(),
            attribute.isOrdered(),
            attribute.isUnique(),
            attribute.isID(),
            attribute.getDefaultValueLiteral()
        );
    }

    /**
     * Descripción:
     * Interpreta una referencia desde el punto de vista de una clase concreta.
     *
     * Entrada:
     * @param viewedClass es la clase cuya información se está construyendo.
     * @param reference es la referencia que se debe interpretar.
     *
     * Salida:
     * @return una descripción de la referencia.
     */
    /** Descripción: Ejecuta la operación «describeReference» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: viewedClass: dato de entrada necesario para la operación; reference: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ReferenceDescriptor describeReference(EClass viewedClass, EReference reference) {
        boolean inherited = reference.getEContainingClass() != viewedClass;
        String oppositeReferenceId = null;

        if (reference.getEOpposite() != null) {
            oppositeReferenceId = buildFeatureId(reference.getEOpposite());
        }

        return new ReferenceDescriptor(
            buildFeatureId(reference),
            reference.getName(),
            buildClassifierId(reference.getEContainingClass()),
            inherited,
            buildClassifierId(reference.getEReferenceType()),
            reference.getEReferenceType().getName(),
            reference.getLowerBound(),
            reference.getUpperBound(),
            reference.isMany(),
            reference.getLowerBound() > 0,
            reference.isContainment(),
            reference.isContainer(),
            reference.isResolveProxies(),
            reference.isChangeable(),
            reference.isDerived(),
            reference.isTransient(),
            reference.isVolatile(),
            reference.isUnsettable(),
            reference.isOrdered(),
            reference.isUnique(),
            oppositeReferenceId
        );
    }

    /**
     * Descripción:
     * Interpreta una enumeración y sus literales.
     *
     * Entrada:
     * @param eEnum es la enumeración que se debe interpretar.
     *
     * Salida:
     * @return una descripción de la enumeración.
     */
    /** Descripción: Ejecuta la operación «describeEnum» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: eEnum: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EnumDescriptor describeEnum(EEnum eEnum) {
        List<EnumLiteralDescriptor> literals = new ArrayList<>();

        for (EEnumLiteral literal : eEnum.getELiterals()) {
            literals.add(new EnumLiteralDescriptor(literal.getName(), literal.getLiteral(), literal.getValue()));
        }

        return new EnumDescriptor(buildClassifierId(eEnum), eEnum.getName(), List.copyOf(literals));
    }

    /**
     * Descripción:
     * Interpreta un tipo de dato personalizado.
     *
     * Entrada:
     * @param eDataType es el tipo de dato que se debe interpretar.
     *
     * Salida:
     * @return una descripción del tipo de dato.
     */
    /** Descripción: Ejecuta la operación «describeDataType» correspondiente a la responsabilidad de MetamodelIntrospectionService. Entradas: eDataType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private DataTypeDescriptor describeDataType(EDataType eDataType) {
        return new DataTypeDescriptor(
            buildClassifierId(eDataType),
            eDataType.getName(),
            eDataType.getInstanceClassName(),
            eDataType.isSerializable()
        );
    }

    /**
     * Descripción:
     * Construye un identificador estable para un clasificador Ecore.
     *
     * Entrada:
     * @param classifier es la clase, enumeración o tipo de dato.
     *
     * Salida:
     * @return un identificador formado por el nsURI y el nombre.
     */
    /** Descripción: Crea o prepara el resultado de «buildClassifierId» dentro de MetamodelIntrospectionService. Entradas: classifier: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildClassifierId(EClassifier classifier) {
        return classifier.getEPackage().getNsURI() + "#//" + classifier.getName();
    }

    /**
     * Descripción:
     * Construye un identificador estable para un atributo o referencia.
     *
     * Entrada:
     * @param feature es la característica estructural.
     *
     * Salida:
     * @return un identificador formado por la clase y el nombre de la característica.
     */
    /** Descripción: Crea o prepara el resultado de «buildFeatureId» dentro de MetamodelIntrospectionService. Entradas: feature: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String buildFeatureId(EStructuralFeature feature) {
        return buildClassifierId(feature.getEContainingClass()) + "/" + feature.getName();
    }
}