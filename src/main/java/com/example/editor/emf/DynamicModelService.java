package com.example.editor.emf;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.springframework.stereotype.Service;

/**
 * Crea y modifica objetos EMF utilizando la información del metamodelo Ecore.
 *
 * La clase funciona de forma dinámica. No necesita clases Java generadas para
 * cada EClass del metamodelo.
 */
@Service
public class DynamicModelService {

    /**
     * Descripción:
     * Crea un EObject a partir del nsURI del paquete y del nombre de una EClass.
     *
     * Entrada:
     * @param loadedMetamodel contiene el metamodelo cargado.
     * @param nsUri identifica el paquete que contiene la clase.
     * @param className contiene el nombre de la EClass.
     *
     * Salida:
     * @return un EObject nuevo de la clase indicada.
     *
     * @throws ModelOperationException cuando el paquete o la clase no son válidos.
     */
    /** Descripción: Crea o prepara el resultado de «createObject» dentro de DynamicModelService. Entradas: loadedMetamodel: dato de entrada necesario para la operación; nsUri: dato de entrada necesario para la operación; className: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EObject createObject(LoadedMetamodel loadedMetamodel, String nsUri, String className) {
        EPackage ePackage = findPackage(loadedMetamodel, nsUri);
        EClassifier classifier = ePackage.getEClassifier(className);

        if (classifier == null) {
            throw new ModelOperationException("EClass was not found: " + className);
        }

        if (!(classifier instanceof EClass)) {
            throw new ModelOperationException("EClassifier is not an EClass: " + className);
        }

        EClass eClass = (EClass) classifier;

        if (eClass.isAbstract()) {
            throw new ModelOperationException("Abstract EClass cannot be instantiated: " + className);
        } else if (eClass.isInterface()) {
            throw new ModelOperationException("Interface EClass cannot be instantiated: " + className);
        }

        return ePackage.getEFactoryInstance().create(eClass);
    }

    /**
     * Descripción:
     * Convierte un texto al tipo definido por un EAttribute y asigna el valor.
     *
     * Entrada:
     * @param object es el EObject que se debe modificar.
     * @param attributeName contiene el nombre del atributo.
     * @param serializedValue contiene el valor escrito como texto.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelOperationException cuando el atributo no puede modificarse.
     */
    /** Descripción: Actualiza el estado gestionado por DynamicModelService mediante la operación «setAttribute». Entradas: object: dato de entrada necesario para la operación; attributeName: dato de entrada necesario para la operación; serializedValue: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAttribute(EObject object, String attributeName, String serializedValue) {
        EStructuralFeature feature = findFeature(object, attributeName);

        if (!(feature instanceof EAttribute)) {
            throw new ModelOperationException("Feature is not an EAttribute: " + attributeName);
        }

        EAttribute attribute = (EAttribute) feature;

        if (!attribute.isChangeable()) {
            throw new ModelOperationException("EAttribute is not changeable: " + attributeName);
        } else if (attribute.isDerived()) {
            throw new ModelOperationException("Derived EAttribute cannot be modified: " + attributeName);
        } else if (attribute.isMany()) {
            throw new ModelOperationException("Many-valued EAttribute is not supported by this operation: " + attributeName);
        }

        if (serializedValue == null) {
            throw new ModelOperationException("Attribute value must not be null: " + attributeName);
        }

        try {
            Object convertedValue = attribute.getEAttributeType().getEPackage().getEFactoryInstance().createFromString(attribute.getEAttributeType(), serializedValue);
            object.eSet(attribute, convertedValue);
        } catch (RuntimeException exception) {
            throw new ModelOperationException("Could not convert attribute value for: " + attributeName, exception);
        }
    }


    /** Elimina el valor explícito de un atributo modificable. */
    /** Descripción: Elimina o libera el recurso gestionado por «clearAttribute» de forma controlada. Entradas: object: dato de entrada necesario para la operación; attributeName: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void clearAttribute(EObject object, String attributeName) {
        EStructuralFeature feature = findFeature(object, attributeName);
        if(!(feature instanceof EAttribute attribute)) throw new ModelOperationException("Feature is not an EAttribute: " + attributeName);
        if(!attribute.isChangeable() || attribute.isDerived()) throw new ModelOperationException("EAttribute cannot be cleared: " + attributeName);
        object.eUnset(attribute);
    }

    /** Elimina una referencia no containment concreta. */
    /** Descripción: Elimina o libera el recurso gestionado por «removeReference» de forma controlada. Entradas: source: dato de entrada necesario para la operación; referenceName: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void removeReference(EObject source, String referenceName, EObject target) {
        EReference reference = findReference(source, referenceName);
        if(reference.isContainment()) throw new ModelOperationException("Containment EReference must be modified by deleting its contained element: " + referenceName);
        if(!reference.isChangeable() || reference.isDerived()) throw new ModelOperationException("EReference cannot be modified: " + referenceName);
        if(reference.isMany()) {
            java.util.List<Object> targets = mutableValues(source.eGet(reference), referenceName);
            targets.remove(target);
            source.eSet(reference, targets);
        } else if(source.eGet(reference) == target) source.eUnset(reference);
    }

    /**
     * Descripción:
     * Añade un EObject hijo a una referencia de containment.
     *
     * Entrada:
     * @param container es el EObject que actuará como contenedor.
     * @param referenceName contiene el nombre de la referencia containment.
     * @param child es el EObject que se debe añadir.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelOperationException cuando la referencia no es válida.
     */
    /** Descripción: Ejecuta la operación «addContained» correspondiente a la responsabilidad de DynamicModelService. Entradas: container: dato de entrada necesario para la operación; referenceName: dato de entrada necesario para la operación; child: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void addContained(EObject container, String referenceName, EObject child) {
        EReference reference = findReference(container, referenceName);

        if (!reference.isContainment()) {
            throw new ModelOperationException("EReference is not a containment reference: " + referenceName);
        }

        validateReferenceChange(reference, child);

        if (reference.isMany()) {
            java.util.List<Object> children = mutableValues(container.eGet(reference), referenceName);

            if (reference.getUpperBound() != -1 && children.size() >= reference.getUpperBound()) {
                throw new ModelOperationException("Containment reference has reached its upper bound: " + referenceName);
            }

            children.add(child);
            container.eSet(reference, children);
        } else {
            if (container.eIsSet(reference)) {
                throw new ModelOperationException("Single-valued containment reference already has a value: " + referenceName);
            }

            container.eSet(reference, child);
        }
    }

    /**
     * Descripción:
     * Establece una referencia semántica entre dos EObjects.
     *
     * Entrada:
     * @param source es el EObject de origen.
     * @param referenceName contiene el nombre de la referencia.
     * @param target es el EObject de destino.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelOperationException cuando la conexión no es válida.
     */
    /** Descripción: Actualiza el estado gestionado por DynamicModelService mediante la operación «setReference». Entradas: source: dato de entrada necesario para la operación; referenceName: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setReference(EObject source, String referenceName, EObject target) {
        EReference reference = findReference(source, referenceName);

        if (reference.isContainment()) {
            throw new ModelOperationException("Containment EReference must be modified with addContained: " + referenceName);
        }

        validateReferenceChange(reference, target);

        if (reference.isMany()) {
            java.util.List<Object> targets = mutableValues(source.eGet(reference), referenceName);

            if (reference.getUpperBound() != -1 && targets.size() >= reference.getUpperBound()) {
                throw new ModelOperationException("EReference has reached its upper bound: " + referenceName);
            }

            if (!targets.contains(target)) {
                targets.add(target);
                source.eSet(reference, targets);
            }
        } else {
            source.eSet(reference, target);
        }
    }

    /**
     * Descripción: Convierte de forma segura un valor multivaluado de EMF en una lista mutable sin casts genéricos no comprobados.
     * Entrada: @param value contiene el valor devuelto por EMF; @param featureName identifica la característica para el diagnóstico.
     * Salida: @return una copia mutable de los valores actuales.
     * @throws ModelOperationException cuando EMF no devuelve una colección para una característica multivaluada.
     */
    /** Descripción: Ejecuta la operación «mutableValues» correspondiente a la responsabilidad de DynamicModelService. Entradas: value: valor de entrada que debe procesarse; featureName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.List<Object> mutableValues(Object value, String featureName) {
        if (!(value instanceof java.util.Collection<?> values)) {
            throw new ModelOperationException("Many-valued feature did not expose a collection: " + featureName);
        }
        return new java.util.ArrayList<>(values);
    }

    /**
     * Descripción:
     * Busca un paquete registrado mediante su nsURI.
     *
     * Entrada:
     * @param loadedMetamodel contiene el registro de paquetes.
     * @param nsUri identifica el paquete solicitado.
     *
     * Salida:
     * @return el EPackage registrado.
     *
     * @throws ModelOperationException cuando el paquete no existe.
     */
    /** Descripción: Obtiene la información correspondiente a «findPackage» dentro de DynamicModelService. Entradas: loadedMetamodel: dato de entrada necesario para la operación; nsUri: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EPackage findPackage(LoadedMetamodel loadedMetamodel, String nsUri) {
        if (loadedMetamodel == null) {
            throw new ModelOperationException("Loaded metamodel must not be null");
        }

        if (nsUri == null || nsUri.isBlank()) {
            throw new ModelOperationException("EPackage nsURI must not be empty");
        }

        Object registeredValue = loadedMetamodel.getResourceSet().getPackageRegistry().get(nsUri);

        if (registeredValue instanceof EPackage) {
            return (EPackage) registeredValue;
        }

        if (registeredValue instanceof EPackage.Descriptor) {
            EPackage.Descriptor descriptor = (EPackage.Descriptor) registeredValue;
            return descriptor.getEPackage();
        }

        throw new ModelOperationException("EPackage was not found for nsURI: " + nsUri);
    }

    /**
     * Descripción:
     * Busca una característica estructural dentro de la clase de un EObject.
     *
     * Entrada:
     * @param object es el EObject que contiene la característica.
     * @param featureName contiene el nombre de la característica.
     *
     * Salida:
     * @return la característica encontrada.
     *
     * @throws ModelOperationException cuando la característica no existe.
     */
    /** Descripción: Obtiene la información correspondiente a «findFeature» dentro de DynamicModelService. Entradas: object: dato de entrada necesario para la operación; featureName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EStructuralFeature findFeature(EObject object, String featureName) {
        if (object == null) {
            throw new ModelOperationException("EObject must not be null");
        }

        if (featureName == null || featureName.isBlank()) {
            throw new ModelOperationException("Feature name must not be empty");
        }

        EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);

        if (feature == null) {
            throw new ModelOperationException("Feature was not found: " + featureName);
        }

        return feature;
    }

    /**
     * Descripción:
     * Busca una EReference dentro de la clase de un EObject.
     *
     * Entrada:
     * @param object es el EObject que contiene la referencia.
     * @param referenceName contiene el nombre de la referencia.
     *
     * Salida:
     * @return la EReference encontrada.
     *
     * @throws ModelOperationException cuando la característica no es una referencia.
     */
    /** Descripción: Obtiene la información correspondiente a «findReference» dentro de DynamicModelService. Entradas: object: dato de entrada necesario para la operación; referenceName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EReference findReference(EObject object, String referenceName) {
        EStructuralFeature feature = findFeature(object, referenceName);

        if (!(feature instanceof EReference)) {
            throw new ModelOperationException("Feature is not an EReference: " + referenceName);
        }

        return (EReference) feature;
    }

    /**
     * Descripción:
     * Comprueba que una referencia puede modificarse y acepta el objeto destino.
     *
     * Entrada:
     * @param reference es la referencia que se debe comprobar.
     * @param target es el EObject que se quiere asignar.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelOperationException cuando la asignación no es válida.
     */
    /** Descripción: Valida las condiciones necesarias para «validateReferenceChange» y rechaza entradas no válidas. Entradas: reference: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateReferenceChange(EReference reference, EObject target) {
        if (target == null) {
            throw new ModelOperationException("Reference target must not be null: " + reference.getName());
        }

        if (!reference.isChangeable()) {
            throw new ModelOperationException("EReference is not changeable: " + reference.getName());
        } else if (reference.isDerived()) {
            throw new ModelOperationException("Derived EReference cannot be modified: " + reference.getName());
        }

        EClass expectedType = reference.getEReferenceType();

        if (!expectedType.isSuperTypeOf(target.eClass())) {
            throw new ModelOperationException("EObject type is not compatible with EReference: " + reference.getName());
        }
    }
}