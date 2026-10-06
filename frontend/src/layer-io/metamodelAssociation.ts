import type { MetaModel } from '../metamodel-designer/types';
import type { LayerImportDiagnostic, LayerImportMetric } from './layerInterchangeTypes';

export interface AssociatedMetamodelCompatibility {
    summary: string;
    metrics: LayerImportMetric[];
    diagnostics: LayerImportDiagnostic[];
    mappedClasses: number;
    missingClasses: number;
    mappedEnums: number;
    missingEnums: number;
    mappedFeatures: number;
    missingFeatures: number;
}

/**
 * Compares an optional source-side metamodel with the currently active canonical
 * metamodel. Matching is deliberately semantic (case-insensitive names inside
 * the owning class), never by transient imported IDs.
 */
export function compareAssociatedMetamodel(source: MetaModel, target: MetaModel): AssociatedMetamodelCompatibility {
    const diagnostics: LayerImportDiagnostic[] = [];
    let mappedClasses = 0;
    let missingClasses = 0;
    let mappedEnums = 0;
    let missingEnums = 0;
    let mappedFeatures = 0;
    let missingFeatures = 0;

    for (const sourceClass of source.classes) {
        const targetClass = target.classes.find(candidate => sameName(candidate.name, sourceClass.name));
        if (targetClass === undefined) {
            missingClasses += 1;
            diagnostics.push({ severity: 'WARNING', category: 'ASSOCIATED_METAMODEL', message: `Class ${sourceClass.name} exists in the associated metamodel but not in the active metamodel.` });
            missingFeatures += sourceClass.attributes.length + sourceClass.operations.length + source.relations.filter(relation => relation.sourceTypeId === sourceClass.id).length;
            continue;
        }
        mappedClasses += 1;
        for (const attribute of sourceClass.attributes) {
            if (targetClass.attributes.some(candidate => sameName(candidate.name, attribute.name))) mappedFeatures += 1;
            else {
                missingFeatures += 1;
                diagnostics.push({ severity: 'WARNING', category: 'ASSOCIATED_METAMODEL', message: `${sourceClass.name}.${attribute.name} is not present in the active metamodel.` });
            }
        }
        for (const operation of sourceClass.operations) {
            if (targetClass.operations.some(candidate => sameName(candidate.name, operation.name))) mappedFeatures += 1;
            else {
                missingFeatures += 1;
                diagnostics.push({ severity: 'WARNING', category: 'ASSOCIATED_METAMODEL', message: `Operation ${sourceClass.name}.${operation.name} is not present in the active metamodel.` });
            }
        }
        for (const relation of source.relations.filter(item => item.sourceTypeId === sourceClass.id)) {
            const sourceTargetName = source.classes.find(item => item.id === relation.targetTypeId)?.name ?? '';
            const relationMatch = target.relations.some(candidate => {
                const candidateSourceName = target.classes.find(item => item.id === candidate.sourceTypeId)?.name ?? '';
                const candidateTargetName = target.classes.find(item => item.id === candidate.targetTypeId)?.name ?? '';
                return sameName(candidateSourceName, sourceClass.name) && sameName(candidate.name, relation.name) && (sourceTargetName === '' || sameName(candidateTargetName, sourceTargetName));
            });
            if (relationMatch) mappedFeatures += 1;
            else {
                missingFeatures += 1;
                diagnostics.push({ severity: 'WARNING', category: 'ASSOCIATED_METAMODEL', message: `Relation ${sourceClass.name}.${relation.name} is not present in the active metamodel.` });
            }
        }
    }

    for (const sourceEnum of source.enums) {
        if (target.enums.some(candidate => sameName(candidate.name, sourceEnum.name))) mappedEnums += 1;
        else {
            missingEnums += 1;
            diagnostics.push({ severity: 'WARNING', category: 'ASSOCIATED_METAMODEL', message: `Enumeration ${sourceEnum.name} exists in the associated metamodel but not in the active metamodel.` });
        }
    }

    const sourceFeatures = source.classes.reduce((sum, metaClass) => sum + metaClass.attributes.length + metaClass.operations.length, 0) + source.relations.length;
    const fullyCompatible = missingClasses === 0 && missingEnums === 0 && missingFeatures === 0;
    return {
        summary: fullyCompatible
            ? `Associated metamodel ${source.name || source.id} is structurally compatible by name with active metamodel ${target.name || target.id}.`
            : `Associated metamodel ${source.name || source.id} can be used as import context, but ${missingClasses + missingEnums + missingFeatures} structural item(s) require adaptation to ${target.name || target.id}.`,
        metrics: [
            { id: 'associated-classes', label: 'Associated classes', value: source.classes.length, tone: 'NEUTRAL' },
            { id: 'associated-classes-mapped', label: 'Classes mapped to active metamodel', value: mappedClasses, tone: missingClasses === 0 ? 'GOOD' : 'WARNING' },
            { id: 'associated-enums-mapped', label: 'Enumerations mapped', value: mappedEnums, tone: missingEnums === 0 ? 'GOOD' : 'WARNING' },
            { id: 'associated-features', label: 'Associated features', value: sourceFeatures, tone: 'NEUTRAL' },
            { id: 'associated-features-mapped', label: 'Features mapped', value: mappedFeatures, tone: missingFeatures === 0 ? 'GOOD' : 'WARNING' },
            { id: 'associated-unresolved', label: 'Associated items requiring adaptation', value: missingClasses + missingEnums + missingFeatures, tone: fullyCompatible ? 'GOOD' : 'WARNING' }
        ],
        diagnostics,
        mappedClasses,
        missingClasses,
        mappedEnums,
        missingEnums,
        mappedFeatures,
        missingFeatures
    };
}

function sameName(left: string, right: string): boolean {
    return left.trim().toLocaleLowerCase() === right.trim().toLocaleLowerCase();
}
