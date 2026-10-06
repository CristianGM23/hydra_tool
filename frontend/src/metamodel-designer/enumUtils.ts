import type { MetaEnum, MetaEnumLiteral, MetaModel } from './types';

export function createUniqueEnumLiteral(metaEnum: MetaEnum, createId: () => string): MetaEnumLiteral {
    const usedNames = new Set(metaEnum.literals.map(literal => literal.name));
    const usedValues = new Set(metaEnum.literals.map(literal => literal.value));
    let name = 'VALUE';
    let suffix = 2;
    let value = 0;
    while (usedNames.has(name)) name = `VALUE_${suffix++}`;
    while (usedValues.has(value)) value += 1;
    return { id: createId(), name, value, annotations: {} };
}

export function findEnumLiteralError(metaEnum: MetaEnum): string {
    if (metaEnum.literals.length === 0) return `Enumeration must contain at least one literal: ${metaEnum.name}`;
    const ids = new Set<string>();
    const names = new Set<string>();
    const values = new Set<number>();
    for (const literal of metaEnum.literals) {
        if (literal.id.trim() === '') return `Enumeration literal id must not be empty: ${metaEnum.name}`;
        if (ids.has(literal.id)) return `Duplicate enumeration literal id: ${literal.id}`;
        if (literal.name.trim() === '') return `Enumeration literal name must not be empty: ${metaEnum.name}`;
        if (names.has(literal.name)) return `Duplicate enumeration literal name: ${literal.name}`;
        if (!Number.isInteger(literal.value)) return `Enumeration literal value must be an integer: ${literal.name}`;
        if (values.has(literal.value)) return `Duplicate enumeration literal value: ${literal.value}`;
        ids.add(literal.id); names.add(literal.name); values.add(literal.value);
    }
    return '';
}

export function findMetamodelEnumError(model: MetaModel): string {
    for (const metaEnum of model.enums) {
        const error = findEnumLiteralError(metaEnum);
        if (error !== '') return error;
    }
    return '';
}
