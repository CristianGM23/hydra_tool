import type { ClassDescriptor, MetamodelDescriptor, PackageDescriptor, ReferenceDescriptor } from '../types/editor';

/**
 * Obtiene todas las clases de un paquete y sus subpaquetes.
 *
 * @param packageDescriptor paquete que se debe recorrer.
 * @returns clases encontradas.
 */
function flattenPackage(packageDescriptor: PackageDescriptor): ClassDescriptor[] {
    const classes = [...packageDescriptor.classes];

    for (const subpackage of packageDescriptor.subpackages) {
        classes.push(...flattenPackage(subpackage));
    }

    return classes;
}

/**
 * Obtiene todas las clases del metamodelo.
 *
 * @param descriptor descripción del metamodelo.
 * @returns clases encontradas.
 */
export function flattenClasses(descriptor: MetamodelDescriptor): ClassDescriptor[] {
    const classes: ClassDescriptor[] = [];

    for (const packageDescriptor of descriptor.packages) {
        classes.push(...flattenPackage(packageDescriptor));
    }

    return classes;
}

/**
 * Busca una clase mediante su identificador.
 *
 * @param descriptor descripción del metamodelo.
 * @param classId identificador buscado.
 * @returns clase encontrada o undefined.
 */
export function findClass(descriptor: MetamodelDescriptor, classId: string): ClassDescriptor | undefined {
    return flattenClasses(descriptor).find(classDescriptor => classDescriptor.id === classId);
}

/**
 * Comprueba si una clase puede asignarse a otra.
 *
 * @param descriptor descripción del metamodelo.
 * @param expectedClassId tipo esperado.
 * @param actualClassId tipo real.
 * @returns true cuando el tipo es compatible.
 */
export function isAssignable(descriptor: MetamodelDescriptor, expectedClassId: string, actualClassId: string): boolean {
    if (expectedClassId === actualClassId) {
        return true;
    }

    const actualClass = findClass(descriptor, actualClassId);

    if (actualClass === undefined) {
        return false;
    }

    for (const superTypeId of actualClass.superTypeIds) {
        if (isAssignable(descriptor, expectedClassId, superTypeId)) {
            return true;
        }
    }

    return false;
}

/**
 * Busca una clase raíz apropiada.
 *
 * @param descriptor descripción del metamodelo.
 * @returns clase raíz o undefined.
 */
export function findRootClass(descriptor: MetamodelDescriptor): ClassDescriptor | undefined {
    const classes = flattenClasses(descriptor);

    for (const classDescriptor of classes) {
        if (classDescriptor.instantiable && classDescriptor.references.some(reference => reference.containment)) {
            return classDescriptor;
        }
    }

    return classes.find(classDescriptor => classDescriptor.instantiable);
}

/**
 * Busca una referencia containment compatible.
 *
 * @param descriptor descripción del metamodelo.
 * @param containerClassId clase del contenedor.
 * @param childClassId clase del hijo.
 * @returns referencia encontrada o undefined.
 */
export function findContainmentReference(descriptor: MetamodelDescriptor, containerClassId: string, childClassId: string): ReferenceDescriptor | undefined {
    const containerClass = findClass(descriptor, containerClassId);

    if (containerClass === undefined) {
        return undefined;
    }

    return containerClass.references.find(reference => reference.containment && isAssignable(descriptor, reference.targetClassId, childClassId));
}

/**
 * Busca una referencia normal compatible.
 *
 * @param descriptor descripción del metamodelo.
 * @param sourceClassId clase de origen.
 * @param targetClassId clase de destino.
 * @returns referencia encontrada o undefined.
 */
export function findConnectionReference(descriptor: MetamodelDescriptor, sourceClassId: string, targetClassId: string): ReferenceDescriptor | undefined {
    const sourceClass = findClass(descriptor, sourceClassId);

    if (sourceClass === undefined) {
        return undefined;
    }

    return sourceClass.references.find(reference => !reference.containment && isAssignable(descriptor, reference.targetClassId, targetClassId));
}