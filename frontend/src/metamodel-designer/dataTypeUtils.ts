import type { MetaModel } from './types';

export function findMetamodelDataTypeError(model: MetaModel): string {
    const classifierNames = new Set<string>();
    const classifierIds = new Set<string>();
    for (const classifier of [...model.classes, ...model.enums, ...model.dataTypes]) {
        if (classifier.id.trim() === '') return 'Classifier id must not be empty';
        if (classifierIds.has(classifier.id)) return `Duplicate classifier id: ${classifier.id}`;
        if (classifier.name.trim() === '') return 'Classifier name must not be empty';
        if (classifierNames.has(classifier.name)) return `Duplicate classifier name: ${classifier.name}`;
        classifierIds.add(classifier.id);
        classifierNames.add(classifier.name);
    }
    return '';
}
