import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import type { FormEvent } from 'react';

import { findClass } from '../model/modelUtils';
import type { MetamodelDescriptor, ModelDocument } from '../types/editor';

interface PropertyInspectorProps {
    descriptor: MetamodelDescriptor | null;
    document: ModelDocument | null;
    selectedElementId: string;
    onApply: (attributeName: string, value: string) => Promise<void>;
}

/**
 * Permite editar los atributos escalares del EObject seleccionado.
 *
 * @param descriptor descripción del metamodelo.
 * @param document documento semántico.
 * @param selectedElementId elemento seleccionado.
 * @param onApply función utilizada para guardar cada atributo.
 * @returns panel de propiedades.
 */
export function PropertyInspector(props: PropertyInspectorProps) {
    const selectedElement = props.document?.elements.find(element => element.id === props.selectedElementId);
    const stateKey = `${props.selectedElementId}:${JSON.stringify(selectedElement?.attributes ?? {})}`;
    return <PropertyInspectorSession key={stateKey} {...props} />;
}

function PropertyInspectorSession({ descriptor, document, selectedElementId, onApply }: PropertyInspectorProps) {
    const selectedElement = document?.elements.find(element => element.id === selectedElementId);
    const selectedClass = descriptor !== null && selectedElement !== undefined ? findClass(descriptor, selectedElement.classId) : undefined;
    const [values, setValues] = useState<Record<string, string>>(() => Object.fromEntries(
        Object.entries(selectedElement?.attributes ?? {}).map(([name, value]) => [name, String(value ?? '')])
    ));

    /**
     * Guarda los atributos editables.
     *
     * @param event evento del formulario.
     * @returns promesa sin resultado.
     */
    async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
        event.preventDefault();

        if (selectedClass === undefined) {
            return;
        }

        for (const attribute of selectedClass.attributes) {
            if (!attribute.many && attribute.changeable && !attribute.derived) {
                await onApply(attribute.name, values[attribute.name] ?? '');
            }
        }
    }

    let content = <p>Select an element</p>;

    if (selectedElement !== undefined && selectedClass !== undefined) {
        const editableAttributes = selectedClass.attributes.filter(attribute => !attribute.many && attribute.changeable && !attribute.derived);

        content = (
            <form onSubmit={submit}>
                <h2>{selectedElement.className}</h2>
                <p className="element-id">{selectedElement.id}</p>
                {editableAttributes.map(attribute => (
                    <label className="property-field" key={attribute.name}>
                        <span>{attribute.name}</span>
                        <input value={values[attribute.name] ?? ''} onChange={event => setValues(currentValues => ({ ...currentValues, [attribute.name]: event.target.value }))} />
                    </label>
                ))}
                <HydraButton type="submit">Apply properties</HydraButton>
            </form>
        );
    }

    return <aside className="inspector"><h2>Properties</h2>{content}</aside>;
}